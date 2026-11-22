package fr.eemicraft.noel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Snow;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Le lobby en mode Noël, sans rien perdre (même principe qu'EemiHalloween) :
 * - /noel build : neige, cadeaux (seuls ou en tas), bougies, houx, petits sapins, lanternes suspendues (plafonds,
 *   avant-toits, arbres), guirlandes sur les clôtures et les toits, couronnes au-dessus des portes, sucres d'orge le
 *   long des allées, bonhommes de neige en blocs ; posés UNIQUEMENT dans l'air. Chaque bloc touché est noté (avec son
 *   état d'origine) dans placed.yml ; /noel remove remet tout comme avant.
 * - /noel sapin : remplace la fontaine (volume autour du joueur, tout noté dans sapin.yml) par un grand sapin ;
 *   /noel sapin remove remet la fontaine à l'identique (eau en dernier).
 * - /noel maison : chalet du Père Noël autour du joueur, sapin décoré, cadeaux, traîneau ; le Père Noël s'y installe.
 *   /noel perenoel note la position du Père Noël. Depuis la 1.2.0, le Père Noël du lobby est un PNJ FancyNpcs (géré à part,
 *   commande player_command noel cadeau) : le villageois du plugin n'apparaît que si santa.villager vaut true.
 * - /noel cadeau : cadeau du Père Noël (pièces EemiCore, une fois par joueur et par saison, voir Cadeau).
 * - /noel on|off : soirée côté client, neige qui tombe, clochettes, bonhommes de neige, Père Noël, titre à l'arrivée,
 *   titres de Noël de la boutique EemiCore (voir Saison).
 * - /noel music on|off : playlist de Noël du lobby (EemiMusique).
 */
public final class EemiNoel extends JavaPlugin implements Listener {

    private static final String TAG = "eeminoel";
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Set<Material> GROUND = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MYCELIUM, Material.DIRT_PATH);
    private static final Material[] GIFTS = {Material.RED_WOOL, Material.GREEN_WOOL, Material.WHITE_WOOL,
            Material.RED_CONCRETE, Material.GREEN_CONCRETE, Material.WHITE_CONCRETE};
    private static final Material[] RIBBONS = {Material.RED_CARPET, Material.YELLOW_CARPET, Material.WHITE_CARPET,
            Material.GREEN_CARPET};
    private static final Material[] CANDLES = {Material.RED_CANDLE, Material.GREEN_CANDLE, Material.WHITE_CANDLE};
    private static final Material[] LIGHTS = {Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT};
    private static final Material[] BAUBLES = {Material.RED_CONCRETE, Material.YELLOW_CONCRETE, Material.BLUE_CONCRETE,
            Material.RED_WOOL, Material.WHITE_WOOL, Material.RED_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.SEA_LANTERN, Material.SEA_LANTERN};
    private static final Material[] GARLAND_GOLD = {Material.GLOWSTONE, Material.OCHRE_FROGLIGHT, Material.SHROOMLIGHT};
    private static final Material[] GARLAND_COLOR = {Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT,
            Material.SEA_LANTERN};
    private static final Sound[] BELLS = {Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_AMETHYST_BLOCK_CHIME};
    private static final String[] SANTA_LINES = {"Ho ho ho ! Joyeux Noël !", "Tu as été sage cette année ?",
            "Les lutins ont bien travaillé... ho ho ho !", "Joyeuses fêtes sur EEMICRAFT !", "Mes rennes se reposent, va donc jouer !"};
    private static final List<String> DEFAULT_PATHS = List.of("dirt_path", "gravel", "cobblestone", "mossy_cobblestone",
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "polished_andesite", "andesite", "smooth_stone",
            "stone", "bricks", "mud_bricks", "packed_mud", "polished_deepslate", "deepslate_bricks", "sandstone",
            "smooth_sandstone", "tuff_bricks", "polished_tuff");

    /** Bloc touché par le plugin : type posé et état d'origine (null = air). */
    private record Placed(int x, int y, int z, Material type, String original) {}

    /** Sapin posé à la place de la fontaine : volume enregistré (cylindre). */
    private record Sapin(String world, int cx, int cy, int cz, int radius, int minY, int maxY) {}

    private final List<Placed> placed = new ArrayList<>();
    private final Map<UUID, Long> nextBell = new HashMap<>();
    private final Map<String, List<int[]>> spaced = new HashMap<>();
    private Set<Material> paths = Set.of();
    private File placedFile, stateFile, sapinFile;
    private boolean ambiance, decorBuilt, houseBuilt, building;
    private int tick;
    private Villager santa;
    private Sapin sapin;
    private Cadeau cadeau;
    private Traineau traineau;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        placedFile = new File(getDataFolder(), "placed.yml");
        stateFile = new File(getDataFolder(), "state.yml");
        sapinFile = new File(getDataFolder(), "sapin.yml");
        for (String s : YamlConfiguration.loadConfiguration(placedFile).getStringList("blocks")) {
            String[] p = s.split(" ");
            try {
                placed.add(new Placed(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Material.valueOf(p[3]), p.length > 4 ? p[4] : null));
            } catch (RuntimeException ignored) {
            }
        }
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        ambiance = st.getBoolean("ambiance", false);
        decorBuilt = st.getBoolean("decor", !placed.isEmpty());
        houseBuilt = st.getBoolean("house", false);
        loadSapin();
        getServer().getPluginManager().registerEvents(this, this);
        cadeau = new Cadeau(this);
        getServer().getPluginManager().registerEvents(cadeau, this);
        traineau = new Traineau(this);
        getServer().getPluginManager().registerEvents(traineau, this);
        // le Père Noël du lobby est maintenant un PNJ FancyNpcs : on retire un éventuel villageois restant
        if (!getConfig().getBoolean("santa.villager", false))
            Bukkit.getScheduler().runTaskLater(this, this::removeSanta, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickAmbiance, 40L, 10L);
        // titres de saison de la boutique EemiCore : suivent l'ambiance (1 s après le démarrage, puis toutes les 10 s)
        Bukkit.getScheduler().runTaskTimer(this, () -> Saison.applyShop(this, ambiance), 20L, 200L);
        getLogger().info("Décor : " + placed.size() + " blocs. Sapin " + (sapin != null ? "posé" : "non posé")
                + ". Ambiance " + (ambiance ? "activée." : "coupée."));
    }

    @Override
    public void onDisable() {
        if (cadeau != null) cadeau.shutdown();
        if (traineau != null) traineau.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
        removeEntities();
    }

    private World world() {
        return Bukkit.getWorld(getConfig().getString("world", "lobby"));
    }

    private void saveState() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("ambiance", ambiance);
        y.set("decor", decorBuilt);
        y.set("house", houseBuilt);
        try {
            y.save(stateFile);
        } catch (IOException e) {
            getLogger().warning("Impossible d'écrire state.yml : " + e.getMessage());
        }
    }

    private void savePlaced() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (Placed p : placed)
            l.add(p.x() + " " + p.y() + " " + p.z() + " " + p.type().name() + (p.original() == null ? "" : " " + p.original()));
        y.set("blocks", l);
        try {
            y.save(placedFile);
        } catch (IOException e) {
            getLogger().warning("Impossible d'écrire placed.yml : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ pose réversible

    /** Pose dans l'air (ou à la place d'une plante remplaçable), en notant l'état d'origine. */
    private boolean put(Block b, BlockData data) {
        Material t = b.getType();
        if (!t.isAir() && !(b.isReplaceable() && !b.isLiquid())) return false;
        String original = t.isAir() ? null : b.getBlockData().getAsString();
        b.setBlockData(data, false);
        placed.add(new Placed(b.getX(), b.getY(), b.getZ(), data.getMaterial(), original));
        return true;
    }

    private boolean put(Block b, Material m) {
        return put(b, m.createBlockData());
    }

    /** Remplace un bloc existant (lanterne -> lanterne en cuivre), en notant l'état d'origine. */
    private void swap(Block b, BlockData data) {
        String original = b.getBlockData().getAsString();
        b.setBlockData(data, false);
        placed.add(new Placed(b.getX(), b.getY(), b.getZ(), data.getMaterial(), original));
    }

    private int removeAll() {
        World w = world();
        if (w == null) return 0;
        int n = 0;
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placed p = placed.get(i);
            Block b = w.getBlockAt(p.x(), p.y(), p.z());
            if (b.getType() != p.type()) continue;
            if (p.original() == null) b.setType(Material.AIR, false);
            else {
                try {
                    b.setBlockData(Bukkit.createBlockData(p.original()), false);
                } catch (IllegalArgumentException e) {
                    b.setType(Material.AIR, false);
                }
            }
            n++;
        }
        placed.clear();
        savePlaced();
        if (houseBuilt) {
            // le chalet disparaît : le Père Noël ne reste pas dans le vide
            getConfig().set("santa.enabled", false);
            saveConfig();
        }
        decorBuilt = false;
        houseBuilt = false;
        saveState();
        removeSanta();
        return n;
    }

    // ------------------------------------------------------------------ blocs utiles

    private static BlockData snow(int layers) {
        Snow s = (Snow) Material.SNOW.createBlockData();
        s.setLayers(layers);
        return s;
    }

    private static BlockData candle(Material m, int count) {
        Candle c = (Candle) m.createBlockData();
        c.setCandles(count);
        c.setLit(true);
        return c;
    }

    private static BlockData leaves() {
        Leaves l = (Leaves) Material.SPRUCE_LEAVES.createBlockData();
        l.setPersistent(true);
        return l;
    }

    private static BlockData holly() {
        Ageable a = (Ageable) Material.SWEET_BERRY_BUSH.createBlockData();
        a.setAge(Math.min(3, a.getMaximumAge()));
        return a;
    }

    private static BlockData hangingLantern() {
        Lantern l = (Lantern) Material.LANTERN.createBlockData();
        l.setHanging(true);
        return l;
    }

    private static boolean isLight(Material m) {
        return m == Material.LANTERN || m == Material.SOUL_LANTERN || Tag.CANDLES.isTagged(m);
    }

    /** Une lumière (lanterne, bougie) est déjà posée juste à côté (pour espacer les guirlandes). */
    private static boolean lightNextTo(Block b) {
        for (BlockFace f : SIDES) if (isLight(b.getRelative(f).getType())) return true;
        return false;
    }

    /** Cadeau : cube coloré + bougie allumée dessus. */
    private boolean gift(Block b, ThreadLocalRandom r) {
        if (!b.getRelative(BlockFace.UP).getType().isAir()) return false;
        if (!put(b, GIFTS[r.nextInt(GIFTS.length)])) return false;
        put(b.getRelative(BlockFace.UP), candle(CANDLES[r.nextInt(CANDLES.length)], 1));
        return true;
    }

    /** Tas de 3 à 6 cadeaux autour de b (certains empilés), ruban (tapis) ou bougie sur le dessus. */
    private boolean giftPile(Block b, ThreadLocalRandom r) {
        if (!b.getType().isAir() || !b.getRelative(0, 1, 0).getType().isAir() || !b.getRelative(0, 2, 0).getType().isAir())
            return false;
        int want = 3 + r.nextInt(4), n = 0;
        List<int[]> spots = new ArrayList<>(List.of(new int[]{0, 0}, new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1},
                new int[]{0, -1}, new int[]{1, 1}, new int[]{-1, -1}, new int[]{1, -1}, new int[]{-1, 1}));
        for (int i = 1; i < spots.size(); i++) {
            int j = 1 + r.nextInt(spots.size() - 1);
            int[] t = spots.get(i);
            spots.set(i, spots.get(j));
            spots.set(j, t);
        }
        for (int[] s : spots) {
            if (n >= want) break;
            Block g = b.getRelative(s[0], 0, s[1]);
            if (nearKeepClear(g.getX(), g.getZ())) continue;
            Block under = g.getRelative(BlockFace.DOWN);
            if (!g.getType().isAir() || !(solid(under) || GROUND.contains(under.getType()))) continue;
            if (!g.getRelative(0, 1, 0).getType().isAir()) continue;
            if (!put(g, GIFTS[r.nextInt(GIFTS.length)])) continue;
            n++;
            Block top = g.getRelative(BlockFace.UP);
            if (r.nextDouble() < 0.35 && top.getRelative(BlockFace.UP).getType().isAir()
                    && put(top, GIFTS[r.nextInt(GIFTS.length)])) top = top.getRelative(BlockFace.UP);
            if (r.nextDouble() < 0.6) put(top, RIBBONS[r.nextInt(RIBBONS.length)]);
            else put(top, candle(CANDLES[r.nextInt(CANDLES.length)], 1));
        }
        return n > 0;
    }

    /** Petit sapin enneigé (tronc de 3, feuilles en cône, neige au sommet). b = case d'air au-dessus du sol. */
    private boolean smallTree(Block b) {
        if (!airColumn(b.getRelative(0, 5, 0), 5)) return false; // b..b+4 libres
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 1; dy <= 2; dy++)
                    if (!b.getRelative(dx, dy, dz).getType().isAir()) return false;
        for (int dy = 0; dy <= 2; dy++) put(b.getRelative(0, dy, 0), Material.SPRUCE_LOG);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (dx != 0 || dz != 0) put(b.getRelative(dx, 1, dz), leaves());
        for (BlockFace f : SIDES) put(b.getRelative(f).getRelative(BlockFace.UP, 2), leaves());
        put(b.getRelative(0, 3, 0), leaves());
        put(b.getRelative(0, 4, 0), snow(1));
        return true;
    }

    /** Sucre d'orge : pilier rouge et blanc de 3-4 blocs, crosse tournée vers {@code hook}. */
    private boolean candyCane(Block b, BlockFace hook, ThreadLocalRandom r) {
        int h = 3 + r.nextInt(2);
        List<Block> cells = new ArrayList<>();
        for (int dy = 0; dy <= h; dy++) cells.add(b.getRelative(0, dy, 0));
        Block side = b.getRelative(hook);
        cells.add(side.getRelative(0, h, 0));
        cells.add(side.getRelative(0, h - 1, 0));
        for (Block c : cells) if (!c.getType().isAir()) return false;
        if (!side.getRelative(0, h - 2, 0).getType().isAir() || nearKeepClear(side.getX(), side.getZ())) return false;
        for (int i = 0; i < cells.size(); i++) put(cells.get(i), i % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
        return true;
    }

    /** Bonhomme de neige en blocs : deux blocs de neige, tête en citrouille sculptée, chapeau noir. */
    private boolean blockSnowman(Block b, ThreadLocalRandom r) {
        for (int dy = 0; dy <= 3; dy++) if (!b.getRelative(0, dy, 0).getType().isAir()) return false;
        put(b, Material.SNOW_BLOCK);
        put(b.getRelative(0, 1, 0), Material.SNOW_BLOCK);
        BlockData head = Material.CARVED_PUMPKIN.createBlockData();
        ((Directional) head).setFacing(SIDES[r.nextInt(SIDES.length)]);
        put(b.getRelative(0, 2, 0), head);
        put(b.getRelative(0, 3, 0), Material.BLACK_CARPET);
        return true;
    }

    /** Couronne au-dessus d'une porte (b = moitié haute) : trois blocs de feuilles contre le mur, bougie rouge au milieu. */
    private boolean wreath(Block b) {
        if (!(b.getBlockData() instanceof Door d) || d.getHalf() != Bisected.Half.TOP) return false;
        Block wall = b.getRelative(BlockFace.UP);
        if (!wall.getType().isSolid()) return false;
        boolean any = false;
        for (BlockFace f : new BlockFace[]{d.getFacing(), d.getFacing().getOppositeFace()}) {
            Block spot = wall.getRelative(f);
            if (!spot.getType().isAir() || !b.getRelative(f).getType().isAir() || nearKeepClear(spot.getX(), spot.getZ()))
                continue;
            if (!put(spot, leaves())) continue;
            any = true;
            BlockFace left = switch (f) {
                case NORTH -> BlockFace.WEST;
                case SOUTH -> BlockFace.EAST;
                case EAST -> BlockFace.NORTH;
                default -> BlockFace.SOUTH;
            };
            for (BlockFace s : new BlockFace[]{left, left.getOppositeFace()}) {
                Block side = spot.getRelative(s);
                if (side.getType().isAir() && wall.getRelative(s).getType().isSolid()) put(side, leaves());
            }
            Block bow = spot.getRelative(BlockFace.UP);
            if (bow.getType().isAir()) put(bow, candle(Material.RED_CANDLE, 1));
        }
        return any;
    }

    // ------------------------------------------------------------------ décor du lobby

    private boolean solid(Block b) {
        return b.getType().isOccluding();
    }

    private boolean nearKeepClear(int x, int z) {
        double r = getConfig().getDouble("keep-clear-radius", 3);
        for (String s : getConfig().getStringList("keep-clear")) {
            String[] p = s.trim().split("\\s+");
            if (p.length < 2) continue;
            double dx = x + 0.5 - Double.parseDouble(p[0]), dz = z + 0.5 - Double.parseDouble(p[1]);
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** Les {@code below} blocs sous b sont de l'air. */
    private boolean airColumn(Block b, int below) {
        for (int i = 1; i <= below; i++) if (!b.getRelative(0, -i, 0).getType().isAir()) return false;
        return true;
    }

    private double chance(String k) {
        return getConfig().getDouble("chance." + k);
    }

    /** Aucun objet du même genre à moins de "espacement.<kind>" blocs (et le note s'il est posé ensuite). */
    private boolean farFrom(String kind, int x, int z) {
        int d = getConfig().getInt("espacement." + kind, 6);
        for (int[] p : spaced.getOrDefault(kind, List.of()))
            if ((p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z) < d * d) return false;
        return true;
    }

    private void mark(String kind, int x, int z) {
        spaced.computeIfAbsent(kind, k -> new ArrayList<>()).add(new int[]{x, z});
    }

    /** Côté du bloc de sol où se trouve une allée praticable (même niveau, air au-dessus), sinon null. */
    private BlockFace pathSide(Block ground) {
        for (BlockFace f : SIDES) {
            Block n = ground.getRelative(f);
            if (paths.contains(n.getType()) && n.getRelative(0, 1, 0).getType().isAir() && n.getRelative(0, 2, 0).getType().isAir())
                return f;
        }
        return null;
    }

    private void loadPaths() {
        List<String> l = getConfig().isList("allees") ? getConfig().getStringList("allees") : DEFAULT_PATHS;
        Set<Material> s = new HashSet<>();
        for (String n : l) {
            Material m = Material.matchMaterial(n.trim());
            if (m != null && m.isBlock()) s.add(m);
            else getLogger().warning("Allée : bloc inconnu « " + n + " » ignoré.");
        }
        paths = s;
    }

    private void build(CommandSender sender) {
        World w = world();
        if (w == null || building) return;
        building = true;
        spaced.clear();
        loadPaths();
        int minX = getConfig().getInt("area.min-x"), maxX = getConfig().getInt("area.max-x");
        int minZ = getConfig().getInt("area.min-z"), maxZ = getConfig().getInt("area.max-z");
        List<int[]> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) columns.add(new int[]{x, z});
        sender.sendMessage(prefix() + "§7Décoration de " + columns.size() + " colonnes...");
        buildStep(w, columns, 0, sender, new int[16]);
    }

    private void buildStep(World w, List<int[]> columns, int from, CommandSender sender, int[] c) {
        try {
            buildColumns(w, columns, from, sender, c);
        } catch (RuntimeException e) {
            building = false;
            decorBuilt = true; // ce qui est déjà posé doit pouvoir être retiré
            saveState();
            savePlaced();
            getLogger().warning("Décoration interrompue : " + e);
            sender.sendMessage(prefix() + "§cDécoration interrompue (" + e.getMessage() + "). §7/noel remove puis build.");
        }
    }

    private void buildColumns(World w, List<int[]> columns, int from, CommandSender sender, int[] c) {
        int minY = getConfig().getInt("area.min-y", 40), maxY = getConfig().getInt("area.max-y", 100);
        boolean lanterns = getConfig().getBoolean("replace-lanterns", false);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int end = Math.min(columns.size(), from + 200);
        for (int i = from; i < end; i++) {
            int x = columns.get(i)[0], z = columns.get(i)[1];
            boolean clear = nearKeepClear(x, z) || inSapin(w, x, z);
            for (int y = maxY; y >= minY; y--) {
                Block b = w.getBlockAt(x, y, z);
                Material t = b.getType();
                if (lanterns && t == Material.LANTERN) {
                    String s = b.getBlockData().getAsString();
                    if (s.startsWith("minecraft:lantern")) {
                        swap(b, Bukkit.createBlockData("minecraft:copper_lantern" + s.substring("minecraft:lantern".length())));
                        c[7]++;
                    }
                    continue;
                }
                if (Tag.DOORS.isTagged(t)) {
                    if (!clear && r.nextDouble() < chance("wreath") && wreath(b)) c[13]++;
                    continue;
                }
                if (!t.isAir() || clear) continue;
                Block above = b.getRelative(BlockFace.UP), below = b.getRelative(BlockFace.DOWN);
                Material bt = below.getType(), at = above.getType();
                if (GROUND.contains(bt)) {
                    BlockFace ps = pathSide(below);
                    if (ps != null && r.nextDouble() < chance("candy-cane") && farFrom("sucre-orge", x, z)
                            && candyCane(b, ps.getOppositeFace(), r)) {
                        mark("sucre-orge", x, z);
                        c[8]++;
                        continue;
                    }
                    if (ps == null && r.nextDouble() < chance("snowman-block") && farFrom("bonhomme", x, z) && blockSnowman(b, r)) {
                        mark("bonhomme", x, z);
                        c[9]++;
                        continue;
                    }
                    if (ps == null && r.nextDouble() < chance("gift-pile") && farFrom("tas-cadeaux", x, z) && giftPile(b, r)) {
                        mark("tas-cadeaux", x, z);
                        c[10]++;
                        continue;
                    }
                    double roll = r.nextDouble();
                    if (roll < chance("gift")) {
                        if (gift(b, r)) c[2]++;
                    } else if ((roll -= chance("gift")) < chance("candle")) {
                        if (at.isAir() && put(b, candle(CANDLES[r.nextInt(CANDLES.length)], 1 + r.nextInt(4)))) c[3]++;
                    } else if ((roll -= chance("candle")) < chance("holly")) {
                        if (at.isAir() && put(b, holly())) c[4]++;
                    } else if ((roll -= chance("holly")) < chance("tree")) {
                        if (smallTree(b)) c[5]++;
                    } else if (r.nextDouble() < chance("snow-ground")) {
                        if (put(b, snow(1 + r.nextInt(2)))) c[0]++;
                    }
                } else if (Tag.LEAVES.isTagged(bt)) {
                    if (r.nextDouble() < chance("snow-leaves") && put(b, snow(1 + r.nextInt(2)))) c[1]++;
                } else if (Tag.FENCES.isTagged(bt) || Tag.WALLS.isTagged(bt)) {
                    // guirlande sur les clôtures et murets : lanterne ou bougies sur un poteau sur deux environ
                    if (r.nextDouble() < chance("fence-light") && !lightNextTo(b)
                            && put(b, r.nextDouble() < 0.4 ? Material.LANTERN.createBlockData()
                            : candle(CANDLES[r.nextInt(CANDLES.length)], 1 + r.nextInt(3)))) c[11]++;
                } else if ((Tag.STAIRS.isTagged(bt) || Tag.SLABS.isTagged(bt)) && at.isAir()) {
                    // bougies le long des toits
                    if (r.nextDouble() < chance("roof-candle") && !lightNextTo(b)
                            && put(b, candle(CANDLES[r.nextInt(CANDLES.length)], 1 + r.nextInt(2)))) c[12]++;
                } else if (Tag.LEAVES.isTagged(at) && airColumn(b, 2)) {
                    // lanternes suspendues aux arbres
                    if (r.nextDouble() < chance("tree-lantern") && !lightNextTo(b) && put(b, hangingLantern())) c[14]++;
                } else if ((Tag.STAIRS.isTagged(at) || Tag.SLABS.isTagged(at)) && airColumn(b, 2)) {
                    // guirlande de lanternes sous les avant-toits
                    if (r.nextDouble() < chance("roof-lantern") && !lightNextTo(b) && put(b, hangingLantern())) c[12]++;
                } else if (solid(above) && airColumn(b, 4)) {
                    if (r.nextDouble() < chance("lantern") && put(b, hangingLantern())) c[6]++;
                }
            }
        }
        if (end < columns.size()) {
            Bukkit.getScheduler().runTaskLater(this, () -> buildStep(w, columns, end, sender, c), 1L);
            return;
        }
        building = false;
        decorBuilt = true;
        saveState();
        savePlaced();
        String msg = "Décor : " + c[0] + " couches de neige au sol, " + c[1] + " sur les arbres, " + c[2] + " cadeaux, "
                + c[10] + " tas de cadeaux, " + c[3] + " bougies, " + c[4] + " houx, " + c[5] + " petits sapins, "
                + c[8] + " sucres d'orge, " + c[9] + " bonhommes de neige, " + c[13] + " couronnes, "
                + c[11] + " lumières sur les clôtures, " + c[12] + " lumières sur les toits, " + c[14]
                + " lanternes dans les arbres, " + c[6] + " lanternes suspendues ; " + c[7] + " lanternes passées en cuivre.";
        getLogger().info(msg);
        sender.sendMessage(prefix() + "§a" + msg);
    }

    // ------------------------------------------------------------------ grand sapin à la place de la fontaine

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static boolean inDisc(int dx, int dz, int r) {
        return dx * dx + dz * dz <= r * r + r;
    }

    private boolean inSapin(World w, int x, int z) {
        return sapin != null && w.getName().equals(sapin.world()) && inDisc(x - sapin.cx(), z - sapin.cz(), sapin.radius());
    }

    private void loadSapin() {
        sapin = null;
        if (!sapinFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(sapinFile);
        String[] c = y.getString("center", "").split(" ");
        if (c.length < 3) return;
        try {
            sapin = new Sapin(y.getString("world", "lobby"), Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]),
                    y.getInt("radius"), y.getInt("min-y"), y.getInt("max-y"));
        } catch (NumberFormatException e) {
            getLogger().warning("sapin.yml illisible : " + e.getMessage());
        }
    }

    private static boolean liquidLike(Material m) {
        return m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS;
    }

    private void set(Block b, BlockData d) {
        b.setBlockData(d, false);
    }

    private void set(Block b, Material m) {
        b.setBlockData(m.createBlockData(), false);
    }

    private void buildSapin(Player p, boolean confirm) {
        World w = p.getWorld();
        Location loc = p.getLocation();
        int cx = loc.getBlockX(), cy = loc.getBlockY(), cz = loc.getBlockZ();
        int R = Math.max(3, Math.min(15, getConfig().getInt("sapin.rayon-fontaine", 7)));
        int minY = cy + Math.min(0, getConfig().getInt("sapin.enregistrer-depuis", -3));
        int cutY = Math.max(minY, cy + getConfig().getInt("sapin.retirer-depuis", -1));
        int maxY = Math.min(w.getMaxHeight() - 1, cy + Math.max(16, getConfig().getInt("sapin.hauteur-volume", 30)));
        minY = Math.max(w.getMinHeight(), minY);

        // décor du /noel build dans le volume : on reprend son état d'origine (et il sort de placed.yml)
        Map<Long, Placed> decor = new HashMap<>();
        if (w.equals(world())) for (Placed pl : placed) decor.put(key(pl.x(), pl.y(), pl.z()), pl);
        Set<Placed> absorbed = new HashSet<>();
        List<String> blocks = new ArrayList<>();
        int tiles = 0;
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                if (!inDisc(dx, dz, R)) continue;
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    String data = b.getType().isAir() ? null : b.getBlockData().getAsString();
                    Placed pl = decor.get(key(b.getX(), y, b.getZ()));
                    if (pl != null && pl.type() == b.getType()) {
                        data = pl.original();
                        absorbed.add(pl);
                    }
                    if (data != null) blocks.add(b.getX() + " " + y + " " + b.getZ() + " " + data);
                    if (y >= cutY && b.getState(false) instanceof TileState) tiles++;
                }
            }
        if (tiles > 0 && !confirm) {
            p.sendMessage(prefix() + "§e" + tiles + " bloc(s) à données (panneau, tête, coffre...) dans la fontaine : leur "
                    + "forme sera remise, mais pas leur contenu (texte, skin, objets). §7Pour continuer : §f/noel sapin confirmer");
            return;
        }
        // on note TOUT avant de toucher au monde
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", w.getName());
        y.set("center", cx + " " + cy + " " + cz);
        y.set("radius", R);
        y.set("min-y", minY);
        y.set("max-y", maxY);
        y.set("blocks", blocks);
        try {
            y.save(sapinFile);
        } catch (IOException e) {
            p.sendMessage(prefix() + "§cImpossible d'écrire sapin.yml (" + e.getMessage() + ") : rien n'a été touché.");
            return;
        }
        if (!absorbed.isEmpty()) {
            placed.removeAll(absorbed);
            savePlaced();
        }
        sapin = new Sapin(w.getName(), cx, cy, cz, R, minY, maxY);

        // 1. on retire la fontaine (tout ce qui n'est pas de l'air, à partir de cutY)
        int removed = 0;
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                if (!inDisc(dx, dz, R)) continue;
                for (int yy = cutY; yy <= maxY; yy++) {
                    Block b = w.getBlockAt(cx + dx, yy, cz + dz);
                    if (!b.getType().isAir()) {
                        set(b, Material.AIR);
                        removed++;
                    }
                }
            }
        // 2. on construit le sapin
        int built = buildTree(w, cx, cy, cz, R, cutY, maxY);
        p.sendMessage(prefix() + "§aFontaine retirée (" + removed + " blocs, tout est noté dans sapin.yml) et grand sapin posé ("
                + built + " blocs). §7Pour remettre la fontaine : §f/noel sapin remove");
        getLogger().info("Sapin posé en " + cx + " " + cy + " " + cz + " (rayon " + R + ", " + removed + " blocs retirés, "
                + blocks.size() + " blocs notés).");
    }

    /** Grand sapin centré sur (cx, cz), pied du tronc en cy. Renvoie le nombre de blocs posés. */
    private int buildTree(World w, int cx, int cy, int cz, int R, int cutY, int maxY) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = 0;
        // sol enneigé (seulement si la couche sous les pieds a été retirée)
        if (cutY <= cy - 1) {
            Material floor = Material.matchMaterial(getConfig().getString("sapin.sol", "snow_block"));
            if (floor == null || !floor.isBlock()) floor = Material.SNOW_BLOCK;
            for (int dx = -R; dx <= R; dx++)
                for (int dz = -R; dz <= R; dz++)
                    if (inDisc(dx, dz, R)) {
                        Block b = w.getBlockAt(cx + dx, cy - 1, cz + dz);
                        if (b.getType().isAir()) {
                            set(b, floor);
                            n++;
                        }
                    }
        }
        int H = Math.max(10, Math.min(maxY - cy - 5, getConfig().getInt("sapin.hauteur", 23)));
        int Rt = Math.max(2, Math.min(R - 1, getConfig().getInt("sapin.rayon", 6)));
        double baubleChance = getConfig().getDouble("sapin.boules", 0.12);
        int leafStart = cy + 3, top = cy + H - 1;
        // tronc
        for (int y = cy; y < top; y++) {
            set(w.getBlockAt(cx, y, cz), Material.SPRUCE_LOG);
            n++;
        }
        // feuillage en étages (cône qui se resserre, bords irréguliers), boules de couleur sur le pourtour
        int tier = 4;
        for (int y = leafStart; y <= top; y++) {
            double rad = radiusAt(y, leafStart, top, Rt, tier);
            for (int dx = -Rt - 1; dx <= Rt + 1; dx++)
                for (int dz = -Rt - 1; dz <= Rt + 1; dz++) {
                    if (dx == 0 && dz == 0 && y < top) continue; // tronc
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double edge = rad + (r.nextDouble() - 0.55) * 0.9;
                    if (d > edge || !inDisc(dx, dz, R)) continue;
                    boolean outer = d > edge - 1.3;
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    set(b, outer && r.nextDouble() < baubleChance ? BAUBLES[r.nextInt(BAUBLES.length)].createBlockData() : leaves());
                    n++;
                }
        }
        // deux guirlandes lumineuses en spirale (dorée et multicolore), décalées d'un demi-tour
        double pitch = 5.0;
        for (int g = 0; g < 2; g++) {
            Material[] pal = g == 0 ? GARLAND_GOLD : GARLAND_COLOR;
            long last = Long.MIN_VALUE;
            int idx = 0;
            for (double th = 0; ; th += 0.05) {
                double yy = leafStart + 1 + th / (2 * Math.PI) * pitch;
                if (yy > top - 2) break;
                int y = (int) Math.floor(yy);
                double rad = radiusAt(y, leafStart, top, Rt, tier) + 0.2;
                double a = th + g * Math.PI;
                int dx = (int) Math.round(Math.cos(a) * rad), dz = (int) Math.round(Math.sin(a) * rad);
                if (dx == 0 && dz == 0) continue;
                long k = key(cx + dx, y, cz + dz);
                if (k == last) continue;
                last = k;
                set(w.getBlockAt(cx + dx, y, cz + dz), pal[(idx++ / 2) % pal.length]);
                n++;
            }
        }
        // neige et bougies sur le dessus des étages
        for (int y = leafStart; y <= top; y++)
            for (int dx = -Rt - 1; dx <= Rt + 1; dx++)
                for (int dz = -Rt - 1; dz <= Rt + 1; dz++) {
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    if (b.getType() != Material.SPRUCE_LEAVES) continue;
                    Block up = b.getRelative(BlockFace.UP);
                    if (!up.getType().isAir() || up.getY() > top) continue;
                    double roll = r.nextDouble();
                    if (roll < 0.07) set(up, candle(r.nextBoolean() ? Material.RED_CANDLE : Material.WHITE_CANDLE, 1 + r.nextInt(2)));
                    else if (roll < 0.40) set(up, snow(1 + r.nextInt(2)));
                    else continue;
                    n++;
                }
        // grande étoile dorée au sommet : croix en relief dans les deux sens, cœur lumineux, lanterne dessus
        int sy = top + 1;
        for (int k = 0; k <= 4; k++) set(w.getBlockAt(cx, sy + k, cz), k == 2 ? Material.GLOWSTONE : Material.GOLD_BLOCK);
        for (int s = -2; s <= 2; s++) {
            if (s == 0) continue;
            set(w.getBlockAt(cx + s, sy + 2, cz), Material.GOLD_BLOCK);
            set(w.getBlockAt(cx, sy + 2, cz + s), Material.GOLD_BLOCK);
        }
        for (int s : new int[]{-1, 1})
            for (int v : new int[]{1, 3}) {
                set(w.getBlockAt(cx + s, sy + v, cz), Material.GOLD_BLOCK);
                set(w.getBlockAt(cx, sy + v, cz + s), Material.GOLD_BLOCK);
            }
        set(w.getBlockAt(cx, sy + 5, cz), Material.LANTERN);
        n += 5 + 8 + 8 + 1;
        // pile de cadeaux au pied, puis tapis de neige
        int gifts = getConfig().getInt("sapin.cadeaux", 14);
        for (int i = 0, tries = 0; i < gifts && tries < gifts * 10; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 1.5 + r.nextDouble() * Math.max(0.5, Math.min(Rt, R - 1) - 1.5);
            int dx = (int) Math.round(Math.cos(a) * d), dz = (int) Math.round(Math.sin(a) * d);
            Block b = w.getBlockAt(cx + dx, cy, cz + dz);
            if ((dx == 0 && dz == 0) || !b.getType().isAir() || !inDisc(dx, dz, R)) continue;
            set(b, GIFTS[r.nextInt(GIFTS.length)]);
            Block t = b.getRelative(BlockFace.UP);
            if (r.nextDouble() < 0.35 && t.getType().isAir() && t.getRelative(BlockFace.UP).getType().isAir()) {
                set(t, GIFTS[r.nextInt(GIFTS.length)]);
                t = t.getRelative(BlockFace.UP);
                n++;
            }
            if (t.getType().isAir()) {
                set(t, r.nextDouble() < 0.7 ? RIBBONS[r.nextInt(RIBBONS.length)].createBlockData()
                        : candle(CANDLES[r.nextInt(CANDLES.length)], 1));
                n++;
            }
            n++;
            i++;
        }
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                if (!inDisc(dx, dz, R)) continue;
                Block b = w.getBlockAt(cx + dx, cy, cz + dz);
                if (b.getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    set(b, snow(1 + r.nextInt(2)));
                    n++;
                }
            }
        return n;
    }

    /** Rayon du feuillage à la hauteur y : cône qui se resserre, chaque étage plus large en bas qu'en haut. */
    private static double radiusAt(int y, int leafStart, int top, int Rt, int tier) {
        double t = (y - leafStart) / (double) Math.max(1, top - leafStart);
        int pos = (y - leafStart) % tier;
        return Rt * (1 - t) * (1 - 0.3 * pos / (tier - 1.0)) + 0.6;
    }

    /** Remet la fontaine exactement comme elle était (eau et plantes aquatiques en dernier). Renvoie le nombre de blocs. */
    private int restoreSapin() {
        if (sapin == null) return -1;
        World w = Bukkit.getWorld(sapin.world());
        if (w == null) return -1;
        Map<Long, String> orig = new HashMap<>();
        for (String s : YamlConfiguration.loadConfiguration(sapinFile).getStringList("blocks")) {
            String[] p = s.split(" ", 4);
            if (p.length < 4) continue;
            try {
                orig.put(key(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), p[3]);
            } catch (NumberFormatException ignored) {
            }
        }
        int R = sapin.radius(), n = 0, bad = 0;
        List<Object[]> liquids = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                if (!inDisc(dx, dz, R)) continue;
                for (int y = sapin.minY(); y <= sapin.maxY(); y++) {
                    Block b = w.getBlockAt(sapin.cx() + dx, y, sapin.cz() + dz);
                    String data = orig.get(key(b.getX(), y, b.getZ()));
                    if (data == null) {
                        if (!b.getType().isAir()) {
                            set(b, Material.AIR);
                            n++;
                        }
                        continue;
                    }
                    BlockData bd;
                    try {
                        bd = Bukkit.createBlockData(data);
                    } catch (IllegalArgumentException e) {
                        bad++;
                        continue;
                    }
                    if (liquidLike(bd.getMaterial())) {
                        if (!b.getType().isAir()) set(b, Material.AIR);
                        liquids.add(new Object[]{b, bd});
                    } else if (!b.getBlockData().equals(bd)) {
                        set(b, bd);
                        n++;
                    }
                }
            }
        for (Object[] l : liquids) {
            set((Block) l[0], (BlockData) l[1]);
            n++;
        }
        if (bad > 0) getLogger().warning(bad + " état(s) de bloc illisible(s) dans sapin.yml, laissés tels quels.");
        try {
            Files.move(sapinFile.toPath(), new File(getDataFolder(), "sapin-retire.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Impossible de renommer sapin.yml : " + e.getMessage());
        }
        sapin = null;
        return n;
    }

    // ------------------------------------------------------------------ chalet du Père Noël

    /** Bloc aux coordonnées locales du chalet (lx vers la droite, lz vers l'avant/la porte, ly vers le haut). */
    private Block at(Location o, BlockFace fwd, int lx, int ly, int lz) {
        BlockFace right = switch (fwd) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
        return o.getBlock().getRelative(right.getModX() * lx + fwd.getModX() * lz, ly, right.getModZ() * lx + fwd.getModZ() * lz);
    }

    private void buildHouse(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location o = p.getLocation();
        BlockFace fwd = p.getFacing();
        if (fwd != BlockFace.NORTH && fwd != BlockFace.SOUTH && fwd != BlockFace.EAST) fwd = BlockFace.WEST;
        int n = placed.size();
        // cheminée (avant le toit, qu'elle traverse) : foyer = four allumé, conduit en briques, feu de camp au sommet
        BlockData furnace = Material.FURNACE.createBlockData();
        ((Directional) furnace).setFacing(fwd);
        ((Lightable) furnace).setLit(true);
        put(at(o, fwd, 2, 0, -2), furnace);
        put(at(o, fwd, 1, 0, -2), Material.BRICKS);
        put(at(o, fwd, 3, 0, -2), Material.BRICKS);
        for (int ly = 1; ly <= 9; ly++) put(at(o, fwd, 2, ly, -2), Material.BRICKS);
        Campfire fire = (Campfire) Material.CAMPFIRE.createBlockData();
        fire.setLit(true);
        fire.setSignalFire(false);
        put(at(o, fwd, 2, 10, -2), fire);
        // porte (deux moitiés)
        for (Bisected.Half half : Bisected.Half.values()) {
            Door d = (Door) Material.SPRUCE_DOOR.createBlockData();
            d.setFacing(fwd);
            d.setHalf(half);
            put(at(o, fwd, 0, half == Bisected.Half.BOTTOM ? 0 : 1, 3), d);
        }
        // murs en planches d'épicéa, piliers en bûches, fenêtres
        for (int lx = -4; lx <= 4; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                boolean edgeX = Math.abs(lx) == 4, edgeZ = Math.abs(lz) == 3;
                if (!edgeX && !edgeZ) continue;
                boolean pillar = edgeX && edgeZ;
                for (int ly = 0; ly <= 3; ly++) {
                    if (!pillar && lz == 3 && lx == 0 && ly <= 1) continue; // porte
                    Block b = at(o, fwd, lx, ly, lz);
                    boolean window = !pillar && (ly == 1 || ly == 2)
                            && ((edgeX && Math.abs(lz) == 1) || (edgeZ && Math.abs(lx) == 2));
                    put(b, pillar ? Material.SPRUCE_LOG : window ? Material.GLASS : Material.SPRUCE_PLANKS);
                }
            }
        // toit en gradins (dépasse d'un bloc), pignons pleins, neige sur chaque marche
        for (int k = 0; k <= 5; k++) {
            int ly = 4 + k, side = 5 - k;
            for (int lz = -4; lz <= 4; lz++) {
                for (int s : side == 0 ? new int[]{0} : new int[]{-side, side}) {
                    put(at(o, fwd, s, ly, lz), Material.SPRUCE_PLANKS);
                    put(at(o, fwd, s, ly + 1, lz), snow(2));
                }
                if (Math.abs(lz) == 3) for (int lx = -(side - 1); lx <= side - 1; lx++) put(at(o, fwd, lx, ly, lz), Material.SPRUCE_PLANKS);
            }
        }
        // intérieur : tapis rouge, lanterne, cadeaux, bougies
        for (int lx = -1; lx <= 1; lx++) for (int lz = -1; lz <= 2; lz++) put(at(o, fwd, lx, 0, lz), Material.RED_CARPET);
        put(at(o, fwd, -2, 3, 0), Material.IRON_CHAIN);
        put(at(o, fwd, -2, 2, 0), hangingLantern());
        put(at(o, fwd, -3, 0, -2), Material.RED_WOOL);
        put(at(o, fwd, -3, 1, -2), candle(Material.WHITE_CANDLE, 1));
        put(at(o, fwd, -3, 0, -1), Material.GREEN_WOOL);
        put(at(o, fwd, -3, 1, -1), candle(Material.RED_CANDLE, 1));
        put(at(o, fwd, 3, 0, 2), candle(Material.RED_CANDLE, 3));
        // grand sapin décoré à gauche du chalet
        int tx = -7, tz = 1;
        for (int ly = 0; ly <= 5; ly++) put(at(o, fwd, tx, ly, tz), Material.SPRUCE_LOG);
        int[][] layers = {{1, 2}, {2, 2}, {3, 1}, {4, 1}, {5, 1}};
        for (int[] layer : layers) {
            int ly = layer[0], rad = layer[1];
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (rad == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue; // coins arrondis
                    if (rad == 1 && ly == 5 && dx != 0 && dz != 0) continue;
                    Block b = at(o, fwd, tx + dx, ly, tz + dz);
                    put(b, r.nextDouble() < 0.18 ? LIGHTS[r.nextInt(LIGHTS.length)].createBlockData() : leaves());
                }
        }
        put(at(o, fwd, tx, 6, tz), leaves());
        put(at(o, fwd, tx, 7, tz), Material.GLOWSTONE); // étoile
        // cadeaux au pied du sapin
        for (int[] g : new int[][]{{tx - 3, tz}, {tx + 3, tz}, {tx, tz + 3}, {tx - 2, tz + 2}, {tx + 2, tz - 2}}) {
            Block b = at(o, fwd, g[0], 0, g[1]);
            put(b, GIFTS[r.nextInt(GIFTS.length)]);
            put(b.getRelative(BlockFace.UP), candle(CANDLES[r.nextInt(CANDLES.length)], 1));
        }
        // traîneau devant à droite : caisse rouge, dossier, avant doré, patins, cadeaux dedans
        for (int lx = 3; lx <= 6; lx++)
            for (int lz = 6; lz <= 7; lz++) {
                put(at(o, fwd, lx, 0, lz), Material.RED_CONCRETE);
                if (lx == 3) put(at(o, fwd, lx, 1, lz), Material.RED_CONCRETE);
                if (lx == 6) put(at(o, fwd, lx, 1, lz), Material.GOLD_BLOCK);
            }
        put(at(o, fwd, 4, 1, 6), Material.GREEN_WOOL);
        put(at(o, fwd, 5, 1, 7), Material.WHITE_WOOL);
        put(at(o, fwd, 4, 1, 7), candle(Material.RED_CANDLE, 2));
        put(at(o, fwd, 7, 0, 6), Material.SPRUCE_FENCE);
        put(at(o, fwd, 7, 0, 7), Material.SPRUCE_FENCE);
        // lanternes de chaque côté de la porte
        put(at(o, fwd, -1, 0, 4), Material.LANTERN);
        put(at(o, fwd, 1, 0, 4), Material.LANTERN);
        // le Père Noël s'installe au milieu, face à la porte
        Location sl = at(o, fwd, 0, 0, -1).getLocation().add(0.5, 0, 0.5);
        sl.setDirection(new org.bukkit.util.Vector(fwd.getModX(), 0, fwd.getModZ()));
        setSantaLocation(sl);
        houseBuilt = true;
        saveState();
        savePlaced();
        p.sendMessage(prefix() + "§aChalet du Père Noël posé (" + (placed.size() - n) + " blocs), porte vers " + fwd.name().toLowerCase()
                + ". Le Père Noël s'y installe.");
    }

    // ------------------------------------------------------------------ Père Noël

    private void setSantaLocation(Location l) {
        getConfig().set("santa.x", Math.round(l.getX() * 100) / 100.0);
        getConfig().set("santa.y", Math.round(l.getY() * 100) / 100.0);
        getConfig().set("santa.z", Math.round(l.getZ() * 100) / 100.0);
        getConfig().set("santa.yaw", Math.round(l.getYaw() * 10) / 10.0);
        getConfig().set("santa.enabled", true);
        saveConfig();
        removeSanta();
    }

    private Location santaLocation(World w) {
        if (!getConfig().getBoolean("santa.enabled", false)) return null;
        return new Location(w, getConfig().getDouble("santa.x"), getConfig().getDouble("santa.y"), getConfig().getDouble("santa.z"),
                (float) getConfig().getDouble("santa.yaw"), 0);
    }

    private void keepSanta(World w) {
        if (!getConfig().getBoolean("santa.villager", false)) {
            if (santa != null) removeSanta();
            return;
        }
        Location l = santaLocation(w);
        if (l == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        if (santa != null && santa.isValid()) return;
        santa = w.spawn(l, Villager.class, e -> {
            e.addScoreboardTag(TAG);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setAI(false);
            e.setInvulnerable(true);
            e.setCanPickupItems(false);
            e.setVillagerType(Villager.Type.SNOW);
            e.customName(Component.text("Père Noël", NamedTextColor.RED).decorate(TextDecoration.BOLD));
            e.setCustomNameVisible(true);
        });
    }

    private void animateSanta() {
        if (santa == null || !santa.isValid()) return;
        Player near = null;
        double best = 24 * 24;
        for (Player p : santa.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(santa.getLocation());
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near == null) return;
        // il suit le joueur le plus proche du regard
        Location look = santa.getLocation().clone();
        look.setDirection(near.getEyeLocation().toVector().subtract(santa.getEyeLocation().toVector()));
        santa.setRotation(look.getYaw(), look.getPitch());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (tick % 12 == 0 && r.nextDouble() < 0.5) {
            Location at = santa.getLocation();
            santa.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 2.2, 0), 25, 0.5, 0.4, 0.5, 0.01);
            santa.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.NEUTRAL, 0.8f, 0.8f + r.nextFloat() * 0.6f);
        }
    }

    private void removeSanta() {
        if (santa != null) santa.remove();
        santa = null;
        World w = world();
        if (w != null) for (Entity e : w.getEntities()) if (e instanceof Villager && e.getScoreboardTags().contains(TAG)) e.remove();
    }

    // ------------------------------------------------------------------ ambiance

    private void setAmbiance(boolean on) {
        ambiance = on;
        saveState();
        if (traineau != null) {
            if (on) traineau.scheduleNext();
            else traineau.stopAll();
        }
        if (!on) {
            for (Player p : Bukkit.getOnlinePlayers()) p.resetPlayerTime();
            removeEntities();
        }
    }

    /** Préfixe des messages (messages.prefixe, & ou § acceptés). */
    String prefix() {
        return msg("prefixe", "§8[§aNoël§8] §7");
    }

    /**
     * Texte réglable messages.&lt;key&gt; du config (défaut dans le code), codes & traduits en §,
     * {cle} remplacés par les paires (cle, valeur) données.
     */
    String msg(String key, String def, Object... kv) {
        String s = getConfig().getString("messages." + key, def);
        if (s == null) s = def;
        s = s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        return s;
    }

    boolean ambianceOn() {
        return ambiance;
    }

    boolean isInLobby(Player p) {
        return inLobby(p);
    }

    private boolean inLobby(Player p) {
        World w = world();
        return w != null && p.getWorld().equals(w);
    }

    private void tickAmbiance() {
        tick++;
        if (!ambiance) return;
        World w = world();
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean night = getConfig().getBoolean("night", true);
        long time = getConfig().getLong("night-time", 13000);
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inLobby(p)) continue;
            any = true;
            if (night && (p.isPlayerTimeRelative() || p.getPlayerTime() % 24000 != time)) p.setPlayerTime(time, false);
            if (getConfig().getBoolean("particles", true)) {
                p.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 6, 0), 30, 10, 3, 10, 0);
                p.spawnParticle(Particle.WHITE_ASH, p.getLocation().add(0, 4, 0), 20, 8, 3, 8, 0);
            }
            // clochettes de temps en temps
            long at = nextBell.computeIfAbsent(p.getUniqueId(), k -> now + 8_000);
            if (now >= at) {
                int min = getConfig().getInt("bells.min-seconds", 20), max = Math.max(min + 1, getConfig().getInt("bells.max-seconds", 45));
                nextBell.put(p.getUniqueId(), now + r.nextInt(min, max) * 1000L);
                Sound s = BELLS[r.nextInt(BELLS.length)];
                double ang = r.nextDouble() * Math.PI * 2;
                Location from = p.getLocation().add(Math.cos(ang) * 6, 2, Math.sin(ang) * 6);
                float base = 0.9f + r.nextFloat() * 0.6f;
                // petit motif de trois notes
                for (int i = 0; i < 3; i++) {
                    float pitch = Math.min(2f, base + (i == 1 ? 0.25f : 0f));
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (p.isOnline() && inLobby(p)) p.playSound(from, s, SoundCategory.AMBIENT, 0.6f, pitch);
                    }, i * 4L);
                }
            }
        }
        if (!any) return;
        if (tick % 4 == 0) keepSanta(w);
        animateSanta();
        if (tick % 10 == 0) keepSnowmen(w);
    }

    private void keepSnowmen(World w) {
        int want = getConfig().getInt("snowmen", 6);
        int minX = getConfig().getInt("area.min-x"), maxX = getConfig().getInt("area.max-x");
        int minZ = getConfig().getInt("area.min-z"), maxZ = getConfig().getInt("area.max-z");
        int count = 0;
        for (Snowman s : w.getEntitiesByClass(Snowman.class)) {
            if (!s.getScoreboardTags().contains(TAG)) continue;
            Location l = s.getLocation();
            if (count >= want || l.getX() < minX || l.getX() > maxX || l.getZ() < minZ || l.getZ() > maxZ) s.remove();
            else count++;
        }
        if (count >= want) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = r.nextInt(minX, maxX + 1), z = r.nextInt(minZ, maxZ + 1);
        if (!w.isChunkLoaded(x >> 4, z >> 4) || nearKeepClear(x, z) || inSapin(w, x, z)) return;
        int hy = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block top = w.getBlockAt(x, hy, z);
        double y;
        if (top.getType() == Material.SNOW && GROUND.contains(top.getRelative(BlockFace.DOWN).getType()))
            y = hy + ((Snow) top.getBlockData()).getLayers() * 0.125;
        else if (GROUND.contains(top.getType()) && top.getRelative(BlockFace.UP).getType() == Material.SNOW)
            y = hy + 1 + ((Snow) top.getRelative(BlockFace.UP).getBlockData()).getLayers() * 0.125;
        else if (GROUND.contains(top.getType())) y = hy + 1;
        else return; // pas au sol (toit, dalle, eau...) : on retentera ailleurs
        Location l = new Location(w, x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0);
        if (!l.getBlock().getRelative(BlockFace.UP).getType().isAir() && l.getBlock().getRelative(BlockFace.UP).getType() != Material.SNOW) return;
        w.spawn(l, Snowman.class, s -> {
            s.addScoreboardTag(TAG);
            s.setPersistent(false);
            s.setRemoveWhenFarAway(false);
            s.setAI(false);
            s.setInvulnerable(true);
            s.setDerp(false);
        });
    }

    private void removeEntities() {
        removeSanta();
        World w = world();
        if (w == null) return;
        for (Entity e : w.getEntities()) if (e instanceof Snowman && e.getScoreboardTags().contains(TAG)) e.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent e) {
        // WorldGuard (mob-spawning deny au lobby) annule les apparitions : on rétablit les nôtres
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSnowTrail(EntityBlockFormEvent e) {
        // nos bonhommes de neige ne laissent pas de neige au sol
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true); // ni commerce avec le Père Noël, ni tonte des bonhommes de neige
        if (!(e.getRightClicked() instanceof Villager)) return;
        Player p = e.getPlayer();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        p.playSound(e.getRightClicked().getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.NEUTRAL, 1f, 1.2f);
        p.playSound(e.getRightClicked().getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 0.8f, 0.7f);
        p.spawnParticle(Particle.SNOWFLAKE, e.getRightClicked().getLocation().add(0, 2, 0), 15, 0.4, 0.3, 0.4, 0.01);
        p.sendMessage(Component.text("Père Noël ", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text(": « " + SANTA_LINES[r.nextInt(SANTA_LINES.length)] + " »", NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!ambiance || !getConfig().getBoolean("join-title", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inLobby(p)) {
                p.showTitle(Title.title(Component.text("Joyeux Noël", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("et bonnes fêtes sur EEMICRAFT !", NamedTextColor.GREEN),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, 0.8f, 1.2f);
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        nextBell.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!inLobby(e.getPlayer())) e.getPlayer().resetPlayerTime();
    }

    // ------------------------------------------------------------------ état musique / boutique

    private String musicState() {
        Plugin mus = Bukkit.getPluginManager().getPlugin("EemiMusique");
        if (mus == null) return "§cEemiMusique absent";
        File f = new File(mus.getDataFolder(), "songs.yml");
        if (!f.exists()) return "§7musique de base (pas de songs.yml)";
        try {
            String first = Files.readAllLines(f.toPath()).stream().findFirst().orElse("");
            if (first.startsWith("# Playlist Noel")) return "§aplaylist de Noël (songs.yml)";
            if (first.startsWith("# Playlist")) return "§eautre playlist de saison (songs.yml)";
        } catch (IOException | RuntimeException ignored) {
        }
        return "§eplaylist perso (songs.yml)";
    }

    private String shopState() {
        if (!getConfig().getBoolean("boutique.enabled", true)) return "§7désactivée";
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        if (core == null || !core.isEnabled()) return "§cEemiCore absent";
        return ambiance ? "§atitres de Noël en vente" : "§ctitres de Noël retirés";
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("cadeau")) {
            cadeau.command(sender, args);
            return true;
        }
        if (!sender.hasPermission("eeminoel.admin")) {
            sender.sendMessage(prefix() + "§cPermission refusée.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "build" -> {
                if (decorBuilt) {
                    sender.sendMessage(prefix() + "§cDécor déjà posé. §7/noel remove (retire tout, chalet et sapin compris) puis build.");
                    return true;
                }
                if (building) {
                    sender.sendMessage(prefix() + "§cDécoration déjà en cours.");
                    return true;
                }
                build(sender);
            }
            case "sapin" -> {
                String m = args.length > 1 ? args[1].toLowerCase() : "";
                if (m.equals("remove") || m.equals("retirer")) {
                    if (building) {
                        sender.sendMessage(prefix() + "§cDécoration en cours, réessaie dans un instant.");
                        return true;
                    }
                    int n = restoreSapin();
                    sender.sendMessage(n < 0 ? prefix() + "§cAucun sapin posé (ou monde introuvable)."
                            : prefix() + "§aSapin retiré, fontaine remise comme avant (" + n + " blocs).");
                    return true;
                }
                if (!(sender instanceof Player p) || !inLobby(p)) {
                    sender.sendMessage(prefix() + "§cÀ faire en jeu, dans le lobby, debout au centre de la fontaine.");
                    return true;
                }
                if (sapin != null) {
                    sender.sendMessage(prefix() + "§cUn sapin est déjà posé. §7/noel sapin remove pour remettre la fontaine.");
                    return true;
                }
                if (building) {
                    sender.sendMessage(prefix() + "§cDécoration en cours, réessaie dans un instant.");
                    return true;
                }
                buildSapin(p, m.equals("confirmer") || m.equals("confirm"));
            }
            case "maison" -> {
                if (!(sender instanceof Player p) || !inLobby(p)) {
                    sender.sendMessage(prefix() + "§cÀ faire en jeu, dans le lobby, là où doit être le chalet.");
                    return true;
                }
                if (houseBuilt) {
                    sender.sendMessage(prefix() + "§cChalet déjà posé. §7/noel remove pour tout retirer.");
                    return true;
                }
                buildHouse(p);
            }
            case "perenoel" -> {
                if (!(sender instanceof Player p) || !inLobby(p)) {
                    sender.sendMessage(prefix() + "§cÀ faire en jeu, dans le lobby.");
                    return true;
                }
                setSantaLocation(p.getLocation());
                sender.sendMessage(prefix() + (getConfig().getBoolean("santa.villager", false)
                        ? "§aLe Père Noël (villageois) apparaîtra ici (ambiance activée)."
                        : "§aPosition notée. §7Le Père Noël du lobby est un PNJ FancyNpcs (/npc), le villageois du plugin "
                        + "est coupé (santa.villager: false)."));
            }
            case "remove" -> {
                if (building) {
                    sender.sendMessage(prefix() + "§cDécoration en cours, réessaie dans un instant.");
                    return true;
                }
                int n = removeAll();
                int s = restoreSapin();
                sender.sendMessage(prefix() + "§a" + n + " blocs retirés ou remis comme avant"
                        + (s >= 0 ? " ; sapin retiré, fontaine remise (" + s + " blocs)." : "."));
            }
            case "on" -> {
                setAmbiance(true);
                int n = Saison.applyShop(this, true);
                sender.sendMessage(prefix() + "§aAmbiance activée (soirée, neige, clochettes, bonhommes de neige, Père Noël). "
                        + n + " cosmétique(s) de Noël remis en boutique.");
            }
            case "off" -> {
                setAmbiance(false);
                int n = Saison.applyShop(this, false);
                sender.sendMessage(prefix() + "§7Ambiance coupée. " + n + " cosmétique(s) de Noël retiré(s) de la boutique.");
            }
            case "music", "musique" -> {
                String m = args.length > 1 ? args[1].toLowerCase() : "";
                if (m.equals("on")) {
                    if (Saison.musicOn(this, "songs_noel.yml", sender))
                        sender.sendMessage(prefix() + "§aPlaylist de Noël au lobby.");
                } else if (m.equals("off")) {
                    if (Saison.musicOff(sender)) sender.sendMessage(prefix() + "§7Musique de base du lobby remise.");
                } else sender.sendMessage(prefix() + "§7/noel music on|off");
            }
            case "traineau", "traîneau" -> {
                boolean now = args.length > 1 && (args[1].equalsIgnoreCase("maintenant") || args[1].equalsIgnoreCase("now"));
                if (!now) {
                    String err = traineau.announceLaunch(sender);
                    if (err != null) sender.sendMessage(prefix() + "§c" + err);
                    else sender.sendMessage(prefix() + "§aAnnonce envoyée au lobby : le traîneau décolle dans "
                            + traineau.announceSeconds() + " s. §7(/noel traineau maintenant = sans annonce)");
                    return true;
                }
                String err;
                try {
                    err = traineau.start(sender);
                } catch (RuntimeException e) {
                    err = "erreur (" + e.getMessage() + ")";
                    getLogger().warning("Traîneau : " + e);
                }
                if (err != null) sender.sendMessage(prefix() + "§c" + err);
                else sender.sendMessage(prefix() + "§aLe traîneau du Père Noël décolle !"
                        + (ambiance ? "" : " §7(ambiance coupée : pas de passage automatique)"));
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(prefix() + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(prefix() + "§7Blocs touchés : §f" + placed.size() + "§7 (décor " + (decorBuilt ? "§aoui" : "§cnon")
                    + "§7, chalet " + (houseBuilt ? "§aoui" : "§cnon") + "§7, sapin " + (sapin != null ? "§aoui" : "§cnon")
                    + "§7), ambiance : " + (ambiance ? "§aactivée" : "§ccoupée")
                    + "§7, boutique : " + shopState() + "§7, musique : " + musicState()
                    + "§7, traîneau : " + traineau.status()
                    + "§7. §f/noel build|sapin [remove]|maison|perenoel|remove|on|off|music on|off|traineau [maintenant]|cadeau [reset <joueur>|liste]|reload");
        }
        return true;
    }
}
