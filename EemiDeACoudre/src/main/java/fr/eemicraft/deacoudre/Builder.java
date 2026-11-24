package fr.eemicraft.deacoudre;

import fr.eemicraft.deacoudre.build.B;
import fr.eemicraft.deacoudre.build.Connect;
import fr.eemicraft.deacoudre.build.Dir;
import fr.eemicraft.deacoudre.build.Mat;
import fr.eemicraft.deacoudre.build.Noise;
import fr.eemicraft.deacoudre.build.Palette;
import fr.eemicraft.deacoudre.build.Roof;
import fr.eemicraft.deacoudre.build.RoofStyle;
import fr.eemicraft.deacoudre.build.Sdf;
import fr.eemicraft.deacoudre.build.Shapes;
import fr.eemicraft.deacoudre.build.Sink;
import fr.eemicraft.deacoudre.build.Stairs;
import fr.eemicraft.deacoudre.build.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Decor du De a coudre (version de construction 3) : crique tropicale fermee par des falaises au nord, a l'est et a
 * l'ouest, lagon ferme par un recif au sud ; piscine ronde au centre (contrat de jeu inchange) ; tour de plongeon
 * ronde et elancee (contreforts, bandeaux, meurtrieres en retrait, escalier helicoidal exterieur, deux balcons) ;
 * belvedere vitre (charpente, colonnes, toit a croupes avec debord) ; plage, palmiers, cabines, transats, ponton.
 *
 * <p>Contrat avec le jeu (verifie par le test hors serveur) : eau en WY-1..WY sur lanternes marines (WY-2) et
 * briques de prismarine (WY-3) dans la piscine ; margelle pleine en WY autour ; rien de solide au-dessus de la
 * piscine ni sous le plongeoir ; plongeoir d'un bloc de large en {@code by}, de z2+1 a -2 ; salle d'attente au sol en
 * {@code by}, 4 rangees de devant (z2-3..z2) en verre, murs en verre clair, arrivee en (0, by+1, -(r+7)).
 */
public final class Builder {
    /** Surface de l'eau (meme valeur que EemiDeACoudre.WY). */
    public static final int WY = 100;
    /** Version de construction du decor : la monter force la reconstruction (data.yml). */
    public static final int VERSION = 3;
    /** Demi-cote de la zone construite (chunks -2..1 en forceload = -32..31). */
    public static final int AREA = 30;

    private final Sink s;
    private final int r, by;
    private final int z1, z2, rx;
    /** Centre de la tour. */
    private final int tz;
    private final int H;
    private final Noise n = new Noise(4242);
    private Terrain t;
    /** Hauteur (bloc de marche) de l'escalier helicoidal par case, pour ne pas le boucher. */
    private final Map<Long, List<Integer>> spiral = new HashMap<>();

    private Builder(Sink s, int r, int by) {
        this.s = s;
        this.r = r;
        this.by = by;
        this.z1 = -(r + 12);
        this.z2 = -(r + 3);
        this.rx = 6;
        this.tz = -(r + 9);
        this.H = by - WY;
    }

    /** Construit tout le decor dans le Sink (zone x,z -AREA..AREA). */
    public static void build(Sink s, int r, int by) {
        new Builder(s, r, by).run();
    }

    static boolean inPool(int r, int x, int z) {
        return x * x + z * z <= r * r + r;
    }

