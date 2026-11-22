package fr.eemicraft.droits.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Titres des menus de /droits au style du lobby : fond (glyphe), calques d'état et textes, police eemicraft:droits
 *  (même mécanique que EemiRefuge ui/Ui.java). Glyphes et dispositions : Fonds.java, généré par pack/gen_droits.py. */
public final class Ui {

    public static final Key FONT = Key.key("eemicraft", "droits");
    public static final NamespacedKey MODEL_VIDE = new NamespacedKey("eemicraft", "nav/vide");
    /** Couleur par défaut des textes : blanc cassé sur le fond violet (15/11). */
    public static final TextColor GRIS = TextColor.color(0xEEF0F6);
    public static Logger log;

    private Ui() { }

    /** Texte dynamique posé à une ancre de la disposition. */
    public record Ligne(String anchor, String text, TextColor color) {
        public Ligne(String anchor, String text) {
            this(anchor, text, null);
        }
    }

    public static boolean has(String menu) {
        return Fonds.MENUS.containsKey(menu);
    }

    public static Component title(String menu, List<String> overlays, List<Ligne> lignes) {
        Fonds.Disposition d = Fonds.MENUS.get(menu);
        Builder b = new Builder();
        b.moveTo(0);
        b.glyph(Fonds.GLYPHES.get("bg:" + menu));
        for (String ov : overlays) {
            Character ch = Fonds.GLYPHES.get("ov:" + menu + ":" + ov);
            if (ch == null) {
                if (log != null) log.warning("Calque inconnu " + menu + ":" + ov);
                continue;
            }
            b.moveTo(0);
            b.glyph(ch);
        }
        for (Ligne l : lignes) {
            if (l.text() == null || l.text().isEmpty()) continue;
            Fonds.Texte t = d.textes().get(l.anchor());
            if (t == null) {
                if (log != null) log.warning("Ancre de texte inconnue " + menu + ":" + l.anchor());
                continue;
            }
            String s = fit(normalize(l.text()), t.max());
            int w = width(s) - 1;
            int x0 = t.align() == 'c' ? t.x() - w / 2 : t.align() == 'd' ? t.x() - w : t.x();
            b.moveTo(x0);
            b.text(s, Key.key("eemicraft", "dt" + t.y()), l.color());
        }
        return b.build();
    }

    /** Couleur lisible sur le fond violet d'après le premier code & d'un texte (statuts EemiCore). */
    public static TextColor colorOf(String legacy) {
        if (legacy == null) return GRIS;
        for (int i = 0; i + 1 < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c != '&' && c != '§') continue;
            switch (Character.toLowerCase(legacy.charAt(i + 1))) {
                case 'a', '2': return TextColor.color(0x6CE07C);
                case 'c', '4': return TextColor.color(0xFF6B6B);
                case 'e', '6': return TextColor.color(0xFFC94A);
                case 'b', '3': return TextColor.color(0x7FD8F0);
                case '9', '1': return TextColor.color(0x8AB4FF);
                case 'd', '5': return TextColor.color(0xD6A6FF);
                case '8': return TextColor.color(0x8A88A8);
                case '7': return TextColor.color(0xB8B6D0);
                case 'f': return TextColor.color(0xEEF0F6);
                default: break;
            }
        }
        return GRIS;
    }

    /** Petites capitales : majuscules, accents gardés quand la police les a, codes & retirés. */
    public static String normalize(String s) {
        s = s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
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
            root.append(Component.text(sb.toString()).font(FONT));
            x = target;
        }

        void glyph(char ch) {
            root.append(Component.text(String.valueOf(ch)).font(FONT).color(NamedTextColor.WHITE));
            x += Fonds.AVANCE_FOND;
        }

        void text(String s, Key font, TextColor color) {
            root.append(Component.text(s).font(font).color(color == null ? GRIS : color));
            x += width(s);
        }

        Component build() {
            return root.build();
        }
    }
}
