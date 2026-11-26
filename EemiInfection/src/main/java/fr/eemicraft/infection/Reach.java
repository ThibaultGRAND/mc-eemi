package fr.eemicraft.infection;

import java.util.ArrayDeque;

/**
 * Accessibilite a pied (sans Bukkit, testable hors serveur) : a partir de cases de depart dans la rue, on parcourt
 * toutes les positions qu'un joueur peut atteindre en marchant, en montant une marche de 1, en tombant et en grimpant
 * aux echelles / lianes / echafaudages. Sert a ne garder que des cases de dispersion d'ou l'on peut sortir
 * (avant : une case "sol + 2 d'air" dans une piece fermee, un comble ou un creux de facade = joueur bloque).
 *
 * <p>1.9.0 (carte #136, "on spawn dans des zones qui nous bloquent") : le parcours ne se faisait que DEPUIS la rue.
 * Une case atteinte en tombant (cour fermee, puits de lumiere, toit -> piece sans porte) ou en montant sur une cloture
 * etait gardee alors qu'on ne pouvait pas en ressortir. Maintenant :
 * <ul>
 *   <li>parcours inverse ({@link #runBack}) : on ne garde que les cases d'ou l'on REJOINT la rue ;</li>
 *   <li>clotures, murets (collision de 1,5) : on ne tient pas debout dessus ({@link #TALL}) ;</li>
 *   <li>marge ({@link #roomy}) : 3 cases libres en hauteur et les 4 voisines praticables au meme niveau.</li>
 * </ul>
 */
final class Reach {
    /** Bit 1 = on peut y avoir le corps (pas de collision) ; bit 2 = on peut y grimper ; bit 4 = collision de plus d'un bloc. */
    static final int PASS = 1, CLIMB = 2, TALL = 4;

    interface Kind {
        int at(int x, int y, int z);
    }

    final int lim, y0, y1, nx, ny;
    final byte[] kind;
    final boolean[] seen;
    final boolean[] back;

