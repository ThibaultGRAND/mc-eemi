package fr.eemicraft.soleil;

import static fr.eemicraft.soleil.Layout.*;

import fr.eemicraft.soleil.build.Audit;
import fr.eemicraft.soleil.build.RecordingSink;
import java.nio.file.Path;
import java.util.List;

/**
 * Controle automatique (escaliers, trappes, portes, supports, etats) des zones du Squid Game, plus les avertissements
 * de contrat des builders (MapBuilder.check, SquidZones). Hors jar. Code de sortie 0 = aucun probleme.
 */
public final class TestAudit {
    static int total;
    static Path json;

    static void report(String title, Audit a, List<String> warnings) throws Exception {
        a.run(json);
        System.out.print(a.report(title));
        total += a.total();
        for (String w : warnings) System.out.println("  CONTRAT : " + w);
        total += warnings.size();
    }

    /** Exceptions purement decoratives, justifiees, par zone. */
    static void exceptions(String id, Audit a) {
        switch (id) {
            case "terrain" -> {
                // lambrequin de l'auvent des gradins : trappes ouvertes suspendues sous le bord avant de l'auvent,
                // panneau aligne sur ce bord (cote nord), rien derriere par construction
                a.ignore("TRAPPE", -15, 107, 67, 15, 107, 67, "lambrequin suspendu sous le bord avant de l'auvent");
                // portes en fer factices (sans redstone, jamais ouvertes) des murs de l'arene et du dortoir :
                // dos contre l'exterieur, decor de facade
                a.ignore("PORTE", 0, 101, -14, 1, 101, -14, "portail en fer factice du mur du fond");
                a.ignore("PORTE", -18, 101, 0, -18, 101, 70, "portes de service factices du mur ouest");
                a.ignore("PORTE", 18, 101, 0, 18, 101, 70, "portes de service factices du mur est");
                a.ignore("PORTE", 0, 109, -80, 0, 109, -80, "porte de sortie factice du dortoir (mur exterieur)");
            }
            case "billes" -> {
                // pans de toit des maisons de la ruelle au-dessus d'une cheminee : la cheminee (pleine) passe pour un
                // "sol" devant la premiere tuile et le faitage en dalle pour une arrivee sans sol ; toiture, pas
                // une circulation (aucun acces aux toits)
                a.ignore("ESCALIER", BX - 47, FY + 6, -22, BX + 47, FY + 25, -8, "pans de toit nord coupes par une cheminee");
                a.ignore("ESCALIER", BX - 47, FY + 6, 9, BX + 47, FY + 25, 23, "pans de toit sud coupes par une cheminee");
            }
            case "pont" -> {
                // portes en fer factices au fond des quais, dans le mur (decor, jamais ouvertes)
                a.ignore("PORTE", P_START1 - 1, PB + 1, 0, P_START1 - 1, PB + 1, 1, "porte factice du quai de depart");
                a.ignore("PORTE", P_END2 + 1, PB + 1, 0, P_END2 + 1, PB + 1, 1, "porte factice du quai d'arrivee");
            }
            default -> { }
        }
    }

    public static void main(String[] args) throws Exception {
        json = Path.of(args[0]);
        String only = args.length > 1 ? args[1] : "";
        if (only.isEmpty() || only.equals("terrain")) {
            RecordingSink s = new RecordingSink();
            MapBuilder b = new MapBuilder(s);
            b.build();
            Audit a = new Audit(s);
            exceptions("terrain", a);
            report("squid terrain", a, b.warnings);
        }
        for (String id : new String[]{"dalgona", "corde", "billes", "pont", "finale"}) {
            if (!only.isEmpty() && !only.equals(id)) continue;
            RecordingSink s = new RecordingSink();
            SquidZones z = new SquidZones(s);
            z.build(id);
            Audit a = new Audit(s);
            exceptions(id, a);
            report("squid " + id, a, z.warnings);
        }
        System.out.println("TOTAL " + total);
        System.exit(total == 0 ? 0 : 2);
    }
}
