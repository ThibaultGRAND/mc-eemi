package fr.eemicraft.murder;

import fr.eemicraft.murder.build.RecordingSink;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Map construite par le plugin ({@link MapDef} : Manoir-bibliotheque, Fete foraine, Grand Musee ; ex-ManoirMap de
 * la 1.7). Cree le monde vide, le (re)construit quand la version de construction change (data.yml), quand la
 * derniere pose n'a pas abouti, quand le monde est vide, ou sur /murder buildmap ; fournit la definition de la map
 * quand le config ne la donne pas.
 *
 * <p>Construction : le decor est dessine en memoire hors du fil principal (aucun acces au monde), puis pose par
 * tranches de quelques millisecondes par tick (pas de gel du serveur). Tant que la pose n'est pas finie ET que le
 * modele des copies n'a pas ete relance, la map est Â« occupee Â» : elle n'est ni tiree au sort ni copiee. Apres un
 * echec, elle reste occupee jusqu'a une construction reussie. Les chunks sont tenus par des tickets du plugin
 * pendant la pose, rendus ensuite (aucun chunk force laisse). Si {@link MapDef#pregen} > 0, les chunks vides
 * autour de la map sont ensuite generes (ils restent sur le disque) avant de rendre la map.
 */
final class BuiltMap {
    /** Le plugin s'arrete de poser des blocs quand ce budget (ms) est depense dans le tick. */
    private static final long BUDGET_MS = 8;
    /** Chunks vides demandes en meme temps pendant la pre-generation. */
    private static final int PREGEN_INFLIGHT = 16;
    /** Map dont la pose de blocs est en cours (les autres attendent leur tour). */
    private static BuiltMap placing;

    final MapDef def;
    private final EemiMurder plugin;
    /** Dessin, pose ou pre-generation en cours. */
    private boolean building;
    /** Derniere construction ratee : map exclue jusqu'a la prochaine reussite. */
    private boolean failed;
    /** Pose finie, modele des copies pas encore relance. */
    private boolean modelPending;
    /** Numero de la construction en cours (les rappels d'une construction abandonnee s'ignorent). */
    private int generation;
    private BukkitTask task;
    private BukkitTask retry;
    private final Set<Long> tickets = new HashSet<>();
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    BuiltMap(EemiMurder plugin, MapDef def) {
        this.plugin = plugin;
        this.def = def;
    }

    // ================================================================ definition de la map

    /**
     * Vrai si l'admin a retire la map : sa cle n'est pas sous {@code maps:} mais sous une autre section du config
     * (par exemple {@code maps-retirees:} ou {@code anciennes-maps:}).
     */
    static boolean retired(FileConfiguration cfg, MapDef def) {
        if (cfg.isConfigurationSection("maps." + def.id)) return false;
        for (String key : cfg.getKeys(false)) {
            if (key.equals("maps")) continue;
            ConfigurationSection sec = cfg.getConfigurationSection(key);
            if (sec != null && sec.contains(def.id)) return true;
            if (cfg.isList(key) && cfg.getStringList(key).contains(def.id)) return true; // maps-retirees: [fete]
        }
        return false;
    }

    /** Definition par defaut (en memoire seulement, le config du serveur n'est pas reecrit). */
    static ConfigurationSection defaultSection(MapDef def) {
        MemoryConfiguration m = new MemoryConfiguration();
        m.set("name", def.name);
        m.set("world", def.world);
        m.set("min", def.bx1 + "," + def.minY + "," + def.bz1);
        m.set("max", def.bx2 + "," + def.maxY + "," + def.bz2);
        List<String> spawns = new ArrayList<>();
        for (int[] p : def.spawns) spawns.add(p[0] + "," + p[1] + "," + p[2]);
        List<String> gold = new ArrayList<>();
        for (int[] p : def.gold) gold.add(p[0] + "," + p[1] + "," + p[2]);
        m.set("spawns", spawns);
        m.set("gold", gold);
        m.set("size", def.small ? "small" : "large");
        return m;
    }

    /**
     * Section maps.&lt;id&gt; ecrite (souvent en partie) par les commandes admin setpos / addspawn / addgold :
     * les cles absentes sont prises dans la definition par defaut (une liste donnee remplace celle par defaut).
     */
    static ConfigurationSection merged(ConfigurationSection cfg, MapDef def) {
        ConfigurationSection m = defaultSection(def);
        for (String k : new String[]{"name", "world", "min", "max", "spawns", "gold", "size"})
            if (cfg.contains(k)) m.set(k, cfg.get(k));
        return m;
    }

    // ================================================================ etat

    /** Map exclue du tirage et des copies (construction, echec, modele pas encore relance). */
    boolean isBusy(String worldName) {
        return (building || failed || modelPending) && def.world.equals(worldName);
    }

    /** Monde en cours de pose (ou rate) : aucune copie ne doit le lire. */
    boolean blocksCopy(String worldName) {
        return (building || failed) && def.world.equals(worldName);
    }

    boolean isBuilding() {
        return building;
    }

    static final class Empty extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    /** Vrai s'il faut construire : version differente, pose precedente inachevee, ou monde vide (dossier efface). */
    boolean needsBuild() {
        World w = world();
        if (w == null) return false;
        YamlConfiguration d = data();
        if (d.getInt(def.dataKey + ".build-version", 0) != def.version || d.getBoolean(def.dataKey + ".building", false)) return true;
        int[] p = def.worldSpawn;
        return w.getBlockAt(p[0], p[1] - 1, p[2]).getType().isAir();
    }

    World world() {
        World w = Bukkit.getWorld(def.world);
        if (w == null) {
            w = new WorldCreator(def.world).generator(new Empty()).environment(World.Environment.NORMAL)
                    .generateStructures(false).createWorld();
            if (w == null) {
                plugin.getLogger().severe("Impossible de creer le monde " + def.world + ".");
                return null;
            }
            plugin.getLogger().info("Monde " + def.world + " charge.");
        }
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        // 1.12.0 : aucun tick aleatoire (la neige et la glace du Chalet fondaient pres des lanternes, l'herbe s'etendait)
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setTime(def.time);
        w.setStorm(false);
        w.setThundering(false);
        w.setSpawnLocation(new Location(w, def.worldSpawn[0] + 0.5, def.worldSpawn[1], def.worldSpawn[2] + 0.5, 180f, 0f));
        return w;
    }

    // ================================================================ construction

    /**
     * Lance une construction ; renvoie un message d'erreur, ou null si elle a demarre. {@code refreshModel} est
     * appele apres la pose, puis toutes les 2 s tant qu'il renvoie false (copie du modele refusee pour l'instant).
     */
    String build(BooleanSupplier refreshModel) {
        if (building) return "une construction est deja en cours";
        World w = world();
        if (w == null) return "monde " + def.world + " introuvable";
        building = true;
        failed = false;
        modelPending = false;
        int gen = ++generation;
        if (retry != null) {
            retry.cancel();
            retry = null;
        }
        warned.clear();
        // marque la pose comme inachevee tant qu'elle n'est pas finie (arret, plantage)
        YamlConfiguration d = data();
        d.set(def.dataKey + ".building", true);
        saveData(d);
        long t0 = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            RecordingSink rec = new RecordingSink();
            Throwable err = null;
            try {
                def.builder.accept(rec);
            } catch (Throwable t) {
                err = t;
            }
            Throwable fail = err;
            if (!plugin.isEnabled()) return;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!building || gen != generation) return; // plugin desactive entre-temps
                    if (fail != null) {
                        building = false;
                        failed = true;
                        plugin.getLogger().severe("Dessin de la map " + def.name + " impossible (map desactivee) : " + fail);
                        return;
                    }
                    plugin.getLogger().info("Map " + def.name + " dessinee en " + (System.currentTimeMillis() - t0) + " ms ("
                            + rec.count() + " blocs, " + rec.paletteSize() + " etats), pose en cours...");
                    place(w, rec, refreshModel, System.currentTimeMillis(), gen);
                });
            } catch (RuntimeException e) {
                // plugin desactive pendant le dessin : rien a faire, la pose sera refaite au demarrage
            }
        });
        return null;
    }

    /** Pose par tranches : chaque tick, des colonnes jusqu'a epuisement du budget. */
    private void place(World w, RecordingSink rec, BooleanSupplier refreshModel, long t0, int gen) {
        int a = def.area;
        for (int cx = (-a) >> 4; cx <= a >> 4; cx++)
            for (int cz = (-a) >> 4; cz <= a >> 4; cz++) {
                w.addPluginChunkTicket(cx, cz, plugin);
                tickets.add(((long) cx << 32) | (cz & 0xFFFFFFFFL));
            }
        // restes d'une partie arretee brutalement (or, fleches)
        for (Entity e : w.getEntities()) {
            if (!(e instanceof Item) && !(e instanceof AbstractArrow)) continue;
            Location l = e.getLocation();
            if (Math.abs(l.getX()) <= a + 1 && Math.abs(l.getZ()) <= a + 1) e.remove();
        }
        int[] cursor = {0};
        int width = 2 * a + 1, total = width * width;
        long[] stats = new long[2]; // poses, effaces
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // une seule pose a la fois (plusieurs maps a construire au demarrage : 8 ms par tick au total)
            if (placing != null && placing != this) return;
            placing = this;
            try {
                placeSlice(w, rec, cursor, width, total, stats);
            } catch (RuntimeException ex) {
                task.cancel();
                task = null;
                placing = null;
                releaseTickets(w);
                building = false;
                failed = true;
                plugin.getLogger().severe("Pose de la map " + def.name + " interrompue (map desactivee jusqu'a /murder buildmap " + def.id + ") : " + ex);
                return;
            }
            if (cursor[0] < total) return;
            task.cancel();
            task = null;
            placing = null;
            releaseTickets(w);
            long tPose = System.currentTimeMillis() - t0;
            if (def.pregen > 0) {
                // la pose est finie et enregistree : un arret pendant la pre-generation ne refera que celle-ci
                YamlConfiguration d = data();
                d.set(def.dataKey + ".build-version", def.version);
                d.set(def.dataKey + ".building", false);
                d.set(def.dataKey + ".pregen-done", false);
                saveData(d);
                pregen(w, gen, () -> finish(w, stats, tPose, refreshModel));
            } else finish(w, stats, tPose, refreshModel);
        }, 1L, 1L);
    }

    /** Une tranche de pose : des colonnes jusqu'a epuisement du budget du tick. */
    private void placeSlice(World w, RecordingSink rec, int[] cursor, int width, int total, long[] stats) {
        int a = def.area;
        {
            long end = System.currentTimeMillis() + BUDGET_MS;
            while (cursor[0] < total && System.currentTimeMillis() < end) {
                int c = cursor[0]++;
                int x = -a + c / width, z = -a + c % width;
                for (int y = def.yLo; y <= def.yHi; y++) {
                    int id = rec.id(x, y, z);
                    Block b = w.getBlockAt(x, y, z);
                    if (id != 0) {
                        b.setBlockData(blockData(rec.state(id)), false);
                        stats[0]++;
                    } else if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR, false);
                        stats[1]++;
                    }
                }
            }
        }
    }

    /**
     * Genere (vides, generateur du plugin) tous les chunks de -pregen a pregen-1 en x et z, quelques-uns a la fois ;
     * ils sont ecrits sur le disque au dechargement et par la sauvegarde finale.
     */
    private void pregen(World w, int gen, Runnable then) {
        if (!(w.getGenerator() instanceof Empty)) {
            plugin.getLogger().warning("Monde " + def.world + " charge avec un autre generateur (Multiverse ?) : chunks autour de la map non pre-generes.");
            markPregen(true);
            then.run();
            return;
        }
        int lo = -def.pregen, hi = def.pregen - 1, side = hi - lo + 1, total = side * side;
        int[] st = {0, 0, 0}; // demandes, finies, ratees
        long t0 = System.currentTimeMillis();
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gen != generation) { // construction abandonnee ou relancee
                self[0].cancel();
                return;
            }
            while (st[0] < total && st[0] - st[1] < PREGEN_INFLIGHT) {
                int i = st[0]++;
                int cx = lo + i / side, cz = lo + i % side;
                w.getChunkAtAsync(cx, cz, true).whenComplete((c, e) -> {
                    st[1]++;
                    if (e != null || c == null) st[2]++;
                });
            }
            if (st[1] < total) return;
            self[0].cancel();
            w.save();
            markPregen(true);
            plugin.getLogger().info("Monde " + def.world + " : " + total + " chunks vides generes (" + lo * 16 + ".." + (hi * 16 + 15)
                    + ")" + (st[2] > 0 ? ", " + st[2] + " echec(s)" : "") + " en " + (System.currentTimeMillis() - t0) + " ms.");
            then.run();
        }, 1L, 1L);
    }

    private void markPregen(boolean done) {
        YamlConfiguration d = data();
        d.set(def.dataKey + ".pregen-done", done);
        saveData(d);
    }

    /** Pre-generation interrompue (arret du serveur) apres une pose reussie. */
    boolean needsPregen() {
        return def.pregen > 0 && !building && data().contains(def.dataKey + ".pregen-done") && !data().getBoolean(def.dataKey + ".pregen-done");
    }

    /** Reprend seulement la pre-generation (la map reste jouable pendant ce temps). */
    void pregenOnly() {
        World w = world();
        if (w == null || building) return;
        plugin.getLogger().info("Monde " + def.world + " : reprise de la pre-generation des chunks vides.");
        pregen(w, ++generation, () -> {});
    }

    private void finish(World w, long[] stats, long tPose, BooleanSupplier refreshModel) {
        YamlConfiguration d = data();
        d.set(def.dataKey + ".build-version", def.version);
        d.set(def.dataKey + ".building", false);
        saveData(d);
        building = false;
        modelPending = true;
        plugin.getLogger().info("Map " + def.name + " construite (version " + def.version + ") : " + stats[0] + " blocs poses, "
                + stats[1] + " effaces, " + warned.size() + " etat(s) invalide(s), pose en " + tPose + " ms.");
        if (refreshModel == null || refreshModel.getAsBoolean()) {
            modelPending = false;
            return;
        }
        // modele refuse pour l'instant (copie en cours, partie sur la map...) : nouvel essai toutes les 2 s
        retry = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (refreshModel.getAsBoolean()) {
                modelPending = false;
                retry.cancel();
                retry = null;
            }
        }, 40L, 40L);
    }

    private BlockData blockData(String state) {
        return cache.computeIfAbsent(state, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                if (warned.add(k)) plugin.getLogger().warning("Bloc invalide ignore : " + k);
                return Material.AIR.createBlockData();
            }
        });
    }

    private void releaseTickets(World w) {
        for (long k : tickets) w.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
        tickets.clear();
    }

    /** Arret du plugin : la pose en cours est abandonnee (data.yml la marque inachevee : refaite au demarrage). */
    void disable() {
        generation++;
        if (placing == this) placing = null;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (retry != null) {
            retry.cancel();
            retry = null;
        }
        World w = Bukkit.getWorld(def.world);
        if (w != null) releaseTickets(w);
        else tickets.clear();
        building = false;
        modelPending = false;
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "data.yml");
    }

    private YamlConfiguration data() {
        return YamlConfiguration.loadConfiguration(dataFile());
    }

    private void saveData(YamlConfiguration d) {
        try {
            plugin.getDataFolder().mkdirs();
            d.save(dataFile());
        } catch (IOException e) {
            plugin.getLogger().warning("data.yml : " + e.getMessage());
        }
    }
}
