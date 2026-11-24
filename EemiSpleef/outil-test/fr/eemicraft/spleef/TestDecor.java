package fr.eemicraft.spleef;


import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


/** Hors serveur (non embarque) : dessine le decor, verifie les etats contre blocks.json, ecrit une vue du dessus et une coupe. */
public final class TestDecor {
    public static void main(String[] a) throws Exception {
        String json = Files.readString(new File(a[0]).toPath());
        File out = new File(a[1]);
        out.mkdirs();
        int bad = 0;
        for (int layers = 1; layers <= 4; layers++) {
            Builder b = Builder.build(layers, 7);
            Map<String, Integer> count = new TreeMap<>();
            for (String s : b.blocks().values()) {
                count.merge(s, 1, Integer::sum);
            }
            for (String s : count.keySet()) {
                String err = check(json, s);
                if (err != null) {
                    System.out.println("INVALIDE " + s + " : " + err);
                    bad++;
                }
            }
            // contrat : rien a moins de 5 blocs du bord du plus grand etage, au-dessus de la lave
            int lava = Builder.lavaY(layers, 7);
            int intrus = 0;
            for (long k : b.blocks().keySet()) {
                int x = Builder.kx(k), y = Builder.ky(k), z = Builder.kz(k);
                if (y > lava + 2 && y <= Builder.TOP + 3 && x * x + z * z < Math.pow(Builder.MAX_R + 3, 2)) {
                    intrus++;
                }
            }
            System.out.println("etages=" + layers + " blocs=" + b.blocks().size() + " etats=" + count.size() + " intrus=" + intrus);
            if (layers == 3) {
                Map<String, Integer> fam = new HashMap<>();
                for (Map.Entry<String, Integer> e : count.entrySet()) {
                    fam.merge(e.getKey().replaceAll("\\[.*", ""), e.getValue(), Integer::sum);
                }
                fam.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(30)
                        .forEach(e -> System.out.println("  " + e.getKey() + " " + e.getValue()));
                render(b, out, lava);
            }
        }
        System.out.println(bad == 0 ? "0 etat invalide" : bad + " etat(s) invalide(s)");
    }

    static String check(String json, String state) {
        String id = state.replaceAll("\\[.*", "");
        int i = json.indexOf("\"" + id + "\": {");
        if (i < 0) {
            return "id inconnu";
        }
        int end = json.indexOf("\"states\"", i);
        String props = json.substring(i, end);
        if (state.contains("[")) {
            String in = state.substring(state.indexOf('[') + 1, state.length() - 1);
            for (String kv : in.split(",")) {
                String[] p = kv.split("=");
                int k = props.indexOf("\"" + p[0] + "\": [");
                if (k < 0) {
                    return "cle " + p[0];
                }
                String vals = props.substring(k, props.indexOf(']', k));
                if (!vals.contains("\"" + p[1] + "\"")) {
                    return "valeur " + kv;
                }
            }
        }
        return null;
    }

    static int color(String s) {
        String id = s.replaceAll("\\[.*", "").replace("minecraft:", "");
        return switch (id) {
            case "lava" -> 0xE0601A;
            case "snow_block", "snow" -> 0xF4F8FA;
            case "packed_ice" -> 0x8DB3EB;
            case "blue_ice" -> 0x6FA2EE;
            case "blackstone", "polished_blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone", "polished_blackstone_brick_slab", "polished_blackstone_wall" -> 0x2C2630;
            case "basalt" -> 0x4A4A50;
            case "magma_block" -> 0x8A3A10;
            case "deepslate" -> 0x505058;
            case "tuff" -> 0x6C6D66;
            case "andesite" -> 0x888888;
            case "stone" -> 0x7D7D7D;
            case "calcite" -> 0xDCDDD8;
            case "diorite" -> 0xC4C4C4;
            case "spruce_leaves" -> 0x3D5E3A;
            case "spruce_log", "stripped_spruce_log", "spruce_planks", "spruce_stairs", "spruce_fence", "spruce_trapdoor" -> 0x6E5030;
            case "dark_oak_stairs", "dark_oak_slab", "dark_oak_planks" -> 0x3C2814;
            case "lantern" -> 0xFFD070;
            default -> 0xFF00FF;
        };
    }

