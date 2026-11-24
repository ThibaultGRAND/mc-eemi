package fr.eemicraft.infection;

import fr.eemicraft.infection.build.B;
import fr.eemicraft.infection.build.Check;
import fr.eemicraft.infection.build.Dir;
import fr.eemicraft.infection.build.RecordingSink;
import fr.eemicraft.infection.build.SchemWriter;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Test hors serveur (non embarqué dans le jar) : construit la ville dans un RecordingSink, compte les cases
 * de dispersion comme scanSpots(), écrit un .schem de la zone jouable pour analyze.py.
 * Usage : TestCity <old|new> <dossier de sortie>
 */
public final class TestCity {
    public static void main(String[] a) throws Exception {
        String mode = a.length > 0 ? a[0] : "new";
        File out = new File(a.length > 1 ? a[1] : ".");
        out.mkdirs();
        Logger log = Logger.getLogger("test");
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        long ops;
        if (mode.equals("old")) {
            CityOld c2 = new CityOld(s, log);
            c2.build();
            ops = c2.ops;
        } else {
            City c = new City(s, log);
            c.build();
            ops = c.ops;
            System.out.println("Echelles : " + c.laddersMade + ", escaliers de secours : " + c.fireEscapes);
            invariants(s);
            int bad = flights(s, c.flights) + trapdoors(s);
            allStairs(s);
            System.out.println(bad == 0 ? "Escaliers et trappes : OK" : "Escaliers et trappes : " + bad + " PROBLEME(S)");
        }
        System.out.println("Operations : " + ops + " (sink " + s.operations() + ") en " + (System.currentTimeMillis() - t0) + " ms");
        spots(s);
        // zone jouable seulement (sans le sous-sol ni la barriere ni le hangar)
        int G = City.G, L = City.WALL - 1;
        RecordingSink crop = new RecordingSink();
        for (int x = -L; x <= L; x++)
            for (int z = -L; z <= L; z++)
                for (int y = G - 1; y <= G + 40; y++) {
                    int id = s.id(x, y, z);
                    if (id != 0) crop.set(x, y, z, s.state(id));
                }
        System.out.println(Check.run(crop));
        pans(crop);
        File f = new File(out, "infection_" + mode + ".schem");
        SchemWriter.write(crop, f);
        System.out.println("Ecrit : " + f.getAbsolutePath());
        // types utilises (pour verification contre Material)
        Map<String, Integer> ids = new TreeMap<>();
        for (int i = 1; i < s.paletteSize(); i++) ids.merge(B.name(s.state(i)), 1, Integer::sum);
        java.nio.file.Files.writeString(new File(out, "ids_" + mode + ".txt").toPath(), String.join("\n", ids.keySet()));
    }

    static void invariants(RecordingSink s) {
        int G = City.G;
        String[] pts = {"EXIT", "CRYPT", "WAIT", "SPECT"};
        double[][] P = {City.EXIT, City.CRYPT, City.WAIT, City.SPECT};
        for (int i = 0; i < 4; i++) {
            int x = (int) Math.floor(P[i][0]), y = (int) Math.floor(P[i][1]), z = (int) Math.floor(P[i][2]);
            System.out.println(pts[i] + " " + x + " " + y + " " + z + " : pieds " + B.name(s.get(x, y, z)) + ", tete " + B.name(s.get(x, y + 1, z)) + ", sol " + B.name(s.get(x, y - 1, z)));
        }
        System.out.println("Panneau (0," + (G + 4) + "," + (City.HZ1 + 2) + ") : " + B.name(s.get(0, G + 4, City.HZ1 + 2)) + ", tableau derriere : " + B.name(s.get(0, G + 4, City.HZ1 + 1)));
        System.out.println("Crypte fermee : " + (flood(s, -14, G - 9, -33, -24, G - 14, -42, -4, G - 2, -24) ? "NON (fuite)" : "oui"));
        System.out.println("Hangar ferme : " + (flood(s, 0, G + 1, City.HZ1 + 10, -15, G - 1, City.HZ1 - 3, 15, G + 16, City.HZ2 + 3) ? "NON (fuite)" : "oui"));
    }

