package fr.eemicraft.refuge.expe;

import fr.eemicraft.refuge.Zombies;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Une zone d'expédition, lue dans plugins/EemiRefuge/zones/<id>.yml (copié du jar au premier démarrage).
 * Lecteur volontairement tolérant : clés françaises ou anglaises (nom/name, monde/world, acte/act, bornes/bounds,
 * arrivees/arrivals, extractions, hordes, conteneurs/containers...), points en carte {x, y, z} ou en texte "x y z".
 * Conteneurs : liste de "x,y,z,type,piece" dans le yml (containers) ou dans <id>_containers.yml à côté.
 */
final class Zone {

    /** Un point de la zone (arrivée, extraction, horde). */
    record Point(String name, double x, double y, double z, float yaw, double radius, String transport, int size) {
        Location at(World w) {
            return new Location(w, x, y, z, yaw, 0);
        }

        Location center(World w) {
            return new Location(w, x, y, z);
        }
    }

    /** Un support de fouille marqué. */
    record Box(int x, int y, int z, String type, String piece) {
        long chunk() {
            return chunkKey(x >> 4, z >> 4);
        }

        long block() {
            return blockKey(x, y, z);
        }
    }

    final String id;
    final File file;
    String name, world, icon;
    int act = 1, minPlayers = 1, maxPlayers = 4;
    int minX, maxX, minZ, maxZ, minY = -64, maxY = 320;
    boolean hasBounds;
    final List<Point> arrivals = new ArrayList<>();
    final List<Point> extractions = new ArrayList<>();
    final List<Point> hordes = new ArrayList<>();
    final List<Box> containers = new ArrayList<>();
    final Map<Long, List<Box>> byChunk = new HashMap<>();
    final Map<Long, Box> byBlock = new HashMap<>();

    // Règle des apparitions (défauts dans le code, surchargés par la section apparitions/spawns du yml).
    final Map<Zombies.Type, Integer> types = new EnumMap<>(Zombies.Type.class);
    /** Extraction : nombre de vagues et zombies par vague (-1 = valeurs du config). */
    int extractionWaves = -1, extractionZombies = -1;
    int spawnMinDist = 24, spawnMaxDist = 40, perPlayer = 8, hordeSize = 8;
    /** Règle des apparitions d'ambiance : « exterieur_au_sol » = colonne à ciel ouvert (feuillage permis), y entre spawnMinY et spawnMaxY. */
    String spawnRule;
    int spawnMinY = Integer.MIN_VALUE, spawnMaxY = Integer.MAX_VALUE;
    /** Bordure du monde imposée par le yml (sinon calculée sur les bornes). */
    Double borderX, borderZ, borderSize;
    double eliteChance = 0.04, nightMult = 1.5;
    long dayTime = 6000, nightTime = 18000;

    final List<String> warnings = new ArrayList<>();

    private Zone(String id, File file) {
        this.id = id;
        this.file = file;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }

    static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    boolean inBounds(double x, double z) {
        return !hasBounds || (x >= minX && x <= maxX + 1 && z >= minZ && z <= maxZ + 1);
    }

    boolean inBounds(double x, double y, double z) {
        return inBounds(x, z) && y >= minY && y <= maxY + 1;
    }

    double centerX() {
        return borderX != null ? borderX : hasBounds ? (minX + maxX + 1) / 2.0 : 0;
    }

    double centerZ() {
        return borderZ != null ? borderZ : hasBounds ? (minZ + maxZ + 1) / 2.0 : 0;
    }

    double borderSize() {
        return borderSize != null ? borderSize : hasBounds ? Math.max(maxX - minX + 1, maxZ - minZ + 1) : 60_000_000;
    }

    boolean hasBorder() {
        return hasBounds || borderSize != null;
    }

    // ------------------------------------------------------------------ lecture

