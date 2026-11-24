package fr.eemicraft.sheepwars.gen;

import fr.eemicraft.sheepwars.gen.build.B;
import fr.eemicraft.sheepwars.gen.build.Dir;
import fr.eemicraft.sheepwars.gen.build.Palette;

/**
 * Base spatiale : un plateau d'asteroide par camp (regolithe gris clair, crateres, rochers), une base lunaire :
 * dome de commandement au fond, deux modules d'habitation cylindriques sur pieds, champs de panneaux solaires,
 * antenne parabolique, mat a feux, aire d'atterrissage avec sa navette (couleur de l'equipe), depot de
 * conteneurs. Dessous, roche sombre et geodes d'amethyste. Le vide (l'espace) separe les deux camps.
 */
final class Spatiale implements SwGen.MapDef {
    public String id() { return "spatiale"; }
    public String name() { return "Base spatiale"; }
    public int voidRel() { return -3; }
    public int magicRel() { return 24; }
    public int spawnFloor() { return 18; }
    public int gap() { return 7; }
    public long seed() { return 0x5BACEL; }
    /** Le camp est dessine en u puis pose 3 blocs plus pres du milieu (vide median ~19 comme les maps aimees). */
    static final int SHIFT = 3;

    static final int G = 18;
    Palette rego, under, hull;

    public void buildRed(Gen g) {
        g.du = SHIFT;
        long sd = seed();
        // regolithe : roches claires naturelles en grappes (calcite, andesite, diorite), plaques de tuf
        rego = Palette.of(sd + 1, "minecraft:calcite", 45, "minecraft:diorite", 20, "minecraft:andesite", 25, "minecraft:tuff", 10).period(11).amp(0.15);
        under = Palette.of(sd + 2, "minecraft:tuff", 40, "minecraft:deepslate", 25, "minecraft:smooth_basalt", 20, "minecraft:blackstone", 15).period(6);
        hull = Palette.of(sd + 3, "minecraft:white_concrete", 70, "minecraft:smooth_quartz", 30).period(8);
        asteroid(g);
        craters(g);
        dome(g, 35, 0);
        module(g, 27, 8, 19);
        module(g, 27, -19, -8);
        solar(g, 32, 9, 19);
        solar(g, 32, -19, -9);
        dish(g, 24, -35);
        lightMast(g, 25, 37);
        pad(g, 18, 31);
        depot(g);
        gantries(g);
        catwalks(g);
        frontCover(g);
        boulders(g);
        bunker(g);
        skywalk(g);
        tower(g);
        trims(g);
    }

