package fr.eemicraft.sheepwars.gen;

import fr.eemicraft.sheepwars.gen.build.B;
import fr.eemicraft.sheepwars.gen.build.Dir;
import fr.eemicraft.sheepwars.gen.build.Mat;
import fr.eemicraft.sheepwars.gen.build.Noise;
import fr.eemicraft.sheepwars.gen.build.Sink;

/**
 * Outils de construction d'UN camp (le camp Rouge, a l'ouest). Coordonnees "u" = distance au milieu (u > 0),
 * x monde = -u. Vers le milieu (l'ennemi) = EAST pour le camp Rouge ; le camp Bleu est obtenu par miroir.
 * Le jeton TEAM dans un etat (ex. "minecraft:TEAM_wool") devient red / blue au miroir.
 */
class Gen {
    final Sink s;
    final Noise n;
    final long seed;
    /** Couleur qui remplace TEAM (null = garder le jeton, remplace au miroir). */
    String team;
    /** Decalage : les builders dessinent en u, le camp est pose en u - du (rapproche le camp du milieu). */
    int du;

    Gen(Sink s, long seed) {
        this.s = s;
        this.seed = seed;
        this.n = new Noise(seed);
    }

    static final Dir IN = Dir.EAST, OUT = Dir.WEST;

    void set(int u, int y, int z, String st) {
        u -= du;
        if (u < 0) throw new IllegalArgumentException("u < 0 : " + u);
        String v = st == null ? B.AIR : B.id(st);
        if (team != null) v = v.replace("TEAM", team);
        s.set(-u, y, z, v);
    }

    void set(int u, int y, int z, Mat m) {
        if (m != null) set(u, y, z, m.at(-(u - du), y, z));
    }

    String get(int u, int y, int z) { return s.get(-(u - du), y, z); }

    boolean air(int u, int y, int z) { return B.isAir(get(u, y, z)); }

    void setIfAir(int u, int y, int z, String st) { if (air(u, y, z)) set(u, y, z, st); }

