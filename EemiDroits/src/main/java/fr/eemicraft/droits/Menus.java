package fr.eemicraft.droits;

import fr.eemicraft.droits.ui.Fonds;
import fr.eemicraft.droits.ui.Ui;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.function.Consumer;

/**
 * Interfaces de /droits au style des menus du lobby (fonds dessinés dans le pack, police eemicraft:droits,
 * générés par pack/gen_droits.py). Boutons = objet invisible eemicraft:nav/vide ; listes = vrais objets
 * (têtes, blocs) posés dans les cases dessinées, avec un cadre de couleur en calque.
 */
final class Menus implements Listener {

    static final String BETA = "eemidroits.beta";
    private static final TextColor VERT = TextColor.color(0x6CE07C), ROUGE = TextColor.color(0xFF6B6B),
            OR = TextColor.color(0xFFC94A), ROSE = TextColor.color(0xD6A6FF);

    private final EemiDroits pl;

    Menus(EemiDroits pl) { this.pl = pl; }

    /** Un écran ouvert : menu, joueur visé, page et action de chaque case. */
    static final class Ecran implements InventoryHolder {
        final String menu;
        final UUID cible;
        final String cibleNom;
        final int page;
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inv;
        Ecran(String menu, UUID cible, String cibleNom, int page) {
            this.menu = menu; this.cible = cible; this.cibleNom = cibleNom; this.page = page;
        }
        @Override public Inventory getInventory() { return inv; }
    }

    /** Un élément d'une liste : l'objet affiché, son action au clic, et le cadre (ok, chef, non ou null). */
    private record Element(ItemStack item, String action, String cadre) { }

    // ------------------------------------------------------------------ outils

    private Ecran ecran(String menu, UUID cible, String nom, int page, List<String> calques, List<Ui.Ligne> lignes) {
        Ecran e = new Ecran(menu, cible, nom, page);
        e.inv = Bukkit.createInventory(e, Fonds.MENUS.get(menu).lignes() * 9, Ui.title(menu, calques, lignes));
        bouton(e, "fermer", "§cFermer", List.of(), "fermer");
        return e;
    }

    private static ItemStack invisible(String nom, List<String> lore) {
        ItemStack it = Txt.objet(Material.PAPER, nom, lore, false);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(Ui.MODEL_VIDE);
        it.setItemMeta(m);
        return it;
    }

    /** Pose l'objet invisible d'un bouton sur toutes ses cases. */
    private void bouton(Ecran e, String id, String nom, List<String> lore, String action) {
        int[] slots = Fonds.MENUS.get(e.menu).boutons().get(id);
        if (slots == null) return;
        ItemStack it = invisible(nom, lore);
        for (int s : slots) {
            e.inv.setItem(s, it);
            if (action != null) e.actions.put(s, action);
        }
    }

    private static int[] cases(String menu, String id) {
        return Fonds.MENUS.get(menu).cases().get(id);
    }

    private static ItemStack tete(UUID id, String nom, List<String> lore) {
        ItemStack it = Txt.objet(Material.PLAYER_HEAD, nom, lore, false);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        it.setItemMeta(m);
        return it;
    }

