package fr.eemicraft.infection;

import java.util.ArrayDeque;

/**
 * Accessibilite a pied (sans Bukkit, testable hors serveur) : a partir de cases de depart dans la rue, on parcourt
 * toutes les positions qu'un joueur peut atteindre en marchant, en montant une marche de 1, en tombant et en grimpant
 * aux echelles / lianes / echafaudages. Sert a ne garder que des cases de dispersion d'ou l'on peut sortir
 * (avant : une case « sol + 2 d'air » dans une piece fermee, un comble ou un creux de facade = joueur bloque).
 */
final class Reach {
    /** Bit 1 = on peut y avoir le corps (pas de collision) ; bit 2 = on peut y grimper. */
    static final int PASS = 1, CLIMB = 2;

    interface Kind {
        int at(int x, int y, int z);
    }

    final int lim, y0, y1, nx, ny;
    final byte[] kind;
    final boolean[] seen;

    /** Boite x/z -lim..lim, y y0..y1 (y0 = niveau du sol le plus bas possible sous des pieds). */
    Reach(Kind k, int lim, int y0, int y1) {
        this.lim = lim;
        this.y0 = y0;
        this.y1 = y1;
        this.nx = 2 * lim + 1;
        this.ny = y1 - y0 + 1;
        kind = new byte[nx * ny * nx];
        seen = new boolean[kind.length];
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

    /** Un corps tient en (x,y,z) : pieds et tete libres. */
    boolean body(int x, int y, int z) {
        return pass(x, y, z) && pass(x, y + 1, z);
    }

    /** Le joueur ne tombe pas : sol dessous (bloc non traversable) ou case grimpable. */
    boolean supported(int x, int y, int z) {
        return climb(x, y, z) || (in(x, y - 1, z) && !pass(x, y - 1, z));
    }

    boolean reached(int x, int y, int z) {
        return in(x, y, z) && seen[idx(x, y, z)];
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
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], z = p[2];
            if (!supported(x, y, z)) {
                // chute
                n += push(q, x, y - 1, z);
                continue;
            }
            for (int[] d : dirs) {
                int ax = x + d[0], az = z + d[1];
                n += push(q, ax, y, az);
                // marche de 1 (tete libre au-dessus de soi pour sauter)
                if (pass(x, y + 2, z)) {
                    n += push(q, ax, y + 1, az);
                }
            }
            if (climb(x, y, z) || climb(x, y + 1, z)) {
                n += push(q, x, y + 1, z);
            }
            if (climb(x, y - 1, z)) {
                n += push(q, x, y - 1, z);
            }
        }
        return n;
    }

    private int push(ArrayDeque<int[]> q, int x, int y, int z) {
        if (!body(x, y, z)) {
            return 0;
        }
        int i = idx(x, y, z);
        if (seen[i]) {
            return 0;
        }
        seen[i] = true;
        q.add(new int[]{x, y, z});
        return 1;
    }
}
