package fr.eemicraft.build.demo;

import fr.eemicraft.build.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Démo hors serveur : maison en L (façades 3 plans + toit composé + cheminée) sur un terrain avec étang,
 * deux arbres, un chemin et un lampadaire, dans un RecordingSink ; écrit demo.schem, affiche Check, puis lance
 * outils/analyze.py du skill et vérifie les cibles (détail ≥ 15 %, mélange local &lt; 15 %, pan coplanaire ≤ 7).
 *
 * Usage : java -cp classes fr.eemicraft.build.demo.Demo [dossier_de_sortie] [chemin_analyze.py]
 */
public final class Demo {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "out");
        String analyze = args.length > 1 ? args[1]
                : "C:\\Users\\esteb\\EEMICRAFT\\.claude\\skills\\builder-minecraft\\outils\\analyze.py";
        outDir.mkdirs();
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        build(s);
        System.out.println("Construit : " + s.count() + " blocs, " + s.operations() + " operations, "
                + (System.currentTimeMillis() - t0) + " ms");

        Check.Report rep = Check.run(s);
        System.out.println("--- Check (Java) ---\n" + rep);

        File schem = new File(outDir, "demo.schem");
        SchemWriter.write(s, schem);
        System.out.println("Ecrit " + schem.getAbsolutePath() + " (" + schem.length() + " octets)");

        // analyse par le script du skill
        ProcessBuilder pb = new ProcessBuilder("python", "-I", analyze, schem.getAbsolutePath(), "4");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) log.append(line).append('\n');
        }
        int code = p.waitFor();
        File json = new File(new File(analyze).getParentFile().getParentFile(), "out" + File.separator + "demo.json");
        if (code != 0 || !json.exists()) {
            System.out.println("analyze.py a echoue (code " + code + ") :\n" + log);
            System.exit(1);
        }
        String j = Files.readString(json.toPath(), StandardCharsets.UTF_8);
        double det = num(j, "part_detail_visible_sur_bati_%"), mix = num(j, "melange_local_%");
        double pan = num(j, "pan_coplanaire_max"), types = num(j, "types");
        double steps = num(j, "part_marches_1_%"), veg = num(j, "part_vegetation_visible_%");
        System.out.println("--- analyze.py ---");
        System.out.printf("detail sur bati %.2f %% (>= 15) %s%n", det, det >= 15 ? "OK" : "ECHEC");
        System.out.printf("melange local %.2f %% (< 15) %s%n", mix, mix < 15 ? "OK" : "ECHEC");
        System.out.printf("pan coplanaire max %.0f (<= 7) %s%n", pan, pan <= 7 ? "OK" : "ECHEC");
        System.out.printf("types %.0f, marches de 1 %.1f %%, vegetation visible %.1f %%%n", types, steps, veg);
        System.out.println("Rendus : " + new File(json.getParentFile(), "demo_dessus.png") + " et demo_coupe.png");
        boolean ok = det >= 15 && mix < 15 && pan <= 7;
        System.out.println(ok ? "CIBLES TENUES" : "CIBLES NON TENUES");
        System.exit(ok ? 0 : 2);
    }

    private static double num(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\\s*(-?[0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    /** Le builder de démo : c'est ce que ferait un MapBuilder de plugin, avec un Sink. */
    public static void build(Sink s) {
        final int G = 64, yBase = G + 1, yTopMain = yBase + 8, yTopWing = yBase + 4;

        // 1. terrain : FBM + warp, étang, plateforme de la maison raccordée sur 6 blocs
        Terrain t = new Terrain(7, 0, 0, 47, 47)
                .fbm(G, 4.5, 30, 4, 10)
                .dig(39, 38, 7, 5)
                .smooth(2)
                .pad(12, 14, 26, 30, G, 6)
                .meltFill()
                .water(62);
        t.place(s, new Terrain.Surface(), G - 10);

        // 2. chemin de terre battue, du seuil vers le nord, qui serpente (bruit, pas de hasard par bloc)
        Noise pn = new Noise(31);
        for (int z = 0; z <= 14; z++) {
            int cx = 20 + (int) Math.round(2.2 * pn.noise2(z / 9.0, 0.5));
            for (int x = cx - 1; x <= cx; x++) {
                int y = t.height(x, z);
                if (B.name(s.get(x, y, z)).equals("grass_block")) s.set(x, y, z, "minecraft:dirt_path");
            }
        }

        // 3. façades : pierre (mousse en bas, fissures au milieu, propre en haut), socle de deepslate, colombage sombre
        Palette mur = Palette.of(21, "minecraft:stone_bricks", 62, "minecraft:cracked_stone_bricks", 22,
                "minecraft:mossy_stone_bricks", 16).period(9).amp(0.2);
        Style st = new Style()
                .wall(mur.vertical(yBase, yTopMain))
                .plinth(Palette.of(22, "minecraft:cobbled_deepslate", 70, "minecraft:deepslate_bricks", 30).period(8))
                .plinthStairs("minecraft:cobbled_deepslate_stairs")
                .post("minecraft:stripped_dark_oak_log")
                .shutter("minecraft:spruce_trapdoor")
                .cornice("minecraft:dark_oak_stairs")
                .floor(Mat.of("minecraft:spruce_planks"))
                .spacing(4).storey(4).seed(5);
        Facade.building(s, st, 14, 16, 24, 22, yBase, yTopMain, Dir.NORTH, -1);
        Facade.building(s, st.seed(6), 18, 22, 24, 28, yBase, yTopWing, Dir.SOUTH, -1, Dir.NORTH);

        // 4. toit composé : corps principal à pignons est-ouest, aile plus basse à pignons nord-sud
        RoofStyle rs = new RoofStyle()
                .stairs(Palette.of(23, "minecraft:spruce_stairs", 70, "minecraft:dark_oak_stairs", 30).period(5))
                .ridge("minecraft:dark_oak_slab")
                .body(Mat.of("minecraft:spruce_planks"))
                .gable(Mat.of("minecraft:spruce_planks"))
                .gablePost("minecraft:stripped_dark_oak_log")
                .overhang(1).spacing(4);
        int ridge = Roof.build(s, rs, Roof.gableX(14, 16, 24, 22, yTopMain), Roof.gableZ(18, 22, 24, 28, yTopWing));
        Roof.chimney(s, 16, 18, 17, 19, yTopMain + 1, ridge + 2,
                Palette.of(24, "minecraft:deepslate_bricks", 70, "minecraft:cracked_deepslate_bricks", 30).period(6),
                "minecraft:deepslate_brick_slab");

        // 5. lampadaire au départ du chemin
        int ly = t.height(17, 13) + 1;
        s.set(17, ly, 13, "minecraft:dark_oak_fence");
        s.set(17, ly + 1, 13, "minecraft:dark_oak_fence");
        s.set(17, ly + 2, 13, "minecraft:lantern[hanging=false]");

        // 6. arbres (un grand près de la maison, un plus petit près de l'étang)
        new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:azalea_leaves").height(9).crown(3.3)
                .place(s, 6, t.height(6, 9) + 1, 9, 5);
        new Tree("minecraft:oak_log", "minecraft:oak_leaves", "minecraft:flowering_azalea_leaves").height(6).crown(2.6)
                .place(s, 31, t.height(31, 30) + 1, 30, 9);

        // 7. végétation en grappes, puis raccords (escaliers, barrières)
        t.vegetation(s, 3, 0.7);
        Stairs.fix(s, 0, G - 10, 0, 47, ridge + 4, 47);
        Connect.fix(s, 0, G - 10, 0, 47, ridge + 4, 47);
    }
}
