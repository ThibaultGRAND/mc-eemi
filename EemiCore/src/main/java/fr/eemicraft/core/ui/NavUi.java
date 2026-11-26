package fr.eemicraft.core.ui;

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

/** Titres des menus du lobby au style Refuge : fond (glyphe), calques d'état et textes, police eemicraft:nav
 *  (même mécanique que EemiRefuge ui/Ui.java). Glyphes et dispositions : NavFonds.java, généré par pack/gen_nav.py. */
public final class NavUi {

    public static final Key FONT = Key.key("eemicraft", "nav");
    public static final NamespacedKey MODEL_VIDE = new NamespacedKey("eemicraft", "nav/vide");
    /** Couleur par défaut des textes : blanc cassé sur le fond violet (15/11). */
    public static final TextColor GRIS = TextColor.color(0xEEF0F6);
    public static Logger log;

    private NavUi() { }

    /** Texte dynamique posé à une ancre de la disposition. */
    public record Ligne(String anchor, String text, TextColor color) {
        public Ligne(String anchor, String text) {
            this(anchor, text, null);
        }
    }

    public static boolean has(String menu) {
        return NavFonds.MENUS.containsKey(menu);
    }

    public static Component title(String menu, List<String> overlays, List<Ligne> lignes) {
        NavFonds.Disposition d = NavFonds.MENUS.get(menu);
        Builder b = new Builder();
        b.moveTo(0);
        b.glyph(NavFonds.GLYPHES.get("bg:" + menu));
        // 26/11 : libellés fixes en police lisse (plus dans le fond), sous les calques (grisé « off »)
        for (NavFonds.Libelle lb : d.libelles()) {
            float w = lb.largeur() / (float) NavFonds.UNITE;
            float x0 = lb.align() == 'c' ? lb.x() - w / 2f : lb.align() == 'd' ? lb.x() - w : lb.x();
            b.moveTo(x0);
            b.text(lb.texte(), Key.key("eemicraft", lb.police()), TextColor.color(lb.couleur()), w);
        }
        for (String ov : overlays) {
            Character ch = NavFonds.GLYPHES.get("ov:" + menu + ":" + ov);
            if (ch == null) {
                if (log != null) log.warning("Calque inconnu " + menu + ":" + ov);
                continue;
            }
            b.moveTo(0);
            b.glyph(ch);
        }
        for (Ligne l : lignes) {
            if (l.text() == null || l.text().isEmpty()) continue;
            NavFonds.Texte t = d.textes().get(l.anchor());
            if (t == null) {
                if (log != null) log.warning("Ancre de texte inconnue " + menu + ":" + l.anchor());
                continue;
            }
            // trop large : taille suivante (nt<y>_<k>), puis coupé à la plus petite
            String s = normalize(l.text());
            int k = 0;
            while (k < NavFonds.TAILLES.length - 1 && width(s, k) > t.max()) k++;
            s = fit(s, t.max(), k);
            float w = width(s, k);
            float x0 = t.align() == 'c' ? t.x() - w / 2f : t.align() == 'd' ? t.x() - w : t.x();
            b.moveTo(x0);
            b.text(s, Key.key("eemicraft", "nt" + t.y() + (k == 0 ? "" : "_" + k)), l.color(), w);
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
            if (c == ' ' || NavFonds.AVANCES.containsKey(c)) {
                out.append(c);
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            char b = base.isEmpty() ? '?' : base.charAt(0);
            out.append(NavFonds.AVANCES.containsKey(b) || b == ' ' ? b : '?');
        }
        return out.toString();
    }

    /** Largeur en pixels d'interface à la taille k (police lisse ; +3 % de marge pour les tailles réduites). */
    public static float width(String s, int k) {
        int q = 0;
        for (char c : s.toCharArray()) q += c == ' ' ? NavFonds.ESPACE : NavFonds.AVANCES.getOrDefault(c, 20);
        float w = q / (float) NavFonds.UNITE;
        return k == 0 ? w : w * NavFonds.TAILLES[k] / NavFonds.TAILLES[0] * 1.03f;
    }

    public static float width(String s) {
        return width(s, 0);
    }

    private static String fit(String s, int max, int k) {
        if (width(s, k) <= max) return s;
        String t = s;
        while (t.length() > 1 && width(t + ".", k) > max) t = t.substring(0, t.length() - 1);
        return t.stripTrailing() + ".";
    }

    private static final class Builder {
        private final TextComponent.Builder root = Component.text();
        private float x = 8; // le titre commence en x = 8 du coffre (position réelle du curseur, en float)

        void moveTo(float target) {
            int delta = Math.round(target - x);
            if (delta == 0) return;
            StringBuilder sb = new StringBuilder();
            char[] set = delta < 0 ? NavFonds.NEG : NavFonds.POS;
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
            x += delta;
        }

        void glyph(char ch) {
            root.append(Component.text(String.valueOf(ch)).font(FONT).color(NamedTextColor.WHITE));
            x += NavFonds.AVANCE_FOND;
        }

        void text(String s, Key font, TextColor color, float w) {
            root.append(Component.text(s).font(font).color(color == null ? GRIS : color));
            x += w;
        }

        Component build() {
            return root.build();
        }
    }
}
