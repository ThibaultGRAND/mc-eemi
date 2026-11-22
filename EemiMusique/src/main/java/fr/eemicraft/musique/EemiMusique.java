package fr.eemicraft.musique;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Musique d'ambiance du lobby jouée note par note avec les sons des blocs musicaux (comme Block Party) :
 * plusieurs pistes par morceau (mélodie, accords, basse, batterie), un séquenceur commun à tous les joueurs
 * des mondes de "worlds". Morceaux dans songs.yml (généré par gen_songs.py, domaine public ou originaux) ;
 * un songs.yml dans le dossier du plugin, s'il existe, remplace celui du jar.
 */
public final class EemiMusique extends JavaPlugin implements Listener {

    private static final String PREFIX = "§d[§fMusique§d] §r";

    /** Un pas d'une piste : notes jouées (indices 0..24 de bloc musical), vide = rien. */
    private record Track(Sound sound, float volume, int[][] steps) {}

    private record Song(String key, String name, int tempo, int repeat, List<Track> tracks) {
        int length() {
            return tracks.get(0).steps().length * repeat;
        }
    }

    private final List<Song> songs = new ArrayList<>();
    private final Deque<Song> queue = new ArrayDeque<>();
    private final Set<String> worlds = new HashSet<>();
    /** Joueurs qui ont coupé la musique (/musique off), sauvegardés dans data.yml. */
    private final Set<UUID> muted = new HashSet<>();
    private File dataFile;

    private Song current;
    private Song last;
    private int step, tick, pause;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        for (String s : YamlConfiguration.loadConfiguration(dataFile).getStringList("muted")) {
            try {
                muted.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 40L, 1L);
    }

    private void loadSettings() {
        reloadConfig();
        worlds.clear();
        for (String w : getConfig().getStringList("worlds")) worlds.add(w.toLowerCase(Locale.ROOT));
        YamlConfiguration y;
        File custom = new File(getDataFolder(), "songs.yml");
        if (custom.exists()) y = YamlConfiguration.loadConfiguration(custom);
        else {
            var in = getResource("songs.yml");
            y = in == null ? new YamlConfiguration() : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        songs.clear();
        ConfigurationSection sec = y.getConfigurationSection("songs");
        if (sec != null) for (String key : sec.getKeys(false)) {
            try {
                songs.add(parseSong(key, sec.getConfigurationSection(key)));
            } catch (RuntimeException e) {
                getLogger().warning("Morceau " + key + " ignore : " + e.getMessage());
            }
        }
        queue.clear();
        current = null;
        pause = 40;
        getLogger().info(songs.size() + " morceaux charges" + (custom.exists() ? " (songs.yml du dossier du plugin)." : "."));
    }

    private Song parseSong(String key, ConfigurationSection s) {
        List<Track> tracks = new ArrayList<>();
        for (var m : s.getMapList("tracks")) {
            String inst = String.valueOf(m.get("instrument")).toLowerCase(Locale.ROOT);
            Sound sound = Registry.SOUND_EVENT.get(NamespacedKey.minecraft("block.note_block." + inst));
            if (sound == null) throw new IllegalArgumentException("instrument inconnu : " + inst);
            Object v = m.get("volume");
            float volume = v instanceof Number n ? n.floatValue() : 0.5f;
            tracks.add(new Track(sound, volume, parseNotes(String.valueOf(m.get("notes")))));
        }
        if (tracks.isEmpty()) throw new IllegalArgumentException("aucune piste");
        return new Song(key, s.getString("name", key), Math.max(1, s.getInt("tempo", 4)), Math.max(1, s.getInt("repeat", 1)), tracks);
    }

    /** « C4:2 D4+F#4 _:3 | ... » -> une case par pas (notes jouées au début de la note, rien pendant qu'elle dure). */
    private static int[][] parseNotes(String notes) {
        List<int[]> out = new ArrayList<>();
        for (String tok : notes.trim().split("\\s+")) {
            if (tok.isEmpty() || tok.equals("|")) continue;
            int len = 1;
            String n = tok;
            int c = tok.indexOf(':');
            if (c >= 0) {
                n = tok.substring(0, c);
                len = Integer.parseInt(tok.substring(c + 1));
            }
            if (n.equals("_")) out.add(new int[0]);
            else {
                String[] parts = n.split("\\+");
                int[] chord = new int[parts.length];
                for (int i = 0; i < parts.length; i++) chord[i] = noteIndex(parts[i]);
                out.add(chord);
            }
            for (int i = 1; i < len; i++) out.add(new int[0]);
        }
        return out.toArray(new int[0][]);
    }

    /** Note « F#4 » -> index de bloc musical (0 = F#3, 24 = F#5), ramené dans la plage par octaves. */
    private static int noteIndex(String n) {
        int pc = switch (n.charAt(0)) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("note invalide : " + n);
        };
        int i = 1;
        if (n.charAt(i) == '#') {
            pc++;
            i++;
        } else if (n.charAt(i) == 'b') {
            pc--;
            i++;
        }
        int idx = 12 * (Integer.parseInt(n.substring(i)) + 1) + pc - 54;
        while (idx < 0) idx += 12;
        while (idx > 24) idx -= 12;
        return idx;
    }

