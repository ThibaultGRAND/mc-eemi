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
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
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

    /** Place d'un manège : la pièce, le point, l'entité porteuse (ItemDisplay vide) et son passager. */
    static final class Seat {
        final Ride ride;
        final Ride.Part part;
        final double[] at;
        org.bukkit.entity.ItemDisplay carrier;
        Entity rider;

        Seat(Ride ride, Ride.Part part, double[] at) {
            this.ride = ride;
            this.part = part;
            this.at = at;
        }
    }

    final List<Seat> seats = new ArrayList<>();
    /** Interaction d'embarquement -> manège. */
    final java.util.Map<java.util.UUID, Ride> boards = new java.util.HashMap<>();
    private final List<Entity> boardEntities = new ArrayList<>();
    final List<Ride> rides = new ArrayList<>();
    private final boolean north;

    Show(EemiFete pl, int parcel, Set<String> bought) {
        this(pl, parcel, bought, Draw.rides(bought));
    }

    /** Décor fixe hors parc (kiosque de la place) : pivots en coordonnées monde. */
    Show(EemiFete pl, List<Ride> rides) {
        this(pl, -1, Set.of(), rides);
    }

    private Show(EemiFete pl, int parcel, Set<String> bought, List<Ride> rides) {
        this.pl = pl;
        this.parcel = parcel;
        this.bought = bought;
        this.north = parcel >= 0 && pl.geo.north(parcel);
        boolean pack = pl.packOn();
        double t = pl.serverTick / 20.0;
        this.rides.addAll(rides);
        for (Ride r : rides) {
            for (Ride.Part p : r.parts) for (double[] s : p.seats) seats.add(new Seat(r, p, s));
            if (r.board != null && parcel >= 0 && pl.getConfig().getBoolean("rides.enabled", true)) {
                Location b = pl.at(parcel, r.board[0], Geo.FLOOR + 1, r.board[1]);
                Interaction it = pl.spawn(b, Interaction.class, e -> {
                    e.setInteractionWidth(1.6f);
                    e.setInteractionHeight(2.2f);
                    e.setResponsive(true);
                });
                if (it != null) {
                    boards.put(it.getUniqueId(), r);
                    boardEntities.add(it);
                }
                TextDisplay td = pl.text(b.clone().add(0, 2.5, 0), "&e&lMONTER\n&7Clic droit · accroupi pour descendre", 0.6f);
                if (td != null) boardEntities.add(td);
            }
            for (Ride.Part p : r.parts) {
                Location at = parcel < 0 ? new Location(pl.world, p.pu, p.py, p.pv)
                        : new Location(pl.world, pl.geo.wxd(parcel, p.pu, p.pv), p.py, pl.geo.wzd(parcel, p.pu, p.pv));
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

    // ------------------------------------------------------------------ places

    /** Position monde d'une place à l'instant t. */
    Location seatLocation(Seat s, double t) {
        double[] w = Ride.seatAt(s.part, s.at, s.part.motion.at(t));
        double y = w[1] + pl.getConfig().getDouble("rides.seat-y", -0.05);
        return parcel < 0 ? new Location(pl.world, w[0], y, w[2]) : pl.at(parcel, w[0], y, w[2]);
    }

    /** Fait asseoir une entité à une place libre du manège (au hasard), ou false s'il est plein. */
    boolean board(Ride r, Entity who) {
        List<Seat> free = new ArrayList<>();
        for (Seat s : seats) if (s.ride == r && s.rider == null) free.add(s);
        if (free.isEmpty()) return false;
        Seat s = free.get(pl.rnd.nextInt(free.size()));
        Location l = seatLocation(s, pl.serverTick / 20.0);
        s.carrier = pl.spawn(l, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setTeleportDuration(2);
            d.addScoreboardTag("fete_siege");
        });
        if (s.carrier == null) return false;
        who.teleport(l);
        if (!s.carrier.addPassenger(who)) {
            s.carrier.remove();
            s.carrier = null;
            return false;
        }
        s.rider = who;
        return true;
    }

    /** Place occupée par cette entité, ou null. */
    Seat seatOf(Entity who) {
        for (Seat s : seats) if (s.rider != null && s.rider.getUniqueId().equals(who.getUniqueId())) return s;
        return null;
    }

    /** Libère une place : l'entité redescend au point d'embarquement du manège. */
    void unboard(Seat s) {
        Entity who = s.rider;
        s.rider = null;
        if (s.carrier != null) {
            s.carrier.eject();
            s.carrier.remove();
            s.carrier = null;
        }
        if (who != null && who.isValid() && s.ride.board != null && parcel >= 0) {
            Location exit = pl.at(parcel, s.ride.board[0], Geo.FLOOR + 1, s.ride.board[1]);
            exit.setDirection(who.getLocation().getDirection());
            who.teleport(exit);
        }
    }

    /** Tous les 2 ticks : les places occupées suivent le manège (le client interpole sur 2 ticks). */
    void seatTick(long tick) {
        double t = (tick + 2) / 20.0;
        for (Seat s : seats) {
            if (s.rider == null) continue;
            if (s.carrier == null || !s.carrier.isValid() || !s.rider.isValid() || !s.carrier.getPassengers().contains(s.rider)) {
                unboard(s);
                continue;
            }
            s.carrier.teleport(seatLocation(s, t), TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
    }

    void stop() {
        for (Seat s : seats) if (s.rider != null) unboard(s);
        for (Entity e : boardEntities) if (e.isValid()) e.remove();
        boardEntities.clear();
        boards.clear();
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
