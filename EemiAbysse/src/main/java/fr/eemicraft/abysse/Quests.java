package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import fr.eemicraft.abysse.Store.PlayerData;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Histoire principale (quêtes), métiers et carnets de l'expédition Varenne. */
final class Quests {

    enum Obj { ENTER, MINE, SELL, PLOT, PRODUCE, KILL, BUY, PAGES, GUARDIAN, DRILL, NONE }

    record Quest(String giver, String title, String objective, Obj obj, String target, int amount,
                 int money, List<String> items, String job, int xp, List<String> intro) {
    }

    static final List<Quest> QUESTS = List.of(
            q("gaspard", "La descente", "Descends dans les Galeries", Obj.ENTER, "1", 1, 50, List.of(), "mineur", 20,
                    "Le gouffre au centre de la place, c'est l'Abysse. Saute, ou demande à Léon de te descendre au monte-charge.",
                    "En bas, garde ta lampe en main gauche : elle brûle le charbon de ton sac."),
            q("gaspard", "Premier filon", "Mine du minerai de cuivre", Obj.MINE, "minerai_cuivre", 20, 80, List.of(), "mineur", 50,
                    "Bien, tu es remonté entier. Le cuivre, c'est le pain des mineurs : rapporte-m'en une vingtaine."),
            q("odette", "Le cours du cuivre", "Gagne des éclats à la bourse", Obj.SELL, "", 100, 100, List.of(), null, 0,
                    "Gaspard m'envoie un nouveau ? Ici on vend, et le prix dépend de ce que tout Bordgouffre a vendu avant toi.",
                    "Plus on vend d'un minerai, moins il vaut. Il remonte avec le temps. Gagne 100 éclats."),
            q("lebrun", "Une terre à soi", "Obtiens ta Concession auprès du Notaire", Obj.PLOT, "", 1, 0, List.of("m_generateur:1", "m_fonderie:1"), null, 0,
                    "Tout mineur sérieux a sa Concession. Signez ici, la parcelle est offerte par la mairie.",
                    "Et Margaux vous offre un Générateur et une Fonderie pour démarrer."),
            q("margaux", "Le feu de la forge", "Produis des lingots de cuivre", Obj.PRODUCE, "lingot_cuivre", 10, 150, List.of("m_quai_expedition:1"), "ingenieur", 50,
                    "Pose le Générateur et la Fonderie dans ta Concession. Charbon dans le générateur, minerai dans la fonderie.",
                    "Un lingot vaut plus que son minerai. Fais-en 10, je t'offre un Quai d'expédition qui vend tout seul.",
                    "Perdu ? Ouvre le Guide de l'usine depuis l'étoile de ton sac : chaque machine et son menu y sont expliqués."),
            q("gaspard", "Ça grouille en bas", "Tue des monstres des Galeries", Obj.KILL, "1", 15, 200, List.of(), "chasseur", 80,
                    "Les Rôdeurs remontent de plus en plus haut. Il y a 30 ans, l'expédition Varenne est descendue et rien n'est remonté... sauf eux.",
                    "Nettoie un peu les Galeries. Quinze, ça devrait les calmer."),
            q("margaux", "Broyer pour gagner plus", "Achète un Concasseur à Margaux", Obj.BUY, "concasseur", 1, 100, List.of(), null, 0,
                    "Un minerai fondu donne un lingot. Broyé d'abord, il donne deux poudres, donc deux lingots.",
                    "Relie tes machines avec des convoyeurs (je les vends) : pose-les depuis une machine vers la suivante, la flèche montre le sens."),
            q("margaux", "La chaîne", "Produis des poudres", Obj.PRODUCE, "poudre_", 30, 250, List.of(), "ingenieur", 80,
                    "Concasseur, entonnoir, fonderie. Fais tourner la chaîne : 30 poudres.",
                    "Ta fonderie coule aussi le bronze : 4 lingots de cuivre et 2 de fer. Avec un Assembleur, ça fait ta première armure."),
            q("ines", "Les carnets de Varenne", "Trouve des pages des carnets", Obj.PAGES, "", 2, 300, List.of(), null, 0,
                    "Varenne tenait un journal. Ses pages sont éparpillées dans les campements abandonnés, au fond des tonneaux.",
                    "Rapporte-m'en deux : je veux savoir ce qu'ils ont trouvé."),
            q("gaspard", "Le Contremaître", "Vaincs le gardien des Galeries", Obj.GUARDIAN, "1", 1, 1000, List.of(), "chasseur", 100,
                    "Au bout de la galerie éclairée, il y a l'antre du Contremaître. Le chef de chantier de Varenne... ce qu'il en reste.",
                    "Frappe l'autel au centre pour le réveiller. Viens avec des amis si tu peux."),
            q("margaux", "Respirer en bas", "Fabrique un Masque filtrant (Assembleur)", Obj.PRODUCE, "masque", 1, 400, List.of(), "ingenieur", 100,
                    "Plus bas, l'air est plein de spores. Sans masque, tu ne tiendras pas.",
                    "Raffinerie pour les lingots purs, Assembleur pour les filtres puis le masque."),
            q("leon", "La Champignonnière", "Descends dans la Champignonnière", Obj.ENTER, "2", 1, 200, List.of(), "mineur", 50,
                    "Le Contremaître est tombé, la strate 2 est ouverte. Je t'y descends quand tu veux. Masque sur le nez !"),
            q("gaspard", "Le métal blanc", "Mine du minerai d'étain", Obj.MINE, "minerai_etain", 30, 400, List.of(), "mineur", 100,
                    "L'étain ne se trouve que là-dessous. Raffiné, il sert aux filtres, aux cellules, et à l'Alliageur il donne deux fois plus de bronze. Ramène-m'en 30."),
            q("margaux", "L'âge de l'acier", "Produis de l'acier (Alliageur)", Obj.PRODUCE, "acier", 10, 600, List.of(), "ingenieur", 120,
                    "Maintenant que la Champignonnière est ouverte, je te vends l'Alliageur. 1 fer pur et 2 charbons : 1 acier.",
                    "L'acier, c'est l'armure et les outils d'en bas : ton bronze ne suffira plus contre la Reine."),
            q("margaux", "Creuser sans soi", "Pose une foreuse sur un filon", Obj.DRILL, "", 1, 500, List.of("kit_reparation:2"), "ingenieur", 100,
                    "Les filons, ces gros blocs de minerai au sol des strates, s'exploitent avec une foreuse.",
                    "Elle envoie tout à ton Quai de réception par le monte-charge, même quand tu dors. Pense à la réparer."),
            q("gaspard", "La Reine Mycélienne", "Vaincs le gardien de la Champignonnière", Obj.GUARDIAN, "2", 1, 2000, List.of(), "chasseur", 200,
                    "Quelque chose règne sur les champignons. Varenne l'appelait la Reine. Va voir si elle existe."),
            q("leon", "Les Forges Englouties", "Descends dans les Forges Englouties", Obj.ENTER, "3", 1, 400, List.of("infusion_glacee:3"), "mineur", 80,
                    "La Reine est tombée, la strate 3 est ouverte. Mais en bas, c'est une fournaise.",
                    "Tiens, trois infusions glacées : sans elles, la chaleur te brûle. Ne traîne pas."),
            q("gaspard", "Le métal lunaire", "Mine du minerai d'argent", Obj.MINE, "minerai_argent", 150, 1500, List.of(), "mineur", 150,
                    "L'argent ne se trouve que là-dessous, dans la roche noire. Rare, et il faut le mériter.",
                    "Il est trop dur pour ta vieille pioche : il faut une Pioche en acier (Mineur 10). Ramène-m'en 150, à la main."),
            q("margaux", "La foudre en bouteille", "Achète un Électrolyseur à Margaux", Obj.BUY, "electrolyseur", 1, 1000, List.of(), "ingenieur", 150,
                    "Ta fonderie ne fera jamais d'argent pur : il faut l'électrolyse. Ça demande l'Ingénieur 16, et beaucoup d'énergie.",
                    "Broyé, lavé, puis électrolysé : c'est la chaîne de l'argent."),
            q("margaux", "Le feu raffiné", "Produis du combustible raffiné", Obj.PRODUCE, "combustible", 120, 2000, List.of(), "ingenieur", 150,
                    "Tes générateurs à charbon ne suivront pas. Soufre et charbon dans l'Électrolyseur : du combustible.",
                    "Le Générateur thermique le brûle et tient une réserve trois fois plus grande."),
            q("margaux", "L'argent pur", "Produis des lingots d'argent pur", Obj.PRODUCE, "lingot_argent_pur", 160, 4000, List.of(), "ingenieur", 250,
                    "160 lingots d'argent pur. Avec eux viendront les circuits, et le Plastron réfrigéré pour ne plus brûler."),
            q("gaspard", "Le Forgeron Oublié", "Vaincs le gardien des Forges Englouties", Obj.GUARDIAN, "3", 1, 5000, List.of(), "chasseur", 300,
                    "Au bout des forges, l'enclume de Varenne bat encore. Celui qui frappe dessus n'a plus rien d'humain.",
                    "Son autel ne s'allume que contre une offrande : 16 lingots d'argent pur et 4 circuits.",
                    "Viens nombreux, en armure d'acier ou abyssale : le feu traverse l'armure ordinaire."),
            // ---- strate 4 : La Mer de Cristal
            q("margaux", "L'outil de précision", "Achète un Établi de précision à Margaux", Obj.BUY, "etabli_precision", 1, 2000, List.of(), "ingenieur", 300,
                    "Le Forgeron est tombé, la strate 4 est ouverte. Mais le cristal d'en bas casse les pioches comme du verre.",
                    "Il faut un Établi de précision : Ingénieur 18. C'est lui qui taillera ton outil, et plus tard ton armure."),
            q("margaux", "La Pioche de forge", "Fabrique une Pioche de forge (Établi de précision)", Obj.PRODUCE, "pioche_forge", 1, 2500, List.of(), "mineur", 300,
                    "Le Cœur du Forgeron, 24 lingots d'argent pur, 8 circuits, 3 moteurs, 6 plaques blindées. Et Mineur 18.",
                    "Sans elle, le cobalt et le cristal ne cèdent pas. Pas de raccourci."),
            q("leon", "La Mer de Cristal", "Descends dans la Mer de Cristal", Obj.ENTER, "4", 1, 800, List.of("elixir_silence:3"), "mineur", 150,
                    "En bas, la roche chante. Ça rend fou, et ça fait saigner les oreilles.",
                    "Trois élixirs de silence. Ensuite, la Cuisine t'en fera avec du cristal résonant. Et méfie-toi de l'eau."),
            q("gaspard", "Le métal bleu", "Mine du minerai de cobalt", Obj.MINE, "minerai_cobalt", 300, 4000, List.of(), "mineur", 400,
                    "Le cobalt ne se trouve que dans la Mer de Cristal, et les foreuses ordinaires n'y mordent pas.",
                    "300, à la pioche. Ton armure abyssale ne vaut plus grand-chose là-dessous : les éclats passent au travers."),
            q("margaux", "Le Four à induction", "Produis des lingots de cobalt pur", Obj.PRODUCE, "lingot_cobalt_pur", 400, 8000, List.of(), "ingenieur", 500,
                    "Ta fonderie ne fondra jamais le cobalt. Le Four à induction (Ingénieur 20) : 2 poudres lavées et 1 combustible raffiné par lingot.",
                    "400 lingots. Ça veut dire des concasseurs, des laveurs, des électrolyseurs pour le combustible... et des Foreuses à induction."),
            q("margaux", "La résonance domptée", "Produis des bobines résonantes (Établi de précision)", Obj.PRODUCE, "bobine_resonante", 40, 10000, List.of(), "ingenieur", 600,
                    "Cristal taillé en lentilles, cobalt, circuits : la bobine résonante. C'est elle qui fait l'armure de cristal.",
                    "40 bobines. Avec elles, tu pourras te couvrir de cristal et ne plus entendre chanter la roche."),
            q("gaspard", "Le Colosse de Cristal", "Vaincs le gardien de la Mer de Cristal", Obj.GUARDIAN, "4", 1, 12000, List.of(), "chasseur", 500,
                    "Au bout de la mer, quelque chose de très grand marche dans le cristal. Les noyés le suivent.",
                    "Son autel réclame 24 lingots de cobalt pur, 8 lentilles de cristal et 2 bobines résonantes.",
                    "Viens nombreux, en armure de cristal : le reste se brise sous ses coups."),
            // ---- strate 5 : Le Néant
            q("margaux", "La Pioche de cristal", "Fabrique une Pioche de cristal (Établi de précision)", Obj.PRODUCE, "pioche_cristal", 1, 5000, List.of(), "mineur", 500,
                    "Le Colosse est tombé, la strate 5 est ouverte. En dessous, il n'y a plus de roche : il y a le Néant.",
                    "Seule une Pioche de cristal l'entame. Le Cœur de cristal, et Mineur 22."),
            q("leon", "Le Néant", "Descends dans le Néant", Obj.ENTER, "5", 1, 1500, List.of("serum_gravite:3"), "mineur", 200,
                    "Je t'avoue que je n'aime pas descendre aussi bas. Le monte-charge grince, et le vide tire vers le haut.",
                    "Trois sérums de gravité. La Cuisine t'en fera avec de l'essence du vide. Les bottes du Néant, plus tard."),
            q("gaspard", "L'endérite", "Mine du minerai d'endérite", Obj.MINE, "minerai_enderite", 400, 8000, List.of(), "mineur", 600,
                    "Varenne appelait ça l'endérite. Un métal qui n'existe nulle part ailleurs.",
                    "400, à la pioche de cristal. Ce qui vit en bas frappe fort : l'armure de cristal elle-même ne suffit plus tout à fait."),
            q("margaux", "Le Condenseur du Vide", "Achète un Condenseur du Vide à Margaux", Obj.BUY, "condenseur", 1, 5000, List.of(), "ingenieur", 400,
                    "L'endérite ne se fond pas : elle se condense, avec une lentille de cristal. Ingénieur 24.",
                    "Il fait aussi la matière noire (éclat de néant + cobalt) et les charges du vide. Prévois un Réacteur du Vide : il a très faim."),
            q("margaux", "L'énergie du vide", "Produis des charges du vide", Obj.PRODUCE, "charge_vide", 200, 12000, List.of(), "ingenieur", 700,
                    "Matière noire, combustible raffiné, essence du vide : quatre charges. Le Réacteur en brûle une par minute.",
                    "200 charges. Ta Concession va devoir tenir une boucle complète : le Condenseur nourrit le Réacteur qui nourrit le Condenseur."),
            q("margaux", "Le métal du fond", "Produis des lingots d'endérite pur", Obj.PRODUCE, "lingot_enderite_pur", 500, 20000, List.of(), "ingenieur", 900,
                    "500 lingots d'endérite pur. Avec eux, les plaques de néant, les noyaux du vide, et l'armure du Néant.",
                    "Quand tu l'auras, tu pourras descendre voir ce qui garde le fond."),
            q("gaspard", "Le Veilleur du Néant", "Vaincs le gardien du Néant", Obj.GUARDIAN, "5", 1, 30000, List.of(), "chasseur", 900,
                    "Au fond du Néant, quelqu'un attend depuis trente ans. Je crois savoir qui.",
                    "Son autel réclame 24 lingots d'endérite pur, 6 matières noires et 2 noyaux du vide.",
                    "Viens nombreux, en armure du Néant. Et ramène-le, s'il reste quelque chose à ramener."),
            q("gaspard", "Plus bas", "La suite arrive bientôt...", Obj.NONE, "", 1, 0, List.of(), null, 0,
                    "Varenne est tombé. Tu es allé plus bas que personne. Les ingénieurs disent que la roche continue, sous le Néant.",
                    "En attendant, fais tourner ton usine et surveille la bourse."));

