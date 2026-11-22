package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.Txt;
import fr.eemicraft.refuge.api.Bilan;
import fr.eemicraft.refuge.api.ZoneInfo;
import fr.eemicraft.refuge.ui.Menu;
import fr.eemicraft.refuge.ui.Ui;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Les menus du Refuge (fonds et dispositions : pack/gen_ui.py → ui/Fonds.java). */
final class Ecrans {

    static final TextColor DARK = TextColor.color(0x3F3F3F);
    static final TextColor GOLD = TextColor.color(0x7A5200);
    static final TextColor GREEN = TextColor.color(0x1E6E1E);
    static final TextColor RED = TextColor.color(0x8A1E1E);
    static final TextColor GREY = TextColor.color(0x7A7A7A);

    private Ecrans() { }

    static String fmt(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    static ItemStack head(UUID u, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (it.getItemMeta() instanceof SkullMeta m) {
            m.setOwningPlayer(Bukkit.getOfflinePlayer(u));
            m.displayName(Txt.item(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) l.add(Txt.item("§7" + s));
            m.lore(l);
            it.setItemMeta(m);
        }
        return Ui.display(it, List.of());
    }

    static ItemStack info(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.item(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Txt.item("§7" + s));
        meta.lore(l);
        it.setItemMeta(meta);
        return Ui.display(it, List.of());
    }

    static void deny(Player p, String msg) {
        Txt.msg(p, "§c" + msg);
        Ui.error(p);
    }

    // ================================================================== Expédition (Le Passeur)

    static final class Expedition extends Menu {
        private final Refuge r;

        Expedition(Refuge r, Player p) {
            super(p, "expedition");
            this.r = r;
        }

        @Override protected String plainTitle() { return "Expédition"; }

        @Override
        protected boolean canOpen() {
            if (r.atRefuge(viewer)) return true;
            deny(viewer, "Les expéditions partent du Refuge (/refuge).");
            return false;
        }

        @Override
        protected List<String> help() {
            return List.of("§fExpédition§7 : le chef choisit la zone et le moment (jour ou nuit).",
                    "Chaque coéquipier se met §aprêt§7, puis le chef clique §fPartir§7.",
                    "On part avec ce qu'on porte : range le reste dans ta réserve.",
                    "Loupe : chercher des coéquipiers (file publique, même zone).");
        }

        @Override
        protected void fill() {
            Escouades e = r.esc();
            Escouades.Squad s = e.ensure(viewer);
            UUID me = viewer.getUniqueId();
            boolean chef = s.leader.equals(me);
            List<ZoneInfo> zs = e.zones();
            ZoneInfo cur = e.zone(s);
            int act = r.profile(me).act();
            for (int i = 0; i < Math.min(cells("zones"), zs.size()); i++) {
                ZoneInfo z = zs.get(i);
                List<String> lore = new ArrayList<>();
                lore.add("Acte " + z.act() + (act >= z.act() ? " §a✔" : " §c(pas encore débloqué)"));
                lore.add(z.minPlayers() + " à " + z.maxPlayers() + " joueurs");
                lore.add(z.freeInstances() > 0 ? "§a" + z.freeInstances() + " place(s) libre(s)" : "§cToutes les instances sont occupées");
                lore.add("");
                lore.add(chef ? "§eClic : choisir cette zone" : "§8Le chef choisit la zone");
                ItemStack it = info(Material.FILLED_MAP, (z.equals(cur) ? "§6▶ " : "§f") + z.name(), lore);
                if (z.icon() != null && !z.icon().isBlank() && pack) {
                    NamespacedKey k = NamespacedKey.fromString(z.icon());
                    if (k != null) {
                        ItemMeta m = it.getItemMeta();
                        m.setItemModel(k);
                        it.setItemMeta(m);
                    }
                }
                cell("zones", i, it, (p, c) -> {
                    if (!chef) {
                        deny(p, "Seul le chef choisit la zone.");
                        return;
                    }
                    s.zone = z.id();
                    s.ready.clear();
                    r.esc().notifyMenus(s);
                });
                if (z.equals(cur)) overlay("cellsel:zones:" + i);
            }
            text("zone", cur == null ? "Aucune zone disponible pour l'instant"
                    : cur.name() + " · acte " + cur.act() + " · " + cur.minPlayers() + " à " + cur.maxPlayers() + " joueurs", DARK);

            button("jour", Material.SUNFLOWER, (s.night ? "§f" : "§6▶ ") + "Partir de jour",
                    List.of("Zombies plus lents, on y voit clair.", chef ? "§eClic : choisir" : "§8Le chef choisit"),
                    (p, c) -> setNight(p, s, chef, false));
            button("nuit", Material.CLOCK, (s.night ? "§6▶ " : "§f") + "Partir de nuit",
                    List.of("Plus dangereux, meilleur butin.", chef ? "§eClic : choisir" : "§8Le chef choisit"),
                    (p, c) -> setNight(p, s, chef, true));
            overlay(s.night ? "sel:nuit" : "sel:jour");

            boolean ready = chef || s.ready.contains(me);
            button("pret", ready ? Material.LIME_DYE : Material.GRAY_DYE, ready ? "§aPrêt" : "§7Pas prêt",
                    List.of(chef ? "Le chef est toujours prêt." : "Clic : prêt / pas prêt"), (p, c) -> {
                        if (chef) {
                            Txt.msg(p, "Le chef est toujours prêt : clique §fPartir§7 quand tout le monde l'est.");
                            return;
                        }
                        r.esc().toggleReady(p);
                    });
            if (ready) overlay("on:pret");

            button("partir", Material.COMPASS, chef ? "§a§lPartir" : "§7Partir",
                    chef ? List.of("Lance l'expédition pour toute l'escouade.", "Tous les coéquipiers doivent être prêts.")
                            : List.of("Seul le chef lance l'expédition."), (p, c) -> {
                        if (!chef) {
                            deny(p, "Seul le chef lance l'expédition.");
                            return;
                        }
                        String err = r.esc().depart(p);
                        if (err != null) deny(p, err);
                    });
            if (!chef) overlay("off:partir");

            for (int i = 0; i < Math.min(cells("membres"), s.members.size()); i++) {
                UUID u = s.members.get(i);
                Player m = Bukkit.getPlayer(u);
                boolean isChef = u.equals(s.leader), ok = isChef || s.ready.contains(u);
                String state = m == null ? "§cdéconnecté" : !r.atRefuge(m) ? "§cpas au Refuge"
                        : isChef ? "§6chef" : ok ? "§aprêt" : "§cpas prêt";
                cell("membres", i, head(u, "§f" + Escouades.name(u), List.of(state)), null);
                overlay((isChef ? "chef" : ok ? "ok" : "non") + ":membres:" + i);
            }
            button("escouade", Material.PLAYER_HEAD, "§fEscouade", List.of("Inviter, rejoindre, quitter…"),
                    (p, c) -> new Escouade(r, p).open());
            button("file", Material.SPYGLASS, s.searching ? "§aRecherche en cours…" : "§fChercher des coéquipiers",
                    List.of("File publique : on te réunit avec d'autres", "joueurs qui partent dans la même zone.",
                            chef ? "§eClic : lancer / arrêter" : "§8Le chef lance la recherche"),
                    (p, c) -> r.esc().toggleSearch(p));
            if (s.searching) overlay("on:file");
        }

        private void setNight(Player p, Escouades.Squad s, boolean chef, boolean night) {
            if (!chef) {
                deny(p, "Seul le chef choisit le moment.");
                return;
            }
            if (s.night == night) return;
            s.night = night;
            s.ready.clear();
            r.esc().notifyMenus(s);
        }
    }

    // ================================================================== Escouade

    static final class Escouade extends Menu {
        private final Refuge r;

        Escouade(Refuge r, Player p) {
            super(p, "escouade");
            this.r = r;
        }

        @Override protected String plainTitle() { return "Escouade"; }

        @Override
        protected boolean canOpen() {
            if (r.atRefuge(viewer)) return true;
            deny(viewer, "Les escouades se forment au Refuge (/refuge).");
            return false;
        }

        @Override
        protected List<String> help() {
            return List.of("§fEscouade§7 : 1 à 4 joueurs. Ton groupe §f/party§7 est automatiquement ton escouade.",
                    "Créer : ouvre ton escouade aux autres. Inviter : envoie une invitation cliquable.",
                    "Entrer : rejoindre une escouade ouverte. Clic sur un membre (chef) : exclure, passer chef.");
        }

        @Override
        protected void fill() {
            Escouades e = r.esc();
            Escouades.Squad s = e.of(viewer);
            UUID me = viewer.getUniqueId();
            boolean party = s != null && s.isParty();
            boolean chef = s != null && s.leader.equals(me);
            boolean alone = s == null || s.members.size() <= 1;
            button("creer", Material.WHITE_BANNER, "§fCréer une escouade", List.of(party ? "§8Ton groupe /party est ton escouade."
                    : s != null && s.open ? "§8Ton escouade est déjà ouverte." : "Ouvre ton escouade : d'autres pourront la rejoindre."),
                    (p, c) -> {
                        String err = r.esc().create(p);
                        if (err != null) deny(p, err);
                        else Txt.msg(p, "Escouade ouverte. Invite des joueurs ou attends qu'ils te rejoignent.");
                    });
            if (party || (s != null && s.open) || !alone) overlay("off:creer");
            button("inviter", Material.NAME_TAG, "§fInviter un joueur", List.of(party ? "§8Invite avec /party invite."
                    : "Joueurs présents au Refuge."), (p, c) -> {
                        if (party) {
                            deny(p, "Ton escouade est ton groupe : invite avec /party invite <joueur>.");
                            return;
                        }
                        if (s != null && !chef) {
                            deny(p, "Seul le chef peut inviter.");
                            return;
                        }
                        new Liste(r, p, true, 0).open();
                    });
            if (party || (s != null && !chef) || (s != null && s.members.size() >= Escouades.MAX)) overlay("off:inviter");
            button("rejoindre", Material.LEAD, "§fRejoindre une escouade", List.of("Escouades ouvertes au Refuge."), (p, c) -> {
                if (party) {
                    deny(p, "Tu es dans un groupe (/party) : c'est ton escouade.");
                    return;
                }
                new Liste(r, p, false, 0).open();
            });
            if (party) overlay("off:rejoindre");
            button("quitter", Material.OAK_DOOR, "§cQuitter l'escouade", List.of(party ? "§8Quitte ton groupe avec /party leave."
                    : "Tu repars seul."), (p, c) -> {
                        if (r.esc().leave(p, true)) new Escouade(r, p).open();
                    });
            if (party || s == null || (alone && !s.open)) overlay("off:quitter");
            if (s == null) return;
            for (int i = 0; i < Math.min(cells("membres"), s.members.size()); i++) {
                UUID u = s.members.get(i);
                boolean isChef = u.equals(s.leader);
                String n = Escouades.name(u);
                List<String> lore = new ArrayList<>();
                lore.add(isChef ? "§6Chef de l'escouade" : s.fromParty.contains(u) ? "Membre du groupe" : "Membre");
                if (chef && !u.equals(me)) lore.add("§eClic : exclure / passer chef");
                cell("membres", i, head(u, "§f" + n, lore), (p, c) -> {
                    if (chef && !u.equals(me)) new Membre(r, p, u).open();
                });
                text("nom" + i, n, isChef ? GOLD : DARK);
                if (isChef) overlay("chef:membres:" + i);
            }
        }
    }

    // ================================================================== listes (inviter / rejoindre)

    static final class Liste extends Menu {
        private final Refuge r;
        private final boolean invite;
        private final int page;

        Liste(Refuge r, Player p, boolean invite, int page) {
            super(p, invite ? "liste_inviter" : "liste_rejoindre");
            this.r = r;
            this.invite = invite;
            this.page = page;
        }

        @Override protected String plainTitle() { return invite ? "Inviter un joueur" : "Rejoindre une escouade"; }

        @Override protected boolean canOpen() { return r.atRefuge(viewer); }

        @Override
        protected List<String> help() {
            return invite ? List.of("Clic sur un joueur présent au Refuge : il reçoit une invitation (60 s).")
                    : List.of("Clic sur une escouade ouverte pour la rejoindre (4 joueurs au plus).");
        }

        @Override
        protected void fill() {
            int per = cells("liste");
            List<Runnable> rows = new ArrayList<>();
            List<ItemStack> its = new ArrayList<>();
            List<Menu.Action> acts = new ArrayList<>();
            if (invite) {
                Escouades.Squad mine = r.esc().of(viewer);
                for (Player o : r.world() == null ? List.<Player>of() : r.world().getPlayers()) {
                    if (o.equals(viewer) || o.hasMetadata("vanished") || r.esc().inExpe(o)) continue;
                    if (mine != null && mine.members.contains(o.getUniqueId())) continue;
                    Escouades.Squad os = r.esc().of(o);
                    String st = os == null || os.members.size() <= 1 ? "Seul" : "Dans une escouade (" + os.members.size() + ")";
                    its.add(head(o.getUniqueId(), "§f" + o.getName(), List.of(st, "§eClic : inviter")));
                    acts.add((p, c) -> {
                        String err = r.esc().invite(p, o);
                        if (err != null) deny(p, err);
                        else Txt.msg(p, "Invitation envoyée à §f" + o.getName() + "§7.");
                    });
                }
            } else {
                for (Escouades.Squad s : r.esc().openSquads(viewer)) {
                    List<String> lore = new ArrayList<>();
                    for (UUID u : s.members) lore.add("§f" + Escouades.name(u) + (u.equals(s.leader) ? " §6(chef)" : ""));
                    ZoneInfo z = r.esc().zone(s);
                    if (z != null) lore.add("Zone : §f" + z.name() + (s.night ? " §8(nuit)" : " §8(jour)"));
                    lore.add("§eClic : rejoindre");
                    its.add(head(s.leader, "§fEscouade de " + Escouades.name(s.leader) + " §8(" + s.members.size() + "/4)", lore));
                    acts.add((p, c) -> {
                        String err = r.esc().join(p, s);
                        if (err != null) deny(p, err);
                        else new Expedition(r, p).open();
                    });
                }
            }
            int pages = Math.max(1, (its.size() + per - 1) / per);
            int pg = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < per && pg * per + i < its.size(); i++) cell("liste", i, its.get(pg * per + i), acts.get(pg * per + i));
            if (its.isEmpty()) plain(22, Material.BARRIER, invite ? "§7Personne à inviter" : "§7Aucune escouade ouverte", List.of());
            button("retour", Material.ARROW, "§fRetour", List.of(), (p, c) -> new Escouade(r, p).open());
            button("prec", Material.ARROW, "§fPage précédente", List.of((pg + 1) + "/" + pages), (p, c) -> {
                if (pg > 0) new Liste(r, p, invite, pg - 1).open();
            });
            button("suiv", Material.ARROW, "§fPage suivante", List.of((pg + 1) + "/" + pages), (p, c) -> {
                if (pg < pages - 1) new Liste(r, p, invite, pg + 1).open();
            });
            text("page", (pg + 1) + "/" + pages, DARK);
            if (its.isEmpty()) text("page", "", DARK);
        }
    }

    // ================================================================== membre (actions du chef)

    static final class Membre extends Menu {
        private final Refuge r;
        private final UUID target;

        Membre(Refuge r, Player p, UUID target) {
            super(p, "membre");
            this.r = r;
            this.target = target;
        }

        @Override protected String plainTitle() { return "Membre : " + Escouades.name(target); }

        @Override protected boolean canOpen() { return r.atRefuge(viewer); }

        @Override protected List<String> help() { return List.of("Exclure ce joueur, ou lui passer le rôle de chef."); }

        @Override
        protected void fill() {
            String n = Escouades.name(target);
            cell("tete", 0, head(target, "§f" + n, List.of()), null);
            text("nom", n, DARK);
            button("exclure", Material.BARRIER, "§cExclure " + n, List.of("Il repart seul."), (p, c) -> {
                String err = r.esc().kick(p, target);
                if (err != null) deny(p, err);
                new Escouade(r, p).open();
            });
            button("chef", Material.GOLDEN_HELMET, "§6Passer chef", List.of(n + " devient le chef de l'escouade."), (p, c) -> {
                String err = r.esc().promote(p, target);
                if (err != null) deny(p, err);
                new Escouade(r, p).open();
            });
            button("retour", Material.ARROW, "§fRetour", List.of(), (p, c) -> new Escouade(r, p).open());
        }
    }

    // ================================================================== réserve (coffre virtuel)

    static final class Reserve extends Menu {
        private final Refuge r;
        private final Profil prof;
        private Inventory inv;
        private boolean held;

        Reserve(Refuge r, Player p) {
            super(p, "reserve");
            this.r = r;
            this.prof = r.profile(p.getUniqueId());
        }

        @Override protected String plainTitle() { return "Réserve"; }

        @Override
        protected boolean canOpen() {
            if (!r.atRefuge(viewer)) {
                deny(viewer, "Ta réserve est au Refuge.");
                return false;
            }
            if (prof.reserveOpen) {
                deny(viewer, "Ta réserve est déjà ouverte.");
                return false;
            }
            return true;
        }

        @Override
        protected List<String> help() {
            return List.of("§fRéserve§7 : 27 cases rien qu'à toi, gardées au Refuge.",
                    "Ce que tu portes part en expédition ; ce qui est ici reste à l'abri.");
        }

        @Override protected void fill() { }

        @Override
        protected void afterBuild(Inventory inv) {
            this.inv = inv;
            for (int i = 0; i < Profil.RESERVE; i++)
                inv.setItem(9 + i, prof.reserve[i] == null ? null : prof.reserve[i].clone());
            prof.reserveOpen = true;
            held = true;
        }

        @Override
        protected void openFailed() {
            prof.reserveOpen = false;
            held = false;
        }

        @Override protected int storageFrom() { return 9; }

        @Override
        protected void storageChanged() {
            Bukkit.getScheduler().runTask(r.plugin(), () -> {
                if (held) sync();
            });
        }

        @Override
        protected boolean customStorageClick(InventoryClickEvent e) {
            if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY || inv == null) return false;
            if (e.getRawSlot() < inv.getSize()) return false;
            ItemStack it = e.getCurrentItem();
            if (it == null || it.isEmpty() || Ui.isUi(it)) return false;
            int left = Ui.store(inv, 9, inv.getSize() - 1, it);
            if (left == it.getAmount()) return true;
            if (left <= 0) e.setCurrentItem(null);
            else it.setAmount(left);
            return true;
        }

        private void sync() {
            for (int i = 0; i < Profil.RESERVE; i++) {
                ItemStack it = inv.getItem(9 + i);
                prof.reserve[i] = it == null || it.isEmpty() || Ui.isUi(it) ? null : it.clone();
            }
            prof.markDirty();
        }

        @Override
        protected void closed(Player p) {
            if (!held) return;
            sync();
            held = false;
            prof.reserveOpen = false;
            prof.save();
        }
    }

