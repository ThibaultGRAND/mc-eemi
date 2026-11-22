package fr.eemicraft.refuge.ui;

import java.util.HashMap;
import java.util.Map;

/** GÉNÉRÉ par pack/gen_ui.py (ne pas modifier à la main) : glyphes et dispositions des menus du pack Refuge. */
public final class Fonds {
    public static final int AVANCE_FOND = 177;
    /** Espaces de la police refuge:gui : NEG[i] = -2^i, POS[i] = +2^i. */
    public static final char[] NEG = {'\uf800', '\uf801', '\uf802', '\uf803', '\uf804', '\uf805', '\uf806', '\uf807', '\uf808'};
    public static final char[] POS = {'\uf810', '\uf811', '\uf812', '\uf813', '\uf814', '\uf815', '\uf816', '\uf817', '\uf818'};
    public static final int ESPACE = 3;
    /** Glyphes : « bg:&lt;menu&gt; » (fond) et « ov:&lt;menu&gt;:&lt;type&gt;:&lt;élément&gt;[:n] » (calques). */
    public static final Map<String, Character> GLYPHES = new HashMap<>();
    /** Avance (px) de chaque caractère des polices de texte refuge:t&lt;y&gt;. */
    public static final Map<Character, Integer> AVANCES = new HashMap<>();
    public record Texte(int x, int y, char align, int max) { }
    public record Disposition(int lignes, Map<String, int[]> boutons, Map<String, int[]> cases,
                              Map<String, Texte> textes) { }
    public static final Map<String, Disposition> MENUS = new HashMap<>();

