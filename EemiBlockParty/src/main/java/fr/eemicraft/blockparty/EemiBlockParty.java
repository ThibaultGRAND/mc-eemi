package fr.eemicraft.blockparty;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Block Party d'EEMICRAFT, façon Hypixel / Mineplex.
 * Une piste de danse colorée (béton par défaut) flotte au-dessus du vide, entourée d'une scène
 * (enceintes, projecteurs, cabine de DJ) et de barrières invisibles. Chaque manche : la piste est
 * redessinée avec un motif au hasard, une mélodie en note blocks est jouée ; quand elle s'arrête,
 * une couleur est annoncée (bloc dans toute la barre, titre, barre de boss). À la fin du temps, tous
 * les blocs d'une autre couleur disparaissent : ceux qui tombent sont éliminés. Le temps diminue à
 * chaque manche. Bonus (balise) de temps en temps. Dernier survivant gagnant.
 * Le plugin crée son monde vide lui-même et construit l'arène (démarrage, /blockparty build).
 *
 * 1.2 : plusieurs parties en même temps (max-instances, 3 par défaut). Partie 1 = monde « blockparty »,
 * partie n = copie « blockparty_n » du modèle « blockparty_modele » (copie propre de la partie 1, refaite
 * après /blockparty build). Chaque partie a sa piste, sa musique (jouée à ses seuls joueurs), sa barre de
 * boss et son scoreboard ; une copie vide est détruite.
 */