    private static Quest q(String giver, String title, String objective, Obj o, String target, int amount, int money,
                           List<String> items, String job, int xp, String... intro) {
        return new Quest(giver, title, objective, o, target, amount, money, items, job, xp, List.of(intro));
    }

    // ------------------------------------------------------------------ carnets

    static final int PAGES = 5;
    private static final String[][] PAGE_TEXT = {
            {"Jour 3. Les Galeries sont plus vastes que prévu. Le Contremaître dit qu'on sera au fond avant l'hiver.",
                    "Jour 9. Morel jure avoir vu bouger une ombre au bout du boyau ouest. On a doublé les lampes.",
                    "Jour 14. Le cuivre est partout. On pourrait enrichir tout Bordgouffre. Pourtant personne ne sourit.",
                    "Jour 20. Trois hommes ne sont pas revenus de la ronde. Le Contremaître refuse de remonter.",
                    "Jour 22. Il parle seul devant l'autel noir. Il dit que la roche l'appelle. On descend demain sans lui."},
            {"Jour 25. Des champignons hauts comme des maisons. L'air pique la gorge, on a taillé des filtres dans nos chemises.",
                    "Jour 27. L'étain d'ici est d'une pureté incroyable. Varenne veut une fonderie en bas. Folie.",
                    "Jour 31. Quelque chose pousse sur Lucas. Il dit qu'il n'a pas mal. Il ne dort plus.",
                    "Jour 34. Lucas est parti dans la grande salle. Il chantait. Les spores brillaient autour de lui.",
                    "Jour 36. Elle nous a laissés passer. La Reine. Elle veut qu'on descende. Plus bas, la roche est chaude."},
            {"Jour 40. Une forge, à cette profondeur. Des enclumes froides, des outils rangés. Quelqu'un vivait ici avant nous.",
                    "Jour 43. L'argent coule dans la roche noire. Varenne a monté une cuve à foudre pour le purifier. Elle marche.",
                    "Jour 47. La chaleur a eu raison de Morel. On boit des infusions de champignons glacées pour tenir.",
                    "Jour 51. Le forgeron d'ici n'est pas mort. Il frappe la nuit. On l'entend à travers la roche.",
                    "Jour 55. Varenne veut descendre encore. Il dit qu'en dessous, tout est cristal. Je ne le suivrai pas."},
            {"Jour 61. Je l'ai suivi. Une mer souterraine, et des cristaux grands comme des cathédrales. Ils chantent.",
                    "Jour 64. Le chant ne s'arrête jamais. Pernot s'est bouché les oreilles à la cire. Il saigne quand même.",
                    "Jour 68. Le cobalt d'ici ne fond dans aucun four. Varenne fait des bobines avec la foudre de la cuve.",
                    "Jour 73. Les noyés étaient des nôtres. Ils marchent sous l'eau, vers quelque chose de très grand.",
                    "Jour 77. Le géant de cristal s'est écarté pour Varenne. Comme la Reine. Comme s'ils l'attendaient."},
            {"Jour 80. Sous la mer, plus rien. Une pierre pâle, et le vide qui tire vers le haut. On s'encorde.",
                    "Jour 83. Varenne ne dort plus. Il dit que le fond lui parle, qu'il voit sans la lampe.",
                    "Jour 86. J'ai trouvé une ferraille qui ne rouille pas. Endérite, dit Varenne. Il en a mis dans ses bottes.",
                    "Jour 90. Je suis le dernier. Varenne s'est assis devant l'autel et ne bouge plus. Ses yeux brillent.",
                    "Jour ?. Je remonte seul. Si quelqu'un lit ceci : ne le réveillez pas. Il veille sur quelque chose."}};

