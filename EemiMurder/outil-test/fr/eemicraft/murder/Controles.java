package fr.eemicraft.murder;

import fr.eemicraft.murder.build.B;
import fr.eemicraft.murder.build.Dir;
import fr.eemicraft.murder.build.RecordingSink;
import fr.eemicraft.murder.build.Stairs;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controles automatiques des details de construction (1.9.1, hors jar) sur la boite de jeu d'une map :
 * <ul>
 *   <li>escaliers de circulation (half=bottom, debout dessus atteignable, dans une volee) : marche suivante
 *       franchissable dans le sens de montee, 2 d'air a l'arrivee, rien devant la premiere marche, forme ;</li>
 *   <li>trappes ouvertes : le bloc contre lequel le panneau se plaque (cote oppose a facing) est plein ;</li>
 *   <li>portes : la porte (2 de haut) et la case devant / derriere (2 de haut) sont libres, avec un sol ;</li>
 *   <li>blocs qui ont besoin d'un support (lanternes, torches, boutons, echelles, paratonnerres, bannieres,
 *       cloches, chaines, tapis, bougies, pointes, fleurs, pots...).</li>
 * </ul>
 */
final class Controles {
    private final RecordingSink s;
    private final TestManoir.Reach r;
    private final MapDef d;
    final Map<String, List<String>> problems = new LinkedHashMap<>();
    /** Dernieres marches adossees a un mur plein (info, a relire). */
    final List<String> backed = new ArrayList<>();
    /** Fausses portes decoratives (porte en fer fermee contre un mur plein), assumees. */
    final List<String> fakes = new ArrayList<>();
    int stairsChecked, trapsChecked, doorsChecked, supportsChecked;

    Controles(RecordingSink s, TestManoir.Reach r, MapDef d) {
        this.s = s; this.r = r; this.d = d;
        for (String k : new String[]{"escalier", "vitre", "trappe", "porte", "support"}) problems.put(k, new ArrayList<>());
    }

