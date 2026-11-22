package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Machines.Recipe;
import fr.eemicraft.abysse.Machines.Type;
import fr.eemicraft.abysse.Store.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Guide de l'usine : une page par machine (rôle, comment la nourrir, recettes) et des pages sur l'interface d'une
 * machine, les convoyeurs/entonnoirs et l'énergie. Ouvert par l'étoile, /abysse guide, le « ? » du menu d'une
 * machine, et proposé dans le chat la première fois qu'on pose chaque machine.
 */
final class Guide {

    /** Rôle et mode d'emploi de chaque machine (lignes de la page). */
    static final Map<String, List<String>> HOWTO = Map.ofEntries(
            Map.entry("generateur", List.of(
                    "Le cœur de l'usine : sans lui, aucune machine ne tourne.",
                    "Il brûle 1 charbon toutes les 10 s et remplit la réserve d'énergie de TOUTE ta Concession (4 énergie/s).",
                    "Chaque générateur ajoute 1 000 à la réserve. Toutes les machines puisent dedans.",
                    "A nourrir : charbon (clic sur le charbon de ton sac dans son menu, ou convoyeur/entonnoir qui arrive dessus).")),
            Map.entry("concasseur", List.of(
                    "Broie 1 minerai en 2 poudres (4 s, 2 énergie).",
                    "C'est lui qui double ta production : 2 poudres donnent plus qu'un minerai fondu directement.",
                    "Entrée : minerai de cuivre, de fer ou d'étain. Sortie : poudres.",
                    "Relie sa sortie au Laveur ou à la Fonderie avec des convoyeurs.")),
            Map.entry("laveur", List.of(
                    "Lave les poudres (3 s, 1 énergie) : 1 poudre lavée = 1 lingot à la fonderie, au lieu de 2 poudres.",
                    "Il lui faut de l'EAU collée à lui : à côté (nord, sud, est, ouest) ou dessous. Un seau d'eau suffit.",
                    "Sans eau, son état affiche « Il faut de l'eau à côté ».",
                    "Entrée : poudres. Sortie : poudres lavées.")),
            Map.entry("fonderie", List.of(
                    "Fond en lingots, en prenant d'abord le plus rentable :",
                    "1 poudre lavée = 1 lingot, puis 2 poudres = 1 lingot, puis 1 minerai = 1 lingot.",
                    "5 à 6 s et 3 à 4 énergie par lingot.",
                    "Un lingot se vend bien plus cher que son minerai.",
                    "Elle coule aussi le BRONZE de la strate 1 : 4 lingots de cuivre + 2 lingots de fer = 1 bronze (8 s, 5 énergie).",
                    "Le bronze part à l'Assembleur : casque, plastron, jambières, bottes, épée et pioche en bronze.")),
            Map.entry("raffinerie", List.of(
                    "Transforme un lingot en lingot pur (8 s, 5 énergie).",
                    "Les lingots purs servent à l'Alliageur et à l'Assembleur, et se vendent plus cher.")),
            Map.entry("alliageur", List.of(
                    "Fait les alliages (10 s, 8 énergie) : 1 fer pur + 2 charbon = 1 acier ; 3 cuivre pur + 1 étain pur = 2 bronze.",
                    "C'est la machine de la strate 2 : Margaux ne la vend qu'une fois la Champignonnière ouverte (Contremaître vaincu).",
                    "L'acier fait la panoplie de la strate 2 ; avec l'étain, le bronze sort en plus grande quantité qu'à la Fonderie.",
                    "Il choisit tout seul la recette dont il a les ingrédients.")),
            Map.entry("assembleur", List.of(
                    "Fabrique composants et équipement : engrenages, bobines, filtres, masque, lampe, outils, foreuse...",
                    "Il ne fait qu'UNE recette à la fois : choisis-la avec le livre en haut à gauche de son menu.",
                    "Il n'accepte que les ingrédients de la recette choisie.",
                    "Équipement : bronze (strate 1, bronze de la Fonderie), acier (strate 2, acier de l'Alliageur), abyssal (trophées des gardiens).",
                    "L'équipement d'acier et abyssal demande un niveau de métier : il s'affiche sur la recette.")),
            Map.entry("electrolyseur", List.of(
                    "La machine des Forges Englouties (Ingénieur 16). Elle seule tire l'argent pur de la poudre d'argent lavée (2 poudres = 1 lingot, 30 énergie).",
                    "Elle fait aussi le combustible raffiné : 2 soufre + 2 charbon = 3 combustibles.",
                    "Chaîne de l'argent : minerai → Concasseur → Laveur → Électrolyseur. Elle consomme beaucoup : prévois un Générateur thermique.")),
            Map.entry("generateur_thermique", List.of(
                    "Brûle 1 combustible raffiné toutes les 30 s : 12 énergie/s, et +3 000 à la réserve (4 cellules en plus possibles).",
                    "Le combustible vient de l'Électrolyseur (soufre + charbon) : relie-les par des convoyeurs.",
                    "Débloqué au niveau 16 d'Ingénieur.")),
            Map.entry("etabli_precision", List.of(
                    "L'atelier des strates 4 et 5 (Ingénieur 18) : comme l'Assembleur, il ne fait qu'UNE recette à la fois, choisie avec le livre de son menu.",
                    "Il taille le cristal résonant en lentilles (3 cristaux + 1 poudre d'argent lavée) et fait les plaques de cobalt, les bobines résonantes, les plaques de néant et les noyaux du vide.",
                    "Il forge la Pioche de forge (Cœur du Forgeron) pour la strate 4, la Pioche de cristal (Cœur de cristal) pour la strate 5, les foreuses et têtes à induction et du Vide, et les panoplies de cristal et du Néant.",
                    "Chaque recette demande un niveau de métier, affiché sur la recette.")),
            Map.entry("four_induction", List.of(
                    "La machine de la Mer de Cristal (Ingénieur 20). Elle seule fond le cobalt : 2 poudres de cobalt lavées + 1 combustible raffiné = 1 lingot de cobalt pur (20 s, 45 énergie).",
                    "Chaîne du cobalt : minerai → Concasseur → Laveur → Four à induction, avec du combustible de l'Électrolyseur à côté.",
                    "Elle consomme beaucoup : plusieurs Générateurs thermiques, ou un Réacteur du Vide plus tard.")),
            Map.entry("condenseur", List.of(
                    "La machine du Néant (Ingénieur 24). Elle prend la première recette dont elle a les ingrédients :",
                    "2 poudres d'endérite lavées + 1 lentille de cristal = 1 lingot d'endérite pur (30 s, 70 énergie) ;",
                    "1 éclat de néant + 2 lingots de cobalt pur = 2 matières noires (40 s, 80 énergie) ;",
                    "1 matière noire + 2 combustibles + 1 essence du vide = 4 charges du vide, le combustible du Réacteur.")),
            Map.entry("reacteur", List.of(
                    "Brûle 1 charge du vide par minute : 40 énergie/s, et +10 000 à la réserve (4 cellules en plus possibles).",
                    "Les charges viennent du Condenseur du Vide : boucle-les, une partie de l'énergie sert à refaire des charges.",
                    "Débloqué au niveau 24 d'Ingénieur.")),
            Map.entry("serre", List.of(
                    "Fait pousser des légumes : 2 toutes les 20 s, sans rien lui donner (3 énergie).",
                    "Avec des spores lumineuses dans son entrée, elle fait plutôt des champignons (1 spore = 4 champignons).",
                    "Il lui faut de l'EAU collée à elle, comme le Laveur.",
                    "Ce qu'elle produit ne se vend pas : ça se mange ou ça part à la Cuisine.")),
            Map.entry("cuisine", List.of(
                    "Cuisine les produits de la Serre en plats à effets (rien ne se vend) :",
                    "Ration (rassasie longtemps), Café (Vitesse I), Ragoût (Célérité I), Soupe anti-spores (plus besoin du masque 5 min).",
                    "Elle prend la première recette dont elle a les ingrédients : relie-la à la Serre par un convoyeur.")),
            Map.entry("groupeur", List.of(
                    "Réunit plusieurs lignes de convoyeurs en une seule : branche-les toutes dessus, il renvoie tout par sa sortie.",
                    "Sa sortie : une ligne de convoyeurs qui part de lui (ou un entonnoir dessous), comme une machine.",
                    "Débloqué au niveau 5 d'Ingénieur (chez Margaux).")),
            Map.entry("separateur", List.of(
                    "Partage ce qui arrive entre ses sorties, à tour de rôle : jusqu'à 4 lignes de convoyeurs (une par côté) + un entonnoir dessous.",
                    "Une sortie pleine est sautée. Pratique pour nourrir deux fonderies avec un seul concasseur.",
                    "Débloqué au niveau 10 d'Ingénieur.")),
            Map.entry("trieur", List.of(
                    "Comme le Séparateur, mais chaque sortie peut avoir un filtre : un objet précis.",
                    "Un objet part vers une sortie qui a son filtre ; sinon vers une sortie sans filtre ; sinon il attend.",
                    "Les filtres se choisissent dans son menu (ligne Sorties). Débloqué au niveau 15 d'Ingénieur.")),
            Map.entry("entrepot", List.of(
                    "Stocke jusqu'à 2 000 objets de toute sorte.",
                    "Mets-le entre deux machines pour faire un tampon, ou au bout d'une chaîne pour tout garder.",
                    "Un convoyeur qui part de lui envoie son stock à la machine suivante.")),
            Map.entry("quai_expedition", List.of(
                    "Vend tout seul à la bourse tout ce qui y arrive, au cours du moment.",
                    "Attention : plus tout le serveur vend un produit, plus son prix baisse. Surveille la bourse.",
                    "Les éclats vont à ta Compagnie.")),
            Map.entry("quai_reception", List.of(
                    "S'achète chez Margaux ou se fabrique à l'Assembleur (Ingénieur 8 : 2 plaques blindées, 1 moteur, 6 cuivre pur).",
                    "Dans son menu, « Foreuses reliées » : choisis quelles foreuses envoient leur minerai à CE quai (les autres passent par le monte-charge commun).",
                    "Reçoit par le monte-charge le minerai extrait par tes Foreuses dans les strates.",
                    "Relie-le à un Concasseur (ou un Entrepôt) pour traiter ce minerai automatiquement.",
                    "Il garde jusqu'à 512 objets.")),
            Map.entry("table_foreuses", List.of(
                    "Se pose dans ta Concession. Clic droit : la liste des foreuses de ta Compagnie.",
                    "Pour chacune : strate, position, minerai, usure et état. Clic sur une foreuse : téléportation dessus.",
                    "Pratique pour aller réparer une foreuse en panne sans chercher dans les galeries.")),
            Map.entry("foreuse", List.of(
                    "Se pose DANS UNE STRATE, sur un filon (gros bloc de minerai brut au sol, incassable à la main).",
                    "Elle extrait 1 minerai toutes les 20 s, même quand tu n'es pas là, et l'envoie à ton Quai de réception.",
                    "Elle s'use : 1 % toutes les 3 min, 4 fois plus vite si des monstres rôdent autour.",
                    "A 0 % elle s'arrête : clic droit avec un Kit de réparation (Assembleur ou Bastien).",
                    "Foreuse renforcée (Assembleur, Ingénieur 10 : moteurs, plaques blindées, beaucoup d'étain pur) : 1,5 fois plus rapide, s'use 2 fois moins, obligatoire à partir de la strate 3. Une Tête de forage renforcée (clic droit) améliore une foreuse déjà posée.",
                    "Foreuse à induction (Établi de précision, Ingénieur 21, cobalt) : obligatoire dans la strate 4, 2 fois plus rapide. Foreuse du Vide (Ingénieur 26, endérite) : obligatoire dans la strate 5, 2,5 fois plus rapide. Leurs têtes de forage améliorent une foreuse posée.")));