    // ---------------------------------------------------------------- niveau souterrain : salle des machines
    /**
     * Salle des machines sous la base : sol en 13, air 14-16, plafond (regolithe) en 17-18. Acces : deux escaliers
     * depuis le plateau (u 17-18, z +-4..8), echelle de service jusqu'au dome (u 32, z 0). Deux sas-balcons ouverts sur
     * l'espace cote milieu (z +-1..2) et des hublots ouverts : on y tire et on y recoit les moutons.
     */
    void bunker(Gen g) {
        int u1 = 16, u2 = 32, z1 = -9, z2 = 9, yf = 13, yc = 17;
        Palette fl = Palette.of(seed() + 61, "minecraft:polished_deepslate", 55, "minecraft:deepslate_tiles", 30, "minecraft:cracked_deepslate_tiles", 15).period(8);
        for (int u = u1 - 1; u <= u2 + 1; u++)
            for (int z = z1 - 1; z <= z2 + 1; z++)
                for (int y = yf - 1; y <= yc; y++) {
                    boolean in = u >= u1 && u <= u2 && z >= z1 && z <= z2;
                    if (in && y > yf && y < yc) { g.set(u, y, z, B.AIR); continue; }
                    if (in && y == yf) { g.set(u, y, z, fl.at(-u, y, z)); continue; }
                    if (g.air(u, y, z) || (in && y == yc)) g.set(u, y, z, under.at(-u, y, z));
                }
        // murs : panneaux blancs a nervures (3 plans : nervure en saillie = escaliers retournes), plafond lumineux
        for (int z = z1; z <= z2; z++) {
            boolean rib = Math.floorMod(z, 3) == 0;
            g.set(u2 + 1, yf + 1, z, rib ? "minecraft:iron_block" : "minecraft:white_concrete");
            g.set(u2 + 1, yf + 2, z, rib ? "minecraft:iron_block" : (Math.abs(z) <= 6 ? "minecraft:black_stained_glass" : "minecraft:white_concrete"));
            g.set(u2 + 1, yf + 3, z, rib ? "minecraft:iron_block" : "minecraft:white_concrete");
            if (rib) {
                g.set(u1, yc - 1, z, Gen.stairs("polished_deepslate_stairs", Gen.IN, true));
                g.set(u2, yc - 1, z, Gen.stairs("polished_deepslate_stairs", Gen.OUT, true));
            }
            if (Math.floorMod(z, 4) == 0) for (int u = u1 + 2; u <= u2 - 2; u += 4) g.set(u, yc, z, "minecraft:sea_lantern");
        }
        // pupitres face aux ecrans du mur du fond
        for (int z = -6; z <= 6; z++) {
            if (Math.floorMod(z, 3) == 0) continue;
            g.set(u2, yf + 1, z, "minecraft:polished_deepslate");
            g.set(u2, yf + 2, z, Math.floorMod(z, 3) == 1 ? B.state("lever", "face", "floor", "facing", Gen.IN.id())
                    : B.state("polished_blackstone_button", "face", "floor", "facing", Gen.IN.id()));
        }
        // baies de serveurs (piliers) et coeur du reacteur
        for (int u : new int[]{20, 24, 28})
            for (int z : new int[]{-5, 5}) {
                g.set(u, yf + 1, z, "minecraft:iron_block");
                g.set(u, yf + 2, z, "minecraft:polished_deepslate");
                g.set(u, yf + 3, z, "minecraft:iron_block");
                for (Dir d : Dir.values()) {
                    int[] p = Gen.step(u, z, d, 1);
                    if (g.air(p[0], yf + 3, p[1])) g.set(p[0], yf + 3, p[1], Gen.stairs("polished_deepslate_stairs", d.opposite(), true));
                    if (g.air(p[0], yf + 1, p[1])) g.set(p[0], yf + 1, p[1], B.trapdoor("iron_trapdoor", d, false, true));
                }
            }
        int rc = 24;
        g.set(rc, yf + 1, 0, "minecraft:iron_block");
        g.set(rc, yf + 2, 0, "minecraft:pearlescent_froglight");
        g.set(rc, yf + 3, 0, "minecraft:iron_block");
        for (Dir d : Dir.values()) {
            int[] p = Gen.step(rc, 0, d, 1);
            g.set(p[0], yf + 1, p[1], Gen.slab("polished_deepslate_slab", false));
            g.set(p[0], yf + 2, p[1], B.pane("iron_bars", false, false, false, false));
            g.set(p[0], yf + 3, p[1], Gen.stairs("smooth_quartz_stairs", d.opposite(), true));
        }
        // conduites au plafond (chaines horizontales accrochees aux murs nord/sud)
        for (int u : new int[]{21, 30}) for (int z = z1; z <= z2; z++) g.setIfAir(u, yc - 1, z, B.axis("iron_chain", 'z'));
        for (int[] e : new int[][]{{22, -2}, {22, 2}, {26, -2}, {26, 2}, {18, 0}})
            g.setIfAir(e[0], yc - 1, e[1], B.state("end_rod", "facing", "down"));
        // escaliers depuis le plateau (montent vers les bords nord / sud) et garde-corps a poteaux
        g.flight(17, 4, yf + 1, 5, Dir.SOUTH, Gen.OUT, 2, "smooth_quartz_stairs", "minecraft:smooth_quartz", yf + 1, rego.first());
        g.flight(17, -4, yf + 1, 5, Dir.NORTH, Gen.OUT, 2, "smooth_quartz_stairs", "minecraft:smooth_quartz", yf + 1, rego.first());
        for (int s : new int[]{-1, 1}) {
            for (int k = 5; k <= 7; k++) {
                g.set(16, G + 1, s * k, k == 6 ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
                g.set(19, G + 1, s * k, k == 6 ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
            }
            g.set(17, G + 1, s * 4, B.pane("iron_bars", false, false, false, false));
            g.set(18, G + 1, s * 4, B.pane("iron_bars", false, false, false, false));
            g.set(16, G + 2, s * 6, B.state("lantern", "hanging", "false"));
            g.set(19, G + 2, s * 6, B.state("lantern", "hanging", "false"));
        }
        // echelle de service jusqu'au dome (trappe d'acces bordee de bandes de danger)
        g.clear(32, yc, 0, 32, G + 1, 0);
        g.ladder(32, 0, yf + 1, G + 1, Gen.IN, null);
        for (int du = -1; du <= 1; du++)
            for (int dz = -1; dz <= 1; dz++)
                if (du != 0 || dz != 0) g.set(32 + du, G + 1, dz, Math.floorMod(du + dz, 2) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete");
        // puits de lumiere ouverts dans le plateau : les moutons tombent aussi dans la salle
        for (int u : new int[]{22, 28}) for (int z : new int[]{-7, 7}) g.clear(u, yc, z, u, G, z);
        // sas-balcons cote milieu et hublots ouverts
        for (int s : new int[]{-1, 1}) airlock(g, s * 1, s * 2, yf, yc);
        for (int z : new int[]{-6, 6}) for (int u = u1 - 1; u >= 12; u--) {
            if (g.air(u, yf + 2, z) && u < u1 - 1) break;
            g.set(u, yf + 2, z, B.AIR);
        }
    }

    /** Sas : galerie 2 de large a travers la roche jusqu'a un balcon a garde-corps (u 11) ouvert sur l'espace. */
    void airlock(Gen g, int za, int zb, int yf, int yc) {
        int z1 = Math.min(za, zb), z2 = Math.max(za, zb);
        for (int u = 15; u >= 12; u--)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean in = z >= z1 && z <= z2;
                for (int y = yf; y <= yc; y++) {
                    if (in && y > yf && y < yc) { g.set(u, y, z, B.AIR); continue; }
                    g.set(u, y, z, y == yf ? "minecraft:smooth_stone" : (in ? "minecraft:white_concrete" : (y == yf + 2 ? "minecraft:sea_lantern" : "minecraft:iron_block")));
                }
                if (g.air(u, yf - 1, z)) g.set(u, yf - 1, z, Gen.stairs("polished_deepslate_stairs", Gen.OUT, true));
            }
        for (int z = z1 - 1; z <= z2 + 1; z++) {
            boolean post = z == z1 - 1 || z == z2 + 1;
            g.set(11, yf, z, "minecraft:white_concrete");
            g.set(11, yf - 1, z, Gen.stairs("smooth_quartz_stairs", Gen.OUT, true));
            g.set(11, yf + 1, z, post ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
            for (int y = yf + 2; y < yc; y++) g.set(11, y, z, post ? "minecraft:iron_block" : B.AIR);
            g.set(11, yc, z, post ? "minecraft:iron_block" : "minecraft:TEAM_concrete");
            g.setIfAir(10, yc, z, Gen.slab("smooth_quartz_slab", false));
        }
        for (int u = 9; u <= 10; u++) for (int z = z1; z <= z2; z++) g.clear(u, yf + 1, z, u, yc - 1, z);
        g.setIfAir(13, yc - 1, z1, B.state("end_rod", "facing", "down"));
    }

    // ---------------------------------------------------------------- passerelle haute entre les modules (2e niveau)
    /** Passerelle en dalles hautes (pieds en 26) qui longe les dessus des deux modules (u 29-30, z -19..19). */
    void skywalk(Gen g) {
        int y = G + 7;
        for (int z = -19; z <= 19; z++) {
            g.set(29, y, z, Gen.slab("smooth_quartz_slab", true));
            g.set(30, y, z, Gen.slab(Math.floorMod(z, 6) == 0 ? "polished_blackstone_slab" : "polished_deepslate_slab", true));
            g.clear(29, y + 1, z, 30, y + 2, z);
            boolean ladder = Math.abs(Math.abs(z) - 13) <= 0;
            boolean door = Math.abs(z) <= 1;
            // longeron sous le garde-corps (u 31) ; bloc plein contre l'echelle des modules
            if (!ladder) g.set(31, y, z, Gen.slab("polished_deepslate_slab", true));
            else g.set(30, y, z, "minecraft:polished_deepslate");
            if (!ladder && !door && Math.floorMod(z, 4) != 2) g.set(31, y + 1, z, Math.floorMod(z, 4) == 0 ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
            if (Math.abs(z) <= 7 && Math.abs(z) >= 2 && Math.floorMod(z, 3) != 0) g.set(28, y + 1, z, B.pane("iron_bars", false, false, false, false));
        }
        // jambes de force (barreaux) et lampes sous la passerelle
        for (int z : new int[]{-6, 6}) for (int yy = G + 2; yy < y; yy++) g.set(30, yy, z, B.pane("iron_bars", false, false, false, false));
        for (int z : new int[]{-4, 4}) g.set(29, y - 1, z, B.state("end_rod", "facing", "down"));
    }

    // ---------------------------------------------------------------- tour de controle : point haut (pieds en 32)
    void tower(Gen g) {
        int cu = 32, y0 = G + 7, yr = G + 13;
        for (int du = -1; du <= 1; du++)
            for (int dz = -1; dz <= 1; dz++) {
                int u = cu + du, z = dz;
                boolean centre = du == 0 && dz == 0;
                g.set(u, y0, z, "minecraft:polished_deepslate");
                for (int y = y0 + 1; y < yr; y++) {
                    if (centre) { g.set(u, y, z, B.AIR); continue; }
                    String st = (y == y0 + 2) ? "minecraft:TEAM_concrete" : (y == y0 + 4 && (du == 0 || dz == 0)) ? "minecraft:light_blue_stained_glass" : "minecraft:white_concrete";
                    g.set(u, y, z, st);
                }
            }
        // porte cote passerelle, echelle contre le mur du fond
        g.clear(cu - 1, y0 + 1, 0, cu - 1, y0 + 2, 0);
        g.ladder(cu, 0, y0 + 1, yr, Gen.IN, null);
        // plate-forme d'observation 5 x 5, garde-corps partiel, antenne et radar
        for (int du = -2; du <= 2; du++)
            for (int dz = -2; dz <= 2; dz++) {
                int u = cu + du;
                if (du == 0 && dz == 0) continue;
                boolean edge = Math.abs(du) == 2 || Math.abs(dz) == 2;
                g.set(u, yr, dz, edge ? Gen.slab("smooth_quartz_slab", true) : "minecraft:white_concrete");
                if (edge && (Math.abs(du) + Math.abs(dz)) % 2 == 1) g.set(u, yr + 1, dz, B.pane("iron_bars", false, false, false, false));
                if (edge && Math.abs(du) == 2 && Math.abs(dz) == 2) g.set(u, yr + 1, dz, "minecraft:iron_block");
            }
        g.set(cu + 1, yr + 2, 1, B.state("end_rod", "facing", "up"));
        g.set(cu + 1, yr + 2, -1, B.state("lightning_rod", "facing", "up"));
        g.set(cu + 1, yr + 1, 1, "minecraft:iron_block");
        g.set(cu + 1, yr + 1, -1, "minecraft:iron_block");
        g.set(cu - 2, yr + 1, 0, B.AIR);
        g.set(cu + 2, yr + 2, 2, B.state("lantern", "hanging", "false"));
        // dessous de la plate-forme : corbeaux
        for (int dz = -2; dz <= 2; dz++) g.setIfAir(cu - 2, yr - 1, dz, Gen.stairs("smooth_quartz_stairs", Gen.OUT, true));
        g.set(cu - 2, y0 + 1, 2, B.AIR);
    }

    // ---------------------------------------------------------------- finitions (moulures, bordures, signaletique)
    void trims(Gen g) {
        // bordure de l'aire d'atterrissage : dalles et feux
        // socle du dome : anneau de dalles
        for (int du = -7; du <= 7; du++)
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(du * du + dz * dz);
                if (d > 6.6 && d <= 7.4) {
                    int u = 35 + du, z = dz;
                    int t = top(g, u, z);
                    if (u >= 30 && rim(g, u, z) < 0.97 && g.air(u, t + 1, z) && g.air(u, t + 2, z)) g.set(u, t + 1, z, Gen.slab("smooth_stone_slab", false));
                }
            }
        // modules : plinthe et corniche en escaliers aux extremites
        for (int z : new int[]{8, 19, -19, -8})
            for (int du = -1; du <= 1; du++) {
                g.setIfAir(27 + du, G + 8, z, Gen.slab("smooth_quartz_slab", false));
            }
    }

    // ---------------------------------------------------------------- plateau
    /** Trois plaques : plateau principal (base), plaque nord (aire d'atterrissage), plaque sud (depot). */
    static final double[][] PLATES = {{25.5, 0, 13.5, 21, 4}, {19, 31, 8.5, 8, 2.6}, {20, -31, 8.5, 8, 2.6}};

    double rim(Gen g, int u, int z) {
        double best = 9;
        for (double[] p : PLATES) {
            double a = Math.abs(u - p[0]) / p[2], b = Math.abs(z - p[1]) / p[3];
            double r = Math.pow(Math.pow(a, p[4]) + Math.pow(b, p[4]), 1 / p[4]);
            best = Math.min(best, r);
        }
        return best + 0.08 * g.n.noise2(u / 6.0, z / 6.0) + 0.03 * g.n.noise2(u / 2.0, z / 2.0 + 90);
    }

    int top(Gen g, int u, int z) {
        double r = rim(g, u, z);
        int t = G;
        if (u >= 30 && Math.abs(z) <= 22) t = G + 1;    // fond legerement plus haut (dome, panneaux)
        if (r > 0.88) t -= 1;
        return t;
    }

    void asteroid(Gen g) {
        for (int u = gap(); u <= 42; u++)
            for (int z = -42; z <= 42; z++) {
                double r = rim(g, u, z);
                if (r >= 1) continue;
                int t = top(g, u, z);
                double dIn = (1 - r) * (Math.abs(z) > 22 ? 8 : 12);
                int depth = (int) Math.round(2 + dIn * 1.1 + 2.5 * g.n.noise2(u / 3.5, z / 3.5 + 20));
                if (g.h(u, 3, z) < 0.04 && dIn > 3) depth += 4 + (int) (g.h(u, 4, z) * 6);
                for (int y = Math.max(0, t - depth); y <= t; y++) {
                    String st = y >= t - 1 ? rego.at(-u, y, z) : under.at(-u, y, z);
                    g.set(u, y, z, st);
                }
                // geodes d'amethyste sous l'asteroide (grappes, decision par zone de bruit)
                int bot = t - depth;
                if (bot > 1 && g.n.noise2(u / 5.0 + 3, z / 5.0) > 0.55) {
                    // geode : calcite, puis cristaux qui pendent (amas, grands et moyens bourgeons)
                    g.set(u, bot, z, g.h(u, 8, z) < 0.3 ? "minecraft:amethyst_block" : "minecraft:calcite");
                    double hc = g.h(u, 9, z);
                    if (hc < 0.75) g.set(u, bot - 1, z, B.state("amethyst_cluster", "facing", "down"));
                }
            }
    }

    void craters(Gen g) {
        int[][] cr = {{18, -14, 2}, {19, 14, 2}, {25, -27, 1}};
        for (int[] c : cr) {
            int cu = c[0], cz = c[1], R = c[2] + 1;
            for (int du = -R - 1; du <= R + 1; du++)
                for (int dz = -R - 1; dz <= R + 1; dz++) {
                    int u = cu + du, z = cz + dz;
                    double d = Math.sqrt(du * du + dz * dz);
                    if (u < gap() || rim(g, u, z) >= 0.95) continue;
                    int t = top(g, u, z);
                    if (d <= R - 0.5) {
                        int dep = (int) Math.round((R - d) * 0.8);
                        for (int y = t; y > t - dep; y--) g.set(u, y, z, B.AIR);
                        g.set(u, t - dep, z, d < 1 ? "minecraft:smooth_basalt" : "minecraft:andesite");
                    } else if (d <= R + 1.2 && g.h(u, 5, z) < 0.6) {
                        g.set(u, t + 1, z, Gen.slab(d < R + 0.5 ? "andesite_slab" : "tuff_slab", false));   // bourrelet
                    }
                }
        }
    }

    // ---------------------------------------------------------------- dome de commandement
    void dome(Gen g, int cu, int cz) {
        int base = top(g, cu, cz) + 1, R = 5;
        // dalle
        for (int du = -R - 1; du <= R + 1; du++)
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.sqrt(du * du + dz * dz);
                if (d > R + 0.6) continue;
                int u = cu + du, z = cz + dz;
                for (int y = G; y < base; y++) g.set(u, y, z, "minecraft:polished_andesite");
                g.set(u, base - 1, z, d > R + 0.3 ? "minecraft:polished_andesite" : (Math.floorMod(du + dz, 2) == 0 ? "minecraft:white_concrete" : "minecraft:light_gray_concrete"));
                for (int y = base; y <= base + R + 1; y++) g.set(u, y, z, B.AIR);
            }
        // coque : anneau bas en beton blanc + bande de l'equipe, puis verre avec nervures
        for (int du = -R; du <= R; du++)
            for (int dz = -R; dz <= R; dz++)
                for (int dy = 0; dy <= R; dy++) {
                    double d = Math.sqrt(du * du + dz * dz + dy * dy);
                    if (d > R + 0.5 || d < R - 0.6) continue;
                    int u = cu + du, z = cz + dz, y = base + dy;
                    String st;
                    if (dy == 0) st = "minecraft:white_concrete";
                    else if (dy == 1) st = "minecraft:TEAM_concrete";
                    else if (du == 0 || dz == 0 || Math.abs(du) == Math.abs(dz) || dy == R) st = "minecraft:white_concrete";
                    else st = "minecraft:light_blue_stained_glass";
                    g.set(u, y, z, st);
                }
        // sas : grande ouverture cote milieu (u = cu - R) et cote nord / sud (portiques)
        for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 3; dy++) g.set(cu - R, base + dy, cz + dz, B.AIR);
        for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 3; dy++) g.set(cu - R + 1, base + dy, cz + dz, B.AIR);
        for (int du = -1; du <= 1; du++) for (int dy = 0; dy <= 2; dy++) { g.set(cu + du, base + dy, cz + R, B.AIR); g.set(cu + du, base + dy, cz - R, B.AIR); }
        // portique du sas avant
        for (int dz : new int[]{-3, 3}) for (int dy = 0; dy <= 4; dy++) g.set(cu - R - 1, base + dy, cz + dz, "minecraft:iron_block");
        for (int dz = -3; dz <= 3; dz++) g.set(cu - R - 1, base + 4, cz + dz, dz == 0 ? "minecraft:sea_lantern" : "minecraft:smooth_quartz");
        for (int dz = -2; dz <= 2; dz++) g.set(cu - R - 2, base + 4, cz + dz, Gen.stairs("smooth_quartz_stairs", Gen.OUT, true));
        // interieur : console, ecrans, sieges, lampe centrale
        for (int dz = -2; dz <= 2; dz++) {
            g.set(cu + 3, base, cz + dz, "minecraft:polished_deepslate");
            g.set(cu + 3, base + 1, cz + dz, dz == 0 ? "minecraft:observer" : B.state("lever", "face", "floor", "facing", "west"));
            g.set(cu + 4, base + 1, cz + dz, "minecraft:black_stained_glass");
            g.set(cu + 4, base + 2, cz + dz, Math.abs(dz) == 1 ? "minecraft:sea_lantern" : "minecraft:black_stained_glass");
        }
        g.set(cu + 3, base + 1, cz, B.state("observer", "facing", "west"));
        g.set(cu + 1, base, cz - 1, Gen.stairs("quartz_stairs", Dir.WEST, false));
        g.set(cu + 1, base, cz + 1, Gen.stairs("quartz_stairs", Dir.WEST, false));
        for (int dy = R - 1; dy >= R - 2; dy--) g.set(cu, base + dy, cz, B.axis("iron_chain", 'y'));
        g.set(cu, base + R - 3, cz, "minecraft:pearlescent_froglight");
        // antenne de toit
        g.set(cu, base + R + 1, cz, "minecraft:lightning_rod");
    }

