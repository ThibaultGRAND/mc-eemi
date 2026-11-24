package fr.eemicraft.casino;

import fr.eemicraft.casino.build.Audit;
import fr.eemicraft.casino.build.RecordingSink;
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
        new Builder(s, true).build();
        Audit a = new Audit(s)
            // escalier nord-ouest : la 7e marche (y 107, dessus en 108) est au niveau du plancher de la galerie,
            // qu'on rejoint en tournant vers le sud (z -27, tapis + air au-dessus) : palier en quart tournant
            .ignore("ESCALIER", -29, 107, -29, -29, 107, -28, "derniere marche en quart tournant vers la galerie (sud)")
            // salon sous la galerie : banquettes (escalier bas) adossees a l'allege retournee du mur ouest
            .ignore("ESCALIER", -29, 101, -22, -29, 101, 18, "banquettes du salon adossees au mur")
            // toits en croupe des deux kiosques (caisse, boutique) : versant sud contre le mur sud, inaccessible
            .ignore("ESCALIER", -17, 107, 22, -5, 110, 28, "toit en croupe du kiosque de la caisse")
            .ignore("ESCALIER", 6, 107, 22, 18, 110, 28, "toit en croupe du kiosque de la boutique");
        report("casino", a);
        System.out.println("TOTAL " + total);
        System.exit(total == 0 ? 0 : 2);
    }
}