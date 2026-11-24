package fr.eemicraft.deacoudre;

import fr.eemicraft.deacoudre.build.Audit;
import fr.eemicraft.deacoudre.build.RecordingSink;
import java.nio.file.Path;

/** Controle automatique (escaliers, trappes, portes, supports, etats) des maps du plugin. Hors jar. */
public final class TestAudit {
    static int total;
    static Path json;

    static Audit audit(String title, RecordingSink s) throws Exception {
        return new Audit(s);
    }

    static void report(String title, Audit a) throws Exception {
        a.run(json);
        System.out.print(a.report(title));
        total += a.total();
    }

    public static void main(String[] args) throws Exception {
        json = Path.of(args[0]);
        String only = args.length > 1 ? args[1] : "";
        for (int[] rh : new int[][]{{4, 40}, {3, 15}, {6, 80}}) {
            RecordingSink s = new RecordingSink();
            Builder.build(s, rh[0], 100 + rh[1]);
            report("de a coudre r" + rh[0] + " h" + rh[1], new Audit(s));
        }
        System.out.println("TOTAL " + total);
        System.exit(total == 0 ? 0 : 2);
    }
}