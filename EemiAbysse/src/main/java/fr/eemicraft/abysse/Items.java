package fr.eemicraft.abysse;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Objets de l'Abysse : un objet vanilla avec son identifiant dans le PDC ({@code eemiabysse:id}), un nom et une
 * description. Deux objets de même matériau mais d'identifiants différents ne s'empilent pas.
 */
final class Items {

    enum Cat {
        /** Ressource brute ramassée dans une strate (perdue à la mort, vendable). */
        RESOURCE,
        /** Produit transformé à l'usine (perdu à la mort, vendable). */
        PRODUCT,
        /** Machine à poser. */
        MACHINE,
        /** Outil, arme, équipement (gardé à la mort). */
        GEAR,
        /** Consommable (fusée, kit, page de carnet). */
        USE,
        /** Objet de la brocante à usages limités (barre de durabilité), gardé à la mort. */
        CHARM
    }

    /**
     * @param price      prix de base à la bourse (0 = pas vendable)
     * @param elasticity nombre d'unités vendues pour que le prix soit divisé par e (bourse)
     */
    record Def(String id, Material mat, String name, Cat cat, double price, double elasticity, boolean glint, String desc) {
        boolean sellable() {
            return price > 0;
        }
    }

    static final Map<String, Def> ALL = new LinkedHashMap<>();
    /**
     * Objets habillés par le pack (EemicraftPack, modèle {@code eemicraft:abysse/<id>}) : depuis la 2.5.0, TOUS les
     * objets de l'Abysse (liste pour le pack : EemiAbysse/ids_modeles.txt). Sans le pack, l'objet vanilla reste affiché.
     */
    static boolean modeled(String id) {
        return ALL.containsKey(id);
    }

