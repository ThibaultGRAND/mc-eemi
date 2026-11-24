package fr.eemicraft.soleil.build;

/**
 * Recalcule la propriété {@code shape} des escaliers d'une boîte avec la règle de Minecraft
 * (StairBlock.getStairsShape) : outer_left/right si l'escalier « derrière » (côté facing) est perpendiculaire,
 * inner_left/right si c'est celui « devant ». Posés sans physique, les escaliers gardent sinon shape=straight.
 * Il suffit donc de poser chaque escalier avec le bon facing/half, puis d'appeler fix sur la zone.
 */
public final class Stairs {
    private Stairs() {}

    static boolean isStairs(String s) { return s != null && B.name(s).endsWith("_stairs"); }

    /** Renvoie le nombre d'escaliers modifiés. */
    public static int fix(Sink s, int x1, int y1, int z1, int x2, int y2, int z2) {
        int n = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    String st = s.get(x, y, z);
                    if (!isStairs(st)) continue;
                    String shape = shape(s, x, y, z, st);
                    if (!shape.equals(B.prop(st, "shape"))) {
                        s.set(x, y, z, B.with(st, "shape", shape));
                        n++;
                    }
                }
        return n;
    }

    /** Forme qu'aurait cet escalier dans le jeu, d'après ses voisins. */
    public static String shape(Sink s, int x, int y, int z, String st) {
        String fs = B.prop(st, "facing");
        if (fs == null) return "straight";
        Dir f = Dir.of(fs);
        String half = B.prop(st, "half");
        String behind = s.get(x + f.dx, y, z + f.dz);
        if (isStairs(behind) && eq(half, B.prop(behind, "half"))) {
            Dir bf = Dir.of(B.prop(behind, "facing"));
            if (bf.axis() != f.axis() && canTakeShape(s, x, y, z, f, half, bf.opposite()))
                return bf == f.ccw() ? "outer_left" : "outer_right";
        }
        String front = s.get(x - f.dx, y, z - f.dz);
        if (isStairs(front) && eq(half, B.prop(front, "half"))) {
            Dir ff = Dir.of(B.prop(front, "facing"));
            if (ff.axis() != f.axis() && canTakeShape(s, x, y, z, f, half, ff))
                return ff == f.ccw() ? "inner_left" : "inner_right";
        }
        return "straight";
    }

    private static boolean canTakeShape(Sink s, int x, int y, int z, Dir f, String half, Dir d) {
        String n = s.get(x + d.dx, y, z + d.dz);
        return !isStairs(n) || !f.id().equals(B.prop(n, "facing")) || !eq(half, B.prop(n, "half"));
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
}
