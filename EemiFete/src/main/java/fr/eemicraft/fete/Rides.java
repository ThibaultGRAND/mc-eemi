package fr.eemicraft.fete;

import fr.eemicraft.fete.build.B;
import fr.eemicraft.fete.build.Dir;
import fr.eemicraft.fete.build.Sink;

import java.util.ArrayList;
import java.util.List;

/**
 * Les manèges de la fête foraine : socle fixe (vrais blocs, dans le repère LOCAL du parc) + pièces mobiles
 * ({@link Ride}, BlockDisplay en jeu). Chaque méthode pose le socle si {@code s} n'est pas nul et rend le manège.
 * Les emplacements sont ceux du plan du parc ({@link Defs}).
 */
public final class Rides {
    private Rides() {
    }

    static final int F = Geo.FLOOR;
    // ampoules : couleurs de fête, toujours lumineuses
    static final String[] BULBS = {"minecraft:pearlescent_froglight", "minecraft:ochre_froglight",
            "minecraft:verdant_froglight", "minecraft:sea_lantern", "minecraft:shroomlight"};
    static final String WARM = "minecraft:ochre_froglight";

    static String bulb(int i) {
        return BULBS[Math.floorMod(i, BULBS.length)];
    }

    static void set(Sink s, int u, int y, int v, String st) {
        if (s != null) s.set(u, y, v, st);
    }