    static String page(int strata, int n) {
        return strata >= 1 && strata <= PAGE_TEXT.length && n >= 1 && n <= PAGES ? PAGE_TEXT[strata - 1][n - 1] : "...";
    }

    static String pageTitle(int strata, int n) {
        Gen.Strata s = Gen.strata(strata);
        return (s == null ? "?" : s.name) + ", page " + n;
    }

    // ------------------------------------------------------------------ état

    private final EemiAbysse plugin;

    Quests(EemiAbysse plugin) {
        this.plugin = plugin;
    }

    Quest current(PlayerData d) {
        if (d.quest <= 0 || d.quest > QUESTS.size()) return null;
        return QUESTS.get(d.quest - 1);
    }

    /** Texte de l'objectif en cours pour le tableau latéral. */
    String objective(PlayerData d) {
        if (d.quest <= 0) return "§eParle à Gaspard";
        Quest q = current(d);
        if (q == null) return "§7Aucune";
        if (q.obj() == Obj.NONE) return "§7" + q.objective();
        return "§f" + q.objective();
    }

    String progress(PlayerData d) {
        Quest q = current(d);
        if (q == null || q.obj() == Obj.NONE) return "";
        if (q.obj() == Obj.SELL) return "§7" + Txt.num(Math.floor(d.questProgress)) + " / " + q.amount() + " ⬥";
        return "§7" + (int) d.questProgress + " / " + q.amount();
    }

