package fr.eemicraft.horscours;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.util.BoundingBox;

/**
 * Niveau 1 « Le Couloir » : labyrinthe VIVANT de bureaux de l'école.
 * Grille de N x N cellules de 5 x 5 blocs, murs d'un bloc (pas de 6), sol y 64, plafond y 68.
 * Chaque cellule a un thème (couloir, bureau, salle de cours, réserve, coin pause) meublé avec les
 * modèles 3D d'EemiMeubles (ItemDisplay + barrières). Certaines cloisons sont « mobiles » : jamais
 * meublées, elles s'ouvrent et se ferment quand personne ne regarde (voir Niveau1).
 * Les meubles ne sont posés que contre des murs fermés et fixes : les passages restent libres.
 */
final class Maze {
    static final int Y = 64, P = 6;
    static final int OX = 1000, OZ = 0;
    static final int COULOIR = 0, BUREAU = 1, COURS = 2, RESERVE = 3, PAUSE = 4;
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Un meuble posé : modèle eemicraft:<model>, case d'ancrage, hauteur (0 sol, 1 au mur/sur un meuble). */
    static final class Piece {
        final String model, role;
        final int x, z, yOff, barrier;
        final float yaw, scale;
        final List<int[]> tiles = new ArrayList<>();
        int cellI, cellJ;
        String content = ""; // fuse / pile / pince / rien / photo...
        Piece(String model, int x, int z, int yOff, float yaw, float scale, int barrier, String role) {
            this.model = model;
            this.x = x;
            this.z = z;
            this.yOff = yOff;
            this.yaw = yaw;
            this.scale = scale;
            this.barrier = barrier;
            this.role = role;
        }
    }

    final World w;
    final int n;
    boolean[][] east, south; // passage entre (i,j) et (i+1,j) / (i,j+1)
    boolean[][] doorE, doorS;
    boolean[][] movE, movS;  // cloisons mobiles
    int[][] theme;
    final List<int[]> lights = new ArrayList<>();
    final List<Object[]> labels = new ArrayList<>(); // {x, y, z, yaw, texte}
    final List<Piece> pieces = new ArrayList<>();
    final List<int[]> freeSpots = new ArrayList<>(); // cases libres (coins) pour les mannequins
    final List<int[]> fuseCells = new ArrayList<>();
    final List<String> fuseKinds = new ArrayList<>(); // shelf / lock / code, même ordre que fuseCells
    int[] pinceCell, boardCell;
    final List<int[]> pileCells = new ArrayList<>();
    String code = "000";
    int[] exitCell;
    BlockFace exitSide;
    BoundingBox exitRoom;
    final List<int[]> exitDoor = new ArrayList<>();
    private final Set<Long> used = new HashSet<>();
    private long seed;
    private double litCells;

    Maze(World w, int n) {
        this.w = w;
        this.n = n;
    }

    int cx(int i) {
        return OX + i * P + 3;
    }

    int cz(int j) {
        return OZ + j * P + 3;
    }

    Location center(int i, int j) {
        return new Location(w, cx(i) + 0.5, Y, cz(j) + 0.5);
    }

    Location start() {
        return new Location(w, cx(0) + 0.5, Y, cz(0) + 0.5, -45, 0);
    }

    /** Cellule qui contient (x, z), ou null hors du labyrinthe. */
    int[] cellAt(double x, double z) {
        int i = (int) Math.floor((x - OX) / P), j = (int) Math.floor((z - OZ) / P);
        if (i < 0 || j < 0 || i >= n || j >= n) return null;
        return new int[] {i, j};
    }

    boolean open(int i, int j, int di, int dj) {
        int ni = i + di, nj = j + dj;
        if (ni < 0 || nj < 0 || ni >= n || nj >= n) return false;
        if (di == 1) return east[i][j];
        if (di == -1) return east[ni][nj];
        if (dj == 1) return south[i][j];
        return south[ni][nj];
    }

    boolean movable(int i, int j, int di, int dj) {
        int ni = i + di, nj = j + dj;
        if (ni < 0 || nj < 0 || ni >= n || nj >= n) return false;
        if (di == 1) return movE[i][j];
        if (di == -1) return movE[ni][nj];
        if (dj == 1) return movS[i][j];
        return movS[ni][nj];
    }

