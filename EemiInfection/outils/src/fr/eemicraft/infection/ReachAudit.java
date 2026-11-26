package fr.eemicraft.infection;

import fr.eemicraft.infection.build.B;
import fr.eemicraft.infection.build.RecordingSink;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Audit hors serveur des cases de dispersion (carte #136) : construit la ville en memoire, refait le filtre
 * d'avant (parcours depuis la rue seulement, comme scanSpots 1.8.0) et celui de la 1.9.0 (aller ET retour,
 * clotures, marge), et compte les cases d'ou l'on ne peut pas rejoindre la rue a pied.
 * Verite = parcours inverse de {@link Reach} avec un classement des blocs par collision reelle ; controle
 * independant : un parcours direct depuis chaque case gardee doit atteindre la rue.
 * Usage : ReachAudit
 */
public final class ReachAudit {
    static final int G = City.G, LIM = City.WALL - 2;

    public static void main(String[] a) throws Exception {
        RecordingSink s = new RecordingSink();
        new City(s, Logger.getLogger("audit")).build();
        int[][] seeds = seeds();

        // modele d'avant (1.8.0) : Bukkit isSolid approche, pas de clotures hautes, trappes toutes ouvrables
        Reach old = new Reach((x, y, z) -> oldKind(s.get(x, y, z)), LIM, G, G + 26);
        old.run(seeds);
        // modele 1.9.0 (= verite pour l'audit)
        Reach nw = new Reach((x, y, z) -> newKind(s.get(x, y, z)), LIM, G, G + 26);
        int fw = nw.run(seeds), bw = nw.runBack(seeds);
        int ex0 = (int) Math.floor(City.EXIT[0]), ey0 = (int) Math.floor(City.EXIT[1]), ez0 = (int) Math.floor(City.EXIT[2]);
        System.out.println("Sortie de la crypte (EXIT, repli des zombies) reliee a la rue : " + nw.connected(ex0, ey0, ez0));
        System.out.println("Parcours 1.9.0 : " + fw + " positions atteintes depuis la rue, " + bw + " positions d'ou l'on rejoint la rue");

        List<int[]> before = new ArrayList<>(), after = new ArrayList<>();
        Map<String, Integer> why = new TreeMap<>();
        List<String> ex = new ArrayList<>();
        int trapped = 0;
        for (int x = -LIM; x <= LIM; x++)
            for (int z = -LIM; z <= LIM; z++)
                for (int y = G + 1; y <= G + 22; y++) {
                    if (!candidate(s, x, y, z)) continue;
                    if (old.reached(x, y, z)) {
                        before.add(new int[]{x, y, z});
                        if (!nw.exits(x, y, z)) {
                            trapped++;
                            String k = cause(s, old, nw, x, y, z);
                            why.merge(k, 1, Integer::sum);
                            if (ex.size() < 25 && trapped % 7 == 1) ex.add(x + " " + y + " " + z + " : " + k + " (sol " + B.name(s.get(x, y - 1, z)) + ")");
                        }
                    }
                    if (nw.roomy(x, y, z)) after.add(new int[]{x, y, z});
                }
        System.out.println();
        System.out.println("AVANT (1.8.0) : " + before.size() + " cases gardees, dont " + trapped + " sans sortie a pied ("
                + pct(trapped, before.size()) + ")");
        for (var e : why.entrySet()) System.out.println("  " + e.getValue() + " : " + e.getKey());
        for (String e : ex) System.out.println("  ex. " + e);
        int notExit = 0, low = 0;
        for (int[] p : after) {
            if (!nw.exits(p[0], p[1], p[2])) notExit++;
            if (p[1] <= G + 11) low++;
        }
        System.out.println();
        System.out.println("APRES (1.9.0) : " + after.size() + " cases gardees (pieds <= G+11 : " + low + "), sans sortie : " + notExit);

        // controle independant : parcours direct depuis chaque case gardee jusqu'a la rue
        HashSet<Long> street = new HashSet<>();
        for (int[] q : seeds) if (nw.body(q[0], q[1], q[2])) street.add(City.key(q[0], q[1], q[2]));
        int fail = 0;
        for (int[] p : after) if (!walkOut(nw, p, street)) fail++;
        System.out.println("Controle direct (chaque case gardee -> rue) : " + (after.size() - fail) + "/" + after.size() + " OK");
        int failOld = 0;
        Random r = new Random(1);
        List<int[]> sample = new ArrayList<>(before);
        java.util.Collections.shuffle(sample, r);
        sample = sample.subList(0, Math.min(400, sample.size()));
        for (int[] p : sample) if (!walkOut(nw, p, street)) failOld++;
        System.out.println("Controle direct sur 400 cases d'avant tirees au hasard : " + failOld + " sans sortie (" + pct(failOld, sample.size()) + ")");

        // assez de places pour 20 joueurs ecartes de 10 blocs (meme tirage que disperse(), pieds <= G+11)
        List<int[]> lowSpots = new ArrayList<>();
        for (int[] p : after) if (p[1] <= G + 11) lowSpots.add(p);
        int worst = 99;
        for (int t = 0; t < 200; t++) worst = Math.min(worst, place(lowSpots, 20, 10, r));
        System.out.println("Dispersion de 20 joueurs a >= 10 blocs : au pire " + worst + "/20 places trouvees sur 200 essais");
    }

