package fr.eemicraft.refuge.ui;

import fr.eemicraft.refuge.EemiRefuge;
import fr.eemicraft.refuge.Txt;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Outils communs des menus du mode : titre en glyphes (fonds du pack Refuge), objets invisibles, sons. */
public final class Ui implements Listener {

    public static final Key FONT_GUI = Key.key("refuge", "gui");
    public static final NamespacedKey MODEL_VIDE = new NamespacedKey("refuge", "gui/vide");
    private static EemiRefuge plugin;
    private static NamespacedKey keyUi;
    /** Dernier état du pack du mode par joueur (refusé / échec = menus en mode simple). */
    private static final Map<UUID, PlayerResourcePackStatusEvent.Status> packStatus = new HashMap<>();

    private Ui() { }

    public static void init(EemiRefuge p) {
        plugin = p;
        keyUi = new NamespacedKey(p, "ui");
        p.getServer().getPluginManager().registerEvents(new Ui(), p);
        p.getServer().getPluginManager().registerEvents(new Menus(), p);
    }

    public static EemiRefuge plugin() { return plugin; }

    /** Ferme tous les menus du mode ouverts (arrêt du plugin : la réserve est enregistrée à la fermeture). */
    public static void closeAll() {
        Menus.closeAll();
    }

    /** Menu du mode ouvert par ce joueur, ou null. */
    public static Menu openMenu(Player p) {
        return p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu m ? m : null;
    }

