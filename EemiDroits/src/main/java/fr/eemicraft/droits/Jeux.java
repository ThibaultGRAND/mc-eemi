package fr.eemicraft.droits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liste des jeux (lue dans le config d'EemiCore) et leur état : ouvert, bêta, maintenance. */
public final class Jeux {

    public enum Etat {
        OUVERT("§aOuvert", "§a"), BETA("§dBêta", "§d"), MAINTENANCE("§cMaintenance", "§c");
        public final String nom, couleur;
        Etat(String nom, String couleur) { this.nom = nom; this.couleur = couleur; }
        Etat suivant() { return values()[(ordinal() + 1) % 3]; }
        Etat precedent() { return values()[(ordinal() + 2) % 3]; }
        static Etat lire(String s) {
            if (s == null) return null;
            switch (s.toLowerCase(Locale.ROOT)) {
                case "ouvert": case "open": case "off": return OUVERT;
                case "beta": case "bêta": return BETA;
                case "maintenance": case "on": return MAINTENANCE;
                default: return null;
            }
        }
    }

    public static final class Jeu {
        public final String id;
        public String nom = "", join = "", spectate = "";
        public Material icone = Material.PAPER;
        public final List<String> aliases = new ArrayList<>();
        public final List<String> mondes = new ArrayList<>();
        Jeu(String id) { this.id = id; }
    }

    private static final Pattern COPIE = Pattern.compile("^(.+)_(\\d+|modele)$");
    private static final Pattern JOIN = Pattern.compile("^(\\S+) join(\\s.*)?$");

    private final EemiDroits pl;
    private final File fichier;
    final LinkedHashMap<String, Jeu> jeux = new LinkedHashMap<>();
    private final Map<String, Etat> etats = new HashMap<>();
    private final Map<String, String> raisons = new HashMap<>();
    private final Map<String, String> mondeVersJeu = new HashMap<>();
    private Set<String> maintCore = Set.of();
    private long maintCoreLu;

    Jeux(EemiDroits pl) {
        this.pl = pl;
        this.fichier = new File(pl.getDataFolder(), "jeux.yml");
    }

    void charger() {
        jeux.clear();
        mondeVersJeu.clear();
        File core = new File(pl.getDataFolder().getParentFile(), "EemiCore/config.yml");
        if (core.exists()) lireSection(YamlConfiguration.loadConfiguration(core).getConfigurationSection("games"));
        else pl.getLogger().warning("plugins/EemiCore/config.yml introuvable : seuls les jeux-supplementaires sont connus.");
        lireSection(pl.getConfig().getConfigurationSection("jeux-supplementaires"));
        for (Jeu j : jeux.values())
            for (String m : j.mondes) mondeVersJeu.put(base(m), j.id);

        etats.clear();
        raisons.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String id : y.getKeys(false)) {
            Etat e = Etat.lire(y.getString(id + ".etat"));
            if (e == null || e == Etat.OUVERT) continue;
            etats.put(id, e);
            raisons.put(id, y.getString(id + ".raison", ""));
        }
        maintCoreLu = 0;
        pl.getLogger().info(jeux.size() + " jeux, " + etats.size() + " en beta ou maintenance.");
    }

    private void lireSection(ConfigurationSection s) {
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection g = s.getConfigurationSection(id);
            if (g == null) continue;
            Jeu j = new Jeu(id.toLowerCase(Locale.ROOT));
            j.nom = Txt.col(g.getString("name", id));
            Material m = Material.matchMaterial(g.getString("icon", "PAPER"));
            if (m != null && m.isItem()) j.icone = m;
            j.join = g.getString("join", "");
            j.spectate = g.getString("spectate", "");
            j.aliases.addAll(g.getStringList("join-aliases"));
            j.mondes.addAll(g.getStringList("worlds"));
            jeux.put(j.id, j);
        }
    }

    void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Etat> e : etats.entrySet()) {
            y.set(e.getKey() + ".etat", e.getValue().name().toLowerCase(Locale.ROOT));
            y.set(e.getKey() + ".raison", raisons.getOrDefault(e.getKey(), ""));
        }
        try { y.save(fichier); } catch (IOException ex) { pl.getLogger().warning("jeux.yml : " + ex.getMessage()); }
    }

    /** Nom du monde sans le suffixe de copie (_2, _3, _modele), en minuscules. */
    static String base(String monde) {
        String m = monde.toLowerCase(Locale.ROOT);
        Matcher x = COPIE.matcher(m);
        return x.matches() ? x.group(1) : m;
    }

    Jeu jeuDuMonde(String monde) {
        String id = mondeVersJeu.get(monde.toLowerCase(Locale.ROOT));
        if (id == null) id = mondeVersJeu.get(base(monde));
        return id == null ? null : jeux.get(id);
    }

    Jeu jeu(String id) { return id == null ? null : jeux.get(id.toLowerCase(Locale.ROOT)); }

    /** Les jeux mis en maintenance directement par /eemi maintenance comptent aussi. */
    private Set<String> maintenanceCore() {
        long now = System.currentTimeMillis();
        if (now - maintCoreLu > 5000) {
            maintCoreLu = now;
            File f = new File(pl.getDataFolder().getParentFile(), "EemiCore/maintenance.yml");
            if (!f.exists()) maintCore = Set.of();
            else {
                Set<String> s = new HashSet<>();
                for (String k : YamlConfiguration.loadConfiguration(f).getKeys(false)) s.add(k.toLowerCase(Locale.ROOT));
                maintCore = s;
            }
        }
        return maintCore;
    }

    Etat etat(String id) {
        Etat e = etats.get(id);
        if (e != null) return e;
        return maintenanceCore().contains(id) ? Etat.MAINTENANCE : Etat.OUVERT;
    }

    String raison(String id) { return raisons.getOrDefault(id, ""); }

    void changer(CommandSender par, Jeu j, Etat nouveau, String raison) {
        Etat ancien = etat(j.id);
        if (nouveau == Etat.OUVERT) { etats.remove(j.id); raisons.remove(j.id); }
        else { etats.put(j.id, nouveau); raisons.put(j.id, raison == null ? "" : raison.trim()); }
        sauver();
        maintCoreLu = 0;
        boolean core = Bukkit.getPluginManager().isPluginEnabled("EemiCore");
        if (core) {
            // EemiCore garde sa propre maintenance (statut, status.json, refus de /eemi join) : on la suit.
            if (nouveau == Etat.MAINTENANCE && ancien != Etat.MAINTENANCE)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi maintenance " + j.id + " on" + (raison == null || raison.isBlank() ? "" : " " + raison.trim()));
            else if (nouveau != Etat.MAINTENANCE && ancien == Etat.MAINTENANCE)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi maintenance " + j.id + " off");
        }
        pl.getLogger().info(j.id + " : " + ancien + " -> " + nouveau + " (par " + par.getName() + ")");
        if (pl.getConfig().getBoolean("annonces", true) && (nouveau == Etat.BETA || !core || nouveau == Etat.OUVERT && ancien == Etat.BETA)) {
            String msg = nouveau == Etat.BETA ? j.nom + " §7passe en §dbêta§7 : réservé aux bêta-testeurs."
                    : nouveau == Etat.MAINTENANCE ? j.nom + " §7est en §cmaintenance§7."
                    : j.nom + " §7est maintenant §aouvert à tous§7 !";
            for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(Txt.PREFIXE + msg);
        }
    }

    /** "murder join" -> "murder leave" ; null si rien à faire. */
    String commandeSortie(Jeu j) {
        ConfigurationSection s = pl.getConfig().getConfigurationSection("sortie");
        if (s != null && s.isString(j.id)) {
            String c = s.getString(j.id, "");
            return c.isBlank() ? null : c;
        }
        List<String> formes = new ArrayList<>(j.aliases);
        formes.add(0, j.join);
        for (String f : formes) {
            if (f == null) continue;
            Matcher m = JOIN.matcher(f.trim().toLowerCase(Locale.ROOT));
            if (m.matches()) return m.group(1) + " leave";
        }
        return null;
    }

    /** Le texte tapé (normalisé) lance-t-il ce jeu (join, alias ou spectate) ? */
    boolean estCommandeDuJeu(Jeu j, String tape) {
        List<String> formes = new ArrayList<>(j.aliases);
        formes.add(j.join);
        formes.add(j.spectate);
        for (String f : formes) {
            if (f == null || f.isBlank() || f.trim().toLowerCase(Locale.ROOT).startsWith("console:")) continue;
            String n = Txt.normCommande(f.replace("%player%", ""));
            if (n.isEmpty()) continue;
            if (n.indexOf(' ') < 0 ? tape.equals(n) : tape.equals(n) || tape.startsWith(n + " ")) return true;
        }
        return false;
    }
}
