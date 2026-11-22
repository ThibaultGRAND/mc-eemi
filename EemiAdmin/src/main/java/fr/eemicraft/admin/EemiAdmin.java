package fr.eemicraft.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Outils admin d'EEMICRAFT.
 * - /vanish : invisible pour les joueurs sans eemiadmin.see, hors tablist, connexion/deconnexion
 *   silencieuses, pas de ramassage, pas cible par les mobs, invulnerable, vol, actionbar « VANISH » ;
 * - un admin en vanish ne peut s'inscrire a aucun mini-jeu (commandes de join bloquees) ;
 * - Loup-Garou : mode spectateur dans le monde lg et mise a l'ecart pendant /lg start ;
 * - /admintp : vanish automatique puis teleportation vers un joueur ;
 * - en vanish, l'inventaire est mis de cote et remplace par les outils admin : boussole des mondes
 *   (mv tp), boussole des joueurs (clic gauche = tp, clic droit = inventaire, shift = coffre de l'Ender),
 *   bouton de sortie du vanish ; l'inventaire est rendu a la sortie du vanish ;
 * - /invsee et /endersee : ouvrir (et modifier) l'inventaire ou le coffre de l'Ender d'un joueur.
 * L'etat de vanish et l'inventaire mis de cote survivent a la reconnexion et au redemarrage (data.yml).
 */
public final class EemiAdmin extends JavaPlugin implements Listener {

    private static final String PREFIX = "§c[Admin] §r";
    private static final String META = "vanished"; // convention lue par beaucoup de plugins (TAB, Essentials...)

    /** UUID des admins en vanish (connectés ou non). */
    private final Set<UUID> vanished = new LinkedHashSet<>();
    /** Mode de jeu d'avant le passage forcé en spectateur (persisté). */
    private final Map<UUID, GameMode> previousMode = new HashMap<>();
    /** Droit de vol d'avant le vanish (mémoire seulement). */
    private final Map<UUID, Boolean> previousFlight = new HashMap<>();

    /**
     * Inventaires mis de côté pendant le vanish (persistés), PAR MONDE : admin → nom du monde où l'inventaire a
     * été pris → objets. Un inventaire n'est rendu que dans le monde d'où il vient : Multiverse-Inventories
     * sépare les inventaires par monde (ou groupe de mondes), rendre celui du skyblock au hub (ou l'inverse)
     * le ferait passer dans le mauvais profil et écraserait celui du monde d'arrivée.
     */
    private final Map<UUID, Map<String, List<ItemStack>>> savedInventory = new HashMap<>();
    /** Clé d'un inventaire de l'ancien format (1.1.1 et avant, sans monde) : rattaché au monde de l'admin au premier usage en vanish. */
    private static final String LEGACY = "*";

    private File dataFile;
    private NamespacedKey toolKey, targetKey;

    /** Menus de l'admin : on les reconnaît à leur porteur. */
    private static final class Menu implements InventoryHolder {
        final String type;
        Inventory inv;

        Menu(String type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toolKey = new NamespacedKey(this, "tool");
        targetKey = new NamespacedKey(this, "target");
        dataFile = new File(getDataFolder(), "data.yml");
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        // cas d'un rechargement : réappliquer aux admins déjà connectés
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isVanished(p)) applyVanish(p);
            else hideVanishedFrom(p);
        }
        // actionbar + entretien de l'état (Multiverse peut remettre mode de jeu / vol en changeant de monde)
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 40L, 40L);
    }

    @Override
    public void onDisable() {
        // on rend tout visible proprement ; l'état reste dans data.yml et sera réappliqué au démarrage
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isVanished(p)) removeEffects(p);
        }
        saveData();
    }

    private void loadData() {
        vanished.clear();
        previousMode.clear();
        savedInventory.clear();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        // ancien format (sans monde) : saved-inventory.<uuid> = liste
        ConfigurationSection inv = data.getConfigurationSection("saved-inventory");
        if (inv != null) {
            for (String k : inv.getKeys(false)) {
                try { stashMap(UUID.fromString(k)).put(LEGACY, items(inv.getList(k, List.of()))); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        // format par monde : saved-inventories.<uuid>.<monde> = liste
        ConfigurationSection invs = data.getConfigurationSection("saved-inventories");
        if (invs != null) {
            for (String k : invs.getKeys(false)) {
                ConfigurationSection byWorld = invs.getConfigurationSection(k);
                if (byWorld == null) continue;
                try {
                    UUID id = UUID.fromString(k);
                    for (String w : byWorld.getKeys(false)) stashMap(id).put(w, items(byWorld.getList(w, List.of())));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        for (String s : data.getStringList("vanished")) {
            try { vanished.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
        }
        ConfigurationSection sec = data.getConfigurationSection("previous-gamemode");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try { previousMode.put(UUID.fromString(k), GameMode.valueOf(sec.getString(k, "SURVIVAL"))); }
                catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID id : vanished) list.add(id.toString());
        data.set("vanished", list);
        for (Map.Entry<UUID, GameMode> e : previousMode.entrySet()) {
            data.set("previous-gamemode." + e.getKey(), e.getValue().name());
        }
        for (Map.Entry<UUID, Map<String, List<ItemStack>>> e : savedInventory.entrySet()) {
            for (Map.Entry<String, List<ItemStack>> w : e.getValue().entrySet()) {
                if (LEGACY.equals(w.getKey())) data.set("saved-inventory." + e.getKey(), w.getValue());
                else data.set("saved-inventories." + e.getKey() + "." + w.getKey(), w.getValue());
            }
        }
        try {
            getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Impossible d'enregistrer data.yml : " + ex.getMessage());
        }
    }

    public boolean isVanished(Player p) {
        return vanished.contains(p.getUniqueId());
    }

    private static List<ItemStack> items(List<?> raw) {
        List<ItemStack> items = new ArrayList<>();
        for (Object o : raw) items.add(o instanceof ItemStack it ? it : null);
        return items;
    }

    private Map<String, List<ItemStack>> stashMap(UUID id) {
        return savedInventory.computeIfAbsent(id, k -> new LinkedHashMap<>());
    }

    /**
     * Inventaire mis de côté dans le monde actuel du joueur (null s'il n'y en a pas). Un inventaire de l'ancien
     * format est rattaché au monde actuel seulement si l'admin est en vanish (comportement de la 1.1.1).
     */
    private List<ItemStack> stashHere(Player p) {
        Map<String, List<ItemStack>> m = savedInventory.get(p.getUniqueId());
        if (m == null) return null;
        String w = p.getWorld().getName();
        if (!m.containsKey(w) && m.containsKey(LEGACY) && isVanished(p)) m.put(w, m.remove(LEGACY));
        return m.get(w);
    }

    private List<ItemStack> takeStashHere(Player p) {
        if (stashHere(p) == null) return null;
        Map<String, List<ItemStack>> m = savedInventory.get(p.getUniqueId());
        List<ItemStack> items = m.remove(p.getWorld().getName());
        if (m.isEmpty()) savedInventory.remove(p.getUniqueId());
        return items;
    }

    // ------------------------------------------------------------------ vanish

    private void setVanished(Player p, boolean on) {
        if (on == isVanished(p)) return;
        if (on) {
            vanished.add(p.getUniqueId());
            previousFlight.put(p.getUniqueId(), p.getAllowFlight());
            applyVanish(p);
            stashAndGiveTools(p);
            p.sendMessage(PREFIX + "§aVanish active : tu es invisible et tu ne seras inscrit a aucun jeu.");
            notifyStaff(p, "§7" + p.getName() + " est passe en vanish.");
        } else {
            stashHere(p); // encore en vanish : rattache un inventaire de l'ancien format au monde actuel
            vanished.remove(p.getUniqueId());
            removeEffects(p);
            restoreInventory(p);
            p.sendMessage(PREFIX + "§eVanish desactive : tu es de nouveau visible.");
            Map<String, List<ItemStack>> left = savedInventory.get(p.getUniqueId());
            if (left != null && !left.isEmpty())
                p.sendMessage(PREFIX + "§7Inventaire encore de cote pour : §f" + String.join(", ", left.keySet())
                        + " §7(rendu a ton retour dans ce monde).");
            notifyStaff(p, "§7" + p.getName() + " n'est plus en vanish.");
        }
        saveData();
    }

    /** Applique tous les effets du vanish (connexion, activation, rechargement). */
    private void applyVanish(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != p && !other.hasPermission("eemiadmin.see")) other.hidePlayer(this, p);
        }
        p.setMetadata(META, new FixedMetadataValue(this, true));
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.setSleepingIgnored(true);
        p.setAllowFlight(true);
        if (getConfig().getBoolean("night-vision", true)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        updateWorldMode(p);
        sendBar(p);
    }

    // ------------------------------------------------------------------ outils admin

    private ItemStack tool(Material m, String id, String name, NamedTextColor color, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String line : lore) l.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private String toolOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
    }

    /** Inventaire de l'admin en vanish : uniquement les outils (les objets du lobby sont retirés). */
    private void giveTools(Player p) {
        p.getInventory().clear();
        p.getInventory().setItem(0, tool(Material.COMPASS, "worlds", "Changer de monde", NamedTextColor.AQUA, "Clic droit : liste des mondes"));
        p.getInventory().setItem(1, tool(Material.RECOVERY_COMPASS, "players", "Joueurs en ligne", NamedTextColor.GOLD,
                "Clic droit : liste des joueurs", "Dans la liste : clic gauche = tp,", "clic droit = inventaire, shift = Ender"));
        p.getInventory().setItem(8, tool(Material.LIME_DYE, "unvanish", "Quitter le vanish", NamedTextColor.GREEN, "Clic droit : redevenir visible"));
        p.getInventory().setHeldItemSlot(0);
    }

    /** Vrai pour tout objet marqué par le plugin (outils du vanish, icônes des menus). */
    private boolean isTool(ItemStack it) {
        return toolOf(it) != null;
    }

    /**
     * Retire tous les outils du vanish : inventaire complet (armure et main secondaire comprises),
     * curseur et grille d'artisanat 2x2 du joueur. Renvoie le nombre d'emplacements nettoyés.
     */
    private int stripTools(Player p) {
        int n = 0;
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (isTool(c[i])) {
                inv.setItem(i, null);
                n++;
            }
        }
        if (isTool(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
            n++;
        }
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            for (int i = 0; i < top.getSize(); i++) {
                if (isTool(top.getItem(i))) {
                    top.setItem(i, null);
                    n++;
                }
            }
        }
        if (n > 0) p.updateInventory();
        return n;
    }

    /**
     * Met l'inventaire réel du monde actuel de côté (sauf s'il l'est déjà pour ce monde : reconnexion, crash)
     * puis donne les outils. Les outils éventuellement présents ne sont jamais mis de côté.
     */
    private void stashAndGiveTools(Player p) {
        p.closeInventory(); // l'objet du curseur revient dans l'inventaire avant la copie
        if (stashHere(p) == null) {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack it : p.getInventory().getContents()) items.add(isTool(it) ? null : it);
            stashMap(p.getUniqueId()).put(p.getWorld().getName(), items);
        }
        giveTools(p);
    }

    /**
     * Rend l'inventaire mis de côté DANS LE MONDE ACTUEL et retire tous les outils (curseur, main secondaire
     * compris). Un inventaire pris dans un autre monde reste de côté : il sera rendu quand l'admin y reviendra.
     */
    private void restoreInventory(Player p) {
        p.closeInventory();
        stripTools(p);
        List<ItemStack> items = takeStashHere(p);
        if (items != null) {
            ItemStack[] arr = new ItemStack[p.getInventory().getSize()];
            for (int i = 0; i < arr.length && i < items.size(); i++) arr[i] = isTool(items.get(i)) ? null : items.get(i);
            p.getInventory().setContents(arr);
        }
        stripTools(p);
        p.updateInventory();
    }

    private void openWorlds(Player p) {
        List<World> worlds = new ArrayList<>(Bukkit.getWorlds());
        Menu m = new Menu("worlds");
        Inventory inv = Bukkit.createInventory(m, Math.min(54, Math.max(9, (worlds.size() + 8) / 9 * 9)), Component.text("Mondes"));
        m.inv = inv;
        for (World w : worlds) {
            Material icon = switch (w.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> w.getName().equals(p.getWorld().getName()) ? Material.LIME_CONCRETE : Material.GRASS_BLOCK;
            };
            ItemStack it = tool(icon, "world:" + w.getName(), w.getName(), NamedTextColor.AQUA,
                    w.getPlayers().size() + " joueur(s)", "Clic : s'y teleporter (mv tp)");
            inv.addItem(it);
            if (inv.firstEmpty() < 0) break;
        }
        p.openInventory(inv);
    }

    private void openPlayers(Player p) {
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.remove(p);
        Menu m = new Menu("players");
        Inventory inv = Bukkit.createInventory(m, Math.min(54, Math.max(9, (list.size() + 8) / 9 * 9)), Component.text("Joueurs en ligne (" + list.size() + ")"));
        m.inv = inv;
        for (Player t : list) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(t);
            double max = t.getAttribute(Attribute.MAX_HEALTH) == null ? 20 : t.getAttribute(Attribute.MAX_HEALTH).getValue();
            meta.displayName(Component.text(t.getName(), isVanished(t) ? NamedTextColor.RED : NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Monde : " + t.getWorld().getName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Mode : " + t.getGameMode().name().toLowerCase(Locale.ROOT) + (isVanished(t) ? " (vanish)" : ""), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Vie : " + Math.round(t.getHealth()) + "/" + Math.round(max), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Clic gauche : tp  -  clic droit : inventaire", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift : coffre de l'Ender", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, t.getUniqueId().toString());
            head.setItemMeta(meta);
            inv.addItem(head);
            if (inv.firstEmpty() < 0) break;
        }
        p.openInventory(inv);
    }

    private void teleportTo(Player p, Player target) {
        if (!isVanished(p)) setVanished(p, true);
        p.teleport(target.getLocation());
        p.sendMessage(PREFIX + "§7Teleporte vers §f" + target.getName() + " §7(" + target.getWorld().getName() + ").");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToolUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String t = toolOf(e.getItem());
        if (t == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!isVanished(p)) { // outil resté par erreur hors vanish : on le retire
            stripTools(p);
            return;
        }
        switch (t) {
            case "worlds" -> openWorlds(p);
            case "players" -> openPlayers(p);
            case "unvanish" -> setVanished(p, false);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // outils : on ne les déplace nulle part (ni dans un coffre, ni en main secondaire, ni par touche 1-9)
        if (clickInvolvesTool(e, p)) {
            e.setCancelled(true);
            if (!isVanished(p)) Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline() && !isVanished(p)) stripTools(p); });
        }
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        if (menu.type.equals("worlds")) {
            String t = toolOf(it);
            if (t == null || !t.startsWith("world:")) return;
            p.closeInventory();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv tp " + p.getName() + " " + t.substring(6));
        } else if (menu.type.equals("players")) {
            String id = it.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
            Player target = id == null ? null : Bukkit.getPlayer(UUID.fromString(id));
            if (target == null) {
                p.sendMessage(PREFIX + "§cCe joueur n'est plus connecte.");
                p.closeInventory();
                return;
            }
            if (e.isShiftClick()) p.openInventory(target.getEnderChest());
            else if (e.isRightClick()) p.openInventory(target.getInventory());
            else {
                p.closeInventory();
                teleportTo(p, target);
            }
        }
    }

    private boolean clickInvolvesTool(InventoryClickEvent e, Player p) {
        if (isTool(e.getCurrentItem()) || isTool(e.getCursor())) return true;
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                && isTool(p.getInventory().getItem(e.getHotbarButton()))) return true;
        return e.getClick() == ClickType.SWAP_OFFHAND && isTool(p.getInventory().getItemInOffHand());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || isTool(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolDrop(PlayerDropItemEvent e) {
        if (isTool(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolSwap(PlayerSwapHandItemsEvent e) {
        if (isTool(e.getMainHandItem()) || isTool(e.getOffHandItem())) e.setCancelled(true);
    }

    /** Pas d'outil posé dans un cadre, donné à un animal, etc. */
    @EventHandler(ignoreCancelled = true)
    public void onToolOnEntity(PlayerInteractEntityEvent e) {
        if (isTool(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolOnArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isTool(e.getPlayerItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onToolDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isTool);
    }

    /** Retire les effets du vanish (sans toucher à la liste des admins en vanish). */
    private void removeEffects(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != p) other.showPlayer(this, p);
        }
        p.removeMetadata(META, this);
        p.setInvulnerable(false);
        p.setCollidable(true);
        p.setSleepingIgnored(false);
        if (getConfig().getBoolean("night-vision", true)) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        restoreMode(p);
        GameMode gm = p.getGameMode();
        boolean flight = previousFlight.getOrDefault(p.getUniqueId(), gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
        previousFlight.remove(p.getUniqueId());
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) flight = true;
        p.setAllowFlight(flight);
        if (!flight) {
            p.setFlying(false);
            p.setFallDistance(0f);
        }
        p.sendActionBar(Component.empty());
    }

    /** Cache les admins en vanish à un joueur qui n'a pas la permission de les voir. */
    private void hideVanishedFrom(Player viewer) {
        if (viewer.hasPermission("eemiadmin.see")) return;
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (v != viewer && isVanished(v)) viewer.hidePlayer(this, v);
        }
    }

    private void sendBar(Player p) {
        p.sendActionBar(Component.text("§c§lVANISH §7- invisible, inscrit a aucun jeu"));
    }

    private void notifyStaff(Player about, String msg) {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o != about && o.hasPermission("eemiadmin.see")) o.sendMessage(PREFIX + msg);
        }
        getLogger().info(org.bukkit.ChatColor.stripColor(msg));
    }

    /** Toutes les 2 s : actionbar + remise en état si un autre plugin a changé vol / mode de jeu. */
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isVanished(p)) {
                stripTools(p); // filet de sécurité : aucun outil hors vanish
                continue;
            }
            sendBar(p);
            if (!p.getAllowFlight()) p.setAllowFlight(true);
            updateWorldMode(p);
        }
    }

    // ------------------------------------------------------------------ mode spectateur (monde lg...)

    private boolean isSpectatorWorld(World w) {
        for (String s : getConfig().getStringList("spectator-in-worlds")) {
            if (s.equalsIgnoreCase(w.getName())) return true;
        }
        return false;
    }

    /** Spectateur dans les mondes configurés, mode précédent rendu ailleurs. */
    private void updateWorldMode(Player p) {
        UUID id = p.getUniqueId();
        if (isVanished(p) && isSpectatorWorld(p.getWorld())) {
            if (!previousMode.containsKey(id)) {
                previousMode.put(id, p.getGameMode());
                saveData();
            }
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
        } else {
            restoreMode(p);
        }
    }

    private void restoreMode(Player p) {
        GameMode before = previousMode.remove(p.getUniqueId());
        if (before == null) return;
        if (p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(before);
        if (isVanished(p)) p.setAllowFlight(true);
        saveData();
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("vanish")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
                listVanished(sender);
                return true;
            }
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§cCommande reservee aux joueurs (/vanish list marche en console).");
                return true;
            }
            setVanished(p, !isVanished(p));
            return true;
        }
        if (name.equals("admintp")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§cCommande reservee aux joueurs.");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(PREFIX + "§7Usage : /admintp <joueur>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getPlayer(args[0]);
            if (target == null || target == p) {
                p.sendMessage(PREFIX + "§cJoueur introuvable : " + args[0]);
                return true;
            }
            teleportTo(p, target);
            return true;
        }
        if (name.equals("invsee") || name.equals("endersee")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§cCommande reservee aux joueurs.");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(PREFIX + "§7Usage : /" + name + " <joueur>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(PREFIX + "§cJoueur introuvable : " + args[0]);
                return true;
            }
            p.openInventory(name.equals("invsee") ? target.getInventory() : target.getEnderChest());
            return true;
        }
        return false;
    }

    private void listVanished(CommandSender sender) {
        List<String> online = new ArrayList<>();
        int offline = 0;
        for (UUID id : vanished) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) online.add(p.getName() + " §8(" + p.getWorld().getName() + ")§f");
            else offline++;
        }
        if (online.isEmpty() && offline == 0) {
            sender.sendMessage(PREFIX + "§7Aucun admin en vanish.");
            return;
        }
        sender.sendMessage(PREFIX + "§7En vanish (" + online.size() + ") : §f" + (online.isEmpty() ? "-" : String.join("§7, §f", online)));
        if (offline > 0) sender.sendMessage(PREFIX + "§7+ " + offline + " hors ligne (repasseront en vanish a la connexion).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("vanish")) {
            if (args.length == 1 && "list".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("list");
            return List.of();
        }
        return null; // /admintp : noms des joueurs
    }

    // ------------------------------------------------------------------ commandes filtrées

    /** "/Murder:Join  x" -> "join x" précédé du nom de commande sans espace de noms. */
    private static String normalize(String raw) {
        String s = raw.startsWith("/") ? raw.substring(1) : raw;
        s = s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        int space = s.indexOf(' ');
        String first = space < 0 ? s : s.substring(0, space);
        int colon = first.indexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        return s;
    }

    private static boolean matches(String cmd, List<String> entries) {
        for (String e : entries) {
            String n = normalize(e);
            if (n.isEmpty()) continue;
            if (cmd.equals(n) || cmd.startsWith(n + " ")) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onJoinCommand(PlayerCommandPreprocessEvent e) {
        if (!isVanished(e.getPlayer())) return;
        if (matches(normalize(e.getMessage()), getConfig().getStringList("blocked-commands"))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + "§cTu es en vanish : /vanish pour jouer.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLgStartPlayer(PlayerCommandPreprocessEvent e) {
        guardLgStart(e.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLgStartConsole(ServerCommandEvent e) {
        guardLgStart(e.getCommand());
    }

    /**
     * LoupGarou (startGame) prend tous les joueurs en ligne du bon monde dans la région, sans regarder
     * le mode de jeu : on écarte les admins en vanish hors de la région (au-dessus de la hauteur max)
     * pendant l'exécution de la commande, puis on les ramène au tick suivant.
     */
    private void guardLgStart(String raw) {
        ConfigurationSection g = getConfig().getConfigurationSection("lg-start-guard");
        if (g == null || !g.getBoolean("enabled", true)) return;
        if (!matches(normalize(raw), g.getStringList("commands"))) return;
        World w = Bukkit.getWorld(g.getString("world", "lg"));
        if (w == null) return;
        Map<Player, Location> parked = new LinkedHashMap<>();
        for (Player p : w.getPlayers()) {
            if (!isVanished(p)) continue;
            Location back = p.getLocation();
            Location up = back.clone();
            up.setY(w.getMaxHeight() + 100);
            if (p.teleport(up)) parked.put(p, back);
        }
        if (parked.isEmpty()) return;
        Bukkit.getScheduler().runTask(this, () -> {
            for (Map.Entry<Player, Location> en : parked.entrySet()) {
                Player p = en.getKey();
                if (p.isOnline() && p.getWorld().equals(w)) p.teleport(en.getValue());
            }
        });
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        hideVanishedFrom(p);
        if (!isVanished(p)) {
            // nettoyage de sécurité (tout de suite, puis après Multiverse-Inventories / DeluxeHub) ; un inventaire
            // resté de côté pour ce monde est rendu après le chargement du profil par Multiverse-Inventories
            stripTools(p);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) restorePending(p); }, 10L);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && !isVanished(p)) stripTools(p); }, 10L);
            return;
        }
        {
            e.joinMessage(null);
            previousFlight.put(p.getUniqueId(), p.getAllowFlight());
            applyVanish(p);
            // inventaire réel mis de côté s'il ne l'est pas déjà (data.yml de la 1.0.0 : rien de sauvé)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && isVanished(p)) {
                    stashAndGiveTools(p);
                    saveData();
                }
            }, 10L);
            p.sendMessage(PREFIX + "§7Tu es toujours en vanish (/vanish pour redevenir visible).");
            notifyStaff(p, "§7" + p.getName() + " s'est connecte en vanish.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!isVanished(p)) return;
        e.quitMessage(null);
        // le mode précédent reste dans data.yml : rendu au retour si l'admin n'est plus dans le monde lg
        previousFlight.remove(p.getUniqueId());
        notifyStaff(p, "§7" + p.getName() + " s'est deconnecte (vanish).");
    }

    /**
     * Multiverse-Inventories enregistre l'inventaire du joueur dans le profil du monde qu'il quitte et
     * charge celui du monde d'arrivée. Avant la 1.1.1, l'admin en vanish partait avec les outils : ils
     * étaient enregistrés dans le profil du lobby, puis rechargés au retour au lobby une fois le vanish
     * quitté (d'où les boussoles qui restaient), et l'inventaire rendu à la sortie était celui d'un autre
     * monde. On rend donc l'inventaire réel AVANT de changer de monde (LOWEST : avant les autres plugins)
     * et on le remet de côté une fois arrivé.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCrossWorldTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!isVanished(p) || e.getTo() == null) return;
        World from = e.getFrom().getWorld(), to = e.getTo().getWorld();
        if (from == null || to == null || from.equals(to)) return;
        if (stashHere(p) == null) return;
        restoreInventory(p);
        saveData();
        // téléportation annulée ensuite par un autre plugin : on remet les outils
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline() && isVanished(p) && p.getWorld().equals(from)) {
                stashAndGiveTools(p);
                saveData();
            }
        });
    }

    /**
     * Portail (Nether, End) : PlayerPortalEvent a sa propre liste d'écouteurs, il ne passe pas par
     * onCrossWorldTeleport. Même traitement : inventaire réel rendu avant de changer de monde.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCrossWorldPortal(PlayerPortalEvent e) {
        onCrossWorldTeleport(e);
    }

    /**
     * Inventaire mis de côté dans ce monde alors que l'admin n'est plus en vanish (il avait quitté ce monde sans
     * passer par une téléportation : mort, portail...) : rendu maintenant qu'il y est revenu. Multiverse-Inventories
     * a chargé le profil de ce monde avant (priorité LOW), qui ne contient que les outils.
     */
    private void restorePending(Player p) {
        if (isVanished(p) || stashHere(p) == null) return;
        restoreInventory(p);
        saveData();
        p.sendMessage(PREFIX + "§7Inventaire mis de cote pendant le vanish rendu (monde " + p.getWorld().getName() + ").");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!isVanished(p)) {
            restorePending(p);
            // filet de sécurité : un profil Multiverse-Inventories peut encore contenir des outils
            stripTools(p);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && !isVanished(p)) stripTools(p); }, 10L);
            return;
        }
        // Multiverse impose le mode de jeu du monde juste après, DeluxeHub donne ses objets :
        // quelques ticks plus tard, inventaire du monde d'arrivée mis de côté puis outils
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline() && isVanished(p)) {
                updateWorldMode(p);
                p.setAllowFlight(true);
                stashAndGiveTools(p);
                saveData();
            }
        }, 10L);
        updateWorldMode(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && isVanished(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onPhysical(PlayerInteractEvent e) {
        // pas de plaques de pression / fils déclenchés par un admin invisible
        if (e.getAction() == Action.PHYSICAL && isVanished(e.getPlayer())) e.setCancelled(true);
    }
}
