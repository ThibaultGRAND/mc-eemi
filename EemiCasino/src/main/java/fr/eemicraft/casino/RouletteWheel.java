package fr.eemicraft.casino;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Cylindre de roulette en 3D sur la table du centre (modele eemicraft:casino/roulette, disque de 3 blocs).
 * Repere : angle compte en degres dans le sens horaire vu du dessus, 0 = nord (-z). Sur la texture, la case i de
 * {@link Roulette#WHEEL} est a i * 360 / 37 ; le disque tourne de "a" degres dans le sens anti-horaire (Ry(a)),
 * donc la case i est au monde a (i * 360 / 37 - a).
 * La bille tourne en sens inverse, ralentit, descend vers les cases puis est entrainee par le cylindre.
 * Chaque lancer (de n'importe quel joueur) relance l'animation vers son numero : c'est un decor, le resultat
 * et les gains viennent toujours de {@link Roulette}.
 */
final class RouletteWheel {
    static final double CX = -1.5;
    static final double CZ = 0.5;
    static final float SCALE = 3f;
    /** Rayons en blocs : piste de la bille, cases. */
    static final double R_TRACK = 226 / 256.0 * SCALE / 2;
    static final double R_POCKET = 163 / 256.0 * SCALE / 2;

    private final EemiCasino pl;
    private final Random rnd = new Random();
    private ItemDisplay wheel;
    private ItemDisplay ball;
    private double angle;
    private int restIndex;
    private BukkitTask task;

    RouletteWheel(EemiCasino pl) {
        this.pl = pl;
    }

    private double y() {
        return Layout.FLOOR + 2.03;
    }

    void spawn() {
        stop();
        wheel = null;
        ball = null;
        if (!Models.on(pl) || pl.world == null) {
            return;
        }
        wheel = Models.spawn(pl, new Location(pl.world, CX, y(), CZ), "roulette", Models.rotY(angle), SCALE);
        double t = pocketWorld(restIndex, angle);
        ball = Models.spawn(pl, ballLoc(t, R_POCKET, 0), "bille", Models.rotY(0), 0.32f);
        if (ball != null) {
            ball.setTeleportDuration(1);
        }
    }

    private static double pocketWorld(int index, double a) {
        return index * 360.0 / 37 - a;
    }

    private Location ballLoc(double theta, double r, double hop) {
        double t = Math.toRadians(theta);
        return new Location(pl.world, CX + r * Math.sin(t), y() + 0.075 + hop, CZ - r * Math.cos(t));
    }

    private static double easeOut(double x, double p) {
        return 1 - Math.pow(1 - Math.max(0, Math.min(1, x)), p);
    }

    /**
     * Lance l'animation : le numero tombe a la fin des "ticks" (meme duree que le cylindre du menu).
     * Refuse (false) si le cylindre tourne deja pour un autre joueur : on n'interrompt jamais un lancer en cours,
     * sinon sa bille finirait sur le numero d'un autre.
     */
    boolean spin(int number, int ticks) {
        if (task != null || wheel == null || !wheel.isValid() || ball == null || !ball.isValid()) {
            return false;
        }
        final int index = Roulette.indexOf(number);
        final int total = Math.max(40, ticks);
        final double a0 = angle;
        final double turnWheel = 360 + rnd.nextInt(180);
        // decalage de la bille par rapport a sa case : part a -bigPhi et revient a 0 ; au plus ~35 degres par tick
        // (au-dela, l'interpolation en ligne droite ferait couper la bille a travers le cylindre)
        final double bigPhi = Math.min(2200, 12.0 * total);
        final double pocketJitter = (rnd.nextDouble() - 0.5) * 4;
        final Location center = new Location(pl.world, CX, y(), CZ);
        task = new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (wheel == null || !wheel.isValid() || ball == null || !ball.isValid()) {
                    cancel();
                    task = null;
                    return;
                }
                t++;
                double x = t / (double) total;
                angle = a0 + turnWheel * easeOut(x, 3);
                double phi = -bigPhi * (1 - easeOut(x / 0.82, 2.4));
                double theta = pocketWorld(index, angle) + pocketJitter * Math.min(1, x / 0.82) + phi;
                double r;
                double hop = 0;
                if (x < 0.5) {
                    r = R_TRACK;
                } else if (x < 0.8) {
                    double k = (x - 0.5) / 0.3;
                    double smooth = k * k * (3 - 2 * k);
                    r = R_TRACK + (R_POCKET - R_TRACK) * smooth;
                    double bounce = Math.abs(Math.sin(k * Math.PI * 5)) * (1 - k);
                    r += 0.05 * bounce;
                    hop = 0.05 * bounce;
                    if (t % 3 == 0) {
                        pl.world.playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.6f + rnd.nextFloat() * 0.4f);
                    }
                } else {
                    r = R_POCKET;
                }
                Models.pose(pl, wheel, Models.rotY(angle), SCALE, 1);
                ball.teleport(ballLoc(theta, r, hop));
                if (t >= total) {
                    angle = angle % 360;
                    restIndex = index;
                    pl.world.playSound(center, Sound.BLOCK_WOOD_PLACE, 0.6f, 1.4f);
                    cancel();
                    task = null;
                }
            }
        }.runTaskTimer(pl, 1L, 1L);
        return true;
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