    // ---------------------------------------------------------------- module d'habitation (cylindre couche le long de z)
    void module(Gen g, int cu, int z1, int z2) {
        int cy = G + 4, R = 3;
        for (int z = z1; z <= z2; z++) {
            boolean end = z == z1 || z == z2;
            for (int du = -R; du <= R; du++)
                for (int dy = -R; dy <= R; dy++) {
                    double d = Math.sqrt(du * du + dy * dy);
                    if (d > R + 0.4) continue;
                    int u = cu + du, y = cy + dy;
                    boolean shell = d > R - 0.6;
                    if (!shell && !end) { g.set(u, y, z, "minecraft:light_gray_concrete"); continue; }   // plein : aucune piece cachee
                    String st;
                    boolean band = Math.floorMod(z - z1, 6) == 0;
                    if (end) st = (Math.abs(du) <= 1 && dy >= -1 && dy <= 1) ? "minecraft:iron_block" : "minecraft:light_gray_concrete";
                    else if (band) st = "minecraft:light_gray_concrete";
                    else if (dy == 0 && Math.abs(du) == R && Math.floorMod(z - z1, 3) == 1) st = "minecraft:light_blue_stained_glass";
                    else if (dy == -1 && Math.abs(du) == R) st = "minecraft:TEAM_concrete";
                    else st = hull.at(-u, y, z);
                    g.set(u, y, z, st);
                }
            // pieds tous les 6
            if (Math.floorMod(z - z1, 6) == 3) for (int du : new int[]{-2, 2}) {
                for (int y = G + 1; y <= cy - R; y++) g.set(cu + du, y, z, "minecraft:andesite_wall");
            }
        }
        // hublots du sas et lampes
        g.set(cu, cy, z1 - 1, B.trapdoor("iron_trapdoor", Dir.NORTH, true, true));
        g.set(cu, cy, z2 + 1, B.trapdoor("iron_trapdoor", Dir.SOUTH, true, true));
        g.set(cu, cy + R + 1, (z1 + z2) / 2, "minecraft:end_rod");
        g.set(cu, cy + R + 1, z1 + 2, "minecraft:lightning_rod");
        // echelle d'acces au dessus du module (cote fond)
        for (int y = G + 1; y <= cy + R; y++) {
            g.setIfAir(cu + R, y, (z1 + z2) / 2, "minecraft:iron_block");          // support de l'echelle
            g.set(cu + R + 1, y, (z1 + z2) / 2, B.state("ladder", "facing", Gen.OUT.id()));
        }
        g.set(cu + R, cy + R, (z1 + z2) / 2, "minecraft:white_concrete");
    }

