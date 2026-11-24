package fr.eemicraft.sheepwars.gen.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Palette ordonnée CLAIR → SOMBRE avec poids. Deux usages (règles E et I2 du skill) :
 * <ul>
 * <li>{@link #at}/{@link #clustered} : bloc principal + variantes en GRAPPES (bruit 3D de période
 * {@link #period}, 4-8 blocs) dans la proportion de leurs poids. Jamais de tirage bloc par bloc.</li>
 * <li>{@link #pick}(x,y,z,t) : dégradé. t ∈ [0,1] = position voulue (0 clair, 1 sombre), décalée par le
 * bruit basse fréquence ±{@link #amp} pour faire des amas, puis lue dans les poids cumulés.
 * {@link #vertical} en fait un Mat « sombre en bas, clair en haut ».</li>
 * </ul>
 * Les entrées peuvent être des identifiants sans propriétés (palette d'escaliers d'un toit par exemple).
 */
public final class Palette implements Mat {
    private final List<String> states = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();
    private double[] cum;
    private int mainIdx;
    private final Noise noise;
    private final long seed;
    private double period = 6, amp = 0.25, jitter = 0.0;

    public Palette(long seed) {
        this.seed = seed;
        this.noise = new Noise(seed);
    }

    /** Palette d'un seul bloc. */
    public static Palette of(String state) {
        return new Palette(state.hashCode()).add(state, 1);
    }

    /** Raccourci : of(graine, "a", 70, "b", 20, "c", 10). */
    public static Palette of(long seed, Object... stateWeight) {
        Palette p = new Palette(seed);
        for (int i = 0; i + 1 < stateWeight.length; i += 2)
            p.add((String) stateWeight[i], ((Number) stateWeight[i + 1]).doubleValue());
        return p;
    }

    /** Ajoute une entrée (dans l'ordre clair → sombre). */
    public Palette add(String state, double weight) {
        states.add(B.id(state));
        weights.add(weight);
        cum = null;
        return this;
    }

    /** Taille des grappes / amas en blocs (4-8 conseillé). */
    public Palette period(double p) { this.period = Math.max(1, p); return this; }
    /** Amplitude du décalage de dégradé (0.25 conseillé). */
    public Palette amp(double a) { this.amp = a; return this; }
    /** Petit bruit par bloc ajouté au dégradé (0 par défaut ; ≤ 0.1, il fait du poivre et sel aux frontières). */
    public Palette jitter(double j) { this.jitter = j; return this; }

    public int size() { return states.size(); }
    public String get(int i) { return states.get(i); }
    public String first() { return states.get(0); }
    public String last() { return states.get(states.size() - 1); }

    private void prepare() {
        if (cum != null) return;
        if (states.isEmpty()) throw new IllegalStateException("Palette vide");
        double tot = 0;
        for (double w : weights) tot += w;
        cum = new double[states.size()];
        double c = 0;
        double best = -1;
        for (int i = 0; i < cum.length; i++) {
            c += weights.get(i) / tot;
            cum[i] = c;
            if (weights.get(i) > best) { best = weights.get(i); mainIdx = i; }
        }
        cum[cum.length - 1] = 1.0;
        // variantes (hors principal) de la plus rare à la plus fréquente, part corrigée des recouvrements
        Integer[] o = new Integer[states.size()];
        for (int i = 0; i < o.length; i++) o[i] = i;
        java.util.Arrays.sort(o, (a, b) -> Double.compare(weights.get(a), weights.get(b)));
        order = new int[o.length - 1];
        share = new double[o.length - 1];
        double taken = 0;
        int k = 0;
        for (Integer i : o) {
            if (i == mainIdx) continue;
            double w = weights.get(i) / tot;
            order[k] = i;
            share[k] = w / Math.max(1e-6, 1 - taken);
            taken += w;
            k++;
        }
    }

    private int[] order;
    private double[] share;

    private String byCum(double v) {
        for (int i = 0; i < cum.length; i++) if (v < cum[i]) return states.get(i);
        return states.get(cum.length - 1);
    }

    /**
     * Dégradé : t (0 = clair, 1 = sombre) + bruit 3D basse fréquence → entrée correspondante des poids cumulés.
     * Avec t constant, seules les entrées autour de t sortent (c'est voulu : t pilote, le bruit fait les amas).
     */
    public String pick(int x, int y, int z, double t) {
        prepare();
        double u = noise.uniform3(x / period, y / period, z / period);
        double v = t + amp * (2 * u - 1);
        if (jitter > 0) v += jitter * (2 * Noise.hash(seed, x, y, z) - 1);
        return byCum(Math.max(0, Math.min(0.999999, v)));
    }

    /**
     * Bloc principal (le plus lourd) + variantes en grappes. Chaque variante a son propre canal de bruit :
     * elle sort là où ce bruit dépasse son quantile (proportion ≈ son poids). Mélange local faible.
     */
    public String clustered(int x, int y, int z) {
        prepare();
        if (states.size() == 1) return states.get(0);
        for (int k = 0; k < order.length; k++) {
            int i = order[k];
            double u = noise.uniform3((x + 101.3 * (i + 1)) / period, (y - 57.1 * (i + 1)) / period,
                    (z + 23.9 * (i + 1)) / period);
            if (u > 1 - share[k]) return states.get(i);
        }
        return states.get(mainIdx);
    }

    @Override
    public String at(int x, int y, int z) { return clustered(x, y, z); }

    /** Mat en dégradé vertical : sombre (fin de liste) en y0, clair (début) en y1. */
    public Mat vertical(int y0, int y1) {
        return (x, y, z) -> pick(x, y, z, 1.0 - Math.max(0, Math.min(1, (y - y0) / (double) Math.max(1, y1 - y0))));
    }

    /** Mat en dégradé selon une fonction t(x,y,z) quelconque (distance au point focal, exposition…). */
    public Mat gradient(TField t) {
        return (x, y, z) -> pick(x, y, z, t.t(x, y, z));
    }

    @FunctionalInterface
    public interface TField { double t(int x, int y, int z); }
}
