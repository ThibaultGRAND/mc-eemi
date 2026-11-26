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

        // pot de Nutella de Julien
        List<El> n = new ArrayList<>();
        n.add(b(4.5, 0, 4.5, 11.5, 8, 11.5, BROWN));
        n.add(b(4.4, 2, 4.4, 11.6, 6.5, 11.6, WHITE));
        n.add(b(6.5, 3, 11.55, 9.5, 5.5, 11.7, RED));                     // étiquette
        n.add(b(4, 8, 4, 12, 10, 12, WHITE));                             // couvercle
        MODELS.put("pot_nutella", n);

        // doigt de pied de Julien
        List<El> d = new ArrayList<>();
        d.add(b(5.5, 0, 3, 10.5, 4.5, 12, SKIN));
        d.add(b(6, 0.5, 11.5, 10, 4, 13.5, SKIN));
        d.add(b(6.5, 3.8, 9.5, 9.5, 4.8, 13, NAIL));                      // ongle
        d.add(b(5.4, 0, 3, 10.6, 3.5, 4, 0xD98F7A));                      // pliure
        MODELS.put("doigt_pied", d);
    }

    /** Modèles teints (dyed_color) : la couleur est donnée par l'objet. */
    public static boolean tinted(String id) {
        for (El e : MODELS.getOrDefault(id, List.of())) if (e.tint()) return true;
        return false;
    }
}
