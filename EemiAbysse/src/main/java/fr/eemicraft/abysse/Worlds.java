package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Mondes de l'Abysse : Bordgouffre ({@code abysse}), les Concessions ({@code abysse_concessions}) et les strates
 * ({@code abysse_s<n>}, chargées à la demande et déchargées quand elles sont vides depuis 5 min).
 */
final class Worlds {

    static final String HUB = "abysse", BASE = "abysse_concessions";
    /** Version de la construction de Bordgouffre (reconstruite si elle change et si hub.build est vrai). */
    static final int HUB_VERSION = 2;
    static final int PLOT_SPACING = 256, PLOT_Y = 63;
    static final int[] PLOT_RADIUS = {16, 24, 32, 48};
    static final int[] PLOT_PRICE = {0, 2000, 8000, 25000};
    static final int HOLE = 11, PLAZA = 34;

    private final EemiAbysse plugin;
    private final NamespacedKey buildKey;
    private final Map<String, Long> emptySince = new HashMap<>();
    private TextDisplay board;

    Worlds(EemiAbysse plugin) {
        this.plugin = plugin;
        buildKey = new NamespacedKey(plugin, "hub_build");
    }

    World hub() {
        return Bukkit.getWorld(HUB);
    }

    World base() {
        return Bukkit.getWorld(BASE);
    }

    static boolean isAbysse(World w) {
        return w != null && (w.getName().equals(HUB) || w.getName().equals(BASE) || Gen.strata(w) != null);
    }

    Location hubSpawn() {
        World w = hub();
        return new Location(w, 0.5, 65, 30.5, 180, 0);
    }

    void setup() {
        World hub = Bukkit.getWorld(HUB);
        if (hub == null) hub = new WorldCreator(HUB).generator(new Gen.VoidGen(0.5, 65, 30.5)).createWorld();
        World base = Bukkit.getWorld(BASE);
        if (base == null) base = new WorldCreator(BASE).generator(new Gen.VoidGen(0.5, 64, 0.5)).createWorld();
        for (World w : new World[]{hub, base}) {
            if (w == null) continue;
            rules(w);
            w.setGameRule(GameRules.ADVANCE_TIME, false);
            w.setTime(w == hub ? 18000 : 6000);
        }
        if (hub != null) {
            Integer v = hub.getPersistentDataContainer().get(buildKey, PersistentDataType.INTEGER);
            if (plugin.getConfig().getBoolean("hub.build", true) && (v == null || v != HUB_VERSION)) buildHub(hub);
        }
    }

    private void rules(World w) {
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.PVP, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
    }

    /** Monde de la strate, chargé (ou créé) si besoin. */
    /** La strate, chargée (et régénérée si son dossier a été supprimé) ; null pendant sa remise à neuf. */
    World strata(int n) {
        if (plugin.reset.busy.contains(n)) return null;
        return load(n);
    }

    World load(int n) {
        Gen.Strata s = Gen.strata(n);
        if (s == null) return null;
        World w = Bukkit.getWorld(s.world());
        if (w != null) return w;
        long t = System.currentTimeMillis();
        w = new WorldCreator(s.world()).generator(new Gen.StrataGen(s)).createWorld();
        if (w == null) {
            plugin.getLogger().severe("Impossible de charger la strate " + n);
            return null;
        }
        rules(w);
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setTime(18000);
        plugin.machines.restoreDrills(w);
        plugin.getLogger().info("Strate " + n + " (" + s.name + ") chargee en " + (System.currentTimeMillis() - t) + " ms.");
        return w;
    }

    /** Décharge les strates vides depuis 5 min (chaque minute). */
    void unloadIdle() {
        long now = System.currentTimeMillis();
        for (Gen.Strata s : Gen.STRATAS) {
            World w = Bukkit.getWorld(s.world());
            if (w == null) {
                emptySince.remove(s.world());
                continue;
            }
            if (!w.getPlayers().isEmpty() || plugin.mobs.fightIn(s.n)) {
                emptySince.remove(s.world());
                continue;
            }
            long since = emptySince.computeIfAbsent(s.world(), k -> now);
            if (now - since > plugin.getConfig().getLong("strate-unload-minutes", 5) * 60_000L) {
                plugin.mobs.clearWorld(w);
                if (Bukkit.unloadWorld(w, true)) plugin.getLogger().info("Strate " + s.n + " dechargee (vide).");
                emptySince.remove(s.world());
            }
        }
    }

