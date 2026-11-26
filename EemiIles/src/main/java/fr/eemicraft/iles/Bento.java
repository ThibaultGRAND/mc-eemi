package fr.eemicraft.iles;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pont par réflexion vers BentoBox 3.23.4 et l'addon AOneBlock 1.28.0 (aucune dépendance de compilation).
 * Noms vérifiés au javap sur les jars publics :
 * - world.bentobox.bentobox.api.events.island.IslandResettedEvent (extends IslandBaseEvent : getIsland(), getPlayerUUID())
 * - world.bentobox.bentobox.database.objects.Island : getWorld(), getUniqueId(), getMemberSet(), getOwner()
 * - world.bentobox.bentobox.BentoBox : getIslands() -> IslandsManager.getIsland(World, UUID), getAddonsManager().getAddonByName(String)
 * - world.bentobox.aoneblock.events.MagicBlockEvent (extends AbstractMagicBlockEvent : getIsland(), getPlayerUUID())
 * - world.bentobox.aoneblock.AOneBlock.getOneBlocksIsland(Island) -> OneBlockIslands.getLifetime() / getBlockNumber()
 * Les classes de l'addon sont chargées par le chargeur de l'addon (pas celui du plugin) : on passe par l'instance de l'addon.
 */
final class Bento {

    private final EemiIles pl;

    Plugin bentoBox;
    Class<? extends Event> resettedEvent;
    private Method evIsland, evPlayer;
    private Method islWorld, islId, islMembers, islOwner;
    private Method bbIslands, imGetIsland;

    Object aoneblock;
    Class<? extends Event> magicEvent;
    private Method magicIsland, magicPlayer;
    private Method obGetIsland, obiLifetime, obiBlockNumber;

    boolean bento, oneblock;
    String bentoError = "pas encore essayé", oneblockError = "pas encore essayé";

    Bento(EemiIles pl) {
        this.pl = pl;
    }

    @SuppressWarnings("unchecked")
    boolean hookBento() {
        if (bento) return true;
        try {
            Plugin bb = Bukkit.getPluginManager().getPlugin("BentoBox");
            if (bb == null || !bb.isEnabled()) throw new IllegalStateException("BentoBox absent ou desactive");
            ClassLoader cl = bb.getClass().getClassLoader();
            Class<?> base = Class.forName("world.bentobox.bentobox.api.events.IslandBaseEvent", true, cl);
            Class<?> island = Class.forName("world.bentobox.bentobox.database.objects.Island", true, cl);
            Class<?> reset = Class.forName("world.bentobox.bentobox.api.events.island.IslandResettedEvent", true, cl);
            evIsland = base.getMethod("getIsland");
            evPlayer = base.getMethod("getPlayerUUID");
            islWorld = island.getMethod("getWorld");
            islId = island.getMethod("getUniqueId");
            islMembers = island.getMethod("getMemberSet");
            islOwner = island.getMethod("getOwner");
            bbIslands = bb.getClass().getMethod("getIslands");
            imGetIsland = bbIslands.getReturnType().getMethod("getIsland", World.class, UUID.class);
            resettedEvent = (Class<? extends Event>) reset.asSubclass(Event.class);
            bentoBox = bb;
            bento = true;
            bentoError = null;
        } catch (Throwable t) {
            bentoError = t.toString();
        }
        return bento;
    }

    @SuppressWarnings("unchecked")
    boolean hookOneBlock() {
        if (oneblock) return true;
        if (!hookBento()) {
            oneblockError = "BentoBox non branche";
            return false;
        }
        try {
            Object am = bentoBox.getClass().getMethod("getAddonsManager").invoke(bentoBox);
            Object opt = am.getClass().getMethod("getAddonByName", String.class).invoke(am, "AOneBlock");
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) throw new IllegalStateException("addon AOneBlock absent");
            Object addon = o.get();
            ClassLoader cl = addon.getClass().getClassLoader();
            Class<?> island = Class.forName("world.bentobox.bentobox.database.objects.Island", true, bentoBox.getClass().getClassLoader());
            Class<?> abs = Class.forName("world.bentobox.aoneblock.events.AbstractMagicBlockEvent", true, cl);
            Class<?> magic = Class.forName("world.bentobox.aoneblock.events.MagicBlockEvent", true, cl);
            magicIsland = abs.getMethod("getIsland");
            magicPlayer = abs.getMethod("getPlayerUUID");
            obGetIsland = addon.getClass().getMethod("getOneBlocksIsland", island);
            Class<?> obi = obGetIsland.getReturnType();
            obiLifetime = obi.getMethod("getLifetime");
            obiBlockNumber = obi.getMethod("getBlockNumber");
            magicEvent = (Class<? extends Event>) magic.asSubclass(Event.class);
            aoneblock = addon;
            oneblock = true;
            oneblockError = null;
        } catch (Throwable t) {
            oneblockError = t.toString();
        }
        return oneblock;
    }

    // ------------------------------------------------------------------ événements

    Object eventIsland(Event e) {
        return call(magicEvent != null && magicEvent.isInstance(e) ? magicIsland : evIsland, e);
    }

    UUID eventPlayer(Event e) {
        Object o = call(magicEvent != null && magicEvent.isInstance(e) ? magicPlayer : evPlayer, e);
        return o instanceof UUID u ? u : null;
    }

    // ------------------------------------------------------------------ îles

    World islandWorld(Object island) {
        return call(islWorld, island) instanceof World w ? w : null;
    }

    String islandId(Object island) {
        Object o = call(islId, island);
        return o == null ? null : o.toString();
    }

    UUID islandOwner(Object island) {
        return call(islOwner, island) instanceof UUID u ? u : null;
    }

    @SuppressWarnings("unchecked")
    Set<UUID> islandMembers(Object island) {
        Object o = call(islMembers, island);
        if (o instanceof Collection<?> c) {
            Set<UUID> out = new java.util.HashSet<>();
            for (Object x : c) if (x instanceof UUID u) out.add(u);
            return out;
        }
        return Set.of();
    }

    /** Île du joueur dans ce monde, ou null. */
    Object islandOf(World w, UUID player) {
        if (!bento || w == null || player == null) return null;
        try {
            return imGetIsland.invoke(bbIslands.invoke(bentoBox), w, player);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Blocs magiques cassés sur l'île (compteur AOneBlock qui ne redescend pas), ou -1 si inconnu. */
    long oneBlockCount(Object island) {
        if (!oneblock || island == null) return -1;
        try {
            Object obi = obGetIsland.invoke(aoneblock, island);
            if (obi == null) return -1;
            long life = ((Number) obiLifetime.invoke(obi)).longValue();
            long nb = ((Number) obiBlockNumber.invoke(obi)).longValue();
            return Math.max(life, nb);
        } catch (Throwable t) {
            return -1;
        }
    }

    private Object call(Method m, Object target) {
        if (m == null || target == null) return null;
        try {
            return m.invoke(target);
        } catch (Throwable t) {
            pl.getLogger().fine("Reflexion " + m.getName() + " : " + t);
            return null;
        }
    }
}
