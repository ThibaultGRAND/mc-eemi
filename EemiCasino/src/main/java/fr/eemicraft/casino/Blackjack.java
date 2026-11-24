package fr.eemicraft.casino;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Blackjack en solo contre un croupier automatique, regles du Casino de Monte-Carlo (regles-de-jeu-black-jack-FR) :
 * - 2 a 9 = valeur faciale, 10 / valet / dame / roi = 10, as = 1 ou 11 ;
 * - le croupier recoit UNE seule carte au depart (pas de carte cachee), tire a 16 ou moins, reste a 17 ou plus ;
 * - blackjack (as + 10 ou figure en 2 cartes) paye 3 pour 2, egalite contre un blackjack du croupier ;
 * - blackjack du croupier : tous les joueurs perdent leur mise (mises doublees et separees comprises), sauf blackjack = nul ;
 * - doubler sur les 2 premieres cartes : une seule carte de plus ;
 * - separer deux cartes de meme valeur, jusqu'a 3 jeux ; as separes : une carte chacun, sans nouvelle separation,
 *   et as + 10 apres separation = 21, pas blackjack ;
 * - assurance si la premiere carte du croupier est un as : la moitie de la mise, payee 2 pour 1 si le croupier fait blackjack.
 * Sabot de 6 jeux rebattu a chaque main.
 */
final class Blackjack {
    static final int BETTING = 0;
    static final int PLAYING = 1;
    static final int FINISHED = 2;
    static final int MAX_HANDS = 3;
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    /** Ligne de chaque main : case du titre, les cartes suivent 2 cases plus loin. */
    static final int[] HAND_ROWS = {18, 27, 36};
    static final int DELAY = 8;

    final class Session {
        final UUID id;
        int table = -1;
        Gui gui;
        Deque<Integer> shoe;
        final List<Integer> dealer = new ArrayList<>();
        final List<List<Integer>> hands = new ArrayList<>();
        final List<Long> handBet = new ArrayList<>();
        final List<String> handResult = new ArrayList<>();
        /** Main issue d'une separation d'as : une seule carte, plus rien a jouer. */
        final List<Boolean> aceSplit = new ArrayList<>();
        int cur;
        int state = BETTING;
        boolean busy;
        boolean draining;
        /** Le joueur a agi sur sa main (l'assurance n'est plus proposee). */
        boolean acted;
        long insurance;
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        BukkitTask ticker;
        String message = "&7Choisis ta mise puis clique sur &aDistribuer&7.";

        Session(UUID id) {
            this.id = id;
        }

