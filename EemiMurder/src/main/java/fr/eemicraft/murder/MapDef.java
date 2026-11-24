package fr.eemicraft.murder;

import fr.eemicraft.murder.build.Sink;

import java.util.List;
import java.util.function.Consumer;

/**
 * Definition d'une map construite par le plugin (1.8.0) : identite, boite de jeu, zone posee, apparitions, or et
 * dessin. Les maps construites : Manoir-bibliotheque ({@link Manoir}), Fete foraine ({@link Fete}),
 * Grand Musee ({@link Musee}), Paquebot ({@link Paquebot}, 1.9.0), Grand Hotel ({@link Hotel}, 1.10.0, petite map), Chalet de montagne ({@link Chalet}, 1.12.0, petite map). Le dessin n'accede jamais a Bukkit (testable
 * hors serveur).
 */
final class MapDef {
    final String id;
    final String world;
    final String name;
    /** Prefixe des cles de data.yml ({@code <cle>.build-version}, {@code <cle>.building}). */
    final String dataKey;
    /** Version de construction : la monter force la reconstruction du monde. */
    final int version;
    /** Boite de jeu (config min/max). */
    final int bx1, bx2, bz1, bz2, minY, maxY;
    /** Zone posee / effacee : x/z -area..area, y yLo..yHi. */
    final int area, yLo, yHi;
    final int[] worldSpawn;
    final long time;
    final int[][] spawns;
    final int[][] gold;
    final Consumer<Sink> builder;
    /**
     * Rayon (en chunks) des chunks vides a generer autour de 0,0 pendant la construction (de -pregen a pregen-1),
     * pour qu'un chargement ulterieur par Multiverse avec un generateur normal ne cree pas de terrain autour ;
     * 0 = rien.
     */
    final int pregen;
    /** 1.10.0 : petite map (2 a 5 joueurs), tiree seulement quand il y a peu de joueurs. */
    final boolean small;

    MapDef(String id, String world, String name, String dataKey, int version,
           int bx1, int bx2, int bz1, int bz2, int minY, int maxY, int area, int yLo, int yHi,
           int[] worldSpawn, long time, int[][] spawns, int[][] gold, Consumer<Sink> builder, int pregen) {
        this(id, world, name, dataKey, version, bx1, bx2, bz1, bz2, minY, maxY, area, yLo, yHi, worldSpawn, time, spawns, gold, builder, pregen, false);
    }

    MapDef(String id, String world, String name, String dataKey, int version,
           int bx1, int bx2, int bz1, int bz2, int minY, int maxY, int area, int yLo, int yHi,
           int[] worldSpawn, long time, int[][] spawns, int[][] gold, Consumer<Sink> builder, int pregen, boolean small) {
        this.small = small;
        this.id = id; this.world = world; this.name = name; this.dataKey = dataKey; this.version = version;
        this.bx1 = bx1; this.bx2 = bx2; this.bz1 = bz1; this.bz2 = bz2; this.minY = minY; this.maxY = maxY;
        this.area = area; this.yLo = yLo; this.yHi = yHi;
        this.worldSpawn = worldSpawn; this.time = time; this.spawns = spawns; this.gold = gold;
        this.builder = builder; this.pregen = pregen;
    }

    static final MapDef MANOIR = new MapDef(Manoir.ID, Manoir.WORLD, Manoir.NAME, "manoir", Manoir.VERSION,
            Manoir.BX1, Manoir.BX2, Manoir.BZ1, Manoir.BZ2, Manoir.MIN_Y, Manoir.MAX_Y,
            Manoir.AREA, Manoir.Y_LO, Manoir.Y_HI, Manoir.WORLD_SPAWN, 13500L, Manoir.SPAWNS, Manoir.GOLD, Manoir::build, 0);

    static final MapDef FETE = new MapDef(Fete.ID, Fete.WORLD, Fete.NAME, "fete", Fete.VERSION,
            Fete.BX1, Fete.BX2, Fete.BZ1, Fete.BZ2, Fete.MIN_Y, Fete.MAX_Y,
            Fete.AREA, Fete.Y_LO, Fete.Y_HI, Fete.WORLD_SPAWN, 18000L, Fete.SPAWNS, Fete.GOLD, Fete::build, 14);

    static final MapDef MUSEE = new MapDef(Musee.ID, Musee.WORLD, Musee.NAME, "musee", Musee.VERSION,
            Musee.BX1, Musee.BX2, Musee.BZ1, Musee.BZ2, Musee.MIN_Y, Musee.MAX_Y,
            Musee.AREA, Musee.Y_LO, Musee.Y_HI, Musee.WORLD_SPAWN, 13800L, Musee.SPAWNS, Musee.GOLD, Musee::build, 14);

    static final MapDef PAQUEBOT = new MapDef(Paquebot.ID, Paquebot.WORLD, Paquebot.NAME, "paquebot", Paquebot.VERSION,
            Paquebot.BX1, Paquebot.BX2, Paquebot.BZ1, Paquebot.BZ2, Paquebot.MIN_Y, Paquebot.MAX_Y,
            Paquebot.AREA, Paquebot.Y_LO, Paquebot.Y_HI, Paquebot.WORLD_SPAWN, 18000L, Paquebot.SPAWNS, Paquebot.GOLD, Paquebot::build, 14);

    static final MapDef HOTEL = new MapDef(Hotel.ID, Hotel.WORLD, Hotel.NAME, "hotel", Hotel.VERSION,
            Hotel.BX1, Hotel.BX2, Hotel.BZ1, Hotel.BZ2, Hotel.MIN_Y, Hotel.MAX_Y,
            Hotel.AREA, Hotel.Y_LO, Hotel.Y_HI, Hotel.WORLD_SPAWN, 16000L, Hotel.SPAWNS, Hotel.GOLD, Hotel::build, 14, true);

    /** 1.12.0 : 2e petite map, chalet de montagne enneige, de nuit. */
    static final MapDef CHALET = new MapDef(Chalet.ID, Chalet.WORLD, Chalet.NAME, "chalet", Chalet.VERSION,
            Chalet.BX1, Chalet.BX2, Chalet.BZ1, Chalet.BZ2, Chalet.MIN_Y, Chalet.MAX_Y,
            Chalet.AREA, Chalet.Y_LO, Chalet.Y_HI, Chalet.WORLD_SPAWN, 14000L, Chalet.SPAWNS, Chalet.GOLD, Chalet::build, 14, true);

    static final List<MapDef> ALL = List.of(MANOIR, FETE, MUSEE, PAQUEBOT, HOTEL, CHALET);

    static MapDef byId(String id) {
        for (MapDef d : ALL) if (d.id.equalsIgnoreCase(id)) return d;
        return null;
    }

    static MapDef byWorld(String base) {
        for (MapDef d : ALL) if (d.world.equals(base)) return d;
        return null;
    }

    static String ids() {
        StringBuilder sb = new StringBuilder();
        for (MapDef d : ALL) sb.append(sb.length() == 0 ? "" : "|").append(d.id);
        return sb.toString();
    }
}
