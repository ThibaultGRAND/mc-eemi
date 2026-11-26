package fr.eemicraft.fete;

import java.util.List;

/**
 * Hors jar : chaque place assise doit rester sur une pièce de son manège pendant tout le tour (une boîte du manège
 * juste sous le siège, à moins de 0,9 bloc), et chaque manège montable a son point d'embarquement dans le parc.
 */
public final class TestSeats {
    public static void main(String[] args) {
        java.util.Set<String> all = new java.util.HashSet<>();
        for (Defs.Purchase p : Defs.PURCHASES) all.add(p.id());
        int seats = 0, bad = 0;
        double worst = 0;
        for (Ride r : Draw.rides(all)) {
            int n = 0;
            for (Ride.Part p : r.parts) n += p.seats.size();
            if (n == 0) continue;
            if (r.board == null || r.board[0] < 0 || r.board[0] > Geo.W || r.board[1] < 0 || r.board[1] > Geo.D) {
                System.out.println("PAS D'EMBARQUEMENT VALIDE : " + r.id);
                bad++;
            }
            for (double t = 0; t < 40; t += 0.35) {
                List<Ride.Box> boxes = r.boxes(t, false);
                for (Ride.Part p : r.parts) {
                    for (double[] s : p.seats) {
                        double[] w = Ride.seatAt(p, s, p.motion.at(t));
                        double best = 1e9;
                        for (Ride.Box b : boxes) {
                            double cx = 0, cy = 0, cz = 0;
                            for (double[] c : b.corners()) { cx += c[0]; cy += c[1]; cz += c[2]; }
                            cx /= 8; cy /= 8; cz /= 8;
                            if (cy > w[1] + 0.2) continue; // la pièce doit être sous le siège
                            best = Math.min(best, Math.sqrt((cx - w[0]) * (cx - w[0]) + (cy - w[1]) * (cy - w[1]) + (cz - w[2]) * (cz - w[2])));
                        }
                        worst = Math.max(worst, best);
                        if (best > 0.9 && t < 0.4) {
                            System.out.printf(java.util.Locale.ROOT, "%s : place (%.2f %.2f %.2f) loin de toute piece (%.2f)%n", r.id, s[0], s[1], s[2], best);
                            bad++;
                        }
                        if (t < 0.4) seats++;
                    }
                }
            }
            System.out.printf(java.util.Locale.ROOT, "%-18s %2d places, embarquement %.1f %.1f%n", r.id, n, r.board == null ? -1 : r.board[0], r.board == null ? -1 : r.board[1]);
        }
        System.out.printf(java.util.Locale.ROOT, "%d places, ecart max %.2f, %d defaut(s)%n", seats, worst, bad);
        System.exit(bad == 0 ? 0 : 1);
    }
}
