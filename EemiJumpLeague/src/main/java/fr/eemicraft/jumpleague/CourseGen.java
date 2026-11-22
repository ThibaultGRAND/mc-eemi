package fr.eemicraft.jumpleague;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Générateur du parcours de JumpLeague (Java pur, sans Bukkit : testable hors serveur).
 *
 * Un parcours = départ, puis 10 modules (3 faciles, 3 normaux, 3 difficiles, 1 ultra), chacun
 * terminé par un checkpoint (le dernier = arrivée). Il est tiré d'une graine : chaque partie a
 * le sien, identique pour les 12 couloirs. Coordonnées relatives au centre du couloir en x
 * (le couloir va de x -4 à 4, coffres jusqu'à ±7), absolues en y et z (on avance vers +z).
 *
 * Sauts (écart = cases d'air entre deux appuis, mesuré dans le sens du saut ; « montée » =
 * différence de hauteur des dessus) : jamais plus de 3 cases, jamais plus d'un bloc de montée.
 * Un saut de 3 cases qui monte demande un élan (piste de 3 blocs ou plateforme avant).
 * Clôtures (poteau fin) : 2 cases au plus, 1 si on monte. Les échelles font monter de 3 à 6.
 * Rien n'est posé dans la trajectoire d'un saut ni au-dessus d'un appui (3 blocs de haut).
 */
public final class CourseGen {

    public static final int MODULES = 10;
    /** À changer si l'algorithme change : la graine sauvée dans le monde ne redonne plus le même parcours. */
    public static final int VERSION = 1;
    static final int START_Y = 100, Y_MIN = 92, Y_MAX = 134;
    static final int X_MIN = -4, X_MAX = 4, CHEST_X = 7;

    public enum Tier {
        EASY("Facile", 6, "lime_concrete", "smooth_stone_slab", "oak_fence", "packed_ice"),
        NORMAL("Normal", 7, "light_blue_concrete", "prismarine_slab", "birch_fence", "packed_ice"),
        HARD("Difficile", 8, "orange_concrete", "cut_red_sandstone_slab", "nether_brick_fence", "blue_ice"),
        ULTRA("Ultra", 10, "crying_obsidian", "blackstone_slab", "warped_fence", "blue_ice");

        public final String label;
        final int jumps;
        final String block, slab, fence, ice;

        Tier(String label, int jumps, String block, String slab, String fence, String ice) {
            this.label = label;
            this.jumps = jumps;
            this.block = block;
            this.slab = slab;
            this.fence = fence;
            this.ice = ice;
        }
    }

    public static Tier tierOf(int module) {
        return module < 3 ? Tier.EASY : module < 6 ? Tier.NORMAL : module < 9 ? Tier.HARD : Tier.ULTRA;
    }

    enum Kind {
        BLOCK(1, 1, 1.0), PLATFORM(2, 2, 1.0), SLAB(1, 1, 0.5), FENCE(1, 1, 1.5), ICE(1, 1, 1.0),
        RUNWAY(1, 3, 1.0), LADDER(1, 1, 1.0), CHECKPOINT(3, 3, 1.0), FINISH(5, 5, 1.0), START(5, 5, 1.0);

        final int w, l;
        final double h;

        Kind(int w, int l, double h) {
            this.w = w;
            this.l = l;
            this.h = h;
        }

        boolean big() {
            return this == CHECKPOINT || this == FINISH || this == START;
        }
    }

    /** Un bloc à poser : state = état de bloc (« minecraft:... »), ou null = béton de la couleur du couloir. */
    public record Place(int x, int y, int z, String state) {}

    /** Plateforme de checkpoint : on l'atteint en se tenant dessus. */
    public record Box(int x0, int x1, int z0, int z1, int base) {
        public boolean contains(double rx, double y, double z) {
            return rx >= x0 && rx < x1 + 1 && z >= z0 && z < z1 + 1 && y >= base + 0.9 && y < base + 3;
        }

        public double cx() {
            return (x0 + x1 + 1) / 2.0;
        }

        public double cz() {
            return (z0 + z1 + 1) / 2.0;
        }
    }

    public static final class Course {
        public final long seed;
        public final List<Place> places = new ArrayList<>();
        /** 0 = départ, 1..9 = fin des modules 1..9, 10 = arrivée. */
        public final List<Box> checkpoints = new ArrayList<>();
        /** Seuil de chute du module m (de checkpoint m à m+1), puis de l'arrivée. */
        public final int[] fallY = new int[MODULES];
        public int finishFallY;
        /** Coffres de l'Ender : position relative → module (0..9). */
        public final Map<Long, Integer> chests = new HashMap<>();
        public int minX, maxX, minY, maxY, minZ, maxZ;
        public int jumps, ladders, sideJumps;

        Course(long seed) {
            this.seed = seed;
        }
    }

    static final class Node {
        final Kind kind;
        final int x0, x1, z0, z1, base;
        int ladderH;

        Node(Kind kind, int x0, int x1, int z0, int z1, int base) {
            this.kind = kind;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.base = base;
        }

        double top() {
            return base + kind.h;
        }

        /** Assez long dans le sens de la course pour prendre de l'élan. */
        boolean runUp() {
            return z1 - z0 >= 2;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    public static Course generate(long seed) {
        return new CourseGen(seed).run();
    }

    // ------------------------------------------------------------------ génération

    private final Random r;
    private final Course c;
    private final Set<Long> solid = new HashSet<>(), reserved = new HashSet<>();
    private Node cur;

    private CourseGen(long seed) {
        r = new Random(seed);
        c = new Course(seed);
        c.minX = c.minY = c.minZ = Integer.MAX_VALUE;
        c.maxX = c.maxY = c.maxZ = Integer.MIN_VALUE;
    }

    private Course run() {
        Node start = new Node(Kind.START, -2, 2, -2, 2, START_Y);
        put(start, Tier.EASY);
        headroom(start);
        c.checkpoints.add(box(start));
        cur = start;
        for (int m = 0; m < MODULES; m++) {
            Tier t = tierOf(m);
            int minBase = cur.base;
            int chestAt = t.jumps / 2;
            boolean chestPending = false;
            for (int j = 0; j < t.jumps; j++) {
                Node n = next(t, pickKind(t));
                put(n, t);
                reserveJump(cur, n);
                minBase = Math.min(minBase, n.base);
                cur = n;
                c.jumps++;
                if (j == chestAt || chestPending) chestPending = !chest(m, t);
            }
            Node cp = next(t, m == MODULES - 1 ? Kind.FINISH : Kind.CHECKPOINT);
            put(cp, t);
            reserveJump(cur, cp);
            cur = cp;
            if (chestPending) chestOnPlatform(m);
            c.checkpoints.add(box(cp));
            minBase = Math.min(minBase, cp.base);
            c.fallY[m] = minBase - 3;
        }
        c.finishFallY = cur.base - 3;
        return c;
    }

    private static Box box(Node n) {
        return new Box(n.x0, n.x1, n.z0, n.z1, n.base);
    }

    private Kind pickKind(Tier t) {
        // BLOCK, PLATFORM, SLAB, FENCE, ICE, RUNWAY, LADDER
        int[] w = switch (t) {
            case EASY -> new int[]{40, 60, 0, 0, 0, 0, 0};
            case NORMAL -> new int[]{40, 10, 15, 10, 10, 5, 10};
            case HARD -> new int[]{35, 0, 10, 15, 15, 15, 10};
            case ULTRA -> new int[]{30, 0, 5, 20, 15, 20, 10};
        };
        Kind[] kinds = {Kind.BLOCK, Kind.PLATFORM, Kind.SLAB, Kind.FENCE, Kind.ICE, Kind.RUNWAY, Kind.LADDER};
        int sum = 0;
        for (int v : w) sum += v;
        int x = r.nextInt(sum);
        for (int i = 0; i < w.length; i++) {
            x -= w[i];
            if (x < 0) return kinds[i];
        }
        return Kind.BLOCK;
    }

    /** Écart du bas vers la base suivante : -2, -1, 0, +1 (tiré, ramené vers le milieu de la plage de hauteur). */
    private int pickDBase(Tier t) {
        int[] w = switch (t) {
            case EASY -> new int[]{0, 2, 4, 3};
            case NORMAL -> new int[]{1, 2, 3, 3};
            case HARD, ULTRA -> new int[]{1, 3, 2, 3};
        };
        if (cur.base > Y_MAX - 6) w = new int[]{w[0] + 1, w[1] + 1, w[2], 0};
        if (cur.base < Y_MIN + 4) w = new int[]{0, 0, w[2], w[3] + 1};
        int sum = w[0] + w[1] + w[2] + w[3];
        int x = r.nextInt(sum);
        for (int i = 0; i < 4; i++) {
            x -= w[i];
            if (x < 0) return i - 2;
        }
        return 0;
    }

    /** Écart (cases d'air) permis selon la difficulté et la montée (différence des dessus). */
    static int[] gapRange(Tier t, double rise) {
        int i = t.ordinal();
        if (rise > 0.5) return new int[][]{{1, 1}, {1, 2}, {2, 2}, {2, 3}}[i];
        if (rise > 0.001) return new int[][]{{1, 2}, {2, 2}, {2, 3}, {2, 3}}[i];
        if (rise > -0.001) return new int[][]{{1, 2}, {2, 2}, {2, 3}, {3, 3}}[i];
        if (rise >= -1.001) return new int[][]{{1, 2}, {2, 3}, {3, 3}, {3, 3}}[i];
        return new int[][]{{2, 2}, {2, 3}, {3, 3}, {3, 3}}[i];
    }

    private Node next(Tier t, Kind kind) {
        for (int tries = 0; tries < 80; tries++) {
            Kind k = kind;
            if (tries >= 40 && !k.big()) k = Kind.BLOCK;
            Node n = k == Kind.LADDER ? ladder(t) : jump(t, k, tries >= 60);
            if (n != null) return n;
        }
        // secours : droit devant, sans dénivelé ou en descendant, le plus petit écart qui passe
        Kind k = kind.big() ? kind : Kind.BLOCK;
        for (int db = 0; db >= -3; db--)
            for (int gap = 1; gap <= 3; gap++) {
                int x0 = Math.max(X_MIN, Math.min(X_MAX - k.w + 1, (cur.x0 + cur.x1) / 2 - k.w / 2));
                int z0 = cur.z1 + 1 + gap;
                Node n = new Node(k, x0, x0 + k.w - 1, z0, z0 + k.l - 1, cur.base + db);
                if (fits(n) && pathFree(cur, n)) return n;
            }
        int x0 = Math.max(X_MIN, Math.min(X_MAX - k.w + 1, cur.x0));
        return new Node(k, x0, x0 + k.w - 1, cur.z1 + 2, cur.z1 + 1 + k.l, cur.base);
    }

    private Node jump(Tier t, Kind k, boolean simple) {
        int nb = cur.base + (simple ? 0 : pickDBase(t));
        double rise = nb + k.h - cur.top();
        if (rise > 1.001) {
            nb--;
            rise -= 1;
        }
        if (nb < Y_MIN || nb > Y_MAX) return null;
        int[] gr = gapRange(t, rise);
        int gap = gr[0] + r.nextInt(gr[1] - gr[0] + 1);
        boolean side = !simple && !k.big() && k != Kind.RUNWAY && t != Tier.EASY && r.nextInt(5) == 0;
        if (side) gap = Math.min(gap, 2);
        else if (!cur.runUp() && rise > 0.001 && gap >= 3) gap = 2;
        if (k == Kind.FENCE || cur.kind == Kind.FENCE) gap = Math.min(gap, rise > 0.5 ? 1 : 2);
        if (k.big()) gap = Math.min(gap, t == Tier.ULTRA ? 3 : 2);
        gap = Math.max(1, gap);
        int x0, z0;
        if (!side) {
            z0 = cur.z1 + 1 + gap;
            int lo = Math.max(cur.x0 - k.w, X_MIN), hi = Math.min(cur.x1 + 1, X_MAX - k.w + 1);
            if (lo > hi) return null;
            x0 = lo + r.nextInt(hi - lo + 1);
        } else {
            boolean right = r.nextBoolean();
            x0 = right ? cur.x1 + 1 + gap : cur.x0 - gap - k.w;
            z0 = cur.z1 + r.nextInt(2);
            if (x0 < X_MIN || x0 + k.w - 1 > X_MAX) return null;
        }
        Node n = new Node(k, x0, x0 + k.w - 1, z0, z0 + k.l - 1, nb);
        if (!fits(n) || !pathFree(cur, n)) return null;
        if (side) c.sideJumps++;
        return n;
    }

    /** Échelle droit devant : un mur de h blocs, l'échelle contre sa face, on finit sur le mur. */
    private Node ladder(Tier t) {
        if (t == Tier.EASY) return null;
        int h = switch (t) {
            case NORMAL -> 3 + r.nextInt(2);
            case HARD -> 4 + r.nextInt(2);
            default -> 5 + r.nextInt(2);
        };
        int nb = cur.base + h;
        if (nb > Y_MAX) return null;
        int x = Math.max(X_MIN, Math.min(X_MAX, cur.x0 + r.nextInt(cur.x1 - cur.x0 + 1)));
        int lz = cur.z1 + 1, wz = cur.z1 + 2;
        for (int y = cur.base + 1; y <= nb; y++)
            if (used(x, y, lz) || used(x, y, wz)) return null;
        for (int y = nb + 1; y <= nb + 3; y++)
            if (solid.contains(key(x, y, wz)) || solid.contains(key(x, y, lz))) return null;
        Node n = new Node(Kind.LADDER, x, x, wz, wz, nb);
        n.ladderH = h;
        c.ladders++;
        return n;
    }

    private boolean used(int x, int y, int z) {
        long k = key(x, y, z);
        return solid.contains(k) || reserved.contains(k);
    }

    /** Appui libre (ni bloc ni trajectoire réservée) et 3 blocs d'air au-dessus. */
    private boolean fits(Node n) {
        for (int x = n.x0; x <= n.x1; x++)
            for (int z = n.z0; z <= n.z1; z++) {
                if (used(x, n.base, z)) return false;
                for (int y = n.base + 1; y <= n.base + 3; y++)
                    if (solid.contains(key(x, y, z))) return false;
            }
        return true;
    }

    /** Aucun bloc dans la boîte du saut, hors appuis eux-mêmes et ce qui les porte. */
    private boolean pathFree(Node a, Node b) {
        int y0 = Math.min(a.base, b.base) + 1, y1 = (int) Math.ceil(Math.max(a.top(), b.top())) + 2;
        for (int x = Math.min(a.x0, b.x0); x <= Math.max(a.x1, b.x1); x++)
            for (int z = Math.min(a.z0, b.z0); z <= Math.max(a.z1, b.z1); z++)
                for (int y = y0; y <= y1; y++) {
                    if (!solid.contains(key(x, y, z))) continue;
                    if (under(a, x, y, z) || under(b, x, y, z)) continue;
                    return false;
                }
        return true;
    }

    private static boolean under(Node n, int x, int y, int z) {
        return x >= n.x0 && x <= n.x1 && z >= n.z0 && z <= n.z1 && y <= n.base;
    }

    private void reserveJump(Node a, Node b) {
        int y0 = Math.min(a.base, b.base) + 1, y1 = (int) Math.ceil(Math.max(a.top(), b.top())) + 2;
        for (int x = Math.min(a.x0, b.x0); x <= Math.max(a.x1, b.x1); x++)
            for (int z = Math.min(a.z0, b.z0); z <= Math.max(a.z1, b.z1); z++)
                for (int y = y0; y <= y1; y++) {
                    long k = key(x, y, z);
                    if (!solid.contains(k)) reserved.add(k);
                }
        headroom(b);
    }

    private void headroom(Node n) {
        for (int x = n.x0; x <= n.x1; x++)
            for (int z = n.z0; z <= n.z1; z++)
                for (int y = n.base + 1; y <= n.base + 3; y++) {
                    long k = key(x, y, z);
                    if (!solid.contains(k)) reserved.add(k);
                }
    }

    // ------------------------------------------------------------------ pose

    private void set(int x, int y, int z, String state) {
        c.places.add(new Place(x, y, z, state));
        solid.add(key(x, y, z));
        c.minX = Math.min(c.minX, x);
        c.maxX = Math.max(c.maxX, x);
        c.minY = Math.min(c.minY, y);
        c.maxY = Math.max(c.maxY, y);
        c.minZ = Math.min(c.minZ, z);
        c.maxZ = Math.max(c.maxZ, z);
    }

    private void put(Node n, Tier t) {
        switch (n.kind) {
            case LADDER -> {
                for (int y = n.base - n.ladderH + 1; y <= n.base; y++) {
                    set(n.x0, y, n.z0, "minecraft:" + t.block);
                    // facing=north : l'échelle regarde vers -z, accrochée au mur en +z
                    set(n.x0, y, n.z0 - 1, "minecraft:ladder[facing=north]");
                }
            }
            case CHECKPOINT, FINISH, START -> {
                int cx = (n.x0 + n.x1) / 2, cz = (n.z0 + n.z1) / 2;
                for (int x = n.x0; x <= n.x1; x++)
                    for (int z = n.z0; z <= n.z1; z++) {
                        boolean edge = x == n.x0 || x == n.x1 || z == n.z0 || z == n.z1;
                        String st = null;
                        if (n.kind == Kind.FINISH && !edge) st = "minecraft:gold_block";
                        if (x == cx && z == cz)
                            st = n.kind == Kind.START ? "minecraft:sea_lantern" : n.kind == Kind.FINISH ? "minecraft:emerald_block" : "minecraft:gold_block";
                        set(x, n.base, z, st);
                    }
                if (n.kind != Kind.START) set(cx, n.base + 1, cz, "minecraft:light_weighted_pressure_plate");
            }
            default -> {
                String st = switch (n.kind) {
                    case SLAB -> t.slab;
                    case FENCE -> t.fence;
                    case ICE -> t.ice;
                    default -> t.block;
                };
                for (int x = n.x0; x <= n.x1; x++)
                    for (int z = n.z0; z <= n.z1; z++) set(x, n.base, z, "minecraft:" + st);
            }
        }
    }

    /** Coffre sur un pilier à côté de l'appui courant (côté centre d'abord), à 1 ou 2 cases. */
    private boolean chest(int m, Tier t) {
        Node n = cur;
        if (n.kind == Kind.FENCE || n.kind == Kind.SLAB) return false;
        int first = n.x0 + n.x1 > 0 ? -1 : 1;
        for (int gap = 1; gap <= 2; gap++)
            for (int side : new int[]{first, -first}) {
                int px = side > 0 ? n.x1 + 1 + gap : n.x0 - 1 - gap;
                if (Math.abs(px) > CHEST_X) continue;
                int pz = (n.z0 + n.z1) / 2, y = n.base;
                boolean ok = true;
                for (int dy = 0; dy <= 3 && ok; dy++) if (used(px, y + dy, pz)) ok = false;
                if (!ok) continue;
                set(px, y, pz, "minecraft:" + t.block);
                set(px, y + 1, pz, "minecraft:ender_chest[facing=" + (side > 0 ? "west" : "east") + "]");
                reserved.add(key(px, y + 2, pz));
                reserved.add(key(px, y + 3, pz));
                c.chests.put(key(px, y + 1, pz), m);
                return true;
            }
        return false;
    }

    /** Dernier recours : le coffre sur un coin du checkpoint. */
    private void chestOnPlatform(int m) {
        int x = cur.x0, z = cur.z1;
        set(x, cur.base + 1, z, "minecraft:ender_chest[facing=south]");
        c.chests.put(key(x, cur.base + 1, z), m);
    }
}