    /** Pose le modèle du pack d'un objet de l'Abysse sur une icône de menu (ou tout autre objet). */
    static ItemStack skin(ItemStack it, String id) {
        if (!modeled(id)) return it;
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("eemicraft", "abysse/" + id));
        it.setItemMeta(meta);
        return it;
    }
    /** Usages des objets de la brocante (durabilité). */
    static final Map<String, Integer> USES = Map.of("totem_garde", 5, "larme_soin", 5, "aimant", 500, "trefle", 100, "boussole_retour", 3);
    static NamespacedKey KEY_ID, KEY_CHARGE;

    private static void add(String id, Material m, String name, Cat cat, double price, double elast, boolean glint, String desc) {
        ALL.put(id, new Def(id, m, name, cat, price, elast, glint, desc));
    }

    static {
        // ---- ressources (strate 1 : charbon, cuivre, fer ; strate 2 : étain, cuivre, spores)
        add("charbon", Material.COAL, "§8Charbon", Cat.RESOURCE, 2, 4000, false, "Combustible des générateurs et des lampes.");
        add("minerai_cuivre", Material.RAW_COPPER, "§6Minerai de cuivre", Cat.RESOURCE, 3, 4000, false, "A broyer, laver puis fondre.");
        add("minerai_fer", Material.RAW_IRON, "§7Minerai de fer", Cat.RESOURCE, 4, 3500, false, "A broyer, laver puis fondre.");
        add("minerai_etain", Material.CLAY_BALL, "§fMinerai d'étain", Cat.RESOURCE, 6, 3000, false, "Le métal blanc de la Champignonnière. Allié au cuivre pur à l'Alliageur, il donne le bronze en grand.");
        add("spore_lumineuse", Material.GLOWSTONE_DUST, "§eSpore lumineuse", Cat.RESOURCE, 8, 2000, false, "Ramassée sur les chapeaux lumineux de la Champignonnière.");
        add("minerai_argent", Material.QUARTZ, "§fMinerai d'argent", Cat.RESOURCE, 9, 3000, false, "Le métal des Forges Englouties. Broyé, lavé, puis seul l'Électrolyseur sait en tirer de l'argent pur.");
        add("soufre", Material.BLAZE_POWDER, "§eSoufre", Cat.RESOURCE, 6, 3000, false, "Avec du charbon, l'Électrolyseur en fait du combustible raffiné.");
        add("coeur_forgeron", Material.NETHER_STAR, "§6§lCœur du Forgeron", Cat.RESOURCE, 1000, 40, true, "Trophée du gardien des Forges Englouties.");
        add("os_mineur", Material.BONE, "§fOs de mineur", Cat.RESOURCE, 3, 2500, false, "Reste d'un mineur qui n'est jamais remonté.");
        add("glande_spores", Material.FERMENTED_SPIDER_EYE, "§2Glande à spores", Cat.RESOURCE, 6, 2000, false, "Arrachée aux créatures de la Champignonnière.");
        add("coeur_contremaitre", Material.HEART_OF_THE_SEA, "§c§lCœur du Contremaître", Cat.RESOURCE, 400, 60, true, "Trophée du gardien des Galeries.");
        add("coeur_reine", Material.SPORE_BLOSSOM, "§d§lCœur de la Reine", Cat.RESOURCE, 700, 60, true, "Trophée du gardien de la Champignonnière.");

        // ---- produits de l'usine
        add("poudre_cuivre", Material.ORANGE_DYE, "§6Poudre de cuivre", Cat.PRODUCT, 2, 3500, false, "Minerai broyé.");
        add("poudre_fer", Material.LIGHT_GRAY_DYE, "§7Poudre de fer", Cat.PRODUCT, 3, 3000, false, "Minerai broyé.");
        add("poudre_etain", Material.WHITE_DYE, "§fPoudre d'étain", Cat.PRODUCT, 4, 2500, false, "Minerai broyé.");
        add("poudre_cuivre_lavee", Material.ORANGE_DYE, "§6Poudre de cuivre lavée", Cat.PRODUCT, 3, 3000, true, "Prête pour la fonderie.");
        add("poudre_fer_lavee", Material.LIGHT_GRAY_DYE, "§7Poudre de fer lavée", Cat.PRODUCT, 4.5, 2500, true, "Prête pour la fonderie.");
        add("poudre_etain_lavee", Material.WHITE_DYE, "§fPoudre d'étain lavée", Cat.PRODUCT, 6, 2000, true, "Prête pour la fonderie.");
        add("lingot_cuivre", Material.COPPER_INGOT, "§6Lingot de cuivre", Cat.PRODUCT, 7, 2500, false, "");
        add("lingot_fer", Material.IRON_INGOT, "§7Lingot de fer", Cat.PRODUCT, 10, 2000, false, "");
        add("lingot_etain", Material.IRON_INGOT, "§fLingot d'étain", Cat.PRODUCT, 14, 1800, false, "");
        add("lingot_cuivre_pur", Material.COPPER_INGOT, "§6Lingot de cuivre pur", Cat.PRODUCT, 12, 1500, true, "Raffiné, sans impureté.");
        add("lingot_fer_pur", Material.IRON_INGOT, "§7Lingot de fer pur", Cat.PRODUCT, 17, 1300, true, "Raffiné, sans impureté.");
        add("lingot_etain_pur", Material.IRON_INGOT, "§fLingot d'étain pur", Cat.PRODUCT, 24, 1200, true, "Raffiné, sans impureté.");
        add("bronze", Material.GOLD_INGOT, "§6§lBronze", Cat.PRODUCT, 60, 700, false, "Le métal de la strate 1 : coulé à la Fonderie (4 lingots de cuivre + 2 de fer), ou allié à l'étain à l'Alliageur, en meilleur rendement.");
        add("acier", Material.NETHERITE_INGOT, "§8§lAcier", Cat.PRODUCT, 55, 700, false, "Fer pur durci au charbon, à l'Alliageur (vendu dès la Champignonnière). Le métal de la strate 2.");
        add("engrenage", Material.CLOCK, "§eEngrenage", Cat.PRODUCT, 45, 600, false, "Composant mécanique.");
        add("bobine", Material.LIGHTNING_ROD, "§6Bobine de cuivre", Cat.PRODUCT, 32, 600, false, "Composant électrique.");
        add("poudre_argent", Material.SUGAR, "§fPoudre d'argent", Cat.PRODUCT, 6, 2500, false, "Minerai d'argent broyé.");
        add("poudre_argent_lavee", Material.SUGAR, "§fPoudre d'argent lavée", Cat.PRODUCT, 9, 2000, true, "Prête pour l'Électrolyseur.");
        add("lingot_argent_pur", Material.IRON_INGOT, "§f§lLingot d'argent pur", Cat.PRODUCT, 48, 900, true, "Sorti de l'Électrolyseur. Base des circuits et de l'équipement des Forges.");
        add("combustible", Material.FIRE_CHARGE, "§6Combustible raffiné", Cat.PRODUCT, 0, 0, false, "Soufre et charbon raffinés. Fait tourner le Générateur thermique. Ne se vend pas.");
        add("circuit", Material.REPEATER, "§bCircuit d'argent", Cat.PRODUCT, 140, 400, false, "Argent pur, bobines et cuivre pur. Composant des machines des strates profondes.");
        add("infusion_glacee", Material.POTION, "§bInfusion glacée", Cat.USE, 0, 0, true, "A boire : 4 min de résistance à la chaleur des Forges Englouties.");
        add("plastron_refrigere", Material.DIAMOND_CHESTPLATE, "§b§lPlastron réfrigéré", Cat.GEAR, 0, 0, true, "Protection III. Circuits d'argent qui refroidissent : la chaleur des Forges ne te brûle plus.");
        add("plaque_blindee", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "§8Plaque blindée", Cat.PRODUCT, 0, 0, false, "Acier laminé et bronze. Composant de l'équipement et des foreuses d'après la strate 2. Ne se vend pas.");
        add("moteur", Material.PISTON, "§eMoteur", Cat.PRODUCT, 0, 0, false, "Engrenages et bobines. Cœur des foreuses renforcées. Ne se vend pas.");
        add("noyau_spores", Material.SCULK_CATALYST, "§2Noyau de spores", Cat.PRODUCT, 0, 0, true, "Spores lumineuses et glande compressées. Donne leurs pouvoirs aux pièces abyssales. Ne se vend pas.");
        add("filtre", Material.WHITE_WOOL, "§fFiltre", Cat.PRODUCT, 40, 500, false, "Arrête les spores.");

        // ---- strate 4 « La Mer de Cristal » : cobalt et cristal résonant (Pioche de forge)
        add("minerai_cobalt", Material.LAPIS_LAZULI, "§9Minerai de cobalt", Cat.RESOURCE, 14, 2500, false, "Le métal bleu de la Mer de Cristal. Trop dur sans Pioche de forge. Broyé, lavé, puis seul le Four à induction en tire du cobalt pur.");
        add("cristal_resonant", Material.AMETHYST_SHARD, "§dCristal résonant", Cat.RESOURCE, 12, 2500, false, "Il chante quand on le touche. Taillé à l'Établi de précision, il donne des lentilles.");
        add("coeur_cristal", Material.ECHO_SHARD, "§b§lCœur de cristal", Cat.RESOURCE, 2000, 30, true, "Trophée du gardien de la Mer de Cristal. Il faut pour la Pioche de cristal, qui seule entame le Néant.");
        add("poudre_cobalt", Material.BLUE_DYE, "§9Poudre de cobalt", Cat.PRODUCT, 9, 2000, false, "Minerai de cobalt broyé.");
        add("poudre_cobalt_lavee", Material.BLUE_DYE, "§9Poudre de cobalt lavée", Cat.PRODUCT, 13, 1800, true, "Prête pour le Four à induction.");
        add("lingot_cobalt_pur", Material.IRON_INGOT, "§9§lLingot de cobalt pur", Cat.PRODUCT, 70, 700, true, "Sorti du Four à induction. Base des plaques de cobalt et des bobines résonantes.");
        add("lentille_cristal", Material.PRISMARINE_CRYSTALS, "§dLentille de cristal", Cat.PRODUCT, 160, 400, true, "Cristal résonant taillé et poli à l'argent. Composant de l'équipement de cristal.");
        add("bobine_resonante", Material.BLAZE_ROD, "§b§lBobine résonante", Cat.PRODUCT, 520, 250, true, "Cobalt, circuits et lentille : le composant électrique des strates profondes.");
        add("plaque_cobalt", Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "§9Plaque de cobalt", Cat.PRODUCT, 0, 0, false, "Cobalt pur et plaques blindées. Base de l'équipement de cristal et des foreuses à induction. Ne se vend pas.");
        add("elixir_silence", Material.POTION, "§dÉlixir de silence", Cat.USE, 0, 0, true, "A boire : 4 min sans la résonance des cristaux (Mer de Cristal).");

        // ---- strate 5 « Le Néant » : endérite et éclats de néant (Pioche de cristal)
        add("minerai_enderite", Material.POPPED_CHORUS_FRUIT, "§5Minerai d'endérite", Cat.RESOURCE, 20, 2200, false, "Le métal du Néant. Trop dur sans Pioche de cristal. Broyé, lavé, puis condensé.");
        add("eclat_neant", Material.NETHERITE_SCRAP, "§8§lÉclat de néant", Cat.RESOURCE, 60, 800, true, "Un morceau de rien qui pèse lourd. Le Condenseur en tire la matière noire.");
        add("essence_vide", Material.SHULKER_SHELL, "§5Essence du vide", Cat.RESOURCE, 25, 1500, false, "Arrachée aux créatures du Néant. Sert au sérum de gravité et aux charges du vide.");
        add("coeur_neant", Material.DRAGON_EGG, "§5§lCœur du Néant", Cat.RESOURCE, 5000, 20, true, "Trophée du dernier gardien. Varenne l'a porté trente ans.");
        add("poudre_enderite", Material.PURPLE_DYE, "§5Poudre d'endérite", Cat.PRODUCT, 13, 1800, false, "Minerai d'endérite broyé.");
        add("poudre_enderite_lavee", Material.PURPLE_DYE, "§5Poudre d'endérite lavée", Cat.PRODUCT, 18, 1600, true, "Prête pour le Condenseur du Vide.");
        add("lingot_enderite_pur", Material.GOLD_INGOT, "§5§lLingot d'endérite pur", Cat.PRODUCT, 130, 500, true, "Sorti du Condenseur du Vide. Le métal de l'équipement du Néant.");
        add("matiere_noire", Material.INK_SAC, "§8§lMatière noire", Cat.PRODUCT, 260, 300, true, "Néant condensé dans le cobalt. Composant des noyaux du vide et des charges du vide.");
        add("charge_vide", Material.PHANTOM_MEMBRANE, "§5Charge du vide", Cat.PRODUCT, 0, 0, true, "Le combustible du Réacteur du Vide. Ne se vend pas.");
        add("noyau_vide", Material.CONDUIT, "§5§lNoyau du vide", Cat.PRODUCT, 0, 0, true, "Endérite, matière noire et bobine résonante. Cœur de l'équipement du Néant. Ne se vend pas.");
        add("plaque_neant", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "§5Plaque de néant", Cat.PRODUCT, 0, 0, true, "Endérite, cobalt et matière noire laminés. Ne se vend pas.");
        add("serum_gravite", Material.POTION, "§5Sérum de gravité", Cat.USE, 0, 0, true, "A boire : 4 min sans l'aspiration du vide (Néant).");
        add("medaille_varenne", Material.NAUTILUS_SHELL, "§6§lMédaille de Varenne", Cat.GEAR, 0, 0, true, "Remise à ceux qui ont vaincu le Veilleur du Néant, au fond de l'Abysse. Pour l'instant, c'est le fond.");

        // ---- production hors bourse (nourriture, modules, dynamite) : prix 0, rien ne se vend
        add("legumes", Material.CARROT, "§aLégumes de serre", Cat.USE, 0, 0, false, "Poussent à la Serre. Se mangent tels quels, ou se cuisinent.");
        add("champignons", Material.BROWN_MUSHROOM, "§6Champignons de culture", Cat.USE, 0, 0, false, "Poussent à la Serre avec des spores lumineuses. Ingrédient de la Cuisine.");
        add("ration", Material.BAKED_POTATO, "§eRation de mineur", Cat.USE, 0, 0, false, "Nourrit bien et longtemps (rassasiement +6).");
        add("ragout", Material.MUSHROOM_STEW, "§6Ragoût des profondeurs", Cat.USE, 0, 0, true, "Nourrit et donne Célérité I pendant 3 min.");
        add("soupe_spores", Material.BEETROOT_SOUP, "§2Soupe anti-spores", Cat.USE, 0, 0, true, "Pendant 5 min : les spores ne t'empoisonnent plus et tu vois dans le noir.");
        add("cafe", Material.HONEY_BOTTLE, "§6Café noir", Cat.USE, 0, 0, false, "Vitesse I pendant 3 min.");
        add("opt_vitesse", Material.REDSTONE_TORCH, "§cModule de vitesse", Cat.USE, 0, 0, true, "A mettre dans une machine : +50 % de vitesse, mais +50 % d'énergie par recette. 2 modules max par machine.");
        add("opt_economie", Material.SOUL_TORCH, "§bModule d'économie", Cat.USE, 0, 0, true, "A mettre dans une machine : -40 % d'énergie par recette. 2 modules max par machine.");
        add("opt_rendement", Material.AMETHYST_CLUSTER, "§dModule de rendement", Cat.USE, 0, 0, true, "A mettre dans une machine : 20 % de chances de doubler ce qu'elle produit. 2 modules max par machine.");
        add("cellule", Material.LIGHTNING_ROD, "§eCellule d'énergie", Cat.USE, 0, 0, true, "A mettre dans un Générateur : +500 à la réserve d'énergie de la Concession. 4 par générateur.");
        add("dynamite", Material.TNT, "§cDynamite de mineur", Cat.USE, 0, 0, false, "Clic droit dans une strate : fait sauter la roche autour de toi (rayon 2) et ramasse le minerai. Ne blesse personne.");

        // ---- machines (achetées à l'Ingénieure)
        add("m_generateur", Material.SEA_LANTERN, "§bGénérateur", Cat.MACHINE, 0, 0, false, "Brûle du charbon et alimente toute ta Concession en énergie.");
        add("m_concasseur", Material.GRINDSTONE, "§7Concasseur", Cat.MACHINE, 0, 0, false, "1 minerai devient 2 poudres.");
        add("m_laveur", Material.CAULDRON, "§3Laveur", Cat.MACHINE, 0, 0, false, "Lave les poudres. Il lui faut de l'eau juste à côté.");
        add("m_fonderie", Material.CHISELED_NETHER_BRICKS, "§cFonderie", Cat.MACHINE, 0, 0, false, "Fond minerais et poudres en lingots. Poudre lavée = 1 lingot, 2 poudres = 1 lingot, 1 minerai = 1 lingot. Coule aussi le bronze : 4 lingots de cuivre + 2 de fer.");
        add("m_raffinerie", Material.CHISELED_QUARTZ_BLOCK, "§fRaffinerie", Cat.MACHINE, 0, 0, false, "Lingot → lingot pur.");
        add("m_alliageur", Material.SMITHING_TABLE, "§6Alliageur", Cat.MACHINE, 0, 0, false, "1 fer pur + 2 charbon = 1 acier ; 3 cuivre pur + 1 étain pur = 2 bronze. En vente quand la Champignonnière est ouverte.");
        add("m_assembleur", Material.FLETCHING_TABLE, "§eAssembleur", Cat.MACHINE, 0, 0, false, "Fabrique composants et équipement (recette au choix).");
        add("m_entrepot", Material.COPPER_GRATE, "§6Entrepôt", Cat.MACHINE, 0, 0, false, "Stocke 2 000 objets ; un entonnoir dessous les envoie plus loin.");
        add("m_quai_expedition", Material.CHISELED_TUFF, "§aQuai d'expédition", Cat.MACHINE, 0, 0, false, "Vend automatiquement à la bourse tout ce qui y arrive.");
        add("m_quai_reception", Material.OXIDIZED_COPPER_GRATE, "§3Quai de réception", Cat.MACHINE, 0, 0, false, "Reçoit par le monte-charge le minerai de tes foreuses.");
        add("m_serre", Material.MOSS_BLOCK, "§aSerre", Cat.MACHINE, 0, 0, false, "Fait pousser des légumes (et des champignons avec des spores lumineuses). Il lui faut de l'eau juste à côté.");
        add("m_cuisine", Material.CRAFTING_TABLE, "§6Cuisine", Cat.MACHINE, 0, 0, false, "Cuisine légumes et champignons en plats qui nourrissent et donnent des effets.");
        add("m_groupeur", Material.WAXED_CHISELED_COPPER, "§6Groupeur", Cat.MACHINE, 0, 0, false, "Réunit plusieurs lignes de convoyeurs en une seule sortie. Ingénieur niveau 5.");
        add("m_separateur", Material.WAXED_EXPOSED_CHISELED_COPPER, "§eSéparateur", Cat.MACHINE, 0, 0, false, "Partage ce qui arrive entre ses sorties (jusqu'à 4 convoyeurs), à tour de rôle. Ingénieur niveau 10.");
        add("m_trieur", Material.WAXED_OXIDIZED_CHISELED_COPPER, "§bTrieur", Cat.MACHINE, 0, 0, false, "Envoie chaque objet vers la sortie qui a son filtre ; le reste part vers les sorties sans filtre. Ingénieur niveau 15.");
        add("m_table_foreuses", Material.CARTOGRAPHY_TABLE, "§aTable des foreuses", Cat.MACHINE, 0, 0, false, "A poser dans ta Concession : montre où sont les foreuses de ta Compagnie, leur état, et t'y téléporte.");
        add("m_electrolyseur", Material.CHISELED_TUFF_BRICKS, "§b§lÉlectrolyseur", Cat.MACHINE, 0, 0, false, "Tire l'argent pur de la poudre d'argent lavée, et fait du combustible avec du soufre et du charbon. Gourmand en énergie.");
        add("m_generateur_thermique", Material.GILDED_BLACKSTONE, "§6§lGénérateur thermique", Cat.MACHINE, 0, 0, false, "Brûle du combustible raffiné : +3 000 de réserve et 12 énergie/s. Indispensable pour l'Électrolyseur.");
        add("m_foreuse_r", Material.LODESTONE, "§c§lForeuse renforcée", Cat.MACHINE, 0, 0, true, "Tête de forage en acier et étain : pose sur les filons de toutes les strates (obligatoire à partir de la strate 3), 1,5 fois plus rapide, s'use 2 fois moins vite.");
        add("tete_forage", Material.HEAVY_CORE, "§cTête de forage renforcée", Cat.USE, 0, 0, true, "Clic droit sur une de tes foreuses : elle devient une Foreuse renforcée (plus rapide, s'use moins, va sur les filons de la strate 3 et plus bas).");
        add("m_foreuse", Material.LODESTONE, "§c§lForeuse", Cat.MACHINE, 0, 0, false, "A poser sur un filon dans une strate. Extrait en continu, même quand tu n'es pas là.");
        add("m_etabli_precision", Material.STONECUTTER, "§d§lÉtabli de précision", Cat.MACHINE, 0, 0, false, "Taille le cristal et fabrique l'équipement des strates 4 et 5 (recette au choix). Ingénieur niveau 18.");
        add("m_four_induction", Material.CHISELED_DEEPSLATE, "§9§lFour à induction", Cat.MACHINE, 0, 0, false, "Seul à fondre le cobalt : 2 poudres de cobalt lavées + 1 combustible raffiné = 1 lingot de cobalt pur. Ingénieur niveau 20.");
        add("m_condenseur", Material.CRYING_OBSIDIAN, "§5§lCondenseur du Vide", Cat.MACHINE, 0, 0, false, "Condense l'endérite, la matière noire et les charges du vide. Énorme consommation. Ingénieur niveau 24.");
        add("m_reacteur", Material.PEARLESCENT_FROGLIGHT, "§d§lRéacteur du Vide", Cat.MACHINE, 0, 0, false, "Brûle des charges du vide : +10 000 de réserve et 40 énergie/s. Ingénieur niveau 24.");
        add("m_foreuse_3", Material.LODESTONE, "§9§lForeuse à induction", Cat.MACHINE, 0, 0, true, "Obligatoire sur les filons de la Mer de Cristal : 2 fois plus rapide qu'une foreuse, s'use 2,5 fois moins vite.");
        add("m_foreuse_4", Material.LODESTONE, "§5§lForeuse du Vide", Cat.MACHINE, 0, 0, true, "Obligatoire sur les filons du Néant : 2,5 fois plus rapide qu'une foreuse, s'use 3 fois moins vite.");
        add("tete_forage_3", Material.HEAVY_CORE, "§9Tête de forage à induction", Cat.USE, 0, 0, true, "Clic droit sur une de tes foreuses : elle devient une Foreuse à induction (filons de la strate 4).");
        add("tete_forage_4", Material.HEAVY_CORE, "§5Tête de forage du Vide", Cat.USE, 0, 0, true, "Clic droit sur une de tes foreuses : elle devient une Foreuse du Vide (filons de la strate 5).");

        // ---- équipement
        add("pioche_fer", Material.IRON_PICKAXE, "§7Pioche de mineur", Cat.GEAR, 0, 0, false, "");
        add("pioche_bronze", Material.IRON_PICKAXE, "§6Pioche en bronze", Cat.GEAR, 0, 0, true, "Plus rapide, plus solide.");
        add("pioche_acier", Material.DIAMOND_PICKAXE, "§8Pioche en acier", Cat.GEAR, 0, 0, true, "L'outil des mineurs de fond. Seule (avec la pioche abyssale) à casser l'argent et le soufre des Forges Englouties.");
        add("epee_pierre", Material.STONE_SWORD, "§7Épée de recrue", Cat.GEAR, 0, 0, false, "");
        add("epee_bronze", Material.IRON_SWORD, "§6Épée en bronze", Cat.GEAR, 0, 0, true, "");
        add("epee_acier", Material.DIAMOND_SWORD, "§8Épée en acier", Cat.GEAR, 0, 0, true, "");
        add("casque_bronze", Material.IRON_HELMET, "§6Casque en bronze", Cat.GEAR, 0, 0, false, "Protection I. L'armure de la strate 1 (ne filtre pas les spores de la Champignonnière).");
        add("plastron_bronze", Material.IRON_CHESTPLATE, "§6Plastron en bronze", Cat.GEAR, 0, 0, false, "Protection I. L'armure de la strate 1.");
        add("jambieres_bronze", Material.IRON_LEGGINGS, "§6Jambières en bronze", Cat.GEAR, 0, 0, false, "Protection I. L'armure de la strate 1.");
        add("bottes_bronze", Material.IRON_BOOTS, "§6Bottes en bronze", Cat.GEAR, 0, 0, false, "Protection I. L'armure de la strate 1.");
        // panoplie d'acier (strate 2) : Assembleur, niveaux de métier requis
        add("casque_filtrant", Material.DIAMOND_HELMET, "§3Casque filtrant", Cat.GEAR, 0, 0, false, "Protection II. Filtre les spores comme le masque. Panoplie d'acier : 4 pièces = Résistance I dans les strates.");
        add("plastron_acier", Material.DIAMOND_CHESTPLATE, "§8Plastron en acier", Cat.GEAR, 0, 0, false, "Protection II. Panoplie d'acier : 4 pièces = Résistance I dans les strates.");
        add("jambieres_acier", Material.DIAMOND_LEGGINGS, "§8Jambières en acier", Cat.GEAR, 0, 0, false, "Protection II. Panoplie d'acier : 4 pièces = Résistance I dans les strates.");
        add("bottes_acier", Material.DIAMOND_BOOTS, "§8Bottes en acier", Cat.GEAR, 0, 0, false, "Protection II, Chute amortie III. Panoplie d'acier : 4 pièces = Résistance I dans les strates.");
        // panoplie abyssale (fin de jeu) : trophées des gardiens
        add("epee_abyssale", Material.NETHERITE_SWORD, "§5§lÉpée abyssale", Cat.GEAR, 0, 0, true, "Tranchant V. Vole 15 % des dégâts infligés aux créatures de l'Abysse.");
        add("pioche_abyssale", Material.NETHERITE_PICKAXE, "§5§lPioche abyssale", Cat.GEAR, 0, 0, true, "Efficacité V. 15 % de chances en plus de doubler le minerai.");
        add("casque_abyssal", Material.NETHERITE_HELMET, "§5Casque abyssal", Cat.GEAR, 0, 0, true, "Protection IV. Filtre les spores. Panoplie abyssale : 4 pièces = Résistance I et Régénération I dans les strates.");
        add("plastron_abyssal", Material.NETHERITE_CHESTPLATE, "§5Plastron abyssal", Cat.GEAR, 0, 0, true, "Protection IV. Panoplie abyssale : 4 pièces = Résistance I et Régénération I dans les strates.");
        add("jambieres_abyssales", Material.NETHERITE_LEGGINGS, "§5Jambières abyssales", Cat.GEAR, 0, 0, true, "Protection IV. Panoplie abyssale : 4 pièces = Résistance I et Régénération I dans les strates.");
        add("bottes_abyssales", Material.NETHERITE_BOOTS, "§5Bottes abyssales", Cat.GEAR, 0, 0, true, "Protection IV, Chute amortie IV. Panoplie abyssale : 4 pièces = Résistance I et Régénération I dans les strates.");
        add("lampe", Material.LANTERN, "§eLampe de mineur", Cat.GEAR, 0, 0, false, "En main gauche dans les strates : tu vois dans le noir. Elle brûle le charbon de ton sac.");
        add("masque", Material.CHAINMAIL_HELMET, "§2Masque filtrant", Cat.GEAR, 0, 0, true, "Sur la tête dans la Champignonnière : les spores ne t'empoisonnent plus.");
        // palier de la strate 3 vers la 4 : pioche de forge (cœur du Forgeron)
        add("pioche_forge", Material.NETHERITE_PICKAXE, "§6§lPioche de forge", Cat.GEAR, 0, 0, true, "Forgée avec le Cœur du Forgeron. Seule à entamer le cobalt et le cristal de la Mer de Cristal. Efficacité V.");
        // panoplie de cristal (strate 4) : Établi de précision
        add("pioche_cristal", Material.NETHERITE_PICKAXE, "§b§lPioche de cristal", Cat.GEAR, 0, 0, true, "Taillée autour du Cœur de cristal. Seule à entamer l'endérite et les éclats du Néant. Efficacité VI.");
        add("epee_cristal", Material.NETHERITE_SWORD, "§b§lÉpée de cristal", Cat.GEAR, 0, 0, true, "Tranchant VI. Lame qui vibre : 10 % de vol de vie contre les créatures de l'Abysse.");
        add("heaume_cristal", Material.NETHERITE_HELMET, "§bHeaume de cristal", Cat.GEAR, 0, 0, true, "Protection IV. Membrane qui étouffe la résonance : seul, il suffit dans la Mer de Cristal. Panoplie de cristal : 4 pièces = Résistance I et Régénération I.");
        add("plastron_cristal", Material.NETHERITE_CHESTPLATE, "§bPlastron de cristal", Cat.GEAR, 0, 0, true, "Protection IV. Panoplie de cristal : 4 pièces = Résistance I et Régénération I, et la résonance ne te touche plus.");
        add("jambieres_cristal", Material.NETHERITE_LEGGINGS, "§bJambières de cristal", Cat.GEAR, 0, 0, true, "Protection IV. Panoplie de cristal : 4 pièces = Résistance I et Régénération I, et la résonance ne te touche plus.");
        add("bottes_cristal", Material.NETHERITE_BOOTS, "§bBottes de cristal", Cat.GEAR, 0, 0, true, "Protection IV, Chute amortie IV. Panoplie de cristal : 4 pièces = Résistance I et Régénération I.");
        // panoplie du Néant (strate 5)
        add("lame_neant", Material.NETHERITE_SWORD, "§5§lLame du Néant", Cat.GEAR, 0, 0, true, "Tranchant VII. Forgée autour du Cœur de cristal : 15 % de vol de vie contre les créatures de l'Abysse.");
        add("casque_neant", Material.NETHERITE_HELMET, "§5Casque du Néant", Cat.GEAR, 0, 0, true, "Protection V. Panoplie du Néant : 4 pièces = Résistance II et Régénération I, le vide ne t'aspire plus.");
        add("plastron_neant", Material.NETHERITE_CHESTPLATE, "§5Plastron du Néant", Cat.GEAR, 0, 0, true, "Protection V. Panoplie du Néant : 4 pièces = Résistance II et Régénération I, le vide ne t'aspire plus.");
        add("jambieres_neant", Material.NETHERITE_LEGGINGS, "§5Jambières du Néant", Cat.GEAR, 0, 0, true, "Protection V. Panoplie du Néant : 4 pièces = Résistance II et Régénération I, le vide ne t'aspire plus.");
        add("bottes_neant", Material.NETHERITE_BOOTS, "§5Bottes du Néant", Cat.GEAR, 0, 0, true, "Protection V, Chute amortie IV. Lestées de néant : seules, elles suffisent contre l'aspiration du vide.");

        // ---- consommables
        add("fusee", Material.FIREWORK_STAR, "§cFusée de détresse", Cat.USE, 0, 0, false, "Clic droit dans une strate : remonte à Bordgouffre après 5 s sans bouger.");
        add("kit_reparation", Material.TRIPWIRE_HOOK, "§eKit de réparation", Cat.USE, 0, 0, false, "Clic droit sur une foreuse : la remet à 100 %.");
        add("menu", Material.NETHER_STAR, "§5§lMenu de l'Abysse §7(clic droit)", Cat.USE, 0, 0, true, "Bordgouffre, ta Concession, la bourse, tes quêtes et tes métiers. Toujours dans ton sac.");
        add("convoyeur", Material.MAGENTA_GLAZED_TERRACOTTA, "§dConvoyeur", Cat.USE, 0, 0, false, "A poser dans ta Concession, en regardant dans le sens où les objets doivent aller : la flèche montre le sens. Une ligne de convoyeurs qui part d'une machine porte sa production jusqu'à la machine du bout (8 objets/s).");

        // ---- brocante de Prosper (usages limités, gardés à la mort)
        add("totem_garde", Material.TOTEM_OF_UNDYING, "§6§lTotem de garde", Cat.CHARM, 0, 0, true, "Dans ton sac : si tu meurs dans une strate, tu gardes ton butin. Un usage par mort.");
        add("larme_soin", Material.GHAST_TEAR, "§dLarme de guérison", Cat.CHARM, 0, 0, true, "Clic droit : rend toute ta vie et chasse le poison. 1 min entre deux usages.");
        add("aimant", Material.IRON_NUGGET, "§7Aimant de mineur", Cat.CHARM, 0, 0, true, "N'a plus d'effet : le minerai va toujours directement dans le sac.");
        add("trefle", Material.EMERALD, "§aTrèfle du prospecteur", Cat.CHARM, 0, 0, true, "Dans ton sac : 1 chance sur 4 de doubler le minerai que tu casses. Un usage par minerai doublé.");
        add("boussole_retour", Material.RECOVERY_COMPASS, "§bBoussole du dernier souffle", Cat.CHARM, 0, 0, true, "Clic droit : te ramène là où tu es mort dans une strate (moins de 20 min) pour récupérer ton butin.");
        add("lampe_eternelle", Material.SOUL_LANTERN, "§bLampe éternelle", Cat.GEAR, 0, 0, true, "En main gauche dans les strates : tu vois dans le noir, sans brûler de charbon.");
        add("page_carnet", Material.PAPER, "§fPage de carnet", Cat.USE, 0, 0, false, "Clic droit pour la lire et la ranger dans ton journal.");
    }

    private Items() {
    }

    static void init(EemiAbysse plugin) {
        KEY_ID = new NamespacedKey(plugin, "id");
        KEY_CHARGE = new NamespacedKey(plugin, "charge");
    }

    static Def def(String id) {
        return ALL.get(id);
    }

    static Collection<Def> all() {
        return ALL.values();
    }

    static List<Def> sellables() {
        List<Def> out = new ArrayList<>();
        for (Def d : ALL.values()) if (d.sellable()) out.add(d);
        return out;
    }

    /** Identifiant Abysse d'un objet, ou null. */
    static String id(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    static boolean is(ItemStack it, String id) {
        return id.equals(id(it));
    }

    static ItemStack make(String id, int amount) {
        Def d = ALL.get(id);
        if (d == null) throw new IllegalArgumentException("Objet inconnu : " + id);
        ItemStack it = new ItemStack(d.mat(), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, id);
        meta.displayName(Txt.c(d.name()));
        List<String> lore = new ArrayList<>();
        if (!d.desc().isEmpty()) lore.addAll(Txt.wrap(d.desc(), "§7"));
        switch (d.cat()) {
            case RESOURCE -> lore.add("§8Ressource de l'Abysse - perdue si tu meurs");
            case PRODUCT -> lore.add("§8Produit de l'usine - perdu si tu meurs");
            case MACHINE -> lore.add("§8Machine - à poser");
            case CHARM -> lore.add("§7Usages : §f" + USES.get(id) + " / " + USES.get(id));
            default -> {
            }
        }
        if (d.sellable()) lore.add("§7Prix de base : " + Txt.money(d.price()));
        meta.lore(Txt.lines(lore));
        if (d.glint()) meta.setEnchantmentGlintOverride(true);
        gear(id, meta);
        // habillage du pack EEMICRAFT (eemicraft:abysse/<id>) ; sans le pack, l'objet vanilla reste affiché
        if (modeled(id)) meta.setItemModel(new NamespacedKey("eemicraft", "abysse/" + id));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (d.cat() == Cat.CHARM) {
            meta.setMaxStackSize(1);
            ((Damageable) meta).setMaxDamage(USES.get(id));
        }
        it.setItemMeta(meta);
        finish(it, id);
        return it;
    }

    /** Le totem de garde n'est pas un vrai totem : il ne ressuscite pas en main. */
    private static void finish(ItemStack it, String id) {
        if (id.equals("totem_garde")) it.unsetData(io.papermc.paper.datacomponent.DataComponentTypes.DEATH_PROTECTION);
    }

    // ------------------------------------------------------------------ objets à usages (brocante)

    /** Casque qui filtre les spores de la Champignonnière. */
    static boolean filters(ItemStack helmet) {
        String id = id(helmet);
        return "masque".equals(id) || "casque_filtrant".equals(id) || "casque_abyssal".equals(id)
                || "heaume_cristal".equals(id) || "casque_neant".equals(id);
    }

    /** Case du sac qui contient cet objet, ou -1. */
    static int slot(org.bukkit.entity.Player p, String id) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) if (is(c[i], id)) return i;
        return -1;
    }

    static int uses(ItemStack it) {
        String id = id(it);
        Integer max = id == null ? null : USES.get(id);
        if (max == null) return 0;
        return max - ((Damageable) it.getItemMeta()).getDamage();
    }

    static ItemStack withUses(String id, int left) {
        ItemStack it = make(id, 1);
        ItemMeta meta = it.getItemMeta();
        int max = USES.get(id);
        ((Damageable) meta).setDamage(max - left);
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
        lore.set(lore.size() - 1, Txt.c("§7Usages : §f" + left + " / " + max));
        meta.lore(lore);
        it.setItemMeta(meta);
        finish(it, id);
        return it;
    }

    /** Retire un usage à l'objet de cette case ; renvoie les usages restants (0 = objet usé et retiré). */
    static int consume(org.bukkit.entity.Player p, int slot) {
        ItemStack it = p.getInventory().getItem(slot);
        String id = id(it);
        if (id == null || !USES.containsKey(id)) return 0;
        int left = uses(it) - 1;
        if (left <= 0) {
            p.getInventory().setItem(slot, null);
            p.playSound(p, org.bukkit.Sound.ENTITY_ITEM_BREAK, 1, 1);
            p.sendMessage(Txt.PREFIX + def(id).name() + " §7est usé(e).");
            return 0;
        }
        p.getInventory().setItem(slot, withUses(id, left));
        return left;
    }

    /** Enchantements de l'équipement. */
    private static void gear(String id, ItemMeta meta) {
        switch (id) {
            case "pioche_fer" -> meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            case "pioche_bronze" -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 2, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "pioche_acier" -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            }
            case "epee_bronze" -> meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            case "epee_acier" -> {
                meta.addEnchant(Enchantment.SHARPNESS, 3, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "casque_bronze", "plastron_bronze", "jambieres_bronze", "bottes_bronze" -> meta.addEnchant(Enchantment.PROTECTION, 1, true);
            case "casque_filtrant", "plastron_acier", "jambieres_acier" -> {
                meta.addEnchant(Enchantment.PROTECTION, 2, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case "bottes_acier" -> {
                meta.addEnchant(Enchantment.PROTECTION, 2, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                meta.addEnchant(Enchantment.FEATHER_FALLING, 3, true);
            }
            case "epee_abyssale" -> {
                meta.addEnchant(Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            }
            case "pioche_abyssale" -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            }
            case "casque_abyssal", "plastron_abyssal", "jambieres_abyssales" -> {
                meta.addEnchant(Enchantment.PROTECTION, 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            }
            case "plastron_refrigere" -> {
                meta.addEnchant(Enchantment.PROTECTION, 3, true);
                meta.addEnchant(Enchantment.UNBREAKING, 4, true);
                meta.addEnchant(Enchantment.FIRE_PROTECTION, 3, true);
            }
            case "pioche_forge" -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 6, true);
            }
            case "pioche_cristal" -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 6, true);
                meta.addEnchant(Enchantment.UNBREAKING, 7, true);
            }
            case "epee_cristal" -> {
                meta.addEnchant(Enchantment.SHARPNESS, 6, true);
                meta.addEnchant(Enchantment.UNBREAKING, 6, true);
            }
            case "lame_neant" -> {
                meta.addEnchant(Enchantment.SHARPNESS, 7, true);
                meta.addEnchant(Enchantment.UNBREAKING, 7, true);
            }
            case "heaume_cristal", "plastron_cristal", "jambieres_cristal" -> {
                meta.addEnchant(Enchantment.PROTECTION, 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, 6, true);
            }
            case "bottes_cristal" -> {
                meta.addEnchant(Enchantment.PROTECTION, 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, 6, true);
                meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            }
            case "casque_neant", "plastron_neant", "jambieres_neant" -> {
                meta.addEnchant(Enchantment.PROTECTION, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 7, true);
            }
            case "bottes_neant" -> {
                meta.addEnchant(Enchantment.PROTECTION, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 7, true);
                meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            }
            case "bottes_abyssales" -> {

                meta.addEnchant(Enchantment.PROTECTION, 4, true);
                meta.addEnchant(Enchantment.UNBREAKING, 5, true);
                meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            }
            default -> {
            }
        }
    }

    /** Page de carnet : identifiant « page_carnet » + numéro de strate et de page dans le PDC. */
    static ItemStack page(int strate, int num, String title) {
        ItemStack it = make("page_carnet", 1);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.c("§fCarnet de Varenne §7- " + title));
        meta.getPersistentDataContainer().set(KEY_CHARGE, PersistentDataType.STRING, strate + ":" + num);
        it.setItemMeta(meta);
        return it;
    }

    static String pageRef(ItemStack it) {
        if (!is(it, "page_carnet")) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_CHARGE, PersistentDataType.STRING);
    }

    /** Charge de la lampe (secondes). */
    static int charge(ItemStack it) {
        Integer v = it.getItemMeta().getPersistentDataContainer().get(KEY_CHARGE, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    static void setCharge(ItemStack it, int seconds) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_CHARGE, PersistentDataType.INTEGER, Math.max(0, seconds));
        it.setItemMeta(meta);
    }

    static boolean lostOnDeath(ItemStack it) {
        String id = id(it);
        if (id == null) return false;
        Def d = def(id);
        return d != null && (d.cat() == Cat.RESOURCE || d.cat() == Cat.PRODUCT);
    }
}
