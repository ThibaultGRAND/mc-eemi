import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Genere la partie « pack de ressources » d'EemiPaintball : textures 16x16 dessinees ici,
 * modeles 3D a elements (marqueur, bille, grenade), definitions d'objets (items/*.json)
 * et apercus PNG (rendu orthographique maison avec z-buffer).
 *
 * Lancer : java PackGen.java <dossier EemiPaintball>
 */
public class PackGen {

    // ------------------------------------------------------------------ modele

    record Rot(char axis, double angle, double ox, double oy, double oz) {}

    static final class El {
        final double[] f;
        final double[] t;
        final String tex;
        final Rot rot;

        El(double x1, double y1, double z1, double x2, double y2, double z2, String tex, Rot rot) {
            this.f = new double[]{x1, y1, z1};
            this.t = new double[]{x2, y2, z2};
            this.tex = tex;
            this.rot = rot;
        }
    }

    static El e(double x1, double y1, double z1, double x2, double y2, double z2, String tex) {
        return new El(x1, y1, z1, x2, y2, z2, tex, null);
    }

    static El e(double x1, double y1, double z1, double x2, double y2, double z2, String tex, char axis, double angle, double ox, double oy, double oz) {
        return new El(x1, y1, z1, x2, y2, z2, tex, new Rot(axis, angle, ox, oy, oz));
    }

    /** Marqueur : canon vers le nord (-z), crosse et bouteille vers le sud (+z). */
    static List<El> marker() {
        List<El> l = new ArrayList<>();
        // canon
        l.add(e(7.1, 11.1, -8, 8.9, 12.9, 6, "metal"));
        l.add(e(6.85, 10.85, -6.6, 9.15, 13.15, -5.6, "accent"));
        l.add(e(6.85, 10.85, -3.6, 9.15, 13.15, -3.0, "dark"));
        l.add(e(6.8, 10.8, -9.5, 9.2, 13.2, -8, "dark"));
        l.add(e(6.6, 10.6, 5, 9.4, 13.4, 7, "accent"));
        // corps
        l.add(e(6.5, 8.5, 6.5, 9.5, 14, 15.5, "body"));
        l.add(e(6.3, 11, 8, 9.7, 12, 14, "accent"));
        l.add(e(6.8, 9, 15.5, 9.2, 13.5, 16.5, "dark"));
        l.add(e(7.5, 14, 10, 8.5, 14.6, 15, "dark"));
        // col et reservoir a billes (loader)
        l.add(e(7.2, 14, 7.5, 8.8, 15.5, 9.5, "dark"));
        l.add(e(5.3, 15.5, 4.5, 10.7, 20.5, 13, "loader"));
        l.add(e(5.8, 16, 3.5, 10.2, 20, 4.5, "loader"));
        l.add(e(5.8, 16, 13, 10.2, 20, 14, "loader"));
        l.add(e(5.8, 20.5, 5.5, 10.2, 21.5, 12, "accent"));
        l.add(e(7.4, 21.5, 7.5, 8.6, 22, 10, "dark"));
        // cadre de detente, pontet, detente
        l.add(e(7, 7, 8.2, 9, 8.5, 13.5, "body"));
        l.add(e(7.5, 4.5, 8.2, 8.5, 5.3, 11.6, "dark"));
        l.add(e(7.5, 4.5, 8.2, 8.5, 7.2, 9, "dark"));
        l.add(e(7.6, 5.6, 9.6, 8.4, 7.2, 10.2, "metal", 'x', 22.5, 8, 7, 10));
        // poignee pistolet inclinee
        l.add(e(7, 2.6, 11.4, 9, 7.2, 13.8, "grip", 'x', -22.5, 8, 7.2, 12.6));
        // poignee avant verticale
        l.add(e(7.2, 6.4, 0.8, 8.8, 11.1, 2.8, "grip"));
        // ASA / regulateur et bouteille d'air (octogone = 2 boites)
        l.add(e(6.6, 3, 13.6, 9.4, 6.4, 14.8, "metal"));
        l.add(e(6, 2.5, 14.8, 10, 6.5, 23.5, "tank"));
        l.add(e(6.45, 2.95, 14.8, 9.55, 6.05, 23.5, "tank", 'z', 45, 8, 4.5, 19));
        l.add(e(5.6, 2.1, 20.5, 10.4, 6.9, 21.5, "accent"));
        l.add(e(6.15, 2.65, 20.5, 9.85, 6.35, 21.5, "accent", 'z', 45, 8, 4.5, 21));
        l.add(e(6.6, 3.1, 23.5, 9.4, 5.9, 24.2, "dark"));
        return l;
    }

