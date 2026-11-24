package fr.eemicraft.spleef;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dessin du decor d'une arene de Spleef, en memoire (aucun acces au monde) : coordonnees relatives au centre
 * de l'arene en x/z, y absolu.
 *
 * Gabarit v2 (17/11/2026, demande d'Esteban : Â« une grande map tout droit avec que de la neige Â») : un seul terrain
 * de neige plat et rectangulaire (37 x 25, pose a part a chaque partie), enfonce comme un stade. Mur de glace de
 * 4 blocs tout autour (on ne sort pas), fosse de 13 blocs sous la neige avec un lit de lave au fond, promenade
 * enneigee sur le bord, gradins au nord, deux obelisques de glace aux extremites (reperes), sapins sur les pentes,
 * chalet d'attente au sud, baie vitree tournee vers le terrain.
 *
 * Regles de jeu respectees par le decor : rien au-dessus du terrain, aucun renfoncement dans le mur ou un joueur
 * pourrait se poser, la lave n'est jamais atteinte (elimine 3 blocs sous la neige).
 */
public final class Builder {
    /** Version du decor : un changement reconstruit le decor de toutes les arenes. */
    public static final int VERSION = 2;
    /** Sol du terrain de neige. */
    public static final int TOP = 100;
    /** Demi-cotes du terrain de neige (x, z). */
    public static final int HX = 18, HZ = 12;
    /** Plus grand demi-cote (compatibilite avec l'ancien code). */
    public static final int MAX_R = HX;
    /** Demi-cote de la zone du decor (nettoyage et chunks charges). */
    public static final int AREA = 50;
    /** Sol du chalet d'attente (la promenade est a TOP + 4). */
    public static final int WAIT_Y = TOP + 5;
    /** Chalet : x -7..7, z WZ1..WZ2 (au sud, derriere la promenade). */
    public static final int WZ1 = -31, WZ2 = -21;
    /** Haut du mur et de la promenade (bloc plein). */
    static final int RIM = TOP + 3;

    final Map<Long, String> blocks = new HashMap<>();
    final Map<String, Integer> palette = new LinkedHashMap<>();
    final Noise noise = new Noise(7311L);
    final int lava;

    private Builder(int layers, int gap) {
        this.lava = lavaY(layers, gap);
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    public static int kx(long k) {
        return (int) (k >>> 40) - 512;
    }

    public static int ky(long k) {
        return (int) ((k >>> 20) & 0xFFFFF) - 512;
    }

    public static int kz(long k) {
        return (int) (k & 0xFFFFF) - 512;
    }

    void set(int x, int y, int z, String s) {
        blocks.put(key(x, y, z), s);
        palette.merge(s, 1, Integer::sum);
    }

    String get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    void air(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    public Map<Long, String> blocks() {
        return blocks;
    }

    public int states() {
        return (int) blocks.values().stream().distinct().count();
    }

    /** Surface de la lave : 13 blocs sous l'etage du bas. */
    public static int lavaY(int layers, int gap) {
        return TOP - (layers - 1) * gap - 13;
    }

    /** Distance (Chebyshev) au mur : 0 = mur, < 0 = terrain, > 0 = dehors. */
    static int ring(int x, int z) {
        return Math.max(Math.abs(x) - HX - 1, Math.abs(z) - HZ - 1);
    }

    static boolean inStands(int x, int z) {
        return z >= HZ + 4 && z <= HZ + 12 && Math.abs(x) <= HX + 3;
    }

    static boolean inChalet(int x, int z) {
        return Math.abs(x) <= 10 && z >= WZ1 - 3 && z <= WZ2 + 3;
    }

    /** Dessine tout le decor. */
    public static Builder build(int layers, int gap) {
        Builder b = new Builder(layers, gap);
        b.ground();
        b.pit();
        b.wallRing();
        b.stands();
        b.obelisk(HX + 10, 0);
        b.obelisk(-HX - 10, 0);
        b.trees();
        b.chalet();
        return b;
    }

    // ------------------------------------------------------------------ terrain autour

    /** Haut (bloc plein) du terrain au point (x, z), dehors du mur. */
    int surface(int x, int z) {
        int d = ring(x, z);
        int s = RIM;
        if (d > 3) {
            double t = Math.min(1, (d - 3) / 12.0);
            double h = t * (3 + 4 * (noise.fbm2(x / 16.0, z / 16.0, 3, 0.5, 2.0) + 0.5));
            s = RIM + (int) Math.round(h);
        }
        int edge = Math.max(Math.abs(x), Math.abs(z));
        if (edge > AREA - 7) {
            s -= (edge - (AREA - 7)) * 3;
        }
        return s;
    }

    /** Collines enneigees autour du stade (strates de roche, neige en grappes, affleurements). */
    void ground() {
        for (int x = -AREA; x <= AREA; x++) {
            for (int z = -AREA; z <= AREA; z++) {
                if (ring(x, z) <= 0) {
                    continue;
                }
                int top = surface(x, z);
                if (top <= lava - 3) {
                    continue;
                }
                for (int y = lava - 3; y <= top; y++) {
                    set(x, y, z, rock(x, y, z, top));
                }
                int d = ring(x, z);
                double crag = noise.noise2(x / 7.0 + 200, z / 7.0);
                if (d <= 3) {
                    // promenade : neige tassee, chemin de pierre polie au milieu
                    set(x, top, z, d == 2 && noise.noise2(x / 5.0, z / 5.0) > -0.35 ? "minecraft:polished_andesite" : "minecraft:snow_block");
                    double s = noise.noise2(x / 4.0 + 100, z / 4.0);
                    if (d != 2 && s > 0.3) {
                        set(x, top + 1, z, "minecraft:snow[layers=1]");
                    }
                } else if (top > RIM + 2 && crag > 0.5) {
                    // affleurements rocheux sans neige, en grappes
                    set(x, top, z, crag > 0.62 ? "minecraft:stone" : "minecraft:andesite");
                    set(x, top + 1, z, crag > 0.66 ? "minecraft:cobblestone" : "minecraft:snow[layers=1]");
                } else {
                    set(x, top, z, "minecraft:snow_block");
                    double s = noise.noise2(x / 4.0 + 100, z / 4.0);
                    if (s > 0.15) {
                        set(x, top + 1, z, "minecraft:snow[layers=" + (s > 0.6 ? 3 : s > 0.4 ? 2 : 1) + "]");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ fosse de lave

    /** Sous le terrain : vide jusqu'au lit de lave (lave sur 2, magma et blackstone dessous, ilots de basalte). */
    void pit() {
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                double n = noise.noise2(x / 5.0, z / 5.0);
                set(x, lava - 3, z, "minecraft:blackstone");
                set(x, lava - 2, z, n > 0.3 ? "minecraft:magma_block" : "minecraft:blackstone");
                set(x, lava - 1, z, "minecraft:lava");
                set(x, lava, z, n > 0.6 ? "minecraft:basalt[axis=y]" : "minecraft:lava");
            }
        }
    }

    // ------------------------------------------------------------------ mur du stade

    /**
     * Anneau de mur (x = +-(HX+1) ou z = +-(HZ+1)) : roche sous le terrain, puis face visible en 3 bandes
     * (socle d'andesite polie, glace tassee avec pilastres de glace bleue tous les 6, chaperon de quartz),
     * garde-corps en sapin et lanternes sur poteau par-dessus. Face pleine : aucun appui pour un joueur.
     */
    void wallRing() {
        for (int x = -HX - 1; x <= HX + 1; x++) {
            for (int z = -HZ - 1; z <= HZ + 1; z++) {
                if (ring(x, z) != 0) {
                    continue;
                }
                for (int y = lava - 3; y < TOP; y++) {
                    set(x, y, z, rock(x, y, z, TOP + 20));
                }
                boolean corner = Math.abs(x) == HX + 1 && Math.abs(z) == HZ + 1;
                int along = Math.abs(x) == HX + 1 ? z : x;
                boolean pilaster = corner || Math.floorMod(along, 6) == 0;
                set(x, TOP, z, "minecraft:polished_andesite");
                for (int y = TOP + 1; y <= TOP + 2; y++) {
                    set(x, y, z, pilaster ? "minecraft:blue_ice" : "minecraft:packed_ice");
                }
                set(x, RIM, z, pilaster ? "minecraft:chiseled_quartz_block" : "minecraft:smooth_quartz");
                // garde-corps relie a ses voisins de l'anneau
                boolean n = ring(x, z - 1) == 0, s = ring(x, z + 1) == 0, w = ring(x - 1, z) == 0, e = ring(x + 1, z) == 0;
                set(x, RIM + 1, z, "minecraft:spruce_fence[east=" + e + ",north=" + n + ",south=" + s + ",west=" + w + "]");
                if (pilaster && !corner) {
                    set(x, RIM + 2, z, "minecraft:lantern[hanging=false]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ gradins

    /**
     * Gradins au nord (face au terrain) : 7 rangs qui montent d'un bloc, banquettes en escaliers de chene noir,
     * allees tous les 9, mur de fond a poteaux de sapin, auvent en dalles sur consoles, lanternes suspendues.
     */
    void stands() {
        int z0 = HZ + 5;
        int rows = 7;
        int x1 = -HX - 2, x2 = HX + 2;
        for (int x = x1; x <= x2; x++) {
            boolean aisle = Math.floorMod(x, 9) == 0;
            for (int k = 0; k < rows; k++) {
                int z = z0 + k;
                int seat = RIM + 1 + k;
                for (int y = lava; y < seat; y++) {
                    set(x, y, z, y >= seat - 2 ? "minecraft:stone_bricks" : rock(x, y, z, seat + 10));
                }
                for (int y = seat + 1; y <= seat + 4; y++) {
                    air(x, y, z);
                }
                if (aisle) {
                    set(x, seat, z, "minecraft:polished_andesite");
                } else {
                    set(x, seat, z, "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight]");
                }
            }
            // mur de fond
            int zb = z0 + rows;
            int top = RIM + rows + 5;
            for (int y = lava; y <= top; y++) {
                boolean post = Math.floorMod(x, 6) == 3 || x == x1 || x == x2;
                String m;
                if (y < RIM + rows) {
                    m = rock(x, y, zb, top + 10);
                } else if (post) {
                    m = "minecraft:stripped_spruce_log[axis=y]";
                } else if (y == top) {
                    m = "minecraft:dark_oak_planks";
                } else {
                    m = noise.noise3(x / 5.0, y / 3.0, zb / 5.0) > 0.35 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
                }
                set(x, y, zb, m);
            }
            // auvent : dalles de chene noir sur 4 rangs, consoles sous les poteaux
            for (int k = rows - 4; k < rows; k++) {
                set(x, top, z0 + k, "minecraft:dark_oak_slab[type=bottom]");
            }
            if (Math.floorMod(x, 6) == 3) {
                set(x, top - 1, zb - 1, "minecraft:dark_oak_stairs[facing=south,half=top,shape=straight]");
                set(x, top - 1, z0 + rows - 3, "minecraft:iron_chain[axis=y]");
                set(x, top - 2, z0 + rows - 3, "minecraft:lantern[hanging=true]");
            }
        }
        // flancs des gradins (pignons en escalier, pas de vide visible sur les cotes)
        for (int side : new int[] {x1 - 1, x2 + 1}) {
            for (int k = 0; k <= rows; k++) {
                int z = z0 + k;
                int h = k == rows ? RIM + rows + 5 : RIM + 1 + k;
                for (int y = lava; y <= h; y++) {
                    set(side, y, z, y > RIM ? "minecraft:stone_bricks" : rock(side, y, z, h + 10));
                }
                set(side, h + 1, z, "minecraft:stone_brick_wall");
            }
        }
    }

    // ------------------------------------------------------------------ obelisques

    /** Obelisque de glace (repere) : socle de blackstone poli a 2 gradins, fut effile en bandes, pointe en glace bleue. */
    void obelisk(int ox, int oz) {
        int g = surface(ox, oz);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int y0 = Math.min(g, surface(ox + dx, oz + dz));
                for (int y = y0; y <= g + 1; y++) {
                    set(ox + dx, y, oz + dz, "minecraft:polished_blackstone_bricks");
                }
                if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                    set(ox + dx, g + 2, oz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2
                        ? "minecraft:polished_blackstone_brick_slab[type=bottom]" : "minecraft:chiseled_polished_blackstone");
                }
                if ((Math.abs(dx) == 3 || Math.abs(dz) == 3) && ((dx + dz) & 1) == 0) {
                    set(ox + dx, g + 2, oz + dz, "minecraft:polished_blackstone_wall");
                }
            }
        }
        int h = 17;
        for (int k = 0; k < h; k++) {
            int y = g + 3 + k;
            double r = 1.6 - k * 1.25 / h;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= r) {
                        set(ox + dx, y, oz + dz, k % 5 == 4 ? "minecraft:blue_ice" : "minecraft:packed_ice");
                    }
                }
            }
        }
        set(ox, g + 3 + h, oz, "minecraft:blue_ice");
        set(ox, g + 4 + h, oz, "minecraft:end_rod[facing=up]");
        // lanternes au pied
        for (int[] c : new int[][] {{3, 3}, {-3, -3}, {3, -3}, {-3, 3}}) {
            set(ox + c[0], g + 2, oz + c[1], "minecraft:polished_blackstone_wall");
            set(ox + c[0], g + 3, oz + c[1], "minecraft:lantern[hanging=false]");
        }
    }

    // ------------------------------------------------------------------ sapins sur les pentes

    void trees() {
        int planted = 0;
        for (int i = 0; i < 600 && planted < 26; i++) {
            int x = (int) Math.round((Noise.hash(91, i, 0, 0) * 2 - 1) * (AREA - 8));
            int z = (int) Math.round((Noise.hash(91, i, 1, 0) * 2 - 1) * (AREA - 8));
            if (ring(x, z) < 7 || inStands(x, z) || inChalet(x, z) || Math.abs(z) < 6 && Math.abs(Math.abs(x) - HX - 10) < 6) {
                continue;
            }
            int top = surface(x, z);
            if (!"minecraft:snow_block".equals(get(x, top, z))) {
                continue;
            }
            boolean clear = true;
            for (int dx = -2; dx <= 2 && clear; dx++) {
                for (int dz = -2; dz <= 2 && clear; dz++) {
                    String o = get(x + dx, top + 2, z + dz);
                    if (o != null && !o.startsWith("minecraft:snow[")) {
                        clear = false;
                    }
                }
            }
            if (!clear) {
                continue;
            }
            spruce(x, top + 1, z, 6 + (int) (Noise.hash(91, i, 2, 0) * 5));
            planted++;
        }
    }

    /** Roche par strates bruitees : jamais tiree bloc par bloc. */
    String rock(int x, int y, int z, int top) {
        double band = (y - lava) + noise.noise3(x / 7.0, y / 4.0, z / 7.0) * 3.5;
        if (top - y <= 1) {
            return "minecraft:packed_ice";
        }
        if (band < 4) {
            return "minecraft:blackstone";
        }
        if (band < 10) {
            return "minecraft:deepslate";
        }
        if (band < 17) {
            return noise.noise3(x / 6.0, y / 3.0, z / 6.0) > 0.25 ? "minecraft:tuff" : "minecraft:andesite";
        }
        if (band < 27) {
            return noise.noise3(x / 6.0 + 30, y / 3.0, z / 6.0) > 0.3 ? "minecraft:packed_ice" : "minecraft:stone";
        }
        return noise.noise3(x / 5.0, y / 3.0, z / 5.0 + 50) > 0.2 ? "minecraft:calcite" : "minecraft:diorite";
    }


    void spruce(int x, int y, int z, int h) {
        air(x, y, z);
        for (int k = 0; k < h; k++) {
            set(x, y + k, z, "minecraft:spruce_log[axis=y]");
        }
        // couronnes de feuilles en etages decroissants
        for (int k = 2; k <= h + 1; k++) {
            int ly = y + k;
            double r = Math.max(0.6, (h + 1 - k) * 0.55 + ((k % 2 == 0) ? 0.7 : 0));
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if ((dx != 0 || dz != 0 || k > h - 1) && dx * dx + dz * dz <= r * r + 0.3 && get(x + dx, ly, z + dz) == null) {
                        set(x + dx, ly, z + dz, "minecraft:spruce_leaves[persistent=true]");
                    }
                }
            }
        }
        // neige sur les feuilles du dessus de chaque couronne
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int ly = y + h + 2; ly >= y + 2; ly--) {
                    if ("minecraft:spruce_leaves[persistent=true]".equals(get(x + dx, ly, z + dz))) {
                        if (get(x + dx, ly + 1, z + dz) == null) {
                            set(x + dx, ly + 1, z + dz, "minecraft:snow[layers=1]");
                        }
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ chalet d'attente

    /**
     * Chalet 15 x 11 (x -7..7, z -48..-38), sol en y 120 : socle de pierre sur la paroi, ossature en sapin ecorce
     * (poteaux en saillie), remplissage en planches, baie vitree au nord-sud face a l'arene, toit a deux pans avec
     * debord, interieur (banquettes, poele, tapis, lanternes).
     */
    void chalet() {
        int x1 = -7, x2 = 7, z1 = WZ1, z2 = WZ2, f = WAIT_Y;
        // vide le volume (la paroi peut y monter) avec un peu de marge
        for (int x = x1 - 2; x <= x2 + 2; x++) {
            for (int z = z1 - 2; z <= z2 + 2; z++) {
                for (int y = f; y <= f + 12; y++) {
                    air(x, y, z);
                }
            }
        }
        // socle : de la paroi jusqu'au plancher, pierre en bas, briques de pierre en haut
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int y = f - 1;
                while (y > lava && get(x, y, z) == null) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    String m;
                    if (y == f - 1) {
                        m = edge ? "minecraft:stone_bricks" : "minecraft:spruce_planks";
                    } else if (f - y <= 3) {
                        m = noise.noise3(x / 3.0, y / 3.0, z / 3.0) > 0.3 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
                    } else {
                        m = noise.noise3(x / 4.0, y / 4.0, z / 4.0) > 0.1 ? "minecraft:cobblestone" : "minecraft:stone";
                    }
                    set(x, y, z, m);
                    y--;
                }
                set(x, f - 1, z, (x == x1 || x == x2 || z == z1 || z == z2) ? "minecraft:stone_bricks" : "minecraft:spruce_planks");
            }
        }
        // corniche du socle (escaliers retournes) sur les 4 cotes
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            set(x, f - 1, z1 - 1, "minecraft:stone_brick_stairs[facing=south,half=top]");
            set(x, f - 1, z2 + 1, "minecraft:stone_brick_stairs[facing=north,half=top]");
        }
        for (int z = z1; z <= z2; z++) {
            set(x1 - 1, f - 1, z, "minecraft:stone_brick_stairs[facing=east,half=top]");
            set(x2 + 1, f - 1, z, "minecraft:stone_brick_stairs[facing=west,half=top]");
        }
        // murs : 5 de haut (f..f+4)
        for (int y = f; y <= f + 4; y++) {
            for (int x = x1; x <= x2; x++) {
                wall(x, y, z1, x == x1 || x == x2 || x % 4 == 0, true, false);
                wall(x, y, z2, x == x1 || x == x2 || x % 4 == 0, true, true);
            }
            for (int z = z1 + 1; z < z2; z++) {
                wall(x1, y, z, (z - z1) % 5 == 0, false, false);
                wall(x2, y, z, (z - z1) % 5 == 0, false, false);
            }
        }
        // poteaux d'angle en saillie (3e plan)
        int[][] corners = {{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}};
        for (int[] c : corners) {
            for (int y = f; y <= f + 4; y++) {
                set(c[0], y, c[1], "minecraft:stripped_spruce_log[axis=y]");
            }
        }
        // sabliÃ¨re : poutre en saillie sur les 4 cotes
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            set(x, f + 5, z1 - 1, "minecraft:stripped_spruce_log[axis=x]");
            set(x, f + 5, z2 + 1, "minecraft:stripped_spruce_log[axis=x]");
        }
        for (int z = z1; z <= z2; z++) {
            set(x1 - 1, f + 5, z, "minecraft:stripped_spruce_log[axis=z]");
            set(x2 + 1, f + 5, z, "minecraft:stripped_spruce_log[axis=z]");
        }
        // plafond
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, f + 5, z, "minecraft:spruce_planks");
            }
        }
        roof(x1 - 2, x2 + 2, z1 - 2, z2 + 2, f + 6);
        interior(x1, x2, z1, z2, f);
    }

    /** Une case de mur : poteau, planches ou fenetre en retrait (baie face a l'arene). */
    void wall(int x, int y, int z, boolean post, boolean alongX, boolean front) {
        int f = WAIT_Y;
        if (post) {
            set(x, y, z, "minecraft:stripped_spruce_log[axis=y]");
            return;
        }
        if (y == f || y == f + 4) {
            set(x, y, z, y == f ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks");
            return;
        }
        boolean window = front ? true : (alongX ? (x % 4 != 2) : ((z - WZ1) % 5 == 2 || (z - WZ1) % 5 == 3));
        if (window) {
            set(x, y, z, alongX ? "minecraft:glass_pane[east=true,west=true]" : "minecraft:glass_pane[north=true,south=true]");
        } else {
            set(x, y, z, "minecraft:spruce_planks");
        }
    }

    /** Toit a deux pans, faitage le long de x, debord de 2 (escaliers + dalles au faitage). */
    void roof(int x1, int x2, int z1, int z2, int y0) {
        int mid = (z1 + z2) / 2;
        int half = (z2 - z1) / 2;
        for (int k = 0; k <= half; k++) {
            int y = y0 + k;
            int za = z1 + k;
            int zb = z2 - k;
            for (int x = x1; x <= x2; x++) {
                if (za < zb) {
                    set(x, y, za, "minecraft:dark_oak_stairs[facing=south,half=bottom]");
                    set(x, y, zb, "minecraft:dark_oak_stairs[facing=north,half=bottom]");
                    if (k > 0) {
                        // remplissage sous le pan (pas de vide entre plafond et toit vu des pignons)
                        for (int z = za + 1; z < zb; z++) {
                            if (x > x1 && x < x2) {
                                set(x, y - 1, z, "minecraft:spruce_planks");
                            }
                        }
                    }
                } else if (za == zb) {
                    set(x, y, za, "minecraft:dark_oak_slab[type=bottom]");
                }
            }
            // neige en grappes sur les pans
            for (int x = x1; x <= x2; x++) {
                if (za < zb) {
                    if (noise.noise2(x / 3.0, za / 2.0) > 0.25) {
                        set(x, y + 1, za, "minecraft:snow[layers=1]");
                    }
                    if (noise.noise2(x / 3.0 + 9, zb / 2.0) > 0.25) {
                        set(x, y + 1, zb, "minecraft:snow[layers=1]");
                    }
                }
            }
        }
        // cheminee au fond (cote nord, loin de l'arene)
        for (int y = y0; y <= y0 + half + 3; y++) {
            set(4, y, mid - 2, "minecraft:bricks");
            set(5, y, mid - 2, "minecraft:bricks");
        }
        set(4, y0 + half + 4, mid - 2, "minecraft:campfire[lit=true,signal_fire=false]");
        set(5, y0 + half + 4, mid - 2, "minecraft:brick_slab[type=bottom]");
    }

    void interior(int x1, int x2, int z1, int z2, int f) {
        // tapis central
        for (int x = -3; x <= 3; x++) {
            for (int z = z1 + 3; z <= z2 - 3; z++) {
                set(x, f, z, (x == -3 || x == 3 || z == z1 + 3 || z == z2 - 3) ? "minecraft:red_carpet" : "minecraft:white_carpet");
            }
        }
        // banquettes le long des murs lateraux
        for (int z = z1 + 2; z <= z2 - 2; z++) {
            if (z == z1 + 5) {
                continue;
            }
            set(x1 + 1, f, z, "minecraft:spruce_stairs[facing=east,half=bottom]");
            set(x2 - 1, f, z, "minecraft:spruce_stairs[facing=west,half=bottom]");
        }
        // poele contre le mur du fond (sous la cheminee)
        set(4, f, z1 + 1, "minecraft:blast_furnace[facing=south,lit=false]");
        set(5, f, z1 + 1, "minecraft:blast_furnace[facing=south,lit=false]");
        set(4, f + 1, z1 + 1, "minecraft:brick_slab[type=bottom]");
        set(5, f + 1, z1 + 1, "minecraft:brick_slab[type=bottom]");
        // etagere et tonneaux au fond
        set(-5, f, z1 + 1, "minecraft:barrel[facing=up]");
        set(-4, f, z1 + 1, "minecraft:barrel[facing=up]");
        set(-5, f + 1, z1 + 1, "minecraft:bookshelf");
        set(-3, f, z1 + 1, "minecraft:bookshelf");
        set(-1, f, z1 + 1, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]");
        // lanternes suspendues au plafond
        for (int x : new int[] {-4, 0, 4}) {
            set(x, f + 4, (z1 + z2) / 2, "minecraft:iron_chain[axis=y]");
            set(x, f + 3, (z1 + z2) / 2, "minecraft:lantern[hanging=true]");
        }
        // garde-corps devant la baie (on regarde l'arene sans toucher la vitre)
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            set(x, f, z2 - 1, "minecraft:spruce_fence[east=" + (x < x2 - 2) + ",west=" + (x > x1 + 2) + "]");
        }
    }
}
