package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Video poker "Jacks or Better" (table 8/5 : retour ~97 % en jouant bien). */
final class VideoPoker {
    static final int[] CARD_SLOTS = {11, 12, 13, 14, 15};
    static final int[] HOLD_SLOTS = {20, 21, 22, 23, 24};
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    final class Session {
        final UUID id;
        Gui gui;
        Deque<Integer> deck;
        final int[] cards = {-1, -1, -1, -1, -1};
        final boolean[] hold = new boolean[5];
        boolean dealt;
        long bet;
        int machine = -1;
        String message = "&7Choisis ta mise puis &aDistribue&7.";

        Session(UUID id) {
            this.id = id;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();

    VideoPoker(EemiCasino pl) {
        this.pl = pl;
    }

    /** Multiplicateur (mise rendue comprise) et nom de la combinaison. */
    static Object[] payout(int[] cards) {
        int score = Cards.eval5(cards);
        int cat = Cards.category(score);
        switch (cat) {
            case 8:
                return (score & 0xF) == 12 ? new Object[]{250, "Quinte flush royale"} : new Object[]{50, "Quinte flush"};
            case 7:
                return new Object[]{25, "Carre"};
            case 6:
                return new Object[]{8, "Full"};
            case 5:
                return new Object[]{5, "Couleur"};
            case 4:
                return new Object[]{4, "Quinte"};
            case 3:
                return new Object[]{3, "Brelan"};
            case 2:
                return new Object[]{2, "Double paire"};
            case 1:
                int pair = (score >> 16) & 0xF;
                return pair >= 9 ? new Object[]{1, "Paire de valets ou mieux"} : new Object[]{0, "Petite paire"};
            default:
                return new Object[]{0, "Rien"};
        }
    }

    void open(Player p) {
        open(p, -1);
    }

    void open(Player p, int machine) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        if (!s.dealt) {
            if (s.machine >= 0 && s.machine != machine) {
                pl.machines.releaseVp(s.machine, p.getUniqueId());
            }
            if (machine >= 0 && !pl.machines.claimVp(machine, p.getUniqueId())) {
                U.msg(p, "&7Cette machine est utilisee par &e" + pl.machines.vpOwnerName(machine) + "&7 : tu joues sans son ecran.");
                machine = -1;
            }
            s.machine = machine;
        }
        s.gui = new Gui("videopoker", 45, "&8Video poker - Jacks or Better");
        s.gui.onClose = () -> {
            if (!s.dealt) {
                pl.machines.releaseVp(s.machine, s.id);
            }
        };
        render(p, s);
        p.openInventory(s.gui.inv);
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        pl.machines.showVideoPoker(s.machine, s.cards, s.hold, s.dealt);
        g.fill(Material.BLUE_STAINED_GLASS_PANE);
        g.set(4, U.item(Material.BOOK, "&eTable des gains", List.of(
                "&dQuinte flush royale &7: x250", "&5Quinte flush &7: x50", "&6Carre &7: x25", "&eFull &7: x8",
                "&bCouleur &7: x5", "&aQuinte &7: x4", "&fBrelan &7: x3", "&fDouble paire &7: x2",
                "&7Paire de valets ou mieux : x1", "", "&8Gains mise comprise."), false), null);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            if (s.cards[i] < 0) {
                g.set(CARD_SLOTS[i], Cards.back(), null);
                continue;
            }
            g.set(CARD_SLOTS[i], Cards.item(s.cards[i], List.of(s.dealt ? "&7Clic pour garder / lacher" : "")), s.dealt ? t -> toggle(p, s, k) : null);
            if (s.dealt) {
                g.set(HOLD_SLOTS[i], U.item(s.hold[i] ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                        s.hold[i] ? "&a&lGARDEE" : "&7Garder ?", List.of(), s.hold[i]), t -> toggle(p, s, k));
            }
        }
        g.set(29, U.item(Material.OAK_SIGN, s.message, List.of(), false), null);
        PData d = pl.data(p);
        g.set(36, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of(), false), null);
        if (s.dealt) {
            g.set(31, U.item(Material.EMERALD_BLOCK, "&a&lTIRER", List.of("&7Remplace les cartes non gardees"), true), t -> draw(p, s));
        } else {
            long bet = pl.currentBet(p, "videopoker");
            boolean ok = d.chips >= bet;
            g.set(31, U.item(ok ? Material.EMERALD_BLOCK : Material.GRAY_CONCRETE, "&a&lDISTRIBUER", List.of("&7Mise : &e" + U.fmt(bet), ok ? "" : "&cPas assez de jetons"), ok),
                    ok ? t -> deal(p, s) : null);
            pl.betBar(g, p, "videopoker", BET_SLOTS, () -> render(p, s));
        }
    }

    private void toggle(Player p, Session s, int i) {
        if (!s.dealt) {
            return;
        }
        s.hold[i] = !s.hold[i];
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, s.hold[i] ? 1.5f : 1f);
        render(p, s);
    }

    private void deal(Player p, Session s) {
        if (s.dealt) {
            return;
        }
        long bet = pl.currentBet(p, "videopoker");
        if (!pl.take(p, bet)) {
            U.msg(p, "&cIl te faut " + U.fmt(bet) + " jetons.");
            return;
        }
        s.bet = bet;
        s.deck = Cards.shoe(1, rnd);
        for (int i = 0; i < 5; i++) {
            s.cards[i] = s.deck.poll();
            s.hold[i] = false;
        }
        s.dealt = true;
        Object[] po = payout(s.cards);
        s.message = (int) po[0] > 0 ? "&eTu as deja : " + po[1] + " &7- garde tes cartes" : "&7Choisis les cartes a garder puis &aTire&7.";
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        render(p, s);
    }

    private void draw(Player p, Session s) {
        if (!s.dealt) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            if (!s.hold[i]) {
                s.cards[i] = s.deck.poll();
            }
        }
        s.dealt = false;
        Object[] po = payout(s.cards);
        int m = (int) po[0];
        long gross = s.bet * m;
        if (gross > 0) {
            pl.win(p, gross, s.bet, "video poker");
            long net = gross - s.bet;
            s.message = "&a&l" + po[1] + " ! &a+" + U.fmt(gross);
            p.playSound(p.getLocation(), m >= 25 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
            if (m >= 25 || net >= 5000) {
                pl.celebrate(p, m >= 50);
            }
        } else {
            s.message = "&c" + po[1] + " - perdu";
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
        }
        p.sendActionBar(U.c(s.message));
        render(p, s);
    }

    void quit(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.dealt) {
            // main abandonnee : on tire sans rien garder de plus (cartes gardees conservees)
            draw(p, s);
        }
        if (s != null) {
            pl.machines.releaseVp(s.machine, s.id);
        }
    }

    void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) {
            Player p = org.bukkit.Bukkit.getPlayer(s.id);
            if (p != null) {
                quit(p);
            }
        }
        sessions.clear();
    }
}
