package fr.eemicraft.resto.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sink en mémoire pour tester un builder hors serveur : sections 16³ d'index de palette (2 octets par case
 * touchée), tient plusieurs millions de blocs. Se lit avec {@link #get}, s'écrit en .schem avec
 * {@link SchemWriter}, se mesure avec {@link Check}.
 */
public final class RecordingSink implements Sink {
    private final Map<Long, short[]> sections = new HashMap<>();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    private long ops;

    public RecordingSink() {
        palette.add(B.AIR);
        index.put(B.AIR, 0);
    }

    private static long key(int x, int y, int z) {
        return (((long) (x >> 4) & 0x3FFFFFL) << 42) | (((long) (y >> 4) & 0xFFFFFL) << 22) | ((long) (z >> 4) & 0x3FFFFFL);
    }

    private static int local(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    @Override
    public void set(int x, int y, int z, String state) {
        ops++;
        if (state == null) state = B.AIR;
        int id;
        if (B.isAir(state)) id = 0;
        else {
            Integer i = index.get(state);
            if (i == null) {
                if (palette.size() >= Short.MAX_VALUE) throw new IllegalStateException("Plus de 32767 états différents");
                i = palette.size();
                palette.add(state);
                index.put(state, i);
            }
            id = i;
        }
        long k = key(x, y, z);
        short[] sec = sections.get(k);
        if (sec == null) {
            if (id == 0) return;
            sec = new short[4096];
            sections.put(k, sec);
        }
        sec[local(x, y, z)] = (short) id;
        if (id != 0) {
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
        }
    }

    /** Index de palette de la case (0 = air). */
    public int id(int x, int y, int z) {
        short[] sec = sections.get(key(x, y, z));
        return sec == null ? 0 : sec[local(x, y, z)];
    }

    @Override
    public String get(int x, int y, int z) {
        return palette.get(id(x, y, z));
    }

    public String state(int id) { return palette.get(id); }
    public int paletteSize() { return palette.size(); }
    public long operations() { return ops; }

    public boolean isEmpty() { return maxX == Integer.MIN_VALUE; }

    /** {minX, minY, minZ, maxX, maxY, maxZ} des blocs non-air posés (ne rétrécit pas si on efface). */
    public int[] bounds() {
        if (isEmpty()) return new int[]{0, 0, 0, -1, -1, -1};
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    /** Nombre de cases non-air dans les bornes. */
    public long count() {
        long n = 0;
        for (short[] sec : sections.values()) for (short v : sec) if (v != 0) n++;
        return n;
    }
}