    /** Remplissage de l\u0027air depuis (x,y,z) dans la boite : vrai si on atteint le bord de la boite. */
    static boolean free(String st) {
        String n = B.name(st);
        return B.isAir(st) || n.endsWith("_carpet") || n.equals("light") || n.equals("short_grass") || n.equals("cobweb");
    }

    /**
     * Volees posees par City.interiorStairs : chaque marche est un escalier bas (facing = sens de montee, shape straight)
     * avec 2 blocs libres au-dessus ; la marche suivante est bien un bloc plus haut dans le sens de montee ; abord de face
     * ou par un cote (pieds + tete libres, sol plein) ; arrivee = plancher plein + 2 blocs libres.
     */
    static int flights(RecordingSink s, java.util.List<int[]> fl) {
        int bad = 0, gone = 0;
        java.util.List<String> ex = new java.util.ArrayList<>();
        for (int[] f : fl) {
            Dir d = Dir.values()[f[3]];
            int n = f[4];
            String first = s.get(f[0], f[1], f[2]);
            if (!B.name(first).endsWith("_stairs")) {
                gone++;
                System.out.println("  volee disparue en " + f[0] + " " + f[1] + " " + f[2] + " : " + B.name(first) + ", au-dessus " + B.name(s.get(f[0], f[1] + 1, f[2])) + ", marches " + B.name(s.get(f[0] + d.dx, f[1] + 1, f[2] + d.dz)) + " "
                        + B.name(s.get(f[0] + 2 * d.dx, f[1] + 2, f[2] + 2 * d.dz)) + ", sol " + B.name(s.get(f[0], f[1] - 1, f[2])) + " (ruine ou decor pose par-dessus)");
                continue;
            }
            String why = null;
            for (int k = 0; k < n && why == null; k++) {
                int x = f[0] + d.dx * k, y = f[1] + k, z = f[2] + d.dz * k;
                String st = s.get(x, y, z);
                if (!B.name(st).endsWith("_stairs")) why = "marche " + k + " absente (" + B.name(st) + ")";
                else if (!d.id().equals(B.prop(st, "facing")) || !"bottom".equals(B.prop(st, "half"))) why = "marche " + k + " mal orientee " + st;
                else if (!"straight".equals(B.prop(st, "shape"))) why = "marche " + k + " shape " + B.prop(st, "shape");
                else if (!free(s.get(x, y + 1, z)) || !free(s.get(x, y + 2, z))) why = "marche " + k + " : pas 2 blocs libres au-dessus ("
                        + B.name(s.get(x, y + 1, z)) + "/" + B.name(s.get(x, y + 2, z)) + ")";
                else if (k < n - 1 && !free(s.get(x, y + 3, z))) why = "marche " + k + " : pas de 3e bloc libre pour passer a la suivante (" + B.name(s.get(x, y + 3, z)) + ")";
            }
            if (why == null) {
                int ax = f[0] + d.dx * n, ay = f[1] + n, az = f[2] + d.dz * n;
                if (!B.isSolid(s.get(ax, ay - 1, az))) why = "arrivee sans plancher (" + B.name(s.get(ax, ay - 1, az)) + ")";
                else if (!free(s.get(ax, ay, az)) || !free(s.get(ax, ay + 1, az))) why = "arrivee bouchee (" + B.name(s.get(ax, ay, az)) + "/" + B.name(s.get(ax, ay + 1, az)) + ")";
            }
            if (why == null) {
                boolean ok = false;
                int[][] app = {{-d.dx, -d.dz}, {d.dz, -d.dx}, {-d.dz, d.dx}};
                for (int[] a : app) {
                    int x = f[0] + a[0], z = f[2] + a[1], y = f[1];
                    if (free(s.get(x, y, z)) && free(s.get(x, y + 1, z)) && B.isSolid(s.get(x, y - 1, z))) ok = true;
                }
                if (!ok) {
                    StringBuilder sb = new StringBuilder("aucun abord libre :");
                    for (int[] a : app) {
                        int x = f[0] + a[0], z = f[2] + a[1], y = f[1];
                        sb.append(" [").append(B.name(s.get(x, y - 1, z))).append('/').append(B.name(s.get(x, y, z))).append('/').append(B.name(s.get(x, y + 1, z))).append(']');
                    }
                    why = sb.toString();
                }
            }
            if (why != null) {
                bad++;
                if (ex.size() < 20) ex.add(f[0] + " " + f[1] + " " + f[2] + " vers " + d.id() + " : " + why);
            }
        }
        System.out.println("Volees interieures : " + fl.size() + ", en ruine " + gone + ", problemes " + bad);
        for (String e : ex) System.out.println("  " + e);
        return bad;
    }