    static Zone load(File f, Logger log) {
        String id = f.getName().replaceFirst("\\.ya?ml$", "").toLowerCase(Locale.ROOT);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Zone z = new Zone(str(y, id, "id"), f);
        z.name = str(y, z.id, "nom", "name", "titre");
        z.world = str(y, "refuge_" + z.id, "monde", "world");
        z.icon = str(y, null, "icone", "icon");
        z.act = (int) num(y, 1, "acte", "act");
        z.minPlayers = (int) num(y, 1, "joueurs-min", "min-joueurs", "min-players", "minPlayers", "joueurs.min", "players.min");
        z.maxPlayers = (int) num(y, 4, "joueurs-max", "max-joueurs", "max-players", "maxPlayers", "joueurs.max", "players.max");

        // Bornes : section bornes/bounds ou clés à plat.
        ConfigurationSection b = sec(y, "bornes", "bounds", "limites");
        ConfigurationSection[] scopes = b != null ? new ConfigurationSection[]{b, y} : new ConfigurationSection[]{y};
        Double x1 = numOrNull(scopes, "x-min", "min-x", "minX", "xmin", "x1"), x2 = numOrNull(scopes, "x-max", "max-x", "maxX", "xmax", "x2");
        Double z1 = numOrNull(scopes, "z-min", "min-z", "minZ", "zmin", "z1"), z2 = numOrNull(scopes, "z-max", "max-z", "maxZ", "zmax", "z2");
        if (b != null && (x1 == null || z1 == null)) {
            double[] mn = xyz(b.get("min")), mx = xyz(b.get("max"));
            if (mn != null && mx != null) {
                x1 = mn[0];
                x2 = mx[0];
                z1 = mn.length >= 3 ? mn[2] : mn[1];
                z2 = mx.length >= 3 ? mx[2] : mx[1];
                if (mn.length >= 3 && numOrNull(scopes, "y-min", "min-y", "minY", "ymin") == null) {
                    z.minY = (int) Math.floor(mn[1]);
                    z.maxY = (int) Math.floor(mx[1]);
                }
            }
        }
        if (x1 != null && x2 != null && z1 != null && z2 != null) {
            z.hasBounds = true;
            z.minX = (int) Math.floor(Math.min(x1, x2));
            z.maxX = (int) Math.floor(Math.max(x1, x2));
            z.minZ = (int) Math.floor(Math.min(z1, z2));
            z.maxZ = (int) Math.floor(Math.max(z1, z2));
        } else z.warnings.add("bornes absentes : pas de bordure ni de controle des bornes");
        Double ymin = numOrNull(scopes, "y-min", "min-y", "minY", "ymin"), ymax = numOrNull(scopes, "y-max", "max-y", "maxY", "ymax");
        if (ymin != null) z.minY = (int) Math.floor(ymin);
        if (ymax != null) z.maxY = (int) Math.floor(ymax);

        ConfigurationSection bd = sec(y, "border", "bordure");
        if (bd != null) {
            z.borderX = numOrNull(new ConfigurationSection[]{bd}, "centerX", "center-x", "centre-x", "x");
            z.borderZ = numOrNull(new ConfigurationSection[]{bd}, "centerZ", "center-z", "centre-z", "z");
            z.borderSize = numOrNull(new ConfigurationSection[]{bd}, "size", "taille");
        }

        z.arrivals.addAll(points(y, "Arrivée", "arrivees", "arrivals", "arrivées", "entrees", "spawns-joueurs"));
        z.extractions.addAll(points(y, "Extraction", "extractions", "extraction", "sorties"));
        z.hordes.addAll(points(y, "Horde", "hordes", "horde"));
        if (z.arrivals.isEmpty()) z.warnings.add("aucune arrivee");
        if (z.extractions.isEmpty()) z.warnings.add("aucune extraction");
        z.extractionWaves = y.getInt("extraction-vagues", -1);
        z.extractionZombies = y.getInt("extraction-zombies", -1);

        // Apparitions
        ConfigurationSection sp = sec(y, "apparitions", "spawns", "spawning", "zombies", "ambient_spawns", "ambient-spawns");
        int act = z.act;
        z.types.put(Zombies.Type.RODEUR, 60);
        z.types.put(Zombies.Type.COUREUR, 22);
        z.types.put(Zombies.Type.BRUTE, 6);
        if (act >= 2) {
            z.types.put(Zombies.Type.HURLEUR, 5);
            z.types.put(Zombies.Type.CRACHEUR, 6);
        }
        if (sp != null) {
            z.spawnMinDist = (int) num(sp, z.spawnMinDist, "distance-min", "min-distance", "min", "minPlayerDistance", "min-player-distance");
            z.spawnRule = str(sp, null, "rule", "regle", "règle");
            z.spawnMinY = (int) num(sp, z.spawnMinY, "minY", "min-y", "y-min");
            z.spawnMaxY = (int) num(sp, z.spawnMaxY, "maxY", "max-y", "y-max");
            z.spawnMaxDist = (int) num(sp, z.spawnMaxDist, "distance-max", "max-distance", "max");
            z.perPlayer = (int) num(sp, z.perPlayer, "max-par-joueur", "par-joueur", "per-player", "max-per-player");
            z.hordeSize = (int) num(sp, z.hordeSize, "taille-horde", "horde-size", "horde");
            z.eliteChance = num(sp, z.eliteChance, "elites", "elite", "elite-chance", "chance-elite");
            z.nightMult = num(sp, z.nightMult, "nuit", "night", "multiplicateur-nuit");
            ConfigurationSection ts = sec(sp, "types", "poids", "weights");
            if (ts != null) {
                z.types.clear();
                for (String k : ts.getKeys(false)) {
                    Zombies.Type t = type(k);
                    if (t == null) z.warnings.add("type de zombie inconnu : " + k);
                    else z.types.put(t, ts.getInt(k));
                }
            } else if (sp.isList("types")) {
                z.types.clear();
                for (String k : sp.getStringList("types")) {
                    Zombies.Type t = type(k);
                    if (t != null) z.types.put(t, 10);
                }
            }
        }
        z.types.values().removeIf(v -> v == null || v <= 0);
        if (z.types.isEmpty()) z.types.put(Zombies.Type.RODEUR, 1);
        ConfigurationSection tm = sec(y, "heure", "time", "temps");
        if (tm != null) {
            z.dayTime = (long) num(tm, z.dayTime, "jour", "day");
            z.nightTime = (long) num(tm, z.nightTime, "nuit", "night");
        }

        // Conteneurs
        List<String> lines = new ArrayList<>();
        Object c = first(y, "conteneurs", "containers", "supports");
        if (c instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Object type = m.containsKey("type") ? m.get("type") : "?";
                    Object piece = m.containsKey("piece") ? m.get("piece") : m.containsKey("room") ? m.get("room") : "defaut";
                    lines.add(m.get("x") + "," + m.get("y") + "," + m.get("z") + "," + type + "," + piece);
                } else if (o != null) lines.add(o.toString());
            }
        }
        String cf = str(y, null, "conteneurs-fichier", "containers-file", "fichier-conteneurs", "containers.file", "conteneurs.fichier");
        if (cf != null) cf = new File(cf).getName();
        File side = new File(f.getParentFile(), cf != null ? cf : z.id + "_containers.yml");
        if (side.isFile()) {
            try {
                lines.addAll(Files.readAllLines(side.toPath(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                z.warnings.add("lecture de " + side.getName() + " impossible : " + e.getMessage());
            }
        }
        int bad = 0;
        for (String raw : lines) {
            Box box = parseBox(raw);
            if (box == null) {
                String t = raw.trim();
                if (!t.isEmpty() && !t.startsWith("#") && !t.endsWith(":")) bad++;
                continue;
            }
            if (z.byBlock.putIfAbsent(box.block(), box) != null) continue;
            z.containers.add(box);
            z.byChunk.computeIfAbsent(box.chunk(), k -> new ArrayList<>()).add(box);
        }
        if (bad > 0) z.warnings.add(bad + " ligne(s) de conteneur illisible(s)");
        if (z.containers.isEmpty()) z.warnings.add("aucun conteneur de fouille");
        for (String w : z.warnings) log.warning("Zone " + z.id + " : " + w);
        return z;
    }

    static Zombies.Type type(String k) {
        String s = k.toUpperCase(Locale.ROOT).replace("Ô", "O").replace("É", "E").replace("Ê", "E").replace("-", "_");
        for (Zombies.Type t : Zombies.Type.values()) if (t.name().equals(s)) return t;
        return null;
    }

    /** « x,y,z,type,piece » (aussi « - "x,y,z,type,piece" » d'une liste YAML). */
    static Box parseBox(String raw) {
        String t = raw.trim();
        if (t.startsWith("-")) t = t.substring(1).trim();
        t = t.replace("\"", "").replace("'", "").trim();
        String[] p = t.split("\\s*[,;]\\s*");
        if (p.length < 3) return null;
        try {
            int x = (int) Math.floor(Double.parseDouble(p[0]));
            int yy = (int) Math.floor(Double.parseDouble(p[1]));
            int zz = (int) Math.floor(Double.parseDouble(p[2]));
            String type = p.length > 3 ? p[3].toLowerCase(Locale.ROOT).replace("minecraft:", "") : "?";
            String piece = p.length > 4 && !p[4].isBlank() ? p[4].toLowerCase(Locale.ROOT) : "defaut";
            return new Box(x, yy, zz, type, piece);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Point> points(ConfigurationSection y, String label, String... keys) {
        List<Point> out = new ArrayList<>();
        for (String k : keys) {
            if (!y.contains(k)) continue;
            if (y.isConfigurationSection(k)) {
                ConfigurationSection s = y.getConfigurationSection(k);
                for (String name : s.getKeys(false)) {
                    Object o = s.isConfigurationSection(name) ? s.getConfigurationSection(name).getValues(false) : s.get(name);
                    Point p = point(o, name);
                    if (p != null) out.add(p);
                }
            } else if (y.isList(k)) {
                int i = 1;
                for (Object o : y.getList(k)) {
                    Point p = point(o, label + " " + i++);
                    if (p != null) out.add(p);
                }
            }
            break;
        }
        return out;
    }

    private static Point point(Object o, String defName) {
        if (o instanceof ConfigurationSection cs) o = cs.getValues(false);
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> mm = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) mm.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), e.getValue());
            double[] c = null;
            if (mm.containsKey("x") && mm.containsKey("z")) {
                c = new double[]{d(mm.get("x")), mm.containsKey("y") ? d(mm.get("y")) : 64, d(mm.get("z"))};
            } else {
                for (String k : List.of("pos", "position", "point", "xyz", "coords")) if (mm.containsKey(k)) c = xyz(mm.get(k));
            }
            if (c == null || c.length < 3) return null;
            blockCenter(c);
            String name = s(mm, defName, "nom", "name", "id");
            float yaw = (float) dd(mm, 0, "yaw", "regard", "facing");
            double r = dd(mm, 4, "rayon", "radius", "r");
            String tr = s(mm, null, "transport", "vehicule", "type");
            int size = (int) dd(mm, 0, "taille", "size", "nombre", "count");
            return new Point(name, c[0], c[1], c[2], yaw, r, tr, size);
        }
        if (o instanceof String str) {
            double[] c = xyz(str);
            if (c == null || c.length < 3) return null;
            blockCenter(c);
            return new Point(defName, c[0], c[1], c[2], c.length > 3 ? (float) c[3] : 0, 4, null, 0);
        }
        if (o instanceof List<?> l) {
            double[] c = xyz(l);
            if (c == null || c.length < 3) return null;
            blockCenter(c);
            return new Point(defName, c[0], c[1], c[2], c.length > 3 ? (float) c[3] : 0, 4, null, 0);
        }
        return null;
    }

    /** Coordonnées de bloc (entières) : on vise le centre du bloc en x et z. */
    private static void blockCenter(double[] c) {
        if (c[0] == Math.floor(c[0])) c[0] += 0.5;
        if (c[2] == Math.floor(c[2])) c[2] += 0.5;
    }

    private static double[] xyz(Object o) {
        if (o == null) return null;
        if (o instanceof List<?> l) {
            double[] r = new double[l.size()];
            try {
                for (int i = 0; i < r.length; i++) r[i] = d(l.get(i));
            } catch (NumberFormatException e) {
                return null;
            }
            return r;
        }
        String[] p = o.toString().trim().split("[\\s,;]+");
        double[] r = new double[p.length];
        try {
            for (int i = 0; i < p.length; i++) r[i] = Double.parseDouble(p[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return r;
    }

    private static double d(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(o).trim());
    }

    private static double dd(Map<String, Object> m, double def, String... keys) {
        for (String k : keys)
            if (m.containsKey(k)) {
                try {
                    return d(m.get(k));
                } catch (NumberFormatException ignored) {
                }
            }
        return def;
    }

    private static String s(Map<String, Object> m, String def, String... keys) {
        for (String k : keys) if (m.get(k) != null) return String.valueOf(m.get(k));
        return def;
    }

    private static Object first(ConfigurationSection s, String... keys) {
        for (String k : keys) if (s.contains(k)) return s.get(k);
        return null;
    }

    private static ConfigurationSection sec(ConfigurationSection s, String... keys) {
        for (String k : keys) if (s.isConfigurationSection(k)) return s.getConfigurationSection(k);
        return null;
    }

    private static String str(ConfigurationSection s, String def, String... keys) {
        for (String k : keys) if (s.contains(k) && s.get(k) != null && !s.isConfigurationSection(k)) return s.getString(k);
        return def;
    }

    private static double num(ConfigurationSection s, double def, String... keys) {
        for (String k : keys) if (s.contains(k) && (s.isDouble(k) || s.isInt(k) || s.isLong(k))) return s.getDouble(k);
        return def;
    }

    private static Double numOrNull(ConfigurationSection[] scopes, String... keys) {
        for (ConfigurationSection s : scopes)
            for (String k : keys) if (s.contains(k) && (s.isDouble(k) || s.isInt(k) || s.isLong(k))) return s.getDouble(k);
        return null;
    }
}
