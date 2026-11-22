package fr.eemicraft.horscours;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Hors Cours, version de test : prologue dans l'école (lampe, craie, café, la voix), passage à travers
 * le mur de la baie réseau, puis niveau 1 « Le Couloir » avec Loïc. Une seule session à la fois
 * (1 à 4 joueurs), réservée aux admins (permission eemihorscours.play).
 */
public final class EemiHorsCours extends JavaPlugin implements Listener {

    static final String WORLD = "horscours";
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacySection();

    private NamespacedKey keyTag, keyItem;
    private World world;
    private Prologue prologue;
    private Ecole ecole;
    private Ambiance amb;
    private Maze maze;
    private Loic loic;
    private Niveau1 n1;
    private final Map<Integer, String> lines = new HashMap<>();
    private final Random rnd = new Random();

    // ------------------------------------------------------------------ session
    private final class Session {
        final Set<UUID> players = new LinkedHashSet<>();
        int stage; // 0 départ, 1 lampe, 2 craie, 3 café (mur ouvert)
        final Set<String> said = new HashSet<>();
        final ArrayDeque<Integer> voice = new ArrayDeque<>();
        long voiceFree;
        BossBar bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        boolean levelStarted;
        long levelStartTick;
        int fuses;
        final Set<String> taken = new HashSet<>();
        int catches;
        long lastCatchLine;
        boolean exitOpen;
        long tick;
    }