    static List<El> ball() {
        List<El> l = new ArrayList<>();
        l.add(e(6.3, 6.3, 6.3, 9.7, 9.7, 9.7, "bille"));
        l.add(e(5.6, 6.9, 6.9, 10.4, 9.1, 9.1, "bille"));
        l.add(e(6.9, 5.6, 6.9, 9.1, 10.4, 9.1, "bille"));
        l.add(e(6.9, 6.9, 5.6, 9.1, 9.1, 10.4, "bille"));
        return l;
    }

    static List<El> grenade() {
        List<El> l = new ArrayList<>();
        l.add(e(5.5, 2, 5.5, 10.5, 10, 10.5, "corps"));
        l.add(e(6.1, 2, 6.1, 9.9, 10, 9.9, "corps", 'y', 45, 8, 6, 8));
        l.add(e(5.3, 4, 5.3, 10.7, 5, 10.7, "dark"));
        l.add(e(5.95, 4, 5.95, 10.05, 5, 10.05, "dark", 'y', 45, 8, 6, 8));
        l.add(e(5.7, 1.6, 5.7, 10.3, 2, 10.3, "dark"));
        l.add(e(6.5, 10, 6.5, 9.5, 11.5, 9.5, "metal"));
        l.add(e(7.25, 11.5, 7.25, 8.75, 13, 8.75, "metal"));
        // cuillere
        l.add(e(7.5, 6, 10.5, 8.5, 12.8, 11, "metal"));
        l.add(e(7.5, 12.3, 8.5, 8.5, 12.8, 11, "metal"));
        // goupille et anneau
        l.add(e(6.0, 12.4, 7.9, 7.3, 12.6, 8.1, "metal"));
        l.add(e(5.6, 11.2, 6.9, 6.0, 11.5, 9.1, "metal"));
        l.add(e(5.6, 13.5, 6.9, 6.0, 13.8, 9.1, "metal"));
        l.add(e(5.6, 11.2, 6.6, 6.0, 13.8, 6.9, "metal"));
        l.add(e(5.6, 11.2, 9.1, 6.0, 13.8, 9.4, "metal"));
        return l;
    }

    // ------------------------------------------------------------------ textures

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int shade(int c, double f) {
        return rgb((int) (((c >> 16) & 255) * f), (int) (((c >> 8) & 255) * f), (int) ((c & 255) * f));
    }

