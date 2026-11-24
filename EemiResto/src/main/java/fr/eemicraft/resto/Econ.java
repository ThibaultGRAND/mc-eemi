package fr.eemicraft.resto;

import java.util.Set;

/**
 * Economie (sans Bukkit, testee hors serveur) : revenu theorique, multiplicateurs, prix des achats.
 *
 * <p>Les prix sont CALCULES pour equilibrer la progression : le prix de l'achat i = revenu par seconde juste avant
 * cet achat (en achetant dans l'ordre) x une attente qui grandit de {@code growth} par achat a partir de
 * {@code baseWait} secondes, arrondi a 2 chiffres significatifs. Avec 25 s et x1,096, le premier restaurant se
 * termine en environ 3 h de jeu (a plein revenu ; voir la simulation du test).
 */
public final class Econ {
    public double baseWait = 25, growth = 1.096, rebirthGain = 0.5, rebirthPrice = 0.25;
    public double commis = 0.85, carte = 1.25, tapis = 1.2, legendMult = 100;
    private long[] prices;

    public Econ() {
    }

    public Econ(double baseWait, double growth, double rebirthGain, double rebirthPrice) {
        this.baseWait = baseWait;
        this.growth = growth;
        this.rebirthGain = rebirthGain;
        this.rebirthPrice = rebirthPrice;
    }

    public static int count(Set<String> bought, String prefix) {
        int n = 0;
        for (String s : bought) if (s.startsWith(prefix)) n++;
        return n;
    }

    /** Multiplicateur global (carte, tapis rapides, renaissances). */
    public double globalMult(Set<String> bought, int rebirths) {
        int c = (bought.contains("carte1") ? 1 : 0) + (bought.contains("carte2") ? 1 : 0) + (bought.contains("carte3") ? 1 : 0);
        int t = (bought.contains("tapis1") ? 1 : 0) + (bought.contains("tapis2") ? 1 : 0) + (bought.contains("tapis3") ? 1 : 0);
        return Math.pow(carte, c) * Math.pow(tapis, t) * (1 + rebirthGain * rebirths);
    }

    /** Facteur des intervalles des droppers (commis). */
    public double intervalFactor(Set<String> bought) {
        int c = (bought.contains("commis1") ? 1 : 0) + (bought.contains("commis2") ? 1 : 0) + (bought.contains("commis3") ? 1 : 0);
        return Math.pow(commis, c);
    }

    /** Produit des ameliorateurs achetes d'une ligne. */
    public static double lineMult(Set<String> bought, int line) {
        double m = 1;
        for (Defs.Upgrader u : Defs.UPGRADERS) if (u.line() == line && bought.contains(u.purchase())) m *= u.mult();
        return m;
    }

    /** Ligne utilisable (tapis achete). */
    public static boolean lineOn(Set<String> bought, int line) {
        return bought.contains(line == 0 ? "tapis_a" : "tapis_b");
    }

    /** Revenu theorique en euros par seconde. */
    public double rate(Set<String> bought, int rebirths) {
        double r = 0, f = intervalFactor(bought);
        for (Defs.Dropper d : Defs.DROPPERS) {
            if (!bought.contains(d.purchase()) || !lineOn(bought, d.line())) continue;
            r += d.value() / (d.interval() * f) * lineMult(bought, d.line());
        }
        return r * globalMult(bought, rebirths);
    }

    /** Prix de base (sans renaissance) de chaque achat ; 0 pour les achats automatiques. */
    public long[] prices() {
        if (prices != null) return prices;
        int n = Defs.PURCHASES.size();
        prices = new long[n];
        java.util.Set<String> b = new java.util.HashSet<>();
        int k = 0;
        for (int i = 0; i < n; i++) {
            Defs.Purchase p = Defs.PURCHASES.get(i);
            if (p.auto()) {
                b.add(p.id());
                continue;
            }
            double r = Math.max(0.5, rate(b, 0));
            prices[i] = nice(r * baseWait * Math.pow(growth, k));
            k++;
            b.add(p.id());
        }
        return prices;
    }

    /** Prix d'un achat apres {@code rebirths} renaissances. */
    public long price(int index, int rebirths) {
        return nice(prices()[index] * (1 + rebirthPrice * rebirths));
    }

    /** Arrondi a 2 chiffres significatifs (minimum 10). */
    public static long nice(double v) {
        if (v < 10) return 10;
        double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        return (long) (Math.round(v / mag) * mag);
    }

    /** Achats disponibles (boutons a afficher), dans l'ordre. */
    public static java.util.List<Integer> available(Set<String> bought) {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int i = 0; i < Defs.PURCHASES.size(); i++) {
            Defs.Purchase p = Defs.PURCHASES.get(i);
            if (p.auto() || bought.contains(p.id())) continue;
            boolean ok = true;
            for (int j = 0; j < i - Defs.WINDOW && ok; j++) ok = bought.contains(Defs.PURCHASES.get(j).id());
            String need = Defs.NEEDS.get(p.id());
            if (need != null && !bought.contains(need)) ok = false;
            if (ok) l.add(i);
        }
        return l;
    }

    public static boolean complete(Set<String> bought) {
        for (Defs.Purchase p : Defs.PURCHASES) if (!bought.contains(p.id())) return false;
        return true;
    }
}
