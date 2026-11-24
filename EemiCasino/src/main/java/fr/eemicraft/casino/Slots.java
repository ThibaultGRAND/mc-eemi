package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Machines a sous a 3 rouleaux. Retour joueur ~95 % (hors jackpot), jackpot progressif sur 7-7-7.
 * Probabilites par rouleau : cerise 30, pasteque 24, cloche 18, BAR 14, diamant 9, sept 5 (sur 100).
 */
final class Slots {
    enum Sym {
        CHERRY(Material.SWEET_BERRIES, "&cCerise", "&cCer", 30, "sym_cerise"),
        MELON(Material.MELON_SLICE, "&aPasteque", "&aPas", 24, "sym_pasteque"),
        BELL(Material.BELL, "&eCloche", "&eClo", 18, "sym_cloche"),
        BAR(Material.GOLD_INGOT, "&6&lBAR", "&6BAR", 14, "sym_bar"),
        DIAMOND(Material.DIAMOND, "&b&lDiamant", "&b◆", 9, "sym_diamant"),
        SEVEN(Material.REDSTONE_BLOCK, "&c&l7", "&c&l7", 5, "sym_sept");

        final Material mat;
        final String name;
        final String screen;
        final int weight;
        final String icon;

        Sym(Material mat, String name, String screen, int weight, String icon) {
            this.mat = mat;
            this.name = name;
            this.screen = screen;
            this.weight = weight;
            this.icon = icon;
        }
    }

