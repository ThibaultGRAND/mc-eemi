package fr.eemicraft.hideseek;

import fr.eemicraft.hideseek.build.Audit;
import fr.eemicraft.hideseek.build.B;
import fr.eemicraft.hideseek.build.RecordingSink;
import fr.eemicraft.hideseek.build.SchemWriter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Banc de test hors serveur des maps du cache-cache (hors jar) :
 * construction en memoire, audit des etats (blocks.json 26.3), materiaux de deguisement valides (enum Material),
 * accessibilite a pied depuis les points d'apparition, part des blocs de deguisement dans le decor visible,
 * export .schem sans plafond pour analyze.py.
 *
 * <p>Arguments : blocks.json, dossier de sortie, [id de map].
 */
public final class TestMaps {
    public static void main(String[] args) throws Exception {
        Path json = Path.of(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        String only = args.length > 2 ? args[2] : "";
        int bad = 0;
        for (HsMap m : new HsMap[]{new SuperMap(), new PortMap()}) {
            if (!only.isEmpty() && !only.equals(m.id)) continue;
            long t0 = System.currentTimeMillis();
            RecordingSink s = new RecordingSink();
            m.build(s);
            System.out.println("=== " + m.id + " : " + s.operations() + " poses, " + s.count() + " blocs, "
                    + (System.currentTimeMillis() - t0) + " ms, " + s.paletteSize() + " etats");
            int[] b = m.box();
            int[] bb = s.bounds();
            if (bb[0] < b[0] || bb[1] < b[1] || bb[2] < b[2] || bb[3] > b[3] || bb[4] > b[4] || bb[5] > b[5]) {
                System.out.println("!! blocs hors de la boite : " + java.util.Arrays.toString(bb));
                bad++;
            }
            // 1. materiaux de deguisement
            // (noms de l'enum Material = identifiants de bloc en majuscules : verifies contre blocks.json 26.3)
            String reg = java.nio.file.Files.readString(json);
            for (String k : m.disguises().keySet()) {
                if (!reg.contains("\"minecraft:" + k.toLowerCase() + "\"")) { System.out.println("!! bloc de deguisement inconnu : " + k); bad++; }
            }
            // 2. audit des etats et de la pose
            Audit a = new Audit(s).roofStairs("deepslate_tile_stairs", "deepslate_brick_stairs", "oxidized_cut_copper_stairs").run(json);
            System.out.print(a.report(m.id));
            bad += count(a.report(m.id), "ETAT");
            // 3. mesures propres au cache-cache
            measure(m, s);
            // 3b. export complet de la boite du port (falaises, mer) pour les vues isometriques
            if (m instanceof PortMap) {
                RecordingSink ex = new RecordingSink();
                int[] bx = m.box();
                for (int x = bx[0]; x <= PortMap.X0 + PortMap.LX2; x++)
                    for (int z = bx[2]; z <= bx[5]; z++)
                        for (int y = bx[1]; y <= 96; y++) {
                            String st = s.get(x, y, z);
                            if (!B.isAir(st) && !B.name(st).equals("barrier")) ex.set(x, y, z, st);
                        }
                File sf = new File(out, "hs_port_complet.schem");
                SchemWriter.write(ex, sf);
                System.out.println("schem : " + sf);
            }
            // 4. export sans plafond : version de mesure (toits du parking gardes) et version de rendu (tout ouvert)
            for (boolean render : new boolean[]{false, true}) {

                int[] pb = m.playBox();
                RecordingSink ex = new RecordingSink();
                int ceil = m.measureCeilY();
                for (int x = pb[0]; x <= pb[3]; x++)
                    for (int z = pb[2]; z <= pb[5]; z++)
                        for (int y = pb[1]; y < ceil; y++) {
                            if (render && m instanceof SuperMap) {
                                int lz = z - SuperMap.Z0;
                                if (lz >= SuperMap.PARK_Z1 && lz <= SuperMap.PARK_Z2 && y >= SuperMap.PCY) continue;
                            }
                            String st = s.get(x, y, z);
                            if (!B.isAir(st)) ex.set(x, y, z, st);
                        }
                File sf = new File(out, "hs_" + m.id + (render ? "_rendu" : "") + ".schem");
                SchemWriter.write(ex, sf);
                System.out.println("schem : " + sf);
            }
        }
        System.out.println("PROBLEMES BLOQUANTS : " + bad);
    }

    static int count(String report, String cat) {
        for (String line : report.split("\n")) {
            String l = line.trim();
            if (l.startsWith(cat)) {
                String[] p = l.split("[^0-9]+");
                for (String q : p) if (!q.isEmpty()) return Integer.parseInt(q);
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ mesures

    /** Pas de collision (on traverse) : air, tapis, boutons, crochets, vegetation basse, lumiere. */
    static boolean free(String st) {
        if (B.isAir(st)) return true;
        String n = B.name(st);
        return n.endsWith("_carpet") || n.endsWith("_button") || (n.endsWith("_door") && !n.endsWith("trapdoor")) || (n.endsWith("_trapdoor") && "true".equals(B.prop(st, "open"))) || n.equals("ladder") || n.equals("seagrass") || n.equals("water") || n.equals("tripwire_hook") || n.equals("lever")
                || n.equals("light") || n.equals("short_grass") || n.endsWith("_pressure_plate");
    }

    static boolean ladder(RecordingSink s, int x, int y, int z) {
        return B.name(s.get(x, y, z)).equals("ladder");
    }

    /** Debout : sol porteur dessous (ni libre ni eau) ou echelle dans la case, 2 cases libres. */
    static boolean standable(RecordingSink s, int x, int y, int z) {
        String below = s.get(x, y - 1, z);
        boolean support = (!free(below) && !B.name(below).equals("barrier")) || ladder(s, x, y, z) || ladder(s, x, y - 1, z);
        return support && free(s.get(x, y, z)) && free(s.get(x, y + 1, z));
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 30000) << 40) | ((long) (z + 30000) << 16) | (y + 1000);
    }

    static void measure(HsMap m, RecordingSink s) {
        int[] pb = m.playBox();
        int ceil = m.measureCeilY();
        int fy = m.measureFloorY();
        // cases debout de la zone de jeu
        Set<Long> stand = new HashSet<>();
        for (int x = pb[0]; x <= pb[3]; x++)
            for (int z = pb[2]; z <= pb[5]; z++)
                for (int y = fy + 1; y < ceil; y++)
                    if (standable(s, x, y, z)) stand.add(key(x, y, z));
        // parcours a pied depuis le point des cacheurs (marche de 1, chute libre)
        double[] hs = m.point("hider-spawn");
        double[] ss = m.point("seeker-spawn");
        int[] start = {(int) Math.floor(hs[0]), (int) Math.floor(hs[1]), (int) Math.floor(hs[2])};
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        if (stand.contains(key(start[0], start[1], start[2]))) { seen.add(key(start[0], start[1], start[2])); q.add(start); }
        else System.out.println("!! point des cacheurs non debout : " + java.util.Arrays.toString(start));
        int[][] d4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : d4) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                // echelle : on monte et descend dans la colonne
                if (d == d4[0]) {
                    if ((ladder(s, c[0], c[1], c[2]) || ladder(s, c[0], c[1] + 1, c[2])) && stand.contains(key(c[0], c[1] + 1, c[2]))) visit(seen, q, c[0], c[1] + 1, c[2]);
                    if (ladder(s, c[0], c[1] - 1, c[2]) && stand.contains(key(c[0], c[1] - 1, c[2]))) visit(seen, q, c[0], c[1] - 1, c[2]);
                }
                // montee de 1 (place pour sauter au-dessus de la tete)
                int up = c[1] + 1;
                if (free(s.get(c[0], c[1] + 2, c[2])) && stand.contains(key(nx, up, nz))) visit(seen, q, nx, up, nz);
                // meme niveau ou chute
                for (int y = c[1]; y >= fy + 1; y--) {
                    if (!free(s.get(nx, y + 1, nz))) break;
                    if (stand.contains(key(nx, y, nz))) { visit(seen, q, nx, y, nz); break; }
                    if (!free(s.get(nx, y, nz))) break;
                }
            }
        }
        int floor = 0, floorOk = 0;
        boolean natural = m.groundY(pb[0] + 5, pb[2] + 40) != Integer.MIN_VALUE;
        java.util.function.LongPredicate onGround = k -> {
            int x = (int) ((k >> 40) - 30000), z = (int) (((k >> 16) & 0xFFFFFF) - 30000), y = (int) ((k & 0xFFFF) - 1000);
            if (!natural) return y == fy + 1;
            int g = m.groundY(x, z);
            return g != Integer.MIN_VALUE && y == g + 1;
        };
        for (long k : stand) if (onGround.test(k)) { floor++; if (seen.contains(k)) floorOk++; }
        System.out.printf("sol : %d cases debout au niveau du sol, %d atteintes (%.1f %%)%n", floor, floorOk, 100.0 * floorOk / Math.max(1, floor));
        StringBuilder fl = new StringBuilder("   cases du sol non atteintes :");
        int nfl = 0;
        for (long k : stand) if (onGround.test(k) && !seen.contains(k) && nfl++ < 30)
            fl.append(" (").append((k >> 40) - 30000).append(",").append((k & 0xFFFF) - 1000).append(",").append(((k >> 16) & 0xFFFFFF) - 30000).append(")");
        if (nfl > 0) System.out.println(fl);
        boolean seekerOk = seen.contains(key((int) Math.floor(ss[0]), (int) Math.floor(ss[1]), (int) Math.floor(ss[2])));
        // poches inaccessibles : regroupees par composante
        Set<Long> rest = new HashSet<>(stand);
        rest.removeAll(seen);
        System.out.printf("accessibilite : %d cases debout, %d atteintes a pied (%.1f %%), point des chercheurs %s%n",
                stand.size(), seen.size(), 100.0 * seen.size() / Math.max(1, stand.size()), seekerOk ? "atteint" : "NON ATTEINT");
        TreeMap<Integer, String> pockets = new TreeMap<>();
        Set<Long> done = new HashSet<>();
        for (long k : rest) {
            if (done.contains(k)) continue;
            int n = 0;
            ArrayDeque<Long> qq = new ArrayDeque<>();
            qq.add(k); done.add(k);
            long first = k;
            while (!qq.isEmpty()) {
                long c = qq.poll();
                n++;
                int x = (int) ((c >> 40) - 30000), z = (int) (((c >> 16) & 0xFFFFFF) - 30000), y = (int) ((c & 0xFFFF) - 1000);
                for (int[] d : d4) for (int dy = -1; dy <= 1; dy++) {
                    long nk = key(x + d[0], y + dy, z + d[1]);
                    if (rest.contains(nk) && done.add(nk)) qq.add(nk);
                }
            }
            int x = (int) ((first >> 40) - 30000), z = (int) (((first >> 16) & 0xFFFFFF) - 30000), y = (int) ((first & 0xFFFF) - 1000);
            pockets.merge(-n, x + " " + y + " " + z, (o, nn) -> o);
        }
        int shown = 0;
        for (Map.Entry<Integer, String> e : pockets.entrySet()) {
            if (shown++ >= 12) break;
            System.out.println("   poche inaccessible de " + (-e.getKey()) + " cases vers " + e.getValue());
        }
        System.out.println("   poches : " + pockets.size());
        // poches couvertes (un bloc au-dessus dans les 8 cases) = pieces fermees ; les autres = toits, falaises
        int covered = 0, coveredCells = 0;
        StringBuilder cv = new StringBuilder();
        for (long k : rest) {
            int x = (int) ((k >> 40) - 30000), z = (int) (((k >> 16) & 0xFFFFFF) - 30000), y = (int) ((k & 0xFFFF) - 1000);
            boolean roofed = false;
            for (int dy = 2; dy <= 8 && !roofed; dy++) if (!free(s.get(x, y + dy, z)) && !B.name(s.get(x, y + dy, z)).equals("barrier")) roofed = true;
            String bl = B.name(s.get(x, y - 1, z));
            if (roofed && (bl.endsWith("_planks") || bl.equals("smooth_stone") || bl.equals("stone_bricks"))) { coveredCells++; if (covered++ < 40) cv.append(" (").append(x).append(",").append(y).append(",").append(z).append(")"); }
        }
        System.out.println("   cases inaccessibles SOUS un toit sur un plancher : " + coveredCells + (coveredCells > 0 ? " :" + cv : ""));
        // part des blocs de deguisement dans le decor visible (blocs pleins vus depuis une case libre)
        Set<String> disg = new HashSet<>();
        for (String k : m.disguises().keySet()) disg.add(k.toLowerCase());
        Set<String> structure = Set.of("smooth_quartz", "quartz_block", "calcite", "quartz_pillar", "smooth_stone",
                "polished_andesite", "andesite", "polished_blackstone", "stripped_birch_log", "stripped_spruce_log",
                "gray_concrete", "polished_diorite", "light_gray_terracotta", "sea_lantern", "white_concrete_powder",
                "gray_concrete_powder", "smooth_sandstone", "white_terracotta", "blue_terracotta");
        Map<String, Integer> vis = new LinkedHashMap<>();
        int all = 0, decor = 0, disgAll = 0, disgDecor = 0;
        Map<String, Integer> present = new HashMap<>();
        for (int x = pb[0]; x <= pb[3]; x++)
            for (int z = pb[2]; z <= pb[5]; z++)
                for (int y = fy + 1; y < ceil; y++) {
                    String st = s.get(x, y, z);
                    String n = B.name(st);
                    if (disg.contains(n)) present.merge(n, 1, Integer::sum);
                    if (!B.isSolid(st) || n.contains("glass")) continue;
                    boolean seenFace = free(s.get(x + 1, y, z)) || free(s.get(x - 1, y, z)) || free(s.get(x, y + 1, z))
                            || free(s.get(x, y, z + 1)) || free(s.get(x, y, z - 1));
                    if (!seenFace) continue;
                    all++;
                    boolean d = disg.contains(n);
                    if (d) disgAll++;
                    if (!structure.contains(n)) {
                        decor++;
                        if (d) disgDecor++;
                        else vis.merge(n, 1, Integer::sum);
                    }
                }
        System.out.printf("deguisements : %.1f %% des blocs pleins visibles (hors sol/plafond), %.1f %% du decor (hors murs/structure)%n",
                100.0 * disgAll / Math.max(1, all), 100.0 * disgDecor / Math.max(1, decor));
        StringBuilder sb = new StringBuilder("   decor visible hors liste :");
        vis.entrySet().stream().sorted((p, r) -> r.getValue() - p.getValue()).limit(12)
                .forEach(e -> sb.append(' ').append(e.getKey()).append('=').append(e.getValue()));
        System.out.println(sb);
        StringBuilder miss = new StringBuilder();
        for (String k : disg) if (!present.containsKey(k)) miss.append(' ').append(k);
        System.out.println("   blocs de deguisement absents de la zone de jeu :" + (miss.length() == 0 ? " aucun" : miss));
    }

    static void visit(Set<Long> seen, ArrayDeque<int[]> q, int x, int y, int z) {
        if (seen.add(key(x, y, z))) q.add(new int[]{x, y, z});
    }
}
