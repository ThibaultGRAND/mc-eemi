package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import fr.eemicraft.abysse.Store.Machine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Machines des Concessions et foreuses des strates. Tout est simulé chaque seconde dans le plugin, sans charger
 * de chunk : les entonnoirs ne servent qu'à relier les machines (liaison mémorisée quand la Concession est
 * chargée) et à les remplir depuis un coffre. L'usine d'une Compagnie tourne tant qu'un membre est connecté,
 * puis {@code offline-hours} heures après la dernière déconnexion.
 */
final class Machines {

    record Recipe(String id, String name, Map<String, Integer> in, Map<String, Integer> out, double seconds, double energy) {
    }

    record Type(String id, String item, int price, List<Recipe> recipes) {
        String name() {
            return Items.def(item).name();
        }
    }

    static final Map<String, Type> TYPES = new LinkedHashMap<>();

    private static Map<String, Integer> m(Object... kv) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], (Integer) kv[i + 1]);
        return map;
    }

    private static Recipe r(String id, String name, Map<String, Integer> in, Map<String, Integer> out, double s, double e) {
        return new Recipe(id, name, in, out, s, e);
    }

    static {
        String[] metals = {"cuivre", "fer", "etain"};
        List<Recipe> crush = new ArrayList<>(), wash = new ArrayList<>(), smelt = new ArrayList<>(), refine = new ArrayList<>();
        crush.add(r("broyer_argent", "Broyer", m("minerai_argent", 1), m("poudre_argent", 2), 6, 4));
        wash.add(r("laver_argent", "Laver", m("poudre_argent", 1), m("poudre_argent_lavee", 1), 4, 2));
        // strates 4 et 5 : cobalt et endérite (broyés et lavés comme les autres, puis Four à induction / Condenseur)
        crush.add(r("broyer_cobalt", "Broyer", m("minerai_cobalt", 1), m("poudre_cobalt", 2), 8, 6));
        wash.add(r("laver_cobalt", "Laver", m("poudre_cobalt", 1), m("poudre_cobalt_lavee", 1), 5, 3));
        crush.add(r("broyer_enderite", "Broyer", m("minerai_enderite", 1), m("poudre_enderite", 2), 10, 10));
        wash.add(r("laver_enderite", "Laver", m("poudre_enderite", 1), m("poudre_enderite_lavee", 1), 6, 5));
        for (String x : metals) {
            crush.add(r("broyer_" + x, "Broyer", m("minerai_" + x, 1), m("poudre_" + x, 2), 4, 2));
            wash.add(r("laver_" + x, "Laver", m("poudre_" + x, 1), m("poudre_" + x + "_lavee", 1), 3, 1));
            refine.add(r("raffiner_" + x, "Raffiner", m("lingot_" + x, 1), m("lingot_" + x + "_pur", 1), 8, 5));
        }
        // la fonderie préfère la poudre lavée, puis la poudre, puis le minerai
        for (String x : metals) smelt.add(r("fondre_lavee_" + x, "Fondre", m("poudre_" + x + "_lavee", 1), m("lingot_" + x, 1), 5, 3));
        for (String x : metals) smelt.add(r("fondre_poudre_" + x, "Fondre", m("poudre_" + x, 2), m("lingot_" + x, 1), 5, 3));
        for (String x : metals) smelt.add(r("fondre_minerai_" + x, "Fondre", m("minerai_" + x, 1), m("lingot_" + x, 1), 6, 4));
        // bronze de la strate 1 : coulé à la fonderie avec les lingots ordinaires (l'Alliageur, en strate 2, fait mieux avec l'étain)
        smelt.add(r("couler_bronze", "Couler du bronze", m("lingot_cuivre", 4, "lingot_fer", 2), m("bronze", 1), 8, 5));

        TYPES.put("generateur", new Type("generateur", "m_generateur", 150, List.of()));
        TYPES.put("concasseur", new Type("concasseur", "m_concasseur", 600, crush));
        TYPES.put("laveur", new Type("laveur", "m_laveur", 1200, wash));
        TYPES.put("fonderie", new Type("fonderie", "m_fonderie", 250, smelt));
        TYPES.put("raffinerie", new Type("raffinerie", "m_raffinerie", 3000, refine));
        TYPES.put("alliageur", new Type("alliageur", "m_alliageur", 4000, List.of(
                r("bronze", "Bronze", m("lingot_cuivre_pur", 3, "lingot_etain_pur", 1), m("bronze", 2), 10, 8),
                r("acier", "Acier", m("lingot_fer_pur", 1, "charbon", 2), m("acier", 1), 10, 8))));
        TYPES.put("assembleur", new Type("assembleur", "m_assembleur", 1000, List.of(
                r("engrenage", "Engrenage", m("lingot_fer_pur", 2), m("engrenage", 1), 6, 4),
                r("bobine", "Bobine de cuivre", m("lingot_cuivre_pur", 2), m("bobine", 1), 6, 4),
                r("filtre", "Filtre", m("lingot_cuivre_pur", 1, "charbon", 4, "os_mineur", 2), m("filtre", 1), 8, 4),
                r("masque", "Masque filtrant", m("filtre", 2, "lingot_fer_pur", 2), m("masque", 1), 12, 6),
                r("lampe", "Lampe de mineur", m("bobine", 1, "lingot_cuivre", 2, "charbon", 2), m("lampe", 1), 10, 4),
                r("fusee", "Fusées de détresse", m("lingot_cuivre", 1, "charbon", 2), m("fusee", 2), 5, 2),
                r("kit", "Kit de réparation", m("engrenage", 1, "lingot_fer", 1), m("kit_reparation", 1), 8, 4),
                r("pioche_bronze", "Pioche en bronze", m("bronze", 3, "lingot_fer", 2), m("pioche_bronze", 1), 15, 10),
                r("epee_bronze", "Épée en bronze", m("bronze", 2, "lingot_fer", 1), m("epee_bronze", 1), 15, 10),
                r("casque_bronze", "Casque en bronze", m("bronze", 5), m("casque_bronze", 1), 15, 10),
                r("plastron_bronze", "Plastron en bronze", m("bronze", 8), m("plastron_bronze", 1), 20, 12),
                r("jambieres_bronze", "Jambières en bronze", m("bronze", 7), m("jambieres_bronze", 1), 20, 12),
                r("bottes_bronze", "Bottes en bronze", m("bronze", 4), m("bottes_bronze", 1), 15, 10),
                r("pioche_acier", "Pioche en acier", m("acier", 3, "engrenage", 2), m("pioche_acier", 1), 20, 15),
                r("epee_acier", "Épée en acier", m("acier", 2, "bronze", 1), m("epee_acier", 1), 20, 15),
                r("foreuse", "Foreuse", m("engrenage", 4, "bobine", 2, "lingot_fer_pur", 6), m("m_foreuse", 1), 30, 20),
                r("opt_vitesse", "Module de vitesse", m("engrenage", 2, "bobine", 1), m("opt_vitesse", 1), 15, 10),
                r("opt_economie", "Module d'économie", m("bobine", 2, "lingot_cuivre_pur", 2), m("opt_economie", 1), 15, 10),
                r("opt_rendement", "Module de rendement", m("bronze", 2, "engrenage", 1, "spore_lumineuse", 2), m("opt_rendement", 1), 20, 12),
                r("cellule", "Cellule d'énergie", m("bobine", 2, "lingot_etain_pur", 2), m("cellule", 1), 15, 8),
                r("dynamite", "Dynamite de mineur", m("charbon", 4, "os_mineur", 1, "lingot_cuivre", 1), m("dynamite", 3), 8, 4),
                r("plaque_blindee", "Plaque blindée", m("acier", 3, "bronze", 2), m("plaque_blindee", 1), 25, 15),
                r("moteur", "Moteur", m("engrenage", 4, "bobine", 4, "lingot_fer_pur", 2), m("moteur", 1), 40, 25),
                r("noyau_spores", "Noyau de spores", m("spore_lumineuse", 12, "glande_spores", 4, "lingot_etain_pur", 2), m("noyau_spores", 1), 45, 30),
                r("foreuse_r", "Foreuse renforcée", m("moteur", 2, "plaque_blindee", 4, "lingot_etain_pur", 10, "bobine", 6), m("m_foreuse_r", 1), 120, 80),
                r("tete_forage", "Tête de forage renforcée", m("moteur", 1, "plaque_blindee", 3, "lingot_etain_pur", 8), m("tete_forage", 1), 90, 60),
                r("quai_reception", "Quai de réception", m("plaque_blindee", 2, "moteur", 1, "lingot_cuivre_pur", 6), m("m_quai_reception", 1), 60, 30),
                r("circuit", "Circuit d'argent", m("lingot_argent_pur", 2, "bobine", 2, "lingot_cuivre_pur", 1), m("circuit", 1), 30, 25),
                r("plastron_refrigere", "Plastron réfrigéré", m("plaque_blindee", 6, "circuit", 3, "lingot_argent_pur", 4), m("plastron_refrigere", 1), 150, 100),
                r("casque_filtrant", "Casque filtrant", m("acier", 4, "filtre", 2, "lingot_etain_pur", 2), m("casque_filtrant", 1), 30, 20),
                r("plastron_acier", "Plastron en acier", m("acier", 8, "bronze", 4), m("plastron_acier", 1), 40, 25),
                r("jambieres_acier", "Jambières en acier", m("acier", 7, "bronze", 3), m("jambieres_acier", 1), 35, 22),
                r("bottes_acier", "Bottes en acier", m("acier", 4, "bronze", 2), m("bottes_acier", 1), 30, 18),
                r("epee_abyssale", "Épée abyssale", m("coeur_contremaitre", 1, "plaque_blindee", 4, "noyau_spores", 3, "acier", 6), m("epee_abyssale", 1), 180, 120),
                r("pioche_abyssale", "Pioche abyssale", m("coeur_contremaitre", 1, "moteur", 2, "plaque_blindee", 3, "noyau_spores", 2), m("pioche_abyssale", 1), 180, 120),
                r("casque_abyssal", "Casque abyssal", m("coeur_reine", 1, "plaque_blindee", 4, "noyau_spores", 3, "filtre", 4), m("casque_abyssal", 1), 180, 120),
                r("plastron_abyssal", "Plastron abyssal", m("coeur_reine", 1, "plaque_blindee", 10, "noyau_spores", 4), m("plastron_abyssal", 1), 240, 160),
                r("jambieres_abyssales", "Jambières abyssales", m("coeur_reine", 1, "plaque_blindee", 8, "noyau_spores", 4), m("jambieres_abyssales", 1), 220, 150),
                r("bottes_abyssales", "Bottes abyssales", m("coeur_reine", 1, "plaque_blindee", 5, "noyau_spores", 3), m("bottes_abyssales", 1), 180, 120))));
        TYPES.put("electrolyseur", new Type("electrolyseur", "m_electrolyseur", 15000, List.of(
                r("argent", "Électrolyse de l'argent", m("poudre_argent_lavee", 2), m("lingot_argent_pur", 1), 15, 30),
                r("combustible", "Combustible raffiné", m("soufre", 2, "charbon", 2), m("combustible", 3), 10, 10))));
        TYPES.put("generateur_thermique", new Type("generateur_thermique", "m_generateur_thermique", 12000, List.of()));
        // ---- strate 4 (La Mer de Cristal) et 5 (Le Néant)
        TYPES.put("etabli_precision", new Type("etabli_precision", "m_etabli_precision", 25000, List.of(
                r("lentille", "Lentille de cristal", m("cristal_resonant", 3, "poudre_argent_lavee", 1), m("lentille_cristal", 1), 20, 20),
                r("plaque_cobalt", "Plaque de cobalt", m("lingot_cobalt_pur", 3, "plaque_blindee", 2), m("plaque_cobalt", 1), 40, 40),
                r("bobine_resonante", "Bobine résonante", m("lingot_cobalt_pur", 2, "circuit", 2, "lentille_cristal", 1), m("bobine_resonante", 1), 45, 50),
                r("pioche_forge", "Pioche de forge", m("coeur_forgeron", 1, "lingot_argent_pur", 24, "circuit", 8, "moteur", 3, "plaque_blindee", 6), m("pioche_forge", 1), 240, 160),
                r("foreuse_3", "Foreuse à induction", m("moteur", 4, "circuit", 10, "plaque_cobalt", 4, "lingot_cobalt_pur", 16), m("m_foreuse_3", 1), 300, 200),
                r("tete_forage_3", "Tête de forage à induction", m("moteur", 2, "circuit", 6, "plaque_cobalt", 3, "lingot_cobalt_pur", 12), m("tete_forage_3", 1), 200, 140),
                r("heaume_cristal", "Heaume de cristal", m("plaque_cobalt", 4, "lentille_cristal", 4, "bobine_resonante", 1), m("heaume_cristal", 1), 240, 160),
                r("plastron_cristal", "Plastron de cristal", m("plaque_cobalt", 8, "lentille_cristal", 6, "bobine_resonante", 2), m("plastron_cristal", 1), 300, 200),
                r("jambieres_cristal", "Jambières de cristal", m("plaque_cobalt", 7, "lentille_cristal", 5, "bobine_resonante", 2), m("jambieres_cristal", 1), 280, 190),
                r("bottes_cristal", "Bottes de cristal", m("plaque_cobalt", 4, "lentille_cristal", 3, "bobine_resonante", 1), m("bottes_cristal", 1), 220, 150),
                r("epee_cristal", "Épée de cristal", m("plaque_cobalt", 4, "lentille_cristal", 6, "bobine_resonante", 2), m("epee_cristal", 1), 240, 160),
                r("pioche_cristal", "Pioche de cristal", m("coeur_cristal", 1, "plaque_cobalt", 6, "bobine_resonante", 4, "lentille_cristal", 8), m("pioche_cristal", 1), 300, 220),
                r("plaque_neant", "Plaque de néant", m("lingot_enderite_pur", 3, "plaque_cobalt", 2, "matiere_noire", 1), m("plaque_neant", 1), 60, 70),
                r("noyau_vide", "Noyau du vide", m("lingot_enderite_pur", 2, "matiere_noire", 1, "bobine_resonante", 1), m("noyau_vide", 1), 60, 70),
                r("foreuse_4", "Foreuse du Vide", m("moteur", 6, "bobine_resonante", 8, "plaque_neant", 6, "lingot_enderite_pur", 24), m("m_foreuse_4", 1), 400, 300),
                r("tete_forage_4", "Tête de forage du Vide", m("bobine_resonante", 4, "plaque_neant", 4, "noyau_vide", 2, "lingot_enderite_pur", 16), m("tete_forage_4", 1), 300, 220),
                r("casque_neant", "Casque du Néant", m("plaque_neant", 5, "noyau_vide", 2), m("casque_neant", 1), 300, 220),
                r("plastron_neant", "Plastron du Néant", m("plaque_neant", 10, "noyau_vide", 4), m("plastron_neant", 1), 400, 300),
                r("jambieres_neant", "Jambières du Néant", m("plaque_neant", 9, "noyau_vide", 3), m("jambieres_neant", 1), 360, 260),
                r("bottes_neant", "Bottes du Néant", m("plaque_neant", 5, "noyau_vide", 2), m("bottes_neant", 1), 300, 220),
                r("lame_neant", "Lame du Néant", m("coeur_cristal", 1, "plaque_neant", 5, "noyau_vide", 3), m("lame_neant", 1), 360, 260))));
        TYPES.put("four_induction", new Type("four_induction", "m_four_induction", 40000, List.of(
                r("cobalt", "Induction du cobalt", m("poudre_cobalt_lavee", 2, "combustible", 1), m("lingot_cobalt_pur", 1), 20, 45))));
        TYPES.put("condenseur", new Type("condenseur", "m_condenseur", 120000, List.of(
                r("enderite", "Condensation de l'endérite", m("poudre_enderite_lavee", 2, "lentille_cristal", 1), m("lingot_enderite_pur", 1), 30, 70),
                r("matiere_noire", "Matière noire", m("eclat_neant", 1, "lingot_cobalt_pur", 2), m("matiere_noire", 2), 40, 80),
                r("charge_vide", "Charges du vide", m("matiere_noire", 1, "combustible", 2, "essence_vide", 1), m("charge_vide", 4), 20, 30))));
        TYPES.put("reacteur", new Type("reacteur", "m_reacteur", 90000, List.of()));
        TYPES.put("serre", new Type("serre", "m_serre", 900, List.of(
                r("champignons", "Champignons", m("spore_lumineuse", 1), m("champignons", 4), 20, 3),
                r("legumes", "Légumes", m(), m("legumes", 2), 20, 3))));
        TYPES.put("cuisine", new Type("cuisine", "m_cuisine", 700, List.of(
                r("infusion_glacee", "Infusion glacée", m("champignons", 2, "legumes", 2, "spore_lumineuse", 1), m("infusion_glacee", 1), 12, 4),
                r("elixir_silence", "Élixir de silence", m("champignons", 2, "legumes", 1, "cristal_resonant", 1), m("elixir_silence", 1), 14, 6),
                r("serum_gravite", "Sérum de gravité", m("champignons", 2, "legumes", 1, "essence_vide", 1), m("serum_gravite", 1), 16, 8),
                r("soupe_spores", "Soupe anti-spores", m("champignons", 2, "glande_spores", 1), m("soupe_spores", 1), 10, 3),
                r("ragout", "Ragoût des profondeurs", m("legumes", 2, "champignons", 2), m("ragout", 1), 10, 3),
                r("cafe", "Café noir", m("legumes", 1, "charbon", 2), m("cafe", 2), 6, 2),
                r("ration", "Ration de mineur", m("legumes", 2, "charbon", 1), m("ration", 2), 8, 2))));
        TYPES.put("entrepot", new Type("entrepot", "m_entrepot", 800, List.of()));
        TYPES.put("quai_expedition", new Type("quai_expedition", "m_quai_expedition", 500, List.of()));
        TYPES.put("quai_reception", new Type("quai_reception", "m_quai_reception", 1500, List.of()));
        TYPES.put("groupeur", new Type("groupeur", "m_groupeur", 400, List.of()));
        TYPES.put("separateur", new Type("separateur", "m_separateur", 900, List.of()));
        TYPES.put("trieur", new Type("trieur", "m_trieur", 2500, List.of()));
        TYPES.put("table_foreuses", new Type("table_foreuses", "m_table_foreuses", 3500, List.of()));
        TYPES.put("foreuse", new Type("foreuse", "m_foreuse", 5000, List.of()));
    }

    /** Niveau de métier requis pour choisir une recette de l'Assembleur : recette → {métier, niveau}. */
    static final Map<String, Object[]> RECIPE_REQ = Map.ofEntries(
            Map.entry("casque_filtrant", new Object[]{"ingenieur", 4}),
            Map.entry("plaque_blindee", new Object[]{"ingenieur", 6}),
            Map.entry("quai_reception", new Object[]{"ingenieur", 8}),
            Map.entry("pioche_acier", new Object[]{"mineur", 10}),
            Map.entry("circuit", new Object[]{"ingenieur", 17}),
            Map.entry("plastron_refrigere", new Object[]{"chasseur", 12}),
            Map.entry("moteur", new Object[]{"ingenieur", 8}),
            Map.entry("noyau_spores", new Object[]{"ingenieur", 8}),
            Map.entry("foreuse_r", new Object[]{"ingenieur", 10}),
            Map.entry("tete_forage", new Object[]{"ingenieur", 10}),
            Map.entry("plastron_acier", new Object[]{"chasseur", 5}),
            Map.entry("jambieres_acier", new Object[]{"chasseur", 5}),
            Map.entry("bottes_acier", new Object[]{"chasseur", 5}),
            Map.entry("epee_abyssale", new Object[]{"chasseur", 15}),
            Map.entry("pioche_abyssale", new Object[]{"mineur", 15}),
            Map.entry("casque_abyssal", new Object[]{"ingenieur", 14}),
            Map.entry("plastron_abyssal", new Object[]{"chasseur", 15}),
            Map.entry("jambieres_abyssales", new Object[]{"chasseur", 15}),
            Map.entry("bottes_abyssales", new Object[]{"chasseur", 15}));

    /** Strate à avoir ouverte pour acheter une machine chez Margaux : l'Alliageur (acier) attend la Champignonnière. */
    static final Map<String, Integer> STRATA_UNLOCK = Map.of("alliageur", 2);
    /** Recettes des strates 4 et 5 (Établi de précision) : métier et niveau requis. */
    static final Map<String, Object[]> RECIPE_REQ_DEEP = Map.ofEntries(
            Map.entry("lentille", new Object[]{"ingenieur", 18}),
            Map.entry("plaque_cobalt", new Object[]{"ingenieur", 19}),
            Map.entry("bobine_resonante", new Object[]{"ingenieur", 20}),
            Map.entry("pioche_forge", new Object[]{"mineur", 18}),
            Map.entry("foreuse_3", new Object[]{"ingenieur", 21}),
            Map.entry("tete_forage_3", new Object[]{"ingenieur", 21}),
            Map.entry("heaume_cristal", new Object[]{"ingenieur", 21}),
            Map.entry("plastron_cristal", new Object[]{"chasseur", 18}),
            Map.entry("jambieres_cristal", new Object[]{"chasseur", 18}),
            Map.entry("bottes_cristal", new Object[]{"chasseur", 18}),
            Map.entry("epee_cristal", new Object[]{"chasseur", 20}),
            Map.entry("pioche_cristal", new Object[]{"mineur", 22}),
            Map.entry("plaque_neant", new Object[]{"ingenieur", 24}),
            Map.entry("noyau_vide", new Object[]{"ingenieur", 25}),
            Map.entry("foreuse_4", new Object[]{"ingenieur", 26}),
            Map.entry("tete_forage_4", new Object[]{"ingenieur", 26}),
            Map.entry("casque_neant", new Object[]{"ingenieur", 26}),
            Map.entry("plastron_neant", new Object[]{"chasseur", 24}),
            Map.entry("jambieres_neant", new Object[]{"chasseur", 24}),
            Map.entry("bottes_neant", new Object[]{"chasseur", 24}),
            Map.entry("lame_neant", new Object[]{"chasseur", 25}));

    /** Niveau de métier requis pour une recette (Assembleur ou Établi de précision), ou null. */
    static Object[] req(String recipe) {
        Object[] r = RECIPE_REQ.get(recipe);
        return r != null ? r : RECIPE_REQ_DEEP.get(recipe);
    }

    /** Machines où l'on choisit la recette à fabriquer (les autres prennent la première possible). */
    static boolean chooses(String type) {
        return type.equals("assembleur") || type.equals("etabli_precision");
    }

    /** Niveau d'Ingénieur pour acheter une machine de logistique (les autres : aucun). */
    static final Map<String, Integer> UNLOCK = Map.of("groupeur", 5, "separateur", 10, "trieur", 15, "electrolyseur", 16, "generateur_thermique", 16,
            "etabli_precision", 18, "four_induction", 20, "condenseur", 24, "reacteur", 24);

    /** Machines qui ne transforment rien : ce qui arrive repart tel quel. */
    static boolean passThrough(String type) {
        return type.equals("entrepot") || type.equals("groupeur") || type.equals("separateur") || type.equals("trieur");
    }

    static boolean multiOut(String type) {
        return type.equals("separateur") || type.equals("trieur");
    }

    /** Une sortie d'un séparateur ou d'un trieur : machine d'arrivée, ligne de convoyeurs, côté de départ. */
    record Out(String target, List<int[]> belt, BlockFace face) {
    }

    static String faceName(BlockFace f) {
        return switch (f) {
            case NORTH -> "Nord";
            case EAST -> "Est";
            case SOUTH -> "Sud";
            case WEST -> "Ouest";
            default -> "Dessous";
        };
    }

    static Type typeOfItem(String itemId) {
        for (Type t : TYPES.values()) if (t.item().equals(itemId)) return t;
        return null;
    }

    private final EemiAbysse plugin;
    private final Map<String, UUID> labels = new HashMap<>();
    /** Habillage des machines posées : un cube du pack (item_display) un peu plus grand que le bloc vanilla. */
    private final Map<String, UUID> skins = new HashMap<>();
    private org.bukkit.NamespacedKey labelKey;

    Machines(EemiAbysse plugin) {
        this.plugin = plugin;
        labelKey = new org.bukkit.NamespacedKey(plugin, "machine_label");
    }

    private int inCap(Machine m) {
        return switch (m.type) {
            case "entrepot" -> plugin.getConfig().getInt("machines.entrepot", 2000);
            case "generateur", "generateur_thermique", "reacteur" -> 64;
            case "groupeur", "separateur", "trieur" -> 64;
            default -> 128;
        };
    }

    private static final int OUT_CAP = 256, RECEPTION_CAP = 512;

    // ------------------------------------------------------------------ simulation (chaque seconde)

    void tick() {
        long now = System.currentTimeMillis();
        long offline = (long) (plugin.getConfig().getDouble("offline-hours", 12) * 3_600_000L);
        for (Company c : plugin.store.companies.values()) {
            boolean online = c.online();
            if (online) {
                c.lastActive = now;
                if (c.sleepStart > 0 && c.sleepEnd == 0) c.sleepEnd = now;
            } else if (now - c.lastActive > offline) {
                // mise en veille : notée pour le bilan d'absence
                if (c.sleepStart == 0 || c.sleepEnd > 0) {
                    c.sleepStart = c.lastActive + offline;
                    c.sleepEnd = 0;
                }
                for (Machine m : c.machines.values()) m.state = "§8En veille (Compagnie absente)";
                continue;
            }
            tickCompany(c);
        }
    }

    double speed(Company c) {
        int best = 0;
        for (UUID u : c.members) best = Math.max(best, plugin.quests.level(plugin.store.player(u), "ingenieur"));
        return 1 + 0.004 * best;
    }

    double energyCap(Company c) {
        double cap = 0;
        for (Machine m : c.machines.values())
            if (m.type.equals("generateur")) cap += 1000 + 500 * count(m, "cellule");
            else if (m.type.equals("generateur_thermique")) cap += 3000 + 500 * count(m, "cellule");
            else if (m.type.equals("reacteur")) cap += 10000 + 500 * count(m, "cellule");
        return cap;
    }

    private void tickCompany(Company c) {
        double speed = speed(c), cap = energyCap(c);
        c.energy = Math.min(c.energy, cap);
        for (Machine m : c.machines.values()) {
            switch (m.type) {
                case "generateur" -> {
                    if (m.left <= 0 && m.in.getOrDefault("charbon", 0) > 0 && c.energy < cap) {
                        remove(m.in, "charbon", 1);
                        m.left = 10;
                    }
                    if (m.left > 0) {
                        c.energy = Math.min(cap, c.energy + 4);
                        m.left -= 1;
                        m.state = "§aEn marche §7(" + m.in.getOrDefault("charbon", 0) + " charbon)";
                    } else m.state = c.energy >= cap ? "§7Réserve pleine" : "§cPas de charbon";
                }
                case "generateur_thermique" -> {
                    if (m.left <= 0 && m.in.getOrDefault("combustible", 0) > 0 && c.energy < cap) {
                        remove(m.in, "combustible", 1);
                        m.left = 30;
                    }
                    if (m.left > 0) {
                        c.energy = Math.min(cap, c.energy + 12);
                        m.left -= 1;
                        m.state = "§aEn marche §7(" + m.in.getOrDefault("combustible", 0) + " combustible)";
                    } else m.state = c.energy >= cap ? "§7Réserve pleine" : "§cPas de combustible";
                }
                case "reacteur" -> {
                    if (m.left <= 0 && m.in.getOrDefault("charge_vide", 0) > 0 && c.energy < cap) {
                        remove(m.in, "charge_vide", 1);
                        m.left = 60;
                    }
                    if (m.left > 0) {
                        c.energy = Math.min(cap, c.energy + 40);
                        m.left -= 1;
                        m.state = "§aEn marche §7(" + m.in.getOrDefault("charge_vide", 0) + " charges)";
                    } else m.state = c.energy >= cap ? "§7Réserve pleine" : "§cPas de charge du vide";
                }
                case "quai_expedition" -> {
                    double gain = 0;
                    for (Map.Entry<String, Integer> e : new ArrayList<>(m.in.entrySet())) gain += plugin.bourse.sell(c, e.getKey(), e.getValue());
                    m.in.clear();
                    c.autoSold += gain;
                    if (gain > 0) m.state = "§aVendu " + Txt.money(gain);
                    else if (m.state.isEmpty() || m.state.startsWith("§aVendu")) m.state = "§7En attente de marchandise";
                }
                case "quai_reception" -> {
                    int room = RECEPTION_CAP - m.count(m.out);
                    // un quai qui a ses foreuses attitrées ne puise pas dans le monte-charge commun
                    boolean own = false;
                    for (Machine x : c.machines.values()) if (m.id.equals(x.dest)) own = true;
                    if (own) room = 0;
                    for (Map.Entry<String, Integer> e : new ArrayList<>(c.waiting.entrySet())) {
                        if (room <= 0) break;
                        int t = Math.min(room, e.getValue());
                        m.out.merge(e.getKey(), t, Integer::sum);
                        remove(c.waiting, e.getKey(), t);
                        room -= t;
                    }
                    m.state = m.count(m.out) >= RECEPTION_CAP ? "§cPlein" : "§7" + m.count(m.out) + " / " + RECEPTION_CAP;
                }
                case "foreuse" -> drill(c, m, speed);
                case "table_foreuses" -> {
                    long n = c.machines.values().stream().filter(x -> x.type.equals("foreuse")).count();
                    m.state = "§7" + n + " foreuse" + (n > 1 ? "s" : "");
                }
                case "entrepot" -> m.state = "§7" + m.count(m.in) + " / " + inCap(m);
                case "groupeur", "separateur", "trieur" -> m.state = multiOut(m.type) && (m.outs == null || m.outs.isEmpty())
                        ? "§cAucune sortie" : "§7" + m.count(m.in) + " en transit";
                default -> process(c, m, speed);
            }
        }
        // transferts par les convoyeurs (8/s) et les entonnoirs (4/s)
        int hopperRate = plugin.getConfig().getInt("machines.debit", 4), beltRate = plugin.getConfig().getInt("machines.debit-convoyeur", 8);
        for (Machine m : c.machines.values()) {
            if (multiOut(m.type)) {
                spread(c, m, beltRate);
                continue;
            }
            if (m.target == null) continue;
            Machine t = c.machines.get(m.target);
            if (t == null) {
                m.target = null;
                continue;
            }
            int rate = m.belt != null ? beltRate : hopperRate;
            Map<String, Integer> src = passThrough(m.type) ? m.in : m.out;
            int moved = 0;
            String first = null;
            for (Map.Entry<String, Integer> e : new ArrayList<>(src.entrySet())) {
                if (moved >= rate) break;
                int n = Math.min(rate - moved, Math.min(e.getValue(), room(t, e.getKey())));
                if (n <= 0) continue;
                if (first == null) first = e.getKey();
                remove(src, e.getKey(), n);
                t.in.merge(e.getKey(), n, Integer::sum);
                moved += n;
            }
            if (first != null && m.belt != null) animate(m.belt, first);
        }
    }

    /**
     * Séparateur : chaque objet part vers la sortie suivante qui a de la place (tour de rôle). Trieur : vers une
     * sortie dont le filtre est cet objet, sinon vers une sortie sans filtre ; s'il n'y en a pas, il attend.
     */
    private void spread(Company c, Machine m, int rate) {
        if (m.outs == null || m.outs.isEmpty()) return;
        boolean sort = m.type.equals("trieur");
        java.util.Set<Out> animated = new java.util.HashSet<>();
        int moved = 0;
        for (Map.Entry<String, Integer> e : new ArrayList<>(m.in.entrySet())) {
            String id = e.getKey();
            int left = e.getValue();
            List<Out> cands = new ArrayList<>();
            if (sort) {
                for (Out o : m.outs) if (id.equals(m.filters.get(o.face().name().toLowerCase()))) cands.add(o);
                if (cands.isEmpty()) for (Out o : m.outs) if (!m.filters.containsKey(o.face().name().toLowerCase())) cands.add(o);
            } else cands.addAll(m.outs);
            while (left > 0 && moved < rate && !cands.isEmpty()) {
                boolean any = false;
                for (int k = 0; k < cands.size() && left > 0 && moved < rate; k++) {
                    Out o = cands.get((m.rr + k) % cands.size());
                    Machine t = c.machines.get(o.target());
                    if (t == null || room(t, id) <= 0) continue;
                    remove(m.in, id, 1);
                    t.in.merge(id, 1, Integer::sum);
                    left--;
                    moved++;
                    any = true;
                    m.rr = (m.rr + k + 1) % cands.size();
                    if (o.belt() != null && animated.add(o)) animate(o.belt(), id);
                    break;
                }
                if (!any) break;
            }
            if (moved >= rate) break;
        }
    }

    private void process(Company c, Machine m, double speed) {
        Type type = TYPES.get(m.type);
        if (type == null) return;
        if ((m.type.equals("laveur") || m.type.equals("serre")) && !m.water) {
            m.state = "§cIl faut de l'eau à côté";
            return;
        }
        if (m.running == null) {
            Recipe r = pick(m, type, c);
            if (r == null) return;
            for (Map.Entry<String, Integer> e : r.in().entrySet()) remove(m.in, e.getKey(), e.getValue());
            c.energy -= cost(m, r);
            m.running = r.id();
            m.left = r.seconds();
        }
        Recipe r = recipe(type, m.running);
        if (r == null) {
            m.running = null;
            return;
        }
        m.left -= speed * (1 + 0.5 * count(m, "opt_vitesse"));
        m.state = "§a" + r.name() + " §7" + Math.max(0, (int) Math.round(100 - m.left / r.seconds() * 100)) + " %";
        if (m.left > 0) return;
        m.running = null;
        double bonus = 0.2 * count(m, "opt_rendement");
        for (Map.Entry<String, Integer> e : r.out().entrySet()) {
            int n = e.getValue();
            if (bonus > 0 && Math.random() < bonus) n *= 2;
            m.out.merge(e.getKey(), n, Integer::sum);
            c.produced += n;
            plugin.quests.onProduce(c, e.getKey(), n);
        }
        plugin.quests.jobXpCompany(c, "ingenieur", 1);
    }

    private Recipe pick(Machine m, Type type, Company c) {
        List<Recipe> list = type.recipes();
        if (chooses(m.type)) {
            Recipe r = recipe(type, m.recipe);
            if (r == null) {
                m.state = "§eChoisis une recette";
                return null;
            }
            list = List.of(r);
        }
        boolean missingEnergy = false, full = false;
        for (Recipe r : list) {
            if (!has(m.in, r.in())) continue;
            int outCount = 0;
            for (int v : r.out().values()) outCount += v;
            if (m.count(m.out) + outCount > OUT_CAP) {
                full = true;
                continue;
            }
            if (c.energy < cost(m, r)) {
                missingEnergy = true;
                continue;
            }
            return r;
        }
        m.state = full ? "§cSortie pleine" : missingEnergy ? "§cPas assez d'énergie" : "§7En attente";
        return null;
    }

    private static Recipe recipe(Type t, String id) {
        if (id == null) return null;
        for (Recipe r : t.recipes()) if (r.id().equals(id)) return r;
        return null;
    }

    private void drill(Company c, Machine m, double speed) {
        if (m.durability <= 0) {
            m.state = "§cEn panne - kit de réparation";
            return;
        }
        if (m.ore == null) return;
        double wear = (m.tier >= 4 ? 1 / 3.0 : m.tier == 3 ? 0.4 : m.tier == 2 ? 0.5 : 1.0) / 180;
        World w = Bukkit.getWorld(m.world);
        if (w != null && w.isChunkLoaded(m.x >> 4, m.z >> 4)) {
            for (Entity e : w.getNearbyEntities(new Location(w, m.x + 0.5, m.y, m.z + 0.5), 10, 6, 10))
                if (plugin.mobs.isAbysseMob(e)) {
                    wear *= 4;
                    break;
                }
        }
        m.durability = Math.max(0, m.durability - wear);
        Machine rec = m.dest == null ? null : c.machines.get(m.dest);
        if (m.dest != null && (rec == null || !rec.type.equals("quai_reception"))) {
            m.dest = null;
            rec = null;
        }
        if (rec != null) {
            if (rec.count(rec.out) >= RECEPTION_CAP) {
                m.state = "§cSon quai de réception est plein";
                return;
            }
        } else {
            int waiting = 0;
            for (int v : c.waiting.values()) waiting += v;
            if (waiting >= 2000) {
                m.state = "§cLe monte-charge est plein";
                return;
            }
        }
        m.left += speed * (1 + 0.5 * count(m, "opt_vitesse")) * (m.tier >= 4 ? 2.5 : m.tier == 3 ? 2 : m.tier == 2 ? 1.5 : 1);
        double every = plugin.getConfig().getDouble("machines.foreuse-secondes", 20);
        if (m.left >= every) {
            m.left -= every;
            if (rec != null) rec.out.merge(m.ore, 1, Integer::sum);
            else c.waiting.merge(m.ore, 1, Integer::sum);
            c.produced++;
        }
        m.state = "§a" + Bourse.strip(Items.def(m.ore).name()) + " §7- usure " + (int) Math.ceil(m.durability) + " %" + (rec != null ? " §8→ " + where(rec) : "");
    }

    /** Repose le bloc des foreuses d'une strate qui vient d'être (re)chargée : après une remise à neuf il a disparu. */
    void restoreDrills(World w) {
        int n = 0;
        for (Company c : plugin.store.companies.values())
            for (Machine m : c.machines.values()) {
                if (!m.type.equals("foreuse") || !m.world.equals(w.getName())) continue;
                Block b = w.getBlockAt(m.x, m.y, m.z);
                if (b.getType() != Items.def(TYPES.get("foreuse").item()).mat()) {
                    b.setType(Items.def(TYPES.get("foreuse").item()).mat(), false);
                    n++;
                }
            }
        if (n > 0) plugin.getLogger().info(n + " foreuse(s) reposee(s) dans " + w.getName() + ".");
    }

    // ------------------------------------------------------------------ modules

    static final java.util.Set<String> MODULES = java.util.Set.of("opt_vitesse", "opt_economie", "opt_rendement", "cellule");

    static int count(Machine m, String module) {
        int n = 0;
        for (String s : m.modules) if (s.equals(module)) n++;
        return n;
    }

    /** Énergie d'une recette avec les modules de la machine. */
    static double cost(Machine m, Recipe r) {
        double f = (1 + 0.5 * count(m, "opt_vitesse")) * (1 - 0.4 * count(m, "opt_economie"));
        return r.energy() * Math.max(0.2, f);
    }

    /** Places de modules : 4 cellules dans un générateur, 2 modules dans une machine qui produit, aucune ailleurs. */
    static int moduleSlots(Machine m) {
        if (m.type.equals("generateur") || m.type.equals("generateur_thermique") || m.type.equals("reacteur")) return 4;
        Type t = TYPES.get(m.type);
        return t != null && !t.recipes().isEmpty() || m.type.equals("foreuse") ? 2 : 0;
    }

    static boolean fitsModule(Machine m, String id) {
        if (!MODULES.contains(id) || m.modules.size() >= moduleSlots(m)) return false;
        if (m.type.equals("generateur") || m.type.equals("generateur_thermique") || m.type.equals("reacteur")) return id.equals("cellule");
        if (id.equals("cellule")) return false;
        return !m.type.equals("foreuse") || id.equals("opt_vitesse");
    }

    /** Place libre pour cet objet dans l'entrée de la machine (0 si elle ne l'accepte pas). */
    int room(Machine m, String item) {
        if (!accepts(m, item)) return 0;
        // fabrication et générateurs : plafond par objet (un ingrédient qui arrive en masse ne bloque plus les autres)
        if (perItemCap(m.type)) return Math.max(0, itemCap(m, item) - m.in.getOrDefault(item, 0));
        return Math.max(0, inCap(m) - m.count(m.in));
    }

    /** Machines dont l'entrée est plafonnée par objet : celles qui fabriquent, et les générateurs (combustible). */
    static boolean perItemCap(String type) {
        if (type.equals("generateur") || type.equals("generateur_thermique") || type.equals("reacteur")) return true;
        Type t = TYPES.get(type);
        return t != null && !t.recipes().isEmpty();
    }

    /** Plafond d'un objet dans l'entrée ({@code machines.input-cap}, 64), jamais sous la quantité d'une recette. */
    int itemCap(Machine m, String item) {
        int cap = Math.max(1, plugin.getConfig().getInt("machines.input-cap", 64));
        Type t = TYPES.get(m.type);
        if (t != null) for (Recipe r : t.recipes()) cap = Math.max(cap, r.in().getOrDefault(item, 0));
        return cap;
    }

    boolean accepts(Machine m, String item) {
        Items.Def d = Items.def(item);
        if (d == null) return false;
        return switch (m.type) {
            case "generateur" -> item.equals("charbon");
            case "generateur_thermique" -> item.equals("combustible");
            case "reacteur" -> item.equals("charge_vide");
            case "entrepot", "groupeur", "separateur", "trieur" -> true;
            case "quai_expedition" -> d.sellable();
            case "quai_reception", "foreuse" -> false;
            default -> {
                Type t = TYPES.get(m.type);
                if (t == null) yield false;
                for (Recipe r : t.recipes()) {
                    if (chooses(m.type) && m.recipe != null && !r.id().equals(m.recipe)) continue;
                    if (r.in().containsKey(item)) yield true;
                }
                yield false;
            }
        };
    }

    private static boolean has(Map<String, Integer> have, Map<String, Integer> need) {
        for (Map.Entry<String, Integer> e : need.entrySet()) if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        return true;
    }

    static void remove(Map<String, Integer> map, String id, int n) {
        int v = map.getOrDefault(id, 0) - n;
        if (v <= 0) map.remove(id);
        else map.put(id, v);
    }

    // ------------------------------------------------------------------ liaisons et entonnoirs (Concession chargée)

    /** Recalcule les liaisons et l'eau des machines chargées d'une Compagnie. */
    void relink(Company c) {
        World base = plugin.worlds.base();
        if (base == null) return;
        for (Machine m : c.machines.values()) {
            if (!m.world.equals(base.getName()) || !base.isChunkLoaded(m.x >> 4, m.z >> 4)) continue;
            Block b = base.getBlockAt(m.x, m.y, m.z);
            m.target = null;
            m.belt = null;
            m.outs = null;
            if (multiOut(m.type)) {
                m.outs = collectOuts(c, m, b);
                continue;
            }
            Block pos = b.getRelative(BlockFace.DOWN);
            for (int i = 0; i < 32 && pos.getType() == Material.HOPPER; i++) {
                BlockFace f = ((Directional) pos.getBlockData()).getFacing();
                Block next = pos.getRelative(f);
                Machine t = plugin.store.machineAt(base.getName(), next.getX(), next.getY(), next.getZ());
                if (t != null && t != m && c.machines.containsKey(t.id)) {
                    m.target = t.id;
                    break;
                }
                pos = next;
            }
            if (m.target == null) followBelt(c, m, b);
            if (m.type.equals("laveur") || m.type.equals("serre")) {
                m.water = false;
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
                    Block n = b.getRelative(f);
                    BlockData bd = n.getBlockData();
                    if (n.getType() == Material.WATER || (bd instanceof Waterlogged wl && wl.isWaterlogged())) m.water = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ convoyeurs

    static final BlockFace[] FLAT = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    static boolean isBelt(Block b) {
        return b.getType() == Material.MAGENTA_GLAZED_TERRACOTTA;
    }

    /** Sens d'un convoyeur : le bloc « regarde » le joueur qui l'a posé, les objets partent à l'opposé. */
    static BlockFace beltDir(Block b) {
        return ((Directional) b.getBlockData()).getFacing().getOppositeFace();
    }

    /** Une ligne de convoyeurs part de la machine (collée à elle, dans son sens) : la suit jusqu'à une machine. */
    private void followBelt(Company c, Machine m, Block b) {
        String w = b.getWorld().getName();
        for (BlockFace f : FLAT) {
            Block pos = b.getRelative(f);
            if (!isBelt(pos) || beltDir(pos) != f) continue;
            List<int[]> path = new ArrayList<>();
            java.util.Set<Block> seen = new java.util.HashSet<>();
            while (pos != null && path.size() < 96 && seen.add(pos)) {
                path.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                Block front = pos.getRelative(beltDir(pos));
                Block next = null;
                for (Block cand : new Block[]{front, front.getRelative(BlockFace.UP), front.getRelative(BlockFace.DOWN)}) {
                    Machine t = plugin.store.machineAt(w, cand.getX(), cand.getY(), cand.getZ());
                    if (t != null && t != m && c.machines.containsKey(t.id)) {
                        m.target = t.id;
                        m.belt = path;
                        return;
                    }
                    if (next == null && isBelt(cand)) next = cand;
                }
                pos = next;
            }
        }
    }

    /** Séparateur / trieur : une sortie par ligne de convoyeurs qui part de lui (4 côtés) + l'entonnoir dessous. */
    private List<Out> collectOuts(Company c, Machine m, Block b) {
        List<Out> outs = new ArrayList<>();
        String w = b.getWorld().getName();
        for (BlockFace f : FLAT) {
            Block pos = b.getRelative(f);
            if (!isBelt(pos) || beltDir(pos) != f) continue;
            List<int[]> path = new ArrayList<>();
            java.util.Set<Block> seen = new java.util.HashSet<>();
            search:
            while (pos != null && path.size() < 96 && seen.add(pos)) {
                path.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                Block front = pos.getRelative(beltDir(pos));
                Block next = null;
                for (Block cand : new Block[]{front, front.getRelative(BlockFace.UP), front.getRelative(BlockFace.DOWN)}) {
                    Machine t = plugin.store.machineAt(w, cand.getX(), cand.getY(), cand.getZ());
                    if (t != null && t != m && c.machines.containsKey(t.id)) {
                        outs.add(new Out(t.id, path, f));
                        break search;
                    }
                    if (next == null && isBelt(cand)) next = cand;
                }
                pos = next;
            }
        }
        Block pos = b.getRelative(BlockFace.DOWN);
        for (int i = 0; i < 32 && pos.getType() == Material.HOPPER; i++) {
            Block next = pos.getRelative(((Directional) pos.getBlockData()).getFacing());
            Machine t = plugin.store.machineAt(w, next.getX(), next.getY(), next.getZ());
            if (t != null && t != m && c.machines.containsKey(t.id)) {
                outs.add(new Out(t.id, null, BlockFace.DOWN));
                break;
            }
            pos = next;
        }
        return outs;
    }

    private final java.util.Set<UUID> movers = new java.util.HashSet<>();

    /** Un objet défile sur le tapis (seulement si un joueur est assez près pour le voir). */
    private void animate(List<int[]> path, String item) {
        World w = plugin.worlds.base();
        if (w == null || path.isEmpty() || movers.size() > 300) return;
        int[] a = path.get(0);
        if (!w.isChunkLoaded(a[0] >> 4, a[2] >> 4)) return;
        Location start = new Location(w, a[0] + 0.5, a[1] + 1.15, a[2] + 0.5);
        boolean seen = false;
        for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(start) < 48 * 48) seen = true;
        if (!seen) return;
        ItemDisplay d = w.spawn(start, ItemDisplay.class, it -> {
            it.setPersistent(false);
            it.setItemStack(Items.make(item, 1));
            it.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            it.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(0.6f, 0.6f, 0.6f), new org.joml.AxisAngle4f()));
            it.setTeleportDuration(4);
            it.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
        });
        movers.add(d.getUniqueId());
        int[] step = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            step[0]++;
            if (!d.isValid() || step[0] >= path.size()) {
                d.remove();
                movers.remove(d.getUniqueId());
                task.cancel();
                return;
            }
            int[] q = path.get(step[0]);
            d.teleport(new Location(w, q[0] + 0.5, q[1] + 1.15, q[2] + 0.5));
        }, 4L, 4L);
    }

    /** Vide dans les machines les entonnoirs qui pointent vers elles (toutes les 2 s, chunks chargés seulement). */
    void pullHoppers() {
        World base = plugin.worlds.base();
        if (base == null) return;
        for (Company c : plugin.store.companies.values())
            for (Machine m : c.machines.values()) {
                if (!m.world.equals(base.getName()) || !base.isChunkLoaded(m.x >> 4, m.z >> 4)) continue;
                Block b = base.getBlockAt(m.x, m.y, m.z);
                for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    Block h = b.getRelative(f);
                    if (h.getType() != Material.HOPPER) continue;
                    if (((Directional) h.getBlockData()).getFacing() != f.getOppositeFace()) continue;
                    if (!(h.getState(false) instanceof Hopper hopper)) continue;
                    Inventory inv = hopper.getInventory();
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack it = inv.getItem(i);
                        String id = Items.id(it);
                        if (id == null) continue;
                        int n = Math.min(it.getAmount(), room(m, id));
                        if (n <= 0) continue;
                        m.in.merge(id, n, Integer::sum);
                        it.setAmount(it.getAmount() - n);
                        inv.setItem(i, it.getAmount() <= 0 ? null : it);
                    }
                }
            }
    }

    // ------------------------------------------------------------------ noms flottants

    void labels() {
        for (Company c : plugin.store.companies.values())
            for (Machine m : c.machines.values()) {
                World w = Bukkit.getWorld(m.world);
                if (w == null || !w.isChunkLoaded(m.x >> 4, m.z >> 4)) {
                    labels.remove(m.id);
                    skins.remove(m.id);
                    continue;
                }
                skin(w, m);
                Location at = new Location(w, m.x + 0.5, m.y + 1.35, m.z + 0.5);
                Entity e = single(at, TextDisplay.class, labels.get(m.id));
                String state = m.state == null || m.state.isEmpty() ? "§7..." : m.state;
                // dans les strates, on voit à qui est la foreuse
                String text = m.type.equals("foreuse")
                        ? Items.def(itemOf(m)).name() + " §8(niveau " + Math.max(1, Math.min(DRILL_TIERS, m.tier)) + "/" + DRILL_TIERS + ")\n§b" + plugin.npcs.members(c) + " §8(" + c.name + ")\n" + state
                        : TYPES.get(m.type).name() + "\n" + state;
                if (e instanceof TextDisplay td && td.isValid()) {
                    labels.put(m.id, td.getUniqueId());
                    td.text(Txt.c(text));
                    continue;
                }
                TextDisplay td = w.spawn(at, TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setViewRange(0.25f);
                    d.text(Txt.c(text));
                    d.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
                });
                labels.put(m.id, td.getUniqueId());
            }
    }

    /** Cube du pack par-dessus le bloc de la machine (échelle 1,004 pour le recouvrir, lumière prise autour). */
    private void skin(World w, Machine m) {
        Location at = new Location(w, m.x + 0.5, m.y + 0.5, m.z + 0.5);
        Entity e = single(at, ItemDisplay.class, skins.get(m.id));
        if (e != null) skins.put(m.id, e.getUniqueId());
        Block b = w.getBlockAt(m.x, m.y, m.z);
        int block = 0, sky = 0;
        for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
            Block n = b.getRelative(f);
            block = Math.max(block, n.getLightFromBlocks());
            sky = Math.max(sky, n.getLightFromSky());
        }
        if (m.type.equals("generateur")) block = 15;
        Display.Brightness light = new Display.Brightness(block, sky);
        if (e instanceof ItemDisplay d && d.isValid()) {
            d.setBrightness(light);
            return;
        }
        ItemDisplay d = w.spawn(at, ItemDisplay.class, it -> {
            it.setPersistent(false);
            it.setItemStack(Items.make(itemOf(m), 1));
            it.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            it.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(1.004f, 1.004f, 1.004f), new org.joml.AxisAngle4f()));
            it.setBrightness(light);
            it.setViewRange(0.6f);
            it.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
        });
        skins.put(m.id, d.getUniqueId());
    }

    /**
     * Le nom ou l'habillage d'une machine à cet endroit : garde celui qu'on suit (ou en adopte un, après un
     * déchargement de chunk le plugin a pu l'oublier alors qu'il existait encore) et supprime les doublons.
     */
    private <T extends Display> T single(Location at, Class<T> type, UUID tracked) {
        T keep = null;
        List<T> found = new ArrayList<>();
        for (Entity x : at.getWorld().getNearbyEntities(at, 0.3, 0.3, 0.3))
            if (type.isInstance(x) && isLabel(x) && x.isValid()) found.add(type.cast(x));
        for (T x : found) if (x.getUniqueId().equals(tracked)) keep = x;
        if (keep == null && !found.isEmpty()) keep = found.get(0);
        for (T x : found) if (x != keep) x.remove();
        return keep;
    }

    void removeLabel(Machine m) {
        for (UUID u : new UUID[]{labels.remove(m.id), skins.remove(m.id)}) {
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
    }

    boolean isLabel(Entity e) {
        return e.getPersistentDataContainer().has(labelKey);
    }

    void removeAllLabels() {
        for (UUID u : movers) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        movers.clear();
        for (Map<String, UUID> map : List.of(labels, skins)) {
            for (UUID u : map.values()) {
                Entity e = Bukkit.getEntity(u);
                if (e != null) e.remove();
            }
            map.clear();
        }
    }

    // ------------------------------------------------------------------ pose, casse, menu

    /** Le joueur pose une machine ; renvoie false (et prévient) si c'est interdit ici. */
    /** Strate à partir de laquelle une foreuse doit être renforcée. */
    static final int REINFORCED_FROM = 3;

    /** Modèle de foreuse que donne cet objet (1 normale, 2 renforcée, 3 à induction, 4 du Vide), 0 si ce n'en est pas une. */
    static int drillTier(String itemId) {
        return switch (itemId) {
            case "m_foreuse" -> 1;
            case "m_foreuse_r" -> 2;
            case "m_foreuse_3" -> 3;
            case "m_foreuse_4" -> 4;
            default -> 0;
        };
    }

    /** Modèle de foreuse minimum sur les filons de cette strate. */
    static int drillNeed(int strata) {
        return strata >= 5 ? 4 : strata == 4 ? 3 : strata >= REINFORCED_FROM ? 2 : 1;
    }

    static String drillName(int tier) {
        return tier >= 4 ? "Foreuse du Vide" : tier == 3 ? "Foreuse à induction" : tier == 2 ? "Foreuse renforcée" : "Foreuse";
    }

    /** Nombre de modèles de foreuse (normale, renforcée, à induction, du Vide). */
    static final int DRILL_TIERS = 4;

    /** Couleur du modèle de foreuse (la même que le nom de son objet). */
    static String drillColor(int tier) {
        return tier >= 4 ? "§5" : tier == 3 ? "§9" : tier == 2 ? "§c" : "§f";
    }

    /** Icône d'une foreuse dans les menus : une par modèle. */
    static Material drillIcon(int tier) {
        return tier >= 4 ? Material.CRYING_OBSIDIAN : tier == 3 ? Material.LAPIS_BLOCK : tier == 2 ? Material.IRON_BLOCK : Material.LODESTONE;
    }

    /** « Foreuse renforcée (niveau 2/4) », en couleur. */
    static String drillLevel(int tier) {
        int t = Math.max(1, Math.min(DRILL_TIERS, tier));
        return drillColor(t) + drillName(t) + " §8(niveau " + t + "/" + DRILL_TIERS + ")";
    }

    /** Objet d'une machine posée (une foreuse renforcée rend son propre objet). */
    static String itemOf(Machine m) {
        if (m.type.equals("foreuse") && m.tier >= 3) return m.tier >= 4 ? "m_foreuse_4" : "m_foreuse_3";
        return m.type.equals("foreuse") && m.tier >= 2 ? "m_foreuse_r" : TYPES.get(m.type).item();
    }

    boolean place(Player p, Block b, String itemId) {
        boolean reinforced = drillTier(itemId) >= 2;
        Type t = reinforced ? TYPES.get("foreuse") : typeOfItem(itemId);
        if (t == null) return false;
        Company c = plugin.store.company(p);
        Gen.Strata s = Gen.strata(b.getWorld());
        Machine m = new Machine(plugin.store.newMachineId());
        if (t.id().equals("foreuse")) {
            Material under = b.getRelative(BlockFace.DOWN).getType();
            String ore = s == null ? null : s.filonOre(under);
            if (ore == null) {
                p.sendMessage(Txt.PREFIX + "§cUne foreuse se pose sur un filon, dans une strate.");
                return false;
            }
            int need = drillNeed(s.n), tier = Math.max(1, drillTier(itemId));
            if (tier < need) {
                p.sendMessage(Txt.PREFIX + "§cDans la strate " + s.n + ", il faut au moins une §l" + drillName(need) + "§c"
                        + (need >= 3 ? " (Établi de précision)." : " (Assembleur)."));
                return false;
            }
            m.ore = ore;
            m.tier = tier;
        } else {
            if (!b.getWorld().equals(plugin.worlds.base()) || !plugin.worlds.inPlot(c, b.getLocation())) {
                p.sendMessage(Txt.PREFIX + "§cLes machines se posent dans ta Concession (§f/abysse base§c).");
                return false;
            }
        }
        m.type = t.id();
        m.world = b.getWorld().getName();
        m.x = b.getX();
        m.y = b.getY();
        m.z = b.getZ();
        c.machines.put(m.id, m);
        p.sendMessage(Txt.PREFIX + "§7" + t.name() + " §7posé(e). Clic droit pour l'ouvrir, §fMaj + clic droit §7pour poser un bloc contre elle.");
        plugin.guide.firstPlace(p, t.id());
        if (t.id().equals("foreuse")) {
            p.sendMessage(Txt.PREFIX + "§7Elle extrait du " + Bourse.strip(Items.def(m.ore).name()) + " §7et l'envoie à ton Quai de réception.");
            plugin.quests.onDrill(p);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> relink(c), 2L);
        return true;
    }

    /** Casse une machine : la machine et son contenu reviennent au joueur. */
    void breakMachine(Player p, Machine m) {
        Company c = plugin.store.owner(m);
        if (c != null) c.machines.remove(m.id);
        removeLabel(m);
        List<ItemStack> give = new ArrayList<>();
        give.add(Items.make(itemOf(m), 1));
        for (String mod : m.modules) give.add(Items.make(mod, 1));
        for (Map<String, Integer> buf : List.of(m.in, m.out))
            for (Map.Entry<String, Integer> e : buf.entrySet()) stacks(e.getKey(), e.getValue(), give);
        if (m.running != null) {
            Recipe r = recipe(TYPES.get(m.type), m.running);
            if (r != null) for (Map.Entry<String, Integer> e : r.in().entrySet()) stacks(e.getKey(), e.getValue(), give);
        }
        for (ItemStack it : give)
            for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
        if (c != null) {
            for (Machine o : c.machines.values()) {
                if (m.id.equals(o.target)) o.target = null;
                if (m.id.equals(o.dest)) o.dest = null;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> relink(c), 2L);
        }
    }

    static void stacks(String id, int n, List<ItemStack> into) {
        int max = Items.def(id).mat().getMaxStackSize();
        while (n > 0) {
            int t = Math.min(n, max);
            into.add(Items.make(id, t));
            n -= t;
        }
    }

    /** Position courte d'une machine (pour les quais de réception). */
    static String where(Machine m) {
        return "quai " + m.x + " " + m.y + " " + m.z;
    }

    /** Menu d'un quai de réception : clic sur une foreuse = elle envoie à ce quai (ou revient au monte-charge commun). */
    void drillLinks(Player p, Machine quai) {
        Company c = plugin.store.owner(quai);
        if (c == null) return;
        Menu menu = new Menu(6, "§cForeuses → " + where(quai));
        menu.refresh = mm -> {
            mm.clear();
            int slot = 0;
            for (Machine d : c.machines.values()) {
                if (!d.type.equals("foreuse") || slot >= 45) continue;
                Gen.Strata s = Gen.strata(d.world);
                boolean here = quai.id.equals(d.dest);
                Machine other = d.dest == null || here ? null : c.machines.get(d.dest);
                List<String> l = new ArrayList<>();
                l.add("§7Modèle : " + drillLevel(d.tier));
                l.add("§7Strate : §f" + (s == null ? d.world : s.n + " - " + s.name));
                l.add("§7Position : §f" + d.x + " " + d.y + " " + d.z);
                l.add("§7Minerai : §f" + (d.ore == null ? "?" : Bourse.strip(Items.def(d.ore).name())));
                l.add("§7Envoie vers : " + (here ? "§aCE QUAI" : other != null ? "§f" + where(other) : "§fmonte-charge commun"));
                l.add("");
                l.add(here ? "§eClic : la renvoyer au monte-charge commun" : "§eClic : l'envoyer vers ce quai");
                int n = slot + 1;
                mm.set(slot++, Txt.icon(here ? Material.LIME_CONCRETE : drillIcon(d.tier), drillColor(d.tier) + "§l" + drillName(d.tier) + " §7n°" + n, l), (pl, ty) -> {
                    d.dest = quai.id.equals(d.dest) ? null : quai.id;
                    pl.playSound(pl, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                });
            }
            if (slot == 0) mm.set(22, Txt.icon(Material.PAPER, "§7Aucune foreuse", "§7Pose une foreuse sur un filon,", "§7dans une strate."));
            mm.set(46, Txt.icon(Material.IRON_CHAIN, "§7Monte-charge commun", "§7Les foreuses sans quai choisi y", "§7envoient leur minerai ; tous les", "§7quais sans foreuse attitrée y puisent."));
            mm.nav((pl, ty) -> open(pl, quai), "Quai de réception");
        };
        menu.open(p);
    }

    /** Tête de forage (renforcée, à induction ou du Vide) sur une foreuse posée : elle passe au modèle de la tête. */
    void upgrade(Player p, Machine m, String head) {
        int to = head.equals("tete_forage_4") ? 4 : head.equals("tete_forage_3") ? 3 : 2;
        if (to == 2) {
            upgrade(p, m);
            return;
        }
        if (m.tier >= to) {
            p.sendMessage(Txt.PREFIX + "§7Cette foreuse est déjà une " + drillName(m.tier) + ".");
            return;
        }
        if (Bourse.countIn(p, head) <= 0) return;
        Bourse.take(p, head, 1);
        m.tier = to;
        removeLabel(m);
        p.sendMessage(Txt.PREFIX + "§aTa foreuse devient une §f" + drillName(to) + "§a : plus rapide, s'use moins, et va sur les filons de la strate " + (to + 1) + ".");
        p.playSound(p, Sound.BLOCK_SMITHING_TABLE_USE, 1, 0.9f);
    }

    /** Tête de forage renforcée sur une foreuse normale : elle passe au niveau 2. */
    void upgrade(Player p, Machine m) {
        if (m.tier >= 2) {
            p.sendMessage(Txt.PREFIX + "§7Cette foreuse est déjà renforcée.");
            return;
        }
        if (Bourse.countIn(p, "tete_forage") <= 0) return;
        Bourse.take(p, "tete_forage", 1);
        m.tier = 2;
        removeLabel(m);
        p.sendMessage(Txt.PREFIX + "§aForeuse renforcée : plus rapide, s'use moins, et va sur les filons des strates profondes.");
        p.playSound(p, Sound.BLOCK_SMITHING_TABLE_USE, 1, 0.9f);
    }

    void repair(Player p, Machine m) {
        if (m.durability >= 100) {
            p.sendMessage(Txt.PREFIX + "§7Cette foreuse est déjà à 100 %.");
            return;
        }
        if (Bourse.countIn(p, "kit_reparation") <= 0) {
            p.sendMessage(Txt.PREFIX + "§cIl te faut un Kit de réparation (Assembleur ou Marchand).");
            return;
        }
        Bourse.take(p, "kit_reparation", 1);
        m.durability = 100;
        p.sendMessage(Txt.PREFIX + "§aForeuse réparée.");
        p.playSound(p, Sound.BLOCK_ANVIL_USE, 1, 1.2f);
    }

    void open(Player p, Machine m) {
        if (m.type.equals("table_foreuses")) {
            drillMap(p, m);
            return;
        }
        Type t = TYPES.get(m.type);
        Menu menu = new Menu(4, t.name());
        menu.tag = m.id;
        menu.refresh = mm -> render(p, m, mm);
        menu.bottom = (pl, slot, item, click) -> deposit(pl, m, slot, item);
        menu.open(p);
    }

    private void render(Player p, Machine m, Menu menu) {
        menu.clear();
        Company c = plugin.store.owner(m);
        Type t = TYPES.get(m.type);
        List<String> info = new ArrayList<>(Txt.wrap(Bourse.strip(Items.def(t.item()).desc()), "§7"));
        info.add("");
        info.add("§7État : " + (m.state == null || m.state.isEmpty() ? "§7..." : m.state));
        if (c != null) {
            double cap = energyCap(c);
            info.add("§7Énergie : §b" + Txt.num(Math.floor(c.energy)) + " / " + Txt.num(cap) + " " + Txt.bar(cap <= 0 ? 0 : c.energy / cap, 10, "§b", "§8"));
            info.add("§7Vitesse : §fx" + Txt.num(Math.round(speed(c) * 100) / 100.0) + " §8(métier d'Ingénieur)");
        }
        if (m.type.equals("foreuse")) {
            info.add("§7Usure : " + Txt.bar(m.durability / 100, 20, "§a", "§c") + " §f" + (int) Math.ceil(m.durability) + " %");
            info.add("§7Monte-charge : §f" + (c == null ? 0 : c.waiting.values().stream().mapToInt(Integer::intValue).sum()) + " §7en attente");
        }
        menu.set(4, Items.skin(Txt.icon(Items.def(t.item()).mat(), t.name(), info), Machines.itemOf(m)));
        if (chooses(m.type)) {
            Recipe r = recipe(t, m.recipe);
            List<String> l = new ArrayList<>();
            if (r != null) {
                for (Map.Entry<String, Integer> e : r.in().entrySet()) l.add("§7- " + e.getValue() + " " + Items.def(e.getKey()).name());
                l.add("§7→ " + r.out().values().iterator().next() + " " + Items.def(r.out().keySet().iterator().next()).name());
            }
            l.add("");
            l.add("§eClic pour changer de recette");
            menu.set(0, Txt.icon(Material.KNOWLEDGE_BOOK, "§eRecette : §f" + (r == null ? "aucune" : r.name()), l), (pl, ty) -> recipes(pl, m));
        } else if (!t.recipes().isEmpty()) {
            List<String> l = new ArrayList<>();
            for (Recipe r : t.recipes()) {
                StringBuilder sb = new StringBuilder("§7");
                for (Map.Entry<String, Integer> e : r.in().entrySet()) sb.append(e.getValue()).append(" ").append(Bourse.strip(Items.def(e.getKey()).name())).append(" + ");
                if (r.in().isEmpty()) sb.append("rien (eau à côté)");
                else sb.setLength(sb.length() - 3);
                Map.Entry<String, Integer> o = r.out().entrySet().iterator().next();
                sb.append(" §8→ §f").append(o.getValue()).append(" ").append(Bourse.strip(Items.def(o.getKey()).name()));
                l.add(sb.toString());
            }
            menu.set(0, Txt.icon(Material.BOOK, "§eRecettes", l));
        }
        int slots = moduleSlots(m);
        for (int i = 0; i < slots; i++) {
            // générateur (pas de livre de recettes) : cases 0 à 3 ; autres machines : 6 et 7
            int slot = slots == 4 ? i : 6 + i;
            if (i < m.modules.size()) {
                String mod = m.modules.get(i);
                ItemStack icon = Items.make(mod, 1);
                var meta = icon.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
                lore.add(Txt.c(""));
                lore.add(Txt.c("§eClic : le reprendre"));
                meta.lore(lore);
                icon.setItemMeta(meta);
                int idx = i;
                menu.set(slot, icon, (pl, ty) -> {
                    if (idx >= m.modules.size()) return;
                    String back = m.modules.remove(idx);
                    for (ItemStack rest : pl.getInventory().addItem(Items.make(back, 1)).values()) pl.getWorld().dropItem(pl.getLocation(), rest);
                    pl.playSound(pl, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.4f);
                });
            } else
                menu.set(slot, Txt.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Place de module",
                        m.type.equals("generateur") ? List.of("§7Clique sur une Cellule d'énergie", "§7de ton sac pour l'installer.")
                                : List.of("§7Clique sur un module de ton sac", "§7(vitesse, économie, rendement)", "§7pour l'installer. Assembleur.")));
        }
        menu.set(8, Txt.icon(Material.IRON_PICKAXE, "§cRamasser la machine", "§7Elle revient dans ton sac,", "§7avec tout son contenu."), (pl, ty) -> {
            if (ty != ClickType.SHIFT_LEFT && ty != ClickType.SHIFT_RIGHT) {
                pl.sendMessage(Txt.PREFIX + "§7Maj + clic pour confirmer.");
                return;
            }
            pl.closeInventory();
            World w = Bukkit.getWorld(m.world);
            if (w != null) w.getBlockAt(m.x, m.y, m.z).setType(Material.AIR);
            breakMachine(pl, m);
        });
        // entrée (l'entrepôt n'a que ça) et sortie
        boolean store = passThrough(m.type);
        if (perItemCap(m.type))
            menu.set(9, Txt.icon(Material.LIME_STAINED_GLASS_PANE, "§aEntrée", "§7Clique sur un objet de ton sac", "§7pour le déposer ici.",
                    "§7Limite : §f" + plugin.getConfig().getInt("machines.input-cap", 64) + " §7par objet."));
        else
            menu.set(9, Txt.icon(Material.LIME_STAINED_GLASS_PANE, store ? "§aStock" : "§aEntrée", "§7Clique sur un objet de ton sac", "§7pour le déposer ici."));
        buffer(menu, m.in, 10, p, perItemCap(m.type) ? m : null);
        if (!store && !m.type.equals("generateur") && !m.type.equals("quai_expedition")) {
            menu.set(18, Txt.icon(Material.ORANGE_STAINED_GLASS_PANE, "§6Sortie", "§7Clique pour récupérer."));
            buffer(menu, m.out, 19, p);
        }
        if (multiOut(m.type)) outputs(menu, m, c);
        String link = m.target == null ? "§7aucune" : TYPES.get(c == null || c.machines.get(m.target) == null ? "entrepot" : c.machines.get(m.target).type).name();
        String via = m.target == null ? "" : m.belt != null ? " §8(convoyeur, " + m.belt.size() + " blocs)" : " §8(entonnoirs)";
        menu.set(33, Txt.icon(m.belt != null ? Material.MAGENTA_GLAZED_TERRACOTTA : Material.HOPPER, "§7Liaison : " + link + via,
                "§7Une ligne de convoyeurs (ou un entonnoir", "§7sous la machine) envoie ses produits", "§7vers la machine où elle mène.", "", "§eClic : comment faire"),
                (pl, ty) -> plugin.guide.links(pl));
        menu.set(27, Txt.icon(Material.KNOWLEDGE_BOOK, "§e? Aide", "§7La fiche de cette machine et", "§7l'explication de ce menu.", "", "§eClic pour ouvrir"),
                (pl, ty) -> plugin.guide.machine(pl, m.type));
        if (m.type.equals("foreuse"))
            menu.set(29, Txt.icon(Material.TRIPWIRE_HOOK, "§eRéparer", "§7Utilise un Kit de réparation."), (pl, ty) -> repair(pl, m));
        if (m.type.equals("quai_reception") && c != null) {
            long mine = c.machines.values().stream().filter(x -> m.id.equals(x.dest)).count();
            menu.set(29, Txt.icon(Material.LODESTONE, "§cForeuses reliées : §f" + mine,
                    "§7Choisis quelles foreuses envoient", "§7leur minerai à ce quai.", "§7Les autres passent par le", "§7monte-charge commun.", "", "§eClic pour choisir"),
                    (pl, ty) -> drillLinks(pl, m));
        }
        menu.nav((pl, ty) -> plugin.menu(pl), "Menu de l'Abysse");
        menu.fill(Txt.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
    }

    /** Table des foreuses : chaque foreuse de la Compagnie (strate, position, minerai, usure), clic = y aller. */
    void drillMap(Player p, Machine table) {
        Company c = plugin.store.owner(table);
        Menu menu = new Menu(6, "§aTable des foreuses");
        menu.refresh = mm -> {
            mm.clear();
            int slot = 0;
            List<Machine> drills = new ArrayList<>();
            if (c != null) for (Machine x : c.machines.values()) if (x.type.equals("foreuse")) drills.add(x);
            drills.sort(java.util.Comparator.comparing((Machine x) -> x.world).thenComparingInt(x -> x.x));
            for (Machine d : drills) {
                if (slot >= 45) break;
                Gen.Strata s = Gen.strata(d.world);
                List<String> l = new ArrayList<>();
                l.add("§7Modèle : " + drillLevel(d.tier));
                l.add("§7Strate : §f" + (s == null ? d.world : s.n + " - " + s.name));
                l.add("§7Position : §f" + d.x + " " + d.y + " " + d.z);
                l.add("§7Minerai : §f" + (d.ore == null ? "?" : Bourse.strip(Items.def(d.ore).name())));
                Machine q = d.dest == null || c == null ? null : c.machines.get(d.dest);
                l.add("§7Envoie vers : " + (q == null ? "§fmonte-charge commun" : "§f" + where(q)));
                l.add("§7Usure : " + Txt.bar(d.durability / 100, 10, "§a", "§c") + " §f" + (int) Math.ceil(d.durability) + " %");
                l.add("§7État : " + (d.state == null || d.state.isEmpty() ? "§7..." : d.state));
                l.add("");
                l.add("§eClic : y aller");
                // une icône par modèle ; une foreuse usée à 0 % reste signalée en rouge
                Material icon = d.durability <= 0 ? Material.REDSTONE_BLOCK : drillIcon(d.tier);
                String worn = d.durability <= 0 ? " §4(usée)" : d.durability < 25 ? " §6(presque usée)" : "";
                int n = slot + 1;
                menu.set(slot++, Txt.icon(icon, drillColor(d.tier) + "§l" + drillName(d.tier) + " §7n°" + n + worn, l), (pl, ty) -> goToDrill(pl, d));
            }
            if (drills.isEmpty())
                mm.set(22, Txt.icon(Material.PAPER, "§7Aucune foreuse", "§7Pose une foreuse sur un filon,", "§7dans une strate (Margaux ou Assembleur)."));
            int waiting = c == null ? 0 : c.waiting.values().stream().mapToInt(Integer::intValue).sum();
            mm.set(46, Txt.icon(Material.IRON_CHAIN, "§7Monte-charge", "§f" + waiting + " §7minerais en route vers", "§7ton Quai de réception."));
            mm.nav((pl, ty) -> plugin.menu(pl), "Menu de l'Abysse");
            mm.set(52, Txt.icon(Material.IRON_PICKAXE, "§cRamasser la table", "§7Maj + clic pour confirmer."), (pl, ty) -> {
                if (ty != ClickType.SHIFT_LEFT && ty != ClickType.SHIFT_RIGHT) return;
                pl.closeInventory();
                World w = Bukkit.getWorld(table.world);
                if (w != null) w.getBlockAt(table.x, table.y, table.z).setType(Material.AIR);
                breakMachine(pl, table);
            });
        };
        menu.open(p);
    }

    private void goToDrill(Player p, Machine d) {
        Gen.Strata s = Gen.strata(d.world);
        if (s == null) return;
        if (plugin.store.player(p).unlocked < s.n) {
            p.sendMessage(Txt.PREFIX + "§cTu n'as pas encore ouvert cette strate.");
            return;
        }
        World w = plugin.worlds.strata(s.n);
        if (w == null) {
            p.sendMessage(Txt.PREFIX + "§eCette strate est en train d'être remise à neuf, réessaie dans quelques secondes.");
            return;
        }
        p.closeInventory();
        p.teleport(new Location(w, d.x + 0.5, d.y + 1, d.z + 0.5, p.getLocation().getYaw(), 30));
        p.playSound(p, Sound.BLOCK_CHAIN_PLACE, 1, 0.8f);
        p.sendActionBar(Txt.c("§aTe voilà sur ta foreuse."));
    }

    /** Ligne des sorties d'un séparateur / trieur (cases 19, 21, 23, 25) ; filtre du trieur au clic. */
    private void outputs(Menu menu, Machine m, Company c) {
        int[] slots = {19, 21, 23, 25};
        menu.set(18, Txt.icon(Material.ORANGE_STAINED_GLASS_PANE, "§6Sorties",
                m.type.equals("trieur") ? List.of("§7Clic sur une sortie pour choisir", "§7l'objet qu'elle reçoit.", "§7Sans filtre : elle reçoit le reste.")
                        : List.of("§7Ce qui arrive est partagé", "§7à tour de rôle entre elles.")));
        for (int i = 0; i < 4; i++) {
            BlockFace f = FLAT[i];
            Out o = null;
            if (m.outs != null) for (Out x : m.outs) if (x.face() == f) o = x;
            String key = f.name().toLowerCase();
            String filter = m.filters.get(key);
            List<String> l = new ArrayList<>();
            Machine t = o == null || c == null ? null : c.machines.get(o.target());
            l.add(t == null ? "§8Pas de convoyeur de ce côté" : "§7Vers : " + TYPES.get(t.type).name() + " §8(" + o.belt().size() + " blocs)");
            if (m.type.equals("trieur")) {
                l.add("§7Filtre : " + (filter == null ? "§faucun (le reste)" : Items.def(filter).name()));
                l.add("");
                l.add("§eClic : choisir le filtre");
                l.add("§eClic droit : retirer le filtre");
            }
            ItemStack icon = filter != null ? Items.make(filter, 1) : new ItemStack(t == null ? Material.GRAY_DYE : Material.MAGENTA_GLAZED_TERRACOTTA);
            var meta = icon.getItemMeta();
            meta.displayName(Txt.c("§fSortie " + faceName(f)));
            meta.lore(Txt.lines(l));
            icon.setItemMeta(meta);
            menu.set(slots[i], icon, !m.type.equals("trieur") ? null : (pl, ty) -> {
                if (ty.isRightClick()) {
                    m.filters.remove(key);
                    open(pl, m);
                } else filterMenu(pl, m, key);
            });
        }
    }

    /** Choix du filtre d'une sortie du trieur : ressources, produits et nourriture de l'Abysse. */
    private void filterMenu(Player p, Machine m, String key) {
        Menu menu = new Menu(6, "§bFiltre §8- sortie " + faceName(BlockFace.valueOf(key.toUpperCase())));
        int slot = 0;
        for (Items.Def d : Items.all()) {
            boolean food = List.of("legumes", "champignons", "ration", "ragout", "soupe_spores", "cafe").contains(d.id());
            if (d.cat() != Items.Cat.RESOURCE && d.cat() != Items.Cat.PRODUCT && !food) continue;
            if (slot >= 45) break;
            menu.set(slot++, Items.make(d.id(), 1), (pl, ty) -> {
                m.filters.put(key, d.id());
                pl.sendMessage(Txt.PREFIX + "§7Sortie " + faceName(BlockFace.valueOf(key.toUpperCase())) + " : " + d.name());
                open(pl, m);
            });
        }
        menu.set(46, Txt.icon(Material.GRAY_DYE, "§7Aucun filtre", "§7La sortie reçoit tout le reste."), (pl, ty) -> {
            m.filters.remove(key);
            open(pl, m);
        });
        menu.nav((pl, ty) -> open(pl, m), "La machine");
        menu.open(p);
    }

    private void buffer(Menu menu, Map<String, Integer> buf, int start, Player p) {
        buffer(menu, buf, start, p, null);
    }

    /** {@code capped} : machine à entrée plafonnée par objet, la limite est affichée (ex. « 64/64 (plein) »). */
    private void buffer(Menu menu, Map<String, Integer> buf, int start, Player p, Machine capped) {
        int slot = start;
        for (Map.Entry<String, Integer> e : buf.entrySet()) {
            if (slot >= start + 8) break;
            String id = e.getKey();
            ItemStack icon = Items.make(id, Math.max(1, Math.min(64, e.getValue())));
            var meta = icon.getItemMeta();
            String qty = "§7Quantité : §f" + e.getValue();
            if (capped != null) {
                int cap = itemCap(capped, id);
                qty = "§7Quantité : §f" + e.getValue() + "/" + cap + (e.getValue() >= cap ? " §c(plein)" : "");
            }
            meta.lore(Txt.lines(List.of(qty, "§eClic §7: en prendre une pile", "§eClic droit §7: tout prendre")));
            icon.setItemMeta(meta);
            menu.set(slot++, icon, (pl, type) -> withdraw(pl, buf, id, type.isRightClick() ? Integer.MAX_VALUE : Items.def(id).mat().getMaxStackSize()));
        }
    }

    private void withdraw(Player p, Map<String, Integer> buf, String id, int max) {
        int have = buf.getOrDefault(id, 0);
        int n = Math.min(have, max);
        if (n <= 0) return;
        List<ItemStack> stacks = new ArrayList<>();
        stacks(id, n, stacks);
        int given = 0;
        for (ItemStack it : stacks) {
            int amount = it.getAmount();
            Map<Integer, ItemStack> rest = p.getInventory().addItem(it);
            int left = 0;
            for (ItemStack r : rest.values()) left += r.getAmount();
            given += amount - left;
            if (left > 0) break;
        }
        remove(buf, id, given);
        if (given < n) p.sendMessage(Txt.PREFIX + "§7Ton sac est plein.");
        p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1);
    }

    private void deposit(Player p, Machine m, int slot, ItemStack item) {
        String id = Items.id(item);
        if (id == null) return;
        if (MODULES.contains(id)) {
            if (!fitsModule(m, id)) {
                p.sendMessage(Txt.PREFIX + "§cCe module ne va pas dans cette machine (ou ses places sont prises).");
                return;
            }
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
            m.modules.add(id);
            p.playSound(p, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.6f, 1.4f);
            return;
        }
        int n = Math.min(item.getAmount(), room(m, id));
        if (n <= 0) {
            p.sendMessage(Txt.PREFIX + "§cCette machine n'accepte pas cet objet (ou elle est pleine).");
            return;
        }
        item.setAmount(item.getAmount() - n);
        p.getInventory().setItem(slot, item.getAmount() <= 0 ? null : item);
        m.in.merge(id, n, Integer::sum);
        p.playSound(p, Sound.BLOCK_BARREL_CLOSE, 0.6f, 1.2f);
    }

    private void recipes(Player p, Machine m) {
        Type t = TYPES.get(m.type);
        Menu menu = new Menu(6, "§eRecettes §8- " + Bourse.strip(t.name()));
        int slot = 0;
        for (Recipe r : t.recipes()) {
            if (slot >= 45) break;
            Map.Entry<String, Integer> o = r.out().entrySet().iterator().next();
            List<String> l = new ArrayList<>();
            Object[] req = req(r.id());

            String job = req == null ? null : (String) req[0];
            int need = req == null ? 0 : (Integer) req[1];
            boolean locked = job != null && plugin.quests.level(plugin.store.player(p), job) < need;
            String desc = Items.def(o.getKey()).desc();
            if (!desc.isEmpty()) {
                l.addAll(Txt.wrap(desc, "§7"));
                l.add("");
            }
            for (Map.Entry<String, Integer> e : r.in().entrySet()) l.add("§7- " + e.getValue() + " " + Items.def(e.getKey()).name());
            l.add("§7Durée " + Txt.num(r.seconds()) + " s, énergie " + Txt.num(r.energy()));
            if (job != null) {
                l.add("");
                l.add(locked ? "§c" + Quests.jobName(job) + " niveau " + need + " requis §7(tu es " + plugin.quests.level(plugin.store.player(p), job) + ")"
                        : "§a" + Quests.jobName(job) + " niveau " + need + " : débloqué");
            }
            ItemStack icon = locked ? new ItemStack(Material.IRON_BARS) : Items.make(o.getKey(), o.getValue());
            var meta = icon.getItemMeta();
            meta.displayName(Txt.c((locked ? "§c" : "§e") + r.name()));
            meta.lore(Txt.lines(l));
            icon.setItemMeta(meta);
            menu.set(slot++, icon, (pl, ty) -> {
                if (locked) {
                    pl.sendMessage(Txt.PREFIX + "§cIl te faut " + Quests.jobName(job) + " niveau " + need + " pour cette recette.");
                    pl.playSound(pl, Sound.ENTITY_VILLAGER_NO, 1, 1);
                    return;
                }
                m.recipe = r.id();
                pl.sendMessage(Txt.PREFIX + "§7Recette : §f" + r.name());
                open(pl, m);
            });
        }
        menu.nav((pl, ty) -> open(pl, m), "La machine");
        menu.open(p);
    }

    /** Machines et foreuses d'une Compagnie, pour le menu de la Concession. */
    static String summary(Company c) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (Machine m : c.machines.values()) n.merge(m.type, 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : n.entrySet()) b.append(Bourse.strip(TYPES.get(e.getKey()).name())).append(" x").append(e.getValue()).append(", ");
        if (b.length() > 2) b.setLength(b.length() - 2);
        return b.toString();
    }
}