    // ---------------------------------------------------------------- panneaux solaires (au fond)
    void solar(Gen g, int u0, int z1, int z2) {
        for (int z = z1; z <= z2; z++) {
            if (Math.floorMod(z - z1, 5) == 4) continue;            // allees
            int t = top(g, u0, z);
            for (int k = 0; k < 4; k++) {
                int u = u0 + k, y = t + 2 + k / 2;                    // incline vers le milieu
                if (rim(g, u, z) >= 0.98) continue;
                g.set(u, y, z, k % 2 == 0 ? "minecraft:daylight_detector" : Gen.slab("deepslate_tile_slab", true));
                if (k == 0 || k == 3) for (int yy = top(g, u, z) + 1; yy < y; yy++) g.set(u, yy, z, B.pane("iron_bars", false, false, false, false));
            }
        }
        // onduleur
        g.set(u0 - 1, top(g, u0 - 1, z1) + 1, z1, "minecraft:blast_furnace");
        g.set(u0 - 1, top(g, u0 - 1, z2) + 1, z2, "minecraft:smoker");
    }

    // ---------------------------------------------------------------- antenne parabolique
    void dish(Gen g, int cu, int cz) {
        int t = top(g, cu, cz);
        for (int y = t + 1; y <= t + 9; y++) {
            g.set(cu, y, cz, y % 3 == 0 ? "minecraft:iron_block" : "minecraft:polished_deepslate_wall");
        }
        for (int du = -1; du <= 1; du += 2) for (int dz = -1; dz <= 1; dz += 2) g.set(cu + du, t + 1, cz + dz, "minecraft:polished_deepslate_wall");
        // coupelle : calotte inclinee vers le ciel et le milieu
        int dy0 = t + 13;
        for (int du = -4; du <= 4; du++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = -4; dy <= 4; dy++) {
                    double x = du, y = dy, z = dz;
                    // repere tourne de 35 deg vers le milieu
                    double yr = y * 0.82 + x * 0.57, xr = x * 0.82 - y * 0.57;
                    double d = Math.sqrt(xr * xr + (yr + 5) * (yr + 5) + z * z);
                    if (d > 6.5 || d < 5.6 || yr < -3.2) continue;
                    if (cu + du < gap()) continue;
                    g.set(cu + du, dy0 + dy, cz + dz, "minecraft:white_concrete");
                }
        g.setIfAir(cu, dy0 - 3, cz, B.state("end_rod", "facing", "up"));
        
    }

