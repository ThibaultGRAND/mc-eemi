package fr.eemicraft.soleil;

import fr.eemicraft.soleil.build.Check;
import fr.eemicraft.soleil.build.RecordingSink;
import fr.eemicraft.soleil.build.SchemWriter;
import java.io.File;

/**
 * Test hors serveur : execute le builder dans un RecordingSink, affiche le rapport Check et ecrit un .schem
 * a mesurer avec analyze.py. Usage : TestMap <old|new> <fichier.schem>. Non embarque dans le jar.
 */
public final class TestMap {
    public static void main(String[] a) throws Exception {
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        if (a[0].equals("old")) {
            new OldMapBuilder(s).build();
        } else {
            // par reflexion : la mesure « avant » se compile sans le nouveau MapBuilder
            Object b = Class.forName("fr.eemicraft.soleil.MapBuilder")
                    .getDeclaredConstructor(fr.eemicraft.soleil.build.Sink.class).newInstance(s);
            java.lang.reflect.Method m = b.getClass().getDeclaredMethod("build");
            m.setAccessible(true);
            m.invoke(b);
            java.lang.reflect.Field f = b.getClass().getDeclaredField("warnings");
            f.setAccessible(true);
            for (Object w : (java.util.List<?>) f.get(b)) System.out.println("AVERTISSEMENT " + w);
        }
        System.out.println("Construit en " + (System.currentTimeMillis() - t0) + " ms, " + s.operations() + " operations, "
                + s.count() + " blocs non-air, " + s.paletteSize() + " etats");
        int[] bb = s.bounds();
        System.out.println("Bornes " + java.util.Arrays.toString(bb));
        System.out.println(Check.run(s));
        SchemWriter.write(s, new File(a[1]));
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        for (int i = 1; i < s.paletteSize(); i++) ids.add(fr.eemicraft.soleil.build.B.name(s.state(i)));
        java.nio.file.Files.write(new File(a[1] + ".ids.txt").toPath(), ids);
    }
}
