package fr.eemicraft.sheepwars.gen.build;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fabrique et lecture de chaînes d'état de bloc ("minecraft:id[clé=valeur,...]"). */
public final class B {
    private B() {}

    public static final String AIR = "minecraft:air";
    public static final String WATER = "minecraft:water";

    /** Ajoute "minecraft:" si absent. */
    public static String id(String s) {
        s = s.trim();
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    /** Identifiant sans propriétés. */
    public static String base(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    /** Nom court sans namespace ni propriétés ("oak_stairs"). */
    public static String name(String state) {
        String b = base(state);
        int i = b.indexOf(':');
        return i < 0 ? b : b.substring(i + 1);
    }

    public static boolean isAir(String s) {
        if (s == null) return true;
        String n = name(s);
        return n.equals("air") || n.equals("cave_air") || n.equals("void_air");
    }

    /**
     * Heuristique « bloc plein » à partir du nom (pas de serveur dans le cœur) : faux pour l'air, les liquides,
     * la végétation, les feuilles et tous les blocs de détail (escaliers, dalles, vitres, barrières…).
     */
    public static boolean isSolid(String s) {
        if (isAir(s)) return false;
        String n = name(s);
        if (n.equals("water") || n.equals("lava") || n.equals("light") || n.equals("barrier") || n.equals("snow")) return false;
        if (n.endsWith("_slab")) return "double".equals(prop(s, "type"));
        if (n.equals("grass_block") || n.equals("sea_lantern") || n.equals("jack_o_lantern") || n.equals("bedrock")
                || n.equals("dirt_path") || n.equals("farmland")) return true;
        String[] no = {"stairs", "pane", "fence", "_wall", "door", "trapdoor", "torch", "lantern", "carpet", "button",
                "pressure_plate", "sign", "banner", "chain", "iron_bars", "ladder", "vine", "leaves", "grass", "fern",
                "flower", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower", "lily",
                "sapling", "bush", "petals", "kelp", "seagrass", "cane", "bamboo", "rail", "candle", "head", "skull",
                "pot", "cobweb", "rod", "dripleaf", "roots", "mushroom", "wheat", "carrots", "potatoes", "beetroots",
                "lichen", "glow_lichen", "scaffolding", "bell", "campfire", "bed", "anvil", "cauldron", "hopper"};
        for (String k : no) {
            if (n.contains(k)) {
                if (k.equals("_wall") && !n.endsWith("_wall")) continue;
                if (k.equals("roots") && n.equals("muddy_mangrove_roots")) continue;
                if (k.equals("mushroom") && (n.endsWith("_block") || n.endsWith("stem"))) continue;
                return false;
            }
        }
        return true;
    }

    public static Map<String, String> props(String state) {
        Map<String, String> m = new LinkedHashMap<>();
        int i = state.indexOf('[');
        if (i < 0) return m;
        int j = state.lastIndexOf(']');
        if (j < i) j = state.length();
        for (String kv : state.substring(i + 1, j).split(",")) {
            int e = kv.indexOf('=');
            if (e > 0) m.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
        }
        return m;
    }

    public static String prop(String state, String key) {
        return props(state).get(key);
    }

    /** Remplace ou ajoute une propriété. */
    public static String with(String state, String key, String value) {
        Map<String, String> m = props(state);
        m.put(key, value);
        return build(base(state), m);
    }

    public static String build(String id, Map<String, String> m) {
        if (m.isEmpty()) return id(id);
        StringBuilder sb = new StringBuilder(id(id)).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }

    /** state("oak_stairs", "facing", "north", "half", "top") ; garde les propriétés déjà présentes dans l'id. */
    public static String state(String id, String... kv) {
        Map<String, String> m = props(id);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return build(base(id), m);
    }

    /** Escalier qui MONTE vers {@code facing} ; {@code top} = retourné (sous-face, corniche, appui). */
    public static String stairs(String id, Dir facing, boolean top) {
        return state(base(id), "facing", facing.id(), "half", top ? "top" : "bottom", "shape", "straight");
    }

    /** type = "bottom", "top" ou "double". */
    public static String slab(String id, String type) {
        return state(base(id), "type", type);
    }

    /** Bûche / pilier orienté : axis = 'x', 'y' ou 'z'. */
    public static String axis(String id, char axis) {
        return state(base(id), "axis", String.valueOf(axis));
    }

    /** Trappe. Ouverte, le panneau est contre la face OPPOSÉE à facing (volet : facing = côté extérieur). */
    public static String trapdoor(String id, Dir facing, boolean top, boolean open) {
        return state(base(id), "facing", facing.id(), "half", top ? "top" : "bottom", "open", String.valueOf(open));
    }

    public static String door(String id, Dir facing, boolean upper, boolean hingeRight) {
        return state(base(id), "facing", facing.id(), "half", upper ? "upper" : "lower",
                "hinge", hingeRight ? "right" : "left", "open", "false");
    }

    /** Vitre / barreaux avec connexions explicites (posées sans physique, elles restent ainsi). */
    public static String pane(String id, boolean n, boolean e, boolean s, boolean w) {
        return state(base(id), "north", String.valueOf(n), "east", String.valueOf(e),
                "south", String.valueOf(s), "west", String.valueOf(w));
    }

    /** Feuilles persistantes (ne se décomposent jamais). */
    public static String leaves(String id) {
        return state(base(id), "persistent", "true");
    }
}
