package fr.eemicraft.iles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Locale;

final class U {
    private static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();
    static final String SKY = "&8[&aSkyBlock&8] &7";
    static final String OB = "&8[&aOneBlock&8] &7";
    static final String ADMIN = "&8[&aÎles&8] &7";

    private U() {
    }

    static Component c(String s) {
        return L.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    static void msg(CommandSender s, String m) {
        if (s != null) s.sendMessage(c(m));
    }

    /** 12345 -> « 12 345 ». */
    static String n(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (v < 0 ? "-" : "") + b;
    }

    static String dec(float f) {
        return f == Math.rint(f) ? Integer.toString((int) f) : String.format(Locale.FRANCE, "%.1f", f);
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }
}
