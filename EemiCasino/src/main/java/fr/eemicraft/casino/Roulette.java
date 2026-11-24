package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Roulette europeenne (0 a 36), mises multiples, cylindre anime dans le menu. */
final class Roulette {
    static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    static final Set<Integer> REDS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    static final int[] PICK_BET_SLOTS = {46, 47, 48, 49, 50, 51, 52};

    /** Mises simples : cle -> {nom, icone, multiplicateur (mise rendue comprise)}. */
    static final Map<String, Object[]> OUTSIDE = new LinkedHashMap<>();

    static {
        OUTSIDE.put("red", new Object[]{"&c&lRouge", Material.RED_WOOL, 2, 18});
        OUTSIDE.put("black", new Object[]{"&8&lNoir", Material.BLACK_WOOL, 2, 19});
        OUTSIDE.put("even", new Object[]{"&fPair", Material.QUARTZ_BLOCK, 2, 20});
        OUTSIDE.put("odd", new Object[]{"&7Impair", Material.SMOOTH_STONE, 2, 21});
        OUTSIDE.put("low", new Object[]{"&bManque (1-18)", Material.LIGHT_BLUE_TERRACOTTA, 2, 22});
        OUTSIDE.put("high", new Object[]{"&9Passe (19-36)", Material.BLUE_TERRACOTTA, 2, 23});
        OUTSIDE.put("d1", new Object[]{"&e1re douzaine (1-12)", Material.YELLOW_TERRACOTTA, 3, 24});
        OUTSIDE.put("d2", new Object[]{"&62e douzaine (13-24)", Material.ORANGE_TERRACOTTA, 3, 25});
        OUTSIDE.put("d3", new Object[]{"&c3e douzaine (25-36)", Material.RED_TERRACOTTA, 3, 26});
        OUTSIDE.put("c1", new Object[]{"&aColonne 1 (1, 4, 7...)", Material.LIME_TERRACOTTA, 3, 27});
        OUTSIDE.put("c2", new Object[]{"&aColonne 2 (2, 5, 8...)", Material.GREEN_TERRACOTTA, 3, 28});
        OUTSIDE.put("c3", new Object[]{"&aColonne 3 (3, 6, 9...)", Material.CYAN_TERRACOTTA, 3, 29});
    }

    final class Session {
        final UUID id;
        Gui gui;
        final Map<String, Long> bets = new LinkedHashMap<>();
        Map<String, Long> last = new LinkedHashMap<>();
        boolean spinning;
        int pos = rnd.nextInt(37);
        int result = -1;
        BukkitTask task;
        boolean atTable;

        Session(UUID id) {
            this.id = id;
        }

        long total() {
            long t = 0;
            for (long v : bets.values()) {
                t += v;
            }
            return t;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    final List<Integer> history = new ArrayList<>();

    Roulette(EemiCasino pl) {
        this.pl = pl;
    }

    static String color(int n) {
        return n == 0 ? "&a" : REDS.contains(n) ? "&c" : "&8";
    }

    static String label(int n) {
        return color(n) + "&l" + n + (n == 0 ? "" : REDS.contains(n) ? " rouge" : " noir");
    }

    static Material mat(int n) {
        return n == 0 ? Material.LIME_CONCRETE : REDS.contains(n) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }

    static boolean wins(String key, int n) {
        if (key.startsWith("n")) {
            return Integer.parseInt(key.substring(1)) == n;
        }
        if (n == 0) {
            return false;
        }
        return switch (key) {
            case "red" -> REDS.contains(n);
            case "black" -> !REDS.contains(n);
            case "even" -> n % 2 == 0;
            case "odd" -> n % 2 == 1;
            case "low" -> n <= 18;
            case "high" -> n >= 19;
            case "d1" -> n <= 12;
            case "d2" -> n >= 13 && n <= 24;
            case "d3" -> n >= 25;
            case "c1" -> n % 3 == 1;
            case "c2" -> n % 3 == 2;
            case "c3" -> n % 3 == 0;
            default -> false;
        };
    }

    static int mult(String key) {
        if (key.startsWith("n")) {
            return 36;
        }
        Object[] o = OUTSIDE.get(key);
        return o == null ? 0 : (int) o[2];
    }

    static String betName(String key) {
        if (key.startsWith("n")) {
            int n = Integer.parseInt(key.substring(1));
            return "Plein " + color(n) + n;
        }
        Object[] o = OUTSIDE.get(key);
        return o == null ? key : (String) o[0];
    }

    void open(Player p, boolean atTable) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        s.atTable = atTable;
        s.gui = new Gui("roulette", 54, "&8Roulette");
        render(p, s);
        p.openInventory(s.gui.inv);
        if (atTable) {
            pl.dealerLook(EemiCasino.DEALER_ROULETTE, p);
            pl.dealerSay(EemiCasino.DEALER_ROULETTE, "&eFaites vos jeux !");
        }
    }

