package fr.eemicraft.ballsofsteel;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Copies de mondes pour les parties en parallèle (utilitaire commun EEMICRAFT,
 * source de référence : mc/_commun/WorldClone.java, à recopier dans chaque jeu).
 *
 * Partie 1 = le monde d'origine (ex. « sheepwars »). Partie n ≥ 2 = une copie nommée
 * « sheepwars_n », faite à la demande depuis le monde d'origine, supprimée quand elle
 * ne sert plus. Les noms sont fixes et bornés (max-instances) : ils sont déclarés à
 * l'avance dans DeluxeHub, TAB, OldCombatMechanics, EemiBlocage et EemiCore.
 */
public final class WorldClone {

    /** Fichiers à ne jamais copier (verrou, identifiant du monde d'origine). */
    private static final Set<String> SKIP = Set.of("session.lock", "uid.dat");

    private WorldClone() {
    }

    /** Nom de la copie n (n ≥ 2) ; n = 1 donne le monde d'origine. */
    public static String name(String base, int n) {
        return n <= 1 ? base : base + "_" + n;
    }

    /** Numéro de partie d'un nom de monde (« sheepwars_3 » → 3, « sheepwars » → 1, autre → 0). */
    public static int index(String base, String worldName) {
        if (worldName.equals(base)) return 1;
        if (!worldName.startsWith(base + "_")) return 0;
        try {
            return Integer.parseInt(worldName.substring(base.length() + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Copie le monde template sous le nom name puis le charge ; done reçoit le monde
     * (thread principal) ou null en cas d'échec. Si la copie est déjà chargée, elle est
     * rendue telle quelle.
     */
    public static void create(Plugin plugin, World template, String name, Consumer<World> done) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            done.accept(loaded);
            return;
        }
        template.save(true);
        Path src = template.getWorldFolder().toPath();
        Path dest = src.resolveSibling(name);
        Path wgSrc = plugin.getServer().getPluginsFolder().toPath().resolve("WorldGuard/worlds").resolve(template.getName());
        Path wgDest = wgSrc.resolveSibling(name);
        // copie des fichiers hors du thread principal, chargement sur le thread principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                delete(dest);
                copy(src, dest);
                if (Files.isDirectory(wgSrc)) {
                    delete(wgDest);
                    copy(wgSrc, wgDest);
                }
                ok = true;
            } catch (IOException e) {
                plugin.getLogger().warning("Copie de " + template.getName() + " vers " + name + " impossible : " + e.getMessage());
                ok = false;
            }
            boolean copied = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!copied) {
                    done.accept(null);
                    return;
                }
                World w = new WorldCreator(name).copy(template).createWorld();
                if (w == null) {
                    plugin.getLogger().warning("Chargement de la copie " + name + " impossible.");
                    done.accept(null);
                    return;
                }
                if (!w.getWorldFolder().toPath().toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                    // Paper a cherché le monde ailleurs : la copie n'a pas été lue, on le signale
                    plugin.getLogger().warning("La copie " + name + " a ete chargee depuis " + w.getWorldFolder()
                            + " et non " + dest + " : la map est peut-etre vide.");
                }
                w.setAutoSave(false);
                plugin.getLogger().info("Copie " + name + " prete (depuis " + template.getName() + ").");
                done.accept(w);
            });
        });
    }

    /** Décharge la copie sans la sauvegarder et supprime ses fichiers (jamais la partie 1). */
    public static void destroy(Plugin plugin, World w, String base, Consumer<Player> evict) {
        if (w == null || w.getName().equals(base)) return;
        for (Player p : w.getPlayers()) evict.accept(p);
        Path folder = w.getWorldFolder().toPath();
        String name = w.getName();
        if (!Bukkit.unloadWorld(w, false)) {
            plugin.getLogger().warning("Impossible de decharger la copie " + name + ".");
            return;
        }
        Path wg = plugin.getServer().getPluginsFolder().toPath().resolve("WorldGuard/worlds").resolve(name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                delete(folder);
                delete(wg);
            } catch (IOException e) {
                plugin.getLogger().warning("Suppression de " + name + " incomplete : " + e.getMessage());
            }
        });
    }

    /** Au démarrage : supprime les copies restées sur le disque (base_2, base_3, …). */
    public static void cleanup(Plugin plugin, World template, String base, int max) {
        if (template == null) return;
        for (int n = 2; n <= Math.max(max, 2) + 4; n++) {
            String name = name(base, n);
            World w = Bukkit.getWorld(name);
            if (w != null) {
                destroy(plugin, w, base, p -> p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation()));
                continue;
            }
            Path dir = template.getWorldFolder().toPath().resolveSibling(name);
            try {
                delete(dir);
            } catch (IOException e) {
                plugin.getLogger().warning("Suppression de " + dir + " incomplete : " + e.getMessage());
            }
        }
    }

    /** data/paper/metadata.dat porte l'identifiant du monde : copié, Paper refuse la copie (« duplicate »). */
    private static boolean isPaperMetadata(Path file) {
        Path parent = file.getParent();
        return file.getFileName().toString().equals("metadata.dat") && parent != null
                && parent.getFileName().toString().equals("paper");
    }

    private static void copy(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP.contains(file.getFileName().toString()) && !isPaperMetadata(file))
                    Files.copy(file, dest.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
