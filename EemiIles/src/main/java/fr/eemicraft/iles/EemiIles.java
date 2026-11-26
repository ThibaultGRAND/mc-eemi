package fr.eemicraft.iles;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Îles EEMICRAFT :
 * - #138 reset d'île SkyBlock = inventaire, coffre de l'Ender et XP remis à zéro (tout de suite ou à la prochaine arrivée) ;
 * - #133 récompense EemiCore tous les N blocs magiques cassés sur une île OneBlock ;
 * - #140 MultiTool du OneBlock (pioche + pelle + hache) qui monte de niveau avec les blocs de l'île.
 */
public final class EemiIles extends JavaPlugin implements Listener {

    Bento bento;
    MultiTool tool;
    private final Set<String> skyWorlds = new HashSet<>();
    private final Set<String> obWorlds = new HashSet<>();

    // données persistantes (data.yml)
    private File dataFile;
    private final Set<UUID> pendingReset = new HashSet<>();
    private final Map<String, Long> paidTier = new HashMap<>();
    private final Map<UUID, long[]> pendingRewards = new HashMap<>();
    /** Joueurs morts avec leur MultiTool : il est rendu à la réapparition dans le OneBlock. */
    private final Set<UUID> giveBack = new HashSet<>();

    private boolean resetHooked, magicHooked;
    private int hookTries;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bento = new Bento(this);
        tool = new MultiTool(this);
        loadSettings();
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        // BentoBox active ses addons (AOneBlock) après le démarrage : on se branche ensuite, avec quelques essais
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            hookTries++;
            hookEvents();
            if ((resetHooked && magicHooked) || hookTries >= 12) {
                task.cancel();
                if (!resetHooked) getLogger().warning("Reset d'ile SkyBlock desactive : BentoBox introuvable (" + bento.bentoError + ")");
                if (!magicHooked) getLogger().warning("Paliers OneBlock et niveaux du MultiTool desactives : AOneBlock introuvable (" + bento.oneblockError + ")");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    refreshTools(p);
                    if (pendingReset.contains(p.getUniqueId()) && isSky(p.getWorld())) scheduleWipe(p);
                }
            }
        }, 40L, 100L);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    void loadSettings() {
        reloadConfig();
        skyWorlds.clear();
        for (String w : getConfig().getStringList("skyblock.mondes")) skyWorlds.add(w.toLowerCase());
        obWorlds.clear();
        for (String w : getConfig().getStringList("oneblock.mondes")) obWorlds.add(w.toLowerCase());
        tool.load(getConfig().getConfigurationSection("multitool"));
    }

    private void hookEvents() {
        if (!resetHooked && bento.hookBento()) {
            listen(bento.resettedEvent, this::onIslandResetted);
            resetHooked = true;
            getLogger().info("Branche sur " + bento.resettedEvent.getSimpleName() + " (BentoBox).");
        }
        if (!magicHooked && bento.hookOneBlock()) {
            listen(bento.magicEvent, this::onMagicBlock);
            magicHooked = true;
            getLogger().info("Branche sur " + bento.magicEvent.getSimpleName() + " (AOneBlock).");
        }
    }

    private void listen(Class<? extends Event> cls, Consumer<Event> handler) {
        getServer().getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, e) -> {
            if (!cls.isInstance(e)) return;
            try {
                handler.accept(e);
            } catch (RuntimeException ex) {
                getLogger().warning("Erreur sur " + cls.getSimpleName() + " : " + ex);
            }
        }, this, false);
    }

    boolean isSky(World w) {
        return w != null && skyWorlds.contains(w.getName().toLowerCase());
    }

    boolean isOneBlock(World w) {
        return w != null && obWorlds.contains(w.getName().toLowerCase());
    }

    private static boolean vanished(Player p) {
        return p.hasMetadata("vanished");
    }

    void eemi(String cmd) {
        if (getServer().getPluginManager().getPlugin("EemiCore") != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        else getLogger().warning("EemiCore absent, commande non lancee : " + cmd);
    }

    // ================================================================== #138 reset d'île SkyBlock

    private void onIslandResetted(Event e) {
        if (!getConfig().getBoolean("skyblock.reset.actif", true)) return;
        Object island = bento.eventIsland(e);
        UUID id = bento.eventPlayer(e);
        if (island == null || id == null || !isSky(bento.islandWorld(island))) return;
        Runnable r = () -> markReset(id);
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(this, r);
    }

    private void markReset(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName();
        // EemiSkyGarde ne doit plus remettre l'inventaire d'avant le reset en revenant au skyblock
        if (name != null && getConfig().getBoolean("skyblock.reset.couper-skygarde", true)
                && getServer().getPluginManager().getPlugin("EemiSkyGarde") != null)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "skygarde oublier " + name + " reset d'ile");
        pendingReset.add(id);
        saveData();
        getLogger().info("Reset d'ile SkyBlock de " + name + " : inventaire, coffre de l'Ender et XP a vider.");
        Player p = op.getPlayer();
        if (p == null) return;
        if (isSky(p.getWorld())) scheduleWipe(p);
        else U.msg(p, U.SKY + "Ton île a été réinitialisée : ton inventaire SkyBlock sera vidé à ta prochaine arrivée.");
    }

    private void scheduleWipe(Player p) {
        UUID id = p.getUniqueId();
        long delay = Math.max(1, getConfig().getLong("skyblock.reset.delai-ticks", 10));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player q = Bukkit.getPlayer(id);
            if (q == null || !pendingReset.contains(id) || !isSky(q.getWorld()) || vanished(q)) return;
            wipe(q);
        }, delay);
    }

    private void wipe(Player p) {
        p.closeInventory();
        p.setItemOnCursor(null);
        p.getInventory().clear();
        p.getEnderChest().clear();
        p.setLevel(0);
        p.setExp(0f);
        p.setTotalExperience(0);
        p.updateInventory();
        pendingReset.remove(p.getUniqueId());
        saveData();
        getLogger().info("Inventaire SkyBlock de " + p.getName() + " vide apres le reset de son ile.");
        p.showTitle(net.kyori.adventure.title.Title.title(U.c("&aNouvelle île"), U.c("&7Tu repars de zéro !")));
        U.msg(p, U.SKY + "Ton île a été réinitialisée : inventaire, coffre de l'Ender et expérience remis à zéro. "
                + "Ton portefeuille et ta banque ne bougent pas.");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    // ================================================================== #133 paliers OneBlock + #140 niveaux

    private void onMagicBlock(Event e) {
        Object island = bento.eventIsland(e);
        UUID breaker = bento.eventPlayer(e);
        if (island == null) return;
        // le compteur est déjà incrémenté par AOneBlock, mais on lit au tick suivant par sécurité
        Bukkit.getScheduler().runTask(this, () -> afterMagicBlock(island, breaker));
    }

    private void afterMagicBlock(Object island, UUID breaker) {
        long count = bento.oneBlockCount(island);
        if (count < 0) return;
        checkTier(island, breaker, count);
        if (getConfig().getBoolean("multitool.actif", true))
            for (UUID m : bento.islandMembers(island)) {
                Player p = Bukkit.getPlayer(m);
                if (p != null && isOneBlock(p.getWorld())) refreshTools(p, count);
            }
    }

    private void checkTier(Object island, UUID breaker, long count) {
        ConfigurationSection c = getConfig().getConfigurationSection("oneblock.paliers");
        if (c == null || !c.getBoolean("actif", true)) return;
        long every = Math.max(1, c.getLong("tous-les", 1000));
        String id = bento.islandId(island);
        if (id == null) return;
        long tier = count / every;
        // première fois qu'on voit l'île : on ne rattrape pas les paliers d'avant l'installation
        long last = paidTier.getOrDefault(id, Math.max(0, (count - 1) / every));
        if (tier <= last) {
            if (!paidTier.containsKey(id)) {
                paidTier.put(id, last);
                saveData();
            }
            return;
        }
        paidTier.put(id, tier);
        saveData();
        long coins = Math.max(0, c.getLong("pieces", 50)), xp = Math.max(0, c.getLong("xp", 100));
        String reached = U.n(tier * every);
        String why = "Palier de &f" + reached + " &7blocs atteint sur ton île";
        if ("membres".equalsIgnoreCase(c.getString("destinataire", "casseur"))) {
            for (UUID m : bento.islandMembers(island)) {
                Player p = Bukkit.getPlayer(m);
                if (p != null) reward(p, coins, xp, why);
            }
        } else {
            UUID to = breaker != null ? breaker : bento.islandOwner(island);
            if (to == null) return;
            Player p = Bukkit.getPlayer(to);
            if (p != null) reward(p, coins, xp, why);
            else {
                long[] r = pendingRewards.computeIfAbsent(to, k -> new long[2]);
                r[0] += coins;
                r[1] += xp;
                saveData();
            }
        }
        for (UUID m : bento.islandMembers(island)) {
            Player p = Bukkit.getPlayer(m);
            if (p != null && !p.getUniqueId().equals(breaker) && "casseur".equalsIgnoreCase(c.getString("destinataire", "casseur")))
                U.msg(p, U.OB + "Ton île a passé les &f" + reached + " &7blocs !");
        }
    }

    private void reward(Player p, long coins, long xp, String why) {
        if (coins > 0) eemi("eemi give " + p.getName() + " coins " + coins);
        if (xp > 0) eemi("eemi give " + p.getName() + " xp " + xp);
        U.msg(p, U.OB + why + " : "
                + (coins > 0 ? "&e+" + coins + " pièces " : "") + (coins > 0 && xp > 0 ? "&7et " : "") + (xp > 0 ? "&b+" + xp + " xp" : "") + "&7 !");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
        getLogger().info("Recompense OneBlock : " + p.getName() + " +" + coins + " pieces, +" + xp + " xp.");
    }

    // ================================================================== #140 MultiTool

    /** Blocs de l'île OneBlock du joueur, ou -1 si inconnu. */
    long countOf(Player p) {
        World w = Bukkit.getWorld(getConfig().getString("oneblock.monde-iles", "oneblock_world"));
        return bento.oneBlockCount(bento.islandOf(w, p.getUniqueId()));
    }

    void refreshTools(Player p) {
        refreshTools(p, isOneBlock(p.getWorld()) ? countOf(p) : -1);
    }

    /** Met l'outil au bon niveau (count -1 = garder le niveau) et le met en sommeil hors du OneBlock ; supprime les doublons. */
    void refreshTools(Player p, long count) {
        if (!getConfig().getBoolean("multitool.actif", true)) return;
        boolean active = isOneBlock(p.getWorld());
        PlayerInventory inv = p.getInventory();
        boolean found = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!tool.isTool(it)) continue;
            if (found) {
                inv.setItem(i, null);
                getLogger().info("MultiTool en double retire a " + p.getName() + ".");
                continue;
            }
            found = true;
            int cur = tool.levelOf(it);
            int target = active && count >= 0 ? tool.levelFor(count) : cur;
            if (target == cur && tool.activeOf(it) == active) continue;
            inv.setItem(i, tool.build(target, active));
            if (active && target > cur) {
                MultiTool.Level lv = tool.levels.get(target);
                p.showTitle(net.kyori.adventure.title.Title.title(U.c("&6MultiTool"), U.c("&7Niveau &e" + lv.nom())));
                U.msg(p, U.OB + "Ton MultiTool passe au niveau &e" + lv.nom() + " &7(" + U.n(lv.blocs()) + " blocs cassés sur ton île) !");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            } else if (active && target < cur) {
                U.msg(p, U.OB + "Ton MultiTool suit ta nouvelle île : niveau &e" + tool.levels.get(target).nom() + "&7.");
            }
        }
    }

    private boolean hasTool(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (tool.isTool(it)) return true;
        for (ItemStack it : p.getEnderChest().getContents()) if (tool.isTool(it)) return true;
        return tool.isTool(p.getItemOnCursor());
    }

    private void giveTool(Player p, boolean quiet) {
        long count = countOf(p);
        int lvl = tool.levelFor(Math.max(0, count));
        ItemStack it = tool.build(lvl, true);
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (!left.isEmpty()) {
            U.msg(p, U.OB + "&cTon inventaire est plein : fais de la place puis refais &f/multitool&c.");
            return;
        }
        if (!quiet) {
            U.msg(p, U.OB + "Voici ton &6MultiTool &7(niveau &e" + tool.levels.get(lvl).nom() + "&7) : il casse comme une pioche, une pelle et une hache.");
            U.msg(p, U.OB + "Il monte de niveau tout seul avec les blocs cassés sur ton île.");
        } else U.msg(p, U.OB + "Ton &6MultiTool &7t'a été rendu.");
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
    }

    // ------------------------------------------------------------------ événements Bukkit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            long[] r = pendingRewards.remove(p.getUniqueId());
            if (r != null) {
                reward(p, r[0], r[1], "Palier de ton île OneBlock atteint pendant ton absence");
                saveData();
            }
            refreshTools(p);
            if (pendingReset.contains(p.getUniqueId()) && isSky(p.getWorld())) scheduleWipe(p);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (pendingReset.contains(p.getUniqueId()) && isSky(p.getWorld())) scheduleWipe(p);
        // Multiverse-Inventories a déjà changé l'inventaire (LOW) ; on laisse passer les objets redonnés par les autres plugins
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) refreshTools(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory()) return;
        if (e.getDrops().removeIf(tool::isTool)) giveBack.add(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!giveBack.remove(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && isOneBlock(p.getWorld()) && !hasTool(p)) giveTool(p, true);
        }, 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!tool.isTool(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        U.msg(e.getPlayer(), U.OB + "&cLe MultiTool ne se jette pas.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        if (!(e.getEntity() instanceof Player p) || !tool.isTool(item.getItemStack())) return;
        if (hasTool(p)) {
            e.setCancelled(true);
            item.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) return;
        boolean clickTop = e.getClickedInventory() == top;
        ItemStack hot = e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                ? e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) : null;
        ItemStack off = e.getClick() == ClickType.SWAP_OFFHAND ? e.getWhoClicked().getInventory().getItemInOffHand() : null;
        boolean bad = (clickTop && (tool.isTool(e.getCursor()) || tool.isTool(hot) || tool.isTool(off)))
                || (!clickTop && e.isShiftClick() && tool.isTool(e.getCurrentItem()));
        if (bad) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) return;
        if (!tool.isTool(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) if (raw < top.getSize()) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame && tool.isTool(e.getPlayer().getInventory().getItem(e.getHand())))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (tool.isTool(e.getPlayerItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("multitool")) {
            if (!(sender instanceof Player p)) {
                U.msg(sender, U.ADMIN + "Commande réservée aux joueurs.");
                return true;
            }
            if (!getConfig().getBoolean("multitool.actif", true)) {
                U.msg(p, U.OB + "&cLe MultiTool est désactivé pour le moment.");
                return true;
            }
            if (!isOneBlock(p.getWorld())) {
                U.msg(p, U.OB + "&cLe MultiTool se récupère dans le OneBlock (&f/ob&c).");
                return true;
            }
            if (hasTool(p)) {
                refreshTools(p);
                U.msg(p, U.OB + "Tu as déjà ton MultiTool (inventaire ou coffre de l'Ender).");
                return true;
            }
            giveTool(p, false);
            return true;
        }
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                loadSettings();
                hookEvents();
                for (Player p : Bukkit.getOnlinePlayers()) refreshTools(p);
                U.msg(sender, U.ADMIN + "Config rechargée (" + tool.levels.size() + " niveaux de MultiTool).");
            }
            case "info" -> {
                U.msg(sender, U.ADMIN + "EemiIles " + getPluginMeta().getVersion());
                U.msg(sender, "&7 BentoBox (reset SkyBlock) : " + (resetHooked ? "&abranché" : "&cnon branché &8(" + bento.bentoError + ")"));
                U.msg(sender, "&7 AOneBlock (paliers, MultiTool) : " + (magicHooked ? "&abranché" : "&cnon branché &8(" + bento.oneblockError + ")"));
                U.msg(sender, "&7 Resets en attente : &f" + pendingReset.size() + " &7; îles OneBlock suivies : &f" + paidTier.size()
                        + " &7; récompenses en attente : &f" + pendingRewards.size());
                U.msg(sender, "&7 Mondes SkyBlock : &f" + skyWorlds + " &7; OneBlock : &f" + obWorlds);
                if (args.length >= 2) {
                    Player t = Bukkit.getPlayerExact(args[1]);
                    if (t == null) U.msg(sender, "&c Joueur non connecté.");
                    else {
                        long c = countOf(t);
                        U.msg(sender, "&7 " + t.getName() + " : île OneBlock &f" + (c < 0 ? "inconnue" : U.n(c) + " blocs")
                                + (c < 0 ? "" : " &7-> niveau &e" + tool.levels.get(tool.levelFor(c)).nom())
                                + " &7; reset en attente : &f" + (pendingReset.contains(t.getUniqueId()) ? "oui" : "non"));
                    }
                }
            }
            default -> U.msg(sender, U.ADMIN + "/iles reload | info [joueur]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("iles") && args.length == 1) return List.of("reload", "info");
        return null;
    }

    // ------------------------------------------------------------------ données

    private void loadData() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String s : y.getStringList("reset-en-attente")) {
            try {
                pendingReset.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // ligne abîmée : ignorée
            }
        }
        ConfigurationSection t = y.getConfigurationSection("paliers-payes");
        if (t != null) for (String k : t.getKeys(false)) paidTier.put(k, t.getLong(k));
        ConfigurationSection r = y.getConfigurationSection("recompenses-en-attente");
        if (r != null) for (String k : r.getKeys(false)) {
            try {
                pendingRewards.put(UUID.fromString(k), new long[]{r.getLong(k + ".pieces"), r.getLong(k + ".xp")});
            } catch (IllegalArgumentException ignored) {
                // ligne abîmée : ignorée
            }
        }
    }

    void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("reset-en-attente", pendingReset.stream().map(UUID::toString).sorted().toList());
        for (Map.Entry<String, Long> e : paidTier.entrySet()) y.set("paliers-payes." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, long[]> e : pendingRewards.entrySet()) {
            y.set("recompenses-en-attente." + e.getKey() + ".pieces", e.getValue()[0]);
            y.set("recompenses-en-attente." + e.getKey() + ".xp", e.getValue()[1]);
        }
        try {
            File tmp = new File(getDataFolder(), "data.yml.tmp");
            y.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde de data.yml impossible : " + ex.getMessage());
        }
    }
}