        long totalBet() {
            long t = insurance;
            for (long b : handBet) {
                t += b;
            }
            return t;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();

    Blackjack(EemiCasino pl) {
        this.pl = pl;
    }

    static int value(int card) {
        int r = Cards.rank(card);
        return r <= 8 ? r + 2 : r == 12 ? 11 : 10;
    }

    static int total(List<Integer> h) {
        int t = 0;
        int aces = 0;
        for (int c : h) {
            t += value(c);
            if (Cards.rank(c) == 12) {
                aces++;
            }
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return t;
    }

    static boolean soft(List<Integer> h) {
        int t = 0;
        int aces = 0;
        for (int c : h) {
            t += value(c);
            if (Cards.rank(c) == 12) {
                aces++;
            }
        }
        while (t > 21 && aces > 0) {
            t -= 10;
            aces--;
        }
        return aces > 0;
    }

    static boolean twoCard21(List<Integer> h) {
        return h.size() == 2 && total(h) == 21;
    }

    /** Vrai blackjack du joueur : 21 en 2 cartes et pas sur une main separee. */
    private static boolean natural(Session s, int i) {
        return s.hands.size() == 1 && twoCard21(s.hands.get(i));
    }

    /** Carte de blackjack : la quantite affichee est la valeur en points (figures = 10, as = 1). */
    static org.bukkit.inventory.ItemStack card(int c) {
        int v = value(c);
        String pts = Cards.rank(c) == 12 ? "&7Vaut 1 ou 11 points" : "&7Vaut " + v + " points";
        return Cards.item(c, List.of(pts), Cards.rank(c) == 12 ? 1 : v);
    }

    private int draw(Session s) {
        if (s.shoe == null || s.shoe.size() < 20) {
            s.shoe = Cards.shoe(6, rnd);
        }
        return s.shoe.poll();
    }

    void open(Player p, int table) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        if (s.state != PLAYING) {
            s.table = table;
        }
        s.gui = new Gui("blackjack", 54, "&8Blackjack" + (s.table >= 0 ? " - table " + (s.table + 1) : ""));
        render(p, s);
        p.openInventory(s.gui.inv);
        if (s.table >= 0 && s.state != PLAYING) {
            pl.dealerLook(s.table, p);
            pl.dealerSay(s.table, "&eBienvenue " + p.getName() + " ! Faites vos jeux.");
        }
    }

    private boolean insuranceOffer(Player p, Session s) {
        return s.state == PLAYING && !s.busy && !s.acted && s.insurance == 0 && s.dealer.size() == 1
                && Cards.rank(s.dealer.get(0)) == 12 && s.hands.size() == 1 && !natural(s, 0)
                && pl.data(p).chips >= s.handBet.get(0) / 2 && s.handBet.get(0) / 2 > 0;
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        // croupier
        List<String> dl = new ArrayList<>();
        dl.add(s.dealer.isEmpty() ? "&7En attente" : "&7Total : &f" + total(s.dealer));
        dl.add("&8Tire a 16 ou moins, reste a 17 ou plus");
        if (s.insurance > 0) {
            dl.add("&bAssurance : " + U.fmt(s.insurance));
        }
        g.set(0, U.item(Material.SKELETON_SKULL, "&6Croupier", dl, false), null);
        for (int i = 0; i < s.dealer.size() && i < 7; i++) {
            g.set(2 + i, card(s.dealer.get(i)), null);
        }
        g.set(13, U.item(Material.OAK_SIGN, s.message, List.of(), false), null);
        // mains du joueur (3 au plus)
        for (int h = 0; h < MAX_HANDS; h++) {
            int labelSlot = HAND_ROWS[h];
            if (h >= s.hands.size()) {
                if (h == 0) {
                    g.set(labelSlot, U.item(Material.PLAYER_HEAD, "&eTa main", List.of("&7Pas encore de cartes"), false), null);
                }
                continue;
            }
            List<Integer> hand = s.hands.get(h);
            boolean current = s.state == PLAYING && s.cur == h;
            List<String> lore = new ArrayList<>();
            lore.add("&7Total : &f" + total(hand) + (soft(hand) && total(hand) < 21 ? " &8(souple)" : ""));
            lore.add("&7Mise : &e" + U.fmt(s.handBet.get(h)));
            if (h < s.handResult.size() && s.handResult.get(h) != null) {
                lore.add(s.handResult.get(h));
            }
            g.set(labelSlot, U.head(p.getUniqueId(), (current ? "&a▶ " : "&e") + (s.hands.size() > 1 ? "Main " + (h + 1) : "Ta main"), lore, current), null);
            for (int i = 0; i < hand.size() && i < 7; i++) {
                g.set(labelSlot + 2 + i, card(hand.get(i)), null);
            }
        }
        PData d = pl.data(p);
        g.set(45, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of(), false), null);
        g.set(46, U.item(Material.BOOK, "&eRegles", List.of(
                "&72 a 9 : valeur faciale", "&710, valet, dame, roi : 10 points", "&7As : 1 ou 11 points",
                "&7Blackjack (as + 10/figure) : paye 3 pour 2", "&7Le croupier n'a qu'une carte au depart,",
                "&7tire a 16 ou moins, reste a 17 ou plus.", "&7Blackjack du croupier : toutes les mises perdues",
                "&7(sauf blackjack = coup nul).", "&7Doubler : une seule carte de plus.",
                "&7Separer : jusqu'a 3 jeux (as : une carte chacun).", "&7Assurance (as du croupier) : 1/2 mise, paye 2 pour 1."), false), null);
        if (s.state == PLAYING) {
            boolean act = !s.busy;
            List<Integer> hand = s.hands.get(s.cur);
            long hb = s.handBet.get(s.cur);
            g.set(47, Icons.icon(U.item(act ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, "&a&lTIRER", List.of("&7Une carte de plus"), false), act ? "bj_tirer" : "desactive"), act ? t -> hit(p, s) : null);
            g.set(48, Icons.icon(U.item(act ? Material.RED_CONCRETE : Material.GRAY_CONCRETE, "&c&lRESTER", List.of("&7Garder cette main"), false), act ? "bj_rester" : "desactive"), act ? t -> stand(p, s) : null);
            boolean canDouble = act && hand.size() == 2 && d.chips >= hb;
            g.set(50, Icons.icon(U.item(canDouble ? Material.GOLD_BLOCK : Material.GRAY_CONCRETE, "&6&lDOUBLER", List.of("&7Double la mise (" + U.fmt(hb) + ")", "&7et prend une seule carte"), false),
                    canDouble ? "bj_doubler" : "desactive"), canDouble ? t -> doubleDown(p, s) : null);
            boolean canSplit = act && s.hands.size() < MAX_HANDS && hand.size() == 2 && value(hand.get(0)) == value(hand.get(1))
                    && !s.aceSplit.get(s.cur) && d.chips >= hb;
            g.set(51, U.item(canSplit ? Material.SHEARS : Material.GRAY_CONCRETE, "&b&lSEPARER", List.of("&7Deux jeux avec la meme mise", "&7(cartes de meme valeur, 3 jeux max)"), false), canSplit ? t -> split(p, s) : null);
            if (insuranceOffer(p, s)) {
                long ins = s.handBet.get(0) / 2;
                g.set(52, U.item(Material.SHIELD, "&b&lASSURANCE &7(" + U.fmt(ins) + ")", List.of("&7Le croupier montre un as.", "&7Paye 2 pour 1 s'il fait blackjack,", "&7perdue sinon."), true), t -> insure(p, s));
            }
        } else {
            long bet = pl.currentBet(p, "blackjack");
            pl.betBar(g, p, "blackjack", BET_SLOTS, () -> render(p, s));
            boolean ok = d.chips >= bet && !s.busy;
            g.set(53, U.item(ok ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, s.state == FINISHED ? "&a&lREJOUER" : "&a&lDISTRIBUER",
                    List.of("&7Mise : &e" + U.fmt(bet) + " jetons", ok ? "" : "&cPas assez de jetons"), ok), ok ? t -> deal(p, s) : null);
        }
    }

