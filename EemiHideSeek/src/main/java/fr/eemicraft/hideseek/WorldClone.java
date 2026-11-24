/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.WorldCreator
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package fr.eemicraft.hideseek;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldClone {
    private static final Set<String> SKIP = Set.of("session.lock", "uid.dat");

    private WorldClone() {
    }

    public static String name(String base, int n) {
        return n <= 1 ? base : base + "_" + n;
    }

    public static int index(String base, String worldName) {
        if (worldName.equals(base)) {
            return 1;
        }
        if (!worldName.startsWith(base + "_")) {
            return 0;
        }
        try {
            return Integer.parseInt(worldName.substring(base.length() + 1));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void create(Plugin plugin, World template, String name, Consumer<World> done) {
        World loaded = Bukkit.getWorld((String)name);
        if (loaded != null) {
            done.accept(loaded);
            return;
        }
        template.save(true);
        Path src = template.getWorldFolder().toPath();
        Path dest = src.resolveSibling(name);
        Path wgSrc = plugin.getServer().getPluginsFolder().toPath().resolve("WorldGuard/worlds").resolve(template.getName());
        Path wgDest = wgSrc.resolveSibling(name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                WorldClone.delete(dest);
                WorldClone.copy(src, dest);
                if (Files.isDirectory(wgSrc, new LinkOption[0])) {
                    WorldClone.delete(wgDest);
                    WorldClone.copy(wgSrc, wgDest);
                }
                ok = true;
            }
            catch (IOException e) {
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
                    plugin.getLogger().warning("La copie " + name + " a ete chargee depuis " + String.valueOf(w.getWorldFolder()) + " et non " + String.valueOf(dest) + " : la map est peut-etre vide.");
                }
                w.setAutoSave(false);
                plugin.getLogger().info("Copie " + name + " prete (depuis " + template.getName() + ").");
                done.accept(w);
            });
        });
    }

    public static void destroy(Plugin plugin, World w, String base, Consumer<Player> evict) {
        if (w == null || w.getName().equals(base)) {
            return;
        }
        for (Player p : w.getPlayers()) {
            evict.accept(p);
        }
        Path folder = w.getWorldFolder().toPath();
        String name = w.getName();
        if (!Bukkit.unloadWorld((World)w, (boolean)false)) {
            plugin.getLogger().warning("Impossible de decharger la copie " + name + ".");
            return;
        }
        Path wg = plugin.getServer().getPluginsFolder().toPath().resolve("WorldGuard/worlds").resolve(name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                WorldClone.delete(folder);
                WorldClone.delete(wg);
            }
            catch (IOException e) {
                plugin.getLogger().warning("Suppression de " + name + " incomplete : " + e.getMessage());
            }
        });
    }

    public static void cleanup(Plugin plugin, World template, String base, int max) {
        if (template == null) {
            return;
        }
        for (int n = 2; n <= Math.max(max, 2) + 4; ++n) {
            String name = WorldClone.name(base, n);
            World w = Bukkit.getWorld((String)name);
            if (w != null) {
                WorldClone.destroy(plugin, w, base, p -> p.teleport(((World)Bukkit.getWorlds().get(0)).getSpawnLocation()));
                continue;
            }
            Path dir = template.getWorldFolder().toPath().resolveSibling(name);
            try {
                WorldClone.delete(dir);
                continue;
            }
            catch (IOException e) {
                plugin.getLogger().warning("Suppression de " + String.valueOf(dir) + " incomplete : " + e.getMessage());
            }
        }
    }

    private static boolean isPaperMetadata(Path file) {
        Path parent = file.getParent();
        return file.getFileName().toString().equals("metadata.dat") && parent != null && parent.getFileName().toString().equals("paper");
    }

    private static void copy(final Path src, final Path dest) throws IOException {
        Files.walkFileTree(src, (FileVisitor<? super Path>)new SimpleFileVisitor<Path>(){

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(src.relativize(dir).toString()), new FileAttribute[0]);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP.contains(file.getFileName().toString()) && !WorldClone.isPaperMetadata(file)) {
                    Files.copy(file, dest.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void delete(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir, new LinkOption[0])) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir, new FileVisitOption[0]);){
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}

