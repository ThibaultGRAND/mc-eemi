package fr.eemicraft.sheepwars.gen.build;

import java.util.EnumSet;

/**
 * Façade en 3 plans (règles C1-C7 du skill). Pour un mur dont la ligne extérieure est P :
 * <ul>
 * <li>plan P (saillie, la ligne donnée) : socle sur le rang du bas (+ fondation descendue jusqu'au sol),
 * angles et chaînage d'angle (angle + poteau voisin de chaque côté), poteaux tous les 4-6, bandeaux à chaque
 * plancher et corniche en escaliers retournés, appuis de fenêtre, volets (trappes ouvertes) ;</li>
 * <li>plan P-1 (remplissage) : le mur proprement dit, vitres des fenêtres dans ce plan, linteau en dalle ;</li>
 * <li>plan P-2 (intérieur) : pilastres intérieurs au droit des poteaux (jamais de vitre ici : elle flotterait dans la pièce).</li>
 * </ul>
 * Tout reste dans l'emprise : la ligne donnée est la face la plus extérieure, rien ne dépasse dehors.
 * Hauteurs : {@code yBase} = rang du sol intérieur (et du socle) ; porte en yBase+1..+2 ; {@code yTop} = rang
 * de la corniche. Un étage tous les {@code style.storey} blocs.
 */
public final class Facade {
    private Facade() {}

    /**
     * Positions des poteaux le long d'un mur de longueur {@code len} (t de 0 à len, angles en 0 et len) :
     * le premier en 1 et le dernier en len-1 (chaînage d'angle), les autres répartis au plus juste.
     */
    public static int[] postPositions(int len, int spacing) {
        if (len < 2) return new int[0];
        if (len == 2) return new int[]{1};
        int span = len - 2;
        int n = Math.max(1, (int) Math.round(span / (double) Math.max(2, spacing)));
        int[] r = new int[n + 1];
        for (int i = 0; i <= n; i++) r[i] = 1 + (int) Math.round(i * span / (double) n);
        return r;
    }

