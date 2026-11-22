package fr.eemicraft.skygarde;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Coffre-fort de l'inventaire du skyblock.
 *
 * Les inventaires sont séparés par Multiverse-Inventories, mais un mini-jeu qui vide l'inventaire
 * au mauvais moment (avant de téléporter, ou après l'arrivée dans un autre monde) efface le stuff
 * du skyblock. Ici : en quittant le skyblock, l'état complet du joueur est sauvegardé sur disque ;
 * en y revenant depuis un autre monde, il est remis tel quel, après les autres plugins.
 */
public final class EemiSkyGarde extends JavaPlugin implements Listener {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[SkyGarde] " + ChatColor.GRAY;

    /** État complet d'un joueur au skyblock. */
    private record Snapshot(long time, String reason, String inv, String ender, int level, float exp,
                            int total, double health, int food, float saturation, int items) {
    }

    private final Set<String> worlds = new HashSet<>();
    /** Copie prise juste avant une commande tapée dans le skyblock. */
    private final Map<UUID, Snapshot> beforeCommand = new HashMap<>();
    /** Dernière sauvegarde « au départ du skyblock » (horodatage), pour repérer les départs sans téléportation. */
    private final Map<UUID, Long> leftAt = new HashMap<>();
    private File dir;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dir = new File(getDataFolder(), "players");
        dir.mkdirs();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        long every = getConfig().getLong("autosave", 60) * 20L;
        if (every > 0) Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers())
                if (inSky(p) && !vanished(p)) saveLast(p, snapshot(p, "auto"), false);
        }, every, every);
        getLogger().info("Mondes proteges : " + worlds);
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (inSky(p) && !vanished(p)) saveLast(p, snapshot(p, "arret"), false);
    }

    private void loadSettings() {
        reloadConfig();
        worlds.clear();
        for (String w : getConfig().getStringList("worlds")) worlds.add(w.toLowerCase());
    }

    private boolean isSky(World w) {
        return w != null && worlds.contains(w.getName().toLowerCase());
    }

    private boolean inSky(Player p) {
        return isSky(p.getWorld());
    }

    private boolean vanished(Player p) {
        return p.hasMetadata("vanished");
    }

    // ------------------------------------------------------------------ événements

    /** Commande tapée dans le skyblock : copie avant qu'un jeu ne vide l'inventaire puis téléporte. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (inSky(p) && !vanished(p)) beforeCommand.put(p.getUniqueId(), snapshot(p, "commande " + e.getMessage().split(" ")[0]));
    }

    /** Départ du skyblock par téléportation : sauvegarde avant tout autre plugin. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (e.getTo() == null || !isSky(e.getFrom().getWorld()) || isSky(e.getTo().getWorld()) || vanished(p)) return;
        Snapshot now = snapshot(p, "depart vers " + e.getTo().getWorld().getName());
        Snapshot before = beforeCommand.remove(p.getUniqueId());
        long window = getConfig().getLong("command-window-ms", 3000);
        // inventaire déjà vidé par la commande qui téléporte : on garde la copie d'avant la commande
        if (now.items() == 0 && before != null && before.items() > 0 && now.time() - before.time() <= window) {
            now = before;
            getLogger().info("Inventaire skyblock de " + p.getName() + " vide au depart : copie d'avant la commande gardee (" + before.items() + " objets).");
        }
        saveLast(p, now, true);
        leftAt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Mort au skyblock : la sauvegarde précédente n'est plus valable (les objets sont tombés). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (inSky(p) && !e.getKeepInventory()) forget(p.getUniqueId(), "mort au skyblock");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean fromSky = isSky(e.getFrom()), toSky = inSky(p);
        if (fromSky && !toSky) {
            // départ sans téléportation (réapparition, portail…) : pas de sauvegarde sûre, on ne restaurera pas
            Long t = leftAt.remove(p.getUniqueId());
            if (t == null || System.currentTimeMillis() - t > 2000) forget(p.getUniqueId(), "depart sans sauvegarde");
            return;
        }
        if (!fromSky && toSky && !vanished(p)) {
            UUID id = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && inSky(p) && !vanished(p)) restore(p, id);
            }, Math.max(1, getConfig().getLong("restore-delay", 3)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        beforeCommand.remove(p.getUniqueId());
        leftAt.remove(p.getUniqueId());
        if (inSky(p) && !vanished(p)) saveLast(p, snapshot(p, "deconnexion"), false);
    }

    // ------------------------------------------------------------------ sauvegarde / restauration

    private Snapshot snapshot(Player p, String reason) {
        ItemStack[] inv = p.getInventory().getContents();
        int items = 0;
        for (ItemStack it : inv) if (it != null && !it.isEmpty()) items++;
        for (ItemStack it : p.getEnderChest().getContents()) if (it != null && !it.isEmpty()) items++;
        return new Snapshot(System.currentTimeMillis(), reason, encode(inv), encode(p.getEnderChest().getContents()),
                p.getLevel(), p.getExp(), p.getTotalExperience(), p.getHealth(), p.getFoodLevel(), p.getSaturation(), items);
    }

    private static String encode(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = items[i] == null ? ItemStack.empty() : items[i];
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(copy));
    }

    private static ItemStack[] decode(String s) {
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    /** Écrit la sauvegarde courante ; history = l'ajouter aussi à l'historique. */
    private void saveLast(Player p, Snapshot s, boolean history) {
        File f = file(p.getUniqueId());
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("name", p.getName());
        write(y.createSection("last"), s);
        y.set("restore", true);
        if (history) {
            List<Map<?, ?>> list = new ArrayList<>(y.getMapList("history"));
            Map<String, Object> m = new HashMap<>();
            write(m, s);
            list.add(0, m);
            int max = Math.max(1, getConfig().getInt("history", 10));
            while (list.size() > max) list.remove(list.size() - 1);
            y.set("history", list);
        }
        try {
            y.save(f);
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde impossible pour " + p.getName() + " : " + ex.getMessage());
        }
    }

    private static void write(ConfigurationSection c, Snapshot s) {
        Map<String, Object> m = new HashMap<>();
        write(m, s);
        for (Map.Entry<String, Object> e : m.entrySet()) c.set(e.getKey(), e.getValue());
    }

    private static void write(Map<String, Object> m, Snapshot s) {
        m.put("time", s.time());
        m.put("reason", s.reason());
        m.put("inv", s.inv());
        m.put("ender", s.ender());
        m.put("level", s.level());
        m.put("exp", (double) s.exp());
        m.put("total", s.total());
        m.put("health", s.health());
        m.put("food", s.food());
        m.put("saturation", (double) s.saturation());
        m.put("items", s.items());
    }

    private static Snapshot read(Map<?, ?> m) {
        return new Snapshot(num(m.get("time")).longValue(), String.valueOf(m.get("reason")), String.valueOf(m.get("inv")),
                String.valueOf(m.get("ender")), num(m.get("level")).intValue(), num(m.get("exp")).floatValue(),
                num(m.get("total")).intValue(), num(m.get("health")).doubleValue(), num(m.get("food")).intValue(),
                num(m.get("saturation")).floatValue(), num(m.get("items")).intValue());
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : 0;
    }

    /** Plus de restauration automatique tant qu'une nouvelle sauvegarde sûre n'a pas été faite. */
    private void forget(UUID id, String why) {
        File f = file(id);
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        if (!y.getBoolean("restore", false)) return;
        y.set("restore", false);
        y.set("restore-off-reason", why);
        try {
            y.save(f);
        } catch (IOException ignored) {
            // la prochaine sauvegarde réécrira le fichier
        }
    }

    /** Arrivée au skyblock : remet la dernière sauvegarde sûre. */
    private void restore(Player p, UUID id) {
        File f = file(id);
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection last = y.getConfigurationSection("last");
        if (last == null || !y.getBoolean("restore", false)) return;
        apply(p, read(last.getValues(false)));
        // une seule restauration par départ : la suivante attend une nouvelle sauvegarde au départ
        y.set("restore", false);
        y.set("restore-off-reason", "restauree");
        try {
            y.save(f);
        } catch (IOException ignored) {
            // sans conséquence : la prochaine sauvegarde réécrit le fichier
        }
    }

    private void apply(Player p, Snapshot s) {
        try {
            ItemStack[] inv = decode(s.inv());
            ItemStack[] ender = decode(s.ender());
            p.getInventory().setContents(inv);
            p.getEnderChest().setContents(ender);
            p.setLevel(s.level());
            p.setExp(Math.max(0f, Math.min(1f, s.exp())));
            p.setTotalExperience(s.total());
            if (s.health() > 0) p.setHealth(Math.min(s.health(), p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            p.setFoodLevel(s.food());
            p.setSaturation(s.saturation());
            p.updateInventory();
            getLogger().info("Inventaire skyblock remis a " + p.getName() + " (" + s.items() + " objets, " + s.reason() + ").");
        } catch (RuntimeException ex) {
            getLogger().warning("Restauration impossible pour " + p.getName() + " : " + ex);
        }
    }

    // ------------------------------------------------------------------ commandes admin

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + "/skygarde liste <joueur> | restaurer <joueur> <n> | sauver <joueur> | reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                loadSettings();
                sender.sendMessage(PREFIX + "Config rechargee : " + worlds);
            }
            case "liste", "list" -> {
                if (args.length < 2) return usage(sender);
                UUID id = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                YamlConfiguration y = YamlConfiguration.loadConfiguration(file(id));
                List<Map<?, ?>> list = y.getMapList("history");
                if (list.isEmpty()) {
                    sender.sendMessage(PREFIX + "Aucune sauvegarde pour " + args[1] + ".");
                    return true;
                }
                SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm:ss");
                sender.sendMessage(PREFIX + "Sauvegardes de " + args[1] + " (restauration auto : "
                        + (y.getBoolean("restore") ? "oui" : "non, " + y.getString("restore-off-reason", "?")) + ") :");
                for (int i = 0; i < list.size(); i++) {
                    Snapshot s = read(list.get(i));
                    sender.sendMessage(ChatColor.YELLOW + " " + (i + 1) + ". " + ChatColor.WHITE + fmt.format(new Date(s.time()))
                            + ChatColor.GRAY + " - " + s.items() + " objets, niveau " + s.level() + " (" + s.reason() + ")");
                }
            }
            case "restaurer", "restore" -> {
                if (args.length < 3) return usage(sender);
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null) {
                    sender.sendMessage(PREFIX + "Le joueur doit etre connecte.");
                    return true;
                }
                if (!inSky(p)) {
                    sender.sendMessage(PREFIX + p.getName() + " doit etre dans le skyblock (sinon l'inventaire irait dans un autre monde).");
                    return true;
                }
                List<Map<?, ?>> list = YamlConfiguration.loadConfiguration(file(p.getUniqueId())).getMapList("history");
                int n;
                try {
                    n = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    return usage(sender);
                }
                if (n < 1 || n > list.size()) {
                    sender.sendMessage(PREFIX + "Numero entre 1 et " + list.size() + ".");
                    return true;
                }
                // l'inventaire actuel est gardé dans l'historique avant d'être remplacé
                saveLast(p, snapshot(p, "avant restauration manuelle"), true);
                apply(p, read(list.get(n - 1)));
                sender.sendMessage(PREFIX + "Sauvegarde " + n + " remise a " + p.getName() + ".");
            }
            case "sauver", "save" -> {
                if (args.length < 2) return usage(sender);
                Player p = Bukkit.getPlayerExact(args[1]);
                if (p == null || !inSky(p)) {
                    sender.sendMessage(PREFIX + "Le joueur doit etre connecte et dans le skyblock.");
                    return true;
                }
                saveLast(p, snapshot(p, "manuelle"), true);
                sender.sendMessage(PREFIX + "Inventaire de " + p.getName() + " sauvegarde.");
            }
            default -> {
                return usage(sender);
            }
        }
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(PREFIX + "/skygarde liste <joueur> | restaurer <joueur> <n> | sauver <joueur> | reload");
        return true;
    }
}
