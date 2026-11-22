package fr.eemicraft.abysse;

import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Remise à zéro des strates aux heures de {@code reset.horaires} (12:00 et 21:30 par défaut, fuseau
 * {@code reset.fuseau}) : les joueurs remontent à Bordgouffre, le monde est déchargé sans sauvegarde puis son dossier
 * supprimé ; il est régénéré (même graine, donc même terrain neuf : minerai, campements, rien de posé ni de cassé) à
 * la prochaine descente. Les foreuses sont reposées sur leur filon. Annonces 5 min et 1 min avant.
 */
final class Reset {

    private final EemiAbysse plugin;
    /** Strates en cours de remise à zéro : personne n'y descend. */
    final Set<Integer> busy = new HashSet<>();
    private final Set<String> done = new HashSet<>();

    Reset(EemiAbysse plugin) {
        this.plugin = plugin;
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("reset.fuseau", "Europe/Paris"));
        } catch (Exception e) {
            return ZoneId.of("Europe/Paris");
        }
    }

    private List<String> times() {
        List<String> l = plugin.getConfig().getStringList("reset.horaires");
        return l.isEmpty() ? List.of("12:00", "21:30") : l;
    }

    /** Toutes les 10 s : annonces et remise à zéro à l'heure dite (une seule fois par horaire et par jour). */
    void tick() {
        if (!plugin.getConfig().getBoolean("reset.actif", true)) return;
        ZonedDateTime now = ZonedDateTime.now(zone());
        LocalTime t = now.toLocalTime();
        for (String h : times()) {
            LocalTime at;
            try {
                at = LocalTime.parse(h.length() == 4 ? "0" + h : h);
            } catch (Exception e) {
                continue;
            }
            String day = now.toLocalDate() + " " + h;
            long secs = java.time.Duration.between(t, at).getSeconds();
            if (secs <= 300 && secs > 290 && done.add(day + " 5")) warn("5 minutes");
            if (secs <= 60 && secs > 50 && done.add(day + " 1")) warn("1 minute");
            if (secs <= 0 && secs > -60 && done.add(day + " go")) run();
        }
        if (done.size() > 200) done.clear();
    }

    private void warn(String in) {
        plugin.broadcastAbysse(Txt.PREFIX + "§e⚠ Les strates sont remises à neuf dans §f" + in + "§e : minerai, galeries et campements reviennent, "
                + "tout ce qui a été posé ou cassé disparaît. Les joueurs en bas remonteront à Bordgouffre.");
        for (Gen.Strata s : Gen.STRATAS) {
            World w = Bukkit.getWorld(s.world());
            if (w == null) continue;
            for (Player p : w.getPlayers()) {
                p.sendTitlePart(TitlePart.TITLE, Txt.c("§e⚠ Remise à neuf"));
                p.sendTitlePart(TitlePart.SUBTITLE, Txt.c("§7des strates dans §f" + in));
                p.playSound(p, Sound.BLOCK_BELL_USE, 1, 0.7f);
            }
        }
    }

    /** Remet à neuf toutes les strates. */
    void run() {
        plugin.getLogger().info("Remise a neuf des strates.");
        for (Gen.Strata s : Gen.STRATAS) reset(s);
        plugin.broadcastAbysse(Txt.PREFIX + "§aLes strates sont remises à neuf : bonne descente !");
    }

    void reset(Gen.Strata s) {
        if (!busy.add(s.n)) return;
        // charger la strate si besoin, pour connaître son dossier et la fermer proprement
        World w = Bukkit.getWorld(s.world());
        if (w == null) w = plugin.worlds.load(s.n);
        if (w == null) {
            busy.remove(s.n);
            return;
        }
        for (Player p : w.getPlayers()) {
            p.teleport(plugin.worlds.hubSpawn());
            p.sendMessage(Txt.PREFIX + "§eLa strate " + s.n + " est remise à neuf : te voilà à Bordgouffre.");
        }
        plugin.mobs.abort(s.n);
        plugin.mobs.clearWorld(w);
        File folder = w.getWorldFolder();
        // garde-fou : on ne supprime que le dossier propre à cette strate, jamais un dossier parent
        if (!folder.getName().equals(s.world()) || !folder.isDirectory()) {
            plugin.getLogger().severe("Strate " + s.n + " : dossier inattendu " + folder + ", remise a neuf annulee.");
            busy.remove(s.n);
            return;
        }
        if (!Bukkit.unloadWorld(w, false)) {
            plugin.getLogger().warning("Strate " + s.n + " : dechargement refuse, remise a neuf annulee.");
            busy.remove(s.n);
            return;
        }
        plugin.regen.clearWorld(s.world());
        // suppression du dossier hors du fil principal, un peu après le déchargement (fichiers de région fermés)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            boolean ok = delete(folder.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> {
                busy.remove(s.n);
                plugin.getLogger().info("Strate " + s.n + (ok ? " remise a neuf (" + folder + " supprime)." : " : dossier " + folder + " pas entierement supprime !"));
            });
        }, 40L);
    }

    private boolean delete(Path root) {
        if (!Files.exists(root)) return true;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    plugin.getLogger().warning("Suppression impossible : " + p + " (" + e.getMessage() + ")");
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Parcours impossible : " + root + " (" + e.getMessage() + ")");
        }
        return !Files.exists(root);
    }
}
