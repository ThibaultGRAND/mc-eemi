package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.build.B;
import fr.eemicraft.hideseek.build.Dir;
import fr.eemicraft.hideseek.build.Noise;
import fr.eemicraft.hideseek.build.Palette;
import fr.eemicraft.hideseek.build.Sink;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Map du cache-cache construite par le plugin (1.4.0 ; supermarche, puis port en 1.5.0), posee dans le monde du jeu a cote du village.
 * Aucun appel Bukkit ici : la map s'ecrit dans un {@link Sink} (etats de bloc vanilla en texte), ce qui permet de la
 * construire hors serveur (test, mesures, rendus) et, en jeu, hors du fil principal.
 *
 * <p>Points (x, y, z, yaw, pitch) : waiting-spawn, seeker-room, hider-spawn, seeker-spawn, spectate.
 */
public abstract class HsMap {
    public final String id;
    public final String name;
    public final int version;

    protected Sink s;

    protected HsMap(String id, String name, int version) {
        this.id = id;
        this.name = name;
        this.version = version;
    }

    /** Boite videe puis reconstruite (absolue) : {x1, y1, z1, x2, y2, z2}. Tout ce que pose build() y est. */
    public abstract int[] box();

    /** Pose la map (coordonnees absolues). */
    protected abstract void draw();

    /** Point nomme, ou null s'il n'existe pas. */
    public abstract double[] point(String key);

    /** Blocs de deguisement de la map : nom de materiau (enum Bukkit) -> nom affiche. */
    public abstract LinkedHashMap<String, String> disguises();

    /** Boite de la zone de jeu seule (sans salle d'attente ni salle noire) : pour les mesures. */
    public abstract int[] playBox();

    /** Mesures hors serveur : bloc du sol le plus bas et plafond (exclu) de la zone de jeu. */
    public int measureFloorY() { return 64; }

    public int measureCeilY() { return 76; }

    /** Hauteur du sol naturel en (x, z) pour les mesures, MIN_VALUE si inconnue (sol = measureFloorY). */
    public int groundY(int x, int z) { return Integer.MIN_VALUE; }

    public final synchronized void build(Sink sink) {
        this.s = sink;
        try {
            draw();
        } finally {
            this.s = null;
        }
    }

    // ------------------------------------------------------------------ boite a outils (etats en texte)

    protected void set(int x, int y, int z, String st) {
        s.set(x, y, z, B.id(st));
    }

    protected String get(int x, int y, int z) {
        return s.get(x, y, z);
    }

    protected boolean air(int x, int y, int z) {
        return B.isAir(s.get(x, y, z));
    }

    protected void setIfAir(int x, int y, int z, String st) {
        if (air(x, y, z)) set(x, y, z, st);
    }