    static BufferedImage tex(int base, double noise, long seed) {
        BufferedImage im = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                im.setRGB(x, y, shade(base, 1 + (r.nextDouble() - 0.5) * noise));
            }
        }
        return im;
    }

    static final int RED = rgb(0xD8, 0x22, 0x22);
    static final int BLUE = rgb(0x1F, 0x5C, 0xE0);

    static Map<String, BufferedImage> textures() {
        Map<String, BufferedImage> m = new LinkedHashMap<>();
        // metal : acier bronze a canon, reflet en haut
        BufferedImage metal = tex(rgb(0x6E, 0x74, 0x7C), 0.10, 1);
        for (int x = 0; x < 16; x++) {
            metal.setRGB(x, 1, shade(rgb(0xB8, 0xBE, 0xC6), 1));
            metal.setRGB(x, 2, shade(rgb(0x96, 0x9C, 0xA4), 1));
            metal.setRGB(x, 14, shade(rgb(0x48, 0x4C, 0x52), 1));
        }
        m.put("metal", metal);
        m.put("dark", tex(rgb(0x22, 0x23, 0x26), 0.18, 2));
        // caoutchouc de poignee : stries horizontales
        BufferedImage grip = tex(rgb(0x2A, 0x2A, 0x2A), 0.12, 3);
        for (int y = 0; y < 16; y += 2) {
            for (int x = 0; x < 16; x++) {
                grip.setRGB(x, y, shade(rgb(0x16, 0x16, 0x16), 1));
            }
        }
        m.put("grip", grip);
        // bouteille d'air en carbone (damier tisse)
        BufferedImage tank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean a = ((x / 2) + (y / 2)) % 2 == 0;
                tank.setRGB(x, y, a ? rgb(0x30, 0x32, 0x36) : rgb(0x1A, 0x1B, 0x1E));
            }
        }
        m.put("tank", tank);
        for (int t = 0; t < 2; t++) {
            int col = t == 0 ? RED : BLUE;
            String id = t == 0 ? "rouge" : "bleu";
            // anodise de couleur, reflet
            BufferedImage acc = tex(col, 0.08, 10 + t);
            for (int x = 0; x < 16; x++) {
                acc.setRGB(x, 1, shade(col, 1.45));
                acc.setRGB(x, 15, shade(col, 0.6));
            }
            m.put("accent_" + id, acc);
            // corps noir usine avec decoupes a la couleur de l'equipe
            BufferedImage body = tex(rgb(0x1E, 0x1F, 0x22), 0.12, 20 + t);
            for (int x = 1; x < 15; x++) {
                body.setRGB(x, 4, shade(col, 1.0));
                body.setRGB(x, 11, shade(col, 0.85));
            }
            for (int x = 3; x < 13; x += 3) {
                body.setRGB(x, 7, shade(col, 1.2));
                body.setRGB(x + 1, 7, shade(col, 1.2));
                body.setRGB(x, 8, shade(col, 0.9));
                body.setRGB(x + 1, 8, shade(col, 0.9));
            }
            m.put("body_" + id, body);
            // loader en plastique fume de couleur, bandes de reflet et billes visibles
            BufferedImage loader = tex(shade(col, 0.75), 0.06, 30 + t);
            Random r = new Random(40 + t);
            for (int i = 0; i < 9; i++) {
                int bx = 1 + r.nextInt(13);
                int by = 3 + r.nextInt(11);
                loader.setRGB(bx, by, shade(col, 1.35));
                loader.setRGB(bx + 1, by, shade(col, 1.1));
                loader.setRGB(bx, by + 1, shade(col, 1.0));
            }
            for (int y = 0; y < 16; y++) {
                loader.setRGB(2, y, shade(col, 1.6));
                loader.setRGB(3, y, shade(col, 1.25));
            }
            for (int x = 0; x < 16; x++) {
                loader.setRGB(x, 0, shade(col, 0.5));
            }
            m.put("loader_" + id, loader);
            // bille brillante
            BufferedImage bille = tex(col, 0.05, 50 + t);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double d = Math.hypot(x - 4.5, y - 4.5);
                    if (d < 2.2) {
                        bille.setRGB(x, y, shade(col, 1.9));
                    } else if (d < 4) {
                        bille.setRGB(x, y, shade(col, 1.35));
                    } else if (x + y > 22) {
                        bille.setRGB(x, y, shade(col, 0.7));
                    }
                }
            }
            m.put("bille_" + id, bille);
            // corps de grenade : metal peint, rainures, etiquette blanche
            BufferedImage gr = tex(shade(col, 0.9), 0.1, 60 + t);
            for (int y = 0; y < 16; y += 4) {
                for (int x = 0; x < 16; x++) {
                    gr.setRGB(x, y, shade(col, 0.55));
                }
            }
            for (int y = 9; y <= 11; y++) {
                for (int x = 4; x <= 11; x++) {
                    gr.setRGB(x, y, y == 10 && x % 2 == 0 ? rgb(0x30, 0x30, 0x30) : rgb(0xEE, 0xEE, 0xEE));
                }
            }
            m.put("grenade_" + id, gr);
        }
        return m;
    }

    // ------------------------------------------------------------------ JSON

    static String num(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String arr(double... v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(num(v[i]));
        }
        return sb.append("]").toString();
    }

    static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    /** Taille (largeur, hauteur) d'une face. */
    static double[] faceSize(El el, String face) {
        double sx = el.t[0] - el.f[0];
        double sy = el.t[1] - el.f[1];
        double sz = el.t[2] - el.f[2];
        return switch (face) {
            case "north", "south" -> new double[]{sx, sy};
            case "east", "west" -> new double[]{sz, sy};
            default -> new double[]{sx, sz};
        };
    }

    static double[] uv(El el, String face, int idx) {
        double[] s = faceSize(el, face);
        double w = Math.min(16, Math.max(0.5, s[0]));
        double h = Math.min(16, Math.max(0.5, s[1]));
        double u0 = w >= 16 ? 0 : (idx * 3.0) % (16 - w);
        double v0 = h >= 16 ? 0 : (idx * 5.0) % (16 - h);
        u0 = Math.floor(u0 * 4) / 4;
        v0 = Math.floor(v0 * 4) / 4;
        return new double[]{u0, v0, u0 + w, v0 + h};
    }

    static String modelJson(List<El> els, Map<String, String> textures, String display) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"credit\": \"EEMICRAFT - EemiPaintball\",\n  \"texture_size\": [16, 16],\n  \"textures\": {\n");
        int i = 0;
        for (Map.Entry<String, String> t : textures.entrySet()) {
            sb.append("    \"").append(t.getKey()).append("\": \"").append(t.getValue()).append("\"").append(++i < textures.size() ? ",\n" : "\n");
        }
        sb.append("  },\n  \"elements\": [\n");
        for (int k = 0; k < els.size(); k++) {
            El el = els.get(k);
            sb.append("    {\n      \"from\": ").append(arr(el.f)).append(",\n      \"to\": ").append(arr(el.t)).append(",\n");
            if (el.rot != null) {
                sb.append("      \"rotation\": {\"angle\": ").append(num(el.rot.angle())).append(", \"axis\": \"").append(el.rot.axis())
                    .append("\", \"origin\": ").append(arr(el.rot.ox(), el.rot.oy(), el.rot.oz())).append("},\n");
            }
            sb.append("      \"faces\": {\n");
            for (int f = 0; f < FACES.length; f++) {
                sb.append("        \"").append(FACES[f]).append("\": {\"uv\": ").append(arr(uv(el, FACES[f], k)))
                    .append(", \"texture\": \"#").append(el.tex).append("\"}").append(f < FACES.length - 1 ? ",\n" : "\n");
            }
            sb.append("      }\n    }").append(k < els.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"gui_light\": \"front\",\n  \"display\": ").append(display).append("\n}\n");
        return sb.toString();
    }

    static String child(String parent, Map<String, String> textures) {
        StringBuilder sb = new StringBuilder("{\n  \"parent\": \"" + parent + "\",\n  \"textures\": {\n");
        int i = 0;
        for (Map.Entry<String, String> t : textures.entrySet()) {
            sb.append("    \"").append(t.getKey()).append("\": \"").append(t.getValue()).append("\"").append(++i < textures.size() ? ",\n" : "\n");
        }
        return sb.append("  }\n}\n").toString();
    }

    static String itemDef(String model) {
        return "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"" + model + "\"\n  }\n}\n";
    }

    static String disp(String ctx, double[] rot, double[] tr, double sc) {
        return "    \"" + ctx + "\": {\"rotation\": " + arr(rot) + ", \"translation\": " + arr(tr) + ", \"scale\": " + arr(sc, sc, sc) + "}";
    }

    static final double[] MARKER_GUI = {90, -70, 90};

    static String markerDisplay() {
        return "{\n" + String.join(",\n",
            disp("thirdperson_righthand", new double[]{90, 0, 0}, new double[]{0, 3.5, 2.5}, 0.75),
            disp("thirdperson_lefthand", new double[]{90, 0, 0}, new double[]{0, 3.5, 2.5}, 0.75),
            disp("firstperson_righthand", new double[]{0, 6, 0}, new double[]{0, 1.5, -1}, 0.6),
            disp("firstperson_lefthand", new double[]{0, -6, 0}, new double[]{0, 1.5, -1}, 0.6),
            disp("gui", MARKER_GUI, new double[]{0, 0, 0}, 0.52),
            disp("ground", new double[]{0, 0, 0}, new double[]{0, 2, 0}, 0.4),
            disp("fixed", new double[]{0, 90, 0}, new double[]{0, 0, 0}, 0.5),
            disp("head", new double[]{0, 0, 0}, new double[]{0, 6, 0}, 0.8)) + "\n  }";
    }

    static final double[] BALL_GUI = {30, 45, 0};

    static String ballDisplay() {
        return "{\n" + String.join(",\n",
            disp("thirdperson_righthand", new double[]{0, 0, 0}, new double[]{0, 2, 1}, 0.7),
            disp("thirdperson_lefthand", new double[]{0, 0, 0}, new double[]{0, 2, 1}, 0.7),
            disp("firstperson_righthand", new double[]{0, 0, 0}, new double[]{0, 2, 0}, 0.8),
            disp("firstperson_lefthand", new double[]{0, 0, 0}, new double[]{0, 2, 0}, 0.8),
            disp("gui", BALL_GUI, new double[]{0, 0, 0}, 2.2),
            disp("ground", new double[]{0, 0, 0}, new double[]{0, 0, 0}, 0.8),
            disp("fixed", new double[]{0, 0, 0}, new double[]{0, 0, 0}, 1.6)) + "\n  }";
    }

    static final double[] GRENADE_GUI = {25, -35, 0};

    static String grenadeDisplay() {
        return "{\n" + String.join(",\n",
            disp("thirdperson_righthand", new double[]{0, 0, 0}, new double[]{0, 1.5, 1.5}, 0.55),
            disp("thirdperson_lefthand", new double[]{0, 0, 0}, new double[]{0, 1.5, 1.5}, 0.55),
            disp("firstperson_righthand", new double[]{0, -20, 0}, new double[]{0, 2.5, 0}, 0.65),
            disp("firstperson_lefthand", new double[]{0, 20, 0}, new double[]{0, 2.5, 0}, 0.65),
            disp("gui", GRENADE_GUI, new double[]{0, -0.5, 0}, 1.35),
            disp("ground", new double[]{0, 0, 0}, new double[]{0, 1, 0}, 0.6),
            disp("fixed", new double[]{0, 180, 0}, new double[]{0, 0, 0}, 1.1)) + "\n  }";
    }

    // ------------------------------------------------------------------ rendu d'apercu

    static double[] rotX(double[] v, double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    static double[] rotY(double[] v, double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    static double[] rotZ(double[] v, double a) {
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new double[]{v[0] * c - v[1] * s, v[0] * s + v[1] * c, v[2]};
    }

    /** Rotation comme les « display » de Minecraft : rotationXYZ (Z applique d'abord, puis Y, puis X). */
    static double[] view(double[] v, double[] xyz) {
        double[] r = rotZ(v, Math.toRadians(xyz[2]));
        r = rotY(r, Math.toRadians(xyz[1]));
        return rotX(r, Math.toRadians(xyz[0]));
    }

    static double[] elemRot(double[] p, Rot rot) {
        if (rot == null) {
            return p;
        }
        double[] q = {p[0] - rot.ox(), p[1] - rot.oy(), p[2] - rot.oz()};
        double a = Math.toRadians(rot.angle());
        q = switch (rot.axis()) {
            case 'x' -> rotX(q, a);
            case 'y' -> rotY(q, a);
            default -> rotZ(q, a);
        };
        return new double[]{q[0] + rot.ox(), q[1] + rot.oy(), q[2] + rot.oz()};
    }

    /** Coins d'une face (haut-gauche, haut-droit, bas-droit, bas-gauche vus de l'exterieur), conventions UV de Minecraft. */
    static double[][] corners(El el, String face) {
        double x1 = el.f[0], y1 = el.f[1], z1 = el.f[2], x2 = el.t[0], y2 = el.t[1], z2 = el.t[2];
        return switch (face) {
            case "north" -> new double[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
            case "south" -> new double[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
            case "east" -> new double[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case "west" -> new double[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
            case "up" -> new double[][]{{x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            default -> new double[][]{{x1, y1, z2}, {x2, y1, z2}, {x2, y1, z1}, {x1, y1, z1}};
        };
    }

    static BufferedImage render(List<El> els, Map<String, BufferedImage> tx, double[] viewRot, int W, int H, String title) {
        // sommets dans l'espace de vue
        List<double[][]> quads = new ArrayList<>();
        List<double[]> uvs = new ArrayList<>();
        List<BufferedImage> qt = new ArrayList<>();
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (int k = 0; k < els.size(); k++) {
            El el = els.get(k);
            for (String f : FACES) {
                double[][] c = corners(el, f);
                double[][] v = new double[4][];
                for (int i = 0; i < 4; i++) {
                    double[] p = elemRot(c[i], el.rot);
                    p = new double[]{p[0] - 8, p[1] - 8, p[2] - 8};
                    v[i] = view(p, viewRot);
                    minX = Math.min(minX, v[i][0]);
                    maxX = Math.max(maxX, v[i][0]);
                    minY = Math.min(minY, v[i][1]);
                    maxY = Math.max(maxY, v[i][1]);
                }
                quads.add(v);
                uvs.add(uv(el, f, k));
                qt.add(tx.get(el.tex));
            }
        }
        double scale = Math.min((W - 80) / (maxX - minX), (H - 110) / (maxY - minY));
        double cx = W / 2.0 - (minX + maxX) / 2 * scale;
        double cy = (H + 30) / 2.0 + (minY + maxY) / 2 * scale;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xEE, 0xF2, 0xF6), 0, H, new Color(0xC9, 0xD3, 0xDD)));
        g.fillRect(0, 0, W, H);
        double[] zb = new double[W * H];
        java.util.Arrays.fill(zb, -1e9);
        double[] light = norm(new double[]{-0.35, 0.8, 0.55});
        for (int q = 0; q < quads.size(); q++) {
            double[][] v = quads.get(q);
            double[] n = norm(cross(sub(v[1], v[0]), sub(v[3], v[0])));
            double bright = 0.55 + 0.45 * Math.max(0, dot(n, light));
            if (n[2] < 0) {
                // face de dos : cachee par le z-buffer de toute facon, on la dessine plus sombre
                bright *= 0.8;
            }
            double[] u = uvs.get(q);
            double[][] st = {{u[0], u[1]}, {u[2], u[1]}, {u[2], u[3]}, {u[0], u[3]}};
            double[][] s = new double[4][];
            for (int i = 0; i < 4; i++) {
                s[i] = new double[]{cx + v[i][0] * scale, cy - v[i][1] * scale, v[i][2]};
            }
            tri(img, zb, s[0], s[1], s[2], st[0], st[1], st[2], qt.get(q), bright, W, H);
            tri(img, zb, s[0], s[2], s[3], st[0], st[2], st[3], qt.get(q), bright, W, H);
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x24, 0x2A, 0x33));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(title, 20, 34);
        g.setStroke(new BasicStroke(1));
        g.dispose();
        return img;
    }

    static void tri(BufferedImage img, double[] zb, double[] a, double[] b, double[] c, double[] ta, double[] tb, double[] tc,
                    BufferedImage t, double bright, int W, int H) {
        int x0 = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int x1 = (int) Math.min(W - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int y1 = (int) Math.min(H - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / area;
                double w1 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) {
                    continue;
                }
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                int i = y * W + x;
                if (z <= zb[i]) {
                    continue;
                }
                zb[i] = z;
                double u = w0 * ta[0] + w1 * tb[0] + w2 * tc[0];
                double v = w0 * ta[1] + w1 * tb[1] + w2 * tc[1];
                int tu = Math.max(0, Math.min(15, (int) Math.floor(u)));
                int tv = Math.max(0, Math.min(15, (int) Math.floor(v)));
                img.setRGB(x, y, shade(t.getRGB(tu, tv), bright));
            }
        }
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l == 0 ? a : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    // ------------------------------------------------------------------ ecriture

    static void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), s, StandardCharsets.UTF_8);
        System.out.println("  " + f);
    }

    static void png(File f, BufferedImage im) throws IOException {
        f.getParentFile().mkdirs();
        ImageIO.write(im, "png", f);
        System.out.println("  " + f);
    }

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : ".");
        File pack = new File(root, "pack/assets/eemicraft");
        File apercu = new File(root, "apercu");
        String ns = "eemicraft:item/paintball/";
        Map<String, BufferedImage> tx = textures();
        for (Map.Entry<String, BufferedImage> t : tx.entrySet()) {
            png(new File(pack, "textures/item/paintball/" + t.getKey() + ".png"), t.getValue());
        }
        // marqueur : modele de base (accents rouges) + une variante par equipe
        Map<String, String> mt = new LinkedHashMap<>();
        mt.put("metal", ns + "metal");
        mt.put("dark", ns + "dark");
        mt.put("grip", ns + "grip");
        mt.put("tank", ns + "tank");
        mt.put("body", ns + "body_rouge");
        mt.put("accent", ns + "accent_rouge");
        mt.put("loader", ns + "loader_rouge");
        mt.put("particle", ns + "body_rouge");
        write(new File(pack, "models/item/paintball/marqueur.json"), modelJson(marker(), mt, markerDisplay()));
        Map<String, String> gt = new LinkedHashMap<>();
        gt.put("metal", ns + "metal");
        gt.put("dark", ns + "dark");
        gt.put("corps", ns + "grenade_rouge");
        gt.put("particle", ns + "grenade_rouge");
        write(new File(pack, "models/item/paintball/grenade.json"), modelJson(grenade(), gt, grenadeDisplay()));
        Map<String, String> bt = new LinkedHashMap<>();
        bt.put("bille", ns + "bille_rouge");
        bt.put("particle", ns + "bille_rouge");
        write(new File(pack, "models/item/paintball/bille.json"), modelJson(ball(), bt, ballDisplay()));
        for (String id : new String[]{"rouge", "bleu"}) {
            Map<String, String> c = new LinkedHashMap<>();
            c.put("body", ns + "body_" + id);
            c.put("accent", ns + "accent_" + id);
            c.put("loader", ns + "loader_" + id);
            c.put("particle", ns + "body_" + id);
            write(new File(pack, "models/item/paintball/marqueur_" + id + ".json"), child(ns + "marqueur", c));
            Map<String, String> c2 = new LinkedHashMap<>();
            c2.put("corps", ns + "grenade_" + id);
            c2.put("particle", ns + "grenade_" + id);
            write(new File(pack, "models/item/paintball/grenade_" + id + ".json"), child(ns + "grenade", c2));
            Map<String, String> c3 = new LinkedHashMap<>();
            c3.put("bille", ns + "bille_" + id);
            c3.put("particle", ns + "bille_" + id);
            write(new File(pack, "models/item/paintball/bille_" + id + ".json"), child(ns + "bille", c3));
            for (String it : new String[]{"marqueur", "grenade", "bille"}) {
                write(new File(pack, "items/paintball/" + it + "_" + id + ".json"), itemDef(ns + it + "_" + id));
            }
        }
        // apercus
        for (String id : new String[]{"rouge", "bleu"}) {
            Map<String, BufferedImage> m = new LinkedHashMap<>();
            m.put("metal", tx.get("metal"));
            m.put("dark", tx.get("dark"));
            m.put("grip", tx.get("grip"));
            m.put("tank", tx.get("tank"));
            m.put("body", tx.get("body_" + id));
            m.put("accent", tx.get("accent_" + id));
            m.put("loader", tx.get("loader_" + id));
            m.put("corps", tx.get("grenade_" + id));
            m.put("bille", tx.get("bille_" + id));
            String nom = id.equals("rouge") ? "Rouge" : "Bleu";
            png(new File(apercu, "marqueur_" + id + "_cote.png"), render(marker(), m, new double[]{0, -90, 0}, 900, 560, "Marqueur " + nom + " - vue de cote"));
            png(new File(apercu, "marqueur_" + id + "_3-4.png"), render(marker(), m, new double[]{22, -125, 0}, 900, 560, "Marqueur " + nom + " - vue 3/4 avant"));
            png(new File(apercu, "marqueur_" + id + "_inventaire.png"), render(marker(), m, MARKER_GUI, 520, 420, "Marqueur " + nom + " - icone (gui)"));
            png(new File(apercu, "grenade_" + id + ".png"), render(grenade(), m, GRENADE_GUI, 480, 480, "Grenade de peinture " + nom));
            png(new File(apercu, "grenade_" + id + "_cote.png"), render(grenade(), m, new double[]{0, -90, 0}, 480, 480, "Grenade " + nom + " - cote"));
            png(new File(apercu, "bille_" + id + ".png"), render(ball(), m, BALL_GUI, 360, 360, "Bille " + nom));
        }
    }
}
