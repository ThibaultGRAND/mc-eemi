package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;

/**
 * Plinko : une bille tombe a travers 8 rangees de picots (gauche ou droite a chaque picot, 1 chance sur 2) et
 * finit dans une des 9 cases du bas. Multiplicateurs par defaut 5,6 / 2,1 / 1,1 / 0,9 / 0,5 (symetriques) : retour 94,6 %.
 * Planche 4 x 5 blocs contre le mur x = 10, face a l'ouest ; coordonnees de planche u (vers la droite vue de face = sud)
 * et v (vers le haut), en blocs depuis le centre ; memes valeurs que pack_src/gen_casino_pack.py.
 */
final class Plinko {
    static final int ROWS = 8;
    static final double SPACING = 0.4;
    static final double ROW_TOP = 1.9;
    static final double ROW_STEP = 0.45;
    static final double CONTACT = 0.11;
    static final double BX = 9.97;
    static final double BZ = 13.5;
    static final float SCALE = 5f;
    static final double[] DEFAULT_MULT = {5.6, 2.1, 1.1, 0.9, 0.5, 0.9, 1.1, 2.1, 5.6};
    static final int[] MULT_SLOTS = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private static final class Ball {
        final UUID id;
        final String name;
        final long bet;
        final int slot;
        final double mult;
        final List<double[]> path;
        final List<Integer> hits;
        ItemDisplay e;
        int t;

        Ball(UUID id, String name, long bet, int slot, double mult, List<double[]> path, List<Integer> hits) {
            this.id = id;
            this.name = name;
            this.bet = bet;
            this.slot = slot;
            this.mult = mult;
            this.path = path;
            this.hits = hits;
        }
    }

    final class Session {
        final UUID id;
        Gui gui;
        final List<String> recent = new ArrayList<>();

        Session(UUID id) {
            this.id = id;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final List<Ball> balls = new ArrayList<>();
    private ItemDisplay board;
    private BukkitTask ticker;

    Plinko(EemiCasino pl) {
        this.pl = pl;
    }

    static double cy() {
        return Layout.FLOOR + 4.0;
    }

    double[] mults() {
        List<Double> l = pl.getConfig().getDoubleList("plinko.multipliers");
        if (l.size() != ROWS + 1) {
            return DEFAULT_MULT;
        }
        double[] m = new double[l.size()];
        for (int i = 0; i < m.length; i++) {
            m[i] = l.get(i);
        }
        return m;
    }

    static String fmtMult(double m) {
        String s = m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m);
        return "x" + s.replace('.', ',');
    }

    void spawn() {
        board = null;
        for (Ball b : balls) {
            b.e = null;
        }
        if (!Models.on(pl) || pl.world == null) {
            return;
        }
        // face avant du modele (sud) tournee vers l'ouest
        board = Models.spawn(pl, new Location(pl.world, BX, cy(), BZ), "plinko", new Quaternionf().rotateY((float) Math.toRadians(-90)), SCALE);
    }

    private Location world(double u, double v) {
        // droite vue de face = sud (+z), devant la planche = ouest (-x)
        return new Location(pl.world, BX - 0.13, cy() + v, BZ + u);
    }

    // ------------------------------------------------------------------ menu

