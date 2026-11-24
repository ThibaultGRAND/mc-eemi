package fr.eemicraft.sheepwars.gen;

import fr.eemicraft.sheepwars.gen.build.Audit;
import fr.eemicraft.sheepwars.gen.build.B;
import fr.eemicraft.sheepwars.gen.build.Connect;
import fr.eemicraft.sheepwars.gen.build.RecordingSink;
import fr.eemicraft.sheepwars.gen.build.SchemWriter;
import fr.eemicraft.sheepwars.gen.build.Stairs;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * Generateur HORS SERVEUR des maps Sheep Wars faites par code (4.5.0). Construit le camp Rouge, le copie en
 * miroir pour le camp Bleu (gameplay identique), ajoute un decor propre a chaque camp (vegetation, petits objets),
 * puis ecrit la map au format SWM2 (palette d'etats modernes) dans src/main/resources/maps/<id>.swm.
 *
 * Usage : java -cp classes fr.eemicraft.sheepwars.gen.SwGen <dossier resources/maps> <dossier sortie> [ids...]
 */
public final class SwGen {

    /** Une map : le camp Rouge (u = -x > 0) et ses reglages pour le plugin. */
    interface MapDef {
        String id();
        String name();
        /** Hauteur de mort relative au bas de la map (pieds sous cette valeur = mort). */
        int voidRel();
        /** Hauteur relative de la laine magique (elle apparait de magicRel a magicRel+2, en x = 0, |z| 10-14). */
        int magicRel();
        /** Y du sol (bloc) des places de depart. */
        int spawnFloor();
        /** Demi-largeur du vide : rien en |x| < gap (sauf sous la hauteur de mort). */
        int gap();
        long seed();
        /** Construit le camp Rouge (et ce qui est commun en x <= 0 : mer...). */
        void buildRed(Gen g);
        /** Decor propre a un camp (side -1 Rouge, +1 Bleu), pose seulement dans l'air. */
        void decor(Gen g, int side);
        /** Exceptions d'audit justifiees (boites en coordonnees MONDE du camp Rouge ; le miroir Bleu est ajoute). */
        default int[][] auditIgnores() { return new int[0][]; }
        default String auditIgnoreWhy() { return ""; }
    }

    /** Places de depart (u, z), 12 par equipe, 2 blocs d'ecart (les cages 3x3 partagent leurs murs). */
    static final int[][] SPAWNS = {{20, -3}, {20, -1}, {20, 1}, {20, 3}, {22, -3}, {22, -1}, {22, 1}, {22, 3},
            {24, -3}, {24, -1}, {24, 1}, {24, 3}};

    public static void main(String[] args) throws IOException {
        File res = new File(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        List<MapDef> all = List.of(new Pirates(), new Ruines(), new Spatiale());
        boolean ok = true;
        for (MapDef m : all) {
            if (args.length > 2) {
                boolean want = false;
                for (int i = 2; i < args.length; i++) if (args[i].equals(m.id())) want = true;
                if (!want) continue;
            }
            ok &= make(m, res, out);
        }
        if (!ok) {
            System.out.println("ECHEC d'un controle (voir ci-dessus).");
            System.exit(2);
        }
    }

    static boolean make(MapDef m, File res, File out) throws IOException {
        long t0 = System.currentTimeMillis();
        RecordingSink half = new RecordingSink();
        m.buildRed(new Gen(half, m.seed()));
        int[] hb = half.bounds();
        RecordingSink fin = new RecordingSink();
        for (int x = hb[0]; x <= Math.min(0, hb[3]); x++)
            for (int y = hb[1]; y <= hb[4]; y++)
                for (int z = hb[2]; z <= hb[5]; z++) {
                    String st = half.get(x, y, z);
                    if (B.isAir(st)) continue;
                    fin.set(x, y, z, st.replace("TEAM", "red"));
                    if (x < 0) fin.set(-x, y, z, mirror(st).replace("TEAM", "blue"));
                    else fin.set(0, y, z, st.replace("TEAM", "white"));
                }
        Gen gr = new Gen(fin, m.seed() * 31 + 7);
        gr.team = "red";
        m.decor(gr, -1);
        m.decor(new MirrorGen(fin, m.seed() * 31 + 11), 1);
        int[] b = fin.bounds();
        Stairs.fix(fin, b[0], b[1], b[2], b[3], b[4], b[5]);
        Connect.fix(fin, b[0], b[1], b[2], b[3], b[4], b[5]);
        boolean ok = check(m, fin);
        String bj = System.getenv("BLOCKS_JSON");
        Audit au = new Audit(fin);
        for (int[] bx : m.auditIgnores()) {
            au.ignore("ESCALIER", bx[0], bx[1], bx[2], bx[3], bx[4], bx[5], m.auditIgnoreWhy());
            au.ignore("ESCALIER", -bx[0], bx[1], bx[2], -bx[3], bx[4], bx[5], m.auditIgnoreWhy());
        }
        au.run(bj == null || bj.isBlank() ? null : java.nio.file.Path.of(bj));
        System.out.print(au.report(m.id()));
        if (bj == null || bj.isBlank()) { System.out.println("  BLOCKS_JSON absent : etats non verifies"); ok = false; }
        if (au.total() > 0) ok = false;
        writeSwm(m, fin, new File(res, m.id() + ".swm"));
        SchemWriter.write(fin, new File(out, "sw_" + m.id() + "_gen.schem"));
        TreeSet<String> states = new TreeSet<>();
        for (int i = 1; i < fin.paletteSize(); i++) states.add(fin.state(i));
        try (PrintWriter w = new PrintWriter(new File(out, "sw_" + m.id() + ".states.txt"), StandardCharsets.UTF_8)) {
            for (String st : states) w.println(st);
        }
        System.out.println(m.id() + " : " + fin.count() + " blocs, " + states.size() + " etats, bornes x " + b[0] + ".." + b[3]
                + " y " + b[1] + ".." + b[4] + " z " + b[2] + ".." + b[5] + " (" + (System.currentTimeMillis() - t0) + " ms)");
        return ok;
    }

    /** Controles : rien dans le vide central, places de depart libres. */
    static boolean check(MapDef m, RecordingSink s) {
        int[] b = s.bounds();
        boolean ok = true;
        int bad = 0;
        for (int x = -m.gap() + 1; x <= m.gap() - 1; x++)
            for (int y = Math.max(b[1], m.voidRel()); y <= b[4]; y++)
                for (int z = b[2]; z <= b[5]; z++)
                    if (!B.isAir(s.get(x, y, z))) {
                        if (bad++ < 5) System.out.println("  bloc dans le vide central : " + x + " " + y + " " + z + " " + s.get(x, y, z));
                    }
        if (bad > 0) { System.out.println("  " + bad + " bloc(s) dans le vide central"); ok = false; }
        for (int side : new int[]{-1, 1})
            for (int[] p : SPAWNS) {
                int x = side * p[0], z = p[1], y = m.spawnFloor();
                if (!B.isSolid(s.get(x, y, z))) { System.out.println("  depart sans sol : " + x + " " + y + " " + z + " " + s.get(x, y, z)); ok = false; }
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = 1; dy <= 4; dy++)
                            if (!B.isAir(s.get(x + dx, y + dy, z + dz))) {
                                System.out.println("  cage genee : " + (x + dx) + " " + (y + dy) + " " + (z + dz) + " " + s.get(x + dx, y + dy, z + dz));
                                ok = false;
                            }
            }
        // blocs soumis a la gravite (sable, gravier...) poses au-dessus d'un vide : ils tomberaient a la 1re mise a jour
        int fall = 0;
        for (int x = b[0]; x <= b[3]; x++)
            for (int y = b[1] + 1; y <= b[4]; y++)
                for (int z = b[2]; z <= b[5]; z++) {
                    String n = B.name(s.get(x, y, z));
                    if (!(n.equals("gravel") || n.endsWith("sand") || n.endsWith("concrete_powder") || n.contains("anvil"))) continue;
                    String below = s.get(x, y - 1, z);
                    if (B.isSolid(below) || B.name(below).equals("water")) continue;
                    if (fall++ < 5) System.out.println("  bloc qui tomberait : " + x + " " + y + " " + z + " " + n + " sur " + below);
                }
        if (fall > 0) { System.out.println("  " + fall + " bloc(s) soumis a la gravite sans appui"); ok = false; }
        for (int side : new int[]{-1, 1})
            for (int k = 0; k <= 2; k++)
                for (int zz = 10; zz <= 14; zz++)
                    if (!B.isAir(s.get(0, m.magicRel() + k, side * zz))) { System.out.println("  laine magique genee"); ok = false; }
        return ok;
    }

    /** Miroir x -> -x d'un etat : est/ouest echanges. */
    static String mirror(String st) {
        if (st.indexOf('[') < 0) return st;
        Map<String, String> p = B.props(st);
        Map<String, String> q = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : p.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            if (k.equals("east")) k = "west";
            else if (k.equals("west")) k = "east";
            if (k.equals("facing") || k.equals("horizontal_facing")) v = v.equals("east") ? "west" : v.equals("west") ? "east" : v;
            else if (k.equals("hinge")) v = v.equals("left") ? "right" : "left";
            else if (k.equals("rotation")) v = String.valueOf((16 - Integer.parseInt(v)) % 16);
            else if (k.equals("shape")) v = v.replace("left", "#").replace("right", "left").replace("#", "right")
                    .replace("east", "#").replace("west", "east").replace("#", "west");
            else if (k.equals("type") && (v.equals("left") || v.equals("right"))) v = v.equals("left") ? "right" : "left";
            q.put(k, v);
        }
        return B.build(B.base(st), q);
    }

    /** Ecrit SWM2 : gzip("SWM2", short W, H, L, int n, n x UTF, W*H*L x short, ordre y/z/x). */
    static void writeSwm(MapDef m, RecordingSink s, File f) throws IOException {
        int[] b = s.bounds();
        int x0 = b[0], y0 = 0, z0 = b[2];
        int W = b[3] - x0 + 1, H = b[4] + 1, L = b[5] - z0 + 1;
        if (b[1] < 0) throw new IllegalStateException("bloc sous y 0");
        List<String> pal = new ArrayList<>();
        Map<String, Integer> idx = new HashMap<>();
        pal.add("minecraft:air");
        idx.put("minecraft:air", 0);
        short[] grid = new short[W * H * L];
        for (int y = 0; y < H; y++)
            for (int z = 0; z < L; z++)
                for (int x = 0; x < W; x++) {
                    String st = s.get(x0 + x, y0 + y, z0 + z);
                    if (B.isAir(st)) continue;
                    Integer i = idx.get(st);
                    if (i == null) { i = pal.size(); pal.add(st); idx.put(st, i); }
                    grid[(y * L + z) * W + x] = (short) (int) i;
                }
        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
            o.writeBytes("SWM2");
            o.writeShort(W);
            o.writeShort(H);
            o.writeShort(L);
            o.writeInt(pal.size());
            for (String p : pal) o.writeUTF(p);
            for (short v : grid) o.writeShort(v);
        }
        int mid = -x0, zc = -z0;
        StringBuilder red = new StringBuilder(), blue = new StringBuilder();
        for (int[] p : SPAWNS) {
            red.append(red.length() == 0 ? "" : ", ").append("{").append(-p[0] + mid).append(", ").append(m.spawnFloor()).append(", ").append(p[1] + zc).append("}");
            blue.append(blue.length() == 0 ? "" : ", ").append("{").append(p[0] + mid).append(", ").append(m.spawnFloor()).append(", ").append(p[1] + zc).append("}");
        }
        System.out.println("IMPORTED.put(\"" + m.id() + "\", new Imported(\"" + m.name() + "\", 0, 0, 0, " + mid + ", " + zc
                + ", false, " + m.voidRel() + ", " + m.magicRel() + ", new int[][]{" + red + "}, new int[][]{" + blue + "}));");
        System.out.println("PARAMS " + m.id() + " {\"name\": \"" + m.name() + "\", \"ox\": 0, \"oy\": 0, \"oz\": 0, \"mid\": " + mid + ", \"zc\": " + zc
                + ", \"rotate\": false, \"voidRel\": " + m.voidRel() + ", \"magicRel\": " + m.magicRel()
                + ", \"red\": [" + red.toString().replace('{', '[').replace('}', ']') + "]"
                + ", \"blue\": [" + blue.toString().replace('{', '[').replace('}', ']') + "]}");
    }

    /** Gen qui ecrit dans le camp Bleu (x = +u) : sert au decor propre au camp Bleu. */
    static final class MirrorGen extends Gen {
        MirrorGen(fr.eemicraft.sheepwars.gen.build.Sink s, long seed) { super(s, seed); }
        @Override void set(int u, int y, int z, String st) {
            if (u < 0) throw new IllegalArgumentException("u < 0");
            s.set(u, y, z, mirror(B.id(st)).replace("TEAM", "blue"));
        }
        @Override String get(int u, int y, int z) { return s.get(u, y, z); }
    }
}
