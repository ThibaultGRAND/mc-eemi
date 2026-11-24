package fr.eemicraft.infection;

import fr.eemicraft.infection.build.Audit;
import fr.eemicraft.infection.build.RecordingSink;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Audit hors serveur de la ville (escaliers, vitres, trappes, portes, supports). Usage : AuditCity <blocks.json> */
public final class AuditCity {
    public static void main(String[] a) throws Exception {
        RecordingSink s = new RecordingSink();
        new City(s, Logger.getLogger("audit")).build();
        System.out.print(new Audit(s).run(Path.of(a[0])).report("ville infection"));
    }
}