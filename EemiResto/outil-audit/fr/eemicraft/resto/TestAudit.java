package fr.eemicraft.resto;

import fr.eemicraft.resto.build.Audit;
import fr.eemicraft.resto.build.Check;
import fr.eemicraft.resto.build.RecordingSink;
import fr.eemicraft.resto.build.SchemWriter;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hors jar. Construit la rue + des parcelles a differents stades dans un RecordingSink, lance l'audit commun
 * (etats, escaliers, vitres, supports...), la mesure Check, ecrit un .schem, et simule l'economie.
 * Usage : audit.ps1 (arguments : blocks.json [dossier de sortie]).
 */
public final class TestAudit {
    public static void main(String[] args) throws Exception {
        Path json = Path.of(args[0]);
        File out = new File(args.length > 1 && !args[1].isEmpty() ? args[1] : ".");
        out.mkdirs();
        econ();
        Geo g = new Geo(16);
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        Draw.street(s, g, 42);
        long t1 = System.currentTimeMillis();
        int n = Defs.PURCHASES.size();
        for (int i = 0; i < g.parcels; i++) {
            int stage = i % 6; // 0 libre, puis 4 / 12 / 20 / 30 / tout
            int[] upto = {0, 4, 12, 20, 30, n};
            if (stage == 0) {
                Draw.parcel(s, g, i, new Draw.State(false, null, List.of(), false));
                continue;
            }
            Set<String> b = new HashSet<>();
            for (int k = 0; k < upto[stage]; k++) b.add(Defs.PURCHASES.get(k).id());
            List<Integer> av = Econ.available(b);
            Draw.parcel(s, g, i, new Draw.State(true, b, av, Econ.complete(b)));
        }
        long t2 = System.currentTimeMillis();
        System.out.println("rue " + (t1 - t0) + " ms, 16 parcelles " + (t2 - t1) + " ms, operations " + s.operations());
        Audit a = new Audit(s)
                // chaises (escaliers isoles) autour des tables : pas des volees de circulation
                .ignore("ESCALIER", g.xStart, Geo.FLOOR + 1, -Geo.EDGE - Geo.D, g.xStart + g.length, Geo.FLOOR + 1, Geo.EDGE + Geo.D, "chaises des tables (escaliers isoles)")
                // auvent rayé au-dessus de la porte : escaliers decoratifs a y 67, inaccessibles
                .ignore("ESCALIER", g.xStart, 67, -Geo.EDGE - Geo.D, g.xStart + g.length, 67, Geo.EDGE + Geo.D, "auvent au-dessus de la porte");
        a.run(json);
        System.out.print(a.report("resto"));
        System.out.println(Check.run(s));
        SchemWriter.write(s, new File(out, "resto.schem"));
        // une parcelle complete seule, pour la mesure du bati
        RecordingSink one = new RecordingSink();
        Set<String> all = new HashSet<>();
        for (Defs.Purchase p : Defs.PURCHASES) all.add(p.id());
        Draw.parcel(one, g, 0, new Draw.State(true, all, List.of(), true));
        SchemWriter.write(one, new File(out, "resto_parcelle.schem"));
        System.out.println("parcelle complete : " + Check.run(one));
        System.out.println("TOTAL " + a.total());
        System.exit(a.total() == 0 ? 0 : 2);
    }

    static void econ() {
        Econ e = new Econ();
        long[] pr = e.prices();
        for (int r = 0; r <= 2; r++) {
            Set<String> b = new HashSet<>();
            for (Defs.Purchase p : Defs.PURCHASES) if (p.auto()) b.add(p.id());
            double money = 0, t = 0;
            StringBuilder sb = new StringBuilder();
            while (!Econ.complete(b)) {
                List<Integer> av = Econ.available(b);
                int best = av.get(0);
                for (int i : av) if (e.price(i, r) < e.price(best, r)) best = i;
                long price = e.price(best, r);
                double rate = e.rate(b, r);
                if (money < price) {
                    t += (price - money) / rate;
                    money = price;
                }
                money -= price;
                b.add(Defs.PURCHASES.get(best).id());
                if (r == 0) sb.append(String.format("  %-14s %,14d EUR  a %6.1f min  (revenu %,.1f/s)%n", Defs.PURCHASES.get(best).id(), price, t / 60, rate));
            }
            if (r == 0) System.out.print(sb);
            System.out.printf("Renaissance %d : restaurant complet en %.2f h (revenu final %,.0f EUR/s)%n", r, t / 3600, e.rate(b, r));
        }
        System.out.println("Somme des prix : " + java.util.Arrays.stream(pr).sum());
    }
}
