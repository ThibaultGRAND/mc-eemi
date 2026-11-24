package fr.eemicraft.murder.build;

/**
 * Relie les vitres, barreaux, barrières et murets ISOLÉS d'une boîte (toutes connexions à false/none), comme le
 * fait le jeu à la pose : sans physique (BukkitSink, schematic collé), ils restent sinon en poteaux.
 * Ne touche pas aux blocs qui ont déjà au moins une connexion (les vitres posées par {@link Facade} gardent les
 * leurs). Heuristique sur les noms (pas de serveur) : se relie à un bloc plein ({@link B#isSolid}) ou de la
 * même famille.
 */
public final class Connect {
    private Connect() {}

    private static final Dir[] D = Dir.values();

    public static int fix(Sink s, int x1, int y1, int z1, int x2, int y2, int z2) {
        int n = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    String st = s.get(x, y, z);
                    int k = kind(st);
                    if (k == 0 || !isolated(st)) continue;
                    String r = st;
                    int cnt = 0;
                    boolean[] c = new boolean[4];
                    for (Dir d : D) {
                        String nb = s.get(x + d.dx, y, z + d.dz);
                        boolean ok = connects(k, st, nb, d);
                        c[d.ordinal()] = ok;
                        if (ok) cnt++;
                    }
                    for (Dir d : D) {
                        boolean ok = c[d.ordinal()];
                        if (k == 4) {
                            String v = ok ? (B.isSolid(s.get(x, y + 1, z)) ? "tall" : "low") : "none";
                            r = B.with(r, d.id(), v);
                        } else r = B.with(r, d.id(), String.valueOf(ok));
                    }
                    if (k == 4) {
                        boolean straight = cnt == 2 && ((c[0] && c[2]) || (c[1] && c[3]));
                        r = B.with(r, "up", String.valueOf(!straight || B.isSolid(s.get(x, y + 1, z))));
                    }
                    if (!r.equals(st)) { s.set(x, y, z, r); n++; }
                }
        return n;
    }

    /** 1 vitre/barreaux, 2 barrière en bois, 3 barrière en briques du Nether, 4 muret, 0 autre. */
    static int kind(String st) {
        if (st == null) return 0;
        String n = B.name(st);
        if (n.endsWith("_pane") || n.equals("iron_bars") || n.equals("glass_pane")) return 1;
        if (n.equals("nether_brick_fence")) return 3;
        if (n.endsWith("_fence")) return 2;
        if (n.endsWith("_wall") && !n.contains("sign") && !n.contains("banner") && !n.contains("torch")
                && !n.contains("head") && !n.contains("skull") && !n.contains("fan")) return 4;
        return 0;
    }

    private static boolean isolated(String st) {
        for (Dir d : D) {
            String v = B.prop(st, d.id());
            if (v != null && !v.equals("false") && !v.equals("none")) return false;
        }
        return true;
    }

    private static boolean connects(int k, String self, String nb, Dir d) {
        if (B.isAir(nb)) return false;
        int kn = kind(nb);
        String n = B.name(nb);
        if (n.endsWith("_fence_gate")) {
            String f = B.prop(nb, "facing");
            return f != null && Dir.of(f).axis() != d.axis() && (k == 2 || k == 3 || k == 4);
        }
        switch (k) {
            case 1: return kn == 1 || kn == 4 || B.isSolid(nb);
            case 2: return kn == 2 || B.isSolid(nb);
            case 3: return kn == 3 || B.isSolid(nb);
            case 4: return kn == 4 || kn == 1 || B.isSolid(nb);
            default: return false;
        }
    }
}
