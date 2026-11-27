package fr.eemicraft.speedrun;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public final class EemiSpeedrun extends JavaPlugin implements Listener {

    static final String PREFIX = "§6[Speedrun] §f";

    private Names names;
    private final Map<Mode, Set<UUID>> queues = new EnumMap<>(Mode.class);
    private Game game;
    private Mode countdownMode;
    private int countdownLeft;
    private BukkitTask countdownTask;
    private final NamespacedKey replayKey = new NamespacedKey(this, "replay");
    private final NamespacedKey queueKey = new NamespacedKey(this, "quitter_file");
    /** 1.2.0 (#134) : objet « Pret » des joueurs en file, et joueurs qui ont clique dessus. */
    private final NamespacedKey readyKey = new NamespacedKey(this, "pret");
    private final Set<UUID> ready = new java.util.HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        names = new Names(this);
        for (Mode mode : Mode.values()) queues.put(mode, new LinkedHashSet<>());
        getServer().getPluginManager().registerEvents(this, this);
        deleteWorld(getConfig().getString("world-name", "speedrun"));
        getLogger().info(names.itemCount() + " noms francais charges ; objets tirables : "
                + names.tier("facile").size() + " faciles, " + names.tier("moyen").size() + " moyens, "
                + names.tier("difficile").size() + " difficiles ; lettres jouables : "
                + letters() + ".");
        Bukkit.getScheduler().runTaskTimer(this, this::queueTick, 20L, 20L);
        status("waiting", 0, 0);
    }

    @Override
    public void onDisable() {
        if (game != null) game.shutdown();
        game = null;
    }

    Names names() {
        return names;
    }

    private String letters() {
        StringBuilder sb = new StringBuilder();
        for (char c : names.playableLetters(getConfig().getInt("letter-min-items", 3))) sb.append(c);
        return sb.toString();
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "aide" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("eemispeedrun.admin");
        Mode direct = Mode.parse(sub);
        if (direct != null) {
            if (sender instanceof Player p) join(p, direct);
            return true;
        }
        switch (sub) {
            case "join", "rejoindre" -> {
                if (!(sender instanceof Player p)) return true;
                Mode mode = args.length > 1 ? Mode.parse(args[1]) : null;
                if (mode == null) {
                    p.sendMessage(PREFIX + "Choisis un mode : §e/speedrun objet §fou §e/speedrun lettre");
                    return true;
                }
                join(p, mode);
            }
            case "leave", "quitter" -> {
                if (!(sender instanceof Player p)) return true;
                if (game != null && game.involves(p)) {
                    game.leave(p, true);
                } else if (removeFromQueues(p)) {
                    p.sendMessage(PREFIX + "Tu as quitte la file.");
                } else {
                    p.sendMessage(PREFIX + "Tu n'es dans aucune file.");
                }
            }
            case "files", "file" -> sender.sendMessage(PREFIX + "File objet : §e" + queues.get(Mode.OBJET).size()
                    + "§f, file lettre : §e" + queues.get(Mode.LETTRE).size()
                    + (game != null ? "§f. Partie en cours : §e" + game.mode.display + " §f(" + game.playerCount() + " joueurs)" : ""));
            case "spectate", "regarder" -> {
                if (!(sender instanceof Player p)) return true;
                if (game == null || game.state() == Game.State.ENDING) {
                    p.sendMessage(PREFIX + "Aucune partie en cours.");
                } else if (game.isPlayer(p)) {
                    p.sendMessage(PREFIX + "Tu joues deja dans cette partie.");
                } else {
                    removeFromQueues(p);
                    game.addSpectator(p);
                }
            }
            case "start" -> {
                if (!admin) return noPerm(sender);
                Mode mode = args.length > 1 ? Mode.parse(args[1]) : countdownMode;
                if (mode == null) {
                    sender.sendMessage(PREFIX + "/speedrun start <objet|lettre>");
                    return true;
                }
                if (game != null) {
                    sender.sendMessage(PREFIX + "Une partie est deja en cours.");
                    return true;
                }
                if (queues.get(mode).isEmpty() && sender instanceof Player p) queues.get(mode).add(p.getUniqueId());
                if (!launch(mode)) sender.sendMessage(PREFIX + "Personne dans la file " + mode.id + ".");
            }
            case "stop" -> {
                if (!admin) return noPerm(sender);
                if (game == null) {
                    sender.sendMessage(PREFIX + "Aucune partie en cours.");
                } else {
                    game.end(true);
                }
            }
            case "reload" -> {
                if (!admin) return noPerm(sender);
                reloadConfig();
                names = new Names(this);
                sender.sendMessage(PREFIX + "Config rechargee. Lettres jouables : " + letters());
            }
            case "lettres" -> sender.sendMessage(PREFIX + "Lettres jouables : §e" + letters());
            default -> {
                sender.sendMessage(PREFIX + "§eSpeedrun d'objets aleatoires");
                sender.sendMessage("§e/speedrun objet §7- roulette d'objet : le premier qui l'obtient marque");
                sender.sendMessage("§e/speedrun lettre §7- roulette de lettre : un objet dont le nom commence par la lettre");
                sender.sendMessage("§e/speedrun leave §7- quitter la file ou la partie");
                sender.sendMessage("§e/speedrun spectate §7- regarder la partie en cours");
                if (admin) sender.sendMessage("§c/speedrun start <objet|lettre> | stop | reload | lettres");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("objet", "lettre", "leave", "files", "spectate", "lettres"));
            if (sender.hasPermission("eemispeedrun.admin")) base.addAll(List.of("start", "stop", "reload"));
            return base.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("join"))) {
            return List.of("objet", "lettre");
        }
        return List.of();
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(PREFIX + "§cReserve aux admins.");
        return true;
    }

    // ------------------------------------------------------------------ files d'attente

    private void join(Player p, Mode mode) {
        boolean wasQueued = queues.get(mode).contains(p.getUniqueId());
        joinOne(p, mode);
        if (wasQueued || !queues.get(mode).contains(p.getUniqueId())) return;
        // Groupe EemiCore : le chef emmene ses membres dans la meme file (la file est au lobby,
        // EemiCore ne fait suivre le groupe qu'au changement de monde, donc trop tard ici).
        String hub = getConfig().getString("hub.world", "lobby");
        for (Player m : partyMembersIfLeader(p)) {
            if (!m.getWorld().getName().equalsIgnoreCase(hub)) {
                m.sendMessage(PREFIX + "§7" + p.getName() + " a rejoint la file " + mode.display + " : reviens au lobby et clique sur le meme mode pour le suivre.");
                continue;
            }
            if (game != null && game.involves(m)) continue;
            if (queues.get(mode).contains(m.getUniqueId())) continue;
            m.sendMessage(PREFIX + "§7Tu suis le chef du groupe §f" + p.getName() + "§7.");
            joinOne(m, mode);
        }
    }

    /** Membres en ligne du groupe EemiCore dont p est le chef (vide si pas chef, pas de groupe ou EemiCore absent). */
    @SuppressWarnings("unchecked")
    private List<Player> partyMembersIfLeader(Player p) {
        List<Player> out = new ArrayList<>();
        try {
            org.bukkit.plugin.Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
            if (core == null || !core.isEnabled()) return out;
            java.lang.reflect.Field f = core.getClass().getDeclaredField("partyOf");
            f.setAccessible(true);
            Object party = ((Map<UUID, ?>) f.get(core)).get(p.getUniqueId());
            if (party == null) return out;
            java.lang.reflect.Field lf = party.getClass().getDeclaredField("leader");
            java.lang.reflect.Field mf = party.getClass().getDeclaredField("members");
            lf.setAccessible(true);
            mf.setAccessible(true);
            if (!p.getUniqueId().equals(lf.get(party))) return out;
            for (Object o : new ArrayList<>((Collection<?>) mf.get(party))) {
                if (!(o instanceof UUID id) || id.equals(p.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(id);
                if (m != null) out.add(m);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!partyWarned) {
                partyWarned = true;
                getLogger().warning("Groupe EemiCore illisible (" + ex + ") : le groupe ne suivra pas le chef dans la file.");
            }
        }
        return out;
    }

    private boolean partyWarned;

    private void joinOne(Player p, Mode mode) {
        if (game != null && game.involves(p)) {
            if (game.state() == Game.State.ENDING) game.replay(p, mode);
            else p.sendMessage(PREFIX + "Tu es deja dans la partie.");
            return;
        }
        Set<UUID> queue = queues.get(mode);
        if (queue.contains(p.getUniqueId())) {
            p.sendMessage(PREFIX + "Tu es deja dans la file " + mode.display + ". §7/speedrun leave pour la quitter.");
            return;
        }
        int max = getConfig().getInt("max-players", 16);
        if (queue.size() >= max) {
            p.sendMessage(PREFIX + "§cLa file est pleine.");
            return;
        }
        removeFromQueues(p);
        queue.add(p.getUniqueId());
        int min = getConfig().getInt("min-players", 2);
        p.sendMessage(PREFIX + "Tu rejoins la file §e" + mode.display + " §f(" + queue.size() + "/" + max + ")."
                + (game != null ? " §7Une partie est en cours : la tienne commencera apres." : "")
                + (queue.size() < min ? " §7Encore " + (min - queue.size()) + " joueur(s) pour lancer." : ""));
        notifyQueue(mode, "§7" + p.getName() + " rejoint la file (" + queue.size() + "/" + max + ").", p);
        ready.remove(p.getUniqueId());
        giveQueueItem(p);
        giveReadyItem(p);
        status("waiting", totalQueued(), 0);
    }

    private boolean removeFromQueues(Player p) {
        boolean removed = false;
        for (Map.Entry<Mode, Set<UUID>> e : queues.entrySet()) {
            if (e.getValue().remove(p.getUniqueId())) {
                removed = true;
                if (e.getKey() == countdownMode && e.getValue().size() < getConfig().getInt("min-players", 2)) {
                    cancelCountdown("§cPas assez de joueurs, decompte annule.");
                }
            }
        }
        if (removed) {
            ready.remove(p.getUniqueId());
            removeQueueItem(p);
        }
        if (removed && game == null) status("waiting", totalQueued(), 0);
        return removed;
    }

    private int totalQueued() {
        int n = 0;
        for (Set<UUID> q : queues.values()) n += q.size();
        return n;
    }

    /** Chaque seconde : lance le decompte de la file la plus remplie quand elle atteint le minimum. */
    private void queueTick() {
        for (Set<UUID> q : queues.values()) q.removeIf(id -> Bukkit.getPlayer(id) == null);
        ready.removeIf(id -> queueOf(id) == null);
        syncQueueItems();
        if (game != null || countdownTask != null) return;
        int min = getConfig().getInt("min-players", 2);
        Mode best = queues.entrySet().stream()
                .filter(e -> e.getValue().size() >= min)
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey).orElse(null);
        if (best == null) return;
        countdownMode = best;
        countdownLeft = Math.max(5, getConfig().getInt("countdown", 30));
        countdownTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Set<UUID> queue = queues.get(countdownMode);
            if (queue.size() < getConfig().getInt("min-players", 2)) {
                cancelCountdown("§cPas assez de joueurs, decompte annule.");
                return;
            }
            checkReady();
            if (countdownLeft <= 0) {
                // cancelCountdown remet countdownMode a null : garder le mode avant (bug de la 1.0.0).
                Mode mode = countdownMode;
                cancelCountdown(null);
                if (!launch(mode)) notifyQueue(mode, "§cLa partie n'a pas pu demarrer, previens un admin.", null);
                return;
            }
            for (UUID id : queue) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                p.sendActionBar(net.kyori.adventure.text.Component.text("Speedrun (" + countdownMode.display + ") dans "
                        + countdownLeft + " s - " + queue.size() + " joueurs"));
                if (countdownLeft <= 5 || countdownLeft % 10 == 0) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.4f);
                }
            }
            countdownLeft--;
        }, 0L, 20L);
        notifyQueue(best, "La partie commence dans " + countdownLeft + " s !", null);
        status("starting", queues.get(best).size(), 0);
    }

    private void cancelCountdown(String message) {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = null;
        if (message != null && countdownMode != null) notifyQueue(countdownMode, message, null);
        countdownMode = null;
    }

    private boolean launch(Mode mode) {
        if (mode == null) return false;
        List<Player> joined = new ArrayList<>();
        int max = getConfig().getInt("max-players", 16);
        for (UUID id : new ArrayList<>(queues.get(mode))) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && joined.size() < max) joined.add(p);
        }
        if (joined.isEmpty()) return false;
        for (Player p : joined) {
            queues.get(mode).remove(p.getUniqueId());
            ready.remove(p.getUniqueId());
            // avant la teleportation : Multiverse-Inventories ne doit pas garder le lit dans l'inventaire du lobby
            removeQueueItem(p);
        }
        try {
            game = new Game(this, mode, joined);
        } catch (Exception e) {
            getLogger().severe("Impossible de lancer la partie : " + e);
            for (Player p : joined) p.sendMessage(PREFIX + "§cErreur au lancement de la partie, previens un admin.");
            game = null;
            return false;
        }
        return true;
    }

    void gameClosed(Game closed) {
        if (game == closed) game = null;
        status("waiting", totalQueued(), 0);
    }

    /**
     * Fin de partie : les joueurs de la partie (et les spectateurs qui ont clique sur « Rejouer ») sont remis
     * automatiquement dans la file du meme mode, une fois revenus au lobby ; le chef d'un groupe y ramene ses membres.
     */
    void requeueLater(Map<UUID, Mode> replay) {
        if (replay.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String hubWorld = getConfig().getString("hub.world", "lobby");
            for (Map.Entry<UUID, Mode> e : replay.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || !p.getWorld().getName().equals(hubWorld)) continue;
                if (game != null && game.involves(p)) continue;
                Mode mode = e.getValue();
                // joinOne et pas join : le chef ne doit pas remettre en file un membre parti pendant la partie
                // (chaque joueur reste de la partie est lui-meme dans replay)
                if (!queues.get(mode).contains(p.getUniqueId())) joinOne(p, mode);
                if (queues.get(mode).contains(p.getUniqueId())) {
                    giveQueueItem(p);
                    p.sendMessage(PREFIX + "§7Tu restes dans la file de §e" + mode.display
                            + "§7. Clique sur le lit (ou §f/speedrun leave§7) pour quitter la file.");
                }
            }
        }, 5L);
    }

    // ------------------------------------------------------------------ objet « Quitter la file »

    private boolean isHub(Player p) {
        return p.getWorld().getName().equals(getConfig().getString("hub.world", "lobby"));
    }

    private boolean queued(Player p) {
        for (Set<UUID> q : queues.values()) if (q.contains(p.getUniqueId())) return true;
        return false;
    }

    boolean isQueueItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(queueKey, PersistentDataType.BYTE);
    }

    private boolean hasQueueItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isQueueItem(it)) return true;
        return false;
    }

    /** Lit rouge en case 8 (ou la premiere case libre de la barre) pour un joueur en file au lobby. */
    private void giveQueueItem(Player p) {
        if (!isHub(p) || !queued(p) || hasQueueItem(p)) return;
        int slot = -1;
        if (p.getInventory().getItem(8) == null) slot = 8;
        else for (int i = 0; i < 9 && slot < 0; i++) if (p.getInventory().getItem(i) == null) slot = i;
        if (slot < 0) return;
        ItemStack bed = new ItemStack(org.bukkit.Material.RED_BED);
        var meta = bed.getItemMeta();
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize("§c§lQuitter la file §7(clic droit)").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(queueKey, PersistentDataType.BYTE, (byte) 1);
        bed.setItemMeta(meta);
        p.getInventory().setItem(slot, bed);
    }

    private void removeQueueItem(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isQueueItem(contents[i])) p.getInventory().setItem(i, null);
        }
        if (isQueueItem(p.getItemOnCursor())) p.setItemOnCursor(null);
        removeReadyItem(p);
    }

    // ------------------------------------------------------------------ objet « Pret » (1.2.0, #134)

    private Mode queueOf(UUID id) {
        for (Map.Entry<Mode, Set<UUID>> e : queues.entrySet()) if (e.getValue().contains(id)) return e.getKey();
        return null;
    }

    boolean isReadyItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(readyKey, PersistentDataType.BYTE);
    }

    private boolean hasReadyItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isReadyItem(it)) return true;
        return false;
    }

    private ItemStack readyItem(boolean on) {
        ItemStack it = new ItemStack(on ? org.bukkit.Material.LIME_DYE : org.bukkit.Material.GRAY_DYE);
        var meta = it.getItemMeta();
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        meta.displayName(legacy.deserialize(on ? "§a§lPrêt ! §7(clic droit pour annuler)" : "§7Prêt ? §8(clic droit)")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(
                legacy.deserialize(on ? "§7Tu attends les autres joueurs." : "§7Quand toute la file est prête,")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                legacy.deserialize(on ? "§7Clic droit pour annuler." : "§7le départ est avancé.")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(readyKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    /** Colorant « Pret » pour un joueur en file au lobby : case 3, sinon 5 ou 1 (DeluxeHub 0/2/4/6/8, gadget 7). */
    private void giveReadyItem(Player p) {
        if (!isHub(p) || !queued(p) || hasReadyItem(p)) return;
        int slot = -1;
        for (int i : new int[] {3, 5, 1}) {
            if (p.getInventory().getItem(i) == null) {
                slot = i;
                break;
            }
        }
        for (int i = 0; i < 9 && slot < 0; i++) if (p.getInventory().getItem(i) == null) slot = i;
        if (slot < 0) return;
        p.getInventory().setItem(slot, readyItem(ready.contains(p.getUniqueId())));
    }

    private void removeReadyItem(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isReadyItem(contents[i])) p.getInventory().setItem(i, null);
        }
        if (isReadyItem(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    private int readyCount(Mode mode) {
        int n = 0;
        for (UUID id : queues.get(mode)) if (ready.contains(id)) n++;
        return n;
    }

    /** Anti-rebond : un clic sur un bloc envoie deux evenements (bloc puis objet) au meme tick. */
    private static final java.util.Map<java.util.UUID, Long> READY_CLICK = new java.util.HashMap<>();

    private void toggleReady(Player p) {
        long clickAt = System.currentTimeMillis();
        Long lastClick = READY_CLICK.put(p.getUniqueId(), clickAt);
        if (lastClick != null && clickAt - lastClick < 350L) {
            return;
        }
        Mode mode = queueOf(p.getUniqueId());
        if (mode == null) {
            removeReadyItem(p);
            return;
        }
        boolean now = !ready.remove(p.getUniqueId());
        if (now) ready.add(p.getUniqueId());
        ItemStack[] contents = p.getInventory().getContents();
        boolean found = false;
        for (int i = 0; i < contents.length; i++) {
            if (isReadyItem(contents[i])) {
                p.getInventory().setItem(i, readyItem(now));
                found = true;
                break;
            }
        }
        if (!found) giveReadyItem(p);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, now ? 1.6f : 0.9f);
        notifyQueue(mode, "§f" + p.getName() + (now ? " §aest prêt" : " §7n'est plus prêt") + " §7("
                + readyCount(mode) + "/" + queues.get(mode).size() + ")", null);
        checkReady();
    }

    /** Decompte lance et toute la file prete : temps restant ramene a ready-skip-to (0 = desactive). */
    private void checkReady() {
        int skip = Math.max(0, getConfig().getInt("ready-skip-to", 5));
        if (skip <= 0 || countdownTask == null || countdownMode == null) return;
        Set<UUID> queue = queues.get(countdownMode);
        if (queue.isEmpty() || queue.size() < getConfig().getInt("min-players", 2)) return;
        for (UUID id : queue) if (!ready.contains(id)) return;
        if (countdownLeft > skip) {
            countdownLeft = skip;
            notifyQueue(countdownMode, "§aTout le monde est prêt : départ dans §e" + skip + " s§a.", null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReadyItemClick(PlayerInteractEvent e) {
        if (!isReadyItem(e.getItem())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        toggleReady(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReadyItemSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (isReadyItem(e.getMainHandItem()) || isReadyItem(e.getOffHandItem())) e.setCancelled(true);
    }

    /** Chaque seconde : le lit suit l'inscription (donne en file au lobby, retire sinon). */
    private void syncQueueItems() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (queued(p) && isHub(p)) {
                giveQueueItem(p);
                giveReadyItem(p);
            } else if (hasQueueItem(p) || hasReadyItem(p)) {
                removeQueueItem(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeaveHubTeleport(PlayerTeleportEvent e) {
        // retirer le lit AVANT le changement de monde (sinon il reste enregistre dans l'inventaire du lobby)
        if (e.getTo() == null || e.getFrom().getWorld() == e.getTo().getWorld()) return;
        removeQueueItem(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQueueItemClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isQueueItem(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (removeFromQueues(p)) p.sendMessage(PREFIX + "Tu as quitte la file.");
        else removeQueueItem(p);
    }

    NamespacedKey replayKey() {
        return replayKey;
    }

    boolean isReplayItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(replayKey, PersistentDataType.STRING);
    }

    private void notifyQueue(Mode mode, String message, Player except) {
        for (UUID id : queues.get(mode)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p != except) p.sendMessage(PREFIX + message);
        }
    }

    // ------------------------------------------------------------------ evenements

    private void checkSoon(org.bukkit.entity.HumanEntity who) {
        if (game == null || !(who instanceof Player p) || !game.isPlayer(p)) return;
        Bukkit.getScheduler().runTask(this, () -> {
            if (game != null) game.check(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) checkSoon(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        checkSoon(e.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        checkSoon(e.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        checkSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnace(FurnaceExtractEvent e) {
        checkSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        checkSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (game == null || getConfig().getBoolean("pvp", false)) return;
        if (e.getEntity() instanceof Player victim && e.getEntity().getWorld() == game.world()) {
            Player attacker = e.getDamager() instanceof Player p ? p
                    : e.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player s ? s : null;
            if (attacker != null && attacker != victim) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent e) {
        if (game != null && e.getFrom().getWorld() == game.world()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + "§7Pas de Nether ni d'End dans le speedrun.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (game == null || !game.isPlayer(e.getPlayer())) return;
        if (e.getRespawnLocation().getWorld() != game.world()) e.setRespawnLocation(game.spawn());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (game != null && e.getFrom() == game.world() && game.involves(p)) game.leave(p, false);
        // Quitter le lobby fait aussi quitter la file (on est parti jouer ailleurs).
        if (game == null || e.getFrom() != game.world()) {
            String hubWorld = getConfig().getString("hub.world", "lobby");
            if (e.getFrom().getName().equals(hubWorld) && removeFromQueues(p)) {
                p.sendMessage(PREFIX + "§7Tu as quitte le lobby : tu sors de la file du speedrun.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReplayClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isReplayItem(e.getItem())) return;
        e.setCancelled(true);
        Mode mode = Mode.parse(e.getItem().getItemMeta().getPersistentDataContainer().get(replayKey, PersistentDataType.STRING));
        if (mode != null) join(e.getPlayer(), mode);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQueueItemMove(InventoryClickEvent e) {
        if (isQueueItem(e.getCurrentItem()) || isQueueItem(e.getCursor()) || isReplayItem(e.getCurrentItem()) || isReplayItem(e.getCursor())
                || isReadyItem(e.getCurrentItem()) || isReadyItem(e.getCursor())
                || (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    && e.getWhoClicked() instanceof Player hp
                    && (isQueueItem(hp.getInventory().getItem(e.getHotbarButton())) || isReplayItem(hp.getInventory().getItem(e.getHotbarButton()))
                        || isReadyItem(hp.getInventory().getItem(e.getHotbarButton()))))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQueueItemDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (isQueueItem(e.getOldCursor()) || isReplayItem(e.getOldCursor()) || isReadyItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onReplayDrop(PlayerDropItemEvent e) {
        if (isReplayItem(e.getItemDrop().getItemStack()) || isQueueItem(e.getItemDrop().getItemStack())
                || isReadyItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeFromQueues(e.getPlayer());
        if (game != null && game.involves(e.getPlayer())) game.leave(e.getPlayer(), false);
    }

    // ------------------------------------------------------------------ outils

    Location hub() {
        ConfigurationSection s = getConfig().getConfigurationSection("hub");
        World w = s != null ? Bukkit.getWorld(s.getString("world", "lobby")) : null;
        if (w == null) w = Bukkit.getWorlds().get(0);
        if (s == null) return w.getSpawnLocation();
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    /** Statut pour EemiCore (si installe) : eemi status <jeu> <etat> <attente> <en_jeu> <max>. */
    void status(String state, int waiting, int playing) {
        if (Bukkit.getPluginManager().getPlugin("EemiCore") == null) return;
        String id = getConfig().getString("eemicore-id", "speedrun");
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, "eemi status " + id + " " + state + " " + waiting + " " + playing + " "
                + getConfig().getInt("max-players", 16));
    }

    /** Recompense EemiCore : play, win ou kill. */
    void reward(String player, String type) {
        if (player == null || Bukkit.getPluginManager().getPlugin("EemiCore") == null) return;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "eemi reward " + player + " " + getConfig().getString("eemicore-id", "speedrun") + " " + type);
    }

    /** Decharge et supprime le monde de jeu (jamais le monde principal). */
    void deleteWorld(String name) {
        if (name == null || name.isBlank()) return;
        World main = Bukkit.getWorlds().get(0);
        if (name.equalsIgnoreCase(main.getName())) return;
        List<File> folders = new ArrayList<>();
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            for (Player p : loaded.getPlayers()) p.teleport(hub());
            folders.add(loaded.getWorldFolder());
            if (!Bukkit.unloadWorld(loaded, false)) {
                getLogger().warning("Le monde " + name + " n'a pas pu etre decharge, il n'est pas supprime.");
                return;
            }
        }
        folders.add(new File(Bukkit.getWorldContainer(), name));
        folders.add(new File(main.getWorldFolder(), "dimensions/minecraft/" + name));
        for (File folder : folders) {
            if (folder == null || !folder.isDirectory()) continue;
            if (!new File(folder, "region").exists() && !new File(folder, "level.dat").exists()
                    && !new File(folder, "data").exists()) continue;
            try (Stream<Path> walk = Files.walk(folder.toPath())) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException ex) {
                getLogger().warning("Suppression incomplete de " + folder + " : " + ex.getMessage());
            }
        }
    }
}