    static final int[] REEL_COL = {2, 4, 6};
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    final class Session {
        final UUID id;
        Gui gui;
        int machine = -1;
        boolean spinning;
        int autoLeft;
        Sym[] result;
        long bet;
        BukkitTask task;
        final Sym[][] shown = new Sym[3][3];

        Session(UUID id) {
            this.id = id;
            for (int r = 0; r < 3; r++) {
                for (int row = 0; row < 3; row++) {
                    shown[r][row] = random();
                }
            }
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();

    Slots(EemiCasino pl) {
        this.pl = pl;
    }

    Sym random() {
        int total = 0;
        for (Sym s : Sym.values()) {
            total += s.weight;
        }
        int x = rnd.nextInt(total);
        for (Sym s : Sym.values()) {
            x -= s.weight;
            if (x < 0) {
                return s;
            }
        }
        return Sym.CHERRY;
    }

    /** Gain en "demi-mises" (x2 pour gerer le x1,5) ; -1 = jackpot. */
    static int halfMultiplier(Sym[] r) {
        if (r[0] == r[1] && r[1] == r[2]) {
            return switch (r[0]) {
                case SEVEN -> -1;
                case DIAMOND -> 200;
                case BAR -> 80;
                case BELL -> 40;
                case MELON -> 20;
                case CHERRY -> 16;
            };
        }
        int cherries = 0;
        int sevens = 0;
        for (Sym s : r) {
            if (s == Sym.CHERRY) {
                cherries++;
            }
            if (s == Sym.SEVEN) {
                sevens++;
            }
        }
        if (cherries == 2) {
            return 3;
        }
        if (sevens == 2) {
            return 4;
        }
        return 0;
    }

    void open(Player p, int machine) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        if (!s.spinning) {
            if (s.machine >= 0 && s.machine != machine) {
                pl.machines.releaseSlot(s.machine, p.getUniqueId());
            }
            if (machine >= 0 && !pl.machines.claimSlot(machine, p.getUniqueId())) {
                U.msg(p, "&7Cette machine est utilisee par &e" + pl.machines.slotOwnerName(machine) + "&7 : tu joues sans son ecran.");
                machine = -1;
            }
            s.machine = machine;
        }
        Gui g = new Gui("slots", 45, "&8Machine a sous" + (machine >= 0 ? " n°" + (machine + 1) : ""));
        g.onClose = () -> {
            if (!s.spinning && s.autoLeft == 0) {
                pl.machines.releaseSlot(s.machine, s.id);
            }
        };
        s.gui = g;
        render(p, s);
        drawMachine(s);
        p.openInventory(g.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.6f);
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        g.fill(Material.BLACK_STAINED_GLASS_PANE);
        for (int row = 0; row < 3; row++) {
            for (int r = 0; r < 3; r++) {
                g.set(REEL_COL[r] - 1 + 9 * row, U.pane(Material.GRAY_STAINED_GLASS_PANE), null);
            }
        }
        g.set(9, U.item(Material.YELLOW_STAINED_GLASS_PANE, "&e▶ Ligne de gain", List.of(), false), null);
        g.set(17, Icons.icon(U.item(Material.YELLOW_STAINED_GLASS_PANE, "&eLigne de gain ◀", List.of(), false), "ligne_gauche"), null);
        drawReels(s);
        PData d = pl.data(p);
        long bet = pl.currentBet(p, "slots");
        g.set(27, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of("&7Achete des jetons a la caisse."), false), null);
        g.set(29, U.item(Material.BOOK, "&eTable des gains", List.of(
                "&c&l7 7 7 &7: &6&lJACKPOT", "&b◆ ◆ ◆ &7: x100", "&6BAR BAR BAR &7: x40",
                "&eCloche x3 &7: x20", "&aPasteque x3 &7: x10", "&cCerise x3 &7: x8",
                "&c2 cerises &7: x1,5", "&c2 sept &7: x2", "", "&7Mise " + U.fmt(pl.cfgLong("slots.jackpot-full-bet", 1000)) + "+ = jackpot entier,",
                "&7sinon une part du jackpot."), false), null);
        boolean can = !s.spinning && d.chips >= bet;
        g.set(31, U.item(s.spinning ? Material.GRAY_DYE : Material.LEVER, s.spinning ? "&7Les rouleaux tournent..." : "&a&lTIRER LE LEVIER",
                List.of("&7Mise : &e" + U.fmt(bet) + " jetons", can || s.spinning ? "" : "&cPas assez de jetons"), !s.spinning),
                s.spinning ? null : t -> spin(p, s));
        g.set(33, U.item(Material.NETHER_STAR, "&6&lJACKPOT : &e" + U.fmt(pl.jackpot), List.of("&7Aligne trois &c&l7 &7pour le gagner.", "&7Chaque mise l'agrandit."), true), null);
        g.set(35, U.item(s.autoLeft > 0 ? Material.REDSTONE_TORCH : Material.CLOCK, s.autoLeft > 0 ? "&cArreter les tours auto (" + s.autoLeft + ")" : "&eTours automatiques x10",
                List.of("&7Relance 10 fois de suite avec la meme mise."), s.autoLeft > 0), t -> {
                    if (s.autoLeft > 0) {
                        s.autoLeft = 0;
                    } else {
                        s.autoLeft = 10;
                        if (!s.spinning) {
                            spin(p, s);
                            return;
                        }
                    }
                    render(p, s);
                });
        pl.betBar(g, p, "slots", BET_SLOTS, () -> render(p, s));
    }

    private void drawReels(Session s) {
        if (s.gui == null) {
            return;
        }
        for (int r = 0; r < 3; r++) {
            for (int row = 0; row < 3; row++) {
                Sym y = s.shown[r][row];
                s.gui.inv.setItem(REEL_COL[r] + 9 * row, Icons.icon(U.item(y.mat, y.name, List.of(), row == 1 && !s.spinning), y.icon));
            }
        }
    }

    /** Rouleaux 3D de la machine du joueur (machine >= 0). */
    private void drawMachine(Session s) {
        if (s.machine >= 0) {
            pl.machines.showReels(s.machine, s.shown);
        }
    }

