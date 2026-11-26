package fr.eemicraft.fete;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hors jar : écrit FetePack.zip, le pack SÉPARÉ de la fête (namespace fete:, chargé seulement dans le monde fete,
 * comme RefugePack). Modèles de {@link Models} : une texture atlas 64x64 (cases de 8x8 couleurs unies légèrement
 * bruitées), un modèle d'objet par entrée, teinte « dye » sur les éléments teints.
 * Usage : java ... GenPack <sortie.zip>
 */
public final class GenPack {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "FetePack.zip");
        // couleurs -> cases de l'atlas (0xFFFFFF = case blanche des éléments teints)
        Map<Integer, Integer> cell = new LinkedHashMap<>();
        cell.put(0xFFFFFF, 0);
        for (List<Models.El> l : Models.MODELS.values()) for (Models.El e : l) if (!e.tint()) cell.putIfAbsent(e.color(), cell.size());
        if (cell.size() > 64) throw new IllegalStateException("trop de couleurs : " + cell.size());
        int[] atlas = new int[64 * 64];
        java.util.Random rnd = new java.util.Random(7);
        for (var en : cell.entrySet()) {
            int c = en.getKey(), k = en.getValue(), cx = (k % 8) * 8, cy = (k / 8) * 8;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int n = rnd.nextInt(9) - 4 - (x == 0 || y == 0 || x == 7 || y == 7 ? 6 : 0);
                    int r = clamp(((c >> 16) & 255) + n), g = clamp(((c >> 8) & 255) + n), b = clamp((c & 255) + n);
                    atlas[(cy + y) * 64 + cx + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
        Map<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", ("{\n  \"pack\": {\n    \"description\": \"EEMICRAFT - Fete foraine\",\n    \"pack_format\": 88,\n"
                + "    \"min_format\": [84, 0],\n    \"max_format\": [200, 0]\n  }\n}\n").getBytes("UTF-8"));
        files.put("assets/fete/textures/item/atlas.png", png(atlas, 64, 64));
        for (var en : Models.MODELS.entrySet()) {
            String id = en.getKey();
            StringBuilder m = new StringBuilder("{\n  \"textures\": {\"0\": \"fete:item/atlas\", \"particle\": \"fete:item/atlas\"},\n  \"elements\": [\n");
            List<String> els = new ArrayList<>();
            for (Models.El e : en.getValue()) {
                for (double v : new double[]{e.x1(), e.y1(), e.z1(), e.x2(), e.y2(), e.z2()}) {
                    if (v < -16 || v > 32) throw new IllegalStateException(id + " : coordonnee hors limites " + v);
                }
                int k = cell.get(e.tint() ? 0xFFFFFF : e.color());
                double u = (k % 8) * 2 + 0.25, w = (k / 8) * 2 + 0.25;
                String uv = String.format(Locale.ROOT, "[%.2f, %.2f, %.2f, %.2f]", u, w, u + 1.5, w + 1.5);
                StringBuilder faces = new StringBuilder();
                for (String f : new String[]{"north", "east", "south", "west", "up", "down"}) {
                    if (faces.length() > 0) faces.append(", ");
                    faces.append('"').append(f).append("\": {\"uv\": ").append(uv).append(", \"texture\": \"#0\"")
                            .append(e.tint() ? ", \"tintindex\": 0" : "").append('}');
                }
                String rot = e.axis() == 'n' ? "" : String.format(Locale.ROOT,
                        ", \"rotation\": {\"angle\": %s, \"axis\": \"%c\", \"origin\": [%s, %s, %s]}", num(e.angle()), e.axis(), num(e.ox()), num(e.oy()), num(e.oz()));
                els.add(String.format(Locale.ROOT, "    {\"from\": [%s, %s, %s], \"to\": [%s, %s, %s]%s, \"faces\": {%s}}",
                        num(e.x1()), num(e.y1()), num(e.z1()), num(e.x2()), num(e.y2()), num(e.z2()), rot, faces));
            }
            m.append(String.join(",\n", els)).append("\n  ]\n}\n");
            files.put("assets/fete/models/item/" + id + ".json", m.toString().getBytes("UTF-8"));
            String tints = Models.tinted(id) ? ", \"tints\": [{\"type\": \"minecraft:dye\", \"default\": -1}]" : "";
            files.put("assets/fete/items/" + id + ".json", ("{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"fete:item/" + id + "\"" + tints + "}\n}\n").getBytes("UTF-8"));
        }
        ByteArrayOutputStream zb = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zb)) {
            for (var en : files.entrySet()) {
                ZipEntry ze = new ZipEntry(en.getKey());
                ze.setTime(1767225600000L);
                z.putNextEntry(ze);
                z.write(en.getValue());
                z.closeEntry();
            }
        }
        try (FileOutputStream fo = new FileOutputStream(out)) {
            fo.write(zb.toByteArray());
        }
        byte[] h = MessageDigest.getInstance("SHA-1").digest(zb.toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        System.out.println(out + " : " + files.size() + " fichiers, " + cell.size() + " couleurs, " + zb.size() + " octets, SHA-1 " + sb);
    }

    /** PNG RGBA minimal (sans AWT : le JDK des outils n'a pas les bibliothèques graphiques). */
    static byte[] png(int[] px, int w, int h) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                raw.write((c >> 16) & 255);
                raw.write((c >> 8) & 255);
                raw.write(c & 255);
                raw.write((c >>> 24) & 255);
            }
        }
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(z)) {
            d.write(raw.toByteArray());
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        java.nio.ByteBuffer ih = java.nio.ByteBuffer.allocate(13);
        ih.putInt(w).putInt(h).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0);
        chunk(o, "IHDR", ih.array());
        chunk(o, "IDAT", z.toByteArray());
        chunk(o, "IEND", new byte[0]);
        return o.toByteArray();
    }

    static void chunk(ByteArrayOutputStream o, String type, byte[] data) throws Exception {
        java.nio.ByteBuffer len = java.nio.ByteBuffer.allocate(4).putInt(data.length);
        o.write(len.array());
        byte[] t = type.getBytes("US-ASCII");
        o.write(t);
        o.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(t);
        crc.update(data);
        o.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }
}
