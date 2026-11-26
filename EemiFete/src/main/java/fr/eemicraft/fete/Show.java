package fr.eemicraft.fete;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Manèges animés d'un parc : chaque cube d'une pièce est un BlockDisplay posé au pivot ; sa transformation
 * (rotation + translation) est recalculée tous les {@link #STEP} ticks pour l'instant t + STEP et le client
 * interpole entre les deux. Les pièces fixes sont posées une fois. N'existe que si un joueur est près du parc.
 */
final class Show {
    /** Pas de mise à jour (ticks) : 10 = 0,5 s, interpolé par le client (voir CONCEPTION.md, budget réseau). */
    static final int STEP = 10;

    private record Entry(Display e, Ride.Part part, Ride.Cube cube, boolean fixed) {
    }

    final EemiFete pl;
    final int parcel;
    final Set<String> bought;
    private final List<Entry> entries = new ArrayList<>();
    private final boolean north;

    Show(EemiFete pl, int parcel, Set<String> bought) {
        this.pl = pl;
        this.parcel = parcel;
        this.bought = bought;
        this.north = pl.geo.north(parcel);
        boolean pack = pl.packOn();
        double t = pl.serverTick / 20.0;
        for (Ride r : Draw.rides(bought)) {
            for (Ride.Part p : r.parts) {
                Location at = new Location(pl.world, pl.geo.wxd(parcel, p.pu, p.pv), p.py, pl.geo.wzd(parcel, p.pu, p.pv));
                Ride.Pose pose = p.motion.at(t);
                for (Ride.Cube c : p.cubes) {
                    if (!c.shown(pack)) continue;
                    if (c.model() != null) {
                        ItemDisplay e = pl.spawn(at, ItemDisplay.class, d -> {
                            d.setItemStack(modelItem(c.model(), c.tint()));
                            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                            d.setViewRange(1.6f);
                            d.setBrightness(new Display.Brightness(13, 15));
                            d.setInterpolationDuration(0);
                            d.setTransformation(transform(p, c, pose));
                        });
                        if (e != null) entries.add(new Entry(e, p, c, isFixed(p)));
                        continue;
                    }
                    BlockData bd = pl.blockData(c.state());
                    if (bd == null) continue;
                    BlockDisplay e = pl.spawn(at, BlockDisplay.class, d -> {
                        d.setBlock(bd);
                        d.setViewRange(1.6f);
                        d.setBrightness(new Display.Brightness(c.glow() ? 15 : 13, 15));
                        d.setInterpolationDuration(0);
                        d.setTransformation(transform(p, c, pose));
                    });
                    if (e != null) entries.add(new Entry(e, p, c, isFixed(p)));
                }
            }
        }
    }

    int size() {
        return entries.size();
    }

    /** Appelé tous les STEP ticks : vise la pose à t + STEP, interpolée par le client. */
    void tick(long tick) {
        double t = (tick + STEP) / 20.0;
        Ride.Part last = null;
        Ride.Pose pose = null;
        for (Entry en : entries) {
            if (en.fixed || !en.e.isValid()) continue;
            if (en.part != last) {
                last = en.part;
                pose = en.part.motion.at(t);
            }
            en.e.setInterpolationDelay(0);
            en.e.setInterpolationDuration(STEP);
            en.e.setTransformation(transform(en.part, en.cube, pose));
        }
    }

    /** Une pièce fixe rend toujours la pose nulle : inutile de la renvoyer. */
    private static boolean isFixed(Ride.Part p) {
        return p.motion.at(0) == Ride.Pose.ZERO && p.motion.at(7.3) == Ride.Pose.ZERO && p.motion.at(13.1) == Ride.Pose.ZERO;
    }

    void stop() {
        for (Entry en : entries) if (en.e.isValid()) en.e.remove();
        entries.clear();
    }

    // ------------------------------------------------------------------ calcul des transformations

    /** Objet portant le modèle du pack fete:&lt;id&gt; (teinté si demandé). */
    static ItemStack modelItem(String id, int tint) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.setData(DataComponentTypes.ITEM_MODEL, Key.key("fete", id));
        if (tint >= 0) it.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(Color.fromRGB(tint & 0xFFFFFF)));
        return it;
    }

    static Quaternionf quat(Ride.Axis axis, double deg) {
        float a = (float) Math.toRadians(deg);
        return switch (axis) {
            // Ride.rot autour de Y = rotateY(-a) de JOML (sens du yaw de Minecraft)
            case Y -> new Quaternionf().rotateY(-a);
            case U -> new Quaternionf().rotateX(a);
            case V -> new Quaternionf().rotateZ(a);
        };
    }

    Transformation transform(Ride.Part p, Ride.Cube c, Ride.Pose pose) {
        return transform(p, c, pose, north);
    }

    /** Transformation d'un BlockDisplay posé au pivot de la pièce (statique : testée hors serveur). */
    static Transformation transform(Ride.Part p, Ride.Cube c, Ride.Pose pose, boolean north) {
        Quaternionf q = quat(p.axis, pose.angle() + c.angle());
        double[] r = Ride.rot(p.axis, pose.angle(), c.x(), c.y(), c.z());
        // BlockDisplay : le bloc va de 0 à 1 (on recentre) ; ItemDisplay : le modèle est déjà centré, et le client le
        // tourne de 180° autour de Y après la transformation (compensé par la rotation de droite)
        boolean model = c.model() != null;
        Vector3f half = model ? new Vector3f() : q.transform(new Vector3f((float) c.sx() / 2f, (float) c.sy() / 2f, (float) c.sz() / 2f));
        Vector3f tr = new Vector3f((float) (r[0] + pose.dx()) - half.x, (float) (r[1] + pose.dy()) - half.y,
                (float) (r[2] + pose.dz()) - half.z);
        if (north) {
            // parc du côté nord : tourné de 180° autour de Y (jamais un miroir)
            Quaternionf r180 = new Quaternionf().rotateY((float) Math.PI);
            tr = r180.transform(tr);
            q = r180.mul(q, new Quaternionf());
        }
        Quaternionf right = model ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
        return new Transformation(tr, q, new Vector3f((float) c.sx(), (float) c.sy(), (float) c.sz()), right);
    }
}