    // ================================================================== boutique (L'Armurier)

    static final class Boutique extends Menu {
        private final Refuge r;
        private final boolean sellMode;
        private final int page;

        Boutique(Refuge r, Player p, boolean sellMode, int page) {
            super(p, "boutique");
            this.r = r;
            this.sellMode = sellMode;
            this.page = page;
        }

        @Override protected String plainTitle() { return sellMode ? "Armurier : vendre" : "Armurier : acheter"; }

        @Override protected boolean canOpen() { return r.atRefuge(viewer); }

        @Override
        protected List<String> help() {
            return List.of("§fAcheter§7 : clic sur un article (prix en Ferraille ⚙).",
                    "§fVendre§7 : clic = vendre 1, Maj-clic = tout vendre. Les objets améliorés",
                    "ou gravés ne sont jamais rachetés.");
        }

        @Override
        protected void fill() {
            Profil prof = r.profile(viewer.getUniqueId());
            button("acheter", Material.EMERALD, (sellMode ? "§f" : "§6▶ ") + "Acheter", List.of("Armes, soins, munitions."),
                    (p, c) -> new Boutique(r, p, false, 0).open());
            button("vendre", Material.IRON_NUGGET, (sellMode ? "§6▶ " : "§f") + "Vendre", List.of("Rachat des objets de valeur."),
                    (p, c) -> new Boutique(r, p, true, 0).open());
            overlay(sellMode ? "sel:vendre" : "sel:acheter");
            text("solde", fmt(prof.ferraille()) + " ⚙", GOLD);
            plain(15, Material.IRON_NUGGET, "§6Ta Ferraille : " + fmt(prof.ferraille()) + " ⚙", List.of());
            int per = cells("articles");
            List<ItemStack> its = new ArrayList<>();
            List<Menu.Action> acts = new ArrayList<>();
            fr.eemicraft.refuge.refuge.Boutique shop = r.shop();
            if (!sellMode) {
                for (fr.eemicraft.refuge.refuge.Boutique.Article a : shop.buy) {
                    ItemStack it = fr.eemicraft.refuge.refuge.Boutique.create(a.key(), a.amount());
                    if (it == null) continue;
                    boolean can = prof.ferraille() >= a.price();
                    its.add(Ui.display(it, List.of((can ? "§6" : "§c") + "Prix : " + fmt(a.price()) + " ⚙"
                            + (a.amount() > 1 ? " §8(" + a.amount() + " objets)" : ""), "§eClic : acheter")));
                    acts.add((p, c) -> r.buy(p, a));
                }
            } else {
                Map<String, Integer> have = new LinkedHashMap<>();
                for (ItemStack s : viewer.getInventory().getStorageContents()) {
                    String k = fr.eemicraft.refuge.refuge.Boutique.keyOf(s);
                    if (k != null && shop.sell.containsKey(k)) have.merge(k, s.getAmount(), Integer::sum);
                }
                for (Map.Entry<String, Integer> en : have.entrySet()) {
                    String k = en.getKey();
                    long price = shop.sell.get(k);
                    ItemStack it = fr.eemicraft.refuge.refuge.Boutique.create(k, 1);
                    if (it == null) continue;
                    its.add(Ui.display(it, List.of("§6Rachat : " + fmt(price) + " ⚙ l'unité", "§7Tu en as §f" + en.getValue()
                            + " §8(" + fmt(price * en.getValue()) + " ⚙)", "§eClic : vendre 1 §8· §eMaj-clic : tout vendre")));
                    acts.add((p, c) -> r.sell(p, k, c.isShiftClick()));
                }
                if (have.isEmpty()) plain(31, Material.BARRIER, "§7Rien à vendre dans ton sac", List.of());
            }
            int pages = Math.max(1, (its.size() + per - 1) / per);
            int pg = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < per && pg * per + i < its.size(); i++) cell("articles", i, its.get(pg * per + i), acts.get(pg * per + i));
            button("prec", Material.ARROW, "§fPage précédente", List.of((pg + 1) + "/" + pages), (p, c) -> {
                if (pg > 0) new Boutique(r, p, sellMode, pg - 1).open();
            });
            button("suiv", Material.ARROW, "§fPage suivante", List.of((pg + 1) + "/" + pages), (p, c) -> {
                if (pg < pages - 1) new Boutique(r, p, sellMode, pg + 1).open();
            });
        }