    /** Distances (en cellules) depuis une cellule, -1 = inatteignable. */
    int[][] bfs(int si, int sj) {
        int[][] d = new int[n][n];
        for (int[] r : d) java.util.Arrays.fill(r, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        d[si][sj] = 0;
        q.add(new int[] {si, sj});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : DIRS) {
                int ni = c[0] + dd[0], nj = c[1] + dd[1];
                if (open(c[0], c[1], dd[0], dd[1]) && d[ni][nj] < 0) {
                    d[ni][nj] = d[c[0]][c[1]] + 1;
                    q.add(new int[] {ni, nj});
                }
            }
        }
        return d;
    }

    boolean allConnected() {
        int[][] d = bfs(0, 0);
        for (int[] r : d) for (int v : r) if (v < 0) return false;
        return true;
    }

    void generate(long seed, int fuses, int piles, double litCells) {
        Random r = new Random(seed);
        east = new boolean[n][n];
        south = new boolean[n][n];
        doorE = new boolean[n][n];
        doorS = new boolean[n][n];
        movE = new boolean[n][n];
        movS = new boolean[n][n];
        theme = new int[n][n];
        // Labyrinthe parfait (parcours en profondeur)
        boolean[][] seen = new boolean[n][n];
        ArrayDeque<int[]> st = new ArrayDeque<>();
        st.push(new int[] {0, 0});
        seen[0][0] = true;
        while (!st.isEmpty()) {
            int[] c = st.peek();
            List<int[]> nb = new ArrayList<>();
            for (int[] dd : DIRS) {
                int ni = c[0] + dd[0], nj = c[1] + dd[1];
                if (ni >= 0 && nj >= 0 && ni < n && nj < n && !seen[ni][nj]) nb.add(dd);
            }
            if (nb.isEmpty()) {
                st.pop();
                continue;
            }
            int[] dd = nb.get(r.nextInt(nb.size()));
            carve(c[0], c[1], dd[0], dd[1]);
            seen[c[0] + dd[0]][c[1] + dd[1]] = true;
            st.push(new int[] {c[0] + dd[0], c[1] + dd[1]});
        }
        // Boucles : ~12 % de murs en plus ouverts
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (i < n - 1 && !east[i][j] && r.nextDouble() < 0.12) east[i][j] = true;
                if (j < n - 1 && !south[i][j] && r.nextDouble() < 0.12) south[i][j] = true;
            }

        int[][] d = bfs(0, 0);
        // Sortie : cellule du bord la plus loin du départ
        int best = -1;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                boolean border = i == 0 || j == 0 || i == n - 1 || j == n - 1;
                if (border && d[i][j] > best && !(i == 0 && j == 0)) {
                    best = d[i][j];
                    exitCell = new int[] {i, j};
                }
            }
        int ei = exitCell[0], ej = exitCell[1];
        exitSide = ei == n - 1 ? BlockFace.EAST : ej == n - 1 ? BlockFace.SOUTH : ei == 0 ? BlockFace.WEST : BlockFace.NORTH;

        // Cellules des fusibles : culs-de-sac loin du départ, bien répartis
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (!(i == 0 && j == 0) && !(i == ei && j == ej) && d[i][j] >= 3) cells.add(new int[] {i, j});
        Collections.shuffle(cells, r);
        cells.sort((a, b2) -> Integer.compare(openings(b2[0], b2[1]) == 1 ? 1 : 0, openings(a[0], a[1]) == 1 ? 1 : 0));
        fuseCells.clear();
        for (int[] c : cells) {
            if (fuseCells.size() >= fuses) break;
            boolean far = true;
            for (int[] f : fuseCells) if (Math.abs(f[0] - c[0]) + Math.abs(f[1] - c[1]) < 4) far = false;
            if (far) fuseCells.add(c);
        }
        for (int[] c : cells) {
            if (fuseCells.size() >= fuses) break;
            if (!contains(fuseCells, c)) fuseCells.add(c);
        }
        // Nature de chaque fusible : sur une étagère, derrière un cadenas, derrière un code
        fuseKinds.clear();
        String[] kinds = {"lock", "code", "shelf", "lock", "shelf", "shelf", "lock"};
        for (int k = 0; k < fuseCells.size(); k++) fuseKinds.add(kinds[k % kinds.length]);
        Collections.shuffle(fuseKinds, r);
        // Pince coupante : assez loin du départ (5 à 10 cellules), hors cellules de fusibles
        List<int[]> mid = new ArrayList<>();
        for (int[] c : cells) if (d[c[0]][c[1]] >= 4 && d[c[0]][c[1]] <= 10 && !contains(fuseCells, c)) mid.add(c);
        if (mid.isEmpty()) for (int[] c : cells) if (!contains(fuseCells, c)) mid.add(c);
        pinceCell = mid.get(r.nextInt(mid.size()));
        // Tableau blanc avec le code : une autre cellule
        List<int[]> rest = new ArrayList<>();
        for (int[] c : cells) if (!contains(fuseCells, c) && !same(c, pinceCell) && openings(c[0], c[1]) <= 2) rest.add(c);
        if (rest.isEmpty()) for (int[] c : cells) if (!contains(fuseCells, c) && !same(c, pinceCell)) rest.add(c);
        boardCell = rest.get(r.nextInt(rest.size()));
        code = String.format("%03d", r.nextInt(1000));
        pileCells.clear();
        Collections.shuffle(cells, r);
        for (int[] c : cells) {
            if (pileCells.size() >= piles) break;
            if (!contains(fuseCells, c) && !same(c, pinceCell)) pileCells.add(c);
        }

        // Thèmes des cellules
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double t = r.nextDouble();
                theme[i][j] = t < 0.36 ? COULOIR : t < 0.62 ? BUREAU : t < 0.76 ? COURS : t < 0.93 ? RESERVE : PAUSE;
            }
        theme[0][0] = COULOIR;
        for (int[] c : fuseCells) if (theme[c[0]][c[1]] == COULOIR || theme[c[0]][c[1]] == COURS) theme[c[0]][c[1]] = RESERVE;
        theme[pinceCell[0]][pinceCell[1]] = BUREAU;
        theme[boardCell[0]][boardCell[1]] = COURS;

        // Cloisons mobiles : murs intérieurs entre deux cellules « ordinaires », ~28 %
        Set<String> special = new HashSet<>();
        special.add("0,0");
        special.add(ei + "," + ej);
        for (int[] c : fuseCells) special.add(c[0] + "," + c[1]);
        special.add(pinceCell[0] + "," + pinceCell[1]);
        special.add(boardCell[0] + "," + boardCell[1]);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (special.contains(i + "," + j)) continue;
                if (i < n - 1 && !special.contains((i + 1) + "," + j) && r.nextDouble() < 0.28) movE[i][j] = true;
                if (j < n - 1 && !special.contains(i + "," + (j + 1)) && r.nextDouble() < 0.28) movS[i][j] = true;
            }
        // Portes : seulement sur les passages fixes
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                doorE[i][j] = east[i][j] && !movE[i][j] && r.nextDouble() < 0.18;
                doorS[i][j] = south[i][j] && !movS[i][j] && r.nextDouble() < 0.18;
            }
        this.litCells = litCells;
        this.seed = seed;
    }

    private static boolean same(int[] a, int[] b) {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1];
    }

    private static boolean contains(List<int[]> l, int[] c) {
        for (int[] x : l) if (same(x, c)) return true;
        return false;
    }

    int openings(int i, int j) {
        int k = 0;
        for (int[] dd : DIRS) if (open(i, j, dd[0], dd[1])) k++;
        return k;
    }

    private void carve(int i, int j, int di, int dj) {
        if (di == 1) east[i][j] = true;
        else if (di == -1) east[i - 1][j] = true;
        else if (dj == 1) south[i][j] = true;
        else south[i][j - 1] = true;
    }

    /** Efface la zone (labyrinthe + salle de sortie possible). */
    void clear() {
        Build b = new Build(w);
        int size = n * P;
        b.fill(OX - 6, Y - 1, OZ - 6, OX + size + 6, Y + 4, OZ + size + 6, Material.AIR);
    }

    void build() {
        Build b = new Build(w);
        Random r = new Random(seed ^ 0x5DEECE66DL);
        lights.clear();
        labels.clear();
        exitDoor.clear();
        pieces.clear();
        freeSpots.clear();
        used.clear();
        int size = n * P;
        clear();
        b.fill(OX, Y - 1, OZ, OX + size, Y - 1, OZ + size, Material.SMOOTH_STONE);
        b.fill(OX, Y + 4, OZ, OX + size, Y + 4, OZ + size, Material.LIGHT_GRAY_CONCRETE);
        // Murs partout sur la grille, sol selon le thème
        for (int x = OX; x <= OX + size; x++)
            for (int z = OZ; z <= OZ + size; z++) {
                boolean wall = (x - OX) % P == 0 || (z - OZ) % P == 0;
                if (wall) wallColumn(b, x, z);
                else {
                    int[] c = cellAt(x, z);
                    b.set(x, Y, z, floor(c == null ? COULOIR : theme[c[0]][c[1]]));
                }
            }
        // Ouvertures
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (east[i][j]) opening(b, OX + (i + 1) * P, OZ + j * P, true, doorE[i][j]);
                if (south[i][j]) opening(b, OX + i * P, OZ + (j + 1) * P, false, doorS[i][j]);
            }
        // Frise violette des couloirs + néons
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int x0 = OX + i * P, z0 = OZ + j * P;
                double lit = theme[i][j] == RESERVE ? litCells * 0.5 : litCells;
                if (r.nextDouble() < lit || (i == 0 && j == 0)) {
                    b.set(x0 + 3, Y + 4, z0 + 3, Material.SEA_LANTERN);
                    lights.add(new int[] {x0 + 3, Y + 4, z0 + 3});
                }
            }
        // Rien devant la porte de sortie
        for (int k = 1; k <= 5; k++) {
            int[] t = tile(exitCell[0], exitCell[1], new int[] {exitSide.getModX(), exitSide.getModZ()}, k);
            used.add(key(t[0], t[1]));
        }
        // Conteneurs « spéciaux » d'abord (fusibles, pince, tableau du code), puis le décor
        for (int k = 0; k < fuseCells.size(); k++) {
            String kind = fuseKinds.get(k);
            String model = kind.equals("shelf") ? "horscours_etagere" : "horscours_armoire";
            Piece p = placeSomewhere(fuseCells.get(k), model, 2, kind, r);
            if (p != null) {
                p.content = "fuse:" + k;
                fuseCells.set(k, new int[] {p.cellI, p.cellJ});
            }
        }
        Piece pince = placeSomewhere(pinceCell, "armoire_basse", 1, "drawer", r);
        if (pince != null) pince.content = "pince";
        Piece board = placeSomewhere(boardCell, "horscours_tableau_blanc", 0, "board", r);
        if (board != null) board.content = "code";
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) decorate(r, i, j);
        // Piles : dans un tiroir ou sur une étagère de leur cellule (posés en plus si besoin)
        int pk = 0;
        for (int[] c : pileCells) {
            Piece host = null;
            for (Piece p : pieces)
                if (p.cellI == c[0] && p.cellJ == c[1] && p.content.isEmpty()
                        && (p.role.equals("drawer") || p.role.equals("shelf"))) host = p;
            if (host == null) host = place(c[0], c[1], r.nextBoolean() ? "armoire_basse" : "horscours_etagere",
                    r.nextBoolean() ? 1 : 2, "", r, false);
            if (host != null) {
                if (host.model.equals("armoire_basse") || host.role.isEmpty())
                    host = retag(host, host.model.equals("armoire_basse") ? "drawer" : "shelf");
                host.content = "pile:" + pk++;
            }
        }
        // Tiroirs vides ou « surprises »
        for (Piece p : pieces)
            if (p.role.equals("drawer") && p.content.isEmpty()) p.content = r.nextDouble() < 0.25 ? "photo" : "rien";
        // Coins libres pour les mannequins
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                if (i == 0 && j == 0) continue;
                int x0 = OX + i * P, z0 = OZ + j * P;
                int[][] corners = {{1, 1}, {5, 1}, {1, 5}, {5, 5}};
                for (int[] k : corners) {
                    int x = x0 + k[0], z = z0 + k[1];
                    if (!used.contains(key(x, z))) freeSpots.add(new int[] {x, z});
                }
            }
        buildExit(b);
        // Barrières sous les meubles (on ne traverse pas un bureau)
        for (Piece p : pieces) {
            if (p.barrier <= 0) continue;
            for (int[] t : p.tiles)
                for (int y = 0; y < p.barrier; y++) b.set(t[0], Y + y, t[1], Material.BARRIER);
        }
    }

    /** Pose dans la cellule voulue, sinon dans n'importe quelle autre (un objet de quête ne doit jamais manquer). */
    private Piece placeSomewhere(int[] cell, String model, int barrier, String role, Random r) {
        Piece p = place(cell[0], cell[1], model, barrier, role, r, true);
        if (p != null) return p;
        List<int[]> all = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (i + j > 2) all.add(new int[] {i, j});
        Collections.shuffle(all, r);
        for (int[] c : all) {
            p = place(c[0], c[1], model, barrier, role, r, false);
            if (p != null) return p;
        }
        return null;
    }

    private Piece retag(Piece p, String role) {
        Piece q = new Piece(p.model, p.x, p.z, p.yOff, p.yaw, p.scale, p.barrier, role);
        q.tiles.addAll(p.tiles);
        q.cellI = p.cellI;
        q.cellJ = p.cellJ;
        pieces.set(pieces.indexOf(p), q);
        return q;
    }

    private Material floor(int th) {
        return switch (th) {
            case BUREAU -> Material.LIGHT_GRAY_CARPET;
            case COURS -> Material.GRAY_CARPET;
            case RESERVE -> Material.AIR;
            case PAUSE -> Material.WHITE_CARPET;
            default -> Material.GRAY_CARPET;
        };
    }

    private void wallColumn(Build b, int x, int z) {
        b.set(x, Y, z, Material.LIGHT_GRAY_CONCRETE);
        b.fill(x, Y + 1, z, x, Y + 3, z, Material.WHITE_CONCRETE);
    }

    private void opening(Build b, int wx, int wz, boolean wallAlongZ, boolean door) {
        // wallAlongZ : mur vertical en x = wx, ouverture sur z = wz+2..wz+4
        if (door) {
            int x = wallAlongZ ? wx : wx + 3, z = wallAlongZ ? wz + 3 : wz;
            b.door(x, Y, z, Material.OAK_DOOR, wallAlongZ ? BlockFace.EAST : BlockFace.SOUTH, true);
            return;
        }
        for (int k = 2; k <= 4; k++) {
            int x = wallAlongZ ? wx : wx + k, z = wallAlongZ ? wz + k : wz;
            b.fill(x, Y, z, x, Y + 2, z, Material.AIR);
            b.set(x, Y, z, Material.GRAY_CARPET);
        }
    }

    /** Ouvre ou ferme une cloison mobile (blocs seulement ; la grille est mise à jour par l'appelant). */
    void setSegment(boolean alongE, int i, int j, boolean opened) {
        Build b = new Build(w);
        int wx = alongE ? OX + (i + 1) * P : OX + i * P;
        int wz = alongE ? OZ + j * P : OZ + (j + 1) * P;
        for (int k = 2; k <= 4; k++) {
            int x = alongE ? wx : wx + k, z = alongE ? wz + k : wz;
            if (opened) {
                b.fill(x, Y, z, x, Y + 2, z, Material.AIR);
                b.set(x, Y, z, Material.GRAY_CARPET);
            } else {
                b.set(x, Y, z, Material.LIGHT_GRAY_CONCRETE);
                b.fill(x, Y + 1, z, x, Y + 3, z, Material.WHITE_CONCRETE);
            }
        }
        if (alongE) east[i][j] = opened;
        else south[i][j] = opened;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** yaw de l'entité pour que l'avant du modèle (vers +z) regarde dans la direction (fx, fz). */
    static float yawFacing(int fx, int fz) {
        return (float) Math.toDegrees(Math.atan2(-fx, fz)) + 180f;
    }

    /** Rotation d'une case du modèle (même calcul qu'EemiMeubles). */
    static int[] rot(int dx, int dz, float yaw) {
        double a = Math.toRadians(yaw + 180);
        double c = Math.cos(a), s = Math.sin(a);
        return new int[] {(int) Math.round(dx * c - dz * s), (int) Math.round(dx * s + dz * c)};
    }

    /** Côtés fermés et fixes d'une cellule (on peut y adosser un meuble). */
    private List<int[]> wallSides(int i, int j) {
        List<int[]> l = new ArrayList<>();
        for (int[] dd : DIRS) if (!open(i, j, dd[0], dd[1]) && !movable(i, j, dd[0], dd[1])) l.add(dd);
        return l;
    }

    /** Case k (1..5) le long du mur du côté s de la cellule. */
    private int[] tile(int i, int j, int[] s, int k) {
        int x0 = OX + i * P, z0 = OZ + j * P;
        if (s[0] != 0) return new int[] {s[0] == 1 ? x0 + 5 : x0 + 1, z0 + k};
        return new int[] {x0 + k, s[1] == 1 ? z0 + 5 : z0 + 1};
    }

    /**
     * Pose un meuble contre un mur fermé et fixe de la cellule, sur des cases libres.
     * @param barrier hauteur de barrière (0 = traversable ou mural)
     * @param mid true : de préférence au milieu du mur (k 2..4)
     */
    private Piece place(int i, int j, String model, int barrier, String role, Random r, boolean mid) {
        boolean wide = WIDE.contains(model);
        List<int[]> sides = wallSides(i, j);
        Collections.shuffle(sides, r);
        int[] ks = mid ? new int[] {3, 2, 4, 1, 5} : new int[] {1, 5, 2, 4, 3};
        for (int[] s : sides)
            for (int k : ks) {
                int[] t = tile(i, j, s, k);
                float yaw = yawFacing(-s[0], -s[1]);
                List<int[]> tiles = new ArrayList<>();
                tiles.add(t);
                if (wide) {
                    int[] o = rot(1, 0, yaw);
                    int[] t2 = {t[0] + o[0], t[1] + o[1]};
                    int[] c2 = cellAt(t2[0], t2[1]);
                    if (c2 == null || c2[0] != i || c2[1] != j || isWall(t2[0], t2[1])) continue;
                    // la 2e case doit rester contre le même mur
                    if (s[0] != 0 ? t2[0] != t[0] : t2[1] != t[1]) continue;
                    tiles.add(t2);
                }
                boolean free = true;
                for (int[] tt : tiles) if (used.contains(key(tt[0], tt[1]))) free = false;
                if (!free) continue;
                return add(i, j, model, t, tiles, MOUNTED.contains(model) ? 1 : 0, yaw, barrier, role);
            }
        return null;
    }

    private boolean isWall(int x, int z) {
        return (x - OX) % P == 0 || (z - OZ) % P == 0;
    }

    private Piece add(int i, int j, String model, int[] t, List<int[]> tiles, int yOff, float yaw, int barrier, String role) {
        Piece p = new Piece(model, t[0], t[1], yOff, yaw, scale(model), barrier, role);
        p.tiles.addAll(tiles);
        p.cellI = i;
        p.cellJ = j;
        if (yOff == 0 || barrier > 0) for (int[] tt : tiles) used.add(key(tt[0], tt[1]));
        pieces.add(p);
        return p;
    }

    /** Pièce posée par-dessus une autre (écran sur un bureau, micro-ondes sur un meuble). */
    private void onTop(Piece base, String model) {
        Piece p = new Piece(model, base.x, base.z, 1, base.yaw, scale(model), 0, "deco");
        p.cellI = base.cellI;
        p.cellJ = base.cellJ;
        pieces.add(p);
    }

    static final Set<String> WIDE = Set.of("bureau", "table_cours", "table_reunion", "table_cuisine", "ecran_pied",
            "canape_orange", "horscours_tableau_blanc");
    static final Set<String> MOUNTED = Set.of("tele_murale", "affiche", "panneau_sortie", "baie_reseau", "horscours_tableau_blanc");

    static float scale(String model) {
        return switch (model) {
            case "kakemono", "portemanteau", "ecran_pied", "tableau_jaune", "frigo", "horscours_armoire",
                 "horscours_armoire_ouverte" -> 2f;
            default -> 1f;
        };
    }

    /** Décor selon le thème, contre les murs fermés et fixes. */
    private void decorate(Random r, int i, int j) {
        if (i == 0 && j == 0) {
            place(i, j, "plante", 1, "deco", r, false);
            place(i, j, "panneau_sortie", 0, "deco", r, true);
            return;
        }
        switch (theme[i][j]) {
            case COULOIR -> {
                double t = r.nextDouble();
                if (t < 0.35) {
                    place(i, j, "pouf_bleu", 1, "deco", r, true);
                    place(i, j, "pouf_orange", 1, "deco", r, true);
                    place(i, j, "pouf_bleu", 1, "deco", r, true);
                } else if (t < 0.6) place(i, j, r.nextBoolean() ? "banquette_bleue" : "banquette_orange", 1, "deco", r, true);
                else if (t < 0.75) {
                    place(i, j, "fauteuil_jaune", 1, "chair", r, true);
                    place(i, j, "table_basse", 1, "deco", r, true);
                }
                if (r.nextDouble() < 0.5) place(i, j, "tele_murale", 0, "tv", r, true);
                if (r.nextDouble() < 0.4) place(i, j, "plante", 1, "deco", r, false);
                if (r.nextDouble() < 0.3) place(i, j, "extincteur", 0, "deco", r, false);
                if (r.nextDouble() < 0.3) place(i, j, r.nextBoolean() ? "affiche" : "kakemono", 0, "deco", r, false);
                if (r.nextDouble() < 0.12) place(i, j, "horscours_armoire", 0, "hide", r, false);
            }
            case BUREAU -> {
                Piece desk = place(i, j, "bureau", 1, "drawer", r, true);
                if (desk != null) {
                    onTop(desk, r.nextDouble() < 0.7 ? "ecran_pc" : "portable");
                    int[] f = rot(0, 1, desk.yaw); // une case devant le bureau... côté pièce
                    float back = desk.yaw;
                    Piece ch = new Piece("chaise_bureau", desk.x + f[0], desk.z + f[1], 0,
                            r.nextDouble() < 0.3 ? r.nextFloat() * 360f : back + 180f, 1f, 0, "chair");
                    ch.cellI = i;
                    ch.cellJ = j;
                    pieces.add(ch);
                }
                if (r.nextDouble() < 0.6) place(i, j, "armoire_basse", 1, "drawer", r, false);
                if (r.nextDouble() < 0.6) place(i, j, "cartons", 1, "deco", r, false);
                if (r.nextDouble() < 0.4) place(i, j, "portemanteau", 0, "deco", r, false);
                if (r.nextDouble() < 0.25) place(i, j, "horscours_armoire", 0, "hide", r, false);
                if (r.nextDouble() < 0.4) place(i, j, "affiche", 0, "deco", r, false);
            }
            case COURS -> {
                if (!(boardCell[0] == i && boardCell[1] == j)) place(i, j, "horscours_tableau_blanc", 0, "deco", r, true);
                for (int k = 0; k < 2; k++) {
                    Piece t = place(i, j, "table_cours", 1, "deco", r, false);
                    if (t != null) {
                        int[] f = rot(0, 1, t.yaw);
                        int[] o = rot(1, 0, t.yaw);
                        for (int s = 0; s < 2; s++) {
                            Piece ch = new Piece("chaise_cours", t.x + f[0] + o[0] * s, t.z + f[1] + o[1] * s, 0,
                                    t.yaw + 180f + (r.nextDouble() < 0.25 ? 70f : 0f), 1f, 0, "chair");
                            ch.cellI = i;
                            ch.cellJ = j;
                            pieces.add(ch);
                        }
                    }
                }
                if (r.nextDouble() < 0.5) place(i, j, "ecran_pied", 0, "tv", r, true);
            }
            case RESERVE -> {
                for (int k = 0; k < 3; k++) place(i, j, "horscours_etagere", 2, "shelf", r, false);
                place(i, j, "cartons", 1, "deco", r, false);
                if (r.nextDouble() < 0.6) place(i, j, "cartons", 1, "deco", r, false);
                if (r.nextDouble() < 0.5) place(i, j, "horscours_armoire", 0, "hide", r, false);
                if (r.nextDouble() < 0.4) place(i, j, "imprimante", 1, "deco", r, false);
                if (r.nextDouble() < 0.4) place(i, j, "baie_reseau", 0, "deco", r, true);
            }
            case PAUSE -> {
                place(i, j, "frigo", 2, "deco", r, false);
                Piece m = place(i, j, "meuble_cuisine", 1, "drawer", r, false);
                if (m != null) onTop(m, r.nextBoolean() ? "micro_ondes" : "machine_cafe");
                if (r.nextDouble() < 0.6) place(i, j, "table_haute", 1, "deco", r, false);
                if (r.nextDouble() < 0.5) place(i, j, "canape_orange", 0, "chair", r, true);
                if (r.nextDouble() < 0.5) place(i, j, "poubelles", 1, "deco", r, false);
            }
            default -> {
            }
        }
    }

    private void buildExit(Build b) {
        int i = exitCell[0], j = exitCell[1];
        int x0 = OX + i * P, z0 = OZ + j * P;
        int dx = exitSide.getModX(), dz = exitSide.getModZ();
        // Case de la porte dans le mur extérieur, puis salle 3x3 derrière
        int doorX = dx == 0 ? x0 + 3 : (dx > 0 ? x0 + P : x0);
        int doorZ = dz == 0 ? z0 + 3 : (dz > 0 ? z0 + P : z0);
        int rx = doorX + dx * 2, rz = doorZ + dz * 2; // centre de la salle
        b.fill(rx - 2, Y - 1, rz - 2, rx + 2, Y - 1, rz + 2, Material.SMOOTH_STONE);
        b.fill(rx - 2, Y, rz - 2, rx + 2, Y + 3, rz + 2, Material.WHITE_CONCRETE);
        b.fill(rx - 1, Y, rz - 1, rx + 1, Y + 2, rz + 1, Material.AIR);
        b.fill(rx - 1, Y, rz - 1, rx + 1, Y, rz + 1, Material.LIME_CARPET);
        b.fill(rx - 2, Y + 4, rz - 2, rx + 2, Y + 4, rz + 2, Material.LIGHT_GRAY_CONCRETE);
        b.set(rx, Y + 4, rz, Material.SEA_LANTERN);
        b.door(doorX, Y, doorZ, Material.IRON_DOOR, exitSide, false);
        exitDoor.add(new int[] {doorX, Y, doorZ});
        exitDoor.add(new int[] {doorX, Y + 1, doorZ});
        exitRoom = new BoundingBox(rx - 1, Y, rz - 1, rx + 2, Y + 3, rz + 2);
        float yaw = switch (exitSide) {
            case EAST -> -90f;
            case WEST -> 90f;
            case SOUTH -> 180f;
            default -> 0f;
        };
        double lx = doorX + 0.5 - dx * 0.52, lz = doorZ + 0.5 - dz * 0.52;
        labels.add(new Object[] {lx, Y + 2.4, lz, yaw + 180f, "§a§lSORTIE"});
    }

    void openExit() {
        for (int[] p : exitDoor) {
            Block bl = w.getBlockAt(p[0], p[1], p[2]);
            if (bl.getBlockData() instanceof Door d) {
                d.setOpen(true);
                bl.setBlockData(d, false);
            }
        }
    }

    /** Néons qui clignotent : bascule un néon au hasard. */
    void flicker(Random r) {
        if (lights.isEmpty()) return;
        int[] l = lights.get(r.nextInt(lights.size()));
        Block bl = w.getBlockAt(l[0], l[1], l[2]);
        bl.setType(bl.getType() == Material.SEA_LANTERN ? Material.WHITE_CONCRETE : Material.SEA_LANTERN, false);
    }
}
