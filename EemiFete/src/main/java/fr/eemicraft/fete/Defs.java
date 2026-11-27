package fr.eemicraft.fete;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu du Tycoon Fête foraine (sans Bukkit) : plan du parc, attractions (productrices de tickets), améliorations,
 * ordre des achats. Coordonnées LOCALES du parc (voir {@link Geo}) : u 0..47 le long de l'allée, v 0..47 vers le
 * fond ; l'allée foraine est du côté v &lt; 0. Sol en y = FLOOR, on marche en FLOOR + 1.
 */
public final class Defs {
    private Defs() {
    }

    // ------------------------------------------------------------------ plan du parc

    /** Entrée : arche u 21..26 sur la clôture (v = 0). */
    public static final int GATE_U1 = 21, GATE_U2 = 26;
    /** Caisse (dalle d'or) : on encaisse en marchant dessus. */
    public static final int[] COLLECTOR = {20, 5};
    /** Emplacements des boutons d'achat, sur l'allée centrale juste après l'entrée. */
    public static final int[][] SLOTS = {{22, 6}, {25, 6}, {22, 9}, {25, 9}};

    public static final double[] CARROUSEL = {12.5, 19.5};
    public static final int[] AUTOS = {30, 13, 44, 23};
    public static final double[] CHAISES = {11.5, 37.5};
    public static final double[] BATEAU = {36.5, 33.5};
    public static final int[] FANTOME = {37, 40, 46, 46};
    public static final double[] ROUE = {24.0, Geo.FLOOR + 14.0, 43.5, 11};
    public static final int[] TOUR = {44, 26};
    /** Toilettes publiques : bloc u 27..29, v 12..18, 3 cabines ouvertes côté allée (u = 27) ; celle du milieu (v 15) est « OCCUPÉ ». */
    public static final int[] TOILETTES = {27, 12, 29, 18};
    public static final int[] PORTE_FRANCOIS = {27, 15};
    /** Kiosque à crêpes : u 27..29, v 20..25, comptoir côté allée. */
    public static final int[] CREPES = {27, 20, 29, 25};
    /** Mini-ferme : enclos u 32..36, v 38..46. */
    public static final int[] FERME = {32, 38, 36, 46};

    /** Stands forains (7 x 5), ouverts vers la promenade de devant (v 1..3). */
    public record Stand(String purchase, String type, int u, int v) {
    }

    public static final Stand[] STANDS = {
            new Stand("peche", "peche", 1, 4),
            new Stand("barbe", "barbe", 9, 4),
            new Stand("chamboule", "chamboule", 29, 4),
            new Stand("tir", "tir", 37, 4)};

    // ------------------------------------------------------------------ attractions

    /** Attraction : achat, nom, valeur d'un tour (tickets), durée d'un tour (s). */
    public record Attraction(String purchase, String name, double value, double cycle) {
    }

    public static final Attraction[] ATTRACTIONS = {
            new Attraction("peche", "Pêche aux canards", 2, 4.0),
            new Attraction("barbe", "Barbe à papa", 5, 4.0),
            new Attraction("chamboule", "Chamboule-tout", 12, 4.5),
            new Attraction("toilettes", "Toilettes publiques", 20, 6.0),
            new Attraction("tir", "Tir à la carabine", 30, 5.0),
            new Attraction("crepes", "Crêpes", 50, 4.5),
            new Attraction("carrousel", "Carrousel", 75, 5.0),
            new Attraction("autos", "Auto-tamponneuses", 180, 4.0),
            new Attraction("chaises", "Chaises volantes", 450, 4.5),
            new Attraction("ferme", "Mini-ferme", 900, 5.0),
            new Attraction("bateau", "Bateau pirate", 1100, 5.0),
            new Attraction("fantome", "Train fantôme", 2600, 4.0),
            new Attraction("roue", "Grande roue", 6500, 5.0),
            new Attraction("tour", "Tour de chute", 16000, 4.5),
            new Attraction("montagnes", "Montagnes russes", 40000, 5.0)};

