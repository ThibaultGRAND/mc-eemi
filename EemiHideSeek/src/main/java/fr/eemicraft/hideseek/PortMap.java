package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.build.B;
import fr.eemicraft.hideseek.build.Connect;
import fr.eemicraft.hideseek.build.Dir;
import fr.eemicraft.hideseek.build.Mat;
import fr.eemicraft.hideseek.build.Noise;
import fr.eemicraft.hideseek.build.Palette;
import fr.eemicraft.hideseek.build.Sink;
import fr.eemicraft.hideseek.build.Stairs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * « Port de peche » (1.5.0) : petit port breton a maree basse, environ 82 x 82 jouables (local 4..85), pose a la
 * place de l'ancienne map EEMI (sa boite est entierement recouverte, donc videe, par celle du port).
 *
 * <p>Du nord au sud : falaise de granit et pins ; terrasse haute (bloc y 68) avec la rue haute, les maisons de
 * pecheurs, la chapelle a clocher-mur, le lavoir, le calvaire, la fumerie et le puits ; mur de soutenement franchi
 * par 4 venelles en escalier ; quai (bloc y 64) avec le chantier naval, le cafe, la criee (gradins et mezzanine),
 * l'entrepot et la capitainerie (tour de guet) ; les batiments du quai ont une porte arriere a l'etage sur la
 * terrasse. Puis le bassin d'echouage (vase en y 60) ou reposent trois bateaux (cales accessibles), une epave, des
 * canots, un ponton sur flotteurs et le mole ; enfin la digue avec le phare et la maison du gardien.
 *
 * <p>Eau : c'est la maree basse, le bassin est de la vase avec des flaques d'UN bloc de profondeur au ras de la vase
 * (fond en y 59, eau en y 60, bordees de blocs pleins : elles ne coulent jamais). On n'y noie personne et le plugin
 * refuse deja qu'un cacheur devienne solide dans une case liquide. La mer profonde n'est qu'au-dela de la digue et
 * de l'estacade, hors de la zone de jeu (rideau de barrieres derriere le decor).
 */
public final class PortMap extends HsMap {
    static final int X0 = 348, Z0 = -45;
    static final int MY = 60, QY = 64, UY = 68, SEA = 58, CY = 100;
    static final int LX1 = -8, LX2 = 97, LZ1 = -3, LZ2 = 104;
    static final int P1 = 4, P2 = 85, RING1 = 3, RING2 = 86;
    static final int WX = 480, WY = 100, WZ = 0;
    static final int SX = 505, SZ = 0;

    static final int CLIFF = 0, UPPER = 1, QUAY = 2, BASIN = 3, PASSE = 4, POINT = 5, SEAZ = 6;

    private int[][] top, zone;
    /** Hauteur du terrain (local) gardee apres la construction, pour les mesures hors serveur. */
    private int[][] groundTop, groundZone;
    private Noise nz;
    private Palette rockP, wallP, whiteP, paveP, mudP, roofP, faceP, wetP, grassP, seaRockP, plankP;

    public PortMap() {
        super("port", "Port de p\u00eache", 1);
        nz = new Noise(0x5047L);
        computeTerrain();
        perchTop = top;
        perchZone = zone;
        top = null;
        zone = null;
        nz = null;
    }

    /**
     * Sols surelevés prevus par la map pour la regle anti-perchoir (local x1, z1, x2, z2, bloc du sol) : ponts des trois
     * bateaux pontes du bassin (pont en y MY + 5, passerelles comprises, cf. boats()) et estacade de la passe (y QY).
     */
    private static final int[][] PERCH_GROUND = {
            {22, 49, 38, 57, MY + 5},   // chalutier bleu (30, 54) + passerelle x 29..31
            {51, 49, 63, 55, MY + 5},   // sardinier vert (57, 53) + passerelle x 56..58
            {20, 66, 30, 70, MY + 5},   // caseyeur orange (25, 68)
            {10, 84, 17, 85, QY},       // estacade de la passe
    };

    /** Galerie exterieure du phare (sol y 84, on s'y tient en y 85) : on y monte par l'echelle, c'est un poste prevu. */
    @Override
    public boolean perchAllowed(int x, int y, int z) {
        int dx = x - X0 - 80, dz = z - Z0 - 80;
        return y == 85 && dx * dx + dz * dz <= 5.5 * 5.5;
    }

    /** Terrain calcule une fois a la creation (lecture seule, sans verrou) pour la regle anti-perchoir (1.6.0). */
    private final int[][] perchTop, perchZone;

    /**
     * Sol de reference pour la regle anti-perchoir (1.6.0) : quai, terrasse haute ou vase du bassin sous le joueur.
     * Falaise : niveau de la zone voisine (terrasse au nord, quai ailleurs), jamais le haut du rocher.
     */
    @Override
    public int roofGround(int x, int z) {
        int lx = x - X0, lz = z - Z0;
        if (lx < LX1 || lx > LX2 || lz < LZ1 || lz > LZ2) return Integer.MIN_VALUE;
        for (int[] g : PERCH_GROUND)
            if (lx >= g[0] && lx <= g[2] && lz >= g[1] && lz <= g[3]) return g[4];
        int zn = perchZone[lx - LX1][lz - LZ1], h = perchTop[lx - LX1][lz - LZ1];
        if (zn == CLIFF) return Math.min(h, lz <= 24 ? UY : QY);
        if (zn == SEAZ) return Math.min(h, SEA);
        return h;
    }

    @Override
    public int[] box() {
        // couvre toute l'ancienne boite de la map EEMI (340..459, 58..112, -48..47) : elle est videe en meme temps
        return new int[]{X0 + LX1, 50, Z0 + LZ1, 520, 112, Z0 + LZ2};
    }

    @Override
    public int[] playBox() {
        return new int[]{X0 + P1, MY - 1, Z0 + P1, X0 + P2, CY + 1, Z0 + P2};
    }

    @Override
    public int measureFloorY() { return MY - 1; }

    @Override
    public int measureCeilY() { return CY; }

    /** Hauteur du sol naturel (quai, terrasse, vase) en absolu ; MIN_VALUE pour la falaise et la mer. */
    @Override
    public int groundY(int x, int z) {
        int lx = x - X0, lz = z - Z0;
        if (groundTop == null || lx < LX1 || lx > LX2 || lz < LZ1 || lz > LZ2) return Integer.MIN_VALUE;
        int zn = groundZone[lx - LX1][lz - LZ1];
        if (zn == CLIFF || zn == SEAZ) return Integer.MIN_VALUE;
        return groundTop[lx - LX1][lz - LZ1];
    }

    @Override
    public double[] point(String key) {
        switch (key) {
            case "waiting-spawn": return new double[]{WX + 0.5, WY + 1, WZ + 4.5, 180, 0};
            case "seeker-room": return new double[]{SX + 0.5, WY + 1, SZ + 0.5, 0, 0};
            // cacheurs : sur le quai devant la criee ; chercheurs : rue haute, devant le puits
            case "hider-spawn": return new double[]{X0 + 47.5, QY + 1, Z0 + 47.5, 0, 0};
            case "seeker-spawn": return new double[]{X0 + 45.5, UY + 1, Z0 + 17.5, 0, 0};
            case "spectate": return new double[]{X0 + 45.5, 92, Z0 + 76.5, 180, 35};
            default: return null;
        }
    }