    private boolean viewing(Player p, Session s) {
        return p.isOnline() && s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui;
    }

    private void refresh(Player p, Session s) {
        pl.tableCards.sync(p, s);
        if (viewing(p, s)) {
            render(p, s);
        }
    }

    private void cardSound(Player p) {
        if (p.isOnline()) {
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        }
    }

    void deal(Player p, Session s) {
        if (s.busy || s.state == PLAYING) {
            return;
        }
        long bet = pl.currentBet(p, "blackjack");
        if (!pl.take(p, bet)) {
            U.msg(p, "&cIl te faut " + U.fmt(bet) + " jetons. Passe a la caisse !");
            return;
        }
        // sabot rebattu a chaque main : le comptage de cartes ne sert a rien
        s.shoe = Cards.shoe(6, rnd);
        s.dealer.clear();
        s.hands.clear();
        s.handBet.clear();
        s.handResult.clear();
        s.aceSplit.clear();
        s.hands.add(new ArrayList<>());
        s.handBet.add(bet);
        s.handResult.add(null);
        s.aceSplit.add(false);
        s.cur = 0;
        s.insurance = 0;
        s.acted = false;
        s.state = PLAYING;
        s.busy = true;
        s.message = "&7Distribution...";
        pl.dealerSay(s.table, "&7Mise de &e" + U.fmt(bet) + "&7, je distribue.");
        // deux cartes au joueur, une seule a la banque
        List<Runnable> steps = new ArrayList<>();
        steps.add(() -> s.hands.get(0).add(draw(s)));
        steps.add(() -> s.dealer.add(draw(s)));
        steps.add(() -> s.hands.get(0).add(draw(s)));
        sequence(p, s, steps, () -> afterDeal(p, s));
    }