    /** Amélioration : multiplie les tickets de TOUTES les attractions. */
    public static final Map<String, Double> BOOSTS = Map.of(
            "musique", 1.5, "mascotte", 1.5, "neons", 2.0, "vip", 2.0, "arche", 1.5);
    /** Accélérateurs : les tours durent 15 % de moins. */
    public static final List<String> SPEED = List.of("vitesse1", "vitesse2", "vitesse3");

    /** Point (u, y, v) au-dessus de chaque attraction : « +tickets » à chaque tour. */
    public static final Map<String, double[]> POPUP = Map.ofEntries(
            Map.entry("peche", new double[]{4.5, Geo.FLOOR + 7.5, 6.5}),
            Map.entry("barbe", new double[]{12.5, Geo.FLOOR + 7.5, 6.5}),
            Map.entry("chamboule", new double[]{32.5, Geo.FLOOR + 7.5, 6.5}),
            Map.entry("tir", new double[]{40.5, Geo.FLOOR + 7.5, 6.5}),
            Map.entry("carrousel", new double[]{12.5, Geo.FLOOR + 13.0, 19.5}),
            Map.entry("autos", new double[]{37.5, Geo.FLOOR + 10.0, 18.5}),
            Map.entry("chaises", new double[]{11.5, Geo.FLOOR + 16.0, 37.5}),
            Map.entry("bateau", new double[]{36.5, Geo.FLOOR + 14.0, 33.5}),
            Map.entry("fantome", new double[]{41.5, Geo.FLOOR + 17.0, 40.0}),
            Map.entry("roue", new double[]{24.0, Geo.FLOOR + 27.5, 43.5}),
            Map.entry("tour", new double[]{45.0, Geo.FLOOR + 36.0, 27.0}),
            Map.entry("montagnes", new double[]{46.5, Geo.FLOOR + 27.0, 30.0}),
            Map.entry("toilettes", new double[]{28.5, Geo.FLOOR + 7.0, 15.5}),
            Map.entry("crepes", new double[]{28.5, Geo.FLOOR + 7.0, 23.0}),
            Map.entry("ferme", new double[]{34.5, Geo.FLOOR + 5.0, 42.0}));

    // ------------------------------------------------------------------ achats

    /** Achat (bouton au sol). auto = donné à la réclamation. */
    public record Purchase(String id, String name, String desc, boolean auto) {
    }

    public static final List<Purchase> PURCHASES = List.of(
            new Purchase("entree", "Entrée du parc", "La clôture, l'arche et la promenade.", true),
            new Purchase("guichet", "Guichet", "On y encaisse les tickets.", true),
            new Purchase("allee", "Allée centrale", "Du guichet jusqu'au fond du parc.", true),
            new Purchase("peche", "Pêche aux canards", "Premier stand : des canards et des lots.", true),
            new Purchase("barbe", "Barbe à papa", "Le stand le plus sucré de la fête.", false),
            new Purchase("lampions", "Lampions", "Des lampions le long de la promenade.", false),
            new Purchase("chamboule", "Chamboule-tout", "Trois balles, dix boîtes.", false),
            new Purchase("toilettes", "Toilettes publiques", "Payantes, évidemment. Une cabine est toujours occupée…", false),
            new Purchase("cloture", "Clôtures", "Le parc est fermé de tous les côtés.", false),
            new Purchase("musique", "Orgue de barbarie", "Amélioration : tickets ×1,5.", false),
            new Purchase("carrousel", "Carrousel", "Le grand classique, chevaux dorés.", false),
            new Purchase("tir", "Tir à la carabine", "Vise le centre de la cible.", false),
            new Purchase("crepes", "Crêpes", "Sucre, confiture… et pâte à tartiner ?", false),
            new Purchase("allees", "Allées", "Des allées entre les attractions.", false),
            new Purchase("ballons", "Ballons", "Des grappes de ballons colorés.", false),
            new Purchase("autos", "Auto-tamponneuses", "Six voitures, zéro règle.", false),
            new Purchase("vitesse1", "Forains I", "Les tours vont 15 % plus vite.", false),
            new Purchase("guirlandes", "Guirlandes", "Des guirlandes lumineuses partout.", false),
            new Purchase("mascotte", "Mascotte", "Amélioration : tickets ×1,5.", false),
            new Purchase("chaises", "Chaises volantes", "On s'envole en tournant.", false),
            new Purchase("ferme", "Mini-ferme", "Vaches, moutons et chèvres à caresser.", false),
            new Purchase("fleurs", "Massifs de fleurs", "Un parc plus fleuri.", false),
            new Purchase("bancs", "Bancs et poubelles", "Pour souffler entre deux manèges.", false),
            new Purchase("bateau", "Bateau pirate", "À l'abordage, jusqu'à la verticale !", false),
            new Purchase("neons", "Néons", "Amélioration : tickets ×2.", false),
            new Purchase("vitesse2", "Forains II", "Les tours vont encore 15 % plus vite.", false),
            new Purchase("fantome", "Train fantôme", "Âmes sensibles s'abstenir.", false),
            new Purchase("roue", "Grande roue", "La plus belle vue de la fête.", false),
            new Purchase("vip", "Pass VIP", "Amélioration : tickets ×2.", false),
            new Purchase("arche", "Arche lumineuse", "Amélioration : tickets ×1,5.", false),
            new Purchase("tour", "Tour de chute", "Trente mètres, puis plus rien.", false),
            new Purchase("vitesse3", "Forains III", "Les tours vont encore 15 % plus vite.", false),
            new Purchase("montagnes", "Montagnes russes", "Tout autour du parc.", false),
            new Purchase("feux", "Feu d'artifice", "La consécration : ton parc est complet.", false));