    /** Boite x/z -lim..lim, y y0..y1 (y0 = niveau du sol le plus bas possible sous des pieds). */
    Reach(Kind k, int lim, int y0, int y1) {
        this.lim = lim;
        this.y0 = y0;
        this.y1 = y1;
        this.nx = 2 * lim + 1;
        this.ny = y1 - y0 + 1;
        kind = new byte[nx * ny * nx];
        seen = new boolean[kind.length];
        back = new boolean[kind.length];
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                for (int y = y0; y <= y1; y++) {
                    kind[idx(x, y, z)] = (byte) k.at(x, y, z);
                }
            }
        }
    }

    int idx(int x, int y, int z) {
        return ((x + lim) * ny + (y - y0)) * nx + (z + lim);
    }

    boolean in(int x, int y, int z) {
        return x >= -lim && x <= lim && z >= -lim && z <= lim && y >= y0 && y <= y1;
    }

    boolean pass(int x, int y, int z) {
        return in(x, y, z) && (kind[idx(x, y, z)] & PASS) != 0;
    }

    boolean climb(int x, int y, int z) {
        return in(x, y, z) && (kind[idx(x, y, z)] & CLIMB) != 0;
    }

    boolean tall(int x, int y, int z) {
        return in(x, y, z) && (kind[idx(x, y, z)] & TALL) != 0;
    }

    /** Un corps tient en (x,y,z) : pieds et tete libres, et pas a cheval sur le haut d'une cloture / d'un muret. */
    boolean body(int x, int y, int z) {
        return pass(x, y, z) && pass(x, y + 1, z) && !tall(x, y - 1, z);
    }

    /** Le joueur ne tombe pas : sol dessous (bloc non traversable) ou case grimpable. */
    boolean supported(int x, int y, int z) {
        return climb(x, y, z) || (in(x, y - 1, z) && !pass(x, y - 1, z));
    }

    /** Atteinte depuis la rue (parcours direct). */
    boolean reached(int x, int y, int z) {
        return in(x, y, z) && seen[idx(x, y, z)];
    }

    /** On peut rejoindre la rue depuis cette position (parcours inverse). */
    boolean exits(int x, int y, int z) {
        return in(x, y, z) && back[idx(x, y, z)];
    }

    /** Dans la grande zone de rue : on y arrive depuis la rue ET on en ressort. */
    boolean connected(int x, int y, int z) {
        return reached(x, y, z) && exits(x, y, z);
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Marge pour une case d'apparition : 3 cases libres en hauteur (pas sous une dalle ni une poutre) et les 4 cases
     * voisines praticables au meme niveau, sur un sol plein, et reliees a la rue (pas colle a un mur, ni au bord d'un vide).
     */
    boolean roomy(int x, int y, int z) {
        if (!connected(x, y, z) || !pass(x, y + 2, z)) {
            return false;
        }
        for (int[] d : DIRS) {
            int ax = x + d[0], az = z + d[1];
            if (!body(ax, y, az) || pass(ax, y - 1, az) || !connected(ax, y, az)) {
                return false;
            }
        }
        return true;
    }

    /** Parcours depuis les positions de depart valides ; renvoie le nombre de positions atteintes. */
    int run(int[][] seeds) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] s : seeds) {
            if (body(s[0], s[1], s[2]) && !seen[idx(s[0], s[1], s[2])]) {
                seen[idx(s[0], s[1], s[2])] = true;
                q.add(s);
            }
        }
        int n = q.size();
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], z = p[2];
            if (!supported(x, y, z)) {
                // chute
                n += push(q, seen, x, y - 1, z);
                continue;
            }
            for (int[] d : DIRS) {
                int ax = x + d[0], az = z + d[1];
                n += push(q, seen, ax, y, az);
                // marche de 1 (tete libre au-dessus de soi pour sauter)
                if (pass(x, y + 2, z)) {
                    n += push(q, seen, ax, y + 1, az);
                }
            }
            if (climb(x, y, z) || climb(x, y + 1, z)) {
                n += push(q, seen, x, y + 1, z);
            }
            if (climb(x, y - 1, z)) {
                n += push(q, seen, x, y - 1, z);
            }
        }
        return n;
    }

    /**
     * Parcours inverse : toutes les positions depuis lesquelles un joueur peut rejoindre l'une des positions de depart
     * avec les memes mouvements que {@link #run} (on remonte chaque mouvement a l'envers).
     */
    int runBack(int[][] seeds) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] s : seeds) {
            if (body(s[0], s[1], s[2]) && !back[idx(s[0], s[1], s[2])]) {
                back[idx(s[0], s[1], s[2])] = true;
                q.add(s);
            }
        }
        int n = q.size();
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int x = c[0], y = c[1], z = c[2];
            // chute depuis la case du dessus (non soutenue)
            if (body(x, y + 1, z) && !supported(x, y + 1, z)) {
                n += push(q, back, x, y + 1, z);
            }
            for (int[] d : DIRS) {
                int px = x - d[0], pz = z - d[1];
                // marche a plat depuis une case soutenue
                if (body(px, y, pz) && supported(px, y, pz)) {
                    n += push(q, back, px, y, pz);
                }
                // marche de 1 depuis une case soutenue avec la tete libre au-dessus
                if (body(px, y - 1, pz) && supported(px, y - 1, pz) && pass(px, y + 1, pz)) {
                    n += push(q, back, px, y - 1, pz);
                }
            }
            // montee a l'echelle depuis la case du dessous
            if (body(x, y - 1, z) && supported(x, y - 1, z) && (climb(x, y - 1, z) || climb(x, y, z))) {
                n += push(q, back, x, y - 1, z);
            }
            // descente a l'echelle depuis la case du dessus
            if (climb(x, y, z) && body(x, y + 1, z) && supported(x, y + 1, z)) {
                n += push(q, back, x, y + 1, z);
            }
        }
        return n;
    }

    private int push(ArrayDeque<int[]> q, boolean[] mark, int x, int y, int z) {
        if (!body(x, y, z)) {
            return 0;
        }
        int i = idx(x, y, z);
        if (mark[i]) {
            return 0;
        }
        mark[i] = true;
        q.add(new int[]{x, y, z});
        return 1;
    }
}
