package fr.eemicraft.antitnt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pont par réflexion vers BentoBox 3.23.4 (aucune dépendance de compilation, comme EemiIles).
 * - BentoBox.getIslands() -> IslandsManager.getIslandAt(Location) : Optional&lt;Island&gt;, getIsland(World, UUID)
 * - Island : getUniqueId(), getOwner(), getMemberSet(), getCenter(), getProtectionRange(),
 *   getMinProtectedX()/getMaxProtectedX()/getMinProtectedZ()/getMaxProtectedZ() (max exclusif, comme onIsland()).
 * Si les méthodes getMin/MaxProtected* manquent, on retombe sur centre ± rayon de protection.
 */
final class Iles {

    /** Zone protégée d'une île : x dans [minX, maxX[, z dans [minZ, maxZ[. */
    record Ile(Object handle, String id, UUID owner, Set<UUID> members, World world,
               int minX, int maxX, int minZ, int maxZ, int cx, int cz) {
        boolean contains(World w, int x, int z) {
            return w == world && x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        boolean contains(Location l) {
            return l != null && contains(l.getWorld(), l.getBlockX(), l.getBlockZ());
        }
    }

    private final EemiAntiTnt pl;
    private Object bentoBox;
    private Method bbIslands, imAt, imGet;
    private Method iId, iOwner, iMembers, iCenter, iRange, iMinX, iMaxX, iMinZ, iMaxZ;
    boolean ok;
    String erreur = "pas encore essaye";
    /** Vrai si le dernier at() a échoué par erreur (et non parce qu'il n'y a pas d'île) : ne rien retirer alors. */
    boolean derniereErreur;

    Iles(EemiAntiTnt pl) {
        this.pl = pl;
    }

    boolean hook() {
        if (ok) return true;
        try {
            Plugin bb = Bukkit.getPluginManager().getPlugin("BentoBox");
            if (bb == null || !bb.isEnabled()) throw new IllegalStateException("BentoBox absent ou desactive");
            ClassLoader cl = bb.getClass().getClassLoader();
            Class<?> island = Class.forName("world.bentobox.bentobox.database.objects.Island", true, cl);
            bbIslands = bb.getClass().getMethod("getIslands");
            Class<?> im = bbIslands.getReturnType();
            imAt = im.getMethod("getIslandAt", Location.class);
            imGet = im.getMethod("getIsland", World.class, UUID.class);
            iId = island.getMethod("getUniqueId");
            iOwner = island.getMethod("getOwner");
            iMembers = island.getMethod("getMemberSet");
            iCenter = island.getMethod("getCenter");
            iRange = island.getMethod("getProtectionRange");
            iMinX = opt(island, "getMinProtectedX");
            iMaxX = opt(island, "getMaxProtectedX");
            iMinZ = opt(island, "getMinProtectedZ");
            iMaxZ = opt(island, "getMaxProtectedZ");
            bentoBox = bb;
            ok = true;
            erreur = null;
            pl.getLogger().info("Branche sur BentoBox (limites " + (iMinX != null ? "getMin/MaxProtected" : "centre +/- rayon") + ").");
        } catch (Throwable t) {
            erreur = t.toString();
        }
        return ok;
    }

    private static Method opt(Class<?> c, String name) {
        try {
            return c.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** Île dont la zone protégée contient ce point, ou null. */
    Ile at(Location l) {
        derniereErreur = !ok;
        if (!ok || l == null || l.getWorld() == null) return null;
        try {
            Object o = imAt.invoke(bbIslands.invoke(bentoBox), l);
            if (!(o instanceof Optional<?> op) || op.isEmpty()) return null;
            Ile i = wrap(op.get(), l.getWorld());
            return i != null && i.contains(l) ? i : null;
        } catch (Throwable t) {
            derniereErreur = true;
            pl.getLogger().warning("BentoBox getIslandAt : " + t);
            return null;
        }
    }

    /** Île du joueur dans ce monde, ou null. */
    Ile of(World w, UUID player) {
        if (!ok || w == null || player == null) return null;
        try {
            Object o = imGet.invoke(bbIslands.invoke(bentoBox), w, player);
            return o == null ? null : wrap(o, w);
        } catch (Throwable t) {
            return null;
        }
    }

    private Ile wrap(Object is, World w) throws Exception {
        Object c = iCenter.invoke(is);
        if (!(c instanceof Location center)) return null;
        int cx = center.getBlockX(), cz = center.getBlockZ();
        int minX, maxX, minZ, maxZ;
        if (iMinX != null && iMaxX != null && iMinZ != null && iMaxZ != null) {
            minX = ((Number) iMinX.invoke(is)).intValue();
            maxX = ((Number) iMaxX.invoke(is)).intValue();
            minZ = ((Number) iMinZ.invoke(is)).intValue();
            maxZ = ((Number) iMaxZ.invoke(is)).intValue();
        } else {
            int r = ((Number) iRange.invoke(is)).intValue();
            minX = cx - r;
            maxX = cx + r;
            minZ = cz - r;
            maxZ = cz + r;
        }
        Object id = iId.invoke(is);
        Object owner = iOwner.invoke(is);
        Set<UUID> members = new HashSet<>();
        if (iMembers.invoke(is) instanceof Collection<?> col) for (Object x : col) if (x instanceof UUID u) members.add(u);
        if (owner instanceof UUID u) members.add(u);
        World world = center.getWorld() != null ? center.getWorld() : w;
        return new Ile(is, id == null ? "?" : id.toString(), owner instanceof UUID u ? u : null, members, world,
                minX, maxX, minZ, maxZ, cx, cz);
    }
}
