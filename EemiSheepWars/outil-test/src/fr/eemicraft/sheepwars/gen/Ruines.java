package fr.eemicraft.sheepwars.gen;

import fr.eemicraft.sheepwars.gen.build.B;
import fr.eemicraft.sheepwars.gen.build.Dir;
import fr.eemicraft.sheepwars.gen.build.Palette;
import fr.eemicraft.sheepwars.gen.build.Tree;

/**
 * Ruines antiques : trois iles flottantes par camp, alignees le long de z (comme les Iles vertes). Au centre le
 * forum (places de depart) ; au nord un temple a colonnade effondre ; au sud un arc de triomphe, une statue et
 * des gradins. Un aqueduc brise court a l'arriere et relie les iles par le haut. Murets bas cote milieu.
 */
final class Ruines implements SwGen.MapDef {
    public String id() { return "ruines"; }
    public String name() { return "Ruines antiques"; }
    public int voidRel() { return -3; }
    public int magicRel() { return 24; }
    public int spawnFloor() { return 18; }
    public int gap() { return 7; }
    public long seed() { return 0xA27C1L; }
    /** Pan de toit de tuiles du temple (escaliers en pente) : un toit, pas une circulation (inaccessible). */
    public int[][] auditIgnores() { return new int[][]{{-(29 - SHIFT), 32, 21, -(20 - SHIFT - 1), 36, 27}}; }
    public String auditIgnoreWhy() { return "pan de toit du temple (pas une circulation)"; }
    /** Le camp est dessine en u puis pose 3 blocs plus pres du milieu (vide median ~19 comme les maps aimees). */
    static final int SHIFT = 3;

    Palette stone, marble, pave, dirtP;

    /** Une ile : centre (u, z), rayons, hauteur du dessus. */
    record Isl(double cu, double cz, double ru, double rz, int top) {}

    final Isl[] isl = {new Isl(23.5, 0, 12.5, 13.5, 18), new Isl(23, 29, 12, 10.5, 20), new Isl(22.5, -29, 11.5, 10.5, 16)};

    public void buildRed(Gen g) {
        g.du = SHIFT;
        long sd = seed();
        stone = Palette.of(sd + 1, "minecraft:stone", 70, "minecraft:andesite", 30).period(11).amp(0.15);
        marble = Palette.of(sd + 2, "minecraft:smooth_quartz", 60, "minecraft:calcite", 25, "minecraft:diorite", 15).period(8);
        pave = Palette.of(sd + 3, "minecraft:smooth_stone", 55, "minecraft:stone_bricks", 30, "minecraft:mossy_stone_bricks", 15).period(8).amp(0.15);
        dirtP = Palette.of(sd + 4, "minecraft:dirt", 85, "minecraft:coarse_dirt", 15).period(10).amp(0.15);
        for (Isl i : isl) island(g, i);
        bridges(g);
        forum(g);
        temple(g);
        southRuins(g);
        aqueduct(g);
        frontWalls(g);
        trees(g);
        crypt(g);
        castellum(g);
        arcLadder(g);
        underside(g);
    }

