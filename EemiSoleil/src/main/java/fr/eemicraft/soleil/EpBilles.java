package fr.eemicraft.soleil;

import static fr.eemicraft.soleil.Layout.*;

import fr.eemicraft.soleil.EemiSoleil.Joueur;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Epreuve 4 : billes, par paires, « pair ou impair ». Nombre impair : un joueur tire au sort est exempte.
 * Chacun commence avec billes.depart (10) billes. A chaque manche, le CACHEUR choisit combien de billes il serre dans
 * son poing (menu : 1 a toutes les siennes) ; le DEVINEUR mise (1 a toutes les siennes) et annonce pair ou impair.
 * Juste : il prend sa mise au cacheur (au plus ce que le cacheur possede) ; faux : il donne sa mise au cacheur (au plus
 * ce qu'il possede). Les roles s'echangent a chaque manche. Plus aucune bille = elimine. Sans decision en
 * billes.decision (15 s) : choix au hasard. Au temps (billes.temps, 180 s par paire), celui qui a le plus de billes
 * gagne (egalite : pile ou face).
 */
final class EpBilles extends Epreuve {
    enum Step { HIDE, GUESS, REVEAL, DONE }

    static final class Pair {
        final Joueur a, b;
        final int k;
        int ma, mb;
        Joueur hider, guesser;
        Step step = Step.HIDE;
        int hidden, bet = 1, stepTick, gameTick;

        Pair(Joueur a, Joueur b, int k, int m) {
            this.a = a;
            this.b = b;
            this.k = k;
            this.ma = m;
            this.mb = m;
        }

        int count(Joueur j) { return j == a ? ma : mb; }

        void add(Joueur j, int d) {
            if (j == a) ma += d;
            else mb += d;
        }

        Joueur other(Joueur j) { return j == a ? b : a; }
    }

    boolean started;
    int readyTick;
    final List<Pair> pairs = new ArrayList<>();
    final Map<UUID, Pair> byPlayer = new HashMap<>();
    Joueur exempt;

    EpBilles(EemiSoleil g) {
        super(g, "billes");
    }

    @Override String nom() { return "Billes"; }

    @Override
    String[] regles() {
        return new String[]{"Par paires, 10 billes chacun. Le cacheur serre des billes dans son poing.",
                "Le devineur mise et annonce pair ou impair : juste, il prend sa mise ; faux, il la donne.",
                "Plus aucune bille = éliminé. Clic droit sur le sac pour rouvrir le menu."};
    }

    @Override Location specSpot() { return new Location(g.world, BX - 38.5, FY + 9, 0.5, -90f, 20f); }

    @Override int[] box() { return SquidZones.BILLES_BOX; }

    int marbles() {
        return Math.max(2, g.cfgI("billes.depart", 10));
    }

    @Override
    void begin(List<Joueur> alive) {
        List<Joueur> l = shuffled(alive);
        if (l.size() % 2 == 1) {
            exempt = l.remove(0);
            Player p = exempt.player();
            if (p != null) {
                p.teleport(new Location(g.world, BX + 36.5, FY + 1, 0.5, 90f, 0f));
                g.title(p, "&e&lEXEMPTÉ", "&7Tu n'as pas de partenaire...", 2500);
            }
            g.tell("&e✦ n°" + exempt.numero() + " " + exempt.name + " &7est exempté (nombre impair).");
        }
        for (int k = 0; k + 1 < l.size(); k += 2) {
            Pair pr = new Pair(l.get(k), l.get(k + 1), k / 2, marbles());
            pairs.add(pr);
            byPlayer.put(pr.a.id, pr);
            byPlayer.put(pr.b.id, pr);
            int x = bPairX(pr.k % 8);
            place(pr.a, new Location(g.world, x + 0.5, FY + 1, -1.5, 0f, 10f));
            place(pr.b, new Location(g.world, x + 0.5, FY + 1, 2.5, 180f, 10f));
            EemiSoleil.msg(pr.a.player() != null ? pr.a.player() : Bukkit.getConsoleSender(), "&7Ton partenaire : &f" + pr.b.name);
            EemiSoleil.msg(pr.b.player() != null ? pr.b.player() : Bukkit.getConsoleSender(), "&7Ton partenaire : &f" + pr.a.name);
            updateItems(pr);
        }
        g.guard(new Location(g.world, BX - 34.5, FY + 1, -3.5, -90f, 0f), 1);
        g.guard(new Location(g.world, BX - 34.5, FY + 1, 4.5, -90f, 0f), 2);
        g.guard(new Location(g.world, BX + 33.5, FY + 1, -3.5, 90f, 0f), 0);
        readyTick = 0;
    }

    void place(Joueur j, Location l) {
        Player p = j.player();
        if (p == null) return;
        g.anchor(p, l);
        g.clearHands(p);
        p.getInventory().setItem(0, g.skin(EemiSoleil.named(Material.BUNDLE, "&6Sac de billes", "&7Clic droit : ouvrir le menu"), "sac_billes"));
        p.getInventory().setHeldItemSlot(0);
    }

    void updateItems(Pair pr) {
        for (Joueur j : new Joueur[]{pr.a, pr.b}) {
            Player p = j.player();
            if (p == null) continue;
            int n = pr.count(j);
            p.getInventory().setItem(4, n <= 0 ? null : marbleItem(Math.min(64, n), "&f" + n + " bille" + (n > 1 ? "s" : "")));
        }
    }

    ItemStack marbleItem(int amount, String name, String... lore) {
        ItemStack it = g.skin(EemiSoleil.named(Material.CLAY_BALL, name, lore), "bille");
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }

    int decision() {
        return Math.max(5, g.cfgI("billes.decision", 15)) * 20;
    }

    int gameTime() {
        return Math.max(30, g.cfgI("billes.temps", 180)) * 20;
    }

    @Override
    void tick() {
        if (!started) {
            readyTick++;
            if (readyTick % 20 == 1) {
                int left = (100 - readyTick + 19) / 20;
                for (Pair pr : pairs)
                    for (Joueur j : new Joueur[]{pr.a, pr.b}) {
                        Player p = j.player();
                        if (p != null) g.title(p, "&e" + left, "&7Pair ou impair ?", 900);
                    }
            }
            if (readyTick >= 100) {
                started = true;
                for (Pair pr : pairs) {
                    pr.hider = ThreadLocalRandom.current().nextBoolean() ? pr.a : pr.b;
                    pr.guesser = pr.other(pr.hider);
                    startStep(pr, Step.HIDE);
                }
                if (pairs.isEmpty()) finish();
            }
            return;
        }
        boolean any = false;
        for (Pair pr : pairs) {
            if (pr.step == Step.DONE) continue;
            any = true;
            pr.stepTick++;
            pr.gameTick++;
            if (pr.gameTick >= gameTime() && pr.step != Step.REVEAL) {
                byTime(pr);
                continue;
            }
            switch (pr.step) {
                case HIDE -> {
                    if (pr.stepTick >= decision()) hide(pr, 1 + ThreadLocalRandom.current().nextInt(pr.count(pr.hider)), true);
                }
                case GUESS -> {
                    if (pr.stepTick >= decision()) {
                        pr.bet = 1 + ThreadLocalRandom.current().nextInt(pr.count(pr.guesser));
                        guess(pr, ThreadLocalRandom.current().nextBoolean(), true);
                    }
                }
                case REVEAL -> {
                    if (pr.stepTick >= 50) {
                        Joueur h = pr.hider;
                        pr.hider = pr.guesser;
                        pr.guesser = h;
                        startStep(pr, Step.HIDE);
                    }
                }
                default -> {}
            }
            if (pr.step != Step.DONE && tick % 10 == 0) actionBars(pr);
        }
        if (!any && !done) finish();
    }

    void actionBars(Pair pr) {
        int left = Math.max(0, (gameTime() - pr.gameTick) / 20);
        for (Joueur j : new Joueur[]{pr.a, pr.b}) {
            Player p = j.player();
            if (p == null) continue;
            String role;
            if (pr.step == Step.HIDE) role = j == pr.hider ? "&eCache tes billes" : "&7" + pr.hider.name + " cache ses billes...";
            else if (pr.step == Step.GUESS) role = j == pr.guesser ? "&eMise et devine" : "&7" + pr.guesser.name + " réfléchit...";
            else role = "&7...";
            p.sendActionBar(EemiSoleil.c(role + " &8| &fToi &6" + pr.count(j) + " &8- &f" + pr.other(j).name + " &6" + pr.count(pr.other(j)) + " &8| &f⏱ " + left + " s"));
        }
    }

    void startStep(Pair pr, Step s) {
        pr.step = s;
        pr.stepTick = 0;
        if (s == Step.HIDE) {
            pr.bet = 1;
            Player h = pr.hider.player();
            if (h != null) openHide(h, pr);
            Player gp = pr.guesser.player();
            if (gp != null && gp.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) gp.closeInventory();
        } else if (s == Step.GUESS) {
            Player gp = pr.guesser.player();
            if (gp != null) openGuess(gp, pr);
        }
    }

    void openHide(Player p, Pair pr) {
        SquidMenu m = new SquidMenu("cacher", pr);
        int n = pr.count(pr.hider);
        Inventory inv = Bukkit.createInventory(m, 27, EemiSoleil.c("&8Combien de billes caches-tu ?"));
        m.inv = inv;
        for (int i = 1; i <= Math.min(n, 27); i++) inv.setItem(i - 1, marbleItem(i, "&eCacher " + i + " bille" + (i > 1 ? "s" : ""),
                "&7" + (i % 2 == 0 ? "pair" : "impair")));
        p.openInventory(inv);
    }

    void openGuess(Player p, Pair pr) {
        SquidMenu m = new SquidMenu("deviner", pr);
        int n = pr.count(pr.guesser);
        Inventory inv = Bukkit.createInventory(m, 27, EemiSoleil.c("&8Mise : " + pr.bet + " - pair ou impair ?"));
        m.inv = inv;
        for (int i = 1; i <= Math.min(n, 18); i++) {
            ItemStack it = marbleItem(i, (i == pr.bet ? "&a&l" : "&e") + "Miser " + i + " bille" + (i > 1 ? "s" : ""));
            if (i == pr.bet) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            inv.setItem(i - 1, it);
        }
        inv.setItem(21, g.skin(EemiSoleil.named(Material.WHITE_DYE, "&f&lPAIR", "&7Mise : " + pr.bet), "pair"));
        inv.setItem(23, g.skin(EemiSoleil.named(Material.BLACK_DYE, "&8&lIMPAIR", "&7Mise : " + pr.bet), "impair"));
        p.openInventory(inv);
    }

    @Override
    void onMenu(Player p, Joueur j, int slot, SquidMenu menu) {
        if (!(menu.ref instanceof Pair pr) || pr.step == Step.DONE) return;
        if (menu.kind.equals("cacher") && pr.step == Step.HIDE && pr.hider == j) {
            int n = slot + 1;
            if (n >= 1 && n <= pr.count(j)) hide(pr, n, false);
        } else if (menu.kind.equals("deviner") && pr.step == Step.GUESS && pr.guesser == j) {
            if (slot >= 0 && slot < 18 && slot + 1 <= pr.count(j)) {
                pr.bet = slot + 1;
                openGuess(p, pr);
            } else if (slot == 21) guess(pr, true, false);
            else if (slot == 23) guess(pr, false, false);
        }
    }

    void hide(Pair pr, int n, boolean auto) {
        pr.hidden = Math.max(1, Math.min(n, pr.count(pr.hider)));
        Player h = pr.hider.player();
        if (h != null) {
            h.closeInventory();
            EemiSoleil.msg(h, (auto ? "&7Temps écoulé : " : "&7") + "tu caches &e" + pr.hidden + " &7bille(s).");
            h.playSound(h.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1f, 1.4f);
        }
        startStep(pr, Step.GUESS);
    }

    void guess(Pair pr, boolean even, boolean auto) {
        int bet = Math.max(1, Math.min(pr.bet, pr.count(pr.guesser)));
        boolean right = (pr.hidden % 2 == 0) == even;
        Player gp = pr.guesser.player(), hp = pr.hider.player();
        if (gp != null) gp.closeInventory();
        int moved;
        String verdict;
        if (right) {
            moved = Math.min(bet, pr.count(pr.hider));
            pr.add(pr.hider, -moved);
            pr.add(pr.guesser, moved);
            verdict = "&a" + pr.guesser.name + " gagne " + moved;
        } else {
            moved = Math.min(bet, pr.count(pr.guesser));
            pr.add(pr.guesser, -moved);
            pr.add(pr.hider, moved);
            verdict = "&c" + pr.guesser.name + " perd " + moved;
        }
        String line = (auto ? "&7(au hasard) " : "") + "&f" + pr.guesser.name + " &7mise &e" + bet + " &7sur &f" + (even ? "pair" : "impair")
                + " &8- &f" + pr.hider.name + " &7cachait &e" + pr.hidden + " &8» " + verdict + " bille" + (moved > 1 ? "s" : "");
        for (Player p : new Player[]{gp, hp}) {
            if (p == null) continue;
            EemiSoleil.msg(p, line);
            p.playSound(p.getLocation(), right ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        }
        updateItems(pr);
        if (pr.ma <= 0 || pr.mb <= 0) {
            lose(pr, pr.ma <= 0 ? pr.a : pr.b, "Plus aucune bille");
            return;
        }
        startStep(pr, Step.REVEAL);
    }

    void byTime(Pair pr) {
        Joueur loser;
        if (pr.ma == pr.mb) {
            loser = ThreadLocalRandom.current().nextBoolean() ? pr.a : pr.b;
            for (Joueur j : new Joueur[]{pr.a, pr.b}) {
                Player p = j.player();
                if (p != null) EemiSoleil.msg(p, "&7Temps écoulé, égalité : pile ou face...");
            }
        } else {
            loser = pr.ma < pr.mb ? pr.a : pr.b;
        }
        lose(pr, loser, "Moins de billes au temps");
    }

    void lose(Pair pr, Joueur loser, String why) {
        pr.step = Step.DONE;
        Joueur winner = pr.other(loser);
        for (Joueur j : new Joueur[]{pr.a, pr.b}) {
            Player p = j.player();
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
        }
        if (winner.alive) {
            Player wp = winner.player();
            if (wp != null) {
                g.title(wp, "&a&lGAGNÉ", "&7Tu as toutes les billes", 2200);
                wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        if (loser.alive) g.eliminate(loser, why, 30);
    }

    @Override
    void timeUp() {
        if (done) return;
        if (!started) {
            readyTick = 100;
            return;
        }
        for (Pair pr : pairs) if (pr.step != Step.DONE) byTime(pr);
        if (!done) finish();
    }

    @Override
    boolean onClick(Player p, Joueur j, PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        if (e.getHand() != EquipmentSlot.HAND) return true;
        Pair pr = byPlayer.get(j.id);
        if (pr == null || pr.step == Step.DONE) return true;
        if (pr.step == Step.HIDE && pr.hider == j) openHide(p, pr);
        else if (pr.step == Step.GUESS && pr.guesser == j) openGuess(p, pr);
        return true;
    }

    @Override
    List<Joueur> ranking() {
        List<Joueur> l = new ArrayList<>(alive());
        l.sort(Comparator.comparingInt(j -> {
            Pair pr = byPlayer.get(j.id);
            return pr == null ? 99 : pr.count(j);
        }));
        return l;
    }

    @Override
    String departage() {
        return "le moins de billes";
    }

    @Override
    void onLeave(Joueur j) {
        Pair pr = byPlayer.get(j.id);
        if (pr == null || pr.step == Step.DONE) return;
        pr.step = Step.DONE;
        Player wp = pr.other(j).player();
        if (wp != null) {
            if (wp.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) wp.closeInventory();
            g.title(wp, "&a&lGAGNÉ", "&7Ton partenaire est parti", 2000);
        }
    }

    @Override
    void cleanup() {
        for (Pair pr : pairs)
            for (Joueur j : new Joueur[]{pr.a, pr.b}) {
                Player p = j.player();
                if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
            }
    }

    @Override
    String barText() {
        int active = 0;
        for (Pair pr : pairs) if (pr.step != Step.DONE) active++;
        return started ? "Billes &8| &f" + active + " paire(s) en cours" : "Billes &8| &ePrêts ?";
    }
}