    static void render(Builder b, File out, int lava) throws Exception {
        int A = Builder.AREA, S = 2 * A + 1;
        Img top = new Img(S * 4, S * 4);
        int[] h = new int[S * S];
        String[] col = new String[S * S];
        for (Map.Entry<Long, String> e : b.blocks().entrySet()) {
            int x = Builder.kx(e.getKey()) + A, y = Builder.ky(e.getKey()), z = Builder.kz(e.getKey()) + A;
            if (x < 0 || z < 0 || x >= S || z >= S) {
                continue;
            }
            int i = z * S + x;
            if (col[i] == null || y > h[i]) {
                h[i] = y;
                col[i] = e.getValue();
            }
        }
        // les etages (3) poses par le plugin, en couleur, pour juger la lisibilite
        int[] er = {14, 12, 10};
        int[] ec = {0xFFFFFF, 0x3AAFD9, 0x158991};
        for (int l = 2; l >= 0; l--) {
            for (int x = -er[l]; x <= er[l]; x++) {
                for (int z = -er[l]; z <= er[l]; z++) {
                    if (x * x + z * z <= er[l] * er[l] + er[l]) {
                        int i = (z + A) * S + x + A;
                        col[i] = "etage" + l;
                        h[i] = Builder.TOP - l * 7;
                    }
                }
            }
        }
        for (int z = 0; z < S; z++) {
            for (int x = 0; x < S; x++) {
                int i = z * S + x;
                int c = 0x101018;
                if (col[i] != null) {
                    c = col[i].startsWith("etage") ? ec[col[i].charAt(5) - '0'] : color(col[i]);
                    double sh = 0.6 + 0.4 * Math.max(0, Math.min(1, (h[i] - lava + 4) / 50.0));
                    c = (((int) (((c >> 16) & 255) * sh)) << 16) | (((int) (((c >> 8) & 255) * sh)) << 8) | (int) ((c & 255) * sh);
                }
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 4; dx++) {
                        top.setRGB(x * 4 + dx, z * 4 + dy, c);
                    }
                }
            }
        }
        top.write(new File(out, "spleef_dessus.ppm"));
        // coupe x = 0 (nord-sud, chalet a gauche)
        int H = Builder.WAIT_Y + 16 - (lava - 4);
        Img cut = new Img(S * 4, H * 4);
        for (int z = -A; z <= A; z++) {
            for (int y = lava - 4; y < Builder.WAIT_Y + 16; y++) {
                String s = b.blocks().get(Builder.key(0, y, z));
                int c = s == null ? 0x101018 : color(s);
                for (int l = 0; l < 3; l++) {
                    if (y == Builder.TOP - l * 7 && Math.abs(z) <= er[l]) {
                        c = ec[l];
                    }
                }
                int py = (Builder.WAIT_Y + 15 - y) * 4;
                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 4; dx++) {
                        cut.setRGB((z + A) * 4 + dx, py + dy, c);
                    }
                }
            }
        }
        cut.write(new File(out, "spleef_coupe.ppm"));
    }

    static final class Img {
        final int w, h;
        final int[] px;

        Img(int w, int h) {
            this.w = w;
            this.h = h;
            px = new int[w * h];
        }

        void setRGB(int x, int y, int c) {
            px[y * w + x] = c;
        }

        void write(File f) throws Exception {
            byte[] head = ("P6\n" + w + " " + h + "\n255\n").getBytes();
            byte[] b = new byte[head.length + w * h * 3];
            System.arraycopy(head, 0, b, 0, head.length);
            for (int i = 0; i < w * h; i++) {
                b[head.length + i * 3] = (byte) (px[i] >> 16);
                b[head.length + i * 3 + 1] = (byte) (px[i] >> 8);
                b[head.length + i * 3 + 2] = (byte) px[i];
            }
            Files.write(f.toPath(), b);
        }
    }
}