    static String pct(int a, int b) {
        return b == 0 ? "-" : String.format("%.1f %%", 100.0 * a / b);
    }

    static int[][] seeds() {
        List<int[]> l = new ArrayList<>();
        for (int a = -LIM; a <= LIM; a++)
            for (int b = -3; b <= 3; b++) {
                l.add(new int[]{a, G + 1, b});
                l.add(new int[]{b, G + 1, a});
            }
        l.add(new int[]{(int) Math.floor(City.EXIT[0]), (int) Math.floor(City.EXIT[1]), (int) Math.floor(City.EXIT[2])});
        return l.toArray(new int[0][]);
    }

    static int place(List<int[]> spots, int n, double d, Random r) {
        List<int[]> placed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int t = 0; t < 300; t++) {
                int[] s = spots.get(r.nextInt(spots.size()));
                boolean ok = true;
                for (int[] o : placed) {
                    double dx = o[0] - s[0], dy = o[1] - s[1], dz = o[2] - s[2];
                    if (dx * dx + dy * dy + dz * dz < d * d) { ok = false; break; }
                }
                if (ok) { placed.add(s); break; }
            }
        }
        return placed.size();
    }

    /** Parcours direct (memes mouvements que Reach.run) depuis p jusqu'a une case de la rue. */
    static boolean walkOut(Reach r, int[] p, HashSet<Long> street) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        HashSet<Long> seen = new HashSet<>();
        q.add(p);
        seen.add(City.key(p[0], p[1], p[2]));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int x = c[0], y = c[1], z = c[2];
            if (street.contains(City.key(x, y, z))) return true;
            List<int[]> nx = new ArrayList<>();
            if (!r.supported(x, y, z)) nx.add(new int[]{x, y - 1, z});
            else {
                for (int[] d : dirs) {
                    nx.add(new int[]{x + d[0], y, z + d[1]});
                    if (r.pass(x, y + 2, z)) nx.add(new int[]{x + d[0], y + 1, z + d[1]});
                }
                if (r.climb(x, y, z) || r.climb(x, y + 1, z)) nx.add(new int[]{x, y + 1, z});
                if (r.climb(x, y - 1, z)) nx.add(new int[]{x, y - 1, z});
            }
            for (int[] n : nx) {
                if (!r.body(n[0], n[1], n[2])) continue;
                if (seen.add(City.key(n[0], n[1], n[2]))) q.add(n);
            }
        }
        return false;
    }

    /** Explication grossiere d'une case piegee. */
    static String cause(RecordingSink s, Reach old, Reach nw, int x, int y, int z) {
        if (!nw.body(x, y, z)) return "sur une cloture / un muret";
        if (!nw.reached(x, y, z)) return "atteinte seulement par une cloture ou une trappe en fer";
        return "atteinte en tombant (cour, trou, toit) sans chemin de retour";
    }

    // ------------------------------------------------------------------ classement des blocs (hors serveur)

    static boolean noCollision(String n) {
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("light") || n.equals("water") || n.equals("lava")
                || n.equals("fire") || n.equals("soul_fire") || n.equals("cobweb") || n.equals("redstone_wire") || n.equals("lever")
                || n.equals("tripwire") || n.equals("tripwire_hook") || n.equals("structure_void") || n.equals("sugar_cane")
                || n.equals("sweet_berry_bush") || n.equals("spore_blossom") || n.equals("hanging_roots") || n.equals("glow_lichen")
                || n.equals("sculk_vein") || n.equals("nether_sprouts") || n.equals("small_dripleaf") || n.equals("big_dripleaf_stem")
                || n.equals("leaf_litter") || n.equals("wildflowers") || n.equals("cactus_flower") || n.equals("firefly_bush")
                || n.equals("bush") || n.equals("dead_bush") || n.equals("short_grass") || n.equals("tall_grass") || n.equals("fern")
                || n.equals("large_fern") || n.equals("short_dry_grass") || n.equals("tall_dry_grass")) return true;
        if (n.contains("torch") || n.endsWith("_sign") || n.endsWith("banner") || n.endsWith("_button") || n.endsWith("pressure_plate")
                || n.endsWith("rail") || n.endsWith("sapling") || n.endsWith("_petals") || n.startsWith("kelp") || n.contains("seagrass")
                || n.endsWith("tulip") || n.endsWith("_roots") && !n.contains("mangrove")) return true;
        for (String f : new String[]{"poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
                "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "torchflower", "pitcher_plant",
                "wheat", "carrots", "potatoes", "beetroots", "red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus"})
            if (n.equals(f)) return true;
        return false;
    }

    static boolean climbable(String n) {
        return n.equals("ladder") || n.equals("vine") || n.equals("scaffolding") || n.contains("vines");
    }

    /** Avant : PASS = air ou non solide au sens Bukkit (panneaux et bannieres sont "solides"), tapis, neige, portes en bois, toutes trappes. */
    static int oldKind(String st) {
        String n = B.name(st);
        if (climbable(n)) return Reach.PASS | Reach.CLIMB;
        boolean pass = (noCollision(n) && !n.endsWith("_sign") && !n.endsWith("banner")) || n.endsWith("_carpet") || n.equals("snow")
                || (n.endsWith("_door") && !n.equals("iron_door")) || n.endsWith("_trapdoor");
        return pass ? Reach.PASS : 0;
    }

    /** 1.9.0 : portillons en bois ouvrables, trappes en fer non, clotures et murets hauts. */
    static int newKind(String st) {
        String n = B.name(st);
        if (climbable(n)) return Reach.PASS | Reach.CLIMB;
        boolean wood = !n.startsWith("iron_");
        if ((noCollision(n) && !n.endsWith("_sign") && !n.endsWith("banner")) || n.endsWith("_carpet") || n.equals("snow")
                || (n.endsWith("_door") && wood) || (n.endsWith("_trapdoor") && wood) || (n.endsWith("_fence_gate") && wood)) return Reach.PASS;
        if (n.endsWith("_fence") || n.endsWith("_wall")) return Reach.TALL;
        return 0;
    }

    /** Case candidate (meme test que scanSpots avant le filtre d'accessibilite). */
    static boolean candidate(RecordingSink s, int x, int y, int z) {
        String f = B.name(s.get(x, y, z));
        if (!(B.isAir(s.get(x, y, z)) || f.endsWith("_carpet") || f.equals("short_grass") || f.equals("dead_bush"))) return false;
        if (!B.isAir(s.get(x, y + 1, z))) return false;
        String b = B.name(s.get(x, y - 1, z));
        if (B.isAir(s.get(x, y - 1, z)) || noCollision(b) || climbable(b)) return false;
        for (String k : new String[]{"barrier", "leaves", "fence", "_wall", "iron_bars", "pane", "campfire", "carpet", "trapdoor", "door",
                "lantern", "chain", "chest", "pot", "candle", "skull", "head", "rod", "bell", "snow", "anvil", "brewing", "dripstone",
                "cluster", "bud", "pickle", "egg", "cake", "lectern", "grindstone", "bamboo", "cactus", "conduit", "sniffer", "frogspawn"})
            if (b.contains(k)) return false;
        return true;
    }
}
