package fr.eemicraft.stagiaires;

import com.artillexstudios.axminions.api.AxMinionsAPI;
import com.artillexstudios.axminions.api.events.PreMinionPickupEvent;
import com.artillexstudios.axminions.api.minions.Minion;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stockage des stagiaires (AxMinions) et XP AuraSkills au retrait.
 *
 * AxMinions 1.0.21 n'a pas d'inventaire propre : le stagiaire remplit le coffre relié
 * (champ privé linkedInventory de com.artillexstudios.axminions.minions.Minion), sinon il jette
 * au sol (addToContainerOrDrop). On pose dans ce champ, pour les stagiaires sans coffre, un
 * inventaire de 9 cases à nous : AxMinions le remplit et s'arrête avec « coffre plein » quand il
 * n'y a plus de case libre (firstEmpty == -1, testé dans chaque type avant de travailler).
 * Le stockage s'ouvre par le bouton « stockage » ajouté au menu AxMinions (pdc axminions:gui_item).
 * On ne peut qu'en retirer ; chaque objet retiré donne ratio-xp × l'XP qu'aurait gagnée le joueur.
 */
public final class EemiStagiaires extends JavaPlugin implements Listener {

    private static final String BUTTON = "stockage";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Map<String, String> SKILL_NAMES = Map.of(
            "mining", "Minage", "foraging", "Bucheronnage", "farming", "Agriculture",
            "fishing", "Peche", "fighting", "Combat", "excavation", "Excavation");

    private final Map<String, Inventory> stores = new HashMap<>();
    private final NamespacedKey guiKey = NamespacedKey.fromString("axminions:gui_item");
    private File dataFile;
    private YamlConfiguration data;
    private Field linkedField;
    private boolean injectionBroken;

    /** Porteur de l'inventaire de stockage : clé de position du stagiaire et son type. */
    private static final class StorageHolder implements InventoryHolder {
        final String key;
        final String type;
        Inventory inventory;

        StorageHolder(String key, String type) {
            this.key = key;
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "stockage.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 40L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::save, 1200L, 1200L);
        getLogger().info(data.getConfigurationSection("stockages") == null ? "Aucun stockage enregistre."
                : data.getConfigurationSection("stockages").getKeys(false).size() + " stockages enregistres.");
    }

    @Override
    public void onDisable() {
        // On rend aux stagiaires leur état d'origine (pas de coffre) avant de garder les objets.
        for (Minion m : minions()) {
            Inventory inv = stores.get(key(m));
            if (inv != null && m.getLinkedInventory() == inv) setLinked(m, null);
        }
        for (Inventory inv : stores.values()) {
            for (HumanEntity h : new ArrayList<>(inv.getViewers())) h.closeInventory();
        }
        save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aEemiStagiaires recharge.");
            return true;
        }
        sender.sendMessage("§7/stagiaires reload");
        return true;
    }

    // ---------------------------------------------------------------- stagiaires

    private List<Minion> minions() {
        try {
            return new ArrayList<>(AxMinionsAPI.Companion.getINSTANCE().getMinions());
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static String key(Minion m) {
        Location l = m.getLocation();
        return (l.getWorld() == null ? "?" : l.getWorld().getName()) + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private boolean handled(Minion m) {
        return getConfig().isConfigurationSection("types." + m.getType().getName());
    }

    private void tick() {
        if (!injectionBroken) {
            for (Minion m : minions()) {
                if (!handled(m) || m.getLinkedChest() != null) continue;
                Inventory inv = store(key(m), m.getType().getName());
                if (m.getLinkedInventory() != inv) setLinked(m, inv);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) refreshButton(p);
    }

    private void setLinked(Minion m, Inventory inv) {
        try {
            if (linkedField == null) {
                for (Class<?> c = m.getClass(); c != null && linkedField == null; c = c.getSuperclass()) {
                    try {
                        linkedField = c.getDeclaredField("linkedInventory");
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (linkedField == null) throw new NoSuchFieldException("linkedInventory");
                linkedField.setAccessible(true);
            }
            linkedField.set(m, inv);
        } catch (ReflectiveOperationException | RuntimeException e) {
            injectionBroken = true;
            getLogger().severe("Impossible de donner un stockage aux stagiaires (AxMinions a change ?) : " + e);
        }
    }

    // ---------------------------------------------------------------- stockage

    private Inventory store(String key, String type) {
        Inventory inv = stores.get(key);
        if (inv != null) return inv;
        int size = Math.max(9, Math.min(54, getConfig().getInt("taille", 9) / 9 * 9));
        StorageHolder holder = new StorageHolder(key, type);
        inv = Bukkit.createInventory(holder, size, LEGACY.deserialize(getConfig().getString("titre-stockage", "Stockage du stagiaire")));
        holder.inventory = inv;
        String saved = data.getString("stockages." + key + ".objets");
        if (saved != null) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(saved));
            List<ItemStack> overflow = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (items[i] == null || items[i].isEmpty()) continue;
                if (i < size) inv.setItem(i, items[i]);
                else overflow.add(items[i]);
            }
            for (ItemStack it : overflow) inv.addItem(it);
        }
        stores.put(key, inv);
        return inv;
    }

    private void save() {
        for (Map.Entry<String, Inventory> e : stores.entrySet()) {
            String path = "stockages." + e.getKey();
            ItemStack[] contents = e.getValue().getContents();
            ItemStack[] copy = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? ItemStack.empty() : contents[i];
            data.set(path + ".type", ((StorageHolder) e.getValue().getHolder()).type);
            data.set(path + ".objets", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(copy)));
        }
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Sauvegarde de stockage.yml impossible : " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- bouton du menu AxMinions

    private void refreshButton(Player p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof Minion m)) return;
        Inventory inv = handled(m) || stores.containsKey(key(m)) ? store(key(m), m.getType().getName()) : null;
        for (int slot = 0; slot < top.getSize(); slot++) {
            ItemStack it = top.getItem(slot);
            if (it == null || !it.hasItemMeta()) continue;
            if (!BUTTON.equals(it.getItemMeta().getPersistentDataContainer().get(guiKey, PersistentDataType.STRING))) continue;
            int used = 0, count = 0;
            if (inv != null) {
                for (ItemStack s : inv.getContents()) {
                    if (s == null || s.isEmpty()) continue;
                    used++;
                    count += s.getAmount();
                }
            }
            int size = inv == null ? getConfig().getInt("taille", 9) : inv.getSize();
            ItemStack updated = it.clone();
            ItemMeta meta = updated.getItemMeta();
            meta.displayName(text(getConfig().getString("bouton.nom", "&6Stockage"), used, count, size));
            List<Component> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("bouton.lore")) lore.add(text(line, used, count, size));
            meta.lore(lore);
            updated.setItemMeta(meta);
            if (!updated.equals(it)) top.setItem(slot, updated);
        }
    }