    public static final Map<String, Integer> INDEX = new HashMap<>();

    static {
        for (int i = 0; i < PURCHASES.size(); i++) INDEX.put(PURCHASES.get(i).id(), i);
    }

    /** Un achat i s'affiche quand tous les achats &lt; i - WINDOW sont faits (comme le resto). */
    public static final int WINDOW = 2;

    // ------------------------------------------------------------------ easter eggs (objets perdus de la fête)

    /** Easter egg : id, nom, indice tant qu'il n'est pas trouvé, réplique à la découverte. */
    public record Secret(String id, String name, String hint, String line) {
    }

    public static final List<Secret> SECRETS = List.of(
            new Secret("doigt_pied", "Le doigt de pied de Julian", "La pêche aux canards ramène parfois des choses… qui ont beaucoup voyagé.",
                    "Tu as pêché… le doigt de pied de Julian ?! Il paraît qu'il le met un peu partout. Rince-le avant de le lui rendre."),
            new Secret("nutella", "Le pot de Nutella de Julian", "Un pot traîne sur la place d'entrée. Julian a la mauvaise habitude d'y mettre les doigts…",
                    "La crêpe au Nutella de Julian. Ne lui demande pas d'où vient celui qu'il a sous les ongles."),
            new Secret("francois", "Les toilettes de François", "Une cabine des toilettes est toujours occupée. Frappe… avec insistance.",
                    "« C'EST OCCUPÉ ! » François sort enfin. On ne demandera pas."),
            new Secret("fluffy", "La vache Fluffy de Clarisse", "Une vache très duveteuse s'échappe parfois de la mini-ferme.",
                    "Fluffy ! La vache de Clarisse, toute douce, toute ronde."),
            new Secret("chevre", "La chèvre naine de Mathilde", "Après une renaissance, une petite chèvre grimpe là où on ne l'attend pas.",
                    "La chèvre naine de Mathilde était perchée là-haut depuis le début."));

    /** Bonus permanent de tickets par easter egg trouvé (+10 %). */
    public static final double SECRET_BONUS = 0.10;

    public static Secret secret(String id) {
        for (Secret s : SECRETS) if (s.id().equals(id)) return s;
        return null;
    }

    public static Attraction attraction(String purchase) {
        for (Attraction a : ATTRACTIONS) if (a.purchase().equals(purchase)) return a;
        return null;
    }
}