    private static String nomDe(UUID id, String defaut) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : defaut;
    }

    private void son(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    /** Liste de 28 cases avec pages, bouton retour et cadres. */
    private void liste(Player v, String menu, UUID cible, String nom, int page, List<Element> el, String haut,
                       String retour, String info) {
        int[] slots = cases(menu, "liste");
        int pages = Math.max(1, (el.size() + slots.length - 1) / slots.length);
        page = Math.max(0, Math.min(page, pages - 1));
        List<String> calques = new ArrayList<>();
        for (int i = 0; i < slots.length && page * slots.length + i < el.size(); i++) {
            String cadre = el.get(page * slots.length + i).cadre();
            if (cadre != null) calques.add(cadre + ":liste:" + i);
        }
        List<Ui.Ligne> lignes = new ArrayList<>();
        lignes.add(new Ui.Ligne("haut", haut));
        if (pages > 1) lignes.add(new Ui.Ligne("page", (page + 1) + "/" + pages));
        Ecran e = ecran(menu, cible, nom, page, calques, lignes);
        for (int i = 0; i < slots.length && page * slots.length + i < el.size(); i++) {
            Element x = el.get(page * slots.length + i);
            e.inv.setItem(slots[i], x.item());
            if (x.action() != null) e.actions.put(slots[i], x.action());
        }
        bouton(e, "info", "§bAide", info == null ? List.of() : List.of(info.split("\n")), null);
        bouton(e, "retour", "§eRetour", List.of(), retour);
        if (page > 0) bouton(e, "prec", "§ePage précédente", List.of(), "page:-1");
        if (page + 1 < pages) bouton(e, "suiv", "§ePage suivante", List.of(), "page:+1");
        v.openInventory(e.inv);
    }

    // ------------------------------------------------------------------ accueil

    void accueil(Player v) {
        int beta = 0, maint = 0;
        for (Jeux.Jeu j : pl.jeux.jeux.values()) {
            Jeux.Etat s = pl.jeux.etat(j.id);
            if (s == Jeux.Etat.BETA) beta++; else if (s == Jeux.Etat.MAINTENANCE) maint++;
        }
        int enLigne = Bukkit.getOnlinePlayers().size(), betaEnLigne = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(BETA)) betaEnLigne++;
        int interdits = 0;
        for (Fiches.Fiche f : pl.fiches.fiches.values()) if (!f.vide()) interdits++;
        List<Ui.Ligne> l = List.of(
                new Ui.Ligne("haut", "Gestion des droits"),
                new Ui.Ligne("s_joueurs", enLigne + " en ligne", VERT),
                new Ui.Ligne("s_jeux", beta + maint == 0 ? "Tous ouverts" : beta + " bêta · " + maint + " maint.",
                        beta + maint == 0 ? VERT : OR),
                new Ui.Ligne("s_betas", betaEnLigne + " en ligne", ROSE),
                new Ui.Ligne("s_interdits", interdits == 0 ? "Personne" : interdits + " joueur" + (interdits > 1 ? "s" : ""),
                        interdits == 0 ? null : ROUGE),
                new Ui.Ligne("bas", "Clique une catégorie"));
        Ecran e = ecran("droits", null, null, 0, List.of(), l);
        bouton(e, "info", "§bGestion des droits", List.of("§7Droits, tag Bêta, mondes et jeux", "§7interdits joueur par joueur,",
                "§7état des jeux (ouvert, bêta, maintenance).", "", "§8Hors ligne : /droits joueur <pseudo>"), null);
        bouton(e, "joueurs", "§eJoueurs", List.of("§7Joueurs en ligne et fiches enregistrées.", "", "§eClic §7: ouvrir"), "nav:joueurs");
        bouton(e, "jeux", "§eÉtat des jeux", List.of("§7Ouvert, bêta ou maintenance.", "", "§eClic §7: ouvrir"), "nav:jeux");
        bouton(e, "betas", "§dBêta-testeurs", List.of("§7Joueurs qui ont le tag Bêta.", "", "§eClic §7: ouvrir"), "nav:betas");
        bouton(e, "interdits", "§cInterdits", List.of("§7Joueurs avec des mondes ou des jeux", "§7interdits.", "", "§eClic §7: ouvrir"), "nav:interdits");
        v.openInventory(e.inv);
    }

    // ------------------------------------------------------------------ listes de joueurs

    private Element elementJoueur(UUID id, String nom, boolean beta) {
        Player en = Bukkit.getPlayer(id);
        Fiches.Fiche f = pl.fiches.fiches.get(id);
        boolean interdit = f != null && !f.vide();
        List<String> lore = new ArrayList<>();
        lore.add(en != null ? "§aEn ligne §7(" + en.getWorld().getName() + ")" : "§8Hors ligne");
        if (beta) lore.add("§dBêta-testeur");
        if (f != null && !f.mondes.isEmpty()) lore.add("§c" + f.mondes.size() + " monde(s) interdit(s)");
        if (f != null && !f.jeux.isEmpty()) lore.add("§c" + f.jeux.size() + " jeu(x) interdit(s)");
        lore.add("");
        lore.add("§eClic §7: ouvrir sa fiche");
        return new Element(tete(id, (beta ? "§d" : "§f") + nom, lore), "joueur:" + id + ":" + nom,
                beta ? "chef" : interdit ? "non" : null);
    }

    void joueurs(Player v, int page) {
        LinkedHashMap<UUID, String> l = new LinkedHashMap<>();
        List<Player> en = new ArrayList<>(Bukkit.getOnlinePlayers());
        en.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        for (Player p : en) l.put(p.getUniqueId(), p.getName());
        List<Map.Entry<UUID, Fiches.Fiche>> hors = new ArrayList<>(pl.fiches.fiches.entrySet());
        hors.sort(Comparator.comparing(x -> x.getValue().nom.toLowerCase(Locale.ROOT)));
        for (Map.Entry<UUID, Fiches.Fiche> x : hors) if (!x.getValue().vide()) l.putIfAbsent(x.getKey(), x.getValue().nom);
        List<Element> el = new ArrayList<>();
        for (Map.Entry<UUID, String> x : l.entrySet()) {
            Player p = Bukkit.getPlayer(x.getKey());
            el.add(elementJoueur(x.getKey(), x.getValue(), p != null && p.hasPermission(BETA)));
        }
        liste(v, "droits_joueurs", null, null, page, el, en.size() + " en ligne", "nav:accueil",
                "§7Cadre §6or §7: bêta-testeur\n§7Cadre §crouge §7: mondes ou jeux interdits\n\n§8Hors ligne : /droits joueur <pseudo>");
    }

    void interdits(Player v, int page) {
        List<Element> el = new ArrayList<>();
        List<Map.Entry<UUID, Fiches.Fiche>> l = new ArrayList<>(pl.fiches.fiches.entrySet());
        l.sort(Comparator.comparing(x -> x.getValue().nom.toLowerCase(Locale.ROOT)));
        for (Map.Entry<UUID, Fiches.Fiche> x : l) {
            if (x.getValue().vide()) continue;
            Element base = elementJoueur(x.getKey(), x.getValue().nom, false);
            el.add(new Element(base.item(), base.action(), "non"));
        }
        liste(v, "droits_interdits", null, null, page, el, el.isEmpty() ? "Personne" : el.size() + " joueur(s)",
                "nav:accueil", "§7Joueurs qui ont au moins un monde\n§7ou un jeu interdit.");
    }

    void betas(Player v, int page) {
        pl.lp.getUserManager().searchAll(NodeMatcher.key(BETA)).thenAccept(res -> Bukkit.getScheduler().runTask(pl, () -> {
            if (!v.isOnline()) return;
            TreeMap<String, UUID> tri = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (Map.Entry<UUID, ? extends Collection<? extends Node>> x : res.entrySet()) {
                boolean actif = false;
                for (Node n : x.getValue()) if (n.getValue() && n.getContexts().isEmpty()) actif = true;
                if (actif) tri.put(nomDe(x.getKey(), x.getKey().toString().substring(0, 8)), x.getKey());
            }
            List<Element> el = new ArrayList<>();
            for (Map.Entry<String, UUID> x : tri.entrySet()) el.add(elementJoueur(x.getValue(), x.getKey(), true));
            liste(v, "droits_betas", null, null, page, el, el.isEmpty() ? "Aucun testeur" : el.size() + " testeur(s)",
                    "nav:accueil", "§7Joueurs avec le tag Bêta : ils peuvent\n§7entrer dans les jeux en bêta.\n\n§8/droits beta <pseudo> on");
        }));
    }

    // ------------------------------------------------------------------ fiche d'un joueur

    void joueur(Player v, UUID id, String nom) {
        avecUser(v, id, nom, u -> fiche(v, u, id, nom));
    }

    private void avecUser(Player v, UUID id, String nom, Consumer<User> suite) {
        pl.lp.getUserManager().loadUser(id, nom).thenAccept(u -> Bukkit.getScheduler().runTask(pl, () -> {
            if (v.isOnline()) suite.accept(u);
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(pl, () -> v.sendMessage(Txt.PREFIXE + "§cImpossible de charger " + nom + " : " + ex.getMessage()));
            return null;
        });
    }

    /** 1 = accordé, -1 = refusé, 0 = pas réglé (permissions globales seulement). */
    static int etatDroit(User u, List<String> perms) {
        int vrai = 0, faux = 0;
        for (String p : perms) {
            for (PermissionNode n : u.getNodes(NodeType.PERMISSION)) {
                if (!n.getContexts().isEmpty() || !n.getPermission().equalsIgnoreCase(p)) continue;
                if (n.getValue()) vrai++; else faux++;
            }
        }
        if (faux > 0) return -1;
        return vrai >= perms.size() && !perms.isEmpty() ? 1 : 0;
    }

    private boolean effectif(UUID id, User u, String perm) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.hasPermission(perm);
        return u.getCachedData().getPermissionData().checkPermission(perm).asBoolean();
    }

    private void fiche(Player v, User u, UUID id, String nom) {
        Player en = Bukkit.getPlayer(id);
        Fiches.Fiche f = pl.fiches.fiches.get(id);
        List<String> groupes = new ArrayList<>();
        for (InheritanceNode n : u.getNodes(NodeType.INHERITANCE)) if (n.getContexts().isEmpty()) groupes.add(n.getGroupName());
        int nbInterdits = f == null ? 0 : f.mondes.size() + f.jeux.size();
        boolean beta = etatDroit(u, List.of(BETA)) == 1;

        // droits du catalogue : vrais objets dans les cases, cadre vert (accordé), rouge (refusé), or (déjà par groupe/op)
        int[] slots = cases("droits_fiche", "droits");
        List<String> calques = new ArrayList<>();
        if (beta) calques.add("sel:beta");
        List<ItemStack> items = new ArrayList<>();
        List<String> cles = new ArrayList<>();
        ConfigurationSection droits = pl.getConfig().getConfigurationSection("droits");
        if (droits != null) for (String k : droits.getKeys(false)) {
            ConfigurationSection d = droits.getConfigurationSection(k);
            if (d == null) continue;
            if (items.size() >= slots.length) {
                pl.getLogger().warning("Plus de " + slots.length + " droits dans le config : " + k + " et les suivants ne sont pas affiches.");
                break;
            }
            List<String> perms = d.getStringList("permissions");
            int s = etatDroit(u, perms);
            boolean eff = !perms.isEmpty() && effectif(id, u, perms.get(0));
            if (s == 1) calques.add("ok:droits:" + items.size());
            else if (s == -1) calques.add("non:droits:" + items.size());
            else if (eff) calques.add("chef:droits:" + items.size());
            Material ic = Material.matchMaterial(d.getString("icone", "PAPER"));
            List<String> lore = new ArrayList<>();
            if (!d.getString("description", "").isEmpty()) lore.add("§7" + d.getString("description"));
            lore.add("§8" + String.join(", ", perms));
            lore.add("");
            lore.add(s == 1 ? "§a✔ Accordé" : s == -1 ? "§c✘ Refusé" : eff ? "§6● A déjà le droit §8(groupe ou op)" : "§7— Pas réglé");
            lore.add("");
            lore.add("§eClic gauche §7: " + (s == 1 ? "retirer" : "accorder"));
            lore.add("§eClic droit §7: " + (s == -1 ? "retirer le refus" : "refuser"));
            String coul = s == 1 ? "§a" : s == -1 ? "§c" : eff ? "§6" : "§f";
            items.add(Txt.objet(ic == null || !ic.isItem() ? Material.PAPER : ic, coul + d.getString("nom", k), lore, false));
            cles.add(k);
        }

        List<Ui.Ligne> lignes = List.of(
                new Ui.Ligne("haut", nom + " · " + (en != null ? en.getWorld().getName() : "hors ligne")),
                new Ui.Ligne("resume", nbInterdits == 0 ? "Aucun interdit" : nbInterdits + " interdit" + (nbInterdits > 1 ? "s" : ""),
                        nbInterdits == 0 ? VERT : ROUGE),
                new Ui.Ligne("bas", "Gauche : accorder · droit : refuser"));
        Ecran e = ecran("droits_fiche", id, nom, 0, calques, lignes);
        for (int i = 0; i < items.size(); i++) {
            e.inv.setItem(slots[i], items.get(i));
            e.actions.put(slots[i], "droit:" + cles.get(i));
        }
        List<String> resume = new ArrayList<>();
        resume.add(en != null ? "§aEn ligne §7dans §f" + en.getWorld().getName() : "§8Hors ligne");
        resume.add("§7Groupes : §f" + (groupes.isEmpty() ? "default" : String.join(", ", groupes)));
        if (en != null && en.isOp()) resume.add("§6Opérateur §7(a tous les droits non refusés)");
        e.inv.setItem(cases("droits_fiche", "tete")[0], tete(id, "§f" + nom, resume));
        bouton(e, "info", "§bFiche de " + nom, List.of("§7Cadre §avert §7: droit accordé", "§7Cadre §crouge §7: droit refusé",
                "§7Cadre §6or §7: a déjà le droit (groupe ou op)"), null);
        bouton(e, "beta", beta ? "§d§lTag Bêta : OUI" : "§7Tag Bêta : non",
                List.of("§7Accès aux jeux en bêta.", "", beta ? "§eClic §7: retirer le tag" : "§eClic §7: donner le tag"), "beta");
        bouton(e, "groupes", "§eGroupes", List.of("§7Groupes LuckPerms :", "§f" + (groupes.isEmpty() ? "default" : String.join(", ", groupes)),
                "", "§eClic §7: ouvrir"), "nav:groupes");
        bouton(e, "mondes", "§eMondes interdits", List.of("§7Le joueur ne peut pas y entrer.",
                "§f" + (f == null || f.mondes.isEmpty() ? "aucun" : String.join(", ", f.mondes)), "", "§eClic §7: ouvrir"), "nav:mondes");
        bouton(e, "jeux", "§eJeux interdits", List.of("§7Le joueur ne peut pas les rejoindre.",
                "§f" + (f == null || f.jeux.isEmpty() ? "aucun" : String.join(", ", f.jeux)), "", "§eClic §7: ouvrir"), "nav:jeuxjoueur");
        bouton(e, "retour", "§eRetour", List.of(), "nav:joueurs");
        v.openInventory(e.inv);
    }

    private void groupes(Player v, User u, UUID id, String nom, int page) {
        Set<String> a = new HashSet<>();
        for (InheritanceNode n : u.getNodes(NodeType.INHERITANCE)) if (n.getContexts().isEmpty()) a.add(n.getGroupName().toLowerCase(Locale.ROOT));
        List<Group> gs = new ArrayList<>(pl.lp.getGroupManager().getLoadedGroups());
        gs.sort(Comparator.comparingInt((Group g) -> -g.getWeight().orElse(0)).thenComparing(Group::getName));
        List<Element> el = new ArrayList<>();
        for (Group g : gs) {
            boolean dedans = a.contains(g.getName().toLowerCase(Locale.ROOT));
            String affiche = g.getDisplayName() != null ? g.getDisplayName() : g.getName();
            el.add(new Element(Txt.objet(dedans ? Material.NAME_TAG : Material.PAPER, (dedans ? "§a" : "§7") + affiche,
                    List.of("§8" + g.getName() + (g.getWeight().isPresent() ? " (poids " + g.getWeight().getAsInt() + ")" : ""),
                            "", dedans ? "§a✔ Dans ce groupe" : "§7Pas dans ce groupe", "", "§eClic §7: " + (dedans ? "retirer" : "ajouter")), false),
                    "groupe:" + g.getName(), dedans ? "ok" : null));
        }
        liste(v, "droits_groupes", id, nom, page, el, nom + " · " + a.size() + " groupe(s)", "joueur:" + id + ":" + nom,
                "§7Cadre §avert §7: le joueur est dans ce groupe.");
    }

    private void mondes(Player v, UUID id, String nom, int page) {
        TreeSet<String> noms = new TreeSet<>();
        for (World w : Bukkit.getWorlds()) noms.add(Jeux.base(w.getName()));
        Fiches.Fiche f = pl.fiches.fiches.get(id);
        if (f != null) noms.addAll(f.mondes);
        List<Element> el = new ArrayList<>();
        for (String m : noms) {
            boolean prot = pl.garde.estProtege(m);
            boolean interdit = f != null && f.mondes.contains(m);
            Jeux.Jeu j = pl.jeux.jeuDuMonde(m);
            List<String> lore = new ArrayList<>();
            if (j != null) lore.add("§7Jeu : " + j.nom);
            lore.add("§8Copies _2, _3 comprises");
            lore.add("");
            if (prot) lore.add("§6Protégé : ne peut pas être interdit");
            else {
                lore.add(interdit ? "§c✘ Interdit" : "§a✔ Autorisé");
                lore.add("");
                lore.add("§eClic §7: " + (interdit ? "autoriser" : "interdire"));
            }
            Material ic = prot ? Material.BEACON : interdit ? Material.BARRIER : j != null ? j.icone : Material.GRASS_BLOCK;
            el.add(new Element(Txt.objet(ic, (interdit ? "§c" : "§f") + m, lore, false), prot ? null : "monde:" + m,
                    interdit ? "non" : prot ? "chef" : null));
        }
        int n = f == null ? 0 : f.mondes.size();
        liste(v, "droits_mondes", id, nom, page, el, nom + " · " + n + " interdit(s)", "joueur:" + id + ":" + nom,
                "§7Cadre §crouge §7: monde interdit\n§7Cadre §6or §7: protégé (le hub)");
    }

    private void jeuxJoueur(Player v, UUID id, String nom, int page) {
        Fiches.Fiche f = pl.fiches.fiches.get(id);
        List<Element> el = new ArrayList<>();
        for (Jeux.Jeu j : pl.jeux.jeux.values()) {
            boolean interdit = f != null && f.jeux.contains(j.id);
            el.add(new Element(Txt.objet(interdit ? Material.BARRIER : j.icone, j.nom,
                    List.of("§8" + j.id, "", interdit ? "§c✘ Interdit à ce joueur" : "§a✔ Autorisé", "",
                            "§eClic §7: " + (interdit ? "autoriser" : "interdire")), false),
                    "jeuban:" + j.id, interdit ? "non" : null));
        }
        int n = f == null ? 0 : f.jeux.size();
        liste(v, "droits_jeuxj", id, nom, page, el, nom + " · " + n + " interdit(s)", "joueur:" + id + ":" + nom,
                "§7Cadre §crouge §7: jeu interdit à ce joueur.");
    }

    // ------------------------------------------------------------------ état des jeux

    void jeux(Player v, int page) {
        List<Element> el = new ArrayList<>();
        int beta = 0, maint = 0;
        for (Jeux.Jeu j : pl.jeux.jeux.values()) {
            Jeux.Etat s = pl.jeux.etat(j.id);
            if (s == Jeux.Etat.BETA) beta++; else if (s == Jeux.Etat.MAINTENANCE) maint++;
            List<String> lore = new ArrayList<>();
            lore.add("§8" + j.id + (j.mondes.isEmpty() ? "" : " - " + String.join(", ", j.mondes)));
            lore.add("");
            lore.add("§7État : " + s.nom);
            if (s == Jeux.Etat.BETA) lore.add("§7Bêta-testeurs et admins seulement");
            if (s == Jeux.Etat.MAINTENANCE) {
                lore.add("§7Admins seulement");
                if (!pl.jeux.raison(j.id).isBlank()) lore.add("§7Raison : §f" + pl.jeux.raison(j.id));
            }
            lore.add("");
            lore.add("§eClic gauche §7: " + s.suivant().nom);
            lore.add("§eClic droit §7: " + s.precedent().nom);
            lore.add("§eMaj + clic §7: §aOuvert");
            lore.add("§8Raison : /droits jeu " + j.id + " maintenance <raison>");
            el.add(new Element(Txt.objet(s == Jeux.Etat.MAINTENANCE ? Material.ANVIL : j.icone, s.couleur + "[" + s.nom.substring(2) + "] §r" + j.nom,
                    lore, s == Jeux.Etat.BETA), "etat:" + j.id, s == Jeux.Etat.BETA ? "chef" : s == Jeux.Etat.MAINTENANCE ? "non" : null));
        }
        liste(v, "droits_jeux", null, null, page, el, beta + maint == 0 ? "Tous ouverts" : beta + " bêta · " + maint + " maint.",
                "nav:accueil", "§7Cadre §6or §7: en bêta (bêta-testeurs et admins)\n§7Cadre §crouge §7: en maintenance (admins)\n\n§7Clic gauche / droit : changer l'état");
    }

    // ------------------------------------------------------------------ clics

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Ecran) e.setCancelled(true);
    }

    @EventHandler
    public void onClic(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Ecran ec)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player v)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String a = ec.actions.get(e.getRawSlot());
        if (a == null) return;
        if (!v.hasPermission("eemidroits.admin")) { v.closeInventory(); return; }
        boolean droit = e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT;
        son(v);
        String[] p = a.split(":", 3);
        switch (p[0]) {
            case "fermer" -> v.closeInventory();
            case "page" -> rouvrir(v, ec, ec.page + Integer.parseInt(p[1]));
            case "nav" -> naviguer(v, ec, p[1]);
            case "joueur" -> joueur(v, UUID.fromString(p[1]), p[2]);
            case "beta" -> modifier(v, ec, u -> basculerPerm(u, BETA), () -> "Tag Bêta de " + ec.cibleNom + " modifié.");
            case "droit" -> {
                List<String> perms = pl.getConfig().getStringList("droits." + p[1] + ".permissions");
                if (perms.isEmpty()) return;
                modifier(v, ec, u -> {
                    int s = etatDroit(u, perms);
                    for (String perm : perms) {
                        effacer(u, perm);
                        if (!droit && s != 1) u.data().add(PermissionNode.builder(perm).value(true).build());
                        if (droit && s != -1) u.data().add(PermissionNode.builder(perm).value(false).build());
                    }
                }, () -> "Droit « " + pl.getConfig().getString("droits." + p[1] + ".nom", p[1]) + " » de " + ec.cibleNom + " modifié.");
            }
            case "groupe" -> modifier(v, ec, u -> {
                boolean dedans = false;
                for (InheritanceNode x : u.getNodes(NodeType.INHERITANCE))
                    if (x.getContexts().isEmpty() && x.getGroupName().equalsIgnoreCase(p[1])) dedans = true;
                if (dedans) u.data().clear(x -> x instanceof InheritanceNode in && in.getContexts().isEmpty() && in.getGroupName().equalsIgnoreCase(p[1]));
                else u.data().add(InheritanceNode.builder(p[1]).build());
            }, () -> "Groupes de " + ec.cibleNom + " modifiés.");
            case "monde" -> {
                boolean interdit = pl.fiches.basculerMonde(ec.cible, ec.cibleNom, p[1]);
                v.sendMessage(Txt.PREFIXE + "§f" + p[1] + " §7: " + (interdit ? "§cinterdit" : "§aautorisé") + " §7pour " + ec.cibleNom + ".");
                pl.getLogger().info(v.getName() + " : monde " + p[1] + (interdit ? " interdit" : " autorise") + " pour " + ec.cibleNom);
                Player cible = Bukkit.getPlayer(ec.cible);
                if (cible != null) pl.garde.verifier(cible);
                mondes(v, ec.cible, ec.cibleNom, ec.page);
            }
            case "jeuban" -> {
                boolean interdit = pl.fiches.basculerJeu(ec.cible, ec.cibleNom, p[1]);
                Jeux.Jeu j = pl.jeux.jeu(p[1]);
                v.sendMessage(Txt.PREFIXE + (j == null ? p[1] : j.nom) + " §7: " + (interdit ? "§cinterdit" : "§aautorisé") + " §7pour " + ec.cibleNom + ".");
                pl.getLogger().info(v.getName() + " : jeu " + p[1] + (interdit ? " interdit" : " autorise") + " pour " + ec.cibleNom);
                jeuxJoueur(v, ec.cible, ec.cibleNom, ec.page);
            }
            case "etat" -> {
                Jeux.Jeu j = pl.jeux.jeu(p[1]);
                if (j == null) return;
                Jeux.Etat s = pl.jeux.etat(j.id);
                Jeux.Etat n = e.isShiftClick() ? Jeux.Etat.OUVERT : droit ? s.precedent() : s.suivant();
                if (n == s) { jeux(v, ec.page); return; }
                pl.jeux.changer(v, j, n, "");
                v.sendMessage(Txt.PREFIXE + j.nom + " §7: " + n.nom);
                // EemiCore écrit sa maintenance juste après : on rafraîchit un peu plus tard.
                Bukkit.getScheduler().runTaskLater(pl, () -> { if (v.isOnline()) jeux(v, ec.page); }, 2L);
            }
            default -> {}
        }
    }

    private void naviguer(Player v, Ecran ec, String ou) {
        switch (ou) {
            case "accueil" -> accueil(v);
            case "joueurs" -> joueurs(v, 0);
            case "jeux" -> jeux(v, 0);
            case "betas" -> betas(v, 0);
            case "interdits" -> interdits(v, 0);
            case "groupes" -> avecUser(v, ec.cible, ec.cibleNom, u -> groupes(v, u, ec.cible, ec.cibleNom, 0));
            case "mondes" -> mondes(v, ec.cible, ec.cibleNom, 0);
            case "jeuxjoueur" -> jeuxJoueur(v, ec.cible, ec.cibleNom, 0);
            default -> {}
        }
    }

    private void rouvrir(Player v, Ecran ec, int page) {
        switch (ec.menu) {
            case "droits_joueurs" -> joueurs(v, page);
            case "droits_jeux" -> jeux(v, page);
            case "droits_betas" -> betas(v, page);
            case "droits_interdits" -> interdits(v, page);
            case "droits_mondes" -> mondes(v, ec.cible, ec.cibleNom, page);
            case "droits_jeuxj" -> jeuxJoueur(v, ec.cible, ec.cibleNom, page);
            case "droits_groupes" -> avecUser(v, ec.cible, ec.cibleNom, u -> groupes(v, u, ec.cible, ec.cibleNom, page));
            default -> {}
        }
    }

    /** Modifie le joueur dans LuckPerms, puis rouvre l'écran où on était. */
    private void modifier(Player v, Ecran ec, Consumer<User> modif, java.util.function.Supplier<String> message) {
        pl.lp.getUserManager().modifyUser(ec.cible, modif::accept).thenRun(() -> Bukkit.getScheduler().runTask(pl, () -> {
            pl.getLogger().info(v.getName() + " : " + message.get());
            if (!v.isOnline()) return;
            v.sendMessage(Txt.PREFIXE + message.get());
            if (ec.menu.equals("droits_groupes")) avecUser(v, ec.cible, ec.cibleNom, u -> groupes(v, u, ec.cible, ec.cibleNom, ec.page));
            else joueur(v, ec.cible, ec.cibleNom);
        })).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(pl, () -> v.sendMessage(Txt.PREFIXE + "§cÉchec : " + ex.getMessage()));
            return null;
        });
    }

    static void effacer(User u, String perm) {
        u.data().clear(n -> n.getKey().equalsIgnoreCase(perm) && n.getContexts().isEmpty());
    }

    /** Accorde si pas accordé, sinon retire. */
    static void basculerPerm(User u, String perm) {
        boolean avait = etatDroit(u, List.of(perm)) == 1;
        effacer(u, perm);
        if (!avait) u.data().add(PermissionNode.builder(perm).value(true).build());
    }
}
