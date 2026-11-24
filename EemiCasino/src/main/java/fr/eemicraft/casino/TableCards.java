package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;

/**
 * Cartes posees en vrai sur les tables de blackjack (modeles eemicraft:casino/carte/...), avec le score au-dessus.
 * Une table montre la partie du dernier joueur qui y a distribue ; tant que sa main est en cours, les autres
 * joueurs de cette table ne la remplacent pas. Les cartes partent de la main du croupier et glissent jusqu'a leur place.
 * Joueur cote est (x croissants), croupier cote ouest : les cartes sont posees face visible, le haut vers le croupier.
 */
final class TableCards {
    static final float SCALE = 0.42f;

    private static final class Shown {
        final int card;
        final ItemDisplay e;
        Location target;

        Shown(int card, ItemDisplay e, Location target) {
            this.card = card;
            this.e = e;
            this.target = target;
        }
    }

    private static final class Table {
        UUID owner;
        boolean ownerPlaying;
        int token;
        final Map<String, Shown> shown = new LinkedHashMap<>();
        TextDisplay score;
    }

    private final EemiCasino pl;
    private final Table[] tables = new Table[Layout.BJ_Z.length];

    TableCards(EemiCasino pl) {
        this.pl = pl;
        for (int i = 0; i < tables.length; i++) {
            tables[i] = new Table();
        }
    }

    /** Apres (re)creation des entites du casino : les anciennes cartes ont ete retirees avec les autres entites. */
    void reset(TextDisplay[] scores) {
        for (int i = 0; i < tables.length; i++) {
            Table t = tables[i];
            for (Shown s : t.shown.values()) {
                if (s.e != null && s.e.isValid()) {
                    s.e.remove();
                }
            }
            t.shown.clear();
            t.owner = null;
            t.ownerPlaying = false;
            t.token++;
            t.score = i < scores.length ? scores[i] : null;
        }
    }

    private static Quaternionf cardRot(double jitter) {
        // face avant du modele (sud) vers le haut, haut de la carte vers l'ouest (cote croupier)
        return new Quaternionf().rotateY((float) Math.toRadians(90 + jitter)).rotateX((float) Math.toRadians(-90));
    }

    private Location dealerHand(int table) {
        return new Location(pl.world, Layout.BJ_X1 - 0.1, Layout.FLOOR + 2.45, Layout.BJ_Z[table] + 0.5);
    }

    void sync(Player p, Blackjack.Session s) {
        if (pl.world == null || s.table < 0 || s.table >= tables.length || !pl.inCasino(p)) {
            return;
        }
        Table t = tables[s.table];
        boolean playing = s.state == Blackjack.PLAYING;
        if (t.owner != null && !t.owner.equals(p.getUniqueId()) && t.ownerPlaying && Bukkit.getPlayer(t.owner) != null) {
            return;
        }
        t.owner = p.getUniqueId();
        t.ownerPlaying = playing;
        int token = ++t.token;
        boolean models = Models.on(pl);
        double cz = Layout.BJ_Z[s.table] + 0.5;
        double y = Layout.FLOOR + 2.075;
        Map<String, Object[]> want = new LinkedHashMap<>();
        // croupier : rangee cote ouest
        int nd = s.dealer.size();
        for (int i = 0; i < nd; i++) {
            want.put("d" + i, new Object[]{s.dealer.get(i), new Location(pl.world, Layout.BJ_X1 + 1.45, y + i * 0.004, cz + (i - (nd - 1) / 2.0) * 0.31)});
        }
        // joueur : une rangee cote est, mains separees cote a cote
        int nh = s.hands.size();
        for (int h = 0; h < nh; h++) {
            List<Integer> hand = s.hands.get(h);
            double hz = cz + (h - (nh - 1) / 2.0) * 1.0;
            double step = nh == 1 ? 0.31 : 0.16;
            for (int i = 0; i < hand.size(); i++) {
                want.put("h" + h + "-" + i, new Object[]{hand.get(i), new Location(pl.world, Layout.BJ_X1 + 2.45, y + i * 0.004, hz + (i - (hand.size() - 1) / 2.0) * step)});
            }
        }
        if (models) {
            Iterator<Map.Entry<String, Shown>> it = t.shown.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Shown> e = it.next();
                Object[] w = want.get(e.getKey());
                if (w == null || (int) w[0] != e.getValue().card || e.getValue().e == null || !e.getValue().e.isValid()) {
                    if (e.getValue().e != null && e.getValue().e.isValid()) {
                        e.getValue().e.remove();
                    }
                    it.remove();
                }
            }
            int delay = 0;
            for (Map.Entry<String, Object[]> e : want.entrySet()) {
                int card = (int) e.getValue()[0];
                Location target = (Location) e.getValue()[1];
                Shown sh = t.shown.get(e.getKey());
                if (sh != null) {
                    if (sh.target.distanceSquared(target) > 1.0e-4 && sh.e.isValid()) {
                        sh.target = target;
                        sh.e.setTeleportDuration(4);
                        sh.e.teleport(target);
                    }
                    continue;
                }
                double jitter = (Math.random() - 0.5) * 8;
                ItemDisplay d = Models.spawn(pl, dealerHand(s.table), Models.card(card), cardRot(jitter), SCALE);
                if (d == null) {
                    continue;
                }
                Shown created = new Shown(card, d, target);
                t.shown.put(e.getKey(), created);
                final int dl = 2 + delay;
                delay += 2;
                // vise la place ACTUELLE de la carte (un sync entre-temps a pu la deplacer)
                pl.later(dl, () -> {
                    if (d.isValid()) {
                        d.setTeleportDuration(6);
                        d.teleport(created.target);
                    }
                });
            }
        }
        // score au-dessus de la table
        if (t.score != null && t.score.isValid()) {
            if (s.dealer.isEmpty() && s.hands.isEmpty()) {
                t.score.text(U.c(""));
            } else {
                List<String> tot = new ArrayList<>();
                for (List<Integer> h : s.hands) {
                    tot.add(String.valueOf(Blackjack.total(h)));
                }
                String line = "&e" + p.getName() + " : &f&l" + String.join(" &7/ &f&l", tot) + "   &6Croupier : &f&l" + (s.dealer.isEmpty() ? "-" : Blackjack.total(s.dealer));
                t.score.text(U.c(line + "\n" + s.message));
            }
        }
        if (s.state == Blackjack.FINISHED) {
            pl.later(400, () -> {
                if (t.token == token) {
                    clear(t);
                }
            });
        }
    }

    /** Le joueur quitte : sa table se vide quelques secondes plus tard. */
    void release(int table, UUID id) {
        if (table < 0 || table >= tables.length) {
            return;
        }
        Table t = tables[table];
        if (!id.equals(t.owner)) {
            return;
        }
        t.ownerPlaying = false;
        int token = ++t.token;
        pl.later(100, () -> {
            if (t.token == token) {
                clear(t);
            }
        });
    }

    private void clear(Table t) {
        for (Shown s : t.shown.values()) {
            if (s.e != null && s.e.isValid()) {
                s.e.remove();
            }
        }
        t.shown.clear();
        t.owner = null;
        t.ownerPlaying = false;
        if (t.score != null && t.score.isValid()) {
            t.score.text(U.c(""));
        }
    }

    Map<Integer, Integer> counts() {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < tables.length; i++) {
            m.put(i, tables[i].shown.size());
        }
        return m;
    }
}