    boolean inPool(int x, int z) {
        return inPool(r, x, z);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void run() {
        terrain();
        pool();
        tower();
        room();
        board();
        beach();
        Stairs.fix(s, -AREA, WY - 6, -AREA, AREA, by + 20, AREA);
        Connect.fix(s, -AREA, WY - 6, -AREA, AREA, by + 20, AREA);
    }

    // ================================================================ terrain

    private double smooth(double a, double b, double v) {
        double k = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return k * k * (3 - 2 * k);
    }

    private void terrain() {
        int bowl = Math.max(17, r + 13);
        t = new Terrain(77, -AREA, -AREA, AREA, AREA);
        Noise wn = n.derive(11), cn = n.derive(12), dn = n.derive(13);
        for (int x = -AREA; x <= AREA; x++) {
            for (int z = -AREA; z <= AREA; z++) {
                double[] w = wn.warp2(x, z, 4, 18);
                double dx = w[0], dz = (w[1] - 1) * 0.95;
                double d = Math.sqrt(dx * dx + dz * dz);
                // dunes douces dans la cuvette
                double h = WY + 0.9 * dn.fbm2(x / 13.0, z / 13.0, 3, 0.5, 2) + 0.4;
                // falaises : anneau qui monte, coupe au sud (le lagon)
                double cliff = smooth(bowl, bowl + 9, d) * (13 + 7 * cn.fbm2(x / 16.0, z / 16.0, 4, 0.5, 2)
                        + 4 * cn.ridge2(x / 11.0, z / 11.0, 3, 0.5, 2));
                double south = smooth(3, 15, z);
                cliff *= 1 - 0.9 * south;
                // crique asymetrique : falaise ouest plus haute et plus abrupte, cote est plus doux
                cliff *= 1.0 + 0.3 * (1 - smooth(-12, 12, x)) - 0.15 * smooth(0, 20, x);
                h += cliff;
                // lagon au sud (decale vers l'est), ferme par un recif sur le bord
                double lag = smooth(9, 17, z - 0.15 * x) * (1 - smooth(bowl - 6, bowl + 5, Math.abs(x - 4)));
                h -= lag * 4.2;
                double reef = smooth(AREA - 5, AREA, z) * (4 + 3 * dn.fbm2(x / 5.0, 3.3, 2, 0.5, 2));
                h += reef;
                if (Math.abs(x) >= AREA - 1 || Math.abs(z) >= AREA - 1) {
                    h = Math.max(h, WY + 1.5);
                }
                t.setHeight(x, z, h);
            }
        }
        t.smooth(1)
                .pad(-(r + 5), -(r + 5), r + 5, r + 5, WY, 4)
                .pad(-8, tz - 7, 8, tz + 6, WY, 3)
                .pad(r + 8, -8, r + 14, 7, WY + 1, 3)
                .meltFill()
                .water(WY - 1);
        Terrain.Surface sf = new Terrain.Surface();
        Palette sand = Palette.of("minecraft:sand");
        Palette grass = Palette.of(32, "minecraft:grass_block[snowy=false]", 1);
        Palette bare = Palette.of(33, "minecraft:coarse_dirt", 60, "minecraft:podzol[snowy=false]", 25, "minecraft:rooted_dirt", 15).period(3);
        final Palette fs = sand;
        sf.grass = (x, y, z) -> y <= WY + 1 ? fs.at(x, y, z) : y == WY + 2 ? (n.uniform2(x / 4.0, z / 4.0) < 0.5 ? fs.at(x, y, z) : grass.at(x, y, z)) : grass.at(x, y, z);
        sf.bare = (x, y, z) -> y <= WY + 2 ? fs.at(x, y, z) : bare.at(x, y, z);
        sf.rock = (x, y, z) -> {
            // strates : pierre en bas, andesite au milieu, calcaire clair (calcite) en haut, decalees par le bruit
            double k = (y - WY) / 16.0 + 0.18 * n.noise3(x / 9.0, y / 5.0, z / 9.0);
            if (k < 0.35) return "minecraft:stone";
            if (k < 0.55) return "minecraft:andesite";
            if (k < 0.62) return "minecraft:tuff";
            if (k < 0.85) return "minecraft:stone";
            return "minecraft:calcite";
        };
        sf.beach = fs;
        sf.bed = Palette.of(34, "minecraft:sand", 60, "minecraft:gravel", 22, "minecraft:clay", 18).period(4);
        sf.sub = (x, y, z) -> y <= WY + 1 ? "minecraft:sandstone" : "minecraft:dirt";
        t.place(s, sf, WY - 8);
    }

    // ================================================================ piscine

    private void pool() {
        for (int x = -(r + 6); x <= r + 6; x++) {
            for (int z = -(r + 6); z <= r + 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (inPool(x, z)) {
                    s.set(x, WY - 3, z, "minecraft:prismarine_bricks");
                    s.set(x, WY - 2, z, "minecraft:sea_lantern");
                    s.set(x, WY - 1, z, B.WATER);
                    s.set(x, WY, z, B.WATER);
                    for (int y = WY + 1; y <= WY + 4; y++) s.set(x, y, z, B.AIR);
                    continue;
                }
                boolean rim = false;
                for (int a = -1; a <= 1 && !rim; a++)
                    for (int b = -1; b <= 1 && !rim; b++)
                        if (inPool(x + a, z + b)) rim = true;
                if (rim) {
                    // paroi de la piscine en prismarine sombre, margelle en quartz
                    for (int y = WY - 4; y <= WY - 1; y++) s.set(x, y, z, "minecraft:dark_prismarine");
                    s.set(x, WY, z, "minecraft:smooth_quartz");
                    clearAbove(x, z, WY + 1);
                } else if (d <= r + 4.5) {
                    // terrasse : dalles de gres taille, rosace de 16 rayons en quartz sur l'anneau du milieu
                    int ring = (int) Math.floor(d - r);
                    boolean ray = ring == 2 && ((int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 8)) & 1) == 1;
                    s.set(x, WY - 1, z, "minecraft:sand");
                    s.set(x, WY, z, ray ? "minecraft:smooth_quartz" : "minecraft:cut_sandstone");
                    clearAbove(x, z, WY + 1);
                }
            }
        }
        // lampadaires sur la terrasse (diagonales, hors de la piscine)
        double a0 = Math.PI / 4;
        for (int i = 0; i < 4; i++) {
            double a = a0 + i * Math.PI / 2;
            int lx = (int) Math.round(Math.cos(a) * (r + 3.6)), lz = (int) Math.round(Math.sin(a) * (r + 3.6));
            lamp(lx, WY + 1, lz, i);
        }
    }

    private void clearAbove(int x, int z, int y0) {
        for (int y = y0; y <= y0 + 6; y++) s.set(x, y, z, B.AIR);
    }

    /** Lampadaire : pied en muret, fut en barriere, lanterne. */
    private void lamp(int x, int y, int z, int v) {
        s.set(x, y, z, "minecraft:sandstone_wall");
        s.set(x, y + 1, z, "minecraft:dark_oak_fence");
        s.set(x, y + 2, z, "minecraft:dark_oak_fence");
        s.set(x, y + 3, z, "minecraft:lantern[hanging=false]");
    }

    // ================================================================ tour

    private double dt(int x, int z) {
        double dz = z - tz;
        return Math.sqrt(x * x + dz * dz);
    }

    private boolean rib(int x, int z) {
        int ax = Math.abs(x), az = Math.abs(z - tz);
        return (ax == 3 && az == 3) || (ax == 3 && az == 2) || (ax == 2 && az == 3);
    }

