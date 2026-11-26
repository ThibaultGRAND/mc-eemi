package fr.eemicraft.fete;

import fr.eemicraft.fete.build.Sink;

/**
 * Geometrie de la rue et des parcelles (aucune dependance Bukkit : servie aussi au test hors serveur).
 *
 * <p>Repere LOCAL d'une parcelle : u = 0..W-1 le long de la rue (vers l'est pour une parcelle sud), v = 0..D-1 en
 * s'eloignant de la rue (la rue est du cote v &lt; 0, le « nord local »). Sol du lot en y = FLOOR (on se tient en
 * FLOOR + 1).
 * <ul>
 * <li>parcelle cote sud (index pair) : monde = (px + u, y, 9 + v) — identite ;</li>
 * <li>parcelle cote nord (index impair) : monde = (px + W-1-u, y, -9 - v) — rotation de 180 degres
 *     (jamais un miroir : escaliers, portes et charnieres restent justes).</li>
 * </ul>
 */
public final class Geo {
    public static final int W = 48;          // largeur d un parc (u)
    public static final int D = 48;          // profondeur (v)
    public static final int GAP = 6;         // bande entre deux parcs (haie, lampadaires)
    public static final int PITCH = W + GAP;
    public static final int PLAZA = 48;      // place centrale
    public static final int EDGE = 12;       // |z| du premier rang du parc (allee de 23 de large)
    public static final int ROAD = 63;       // dessus de la chaussee (on marche en 64)
    public static final int FLOOR = 64;      // dessus des trottoirs et des lots (on marche en 65)
    public static final int TOP = 110;       // plus haut bloc d un parc (grande roue, tour de chute)

    public final int parcels, cols, length, xStart, plazaX0;

    public Geo(int parcels) {
        this.parcels = Math.max(2, Math.min(20, parcels + (parcels & 1)));
        this.cols = this.parcels / 2;
        this.length = cols * PITCH + PLAZA;
        this.xStart = -length / 2;
        this.plazaX0 = xStart + (cols / 2) * PITCH;
    }

    /** x monde du coin u = 0 (avant rotation) de la colonne. */
    public int colX(int col) {
        return xStart + col * PITCH + (col >= cols / 2 ? PLAZA : 0) + 1;
    }

    public boolean north(int parcel) {
        return (parcel & 1) == 1;
    }

    public int wx(int parcel, int u, int v) {
        int px = colX(parcel / 2);
        return north(parcel) ? px + W - 1 - u : px + u;
    }

    public int wz(int parcel, int u, int v) {
        return north(parcel) ? -EDGE - v : EDGE + v;
    }

    /** Coordonnees monde (centre de case) d'un point local non entier. */
    public double wxd(int parcel, double u, double v) {
        int px = colX(parcel / 2);
        return north(parcel) ? px + W - u : px + u;
    }

    public double wzd(int parcel, double u, double v) {
        return north(parcel) ? -EDGE + 1 - v : EDGE + v;
    }

    /** Yaw monde d'un regard local vers +v (« sud local », vers le fond de la parcelle). */
    public float yawInto(int parcel) {
        return north(parcel) ? 180f : 0f;
    }

    /** Parcelle d'un bloc monde, ou -1. */
    public int parcelAt(int x, int z) {
        boolean n = z <= -EDGE;
        if (!n && z < EDGE) return -1;
        int v = n ? -EDGE - z : z - EDGE;
        if (v < 0 || v >= D) return -1;
        for (int col = 0; col < cols; col++) {
            int px = colX(col);
            if (x >= px && x < px + W) return col * 2 + (n ? 1 : 0);
        }
        return -1;
    }

    /** {u, v} local d'un bloc monde dans la parcelle donnee. */
    public int[] local(int parcel, int x, int z) {
        int px = colX(parcel / 2);
        if (north(parcel)) return new int[]{px + W - 1 - x, -EDGE - z};
        return new int[]{x - px, z - EDGE};
    }

    /** Boite monde {x1, z1, x2, z2} de la parcelle. */
    public int[] box(int parcel) {
        int ax = wx(parcel, 0, 0), bx = wx(parcel, W - 1, 0), az = wz(parcel, 0, 0), bz = wz(parcel, 0, D - 1);
        return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
    }

    /** Boite monde de toute la carte (terrain compris). */
    public int[] worldBox() {
        return new int[]{xStart - 40, -EDGE - D - 30, xStart + length + 40, EDGE + D + 30};
    }

    /** Sink dans le repere local de la parcelle (les etats sont tournes de 180 degres pour le cote nord). */
    public Sink local(Sink world, int parcel) {
        boolean n = north(parcel);
        return new Sink() {
            public void set(int u, int y, int v, String s) {
                world.set(wx(parcel, u, v), y, wz(parcel, u, v), n ? rot180(s) : s);
            }

            public String get(int u, int y, int v) {
                return world.get(wx(parcel, u, v), y, wz(parcel, u, v));
            }
        };
    }

    // ------------------------------------------------------------------ rotation des etats

    private static String swapDir(String d) {
        return switch (d) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> d;
        };
    }

    /** Etat de bloc tourne de 180 degres autour de Y. */
    public static String rot180(String s) {
        if (s == null) return null;
        int b = s.indexOf('[');
        if (b < 0) return s;
        String id = s.substring(0, b);
        String[] kv = s.substring(b + 1, s.length() - 1).split(",");
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String p : kv) {
            int e = p.indexOf('=');
            if (e > 0) m.put(p.substring(0, e), p.substring(e + 1));
        }
        java.util.Map<String, String> o = new java.util.LinkedHashMap<>();
        for (var en : m.entrySet()) {
            String k = en.getKey(), v = en.getValue();
            switch (k) {
                case "facing" -> o.put(k, swapDir(v));
                case "rotation" -> {
                    try {
                        o.put(k, Integer.toString((Integer.parseInt(v) + 8) % 16));
                    } catch (NumberFormatException ex) {
                        o.put(k, v);
                    }
                }
                case "north" -> o.put("south", v);
                case "south" -> o.put("north", v);
                case "east" -> o.put("west", v);
                case "west" -> o.put("east", v);
                default -> o.put(k, v);
            }
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (var en : o.entrySet()) {
            if (!first) sb.append(',');
            sb.append(en.getKey()).append('=').append(en.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
