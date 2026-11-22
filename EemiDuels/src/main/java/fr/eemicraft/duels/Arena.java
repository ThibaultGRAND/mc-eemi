package fr.eemicraft.duels;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Arènes construites par le plugin dans le monde des duels, toutes au même niveau (sol en y = FLOOR) :
 * duels en x = i × 150, z = 0 ; sumo en x = i × 150, z = 300 ; FFA centrée en 0, -300.
 */
final class Arena {

    enum Type { DUEL, SUMO, FFA }

    static final int FLOOR = 100;
    static final int DUEL_RADIUS = 20;
    static final int SUMO_RADIUS = 6;
    static final int FFA_RADIUS = 35;
    static final int WALL_HEIGHT = 25;

    final Type type;
    final int index;
    final World world;
    final int cx, cz;
    /** Partie en cours dans cette arène (null = libre). */
    Match match;

    Arena(Type type, int index, World world) {
        this.type = type;
        this.index = index;
        this.world = world;
        this.cx = index * 150;
        this.cz = switch (type) {
            case DUEL -> 0;
            case SUMO -> 300;
            case FFA -> -300;
        };
    }

    int radius() {
        return switch (type) {
            case DUEL -> DUEL_RADIUS;
            case SUMO -> SUMO_RADIUS;
            case FFA -> FFA_RADIUS;
        };
    }

    /** Point de départ du joueur 0 ou 1, face à l'adversaire. */
    Location spawn(int side) {
        int d = type == Type.SUMO ? 3 : 15;
        int sign = side == 0 ? -1 : 1;
        Location l = new Location(world, cx + sign * d + 0.5, FLOOR + 1, cz + 0.5);
        l.setYaw(side == 0 ? -90 : 90);
        return l;
    }

    Location center() {
        return new Location(world, cx + 0.5, FLOOR + 1, cz + 0.5);
    }

    /** Dans la boîte de l'arène (murs compris), sur le plan horizontal. */
    boolean contains(Location l) {
        if (!l.getWorld().equals(world)) return false;
        int r = radius() + 1;
        return Math.abs(l.getBlockX() - cx) <= r && Math.abs(l.getBlockZ() - cz) <= r;
    }

    /** Y en dessous duquel on a perdu (chute). */
    double fallY() {
        return type == Type.SUMO ? FLOOR - 3 : FLOOR - 10;
    }

    // ------------------------------------------------------------------ construction

    void build() {
        switch (type) {
            case DUEL -> buildBox(DUEL_RADIUS, false);
            case FFA -> buildBox(FFA_RADIUS, true);
            case SUMO -> buildSumo();
        }
    }

    /** Sol en briques de pierre variées, bordure en andésite polie, murs et plafond de barrières. */
    private void buildBox(int r, boolean pillars) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                int x = cx + dx, z = cz + dz;
                boolean edge = Math.abs(dx) == r + 1 || Math.abs(dz) == r + 1;
                for (int y = FLOOR - 1; y <= FLOOR + WALL_HEIGHT + 1; y++) set(x, y, z, Material.AIR);
                if (edge) {
                    set(x, FLOOR, z, Material.POLISHED_ANDESITE);
                    set(x, FLOOR + 1, z, Material.STONE_BRICK_WALL);
                    for (int y = FLOOR + 2; y <= FLOOR + WALL_HEIGHT; y++) set(x, y, z, Material.BARRIER);
                } else {
                    set(x, FLOOR - 1, z, Material.STONE);
                    set(x, FLOOR, z, floor(x, z, Math.abs(dx) == r || Math.abs(dz) == r));
                }
                set(x, FLOOR + WALL_HEIGHT + 1, z, Material.BARRIER);
            }
        }
        if (pillars) {
            // Couverts façon arène FFA : 8 piliers 3×3 de 5 de haut à 15 blocs du centre.
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                int px = cx + (int) Math.round(Math.cos(a) * 15), pz = cz + (int) Math.round(Math.sin(a) * 15);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        for (int y = FLOOR + 1; y <= FLOOR + 5; y++)
                            set(px + dx, y, pz + dz, y == FLOOR + 5 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
            }
        }
    }

    private static Material floor(int x, int z, boolean border) {
        if (border) return Material.POLISHED_ANDESITE;
        int h = Math.floorMod(x * 734287 + z * 912931, 10);
        return h < 6 ? Material.STONE_BRICKS : h < 8 ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
    }

    /** Disque de 13 blocs suspendu au-dessus du vide, sans mur (on gagne en faisant tomber l'autre). */
    private void buildSumo() {
        int r = SUMO_RADIUS;
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                for (int y = FLOOR - 2; y <= FLOOR + 4; y++) set(cx + dx, y, cz + dz, Material.AIR);
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r + 0.5) {
                    set(cx + dx, FLOOR, cz + dz, d >= r - 0.5 ? Material.RED_TERRACOTTA : Material.SMOOTH_SANDSTONE);
                    set(cx + dx, FLOOR - 1, cz + dz, Material.SANDSTONE);
                }
            }
        }
    }

    private void set(int x, int y, int z, Material m) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != m) b.setType(m, false);
    }

    /** Points d'apparition FFA : sol dégagé sur 2 blocs, loin des piliers. */
    List<Location> ffaSpawns() {
        List<Location> list = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI / 8 * i + Math.PI / 16;
            int x = cx + (int) Math.round(Math.cos(a) * 26), z = cz + (int) Math.round(Math.sin(a) * 26);
            Location l = new Location(world, x + 0.5, FLOOR + 1, z + 0.5);
            l.setYaw((float) Math.toDegrees(Math.atan2(x - cx, -(z - cz))));
            list.add(l);
        }
        return list;
    }
}