    /**
     * Pose un mur droit. (x0, z0) = extrémité de la ligne extérieure P ; le mur va de t = 0 à len vers
     * {@code along} ; {@code out} = côté extérieur (perpendiculaire à along). {@code doorT} = position de la
     * porte le long du mur (-1 = pas de porte ; décalée si elle tombe sur un poteau).
     */
    public static void wall(Sink s, Style st, int x0, int z0, Dir along, int len, Dir out,
                            int yBase, int yTop, int doorT) {
        if (along.axis() == out.axis()) throw new IllegalArgumentException("along et out doivent être perpendiculaires");
        Dir in = out.opposite();
        int[] posts = postPositions(len, st.postSpacing);
        boolean[] isPost = new boolean[len + 1];
        for (int p : posts) isPost[p] = true;
        if (doorT >= 0) {
            doorT = Math.max(2, Math.min(len - 2, doorT));
            if (doorT >= 0 && doorT <= len && isPost[doorT]) doorT = doorT + 1 <= len - 2 ? doorT + 1 : doorT - 1;
        }
        String postY = B.axis(st.post, 'y');
        String postH = B.axis(st.post, along.axis());
        Mat corner = st.corner != null ? st.corner : Mat.of(postY);
        long wid = st.seed * 31 + x0 * 73856093L + z0 * 19349663L + along.ordinal() * 7 + out.ordinal();

        // 1. socle + fondation (P et P-1)
        for (int t = 0; t <= len; t++) {
            int x = x0 + along.dx * t, z = z0 + along.dz * t;
            foundation(s, st, x, z, yBase);
            if (t > 0 && t < len) foundation(s, st, x + in.dx, z + in.dz, yBase);
        }
        // 2. plan P : angles et poteaux
        for (int t = 0; t <= len; t++) {
            int x = x0 + along.dx * t, z = z0 + along.dz * t;
            boolean c = t == 0 || t == len;
            if (!c && !isPost[t]) continue;
            for (int y = yBase + 1; y < yTop; y++) s.set(x, y, z, c ? corner.at(x, y, z) : postY);
        }
        // 3. remplissage P-1
        for (int t = 1; t < len; t++) {
            int x = x0 + along.dx * t + in.dx, z = z0 + along.dz * t + in.dz;
            for (int y = yBase + 1; y <= yTop; y++) s.set(x, y, z, st.wall);
        }
        // 4. bandeaux (P) à chaque plancher d'étage
        if (st.bandStairs != null)
            for (int k = 1; ; k++) {
                int yk = yBase + k * st.storey;
                if (yk >= yTop) break;
                for (int t = 1; t < len; t++) {
                    if (isPost[t]) continue;
                    s.set(x0 + along.dx * t, yk, z0 + along.dz * t, B.stairs(st.bandStairs, in, true));
                }
            }
        // 5. corniche (P, rang yTop)
        for (int t = 0; t <= len; t++) {
            int x = x0 + along.dx * t, z = z0 + along.dz * t;
            s.set(x, yTop, z, st.corniceStairs != null ? B.stairs(st.corniceStairs, in, true) : corner.at(x, yTop, z));
        }
        // 6. fenêtres, travée par travée
        String pane = along.axis() == 'x' ? B.pane(st.pane, false, true, false, true) : B.pane(st.pane, true, false, true, false);
        for (int i = 0; i + 1 < posts.length; i++) {
            int a = posts[i], b = posts[i + 1], m = b - a - 1;
            if (m <= 0) continue;
            boolean doorBay = doorT > a && doorT < b;
            for (int k = 0; ; k++) {
                int yk = yBase + k * st.storey;
                if (yk + st.storey > yTop) break;
                if (k == 0 && doorBay) continue;
                if (Noise.hash(wid, i, k, 0) < st.blankBay) continue;
                int wW = m >= 3 ? Math.min(3, m - 2) : m;
                int start = a + 1 + (m - wW) / 2;
                int wy0 = yk + 2, wy1 = Math.max(wy0, yk + st.storey - 1);
                for (int c = start; c < start + wW; c++) {
                    int x = x0 + along.dx * c, z = z0 + along.dz * c;
                    for (int y = wy0; y <= wy1; y++) {
                        // vitre DANS le plan du mur (P-1) : posee en P-2 elle flottait dans la piece, decalee de 1 (Esteban, 17/11/2026)
                        s.set(x + in.dx, y, z + in.dz, (y == wy1 && wy1 > wy0) ? B.slab(st.lintelSlab, "top") : pane);
                    }
                    s.set(x, yk + 1, z, B.stairs(st.sillStairs, in, true));
                }
                if (st.shutter != null && m >= wW + 2 && Noise.hash(wid, i, k, 1) < st.shutterChance) {
                    for (int c : new int[]{start - 1, start + wW}) {
                        if (c <= 0 || c >= len || isPost[c]) continue;
                        int x = x0 + along.dx * c, z = z0 + along.dz * c;
                        for (int y = wy0; y <= wy1; y++) s.set(x, y, z, B.trapdoor(st.shutter, out, false, true));
                    }
                }
            }
        }
        // 7. porte
        if (doorT > 0 && doorT < len) {
            int x = x0 + along.dx * doorT, z = z0 + along.dz * doorT;
            s.set(x, yBase, z, B.stairs(st.plinthStairs, in, false));
            s.set(x, yBase + 1, z, B.AIR);
            s.set(x, yBase + 2, z, B.AIR);
            s.set(x + in.dx, yBase + 1, z + in.dz, B.door(st.door, in, false, false));
            s.set(x + in.dx, yBase + 2, z + in.dz, B.door(st.door, in, true, false));
            if (yBase + 3 < yTop) s.set(x + in.dx, yBase + 3, z + in.dz, postH);
            s.set(x + 2 * in.dx, yBase + 1, z + 2 * in.dz, B.AIR);
            s.set(x + 2 * in.dx, yBase + 2, z + 2 * in.dz, B.AIR);
        }
    }

