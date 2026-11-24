package fr.eemicraft.casino;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;

/**
 * Roue de la fortune : on mise une somme, la roue s'arrete sur un multiplicateur (x0 = mise perdue).
 * 50 segments : x0 (22), x0,5 (8), x1 (6), x1,5 (6), x2 (4), x3 (2), x5 (1), x10 (1) : retour moyen 96 %.
 * Une seule roue partagee : un lancer a la fois.
 * Repere du modele (face avant = sud, Rz(s) tourne dans le sens anti-horaire vu de face) : le segment j est dessine a
 * j * 7,2 degres dans le sens horaire depuis le haut, donc le segment sous la fleche est celui a s degres.
 * L'ordre des segments doit rester celui de pack_src/gen_casino_pack.py (fortune_sequence).
 */
final class Fortune {
    static final String SEQ = "70421350000213400012003016042135000021340010200301";
    static final int N = SEQ.length();
    static final double[] MULT = {0, 0.5, 1, 1.5, 2, 3, 5, 10};
    static final String[] NAMES = {"&8&lx0", "&6&lx0,5", "&7&lx1", "&9&lx1,5", "&a&lx2", "&5&lx3", "&6&lx5", "&e&lx10"};
    static final Material[] ICONS = {Material.BLACK_CONCRETE, Material.BROWN_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.BLUE_CONCRETE,
            Material.LIME_CONCRETE, Material.PURPLE_CONCRETE, Material.ORANGE_CONCRETE, Material.GOLD_BLOCK};
    static final int[] INFO_SLOTS = {19, 20, 21, 22, 23, 24, 25, 26};
    static final int[] BET_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    static final int SPIN_TICKS = 130;

    static final double X = -8.93;
    static final double Z = 13.5;
    static final float SCALE = 4.5f;

    final class Session {
        final UUID id;
        Gui gui;
        boolean atWheel;
        long bet;

        Session(UUID id) {
            this.id = id;
        }
    }

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();
    final List<Integer> history = new ArrayList<>();
    private ItemDisplay wheel;
    private ItemDisplay arrow;
    /** Angle de la roue (degres). */
    private double angle;
    private BukkitTask task;
    private Session spinning;
    private int result = -1;

    Fortune(EemiCasino pl) {
        this.pl = pl;
    }

    static double cy() {
        return Layout.FLOOR + 4.5;
    }

    static int tier(int segment) {
        return SEQ.charAt(Math.floorMod(segment, N)) - '0';
    }