    private void say(Player p, String npc, List<String> lines) {
        Npcs.Def d = Npcs.def(npc);
        String who = d == null ? npc : d.name();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage("§6" + who + " §8» §f" + line);
                p.playSound(p, Sound.ENTITY_VILLAGER_AMBIENT, 0.5f, 1.1f);
            }, i * 50L);
        }
    }

    /** Clic sur un PNJ : démarre l'histoire (Gaspard) ou répète la quête qu'il a donnée. Renvoie vrai si on a parlé. */
    boolean talk(Player p, String npc) {
        PlayerData d = plugin.store.player(p);
        if (d.quest <= 0) {
            if (!npc.equals("gaspard")) {
                p.sendMessage("§6" + Npcs.def(npc).name() + " §8» §fVa d'abord voir Gaspard, " + EemiAbysse.GASPARD_WHERE + ".");
                return true;
            }
            say(p, "gaspard", List.of("Un nouveau ! Bienvenue à Bordgouffre, petit.",
                    "Tiens, voilà de quoi commencer : une pioche, une épée, une lampe et de quoi manger.",
                    "Si tu perds ta pioche ou ton épée, Bastien t'en redonne gratuitement. Le bronze, ta fonderie le coulera ; l'acier, ce sera pour plus bas.",
                    "Les éclats que tu gagnes vont à ta Compagnie. Tu peux t'associer à 4 avec /abysse compagnie."));
            if (!d.kitGiven) {
                d.kitGiven = true;
                List<ItemStack> kit = new ArrayList<>();
                kit.add(Items.make("pioche_fer", 1));
                kit.add(Items.make("epee_pierre", 1));
                ItemStack lamp = Items.make("lampe", 1);
                Items.setCharge(lamp, 600);
                kit.add(lamp);
                kit.add(Items.make("fusee", 2));
                kit.add(Items.make("charbon", 8));
                kit.add(new ItemStack(Material.BREAD, 16));
                kit.add(new ItemStack(Material.TORCH, 8));
                for (ItemStack it : kit)
                    for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
            }
            d.quest = 1;
            d.questProgress = 0;
            Bukkit.getScheduler().runTaskLater(plugin, () -> announce(p, d), 160L);
            return true;
        }
        Quest q = current(d);
        if (q != null && q.giver().equals(npc)) {
            say(p, npc, q.intro());
            return true;
        }
        return false;
    }

    void remind(Player p) {
        PlayerData d = plugin.store.player(p);
        Quest q = current(d);
        if (q == null) return;
        p.sendMessage(Txt.PREFIX + "§eQuête : §f" + q.title() + " §8- §7" + q.objective() + " " + progress(d));
    }

    private void announce(Player p, PlayerData d) {
        Quest q = current(d);
        if (q == null || !p.isOnline()) return;
        p.sendMessage("");
        p.sendMessage(Txt.PREFIX + "§e§lNouvelle quête : §f" + q.title());
        say(p, q.giver(), q.intro());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.sendMessage(Txt.PREFIX + "§7Objectif : §f" + q.objective() + (q.amount() > 1 ? " §7(" + q.amount() + ")" : ""));
        }, q.intro().size() * 50L);
        p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
    }

    private void add(Player p, Obj obj, String target, double amount) {
        PlayerData d = plugin.store.player(p);
        Quest q = current(d);
        if (q == null || q.obj() != obj) return;
        if (!q.target().isEmpty()) {
            boolean match = q.target().endsWith("_") ? target.startsWith(q.target()) : q.target().equals(target);
            if (!match) return;
        }
        d.questProgress += amount;
        if (d.questProgress >= q.amount()) complete(p, d, q);
    }

    /** Admin : termine la quête en cours du joueur, avec sa récompense. Renvoie le titre, ou null s'il n'y en a pas. */
    String skip(Player p) {
        PlayerData d = plugin.store.player(p);
        Quest q = current(d);
        if (q == null || q.obj() == Obj.NONE) return null;
        complete(p, d, q);
        return q.title();
    }

    private void complete(Player p, PlayerData d, Quest q) {
        Company c = plugin.store.company(p);
        c.money += EemiAbysse.gain(q.money());
        for (String s : q.items()) {
            String[] kv = s.split(":");
            for (ItemStack rest : p.getInventory().addItem(Items.make(kv[0], Integer.parseInt(kv[1]))).values())
                p.getWorld().dropItem(p.getLocation(), rest);
        }
        if (q.job() != null) jobXp(p, q.job(), q.xp());
        p.sendTitlePart(TitlePart.TITLE, Txt.c("§aQuête terminée"));
        p.sendTitlePart(TitlePart.SUBTITLE, Txt.c("§f" + q.title()));
        p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
        StringBuilder r = new StringBuilder(Txt.PREFIX + "§aQuête terminée : §f" + q.title());
        if (q.money() > 0) r.append(" §7(+").append(Txt.money(EemiAbysse.gain(q.money()))).append("§7)");
        p.sendMessage(r.toString());
        d.quest++;
        d.questProgress = 0;
        Bukkit.getScheduler().runTaskLater(plugin, () -> announce(p, d), 60L);
        // objectifs déjà remplis au moment où la quête commence
        Quest next = current(d);
        if (next != null) {
            if (next.obj() == Obj.PLOT && c.plot >= 0) add(p, Obj.PLOT, "", 1);
            else if (next.obj() == Obj.BUY && c.machines.values().stream().anyMatch(m -> m.type.equals(next.target()))) add(p, Obj.BUY, next.target(), 1);
            else if (next.obj() == Obj.PAGES) {
                d.questProgress = d.pages.size();
                if (d.questProgress >= next.amount()) complete(p, d, next);
            }
        }
    }

    void onEnterStrata(Player p, int n) {
        add(p, Obj.ENTER, String.valueOf(n), 1);
    }

    void onMine(Player p, String item, int n) {
        add(p, Obj.MINE, item, n);
        int xp = switch (item) {
            case "charbon" -> 1;
            case "minerai_cuivre", "spore_lumineuse" -> 2;
            case "minerai_fer" -> 3;
            case "minerai_cobalt", "cristal_resonant" -> 6;
            case "minerai_enderite" -> 9;
            case "eclat_neant" -> 14;
            default -> 4;

        };
        jobXp(p, "mineur", xp * n);
    }

    void onSell(Player p, double gain) {
        add(p, Obj.SELL, "", gain);
    }

    void onPlot(Player p) {
        add(p, Obj.PLOT, "", 1);
    }

    void onProduce(Company c, String item, int n) {
        for (UUID u : c.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) add(p, Obj.PRODUCE, item, n);
        }
    }

    void onKill(Player p, int strata) {
        add(p, Obj.KILL, String.valueOf(strata), 1);
    }

    void onBuyMachine(Player p, String type) {
        add(p, Obj.BUY, type, 1);
    }

    void onPage(Player p) {
        PlayerData d = plugin.store.player(p);
        Quest q = current(d);
        if (q == null || q.obj() != Obj.PAGES) return;
        d.questProgress = d.pages.size();
        if (d.questProgress >= q.amount()) complete(p, d, q);
    }

    void onGuardian(Player p, int strata) {
        add(p, Obj.GUARDIAN, String.valueOf(strata), 1);
    }

    void onDrill(Player p) {
        add(p, Obj.DRILL, "", 1);
    }

    // ------------------------------------------------------------------ métiers

    static final int MAX_LEVEL = 50;

    /** Récompense d'un palier de métier (tous les 5 niveaux) : texte, éclats, objet. */
    record Palier(String text, int money, String item, int amount) {
        /** Texte affiché : les éclats suivent le réglage {@code gains}. */
        String shown() {
            return money > 0 && item == null ? Txt.money(EemiAbysse.gain(money)) : text;
        }
    }

    private static Palier money(int n) {
        return new Palier(Txt.money(n), n, null, 0);
    }

    private static Palier item(String id, int n, String text) {
        return new Palier(text, 0, id, n);
    }

    private static Palier bonus(String text) {
        return new Palier(text, 0, null, 0);
    }

    static final Map<String, Map<Integer, Palier>> PALIERS = Map.of(
            "mineur", Map.of(5, item("fusee", 3, "3 Fusées de détresse"), 10, money(600),
                    15, item("dynamite", 8, "8 Dynamites"), 20, money(1000), 25, bonus("§bCélérité I §7dans les strates"),
                    30, money(2500), 35, item("dynamite", 24, "24 Dynamites"), 40, money(5000),
                    45, money(8000), 50, money(15000)),
            "ingenieur", Map.of(5, item("m_entrepot", 1, "Entrepôt"), 10, item("m_quai_expedition", 1, "Quai d'expédition"),
                    15, money(1000), 20, item("m_foreuse", 1, "Foreuse"), 25, item("kit_reparation", 3, "3 Kits de réparation"),
                    30, item("m_laveur", 1, "Laveur"), 35, money(3000), 40, item("m_foreuse", 1, "Foreuse"),
                    45, item("kit_reparation", 5, "5 Kits de réparation"), 50, money(8000)),
            "chasseur", Map.of(5, money(300), 10, money(500), 15, money(700),
                    20, money(800), 25, money(1500), 30, money(2000),
                    35, money(2500), 40, money(4000), 45, money(6000), 50, money(12000)));

    /** XP totale pour atteindre ce niveau depuis le niveau 1. */
    static int totalFor(int level) {
        int t = 0;
        for (int l = 1; l < level; l++) t += need(l);
        return t;
    }

    /** Donne les paliers franchis entre deux niveaux. */
    private void grantPaliers(Player p, String job, int from, int to) {
        Map<Integer, Palier> map = PALIERS.getOrDefault(job, Map.of());
        for (int l = from + 1; l <= to; l++) {
            Palier pa = map.get(l);
            if (pa == null) continue;
            if (pa.money() > 0) plugin.store.company(p).money += EemiAbysse.gain(pa.money());
            if (pa.item() != null)
                for (ItemStack rest : p.getInventory().addItem(Items.make(pa.item(), pa.amount())).values()) p.getWorld().dropItem(p.getLocation(), rest);
            p.sendMessage(Txt.PREFIX + "§6Palier " + jobName(job) + " " + l + " : §f" + pa.shown());
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    static String jobName(String job) {
        return switch (job) {
            case "mineur" -> "Mineur";
            case "ingenieur" -> "Ingénieur";
            case "chasseur" -> "Chasseur";
            default -> job;
        };
    }

    /**
     * XP pour passer du niveau {@code level} au suivant : courbe exponentielle (+15 % par niveau, façon Hypixel
     * SkyBlock). Niveau 10 ≈ 1 700 XP au total, 15 ≈ 4 000, 25 ≈ 18 000, 50 ≈ 630 000.
     */
    static int need(int level) {
        return (int) Math.round(100 * Math.pow(1.15, level - 1));
    }

    int level(PlayerData d, String job) {
        int xp = d.xp(job), lvl = 1;
        while (lvl < MAX_LEVEL && xp >= need(lvl)) {
            xp -= need(lvl);
            lvl++;
        }
        return lvl;
    }

    /** XP restant dans le niveau en cours et XP du niveau. */
    int[] levelProgress(PlayerData d, String job) {
        int xp = d.xp(job), lvl = 1;
        while (lvl < MAX_LEVEL && xp >= need(lvl)) {
            xp -= need(lvl);
            lvl++;
        }
        return new int[]{xp, need(lvl)};
    }

    void jobXp(Player p, String job, int amount) {
        if (amount <= 0) return;
        PlayerData d = plugin.store.player(p);
        int before = level(d, job);
        d.jobXp.merge(job, amount, Integer::sum);
        int after = level(d, job);
        if (after > before) {
            p.sendMessage(Txt.PREFIX + "§b" + jobName(job) + " §7passe au niveau §f" + after + " §7! " + bonus(job, after));
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
            grantPaliers(p, job, before, after);
        } else if (!job.equals("ingenieur")) {
            int[] lp = levelProgress(d, job);
            p.sendActionBar(Txt.c("§b+" + amount + " " + jobName(job) + " §7(" + lp[0] + "/" + lp[1] + ")"));
        }
    }

    void jobXpCompany(Company c, String job, int amount) {
        for (UUID u : c.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) jobXp(p, job, amount);
        }
    }

    static String bonus(String job, int lvl) {
        return switch (job) {
            case "mineur" -> "§8(" + Txt.num(lvl * 0.4) + " % de minerai double" + (lvl >= 25 ? ", Célérité I" : "") + ")";
            case "ingenieur" -> "§8(machines +" + Txt.num(lvl * 0.4) + " %)";
            case "chasseur" -> "§8(+" + Txt.num(lvl * 0.5) + " % de dégâts dans l'Abysse)";
            default -> "";
        };
    }

    void openJobs(Player p) {
        PlayerData d = plugin.store.player(p);
        Menu m = new Menu(3, "§bMétiers");
        int slot = 11;
        for (String job : Store.JOBS) {
            int lvl = level(d, job);
            int[] lp = levelProgress(d, job);
            Material icon = switch (job) {
                case "mineur" -> Material.IRON_PICKAXE;
                case "ingenieur" -> Material.COMPARATOR;
                default -> Material.IRON_SWORD;
            };
            List<String> lore = new ArrayList<>();
            lore.add(lvl >= MAX_LEVEL ? "§aNiveau maximum" : "§7" + lp[0] + " / " + lp[1] + " XP " + Txt.bar((double) lp[0] / lp[1], 15, "§b", "§8"));
            lore.add("§7XP totale : §f" + d.xp(job));
            lore.add(bonus(job, lvl));
            lore.add("§7Gagné en : §f" + howTo(job));
            Integer next = null;
            for (int l = lvl + 1; l <= MAX_LEVEL && next == null; l++) if (PALIERS.getOrDefault(job, Map.of()).containsKey(l)) next = l;
            if (next != null) lore.add("§6Prochain palier (niv. " + next + ") : §f" + PALIERS.get(job).get(next).shown());
            lore.add("");
            lore.add("§eClic : voir tous les niveaux");
            m.set(slot, Txt.icon(icon, "§b" + jobName(job) + " §fniveau " + lvl, lore), (pl, t) -> openLevels(pl, job));
            slot += 2;
        }
        m.set(15 + 2, Txt.icon(Material.GRAY_DYE, "§8Marchand, Forgeron, Explorateur", "§7Bientôt."));
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.open(p);
    }

    static String howTo(String job) {
        return switch (job) {
            case "mineur" -> "minant dans les strates";
            case "ingenieur" -> "faisant tourner tes machines";
            case "chasseur" -> "tuant les monstres de l'Abysse";
            default -> "?";
        };
    }

    /** Les 50 niveaux d'un métier : XP requise, bonus, paliers ; atteints en vert, en cours en jaune. */
    void openLevels(Player p, String job) {
        PlayerData d = plugin.store.player(p);
        int lvl = level(d, job);
        Menu m = new Menu(6, "§b" + jobName(job) + " §8- niveaux");
        Map<Integer, Palier> pal = PALIERS.getOrDefault(job, Map.of());
        for (int l = 1; l <= MAX_LEVEL; l++) {
            boolean done = l <= lvl, cur = l == lvl + 1;
            Palier pa = pal.get(l);
            Material mat = pa != null ? (done ? Material.GOLD_BLOCK : Material.CHEST)
                    : done ? Material.LIME_STAINED_GLASS_PANE : cur ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            List<String> lore = new ArrayList<>();
            lore.add("§7XP totale requise : §f" + totalFor(l));
            lore.add(bonus(job, l));
            if (pa != null) lore.add("§6Palier : §f" + pa.shown());
            if (job.equals("ingenieur"))
                for (Map.Entry<String, Integer> u : Machines.UNLOCK.entrySet())
                    if (u.getValue() == l) lore.add("§bDébloque : §f" + Bourse.strip(Machines.TYPES.get(u.getKey()).name()) + " §7(chez Margaux)");
            lore.add(done ? "§aAtteint" : cur ? "§eEn cours : " + levelProgress(d, job)[0] + " / " + need(lvl) + " XP" : "§8Pas encore atteint");
            // niveaux 1 à 48 en 0..47 ; Retour et Fermer en 48-49 ; niveaux 49-50 en 50-51
            m.set(l <= 48 ? l - 1 : l + 1, Txt.icon(mat, (done ? "§a" : cur ? "§e" : "§7") + "Niveau " + l, lore));
        }
        m.set(53, Txt.icon(Material.EXPERIENCE_BOTTLE, "§b" + jobName(job) + " §fniveau " + lvl, "§7XP totale : §f" + d.xp(job), "§7Gagné en " + howTo(job)));
        m.nav((pl, t) -> openJobs(pl), "Métiers");
        m.open(p);
    }

    void openJournal(Player p) {
        PlayerData d = plugin.store.player(p);
        Menu m = new Menu(3, "§eJournal");
        Quest q = current(d);
        if (q != null) {
            List<String> l = new ArrayList<>(Txt.wrap(q.objective(), "§f"));
            l.add(progress(d));
            l.add("");
            l.add("§7Donnée par §6" + Npcs.def(q.giver()).name());
            if (q.money() > 0) l.add("§7Récompense : " + Txt.money(EemiAbysse.gain(q.money())));
            if (q.obj() == Obj.GUARDIAN) {
                l.add("");
                l.add("§6Indice : §7l'antre est au bout de la galerie");
                l.add("§7éclairée de lanternes qui part du camp.");
                l.add("§7Dans la strate, une flèche en bas de l'écran");
                l.add("§7t'indique la direction et la distance.");
            }
            if (q.obj() == Obj.PAGES) {
                l.add("");
                l.add("§6Indice : §7les pages sont dans les tonneaux");
                l.add("§7des campements abandonnés (feu de camp éteint).");
                l.add("§7Chaque tonneau est à toi seul. Dans une strate,");
                l.add("§7une flèche en bas de l'écran montre le plus proche.");
            }
            m.set(11, Txt.icon(Material.WRITABLE_BOOK, "§e" + q.title(), l));
        } else m.set(11, Txt.icon(Material.BOOK, "§eParle à Gaspard", "§7Le vieux mineur, au sud de la place", "§7de Bordgouffre (juste devant l'arrivée)."));
        m.set(13, Txt.icon(Material.FILLED_MAP, "§fCarnets de Varenne", "§7Pages trouvées : §f" + d.pages.size() + " / " + PAGES * Gen.STRATAS.length,
                "§7Inès les garde pour toi."), (pl, t) -> plugin.npcs.carnets(pl));
        m.set(15, Txt.icon(Material.EXPERIENCE_BOTTLE, "§bMétiers"), (pl, t) -> openJobs(pl));
        m.set(26, Txt.icon(Material.DIAMOND_SWORD, "§7Monstres tués : §f" + d.kills, "§7Strate la plus profonde : §f" + d.unlocked));
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.open(p);
    }
}
