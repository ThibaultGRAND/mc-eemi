package fr.eemicraft.fete;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Économie de la fête (sans Bukkit, testée hors serveur) : revenu théorique, multiplicateurs, prix des achats.
 *
 * <p>Même principe que le resto : le prix de l'achat i = revenu par seconde juste avant cet achat (en achetant dans
 * l'ordre) × une attente qui grandit de {@code growth} par achat à partir de {@code baseWait} secondes, arrondi à
 * 2 chiffres significatifs. La simulation de {@code TestEcon} donne la durée d'un parc.
 */
public final class Econ {
    public double baseWait = 25, growth = 1.155, rebirthGain = 0.5, rebirthPrice = 0.25, speed = 0.85;
    private long[] prices;

    public Econ() {
    }

    public Econ(double baseWait, double growth, double rebirthGain, double rebirthPrice) {
        this.baseWait = baseWait;
        this.growth = growth;
        this.rebirthGain = rebirthGain;
        this.rebirthPrice = rebirthPrice;
    }

    /** Multiplicateur des tickets (améliorations et renaissances). */
    public double globalMult(Set<String> bought, int rebirths) {
        double m = 1;
        for (var e : Defs.BOOSTS.entrySet()) if (bought.contains(e.getKey())) m *= e.getValue();
        return m * (1 + rebirthGain * rebirths);
    }

    /** Facteur de durée des tours (forains). */
    public double cycleFactor(Set<String> bought) {
        int n = 0;
        for (String s : Defs.SPEED) if (bought.contains(s)) n++;
        return Math.pow(speed, n);
    }

    /** Durée réelle d'un tour d'une attraction (s). */
    public double cycle(Set<String> bought, Defs.Attraction a) {
        return a.cycle() * cycleFactor(bought);
    }

    /** Tickets d'un tour d'une attraction. */
    public double value(Set<String> bought, int rebirths, Defs.Attraction a) {
        return a.value() * globalMult(bought, rebirths);
    }

    /** Revenu théorique en tickets par seconde. */
    public double rate(Set<String> bought, int rebirths) {
        double r = 0, f = cycleFactor(bought);
        for (Defs.Attraction a : Defs.ATTRACTIONS) {
            if (bought.contains(a.purchase())) r += a.value() / (a.cycle() * f);
        }
        return r * globalMult(bought, rebirths);
    }

    /** Prix de base (sans renaissance) de chaque achat ; 0 pour les achats automatiques. */
    public long[] prices() {
        if (prices != null) return prices;
        int n = Defs.PURCHASES.size();
        prices = new long[n];
        Set<String> b = new HashSet<>();
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

    /** Prix d'un achat après {@code rebirths} renaissances. */
    public long price(int index, int rebirths) {
        return nice(prices()[index] * (1 + rebirthPrice * rebirths));
    }

    /** Arrondi à 2 chiffres significatifs (minimum 10). */
    public static long nice(double v) {
        if (v < 10) return 10;
        double mag = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        return (long) (Math.round(v / mag) * mag);
    }

    /** Achats disponibles (boutons à afficher), dans l'ordre. */
    public static List<Integer> available(Set<String> bought) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < Defs.PURCHASES.size(); i++) {
            Defs.Purchase p = Defs.PURCHASES.get(i);
            if (p.auto() || bought.contains(p.id())) continue;
            boolean ok = true;
            for (int j = 0; j < i - Defs.WINDOW && ok; j++) ok = bought.contains(Defs.PURCHASES.get(j).id());
            if (ok) l.add(i);
        }
        return l;
    }

    public static boolean complete(Set<String> bought) {
        for (Defs.Purchase p : Defs.PURCHASES) if (!bought.contains(p.id())) return false;
        return true;
    }

    /** Simulation : secondes pour finir le parc (achats les moins chers d'abord, revenu plein). */
    public double simulate(int rebirths, StringBuilder log) {
        Set<String> b = new HashSet<>();
        for (Defs.Purchase p : Defs.PURCHASES) if (p.auto()) b.add(p.id());
        double money = 0, t = 0;
        while (!complete(b)) {
            List<Integer> av = available(b);
            int best = av.get(0);
            for (int i : av) if (price(i, rebirths) < price(best, rebirths)) best = i;
            long price = price(best, rebirths);
            double rate = rate(b, rebirths);
            if (money < price) {
                t += (price - money) / rate;
                money = price;
            }
            money -= price;
            b.add(Defs.PURCHASES.get(best).id());
            if (log != null) log.append(String.format(java.util.Locale.ROOT, "%6.1f min  %-20s %12d  (%.1f/s)%n", t / 60,
                    Defs.PURCHASES.get(best).id(), price, rate));
        }
        return t;
    }
}