public final class EemiBlockParty extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§dBlock Party§8] §7";

    /** Sous-titre d'égalité : les pseudos s'ils tiennent (2 ou 3 joueurs), sinon le nombre de gagnants. */
    static String tieSubtitle(List<Player> win) {
        if (win.size() <= 3) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < win.size(); i++)
                sb.append(i == 0 ? "" : i == win.size() - 1 ? " et " : ", ").append(win.get(i).getName());
            if (sb.length() <= 34) return sb.toString();
        }
        return win.size() + " gagnants";
    }

    enum State { WAITING, STARTING, PLAYING, ENDING }

    enum Phase { NONE, INTRO, DANCE, CHOOSE, FALL }

    enum Bonus { SPEED, JUMP, ORACLE, SAVIOR }

    // ------------------------------------------------------------------ couleurs

    /** Les 16 colorants : nom Minecraft, nom affiché, couleur du texte, couleur de la barre de boss. */
    private record Dye(String id, String name, int rgb, BossBar.Color bar) {}

    private static final Dye[] DYES = {
            new Dye("WHITE", "BLANC", 0xF9FFFE, BossBar.Color.WHITE),
            new Dye("ORANGE", "ORANGE", 0xF9801D, BossBar.Color.YELLOW),
            new Dye("MAGENTA", "MAGENTA", 0xC74EBD, BossBar.Color.PINK),
            new Dye("LIGHT_BLUE", "BLEU CLAIR", 0x3AB3DA, BossBar.Color.BLUE),
            new Dye("YELLOW", "JAUNE", 0xFED83D, BossBar.Color.YELLOW),
            new Dye("LIME", "VERT CLAIR", 0x80C71F, BossBar.Color.GREEN),
            new Dye("PINK", "ROSE", 0xF38BAA, BossBar.Color.PINK),
            new Dye("GRAY", "GRIS", 0x6F7679, BossBar.Color.WHITE),
            new Dye("LIGHT_GRAY", "GRIS CLAIR", 0xB4B4AE, BossBar.Color.WHITE),
            new Dye("CYAN", "CYAN", 0x169C9C, BossBar.Color.BLUE),
            new Dye("PURPLE", "VIOLET", 0x8932B8, BossBar.Color.PURPLE),
            new Dye("BLUE", "BLEU", 0x3C44AA, BossBar.Color.BLUE),
            new Dye("BROWN", "MARRON", 0x835432, BossBar.Color.RED),
            new Dye("GREEN", "VERT", 0x5E7C16, BossBar.Color.GREEN),
            new Dye("RED", "ROUGE", 0xB02E26, BossBar.Color.RED),
            new Dye("BLACK", "NOIR", 0x55555F, BossBar.Color.WHITE),
    };

    // ------------------------------------------------------------------ musique

    /** Un air : nom affiché, instrument, ticks par pas, et pas (index note block 0..24, -1 silence, -2 note tenue). */
    private record Song(String name, Sound instrument, int tempo, int[] steps) {}

    private static final List<Song> SONGS = List.of(
            song("Frère Jacques", Sound.BLOCK_NOTE_BLOCK_HARP, 3,
                    "G4:2 A4:2 B4:2 G4:2 G4:2 A4:2 B4:2 G4:2 B4:2 C5:2 D5:4 B4:2 C5:2 D5:4 "
                            + "D5 E5 D5 C5 B4:2 G4:2 D5 E5 D5 C5 B4:2 G4:2 G4:2 D4:2 G4:4 G4:2 D4:2 G4:4"),
            song("Au clair de la lune", Sound.BLOCK_NOTE_BLOCK_FLUTE, 3,
                    "G4:2 G4:2 G4:2 A4:2 B4:4 A4:4 G4:2 B4:2 A4:2 A4:2 G4:8 "
                            + "G4:2 G4:2 G4:2 A4:2 B4:4 A4:4 G4:2 B4:2 A4:2 A4:2 G4:8 "
                            + "A4:2 A4:2 A4:2 A4:2 E4:4 E4:4 A4:2 G4:2 F#4:2 E4:2 D4:8 "
                            + "G4:2 G4:2 G4:2 A4:2 B4:4 A4:4 G4:2 B4:2 A4:2 A4:2 G4:8"),
            song("Ode à la joie", Sound.BLOCK_NOTE_BLOCK_HARP, 3,
                    "B4:2 B4:2 C5:2 D5:2 D5:2 C5:2 B4:2 A4:2 G4:2 G4:2 A4:2 B4:2 B4:3 A4:1 A4:4 "
                            + "B4:2 B4:2 C5:2 D5:2 D5:2 C5:2 B4:2 A4:2 G4:2 G4:2 A4:2 B4:2 A4:3 G4:1 G4:4"),
            song("Korobeiniki", Sound.BLOCK_NOTE_BLOCK_BIT, 2,
                    "B4:2 F#4 G4 A4:2 G4 F#4 E4:2 E4 G4 B4:2 A4 G4 F#4:3 G4 A4:2 B4:2 G4:2 E4:2 E4:4 "
                            + "_ A4:2 C5 E5:2 D5 C5 B4:3 G4 B4:2 A4 G4 F#4:2 F#4 G4 A4:2 B4:2 G4:2 E4:2 E4:4"),
            song("Ah ! vous dirai-je, maman", Sound.BLOCK_NOTE_BLOCK_BELL, 3,
                    "G4:2 G4:2 D5:2 D5:2 E5:2 E5:2 D5:4 C5:2 C5:2 B4:2 B4:2 A4:2 A4:2 G4:4 "
                            + "D5:2 D5:2 C5:2 C5:2 B4:2 B4:2 A4:4 D5:2 D5:2 C5:2 C5:2 B4:2 B4:2 A4:4 "
                            + "G4:2 G4:2 D5:2 D5:2 E5:2 E5:2 D5:4 C5:2 C5:2 B4:2 B4:2 A4:2 A4:2 G4:4"),
            song("Antre du roi de la montagne", Sound.BLOCK_NOTE_BLOCK_PLING, 2,
                    "E4 F#4 G4 A4 B4 G4 B4:2 A#4 F#4 A#4:2 A4 F4 A4:2 "
                            + "E4 F#4 G4 A4 B4 G4 B4 E5 D5 B4 G4 B4 D5:4"),
            song("La Cucaracha", Sound.BLOCK_NOTE_BLOCK_GUITAR, 2,
                    "C4 C4 C4 F4:3 A4:3 C4 C4 C4 F4:3 A4:3 _:2 F4:2 F4 E4 E4 D4 D4 C4:4 _:2 "
                            + "C4 C4 C4 E4:3 G4:3 C4 C4 C4 E4:3 G4:3 _:2 C5:2 D5 C5 A#4 A4 G4 F4:4 _:2")
    );

    /** Découpe un air écrit « C4:2 D4 _ ... » (note:durée en pas, _ = silence). */
    private static Song song(String name, Sound instrument, int tempo, String notes) {
        List<Integer> out = new ArrayList<>();
        for (String tok : notes.trim().split("\\s+")) {
            int len = 1;
            String n = tok;
            int c = tok.indexOf(':');
            if (c >= 0) {
                n = tok.substring(0, c);
                len = Integer.parseInt(tok.substring(c + 1));
            }
            out.add(n.equals("_") ? -1 : noteIndex(n));
            for (int i = 1; i < len; i++) out.add(-2);
        }
        int[] steps = new int[out.size()];
        for (int i = 0; i < steps.length; i++) steps[i] = out.get(i);
        return new Song(name, instrument, tempo, steps);
    }

    /** Note « F#4 » -> index de note block (0 = F#3, 24 = F#5), ramené dans la plage par octaves. */
    private static int noteIndex(String n) {
        int pc = switch (n.charAt(0)) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            default -> 11;
        };
        int i = 1;
        if (n.charAt(i) == '#') { pc++; i++; } else if (n.charAt(i) == 'b') { pc--; i++; }
        int octave = Integer.parseInt(n.substring(i));
        int idx = 12 * (octave + 1) + pc - 54;
        while (idx < 0) idx += 12;
        while (idx > 24) idx -= 12;
        return idx;
    }

    private static float pitch(int idx) {
        return (float) Math.pow(2, (idx - 12) / 12.0);
    }

    // ------------------------------------------------------------------ état global (pas d'état de partie ici)

    private NamespacedKey kitKey;
    /** Nom du monde de la partie 1 (config « world »). */
    private String base = "blockparty";
    private int floorY, size, half, lobbyY;
    private final List<Integer> palette = new ArrayList<>();
    private String floorSuffix = "CONCRETE";
    /** Parties par numéro (1 = monde d'origine, n ≥ 2 = copies). */
    private final Map<Integer, Game> games = new TreeMap<>();
    /** Modèle propre (copie de la partie 1) d'où partent les copies ; personne n'y va. */
    private World model;
    private boolean modelBusy;
    private int modelGen;
    private String lastStatus;
    private long lastStatusAt;
    private long ticks;
    private final Random random = new Random();

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        kitKey = new NamespacedKey(this, "kit");
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        World w = setupWorld();
        if (w != null) {
            WorldClone.cleanup(this, w, base, maxInstances());
            Game g1 = new Game(1, w);
            games.put(1, g1);
            g1.prepareWorld();
        }
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Game g1 = games.get(1);
            if (g1 != null && g1.world != null) {
                g1.build();
                makeModel();
            }
        }, 20L);
        // statut initial pour EemiCore (une fois tous les plugins actives)
        Bukkit.getScheduler().runTask(this, this::reportStatus);
    }

    @Override
    public void onDisable() {
        for (Game g : new ArrayList<>(games.values())) {
            g.removeBonus();
            for (Player p : online(g.players)) {
                if (g.bar != null) p.hideBossBar(g.bar);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            for (Player p : online(g.spectators)) {
                if (g.bar != null) p.hideBossBar(g.bar);
                if (g.world != null && p.getWorld().equals(g.world)) sendToHub(p);
            }
            g.players.clear();
            g.spectators.clear();
            g.pending.clear();
            if (g.sign != null) g.sign.remove();
        }
        // les copies restées sur le disque sont supprimées au prochain démarrage (WorldClone.cleanup)
    }

    private void loadSettings() {
        reloadConfig();
        base = getConfig().getString("world", "blockparty");
        floorY = cfg("floor-y", 100);
        size = Math.max(8, cfg("floor-size", 48) / 2 * 2);
        half = size / 2;
        lobbyY = cfg("lobby-y", 130);
        floorSuffix = getConfig().getString("floor-block", "CONCRETE").toUpperCase(Locale.ROOT);
        if (Material.matchMaterial("WHITE_" + floorSuffix) == null) {
            getLogger().warning("floor-block inconnu : " + floorSuffix + ", béton utilisé.");
            floorSuffix = "CONCRETE";
        }
        palette.clear();
        for (String s : getConfig().getStringList("colors"))
            for (int i = 0; i < DYES.length; i++)
                if (DYES[i].id().equalsIgnoreCase(s.trim()) && !palette.contains(i)) palette.add(i);
        if (palette.size() < 2) {
            getLogger().warning("Moins de 2 couleurs valides dans la config : les 16 couleurs sont utilisées.");
            palette.clear();
            for (int i = 0; i < DYES.length; i++) palette.add(i);
        }
        for (Game g : games.values()) g.resetGrid();
    }

    private int maxInstances() {
        return Math.max(1, cfg("max-instances", 3));
    }

    private String modelName() {
        return base + "_modele";
    }

    /** Charge ou crée le monde vide de la partie 1. */
    private World setupWorld() {
        World w = Bukkit.getWorld(base);
        if (w == null) {
            w = new WorldCreator(base).generator(new VoidGenerator()).createWorld();
            if (w != null) getLogger().info("Monde " + base + " cree (vide).");
        }
        if (w == null) getLogger().severe("Impossible de charger ou créer le monde " + base + " : Block Party désactivé.");
        return w;
    }

    /** Règles d'un monde du jeu (partie ou modèle) : jour fixe, pas de mobs, pas de dégâts de chute ni de PvP, rien ne bouge. */
    private void prepareRules(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setTime(cfg("time", 6000));
        world.setStorm(false);
        world.setThundering(false);
    }

    /** Générateur vide : le monde n'a que ce que le plugin construit. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.0, 131, 0.0);
        }
    }

    private int cfg(String key, int def) {
        return getConfig().getInt(key, def);
    }

    private double cfgD(String key, double def) {
        return getConfig().getDouble(key, def);
    }

    // ------------------------------------------------------------------ modèle et copies

    /**
     * (Re)fait le modèle depuis la partie 1 (juste construite). L'ancien modèle est déchargé sans
     * sauvegarde ; WorldClone.create supprime son dossier avant d'y recopier la partie 1. Jamais pendant
     * la préparation d'une copie (elle lit le dossier du modèle) : on réessaie 2 s après.
     */
    private void makeModel() {
        Game g1 = games.get(1);
        if (g1 == null || g1.world == null) return;
        for (Game g : games.values())
            if (g.preparing) {
                Bukkit.getScheduler().runTaskLater(this, this::makeModel, 40L);
                return;
            }
        int gen = ++modelGen;
        modelBusy = true;
        model = null;
        World old = Bukkit.getWorld(modelName());
        if (old != null) {
            for (Player p : old.getPlayers()) sendToHub(p);
            if (!Bukkit.unloadWorld(old, false)) getLogger().warning("Impossible de décharger l'ancien modèle " + modelName() + ".");
        }
        WorldClone.create(this, g1.world, modelName(), w -> {
            if (gen != modelGen) return; // un modèle plus récent est en route
            modelBusy = false;
            if (w == null) {
                getLogger().warning("Modèle " + modelName() + " impossible a créer : une seule partie possible.");
                return;
            }
            model = w;
            prepareRules(w);
            getLogger().info("Modèle " + modelName() + " prêt.");
        });
    }

    /** Nouvelle partie dans une copie du modèle (numéro libre le plus bas), ou null si impossible. */
    private Game newCopy() {
        if (model == null || modelBusy) return null;
        int idx = -1;
        for (int n = 2; n <= maxInstances(); n++)
            if (!games.containsKey(n)) { idx = n; break; }
        if (idx < 0) return null;
        Game g = new Game(idx, null);
        g.preparing = true;
        games.put(idx, g);
        WorldClone.create(this, model, WorldClone.name(base, idx), w -> {
            if (g.destroyed) {
                if (w != null) WorldClone.destroy(this, w, base, this::sendToHub);
                return;
            }
            if (w == null) {
                games.remove(g.index);
                g.destroyed = true;
                for (Player p : online(g.pending)) p.sendMessage(PREFIX + "§cImpossible de préparer une nouvelle partie, réessaie plus tard.");
                g.pending.clear();
                reportStatus();
                return;
            }
            g.world = w;
            g.prepareWorld();
            g.preparing = false;
            World hub = hubWorld();
            for (Player p : online(g.pending)) {
                if (hub != null && !p.getWorld().equals(hub)) {
                    p.sendMessage(PREFIX + "§7Tu as quitté le hub : la nouvelle partie ne t'attend pas.");
                    continue;
                }
                g.addPlayer(p);
            }
            g.pending.clear();
            reportStatus();
        });
        return g;
    }

    /** Détruit une copie vide (jamais la partie 1). */
    private void destroyGame(Game g) {
        if (g.index <= 1 || g.destroyed) return;
        g.destroyed = true;
        games.remove(g.index);
        g.removeBonus();
        if (g.sign != null) g.sign.remove();
        for (Player p : online(g.players)) g.leave(p, true);
        for (Player p : online(g.spectators)) sendToHub(p);
        g.spectators.clear();
        if (g.world != null) WorldClone.destroy(this, g.world, base, this::sendToHub);
        getLogger().info("Partie " + g.index + " terminée : copie " + WorldClone.name(base, g.index) + " supprimée.");
        reportStatus();
    }

    // ------------------------------------------------------------------ recherche des parties

    /** Monde d'une partie ou modèle : toutes les protections s'y appliquent. */
    private boolean isGameWorld(World w) {
        return w != null && (WorldClone.index(base, w.getName()) > 0 || w.getName().equals(modelName()));
    }

    private Game gameAt(World w) {
        if (w == null) return null;
        Game g = games.get(WorldClone.index(base, w.getName()));
        return g != null && w.equals(g.world) ? g : null;
    }

    /** Partie où le joueur est inscrit (hors attente de copie). */
    private Game gameOf(Player p) {
        for (Game g : games.values()) if (g.players.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game pendingOf(Player p) {
        for (Game g : games.values()) if (g.pending.contains(p.getUniqueId())) return g;
        return null;
    }

    private Game spectatorGame(Player p) {
        for (Game g : games.values()) if (g.spectators.contains(p.getUniqueId())) return g;
        return null;
    }

    /** Partie précise (« start 2 »), sinon celle de l'admin, sinon la partie 1. */
    private Game targetGame(CommandSender sender, String[] args) {
        if (args.length > 1) {
            try {
                return games.get(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sender instanceof Player p) {
            Game g = gameOf(p);
            if (g == null) g = spectatorGame(p);
            if (g != null) return g;
        }
        return games.get(1);
    }

    // ------------------------------------------------------------------ positions (géométrie commune)

    /** Distance « carrée » d'une case au centre (0 pour x = -1 ou 0, half - 1 au bord de la piste). */
    private static int ring(int v) {
        return v < 0 ? -v - 1 : v;
    }

    private static int dist(int x, int z) {
        return Math.max(ring(x), ring(z));
    }

    /** Coordonnée miroir : côté positif d, côté négatif -d-1 (symétrique autour de 0). */
    private static int side(int sign, int d) {
        return sign > 0 ? d : -d - 1;
    }

    private Material floorMaterial(int dye) {
        Material m = Material.matchMaterial(DYES[dye].id() + "_" + floorSuffix);
        return m == null ? Material.WHITE_CONCRETE : m;
    }

    private Material concrete(int dye) {
        Material m = Material.matchMaterial(DYES[dye].id() + "_CONCRETE");
        return m == null ? Material.WHITE_CONCRETE : m;
    }

    /** Choisit k couleurs au hasard dans la palette, puis un des générateurs de motif. */
    private int[][] randomPattern(int k) {
        List<Integer> pool = new ArrayList<>(palette);
        Collections.shuffle(pool, random);
        k = Math.max(2, Math.min(k, pool.size()));
        int[] cols = new int[k];
        for (int i = 0; i < k; i++) cols[i] = pool.get(i);
        int[][] g = new int[size][size];
        int type = random.nextInt(8);
        int w = 2 + random.nextInt(3); // largeur des bandes / cases : 2 à 4
        switch (type) {
            case 0 -> { // cases (damier coloré au hasard)
                int c = 2 + random.nextInt(3);
                int n = size / c + 2;
                int[][] cell = new int[n][n];
                for (int[] row : cell) for (int j = 0; j < n; j++) row[j] = cols[random.nextInt(k)];
                for (int i = 0; i < size; i++) for (int j = 0; j < size; j++) g[i][j] = cell[i / c][j / c];
            }
            case 1 -> { // rayures droites ou en diagonale
                int dir = random.nextInt(4);
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        int v = switch (dir) {
                            case 0 -> i;
                            case 1 -> j;
                            case 2 -> i + j;
                            default -> i - j + size;
                        };
                        g[i][j] = cols[Math.floorMod(v / w, k)];
                    }
            }
            case 2 -> { // cercles concentriques
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        double d = Math.hypot(i - half + 0.5, j - half + 0.5);
                        g[i][j] = cols[(int) (d / w) % k];
                    }
            }
            case 3 -> { // taches (diagramme de Voronoï)
                int n = 25 + random.nextInt(30);
                int[][] seeds = new int[n][3];
                for (int[] s : seeds) { s[0] = random.nextInt(size); s[1] = random.nextInt(size); s[2] = cols[random.nextInt(k)]; }
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        int best = 0, bd = Integer.MAX_VALUE;
                        for (int s = 0; s < n; s++) {
                            int dx = seeds[s][0] - i, dz = seeds[s][1] - j, dd = dx * dx + dz * dz;
                            if (dd < bd) { bd = dd; best = s; }
                        }
                        g[i][j] = seeds[best][2];
                    }
            }
            case 4 -> { // spirale
                double twist = 3 + random.nextInt(4);
                int sgn = random.nextBoolean() ? 1 : -1;
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        double x = i - half + 0.5, z = j - half + 0.5;
                        double a = (Math.atan2(z, x) + Math.PI) / (2 * Math.PI);
                        g[i][j] = cols[Math.floorMod((int) Math.floor(a * k + sgn * Math.hypot(x, z) / twist), k)];
                    }
            }
            case 5 -> { // losanges concentriques
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        double d = Math.abs(i - half + 0.5) + Math.abs(j - half + 0.5);
                        g[i][j] = cols[(int) (d / w) % k];
                    }
            }
            case 6 -> { // carreaux en diagonale (arlequin)
                int c = 3 + random.nextInt(2);
                Map<Long, Integer> cell = new HashMap<>();
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        long key = ((long) Math.floorDiv(i + j, c) << 32) ^ (Math.floorDiv(i - j, c) & 0xFFFFFFFFL);
                        g[i][j] = cell.computeIfAbsent(key, kk -> cols[random.nextInt(cols.length)]);
                    }
            }
            default -> { // vagues
                double amp = 2 + random.nextInt(3), per = 4 + random.nextInt(5);
                boolean alongX = random.nextBoolean();
                for (int i = 0; i < size; i++)
                    for (int j = 0; j < size; j++) {
                        int a = alongX ? i : j, b = alongX ? j : i;
                        int v = b + (int) Math.round(Math.sin(a / per) * amp) + size;
                        g[i][j] = cols[Math.floorMod(v / w, k)];
                    }
            }
        }
        return g;
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        ticks++;
        for (Game g : new ArrayList<>(games.values())) {
            if (g.world == null || g.destroyed) continue;
            g.tick();
        }
        if (ticks % 20 == 0) {
            // copie vide en attente : détruite (la prochaine repartira du modèle)
            for (Game g : new ArrayList<>(games.values()))
                if (g.index > 1 && !g.preparing && !g.destroyed && g.state == State.WAITING
                        && g.players.isEmpty() && g.pending.isEmpty() && g.spectators.isEmpty())
                    destroyGame(g);
            reportStatus();
        }
    }

    // ------------------------------------------------------------------ une partie

    final class Game {
        final int index;
        World world;
        boolean preparing, destroyed;
        /** Couleur (index dans DYES) de chaque case de la piste, -1 = vide. */
        int[][] grid = new int[0][0];

        final Set<UUID> players = new LinkedHashSet<>();
        /** Joueurs qui attendent au hub que la copie soit prête. */
        final Set<UUID> pending = new LinkedHashSet<>();
        final Set<UUID> inGame = new HashSet<>();
        final Set<UUID> alive = new LinkedHashSet<>();
        /** Éliminés pendant la manche en cours (gagnants à égalité si tout le monde tombe). */
        final List<UUID> roundOut = new ArrayList<>();
        final Set<UUID> saviors = new HashSet<>();
        final Map<UUID, Scoreboard> boards = new HashMap<>();
        /** Spectateurs (non inscrits) de la partie en cours. */
        final Set<UUID> spectators = new LinkedHashSet<>();

        State state = State.WAITING;
        Phase phase = Phase.NONE;
        int timer;
        int phaseTicks, phaseLength;
        int round, total;
        int target = -1;
        boolean revealed;
        double reaction;
        Song song;
        int songStep, songTick, lastNote = -1;
        Block bonusBlock;
        BossBar bar;
        TextDisplay sign;

        Game(int index, World world) {
            this.index = index;
            this.world = world;
            resetGrid();
        }

        void resetGrid() {
            grid = new int[size][size];
            for (int[] row : grid) java.util.Arrays.fill(row, -1);
        }

        void prepareWorld() {
            prepareRules(world);
            world.setSpawnLocation(lobbySpawn());
        }

        // -------------------------------------------------------------- positions

        Location lobbySpawn() {
            Location l = new Location(world, 0.0, lobbyY + 1, 2.0);
            l.setYaw(180);
            return l;
        }

        Location spectatorSpot() {
            Location l = new Location(world, 0.0, floorY + 12, half + 6.0);
            l.setYaw(180);
            l.setPitch(40);
            return l;
        }

        Location randomFloorSpot() {
            int m = Math.max(1, half - 3);
            double x = -m + random.nextInt(2 * m) + 0.5;
            double z = -m + random.nextInt(2 * m) + 0.5;
            Location l = new Location(world, x, floorY + 1, z);
            l.setYaw(random.nextInt(360) - 180);
            return l;
        }

        // -------------------------------------------------------------- construction de l'arène

        void set(int x, int y, int z, Material m) {
            world.getBlockAt(x, y, z).setType(m, false);
        }

        void setData(int x, int y, int z, String data) {
            try {
                world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(data), false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bloc inconnu : " + data);
            }
        }

        /**
         * Construit tout : la piste (half x half de chaque côté de 0,0 en y floor-y), un mur de barrières
         * invisible tout autour, un fossé de 2 blocs, puis la scène (pont de dalles de deepslate de 8 blocs
         * de large) avec enceintes aux coins et sur les côtés est/ouest, poteaux et portique de projecteurs
         * à 16 blocs de haut, cabine de DJ au nord. Salle d'attente 20 x 20 en lobby-y, vitre au centre.
         */
        void build() {
            int F = floorY, H = half, L = lobbyY;
            int R = H + 14;
            resetGrid();
            for (int cx = (-R) >> 4; cx <= (R) >> 4; cx++)
                for (int cz = (-R) >> 4; cz <= (R) >> 4; cz++) world.getChunkAt(cx, cz).load(true);

            removeBonus();
            // 1) on vide le volume de l'arène et de la salle d'attente
            for (int x = -R; x < R; x++)
                for (int z = -R; z < R; z++)
                    for (int y = F - 4; y <= F + 20; y++) set(x, y, z, Material.AIR);
            for (int x = -12; x < 12; x++)
                for (int z = -12; z < 12; z++)
                    for (int y = L - 1; y <= L + 8; y++) set(x, y, z, Material.AIR);

            // 2) piste + mur de barrières (on ne peut ni sortir ni monter dessus)
            drawFloor(randomPattern(palette.size()));
            for (int x = -H - 1; x <= H; x++)
                for (int z = -H - 1; z <= H; z++)
                    if (dist(x, z) == H) for (int y = F; y <= F + 8; y++) set(x, y, z, Material.BARRIER);

            // 3) la scène : pont tout autour (dist H+3 à H+10)
            int in = H + 3, out = H + 10;
            for (int x = -out - 1; x <= out; x++)
                for (int z = -out - 1; z <= out; z++) {
                    int d = dist(x, z);
                    if (d < in || d > out) continue;
                    Material top;
                    if (d == in) top = Material.SMOOTH_QUARTZ;
                    else if (d == in + 1) top = Math.floorMod(x + z, 3) == 0 ? Material.SEA_LANTERN : Material.POLISHED_BLACKSTONE;
                    else if (d == out) top = Material.POLISHED_BLACKSTONE_BRICKS;
                    else top = Material.DEEPSLATE_TILES;
                    set(x, F, z, top);
                    set(x, F - 1, z, Material.POLISHED_BLACKSTONE);
                    if (d == out) {
                        set(x, F - 2, z, Math.floorMod(x + z, 4) == 0 ? Material.SEA_LANTERN : Material.BLACK_CONCRETE);
                        // garde-corps : murets de blackstone, une lanterne tous les 4 blocs
                        set(x, F + 1, z, Material.POLISHED_BLACKSTONE_WALL);
                        if (Math.floorMod(x + z, 4) == 0) setData(x, F + 2, z, "minecraft:lantern[hanging=false]");
                    }
                }

            // 4) enceintes : 4 coins (3 x 3) et milieux est / ouest (3 x 4)
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2) {
                    int cx = side(sx, H + 7), cz = side(sz, H + 7);
                    speaker(cx - 1, cx + 1, cz - 1, cz + 1);
                }
            for (int sx = -1; sx <= 1; sx += 2) {
                int cx = side(sx, H + 7);
                speaker(cx - 1, cx + 1, -2, 1);
            }

            // 5) projecteurs : poteaux sur le pont (dist H+4), portique en y F+16, lampes colorées dessous
            int pd = H + 4, top = F + 16;
            List<int[]> posts = new ArrayList<>();
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2) posts.add(new int[]{side(sx, pd), side(sz, pd)});
            for (int s = -1; s <= 1; s += 2) {
                int q = side(s, H / 2);
                posts.add(new int[]{q, side(-1, pd)});
                posts.add(new int[]{q, side(1, pd)});
                posts.add(new int[]{side(-1, pd), q});
                posts.add(new int[]{side(1, pd), q});
            }
            for (int[] p : posts)
                for (int y = F + 1; y < top; y++) set(p[0], y, p[1], Material.POLISHED_BLACKSTONE_WALL);
            Material[] lights = {Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.OCHRE_FROGLIGHT, Material.SEA_LANTERN};
            int k = 0;
            for (int x = -pd - 1; x <= pd; x++)
                for (int z = -pd - 1; z <= pd; z++) {
                    if (dist(x, z) != pd) continue;
                    set(x, top, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    if (Math.floorMod(x + z, 4) == 0) {
                        set(x, top - 1, z, lights[k++ % lights.length]);
                        setData(x, top - 2, z, "minecraft:end_rod[facing=down]");
                    }
                }

            // 6) cabine du DJ au nord (z négatif) : estrade, pupitre, mur d'écran arc-en-ciel
            int zIn = side(-1, H + 5), zOut = side(-1, H + 9);
            for (int x = -6; x <= 5; x++)
                for (int z = zOut; z <= zIn; z++) set(x, F + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            Material[] desk = {Material.NOTE_BLOCK, Material.JUKEBOX, Material.SEA_LANTERN, Material.NOTE_BLOCK};
            for (int x = -4; x <= 3; x++) set(x, F + 2, zIn, desk[Math.floorMod(x, desk.length)]);
            for (int x = -6; x <= 5; x++) {
                for (int y = F + 2; y <= F + 8; y++) set(x, y, zOut, Material.BLACK_CONCRETE);
                if (x >= -5 && x <= 4)
                    for (int y = F + 3; y <= F + 7; y++)
                        set(x, y, zOut, concrete(palette.get(Math.floorMod(x + y, palette.size()))));
            }

            // 7) salle d'attente : vitre au centre (dist <= 4), anneau lumineux, sol coloré, murs de verre
            Random r = new Random(26);
            for (int x = -10; x <= 9; x++)
                for (int z = -10; z <= 9; z++) {
                    int d = dist(x, z);
                    if (d <= 4) set(x, L, z, Material.GLASS);
                    else if (d == 5) set(x, L, z, Material.SEA_LANTERN);
                    else if (d <= 8) set(x, L, z, concrete(palette.get(r.nextInt(palette.size()))));
                    else {
                        set(x, L, z, Material.SMOOTH_QUARTZ);
                        set(x, L + 1, z, Material.SMOOTH_QUARTZ);
                        for (int y = L + 2; y <= L + 4; y++) set(x, y, z, Material.GLASS);
                        set(x, L + 5, z, Material.SMOOTH_QUARTZ);
                    }
                    set(x, L + 6, z, d <= 8 && Math.floorMod(x + z, 4) == 0 && Math.floorMod(x - z, 4) == 0 ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ);
                }
            // boule à facettes au plafond
            for (int x = -1; x <= 0; x++)
                for (int z = -1; z <= 0; z++) {
                    setData(x, L + 5, z, "minecraft:iron_chain[axis=y]");
                    set(x, L + 4, z, Material.AMETHYST_BLOCK);
                }
            world.setSpawnLocation(lobbySpawn());
            ensureSign();
            getLogger().info("Arène construite (partie " + index + ", " + world.getName() + ") : piste " + size + "x" + size + " en y " + F + ", salle d'attente en y " + L + ".");
        }

        /** Enceinte : socle, caisse noire, haut-parleurs (note blocks) sur les faces, spot au sommet. */
        void speaker(int x0, int x1, int z0, int z1) {
            int F = floorY;
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean edgeX = x == x0 || x == x1, edgeZ = z == z0 || z == z1;
                    boolean corner = edgeX && edgeZ;
                    set(x, F + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
                    for (int y = F + 2; y <= F + 5; y++) {
                        boolean face = (edgeX || edgeZ) && !corner;
                        Material m = Material.BLACK_CONCRETE;
                        if (face && (y == F + 2 || y == F + 4)) m = Material.NOTE_BLOCK;
                        else if (face) m = Material.BLACK_WOOL;
                        set(x, y, z, m);
                    }
                    set(x, F + 6, z, corner ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE);
                }
            set((x0 + x1) / 2, F + 7, (z0 + z1) / 2, Material.SEA_LANTERN);
        }

        /** Panneau flottant de la salle d'attente (non sauvegardé : recréé s'il a disparu). */
        void ensureSign() {
            if (world == null || (sign != null && sign.isValid())) return;
            Location l = new Location(world, 0.0, lobbyY + 2.6, -7.0);
            if (!l.isChunkLoaded()) return;
            sign = world.spawn(l, TextDisplay.class, td -> {
                td.text(Component.text("BLOCK ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                        .append(Component.text("PARTY", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Quand la musique s'arrête,", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("cours sur la bonne couleur !", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text("Ramasse les balises pour un bonus.", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
                td.setBillboard(Display.Billboard.CENTER);
                td.setPersistent(false);
            });
        }

        // -------------------------------------------------------------- motifs de la piste

        /** Pose la piste (case [i][j] = bloc x = i - half, z = j - half). */
        void drawFloor(int[][] g) {
            if (grid.length != size) resetGrid();
            for (int i = 0; i < size; i++)
                for (int j = 0; j < size; j++) {
                    grid[i][j] = g[i][j];
                    set(i - half, floorY, j - half, floorMaterial(g[i][j]));
                }
        }

        /** Retire tous les blocs qui ne sont pas de la couleur annoncée. */
        void clearFloor(int keep) {
            for (int i = 0; i < size; i++)
                for (int j = 0; j < size; j++)
                    if (grid[i][j] != keep) {
                        grid[i][j] = -1;
                        set(i - half, floorY, j - half, Material.AIR);
                    }
        }

        int colorsForRound() {
            int min = cfg("colors-min", 10), max = cfg("colors-max", 16);
            return Math.min(Math.min(max, palette.size()), min + (Math.max(1, round) - 1) / 2);
        }

        // -------------------------------------------------------------- boucle (chaque tick)

        void tick() {
            if (state == State.PLAYING) gameTick();
            if (destroyed) return;
            if (ticks % 20 == 0) second();
            if (destroyed) return;
            if (ticks % 5 == 0) safety();
        }

        void second() {
            if (world.getTime() != cfg("time", 6000)) world.setTime(cfg("time", 6000));
            int min = Math.max(1, cfg("min-players", 2));
            switch (state) {
                case WAITING -> {
                    if (players.size() >= min) {
                        state = State.STARTING;
                        timer = cfg("countdown", 20);
                        broadcast("§eAssez de joueurs : la fête commence dans §6" + timer + " s§e !");
                    }
                }
                case STARTING -> {
                    if (players.size() < min) {
                        state = State.WAITING;
                        broadcast("§cPas assez de joueurs, lancement annulé.");
                    } else {
                        if (players.size() >= cfg("max-players", 30)) timer = Math.min(timer, cfg("full-countdown", 10));
                        if (timer <= 5 && timer > 0)
                            for (Player p : online(players)) {
                                p.showTitle(Title.title(Component.text(String.valueOf(timer), NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
                                        Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                            }
                        if (timer <= 0) startGame();
                        else timer--;
                    }
                }
                case PLAYING -> { }
                case ENDING -> {
                    if (--timer <= 0) backToLobby();
                }
            }
            if (destroyed) return;
            if (!players.isEmpty()) ensureSign();
            for (Player p : online(players)) updateSidebar(p);
        }

        /** Toutes les 5 ticks : un joueur du monde qui tombe hors partie revient à la salle d'attente. */
        void safety() {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().getY() > floorY - 20) continue;
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                if (alive.contains(p.getUniqueId())) continue;
                p.setFallDistance(0);
                p.teleport(state == State.PLAYING && players.contains(p.getUniqueId()) ? spectatorSpot() : lobbySpawn());
            }
        }

        // -------------------------------------------------------------- manches

        void gameTick() {
            checkFalls();
            if (state != State.PLAYING) return;
            phaseTicks--;
            switch (phase) {
                case INTRO -> {
                    if (phaseTicks <= 0) startRound();
                }
                case DANCE -> {
                    music();
                    bonusTick();
                    setBar(Component.text("Manche " + round + " - ", NamedTextColor.LIGHT_PURPLE)
                                    .append(Component.text("Danse !", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)),
                            BossBar.Color.PURPLE, (float) phaseTicks / Math.max(1, phaseLength));
                    if (phaseTicks % 20 == 0)
                        for (Player p : online(inGame))
                            p.sendActionBar(Component.text("Musique : ", NamedTextColor.GRAY).append(Component.text(song.name(), NamedTextColor.LIGHT_PURPLE)));
                    if (phaseTicks <= 0) startChoose();
                }
                case CHOOSE -> {
                    Dye d = DYES[target];
                    double left = phaseTicks / 20.0;
                    setBar(Component.text("» ", NamedTextColor.WHITE)
                                    .append(Component.text(d.name(), TextColor.color(d.rgb())).decorate(TextDecoration.BOLD))
                                    .append(Component.text(" « " + String.format(Locale.ROOT, "%.1f", Math.max(0, left)) + " s", NamedTextColor.WHITE)),
                            d.bar(), (float) phaseTicks / Math.max(1, phaseLength));
                    if (phaseTicks > 0 && phaseTicks % 10 == 0)
                        for (Player p : online(inGame)) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, 0.8f, 1.6f);
                    if (phaseTicks <= 0) removeFloor();
                }
                case FALL -> {
                    if (phaseTicks <= 0) endRound();
                }
                case NONE -> { }
            }
        }

        /** Pieds sous la piste = éliminé. */
        void checkFalls() {
            int limit = floorY - cfg("fall-depth", 3);
            for (Player p : online(alive))
                if (p.getWorld().equals(world) && p.getLocation().getY() < limit) eliminate(p);
        }

        /** Nouvelle manche : piste redessinée, couleur tirée d'avance, musique, peut-être un bonus. */
        void startRound() {
            round++;
            roundOut.clear();
            int[][] g = randomPattern(colorsForRound());
            drawFloor(g);
            // couleur annoncée : une couleur réellement présente sur la piste
            List<Integer> present = new ArrayList<>();
            for (int[] row : g) for (int c : row) if (!present.contains(c)) present.add(c);
            target = present.get(random.nextInt(present.size()));
            revealed = false;
            reaction = Math.max(cfgD("reaction-min", 1.5), cfgD("reaction-start", 5.0) - (round - 1) * cfgD("reaction-step", 0.25));
            int dmin = cfg("dance-min", 5), dmax = Math.max(dmin, cfg("dance-max", 9));
            phase = Phase.DANCE;
            phaseLength = phaseTicks = (dmin + random.nextInt(dmax - dmin + 1)) * 20;
            song = SONGS.get(random.nextInt(SONGS.size()));
            songStep = 0;
            songTick = 0;
            lastNote = -1;
            for (Player p : online(inGame)) {
                clearHotbar(p);
                p.showTitle(Title.title(Component.empty(),
                        Component.text("Manche " + round, NamedTextColor.LIGHT_PURPLE).append(Component.text(" - " + fmt(reaction) + " s", NamedTextColor.GRAY)),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300))));
            }
            if (round >= cfg("bonus-from-round", 2) && random.nextDouble() < cfgD("bonus-chance", 0.4)) spawnBonus();
        }

        /** La musique s'arrête : la couleur est annoncée, chacun reçoit le bloc dans toute sa barre. */
        void startChoose() {
            removeBonus();
            phase = Phase.CHOOSE;
            phaseLength = phaseTicks = (int) Math.round(reaction * 20);
            Dye d = DYES[target];
            ItemStack it = kit(new ItemStack(floorMaterial(target)));
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Component.text(d.name(), TextColor.color(d.rgb())).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
            for (Player p : online(inGame)) {
                if (mayTouch(p)) for (int s = 0; s < 9; s++) p.getInventory().setItem(s, it.clone());
                p.showTitle(Title.title(Component.text(d.name(), TextColor.color(d.rgb())).decorate(TextDecoration.BOLD),
                        Component.text("Va sur cette couleur !", NamedTextColor.WHITE),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(phaseTicks * 50L), Duration.ofMillis(200))));
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, 1f, 0.5f);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.RECORDS, 1f, 2f);
            }
        }

        /** Fin du temps : le sauveur agit, puis tous les blocs d'une autre couleur disparaissent. */
        void removeFloor() {
            for (Player p : online(alive)) {
                if (!saviors.contains(p.getUniqueId()) || standingOnTarget(p)) continue;
                Location safe = nearestTarget(p.getLocation());
                if (safe == null) continue;
                saviors.remove(p.getUniqueId());
                safe.setYaw(p.getLocation().getYaw());
                safe.setPitch(p.getLocation().getPitch());
                p.teleport(safe);
                p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                p.sendMessage(PREFIX + "§bLe bonus §fSauveur §bt'a déposé sur la bonne couleur !");
            }
            clearFloor(target);
            phase = Phase.FALL;
            phaseLength = phaseTicks = Math.max(1, cfg("fall-time", 3)) * 20;
            setBar(Component.text(DYES[target].name(), TextColor.color(DYES[target].rgb())).decorate(TextDecoration.BOLD), DYES[target].bar(), 0f);
            for (Player p : online(inGame)) p.playSound(p, Sound.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
        }

        boolean standingOnTarget(Player p) {
            Location l = p.getLocation();
            if (l.getY() < floorY + 0.9) return false;
            // le joueur peut être à cheval sur plusieurs cases : on regarde sous ses 4 coins
            for (double dx = -0.3; dx <= 0.3; dx += 0.6)
                for (double dz = -0.3; dz <= 0.3; dz += 0.6) {
                    int i = (int) Math.floor(l.getX() + dx) + half, j = (int) Math.floor(l.getZ() + dz) + half;
                    if (i >= 0 && j >= 0 && i < size && j < size && grid[i][j] == target) return true;
                }
            return false;
        }

        Location nearestTarget(Location from) {
            int bi = -1, bj = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < size; i++)
                for (int j = 0; j < size; j++) {
                    if (grid[i][j] != target) continue;
                    double d = Math.hypot(i - half + 0.5 - from.getX(), j - half + 0.5 - from.getZ());
                    if (d < bd) { bd = d; bi = i; bj = j; }
                }
            return bi < 0 ? null : new Location(world, bi - half + 0.5, floorY + 1, bj - half + 0.5);
        }

        /** Fin de manche : vainqueur(s) ou manche suivante. */
        void endRound() {
            for (Player p : online(inGame)) clearHotbar(p);
            if (alive.isEmpty()) {
                endGame(new ArrayList<>(roundOut));
                return;
            }
            if (alive.size() == 1 && total >= 2) {
                endGame(new ArrayList<>(alive));
                return;
            }
            if (round >= cfg("max-rounds", 25)) {
                broadcast("§eDernière manche terminée : tous les survivants gagnent !");
                endGame(new ArrayList<>(alive));
                return;
            }
            if (!roundOut.isEmpty()) broadcast("§7Manche " + round + " : §c" + roundOut.size() + " §7éliminé(s), §a" + alive.size() + " §7en piste.");
            startRound();
        }

        void eliminate(Player p) {
            UUID id = p.getUniqueId();
            if (!alive.remove(id)) return;
            roundOut.add(id);
            saviors.remove(id);
            if (p.getWorld().equals(world)) {
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                p.getInventory().clear();
                p.setFallDistance(0);
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(spectatorSpot());
            }
            p.showTitle(Title.title(Component.text("Éliminé", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    Component.text("Manche " + round, NamedTextColor.GRAY)));
            p.playSound(p, Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
            broadcast("§c" + p.getName() + " §7est tombé ! §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            reportStatus();
        }

        // -------------------------------------------------------------- musique

        /**
         * Un pas de l'air tous les « tempo » ticks : mélodie, basse sur les temps, grosse caisse / caisse
         * claire / charleston. Joué sur chaque joueur de CETTE partie seulement (inGame : en piste et
         * éliminés qui regardent), jamais aux autres parties ni au hub.
         */
        void music() {
            if (song == null || songTick++ % song.tempo() != 0) return;
            int[] steps = song.steps();
            int n = steps[songStep % steps.length];
            int beat = songStep % 4;
            songStep++;
            float mv = (float) cfgD("music-volume", 0.9), bv = (float) cfgD("bass-volume", 0.5), dv = (float) cfgD("drums-volume", 0.45);
            if (n >= 0) lastNote = n;
            for (Player p : online(inGame)) {
                if (!p.getWorld().equals(world)) continue;
                if (n >= 0) p.playSound(p, song.instrument(), SoundCategory.RECORDS, mv, pitch(n));
                if (beat == 0) {
                    p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.RECORDS, dv, 0.7f);
                    if (lastNote >= 0) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.RECORDS, bv, pitch(lastNote));
                } else if (beat == 2) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.RECORDS, dv * 0.8f, 1f);
                else p.playSound(p, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.RECORDS, dv * 0.5f, 1.4f);
            }
        }

        // -------------------------------------------------------------- bonus

        void spawnBonus() {
            removeBonus();
            int m = Math.max(1, half - 3);
            int x = -m + random.nextInt(2 * m), z = -m + random.nextInt(2 * m);
            bonusBlock = world.getBlockAt(x, floorY + 1, z);
            bonusBlock.setType(Material.BEACON, false);
            broadcast("§bUn bonus est apparu sur la piste ! §7Touche la balise pour le ramasser.");
            for (Player p : online(inGame)) p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        }

        void removeBonus() {
            if (bonusBlock != null && bonusBlock.getType() == Material.BEACON) bonusBlock.setType(Material.AIR, false);
            bonusBlock = null;
        }

        /** Particules autour de la balise ; un survivant qui la touche ramasse le bonus. */
        void bonusTick() {
            if (bonusBlock == null) return;
            double bx = bonusBlock.getX() + 0.5, by = bonusBlock.getY(), bz = bonusBlock.getZ() + 0.5;
            if (ticks % 5 == 0) world.spawnParticle(Particle.END_ROD, bx, by + 1.2, bz, 3, 0.3, 0.4, 0.3, 0.01);
            for (Player p : online(alive)) {
                Location l = p.getLocation();
                if (!l.getWorld().equals(world)) continue;
                if (Math.abs(l.getX() - bx) < 1.25 && Math.abs(l.getZ() - bz) < 1.25 && l.getY() > by - 1.5 && l.getY() < by + 2) {
                    removeBonus();
                    giveBonus(p);
                    return;
                }
            }
        }

        void giveBonus(Player p) {
            List<Bonus> list = new ArrayList<>();
            for (String s : getConfig().getStringList("bonuses"))
                try { list.add(Bonus.valueOf(s.trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) { }
            if (list.isEmpty()) list.add(Bonus.SPEED);
            Bonus b = list.get(random.nextInt(list.size()));
            if (!mayTouch(p)) return;
            int dur = cfg("bonus-effect-time", 10) * 20;
            String name;
            switch (b) {
                case SPEED -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1, false, true, true));
                    name = "Vitesse II";
                }
                case JUMP -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 1, false, true, true));
                    name = "Saut II";
                }
                case ORACLE -> {
                    name = "Oracle";
                    revealed = true;
                    Dye d = DYES[target];
                    p.sendMessage(PREFIX + "§bOracle : §7la prochaine couleur sera "
                            + "§l" + d.name() + "§r§7 !");
                    p.showTitle(Title.title(Component.text("Oracle", NamedTextColor.AQUA),
                            Component.text("Prochaine couleur : ", NamedTextColor.GRAY).append(Component.text(d.name(), TextColor.color(d.rgb())).decorate(TextDecoration.BOLD))));
                }
                default -> {
                    saviors.add(p.getUniqueId());
                    name = "Sauveur";
                    p.sendMessage(PREFIX + "§bSauveur : §7si tu n'es pas sur la bonne couleur quand le sol disparaît, tu y seras téléporté (une fois).");
                }
            }
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
            broadcast("§b" + p.getName() + " §7a ramassé le bonus §f" + name + "§7.");
        }

        // -------------------------------------------------------------- partie

        boolean running() {
            return state == State.PLAYING || state == State.ENDING;
        }

        void startGame() {
            List<Player> list = online(players);
            if (list.isEmpty() || world == null) { state = State.WAITING; return; }
            alive.clear();
            inGame.clear();
            roundOut.clear();
            saviors.clear();
            removeBonus();
            state = State.PLAYING;
            round = 0;
            total = list.size();
            world.setTime(cfg("time", 6000));
            drawFloor(randomPattern(colorsForRound()));
            bar = BossBar.bossBar(Component.text("Block Party"), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            for (Player p : list) {
                alive.add(p.getUniqueId());
                inGame.add(p.getUniqueId());
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(randomFloorSpot());
                p.showBossBar(bar);
                p.showTitle(Title.title(Component.text("BLOCK ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                                .append(Component.text("PARTY", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)),
                        Component.text("Reste sur la bonne couleur !", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
                p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            phase = Phase.INTRO;
            phaseLength = phaseTicks = 60;
            setBar(Component.text("Prépare-toi !", NamedTextColor.YELLOW), BossBar.Color.PURPLE, 1f);
            reportStatus();
            broadcast("§eLa fête commence avec §6" + total + " §ejoueurs ! §7Quand la musique s'arrête, va sur la couleur annoncée.");
        }

        void endGame(List<UUID> winners) {
            endGame(winners, true);
        }

        /** rewards = false pour un arrêt admin (pas de récompenses EemiCore). */
        void endGame(List<UUID> winners, boolean rewards) {
            if (state != State.PLAYING) return;
            state = State.ENDING;
            if (rewards) {
                for (Player p : online(inGame)) {
                    eemi("reward " + p.getName() + " blockparty play");
                    if (winners.contains(p.getUniqueId())) eemi("reward " + p.getName() + " blockparty win");
                }
            }
            phase = Phase.NONE;
            timer = cfg("end-time", 8);
            removeBonus();
            target = -1;
            if (bar != null) {
                for (Player p : online(players)) p.hideBossBar(bar);
                for (Player p : online(spectators)) p.hideBossBar(bar);
            }
            // la piste revient en entier sous les gagnants
            drawFloor(randomPattern(palette.size()));
            List<Player> win = online(new LinkedHashSet<>(winners));
            StringBuilder names = new StringBuilder();
            for (Player w : win) names.append(names.isEmpty() ? "" : ", ").append(w.getName());
            Component title = win.isEmpty()
                    ? Component.text("Fin de la fête", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    : Component.text(win.size() == 1 ? names.toString() : "Égalité !", win.size() == 1 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD); // titre court : pseudo seul
            for (Player p : online(players)) {
                boolean won = win.contains(p);
                p.showTitle(Title.title(title, Component.text(win.size() > 1 ? tieSubtitle(win) : won ? "Tu as gagné la Block Party !" : win.size() == 1 ? "gagne la Block Party !" : "Manche " + round, NamedTextColor.YELLOW)));
                p.playSound(p, won ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            if (win.isEmpty()) broadcast("§7La partie se termine sans vainqueur.");
            else broadcast("§6" + names + " §eremporte" + (win.size() > 1 ? "nt" : "") + " la Block Party §7(manche " + round + ") !");
            for (Player w : win) {
                if (w.getGameMode() == GameMode.SPECTATOR && w.getWorld().equals(world)) {
                    // tombés à la dernière manche : on les remet sur la piste pour la fête
                    w.setGameMode(GameMode.ADVENTURE);
                    w.teleport(randomFloorSpot());
                }
                for (int i = 0; i < 6; i++)
                    Bukkit.getScheduler().runTaskLater(EemiBlockParty.this, () -> {
                        if (w.isOnline() && state == State.ENDING && !destroyed && w.getWorld().equals(world)) firework(w.getLocation());
                    }, i * 20L);
            }
            alive.clear();
            saviors.clear();
            reportStatus();
        }

        /**
         * Après la fin : tout le monde (joueurs inscrits) revient dans la salle d'attente, les spectateurs
         * au hub. Pareil pour une copie (elle redevient une partie en attente) ; une copie vide est détruite.
         */
        void backToLobby() {
            state = State.WAITING;
            phase = Phase.NONE;
            alive.clear();
            inGame.clear();
            roundOut.clear();
            saviors.clear();
            for (Player p : online(players)) toWaiting(p);
            for (Player p : online(spectators)) {
                sendToHub(p);
                p.sendMessage(PREFIX + "§7Partie terminée, retour au hub.");
            }
            spectators.clear();
            reportStatus();
            if (index > 1 && players.isEmpty()) destroyGame(this);
        }

        void forceStop() {
            if (state == State.PLAYING) endGame(List.of(), false);
            if (state == State.ENDING) backToLobby();
            if (state == State.STARTING) state = State.WAITING;
        }

        void setBar(Component name, BossBar.Color color, float progress) {
            if (bar == null) return;
            bar.name(name);
            bar.color(color);
            bar.progress(Math.max(0f, Math.min(1f, progress)));
        }

        // -------------------------------------------------------------- inscription

        void addPlayer(Player p) {
            UUID id = p.getUniqueId();
            Game sg = spectatorGame(p);
            if (sg != null) {
                sg.spectators.remove(id);
                if (sg.bar != null) p.hideBossBar(sg.bar);
            }
            players.add(id);
            toWaiting(p);
            broadcast("§a" + p.getName() + " §7a rejoint la Block Party §8(partie " + index + ") §7(§f" + players.size() + "/" + cfg("max-players", 30) + "§7).");
            reportStatus();
        }

        void toWaiting(Player p) {
            if (bar != null) p.hideBossBar(bar);
            Location l = lobbySpawn();
            p.teleport(l);
            later(() -> {
                if (!p.isOnline() || !players.contains(p.getUniqueId()) || destroyed) return;
                // téléportation refusée ou joueur reparti entre-temps : on ne touche à rien hors du monde de la partie
                if (!p.getWorld().equals(world)) return;
                resetPlayer(p, GameMode.ADVENTURE);
                p.teleport(l);
                p.getInventory().setItem(8, named(kit(new ItemStack(Material.RED_BED)), "Quitter Block Party"));
                updateSidebar(p);
            });
        }

        void leave(Player p, boolean teleport) {
            UUID id = p.getUniqueId();
            if (!players.remove(id)) return;
            if (alive.remove(id) && state == State.PLAYING)
                broadcast("§c" + p.getName() + " §7a quitté la piste. §8(" + alive.size() + " restant" + (alive.size() > 1 ? "s" : "") + ")");
            inGame.remove(id);
            saviors.remove(id);
            roundOut.remove(id);
            boards.remove(id);
            if (bar != null) p.hideBossBar(bar);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // sans téléportation (le joueur a changé de monde) : on ne touche plus au joueur, Multiverse-Inventories
            // a déjà sauvegardé l'inventaire du jeu et chargé celui du monde d'arrivée
            if (teleport) sendToHub(p);
            // plus personne en piste : la partie s'arrête tout de suite
            if (state == State.PLAYING && alive.isEmpty() && roundOut.isEmpty()) endGame(List.of());
            reportStatus();
        }

        // -------------------------------------------------------------- scoreboard latéral

        Scoreboard board(Player p) {
            Scoreboard b = boards.get(p.getUniqueId());
            if (b == null) {
                b = Bukkit.getScoreboardManager().getNewScoreboard();
                Objective o = b.registerNewObjective("bp", Criteria.DUMMY,
                        Component.text("BLOCK ", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                                .append(Component.text("PARTY", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)));
                o.setDisplaySlot(DisplaySlot.SIDEBAR);
                boards.put(p.getUniqueId(), b);
            }
            if (p.getScoreboard() != b) p.setScoreboard(b);
            return b;
        }

        void setLines(Player p, List<Component> lines) {
            Scoreboard b = board(p);
            Objective o = b.getObjective("bp");
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
            boolean playing = inGame.contains(p.getUniqueId()) && running();
            if (!playing) {
                l.add(Component.text("Partie : ", NamedTextColor.GRAY).append(Component.text("n°" + index, NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs : ", NamedTextColor.GRAY).append(Component.text(players.size() + "/" + cfg("max-players", 30), NamedTextColor.WHITE)));
                l.add(Component.text("Minimum : ", NamedTextColor.GRAY).append(Component.text(String.valueOf(cfg("min-players", 2)), NamedTextColor.WHITE)));
                l.add(Component.text("  "));
                if (state == State.STARTING) l.add(Component.text("Début dans ", NamedTextColor.GRAY).append(Component.text(timer + " s", NamedTextColor.LIGHT_PURPLE)));
                else if (state == State.WAITING) l.add(Component.text("En attente...", NamedTextColor.YELLOW));
                else {
                    l.add(Component.text("Partie en cours...", NamedTextColor.YELLOW));
                    l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
                }
            } else {
                double speed = round == 0 ? cfgD("reaction-start", 5.0) : reaction;
                l.add(Component.text("Manche : ", NamedTextColor.GRAY).append(Component.text(Math.max(1, round) + "/" + cfg("max-rounds", 25), NamedTextColor.WHITE)));
                l.add(Component.text("Joueurs restants : ", NamedTextColor.GRAY).append(Component.text(alive.size() + "/" + total, NamedTextColor.WHITE)));
                l.add(Component.text("Vitesse : ", NamedTextColor.GRAY).append(Component.text(fmt(speed) + " s", NamedTextColor.AQUA)));
                Component color;
                boolean known = target >= 0 && (phase == Phase.CHOOSE || phase == Phase.FALL || (phase == Phase.DANCE && revealed && alive.contains(p.getUniqueId())));
                if (known) color = Component.text(DYES[target].name(), TextColor.color(DYES[target].rgb())).decorate(TextDecoration.BOLD);
                else color = Component.text("?", NamedTextColor.DARK_GRAY);
                l.add(Component.text("Ta couleur : ", NamedTextColor.GRAY).append(color));
                if (saviors.contains(p.getUniqueId())) l.add(Component.text("Bonus : ", NamedTextColor.GRAY).append(Component.text("Sauveur", NamedTextColor.AQUA)));
                if (!alive.contains(p.getUniqueId()) && state == State.PLAYING) l.add(Component.text("Spectateur", NamedTextColor.DARK_GRAY));
            }
            l.add(Component.text("   "));
            l.add(Component.text("eemicraft", NamedTextColor.DARK_GRAY));
            setLines(p, l);
        }

        /** Message aux seuls joueurs inscrits dans cette partie. */
        void broadcast(String msg) {
            for (Player p : online(players)) p.sendMessage(PREFIX + msg);
        }

        String stateLabel() {
            if (preparing) return "preparation";
            return switch (state) {
                case WAITING -> "attente";
                case STARTING -> "décompte " + timer + " s";
                case PLAYING -> "manche " + round;
                case ENDING -> "fin";
            };
        }
    }

    // ------------------------------------------------------------------ inscription (choix de la partie)

    /**
     * /blockparty join : la partie en attente (ou en décompte, ou en préparation) qui a de la place, la plus
     * remplie d'abord ; sinon une nouvelle copie si on est sous max-instances ; sinon « pleines ».
     * Les membres d'un groupe rejoignent l'un après l'autre : la plus remplie est celle du premier.
     */
    private void join(Player p) {
        UUID id = p.getUniqueId();
        if (gameOf(p) != null) { p.sendMessage(PREFIX + "§7Tu es déjà inscrit."); return; }
        if (pendingOf(p) != null) { p.sendMessage(PREFIX + "§7Ta partie est en préparation, patiente un instant."); return; }
        if (games.isEmpty()) { p.sendMessage(PREFIX + "§cLe monde de Block Party n'est pas chargé."); return; }
        int max = cfg("max-players", 30);
        Game target = null;
        int best = -1;
        for (Game g : games.values()) {
            if (g.destroyed || g.running()) continue;
            if (!g.preparing && g.world == null) continue;
            int n = g.players.size() + g.pending.size();
            if (n >= max) continue;
            if (n > best) { best = n; target = g; }
        }
        if (target == null) {
            if (games.size() >= maxInstances()) { p.sendMessage(PREFIX + "§cToutes les parties sont pleines."); return; }
            target = newCopy();
            if (target == null) { p.sendMessage(PREFIX + "§cUne partie est en préparation, réessaie dans quelques secondes."); return; }
        }
        if (target.preparing) {
            target.pending.add(id);
            p.sendMessage(PREFIX + "§7Préparation d'une nouvelle partie...");
            reportStatus();
            return;
        }
        target.addPlayer(p);
    }

    /**
     * Renvoi au hub. Seulement si le joueur est dans un monde du jeu ou déjà au hub : ailleurs (skyblock...)
     * on ne le téléporte ni ne le réinitialise. Dans un monde du jeu, la remise à zéro touche l'inventaire
     * de CE monde (Multiverse-Inventories charge ensuite celui du hub) ; au hub, celui du hub.
     */
    private void sendToHub(Player p) {
        if (!mayTouch(p)) return;
        resetPlayer(p, GameMode.SURVIVAL);
        World hub = hubWorld();
        if (hub != null) p.teleport(hub.getSpawnLocation());
    }

    /**
     * Le plugin ne touche à l'inventaire, à la vie, aux effets ou au mode de jeu d'un joueur que s'il est
     * dans un monde du jeu (partie, copie, modèle) ou au hub : jamais dans un autre monde (skyblock...),
     * sinon Multiverse-Inventories sauvegarde l'inventaire vidé de ce monde.
     */
    private boolean mayTouch(Player p) {
        World w = p.getWorld();
        if (isGameWorld(w)) return true;
        World hub = hubWorld();
        return hub != null && w.equals(hub);
    }

    private World hubWorld() {
        return Bukkit.getWorld(getConfig().getString("hub-world", "lobby"));
    }

    private void resetPlayer(Player p, GameMode mode) {
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

    private void clearHotbar(Player p) {
        if (!mayTouch(p)) return;
        for (int s = 0; s < 9; s++) p.getInventory().setItem(s, null);
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l.clone().add(0, 1, 0), Firework.class);
        FireworkMeta m = fw.getFireworkMeta();
        Dye a = DYES[palette.get(random.nextInt(palette.size()))], b = DYES[palette.get(random.nextInt(palette.size()))];
        m.addEffect(FireworkEffect.builder().with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                .withColor(Color.fromRGB(a.rgb()), Color.fromRGB(b.rgb())).withFade(Color.WHITE).flicker(true).build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ------------------------------------------------------------------ objets

    private ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kit(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isKit(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(kitKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ événements

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("eemiblockparty.admin");
    }

    /** Aucun dégât dans les mondes du jeu (ni PvP, ni chute, ni vide : les chutes sont gérées par le plugin). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (isGameWorld(p.getWorld()) || gameOf(p) != null)) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (isGameWorld(p.getWorld()) || gameOf(p) != null)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (isGameWorld(e.getBlock().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (isGameWorld(e.getBlock().getWorld()) && !admin(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isGameWorld(e.getLocation().getWorld()) && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // le décor (note blocks, juke-box, balise) ne se règle pas
        if (isGameWorld(p.getWorld()) && !admin(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) e.setCancelled(true);
        Game g = gameOf(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.RED_BED && isKit(it)) {
            e.setCancelled(true);
            g.leave(p, true);
            p.sendMessage(PREFIX + "§7Tu as quitté la Block Party.");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (gameOf(e.getPlayer()) != null || isKit(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && gameOf(p) != null && !admin(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (gameOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    /** Quitter le monde de SA partie = quitter la partie. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player sp = e.getPlayer();
        Game sg = spectatorGame(sp);
        if (sg != null && !sp.getWorld().equals(sg.world)) {
            // spectateur parti (/lobby) : il redevient un joueur normal
            sg.spectators.remove(sp.getUniqueId());
            if (sg.bar != null) sp.hideBossBar(sg.bar);
            if (sp.getGameMode() == GameMode.SPECTATOR && mayTouch(sp)) sp.setGameMode(GameMode.SURVIVAL);
        }
        Game g = gameOf(sp);
        if (g != null && g.world != null && e.getFrom().equals(g.world) && !sp.getWorld().equals(g.world)) g.leave(sp, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Game sg = spectatorGame(p);
        if (sg != null) {
            sg.spectators.remove(p.getUniqueId());
            if (sg.bar != null) p.hideBossBar(sg.bar);
            sendToHub(p);
        }
        Game pg = pendingOf(p);
        if (pg != null) pg.pending.remove(p.getUniqueId());
        Game g = gameOf(p);
        if (g != null) g.leave(p, true);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        boolean admin = sender.hasPermission("eemiblockparty.admin");
        Game g1 = games.get(1);
        switch (sub) {
            case "join" -> {
                Player t = args.length > 1 && admin ? Bukkit.getPlayerExact(args[1]) : p;
                if (t == null) { sender.sendMessage(PREFIX + "§cJoueur introuvable."); return true; }
                join(t);
            }
            case "spectate", "spec" -> {
                if (p != null) spectate(p, args.length > 1 ? args[1] : null);
            }
            case "leave" -> {
                if (p == null) return true;
                if (spectatorGame(p) != null) { sendToHub(p); return true; }
                Game pg = pendingOf(p);
                if (pg != null) {
                    pg.pending.remove(p.getUniqueId());
                    p.sendMessage(PREFIX + "§7Tu ne rejoindras pas la partie en préparation.");
                    reportStatus();
                    return true;
                }
                Game g = gameOf(p);
                if (g == null) return true;
                g.leave(p, true);
                p.sendMessage(PREFIX + "§7Tu as quitté la Block Party.");
            }
            case "start" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.preparing) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                if (g.running()) { sender.sendMessage(PREFIX + "§cUne partie est déjà en cours."); return true; }
                if (g.players.isEmpty()) { sender.sendMessage(PREFIX + "§cPersonne n'est inscrit."); return true; }
                g.startGame();
                sender.sendMessage(PREFIX + "§aPartie " + g.index + " lancée.");
            }
            case "stop" -> {
                if (!admin) return true;
                Game g = targetGame(sender, args);
                if (g == null || g.preparing) { sender.sendMessage(PREFIX + "§cPartie introuvable."); return true; }
                g.forceStop();
                sender.sendMessage(PREFIX + "§7Partie " + g.index + " arrêtée.");
            }
            case "parties", "games" -> {
                if (!admin) return true;
                sender.sendMessage(PREFIX + "§7Parties (" + games.size() + "/" + maxInstances() + ") :");
                for (Game g : games.values())
                    sender.sendMessage("§8- §e#" + g.index + " §f" + WorldClone.name(base, g.index) + " §7" + g.stateLabel()
                            + " §8| §7joueurs §f" + g.players.size()
                            + (g.pending.isEmpty() ? "" : " §8(+" + g.pending.size() + " en route)")
                            + (g.running() ? " §8| §7en piste §f" + g.alive.size() : "")
                            + (g.spectators.isEmpty() ? "" : " §8| §7spectateurs §f" + g.spectators.size()));
                sender.sendMessage("§8- §7modèle §f" + modelName() + " §7" + (model != null ? "pret" : modelBusy ? "en préparation" : "absent"));
            }
            case "build" -> {
                if (!admin) return true;
                if (g1 == null || g1.world == null) { sender.sendMessage(PREFIX + "§cMonde introuvable."); return true; }
                if (g1.running()) { sender.sendMessage(PREFIX + "§cImpossible : la partie 1 est en cours."); return true; }
                g1.build();
                for (Player pl : online(g1.players)) g1.toWaiting(pl);
                makeModel();
                sender.sendMessage(PREFIX + "§aArène de la partie 1 reconstruite §7(modèle refait).");
            }
            case "reload" -> {
                if (!admin) return true;
                for (Game g : games.values())
                    if (g.running()) { sender.sendMessage(PREFIX + "§cImpossible pendant une partie (partie " + g.index + ")."); return true; }
                String oldBase = base;
                loadSettings();
                if (!base.equals(oldBase)) {
                    base = oldBase;
                    sender.sendMessage(PREFIX + "§cLe monde du jeu ne change qu'au redémarrage.");
                }
                for (Game g : games.values()) {
                    if (g.world == null) continue;
                    prepareRules(g.world);
                    g.drawFloor(randomPattern(palette.size()));
                }
                sender.sendMessage(PREFIX + "§aConfig rechargée. §7(/blockparty build si la taille ou la hauteur a changé)");
            }
            default -> sender.sendMessage(PREFIX + "§7/blockparty <join|leave|spectate [joueur]>" + (admin ? " §8| admin : start [n], stop [n], parties, build, reload" : ""));
        }
        return true;
    }

    /** Regarder une partie en cours en spectateur (celle d'un joueur, sinon la plus remplie), au bord de la piste. */
    private void spectate(Player p, String targetName) {
        if (gameOf(p) != null || pendingOf(p) != null) { p.sendMessage(PREFIX + "§cTu es inscrit : §f/blockparty leave §cd'abord."); return; }
        Game g = null;
        if (targetName != null) {
            Player t = Bukkit.getPlayerExact(targetName);
            g = t == null ? null : gameOf(t);
            if (g == null) { p.sendMessage(PREFIX + "§cCe joueur n'est dans aucune partie de Block Party."); return; }
            if (g.state != State.PLAYING) { p.sendMessage(PREFIX + "§cSa partie n'a pas encore commencé."); return; }
        } else {
            int best = -1;
            for (Game c : games.values())
                if (c.state == State.PLAYING && c.world != null && c.inGame.size() > best) { best = c.inGame.size(); g = c; }
        }
        if (g == null || g.world == null) { p.sendMessage(PREFIX + "§cAucune partie en cours."); return; }
        Game old = spectatorGame(p);
        if (old != null && old != g) {
            old.spectators.remove(p.getUniqueId());
            if (old.bar != null) p.hideBossBar(old.bar);
        }
        g.spectators.add(p.getUniqueId());
        // téléporter d'abord (Multiverse-Inventories sauvegarde l'inventaire du monde quitté), puis vider
        p.teleport(g.spectatorSpot());
        if (!p.getWorld().equals(g.world)) {
            g.spectators.remove(p.getUniqueId());
            return;
        }
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(PREFIX + "§7Tu regardes la partie " + g.index + ", §f/lobby §7pour revenir.");
    }

    // ------------------------------------------------------------------ EemiCore

    /** Prévient EemiCore (statut de la partie, récompenses) s'il est installé ; sans lui, ne fait rien. */
    private void eemi(String args) {
        if (Bukkit.getPluginManager().isPluginEnabled("EemiCore"))
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi " + args);
    }

    /**
     * Statut agrégé de toutes les parties pour EemiCore : waiting si une partie attend ou décompte (ou se
     * prépare), sinon playing si une partie joue, sinon waiting. Envoyé s'il a changé (ou toutes les 30 s).
     */
    private void reportStatus() {
        int waiting = 0, playing = 0;
        boolean anyWait = false, anyPlay = false;
        for (Game g : games.values()) {
            if (g.destroyed) continue;
            if (g.preparing) {
                anyWait = true;
                waiting += g.pending.size();
                continue;
            }
            if (g.state == State.WAITING || g.state == State.STARTING) anyWait = true;
            else anyPlay = true;
            for (Player p : online(g.players)) {
                UUID id = p.getUniqueId();
                if (!g.running() || !g.inGame.contains(id)) waiting++;
                else if (g.state == State.ENDING || g.alive.contains(id)) playing++;
            }
        }
        String st = anyWait ? "waiting" : anyPlay ? "playing" : "waiting";
        String status = "status blockparty " + st + " " + waiting + " " + playing + " " + cfg("max-players", 30) * maxInstances();
        long now = System.currentTimeMillis();
        if (status.equals(lastStatus) && now - lastStatusAt < 30_000) return;
        lastStatus = status;
        lastStatusAt = now;
        eemi(status);
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
