package fr.eemicraft.fete;

/** Hors jar : simulation de la progression (durée d'un parc à plein revenu, par renaissance). */
public final class TestEcon {
    public static void main(String[] args) {
        Econ e = args.length >= 2 ? new Econ(Double.parseDouble(args[0]), Double.parseDouble(args[1]), 0.5, 0.25) : new Econ();
        StringBuilder sb = new StringBuilder();
        double t0 = e.simulate(0, sb);
        System.out.print(sb);
        for (int r = 0; r <= 3; r++) {
            System.out.printf(java.util.Locale.ROOT, "renaissance %d : %.0f min%n", r, e.simulate(r, null) / 60);
        }
        System.out.printf(java.util.Locale.ROOT, "parc 1 : %.1f h%n", t0 / 3600);
    }
}
