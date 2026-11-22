package fr.eemicraft.horscours;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.BoundingBox;

/**
 * L'étage de l'EEMI, d'après les visuels de la page Notion « Pièces » (pas de plan à l'échelle :
 * dimensions approximatives). Sol (moquette) en y 64, plafond en y 68.
 *
 * Couloir est-ouest (z 15..19). Au nord : bureau du directeur, bureau du staff, local imprimante
 * (baie réseau murale), salle de réunion, salle de pause. Au sud : salle 01, salle 02, puis
 * l'espace détente (poufs, fauteuils, télé) et la porte de sortie de l'école.
 */
final class Prologue {
    static final int Y = 64;

    final World w;
    final List<int[]> lights = new ArrayList<>();
    final List<int[]> tvs = new ArrayList<>();
    final List<Object[]> labels = new ArrayList<>(); // {x, y, z, yaw, texte}

    final Location spawn;
    final Location lampPos, chalkPos;
    final int[] cafe = {49, 65, 1};
    final List<int[]> baie = new ArrayList<>();

    final BoundingBox corridor = new BoundingBox(1, Y, 15, 36, Y + 3, 20);
    final BoundingBox directeur = new BoundingBox(1, Y, 1, 10, Y + 3, 14);
    final BoundingBox imprimante = new BoundingBox(21, Y, 1, 28, Y + 3, 14);
    final BoundingBox noclip = new BoundingBox(22.7, Y, 1.9, 26.3, Y + 1.6, 2.6);

    Prologue(World w) {
        this.w = w;
        spawn = new Location(w, 44.5, Y, 17.5, 90, 0);
        lampPos = new Location(w, 30.5, Y + 1.05, 22.5);
        chalkPos = new Location(w, 6.5, Y + 1.05, 22.5);
    }

    void build() {
        Build b = new Build(w);
        lights.clear();
        tvs.clear();
        labels.clear();
        baie.clear();

        // Volume vidé, sol, plafond, murs extérieurs
        b.fill(-1, Y - 2, -1, 57, Y + 5, 35, Material.AIR);
        b.fill(0, Y - 1, 0, 56, Y - 1, 34, Material.SMOOTH_STONE);
        b.fill(1, Y, 1, 55, Y, 33, Material.GRAY_CARPET);
        b.fill(0, Y + 4, 0, 56, Y + 4, 34, Material.LIGHT_GRAY_CONCRETE);
        b.fill(0, Y, 0, 56, Y + 3, 0, Material.WHITE_CONCRETE);
        b.fill(0, Y, 34, 56, Y + 3, 34, Material.WHITE_CONCRETE);
        b.fill(0, Y, 0, 0, Y + 3, 34, Material.WHITE_CONCRETE);
        b.fill(56, Y, 0, 56, Y + 3, 34, Material.WHITE_CONCRETE);
        // Fenêtres (nuit noire derrière)
        for (int x = 2; x <= 54; x += 3) {
            if (x == 23 || x == 26) continue; // baie réseau
            b.pane(x, Y + 1, 0, Material.GRAY_STAINED_GLASS_PANE, true);
            b.pane(x, Y + 2, 0, Material.GRAY_STAINED_GLASS_PANE, true);
        }
        for (int x = 3; x <= 33; x += 3) {
            b.pane(x, Y + 1, 34, Material.GRAY_STAINED_GLASS_PANE, true);
            b.pane(x, Y + 2, 34, Material.GRAY_STAINED_GLASS_PANE, true);
        }

        // Murs vitrés du couloir (bas plein, vitrage à pois, haut plein)
        glassWall(b, 1, 55, 14);
        glassWall(b, 1, 35, 20);
        // Cloisons entre pièces
        for (int x : new int[] {10, 20, 28, 42}) b.fill(x, Y, 1, x, Y + 3, 14, Material.WHITE_CONCRETE);
        b.fill(18, Y, 20, 18, Y + 3, 33, Material.WHITE_CONCRETE);
        b.fill(36, Y, 20, 36, Y + 3, 33, Material.WHITE_CONCRETE);

        // Portes et étiquettes
        door(b, 5, 14, BlockFace.SOUTH, "#Directeur", 0);
        door(b, 15, 14, BlockFace.SOUTH, "#Staff", 0);
        door(b, 24, 14, BlockFace.SOUTH, "#Reprographie", 0);
        door(b, 35, 14, BlockFace.SOUTH, "#Reunion", 0);
        door(b, 49, 14, BlockFace.SOUTH, "#Cuisine", 0);
        door(b, 9, 20, BlockFace.NORTH, "Salle 01", 180);
        door(b, 27, 20, BlockFace.NORTH, "Salle 02", 180);
        // Porte de sortie de l'école (fermée)
        b.door(56, Y, 26, Material.IRON_DOOR, BlockFace.WEST, false);
        b.door(56, Y, 27, Material.IRON_DOOR, BlockFace.WEST, false);
        labels.add(new Object[] {55.9, Y + 2.3, 27.0, -90f, "§a§lSORTIE"});

        directeur(b);
        staff(b);
        imprimante(b);
        reunion(b);
        pause(b);
        salle(b, 1, 17, 7, chalkPos); // salle 01 : craie sur le bureau du prof
        salle(b, 19, 35, 31, lampPos); // salle 02 : lampe sur le bureau du prof
        lounge(b);

        // Néons du plafond
        for (int x = 4; x <= 52; x += 4) light(b, x, 17);
        for (int[] c : new int[][] {{5, 7}, {15, 7}, {24, 7}, {32, 5}, {38, 9}, {46, 5}, {52, 10},
                {5, 25}, {13, 30}, {23, 25}, {31, 30}, {41, 24}, {50, 29}, {44, 31}})
            light(b, c[0], c[1]);
    }