        int page() { return page; }

        boolean sellMode() { return sellMode; }
    }

    // ================================================================== assurance (La Notaire)

    static final class Assurance extends Menu {
        private final Refuge r;

        Assurance(Refuge r, Player p) {
            super(p, "assurance");
            this.r = r;
        }

        @Override protected String plainTitle() { return "Assurance"; }

        @Override protected boolean canOpen() { return r.atRefuge(viewer); }

        @Override
        protected List<String> help() {
            return List.of("§fAssurance§7 : jusqu'à 3 objets, payés en Ferraille, rendus au Refuge si tu meurs.",
                    "Clic sur un objet de ton sac pour l'assurer (prix selon la rareté).",
                    "Il doit être encore sur toi au départ, sinon tu es remboursé.",
                    "L'assurance vaut pour UNE expédition. Clic sur un objet assuré : annuler (remboursé).");
        }

        @Override
        protected void fill() {
            Profil prof = r.profile(viewer.getUniqueId());
            List<ItemStack> ins = prof.insuredRaw();
            for (int i = 0; i < cells("assures"); i++) {
                if (i < ins.size()) {
                    final int idx = i;
                    long price = prof.insuredPrices.get(i);
                    cell("assures", i, Ui.display(ins.get(i), List.of("§6Payé : " + fmt(price) + " ⚙", "§eClic : annuler (remboursé)")),
                            (p, c) -> {
                                long back = prof.removeInsured(idx);
                                prof.addFerraille(back);
                                prof.save();
                                Txt.msg(p, "Assurance annulée, " + fmt(back) + " ⚙ remboursés.");
                                new Assurance(r, p).open();
                            });
                    text("prix" + i, fmt(price) + " ⚙", GOLD);
                } else text("prix" + i, "libre", GREY);
            }
            text("solde", "Ferraille : " + fmt(prof.ferraille()) + " ⚙", GOLD);
            plain(40, Material.IRON_NUGGET, "§6Ta Ferraille : " + fmt(prof.ferraille()) + " ⚙",
                    List.of("Clic sur un objet de ton sac pour l'assurer."));
        }

        @Override
        protected void clickBottom(Player p, int slot, ItemStack item, ClickType click) {
            Profil prof = r.profile(p.getUniqueId());
            if (prof.insuredRaw().size() >= cells("assures")) {
                deny(p, "Tu as déjà 3 objets assurés.");
                return;
            }
            long price = r.shop().insurancePrice(item);
            if (prof.ferraille() < price) {
                deny(p, "Il te faut " + fmt(price) + " ⚙ pour assurer cet objet.");
                return;
            }
            prof.addFerraille(-price);
            prof.addInsured(item.clone(), price);
            prof.save();
            Ui.success(p);
            Txt.msg(p, "Objet assuré pour " + fmt(price) + " ⚙ (prochaine expédition).");
            new Assurance(r, p).open();
        }
    }

    // ================================================================== bilan

    static final class BilanMenu extends Menu {
        private final Refuge r;
        private final Bilan b;

        BilanMenu(Refuge r, Player p, Bilan b) {
            super(p, "bilan");
            this.r = r;
            this.b = b;
        }

        @Override protected String plainTitle() { return "Bilan : " + b.zoneName(); }

        @Override
        protected List<String> help() {
            List<String> l = new ArrayList<>();
            l.add("§fBilan§7 de l'expédition à §f" + b.zoneName() + "§7.");
            for (String n : b.notes()) l.add(n);
            return l;
        }

        static String time(int s) {
            return String.format("%d:%02d", s / 60, s % 60);
        }

        @Override
        protected void fill() {
            overlay(b.extracted() ? "icon:issue:extrait" : "icon:issue:mort");
            text("issue", b.extracted() ? "Extrait" : "Mort", b.extracted() ? GREEN : RED);
            text("temps", time(b.durationSeconds()), DARK);
            text("zombies", Integer.toString(b.kills()), DARK);
            text("releves", Integer.toString(b.revives()), DARK);
            text("ferraille", "+ " + fmt(b.ferraille()) + " ⚙", GOLD);
            text("xp", "+ " + fmt(b.xp()) + " XP", GREEN);
            plain(9, b.extracted() ? Material.LIME_BANNER : Material.SKELETON_SKULL, b.extracted() ? "§aExtrait" : "§cMort",
                    List.of(b.zoneName()));
            plain(10, Material.CLOCK, "§fTemps : " + time(b.durationSeconds()), List.of());
            plain(11, Material.ROTTEN_FLESH, "§fZombies : " + b.kills(), List.of());
            plain(12, Material.TOTEM_OF_UNDYING, "§fRelèves : " + b.revives(), List.of());
            plain(27, Material.IRON_NUGGET, "§6+ " + fmt(b.ferraille()) + " ⚙", List.of());
            plain(28, Material.EXPERIENCE_BOTTLE, "§a+ " + b.xp() + " XP", List.of());
            List<ItemStack> loot = b.loot() == null ? List.of() : b.loot();
            int n = cells("butin");
            for (int i = 0; i < Math.min(n, loot.size()); i++) {
                ItemStack it = loot.get(i);
                if (it == null || it.isEmpty()) continue;
                if (i == n - 1 && loot.size() > n)
                    cell("butin", i, info(Material.BUNDLE, "§f+ " + (loot.size() - n + 1) + " autres objets", List.of()), null);
                else cell("butin", i, Ui.display(it, List.of("§8Ramené de l'expédition")), null);
            }
        }
    }

    // ================================================================== profil

    static final class ProfilMenu extends Menu {
        private final Refuge r;

        ProfilMenu(Refuge r, Player p) {
            super(p, "profil");
            this.r = r;
        }

        @Override protected String plainTitle() { return "Profil de " + viewer.getName(); }

        @Override
        protected List<String> help() {
            return List.of("§fProfil§7 : ta Ferraille ⚙, ton acte et ton niveau de survivant.",
                    "Le niveau n'est qu'un repère : la puissance vient de l'équipement.");
        }

        @Override
        protected void fill() {
            Profil prof = r.profile(viewer.getUniqueId());
            boolean here = r.atRefuge(viewer);
            int lvl = prof.level();
            String l1 = fmt(prof.ferraille()) + " ⚙ · acte " + prof.act() + " · niveau " + lvl;
            String l2 = "XP : " + fmt(prof.xp()) + " / " + fmt(Profil.needFor(lvl + 1));
            text("ligne1", l1, DARK);
            text("ligne2", l2, GREEN);
            plain(31, Material.PLAYER_HEAD, "§f" + viewer.getName(), List.of(l1, l2));
            button("expedition", Material.MAP, "§fDépart", List.of("Préparer une expédition (Le Passeur)."), (p, c) -> {
                if (r.atRefuge(p)) new Expedition(r, p).open();
                else deny(p, "Va d'abord au Refuge : /refuge");
            });
            button("escouade", Material.PLAYER_HEAD, "§fEscouade", List.of("Former ou rejoindre une escouade."), (p, c) -> {
                if (r.atRefuge(p)) new Escouade(r, p).open();
                else deny(p, "Va d'abord au Refuge : /refuge");
            });
            button("reserve", Material.CHEST, "§fRéserve", List.of(here ? "Ouvrir ta réserve." : "§8Seulement au Refuge."),
                    (p, c) -> new Reserve(r, p).open());
            if (!here) overlay("off:reserve");
            Bilan last = r.lastBilan(viewer.getUniqueId());
            button("bilan", Material.WRITTEN_BOOK, "§fDernier bilan", List.of(last == null ? "§8Aucune expédition pour l'instant."
                    : "Revoir le bilan de " + last.zoneName() + "."), (p, c) -> {
                        if (last != null) new BilanMenu(r, p, last).open();
                    });
            if (last == null) overlay("off:bilan");
        }
    }
}