    int run(File out) throws Exception {
        for (int x = d.bx1; x <= d.bx2; x++)
            for (int z = d.bz1; z <= d.bz2; z++)
                for (int y = d.minY - 4; y <= d.yHi; y++) {
                    String st = s.get(x, y, z);
                    if (B.isAir(st)) continue;
                    String n = B.name(st);
                    if (n.endsWith("_stairs")) stairs(x, y, z, st);
                    else if (n.endsWith("_trapdoor")) trap(x, y, z, st);
                    else if (n.endsWith("_door") && "lower".equals(B.prop(st, "half"))) door(x, y, z, st);
                    else if (n.endsWith("glass_pane")) pane(x, y, z, st);
                    support(x, y, z, st, n);
                }
        int total = 0;
        StringBuilder sb = new StringBuilder("Controles de details (" + stairsChecked + " escaliers de circulation, " + trapsChecked
                + " trappes ouvertes, " + doorsChecked + " portes, " + supportsChecked + " blocs a support) :");
        for (Map.Entry<String, List<String>> e : problems.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue().size());
            total += e.getValue().size();
        }
        System.out.println(sb);
        try (PrintWriter w = new PrintWriter(new File(out, "mm_" + d.id + ".controles.txt"), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> e : problems.entrySet())
                for (String p : e.getValue()) w.println(e.getKey() + " " + p);
            for (String p : backed) w.println("info-adosse " + p);
            for (String p : fakes) w.println("info-fausse-porte " + p);
        }
        if (!fakes.isEmpty()) System.out.println("  (info) " + fakes.size() + " fausse(s) porte(s) decorative(s) : porte en fer fermee contre un mur plein");
        System.out.println("  (info) " + backed.size() + " dernieres marches adossees a un mur plein (liste dans le .controles.txt)");
        for (Map.Entry<String, List<String>> e : problems.entrySet()) {
            int k = 0;
            for (String p : e.getValue()) if (k++ < 40) System.out.println("  " + e.getKey().toUpperCase() + " " + p);
        }
        return total;
    }

    private void add(String cat, int x, int y, int z, String what) {
        problems.get(cat).add(x + "," + y + "," + z + " " + s.get(x, y, z) + " : " + what);
    }

    // ---------------------------------------------------------------- outils

    /** Le corps peut occuper la case (air, plantes, tapis, panneaux, porte, trappe ouverte...). */
    boolean pass(int x, int y, int z) {
        String st = s.get(x, y, z);
        if (B.isAir(st) || B.name(st).equals("light")) return true;
        return (TestManoir.kindOf(s, x, y, z) & TestManoir.PASS) != 0;
    }

    /** Dessus a pleine hauteur (y+1) : bloc plein, dalle haute/double, escalier retourne, tonneau, etc. */
    static boolean topFull(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.endsWith("_slab")) return !"bottom".equals(B.prop(st, "type"));
        if (n.endsWith("_stairs")) return true; // escalier : on peut marcher sur sa partie haute
        if (n.endsWith("_trapdoor")) return "top".equals(B.prop(st, "half")) && !"true".equals(B.prop(st, "open"));
        return B.isSolid(st) || n.endsWith("leaves") || n.equals("barrel") || n.equals("chest") || n.equals("bookshelf")
                || n.equals("bamboo_mosaic") || n.equals("bamboo_planks") || n.equals("bamboo_block");
    }

    /** Face pleine contre laquelle on peut plaquer / accrocher quelque chose. */
    static boolean full(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.endsWith("_slab")) return "double".equals(B.prop(st, "type"));
        return B.isSolid(st) || n.endsWith("leaves") || n.equals("barrel") || n.equals("bookshelf")
                || n.endsWith("glass") || n.equals("chiseled_bookshelf") || n.equals("mud_bricks")
                || n.equals("bamboo_mosaic") || n.equals("bamboo_planks") || n.equals("bamboo_block");
    }

    /** Peut porter un objet pose dessus / suspendu (pas besoin d'une face pleine : chaine, barriere, muret...). */
    static boolean holds(String st) {
        if (B.isAir(st)) return false;
        String n = B.name(st);
        if (n.equals("light") || n.equals("water") || n.equals("lava") || n.equals("cobweb") || n.endsWith("_carpet")
                || n.contains("torch") || n.endsWith("_button") || n.endsWith("pressure_plate") || n.endsWith("rail")
                || n.equals("redstone_wire") || n.contains("candle") || n.equals("ladder") || n.equals("vine")
                || n.contains("sign") || n.contains("banner") || n.equals("lantern") || n.equals("soul_lantern")
                || n.equals("short_grass") || n.equals("tall_grass") || n.equals("fern") || n.equals("large_fern")
                || flower(n) || n.endsWith("sapling") || n.equals("lily_pad") || n.equals("dead_bush")
                || n.startsWith("potted_") || n.equals("flower_pot") || n.endsWith("_trapdoor") && "true".equals(B.prop(st, "open")))
            return false;
        if (n.equals("iron_chain") || n.equals("chain")) { String a = B.prop(st, "axis"); return a == null || a.equals("y"); }
        return true;
    }

    static boolean flower(String n) {
        return n.equals("dandelion") || n.equals("poppy") || n.contains("tulip") || n.equals("allium") || n.equals("azure_bluet")
                || n.equals("oxeye_daisy") || n.equals("cornflower") || n.equals("lily_of_the_valley") || n.equals("blue_orchid")
                || n.equals("rose_bush") || n.equals("peony") || n.equals("lilac") || n.equals("sunflower") || n.equals("torchflower")
                || n.equals("wither_rose") || n.equals("pink_petals") || n.equals("wildflowers") || n.equals("pitcher_plant")
                || n.equals("open_eyeblossom") || n.equals("closed_eyeblossom") || n.equals("leaf_litter");
    }

    String at(int x, int y, int z, Dir dir) { return s.get(x + dir.dx, y, z + dir.dz); }

    // ---------------------------------------------------------------- escaliers

    private boolean flightStair(String st) {
        return st != null && B.name(st).endsWith("_stairs") && "bottom".equals(B.prop(st, "half"));
    }

    private void stairs(int x, int y, int z, String st) {
        if (!"bottom".equals(B.prop(st, "half"))) return;
        if (!(r.reached(x, y + 1, z) && r.supported(x, y + 1, z))) return;
        Dir f = Dir.of(B.prop(st, "facing"));
        String up = s.get(x + f.dx, y + 1, z + f.dz), down = s.get(x - f.dx, y - 1, z - f.dz);
        boolean inFlight = (flightStair(up) && f.id().equals(B.prop(up, "facing")))
                || (flightStair(down) && f.id().equals(B.prop(down, "facing")));
        if (!inFlight) return;
        stairsChecked++;
        // forme
        String shape = Stairs.shape(s, x, y, z, st);
        if (!shape.equals(B.prop(st, "shape"))) add("escalier", x, y, z, "forme " + B.prop(st, "shape") + " au lieu de " + shape);
        // marche suivante dans le sens de montee
        int nx = x + f.dx, nz = z + f.dz;
        String n1 = s.get(nx, y + 1, nz), n0 = s.get(nx, y, nz);
        boolean ok;
        String why;
        if (flightStair(n1) || (B.name(n1).endsWith("_slab") && "bottom".equals(B.prop(n1, "type")))) {
            ok = pass(nx, y + 2, nz) && pass(nx, y + 3, nz);
            why = "pas 2 d'air au-dessus de la marche suivante (" + s.get(nx, y + 2, nz) + " / " + s.get(nx, y + 3, nz) + ")";
        } else if (topFull(n0) && pass(nx, y + 1, nz)) {
            ok = pass(nx, y + 2, nz);
            why = "palier d'arrivee sans 2 d'air (" + s.get(nx, y + 2, nz) + ")";
        } else if (!pass(nx, y + 1, nz) && !pass(nx, y + 2, nz) && !flightStair(n1)) {
            // derniere marche adossee a un mur d'au moins 2 de haut : rien a gravir derriere (dernier rang de
            // gradins, banc) ; liste a part pour relecture, pas un probleme de circulation
            ok = true;
            why = null;
            backed.add(x + "," + y + "," + z + " " + st + " contre " + n1 + " / " + s.get(nx, y + 2, nz));
        } else {
            ok = false;
            why = "marche suivante infranchissable (" + n0 + " / " + n1 + " / " + s.get(nx, y + 2, nz) + ")";
        }
        if (!ok) add("escalier", x, y, z, why);
        // tete (Esteban, 17/11) : on passe de la marche du dessus (pieds y+2) a celle-ci avec la tete encore a la
        // hauteur du dessus -> 3 cases libres au-dessus de chaque marche suivie d'une marche plus haute
        if (flightStair(n1) && !pass(x, y + 3, z))
            add("escalier", x, y, z, "tete cognee : 3e case au-dessus de la marche pleine (" + s.get(x, y + 3, z) + ")");
        // devant la marche (cote bas) : libre sur 2 de haut (ou marche large : escalier identique au meme niveau)
        int px = x - f.dx, pz = z - f.dz;
        String front = s.get(px, y, pz);
        boolean wideTread = flightStair(front) && pass(px, y + 1, pz); // marche large ou angle (meme niveau)
        // colimacon / volee tournante : on arrive sur cette marche depuis la marche du dessous, sur un autre cote
        boolean fromBelow = false;
        for (Dir dd : Dir.values()) {
            String b = s.get(x + dd.dx, y - 1, z + dd.dz);
            if (flightStair(b) && dd.opposite().id().equals(B.prop(b, "facing"))) fromBelow = true;
        }
        if (!wideTread && !fromBelow && !(pass(px, y, pz) && pass(px, y + 1, pz)))
            add("escalier", x, y, z, "obstacle devant la marche (" + s.get(px, y, pz) + " / " + s.get(px, y + 1, pz) + ")");
        else if (!wideTread && !fromBelow && !flightStair(down) && !pass(px, y + 2, pz))
            add("escalier", x, y, z, "tete cognee en montant sur la 1re marche (" + s.get(px, y + 2, pz) + ")");
        // la tete (2 d'air au-dessus de la marche) est garantie : le dessus de la marche est atteint par le parcours
    }

    // ---------------------------------------------------------------- vitres

    /**
     * Vitre flottante (Esteban, 17/11) : une rangee de vitres doit tenir dans un mur, au moins un de ses deux bouts
     * contre un bloc plein (ou une autre vitre perpendiculaire). Les deux bouts dans le vide = vitre decalee hors du mur.
     */
    private void pane(int x, int y, int z, String st) {
        boolean ax = "true".equals(B.prop(st, "east")) || "true".equals(B.prop(st, "west"));
        boolean az = "true".equals(B.prop(st, "north")) || "true".equals(B.prop(st, "south"));
        int dx = ax || !az ? 1 : 0, dz = dx == 1 ? 0 : 1;
        if (B.name(s.get(x - dx, y, z - dz)).endsWith("glass_pane")) return; // pas le debut de la rangee
        int len = 0;
        while (B.name(s.get(x + (len + 1) * dx, y, z + (len + 1) * dz)).endsWith("glass_pane") && len < 64) len++;
        String a = s.get(x - dx, y, z - dz), b = s.get(x + (len + 1) * dx, y, z + (len + 1) * dz);
        boolean okA = full(a) || holds(a) && !B.isAir(a), okB = full(b) || holds(b) && !B.isAir(b);
        if (!ax && !az) { // vitre seule, sans liaison : elle doit toucher un bloc d'un cote au moins
            for (Dir dd : Dir.values()) if (full(at(x, y, z, dd))) return;
            add("vitre", x, y, z, "vitre isolee touchant rien");
            return;
        }
        if (!okA && !okB) add("vitre", x, y, z, "rangee de " + (len + 1) + " vitre(s) qui ne tient a aucun mur (bouts : " + a + " / " + b + ")");
    }

    // ---------------------------------------------------------------- trappes

    private void trap(int x, int y, int z, String st) {
        if (!"true".equals(B.prop(st, "open"))) return;
        trapsChecked++;
        Dir f = Dir.of(B.prop(st, "facing"));
        String back = at(x, y, z, f.opposite());
        if (!trapBacker(back, f, x + f.opposite().dx, y, z + f.opposite().dz)) add("trappe", x, y, z, "panneau plaque contre " + back + " (cote " + f.opposite().id() + "), en face : " + at(x, y, z, f));
    }

    /**
     * Ce contre quoi un panneau de trappe peut etre plaque : face pleine, flanc d'escalier ou de dalle (accoudoir),
     * trappe ouverte dos a dos, ou plante / pot sur un bloc (bord de jardiniere).
     */
    boolean trapBacker(String back, Dir f, int bx, int by, int bz) {
        if (full(back)) return true;
        String n = B.name(back);
        if (n.endsWith("_stairs")) return true; // accoudoir contre le flanc d'un siege
        if (n.endsWith("_trapdoor") && "true".equals(B.prop(back, "open"))) return f.opposite().id().equals(B.prop(back, "facing"));
        return false;
    }

    // ---------------------------------------------------------------- portes

    private void door(int x, int y, int z, String st) {
        doorsChecked++;
        Dir f = Dir.of(B.prop(st, "facing"));
        String upper = s.get(x, y + 1, z);
        if (!B.name(upper).endsWith("_door")) add("porte", x, y, z, "moitie haute absente (" + upper + ")");
        if (!holds(s.get(x, y - 1, z))) add("porte", x, y, z, "pas de sol sous la porte (" + s.get(x, y - 1, z) + ")");
        boolean fake = B.name(st).equals("iron_door") && "false".equals(B.prop(st, "open"));
        if (fake) {
            // porte en fer fermee (impossible a ouvrir a la main) : admise seulement plaquee sur un mur plein
            boolean walled = false;
            for (Dir sd : new Dir[]{f, f.opposite()})
                if (full(s.get(x + sd.dx, y, z + sd.dz)) && full(s.get(x + sd.dx, y + 1, z + sd.dz))) walled = true;
            if (!walled) add("porte", x, y, z, "porte en fer fermee dans un passage");
        }
        for (Dir side : new Dir[]{f, f.opposite()}) {
            int ax = x + side.dx, az = z + side.dz;
            String c0 = s.get(ax, y, az);
            // premiere marche d'un escalier (ou dalle basse) juste devant la porte : on y monte de face
            // (escalier qui monte en s'eloignant de la porte : facing = ce cote)
            boolean step = ((flightStair(c0) && side.id().equals(B.prop(c0, "facing")))
                    || (B.name(c0).endsWith("_slab") && "bottom".equals(B.prop(c0, "type"))))
                    && pass(ax, y + 1, az) && pass(ax, y + 2, az);
            if (fake && full(c0) && full(s.get(ax, y + 1, az))) {
                // porte en fer fermee plaquee sur un mur : fausse porte decorative (cabine condamnee), assumee
                fakes.add(x + "," + y + "," + z + " " + st);
                continue;
            }
            if (!step && !(pass(ax, y, az) && pass(ax, y + 1, az)))
                add("porte", x, y, z, "cote " + side.id() + " obstrue (" + s.get(ax, y, az) + " / " + s.get(ax, y + 1, az) + ")");
            else if (!step && pass(ax, y - 1, az) && !r.climb(ax, y - 1, az) && !B.name(s.get(ax, y - 1, az)).endsWith("_stairs")) {
                // pas de sol devant : accepte une marche (escalier ou dalle sous la case)
                String below2 = s.get(ax, y - 2, az);
                if (!topFull(below2) && !B.name(below2).endsWith("_slab"))
                    add("porte", x, y, z, "cote " + side.id() + " donne sur le vide (" + s.get(ax, y - 1, az) + ")");
            }
        }
    }

    // ---------------------------------------------------------------- supports

    private void support(int x, int y, int z, String st, String n) {
        String why = null;
        Boolean ok = null;
        if (n.equals("lantern") || n.equals("soul_lantern") || n.equals("copper_lantern") || n.endsWith("_copper_lantern")) {
            boolean hanging = "true".equals(B.prop(st, "hanging"));
            ok = hanging ? holds(s.get(x, y + 1, z)) : holds(s.get(x, y - 1, z));
            why = hanging ? "suspendue sous " + s.get(x, y + 1, z) : "posee sur " + s.get(x, y - 1, z);
        } else if (n.endsWith("wall_torch") || n.endsWith("wall_banner") || n.endsWith("wall_sign") || n.endsWith("wall_head")
                || n.endsWith("wall_skull") || n.equals("ladder") || n.endsWith("coral_wall_fan")) {
            Dir f = Dir.of(B.prop(st, "facing"));
            String back = at(x, y, z, f.opposite());
            ok = n.equals("ladder") || n.endsWith("wall_torch") ? full(back) : holds(back);
            why = "accroche a " + back + " (cote " + f.opposite().id() + ")";
        } else if (n.endsWith("_button") || n.equals("lever")) {
            String face = B.prop(st, "face");
            String b;
            if ("floor".equals(face)) b = s.get(x, y - 1, z);
            else if ("ceiling".equals(face)) b = s.get(x, y + 1, z);
            else b = at(x, y, z, Dir.of(B.prop(st, "facing")).opposite());
            ok = holds(b);
            why = "fixe a " + b;
        } else if (n.equals("lightning_rod") || n.equals("end_rod")) {
            String f = B.prop(st, "facing");
            if (f == null) f = "up"; // etat par defaut du jeu
            String b;
            if ("up".equals(f)) b = s.get(x, y - 1, z);
            else if ("down".equals(f)) b = s.get(x, y + 1, z);
            else b = at(x, y, z, Dir.of(f).opposite());
            ok = holds(b);
            why = "fixe a " + b + " (facing=" + f + ")";
        } else if (n.equals("bell")) {
            String att = B.prop(st, "attachment");
            Dir f = Dir.of(B.prop(st, "facing"));
            if ("floor".equals(att)) ok = holds(s.get(x, y - 1, z));
            else if ("ceiling".equals(att)) ok = holds(s.get(x, y + 1, z));
            else if ("double_wall".equals(att)) ok = holds(at(x, y, z, f)) && holds(at(x, y, z, f.opposite()));
            else ok = holds(at(x, y, z, f)) || holds(at(x, y, z, f.opposite()));
            why = "cloche " + att;
        } else if (n.equals("iron_chain") || n.equals("chain")) {
            String axis = B.prop(st, "axis");
            if (axis == null) axis = "y";
            if ("y".equals(axis)) {
                String a1 = s.get(x, y + 1, z), a2 = s.get(x, y - 1, z);
                ok = garland(a1) || holds(a1) || garland(a2) || holds(a2);
            } else {
                boolean ax = "x".equals(axis);
                // un maillon horizontal touche, dans son axe, un autre maillon / une ampoule / un porteur
                String a1 = s.get(x + (ax ? 1 : 0), y, z + (ax ? 0 : 1)), a2 = s.get(x - (ax ? 1 : 0), y, z - (ax ? 0 : 1));
                ok = garland(a1) || holds(a1) || garland(a2) || holds(a2);
            }
            // le maillon voisin doit etre dans l'axe : tout le cordon (chaines + ampoules, 6-connexe) doit toucher
            // un porteur
            if (ok) ok = cordAttached(x, y, z);
            why = "chaine sans attache (axe " + axis + ")";
        } else if (n.equals("pointed_dripstone")) {
            boolean upDir = "up".equals(B.prop(st, "vertical_direction"));
            String b = upDir ? s.get(x, y - 1, z) : s.get(x, y + 1, z);
            ok = holds(b);
            why = "pointe sur " + b;
        } else if (n.endsWith("_carpet") || n.contains("candle") || n.equals("torch") || n.equals("soul_torch") || n.equals("redstone_torch")
                || n.endsWith("pressure_plate") || n.endsWith("rail") || n.equals("redstone_wire") || n.endsWith("_sign") && !n.contains("hanging")
                || n.endsWith("_banner") || n.startsWith("potted_") || n.equals("flower_pot") || n.equals("short_grass") || n.equals("fern")
                || n.equals("tall_grass") || n.equals("large_fern") || flower(n) || n.endsWith("sapling") || n.equals("dead_bush")
                || n.equals("sugar_cane") || n.equals("snow") || n.endsWith("_head") || n.endsWith("_skull") || n.equals("cake")
                || n.equals("azalea") || n.equals("flowering_azalea") || n.equals("sweet_berry_bush") || n.endsWith("mushroom")
                || n.equals("sea_pickle") || n.equals("lily_pad")) {
            String b = s.get(x, y - 1, z);
            if (n.equals("lily_pad")) ok = B.name(b).equals("water");
            else ok = holds(b) || (B.name(b).equals(n) || B.name(b).equals("water") && n.equals("sea_pickle"));
            why = "pose sur " + b;
        } else if (n.endsWith("_hanging_sign") && !n.contains("wall")) {
            ok = holds(s.get(x, y + 1, z));
            why = "enseigne suspendue sous " + s.get(x, y + 1, z);
        }
        if (ok == null) return;
        supportsChecked++;
        if (!ok) add("support", x, y, z, why);
    }

    private boolean chainEnd(int x, int y, int z, int dy) {
        for (int k = 1; k < 40; k++) {
            String b = s.get(x, y + dy * k, z);
            String n = B.name(b);
            if (n.equals("iron_chain") || n.equals("chain")) { if (!"y".equals(B.prop(b, "axis"))) return true; continue; }
            if (n.equals("lantern") || n.equals("soul_lantern")) return dy < 0 ? false : holds(b);
            return holds(b);
        }
        return false;
    }

    /** Chaine horizontale : suit l'axe (guirlande qui pend : la suite peut etre un rang plus haut ou plus bas). */
    private boolean chainEndH(int x, int y, int z, int dx, int dz) {
        int cy = y;
        for (int k = 1; k < 80; k++) {
            int ax = x + dx * k, az = z + dz * k;
            String b = s.get(ax, cy, az);
            if (B.isAir(b)) {
                String up = s.get(ax, cy + 1, az), dn = s.get(ax, cy - 1, az);
                if (garland(up)) { cy++; continue; }
                if (garland(dn)) { cy--; continue; }
                return false;
            }
            if (isChain(b)) continue;
            return holds(b);
        }
        return false;
    }

    private final java.util.HashMap<Long, Boolean> cordMemo = new java.util.HashMap<>();

    private static long key(int x, int y, int z) { return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096); }

    /** Le cordon (chaines et ampoules reliees face a face) touche-t-il un porteur (hors air, plantes, tapis...) ? */
    private boolean cordAttached(int x, int y, int z) {
        Boolean m = cordMemo.get(key(x, y, z));
        if (m != null) return m;
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        q.add(new int[]{x, y, z});
        seen.add(key(x, y, z));
        boolean att = false;
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty() && seen.size() < 4000) {
            int[] p = q.poll();
            for (int[] d : nb) {
                int ax = p[0] + d[0], ay = p[1] + d[1], az = p[2] + d[2];
                String b = s.get(ax, ay, az);
                if (garland(b)) { if (seen.add(key(ax, ay, az))) q.add(new int[]{ax, ay, az}); }
                else if (holds(b) && !B.name(b).endsWith("lantern")) att = true;
            }
        }
        for (long k : seen) cordMemo.put(k, att);
        return att;
    }

    private static boolean isChain(String b) { String n = B.name(b); return n.equals("iron_chain") || n.equals("chain"); }

    private static boolean garland(String b) { return isChain(b) || B.name(b).endsWith("froglight") || B.name(b).equals("shroomlight"); }
}