    /** Remet les néons (true) ou les coupe (false). */
    void setLights(boolean on) {
        for (int[] l : lights)
            w.getBlockAt(l[0], l[1], l[2]).setType(on ? Material.SEA_LANTERN : Material.WHITE_CONCRETE, false);
    }

    private void light(Build b, int x, int z) {
        b.set(x, Y + 4, z, Material.SEA_LANTERN);
        lights.add(new int[] {x, Y + 4, z});
    }

    private void glassWall(Build b, int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, Y, z, Material.WHITE_CONCRETE);
            b.pane(x, Y + 1, z, Material.WHITE_STAINED_GLASS_PANE, true);
            b.pane(x, Y + 2, z, Material.WHITE_STAINED_GLASS_PANE, true);
            b.set(x, Y + 3, z, Material.WHITE_CONCRETE);
        }
    }

    private void door(Build b, int x, int z, BlockFace facing, String label, float yaw) {
        b.door(x, Y, z, Material.OAK_DOOR, facing, false);
        b.set(x, Y + 2, z, Material.WHITE_CONCRETE);
        double lz = yaw == 0 ? z + 1.02 : z - 0.02;
        labels.add(new Object[] {x + 0.5, Y + 2.3, lz, yaw, "§f" + label});
    }

    private void tv(Build b, int x1, int x2, int z, int y) {
        for (int x = x1; x <= x2; x++)
            for (int yy = y; yy <= y + 1; yy++) {
                b.set(x, yy, z, Material.BLACK_CONCRETE);
                tvs.add(new int[] {x, yy, z});
            }
    }

    private void directeur(Build b) {
        for (int x = 4; x <= 6; x++) b.slabTop(x, Y, 6, Material.POLISHED_BLACKSTONE_SLAB);
        b.pane(5, Y + 1, 6, Material.BLACK_STAINED_GLASS_PANE, true);
        b.stairs(5, Y, 8, Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.SOUTH);
        b.stairs(4, Y, 4, Material.QUARTZ_STAIRS, BlockFace.NORTH);
        b.stairs(6, Y, 4, Material.QUARTZ_STAIRS, BlockFace.NORTH);
        for (int x = 2; x <= 7; x++) b.set(x, Y, 1, Material.QUARTZ_BLOCK);
        b.set(7, Y + 1, 1, Material.POTTED_FERN);
        b.set(3, Y + 1, 1, Material.BOOKSHELF);
        b.set(1, Y, 12, Material.DARK_OAK_FENCE);
        b.set(1, Y + 1, 12, Material.DARK_OAK_FENCE);
        b.set(1, Y + 2, 12, Material.BLUE_WOOL);
        b.stairs(1, Y, 9, Material.BLACKSTONE_STAIRS, BlockFace.WEST);
    }

    private void staff(Build b) {
        for (int x = 13; x <= 17; x++) {
            b.slabTop(x, Y, 6, Material.POLISHED_BLACKSTONE_SLAB);
            b.slabTop(x, Y, 7, Material.POLISHED_BLACKSTONE_SLAB);
            b.pane(x, Y + 1, 7, Material.YELLOW_STAINED_GLASS_PANE, true);
        }
        b.set(14, Y + 1, 6, Material.BLACK_CARPET);
        b.stairs(14, Y, 5, Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.NORTH);
        b.stairs(16, Y, 8, Material.POLISHED_BLACKSTONE_STAIRS, BlockFace.SOUTH);
        boxes(b, 18, 19, 1, 3);
        boxes(b, 11, 12, 11, 13);
    }

    private void boxes(Build b, int x1, int x2, int z1, int z2) {
        int h = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int top = 1 + (h++ % 3);
                for (int y = 0; y < top; y++) b.set(x, Y + y, z, Material.SMOOTH_SANDSTONE);
            }
    }

    private void imprimante(Build b) {
        b.set(26, Y, 10, Material.SMOOTH_QUARTZ);
        b.facing(26, Y + 1, 10, Material.DROPPER, BlockFace.WEST);
        boxes(b, 21, 22, 1, 5);
        boxes(b, 26, 27, 12, 13);
        // Baie réseau murale (point de passage vers le Couloir)
        for (int x = 23; x <= 25; x++)
            for (int y = Y + 1; y <= Y + 3; y++) {
                if (y == Y + 2) b.lit(x, y, 1, Material.REDSTONE_LAMP);
                else b.set(x, y, 1, Material.POLISHED_BLACKSTONE);
                baie.add(new int[] {x, y, 1});
            }
    }

    private void reunion(Build b) {
        for (int x = 32; x <= 38; x++) {
            b.slabTop(x, Y, 6, Material.POLISHED_BLACKSTONE_SLAB);
            b.slabTop(x, Y, 7, Material.POLISHED_BLACKSTONE_SLAB);
            if (x % 2 == 0) {
                b.stairs(x, Y, 5, Material.SMOOTH_QUARTZ_STAIRS, BlockFace.NORTH);
                b.stairs(x, Y, 8, Material.SMOOTH_QUARTZ_STAIRS, BlockFace.SOUTH);
            }
        }
        tv(b, 34, 36, 1, Y + 1);
        b.fill(41, Y, 3, 41, Y + 1, 6, Material.YELLOW_CONCRETE);
    }

    private void pause(Build b) {
        for (int x = 44; x <= 51; x++) b.set(x, Y, 1, Material.BLACK_CONCRETE);
        b.set(47, Y, 1, Material.CAULDRON);
        b.set(49, Y + 1, 1, Material.BREWING_STAND); // machine à café
        b.fill(52, Y, 1, 52, Y + 3, 1, Material.BLACK_CONCRETE);
        b.facing(52, Y + 1, 1, Material.SMOKER, BlockFace.SOUTH);
        b.facing(52, Y + 2, 1, Material.SMOKER, BlockFace.SOUTH);
        b.fill(54, Y, 1, 54, Y + 1, 1, Material.QUARTZ_BLOCK);
        b.set(55, Y, 1, Material.BLACK_CONCRETE);
        // Tables et chaises
        for (int[] t : new int[][] {{47, 7}, {51, 9}}) {
            b.set(t[0], Y, t[1], Material.OAK_FENCE);
            b.slabBottom(t[0], Y + 1, t[1], Material.OAK_SLAB);
            b.stairs(t[0] - 1, Y, t[1], Material.SMOOTH_QUARTZ_STAIRS, BlockFace.WEST);
            b.stairs(t[0] + 1, Y, t[1], Material.SMOOTH_QUARTZ_STAIRS, BlockFace.EAST);
        }
        b.set(44, Y, 11, Material.OAK_FENCE);
        b.slabBottom(44, Y + 1, 11, Material.OAK_SLAB);
        // Canapé orange
        for (int z = 8; z <= 11; z++) {
            b.set(55, Y, z, Material.ORANGE_WOOL);
            b.set(54, Y, z, Material.ORANGE_CARPET);
        }
    }

    /** Salle de cours : rangées de tables blanches, écran au nord, bureau du prof (objet posé dessus). */
    private void salle(Build b, int x1, int x2, int profX, Location objet) {
        for (int z = 25; z <= 31; z += 3)
            for (int x = x1 + 1; x <= x2 - 1; x++) {
                if (x == (x1 + x2) / 2) continue; // allée
                b.slabTop(x, Y, z, Material.SMOOTH_QUARTZ_SLAB);
                if (x % 2 == 0) b.stairs(x, Y, z + 1, Material.POLISHED_DIORITE_STAIRS, BlockFace.SOUTH);
            }
        for (int x = profX - 1; x <= profX + 1; x++) b.slabTop(x, Y, 22, Material.SMOOTH_QUARTZ_SLAB);
        tv(b, x2 - 4, x2 - 2, 21, Y + 1);
        if (x1 == 1) {
            // salle 01 : PC portables
            for (int x = x1 + 2; x <= x2 - 2; x += 3) b.set(x, Y + 1, 25, Material.BLACK_CARPET);
        }
    }

    private void lounge(Build b) {
        int[][] s = {{42, 24}, {43, 25}, {44, 25}, {45, 26}, {46, 27}, {47, 27}, {48, 28}, {49, 29}, {50, 29}};
        for (int i = 0; i < s.length; i++)
            b.set(s[i][0], Y, s[i][1], i % 2 == 0 ? Material.BLUE_WOOL : Material.ORANGE_WOOL);
        b.stairs(39, Y, 30, Material.SANDSTONE_STAIRS, BlockFace.NORTH);
        b.set(39, Y, 31, Material.YELLOW_CARPET);
        b.set(41, Y, 31, Material.BIRCH_FENCE);
        b.set(41, Y + 1, 31, Material.WHITE_CARPET);
        b.stairs(43, Y, 30, Material.DEEPSLATE_TILE_STAIRS, BlockFace.NORTH);
        tv(b, 44, 47, 33, Y + 1);
        b.set(37, Y, 21, Material.POTTED_AZALEA_BUSH);
        b.set(55, Y, 33, Material.POTTED_AZALEA_BUSH);
        // kakémono eemi
        b.fill(37, Y, 25, 37, Y + 2, 25, Material.PURPLE_CONCRETE);
    }
}
