package fr.eemicraft.sheepwars.gen;

import fr.eemicraft.sheepwars.gen.build.B;
import fr.eemicraft.sheepwars.gen.build.Dir;
import fr.eemicraft.sheepwars.gen.build.Palette;
import fr.eemicraft.sheepwars.gen.build.Roof;
import fr.eemicraft.sheepwars.gen.build.RoofStyle;

/**
 * Port pirate : deux quais face a face sur une baie (la mer tue, comme le Vaisseau). Chaque camp : un navire
 * amarre le long du quai (cote milieu), le quai pave, la place des departs, deux entrepots ouverts, deux grues,
 * un fortin a canons au fond, plages et palmiers aux extremites. Rien entre les deux coques (17 blocs d'eau).
 */
final class Pirates implements SwGen.MapDef {
    public String id() { return "pirates"; }
    public String name() { return "Port pirate"; }
    public int voidRel() { return 8; }
    public int magicRel() { return 16; }
    public int spawnFloor() { return 11; }
    public int gap() { return 9; }
    public long seed() { return 0x50AB1L; }

    static final int SEABED = 4, WATER_TOP = 6, DECK = 11;
    static final int ZMAX = 37;

    Palette pave, quayWall, rock, sand, plank;

    public void buildRed(Gen g) {
        long sd = seed();
        // place en terre battue : chemin, gravier, terre grossiere, plaques de paves (en grappes)
        pave = Palette.of(sd + 1, "minecraft:dirt_path", 42, "minecraft:coarse_dirt", 24, "minecraft:gravel", 22, "minecraft:cobblestone", 12).period(9).amp(0.15);
        quayWall = Palette.of(sd + 2, "minecraft:stone_bricks", 65, "minecraft:cracked_stone_bricks", 15,
                "minecraft:mossy_stone_bricks", 20).period(10).amp(0.15);
        rock = Palette.of(sd + 3, "minecraft:stone", 70, "minecraft:andesite", 30).period(11).amp(0.15);
        sand = Palette.of(sd + 4, "minecraft:sand", 75, "minecraft:suspicious_sand", 0.0001, "minecraft:sandstone", 25).period(6);
        plank = Palette.of(sd + 5, "minecraft:spruce_planks", 70, "minecraft:dark_oak_planks", 30).period(6);
        sea(g);
        land(g);
        quay(g);
        ship(g);
        warehouseNorth(g);
        warehouseSouth(g);
        crane(g, 18, 25, Dir.SOUTH, 22);
        crane(g, 18, -25, Dir.NORTH, 20);
        fort(g);
        quayProps(g);
        stall(g, 23, 9, "minecraft:orange_wool");
        stall(g, 23, -12, "minecraft:white_wool");
        cover(g);
        cellar(g);
        extras(g);
    }