    void open(Player p) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        s.gui = new Gui("plinko", 54, "&8Plinko");
        render(p, s);
        p.openInventory(s.gui.inv);
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        g.fill(Material.PURPLE_STAINED_GLASS_PANE);
        double[] m = mults();
        double rtp = 0;
        for (int j = 0; j <= ROWS; j++) {
            double prob = binom(ROWS, j) / Math.pow(2, ROWS);
            rtp += prob * m[j];
            int dist = Math.abs(j - ROWS / 2);
            Material mat = dist == 0 ? Material.YELLOW_CONCRETE : dist == 1 ? Material.ORANGE_CONCRETE : dist == 2 ? Material.ORANGE_TERRACOTTA : dist == 3 ? Material.RED_CONCRETE : Material.MAGENTA_CONCRETE;
            g.set(MULT_SLOTS[j], Icons.icon(U.item(mat, (m[j] >= 1 ? "&a&l" : "&c&l") + fmtMult(m[j]), List.of(String.format("&7Chance : &f%.2f %%", prob * 100).replace('.', ',')), m[j] >= 5), java.util.Arrays.equals(m, DEFAULT_MULT) ? "pl_" + j : null), null);
        }
        g.set(4, U.item(Material.HEAVY_CORE, "&d&lPLINKO", List.of("&7Lache une bille : elle rebondit sur", "&78 rangees de picots et tombe dans", "&7une case qui multiplie ta mise.",
                String.format("&8Retour moyen : %.1f %%", rtp * 100).replace('.', ','), "", "&eAstuce : &7clic droit sur la planche", "&7pour lacher sans ouvrir ce menu."), false), null);
        List<String> rec = new ArrayList<>();
        rec.add("&7Dernieres billes :");
        rec.addAll(s.recent.isEmpty() ? List.of("&8aucune") : s.recent);
        g.set(9, U.item(Material.BOOK, "&eHistorique", rec, false), null);
        pl.betBar(g, p, "plinko", BET_SLOTS, () -> render(p, s));
        long bet = pl.currentBet(p, "plinko");
        long chips = pl.data(p).chips;
        g.set(45, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(chips), List.of(), false), null);
        boolean ok = chips >= bet;
        g.set(48, U.item(ok ? Material.SNOWBALL : Material.GRAY_DYE, "&f&lLACHER 1 BILLE", List.of("&7Mise : &e" + U.fmt(bet)), false), ok ? c -> drop(p, 1) : null);
        boolean ok5 = chips >= bet * 5;
        g.set(50, U.item(ok5 ? Material.SLIME_BALL : Material.GRAY_DYE, "&a&lLACHER 5 BILLES", List.of("&7Mise : &e5 x " + U.fmt(bet)), false), ok5 ? c -> drop(p, 5) : null);
        g.set(53, U.item(Material.ENDER_EYE, "&eRegarder la planche", List.of("&7Ferme le menu"), false), c -> p.closeInventory());
    }

    static double binom(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
        }
        return r;
    }

    private void refresh(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.gui != null && p.getOpenInventory().getTopInventory().getHolder() == s.gui) {
            render(p, s);
        }
    }

    // ------------------------------------------------------------------ billes

    private int active(UUID id) {
        int n = 0;
        for (Ball b : balls) {
            if (b.id.equals(id)) {
                n++;
            }
        }
        return n;
    }

    /** Lache n billes (une toutes les 6 ticks). Clic droit sur la planche = 1 bille avec la mise choisie. */
    void drop(Player p, int n) {
        int max = pl.getConfig().getInt("plinko.max-balls", 6);
        long bet = pl.currentBet(p, "plinko");
        long maxBet = pl.maxBet("plinko");
        if (bet > maxBet) {
            bet = maxBet;
        }
        for (int i = 0; i < n; i++) {
            if (active(p.getUniqueId()) >= max) {
                U.msg(p, "&7" + max + " billes au plus en meme temps.");
                break;
            }
            if (!pl.take(p, bet)) {
                if (i == 0) {
                    U.msg(p, "&cIl te faut " + U.fmt(bet) + " jetons. Passe a la caisse !");
                }
                break;
            }
            // toutes les billes sont creees tout de suite (jetons pris = bille reglee quoi qu'il arrive),
            // les suivantes attendent leur tour dans l'entonnoir
            launch(p, bet, 6 * i);
        }
        refresh(p);
    }

    private void launch(Player p, long bet, int wait) {
        List<double[]> path = new ArrayList<>();
        List<Integer> hits = new ArrayList<>();
        double u = (rnd.nextDouble() - 0.5) * 0.06;
        double v = 2.3;
        for (int k = 0; k < wait; k++) {
            path.add(new double[]{u, v});
        }
        double cu = 0;
        double cv = ROW_TOP + CONTACT;
        if (path.isEmpty()) {
            path.add(new double[]{u, v});
        }
        segment(path, u, v, cu, cv, 5, 0);
        int rights = 0;
        for (int r = 0; r < ROWS; r++) {
            hits.add(path.size());
            boolean right = rnd.nextBoolean();
            if (right) {
                rights++;
            }
            double nu = cu + (right ? SPACING / 2 : -SPACING / 2);
            double nv = r < ROWS - 1 ? ROW_TOP - ROW_STEP * (r + 1) + CONTACT : -1.75;
            segment(path, cu, cv, nu + (rnd.nextDouble() - 0.5) * 0.02, nv, r < ROWS - 1 ? 5 : 7, 0.1);
            cu = nu;
            cv = nv;
        }
        for (int k = 0; k < 12; k++) {
            path.add(new double[]{cu, cv});
        }
        double mult = mults()[rights];
        Ball ball = new Ball(p.getUniqueId(), p.getName(), bet, rights, mult, path, hits);
        if (board != null && board.isValid()) {
            double[] st = path.get(0);
            ball.e = Models.spawn(pl, world(st[0], st[1]), "bille_or", new Quaternionf(), 0.45f);
            if (ball.e != null) {
                ball.e.setTeleportDuration(1);
            }
        }
        balls.add(ball);
        if (p.isOnline()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.8f);
        }
        if (ticker == null) {
            ticker = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
        }
    }

    private static void segment(List<double[]> path, double u0, double v0, double u1, double v1, int ticks, double hop) {
        for (int k = 1; k <= ticks; k++) {
            double t = k / (double) ticks;
            double u = u0 + (u1 - u0) * t;
            double v = v0 + (v1 - v0) * t * t + hop * 4 * t * (1 - t);
            path.add(new double[]{u, v});
        }
    }

    private void tick() {
        Iterator<Ball> it = balls.iterator();
        Location sound = world(0, 0);
        while (it.hasNext()) {
            Ball b = it.next();
            b.t++;
            if (b.e != null && b.e.isValid() && b.t < b.path.size()) {
                double[] pos = b.path.get(b.t);
                b.e.teleport(world(pos[0], pos[1]));
            }
            if (b.hits.contains(b.t)) {
                pl.world.playSound(sound, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.4f + rnd.nextFloat() * 0.6f);
            }
            if (b.t == b.path.size() - 12) {
                settle(b);
            }
            if (b.t >= b.path.size()) {
                if (b.e != null && b.e.isValid()) {
                    b.e.remove();
                }
                it.remove();
            }
        }
        if (balls.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void settle(Ball b) {
        long gross = (long) Math.floor(b.bet * b.mult + 1e-9);
        Player p = Bukkit.getPlayer(b.id);
        if (p != null) {
            if (gross > 0) {
                pl.win(p, gross, b.bet, "plinko");
            }
            long net = gross - b.bet;
            String col = b.mult >= 1 ? "&a" : "&c";
            p.sendActionBar(U.c("&dPlinko &7: " + col + "&l" + fmtMult(b.mult) + " &7- " + (net >= 0 ? "&a+" + U.fmt(net) : "&c" + U.fmt(net)) + " jetons"));
            if (b.mult >= 5) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                pl.celebrate(p, false);
            } else if (b.mult >= 1) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.8f);
            }
            Session s = sessions.get(b.id);
            if (s != null) {
                s.recent.add(0, col + fmtMult(b.mult) + " &7(" + U.fmt(b.bet) + " -> " + U.fmt(gross) + ")");
                while (s.recent.size() > 8) {
                    s.recent.remove(s.recent.size() - 1);
                }
            }
            refresh(p);
        } else if (gross > 0) {
            pl.deposit(b.id, gross);
        }
        if (b.e != null && b.e.isValid()) {
            pl.world.spawnParticle(b.mult >= 1 ? Particle.HAPPY_VILLAGER : Particle.SMOKE, b.e.getLocation(), b.mult >= 5 ? 20 : 6, 0.1, 0.1, 0.1, 0);
        }
        b.t = Math.max(b.t, b.path.size() - 12);
    }

    /** Le joueur part : ses billes en l'air sont reglees tout de suite. */
    void quit(Player p) {
        sessions.remove(p.getUniqueId());
        Iterator<Ball> it = balls.iterator();
        List<Ball> mine = new ArrayList<>();
        while (it.hasNext()) {
            Ball b = it.next();
            if (b.id.equals(p.getUniqueId())) {
                mine.add(b);
                it.remove();
            }
        }
        for (Ball b : mine) {
            if (b.t < b.path.size() - 12) {
                settle(b);
            }
            if (b.e != null && b.e.isValid()) {
                b.e.remove();
            }
        }
    }

    void shutdown() {
        for (Ball b : new ArrayList<>(balls)) {
            if (b.t < b.path.size() - 12) {
                settle(b);
            }
            if (b.e != null && b.e.isValid()) {
                b.e.remove();
            }
        }
        balls.clear();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        sessions.clear();
    }
}