    /**
     * Tous les escaliers bas accessibles a pied (hors volees deja verifiees) : avertissement si la case dans le sens de
     * montee est un mur de 2 de haut (la marche mene contre un mur) ou si l'escalier n'a pas 2 blocs libres au-dessus
     * alors qu'on arrive dessus. Les escaliers de decor (chaises, bancs, toits) peuvent apparaitre : liste a lire.
     */
    static void allStairs(RecordingSink s) {
        int G = City.G, lim = City.WALL - 2;
        Reach reach = new Reach((x, y, z) -> {
            String st = s.get(x, y, z);
            String n = B.name(st);
            if (n.equals("ladder") || n.contains("vine") || n.equals("scaffolding")) return Reach.PASS | Reach.CLIMB;
            if (B.isAir(st) || n.endsWith("_carpet") || n.equals("snow") || (n.endsWith("_door") && !n.startsWith("iron"))
                    || n.endsWith("trapdoor") || (!B.isSolid(st) && !n.endsWith("_stairs") && !n.endsWith("_slab"))) return Reach.PASS;
            return 0;
        }, lim, G, G + 26);
        java.util.List<int[]> seeds = new java.util.ArrayList<>();
        for (int a = -lim; a <= lim; a++) for (int b = -3; b <= 3; b++) { seeds.add(new int[]{a, G + 1, b}); seeds.add(new int[]{b, G + 1, a}); }
        reach.run(seeds.toArray(new int[0][]));
        int n = 0, wall = 0, low = 0;
        java.util.List<String> ex = new java.util.ArrayList<>();
        for (int x = -lim; x <= lim; x++)
            for (int z = -lim; z <= lim; z++)
                for (int y = G; y <= G + 24; y++) {
                    String st = s.get(x, y, z);
                    if (!B.name(st).endsWith("_stairs") || !"bottom".equals(B.prop(st, "half"))) continue;
                    // on y arrive a pied : une case voisine au meme niveau est atteinte (pieds en y)
                    boolean near = false;
                    for (Dir d : Dir.values()) if (reach.reached(x + d.dx, y, z + d.dz)) near = true;
                    if (!near) continue;
                    n++;
                    Dir f = Dir.of(B.prop(st, "facing"));
                    boolean headOk = free(s.get(x, y + 1, z)) && free(s.get(x, y + 2, z));
                    String u1 = s.get(x + f.dx, y + 1, z + f.dz), u2 = s.get(x + f.dx, y + 2, z + f.dz);
                    String why = null;
                    if (!headOk && !B.isSolid(s.get(x, y + 1, z))) { low++; why = "pas 2 blocs libres au-dessus (" + B.name(s.get(x, y + 1, z)) + "/" + B.name(s.get(x, y + 2, z)) + ")"; }
                    else if (headOk && B.isSolid(u1) && B.isSolid(u2)) { wall++; why = "monte contre un mur (" + B.name(u1) + ")"; }
                    if (why != null && ex.size() < 25) ex.add(x + " " + y + " " + z + " " + B.name(st) + " vers " + f.id() + " : " + why);
                }
        System.out.println("Escaliers accessibles : " + n + ", contre un mur : " + wall + ", plafond bas : " + low + " (avertissements, decor compris)");
        for (String e : ex) System.out.println("  " + e);
    }

    /** Trappes ouvertes : le panneau se plaque contre la face OPPOSEE a facing ; le bloc de ce cote doit etre plein. */
    static int trapdoors(RecordingSink s) {
        int[] b = s.bounds();
        int n = 0, bad = 0;
        java.util.List<String> ex = new java.util.ArrayList<>();
        for (int x = b[0]; x <= b[3]; x++)
            for (int y = b[1]; y <= b[4]; y++)
                for (int z = b[2]; z <= b[5]; z++) {
                    String st = s.get(x, y, z);
                    if (!B.name(st).endsWith("_trapdoor") || !"true".equals(B.prop(st, "open"))) continue;
                    n++;
                    Dir d = Dir.of(B.prop(st, "facing"));
                    String back = s.get(x - d.dx, y, z - d.dz);
                    if (!B.isSolid(back)) {
                        bad++;
                        if (ex.size() < 20) ex.add(x + " " + y + " " + z + " " + st + " contre " + B.name(back));
                    }
                }
        System.out.println("Trappes ouvertes : " + n + ", sans bloc plein derriere : " + bad);
        for (String e : ex) System.out.println("  " + e);
        return bad;
    }

