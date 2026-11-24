package fr.eemicraft.tresor;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Coffre au tresor cache au hasard dans le lobby, toutes les N minutes. Le premier qui l'ouvre gagne. */
public final class EemiTresor extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final BlockFace[] FACINGS = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private Location chest;
    private long spawnedAt;
    private boolean hintSent;
    private long nextSpawnAt;
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        removeLeftover();
        nextSpawnAt = System.currentTimeMillis() + getConfig().getLong("first-delay-minutes", 10) * 60_000L;
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 400L, 400L);
        Bukkit.getScheduler().runTaskTimer(this, this::proximitySound, 40L,
                Math.max(5L, getConfig().getLong("sound.interval-ticks", 20)));
        getLogger().info("Premier coffre au tresor dans " + getConfig().getLong("first-delay-minutes", 10)
                + " min, puis toutes les " + getConfig().getLong("interval-minutes", 120) + " min.");
    }

    @Override
    public void onDisable() {
        removeChest(false);
    }

    // ------------------------------------------------------------------ cycle

    /** Son quand un joueur s'approche du coffre : plus aigu et plus fort a mesure qu'il se rapproche. */
    private void proximitySound() {
        if (chest == null) return;
        double radius = getConfig().getDouble("sound.radius", 10);
        Location at = chest.clone().add(0.5, 0.5, 0.5);
        for (Player p : chest.getWorld().getPlayers()) {
            double d = p.getLocation().distance(at);
            if (d > radius) continue;
            float closeness = (float) (1 - d / radius);
            p.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f + 0.6f * closeness, 0.6f + 1.2f * closeness);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (chest != null && !hintSent) {
            long hintAfter = getConfig().getLong("hint-after-minutes", 20) * 60_000L;
            if (hintAfter > 0 && now - spawnedAt >= hintAfter) {
                hintSent = true;
                broadcast(msg("hint").replace("%direction%", direction(chest)));
            }
        }
        if (now < nextSpawnAt) return;
        World world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        if (world == null || world.getPlayers().size() < getConfig().getInt("min-players", 1)) {
            nextSpawnAt = now + 5 * 60_000L;
            return;
        }
        if (chest != null) {
            removeChest(false);
            broadcast(msg("expired"));
        }
        if (spawnChest()) nextSpawnAt = now + getConfig().getLong("interval-minutes", 120) * 60_000L;
        else nextSpawnAt = now + 5 * 60_000L;
    }

    /** Cherche une cachette et y pose le coffre. */
    private boolean spawnChest() {
        Location spot = findSpot();
        if (spot == null) {
            getLogger().warning("Aucune cachette trouvee pour le coffre au tresor (chunks du lobby charges ?). Nouvel essai dans 5 min.");
            return false;
        }
        Block block = spot.getBlock();
        block.setType(Material.CHEST, false);
        if (block.getBlockData() instanceof Chest data) {
            data.setFacing(FACINGS[ThreadLocalRandom.current().nextInt(FACINGS.length)]);
            block.setBlockData(data, false);
        }
        chest = block.getLocation();
        spawnedAt = System.currentTimeMillis();
        hintSent = false;
        saveData();
        getLogger().info("Coffre au tresor pose en " + chest.getBlockX() + " " + chest.getBlockY() + " " + chest.getBlockZ() + ".");
        broadcast(msg("spawned"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        return true;
    }

    private Location findSpot() {
        World world = Bukkit.getWorld(getConfig().getString("world", "lobby"));
        if (world == null) return null;
        int cx = getConfig().getInt("center-x", 663), cz = getConfig().getInt("center-z", 650);
        int radius = getConfig().getInt("radius", 130);
        List<Chunk> chunks = new ArrayList<>();
        for (Chunk c : world.getLoadedChunks()) {
            int mx = (c.getX() << 4) + 8, mz = (c.getZ() << 4) + 8;
            if ((long) (mx - cx) * (mx - cx) + (long) (mz - cz) * (mz - cz) <= (long) (radius + 12) * (radius + 12)) chunks.add(c);
        }
        if (chunks.isEmpty()) return null;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 600; attempt++) {
            Chunk c = chunks.get(rnd.nextInt(chunks.size()));
            int x = (c.getX() << 4) + rnd.nextInt(16), z = (c.getZ() << 4) + rnd.nextInt(16);
            if ((long) (x - cx) * (x - cx) + (long) (z - cz) * (z - cz) > (long) radius * radius) continue;
            int sx = getConfig().getInt("spawn-x", 722), sz = getConfig().getInt("spawn-z", 650);
            int minD = getConfig().getInt("min-distance-from-spawn", 20);
            if ((long) (x - sx) * (x - sx) + (long) (z - sz) * (z - sz) < (long) minD * minD) continue;
            List<Integer> floors = new ArrayList<>();
            for (int y = getConfig().getInt("max-y", 120); y > getConfig().getInt("min-y", 40); y--) {
                if (standable(world, x, y, z) && onGround(world, x, y, z)) floors.add(y);
            }
            if (floors.isEmpty()) continue;
            int y = floors.get(rnd.nextInt(floors.size()));
            // Les 400 premiers essais exigent un abri (arbre, rebord...) ; ensuite le sol suffit.
            if (attempt < 400 && !covered(world, x, y, z)) continue;
            if (!reachesOpenAir(world, x, y, z)) continue;
            return new Location(world, x, y, z);
        }
        return null;
    }

    private static boolean passable(Block b) {
        return b.isPassable() && !b.isLiquid() && b.getType() != Material.LIGHT && b.getType() != Material.CHEST;
    }

    /** Case ou un joueur tient debout : pieds et tete libres, sol plein. */
    private static boolean standable(World w, int x, int y, int z) {
        Block feet = w.getBlockAt(x, y, z), head = w.getBlockAt(x, y + 1, z), floor = w.getBlockAt(x, y - 1, z);
        if (!feet.getType().isAir() || !passable(head)) return false;
        Material f = floor.getType();
        return f.isSolid() && f != Material.BARRIER && !Tag.LEAVES.isTagged(f) && !floor.isLiquid();
    }

    /** Au sol : bloc naturel (herbe, terre, sable...) sous le coffre, pas un toit ni un plancher de maison. */
    private boolean onGround(World w, int x, int y, int z) {
        Material floor = w.getBlockAt(x, y - 1, z).getType();
        List<String> ground = getConfig().getStringList("ground-blocks");
        return ground.contains(floor.name().toLowerCase(Locale.ROOT)) || ground.contains(floor.name());
    }

    /** Un toit (bloc plein ou feuilles) au-dessus du coffre : il est cache, pas en plein ciel. */
    private boolean covered(World w, int x, int y, int z) {
        int h = getConfig().getInt("cover-height", 12);
        for (int dy = 2; dy <= h; dy++) {
            Material m = w.getBlockAt(x, y + dy, z).getType();
            if (m.isOccluding() || Tag.LEAVES.isTagged(m)) return true;
        }
        return false;
    }

    /** Chemin a pied (marche de 1 bloc max) jusqu'a une case a l'air libre : le coffre n'est pas emmure. */
    private boolean reachesOpenAir(World w, int sx, int sy, int sz) {
        int max = getConfig().getInt("max-path", 60);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{sx, sy, sz, 0});
        seen.add(key(sx, sy, sz));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (!(c[0] == sx && c[1] == sy && c[2] == sz) && w.getHighestBlockYAt(c[0], c[2]) < c[1]) return true;
            if (c[3] >= max) continue;
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                if (!w.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                for (int dy = 1; dy >= -1; dy--) {
                    int ny = c[1] + dy;
                    if (dy == 1 && !passable(w.getBlockAt(c[0], c[1] + 2, c[2]))) continue;
                    if (!standableLoose(w, nx, ny, nz)) continue;
                    if (seen.add(key(nx, ny, nz))) queue.add(new int[]{nx, ny, nz, c[3] + 1});
                    break;
                }
            }
        }
        return false;
    }

    private static boolean standableLoose(World w, int x, int y, int z) {
        Block floor = w.getBlockAt(x, y - 1, z);
        return passable(w.getBlockAt(x, y, z)) && passable(w.getBlockAt(x, y + 1, z))
                && floor.getType().isSolid() && !floor.isLiquid();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    /** Direction approximative du coffre depuis le spawn, pour l'indice. */
    private String direction(Location loc) {
        double dx = loc.getX() - getConfig().getInt("spawn-x", 722);
        double dz = loc.getZ() - getConfig().getInt("spawn-z", 650);
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = nord (-z), 90 = est (+x)
        if (angle < 0) angle += 360;
        String[] names = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        return names[(int) Math.round(angle / 45.0) % 8];
    }

    // ------------------------------------------------------------------ ouverture

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (chest == null || e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (!e.getClickedBlock().getLocation().equals(chest)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.hasMetadata("vanished")) {
            p.sendMessage(LEGACY.deserialize("&6&l[Tresor] &7Tu es en vanish : pas de recompense."));
            return;
        }
        Location at = chest.clone().add(0.5, 0.5, 0.5);
        removeChest(false);
        for (String cmd : getConfig().getStringList("rewards")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 60, 0.4, 0.6, 0.4, 0.3);
        at.getWorld().playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        broadcast(msg("found").replace("%player%", p.getName()));
        getLogger().info(p.getName() + " a trouve le coffre au tresor.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (chest != null && e.getBlock().getLocation().equals(chest)) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (chest != null) e.blockList().removeIf(b -> b.getLocation().equals(chest));
    }

    // ------------------------------------------------------------------ outils

    private void removeChest(boolean silent) {
        if (chest == null) return;
        Block b = chest.getBlock();
        if (b.getType() == Material.CHEST) b.setType(Material.AIR, false);
        chest = null;
        saveData();
    }

    /** Coffre laisse par un arret brutal : on le retire au demarrage. */
    private void removeLeftover() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        String worldName = data.getString("chest.world");
        if (worldName == null) return;
        World w = Bukkit.getWorld(worldName);
        if (w != null) {
            Block b = w.getBlockAt(data.getInt("chest.x"), data.getInt("chest.y"), data.getInt("chest.z"));
            if (b.getType() == Material.CHEST) {
                b.setType(Material.AIR, false);
                getLogger().info("Ancien coffre au tresor retire en " + b.getX() + " " + b.getY() + " " + b.getZ() + ".");
            }
        }
        data.set("chest", null);
        try {
            data.save(dataFile);
        } catch (IOException ignored) {
        }
    }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        if (chest != null) {
            data.set("chest.world", chest.getWorld().getName());
            data.set("chest.x", chest.getBlockX());
            data.set("chest.y", chest.getBlockY());
            data.set("chest.z", chest.getBlockZ());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Sauvegarde de data.yml impossible : " + e.getMessage());
        }
    }

    private String msg(String key) {
        return getConfig().getString("messages." + key, "").replace("%reward%", getConfig().getString("reward-text", ""));
    }

    private void broadcast(String text) {
        if (text.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(LEGACY.deserialize(text));
        getLogger().info(text.replaceAll("&.", ""));
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eemitresor.admin")) {
            sender.sendMessage(LEGACY.deserialize("&6&l[Tresor] &7Un coffre au tresor apparait parfois dans le lobby. Ouvre l'oeil !"));
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn" -> {
                if (chest != null) removeChest(false);
                if (spawnChest()) {
                    nextSpawnAt = System.currentTimeMillis() + getConfig().getLong("interval-minutes", 120) * 60_000L;
                    sender.sendMessage("Coffre pose en " + chest.getBlockX() + " " + chest.getBlockY() + " " + chest.getBlockZ() + ".");
                } else {
                    sender.sendMessage("Aucune cachette trouvee (il faut des joueurs au lobby pour que les chunks soient charges).");
                }
            }
            case "remove" -> {
                removeChest(false);
                sender.sendMessage("Coffre retire.");
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("Config rechargee.");
            }
            default -> {
                long next = Math.max(0, nextSpawnAt - System.currentTimeMillis()) / 60_000L;
                sender.sendMessage(chest == null ? "Aucun coffre en ce moment."
                        : "Coffre en " + chest.getBlockX() + " " + chest.getBlockY() + " " + chest.getBlockZ()
                        + " (" + direction(chest) + " du spawn).");
                sender.sendMessage("Prochain coffre dans " + next + " min. /tresor spawn | remove | reload");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("eemitresor.admin")) {
            List<String> out = new ArrayList<>(List.of("spawn", "remove", "info", "reload"));
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
            return out;
        }
        return List.of();
    }
}
