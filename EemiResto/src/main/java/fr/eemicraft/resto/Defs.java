package fr.eemicraft.resto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu du jeu (sans Bukkit) : plats, droppers, ameliorateurs, achats, plats secrets.
 * Les coordonnees sont LOCALES (voir {@link Geo}). Les prix sont calcules par {@link Econ}.
 */
public final class Defs {
    private Defs() {
    }

    /** Plat : id (= nom du modele eemicraft:resto/&lt;id&gt;), nom affiche, objet vanilla de repli. */
    public record Dish(String id, String name, String fallback) {
    }

    public static final Map<String, Dish> DISHES = new HashMap<>();

    static {
        for (Dish d : new Dish[]{
                new Dish("croque", "Croque-monsieur", "BREAD"),
                new Dish("frites", "Cornet de frites", "BAKED_POTATO"),
                new Dish("burger", "Burger maison", "COOKED_BEEF"),
                new Dish("pizza", "Pizza", "PUMPKIN_PIE"),
                new Dish("ramen", "Bol de ramen", "MUSHROOM_STEW"),
                new Dish("salade", "Salade composée", "BEETROOT_SOUP"),
                new Dish("sushi", "Plateau de sushis", "COOKED_SALMON"),
                new Dish("gateau", "Gâteau au chocolat", "CAKE"),
                new Dish("glace", "Coupe glacée", "SNOWBALL"),
                new Dish("brownie_mehdi", "Brownie magique de Mehdi", "COOKIE"),
                new Dish("bk_esteban", "BK d'Esteban", "COOKED_BEEF"),
                new Dish("saucisse_dylan", "Saucisse de veau de Dylan", "COOKED_PORKCHOP"),
                new Dish("sushi_mathilde", "Sushi de Mathilde", "TROPICAL_FISH"),
                new Dish("ptit_wrap", "Le p'tit wrap", "BREAD")}) {
            DISHES.put(d.id(), d);
        }
    }

    /** Ligne de production (tapis). path = cases locales {u, v} du depart a la vente. */
    public record Line(int id, int[][] path, int saleU, int saleV) {
    }

    public static final Line LINE_A = new Line(0, path(new int[]{18, 24}, new int[]{6, 24}, new int[]{6, 17}), 6, 17);
    public static final Line LINE_B = new Line(1, path(new int[]{18, 20}, new int[]{9, 20}, new int[]{9, 17}), 9, 17);
    public static final Line[] LINES = {LINE_A, LINE_B};

    private static int[][] path(int[]... corners) {
        List<int[]> l = new ArrayList<>();
        l.add(corners[0]);
        for (int i = 1; i < corners.length; i++) {
            int[] a = corners[i - 1], b = corners[i];
            int du = Integer.signum(b[0] - a[0]), dv = Integer.signum(b[1] - a[1]);
            int u = a[0], v = a[1];
            while (u != b[0] || v != b[1]) {
                u += du;
                v += dv;
                l.add(new int[]{u, v});
            }
        }
        return l.toArray(new int[0][]);
    }

    /** Index dans le chemin de la case (u, v), ou -1. */
    public static int pathIndex(Line l, int u, int v) {
        for (int i = 0; i < l.path().length; i++) {
            if (l.path()[i][0] == u && l.path()[i][1] == v) return i;
        }
        return -1;
    }

    /** Dropper (appareil de cuisine) : pose en (u, v), fait tomber son plat sur la case (u, v-1) de sa ligne. */
    public record Dropper(String purchase, String dish, int line, int u, int v, double interval, double value) {
    }

    public static final Dropper[] DROPPERS = {
            new Dropper("four_pain", "croque", 0, 17, 25, 4.0, 2),
            new Dropper("friteuse", "frites", 0, 15, 25, 4.0, 5),
            new Dropper("grill", "burger", 0, 13, 25, 4.5, 12),
            new Dropper("four_pizza", "pizza", 0, 11, 25, 5.0, 30),
            new Dropper("marmite", "ramen", 0, 9, 25, 5.0, 75),
            new Dropper("salades", "salade", 1, 17, 21, 4.0, 180),
            new Dropper("sushi", "sushi", 1, 15, 21, 4.5, 450),
            new Dropper("patisserie", "gateau", 1, 13, 21, 5.0, 1100),
            new Dropper("glacier", "glace", 1, 11, 21, 4.0, 2600),
    };

