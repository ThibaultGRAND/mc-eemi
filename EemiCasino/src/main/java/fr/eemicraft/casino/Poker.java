package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Texas Hold'em multijoueur, 2 a 6 joueurs par table, sans commission.
 * Le tapis (jetons poses a la table) est retire du solde a l'arrivee et rendu en se levant.
 */
final class Poker {
    static final int WAIT = 0;
    static final int COUNTDOWN = 1;
    static final int BETTING = 2;
    static final int PAUSE = 3;
    static final int[] SEAT_SLOTS = {1, 2, 3, 5, 6, 7};
    static final int[] BET_ROW = {10, 11, 12, 14, 15, 16};
    static final String[] STREETS = {"Pre-flop", "Flop", "Turn", "River"};

    final class Seat {
        UUID id;
        String name;
        long stack;
        long bet;
        long total;
        final List<Integer> hole = new ArrayList<>();
        boolean inHand;
        boolean folded;
        boolean allIn;
        boolean acted;
        boolean leave;
        boolean gone;
        boolean show;
        int timeouts;
        String last = "";
        Gui gui;

        Player player() {
            return id == null ? null : Bukkit.getPlayer(id);
        }
    }

    final class Table {
        final int id;
        final String name;
        final long sb;
        final long bb;
        final long minBuy;
        final long maxBuy;
        final Seat[] seats = new Seat[6];
        int state = WAIT;
        int street;
        int dealer = -1;
        int actor = -1;
        long currentBet;
        long minRaise;
        final List<Integer> board = new ArrayList<>();
        Deque<Integer> deck;
        long deadline;
        int countdown;
        int hand;

        Table(int id, String name, long sb, long bb, long minBuy, long maxBuy) {
            this.id = id;
            this.name = name;
            this.sb = sb;
            this.bb = bb;
            this.minBuy = minBuy;
            this.maxBuy = maxBuy;
        }

        long pot() {
            long t = 0;
            for (Seat s : seats) {
                if (s != null) {
                    t += s.total;
                }
            }
            return t;
        }

        int seatOf(UUID id) {
            for (int i = 0; i < 6; i++) {
                if (seats[i] != null && !seats[i].gone && id.equals(seats[i].id)) {
                    return i;
                }
            }
            return -1;
        }

