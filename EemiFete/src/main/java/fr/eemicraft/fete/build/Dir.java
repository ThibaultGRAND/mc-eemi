package fr.eemicraft.fete.build;

import java.util.Locale;

/** Directions horizontales (mêmes noms et même sens de rotation que Minecraft). */
public enum Dir {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

    public final int dx, dz;

    Dir(int dx, int dz) { this.dx = dx; this.dz = dz; }

    public Dir opposite() { return values()[(ordinal() + 2) & 3]; }
    /** Sens horaire vu du dessus : NORTH → EAST. */
    public Dir cw() { return values()[(ordinal() + 1) & 3]; }
    /** Sens anti-horaire : NORTH → WEST (= Direction.getCounterClockWise de Minecraft). */
    public Dir ccw() { return values()[(ordinal() + 3) & 3]; }
    /** "north", "east"… pour les propriétés d'état. */
    public String id() { return name().toLowerCase(Locale.ROOT); }
    /** Axe 'x' ou 'z'. */
    public char axis() { return dx != 0 ? 'x' : 'z'; }

    public static Dir of(String s) { return valueOf(s.toUpperCase(Locale.ROOT)); }

    public static Dir fromDelta(int dx, int dz) {
        if (dx > 0) return EAST;
        if (dx < 0) return WEST;
        if (dz > 0) return SOUTH;
        return NORTH;
    }
}