    // ---------------------------------------------------------------- crypte sous le forum (niveau souterrain)
    /**
     * Crypte : sol en 13, air 14-16, voute en 17 sous le dallage du forum (18). Deux escaliers depuis le forum
     * (u 18-19, z +-4..8), deux loggias ouvertes sur la falaise cote milieu (z +-1..2) et des meurtrieres : on y tire
     * et on y recoit les moutons. Piliers, sarcophages, autel, lanternes des ames.
     */
    void crypt(Gen g) {
        Palette fl = Palette.of(seed() + 41, "minecraft:polished_andesite", 50, "minecraft:stone_bricks", 30, "minecraft:cracked_stone_bricks", 20).period(9).amp(0.12);
        int u1 = 16, u2 = 30, z1 = -8, z2 = 8, yf = 13, yc = 17;
        for (int u = u1 - 1; u <= u2 + 1; u++)
            for (int z = z1 - 1; z <= z2 + 1; z++)
                for (int y = yf - 1; y <= yc; y++) {
                    boolean in = u >= u1 && u <= u2 && z >= z1 && z <= z2;
                    if (in && y > yf && y < yc) { g.set(u, y, z, B.AIR); continue; }
                    if (in && y == yf) { g.set(u, y, z, fl.at(-u, y, z)); continue; }
                    if (g.air(u, y, z) || (in && y == yc)) g.set(u, y, z, y == yc ? "minecraft:dirt" : stone.at(-u, y, z));
                }
        // voute : nervures en escaliers retournes le long des murs, tous les 4
        for (int z = z1; z <= z2; z++) {
            if (Math.floorMod(z, 4) != 2) continue;
            g.set(u1, yc - 1, z, Gen.stairs("stone_brick_stairs", Gen.IN, true));
            g.set(u2, yc - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
        }
        for (int u = u1 + 2; u <= u2 - 1; u += 4) {
            g.set(u, yc - 1, z1, Gen.stairs("stone_brick_stairs", Dir.NORTH, true));
            g.set(u, yc - 1, z2, Gen.stairs("stone_brick_stairs", Dir.SOUTH, true));
        }
        // piliers (fut de quartz, chapiteau en escaliers)
        for (int u : new int[]{21, 25, 29})
            for (int z : new int[]{-4, 4}) {
                g.set(u, yf + 1, z, "minecraft:chiseled_stone_bricks");
                g.set(u, yf + 2, z, B.axis("quartz_pillar", 'y'));
                g.set(u, yf + 3, z, "minecraft:chiseled_quartz_block");
                for (Dir d : Dir.values()) {
                    int[] p = Gen.step(u, z, d, 1);
                    if (g.air(p[0], yf + 3, p[1])) g.set(p[0], yf + 3, p[1], Gen.stairs("quartz_stairs", d.opposite(), true));
                }
            }
        // autel souterrain et sarcophages le long du mur du fond
        g.set(25, yf + 1, 0, "minecraft:chiseled_quartz_block");
        g.set(25, yf + 2, 0, B.state("candle", "candles", "4", "lit", "false"));
        g.set(24, yf + 1, 0, Gen.slab("smooth_quartz_slab", false));
        for (int z : new int[]{-6, -2, 2, 6}) {
            g.set(30, yf + 1, z, "minecraft:smooth_stone");
            g.set(30, yf + 1, z + (z > 0 ? 1 : -1), "minecraft:smooth_stone");
            g.set(29, yf + 1, z, Gen.slab("smooth_stone_slab", false));
            g.set(30, yf + 2, z, Gen.slab("stone_brick_slab", false));
            g.set(30, yf + 2, z + (z > 0 ? 1 : -1), Gen.stairs("stone_brick_stairs", Gen.IN, false));
        }
        g.set(30, yf + 1, 0, "minecraft:mossy_stone_bricks");
        g.set(30, yf + 2, 0, B.state("skeleton_skull", "rotation", "4"));
        for (int[] l : new int[][]{{19, 0}, {23, -2}, {23, 2}, {27, -6}, {27, 6}, {27, 0}}) g.hangTo(l[0], yc - 1, l[1], true);
        for (int[] w : new int[][]{{16, -8}, {30, 8}, {30, -8}, {16, 8}}) g.setIfAir(w[0], yc - 1, w[1], "minecraft:cobweb");
        // escaliers depuis le forum (montent vers les bords nord / sud), garde-corps autour des tremies
        g.flight(18, 4, yf + 1, 5, Dir.SOUTH, Gen.OUT, 2, "stone_brick_stairs", "minecraft:stone_bricks", yf + 1, pave.first());
        g.flight(18, -4, yf + 1, 5, Dir.NORTH, Gen.OUT, 2, "stone_brick_stairs", "minecraft:stone_bricks", yf + 1, pave.first());
        for (int s : new int[]{-1, 1}) {
            for (int k = 5; k <= 7; k++) {
                g.set(17, 19, s * k, "minecraft:stone_brick_wall");
                g.set(20, 19, s * k, k == 6 ? "minecraft:mossy_stone_brick_wall" : "minecraft:stone_brick_wall");
            }
            g.set(18, 19, s * 4, "minecraft:stone_brick_wall");
            g.set(19, 19, s * 4, "minecraft:stone_brick_wall");
            g.set(17, 18, s * 8, pave.first());
            g.set(20, 18, s * 8, pave.first());
        }
        // oculi ouverts dans le dallage : les moutons tombent aussi dans la crypte
        for (int u : new int[]{24, 28}) for (int z : new int[]{-6, 6}) g.clear(u, yc, z, u, 18, z);
        // loggias sur la falaise cote milieu (2 x 3, balcon a muret) et meurtrieres
        for (int s : new int[]{-1, 1}) loggia(g, s * 1, s * 2, yf, yc);
        for (int z : new int[]{-6, 6}) for (int u = u1 - 1; u >= 12; u--) {
            if (g.air(u, yf + 2, z) && u < u1 - 1) break;
            g.set(u, yf + 2, z, B.AIR);
        }
    }

    /** Loggia : galerie de 2 de large (z a..b) qui traverse la falaise depuis la crypte, balcon a muret au bout. */
    void loggia(Gen g, int za, int zb, int yf, int yc) {
        int z1 = Math.min(za, zb), z2 = Math.max(za, zb);
        int uEnd = 12;
        for (int u = 15; u >= uEnd; u--)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean in = z >= z1 && z <= z2;
                for (int y = yf; y <= yc; y++) {
                    if (in && y > yf && y < yc) { g.set(u, y, z, B.AIR); continue; }
                    if (y == yf || y == yc || !in) { g.set(u, y, z, y == yc ? "minecraft:stone_bricks" : (y == yf ? "minecraft:polished_andesite" : "minecraft:stone_bricks")); }
                }
                if (g.air(u, yf - 1, z)) g.set(u, yf - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            }
        // balcon (u 11) : dalle et muret
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            g.set(11, yf, z, "minecraft:stone_bricks");
            g.set(11, yf + 1, z, (z == z1 - 1 || z == z2 + 1) ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_brick_wall");
            g.set(11, yf - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            for (int y = yf + 2; y <= yc; y++) if (z == z1 - 1 || z == z2 + 1) g.set(11, y, z, B.axis("quartz_pillar", 'y'));
            if (z >= z1 && z <= z2) g.clear(11, yf + 2, z, 11, yc - 1, z);
            g.set(11, yc, z, (z == z1 - 1 || z == z2 + 1) ? "minecraft:chiseled_quartz_block" : "minecraft:smooth_quartz");
            g.setIfAir(10, yc, z, Gen.stairs("quartz_stairs", Gen.IN.opposite(), true));
        }
        for (int u = 9; u <= 10; u++) for (int z = z1; z <= z2; z++) g.clear(u, yf + 1, z, u, yc - 1, z);
        g.hangTo(13, yc - 1, z1, true);
    }

    // ---------------------------------------------------------------- castellum (chateau d'eau) sur l'aqueduc : point haut
    /** Tour 5 x 5 a cheval sur l'aqueduc (z -9..-5), traversee par le chemin de l'aqueduc, echelle vers le toit-terrasse (pieds en 32). */
    void castellum(Gen g) {
        int u1 = 32, u2 = 36, z1 = -9, z2 = -5, yb = 26, yr = 31;
        Palette w = Palette.of(seed() + 51, "minecraft:stone_bricks", 60, "minecraft:mossy_stone_bricks", 22, "minecraft:cracked_stone_bricks", 18).period(7);
        for (int u = u1; u <= u2; u++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = u == u1 || u == u2 || z == z1 || z == z2;
                g.set(u, yb, z, edge && (u == u1 || u == u2) ? w.at(-u, yb, z) : "minecraft:stone_bricks");
                for (int y = yb + 1; y < yr; y++) g.set(u, y, z, edge ? w.at(-u, y, z) : B.AIR);
                g.set(u, yr, z, "minecraft:stone_bricks");
                if (edge) g.set(u, yr + 1, z, Math.floorMod(u + z, 2) == 0 ? "minecraft:stone_brick_wall" : Gen.slab("stone_brick_slab", false));
            }
        // cordon en escaliers retournes sous le toit-terrasse, dans le plan des murs
        for (int u = u1; u <= u2; u++)
            for (int z = z1; z <= z2; z++) {
                if (!(u == u1 || u == u2 || z == z1 || z == z2)) continue;
                Dir back = u == u1 ? Gen.OUT : u == u2 ? Gen.IN : z == z1 ? Dir.SOUTH : Dir.NORTH;
                g.set(u, yr - 1, z, Gen.stairs("stone_brick_stairs", back, true));
                if ((u == u1 || u == u2) && Math.floorMod(z, 2) == 1) g.set(u, yb + 1, z, "minecraft:chiseled_stone_bricks");
            }
        // corbeaux sous les murs en surplomb (u1 et u2)
        for (int z = z1; z <= z2; z++) {
            g.set(u1, yb - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            g.setIfAir(u2, yb - 1, z, Gen.stairs("stone_brick_stairs", Gen.IN, true));
        }
        // le chemin de l'aqueduc (u 33) traverse la tour ; meurtrieres ; echelle contre le mur du fond
        for (int z : new int[]{z1, z2}) g.clear(33, yb + 1, z, 33, yb + 2, z);
        g.set(34, yb + 3, z1, B.AIR);
        g.set(34, yb + 3, z2, B.AIR);
        g.set(u1, yb + 3, -7, B.AIR);
        g.clear(35, yr, -7, 35, yr, -7);
        g.ladder(35, -7, yb + 1, yr, Gen.IN, null);
        g.hangTo(33, yb + 3, -7, true);
        g.set(u1 - 1, yb + 2, -7, B.state("TEAM_wall_banner", "facing", Gen.IN.id()));
        g.set(34, yr + 1, -7, B.state("soul_lantern", "hanging", "false"));
        // canal bouche de part et d'autre de la tour
        for (int z = z1 - 1; z <= z2 + 1; z++) g.set(34, yb, z, "minecraft:stone_bricks");
    }

    // ---------------------------------------------------------------- echelle dans la pile de l'arc (dessus praticable)
    void arcLadder(Gen g) {
        int y = 16;
        g.clear(25, y + 1, -30, 25, y + 9, -30);
        g.ladder(25, -30, y + 1, y + 9, Gen.IN, null);
        for (int u = 24; u <= 27; u++) for (int z = -31; z <= -28; z++) if (!(u == 25 && z == -30)) g.set(u, y + 9, z, "minecraft:stone_bricks");
        for (int u = 17; u <= 27; u++) {
            if (Math.floorMod(u, 3) == 0) { g.setIfAir(u, y + 10, -31, "minecraft:mossy_stone_brick_wall"); g.setIfAir(u, y + 10, -28, "minecraft:stone_brick_wall"); }
        }
    }

    // ---------------------------------------------------------------- dessous des iles : stalactites, lierre, racines
    void underside(Gen g) {
        for (int u = gap() + SHIFT + 1; u <= 40; u++)
            for (int z = -42; z <= 42; z++) {
                int bot = -1;
                for (int y = 4; y <= 22; y++) if (!g.air(u, y, z)) { bot = y; break; }
                if (bot < 2 || !B.isSolid(g.get(u, bot, z))) continue;
                if (!g.air(u, bot - 1, z)) continue;
                double v = g.n.noise2(u / 3.0 + 7, z / 3.0);
                double hh = g.h(u, bot, z);
                if (v > 0.45 && hh < 0.5) {
                    g.set(u, bot - 1, z, B.state("pointed_dripstone", "vertical_direction", "down", "thickness", hh < 0.2 && g.air(u, bot - 2, z) ? "frustum" : "tip"));
                    if (hh < 0.2 && g.air(u, bot - 2, z)) g.set(u, bot - 2, z, B.state("pointed_dripstone", "vertical_direction", "down", "thickness", "tip"));
                } else if (v < -0.4 && hh < 0.4) g.set(u, bot - 1, z, "minecraft:hanging_roots");
                else if (v > 0.1 && v < 0.3 && hh < 0.3) g.set(u, bot - 1, z, B.state("glow_lichen", "up", "true"));
            }
    }

    // ---------------------------------------------------------------- iles
    /** Distance normalisee au bord (< 1 dedans), bord bruite. */
    double inside(Gen g, Isl i, int u, int z) {
        double a = (u - i.cu) / i.ru, b = (z - i.cz) / i.rz;
        double r = Math.sqrt(a * a + b * b);
        return r + 0.10 * g.n.noise2(u / 5.0, z / 5.0) + 0.05 * g.n.noise2(u / 2.5 + 40, z / 2.5);
    }

    void island(Gen g, Isl i) {
        for (int u = (int) (i.cu - i.ru - 2); u <= i.cu + i.ru + 2; u++)
            for (int z = (int) (i.cz - i.rz - 2); z <= i.cz + i.rz + 2; z++) {
                if (u - SHIFT < gap()) continue;
                double r = inside(g, i, u, z);
                if (r >= 1) continue;
                double dIn = (1 - r) * Math.min(i.ru, i.rz);           // distance au bord (blocs)
                int top = i.top + (r > 0.55 ? (int) Math.round(1.2 * g.n.noise2(u / 6.0 + 9, z / 6.0)) : 0);
                if (r > 0.86) top -= 1;
                int depth = (int) Math.round(2 + dIn * 1.05 + 2 * g.n.noise2(u / 3.0, z / 3.0 + 70));
                if (g.h(u, 1, z) < 0.05 && dIn > 2) depth += 3 + (int) (g.h(u, 2, z) * 5);   // stalactites
                for (int y = top - depth; y <= top; y++) {
                    if (y < 0) continue;
                    String st;
                    if (y == top) st = g.n.noise2(u / 4.0, z / 4.0 + 30) > 0.5 ? "minecraft:moss_block" : "minecraft:grass_block";
                    else if (y >= top - 2) st = dirtP.at(-u, y, z);
                    else st = stone.at(-u, y, z);
                    g.set(u, y, z, st);
                }
                // racines et lierre sous les bords
                int bot = top - depth;
                if (bot - 1 >= 0 && g.h(u, 7, z) < 0.07) g.set(u, bot - 1, z, "minecraft:hanging_roots");
            }
    }

    int topOf(Gen g, int u, int z) {
        for (int y = 45; y >= 0; y--) {
            String n = B.name(g.get(u, y, z));
            if (n.equals("grass_block") || n.equals("moss_block") || n.equals("smooth_stone") || n.contains("brick") || n.contains("andesite")
                    || n.equals("smooth_quartz") || n.equals("calcite") || n.equals("diorite") || n.equals("coarse_dirt")
                    || n.contains("cobble") || n.equals("dirt_path") || n.equals("mud_bricks")) return y;
        }
        return -1;
    }

    // ---------------------------------------------------------------- ponts brises entre les iles (u 20-22)
    void bridges(Gen g) {
        for (int sgn : new int[]{-1, 1}) {
            int yB = sgn > 0 ? 20 : 16;
            int prev = 18;
            int[] ys = new int[26];
            for (int k = 8; k <= 25; k++) ys[k] = (int) Math.round(18 + (yB - 18) * Math.max(0, Math.min(1, (k - 12) / 8.0)));
            for (int k = 10; k <= 23; k++) {
                int z = sgn * k;
                int y = ys[k];
                boolean flat = ys[k - 2] == y && ys[k - 1] == y && ys[k + 1] == y;   // ni marche ni arrivee de marche
                boolean gapHere = k == 16;                        // une dalle tombee : saut d'un bloc
                for (int u = 20; u <= 22; u++) {
                    if (!g.air(u, y, z) || !g.air(u, y - 1, z)) continue;   // deja de la terre ici
                    if (gapHere) continue;
                    String deck;
                    if (y != prev) deck = Gen.stairs("stone_brick_stairs", (y > prev) == (sgn > 0) ? Dir.SOUTH : Dir.NORTH, false);
                    else deck = (u == 21) ? "minecraft:stone_bricks" : (g.h(u, y, z) < 0.3 ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks");
                    if (y != prev) g.set(u, y - 1, z, "minecraft:stone_bricks");
                    g.set(u, y, z, deck);
                    if (u != 21) {
                        g.set(u, y - 1, z, Gen.stairs("stone_brick_stairs", u == 20 ? Dir.EAST : Dir.WEST, true));
                        if (flat && g.h(u, 4, z) < 0.6) g.setIfAir(u, y + 1, z, g.h(u, 5, z) < 0.5 ? "minecraft:stone_brick_wall" : "minecraft:mossy_stone_brick_wall");
                    }
                }
                prev = y;
            }
        }
    }

    // ---------------------------------------------------------------- forum (places de depart)
    void forum(Gen g) {
        int y = 18;
        for (int u = 15; u <= 31; u++)
            for (int z = -9; z <= 9; z++) {
                double r = Math.max(Math.abs(u - 23) / 8.5, Math.abs(z) / 9.5);
                if (r > 1) continue;
                boolean crack = r > 0.8 && g.n.noise2(u / 3.0, z / 3.0) > 0.35;   // dallage qui s'effrite au bord
                if (crack) continue;
                g.set(u, y, z, pave);
                for (int yy = y + 1; yy <= y + 5; yy++) g.clear(u, yy, z, u, yy, z);
            }
        // bordure en marches de marbre (au nord et au sud de la place)
        for (int u = 17; u <= 29; u++) {
            g.set(u, y + 1, 10, Gen.stairs("quartz_stairs", Dir.SOUTH, false));
            g.set(u, y + 1, -10, Gen.stairs("quartz_stairs", Dir.NORTH, false));
        }
        // mosaique de l'equipe autour de l'autel (au fond de la place, hors des cages)
        for (int z = -2; z <= 2; z++) for (int u = 28; u <= 30; u++) g.set(u, y, z, (u == 29 && Math.abs(z) <= 1) ? B.state("TEAM_glazed_terracotta", "facing", "east") : "minecraft:TEAM_terracotta");
        // autel et braseros
        g.set(29, y + 1, 0, "minecraft:chiseled_quartz_block");
        g.set(29, y + 2, 0, Gen.slab("smooth_quartz_slab", false));
        g.set(29, y + 1, -2, "minecraft:andesite_wall");
        g.set(29, y + 2, -2, B.state("campfire", "lit", "false"));
        g.set(29, y + 1, 2, "minecraft:andesite_wall");
        g.set(29, y + 2, 2, B.state("campfire", "lit", "false"));
        // deux colonnes votives a l'avant de la place, avec banniere d'equipe
        for (int z : new int[]{-7, 7}) {
            column(g, 16, y + 1, z, 6, true);
            g.set(17, y + 4, z, B.state("TEAM_wall_banner", "facing", Gen.OUT.id()));
        }
    }

    /** Colonne : base, fut cannele, chapiteau ; h = hauteur totale ; broken = cassee net. */
    void column(Gen g, int u, int y, int z, int h, boolean full) {
        g.set(u, y, z, "minecraft:smooth_quartz");
        for (int k = 1; k < h - 1; k++) g.set(u, y + k, z, B.axis("quartz_pillar", 'y'));
        g.set(u, y + h - 1, z, full ? "minecraft:chiseled_quartz_block" : B.axis("quartz_pillar", 'y'));
    }

    // ---------------------------------------------------------------- temple a colonnade effondre (ile nord)
    void temple(Gen g) {
        int u1 = 14, u2 = 29, z1 = 22, z2 = 36, y0 = 20;
        // stylobate a 3 degres
        for (int k = 0; k < 3; k++)
            for (int u = u1 - 2 + k; u <= u2 + 2 - k; u++)
                for (int z = z1 - 2 + k; z <= z2 + 2 - k; z++) {
                    boolean edge = u == u1 - 2 + k || u == u2 + 2 - k || z == z1 - 2 + k || z == z2 + 2 - k;
                    if (edge && g.n.noise2(u / 2.5, z / 2.5 + k * 10) > 0.55) continue;   // marches ebrechees
                    g.set(u, y0 + k, z, edge ? marble.at(-u, y0 + k, z) : marble.at(-u, y0 + k, z));
                    for (int yy = y0 + k + 1; yy <= y0 + 3; yy++) if (!(k < 2 && yy <= y0 + 2)) g.clear(u, yy, z, u, yy, z);
                }
        int fl = y0 + 2;   // dessus du stylobate (pieds en fl+1)
        // dallage de la cella (mosaique d'equipe au centre)
        for (int u = u1 + 3; u <= u2 - 3; u++)
            for (int z = z1 + 3; z <= z2 - 3; z++) {
                boolean b = u == u1 + 3 || u == u2 - 3 || z == z1 + 3 || z == z2 - 3;
                boolean c = Math.abs(u - (u1 + u2) / 2) <= 1 && Math.abs(z - (z1 + z2) / 2) <= 2;
                g.set(u, fl, z, c ? B.state("TEAM_glazed_terracotta", "facing", Math.floorMod(u + z, 2) == 0 ? "north" : "east")
                        : b ? "minecraft:TEAM_terracotta" : "minecraft:polished_diorite");
            }
        // peristyle : colonnes tous les 3, hauteur 8 ; certaines cassees (decision par colonne)
        int H = 8;
        for (int u = u1; u <= u2; u += 3)
            for (int z = z1; z <= z2; z += 2) {
                boolean per = u == u1 || u == u2 || z == z1 || z == z2;
                if (!per) continue;
                if ((z - z1) % 2 != 0) continue;
                if ((u == u1 || u == u2) && (z - z1) % 4 != 0 && z != z2) continue;
                double hh = g.h(u, 0, z);
                int h = hh < 0.45 ? H : hh < 0.7 ? 3 + (int) (hh * 6) : 1 + (int) (hh * 2);
                column(g, u, fl + 1, z, Math.min(H, h), h >= H);
                // tambour tombe a cote d'une colonne cassee
                if (h < 5 && hh > 0.85) {
                    Dir d = z <= (z1 + z2) / 2 ? Dir.NORTH : Dir.SOUTH;
                    for (int k = 1; k <= 3; k++) g.setIfAir(u, fl + 1, z + d.dz * (k + 1), B.axis("quartz_pillar", 'z'));
                }
            }
        // architrave et fronton conserves sur l'angle sud-ouest (u2, z1) : poutre sur les colonnes intactes
        int ay = fl + 1 + H;
        for (int z = z1; z <= z1 + 8; z++) {
            g.set(u2, ay, z, "minecraft:smooth_quartz");
            g.set(u2 + 1, ay, z, Gen.stairs("quartz_stairs", Gen.IN, true));
            g.set(u2, ay + 1, z, Gen.slab("smooth_quartz_slab", false));
        }
        for (int u = u2 - 9; u <= u2; u++) {
            g.set(u, ay, z1, "minecraft:smooth_quartz");
            g.set(u, ay, z1 - 1, Gen.stairs("quartz_stairs", Dir.SOUTH, true));
        }
        // colonnes sous l'architrave : forcees entieres
        for (int z = z1; z <= z1 + 8; z += 4) column(g, u2, fl + 1, z, H, true);
        for (int u = u2 - 9; u <= u2; u += 3) column(g, u, fl + 1, z1, H, true);
        // morceau de toit de tuiles (equipe) sur l'angle
        for (int k = 0; k < 4; k++)
            for (int u = u2 - 8; u <= u2; u++) {
                g.set(u, ay + 1 + k, z1 + k, Gen.stairs("brick_stairs", Dir.SOUTH, false));
                if (k < 3) g.set(u, ay + 1 + k, z1 + k + 1, "minecraft:TEAM_terracotta");
            }
        // fronton triangulaire sur le pignon sud (z1-1), partiel
        for (int k = 0; k < 3; k++) for (int u = u2 - 7 + k; u <= u2 - k; u++) g.set(u, ay + 1 + k, z1 - 1, marble.at(-u, ay + 1 + k, z1 - 1));
        // murs de la cella, partiels (hauteurs variables, jamais fermes)
        for (int u = u1 + 4; u <= u2 - 4; u++) {
            int hw = 1 + (int) Math.round(3 * (0.5 + 0.5 * g.n.noise2(u / 3.0, 3)));
            for (int k = 1; k <= hw; k++) g.set(u, fl + k, z2 - 4, k == 1 ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone");
        }
        for (int z = z1 + 6; z <= z2 - 4; z++) {
            int hw = (int) Math.round(3 * (0.5 + 0.5 * g.n.noise2(9, z / 3.0)));
            for (int k = 1; k <= hw; k++) g.set(u2 - 4, fl + k, z, k == 1 ? "minecraft:cut_sandstone" : "minecraft:smooth_sandstone");
        }
        // statue renversee (tete et bras) et lierre
        g.set(u1 + 6, fl + 1, z1 + 6, "minecraft:chiseled_quartz_block");
        g.set(u1 + 7, fl + 1, z1 + 6, Gen.stairs("quartz_stairs", Dir.WEST, false));
        // vigne sur les murs
        for (int u = u1 + 4; u <= u2 - 4; u += 2) if (!g.air(u, fl + 2, z2 - 4)) g.setIfAir(u, fl + 2, z2 - 3, B.state("vine", "north", "true"));
        // escalier d'acces cote forum (au sud du stylobate, z1-3 -> fl)
        // braseros
        g.set(u1 + 2, fl + 1, z1 + 2, "minecraft:andesite_wall");
        g.set(u1 + 2, fl + 2, z1 + 2, B.state("soul_lantern", "hanging", "false"));
    }

    // ---------------------------------------------------------------- ile sud : arc, statue, gradins
    void southRuins(Gen g) {
        int y = 16;
        // dallage de la voie
        for (int z = -38; z <= -21; z++)
            for (int u = 19; u <= 25; u++) {
                if (g.n.noise2(u / 2.5, z / 2.5 + 50) > 0.45) continue;
                if (topOf(g, u, z) == y) g.set(u, y, z, pave);
            }
        // arc de triomphe (ouverture le long de z), u 17..27, z -31..-28
        int au1 = 17, au2 = 27, az1 = -31, az2 = -28, ah = 9;
        Palette arcP = Palette.of(seed() + 9, "minecraft:stone_bricks", 55, "minecraft:cracked_stone_bricks", 25, "minecraft:mossy_stone_bricks", 20).period(8);
        for (int u = au1; u <= au2; u++)
            for (int z = az1; z <= az2; z++)
                for (int yy = y + 1; yy <= y + ah; yy++) {
                    double du = Math.abs(u - 22);
                    boolean opening = du <= 2.5 && (yy <= y + 5 || (yy == y + 6 && du <= 1.5));
                    boolean side = (du >= 4 && du <= 3.9 + 0) ;
                    if (opening) continue;
                    if (yy == y + ah && g.h(u, yy, z) < 0.3 && u > 23) continue;      // couronnement ebreche
                    String st = (yy == y + ah - 1) ? "minecraft:chiseled_stone_bricks" : arcP.at(-u, yy, z);
                    if ((z == az1 || z == az2) && (u == au1 + 1 || u == au2 - 1) && yy <= y + 6) st = B.axis("quartz_pillar", 'y');
                    g.set(u, yy, z, st);
                }
        // corniche
        for (int u = au1 - 1; u <= au2 + 1; u++) {
            g.setIfAir(u, y + ah - 2, az1 - 1, Gen.stairs("stone_brick_stairs", Dir.SOUTH, true));
            g.setIfAir(u, y + ah - 2, az2 + 1, Gen.stairs("stone_brick_stairs", Dir.NORTH, true));
        }
        g.set(22, y + 7, az1 - 1, B.state("TEAM_wall_banner", "facing", "north"));
        // statue sur socle (au sud de l'arc) : figure stylisee en quartz
        int su = 22, sz = -36;
        g.fill(su - 1, y + 1, sz - 1, su + 1, y + 1, sz + 1, "minecraft:polished_andesite");
        g.set(su, y + 2, sz, "minecraft:chiseled_stone_bricks");
        g.set(su, y + 3, sz, "minecraft:smooth_quartz");
        g.set(su, y + 4, sz, "minecraft:smooth_quartz");
        g.set(su, y + 5, sz, "minecraft:chiseled_quartz_block");
        g.set(su - 1, y + 4, sz, Gen.stairs("quartz_stairs", Dir.EAST, true));
        g.set(su + 1, y + 4, sz, Gen.stairs("quartz_stairs", Dir.WEST, true));
        g.set(su + 1, y + 5, sz, "minecraft:quartz_pillar");
        g.set(su, y + 6, sz, "minecraft:skeleton_skull");
        // gradins en demi-cercle au fond (u 28..33) tournes vers l'arc
        for (int z = -36; z <= -22; z++) {
            int kmax = -1;
            for (int k = 0; k < 3; k++) {
                if (topOf(g, 29 + k, z) < 0 || Math.abs(z + 29) > 7 - k) break;
                kmax = k;
            }
            for (int k = 0; k <= kmax; k++) {
                int u = 29 + k;
                for (int yy = y + 1; yy <= y + k; yy++) g.set(u, yy, z, "minecraft:stone_bricks");
                g.set(u, y + 1 + k, z, Gen.stairs("stone_brick_stairs", Gen.OUT, false));
            }
            // dossier plein derriere le dernier gradin (la volee arrive sur un sol)
            if (kmax >= 0) for (int yy = y + 1; yy <= y + kmax + 1; yy++)
                g.set(30 + kmax, yy, z, yy == y + kmax + 1 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
        }
        // colonne isolee et chapiteau tombe
        column(g, 18, y + 1, -24, 5, false);
        g.setIfAir(19, y + 1, -24, "minecraft:chiseled_quartz_block");
    }

    // ---------------------------------------------------------------- aqueduc brise (arriere, u 33-34)
    void aqueduct(Gen g) {
        int ua = 33, top = 26;
        Palette aq = Palette.of(seed() + 11, "minecraft:stone_bricks", 50, "minecraft:mossy_stone_bricks", 20,
                "minecraft:cracked_stone_bricks", 15, "minecraft:bricks", 15).period(8);
        for (int z = -33; z <= 36; z++) {
            if (z == 15 || z == 16) continue;                              // breche jouable (2 blocs)
            int pz = Math.floorMod(z + 1, 5);
            int ground = Math.max(topOf(g, ua, z), topOf(g, ua + 1, z));
            for (int u = ua; u <= ua + 2; u++) {
                int bottom;
                if (pz == 0) bottom = ground >= 0 ? ground : top - 6;        // pile (suspendue au-dessus du vide)
                else bottom = (pz == 1 || pz == 4) ? top - 4 : top - 3;      // intrados de l'arc
                if (u == ua + 2 && pz != 0) bottom = top - 1;
                for (int yy = bottom; yy <= top; yy++) g.set(u, yy, z, aq.at(-u, yy, z));
                if (pz != 0 && u <= ua + 1) g.set(u, bottom, z, Gen.stairs("stone_brick_stairs", pz <= 2 ? Dir.NORTH : Dir.SOUTH, true));
            }
            // pilastres des piles en saillie sur la face arriere (cassent le plan du mur) + corniche
            if (pz == 0 && ground >= 0) {   // pilastre seulement sur une pile qui touche l'ile
                int bottom = ground >= 0 ? ground + 1 : top - 6;
                for (int yy = bottom; yy <= top - 2; yy++) g.set(ua + 3, yy, z, yy == top - 2 ? Gen.stairs("stone_brick_stairs", Dir.EAST, true) : "minecraft:chiseled_stone_bricks");
            } else {
                g.set(ua + 3, top - 1, z, Gen.stairs("stone_brick_stairs", Dir.EAST, true));
            }
            // corniche sous le chemin (face cote forum) et lanterne sous les arches hautes
            g.set(ua, top - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            if (pz != 0) g.set(ua + 2, top - 1, z, Gen.stairs("stone_brick_stairs", Gen.IN, true));   // corniche de la face arriere
            if (pz == 2 && ground >= 0 && ground <= top - 8) g.hangTo(ua, top - 4, z, false);
            // dessus : chemin (u 33, on marche en top+1), canal d'eau enfonce (u 34), parapet (u 35)
            g.set(ua, top, z, "minecraft:stone_bricks");
            g.set(ua + 1, top - 1, z, "minecraft:stone_bricks");
            g.set(ua + 1, top, z, B.WATER);
            g.set(ua + 2, top, z, "minecraft:stone_bricks");
            g.set(ua + 2, top + 1, z, Math.floorMod(z, 3) == 0 ? "minecraft:stone_brick_wall" : Gen.slab("stone_brick_slab", false));
            if (Math.floorMod(z, 4) == 0 && !g.air(ua, top - 2, z)) g.setIfAir(ua - 1, top - 2, z, B.state("vine", "west", "true"));
        }
        // bouchons du canal (extremites et bords de la breche)
        for (int z : new int[]{-33, 14, 17, 36}) g.set(ua + 1, top, z, "minecraft:stone_bricks");        // escaliers d'acces a l'aqueduc : depuis le forum (z -12) et depuis le temple (z 20)
        stairsUp(g, ua - 1, 4, 18, top, Dir.NORTH);
        stairsUp(g, ua - 1, 20, 20, top, Dir.SOUTH);
    }

    /** Escalier qui monte le long de z (vers d) contre l'aqueduc, en u, de y0 a top. */
    void stairsUp(Gen g, int u, int z0, int y0, int top, Dir d) {
        int n = top - y0;
        for (int k = 0; k < n; k++) {
            int z = z0 + d.dz * k;
            g.set(u, y0 + 1 + k, z, Gen.stairs("stone_brick_stairs", d, false));
            for (int yy = y0 + 1; yy <= y0 + k; yy++) g.set(u, yy, z, "minecraft:stone_bricks");
            if (y0 + k >= y0 + 1) g.set(u, y0 + k, z, "minecraft:stone_bricks");
            for (int yy = y0 + 2 + k; yy <= y0 + 4 + k; yy++) g.clear(u, yy, z, u, yy, z);
            g.set(u - 1, y0 + 2 + k, z, k % 2 == 0 ? "minecraft:stone_brick_wall" : Gen.slab("stone_brick_slab", false));
        }
        // palier : rejoint le dessus de l'aqueduc
        int zl = z0 + d.dz * n;
        g.set(u, top, zl, Gen.slab("stone_brick_slab", true));
    }

    // ---------------------------------------------------------------- murets bas cote milieu (couverts)
    void frontWalls(Gen g) {
        for (Isl i : isl) {
            for (int z = (int) (i.cz - i.rz); z <= i.cz + i.rz; z++) {
                // premier bloc de terre depuis le milieu
                int u0 = -1;
                for (int u = gap(); u < i.cu; u++) if (topOf(g, u, z) >= 0) { u0 = u; break; }
                if (u0 < 0) continue;
                int uu = u0 + 1;
                int y = topOf(g, uu, z);
                if (y < 0) continue;
                if (uu >= 18 && uu <= 24 && Math.abs(z) >= 9 && Math.abs(z) <= 25) continue;   // debouche des ponts
                double v = g.n.noise2(3.3, z / 4.0 + i.cz);
                if (v < -0.15) continue;              // ouvertures
                int h = v > 0.4 ? 2 : 1;
                for (int k = 1; k <= h; k++) g.setIfAir(uu, y + k, z, k == h && h == 2 ? Gen.slab("stone_brick_slab", false) : (g.h(uu, k, z) < 0.5 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks"));
            }
        }
    }

    // ---------------------------------------------------------------- arbres
    void trees(Gen g) {
        int[][] cyp = {{31, -8, 8}, {31, 9, 7}, {33, -21, 9}, {32, 40, 6}, {20, 39, 6}};
        for (int[] c : cyp) {
            int y = topOf(g, c[0], c[1]);
            if (y > 0 && g.air(c[0], y + 1, c[1])) g.cypress(c[0], y + 1, c[1], c[2]);
        }
        int[][] olive = {{27, -38, 5}, {17, 19, 5}};
        for (int[] o : olive) {
            int y = topOf(g, o[0], o[1]);
            if (y > 0) new Tree("minecraft:oak_log", "minecraft:azalea_leaves", "minecraft:oak_leaves").height(o[2]).crown(2.6)
                    .place(g.s, -(o[0] - SHIFT), y + 1, o[1], seed() + o[0] * 7L + o[1]);
        }
    }

    // ---------------------------------------------------------------- decor propre a chaque camp
    public void decor(Gen g, int side) {
        for (int z = -42; z <= 42; z++)
            for (int u = gap() + 1; u <= 38; u++) {
                if (u >= 18 && u <= 26 && Math.abs(z) <= 5) continue;   // places de depart
                int y = topOf(g, u, z);
                if (y < 0) continue;
                String t = B.name(g.get(u, y, z));
                double v = g.n.noise2(u / 4.0, z / 4.0), hh = g.h(u, y, z);
                if (t.equals("grass_block") || t.equals("moss_block")) {
                    if (v > 0.2 && hh < 0.5) g.plant(u, y + 1, z, hh < 0.15 ? "minecraft:fern" : "minecraft:short_grass");
                    else if (v < -0.45 && hh < 0.25) g.plant(u, y + 1, z, hh < 0.08 ? "minecraft:allium" : hh < 0.16 ? "minecraft:oxeye_daisy" : "minecraft:cornflower");
                    else if (hh > 0.985) g.plant(u, y + 1, z, "minecraft:azalea");
                } else if ((t.contains("brick") || t.equals("smooth_stone") || t.contains("andesite") || t.equals("smooth_quartz")
                        || t.equals("polished_diorite")) && B.isSolid(g.get(u, y, z)) && g.air(u, y + 1, z)
                        && (hh < 0.04 || g.n.noise2(u / 4.5 + 90, z / 4.5) > 0.25)) {
                    g.set(u, y + 1, z, "minecraft:moss_carpet");   // mousse en plaques sur les dallages
                }
                // eboulis en grappes (pierres tombees des ruines)
                if (g.n.noise2(u / 6.0 + 50, z / 6.0) > 0.42 && hh > 0.6) {
                    g.debris(u, y + 1, z, new String[]{"minecraft:stone_brick_slab", "minecraft:stone_brick_stairs", "minecraft:mossy_stone_brick_slab",
                            "minecraft:cobblestone_slab", "minecraft:quartz_slab", "minecraft:mossy_cobblestone_stairs", "minecraft:stone_button"});
                }
            }
    }
}
