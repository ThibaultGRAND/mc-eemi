package fr.eemicraft.fete;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèles 3D du pack de la fête (FetePack, namespace {@code fete:}), décrits en boîtes (sans Bukkit) : la même
 * description sert à écrire le pack (outil-test GenPack) et aux aperçus hors serveur ({@link Ride#boxes}).
 *
 * <p>Unités du modèle : 16 = 1 bloc, centre (8, 8, 8) au point de l'ItemDisplay, avant de l'objet vers +z.
 * Un élément « teint » prend la couleur du composant dyed_color de l'objet (une couleur par visiteur, par voiture...).
 */
public final class Models {
    private Models() {
    }

    /** Élément : coins, couleur (0xRRGGBB), teint (dyed_color), rotation facultative (axe x/y/z, angle ±22,5/45, origine). */
    public record El(double x1, double y1, double z1, double x2, double y2, double z2, int color, boolean tint,
                     char axis, double angle, double ox, double oy, double oz) {
    }

    public static final Map<String, List<El>> MODELS = new LinkedHashMap<>();

    static final int WHITE = 0xF2F2F2, GOLD = 0xE8B83A, GOLD_D = 0xB8861E, RED = 0xC8323A, BLACK = 0x262628, GREY = 0x9AA0A8,
            DARK = 0x3A3D44, SKIN = 0xE9B796, NAIL = 0xF7D9D4, GLASS = 0xB9E4F5, BROWN = 0x5A3216, CREAM = 0xFFF3DE;

    private static El b(double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        return new El(x1, y1, z1, x2, y2, z2, c, false, 'n', 0, 0, 0, 0);
    }

    private static El t(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new El(x1, y1, z1, x2, y2, z2, 0xFFFFFF, true, 'n', 0, 0, 0, 0);
    }

    private static El r(El e, char axis, double angle, double ox, double oy, double oz) {
        return new El(e.x1(), e.y1(), e.z1(), e.x2(), e.y2(), e.z2(), e.color(), e.tint(), axis, angle, ox, oy, oz);
    }

    static {
        // cheval de carrousel au galop (robe teinte, crinière et sabots dorés, selle rouge)
        List<El> h = new ArrayList<>();
        h.add(t(5, 7, 2, 11, 13, 17));                                    // corps
        h.add(r(t(6, 11, 13, 10, 18, 17), 'x', -22.5, 8, 12, 15));        // encolure
        h.add(t(6, 16, 16, 10, 20, 23));                                  // tête
        h.add(b(6.5, 16.5, 22.9, 9.5, 18.5, 23.4, DARK));                 // naseaux
        h.add(t(6, 20, 17, 7, 22, 18));                                   // oreilles
        h.add(t(9, 20, 17, 10, 22, 18));
        h.add(r(b(7, 13, 11.5, 9, 21, 14, GOLD), 'x', -22.5, 8, 13, 13)); // crinière
        h.add(b(4.5, 10, 6.5, 11.5, 13.5, 12.5, RED));                   // tapis de selle
        h.add(b(5.5, 13.5, 7, 10.5, 14.5, 12, GOLD_D));                  // selle
        h.add(r(t(5.5, 1, 14, 7.5, 8, 16), 'x', 22.5, 6.5, 7, 15));      // jambes avant (pliées)
        h.add(r(t(8.5, 2, 13.5, 10.5, 8, 15.5), 'x', 45, 9.5, 7, 15));
        h.add(t(5.5, 0.5, 3, 7.5, 8, 5));                                // jambes arrière
        h.add(t(8.5, 0.5, 3, 10.5, 8, 5));
        h.add(b(5.4, 0, 2.9, 7.6, 1.2, 5.1, GOLD));                       // sabots
        h.add(b(8.4, 0, 2.9, 10.6, 1.2, 5.1, GOLD));
        h.add(r(b(7, 6, 0, 9, 13, 2.5, GOLD), 'x', 22.5, 8, 12, 2));      // queue
        h.add(b(7.2, 0, 6.5, 8.8, 7, 8, GOLD));                           // bout de la barre dans le corps
        MODELS.put("cheval", h);

        // nacelle de grande roue (caisse teinte, poteaux blancs, toit teint à lanterneau doré, vitres)
        List<El> g = new ArrayList<>();
        g.add(t(2, 0, 2, 14, 6, 14));
        g.add(b(1.5, 5.5, 1.5, 14.5, 6.5, 14.5, WHITE));
        for (double[] c : new double[][]{{2, 2}, {13, 2}, {2, 13}, {13, 13}}) g.add(b(c[0], 6.5, c[1], c[0] + 1, 13, c[1] + 1, WHITE));
        g.add(b(2.5, 7, 2.5, 13.5, 12, 13.5, GLASS));
        g.add(t(1, 13, 1, 15, 15, 15));
        g.add(t(3, 15, 3, 13, 16.5, 13));
        g.add(b(6, 16.5, 6, 10, 17.5, 10, GOLD));
        g.add(b(7.5, 17.5, 7.5, 8.5, 26, 8.5, GREY));                    // attache
        g.add(b(3, 6, 3, 13, 7, 5, DARK));                                // banc
        MODELS.put("nacelle", g);

        // auto-tamponneuse (carrosserie teinte, pare-chocs noir, siège, volant, perche)
        List<El> a = new ArrayList<>();
        a.add(b(0.5, 0, -1.5, 15.5, 3, 17.5, BLACK));                    // pare-chocs
        a.add(t(1.5, 2, -0.5, 14.5, 7, 16.5));                           // carrosserie
        a.add(t(2.5, 7, 9, 13.5, 9, 16));                                 // capot
        a.add(b(4, 7, 0.5, 12, 15, 3.5, BLACK));                          // dossier
        a.add(b(4, 7, 3.5, 12, 8, 8.5, DARK));                            // siège
        a.add(r(b(7.5, 7, 8.5, 8.5, 12, 9.5, GREY), 'x', -22.5, 8, 7, 9));// colonne de direction
        a.add(b(5.5, 11.5, 9.5, 10.5, 12.5, 10.5, BLACK));                // volant
        a.add(b(3, 4, 16.4, 5, 6, 16.8, CREAM));                          // phares
        a.add(b(11, 4, 16.4, 13, 6, 16.8, CREAM));
        a.add(b(7.4, 15, 1, 8.6, 32, 2.2, GREY));                         // perche
        MODELS.put("auto", a);

        // wagon de montagnes russes (caisse teinte, nez profilé, siège, barre de sécurité)
        List<El> w = new ArrayList<>();
        w.add(b(1, 0, 0, 15, 2, 16, DARK));                               // châssis
        w.add(t(1, 2, 0, 15, 9, 13));
        w.add(r(t(1, 2, 12, 15, 8, 18), 'x', -22.5, 8, 2, 13));           // nez
        w.add(b(3, 9, 0.5, 13, 14, 2.5, BLACK));                          // dossier
        w.add(b(3, 9, 2.5, 13, 10, 9, DARK));                             // siège
        w.add(b(2.5, 12, 9, 13.5, 13, 10, GREY));                         // barre
        w.add(b(1, 4, 0, 15, 5, 13, GOLD));                               // liseré
        MODELS.put("wagon", w);

        // pot de Nutella de Julian
        List<El> n = new ArrayList<>();
        n.add(b(4.5, 0, 4.5, 11.5, 8, 11.5, BROWN));
        n.add(b(4.4, 2, 4.4, 11.6, 6.5, 11.6, WHITE));
        n.add(b(6.5, 3, 11.55, 9.5, 5.5, 11.7, RED));                     // étiquette
        n.add(b(4, 8, 4, 12, 10, 12, WHITE));                             // couvercle
        MODELS.put("pot_nutella", n);

        // doigt de pied de Julian
        List<El> d = new ArrayList<>();
        d.add(b(5.5, 0, 3, 10.5, 4.5, 12, SKIN));
        d.add(b(6, 0.5, 11.5, 10, 4, 13.5, SKIN));
        d.add(b(6.5, 3.8, 9.5, 9.5, 4.8, 13, NAIL));                      // ongle
        d.add(b(5.4, 0, 3, 10.6, 3.5, 4, 0xD98F7A));                      // pliure
        MODELS.put("doigt_pied", d);

        // ---------------------------------------------------------------- stands (0.4.0, Esteban 27/11)
        final int WATER = 0x2E8FD8, WATER_L = 0x7CC7F2, YELLOW = 0xF5D21E, ORANGE = 0xF08A1C, PINK = 0xFFA8D2,
                PINK_D = 0xE0609A, WOOD = 0x8A5A2B, WICKER = 0xB07A3C, CREPE = 0xE8B062, CREPE_D = 0xC98A3E,
                JAM = 0xA8182C, STEEL = 0xC9CED6;

        // bassin de la pêche aux canards (échelle 2 : 3 x 2 blocs), bord rayé rouge et blanc, eau bleue
        List<El> bs = new ArrayList<>();
        bs.add(b(-3, 0, 1, 19, 5, 15, WATER));
        bs.add(b(0, 5, 4, 5, 5.15, 6, WATER_L));                         // reflets
        bs.add(b(11, 5, 10, 17, 5.15, 12, WATER_L));
        for (int k = 0; k < 6; k++) {
            int c = k % 2 == 0 ? RED : WHITE;
            bs.add(b(-4 + 4 * k, 0, 0, 4 * k, 6, 1, c));                // bord avant
            bs.add(b(-4 + 4 * k, 0, 15, 4 * k, 6, 16, k % 2 == 0 ? WHITE : RED)); // bord arrière
        }
        bs.add(b(-4, 0, 1, -3, 6, 8, WHITE));
        bs.add(b(-4, 0, 8, -3, 6, 15, RED));
        bs.add(b(19, 0, 1, 20, 6, 8, RED));
        bs.add(b(19, 0, 8, 20, 6, 15, WHITE));
        MODELS.put("bassin", bs);

        // canard en plastique
        List<El> ca = new ArrayList<>();
        ca.add(b(5, 0, 3, 11, 5, 12, YELLOW));                           // corps
        ca.add(b(6, 3, 2, 10, 6, 4, YELLOW));                            // queue
        ca.add(b(6, 5, 8, 10, 9.5, 12, YELLOW));                         // tête
        ca.add(b(7, 6, 12, 9, 7.5, 14.5, ORANGE));                       // bec
        ca.add(b(5.9, 7.5, 10, 6.1, 8.5, 11, BLACK));                    // yeux
        ca.add(b(9.9, 7.5, 10, 10.1, 8.5, 11, BLACK));
        MODELS.put("canard", ca);

        // peluche (lot), teinte
        List<El> pe = new ArrayList<>();
        pe.add(t(5, 0, 5, 11, 6, 10));                                   // corps
        pe.add(t(5.5, 6, 5.5, 10.5, 11, 10));                            // tête
        pe.add(t(5, 10, 7, 7, 12, 8.5));                                 // oreilles
        pe.add(t(9, 10, 7, 11, 12, 8.5));
        pe.add(b(7, 7, 10, 9, 8.5, 11, CREAM));                          // museau
        pe.add(b(6.5, 9, 9.9, 7.3, 9.8, 10.1, BLACK));                   // yeux
        pe.add(b(8.7, 9, 9.9, 9.5, 9.8, 10.1, BLACK));
        pe.add(t(3.5, 2, 6.5, 5, 5, 8.5));                               // bras
        pe.add(t(11, 2, 6.5, 12.5, 5, 8.5));
        pe.add(t(5.5, 0, 9.5, 7.5, 2, 12));                              // pattes
        pe.add(t(8.5, 0, 9.5, 10.5, 2, 12));
        pe.add(b(7, 5.5, 10, 9, 6.5, 10.6, RED));                        // nœud
        MODELS.put("peluche", pe);

        // machine à barbe à papa (chariot, cuve en inox, barbe rose dedans)
        List<El> mb = new ArrayList<>();
        mb.add(b(4, 0, 4, 12, 7, 12, PINK_D));
        mb.add(b(3.9, 3, 3.9, 12.1, 4, 12.1, WHITE));
        mb.add(b(2, 7, 2, 14, 8, 14, STEEL));                            // fond de la cuve
        mb.add(b(2, 8, 2, 14, 11, 3, STEEL));                            // parois
        mb.add(b(2, 8, 13, 14, 11, 14, STEEL));
        mb.add(b(2, 8, 3, 3, 11, 13, STEEL));
        mb.add(b(13, 8, 3, 14, 11, 13, STEEL));
        mb.add(b(4, 8, 4, 12, 12, 12, PINK));                            // barbe qui tourne
        mb.add(b(5.5, 12, 5.5, 10.5, 13, 10.5, PINK));
        MODELS.put("barbe_machine", mb);

        // barbe à papa sur son bâton (teinte)
        List<El> bp = new ArrayList<>();
        bp.add(b(7.5, 0, 7.5, 8.5, 9, 8.5, CREAM));
        bp.add(t(5, 8, 5, 11, 15, 11));
        bp.add(t(4, 9.5, 6, 12, 13.5, 10));
        bp.add(t(6, 9.5, 4, 10, 13.5, 12));
        MODELS.put("barbe_papa", bp);

        // boîte de conserve du chamboule-tout (teinte)
        List<El> co = new ArrayList<>();
        co.add(t(5, 0.8, 5, 11, 8.2, 11));
        co.add(b(4.8, 0, 4.8, 11.2, 0.8, 11.2, GREY));
        co.add(b(4.8, 8.2, 4.8, 11.2, 9, 11.2, GREY));
        co.add(b(4.95, 3.5, 4.95, 11.05, 5.5, 11.05, WHITE));            // étiquette
        MODELS.put("conserve", co);

        // panier de balles
        List<El> pa = new ArrayList<>();
        pa.add(b(2, 0, 2, 14, 1, 14, WICKER));
        pa.add(b(2, 1, 2, 14, 6, 3, WICKER));
        pa.add(b(2, 1, 13, 14, 6, 14, WICKER));
        pa.add(b(2, 1, 3, 3, 6, 13, WICKER));
        pa.add(b(13, 1, 3, 14, 6, 13, WICKER));
        for (double[] c : new double[][]{{4, 4}, {8.5, 4.5}, {4.5, 8.5}, {8.5, 8.5}, {6.5, 6.5}}) {
            double hb = c[0] == 6.5 ? 3 : 1;
            pa.add(b(c[0], hb, c[1], c[0] + 3.5, hb + 3.5, c[1] + 3.5, RED));
            pa.add(b(c[0] - 0.05, hb + 1.6, c[1] - 0.05, c[0] + 3.55, hb + 1.9, c[1] + 3.55, WHITE));
        }
        MODELS.put("panier_balles", pa);

        // carabine du tir (couchée, le long de x)
        List<El> cb = new ArrayList<>();
        cb.add(b(0, 0, 6.5, 5, 2.5, 9.5, WOOD));                         // crosse
        cb.add(b(5, 1, 7, 12, 2.5, 9, DARK));                            // boîtier
        cb.add(b(12, 1.5, 7.5, 22, 2.3, 8.5, GREY));                     // canon
        cb.add(b(6, 0, 7.6, 7, 1, 8.4, DARK));                           // pontet
        cb.add(b(20.5, 2.3, 7.8, 21.1, 3, 8.2, DARK));                   // guidon
        cb.add(b(9, 0.5, 6.9, 13, 2.2, 9.1, WOOD));                      // garde-main
        MODELS.put("carabine", cb);

        // cible ronde (mêmes anneaux des deux côtés)
        List<El> ci = new ArrayList<>();
        ci.add(b(2, 2, 7, 14, 14, 9, WHITE));
        ci.add(b(3, 1, 7, 13, 15, 9, WHITE));
        ci.add(b(1, 3, 7, 15, 13, 9, WHITE));
        ci.add(b(4, 4, 6.9, 12, 12, 9.1, RED));
        ci.add(b(5.5, 5.5, 6.8, 10.5, 10.5, 9.2, WHITE));
        ci.add(b(7, 7, 6.7, 9, 9, 9.3, RED));
        MODELS.put("cible", ci);

        // crêpière (plaque ronde sur son socle, une crêpe qui cuit)
        List<El> cr = new ArrayList<>();
        cr.add(b(4, 0, 4, 12, 6, 12, DARK));
        cr.add(b(7, 3, 12, 9, 4, 12.6, RED));                            // bouton
        cr.add(b(1, 6, 3, 15, 7, 13, BLACK));
        cr.add(b(3, 6, 1, 13, 7, 15, BLACK));
        cr.add(b(3, 7, 4, 13, 7.3, 12, CREPE));
        cr.add(b(4, 7, 3, 12, 7.3, 13, CREPE));
        MODELS.put("crepiere", cr);

        // assiette de crêpes pliées, filet de chocolat
        List<El> cp = new ArrayList<>();
        cp.add(b(2, 0, 2, 14, 0.6, 14, WHITE));
        cp.add(b(3, 0.6, 3, 12, 1.6, 9, CREPE));
        cp.add(b(3.5, 1.6, 3.5, 11, 2.6, 8.5, CREPE_D));
        cp.add(b(4, 2.6, 4, 10, 3.6, 8, CREPE));
        cp.add(b(4.5, 3.6, 5, 9.5, 3.8, 6, BROWN));
        cp.add(b(4, 0.6, 9.5, 12, 1.6, 13, CREPE_D));
        MODELS.put("crepes_assiette", cp);

        // pot de confiture (couvercle vichy)
        List<El> pc = new ArrayList<>();
        pc.add(b(5, 0, 5, 11, 7, 11, JAM));
        pc.add(b(4.5, 7, 4.5, 11.5, 8.5, 11.5, RED));
        pc.add(b(4.4, 7.5, 4.4, 11.6, 8, 11.6, WHITE));
        pc.add(b(6, 2.5, 10.95, 10, 5, 11.1, CREAM));                    // étiquette
        MODELS.put("pot_confiture", pc);
    }

    /** Modèles teints (dyed_color) : la couleur est donnée par l'objet. */
    public static boolean tinted(String id) {
        for (El e : MODELS.getOrDefault(id, List.of())) if (e.tint()) return true;
        return false;
    }
}