    /** Pilastres intérieurs (plan P-2) au droit des poteaux, posés seulement dans l'air. */
    public static void innerPosts(Sink s, Style st, int x0, int z0, Dir along, int len, Dir out, int yBase, int yTop) {
        Dir in = out.opposite();
        String postY = B.axis(st.post, 'y');
        int[] posts = postPositions(len, st.postSpacing);
        for (int p : posts) {
            if (p <= 1 || p >= len - 1) continue;
            int x = x0 + along.dx * p + 2 * in.dx, z = z0 + along.dz * p + 2 * in.dz;
            for (int y = yBase + 1; y < yTop; y++) if (s.isAir(x, y, z)) s.set(x, y, z, postY);
        }
    }

    private static void foundation(Sink s, Style st, int x, int z, int yBase) {
        s.set(x, yBase, z, st.plinth);
        for (int d = 1; d <= st.groundDepth; d++) {
            int y = yBase - d;
            if (s.isSolid(x, y, z)) break;
            s.set(x, y, z, st.plinth);
        }
    }

    /**
     * Bâtiment rectangulaire complet : intérieur vidé, sol, planchers d'étage, plafond en yTop, 4 façades
     * (sauf celles de {@code skip}, pour accoler une aile), porte sur {@code doorSide} en {@code doorT}
     * (-1 = milieu), pilastres intérieurs. Le toit se pose ensuite avec {@link Roof} (même yTop).
     * Pas d'escalier intérieur : à ajouter par le jeu si l'étage doit être accessible.
     */
    public static void building(Sink s, Style st, int x1, int z1, int x2, int z2, int yBase, int yTop,
                                Dir doorSide, int doorT, Dir... skip) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        EnumSet<Dir> sk = EnumSet.noneOf(Dir.class);
        for (Dir d : skip) sk.add(d);
        Shapes.fill(s, ax + 1, yBase + 1, az + 1, bx - 1, yTop - 1, bz - 1, Mat.AIR);
        Shapes.fill(s, ax + 1, yBase, az + 1, bx - 1, yBase, bz - 1, st.floor);
        for (int k = 1; ; k++) {
            int yk = yBase + k * st.storey;
            if (yk >= yTop) break;
            Shapes.fill(s, ax + 2, yk, az + 2, bx - 2, yk, bz - 2, st.floor);
        }
        Shapes.fill(s, ax + 2, yTop, az + 2, bx - 2, yTop, bz - 2, st.floor);
        int lx = bx - ax, lz = bz - az;
        int dN = doorSide == Dir.NORTH ? (doorT < 0 ? lx / 2 : doorT) : -1;
        int dS = doorSide == Dir.SOUTH ? (doorT < 0 ? lx / 2 : doorT) : -1;
        int dW = doorSide == Dir.WEST ? (doorT < 0 ? lz / 2 : doorT) : -1;
        int dE = doorSide == Dir.EAST ? (doorT < 0 ? lz / 2 : doorT) : -1;
        if (!sk.contains(Dir.NORTH)) wall(s, st, ax, az, Dir.EAST, lx, Dir.NORTH, yBase, yTop, dN);
        if (!sk.contains(Dir.SOUTH)) wall(s, st, ax, bz, Dir.EAST, lx, Dir.SOUTH, yBase, yTop, dS);
        if (!sk.contains(Dir.WEST)) wall(s, st, ax, az, Dir.SOUTH, lz, Dir.WEST, yBase, yTop, dW);
        if (!sk.contains(Dir.EAST)) wall(s, st, bx, az, Dir.SOUTH, lz, Dir.EAST, yBase, yTop, dE);
        if (st.innerPosts) {
            if (!sk.contains(Dir.NORTH)) innerPosts(s, st, ax, az, Dir.EAST, lx, Dir.NORTH, yBase, yTop);
            if (!sk.contains(Dir.SOUTH)) innerPosts(s, st, ax, bz, Dir.EAST, lx, Dir.SOUTH, yBase, yTop);
            if (!sk.contains(Dir.WEST)) innerPosts(s, st, ax, az, Dir.SOUTH, lz, Dir.WEST, yBase, yTop);
            if (!sk.contains(Dir.EAST)) innerPosts(s, st, bx, az, Dir.SOUTH, lz, Dir.EAST, yBase, yTop);
        }
    }
}
