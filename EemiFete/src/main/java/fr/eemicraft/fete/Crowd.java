package fr.eemicraft.fete;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;

/**
 * Visiteurs d'un parc : des Mannequin qui se promènent sur les allées (marche aléatoire sur un petit graphe de
 * carrefours), s'arrêtent un moment devant les attractions. Déplacés par téléportation courte tous les 2 ticks
 * (le client lisse le mouvement). N'existent que si un joueur est près du parc.
 */
final class Crowd {
    static final double SPEED = 0.26;     // blocs par pas de 2 ticks (≈ 2,6 blocs/s)

    private static final class Walker {
        Mannequin e;
        int from, to;
        double u, v;
        int pause;
    }

    final EemiFete pl;
    final int parcel;
    private final double[][] nodes;
    private final List<int[]> edges = new ArrayList<>();
    private final List<Walker> walkers = new ArrayList<>();
    private final Random rnd = new Random();

    private final List<org.bukkit.entity.Entity> animals = new ArrayList<>();

    Crowd(EemiFete pl, int parcel, Set<String> b, boolean goat) {
        this.pl = pl;
        this.parcel = parcel;
        // carrefours (u, v) : entrée, bouts de la promenade, allée centrale, bouts des allées en travers
        nodes = new double[][]{{23.5, 2.5}, {3.5, 2.5}, {44.5, 2.5}, {23.5, 10.5}, {23.5, 28.5}, {23.5, 44.5},
                {3.5, 10.5}, {44.5, 10.5}, {3.5, 28.5}, {40.5, 28.5}};
        edges.add(new int[]{0, 1});
        edges.add(new int[]{0, 2});
        edges.add(new int[]{0, 3});
        edges.add(new int[]{3, 4});
        edges.add(new int[]{4, 5});
        if (b.contains("allees")) {
            edges.add(new int[]{3, 6});
            edges.add(new int[]{3, 7});
            edges.add(new int[]{4, 8});
            edges.add(new int[]{4, 9});
        }
        int attractions = 0;
        for (Defs.Attraction a : Defs.ATTRACTIONS) if (b.contains(a.purchase())) attractions++;
        int n = Math.min(pl.getConfig().getInt("visitors.max-per-park", 10), 2 + attractions);
        for (int k = 0; k < n; k++) {
            Walker w = new Walker();
            int[] e = edges.get(rnd.nextInt(edges.size()));
            w.from = e[0];
            w.to = e[1];
            double f = rnd.nextDouble();
            w.u = nodes[w.from][0] + (nodes[w.to][0] - nodes[w.from][0]) * f;
            w.v = nodes[w.from][1] + (nodes[w.to][1] - nodes[w.from][1]) * f;
            w.e = pl.spawn(loc(w.u, w.v, 0), Mannequin.class, m -> {
                m.setAI(false);
                m.setGravity(false);
                m.setInvulnerable(true);
                m.setSilent(true);
                m.setCollidable(false);
                m.setImmovable(true);
                m.setDescription(Component.empty());
                m.setCustomNameVisible(false);
                m.setProfile(pl.visitorProfile());
            });
            if (w.e != null) walkers.add(w);
        }
        if (b.contains("ferme")) farm(goat, b.contains("fantome"));
    }

    /** Animaux de la mini-ferme (sans IA, dans l'enclos) et chèvre naine de Mathilde après une renaissance. */
    private void farm(boolean goat, boolean fantome) {
        int[] f = Defs.FERME;
        double[][] spots = {{f[0] + 1.5, f[1] + 2.5}, {f[0] + 3.5, f[1] + 4.0}, {f[0] + 2.0, f[1] + 6.0}, {f[0] + 3.2, f[1] + 1.6}};
        Class<?>[] kinds = {org.bukkit.entity.Cow.class, org.bukkit.entity.Sheep.class, org.bukkit.entity.Goat.class, org.bukkit.entity.Sheep.class};
        for (int k = 0; k < spots.length; k++) {
            @SuppressWarnings("unchecked") Class<org.bukkit.entity.LivingEntity> cls = (Class<org.bukkit.entity.LivingEntity>) kinds[k];
            org.bukkit.entity.LivingEntity e = pl.spawn(loc(spots[k][0], spots[k][1], rnd.nextFloat() * 360f), cls, m -> {
                m.setAI(false);
                m.setInvulnerable(true);
                m.setSilent(true);
            });
            if (e != null) animals.add(e);
        }
        if (!goat) return;
        // perchée sur le faîte du train fantôme (sinon sur le toit des toilettes)
        Location at = fantome ? new Location(pl.world, pl.geo.wxd(parcel, 41.5, 43.5), Geo.FLOOR + 15, pl.geo.wzd(parcel, 41.5, 43.5))
                : new Location(pl.world, pl.geo.wxd(parcel, 28.5, 15.5), Geo.FLOOR + 4.5, pl.geo.wzd(parcel, 28.5, 15.5));
        org.bukkit.entity.Goat g = pl.spawn(at, org.bukkit.entity.Goat.class, m -> {
            m.setBaby();
            m.setAgeLock(true);
            m.setAI(false);
            m.setInvulnerable(true);
            m.addScoreboardTag("fete_chevre");
            m.addScoreboardTag("fete_p" + parcel);
        });
        if (g != null) animals.add(g);
    }

    Location loc(double u, double v, float yaw) {
        Location l = new Location(pl.world, pl.geo.wxd(parcel, u, v), Geo.FLOOR + 1, pl.geo.wzd(parcel, u, v));
        l.setYaw(yaw);
        return l;
    }

    int size() {
        return walkers.size();
    }

    /** Un pas (tous les 2 ticks). */
    void tick() {
        for (Walker w : walkers) {
            if (w.e == null || !w.e.isValid()) continue;
            if (w.pause > 0) {
                w.pause--;
                continue;
            }
            double tu = nodes[w.to][0], tv = nodes[w.to][1];
            double du = tu - w.u, dv = tv - w.v, d = Math.hypot(du, dv);
            if (d < SPEED) {
                w.u = tu;
                w.v = tv;
                // au carrefour : pause éventuelle, puis une autre direction (pas demi-tour si possible)
                if (rnd.nextInt(3) == 0) w.pause = 10 + rnd.nextInt(40);
                List<Integer> next = new ArrayList<>();
                for (int[] e : edges) {
                    if (e[0] == w.to && e[1] != w.from) next.add(e[1]);
                    else if (e[1] == w.to && e[0] != w.from) next.add(e[0]);
                }
                if (next.isEmpty()) next.add(w.from);
                w.from = w.to;
                w.to = next.get(rnd.nextInt(next.size()));
                continue;
            }
            w.u += du / d * SPEED;
            w.v += dv / d * SPEED;
            // regard dans le sens de la marche (repère local -> monde : le parc nord est tourné de 180°)
            double yaw = Math.toDegrees(Math.atan2(-du, dv)) + (pl.geo.north(parcel) ? 180 : 0);
            w.e.teleport(loc(w.u, w.v, (float) yaw));
        }
    }

    void stop() {
        for (Walker w : walkers) if (w.e != null && w.e.isValid()) w.e.remove();
        walkers.clear();
        for (org.bukkit.entity.Entity e : animals) if (e.isValid()) e.remove();
        animals.clear();
    }
}
