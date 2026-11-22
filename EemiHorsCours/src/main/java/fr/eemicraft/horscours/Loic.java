package fr.eemicraft.horscours;

import java.util.List;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Loïc, le commercial : modèle 3D du pack (eemicraft:horscours/loic_*) sur une ItemDisplay.
 * Il ne bouge que quand aucun joueur ne le regarde ; il suit le plus court chemin dans la grille
 * du labyrinthe jusqu'au joueur le plus proche.
 */
final class Loic {
    private final EemiHorsCours plugin;
    private final Maze maze;
    ItemDisplay ent;
    double x, z;
    private int frame, step;
    boolean wasSeen;
    /** Dernier bruit entendu (cadenas coupé, mauvais code...) : il y va en priorité. */
    private int[] noiseCell;
    private long noiseUntil;

    /** Un bruit à (nx, nz), entendu jusqu'à radius blocs. */
    void hear(double nx, double nz, double radius) {
        if (!alive()) return;
        double dx = nx - x, dz = nz - z;
        if (dx * dx + dz * dz > radius * radius) return;
        int[] c = maze.cellAt(nx, nz);
        if (c == null) return;
        noiseCell = c;
        noiseUntil = System.currentTimeMillis() + 20000;
    }

    Loic(EemiHorsCours plugin, Maze maze) {
        this.plugin = plugin;
        this.maze = maze;
    }

    boolean alive() {
        return ent != null && ent.isValid();
    }

    void spawn(int i, int j) {
        remove();
        x = maze.cx(i) + 0.5;
        z = maze.cz(j) + 0.5;
        World w = maze.w;
        ent = w.spawn(new Location(w, x, Maze.Y, z), ItemDisplay.class, d -> {
            d.setItemStack(model("loic_idle"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0f, 0.5f, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setShadowRadius(0.4f);
            d.setShadowStrength(0.8f);
            plugin.tag(d, "loic");
        });
    }

    void remove() {
        if (ent != null && ent.isValid()) ent.remove();
        ent = null;
    }

    private ItemStack model(String id) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("eemicraft", "horscours/" + id));
        it.setItemMeta(m);
        return it;
    }

    /** Un joueur le voit : dans le champ de vision et rien entre eux (tête ou torse). */
    boolean seenBy(Player p) {
        Location eye = p.getEyeLocation();
        double maxD = plugin.getConfig().getDouble("loic.view-distance", 48);
        double cos = plugin.getConfig().getDouble("loic.view-cos", 0.6);
        for (double h : new double[] {1.0, 1.75}) {
            Vector to = new Vector(x - eye.getX(), Maze.Y + h - eye.getY(), z - eye.getZ());
            double d = to.length();
            if (d > maxD) return false;
            if (d < 0.01) return true;
            Vector dir = to.clone().multiply(1.0 / d);
            if (dir.dot(eye.getDirection()) < cos) continue;
            RayTraceResult hit = maze.w.rayTraceBlocks(eye, dir, Math.max(0, d - 0.3), FluidCollisionMode.NEVER, true);
            if (hit == null) return true;
        }
        return false;
    }

    /**
     * Un pas (toutes les 2 ticks). Renvoie le joueur attrapé, ou null.
     * @param targets joueurs dans le labyrinthe
     */
    Player tick(List<Player> targets) {
        if (!alive()) return null;
        boolean seen = false;
        for (Player p : targets) if (seenBy(p)) {
            seen = true;
            break;
        }
        wasSeen = seen;
        if (seen) {
            frame = 0;
            setFrame("loic_idle");
            return null;
        }
        // Cible : joueur le plus proche en distance de chemin
        int[] my = maze.cellAt(x, z);
        if (my == null) return null;
        int[][] dist = maze.bfs(my[0], my[1]);
        Player target = null;
        int best = Integer.MAX_VALUE;
        for (Player p : targets) {
            int[] c = maze.cellAt(p.getX(), p.getZ());
            if (c == null) continue;
            int d = dist[c[0]][c[1]];
            if (d >= 0 && d < best) {
                best = d;
                target = p;
            }
        }
        // Un bruit récent l'attire, sauf si un joueur est déjà tout près
        boolean toNoise = noiseCell != null && System.currentTimeMillis() < noiseUntil && (target == null || best > 1);
        if (toNoise && my[0] == noiseCell[0] && my[1] == noiseCell[1]) {
            noiseCell = null;
            toNoise = false;
        }
        if (target == null && !toNoise) return null;
        double tx, tz;
        int[] tc = toNoise ? noiseCell : maze.cellAt(target.getX(), target.getZ());
        if (toNoise) best = Math.max(1, dist[tc[0]][tc[1]]);
        if (best == 0) {
            tx = target.getX();
            tz = target.getZ();
        } else {
            // Prochaine cellule vers la cible : voisine ouverte dont la distance (depuis la cible) baisse
            int[][] fromT = maze.bfs(tc[0], tc[1]);
            int[] next = null;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dd : dirs)
                if (maze.open(my[0], my[1], dd[0], dd[1])) {
                    int ni = my[0] + dd[0], nj = my[1] + dd[1];
                    if (fromT[ni][nj] >= 0 && fromT[ni][nj] < fromT[my[0]][my[1]]) next = new int[] {ni, nj};
                }
            if (next == null) return null;
            // On passe par le milieu de l'ouverture : d'abord recentrer sur l'axe, puis avancer
            double ccx = maze.cx(my[0]) + 0.5, ccz = maze.cz(my[1]) + 0.5;
            if (next[0] != my[0] && Math.abs(z - ccz) > 0.3) {
                tx = x;
                tz = ccz;
            } else if (next[1] != my[1] && Math.abs(x - ccx) > 0.3) {
                tx = ccx;
                tz = z;
            } else {
                tx = maze.cx(next[0]) + 0.5;
                tz = maze.cz(next[1]) + 0.5;
            }
        }
        double speed = best > 8 ? plugin.getConfig().getDouble("loic.catch-up-speed", 0.8)
                : plugin.getConfig().getDouble("loic.speed", 0.62);
        double dx = tx - x, dz = tz - z, len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) {
            double s = Math.min(speed, len);
            x += dx / len * s;
            z += dz / len * s;
        }
        // Regarde le joueur s'il est dans la même cellule, sinon dans le sens de la marche
        double lx = best == 0 && target != null ? target.getX() - x : dx, lz = best == 0 && target != null ? target.getZ() - z : dz;
        float yaw = (float) Math.toDegrees(Math.atan2(-lx, lz)) + (float) plugin.getConfig().getDouble("loic.yaw-offset", 180);
        ent.teleport(new Location(maze.w, x, Maze.Y, z, yaw, 0));
        if (++step % 3 == 0) {
            frame = (frame + 1) % 2;
            setFrame(frame == 0 ? "loic_walk1" : "loic_walk2");
        }
        if (step % 4 == 0) maze.w.playSound(ent.getLocation(), Sound.BLOCK_STONE_STEP, SoundCategory.HOSTILE, 0.7f, 0.8f);
        if (target == null) return null;
        double hx = target.getX() - x, hz = target.getZ() - z;
        if (hx * hx + hz * hz < 1.0 && Math.abs(target.getY() - Maze.Y) < 1.5) return target;
        return null;
    }

    private String current = "";

    private void setFrame(String id) {
        if (id.equals(current)) return;
        current = id;
        ent.setItemStack(model(id));
    }

    double distTo(Player p) {
        double dx = p.getX() - x, dz = p.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
