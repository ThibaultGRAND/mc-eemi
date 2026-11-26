package fr.eemicraft.hideseek;

/**
 * Regle anti-perchoir des cacheurs (1.6.0, carte #135 : « ne pas pouvoir se cacher sur les maisons ou en hauteur a
 * base de jump »). Pur Java, sans Bukkit : la meme regle tourne en jeu et dans le banc de test hors serveur.
 *
 * <p>Un cacheur pose au sol est sur un perchoir interdit quand les deux conditions sont vraies :
 * <ul>
 *   <li>il est a l'air libre : aucun bloc qui abrite (ni feuilles, ni barriere) dans sa colonne au-dessus de sa tete,
 *       sur {@code scan} blocs. Les etages, mezzanines, combles et tout l'interieur des batiments restent donc permis ;</li>
 *   <li>ses pieds sont a plus de {@code maxHeight} blocs au-dessus du sol de reference de la map a cet endroit
 *       ({@link HsMap#roofGround}) : toits, haut des murs, auvents, cimes d'arbres, haut du puits, rochers de falaise.</li>
 * </ul>
 * Les cases que la map declare prevues en hauteur ({@link HsMap#perchAllowed}) ne sont jamais interdites.
 */
final class Perch {
    /** Acces aux blocs : vrai si le bloc abrite (toit, plafond, plancher d'etage). */
    interface Cells {
        boolean covers(int x, int y, int z);
    }

    /** Bloc du sol de reference de la map en (x, z), MIN_VALUE hors map. */
    interface Ground {
        int at(int x, int z);
    }

    private Perch() {
    }

    /**
     * Sol de reference au plus haut des 9 colonnes autour de (x, z) : le bord d'un quai, le haut d'une echelle ou d'un
     * escalier qui monte du bassin ne sont pas des perchoirs. MIN_VALUE si la colonne elle-meme est hors map.
     */
    static int groundNear(Ground g, int x, int z) {
        int c = g.at(x, z);
        if (c == Integer.MIN_VALUE) return c;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int v = g.at(x + dx, z + dz);
                if (v > c) c = v;
            }
        return c;
    }

    /** Premier bloc au-dessus de la tete d'un joueur debout dont les pieds sont en feetY (1,8 de haut). */
    static int firstAboveHead(double feetY) {
        return (int) Math.floor(feetY + 1.8 - 1e-6) + 1;
    }

    /** Hauteur des pieds au-dessus de la surface du sol de reference (bloc ground + 1). */
    static double heightAbove(double feetY, int ground) {
        return feetY - (ground + 1);
    }

    /**
     * @param ground bloc du sol de reference en (x, z) (voir {@link #groundNear}), MIN_VALUE = hors map (jamais interdit)
     * @return vrai si la position est un perchoir interdit
     */
    static boolean forbidden(Cells c, HsMap map, int x, double feetY, int z, int ground, double maxHeight, int scan) {
        if (ground == Integer.MIN_VALUE) return false;
        if (heightAbove(feetY, ground) <= maxHeight + 1e-6) return false;
        int fy = (int) Math.floor(feetY + 1e-6);
        if (map != null && map.perchAllowed(x, fy, z)) return false;
        int y0 = firstAboveHead(feetY);
        for (int y = y0; y < y0 + scan; y++) if (c.covers(x, y, z)) return false;
        return true;
    }
}
