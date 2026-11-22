package fr.eemicraft.abysse;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Petits utilitaires de texte et d'objets (couleurs en codes §, comme les autres plugins maison). */
final class Txt {

    static final String PREFIX = "§8[§5Abysse§8] §r";
    static final String COIN = "⬥";

    private Txt() {
    }

    /** Texte en codes § → Component sans italique (noms et descriptions d'objets). */
    static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    static List<Component> lines(List<String> legacy) {
        List<Component> out = new ArrayList<>();
        for (String s : legacy) out.add(c(s));
        return out;
    }

    /** Objet d'interface (icône de menu). */
    static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, List.of(lore));
    }

    static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (!lore.isEmpty()) meta.lore(lines(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** 1234.5 → « 1 234,5 », 12.0 → « 12 ». */
    static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.005) return String.format(Locale.FRANCE, "%,d", Math.round(v)).replace(' ', ' ').replace(' ', ' ');
        return String.format(Locale.FRANCE, "%,.1f", v).replace(' ', ' ').replace(' ', ' ');
    }

    static String money(double v) {
        return "§6" + num(v) + " " + COIN;
    }

    /** Barre de progression en caractères. */
    static String bar(double ratio, int len, String on, String off) {
        int n = (int) Math.round(Math.max(0, Math.min(1, ratio)) * len);
        return on + "|".repeat(n) + off + "|".repeat(len - n);
    }

    /** Durée en secondes → « 3 min 20 s ». */
    static String time(long s) {
        if (s >= 3600) return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
        if (s >= 60) return (s / 60) + " min " + (s % 60) + " s";
        return s + " s";
    }

    /** Coupe un texte long en lignes de description d'environ 38 caractères. */
    static List<String> wrap(String text, String color) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() + 1 > 38 && line.length() > 0) {
                out.add(color + line);
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(color + line);
        return out;
    }
}