    /** File d'etapes jouees toutes les DELAY ticks (ou d'un coup quand le joueur part). */
    private void enqueue(Session s, Runnable r) {
        s.queue.add(r);
        if (s.ticker == null && !s.draining) {
            s.ticker = new BukkitRunnable() {
                @Override
                public void run() {
                    Runnable next = s.queue.poll();
                    if (next != null) {
                        next.run();
                    }
                    if (s.queue.isEmpty()) {
                        cancel();
                        s.ticker = null;
                    }
                }
            }.runTaskTimer(pl, DELAY, DELAY);
        }
    }

    /** Joue les etapes une par une avec l'animation du croupier. */
    private void sequence(Player p, Session s, List<Runnable> steps, Runnable then) {
        s.busy = true;
        refresh(p, s);
        for (Runnable step : steps) {
            enqueue(s, () -> {
                step.run();
                pl.dealerSwing(s.table, p);
                cardSound(p);
                refresh(p, s);
            });
        }
        enqueue(s, () -> {
            s.busy = false;
            then.run();
            refresh(p, s);
        });
    }

    private void afterDeal(Player p, Session s) {
        if (natural(s, 0)) {
            s.message = "&6&lBLACKJACK ! &7Le croupier tire sa carte...";
            pl.dealerSay(s.table, "&6Blackjack ! &7Voyons ma carte...");
            dealerTurn(p, s);
            return;
        }
        if (Cards.rank(s.dealer.get(0)) == 12) {
            s.message = "&eLe croupier montre un as : assurance ?";
            pl.dealerSay(s.table, "&bUn as... Assurance ?");
        } else {
            s.message = "&eA toi de jouer : tirer ou rester ?";
        }
    }

    void insure(Player p, Session s) {
        if (!insuranceOffer(p, s)) {
            return;
        }
        long ins = s.handBet.get(0) / 2;
        if (!pl.take(p, ins)) {
            return;
        }
        s.insurance = ins;
        s.message = "&bAssure (" + U.fmt(ins) + "). &eTirer ou rester ?";
        refresh(p, s);
    }

    void hit(Player p, Session s) {
        if (s.busy || s.state != PLAYING) {
            return;
        }
        s.acted = true;
        sequence(p, s, List.of(() -> s.hands.get(s.cur).add(draw(s))), () -> {
            int t = total(s.hands.get(s.cur));
            if (t > 21) {
                s.handResult.set(s.cur, "&cSaute (" + t + ")");
                pl.dealerSay(s.table, "&7" + t + ", ca saute...");
                nextHand(p, s);
            } else if (t == 21) {
                nextHand(p, s);
            } else {
                s.message = "&eTotal " + t + " : tirer ou rester ?";
            }
        });
    }

    void stand(Player p, Session s) {
        if (s.busy || s.state != PLAYING) {
            return;
        }
        s.acted = true;
        nextHand(p, s);
        refresh(p, s);
    }

    void doubleDown(Player p, Session s) {
        if (s.busy || s.state != PLAYING || s.hands.get(s.cur).size() != 2) {
            return;
        }
        long hb = s.handBet.get(s.cur);
        if (!pl.take(p, hb)) {
            U.msg(p, "&cPas assez de jetons pour doubler.");
            return;
        }
        s.acted = true;
        s.handBet.set(s.cur, hb * 2);
        pl.dealerSay(s.table, "&6Mise doublee !");
        sequence(p, s, List.of(() -> s.hands.get(s.cur).add(draw(s))), () -> {
            int t = total(s.hands.get(s.cur));
            if (t > 21) {
                s.handResult.set(s.cur, "&cSaute (" + t + ")");
            }
            nextHand(p, s);
        });
    }

