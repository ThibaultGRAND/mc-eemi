package fr.eemicraft.jump;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Test hors serveur (jamais dans le jar) : construit la Balade tranquille en memoire, controle, vue du dessus, export texte. */
public final class TestTranquille {
    public static void main(String[] a) throws Exception {
        Tranquille t = new Tranquille(Long.parseLong(a[0]), Integer.parseInt(a[1]));
        Map<Long, String> top = new HashMap<>();
        Map<Long, Integer> topY = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        StringBuilder dump = new StringBuilder();
        int[] n = {0};
        t.build((x, y, z, s) -> {
            n[0]++;
            count.merge(s, 1, Integer::sum);
            long k = Tranquille.key(x, z);
            if (!s.equals("water") || !topY.containsKey(k)) {
                if (topY.getOrDefault(k, -999) <= y) {
                    topY.put(k, y);
                    top.put(k, s);
                }
            }
            dump.append(x).append(' ').append(y).append(' ').append(z).append(' ').append(s).append('\n');
        });
        System.out.println("poses " + n[0] + ", etapes " + (t.points.size() - 1) + ", sauts " + t.jumps + ", vide max a plat " + t.maxFlatGap + ", vide max en montee " + t.maxUpGap);
        System.out.println("boite x " + t.minX + ".." + t.maxX + " z " + t.minZ + ".." + t.maxZ);
        System.out.println("problemes " + t.problems);
        System.out.println("types " + count.size() + " " + count);
        int W = t.maxX - t.minX + 1, L = t.maxZ - t.minZ + 1;
        if (true) { writeText(a[2], dump, t); return; }
        BufferedImage img = null;
        for (Map.Entry<Long, String> e : top.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = e.getKey().intValue();
            if (x < t.minX || x > t.maxX || z < t.minZ || z > t.maxZ) {
                continue;
            }
            int y = topY.get(e.getKey());
            int c = color(e.getValue());
            double k = 0.55 + 0.45 * Math.max(0, Math.min(1, (y - 55) / 25.0));
            int r = (int) (((c >> 16) & 255) * k), g = (int) (((c >> 8) & 255) * k), b = (int) ((c & 255) * k);
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    img.setRGB((x - t.minX) * 2 + i, (z - t.minZ) * 2 + j, (r << 16) | (g << 8) | b);
                }
            }
        }

        try (PrintWriter w = new PrintWriter(a[2] + ".txt")) {
            w.print(dump);
        }
        try (PrintWriter w = new PrintWriter(a[2] + "_points.txt")) {
            for (Tranquille.Point p : t.points) {
                w.println(p.x + " " + p.y + " " + p.z + " " + p.yaw + " " + p.failY);
            }
        }
    }

    static void writeText(String out, StringBuilder dump, Tranquille t) throws Exception {
        try (PrintWriter w = new PrintWriter(out + ".txt")) {
            w.print(dump);
        }
        try (PrintWriter w = new PrintWriter(out + "_points.txt")) {
            for (Tranquille.Point p : t.points) {
                w.println(p.x + " " + p.y + " " + p.z + " " + p.yaw + " " + p.failY);
            }
        }
    }

    static int color(String s) {
        if (s.startsWith("water")) return 0x2850dc;
        if (s.startsWith("sand")) return 0xdccf96;
        if (s.startsWith("grass")) return 0x5aa03c;
        if (s.contains("leaves")) return s.contains("cherry") ? 0xf0a0c8 : 0x2e7a28;
        if (s.contains("snow")) return 0xf4f8ff;
        if (s.contains("gold")) return 0xffe000;
        if (s.contains("plate")) return 0xffff00;
        int h = s.hashCode();
        return 0x404040 | (h & 0xbfbfbf);
    }
}
