package fr.eemicraft.paintball.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Imite le monde Paper vu par BukkitSink (hors jar) : un etat pose est complete avec les valeurs par defaut de
 * toutes ses proprietes, dans l'ordre du jeu (comme Bukkit.createBlockData + BlockData.getAsString), et un etat
 * inconnu du blocks.json devient de l'air (comme le repli de BukkitSink). Sert a verifier qu'un builder se comporte
 * pareil quand get() renvoie l'etat complet.
 */
public final class CanonSink implements Sink {
    private static Map<String, Map<String, String>> defaults;
    private final RecordingSink r = new RecordingSink();
    private final Map<String, String> cache = new HashMap<>();
    public int invalid;

    public CanonSink(Path blocksJson) throws IOException {
        synchronized (CanonSink.class) {
            if (defaults == null) {
                defaults = new HashMap<>();
                Object root = new Audit.Json(new String(Files.readAllBytes(blocksJson), StandardCharsets.UTF_8)).value();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
                    Map<?, ?> def = (Map<?, ?>) e.getValue();
                    Map<String, String> d = new LinkedHashMap<>();
                    Map<?, ?> props = (Map<?, ?>) def.get("properties");
                    Map<?, ?> dflt = null;
                    for (Object st : (List<?>) def.get("states")) {
                        Map<?, ?> m = (Map<?, ?>) st;
                        if ("true".equals(String.valueOf(m.get("default")))) dflt = (Map<?, ?>) m.get("properties");
                    }
                    if (props != null && dflt != null)
                        for (Object k : props.keySet()) d.put(String.valueOf(k), String.valueOf(dflt.get(k)));
                    defaults.put(String.valueOf(e.getKey()), d);
                }
            }
        }
    }

    public String canon(String st) {
        if (st == null) return B.AIR;
        String c = cache.get(st);
        if (c != null) return c;
        String id = B.id(B.base(st));
        Map<String, String> d = defaults.get(id);
        if (d == null) { invalid++; c = B.AIR; }
        else {
            Map<String, String> m = new LinkedHashMap<>(d);
            boolean ok = true;
            for (Map.Entry<String, String> e : B.props(st).entrySet()) {
                if (!m.containsKey(e.getKey())) { ok = false; break; }
                m.put(e.getKey(), e.getValue());
            }
            if (!ok) { invalid++; c = B.AIR; }
            else {
                StringBuilder b = new StringBuilder(id);
                if (!m.isEmpty()) {
                    b.append('[');
                    boolean first = true;
                    for (Map.Entry<String, String> e : m.entrySet()) {
                        if (!first) b.append(',');
                        b.append(e.getKey()).append('=').append(e.getValue());
                        first = false;
                    }
                    b.append(']');
                }
                c = b.toString();
            }
        }
        cache.put(st, c);
        return c;
    }

    @Override public void set(int x, int y, int z, String state) { r.set(x, y, z, canon(state)); }

    @Override public String get(int x, int y, int z) { return r.get(x, y, z); }

    public RecordingSink rec() { return r; }
}