    void spin(Player p, Session s) {
        if (s.spinning) {
            return;
        }
        long bet = pl.currentBet(p, "slots");
        if (!pl.take(p, bet)) {
            U.msg(p, "&cIl te faut " + U.fmt(bet) + " jetons. Passe a la caisse !");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            s.autoLeft = 0;
            render(p, s);
            return;
        }
        long contrib = Math.max(1, bet * pl.cfgLong("slots.jackpot-percent", 3) / 100);
        pl.jackpot += contrib;
        s.bet = bet;
        s.spinning = true;
        s.result = new Sym[]{random(), random(), random()};
        render(p, s);
        p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 0.8f);
        if (s.machine >= 0) {
            pl.machines.pull(s.machine);
        }
        final int[] stops = {8, 13, 18};
        s.task = new BukkitRunnable() {
            int frame;

            @Override
            public void run() {
                frame++;
                for (int r = 0; r < 3; r++) {
                    if (frame < stops[r]) {
                        s.shown[r][2] = s.shown[r][1];
                        s.shown[r][1] = s.shown[r][0];
                        s.shown[r][0] = random();
                    } else if (frame == stops[r]) {
                        s.shown[r][1] = s.result[r];
                        s.shown[r][0] = random();
                        s.shown[r][2] = random();
                        if (p.isOnline()) {
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 1f);
                        }
                    }
                }
                if (p.isOnline() && frame < stops[2]) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, 1.5f + rnd.nextFloat() * 0.4f);
                }
                drawReels(s);
                drawMachine(s);
                if (frame >= stops[2]) {
                    cancel();
                    s.task = null;
                    finish(p, s);
                }
            }
        }.runTaskTimer(pl, 2L, 2L);
    }

    /** Regle la partie (aussi a l'arret du plugin). */
    private void finish(Player p, Session s) {
        s.spinning = false;
        Sym[] r = s.result;
        long bet = s.bet;
        int hm = halfMultiplier(r);
        String screen = "&f[ " + r[0].screen + " &f| " + r[1].screen + " &f| " + r[2].screen + " &f]";
        long gross = 0;
        if (hm == -1) {
            long full = Math.max(1, pl.cfgLong("slots.jackpot-full-bet", 1000));
            long amount = bet >= full ? pl.jackpot : pl.jackpot * bet / full;
            pl.jackpot -= amount;
            long seed = pl.cfgLong("slots.jackpot-seed", 10000);
            if (pl.jackpot < seed) {
                pl.jackpot = seed;
            }
            gross = amount + bet * 10;
            pl.data(p).jackpots++;
            pl.broadcast("&6&l★ JACKPOT ! ★ &e" + p.getName() + " &6remporte &e&l" + U.fmt(gross) + " jetons &6a la machine a sous !");
            pl.celebrate(p, true);
        } else if (hm > 0) {
            gross = bet * hm / 2;
        }
        if (gross > 0) {
            pl.win(p, gross, bet, "machine a sous");
            if (hm != -1) {
                if (gross >= bet * 20) {
                    pl.celebrate(p, true);
                    p.showTitle(net.kyori.adventure.title.Title.title(U.c("&6&lGROS GAIN !"), U.c("&e+" + U.fmt(gross) + " jetons")));
                } else {
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                }
                p.sendActionBar(U.c("&a+" + U.fmt(gross) + " jetons &7(" + screen + "&7)"));
            }
        } else {
            boolean near = (r[0] == Sym.SEVEN ? 1 : 0) + (r[1] == Sym.SEVEN ? 1 : 0) + (r[2] == Sym.SEVEN ? 1 : 0) >= 2
                    || (r[0] == r[1] && r[0] != r[2] && (r[0] == Sym.DIAMOND || r[0] == Sym.SEVEN));
            p.sendActionBar(U.c(near ? "&e&lPresque ! &7Retente ta chance..." : "&7Perdu. " + screen));
        }
        pl.slotScreen(s.machine, screen, gross > 0);
        if (p.isOnline() && s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
            render(p, s);
        } else if (s.autoLeft <= 1) {
            pl.machines.releaseSlot(s.machine, s.id);
        }
        if (s.autoLeft > 0) {
            s.autoLeft--;
            if (s.autoLeft > 0 && p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
                pl.later(15, () -> {
                    if (p.isOnline() && !s.spinning && s.autoLeft > 0 && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
                        spin(p, s);
                    }
                });
            } else {
                s.autoLeft = 0;
            }
        }
    }

    void quit(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return;
        }
        s.autoLeft = 0;
        if (s.spinning && s.task != null) {
            s.task.cancel();
            s.task = null;
            finish(p, s);
        }
        pl.machines.releaseSlot(s.machine, s.id);
        sessions.remove(p.getUniqueId());
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

    static void sparkle(Player p) {
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.3);
    }
}