    void fill(int u1, int y1, int z1, int u2, int y2, int z2, String st) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(u, y, z, st);
    }

    void fill(int u1, int y1, int z1, int u2, int y2, int z2, Mat m) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(u, y, z, m);
    }

    void clear(int u1, int y1, int z1, int u2, int y2, int z2) { fill(u1, y1, z1, u2, y2, z2, B.AIR); }

    /** Hachage deterministe [0,1) par case (pour des decisions PAR OBJET, jamais pour du poivre et sel). */
    double h(int a, int b, int c) { return Noise.hash(seed, a, b, c); }

    /** Bruit lisse [-1,1] (periode p) en (u, z). */
    double nz(double u, double z, double p) { return n.noise2(u / p, z / p); }

    static String stairs(String id, Dir f, boolean top) { return B.stairs(id, f, top); }

    static String slab(String id, boolean top) { return B.slab(id, top ? "top" : "bottom"); }

    static String axis(String id, char a) { return B.axis(id, a); }

    static String facing(String id, Dir d) { return B.state(id, "facing", d.id()); }

    static String leaves(String id) { return B.leaves(id); }

    /** Lanterne sur poteau de barriere (h poteaux). */
    void lanternPost(int u, int y, int z, int h, String fence, boolean soul) {
        for (int i = 0; i < h; i++) set(u, y + i, z, fence);
        set(u, y + h, z, B.state(soul ? "soul_lantern" : "lantern", "hanging", "false"));
    }

    /** Lanterne suspendue sous (u, y+1, z) par une chaine de len maillons. */
    void hangLantern(int u, int y, int z, int len, boolean soul) {
        for (int i = 0; i < len; i++) set(u, y - i, z, B.axis("iron_chain", 'y'));
        set(u, y - len, z, B.state(soul ? "soul_lantern" : "lantern", "hanging", "true"));
    }

    // ------------------------------------------------------------------ aides 4.6 (circulations controlees)

    /** Pas (du, dz) en coordonnees u/z pour une direction monde (u = -x). */
    static int du(Dir d) { return -d.dx; }

    /** Bloc qui peut porter une chaine ou une lanterne suspendue par dessous. */
    static boolean holdsBelow(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.equals("water") || n.equals("lava") || n.endsWith("carpet") || n.endsWith("lantern") || n.endsWith("_door")
                || n.endsWith("_button") || n.endsWith("pressure_plate") || n.equals("ladder") || n.endsWith("banner")) return false;
        if (n.endsWith("_slab")) return !"top".equals(B.prop(st, "type"));
        if (n.endsWith("_stairs")) return "bottom".equals(B.prop(st, "half"));   // dessous d'un escalier retourne : demi-face
        if (n.endsWith("_trapdoor")) return "bottom".equals(B.prop(st, "half")) && !"true".equals(B.prop(st, "open"));
        return true;
    }

    /**
     * Lanterne en (u, y, z) suspendue par une chaine qui monte jusqu'au premier bloc porteur (8 max). Rien n'est pose
     * si aucun bloc porteur n'est trouve (jamais de lanterne dans le vide).
     */
    boolean hangTo(int u, int y, int z, boolean soul) {
        if (!air(u, y, z)) return false;
        int top = -1;
        for (int k = 1; k <= 8; k++) {
            String st = get(u, y + k, z);
            if (B.isAir(st)) continue;
            if (holdsBelow(st) || B.name(st).equals("iron_chain")) top = y + k;
            break;
        }
        if (top < 0) return false;
        for (int yy = y + 1; yy < top; yy++) set(u, yy, z, B.axis("iron_chain", 'y'));
        set(u, y, z, B.state(soul ? "soul_lantern" : "lantern", "hanging", "true"));
        return true;
    }

    /** Echelle de y1 a y2 en (u, z), plaquee contre le bloc situe du cote oppose a facing (pose ce bloc s'il manque). */
    void ladder(int u, int z, int y1, int y2, Dir facing, String wallIfMissing) {
        int wu = u + du(facing.opposite()), wz = z + facing.opposite().dz;   // mur du cote oppose a facing
        for (int y = y1; y <= y2; y++) {
            if (B.isAir(get(wu, y, wz)) && wallIfMissing != null) set(wu, y, wz, wallIfMissing);
            set(u, y, z, B.state("ladder", "facing", facing.id()));
        }
    }

    /**
     * Volee d'escalier qui MONTE vers d : premiere marche en (u, y0, z), n marches, largeur w vers side. Remplit
     * sous chaque marche avec fill (si non null) jusqu'a yFloor, degage 3 blocs au-dessus de chaque marche et 2 au-dessus
     * de l'arrivee ; l'arrivee (case apres la derniere marche, en y0+n-1) recoit arrive si elle est vide.
     */
    void flight(int u, int z, int y0, int n, Dir d, Dir side, int w, String stairId, String fill, int yFloor, String arrive) {
        for (int i = 0; i < w; i++) {
            int bu = u + du(side) * i, bz = z + side.dz * i;
            for (int k = 0; k < n; k++) {
                int cu = bu + du(d) * k, cz = bz + d.dz * k, y = y0 + k;
                if (fill != null) for (int yy = yFloor; yy < y; yy++) set(cu, yy, cz, fill);
                set(cu, y, cz, B.stairs(stairId, d, false));
                clear(cu, y + 1, cz, cu, y + 3, cz);
            }
            int au = bu + du(d) * n, az = bz + d.dz * n, ay = y0 + n - 1;
            if (arrive != null && !B.isSolid(get(au, ay, az))) set(au, ay, az, arrive);
            clear(au, ay + 1, az, au, ay + 2, az);
            // approche libre
            int pu = bu - du(d), pz = bz - d.dz;
            clear(pu, y0, pz, pu, y0 + 2, pz);
        }
    }

    /** Pas de la direction d applique n fois a (u, z) : renvoie {u, z}. */
    static int[] step(int u, int z, Dir d, int n) { return new int[]{u + du(d) * n, z + d.dz * n}; }

    /** Tonneau (couche) ou caisse 1 bloc. */
    void barrel(int u, int y, int z, boolean lying, Dir d) {
        set(u, y, z, B.state("barrel", "facing", lying ? d.id() : "up"));
    }

    /** Palmier : tronc de jungle qui penche vers d, palmes en etoile. */
    void palm(int u, int y, int z, int h, Dir d, long sd) {
        int cu = u, cz = z;
        for (int i = 0; i < h; i++) {
            if (i > 2 && i % 3 == 0) { cu += d.dx; cz += d.dz; }
            set(Math.max(0, cu), y + i, cz, B.axis("jungle_log", 'y'));
        }
        int ty = y + h;
        set(Math.max(0, cu), ty, cz, leaves("jungle_leaves"));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] dv : dirs) {
            int len = (dv[0] != 0 && dv[1] != 0) ? 3 : 4;
            for (int k = 1; k <= len; k++) {
                int yy = ty - (k >= len - 1 ? 1 : 0) - (k == len ? 1 : 0);
                int uu = cu + dv[0] * k, zz = cz + dv[1] * k;
                if (uu < 0) continue;
                setIfAir(uu, yy, zz, leaves("jungle_leaves"));
            }
        }
    }

    /** Cypres : colonne fine de feuilles de sapin autour d'un tronc. */
    void cypress(int u, int y, int z, int h) {
        for (int i = 0; i < h; i++) set(u, y + i, z, B.axis("spruce_log", 'y'));
        for (int i = 2; i <= h + 1; i++) {
            int r = (i < 3 || i > h - 1) ? 0 : 1;
            for (int du = -r; du <= r; du++)
                for (int dz = -r; dz <= r; dz++) {
                    if (du == 0 && dz == 0 && i < h) continue;
                    if (r == 1 && Math.abs(du) + Math.abs(dz) == 2 && (i < 4 || i > h - 3)) continue;
                    if (u + du < 0) continue;
                    setIfAir(u + du, y + i, z + dz, leaves(h(u + du, i, z + dz) < 0.3 ? "dark_oak_leaves" : "spruce_leaves"));
                }
        }
    }

    /**
     * Eboulis / petit objet pose sur un sol plein (case d'air) : dalle basse, escalier oriente au hasard (par case,
     * mais seulement dans les grappes decidees par l'appelant avec un bruit), tapis, trappe fermee, bouton.
     */
    void debris(int u, int y, int z, String[] ids) {
        if (!air(u, y, z) || !B.isSolid(get(u, y - 1, z))) return;
        // jamais contre une marche (on ne bouche pas une volee d'escalier)
        for (Dir e : Dir.values()) {
            int[] p = step(u, z, e, 1);
            if (B.name(get(p[0], y, p[1])).endsWith("_stairs") || B.name(get(p[0], y - 1, p[1])).endsWith("_stairs")) return;
        }
        String id = B.id(ids[(int) (h(u, y + 31, z) * ids.length) % ids.length]);
        Dir d = Dir.values()[(int) (h(u, y + 7, z) * 4) % 4];
        String st;
        if (id.endsWith("_stairs")) {
            // eboulis en marche : tournee vers une case libre (sinon ce serait une marche qui bute)
            Dir ok = null;
            for (int k = 0; k < 4 && ok == null; k++) {
                Dir e = Dir.values()[(d.ordinal() + k) & 3];
                int[] p = step(u, z, e, 1);
                if (!B.isSolid(get(p[0], y, p[1])) && !B.name(get(p[0], y, p[1])).endsWith("_stairs")) ok = e;
            }
            if (ok == null) return;
            d = ok;
        }
        if (id.endsWith("_stairs")) st = B.stairs(id, d, false);
        else if (id.endsWith("_slab")) st = B.slab(id, "bottom");
        else if (id.endsWith("_trapdoor")) st = B.trapdoor(id, d, false, false);
        else if (id.endsWith("_button")) st = B.state(id, "face", "floor", "facing", d.id());
        else st = id;
        set(u, y, z, st);
    }

    /** Plante si la case est de l'air et le dessous est de l'herbe / mousse. */
    void plant(int u, int y, int z, String st) {
        if (!air(u, y, z)) return;
        String below = B.name(get(u, y - 1, z));
        if (below.equals("grass_block") || below.equals("moss_block") || below.equals("podzol") || below.equals("coarse_dirt")
                || below.equals("rooted_dirt"))
            set(u, y, z, st);
    }
}
