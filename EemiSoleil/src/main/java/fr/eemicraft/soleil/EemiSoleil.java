package fr.eemicraft.soleil;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.eemicraft.soleil.build.RecordingSink;
import fr.eemicraft.soleil.build.bukkit.BukkitSink;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Squid Game (ex-« 1, 2, 3 Soleil », id EemiCore « soleil ») : une partie = une suite d'epreuves de la saison 1, dans
 * l'ordre de la serie, puis une finale en 1 contre 1.
 *
 * <p>Programme selon le nombre N de joueurs au DEPART (seuils « programme.* » du config ; une epreuve est aussi sautee
 * si, au moment de la jouer, il reste moins que son minimum : corde 4, les autres 3) :
 * <pre>
 *   N = 2       : finale directe
 *   N = 3       : pont de verre -> finale
 *   N = 4..5    : 1 2 3 Soleil -> pont de verre -> finale
 *   N = 6..7    : 1 2 3 Soleil -> dalgona -> billes -> pont de verre -> finale
 *   N = 8..16   : 1 2 3 Soleil -> dalgona -> tir a la corde -> billes -> pont de verre -> finale
 * </pre>
 * Regle des % : chaque epreuve elimine entre min % et max % des joueurs EN JEU a son debut (arrondis : min vers le haut,
 * max vers le bas, max plafonne pour en laisser 2). Au-dela du max, les eliminations sont annulees (« gracie ») ; sous le
 * min, departage des moins bons. La derniere epreuve avant la finale doit laisser exactement 2 joueurs (min = max =
 * N - 2) ; s'il ne reste que 2 joueurs, on passe directement a la finale. Il reste toujours au moins 2 joueurs, sauf
 * abandon (le dernier present gagne alors par forfait).
 */
public final class EemiSoleil extends JavaPlugin implements Listener {
    static final String P = "&d&l[Squid Game] &r";
    static final String TAG = "eemisoleil";
    static final int FY = MapBuilder.FY;

    /** Versions de construction : une zone dont la version change est reconstruite au demarrage. */
    static final Map<String, Integer> ZONE_VERSION = new LinkedHashMap<>();

    static {
        ZONE_VERSION.put("terrain", 4);
        ZONE_VERSION.put("dalgona", 2);
        ZONE_VERSION.put("corde", 2);
        ZONE_VERSION.put("billes", 2);
        ZONE_VERSION.put("pont", 2);
        ZONE_VERSION.put("finale", 1);
    }

    static final String[] ORDER = {"soleil", "dalgona", "corde", "billes", "pont"};

    static final String DEFAULT_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTc5Mzk2OTIyNjM0NiwKICAicHJvZmlsZUlkIiA6ICJlYzdkYzZmZTVkM2M0ZWNiYjJiYzEwM2E3NmQ4NmE5NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJQYXBlck1hY2hpbmU5MSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kNjdlMDM0OTNjNjZmYjQyNDllZDg2MTViY2MzMmU2NTc5ZWY2ODVhMGMyZDIxNmY5ZDNmYzJhZTEyZGEwYjJmIgogICAgfQogIH0KfQ==";
    static final String DEFAULT_SIG = "BD5grlN+honUsj/UIIWcy+QIw0sXdBveK/UKph1ittlR7+y6Cq1cFDOsT/DkNZYdDr7UTuxbFaqZnXkHb52GkeB8vJr3CJSLfUCGfo4BsW8D7jubPq/uXq3PHMeBWmLEga05qPQ8bbPYLsJNnEaorOSDwkgAyY+0c5pO8/NTjXzaLXE6CdROWJNRoD5Z/o7sbaj9yhk6L/sKUEUqH8hlIUO32PxSF7rS/v6QCy8xSzndeJX7BDvjrh4kyAZ2zm5+Pcclnf72hKokuWd1aAaUi9PHxnh7k7pB7ptYDd9lW2SXkmwYCtvke5nuf44pn5sn+LDL2v8jLkfQOJL58Ff6inFyRVdgjjm1A2WFhZfZen/8lqOh0D8H5d3dHPvN+prBnVEKyDG0JIGPinJZDDzJhukTmT966QqrZCgK1fBMX0BSGDgzbjHSqpOveXnyhKgyviVdQqVKuzQFMf2w4yxW7qcgepLLDledYSufyVTLCPSMrD++bqFjEntHI5/z9m48u2/WYFC4DePTTZel31PYBqksU9fbYOqv4rWr9WrlJbQaAu87HwYA6NGT4b0BGYtGUFOfD9XMWIvEwEw/yUINqijA7F5LROEa+Bu+3KoN8vEGtA1dcDqSv2lKWkmGYHtBoGLx/lmYAuFjHcOCaxr6NhVMkHuWKBwzAHyySD/mBbA=";

    enum State { WAITING, COUNTDOWN, PLAYING, ENDING }

    /** Un concurrent de la partie. */
    static final class Joueur {
        final UUID id;
        String name;
        int num;
        boolean alive = true;
        boolean abandoned;

        Joueur(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        String numero() {
            return String.format("%03d", num);
        }

        Player player() {
            return Bukkit.getPlayer(id);
        }
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();

    World world;
    State state = State.WAITING;
    final Map<UUID, Joueur> players = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    final Set<UUID> gone = new HashSet<>();
    final Map<UUID, Location> anchors = new HashMap<>();
    /** 2.1.0 : spectateurs venus par /soleil join pendant un jeu (« tu joueras au suivant ») : mis dans la file a la fin. */
    final Set<UUID> wantsNext = new HashSet<>();
    /** 2.1.0 : cle PDC de l'objet « Retour au lobby » du dortoir. */
    org.bukkit.NamespacedKey leaveKey;
    int gen;
    int countdown;

    // ------------------------------------------------------------ partie en cours
    List<String> programme = new ArrayList<>();
    int progIdx;
    Epreuve current;
    boolean intermission;
    int interLeft;
    int epNumber;
    int startCount;
    boolean testMode;
    String winnerName;
    final Map<UUID, Scoreboard> boards = new HashMap<>();
    BossBar bar;
    final List<Entity> zoneEntities = new ArrayList<>();

    // ------------------------------------------------------------ poupee et panneaux du terrain
    Mannequin doll;
    float dollYaw = 180f;
    final List<Entity> displays = new ArrayList<>();
    boolean spawning;

    // ------------------------------------------------------------ construction etalee
    final Map<String, Integer> builtVersion = new HashMap<>();
    final Deque<String> buildQueue = new ArrayDeque<>();
    String buildId;
    RecordingSink buildRec;
    BukkitSink buildOut;
    int[] buildBox;
    int bx, by, bz;
    long buildT0;

    BukkitTask secTask;
    BukkitTask gameTask;

    static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        s.sendMessage(c(P + m));
    }

    void tell(String m) {
        for (Player p : online()) msg(p, m);
    }

    static Title.Times times(long in, long stay, long out) {
        return Title.Times.times(Duration.ofMillis(in), Duration.ofMillis(stay), Duration.ofMillis(out));
    }

    void title(Player p, String t, String sub, long stay) {
        p.showTitle(Title.title(c(t), c(sub), times(150, stay, 500)));
    }

    void titleAll(String t, String sub, long stay) {
        for (Player p : online()) title(p, t, sub, stay);
    }

    void soundAll(Sound s, float vol, float pitch) {
        for (Player p : online()) p.playSound(p.getLocation(), s, vol, pitch);
    }

    /** Tous les gens suivis par la partie (concurrents non partis + spectateurs). */
    List<UUID> all() {
        List<UUID> l = new ArrayList<>(players.keySet());
        l.removeAll(gone);
        for (UUID s : spectators) if (!l.contains(s)) l.add(s);
        return l;
    }

