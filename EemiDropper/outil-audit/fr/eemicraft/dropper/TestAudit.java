package fr.eemicraft.dropper;

import fr.eemicraft.dropper.build.Audit;
import fr.eemicraft.dropper.build.RecordingSink;
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
        RecordingSink s = new RecordingSink();
        WaitingRoom.build(s, 100, false);
        report("salle d'attente du dropper", new Audit(s));
        System.out.println("TOTAL " + total);
        System.exit(total == 0 ? 0 : 2);
    }
}