    private static float pitch(int idx) {
        return (float) Math.pow(2, (idx - 12) / 12.0);
    }

    private void saveMuted() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (UUID id : muted) l.add(id.toString());
        y.set("muted", l);
        try {
            y.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Impossible d'ecrire data.yml : " + e.getMessage());
        }
    }

    private boolean listens(Player p) {
        return !muted.contains(p.getUniqueId()) && worlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private List<Player> listeners() {
        List<Player> l = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (listens(p)) l.add(p);
        return l;
    }

    // ------------------------------------------------------------------ séquenceur

    private void tick() {
        if (songs.isEmpty()) return;
        if (current == null) {
            if (pause-- > 0) return;
            start(nextSong());
            return;
        }
        if (tick++ % current.tempo() != 0) return;
        if (step >= current.length()) {
            last = current;
            current = null;
            pause = Math.max(0, getConfig().getInt("gap", 3)) * 20;
            return;
        }
        List<Player> ls = listeners();
        if (!ls.isEmpty()) {
            float master = (float) Math.max(0, Math.min(1, getConfig().getDouble("volume", 0.8)));
            for (Track t : current.tracks()) {
                int[] notes = t.steps()[step % t.steps().length];
                for (int n : notes) {
                    float pitch = pitch(n);
                    for (Player p : ls) p.playSound(p, t.sound(), SoundCategory.RECORDS, t.volume() * master, pitch);
                }
            }
        }
        step++;
    }

    private Song nextSong() {
        if (queue.isEmpty()) {
            List<Song> l = new ArrayList<>(songs);
            if (getConfig().getBoolean("shuffle", true)) {
                Collections.shuffle(l);
                if (l.size() > 1 && l.get(0).equals(last)) Collections.swap(l, 0, l.size() - 1);
            }
            queue.addAll(l);
        }
        return queue.poll();
    }

    private void start(Song s) {
        current = s;
        step = 0;
        tick = 0;
        for (Player p : listeners()) showTitle(p);
    }

    private void showTitle(Player p) {
        if (current == null || !getConfig().getBoolean("show-title", true)) return;
        p.sendActionBar(Component.text("♪ ", NamedTextColor.LIGHT_PURPLE).append(Component.text(current.name(), NamedTextColor.WHITE))
                .append(Component.text(" ♪", NamedTextColor.LIGHT_PURPLE)));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (listens(p)) Bukkit.getScheduler().runTaskLater(this, () -> showTitle(p), 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && listens(p)) showTitle(p);
        }, 60L);
    }

    // ------------------------------------------------------------------ commande

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("eemimusique.admin");
        switch (sub) {
            case "reload" -> {
                if (!admin) return deny(sender);
                loadSettings();
                sender.sendMessage(PREFIX + "§aRecharge : " + songs.size() + " morceaux.");
                return true;
            }
            case "suivant", "skip", "next" -> {
                if (!admin) return deny(sender);
                if (args.length > 1) {
                    for (Song s : songs)
                        if (s.key().equalsIgnoreCase(args[1])) {
                            start(s);
                            sender.sendMessage(PREFIX + "§7Lecture : §f" + s.name());
                            return true;
                        }
                    sender.sendMessage(PREFIX + "§cMorceau inconnu. §7/musique liste");
                    return true;
                }
                start(nextSong());
                sender.sendMessage(PREFIX + "§7Lecture : §f" + current.name());
                return true;
            }
            case "liste", "list" -> {
                sender.sendMessage(PREFIX + "§7" + songs.size() + " morceaux :");
                for (Song s : songs) sender.sendMessage("§8- §f" + s.name() + " §8(" + s.key() + ", "
                        + Math.round(s.length() * s.tempo() / 20.0) + " s)");
                return true;
            }
            case "info" -> {
                sender.sendMessage(PREFIX + (current == null ? "§7Pause entre deux morceaux." : "§7En cours : §f" + current.name()));
                return true;
            }
            default -> { }
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(PREFIX + "§7/musique liste|info|suivant [morceau]|reload");
            return true;
        }
        UUID id = p.getUniqueId();
        boolean off = sub.equals("off") || (!sub.equals("on") && !muted.contains(id));
        if (off) {
            muted.add(id);
            p.sendMessage(PREFIX + "§7Musique coupee. §f/musique on §7pour la remettre.");
        } else {
            muted.remove(id);
            p.sendMessage(PREFIX + "§aMusique activee.");
            showTitle(p);
        }
        saveMuted();
        return true;
    }

    private boolean deny(CommandSender s) {
        s.sendMessage(PREFIX + "§cPermission refusee.");
        return true;
    }
}
