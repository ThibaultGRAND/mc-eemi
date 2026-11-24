package fr.eemicraft.soleil;

/**
 * Coordonnees partagees entre les constructeurs de maps (SquidZones, MapBuilder) et le jeu (epreuves).
 * Toutes les zones sont dans le monde « soleil », alignees sur l'axe z = 0, a 200 blocs d'ecart en x.
 * Le terrain du 1, 2, 3 Soleil et le dortoir restent autour de x = 0 (MapBuilder).
 */
final class Layout {
    private Layout() {}

    static final int FY = MapBuilder.FY;

    // ================================================================ dalgona (terrain de jeux geant)
    static final int DX = 200;
    /** Centre x des 4 colonnes de postes (biscuit de 11 de large centre sur cette colonne). */
    static final int[] D_COLS = {-21, -7, 7, 21};
    /** Mur du biscuit de la rangee r : z = D_ROW0 + r * D_ROWSTEP (4 rangees). */
    static final int D_ROW0 = 4, D_ROWSTEP = 10, D_ROWS = 4;
    /** Biscuit : 11 x 11 cases, bas en y D_BY0. */
    static final int D_SIZE = 11, D_BY0 = FY + 2;
    /** Boites des formes (rond, triangle, etoile, parapluie) : centre x, face avant en z D_BOXZ ; tapis 3x3 devant. */
    static final int[] D_BOXX = {-15, -5, 5, 15};
    static final int D_BOXZ = -30, D_PADZ = -26;
    /** Arrivee dans le bac a sable. */
    static final double D_SPAWN_Z = -15.5;

    static int dColX(int c) { return DX + D_COLS[c]; }
    static int dWallZ(int r) { return D_ROW0 + r * D_ROWSTEP; }

    // ================================================================ tir a la corde (tours au-dessus du gouffre)
    static final int CX = 400;
    /** Plancher des plateformes (on s'y tient en CP + 1). */
    static final int CP = FY + 20;
    /** Vide entre les plateformes : |x - CX| < C_EDGE. Plateforme A (ouest) x CX-26..CX-7, B (est) x CX+7..CX+26, z -2..2. */
    static final int C_EDGE = 7, C_LEN = 20;
    /** Fond du gouffre (decor) ; sous C_FALL on est elimine. */
    static final int C_BOTTOM = FY - 26, C_FALL = CP - 8;

    // ================================================================ billes (ruelle de quartier)
    static final int BX = 600;
    /** Paire k (0..7) : x = BX - 28 + 8k ; joueur A en z -1.5 (regarde le sud), joueur B en z 2.5 (regarde le nord). */
    static int bPairX(int k) { return BX - 28 + 8 * k; }

    // ================================================================ pont de verre (hall sombre)
    static final int PX = 800;
    /** Dessus des dalles de verre (on s'y tient en PB + 1). */
    static final int PB = FY + 20;
    /** Rangee r : dalles de 2x2 en x P_XB + 3r .. +1 ; gauche z -3..-2, droite z 2..3. */
    static final int P_XB = PX - 27, P_ROWS = 18;
    /** Quai de depart x PX-43 .. P_XB-2, quai d'arrivee x P_XB + 3*P_ROWS .. PX+41 (z -6..6), contre les murs. */
    static final int P_START1 = PX - 43, P_START2 = P_XB - 2, P_END1 = P_XB + 3 * P_ROWS, P_END2 = PX + 41;
    static final int P_FALL = PB - 6, P_BOTTOM = FY - 22;

    static int pRowX(int r) { return P_XB + 3 * r; }

    // ================================================================ finale (terrain du calamar)
    static final int FX = 1000;
    /** Tete : disque de rayon 5 centre (FX, F_HEADZ) ; corps x FX-7..FX+7, z -9..6 ; queue (triangle) z 7..15 ; taille en z 0. */
    static final int F_HEADZ = -15, F_HEADR = 5;
    static final int F_BODY_HW = 7, F_BODY_Z1 = -9, F_BODY_Z2 = 6, F_TAIL_Z2 = 15, F_WAIST = 0;
    /** Bordure de l'aire de jeu (craie) : |x - FX| <= 20, |z| <= 26. */
    static final int F_ARENA_HW = 20, F_ARENA_HZ = 26;

    /** Case (x, z) dans le dessin du calamar. */
    static boolean inSquid(int x, int z) {
        int dx = x - FX;
        int hz = z - F_HEADZ;
        if (dx * dx + hz * hz <= (F_HEADR + 0.4) * (F_HEADR + 0.4)) return true;
        if (Math.abs(dx) <= F_BODY_HW && z >= F_BODY_Z1 && z <= F_BODY_Z2) return true;
        if (z > F_BODY_Z2 && z <= F_TAIL_Z2) {
            double hw = F_BODY_HW * (F_TAIL_Z2 - z) / (double) (F_TAIL_Z2 - F_BODY_Z2);
            return Math.abs(dx) <= hw + 0.01;
        }
        return false;
    }

    /** Case dans la tete (zone a atteindre pour l'attaquant : rayon 3 autour du centre). */
    static boolean inHead(double x, double z, double r) {
        double dx = x - (FX + 0.5);
        double dz = z - (F_HEADZ + 0.5);
        return dx * dx + dz * dz <= r * r;
    }

    static boolean inArena(double x, double z) {
        return Math.abs(x - (FX + 0.5)) <= F_ARENA_HW + 0.5 && Math.abs(z - 0.5) <= F_ARENA_HZ + 0.5;
    }
}
