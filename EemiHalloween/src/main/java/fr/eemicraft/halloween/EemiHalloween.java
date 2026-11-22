package fr.eemicraft.halloween;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Le lobby en mode Halloween, sans rien perdre :
 * - /halloween build : citrouilles, toiles d'araignée, lanternes des âmes, buissons morts, crânes, bougies, posés
 *   UNIQUEMENT dans l'air ; les lanternes existantes deviennent des lanternes des âmes. Chaque bloc touché est noté
 *   (avec son état d'origine) dans placed.yml ; /halloween remove remet tout comme avant.
 * - /halloween maison : maison en ruine (et tombes) autour du joueur, sorcière à l'intérieur ; /halloween sorciere
 *   déplace la sorcière à la position du joueur.
 * - /halloween on|off : nuit côté client, cendres, chauves-souris, tonnerre au hasard, sons inquiétants, passages
 *   d'obscurité, titre à l'arrivée.
 */
public final class EemiHalloween extends JavaPlugin implements Listener {

    private static final String PREFIX = "§6[§cHalloween§6] §r";
    private static final String TAG = "eemihalloween";
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** Les 16 orientations possibles d'un crâne posé au sol (pas UP/DOWN/SELF). */
    private static final BlockFace[] SKULL_FACES = {BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST, BlockFace.WEST,
            BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST};
    private static final Set<Material> GROUND = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.PODZOL, Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MYCELIUM, Material.DIRT_PATH);
    private static final Material[] WALL = {Material.MOSSY_COBBLESTONE, Material.COBBLESTONE, Material.CRACKED_STONE_BRICKS,
            Material.DARK_OAK_PLANKS, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS};
    private static final Sound[] SCARY = {Sound.AMBIENT_CAVE, Sound.ENTITY_GHAST_SCREAM, Sound.ENTITY_ZOMBIE_AMBIENT,
            Sound.BLOCK_WOODEN_DOOR_CLOSE, Sound.ENTITY_VEX_AMBIENT, Sound.ENTITY_WITCH_CELEBRATE,
            Sound.ENTITY_WARDEN_HEARTBEAT, Sound.BLOCK_CHAIN_BREAK, Sound.ENTITY_PHANTOM_AMBIENT};

    /** Bloc touché par le plugin : type posé et état d'origine (null = air). */
    private record Placed(int x, int y, int z, Material type, String original) {}

    private final List<Placed> placed = new ArrayList<>();
    private final Map<UUID, Long> nextScare = new HashMap<>();
    private File placedFile, stateFile;
    private boolean ambiance, decorBuilt, houseBuilt, building;
    private long nextThunder;
    private int tick;
    private Witch witch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        placedFile = new File(getDataFolder(), "placed.yml");
        stateFile = new File(getDataFolder(), "state.yml");
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
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickAmbiance, 40L, 10L);
        // titres de saison de la boutique EemiCore : suivent l'ambiance (rappel toutes les 10 s, voir Saison)
        Bukkit.getScheduler().runTaskTimer(this, () -> Saison.applyShop(this, ambiance), 20L, 200L);
        getLogger().info("Decor : " + placed.size() + " blocs. Ambiance " + (ambiance ? "activee." : "coupee."));
    }

    @Override
    public void onDisable() {
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
            getLogger().warning("Impossible d'ecrire state.yml : " + e.getMessage());
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
            getLogger().warning("Impossible d'ecrire placed.yml : " + e.getMessage());
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

    /** Remplace un bloc existant (lanterne -> lanterne des âmes), en notant l'état d'origine. */
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
        decorBuilt = false;
        houseBuilt = false;
        saveState();
        removeWitch();
        return n;
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

    private boolean airColumn(Block b, int below) {
        for (int i = 1; i <= below; i++) if (!b.getRelative(0, -i, 0).getType().isAir()) return false;
        return true;
    }

    private boolean corner(Block b) {
        for (int i = 0; i < 4; i++)
            if (solid(b.getRelative(SIDES[i])) && solid(b.getRelative(SIDES[(i + 1) % 4]))) return true;
        return false;
    }

    private double chance(String k) {
        return getConfig().getDouble("chance." + k);
    }

    private void build(CommandSender sender) {
        World w = world();
        if (w == null || building) return;
        building = true;
        int minX = getConfig().getInt("area.min-x"), maxX = getConfig().getInt("area.max-x");
        int minZ = getConfig().getInt("area.min-z"), maxZ = getConfig().getInt("area.max-z");
        List<int[]> columns = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) columns.add(new int[]{x, z});
        sender.sendMessage(PREFIX + "§7Decoration de " + columns.size() + " colonnes...");
        buildStep(w, columns, 0, sender, new int[8]);
    }

    private void buildStep(World w, List<int[]> columns, int from, CommandSender sender, int[] c) {
        try {
            buildColumns(w, columns, from, sender, c);
        } catch (RuntimeException e) {
            building = false;
            savePlaced();
            getLogger().warning("Decoration interrompue : " + e);
            sender.sendMessage(PREFIX + "§cDecoration interrompue (" + e.getMessage() + "). §7/halloween remove puis build.");
        }
    }

    private void buildColumns(World w, List<int[]> columns, int from, CommandSender sender, int[] c) {
        int minY = getConfig().getInt("area.min-y", 40), maxY = getConfig().getInt("area.max-y", 100);
        boolean lanterns = getConfig().getBoolean("replace-lanterns", true), torches = getConfig().getBoolean("replace-torches", false);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int end = Math.min(columns.size(), from + 200);
        for (int i = from; i < end; i++) {
            int x = columns.get(i)[0], z = columns.get(i)[1];
            boolean clear = nearKeepClear(x, z);
            for (int y = maxY; y >= minY; y--) {
                Block b = w.getBlockAt(x, y, z);
                Material t = b.getType();
                // lanternes (et torches si demandé) existantes -> version des âmes
                if (lanterns && t == Material.LANTERN) {
                    swap(b, Bukkit.createBlockData(b.getBlockData().getAsString().replace("minecraft:lantern", "minecraft:soul_lantern")));
                    c[6]++;
                    continue;
                }
                if (torches && (t == Material.TORCH || t == Material.WALL_TORCH)) {
                    swap(b, Bukkit.createBlockData(b.getBlockData().getAsString().replace("minecraft:", "minecraft:soul_")));
                    c[7]++;
                    continue;
                }
                if (!t.isAir() || clear) continue;
                Block above = b.getRelative(BlockFace.UP), below = b.getRelative(BlockFace.DOWN);
                if (GROUND.contains(below.getType()) && above.getType().isAir()) {
                    double roll = r.nextDouble();
                    if (roll < chance("pumpkin")) {
                        BlockData d = (r.nextBoolean() ? Material.JACK_O_LANTERN : Material.CARVED_PUMPKIN).createBlockData();
                        ((Directional) d).setFacing(SIDES[r.nextInt(4)]);
                        if (put(b, d)) c[0]++;
                    } else if ((roll -= chance("pumpkin")) < chance("dead-bush")) {
                        if (put(b, Material.DEAD_BUSH)) c[3]++;
                    } else if ((roll -= chance("dead-bush")) < chance("skull")) {
                        BlockData d = Material.SKELETON_SKULL.createBlockData();
                        ((Rotatable) d).setRotation(SKULL_FACES[r.nextInt(16)]);
                        if (put(b, d)) c[4]++;
                    } else if ((roll -= chance("skull")) < chance("candle")) {
                        Candle d = (Candle) Material.CANDLE.createBlockData();
                        d.setCandles(1 + r.nextInt(4));
                        d.setLit(true);
                        if (put(b, d)) c[5]++;
                    }
                } else if (solid(above) && corner(b) && airColumn(b, 2)) {
                    if (r.nextDouble() < chance("web-corner") && put(b, Material.COBWEB)) c[1]++;
                } else if (Tag.LEAVES.isTagged(above.getType()) && airColumn(b, 2)) {
                    if (r.nextDouble() < chance("web-leaves") && put(b, Material.COBWEB)) c[1]++;
                } else if (solid(above) && airColumn(b, 2)) {
                    double roll = r.nextDouble();
                    if (roll < chance("web-ceiling")) {
                        if (put(b, Material.COBWEB)) c[1]++;
                    } else if (roll - chance("web-ceiling") < chance("soul-lantern") && airColumn(b, 4)) {
                        Lantern d = (Lantern) Material.SOUL_LANTERN.createBlockData();
                        d.setHanging(true);
                        if (put(b, d)) c[2]++;
                    }
                } else if (solid(b.getRelative(SIDES[r.nextInt(4)])) && airColumn(b, 2) && !airColumn(b, 6)) {
                    // contre un mur, à hauteur de tête et plus
                    if (r.nextDouble() < chance("web-wall") && put(b, Material.COBWEB)) c[1]++;
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
        String msg = "Decor : " + c[0] + " citrouilles, " + c[1] + " toiles, " + c[2] + " lanternes des ames suspendues, "
                + c[3] + " buissons morts, " + c[4] + " cranes, " + c[5] + " bougies ; " + c[6] + " lanternes et "
                + c[7] + " torches passees aux ames.";
        getLogger().info(msg);
        sender.sendMessage(PREFIX + "§a" + msg);
    }

    // ------------------------------------------------------------------ maison en ruine

    /** Bloc aux coordonnées locales de la maison (lx vers la droite, lz vers l'avant/la porte, ly vers le haut). */
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
        // murs, piliers, ouvertures
        for (int lx = -4; lx <= 4; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                boolean edgeX = Math.abs(lx) == 4, edgeZ = Math.abs(lz) == 3;
                if (!edgeX && !edgeZ) continue;
                boolean pillar = edgeX && edgeZ;
                boolean collapsed = lx >= 2 && lz <= -1; // coin effondré (arrière droit)
                for (int ly = 0; ly <= (pillar ? 4 : 3); ly++) {
                    if (collapsed && ly >= 1 + (pillar ? 1 : 0)) continue;
                    if (!pillar && lz == 3 && lx == 0 && ly <= 1) continue; // porte
                    Block b = at(o, fwd, lx, ly, lz);
                    if (!pillar && ly == 2 && ((edgeX && lz == 0) || (lz == 3 && Math.abs(lx) == 2))) {
                        put(b, Material.COBWEB); // fenêtres pleines de toiles
                        continue;
                    }
                    if (!pillar && r.nextDouble() < 0.12 + ly * 0.1) continue; // trous
                    put(b, pillar ? Material.DARK_OAK_LOG : WALL[r.nextInt(WALL.length)]);
                }
            }
        // toit effondré
        for (int lx = -4; lx <= 4; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                if (lx >= 1 && lz <= 0) continue;
                if (r.nextDouble() < 0.4) continue;
                Slab s = (Slab) Material.DARK_OAK_SLAB.createBlockData();
                s.setType(Slab.Type.BOTTOM);
                put(at(o, fwd, lx, 4, lz), s);
                if (lz == 0 && r.nextDouble() < 0.5) put(at(o, fwd, lx, 5, lz), Material.DARK_OAK_PLANKS);
            }
        // intérieur
        put(at(o, fwd, -2, 0, -1), Material.CAULDRON);
        for (int[] cw : new int[][]{{-3, -2}, {3, 2}, {-3, 2}}) put(at(o, fwd, cw[0], 3, cw[1]), Material.COBWEB);
        BlockData jack = Material.JACK_O_LANTERN.createBlockData();
        ((Directional) jack).setFacing(fwd);
        put(at(o, fwd, -3, 0, 2), jack);
        BlockData skull = Material.SKELETON_SKULL.createBlockData();
        ((Rotatable) skull).setRotation(fwd);
        put(at(o, fwd, 3, 0, 1), skull);
        Candle candles = (Candle) Material.CANDLE.createBlockData();
        candles.setCandles(3);
        candles.setLit(true);
        put(at(o, fwd, 2, 0, 2), candles);
        put(at(o, fwd, 0, 3, 1), Material.IRON_CHAIN);
        Lantern lantern = (Lantern) Material.SOUL_LANTERN.createBlockData();
        lantern.setHanging(true);
        put(at(o, fwd, 0, 2, 1), lantern);
        // cimetière devant la porte, barrière cassée
        for (int[] g : new int[][]{{-3, 6}, {0, 7}, {3, 6}, {-1, 9}, {2, 9}}) {
            put(at(o, fwd, g[0], 0, g[1]), Material.MOSSY_STONE_BRICK_WALL);
            if (r.nextBoolean()) put(at(o, fwd, g[0], 1, g[1]), Material.SKELETON_SKULL);
        }
        for (int lx = -5; lx <= 5; lx++) if (r.nextDouble() > 0.35) put(at(o, fwd, lx, 0, 11), Material.DARK_OAK_FENCE);
        for (int i = 0; i < 6; i++) put(at(o, fwd, r.nextInt(-5, 6), 0, r.nextInt(4, 11)), Material.DEAD_BUSH);
        BlockData pump = Material.CARVED_PUMPKIN.createBlockData();
        ((Directional) pump).setFacing(fwd);
        put(at(o, fwd, -2, 0, 4), pump);
        put(at(o, fwd, 2, 0, 4), jack.clone());
        // la sorcière s'installe au milieu, face à la porte
        Location wl = at(o, fwd, 0, 0, -1).getLocation().add(0.5, 0, 0.5);
        wl.setDirection(new org.bukkit.util.Vector(fwd.getModX(), 0, fwd.getModZ()));
        setWitchLocation(wl);
        houseBuilt = true;
        saveState();
        savePlaced();
        p.sendMessage(PREFIX + "§aMaison en ruine posee (" + (placed.size() - n) + " blocs), porte vers " + fwd.name().toLowerCase()
                + ". La sorciere s'y installe.");
    }

    // ------------------------------------------------------------------ sorcière

    private void setWitchLocation(Location l) {
        getConfig().set("witch.x", Math.round(l.getX() * 100) / 100.0);
        getConfig().set("witch.y", Math.round(l.getY() * 100) / 100.0);
        getConfig().set("witch.z", Math.round(l.getZ() * 100) / 100.0);
        getConfig().set("witch.yaw", Math.round(l.getYaw() * 10) / 10.0);
        getConfig().set("witch.enabled", true);
        saveConfig();
        removeWitch();
    }

    private Location witchLocation(World w) {
        if (!getConfig().getBoolean("witch.enabled", false)) return null;
        return new Location(w, getConfig().getDouble("witch.x"), getConfig().getDouble("witch.y"), getConfig().getDouble("witch.z"),
                (float) getConfig().getDouble("witch.yaw"), 0);
    }

    private void keepWitch(World w) {
        Location l = witchLocation(w);
        if (l == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return;
        if (witch != null && witch.isValid()) return;
        witch = w.spawn(l, Witch.class, e -> {
            e.addScoreboardTag(TAG);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setAI(false);
            e.setInvulnerable(true);
            e.setCanPickupItems(false);
            e.customName(Component.text("La Sorciere", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
            e.setCustomNameVisible(true);
        });
    }

    private void animateWitch() {
        if (witch == null || !witch.isValid()) return;
        Player near = null;
        double best = 24 * 24;
        for (Player p : witch.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(witch.getLocation());
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near == null) return;
        // elle suit le joueur le plus proche du regard
        Location look = witch.getLocation().clone();
        look.setDirection(near.getEyeLocation().toVector().subtract(witch.getEyeLocation().toVector()));
        witch.setRotation(look.getYaw(), look.getPitch());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (tick % 12 == 0 && r.nextDouble() < 0.5) {
            Location at = witch.getLocation();
            if (r.nextBoolean()) {
                witch.getWorld().playSound(at, Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.8f + r.nextFloat() * 0.3f);
                witch.getWorld().spawnParticle(Particle.WITCH, at.clone().add(0, 2, 0), 20, 0.4, 0.4, 0.4, 0);
            } else {
                witch.setDrinkingPotion(new ItemStack(Material.POTION));
                witch.getWorld().playSound(at, Sound.ENTITY_WITCH_DRINK, SoundCategory.HOSTILE, 1f, 1f);
            }
        }
    }

    private void removeWitch() {
        if (witch != null) witch.remove();
        witch = null;
        World w = world();
        if (w != null) for (Entity e : w.getEntities()) if (e instanceof Witch && e.getScoreboardTags().contains(TAG)) e.remove();
    }

    // ------------------------------------------------------------------ ambiance

    private void setAmbiance(boolean on) {
        ambiance = on;
        saveState();
        if (!on) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.resetPlayerTime();
                p.removePotionEffect(PotionEffectType.DARKNESS);
            }
            removeEntities();
        }
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
        long time = getConfig().getLong("night-time", 18000);
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inLobby(p)) continue;
            any = true;
            if (night && (p.isPlayerTimeRelative() || p.getPlayerTime() % 24000 != time)) p.setPlayerTime(time, false);
            if (getConfig().getBoolean("particles", true)) {
                p.spawnParticle(Particle.ASH, p.getLocation().add(0, 3, 0), 25, 9, 3, 9, 0);
                if (r.nextInt(4) == 0) p.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 0.2, 0), 8, 4, 0.1, 4, 0.01);
            }
            // sons inquiétants autour du joueur, parfois un passage d'obscurité
            long at = nextScare.computeIfAbsent(p.getUniqueId(), k -> now + 10_000);
            if (now >= at) {
                int min = getConfig().getInt("scare.min-seconds", 15), max = Math.max(min + 1, getConfig().getInt("scare.max-seconds", 35));
                nextScare.put(p.getUniqueId(), now + r.nextInt(min, max) * 1000L);
                Sound s = SCARY[r.nextInt(SCARY.length)];
                double ang = r.nextDouble() * Math.PI * 2;
                Location from = p.getLocation().add(Math.cos(ang) * 7, 1, Math.sin(ang) * 7);
                float vol = s == Sound.ENTITY_GHAST_SCREAM ? 0.25f : 0.7f;
                p.playSound(from, s, SoundCategory.AMBIENT, vol, 0.6f + r.nextFloat() * 0.4f);
                if (r.nextDouble() < getConfig().getDouble("scare.darkness-chance", 0.15))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, false));
            }
        }
        if (!any) return;
        // tonnerre au hasard (effet seulement : ni dégâts ni feu)
        if (now >= nextThunder) {
            int min = getConfig().getInt("thunder.min-seconds", 30), max = Math.max(min + 1, getConfig().getInt("thunder.max-seconds", 90));
            if (nextThunder > 0 && getConfig().getBoolean("thunder.enabled", true)) {
                int x = r.nextInt(getConfig().getInt("area.min-x"), getConfig().getInt("area.max-x") + 1);
                int z = r.nextInt(getConfig().getInt("area.min-z"), getConfig().getInt("area.max-z") + 1);
                if (w.isChunkLoaded(x >> 4, z >> 4)) w.strikeLightningEffect(new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5));
            }
            nextThunder = now + r.nextInt(min, max) * 1000L;
        }
        if (tick % 4 == 0) keepWitch(w);
        animateWitch();
        if (tick % 10 == 0) keepBats(w);
    }

    private void keepBats(World w) {
        int want = getConfig().getInt("bats", 14);
        int minX = getConfig().getInt("area.min-x"), maxX = getConfig().getInt("area.max-x");
        int minZ = getConfig().getInt("area.min-z"), maxZ = getConfig().getInt("area.max-z");
        int count = 0;
        for (Bat b : w.getEntitiesByClass(Bat.class)) {
            if (!b.getScoreboardTags().contains(TAG)) continue;
            Location l = b.getLocation();
            if (l.getX() < minX || l.getX() > maxX || l.getZ() < minZ || l.getZ() > maxZ) b.remove();
            else count++;
        }
        if (count >= want) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int x = r.nextInt(minX, maxX + 1), z = r.nextInt(minZ, maxZ + 1);
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
        w.spawn(new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 4, z + 0.5), Bat.class, b -> {
            b.addScoreboardTag(TAG);
            b.setPersistent(false);
            b.setRemoveWhenFarAway(false);
            b.setAwake(true);
        });
    }

    private void removeEntities() {
        removeWitch();
        World w = world();
        if (w == null) return;
        for (Entity e : w.getEntities()) if (e instanceof Bat && e.getScoreboardTags().contains(TAG)) e.remove();
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

    @EventHandler
    public void onClickWitch(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        p.playSound(e.getRightClicked().getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1f, 0.7f);
        p.sendMessage(Component.text("La Sorciere ", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.text("ricane : « Joyeux Halloween... si tu survis ! »", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!ambiance || !getConfig().getBoolean("join-title", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inLobby(p)) {
                p.showTitle(Title.title(Component.text("Joyeux Halloween", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                        Component.text("si tu oses entrer...", NamedTextColor.DARK_PURPLE),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.AMBIENT, 0.8f, 0.7f);
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        nextScare.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!inLobby(e.getPlayer())) {
            e.getPlayer().resetPlayerTime();
            e.getPlayer().removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eemihalloween.admin")) {
            sender.sendMessage(PREFIX + "§cPermission refusee.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "build" -> {
                if (decorBuilt) {
                    sender.sendMessage(PREFIX + "§cDecor deja pose. §7/halloween remove (retire tout, maison comprise) puis build.");
                    return true;
                }
                build(sender);
            }
            case "maison" -> {
                if (!(sender instanceof Player p) || !inLobby(p)) {
                    sender.sendMessage(PREFIX + "§cA faire en jeu, dans le lobby, la ou doit etre la maison.");
                    return true;
                }
                if (houseBuilt) {
                    sender.sendMessage(PREFIX + "§cMaison deja posee. §7/halloween remove pour tout retirer.");
                    return true;
                }
                buildHouse(p);
            }
            case "sorciere" -> {
                if (!(sender instanceof Player p) || !inLobby(p)) {
                    sender.sendMessage(PREFIX + "§cA faire en jeu, dans le lobby.");
                    return true;
                }
                setWitchLocation(p.getLocation());
                sender.sendMessage(PREFIX + "§aLa sorciere apparaitra ici (ambiance activee).");
            }
            case "remove" -> sender.sendMessage(PREFIX + "§a" + removeAll() + " blocs retires ou remis comme avant.");
            case "on" -> {
                setAmbiance(true);
                int n = Saison.applyShop(this, true);
                sender.sendMessage(PREFIX + "§aAmbiance activee (nuit, cendres, chauves-souris, tonnerre, sons, sorciere). "
                        + n + " cosmetique(s) Halloween remis en boutique.");
            }
            case "off" -> {
                setAmbiance(false);
                int n = Saison.applyShop(this, false);
                sender.sendMessage(PREFIX + "§7Ambiance coupee. " + n + " cosmetique(s) Halloween retires de la boutique.");
            }
            case "music", "musique" -> {
                String m = args.length > 1 ? args[1].toLowerCase() : "";
                if (m.equals("on")) {
                    if (Saison.musicOn(this, "songs_halloween.yml", sender))
                        sender.sendMessage(PREFIX + "§aPlaylist Halloween au lobby.");
                } else if (m.equals("off")) {
                    if (Saison.musicOff(sender)) sender.sendMessage(PREFIX + "§7Musique de base du lobby remise.");
                } else sender.sendMessage(PREFIX + "§7/halloween music on|off");
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(PREFIX + "§aConfig rechargee.");
            }
            default -> sender.sendMessage(PREFIX + "§7Blocs touches : §f" + placed.size() + "§7 (decor " + (decorBuilt ? "§aoui" : "§cnon")
                    + "§7, maison " + (houseBuilt ? "§aoui" : "§cnon") + "§7), ambiance : " + (ambiance ? "§aactivee" : "§ccoupee")
                    + "§7. §f/halloween build|maison|sorciere|remove|on|off|music on|off");
        }
        return true;
    }
}