    void split(Player p, Session s) {
        if (s.busy || s.state != PLAYING || s.hands.size() >= MAX_HANDS) {
            return;
        }
        int i = s.cur;
        List<Integer> h = s.hands.get(i);
        if (h.size() != 2 || value(h.get(0)) != value(h.get(1)) || s.aceSplit.get(i)) {
            return;
        }
        long hb = s.handBet.get(i);
        if (!pl.take(p, hb)) {
            U.msg(p, "&cPas assez de jetons pour separer.");
            return;
        }
        s.acted = true;
        boolean aces = Cards.rank(h.get(0)) == 12;
        List<Integer> h2 = new ArrayList<>();
        h2.add(h.remove(1));
        s.hands.add(i + 1, h2);
        s.handBet.add(i + 1, hb);
        s.handResult.add(i + 1, null);
        s.aceSplit.add(i + 1, aces);
        s.aceSplit.set(i, aces);
        pl.dealerSay(s.table, "&b" + s.hands.size() + " jeux !");
        sequence(p, s, List.of(() -> s.hands.get(i).add(draw(s)), () -> s.hands.get(i + 1).add(draw(s))), () -> {
            if (aces) {
                // as separes : une seule carte chacun, les deux jeux sont termines
                s.cur = i + 1;
                nextHand(p, s);
            } else if (total(s.hands.get(i)) == 21) {
                nextHand(p, s);
            } else {
                s.message = "&eJeu " + (i + 1) + " : tirer ou rester ?";
            }
        });
    }

    private void nextHand(Player p, Session s) {
        while (s.cur + 1 < s.hands.size()) {
            s.cur++;
            if (s.aceSplit.get(s.cur) || total(s.hands.get(s.cur)) == 21) {
                continue;
            }
            s.message = "&eJeu " + (s.cur + 1) + " : tirer ou rester ?";
            refresh(p, s);
            return;
        }
        dealerTurn(p, s);
    }

    private void dealerTurn(Player p, Session s) {
        boolean live = false;
        boolean onlyNatural = true;
        for (int i = 0; i < s.hands.size(); i++) {
            if (total(s.hands.get(i)) <= 21) {
                live = true;
                if (!natural(s, i)) {
                    onlyNatural = false;
                }
            }
        }
        s.message = "&7Le croupier joue...";
        s.busy = true;
        refresh(p, s);
        if (!live || onlyNatural) {
            // il ne reste a savoir que si la banque fait blackjack (assurance, blackjack du joueur)
            if (s.insurance > 0 || live) {
                enqueue(s, () -> {
                    s.dealer.add(draw(s));
                    pl.dealerSwing(s.table, p);
                    cardSound(p);
                    refresh(p, s);
                    s.busy = false;
                    settle(p, s);
                });
            } else {
                s.busy = false;
                settle(p, s);
            }
            return;
        }
        dealerStep(p, s);
    }

    private void dealerStep(Player p, Session s) {
        // tire a 16 ou moins, reste a 17 ou plus (17 souple compris)
        if (total(s.dealer) >= 17) {
            s.busy = false;
            settle(p, s);
            return;
        }
        enqueue(s, () -> {
            s.dealer.add(draw(s));
            pl.dealerSwing(s.table, p);
            cardSound(p);
            refresh(p, s);
            dealerStep(p, s);
        });
    }

