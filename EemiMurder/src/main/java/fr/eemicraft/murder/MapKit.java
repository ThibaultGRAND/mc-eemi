package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Connect;
import fr.eemicraft.murder.build.Details;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.Mat;
import fr.eemicraft.murder.build.Noise;
import fr.eemicraft.murder.build.Shapes;
import fr.eemicraft.murder.build.Sink;
import fr.eemicraft.murder.build.Stairs;

/**
 * Outils communs aux maps dessinees par code de la 1.8.0 ({@link Fete}, {@link Musee}) : poses courtes, etats de
 * blocs, modules de decor reutilisables (lampadaires, guirlandes, bancs, caisses, plantes, lustres...) et finitions.
 * Aucun acces Bukkit : tout passe par le {@link Sink}.
 */
abstract class MapKit {
    protected final Sink s;
    protected final Noise noise;
    private final long seed;

    MapKit(Sink s, long seed) {
        this.s = s;
        this.seed = seed;
        this.noise = new Noise(seed);
    }

    // ================================================================ poses

    void set(int x, int y, int z, String st) {
        s.set(x, y, z, B.id(st));
    }

    void set(int x, int y, int z, Mat m) {
        s.set(x, y, z, m.at(x, y, z));
    }

    String get(int x, int y, int z) {
        return s.get(x, y, z);
    }

    boolean isAir(int x, int y, int z) {
        return s.isAir(x, y, z);
    }

