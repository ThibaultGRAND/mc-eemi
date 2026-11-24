package fr.eemicraft.soleil;

import fr.eemicraft.soleil.build.Check;
import fr.eemicraft.soleil.build.RecordingSink;
import fr.eemicraft.soleil.build.SchemWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Test hors serveur des zones de Squid Game : construit une zone (dalgona, corde, billes, pont, finale, terrain)
 * dans un RecordingSink, affiche les avertissements et le rapport Check, ecrit le .schem (a mesurer avec analyze.py)
 * et la liste complete des etats de blocs (.states.txt, a valider contre blocks.json). Non embarque dans le jar.
 * Usage : TestSquid <zone> <sortie.schem>
 */
public final class TestSquid {
    public static void main(String[] a) throws Exception {
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        List<String> warnings;
        if (a[0].equals("terrain")) {
            MapBuilder b = new MapBuilder(s);
            b.build();
            warnings = b.warnings;
        } else {
            SquidZones z = new SquidZones(s);
            z.build(a[0]);
            warnings = z.warnings;
        }
        for (String w : warnings) System.out.println("AVERTISSEMENT " + w);
        System.out.println("Construit en " + (System.currentTimeMillis() - t0) + " ms, " + s.operations() + " operations, "
                + s.count() + " blocs non-air, " + s.paletteSize() + " etats");
        System.out.println("Bornes " + java.util.Arrays.toString(s.bounds()));
        System.out.println(Check.run(s));
        SchemWriter.write(s, new File(a[1]));
        TreeSet<String> st = new TreeSet<>();
        for (int i = 1; i < s.paletteSize(); i++) st.add(s.state(i));
        java.nio.file.Files.write(new File(a[1] + ".states.txt").toPath(), new ArrayList<>(st));
    }
}
