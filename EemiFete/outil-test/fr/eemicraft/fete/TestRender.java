package fr.eemicraft.fete;

import fr.eemicraft.fete.build.B;
import fr.eemicraft.fete.build.Check;
import fr.eemicraft.fete.build.RecordingSink;
import fr.eemicraft.fete.build.SchemWriter;
import fr.eemicraft.fete.build.Sink;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hors jar. Construit l'allée foraine et des parcs à différents stades, écrit des vidages de blocs pour le rendu
 * isométrique (render_iso.py), un .schem, et les images d'animation d'un parc complet (manèges à plusieurs instants).
 * Usage : java ... TestRender <dossier de sortie>
 */
public final class TestRender {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "out");
        boolean pack = args.length > 1 && args[1].equals("pack");
        out.mkdirs();
        Geo g = new Geo(10);
        RecordingSink s = new RecordingSink();
        java.util.List<Object[]> allBoxes = new java.util.ArrayList<>();
        long t0 = System.currentTimeMillis();
        Draw.street(s, g, 42);
        int n = Defs.PURCHASES.size();
        int[] upto = {0, 4, 12, 20, n, n, 26, 8, n, 16};
        for (int i = 0; i < g.parcels; i++) {
            if (upto[i] == 0) {
                Draw.parcel(s, g, i, new Draw.State(false, null, List.of(), false));
                continue;
            }
            Set<String> b = new HashSet<>();
            for (int k = 0; k < upto[i]; k++) b.add(Defs.PURCHASES.get(k).id());
            Draw.parcel(s, g, i, new Draw.State(true, b, List.of(0, 1), upto[i] == n));
            // manèges figés à un instant différent par parc (boîtes tournées, comme le client)
            for (Ride r : Draw.rides(b)) allBoxes.add(new Object[]{i, r.boxes(3.0 + i * 1.7)});
        }
        System.out.println("fete : " + (System.currentTimeMillis() - t0) + " ms, operations " + s.operations());
        System.out.println(Check.run(s));
        SchemWriter.write(s, new File(out, "fete.schem"));
        dump(s, new File(out, "fete.txt"), null);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(out, "fete.txt"), true))) {
            double[] sp = Draw.spawn(g);
            writeBoxes(w, g, -1, Rides.kiosque(sp[0], 0.5).boxes(0));
            for (Object[] o : allBoxes) {
                @SuppressWarnings("unchecked") List<Ride.Box> l = (List<Ride.Box>) o[1];
                writeBoxes(w, g, (Integer) o[0], l);
            }
        }

        // un parc complet seul (avec un bout d'allée), plusieurs instants pour l'animation
        Set<String> all = new HashSet<>();
        for (Defs.Purchase p : Defs.PURCHASES) all.add(p.id());
        List<Ride> rides = Draw.rides(all);
        int cubes = 0;
        for (Ride r : rides) {
            cubes += r.cubes();
            System.out.println("  " + r.id + " : " + r.cubes() + " cubes, " + r.parts.size() + " pièces");
        }
        System.out.println("parc complet : " + cubes + " BlockDisplay");
        for (int f = 0; f < 24; f++) {
            RecordingSink one = new RecordingSink();
            Draw.parcel(one, g, 0, new Draw.State(true, all, List.of(), true));
            double t = f * 0.5;
            int[] bx = g.box(0);
            File ff = new File(out, String.format("parc_%02d.txt", f));
            dump(one, ff, new int[]{bx[0] - 2, bx[1] - 12, bx[2] + 2, bx[3] + 2});
            try (BufferedWriter w = new BufferedWriter(new FileWriter(ff, true))) {
                for (Ride r : rides) writeBoxes(w, g, 0, r.boxes(t, pack));
            }
            if (f == 0) {
                SchemWriter.write(one, new File(out, "parc_complet.schem"));
                System.out.println("parc complet (statique) : " + Check.run(one));
            }
        }
    }

    /** Boîtes tournées : « box etat glow » + 8 coins (monde). */
    static void writeBoxes(BufferedWriter w, Geo g, int parcel, List<Ride.Box> l) throws Exception {
        for (Ride.Box b : l) {
            StringBuilder sb = new StringBuilder("box ").append(b.state()).append(' ').append(b.glow() ? 1 : 0);
            for (double[] c : b.corners()) {
                double x = parcel < 0 ? c[0] : g.wxd(parcel, c[0], c[2]), z = parcel < 0 ? c[2] : g.wzd(parcel, c[0], c[2]);
                sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f", x, c[1], z));
            }
            w.write(sb.append('\n').toString());
        }
    }

    /** Vidage « x y z etat » des blocs non vides (boîte {x1, z1, x2, z2} optionnelle). */
    static void dump(RecordingSink s, File f, int[] box) throws Exception {
        int[] b = s.bounds();
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (int x = b[0]; x <= b[3]; x++) {
                if (box != null && (x < box[0] || x > box[2])) continue;
                for (int z = b[2]; z <= b[5]; z++) {
                    if (box != null && (z < box[1] || z > box[3])) continue;
                    for (int y = b[1]; y <= b[4]; y++) {
                        String st = s.get(x, y, z);
                        if (st == null || B.isAir(st)) continue;
                        w.write(x + " " + y + " " + z + " " + st + "\n");
                    }
                }
            }
        }
    }
}