    private Session session;
    private final Map<UUID, Double> battery = new HashMap<>();
    private final Set<UUID> lampOn = new HashSet<>();
    private final Map<UUID, Location> lightAt = new HashMap<>();
    private final Map<UUID, Integer> level = new HashMap<>(); // 0 prologue, 1 couloir
    private final Map<UUID, Long> lowBatteryWarn = new HashMap<>();
    private final Map<UUID, Integer> noclipTicks = new HashMap<>();
    private final Set<UUID> won = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyTag = new NamespacedKey(this, "tag");
        keyItem = new NamespacedKey(this, "item");
        loadLines();
        world = setupWorld();
        if (world == null) return;
        prologue = new Prologue(world);
        ecole = new Ecole(world, getConfig());
        amb = new Ambiance(this, ecole);
        maze = new Maze(world, Math.max(5, getConfig().getInt("maze.size", 11)));
        loic = new Loic(this, maze);
        n1 = new Niveau1(this, maze);
        cleanEntities();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 2L);
        getLogger().info("Hors Cours pret (" + lines.size() + " repliques de la voix, " + durations.size() + " enregistrees).");
    }

    @Override
    public void onDisable() {
        if (session != null) endSession(true);
        cleanEntities();
    }

    private World setupWorld() {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) {
            w = new WorldCreator(WORLD).generator(new ChunkGenerator() {
                @Override
                public Location getFixedSpawnLocation(World world, Random random) {
                    return new Location(world, 44.5, 64, 17.5);
                }
            }).createWorld();
            if (w != null) getLogger().info("Monde " + WORLD + " cree (vide).");
        }
        if (w == null) {
            getLogger().severe("Impossible de creer le monde " + WORLD);
            return null;
        }
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setTime(18000);
        return w;
    }

    private void loadLines() {
        try (InputStream in = getResource("voix.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf('|');
                if (i > 0) lines.put(Integer.parseInt(l.substring(0, i).trim()), l.substring(i + 1).trim());
            }
        } catch (Exception e) {
            getLogger().warning("voix.txt illisible : " + e.getMessage());
        }
        // Durée réelle de chaque réplique enregistrée (écrite par voix/traiter_voix.py)
        try (InputStream in = getResource("voix_durees.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf('|');
                if (i > 0) durations.put(Integer.parseInt(l.substring(0, i).trim()), Long.parseLong(l.substring(i + 1).trim()));
            }
        } catch (Exception e) {
            getLogger().warning("voix_durees.txt illisible : " + e.getMessage());
        }
    }

    private final Map<Integer, Long> durations = new HashMap<>();

    void tag(Entity e, String v) {
        e.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, v);
    }

    String tagOf(Entity e) {
        return e.getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    private void cleanEntities() {
        if (world == null) return;
        for (Entity e : world.getEntities())
            if (tagOf(e) != null) e.remove();
    }

    // ------------------------------------------------------------------ commande
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemihorscours.admin");
        switch (sub) {
            case "join" -> {
                if (!(s instanceof Player p)) return true;
                if (!p.hasPermission("eemihorscours.play")) {
                    msg(p, "§cHors Cours est en test : bientot disponible !");
                    return true;
                }
                join(p);
            }
            case "leave" -> {
                if (s instanceof Player p) leave(p, true);
            }
            case "reset" -> {
                if (!admin) return true;
                if (session != null) endSession(false);
                s.sendMessage("Session Hors Cours terminee.");
            }
            case "niveau" -> {
                if (!admin || session == null || a.length < 2) return true;
                int lv = a[1].equals("1") ? 1 : 0;
                for (Player p : online()) {
                    if (lv == 1) goLevel1(p);
                    else p.teleport(ecole.spawn());
                }
            }
            case "peur" -> {
                if (!admin) return true;
                Player t = a.length >= 2 ? Bukkit.getPlayer(a[1]) : (s instanceof Player pp ? pp : null);
                if (t != null && t.getWorld().equals(world)) screamer(t);
            }
            case "point" -> {
                if (!admin || !(s instanceof Player p) || a.length < 2) {
                    s.sendMessage("/horscours point <depart|lampe|craie|cafe|passage> (regarde l'endroit voulu)");
                    return true;
                }
                setPoint(p, a[1].toLowerCase());
            }
            case "loic" -> {
                if (!admin || session == null || !session.levelStarted) return true;
                spawnLoic();
            }
            default -> s.sendMessage("/horscours <join|leave>" + (admin ? " | reset | niveau <0|1> | loic | point <nom> | peur <joueur>" : ""));
        }
        return true;
    }

    /** Règle un point du jeu dans l'école, là où l'admin se tient ou regarde. */
    private void setPoint(Player p, String name) {
        Location l;
        Block target = p.getTargetBlockExact(6);
        switch (name) {
            case "depart" -> l = p.getLocation();
            case "lampe", "craie" -> {
                if (target == null) { msg(p, "§cRegarde le bloc où poser l'objet."); return; }
                l = target.getLocation().add(0.5, 1.0, 0.5);
            }
            case "cafe", "passage" -> {
                if (target == null) { msg(p, "§cRegarde le bloc voulu."); return; }
                l = target.getLocation().add(0.5, 0.5, 0.5);
            }
            default -> { msg(p, "§cPoints : depart, lampe, craie, cafe, passage"); return; }
        }
        String v = String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.1f", l.getX(), l.getY(), l.getZ(), p.getLocation().getYaw());
        getConfig().set("ecole.points." + name, v);
        saveConfig();
        msg(p, "§aPoint §f" + name + " §aregle : §7" + v + " §8(pris en compte a la prochaine partie)");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickNearCoffee(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || session == null) return;
        Player p = e.getPlayer();
        if (!session.players.contains(p.getUniqueId()) || !p.getWorld().equals(world)) return;
        if (e.getRightClicked().getLocation().distanceSquared(ecole.cafe()) < 2.5 * 2.5) {
            e.setCancelled(true);
            drinkCoffee(p);
        }
    }

    // ------------------------------------------------------------------ entrer / sortir
    private void join(Player p) {
        if (session != null && session.players.contains(p.getUniqueId())) {
            msg(p, "§7Tu es deja dans Hors Cours (/horscours leave pour sortir).");
            return;
        }
        if (session == null) startSession();
        if (session.players.size() >= getConfig().getInt("max-players", 4)) {
            msg(p, "§cLa partie est pleine.");
            return;
        }
        session.players.add(p.getUniqueId());
        battery.put(p.getUniqueId(), 100.0);
        level.put(p.getUniqueId(), 0);
        won.remove(p.getUniqueId());
        // Téléporter d'abord, puis préparer le joueur dans le monde du jeu
        Location to = ecole.spawn();
        p.teleport(to);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!mayTouch(p) || session == null || !session.players.contains(p.getUniqueId())) return;
            reset(p, GameMode.ADVENTURE);
            if (session.stage >= 1) p.getInventory().addItem(lamp());
            if (session.stage >= 2) p.getInventory().addItem(chalk());
            session.bar.addPlayer(p);
            if (session.players.size() > 1) say(6);
        }, 2L);
    }

    private void leave(Player p, boolean toHub) {
        UUID id = p.getUniqueId();
        if (session == null || !session.players.remove(id)) {
            if (toHub && p.getWorld().equals(world)) sendToHub(p);
            return;
        }
        session.bar.removePlayer(p);
        restoreLight(p);
        stopAmbience(p);
        amb.forget(p);
        if (n1.inLocker(p)) n1.unhide(p);
        n1.forget(p);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        lampOn.remove(id);
        level.remove(id);
        noclipTicks.remove(id);
        if (toHub) sendToHub(p);
        if (session.players.isEmpty()) endSession(false);
    }

    private void startSession() {
        session = new Session();
        cleanEntities();
        ecole.scan();
        amb.reset();
        getLogger().info("Ecole : " + ecole.lights.size() + " lumieres, " + ecole.doors.size() + " portes.");
        maze.generate(rnd.nextLong(), getConfig().getInt("maze.fuses", 5), getConfig().getInt("maze.piles", 6),
                getConfig().getDouble("maze.lit-cells", 0.22));
        maze.build();
        forceLoad(true);
        spawnLabels(maze.labels);
        n1.setup();
        getLogger().info("Niveau 1 : " + maze.pieces.size() + " meubles, " + maze.fuseCells.size() + " fusibles, code " + maze.code + ".");
        spawnPickup(ecole.lampe(), Material.LANTERN, "lamp");
        spawnPickup(ecole.craie(), Material.WHITE_DYE, "chalk");
        objective("§fExplore l'ecole");
        later(40, () -> say(1));
        later(140, () -> {
            say(2);
            objective("§fTrouve une lampe §7(salle 02)");
        });
        getLogger().info("Session Hors Cours demarree.");
    }

    private void endSession(boolean shutdown) {
        Session s = session;
        session = null;
        if (s == null) return;
        s.bar.removeAll();
        for (UUID id : s.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restoreLight(p);
                stopAmbience(p);
                if (p.getWorld().equals(world)) sendToHub(p);
            }
        }
        lampOn.clear();
        lightAt.clear();
        level.clear();
        noclipTicks.clear();
        loic.remove();
        n1.clear();
        cleanEntities();
        if (!shutdown) forceLoad(false);
    }

    private void forceLoad(boolean on) {
        int size = maze.n * Maze.P;
        for (int cx = (Maze.OX - 8) >> 4; cx <= (Maze.OX + size + 8) >> 4; cx++)
            for (int cz = (Maze.OZ - 8) >> 4; cz <= (Maze.OZ + size + 8) >> 4; cz++)
                world.setChunkForceLoaded(cx, cz, on);
        for (int cx = 18; cx <= 21; cx++)
            for (int cz = -1; cz <= 1; cz++) world.setChunkForceLoaded(cx, cz, on);
    }

    private List<Player> online() {
        List<Player> l = new ArrayList<>();
        if (session == null) return l;
        for (UUID id : session.players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getWorld().equals(world)) l.add(p);
        }
        return l;
    }

    /** Le plugin ne touche au joueur que dans son monde ou au hub (règle commune inventaires). */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        if (w.equals(world)) return true;
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        return hub != null && w.equals(hub);
    }

    private void reset(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        if (p.getWorld().equals(world)) reset(p, GameMode.SURVIVAL);
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    // ------------------------------------------------------------------ objets
    private ItemStack named(Material m, String id, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEG.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(LEG.deserialize(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        meta.getPersistentDataContainer().set(keyItem, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("eemicraft", "horscours/" + switch (id) {
            case "lamp" -> "lampe";
            case "pince" -> "pince";
            default -> "craie";
        }));
        it.setItemMeta(meta);
        return it;
    }

    /** Objet affiché (posé dans le décor) avec sa texture du pack. */
    private ItemStack shown(Material m, String model) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("eemicraft", "horscours/" + model));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack lamp() {
        return named(Material.LANTERN, "lamp", "§eLampe torche", "§7Clic droit : allumer / eteindre",
                "§7La batterie s'use quand elle est allumee.");
    }

    private ItemStack chalk() {
        return named(Material.WHITE_DYE, "chalk", "§fCraie", "§7Clic droit sur un mur ou le sol : faire une marque");
    }

    private ItemStack pince() {
        return named(Material.SHEARS, "pince", "§cPince coupante", "§7Coupe les cadenas des armoires.",
                "§8Attention : ca fait du bruit.");
    }

    boolean holdsPince(Player p) {
        return "pince".equals(itemId(p.getInventory().getItemInMainHand()));
    }

    void givePince(Player p) {
        p.getInventory().addItem(pince());
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
    }

    boolean lampOn(Player p) {
        return lampOn.contains(p.getUniqueId());
    }

    boolean loicSees(Player p) {
        return loic.alive() && loic.distTo(p) < 9 && loic.seenBy(p);
    }

    /** Un bruit dans le labyrinthe : Loïc l'entend s'il est à moins de radius blocs. */
    void noise(Location at, double radius) {
        loic.hear(at.getX(), at.getZ(), radius);
    }

    void fuse(Player p) {
        takeFuse(p);
    }

    void pile(Player p) {
        double b = Math.min(100, battery.getOrDefault(p.getUniqueId(), 0.0) + getConfig().getDouble("lamp.pile-recharge", 50));
        battery.put(p.getUniqueId(), b);
        p.playSound(p.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 1f, 1.2f);
        p.sendActionBar(LEG.deserialize("§ePile recuperee : lampe a " + (int) b + " %"));
    }

    private String itemId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(keyItem, PersistentDataType.STRING);
    }

    private void spawnPickup(Location at, Material m, String id) {
        ItemDisplay d = world.spawn(at, ItemDisplay.class, e -> {
            String model = id.equals("lamp") ? "lampe" : id.equals("chalk") ? "craie" : id.startsWith("fuse") ? "fusible" : "pile";
            e.setItemStack(shown(m, model));
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setTransformation(new Transformation(new Vector3f(0f, 0.25f, 0f), new AxisAngle4f(),
                    new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            tag(e, "pick:" + id);
        });
        world.spawn(at, Interaction.class, e -> {
            e.setInteractionWidth(0.7f);
            e.setInteractionHeight(0.6f);
            e.setResponsive(true);
            e.setPersistent(false);
            tag(e, "click:" + id);
        });
        if (d == null) getLogger().warning("Objet " + id + " non pose");
    }

    private void removePickup(String id) {
        for (Entity e : world.getEntities()) {
            String t = tagOf(e);
            if (t != null && (t.equals("pick:" + id) || t.equals("click:" + id))) e.remove();
        }
    }

    private void spawnLabels(List<Object[]> labels) {
        for (Object[] l : labels) {
            Location at = new Location(world, ((Number) l[0]).doubleValue(), ((Number) l[1]).doubleValue(),
                    ((Number) l[2]).doubleValue(), (Float) l[3], 0);
            world.spawn(at, TextDisplay.class, t -> {
                t.text(LEG.deserialize((String) l[4]));
                t.setBillboard(Display.Billboard.FIXED);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setShadowed(true);
                t.setPersistent(false);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
                tag(t, "label");
            });
        }
    }

    // ------------------------------------------------------------------ interactions
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || session == null) return;
        String t = tagOf(e.getRightClicked());
        if (t != null && t.startsWith("n1:")) {
            e.setCancelled(true);
            if (session.players.contains(e.getPlayer().getUniqueId())) n1.click(e.getPlayer(), Integer.parseInt(t.substring(3)));
            return;
        }
        if (t == null || !t.startsWith("click:")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!session.players.contains(p.getUniqueId())) return;
        String id = t.substring(6);
        if (!session.taken.add(id)) return;
        removePickup(id);
        if (id.equals("lamp")) takeLamp();
        else if (id.equals("chalk")) takeChalk();
        else if (id.startsWith("fuse:")) takeFuse(p);
        else if (id.startsWith("pile:")) pile(p);
    }

    private void takeLamp() {
        for (Player p : online()) {
            p.getInventory().addItem(lamp());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
        session.stage = Math.max(session.stage, 1);
        // Coupure de courant
        later(30, () -> {
            amb.powerCut(online());
            for (Player p : online()) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 1f, 0.6f);
            say(3);
            say(4);
            objective("§fTrouve de la craie §7(salle 01)");
        });
    }

    private void takeChalk() {
        for (Player p : online()) {
            p.getInventory().addItem(chalk());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
        session.stage = Math.max(session.stage, 2);
        say(5);
        say(7);
        objective("§fBois un cafe §7(salle de pause)");
    }

    private void takeFuse(Player p) {
        session.fuses++;
        int need = maze.fuseCells.size();
        for (Player o : online()) o.playSound(o.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER, 1f, 1.4f);
        if (session.fuses == 1) say(19);
        if (session.fuses >= need) {
            session.exitOpen = true;
            maze.openExit();
            for (Player o : online()) o.playSound(o.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.8f);
            say(20);
            objective("§aLa sortie est ouverte !");
        } else objective("§fFusibles : §e" + session.fuses + "/" + need);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!p.getWorld().equals(world) || session == null || !session.players.contains(p.getUniqueId())) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        Block b = e.getClickedBlock();
        // Machine à café
        if (right && b != null && b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(ecole.cafe()) < 2.5 * 2.5) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            drinkCoffee(p);
            return;
        }
        String id = itemId(e.getItem());
        if (id == null || !right) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (id.equals("lamp")) toggleLamp(p);
        else if (id.equals("pince")) p.sendActionBar(LEG.deserialize("§7Clic droit sur une armoire cadenassee pour couper."));
        else if (id.equals("chalk") && b != null && e.getBlockFace() != null) chalkMark(p, b, e.getBlockFace());
    }

    private void drinkCoffee(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 0, false, false, true));
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.sendActionBar(LEG.deserialize("§6Un cafe bien chaud. Ca va mieux."));
        if (session.stage == 2) {
            session.stage = 3;
            amb.strobe(online(), p.getLocation(), 70);
            say(10);
            later(160, () -> {
                say(12);
                objective("§fLe local de reprographie... §7(la baie reseau)");
            });
        }
    }

    private void tvGlitch(List<int[]> tvs) {
        for (int k = 0; k < 20; k++) {
            final boolean on = k % 2 == 0;
            later(k * 3L, () -> {
                for (int[] t : tvs)
                    world.getBlockAt(t[0], t[1], t[2]).setType(on ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE, false);
            });
        }
        later(62, () -> {
            for (int[] t : tvs) world.getBlockAt(t[0], t[1], t[2]).setType(Material.BLACK_CONCRETE, false);
        });
        for (Player p : online()) p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 1f, 2f);
    }

    private void chalkMark(Player p, Block b, BlockFace f) {
        if (f == BlockFace.DOWN) return;
        long marks = world.getEntities().stream().filter(en -> "chalk".equals(tagOf(en))).count();
        if (marks >= 120) {
            p.sendActionBar(LEG.deserialize("§7Trop de marques : la craie est usee."));
            return;
        }
        double x = b.getX() + 0.5 + f.getModX() * 0.51, y = b.getY() + 0.5 + f.getModY() * 0.51, z = b.getZ() + 0.5 + f.getModZ() * 0.51;
        float yaw = switch (f) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
        float pitch = 0;
        if (f == BlockFace.UP) {
            yaw = p.getLocation().getYaw();
            pitch = -90f;
        } else y -= 0.2;
        Location at = new Location(world, x, y, z, yaw, pitch);
        String text = f == BlockFace.UP ? "↑" : "✕";
        world.spawn(at, TextDisplay.class, t -> {
            t.text(Component.text(text));
            t.setBillboard(Display.Billboard.FIXED);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(2.5f, 2.5f, 2.5f), new AxisAngle4f()));
            tag(t, "chalk");
        });
        p.playSound(at, Sound.BLOCK_GRAVEL_HIT, 0.6f, 1.8f);
    }

    // ------------------------------------------------------------------ lampe
    private void toggleLamp(Player p) {
        UUID id = p.getUniqueId();
        if (lampOn.remove(id)) {
            restoreLight(p);
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 0.7f);
            return;
        }
        if (battery.getOrDefault(id, 0.0) <= 0) {
            p.sendActionBar(LEG.deserialize("§cLa batterie est vide. Trouve des piles."));
            return;
        }
        lampOn.add(id);
        p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 1.3f);
    }

    /** Lumière qui suit le joueur : un bloc de lumière envoyé aux joueurs de la session (rien n'est posé). */
    private void updateLight(Player p, List<Player> viewers) {
        Location eye = p.getEyeLocation();
        Block b = eye.getBlock();
        if (!b.getType().isAir()) b = b.getRelative(BlockFace.DOWN);
        Location at = b.getLocation();
        Location old = lightAt.get(p.getUniqueId());
        if (old != null && old.equals(at)) return;
        if (old != null) for (Player v : viewers) v.sendBlockChange(old, old.getBlock().getBlockData());
        if (!b.getType().isAir()) {
            lightAt.remove(p.getUniqueId());
            return;
        }
        Light l = (Light) Material.LIGHT.createBlockData();
        l.setLevel(Math.max(1, Math.min(15, getConfig().getInt("lamp.light-level", 13))));
        for (Player v : viewers) v.sendBlockChange(at, l);
        lightAt.put(p.getUniqueId(), at);
    }

    private void restoreLight(Player p) {
        Location old = lightAt.remove(p.getUniqueId());
        if (old == null) return;
        BlockData real = old.getBlock().getBlockData();
        for (Player v : online()) v.sendBlockChange(old, real);
        if (p.isOnline() && p.getWorld().equals(old.getWorld())) p.sendBlockChange(old, real);
    }

    // ------------------------------------------------------------------ voix
    /** Réplique de la voix (une fois par session) : son du pack s'il existe + sous-titre. */
    void say(int id) {
        if (session == null) return;
        if (!session.said.add("v" + id)) return;
        session.voice.add(id);
    }

    private void sayAgain(int id) {
        if (session != null) session.voice.add(id);
    }

    private void voiceTick() {
        long now = System.currentTimeMillis();
        if (session.voice.isEmpty() || now < session.voiceFree) return;
        int id = session.voice.poll();
        String text = lines.getOrDefault(id, "...");
        String sound = String.format("eemicraft:horscours.moustache.%02d", id);
        for (Player p : online()) {
            p.playSound(p.getLocation(), sound, SoundCategory.VOICE, 1f, 1f);
            p.sendMessage(LEG.deserialize("§8[§7Voix§8] §7§o" + text));
        }
        Long ms = durations.get(id);
        session.voiceFree = now + (ms != null ? ms + 700 : 1500 + text.length() * 65L);
    }

    private void objective(String s) {
        if (session != null) session.bar.setTitle(s);
    }

    private void msg(Player p, String s) {
        p.sendMessage(LEG.deserialize("§8[§fHors Cours§8] " + s));
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (session != null) r.run();
        }, ticks);
    }

    // ------------------------------------------------------------------ boucle
    private void tick() {
        if (session == null) return;
        Session s = session;
        s.tick++;
        List<Player> all = online();
        voiceTick();
        double drain = 100.0 / (getConfig().getDouble("lamp.battery-minutes", 4) * 60 * 10); // par 2 ticks
        List<Player> inMaze = new ArrayList<>();
        for (Player p : all) {
            UUID id = p.getUniqueId();
            int lv = level.getOrDefault(id, 0);
            if (lv == 1 && !won.contains(id)) inMaze.add(p);
            darkVision(p, lv);
            // Lampe
            if (lampOn.contains(id)) {
                double b = battery.getOrDefault(id, 0.0) - drain;
                battery.put(id, Math.max(0, b));
                if (b <= 0) {
                    lampOn.remove(id);
                    restoreLight(p);
                    sayAgain(50);
                } else {
                    updateLight(p, all);
                    if (s.tick % 10 == 0) p.sendActionBar(LEG.deserialize(batteryText(b)));
                    if (b < 20 && System.currentTimeMillis() - lowBatteryWarn.getOrDefault(id, 0L) > 90000) {
                        lowBatteryWarn.put(id, System.currentTimeMillis());
                        sayAgain(49);
                    }
                }
            }
            ambience(p, lv);
            amb.tickPlayer(p, lv, session.stage, s.tick);
            if (s.tick % 5 == 0) {
                if (lv == 0) prologueChecks(p);
                else mazeChecks(p);
            }
        }
        amb.tickLights(all, s.tick);
        // Passage à travers le mur (prologue)
        if (s.stage >= 3) {
            Location pass = ecole.passage();
            if (s.tick % 5 == 0)
                world.spawnParticle(Particle.REVERSE_PORTAL, pass, 8, 0.6, 0.8, 0.6, 0.02);
            for (Player p : all) {
                if (level.getOrDefault(p.getUniqueId(), 0) != 0) continue;
                Location pl = p.getLocation();
                double dx = pass.getX() - pl.getX(), dz = pass.getZ() - pl.getZ();
                double hd = Math.sqrt(dx * dx + dz * dz);
                org.bukkit.util.Vector dir = pl.getDirection().setY(0);
                boolean facing = hd < 0.01 || (dir.lengthSquared() > 0 && dir.normalize().dot(new org.bukkit.util.Vector(dx / hd, 0, dz / hd)) > 0.4);
                boolean in = hd < 1.4 && Math.abs(pass.getY() - pl.getY()) < 2.2 && facing;
                int t = in ? noclipTicks.merge(p.getUniqueId(), 1, Integer::sum) : 0;
                if (!in) noclipTicks.remove(p.getUniqueId());
                if (t >= 8) {
                    noclipTicks.remove(p.getUniqueId());
                    noclip(p);
                }
            }
        }
        // Niveau 1
        if (s.levelStarted) {
            if (s.tick % 7 == 0 && rnd.nextDouble() < 0.5) maze.flicker(rnd);
            long since = s.tick - s.levelStartTick;
            if (!loic.alive() && since >= getConfig().getInt("loic.spawn-delay", 20) * 10L && !inMaze.isEmpty()) spawnLoic();
            n1.tick(inMaze, loic.x, loic.z, loic.alive());
            if (loic.alive()) {
                List<Player> targets = new ArrayList<>();
                for (Player p : inMaze) if (!n1.isHidden(p)) targets.add(p);
                Player caught = loic.tick(targets);
                for (Player p : inMaze)
                    if (loic.distTo(p) < 12 && !loic.wasSeen) say(18);
                if (caught != null) caught(caught);
            }
        }
    }

    /**
     * Sans lampe allumée, la vue est réduite à quelques blocs (Cécité) : le brouillard de la Cécité
     * s'applique quel que soit le réglage de luminosité du joueur (et le « fullbright » des clients
     * comme Lunar), contrairement à l'obscurité des blocs. Lampe allumée = vue normale.
     * Avant la coupure de courant (prologue), rien.
     */
    private void darkVision(Player p, int lv) {
        if (!getConfig().getBoolean("lamp.blind-without-lamp", true)) return;
        boolean dark = (lv == 1 || session.stage >= 1) && !lampOn.contains(p.getUniqueId()) && !won.contains(p.getUniqueId());
        PotionEffect cur = p.getPotionEffect(PotionEffectType.BLINDNESS);
        if (dark) {
            if (cur == null || cur.getDuration() < 30)
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        } else if (cur != null && cur.getDuration() <= 60 && System.currentTimeMillis() > screamerUntil.getOrDefault(p.getUniqueId(), 0L)) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }

    private final Map<UUID, Long> screamerUntil = new HashMap<>();

    private String batteryText(double b) {
        int bars = (int) Math.ceil(b / 20);
        StringBuilder sb = new StringBuilder("§eLampe ");
        for (int i = 0; i < 5; i++) sb.append(i < bars ? "§e▮" : "§8▮");
        return sb.append(" §7").append((int) b).append(" %").toString();
    }

    private void prologueChecks(Player p) {
        if (session.stage >= 1 && ecole.directeur.contains(p.getLocation().toVector())) say(11);
        if (session.stage >= 2 && ecole.couloirOuest.contains(p.getLocation().toVector())) say(9);
        if (session.stage >= 3 && ecole.cagibi.contains(p.getLocation().toVector())) say(12);
        if (session.stage == 1 && rnd.nextDouble() < 0.01) say(8);
    }

    private void mazeChecks(Player p) {
        if (session.exitOpen && maze.exitRoom != null && maze.exitRoom.contains(p.getLocation().toVector()) && won.add(p.getUniqueId())) {
            p.showTitle(Title.title(LEG.deserialize("§a§lFin de la version de test"),
                    LEG.deserialize("§7Merci d'avoir joue a Hors Cours !"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && session != null && session.players.contains(p.getUniqueId())) leave(p, true);
            }, 120L);
        }
        // Tombé hors de la carte (ne devrait pas arriver)
        if (p.getY() < Maze.Y - 6) p.teleport(maze.start());
    }

    private void noclip(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.AMBIENT, 0.4f, 1.6f);
        p.showTitle(Title.title(Component.text(""), LEG.deserialize("§8..."),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (session != null && session.players.contains(p.getUniqueId()) && p.getWorld().equals(world)) goLevel1(p);
        }, 20L);
    }

    private void goLevel1(Player p) {
        restoreLight(p);
        level.put(p.getUniqueId(), 1);
        p.teleport(maze.start());
        if (session.stage < 3) {
            // (admin /horscours niveau 1) : donner les objets manquants
            session.stage = 3;
            if (!p.getInventory().contains(Material.LANTERN)) p.getInventory().addItem(lamp());
            if (!p.getInventory().contains(Material.WHITE_DYE)) p.getInventory().addItem(chalk());
        }
        if (!session.levelStarted) {
            session.levelStarted = true;
            session.levelStartTick = session.tick;
            objective("§fFusibles : §e0/" + maze.fuseCells.size());
            later(40, () -> say(13));
            later(140, () -> say(14));
        }
    }

    private void spawnLoic() {
        // Cellule la plus loin de tous les joueurs du labyrinthe
        List<Player> inMaze = new ArrayList<>();
        for (Player p : online()) if (level.getOrDefault(p.getUniqueId(), 0) == 1) inMaze.add(p);
        int bi = maze.n - 1, bj = maze.n - 1, best = -1;
        int[][] minD = new int[maze.n][maze.n];
        for (int[] r : minD) java.util.Arrays.fill(r, Integer.MAX_VALUE);
        for (Player p : inMaze) {
            int[] c = maze.cellAt(p.getX(), p.getZ());
            if (c == null) continue;
            int[][] d = maze.bfs(c[0], c[1]);
            for (int i = 0; i < maze.n; i++)
                for (int j = 0; j < maze.n; j++) if (d[i][j] >= 0) minD[i][j] = Math.min(minD[i][j], d[i][j]);
        }
        for (int i = 0; i < maze.n; i++)
            for (int j = 0; j < maze.n; j++)
                if (minD[i][j] != Integer.MAX_VALUE && minD[i][j] > best) {
                    best = minD[i][j];
                    bi = i;
                    bj = j;
                }
        loic.spawn(bi, bj);
        if (session.said.contains("v15")) return;
        say(15);
        say(16);
        say(17);
    }

    private void caught(Player p) {
        session.catches++;
        screamer(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (session != null && session.players.contains(p.getUniqueId()) && p.getWorld().equals(world)) {
                restoreLight(p);
                p.teleport(maze.start());
            }
        }, 25L);
        long now = System.currentTimeMillis();
        if (now - session.lastCatchLine > 20000) {
            session.lastCatchLine = now;
            sayAgain(session.catches >= 3 ? 55 : 21);
        }
        // Loïc repart loin
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (session != null && loic.alive()) spawnLoic();
        }, 30L);
    }

    /**
     * Screamer façon FNAF : la tête de Loïc fonce vers l'écran (5 tailles du même glyphe de la police
     * eemicraft:horscours_screamer), tremble, parasites d'image (glyphe de bruit), flash rouge, cris
     * superposés, image qui se tord (nausée) puis noir. « Vous avez deux minutes ? » à la fin.
     */
    void screamer(Player p) {
        screamerUntil.put(p.getUniqueId(), System.currentTimeMillis() + 3000);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 110, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 6, false, false, false));
        net.kyori.adventure.key.Key font = net.kyori.adventure.key.Key.key("eemicraft", "horscours_screamer");
        String[] frames = {"", "", "", "", "", "", "", "",
                "", "", "", "", "", ""};
        Location l = p.getLocation();
        p.playSound(l, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 1f, 0.5f);
        p.playSound(l, Sound.ENTITY_GHAST_SCREAM, SoundCategory.MASTER, 1f, 0.6f);
        p.playSound(l, Sound.ENTITY_WARDEN_ROAR, SoundCategory.MASTER, 1f, 1.3f);
        p.playSound(l, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.MASTER, 1f, 0.7f);
        p.playSound(l, Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 1f, 0.7f);
        for (int k = 0; k < frames.length; k++) {
            final int f = k;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline() || !p.getWorld().equals(world)) return;
                String shake = f % 2 == 0 ? "" : ""; // décalage à gauche / à droite : l'image tremble
                Component face = Component.text(shake + frames[f]).font(font);
                Component sub = f >= 8 ? LEG.deserialize("§4§l« VOUS AVEZ DEUX MINUTES ? »") : Component.empty();
                boolean last = f == frames.length - 1;
                p.showTitle(Title.title(face, sub, Title.Times.times(Duration.ZERO,
                        Duration.ofMillis(last ? 700 : 150), Duration.ofMillis(last ? 400 : 0))));
                if (f == 4) p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.MASTER, 1f, 0.65f);
                if (f == 8) p.playSound(p.getLocation(), Sound.ENTITY_GHAST_HURT, SoundCategory.MASTER, 1f, 0.5f);
            }, k * 2L);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.getWorld().equals(world))
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        }, frames.length * 2L);
    }

    // ------------------------------------------------------------------ musique et ambiance
    private final Map<UUID, Long> musicAt = new HashMap<>();
    private final Map<UUID, Integer> musicIdx = new HashMap<>();
    private final Map<UUID, Long> droneAt = new HashMap<>();
    private final Map<UUID, Long> heartAt = new HashMap<>();
    private static final Sound[] TRACKS = {Sound.MUSIC_OVERWORLD_DEEP_DARK, Sound.MUSIC_DISC_13,
            Sound.MUSIC_OVERWORLD_DEEP_DARK, Sound.MUSIC_DISC_11};

    /**
     * Grosse ambiance sonore en couches : musique (musique du Deep Dark, disques 13 et 11, en rotation toutes
     * les ~3 min), nappe grave en continu (boucle de la vallée des âmes, ralentie), bruits de grotte au
     * hasard, battements de cœur de plus en plus rapides quand Loïc approche.
     */
    private void ambience(Player p, int lv) {
        if (!getConfig().getBoolean("music.enabled", true)) return;
        float vol = (float) getConfig().getDouble("music.volume", 0.5);
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        if (now - musicAt.getOrDefault(id, 0L) > 175_000) {
            musicAt.put(id, now);
            for (Sound t : TRACKS) p.stopSound(t);
            int i = (musicIdx.getOrDefault(id, -1) + 1) % TRACKS.length;
            musicIdx.put(id, i);
            p.playSound(p, TRACKS[i], SoundCategory.AMBIENT, vol + 0.2f, lv == 1 ? 0.85f : 1f);
        }
        if (now - droneAt.getOrDefault(id, 0L) > 36_000) {
            droneAt.put(id, now);
            p.playSound(p, Sound.AMBIENT_SOUL_SAND_VALLEY_LOOP, SoundCategory.AMBIENT, vol, 0.6f);
            p.playSound(p, Sound.AMBIENT_BASALT_DELTAS_LOOP, SoundCategory.AMBIENT, vol * 0.5f, 0.5f);
        }
        if (rnd.nextDouble() < 0.004)
            p.playSound(p.getLocation().add(rnd.nextGaussian() * 6, 0, rnd.nextGaussian() * 6), Sound.AMBIENT_CAVE,
                    SoundCategory.AMBIENT, vol + 0.3f, 0.8f + rnd.nextFloat() * 0.4f);
        if (lv == 1 && loic.alive()) {
            double d = loic.distTo(p);
            if (d < 16) {
                long gap = (long) (350 + d * 70); // 350 ms tout près, ~1,4 s à 16 blocs
                if (now - heartAt.getOrDefault(id, 0L) > gap) {
                    heartAt.put(id, now);
                    p.playSound(p, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1f, 1f);
                }
            }
        }
    }

    private void stopAmbience(Player p) {
        UUID id = p.getUniqueId();
        musicAt.remove(id);
        musicIdx.remove(id);
        droneAt.remove(id);
        heartAt.remove(id);
        for (Sound t : TRACKS) p.stopSound(t);
        p.stopSound(Sound.AMBIENT_SOUL_SAND_VALLEY_LOOP);
        p.stopSound(Sound.AMBIENT_BASALT_DELTAS_LOOP);
    }

    // ------------------------------------------------------------------ digicode et cachettes
    @EventHandler
    public void onKeypadClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Niveau1.Keypad k)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        n1.keypadClick(p, k, e.getSlot());
    }

    @EventHandler
    public void onKeypadDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Niveau1.Keypad) e.setCancelled(true);
    }

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent e) {
        if (e.isSneaking() && n1.inLocker(e.getPlayer())) n1.unhide(e.getPlayer());
    }

    @EventHandler
    public void onMoveHidden(org.bukkit.event.player.PlayerMoveEvent e) {
        if (!n1.inLocker(e.getPlayer())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location keep = f.clone();
            keep.setYaw(t.getYaw());
            keep.setPitch(t.getPitch());
            e.setTo(keep);
        }
    }

    // ------------------------------------------------------------------ protections
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getWorld().equals(world) && e.getEntity() instanceof Player) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(world) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().equals(world) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        // Quitter le monde du jeu (/lobby, autre jeu...) = quitter la session, sans toucher à l'inventaire
        Player p = e.getPlayer();
        if (!e.getFrom().equals(world)) return;
        leave(p, false);
        // Le monde horscours n'est pas géré par Multiverse-Inventories : on retire nous-mêmes NOS objets
        // (lampe, craie), seulement eux, et on remet la survie au hub.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || p.getWorld().equals(world)) return;
            stripItems(p);
            World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
            if (hub != null && p.getWorld().equals(hub) && p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(GameMode.SURVIVAL);
        }, 2L);
    }

    /** Retire uniquement les objets du jeu (marqués par le plugin) : ne touche à rien d'autre. */
    private void stripItems(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++)
            if (itemId(c[i]) != null) p.getInventory().setItem(i, null);
        if (itemId(p.getItemOnCursor()) != null) p.setItemOnCursor(null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.getWorld().equals(world)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !p.getWorld().equals(world)) stripItems(p);
            }, 10L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && (session == null || !session.players.contains(p.getUniqueId()))) sendToHub(p);
        }, 10L);
    }
}
