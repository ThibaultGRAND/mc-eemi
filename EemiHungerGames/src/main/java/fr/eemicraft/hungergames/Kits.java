package fr.eemicraft.hungergames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kits au choix (section « kits » du config) : objet « Choisir un kit » pendant l'attente, menu des kits,
 * choix mémorisé par joueur (choix-kits.yml), kit ajouté à l'inventaire au lancement de la partie.
 * Un kit avec « permission » n'est utilisable que si le joueur a cette permission (déverrouillage EemiCore).
 * Objets : « MATERIAL [quantite] », enchantements « MATERIAL:ENCHANT:NIVEAU[:ENCHANT:NIVEAU] »,
 * potions « POTION:TYPE » (aussi SPLASH_POTION / LINGERING_POTION). Effets : « EFFET secondes [niveau] ».
 */
final class Kits implements Listener {

    /** Un kit du config. */
    record Kit(String id, String name, Material icon, List<String> lore, List<ItemStack> items, List<PotionEffect> effects, String permission) {}

    /** Menu des kits (reconnu au clic par son holder). */
    private static final class Menu implements InventoryHolder {
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final JavaPlugin plugin;
    private final NamespacedKey markKey;
    private final String prefix;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private String defaultKit = "defaut";
    private File choiceFile;
    private YamlConfiguration choices;

    Kits(JavaPlugin plugin, NamespacedKey markKey, String prefix) {
        this.plugin = plugin;
        this.markKey = markKey;
        this.prefix = prefix;
    }

    /** Lit les kits (section du jar si le config du serveur n'en a pas) et les choix des joueurs. */
    void load() {
        kits.clear();
        var cfg = plugin.getConfig();
        // un config de serveur sans section « kits » rend une section vide (pas celle du jar) : on prend alors celle du jar
        ConfigurationSection sec = cfg.getConfigurationSection("kits");
        if ((sec == null || sec.getKeys(false).isEmpty()) && cfg.getDefaults() != null)
            sec = cfg.getDefaults().getConfigurationSection("kits");
        if (sec != null)
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
                List<ItemStack> items = new ArrayList<>();
                for (String s : c.getStringList("items")) {
                    ItemStack it = parseItem(s);
                    if (it == null) plugin.getLogger().warning("Kit " + id + " : objet invalide « " + s + " ».");
                    else items.add(it);
                }
                List<PotionEffect> effects = new ArrayList<>();
                for (String s : c.getStringList("effects")) {
                    PotionEffect pe = parseEffect(s);
                    if (pe == null) plugin.getLogger().warning("Kit " + id + " : effet invalide « " + s + " ».");
                    else effects.add(pe);
                }
                String perm = c.getString("permission", "");
                kits.put(id.toLowerCase(Locale.ROOT), new Kit(id.toLowerCase(Locale.ROOT), c.getString("name", id),
                        icon == null ? Material.CHEST : icon, c.getStringList("description"), items, effects,
                        perm == null || perm.isBlank() ? null : perm));
            }
        defaultKit = cfg.getString("default-kit", "defaut").toLowerCase(Locale.ROOT);
        if (!kits.containsKey(defaultKit) && !kits.isEmpty()) defaultKit = kits.keySet().iterator().next();
        choiceFile = new File(plugin.getDataFolder(), "choix-kits.yml");
        choices = YamlConfiguration.loadConfiguration(choiceFile);
        plugin.getLogger().info(kits.size() + " kits (defaut : " + defaultKit + ").");
    }

    private static Material material(String s) {
        return Material.matchMaterial(s.trim());
    }