    protected void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        String id = B.id(st);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) s.set(x, y, z, id);
    }

    protected void fill(int x1, int y1, int z1, int x2, int y2, int z2, Palette p) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) s.set(x, y, z, p.clustered(x, y, z));
    }

    protected void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, B.AIR);
    }

    protected static String stairs(String id, Dir facing, boolean top) {
        return B.stairs(id, facing, top);
    }

    protected static String slab(String id, boolean top) {
        return B.slab(id, top ? "top" : "bottom");
    }

    protected static String axis(String id, char a) {
        return B.axis(id, a);
    }

    protected static String facing(String id, Dir d) {
        return B.state(id, "facing", d.id());
    }

    protected static String trap(String id, Dir facing, boolean top, boolean open) {
        return B.trapdoor(id, facing, top, open);
    }

    /** Lanterne posee (au sol / sur un meuble). */
    protected static String lantern(boolean soul) {
        return B.state(soul ? "soul_lantern" : "lantern", "hanging", "false", "waterlogged", "false");
    }

    protected static String hangingLantern() {
        return B.state("lantern", "hanging", "true", "waterlogged", "false");
    }

    /** Porte vanilla ouverte (deux moities). */
    protected void door(int x, int y, int z, String id, Dir facing, boolean hingeRight, boolean open) {
        String lo = B.state(B.door(id, facing, false, hingeRight), "open", String.valueOf(open), "powered", "false");
        String hi = B.state(B.door(id, facing, true, hingeRight), "open", String.valueOf(open), "powered", "false");
        set(x, y, z, lo);
        set(x, y + 1, z, hi);
    }

    protected static double hash(long seed, int x, int y, int z) {
        return Noise.hash(seed, x, y, z);
    }

    // ------------------------------------------------------------------ salles communes

    /**
     * Salle d'attente : piece vitree de (2r+1)^2 au plancher fy, socles d'exposition avec les blocs de deguisement de
     * la map (les joueurs voient ce qu'ils pourront copier), lanternes suspendues. Murs en 3 plans (poteaux, vitres
     * en retrait), pas de mur d'un seul plan.
     */
    protected void waitingRoom(int cx, int fy, int cz, int r, String floorA, String floorB, String post, String wall,
                               String roof, List<String> show) {
        int x1 = cx - r, x2 = cx + r, z1 = cz - r, z2 = cz + r;
        air(x1 - 1, fy, z1 - 1, x2 + 1, fy + 8, z2 + 1);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                // damier de dalles 3x3 (motif regulier, pas de tirage au hasard)
                set(x, fy, z, edge ? post : (((x - x1) / 3 + (z - z1) / 3) % 2 == 0 ? floorA : floorB));
                set(x, fy + 7, z, roof);
                if (!edge) continue;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                boolean pillar = corner || (z == z1 || z == z2) && (x - x1) % 4 == 0 || (x == x1 || x == x2) && (z - z1) % 4 == 0;
                for (int y = fy + 1; y <= fy + 6; y++) {
                    if (pillar) set(x, y, z, post);
                    else if (y == fy + 1 || y == fy + 6) set(x, y, z, wall);
                    else set(x, y, z, "glass");
                }
            }
        // poteaux saillants a l'exterieur et corniche : 3 plans
        for (int x = x1; x <= x2; x += 4) {
            fill(x, fy + 1, z1 - 1, x, fy + 6, z1 - 1, post);
            fill(x, fy + 1, z2 + 1, x, fy + 6, z2 + 1, post);
        }
        for (int z = z1; z <= z2; z += 4) {
            fill(x1 - 1, fy + 1, z, x1 - 1, fy + 6, z, post);
            fill(x2 + 1, fy + 1, z, x2 + 1, fy + 6, z, post);
        }
        // socles d'exposition (un bloc de deguisement sur chaque socle), le long des murs
        int i = 0;
        for (int x = x1 + 2; x <= x2 - 2 && i < show.size(); x += 2) {
            set(x, fy + 1, z1 + 1, "polished_andesite");
            set(x, fy + 2, z1 + 1, show.get(i++));
        }
        for (int x = x1 + 2; x <= x2 - 2 && i < show.size(); x += 2) {
            set(x, fy + 1, z2 - 1, "polished_andesite");
            set(x, fy + 2, z2 - 1, show.get(i++));
        }
        for (int z = z1 + 3; z <= z2 - 3 && i < show.size(); z += 2) {
            set(x1 + 1, fy + 1, z, "polished_andesite");
            set(x1 + 1, fy + 2, z, show.get(i++));
        }
        for (int z = z1 + 3; z <= z2 - 3 && i < show.size(); z += 2) {
            set(x2 - 1, fy + 1, z, "polished_andesite");
            set(x2 - 1, fy + 2, z, show.get(i++));
        }
        // anneau interieur de socles (a 4 du centre), sans gener le point d'arrivee (cote sud)
        for (int dx = -4; dx <= 4 && i < show.size(); dx++)
            for (int dz = -4; dz <= 4 && i < show.size(); dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 4 || ((dx + dz) & 1) != 0) continue;
                if (dz == 4 && Math.abs(dx) <= 1) continue;
                set(cx + dx, fy + 1, cz + dz, "polished_andesite");
                set(cx + dx, fy + 2, cz + dz, show.get(i++));
            }
        // tapis central et lanternes
        fill(cx - 2, fy + 1, cz - 2, cx + 2, fy + 1, cz + 2, "green_carpet");
        for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {0, 0}}) set(cx + p[0], fy + 6, cz + p[1], hangingLantern());
    }

    /** Salle noire des chercheurs (7x7x6, interieur 5x5x4). */
    protected void seekerRoom(int cx, int fy, int cz) {
        fill(cx - 3, fy, cz - 3, cx + 3, fy + 5, cz + 3, "black_concrete");
        air(cx - 2, fy + 1, cz - 2, cx + 2, fy + 4, cz + 2);
    }
}
