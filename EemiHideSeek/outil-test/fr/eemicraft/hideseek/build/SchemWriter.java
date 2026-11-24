package fr.eemicraft.hideseek.build;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Écrit un RecordingSink en schematic Sponge v2 (.schem, NBT gzip) : Version 2, DataVersion, Width/Height/Length,
 * Offset, PaletteMax, Palette (état → index), BlockData (varints, ordre (y·Length + z)·Width + x).
 * Lisible par outils/analyze.py du skill et par WorldEdit/FAWE.
 */
public final class SchemWriter {
    private SchemWriter() {}

    /**
     * DataVersion écrite par défaut. 4671 = 1.21.11 (vu dans les maps du serveur) ; Paper 26.3 a une version plus
     * récente que cette bibliothèque ne connaît pas : WorldEdit mettra simplement les états à jour au chargement.
     */
    public static int DATA_VERSION = 4671;

    public static void write(RecordingSink r, File f) throws IOException { write(r, f, DATA_VERSION); }

    public static void write(RecordingSink r, File f, int dataVersion) throws IOException {
        if (r.isEmpty()) throw new IOException("Rien à écrire");
        int[] b = r.bounds();
        int W = b[3] - b[0] + 1, H = b[4] - b[1] + 1, L = b[5] - b[2] + 1;
        if (W > 65535 || H > 65535 || L > 65535) throw new IOException("Schematic trop grand");
        Map<String, Integer> pal = new LinkedHashMap<>();
        pal.put(B.AIR, 0);
        int[] remap = new int[r.paletteSize()];
        for (int i = 0; i < r.paletteSize(); i++) {
            String st = r.state(i);
            if (B.isAir(st)) { remap[i] = 0; continue; }
            Integer v = pal.get(st);
            if (v == null) { v = pal.size(); pal.put(st, v); }
            remap[i] = v;
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream(W * H * L);
        for (int y = 0; y < H; y++)
            for (int z = 0; z < L; z++)
                for (int x = 0; x < W; x++) {
                    int v = remap[r.id(b[0] + x, b[1] + y, b[2] + z)];
                    while ((v & ~0x7F) != 0) { data.write((v & 0x7F) | 0x80); v >>>= 7; }
                    data.write(v);
                }
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))))) {
            o.writeByte(10); o.writeUTF("Schematic");
            tagInt(o, "Version", 2);
            tagInt(o, "DataVersion", dataVersion);
            tagShort(o, "Width", W);
            tagShort(o, "Height", H);
            tagShort(o, "Length", L);
            o.writeByte(11); o.writeUTF("Offset"); o.writeInt(3); o.writeInt(b[0]); o.writeInt(b[1]); o.writeInt(b[2]);
            tagInt(o, "PaletteMax", pal.size());
            o.writeByte(10); o.writeUTF("Palette");
            for (Map.Entry<String, Integer> e : pal.entrySet()) tagInt(o, e.getKey(), e.getValue());
            o.writeByte(0);
            byte[] bd = data.toByteArray();
            o.writeByte(7); o.writeUTF("BlockData"); o.writeInt(bd.length); o.write(bd);
            o.writeByte(10); o.writeUTF("Metadata");
            tagInt(o, "WEOffsetX", 0); tagInt(o, "WEOffsetY", 0); tagInt(o, "WEOffsetZ", 0);
            o.writeByte(0);
            o.writeByte(0);
        }
    }

    private static void tagInt(DataOutputStream o, String n, int v) throws IOException {
        o.writeByte(3); o.writeUTF(n); o.writeInt(v);
    }

    private static void tagShort(DataOutputStream o, String n, int v) throws IOException {
        o.writeByte(2); o.writeUTF(n); o.writeShort((short) v);
    }
}