    private static Component text(String s, int used, int count, int size) {
        return LEGACY.deserialize(s.replace("%cases%", String.valueOf(used)).replace("%objets%", String.valueOf(count))
                .replace("%taille%", String.valueOf(size))).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder() instanceof Minion && e.getPlayer() instanceof Player p) {
            // AxMinions remplit son menu juste après l'ouverture : on repasse au tick suivant.
            getServer().getScheduler().runTask(this, () -> refreshButton(p));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMinionMenuClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Minion m) || e.getClickedInventory() != top) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        if (!BUTTON.equals(it.getItemMeta().getPersistentDataContainer().get(guiKey, PersistentDataType.STRING))) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!handled(m) && !stores.containsKey(key(m)) && data.getString("stockages." + key(m) + ".objets") == null) {
            p.sendMessage(LEGACY.deserialize("&cCe stagiaire travaille avec son coffre relie, il n'a pas de stockage."));
            return;
        }
        Inventory inv = store(key(m), m.getType().getName());
        getServer().getScheduler().runTask(this, () -> p.openInventory(inv));
    }

    // ---------------------------------------------------------------- retrait des objets

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStorageClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof StorageHolder holder)) return;
        if (e.getClickedInventory() != top) {
            // Son propre inventaire reste libre, mais rien ne doit entrer dans le stockage.
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack item = top.getItem(e.getSlot());
        if (item == null || item.isEmpty()) return;
        int amount;
        ClickType click = e.getClick();
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) amount = item.getAmount();
        else if (click == ClickType.RIGHT) amount = (item.getAmount() + 1) / 2;
        else return;
        ItemStack give = item.clone();
        give.setAmount(amount);
        int left = 0;
        for (ItemStack rest : p.getInventory().addItem(give).values()) left += rest.getAmount();
        int taken = amount - left;
        if (taken <= 0) {
            p.sendActionBar(LEGACY.deserialize("&cTon inventaire est plein."));
            return;
        }
        Material type = item.getType();
        if (item.getAmount() - taken <= 0) top.setItem(e.getSlot(), null);
        else {
            item.setAmount(item.getAmount() - taken);
            top.setItem(e.getSlot(), item);
        }
        giveXp(p, holder.type, type, taken);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStorageDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof StorageHolder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Le stagiaire est ramassé : son stockage revient au joueur (avec l'XP, comme un retrait). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PreMinionPickupEvent e) {
        Minion m = e.getMinion();
        String key = key(m);
        if (!stores.containsKey(key) && data.getString("stockages." + key + ".objets") == null) return;
        Inventory inv = store(key, m.getType().getName());
        for (HumanEntity h : new ArrayList<>(inv.getViewers())) h.closeInventory();
        if (m.getLinkedInventory() == inv) setLinked(m, null);
        Player p = e.getPlayer();
        Location drop = m.getLocation();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.isEmpty()) continue;
            int left = 0;
            for (ItemStack rest : p.getInventory().addItem(it.clone()).values()) {
                left += rest.getAmount();
                if (drop.getWorld() != null) drop.getWorld().dropItemNaturally(drop, rest);
            }
            giveXp(p, ((StorageHolder) inv.getHolder()).type, it.getType(), it.getAmount() - left);
        }
        inv.clear();
        stores.remove(key);
        data.set("stockages." + key, null);
        save();
    }

    private void giveXp(Player p, String minionType, Material material, int amount) {
        if (amount <= 0) return;
        ConfigurationSection t = getConfig().getConfigurationSection("types." + minionType);
        if (t == null) return;
        String skillId = t.getString("competence", "aucune").toLowerCase(Locale.ROOT);
        double perItem = t.getDouble("xp." + material.name().toLowerCase(Locale.ROOT), 0);
        double xp = perItem * amount * getConfig().getDouble("ratio-xp", 0.4);
        if (xp <= 0 || skillId.equals("aucune")) return;
        Skills skill;
        try {
            skill = Skills.valueOf(skillId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Competence inconnue pour " + minionType + " : " + skillId);
            return;
        }
        SkillsUser user = AuraSkillsApi.get().getUser(p.getUniqueId());
        if (user == null || !user.isLoaded()) return;
        user.addSkillXp(skill, xp);
        String msg = getConfig().getString("message-xp", "");
        if (!msg.isEmpty()) {
            p.sendActionBar(LEGACY.deserialize(msg.replace("%xp%", String.format(Locale.FRANCE, "%.1f", xp))
                    .replace("%competence%", SKILL_NAMES.getOrDefault(skillId, skillId))));
        }
    }
}