    List<Player> online() {
        List<Player> l = new ArrayList<>();
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) l.add(p);
        }
        return l;
    }

    List<Joueur> aliveList() {
        List<Joueur> l = new ArrayList<>();
        for (Joueur j : players.values()) if (j.alive && !gone.contains(j.id)) l.add(j);
        return l;
    }

    int aliveCount() {
        return aliveList().size();
    }

    Joueur joueur(Player p) {
        return p == null ? null : players.get(p.getUniqueId());
    }

    /** Tache planifiee liee a la partie en cours (annulee si la partie change). */
    void later(long ticks, Runnable r) {
        int g = gen;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (g == gen) r.run();
        }, ticks);
    }

    double cfgD(String k, double def) {
        return getConfig().getDouble(k, def);
    }

    int cfgI(String k, int def) {
        return getConfig().getInt(k, def);
    }

    int ticks(String k, double def) {
        return Math.max(1, (int) Math.round(cfgD(k, def) * 20));
    }

    int minPlayers() {
        return Math.max(2, cfgI("partie.min-joueurs", 4));
    }

    int maxPlayers() {
        // 16 postes de dalgona, 8 paires de billes, 16 places au tir a la corde : 16 au plus
        return Math.max(2, Math.min(16, cfgI("partie.max-joueurs", 16)));
    }

    // ================================================================ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        leaveKey = new org.bukkit.NamespacedKey(this, "leave");
        File f = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        // ancienne cle (1.x) : version du terrain
        builtVersion.put("terrain", data.getInt("zones.terrain", data.getInt("build-version", 0) >= 3 ? 3 : 0));
        for (String id : ZONE_VERSION.keySet()) if (!id.equals("terrain")) builtVersion.put(id, data.getInt("zones." + id, 0));
        createWorld();
        getServer().getPluginManager().registerEvents(this, this);
        if (world != null) {
            cleanEntities();
            for (Map.Entry<String, Integer> e : ZONE_VERSION.entrySet())
                if (!e.getValue().equals(builtVersion.get(e.getKey()))) buildQueue.add(e.getKey());
            if (buildQueue.isEmpty()) {
                spawnEntities();
            } else {
                getLogger().info("Zones a construire : " + buildQueue + " (etale sur plusieurs ticks).");
            }
            resetPiggy();
        }
        secTask = Bukkit.getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        gameTask = Bukkit.getScheduler().runTaskTimer(this, this::gameTick, 1L, 1L);
        sendStatus();
    }

    @Override
    public void onDisable() {
        if (current != null) {
            try {
                current.cleanup();
            } catch (RuntimeException ex) {
                getLogger().warning("Nettoyage de l'epreuve : " + ex);
            }
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p);
                if (inWorld(p)) p.teleport(hub());
            }
        }
        players.clear();
        spectators.clear();
        clearZoneEntities();
        cleanEntities();
    }

    static final class Empty extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }

    private void createWorld() {
        String name = getConfig().getString("world", "soleil");
        world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new Empty()).environment(World.Environment.NORMAL).generateStructures(false).createWorld();
            getLogger().info("Monde " + name + " charge.");
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
        // les coups entre joueurs (poussees du soleil, couteau de la finale) doivent emettre l'evenement de degats
        world.setGameRule(GameRules.PVP, true);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnLocation(hallSpawn());
        for (int cx = -2; cx <= 1; cx++)
            for (int cz = -6; cz <= 5; cz++) world.setChunkForceLoaded(cx, cz, true);
    }

    // ================================================================ construction etalee

    int blocksPerTick() {
        return Math.max(2000, cfgI("construction.blocs-par-tick", 40000));
    }

    static int[] zoneBox(String id) {
        if (id.equals("terrain")) {
            return new int[]{MapBuilder.BX1, MapBuilder.BY1, MapBuilder.BZ1, MapBuilder.BX2, MapBuilder.BY2, MapBuilder.BZ2};
        }
        return SquidZones.box(id);
    }

    boolean building() {
        return buildId != null || !buildQueue.isEmpty();
    }

    void setZoneLoaded(int[] b, boolean on) {
        if (world == null || b == null) return;
        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++)
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                // le terrain et le dortoir restent toujours charges
                if (!on && cx >= -2 && cx <= 1 && cz >= -6 && cz <= 5) continue;
                world.setChunkForceLoaded(cx, cz, on);
            }
    }

    /** Chaque tick : construit la zone en tete de file en memoire, puis la recopie par tranches. */
    private void buildStep() {
        if (world == null) return;
        if (buildId == null) {
            if (buildQueue.isEmpty()) return;
            if (state == State.PLAYING || state == State.ENDING) return;
            buildId = buildQueue.poll();
            buildT0 = System.currentTimeMillis();
            buildRec = new RecordingSink();
            List<String> warns;
            try {
                if (buildId.equals("terrain")) {
                    MapBuilder b = new MapBuilder(buildRec);
                    b.build();
                    warns = b.warnings;
                } else {
                    SquidZones z = new SquidZones(buildRec);
                    z.build(buildId);
                    warns = z.warnings;
                }
            } catch (RuntimeException ex) {
                getLogger().severe("Construction de la zone " + buildId + " impossible : " + ex);
                buildId = null;
                buildRec = null;
                return;
            }
            for (String w : warns) getLogger().warning("Controle de la zone " + buildId + " : " + w);
            buildBox = zoneBox(buildId);
            setZoneLoaded(buildBox, true);
            buildOut = new BukkitSink(world, getLogger());
            bx = buildBox[0];
            by = buildBox[1];
            bz = buildBox[2];
            getLogger().info("Zone " + buildId + " preparee en memoire (" + buildRec.count() + " blocs), copie dans le monde...");
            return;
        }
        int budget = blocksPerTick();
        int[] b = buildBox;
        while (budget-- > 0) {
            buildOut.set(bx, by, bz, buildRec.get(bx, by, bz));
            if (++bx > b[3]) {
                bx = b[0];
                if (++bz > b[5]) {
                    bz = b[2];
                    if (++by > b[4]) {
                        finishZone();
                        return;
                    }
                }
            }
        }
    }

    private void finishZone() {
        String id = buildId;
        if (!buildOut.invalidStates().isEmpty()) getLogger().warning(buildOut.invalidStates().size() + " etat(s) de bloc invalide(s) dans " + id + ".");
        getLogger().info("Zone " + id + " construite : " + buildRec.count() + " blocs, " + buildOut.operations() + " poses en "
                + (System.currentTimeMillis() - buildT0) + " ms (etale).");
        builtVersion.put(id, ZONE_VERSION.get(id));
        if (!id.equals("terrain")) setZoneLoaded(buildBox, false);
        saveData();
        buildId = null;
        buildRec = null;
        buildOut = null;
        if (id.equals("terrain") || buildQueue.isEmpty()) {
            cleanEntities();
            spawnEntities();
            resetPiggy();
        }
        if (buildQueue.isEmpty()) {
            getLogger().info("Toutes les zones de Squid Game sont construites.");
            tell("&aLes épreuves sont prêtes.");
        }
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : builtVersion.entrySet()) y.set("zones." + e.getKey(), e.getValue());
        try {
            getDataFolder().mkdirs();
            y.save(new File(getDataFolder(), "data.yml"));
        } catch (IOException e) {
            getLogger().warning("data.yml : " + e.getMessage());
        }
    }

    // ================================================================ lieux

    Location hallSpawn() {
        return new Location(world, 0.5, FY + 1, -60.5, 0f, 0f);
    }

    /** Place au dortoir (allee centrale), tournee vers le panneau du programme. */
    Location hallSpot(int i) {
        return new Location(world, -0.5 + (i % 3), FY + 1, -70.5 + (i / 3) * 2.0, 0f, 0f);
    }

    Location hallSpec() {
        return new Location(world, 0.5, FY + 8, -48.5, 180f, 15f);
    }

    Location hub() {
        World w = Bukkit.getWorld(getConfig().getString("hub.world", "lobby"));
        if (w == null) return Bukkit.getWorlds().get(0).getSpawnLocation();
        return new Location(w, cfgD("hub.x", 722.5), cfgD("hub.y", 51), cfgD("hub.z", 650.5),
                (float) cfgD("hub.yaw", 90), (float) cfgD("hub.pitch", 0));
    }

    boolean inWorld(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    // ================================================================ entites du terrain (poupee, panneaux)

    void cleanEntities() {
        if (doll != null) {
            doll.remove();
            doll = null;
        }
        for (Entity e : displays) e.remove();
        displays.clear();
        if (world != null) {
            for (Entity e : world.getEntities()) if (e.getScoreboardTags().contains(TAG)) e.remove();
        }
    }

    void spawnEntities() {
        if (world == null) return;
        spawnDoll();
        displays.add(text(new Location(world, 0.5, FY + 6, MapBuilder.START_Z + 0.5), "&c&lDÉPART", 3f));
        displays.add(text(new Location(world, 0.5, FY + 9, MapBuilder.FINISH_Z + 0.5), "&a&lARRIVÉE", 3f));
        displays.add(text(new Location(world, 0.5, FY + 6, -43.5),
                "&d&lSQUID GAME\n\n&f1. &e1, 2, 3 Soleil\n&f2. &eBiscuit dalgona\n&f3. &eTir à la corde\n&f4. &eBilles\n"
                        + "&f5. &ePont de verre\n&f6. &c&lFinale du calamar &7(1 contre 1)\n\n&7Moins on est, moins il y a d'épreuves.\n"
                        + "&7Un éliminé regarde la suite jusqu'à la fin.", 1.4f));
    }

    TextDisplay text(Location l, String txt, float scale) {
        spawning = true;
        try {
            return world.spawn(l, TextDisplay.class, t -> {
                t.text(c(txt));
                t.setBillboard(Display.Billboard.VERTICAL);
                t.setPersistent(false);
                t.setShadowed(true);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
                t.setLineWidth(400);
                t.setViewRange(2f);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                t.setBrightness(new Display.Brightness(15, 15));
                t.addScoreboardTag(TAG);
            });
        } finally {
            spawning = false;
        }
    }

    /** Panneau d'une epreuve (retire au nettoyage). */
    TextDisplay zoneText(Location l, String txt, float scale) {
        TextDisplay t = text(l, txt, scale);
        zoneEntities.add(t);
        return t;
    }

    void clearZoneEntities() {
        for (Entity e : zoneEntities) if (e != null) e.remove();
        zoneEntities.clear();
    }

    void spawnDoll() {
        if (world == null) return;
        if (doll != null) {
            doll.remove();
            doll = null;
        }
        String tex = getConfig().getString("doll.texture", DEFAULT_TEX);
        String sig = getConfig().getString("doll.signature", DEFAULT_SIG);
        double scale = Math.max(0.5, Math.min(16, cfgD("doll.scale", 3.8)));
        spawning = true;
        try {
            Mannequin m = world.spawn(new Location(world, MapBuilder.DOLL_X, FY + 1, MapBuilder.DOLL_Z, dollYaw, 0f), Mannequin.class, e -> {
                if (tex != null && !tex.isEmpty()) {
                    e.setProfile(ResolvableProfile.resolvableProfile().name("Poupee")
                            .addProperty(new ProfileProperty("textures", tex, sig == null || sig.isEmpty() ? null : sig)).build());
                }
                initStatue(e);
                AttributeInstance a = e.getAttribute(Attribute.SCALE);
                if (a != null) a.setBaseValue(scale);
            });
            if (m.isValid()) {
                doll = m;
                applyYaw(dollYaw);
            } else {
                getLogger().warning("La poupee n'a pas pu apparaitre.");
            }
        } finally {
            spawning = false;
        }
    }

    void initStatue(Mannequin e) {
        e.setImmovable(true);
        e.setInvulnerable(true);
        e.setGravity(false);
        e.setSilent(true);
        e.setPersistent(false);
        e.setCollidable(false);
        e.setCustomNameVisible(false);
        try {
            e.setDescription(null);
        } catch (RuntimeException ex) {
            e.setDescription(Component.empty());
        }
        e.addScoreboardTag(TAG);
    }

    void applyYaw(float y) {
        dollYaw = y;
        if (doll != null && doll.isValid()) {
            Location l = doll.getLocation();
            l.setYaw(y);
            l.setPitch(0f);
            doll.teleport(l);
            doll.setRotation(y, 0f);
            doll.setBodyYaw(y);
        }
    }

    /** Position d'un oeil de la poupee (cote -1 ou 1). */
    Location eye(int side) {
        double s = Math.max(0.5, Math.min(16, cfgD("doll.scale", 3.8)));
        double yaw = Math.toRadians(dollYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = Math.cos(yaw), rz = Math.sin(yaw);
        Location base = doll != null && doll.isValid() ? doll.getLocation() : new Location(world, MapBuilder.DOLL_X, FY + 1, MapBuilder.DOLL_Z);
        return new Location(world, base.getX() + fx * 0.27 * s + rx * 0.125 * s * side, base.getY() + 1.62 * s,
                base.getZ() + fz * 0.27 * s + rz * 0.125 * s * side);
    }

    void laser(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector());
        double len = v.length();
        if (len < 0.1) return;
        v.normalize().multiply(0.4);
        Location cur = from.clone();
        Particle.DustOptions red = new Particle.DustOptions(Color.RED, 0.9f);
        for (double d = 0; d < len && d < 120; d += 0.4) {
            world.spawnParticle(Particle.DUST, cur, 1, 0, 0, 0, 0, red, true);
            cur.add(v);
        }
    }

    /** Garde en combinaison rose (Mannequin), masque rond / triangle / carre. Retire au nettoyage de l'epreuve. */
    Mannequin guard(Location l, int mask) {
        spawning = true;
        try {
            Mannequin m = world.spawn(l, Mannequin.class, e -> {
                initStatue(e);
                var eq = e.getEquipment();
                String[] masks = {"rond", "triangle", "carre"};
                String mk = masks[Math.floorMod(mask, 3)];
                if (textures()) {
                    eq.setHelmet(outfit(new ItemStack(Material.LEATHER_HELMET), EquipmentSlot.HEAD, "squid_garde_" + mk, "masque_" + mk));
                    eq.setChestplate(outfit(new ItemStack(Material.LEATHER_CHESTPLATE), EquipmentSlot.CHEST, "squid_garde_" + mk, "garde_haut"));
                    eq.setLeggings(outfit(new ItemStack(Material.LEATHER_LEGGINGS), EquipmentSlot.LEGS, "squid_garde_" + mk, "garde_bas"));
                    eq.setBoots(outfit(new ItemStack(Material.LEATHER_BOOTS), EquipmentSlot.FEET, "squid_garde_" + mk, "garde_pieds"));
                } else {
                    eq.setHelmet(dyed(Material.LEATHER_HELMET, 0x111111, "Masque"));
                    eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, 0xE0457B, "Combinaison"));
                    eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, 0xE0457B, "Combinaison"));
                    eq.setBoots(dyed(Material.LEATHER_BOOTS, 0x111111, "Bottes"));
                }
            });
            zoneEntities.add(m);
            return m;
        } finally {
            spawning = false;
        }
    }

    /** WorldGuard bloque les apparitions par plugin : on retablit les notres. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        if (spawning && world != null && e.getEntity().getWorld().equals(world)) e.setCancelled(false);
    }

    // ================================================================ objets et textures du pack

    /** Textures du pack EEMICRAFT (tenues, masques, aiguille, billes...). Relu a chaque usage. */
    boolean textures() {
        return getConfig().getBoolean("textures", false);
    }

    static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Pose le modele eemicraft:squid/<model> si les textures sont activees (sinon objet vanilla inchange). */
    ItemStack skin(ItemStack it, String model) {
        if (it != null && textures()) it.setData(DataComponentTypes.ITEM_MODEL, Key.key("eemicraft", "squid/" + model));
        return it;
    }

    /** Piece de tenue du pack : equipement eemicraft:<asset> porte, icone eemicraft:squid/<model>, sans point d'armure. */
    ItemStack outfit(ItemStack it, EquipmentSlot slot, String asset, String model) {
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(Key.key("eemicraft", asset)).build());
        it.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().build());
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("eemicraft", "squid/" + model));
        return it;
    }

    static ItemStack dyed(Material m, int rgb, String name) {
        ItemStack it = named(m, name);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(Color.fromRGB(rgb));
        it.setItemMeta(lm);
        return it;
    }

    /** Survetement vert numerote. */
    void giveOutfit(Player p, Joueur j) {
        String nm = "&2Tenue n°" + j.numero();
        ItemStack chest, legs;
        if (textures()) {
            String asset = j.num >= 1 && j.num <= 30 ? String.format("squid_joueur_%02d", j.num) : "squid_joueur";
            chest = outfit(named(Material.LEATHER_CHESTPLATE, nm), EquipmentSlot.CHEST, asset, "tenue_haut");
            legs = outfit(named(Material.LEATHER_LEGGINGS, nm), EquipmentSlot.LEGS, asset, "tenue_bas");
        } else {
            chest = dyed(Material.LEATHER_CHESTPLATE, 0x2F8F7A, nm);
            legs = dyed(Material.LEATHER_LEGGINGS, 0x2F8F7A, nm);
        }
        p.getInventory().setChestplate(chest);
        p.getInventory().setLeggings(legs);
        p.getInventory().setBoots(dyed(Material.LEATHER_BOOTS, 0xF2F2F2, "&fBaskets blanches"));
    }

    ItemStack invitation() {
        ItemStack it = skin(named(Material.PAPER, "&6&lCarte d'invitation", "&7○ △ □", "&7Le jeu commence bientôt..."), "invitation");
        if (leaveKey != null) {
            ItemMeta m = it.getItemMeta();
            m.getPersistentDataContainer().set(leaveKey, org.bukkit.persistence.PersistentDataType.STRING, "invitation");
            it.setItemMeta(m);
        }
        return it;
    }

    /** 2.1.0 : retire les objets du dortoir (lit, invitation) d'un joueur deja sorti du monde. */
    void removeWaitingItems(Player p) {
        if (leaveKey == null) return;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(leaveKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                inv.setItem(i, null);
            }
        }
    }

    /** 2.1.0 : lit « Retour au lobby » (case 8 du dortoir) = /soleil leave. */
    ItemStack leaveItem() {
        ItemStack it = named(Material.RED_BED, "&cRetour au lobby &7(clic droit)", "&7Quitte la file du Squid Game");
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(leaveKey, org.bukkit.persistence.PersistentDataType.STRING, "leave");
        it.setItemMeta(m);
        return it;
    }

    boolean isLeaveItem(ItemStack it) {
        return it != null && leaveKey != null && it.hasItemMeta()
                && "leave".equals(it.getItemMeta().getPersistentDataContainer().get(leaveKey, org.bukkit.persistence.PersistentDataType.STRING));
    }

    /** Objets du dortoir (invitation + lit), 4 ticks apres l'arrivee (apres le vidage de prepare). */
    void giveWaitingItems(Player p) {
        UUID id = p.getUniqueId();
        int g0 = gen;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (gen == g0 && p.isOnline() && inWorld(p) && players.containsKey(id) && (state == State.WAITING || state == State.COUNTDOWN)) {
                p.getInventory().setItem(4, invitation());
                p.getInventory().setItem(8, leaveItem());
            }
        }, 4L);
    }

    /** Vide la main et la barre (garde la tenue). */
    void clearHands(Player p) {
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, null);
        p.getInventory().setItemInOffHand(null);
        p.setItemOnCursor(null);
    }

    void resetRange(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (a != null) a.setBaseValue(a.getDefaultValue());
    }

    // ================================================================ boucles

    /** Chaque seconde : poupee presente, decompte d'attente, entracte. */
    private void second() {
        if (world == null) return;
        if (!building()) {
            if (doll == null || !doll.isValid()) spawnDoll();
            else if (!(current instanceof EpSoleil es && es.turning())) applyYaw(dollYaw);
        }
        int min = minPlayers();
        int max = maxPlayers();
        switch (state) {
            case WAITING -> {
                if (players.size() >= min) {
                    state = State.COUNTDOWN;
                    countdown = cfgI("countdown", 20);
                    tell("&aLe jeu commence dans &e" + countdown + " s&a.");
                    sendStatus();
                }
            }
            case COUNTDOWN -> {
                if (players.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, décompte annulé.");
                    sendStatus();
                    return;
                }
                if (building()) {
                    for (Player p : online()) p.sendActionBar(c("&7Construction des épreuves en cours..."));
                    return;
                }
                if (players.size() >= max && countdown > 5) {
                    countdown = 5;
                    tell("&aPartie pleine : départ dans &e5 s&a.");
                }
                countdown--;
                if (countdown <= 0) {
                    start(null);
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (Player p : online()) {
                        title(p, "&d" + countdown, "&7Le jeu va commencer...", 900);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                    }
                }
            }
            case PLAYING -> {
                if (intermission) {
                    interLeft--;
                    if (interLeft <= 0) beginCurrent();
                    else if (interLeft <= 3) soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                }
            }
            default -> {}
        }
    }

    /** Chaque tick : construction etalee, epreuve en cours, affichage. */
    private void gameTick() {
        try {
            buildStep();
        } catch (RuntimeException ex) {
            getLogger().severe("Construction de la zone " + buildId + " abandonnee : " + ex);
            if (buildBox != null && buildId != null && !buildId.equals("terrain")) setZoneLoaded(buildBox, false);
            buildId = null;
            buildRec = null;
            buildOut = null;
        }
        if (state != State.PLAYING || world == null) return;
        Epreuve ep = current;
        if (ep != null && !intermission && !ep.done) {
            ep.tick++;
            try {
                ep.tick();
            } catch (RuntimeException ex) {
                getLogger().severe("Erreur dans l'epreuve " + ep.id + " : " + ex);
                ex.printStackTrace();
                failEpreuve(ep);
            }
        }
        if (state == State.PLAYING && Bukkit.getCurrentTick() % 10 == 0) {
            updateBar();
            updateBoards();
        }
    }

    // ================================================================ deroulement de la partie

    static int runtimeMin(String id) {
        return id.equals("corde") ? 4 : 3;
    }

    /** Programme selon le nombre de joueurs au depart (voir le tableau en tete de classe). */
    List<String> computeProgramme(int n) {
        List<String> l = new ArrayList<>();
        int[] def = {4, 6, 8, 6, 3};
        for (int i = 0; i < ORDER.length; i++) {
            if (n >= Math.max(runtimeMin(ORDER[i]), cfgI("programme." + ORDER[i], def[i]))) l.add(ORDER[i]);
        }
        return l;
    }

    static String nomEpreuve(String id) {
        return switch (id) {
            case "soleil" -> "1, 2, 3 Soleil";
            case "dalgona" -> "Biscuit dalgona";
            case "corde" -> "Tir à la corde";
            case "billes" -> "Billes";
            case "pont" -> "Pont de verre";
            case "finale" -> "Finale du calamar";
            default -> id;
        };
    }

    Epreuve create(String id) {
        return switch (id) {
            case "soleil" -> new EpSoleil(this);
            case "dalgona" -> new EpDalgona(this);
            case "corde" -> new EpCorde(this);
            case "billes" -> new EpBilles(this);
            case "pont" -> new EpPont(this);
            default -> new EpFinale(this);
        };
    }

    /** Lancement (forced = programme impose par /soleil test). */
    void start(List<String> forced) {
        gen++;
        gone.clear();
        anchors.clear();
        state = State.PLAYING;
        testMode = forced != null;
        winnerName = null;
        List<Joueur> list = new ArrayList<>(players.values());
        Collections.shuffle(list);
        int n = 0;
        for (Joueur j : list) {
            Player p = j.player();
            if (p == null || !p.isOnline()) {
                gone.add(j.id);
                j.alive = false;
                j.abandoned = true;
                continue;
            }
            j.num = ++n;
            j.alive = true;
            j.abandoned = false;
        }
        startCount = n;
        programme = forced != null ? new ArrayList<>(forced) : computeProgramme(n);
        progIdx = 0;
        epNumber = 0;
        bar = BossBar.bossBar(c("&dSquid Game"), 1f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS);
        boards.clear();
        for (Joueur j : aliveList()) {
            Player p = j.player();
            clearHands(p);
            giveOutfit(p, j);
            p.setGameMode(GameMode.ADVENTURE);
            p.showBossBar(bar);
            p.setScoreboard(boardFor(p.getUniqueId()));
        }
        for (UUID s : spectators) {
            Player p = Bukkit.getPlayer(s);
            if (p != null) {
                p.showBossBar(bar);
                p.setScoreboard(boardFor(s));
            }
        }
        StringBuilder prog = new StringBuilder();
        for (String id : programme) prog.append(nomEpreuve(id)).append(" &8› &e");
        tell("&d&lLe jeu commence ! &7" + n + " joueurs. Programme : &e" + prog + "&c&lFinale");
        tell("&7Le dernier joueur debout remporte la cagnotte.");
        resetPiggy();
        sendStatus();
        nextStep();
    }

    /** Choisit l'epreuve suivante d'apres les joueurs restants. */
    void nextStep() {
        if (state != State.PLAYING) return;
        clearAnchors();
        List<Joueur> alive = aliveList();
        int n = alive.size();
        if (n <= 1) {
            end(n == 1 ? alive.get(0) : null, n == 1 ? "forfait" : null);
            return;
        }
        // Partie test (/soleil test <epreuve>) : l'epreuve imposee se joue meme a 2 joueurs
        // (pas de minimum de joueurs ; a 2, le quota max est 0 : personne n'est elimine, tout le monde est gracie).
        if (testMode && n >= 2) {
            for (int i = progIdx; i < programme.size(); i++) {
                if (programme.get(i).equals("finale")) continue;
                progIdx = i + 1;
                startEpreuve(programme.get(i), true); // seule epreuve avant la finale : ramene a 2 (0 elimine a 2 joueurs)
                return;
            }
        }
        if (n == 2) {
            startEpreuve("finale", true);
            return;
        }
        String id = null;
        int idx = -1;
        for (int i = progIdx; i < programme.size(); i++) {
            if (programme.get(i).equals("finale")) continue;
            if (n >= runtimeMin(programme.get(i))) {
                id = programme.get(i);
                idx = i;
                break;
            }
        }
        if (id == null) {
            // plus d'epreuve jouable mais plus de 2 joueurs : le pont de verre ramene a 2
            id = "pont";
            idx = programme.size();
        }
        boolean last = true;
        for (int j = idx + 1; j < programme.size(); j++)
            if (!programme.get(j).equals("finale") && n >= runtimeMin(programme.get(j))) last = false;
        progIdx = idx + 1;
        startEpreuve(id, last);
    }

    void startEpreuve(String id, boolean last) {
        Epreuve ep = create(id);
        int n0 = aliveCount();
        ep.last = last;
        if (id.equals("finale")) {
            ep.minElim = 1;
            ep.maxElim = 1;
        } else if (last) {
            ep.minElim = Math.max(0, n0 - 2);
            ep.maxElim = Math.max(0, n0 - 2);
        } else {
            double minP = cfgD("elimination." + id + ".min", defMin(id));
            double maxP = cfgD("elimination." + id + ".max", defMax(id));
            int mx = (int) Math.floor(n0 * maxP / 100.0 + 1e-9);
            int mn = (int) Math.ceil(n0 * minP / 100.0 - 1e-9);
            mx = Math.max(0, Math.min(mx, n0 - 2));
            mn = Math.max(0, Math.min(mn, mx));
            ep.minElim = mn;
            ep.maxElim = mx;
        }
        current = ep;
        epNumber++;
        intermission = true;
        interLeft = Math.max(3, cfgI("partie.entracte", 25)); // 2.0.2 : 25 s pour lire les regles (8 avant)
        setZoneLoaded(ep.box(), true);
        // tout le monde au dortoir
        int i = 0;
        for (Joueur j : aliveList()) {
            Player p = j.player();
            if (p == null) continue;
            resetRange(p);
            clearHands(p);
            clearEffects(p);
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(hallSpot(i++));
            p.setFallDistance(0);
        }
        for (Player p : online()) {
            Joueur j = joueur(p);
            if (j == null || !j.alive) {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(hallSpec());
            }
        }
        updatePiggy();
        String quota = id.equals("finale") ? "&7Un seul gagnant."
                : last ? "&7Il n'en restera que &f2&7."
                : ep.minElim == ep.maxElim ? "&7Éliminés : &f" + ep.minElim
                : "&7Éliminés : &f" + ep.minElim + " &7à &f" + ep.maxElim;
        String head = id.equals("finale") ? "&c&lFINALE" : "&d&lÉpreuve " + epNumber;
        titleAll(head, "&f" + ep.nom() + " &8- &7" + n0 + " joueurs restants", 3500);
        soundAll(Sound.BLOCK_BELL_USE, 1f, 0.8f);
        tell(head + " &8» &f&l" + ep.nom() + " &8(" + n0 + " joueurs) " + quota);
        for (String r : ep.regles()) tell("&8 • &7" + r);
        sendStatus();
    }

    static double defMin(String id) {
        return switch (id) {
            case "corde", "billes" -> 25;
            default -> 0;
        };
    }

    static double defMax(String id) {
        return switch (id) {
            case "soleil", "corde", "billes" -> 60;
            case "dalgona" -> 50;
            default -> 100;
        };
    }

    void beginCurrent() {
        intermission = false;
        if (current == null) return;
        for (Player p : online()) {
            Joueur j = joueur(p);
            if (j == null || !j.alive) {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(current.specSpot());
            }
        }
        Epreuve ep = current;
        try {
            ep.begin(aliveList());
        } catch (RuntimeException ex) {
            getLogger().severe("Erreur au debut de l'epreuve " + ep.id + " : " + ex);
            ex.printStackTrace();
            failEpreuve(ep);
        }
    }

    /**
     * Erreur dans une epreuve : on tente la fin du temps une fois ; si elle echoue aussi (ou pour la finale), l'epreuve
     * est terminee de force (finale : fin du jeu sans gagnant) pour ne jamais bloquer la partie.
     */
    void failEpreuve(Epreuve ep) {
        if (ep == null || ep != current || ep.done) return;
        if (ep.id.equals("finale")) {
            ep.done = true;
            tell("&cErreur dans la finale : jeu arrêté.");
            end(null, null);
            return;
        }
        if (!ep.failedOnce) {
            ep.failedOnce = true;
            try {
                ep.timeUp();
                return;
            } catch (RuntimeException ex2) {
                getLogger().severe("Fin du temps impossible pour " + ep.id + " : " + ex2);
            }
        }
        if (ep == current && !ep.done) {
            tell("&cErreur dans l'épreuve : on passe à la suivante.");
            ep.finish();
        }
    }

    /** Fin d'une epreuve : departage jusqu'au minimum, nettoyage, epreuve suivante. */
    void epreuveDone() {
        Epreuve ep = current;
        if (ep == null || state != State.PLAYING) return;
        List<Joueur> rank = ep.ranking();
        int k = 0;
        while (ep.elimCount < ep.minElim && aliveCount() > 2 && k < rank.size()) {
            Joueur j = rank.get(k++);
            if (!j.alive || gone.contains(j.id)) continue;
            tell("&c⚖ Départage : &f" + j.name + " &7(" + ep.departage() + ")");
            doEliminate(j, "Départage", 0);
            ep.elimCount++;
        }
        int g0 = gen;
        later(60L, () -> {
            if (g0 != gen || current != ep) return;
            closeEpreuve();
            nextStep();
        });
    }

    void closeEpreuve() {
        if (current == null) return;
        try {
            current.cleanup();
        } catch (RuntimeException ex) {
            getLogger().warning("Nettoyage de " + current.id + " : " + ex);
        }
        clearZoneEntities();
        clearAnchors();
        setZoneLoaded(current.box(), false);
        for (Joueur j : aliveList()) {
            Player p = j.player();
            if (p != null) {
                resetRange(p);
                clearEffects(p);
                clearHands(p);
            }
        }
        current = null;
    }

    /** Resultat de la finale. */
    void finaleResult(Joueur winner, Joueur loser) {
        if (state != State.PLAYING) return;
        if (loser != null && loser.alive) doEliminate(loser, "Finale perdue", 0);
        end(winner, null);
    }

    void end(Joueur winner, String how) {
        if (state != State.PLAYING) return;
        if (current != null) {
            closeEpreuve();
        }
        state = State.ENDING;
        intermission = false;
        winnerName = winner == null ? null : winner.name;
        if (!testMode) {
            for (Joueur j : players.values()) if (!j.abandoned) eemi("eemi reward " + j.name + " " + coreId() + " play");
        }
        int prize = prize();
        if (winner != null) {
            if (!testMode) {
                eemi("eemi reward " + winner.name + " " + coreId() + " win");
                if (prize > 0) eemi("eemi give " + winner.name + " coins " + prize);
            }
            String sub = "&e" + winner.name + " &7(n°" + winner.numero() + ")" + (how != null ? " &8- " + how : "");
            titleAll("&6&lGRAND GAGNANT", sub, 5000);
            tell("&6&l★ " + winner.name + " &6remporte le Squid Game" + (how != null ? " par " + how : "") + " !"
                    + (testMode ? " &8(partie test, sans récompense)" : " &e+" + prize + " pièces"));
            if (!testMode) {
                Bukkit.broadcast(c(P + "&e" + winner.name + " &7remporte le &dSquid Game &7(" + startCount + " joueurs, cagnotte &6" + prize + " pièces&7) !"));
            }
            Player wp = winner.player();
            if (wp != null) {
                wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                fireworks(wp.getLocation(), 6);
                wp.teleport(hallSpot(0));
            }
        } else {
            titleAll("&7Fin du jeu", "&7Aucun gagnant", 3000);
            tell("&7Le jeu se termine sans gagnant.");
        }
        updatePiggy();
        if (bar != null) {
            bar.name(c(winner == null ? "&7Fin du jeu" : "&6Grand gagnant : &e" + winner.name));
            bar.color(BossBar.Color.YELLOW);
            bar.progress(1f);
        }
        updateBoards();
        sendStatus();
        // 2.1.0 : fin normale -> retour au dortoir, dans la file du jeu suivant (un /soleil stop garde le retour au hub)
        later(200L, this::finishToWaiting);
    }

    int prize() {
        return Math.max(0, cfgI("recompense.base", 300) + cfgI("recompense.par-elimine", 50) * Math.max(0, startCount - 1));
    }

    int cagnotte() {
        int out = 0;
        for (Joueur j : players.values()) if (!j.alive) out++;
        return Math.max(0, cfgI("recompense.base", 300) + cfgI("recompense.par-elimine", 50) * out);
    }

    void fireworks(Location l, int n) {
        spawning = true;
        try {
            for (int i = 0; i < n; i++) {
                Location at = l.clone().add(ThreadLocalRandom.current().nextDouble(-3, 3), 1, ThreadLocalRandom.current().nextDouble(-3, 3));
                Firework fw = world.spawn(at, Firework.class, f -> {
                    FireworkMeta fm = f.getFireworkMeta();
                    fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.fromRGB(0xE0457B), Color.fromRGB(0x2F8F7A), Color.YELLOW).withFade(Color.WHITE).build());
                    fm.setPower(1);
                    f.setFireworkMeta(fm);
                    f.addScoreboardTag(TAG);
                });
                fw.setSilent(false);
            }
        } finally {
            spawning = false;
        }
    }

    /** Fin : tout le monde au hub, remise a zero. */
    void finish() {
        if (current != null) closeEpreuve();
        gen++;
        List<UUID> everyone = all();
        players.clear();
        spectators.clear();
        gone.clear();
        anchors.clear();
        wantsNext.clear();
        for (UUID id : everyone) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p);
                if (inWorld(p)) p.teleport(hub());
            }
        }
        boards.clear();
        bar = null;
        current = null;
        intermission = false;
        programme = new ArrayList<>();
        state = State.WAITING;
        resetPiggy();
        applyYaw(180f);
        sendStatus();
    }

    /**
     * 2.1.0 (demande d'Esteban) : fin normale d'un jeu. Les concurrents encore la (gagnant, elimines en spectateur ;
     * pas ceux partis) et les spectateurs venus par /soleil join restent inscrits : retour au dortoir, etat d'attente,
     * lit « Retour au lobby » en case 8. Le decompte repart tout seul des qu'il y a assez de joueurs. Les simples
     * spectateurs (/soleil spectate) repartent au hub comme avant.
     */
    void finishToWaiting() {
        if (current != null) closeEpreuve();
        gen++;
        List<UUID> keep = new ArrayList<>();
        for (Joueur j : players.values()) if (!gone.contains(j.id)) keep.add(j.id);
        for (UUID s : spectators) if (wantsNext.contains(s) && !keep.contains(s)) keep.add(s);
        List<UUID> others = new ArrayList<>(spectators);
        others.removeAll(keep);
        BossBar oldBar = bar;
        players.clear();
        spectators.clear();
        gone.clear();
        anchors.clear();
        wantsNext.clear();
        boards.clear();
        bar = null;
        current = null;
        intermission = false;
        programme = new ArrayList<>();
        winnerName = null;
        state = State.WAITING;
        for (UUID id : others) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (oldBar != null) p.hideBossBar(oldBar);
            restore(p);
            if (inWorld(p)) p.teleport(hub());
        }
        int max = maxPlayers();
        for (UUID id : keep) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            if (oldBar != null) p.hideBossBar(oldBar);
            if (!inWorld(p) || players.size() >= max) {
                // deja ailleurs (ou file pleine) : seulement l'etat lobby, sans le deplacer
                if (inWorld(p)) {
                    restore(p);
                    p.teleport(hub());
                } else {
                    p.setGlowing(false);
                    resetRange(p);
                    p.setWalkSpeed(0.2f);
                }
                continue;
            }
            players.put(id, new Joueur(id, p.getName()));
            // teleporter d'abord, l'inventaire est vide par prepare() 2 ticks plus tard (dans le monde du jeu)
            if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
            p.teleport(hallSpawn());
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setGlowing(false);
            clearEffects(p);
            resetRange(p);
            p.setFireTicks(0);
            p.setFallDistance(0);
            p.setWalkSpeed(0.2f);
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
            try {
                AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
                if (!p.isDead()) p.setHealth(hp == null ? 20.0 : hp.getValue());
            } catch (RuntimeException ignored) {
                // joueur en train de partir
            }
            prepare(p, GameMode.ADVENTURE);
            giveWaitingItems(p);
            msg(p, "&7Tu restes dans la file du &dSquid Game&7. Clique sur le lit (ou &f/soleil leave&7) pour revenir au lobby.");
        }
        resetPiggy();
        applyYaw(180f);
        sendStatus();
    }

    // ================================================================ eliminations

    /** Une elimination de plus est-elle permise (quota max de l'epreuve, plancher de 2 joueurs) ? */
    boolean canEliminate() {
        return current != null && current.elimCount < current.maxElim && aliveCount() > 2;
    }

    /**
     * Elimination demandee par une epreuve. Refusee (le joueur est gracie, message) si le quota max est atteint ou s'il
     * ne resterait qu'un joueur. delay : ticks avant le passage en spectateur (chute visible).
     */
    boolean eliminate(Joueur j, String why, int delay) {
        if (j == null || !j.alive) return false;
        if (!canEliminate()) {
            String r = aliveCount() <= 2 ? "il doit rester 2 joueurs pour la finale" : "le quota d'éliminations est atteint";
            tell("&e✦ " + j.name + " &7est &egracié &7(" + r + ").");
            Player p = j.player();
            if (p != null) {
                title(p, "&e&lGRACIÉ", "&7" + why + " &8- mais tu continues", 2200);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.3f);
            }
            return false;
        }
        doEliminate(j, why, delay);
        current.elimCount++;
        return true;
    }

    void doEliminate(Joueur j, String why, int delay) {
        j.alive = false;
        anchors.remove(j.id);
        Player p = j.player();
        if (p != null) {
            Location l = p.getLocation();
            world.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 1.9f);
            world.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 3f, 0.5f);
            world.spawnParticle(Particle.SMOKE, l.clone().add(0, 1, 0), 15, 0.2, 0.4, 0.2, 0.02, null, true);
            title(p, "&c&lÉLIMINÉ", "&7" + why, 2500);
            p.setGlowing(false);
            Runnable spec = () -> {
                if (p.isOnline() && inWorld(p) && !j.alive) {
                    resetRange(p);
                    clearEffects(p);
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(current != null && !intermission ? current.specSpot() : hallSpec());
                }
            };
            if (delay > 0) later(delay, spec);
            else spec.run();
        }
        tell("&c✖ n°" + j.numero() + " " + j.name + " &7est éliminé &8(" + why + ")");
        setNameColor(j);
        updatePiggy();
        sendStatus();
    }

    /** Depart d'un concurrent pendant la partie (deconnexion, /lobby, autre monde) : elimine, sans quota. */
    void abandon(Joueur j) {
        boolean wasAlive = j.alive;
        j.alive = false;
        j.abandoned = true;
        gone.add(j.id);
        anchors.remove(j.id);
        if (!wasAlive || state != State.PLAYING) return;
        tell("&7n°" + j.numero() + " " + j.name + " abandonne.");
        if (current != null && !intermission) current.onLeave(j);
        updatePiggy();
        sendStatus();
        List<Joueur> alive = aliveList();
        if (alive.size() <= 1) {
            end(alive.isEmpty() ? null : alive.get(0), alive.isEmpty() ? null : "forfait");
        } else if (alive.size() == 2 && current != null && !current.id.equals("finale") && !current.done) {
            // l'epreuve n'a plus de sens a 2 (toute elimination serait graciee) : finale directe
            tell("&7Il ne reste que 2 joueurs : direction la finale.");
            if (intermission) {
                closeEpreuve();
                intermission = false;
                nextStep();
            } else {
                current.finish();
            }
        }
    }

    // ================================================================ ancres (joueurs figes)

    void anchor(Player p, Location l) {
        anchors.put(p.getUniqueId(), l.clone());
        p.teleport(l);
        p.setFallDistance(0);
    }

    void clearAnchors() {
        anchors.clear();
    }

    static void clearEffects(Player p) {
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
    }

    // ================================================================ tirelire

    void resetPiggy() {
        setPiggy(0);
    }

    void updatePiggy() {
        int out = 0;
        for (Joueur j : players.values()) if (!j.alive) out++;
        double f = startCount <= 1 ? 0 : Math.min(1, out / (double) (startCount - 1));
        setPiggy(f);
    }

    void setPiggy(double f) {
        if (world == null || building()) return;
        List<int[]> cells = MapBuilder.piggyCells();
        int n = (int) Math.round(f * cells.size());
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            Material m = i < n ? (i % 7 == 3 ? Material.RAW_GOLD_BLOCK : Material.GOLD_BLOCK) : Material.AIR;
            if (world.getBlockAt(c[0], c[1], c[2]).getType() != m) world.getBlockAt(c[0], c[1], c[2]).setType(m, false);
        }
    }

    // ================================================================ affichage

    Scoreboard boardFor(UUID viewer) {
        Scoreboard b = boards.get(viewer);
        if (b != null) return b;
        b = Bukkit.getScoreboardManager().getNewScoreboard();
        for (Joueur j : players.values()) {
            if (j.num <= 0) continue;
            Team t = b.registerNewTeam("n" + j.num);
            t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            t.addEntry(j.name);
            t.prefix(c("&2" + j.numero() + " "));
        }
        boards.put(viewer, b);
        return b;
    }

    void setNameColor(Joueur j) {
        for (Scoreboard b : boards.values()) {
            Team t = b.getTeam("n" + j.num);
            if (t != null) t.prefix(c((j.alive ? "&2" : "&8&m") + j.numero() + "&r "));
        }
    }

    void updateBar() {
        if (bar == null) return;
        if (intermission && current != null) {
            bar.name(c("&dDortoir &8| &fProchaine épreuve : &e" + current.nom() + " &8| &7" + interLeft + " s"));
            bar.color(BossBar.Color.PINK);
            bar.progress(1f);
            return;
        }
        if (current != null) {
            bar.name(c("&d" + (current.id.equals("finale") ? "Finale" : "Épreuve " + epNumber) + " &8| &f" + current.barText()));
            bar.color(current.id.equals("finale") ? BossBar.Color.RED : BossBar.Color.PINK);
            bar.progress(Math.max(0f, Math.min(1f, current.barProgress())));
        }
    }

    void updateBoards() {
        int alive = aliveCount();
        String ep = current == null ? "-" : current.nom();
        for (Player p : online()) {
            Scoreboard b = boardFor(p.getUniqueId());
            if (p.getScoreboard() != b) p.setScoreboard(b);
            Objective o = b.getObjective("squid");
            if (o != null) o.unregister();
            o = b.registerNewObjective("squid", Criteria.DUMMY, c("&d&lSQUID GAME"));
            o.setDisplaySlot(DisplaySlot.SIDEBAR);
            Joueur j = joueur(p);
            int line = 12;
            o.getScore(legacy("&8 ")).setScore(line--);
            o.getScore(legacy("&fÉpreuve : &e" + ep)).setScore(line--);
            o.getScore(legacy("&fJoueurs : &a" + alive + " &7/ " + startCount)).setScore(line--);
            o.getScore(legacy("&fCagnotte : &6" + cagnotte())).setScore(line--);
            o.getScore(legacy("&7 ")).setScore(line--);
            if (j != null && j.num > 0) {
                o.getScore(legacy("&fTon numéro : &2" + j.numero())).setScore(line--);
                o.getScore(legacy(j.alive ? "&aEn jeu" : "&cÉliminé")).setScore(line--);
            } else {
                o.getScore(legacy("&7Spectateur")).setScore(line--);
            }
            if (winnerName != null) o.getScore(legacy("&6Gagnant : &e" + winnerName)).setScore(line--);
        }
    }

    static String legacy(String s) {
        return s.replace('&', '§');
    }

    /** Etat « lobby » : mode, scoreboard, barre, effets, contour, portee, objets du jeu. */
    void restore(Player p) {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        if (bar != null) p.hideBossBar(bar);
        p.setGlowing(false);
        clearEffects(p);
        resetRange(p);
        if (inWorld(p)) {
            p.getInventory().clear();
            p.setItemOnCursor(null);
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
        p.setGameMode(GameMode.SURVIVAL);
        p.setFallDistance(0);
        p.setWalkSpeed(0.2f);
    }

    // ================================================================ entree / sortie

    String coreId() {
        return getConfig().getString("eemicore-id", "soleil");
    }

    void eemi(String cmd) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    void sendStatus() {
        String st = switch (state) {
            case WAITING, COUNTDOWN -> "waiting";
            case PLAYING -> "playing";
            case ENDING -> "ending";
        };
        int waiting = state == State.PLAYING || state == State.ENDING ? 0 : players.size();
        int inGame = state == State.PLAYING ? aliveCount() : 0;
        eemi("eemi status " + coreId() + " " + st + " " + waiting + " " + inGame + " " + maxPlayers());
    }

    void join(Player p) {
        if (world == null) {
            msg(p, "&cLe jeu est indisponible.");
            return;
        }
        if (!canEnter(p)) return;
        UUID id = p.getUniqueId();
        Joueur old = players.get(id);
        if (old != null && gone.contains(id) && (state == State.PLAYING || state == State.ENDING)) {
            msg(p, "&7Un jeu est en cours : tu le regardes, tu joueras au suivant.");
            spectate(p);
            wantsNext.add(id);
            return;
        }
        if (spectators.contains(id) && (state == State.PLAYING || state == State.ENDING) && old == null) {
            // spectateur (/soleil spectate) qui veut jouer : inscrit pour le jeu suivant
            wantsNext.add(id);
            msg(p, "&7Tu joueras au jeu suivant.");
            return;
        }
        if (spectators.contains(id) && (state == State.WAITING || state == State.COUNTDOWN)) {
            spectators.remove(id);
        } else if (old != null || spectators.contains(id)) {
            msg(p, "&7Tu es déjà dans la partie. &f/soleil leave &7pour sortir.");
            return;
        }
        if (state == State.PLAYING || state == State.ENDING) {
            msg(p, "&7Un jeu est en cours : tu le regardes, tu joueras au suivant.");
            spectate(p);
            wantsNext.add(id);
            return;
        }
        int max = maxPlayers();
        if (players.size() >= max) {
            msg(p, "&cLa partie est pleine.");
            return;
        }
        wantsNext.remove(id);
        players.put(id, new Joueur(id, p.getName()));
        p.teleport(hallSpawn());
        prepare(p, GameMode.ADVENTURE);
        giveWaitingItems(p);
        tell("&e" + p.getName() + " &7a accepté l'invitation (&f" + players.size() + "&7/&f" + max + "&7)");
        int min = minPlayers();
        if (players.size() < min) {
            msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
            if (p.hasPermission("eemisoleil.admin")) msg(p, "&8(admin : &7/soleil start &8lance dès 2 joueurs, &7/soleil test <épreuve>&8)");
        }
        sendStatus();
    }

    /** Apres la teleportation : inventaire vide (Multiverse-Inventories a deja range celui du lobby). */
    void prepare(Player p, GameMode gm) {
        p.setGameMode(gm);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && inWorld(p)) {
                p.getInventory().clear();
                p.setItemOnCursor(null);
                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setAllowFlight(p.getGameMode() == GameMode.SPECTATOR);
                p.setFlying(p.getGameMode() == GameMode.SPECTATOR);
                clearEffects(p);
            }
        }, 2L);
    }

    /** On n'entre que depuis le lobby (ou le monde du jeu). */
    boolean canEnter(Player p) {
        List<String> from = getConfig().getStringList("join-from");
        if (!inWorld(p) && !from.isEmpty() && !from.contains(p.getWorld().getName()) && !p.hasPermission("eemisoleil.admin")) {
            msg(p, "&cReviens au lobby (&f/lobby&c) pour rejoindre.");
            return false;
        }
        return true;
    }

    void spectate(Player p) {
        UUID id = p.getUniqueId();
        if (!spectators.contains(id)) spectators.add(id);
        Location at = current != null && !intermission ? current.specSpot() : hallSpec();
        p.teleport(at);
        prepare(p, GameMode.SPECTATOR);
        if (state == State.PLAYING || state == State.ENDING) p.setScoreboard(boardFor(id));
        if (bar != null) p.showBossBar(bar);
    }

    void leave(Player p, boolean teleport, boolean leftWorld) {
        UUID id = p.getUniqueId();
        boolean was = players.containsKey(id) || spectators.contains(id);
        if (!was) return;
        boolean here = inWorld(p) || leftWorld;
        if (gone.contains(id) && !here) return;
        spectators.remove(id);
        wantsNext.remove(id);
        Joueur j = players.get(id);
        if (j != null) {
            if (state == State.PLAYING || state == State.ENDING) {
                if (!gone.contains(id)) {
                    if (j.alive && state == State.PLAYING) abandon(j);
                    else gone.add(id);
                }
            } else {
                players.remove(id);
                tell("&7" + j.name + " quitte la partie.");
            }
        }
        if (p.isOnline() && here) {
            if (leftWorld && !inWorld(p)) {
                // deja dans un autre monde (autre jeu) : on ne touche ni a son mode ni a son scoreboard
                if (bar != null) p.hideBossBar(bar);
                resetRange(p);
                p.setGlowing(false);
                p.setWalkSpeed(0.2f);
                removeWaitingItems(p);
            } else {
                restore(p);
                if (teleport && inWorld(p)) p.teleport(hub());
            }
        }
        sendStatus();
    }

    // ================================================================ evenements

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p) || p.getGameMode() == GameMode.SPECTATOR) return;
        Location to = e.getTo();
        Joueur j = joueur(p);
        if (j == null || !j.alive || state != State.PLAYING) {
            if (to.getY() < FY - 40 && !builder(p)) p.teleport(hallSpawn());
            return;
        }
        Location a = anchors.get(p.getUniqueId());
        if (a != null) {
            double dx = to.getX() - a.getX(), dz = to.getZ() - a.getZ();
            if (dx * dx + dz * dz > 0.35 * 0.35 || to.getY() - a.getY() > 1.3 || a.getY() - to.getY() > 1.5) {
                Location back = a.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                e.setTo(back);
            }
            return;
        }
        if (current != null && !intermission && !current.done) current.onMove(p, j, e);
        else if (intermission && to.getY() < FY - 5) p.teleport(hallSpawn());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (world != null && e.getFrom().equals(world)) leave(e.getPlayer(), false, true);
    }

    /** Le menu spectateur ne doit pas emmener dans un autre monde. */
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        if (world != null && e.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.SPECTATE
                && e.getFrom().getWorld().equals(world) && !world.equals(e.getTo().getWorld())) {
            e.setCancelled(true);
            msg(e.getPlayer(), "&7Fais &f/soleil leave &7pour sortir.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        leave(e.getPlayer(), false, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (inWorld(p)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && inWorld(p)) {
                    restore(p);
                    p.teleport(hub());
                }
            }, 10L);
        }
    }

    private boolean builder(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE;
    }

    /** Aucun degat dans le monde ; certains coups sont des poussees (soleil, finale). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (world == null || !e.getEntity().getWorld().equals(world)) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent ed) || !(ed.getDamager() instanceof Player a) || !(e.getEntity() instanceof Player v)) return;
        if (state != State.PLAYING || current == null || intermission || current.done) return;
        Joueur ja = joueur(a), jv = joueur(v);
        if (ja == null || jv == null || !ja.alive || !jv.alive) return;
        current.onHit(a, ja, v, jv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (world == null || !p.getWorld().equals(world) || builder(p)) return;
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && isLeaveItem(e.getItem())) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (players.containsKey(p.getUniqueId()) || spectators.contains(p.getUniqueId())) leave(p, true, false);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (e.getAction() == Action.PHYSICAL) return;
        Joueur j = joueur(p);
        if (j != null && j.alive && state == State.PLAYING && current != null && !intermission && !current.done) {
            current.onClick(p, j, e);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (world != null && e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (world != null && e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || world == null || !p.getWorld().equals(world)) return;
        if (e.getView().getTopInventory().getHolder() instanceof SquidMenu menu) {
            e.setCancelled(true);
            Joueur j = joueur(p);
            if (e.getClickedInventory() == e.getView().getTopInventory() && j != null && j.alive && current != null && !intermission) {
                // ouvrir / fermer un menu pendant l'evenement de clic est deconseille : on traite au tick suivant
                Epreuve ep = current;
                int slot = e.getRawSlot();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (ep == current && !ep.done && !intermission && j.alive && p.isOnline()) ep.onMenu(p, j, slot, menu);
                });
            }
            return;
        }
        if (!builder(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && world != null && p.getWorld().equals(world) && !builder(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && !(e.getEntity().getShooter() instanceof Player p && builder(p))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleGlide(org.bukkit.event.entity.EntityToggleGlideEvent e) {
        if (world != null && e.getEntity().getWorld().equals(world) && e.isGliding()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity().getWorld().equals(world)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().getWorld().equals(world) && !builder(e.getPlayer())) e.setCancelled(true);
    }

    // ================================================================ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "join" : a[0].toLowerCase();
        boolean admin = s.hasPermission("eemisoleil.admin");
        switch (sub) {
            case "join", "rejoindre" -> {
                if (s instanceof Player p) join(p);
            }
            case "leave", "quitter" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId()) || spectators.contains(p.getUniqueId())) leave(p, true, false);
                    else msg(p, "&7Tu n'es pas dans la partie.");
                }
            }
            case "spectate", "regarder" -> {
                if (s instanceof Player p) {
                    if (players.containsKey(p.getUniqueId()) && !gone.contains(p.getUniqueId())) msg(p, "&7Tu es déjà inscrit.");
                    else if (world != null && canEnter(p)) spectate(p);
                }
            }
            case "programme" -> {
                msg(s, "&7Programme selon le nombre de joueurs au départ :");
                for (int n = 2; n <= Math.max(16, maxPlayers()); n++) {
                    List<String> l = computeProgramme(n);
                    StringBuilder b = new StringBuilder();
                    for (String id : l) b.append(nomEpreuve(id)).append(", ");
                    msg(s, "&f" + n + " &8» &e" + b + "&cFinale");
                }
            }
            case "start" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else if (state == State.PLAYING || state == State.ENDING) msg(s, "&cUne partie est déjà en cours.");
                else if (building()) msg(s, "&cLes épreuves sont en construction, patiente.");
                else if (players.size() < 2) msg(s, "&cIl faut au moins 2 joueurs inscrits (finale en 1 contre 1).");
                else start(null);
            }
            case "test" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else if (a.length < 2) msg(s, "&7/soleil test <soleil|dalgona|corde|billes|pont|finale> &8- joue seulement cette épreuve puis la finale, sans récompense");
                else if (state == State.PLAYING || state == State.ENDING) msg(s, "&cUne partie est déjà en cours.");
                else if (building()) msg(s, "&cLes épreuves sont en construction, patiente.");
                else if (players.size() < 2) msg(s, "&cIl faut au moins 2 joueurs inscrits.");
                else {
                    String id = a[1].toLowerCase();
                    if (!List.of("soleil", "dalgona", "corde", "billes", "pont", "finale").contains(id)) {
                        msg(s, "&cÉpreuve inconnue.");
                    } else {
                        start(id.equals("finale") ? new ArrayList<>() : new ArrayList<>(List.of(id)));
                        if (!id.equals("finale") && players.size() < runtimeMin(id)) msg(s, "&7(partie test : l'épreuve se joue quand même à " + players.size() + " ; personne n'est éliminé à 2 joueurs)");
                    }
                }
            }
            case "passer", "skip" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else if (current == null || state != State.PLAYING) msg(s, "&cAucune épreuve en cours.");
                else if (intermission) {
                    interLeft = 1;
                    msg(s, "&7Entracte écourté.");
                } else {
                    current.timeUp();
                    msg(s, "&7Fin du temps forcée pour " + current.nom() + ".");
                }
            }
            case "stop" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else {
                    if (state == State.PLAYING || state == State.ENDING) tell("&cJeu arrêté par un admin (aucune récompense).");
                    finish();
                    msg(s, "&7Jeu arrêté, attente remise à zéro.");
                }
            }
            case "build" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else if (state == State.PLAYING || state == State.ENDING) msg(s, "&cImpossible pendant une partie.");
                else if (world != null) {
                    String id = a.length > 1 ? a[1].toLowerCase() : "tout";
                    if (id.equals("tout")) {
                        for (String z : ZONE_VERSION.keySet()) if (!buildQueue.contains(z) && !z.equals(buildId)) buildQueue.add(z);
                    } else if (ZONE_VERSION.containsKey(id)) {
                        if (!buildQueue.contains(id) && !id.equals(buildId)) buildQueue.add(id);
                    } else {
                        msg(s, "&cZone inconnue : terrain, dalgona, corde, billes, pont, finale ou tout.");
                        return true;
                    }
                    msg(s, "&aReconstruction lancée : " + buildQueue + " (étalée sur plusieurs ticks).");
                }
            }
            case "reload" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else {
                    reloadConfig();
                    if (world != null && state != State.PLAYING && !building()) spawnDoll();
                    msg(s, "&aConfig rechargée.");
                }
            }
            case "info" -> {
                if (!admin) msg(s, "&cPermission refusée.");
                else {
                    msg(s, "&7Etat &f" + state + "&7, inscrits &f" + players.size() + "&7, en jeu &f" + aliveCount()
                            + "&7, épreuve &f" + (current == null ? "-" : current.id + " (" + current.elimCount + "/" + current.minElim + ".." + current.maxElim + ")")
                            + "&7, programme &f" + programme + "&7, construction &f" + (building() ? buildId + " " + buildQueue : "non")
                            + "&7, poupée " + (doll != null && doll.isValid() ? "&aOK" : "&cabsente"));
                }
            }
            default -> {
                msg(s, "&f/soleil join &7- rejoindre | &f/soleil leave &7- quitter | &f/soleil spectate &7- regarder | &f/soleil programme");
                if (admin) msg(s, "&c/soleil start | test <épreuve> | passer | stop | build [zone] | reload | info");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>(List.of("join", "leave", "spectate", "programme"));
        boolean admin = s.hasPermission("eemisoleil.admin");
        if (admin) out.addAll(List.of("start", "test", "passer", "stop", "build", "reload", "info"));
        if (a.length == 1) {
            out.removeIf(x -> !x.startsWith(a[0].toLowerCase()));
            return out;
        }
        if (a.length == 2 && admin && a[0].equalsIgnoreCase("test")) {
            List<String> l = new ArrayList<>(List.of("soleil", "dalgona", "corde", "billes", "pont", "finale"));
            l.removeIf(x -> !x.startsWith(a[1].toLowerCase()));
            return l;
        }
        if (a.length == 2 && admin && a[0].equalsIgnoreCase("build")) {
            List<String> l = new ArrayList<>(ZONE_VERSION.keySet());
            l.add("tout");
            l.removeIf(x -> !x.startsWith(a[1].toLowerCase()));
            return l;
        }
        return List.of();
    }
}