    /** Ameliorateur : arche au-dessus de la case (u, v) du tapis, multiplie la valeur des plats qui passent. */
    public record Upgrader(String purchase, int line, int u, int v, boolean column, double mult, String label) {
    }

    public static final Upgrader[] UPGRADERS = {
            new Upgrader("epices", 0, 6, 23, true, 1.5, "Épices"),
            new Upgrader("sauce", 0, 6, 21, true, 1.5, "Sauce maison"),
            new Upgrader("emballage", 0, 6, 19, true, 2.0, "Emballage"),
            new Upgrader("presentation", 1, 10, 20, false, 2.0, "Présentation"),
            new Upgrader("dressage", 1, 9, 18, true, 1.5, "Dressage"),
    };

    /** Achat (bouton au sol). auto = donne a la reclamation. */
    public record Purchase(String id, String name, String desc, boolean auto) {
    }

    public static final List<Purchase> PURCHASES = List.of(
            new Purchase("sol_depart", "Dalle de départ", "Le sol de ta future cuisine.", true),
            new Purchase("tapis_a", "Tapis roulant", "Amène les plats au comptoir.", true),
            new Purchase("comptoir", "Comptoir et caisse", "Les plats y sont vendus.", true),
            new Purchase("four_pain", "Four à croque", "Premier appareil : des croque-monsieur.", true),
            new Purchase("friteuse", "Friteuse", "Des frites bien dorées.", false),
            new Purchase("sol_salle", "Parquet de la salle", "Un vrai sol pour tes clients.", false),
            new Purchase("epices", "Épices", "Améliorateur : valeur ×1,5.", false),
            new Purchase("mur_ouest", "Mur ouest", "Ton resto prend forme.", false),
            new Purchase("grill", "Grill à burgers", "Des burgers maison.", false),
            new Purchase("mur_est", "Mur est", "Encore un mur.", false),
            new Purchase("sauce", "Sauce maison", "Améliorateur : valeur ×1,5.", false),
            new Purchase("mur_fond", "Mur du fond", "La cuisine est fermée.", false),
            new Purchase("lampes", "Lampes de cuisine", "On y voit plus clair.", false),
            new Purchase("four_pizza", "Four à pizza", "Pizzas au feu de bois.", false),
            new Purchase("facade", "Façade et porte", "La façade sur la rue.", false),
            new Purchase("commis1", "Commis", "Les appareils vont 15 % plus vite.", false),
            new Purchase("tables1", "Tables de la salle", "Des tables pour l'ambiance.", false),
            new Purchase("toit", "Toit", "Un toit, enfin.", false),
            new Purchase("emballage", "Emballage", "Améliorateur : valeur ×2.", false),
            new Purchase("marmite", "Marmite à ramen", "Des bols de ramen.", false),
            new Purchase("enseigne", "Enseigne", "Ton nom au-dessus de la porte.", false),
            new Purchase("tapis1", "Tapis rapide I", "Plats livrés chauds : +20 %.", false),
            new Purchase("tables2", "Tables du fond", "Encore plus de couverts.", false),
            new Purchase("carte1", "Nouvelle carte", "Tous les plats : +25 %.", false),
            new Purchase("tapis_b", "Deuxième tapis", "Une deuxième ligne de production.", false),
            new Purchase("salades", "Bar à salades", "Des salades composées.", false),
            new Purchase("terrasse", "Terrasse", "Une terrasse en bois.", false),
            new Purchase("commis2", "Commis II", "Les appareils vont encore 15 % plus vite.", false),
            new Purchase("sushi", "Machine à sushis", "Des plateaux de sushis.", false),
            new Purchase("lustres", "Lustres", "De la lumière dans la salle.", false),
            new Purchase("presentation", "Présentation du chef", "Améliorateur : valeur ×2.", false),
            new Purchase("carte2", "Carte gourmande", "Tous les plats : +25 %.", false),
            new Purchase("patisserie", "Pâtisserie", "Des gâteaux au chocolat.", false),
            new Purchase("auvent", "Auvent", "Un auvent rayé au-dessus de la porte.", false),
            new Purchase("plantes", "Plantes vertes", "Un peu de verdure.", false),
            new Purchase("tapis2", "Tapis rapide II", "Plats livrés chauds : +20 %.", false),
            new Purchase("glacier", "Glacier", "Des coupes glacées.", false),
            new Purchase("dressage", "Dressage", "Améliorateur : valeur ×1,5.", false),
            new Purchase("commis3", "Commis III", "Les appareils vont encore 15 % plus vite.", false),
            new Purchase("parasols", "Parasols", "Des parasols sur la terrasse.", false),
            new Purchase("carte3", "Carte étoilée", "Tous les plats : +25 %.", false),
            new Purchase("jardin", "Jardin", "Un jardin autour du resto.", false),
            new Purchase("fontaine", "Fontaines", "Deux fontaines devant l'entrée.", false),
            new Purchase("tapis3", "Tapis rapide III", "Plats livrés chauds : +20 %.", false),
            new Purchase("etoile", "Étoile du guide", "La consécration : ton resto est complet.", false));