    // ------------------------------------------------------------------ Concessions

    static Location plotCenter(World base, int plot) {
        return new Location(base, (plot % 16) * PLOT_SPACING + 0.5, PLOT_Y + 1, (plot / 16) * PLOT_SPACING + 0.5);
    }

    int radius(Company c) {
        return PLOT_RADIUS[Math.max(0, Math.min(PLOT_RADIUS.length - 1, c.plotTier))];
    }

    boolean inPlot(Company c, Location l) {
        if (c == null || c.plot < 0 || l.getWorld() == null || !l.getWorld().getName().equals(BASE)) return false;
        Location ctr = plotCenter(l.getWorld(), c.plot);
        int r = radius(c);
        return Math.abs(l.getBlockX() - ctr.getBlockX()) <= r && Math.abs(l.getBlockZ() - ctr.getBlockZ()) <= r
                && l.getBlockY() >= 40 && l.getBlockY() <= 200;
    }

    /** Compagnie dont la parcelle contient ce point, ou null. */
    Company plotAt(Location l) {
        int ix = Math.floorDiv(l.getBlockX() + PLOT_SPACING / 2, PLOT_SPACING), iz = Math.floorDiv(l.getBlockZ() + PLOT_SPACING / 2, PLOT_SPACING);
        if (ix < 0 || ix >= 16 || iz < 0) return null;
        Company c = plugin.store.companyByPlot(iz * 16 + ix);
        return c != null && inPlot(c, l) ? c : null;
    }

    /** Donne une parcelle à la Compagnie et construit son sol. */
    void claim(Company c) {
        if (c.plot >= 0) return;
        c.plot = plugin.store.nextPlot++;
        c.plotTier = 0;
        buildPlot(c, -1);
    }

