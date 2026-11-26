package fr.eemicraft.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controle AUTOMATIQUE hors serveur d'une map posee dans un RecordingSink (rien de ce fichier n'entre dans un jar) :
 * <ul>
 * <li>ETAT : chaque etat de bloc existe dans le blocks.json du data generator 26.3 (id, cles, valeurs) ;</li>
 * <li>FORME : la propriete shape des escaliers est celle que le jeu calculerait (pose sans physique) ;</li>
 * <li>ESCALIER : volees de circulation (premiere marche posee devant un sol) bien orientees, approche libre,
 *     2 blocs d'air au-dessus de chaque marche et de l'arrivee, arrivee sur un sol ; marches a l'envers ;</li>
 * <li>TRAPPE : trappe ouverte plaquee contre un bloc (cote oppose a facing) ;</li>
 * <li>PORTE : deux moities coherentes, posee sur un sol, 2 cases libres devant et derriere ;</li>
 * <li>SUPPORT : lanternes, torches, echelles, boutons, bannieres, panneaux, cloches, chaines, paratonnerres,
 *     tapis, plantes... accroches a un bloc.</li>
 * </ul>
 * Exceptions decoratives : {@link #ignore} (boite + categorie + raison), listees dans le rapport.
 */
public final class Audit {
    private final RecordingSink s;
    private final int[] bb;
    private final Map<String, List<String>> problems = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<int[]> ignoreBoxes = new ArrayList<>();
    private final List<String> ignoreCat = new ArrayList<>(), ignoreWhy = new ArrayList<>();
    private final Map<String, Integer> ignored = new LinkedHashMap<>();

    public Audit(RecordingSink s) {
        this.s = s;
        this.bb = s.bounds();
        for (String c : new String[]{"ETAT", "FORME", "ESCALIER", "VITRE", "TRAPPE", "PORTE", "SUPPORT"}) {
            problems.put(c, new ArrayList<>());
            counts.put(c, 0);
        }
    }

    /** Exception decorative justifiee : categorie (ou "*") dans la boite donnee. */
    public Audit ignore(String cat, int x1, int y1, int z1, int x2, int y2, int z2, String why) {
        ignoreBoxes.add(new int[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
        ignoreCat.add(cat);
        ignoreWhy.add(why);
        return this;
    }

    private void bad(String cat, int x, int y, int z, String msg) {
        for (int i = 0; i < ignoreBoxes.size(); i++) {
            int[] b = ignoreBoxes.get(i);
            if ((ignoreCat.get(i).equals("*") || ignoreCat.get(i).equals(cat)) && x >= b[0] && y >= b[1] && z >= b[2]
                    && x <= b[3] && y <= b[4] && z <= b[5]) {
                ignored.merge(cat + " : " + ignoreWhy.get(i), 1, Integer::sum);
                return;
            }
        }
        counts.merge(cat, 1, Integer::sum);
        List<String> l = problems.get(cat);
        if (l.size() < 40) l.add(x + " " + y + " " + z + "  " + msg);
    }

    public int total() {
        int t = 0;
        for (int v : counts.values()) t += v;
        return t;
    }

    // ------------------------------------------------------------------ outils d'etat

    private String g(int x, int y, int z) { return s.get(x, y, z); }

    private static String n(String st) { return B.name(st); }

    private static String p(String st, String k) { return B.prop(st, k); }

    private static boolean liquid(String st) {
        String n = n(st);
        return n.equals("water") || n.equals("lava") || n.equals("bubble_column");
    }

    private static final Set<String> PLANTS = new HashSet<>(List.of("short_grass", "tall_grass", "fern", "large_fern",
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
            "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac",
            "rose_bush", "peony", "dead_bush", "sweet_berry_bush", "brown_mushroom", "red_mushroom", "torchflower",
            "pink_petals", "wildflowers", "leaf_litter", "bush", "firefly_bush", "short_dry_grass", "tall_dry_grass",
            "pitcher_plant", "open_eyeblossom", "closed_eyeblossom", "cactus_flower", "crimson_roots", "warped_roots",
            "nether_sprouts", "crimson_fungus", "warped_fungus", "wheat", "carrots", "potatoes", "beetroots",
            "sugar_cane", "bamboo", "cactus", "melon_stem", "pumpkin_stem", "azalea", "flowering_azalea",
            "spore_blossom_x"));

    private static boolean plant(String st) {
        String n = n(st);
        return PLANTS.contains(n) || n.endsWith("_sapling") || n.endsWith("_tulip");
    }

    /** Peut porter un objet accroche (tout sauf air, liquide, plante, toile). */
    private static boolean holds(String st) {
        return !B.isAir(st) && !liquid(st) && !plant(st) && !n(st).equals("cobweb") && !n(st).equals("light")
                && !n(st).endsWith("carpet") && !n(st).equals("snow");
    }

    /** Le dessus du bloc touche le haut de sa case au centre (on peut poser un petit objet dessus sans vide). */
    private static boolean topFace(String st) {
        if (!holds(st) || n(st).equals("barrier") || n(st).equals("structure_void")) return false;
        String n = n(st);
        if (n.equals("sea_lantern")) return true;
        if (n.endsWith("_slab")) return !"bottom".equals(p(st, "type"));
        if (n.endsWith("_stairs")) return "top".equals(p(st, "half"));
        if (n.endsWith("_trapdoor")) return "top".equals(p(st, "half")) && !"true".equals(p(st, "open"));
        if (n.endsWith("carpet") || n.endsWith("pressure_plate") || n.equals("snow") || n.endsWith("_door")
                || n.endsWith("_bed") || n.endsWith("candle") || n.equals("flower_pot") || n.startsWith("potted_")
                || n.endsWith("lantern") || n.endsWith("torch") || n.endsWith("_sign") || n.endsWith("_banner")
                || n.endsWith("_button") || n.equals("lever") || n.endsWith("rail") || n.equals("ladder") || n.equals("vine")
                || n.endsWith("_head") || n.endsWith("_skull") || n.equals("campfire") || n.equals("soul_campfire")
                || n.equals("lectern") || n.endsWith("cauldron") || n.equals("enchanting_table") || n.equals("stonecutter")
                || n.equals("daylight_detector") || n.equals("bell") || n.endsWith("_coral_fan")) return false;
        return true;
    }

    /** Le dessous du bloc touche le bas de sa case au centre (on peut y suspendre une lanterne ou une chaine). */
    private static boolean bottomFace(String st) {
        if (!holds(st) || n(st).equals("barrier") || n(st).equals("structure_void")) return false;
        String n = n(st);
        if (n.equals("sea_lantern")) return true;
        if (n.endsWith("_slab")) return !"top".equals(p(st, "type"));
        // escalier retourne : la marche du bas arrive au centre de la face, le crochet y touche
        if (n.endsWith("_stairs")) return true;
        if (n.endsWith("_trapdoor")) return "bottom".equals(p(st, "half")) && !"true".equals(p(st, "open"));
        if (n.endsWith("lantern") || n.endsWith("torch") || n.endsWith("_sign") && !n.endsWith("hanging_sign")
                || n.endsWith("_banner") || n.endsWith("_button") || n.equals("lever") || n.equals("ladder")
                || n.endsWith("carpet") || n.endsWith("pressure_plate") || n.endsWith("candle") || n.equals("flower_pot")
                || n.startsWith("potted_") || n.endsWith("_head") || n.endsWith("_skull") || n.endsWith("_door")) return false;
        return true;
    }

    /** Case sans collision pour un joueur qui la traverse (pieds ou tete). */
    private static boolean free(String st) {
        if (B.isAir(st) || liquid(st) || plant(st)) return true;
        String n = n(st);
        if (n.endsWith("_trapdoor")) return "true".equals(p(st, "open")) || "top".equals(p(st, "half"));
        if (n.endsWith("_door")) return "true".equals(p(st, "open"));
        return n.equals("light") || n.endsWith("carpet") || n.endsWith("torch") || n.endsWith("_sign")
                || n.endsWith("_banner") || n.endsWith("_button") || n.endsWith("pressure_plate") || n.endsWith("rail")
                || n.equals("vine") || n.equals("cobweb") || n.equals("snow") || n.equals("redstone_wire")
                || n.equals("glow_lichen") || n.equals("ladder") || n.equals("lever") || n.endsWith("_wall_head")
                || n.equals("tripwire") || n.equals("string") || n.equals("seagrass") || n.equals("kelp")
                || n.equals("kelp_plant") || n.equals("tall_seagrass") || n.equals("lily_pad") || n.endsWith("_coral_fan")
                || n.endsWith("_coral_wall_fan") || n.endsWith("_coral");
    }

    /** Case d'ou l'on monte sur la premiere marche : libre, ou demi-bloc bas (dalle, marche, trappe fermee). */
    private static boolean approachOk(String st) {
        if (free(st)) return true;
        String n = n(st);
        if (n.endsWith("_slab")) return "bottom".equals(p(st, "type"));
        if (n.endsWith("_stairs")) return "bottom".equals(p(st, "half"));
        return n.endsWith("_trapdoor") && "bottom".equals(p(st, "half"));
    }

    /** On peut avoir les pieds dans la case (x,y,z) en etant porte : sol dessous ou demi-bloc bas dans la case. */
    private boolean floorAt(int x, int y, int z) {
        String c = g(x, y, z);
        String nc = n(c);
        if (nc.endsWith("_slab") && "bottom".equals(p(c, "type"))) return true;
        if (nc.endsWith("_stairs") && "bottom".equals(p(c, "half"))) return true;
        if (nc.endsWith("carpet") || (nc.endsWith("_trapdoor") && "bottom".equals(p(c, "half")) && !"true".equals(p(c, "open"))))
            return true;
        String b = g(x, y - 1, z);
        String nb = n(b);
        if (B.isSolid(b)) return true;
        if (nb.endsWith("_slab")) return !"bottom".equals(p(b, "type"));
        if (nb.endsWith("_stairs")) return true;
        if (nb.endsWith("_trapdoor") && "top".equals(p(b, "half")) && !"true".equals(p(b, "open"))) return true;
        return nb.endsWith("_fence") || nb.endsWith("_wall") || nb.equals("glass_pane") || nb.endsWith("_pane")
                || nb.equals("scaffolding") || nb.endsWith("leaves") || nb.equals("water");
    }

    // ------------------------------------------------------------------ passes

    public Audit run(Path blocksJson) throws IOException {
        if (blocksJson != null) states(blocksJson);
        for (int y = bb[1]; y <= bb[4]; y++)
            for (int z = bb[2]; z <= bb[5]; z++)
                for (int x = bb[0]; x <= bb[3]; x++) {
                    if (s.id(x, y, z) == 0) continue;
                    String st = g(x, y, z);
                    String nm = n(st);
                    if (nm.endsWith("_stairs")) stairs(x, y, z, st);
                    else if (nm.endsWith("_trapdoor")) trapdoor(x, y, z, st);
                    else if (nm.endsWith("_door")) door(x, y, z, st);
                    else if (nm.endsWith("glass_pane")) pane(x, y, z, st);
                    support(x, y, z, st, nm);
                }
        return this;
    }

    private void states(Path blocksJson) throws IOException {
        Map<String, Map<String, Set<String>>> reg = BlocksJson.load(blocksJson);
        for (int i = 1; i < s.paletteSize(); i++) {
            String st = s.state(i);
            Map<String, Set<String>> def = reg.get(B.base(st));
            if (def == null) { bad("ETAT", 0, 0, 0, "bloc inconnu : " + st); continue; }
            for (Map.Entry<String, String> e : B.props(st).entrySet()) {
                Set<String> v = def.get(e.getKey());
                if (v == null || !v.contains(e.getValue())) bad("ETAT", 0, 0, 0, "propriete invalide : " + st);
            }
        }
    }

    private void stairs(int x, int y, int z, String st) {
        String want = Stairs.shape(s, x, y, z, st);
        String have = p(st, "shape") == null ? "straight" : p(st, "shape");
        if (!want.equals(have)) bad("FORME", x, y, z, st + " -> le jeu calculerait shape=" + want);
        if (!"bottom".equals(p(st, "half")) || p(st, "facing") == null) return;
        Dir f = Dir.of(p(st, "facing"));
        // marches a l'envers : la volee monte vers -facing (escalier bas suivant en (x-f, y+1) tourne pareil)
        String rev = g(x - f.dx, y + 1, z - f.dz);
        if (n(rev).endsWith("_stairs") && "bottom".equals(p(rev, "half")) && f.id().equals(p(rev, "facing"))
                && free(g(x, y + 1, z)) && free(g(x - f.dx, y + 2, z - f.dz)))
            bad("ESCALIER", x, y, z, "marches a l'envers (la volee monte vers " + f.opposite().id() + ")");
        // une volee de circulation commence la ou il n'y a pas d'escalier de meme sens juste en dessous-derriere
        String prev = g(x - f.dx, y - 1, z - f.dz);
        boolean first = !(n(prev).endsWith("_stairs") && f.id().equals(p(prev, "facing")) && "bottom".equals(p(prev, "half")));
        if (!first) return;
        int ax = x - f.dx, az = z - f.dz;
        // circulation seulement si l'approche a un sol (sinon : toit, corniche, assise de chaise, rocher)
        String below = g(ax, y - 1, az);
        boolean approachFloor = B.isSolid(below) || (n(below).endsWith("_slab") && !"bottom".equals(p(below, "type")))
                || (n(below).endsWith("_stairs") && "top".equals(p(below, "half")))
                || (n(below).endsWith("_slab") && "bottom".equals(p(below, "type")) && B.isSolid(g(ax, y - 2, az)));
        if (!approachFloor) return;
        // compte les marches de la volee
        List<int[]> steps = new ArrayList<>();
        int cx = x, cy = y, cz = z;
        while (true) {
            steps.add(new int[]{cx, cy, cz});
            String nx = g(cx + f.dx, cy + 1, cz + f.dz);
            if (n(nx).endsWith("_stairs") && f.id().equals(p(nx, "facing")) && "bottom".equals(p(nx, "half"))) {
                cx += f.dx; cy++; cz += f.dz;
            } else break;
        }
        int[] last = steps.get(steps.size() - 1);
        String arr = g(last[0] + f.dx, last[1], last[2] + f.dz);
        boolean arrSolid = B.isSolid(arr) || (n(arr).endsWith("_slab") && !"bottom".equals(p(arr, "type")))
                || (n(arr).endsWith("_stairs") && "top".equals(p(arr, "half")));
        // une marche isolee qui n'arrive pas sur un sol plein : assise, decor -> pas de la circulation
        if (steps.size() == 1 && !arrSolid) return;
        // une marche isolee contre un mur plein (2 blocs) = assise / decor (siege adosse) -> ignore
        if (steps.size() == 1 && !free(g(last[0] + f.dx, last[1] + 1, last[2] + f.dz))
                && !free(g(last[0] + f.dx, last[1] + 2, last[2] + f.dz))) return;
        String id = "volee de " + steps.size() + " vers " + f.id();
        // une marche isolee encastree (approche pleine) : decor de facade, pas de la circulation
        if (steps.size() == 1 && B.isSolid(g(ax, y, az))) return;
        if (!approachOk(g(ax, y, az)) || !free(g(ax, y + 1, az)))
            bad("ESCALIER", x, y, z, id + " : obstacle devant la premiere marche (" + n(g(ax, y, az)) + "/" + n(g(ax, y + 1, az)) + ")");
        for (int i = 0; i < steps.size(); i++) {
            int[] q = steps.get(i);
            if (!free(g(q[0], q[1] + 1, q[2])) || !free(g(q[0], q[1] + 2, q[2])))
                bad("ESCALIER", q[0], q[1], q[2], id + " : moins de 2 blocs libres au-dessus de la marche ("
                        + n(g(q[0], q[1] + 1, q[2])) + "/" + n(g(q[0], q[1] + 2, q[2])) + ")");
            // Esteban 17/11 : en passant de la marche du dessus a celle-ci, la tete est encore a la hauteur du
            // dessus -> 3 cases libres au-dessus de toute marche suivie d'une marche plus haute
            else if (i < steps.size() - 1 && !free(g(q[0], q[1] + 3, q[2])))
                bad("ESCALIER", q[0], q[1], q[2], id + " : tete cognee, 3e case au-dessus pleine (" + n(g(q[0], q[1] + 3, q[2])) + ")");
        }
        // case d'approche : en posant le pied sur la 1re marche, la tete monte d'un cran
        if (approachOk(g(ax, y, az)) && free(g(ax, y + 1, az)) && !free(g(ax, y + 2, az)))
            bad("ESCALIER", x, y, z, id + " : tete cognee en montant sur la 1re marche (" + n(g(ax, y + 2, az)) + ")");
        int tx = last[0] + f.dx, ty = last[1] + 1, tz = last[2] + f.dz;
        String a1 = g(tx, ty, tz), a2 = g(tx, ty + 1, tz);
        boolean slabStep = n(a1).endsWith("_slab") && "bottom".equals(p(a1, "type"));
        if (!arrSolid && !slabStep)
            bad("ESCALIER", last[0], last[1], last[2], id + " : arrivee sans sol (" + n(arr) + ")");
        if ((!free(a1) && !slabStep) || !free(a2) || (slabStep && !free(g(tx, ty + 2, tz))))
            bad("ESCALIER", last[0], last[1], last[2], id + " : l'arrivee bute contre " + n(free(a1) ? a2 : a1));
    }

    /**
     * VITRE (Esteban 17/11) : une rangee de vitres doit tenir dans un mur, au moins un de ses deux bouts contre un
     * bloc ; les deux bouts dans le vide = vitre decalee hors du plan du mur (elle flotte dans la piece).
     */
    private void pane(int x, int y, int z, String st) {
        boolean ax = "true".equals(p(st, "east")) || "true".equals(p(st, "west"));
        boolean az = "true".equals(p(st, "north")) || "true".equals(p(st, "south"));
        if (!ax && !az) {
            for (Dir d : Dir.values()) if (holds(g(x + d.dx, y, z + d.dz))) return;
            bad("VITRE", x, y, z, "vitre isolee touchant rien");
            return;
        }
        int dx = ax ? 1 : 0, dz = ax ? 0 : 1;
        if (n(g(x - dx, y, z - dz)).endsWith("glass_pane")) return;
        int len = 0;
        while (n(g(x + (len + 1) * dx, y, z + (len + 1) * dz)).endsWith("glass_pane") && len < 64) len++;
        String a = g(x - dx, y, z - dz), b = g(x + (len + 1) * dx, y, z + (len + 1) * dz);
        if (!holds(a) && !holds(b))
            bad("VITRE", x, y, z, "rangee de " + (len + 1) + " vitre(s) qui ne tient a aucun mur");
    }

    private void trapdoor(int x, int y, int z, String st) {
        if (!"true".equals(p(st, "open")) || p(st, "facing") == null) return;
        Dir f = Dir.of(p(st, "facing"));
        String back = g(x - f.dx, y, z - f.dz);
        if (!B.isSolid(back) && !n(back).endsWith("_trapdoor") && !n(back).endsWith("_slab") && !n(back).endsWith("_stairs")
                && !n(back).endsWith("_wall") && !n(back).endsWith("_fence") && !n(back).endsWith("leaves"))
            bad("TRAPPE", x, y, z, st + " : rien derriere le panneau (cote " + f.opposite().id() + " = " + n(back) + ")");
    }

    private void door(int x, int y, int z, String st) {
        if (!"lower".equals(p(st, "half"))) {
            String lo = g(x, y - 1, z);
            if (!n(lo).equals(n(st)) || !"lower".equals(p(lo, "half"))) bad("PORTE", x, y, z, "moitie haute sans moitie basse");
            return;
        }
        String up = g(x, y + 1, z);
        if (!n(up).equals(n(st)) || !"upper".equals(p(up, "half")) || !eq(p(up, "facing"), p(st, "facing"))
                || !eq(p(up, "hinge"), p(st, "hinge")))
            bad("PORTE", x, y, z, "moitie haute absente ou differente");
        if (!holds(g(x, y - 1, z))) bad("PORTE", x, y, z, "porte sans bloc dessous");
        if (p(st, "facing") == null) return;
        Dir f = Dir.of(p(st, "facing"));
        for (int sgn : new int[]{1, -1}) {
            int px = x + sgn * f.dx, pz = z + sgn * f.dz;
            String c0 = g(px, y, pz), c1 = g(px, y + 1, pz);
            if (!free(c0) || !free(c1))
                bad("PORTE", x, y, z, "passage obstrue cote " + (sgn > 0 ? f : f.opposite()).id() + " (" + n(c0) + "/" + n(c1) + ")");
        }
    }

    private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    private void support(int x, int y, int z, String st, String nm) {
        String below = g(x, y - 1, z), above = g(x, y + 1, z);
        String f = p(st, "facing");
        Dir d = f == null || f.equals("up") || f.equals("down") ? null : Dir.of(f);
        String behind = d == null ? null : g(x - d.dx, y, z - d.dz);
        String why = null;
        if (nm.equals("lantern") || nm.equals("soul_lantern") || nm.equals("copper_lantern") || nm.endsWith("_copper_lantern")) {
            if ("true".equals(p(st, "hanging"))) { if (!bottomFace(above)) why = "lanterne suspendue a rien (" + n(above) + ")"; }
            else if (!topFace(below)) why = "lanterne posee sur rien (" + n(below) + ")";
        } else if (nm.endsWith("wall_torch")) {
            if (!B.isSolid(behind) && !holds(behind)) why = "torche murale sans mur";
        } else if (nm.endsWith("torch")) {
            if (!topFace(below) && !n(below).endsWith("_fence") && !n(below).endsWith("_wall")) why = "torche dans le vide (" + n(below) + ")";
        } else if (nm.equals("ladder")) {
            if (!holds(behind)) why = "echelle sans mur";
        } else if (nm.equals("lightning_rod") || nm.equals("end_rod") || nm.endsWith("_lightning_rod")) {
            String at = (f == null || "up".equals(f)) ? below : "down".equals(f) ? above : behind;
            if (!holds(at)) why = "tige posee dans le vide";
        } else if (nm.endsWith("_button") || nm.equals("lever")) {
            String face = p(st, "face");
            String at = "floor".equals(face) ? below : "ceiling".equals(face) ? above : behind;
            if (!holds(at)) why = "bouton/levier accroche a rien";
        } else if (nm.endsWith("_wall_banner") || nm.endsWith("_wall_sign") || nm.endsWith("_wall_head") || nm.endsWith("_wall_skull")) {
            if (!holds(behind)) why = "objet mural sans mur";
        } else if (nm.endsWith("_hanging_sign")) {
            if (!nm.contains("wall") && !holds(above)) why = "enseigne suspendue a rien";
        } else if (nm.endsWith("_banner") || nm.endsWith("_sign") || nm.endsWith("candle") || nm.equals("flower_pot")
                || nm.startsWith("potted_") || nm.endsWith("carpet") || nm.endsWith("pressure_plate") || nm.endsWith("rail")
                || nm.equals("redstone_wire") || nm.endsWith("_head") || nm.endsWith("_skull") || nm.equals("campfire")
                || nm.equals("soul_campfire") || nm.equals("snow") || nm.equals("sea_pickle") || nm.equals("turtle_egg")) {
            boolean small = nm.endsWith("candle") || nm.equals("flower_pot") || nm.startsWith("potted_") || nm.endsWith("carpet")
                    || nm.endsWith("pressure_plate");
            if (small ? !topFace(below) : !holds(below) && !(nm.equals("sea_pickle") && liquid(below)))
                why = "pose sur rien (" + n(below) + ")";
        } else if (nm.equals("bell")) {
            String at = p(st, "attachment");
            if ("floor".equals(at) && !holds(below)) why = "cloche posee sur rien";
            if ("ceiling".equals(at) && !holds(above)) why = "cloche suspendue a rien";
            if ("single_wall".equals(at) && d != null && !holds(g(x + d.dx, y, z + d.dz)) && !holds(behind)) why = "cloche murale sans mur";
            if ("double_wall".equals(at) && d != null && (!holds(g(x + d.dx, y, z + d.dz)) || !holds(behind))) why = "cloche entre deux murs absents";
        } else if (nm.equals("iron_chain") || nm.equals("chain") || nm.endsWith("_chain")) {
            String ax = p(st, "axis");
            if ("y".equals(ax) || ax == null) {
                // bout haut d'une chaine verticale : doit etre accroche au-dessus (ou porte quelque chose dessous ET appuye)
                if (!(n(above).equals(nm)) && !bottomFace(above) && !n(above).endsWith("_fence") && !n(above).endsWith("_wall"))
                    why = "chaine qui ne pend a rien (" + n(above) + ")";
            } else {
                int dx = ax.equals("x") ? 1 : 0, dz = 1 - dx;
                int a = 0, b = 0;
                while (n(g(x + (a + 1) * dx, y, z + (a + 1) * dz)).equals(nm) && a < 64) a++;
                while (n(g(x - (b + 1) * dx, y, z - (b + 1) * dz)).equals(nm) && b < 64) b++;
                if (!holds(g(x + (a + 1) * dx, y, z + (a + 1) * dz)) && !holds(g(x - (b + 1) * dx, y, z - (b + 1) * dz)))
                    why = "chaine horizontale accrochee a rien";
            }
        } else if (plant(st) && !nm.equals("bamboo") && !nm.equals("sugar_cane") && !nm.equals("cactus")) {
            String half = p(st, "half");
            if ("upper".equals(half)) { if (!n(below).equals(nm)) why = "haut de plante sans bas"; }
            else if (!holds(below)) why = "plante dans le vide";
        }
        if (why != null) bad("SUPPORT", x, y, z, why + " : " + st);
    }

    // ------------------------------------------------------------------ rapport

    public String report(String title) {
        StringBuilder b = new StringBuilder();
        b.append("=== Audit ").append(title).append(" : ").append(total()).append(" probleme(s)\n");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            b.append("  ").append(e.getKey()).append(" : ").append(e.getValue()).append('\n');
            for (String l : problems.get(e.getKey())) b.append("     ").append(l).append('\n');
        }
        if (!ignored.isEmpty()) {
            b.append("  exceptions decoratives ignorees :\n");
            for (Map.Entry<String, Integer> e : ignored.entrySet())
                b.append("     ").append(e.getValue()).append(" x ").append(e.getKey()).append('\n');
        }
        String d = System.getenv("AUDIT_DUMP");
        if (d != null && !d.isBlank()) {
            String[] t = d.trim().split("[ ,]+");
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(t[i]);
            b.append(dump(s, Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                    Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5])));
        }
        return b.toString();
    }

    /** Liste les blocs non-air d'une boite (pour regarder un probleme de pres). */
    public static String dump(RecordingSink s, int x1, int y1, int z1, int x2, int y2, int z2) {
        StringBuilder b = new StringBuilder();
        for (int y = y1; y <= y2; y++)
            for (int z = z1; z <= z2; z++)
                for (int x = x1; x <= x2; x++) {
                    String st = s.get(x, y, z);
                    if (!B.isAir(st)) b.append(x).append(' ').append(y).append(' ').append(z).append("  ")
                            .append(st.replace("minecraft:", "")).append('\n');
                }
        return b.toString();
    }

    /** Lecteur minimal du blocks.json (data generator) : id -> cle -> valeurs. */
    static final class BlocksJson {
        private static final Map<Path, Map<String, Map<String, Set<String>>>> CACHE = new HashMap<>();

        static synchronized Map<String, Map<String, Set<String>>> load(Path p) throws IOException {
            Map<String, Map<String, Set<String>>> m = CACHE.get(p);
            if (m != null) return m;
            String t = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            Object root = new Json(t).value();
            m = new HashMap<>();
            @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) root;
            for (Map.Entry<String, Object> e : r.entrySet()) {
                Map<String, Set<String>> props = new HashMap<>();
                @SuppressWarnings("unchecked") Map<String, Object> def = (Map<String, Object>) e.getValue();
                Object pr = def.get("properties");
                if (pr instanceof Map<?, ?> pm)
                    for (Map.Entry<?, ?> pe : pm.entrySet()) {
                        Set<String> vals = new HashSet<>();
                        for (Object v : (List<?>) pe.getValue()) vals.add(String.valueOf(v));
                        props.put(String.valueOf(pe.getKey()), vals);
                    }
                m.put(e.getKey(), props);
            }
            CACHE.put(p, m);
            return m;
        }
    }

    /** Analyseur JSON minimal (objets, tableaux, chaines, nombres, booleens). */
    static final class Json {
        private final String t;
        private int i;

        Json(String t) { this.t = t; }

        Object value() {
            ws();
            char c = t.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (t.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = str();
                    ws(); i++; // ':'
                    m.put(k, value());
                    ws();
                    if (t.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (t.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (t.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return str();
            int j = i;
            while (i < t.length() && ",}] \n\r\t".indexOf(t.charAt(i)) < 0) i++;
            return t.substring(j, i);
        }

        private String str() {
            i++;
            StringBuilder b = new StringBuilder();
            while (t.charAt(i) != '"') {
                if (t.charAt(i) == '\\') i++;
                b.append(t.charAt(i++));
            }
            i++;
            return b.toString();
        }

        private void ws() { while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++; }
    }
}