    @Override
    public LinkedHashMap<String, String> disguises() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("BARREL", "Tonneau");
        m.put("SPRUCE_PLANKS", "Caisse en sapin");
        m.put("DARK_OAK_PLANKS", "Bordé de coque");
        m.put("OAK_PLANKS", "Planches de ponton");
        m.put("SPRUCE_LOG", "Pieu en sapin");
        m.put("DRIED_KELP_BLOCK", "Goémon séché");
        m.put("WHITE_WOOL", "Voile pliée");
        m.put("BLUE_WOOL", "Filet bleu");
        m.put("GREEN_WOOL", "Filet vert");
        m.put("ORANGE_WOOL", "Bouée orange");
        m.put("COBBLESTONE", "Pierre");
        m.put("MOSSY_COBBLESTONE", "Pierre moussue");
        m.put("STONE_BRICKS", "Pierre de taille");
        m.put("GRANITE", "Granit rose");
        m.put("CALCITE", "Mur blanchi");
        m.put("DEEPSLATE_TILES", "Ardoises");
        m.put("OXIDIZED_COPPER", "Cuivre oxydé");
        m.put("PRISMARINE", "Rocher aux algues");
        m.put("SAND", "Sable");
        m.put("MUD", "Vase");
        m.put("MOSS_BLOCK", "Mousse");
        m.put("COAL_BLOCK", "Goudron");
        m.put("IRON_BLOCK", "Treuil");
        m.put("COMPOSTER", "Bac à poissons");
        m.put("CAULDRON", "Bassine");
        m.put("SMOKER", "Fumoir");
        m.put("FURNACE", "Fourneau");
        m.put("LANTERN", "Lanterne");
        m.put("CRAFTING_TABLE", "Établi");
        m.put("LOOM", "Métier à ramender");
        m.put("GRINDSTONE", "Meule");
        m.put("CARTOGRAPHY_TABLE", "Table des cartes");
        m.put("BOOKSHELF", "Bibliothèque");
        m.put("SPRUCE_LEAVES", "Aiguilles de pin");
        m.put("FLOWERING_AZALEA_LEAVES", "Hortensia");
        return m;
    }

    // ------------------------------------------------------------------ construction

    @Override
    protected void draw() {
        Sink abs = s;
        nz = new Noise(0x5047L);
        rockP = Palette.of(41, "stone", 50, "andesite", 22, "cobblestone", 14, "tuff", 14).period(7);
        wallP = Palette.of(42, "cobblestone", 66, "granite", 17, "mossy_cobblestone", 17).period(8);
        whiteP = Palette.of(43, "calcite", 88, "diorite", 12).period(6);
        paveP = Palette.of(44, "cobblestone", 62, "stone", 38).period(11);
        mudP = Palette.of(45, "mud", 72, "sand", 28).period(10);
        roofP = Palette.of(46, "deepslate_tile_stairs", 75, "deepslate_brick_stairs", 25).period(5);
        faceP = Palette.of(47, "cobblestone", 65, "stone_bricks", 20, "mossy_cobblestone", 15).period(8);
        wetP = Palette.of(48, "mossy_cobblestone", 60, "cobblestone", 22, "prismarine", 18).period(6);
        grassP = Palette.of(49, "grass_block", 80, "coarse_dirt", 8, "moss_block", 12).period(9);
        seaRockP = Palette.of(50, "mossy_cobblestone", 40, "cobblestone", 25, "prismarine", 20, "stone", 15).period(5);
        plankP = Palette.of(51, "spruce_planks", 70, "dark_oak_planks", 30).period(4);

        List<String> show = new ArrayList<>();
        for (String k : disguises().keySet()) {
            String n = k.toLowerCase();
            show.add(n.endsWith("_leaves") ? B.leaves(n) : n.equals("grindstone") ? "grindstone[face=floor,facing=north]" : n);
        }
        waitingRoom(WX, WY, WZ, 8, "spruce_planks", "oak_planks", "stripped_spruce_log", "cobblestone", "spruce_planks", show);
        // enseigne : bouee et filets au-dessus des vitres du fond
        for (int x = WX - 6; x <= WX + 6; x++) set(x, WY + 6, WZ - 8, (x % 3 == 0) ? "orange_wool" : "blue_wool");
        seekerRoom(SX, WY, SZ);

        s = abs.offset(X0, 0, Z0);
        terrain();
        faces();
        shipyard();
        cafe();
        criee();
        entrepot();
        capitainerie();
        upperHouses();
        chapel();
        terraceSouth();
        lanes();
        harbour();
        lighthouse(80, 80);
        keeperHouse();
        pontoon();
        boats();
        basinDetails();
        quayProps();
        parapets();
        curtain();
        waterCheck();
        Stairs.fix(s, LX1, 50, LZ1, LX2, 112, LZ2);
        Connect.fix(s, LX1, 50, LZ1, LX2, 112, LZ2);
        // hauteurs du sol naturel pour les mesures
        groundTop = top;
        groundZone = zone;
        s = abs;
    }

    // ------------------------------------------------------------------ terrain

    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

    private int lzc(int lx) { return clamp(5 + (int) Math.round(1.3 * nz.noise2(lx / 7.0, 3.1)), 4, 6); }

    private int lxw(int lz) { if (lz >= 25 && lz <= 47) return 4; return clamp(4 + (int) Math.round(0.9 * nz.noise2(5.7, lz / 6.0) + 0.3), 4, 5); }

    private int lxe(int lz) { if (lz <= 37) return 84; return lz >= 74 ? 85 : clamp(85 - (int) Math.round(0.9 * nz.noise2(9.3, lz / 6.0) + 0.3), 84, 85); }

    private boolean inT(int lx, int lz) { return lx >= LX1 && lx <= LX2 && lz >= LZ1 && lz <= LZ2; }

    int topAt(int lx, int lz) { return inT(lx, lz) ? top[lx - LX1][lz - LZ1] : 50; }

    int zoneAt(int lx, int lz) { return inT(lx, lz) ? zone[lx - LX1][lz - LZ1] : SEAZ; }

    private int zoneOf(int lx, int lz) {
        if (lz >= 86) {
            int k = (lz - 86) / 3;
            if (lx <= 6 - k || lx >= 86 + k) return CLIFF;
            return SEAZ;
        }
        if (lx < lxw(lz) || lx > lxe(lz) || lz < lzc(lx)) return CLIFF;
        if (lz <= 24) return UPPER;
        if (lz <= 48) return QUAY;
        if (lz <= 79) {
            if (lx <= 9) return QUAY;
            if (lx >= 81 && lz <= 73) return QUAY;
            if (lx >= 68 && lz >= 74) return QUAY;
            if (lx >= 43 && lx <= 47 && lz <= 68) return QUAY;
            return BASIN;
        }
        if (lx <= 9) return POINT;
        if (lx <= 17) return PASSE;
        return QUAY;
    }

    private int cliffTop(int lx, int lz) {
        int ref = Math.min(lz, 85);
        int dn = lzc(lx) - lz, dw = lxw(ref) - lx, de = lx - lxe(ref);
        if (lz >= 86) { int k = (lz - 86) / 3; dw = Math.max(dw, 7 - k - lx); de = Math.max(de, lx - (85 + k)); }
        int d = Math.max(1, Math.max(dn, Math.max(dw, de)));
        double n = nz.fbm2(lx / 11.0, lz / 11.0, 3, 0.5, 2);
        double base = lz <= 24 ? 68 : lz <= 79 ? 66 : 66 - (lz - 79) * 0.9;
        double h = base + 3 + d * 3.0 + 3.5 * n;
        double plateau = lz <= 79 ? 80 + 2.5 * nz.noise2(lx / 9.0, lz / 9.0) - Math.max(0, lz - 55) * 0.75 : 62 + 2 * n - (lz - 80) * 0.5;
        h = Math.min(h, plateau);
        // dans l'anneau de jeu, la roche reste toujours au-dessus du sol voisin (pas de fosse sans sortie)
        if (lx > RING1 && lx < RING2 && lz > RING1 && lz < RING2) h = Math.max(h, (lz <= 24 ? UY : QY) + 3);
        return clamp((int) Math.round(h), 52, 90);
    }

    private int seaFloor(int lx, int lz) {
        double n = nz.fbm2(lx / 8.0 + 40, lz / 8.0, 3, 0.5, 2);
        double f;
        if (lz <= 88 && lx >= 18) f = 63.5 - (lz - 86) * 1.6 + 1.5 * n;     // enrochement le long de la digue
        else f = 58.4 - (lz - 86) * 0.45 + 1.4 * n;
        // ilot a la balise
        double di = Math.hypot(lx - 44, lz - 98) - 1.2 * nz.noise2(lx / 3.0, lz / 3.0);
        if (di < 5.5) f = Math.max(f, 58 + (5.5 - di) * 1.2);
        if (lz >= 101 + (int) Math.round(2 * nz.noise2(lx / 6.0, 77)) || lx <= -6 || lx >= 95) f = Math.max(f, 58 + (lz >= 103 ? 1 : 0));          // banc de sable au bord de la boite
        return clamp((int) Math.round(f), 51, 66);
    }

    /** Hauteurs et zones du terrain (local), sans rien poser : sert a la construction et a la regle anti-perchoir. */
    private void computeTerrain() {
        int nx = LX2 - LX1 + 1, nzz = LZ2 - LZ1 + 1;
        top = new int[nx][nzz];
        zone = new int[nx][nzz];
        for (int lx = LX1; lx <= LX2; lx++)
            for (int lz = LZ1; lz <= LZ2; lz++) {
                int zn = zoneOf(lx, lz);
                int h;
                switch (zn) {
                    case CLIFF: h = cliffTop(lx, lz); break;
                    case UPPER: h = UY; break;
                    case QUAY: h = QY; break;
                    case BASIN: case PASSE: h = MY; break;
                    case POINT: h = QY + (nz.noise2(lx / 3.0, lz / 3.0) > 0.25 ? 1 : 0); break;
                    default: h = seaFloor(lx, lz);
                }
                zone[lx - LX1][lz - LZ1] = zn;
                top[lx - LX1][lz - LZ1] = h;
            }
    }

    private void terrain() {
        computeTerrain();
        for (int lx = LX1; lx <= LX2; lx++)
            for (int lz = LZ1; lz <= LZ2; lz++) {
                int zn = zoneAt(lx, lz), h = topAt(lx, lz);
                switch (zn) {
                    case CLIFF: {
                        for (int y = 50; y <= h; y++) set(lx, y, lz, rockP.clustered(lx, y, lz));
                        int minN = Math.min(Math.min(topAt(lx + 1, lz), topAt(lx - 1, lz)), Math.min(topAt(lx, lz + 1), topAt(lx, lz - 1)));
                        if (h > SEA + 1 && h - minN <= 1) {
                            set(lx, h, lz, grassP.clustered(lx, h, lz));
                            set(lx, h - 1, lz, "dirt");
                        }
                        if (h < SEA) for (int y = h + 1; y <= SEA; y++) set(lx, y, lz, B.WATER);
                        break;
                    }
                    case UPPER: {
                        for (int y = 50; y < h; y++) set(lx, y, lz, y < h - 3 ? "stone" : "dirt");
                        String st;
                        if (lz >= 15 && lz <= 18) st = (lz == 16 && lx % 2 == 0) ? "stone_bricks" : paveP.clustered(lx, h, lz);
                        else st = grassP.clustered(lx, h, lz);
                        set(lx, h, lz, st);
                        break;
                    }
                    case QUAY: {
                        for (int y = 50; y < h; y++) set(lx, y, lz, "stone");
                        boolean edge = false;
                        for (Dir d : Dir.values()) { int zz = zoneAt(lx + d.dx, lz + d.dz); if (zz == BASIN || zz == PASSE) edge = true; }
                        set(lx, h, lz, edge ? "stone_bricks" : paveP.clustered(lx, h, lz));
                        break;
                    }
                    case BASIN: case PASSE: {
                        for (int y = 50; y < h; y++) set(lx, y, lz, y < h - 2 ? "stone" : "mud");
                        boolean nearWall = false;
                        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                            int zz = zoneAt(lx + dx, lz + dz);
                            if (zz != BASIN) nearWall = true;
                        }
                        double pn = nz.noise2(lx / 7.0 + 11, lz / 7.0 - 7);
                        if (!nearWall && zn == BASIN && pn > 0.5) {
                            set(lx, h - 1, lz, "mud");
                            set(lx, h, lz, (pn > 0.6 && Noise.hash(77, lx, 0, lz) < 0.25) ? "seagrass" : B.WATER);
                        } else set(lx, h, lz, mudP.clustered(lx, h, lz));
                        break;
                    }
                    case POINT: {
                        for (int y = 50; y <= h; y++) set(lx, y, lz, seaRockP.clustered(lx, y, lz));
                        if (nz.noise2(lx / 2.5, lz / 2.5 + 5) > 0.2) set(lx, h, lz, "moss_block");
                        break;
                    }
                    default: {
                        for (int y = 50; y <= h; y++) set(lx, y, lz, h > SEA && y > SEA - 1 ? seaRockP.clustered(lx, y, lz) : (y == h ? "sand" : "sandstone"));
                        for (int y = h + 1; y <= SEA; y++) set(lx, y, lz, B.WATER);
                    }
                }
            }
        // lande du plateau : pins, fougeres, herbes (hors de l'anneau de jeu)
        for (int lx = LX1 + 4; lx <= LX2 - 4; lx++)
            for (int lz = LZ1 + 4; lz <= LZ2 - 4; lz++) {
                if (zoneAt(lx, lz) != CLIFF) continue;
                if (lx >= RING1 - 5 && lx <= RING2 + 5 && lz >= RING1 - 5 && lz <= RING2 + 5) continue;
                int h = topAt(lx, lz);
                String g = B.name(get(lx, h, lz));
                if (!g.equals("grass_block") && !g.equals("moss_block")) continue;
                double r = hash(91, lx, 0, lz);
                if (r < 0.035 && ((lx * 7 + lz * 13) % 5 == 0)) pine(lx, h + 1, lz, 6 + (int) (hash(92, lx, 0, lz) * 5));
                else if (r < 0.16) setIfAir(lx, h + 1, lz, hash(93, lx, 0, lz) < 0.5 ? "fern" : "short_grass");
            }
        // balise sur l'ilot (cardinale noire et jaune, hors zone de jeu)
        int bx = 44, bz = 98, by = topAt(bx, bz);
        for (int y = by + 1; y <= by + 9; y++)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= 1 || y <= by + 2) set(bx + dx, y, bz + dz, ((y - by) / 3) % 2 == 0 ? "black_concrete" : "yellow_concrete");
        set(bx, by + 10, bz, "iron_bars");
        set(bx, by + 11, bz, "black_concrete");
    }

    /** Parois exposees des quais et du soutenement : maconnerie, mouillee (moussue, algues) au pied. */
    private void faces() {
        for (int lx = LX1; lx <= LX2; lx++)
            for (int lz = LZ1; lz <= LZ2; lz++) {
                int zn = zoneAt(lx, lz);
                if (zn != QUAY && zn != UPPER) continue;
                int h = topAt(lx, lz);
                int minN = h;
                for (Dir d : Dir.values()) {
                    if (!inT(lx + d.dx, lz + d.dz)) continue;
                    int zz = zoneAt(lx + d.dx, lz + d.dz);
                    if (zz == CLIFF) continue;
                    minN = Math.min(minN, topAt(lx + d.dx, lz + d.dz));
                }
                if (minN >= h) continue;
                for (int y = Math.max(50, minN + 1); y < h; y++) set(lx, y, lz, y <= MY + 2 ? wetP.clustered(lx, y, lz) : faceP.clustered(lx, y, lz));
                set(lx, h, lz, "stone_bricks");
            }
    }

    // ------------------------------------------------------------------ outils de batiment

    /**
     * Toit a pignons (un ou plusieurs pans paralleles, faite selon ridge) avec debord de 1 sur les quatre cotes.
     * spans = paires (a1, a2) des murs en travers ; b1..b2 = murs le long du faite. Noues en dalles.
     */
    private void roof(char ridge, int b1, int b2, int r0, Mat gable, int... spans) {
        int amin = Integer.MAX_VALUE, amax = Integer.MIN_VALUE;
        for (int i = 0; i < spans.length; i += 2) { amin = Math.min(amin, spans[i] - 1); amax = Math.max(amax, spans[i + 1] + 1); }
        for (int a = amin; a <= amax; a++) {
            int best = -1, bestSpan = -1, sgnBest = 0;
            boolean tie = false, ridgeRow = false, pair = false;
            for (int i = 0; i < spans.length; i += 2) {
                int lo = spans[i] - 1, hi = spans[i + 1] + 1;
                if (a < lo || a > hi) continue;
                int dl = a - lo, dh = hi - a, e = Math.min(dl, dh);
                int sgn = dl < dh ? 1 : dl > dh ? -1 : 0;
                if (e > best) { best = e; bestSpan = i; sgnBest = sgn; tie = false; ridgeRow = dl == dh; pair = Math.abs(dl - dh) == 1; }
                else if (e == best && i != bestSpan && sgn != sgnBest) tie = true;
            }
            if (best < 0) continue;
            int y = r0 + best;
            for (int b = b1 - 1; b <= b2 + 1; b++) {
                int x = ridge == 'x' ? b : a, z = ridge == 'x' ? a : b;
                String st;
                if (tie) st = slab("deepslate_tile_slab", false);
                else if (ridgeRow) st = "deepslate_tiles";
                else {
                    Dir f = ridge == 'x' ? (sgnBest > 0 ? Dir.SOUTH : Dir.NORTH) : (sgnBest > 0 ? Dir.EAST : Dir.WEST);
                    st = B.stairs(roofP.clustered(x, y, z), f, false);
                }
                set(x, y, z, st);
                if (pair && !tie) set(x, y + 1, z, slab("deepslate_tile_slab", false));
                if ((b == b1 || b == b2) && a > amin && a < amax)
                    for (int yy = r0 + 1; yy < y; yy++) set(x, yy, z, gable.at(x, yy, z));
            }
        }
    }

    /** Hauteur du faite d'un toit simple (pour les cheminees). */
    private static int ridgeY(int a1, int a2, int r0) {
        return r0 + ((a2 + 1) - (a1 - 1)) / 2;
    }

    /**
     * Murs et planchers d'un batiment (perimetre x1..x2 / z1..z2), plancher du rez en fy, n niveaux de 4.
     * Chainages d'angle en pierre de taille, socle sombre, bandeau d'etage en escaliers retournes. Renvoie r0.
     */
    private int shell(int x1, int z1, int x2, int z2, int fy, int floors, boolean white, String floorMat) {
        int r0 = fy + 4 * floors - 1;
        Mat wall = white ? whiteP : wallP;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                boolean edge = edgeX || edgeZ;
                if (!edge) {
                    for (int y = fy + 1; y <= r0 + 9; y++) set(x, y, z, B.AIR);
                    for (int k = 0; k < floors; k++) set(x, fy + 4 * k, z, k == 0 ? floorMat : "spruce_planks");
                    continue;
                }
                set(x, fy, z, "cobblestone");
                for (int y = fy + 1; y <= r0; y++) {
                    boolean corner = edgeX && edgeZ;
                    boolean nearCorner = (edgeX && (z == z1 + 1 || z == z2 - 1)) || (edgeZ && (x == x1 + 1 || x == x2 - 1));
                    boolean even = ((y - fy) & 1) == 0;
                    String st;
                    if (corner) st = even ? "stone_bricks" : (white ? "cobblestone" : "granite");
                    else if (nearCorner && even) st = "stone_bricks";
                    else if (y == fy + 1) st = white ? "cobblestone" : (nz.noise2(x / 3.0, z / 3.0) > 0 ? "mossy_cobblestone" : "cobblestone");
                    else st = wall.at(x, y, z);
                    set(x, y, z, st);
                }
            }
        // bandeau d'etage (saillie de 1)
        for (int k = 1; k < floors; k++) {
            int y = fy + 4 * k;
            for (int x = x1; x <= x2; x++) {
                if (air(x, y, z1 - 1)) set(x, y, z1 - 1, stairs("stone_brick_stairs", Dir.SOUTH, true));
                if (air(x, y, z2 + 1)) set(x, y, z2 + 1, stairs("stone_brick_stairs", Dir.NORTH, true));
            }
            for (int z = z1; z <= z2; z++) {
                if (air(x1 - 1, y, z)) set(x1 - 1, y, z, stairs("stone_brick_stairs", Dir.EAST, true));
                if (air(x2 + 1, y, z)) set(x2 + 1, y, z, stairs("stone_brick_stairs", Dir.WEST, true));
            }
        }
        return r0;
    }

    /** Fenetre de 2 de haut dans le mur, volets ouverts en saillie, appui en escalier retourne aux etages. */
    private void window(int x, int y, int z, Dir out, String shutter, boolean sill) {
        set(x, y, z, "glass_pane");
        set(x, y + 1, z, "glass_pane");
        Dir p = out.cw();
        for (int sg = -1; sg <= 1; sg += 2) {
            int sx = x + out.dx + sg * p.dx, sz = z + out.dz + sg * p.dz;
            for (int yy = y; yy <= y + 1; yy++) if (air(sx, yy, sz)) set(sx, yy, sz, trap(shutter, out, false, true));
        }
        if (sill && air(x + out.dx, y - 1, z + out.dz)) set(x + out.dx, y - 1, z + out.dz, stairs("stone_brick_stairs", out.opposite(), true));
    }

    /** Fenetres des quatre faces (facade sud), en sautant la porte, la porte arriere et les murs mitoyens. */
    private void windows(int x1, int z1, int x2, int z2, int fy, int floors, int xd, int xr, String shutter, int step) {
        for (int k = 0; k < floors; k++) {
            int y = fy + 4 * k + 2;
            for (int x = x1 + 2; x <= x2 - 2; x += step) {
                if (!(k == 0 && Math.abs(x - xd) <= 1) && air(x, y, z2 + 1)) window(x, y, z2, Dir.SOUTH, shutter, k > 0);
                if (!(k == 1 && Math.abs(x - xr) <= 1) && air(x, y, z1 - 1) && air(x, y + 1, z1 - 1)) window(x, y, z1, Dir.NORTH, shutter, k > 0);
            }
            for (int z = z1 + 2; z <= z2 - 2; z += step) {
                if (air(x1 - 1, y, z) && air(x1 - 1, y + 1, z)) window(x1, y, z, Dir.WEST, shutter, k > 0);
                if (air(x2 + 1, y, z) && air(x2 + 1, y + 1, z)) window(x2, y, z, Dir.EAST, shutter, k > 0);
            }
        }
    }

    private void doorway(int x, int y, int z, Dir out, String wood) {
        door(x, y, z, wood, out.opposite(), false, true);
        set(x, y + 2, z, "stone_bricks");
    }

    /** Cheminee de pignon 1 x 1 dans la colonne (x, z), du sol au-dessus du faite, fumee (feu de camp). */
    private void chimney(int x, int z, int y1, int y2) {
        for (int y = y1; y <= y2; y++) set(x, y, z, (y % 3 == 0) ? "stone_bricks" : "cobblestone");
        set(x, y2 + 1, z, "campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
    }

    /**
     * Escalier interieur : 4 marches montant vers dir depuis (x, fy+1, z) ; la 4e est dans le plancher fy+4, les
     * 3 premieres sous une tremie (on monte sans se cogner la tete ; garde-corps cote railSide). Depart en x-dir,
     * arrivee en x+4dir.
     */
    private void innerStairs(int x, int z, int fy, Dir dir, String wood, Dir railSide) {
        for (int i = 0; i < 4; i++) {
            int sx = x + i * dir.dx, sz = z + i * dir.dz;
            for (int y = fy + 1; y < fy + 1 + i; y++) set(sx, y, sz, wood + "_planks");
            set(sx, fy + 1 + i, sz, stairs(wood + "_stairs", dir, false));
            for (int y = fy + 2 + i; y <= fy + 6; y++) set(sx, y, sz, B.AIR);
            if (i <= 2) set(sx + railSide.dx, fy + 5, sz + railSide.dz, wood + "_fence");
        }
        int ax = x + 4 * dir.dx, az = z + 4 * dir.dz;
        for (int y = fy + 5; y <= fy + 6; y++) set(ax, y, az, B.AIR);
        for (int y = fy + 1; y <= fy + 2; y++) set(x - dir.dx, y, z - dir.dz, B.AIR);
    }

    private static long key(int x, int z) { return ((long) (x + 1000) << 20) | (z + 1000); }

    /**
     * Amenagement generique d'un batiment a facade sud : porte au rez en xd, escaliers (niveaux pairs le long du mur
     * du fond vers l'ouest, impairs le long de la facade vers l'est), porte arriere a l'etage en xr (-1 = aucune),
     * objets le long des murs (items[niveau]) en laissant le centre libre, lanternes suspendues.
     */
    private void fitOut(int x1, int z1, int x2, int z2, int fy, int floors, int xd, int xr, String[][] items, long seed) {
        for (int k = 0; k < floors; k++) {
            int y = fy + 4 * k;
            Set<Long> res = new HashSet<>();
            if (k == 0) for (int dz = 1; dz <= 2; dz++) for (int dx = -1; dx <= 1; dx++) res.add(key(xd + dx, z2 - dz));
            if (k == 1 && xr >= 0) for (int dz = 1; dz <= 2; dz++) res.add(key(xr, z1 + dz));
            // escaliers de ce niveau (depart) et tremie du niveau precedent (arrivee)
            if (k < floors - 1) {
                if ((k & 1) == 0) for (int x = x2 - 6; x <= x2 - 1; x++) { res.add(key(x, z1 + 1)); res.add(key(x, z1 + 2)); }
                else for (int x = x1 + 1; x <= x1 + 6; x++) { res.add(key(x, z2 - 1)); res.add(key(x, z2 - 2)); }
            }
            if (k > 0) {
                if (((k - 1) & 1) == 0) for (int x = x2 - 7; x <= x2 - 1; x++) { res.add(key(x, z1 + 1)); res.add(key(x, z1 + 2)); }
                else for (int x = x1 + 1; x <= x1 + 7; x++) { res.add(key(x, z2 - 1)); res.add(key(x, z2 - 2)); }
            }
            String[] list = items[Math.min(k, items.length - 1)];
            if (list.length == 0) continue;
            int idx = 0;
            for (int x = x1 + 1; x <= x2 - 1; x++)
                for (int z = z1 + 1; z <= z2 - 1; z++) {
                    boolean ring = x == x1 + 1 || x == x2 - 1 || z == z1 + 1 || z == z2 - 1;
                    boolean nearCorner = (x <= x1 + 2 || x >= x2 - 2) && (z <= z1 + 2 || z >= z2 - 2);
                    if (!ring || nearCorner || res.contains(key(x, z)) || !air(x, y + 1, z) || B.isAir(get(x, y, z))) continue;
                    if (hash(seed + k, x, 0, z) > 0.58) continue;
                    String it = list[Math.floorMod((int) (hash(seed + 7 + k, x, 1, z) * 1000) + idx++, list.length)];
                    set(x, y + 1, z, it);
                    boolean full = B.isSolid(it) && !it.contains("composter") && !it.contains("cauldron");
                    if (full && hash(seed + 3, x, k, z) < 0.35 && air(x, y + 2, z)) set(x, y + 2, z, list[Math.floorMod(idx * 7 + 3, list.length)]);
                    else if (full && hash(seed + 5, x, k, z) < 0.15 && air(x, y + 2, z)) set(x, y + 2, z, lantern(false));
                }
            if (k < floors - 1) {
                int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
                if (!B.isAir(get(cx, y + 4, cz)) && air(cx, y + 3, cz)) set(cx, y + 3, cz, hangingLantern());
            }
        }
    }

    /** Escaliers d'un batiment selon la regle de fitOut ; porte arriere (xr) a l'etage sur la terrasse. */
    private void stairsAndRear(int x1, int z1, int x2, int z2, int fy, int floors, int xr, String wood) {
        for (int k = 0; k < floors - 1; k++) {
            int y = fy + 4 * k;
            if ((k & 1) == 0) innerStairs(x2 - 2, z1 + 1, y, Dir.WEST, wood, Dir.SOUTH);
            else innerStairs(x1 + 2, z2 - 1, y, Dir.EAST, wood, Dir.NORTH);
        }
        if (xr >= 0) door(xr, fy + 5, z1, "spruce_door", Dir.SOUTH, false, true);
    }

    private void table(int x, int y, int z, Dir... chairs) {
        set(x, y, z, "spruce_fence");
        set(x, y + 1, z, "spruce_pressure_plate");
        for (Dir d : chairs) if (air(x + d.dx, y, z + d.dz)) set(x + d.dx, y, z + d.dz, stairs("spruce_stairs", d, false));
    }

    private void lamp(int x, int y, int z) {
        set(x, y, z, "cobblestone_wall");
        set(x, y + 1, z, "spruce_fence");
        set(x, y + 2, z, "spruce_fence");
        set(x, y + 3, z, lantern(false));
    }

    private void bench(int x, int y, int z, Dir back, int len) {
        Dir along = back.cw();
        for (int i = 0; i < len; i++) set(x + i * along.dx, y, z + i * along.dz, stairs("spruce_stairs", back, false));
    }

    private void pine(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) set(x, y + i, z, "spruce_log[axis=y]");
        String lv = B.leaves("spruce_leaves");
        for (int i = 2; i <= h; i++) {
            int r = i == h ? 0 : Math.max(1, (int) Math.round((h - i) * 0.45 + (i % 2 == 0 ? 0.5 : 0)));
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                if (air(x + dx, y + i, z + dz)) set(x + dx, y + i, z + dz, lv);
            }
        }
        set(x, y + h, z, lv);
    }

    /** Hortensia : boule de feuilles d'azalee fleurie (et non fleurie) sur une souche. */
    private void hydrangea(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 1; dy++) {
            if (Math.abs(dx) + Math.abs(dz) + dy > 2) continue;
            if (!air(x + dx, y + dy, z + dz)) continue;
            set(x + dx, y + dy, z + dz, B.leaves(hash(61, x + dx, y + dy, z + dz) < 0.7 ? "flowering_azalea_leaves" : "azalea_leaves"));
        }
    }

    private void netPile(int x, int y, int z, String wool) {
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
            set(x + dx, y, z + dz, wool);
            if (dx + dz == 0) set(x + dx, y + 1, z + dz, wool);
            else if (air(x + dx, y + 1, z + dz)) set(x + dx, y + 1, z + dz, wool.replace("_wool", "_carpet"));
        }
    }

    private void kelpHeap(int x, int y, int z, int w, int d) {
        for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < d; dz++) {
            set(x + dx, y, z + dz, "dried_kelp_block");
            if (dx > 0 && dx < w - 1 && hash(62, x + dx, 0, z + dz) < 0.7) set(x + dx, y + 1, z + dz, "dried_kelp_block");
        }
    }

    private void crates(int x, int y, int z, int w, int d, long seed) {
        String[] g = {"spruce_planks", "barrel[facing=up,open=false]", "spruce_planks", "dark_oak_planks"};
        for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < d; dz++) {
            int h = 1 + (int) (hash(seed, x + dx, 0, z + dz) * 2.6);
            for (int i = 0; i < h; i++) set(x + dx, y + i, z + dz, g[(int) (hash(seed + 1, x + dx, i, z + dz) * g.length)]);
        }
    }

    private void rock(int cx, int cy, int cz, double rx, double ry, double rz, long seed) {
        Noise rn = new Noise(seed);
        int ex = (int) Math.ceil(rx) + 1, ey = (int) Math.ceil(ry) + 1, ez = (int) Math.ceil(rz) + 1;
        for (int dx = -ex; dx <= ex; dx++) for (int dy = -1; dy <= ey; dy++) for (int dz = -ez; dz <= ez; dz++) {
            double v = (dx / rx) * (dx / rx) + (dy / ry) * (dy / ry) + (dz / rz) * (dz / rz);
            if (v > 1 + 0.35 * rn.noise3(dx / 2.0, dy / 2.0, dz / 2.0)) continue;
            int y = cy + dy;
            if (y < MY) continue;
            set(cx + dx, y, cz + dz, seaRockP.clustered(cx + dx, y, cz + dz));
        }
    }

    // ------------------------------------------------------------------ batiments du quai

    private void shipyard() {
        // atelier (2 niveaux, porte arriere sur la terrasse) : x 5..20, z 25..32
        int r0 = shell(6, 25, 20, 32, QY, 2, false, "spruce_planks");
        roof('x', 6, 20, r0, wallP, 25, 32);
        chimney(5, 28, QY + 1, ridgeY(25, 32, r0) + 1);
        windows(6, 25, 20, 32, QY, 2, 12, 7, "spruce_trapdoor", 4);
        // grande baie vers le hangar
        air(9, QY + 1, 32, 16, QY + 3, 32);
        for (int x = 9; x <= 16; x++) set(x, QY + 4, 32, "stripped_spruce_log[axis=x]");
        stairsAndRear(6, 25, 20, 32, QY, 2, 8, "spruce");
        fitOut(6, 25, 20, 32, QY, 2, 12, 7, new String[][]{
                {"crafting_table", "grindstone[face=floor,facing=north]", "barrel[facing=up,open=false]", "coal_block", "spruce_planks", "dark_oak_planks"},
                {"loom[facing=south]", "white_wool", "blue_wool", "barrel[facing=up,open=false]", "white_wool", "green_wool"}}, 100);
        air(9, QY + 1, 31, 16, QY + 3, 31);
        // hangar ouvert : x 6..20, z 33..47, poteaux, toit a pignon vers le bassin
        for (int x = 6; x <= 20; x++) for (int z = 33; z <= 47; z++) for (int y = QY + 1; y <= QY + 16; y++) set(x, y, z, B.AIR);
        for (int z = 33; z <= 47; z += 2) {
            boolean post = (z - 33) % 4 == 0 || z == 47;
            for (int y = QY + 1; y <= QY + 6; y++) {
                if (post) { set(6, y, z, "spruce_log[axis=y]"); set(20, y, z, "spruce_log[axis=y]"); }
            }
        }
        for (int z = 34; z <= 46; z++) if ((z - 33) % 4 != 0) for (int y = QY + 1; y <= QY + 5; y++) set(6, y, z, plankP.clustered(6, y, z));
        for (int z = 33; z <= 47; z++) { set(6, QY + 6, z, "spruce_log[axis=z]"); set(20, QY + 6, z, "spruce_log[axis=z]"); }
        roof('z', 33, 47, QY + 6, plankP, 6, 20);
        // cale de construction : rails du hangar jusqu'a la vase
        for (int z = 35; z <= 56; z++) {
            int h = z <= 48 ? QY : Math.max(MY, QY - (z - 47) / 2);
            for (int x = 10; x <= 15; x++) {
                if (z > 48) {
                    for (int y = MY - 2; y < h; y++) set(x, y, z, (x == 10 || x == 15) ? faceP.clustered(x, y, z) : "stone");
                    set(x, h, z, (x == 11 || x == 14) ? "spruce_log[axis=z]" : "stone_bricks");
                    for (int y = h + 1; y <= QY + 2; y++) set(x, y, z, B.AIR);
                } else if (x == 11 || x == 14) set(x, QY, z, "spruce_log[axis=z]");
            }
        }
        // coque en construction sur son ber
        for (int z = 36; z <= 46; z += 3) for (int x = 9; x <= 15; x++) set(x, QY + 1, z, "spruce_log[axis=x]");
        hull(12, 41, Dir.SOUTH, 12, 5, 4, QY + 2, "dark_oak_planks", 1, false, false, -99, 0, 500);
        // bois de charpente, planches, goudron, etablis
        for (int z = 35; z <= 45; z++) { set(7, QY + 1, z, "spruce_log[axis=z]"); if (z % 3 != 0) set(7, QY + 2, z, "spruce_log[axis=z]"); }
        for (int z = 30; z <= 44; z += 2) { set(4, QY + 1, z, "spruce_log[axis=x]"); set(5, QY + 1, z, "spruce_log[axis=x]"); if (z % 4 == 0) set(5, QY + 2, z, "barrel[facing=up,open=false]"); }
        crates(17, QY + 1, 39, 3, 2, 501);
        for (int z = 42; z <= 44; z++) { set(19, QY + 1, z, "dark_oak_planks"); set(19, QY + 2, z, "deepslate_tiles"); set(18, QY + 1, z, "dark_oak_planks"); }
        set(18, QY + 1, 34, "crafting_table");
        set(19, QY + 1, 34, "grindstone[face=floor,facing=north]");
        set(17, QY + 1, 34, "coal_block");
        set(17, QY + 2, 34, "coal_block");
        set(8, QY + 1, 34, "barrel[facing=up,open=false]");
        set(7, QY + 1, 34, "coal_block");
        // lanternes au bout de chaines sous le faite
        for (int z = 37; z <= 45; z += 4) {
            for (int y = QY + 9; y <= QY + 13; y++) set(13, y, z, "iron_chain[axis=y,waterlogged=false]");
            set(13, QY + 8, z, hangingLantern());
        }
    }

    private void cafe() {
        int x1 = 23, z1 = 25, x2 = 33, z2 = 35;
        int r0 = shell(x1, z1, x2, z2, QY, 2, true, "oak_planks");
        roof('x', x1, x2, r0, whiteP, z1, z2);
        windows(x1, z1, x2, z2, QY, 2, 28, 25, "warped_trapdoor", 3);
        doorway(28, QY + 1, z2, Dir.SOUTH, "spruce_door");
        stairsAndRear(x1, z1, x2, z2, QY, 2, 25, "spruce");
        // bar, cuisine, tables
        for (int x = 24; x <= 27; x++) { set(x, QY + 1, 29, "spruce_planks"); set(x, QY + 1, 26, "barrel[facing=up,open=false]"); }
        set(26, QY + 2, 29, lantern(false));
        set(24, QY + 1, 30, "smoker[facing=east,lit=false]");
        set(24, QY + 1, 31, "furnace[facing=east,lit=false]");
        set(24, QY + 1, 32, "cauldron");
        table(30, QY + 1, 31, Dir.WEST, Dir.EAST);
        table(26, QY + 1, 33, Dir.WEST);
        set(32, QY + 1, 33, "bookshelf");
        set(32, QY + 1, 30, "barrel[facing=up,open=false]");
        set(32, QY + 2, 30, "barrel[facing=up,open=false]");
        set(27, QY + 3, 31, hangingLantern());
        // etage : chambre
        fitOut(x1, z1, x2, z2, QY, 2, 28, 25, new String[][]{{}, {"bookshelf", "loom[facing=south]", "barrel[facing=up,open=false]", "white_wool", "blue_wool"}}, 110);
        // terrasse : tables a parasols
        int[][] tb = {{25, 38}, {30, 38}, {27, 41}};
        for (int[] t : tb) {
            table(t[0], QY + 1, t[1], Dir.WEST, Dir.EAST);
            set(t[0], QY + 2, t[1], "spruce_fence");
            set(t[0], QY + 3, t[1], "spruce_fence");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(t[0] + dx, QY + 4, t[1] + dz, "white_wool");
        }
        for (int x : new int[]{24, 32}) set(x, QY + 1, 36, "potted_red_tulip");
    }

    private void criee() {
        int x1 = 37, z1 = 25, x2 = 57, z2 = 44, r0 = QY + 7;
        // sol, vidage
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            for (int y = QY + 1; y <= r0 + 8; y++) set(x, y, z, B.AIR);
            set(x, QY, z, ((x + z) & 3) == 0 ? "stone_bricks" : "smooth_stone");
        }
        // mur du fond plein (granit), cotes : soubassement + grandes baies entre poteaux, facade sud en arcades
        for (int x = x1; x <= x2; x++) for (int y = QY + 1; y <= r0; y++) set(x, y, z1, (x == x1 || x == x2) ? "stone_bricks" : wallP.at(x, y, z1));
        for (int z = z1; z <= z2; z++) {
            boolean post = (z - z1) % 4 == 0 || z == z2;
            for (int x : new int[]{x1, x2}) for (int y = QY + 1; y <= r0; y++) {
                String st;
                if (post) st = (y & 1) == 0 ? "stone_bricks" : "granite";
                else if (y == QY + 1) st = "cobblestone";
                else if (y <= QY + 4) st = (z % 4 == 2) ? "spruce_fence" : B.AIR;
                else if (y == QY + 5) st = "stripped_spruce_log[axis=z]";
                else st = whiteP.at(x, y, z);
                set(x, y, z, st);
            }
        }
        for (int x = x1; x <= x2; x++) {
            boolean post = (x - x1) % 4 == 0;
            for (int y = QY + 1; y <= r0; y++) {
                String st;
                if (post) st = (y & 1) == 0 ? "stone_bricks" : "granite";
                else if (y <= QY + 4) st = B.AIR;
                else if (y == QY + 5) st = "stripped_spruce_log[axis=x]";
                else st = whiteP.at(x, y, z2);
                set(x, y, z2, st);
            }
            if (!post) set(x, QY + 4, z2, stairs("stone_brick_stairs", Dir.EAST, true));
        }
        // poteaux de la noue et entraits
        for (int z = z1 + 4; z < z2; z += 4) for (int y = QY + 1; y <= r0; y++) set(47, y, z, "spruce_log[axis=y]");
        for (int z = z1 + 4; z < z2; z += 4) for (int x = x1 + 1; x < x2; x++) if (x != 47) set(x, r0, z, "spruce_log[axis=x]");
        roof('z', z1, z2, r0, whiteP, 37, 47, 47, 57);
        for (int z = z1 + 4; z < z2; z += 4) for (int x : new int[]{41, 52}) set(x, r0 - 1, z, hangingLantern());
        // mezzanine z 26..30 en y 68, gradins z 31..33 (x 40..54)
        for (int x = x1 + 1; x < x2; x++) for (int z = 26; z <= 30; z++) set(x, UY, z, "spruce_planks");
        for (int x = x1 + 1; x < x2; x++) if (x < 40 || x > 54) set(x, UY + 1, 30, "spruce_fence");
        for (int x = 40; x <= 54; x++)
            for (int k = 0; k < 3; k++) for (int y = QY + 1; y <= QY + 1 + k; y++) set(x, y, 33 - k, x % 5 == 2 ? "dark_oak_planks" : "oak_planks");
        // sous la mezzanine : chambre froide (ouest) et bureau du crieur (est)
        for (int z = 26; z <= 29; z++) for (int y = QY + 1; y <= QY + 3; y++) { set(44, y, z, "calcite"); set(50, y, z, "calcite"); }
        air(44, QY + 1, 28, 44, QY + 2, 28);
        air(50, QY + 1, 28, 50, QY + 2, 28);
        for (int x = 45; x <= 49; x++) { set(x, QY + 1, 26, "packed_ice"); if (x % 2 == 1) set(x, QY + 2, 26, "blue_ice"); }
        set(45, QY + 1, 29, "barrel[facing=up,open=false]");
        set(49, QY + 1, 29, "composter[level=0]");
        for (int z = 26; z <= 29; z++) { set(38, QY + 1, z, "composter[level=0]"); if (z % 2 == 0) set(39, QY + 1, z, "cauldron"); }
        set(56, QY + 1, 26, "bookshelf"); set(55, QY + 1, 26, "bookshelf"); set(56, QY + 2, 26, "bookshelf");
        set(53, QY + 1, 27, "barrel[facing=up,open=false]");
        set(56, QY + 1, 29, "cartography_table");
        // mezzanine : pupitre de la vente, caisses, porte sur la terrasse
        set(47, UY + 1, 29, "lectern[facing=south,has_book=false,powered=false]");
        crates(39, UY + 1, 26, 3, 2, 520);
        crates(53, UY + 1, 26, 2, 2, 521);
        set(44, UY + 1, 26, "loom[facing=south]");
        set(45, UY + 1, 26, "blue_wool"); set(46, UY + 1, 26, "green_wool"); set(45, UY + 2, 26, "blue_carpet");
        door(50, UY + 1, z1, "spruce_door", Dir.SOUTH, false, true);
        air(49, UY + 1, 26, 51, UY + 2, 27);
        // halle : etals (bacs, bassines, glace, balances), en ilots decales
        int[][] isl = {{39, 35, 5}, {39, 40, 4}, {49, 36, 5}, {50, 41, 3}};
        int k = 0;
        for (int[] is : isl) {
            for (int dx = 0; dx < is[2]; dx++) for (int dz = 0; dz <= 1; dz++) {
                int x = is[0] + dx, z = is[1] + dz;
                if (x == 47) continue;
                String st = ((dx + k) % 4 == 0) ? "iron_block" : ((dx + dz) % 3 == 0) ? "cauldron" : ((dx + dz) % 3 == 1) ? "composter[level=0]" : "packed_ice";
                set(x, QY + 1, z, st);
                if (st.equals("iron_block")) set(x, QY + 2, z, slab("smooth_stone_slab", false));
            }
            k++;
        }
        crates(55, QY + 1, 38, 2, 3, 522);
        set(38, QY + 1, 43, "barrel[facing=up,open=false]"); set(38, QY + 2, 43, "barrel[facing=up,open=false]");
        set(56, QY + 1, 43, "orange_wool"); set(56, QY + 2, 43, "orange_wool"); set(55, QY + 1, 43, "orange_wool");
    }

    private void entrepot() {
        int x1 = 60, z1 = 25, x2 = 70, z2 = 36;
        int r0 = shell(x1, z1, x2, z2, QY, 3, false, "stone");
        roof('x', x1, x2, r0, wallP, z1, z2);
        chimney(71, 30, QY + 1, ridgeY(z1, z2, r0) + 1);
        windows(x1, z1, x2, z2, QY, 3, 65, 62, "dark_oak_trapdoor", 3);
        // grande porte de chargement et lucarne de l'etage
        air(64, QY + 1, z2, 66, QY + 3, z2);
        for (int y = QY + 1; y <= QY + 3; y++) { set(63, y, z2, "stripped_spruce_log[axis=y]"); set(67, y, z2, "stripped_spruce_log[axis=y]"); }
        for (int x = 63; x <= 67; x++) set(x, QY + 4, z2, "stripped_spruce_log[axis=x]");
        air(65, QY + 5, z2, 65, QY + 6, z2);
        set(65, r0, z2 + 1, "spruce_log[axis=z]");
        set(65, r0, z2 + 2, "spruce_log[axis=z]");
        for (int y = QY + 4; y <= r0 - 1; y++) set(65, y, z2 + 2, "iron_chain[axis=y,waterlogged=false]");
        set(65, QY + 3, z2 + 2, "barrel[facing=up,open=false]");
        stairsAndRear(x1, z1, x2, z2, QY, 3, 62, "spruce");
        fitOut(x1, z1, x2, z2, QY, 3, 65, 62, new String[][]{
                {"spruce_planks", "barrel[facing=up,open=false]", "coal_block", "dried_kelp_block", "spruce_planks", "dark_oak_planks"},
                {"blue_wool", "green_wool", "loom[facing=south]", "barrel[facing=up,open=false]", "spruce_planks"},
                {"white_wool", "dried_kelp_block", "barrel[facing=up,open=false]", "orange_wool", "spruce_planks"}}, 120);
        // rangees de caisses au centre du rez (allees entre les piles)
        crates(63, QY + 1, 29, 2, 2, 523);
        crates(66, QY + 1, 31, 2, 2, 524);
    }

    private void capitainerie() {
        int x1 = 73, z1 = 25, x2 = 84, z2 = 35;
        int r0 = shell(x1, z1, x2, z2, QY, 2, true, "spruce_planks");
        roof('x', x1, x2, r0, whiteP, z1, z2);
        // tour de guet 5 x 5 a l'angle sud-est (x 80..84, z 31..35), salle de veille en y 77..79
        int tx1 = 80, tz1 = 31, tx2 = 84, tz2 = 35, tTop = QY + 16;
        for (int x = tx1; x <= tx2; x++) for (int z = tz1; z <= tz2; z++) {
            boolean edge = x == tx1 || x == tx2 || z == tz1 || z == tz2;
            boolean corner = (x == tx1 || x == tx2) && (z == tz1 || z == tz2);
            for (int y = QY + 1; y <= tTop; y++) {
                if (edge) {
                    boolean look = y >= QY + 13 && y <= QY + 14 && !corner;
                    set(x, y, z, look ? "glass_pane" : corner ? ((y & 1) == 0 ? "stone_bricks" : "cobblestone") : whiteP.at(x, y, z));
                } else if (y > QY + 4 && y != QY + 12) set(x, y, z, B.AIR);
            }
            if (!edge) set(x, QY + 12, z, "spruce_planks");
        }
        // toit en pyramide de cuivre oxyde
        for (int l = 0; l <= 3; l++) {
            int y = tTop + 1 + l, r = 3 - l;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                int x = 82 + dx, z = 33 + dz;
                if (r == 0) { set(x, y, z, "oxidized_copper"); continue; }
                if (Math.abs(dx) == r || Math.abs(dz) == r) {
                    Dir f = Math.abs(dx) == r && Math.abs(dz) != r ? (dx < 0 ? Dir.EAST : Dir.WEST) : (dz < 0 ? Dir.SOUTH : Dir.NORTH);
                    set(x, y, z, stairs("oxidized_cut_copper_stairs", f, false));
                } else set(x, y, z, "oxidized_copper");
            }
        }
        set(82, tTop + 5, 33, "lightning_rod[facing=up,powered=false,waterlogged=false]");
        // echelle de la tour (de l'etage a la salle de veille)
        for (int y = QY + 5; y <= QY + 12; y++) set(83, y, 34, "ladder[facing=west,waterlogged=false]");
        air(83, QY + 13, 34, 83, QY + 14, 34);
        air(80, QY + 1, 33, 80, QY + 2, 33);
        air(80, QY + 5, 33, 80, QY + 6, 33);
        set(81, QY + 13, 32, "cartography_table");
        set(81, QY + 13, 34, "barrel[facing=up,open=false]");
        set(81, QY + 14, 34, lantern(false));
        windows(x1, z1, x2, z2, QY, 2, 76, 75, "warped_trapdoor", 3);
        doorway(76, QY + 1, z2, Dir.SOUTH, "dark_oak_door");
        stairsAndRear(x1, z1, x2, z2, QY, 2, 75, "spruce");
        fitOut(x1, z1, tx1 - 1, z2, QY, 2, 76, 75, new String[][]{
                {"cartography_table", "bookshelf", "barrel[facing=up,open=false]", "bookshelf", "lectern[facing=south,has_book=false,powered=false]"},
                {"bookshelf", "loom[facing=south]", "cartography_table", "barrel[facing=up,open=false]", "blue_wool"}}, 130);
        // mat a pavillon devant la capitainerie
        for (int y = QY + 1; y <= QY + 9; y++) set(72, y, 38, "spruce_fence");
        set(72, QY + 1, 38, "cobblestone_wall");
        set(71, QY + 9, 38, "blue_wool"); set(71, QY + 8, 38, "white_wool");
    }

    // ------------------------------------------------------------------ terrasse haute

    private void house(int x1, int x2, int floors, boolean white, int chimneySide, long seed) {
        int z1 = 7, z2 = 14, fy = UY;
        int xd = (x1 + x2) / 2 + (hash(seed, 1, 0, 0) < 0.5 ? 0 : 1);
        if (xd >= x2 - 1) xd = x2 - 2;
        int r0 = shell(x1, z1, x2, z2, fy, floors, white, "oak_planks");
        roof('x', x1, x2, r0, white ? whiteP : wallP, z1, z2);
        int ry = ridgeY(z1, z2, r0) + 1;
        if (chimneySide <= 0 && air(x1 - 1, fy + 2, 10)) chimney(x1 - 1, 10, fy + 1, ry);
        if (chimneySide >= 0 && air(x2 + 1, fy + 2, 11)) chimney(x2 + 1, 11, fy + 1, ry);
        windows(x1, z1, x2, z2, fy, floors, xd, -1, white ? "warped_trapdoor" : "spruce_trapdoor", 3);
        doorway(xd, fy + 1, z2, Dir.SOUTH, white ? "spruce_door" : "dark_oak_door");
        stairsAndRear(x1, z1, x2, z2, fy, floors, -1, "spruce");
        String[][] items = {
                {"furnace[facing=south,lit=false]", "barrel[facing=up,open=false]", "bookshelf", "crafting_table", "cauldron", "smoker[facing=south,lit=false]"},
                {"blue_wool", "white_wool", "loom[facing=south]", "bookshelf", "barrel[facing=up,open=false]"},
                {"white_wool", "barrel[facing=up,open=false]", "loom[facing=south]", "orange_wool"}};
        fitOut(x1, z1, x2, z2, fy, floors, xd, -1, items, seed);
        // seuil : marche, pots, lanterne au mur
        if (air(xd - 1, fy + 1, z2 + 1)) set(xd - 1, fy + 1, z2 + 1, white ? "potted_red_tulip" : "barrel[facing=up,open=false]");
        if (air(xd + 1, fy + 1, z2 + 1) && hash(seed, 2, 0, 0) < 0.6) set(xd + 1, fy + 1, z2 + 1, "flower_pot");
    }

    private void upperHouses() {
        house(6, 13, 2, true, -1, 201);
        house(15, 22, 2, false, 1, 202);
        house(26, 36, 1, false, -1, 203);
        house(39, 46, 3, true, 1, 204);
        house(48, 56, 2, false, 1, 205);
        house(74, 84, 2, true, -1, 206);
        // tas de bois et pins dans les ecarts
        for (int x = 13; x <= 15; x++) { set(x, UY + 1, 23, "spruce_log[axis=x]"); if (x != 15) set(x, UY + 2, 23, "spruce_log[axis=x]"); }
        pine(59, UY + 1, 10, 9);
        pine(61, UY + 1, 21, 7);
        pine(72, UY + 1, 9, 8);
        hydrangea(58, UY + 1, 13);
        hydrangea(71, UY + 1, 13);
        for (int x = 57; x <= 62; x++) if (x != 60) set(x, UY + 1, 14, "cobblestone_wall");
        bench(60, UY + 1, 11, Dir.NORTH, 2);
    }

    private void chapel() {
        int x1 = 63, x2 = 69, z1 = 7, z2 = 14, fy = UY, r0 = UY + 6;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
            set(x, fy, z, edge ? "cobblestone" : "stone_bricks");
            for (int y = fy + 1; y <= r0 + 12; y++) set(x, y, z, edge && y <= r0 ? ((x == x1 || x == x2) && (z == z1 || z == z2) ? "stone_bricks" : wallP.at(x, y, z)) : B.AIR);
        }
        // contreforts
        for (int z = z1; z <= z2; z += 4) for (int y = fy + 1; y <= fy + 4; y++) {
            set(x1 - 1, y, z, y == fy + 4 ? stairs("stone_brick_stairs", Dir.EAST, false) : "stone_bricks");
            set(x2 + 1, y, z, y == fy + 4 ? stairs("stone_brick_stairs", Dir.WEST, false) : "stone_bricks");
        }
        roof('z', z1, z2, r0, wallP, x1, x2);
        // clocher-mur au-dessus du pignon sud
        int ry = ridgeY(x1, x2, r0);
        for (int x = 64; x <= 68; x++) for (int y = r0 + 1; y <= ry + 6; y++) {
            int dx = Math.abs(x - 66);
            if (y > ry + 6 - dx) continue;
            set(x, y, z2, (dx == 2 || y > ry + 4) ? "stone_bricks" : "granite");
        }
        air(66, ry + 2, z2, 66, ry + 3, z2);
        set(66, ry + 3, z2, "bell[attachment=ceiling,facing=south,powered=false]");
        set(66, ry + 7, z2, "cobblestone_wall");
        set(66, ry + 8, z2, "cobblestone_wall");
        // porte en arc, baies etroites a vitraux, bancs, autel
        doorway(66, fy + 1, z2, Dir.SOUTH, "spruce_door");
        set(66, fy + 3, z2, "stone_bricks");
        for (int z = z1 + 2; z <= z2 - 2; z += 3) for (int x : new int[]{x1, x2}) { set(x, fy + 2, z, "light_blue_stained_glass_pane"); set(x, fy + 3, z, "light_blue_stained_glass_pane"); }
        for (int z = 9; z <= 12; z += 2) { bench(65, fy + 1, z, Dir.SOUTH, 2); bench(68, fy + 1, z, Dir.SOUTH, 2); }
        for (int x = 65; x <= 67; x++) set(x, fy + 1, 8, "stone_bricks");
        set(66, fy + 2, 8, lantern(false));
        set(64, fy + 1, 8, "bookshelf");
        set(68, fy + 1, 8, "bookshelf");
        for (int y = r0 + 1; y < ry; y++) set(66, y, 10, "iron_chain[axis=y,waterlogged=false]");
        set(66, r0, 10, hangingLantern());
        // parvis
        for (int x = 64; x <= 68; x++) set(x, fy, z2 + 1, "stone_bricks");
    }

    private void terraceSouth() {
        // lavoir (x 6..12, z 20..22) : bassin d'un bloc, toit sur poteaux
        for (int x = 6; x <= 12; x++) for (int z = 20; z <= 22; z++) set(x, UY, z, "stone_bricks");
        for (int x = 8; x <= 10; x++) { set(x, UY - 1, 21, "stone"); set(x, UY, 21, B.WATER); }
        for (int[] p : new int[][]{{6, 20}, {12, 20}, {6, 22}, {12, 22}}) for (int y = UY + 1; y <= UY + 3; y++) set(p[0], y, p[1], "spruce_log[axis=y]");
        for (int x = 6; x <= 12; x++) for (int z = 20; z <= 22; z += 2) set(x, UY + 3, z, "spruce_log[axis=x]");
        roof('x', 6, 12, UY + 3, Mat.of("spruce_planks"), 20, 22);
        set(7, UY + 1, 21, "cauldron");
        set(11, UY + 1, 21, "barrel[facing=up,open=false]");
        // calvaire (x 16..18, z 20..22)
        for (int x = 16; x <= 18; x++) for (int z = 20; z <= 22; z++) set(x, UY + 1, z, (x == 17 && z == 21) ? "stone_bricks" : slab("stone_brick_slab", false));
        for (int y = UY + 2; y <= UY + 5; y++) set(17, y, 21, "cobblestone_wall");
        set(16, UY + 4, 21, "cobblestone_wall"); set(18, UY + 4, 21, "cobblestone_wall");
        // fumerie (x 26..31, z 19..22)
        int r0 = shell(26, 19, 31, 22, UY, 1, false, "cobblestone");
        roof('x', 26, 31, r0, wallP, 19, 22);
        chimney(32, 21, UY + 1, ridgeY(19, 22, r0) + 1);
        door(28, UY + 1, 19, "spruce_door", Dir.SOUTH, false, true);
        for (int x = 27; x <= 30; x++) { if (x != 28) set(x, UY + 1, 21, "smoker[facing=north,lit=false]"); set(x, UY + 3, 21, "iron_chain[axis=x,waterlogged=false]"); }
        set(27, UY + 1, 20, "barrel[facing=up,open=false]");
        set(30, UY + 1, 20, "dried_kelp_block");
        // puits (x 44..46, z 20..22)
        for (int x = 44; x <= 46; x++) for (int z = 20; z <= 22; z++) {
            boolean c = x == 45 && z == 21;
            set(x, UY + 1, z, c ? trap("spruce_trapdoor", Dir.NORTH, true, false) : "cobblestone");
            if (c) { set(x, UY - 1, z, "stone"); set(x, UY, z, B.WATER); }
        }
        set(44, UY + 2, 21, "spruce_fence"); set(46, UY + 2, 21, "spruce_fence");
        set(44, UY + 3, 21, "spruce_fence"); set(46, UY + 3, 21, "spruce_fence");
        for (int x = 44; x <= 46; x++) set(x, UY + 4, 21, slab("deepslate_tile_slab", false));
        set(45, UY + 3, 21, "iron_chain[axis=y,waterlogged=false]");
        // jardins : hortensias, muret, bancs, sentier
        int[][] hy = {{21, 22}, {35, 21}, {38, 23}, {50, 22}, {55, 21}, {66, 22}, {77, 21}, {83, 22}, {10, 23}};
        for (int[] h : hy) hydrangea(h[0], UY + 1, h[1]);
        for (int x = 47; x <= 57; x++) if (x % 5 != 1) set(x, UY + 1, 20, "cobblestone_wall");
        for (int x = 74; x <= 84; x++) if (x % 4 != 0) set(x, UY + 1, 20, "cobblestone_wall");
        bench(41, UY + 1, 23, Dir.NORTH, 2);
        bench(69, UY + 1, 23, Dir.NORTH, 3);
        for (int x = 22; x <= 84; x++) if (zoneAt(x, 19) == UPPER && hash(63, x, 0, 19) < 0.5 && B.name(get(x, UY, 19)).equals("grass_block")) set(x, UY, 19, "dirt_path");
        crates(79, UY + 1, 22, 2, 1, 530);
        kelpHeap(33, UY + 1, 22, 3, 1);
        lamp(24, UY + 1, 19);
        lamp(60, UY + 1, 19);
        lamp(83, UY + 1, 19);
    }

    /** Venelles en escalier du quai a la terrasse (entre les batiments du quai). */
    private void lanes() {
        int[][] ln = {{21, 22}, {34, 36}, {58, 59}, {71, 72}};
        for (int[] l : ln)
            for (int x = l[0]; x <= l[1]; x++) {
                for (int i = 0; i < 4; i++) {
                    int z = 28 - i, y = QY + 1 + i;
                    for (int yy = QY + 1; yy < y; yy++) set(x, yy, z, "cobblestone");
                    set(x, y, z, stairs("stone_brick_stairs", Dir.NORTH, false));
                    for (int yy = y + 1; yy <= y + 3; yy++) set(x, yy, z, B.AIR);
                }
                for (int yy = UY + 1; yy <= UY + 3; yy++) set(x, yy, 24, B.AIR);
            }
    }

    private void parapets() {
        for (int x = P1; x <= P2; x++) {
            if (zoneAt(x, 24) != UPPER || zoneAt(x, 25) == CLIFF) continue;
            if (!air(x, UY, 25) || !air(x, UY + 1, 24)) continue;
            set(x, UY + 1, 24, "cobblestone_wall");
        }
    }

    // ------------------------------------------------------------------ port : mole, escaliers, digue, estacade

    /** Volee de 3 marches + palier contre un mur de quai (dans le bassin), montant vers up. */
    private void quayStairs(int x, int z, Dir up) {
        for (int i = 0; i <= 3; i++) {
            int sx = x + i * up.dx, sz = z + i * up.dz, y = MY + 1 + i;
            for (int yy = MY; yy < y; yy++) set(sx, yy, sz, faceP.clustered(sx, yy, sz));
            if (i == 3) y = MY + 3;
            for (int yy = MY; yy < y; yy++) set(sx, yy, sz, faceP.clustered(sx, yy, sz));
            set(sx, y, sz, i == 3 ? "stone_bricks" : stairs("stone_brick_stairs", up, false));
            for (int yy = y + 1; yy <= y + 2; yy++) set(sx, yy, sz, B.AIR);
        }
    }

    private void ladder(int x, int z, Dir facing) {
        for (int y = MY + 1; y <= QY; y++) set(x, y, z, B.state("ladder", "facing", facing.id(), "waterlogged", "false"));
    }

    private void bollard(int x, int y, int z) {
        set(x, y, z, "polished_deepslate_wall");
    }

    private void harbour() {
        // mole : bittes, abri, feu vert au bout, escalier vers la vase
        for (int z = 51; z <= 66; z += 5) { bollard(43, QY + 1, z); bollard(47, QY + 1, z); }
        for (int[] p : new int[][]{{44, 62}, {46, 62}, {44, 65}, {46, 65}}) for (int y = QY + 1; y <= QY + 3; y++) set(p[0], y, p[1], "spruce_fence");
        for (int x = 43; x <= 47; x++) for (int z = 61; z <= 66; z++) set(x, QY + 4, z, slab("deepslate_tile_slab", (x == 43 || x == 47 || z == 61 || z == 66)));
        for (int x = 44; x <= 46; x++) set(x, QY + 4, 63, "deepslate_tiles");
        set(45, QY + 3, 64, hangingLantern());
        bench(45, QY + 1, 65, Dir.SOUTH, 1);
        netPile(44, QY + 1, 57, "green_wool");
        for (int y = QY + 1; y <= QY + 3; y++) set(47, y, 68, "cobblestone_wall");
        set(47, QY + 4, 68, "green_stained_glass");
        set(47, QY + 5, 68, lantern(false));
        for (int x = 44; x <= 46; x++)
            for (int i = 0; i < 4; i++) {
                int z = 69 + i, y = QY - i;
                for (int yy = MY; yy < y; yy++) set(x, yy, z, faceP.clustered(x, yy, z));
                set(x, y, z, stairs("stone_brick_stairs", Dir.NORTH, false));
                for (int yy = y + 1; yy <= y + 3; yy++) set(x, yy, z, B.AIR);
            }
        for (int i = 0; i < 4; i++) for (int x : new int[]{43, 47}) for (int y = MY + 1; y <= QY - i; y++) set(x, y, 69 + i, faceP.clustered(x, y, 69 + i));
        // escaliers de quai et echelles
        quayStairs(39, 49, Dir.EAST);
        quayStairs(75, 49, Dir.WEST);
        quayStairs(10, 63, Dir.NORTH);
        quayStairs(80, 53, Dir.NORTH);
        quayStairs(24, 79, Dir.EAST);
        quayStairs(63, 79, Dir.WEST);
        for (int x : new int[]{25, 34, 55, 66}) ladder(x, 49, Dir.SOUTH);
        for (int z : new int[]{52, 72}) ladder(10, z, Dir.EAST);
        ladder(42, 56, Dir.WEST);
        ladder(48, 61, Dir.EAST);
        for (int x : new int[]{38, 47, 56}) ladder(x, 79, Dir.NORTH);
        for (int x : new int[]{70, 76}) ladder(x, 73, Dir.NORTH);
        ladder(80, 66, Dir.WEST);
        // digue : parapet cote mer, bittes, bancs, cabane de pecheur
        for (int x = 18; x <= 85; x++) {
            if (zoneAt(x, 85) != QUAY) continue;
            set(x, QY + 1, 85, (x % 6 == 0) ? "stone_bricks" : faceP.clustered(x, QY + 1, 85));
            if (x % 6 == 0) set(x, QY + 2, 85, slab("stone_brick_slab", false));
        }
        for (int x : new int[]{22, 30, 42, 51, 59, 66}) bollard(x, QY + 1, 80);
        bench(44, QY + 1, 84, Dir.SOUTH, 2);
        bench(55, QY + 1, 84, Dir.SOUTH, 2);
        lamp(27, QY + 1, 84);
        lamp(50, QY + 1, 84);
        lamp(64, QY + 1, 84);
        int r0 = shell(31, 82, 35, 84, QY, 1, false, "spruce_planks");
        roof('x', 31, 35, r0, wallP, 82, 84);
        air(33, QY + 1, 82, 33, QY + 2, 82);
        set(32, QY + 1, 83, "barrel[facing=up,open=false]");
        set(34, QY + 1, 83, "loom[facing=north]");
        crates(37, QY + 1, 83, 2, 2, 540);
        netPile(60, QY + 1, 82, "blue_wool");
        kelpHeap(19, QY + 1, 82, 2, 2);
        // estacade de la passe : passerelle en y 64 sur un rang de pieux jointifs (on ne passe pas dessous)
        for (int x = 10; x <= 17; x++) {
            for (int z = 84; z <= 85; z++) set(x, QY, z, "spruce_planks");
            for (int y = MY + 1; y < QY; y++) set(x, y, 85, (x % 3 == 1) ? "spruce_log[axis=y]" : "spruce_fence");
            set(x, QY + 1, 85, "spruce_fence");
            set(x, MY, 85, "mud");
        }
        // pointe ouest : rochers et casiers
        rock(6, QY + 1, 83, 1.6, 1.2, 1.4, 541);
        crates(7, QY + 1, 80, 2, 1, 542);
    }

    private void lighthouse(int cx, int cz) {
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double r2 = dx * dx + dz * dz;
            int x = cx + dx, z = cz + dz;
            for (int y = QY + 1; y <= 92; y++) {
                if (r2 <= 3.5 * 3.5 && y <= 83) {
                    boolean shell = r2 > 2.5 * 2.5;
                    if (shell) {
                        String st = y <= QY + 2 ? "stone_bricks" : (y >= 78 && y <= 79) ? "red_terracotta" : whiteP.at(x, y, z);
                        set(x, y, z, st);
                    } else set(x, y, z, (y == 74) ? "spruce_planks" : B.AIR);
                }
            }
            if (r2 <= 5.5 * 5.5) set(x, 84, z, "stone_bricks");
            if (r2 > 3.5 * 3.5 && r2 <= 5.5 * 5.5) {
                Dir f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Dir.WEST : Dir.EAST) : (dz > 0 ? Dir.NORTH : Dir.SOUTH);
                set(x, 83, z, stairs("stone_brick_stairs", f, true));
                if (r2 > 4.5 * 4.5) set(x, 85, z, "iron_bars");
            }
            if (r2 <= 3.5 * 3.5) {
                boolean ring = r2 > 2.5 * 2.5;
                for (int y = 85; y <= 87; y++) set(x, y, z, ring ? "glass_pane" : B.AIR);
                set(x, 88, z, "oxidized_copper");
            }
            if (r2 <= 2.5 * 2.5) set(x, 89, z, "oxidized_copper");
            if (r2 <= 1.5 * 1.5) set(x, 90, z, "oxidized_copper");
        }
        set(cx, 91, cz, "lightning_rod[facing=up,powered=false,waterlogged=false]");
        set(cx, 85, cz, "iron_block");
        set(cx, 86, cz, "sea_lantern");
        air(cx + 3, 85, cz, cx + 3, 86, cz);
        // porte au nord, echelle au sud, fenetres
        air(cx, QY + 1, cz - 3, cx, QY + 2, cz - 3);
        set(cx, QY + 3, cz - 3, "stone_bricks");
        for (int y = QY + 1; y <= 84; y++) set(cx, y, cz + 2, "ladder[facing=north,waterlogged=false]");
        for (int y : new int[]{70, 76, 81}) { set(cx - 3, y, cz, "glass_pane"); set(cx + 3, y, cz, "glass_pane"); }
        set(cx - 1, 75, cz - 1, "barrel[facing=up,open=false]");
        set(cx + 1, 75, cz - 1, lantern(false));
        set(cx - 2, QY + 1, cz, "barrel[facing=up,open=false]");
        set(cx + 2, QY + 1, cz, "coal_block");
    }

    private void keeperHouse() {
        int x1 = 69, z1 = 76, x2 = 75, z2 = 81;
        int r0 = shell(x1, z1, x2, z2, QY, 1, true, "spruce_planks");
        roof('x', x1, x2, r0, whiteP, z1, z2);
        chimney(x1 - 1, 78, QY + 1, ridgeY(z1, z2, r0) + 1);
        windows(x1, z1, x2, z2, QY, 1, 72, -1, "warped_trapdoor", 3);
        doorway(72, QY + 1, z2, Dir.SOUTH, "spruce_door");
        set(70, QY + 1, 77, "furnace[facing=south,lit=false]");
        set(71, QY + 1, 77, "bookshelf");
        set(74, QY + 1, 77, "blue_wool"); set(74, QY + 1, 78, "blue_wool");
        set(70, QY + 1, 79, "barrel[facing=up,open=false]");
        set(74, QY + 1, 80, "cartography_table");
        set(72, ridgeY(z1, z2, r0) - 1, 78, hangingLantern());
        hydrangea(68, QY + 1, 83);
    }

    private void pontoon() {
        for (int x = 60; x <= 79; x++)
            for (int z = 62; z <= 63; z++) {
                set(x, MY + 2, z, "oak_planks");
                set(x, MY + 1, z, (x % 4 == 0) ? "orange_wool" : (x % 4 == 2) ? "barrel[facing=up,open=false]" : B.AIR);
            }
        for (int z = 62; z <= 63; z++) {
            set(79, MY + 3, z, stairs("spruce_stairs", Dir.EAST, false));
            set(80, MY + 4, z, stairs("spruce_stairs", Dir.EAST, false));
            for (int y = MY + 1; y <= MY + 3; y++) set(80, y, z, "spruce_fence");
        }
        for (int fx : new int[]{63, 69, 75})
            for (int x = fx; x <= fx + 1; x++) {
                for (int z = 64; z <= 71; z++) {
                    set(x, MY + 2, z, "oak_planks");
                    set(x, MY + 1, z, (z % 3 == 0) ? "barrel[facing=up,open=false]" : B.AIR);
                }
                set(x, MY + 1, 72, stairs("oak_stairs", Dir.NORTH, false));
            }
        for (int x = 61; x <= 74; x += 6) { set(x, MY + 3, 62, "spruce_fence"); set(x, MY + 4, 62, lantern(false)); }
        set(64, MY + 3, 70, "barrel[facing=up,open=false]");
        netPile(76, MY + 3, 66, "blue_wool");
        set(70, MY + 3, 65, "orange_wool");
    }

    // ------------------------------------------------------------------ bateaux

    /**
     * Coque posee (u le long de dir, 0 = poupe ; v en travers). mode 0 = bateau ponte, 1 = membrures (chantier),
     * 2 = epave, 3 = canot ouvert, 4 = canot retourne. gapU/gapV : coupee du pavois (passerelle), -99 = aucune.
     */
    private void hull(int cx, int cz, Dir dir, int len, int beam, int depth, int y0, String band, int mode,
                      boolean wheelhouse, boolean sternLadder, int gapU, int gapV, long seed) {
        Dir cw = dir.cw();
        int hb = beam / 2, half = len / 2;
        int[][] w = new int[len + 2][depth + 1];
        for (int u = -1; u <= len; u++)
            for (int d = 0; d <= depth; d++) {
                if (u < 0 || u >= len) { w[u + 1][d] = -1; continue; }
                double t = u / (double) (len - 1);
                double plan = t < 0.12 ? 0.8 + t / 0.12 * 0.2 : t < 0.55 ? 1 : Math.sqrt(Math.max(0, (1 - t) / 0.45));
                double sec = 0.45 + 0.55 * Math.sqrt(d / (double) depth);
                w[u + 1][d] = (int) Math.round(hb * plan * sec);
            }
        for (int u = 0; u < len; u++)
            for (int d = 0; d <= depth; d++) {
                int wu = w[u + 1][d];
                for (int v = -wu; v <= wu; v++) {
                    int av = Math.abs(v);
                    int x = cx + (u - half) * dir.dx + v * cw.dx, z = cz + (u - half) * dir.dz + v * cw.dz;
                    int y = mode == 4 ? y0 + (depth - d) : y0 + d;
                    boolean interior = d > 0 && d < depth && av < wu && av <= w[u + 1][d - 1] && av < w[u][d] && av < w[u + 2][d];
                    boolean openTop = (mode == 3 || mode == 4) && d == depth && av < wu && av < w[u][d] && av < w[u + 2][d];
                    String st;
                    if (mode == 1 || mode == 2) {
                        boolean keel = v == 0 && d == 0;
                        if (keel) st = "stripped_spruce_log[axis=" + dir.axis() + "]";
                        else if (interior || (d == depth && av < wu)) st = null;
                        else if (mode == 1 && u < len / 3 && d <= 1) st = "dark_oak_planks";
                        else if (u % 2 == 0 && (mode == 1 || hash(seed, u, d, v) < 0.75) && (mode == 1 || d <= 3)) st = "dark_oak_fence";
                        else st = null;
                        if (st == null) { if (y > MY) set(x, y, z, B.AIR); continue; }
                    } else if (mode == 4 && (interior || openTop)) st = "dark_oak_planks";
                    else if (mode == 3 && interior && u == half && v == 0) st = slab("spruce_slab", true);
                    else if (interior || openTop) st = B.AIR;
                    else if (d == depth && mode == 0) st = av == wu ? band : (u % 4 == 0 ? "dark_oak_planks" : "spruce_planks");
                    else if (d <= 1) st = "coal_block";
                    else if (d == depth - 1 || (mode >= 3 && d == depth)) st = band;
                    else st = "dark_oak_planks";
                    set(x, y, z, st);
                }
            }
        if (mode == 0) {
            // pavois et etrave
            for (int u = 0; u < len; u++) {
                int wu = w[u + 1][depth];
                for (int v = -wu; v <= wu; v++) {
                    int av = Math.abs(v);
                    boolean rim = av == wu || av >= w[u][depth] || av >= w[u + 2][depth];
                    if (!rim) continue;
                    if (u == gapU && Integer.signum(v) == Integer.signum(gapV) && av == wu) continue;
                    if (sternLadder && u == 0 && v == 0) continue;
                    int x = cx + (u - half) * dir.dx + v * cw.dx, z = cz + (u - half) * dir.dz + v * cw.dz;
                    set(x, y0 + depth + 1, z, (u == len - 1) ? "dark_oak_planks" : band);
                }
            }
            int xb = cx + (len - 1 - half) * dir.dx, zb = cz + (len - 1 - half) * dir.dz;
            set(xb, y0 + depth + 2, zb, "dark_oak_fence");
            // descente vers la cale (3 marches) sous une ecoutille
            if (depth >= 4) {
                int uh = half - 2;
                for (int i = 0; i < 4; i++) {
                    int x = cx + (uh + i - half) * dir.dx, z = cz + (uh + i - half) * dir.dz;
                    if (i <= 2) set(x, y0 + depth, z, B.AIR);
                    set(x, y0 + 1 + i, z, stairs("spruce_stairs", dir, false));
                    for (int yy = y0 + 1; yy < y0 + 1 + i; yy++) set(x, yy, z, "spruce_planks");
                }
                // cargaison dans la cale
                for (int u = 1; u < len - 2; u++) for (int v : new int[]{-2, 2}) {
                    int x = cx + (u - half) * dir.dx + v * cw.dx, z = cz + (u - half) * dir.dz + v * cw.dz;
                    if (air(x, y0 + 1, z) && hash(seed + 9, u, 0, v) < 0.45)
                        set(x, y0 + 1, z, hash(seed + 10, u, 0, v) < 0.5 ? "barrel[facing=up,open=false]" : (hash(seed + 11, u, 0, v) < 0.5 ? "blue_wool" : "composter[level=0]"));
                }
                int xl = cx + (half - 1) * dir.dx, zl = cz + (half - 1) * dir.dz;
                if (air(xl, y0 + 3, zl)) set(xl, y0 + 3, zl, hangingLantern());
            }
            // timonerie
            if (wheelhouse) {
                for (int u = 1; u <= 4; u++) for (int v = -1; v <= 1; v++) {
                    int x = cx + (u - half) * dir.dx + v * cw.dx, z = cz + (u - half) * dir.dz + v * cw.dz;
                    boolean edge = u == 1 || u == 4 || Math.abs(v) == 1;
                    for (int dy = 1; dy <= 3; dy++) {
                        int y = y0 + depth + dy;
                        String st;
                        if (!edge) st = B.AIR;
                        else if (u == 4 && v == 0 && dy <= 2) st = B.AIR;
                        else if (dy == 2 && !(Math.abs(v) == 1 && (u == 1 || u == 4))) st = "glass_pane";
                        else st = "white_wool";
                        set(x, y, z, st);
                    }
                    set(x, y0 + depth + 4, z, slab("spruce_slab", false));
                }
                int xw = cx + (2 - half) * dir.dx, zw = cz + (2 - half) * dir.dz;
                set(xw, y0 + depth + 1, zw, "iron_block");
                set(xw + dir.dx, y0 + depth + 3, zw + dir.dz, hangingLantern());
            }
            // mat, baume et voile ferlee
            int um = Math.max((int) Math.round(len * 0.66), half + 3);
            int xm = cx + (um - half) * dir.dx, zm = cz + (um - half) * dir.dz;
            int mh = len >= 15 ? 9 : 7;
            for (int dy = 1; dy <= mh; dy++) set(xm, y0 + depth + dy, zm, "spruce_log[axis=y]");
            set(xm, y0 + depth + mh + 1, zm, lantern(false));
            for (int i = 1; i <= 4; i++) {
                int x = xm - i * dir.dx, z = zm - i * dir.dz;
                set(x, y0 + depth + 3, z, "spruce_fence");
                set(x, y0 + depth + 4, z, "white_wool");
            }
            // pont : treuil, caisses a poisson, filets
            int[] us = {um + 2, um - 6, half + 1};
            for (int k = 0; k < us.length; k++) {
                int u = us[k];
                if (u <= 4 || u >= len - 1) continue;
                int v = (k % 2 == 0) ? 1 : -1;
                int x = cx + (u - half) * dir.dx + v * cw.dx, z = cz + (u - half) * dir.dz + v * cw.dz;
                if (!air(x, y0 + depth + 1, z)) continue;
                String[] g = {"iron_block", "composter[level=0]", "blue_wool"};
                set(x, y0 + depth + 1, z, g[k]);
            }
            if (sternLadder) {
                int x = cx + (-1 - half) * dir.dx, z = cz + (-1 - half) * dir.dz;
                for (int y = y0; y <= y0 + depth; y++) set(x, y, z, B.state("ladder", "facing", dir.opposite().id(), "waterlogged", "false"));
            }
        }
    }

    private void boats() {
        // chalutier bleu le long du quai (passerelle x 30), sardinier vert, caseyeur orange au milieu du bassin
        hull(30, 54, Dir.EAST, 17, 7, 4, MY + 1, "blue_wool", 0, true, true, 8, -1, 601);
        for (int z = 49; z <= 50; z++) { set(30, QY + 1, z, "spruce_planks"); set(29, QY + 2, z, "spruce_fence"); set(31, QY + 2, z, "spruce_fence"); }
        hull(57, 53, Dir.WEST, 13, 5, 4, MY + 1, "green_wool", 0, false, true, 6, 1, 602);
        for (int z = 49; z <= 50; z++) { set(57, QY + 1, z, "spruce_planks"); set(56, QY + 2, z, "spruce_fence"); set(58, QY + 2, z, "spruce_fence"); }
        hull(25, 68, Dir.EAST, 11, 5, 4, MY + 1, "orange_wool", 0, false, true, -99, 0, 603);
        // epave a demi enfouie, canots
        hull(36, 75, Dir.EAST, 11, 5, 4, MY - 1, "dark_oak_planks", 2, false, false, -99, 0, 604);
        hull(66, 68, Dir.SOUTH, 5, 3, 2, MY + 1, "white_wool", 3, false, false, -99, 0, 605);
        hull(72, 67, Dir.SOUTH, 5, 3, 2, MY + 1, "blue_wool", 3, false, false, -99, 0, 606);
        hull(53, 76, Dir.EAST, 5, 3, 2, MY + 1, "green_wool", 3, false, false, -99, 0, 607);
        hull(66, 41, Dir.EAST, 5, 3, 2, QY + 1, "orange_wool", 4, false, false, -99, 0, 608);
        hull(6, 69, Dir.SOUTH, 5, 3, 2, QY + 1, "white_wool", 4, false, false, -99, 0, 609);
        // grue du quai au-dessus du chalutier
        for (int x = 30; x <= 32; x++) for (int z = 45; z <= 47; z++) set(x, QY + 1, z, "stone_bricks");
        set(31, QY + 2, 46, "iron_block");
        for (int y = QY + 3; y <= QY + 8; y++) set(31, y, 46, "spruce_log[axis=y]");
        for (int z = 46; z <= 52; z++) set(31, QY + 9, z, "spruce_log[axis=z]");
        for (int y = QY + 5; y <= QY + 8; y++) set(31, y, 52, "iron_chain[axis=y,waterlogged=false]");
        set(31, QY + 4, 52, "barrel[facing=up,open=false]");
    }

    private void basinDetails() {
        rock(15, MY + 1, 60, 2.2, 1.6, 2.0, 701);
        rock(19, MY + 1, 75, 1.8, 1.5, 1.6, 702);
        rock(38, MY + 1, 63, 2.0, 1.5, 1.6, 703);
        rock(51, MY + 1, 67, 1.8, 1.8, 1.8, 704);
        rock(59, MY + 1, 75, 2.4, 1.4, 1.8, 705);
        rock(78, MY + 1, 57, 1.6, 1.4, 1.6, 706);
        rock(13, MY + 1, 70, 1.5, 1.3, 1.5, 707);
        rock(49, MY + 1, 59, 1.4, 1.2, 1.4, 708);
        rock(29, MY + 1, 62, 1.4, 1.2, 1.2, 709);
        rock(66, MY + 1, 74, 1.5, 1.2, 1.4, 710);
        rock(44, MY + 1, 77, 1.6, 1.3, 1.4, 711);
        // vieux pieux d'une ancienne pecherie
        for (int i = 0; i < 8; i++) {
            int x = 50 + i, z = 70 + i / 2;
            int h = 1 + (int) (hash(712, x, 0, z) * 3);
            for (int y = MY + 1; y <= MY + h; y++) set(x, y, z, "spruce_log[axis=y]");
        }
        for (int i = 0; i < 6; i++) {
            int x = 22 + i * 2, z = 59;
            int h = 1 + (int) (hash(713, x, 0, z) * 2);
            for (int y = MY + 1; y <= MY + h; y++) set(x, y, z, "spruce_log[axis=y]");
        }
        // bouees couchees sur la vase, chaines d'amarrage, casiers
        int[][] bu = {{20, 62}, {41, 59}, {57, 63}, {67, 57}, {29, 77}, {48, 72}, {12, 77}, {62, 69}, {34, 66}, {77, 61}};
        for (int[] b : bu) if (air(b[0], MY + 1, b[1])) set(b[0], MY + 1, b[1], "orange_wool");
        int[][] cs = {{16, 66}, {55, 59}, {41, 70}, {24, 74}, {70, 59}};
        for (int[] c : cs) crates(c[0], MY + 1, c[1], 2, 1, 714 + c[0]);
        kelpHeap(32, MY + 1, 69, 3, 2);
        kelpHeap(64, MY + 1, 77, 3, 1);
    }

    // ------------------------------------------------------------------ quai : decor

    private void quayProps() {
        int y = QY + 1;
        netPile(35, y, 46, "blue_wool");
        netPile(60, y, 41, "green_wool");
        netPile(79, y, 46, "blue_wool");
        netPile(7, y, 74, "green_wool");
        kelpHeap(38, y, 46, 4, 2);
        kelpHeap(67, y, 45, 3, 2);
        for (int x = 43; x <= 54; x++) if (x % 3 != 0 && x != 47) set(x, y, 46, x % 2 == 0 ? "composter[level=0]" : "barrel[facing=up,open=false]");
        crates(50, y, 46, 2, 1, 801);
        crates(62, y, 38, 2, 2, 802);
        crates(75, y, 38, 2, 2, 803);
        crates(82, y, 50, 2, 2, 804);
        crates(82, y, 69, 2, 2, 805);
        for (int z = 38; z <= 40; z++) { set(73, y, z, "spruce_log[axis=x]"); set(74, y, z, "spruce_log[axis=x]"); if (z != 40) set(73, y + 1, z, "spruce_log[axis=x]"); }
        set(83, y, 44, "iron_block"); set(83, y + 1, 44, "coal_block");
        bench(22, y, 46, Dir.NORTH, 2);
        bench(57, y, 47, Dir.NORTH, 2);
        bench(70, y, 47, Dir.NORTH, 2);
        for (int x : new int[]{22, 27, 36, 41, 52, 61, 64, 70, 77, 82}) if (air(x, y, 48) && zoneAt(x, 49) == BASIN) bollard(x, y, 48);
        for (int z = 52; z <= 78; z += 6) if (air(9, y, z)) bollard(9, y, z);
        for (int z = 51; z <= 72; z += 7) if (air(81, y, z)) bollard(81, y, z);
        lamp(21, y, 40);
        lamp(36, y, 40);
        lamp(59, y, 43);
        lamp(84, y, 40);
        lamp(8, y, 60);
        lamp(84, y, 60);
        // sechoir a filets sur le quai ouest
        for (int z = 52; z <= 57; z++) {
            set(6, y + 2, z, "spruce_fence");
            if (z == 52 || z == 57) { set(6, y, z, "spruce_fence"); set(6, y + 1, z, "spruce_fence"); }
            else set(6, y + 1, z, (z % 2 == 0) ? "green_wool" : "blue_wool");
        }
        for (int z = 62; z <= 66; z++) if (z != 64) set(5, y, z, "barrel[facing=up,open=false]");
        set(5, y + 1, 63, "barrel[facing=up,open=false]");
        kelpHeap(5, y, 76, 2, 2);
        // quai est
        netPile(83, y, 56, "green_wool");
        for (int z = 64; z <= 66; z++) set(84, y, z, "orange_wool");
        set(84, y + 1, 65, "orange_wool");
        // esplanade
        crates(82, y, 75, 2, 2, 806);
        bench(78, y, 75, Dir.NORTH, 2);
    }

    // ------------------------------------------------------------------ rideau, eau

    /** Rideau de barrieres sur l'anneau exterieur, seulement AU-DESSUS du decor (falaise, digue, enrochement). */
    private void curtain() {
        for (int a = RING1; a <= RING2; a++) {
            int[][] cells = {{RING1, a}, {RING2, a}, {a, RING1}, {a, RING2}};
            for (int[] c : cells) {
                int yTop = 50;
                for (int y = CY + 1; y >= 50; y--) {
                    String n = B.name(get(c[0], y, c[1]));
                    if (!B.isAir(get(c[0], y, c[1])) && !n.endsWith("_leaves")) { yTop = y; break; }
                }
                for (int y = yTop + 1; y <= CY + 1; y++) if (air(c[0], y, c[1])) set(c[0], y, c[1], "barrier");
            }
        }
    }

    /** Aucune eau a cote d'une case d'air (elle coulerait a la premiere mise a jour) : remplacee par le sol. */
    private void waterCheck() {
        for (int pass = 0; pass < 3; pass++)
            for (int x = LX1; x <= LX2; x++)
                for (int z = LZ1; z <= LZ2; z++)
                    for (int y = 50; y <= 75; y++) {
                        String st = get(x, y, z);
                        if (!B.name(st).equals("water") && !B.name(st).equals("seagrass")) continue;
                        boolean bad = air(x, y - 1, z);
                        for (Dir d : Dir.values()) {
                            if (x + d.dx < LX1 || x + d.dx > LX2 || z + d.dz < LZ1 || z + d.dz > LZ2) { bad = true; continue; }
                            if (air(x + d.dx, y, z + d.dz)) bad = true;
                        }
                        if (bad) set(x, y, z, y <= SEA ? "sand" : "mud");
                    }
    }
}
