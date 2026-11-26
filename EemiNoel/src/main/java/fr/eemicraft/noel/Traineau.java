package fr.eemicraft.noel;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Passage du traîneau du Père Noël au-dessus du lobby (ambiance activée, au moins un joueur au lobby) :
 * traîneau, Père Noël (mannequin avec skin ou version en blocs), sac de cadeaux et 4 rennes en entités d'affichage,
 * clochettes, « Ho ho ho », traînée de particules. Il lâche des cadeaux qui tombent, se posent au sol en brillant et
 * donnent 1 ou 2 pièces EemiCore au premier joueur qui passe dessus (détection de proximité : DeluxeHub empêche le
 * ramassage d'objets au lobby, et les cadeaux ne sont de toute façon pas des objets ramassables).
 * Toutes les entités sont non persistantes, taguées, retirées en fin de passage, au /noel off, à l'arrêt et au démarrage.
 */
final class Traineau implements Listener {

    static final String TAG = "eeminoel_traineau";
    private static final Material[] GIFT_COLORS = {Material.RED_CONCRETE, Material.GREEN_CONCRETE, Material.BLUE_CONCRETE,
            Material.RED_WOOL, Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE};
    private static final Material[] RIBBON_COLORS = {Material.YELLOW_CONCRETE, Material.WHITE_CONCRETE, Material.GOLD_BLOCK};

    /** Une pièce du modèle : bloc de taille s centré en c (repère local : x côté, y haut, z avant). */
    private record Part(Material m, float cx, float cy, float cz, float sx, float sy, float sz, boolean lit, Color glow) {}

    private final class Pass {
        final int id;
        final World world;
        final double sx, sz, dx, dz, baseY;
        final int total;
        final float yaw;
        final Quaternionf q;
        final List<BlockDisplay> parts = new ArrayList<>();
        final Set<Long> tickets = new HashSet<>();
        final List<Integer> drops = new ArrayList<>();
        Mannequin santa;
        int t;

        Pass(int id, World world, double sx, double sz, double dx, double dz, double baseY, int total) {
            this.id = id;
            this.world = world;
            this.sx = sx;
            this.sz = sz;
            this.dx = dx;
            this.dz = dz;
            this.baseY = baseY;
            this.total = total;
            this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            this.q = new Quaternionf().rotationY((float) Math.atan2(dx, dz));
        }

        Location root(int tick) {
            double d = tick * speed();
            double y = baseY + 1.2 * Math.sin(tick * Math.PI * 2 / 60.0);
            return new Location(world, sx + dx * d, y, sz + dz * d, 0f, 0f);
        }
    }

    private final class Gift {
        final int pass;
        final List<BlockDisplay> parts = new ArrayList<>();
        final double x0, z0, y0, x1, z1, groundY;
        double y, vy;
        int age, landedAt = -1;

        Gift(int pass, double x0, double y0, double z0, double x1, double z1, double groundY) {
            this.pass = pass;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.groundY = groundY;
            this.y = y0;
        }

        boolean landed() {
            return landedAt >= 0;
        }

        Location loc(World w) {
            double f = landed() ? 1 : 1 - Math.pow(0.85, age);
            return new Location(w, x0 + (x1 - x0) * f, y, z0 + (z1 - z0) * f, 0f, 0f);
        }
    }

    private final EemiNoel plugin;
    private final List<Gift> gifts = new ArrayList<>();
    private final Map<Integer, Map<UUID, Integer>> taken = new HashMap<>();
    /** Pièces versées par passage et par joueur (pour le message de fin). */
    private final Map<Integer, Map<UUID, Integer>> coinsTaken = new HashMap<>();
    /** Passages finis dont le bilan sera envoyé quand leurs derniers cadeaux auront disparu. */
    private final Set<Integer> summaryPending = new HashSet<>();
    /** Annonce du prochain passage déjà envoyée ; forced = lancé par /noel traineau (même ambiance coupée). */
    private boolean announced, forced;
    private Pass pass;
    private int passCounter, clock;
    private long nextPass;
    private BukkitTask task;
    private ResolvableProfile skin;
    private String skinName = "";

    Traineau(EemiNoel plugin) {
        this.plugin = plugin;
        scheduleNext();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, this::removeOrphans, 40L);
    }

    // ------------------------------------------------------------------ réglages

    private double cfgD(String k, double def) {
        return plugin.getConfig().getDouble("traineau." + k, def);
    }

    private int cfgI(String k, int def) {
        return plugin.getConfig().getInt("traineau." + k, def);
    }

    private double speed() {
        return Math.max(0.1, Math.min(2.0, cfgD("vitesse", 0.5)));
    }

    void scheduleNext() {
        announced = false;
        forced = false;
        int base = Math.max(30, cfgI("intervalle", 600));
        double f = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        nextPass = System.currentTimeMillis() + (long) (base * f * 1000);
    }

    String status() {
        if (!plugin.getConfig().getBoolean("traineau.enabled", true)) return "§7désactivé";
        if (pass != null) return "§aen vol (" + (pass.total - pass.t) / 20 + " s)";
        if (announced) return "§eannoncé, décolle dans " + Math.max(0, (nextPass - System.currentTimeMillis()) / 1000) + " s";
        if (!plugin.ambianceOn()) return "§7en pause (ambiance coupée)";
        long s = Math.max(0, (nextPass - System.currentTimeMillis()) / 1000);
        return "§fprochain passage dans " + s / 60 + " min " + s % 60 + " s" + (s == 0 ? " §7(dès qu'un joueur est au lobby)" : "");
    }

    boolean flying() {
        return pass != null;
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        clock++;
        try {
            if (pass != null) stepPass();
            else if (clock % 20 == 0 && (forced || plugin.ambianceOn() && plugin.getConfig().getBoolean("traineau.enabled", true))
                    && lobbyPlayers().size() > 0) {
                long now = System.currentTimeMillis();
                if (!announced && now >= nextPass - announceSeconds() * 1000L) {
                    nextPass = Math.max(nextPass, now + announceSeconds() * 1000L);
                    announce();
                } else if (announced && now >= nextPass) {
                    String err = start(null);
                    if (err != null) {
                        plugin.getLogger().warning("Traîneau : passage annoncé impossible : " + err);
                        scheduleNext();
                    }
                }
            }
            stepGifts();
            if (clock % 20 == 0) sendSummaries();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Traîneau : erreur, passage arrêté : " + e);
            stopAll();
        }
    }

    private World world() {
        return Bukkit.getWorld(plugin.getConfig().getString("world", "lobby"));
    }

    private List<Player> lobbyPlayers() {
        World w = world();
        return w == null ? List.of() : w.getPlayers();
    }

    // ------------------------------------------------------------------ départ

    /** Secondes entre l'annonce dans le chat et le passage (traineau.annonce-secondes, 10 par défaut). */
    int announceSeconds() {
        return Math.max(0, Math.min(120, cfgI("annonce-secondes", 10)));
    }

    /** Annonce dans le chat : le Père Noël arrive. 1.4.2 (#150) : à TOUS les joueurs (partout sur le serveur),
     *  avec un rappel /lobby pour ceux qui n'y sont pas ; les sons seulement au lobby (pas en pleine partie). */
    private void announce() {
        announced = true;
        String text = plugin.prefix() + plugin.msg("traineau-annonce",
                "§fLe Père Noël arrive au-dessus du lobby ! §7Ramasse ses cadeaux §8(dans {secondes} s)",
                "secondes", announceSeconds());
        List<Player> lobby = lobbyPlayers();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!lobby.contains(pl)) pl.sendMessage(text + " §7(§f/lobby §7pour y aller)");
        }
        for (Player pl : lobby) {
            pl.sendMessage(text);
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.9f, 1.4f);
            pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.7f, 1.8f);
        }
        plugin.getLogger().info("Traîneau annoncé (départ dans " + announceSeconds() + " s).");
    }

    /** /noel traineau : annonce tout de suite, puis passage annonce-secondes plus tard. */
    String announceLaunch(org.bukkit.command.CommandSender by) {
        if (pass != null) return "Le traîneau est déjà en vol.";
        if (announced) return "Un passage est déjà annoncé.";
        if (world() == null) return "Monde du lobby introuvable.";
        if (lobbyPlayers().isEmpty()) return "Personne au lobby : /noel traineau maintenant pour le lancer quand même.";
        forced = true;
        nextPass = System.currentTimeMillis() + announceSeconds() * 1000L;
        announce();
        return null;
    }

    /** Lance un passage. Renvoie un message d'erreur, ou null si c'est parti. */
    String start(org.bukkit.command.CommandSender by) {
        if (pass != null) return "Le traîneau est déjà en vol.";
        World w = world();
        if (w == null) return "Monde du lobby introuvable.";
        int minX = plugin.getConfig().getInt("area.min-x", 630), maxX = plugin.getConfig().getInt("area.max-x", 760);
        int minZ = plugin.getConfig().getInt("area.min-z", 600), maxZ = plugin.getConfig().getInt("area.max-z", 700);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble() * Math.PI * 2;
        double dx = Math.cos(ang), dz = Math.sin(ang);
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0, hx = (maxX - minX) / 2.0, hz = (maxZ - minZ) / 2.0;
        // décalage de côté pour ne pas toujours passer au centre
        double side = (hx * Math.abs(dz) + hz * Math.abs(dx)) * 0.25 * (r.nextDouble() * 2 - 1);
        cx += -dz * side;
        cz += dx * side;
        double half = hx * Math.abs(dx) + hz * Math.abs(dz) + 15;
        double sx = cx - dx * half, sz = cz - dz * half;
        double baseY = cfgD("sol-y", 51) + cfgD("hauteur", 25);
        int total = (int) Math.ceil(2 * half / speed());
        Pass p = new Pass(++passCounter, w, sx, sz, dx, dz, baseY, total);
        // chunks du trajet chargés le temps du passage
        for (double d = 0; d <= 2 * half + 8; d += 4) {
            int chx = (int) Math.floor(sx + dx * d) >> 4, chz = (int) Math.floor(sz + dz * d) >> 4;
            long k = ((long) chx << 32) ^ (chz & 0xFFFFFFFFL);
            if (p.tickets.add(k)) w.addPluginChunkTicket(chx, chz, plugin);
        }
        // largages répartis sur la partie du trajet au-dessus de la zone
        int t0 = -1, t1 = -1;
        for (int t = 0; t <= total; t++) {
            Location l = p.root(t);
            if (l.getX() >= minX && l.getX() <= maxX && l.getZ() >= minZ && l.getZ() <= maxZ) {
                if (t0 < 0) t0 = t;
                t1 = t;
            }
        }
        int n = Math.max(0, cfgI("cadeaux", 12));
        if (t0 >= 0 && n > 0) for (int i = 0; i < n; i++) p.drops.add(t0 + (int) ((i + 0.5) * (t1 - t0) / n));
        pass = p;
        try {
            build(p);
        } catch (RuntimeException e) {
            end();
            throw e;
        }
        taken.put(p.id, new HashMap<>());
        scheduleNext();
        LegacyComponentSerializer ser = LegacyComponentSerializer.legacySection();
        Title title = Title.title(ser.deserialize(plugin.msg("traineau-titre", "§c§lHo ho ho !")),
                ser.deserialize(plugin.msg("traineau-sous-titre", "§fLe Père Noël passe au-dessus du lobby !")),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700)));
        for (Player pl : lobbyPlayers()) {
            pl.showTitle(title);
            pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.MASTER, 0.8f, 1.2f);
            pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.6f, 1.5f);
        }
        hoHoHo(p, 10);
        plugin.getLogger().info("Passage du traîneau (" + n + " cadeaux, " + total / 20 + " s"
                + (by != null ? ", lancé par " + by.getName() : "") + ").");
        return null;
    }

    // ------------------------------------------------------------------ modèle

    private List<Part> model(boolean blockSanta) {
        List<Part> l = new ArrayList<>();
        Material red = Material.RED_CONCRETE, gold = Material.GOLD_BLOCK, wood = Material.DARK_OAK_PLANKS;
        // patins dorés et montants
        for (float x : new float[]{-0.6f, 0.6f}) {
            l.add(new Part(gold, x, 0.06f, 0f, 0.12f, 0.12f, 3.0f, false, null));
            l.add(new Part(gold, x, 0.35f, 1.5f, 0.12f, 0.6f, 0.12f, false, null));
            l.add(new Part(gold, x, 0.62f, 1.38f, 0.12f, 0.12f, 0.3f, false, null));
            l.add(new Part(wood, x, 0.3f, 0.9f, 0.1f, 0.36f, 0.1f, false, null));
            l.add(new Part(wood, x, 0.3f, -0.9f, 0.1f, 0.36f, 0.1f, false, null));
            // côtés rouges et liseré doré
            l.add(new Part(red, x * 1.17f, 0.85f, -0.1f, 0.12f, 0.6f, 2.4f, false, null));
            l.add(new Part(gold, x * 1.17f, 1.18f, -0.1f, 0.16f, 0.08f, 2.44f, false, null));
        }
        l.add(new Part(red, 0f, 0.5f, -0.1f, 1.4f, 0.15f, 2.4f, false, null));      // plancher
        l.add(new Part(red, 0f, 1.05f, -1.25f, 1.4f, 1.0f, 0.15f, false, null));    // dossier
        l.add(new Part(gold, 0f, 1.58f, -1.25f, 1.44f, 0.08f, 0.19f, false, null));
        l.add(new Part(red, 0f, 0.8f, 1.05f, 1.4f, 0.5f, 0.15f, false, null));      // avant
        l.add(new Part(gold, 0f, 1.08f, 1.05f, 1.44f, 0.08f, 0.19f, false, null));
        // sac de cadeaux derrière le Père Noël, deux paquets qui dépassent
        l.add(new Part(Material.BROWN_WOOL, 0f, 0.95f, -0.8f, 0.85f, 0.8f, 0.7f, false, null));
        l.add(new Part(Material.RED_CONCRETE, 0.17f, 1.45f, -0.8f, 0.3f, 0.3f, 0.3f, false, null));
        l.add(new Part(Material.GREEN_CONCRETE, -0.2f, 1.42f, -0.75f, 0.28f, 0.28f, 0.28f, false, null));
        l.add(new Part(Material.YELLOW_CONCRETE, 0.17f, 1.61f, -0.8f, 0.32f, 0.04f, 0.08f, false, null));
        // rênes
        for (float x : new float[]{-0.55f, 0.55f})
            l.add(new Part(Material.BROWN_TERRACOTTA, x, 1.0f, 3.0f, 0.04f, 0.04f, 3.9f, false, null));
        if (blockSanta) {
            float z = -0.15f;
            Material skinTone = Material.PINK_TERRACOTTA, white = Material.WHITE_WOOL, coat = Material.RED_WOOL;
            l.add(new Part(coat, 0f, 1.0f, z, 0.6f, 0.8f, 0.4f, false, null));                 // manteau
            l.add(new Part(white, 0f, 1.0f, z + 0.2f, 0.12f, 0.8f, 0.02f, false, null));       // bande blanche
            l.add(new Part(Material.BLACK_WOOL, 0f, 0.85f, z, 0.62f, 0.12f, 0.42f, false, null)); // ceinture
            l.add(new Part(gold, 0f, 0.85f, z + 0.22f, 0.16f, 0.1f, 0.03f, false, null));
            for (float x : new float[]{-0.39f, 0.39f}) {
                l.add(new Part(coat, x, 1.12f, z + 0.12f, 0.18f, 0.55f, 0.18f, false, null)); // bras
                l.add(new Part(white, x, 0.86f, z + 0.12f, 0.2f, 0.06f, 0.2f, false, null));  // poignet
                l.add(new Part(Material.BLACK_WOOL, x, 0.8f, z + 0.14f, 0.14f, 0.08f, 0.14f, false, null)); // gant
            }
            l.add(new Part(skinTone, 0f, 1.65f, z, 0.45f, 0.45f, 0.45f, false, null));      // tête
            l.add(new Part(Material.BLACK_CONCRETE, -0.1f, 1.72f, z + 0.23f, 0.07f, 0.07f, 0.02f, false, null));
            l.add(new Part(Material.BLACK_CONCRETE, 0.1f, 1.72f, z + 0.23f, 0.07f, 0.07f, 0.02f, false, null));
            l.add(new Part(white, 0f, 1.5f, z + 0.25f, 0.48f, 0.3f, 0.06f, false, null));    // barbe
            l.add(new Part(white, 0f, 1.4f, z + 0.1f, 0.5f, 0.16f, 0.32f, false, null));
            l.add(new Part(white, 0f, 1.89f, z, 0.5f, 0.08f, 0.5f, false, null));            // bord du bonnet
            l.add(new Part(coat, 0f, 1.99f, z, 0.44f, 0.14f, 0.44f, false, null));            // bonnet
            l.add(new Part(coat, 0f, 2.13f, z - 0.06f, 0.26f, 0.16f, 0.26f, false, null));
            l.add(new Part(white, 0f, 2.26f, z - 0.12f, 0.14f, 0.14f, 0.14f, false, null));   // pompon
        }
        // 4 rennes attelés devant, en 2 x 2 ; celui de tête à gauche a le nez rouge lumineux
        float[][] deer = {{-0.55f, 4.7f}, {0.55f, 4.7f}, {-0.55f, 2.85f}, {0.55f, 2.85f}};
        for (int i = 0; i < deer.length; i++) {
            float x = deer[i][0], z = deer[i][1];
            Material fur = Material.BROWN_TERRACOTTA, dark = Material.DARK_OAK_PLANKS, antler = Material.STRIPPED_BIRCH_LOG;
            l.add(new Part(fur, x, 0.75f, z, 0.45f, 0.45f, 0.95f, false, null));               // corps
            l.add(new Part(Material.WHITE_WOOL, x, 0.92f, z - 0.5f, 0.14f, 0.14f, 0.08f, false, null)); // queue
            for (float lx : new float[]{-0.15f, 0.15f})
                for (float lz : new float[]{-0.33f, 0.33f})
                    l.add(new Part(dark, x + lx, 0.29f, z + lz, 0.1f, 0.54f, 0.1f, false, null)); // pattes
            l.add(new Part(fur, x, 1.05f, z + 0.4f, 0.22f, 0.42f, 0.22f, false, null));       // cou
            l.add(new Part(Material.RED_WOOL, x, 0.95f, z + 0.4f, 0.3f, 0.08f, 0.3f, false, null)); // collier
            l.add(new Part(gold, x, 0.86f, z + 0.56f, 0.09f, 0.09f, 0.09f, false, null));     // grelot
            l.add(new Part(fur, x, 1.32f, z + 0.6f, 0.3f, 0.3f, 0.42f, false, null));         // tête
            boolean rudolph = i == 0;
            l.add(new Part(rudolph ? Material.RED_CONCRETE : Material.BLACK_WOOL, x, 1.27f, z + 0.84f,
                    rudolph ? 0.15f : 0.12f, rudolph ? 0.15f : 0.12f, 0.08f, rudolph, rudolph ? Color.RED : null)); // nez
            for (float ax : new float[]{-0.1f, 0.1f}) {
                l.add(new Part(antler, x + ax, 1.62f, z + 0.5f, 0.06f, 0.34f, 0.06f, false, null)); // bois
                l.add(new Part(antler, x + ax * 2, 1.74f, z + 0.5f, 0.2f, 0.06f, 0.06f, false, null));
                l.add(new Part(antler, x + ax, 1.7f, z + 0.58f, 0.06f, 0.06f, 0.16f, false, null));
            }
        }
        return l;
    }

    private void build(Pass p) {
        String wanted = plugin.getConfig().getString("traineau.skin", "").trim();
        boolean useSkin = !wanted.isEmpty();
        Location root = p.root(0);
        for (Part part : model(!useSkin)) p.parts.add(spawnPart(p.world, root, p.q, part));
        if (useSkin) {
            Location sl = santaLoc(p, root);
            p.santa = p.world.spawn(sl, Mannequin.class, m -> {
                m.setPersistent(false);
                m.addScoreboardTag(TAG);
                m.addScoreboardTag("eeminoel");
                m.setInvulnerable(true);
                m.setImmovable(true);
                m.setGravity(false);
                m.setSilent(true);
                m.setCollidable(false);
                m.setRemoveWhenFarAway(false);
                m.setDescription(null);
            });
            if (!p.santa.isValid()) {
                plugin.getLogger().warning("Traîneau : le mannequin du Père Noël n'a pas pu apparaître.");
                p.santa = null;
            } else applySkin(p.santa, wanted);
        }
    }

    private Location santaLoc(Pass p, Location root) {
        Vector3f o = p.q.transform(new Vector3f(0f, 0.575f, -0.15f));
        Location l = root.clone().add(o.x, o.y, o.z);
        l.setYaw(p.yaw);
        return l;
    }

    private void applySkin(Mannequin m, String name) {
        if (skin != null && name.equalsIgnoreCase(skinName)) {
            m.setProfile(skin);
            return;
        }
        ResolvableProfile.resolvableProfile().name(name).build().resolve().whenComplete((profile, error) -> {
            if (error != null || profile == null) {
                plugin.getLogger().warning("Traîneau : skin introuvable pour " + name + (error != null ? " : " + error.getMessage() : ""));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                skin = ResolvableProfile.resolvableProfile(profile);
                skinName = name;
                if (m.isValid()) m.setProfile(skin);
            });
        });
    }

    private BlockDisplay spawnPart(World w, Location root, Quaternionf q, Part part) {
        Vector3f size = new Vector3f(part.sx(), part.sy(), part.sz());
        Vector3f corner = new Vector3f(part.cx(), part.cy(), part.cz()).sub(new Vector3f(size).mul(0.5f));
        Transformation tr = new Transformation(q.transform(corner), new Quaternionf(q), size, new Quaternionf());
        return w.spawn(root, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            d.setBlock(part.m().createBlockData());
            d.setTransformation(tr);
            d.setTeleportDuration(2);
            d.setViewRange(2.0f);
            if (part.lit()) d.setBrightness(new Display.Brightness(15, 15));
            if (part.glow() != null) {
                d.setGlowing(true);
                d.setGlowColorOverride(part.glow());
            }
        });
    }

    // ------------------------------------------------------------------ vol

    private void stepPass() {
        Pass p = pass;
        p.t++;
        if (p.t > p.total) {
            summaryPending.add(p.id);
            end();
            return;
        }
        Location root = p.root(p.t);
        for (BlockDisplay d : p.parts) if (d.isValid()) d.teleport(root);
        if (p.santa != null && p.santa.isValid()) p.santa.teleport(santaLoc(p, root));
        World w = p.world;
        // traînée derrière le traîneau : neige et étincelles dorées
        Vector3f back = p.q.transform(new Vector3f(0f, 0.8f, -1.8f));
        Location tail = root.clone().add(back.x, back.y, back.z);
        w.spawnParticle(Particle.SNOWFLAKE, tail, 4, 0.3, 0.2, 0.3, 0.01, null, true);
        w.spawnParticle(Particle.DUST, tail, 3, 0.25, 0.2, 0.25, 0, new Particle.DustOptions(Color.fromRGB(255, 205, 40), 1.3f), true);
        if (p.t % 2 == 0) w.spawnParticle(Particle.FIREWORK, tail, 1, 0.2, 0.1, 0.2, 0.02, null, true);
        if (p.t % 3 == 0) {
            Vector3f nose = p.q.transform(new Vector3f(-0.55f, 1.27f, 5.6f));
            w.spawnParticle(Particle.DUST, root.clone().add(nose.x, nose.y, nose.z), 1, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.RED, 0.8f), true);
        }
        // clochettes en continu (rythme de grelots)
        if (p.t % 4 == 0) {
            float pitch = (p.t / 4) % 4 == 3 ? 1.33f : 1.78f;
            Sound s = (p.t / 4) % 2 == 0 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_CHIME;
            for (Player pl : w.getPlayers()) pl.playSound(root, s, SoundCategory.NEUTRAL, 2.5f, pitch);
        }
        if (p.t == p.total / 2) hoHoHo(p, 0);
        while (!p.drops.isEmpty() && p.drops.get(0) <= p.t) {
            p.drops.remove(0);
            drop(p, root);
        }
    }

    /** « Ho ho ho » : trois rires graves du Père Noël, joués à la position du traîneau. */
    private void hoHoHo(Pass p, int delay) {
        for (int i = 0; i < 3; i++) {
            float pitch = 0.6f - i * 0.05f;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pass != p) return;
                Location l = p.root(p.t);
                for (Player pl : p.world.getPlayers()) {
                    pl.playSound(l, Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 3f, pitch);
                    pl.playSound(l, Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 2f, pitch - 0.1f);
                }
            }, delay + i * 7L);
        }
    }

    private void end() {
        Pass p = pass;
        pass = null;
        if (p == null) return;
        for (BlockDisplay d : p.parts) d.remove();
        if (p.santa != null) p.santa.remove();
        for (long k : p.tickets) p.world.removePluginChunkTicket((int) (k >> 32), (int) k, plugin);
    }

    // ------------------------------------------------------------------ cadeaux

    private void drop(Pass p, Location root) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3f sack = p.q.transform(new Vector3f(0f, 0.8f, -0.9f));
        double x0 = root.getX() + sack.x, y0 = root.getY() + sack.y, z0 = root.getZ() + sack.z;
        double solY = cfgD("sol-y", 51);
        int minX = plugin.getConfig().getInt("area.min-x", 630), maxX = plugin.getConfig().getInt("area.max-x", 760);
        int minZ = plugin.getConfig().getInt("area.min-z", 600), maxZ = plugin.getConfig().getInt("area.max-z", 700);
        for (int attempt = 0; attempt < 8; attempt++) {
            double spread = attempt == 0 ? 1.0 : 4.0;
            double x1 = x0 + p.dx * 1.7 + (r.nextDouble() * 2 - 1) * spread;
            double z1 = z0 + p.dz * 1.7 + (r.nextDouble() * 2 - 1) * spread;
            if (x1 < minX || x1 > maxX || z1 < minZ || z1 > maxZ) continue;
            int bx = (int) Math.floor(x1), bz = (int) Math.floor(z1);
            if (!p.world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            Block top = p.world.getHighestBlockAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (top.isLiquid() || top.getType() == Material.LAVA) continue;
            Block above = top.getRelative(0, 1, 0);
            double gy = top.getType() == Material.SNOW ? top.getY() + ((Snow) top.getBlockData()).getLayers() * 0.125
                    : above.getType() == Material.SNOW ? above.getY() + ((Snow) above.getBlockData()).getLayers() * 0.125
                    : top.getY() + 1;
            if (gy < solY - 12 || gy > solY + 6 || gy >= y0) continue; // pas sur un toit, ni dans un trou
            Gift g = new Gift(p.id, x0, y0, z0, x1, z1, gy);
            spawnGift(p.world, g);
            gifts.add(g);
            return;
        }
    }

    private void spawnGift(World w, Gift g) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Material box = GIFT_COLORS[r.nextInt(GIFT_COLORS.length)], rib = RIBBON_COLORS[r.nextInt(RIBBON_COLORS.length)];
        Location at = g.loc(w);
        Quaternionf q = new Quaternionf().rotationY((float) (r.nextDouble() * Math.PI));
        Part[] parts = {
                new Part(box, 0f, 0.25f, 0f, 0.5f, 0.5f, 0.5f, false, null),
                new Part(rib, 0f, 0.25f, 0f, 0.53f, 0.53f, 0.1f, false, null),
                new Part(rib, 0f, 0.25f, 0f, 0.1f, 0.53f, 0.53f, false, null),
                new Part(rib, -0.07f, 0.56f, 0f, 0.12f, 0.1f, 0.12f, false, null),
                new Part(rib, 0.07f, 0.56f, 0f, 0.12f, 0.1f, 0.12f, false, null)};
        for (Part part : parts) {
            BlockDisplay d = spawnPart(w, at, q, part);
            d.setTeleportDuration(1);
            d.setViewRange(1.0f);
            g.parts.add(d);
        }
    }

    private void stepGifts() {
        if (gifts.isEmpty()) return;
        int life = Math.max(5, cfgI("duree-cadeau", 60)) * 20;
        double reach = cfgD("rayon-ramassage", 1.3);
        Iterator<Gift> it = gifts.iterator();
        while (it.hasNext()) {
            Gift g = it.next();
            if (g.parts.isEmpty() || !g.parts.get(0).isValid()) {
                removeGift(g);
                it.remove();
                continue;
            }
            World w = g.parts.get(0).getWorld();
            g.age++;
            if (!g.landed()) {
                g.vy = Math.min(0.7, g.vy + 0.04);
                g.y -= g.vy;
                if (g.y <= g.groundY) {
                    g.y = g.groundY;
                    g.landedAt = g.age;
                    Location l = g.loc(w);
                    for (BlockDisplay d : g.parts) {
                        d.teleport(l);
                        if (d == g.parts.get(0)) {
                            d.setGlowing(true);
                            d.setGlowColorOverride(Color.fromRGB(255, 215, 0));
                        }
                    }
                    w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 0.3, 0), 15, 0.3, 0.1, 0.3, 0.05);
                    w.playSound(l, Sound.BLOCK_WOOL_BREAK, SoundCategory.NEUTRAL, 0.8f, 1.2f);
                    continue;
                }
                Location l = g.loc(w);
                for (BlockDisplay d : g.parts) d.teleport(l);
                w.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.6, 0), 1, 0.05, 0.05, 0.05, 0.0, null, true);
                if (g.age % 2 == 0) w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 0.6, 0), 1, 0.1, 0.1, 0.1, 0.0, null, true);
                continue;
            }
            Location l = g.loc(w);
            if (g.age - g.landedAt >= life) {
                w.spawnParticle(Particle.POOF, l.clone().add(0, 0.3, 0), 8, 0.2, 0.2, 0.2, 0.02);
                removeGift(g);
                it.remove();
                continue;
            }
            if (g.age % 10 == 0) w.spawnParticle(Particle.WAX_ON, l.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0.5);
            if (g.age % 2 != 0) continue;
            Player taker = null;
            for (Player pl : w.getPlayers()) {
                if (!canTake(pl)) continue;
                Location pl0 = pl.getLocation();
                double ddx = pl0.getX() - l.getX(), ddz = pl0.getZ() - l.getZ(), ddy = pl0.getY() - g.groundY;
                if (ddx * ddx + ddz * ddz > reach * reach || ddy < -1.5 || ddy > 2.5) continue;
                Map<UUID, Integer> counts = taken.computeIfAbsent(g.pass, k -> new HashMap<>());
                if (counts.getOrDefault(pl.getUniqueId(), 0) >= Math.max(1, cfgI("max-par-joueur", 5))) {
                    if (g.age % 40 == 0)
                        pl.sendActionBar(Component.text("Tu as déjà ramassé assez de cadeaux pour ce passage, laisse-en aux autres !",
                                NamedTextColor.GRAY));
                    continue;
                }
                taker = pl;
                break;
            }
            if (taker == null) continue;
            removeGift(g);
            it.remove();
            reward(taker, g, l);
        }
    }

    private static boolean canTake(Player p) {
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        for (MetadataValue v : p.getMetadata("vanished")) if (v.asBoolean()) return false;
        return true;
    }

    private void reward(Player p, Gift g, Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = Math.max(1, cfgI("pieces-min", 1)), max = Math.max(min, cfgI("pieces-max", 2));
        int n = r.nextInt(min, max + 1);
        taken.computeIfAbsent(g.pass, k -> new HashMap<>()).merge(p.getUniqueId(), 1, Integer::sum);
        World w = at.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.25);
        w.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.6, 0), 12, 0.3, 0.3, 0.3, 0.05);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.9f, 1.2f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.8f, 1.6f);
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        boolean ok = false;
        if (core != null && core.isEnabled() && Bukkit.getPluginCommand("eemi") != null) {
            try {
                ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi give " + p.getName() + " coins " + n);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Traîneau : eemi give a échoué pour " + p.getName() + " : " + e);
            }
        }
        if (ok) coinsTaken.computeIfAbsent(g.pass, k -> new HashMap<>()).merge(p.getUniqueId(), n, Integer::sum);
        if (ok) p.sendActionBar(Component.text("🎁 +" + n + (n > 1 ? " pièces" : " pièce") + " ! Cadeau du Père Noël", NamedTextColor.GOLD));
        else {
            p.sendActionBar(Component.text("🎁 Cadeau trouvé... mais la hotte est coincée (pièces non versées)", NamedTextColor.RED));
            plugin.getLogger().warning("Traîneau : " + n + " pièce(s) non versée(s) à " + p.getName() + " (EemiCore indisponible).");
        }
    }

    /**
     * Bilan d'un passage fini, quand ses derniers cadeaux ont été ramassés ou ont disparu : chaque joueur du lobby
     * (et chaque joueur connecté qui a ramassé quelque chose) reçoit son nombre de cadeaux et de pièces.
     */
    private void sendSummaries() {
        if (summaryPending.isEmpty()) return;
        Iterator<Integer> it = summaryPending.iterator();
        while (it.hasNext()) {
            int id = it.next();
            boolean left = false;
            for (Gift g : gifts)
                if (g.pass == id) {
                    left = true;
                    break;
                }
            if (left) continue;
            it.remove();
            Map<UUID, Integer> counts = taken.getOrDefault(id, Map.of());
            Map<UUID, Integer> coins = coinsTaken.getOrDefault(id, Map.of());
            Set<Player> to = new LinkedHashSet<>(lobbyPlayers());
            for (UUID u : counts.keySet()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null) to.add(pl);
            }
            for (Player pl : to) {
                int n = counts.getOrDefault(pl.getUniqueId(), 0), c = coins.getOrDefault(pl.getUniqueId(), 0);
                String text = n > 0
                        ? plugin.msg("traineau-fin",
                        "§fLe Père Noël est reparti ! §7Tu as ramassé §e{cadeaux}§7 cadeau{s} (§6{pieces} pièce{sp}§7).",
                        "cadeaux", n, "s", n > 1 ? "x" : "", "pieces", c, "sp", c > 1 ? "s" : "")
                        : plugin.msg("traineau-fin-rien",
                        "§fLe Père Noël est reparti. §7Tu n'as ramassé aucun cadeau cette fois : guette son prochain passage !");
                pl.sendMessage(plugin.prefix() + text);
            }
            taken.remove(id);
            coinsTaken.remove(id);
        }
    }

    private void removeGift(Gift g) {
        for (BlockDisplay d : g.parts) d.remove();
        g.parts.clear();
    }

    // ------------------------------------------------------------------ arrêt / nettoyage

    /** Retire le traîneau et tous les cadeaux (/noel off, arrêt du plugin). */
    void stopAll() {
        end();
        for (Gift g : gifts) removeGift(g);
        gifts.clear();
        taken.clear();
        coinsTaken.clear();
        summaryPending.clear();
        announced = false;
        forced = false;
    }

    void shutdown() {
        if (task != null) task.cancel();
        task = null;
        stopAll();
        removeOrphans();
    }

    private void removeOrphans() {
        World w = world();
        if (w == null) return;
        Set<Entity> keep = new HashSet<>();
        if (pass != null) {
            keep.addAll(pass.parts);
            if (pass.santa != null) keep.add(pass.santa);
        }
        for (Gift g : gifts) keep.addAll(g.parts);
        for (Entity e : w.getEntities())
            if (e.getScoreboardTags().contains(TAG) && !keep.contains(e)) e.remove();
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent e) {
        // WorldGuard (mob-spawning deny, block-plugin-spawning) annule les apparitions au lobby : on rétablit les nôtres
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(false);
    }
}