    static {
        GLYPHES.put("bg:expedition", '\ue000');
        GLYPHES.put("bg:escouade", '\ue001');
        GLYPHES.put("bg:liste_inviter", '\ue002');
        GLYPHES.put("bg:liste_rejoindre", '\ue003');
        GLYPHES.put("bg:membre", '\ue004');
        GLYPHES.put("bg:reserve", '\ue005');
        GLYPHES.put("bg:boutique", '\ue006');
        GLYPHES.put("bg:assurance", '\ue007');
        GLYPHES.put("bg:bilan", '\ue008');
        GLYPHES.put("bg:profil", '\ue009');
        GLYPHES.put("ov:expedition:sel:jour", '\ue100');
        GLYPHES.put("ov:expedition:sel:nuit", '\ue101');
        GLYPHES.put("ov:expedition:on:pret", '\ue102');
        GLYPHES.put("ov:expedition:on:file", '\ue103');
        GLYPHES.put("ov:expedition:off:partir", '\ue104');
        GLYPHES.put("ov:expedition:cellsel:zones:0", '\ue105');
        GLYPHES.put("ov:expedition:cellsel:zones:1", '\ue106');
        GLYPHES.put("ov:expedition:cellsel:zones:2", '\ue107');
        GLYPHES.put("ov:expedition:cellsel:zones:3", '\ue108');
        GLYPHES.put("ov:expedition:cellsel:zones:4", '\ue109');
        GLYPHES.put("ov:expedition:cellsel:zones:5", '\ue10a');
        GLYPHES.put("ov:expedition:cellsel:zones:6", '\ue10b');
        GLYPHES.put("ov:expedition:chef:membres:0", '\ue10c');
        GLYPHES.put("ov:expedition:ok:membres:0", '\ue10d');
        GLYPHES.put("ov:expedition:non:membres:0", '\ue10e');
        GLYPHES.put("ov:expedition:chef:membres:1", '\ue10f');
        GLYPHES.put("ov:expedition:ok:membres:1", '\ue110');
        GLYPHES.put("ov:expedition:non:membres:1", '\ue111');
        GLYPHES.put("ov:expedition:chef:membres:2", '\ue112');
        GLYPHES.put("ov:expedition:ok:membres:2", '\ue113');
        GLYPHES.put("ov:expedition:non:membres:2", '\ue114');
        GLYPHES.put("ov:expedition:chef:membres:3", '\ue115');
        GLYPHES.put("ov:expedition:ok:membres:3", '\ue116');
        GLYPHES.put("ov:expedition:non:membres:3", '\ue117');
        GLYPHES.put("ov:escouade:chef:membres:0", '\ue118');
        GLYPHES.put("ov:escouade:chef:membres:1", '\ue119');
        GLYPHES.put("ov:escouade:chef:membres:2", '\ue11a');
        GLYPHES.put("ov:escouade:chef:membres:3", '\ue11b');
        GLYPHES.put("ov:escouade:off:creer", '\ue11c');
        GLYPHES.put("ov:escouade:off:inviter", '\ue11d');
        GLYPHES.put("ov:escouade:off:rejoindre", '\ue11e');
        GLYPHES.put("ov:escouade:off:quitter", '\ue11f');
        GLYPHES.put("ov:boutique:sel:acheter", '\ue120');
        GLYPHES.put("ov:boutique:sel:vendre", '\ue121');
        GLYPHES.put("ov:bilan:icon:issue:extrait", '\ue122');
        GLYPHES.put("ov:bilan:icon:issue:mort", '\ue123');
        GLYPHES.put("ov:profil:off:reserve", '\ue124');
        GLYPHES.put("ov:profil:off:bilan", '\ue125');
        AVANCES.put('A', 4);
        AVANCES.put('B', 4);
        AVANCES.put('C', 4);
        AVANCES.put('D', 4);
        AVANCES.put('E', 4);
        AVANCES.put('F', 4);
        AVANCES.put('G', 4);
        AVANCES.put('H', 4);
        AVANCES.put('I', 2);
        AVANCES.put('J', 4);
        AVANCES.put('K', 4);
        AVANCES.put('L', 4);
        AVANCES.put('M', 6);
        AVANCES.put('N', 5);
        AVANCES.put('O', 4);
        AVANCES.put('P', 4);
        AVANCES.put('Q', 4);
        AVANCES.put('R', 4);
        AVANCES.put('S', 4);
        AVANCES.put('T', 4);
        AVANCES.put('U', 4);
        AVANCES.put('V', 4);
        AVANCES.put('W', 6);
        AVANCES.put('X', 4);
        AVANCES.put('Y', 4);
        AVANCES.put('Z', 4);
        AVANCES.put('0', 4);
        AVANCES.put('1', 3);
        AVANCES.put('2', 4);
        AVANCES.put('3', 4);
        AVANCES.put('4', 4);
        AVANCES.put('5', 4);
        AVANCES.put('6', 4);
        AVANCES.put('7', 4);
        AVANCES.put('8', 4);
        AVANCES.put('9', 4);
        AVANCES.put('.', 2);
        AVANCES.put(':', 2);
        AVANCES.put('!', 2);
        AVANCES.put('?', 4);
        AVANCES.put('\'', 2);
        AVANCES.put('-', 4);
        AVANCES.put('+', 4);
        AVANCES.put('/', 4);
        AVANCES.put('(', 3);
        AVANCES.put(')', 3);
        AVANCES.put('%', 4);
        AVANCES.put('_', 4);
        AVANCES.put('\u00b7', 2);
        AVANCES.put('\u00d7', 4);
        AVANCES.put('\u2699', 6);
        AVANCES.put('\u00c9', 4);
        AVANCES.put('\u00c8', 4);
        AVANCES.put('\u00ca', 4);
        AVANCES.put('\u00c0', 4);
        AVANCES.put('\u00d4', 4);
        AVANCES.put('\u00c2', 4);
        AVANCES.put('\u00ce', 2);
        AVANCES.put(',', 2);
        { // expedition
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("jour", new int[]{27, 28, 36, 37});
            b.put("nuit", new int[]{29, 30, 38, 39});
            b.put("pret", new int[]{32, 33, 41, 42});
            b.put("partir", new int[]{34, 35, 43, 44});
            b.put("escouade", new int[]{52});
            b.put("file", new int[]{53});
            c.put("zones", new int[]{10, 11, 12, 13, 14, 15, 16});
            c.put("membres", new int[]{45, 46, 47, 48});
            t.put("zone", new Texte(88, 59, 'c', 160));
            MENUS.put("expedition", new Disposition(6, b, c, t));
        }
        { // escouade
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("creer", new int[]{9, 10, 18, 19});
            b.put("inviter", new int[]{11, 12, 20, 21});
            b.put("rejoindre", new int[]{14, 15, 23, 24});
            b.put("quitter", new int[]{16, 17, 25, 26});
            c.put("membres", new int[]{28, 30, 32, 34});
            t.put("nom0", new Texte(34, 95, 'c', 34));
            t.put("nom1", new Texte(70, 95, 'c', 34));
            t.put("nom2", new Texte(106, 95, 'c', 34));
            t.put("nom3", new Texte(142, 95, 'c', 34));
            MENUS.put("escouade", new Disposition(5, b, c, t));
        }
        { // liste_inviter
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("retour", new int[]{45});
            b.put("prec", new int[]{48});
            b.put("suiv", new int[]{50});
            c.put("liste", new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43});
            t.put("page", new Texte(88, 114, 'c', 18));
            MENUS.put("liste_inviter", new Disposition(6, b, c, t));
        }
        { // liste_rejoindre
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("retour", new int[]{45});
            b.put("prec", new int[]{48});
            b.put("suiv", new int[]{50});
            c.put("liste", new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43});
            t.put("page", new Texte(88, 114, 'c', 18));
            MENUS.put("liste_rejoindre", new Disposition(6, b, c, t));
        }
        { // membre
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("exclure", new int[]{12, 13, 21, 22});
            b.put("chef", new int[]{15, 16, 24, 25});
            b.put("retour", new int[]{27});
            c.put("tete", new int[]{10});
            t.put("nom", new Texte(34, 59, 'c', 52));
            MENUS.put("membre", new Disposition(4, b, c, t));
        }
        { // reserve
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            c.put("stock", new int[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35});
            MENUS.put("reserve", new Disposition(4, b, c, t));
        }
        { // boutique
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("acheter", new int[]{9, 10, 18, 19});
            b.put("vendre", new int[]{11, 12, 20, 21});
            b.put("prec", new int[]{45});
            b.put("suiv", new int[]{53});
            c.put("articles", new int[]{28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 49, 50, 51, 52});
            t.put("solde", new Texte(133, 51, 'c', 70));
            MENUS.put("boutique", new Disposition(6, b, c, t));
        }
        { // assurance
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            c.put("assures", new int[]{11, 13, 15});
            t.put("prix0", new Texte(52, 59, 'c', 34));
            t.put("prix1", new Texte(88, 59, 'c', 34));
            t.put("prix2", new Texte(124, 59, 'c', 34));
            t.put("solde", new Texte(88, 95, 'c', 150));
            MENUS.put("assurance", new Disposition(5, b, c, t));
        }
        { // bilan
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            c.put("butin", new int[]{36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53});
            t.put("issue", new Texte(25, 63, 'c', 34));
            t.put("temps", new Texte(61, 63, 'c', 34));
            t.put("zombies", new Texte(115, 63, 'c', 34));
            t.put("releves", new Texte(151, 63, 'c', 34));
            t.put("ferraille", new Texte(45, 77, 'g', 70));
            t.put("xp", new Texte(117, 77, 'g', 52));
            MENUS.put("bilan", new Disposition(6, b, c, t));
        }
        { // profil
            Map<String, int[]> b = new HashMap<>(), c = new HashMap<>();
            Map<String, Texte> t = new HashMap<>();
            b.put("fermer", new int[]{0});
            b.put("info", new int[]{8});
            b.put("expedition", new int[]{9, 10, 18, 19});
            b.put("escouade", new int[]{11, 12, 20, 21});
            b.put("reserve", new int[]{14, 15, 23, 24});
            b.put("bilan", new int[]{16, 17, 25, 26});
            t.put("ligne1", new Texte(88, 77, 'c', 160));
            t.put("ligne2", new Texte(88, 95, 'c', 160));
            MENUS.put("profil", new Disposition(5, b, c, t));
        }
    }

    private Fonds() { }
}
