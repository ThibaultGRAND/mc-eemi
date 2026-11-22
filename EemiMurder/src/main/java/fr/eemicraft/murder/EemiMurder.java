package fr.eemicraft.murder;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Murder Mystery d'EEMICRAFT.
 * - salon d'attente COMMUN (salon du monde murder) pour toutes les parties ; chaque partie a ses inscrits,
 *   son décompte et son scoreboard (les joueurs de parties différentes se croisent au salon) ;
 * - plusieurs parties en même temps (max-instances, 3 par défaut) : la partie 1 joue sur le monde de la map
 *   tirée (ex. mm_carnival), la partie n ≥ 2 sur une copie « mm_carnival_n » faite depuis « mm_carnival_modele »
 *   au début du décompte, détruite à la fin de la partie ;
 * - un meurtrier (épée), un détective (arc), des innocents qui ramassent de l'or (10 pièces = un arc) ;
 * - à la fin, les joueurs reviennent au salon pour la partie suivante (même numéro de partie).
 */
public final class EemiMurder extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§dMurder§8] §7";
    private static final String TEAM = "eemimurder";
    /** Délai entre la destruction d'une copie et la création d'une nouvelle copie du même nom (suppression asynchrone). */
    private static final long RECREATE_DELAY_MS = 10_000L;

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Role {
        MURDERER("Meurtrier", NamedTextColor.RED),
        DETECTIVE("Détective", NamedTextColor.AQUA),
        INNOCENT("Innocent", NamedTextColor.GREEN);

        final String label;
        final NamedTextColor color;

        Role(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }
    }

    private NamespacedKey toolKey;

    private final Map<String, GameMap> maps = new LinkedHashMap<>();
    private Location lobby;

    /** Parties par numéro (la partie 1 existe toujours). */
    private final TreeMap<Integer, Game> games = new TreeMap<>();
    /** Partie de chaque inscrit (salon d'attente compris). */
    private final Map<UUID, Game> byPlayer = new HashMap<>();
    /** Partie regardée par chaque spectateur non inscrit. */
    private final Map<UUID, Game> bySpectator = new HashMap<>();
    /** Copies détruites récemment (nom → heure) : on attend la fin de la suppression avant d'en refaire une. */
    private final Map<String, Long> destroyedAt = new HashMap<>();
    /** Mondes de map dont le modèle est en cours de copie. */
    private final Set<String> modelBusy = new HashSet<>();
    /** Copies en cours depuis le modèle de chaque monde de map (le modèle ne doit pas être refait pendant ce temps). */
    private final Map<String, Integer> pendingCopies = new HashMap<>();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        toolKey = new NamespacedKey(this, "tool");
        saveDefaultConfig();
        loadSettings();
        games.put(1, new Game(1));
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        // copies restées sur le disque, puis modèles propres (mondes chargés par Multiverse avant nous)
        Bukkit.getScheduler().runTask(this, () -> {
            for (String base : mapWorlds()) {
                World w = Bukkit.getWorld(base);
                if (w == null) continue;
                WorldClone.cleanup(this, w, base, maxInstances());
                refreshModel(base);
            }
        });
        reportStatus();
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) g.shutdown();
    }

    private void loadSettings() {
        reloadConfig();
        maps.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("maps");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection m = sec.getConfigurationSection(id);
                if (m != null) maps.put(id, GameMap.load(id, m));
            }
        }
        lobby = parseLocation(getConfig().getString("lobby", ""));
        getLogger().info(maps.size() + " map(s) chargee(s), lobby " + (lobby == null ? "non défini" : "ok") + ".");
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private int maxPlayers() {
        return cfg("max-players", 16);
    }

    /** Délai entre deux kills du meurtrier (clé kill-cooldown, en secondes, 3 par défaut). */
    private long knifeCooldownMs() {
        return Math.max(0L, Math.round(getConfig().getDouble("kill-cooldown", 3.0) * 1000));
    }

    private static String formatSeconds(long ms) {
        return String.format(java.util.Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    // ------------------------------------------------------------------ mondes : modèles et copies

    /** Mondes d'origine des maps configurées (ex. mm_carnival, mm_museum). */
    private Set<String> mapWorlds() {
        Set<String> s = new LinkedHashSet<>();
        for (GameMap m : maps.values()) s.add(m.worldName);
        return s;
    }

    private static String modelName(String base) {
        return base + "_modele";
    }

    /**
     * (Re)fait le modèle « base_modele » depuis le monde d'origine. Refusé si une copie en est en cours
     * ou si une partie joue sur le monde d'origine (les objets de la partie seraient copiés).
     */
    private boolean refreshModel(String base) {
        World template = Bukkit.getWorld(base);
        if (template == null) return false;
        if (modelBusy.contains(base) || pendingCopies.getOrDefault(base, 0) > 0) return false;
        for (Game g : games.values()) {
            if (g.world != null && g.world.equals(template) && (g.state == State.PLAYING || g.state == State.ENDING)) return false;
        }
        World old = Bukkit.getWorld(modelName(base));
        if (old != null && !Bukkit.unloadWorld(old, false)) {
            getLogger().warning("Impossible de décharger " + old.getName() + " : modèle non refait.");
            return false;
        }
        modelBusy.add(base);
        WorldClone.create(this, template, modelName(base), w -> {
            modelBusy.remove(base);
            if (w == null) getLogger().warning("Modèle " + modelName(base) + "non créé : pas de partie 2+ sur cette map.");
        });
        return true;
    }

    /** Modèles des mondes de map qui n'en ont pas encore (après un reload qui ajoute une map). */
    private void ensureModels() {
        for (String base : mapWorlds()) {
            if (Bukkit.getWorld(base) != null && Bukkit.getWorld(modelName(base)) == null && !modelBusy.contains(base)) {
                WorldClone.cleanup(this, Bukkit.getWorld(base), base, maxInstances());
                refreshModel(base);
            }
        }
    }

    /**
     * Copie « base_n » depuis le modèle ; attend que le modèle soit prêt et que l'ancienne copie du même
     * nom ait fini d'être supprimée. done reçoit le monde (ou null) sur le thread principal.
     */
    private void requestCopy(String base, int n, Consumer<World> done) {
        String name = WorldClone.name(base, n);
        World model = Bukkit.getWorld(modelName(base));
        long since = System.currentTimeMillis() - destroyedAt.getOrDefault(name, 0L);
        if (model == null || modelBusy.contains(base) || since < RECREATE_DELAY_MS) {
            if (model == null && !modelBusy.contains(base) && !refreshModel(base)) {
                done.accept(null);
                return;
            }
            Bukkit.getScheduler().runTaskLater(this, () -> requestCopy(base, n, done), 40L);
            return;
        }
        pendingCopies.merge(base, 1, Integer::sum);
        WorldClone.create(this, model, name, w -> {
            pendingCopies.merge(base, -1, Integer::sum);
            done.accept(w);
        });
    }

    private void destroyCopy(World w, String base) {
        if (w == null || w.getName().equals(base)) return;
        destroyedAt.put(w.getName(), System.currentTimeMillis());
        WorldClone.destroy(this, w, base, this::sendToHub);
    }

    // ------------------------------------------------------------------ boucle (1x / seconde)

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) g.tick();
    }

    /** Statut agrégé pour EemiCore : un seul envoi pour toutes les parties. */
    private void reportStatus() {
        boolean anyWaiting = false, anyPlaying = false;
        long waiting = 0;
        int playing = 0;
        for (Game g : games.values()) {
            if (g.state == State.WAITING || g.state == State.STARTING) anyWaiting = true;
            else anyPlaying = true;
            waiting += g.players.stream().filter(u -> !g.inGame.contains(u)).count();
            if (g.state == State.PLAYING || g.state == State.ENDING) playing += g.alive.size();
        }
        String st = anyWaiting ? "waiting" : anyPlaying ? "playing" : "waiting";
        eemi("status murder " + st + " " + waiting + " " + playing + " " + (maxPlayers() * maxInstances()));
    }

    // ------------------------------------------------------------------ inscription (choix de la partie)

    private void join(Player p) {
        if (byPlayer.containsKey(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§7Tu es déjà inscrit.");
            return;
        }
        if (lobby == null || lobby.getWorld() == null) {
            p.sendMessage(PREFIX + "§cLe lobby d'attente n'est pas configuré.");
            return;
        }
        int max = maxPlayers();
        // partie en attente ou en décompte avec de la place, la plus remplie d'abord (garde les groupes ensemble)
        Game best = games.values().stream()
                .filter(g -> (g.state == State.WAITING || g.state == State.STARTING) && g.players.size() < max)
                .max(Comparator.<Game>comparingInt(g -> g.players.size()).thenComparing(g -> -g.n))
                .orElse(null);
        if (best == null && games.size() < maxInstances()) {
            int n = 2;
            while (games.containsKey(n)) n++;
            best = new Game(n);
            games.put(n, best);
            getLogger().info("Nouvelle partie de Murder : " + n + ".");
        }
        if (best == null) {
            // toutes les parties jouent : file d'attente de la partie suivante, comme avant
            best = games.values().stream().filter(g -> g.players.size() < max)
                    .min(Comparator.comparingInt(g -> g.players.size())).orElse(null);
        }
        if (best == null) {
            p.sendMessage(PREFIX + "§cToutes les parties sont pleines.");
            return;
        }
        best.join(p);
    }

    private void removeGame(Game g) {
        if (g.n == 1) return;
        g.releaseWorld();
        games.remove(g.n);
        getLogger().info("Partie de Murder " + g.n + " fermée.");
        reportStatus();
    }

    /**
     * Le plugin ne touche au joueur (inventaire, armure, XP, vie, faim, effets, mode de jeu) que s'il est
     * dans un monde du Murder (salon d'attente, maps, leurs copies et modèles) ou au hub : ailleurs (skyblock...)
     * c'est l'inventaire de CE monde que Multiverse-Inventories sauvegarderait.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        String name = w.getName();
        if (name.equals(getConfig().getString("hub-world", "lobby"))) return true;
        if (lobby != null && w.equals(lobby.getWorld())) return true;
        for (String b : mapWorlds())
            if (WorldClone.index(b, name) > 0 || name.equals(modelName(b))) return true;
        return false;
    }

    /**
     * Renvoi au hub. Joueur dans un monde du Murder : réinitialisé dans ce monde puis téléporté (MV-Inv sauvegarde
     * l'inventaire du jeu et recharge celui du hub, objets du hub compris). Déjà au hub : réinitialisé sur place.
     * Ailleurs (skyblock...) : ni téléporté ni réinitialisé.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
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
        p.setGlowing(false);
    }

    // ================================================================== une partie

    final class Game {
        final int n;
        /** Équipe qui cache les pseudos (nom propre à la partie). */
        final String teamName;

        /** Tous les joueurs inscrits (salon d'attente + partie en cours). */
        final Set<UUID> players = new LinkedHashSet<>();
        /** Joueurs de la partie en cours (vivants ou spectateurs). */
        final Set<UUID> inGame = new LinkedHashSet<>();
        final Set<UUID> alive = new HashSet<>();
        final Map<UUID, Role> roles = new HashMap<>();
        final Map<UUID, Integer> gold = new HashMap<>();
        final Map<UUID, Long> lastShot = new HashMap<>();
        /** Heure (ms) du dernier kill au couteau du meurtrier : délai anti-spam entre deux kills. */
        long lastKnifeKill;
        final Set<Entity> spawned = new HashSet<>();
        final Set<Scoreboard> boards = new HashSet<>();
        /** Scoreboard latéral de chaque inscrit (porte aussi l'équipe qui cache les pseudos). */
        final Map<UUID, Scoreboard> sidebars = new HashMap<>();
        /** Spectateurs non inscrits de la partie en cours (/murder spectate). */
        final Set<UUID> spectators = new HashSet<>();

        State state = State.WAITING;
        /** Map tirée pour la prochaine partie (config d'origine), et son monde (origine ou copie) une fois prêt. */
        GameMap chosen;
        World world;
        boolean preparing;
        int prepToken;
        boolean removed;
        /** Lancement forcé par un admin (start) : pas de minimum de joueurs pendant la préparation. */
        boolean forced;
        /** Après un échec de copie : pas de nouvel essai avant cette heure (ms). */
        long nextPrepare;
        /** Map de la partie en cours, liée au monde de la partie. */
        GameMap current;
        String lastMapId;
        int timer;
        int elapsed;
        UUID murderer;
        UUID detective;
        BossBar bar;

        Game(int n) {
            this.n = n;
            this.teamName = n == 1 ? TEAM : TEAM + "_" + n;
        }

        String label() {
            return "Partie " + n;
        }

        long knifeCooldownLeft() {
            if (lastKnifeKill == 0) return 0;
            return Math.max(0L, lastKnifeKill + knifeCooldownMs() - System.currentTimeMillis());
        }

        /** Mondes de la partie : le salon (commun) et le monde de la map. */
        boolean isOwnWorld(World w) {
            if (w == null) return false;
            if (lobby != null && w.equals(lobby.getWorld())) return true;
            return world != null && w.equals(world);
        }

        // -------------------------------------------------------------- boucle

        void tick() {
            for (Player p : online(players)) updateSidebar(p);
            switch (state) {
                case WAITING -> {
                    if (players.size() >= cfg("min-players", 3)) {
                        state = State.STARTING;
                        timer = cfg("countdown", 30);
                        broadcast("§eAssez de joueurs : la partie commence dans §6" + timer + " s§e.");
                        if (chosen == null) prepareMap();
                        reportStatus();
                    }
                    for (Player p : online(players)) p.sendActionBar(Component.text(
                            "En attente de joueurs (" + players.size() + "/" + cfg("min-players", 3) + ")", NamedTextColor.YELLOW));
                }
                case STARTING -> {
                    if (players.size() < (forced ? 2 : cfg("min-players", 3))) {
                        state = State.WAITING;
                        forced = false;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                        reportStatus();
                        return;
                    }
                    if (players.size() >= maxPlayers()) timer = Math.min(timer, cfg("full-countdown", 10));
                    if (timer <= 0 && world == null) {
                        // la copie de la map n'est pas encore prête
                        if (chosen == null && !preparing && System.currentTimeMillis() >= nextPrepare) prepareMap();
                        for (Player p : online(players)) p.sendActionBar(Component.text("Préparation de la map...", NamedTextColor.GOLD));
                        return;
                    }
                    for (Player p : online(players)) {
                        if (mayTouch(p)) p.setLevel(timer);
                        p.sendActionBar(Component.text("Début dans " + timer + " s", NamedTextColor.GOLD));
                    }
                    if (timer <= 5 && timer > 0) {
                        for (Player p : online(players)) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    }
                    if (timer <= 0) startGame();
                    else timer--;
                }
                case PLAYING -> playingTick();
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
        }

        void playingTick() {
            elapsed++;
            timer--;
            int total = cfg("game-time", 270);
            if (bar != null) {
                bar.progress(Math.max(0f, Math.min(1f, timer / (float) total)));
                bar.name(Component.text(current.name + "  -  " + formatTime(timer), NamedTextColor.WHITE));
            }
            if (elapsed == cfg("sword-delay", 10)) {
                Player m = Bukkit.getPlayer(murderer);
                if (m != null && alive.contains(murderer)) {
                    m.getInventory().setItem(1, tool(Material.IRON_SWORD, "Couteau du meurtrier", NamedTextColor.RED));
                    m.sendMessage(PREFIX + "§cTu as ton couteau. Élimine tout le monde !");
                    m.playSound(m.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
                }
            }
            if (elapsed % Math.max(1, cfg("gold-interval", 3)) == 0) spawnGold();
            refillArrows();
            for (Player p : online(alive)) {
                Role r = roles.get(p.getUniqueId());
                Component ab = Component.text(r.label, r.color).decorate(TextDecoration.BOLD)
                        .append(Component.text("   Or : " + gold.getOrDefault(p.getUniqueId(), 0) + "/" + cfg("gold-for-bow", 10), NamedTextColor.GOLD));
                long left = r == Role.MURDERER ? knifeCooldownLeft() : 0;
                if (left > 0) ab = ab.append(Component.text("   Couteau : " + formatSeconds(left), NamedTextColor.RED));
                p.sendActionBar(ab);
            }
            if (timer <= 0) endGame(false, "Le temps est écoulé !");
        }

        // -------------------------------------------------------------- map et monde de la partie

        /** Tire la map de la prochaine partie ; partie 1 = monde d'origine, partie n = copie « monde_n » du modèle. */
        void prepareMap() {
            List<GameMap> ready = new ArrayList<>();
            for (GameMap m : maps.values()) if (m.isReady()) ready.add(m);
            if (ready.isEmpty()) {
                broadcast("§cAucune map prête.");
                return;
            }
            if (n != 1) {
                // une partie n ≥ 2 a besoin du modèle de la map : on préfère les maps dont le modèle existe
                List<GameMap> withModel = new ArrayList<>(ready);
                withModel.removeIf(m -> Bukkit.getWorld(modelName(m.worldName)) == null && !modelBusy.contains(m.worldName));
                if (!withModel.isEmpty()) ready = withModel;
            }
            if (ready.size() > 1) ready.removeIf(m -> m.id.equals(lastMapId));
            GameMap m = ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
            chosen = m;
            lastMapId = m.id;
            if (n == 1) {
                world = m.world();
                return;
            }
            preparing = true;
            int token = ++prepToken;
            requestCopy(m.worldName, n, w -> {
                if (removed || token != prepToken) {
                    // partie fermée ou map changée entre-temps : la copie ne sert plus
                    if (w != null && !isUsedByAnotherGame(w)) destroyCopy(w, m.worldName);
                    return;
                }
                preparing = false;
                if (w == null) {
                    chosen = null;
                    nextPrepare = System.currentTimeMillis() + 10_000L;
                    broadcast("§cPréparation de la map impossible, nouvel essai dans 10 s...");
                    return;
                }
                world = w;
            });
        }

        boolean isUsedByAnotherGame(World w) {
            for (Game g : games.values()) if (g != this && w.equals(g.world)) return true;
            return false;
        }

        /** Rend le monde de la map : la copie (partie n ≥ 2) est détruite, la partie 1 garde son monde d'origine. */
        void releaseWorld() {
            prepToken++;
            preparing = false;
            if (n != 1 && world != null && chosen != null) destroyCopy(world, chosen.worldName);
            world = null;
            chosen = null;
        }

        // -------------------------------------------------------------- inscription

        void join(Player p) {
            bySpectator.remove(p.getUniqueId());
            for (Game g : games.values()) g.spectators.remove(p.getUniqueId());
            players.add(p.getUniqueId());
            byPlayer.put(p.getUniqueId(), this);
            p.teleport(lobby);
            later(() -> prepareLobbyPlayer(p));
            broadcast("§a" + p.getName() + " §7a rejoint le Murder (§f" + players.size() + "§7, partie " + n + ").");
            if (state == State.PLAYING || state == State.ENDING) {
                p.sendMessage(PREFIX + "§7Une partie est en cours, tu joueras à la suivante.");
            }
            reportStatus();
        }

        /** Appliqué un instant après la téléportation, pour passer après Multiverse-Inventories. */
        void prepareLobbyPlayer(Player p) {
            if (!p.isOnline() || !players.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.getInventory().setItem(8, tool(Material.RED_BED, "Quitter le Murder", NamedTextColor.RED));
            updateSidebar(p);
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            byPlayer.remove(id);
            if (state == State.PLAYING && alive.contains(id)) {
                broadcast("§7" + p.getName() + " a quitté la partie.");
                eliminate(p, null, false);
            }
            inGame.remove(id);
            alive.remove(id);
            if (bar != null) p.hideBossBar(bar);
            showName(p);
            sidebars.remove(id);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            if (teleport) sendToHub(p);
            if (state == State.PLAYING) checkWin();
            if (n != 1 && players.isEmpty() && (state == State.WAITING || state == State.STARTING)) {
                state = State.WAITING;
                removed = true;
                removeGame(this);
                return;
            }
            reportStatus();
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard sidebar(Player p) {
            Scoreboard b = sidebars.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                org.bukkit.scoreboard.Objective o = b.registerNewObjective("murder", org.bukkit.scoreboard.Criteria.DUMMY,
                        Component.text("MURDER", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
                o.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
                sidebars.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        /** Une équipe par ligne avec une entrée invisible : pas de clignotement à la mise à jour. */
        void setLines(Player p, List<Component> lines) {
            Scoreboard b = sidebar(p);
            org.bukkit.scoreboard.Objective o = b.getObjective("murder");
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
            UUID id = p.getUniqueId();
            List<Component> l = new ArrayList<>();
            l.add(Component.text(" "));
            if (inGame.contains(id) && (state == State.PLAYING || state == State.ENDING)) {
                Role r = roles.get(id);
                long innocents = alive.stream().filter(u -> roles.get(u) != Role.MURDERER).count();
                l.add(Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(current == null ? "-" : current.name, NamedTextColor.WHITE)));
                l.add(Component.text("Rôle : ", NamedTextColor.GRAY).append(r == null ? Component.text("-")
                        : alive.contains(id) ? Component.text(r.label, r.color) : Component.text("Mort", NamedTextColor.DARK_GRAY)));
                l.add(Component.text("  "));
                l.add(Component.text("Innocents en vie : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(innocents), NamedTextColor.GREEN)));
                l.add(Component.text("Or : ", NamedTextColor.GRAY).append(Component.text(gold.getOrDefault(id, 0) + "/" + cfg("gold-for-bow", 10), NamedTextColor.GOLD)));
                l.add(Component.text("Temps : ", NamedTextColor.GRAY).append(Component.text(formatTime(state == State.PLAYING ? timer : 0), NamedTextColor.WHITE)));
            } else {
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(n), NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + maxPlayers(), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 3)), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.GOLD)));
                else if (state == State.PLAYING || state == State.ENDING) l.add(Component.text("Partie en cours", NamedTextColor.YELLOW));
                else l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                l.add(chosen != null
                        ? Component.text("Map : ", NamedTextColor.GRAY).append(Component.text(chosen.name, NamedTextColor.WHITE))
                        : Component.text("Maps : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(maps.size()), NamedTextColor.WHITE)));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        // -------------------------------------------------------------- partie

        void startGame() {
            if (chosen == null || world == null) {
                // admin start pendant la préparation, ou map indisponible
                if (chosen == null && !preparing) prepareMap();
                if (world == null) {
                    if (state == State.WAITING) { state = State.STARTING; timer = 0; forced = true; }
                    broadcast("§7Préparation de la map, la partie commence dès qu'elle est prête.");
                    reportStatus();
                    return;
                }
            }
            forced = false;
            current = chosen.withWorld(world.getName());

            List<Player> list = online(players);
            if (list.size() < 2) {
                state = State.WAITING;
                reportStatus();
                return;
            }
            Collections.shuffle(list);
            inGame.clear();
            alive.clear();
            roles.clear();
            gold.clear();
            lastShot.clear();
            lastKnifeKill = 0;
            murderer = list.get(0).getUniqueId();
            detective = list.get(1).getUniqueId();

            bar = BossBar.bossBar(Component.text(current.name), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            hideNames(list);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < current.spawns.size(); i++) order.add(i);
            Collections.shuffle(order);

            for (int i = 0; i < list.size(); i++) {
                Player p = list.get(i);
                UUID id = p.getUniqueId();
                Role role = i == 0 ? Role.MURDERER : i == 1 ? Role.DETECTIVE : Role.INNOCENT;
                inGame.add(id);
                alive.add(id);
                roles.put(id, role);
                gold.put(id, 0);
                p.teleport(current.spawn(order.get(i % order.size())));
                p.showBossBar(bar);
                later(() -> equip(p, role));
            }
            state = State.PLAYING;
            timer = cfg("game-time", 270);
            elapsed = 0;
            broadcast("§eLa partie commence sur §6" + current.name + "§e !");
            reportStatus();
        }

        void equip(Player p, Role role) {
            if (!p.isOnline() || !alive.contains(p.getUniqueId()) || !mayTouch(p)) return;
            resetPlayer(p, GameMode.ADVENTURE);
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false));
            String sub = switch (role) {
                case MURDERER -> "Élimine tout le monde, discrètement";
                case DETECTIVE -> "Trouve et abats le meurtrier";
                case INNOCENT -> "Survis et ramasse de l'or";
            };
            p.showTitle(Title.title(Component.text(role.label, role.color).decorate(TextDecoration.BOLD),
                    Component.text(sub, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.2f);
            if (role == Role.DETECTIVE) giveBow(p, true);
        }

        void spawnGold() {
            if (current == null) return;
            long onGround = spawned.stream().filter(e -> e instanceof Item i && i.isValid() && i.getItemStack().getType() == Material.GOLD_INGOT).count();
            if (onGround >= cfg("gold-max", 12)) return;
            Location loc = current.randomGold();
            if (loc == null || loc.getWorld() == null) return;
            Item item = loc.getWorld().dropItem(loc.add(0, 0.2, 0), new ItemStack(Material.GOLD_INGOT));
            item.setVelocity(item.getVelocity().zero());
            item.setUnlimitedLifetime(true);
            spawned.add(item);
        }

        void refillArrows() {
            long cooldown = cfg("arrow-cooldown", 5) * 1000L;
            long now = System.currentTimeMillis();
            for (Player p : online(alive)) {
                if (roles.get(p.getUniqueId()) == Role.MURDERER) continue;
                if (!p.getInventory().contains(Material.BOW) || p.getInventory().contains(Material.ARROW)) continue;
                if (now - lastShot.getOrDefault(p.getUniqueId(), 0L) < cooldown) continue;
                p.getInventory().setItem(9, new ItemStack(Material.ARROW));
            }
        }

        /** Élimine un joueur : il passe spectateur sur la map. killer peut être null (départ, suicide). */
        void eliminate(Player victim, Player killer, boolean announce) {
            UUID id = victim.getUniqueId();
            if (!alive.remove(id)) return;
            Location at = victim.getLocation();
            // parti par changement de monde (/lobby, /is...) : il est déjà dans le monde d'arrivée, dont
            // l'inventaire a été rechargé par MV-Inv : on n'y touche pas et rien n'est lâché là-bas
            boolean here = isOwnWorld(victim.getWorld()) && mayTouch(victim);
            if (here && victim.getInventory().contains(Material.BOW)) {
                ItemStack bow = tool(Material.BOW, "Arc du détective", NamedTextColor.AQUA);
                Item drop = at.getWorld().dropItem(at, bow);
                drop.setGlowing(true);
                drop.setUnlimitedLifetime(true);
                spawned.add(drop);
                if (announce) broadcast("§bL'arc du détective est tombé ! Un innocent peut le ramasser.");
            }
            if (here) {
                victim.getInventory().clear();
                victim.setGameMode(GameMode.SPECTATOR);
                victim.getInventory().setItem(8, tool(Material.RED_BED, "Quitter le Murder", NamedTextColor.RED));
            }
            victim.showTitle(Title.title(Component.text("Tu es mort", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text(killer == null ? "" : "Tué par " + killer.getName(), NamedTextColor.GRAY)));
            at.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            if (announce) {
                long innocents = alive.stream().filter(u -> roles.get(u) != Role.MURDERER).count();
                broadcast("§7Un joueur est mort. §f" + innocents + " §7innocent(s) encore en vie.");
            }
            // récompense de kill : seulement les kills « légitimes » (le meurtrier, ou le tueur du meurtrier)
            if (killer != null && !killer.equals(victim)
                    && (killer.getUniqueId().equals(murderer) || id.equals(murderer))) {
                eemi("reward " + killer.getName() + " murder kill");
            }
            reportStatus();
        }

        void checkWin() {
            if (state != State.PLAYING) return;
            if (!alive.contains(murderer)) {
                endGame(false, "Le meurtrier a été éliminé !");
                return;
            }
            boolean others = alive.stream().anyMatch(u -> !u.equals(murderer));
            if (!others) endGame(true, "Tous les innocents sont morts.");
        }

        void endGame(boolean murdererWins, String reason) {
            endGame(murdererWins, reason, true);
        }

        /** rewards = false pour un arrêt admin : pas de récompenses EemiCore. */
        void endGame(boolean murdererWins, String reason, boolean rewards) {
            state = State.ENDING;
            if (rewards) {
                for (Player p : online(inGame)) {
                    if (!players.contains(p.getUniqueId())) continue;
                    eemi("reward " + p.getName() + " murder play");
                    boolean isMurderer = p.getUniqueId().equals(murderer);
                    if (murdererWins == isMurderer) eemi("reward " + p.getName() + " murder win");
                }
            }
            timer = cfg("end-time", 8);
            String mName = name(murderer), dName = name(detective);
            // titre court (<= 16 caractères), le détail (raison) part dans le chat via broadcast
            NamedTextColor winColor = murdererWins ? NamedTextColor.RED : NamedTextColor.GREEN;
            Component title = Component.text("Victoire !", winColor).decorate(TextDecoration.BOLD);
            Component sub = Component.text(murdererWins ? "Le meurtrier gagne" : "Les innocents gagnent", winColor);
            for (Player p : online(inGame)) {
                p.showTitle(Title.title(title, sub));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                if (bar != null) p.hideBossBar(bar);
            }
            broadcast("§6" + reason + " §7Meurtrier : §c" + mName + "§7, détective : §b" + dName + "§7.");
            clearSpawned();
            reportStatus();
        }

        void backToLobby() {
            removeTeam();
            clearSpawned();
            for (UUID id : inGame) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !players.contains(id)) continue;
                p.teleport(lobby);
                later(() -> prepareLobbyPlayer(p));
            }
            inGame.clear();
            alive.clear();
            roles.clear();
            gold.clear();
            current = null;
            bar = null;
            murderer = null;
            detective = null;
            state = State.WAITING;
            sendSpectatorsToHub();
            // la copie de cette partie est détruite (la prochaine repartira du modèle) ; la partie 1 garde son monde
            releaseWorld();
            if (n != 1 && players.isEmpty()) {
                removed = true;
                removeGame(this);
                return;
            }
            reportStatus();
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(false, "Partie arrêtée par un admin.", false);
            if (state == State.ENDING) backToLobby();
            if (removed) return;
            state = State.WAITING;
            reportStatus();
        }

        void clearSpawned() {
            for (Entity e : spawned) if (e.isValid()) e.remove();
            spawned.clear();
            if (current != null && current.world() != null) {
                for (Entity e : current.world().getEntitiesByClass(AbstractArrow.class)) {
                    if (current.contains(e.getLocation())) e.remove();
                }
            }
        }

        /** Arrêt du plugin : tout le monde au hub. */
        void shutdown() {
            sendSpectatorsToHub();
            for (UUID id : new ArrayList<>(players)) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                sendToHub(p);
            }
            clearSpawned();
            removeTeam();
        }

        // -------------------------------------------------------------- spectateurs

        void spectate(Player p) {
            // centre de la map, à mi-hauteur, regard vers le bas
            Location view = new Location(current.world(), (current.min[0] + current.max[0]) / 2.0 + 0.5,
                    (current.min[1] + current.max[1]) / 2.0 + 2, (current.min[2] + current.max[2]) / 2.0 + 0.5, 0f, 60f);
            Game old = bySpectator.remove(p.getUniqueId());
            if (old != null) old.spectators.remove(p.getUniqueId());
            spectators.add(p.getUniqueId());
            bySpectator.put(p.getUniqueId(), this);
            p.teleport(view);
            if (mayTouch(p)) p.setGameMode(GameMode.SPECTATOR);
            // après Multiverse (mode de jeu imposé au changement de monde)
            later(() -> { if (p.isOnline() && spectators.contains(p.getUniqueId()) && mayTouch(p)) p.setGameMode(GameMode.SPECTATOR); });
            p.sendMessage(PREFIX + "§7Tu regardes la partie " + n + ", /lobby pour revenir.");
        }

        void sendSpectatorsToHub() {
            List<Player> list = online(spectators);
            for (UUID id : spectators) bySpectator.remove(id);
            spectators.clear();
            for (Player s : list) sendToHub(s);
        }

        // -------------------------------------------------------------- pseudos cachés

        /**
         * Pseudos cachés : l'équipe doit exister sur le scoreboard de chaque joueur qui regarde
         * (un plugin de lobby peut leur avoir donné un scoreboard à part).
         */
        void hideNames(List<Player> list) {
            for (Player viewer : list) {
                Scoreboard board = viewer.getScoreboard();
                Team team = board.getTeam(teamName);
                if (team == null) {
                    team = board.registerNewTeam(teamName);
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                }
                for (Player p : list) team.addEntry(p.getName());
                boards.add(board);
            }
        }

        void showName(Player p) {
            for (Scoreboard board : boards) {
                Team team = board.getTeam(teamName);
                if (team != null) team.removeEntry(p.getName());
            }
        }

        void removeTeam() {
            boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
            for (Scoreboard board : boards) {
                Team team = board.getTeam(teamName);
                if (team != null) team.unregister();
            }
            boards.clear();
        }

        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String describe() {
            String w = world != null ? world.getName() : preparing ? "copie en préparation" : "-";
            return "§f" + label() + " §7: monde §f" + w + "§7, " + state.name().toLowerCase()
                    + ", §f" + players.size() + "§7/" + maxPlayers() + " inscrits"
                    + (state == State.PLAYING || state == State.ENDING ? ", " + alive.size() + " en vie" : "")
                    + (chosen != null ? ", map " + chosen.name : "");
        }
    }

    private void giveBow(Player p, boolean detectiveBow) {
        p.getInventory().setItem(1, tool(Material.BOW, detectiveBow ? "Arc du détective" : "Arc", NamedTextColor.AQUA));
        p.getInventory().setItem(9, new ItemStack(Material.ARROW));
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageBy(EntityDamageByEntityEvent e) {
        // aucun dégât entre joueurs inscrits (salon compris) : seules les règles du jeu peuvent tuer
        if (!(e.getEntity() instanceof Player victim)) return;
        Game g = byPlayer.get(victim.getUniqueId());
        if (g == null) return;
        e.setCancelled(true);
        if (g.state != State.PLAYING || !g.inGame.contains(victim.getUniqueId()) || !g.alive.contains(victim.getUniqueId())) return;

        if (e.getDamager() instanceof Player attacker) {
            // couteau du meurtrier au corps à corps
            if (attacker.getUniqueId().equals(g.murderer) && g.alive.contains(g.murderer)
                    && attacker.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD) {
                // délai entre deux kills : pendant le délai le coup ne fait rien (déjà annulé plus haut)
                long left = g.knifeCooldownLeft();
                if (left > 0) {
                    attacker.sendActionBar(Component.text("Couteau prêt dans " + formatSeconds(left), NamedTextColor.RED));
                    return;
                }
                g.lastKnifeKill = System.currentTimeMillis();
                int ticks = (int) Math.round(knifeCooldownMs() / 50.0);
                if (ticks > 0) attacker.setCooldown(Material.IRON_SWORD, ticks); // indication visuelle sur l'épée
                g.eliminate(victim, attacker, true);
                g.checkWin();
            }
            return;
        }
        if (e.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player shooter) {
            arrow.remove();
            if (!g.alive.contains(shooter.getUniqueId()) || shooter.equals(victim)) return;
            g.eliminate(victim, shooter, true);
            // tirer sur un innocent : le tireur meurt aussi
            if (!victim.getUniqueId().equals(g.murderer) && !shooter.getUniqueId().equals(g.murderer)) {
                shooter.sendMessage(PREFIX + "§cTu as tué un innocent !");
                g.eliminate(shooter, null, true);
            }
            g.checkWin();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (e.getEntity() instanceof Player p && byPlayer.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || !g.inGame.contains(p.getUniqueId())) return;
        g.lastShot.put(p.getUniqueId(), System.currentTimeMillis());
        if (e.getProjectile() instanceof AbstractArrow arrow) arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        if (e.getProjectile() instanceof Projectile proj) g.spawned.add(proj);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow) || e.getHitBlock() == null) return;
        for (Game g : games.values()) {
            if (g.spawned.contains(arrow)) {
                arrow.remove();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || !g.inGame.contains(p.getUniqueId())) return;
        Item item = e.getItem();
        e.setCancelled(true);
        if (g.state != State.PLAYING || !g.alive.contains(p.getUniqueId()) || !g.spawned.contains(item)) return;
        Material type = item.getItemStack().getType();
        boolean isMurderer = p.getUniqueId().equals(g.murderer);
        if (type == Material.GOLD_INGOT) {
            item.remove();
            g.spawned.remove(item);
            int count = g.gold.merge(p.getUniqueId(), 1, Integer::sum);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            int needed = cfg("gold-for-bow", 10);
            if (count >= needed && !isMurderer) {
                g.gold.put(p.getUniqueId(), count - needed);
                if (!p.getInventory().contains(Material.BOW)) {
                    giveBow(p, false);
                    p.sendMessage(PREFIX + "§6" + needed + " pièces d'or : tu obtiens un arc !");
                } else {
                    p.getInventory().setItem(9, new ItemStack(Material.ARROW));
                    p.sendMessage(PREFIX + "§6" + needed + " pièces d'or : une flèche de plus !");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
            int shown = g.gold.get(p.getUniqueId());
            p.getInventory().setItem(8, shown > 0 ? new ItemStack(Material.GOLD_INGOT, shown) : null);
        } else if (type == Material.BOW && !isMurderer && !p.getInventory().contains(Material.BOW)) {
            item.remove();
            g.spawned.remove(item);
            giveBow(p, true);
            g.broadcast("§bQuelqu'un a ramassé l'arc du détective !");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.current == null) return;
        if (!g.inGame.contains(p.getUniqueId())) return;
        Location to = e.getTo();
        if (to.getBlockX() == e.getFrom().getBlockX() && to.getBlockY() == e.getFrom().getBlockY()
                && to.getBlockZ() == e.getFrom().getBlockZ()) return;
        if (g.alive.contains(p.getUniqueId()) && g.current.isBelowFloor(to)) {
            p.sendMessage(PREFIX + "§cTu es tombé dans un piège !");
            g.eliminate(p, null, true);
            g.checkWin();
            return;
        }
        if (g.current.contains(to)) return;
        if (g.current.contains(e.getFrom())) e.setTo(e.getFrom());
        else p.teleport(g.current.randomSpawn());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        // spectateurs : pas de téléportation vers un autre joueur hors de la map
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || g.state != State.PLAYING || g.current == null) return;
        if (!g.inGame.contains(p.getUniqueId()) || e.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        if (!g.current.contains(e.getTo())) e.setCancelled(true);
    }

    /**
     * Salon d'attente (monde du lobby, commun à toutes les parties) : hors créatif, on ne peut ouvrir
     * ni portes, ni trappes, ni portillons, ni actionner boutons, leviers ou plaques de pression
     * (WorldGuard du monde murder ne refuse pas `use`). Seul le bloc cliqué est refusé : l'objet tenu
     * reste géré normalement (le lit « Quitter » est traité par onInteract, qui suit).
     * Les zones de map configurées dans ce monde (anciennes maps) et les mondes des maps ne sont pas touchés.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onLobbyBlockUse(PlayerInteractEvent e) {
        if (lobby == null || lobby.getWorld() == null) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        org.bukkit.block.Block b = e.getClickedBlock();
        if (b == null || !b.getWorld().equals(lobby.getWorld())) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.PHYSICAL) return;
        if (!isPassageBlock(b.getType())) return;
        Location loc = b.getLocation();
        for (GameMap m : maps.values()) if (m.contains(loc)) return;
        e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    private static boolean isPassageBlock(Material t) {
        return org.bukkit.Tag.DOORS.isTagged(t) || org.bukkit.Tag.TRAPDOORS.isTagged(t)
                || org.bukkit.Tag.FENCE_GATES.isTagged(t) || org.bukkit.Tag.BUTTONS.isTagged(t)
                || org.bukkit.Tag.PRESSURE_PLATES.isTagged(t) || t == Material.LEVER;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isTool(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté le Murder.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (byPlayer.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (byPlayer.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && byPlayer.containsKey(p.getUniqueId())
                && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && byPlayer.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    /** Un inscrit qui quitte les mondes de SA partie (salon, map de la partie) quitte la partie. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null && !g.isOwnWorld(p.getWorld())) g.leave(p, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game g = byPlayer.get(p.getUniqueId());
        if (g != null) g.leave(p, true);
    }

    /** Un spectateur qui quitte le monde de la partie regardée (/lobby...) redevient un joueur normal. */
    @EventHandler
    public void onSpectatorWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Game g = bySpectator.get(p.getUniqueId());
        if (g == null) return;
        if (g.current != null && p.getWorld().getName().equals(g.current.worldName)) return;
        bySpectator.remove(p.getUniqueId());
        g.spectators.remove(p.getUniqueId());
        // seulement s'il arrive au hub : ailleurs le mode est celui du monde (Multiverse)
        if (mayTouch(p)) p.setGameMode(GameMode.SURVIVAL);
    }

    @EventHandler
    public void onSpectatorQuit(PlayerQuitEvent e) {
        Game g = bySpectator.remove(e.getPlayer().getUniqueId());
        if (g != null) {
            g.spectators.remove(e.getPlayer().getUniqueId());
            sendToHub(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ EemiCore et spectateurs

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /** /murder spectate [joueur] : la partie de ce joueur, sinon la partie en cours la plus remplie. */
    private void spectate(Player p, String targetName) {
        if (byPlayer.containsKey(p.getUniqueId())) { p.sendMessage(PREFIX + "§cTu es inscrit : /murder leave d'abord."); return; }
        Game g;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : byPlayer.get(t.getUniqueId());
            if (g == null) { p.sendMessage(PREFIX + "§c" + targetName + " n'est pas dans une partie de Murder."); return; }
        } else {
            g = games.values().stream().filter(x -> x.state == State.PLAYING)
                    .max(Comparator.comparingInt(x -> x.alive.size())).orElse(null);
        }
        if (g == null || g.state != State.PLAYING || g.current == null || g.current.world() == null) {
            p.sendMessage(PREFIX + "§cAucune partie en cours.");
            return;
        }
        g.spectate(p);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemimurder.admin");
        switch (sub) {
            case "help", "aide", "?" -> sendHelp(sender, admin);
            case "join" -> {
                Player target = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (target == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(target);
            }
            case "leave" -> {
                if (p == null) return true;
                Game g = byPlayer.get(p.getUniqueId());
                if (g == null) { p.sendMessage(PREFIX + "§7Tu n'es pas inscrit."); return true; }
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté le Murder.");
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "maps" -> {
                for (GameMap m : maps.values()) sender.sendMessage(PREFIX + "§f" + m.id + " §7(" + m.name + ") : "
                        + m.spawns.size() + " apparitions, " + m.gold.size() + " points d'or, "
                        + (m.isReady() ? "§aprête" : "§cincomplète")
                        + (Bukkit.getWorld(modelName(m.worldName)) != null ? " §7(modèle ok)" : " §7(pas de modèle)"));
            }
            default -> {
                if (!admin) { sendHelp(sender, false); return true; }
                adminCommand(sender, p, sub, args);
            }
        }
        return true;
    }

    /** Aide : les commandes admin ne sont montrées qu'à ceux qui ont la permission. */
    private void sendHelp(CommandSender s, boolean admin) {
        s.sendMessage(PREFIX + "§fCommandes du Murder :");
        s.sendMessage("§e/murder join §7: rejoindre une partie");
        s.sendMessage("§e/murder leave §7: quitter la partie");
        s.sendMessage("§e/murder maps §7: liste des maps");
        s.sendMessage("§e/murder spectate [joueur] §7: regarder une partie");
        if (!admin) return;
        s.sendMessage("§c/murder start [n] §7| §cstop [n] §7| §cparties §7| §cmodeles §7| §creload");
        s.sendMessage("§c/murder setlobby §7| §cbuildlobby §7| §ccreatemap §7| §csetpos §7| §caddspawn §7| §caddgold");
    }

    /** Partie visée par start/stop : numéro donné, sinon celle de l'admin, sinon la partie 1. */
    private Game targetGame(CommandSender sender, Player p, String[] args) {
        if (args.length > 1) {
            try {
                Game g = games.get(Integer.parseInt(args[1]));
                if (g == null) sender.sendMessage(PREFIX + "§cPas de partie " + args[1] + ".");
                return g;
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cNuméro de partie invalide.");
                return null;
            }
        }
        if (p != null && byPlayer.containsKey(p.getUniqueId())) return byPlayer.get(p.getUniqueId());
        return games.get(1);
    }

    private boolean anyRunning() {
        return games.values().stream().anyMatch(g -> g.state == State.PLAYING || g.state == State.ENDING);
    }

    private void adminCommand(CommandSender sender, Player p, String sub, String[] args) {
        switch (sub) {
            case "start" -> {
                Game g = targetGame(sender, p, args);
                if (g == null) return;
                if (g.state != State.WAITING && g.state != State.STARTING) { sender.sendMessage(PREFIX + "§cUne partie est déjà en cours."); return; }
                if (g.players.size() < 2) { sender.sendMessage(PREFIX + "§cIl faut au moins 2 joueurs inscrits."); return; }
                g.startGame();
            }
            case "stop" -> {
                Game g = targetGame(sender, p, args);
                if (g == null) return;
                g.forceStop();
                sender.sendMessage(PREFIX + "§7" + g.label() + " arrêtée.");
            }
            case "parties", "games" -> {
                sender.sendMessage(PREFIX + "§e" + games.size() + "/" + maxInstances() + " partie(s) :");
                for (Game g : games.values()) sender.sendMessage(PREFIX + g.describe());
            }
            case "modeles" -> {
                for (String base : mapWorlds()) {
                    boolean ok = refreshModel(base);
                    sender.sendMessage(PREFIX + (ok ? "§aModèle " + modelName(base) + " en cours de copie."
                            : "§cModèle " + modelName(base) + " non refait (monde absent, copie en cours ou partie sur la map)."));
                }
            }
            case "reload" -> {
                if (anyRunning()) { sender.sendMessage(PREFIX + "§cAttends la fin des parties."); return; }
                loadSettings();
                ensureModels();
                sender.sendMessage(PREFIX + "§aConfig rechargée (" + maps.size() + " maps).");
            }
            case "buildlobby" -> {
                if (lobby == null || lobby.getWorld() == null) { sender.sendMessage(PREFIX + "§cLobby non défini."); return; }
                Game g1 = games.get(1);
                if (g1.state == State.PLAYING || g1.state == State.ENDING) { sender.sendMessage(PREFIX + "§cLa partie 1 est en cours."); return; }
                buildLobby(lobby);
                sender.sendMessage(PREFIX + "§aSalon d'attente construit.");
                // si le monde du salon porte aussi une map, son modèle doit suivre
                String lw = lobby.getWorld().getName();
                if (mapWorlds().contains(lw)) refreshModel(lw);
            }
            case "setlobby" -> {
                if (p == null) return;
                getConfig().set("lobby", formatLocation(p.getLocation(), true));
                saveConfig();
                lobby = p.getLocation();
                p.sendMessage(PREFIX + "§aLobby d'attente défini ici.");
            }
            case "createmap" -> {
                if (p == null || args.length < 3) { sender.sendMessage(PREFIX + "§7/murder createmap <id> <nom>"); return; }
                String path = "maps." + args[1];
                getConfig().set(path + ".name", String.join(" ", List.of(args).subList(2, args.length)));
                getConfig().set(path + ".world", p.getWorld().getName());
                saveAndReloadMaps();
                p.sendMessage(PREFIX + "§aMap " + args[1] + " créée. Définis /murder setpos " + args[1] + " 1 et 2, puis addspawn / addgold.");
            }
            case "setpos" -> {
                if (p == null || args.length < 3 || !maps.containsKey(args[1])) { sender.sendMessage(PREFIX + "§7/murder setpos <map> <1|2>"); return; }
                getConfig().set("maps." + args[1] + "." + ("1".equals(args[2]) ? "min" : "max"), formatLocation(p.getLocation(), false));
                saveAndReloadMaps();
                p.sendMessage(PREFIX + "§aCoin " + args[2] + " de " + args[1] + " défini.");
            }
            case "addspawn", "addgold" -> {
                if (p == null || args.length < 2 || !maps.containsKey(args[1])) { sender.sendMessage(PREFIX + "§7/murder " + sub + " <map>"); return; }
                String key = "maps." + args[1] + "." + (sub.equals("addspawn") ? "spawns" : "gold");
                List<String> list = new ArrayList<>(getConfig().getStringList(key));
                list.add(formatLocation(p.getLocation(), false));
                getConfig().set(key, list);
                saveAndReloadMaps();
                p.sendMessage(PREFIX + "§aPoint ajouté (" + list.size() + ").");
            }
            default -> sendHelp(sender, true);
        }
    }

    /**
     * Salon d'attente façon manoir autour du point de lobby : parquet de chêne noir et tapis rouge,
     * murs de bibliothèques entre des poutres, lustres, fauteuils, cheminée et horloge.
     */
    private void buildLobby(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX(), fy = center.getBlockY() - 1, cz = center.getBlockZ();
        int h = 8;        // demi-largeur intérieure
        int top = 6;      // hauteur sous plafond
        java.util.function.BiConsumer<int[], String> set = (p, data) -> {
            try {
                w.getBlockAt(cx + p[0], fy + p[1], cz + p[2]).setBlockData(Bukkit.createBlockData(data), false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc inconnu pour le salon : " + data);
            }
        };

        // nettoyage (ancienne salle comprise) puis sol, murs et plafond
        for (int x = -h - 4; x <= h + 4; x++)
            for (int z = -h - 4; z <= h + 4; z++)
                for (int y = 0; y <= top + 6; y++) set.accept(new int[]{x, y, z}, "minecraft:air");
        for (int x = -h - 1; x <= h + 1; x++) {
            for (int z = -h - 1; z <= h + 1; z++) {
                boolean wall = Math.abs(x) == h + 1 || Math.abs(z) == h + 1;
                set.accept(new int[]{x, -1, z}, "minecraft:stone_bricks");
                set.accept(new int[]{x, 0, z}, wall ? "minecraft:polished_deepslate"
                        : ((x + z) & 1) == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
                set.accept(new int[]{x, top + 1, z}, (Math.abs(x) % 4 == 0 && Math.abs(z) % 4 == 0 && !wall)
                        ? "minecraft:shroomlight" : "minecraft:dark_oak_planks");
                if (!wall) continue;
                int along = Math.abs(x) == h + 1 ? z : x;
                boolean beam = Math.abs(along) % 4 == 0 || Math.abs(along) == h + 1;
                for (int y = 1; y <= top; y++) {
                    String block = beam ? "minecraft:stripped_dark_oak_log"
                            : y == 1 ? "minecraft:dark_oak_planks"
                            : y == top ? "minecraft:dark_oak_planks"
                            : "minecraft:bookshelf";
                    set.accept(new int[]{x, y, z}, block);
                }
            }
        }
        // tapis rouge en croix, bordé de noir
        for (int i = -h; i <= h; i++) {
            set.accept(new int[]{i, 1, 0}, "minecraft:red_carpet");
            set.accept(new int[]{0, 1, i}, "minecraft:red_carpet");
            if (Math.abs(i) > 1) {
                set.accept(new int[]{i, 1, 1}, "minecraft:black_carpet");
                set.accept(new int[]{i, 1, -1}, "minecraft:black_carpet");
                set.accept(new int[]{1, 1, i}, "minecraft:black_carpet");
                set.accept(new int[]{-1, 1, i}, "minecraft:black_carpet");
            }
        }
        // lustres : chaînes + lanternes au-dessus de chaque quart de salle
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            set.accept(new int[]{c[0], top, c[1]}, "minecraft:iron_chain");
            set.accept(new int[]{c[0], top - 1, c[1]}, "minecraft:lantern[hanging=true]");
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                set.accept(new int[]{c[0] + d[0], top, c[1] + d[1]}, "minecraft:lantern[hanging=true]");
        }
        // cheminée au fond (nord) avec feu de camp, et horloge de part et d'autre
        for (int x = -2; x <= 2; x++)
            for (int y = 1; y <= top; y++) set.accept(new int[]{x, y, -h}, Math.abs(x) == 2 || y >= 4 ? "minecraft:bricks" : "minecraft:air");
        set.accept(new int[]{0, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{-1, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{1, 1, -h}, "minecraft:campfire[lit=true]");
        set.accept(new int[]{-2, 4, -h + 1}, "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false]");
        set.accept(new int[]{2, 4, -h + 1}, "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false]");
        for (int s : new int[]{-5, 5}) {
            set.accept(new int[]{s, 1, -h}, "minecraft:dark_oak_log");
            set.accept(new int[]{s, 2, -h}, "minecraft:dark_oak_log");
            set.accept(new int[]{s, 3, -h}, "minecraft:spruce_planks");
            set.accept(new int[]{s, 3, -h + 1}, "minecraft:spruce_button[face=wall,facing=south]");
        }
        // fauteuils (escaliers rouges) et petites tables autour du tapis
        for (int s : new int[]{-1, 1}) {
            for (int z : new int[]{-5, 5}) {
                set.accept(new int[]{s * 5, 1, z}, "minecraft:dark_oak_fence");
                set.accept(new int[]{s * 5, 2, z}, "minecraft:red_carpet");
                set.accept(new int[]{s * 6, 1, z}, "minecraft:spruce_stairs[facing=" + (s < 0 ? "west" : "east") + "]");
                set.accept(new int[]{s * 4, 1, z}, "minecraft:spruce_stairs[facing=" + (s < 0 ? "east" : "west") + "]");
            }
        }
        // plantes en pots dans les coins et bougies sur les tables
        for (int[] c : new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}}) {
            set.accept(new int[]{c[0], 1, c[1]}, "minecraft:potted_fern");
            set.accept(new int[]{c[0], 2, c[1]}, "minecraft:air");
        }
        set.accept(new int[]{-5, 3, -5}, "minecraft:candle[candles=3,lit=true]");
        set.accept(new int[]{5, 3, 5}, "minecraft:candle[candles=3,lit=true]");
        // porte d'entrée factice au sud
        set.accept(new int[]{0, 1, h + 1}, "minecraft:dark_oak_door[facing=north,half=lower]");
        set.accept(new int[]{0, 2, h + 1}, "minecraft:dark_oak_door[facing=north,half=upper]");
        set.accept(new int[]{0, 0, h + 1}, "minecraft:polished_deepslate");
    }

    private void saveAndReloadMaps() {
        saveConfig();
        if (!anyRunning()) {
            loadSettings();
            ensureModels();
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private ItemStack tool(Material type, String name, NamedTextColor color) {
        ItemStack it = new ItemStack(type);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isTool(ItemStack it) {
        return it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
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

    private String name(UUID id) {
        if (id == null) return "?";
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private static String formatTime(int s) {
        return String.format("%d:%02d", Math.max(0, s) / 60, Math.max(0, s) % 60);
    }

    private static String formatLocation(Location l, boolean full) {
        if (full) return String.format(java.util.Locale.ROOT, "%s,%.1f,%.1f,%.1f,%.0f,%.0f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static Location parseLocation(String s) {
        String[] p = s.split(",");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) return null;
        Location loc = new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()));
        if (p.length >= 6) {
            loc.setYaw(Float.parseFloat(p[4].trim()));
            loc.setPitch(Float.parseFloat(p[5].trim()));
        }
        return loc;
    }
}
