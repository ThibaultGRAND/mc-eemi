package fr.eemicraft.deacoudre.test;

import fr.eemicraft.deacoudre.Builder;
import fr.eemicraft.deacoudre.build.B;
import fr.eemicraft.deacoudre.build.RecordingSink;
import java.io.FileOutputStream;
import java.io.File;
import java.util.Map;


/** Elevations (vues de face et de cote, ombrees par la profondeur) : Vue <dossier> [rayon hauteur]. */
public final class Vue {
    static final Map<String, Integer> C = Map.ofEntries(
            Map.entry("stone", 0x7d7d7d), Map.entry("andesite", 0x888c8c), Map.entry("tuff", 0x6c6d66), Map.entry("calcite", 0xe0e1dd),
            Map.entry("grass_block", 0x6b9a3c), Map.entry("dirt", 0x86603e), Map.entry("sand", 0xdbcfa0), Map.entry("sandstone", 0xd8cb94),
            Map.entry("cut_sandstone", 0xd9cd96), Map.entry("smooth_quartz", 0xece6df), Map.entry("white_terracotta", 0xd1b2a1),
            Map.entry("water", 0x3a5fd0), Map.entry("glass", 0xc8e8f0), Map.entry("jungle_leaves", 0x3c8a1e), Map.entry("jungle_log", 0x56441e),
            Map.entry("prismarine_bricks", 0x63ab9e), Map.entry("dark_prismarine", 0x335b4b), Map.entry("dark_oak_log", 0x3c2e1a),
            Map.entry("stripped_spruce_log", 0x73593a), Map.entry("dark_oak_planks", 0x42301a), Map.entry("lantern", 0xf2c45a),
            Map.entry("sea_lantern", 0xb8d9d0), Map.entry("moss_block", 0x597e2d), Map.entry("white_concrete", 0xcfd5d6),
            Map.entry("yellow_concrete", 0xf0af15), Map.entry("light_blue_stained_glass", 0x6b9ad8));

    static int color(String st) {
        String n = B.name(st);
        Integer c = C.get(n);
        if (c != null) return c;
        for (String k : C.keySet()) if (n.startsWith(k) || n.endsWith(k)) return C.get(k);
        if (n.contains("prismarine")) return n.contains("dark") ? 0x335b4b : 0x63ab9e;
        if (n.contains("dark_oak")) return 0x42301a;
        if (n.contains("spruce")) return 0x73593a;
        if (n.contains("sandstone")) return 0xd8cb94;
        if (n.contains("birch")) return 0xd7c185;
        if (n.contains("wool") || n.contains("carpet")) return n.contains("red") ? 0xa02722 : n.contains("yellow") ? 0xf8c527 : n.contains("blue") ? 0x3aafd9 : 0xe9ecec;
        return 0x5aa03a;
    }

    public static void main(String[] a) throws Exception {
        int r = a.length > 1 ? Integer.parseInt(a[1]) : 4, h = a.length > 2 ? Integer.parseInt(a[2]) : 40;
        RecordingSink s = new RecordingSink();
        Builder.build(s, r, 100 + h);
        int[] b = s.bounds();
        int sc = 5;
        for (int view = 0; view < 2; view++) {
            int W = (view == 0 ? b[3] - b[0] : b[5] - b[2]) + 1, H = b[4] - b[1] + 1;
            byte[] img = new byte[W * sc * H * sc * 3];
            for (int u = 0; u < W; u++)
                for (int yy = 0; yy < H; yy++) {
                    int y = b[4] - yy;
                    int col = 0x9cc4ec, depth = 0;
                    // vue 0 : depuis le sud (on regarde vers -z) ; vue 1 : depuis l'est (on regarde vers -x)
                    int n = view == 0 ? b[5] - b[2] + 1 : b[3] - b[0] + 1;
                    for (int k = 0; k < n; k++) {
                        int x = view == 0 ? b[0] + u : b[3] - k, z = view == 0 ? b[5] - k : b[5] - u;
                        String st = s.get(x, y, z);
                        if (!B.isAir(st)) { col = color(st); depth = k; break; }
                    }
                    double f = 1.0 - Math.min(0.55, depth / 110.0);
                    int rr = (int) (((col >> 16) & 255) * f), gg = (int) (((col >> 8) & 255) * f), bb = (int) ((col & 255) * f);
                    int px = (rr << 16) | (gg << 8) | bb;
                    for (int i = 0; i < sc; i++) for (int j = 0; j < sc; j++) { int o = ((yy * sc + j) * W * sc + u * sc + i) * 3; img[o] = (byte) rr; img[o + 1] = (byte) gg; img[o + 2] = (byte) bb; }
                }
            File f = new File(a[0], "dac_vue_" + (view == 0 ? "sud" : "est") + ".ppm");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(("P6\n" + W * sc + " " + H * sc + "\n255\n").getBytes()); o.write(img); }
            System.out.println("Ecrit " + f.getAbsolutePath());
        }
    }
}