    /** Face exterieure (vers le dehors) d'une case autour de la tour. */
    private Dir outward(int x, int z) {
        int dz = z - tz;
        if (Math.abs(x) >= Math.abs(dz)) return x >= 0 ? Dir.EAST : Dir.WEST;
        return dz >= 0 ? Dir.SOUTH : Dir.NORTH;
    }

    private void tower() {
        // fut blanc : calcite claire en haut, terre cuite blanche (plus chaude) en bas, en amas ; contreforts, socle
        // et chapiteau en gres taille (une autre famille : pas de poivre et sel au contact)
        Palette wall = Palette.of(51, "minecraft:calcite", 45, "minecraft:white_terracotta", 55).period(8).amp(0.22);
        Mat wallMat = wall.vertical(WY + 1, WY + Math.max(12, H * 3 / 4));
        Mat ribM = Mat.of("minecraft:cut_sandstone");
        int top = by - 1;
        int yb1 = WY + Math.max(5, Math.round(H * 0.33f));
        int yb2 = WY + Math.max(9, Math.round(H * 0.62f));
        for (int x = -7; x <= 7; x++) {
            for (int z = tz - 7; z <= tz + 7; z++) {
                double d = dt(x, z);
                // socle : anneau large en pierre de taille, chapeau en escaliers
                if (d <= 4.6) {
                    for (int y = WY + 1; y <= WY + 2; y++) s.set(x, y, z, d > 3.5 ? "minecraft:cut_sandstone" : wallMat.at(x, y, z));
                    if (d > 3.5) s.set(x, WY + 3, z, B.stairs("minecraft:sandstone_stairs", outward(x, z).opposite(), false));
                }
                // dallage autour de la tour
                if (d > 4.6 && d <= 7.2 && !inPool(x, z)) {
                    s.set(x, WY - 1, z, "minecraft:sand");
                    s.set(x, WY, z, (int) Math.floor(d) == 7 ? "minecraft:smooth_quartz" : "minecraft:cut_sandstone");
                }
                for (int y = WY + 3; y <= top - 3; y++) {
                    if (d <= 3.5 && d > 2.5) {
                        s.set(x, y, z, wallMat.at(x, y, z));
                    } else if (d <= 2.5) {
                        s.set(x, y, z, B.AIR);
                    } else if (rib(x, z) && y <= top - 4) {
                        boolean band = (y - WY) % 6 == 0;
                        s.set(x, y, z, band ? "minecraft:prismarine_bricks" : ribM.at(x, y, z));
                    } else if (d <= 4.4 && (y - WY) % 6 == 0) {
                        // bandeau en escaliers retournes de prismarine
                        s.set(x, y, z, B.stairs("minecraft:prismarine_brick_stairs", outward(x, z).opposite(), true));
                    }
                }
            }
        }
        // meurtrieres en retrait (vitre bleue un bloc en arriere) aux 4 points cardinaux, deux blocs de haut par niveau
        int[][] cards = {{0, -3}, {3, 0}, {-3, 0}, {0, 3}};
        for (int y = WY + 4; y <= top - 5; y++) {
            int lvl = (y - WY) % 6;
            if (lvl != 2 && lvl != 3) continue;
            for (int[] c : cards) {
                int x = c[0], z = tz + c[1];
                s.set(x, y, z, B.AIR);
                s.set(x - Integer.signum(c[0]), y, z - Integer.signum(c[1]), "minecraft:light_blue_stained_glass");
                // appui : escalier retourne sous la baie, au niveau bas seulement
                if (lvl == 2) {
                    Dir o = outward(x, z);
                    int ox = x + o.dx, oz = z + o.dz;
                    if (s.isAir(ox, y - 1, oz)) s.set(ox, y - 1, oz, B.stairs("minecraft:smooth_sandstone_stairs", o.opposite(), true));
                }
            }
        }
        spiral(yb2);
        balcony(yb1);
        balcony(yb2);
        // chapiteau : couronne en encorbellement qui porte le belvedere
        for (int x = -6; x <= 6; x++) {
            for (int z = tz - 6; z <= tz + 6; z++) {
                double d = dt(x, z);
                if (d <= 3.5 && d > 2.5) {
                    for (int y = top - 3; y <= top; y++) s.set(x, y, z, y == top - 1 ? "minecraft:prismarine_bricks" : "minecraft:cut_sandstone");
                } else if (d <= 2.5) {
                    for (int y = top - 3; y <= top; y++) s.set(x, y, z, B.AIR);
                } else if (d <= 4.5) {
                    s.set(x, top - 2, z, B.stairs("minecraft:sandstone_stairs", outward(x, z).opposite(), true));
                    s.set(x, top - 1, z, "minecraft:cut_sandstone");
                    s.set(x, top, z, "minecraft:cut_sandstone");
                } else if (d <= 5.5) {
                    s.set(x, top - 1, z, B.stairs("minecraft:sandstone_stairs", outward(x, z).opposite(), true));
                }
            }
        }
        // echelle de service a l'interieur (on ne la voit que par les meurtrieres)
        for (int y = WY + 3; y <= top; y++) s.set(1, y, tz - 2, "minecraft:ladder[facing=south]");
    }