    static void line(Sink s, double u1, double y1, double v1, double u2, double y2, double v2, String st) {
        int n = (int) Math.ceil(Math.max(Math.abs(u2 - u1), Math.max(Math.abs(y2 - y1), Math.abs(v2 - v1))) * 2) + 1;
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n;
            set(s, (int) Math.floor(u1 + (u2 - u1) * f), (int) Math.floor(y1 + (y2 - y1) * f), (int) Math.floor(v1 + (v2 - v1) * f), st);
        }
    }

    /** Disque plein de rayon r centré sur (cu, cv) (centres de blocs). */
    static void disk(Sink s, double cu, double cv, double r, int y, String st) {
        for (int u = (int) Math.floor(cu - r - 1); u <= cu + r + 1; u++) {
            for (int v = (int) Math.floor(cv - r - 1); v <= cv + r + 1; v++) {
                if (Ride.sq(u + 0.5 - cu) + Ride.sq(v + 0.5 - cv) <= r * r) set(s, u, y, v, st);
            }
        }
    }

    // ------------------------------------------------------------------ carrousel

    /** Carrousel : centre (cu, cv) au milieu d'un bloc, rayon 6. Toit rayé rouge et blanc, 8 chevaux qui montent et descendent. */
    public static Ride carrousel(Sink s, double cu, double cv) {
        Ride r = new Ride("carrousel").board(cu, cv - 7.9);
        // socle fixe : marche circulaire et barrière dorée
        disk(s, cu, cv, 6.9, F + 1, B.slab("minecraft:polished_diorite_slab", "bottom"));
        for (int k = 0; k < 40; k++) {
            double a = Math.toRadians(k * 9);
            int u = (int) Math.floor(cu + 7.3 * Math.cos(a)), v = (int) Math.floor(cv + 7.3 * Math.sin(a));
            if (Math.abs(Math.sin(a)) > 0.92 && Math.sin(a) < 0) continue; // entrée côté allée (v petit)
            set(s, u, F + 1, v, "minecraft:polished_blackstone_wall");
            if (k % 5 == 0) set(s, u, F + 2, v, "minecraft:lantern");
        }
        double py = F + 1.75;
        Ride.Part top = r.part("plateau", cu, py, cv, Ride.Axis.Y, Ride.spin(-14));
        // plancher en bois (anneaux alternés)
        for (int k = 0; k < 24; k++) {
            double a = k * 15;
            for (double rr = 1.5; rr <= 5.6; rr += 1.0) {
                double x = rr * Math.cos(Math.toRadians(a)), z = rr * Math.sin(Math.toRadians(a));
                String w = ((int) rr) % 2 == 0 ? "minecraft:birch_planks" : "minecraft:dark_oak_planks";
                top.cube(x, 0, z, 1.35, 0.25, 1.35, a, w);
            }
        }
        // colonne centrale : or et miroirs
        top.cube(0, 2.75, 0, 2.0, 5.5, 2.0, "minecraft:gold_block");
        for (int k = 0; k < 4; k++) top.cube(0, 2.6, 0, 2.15, 3.2, 0.5, k * 45, "minecraft:light_blue_stained_glass");
        // toit conique rayé : 16 secteurs, 4 couronnes
        for (int k = 0; k < 32; k++) {
            double a = k * 11.25;
            String w = (k / 2) % 2 == 0 ? "minecraft:red_wool" : "minecraft:white_wool";
            for (int ring = 0; ring < 4; ring++) {
                double rr = 6.3 - ring * 1.55, y = 5.6 + ring * 0.9;
                double x = rr * Math.cos(Math.toRadians(a)), z = rr * Math.sin(Math.toRadians(a));
                top.cube(x, y, z, 1.55 * (rr / 6.3) + 0.6, 0.7, 1.4, a, w);
            }
        }
        top.cube(0, 9.4, 0, 1.2, 1.2, 1.2, "minecraft:gold_block");
        top.light(0, 10.4, 0, 0.7, WARM);
        // lambrequin doré et ampoules au bord du toit
        for (int k = 0; k < 24; k++) {
            double a = Math.toRadians(k * 15);
            top.cube(6.6 * Math.cos(a), 5.0, 6.6 * Math.sin(a), 0.3, 0.6, 1.9, k * 15, "minecraft:gold_block");
            top.light(6.85 * Math.cos(a), 4.55, 6.85 * Math.sin(a), 0.32, bulb(k));
        }
        // 8 chevaux sur leur barre, chacun monte et descend
        String[] coats = {"minecraft:white_concrete", "minecraft:brown_terracotta", "minecraft:black_concrete", "minecraft:white_concrete"};
        String[] saddles = {"minecraft:red_wool", "minecraft:blue_wool", "minecraft:lime_wool", "minecraft:purple_wool"};
        for (int k = 0; k < 8; k++) {
            double a = k * 45 + 22.5, rr = 4.0;
            Ride.Part h = r.part("cheval" + k, cu, py, cv, Ride.Axis.Y, Ride.spinBob(-14, 0.45, 3.0, k * 0.37));
            double x = rr * Math.cos(Math.toRadians(a)), z = rr * Math.sin(Math.toRadians(a));
            // le plateau tourne en sens inverse (période négative) : la tête regarde vers -tangente
            double tx = Math.sin(Math.toRadians(a)), tz = -Math.cos(Math.toRadians(a));
            String coat = coats[k % 4], saddle = saddles[k % 4];
            h.cube(x, 3.0, z, 0.12, 5.6, 0.12, "minecraft:gold_block");                    // barre
            h.model(x, 2.0, z, 1.3, a + 180, "cheval", COATS[k % COATS.length]);
            h.seat(x, 2.45, z);
            h.blocksOnly();
            h.cube(x, 1.55, z, 0.5, 0.55, 1.25, a, coat);                                  // corps
            h.cube(x + tx * 0.75, 2.05, z + tz * 0.75, 0.38, 0.7, 0.4, a, coat);            // cou
            h.cube(x + tx * 0.95, 2.35, z + tz * 0.95, 0.34, 0.32, 0.55, a, coat);          // tête
            h.cube(x, 1.9, z, 0.56, 0.16, 0.6, a, saddle);                                  // selle
            h.cube(x - tx * 0.7, 1.45, z - tz * 0.7, 0.16, 0.5, 0.16, a, "minecraft:gold_block"); // queue
            for (int leg = 0; leg < 4; leg++) {
                double f = leg < 2 ? 0.42 : -0.42, side = leg % 2 == 0 ? 0.15 : -0.15;
                double nx = Math.cos(Math.toRadians(a)) * side, nz = Math.sin(Math.toRadians(a)) * side;
                h.cube(x + tx * f + nx, 1.0, z + tz * f + nz, 0.12, 0.6, 0.12, a, coat);
            }
            h.always();
        }
        return r;
    }

    /** Robes des chevaux (teinte du modèle) : blanc, alezan, noir, gris pommelé, isabelle. */
    static final int[] COATS = {0xF4F1EA, 0x8B5A2B, 0x2E2B2B, 0xB9B9B9, 0xD9B77A};
    /** Couleurs des nacelles, voitures et wagons (teinte du modèle). */
    static final int[] PAINT = {0xD03A3A, 0xF2C230, 0x58B7E6, 0x7BD04A, 0xF08A2A, 0xF08CB4};

    // ------------------------------------------------------------------ kiosque de la place

    /**
     * Kiosque à musique de la place (pièces FIXES en coordonnées monde, pas de parc) : 8 colonnettes en fer forgé à
     * chapiteau doré, toit conique rayé rouge et blanc comme le carrousel, lambrequin doré, ampoules, flèche.
     * Le socle, le plancher et le garde-corps sont en blocs ({@link Draw#kiosque}).
     */
    public static Ride kiosque(double cx, double cz) {
        Ride r = new Ride("kiosque");
        double py = F + 3;
        Ride.Part k = r.part("kiosque", cx, py, cz, Ride.Axis.Y, Ride.fixed());
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            double x = 3.95 * Math.cos(a), z = 3.95 * Math.sin(a);
            k.cube(x, 1.9, z, 0.22, 3.8, 0.22, "minecraft:polished_blackstone");
            k.cube(x, 0.1, z, 0.42, 0.2, 0.42, "minecraft:gold_block");
            k.cube(x, 3.75, z, 0.42, 0.25, 0.42, "minecraft:gold_block");
            // consoles dorées vers l'avant-toit
            k.cube(x * 1.12, 3.55, z * 1.12, 0.12, 0.12, 0.9, i * 45 + 22.5 + 90, "minecraft:gold_block");
        }
        for (int i = 0; i < 32; i++) {
            double a = i * 11.25;
            String w = (i / 2) % 2 == 0 ? "minecraft:red_wool" : "minecraft:white_wool";
            for (int ring = 0; ring < 5; ring++) {
                double rr = 6.2 - ring * 1.3, y = 4.1 + ring * 0.85;
                double x = rr * Math.cos(Math.toRadians(a)), z = rr * Math.sin(Math.toRadians(a));
                k.cube(x, y, z, 1.4 * (rr / 6.2) + 0.55, 0.6, 1.45, a, w);
            }
        }
        k.cube(0, 8.6, 0, 1.0, 1.0, 1.0, "minecraft:gold_block");
        k.cube(0, 9.6, 0, 0.2, 1.2, 0.2, "minecraft:gold_block");
        k.light(0, 10.35, 0, 0.45, WARM);
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15);
            k.cube(6.45 * Math.cos(a), 3.55, 6.45 * Math.sin(a), 0.25, 0.55, 1.75, i * 15, "minecraft:gold_block");
            k.light(6.7 * Math.cos(a), 3.15, 6.7 * Math.sin(a), 0.3, bulb(i));
        }
        // plafond : rosace dorée et lustre
        k.cube(0, 4.0, 0, 3.2, 0.12, 3.2, "minecraft:gold_block");
        k.light(0, 3.6, 0, 0.6, WARM);
        return r;
    }

    // ------------------------------------------------------------------ grande roue

    /**
     * Grande roue dans le plan (u, y) : axe au centre (cu, cy, cv), rayon R, 12 nacelles qui restent droites.
     * Pieds en A de part et d'autre (v = cv ± 2).
     */
    public static Ride roue(Sink s, double cu, double cy, double cv, double R) {
        Ride r = new Ride("grande_roue").board(cu, cv - 3.5);
        // pieds en A (poutres fines en BlockDisplay) et socles en pierre
        Ride.Part legs = r.part("pieds", cu, cy, cv, Ride.Axis.V, Ride.fixed());
        double h = cy - F - 1;
        for (int side = -1; side <= 1; side += 2) {
            double z = side * 2.0;
            for (int leg = -1; leg <= 1; leg += 2) {
                legs.strut(leg * 7.5, -h, z, leg * 0.3, 0, z, 0.55, "minecraft:white_concrete");
                legs.strut(leg * 5.0, -h, z, leg * 0.2, -0.2, z, 0.25, "minecraft:light_gray_concrete");
                set(s, (int) Math.floor(cu + leg * 7.5), F + 1, (int) Math.floor(cv + z), "minecraft:polished_andesite");
            }
            legs.strut(-4.1, -h * 0.55, z, 4.1, -h * 0.55, z, 0.3, "minecraft:white_concrete");
            legs.strut(-2.0, -h * 0.25, z, 2.0, -h * 0.25, z, 0.3, "minecraft:white_concrete");
        }
        // quai d'embarquement
        for (int u = (int) cu - 3; u <= cu + 3; u++) {
            for (int v = (int) cv - 1; v <= cv + 1; v++) set(s, u, F + 1, v, B.slab("minecraft:smooth_stone_slab", "bottom"));
        }
        Ride.Part wheel = r.part("roue", cu, cy, cv, Ride.Axis.V, Ride.spin(40));
        wheel.cube(0, 0, 0, 1.6, 1.6, 3.4, "minecraft:iron_block");
        wheel.light(0, 0, 1.8, 0.9, WARM);
        wheel.light(0, 0, -1.8, 0.9, WARM);
        int spokes = 16;
        for (int k = 0; k < spokes; k++) {
            double a = k * 360.0 / spokes;
            for (int side = -1; side <= 1; side += 2) {
                double z = side * 0.9;
                // rayon : cube long et fin tourné de a (le long de -y en angle 0)
                double[] mid = Ride.rot(Ride.Axis.V, a, 0, -R / 2, 0);
                wheel.cube(mid[0], mid[1], z, 0.22, R, 0.22, a, "minecraft:white_concrete");
                // jante : segment tangent
                double[] rim = Ride.rot(Ride.Axis.V, a + 180.0 / spokes, 0, -R * Math.cos(Math.PI / spokes), 0);
                wheel.cube(rim[0], rim[1], z, 2 * R * Math.sin(Math.PI / spokes) + 0.25, 0.35, 0.35, a + 180.0 / spokes, "minecraft:white_concrete");
                // ampoules le long des rayons et sur la jante
                for (int b = 1; b <= 3; b++) {
                    double[] p = Ride.rot(Ride.Axis.V, a, 0, -R * b / 3.6, 0);
                    wheel.light(p[0], p[1], z * 1.25, 0.28, bulb(k + b));
                }
            }
            double[] tip = Ride.rot(Ride.Axis.V, a, 0, -R, 0);
            wheel.cube(tip[0], tip[1], 0, 0.3, 0.3, 2.0, a, "minecraft:white_concrete");
            wheel.light(tip[0], tip[1], 1.3, 0.4, bulb(k));
            wheel.light(tip[0], tip[1], -1.3, 0.4, bulb(k + 2));
        }
        // 12 nacelles colorées, droites
        String[] cols = {"red", "yellow", "light_blue", "lime", "orange", "pink"};
        int n = 12;
        for (int k = 0; k < n; k++) {
            double a0 = k * 360.0 / n;
            Ride.Part g = r.part("nacelle" + k, cu, cy, cv, Ride.Axis.V, Ride.orbit(Ride.Axis.V, R, 40, a0));
            String c = "minecraft:" + cols[k % cols.length] + "_concrete";
            g.model(0, -1.95, 0, 1.7, 0, "nacelle", PAINT[k % PAINT.length]);
            g.seat(-0.35, -2.55, 0);
            g.seat(0.35, -2.55, 0);
            g.blocksOnly();
            g.cube(0, -0.35, 0, 0.12, 0.7, 0.12, "minecraft:iron_block");          // attache
            g.cube(0, -1.35, 0, 1.5, 0.15, 1.5, c);                                // toit
            g.cube(0, -2.1, 0, 1.3, 0.12, 1.3, "minecraft:white_concrete");        // plafond
            g.cube(-0.6, -2.45, 0, 0.1, 0.9, 1.2, "minecraft:glass");              // vitres
            g.cube(0.6, -2.45, 0, 0.1, 0.9, 1.2, "minecraft:glass");
            g.cube(0, -2.95, 0, 1.4, 0.35, 1.4, c);                                // caisse
            g.light(0, -1.15, 0, 0.25, bulb(k));
        }
        return r;
    }

    // ------------------------------------------------------------------ chaises volantes

    /** Chaises volantes : tour centrale fixe, parasol tournant, 12 sièges suspendus qui s'écartent. */
    public static Ride chaises(Sink s, double cu, double cv) {
        Ride r = new Ride("chaises_volantes").board(cu, cv - 9.3);
        int cuI = (int) Math.floor(cu), cvI = (int) Math.floor(cv);
        disk(s, cu, cv, 2.6, F + 1, "minecraft:polished_andesite");
        for (int y = F + 1; y <= F + 11; y++) {
            String st = (y / 2) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:light_blue_concrete";
            set(s, cuI, y, cvI, st);
        }
        // aire des chaises : rosace de pierre polie et anneau de briques
        for (int u = (int) Math.floor(cu - 9); u <= cu + 9; u++) {
            for (int v = (int) Math.floor(cv - 9); v <= cv + 9; v++) {
                double d = Math.sqrt(Ride.sq(u + 0.5 - cu) + Ride.sq(v + 0.5 - cv));
                if (d > 8.6) continue;
                double a = Math.atan2(v + 0.5 - cv, u + 0.5 - cu);
                int sector = (int) Math.floor((a + Math.PI) / (Math.PI / 6));
                String st = d > 7.6 ? "minecraft:bricks" : (sector % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:polished_diorite");
                set(s, u, F, v, st);
            }
        }
        Ride.Part top = r.part("parasol", cu, F + 12.0, cv, Ride.Axis.Y, Ride.spin(9));
        for (int k = 0; k < 24; k++) {
            double a = k * 15;
            String w = (k / 2) % 2 == 0 ? "minecraft:light_blue_wool" : "minecraft:white_wool";
            for (int ring = 0; ring < 3; ring++) {
                double rr = 4.2 - ring * 1.3, y = ring * 0.65;
                double x = rr * Math.cos(Math.toRadians(a)), z = rr * Math.sin(Math.toRadians(a));
                top.cube(x, y, z, 1.6 * rr / 4.2 + 0.5, 0.6, 1.3, a, w);
            }
            top.light(4.5 * Math.cos(Math.toRadians(a)), -0.45, 4.5 * Math.sin(Math.toRadians(a)), 0.3, bulb(k));
        }
        top.cube(0, 2.0, 0, 0.9, 0.9, 0.9, "minecraft:gold_block");
        top.light(0, 2.8, 0, 0.6, WARM);
        // sièges : chaînes inclinées vers l'extérieur (13 blocs de rayon à pleine vitesse)
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30 + 15);
            double ca = Math.cos(a), sa = Math.sin(a);
            for (int c = 1; c <= 5; c++) {
                double f = c / 6.0;
                top.cube(ca * (4.0 + 3.2 * f), -f * 5.2, sa * (4.0 + 3.2 * f), 0.08, 0.95, 0.08, "minecraft:iron_block");
            }
            String seat = k % 2 == 0 ? "minecraft:yellow_concrete" : "minecraft:red_concrete";
            top.cube(ca * 7.3, -5.6, sa * 7.3, 0.7, 0.18, 0.7, k * 30 + 15, seat);
            top.seat(ca * 7.3, -5.45, sa * 7.3);
            top.cube(ca * 7.6, -5.2, sa * 7.6, 0.12, 0.6, 0.7, k * 30 + 15, seat);
        }
        return r;
    }

    // ------------------------------------------------------------------ bateau pirate

    /** Bateau pirate : coque suspendue sous un portique, balancement le long de u. */
    public static Ride bateau(Sink s, double cu, double cv) {
        Ride r = new Ride("bateau_pirate").board(cu, cv - 4.2);
        double py = F + 11.5;
        Ride.Part frame = r.part("portique", cu, py, cv, Ride.Axis.V, Ride.fixed());
        double h = py - F - 1;
        for (int side = -1; side <= 1; side += 2) {
            double z = side * 3.0;
            frame.strut(-6.5, -h, z, 0, 0, z, 0.6, "minecraft:dark_oak_log");
            frame.strut(6.5, -h, z, 0, 0, z, 0.6, "minecraft:dark_oak_log");
            frame.strut(-3.3, -h * 0.5, z, 3.3, -h * 0.5, z, 0.35, "minecraft:dark_oak_planks");
            set(s, (int) Math.floor(cu - 6.5), F + 1, (int) Math.floor(cv + z), "minecraft:polished_blackstone");
            set(s, (int) Math.floor(cu + 6.5), F + 1, (int) Math.floor(cv + z), "minecraft:polished_blackstone");
        }
        frame.cube(0, 0, 0, 0.7, 0.7, 6.6, "minecraft:stripped_dark_oak_wood");
        frame.light(0, 0.6, 3.3, 0.4, WARM);
        frame.light(0, 0.6, -3.3, 0.4, WARM);
        for (int u = (int) cu - 2; u <= cu + 2; u++) {
            for (int v = (int) cv - 1; v <= cv + 1; v++) set(s, u, F + 1, v, B.slab("minecraft:spruce_slab", "bottom"));
        }
        Ride.Part ship = r.part("coque", cu, py, cv, Ride.Axis.V, Ride.swing(55, 7.0));
        for (int side = -1; side <= 1; side += 2) {
            ship.cube(-1.2, -3.8, side * 1.6, 0.25, 7.6, 0.25, "minecraft:dark_oak_planks");
            ship.cube(1.2, -3.8, side * 1.6, 0.25, 7.6, 0.25, "minecraft:dark_oak_planks");
        }
        // coque : 11 de long, bord relevé aux extrémités
        for (int i = -5; i <= 5; i++) {
            double lift = 0.06 * i * i;
            double w = 2.6 - 0.12 * Math.abs(i);
            ship.cube(i, -8.6 + lift, 0, 1.05, 0.9, w, "minecraft:dark_oak_planks");
            ship.cube(i, -7.75 + lift, w / 2, 1.05, 0.7, 0.2, "minecraft:spruce_planks");
            ship.cube(i, -7.75 + lift, -w / 2, 1.05, 0.7, 0.2, "minecraft:spruce_planks");
            ship.cube(i, -9.2 + lift, 0, 1.0, 0.3, w * 0.7, i % 2 == 0 ? "minecraft:red_concrete" : "minecraft:black_concrete");
            if (Math.abs(i) <= 4 && i != 0) ship.cube(i, -7.9 + lift, 0, 0.8, 0.25, w * 0.8, "minecraft:spruce_slab");
        }
        // mât, voile, drapeau, figure de proue
        ship.cube(0, -5.2, 0, 0.3, 5.5, 0.3, "minecraft:spruce_log");
        for (int i : new int[]{-4, -3, -2, 2, 3, 4}) {
            ship.seat(i, -7.75 + 0.06 * i * i, 0.55);
            ship.seat(i, -7.75 + 0.06 * i * i, -0.55);
        }
        ship.cube(0, -5.4, 0, 3.2, 2.6, 0.12, "minecraft:white_wool");
        ship.cube(0, -2.2, 0.0, 1.0, 0.6, 0.08, "minecraft:black_wool");
        ship.cube(5.9, -7.0, 0, 0.7, 0.7, 0.6, "minecraft:gold_block");
        ship.light(-5.6, -6.8, 0, 0.4, WARM);
        ship.light(5.6, -6.6, 0.9, 0.3, bulb(1));
        return r;
    }

    // ------------------------------------------------------------------ tour de chute

    /** Tour de chute de 30 blocs : nacelle annulaire qui monte lentement et retombe. */
    public static Ride tour(Sink s, int cu, int cv) {
        Ride r = new Ride("tour_chute").board(cu - 2.6, cv + 1.0);
        int top = F + 32;
        for (int y = F + 1; y <= top; y++) {
            for (int du = 0; du <= 1; du++) {
                for (int dv = 0; dv <= 1; dv++) {
                    String st = ((y - F) % 6 == 0) ? "minecraft:sea_lantern" : (du == dv ? "minecraft:red_concrete" : "minecraft:white_concrete");
                    set(s, cu + du, y, cv + dv, st);
                }
            }
        }
        for (int du = -1; du <= 2; du++) {
            for (int dv = -1; dv <= 2; dv++) set(s, cu + du, top + 1, cv + dv, "minecraft:gold_block");
        }
        set(s, cu, top + 2, cv, "minecraft:pearlescent_froglight");
        set(s, cu + 1, top + 2, cv + 1, "minecraft:pearlescent_froglight");
        for (int du = -2; du <= 3; du++) {
            for (int dv = -2; dv <= 3; dv++) {
                if (du >= 0 && du <= 1 && dv >= 0 && dv <= 1) continue;
                set(s, cu + du, F + 1, cv + dv, B.slab("minecraft:polished_blackstone_slab", "bottom"));
            }
        }
        Ride.Part ring = r.part("nacelle", cu + 1.0, F + 2.2, cv + 1.0, Ride.Axis.Y, Ride.drop(26, 10, 2.5, 1.3, 4));
        for (int k = 0; k < 8; k++) {
            double a = k * 45;
            double x = 1.65 * Math.cos(Math.toRadians(a)), z = 1.65 * Math.sin(Math.toRadians(a));
            ring.cube(x, 0, z, 0.9, 0.25, 0.9, a, "minecraft:yellow_concrete");
            ring.seat(x, 0.15, z);
            ring.cube(x * 1.25, 0.55, z * 1.25, 0.15, 0.9, 0.8, a, "minecraft:yellow_concrete");
            ring.light(x * 1.35, 1.05, z * 1.35, 0.22, bulb(k));
        }
        ring.cube(0, 0.6, 0, 2.6, 0.25, 2.6, "minecraft:black_concrete");
        return r;
    }

    // ------------------------------------------------------------------ auto-tamponneuses

    /** Piste d'auto-tamponneuses (u1..u2, v1..v2) sous un plafond lumineux, 6 voitures. */
    public static Ride autos(Sink s, int u1, int v1, int u2, int v2) {
        Ride r = new Ride("autos").board((u1 + u2 + 1) / 2.0, v1 - 0.8);
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                boolean edge = u == u1 || u == u2 || v == v1 || v == v2;
                set(s, u, F, v, edge ? "minecraft:yellow_concrete" : ((u + v) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles"));
                if (edge) {
                    boolean entry = v == v1 && Math.abs(u - (u1 + u2) / 2) <= 1;
                    if (!entry) set(s, u, F + 1, v, "minecraft:black_wool");
                }
            }
        }
        int[][] posts = {{u1, v1}, {u2, v1}, {u1, v2}, {u2, v2}, {(u1 + u2) / 2, v2}};
        for (int[] p : posts) {
            for (int y = F + 2; y <= F + 6; y++) set(s, p[0], y, p[1], "minecraft:white_concrete");
        }
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                boolean grid = (u - u1) % 3 == 0 || (v - v1) % 3 == 0;
                if (grid) set(s, u, F + 7, v, ((u + v) % 6 == 0) ? "minecraft:pearlescent_froglight" : "minecraft:iron_bars");
                if (u == u1 || u == u2 || v == v1 || v == v2) {
                    set(s, u, F + 8, v, ((u + v) / 2) % 2 == 0 ? "minecraft:red_wool" : "minecraft:white_wool");
                }
            }
        }
        double cu = (u1 + u2 + 1) / 2.0, cv = (v1 + v2 + 1) / 2.0;
        double ax = (u2 - u1) / 2.0 - 2.0, az = (v2 - v1) / 2.0 - 2.0;
        String[] cols = {"red", "lime", "light_blue", "yellow", "orange", "magenta"};
        for (int k = 0; k < 6; k++) {
            Ride.Part car = r.part("auto" + k, cu, F + 1.3, cv, Ride.Axis.Y,
                    Ride.bumper(ax, az, 0.37 + 0.06 * k, 0.53 - 0.05 * k, k * 1.3));
            String c = "minecraft:" + cols[k] + "_concrete";
            car.model(0, 0.25, 0, 1.1, 0, "auto", PAINT[k % PAINT.length]);
            car.seat(0, 0.3, -0.1);
            car.blocksOnly();
            car.cube(0, 0, 0, 1.3, 0.55, 1.7, c);
            car.cube(0, 0.15, -0.55, 1.0, 0.35, 0.35, "minecraft:white_concrete");
            car.cube(0, 0.35, 0.2, 0.9, 0.12, 0.9, "minecraft:black_wool");
            car.cube(0, -0.25, 0, 1.45, 0.2, 1.85, "minecraft:black_concrete");
            car.light(0, 0.2, 0.86, 0.22, "minecraft:sea_lantern");
            car.always();
            car.cube(0, 2.9, 0.6, 0.08, 5.0, 0.08, "minecraft:iron_block");
        }
        return r;
    }

    // ------------------------------------------------------------------ train fantôme

    /** Train fantôme : façade de manoir hanté (u1..u2, v1..v2), fantôme qui flotte, wagonnet qui entre et sort. */
    public static Ride fantome(Sink s, int u1, int v1, int u2, int v2) {
        Ride r = new Ride("train_fantome").board((u1 + u2) / 2 + 1.0, v1 - 3.2);
        if (s != null) {
            for (int u = u1; u <= u2; u++) {
                for (int v = v1; v <= v2; v++) {
                    for (int y = F + 1; y <= F + 9; y++) {
                        boolean wall = u == u1 || u == u2 || v == v1 || v == v2;
                        if (!wall) continue;
                        String st = y <= F + 2 ? "minecraft:polished_blackstone_bricks"
                                : ((u + y) % 7 == 0 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:purple_terracotta");
                        set(s, u, y, v, st);
                    }
                }
            }
            // toit à deux pans (faîte le long de u) en ardoise sombre, pignons, deux tourelles
            int depth = v2 - v1;
            for (int k = 0; k <= depth / 2 + 1; k++) {
                int y = F + 10 + k;
                for (int u = u1 - 1; u <= u2 + 1; u++) {
                    int va = v1 - 1 + k, vb = v2 + 1 - k;
                    if (va > vb) continue;
                    set(s, u, y, va, B.stairs("minecraft:deepslate_tile_stairs", Dir.SOUTH, false));
                    set(s, u, y, vb, B.stairs("minecraft:deepslate_tile_stairs", Dir.NORTH, false));
                    for (int v = va + 1; v < vb; v++) {
                        if (u == u1 || u == u2) set(s, u, y, v, "minecraft:purple_terracotta");
                    }
                }
            }
            for (int tu : new int[]{u1, u2}) {
                for (int y = F + 1; y <= F + 16; y++) set(s, tu, y, v1, y > F + 12 ? "minecraft:polished_blackstone_bricks" : "minecraft:purple_terracotta");
                set(s, tu, F + 17, v1, "minecraft:soul_lantern");
            }
            // façade côté allée (v = v1) : deux portes en arche, crâne géant, fenêtres vertes
            int mid = (u1 + u2) / 2;
            for (int du : new int[]{-3, 3}) {
                for (int y = F + 1; y <= F + 3; y++) {
                    set(s, mid + du, y, v1, "minecraft:air");
                    set(s, mid + du + 1, y, v1, "minecraft:air");
                }
                set(s, mid + du, F + 4, v1, "minecraft:polished_blackstone_brick_wall");
                set(s, mid + du + 1, F + 4, v1, "minecraft:polished_blackstone_brick_wall");
            }
            for (int u = mid - 1; u <= mid + 2; u++) {
                for (int y = F + 6; y <= F + 9; y++) set(s, u, y, v1 - 1, "minecraft:bone_block[axis=y]");
            }
            set(s, mid, F + 8, v1 - 2, "minecraft:verdant_froglight");
            set(s, mid + 1, F + 8, v1 - 2, "minecraft:verdant_froglight");
            set(s, mid, F + 6, v1 - 2, "minecraft:black_concrete");
            set(s, mid + 1, F + 6, v1 - 2, "minecraft:black_concrete");
            for (int u = u1 + 1; u <= u2 - 1; u += 3) set(s, u, F + 6, v1, "minecraft:lime_stained_glass");
            for (int u = u1; u <= u2; u += 2) set(s, u, F + 11, v1, "minecraft:polished_blackstone_brick_wall");
            set(s, u1, F + 11, v1, "minecraft:soul_lantern");
            set(s, u2, F + 11, v1, "minecraft:soul_lantern");
            // rails devant la façade, d'une porte à l'autre
            for (int u = mid - 3; u <= mid + 4; u++) set(s, u, F + 1, v1 - 2, "minecraft:rail[shape=east_west]");
            for (int dv = -1; dv >= -1; dv--) {
                set(s, mid - 3, F + 1, v1 - 1, "minecraft:rail[shape=north_south]");
                set(s, mid + 4, F + 1, v1 - 1, "minecraft:rail[shape=north_south]");
            }
        }
        int mid = (u1 + u2) / 2;
        Ride.Part ghost = r.part("fantome", mid + 1.0, F + 12.5, v1 - 1.0, Ride.Axis.Y,
                t -> new Ride.Pose(15 * Math.sin(t * 0.8), 2.5 * Math.sin(t * 0.6), 0.6 * Math.sin(t * 1.7), 0, 0));
        ghost.cube(0, 0, 0, 1.2, 1.4, 0.9, "minecraft:white_wool");
        ghost.cube(0, -0.95, 0, 1.0, 0.6, 0.8, "minecraft:white_wool");
        ghost.cube(0.25, 0.25, -0.46, 0.2, 0.3, 0.05, "minecraft:black_concrete");
        ghost.cube(-0.25, 0.25, -0.46, 0.2, 0.3, 0.05, "minecraft:black_concrete");
        ghost.light(0, 0.7, 0, 0.3, "minecraft:verdant_froglight");
        double[][] track = {{mid - 2.5, F + 1.4, v1 + 0.5}, {mid - 2.5, F + 1.4, v1 - 1.5}, {mid + 4.5, F + 1.4, v1 - 1.5},
                {mid + 4.5, F + 1.4, v1 + 0.5}, {mid + 4.5, F + 1.4, v1 + 4.0}, {mid - 2.5, F + 1.4, v1 + 4.0}};
        Ride.Part cart = r.part("wagonnet", mid + 1.0, F + 1.4, v1 + 1.0, Ride.Axis.Y, Ride.path(track, 14, mid + 1.0, F + 1.4, v1 + 1.0));
        cart.cube(0, 0.1, 0, 1.0, 0.7, 1.3, "minecraft:purple_concrete");
        cart.seat(0, 0.45, -0.1);
        cart.cube(0, 0.55, 0.45, 0.9, 0.5, 0.2, "minecraft:bone_block");
        return r;
    }

    // ------------------------------------------------------------------ montagnes russes

    /**
     * Montagnes russes tout autour du parc, sur des poteaux : un circuit fermé (polyligne) avec bosse de lancement
     * côté est, grande descente au fond, petites bosses à l'ouest, passage au-dessus de l'entrée.
     */
    public static double[][] coasterPath() {
        List<double[]> p = new ArrayList<>();
        int n = 120;
        // rectangle arrondi : u 1.5..46.5, v 3.5..46.5, hauteur selon l'abscisse curviligne
        double u1 = 1.5, u2 = 46.5, v1 = 3.5, v2 = 46.5, rc = 4;
        double[][] corners = {{u2 - rc, v1 + rc, -90}, {u2 - rc, v2 - rc, 0}, {u1 + rc, v2 - rc, 90}, {u1 + rc, v1 + rc, 180}};
        double[] lens = {u2 - u1 - 2 * rc, v2 - v1 - 2 * rc};
        double per = 2 * (lens[0] + lens[1]) + 2 * Math.PI * rc;
        for (int i = 0; i < n; i++) {
            double d = per * i / n;
            double[] xy = rect(d, u1, v1, u2, v2, rc);
            double f = d / per;
            double h;
            if (f < 0.05) h = 9;                                       // départ (au-dessus de l'entrée, côté est)
            else if (f < 0.27) h = 9 + 15 * (f - 0.05) / 0.22;         // montée côté est
            else if (f < 0.33) h = 24;                                 // sommet (coin du fond est)
            else if (f < 0.42) h = 24 - 17 * Ride.smooth((f - 0.33) / 0.09); // grande descente le long du fond
            else if (f < 0.52) h = 7 + 6 * Math.sin(Math.PI * (f - 0.42) / 0.10);
            else if (f < 0.62) h = 7 + 9 * Math.sin(Math.PI * (f - 0.52) / 0.10);
            else if (f < 0.80) h = 7 + 3 * Math.sin(2 * Math.PI * (f - 0.62) / 0.09);
            else h = 7 + 2 * Ride.smooth((f - 0.80) / 0.20);
            p.add(new double[]{xy[0], F + 1 + h, xy[1]});
        }
        return p.toArray(new double[0][]);
    }

    /** Point d'un rectangle arrondi (sens : est vers le fond, puis ouest, puis devant). */
    static double[] rect(double d, double u1, double v1, double u2, double v2, double rc) {
        double a = v2 - v1 - 2 * rc, b = u2 - u1 - 2 * rc, q = Math.PI * rc / 2;
        // côté est (u2), de v1+rc à v2-rc
        if (d < a) return new double[]{u2, v1 + rc + d};
        d -= a;
        if (d < q) { double t = d / rc; return new double[]{u2 - rc + rc * Math.cos(t), v2 - rc + rc * Math.sin(t)}; }
        d -= q;
        if (d < b) return new double[]{u2 - rc - d, v2};
        d -= b;
        if (d < q) { double t = d / rc; return new double[]{u1 + rc - rc * Math.sin(t), v2 - rc + rc * Math.cos(t)}; }
        d -= q;
        if (d < a) return new double[]{u1, v2 - rc - d};
        d -= a;
        if (d < q) { double t = d / rc; return new double[]{u1 + rc - rc * Math.cos(t), v1 + rc - rc * Math.sin(t)}; }
        d -= q;
        if (d < b) return new double[]{u1 + rc + d, v1};
        d -= b;
        double t = d / rc;
        return new double[]{u2 - rc + rc * Math.sin(t), v1 + rc - rc * Math.cos(t)};
    }

    public static Ride montagnes(Sink s) {
        Ride r = new Ride("montagnes_russes").board(28.5, 2.5);
        double[][] pts = coasterPath();
        if (s != null) {
            int n = pts.length;
            for (int i = 0; i < n; i++) {
                double[] a = pts[i], b = pts[(i + 1) % n];
                int steps = 6;
                for (int k = 0; k < steps; k++) {
                    double f = (double) k / steps;
                    double u = a[0] + (b[0] - a[0]) * f, y = a[1] + (b[1] - a[1]) * f, v = a[2] + (b[2] - a[2]) * f;
                    int bu = (int) Math.floor(u), bv = (int) Math.floor(v), by = (int) Math.floor(y);
                    boolean half = (y - by) >= 0.5;
                    set(s, bu, by, bv, half ? B.slab("minecraft:crimson_slab", "bottom") : "minecraft:crimson_planks");
                    if (s.get(bu, by - 1, bv) == null || B.isAir(s.get(bu, by - 1, bv)))
                        set(s, bu, by - 1, bv, "minecraft:iron_bars");
                }
                // poteau fin tous les 6 points (environ 13 blocs), socle en pierre
                if (i % 6 == 0) {
                    int bu = (int) Math.floor(a[0]), bv = (int) Math.floor(a[2]);
                    for (int y = F + 2; y < (int) Math.floor(a[1]) - 1; y++) set(s, bu, y, bv, "minecraft:diorite_wall");
                    set(s, bu, F + 1, bv, "minecraft:polished_andesite");
                }
                if (i % 4 == 0) set(s, (int) Math.floor(a[0]), (int) Math.floor(a[1]) + 1, (int) Math.floor(a[2]), bulb(i));
            }
        }
        Ride.Part train = r.part("wagons", 24.0, F + 12.0, 24.0, Ride.Axis.Y, Ride.path(pts, 30, 24.0, F + 12.0, 24.0));
        String[] cols = {"minecraft:yellow_concrete", "minecraft:blue_concrete", "minecraft:yellow_concrete"};
        for (int w = 0; w < 3; w++) {
            double z = -w * 1.6;
            train.model(0, 1.15, z, 1.2, 0, "wagon", w == 1 ? 0x2F6BD8 : 0xF2C230);
            train.seat(0, 1.15, z - 0.1);
            train.blocksOnly();
            train.cube(0, 0.85, z, 1.2, 0.6, 1.4, cols[w]);
            train.cube(0, 1.25, z - 0.55, 1.2, 0.35, 0.2, "minecraft:black_concrete");
            train.light(0.5, 1.2, z + 0.6, 0.2, "minecraft:sea_lantern");
            train.always();
        }
        return r;
    }

    // ------------------------------------------------------------------ stands

    /**
     * Stand forain 7 x 5 (u0..u0+6, v0..v0+4), ouvert côté allée (v0) : comptoir, auvent rayé, enseigne lumineuse et
     * décor propre au type.
     */
    public static void stand(Sink s, int u0, int v0, String type) {
        String[] stripe = switch (type) {
            case "peche" -> new String[]{"minecraft:light_blue_wool", "minecraft:white_wool"};
            case "barbe" -> new String[]{"minecraft:pink_wool", "minecraft:white_wool"};
            case "chamboule" -> new String[]{"minecraft:lime_wool", "minecraft:yellow_wool"};
            case "tir" -> new String[]{"minecraft:red_wool", "minecraft:white_wool"};
            default -> new String[]{"minecraft:orange_wool", "minecraft:yellow_wool"};
        };
        for (int u = u0; u <= u0 + 6; u++) {
            for (int v = v0; v <= v0 + 4; v++) {
                set(s, u, F, v, "minecraft:spruce_planks");
                boolean back = v == v0 + 4, side = u == u0 || u == u0 + 6;
                for (int y = F + 1; y <= F + 4; y++) {
                    if (back || (side && v > v0)) set(s, u, y, v, y == F + 1 ? "minecraft:stripped_spruce_wood" : "minecraft:spruce_planks");
                }
                if (side && v == v0) for (int y = F + 1; y <= F + 4; y++) set(s, u, y, v, "minecraft:spruce_log[axis=y]");
                // toit en dents de scie, rayé
                set(s, u, F + 5, v, (u - u0) % 2 == 0 ? stripe[0] : stripe[1]);
            }
            // auvent rayé qui déborde sur l'allée, lanternes suspendues dessous
            set(s, u, F + 5, v0 - 1, (u - u0) % 2 == 0 ? stripe[0] : stripe[1]);
            if ((u - u0) % 3 == 1) set(s, u, F + 4, v0 - 1, "minecraft:lantern[hanging=true]");
            // comptoir
            if (u > u0 && u < u0 + 6) {
                set(s, u, F + 1, v0 + 1, "minecraft:spruce_planks");
                set(s, u, F + 2, v0 + 1, B.slab("minecraft:spruce_slab", "bottom"));
            }
        }
        // enseigne lumineuse
        for (int u = u0 + 1; u <= u0 + 5; u++) set(s, u, F + 6, v0, (u % 2 == 0) ? "minecraft:pearlescent_froglight" : "minecraft:ochre_froglight");
        set(s, u0, F + 5, v0 - 1, "minecraft:lantern[hanging=false]");
        set(s, u0 + 6, F + 5, v0 - 1, "minecraft:lantern[hanging=false]");
        switch (type) {
            case "peche" -> {
                for (int u = u0 + 2; u <= u0 + 4; u++) {
                    set(s, u, F + 1, v0 + 2, "minecraft:water");
                    set(s, u, F + 1, v0 + 3, "minecraft:water");
                }
                set(s, u0 + 2, F + 2, v0 + 2, "minecraft:yellow_carpet");
                set(s, u0 + 4, F + 2, v0 + 3, "minecraft:yellow_carpet");
                set(s, u0 + 1, F + 3, v0 + 3, "minecraft:yellow_wool");
                set(s, u0 + 5, F + 3, v0 + 3, "minecraft:pink_wool");
            }
            case "barbe" -> {
                set(s, u0 + 2, F + 3, v0 + 3, "minecraft:pink_wool");
                set(s, u0 + 3, F + 3, v0 + 3, "minecraft:white_wool");
                set(s, u0 + 4, F + 3, v0 + 3, "minecraft:pink_wool");
                set(s, u0 + 3, F + 2, v0 + 3, "minecraft:cauldron");
            }
            case "chamboule" -> {
                for (int u = u0 + 2; u <= u0 + 4; u++) set(s, u, F + 2, v0 + 3, "minecraft:copper_block");
                set(s, u0 + 3, F + 3, v0 + 3, "minecraft:copper_block");
            }
            case "tir" -> {
                for (int u = u0 + 1; u <= u0 + 5; u++) set(s, u, F + 3, v0 + 3, u % 2 == 0 ? "minecraft:target" : "minecraft:white_concrete");
                set(s, u0 + 3, F + 4, v0 + 3, "minecraft:red_concrete");
            }
            default -> {
                set(s, u0 + 3, F + 2, v0 + 3, "minecraft:smoker[facing=north]");
                set(s, u0 + 2, F + 2, v0 + 3, "minecraft:barrel[facing=up]");
            }
        }
    }
}
