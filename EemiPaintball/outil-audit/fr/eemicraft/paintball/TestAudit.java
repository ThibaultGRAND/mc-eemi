package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Audit;
import fr.eemicraft.paintball.build.CanonSink;
import fr.eemicraft.paintball.build.RecordingSink;
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

    /** Exceptions purement decoratives, map par map (boites serrees). */
    static void exceptions(String id, Audit a) {
        int f = Speedball.FY;
        switch (id) {
            case "chantier" -> {
                // buse en beton couchee (le long de x) : les escaliers du bas sont l'arrondi exterieur du tube,
                // la paroi est juste au-dessus ; ce n'est pas une marche
                for (int sd : new int[]{-1, 1})
                    a.ignore("ESCALIER", sd * 14, f + 1, -2, sd * 18, f + 1, 1, "arrondi exterieur des buses couchees");
            }
            case "western" -> {
                // toit a pignons de la cabane d'equipe : son egout bute contre le dos de la fausse facade du saloon
                // (meme cas que n'importe quel toit, mais l'audit voit le haut de la facade comme un sol d'approche)
                for (int sd : new int[]{-1, 1})
                    a.ignore("ESCALIER", sd * 31, f + 6, -6, sd * 33, f + 8, 6, "pans du toit de la cabane derriere la fausse facade");
            }
            default -> { }
        }
    }

    /**
     * Construction comme sur le serveur (get renvoie l'etat complet, etats inconnus = air) : aucune exception, meme
     * resultat que la construction de test, et une map posee par-dessus une autre ne laisse rien de l'ancienne.
     */
    static void bukkit() throws Exception {
        java.util.Map<String, RecordingSink> alone = new java.util.LinkedHashMap<>();
        int bad = 0;
        for (String id : SmallMaps.ids()) {
            RecordingSink plain = new RecordingSink();
            SmallMaps.buildField(id, plain);
            CanonSink c = new CanonSink(json);
            try {
                SmallMaps.buildField(id, c);
            } catch (RuntimeException e) {
                System.out.println("  bukkit " + id + " : EXCEPTION " + e);
                bad++;
                continue;
            }
            int d = diff(plain, c.rec(), c, Integer.MAX_VALUE);
            System.out.println("  bukkit " + id + " : " + c.invalid + " etat(s) refuse(s), " + d + " case(s) differente(s) de la construction de test");
            bad += c.invalid + d;
            alone.put(id, c.rec());
        }
        for (String a : SmallMaps.ids())
            for (String b : SmallMaps.ids()) {
                if (a.equals(b)) continue;
                CanonSink c = new CanonSink(json);
                SmallMaps.buildField(a, c);
                SmallMaps.buildField(b, c);
                int d = diff(alone.get(b), c.rec(), null, Speedball.WY - 3); // la salle d'attente (posee par le speedball) reste, c'est voulu
                if (d > 0) System.out.println("  " + b + " pose sur " + a + " : " + d + " case(s) restee(s) de " + a);
                bad += d;
            }
        System.out.println("  bukkit : " + bad + " ecart(s)");
        total += bad;
    }

    static int diff(RecordingSink ref, RecordingSink got, CanonSink canonRef, int yMax) {
        int[] p = ref.bounds(), q = got.bounds();
        int n = 0;
        for (int y = Math.min(p[1], q[1]); y <= Math.min(yMax, Math.max(p[4], q[4])); y++)
            for (int z = Math.min(p[2], q[2]); z <= Math.max(p[5], q[5]); z++)
                for (int x = Math.min(p[0], q[0]); x <= Math.max(p[3], q[3]); x++) {
                    String r = ref.get(x, y, z);
                    if (canonRef != null) r = canonRef.canon(r);
                    String g = got.get(x, y, z);
                    if (!r.equals(g)) {
                        if (n < 5) System.out.println("     " + x + " " + y + " " + z + " attendu " + r + " / obtenu " + g);
                        n++;
                    }
                }
        return n;
    }

    public static void main(String[] args) throws Exception {
        json = Path.of(args[0]);
        String only = args.length > 1 ? args[1] : "";
        for (String id : SmallMaps.ids()) {
            if (!only.isEmpty() && !only.equals(id)) continue;
            RecordingSink s = new RecordingSink();
            SmallMaps.buildField(id, s);
            Audit a = new Audit(s);
            exceptions(id, a);
            report("paintball " + id, a);
        }
        if (only.isEmpty() || only.equals("bukkit")) bukkit();
        System.out.println("TOTAL " + total);
        System.exit(total == 0 ? 0 : 2);
    }
}