    static boolean flood(RecordingSink s, int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2) {
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(new int[]{x0, y0, z0});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] <= x1 || c[0] >= x2 || c[1] <= y1 || c[1] >= y2 || c[2] <= z1 || c[2] >= z2) { System.out.println("  fuite en " + c[0] + " " + c[1] + " " + c[2]); return true; }
            long k = City.key(c[0], c[1], c[2]);
            if (!seen.add(k)) continue;
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] e : d) {
                int nx = c[0] + e[0], ny = c[1] + e[1], nz = c[2] + e[2];
                String st = s.get(nx, ny, nz);
                String n = B.name(st);
                boolean pass = B.isAir(st) || n.contains("carpet") || (n.contains("lantern") && !n.equals("sea_lantern")) || n.contains("cobweb") || n.contains("chain")
                        || n.contains("campfire") || n.equals("light") || n.contains("door") || n.contains("ladder") || n.contains("skull") || n.contains("candle");
                if (pass && !seen.contains(City.key(nx, ny, nz))) q.add(new int[]{nx, ny, nz});
            }
        }
        return false;
    }

    /** Plus longs pans coplanaires (meme logique que Check, simplifiee) avec leur position, pour corriger. */
    static void pans(RecordingSink r) {
        int[] b = r.bounds();
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.List<Integer> lens = new java.util.ArrayList<>();
        int[][] nrm = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] nd : nrm)
            for (int y = b[1] + 1; y < b[4]; y++) {
                boolean axZ = nd[0] == 0;
                int aMin = axZ ? b[2] : b[0], aMax = axZ ? b[5] : b[3];
                int tMin = axZ ? b[0] : b[2], tMax = axZ ? b[3] : b[5];
                for (int a = aMin + 1; a <= aMax - 1; a++) {
                    int run = 0, start = 0;
                    for (int t = tMin; t <= tMax + 1; t++) {
                        int x = axZ ? t : a, z = axZ ? a : t;
                        boolean face = t <= tMax && wallFace(r, x, y, z, nd) && (wallFace(r, x, y + 1, z, nd) || wallFace(r, x, y - 1, z, nd));
                        if (face) {
                            if (run == 0) start = t;
                            run++;
                        } else {
                            if (run >= 12) {
                                int sx = axZ ? start : a, sz = axZ ? a : start;
                                found.add(run + " blocs a partir de " + sx + " " + y + " " + sz + " normale " + nd[0] + "," + nd[1] + " (" + B.name(r.get(sx, y, sz)) + ")");
                                lens.add(run);
                            }
                            run = 0;
                        }
                    }
                }
            }
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        for (int i = 0; i < lens.size(); i++) idx.add(i);
        idx.sort((p, q) -> lens.get(q) - lens.get(p));
        System.out.println("Pans >= 12 : " + lens.size());
        for (int i = 0; i < Math.min(12, idx.size()); i++) System.out.println("  " + found.get(idx.get(i)));
    }

    static boolean wallFace(RecordingSink r, int x, int y, int z, int[] nd) {
        String st = r.get(x, y, z);
        String n = B.name(st);
        if (B.isAir(st) || !B.isSolid(st) || n.contains("leaves") || n.equals("barrier")) return false;
        for (String k : new String[]{"stone", "dirt", "grass_block", "gravel", "mud", "podzol", "moss_block", "tuff", "calcite", "andesite"})
            if (n.equals(k) || n.equals("coarse_dirt") || n.equals("rooted_dirt")) return false;
        String f = r.get(x + nd[0], y, z + nd[1]);
        String fn = B.name(f);
        return B.isAir(f) || fn.contains("carpet") || fn.contains("lantern") || fn.contains("grass") || fn.contains("torch");
    }

    static final String[] NOT_STAND = {"leaves", "fence", "_wall", "gate", "iron_bars", "pane", "campfire", "barrier", "carpet",
            "trapdoor", "door", "lantern", "chain", "ladder", "torch", "button", "pressure_plate", "sign", "cobweb", "candle",
            "skull", "head", "rod", "bell", "potted", "flower_pot", "chest", "water", "lava", "light", "grass", "fern", "bush",
            "flower", "tulip", "lily", "vine", "rail", "tripwire", "sapling", "scaffolding", "end_rod", "pickle", "dripleaf",
            "petals", "bamboo", "kelp", "mushroom", "wheat", "roots", "lichen", "hook", "brewing", "conduit", "snow"};

    static boolean standable(String st) {
        String n = B.name(st);
        if (B.isAir(st)) return false;
        if (n.equals("grass_block") || n.endsWith("_block") && !n.contains("mushroom")) return true;
        if (n.equals("mangrove_roots") || n.equals("muddy_mangrove_roots")) return true;
        for (String k : NOT_STAND) if (n.contains(k)) return false;
        return true;
    }

    static boolean feetOk(String st) {
        String n = B.name(st);
        return B.isAir(st) || n.endsWith("_carpet") || n.equals("short_grass") || n.equals("dead_bush");
    }

    static void spots(RecordingSink s) {
        int G = City.G, lim = City.WALL - 2;
        int total = 0, low = 0, high = 0;
        int[] byLevel = new int[23];
        for (int x = -lim; x <= lim; x++)
            for (int z = -lim; z <= lim; z++)
                for (int y = G + 1; y <= G + 22; y++) {
                    if (!feetOk(s.get(x, y, z)) || !B.isAir(s.get(x, y + 1, z)) || !standable(s.get(x, y - 1, z))) continue;
                    total++;
                    byLevel[y - G]++;
                    if (y <= G + 11) low++; else high++;
                }
        System.out.println("Cases de dispersion : " + total + " (y <= G+11 : " + low + ", plus haut : " + high + ")");
        // accessibilite a pied depuis les rues (meme logique que scanSpots du plugin)
        Reach reach = new Reach((x, y, z) -> {
            String st = s.get(x, y, z);
            String n = B.name(st);
            if (n.equals("ladder") || n.contains("vine") || n.equals("scaffolding")) return Reach.PASS | Reach.CLIMB;
            if (B.isAir(st) || n.endsWith("_carpet") || n.equals("snow") || (n.endsWith("_door") && !n.startsWith("iron"))
                    || n.endsWith("trapdoor") || !B.isSolid(st)) return Reach.PASS;
            return 0;
        }, lim, G, G + 26);
        java.util.List<int[]> seeds = new java.util.ArrayList<>();
        for (int a = -lim; a <= lim; a++) for (int b = -3; b <= 3; b++) { seeds.add(new int[]{a, G + 1, b}); seeds.add(new int[]{b, G + 1, a}); }
        seeds.add(new int[]{(int) Math.floor(City.EXIT[0]), (int) Math.floor(City.EXIT[1]), (int) Math.floor(City.EXIT[2])});
        int reached = reach.run(seeds.toArray(new int[0][]));
        int ok = 0, okHigh = 0, shut = 0;
        java.util.List<String> ex = new java.util.ArrayList<>();
        for (int x = -lim; x <= lim; x++)
            for (int z = -lim; z <= lim; z++)
                for (int y = G + 1; y <= G + 22; y++) {
                    if (!feetOk(s.get(x, y, z)) || !B.isAir(s.get(x, y + 1, z)) || !standable(s.get(x, y - 1, z))) continue;
                    if (reach.reached(x, y, z)) { ok++; if (y > G + 11) okHigh++; }
                    else { shut++; if (ex.size() < 15 && shut % 97 == 1) ex.add(x + " " + y + " " + z + " sous " + B.name(s.get(x, y - 1, z))); }
                }
        System.out.println("Accessibilite : " + reached + " positions atteintes ; cases gardees " + ok + " (en hauteur " + okHigh + "), enfermees " + shut);
        for (String e : ex) System.out.println("  enfermee : " + e);
        StringBuilder sb = new StringBuilder("  par hauteur des pieds (G+n) :");
        for (int i = 1; i < byLevel.length; i++) if (byLevel[i] > 0) sb.append(' ').append(i).append('=').append(byLevel[i]);
        System.out.println(sb);
    }
}