    private final EemiAbysse plugin;

    Guide(EemiAbysse plugin) {
        this.plugin = plugin;
    }

    void open(Player p) {
        Menu m = new Menu(6, "§eGuide de l'usine");
        m.set(4, Txt.icon(Material.BOOK, "§eComment marche l'usine ?", List.of(
                "§71. Mine du minerai dans les strates.",
                "§72. Pose tes machines dans ta Concession.",
                "§73. Un Générateur avec du charbon donne l'énergie.",
                "§74. Relie les machines par des convoyeurs :",
                "§7   minerai → Concasseur → Laveur → Fonderie",
                "§7   → Quai d'expédition (vente automatique).",
                "§75. L'usine tourne tant qu'un membre de ta",
                "§7   Compagnie est là, puis 12 h après son départ.",
                "", "§eClique sur une machine pour sa fiche.")));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 43, 44, 46, 47, 51, 52};
        int i = 0;
        for (Type t : Machines.TYPES.values()) {
            if (i >= slots.length) break;
            Items.Def d = Items.def(t.item());
            List<String> l = new ArrayList<>(Txt.wrap(HOWTO
.getOrDefault(t.id(), List.of("")).get(0), "§7"));
            l.add("");
            l.add("§7Prix chez Margaux : " + Txt.money(t.price()));
            l.add("§eClic : fiche complète");
            m.set(slots[i++], Items.skin(Txt.icon(d.mat(), d.name(), l), t.item()), (pl, c) -> machine(pl, t.id()));
        }
        m.set(39, Txt.icon(Material.MAGENTA_GLAZED_TERRACOTTA, "§dConvoyeurs et entonnoirs", "§7Relier les machines entre elles.", "", "§eClic pour lire"), (pl, c) -> links(pl));
        m.set(40, Txt.icon(Material.CHEST, "§fLe menu d'une machine", "§7A quoi sert chaque case.", "", "§eClic pour lire"), (pl, c) -> ui(pl, null));
        m.set(41, Txt.icon(Material.SEA_LANTERN, "§bL'énergie", "§7Générateurs, réserve, panne.", "", "§eClic pour lire"), (pl, c) -> energy(pl));
        m.set(42, Txt.icon(Material.REDSTONE_TORCH, "§cModules et dynamite", "§7Améliorer ses machines.", "", "§eClic pour lire"), (pl, c) -> modules(pl));
        m.nav((pl, c) -> plugin.menu(pl), "Menu de l'Abysse");
        m.open(p);
    }

    void machine(Player p, String type) {
        Type t = Machines.TYPES.get(type);
        if (t == null) {
            open(p);
            return;
        }
        Items.Def d = Items.def(t.item());
        Menu m = new Menu(5, "§eGuide §8- " + Bourse.strip(d.name()));
        List<String> l = new ArrayList<>();
        for (String line : HOWTO.getOrDefault(type, List.of())) {
            l.addAll(Txt.wrap(line, "§7"));
            l.add("");
        }
        l.add("§7Prix chez Margaux : " + Txt.money(t.price()));
        m.set(4, Items.skin(Txt.icon(d.mat(), d.name(), l), t.item()));
        int slot = 18;
        for (Recipe r : t.recipes()) {
            if (slot > 35) break;
            Map.Entry<String, Integer> o = r.out().entrySet().iterator().next();
            List<String> rl = new ArrayList<>();
            rl.add("§7Il faut :");
            for (Map.Entry<String, Integer> e : r.in().entrySet()) rl.add("§7- §f" + e.getValue() + " " + Bourse.strip(Items.def(e.getKey()).name()));
            rl.add("§7Donne : §f" + o.getValue() + " " + Bourse.strip(Items.def(o.getKey()).name()));
            rl.add("§7Durée : §f" + Txt.num(r.seconds()) + " s §7- énergie : §b" + Txt.num(r.energy()));
            ItemStack icon = Items.make(o.getKey(), Math.max(1, o.getValue()));
            var meta = icon.getItemMeta();
            meta.displayName(Txt.c("§e" + r.name()));
            meta.lore(Txt.lines(rl));
            icon.setItemMeta(meta);
            m.set(slot++, icon);
        }
        if (t.recipes().isEmpty()) m.set(22, Txt.icon(Material.PAPER, "§7Pas de recette", "§7Cette machine ne transforme rien :", "§7lis sa fiche au-dessus."));
        m.set(36, Txt.icon(Material.CHEST, "§fLe menu d'une machine", "§eClic pour lire"), (pl, c) -> ui(pl, type));
        m.set(37, Txt.icon(Material.MAGENTA_GLAZED_TERRACOTTA, "§dConvoyeurs et entonnoirs", "§eClic pour lire"), (pl, c) -> links(pl));
        m.nav((pl, c) -> open(pl), "Guide de l'usine");
        m.open(p);
    }

    /** Page « le menu d'une machine » : une maquette du vrai menu, chaque case expliquée. */
    void ui(Player p, String back) {
        Menu m = new Menu(4, "§eGuide §8- menu d'une machine");
        m.set(0, Txt.icon(Material.BOOK, "§eRecettes (en haut à gauche)", "§7Ce que la machine sait faire.", "§7Assembleur : clic pour CHOISIR", "§7la recette à fabriquer."));
        m.set(4, Txt.icon(Material.GRINDSTONE, "§fLa machine (en haut au centre)", "§7Son rôle, son état (en marche,", "§7pas de charbon, sortie pleine...),",
                "§7l'énergie de ta Concession", "§7et la vitesse (métier d'Ingénieur)."));
        m.set(8, Txt.icon(Material.IRON_PICKAXE, "§cRamasser (en haut à droite)", "§7Maj + clic : la machine revient", "§7dans ton sac avec son contenu."));
        m.set(9, Txt.icon(Material.LIME_STAINED_GLASS_PANE, "§aEntrée (ligne verte)", "§7Ce qui attend d'être transformé.", "§7Pour remplir : clique sur l'objet",
                "§7dans TON sac, en bas de l'écran.", "§7Ou amène-le par un convoyeur."));
        m.set(18, Txt.icon(Material.ORANGE_STAINED_GLASS_PANE, "§6Sortie (ligne orange)", "§7Ce que la machine a fabriqué.", "§7Clic : prendre une pile.",
                "§7Clic droit : tout prendre.", "§7Avec un convoyeur, la sortie part", "§7toute seule vers la machine suivante."));
        m.set(27, Txt.icon(Material.KNOWLEDGE_BOOK, "§e? (en bas à gauche)", "§7Ouvre la fiche de la machine", "§7dans ce guide."));
        m.set(33, Txt.icon(Material.HOPPER, "§7Liaison (en bas à droite)", "§7Vers quelle machine part la", "§7production : convoyeur ou entonnoir.",
                "§7« aucune » = elle reste dans la sortie."));
        m.set(13, Txt.icon(Material.NAME_TAG, "§fAu-dessus de la machine", "§7Dans le monde, son nom et son état", "§7flottent au-dessus d'elle :",
                "§7un coup d'œil suffit pour voir", "§7ce qui bloque."));
        m.set(29, Txt.icon(Material.TRIPWIRE_HOOK, "§eRéparer / Foreuses reliées", "§7Seulement sur une foreuse", "§7ou un quai de réception."));
        m.set(30, Menu.backIcon("Retour (en bas) : le menu de l'Abysse"));
        m.set(31, Menu.closeIcon());
        m.nav((pl, c) -> {
            if (back == null) open(pl);
            else machine(pl, back);
        }, "Guide de l'usine");
        m.fill(Txt.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        m.open(p);
    }

    void links(Player p) {
        Menu m = new Menu(3, "§eGuide §8- convoyeurs");
        m.set(11, Txt.icon(Material.MAGENTA_GLAZED_TERRACOTTA, "§dConvoyeur §7(Margaux, 16 pour 80 ⬥)", List.of(
                "§71. Pose le premier convoyeur COLLÉ à la",
                "§7   machine de départ, en regardant dans",
                "§7   le sens où les objets doivent partir.",
                "§72. Continue la ligne : la flèche de chaque",
                "§7   convoyeur montre le sens. Tu peux tourner,",
                "§7   et monter ou descendre d'un bloc.",
                "§73. Le dernier convoyeur doit toucher la",
                "§7   machine d'arrivée.",
                "",
                "§7La sortie de la machine part alors seule,",
                "§78 objets/s, et défile sur le tapis.",
                "§7Le menu de la machine affiche la liaison.")));
        m.set(13, Txt.icon(Material.HOPPER, "§7Entonnoirs", List.of(
                "§7Entonnoir SOUS une machine : comme un",
                "§7convoyeur, la production suit la chaîne",
                "§7d'entonnoirs jusqu'à une machine (4/s).",
                "",
                "§7Entonnoir qui POINTE DANS une machine :",
                "§7il la remplit avec ce qu'il contient.",
                "§7Pratique sous un coffre de minerai.")));
        m.set(15, Txt.icon(Material.COMPARATOR, "§fExemple de chaîne", List.of(
                "§7Quai de réception (foreuses)",
                "§7  → Concasseur → Laveur (eau à côté)",
                "§7  → Fonderie → Raffinerie",
                "§7  → Entrepôt ou Quai d'expédition.",
                "",
                "§7Une machine n'envoie qu'à UNE machine.",
                "§7Si la suivante est pleine, la sortie",
                "§7attend : mets un Entrepôt en tampon.")));
        m.nav((pl, c) -> open(pl), "Guide de l'usine");
        m.fill(Txt.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        m.open(p);
    }

    void modules(Player p) {
        Menu m = new Menu(3, "§eGuide §8- modules");
        m.set(10, Txt.icon(Material.REDSTONE_TORCH, "§cModule de vitesse", "§7+50 % de vitesse, +50 % d'énergie", "§7par recette. Marche aussi sur", "§7une Foreuse.", "", "§72 engrenages + 1 bobine"));
        m.set(11, Txt.icon(Material.SOUL_TORCH, "§bModule d'économie", "§7-40 % d'énergie par recette.", "", "§72 bobines + 2 cuivre pur"));
        m.set(12, Txt.icon(Material.AMETHYST_CLUSTER, "§dModule de rendement", "§720 % de chances de doubler", "§7ce que la machine produit.", "", "§72 bronze + 1 engrenage + 2 spores"));
        m.set(13, Txt.icon(Material.LIGHTNING_ROD, "§eCellule d'énergie", "§7Dans un Générateur : +500 à la", "§7réserve d'énergie (4 par générateur).", "", "§72 bobines + 2 étain pur"));
        m.set(15, Txt.icon(Material.CHEST, "§fInstaller un module", "§7Ouvre la machine et clique sur", "§7le module dans TON sac : il va dans", "§7une place grise en haut du menu.",
                "§72 places par machine. Clic sur un", "§7module installé pour le reprendre."));
        m.set(16, Txt.icon(Material.TNT, "§cDynamite de mineur", "§7Clic droit dans une strate : la roche", "§7autour de toi (rayon 2) saute et le", "§7minerai va dans ton sac.",
                "§7Ne blesse personne, ne casse ni filons", "§7ni camp. 3 pour 4 charbon, 1 os, 1 cuivre."));
        m.nav((pl, c) -> open(pl), "Guide de l'usine");
        m.fill(Txt.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        m.open(p);
    }

    void energy(Player p) {
        Menu m = new Menu(3, "§eGuide §8- énergie");
        m.set(13, Txt.icon(Material.SEA_LANTERN, "§bL'énergie", List.of(
                "§7Chaque Générateur ajoute 1 000 à la réserve",
                "§7de ta Concession et la remplit de 4/s",
                "§7tant qu'il a du charbon (1 toutes les 10 s).",
                "",
                "§7Chaque recette coûte de l'énergie au départ",
                "§7(voir les fiches). Réserve vide = les machines",
                "§7affichent « Pas assez d'énergie ».",
                "",
                "§7Cellule d'énergie dans un générateur : +500.",
                "§7Astuce : relie un Entrepôt plein de charbon",
                "§7au générateur par un convoyeur.",
                "§7Ta réserve est affichée dans le tableau",
                "§7à droite de l'écran (⚡).")));
        m.nav((pl, c) -> open(pl), "Guide de l'usine");
        m.fill(Txt.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        m.open(p);
    }

    /** Première pose d'une machine de ce type : explication courte dans le chat et lien vers sa fiche. */
    void firstPlace(Player p, String type) {
        PlayerData d = plugin.store.player(p);
        if (!d.guides.add(type)) return;
        List<String> how = HOWTO.get(type);
        if (how == null) return;
        p.sendMessage("");
        p.sendMessage(Txt.PREFIX + "§e§lNouvelle machine : " + Machines.TYPES.get(type).name());
        for (int i = 0; i < Math.min(3, how.size()); i++) p.sendMessage("§7 " + how.get(i));
        p.sendMessage(Txt.c("§7 Clic droit dessus pour ouvrir son menu. ")
                .append(Txt.c("§e§n[Lire sa fiche]").clickEvent(ClickEvent.runCommand("/abysse guide " + type))
                        .hoverEvent(HoverEvent.showText(Component.text("Guide de l'usine")))));
    }
}
