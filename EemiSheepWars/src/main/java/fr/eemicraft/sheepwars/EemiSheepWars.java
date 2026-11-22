package fr.eemicraft.sheepwars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Sheep Wars d'EEMICRAFT, d'après le Sheep Wars d'Hypixel (Wool Games). Rouge contre Bleu, chacun sur
 * son camp, séparés par une ligne qu'on ne peut pas franchir en construisant. Les moutons arrivent
 * régulièrement (plus vite quand l'équipe a moins de joueurs) : clic gauche = tir droit (sans gravité),
 * clic droit = tir en cloche (plus rapide, plus fort pour les explosifs). Une seule vie, partie de 10 minutes,
 * la dernière équipe en vie gagne. La « laine magique » apparaît au-dessus de la séparation toutes les 30 s :
 * la toucher à l'arc donne un bonus à l'équipe selon sa couleur. Les maps (Désert rose, Îles vertes,
 * Vaisseau) viennent d'un monde Sheep Wars 1.8 fourni par Thibault, embarquées dans le jar et reconstruites
 * après chaque partie.
 */
public final class EemiSheepWars extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§9Sheep Wars§8] §7";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Side {
        ROUGE("Rouge", NamedTextColor.RED, Color.RED, Material.RED_WOOL, Material.RED_TERRACOTTA),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), Material.BLUE_WOOL, Material.BLUE_TERRACOTTA);

        final String label;
        final NamedTextColor color;
        final Color armor;
        final Material wool;
        /** Terracotta de l'équipe (squelette des anciennes maps, plus utilisé comme bloc fixe). */
        final Material skeleton;

        Side(String label, NamedTextColor color, Color armor, Material wool, Material skeleton) {
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.wool = wool;
            this.skeleton = skeleton;
        }

        Side other() {
            return this == ROUGE ? BLEU : ROUGE;
        }

        /** Nom accordé avec « équipe » (féminin) : Rouge, Bleue. */
        String fem() {
            return this == BLEU ? "Bleue" : label;
        }

        String code() {
            return this == ROUGE ? "§c" : "§9";
        }

        String key() {
            return name().toLowerCase();
        }

        /** -1 pour Rouge (camp ouest), +1 pour Bleu. */
        int dir() {
            return this == ROUGE ? -1 : 1;
        }

        /** Direction (facing) vers le vide central, et vers l'arrière du camp. */
        String front() {
            return this == ROUGE ? "east" : "west";
        }

        String back() {
            return this == ROUGE ? "west" : "east";
        }
    }

    enum SheepType {
        EXPLOSIF("explosif", "Mouton explosif", DyeColor.RED, Material.RED_WOOL, 0xFF5555, 0, true, "Explose à l'impact"),
        EXPLOSIF_FORT("explosif-fort", "Mouton très explosif", DyeColor.GRAY, Material.GRAY_WOOL, 0xAAAAAA, 0, true, "Grosse explosion à l'impact"),
        CHERCHEUR("chercheur", "Mouton chercheur", DyeColor.LIME, Material.LIME_WOOL, 0x55FF55, 0, true, "Se dirige en vol vers l'ennemi le plus proche"),
        INCENDIAIRE("incendiaire", "Mouton incendiaire", DyeColor.ORANGE, Material.ORANGE_WOOL, 0xFFAA00, 30, false, "Explose et met le feu"),
        GLACE("glace", "Mouton de glace", DyeColor.LIGHT_BLUE, Material.LIGHT_BLUE_WOOL, 0x55FFFF, 10, false, "Glace le sol, ralentit les ennemis"),
        AVEUGLANT("aveuglant", "Mouton aveuglant", DyeColor.BLACK, Material.BLACK_WOOL, 0x555555, 10, false, "Aveugle les ennemis proches"),
        TROU_NOIR("trou-noir", "Mouton trou noir", DyeColor.PURPLE, Material.PURPLE_WOOL, 0xAA00AA, 100, false, "Aspire joueurs et blocs pendant 5 s"),
        SEISME("seisme", "Mouton séisme", DyeColor.BROWN, Material.BROWN_WOOL, 0xAA7744, 20, false, "Fait sauter le sol et les ennemis"),
        SOIN("soin", "Mouton soigneur", DyeColor.PINK, Material.PINK_WOOL, 0xFF77CC, 5, false, "Régénération et vitesse pour ton équipe"),
        FOUDRE("foudre", "Mouton foudroyant", DyeColor.YELLOW, Material.YELLOW_WOOL, 0xFFFF55, 20, false, "Appelle la foudre"),
        INTERGALACTIQUE("intergalactique", "Mouton intergalactique", DyeColor.BLUE, Material.BLUE_WOOL, 0x5555FF, 20, false, "Fait pleuvoir des météores"),
        POISON("poison", "Mouton empoisonné", DyeColor.GREEN, Material.GREEN_WOOL, 0x00AA00, 120, false, "Nuage de poison pendant 6 s"),
        ENDER("ender", "Mouton de l'Ender", DyeColor.CYAN, Material.CYAN_WOOL, 0x00AAAA, 20, false, "Téléporte les ennemis proches au hasard"),
        ABORDAGE("abordage", "Mouton d'abordage", DyeColor.WHITE, Material.WHITE_WOOL, 0xFFFFFF, 0, false, "Tu voyages dessus (accroupis-toi pour descendre)"),
        ECHANGEUR("echangeur", "Mouton échangeur", DyeColor.MAGENTA, Material.MAGENTA_WOOL, 0xFF55FF, 10, false, "Échange ta place avec l'ennemi le plus proche");

        final String key;
        final String label;
        final DyeColor dye;
        final Material item;
        final int rgb;
        /** Ticks entre la pose au sol et l'effet (ou durée de l'effet pour trou noir et poison). */
        final int fuse;
        /** Effet dès l'impact, sans attendre d'être posé au sol. */
        final boolean instant;
        final String lore;

        SheepType(String key, String label, DyeColor dye, Material item, int rgb, int fuse, boolean instant, String lore) {
            this.key = key;
            this.label = label;
            this.dye = dye;
            this.item = item;
            this.rgb = rgb;
            this.fuse = fuse;
            this.instant = instant;
            this.lore = lore;
        }

        static SheepType of(String key) {
            for (SheepType t : values()) if (t.key.equals(key)) return t;
            return null;
        }
    }

    /** Bonus de la laine magique, dans l'ordre des couleurs de laine (comme sur Hypixel). */
    private static final DyeColor[] MAGIC_ORDER = {DyeColor.WHITE, DyeColor.ORANGE, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.YELLOW, DyeColor.LIME, DyeColor.PINK, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.CYAN, DyeColor.PURPLE,
            DyeColor.BLUE, DyeColor.BROWN, DyeColor.GREEN, DyeColor.RED, DyeColor.BLACK};

    /** Mouton lancé : en vol tant qu'il est sur sa boule de neige, puis armé à l'impact. */
    private static final class Launched {
        final SheepType type;
        final Side side;
        final UUID owner;
        final boolean gravityShot;
        boolean armed;
        int fuse;
        int life;
        /** Ticks passés armé mais pas encore au sol. */
        int airborne;

        Launched(SheepType type, Side side, UUID owner, boolean gravityShot) {
            this.type = type;
            this.side = side;
            this.owner = owner;
            this.gravityShot = gravityShot;
            this.fuse = type.fuse;
        }
    }

    private record Op(int x, int y, int z, BlockData data) {}

    private NamespacedKey kitKey, sheepKey, ballKey;
    /** Monde de la partie 1 (nom de base des copies : base_2, base_3, base_modele). */
    private String base;
    private int cy;

    /** Parties (la partie 1 en tête, toujours présente). */
    private final List<Game> games = new ArrayList<>();
    /** Joueur inscrit (ou en attente d'une copie) -> sa partie. */
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<String, BlockData> dataCache = new HashMap<>();
    /** Zone de jeu de chaque map une fois posée (minX, maxX, minY, maxY, minZ, maxZ), pour les copies. */
    private final Map<String, int[]> areaCache = new HashMap<>();
    private final Random random = new Random();

    /** Monde modèle (base_modele) : copie propre de la partie 1, source des copies ; personne n'y va. */
    private World modele;
    private String modeleMap;
    private boolean modeleBusy;
    private final List<Runnable> modeleWaiters = new ArrayList<>();
    /** Copies détruites récemment (nom -> ms) : le nom n'est pas réutilisé tant que la suppression peut tourner. */
    private final Map<String, Long> destroyedAt = new HashMap<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        sheepKey = new NamespacedKey(this, "sheep");
        ballKey = new NamespacedKey(this, "ball");
        saveDefaultConfig();
        reloadConfig();
        base = getConfig().getString("world", "sheepwars");
        cy = cfg("center-y", 100);
        Game first = new Game(1);
        games.add(first);
        first.attach(Bukkit.getWorld(base));
        if (first.world == null) getLogger().warning("Monde " + base + " introuvable.");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::sheepTick, 1L, 1L);
        // map neuve à chaque démarrage, puis modèle des copies
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (first.world == null) first.attach(Bukkit.getWorld(base));
            if (first.world == null) return;
            WorldClone.cleanup(this, first.world, base, maxInstances());
            first.remakeModele = true;
            first.rebuild(null, pickMap(first, false));
        }, 100L);
        reportStatus();
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games)) g.shutdown();
        for (Game g : new ArrayList<>(games))
            if (g.n > 1 && g.world != null) Bukkit.unloadWorld(g.world, false);
        if (modele != null) Bukkit.unloadWorld(modele, false);
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private String modeleName() {
        return base + "_modele";
    }

    /** Monde de partie (sheepwars, sheepwars_n) ou modèle : toutes les protections s'y appliquent. */
    private boolean isProtected(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modeleName()));
    }

    private Game gameOf(World w) {
        if (w == null) return null;
        for (Game g : games) if (!g.destroyed && w.equals(g.world)) return g;
        return null;
    }

    private Game game(int n) {
        for (Game g : games) if (g.n == n && !g.destroyed) return g;
        return null;
    }

    /** Partie où le joueur est inscrit (pas en attente de copie). */
    private Game playing(Player p) {
        Game g = byPlayer.get(p.getUniqueId());
        return g != null && g.players.contains(p.getUniqueId()) ? g : null;
    }

    /** Limites de la map (barrières) et socle : jamais cassés, déplacés ni gelés par les moutons. */
    private static boolean isFixed(Material m) {
        return m == Material.BARRIER || m == Material.BEDROCK;
    }

    // ------------------------------------------------------------------ boucles

    private void tick() {
        for (Game g : new ArrayList<>(games)) {
            if (g.destroyed) continue;
            g.tick();
            // copie vide et au repos : détruite, la prochaine repartira du modèle
            if (!g.destroyed && g.n > 1 && !g.preparing && !g.building && g.state == State.WAITING && g.size() == 0) destroyGame(g);
        }
    }

    private void sheepTick() {
        for (Game g : new ArrayList<>(games)) if (!g.destroyed && g.world != null) g.sheepTick();
    }

    // ------------------------------------------------------------------ modèle et copies

    /** Refait base_modele depuis la partie 1 (qui vient d'être reconstruite). */
    private void remakeModele() {
        Game first = game(1);
        if (first == null || first.world == null) return;
        modeleBusy = true;
        if (modele != null) {
            World old = modele;
            modele = null;
            for (Player p : old.getPlayers()) sendToHub(p);
            // pas de WorldClone.destroy ici : create supprime le dossier avant de copier (pas de suppression en parallèle)
            if (!Bukkit.unloadWorld(old, false)) getLogger().warning("Impossible de décharger " + old.getName() + ".");
        }
        String map = first.mapId;
        WorldClone.create(this, first.world, modeleName(), w -> {
            modele = w;
            modeleMap = map;
            modeleBusy = false;
            if (w == null) getLogger().warning("Modèle " + modeleName() + " indisponible : pas de partie en plus.");
            else getLogger().info("Modèle " + modeleName() + " prêt (map " + map + ").");
            List<Runnable> todo = new ArrayList<>(modeleWaiters);
            modeleWaiters.clear();
            todo.forEach(Runnable::run);
        });
    }

    /** Lance r quand le modèle est prêt (tout de suite s'il l'est). */
    private void withModele(Runnable r) {
        if (modele != null && !modeleBusy) r.run();
        else modeleWaiters.add(r);
    }

    /** Numéro libre pour une nouvelle copie (2..max), 0 si aucun ; -1 si un nom se libère encore. */
    private int freeIndex() {
        boolean cooling = false;
        for (int n = 2; n <= maxInstances(); n++) {
            if (game(n) != null) continue;
            Long t = destroyedAt.get(WorldClone.name(base, n));
            if (t != null && System.currentTimeMillis() - t < 15000) { cooling = true; continue; }
            return n;
        }
        return cooling ? -1 : 0;
    }

    /** Nouvelle partie (copie du modèle) pour ce joueur ; il attend au hub. */
    private void newCopy(Player p, int n) {
        Game g = new Game(n);
        g.preparing = true;
        g.pending.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), g);
        games.add(g);
        p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
        reportStatus();
        withModele(() -> {
            if (g.destroyed) return;
            if (modele == null) { failCopy(g); return; }
            String map = modeleMap;
            WorldClone.create(this, modele, WorldClone.name(base, n), w -> {
                if (g.destroyed) {
                    if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                    return;
                }
                if (w == null) { failCopy(g); return; }
                g.attach(w);
                g.mapId = map;
                g.restoreArea();
                g.preparing = false;
                World hub = hubWorld();
                for (UUID id : new ArrayList<>(g.pending)) {
                    g.pending.remove(id);
                    Player pl = Bukkit.getPlayer(id);
                    // parti entre-temps (autre jeu, déconnecté) : on ne l'emmène pas
                    if (pl == null || (hub != null && !pl.getWorld().equals(hub))) {
                        byPlayer.remove(id);
                        if (pl != null) pl.sendMessage(PREFIX + "§7Tu n'es plus dans le hub : inscription annulée.");
                        continue;
                    }
                    g.addPlayer(pl);
                }
                if (g.players.isEmpty()) {
                    // tout le monde a renoncé pendant la copie
                    destroyGame(g);
                    return;
                }
                // chaque partie tire sa map ; si c'est celle du modèle, la copie est déjà prête
                String pick = pickMap(g, true);
                if (!pick.equals(g.mapId) || !areaCache.containsKey(g.mapId)) g.rebuild(null, pick);
                reportStatus();
            });
        });
    }

    private void failCopy(Game g) {
        for (UUID id : g.pending) {
            byPlayer.remove(id);
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) pl.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie.");
        }
        g.pending.clear();
        g.destroyed = true;
        games.remove(g);
        reportStatus();
    }

    /** Détruit une copie (jamais la partie 1) : joueurs et spectateurs au hub, monde déchargé et supprimé. */
    private void destroyGame(Game g) {
        if (g.n <= 1) return;
        g.shutdown();
        g.destroyed = true;
        games.remove(g);
        if (g.world != null) {
            destroyedAt.put(g.world.getName(), System.currentTimeMillis());
            WorldClone.destroy(this, g.world, base, this::sendToHub);
            getLogger().info("Partie " + g.n + " terminée : copie " + g.world.getName() + " supprimée.");
        }
        reportStatus();
    }

    // ------------------------------------------------------------------ inscription

    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id)) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        // un spectateur qui s'inscrit repasse d'abord par le hub
        boolean spec = false;
        for (Game g : games) spec |= g.spectators.remove(id);
        if (spec) sendToHub(p);
        int max = cfg("max-players", 12);
        // partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus remplie d'abord
        Game best = null;
        for (Game g : games) {
            if (g.destroyed || g.size() >= max) continue;
            if (!g.preparing && (g.world == null || (g.state != State.WAITING && g.state != State.STARTING))) continue;
            if (best == null || g.size() > best.size()) best = g;
        }
        if (best != null) {
            for (Game g : games) g.spectators.remove(id);
            if (best.preparing) {
                best.pending.add(id);
                byPlayer.put(id, best);
                p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
                reportStatus();
            } else best.addPlayer(p);
            return;
        }
        Game first = game(1);
        if (first == null || first.world == null) { p.sendMessage(PREFIX + "§cLa map n'est pas chargée."); return; }
        if (games.size() < maxInstances()) {
            int n = freeIndex();
            if (n > 0) {
                for (Game g : games) g.spectators.remove(id);
                newCopy(p, n);
                return;
            }
            if (n < 0) { p.sendMessage(PREFIX + "§cUne partie se libère, réessaie dans quelques secondes."); return; }
        }
        p.sendMessage(PREFIX + "§cToutes les parties sont pleines.");
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
    }

    /**
     * Le plugin ne touche au joueur (inventaire, armure, XP, vie, faim, effets, mode de jeu) que s'il est
     * dans un monde du jeu (partie, copies, modèle) ou au hub : ailleurs (skyblock...) c'est l'inventaire
     * de CE monde que Multiverse-Inventories sauvegarderait.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        return isProtected(w) || w.equals(hubWorld());
    }

    /**
     * Renvoi au hub. Joueur dans un monde du jeu : réinitialisé dans ce monde puis téléporté (MV-Inv sauvegarde
     * l'inventaire du jeu et recharge celui du hub, objets du hub compris). Déjà au hub : réinitialisé sur place.
     * Ailleurs (skyblock...) : ni téléporté ni réinitialisé.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    private void resetPlayer(Player p, GameMode mode) {
        if (!mayTouch(p)) return;
        p.getInventory().clear();
        p.setGameMode(mode);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setLevel(0);
        p.setExp(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    // ------------------------------------------------------------------ objets

    private ItemStack unbreakable(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack armor(Material type, Side s) {
        ItemStack it = unbreakable(kit(new ItemStack(type), null));
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(s.armor);
        it.setItemMeta(meta);
        return it;
    }

    /** Objet de kit : marqué pour ne pas tomber à la mort ni être jeté. */
    private ItemStack kit(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        if (name != null) meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    private SheepType sheepOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String k = it.getItemMeta().getPersistentDataContainer().get(sheepKey, PersistentDataType.STRING);
        return k == null ? null : SheepType.of(k);
    }

    private ItemStack sheepItem(SheepType t) {
        ItemStack it = new ItemStack(t.item);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(t.label, TextColor.color(t.rgb)).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(t.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Clic gauche : tir droit - clic droit : tir en cloche", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(new NamespacedKey("eemicraft", "sheep_" + t.key.replace('-', '_')));
        meta.getPersistentDataContainer().set(sheepKey, PersistentDataType.STRING, t.key);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private void giveSheep(Player p, int n) {
        if (!mayTouch(p)) return;
        int held = 0;
        for (ItemStack it : p.getInventory().getContents()) if (sheepOf(it) != null) held += it.getAmount();
        n = Math.min(n, cfg("max-sheep", 6) - held);
        for (int i = 0; i < n; i++) {
            SheepType t = randomSheep();
            if (t == null) return;
            p.getInventory().addItem(sheepItem(t));
            p.sendActionBar(Component.text("+ ", NamedTextColor.GRAY).append(Component.text(t.label, TextColor.color(t.rgb))));
            p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1.2f);
        }
    }

    private SheepType randomSheep() {
        int total = 0;
        for (SheepType t : SheepType.values()) total += Math.max(0, getConfig().getInt("weights." + t.key, 10));
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (SheepType t : SheepType.values()) {
            r -= Math.max(0, getConfig().getInt("weights." + t.key, 10));
            if (r < 0) return t;
        }
        return SheepType.EXPLOSIF;
    }

    private void top(Player p, Material m, int add, int max) {
        if (!mayTouch(p)) return;
        int have = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == m) have += it.getAmount();
        int n = Math.min(add, max - have);
        if (n > 0) p.getInventory().addItem(kit(new ItemStack(m, n), null));
    }

    private int sheepDelay(int teamAlive) {
        if (teamAlive >= 5) return 12;
        if (teamAlive >= 3) return 10;
        if (teamAlive == 2) return 8;
        return 6;
    }

    // ------------------------------------------------------------------ partie

    /** Une partie de Sheep Wars, dans son propre monde (partie 1 = monde d'origine, n ≥ 2 = copie). */
    final class Game {
        final int n;
        World world;
        Location lobby;
        /** Copie en cours de création : les joueurs attendent au hub (pending). */
        boolean preparing;
        boolean destroyed;
        /** Refaire le modèle à la fin de la prochaine construction (partie 1). */
        boolean remakeModele;

        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID, Side> teams = new HashMap<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Launched> sheep = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs non inscrits (/sheepwars spectate). */
        final Set<UUID> spectators = new java.util.HashSet<>();
        final Map<Side, Integer> sheepTimer = new EnumMap<>(Side.class);
        /** Bonus actifs par équipe : nom -> fin (ms). */
        final Map<Side, Map<String, Long>> buffs = new EnumMap<>(Side.class);

        State state = State.WAITING;
        int timer;
        int cageTimer;
        int magicTimer;
        Block magicWool;
        int magicColor;
        int magicLife;
        boolean building;
        final List<Block> cages = new ArrayList<>();

        String mapId = "rose";
        /** Zone de jeu de la map en place (coordonnées monde), mise à jour à la construction. */
        int aMinX = -CLEAR_X, aMaxX = CLEAR_X, aMinY, aMaxY, aMinZ = -CLEAR_Z, aMaxZ = CLEAR_Z;

        Game(int n) {
            this.n = n;
        }

        void attach(World w) {
            world = w;
            lobby = w == null ? null : new Location(w, 0.5, cfg("lobby-y", 140) + 1, 0.5);
        }

        int size() {
            return players.size() + pending.size();
        }

        /** Pas de lancement pendant une construction, une préparation ou la copie du modèle (partie 1). */
        boolean busy() {
            return building || preparing || (n == 1 && modeleBusy);
        }

        void restoreArea() {
            int[] a = areaCache.get(mapId);
            if (a == null) return;
            aMinX = a[0]; aMaxX = a[1]; aMinY = a[2]; aMaxY = a[3]; aMinZ = a[4]; aMaxZ = a[5];
        }

        /** Places de départ de la map (une par joueur ; s'il y a plus de joueurs que de places, elles sont partagées). */
        Location spawnSlot(Side s, int i) {
            Imported m = cur();
            int[][] pts = s == Side.ROUGE ? m.red() : m.blue();
            int[] o = pts[i % pts.length];
            int[] p = toWorld(m, o[0], o[1], o[2]);
            Location l = new Location(world, p[0] + 0.5, p[1] + 1, p[2] + 0.5);
            l.setYaw(s == Side.ROUGE ? -90 : 90);
            return l;
        }

        boolean inArea(int x, int y, int z) {
            return x >= aMinX && x <= aMaxX && z >= aMinZ && z <= aMaxZ && y >= aMinY && y <= aMaxY;
        }

        boolean buff(Side s, String key) {
            if (s == null) return false;
            Long until = buffs.computeIfAbsent(s, k -> new HashMap<>()).get(key);
            return until != null && until > System.currentTimeMillis();
        }

        void setBuff(Side s, String key, int seconds) {
            buffs.computeIfAbsent(s, k -> new HashMap<>()).put(key, System.currentTimeMillis() + seconds * 1000L);
        }

        // -------------------------------------------------------------- boucle (1x / seconde)

        void tick() {
            if (preparing || world == null) return;
            switch (state) {
                case WAITING -> {
                    if (!busy() && players.size() >= cfg("min-players", 2)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                        reportStatus();
                    }
                }
                case STARTING -> {
                    if (players.size() < cfg("min-players", 2)) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                        reportStatus();
                    } else {
                        if (players.size() >= cfg("max-players", 12)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0) for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        if (timer <= 0) {
                            if (!busy()) startGame();
                        } else timer--;
                    }
                }
                case PLAYING -> {
                    if (cageTimer > 0) {
                        if (--cageTimer == 0) openCages();
                        else for (Player p : online(alive)) {
                            // le titre « Équipe X » posé au départ reste affiché : le décompte passe dans la barre d'action
                            p.sendActionBar(Component.text("Les cages s'ouvrent dans ", NamedTextColor.GRAY)
                                    .append(Component.text(cageTimer + " s", NamedTextColor.YELLOW)));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
                        }
                        break;
                    }
                    timer--;
                    // moutons : plus l'équipe a perdu de joueurs, plus ils arrivent vite (12/10/8/6 s)
                    for (Side s : Side.values()) {
                        int t = sheepTimer.getOrDefault(s, 1) - 1;
                        if (t <= 0) {
                            for (Player p : online(alive)) if (teams.get(p.getUniqueId()) == s) {
                                giveSheep(p, 1);
                                refill(p);
                            }
                            t = sheepDelay(count(s));
                        }
                        sheepTimer.put(s, t);
                    }
                    if (--magicTimer <= 0) {
                        magicTimer = cfg("magic-wool-interval", 30);
                        spawnMagicWool();
                    }
                    if (magicWool != null && --magicLife <= 0) removeMagicWool();
                    if (timer <= 0) {
                        int r = count(Side.ROUGE), b = count(Side.BLEU);
                        endGame(r == b ? null : (r > b ? Side.ROUGE : Side.BLEU), "Temps écoulé !");
                    }
                }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (!destroyed) for (Player p : online(players)) updateSidebar(p);
        }

        int count(Side s) {
            int c = 0;
            for (UUID id : alive) if (teams.get(id) == s) c++;
            return c;
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("sheepwars", Criteria.DUMMY,
                        Component.text("SHEEP ", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                                .append(Component.text("WARS", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Écrit les lignes du scoreboard (une équipe par ligne, sans clignotement). */
        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("sheepwars");
            for (int i = 0; i < 15; i++) {
                String entry = "§" + Integer.toHexString(i) + "§r";
                Team t = b.getTeam("line" + i);
                if (i < lines.size()) {
                    if (t == null) {
                        t = b.registerNewTeam("line" + i);
                        t.addEntry(entry);
                    }
                    t.prefix(lines.get(i));
                    o.getScore(entry).setScore(lines.size() - i);
                } else if (t != null) {
                    b.resetScores(entry);
                    t.unregister();
                }
            }
        }

        void updateSidebar(Player p) {
            List<Component> l = new ArrayList<>();
            l.add(Component.text(" "));
            l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("#" + n, NamedTextColor.WHITE)));
            l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(building ? "..." : mapName(), NamedTextColor.YELLOW)));
            switch (state) {
                case WAITING, STARTING -> {
                    l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 12), NamedTextColor.WHITE)));
                    l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    if (building) l.add(Component.text("Construction de la map...", NamedTextColor.GOLD));
                    else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                    else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                }
                case PLAYING, ENDING -> {
                    l.add(Component.text("Rouge : ", NamedTextColor.RED).append(Component.text(count(Side.ROUGE) + " en vie", NamedTextColor.WHITE)));
                    l.add(Component.text("Bleu : ", NamedTextColor.BLUE).append(Component.text(count(Side.BLEU) + " en vie", NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    Side s = teams.get(p.getUniqueId());
                    if (s != null) l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color)));
                    l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                    if (state == State.PLAYING && s != null && cageTimer == 0)
                        l.add(Component.text("Mouton dans : ", NamedTextColor.GRAY).append(Component.text(Math.max(0, sheepTimer.getOrDefault(s, 0)) + " s", NamedTextColor.YELLOW)));
                    l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(
                            String.format("%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60), NamedTextColor.WHITE)));
                    if (s != null) {
                        List<String> active = new ArrayList<>();
                        if (buff(s, "big")) active.add("explosions+");
                        if (buff(s, "arrows")) active.add("flèches explosives");
                        if (buff(s, "punch")) active.add("recul");
                        if (buff(s, "fast")) active.add("moutons rapides");
                        if (buff(s, "slow")) active.add("moutons lents");
                        if (buff(s, "nobow")) active.add("arcs bloqués");
                        if (!active.isEmpty()) l.add(Component.text(String.join(", ", active), NamedTextColor.LIGHT_PURPLE));
                    }
                }
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Pseudos colorés par équipe, sur le scoreboard de chaque joueur. */
        void colorNames() {
            for (Player viewer : online(players)) {
                Scoreboard b = board(viewer);
                for (Side s : Side.values()) {
                    Team t = b.getTeam("sw_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("sw_" + s.key());
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
                }
                for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("sw_" + e.getValue().key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("sw_" + s.key());
                    if (t != null) t.unregister();
                }
        }

        // -------------------------------------------------------------- inscription

        void addPlayer(Player p) {
            UUID id = p.getUniqueId();
            spectators.remove(id);
            players.add(id);
            byPlayer.put(id, this);
            p.teleport(lobby);
            later(() -> prepareLobbyPlayer(p));
            broadcast("§a" + p.getName() + " §7a rejoint Sheep Wars (§f" + players.size() + "§7).");
            if (state == State.PLAYING || state == State.ENDING) p.sendMessage(PREFIX + "§7Une partie est en cours, tu joueras à la suivante.");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, kit(new ItemStack(Material.RED_BED), "Quitter Sheep Wars"));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (pending.remove(id)) {
                byPlayer.remove(id);
                reportStatus();
                return;
            }
            if (!players.remove(id)) return;
            byPlayer.remove(id);
            Side side = teams.remove(id);
            boolean wasAlive = alive.remove(id);
            boards.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            colorNames();
            if (state == State.PLAYING && side != null && wasAlive) {
                broadcast("§7" + p.getName() + " a quitté la partie.");
                checkWin();
            }
            reportStatus();
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            List<Player> list = online(players);
            if (list.size() < 2 || busy()) { state = State.WAITING; reportStatus(); return; }
            Collections.shuffle(list);
            teams.clear();
            alive.clear();
            kills.clear();
            lastHit.clear();
            lastHitTime.clear();
            buffs.clear();
            for (int i = 0; i < list.size(); i++) {
                teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? Side.ROUGE : Side.BLEU);
                alive.add(list.get(i).getUniqueId());
            }
            colorNames();
            state = State.PLAYING;
            timer = cfg("game-time", 600);
            cageTimer = cfg("cage-time", 5);
            magicTimer = cfg("magic-wool-interval", 30);
            for (Side s : Side.values()) sheepTimer.put(s, sheepDelay(count(s)));
            Map<Side, Integer> slot = new EnumMap<>(Side.class);
            for (Player p : list) {
                Side s = teams.get(p.getUniqueId());
                int i = slot.merge(s, 1, Integer::sum) - 1;
                Location at = spawnSlot(s, i);
                buildCage(at);
                p.teleport(at);
                later(() -> equip(p));
                // titre de départ : l'équipe du joueur, dans sa couleur, et le but en sous-titre
                p.showTitle(Title.title(Component.text("Équipe " + s.fem(), s.color).decorate(TextDecoration.BOLD),
                        Component.text("Élimine l'équipe adverse", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(Math.max(2, cfg("cage-time", 5))), Duration.ofMillis(500))));
            }
            broadcast("§7Map : §e" + mapName());
            broadcast("§eLa partie commence ! §7Clic gauche = tir droit, clic droit = tir en cloche. Tire sur la §dlaine magique §7à l'arc pour des bonus. Une seule vie !");
            reportStatus();
        }

        void buildCage(Location at) {
            Block b0 = at.getBlock();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 3; dy++) {
                        boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == 3;
                        if (!wall) continue;
                        Block b = b0.getRelative(dx, dy, dz);
                        if (b.getType().isAir()) {
                            b.setType(Material.GLASS, false);
                            cages.add(b);
                        }
                    }
        }

        void openCages() {
            for (Block b : cages) if (b.getType() == Material.GLASS) b.setType(Material.AIR, false);
            cages.clear();
            for (Player p : online(alive)) {
                p.showTitle(Title.title(Component.text("GO !", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                        Component.text("Lance tes moutons sur le camp adverse", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, 1f);
            }
        }

        void equip(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || state != State.PLAYING || !mayTouch(p)) return;
            resetPlayer(p, GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            inv.setHelmet(armor(Material.LEATHER_HELMET, s));
            inv.setChestplate(armor(Material.LEATHER_CHESTPLATE, s));
            inv.setLeggings(armor(Material.LEATHER_LEGGINGS, s));
            inv.setBoots(armor(Material.LEATHER_BOOTS, s));
            inv.setItem(0, unbreakable(kit(new ItemStack(Material.STONE_SWORD), null)));
            inv.setItem(1, unbreakable(kit(new ItemStack(Material.BOW), null)));
            inv.setItem(2, kit(new ItemStack(s.wool, 32), null));
            inv.setItem(8, kit(new ItemStack(Material.ARROW, 16), null));
            // les blocs de la map se cassent vite (comme sur Hypixel)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, PotionEffect.INFINITE_DURATION, 2, false, false, false));
            // moutons de départ dès la cage (lançables à l'ouverture)
            giveSheep(p, cfg("start-sheep", 2));
        }

        /** À chaque mouton reçu : quelques blocs et une flèche de plus (plafonnés). */
        void refill(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (s == null) return;
            top(p, s.wool, 4, 64);
            top(p, Material.ARROW, 2, 32);
        }

        void eliminate(Player p, String msg) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            broadcast(msg);
            p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Tu regardes la fin de la partie", NamedTextColor.GRAY)));
            checkWin();
            reportStatus();
        }

        void checkWin() {
            if (state != State.PLAYING) return;
            int r = count(Side.ROUGE), b = count(Side.BLEU);
            if (r == 0 && b == 0) endGame(null, "Plus personne en vie !");
            else if (r == 0) endGame(Side.BLEU, "L'équipe Rouge est éliminée !");
            else if (b == 0) endGame(Side.ROUGE, "L'équipe Bleue est éliminée !");
        }

        void endGame(Side winner, String reason) {
            endGame(winner, reason, true);
        }

        /** rewards = false pour un arrêt admin : pas de récompenses EemiCore. */
        void endGame(Side winner, String reason, boolean rewards) {
            state = State.ENDING;
            if (rewards) {
                for (Map.Entry<UUID, Side> en : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p == null) continue;
                    eemi("reward " + p.getName() + " sheepwars play");
                    if (en.getValue() == winner) eemi("reward " + p.getName() + " sheepwars win");
                }
            }
            timer = cfg("end-time", 8);
            removeMagicWool();
            Component title = winner == null ? Component.text("Égalité !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                    : Component.text("Victoire " + winner.label + " !", winner.color).decorate(TextDecoration.BOLD); // titre court (≤ 16 caractères)
            for (Player p : online(players)) {
                if (!teams.containsKey(p.getUniqueId())) continue;
                p.showTitle(Title.title(title, Component.text(reason, NamedTextColor.GRAY)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (p.getGameMode() == GameMode.SURVIVAL && mayTouch(p)) p.setGameMode(GameMode.ADVENTURE);
            }
            broadcast("§6" + reason);
            reportStatus();
        }

        /**
         * Fin de partie : les inscrits retournent au salon d'attente et la map est reconstruite (nouvelle map au
         * hasard). Une copie sans plus aucun inscrit est détruite au lieu d'être reconstruite.
         */
        void backToLobby() {
            clearNames();
            for (UUID id : new ArrayList<>(teams.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) continue;
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            teams.clear();
            alive.clear();
            cages.clear();
            state = State.WAITING;
            sendSpectatorsToHub();
            reportStatus();
            if (n > 1 && online(players).isEmpty()) {
                destroyGame(this);
                return;
            }
            Bukkit.getScheduler().runTaskLater(EemiSheepWars.this, () -> {
                if (!destroyed) rebuild(null, pickMap(this, false));
            }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (!destroyed) state = State.WAITING;
            reportStatus();
        }

        /** Arrêt du plugin / destruction : moutons, laine magique, joueurs et spectateurs au hub. */
        void shutdown() {
            sendSpectatorsToHub();
            for (UUID id : new ArrayList<>(sheep.keySet())) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
            sheep.clear();
            removeMagicWool();
            for (Player p : online(players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
            }
            for (UUID id : players) byPlayer.remove(id);
            for (UUID id : pending) byPlayer.remove(id);
            players.clear();
            pending.clear();
        }

        // -------------------------------------------------------------- laine magique

        void spawnMagicWool() {
            removeMagicWool();
            // au-dessus de la séparation, à côté de l'îlot central (dont la cage de barrières arrêterait les flèches)
            magicWool = world.getBlockAt(0, baseY() + cur().magicRel() + random.nextInt(3), (random.nextBoolean() ? 1 : -1) * (10 + random.nextInt(5)));
            magicColor = 0;
            magicLife = cfg("magic-wool-life", 20);
            magicWool.setType(Material.valueOf(MAGIC_ORDER[0].name() + "_WOOL"), false);
            world.playSound(magicWool.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.5f);
            broadcast("§dLa laine magique est apparue ! §7Tire dessus à l'arc : la couleur donne le bonus.");
        }

        void removeMagicWool() {
            if (magicWool != null && magicWool.getType().name().endsWith("_WOOL")) magicWool.setType(Material.AIR, false);
            magicWool = null;
        }

        /** La laine magique change de couleur toutes les demi-secondes. */
        void cycleMagicWool() {
            if (magicWool == null) return;
            magicColor = (magicColor + 1) % MAGIC_ORDER.length;
            magicWool.setType(Material.valueOf(MAGIC_ORDER[magicColor].name() + "_WOOL"), false);
            world.spawnParticle(Particle.FLAME, magicWool.getLocation().add(0.5, 0.5, 0.5), 6, 0.4, 0.4, 0.4, 0.01);
        }

        void magicHit(Player shooter, DyeColor color) {
            Side s = teams.get(shooter.getUniqueId());
            if (s == null) return;
            removeMagicWool();
            String what;
            switch (color) {
                case ORANGE -> { setBuff(s, "big", 15); what = "explosions plus grosses (15 s)"; }
                case LIGHT_BLUE -> { setBuff(s.other(), "slow", 10); what = "moutons ennemis ralentis (10 s)"; }
                case PINK -> {
                    for (Player p : teamAlive(s)) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 1));
                    what = "régénération (10 s)";
                }
                case GRAY -> {
                    for (Player p : teamAlive(s.other())) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 5 * 20, 0));
                    what = "aveuglement des ennemis (5 s)";
                }
                case LIGHT_GRAY -> { setBuff(s, "punch", 20); what = "flèches à recul (20 s)"; }
                case CYAN -> { setBuff(s, "fast", 10); what = "moutons plus rapides (10 s)"; }
                case BLUE -> { setBuff(s.other(), "nobow", 10); what = "arcs ennemis bloqués (10 s)"; }
                case GREEN -> {
                    for (Player p : teamAlive(s.other())) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 3 * 20, 1));
                    what = "poison sur les ennemis (3 s)";
                }
                case RED -> { setBuff(s, "arrows", 15); what = "flèches explosives (15 s)"; }
                default -> {
                    for (Player p : teamAlive(s)) giveSheep(p, 1);
                    what = "+1 mouton pour l'équipe";
                }
            }
            broadcast(s.code() + shooter.getName() + " §7a touché la laine magique : §d" + what + "§7 pour " + s.code() + "l'équipe " + s.fem() + "§7.");
            for (Player p : online(players)) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        }

        List<Player> teamAlive(Side s) {
            List<Player> list = new ArrayList<>();
            for (Player p : online(alive)) if (teams.get(p.getUniqueId()) == s) list.add(p);
            return list;
        }

        // -------------------------------------------------------------- moutons

        void launch(Player p, SheepType t, boolean gravity) {
            Side s = teams.get(p.getUniqueId());
            double speed = gravity ? getConfig().getDouble("gravity-speed", 2.0) : getConfig().getDouble("straight-speed", 1.3);
            if (buff(s, "fast")) speed *= 1.3;
            if (buff(s, "slow")) speed *= 0.7;
            Vector v = p.getLocation().getDirection().multiply(speed);
            Snowball ball = p.launchProjectile(Snowball.class, v);
            ball.setItem(new ItemStack(t.item));
            if (!gravity) ball.setGravity(false);
            ball.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);
            Sheep sh = world.spawn(ball.getLocation(), Sheep.class, e -> {
                e.setColor(t.dye);
                e.customName(Component.text(t.label, TextColor.color(t.rgb)));
                e.setCustomNameVisible(true);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                double health = Math.max(1, getConfig().getDouble("sheep-health", 20));
                AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(health);
                e.setHealth(health);
            });
            ball.addPassenger(sh);
            if (t == SheepType.ABORDAGE) {
                sh.addPassenger(p);
                p.setFallDistance(0);
            }
            sheep.put(sh.getUniqueId(), new Launched(t, s, p.getUniqueId(), gravity));
            world.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1f, gravity ? 0.7f : 1.1f);
        }

        void arm(Launched l, Entity e) {
            if (l.armed) return;
            l.armed = true;
            for (Entity r : new ArrayList<>(e.getPassengers())) {
                e.removePassenger(r);
                r.setFallDistance(0);
            }
            e.setVelocity(new Vector());
            world.playSound(e.getLocation(), Sound.ENTITY_SHEEP_HURT, 1f, 1f);
            if (l.type.instant || l.type == SheepType.SOIN) l.fuse = 0;
        }

        /** Toutes les ticks : vol guidé, mèches, effets durables, nettoyage des moutons perdus, laine magique. */
        void sheepTick() {
            if (magicWool != null && Bukkit.getCurrentTick() % 10 == 0) cycleMagicWool();
            if (sheep.isEmpty()) return;
            // copie des clés : une explosion peut tuer d'autres moutons (et les retirer de la map) pendant la boucle
            for (UUID id : new ArrayList<>(sheep.keySet())) {
                Launched l = sheep.get(id);
                if (l == null) continue;
                Entity e = Bukkit.getEntity(id);
                l.life++;
                if (e == null || !e.isValid() || e.getLocation().getY() < mapVoidY() || l.life > 600 || (state != State.PLAYING && l.side != null)) {
                    if (e != null) {
                        if (e.getVehicle() != null) e.getVehicle().remove();
                        e.remove();
                    }
                    sheep.remove(id);
                    continue;
                }
                if (!l.armed) {
                    Entity ball = e.getVehicle();
                    if (ball != null && l.type == SheepType.CHERCHEUR) home(l, ball);
                    // la boule a disparu sans toucher, ou vol trop long (tir droit sans gravité) : on arme sur place
                    if (ball == null || l.life > (l.gravityShot ? 200 : 100)) {
                        if (ball != null) ball.remove();
                        arm(l, e);
                    }
                    continue;
                }
                Location at = e.getLocation();
                world.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(l.type.rgb), 1.2f));
                // les effets « au sol » attendent que le mouton soit posé (5 s max en l'air)
                if (!l.type.instant && !e.isOnGround() && ++l.airborne < 100) continue;
                switch (l.type) {
                    case TROU_NOIR -> pull(l, at);
                    case POISON -> poison(l, at);
                    default -> { }
                }
                if (--l.fuse <= 0) {
                    sheep.remove(id);
                    trigger(l, e);
                    e.remove();
                }
            }
        }

        /** Mouton chercheur : la boule de neige s'oriente vers l'ennemi le plus proche. */
        void home(Launched l, Entity ball) {
            Player target = null;
            double best = 40 * 40;
            for (Player p : enemiesNear(l, ball.getLocation(), 40)) {
                double d = p.getLocation().distanceSquared(ball.getLocation());
                if (d < best) { best = d; target = p; }
            }
            if (target == null) return;
            Vector to = target.getLocation().add(0, 1, 0).toVector().subtract(ball.getLocation().toVector());
            if (to.lengthSquared() < 0.01) return;
            Vector v = ball.getVelocity();
            double speed = Math.max(1.1, v.length());
            Vector nv = v.normalize().multiply(0.8).add(to.normalize().multiply(0.35)).normalize().multiply(speed);
            ball.setGravity(false);
            ball.setVelocity(nv);
        }

        List<Player> enemiesNear(Launched l, Location at, double r) {
            List<Player> list = new ArrayList<>();
            for (Player p : at.getNearbyPlayers(r)) {
                // mouton de test (admin hors partie) : touche tout le monde sauf le lanceur
                if (l.side == null) {
                    if (!p.getUniqueId().equals(l.owner) && p.getGameMode() == GameMode.SURVIVAL) list.add(p);
                    continue;
                }
                Side s = teams.get(p.getUniqueId());
                if (s != null && s != l.side && alive.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) list.add(p);
            }
            return list;
        }

        /** Trou noir : aspire les ennemis et arrache des blocs vers lui. */
        void pull(Launched l, Location at) {
            world.spawnParticle(Particle.PORTAL, at, 20, 2, 1, 2, 0.5);
            for (Player p : enemiesNear(l, at, 8)) {
                Vector v = at.toVector().subtract(p.getLocation().toVector());
                if (v.lengthSquared() < 1) continue;
                p.setVelocity(p.getVelocity().add(v.normalize().multiply(0.12)));
                if (l.fuse % 20 == 0) p.showTitle(Title.title(Component.empty(), Component.text("Tu es aspiré par un trou noir !", NamedTextColor.DARK_PURPLE),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)));
            }
            if (l.fuse % 10 == 0) {
                Block b = at.getBlock().getRelative(random.nextInt(7) - 3, -1 - random.nextInt(2), random.nextInt(7) - 3);
                if (b.getType().isSolid() && !isFixed(b.getType()) && inArea(b.getX(), b.getY(), b.getZ())) {
                    BlockData data = b.getBlockData();
                    b.setType(Material.AIR);
                    FallingBlock fb = world.spawnFallingBlock(b.getLocation().add(0.5, 0.2, 0.5), data);
                    fb.setDropItem(false);
                    fb.setVelocity(at.toVector().subtract(b.getLocation().toVector()).normalize().multiply(0.4).setY(0.5));
                }
            }
        }

        void poison(Launched l, Location at) {
            world.spawnParticle(Particle.DUST, at.clone().add(0, 0.5, 0), 25, 3, 0.6, 3, 0,
                    new Particle.DustOptions(Color.fromRGB(0x2E8B2E), 1.6f));
            if (l.fuse % 20 != 0) return;
            for (Player p : enemiesNear(l, at, 4)) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 1));
        }

        float power(Launched l, float b) {
            float p = b;
            if (l.gravityShot && (l.type == SheepType.EXPLOSIF || l.type == SheepType.EXPLOSIF_FORT)) p *= 1.25f;
            if (buff(l.side, "big")) p *= 1.4f;
            return p;
        }

        void trigger(Launched l, Entity e) {
            Location at = e.getLocation();
            Player owner = Bukkit.getPlayer(l.owner);
            switch (l.type) {
                case EXPLOSIF -> world.createExplosion(at, power(l, 3.0f), false, true, owner);
                case EXPLOSIF_FORT -> world.createExplosion(at, power(l, 4.4f), false, true, owner);
                case CHERCHEUR -> world.createExplosion(at, power(l, 2.6f), false, true, owner);
                case INCENDIAIRE -> world.createExplosion(at, power(l, 2.4f), true, true, owner);
                case TROU_NOIR -> world.createExplosion(at, power(l, 1.5f), false, true, owner);
                case GLACE -> {
                    forSphere(at, 3, b -> {
                        if (b.getType().isSolid() && !isFixed(b.getType()) && b.getRelative(0, 1, 0).getType().isAir()) b.setType(Material.PACKED_ICE);
                    });
                    world.spawnParticle(Particle.SNOWFLAKE, at, 80, 3, 1, 3, 0.05);
                    world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
                    for (Player p : enemiesNear(l, at, 5)) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6 * 20, 2));
                }
                case AVEUGLANT -> {
                    world.spawnParticle(Particle.LARGE_SMOKE, at, 80, 3, 1, 3, 0.02);
                    world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.5f);
                    for (Player p : enemiesNear(l, at, 6)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 6 * 20, 0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 6 * 20, 0));
                    }
                }
                case SEISME -> {
                    int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ();
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++) {
                            if (dx * dx + dz * dz > 9) continue;
                            for (int dy = -3; dy <= -1; dy++) {
                                Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                                if (!b.getType().isSolid() || isFixed(b.getType()) || !inArea(b.getX(), b.getY(), b.getZ())) continue;
                                BlockData data = b.getBlockData();
                                b.setType(Material.AIR);
                                FallingBlock fb = world.spawnFallingBlock(b.getLocation().add(0.5, 0, 0.5), data);
                                fb.setDropItem(false);
                                fb.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.4, 0.4 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.4));
                            }
                        }
                    world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                    for (Player p : enemiesNear(l, at, 5)) {
                        p.setVelocity(p.getVelocity().setY(1.0));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 5 * 20, 0));
                        p.showTitle(Title.title(Component.empty(), Component.text("Tremblement de terre !", NamedTextColor.GOLD),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
                    }
                }
                case SOIN -> {
                    world.spawnParticle(Particle.HEART, at.clone().add(0, 1, 0), 15, 2, 1, 2, 0);
                    world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                    for (Player p : at.getNearbyPlayers(6))
                        if (teams.get(p.getUniqueId()) == l.side && alive.contains(p.getUniqueId())) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6 * 20, 1));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6 * 20, 0));
                        }
                }
                case POISON, ABORDAGE -> { }
                case INTERGALACTIQUE -> {
                    world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.8f);
                    for (int i = 0; i < 8; i++) {
                        Location from = at.clone().add(random.nextInt(13) - 6, 28 + random.nextInt(6), random.nextInt(13) - 6);
                        Bukkit.getScheduler().runTaskLater(EemiSheepWars.this, () -> {
                            if (destroyed || (state != State.PLAYING && l.side != null)) return;
                            org.bukkit.entity.Fireball fb = world.spawn(from, org.bukkit.entity.Fireball.class, f -> {
                                f.setDirection(new Vector(0, -1, 0));
                                f.setYield(1.8f);
                                f.setIsIncendiary(false);
                                if (owner != null) f.setShooter(owner);
                            });
                            fb.setVelocity(new Vector(0, -1.2, 0));
                        }, 5L * i);
                    }
                }
                case ENDER -> {
                    world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    world.spawnParticle(Particle.PORTAL, at, 120, 3, 1, 3, 1);
                    for (Player p : enemiesNear(l, at, 5)) {
                        Location to = safeSpotNear(at, 8);
                        if (to == null) continue;
                        to.setYaw(random.nextFloat() * 360);
                        p.teleport(to);
                        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    }
                }
                case ECHANGEUR -> {
                    Player target = null;
                    double best = 8 * 8;
                    for (Player p : enemiesNear(l, at, 8)) {
                        double d = p.getLocation().distanceSquared(at);
                        if (d < best) { best = d; target = p; }
                    }
                    if (owner == null || target == null || owner.getGameMode() != GameMode.SURVIVAL || !world.equals(owner.getWorld())) {
                        world.spawnParticle(Particle.SMOKE, at, 20, 0.5, 0.5, 0.5, 0.02);
                        break;
                    }                    Location a = owner.getLocation(), b = target.getLocation();
                    owner.teleport(b);
                    target.teleport(a);
                    owner.setFallDistance(0);
                    target.setFallDistance(0);
                    for (Player p : List.of(owner, target)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
                        world.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
                    }
                }
                case FOUDRE -> {
                    world.strikeLightning(at);
                    for (int i = 0; i < 2; i++)
                        world.strikeLightning(at.clone().add(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
                }
            }
        }

        /** Case au hasard autour du point, avec un bloc solide dessous et de la place pour un joueur. */
        Location safeSpotNear(Location c, int r) {
            for (int i = 0; i < 20; i++) {
                int x = c.getBlockX() + random.nextInt(2 * r + 1) - r, z = c.getBlockZ() + random.nextInt(2 * r + 1) - r;
                for (int y = c.getBlockY() + 3; y >= c.getBlockY() - 4; y--) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable() && b.getRelative(0, 2, 0).isPassable()
                            && inArea(x, y, z)) return b.getLocation().add(0.5, 1, 0.5);
                }
            }
            return null;
        }

        void forSphere(Location c, int r, java.util.function.Consumer<Block> f) {
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r) continue;
                        Block b = c.getBlock().getRelative(dx, dy, dz);
                        if (inArea(b.getX(), b.getY(), b.getZ())) f.accept(b);
                    }
        }

        // -------------------------------------------------------------- map

        String mapName() {
            Imported m = IMPORTED.get(mapId);
            return m == null ? mapId : m.name();
        }

        Imported cur() {
            Imported m = IMPORTED.get(mapId);
            return m != null ? m : IMPORTED.values().iterator().next();
        }

        int mapVoidY() {
            return baseY() + cur().voidRel();
        }

        /** Point d'observation (éliminés, spectateurs) : au-dessus de la séparation. */
        Location spectatorPoint() {
            return new Location(world, 0.5, baseY() + cur().magicRel() + 6, 0.5, 0f, 50f);
        }

        /**
         * Vide la zone de jeu puis construit la map id (map importée, voir Imported) et la salle d'attente.
         * Blocs posés par paquets.
         */
        void rebuild(CommandSender sender, String id) {
            if (world == null || building) return;
            building = true;
            try {
                clearEntities();
                removeMagicWool();
                mapId = id;
                Builder b = new Builder();
                for (int x = -CLEAR_X; x <= CLEAR_X; x++)
                    for (int z = -CLEAR_Z; z <= CLEAR_Z; z++)
                        for (int y = cy - CLEAR_DOWN; y <= cy + CLEAR_UP; y++) b.put(x, y, z, null);
                importedMap(b, cur());
                lobbyRoom(b);
                apply(b.ops, 0, sender);
            } catch (RuntimeException | java.io.IOException ex) {
                building = false;
                if (remakeModele) {
                    remakeModele = false;
                    // pas de nouveau modèle : les copies en attente échouent proprement (l'ancien modèle reste utilisable)
                    List<Runnable> todo = new ArrayList<>(modeleWaiters);
                    modeleWaiters.clear();
                    todo.forEach(Runnable::run);
                }
                getLogger().log(java.util.logging.Level.SEVERE, "Construction de la map " + mapId + " impossible", ex);
                if (sender != null) sender.sendMessage(PREFIX + "§cErreur de construction (voir la console).");
            }
        }

        void apply(List<Op> ops, int from, CommandSender sender) {
            if (destroyed) return;
            int end = Math.min(ops.size(), from + 40000);
            for (int i = from; i < end; i++) {
                Op o = ops.get(i);
                Block b = world.getBlockAt(o.x, o.y, o.z);
                if (o.data == null) {
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                } else if (!b.getBlockData().equals(o.data)) b.setBlockData(o.data, false);
            }
            if (end < ops.size()) {
                Bukkit.getScheduler().runTaskLater(EemiSheepWars.this, () -> apply(ops, end, sender), 1L);
                return;
            }
            building = false;
            clearEntities();
            getLogger().info("Map Sheep Wars construite (" + world.getName() + ") : " + mapName() + " (" + mapId + ", " + ops.size() + " opérations).");
            if (sender != null) sender.sendMessage(PREFIX + "§aMap construite : §e" + mapName());
            if (remakeModele) {
                remakeModele = false;
                remakeModele();
            }
        }

        /** Lit la map, la convertit, raccorde clôtures/vitres/murets, la tourne et l'ajoute au chantier. */
        void importedMap(Builder b, Imported m) throws java.io.IOException {
            java.io.InputStream res = getResource("maps/" + mapId + ".swm");
            if (res == null) throw new java.io.IOException("maps/" + mapId + ".swm absent du jar");
            int w, h, l;
            byte[] raw;
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(res))) {
                byte[] magic = in.readNBytes(4);
                if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("SWM1")) throw new java.io.IOException("format inconnu");
                w = in.readShort();
                h = in.readShort();
                l = in.readShort();
                raw = in.readAllBytes();
            }
            BlockData[] grid = new BlockData[w * h * l];
            for (int i = 0; i < grid.length; i++) {
                int id = raw[2 * i] & 0xFF;
                // 166 = barrière : murs invisibles du mini-jeu d'origine, retirés (gênants, Thibault)
                if (id != 0 && id != 166) grid[i] = legacy(id, raw[2 * i + 1] & 0x0F);
            }
            org.bukkit.block.BlockFace[] sides = {org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST};
            for (int y = 0; y < h; y++)
                for (int z = 0; z < l; z++)
                    for (int x = 0; x < w; x++) {
                        int i = (y * l + z) * w + x;
                        BlockData bd = grid[i];
                        if (bd == null) continue;
                        Material mat = bd.getMaterial();
                        boolean fenceLike = Tag.FENCES.isTagged(mat) || pane(mat);
                        boolean wall = Tag.WALLS.isTagged(mat) && bd instanceof org.bukkit.block.data.type.Wall;
                        if (!fenceLike && !wall) continue;
                        BlockData c = bd.clone();
                        boolean ns = false, ew = false;
                        for (org.bukkit.block.BlockFace f : sides) {
                            int nx = x + f.getModX(), nz = z + f.getModZ();
                            BlockData nb = nx < 0 || nz < 0 || nx >= w || nz >= l ? null : grid[(y * l + nz) * w + nx];
                            boolean on = connects(bd, nb);
                            if (on && f.getModZ() != 0) ns = true;
                            if (on && f.getModX() != 0) ew = true;
                            if (c instanceof org.bukkit.block.data.MultipleFacing mf && mf.getAllowedFaces().contains(f)) mf.setFace(f, on);
                            if (c instanceof org.bukkit.block.data.type.Wall wl)
                                wl.setHeight(f, on ? org.bukkit.block.data.type.Wall.Height.LOW : org.bukkit.block.data.type.Wall.Height.NONE);
                        }
                        if (c instanceof org.bukkit.block.data.type.Wall wl) wl.setUp(ns == ew); // pilier sauf en ligne droite
                        grid[i] = c;
                    }
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int y = 0; y < h; y++)
                for (int z = 0; z < l; z++)
                    for (int x = 0; x < w; x++) {
                        BlockData bd = grid[(y * l + z) * w + x];
                        if (bd == null) continue;
                        if (m.rotate()) {
                            bd = bd.clone();
                            bd.rotate(org.bukkit.block.structure.StructureRotation.CLOCKWISE_180);
                        }
                        int[] p = toWorld(m, m.ox() + x, m.oy() + y, m.oz() + z);
                        b.ops.add(new Op(p[0], p[1], p[2], bd));
                        minX = Math.min(minX, p[0]);
                        maxX = Math.max(maxX, p[0]);
                        minZ = Math.min(minZ, p[2]);
                        maxZ = Math.max(maxZ, p[2]);
                    }
            aMinX = minX - 1;
            aMaxX = maxX + 1;
            aMinZ = minZ - 1;
            aMaxZ = maxZ + 1;
            aMinY = baseY() - 3;
            aMaxY = baseY() + h + 1;
            areaCache.put(mapId, new int[]{aMinX, aMaxX, aMinY, aMaxY, aMinZ, aMaxZ});
        }

        /** Retire ce qu'une partie laisse : moutons, objets, projectiles, blocs qui tombent, XP. */
        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities()) {
                if (e instanceof Player) continue;
                if (e instanceof Sheep || e instanceof Item || e instanceof Projectile || e instanceof FallingBlock
                        || e instanceof org.bukkit.entity.Mob || e instanceof org.bukkit.entity.ExperienceOrb
                        || e instanceof org.bukkit.entity.TNTPrimed) e.remove();
            }
            sheep.clear();
        }

        // -------------------------------------------------------------- spectateurs

        /** /sheepwars spectate : regarder la partie en cours en spectateur, sans y participer. */
        void spectate(Player p) {
            // au-dessus du vide central, entre les deux fermes (même point que les éliminés)
            p.teleport(spectatorPoint());
            spectators.add(p.getUniqueId());
            if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR);
            later(() -> { if (p.isOnline() && spectators.contains(p.getUniqueId()) && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            p.sendMessage(PREFIX + "§7Tu regardes la partie " + n + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            for (Player sp : online(spectators)) sendToHub(sp);
            spectators.clear();
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String stateLabel() {
            if (preparing) return "preparation";
            if (building) return "construction";
            return switch (state) {
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case PLAYING -> "en jeu";
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ construction de la map

    /** État de bloc ; une chaîne invalide donne de l'air (avertissement) au lieu d'arrêter la construction. */
    private BlockData bd(String s) {
        return dataCache.computeIfAbsent(s, k -> {
            try {
                return Bukkit.createBlockData(k.contains(":") ? k : "minecraft:" + k);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Bloc invalide ignore : " + k);
                return Material.AIR.createBlockData();
            }
        });
    }

    /** Map imposée par un admin pour la prochaine construction de la partie 1. */
    private String nextMap;

    /**
     * Map de la prochaine construction : celle imposée (partie 1), sinon au hasard parmi « maps »
     * (pas deux fois de suite dans la même partie, sauf sameOk).
     */
    private String pickMap(Game g, boolean sameOk) {
        if (g.n == 1 && nextMap != null) {
            String m = nextMap;
            nextMap = null;
            return m;
        }
        List<String> pool = new ArrayList<>();
        for (String id : getConfig().getStringList("maps")) if (IMPORTED.containsKey(id.toLowerCase())) pool.add(id.toLowerCase());
        if (pool.isEmpty()) pool.addAll(IMPORTED.keySet());
        if (!sameOk && pool.size() > 1) pool.remove(g.mapId);
        return pool.get(random.nextInt(pool.size()));
    }

    /** Liste d'opérations ; la dernière écriture d'une case l'emporte. */
    private final class Builder {
        final List<Op> ops = new ArrayList<>();

        void put(int x, int y, int z, String data) {
            ops.add(new Op(x, y, z, data == null ? null : bd(data)));
        }
    }

    // ------------------------------------------------------------------ maps importées (4.0)

    /**
     * Map reprise d'un vieux monde Sheep Wars (Minecraft 1.8, ids numériques) : blocs dans maps/<id>.swm
     * du jar (gzip : « SWM1 », largeur x, hauteur y, longueur z, puis id + data par case, ordre y/z/x),
     * convertis au chargement par Paper (fromLegacy). Coordonnées d'origine : coin (ox, oy, oz), ligne de
     * séparation des camps en x = mid, centre en z = zc. rotate = tourner de 180° pour que Rouge soit à
     * l'ouest (x < 0). voidRel / magicRel : hauteurs de mort et de la laine magique, relatives au bas de la
     * map. red / blue : places de départ (coordonnées d'origine, bloc du sol).
     */
    private record Imported(String name, int ox, int oy, int oz, int mid, int zc, boolean rotate,
                            int voidRel, int magicRel, int[][] red, int[][] blue) {}

    private static final Map<String, Imported> IMPORTED = new java.util.LinkedHashMap<>();
    static {
        IMPORTED.put("rose", new Imported("Désert rose", -113, 29, 999, -81, 1050, true, -3, 29,
                new int[][]{{-57, 51, 1050}, {-57, 51, 1052}, {-58, 50, 1054}, {-59, 50, 1056}, {-55, 50, 1048}, {-58, 51, 1041}},
                new int[][]{{-102, 50, 1049}, {-104, 51, 1052}, {-104, 51, 1054}, {-106, 50, 1056}, {-103, 51, 1063}}));
        IMPORTED.put("vert", new Imported("Îles vertes", -46, 23, 999, -17, 1050, false, -3, 27,
                new int[][]{{-35, 42, 1050}, {-35, 42, 1048}, {-35, 42, 1052}, {-33, 42, 1050}, {-37, 42, 1050}, {-33, 42, 1048}, {-33, 42, 1052}, {-35, 42, 1046}, {-35, 42, 1054}, {-37, 42, 1047}, {-33, 42, 1054}, {-38, 43, 1052}},
                new int[][]{{1, 42, 1048}, {1, 42, 1052}, {0, 42, 1050}, {2, 42, 1046}, {2, 42, 1054}, {-1, 42, 1048}, {-1, 42, 1052}, {0, 42, 1046}, {0, 42, 1054}, {-2, 42, 1050}, {2, 42, 1044}, {2, 42, 1056}}));
        // l'eau tout autour du vaisseau tue (voidRel = juste au-dessus de sa surface)
        IMPORTED.put("vaisseau", new Imported("Vaisseau", 16, 18, 999, 58, 1050, false, 7, 37,
                new int[][]{{31, 48, 1050}, {33, 48, 1050}, {32, 48, 1052}, {31, 48, 1048}, {34, 48, 1052}, {31, 48, 1046}, {35, 48, 1050}, {34, 46, 1071}, {32, 46, 1072}, {36, 46, 1071}, {34, 46, 1073}, {36, 46, 1073}},
                new int[][]{{83, 42, 1050}, {83, 42, 1052}, {81, 42, 1049}, {81, 42, 1051}, {85, 42, 1051}, {82, 42, 1054}, {81, 42, 1047}, {84, 42, 1054}, {80, 42, 1053}, {86, 42, 1053}, {82, 42, 1056}, {84, 42, 1056}}));
        // cartes générées par gen_maps.py (même plan que les îles, plus grandes)
        IMPORTED.put("glacier", new Imported("Pics gelés", 0, 0, 0, 46, 56, false, -3, 30,
                new int[][]{{20, 22, 55}, {20, 22, 57}, {20, 22, 53}, {18, 22, 55}, {22, 22, 55}, {18, 22, 57}, {22, 22, 57}, {20, 22, 59}, {18, 22, 53}, {22, 22, 53}, {18, 22, 59}, {22, 22, 59}},
                new int[][]{{72, 22, 55}, {72, 22, 57}, {72, 22, 53}, {74, 22, 55}, {70, 22, 55}, {74, 22, 57}, {70, 22, 57}, {72, 22, 59}, {74, 22, 53}, {70, 22, 53}, {74, 22, 59}, {70, 22, 59}}));
        IMPORTED.put("jade", new Imported("Temple de jade", 0, 0, 0, 46, 56, false, -3, 30,
                new int[][]{{20, 22, 55}, {20, 22, 57}, {20, 22, 53}, {18, 22, 55}, {22, 22, 55}, {18, 22, 57}, {22, 22, 57}, {20, 22, 59}, {18, 22, 53}, {22, 22, 53}, {18, 22, 59}, {22, 22, 59}},
                new int[][]{{72, 22, 55}, {72, 22, 57}, {72, 22, 53}, {74, 22, 55}, {70, 22, 55}, {74, 22, 57}, {70, 22, 57}, {72, 22, 59}, {74, 22, 53}, {70, 22, 53}, {74, 22, 59}, {70, 22, 59}}));
        // ville steampunk originale, générée par gen_steampunk.py (113 x 64 x 113)
        IMPORTED.put("engrenages", new Imported("Cité des engrenages", 0, 0, 0, 56, 56, false, -3, 34,
                new int[][]{{30, 26, 55}, {30, 26, 57}, {30, 26, 53}, {28, 26, 55}, {32, 26, 55}, {28, 26, 57}, {32, 26, 57}, {30, 26, 59}, {28, 26, 53}, {32, 26, 53}, {28, 26, 59}, {32, 26, 59}},
                new int[][]{{82, 26, 55}, {82, 26, 57}, {82, 26, 53}, {84, 26, 55}, {80, 26, 55}, {84, 26, 57}, {80, 26, 57}, {82, 26, 59}, {84, 26, 53}, {80, 26, 53}, {84, 26, 59}, {80, 26, 59}}));
    }

    /** Boîte vidée à chaque reconstruction : couvre toutes les maps, sous la salle d'attente. */
    private static final int CLEAR_X = 60, CLEAR_Z = 58, CLEAR_DOWN = 35, CLEAR_UP = 38;

    /** Bas de la map en y monde (center-y - 30) : la plus haute (vaisseau, 67 blocs) reste sous la salle d'attente. */
    private int baseY() {
        return cy - 30;
    }

    /** Point de la map d'origine -> coordonnées monde (centré sur la séparation, Rouge à l'ouest). */
    private int[] toWorld(Imported m, int x, int y, int z) {
        int dx = x - m.mid(), dz = z - m.zc();
        if (m.rotate()) {
            dx = -dx;
            dz = -dz;
        }
        return new int[]{dx, baseY() + y - m.oy(), dz};
    }

    /** Air : marque une conversion déjà tentée sans résultat (HashMap ne garde pas null). */
    private static final BlockData NONE = Material.AIR.createBlockData();
    private final Map<Integer, BlockData> legacyCache = new HashMap<>();
    private Map<Integer, Material> legacyIds;

    /** Bloc 1.8 (id + data) -> état actuel, null si inconnu (air). Feuilles toujours persistantes. Un avertissement par cas. */
    @SuppressWarnings("deprecation")
    private BlockData legacy(int id, int data) {
        BlockData r = legacyCache.computeIfAbsent(id << 4 | data, k -> {
            if (legacyIds == null) {
                legacyIds = new HashMap<>();
                // Paper réécrit Material.values() des plugins récents sans les LEGACY_ : on passe par la réflexion
                for (Material m : Material.class.getEnumConstants()) if (m.isLegacy()) legacyIds.put(m.getId(), m);
                getLogger().info(legacyIds.size() + " ids de blocs 1.8 connus.");
            }
            Material m = legacyIds.get(id);
            if (m == null) {
                getLogger().warning("Bloc 1.8 inconnu ignore : " + id + ":" + data);
                return NONE;
            }
            try {
                BlockData bd = Bukkit.getUnsafe().fromLegacy(m, (byte) data);
                if (bd == null || bd.getMaterial().isAir()) return NONE;
                if (bd instanceof org.bukkit.block.data.type.Leaves l) l.setPersistent(true);
                return bd;
            } catch (RuntimeException ex) {
                getLogger().warning("Conversion impossible du bloc 1.8 " + id + ":" + data + " : " + ex.getMessage());
                return NONE;
            }
        });
        return r == NONE ? null : r;
    }

    private static boolean pane(Material m) {
        return m == Material.IRON_BARS || m.name().endsWith("_PANE");
    }

    /** Une clôture, une vitre ou un muret se raccorde-t-il à ce voisin ? (en 1.8 c'était calculé à l'affichage) */
    private static boolean connects(BlockData self, BlockData n) {
        if (n == null) return false;
        Material a = self.getMaterial(), b = n.getMaterial();
        if (b.isOccluding()) return true;
        if (Tag.FENCES.isTagged(a)) return Tag.FENCES.isTagged(b) && (a == Material.NETHER_BRICK_FENCE) == (b == Material.NETHER_BRICK_FENCE) || Tag.FENCE_GATES.isTagged(b);
        if (pane(a)) return pane(b) || Tag.WALLS.isTagged(b);
        if (Tag.WALLS.isTagged(a)) return Tag.WALLS.isTagged(b) || Tag.FENCE_GATES.isTagged(b) || pane(b);
        return false;
    }

    /** Salle d'attente : plancher de sapin bordé de laine blanche, murs invisibles, lanternes. */
    private void lobbyRoom(Builder b) {
        int y = cfg("lobby-y", 140);
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                b.put(x, y, z, edge ? "white_wool" : ((x + z) % 2 == 0 ? "spruce_planks" : "stripped_spruce_wood"));
                for (int dy = 1; dy <= 4; dy++) b.put(x, y + dy, z, edge ? "barrier" : null);
            }
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                b.put(sx * 6, y + 1, sz * 6, "hay_block");
                b.put(sx * 6, y + 2, sz * 6, "lantern");
            }
    }


    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isProtected(e.getLocation().getWorld())
                && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        // explosions, blocs cassés, moutons : rien ne tombe au sol
        if (isProtected(e.getLocation().getWorld())) e.setCancelled(true);
    }

    /** Les limites de la map, la laine magique et ce qui sort de la zone de jeu résistent aux explosions. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        World w = e.getLocation().getWorld();
        if (!isProtected(w)) return;
        Game g = gameOf(w);
        if (g == null) e.blockList().clear();
        else e.blockList().removeIf(b -> b.equals(g.magicWool) || !g.inArea(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        World w = e.getBlock().getWorld();
        if (!isProtected(w)) return;
        Game g = gameOf(w);
        if (g == null) e.blockList().clear();
        else e.blockList().removeIf(b -> b.equals(g.magicWool) || !g.inArea(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBallHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball ball) || !ball.getPersistentDataContainer().has(ballKey, PersistentDataType.BYTE)) return;
        Game g = gameOf(ball.getWorld());
        // la boule ne touche ni son mouton ni le joueur qui le chevauche
        Entity hit = e.getHitEntity();
        if (hit != null) for (Entity passenger : ball.getPassengers())
            if (passenger.equals(hit) || passenger.getPassengers().contains(hit)) {
                e.setCancelled(true);
                return;
            }
        if (g == null) return;
        for (Entity passenger : new ArrayList<>(ball.getPassengers())) {
            ball.removePassenger(passenger);
            Launched l = g.sheep.get(passenger.getUniqueId());
            if (l == null) continue;
            Location at = ball.getLocation();
            if (e.getHitBlock() != null && e.getHitBlockFace() != null)
                at = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
            List<Entity> riders = new ArrayList<>(passenger.getPassengers());
            for (Entity r : riders) passenger.removePassenger(r);
            passenger.teleport(at);
            for (Entity r : riders) {
                r.teleport(at.clone().add(0, 1, 0));
                r.setFallDistance(0);
            }
            g.arm(l, passenger);
        }
    }

    /** Flèches : laine magique, flèches explosives, recul. */
    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) return;
        Game g = playing(shooter);
        if (g == null || g.state != State.PLAYING || !g.alive.contains(shooter.getUniqueId()) || !arrow.getWorld().equals(g.world)) return;
        Side s = g.teams.get(shooter.getUniqueId());
        Block hb = e.getHitBlock();
        if (hb != null && g.magicWool != null && hb.equals(g.magicWool)) {
            g.magicHit(shooter, MAGIC_ORDER[g.magicColor]);
            arrow.remove();
            return;
        }
        if (g.buff(s, "arrows")) {
            g.world.createExplosion(arrow.getLocation(), 1.6f, false, true, shooter);
            arrow.remove();
        }
        if (g.buff(s, "punch") && e.getHitEntity() instanceof Player victim && g.teams.get(victim.getUniqueId()) != s) {
            Vector push = arrow.getVelocity().setY(0);
            if (push.lengthSquared() > 0) victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(1.2).setY(0.35)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = playing(p);
        if (g == null || !g.alive.contains(p.getUniqueId())) return;
        if (g.cageTimer > 0 || g.buff(g.teams.get(p.getUniqueId()), "nobow")) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("Ton arc est bloqué !", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onSheepDeath(EntityDeathEvent e) {
        Game g = gameOf(e.getEntity().getWorld());
        if (g == null) return;
        Launched l = g.sheep.remove(e.getEntity().getUniqueId());
        if (l == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        // le joueur qui tue un mouton lancé (épée ou flèche, pas une explosion) le récupère dans son inventaire
        Player killer = e.getEntity().getKiller();
        EntityDamageEvent last = e.getEntity().getLastDamageCause();
        boolean direct = last != null && (last.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || last.getCause() == EntityDamageEvent.DamageCause.PROJECTILE);
        if (killer != null && direct && g.alive.contains(killer.getUniqueId()) && g.state == State.PLAYING) {
            killer.getInventory().addItem(sheepItem(l.type));
            killer.sendActionBar(Component.text("Mouton récupéré : ", NamedTextColor.GRAY)
                    .append(Component.text(l.type.label, TextColor.color(l.type.rgb))));
            killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = playing(victim);
        if (g == null) return;
        UUID id = victim.getUniqueId();
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(id) || victim.getGameMode() != GameMode.SURVIVAL) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent by) {
            Player attacker = by.getDamager() instanceof Player pl ? pl
                    : by.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl ? pl : null;
            if (attacker == null) return;
            if (g.teams.get(attacker.getUniqueId()) == g.teams.get(id) || !g.teams.containsKey(attacker.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            g.lastHit.put(id, attacker.getUniqueId());
            g.lastHitTime.put(id, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Game g = playing(p);
        if (g == null) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Player killer = p.getKiller();
        Long t = g.lastHitTime.get(p.getUniqueId());
        if (killer == null && t != null && System.currentTimeMillis() - t < 10000) killer = Bukkit.getPlayer(g.lastHit.get(p.getUniqueId()));
        String msg;
        if (killer != null && g.teams.containsKey(killer.getUniqueId()) && killer != p) {
            g.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            if (g.state == State.PLAYING) eemi("reward " + killer.getName() + " sheepwars kill");
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            msg = g.teams.get(killer.getUniqueId()).code() + killer.getName() + " §7a éliminé " + s.code() + p.getName();
        } else {
            msg = s.code() + p.getName() + " §7est éliminé.";
        }
        if (g.state == State.PLAYING) g.eliminate(p, msg);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = playing(p);
        if (g == null || g.lobby == null) return;
        if (g.teams.containsKey(p.getUniqueId()) && (g.state == State.PLAYING || g.state == State.ENDING)) {
            e.setRespawnLocation(g.spectatorPoint());
            later(() -> {
                if (!p.isOnline() || !mayTouch(p)) return; // parti ailleurs entre-temps
                resetPlayer(p, GameMode.SPECTATOR);
                p.getInventory().clear();
            });
            return;
        }
        e.setRespawnLocation(g.lobby);
        later(() -> g.prepareLobbyPlayer(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = playing(p);
        if (g == null) return;
        Location to = e.getTo();
        if (g.world == null || !to.getWorld().equals(g.world)) return;
        if (to.getBlockY() == e.getFrom().getBlockY() && to.getBlockX() == e.getFrom().getBlockX()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) {
            if (to.getY() < g.mapVoidY()) p.setHealth(0);
            return;
        }
        // salle d'attente, spectateurs : on ne tombe pas dans le vide
        if (to.getY() < g.mapVoidY() - 20 && g.lobby != null)
            p.teleport(g.state == State.WAITING || g.state == State.STARTING || !g.teams.containsKey(p.getUniqueId()) ? g.lobby : g.spectatorPoint());
    }

    /** Un inscrit qui quitte le monde de sa partie (/lobby, /mv tp...) quitte la partie, au lieu d'y être renvoyé. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g = playing(e.getPlayer());
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !e.getPlayer().getWorld().equals(g.world)) g.leave(e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (sheepOf(e.getItemInHand()) != null || !canEdit(e.getPlayer(), e.getBlock(), true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        boolean admin = e.getPlayer().getGameMode() == GameMode.CREATIVE && e.getPlayer().hasPermission("eemisheepwars.admin");
        boolean ours = isProtected(e.getBlock().getWorld());
        if (!admin && ours && isFixed(e.getBlock().getType())) {
            e.setCancelled(true);
            return;
        }
        if (!canEdit(e.getPlayer(), e.getBlock(), false)) e.setCancelled(true);
        else if (ours) e.setDropItems(false); // ailleurs les blocs doivent tomber
    }

    /**
     * Construction : seulement dans son camp (on ne peut pas dépasser d'un bloc au-dessus du vide,
     * comme sur Hypixel). Casser : n'importe où dans la zone de jeu. Admins en créatif : libres.
     */
    private boolean canEdit(Player p, Block b, boolean place) {
        if (!isProtected(b.getWorld())) return true;
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemisheepwars.admin")) return true;
        Game g = gameOf(b.getWorld());
        if (g == null || playing(p) != g) return false;
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(p.getUniqueId())) return false;
        if (!g.inArea(b.getX(), b.getY(), b.getZ())) return false;
        if (!place) return true;
        Side s = g.teams.get(p.getUniqueId());
        return s != null && b.getX() * s.dir() >= 2;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        Game g = playing(p);
        if (g == null) {
            // test d'un mouton par un admin hors partie (/sheepwars give <type>)
            SheepType t = sheepOf(it);
            Game here = gameOf(p.getWorld());
            if (t != null && here != null && p.hasPermission("eemisheepwars.admin")) {
                e.setCancelled(true);
                here.launch(p, t, right);
            }
            return;
        }
        if (right && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté Sheep Wars.");
            return;
        }
        SheepType t = sheepOf(it);
        if (t == null) return;
        e.setCancelled(true);
        if (g.state != State.PLAYING || g.cageTimer > 0 || !g.alive.contains(p.getUniqueId())) return;
        long now = System.currentTimeMillis();
        if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 750) return;
        cooldown.put(p.getUniqueId(), now);
        it.setAmount(it.getAmount() - 1);
        g.launch(p, t, right);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (playing(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && playing(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, g.players.contains(p.getUniqueId()));
        cooldown.remove(p.getUniqueId());
    }

    /** Un spectateur qui quitte le monde de la partie (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        for (Game g : games) {
            if (!g.spectators.contains(p.getUniqueId()) || (g.world != null && p.getWorld().equals(g.world))) continue;
            g.spectators.remove(p.getUniqueId());
            // seulement s'il arrive au hub : ailleurs le mode est celui du monde (Multiverse)
            if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        boolean was = false;
        for (Game g : games) was |= g.spectators.remove(e.getPlayer().getUniqueId());
        if (was) sendToHub(e.getPlayer());
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut des parties, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé pour EemiCore : waiting si une partie attend (ou décompte, ou se prépare), sinon playing
     * si une partie joue ; attente = inscrits hors partie, en jeu = joueurs encore en vie ; max = max par
     * partie × nombre de parties possibles.
     */
    private void reportStatus() {
        boolean anyWait = false, anyPlay = false;
        long waiting = 0;
        int inGame = 0;
        for (Game g : games) {
            if (g.destroyed) continue;
            boolean play = g.state == State.PLAYING || g.state == State.ENDING;
            if (g.preparing || !play) anyWait = true;
            else anyPlay = true;
            waiting += g.pending.size() + g.players.stream().filter(u -> !g.teams.containsKey(u)).count();
            if (play) inGame += g.alive.size();
        }
        String st = anyWait ? "waiting" : anyPlay ? "playing" : "waiting";
        eemi("status sheepwars " + st + " " + waiting + " " + inGame + " " + cfg("max-players", 12) * maxInstances());
    }

    // ------------------------------------------------------------------ commandes

    /** Partie visée par une commande admin : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            try {
                return game(Integer.parseInt(args[idx]));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = playing(p);
            if (g != null) return g;
            g = gameOf(p.getWorld());
            if (g != null) return g;
        }
        return game(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemisheepwars.admin");
        switch (sub) {
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                if (p == null) return true;
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) return true;
                g.leave(p, g.players.contains(p.getUniqueId()));
                p.sendMessage(PREFIX + "§7Tu as quitté Sheep Wars.");
            }
            case "spectate", "spec" -> {
                if (p == null) return true;
                if (byPlayer.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§cTu es inscrit : /sheepwars leave d'abord."); return true; }
                Game g = null;
                if (args.length > 1) {
                    Player who = Bukkit.getPlayerExact(args[1]);
                    g = who == null ? null : playing(who);
                    if (g == null || g.state != State.PLAYING) { p.sendMessage(PREFIX + "§cCe joueur n'est pas dans une partie en cours."); return true; }
                } else {
                    for (Game c : games)
                        if (!c.destroyed && c.state == State.PLAYING && c.world != null && (g == null || c.alive.size() > g.alive.size())) g = c;
                    if (g == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return true; }
                }
                for (Game c : games) c.spectators.remove(p.getUniqueId());
                g.spectate(p);
            }
            case "start" -> {
                if (!admin) return true;
                Game g = target(sender, args, 1);
                if (g == null || g.world == null) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cLa partie " + g.n + " est déjà en cours."); return true; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits dans la partie " + g.n + "."); return true; }
                if (g.busy()) { sender.sendMessage(PREFIX + "§cLa map est en construction."); return true; }
                g.startGame();
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = target(sender, args, 1);
                if (g == null) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.n + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games)
                    sender.sendMessage("§7 #" + g.n + " §f" + (g.world == null ? WorldClone.name(base, g.n) : g.world.getName())
                            + " §7- " + g.stateLabel() + " - map §e" + g.mapName() + " §7- joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §7(+" + g.pending.size() + " en attente)")
                            + (g.state == State.PLAYING ? " §7- en vie §f" + g.alive.size() : ""));
                sender.sendMessage("§7 Modèle : §f" + modeleName() + " §7- " + (modeleBusy ? "en copie" : modele == null ? "absent" : "prêt (map " + modeleMap + ")"));
            }
            case "build" -> {
                if (!admin) return true;
                Game g = game(1);
                if (g == null || g.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g.state == State.PLAYING || g.state == State.ENDING) { sender.sendMessage(PREFIX + "§cAttends la fin de la partie 1."); return true; }
                if (g.building) { sender.sendMessage(PREFIX + "§cLa map est déjà en construction."); return true; }
                if (preparingCopies()) { sender.sendMessage(PREFIX + "§cUne copie est en préparation, réessaie dans quelques secondes."); return true; }
                sender.sendMessage(PREFIX + "§7Construction de la map (partie 1), puis copie du modèle...");
                g.remakeModele = true;
                g.rebuild(sender, pickMap(g, false));
            }
            case "map", "maps" -> {
                if (!admin) return true;
                Game g = game(1);
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + "§7Map de la partie 1 : §e" + (g == null ? "?" : g.mapName()) + " §7- maps : §f" + String.join(", ", IMPORTED.keySet()));
                    sender.sendMessage(PREFIX + "§7/sheepwars map <id> : impose la prochaine map de la partie 1 (reconstruite tout de suite si aucune partie).");
                    return true;
                }
                String id = args[1].toLowerCase();
                if (!IMPORTED.containsKey(id)) { sender.sendMessage(PREFIX + "§cMap inconnue. §7" + String.join(", ", IMPORTED.keySet())); return true; }
                nextMap = id;
                if (g != null && (g.state == State.WAITING || g.state == State.STARTING) && !g.building && g.world != null && !preparingCopies()) {
                    sender.sendMessage(PREFIX + "§7Construction de §e" + IMPORTED.get(id).name() + "§7 (partie 1)...");
                    g.remakeModele = true;
                    g.rebuild(sender, pickMap(g, false));
                } else sender.sendMessage(PREFIX + "§7Prochaine map de la partie 1 : §e" + IMPORTED.get(id).name());
            }
            case "give" -> {
                // test : /sheepwars give <type>
                if (!admin || p == null || args.length < 2) return true;
                SheepType t = SheepType.of(args[1].toLowerCase());
                if (t != null) p.getInventory().addItem(sheepItem(t));
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games)
                    if (g.state == State.PLAYING || g.state == State.ENDING) { sender.sendMessage(PREFIX + "§cAttends la fin des parties en cours."); return true; }
                reloadConfig();
                cy = cfg("center-y", 100);
                for (Game g : games) if (g.world != null) g.attach(g.world);
                Game first = game(1);
                if (first != null && first.world == null) first.attach(Bukkit.getWorld(base));
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/sheepwars <join|leave|spectate [joueur]>" + (admin ? " §8- admin : start|stop [n], parties, build, map, give, reload" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ utilitaires

    /** Copie en cours de création (on ne refait pas le modèle pendant qu'il est lu). */
    private boolean preparingCopies() {
        if (modeleBusy) return true;
        for (Game g : games) if (g.preparing && !g.destroyed) return true;
        return false;
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTaskLater(this, r, 3L);
    }

    private List<Player> online(Set<UUID> ids) {
        List<Player> list = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) list.add(p);
        }
        return list;
    }
}