    /** Escalier helicoidal exterieur du sol jusqu'au balcon haut. */
    private void spiral(int yEnd) {
        int rise = yEnd - (WY + 1);
        if (rise < 4) return;
        int turns = Math.max(1, Math.round(rise / 12f));
        double pitch = rise / (double) turns;
        double th0 = Math.PI / 4; // depart au sud-est, cote plage
        List<int[]> cells = new ArrayList<>();
        for (int x = -6; x <= 6; x++)
            for (int z = tz - 6; z <= tz + 6; z++) {
                double d = dt(x, z);
                if (d >= 4.4 && d < 5.6) cells.add(new int[]{x, z});
            }
        for (int k = 0; k < turns; k++) {
            for (int[] c : cells) {
                int x = c[0], z = c[1];
                double th = Math.atan2(z - tz, x) - th0;
                while (th < 0) th += 2 * Math.PI;
                double f = (k + th / (2 * Math.PI));
                double yf = WY + 1 + f * pitch;
                int y = (int) Math.floor(yf);
                if (y > yEnd) continue;
                double tx = -Math.sin(th + th0), tzz = Math.cos(th + th0);
                Dir face = Math.abs(tx) >= Math.abs(tzz) ? (tx > 0 ? Dir.EAST : Dir.WEST) : (tzz > 0 ? Dir.SOUTH : Dir.NORTH);
                if (yf - y >= 0.5 || y == yEnd) {
                    s.set(x, y, z, B.stairs("minecraft:smooth_sandstone_stairs", face, false));
                } else {
                    s.set(x, y, z, B.slab("minecraft:smooth_sandstone_slab", "bottom"));
                }
                if (y - 1 > WY && s.isAir(x, y - 1, z)) s.set(x, y - 1, z, B.stairs("minecraft:smooth_sandstone_stairs", face, true));
                spiral.computeIfAbsent(key(x, z), q -> new ArrayList<>()).add(y);
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    String cur = s.get(x, yy, z);
                    if (B.name(cur).endsWith("_stairs") && B.prop(cur, "half").equals("top")) s.set(x, yy, z, B.AIR);
                }
                // garde-corps : poteau tous les 5 pas sur le bord exterieur
                if (dt(x, z) >= 5.1 && Math.floorMod((int) Math.round(f * 40), 5) == 0 && s.isAir(x, y + 1, z)) {
                    s.set(x, y + 1, z, "minecraft:dark_oak_fence");
                    if (Math.floorMod((int) Math.round(f * 40), 15) == 0) s.set(x, y + 2, z, "minecraft:lantern[hanging=false]");
                }
            }
        }
    }

    private boolean spiralNear(int x, int z, int y0, int y1) {
        List<Integer> l = spiral.get(key(x, z));
        if (l == null) return false;
        for (int y : l) if (y >= y0 && y <= y1) return true;
        return false;
    }

    /** Balcon circulaire en encorbellement avec garde-corps et lanternes. */
    private void balcony(int y) {
        int post = 0;
        for (int x = -7; x <= 7; x++) {
            for (int z = tz - 7; z <= tz + 7; z++) {
                double d = dt(x, z);
                if (d <= 3.5 || d > 6.4) continue;
                if (spiralNear(x, z, y - 3, y - 1)) continue; // tremie : l'escalier passe dessous
                if (s.isAir(x, y, z) || B.name(s.get(x, y, z)).contains("smooth_sandstone")) s.set(x, y, z, B.slab("minecraft:dark_oak_slab", "top"));
                if (d <= 4.4) {
                    if (s.isAir(x, y - 1, z)) s.set(x, y - 1, z, B.stairs("minecraft:dark_oak_stairs", outward(x, z).opposite(), true));
                } else if (d > 5.6) {
                    if (spiralNear(x, z, y, y + 2)) continue;
                    s.set(x, y + 1, z, "minecraft:dark_oak_fence");
                    int dz = z - tz;
                    boolean axis = (x == 0 && Math.abs(dz) == 6) || (dz == 0 && Math.abs(x) == 6)
                            || (Math.abs(x) == 4 && Math.abs(dz) == 4);
                    if (axis) {
                        s.set(x, y + 2, z, "minecraft:dark_oak_fence");
                        s.set(x, y + 3, z, "minecraft:lantern[hanging=false]");
                    }
                }
            }
        }
        // consoles sous le balcon aux 8 directions
        int[][] dirs = {{0, 5}, {0, -5}, {5, 0}, {-5, 0}, {4, 3}, {-4, 3}, {4, -3}, {-4, -3}};
        for (int[] d : dirs) {
            int x = d[0], z = tz + d[1];
            // pas de console sur le passage de l'escalier helicoidal (elle couperait la hauteur sous plafond)
            if (spiralNear(x, z, y - 4, y - 1)) continue;
            if (s.isAir(x, y - 1, z)) s.set(x, y - 1, z, B.stairs("minecraft:dark_oak_stairs", outward(x, z).opposite(), true));
        }
    }

    // ================================================================ belvedere

    private boolean column(int x, int z) {
        boolean front = z == z2 && (Math.abs(x) == rx || Math.abs(x) == 1);
        boolean back = z == z1 && (Math.abs(x) == rx || Math.abs(x) == 2);
        boolean side = Math.abs(x) == rx && (z == z1 || z == z2 || z == (z1 + z2) / 2);
        return front || back || side;
    }

    private static final String BEAM = "minecraft:stripped_spruce_log";
    private static final String POST = "minecraft:dark_oak_log";

    private void room() {
        int top = by + 5;
        int mid = (z1 + z2) / 2;
        // charpente sous le plancher : poutres de rive et entretoise derriere le verre (sapin ecorce)
        for (int x = -rx; x <= rx; x++) {
            s.set(x, by - 1, z1, B.axis(BEAM, 'x'));
            s.set(x, by - 1, z2, B.axis(BEAM, 'x'));
        }
        for (int z = z1; z <= z2; z++) {
            s.set(-rx, by - 1, z, B.axis(BEAM, 'z'));
            s.set(rx, by - 1, z, B.axis(BEAM, 'z'));
        }
        // jambes de force : de la tour vers les 4 coins du belvedere
        for (int sx = -1; sx <= 1; sx += 2) {
            Shapes.line(s, sx * 3, by - 9, tz + 1, sx * rx, by - 2, z2, Mat.of(B.axis(POST, 'y')));
            Shapes.line(s, sx * 3, by - 9, tz - 1, sx * rx, by - 2, z1, Mat.of(B.axis(POST, 'y')));
        }
        // plancher, murs, corniche
        for (int x = -rx; x <= rx; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = Math.abs(x) == rx || z == z1 || z == z2;
                boolean glass = z >= z2 - 3 && Math.abs(x) < rx && !column(x, z);
                String m;
                if (glass) m = "minecraft:glass";
                else if (edge) m = B.axis(BEAM, Math.abs(x) == rx ? 'z' : 'x');
                else m = "minecraft:dark_oak_planks";
                s.set(x, by, z, m);
                for (int y = by + 1; y < top; y++) s.set(x, y, z, B.AIR);
                if (!edge) {
                    s.set(x, top, z, "minecraft:dark_oak_planks");
                    continue;
                }
                s.set(x, top, z, B.axis(BEAM, Math.abs(x) == rx ? 'z' : 'x'));
                if (column(x, z)) {
                    for (int y = by + 1; y < top; y++) s.set(x, y, z, B.axis(POST, 'y'));
                    continue;
                }
                if (x == 0 && z == z2) {
                    // porte du plongeoir : 3 de haut, linteau
                    s.set(x, by + 4, z, B.axis(BEAM, 'x'));
                    continue;
                }
                for (int y = by + 1; y < top; y++) {
                    // murs en verre clair ; allege pleine sur le mur du fond seulement
                    s.set(x, y, z, z == z1 && y == by + 1 ? "minecraft:cut_sandstone" : "minecraft:glass");
                }
            }
        }
        // pilastres en saillie (fond et cotes) : poteau, base sur console, ils cassent les grands pans vitres
        int[][] pil = {{-2, z1 - 1, 2}, {2, z1 - 1, 2}, {-rx - 1, mid, 1}, {rx + 1, mid, 3}};
        for (int[] p : pil) {
            Dir toWall = Dir.values()[p[2]];
            s.set(p[0], by - 1, p[1], B.stairs("minecraft:dark_oak_stairs", toWall, true));
            for (int y = by; y < top; y++) s.set(p[0], y, p[1], B.axis(POST, 'y'));
        }
        // toit a croupes, debord de 1 : la rive avant reste en by+5, au-dessus de la tete d'un joueur qui saute
        RoofStyle rs = new RoofStyle()
                .stairs(Palette.of(62, "minecraft:prismarine_brick_stairs", 70, "minecraft:dark_prismarine_stairs", 30).period(5))
                .ridge("minecraft:prismarine_brick_slab")
                .body(Mat.of("minecraft:dark_prismarine"))
                .overhang(1);
        int ridge = Roof.build(s, rs, Roof.hip(-rx, z1, rx, z2, top));
        // epi de faitage : mat, lanterne, paratonnerre
        int ry = ridge + 1;
        s.set(0, ry, mid, "minecraft:dark_oak_fence");
        s.set(0, ry + 1, mid, "minecraft:dark_oak_fence");
        s.set(0, ry + 2, mid, "minecraft:lantern[hanging=false]");
        s.set(0, ry + 3, mid, "minecraft:lightning_rod");
        // lanternes suspendues sous le debord aux 4 coins, et dans la salle
        int[][] eaves = {{-rx - 1, z1 - 1}, {rx + 1, z1 - 1}, {-rx - 1, z2 + 1}, {rx + 1, z2 + 1}};
        for (int[] e : eaves) s.set(e[0], top - 1, e[1], "minecraft:lantern[hanging=true]");
        int[][] inner = {{-3, z1 + 3}, {3, z1 + 3}, {-3, z2 - 2}, {3, z2 - 2}};
        for (int[] e : inner) s.set(e[0], top - 1, e[1], "minecraft:lantern[hanging=true]");
        // bancs face a la piscine le long du mur du fond, jardinieres
        for (int x = -rx + 1; x <= rx - 1; x++) {
            if (Math.abs(x) <= 1) continue;
            s.set(x, by + 1, z1 + 1, B.stairs("minecraft:spruce_stairs", Dir.NORTH, false));
        }
        s.set(0, by + 1, z1 + 1, "minecraft:chiseled_sandstone");
        s.set(0, by + 2, z1 + 1, "minecraft:potted_dead_bush");
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * (rx - 1);
            s.set(x, by + 1, mid - 1, "minecraft:spruce_trapdoor[facing=" + (sx < 0 ? "east" : "west") + ",half=top,open=false]");
            s.set(x, by + 2, mid - 1, "minecraft:potted_fern");
        }
        // tapis devant les bancs
        for (int x = -2; x <= 2; x++) s.set(x, by + 1, z1 + 3, "minecraft:light_blue_carpet");
    }

    // ================================================================ plongeoir

    private void board() {
        for (int z = z2 + 1; z <= -2; z++) {
            s.set(0, by, z, z == -2 ? "minecraft:yellow_concrete" : "minecraft:spruce_slab[type=top]");
            for (int y = by + 1; y <= by + 4; y++) s.set(0, y, z, B.AIR);
        }
    }

    // ================================================================ plage

    private int ground(int x, int z) {
        int y = WY + 8;
        while (y > WY - 6 && !B.isSolid(s.get(x, y, z))) y--;
        return y;
    }

    /** Vrai si on peut decorer en (x,z) : hors piscine, terrasse, tour, trajectoire du plongeon. */
    private boolean free(int x, int z) {
        if (Math.sqrt(x * x + z * z) <= r + 5) return false;
        if (dt(x, z) <= 7.5) return false;
        return Math.abs(x) <= AREA - 2 && Math.abs(z) <= AREA - 2;
    }

    private void beach() {
        SplittableRandom rnd = new SplittableRandom(99);
        // rochers au pied des falaises et dans le lagon
        Palette rock = Palette.of(71, "minecraft:stone", 55, "minecraft:andesite", 30, "minecraft:tuff", 15).period(4);
        int[][] rocks = {{-(r + 15), -(r + 4), 3}, {r + 16, -(r + 8), 2}, {-(r + 12), r + 10, 2}, {r + 13, r + 12, 3},
                {-12, AREA - 6, 2}, {9, AREA - 7, 2}, {-(r + 17), 6, 2}};
        for (int[] k : rocks) {
            int x = k[0], z = k[1];
            if (Math.abs(x) > AREA - 3 || Math.abs(z) > AREA - 3) continue;
            int gy = ground(x, z);
            double rr = k[2] + 0.5;
            Sdf.ellipsoid(x, gy, z, rr * 1.3, rr, rr).deform(n.derive(x * 31 + z), 0.9, 3.0)
                    .fillLayered(s, depth -> depth == 0 && gy >= WY ? Mat.of("minecraft:moss_block") : rock);
        }
        // palmiers sur mesure, penches vers la piscine
        int[][] palms = {{-(r + 7), r + 4}, {r + 8, r + 6}, {-(r + 10), -8}, {r + 10, -9}, {3, r + 9},
                {-(r + 13), -(r + 9)}, {r + 14, 9}, {-(r + 4), r + 11}};
        int i = 0;
        for (int[] p : palms) {
            if (!free(p[0], p[1])) continue;
            palm(p[0], ground(p[0], p[1]) + 1, p[1], 6 + (i * 5) % 4, 900 + i);
            i++;
        }
        // cabines de plage a rayures, cote est, portes vers la piscine
        String[][] stripes = {{"minecraft:white_concrete", "minecraft:light_blue_wool"},
                {"minecraft:white_concrete", "minecraft:red_wool"},
                {"minecraft:white_concrete", "minecraft:yellow_wool"}};
        int cx = r + 11;
        // trois cabines a ecarts, retraits, hauteurs et sens de toit differents (pas une rangee de boites)
        int[][] cab = {{0, -6, 3, 0}, {1, 0, 4, 1}, {-1, 5, 3, 0}};
        for (int c = 0; c < 3; c++) cabin(cx + cab[c][0], cab[c][1], stripes[c], c, cab[c][2], cab[c][3] == 1);
        // transats et parasols, cote ouest
        lounge(-(r + 7), 2, "minecraft:light_blue_wool", "minecraft:white_wool");
        lounge(-(r + 7), -4 + 0, "minecraft:orange_wool", "minecraft:white_wool");
        // serviettes sur le sable
        towel(-3, r + 7, "minecraft:red_carpet", "minecraft:white_carpet");
        towel(5, r + 6, "minecraft:cyan_carpet", "minecraft:yellow_carpet");
        // ponton sur le lagon
        pier();
        // lampadaires le long de la plage
        int[][] lamps = {{-(r + 10), 7}, {r + 8, -12}, {-(r + 11), -1}, {0, r + 12}};
        for (int[] l : lamps) if (free(l[0], l[1]) && ground(l[0], l[1]) >= WY) lamp(l[0], ground(l[0], l[1]) + 1, l[1], 0);
        // vegetation : grappes sur l'herbe (falaises), touffes seches et buissons sur le sable
        t.vegetation(s, 5, 0.95);
        Noise vn = n.derive(21);
        for (int x = -AREA + 1; x < AREA; x++) {
            for (int z = -AREA + 1; z < AREA; z++) {
                if (!free(x, z)) continue;
                int gy = ground(x, z);
                String g = B.name(s.get(x, gy, z));
                if (!s.isAir(x, gy + 1, z)) continue;
                double c = vn.uniform2(x / 6.0, z / 6.0);
                double h = Noise.hash(7, x, gy, z);
                if (g.equals("sand") && gy >= WY) {
                    if (c > 0.52 && h < 0.6) s.set(x, gy + 1, z, h < 0.12 ? "minecraft:dead_bush" : h < 0.25 ? "minecraft:short_dry_grass" : "minecraft:tall_dry_grass");
                } else if (g.equals("grass_block")) {
                    if (c > 0.8 && h < 0.08) s.set(x, gy + 1, z, h < 0.03 ? "minecraft:flowering_azalea" : "minecraft:azalea");
                    else if (c > 0.35 && c < 0.62 && h < 0.22) s.set(x, gy + 1, z, h < 0.05 ? "minecraft:fern" : "minecraft:short_grass");
                    else if (c < 0.12 && h < 0.3 && s.isAir(x, gy + 2, z)) {
                        s.set(x, gy + 1, z, "minecraft:large_fern[half=lower]");
                        s.set(x, gy + 2, z, "minecraft:large_fern[half=upper]");
                    }
                } else if (B.name(s.get(x, gy + 1, z)).equals("water") || g.equals("gravel") || g.equals("clay")) {
                    // fond du lagon
                }
            }
        }
        // herbiers et cornichons de mer dans le lagon
        for (int x = -AREA + 1; x < AREA; x++) {
            for (int z = 8; z < AREA; z++) {
                if (!free(x, z) || !B.name(s.get(x, WY - 1, z)).equals("water")) continue;
                int y = WY - 1;
                while (y > WY - 8 && B.name(s.get(x, y, z)).equals("water")) y--;
                double c = vn.uniform2(x / 5.0 + 40, z / 5.0);
                double h = Noise.hash(8, x, y, z);
                if (c > 0.6 && h < 0.45) {
                    if (h < 0.08 && y + 2 < WY - 1) {
                        s.set(x, y + 1, z, "minecraft:tall_seagrass[half=lower]");
                        s.set(x, y + 2, z, "minecraft:tall_seagrass[half=upper]");
                    } else if (h < 0.12) s.set(x, y + 1, z, "minecraft:sea_pickle[pickles=3,waterlogged=true]");
                    else s.set(x, y + 1, z, "minecraft:seagrass");
                } else if (c < 0.15 && h < 0.05 && y + 1 < WY - 1) {
                    s.set(x, WY, z, "minecraft:lily_pad");
                }
            }
        }
    }

    /** Palmier : tronc courbe (decale d'un bloc tous les 2-3), couronne de palmes retombantes, noix de coco. */
    private void palm(int x, int y, int z, int h, long seed) {
        SplittableRandom rr = new SplittableRandom(seed);
        // penche vers le centre (la piscine)
        Dir lean = Math.abs(x) > Math.abs(z) ? (x > 0 ? Dir.WEST : Dir.EAST) : (z > 0 ? Dir.NORTH : Dir.SOUTH);
        int cx = x, cz = z;
        int next = 2 + rr.nextInt(2);
        for (int k = 0; k < h; k++) {
            s.set(cx, y + k, cz, B.axis("minecraft:jungle_log", 'y'));
            if (k == next && k < h - 1) {
                cx += lean.dx;
                cz += lean.dz;
                s.set(cx, y + k, cz, B.axis("minecraft:jungle_log", lean.axis()));
                next += 2 + rr.nextInt(2);
            }
        }
        int ty = y + h;
        s.set(cx, ty, cz, B.leaves("minecraft:jungle_leaves"));
        s.set(cx, ty + 1, cz, B.leaves("minecraft:jungle_leaves"));
        // noix de coco sous la couronne
        for (Dir d : Dir.values()) {
            if (rr.nextDouble() < 0.5) s.set(cx + d.dx, ty - 1, cz + d.dz, "minecraft:cocoa[age=2,facing=" + d.opposite().id() + "]");
        }
        // 8 palmes de longueur inegale qui retombent
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] d : dirs) {
            int len = (d[0] != 0 && d[1] != 0 ? 3 : 4) + (rr.nextDouble() < 0.4 ? 1 : 0);
            for (int k = 1; k <= len; k++) {
                int fy = ty + 1 - (k >= 3 ? k - 2 : 0);
                int fx = cx + d[0] * k, fz = cz + d[1] * k;
                if (s.isAir(fx, fy, fz)) s.set(fx, fy, fz, B.leaves("minecraft:jungle_leaves"));
                if (k == len && s.isAir(fx, fy - 1, fz)) s.set(fx, fy - 1, fz, B.leaves("minecraft:jungle_leaves"));
            }
        }
    }

    /** Cabine de plage 3x3 a rayures verticales, porte a l'ouest, toit a deux pans en debord. */
    private void cabin(int x0, int zc, String[] col, int v, int hgt, boolean ridgeX) {
        int y = WY + 3;
        // plancher sur pilotis bas (poteaux aux angles)
        for (int x = x0 - 1; x <= x0 + 1; x++)
            for (int z = zc - 1; z <= zc + 1; z++) {
                int g = ground(x, z);
                boolean corner = x != x0 && z != zc;
                for (int yy = g + 1; yy < y - 1; yy++) s.set(x, yy, z, corner ? B.axis("minecraft:stripped_spruce_log", 'y') : B.AIR);
                s.set(x, y - 1, z, "minecraft:birch_planks");
            }
        s.set(x0 - 2, y - 1, zc, B.slab("minecraft:spruce_slab", "bottom"));
        for (int x = x0 - 1; x <= x0 + 1; x++)
            for (int z = zc - 1; z <= zc + 1; z++) {
                boolean wall = x != x0 || z != zc;
                for (int yy = y; yy < y + hgt; yy++) {
                    if (!wall) { s.set(x, yy, z, B.AIR); continue; }
                    // rayures : une colonne sur deux, comptee le long du pourtour (regle, pas hasard)
                    boolean corner = x != x0 && z != zc;
                    String m = corner ? "minecraft:stripped_spruce_log" : ((x + z) & 1) == 0 ? col[0] : col[1];
                    if (corner) m = B.axis(m, 'y');
                    s.set(x, yy, z, m);
                }
            }
        // porte cote piscine (ouest), lanterne au-dessus
        s.set(x0 - 1, y, zc, B.door("minecraft:spruce_door", Dir.EAST, false, v % 2 == 0));
        s.set(x0 - 1, y + 1, zc, B.door("minecraft:spruce_door", Dir.EAST, true, v % 2 == 0));
        // lanterne suspendue sous le debord du toit, accrochee par une chaine (posee apres le toit)
        s.set(x0 - 1, y + 2, zc, "minecraft:stripped_spruce_log[axis=z]");
        if (hgt > 3) s.set(x0 - 1, y + 3, zc, "minecraft:spruce_trapdoor[facing=west,half=top,open=false]");
        // fenetre a l'est
        s.set(x0 + 1, y + 1, zc, "minecraft:glass_pane");
        int ridge = Roof.build(s, new RoofStyle().stairs("minecraft:spruce_stairs").ridge("minecraft:spruce_slab")
                .body(Mat.of("minecraft:spruce_planks")).gable(Mat.of(col[1])).gablePost(null).overhang(1),
                ridgeX ? Roof.gableX(x0 - 1, zc - 1, x0 + 1, zc + 1, y + hgt - 1)
                        : Roof.gableZ(x0 - 1, zc - 1, x0 + 1, zc + 1, y + hgt - 1));
        if (ridge < 0) s.set(x0, y + hgt, zc, "minecraft:spruce_planks");
        int ly = y + 1, lx = x0 - 2, lz = zc + 1;
        int hook = ly + 1;
        while (hook <= ly + 5 && s.isAir(lx, hook, lz)) hook++;
        if (hook <= ly + 5 && s.isAir(lx, ly, lz)) {
            for (int yy = ly + 1; yy < hook; yy++) s.set(lx, yy, lz, "minecraft:iron_chain[axis=y]");
            s.set(lx, ly, lz, "minecraft:lantern[hanging=true]");
        }
    }

    /** Deux transats autour d'un parasol. */
    private void lounge(int x0, int z0, String c1, String c2) {
        for (int k = -1; k <= 1; k += 2) {
            int x = x0 + 2 * k;
            int g = ground(x, z0);
            if (B.name(s.get(x, g, z0)).contains("water")) continue;
            s.set(x, g + 1, z0 - 1, B.stairs("minecraft:birch_stairs", Dir.NORTH, false));
            s.set(x, g + 1, z0, B.slab("minecraft:birch_slab", "bottom"));
            s.set(x, g + 1, z0 + 1, B.slab("minecraft:birch_slab", "bottom"));
        }
        int g = ground(x0, z0);
        s.set(x0, g + 1, z0, "minecraft:birch_fence");
        s.set(x0, g + 2, z0, "minecraft:birch_fence");
        s.set(x0, g + 3, z0, c1);
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : arms) s.set(x0 + a[0], g + 3, z0 + a[1], c2);
        int[][] corners = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] a : corners) s.set(x0 + a[0], g + 3, z0 + a[1], c1); // toile pleine aux coins (un tapis flotterait)
    }

    private void towel(int x0, int z0, String a, String b) {
        for (int dz : new int[]{0, -1, 1, -2, 2, -3, 3}) if (towelAt(x0, z0 + dz, a, b)) return;
    }

    private boolean towelAt(int x0, int z, String a, String b) {
        // les 4 cases doivent etre du sable au meme niveau, a l'air libre (un tapis ne tient que sur un bloc) :
        // on cherche la place la plus proche le long de la plage
        int x = Integer.MIN_VALUE, g = 0;
        for (int k = 0; k <= 8 && x == Integer.MIN_VALUE; k++) {
            for (int c : new int[]{x0 + k, x0 - k}) {
                int gg = ground(c, z);
                boolean ok = true;
                for (int dx = 0; dx <= 1 && ok; dx++)
                    for (int dz = 0; dz <= 1 && ok; dz++)
                        ok = ground(c + dx, z + dz) == gg && B.name(s.get(c + dx, gg, z + dz)).equals("sand")
                                && s.isAir(c + dx, gg + 1, z + dz) && free(c + dx, z + dz);
                if (ok) { x = c; g = gg; break; }
            }
        }
        if (x == Integer.MIN_VALUE) return false;
        s.set(x, g + 1, z, a);
        s.set(x, g + 1, z + 1, b);
        s.set(x + 1, g + 1, z, b);
        s.set(x + 1, g + 1, z + 1, a);
        return true;
    }

    /** Ponton en bois qui avance dans le lagon, poteaux dans l'eau. */
    private void pier() {
        int x = -(r + 3);
        int zs = -1;
        for (int z = r + 6; z < AREA - 3; z++) {
            int g = ground(x, z);
            if (g >= WY && !B.name(s.get(x, WY - 1, z)).equals("water")) { zs = z; continue; }
            break;
        }
        if (zs < 0) return;
        int end = Math.min(AREA - 4, zs + 8);
        for (int z = zs; z <= end; z++) {
            for (int dx = 0; dx <= 1; dx++) s.set(x + dx, WY, z, B.slab("minecraft:spruce_slab", "top"));
            if ((z - zs) % 3 == 0 || z == end) {
                for (int dx = -1; dx <= 2; dx += 3) {
                    int g = ground(x + dx, z);
                    for (int y = g + 1; y <= WY; y++) s.set(x + dx, y, z, "minecraft:stripped_spruce_log[axis=y]");
                    s.set(x + dx, WY + 1, z, "minecraft:spruce_fence");
                }
            }
        }
        s.set(x - 1, WY + 2, end, "minecraft:lantern[hanging=false]");
        s.set(x + 2, WY + 2, end, "minecraft:lantern[hanging=false]");
    }
}