    /** Fin de partie : paye chaque main et l'assurance. */
    private void settle(Player p, Session s) {
        s.busy = false;
        int dt = total(s.dealer);
        boolean dealerBj = twoCard21(s.dealer);
        long gross = 0;
        long stake = s.totalBet();
        for (int i = 0; i < s.hands.size(); i++) {
            List<Integer> h = s.hands.get(i);
            long b = s.handBet.get(i);
            int t = total(h);
            boolean nat = natural(s, i);
            String r;
            long pay;
            if (t > 21) {
                pay = 0;
                r = "&cSaute (" + t + ")";
            } else if (dealerBj) {
                pay = nat ? b : 0;
                r = nat ? "&eCoup nul : blackjack contre blackjack" : "&cBlackjack du croupier";
            } else if (nat) {
                pay = b + b * 3 / 2;
                r = "&6&lBlackjack ! +" + U.fmt(pay);
            } else if (dt > 21) {
                pay = b * 2;
                r = "&aGagne ! Le croupier saute";
            } else if (t > dt) {
                pay = b * 2;
                r = "&aGagne ! " + t + " contre " + dt;
            } else if (t == dt) {
                pay = b;
                r = "&eCoup nul (" + t + ")";
            } else {
                pay = 0;
                r = "&cPerdu : " + t + " contre " + dt;
            }
            s.handResult.set(i, r);
            gross += pay;
        }
        if (s.insurance > 0 && dealerBj) {
            gross += s.insurance * 3;
        }
        s.state = FINISHED;
        long net = gross - stake;
        if (gross > 0) {
            pl.win(p, gross, stake, "blackjack");
        }
        String ins = s.insurance > 0 ? (dealerBj ? " &b(assurance payee)" : " &8(assurance perdue)") : "";
        if (dealerBj) {
            s.message = "&cBlackjack du croupier" + (net >= 0 ? " &e(" + (net > 0 ? "+" : "") + U.fmt(net) + ")" : " &c(" + U.fmt(net) + ")") + ins;
        } else if (net > 0) {
            s.message = "&a&lGAGNE ! &a+" + U.fmt(net) + " jetons" + ins;
        } else if (net == 0) {
            s.message = "&eCoup nul, mise rendue." + ins;
        } else {
            s.message = "&cPerdu (" + U.fmt(net) + " jetons)" + ins;
        }
        if (p.isOnline()) {
            if (net > 0) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                pl.dealerSay(s.table, dt > 21 ? "&7Je saute ! &aBien joue." : "&aFelicitations " + p.getName() + " !");
                if (net >= stake * 2 && net >= 1000) {
                    pl.celebrate(p, false);
                }
            } else if (net < 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
                pl.dealerSay(s.table, dealerBj ? "&cBlackjack de la banque !" : "&7La banque gagne. Une autre ?");
            } else {
                pl.dealerSay(s.table, "&eCoup nul.");
            }
            p.sendActionBar(U.c(s.message));
        }
        refresh(p, s);
    }

    /** Le joueur part en pleine main : il reste sur toutes ses mains, la partie est reglee tout de suite. */
    private void finishNow(Player p, Session s) {
        if (s.ticker != null) {
            s.ticker.cancel();
            s.ticker = null;
        }
        s.draining = true;
        for (int guard = 0; guard < 100 && s.state == PLAYING; guard++) {
            Runnable next = s.queue.poll();
            if (next != null) {
                next.run();
            } else if (!s.busy) {
                s.acted = true;
                nextHand(p, s);
            } else {
                s.busy = false;
            }
        }
        s.queue.clear();
        s.draining = false;
    }

    void quit(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.state == PLAYING) {
            finishNow(p, s);
        }
        if (s != null) {
            pl.tableCards.release(s.table, p.getUniqueId());
        }
    }

    void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (s.state == PLAYING) {
                Player p = Bukkit.getPlayer(s.id);
                if (p != null) {
                    finishNow(p, s);
                } else {
                    pl.refund(s.id, s.totalBet());
                }
            }
        }
        sessions.clear();
    }

    boolean playing(UUID id) {
        Session s = sessions.get(id);
        return s != null && s.state == PLAYING;
    }
}