    private void drawWheel(Session s) {
        if (s.gui == null) {
            return;
        }
        for (int k = 0; k < 9; k++) {
            int n = WHEEL[Math.floorMod(s.pos - 4 + k, 37)];
            s.gui.inv.setItem(k, Icons.icon(U.item(mat(n), Icons.on ? 1 : Math.max(1, n), label(n), List.of(), k == 4 && !s.spinning && s.result >= 0), "num_" + n));
        }
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        drawWheel(s);
        g.set(13, Icons.icon(U.item(Material.GOLD_NUGGET, "&e▲ La bille", List.of(), true), "fleche_haut"), null);
        List<String> hist = new ArrayList<>();
        hist.add("&7Derniers numeros sortis :");
        StringBuilder b = new StringBuilder();
        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 12); i--) {
            b.append(color(history.get(i))).append(history.get(i)).append(' ');
        }
        hist.add(b.length() == 0 ? "&8aucun" : b.toString());
        g.set(9, U.item(Material.BOOK, "&eHistorique", hist, false), null);
        long chip = pl.currentBet(p, "roulette");
        boolean open = !s.spinning;
        for (Map.Entry<String, Object[]> e : OUTSIDE.entrySet()) {
            String key = e.getKey();
            Object[] o = e.getValue();
            long v = s.bets.getOrDefault(key, 0L);
            g.set((int) o[3], Icons.icon(U.item((Material) o[1], (String) o[0], List.of(
                    "&7Paye &fx" + o[2] + " &8(mise comprise)",
                    v > 0 ? "&eTa mise : " + U.fmt(v) : "&8Pas de mise",
                    "", "&aClic gauche &7: +" + U.fmt(chip), "&cClic droit &7: -" + U.fmt(chip)), v > 0), "rl_" + key),
                    open ? t -> click(p, s, key, t) : null);
        }
        long zero = s.bets.getOrDefault("n0", 0L);
        g.set(31, Icons.icon(U.item(Material.LIME_WOOL, "&a&lZero", List.of("&7Paye &fx36", zero > 0 ? "&eTa mise : " + U.fmt(zero) : "&8Pas de mise",
                "", "&aClic gauche &7: +" + U.fmt(chip), "&cClic droit &7: -" + U.fmt(chip)), zero > 0), "num_0"), open ? t -> click(p, s, "n0", t) : null);
        long plein = 0;
        for (Map.Entry<String, Long> e : s.bets.entrySet()) {
            if (e.getKey().startsWith("n") && !e.getKey().equals("n0")) {
                plein += e.getValue();
            }
        }
        g.set(33, U.item(Material.TARGET, "&d&lNumero plein...", List.of("&7Mise sur un numero de 1 a 36 (x36)", plein > 0 ? "&eTes mises : " + U.fmt(plein) : "&8Pas de mise"), plein > 0),
                open ? t -> openPicker(p, s) : null);
        g.set(35, U.item(Material.SPONGE, "&eEffacer les mises", List.of(), false), open ? t -> {
            s.bets.clear();
            render(p, s);
        } : null);
        pl.betBar(g, p, "roulette", BET_SLOTS, () -> render(p, s));
        PData d = pl.data(p);
        g.set(45, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(d.chips), List.of(), false), null);
        List<String> recap = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.bets.entrySet()) {
            recap.add("&7" + betName(e.getKey()) + " &7: &e" + U.fmt(e.getValue()));
        }
        g.set(47, U.item(Material.GOLD_NUGGET, "&eMise totale : " + U.fmt(s.total()), recap, false), null);
        boolean can = open && s.total() > 0;
        g.set(49, U.item(can ? Material.ENDER_PEARL : Material.GRAY_DYE, s.spinning ? "&7Rien ne va plus..." : "&a&lLANCER LA BILLE",
                List.of(s.total() > 0 ? "&7Mise totale : &e" + U.fmt(s.total()) : "&cPlace d'abord une mise"), can), can ? t -> spin(p, s) : null);
        boolean again = open && !s.last.isEmpty();
        long lastTotal = 0;
        for (long v : s.last.values()) {
            lastTotal += v;
        }
        g.set(51, Icons.icon(U.item(again ? Material.CLOCK : Material.GRAY_DYE, "&eRejouer les memes mises", List.of(again ? "&7Total : &e" + U.fmt(lastTotal) : "&8Aucune partie precedente"), false),
                again ? "rejouer" : "desactive"),
                again ? t -> {
                    s.bets.clear();
                    s.bets.putAll(s.last);
                    spin(p, s);
                } : null);
    }

    private void click(Player p, Session s, String key, ClickType t) {
        if (s.spinning) {
            return;
        }
        long chip = pl.currentBet(p, "roulette");
        long cur = s.bets.getOrDefault(key, 0L);
        if (t.isRightClick()) {
            long nv = Math.max(0, cur - chip);
            if (nv == 0) {
                s.bets.remove(key);
            } else {
                s.bets.put(key, nv);
            }
        } else {
            long max = pl.maxBet("roulette");
            if (s.total() + chip > max) {
                U.msg(p, "&cMise totale maximale : " + U.fmt(max) + " jetons.");
                return;
            }
            if (s.total() + chip > pl.data(p).chips) {
                U.msg(p, "&cPas assez de jetons.");
                return;
            }
            s.bets.put(key, cur + chip);
            p.playSound(p.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.6f, 1.6f);
        }
        if (s.gui != null && s.gui.kind.equals("roulette")) {
            render(p, s);
        } else {
            renderPicker(p, s);
        }
    }

    private void openPicker(Player p, Session s) {
        s.gui = new Gui("roulette-plein", 54, "&8Roulette - numero plein");
        renderPicker(p, s);
        p.openInventory(s.gui.inv);
    }

    private void renderPicker(Player p, Session s) {
        Gui g = s.gui;
        g.fill(Material.GREEN_STAINED_GLASS_PANE);
        long chip = pl.currentBet(p, "roulette");
        for (int n = 0; n <= 36; n++) {
            String key = "n" + n;
            long v = s.bets.getOrDefault(key, 0L);
            g.set(n, Icons.icon(U.item(mat(n), Icons.on ? 1 : Math.max(1, n), label(n), List.of("&7Paye &fx36", v > 0 ? "&eTa mise : " + U.fmt(v) : "&8Pas de mise",
                    "", "&aClic gauche &7: +" + U.fmt(chip), "&cClic droit &7: -" + U.fmt(chip)), v > 0), "num_" + n), t -> click(p, s, key, t));
        }
        pl.betBar(g, p, "roulette", PICK_BET_SLOTS, () -> renderPicker(p, s));
        g.set(45, U.item(Material.SUNFLOWER, "&6Jetons : &e" + U.fmt(pl.data(p).chips) + " &7- mise : &e" + U.fmt(s.total()), List.of(), false), null);
        g.set(53, U.item(Material.ARROW, "&eRetour a la table", List.of(), false), t -> {
            s.gui = new Gui("roulette", 54, "&8Roulette");
            render(p, s);
            p.openInventory(s.gui.inv);
        });
    }

    void spin(Player p, Session s) {
        if (s.spinning || s.bets.isEmpty()) {
            return;
        }
        long total = s.total();
        long max = pl.maxBet("roulette");
        if (total > max) {
            U.msg(p, "&cMise totale maximale : " + U.fmt(max) + " jetons.");
            return;
        }
        if (!pl.take(p, total)) {
            U.msg(p, "&cPas assez de jetons (" + U.fmt(total) + " necessaires).");
            return;
        }
        s.last = new LinkedHashMap<>(s.bets);
        s.spinning = true;
        s.result = rnd.nextInt(37);
        int targetIndex = 0;
        for (int i = 0; i < 37; i++) {
            if (WHEEL[i] == s.result) {
                targetIndex = i;
            }
        }
        int steps = 37 + Math.floorMod(targetIndex - s.pos, 37);
        if (s.atTable) {
            pl.dealerSwing(EemiCasino.DEALER_ROULETTE, p);
            pl.dealerSay(EemiCasino.DEALER_ROULETTE, "&7Rien ne va plus...");
        }
        render(p, s);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.6f);
        if (pl.inCasino(p)) {
            // cylindre 3D : meme duree que l'animation du menu (simulation de la boucle ci-dessous)
            int ticks = 1;
            int w = 0;
            int d = 0;
            while (d < steps) {
                ticks++;
                if (w > 0) {
                    w--;
                    continue;
                }
                d++;
                double f = d / (double) steps;
                w = (int) (f * f * 3);
            }
            boolean shown = pl.rouletteWheel.spin(s.result, ticks);
            if (s.atTable && shown) {
                // on ferme le menu pour regarder la bille sur la vraie roue
                p.closeInventory();
                p.sendActionBar(U.c("&7Rien ne va plus... &8(regarde la roue)"));
            }
        }
        s.task = new BukkitRunnable() {
            int done;
            int wait;

            @Override
            public void run() {
                if (wait > 0) {
                    wait--;
                    return;
                }
                s.pos = Math.floorMod(s.pos + 1, 37);
                done++;
                drawWheel(s);
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.8f);
                }
                double f = done / (double) steps;
                wait = (int) (f * f * 3);
                if (done >= steps) {
                    cancel();
                    s.task = null;
                    finish(p, s);
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    private void finish(Player p, Session s) {
        s.spinning = false;
        int n = s.result;
        s.pos = indexOf(n);
        history.add(n);
        while (history.size() > 20) {
            history.remove(0);
        }
        long stake = 0;
        long gross = 0;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Long> e : s.bets.entrySet()) {
            stake += e.getValue();
            if (wins(e.getKey(), n)) {
                long pay = e.getValue() * mult(e.getKey());
                gross += pay;
                lines.add("&a" + betName(e.getKey()) + " &a+" + U.fmt(pay));
            }
        }
        s.bets.clear();
        if (gross > 0) {
            pl.win(p, gross, stake, "roulette");
        }
        long net = gross - stake;
        String head = "&7La bille s'arrete sur " + label(n);
        if (p.isOnline()) {
            if (net > 0) {
                U.msg(p, head + " &7- &a&lGAGNE &a+" + U.fmt(net) + " jetons");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                if (net >= 2000) {
                    pl.celebrate(p, false);
                }
            } else if (gross > 0) {
                U.msg(p, head + " &7- &erecupere " + U.fmt(gross) + " jetons");
            } else {
                U.msg(p, head + " &7- &cperdu (" + U.fmt(stake) + " jetons)");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            }
            for (String l : lines) {
                p.sendMessage(U.c("  " + l));
            }
            if (s.atTable && Models.on(pl) && p.getOpenInventory().getTopInventory().getHolder() != s.gui) {
                p.showTitle(U.title(label(n), net > 0 ? "&a+" + U.fmt(net) + " jetons" : gross > 0 ? "&eMise recuperee" : "&cPerdu"));
                p.sendActionBar(U.c("&7Clic sur la table pour rejouer"));
            }
        }
        if (s.atTable) {
            pl.dealerSay(EemiCasino.DEALER_ROULETTE, "&eLe " + label(n) + "&e !");
        }
        pl.rouletteBoard();
        if (p.isOnline() && s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
            if (s.gui.kind.equals("roulette")) {
                render(p, s);
            } else {
                renderPicker(p, s);
            }
        }
    }

    static int indexOf(int n) {
        for (int i = 0; i < 37; i++) {
            if (WHEEL[i] == n) {
                return i;
            }
        }
        return 0;
    }

    void quit(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && s.spinning && s.task != null) {
            s.task.cancel();
            s.task = null;
            finish(p, s);
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
