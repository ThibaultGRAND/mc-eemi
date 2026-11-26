package fr.eemicraft.fete;

import fr.eemicraft.fete.build.B;
import fr.eemicraft.fete.build.Sink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan de pose en memoire (sans Bukkit, rempli hors du fil principal) : contrairement au RecordingSink, l'air est
 * enregistre (0 = case non touchee), pour que la pose vide aussi ce qui doit l'etre.
 */
final class Plan implements Sink {
    private final Map<Long, short[]> sections = new HashMap<>();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> index = new HashMap<>();

    Plan() {
        palette.add(null); // 0 = non touche
    }

    private static long sec(int x, int y, int z) {
        return (((long) (x >> 4) & 0x3FFFFFL) << 42) | (((long) (y >> 4) & 0xFFFFFL) << 22) | ((long) (z >> 4) & 0x3FFFFFL);
    }

    private static int loc(int x, int y, int z) {
        return ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
    }

    @Override
    public void set(int x, int y, int z, String state) {
        if (y < -64 || y > 319) return;
        if (state == null || B.isAir(state)) state = B.AIR;
        Integer i = index.get(state);
        if (i == null) {
            i = palette.size();
            palette.add(state);
            index.put(state, i);
        }
        sections.computeIfAbsent(sec(x, y, z), k -> new short[4096])[loc(x, y, z)] = (short) (int) i;
    }

    @Override
    public String get(int x, int y, int z) {
        short[] s = sections.get(sec(x, y, z));
        int i = s == null ? 0 : s[loc(x, y, z)];
        return i == 0 ? B.AIR : palette.get(i);
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int ux(long p) {
        return (int) (p >> 38) << 6 >> 6;
    }

    static int uz(long p) {
        return (int) ((p >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    static int uy(long p) {
        return (int) (p & 0xFFF) << 20 >> 20;
    }

    /** Liste a poser : positions et etats. */
    record Compiled(long[] pos, String[] states, Map<Long, String> all) {
        int size() {
            return pos.length;
        }
    }

    /** Compile en ne gardant que ce qui differe de {@code prev} (null = tout). {@code all} = plan complet (pour le prochain diff). */
    Compiled compile(Map<Long, String> prev, boolean keep) {
        Map<Long, String> all = keep ? new HashMap<>() : null;
        List<Long> pos = new ArrayList<>();
        List<String> st = new ArrayList<>();
        for (var e : sections.entrySet()) {
            long k = e.getKey();
            int sx = (int) (k >> 42) << 10 >> 10, sy = (int) ((k >> 22) & 0xFFFFF) << 12 >> 12, sz = (int) (k & 0x3FFFFF) << 10 >> 10;
            short[] a = e.getValue();
            for (int i = 0; i < 4096; i++) {
                if (a[i] == 0) continue;
                int x = (sx << 4) | (i & 15), z = (sz << 4) | ((i >> 4) & 15), y = (sy << 4) | (i >> 8);
                long p = pack(x, y, z);
                String s = palette.get(a[i]);
                if (keep) all.put(p, s);
                if (prev != null && s.equals(prev.get(p))) continue;
                pos.add(p);
                st.add(s);
            }
        }
        long[] pa = new long[pos.size()];
        for (int i = 0; i < pa.length; i++) pa[i] = pos.get(i);
        return new Compiled(pa, st.toArray(new String[0]), all);
    }
}
