package fr.eemicraft.spleef;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spleef a la pelle : etages de neige au-dessus d'un bassin de lave, on casse la neige sous les pieds des autres,
 * dernier debout gagne. Plusieurs arenes cote a cote dans le monde {@code spleef} (une partie par arene).
 */
public final class EemiSpleef extends JavaPlugin implements Listener {
    static final String P = "&f&l[&b&lSpleef&f&l] &r";
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    final List<Arena> arenas = new ArrayList<>();
    final Map<UUID, Arena> where = new HashMap<>();
    NamespacedKey itemKey;
    /** Decor dessine (memoire), partage par toutes les arenes. */
    private Builder decor;
    private YamlConfiguration data;
    private final Map<String, BlockData> cache = new HashMap<>();
    int invalidStates;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    // ================================================================ reglages (defauts dans le code)

    int cfg(String k, int def, int min, int max) {
        return Math.max(min, Math.min(max, getConfig().getInt(k, def)));
    }

    double cfgD(String k, double def, double min, double max) {
        return Math.max(min, Math.min(max, getConfig().getDouble(k, def)));
    }

    /** Nombre d'etages et ecart : figes au demarrage (ils fixent le decor). */
    int layers;
    int gap;

    List<Material> layerMaterials() {
        List<String> names = getConfig().getStringList("layer-materials");
        if (names.isEmpty()) {
            names = List.of("snow_block", "light_blue_wool", "cyan_wool");
        }
        List<Material> out = new ArrayList<>();
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m == null || !m.isBlock() || !m.isSolid()) {
                getLogger().warning("Bloc d'etage invalide ignore : " + n);
                continue;
            }
            out.add(m);
        }
        if (out.isEmpty()) {
            out.add(Material.SNOW_BLOCK);
        }
        return out;
    }

    String coreId() {
        return getConfig().getString("eemicore-id", "spleef");
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "item");
        // v2 (17/11/2026) : un seul grand terrain de neige plat, la cle "layers" n'est plus lue
        layers = 1;
        gap = cfg("layer-gap", 7, 5, 10);
        File f = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(f);
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 20L, 20L);
        if (world != null) {
            // toutes les arenes sont preparees au demarrage (pas de construction pendant que d'autres jouent)
            arena(cfg("max-instances", 3, 1, 8) - 1);
        }
        sendStatus();
    }

    @Override
    public void onDisable() {
        for (Arena a : arenas) {
            a.shutdown();
        }
        where.clear();
    }

    static final class Empty extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }
    }

    private void createWorld() {
        String name = getConfig().getString("world", "spleef");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            if (world != null) {
                getLogger().info("Monde " + name + " cree (vide).");
            }
        }
        if (world == null) {
            getLogger().severe("Impossible de creer le monde " + name);
            return;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setTime(13000); // crepuscule : ciel orange, neige lisible
        world.setStorm(false);
    }

    /** Arene n (creee et decoree au besoin). */
    Arena arena(int index) {
        while (arenas.size() <= index) {
            Arena a = new Arena(this, arenas.size());
            arenas.add(a);
            a.forceLoad(true);
            ensureDecor(a);
            a.resetLayers(cfg("max-players", 16, 2, 32) / 2);
            if (a.index == 0) {
                world.setSpawnLocation(a.waitSpawn());
            }
        }
        return arenas.get(index);
    }

    String decorSignature(Arena a) {
        return "v" + Builder.VERSION + "-l" + layers + "-g" + gap + "-x" + a.cx + "-" + world.getName();
    }

    void ensureDecor(Arena a) {
        // bloc temoin : le plancher du chalet (monde recree ou efface -> reconstruction)
        boolean floor = !world.getBlockAt(a.cx, Builder.WAIT_Y - 1, a.cz + (Builder.WZ1 + Builder.WZ2) / 2).getType().isAir();
        if (!floor || !decorSignature(a).equals(data.getString("decor." + a.index, ""))) {
            buildDecor(a);
        }
    }

    BlockData state(String s) {
        return cache.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData(k);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc invalide ignore : " + k);
                invalidStates++;
                return Bukkit.createBlockData("minecraft:air");
            }
        });
    }

    /** Pose le decor d'une arene : tout bloc dessine, et de l'air la ou reste un bloc d'une autre version. */
    void buildDecor(Arena a) {
        long t0 = System.currentTimeMillis();
        if (decor == null) {
            decor = Builder.build(layers, gap);
        }
        int placed = 0;
        int cleared = 0;
        int lava = Builder.lavaY(layers, gap);
        int yMin = Math.min(lava - 4, Builder.lavaY(4, 10) - 4);
        // 140 au moins : le chalet du decor v1 montait jusqu'a y ~136
        int yMax = Math.max(Builder.WAIT_Y + 22, 140);
        int ax = a.cx;
        BlockData air = state("minecraft:air");
        // 1. nettoyage de ce qui n'est pas dans le dessin (ancienne version, etages hors de la zone)
        for (int x = -Builder.AREA; x <= Builder.AREA; x++) {
            for (int z = -Builder.AREA; z <= Builder.AREA; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    if (!decor.blocks().containsKey(Builder.key(x, y, z))) {
                        Block b = world.getBlockAt(ax + x, y, a.cz + z);
                        if (!b.getType().isAir()) {
                            b.setBlockData(air, false);
                            cleared++;
                        }
                    }
                }
            }
        }
        // 2. le decor
        for (Map.Entry<Long, String> e : decor.blocks().entrySet()) {
            long k = e.getKey();
            world.getBlockAt(ax + Builder.kx(k), Builder.ky(k), a.cz + Builder.kz(k)).setBlockData(state(e.getValue()), false);
            placed++;
        }
        data.set("decor." + a.index, decorSignature(a));
        saveData();
        getLogger().info("Arene " + (a.index + 1) + " construite (decor v" + Builder.VERSION + ", " + layers + " etages) : " + placed + " blocs, "
                + cleared + " effaces, " + decor.states() + " etats, " + invalidStates + " invalides, en " + (System.currentTimeMillis() - t0) + " ms.");
    }

    void saveData() {
        try {
            getDataFolder().mkdirs();
            data.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    void tickAll() {
        for (Arena a : new ArrayList<>(arenas)) {
            a.tick();
        }
    }

    // ================================================================ EemiCore

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /** Statut agrege de toutes les arenes. */
    void sendStatus() {
        boolean playing = false;
        boolean ending = false;
        int waiting = 0;
        int inGame = 0;
        for (Arena a : arenas) {
            switch (a.state) {
                case WAITING, COUNTDOWN -> waiting += a.players.size();
                case GRACE, PLAYING -> {
                    playing = true;
                    inGame += a.alive();
                }
                case ENDING -> ending = true;
            }
        }
        String st = playing ? "playing" : (waiting > 0 ? "waiting" : (ending ? "ending" : "waiting"));
        int max = cfg("max-players", 16, 2, 32) * cfg("max-instances", 3, 1, 8);
        eemi("eemi status " + coreId() + " " + st + " " + waiting + " " + inGame + " " + max);
    }

    // ================================================================ entree / sortie

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemispleef.admin")) {
            msg(p, "&cOn rejoint le Spleef depuis le lobby (&f/lobby&c).");
            return false;
        }
        return true;
    }

    void join(Player p) {
        if (world == null) {
            msg(p, "&cLe jeu est indisponible.");
            return;
        }
        Arena cur = where.get(p.getUniqueId());
        if (cur != null) {
            if (cur.spectators.contains(p.getUniqueId()) && cur.waitingPhase()) {
                cur.spectators.remove(p.getUniqueId());
                where.remove(p.getUniqueId());
            } else {
                msg(p, "&7Tu es deja dans une partie. &f/spleef leave &7pour sortir.");
                return;
            }
        }
        if (!canEnter(p)) {
            return;
        }
        int max = cfg("max-players", 16, 2, 32);
        Arena best = null;
        for (Arena a : arenas) {
            if (a.waitingPhase() && a.players.size() < max && (best == null || a.players.size() > best.players.size())) {
                best = a;
            }
        }
        if (best == null && arenas.size() < cfg("max-instances", 3, 1, 8)) {
            best = arena(arenas.size());
        }
        if (best == null) {
            msg(p, "&cToutes les parties sont pleines, reessaie dans un instant.");
            return;
        }
        best.addPlayer(p);
    }

    void spectate(Player p, Player target) {
        if (where.containsKey(p.getUniqueId())) {
            msg(p, "&7Tu es deja dans une partie. &f/spleef leave &7d'abord.");
            return;
        }
        if (!canEnter(p)) {
            return;
        }
        Arena best = target == null ? null : where.get(target.getUniqueId());
        if (best == null) {
            for (Arena a : arenas) {
                if (a.inGamePhase() && (best == null || a.alive() > best.alive())) {
                    best = a;
                }
            }
        }
        if (best == null) {
            msg(p, "&7Aucune partie en cours a regarder.");
            return;
        }
        best.addSpectator(p);
    }

    void toLobby(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.SURVIVAL) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        String cmd = getConfig().getString("lobby-command", "lobby");
        if (!p.performCommand(cmd)) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    // ================================================================ objets

    ItemStack tagged(Material m, String name, String tag) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        it.setItemMeta(meta);
        return it;
    }

    /** Objet de la file d'attente (case 8) : clic droit = quitter la file et revenir au lobby. */
    ItemStack leaveItem() {
        return tagged(Material.RED_BED, "&cRetour au lobby &7(clic droit)", "leave");
    }

    String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p)) {
            return;
        }
        Arena a = where.get(p.getUniqueId());
        if (a != null && a.inGamePhase() && (e.getFrom().getYaw() != e.getTo().getYaw() || e.getFrom().getPitch() != e.getTo().getPitch())) {
            // a tourne la tete de lui-meme pendant la partie : pas un compte AFK (garde en file a la fin)
            a.active.add(p.getUniqueId());
        }
        if (p.getGameMode() == GameMode.SPECTATOR) {
            // spectateurs et elimines restent autour de leur arene
            if (a != null) {
                double dx = e.getTo().getX() - a.cx;
                double dz = e.getTo().getZ() - a.cz;
                double dy = e.getTo().getY();
                if (dx * dx + dz * dz > 60 * 60 || dy < Builder.lavaY(layers, gap) - 8 || dy > Builder.WAIT_Y + 40) {
                    p.teleport(a.specSpawn());
                }
            }
            return;
        }
        if (a == null) {
            // perdu dans le monde sans partie (admin hors creatif, reconnexion) : ramene au chalet
            if (e.getTo().getY() < Builder.lavaY(layers, gap) + 4 && !(p.isOp() && p.getGameMode() == GameMode.CREATIVE)) {
                p.teleport(arenas.isEmpty() ? world.getSpawnLocation() : arenas.get(0).waitSpawn());
            }
            return;
        }
        a.onMove(p, e.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageBlock(BlockDamageEvent e) {
        if (world == null || !e.getBlock().getWorld().equals(world)) {
            return;
        }
        Player p = e.getPlayer();
        Arena a = where.get(p.getUniqueId());
        if (a != null) {
            a.onDig(p, e.getBlock());
            e.setCancelled(true);
        } else if (!builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (world != null && e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE && !where.containsKey(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) {
            return;
        }
        Arena a = where.get(p.getUniqueId());
        String t = tag(e.getItem());
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
        if (right && "leave".equals(t) && a != null) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setCancelled(true);
            a.leave(p, true);
            return;
        }
        boolean snowball = e.getItem() != null && e.getItem().getType() == Material.SNOWBALL;
        if (right && !(snowball && a != null && a.canThrow(p))) {
            // objets du lobby (perles, fusees...) et boules de neige pendant la grace : rien
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        if (e.getEntity() instanceof Snowball && e.getEntity().getShooter() instanceof Player p) {
            Arena a = where.get(p.getUniqueId());
            if (a != null && a.canThrow(p)) {
                return;
            }
        }
        if (!(e.getEntity().getShooter() instanceof Player p && builder(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (world == null || !(e.getEntity() instanceof Snowball ball) || !e.getEntity().getWorld().equals(world)) {
            return;
        }
        if (!(e.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        Arena a = where.get(shooter.getUniqueId());
        if (a != null) {
            a.onSnowballHit(shooter, e.getHitBlock(), e.getHitEntity(), ball);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (world != null && e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (where.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && where.containsKey(p.getUniqueId()) && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && e.isGliding()) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (world != null && e.getFrom().equals(world)) {
            Arena a = where.get(e.getPlayer().getUniqueId());
            if (a != null) {
                a.leave(e.getPlayer(), false);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Arena a = where.get(e.getPlayer().getUniqueId());
        if (a != null) {
            a.leave(e.getPlayer(), false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (inWorld(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && inWorld(p) && !where.containsKey(p.getUniqueId())) {
                    Arena.clearGameItems(this, p);
                    toLobby(p);
                }
            }, 10L);
        }
    }

    // ================================================================ commandes

    Arena arenaArg(CommandSender s, String[] a) {
        int n = 1;
        if (a.length >= 2) {
            try {
                n = Integer.parseInt(a[1]);
            } catch (NumberFormatException ex) {
                msg(s, "&cNumero de partie invalide.");
                return null;
            }
        } else if (s instanceof Player p && where.containsKey(p.getUniqueId())) {
            return where.get(p.getUniqueId());
        }
        if (n < 1 || n > arenas.size()) {
            msg(s, "&cPartie " + n + " inexistante (1 a " + arenas.size() + ").");
            return null;
        }
        return arenas.get(n - 1);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemispleef.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) {
                    join(p);
                }
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    Arena ar = where.get(p.getUniqueId());
                    if (ar != null) {
                        ar.leave(p, true);
                    } else {
                        msg(p, "&7Tu n'es dans aucune partie.");
                    }
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p) {
                    Player target = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : null;
                    spectate(p, target);
                }
            }
            case "start" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                    return true;
                }
                Arena ar = arenaArg(s, a);
                if (ar == null) {
                    return true;
                }
                if (!ar.waitingPhase()) {
                    msg(s, "&cLa partie " + (ar.index + 1) + " est deja lancee.");
                } else if (ar.players.isEmpty()) {
                    msg(s, "&cPersonne n'est inscrit dans la partie " + (ar.index + 1) + ".");
                } else {
                    ar.start();
                    msg(s, "&aPartie " + (ar.index + 1) + " lancee.");
                }
            }
            case "stop" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                    return true;
                }
                Arena ar = arenaArg(s, a);
                if (ar != null) {
                    ar.tell("&cPartie arretee par un admin.");
                    ar.finish(false);
                    msg(s, "&7Partie " + (ar.index + 1) + " arretee.");
                }
            }
            case "build" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                    return true;
                }
                int n = 0;
                for (Arena ar : arenas) {
                    if (ar.waitingPhase() && ar.players.isEmpty()) {
                        buildDecor(ar);
                        ar.resetLayers(cfg("max-players", 16, 2, 32) / 2);
                        n++;
                    }
                }
                msg(s, "&a" + n + " arene(s) reconstruite(s) (les arenes occupees sont laissees).");
            }
            case "reload" -> {
                if (!admin) {
                    msg(s, "&cPermission refusee.");
                    return true;
                }
                reloadConfig();
                msg(s, "&aConfig rechargee (etages et ecart : au prochain redemarrage).");
            }
            case "parties", "info" -> {
                if (arenas.isEmpty()) {
                    msg(s, "&7Aucune arene.");
                }
                for (Arena ar : arenas) {
                    msg(s, "&7Partie &f" + (ar.index + 1) + "&7 : &f" + ar.state + "&7, inscrits &f" + ar.players.size() + "&7, en vie &f" + ar.alive()
                            + "&7, spectateurs &f" + ar.spectators.size());
                }
            }
            default -> {
                msg(s, "&f/spleef join &7- rejoindre | &f/spleef leave &7- quitter | &f/spleef spectate [joueur] &7- regarder");
                if (admin) {
                    msg(s, "&c/spleef start [n] | stop [n] | build | reload | parties");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate"));
        if (s.hasPermission("eemispleef.admin")) {
            out.addAll(List.of("start", "stop", "build", "reload", "parties"));
        }
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("spectate")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
