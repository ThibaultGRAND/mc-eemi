package fr.eemicraft.fete;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Pose des plans sur le fil principal, par tranches de quelques millisecondes par tick, sans physique.
 * Un etat invalide devient de l'air avec un avertissement (une fois par etat), jamais un plantage.
 */
final class Placer {
    record Job(Plan.Compiled plan, Runnable done, String label) {
    }

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Set<String> invalid = new HashSet<>();
    private final BlockData air = Material.AIR.createBlockData();
    private final Logger log;
    private Job cur;
    private int i;
    private long placed, t0;

    Placer(Logger log) {
        this.log = log;
    }

    void add(Job j) {
        jobs.add(j);
    }

    boolean busy() {
        return cur != null || !jobs.isEmpty();
    }

    int invalidCount() {
        return invalid.size();
    }

    private BlockData data(String s) {
        BlockData d = cache.get(s);
        if (d != null) return d;
        try {
            d = Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            if (invalid.add(s)) log.warning("Bloc invalide ignore : " + s);
            d = air;
        }
        cache.put(s, d);
        return d;
    }

    void tick(World w, long budgetMs) {
        if (w == null) return;
        long end = System.nanoTime() + budgetMs * 1_000_000L;
        while (System.nanoTime() < end) {
            if (cur == null) {
                cur = jobs.poll();
                if (cur == null) return;
                i = 0;
                placed = 0;
                t0 = System.currentTimeMillis();
            }
            Plan.Compiled c = cur.plan();
            int stop = Math.min(c.size(), i + 256);
            for (; i < stop; i++) {
                long p = c.pos()[i];
                int y = Plan.uy(p);
                if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                w.getBlockAt(Plan.ux(p), y, Plan.uz(p)).setBlockData(data(c.states()[i]), false);
                placed++;
            }
            if (i >= c.size()) {
                Job j = cur;
                cur = null;
                if (j.label() != null) log.info(j.label() + " : " + placed + " blocs poses en " + (System.currentTimeMillis() - t0) + " ms.");
                try {
                    if (j.done() != null) j.done().run();
                } catch (RuntimeException ex) {
                    log.warning("Fin de pose : " + ex);
                }
            }
        }
    }
}