    // ---------------------------------------------------------------- mat a feux
    void lightMast(Gen g, int cu, int cz) {
        int t = top(g, cu, cz);
        for (int y = t + 1; y <= t + 20; y++) g.set(cu, y, cz, y % 5 == 0 ? "minecraft:TEAM_concrete" : B.pane("iron_bars", false, false, false, false));
        g.set(cu, t + 21, cz, "minecraft:sea_lantern");
        g.set(cu, t + 22, cz, "minecraft:lightning_rod");
        for (int y = t + 6; y <= t + 18; y += 6) {
            g.set(cu + 1, y, cz, B.state("end_rod", "facing", Gen.OUT.id()));
            g.set(cu, y, cz + 1, B.state("end_rod", "facing", "south"));
            g.set(cu, y, cz - 1, B.state("end_rod", "facing", "north"));
        }
        for (int du = -1; du <= 1; du++) for (int dz = -1; dz <= 1; dz++) if (du != 0 || dz != 0) g.set(cu + du, t + 1, cz + dz, Gen.slab("smooth_stone_slab", false));
    }

    // ---------------------------------------------------------------- aire d'atterrissage + navette
    void pad(Gen g, int cu, int cz) {
        int R = 6;
        for (int du = -R; du <= R; du++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(du * du + dz * dz);
                if (d > R + 0.3) continue;
                int u = cu + du, z = cz + dz;
                if (u < gap() + 1) continue;
                String st = d > R - 0.7 ? "minecraft:TEAM_concrete" : (d > R - 1.7 ? "minecraft:gray_concrete" : "minecraft:smooth_stone");
                if (Math.abs(du) <= 2 && dz == 0 || du == 0 && Math.abs(dz) <= 2) st = "minecraft:yellow_concrete";
                g.set(u, G, z, st);
                for (int y = G + 1; y <= G + 6; y++) g.set(u, y, z, B.AIR);
            }
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int u = cu + (int) Math.round(Math.cos(ang) * (R + 1)), z = cz + (int) Math.round(Math.sin(ang) * (R + 1));
            if (u > gap() && rim(g, u, z) < 0.97) g.set(u, G + 1, z, "minecraft:verdant_froglight");
        }
        // navette couchee le long de z (nez au nord), posee sur l'aire
        int nu = cu + 1;
        for (int k = 0; k <= 9; k++) {
            int z = cz + 4 - k;
            int w = k <= 1 ? 0 : (k >= 8 ? 1 : 1);
            for (int du = -w; du <= w; du++) {
                g.set(nu + du, G + 2, z, k == 0 ? "minecraft:black_concrete" : "minecraft:white_concrete");
                if (k >= 1 && k <= 7) g.set(nu + du, G + 3, z, (k >= 1 && k <= 2) ? "minecraft:tinted_glass" : (du == 0 ? "minecraft:TEAM_concrete" : "minecraft:white_concrete"));
            }
            if (k >= 2 && k <= 8) g.set(nu, G + 1, z, k % 3 == 2 ? "minecraft:iron_block" : "minecraft:gray_concrete");
        }
        // ailes
        for (int k = 5; k <= 8; k++) {
            int z = cz + 4 - k;
            int span = 2 + (k - 5);
            for (int s = 2; s <= span; s++) {
                g.set(nu - s, G + 2, z, Gen.slab("smooth_quartz_slab", false));
                g.set(nu + s, G + 2, z, Gen.slab("smooth_quartz_slab", false));
            }
        }
        g.set(nu - 4, G + 3, cz - 4, "minecraft:TEAM_stained_glass");
        g.set(nu + 4, G + 3, cz - 4, "minecraft:TEAM_stained_glass");
        // reacteurs
        for (int du = -1; du <= 1; du += 2) {
            g.set(nu + du, G + 2, cz - 6, "minecraft:polished_blackstone");
            g.set(nu + du, G + 2, cz - 7, B.state("end_rod", "facing", "north"));
        }
        g.set(nu, G + 4, cz - 3, "minecraft:white_concrete");
        g.set(nu, G + 5, cz - 3, Gen.slab("smooth_quartz_slab", false));
        // escalier d'embarquement
        g.set(nu + 2, G + 1, cz + 1, Gen.stairs("smooth_quartz_stairs", Dir.WEST, false));
    }

    // ---------------------------------------------------------------- depot de conteneurs (sud, cote milieu)
    void depot(Gen g) {
        int[][] cont = {{13, -30, 0}, {16, -30, 0}, {14, -37, 1}};
        for (int[] c : cont) container(g, c[0], G + 1, c[1], c[2] == 1);
        container(g, 16, G + 4, -30, false);   // empile
        // caisses et bidons
        g.set(21, G + 1, -26, "minecraft:iron_block");
        g.set(21, G + 2, -26, Gen.slab("smooth_stone_slab", false));
        g.set(22, G + 1, -26, B.state("barrel", "facing", "up"));
        g.set(13, G + 1, -24, "minecraft:target");
        g.set(14, G + 1, -24, B.state("barrel", "facing", "east"));
        // rover
        int ru = 20, rz = -33;
        for (int dz = 0; dz <= 3; dz++) for (int du = 0; du <= 1; du++) g.set(ru + du, G + 2, rz + dz, dz == 0 ? "minecraft:tinted_glass" : "minecraft:white_concrete");
        for (int dz : new int[]{0, 3}) for (int du : new int[]{-1, 2}) g.set(ru + du, G + 1, rz + dz, "minecraft:black_concrete");
        g.set(ru, G + 3, rz + 2, "minecraft:lightning_rod");
        g.set(ru + 1, G + 3, rz + 3, "minecraft:TEAM_concrete");
    }

    /** Conteneur 2 x 3 x 5 (couche le long de z), portes en trappes de fer. */
    void container(Gen g, int u, int y, int z, boolean grey) {
        String c = grey ? "minecraft:gray_concrete" : "minecraft:TEAM_concrete";
        for (int du = 0; du <= 1; du++)
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = 0; dz <= 4; dz++) {
                    boolean rib = dz == 0 || dz == 4 || dz == 2;
                    g.set(u + du, y + dy, z + dz, rib ? (dy == 1 && dz == 2 ? "minecraft:iron_block" : c) : (dy == 1 ? "minecraft:light_gray_concrete" : c));
                }
        g.setIfAir(u + 2, y + 1, z + 1, B.trapdoor("iron_trapdoor", Gen.OUT, false, true));
        g.setIfAir(u + 2, y + 1, z + 3, B.trapdoor("iron_trapdoor", Gen.OUT, false, true));
    }

    // ---------------------------------------------------------------- passerelles, conduites et cables
    void catwalks(Gen g) {
        // passerelle surelevee entre les deux modules, passe derriere le dome (u 25, y G+5) ... courte : relie le
        // dessus des modules au toit plat de l'avant du dome
        // conduites au sol (dome -> panneaux)
        for (int z = 6; z <= 8; z++) g.setIfAir(33, top(g, 33, z) + 1, z, B.axis("polished_basalt", 'z'));
        for (int z = -8; z <= -6; z++) g.setIfAir(33, top(g, 33, z) + 1, z, B.axis("polished_basalt", 'z'));
        // lampadaires du chemin
        int[][] lamps = {{20, 11}, {20, -11}, {14, 6}, {14, -6}, {25, 25}, {25, -25}};
        for (int[] l : lamps) {
            int t = top(g, l[0], l[1]);
            if (!g.air(l[0], t + 1, l[1])) continue;
            for (int y = t + 1; y <= t + 2; y++) g.set(l[0], y, l[1], B.pane("iron_bars", false, false, false, false));
            g.set(l[0], t + 3, l[1], "minecraft:sea_lantern");
            g.set(l[0], t + 4, l[1], Gen.slab("smooth_stone_slab", false));
        }
    }

    // ---------------------------------------------------------------- passerelles metalliques entre les plaques
    void gantries(Gen g) {
        for (int sgn : new int[]{-1, 1}) {
            for (int k = 15; k <= 28; k++) {
                int z = sgn * k;
                for (int u = 18; u <= 20; u++) {
                    if (!g.air(u, G, z)) continue;
                    g.set(u, G, z, u == 19 ? "minecraft:polished_andesite" : Gen.slab("smooth_stone_slab", true));
                    g.set(u, G - 1, z, u == 19 ? B.axis("polished_basalt", 'z') : "minecraft:iron_trapdoor");
                }
                if (rim(g, 17, z) >= 1 && !g.air(18, G, z)) g.set(17, G + 1, z, Math.floorMod(z, 3) == 0 ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
                if (rim(g, 21, z) >= 1 && !g.air(20, G, z)) g.set(21, G + 1, z, Math.floorMod(z, 3) == 0 ? "minecraft:iron_block" : B.pane("iron_bars", false, false, false, false));
                if (Math.floorMod(z, 3) == 0) {
                    if (rim(g, 17, z) >= 1 && !g.air(18, G, z)) g.set(17, G + 2, z, "minecraft:end_rod");
                }
            }
        }
    }

    // ---------------------------------------------------------------- barricades basses cote milieu
    void frontCover(Gen g) {
        for (int z = -38; z <= 38; z++) {
            int u0 = -1;
            for (int u = gap(); u <= 20; u++) if (rim(g, u, z) < 1) { u0 = u; break; }
            if (u0 < 0) continue;
            int u = u0 + 1;
            int t = top(g, u, z);
            double v = g.n.noise2(7.7, z / 5.0);
            if (v < -0.1 || !g.air(u, t + 1, z)) continue;
            boolean post = Math.floorMod(z, 5) == 0;
            g.set(u, t + 1, z, post ? "minecraft:iron_block" : "minecraft:polished_andesite");
            if (v > 0.35) g.set(u, t + 2, z, post ? "minecraft:sea_lantern" : Gen.slab("smooth_stone_slab", false));
        }
    }

    // ---------------------------------------------------------------- rochers
    void boulders(Gen g) {
        int[][] b = {{14, -18, 1}, {14, 18, 1}, {26, -25, 1}, {35, 2, 1}};
        for (int[] r : b) {
            int t = top(g, r[0], r[1]);
            for (int du = -r[2]; du <= r[2]; du++)
                for (int dz = -r[2]; dz <= r[2]; dz++)
                    for (int dy = 0; dy <= r[2]; dy++) {
                        double d = Math.sqrt(du * du + dz * dz + dy * dy * 1.6) + 0.4 * g.n.noise3(du, dy, dz);
                        if (d > r[2] + 0.5 || r[0] + du < gap()) continue;
                        g.setIfAir(r[0] + du, t + 1 + dy, r[1] + dz, dy == 0 ? "minecraft:smooth_basalt" : "minecraft:tuff");
                    }
        }
    }

    // ---------------------------------------------------------------- decor propre a chaque camp
    public void decor(Gen g, int side) {
        for (int z = -40; z <= 40; z++)
            for (int u = gap() + 1; u <= 40; u++) {
                double hh = g.h(u, 1, z);
                if (hh > 0.02) continue;
                if (u >= 18 && u <= 26 && Math.abs(z) <= 5) continue;   // places de depart
                int t = -1;
                for (int y = 30; y >= 0; y--) if (!g.air(u, y, z)) { t = y; break; }
                if (t < 0 || !g.air(u, t + 1, z) || !g.air(u, t + 2, z)) continue;
                String n = B.name(g.get(u, t, z));
                if (n.contains("concrete") && !n.contains("light_gray")) continue;
                if (n.equals("light_gray_concrete") || n.equals("calcite") || n.equals("andesite") || n.equals("tuff"))
                    g.set(u, t + 1, z, hh < 0.007 ? Gen.slab("tuff_slab", false) : B.state("amethyst_cluster", "facing", "up"));
            }
        // cailloux, plaques et grilles d'aeration en grappes sur le regolithe
        for (int z = -40; z <= 40; z++)
            for (int u = gap() + 1; u <= 40; u++) {
                if (u >= 18 && u <= 26 && Math.abs(z) <= 5) continue;
                if (g.n.noise2(u / 6.0 + 60, z / 6.0) < 0.45 || g.h(u, 2, z) < 0.55) continue;
                int t = -1;
                for (int y = 30; y >= 0; y--) if (!g.air(u, y, z)) { t = y; break; }
                if (t < 0) continue;
                String n = B.name(g.get(u, t, z));
                if (n.equals("light_gray_concrete") || n.equals("calcite") || n.equals("andesite"))
                    g.debris(u, t + 1, z, new String[]{"minecraft:andesite_slab", "minecraft:tuff_slab", "minecraft:tuff_stairs",
                            "minecraft:andesite_stairs", "minecraft:iron_trapdoor", "minecraft:polished_blackstone_button", "minecraft:light_gray_carpet"});
            }
    }
}