    // ------------------------------------------------------------------ pack

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent e) {
        if (EemiRefuge.PACK_UUID.equals(e.getID())) packStatus.put(e.getPlayer().getUniqueId(), e.getStatus());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        packStatus.remove(e.getPlayer().getUniqueId());
    }

    /** Menus dessinés (pack chargé dans ce monde) ou menus simples en objets vanilla. */
    public static boolean hasPack(Player p) {
        if (!plugin.getConfig().getBoolean("pack.enabled", false)) return false;
        String w = p.getWorld().getName().toLowerCase(Locale.ROOT);
        if (!w.startsWith("refuge")) return false;
        PlayerResourcePackStatusEvent.Status s = packStatus.get(p.getUniqueId());
        return s == null || s == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED
                || s == PlayerResourcePackStatusEvent.Status.ACCEPTED || s == PlayerResourcePackStatusEvent.Status.DOWNLOADED;
    }

    // ------------------------------------------------------------------ objets

    /** Objet de case cliquable : invisible avec le pack, sinon l'objet vanilla de repli. */
    public static ItemStack button(boolean pack, Material fallback, String name, List<String> lore) {
        ItemStack it = new ItemStack(pack ? Material.PAPER : fallback);
        ItemMeta m = it.getItemMeta();
        m.displayName(Txt.item(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Txt.item("§7" + s));
        m.lore(l);
        if (pack) m.setItemModel(MODEL_VIDE);
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(keyUi, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    /** Copie d'affichage d'un objet réel (marquée, jamais rendue au joueur). */
    public static ItemStack display(ItemStack src, List<String> extraLore) {
        ItemStack it = src.clone();
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        if (!extraLore.isEmpty()) {
            List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            if (!l.isEmpty()) l.add(Component.empty());
            for (String s : extraLore) l.add(Txt.item(s));
            m.lore(l);
        }
        m.getPersistentDataContainer().set(keyUi, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public static boolean isUi(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(keyUi, PersistentDataType.BYTE);
    }

    /** Place-t-on tout l'objet dans le sac (cases de rangement seulement) ? */
    public static boolean fits(Player p, ItemStack item) {
        int left = item.getAmount();
        int max = item.getMaxStackSize();
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.isEmpty()) left -= max;
            else if (s.isSimilar(item)) left -= Math.max(0, max - s.getAmount());
            if (left <= 0) return true;
        }
        return false;
    }

    /** Range dans les cases données d'un inventaire ; renvoie ce qui reste (0 = tout rangé). */
    public static int store(Inventory inv, int from, int to, ItemStack item) {
        int left = item.getAmount();
        int max = item.getMaxStackSize();
        for (int i = from; i <= to && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && !s.isEmpty() && s.isSimilar(item) && s.getAmount() < max) {
                int k = Math.min(left, max - s.getAmount());
                s.setAmount(s.getAmount() + k);
                left -= k;
            }
        }
        for (int i = from; i <= to && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty()) {
                ItemStack c = item.clone();
                int k = Math.min(left, max);
                c.setAmount(k);
                inv.setItem(i, c);
                left -= k;
            }
        }
        return left;
    }

    // ------------------------------------------------------------------ sons

    public static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.35f, 1.3f);
    }

    public static void error(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.8f, 0.6f);
    }

    public static void success(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.7f, 1.4f);
    }

    // ------------------------------------------------------------------ titre

    /** Texte dynamique posé à une ancre de la disposition. */
    public record Ligne(String anchor, String text, TextColor color) { }

    public static Component title(String menu, List<String> overlays, List<Ligne> lignes) {
        Fonds.Disposition d = Fonds.MENUS.get(menu);
        Builder b = new Builder();
        b.moveTo(0);
        b.glyph(Fonds.GLYPHES.get("bg:" + menu));
        for (String ov : overlays) {
            Character ch = Fonds.GLYPHES.get("ov:" + menu + ":" + ov);
            if (ch == null) {
                plugin.getLogger().warning("Calque inconnu " + menu + ":" + ov);
                continue;
            }
            b.moveTo(0);
            b.glyph(ch);
        }
        for (Ligne l : lignes) {
            Fonds.Texte t = d.textes().get(l.anchor());
            if (t == null) {
                plugin.getLogger().warning("Ancre de texte inconnue " + menu + ":" + l.anchor());
                continue;
            }
            String s = fit(normalize(l.text()), t.max());
            int w = width(s) - 1;
            int x0 = t.align() == 'c' ? t.x() - w / 2 : t.align() == 'd' ? t.x() - w : t.x();
            b.moveTo(x0);
            b.text(s, Key.key("refuge", "t" + t.y()), l.color());
        }
        return b.build();
    }

    /** Petites capitales : majuscules, accents gardés quand la police les a (É È Ê À Â Ô Î). */
    public static String normalize(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == ' ' || Fonds.AVANCES.containsKey(c)) {
                out.append(c);
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            char b = base.isEmpty() ? '?' : base.charAt(0);
            out.append(Fonds.AVANCES.containsKey(b) || b == ' ' ? b : '?');
        }
        return out.toString();
    }

    public static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c == ' ' ? Fonds.ESPACE : Fonds.AVANCES.getOrDefault(c, 4);
        return w;
    }

    private static String fit(String s, int max) {
        if (width(s) - 1 <= max) return s;
        String t = s;
        while (t.length() > 1 && width(t + ".") - 1 > max) t = t.substring(0, t.length() - 1);
        return t.stripTrailing() + ".";
    }

    private static final class Builder {
        private final TextComponent.Builder root = Component.text();
        private int x = 8; // le titre commence en x = 8 du coffre

        void moveTo(int target) {
            int delta = target - x;
            if (delta == 0) return;
            StringBuilder sb = new StringBuilder();
            char[] set = delta < 0 ? Fonds.NEG : Fonds.POS;
            int left = Math.abs(delta);
            while (left > 0) {
                for (int i = set.length - 1; i >= 0; i--) {
                    int v = 1 << i;
                    if (v <= left) {
                        sb.append(set[i]);
                        left -= v;
                        break;
                    }
                }
            }
            root.append(Component.text(sb.toString()).font(FONT_GUI));
            x = target;
        }

        void glyph(char ch) {
            root.append(Component.text(String.valueOf(ch)).font(FONT_GUI).color(NamedTextColor.WHITE));
            x += Fonds.AVANCE_FOND;
        }

        void text(String s, Key font, TextColor color) {
            root.append(Component.text(s).font(font).color(color == null ? TextColor.color(0x3F3F3F) : color));
            x += width(s);
        }

        Component build() {
            return root.build();
        }
    }
}
