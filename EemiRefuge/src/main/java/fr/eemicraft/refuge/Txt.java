package fr.eemicraft.refuge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/** Textes : codes § vers Adventure, nombres à la française. */
public final class Txt {

    public static final String PREFIX = "§8[§aDernier Refuge§8] §7";

    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /** Nom ou ligne de description d'objet : jamais en italique. */
    public static Component item(String legacy) {
        return c(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static void msg(CommandSender s, String legacy) {
        s.sendMessage(c(PREFIX + legacy));
    }

    /** 1.5 → « 1,5 », 2.0 → « 2 ». */
    public static String num(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return Long.toString(Math.round(v));
        return String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private Txt() { }
}