    /** Agrandit le sol de la parcelle jusqu'au rayon actuel (sans toucher à ce qui était déjà là). */
    void buildPlot(Company c, int oldRadius) {
        World w = base();
        if (w == null) return;
        Location ctr = plotCenter(w, c.plot);
        int r = radius(c), cx = ctr.getBlockX(), cz = ctr.getBlockZ();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (oldRadius >= 0 && Math.abs(dx) <= oldRadius && Math.abs(dz) <= oldRadius) continue;
                int edge = r - Math.max(Math.abs(dx), Math.abs(dz));
                int depth = Math.min(8, 3 + edge);
                for (int y = PLOT_Y - depth; y <= PLOT_Y; y++) {
                    Material m = y == PLOT_Y ? Material.GRASS_BLOCK : y >= PLOT_Y - 3 ? Material.DIRT : Material.STONE;
                    w.getBlockAt(cx + dx, y, cz + dz).setType(m, false);
                }
            }
    }

    // ------------------------------------------------------------------ Bordgouffre

    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void buildHub(World w) {
        long t = System.currentTimeMillis();
        for (int x = -PLAZA - 2; x <= PLAZA + 2; x++)
            for (int z = -PLAZA - 2; z <= PLAZA + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= HOLE || r > PLAZA) continue;
                // falaise sous la place, plus profonde au bord du gouffre
                int bottom = (int) (64 - 8 - (PLAZA - r) * 2.2);
                for (int y = bottom; y < 64; y++) {
                    int h = Gen.hash3(x, y, z) % 10;
                    set(w, x, y, z, y < 40 ? Material.DEEPSLATE : h < 2 ? Material.TUFF : h < 3 ? Material.ANDESITE : Material.STONE);
                }
                Material floor;
                if (r <= HOLE + 2) floor = Material.POLISHED_DEEPSLATE;
                else if (Math.abs(r - 18) < 0.6 || Math.abs(r - 27) < 0.6) floor = Material.POLISHED_ANDESITE;
                else if (r > PLAZA - 3) floor = Gen.hash(x, z, 9) % 4 == 0 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK;
                else floor = (x + z & 1) == 0 ? Material.STONE_BRICKS : Material.CRACKED_STONE_BRICKS;
                set(w, x, 64, z, floor);
            }
        // garde-fou autour du gouffre, ouvert au sud pour sauter
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64;
            int x = (int) Math.round(Math.cos(a) * (HOLE + 1.2)), z = (int) Math.round(Math.sin(a) * (HOLE + 1.2));
            if (z > 8 && Math.abs(x) <= 2) continue;
            set(w, x, 65, z, Material.POLISHED_DEEPSLATE_WALL);
        }
        // monte-charge au nord
        int lz = -(HOLE + 3);
        set(w, 0, 65, lz, Material.BELL);
        w.getBlockAt(0, 65, lz).setBlockData(Gen.state("minecraft:bell[attachment=floor,facing=south]"), false);
        for (int dx : new int[]{-2, 2}) for (int y = 65; y <= 69; y++) set(w, dx, y, lz, Material.DARK_OAK_LOG);
        for (int dx = -2; dx <= 2; dx++) set(w, dx, 70, lz, Material.DARK_OAK_PLANKS);
        w.getBlockAt(0, 69, lz).setBlockData(Gen.state("minecraft:lantern[hanging=true]"), false);
        // échoppes des PNJ
        for (Npcs.Def d : Npcs.DEFS) {
            if (d.angle() < 0) continue;
            Location l = Npcs.position(w, d);
            stall(w, l.getBlockX(), l.getBlockZ(), d.roof());
        }
        // réverbères
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 31), z = (int) Math.round(Math.sin(a) * 31);
            set(w, x, 65, z, Material.DARK_OAK_FENCE);
            set(w, x, 66, z, Material.DARK_OAK_FENCE);
            set(w, x, 67, z, Material.LANTERN);
        }
        w.getPersistentDataContainer().set(buildKey, PersistentDataType.INTEGER, HUB_VERSION);
        plugin.getLogger().info("Bordgouffre construite en " + (System.currentTimeMillis() - t) + " ms.");
    }

    private void stall(World w, int cx, int cz, Material roof) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                set(w, cx + dx, 64, cz + dz, Material.SPRUCE_PLANKS);
                set(w, cx + dx, 68, cz + dz, roof);
            }
        for (int dx : new int[]{-2, 2})
            for (int dz : new int[]{-2, 2})
                for (int y = 65; y <= 67; y++) set(w, cx + dx, y, cz + dz, Material.SPRUCE_LOG);
        w.getBlockAt(cx, 67, cz).setBlockData(Gen.state("minecraft:lantern[hanging=true]"), false);
    }

    /** Tableau de la bourse (hologramme près de la Banquière), mis à jour toutes les 30 s. */
    void updateBoard() {
        World w = hub();
        if (w == null || !w.isChunkLoaded(0, 0)) return;
        Location l = Npcs.position(w, Npcs.def("odette")).add(0, 4.2, 0);
        if (board == null || !board.isValid()) {
            for (Entity e : w.getNearbyEntities(l, 2, 3, 2)) if (e instanceof TextDisplay) e.remove();
            board = w.spawn(l, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setLineWidth(260);
            });
        }
        board.text(Txt.c(plugin.bourse.board()));
    }

    void removeBoard() {
        if (board != null) board.remove();
    }

    /** Le gouffre : tomber sous y 40 à Bordgouffre = arriver en strate 1. Tomber de la Concession = y revenir. */
    void fall(Player p) {
        World w = p.getWorld();
        if (p.getLocation().getY() > 40) return;
        if (w.getName().equals(HUB)) {
            World s1 = strata(1);
            if (s1 == null) {
                p.setFallDistance(0);
                p.teleport(hubSpawn());
                p.sendActionBar(Txt.c("§eLes Galeries sont en train d'être remises à neuf, reviens dans un instant."));
                return;
            }
            p.setFallDistance(0);
            p.teleport(Gen.strata(1).camp(s1));
            p.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, Txt.c("§5Les Galeries"));
            p.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, Txt.c("§7Strate 1"));
            plugin.quests.onEnterStrata(p, 1);
        } else if (w.getName().equals(BASE) && p.getLocation().getY() < 0) {
            Company c = plugin.store.company(p);
            p.setFallDistance(0);
            p.teleport(c.plot >= 0 ? plotCenter(w, c.plot) : hubSpawn());
        }
    }

    /** Point d'apparition d'un joueur à sa Concession. */
    Location plotSpawn(Company c) {
        World w = base();
        Location l = plotCenter(w, c.plot);
        Block b = w.getHighestBlockAt(l.getBlockX(), l.getBlockZ());
        return new Location(w, l.getX(), Math.max(PLOT_Y + 1, b.getY() + 1), l.getZ());
    }
}