        int players() {
            int n = 0;
            for (Seat s : seats) {
                if (s != null && !s.gone) {
                    n++;
                }
            }
            return n;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    final List<Table> tables = new ArrayList<>();

    Poker(EemiCasino pl) {
        this.pl = pl;
        List<Map<?, ?>> list = pl.getConfig().getMapList("poker.tables");
        int i = 0;
        for (Map<?, ?> m : list) {
            tables.add(new Table(i++, String.valueOf(m.get("name")), num(m.get("small"), 10), num(m.get("big"), 20), num(m.get("min-buy"), 400), num(m.get("max-buy"), 2000)));
        }
        if (tables.isEmpty()) {
            tables.add(new Table(0, "Table classique", 10, 20, 400, 2000));
        }
        Bukkit.getPluginManager().registerEvents(new RaiseInput(), pl);
    }

    // ---------------- relance au choix (montant tape dans le chat) ----------------

    /** Saisie en attente : table, main, street et echeance du tour au moment du clic (refusee si l'un a change). */
    private record Pending(int table, int hand, int street, long deadline) {
    }

    /** Joueurs qui doivent taper le montant de leur relance. */
    private final Map<UUID, Pending> askingRaise = new ConcurrentHashMap<>();

    void askRaise(Player p, Table t) {
        askingRaise.put(p.getUniqueId(), new Pending(t.id, t.hand, t.street, t.deadline));
        p.closeInventory();
        Seat me = t.seats[t.seatOf(p.getUniqueId())];
        long min = t.currentBet + t.minRaise;
        long max = me.bet + me.stack;
        U.msg(p, "&dTape dans le chat le montant de ta relance &7(total de ta mise, entre &e" + U.fmt(Math.min(min, max))
                + " &7et &e" + U.fmt(max) + "&7, ex. 350, 2k, tapis) ou &cannuler&7.");
    }

    private static long parseAmount(String s, long max) {
        String x = s.trim().toLowerCase().replace(" ", "").replace(" ", "").replace(",", ".");
        if (x.equals("tapis") || x.equals("allin") || x.equals("max")) {
            return max;
        }
        double mult = 1;
        if (x.endsWith("k")) {
            mult = 1_000;
            x = x.substring(0, x.length() - 1);
        } else if (x.endsWith("m")) {
            mult = 1_000_000;
            x = x.substring(0, x.length() - 1);
        }
        try {
            double v = Double.parseDouble(x) * mult;
            return v <= 0 || Double.isNaN(v) ? -1 : (long) Math.floor(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void handleRaise(Player p, Pending pend, String text) {
        if (pend.table() < 0 || pend.table() >= tables.size()) {
            return;
        }
        Table t = tables.get(pend.table());
        int i = t.seatOf(p.getUniqueId());
        if (i < 0) {
            return;
        }
        Seat me = t.seats[i];
        // le tour doit etre exactement celui du clic (meme main, meme street, meme echeance)
        if (t.state != BETTING || t.actor != i || t.hand != pend.hand() || t.street != pend.street() || t.deadline != pend.deadline()) {
            U.msg(p, "&cCe n'est plus ton tour.");
            return;
        }
        if (text.trim().equalsIgnoreCase("annuler")) {
            openGui(p, t, me);
            return;
        }
        long max = me.bet + me.stack;
        long min = t.currentBet + t.minRaise;
        long to = parseAmount(text, max);
        if (to < 0) {
            U.msg(p, "&cMontant illisible. &7Reclique sur &dRelance au choix &7pour reessayer.");
            openGui(p, t, me);
            return;
        }
        if (to >= max) {
            act(p, t, Act.ALLIN, 0);
        } else if (to < min) {
            U.msg(p, "&cLa relance doit etre d'au moins &e" + U.fmt(min) + "&c (ou tapis).");
            openGui(p, t, me);
            return;
        } else {
            act(p, t, Act.RAISE, to);
        }
        if (t.seatOf(p.getUniqueId()) == i) {
            openGui(p, t, me);
        }
    }

    private final class RaiseInput implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncChatEvent e) {
            Pending pend = askingRaise.remove(e.getPlayer().getUniqueId());
            if (pend == null || System.currentTimeMillis() > pend.deadline() + 500) {
                // pas de saisie, ou tour deja expire : le message part normalement dans le chat
                return;
            }
            e.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText().serialize(e.message());
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(pl, () -> handleRaise(p, pend, text));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            askingRaise.remove(e.getPlayer().getUniqueId());
        }
    }

    private static long num(Object o, long def) {
        return o instanceof Number n ? n.longValue() : def;
    }

    Table tableOf(UUID id) {
        for (Table t : tables) {
            if (t.seatOf(id) >= 0) {
                return t;
            }
        }
        return null;
    }

    // ---------------- arrivee / depart ----------------

    void click(Player p, int tableIndex) {
        if (tableIndex < 0 || tableIndex >= tables.size()) {
            return;
        }
        Table t = tables.get(tableIndex);
        int i = t.seatOf(p.getUniqueId());
        if (i >= 0) {
            openGui(p, t, t.seats[i]);
            return;
        }
        Table other = tableOf(p.getUniqueId());
        if (other != null) {
            U.msg(p, "&cTu es deja assis a la " + other.name + ".");
            return;
        }
        openBuyIn(p, t);
    }

    void openBuyIn(Player p, Table t) {
        Gui g = new Gui("poker-buyin", 27, "&8" + t.name + " - cave");
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        PData d = pl.data(p);
        g.set(4, U.item(Material.PAPER, "&b&l" + t.name, List.of("&7Blindes : &f" + U.fmt(t.sb) + "/" + U.fmt(t.bb),
                "&7Joueurs : &f" + t.players() + "/6", "&7Cave : &f" + U.fmt(t.minBuy) + " a " + U.fmt(t.maxBuy),
                "", "&7Tes jetons : &e" + U.fmt(d.chips)), false), null);
        long[] opts = {t.minBuy, (t.minBuy + t.maxBuy) / 2, t.maxBuy};
        String[] names = {"&eCave minimum", "&6Cave moyenne", "&c&lCave maximum"};
        Material[] mats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.GOLD_INGOT};
        int[] slots = {11, 13, 15};
        for (int k = 0; k < 3; k++) {
            long amount = opts[k];
            boolean ok = d.chips >= amount && t.players() < 6;
            g.set(slots[k], U.item(ok ? mats[k] : Material.GRAY_DYE, names[k], List.of("&7S'asseoir avec &e" + U.fmt(amount) + " jetons", ok ? "&aClic pour t'asseoir" : t.players() >= 6 ? "&cTable pleine" : "&cPas assez de jetons"), false),
                    ok ? c -> sit(p, t, amount) : null);
        }
        g.set(22, U.item(Material.BOOK, "&eRegles", List.of("&7Texas Hold'em, 2 a 6 joueurs.", "&7Deux cartes a toi, cinq au milieu,", "&7la meilleure main de 5 cartes gagne.",
                "&7Tu as " + pl.cfgLong("poker.action-time", 30) + " s pour jouer.", "&7Tes jetons te sont rendus en te levant."), false), null);
        p.openInventory(g.inv);
    }

    void sit(Player p, Table t, long buyIn) {
        if (tableOf(p.getUniqueId()) != null) {
            return;
        }
        int free = -1;
        for (int i = 0; i < 6; i++) {
            if (t.seats[i] == null) {
                free = i;
                break;
            }
        }
        if (free < 0) {
            U.msg(p, "&cTable pleine.");
            p.closeInventory();
            return;
        }
        if (!pl.withdraw(p, buyIn)) {
            U.msg(p, "&cPas assez de jetons.");
            return;
        }
        Seat s = new Seat();
        s.id = p.getUniqueId();
        s.name = p.getName();
        s.stack = buyIn;
        t.seats[free] = s;
        tell(t, "&e" + p.getName() + " &7s'assoit avec &e" + U.fmt(buyIn) + " jetons.");
        openGui(p, t, s);
        refreshAll(t);
    }

    /** Se lever : jetons rendus tout de suite ; en pleine main on se couche d'abord. */
    void standUp(UUID id, String reason) {
        Table t = tableOf(id);
        if (t == null) {
            return;
        }
        int i = t.seatOf(id);
        Seat s = t.seats[i];
        boolean inHand = (t.state == BETTING || t.state == PAUSE) && s.inHand;
        if (inHand && t.state == BETTING && !s.folded && !s.allIn) {
            // un joueur a tapis garde sa part du pot (versee sur son solde a l'abattage)
            s.folded = true;
            s.last = "&cCouche";
        }
        long back = s.stack;
        s.stack = 0;
        if (back > 0) {
            pl.deposit(id, back);
        }
        Player p = s.player();
        if (p != null) {
            U.msg(p, "&7Tu quittes la " + t.name + (back > 0 ? " avec &e" + U.fmt(back) + " jetons&7." : "."));
            if (s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
                p.closeInventory();
            }
        }
        tell(t, "&e" + s.name + " &7quitte la table" + (reason == null ? "." : " (" + reason + ")."));
        if (inHand) {
            s.gone = true;
            if (t.state == BETTING) {
                if (alive(t) <= 1) {
                    winByFold(t);
                } else if (t.actor == i) {
                    afterAction(t, i);
                }
            }
        } else {
            t.seats[i] = null;
        }
        refreshAll(t);
    }

    // ---------------- deroulement ----------------

    private boolean eligible(Seat s) {
        return s != null && !s.gone && !s.leave && s.stack > 0 && s.player() != null;
    }

    private int nextIn(Table t, int from) {
        for (int k = 1; k <= 6; k++) {
            int j = Math.floorMod(from + k, 6);
            if (t.seats[j] != null && t.seats[j].inHand) {
                return j;
            }
        }
        return -1;
    }

    private boolean canAct(Seat s) {
        return s != null && s.inHand && !s.folded && !s.allIn && !s.gone;
    }

    private boolean needsAct(Table t, Seat s) {
        return canAct(s) && (!s.acted || s.bet < t.currentBet);
    }

    private int nextToAct(Table t, int from) {
        for (int k = 1; k <= 6; k++) {
            int j = Math.floorMod(from + k, 6);
            if (needsAct(t, t.seats[j])) {
                return j;
            }
        }
        return -1;
    }

    private int alive(Table t) {
        int n = 0;
        for (Seat s : t.seats) {
            if (s != null && s.inHand && !s.folded) {
                n++;
            }
        }
        return n;
    }

    /** Toutes les 10 ticks. */
    void tick() {
        long now = System.currentTimeMillis();
        for (Table t : tables) {
            int el = 0;
            for (Seat s : t.seats) {
                if (eligible(s)) {
                    el++;
                }
            }
            switch (t.state) {
                case WAIT -> {
                    if (el >= 2) {
                        t.state = COUNTDOWN;
                        t.countdown = 10;
                        tell(t, "&7Nouvelle main dans 5 secondes...");
                        refreshAll(t);
                    }
                }
                case COUNTDOWN -> {
                    if (el < 2) {
                        t.state = WAIT;
                        refreshAll(t);
                    } else if (--t.countdown <= 0) {
                        startHand(t);
                    }
                }
                case BETTING -> {
                    if (t.actor >= 0 && now > t.deadline) {
                        timeout(t);
                    } else if (t.actor >= 0 && (now / 500) % 2 == 0) {
                        refreshAll(t);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void post(Seat s, long amount) {
        long a = Math.min(s.stack, amount);
        s.stack -= a;
        s.bet += a;
        s.total += a;
        if (s.stack == 0) {
            s.allIn = true;
        }
    }

    private void startHand(Table t) {
        t.hand++;
        t.board.clear();
        t.deck = Cards.shoe(1, rnd);
        int n = 0;
        for (Seat s : t.seats) {
            if (s == null) {
                continue;
            }
            s.hole.clear();
            s.bet = 0;
            s.total = 0;
            s.folded = false;
            s.allIn = false;
            s.acted = false;
            s.show = false;
            s.last = "";
            s.inHand = eligible(s);
            if (s.inHand) {
                n++;
            }
        }
        if (n < 2) {
            t.state = WAIT;
            return;
        }
        t.dealer = nextIn(t, t.dealer);
        int sbI;
        int bbI;
        if (n == 2) {
            sbI = t.dealer;
            bbI = nextIn(t, sbI);
        } else {
            sbI = nextIn(t, t.dealer);
            bbI = nextIn(t, sbI);
        }
        post(t.seats[sbI], t.sb);
        t.seats[sbI].last = "&7Petite blinde";
        post(t.seats[bbI], t.bb);
        t.seats[bbI].last = "&7Grosse blinde";
        t.currentBet = t.bb;
        t.minRaise = t.bb;
        for (int round = 0; round < 2; round++) {
            int j = t.dealer;
            for (int k = 0; k < n; k++) {
                j = nextIn(t, j);
                t.seats[j].hole.add(t.deck.poll());
            }
        }
        t.street = 0;
        t.state = BETTING;
        tell(t, "&b&lMain n°" + t.hand + " &7- donneur : &e" + t.seats[t.dealer].name + "&7, blindes " + U.fmt(t.sb) + "/" + U.fmt(t.bb));
        for (Seat s : t.seats) {
            Player p = s == null ? null : s.player();
            if (p != null && s.inHand) {
                U.msg(p, "&7Tes cartes : " + Cards.names(s.hole));
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
            }
        }
        int first = nextToAct(t, bbI);
        if (first < 0) {
            endStreet(t);
        } else {
            setActor(t, first);
        }
    }

    private void setActor(Table t, int i) {
        t.actor = i;
        t.deadline = System.currentTimeMillis() + pl.cfgLong("poker.action-time", 30) * 1000L;
        Player p = t.seats[i].player();
        if (p != null) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            p.sendActionBar(U.c("&e&lA toi de jouer ! &7(" + t.name + ")"));
            if (t.seats[i].gui == null || p.getOpenInventory().getTopInventory().getHolder() != t.seats[i].gui) {
                U.msg(p, "&eA toi de jouer a la " + t.name + " ! &7Clique sur la table ou tape &f/casino poker&7.");
            }
        }
        refreshAll(t);
    }

    enum Act { FOLD, CHECK, CALL, RAISE, ALLIN }

    void act(Player p, Table t, Act a, long target) {
        int i = t.seatOf(p.getUniqueId());
        if (i < 0 || t.state != BETTING || t.actor != i) {
            return;
        }
        Seat s = t.seats[i];
        s.timeouts = 0;
        apply(t, i, a, target);
    }

    private void apply(Table t, int i, Act a, long target) {
        Seat s = t.seats[i];
        long toCall = t.currentBet - s.bet;
        switch (a) {
            case FOLD -> {
                if (toCall <= 0 && !s.leave) {
                    // pas de sur-mise : on ne se couche pas, on parle
                    apply(t, i, Act.CHECK, 0);
                    return;
                }
                s.folded = true;
                s.last = "&cCouche";
                tell(t, "&e" + s.name + " &7se couche.");
            }
            case CHECK -> {
                if (toCall > 0) {
                    return;
                }
                s.last = "&7Parole";
                tell(t, "&e" + s.name + " &7parle.");
            }
            case CALL -> {
                if (toCall <= 0) {
                    return;
                }
                long add = Math.min(toCall, s.stack);
                post(s, add);
                s.last = s.allIn ? "&6Tapis" : "&aSuit";
                tell(t, "&e" + s.name + " &7suit (&e" + U.fmt(add) + "&7)" + (s.allIn ? " &6- tapis !" : "."));
            }
            case RAISE, ALLIN -> {
                long max = s.bet + s.stack;
                long to = a == Act.ALLIN ? max : Math.min(target, max);
                if (to <= t.currentBet) {
                    // tapis inferieur ou egal a la mise a suivre : simple suivi
                    if (toCall <= 0) {
                        return;
                    }
                    apply(t, i, Act.CALL, 0);
                    return;
                }
                if (to < t.currentBet + t.minRaise && to < max) {
                    return;
                }
                long raise = to - t.currentBet;
                boolean full = raise >= t.minRaise;
                if (full) {
                    t.minRaise = raise;
                }
                post(s, to - s.bet);
                t.currentBet = to;
                if (full) {
                    // une relance complete rouvre les encheres ; un tapis incomplet oblige seulement a suivre
                    for (Seat o : t.seats) {
                        if (o != null && o != s && canAct(o)) {
                            o.acted = false;
                        }
                    }
                }
                s.last = s.allIn ? "&6Tapis (" + U.fmt(to) + ")" : "&dRelance a " + U.fmt(to);
                tell(t, "&e" + s.name + (s.allIn ? " &6fait tapis a &e" : " &drelance a &e") + U.fmt(to) + "&7.");
                for (Seat o : t.seats) {
                    Player op = o == null ? null : o.player();
                    if (op != null && o != s) {
                        op.playSound(op.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.2f);
                    }
                }
            }
        }
        s.acted = true;
        afterAction(t, i);
    }

    private void afterAction(Table t, int i) {
        if (alive(t) <= 1) {
            winByFold(t);
            return;
        }
        int j = nextToAct(t, i);
        if (j < 0) {
            endStreet(t);
        } else {
            setActor(t, j);
        }
    }

    private void timeout(Table t) {
        Seat s = t.seats[t.actor];
        s.timeouts++;
        Player p = s.player();
        if (p != null) {
            U.msg(p, "&cTemps ecoule !");
        }
        if (s.timeouts >= 2) {
            s.leave = true;
            tell(t, "&e" + s.name + " &7est absent : il sera leve de table apres la main.");
        }
        apply(t, t.actor, t.currentBet > s.bet ? Act.FOLD : Act.CHECK, 0);
    }

    private void endStreet(Table t) {
        t.actor = -1;
        for (Seat s : t.seats) {
            if (s != null) {
                s.bet = 0;
                s.acted = false;
            }
        }
        t.currentBet = 0;
        t.minRaise = t.bb;
        if (t.street >= 3) {
            showdown(t);
            return;
        }
        t.street++;
        int cards = t.street == 1 ? 3 : 1;
        for (int k = 0; k < cards; k++) {
            t.board.add(t.deck.poll());
        }
        tell(t, "&b" + STREETS[t.street] + " &7: " + Cards.names(t.board));
        for (Seat s : t.seats) {
            Player p = s == null ? null : s.player();
            if (p != null && !s.gone) {
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.9f);
            }
        }
        int can = 0;
        for (Seat s : t.seats) {
            if (canAct(s)) {
                can++;
            }
        }
        if (can <= 1) {
            // plus personne ne peut miser : on retourne les cartes une a une
            for (Seat s : t.seats) {
                if (s != null && s.inHand && !s.folded) {
                    s.show = true;
                }
            }
            refreshAll(t);
            int hand = t.hand;
            pl.later(40, () -> {
                if (t.hand == hand && t.state == BETTING) {
                    endStreet(t);
                }
            });
            return;
        }
        int first = nextToAct(t, t.dealer);
        if (first < 0) {
            endStreet(t);
        } else {
            setActor(t, first);
        }
    }

    private void winByFold(Table t) {
        t.actor = -1;
        Seat w = null;
        for (Seat s : t.seats) {
            if (s != null && s.inHand && !s.folded) {
                w = s;
            }
        }
        if (w != null) {
            // ce qu'un siege a mise au-dela du total du gagnant n'a jamais ete suivi : il lui est rendu
            for (Seat s : t.seats) {
                if (s == null || s == w) {
                    continue;
                }
                long back = s.total - w.total;
                if (back > 0) {
                    if (s.gone) {
                        pl.deposit(s.id, back);
                    } else {
                        s.stack += back;
                    }
                    s.total -= back;
                    tell(t, "&7" + s.name + " recupere sa mise non suivie (&e" + U.fmt(back) + "&7).");
                }
            }
        }
        long pot = t.pot();
        if (w != null) {
            award(t, w, pot);
            tell(t, "&a" + w.name + " &aremporte le pot de &e" + U.fmt(pot) + " jetons&a.");
        }
        pause(t, 60);
    }

    private void award(Table t, Seat w, long amount) {
        if (amount <= 0) {
            return;
        }
        if (w.gone) {
            pl.deposit(w.id, amount);
        } else {
            w.stack += amount;
        }
        Player p = w.player();
        if (p != null) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }
    }

    private void showdown(Table t) {
        t.actor = -1;
        List<Seat> contenders = new ArrayList<>();
        Map<Seat, Integer> score = new HashMap<>();
        for (Seat s : t.seats) {
            if (s != null && s.inHand && !s.folded) {
                contenders.add(s);
                List<Integer> all = new ArrayList<>(s.hole);
                all.addAll(t.board);
                score.put(s, Cards.best(all));
                s.show = true;
            }
        }
        tell(t, "&b&lAbattage &7- tableau : " + Cards.names(t.board));
        for (Seat s : contenders) {
            tell(t, " &e" + s.name + " &7: " + Cards.names(s.hole) + " &7- &f" + Cards.handName(score.get(s)));
        }
        // pots principaux et pots annexes
        TreeSet<Long> levels = new TreeSet<>();
        for (Seat s : t.seats) {
            if (s != null && s.total > 0) {
                levels.add(s.total);
            }
        }
        Map<Seat, Long> gains = new HashMap<>();
        // mise non suivie : la tranche ou un seul joueur a mise lui est rendue, ce n'est pas un gain
        Map<Seat, Long> refunds = new HashMap<>();
        long prev = 0;
        List<Seat> lastEligible = contenders;
        for (long level : levels) {
            long part = 0;
            Seat only = null;
            int contributors = 0;
            for (Seat s : t.seats) {
                if (s != null) {
                    long add = Math.min(s.total, level) - Math.min(s.total, prev);
                    part += add;
                    if (add > 0) {
                        contributors++;
                        only = s;
                    }
                }
            }
            prev = level;
            if (contributors == 1 && only != null) {
                refunds.merge(only, part, Long::sum);
                continue;
            }
            List<Seat> eligible = new ArrayList<>();
            for (Seat s : contenders) {
                if (s.total >= level) {
                    eligible.add(s);
                }
            }
            if (eligible.isEmpty()) {
                eligible = lastEligible;
            }
            lastEligible = eligible;
            int best = -1;
            for (Seat s : eligible) {
                best = Math.max(best, score.get(s));
            }
            List<Seat> winners = new ArrayList<>();
            for (int k = 1; k <= 6; k++) {
                Seat s = t.seats[Math.floorMod(t.dealer + k, 6)];
                if (s != null && eligible.contains(s) && score.get(s) == best) {
                    winners.add(s);
                }
            }
            long share = part / winners.size();
            long rest = part - share * winners.size();
            for (int k = 0; k < winners.size(); k++) {
                gains.merge(winners.get(k), share + (k == 0 ? rest : 0), Long::sum);
            }
        }
        for (Map.Entry<Seat, Long> e : refunds.entrySet()) {
            Seat s = e.getKey();
            if (s.gone) {
                pl.deposit(s.id, e.getValue());
            } else {
                s.stack += e.getValue();
            }
            s.total -= e.getValue();
            tell(t, "&7" + s.name + " recupere sa mise non suivie (&e" + U.fmt(e.getValue()) + "&7).");
            if (!gains.containsKey(s)) {
                s.last = "&7Perdu";
            }
        }
        for (Map.Entry<Seat, Long> e : gains.entrySet()) {
            Seat s = e.getKey();
            award(t, s, e.getValue());
            long net = e.getValue() - s.total;
            tell(t, "&a" + s.name + " &aremporte &e" + U.fmt(e.getValue()) + " jetons &7(" + Cards.handName(score.get(s)) + ")");
            s.last = "&a+" + U.fmt(e.getValue());
            if (net >= pl.cfgLong("broadcast-min", 5000)) {
                pl.broadcast("&b" + s.name + " &7remporte un pot de &e" + U.fmt(e.getValue()) + " jetons &7au poker (" + Cards.handName(score.get(s)) + ") !");
            }
        }
        pause(t, 120);
    }

    private void pause(Table t, int ticks) {
        t.state = PAUSE;
        refreshAll(t);
        int hand = t.hand;
        pl.later(ticks, () -> {
            if (t.hand == hand && t.state == PAUSE) {
                endHand(t);
            }
        });
    }

    private void endHand(Table t) {
        for (int i = 0; i < 6; i++) {
            Seat s = t.seats[i];
            if (s == null) {
                continue;
            }
            s.inHand = false;
            s.bet = 0;
            s.total = 0;
            if (s.gone) {
                t.seats[i] = null;
                continue;
            }
            Player p = s.player();
            if (p == null) {
                standUp(s.id, "deconnecte");
            } else if (s.leave) {
                standUp(s.id, "absent");
            } else if (s.stack <= 0) {
                U.msg(p, "&cTu n'as plus de jetons a la table. Recave avec le bouton &eRecaver &cou leve-toi.");
            }
        }
        t.state = WAIT;
        t.actor = -1;
        refreshAll(t);
    }

    // ---------------- affichage ----------------

    private void tell(Table t, String m) {
        for (Seat s : t.seats) {
            Player p = s == null || s.gone ? null : s.player();
            if (p != null) {
                p.sendMessage(U.c("&b[" + t.name + "] &r" + m));
            }
        }
    }

    void refreshAll(Table t) {
        for (Seat s : t.seats) {
            Player p = s == null || s.gone ? null : s.player();
            if (p != null && s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
                render(p, t, s);
            }
        }
        pl.pokerBoard(t.id, label(t));
    }

    String label(Table t) {
        StringBuilder b = new StringBuilder("&b&lTEXAS HOLD'EM\n&7" + t.name + " - blindes " + U.fmt(t.sb) + "/" + U.fmt(t.bb) + "\n&f" + t.players() + "/6 joueurs");
        if (t.state == BETTING || t.state == PAUSE) {
            b.append(" &7- pot &e").append(U.fmt(t.pot()));
            if (!t.board.isEmpty()) {
                b.append("\n").append(Cards.names(t.board));
            }
        } else {
            b.append("\n&7Clic pour t'asseoir");
        }
        return b.toString();
    }

    void openGui(Player p, Table t, Seat s) {
        s.gui = new Gui("poker", 54, "&8" + t.name);
        render(p, t, s);
        p.openInventory(s.gui.inv);
    }

    private void render(Player p, Table t, Seat me) {
        Gui g = me.gui;
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            Seat s = t.seats[i];
            if (s == null || s.gone) {
                g.set(SEAT_SLOTS[i], U.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Place libre", List.of(), false), null);
                continue;
            }
            boolean turn = t.state == BETTING && t.actor == i;
            List<String> lore = new ArrayList<>();
            lore.add("&7Tapis : &e" + U.fmt(s.stack));
            if (s.bet > 0) {
                lore.add("&7Mise : &6" + U.fmt(s.bet));
            }
            if (!s.last.isEmpty()) {
                lore.add(s.last);
            }
            if (turn) {
                lore.add("&e&lA lui de jouer &7(" + Math.max(0, (t.deadline - now) / 1000) + " s)");
            }
            if (s.show && s.inHand && !s.folded && !s.hole.isEmpty()) {
                lore.add("&7Cartes : " + Cards.names(s.hole));
            }
            if (!s.inHand && (t.state == BETTING || t.state == PAUSE)) {
                lore.add("&8Attend la prochaine main");
            }
            String prefix = (i == t.dealer && t.state != WAIT ? "&f[D] " : "") + (turn ? "&e▶ " : s.folded ? "&8" : "&f");
            g.set(SEAT_SLOTS[i], U.head(s.id, prefix + s.name, lore, turn), null);
            if (s.bet > 0) {
                g.set(BET_ROW[i], U.item(U.chip(s.bet), "&6" + U.fmt(s.bet), List.of(), false), null);
            }
        }
        g.set(4, U.item(Material.PAPER, "&b&l" + t.name, List.of("&7Blindes " + U.fmt(t.sb) + "/" + U.fmt(t.bb),
                t.state == BETTING ? "&7" + STREETS[t.street] + " - main n°" + t.hand : t.state == COUNTDOWN ? "&7Nouvelle main..." : t.state == PAUSE ? "&7Fin de la main" : "&7En attente de joueurs (2 min.)"), false), null);
        g.set(13, U.item(Material.GOLD_BLOCK, "&6&lPot : " + U.fmt(t.pot()), List.of(), t.pot() > 0), null);
        for (int k = 0; k < 5; k++) {
            g.set(20 + k, k < t.board.size() ? Cards.item(t.board.get(k), null) : U.pane(Material.BLACK_STAINED_GLASS_PANE), null);
        }
        if (me.inHand && !me.hole.isEmpty()) {
            g.set(30, Cards.item(me.hole.get(0), null), null);
            g.set(32, Cards.item(me.hole.get(1), null), null);
            if (!t.board.isEmpty()) {
                List<Integer> all = new ArrayList<>(me.hole);
                all.addAll(t.board);
                g.set(31, U.item(Material.NAME_TAG, "&fTa main : &e" + Cards.handName(Cards.best(all)), List.of(), false), null);
            } else {
                g.set(31, U.item(Material.NAME_TAG, "&fTes cartes", List.of(Cards.names(me.hole)), false), null);
            }
        }
        g.set(36, U.item(Material.SUNFLOWER, "&6Ton tapis : &e" + U.fmt(me.stack), List.of("&7Hors table : &e" + U.fmt(pl.data(p).chips)), false), null);
        if (t.state == BETTING && t.actor >= 0) {
            Seat a = t.seats[t.actor];
            g.set(40, U.item(Material.CLOCK, a == me ? "&e&lA toi ! &7" + Math.max(0, (t.deadline - now) / 1000) + " s" : "&7Au tour de &e" + a.name, List.of(), a == me), null);
        }
        boolean myTurn = t.state == BETTING && t.actor >= 0 && t.seats[t.actor] == me;
        if (myTurn) {
            long toCall = t.currentBet - me.bet;
            long max = me.bet + me.stack;
            if (toCall > 0) {
                g.set(45, Icons.icon(U.item(Material.RED_CONCRETE, "&c&lSE COUCHER", List.of(), false), "po_coucher"), c -> act(p, t, Act.FOLD, 0));
            } else {
                // rien a suivre : se coucher n'a pas de sens, on peut parler gratuitement
                g.set(45, U.item(Material.GRAY_DYE, "&7Se coucher", List.of("&8Aucune mise a suivre :", "&8parle gratuitement a la place."), false), null);
            }
            if (toCall <= 0) {
                g.set(46, Icons.icon(U.item(Material.LIME_CONCRETE, "&a&lPAROLE", List.of("&7Ne rien miser"), false), "po_parole"), c -> act(p, t, Act.CHECK, 0));
            } else {
                g.set(46, Icons.icon(U.item(Material.LIME_CONCRETE, "&a&lSUIVRE " + U.fmt(Math.min(toCall, me.stack)), List.of(), false), "po_suivre"), c -> act(p, t, Act.CALL, 0));
            }
            long potAfterCall = t.pot() + Math.max(0, toCall);
            long[] targets = {t.currentBet + t.minRaise, t.currentBet + potAfterCall / 2, t.currentBet + potAfterCall};
            String[] names = {"&eRelance min", "&6Relance 1/2 pot", "&dRelance pot"};
            for (int k = 0; k < 3; k++) {
                long to = Math.min(targets[k], max);
                boolean ok = to > t.currentBet && (to >= t.currentBet + t.minRaise || to == max) && (k == 0 || targets[k] > targets[0]);
                if (ok) {
                    final long fto = to;
                    g.set(47 + k, U.item(Material.YELLOW_CONCRETE, names[k] + " &7a &f" + U.fmt(to) + (to == max ? " &6(tapis)" : ""), List.of(), false), c -> act(p, t, Act.RAISE, fto));
                }
            }
            if (me.stack > 0) {
                g.set(50, U.item(Material.TNT, "&6&lTAPIS &7(" + U.fmt(max) + ")", List.of("&7Miser tous tes jetons"), false), c -> act(p, t, Act.ALLIN, 0));
            }
            if (max > t.currentBet + t.minRaise) {
                // relance libre : le joueur tape son montant dans le chat (suggestion #108)
                g.set(51, U.item(Material.WRITABLE_BOOK, "&d&lRELANCE AU CHOIX", List.of("&7Tape le montant dans le chat", "&7(entre &e" + U.fmt(t.currentBet + t.minRaise) + " &7et &e" + U.fmt(max) + "&7)"), false), c -> askRaise(p, t));
            }
        }
        boolean between = !me.inHand || t.state == WAIT || t.state == COUNTDOWN;
        if (between && me.stack < t.maxBuy) {
            long add = Math.min(t.maxBuy - me.stack, pl.data(p).chips);
            if (add > 0) {
                g.set(51, U.item(Material.GOLD_NUGGET, "&eRecaver &7(+" + U.fmt(add) + ")", List.of("&7Remet ton tapis a " + U.fmt(me.stack + add)), false), c -> {
                    if (pl.withdraw(p, add)) {
                        me.stack += add;
                        tell(t, "&e" + me.name + " &7recave (&e" + U.fmt(add) + "&7).");
                        refreshAll(t);
                    }
                });
            }
        }
        g.set(52, U.item(Material.OAK_DOOR, "&cQuitter la table", List.of("&7Tes jetons te sont rendus.", me.inHand && t.state == BETTING && !me.folded ? "&cTu te couches pour cette main." : ""), false),
                c -> standUp(p.getUniqueId(), null));
        g.set(53, U.item(Material.BARRIER, "&7Fermer le menu", List.of("&7Tu restes assis.", "&7Clique sur la table pour revenir."), false), c -> p.closeInventory());
    }

    void shutdown() {
        for (Table t : tables) {
            for (int i = 0; i < 6; i++) {
                Seat s = t.seats[i];
                if (s == null) {
                    continue;
                }
                // main en cours annulee : chacun recupere son tapis + ce qu'il a mis au pot
                long back = (s.gone ? 0 : s.stack) + (t.state == BETTING && s.inHand ? s.total : 0);
                if (back > 0) {
                    pl.deposit(s.id, back);
                }
                t.seats[i] = null;
            }
            t.state = WAIT;
        }
    }

    /** Jetons poses aux tables par joueur (tapis + mise dans la main en cours), pour la sauvegarde anti-crash. */
    Map<UUID, Long> onTables() {
        Map<UUID, Long> m = new HashMap<>();
        for (Table t : tables) {
            for (Seat s : t.seats) {
                if (s == null || s.id == null) {
                    continue;
                }
                long v = (s.gone ? 0 : s.stack) + (t.state == BETTING && s.inHand ? s.total : 0);
                if (v > 0) {
                    m.merge(s.id, v, Long::sum);
                }
            }
        }
        return m;
    }

    static ConfigurationSection none() {
        return null;
    }
}
