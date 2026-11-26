package CHANGER_LE_PACKAGE;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Partie commune des plugins de saison (EemiHalloween, EemiNoel). Copier ce fichier dans le plugin en changeant
 * le package. Source : _commun/Saison.java.
 *
 * Boutique : EemiCore lit available-from / available-until / text de ses cosmétiques dans sa config EN MÉMOIRE
 * (getConfig(), jamais sauvegardée par EemiCore). On modifie seulement cette copie en mémoire, jamais le fichier :
 * - saison activée : en vente (available-from retiré, available-until = boutique.jusqu-au ou 2099-12-31), titre affiché ;
 * - saison coupée : plus en vente ni visible pour ceux qui ne l'ont pas (available-from 2099-01-01) ; ceux qui l'ont
 *   le voient toujours et peuvent le porter (26/11, Esteban).
 *   Les joueurs gardent leurs achats ; tout revient à l'activation suivante.
 * Les valeurs d'origine sont relues dans plugins/EemiCore/config.yml (lecture seule). Un /eemi reload remet la config
 * du fichier : apply() est rappelé toutes les 10 s pour reprendre la main.
 *
 * Musique : EemiMusique joue plugins/EemiMusique/songs.yml s'il existe, sinon la playlist de son jar (musique de base).
 * music on : copie la playlist de saison (ressource du jar de ce plugin) à cet endroit ; music off : renomme songs.yml
 * en songs.yml.off ; puis /musique reload dans les deux cas.
 */
final class Saison {

    private Saison() {
    }

    // ------------------------------------------------------------------ boutique EemiCore

    /** Applique l'état de la saison aux cosmétiques listés dans boutique.cosmetiques. Renvoie le nombre touché. */
    static int applyShop(JavaPlugin pl, boolean on) {
        if (!pl.getConfig().getBoolean("boutique.enabled", true)) return 0;
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        if (!(core instanceof JavaPlugin jp) || !core.isEnabled()) return 0;
        ConfigurationSection mem = jp.getConfig().getConfigurationSection("cosmetics");
        if (mem == null) return 0;
        ConfigurationSection disk = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "config.yml"))
                .getConfigurationSection("cosmetics");
        String until = pl.getConfig().getString("boutique.jusqu-au", "");
        try {
            if (until == null || until.isBlank() || LocalDate.parse(until).isBefore(LocalDate.now())) until = "2099-12-31";
        } catch (RuntimeException e) {
            until = "2099-12-31";
        }
        int n = 0;
        List<String> ids = pl.getConfig().getStringList("boutique.cosmetiques");
        for (String id : ids) {
            ConfigurationSection s = mem.getConfigurationSection(id);
            if (s == null) continue;
            ConfigurationSection orig = disk == null ? null : disk.getConfigurationSection(id);
            String text = orig != null ? orig.getString("text", s.getString("text", "")) : s.getString("text", "");
            if (on) {
                // 26/11 : une date passee, PAS null : sans la cle, EemiCore relit la valeur par defaut de son jar
                // (getConfig() a les defauts du jar), donc « 2026-12-01 » et rien n'etait en vente avant decembre.
                set(s, "available-from", "2000-01-01");
                set(s, "available-until", until);
                if ("title".equals(s.getString("type")) && !text.isEmpty()) set(s, "text", text);
            } else {
                set(s, "available-from", "2099-01-01");
                if (orig != null && orig.contains("available-until")) set(s, "available-until", orig.getString("available-until"));
                // 26/11 : hors saison, le titre reste porte par ceux qui l'ont (EemiCore le cache aux autres)
                if ("title".equals(s.getString("type")) && !text.isEmpty()) set(s, "text", text);
            }
            n++;
        }
        return n;
    }

    /** Nombre de cosmetiques de boutique.cosmetiques reellement en vente dans la config EN MEMOIRE d'EemiCore,
     *  au format « n/total » (pour /noel status et /halloween status). */
    static String onSaleCount(JavaPlugin pl) {
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        List<String> ids = pl.getConfig().getStringList("boutique.cosmetiques");
        if (!(core instanceof JavaPlugin jp)) return "?/" + ids.size();
        ConfigurationSection mem = jp.getConfig().getConfigurationSection("cosmetics");
        LocalDate today = LocalDate.now();
        int n = 0;
        for (String id : ids) {
            ConfigurationSection s = mem == null ? null : mem.getConfigurationSection(id);
            if (s == null) continue;
            try {
                String from = s.getString("available-from"), until = s.getString("available-until");
                if (from != null && today.isBefore(LocalDate.parse(from))) continue;
                if (until != null && today.isAfter(LocalDate.parse(until))) continue;
            } catch (RuntimeException e) {
                // date illisible : EemiCore la considere en vente
            }
            n++;
        }
        return n + "/" + ids.size();
    }

    private static void set(ConfigurationSection s, String key, Object value) {
        Object cur = s.get(key);
        if (value == null ? cur != null : !value.equals(cur)) s.set(key, value);
    }

    // ------------------------------------------------------------------ musique du lobby (EemiMusique)

    /** Remplace la playlist du lobby par la ressource {@code resource} de ce plugin. */
    static boolean musicOn(JavaPlugin pl, String resource, CommandSender sender) {
        Plugin mus = Bukkit.getPluginManager().getPlugin("EemiMusique");
        if (mus == null) {
            sender.sendMessage("§cEemiMusique n'est pas installe.");
            return false;
        }
        File target = new File(mus.getDataFolder(), "songs.yml");
        try (InputStream in = pl.getResource(resource)) {
            if (in == null) {
                sender.sendMessage("§cPlaylist " + resource + " absente du jar.");
                return false;
            }
            mus.getDataFolder().mkdirs();
            if (target.exists() && !isSeasonal(target)) {
                // playlist perso (pas une playlist de saison) : on la garde à côté
                Files.copy(target.toPath(), new File(mus.getDataFolder(), "songs-perso.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            sender.sendMessage("§cImpossible d'ecrire songs.yml : " + e.getMessage());
            return false;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "musique reload");
        return true;
    }

    /** Remet la musique de base du lobby (celle du jar d'EemiMusique). */
    static boolean musicOff(CommandSender sender) {
        Plugin mus = Bukkit.getPluginManager().getPlugin("EemiMusique");
        if (mus == null) {
            sender.sendMessage("§cEemiMusique n'est pas installe.");
            return false;
        }
        File target = new File(mus.getDataFolder(), "songs.yml");
        if (target.exists()) {
            try {
                Files.move(target.toPath(), new File(mus.getDataFolder(), "songs.yml.off").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                sender.sendMessage("§cImpossible de renommer songs.yml : " + e.getMessage());
                return false;
            }
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "musique reload");
        return true;
    }

    /** Les playlists de saison commencent par « # Playlist Halloween » / « # Playlist Noel ». */
    private static boolean isSeasonal(File f) {
        try {
            String first = Files.readAllLines(f.toPath()).stream().findFirst().orElse("");
            return first.startsWith("# Playlist");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