    public static final Map<String, Integer> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < PURCHASES.size(); i++) INDEX.put(PURCHASES.get(i).id(), i);
    }

    /** Achats qui exigent un autre achat en plus de la fenetre de progression. */
    public static final Map<String, String> NEEDS = Map.of("salades", "tapis_b");

    /** Nombre d'achats precedents laisses en arriere : un achat i s'affiche quand tous les achats &lt; i-WINDOW sont faits. */
    public static final int WINDOW = 2;

    /** Emplacements des boutons d'achat (allee centrale de la salle). */
    public static final int[][] SLOTS = {{10, 9}, {13, 9}, {10, 12}, {13, 12}};

    /** Collecteur (caisse) : dalle d'or dans la salle, contre le comptoir. */
    public static final int[] COLLECTOR = {12, 15};
    /** Bouton secret de la cuisine (paillasson de la reserve). */
    public static final int[] BK_PAD = {19, 25};
    /** Arche « Reclamer ce restaurant » : on passe en u 10..12, v 0..1. */
    public static final int GATE_U1 = 10, GATE_U2 = 12;

    // ------------------------------------------------------------------ plats secrets

    /** Plat secret : droppers ou il peut sortir, indice affiche tant qu'il n'est pas trouve, replique. */
    public record Secret(String dish, List<String> droppers, String hint, String line) {
    }

    public static final List<Secret> SECRETS = List.of(
            new Secret("brownie_mehdi", List.of("four_pain", "four_pizza", "patisserie"),
                    "Un ingrédient magique se cache dans la rue…",
                    "« Il est magique, ce brownie… je vois des licornes. » Mehdi approuve."),
            new Secret("bk_esteban", List.of("friteuse", "grill"),
                    "Même la cuisine a ses secrets : regarde où tu mets les pieds.",
                    "« Un BK à cette heure-ci ? » Esteban serait fier."),
            new Secret("saucisse_dylan", List.of("grill"),
                    "Le grill a ses heures de gloire : le midi.",
                    "La saucisse de veau de Dylan. La vraie, l'unique."),
            new Secret("sushi_mathilde", List.of("sushi"),
                    "Il faut renaître pour rouler ce sushi-là.",
                    "Roulé avec amour par Mathilde (avec une fraise, on ne juge pas)."),
            new Secret("ptit_wrap", List.of("salades"),
                    "Après 5 000 plats vendus, le bar à salades improvise.",
                    "« Un p'tit wrap ? » Allez, un p'tit wrap."));

    public static Secret secret(String dish) {
        for (Secret s : SECRETS) if (s.dish().equals(dish)) return s;
        return null;
    }

    public static Dropper dropper(String purchase) {
        for (Dropper d : DROPPERS) if (d.purchase().equals(purchase)) return d;
        return null;
    }

    public static Upgrader upgrader(String purchase) {
        for (Upgrader u : UPGRADERS) if (u.purchase().equals(purchase)) return u;
        return null;
    }
}
