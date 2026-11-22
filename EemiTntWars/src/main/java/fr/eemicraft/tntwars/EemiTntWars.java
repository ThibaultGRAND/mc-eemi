package fr.eemicraft.tntwars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * TNT Wars d'EEMICRAFT. Rouge (ouest) contre Bleu (est), séparés par un canal d'eau infranchissable :
 * chaque camp construit des canons à TNT pour atteindre l'autre. Les joueurs reçoivent TNT, redstone,
 * répéteurs, boutons, seaux d'eau et blocs, rechargés régulièrement. Chaque équipe a un stock de vies
 * partagé ; une fois vide, chaque mort élimine. La TNT d'une équipe ne blesse pas ses propres joueurs.
 * La map (deux camps, canal, salle d'attente) est construite par le plugin et reconstruite après chaque partie.
 */
public final class EemiTntWars extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§6TNT Wars§8] §7";

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Side {
        ROUGE("Rouge", NamedTextColor.RED, Color.RED, Material.RED_CONCRETE, Material.RED_WOOL),
        BLEU("Bleu", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), Material.BLUE_CONCRETE, Material.BLUE_WOOL);

        final String label;
        final NamedTextColor color;
        final Color armor;
        final Material concrete;
        final Material wool;

        Side(String label, NamedTextColor color, Color armor, Material concrete, Material wool) {
            this.label = label;
            this.color = color;
            this.armor = armor;
            this.concrete = concrete;
            this.wool = wool;
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
    }

    private record Op(int x, int y, int z, Material m) {}


    private NamespacedKey kitKey, tntTeamKey, tntOwnerKey;
    /** Monde de la partie 1 (nom de base des copies : base_2, base_3, base_modele). */
    private String base;
    private int cy, canal, halfLength, halfWidth, voidY;

    /** Parties (la partie 1 en tête, toujours présente). */
    private final List<Game> games = new ArrayList<>();
    /** Joueur inscrit (ou en attente d'une copie) -> sa partie. */
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    /**
     * Blocs posés par la construction de la map : indestructibles (explosions et pioche). La map est
     * identique dans toutes les parties (construite de la même façon, copies du modèle).
     */
    private final Set<Long> mapBlocks = new java.util.HashSet<>();

    /** Monde modèle (base_modele) : copie propre de la partie 1, source des copies ; personne n'y va. */
    private World modele;
    private boolean modeleBusy;
    private final List<Runnable> modeleWaiters = new ArrayList<>();
    /** Copies détruites récemment (nom -> ms) : le nom n'est pas réutilisé tant que la suppression peut tourner. */
    private final Map<String, Long> destroyedAt = new HashMap<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        tntTeamKey = new NamespacedKey(this, "tnt_team");
        tntOwnerKey = new NamespacedKey(this, "tnt_owner");
        saveDefaultConfig();
        loadSettings();
        Game first = new Game(1);
        games.add(first);
        first.attach(Bukkit.getWorld(base));
        if (first.world == null) getLogger().warning("Monde " + base + " introuvable.");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // map neuve à chaque démarrage, puis modèle des copies
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (first.world == null) first.attach(Bukkit.getWorld(base));
            if (first.world == null) return;
            WorldClone.cleanup(this, first.world, base, maxInstances());
            first.remakeModele = true;
            first.rebuild(null);
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

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "tntwars");
        cy = cfg("center-y", 100);
        canal = cfg("canal-half", 6);
        halfLength = cfg("half-length", 42);
        halfWidth = cfg("half-width", 26);
        voidY = cfg("void-y", 80);
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

    /** Monde de partie (tntwars, tntwars_n) ou modèle : toutes les protections s'y appliquent. */
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

    /** Zone de jeu : tout ce qui sort de cette boîte est nettoyé à la remise à zéro. */
    private boolean inArea(int x, int y, int z) {
        return Math.abs(x) <= halfLength + 4 && Math.abs(z) <= halfWidth + 4 && y >= cy - 25 && y <= cy + cfg("build-height", 25) + 5;
    }

    /** Camp d'un bloc (null = canal). */
    private Side sideOf(int x) {
        if (x < -canal) return Side.ROUGE;
        if (x > canal) return Side.BLEU;
        return null;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // ------------------------------------------------------------------ boucle (1x / seconde)

    private void tick() {
        for (Game g : new ArrayList<>(games)) {
            if (g.destroyed) continue;
            g.tick();
            // copie vide et au repos : détruite, la prochaine repartira du modèle
            if (!g.destroyed && g.n > 1 && !g.preparing && !g.building && g.state == State.WAITING && g.size() == 0) destroyGame(g);
        }
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
        WorldClone.create(this, first.world, modeleName(), w -> {
            modele = w;
            modeleBusy = false;
            if (w == null) getLogger().warning("Modèle " + modeleName() + " indisponible : pas de partie en plus.");
            else getLogger().info("Modèle " + modeleName() + " prêt.");
            runModeleWaiters();
        });
    }

    private void runModeleWaiters() {
        List<Runnable> todo = new ArrayList<>(modeleWaiters);
        modeleWaiters.clear();
        todo.forEach(Runnable::run);
    }

    /** Lance r quand le modèle est prêt (tout de suite s'il l'est). */
    private void withModele(Runnable r) {
        if (modele != null && !modeleBusy) r.run();
        else modeleWaiters.add(r);
    }

    /** Copie en cours de création (on ne refait pas le modèle pendant qu'il est lu). */
    private boolean preparingCopies() {
        if (modeleBusy) return true;
        for (Game g : games) if (g.preparing && !g.destroyed) return true;
        return false;
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
            WorldClone.create(this, modele, WorldClone.name(base, n), w -> {
                if (g.destroyed) {
                    if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                    return;
                }
                if (w == null) { failCopy(g); return; }
                g.attach(w);
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
                if (g.players.isEmpty()) destroyGame(g);
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
        if (g.n <= 1 || g.destroyed) return;
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
        int max = cfg("max-players", 16);
        // partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus remplie d'abord
        Game best = null;
        for (Game g : games) {
            if (g.destroyed || g.size() >= max) continue;
            if (!g.preparing && (g.world == null || (g.state != State.WAITING && g.state != State.STARTING))) continue;
            if (best == null || g.size() > best.size()) best = g;
        }
        if (best != null) {
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
            if (n > 0) { newCopy(p, n); return; }
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

    private ItemStack armor(Material type, Side s) {
        ItemStack it = kit(new ItemStack(type), null);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(s.armor);
        meta.setUnbreakable(true);
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

    /** Objet de kit avec un nom (jaune) et une ligne d'aide (grise). */
    private ItemStack kit(ItemStack it, String name, String lore) {
        kit(it, null);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(meta);
        return it;
    }

    /** Un objet du kit de TNT Wars, toujours avec le même nom et la même aide (kit et recharge s'empilent). */
    private ItemStack kitItem(Material m, int amount, Side s) {
        ItemStack it = new ItemStack(m, amount);
        return switch (m) {
            case TNT -> kit(it, "TNT", "Le cœur de tes canons. Rechargée en continu.");
            case REDSTONE -> kit(it, "Poudre de redstone", "Relie boutons, répéteurs et TNT. Rechargée.");
            case REPEATER -> kit(it, "Répéteur", "Retarde le signal pour régler tes tirs.");
            case STONE_BUTTON -> kit(it, "Bouton", "Clic droit : une impulsion pour tirer.");
            case LEVER -> kit(it, "Levier", "Signal continu : allume ou coupe un circuit.");
            case WATER_BUCKET -> kit(it, "Seau d'eau infini", "L'eau protège ton canon de sa propre charge.");
            case COBBLESTONE -> kit(it, "Pierre", "Pour construire. Rechargée en continu.");
            case IRON_PICKAXE -> kit(it, "Pioche", "Casse les blocs posés par les joueurs.");
            case FLINT_AND_STEEL -> kit(it, "Briquet", "Allume une TNT à la main.");
            case REDSTONE_TORCH -> kit(it, "Torche de redstone", "Source de signal permanente.");
            case COMPARATOR -> kit(it, "Comparateur", "Pour les circuits avancés.");
            case OBSERVER -> kit(it, "Observateur", "Envoie une impulsion quand un bloc change.");
            case STONE_PRESSURE_PLATE -> kit(it, "Plaque de pression", "Envoie un signal quand on marche dessus.");
            case DISPENSER -> kit(it, "Distributeur", "Une TNT dedans sort déjà allumée.");
            case SAND -> kit(it, "Sable", "Tombe : utile pour les canons à sable.");
            case PISTON -> kit(it, "Piston", "Pousse les blocs.");
            case STICKY_PISTON -> kit(it, "Piston collant", "Pousse et ramène les blocs.");
            case SLIME_BLOCK -> kit(it, "Bloc de slime", "Entraîne les blocs voisins avec un piston.");
            case SMOOTH_STONE_SLAB -> kit(it, "Dalle de pierre", "Demi-bloc pour les circuits. Rechargée.");
            case LADDER -> kit(it, "Échelle", "Pour grimper.");
            default -> m == s.wool ? kit(it, "Laine " + s.fem(), "Pour construire. Rechargée en continu.") : kit(it, null);
        };
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ partie

    /** Une partie de TNT Wars, dans son propre monde (partie 1 = monde d'origine, n ≥ 2 = copie). */
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
        final Map<Side, Integer> lives = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        /** TNT posées : position du bloc -> joueur qui l'a posée (pour l'équipe et le kill). */
        final Map<Long, UUID> placedTnt = new HashMap<>();
        final Map<UUID, UUID> lastHit = new HashMap<>();
        final Map<UUID, Long> lastHitTime = new HashMap<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs non inscrits (/tntwars spectate). */
        final Set<UUID> spectators = new java.util.HashSet<>();

        State state = State.WAITING;
        int timer;
        int refillTimer;
        boolean building;

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

        /** Point de réapparition : au fond du camp, face au canal. */
        Location spawn(Side s) {
            Location l = new Location(world, s.dir() * (halfLength - 5) + 0.5, cy + 1, 0.5);
            l.setYaw(s == Side.ROUGE ? -90 : 90);
            return l;
        }

        Location aboveCanal(float yaw) {
            return new Location(world, 0.5, cy + 25, 0.5, yaw, 50f);
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
                        if (players.size() >= cfg("max-players", 16)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0) for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                        if (timer <= 0) {
                            if (!busy()) startGame();
                        } else timer--;
                    }
                }
                case PLAYING -> {
                    timer--;
                    if (--refillTimer <= 0) {
                        refillTimer = cfg("refill-interval", 10);
                        for (Player p : online(alive)) if (p.getGameMode() == GameMode.SURVIVAL) refill(p);
                    }
                    if (timer <= 0) {
                        int r = lives.get(Side.ROUGE) * 100 + count(Side.ROUGE), b = lives.get(Side.BLEU) * 100 + count(Side.BLEU);
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
                Objective o = b.registerNewObjective("tntwars", Criteria.DUMMY,
                        Component.text("TNT ", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                                .append(Component.text("WARS", NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Écrit les lignes du scoreboard (une équipe par ligne, sans clignotement). */
        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("tntwars");
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
            switch (state) {
                case WAITING, STARTING -> {
                    l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 16), NamedTextColor.WHITE)));
                    l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    if (building) l.add(Component.text("Construction de la map...", NamedTextColor.GOLD));
                    else if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                    else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                }
                case PLAYING, ENDING -> {
                    for (Side s : Side.values())
                        l.add(Component.text(s.label + " : ", s.color).append(Component.text(
                                lives.getOrDefault(s, 0) + " vies, " + count(s) + " en jeu", NamedTextColor.WHITE)));
                    l.add(Component.text("  "));
                    Side s = teams.get(p.getUniqueId());
                    if (s != null) l.add(Component.text("Équipe : ", NamedTextColor.GRAY).append(Component.text(s.fem(), s.color)));
                    l.add(Component.text("Kills : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(kills.getOrDefault(p.getUniqueId(), 0)), NamedTextColor.WHITE)));
                    if (state == State.PLAYING)
                        l.add(Component.text("Recharge dans : ", NamedTextColor.GRAY).append(Component.text(Math.max(0, refillTimer) + " s", NamedTextColor.YELLOW)));
                    l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(
                            String.format("%d:%02d", Math.max(0, timer) / 60, Math.max(0, timer) % 60), NamedTextColor.WHITE)));
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
                    Team t = b.getTeam("tw_" + s.key());
                    if (t == null) {
                        t = b.registerNewTeam("tw_" + s.key());
                        t.color(s.color);
                        t.prefix(Component.text("[" + s.label + "] ", s.color));
                        t.setAllowFriendlyFire(false);
                    }
                    for (String e : new ArrayList<>(t.getEntries())) t.removeEntry(e);
                }
                for (Map.Entry<UUID, Side> e : teams.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) b.getTeam("tw_" + e.getValue().key()).addEntry(p.getName());
                }
            }
        }

        void clearNames() {
            for (Scoreboard b : boards.values())
                for (Side s : Side.values()) {
                    Team t = b.getTeam("tw_" + s.key());
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
            broadcast("§a" + p.getName() + " §7a rejoint TNT Wars (§f" + players.size() + "§7).");
            if (state == State.PLAYING || state == State.ENDING) p.sendMessage(PREFIX + "§7Une partie est en cours, tu joueras à la suivante.");
            reportStatus();
        }

        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, kit(new ItemStack(Material.RED_BED), "Quitter TNT Wars"));
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
            placedTnt.clear();
            for (Side s : Side.values()) lives.put(s, cfg("team-lives", 15));
            for (int i = 0; i < list.size(); i++) {
                teams.put(list.get(i).getUniqueId(), i % 2 == 0 ? Side.ROUGE : Side.BLEU);
                alive.add(list.get(i).getUniqueId());
            }
            colorNames();
            state = State.PLAYING;
            timer = cfg("game-time", 900);
            refillTimer = cfg("refill-interval", 10);
            for (Player p : list) {
                Side s = teams.get(p.getUniqueId());
                p.teleport(spawn(s));
                later(() -> equip(p));
                p.showTitle(Title.title(Component.text("Équipe " + s.fem(), s.color).decorate(TextDecoration.BOLD),
                        Component.text("Construis des canons, vise l'ennemi !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            }
            broadcast("§eLa partie commence ! §7Le canal ne se traverse pas : tout se joue aux canons. §6"
                    + cfg("team-lives", 15) + " vies §7par équipe.");
            reportStatus();
        }

        /** Kit complet : armure de l'équipe + matériel de canon (rechargé ensuite par refill). */
        void equip(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (!p.isOnline() || s == null || state != State.PLAYING || destroyed || !mayTouch(p)) return;
            resetPlayer(p, GameMode.SURVIVAL);
            PlayerInventory inv = p.getInventory();
            inv.setHelmet(armor(Material.LEATHER_HELMET, s));
            inv.setChestplate(armor(Material.LEATHER_CHESTPLATE, s));
            inv.setLeggings(armor(Material.LEATHER_LEGGINGS, s));
            inv.setBoots(armor(Material.LEATHER_BOOTS, s));
            // barre : l'essentiel du canon ; le reste rangé dans l'inventaire (le 2e seau d'eau, inutile car infini, est retiré)
            inv.setItem(0, kitItem(Material.TNT, 64, s));
            inv.setItem(1, kitItem(Material.REDSTONE, 64, s));
            inv.setItem(2, kitItem(Material.REPEATER, 16, s));
            inv.setItem(3, kitItem(Material.STONE_BUTTON, 16, s));
            inv.setItem(4, kitItem(Material.LEVER, 4, s));
            inv.setItem(5, kitItem(Material.WATER_BUCKET, 1, s));
            inv.setItem(6, kitItem(Material.COBBLESTONE, 64, s));
            inv.setItem(7, kitItem(Material.IRON_PICKAXE, 1, s));
            inv.setItem(8, kitItem(Material.FLINT_AND_STEEL, 1, s));
            // 1re rangée de l'inventaire : redstone avancée et mécanismes
            inv.setItem(9, kitItem(Material.REDSTONE_TORCH, 8, s));
            inv.setItem(10, kitItem(Material.COMPARATOR, 8, s));
            inv.setItem(11, kitItem(Material.OBSERVER, 8, s));
            inv.setItem(12, kitItem(Material.STONE_PRESSURE_PLATE, 8, s));
            inv.setItem(13, kitItem(Material.DISPENSER, 16, s));
            inv.setItem(14, kitItem(Material.PISTON, 8, s));
            inv.setItem(15, kitItem(Material.STICKY_PISTON, 8, s));
            inv.setItem(16, kitItem(Material.SLIME_BLOCK, 16, s));
            inv.setItem(17, kitItem(Material.SAND, 64, s));
            // 2e rangée : blocs de construction en plus
            inv.setItem(18, kitItem(Material.SMOOTH_STONE_SLAB, 64, s));
            inv.setItem(19, kitItem(s.wool, 64, s));
            inv.setItem(20, kitItem(Material.LADDER, 16, s));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 3 * 20, 127));
        }

        /** Recharge : TNT, redstone, pierre, dalles et laine remis à 64. */
        void refill(Player p) {
            Side s = teams.get(p.getUniqueId());
            if (s == null || !mayTouch(p)) return;
            int[] slots = {0, 1, 6, 18, 19}; // mêmes cases que dans equip()
            Material[] mats = {Material.TNT, Material.REDSTONE, Material.COBBLESTONE, Material.SMOOTH_STONE_SLAB, s.wool};
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < mats.length; i++) {
                int have = 0;
                for (ItemStack it : inv.getContents()) if (it != null && it.getType() == mats[i]) have += it.getAmount();
                if (have >= 64) continue;
                ItemStack add = kitItem(mats[i], 64 - have, s);
                ItemStack cur = inv.getItem(slots[i]);
                if (cur == null) inv.setItem(slots[i], add);
                else inv.addItem(add);
            }
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }

        void checkWin() {
            if (state != State.PLAYING) return;
            int r = count(Side.ROUGE), b = count(Side.BLEU);
            if (r == 0 && b == 0) endGame(null, "Plus personne en jeu !");
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
                    eemi("reward " + p.getName() + " tntwars play");
                    if (en.getValue() == winner) eemi("reward " + p.getName() + " tntwars win");
                }
            }
            timer = cfg("end-time", 8);
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
         * Fin de partie : les inscrits retournent au salon d'attente et la map est reconstruite. Une copie sans
         * plus aucun inscrit est détruite au lieu d'être reconstruite.
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
            state = State.WAITING;
            sendSpectatorsToHub();
            reportStatus();
            if (n > 1 && online(players).isEmpty()) {
                destroyGame(this);
                return;
            }
            Bukkit.getScheduler().runTaskLater(EemiTntWars.this, () -> {
                if (!destroyed) rebuild(null);
            }, 20L);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(null, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (!destroyed) state = State.WAITING;
            reportStatus();
        }

        /** Arrêt du plugin / destruction : joueurs et spectateurs au hub. */
        void shutdown() {
            sendSpectatorsToHub();
            for (Player p : online(players)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
            }
            for (UUID id : players) byPlayer.remove(id);
            for (UUID id : pending) byPlayer.remove(id);
            players.clear();
            pending.clear();
        }

        // -------------------------------------------------------------- construction de la map

        /**
         * Vide la zone de jeu puis construit la salle d'attente, les deux camps et le canal.
         * Les blocs sont posés par paquets pour ne pas geler le serveur.
         */
        void rebuild(CommandSender sender) {
            if (world == null || building) return;
            building = true;
            clearEntities();
            placedTnt.clear();
            List<Op> ops = new ArrayList<>();
            int top = cy + cfg("build-height", 25) + 5;
            for (int x = -halfLength - 4; x <= halfLength + 4; x++)
                for (int z = -halfWidth - 4; z <= halfWidth + 4; z++)
                    for (int y = cy - 25; y <= top; y++) ops.add(new Op(x, y, z, null));
            Random r = new Random(7);
            for (Side s : Side.values()) camp(ops, r, s);
            canal(ops);
            lobbyRoom(ops);
            // même map dans toutes les parties : l'ensemble est commun
            mapBlocks.clear();
            for (Op o : ops) if (o.m != null) mapBlocks.add(key(o.x, o.y, o.z));
            apply(ops, 0, sender);
        }

        void apply(List<Op> ops, int from, CommandSender sender) {
            if (destroyed) return;
            int end = Math.min(ops.size(), from + 40000);
            for (int i = from; i < end; i++) {
                Op o = ops.get(i);
                Block b = world.getBlockAt(o.x, o.y, o.z);
                if (o.m == null) {
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                } else if (b.getType() != o.m) b.setType(o.m, false);
            }
            if (end < ops.size()) {
                Bukkit.getScheduler().runTaskLater(EemiTntWars.this, () -> apply(ops, end, sender), 1L);
                return;
            }
            building = false;
            clearEntities();
            getLogger().info("Map TNT Wars construite (" + world.getName() + ").");
            if (sender != null) sender.sendMessage(PREFIX + "§aMap construite.");
            if (remakeModele) {
                remakeModele = false;
                remakeModele();
            }
        }

        /** Retire ce qu'une partie laisse : TNT, objets, blocs qui tombent, projectiles, mobs, XP. */
        void clearEntities() {
            if (world == null) return;
            for (Entity e : world.getEntities()) {
                if (e instanceof Player) continue;
                if (e instanceof TNTPrimed || e instanceof Item || e instanceof Projectile || e instanceof FallingBlock
                        || e instanceof org.bukkit.entity.Mob || e instanceof org.bukkit.entity.ExperienceOrb) e.remove();
            }
        }

        // -------------------------------------------------------------- spectateurs

        /** /tntwars spectate : regarder la partie en cours en spectateur, sans y participer. */
        void spectate(Player p) {
            // au-dessus du canal (même point que les morts)
            p.teleport(aboveCanal(0f));
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

    // ------------------------------------------------------------------ construction (commune)

    /**
     * Un camp : plateau de terre et pierre (4 couches, puis le vide), dessus en pierre lisse pour
     * construire, bordure de béton de l'équipe, zone de réapparition au fond avec un abri.
     */
    private void camp(List<Op> ops, Random r, Side s) {
        int d = s.dir();
        for (int ax = canal + 1; ax <= halfLength; ax++)
            for (int z = -halfWidth; z <= halfWidth; z++) {
                int x = d * ax;
                boolean edge = ax == halfLength || Math.abs(z) == halfWidth;
                ops.add(new Op(x, cy, z, edge ? s.concrete : (r.nextInt(10) == 0 ? Material.STONE : Material.SMOOTH_STONE)));
                ops.add(new Op(x, cy - 1, z, Material.DIRT));
                ops.add(new Op(x, cy - 2, z, Material.STONE));
                ops.add(new Op(x, cy - 3, z, r.nextInt(6) == 0 ? Material.ANDESITE : Material.STONE));
                if (edge) ops.add(new Op(x, cy + 1, z, s.concrete));
            }
        // zone de réapparition : dalle de laine 7x7 au fond, poteaux et lanternes
        int sx = d * (halfLength - 5);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) ops.add(new Op(sx + dx, cy, dz, s.wool));
        for (int px = -3; px <= 3; px += 6)
            for (int pz = -3; pz <= 3; pz += 6) {
                ops.add(new Op(sx + px, cy + 1, pz, Material.STONE_BRICK_WALL));
                ops.add(new Op(sx + px, cy + 2, pz, Material.LANTERN));
            }
    }

    /** Canal d'eau infranchissable entre les camps : fond en pierre, 4 blocs d'eau. */
    private void canal(List<Op> ops) {
        for (int x = -canal; x <= canal; x++)
            for (int z = -halfWidth; z <= halfWidth; z++) {
                ops.add(new Op(x, cy - 4, z, Material.STONE));
                boolean bank = Math.abs(z) == halfWidth;
                for (int y = cy - 3; y <= cy - 1; y++) ops.add(new Op(x, y, z, bank ? Material.STONE_BRICKS : Material.WATER));
                ops.add(new Op(x, cy, z, bank ? Material.STONE_BRICKS : Material.WATER));
            }
    }

    /** Salle d'attente : plancher sombre bordé de TNT, murs invisibles, lanternes. */
    private void lobbyRoom(List<Op> ops) {
        int y = cfg("lobby-y", 140);
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                ops.add(new Op(x, y, z, edge ? Material.TNT : ((x + z) % 2 == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS)));
                for (int dy = 1; dy <= 4; dy++) ops.add(new Op(x, y + dy, z, edge ? Material.BARRIER : null));
            }
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                ops.add(new Op(sx * 6, y + 1, sz * 6, Material.POLISHED_BLACKSTONE));
                ops.add(new Op(sx * 6, y + 2, sz * 6, Material.LANTERN));
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
        // explosions, blocs cassés : rien ne tombe au sol (le matériel est rechargé)
        if (isProtected(e.getLocation().getWorld())) e.setCancelled(true);
    }

    /** TNT allumée : on lui attache l'équipe et le joueur qui l'a posée (pas de tir ami, kills). */
    @EventHandler
    public void onTntSpawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tnt)) return;
        Game g = gameOf(tnt.getWorld());
        if (g == null) return;
        Location l = tnt.getLocation();
        UUID owner = g.placedTnt.remove(key(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        if (owner == null) return;
        Side s = g.teams.get(owner);
        if (s == null) return;
        tnt.getPersistentDataContainer().set(tntTeamKey, PersistentDataType.STRING, s.key());
        tnt.getPersistentDataContainer().set(tntOwnerKey, PersistentDataType.STRING, owner.toString());
        Player p = Bukkit.getPlayer(owner);
        if (p != null) tnt.setSource(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = playing(victim);
        if (g == null) return;
        UUID id = victim.getUniqueId();
        if (g.state != State.PLAYING || !g.alive.contains(id) || victim.getGameMode() != GameMode.SURVIVAL) {
            e.setCancelled(true);
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent by)) return;
        Side victimSide = g.teams.get(id);
        UUID attacker = null;
        if (by.getDamager() instanceof TNTPrimed tnt) {
            String team = tnt.getPersistentDataContainer().get(tntTeamKey, PersistentDataType.STRING);
            String owner = tnt.getPersistentDataContainer().get(tntOwnerKey, PersistentDataType.STRING);
            // la TNT de son équipe ne blesse pas
            if (team != null && team.equals(victimSide.key())) {
                e.setCancelled(true);
                return;
            }
            if (owner != null) attacker = UUID.fromString(owner);
        } else if (by.getDamager() instanceof Player pl) {
            attacker = pl.getUniqueId();
        } else if (by.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl) {
            attacker = pl.getUniqueId();
        }
        if (attacker == null) return;
        if (g.teams.get(attacker) == victimSide) {
            e.setCancelled(true);
            return;
        }
        g.lastHit.put(id, attacker);
        g.lastHitTime.put(id, System.currentTimeMillis());
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
        Long t = g.lastHitTime.get(p.getUniqueId());
        Player killer = t != null && System.currentTimeMillis() - t < 10000 ? Bukkit.getPlayer(g.lastHit.get(p.getUniqueId())) : null;
        if (killer != null && g.teams.containsKey(killer.getUniqueId()) && killer != p) {
            g.kills.merge(killer.getUniqueId(), 1, Integer::sum);
            if (g.state == State.PLAYING) eemi("reward " + killer.getName() + " tntwars kill");
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId())) {
            int left = g.lives.getOrDefault(s, 0);
            String who = killer != null && killer != p && g.teams.containsKey(killer.getUniqueId())
                    ? g.teams.get(killer.getUniqueId()).code() + killer.getName() + " §7a eu " + s.code() + p.getName()
                    : s.code() + p.getName() + " §7est mort";
            if (left > 0) {
                g.lives.put(s, left - 1);
                g.broadcast(who + " §8(" + s.code() + s.label + " : " + (left - 1) + " vies§8)");
            } else {
                g.alive.remove(p.getUniqueId());
                g.broadcast(who + " §7et est §céliminé§7.");
                p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Ton équipe n'a plus de vies", NamedTextColor.GRAY)));
                g.checkWin();
                reportStatus();
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && p.isDead()) p.spigot().respawn();
        }, 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = playing(p);
        if (g == null || g.lobby == null) return;
        Side s = g.teams.get(p.getUniqueId());
        if (s != null && (g.state == State.PLAYING || g.state == State.ENDING)) {
            // réapparition en spectateur au-dessus du terrain, puis retour au camp après le délai
            e.setRespawnLocation(g.aboveCanal(s == Side.ROUGE ? -90 : 90));
            later(() -> { if (p.isOnline() && mayTouch(p)) resetPlayer(p, GameMode.SPECTATOR); });
            if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId())) {
                int delay = cfg("respawn-delay", 5);
                p.showTitle(Title.title(Component.text("Mort", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                        Component.text("Retour au camp dans " + delay + " s", NamedTextColor.GRAY)));
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!p.isOnline() || g.destroyed || g.state != State.PLAYING || !g.alive.contains(p.getUniqueId())) return;
                    p.teleport(g.spawn(s));
                    later(() -> g.equip(p));
                }, 20L * delay);
            }
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
        Side s = g.teams.get(p.getUniqueId());
        if (g.state == State.PLAYING && g.alive.contains(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL && s != null) {
            if (to.getY() < voidY) {
                p.setHealth(0);
                return;
            }
            // le canal ne se traverse pas : on reste dans son camp (le milieu du canal compte comme frontière)
            if (to.getX() * s.dir() < 1.5 || Math.abs(to.getX()) > halfLength + 3 || Math.abs(to.getZ()) > halfWidth + 3) {
                e.setTo(e.getFrom().setDirection(to.getDirection()));
                p.sendActionBar(Component.text("Tu ne peux pas quitter ton camp !", NamedTextColor.RED));
            }
            return;
        }
        // salle d'attente, spectateurs : on ne tombe pas dans le vide
        if (to.getY() < voidY - 20 && g.lobby != null)
            p.teleport(g.state == State.WAITING || g.state == State.STARTING || s == null ? g.lobby : g.aboveCanal(0f));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        if (!canEdit(e.getPlayer(), b)) {
            e.setCancelled(true);
            return;
        }
        Game g = gameOf(b.getWorld());
        if (g != null && b.getType() == Material.TNT)
            g.placedTnt.put(key(b.getX(), b.getY(), b.getZ()), e.getPlayer().getUniqueId());
    }

    /** Le sol et le décor de la map résistent aux explosions : seuls les blocs posés par les joueurs sautent. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (isProtected(e.getLocation().getWorld()))
            e.blockList().removeIf(b -> mapBlocks.contains(key(b.getX(), b.getY(), b.getZ())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (isProtected(e.getBlock().getWorld()))
            e.blockList().removeIf(b -> mapBlocks.contains(key(b.getX(), b.getY(), b.getZ())));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        boolean admin = e.getPlayer().getGameMode() == GameMode.CREATIVE && e.getPlayer().hasPermission("eemitntwars.admin");
        boolean ours = isProtected(b.getWorld());
        if (ours && !admin && mapBlocks.contains(key(b.getX(), b.getY(), b.getZ()))) {
            e.setCancelled(true);
            return;
        }
        if (!canEdit(e.getPlayer(), b)) e.setCancelled(true);
        else if (ours) {
            // seulement dans les mondes de TNT Wars : ailleurs (EEMINGO, Balls of Steel...) les blocs doivent tomber
            e.setDropItems(false);
            Game g = gameOf(b.getWorld());
            if (g != null) g.placedTnt.remove(key(b.getX(), b.getY(), b.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (!canEdit(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        else if (playing(e.getPlayer()) != null) {
            // seau infini : il reste plein
            Bukkit.getScheduler().runTask(this, () -> {
                if (!mayTouch(e.getPlayer())) return;
                ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
                if (hand != null && hand.getType() == Material.BUCKET) e.getPlayer().getInventory().setItem(e.getHand(), kitItem(Material.WATER_BUCKET, 1, null));
            });
        }
    }

    /** Construction libre dans son propre camp pendant la partie. Admins en créatif : libres. */
    private boolean canEdit(Player p, Block b) {
        if (!isProtected(b.getWorld())) return true;
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemitntwars.admin")) return true;
        Game g = gameOf(b.getWorld());
        if (g == null || playing(p) != g) return false;
        if (g.state != State.PLAYING || !g.alive.contains(p.getUniqueId())) return false;
        if (!inArea(b.getX(), b.getY(), b.getZ())) return false;
        return sideOf(b.getX()) == g.teams.get(p.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = playing(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté TNT Wars.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (playing(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && playing(p) != null) e.setCancelled(true);
    }

    /** Un inscrit qui quitte le monde de sa partie (/lobby, /mv tp...) quitte la partie, au lieu d'y être renvoyé. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g = playing(e.getPlayer());
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !e.getPlayer().getWorld().equals(g.world)) g.leave(e.getPlayer(), false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, g.players.contains(p.getUniqueId()));
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
        eemi("status tntwars " + st + " " + waiting + " " + inGame + " " + cfg("max-players", 16) * maxInstances());
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
        boolean admin = sender.hasPermission("eemitntwars.admin");
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
                p.sendMessage(PREFIX + "§7Tu as quitté TNT Wars.");
            }
            case "spectate", "spec" -> {
                if (p == null) return true;
                if (byPlayer.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§cTu es inscrit : /tntwars leave d'abord."); return true; }
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
                            + " §7- " + g.stateLabel() + " - joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §7(+" + g.pending.size() + " en attente)")
                            + (g.state == State.PLAYING ? " §7- en jeu §f" + g.alive.size() : ""));
                sender.sendMessage("§7 Modèle : §f" + modeleName() + " §7- " + (modeleBusy ? "en copie" : modele == null ? "absent" : "pret"));
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
                g.rebuild(sender);
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games)
                    if (g.state == State.PLAYING || g.state == State.ENDING) { sender.sendMessage(PREFIX + "§cAttends la fin des parties en cours."); return true; }
                String oldBase = base;
                loadSettings();
                base = oldBase; // le monde des parties ne change qu'au redémarrage
                for (Game g : games) if (g.world != null) g.attach(g.world);
                Game first = game(1);
                if (first != null && first.world == null) first.attach(Bukkit.getWorld(base));
                sender.sendMessage(PREFIX + "§aConfig rechargée.");
            }
            default -> sender.sendMessage(PREFIX + "§7/tntwars <join|leave|spectate [joueur]>" + (admin ? " §8- admin : start|stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ utilitaires

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