    static String multText(int t) {
        double m = MULT[t];
        return "x" + (m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m).replace('.', ','));
    }

    private static Quaternionf rot(double s) {
        // face avant tournee vers l'est (les joueurs arrivent par l'est), puis rotation dans le plan de la roue
        return new Quaternionf().rotateY((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(s));
    }

    void spawn() {
        wheel = null;
        arrow = null;
        if (!Models.on(pl) || pl.world == null) {
            return;
        }
        wheel = Models.spawn(pl, new Location(pl.world, X, cy(), Z), "fortune", rot(angle), SCALE);
        arrow = Models.spawn(pl, new Location(pl.world, X + 0.06, cy() + SCALE / 2 + 0.05, Z), "fleche", new Quaternionf().rotateY((float) Math.toRadians(90)), 0.75f);
    }

    int segmentAt(double s) {
        return Math.floorMod((int) Math.round(s / (360.0 / N)), N);
    }

    private ItemStack tierItem(int t, List<String> lore, boolean glint) {
        return Icons.icon(U.item(ICONS[t], NAMES[t], lore, glint), "roue_" + t);
    }

    // ------------------------------------------------------------------ menu

    void open(Player p, boolean atWheel) {
        Session s = sessions.computeIfAbsent(p.getUniqueId(), Session::new);
        s.atWheel = atWheel;
        s.gui = new Gui("fortune", 54, "&8Roue de la fortune");
        render(p, s);
        p.openInventory(s.gui.inv);
    }

    private void drawStrip(Session s) {
        if (s.gui == null) {
            return;
        }
        int top = segmentAt(angle);
        for (int k = 0; k < 9; k++) {
            int seg = Math.floorMod(top - 4 + k, N);
            s.gui.inv.setItem(k, tierItem(tier(seg), List.of(), k == 4 && spinning == null && result >= 0));
        }
    }

    private void render(Player p, Session s) {
        Gui g = s.gui;
        if (g == null) {
            return;
        }
        g.fill(Material.RED_STAINED_GLASS_PANE);
        drawStrip(s);
        g.set(13, Icons.icon(U.item(Material.GOLD_NUGGET, "&e▲ La fleche", List.of(), true), "fleche_haut"), null);
        List<String> hist = new ArrayList<>();
        hist.add("&7Derniers resultats :");
        StringBuilder b = new StringBuilder();
        for (int i = history.size() - 1; i >= Math.max(0, history.size() - 12); i--) {
            b.append(NAMES[history.get(i)]).append(' ');
        }
        hist.add(b.length() == 0 ? "&8aucun" : b.toString());
        g.set(9, U.item(Material.BOOK, "&eHistorique", hist, false), null);
        g.set(17, U.item(Material.KNOWLEDGE_BOOK, "&eRegles", List.of("&7Choisis ta mise et lance la roue.", "&7Elle s'arrete sur un multiplicateur :",
                "&7ta mise est multipliee par sa valeur.", "&8x0 = mise perdue, x1 = mise rendue.", "", "&7Retour moyen : &f96 %"), false), null);
        int[] counts = new int[MULT.length];
        for (int i = 0; i < N; i++) {
            counts[tier(i)]++;
        }
        long bet = pl.currentBet(p, "roue");
        for (int t = 0; t < MULT.length; t++) {
            long gain = Math.round(bet * MULT[t]);
            g.set(INFO_SLOTS[t], tierItem(t, List.of("&7" + counts[t] + " segments sur " + N + String.format(" &8(%.0f %%)", counts[t] * 100.0 / N),
                    t == 0 ? "&cMise perdue" : "&7Avec ta mise : &e" + U.fmt(gain) + " jetons"), false), null);
        }
        pl.betBar(g, p, "roue", BET_SLOTS, () -> render(p, s));
        long chips = pl.data(p).chips;
        g.set(45, U.item(Material.SUNFLOWER, "&6Tes jetons : &e" + U.fmt(chips), List.of(), false), null);
        boolean can = spinning == null && chips >= bet;
        String label = spinning != null ? (spinning == s ? "&7La roue tourne..." : "&7La roue tourne pour un autre joueur") : "&a&lLANCER LA ROUE";
        g.set(49, Icons.icon(U.item(can ? Material.CLOCK : Material.GRAY_DYE, label,
                List.of("&7Mise : &e" + U.fmt(bet) + " jetons", chips >= bet ? "" : "&cPas assez de jetons"), can), can ? "lancer" : "desactive"),
                can ? c -> spin(p, s) : null);
    }

    // ------------------------------------------------------------------ lancer

    void spin(Player p, Session s) {
        if (spinning != null) {
            U.msg(p, "&cLa roue tourne deja, attends la fin du tour.");
            return;
        }
        long bet = Math.min(pl.currentBet(p, "roue"), pl.maxBet("roue"));
        if (!pl.take(p, bet)) {
            U.msg(p, "&cPas assez de jetons (" + U.fmt(bet) + " necessaires).");
            return;
        }
        s.bet = bet;
        spinning = s;
        result = rnd.nextInt(N);
        double seg = 360.0 / N;
        double target = result * seg + (rnd.nextDouble() - 0.5) * seg * 0.7;
        final double s0 = angle;
        final double delta = 4 * 360 + Math.floorMod((long) Math.round((target - s0) * 1000), 360_000L) / 1000.0;
        final Location center = new Location(pl.world, X, cy(), Z);
        render(p, s);
        if (s.atWheel && Models.on(pl) && wheel != null && wheel.isValid()) {
            p.closeInventory();
        }
        pl.broadcastNear(center, 20, "&6" + p.getName() + " &7lance la roue de la fortune (&e" + U.fmt(bet) + " jetons&7) !");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.7f);
        task = new BukkitRunnable() {
            int t;
            int lastSeg = segmentAt(s0);

            @Override
            public void run() {
                try {
                    step();
                } catch (RuntimeException ex) {
                    // jamais de roue bloquee : on regle le tour tout de suite
                    pl.getLogger().warning("Roue de la fortune : " + ex);
                    cancel();
                    task = null;
                    angle = result * (360.0 / N);
                    if (wheel != null && wheel.isValid()) {
                        Models.pose(pl, wheel, rot(angle), SCALE, 10);
                    }
                    finish(p, s);
                }
            }

            private void step() {
                t++;
                double x = t / (double) SPIN_TICKS;
                angle = s0 + delta * (1 - Math.pow(1 - x, 3));
                if (wheel != null && wheel.isValid()) {
                    Models.pose(pl, wheel, rot(angle), SCALE, 1);
                }
                int sg = segmentAt(angle);
                if (sg != lastSeg) {
                    lastSeg = sg;
                    pl.world.playSound(center, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 1.9f);
                }
                if (t % 2 == 0) {
                    for (Session o : sessions.values()) {
                        Player op = Bukkit.getPlayer(o.id);
                        if (op != null && o.gui != null && op.getOpenInventory().getTopInventory().getHolder() == o.gui) {
                            drawStrip(o);
                        }
                    }
                }
                if (t >= SPIN_TICKS) {
                    cancel();
                    task = null;
                    angle = angle % 360;
                    finish(p, s);
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    private void finish(Player p, Session s) {
        if (spinning != s) {
            return;
        }
        spinning = null;
        int t = tier(result);
        history.add(t);
        while (history.size() > 20) {
            history.remove(0);
        }
        long stake = s.bet;
        long gross = Math.round(stake * MULT[t]);
        s.bet = 0;
        if (gross > 0) {
            pl.win(p, gross, stake, "roue de la fortune");
        }
        long net = gross - stake;
        Location center = new Location(pl.world, X + 0.3, cy() + SCALE / 2, Z);
        pl.world.spawnParticle(t == 0 ? Particle.SMOKE : Particle.FIREWORK, center, t >= 5 ? 60 : 15, 0.3, 0.3, 0.6, 0.05);
        if (p.isOnline()) {
            String head = "&7La roue s'arrete sur " + NAMES[t];
            if (net > 0) {
                U.msg(p, head + " &7- &a&lGAGNE &a+" + U.fmt(net) + " jetons");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                if (t >= 5) {
                    pl.celebrate(p, t == 7);
                }
            } else if (net == 0) {
                U.msg(p, head + " &7- &emise rendue");
            } else if (gross > 0) {
                U.msg(p, head + " &7- &erecupere " + U.fmt(gross) + " jetons sur " + U.fmt(stake));
            } else {
                U.msg(p, head + " &7- &cperdu (" + U.fmt(stake) + " jetons)");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            }
            if (pl.inCasino(p) && p.getOpenInventory().getTopInventory().getHolder() != s.gui) {
                p.showTitle(U.title(NAMES[t], net > 0 ? "&a+" + U.fmt(net) + " jetons" : net == 0 ? "&eMise rendue" : gross > 0 ? "&e" + U.fmt(gross) + " jetons" : "&cPerdu"));
                p.sendActionBar(U.c("&7Clic sur la roue pour rejouer"));
            }
        }
        // rafraichir les menus ouverts (bouton Lancer de nouveau disponible)
        for (Session o : new ArrayList<>(sessions.values())) {
            Player op = Bukkit.getPlayer(o.id);
            if (op != null && o.gui != null && op.getOpenInventory().getTopInventory().getHolder() == o.gui) {
                render(op, o);
            }
        }
    }

    /** Le lanceur s'en va : le tour est regle tout de suite et la roue se recale sur le resultat. */
    void quit(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null && spinning == s) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            angle = result * (360.0 / N);
            if (wheel != null && wheel.isValid()) {
                Models.pose(pl, wheel, rot(angle), SCALE, 10);
            }
            finish(p, s);
        }
    }

    void shutdown() {
        if (spinning != null) {
            Player p = Bukkit.getPlayer(spinning.id);
            if (p != null) {
                quit(p);
            } else {
                pl.refund(spinning.id, spinning.bet);
                spinning = null;
            }
        }
        sessions.clear();
    }
}