    /** « MATERIAL[:ENCHANT:NIVEAU...|:POTION] [quantite] » */
    static ItemStack parseItem(String s) {
        String[] parts = s.trim().split("\\s+");
        String[] spec = parts[0].split(":");
        Material m = material(spec[0]);
        if (m == null || m.isAir()) return null;
        int amount = 1;
        if (parts.length > 1) {
            try { amount = Integer.parseInt(parts[1]); } catch (NumberFormatException ex) { return null; }
        }
        ItemStack it = new ItemStack(m, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof PotionMeta pm && spec.length >= 2) {
            PotionType t = Registry.POTION.get(NamespacedKey.minecraft(spec[1].toLowerCase(Locale.ROOT)));
            if (t == null) return null;
            pm.setBasePotionType(t);
        } else
            for (int i = 1; i + 1 < spec.length; i += 2) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(spec[i].toLowerCase(Locale.ROOT)));
                if (e == null) return null;
                try { meta.addEnchant(e, Integer.parseInt(spec[i + 1]), true); } catch (NumberFormatException ex) { return null; }
            }
        it.setItemMeta(meta);
        return it;
    }

    /** « EFFET secondes [niveau] » (niveau 1 = I). */
    static PotionEffect parseEffect(String s) {
        String[] p = s.trim().split("\\s+");
        PotionEffectType t = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(p[0].toLowerCase(Locale.ROOT)));
        if (t == null) return null;
        try {
            int sec = p.length > 1 ? Integer.parseInt(p[1]) : 30;
            int lvl = p.length > 2 ? Integer.parseInt(p[2]) : 1;
            return new PotionEffect(t, sec * 20, Math.max(0, lvl - 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    boolean allowed(Player p, Kit k) {
        return k.permission() == null || p.hasPermission(k.permission());
    }

    /** Kit choisi par le joueur (le kit par défaut si aucun choix, kit disparu ou verrouillé). */
    Kit selected(Player p) {
        Kit k = kits.get(choices.getString(p.getUniqueId().toString(), defaultKit));
        if (k == null || !allowed(p, k)) k = kits.get(defaultKit);
        return k;
    }

    private void choose(Player p, Kit k) {
        choices.set(p.getUniqueId().toString(), k.id());
        try {
            choices.save(choiceFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Sauvegarde de choix-kits.yml impossible : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ objet et menu

    /** Objet « Choisir un kit » (objet du plugin : ne se jette pas, ne tombe pas à la mort). */
    ItemStack chooser() {
        ItemStack it = new ItemStack(Material.CHEST);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Choisir un kit", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Clic droit : choisir ton kit de départ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    boolean isChooser(ItemStack it) {
        return it != null && it.getType() == Material.CHEST && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(markKey, PersistentDataType.BYTE);
    }

    void open(Player p) {
        if (kits.isEmpty()) { p.sendMessage(prefix + "§cAucun kit configuré."); return; }
        Menu menu = new Menu();
        int size = Math.min(54, Math.max(9, (kits.size() + 8) / 9 * 9));
        menu.inv = Bukkit.createInventory(menu, size, Component.text("Choisir un kit", NamedTextColor.DARK_GRAY));
        Kit current = selected(p);
        for (Kit k : kits.values()) {
            if (menu.ids.size() >= size) break;
            boolean ok = allowed(p, k), sel = current != null && current.id().equals(k.id());
            ItemStack icon = new ItemStack(ok ? k.icon() : Material.GRAY_DYE);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(k.name(), sel ? NamedTextColor.GREEN : ok ? NamedTextColor.YELLOW : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, sel));
            List<Component> lore = new ArrayList<>();
            for (String l : k.lore()) lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(sel ? "Kit choisi" : ok ? "Clic : choisir ce kit" : "Verrouillé",
                    sel ? NamedTextColor.GREEN : ok ? NamedTextColor.YELLOW : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            if (sel) meta.setEnchantmentGlintOverride(true);
            icon.setItemMeta(meta);
            menu.inv.setItem(menu.ids.size(), icon);
            menu.ids.add(k.id());
        }
        p.openInventory(menu.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot < 0 || slot >= menu.ids.size()) return;
        Kit k = kits.get(menu.ids.get(slot));
        if (k == null) return;
        if (!allowed(p, k)) {
            p.sendMessage(prefix + "§cCe kit est verrouillé.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        choose(p, k);
        p.sendMessage(prefix + "§7Kit choisi : §e" + k.name());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ lancement

    /** Ajoute le kit choisi à l'inventaire (armure portée si l'emplacement est libre) et ses effets. */
    void give(Player p) {
        Kit k = selected(p);
        if (k == null) return;
        PlayerInventory inv = p.getInventory();
        for (ItemStack proto : k.items()) {
            ItemStack it = proto.clone();
            EquipmentSlot slot = it.getType().getEquipmentSlot();
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                ItemStack worn = inv.getItem(slot);
                if (worn == null || worn.getType().isAir()) { inv.setItem(slot, it); continue; }
            }
            for (ItemStack left : inv.addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        for (PotionEffect pe : k.effects()) p.addPotionEffect(pe);
        if (!k.items().isEmpty() || !k.effects().isEmpty()) p.sendMessage(prefix + "§7Kit §e" + k.name() + " §7reçu.");
    }
}