    void setIfAir(int x, int y, int z, String st) {
        if (isAir(x, y, z)) set(x, y, z, st);
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, Mat.of(B.id(st)));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Mat m) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, m);
    }

    void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        Shapes.fill(s, x1, y1, z1, x2, y2, z2, Mat.AIR);
    }

    /** Hasard deterministe par position (decisions par objet, jamais pour une texture). */
    double h(int a, int b, int c) {
        return Noise.hash(seed * 31 + 7, a, b, c);
    }

    // ================================================================ etats

    static String stairs(String id, Dir facing, boolean top) {
        return B.stairs(B.id(id), facing, top);
    }

    static String slab(String id, String type) {
        return B.slab(B.id(id), type);
    }

    static String trap(String id, Dir facing, boolean top, boolean open) {
        return B.trapdoor(B.id(id), facing, top, open);
    }

    static String log(String id, char axis) {
        return B.axis(B.id(id), axis);
    }

    static String hanging(String lantern) {
        return B.state(B.id(lantern), "hanging", "true");
    }

    static String standing(String lantern) {
        return B.state(B.id(lantern), "hanging", "false");
    }

    static String facing(String id, Dir d) {
        return B.state(B.id(id), "facing", d.id());
    }

    static String chain(char axis) {
        return B.axis("minecraft:iron_chain", axis);
    }

    static String candles(int n, String color) {
        String id = color == null ? "minecraft:candle" : "minecraft:" + color + "_candle";
        return id + "[candles=" + Math.max(1, Math.min(4, n)) + ",lit=true]";
    }

    /** Bois de laine / beton etc. : couleur de teinture -> identifiant ("red", "wool" -> red_wool). */
    static String dye(String color, String kind) {
        return "minecraft:" + color + "_" + kind;
    }

    // ================================================================ formes

    /** Disque plein (centre de bloc), rayon r, a la hauteur y. */
    void disc(double cx, double cz, int y, double r, Mat m) {
        for (int x = (int) Math.floor(cx - r); x <= (int) Math.ceil(cx + r); x++)
            for (int z = (int) Math.floor(cz - r); z <= (int) Math.ceil(cz + r); z++) {
                double dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r * r) set(x, y, z, m);
            }
    }

    /** Anneau r0 < d <= r1. */
    void ring(double cx, double cz, int y, double r0, double r1, Mat m) {
        for (int x = (int) Math.floor(cx - r1); x <= (int) Math.ceil(cx + r1); x++)
            for (int z = (int) Math.floor(cz - r1); z <= (int) Math.ceil(cz + r1); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > r0 && d <= r1) set(x, y, z, m);
            }
    }

    /** Angle (0..1) autour d'un centre, 0 au nord, sens horaire. */
    static double angle01(double dx, double dz) {
        double a = Math.atan2(dx, -dz) / (2 * Math.PI);
        return a < 0 ? a + 1 : a;
    }

    // ================================================================ modules de decor

    /** Lanterne posee sur une console (escalier retourne) contre un mur. */
    void sconce(int x, int y, int z, Dir faces, String stairsId, String lantern) {
        set(x, y, z, stairs(stairsId, faces.opposite(), true));
        setIfAir(x, y + 1, z, standing(lantern));
    }

    /** Lustre : chaine depuis yTop sur len blocs, couronne de trappes et bougies, lanternes. */
    void chandelier(int x, int yTop, int z, int len, boolean big, String trapId, String lantern) {
        int yb = yTop - len;
        for (int y = yb; y <= yTop; y++) set(x, y, z, chain('y'));
        set(x, yb - 1, z, hanging(lantern));
        for (Dir d : Dir.values()) {
            set(x + d.dx, yb, z + d.dz, trap(trapId, d, true, false));
            set(x + d.dx, yb + 1, z + d.dz, candles(3, null));
            if (big) {
                // bout de bras en barriere : la lanterne suspendue y tient (centre porteur)
                set(x + 2 * d.dx, yb, z + 2 * d.dz, "minecraft:dark_oak_fence");
                set(x + 2 * d.dx, yb - 1, z + 2 * d.dz, hanging(lantern));
            }
        }
    }

    /** Lanterne suspendue sous un plafond plein en yCeil : chaine dans la case du dessous, lanterne encore dessous. */
    void hangLamp(int x, int yCeil, int z, String lantern) {
        if (!isAir(x, yCeil - 1, z) || !isAir(x, yCeil - 2, z)) return;
        set(x, yCeil - 1, z, chain('y'));
        set(x, yCeil - 2, z, hanging(lantern));
    }

    /** Banc de 3 (escaliers) avec accoudoirs en trappes ; le joueur assis regarde vers faces. */
    void bench(int x, int y, int z, Dir faces, String wood) {
        Dir along = faces.cw();
        for (int i = -1; i <= 1; i++) set(x + along.dx * i, y, z + along.dz * i, stairs("minecraft:" + wood + "_stairs", faces.opposite(), false));
        // accoudoirs : panneau (cote oppose a facing) plaque contre le bout du banc
        setIfAir(x + along.dx * 2, y, z + along.dz * 2, trap("minecraft:" + wood + "_trapdoor", along, false, true));
        setIfAir(x - along.dx * 2, y, z - along.dz * 2, trap("minecraft:" + wood + "_trapdoor", along.opposite(), false, true));
    }

    /** Fauteuil : escalier + accoudoirs ; le joueur assis regarde vers faces. */
    void armchair(int x, int y, int z, Dir faces, String wood) {
        set(x, y, z, stairs("minecraft:" + wood + "_stairs", faces.opposite(), false));
        setIfAir(x + faces.cw().dx, y, z + faces.cw().dz, trap("minecraft:" + wood + "_trapdoor", faces.cw(), false, true));
        setIfAir(x + faces.ccw().dx, y, z + faces.ccw().dz, trap("minecraft:" + wood + "_trapdoor", faces.ccw(), false, true));
    }

    /** Table ronde (barriere + plateau en dalle haute) entouree de chaises (escaliers). */
    void cafeTable(int x, int y, int z, String wood, boolean chairs) {
        set(x, y, z, "minecraft:" + wood + "_fence");
        set(x, y + 1, z, "minecraft:" + wood + "_pressure_plate");
        if (!chairs) return;
        for (Dir d : Dir.values()) if (h(x, d.ordinal(), z) < 0.7) setIfAir(x + d.dx, y, z + d.dz, stairs("minecraft:" + wood + "_stairs", d, false));
    }

    /** Petite pile de caisses / tonneaux (1 de haut + une piece au-dessus parfois). */
    void crates(int x, int y, int z, int n) {
        String[] k = {"minecraft:barrel[facing=up,open=false]", "minecraft:spruce_planks", "minecraft:barrel[facing=north,open=false]",
                "minecraft:stripped_spruce_wood[axis=y]", "minecraft:composter[level=0]"};
        for (int i = 0; i < n; i++) {
            int dx = (int) (h(x + i, y, z) * 3) - 1, dz = (int) (h(x, y, z + i) * 3) - 1;
            if (!isAir(x + dx, y, z + dz)) continue;
            set(x + dx, y, z + dz, k[(int) (h(x + dx, y + 1, z + dz) * k.length)]);
        }
    }

    /** Pot de fleur (variantes). */
    void pot(int x, int y, int z) {
        String[] pots = {"minecraft:potted_fern", "minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush",
                "minecraft:potted_red_tulip", "minecraft:potted_poppy", "minecraft:potted_blue_orchid", "minecraft:potted_cornflower"};
        setIfAir(x, y, z, pots[(int) (h(x, y, z) * pots.length)]);
    }

    /** Grande plante en bac (bloc de mousse, azalee, trappes autour). */
    void bigPlant(int x, int y, int z, String trapWood) {
        set(x, y, z, "minecraft:moss_block");
        set(x, y + 1, z, h(x, y, z) < 0.5 ? "minecraft:flowering_azalea" : "minecraft:azalea");
        for (Dir d : Dir.values()) setIfAir(x + d.dx, y, z + d.dz, trap("minecraft:" + trapWood + "_trapdoor", d, false, true));
    }

    /** Tapis a bordure (seulement dans l'air). */
    void rug(int x1, int z1, int x2, int z2, int y, String border, String inner) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!isAir(x, y, z)) continue;
                boolean b = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, y, z, b ? border : inner);
            }
    }

    /**
     * Guirlande entre deux points de meme x ou de meme z : chaine qui pend (fleche {@code sag}), ampoules
     * colorees (froglights) en ligne et lanternes suspendues en dessous, en alternance.
     */
    void garland(int x1, int y1, int z1, int x2, int y2, int z2, double sag, int phase) {
        int len = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        if (len < 2) return;
        String[] bulbs = {"minecraft:pearlescent_froglight", "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:shroomlight"};
        // 1.9.1 : chemin continu (une seule coordonnee change d'une case a la suivante) et chaque chaine orientee
        // vers la case suivante : avant, une guirlande en biais ou qui pend etait faite de maillons qui ne se
        // touchaient que par les coins (chaines « dans le vide »)
        java.util.List<int[]> path = new java.util.ArrayList<>();
        int px = x1, py = y1, pz = z1, steps = len * 8;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int tx = (int) Math.round(x1 + (x2 - x1) * t), tz = (int) Math.round(z1 + (z2 - z1) * t);
            int ty = (int) Math.round(y1 + (y2 - y1) * t - sag * 4 * t * (1 - t));
            while (px != tx || py != ty || pz != tz) {
                if (px != tx) px += Integer.signum(tx - px);
                else if (pz != tz) pz += Integer.signum(tz - pz);
                else py += Integer.signum(ty - py);
                path.add(new int[]{px, py, pz});
            }
        }
        if (!path.isEmpty()) path.remove(path.size() - 1); // le point d'arrivee (poteau) n'est pas touche
        for (int i = 0; i < path.size(); i++) {
            int[] c = path.get(i);
            int x = c[0], y = c[1], z = c[2];
            if (!isAir(x, y, z)) continue;
            int[] o = i + 1 < path.size() ? path.get(i + 1) : (i > 0 ? path.get(i - 1) : new int[]{x2, y2, z2});
            char axis = o[0] != x ? 'x' : o[2] != z ? 'z' : 'y';
            int k = Math.floorMod(i + 1 + phase, 4);
            if (k == 0 && axis != 'y') {
                set(x, y, z, bulbs[Math.floorMod((i + 1) / 4 + phase, bulbs.length)]);
                // lanterne suspendue sous une ampoule sur deux (sous un maillon couche, elle ne tiendrait pas)
                if (Math.floorMod((i + 1) / 4, 2) == 0 && isAir(x, y - 1, z))
                    set(x, y - 1, z, hanging(Math.floorMod((i + 1) / 4 + phase, 3) == 1 ? "minecraft:soul_lantern" : "minecraft:lantern"));
            } else set(x, y, z, chain(axis));
        }
        // 2e passe : un maillon dont le voisin dans l'axe n'est ni un maillon ni un porteur (case sautee parce
        // qu'occupee, guirlande posee sur une toile) prend l'axe d'un voisin qui le tient
        // ; un maillon isole (aucun voisin qui le tienne) est retire
        // (repete jusqu'a stabilite ; une ampoule restee sans voisin est retiree aussi)
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int[] c : path) {
                String st = get(c[0], c[1], c[2]);
                String nm = B.name(st);
                if (nm.endsWith("froglight") || nm.equals("shroomlight")) {
                    boolean any = false;
                    for (char ax : new char[]{'x', 'z', 'y'}) if (linked(c[0], c[1], c[2], ax)) any = true;
                    if (!any) {
                        set(c[0], c[1], c[2], B.AIR);
                        String below = get(c[0], c[1] - 1, c[2]);
                        if (B.name(below).endsWith("lantern") && "true".equals(B.prop(below, "hanging"))) set(c[0], c[1] - 1, c[2], B.AIR);
                        changed = true;
                    }
                    continue;
                }
                if (!nm.equals("iron_chain")) continue;
                String a = B.prop(st, "axis");
                if (a == null) a = "y";
                if (linked(c[0], c[1], c[2], a.charAt(0))) continue;
                boolean done = false;
                for (char ax : new char[]{'x', 'z', 'y'})
                    if (linked(c[0], c[1], c[2], ax)) { set(c[0], c[1], c[2], chain(ax)); done = true; break; }
                if (!done) {
                    set(c[0], c[1], c[2], B.AIR);
                    changed = true;
                }
            }
        }
    }

    private boolean linked(int x, int y, int z, char axis) {
        int dx = axis == 'x' ? 1 : 0, dy = axis == 'y' ? 1 : 0, dz = axis == 'z' ? 1 : 0;
        return holdsLink(get(x + dx, y + dy, z + dz)) || holdsLink(get(x - dx, y - dy, z - dz));
    }

    private static boolean holdsLink(String st) {
        String n = B.name(st);
        return n.equals("iron_chain") || n.endsWith("froglight") || n.equals("shroomlight") || (Details.holds(st) && !n.endsWith("lantern"));
    }

    /** Lampadaire : socle en muret, fut en barrieres, couronne de lanternes ; renvoie la hauteur de la tete. */
    int lampPost(int x, int y, int z, int hgt, String fence, String base, boolean arms) {
        set(x, y, z, base);
        for (int i = 1; i < hgt; i++) set(x, y + i, z, fence);
        int top = y + hgt;
        if (arms) {
            // bras en barrieres (centre porteur : les lanternes y tiennent vraiment), lanterne au sommet du fut
            set(x, top, z, fence);
            for (Dir d : Dir.values()) {
                set(x + d.dx, top - 1, z + d.dz, "minecraft:dark_oak_fence");
                set(x + d.dx, top - 2, z + d.dz, hanging("minecraft:lantern"));
            }
            set(x, top + 1, z, standing("minecraft:lantern"));
        } else {
            set(x, top, z, standing("minecraft:lantern"));
        }
        return top;
    }

    // ================================================================ finitions

    /** Portes ouvertes, formes des escaliers, connexions des barrieres / vitres / murets dans la boite donnee. */
    void finish(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y1; y <= y2; y++) {
                    String b = s.get(x, y, z);
                    if (b.contains("_door[") && b.contains("open=false") && !b.contains("iron_door")) set(x, y, z, b.replace("open=false", "open=true"));
                }
        Details.fix(s, x1, y1, z1, x2, y2, z2); // 1.9.1 : trappes, lanternes, paratonnerres contre leur appui
        Stairs.fix(s, x1, y1, z1, x2, y2, z2);
        Connect.fix(s, x1, y1, z1, x2, y2, z2);
    }
}
