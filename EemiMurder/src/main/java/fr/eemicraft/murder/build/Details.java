package fr.eemicraft.murder.build;

/**
 * Passe de finition (1.9.1) sur une boîte, après tout le dessin : remet contre leur support les détails posés
 * dans le mauvais sens (posés sans physique, ils restent sinon tels quels) :
 * <ul>
 *   <li>trappe ouverte dont le panneau (côté OPPOSÉ à facing) est dans le vide alors que l'autre côté est
 *       plein : facing inversé, le panneau se plaque contre le bloc ;</li>
 *   <li>lanterne posée sur du vide mais avec un porteur au-dessus → suspendue, et l'inverse ;</li>
 *   <li>paratonnerre / barre de l'End sans appui dans son sens : réorienté vers un appui (bloc dessous →
 *       debout, sinon contre un mur) ; laissé tel quel s'il n'y en a aucun (le contrôle hors ligne le signale).</li>
 * </ul>
 * Ne touche jamais à un détail déjà bien appuyé. Heuristique sur les noms (pas de serveur).
 */
public final class Details {
    private Details() {}

    private static final Dir[] D = Dir.values();

    /** Renvoie {trappes, lanternes, paratonnerres} corrigés. */
    public static int[] fix(Sink s, int x1, int y1, int z1, int x2, int y2, int z2) {
        int traps = 0, lanterns = 0, rods = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    String st = s.get(x, y, z);
                    if (B.isAir(st)) continue;
                    String n = B.name(st);
                    if (n.endsWith("_trapdoor") && "true".equals(B.prop(st, "open"))) {
                        Dir f = Dir.of(B.prop(st, "facing"));
                        if (!backs(s, x, y, z, f.opposite(), f) && backs(s, x, y, z, f, f.opposite())) {
                            s.set(x, y, z, B.with(st, "facing", f.opposite().id()));
                            traps++;
                            if (DEBUG) System.out.println("Details : trappe " + x + "," + y + "," + z + " -> " + f.opposite().id());
                        }
                    } else if (n.equals("lantern") || n.equals("soul_lantern")) {
                        boolean hanging = "true".equals(B.prop(st, "hanging"));
                        boolean up = holds(s.get(x, y + 1, z)), down = holds(s.get(x, y - 1, z));
                        if (hanging && !up && down) { s.set(x, y, z, B.with(st, "hanging", "false")); lanterns++; }
                        else if (!hanging && !down && up) { s.set(x, y, z, B.with(st, "hanging", "true")); lanterns++; }
                    } else if (n.equals("lightning_rod") || n.equals("end_rod")) {
                        String f = B.prop(st, "facing");
                        if (f == null) f = "up";
                        if (rodHeld(s, x, y, z, f)) continue;
                        String nf = null;
                        if (holds(s.get(x, y - 1, z))) nf = "up";
                        else for (Dir d : D) if (full(s.get(x + d.dx, y, z + d.dz))) { nf = d.opposite().id(); break; }
                        if (nf == null && holds(s.get(x, y + 1, z))) nf = "down";
                        if (nf != null) {
                            s.set(x, y, z, B.with(st, "facing", nf));
                            rods++;
                            if (DEBUG) System.out.println("Details : paratonnerre " + x + "," + y + "," + z + " -> " + nf);
                        }
                    }
                }
        STATS[0] += traps;
        STATS[1] += lanterns;
        STATS[2] += rods;
        return new int[]{traps, lanterns, rods};
    }

    /** Cumul des corrections depuis le dernier {@code Arrays.fill(STATS, 0)} (lu par l'outil de test hors serveur). */
    public static final int[] STATS = new int[3];
    /** Trace des corrections (outil de test : -Ddetails.debug=true). */
    static final boolean DEBUG = Boolean.getBoolean("details.debug");

    private static boolean rodHeld(Sink s, int x, int y, int z, String f) {
        if (f.equals("up")) return holds(s.get(x, y - 1, z));
        if (f.equals("down")) return holds(s.get(x, y + 1, z));
        Dir d = Dir.of(f).opposite();
        return holds(s.get(x + d.dx, y, z + d.dz));
    }

    /** Le panneau d'une trappe (orientée {@code f}) peut-il se plaquer contre la case du côté {@code side} ? */
    private static boolean backs(Sink s, int x, int y, int z, Dir side, Dir f) {
        String b = s.get(x + side.dx, y, z + side.dz);
        if (full(b)) return true;
        String n = B.name(b);
        if (n.endsWith("_stairs")) return true; // accoudoir contre le flanc d'un siege
        return n.endsWith("_trapdoor") && "true".equals(B.prop(b, "open")) && side.id().equals(B.prop(b, "facing"));
    }

    /** Face pleine (bloc plein, dalle double, feuilles, verre, tonneau, bibliothèque). */
    public static boolean full(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.endsWith("_slab")) return "double".equals(B.prop(st, "type"));
        return B.isSolid(st) || n.endsWith("leaves") || n.equals("barrel") || n.endsWith("glass")
                || n.equals("bamboo_planks") || n.equals("bamboo_mosaic") || n.equals("bamboo_block");
    }

    /** Peut porter un objet posé dessus ou suspendu dessous (plein, dalle, escalier, barrière, muret, chaîne...). */
    public static boolean holds(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.equals("light") || n.equals("water") || n.equals("lava") || n.equals("cobweb") || n.endsWith("_carpet")
                || n.contains("torch") || n.endsWith("_button") || n.endsWith("pressure_plate") || n.endsWith("rail")
                || n.contains("candle") || n.equals("ladder") || n.equals("vine") || n.contains("sign") || n.contains("banner")
                || n.equals("lantern") || n.equals("soul_lantern") || n.equals("short_grass") || n.equals("tall_grass")
                || n.equals("fern") || n.equals("large_fern") || n.endsWith("sapling") || n.equals("lily_pad")
                || n.startsWith("potted_") || n.equals("flower_pot") || n.endsWith("_trapdoor") && "true".equals(B.prop(st, "open")))
            return false;
        // chaine couchee : pas de face basse porteuse (une lanterne n'y tient pas dans le jeu)
        if (n.equals("iron_chain") || n.equals("chain")) { String a = B.prop(st, "axis"); return a == null || a.equals("y"); }
        return true;
    }
}