    /** Finitions : toiles des caves, cordages du pont, volets des entrepots, bannieres du fortin, caisses de la terrasse. */
    void extras(Gen g) {
        int[][] webs = {{20, 10, 7}, {20, 10, -7}, {20, 10, 6}, {26, 10, 26}, {26, 10, -26}, {20, 10, 18}, {20, 10, -18}, {19, 10, 28}, {19, 10, -28}};
        for (int[] w : webs) g.setIfAir(w[0], w[1], w[2], "minecraft:cobweb");
        // pont : cordages loves, grilles d'aeration, seaux
        for (int[] c : new int[][]{{10, 6}, {14, -6}, {13, 9}, {11, -14}}) g.setIfAir(c[0], DECK + 1, c[1], "minecraft:brown_carpet");
        for (int z : new int[]{-1, 0}) g.setIfAir(13, DECK + 1, z, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        // volets ouverts des fenetres du fond des entrepots (plaques contre le mur)
        for (int s : new int[]{-1, 1}) {
            int z1 = s > 0 ? 17 : -27;
            for (int z = z1 + 2; z <= z1 + 7; z += 4) {
                g.setIfAir(28, DECK + 2, z - 1, B.trapdoor("spruce_trapdoor", Gen.OUT, false, true));
                g.setIfAir(28, DECK + 2, z + 1, B.trapdoor("spruce_trapdoor", Gen.OUT, false, true));
                g.setIfAir(28, DECK + 1, z, B.stairs("stone_brick_stairs", Gen.IN, true));
            }
        }
        // bannieres de l'equipe sur la face du fortin
        for (int z : new int[]{-4, 4}) g.setIfAir(29, 17, z, B.state("TEAM_wall_banner", "facing", Gen.IN.id()));
        // caisses et tonneaux sur la terrasse (couverts)
        int[][] piles = {{31, 12, 2}, {31, -12, 1}, {33, 22, 2}, {33, -21, 2}, {30, 18, 1}, {30, -18, 1}};
        for (int[] p : piles) {
            int y = topY(g, p[0], p[1]);
            if (y < 13 || !g.air(p[0], y + 1, p[1])) continue;
            g.set(p[0], y + 1, p[1], "minecraft:spruce_planks");
            if (p[2] == 2) g.set(p[0], y + 2, p[1], B.state("barrel", "facing", "up"));
            else g.setIfAir(p[0], y + 2, p[1], B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
            g.setIfAir(p[0], y + 1, p[1] + 1, B.state("barrel", "facing", "north"));
        }
    }

    /** Couverts : caisses sur le quai et la place, cabestan et caisses sur le pont, petite batterie de quai. */
    void cover(Gen g) {
        int y = DECK + 1;
        int[][] crates = {{18, 8, 2}, {18, 9, 1}, {18, -8, 2}, {18, -9, 1}, {21, 13, 1}, {21, -14, 1}, {20, 24, 2}, {20, -24, 2}};
        for (int[] c : crates) {
            for (int k = 0; k < c[2]; k++) g.set(c[0], y + k, c[1], k == 0 ? "minecraft:spruce_planks" : "minecraft:barrel");
            g.setIfAir(c[0], y + c[2], c[1], B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        }
        // cabestan au milieu du pont
        g.set(12, y, -6, B.axis("spruce_log", 'y'));
        g.set(12, y + 1, -6, Gen.slab("spruce_slab", false));
        for (Dir d : Dir.values()) g.set(12 + d.dx, y, -6 + d.dz, "minecraft:spruce_fence");
        // caisses arrimees sur le pont
        g.set(10, y, -1, "minecraft:spruce_planks");
        g.set(10, y, 0, "minecraft:spruce_planks");
        g.set(10, y + 1, -1, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        g.set(10, y, 13, "minecraft:hay_block");
        g.set(10, y, 12, B.state("barrel", "facing", "up"));
        // batterie de quai : muret de sacs (laine brune) et canon tourne vers le milieu
        for (int z : new int[]{-17, 17}) {
            for (int dz = -2; dz <= 2; dz++) g.set(17, y, z + dz, Math.abs(dz) == 2 ? "minecraft:cobblestone_wall" : "minecraft:brown_wool");
            g.set(17, y + 1, z, Gen.slab("cobblestone_slab", false));
            g.set(18, y, z, B.axis("polished_basalt", 'x'));
            g.set(19, y, z, "minecraft:coal_block");
            g.set(18, y, z - 1, B.trapdoor("dark_oak_trapdoor", Dir.NORTH, false, true));
            g.set(18, y, z + 1, B.trapdoor("dark_oak_trapdoor", Dir.SOUTH, false, true));
        }
    }

    /** Etal de marche : 4 poteaux, auvent raye, comptoir en trappes, marchandises. */
    void stall(Gen g, int u1, int z1, String stripe) {
        int y = DECK + 1;
        for (int du = 0; du <= 2; du += 2)
            for (int dz = 0; dz <= 2; dz += 2)
                for (int k = 0; k < 2; k++) g.set(u1 + du, y + k, z1 + dz, "minecraft:spruce_fence");
        for (int du = -1; du <= 3; du++)
            for (int dz = 0; dz <= 2; dz++)
                g.set(u1 + du, y + 2, z1 + dz, (du == -1 || du == 3) ? Gen.slab("spruce_slab", true)
                        : (Math.floorMod(dz, 2) == 0 ? stripe : "minecraft:TEAM_wool"));
        g.set(u1, y, z1 + 1, B.trapdoor("spruce_trapdoor", Dir.WEST, true, false));
        g.set(u1 + 1, y, z1 + 1, B.state("barrel", "facing", "up"));
        g.set(u1 + 2, y, z1 + 1, "minecraft:melon");
        g.set(u1 + 1, y + 1, z1 + 1, "minecraft:flower_pot");
        g.set(u1 + 2, y + 1, z1 + 1, Gen.slab("spruce_slab", false));
        g.hangLantern(u1 + 1, y + 1, z1 + 1, 0, false);
    }

    // ---------------------------------------------------------------- mer
    void sea(Gen g) {
        for (int u = 0; u <= 41; u++)
            for (int z = -ZMAX - 6; z <= ZMAX + 6; z++) {
                double rn = 1.6 * g.nz(u + 100, z, 5);
                boolean rim = u >= 39 + rn || Math.abs(z) >= ZMAX + 4 + rn;
                double nn = g.nz(u, z, 7);
                g.set(u, SEABED - 1, z, "minecraft:stone");
                g.set(u, SEABED, z, nn > 0.35 ? "minecraft:gravel" : nn < -0.45 ? "minecraft:clay" : "minecraft:sand");
                if (rim) {
                    g.set(u, SEABED + 1, z, rock);
                    // recif : dessus en 6 (au ras de l'eau, on y meurt), parfois mousse ou gravier
                    double t = g.nz(u, z + 300, 3);
                    g.set(u, WATER_TOP, z, t > 0.4 ? "minecraft:mossy_cobblestone" : t < -0.5 ? "minecraft:gravel" : rock.at(-u, WATER_TOP, z));
                    // rochers emerges en grappes (hors zone de jeu : on meurt sur le recif)
                    double rk = g.nz(u + 50, z, 4);
                    if (rk > 0.35 && u >= 12) {
                        g.set(u, WATER_TOP + 1, z, rk > 0.6 ? "minecraft:mossy_cobblestone" : "minecraft:andesite");
                        if (rk > 0.62) g.set(u, WATER_TOP + 2, z, "minecraft:stone");
                    }
                } else {
                    g.set(u, SEABED + 1, z, B.WATER);
                    g.set(u, WATER_TOP, z, B.WATER);
                    if (nn < -0.2 && g.h(u, 0, z) < 0.08 && u < 40) g.set(u, SEABED + 1, z, "minecraft:seagrass");
                }
            }
    }

    // ---------------------------------------------------------------- terre ferme
    /** Bord arriere de la terre (u max) selon z. */
    int back(int z) { return 34 + (int) Math.round(1.4 * Math.sin(z * 0.21) + 0.8 * Math.cos(z * 0.47)); }
    /** Extremite en z selon u. */
    int zend(int u) { return 33 - (int) Math.round(Math.abs(u - 25) * 0.2 + 1.2 * Math.sin(u * 0.6)); }

    void land(Gen g) {
        for (int z = -ZMAX; z <= ZMAX; z++)
            for (int u = 16; u <= back(z); u++) {
                int ze = zend(u);
                if (Math.abs(z) > ze + 3) continue;
                int top;
                boolean beach = Math.abs(z) > ze;
                if (beach) top = 7;                       // plage (pieds en 8, au ras de la mort : eau juste derriere)
                else if (u >= 29) top = 15 + (u >= 32 ? 1 : 0);   // terrasse du fond
                else top = DECK;
                if (!beach && Math.abs(z) > ze - 3 && top > 8) top = Math.max(8, top - (Math.abs(z) - (ze - 3)) * 2);
                for (int y = SEABED; y <= top; y++) {
                    String st;
                    if (y == top) {
                        if (top <= 9) st = sand.at(-u, y, z);
                        else if (u >= 29 && top >= 13) st = g.nz(u, z, 5) > 0.45 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                        else st = pave.at(-u, y, z);
                    } else if (top >= 13 && u >= 29 && y >= top - 2) st = "minecraft:dirt";
                    else st = rock.at(-u, y, z);
                    g.set(u, y, z, st);
                }
            }
        // falaise du fond : marche de 4 (pierre) entre la place (11) et la terrasse (15) : rampes en escaliers
        for (int z : new int[]{-14, 14}) {
            for (int k = 0; k < 4; k++)
                for (int dz = -1; dz <= 1; dz++) {
                    g.set(25 + k, DECK + 1 + k, z + dz, Gen.stairs("stone_brick_stairs", Gen.OUT, false));
                    for (int y = DECK; y <= DECK + k; y++) g.set(25 + k, y, z + dz, "minecraft:stone_bricks");
                    g.clear(25 + k, DECK + 2 + k, z + dz, 25 + k, DECK + 5 + k, z + dz);
                }
            // rambarde pierre
            for (int k = 0; k < 4; k++) {
                g.set(25 + k, DECK + 2 + k, z - 2, "minecraft:stone_brick_wall");
                g.set(25 + k, DECK + 2 + k, z + 2, "minecraft:stone_brick_wall");
            }
        }
        // mur de soutenement entre place et terrasse (3 plans : socle, pilastres, couronnement)
        for (int z = -ZMAX; z <= ZMAX; z++) {
            if (Math.abs(z) > zend(29) - 3) continue;
            if (Math.abs(Math.abs(z) - 14) <= 2) continue;
            for (int y = DECK + 1; y <= 14; y++) g.set(29, y, z, quayWall);
            g.set(29, 15, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));   // corniche
            g.set(28, DECK + 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, false));
            if (Math.floorMod(z, 6) == 0) {
                for (int y = DECK + 1; y <= 15; y++) g.set(28, y, z, "minecraft:polished_andesite");
                g.set(28, 16, z, Gen.slab("smooth_stone_slab", false));
            }
            if (Math.floorMod(z, 6) != 3) g.set(29, 16, z, "minecraft:stone_brick_wall");
        }
    }

    // ---------------------------------------------------------------- quai
    void quay(Gen g) {
        int ze = 30;
        for (int z = -ze; z <= ze; z++) {
            for (int y = SEABED; y <= DECK; y++) {
                // parement du quai cote eau (u = 16) : bas moussu, haut propre, chaperon en dalles
                String st = y <= 7 ? (g.nz(16, z + y * 3, 4) > -0.2 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks")
                        : quayWall.at(-16, y, z);
                g.set(16, y, z, st);
            }
            g.set(16, DECK, z, "minecraft:polished_andesite");
            g.set(16, DECK - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));   // corniche sous le chaperon
            g.set(17, DECK, z, "minecraft:smooth_stone");
            // contreforts tous les 8 (pas devant l'embarcadere)
            if (Math.floorMod(z, 8) == 4 && Math.abs(z) < 21) {
                for (int y = SEABED; y <= DECK - 1; y++) g.set(15, y, z, "minecraft:stone_bricks");
                g.set(15, DECK - 1, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            }
            // algues et lierre sur le parement
            if (g.h(16, 3, z) < 0.18) g.set(15, 7, z, B.state("vine", "west", "true"));
        }
    }

    // ---------------------------------------------------------------- caves de contrebandiers (sous le quai)
    /**
     * Niveau souterrain : sol en 7, air 8-10, plafond = le quai / la place (11). Galerie voutee le long du quai
     * (u 17-19), salle des contrebandiers sous la place (u 20-27, |z| <= 7), reserves sous les deux entrepots. Acces :
     * 2 escaliers depuis la place, 1 par entrepot, le passage de la cale, l'embarcadere (porte d'eau) a chaque bout.
     */
    void cellar(Gen g) {
        Palette floor = Palette.of(seed() + 31, "minecraft:stone", 40, "minecraft:gravel", 25, "minecraft:coarse_dirt", 20, "minecraft:cobblestone", 15).period(10).amp(0.12);
        // creusement
        dig(g, 17, 19, -28, 28, floor);
        dig(g, 20, 27, -7, 7, floor);
        dig(g, 20, 26, 18, 26, floor);
        dig(g, 20, 26, -26, -18, floor);
        // galerie : arcs doubleaux tous les 6, lanternes entre deux
        for (int z = -28; z <= 28; z++) {
            if (Math.floorMod(z, 6) == 0) {
                g.set(17, 10, z, Gen.stairs("stone_brick_stairs", Gen.IN, true));
                if (!(z >= -7 && z <= 7) && !(Math.abs(z) >= 18 && Math.abs(z) <= 26)) g.set(19, 10, z, Gen.stairs("stone_brick_stairs", Gen.OUT, true));
            }
            if (Math.floorMod(z, 6) == 3) g.hangTo(18, 10, z, false);
            // anneaux d'amarrage et cordages le long du parement
            if (Math.floorMod(z, 6) == 1 && Math.abs(z) < 26) g.set(17, 8, z, "minecraft:brown_carpet");
        }
        // soupiraux ouverts dans la place et le quai : les moutons tombent aussi dans les caves
        for (int z : new int[]{-2, 2}) g.clear(26, DECK, z, 26, DECK, z);
        for (int z : new int[]{-17, -12, 12, 17}) g.clear(18, DECK, z, 18, DECK, z);
        // salle des contrebandiers : piliers a chapiteaux, table, tonneaux de rhum, butin
        for (int z : new int[]{-4, 0, 4}) pillar(g, 22, z);
        for (int z : new int[]{-4, 4}) pillar(g, 25, z);
        g.set(24, 8, 0, "minecraft:dark_oak_fence");
        g.set(24, 9, 0, B.state("dark_oak_pressure_plate"));
        g.set(24, 8, -1, Gen.stairs("dark_oak_stairs", Dir.SOUTH, false));
        g.set(24, 8, 1, Gen.stairs("dark_oak_stairs", Dir.NORTH, false));
        g.set(25, 8, 0, Gen.stairs("dark_oak_stairs", Gen.IN, false));
        g.hangTo(24, 10, 0, false);
        for (int z = -7; z <= 7; z++) {
            if (Math.abs(z) <= 1) continue;
            if (Math.abs(z) >= 5) continue;   // escaliers
            g.barrel(27, 8, z, Math.floorMod(z, 2) == 0, Dir.SOUTH);
            if (Math.abs(z) == 3) g.barrel(27, 9, z, false, Dir.NORTH);
        }
        g.set(27, 8, 0, "minecraft:gold_block");
        g.set(27, 8, 1, "minecraft:raw_gold_block");
        g.set(26, 8, 1, Gen.slab("smooth_stone_slab", false));
        g.set(27, 9, 0, B.state("candle", "candles", "3", "lit", "false"));
        g.set(27, 8, -1, "minecraft:chiseled_bookshelf");
        g.hangTo(22, 10, -2, false);
        g.hangTo(22, 10, 2, false);
        g.hangTo(26, 10, 0, true);
        // sorties de la salle vers la place (montent vers les bords) + garde-corps autour des tremies
        g.flight(26, 5, 8, 4, Dir.SOUTH, Gen.OUT, 2, "stone_brick_stairs", "minecraft:stone_bricks", 8, null);
        g.flight(26, -5, 8, 4, Dir.NORTH, Gen.OUT, 2, "stone_brick_stairs", "minecraft:stone_bricks", 8, null);
        for (int s : new int[]{-1, 1}) {
            for (int k = 5; k <= 7; k++) g.set(25, DECK + 1, s * k, k == 6 ? "minecraft:cobblestone_wall" : "minecraft:stone_brick_wall");
            g.set(26, DECK + 1, s * 4, "minecraft:stone_brick_wall");
            g.set(27, DECK + 1, s * 4, "minecraft:stone_brick_wall");
        }
        // reserves sous les entrepots : sacs, caisses, sorties par escalier dans l'entrepot
        for (int s : new int[]{-1, 1}) {
            for (int k = 18; k <= 26; k += 2) { g.barrel(26, 8, s * k, true, Dir.NORTH); }
            g.set(25, 8, s * 25, "minecraft:spruce_planks");
            g.set(25, 9, s * 25, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
            g.set(20, 8, s * 25, "minecraft:hay_block");
            g.set(21, 8, s * 25, "minecraft:hay_block");
            g.set(20, 9, s * 25, "minecraft:hay_block");
            g.hangTo(24, 10, s * 20, false);
            g.hangTo(21, 10, s * 22, false);
            g.flight(22, s * 22, 8, 4, s > 0 ? Dir.NORTH : Dir.SOUTH, Gen.OUT, 2, "spruce_stairs", "minecraft:spruce_planks", 8, null);
            for (int k = 19; k <= 22; k++) g.set(24, DECK + 1, s * k, "minecraft:spruce_fence");
            g.set(22, DECK + 1, s * 23, "minecraft:spruce_fence");
            g.set(23, DECK + 1, s * 23, "minecraft:spruce_fence");
            g.set(21, DECK + 1, s * 20, "minecraft:spruce_fence");
            g.set(21, DECK + 1, s * 22, "minecraft:spruce_fence");
        }
        // embarcadere a chaque bout : palier au ras de l'eau, porte d'eau 2 x 2, escalier le long du parement
        for (int s : new int[]{-1, 1}) {
            for (int z = 27; z <= 29; z++)
                for (int u = 14; u <= 15; u++) {
                    for (int y = SEABED; y <= 6; y++) g.set(u, y, s * z, "minecraft:stone");
                    g.set(u, 7, s * z, z == 29 && u == 14 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
                }
            g.set(14, 8, s * 27, "minecraft:mossy_cobblestone_wall");
            g.set(14, 8, s * 29, "minecraft:cobblestone_wall");
            g.set(14, 8, s * 30, "minecraft:cobblestone_wall");
            g.set(15, 8, s * 30, "minecraft:cobblestone_wall");
            for (int y = SEABED; y <= 7; y++) { g.set(14, y, s * 30, "minecraft:stone_bricks"); g.set(15, y, s * 30, "minecraft:stone_bricks"); }
            g.clear(16, 8, s * 27, 16, 9, s * 28);
            g.set(16, 10, s * 27, Gen.stairs("stone_brick_stairs", s > 0 ? Dir.NORTH : Dir.SOUTH, true));
            g.set(16, 10, s * 28, Gen.stairs("stone_brick_stairs", s > 0 ? Dir.SOUTH : Dir.NORTH, true));
            // pile du palier d'arrivee, puis l'escalier qui monte vers le milieu du quai
            for (int y = SEABED; y <= DECK; y++) g.set(15, y, s * 22, y == DECK ? "minecraft:polished_andesite" : "minecraft:stone_bricks");
            g.flight(15, s * 26, 8, 4, s > 0 ? Dir.NORTH : Dir.SOUTH, Gen.OUT, 1, "stone_brick_stairs", "minecraft:stone_bricks", SEABED, null);
            // meurtriere vers la baie, a cote du navire
            g.set(16, 9, s * 21, B.AIR);
            g.set(14, 8, s * 28, B.AIR);
        }
    }

    /** Creuse une salle du niveau souterrain (sol en 7, air 8-10). */
    void dig(Gen g, int u1, int u2, int z1, int z2, Palette floor) {
        for (int u = u1; u <= u2; u++)
            for (int z = z1; z <= z2; z++) {
                g.clear(u, 8, z, u, 10, z);
                g.set(u, 7, z, floor.at(-u, 7, z));
                // plafond : jamais de gravier au-dessus du vide (il tomberait)
                if (B.name(g.get(u, DECK, z)).equals("gravel")) g.set(u, DECK, z, "minecraft:coarse_dirt");
            }
    }

    /** Pilier de cave : fut en pierre, chapiteau en escaliers retournes. */
    void pillar(Gen g, int u, int z) {
        for (int y = 8; y <= 10; y++) g.set(u, y, z, y == 8 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
        for (Dir d : Dir.values()) {
            int[] p = Gen.step(u, z, d, 1);
            if (g.air(p[0], 10, p[1])) g.set(p[0], 10, p[1], Gen.stairs("stone_brick_stairs", d.opposite(), true));
        }
    }

    // ---------------------------------------------------------------- navire
    /** Demi-largeur de la coque (autour de u = 12) a la position z ; -1 hors navire. */
    static int beam(int z) {
        if (z < -20 || z > 21) return -1;
        if (z <= -18) return 2;
        if (z <= 15) return 3;
        if (z <= 18) return 2;
        if (z <= 20) return 1;
        return 0;
    }

    void ship(Gen g) {
        final int C = 12;
        for (int z = -20; z <= 21; z++) {
            int b = beam(z);
            if (b < 0) continue;
            for (int y = 2; y <= DECK; y++) {
                int bw = b;
                if (y <= 4) bw = Math.max(0, b - (5 - y));          // fond arrondi
                else if (y == 5) bw = Math.max(0, b - 1);
                for (int u = C - bw; u <= C + bw; u++) {
                    boolean edge = Math.abs(u - C) == bw;
                    String st;
                    if (y == DECK) st = edge ? "minecraft:stripped_dark_oak_wood" : "minecraft:spruce_planks";
                    else if (y == 9 && edge) st = "minecraft:TEAM_terracotta";            // bande de l'equipe
                    else if (y == 6 && edge) st = "minecraft:stripped_dark_oak_wood";     // ligne de flottaison
                    else if (edge) st = (y >= 7 && Math.floorMod(z, 3) == 0) ? "minecraft:stripped_spruce_wood" : "minecraft:dark_oak_planks";
                    else st = "minecraft:spruce_planks";
                    g.set(u, y, z, st);
                }
            }
            // pavois : 1 bloc + main courante en dalle (couvert a hauteur de poitrine)
            for (int side : new int[]{-1, 1}) {
                int u = C + side * b;
                boolean gangway = side == 1 && (z == -6 || z == -5 || z == 7 || z == 8);  // passerelles vers le quai
                if (gangway) continue;
                g.set(u, DECK + 1, z, Math.floorMod(z, 4) == 1 ? "minecraft:stripped_dark_oak_log" : "minecraft:dark_oak_planks");
                g.set(u, DECK + 2, z, Gen.slab("dark_oak_slab", false));
            }
            if (b < 3 && b > 0 && z > 0) {   // pavois de la proue (le long de z)
                g.set(C, DECK + 1, 21, "minecraft:dark_oak_planks");
            }
            // sabords et canons cote milieu tous les 6
            if (b == 3 && Math.floorMod(z, 6) == 3 && z > -12 && z < 12) {
                // volet de sabord ouvert, plaque contre le pavois voisin (z+1)
                g.set(C - 3, DECK + 1, z, B.trapdoor("dark_oak_trapdoor", Dir.NORTH, true, true));
                g.set(C - 3, DECK + 2, z, Gen.slab("dark_oak_slab", false));
                g.set(C - 2, DECK + 1, z, B.axis("polished_basalt", 'x'));
                g.set(C - 1, DECK + 1, z, B.axis("polished_basalt", 'x'));
                g.set(C - 1, DECK + 1, z - 1, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, true));
                g.set(C - 1, DECK + 1, z + 1, B.trapdoor("spruce_trapdoor", Dir.SOUTH, false, true));
            }
        }
        // passerelles vers le quai
        for (int z : new int[]{-6, -5, 7, 8}) g.set(15, DECK, z, Gen.slab("spruce_slab", true));
        // gaillard d'arriere (z -20..-13) a +4, cabine ouverte a l'avant et cote milieu (arcades)
        int Q = DECK + 4;
        for (int z = -20; z <= -13; z++) {
            int b = beam(z);
            for (int u = C - b; u <= C + b; u++) {
                boolean edge = Math.abs(u - C) == b;
                boolean stern = z == -20;
                for (int y = DECK + 1; y < Q; y++) {
                    String st = B.AIR;
                    if (stern) {
                        boolean win = y == DECK + 2 && Math.abs(u - C) <= 1;
                        st = win ? B.pane("glass_pane", false, true, false, true) : (Math.abs(u - C) == 2 && y == DECK + 2 ? "minecraft:TEAM_wool" : "minecraft:dark_oak_planks");
                    } else if (edge) {
                        boolean post = Math.floorMod(z + 1, 3) == 0;
                        if (u > C) st = post ? "minecraft:stripped_dark_oak_log" : (y == DECK + 2 ? B.pane("glass_pane", true, false, true, false) : "minecraft:dark_oak_planks");
                        else st = post || y == Q - 1 ? "minecraft:stripped_dark_oak_log" : B.AIR;     // arcades cote milieu
                    }
                    g.set(u, y, z, st);
                }
                g.set(u, Q, z, "minecraft:spruce_planks");
                if (edge || stern) g.set(u, Q + 1, z, "minecraft:dark_oak_fence");
            }
        }
        // mobilier de la cabine
        g.hangLantern(C, Q - 1, -17, 0, false);
        g.set(C, DECK + 1, -18, "minecraft:dark_oak_fence");
        g.set(C, DECK + 2, -18, B.state("dark_oak_pressure_plate"));
        g.set(C + 1, DECK + 1, -19, B.state("barrel", "facing", "up"));
        g.set(C - 1, DECK + 1, -19, "minecraft:chiseled_bookshelf");
        g.set(C + 2, DECK + 1, -16, "minecraft:cartography_table");
        // escaliers vers le gaillard (cote quai, u 13..14), 4 marches
        for (int k = 0; k < 4; k++) {
            for (int u = 13; u <= 14; u++) {
                g.set(u, DECK + 1 + k, -9 - k, Gen.stairs("spruce_stairs", Dir.NORTH, false));
                for (int y = DECK + 1; y < DECK + 1 + k; y++) g.set(u, y, -9 - k, "minecraft:spruce_planks");
            }
        }
        g.clear(13, Q + 1, -13, 14, Q + 1, -13);
        // poupe : lanterne et banniere
        g.set(C, Q + 2, -20, B.state("lantern", "hanging", "false"));
        g.set(C, Q + 1, -20, "minecraft:dark_oak_fence");
        g.set(C, DECK + 1, -21, B.state("TEAM_wall_banner", "facing", "north"));
        // gaillard d'avant (z 15..20) a +2
        for (int z = 15; z <= 20; z++) {
            int b = beam(z);
            for (int u = C - b; u <= C + b; u++) {
                g.set(u, DECK + 1, z, "minecraft:dark_oak_planks");
                g.set(u, DECK + 2, z, "minecraft:spruce_planks");
                if (Math.abs(u - C) == b || z == 20) g.set(u, DECK + 3, z, "minecraft:dark_oak_fence");
            }
        }
        // deux marches vers le gaillard d'avant (+2)
        for (int u = C - 1; u <= C + 1; u++) {
            g.set(u, DECK + 1, 13, Gen.stairs("spruce_stairs", Dir.SOUTH, false));
            g.set(u, DECK + 1, 14, "minecraft:spruce_planks");
            g.set(u, DECK + 2, 14, Gen.stairs("spruce_stairs", Dir.SOUTH, false));
        }
        g.clear(C - 1, DECK + 3, 15, C + 1, DECK + 4, 15);
        // beaupre (le long de z, vers l'avant) et figure de proue
        for (int k = 0; k < 6; k++) g.set(C, DECK + 2 + k / 2, 21 + k, B.axis("spruce_log", 'z'));
        g.set(C, DECK + 1, 21, "minecraft:carved_pumpkin");
        // chaine d'ancre le long de la coque cote quai
        for (int y = 7; y <= DECK - 1; y++) g.set(C + 3, y, 12, B.axis("iron_chain", 'y'));
        // mats
        mast(g, C, 1, 33, true);
        mast(g, C, 10, 28, false);
        mast(g, C, -16, Q + 12, false);
        // drapeau de l'equipe au grand mat
        for (int y = 32; y <= 33; y++) for (int z = 2; z <= 4; z++) g.set(C, y, z, "minecraft:TEAM_wool");
        // haubans (chaines) du grand mat
        for (int k = 1; k <= 6; k++) g.set(C + 3, DECK + 1 + k, 1, B.axis("iron_chain", 'y'));   // jusqu'a la vergue (y 19)
        // tonneaux, foin, ecoutille
        g.barrel(14, DECK + 1, -2, false, Dir.NORTH);
        g.barrel(14, DECK + 1, -1, true, Dir.NORTH);
        g.barrel(14, DECK + 2, -2, false, Dir.NORTH);
        g.set(14, DECK + 1, 11, B.state("barrel", "facing", "up"));
        hold(g);
    }

    // ---------------------------------------------------------------- cale du navire (sous le pont)
    /**
     * Cale : sol en 7 (pieds en 8 = hauteur de mort, on n'y meurt pas), 3 blocs d'air sous le pont. Acces : escalier
     * depuis le pont (u 13-14, z 2-5), echelle d'ecoutille (u 14, z -4), passage vers la galerie du quai (z 7-8).
     * Sabords ouverts sur le milieu (u 9, y 9) : on tire et on recoit les moutons.
     */
    void hold(Gen g) {
        final int C = 12;
        for (int z = -12; z <= 13; z++)
            for (int u = C - 2; u <= C + 2; u++) {
                g.clear(u, 8, z, u, 10, z);
                // lest de gravier, coursive de planches au milieu
                g.set(u, 7, z, (u == C) ? "minecraft:dark_oak_planks" : (g.nz(u, z, 3) > 0.3 ? "minecraft:coarse_dirt" : "minecraft:gravel"));
            }
        // preceintes : moulures en escaliers retournes dans le bordage cote milieu (y 8 et 10)
        for (int z = -20; z <= 21; z++) {
            int b = beam(z);
            if (b < 2) continue;
            for (int y : new int[]{8, 10, DECK}) {
                String st = g.get(C - b, y, z);
                if (!B.isAir(st) && (B.name(st).contains("planks") || B.name(st).contains("wood")))
                    g.set(C - b, y, z, Gen.stairs("dark_oak_stairs", Gen.OUT, true));
            }
        }
        // couples (membrures) tous les 4 : arcs en escaliers retournes sous le pont
        for (int z = -12; z <= 13; z += 4) {
            g.set(C - 2, 10, z, Gen.stairs("dark_oak_stairs", Gen.IN, true));
            g.set(C + 2, 10, z, Gen.stairs("dark_oak_stairs", Gen.OUT, true));
        }
        // pieds de mats
        for (int z : new int[]{1, 10}) for (int y = 8; y <= 10; y++) g.set(C, y, z, B.axis("spruce_log", 'y'));
        // sabords bas cote milieu (1 x 1 a hauteur des yeux) et canons de batterie
        for (int z = -11; z <= 13; z += 3) g.set(C - 3, 9, z, B.AIR);
        for (int z : new int[]{-8, 4}) {
            g.set(C - 2, 8, z, B.axis("polished_basalt", 'x'));
            g.set(C - 1, 8, z, "minecraft:coal_block");
            g.set(C - 1, 9, z, Gen.slab("dark_oak_slab", false));
            g.set(C - 2, 8, z - 1, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, true));
            g.set(C - 2, 8, z + 1, B.trapdoor("spruce_trapdoor", Dir.SOUTH, false, true));
        }
        // escalier du pont vers la cale (monte vers le sud), garde-corps autour de la tremie
        g.flight(C + 1, 2, 8, 4, Dir.SOUTH, Gen.OUT, 2, "spruce_stairs", "minecraft:spruce_planks", 8, "minecraft:spruce_planks");
        for (int z = 1; z <= 4; z++) g.set(C, DECK + 1, z, z == 1 ? B.axis("spruce_log", 'y') : "minecraft:spruce_fence");
        g.set(C + 1, DECK + 1, 1, "minecraft:spruce_fence");
        g.set(C + 2, DECK + 1, 1, "minecraft:spruce_fence");
        // ecoutille a echelle (contre le bordage cote quai)
        g.clear(C + 2, DECK, -4, C + 2, DECK, -4);
        g.ladder(C + 2, -4, 8, DECK, Gen.IN, null);
        // passage vers la galerie du quai (a travers le bordage et le parement)
        for (int z = 7; z <= 8; z++) for (int u = C + 3; u <= 16; u++) g.clear(u, 8, z, u, 10, z);
        g.set(C + 3, 10, 7, Gen.stairs("stone_brick_stairs", Dir.NORTH, true));
        g.set(C + 3, 10, 8, Gen.stairs("stone_brick_stairs", Dir.SOUTH, true));
        // cargaison : tonneaux, caisses, hamacs (tapis), cordages
        for (int z : new int[]{-12, -11, -10, 11, 12, 13}) {
            g.barrel(C + 2, 8, z, z % 2 == 0, Dir.NORTH);
            if (z == -12 || z == 13) g.barrel(C + 2, 9, z, false, Dir.NORTH);
        }
        g.set(C + 2, 8, -6, "minecraft:spruce_planks");
        g.set(C + 2, 9, -6, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        g.set(C + 1, 8, -1, "minecraft:hay_block");
        for (int z = -5; z <= -3; z++) g.set(C - 1, 8, z, "minecraft:white_carpet");
        for (int z = 9; z <= 11; z++) g.set(C - 1, 8, z, "minecraft:brown_carpet");
        g.set(C - 2, 8, -11, "minecraft:cauldron");
        for (int z : new int[]{-10, -2, 6, 12}) g.hangTo(C, 10, z, false);
    }

    void mast(Gen g, int u, int z, int top, boolean nest) {
        int base = DECK + 1;
        if (z <= -16) base = DECK + 4;
        for (int y = base; y <= top; y++) g.set(u, y, z, B.axis("spruce_log", 'y'));
        // vergues (le long de x, dans la largeur de la coque) et voiles carguees
        int[] yards = nest ? new int[]{top - 14, top - 8} : new int[]{top - 7};
        for (int yy : yards) {
            for (int du = -3; du <= 3; du++) if (du != 0) g.set(u + du, yy, z, B.axis("stripped_spruce_log", 'x'));
            for (int du = -2; du <= 2; du++) {
                if (du == 0) continue;
                g.set(u + du, yy - 1, z, "minecraft:white_wool");
                if (Math.abs(du) <= 1) g.set(u + du, yy - 2, z, "minecraft:white_wool");
            }
            for (int du = -1; du <= 1; du += 2) g.set(u + du * 2, yy - 2, z, Gen.slab("smooth_quartz_slab", true));
        }
        if (nest) {
            int ny = top - 5;
            for (int du = -1; du <= 1; du++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (du == 0 && dz == 0) continue;
                    g.set(u + du, ny, z + dz, "minecraft:spruce_planks");
                    g.set(u + du, ny + 1, z + dz, "minecraft:spruce_fence");
                }
            g.set(u, ny + 1, z + 1, B.AIR);
            // echelle le long du mat (face sud), du pont au nid de pie
            for (int y = DECK + 1; y <= ny; y++) g.set(u, y, z + 1, B.state("ladder", "facing", "south"));
            g.set(u + 1, ny + 2, z - 1, B.state("lantern", "hanging", "false"));
        }
    }

    // ---------------------------------------------------------------- entrepots (ouverts cote quai)
    void warehouseNorth(Gen g) {
        int u1 = 21, u2 = 27, z1 = 17, z2 = 27, y0 = DECK + 1, y1 = DECK + 6;
        shed(g, u1, u2, z1, z2, y0, y1, "minecraft:spruce_planks", "minecraft:stripped_spruce_log", "minecraft:spruce_stairs", true);
        // caisses et tonneaux dedans
        crates(g, 25, y0, 19, 3);
        crates(g, 25, y0, 24, 2);
        g.set(23, y0, 25, "minecraft:hay_block");
        g.set(23, y0 + 1, 25, "minecraft:hay_block");
        g.set(22, y0, 26, B.state("barrel", "facing", "east"));
        loft(g, 18, 26, 22);
        g.hangTo(22, y0 + 3, 24, false);
    }

    /**
     * Soupente d'entrepot (2e niveau, +3) : plancher en 14 sur u 24-26, garde-corps partiel, echelle contre le mur du
     * fond. On tire de la-haut par-dessus le quai, a travers la facade ouverte.
     */
    void loft(Gen g, int z1, int z2, int zl) {
        int y = DECK + 3;
        for (int z = z1; z <= z2; z++)
            for (int u = 24; u <= 26; u++) {
                g.set(u, y, z, (u == 24) ? "minecraft:stripped_spruce_wood" : "minecraft:spruce_planks");
                g.clear(u, y + 1, z, u, y + 1, z);
            }
        for (int z = z1; z <= z2; z++) {
            if (Math.floorMod(z - z1, 3) != 1) g.set(24, y + 1, z, "minecraft:spruce_fence");
            if (Math.floorMod(z - z1, 4) == 0) g.set(24, y - 1, z, Gen.stairs("spruce_stairs", Gen.OUT, true));
        }
        g.clear(26, y, zl, 26, y, zl);
        g.ladder(26, zl, DECK + 1, y, Gen.IN, null);
        g.set(25, y + 1, z1 + 1, B.state("barrel", "facing", "up"));
        g.set(26, y + 1, z2 - 1, "minecraft:spruce_planks");
        g.set(26, y + 2, z2 - 1, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
    }

    void warehouseSouth(Gen g) {
        int u1 = 21, u2 = 27, z1 = -27, z2 = -17, y0 = DECK + 1, y1 = DECK + 7;
        shed(g, u1, u2, z1, z2, y0, y1, "minecraft:bricks", "minecraft:stone_bricks", "minecraft:dark_oak_stairs", false);
        crates(g, 25, y0, -25, 3);
        crates(g, 25, y0, -20, 2);
        g.set(22, y0, -26, B.state("barrel", "facing", "up"));
        g.set(22, y0 + 1, -26, B.state("barrel", "facing", "up"));
        g.set(23, y0, -26, "minecraft:cauldron");
        loft(g, -26, -18, -22);
        g.hangTo(22, y0 + 3, -25, false);
    }

    /**
     * Halle : mur du fond (u2) et murs de pignon (z1/z2) partiels, facade cote quai (u1) ouverte sur des poteaux,
     * toit a deux pans (pignons nord/sud) avec debord.
     */
    void shed(Gen g, int u1, int u2, int z1, int z2, int y0, int y1, String wall, String post, String roofStairs, boolean wood) {
        Palette w = wood ? Palette.of(seed() + 21, wall, 70, "minecraft:dark_oak_planks", 30).period(8)
                : Palette.of(seed() + 22, wall, 65, "minecraft:mud_bricks", 20, "minecraft:granite", 15).period(8);
        String postY = post.contains("log") ? B.axis(post, 'y') : post;
        String postZ = post.contains("log") ? B.axis(post, 'z') : post;
        for (int z = z1; z <= z2; z++)
            for (int u = u1; u <= u2; u++) {
                g.set(u, y0 - 1, z, wood ? "minecraft:spruce_planks" : "minecraft:polished_andesite");
                boolean back = u == u2, gable = z == z1 || z == z2, front = u == u1;
                boolean corner = (back || front) && gable;
                for (int y = y0; y <= y1; y++) {
                    if (corner || (front && Math.floorMod(z - z1, 4) == 0)) { g.set(u, y, z, postY); continue; }
                    if (back) {
                        boolean win = y == y0 + 2 && Math.floorMod(z - z1, 4) == 2;
                        g.set(u, y, z, win ? "minecraft:glass_pane" : (y == y0 ? (wood ? "minecraft:cobblestone" : "minecraft:stone_bricks") : w.at(-u, y, z)));
                    } else if (gable) {
                        boolean open = u > u1 + 1 && u < u2 - 1 && y <= y0 + 2;       // grande porte de pignon
                        if (!open) g.set(u, y, z, y == y0 ? (wood ? "minecraft:cobblestone" : "minecraft:stone_bricks") : w.at(-u, y, z));
                    }
                }
                if (front) g.set(u, y1, z, postZ);   // sabliere
            }
        // toit : pignons au nord et au sud, faitage le long de z ? -> pans vers u1 et u2 (faitage le long de z)
        RoofStyle rs = new RoofStyle().stairs(roofStairs).overhang(1);
        Roof.build(g.s, rs, Roof.gableZ(-u2, z1, -u1, z2, y1));
    }

    void crates(Gen g, int u, int y, int z, int n) {
        for (int i = 0; i < n; i++) {
            int du = i % 2, dz = i / 2;
            g.set(u + du, y, z + dz, "minecraft:spruce_planks");
            g.set(u + du, y + 1, z + dz, i == 0 ? "minecraft:spruce_planks" : B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        }
        g.set(u, y + 2, z, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
    }

    // ---------------------------------------------------------------- grue de port (fleche le long du quai)
    void crane(Gen g, int u, int z, Dir along, int top) {
        for (int du = -1; du <= 1; du++)
            for (int dz = -1; dz <= 1; dz++) {
                g.set(u + du, DECK + 1, z + dz, Math.abs(du) + Math.abs(dz) == 2 ? Gen.slab("stone_brick_slab", false) : "minecraft:stone_bricks");
            }
        for (int y = DECK + 2; y <= top; y++) g.set(u, y, z, B.axis("spruce_log", 'y'));
        // jambes de force
        g.set(u + 1, DECK + 2, z, Gen.stairs("spruce_stairs", Dir.WEST, false));
        g.set(u - 1, DECK + 2, z, Gen.stairs("spruce_stairs", Dir.EAST, false));
        // fleche le long de z
        int len = 8;
        for (int k = -2; k <= len; k++) g.set(u, top, z + along.dz * k, B.axis("stripped_spruce_log", 'z'));
        for (int k = 1; k <= 3; k++) g.set(u, top - k, z + along.dz * k, Gen.stairs("spruce_stairs", along.opposite(), true));
        g.set(u, top + 1, z, "minecraft:spruce_fence");
        g.set(u, top + 2, z, B.state("lantern", "hanging", "false"));
        // contrepoids
        g.set(u, top - 1, z - along.dz * 2, "minecraft:cobblestone");
        // crochet et caisse suspendue
        int hz = z + along.dz * (len - 1);
        for (int y = top - 1; y >= DECK + 6; y--) g.set(u, y, hz, B.axis("iron_chain", 'y'));
        g.set(u, DECK + 5, hz, B.state("barrel", "facing", "up"));
    }

    // ---------------------------------------------------------------- fortin a canons au fond
    void fort(Gen g) {
        int u1 = 30, u2 = 36, z1 = -8, z2 = 8, f = 18;
        for (int z = z1; z <= z2; z++)
            for (int u = u1; u <= Math.min(u2, back(z)); u++) {
                for (int y = 15; y < f; y++) g.set(u, y, z, quayWall);
                int ub = Math.min(u2, back(z));
                if (u == u1 || z == z1 || z == z2 || u == ub) {
                    Dir back = u == u1 ? Gen.OUT : u == ub ? Gen.IN : z == z1 ? Dir.SOUTH : Dir.NORTH;
                    g.set(u, f - 1, z, Gen.stairs("stone_brick_stairs", back, true));   // cordon sous le chemin de ronde
                }
                g.set(u, f, z, g.nz(u, z + 70, 4) > 0.2 ? "minecraft:andesite" : "minecraft:stone");
                boolean edge = u == u1 || z == z1 || z == z2 || u == Math.min(u2, back(z));
                if (edge) {
                    boolean merlon = Math.floorMod(u + z, 3) != 0;
                    g.set(u, f + 1, z, merlon ? "minecraft:stone_bricks" : Gen.slab("stone_brick_slab", false));
                    if (merlon && u == u1) g.set(u, f + 2, z, Gen.slab("stone_brick_slab", false));
                }
            }
        // escalier d'acces (face avant, u 31 -> fortin)
        for (int k = 0; k <= 1; k++)
            for (int dz = -1; dz <= 1; dz++) {
                g.set(u1 + k, 17 + k, dz, Gen.stairs("stone_brick_stairs", Gen.OUT, false));
                if (k == 1) g.set(u1 + k, 17, dz, "minecraft:stone_bricks");
                g.clear(u1 + k, 18 + k, dz, u1 + k, 21 + k, dz);
            }
        g.set(u1, f + 1, -2, "minecraft:stone_bricks");
        g.set(u1, f + 1, 2, "minecraft:stone_bricks");
        // canons vers le milieu
        for (int z : new int[]{-6, 6}) {
            g.set(u1 + 1, f + 1, z, B.axis("polished_basalt", 'x'));
            g.set(u1 + 2, f + 1, z, B.axis("polished_basalt", 'x'));
            g.set(u1 + 3, f + 1, z, "minecraft:coal_block");
            g.set(u1 + 2, f + 1, z - 1, B.trapdoor("dark_oak_trapdoor", Dir.NORTH, false, true));
            g.set(u1 + 2, f + 1, z + 1, B.trapdoor("dark_oak_trapdoor", Dir.SOUTH, false, true));
            g.set(u1 + 3, f + 1, z + 2, "minecraft:polished_blackstone_pressure_plate");
        }
        // tourelle ronde au fond : point haut du camp (toit-terrasse en f+6, pieds en 25 = +13 au-dessus du quai)
        int tc = 34, tz = 0, roof = f + 6;
        for (int y = f + 1; y <= roof; y++)
            for (int du = -2; du <= 2; du++)
                for (int dz = -2; dz <= 2; dz++) {
                    double d = Math.sqrt(du * du + dz * dz);
                    if (d > 2.5) continue;
                    if (y == roof) { g.set(tc + du, y, tz + dz, d > 1.5 ? "minecraft:stone_bricks" : "minecraft:spruce_planks"); continue; }
                    if (d > 1.5) {
                        boolean slit = (y == f + 4 || y == f + 5) && du == 0 && Math.abs(dz) == 2;
                        g.set(tc + du, y, tz + dz, slit ? B.AIR : quayWall.at(-(tc + du), y, tz + dz));
                    } else g.set(tc + du, y, tz + dz, B.AIR);
                }
        // machicoulis : corniche en escaliers retournes sous le toit, merlons alternes
        for (int du = -3; du <= 3; du++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(du * du + dz * dz);
                if (d > 2.5 && d <= 3.3 && (Math.abs(du) == 3 || Math.abs(dz) == 3)) {
                    Dir out = Math.abs(du) >= Math.abs(dz) ? (du > 0 ? Gen.OUT : Gen.IN) : (dz > 0 ? Dir.SOUTH : Dir.NORTH);
                    if (tc + du > 0) g.set(tc + du, roof, tz + dz, Gen.stairs("stone_brick_stairs", out.opposite(), true));
                }
                if (d > 1.5 && d <= 2.5) g.set(tc + du, roof + 1, tz + dz, Math.floorMod(du + dz, 2) == 0 ? "minecraft:stone_brick_wall" : Gen.slab("stone_brick_slab", false));
            }
        // porte (cote fortin), meurtriere face au milieu, echelle interieure jusqu'au toit
        g.clear(tc - 2, f + 1, 0, tc - 2, f + 3, 0);
        g.set(tc - 2, f + 5, 0, B.AIR);
        g.ladder(tc + 1, 0, f + 1, roof, Gen.IN, null);
        g.hangTo(tc - 1, f + 4, -1, false);
        // mat et pavillon sur le toit
        for (int y = roof + 1; y <= roof + 6; y++) g.set(tc - 1, y, 1, "minecraft:spruce_fence");
        for (int y = roof + 5; y <= roof + 6; y++) for (int z = 2; z <= 4; z++) g.set(tc - 1, y, z, "minecraft:TEAM_wool");
        g.set(tc, roof + 1, -1, B.state("lantern", "hanging", "false"));
        g.set(tc - 1, roof + 1, -1, B.state("barrel", "facing", "up"));
        // pile de boulets et caisse de poudre
        g.set(u1 + 3, f + 1, -4, "minecraft:coal_block");
        g.set(u1 + 4, f + 1, -4, Gen.slab("blackstone_slab", false));
        g.set(u1 + 3, f + 1, 4, B.state("barrel", "facing", "up"));
        g.set(u1 + 4, f + 1, 4, B.state("barrel", "facing", "east"));
    }

    // ---------------------------------------------------------------- accessoires du quai et de la place
    void quayProps(Gen g) {
        // parapet du quai : muret bas, bornes pleines tous les 5 (couverts), ouvert aux passerelles et a l'escalier
        for (int z = -30; z <= 30; z++) {
            if (z == -6 || z == -5 || z == 7 || z == 8 || Math.abs(z) == 22 || Math.abs(z) == 23) continue;
            if (Math.floorMod(z, 5) == 0) {
                g.set(16, DECK + 1, z, "minecraft:stone_bricks");
                g.set(16, DECK + 2, z, Math.floorMod(z, 10) == 0 ? B.state("lantern", "hanging", "false") : Gen.slab("stone_brick_slab", false));
            } else if (Math.floorMod(z, 5) != 3) g.set(16, DECK + 1, z, "minecraft:stone_brick_wall");
        }
        // bollards (bittes d'amarrage) et cordage vers le navire
        for (int z = -28; z <= 28; z += 8) {
            if (Math.abs(z) <= 5) continue;
            g.set(17, DECK + 1, z, "minecraft:polished_blackstone_wall");
            g.set(17, DECK + 2, z, Gen.slab("polished_blackstone_slab", false));
        }
        // reverberes
        for (int z = -28; z <= 28; z += 14) {
            if (Math.abs(z) <= 6) continue;
            g.lanternPost(19, DECK + 1, z, 3, "minecraft:dark_oak_fence", false);
            g.set(19, DECK + 1, z, "minecraft:cobblestone_wall");
        }
        g.lanternPost(23, DECK + 1, 7, 3, "minecraft:dark_oak_fence", false);
        g.lanternPost(23, DECK + 1, -7, 3, "minecraft:dark_oak_fence", false);
        // torches sur les pilastres du mur de soutenement
        for (int z = -27; z <= 27; z++) {
            if (Math.floorMod(z, 6) != 3 || Math.abs(Math.abs(z) - 14) <= 2 || Math.abs(z) > zend(29) - 3) continue;
            g.setIfAir(28, DECK + 3, z, B.state("wall_torch", "facing", Gen.IN.id()));
        }
        // filets et casiers
        g.set(18, DECK + 1, 14, "minecraft:spruce_trapdoor");
        g.set(18, DECK + 1, 15, B.state("barrel", "facing", "up"));
        g.set(19, DECK + 1, 15, "minecraft:white_carpet");
        g.set(18, DECK + 1, -14, "minecraft:cauldron");
        g.set(19, DECK + 1, -14, B.state("barrel", "facing", "north"));
        g.set(18, DECK + 1, -12, "minecraft:spruce_planks");
        g.set(18, DECK + 2, -12, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        // couverts bas sur le quai, cote mer (caisses empilees 1-2 de haut)
        int[][] stacks = {{18, 20, 2}, {18, -21, 2}, {20, 10, 1}, {20, -10, 1}, {17, 29, 1}, {17, -29, 1}};
        for (int[] c : stacks) {
            for (int k = 0; k < c[2]; k++) g.set(c[0], DECK + 1 + k, c[1], k == c[2] - 1 ? B.state("barrel", "facing", "up") : "minecraft:spruce_planks");
            g.set(c[0], DECK + 1, c[1] + 1, B.trapdoor("spruce_trapdoor", Dir.NORTH, false, false));
        }
        // puits de la place
        int pu = 21, pz = -8;
        for (int du = -1; du <= 1; du++)
            for (int dz = -1; dz <= 1; dz++) {
                if (du == 0 && dz == 0) { g.set(pu, DECK, pz, B.WATER); continue; }
                g.set(pu + du, DECK + 1, pz + dz, Math.abs(du) + Math.abs(dz) == 2 ? "minecraft:cobblestone_wall" : Gen.slab("cobblestone_slab", false));
            }
        g.set(pu, DECK - 1, pz, "minecraft:stone");
        for (int du = -1; du <= 1; du += 2) for (int dz = -1; dz <= 1; dz += 2) g.set(pu + du, DECK + 2, pz + dz, "minecraft:spruce_fence");
        for (int du = -1; du <= 1; du++) for (int dz = -1; dz <= 1; dz++) g.set(pu + du, DECK + 3, pz + dz, Gen.slab("spruce_slab", false));
        // palmiers de la terrasse et des plages
        int[][] palms = {{32, 17, 7}, {33, -16, 8}, {31, 26, 6}, {32, -25, 7}, {23, 32, 6}, {27, -33, 7}};
        for (int[] p : palms) {
            int u = p[0], z = p[1];
            int y = topY(g, u, z);
            if (y < 0) continue;
            g.palm(u, y + 1, z, p[2], z > 0 ? Dir.SOUTH : Dir.NORTH, seed() + u * 31 + z);
        }
        // barque sur la plage (nord)
        int bu = 20, bz = 33;
        int by = topY(g, bu, bz) + 1;
        if (by > 0) {
            for (int k = -2; k <= 2; k++) {
                g.set(bu, by, bz + k, Math.abs(k) == 2 ? Gen.stairs("spruce_stairs", k > 0 ? Dir.NORTH : Dir.SOUTH, false) : Gen.slab("spruce_slab", false));
                if (Math.abs(k) < 2) {
                    g.set(bu - 1, by, bz + k, Gen.stairs("spruce_stairs", Dir.EAST, false));
                    g.set(bu + 1, by, bz + k, Gen.stairs("spruce_stairs", Dir.WEST, false));
                }
            }
        }
    }

    /** Y du plus haut bloc non vide de la colonne (ou -1). */
    static int topY(Gen g, int u, int z) {
        for (int y = 40; y >= 0; y--) if (!B.isAir(g.get(u, y, z)) && !B.name(g.get(u, y, z)).equals("water")) return y;
        return -1;
    }

    // ---------------------------------------------------------------- decor propre a chaque camp
    public void decor(Gen g, int side) {
        for (int z = -ZMAX; z <= ZMAX; z++)
            for (int u = 18; u <= 37; u++) {
                int y = topY(g, u, z);
                if (y < 0) continue;
                String top = B.name(g.get(u, y, z));
                double v = g.n.noise2(u / 4.0, z / 4.0);
                double hh = g.h(u, y, z);
                if (u >= 18 && u <= 26 && Math.abs(z) <= 5) continue;   // places de depart
                if (top.equals("grass_block")) {
                    if (v > 0.25 && hh < 0.45) g.plant(u, y + 1, z, hh < 0.12 ? "minecraft:fern" : "minecraft:short_grass");
                    else if (v < -0.5 && hh < 0.18) g.plant(u, y + 1, z, hh < 0.06 ? "minecraft:poppy" : "minecraft:dandelion");
                } else if (top.equals("sand") && hh < 0.025) {
                    if (g.air(u, y + 1, z)) g.set(u, y + 1, z, "minecraft:dead_bush");
                } else if (y == DECK && u >= 17 && g.n.noise2(u / 6.0 + 40, z / 6.0) > 0.5 && hh < 0.3) {
                    // cordages, filets et caisses legeres sur le quai et la place (grappes)
                    g.debris(u, y + 1, z, new String[]{"minecraft:brown_carpet", "minecraft:spruce_slab", "minecraft:spruce_trapdoor", "minecraft:white_carpet"});
                }
            }
    }
}
