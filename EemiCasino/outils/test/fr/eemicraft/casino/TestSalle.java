package fr.eemicraft.casino;

import fr.eemicraft.casino.build.B;
import fr.eemicraft.casino.build.Check;
import fr.eemicraft.casino.build.RecordingSink;
import fr.eemicraft.casino.build.SchemWriter;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * Test hors serveur : construit la salle (avant = 1.2.x, apres = Builder actuel) dans un RecordingSink et ecrit un
 * .schem de l'interieur seul : x/z -31..31, y 100..yTop. Pour que l'analyse ne mesure que ce qu'on voit depuis la
 * salle, l'exterieur (air hors de la salle, masse brute non visible) devient de l'eau (ni air, ni bati).
 * Usage : TestSalle avant|apres fichier.schem [yTop]
 */
public final class TestSalle {
    public static void main(String[] a) throws Exception {
        RecordingSink r = new RecordingSink();
        if (a[0].equals("avant")) new AncienneSalle(r, true).build();
        else new Builder(r, true).build();
        int yTop = a.length > 2 ? Integer.parseInt(a[2]) : 111;
        RecordingSink c = new RecordingSink();
        Map<String, Integer> count = new TreeMap<>();
        for (int y = 100; y <= yTop; y++) {
            for (int z = -31; z <= 31; z++) {
                for (int x = -31; x <= 31; x++) {
                    String s = r.get(x, y, z);
                    boolean inside = Math.abs(x) <= 30 && Math.abs(z) <= 30 && y >= 101;
                    if (B.isAir(s)) {
                        if (!inside) c.set(x, y, z, "minecraft:water");
                        continue;
                    }
                    if (B.name(s).equals("blackstone")) s = "minecraft:water";
                    c.set(x, y, z, s);
                    count.merge(B.base(s), 1, Integer::sum);
                }
            }
        }
        SchemWriter.write(c, new File(a[1]));
        System.out.println("operations " + r.operations() + ", blocs exportes " + c.count());
        System.out.println(Check.run(c));
        count.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue()).limit(40)
                .forEach(e -> System.out.println("  " + e.getValue() + "  " + e.getKey()));
    }
}
