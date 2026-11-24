package fr.eemicraft.buildbattle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Complements EEMICRAFT pour Build Battle (Plugily Projects 5.1.8), par reflexion.
 * 1.0.0 : source reconstruite par decompilation (CFR 0.152) du jar du serveur.
 * 1.1.0 : duo - les coequipiers d'un meme terrain (BaseArena#getPlotList) se voient pendant la construction.
 */
public final class EemiBuildBattle extends JavaPlugin implements Listener {
    private static final String PREFIX = "§e[§6Build Battle§e] §r";
    private NamespacedKey readyKey;
    private org.bukkit.plugin.Plugin bb;
    private final Map<Object, Set<UUID>> ready = new HashMap<>();
    private final Map<UUID, Object> building = new HashMap<>();
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();
    private boolean warned;
    private boolean warnedPlots;

    @Override
    public void onEnable() {
        readyKey = new NamespacedKey(this, "ready");
        saveDefaultConfig();
        bb = Bukkit.getPluginManager().getPlugin("BuildBattle");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 40L, 10L);
    }

    @Override
    public void onDisable() {
        showAll();
    }

    private Object call(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = findMethod(target.getClass(), name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private Object call(Object target, String name) throws ReflectiveOperationException {
        return call(target, name, new Class<?>[0]);
    }

    private static Method findMethod(Class<?> c, String name, Class<?>[] types) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                return k.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException ignored) {
                // classe parente
            }
        }
        return c.getMethod(name, types);
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> arenas() throws ReflectiveOperationException {
        Object registry = call(bb, "getArenaRegistry");
        Object list = call(registry, "getArenas");
        return list instanceof Collection<?> c ? (Collection<Object>) c : List.of();
    }

    private boolean isBuilding(Object arena) throws ReflectiveOperationException {
        Object state = call(arena, "getArenaState");
        if (state == null || !"IN_GAME".equals(((Enum<?>) state).name())) {
            return false;
        }
        Object phase = call(arena, "getArenaInGameState");
        return phase != null && "BUILD_TIME".equals(((Enum<?>) phase).name());
    }

    private List<Player> playersLeft(Object arena) throws ReflectiveOperationException {
        Object l = call(arena, "getPlayersLeft");
        List<Player> out = new ArrayList<>();
        if (l instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Player p) out.add(p);
            }
        }
        return out;
    }

    /** Terrain de chaque joueur (BaseArena#getPlotList : Map<Player, Plot>, rempli par distributePlots). */
    private Map<UUID, Object> plotsOf(Object arena) {
        Map<UUID, Object> out = new HashMap<>();
        try {
            Object m = call(arena, "getPlotList");
            if (m instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() instanceof Player p && e.getValue() != null) {
                        out.put(p.getUniqueId(), e.getValue());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warnedPlots) {
                warnedPlots = true;
                getLogger().warning("Impossible de lire les terrains des joueurs (coequipiers caches) : " + ex);
            }
        }
        return out;
    }

    private static boolean sameTeam(Map<UUID, Object> plots, UUID a, UUID b) {
        Object pa = plots.get(a);
        return pa != null && pa == plots.get(b);
    }

    /**
     * Correctif d'un defaut de Plugily 5.1.8 en equipes (BuildArena#distributePlots, cas TEAM) : les joueurs
     * restants ne sont places que si moins de joueurs/taille terrains sont occupes ; avec des choix faits au
     * sélecteur du lobby, des joueurs peuvent rester SANS terrain (ex. 3 joueurs : A et B sur le terrain 1, C
     * rien). Ici, pendant le vote du theme et la construction, tout joueur en jeu sans terrain est ajoute au
     * terrain incomplet le plus rempli (sinon un terrain vide) puis teleporte au centre.
     */
    private void placeOrphans(Object arena, List<Player> ps) {
        try {
            Object phase = call(arena, "getArenaInGameState");
            if (phase == null) return;
            String ph = ((Enum<?>) phase).name();
            if (!"THEME_VOTING".equals(ph) && !"BUILD_TIME".equals(ph)) return;
            int size = ((Integer) call(arena, "getArenaOption", new Class<?>[]{String.class}, "PLOT_MEMBER_SIZE")).intValue();
            if (size <= 1) return;
            Object pm = call(arena, "getPlotManager");
            Object plotsObj = call(pm, "getPlots");
            if (!(plotsObj instanceof List<?> plots)) return;
            Object plotList = call(arena, "getPlotList");
            for (Player p : ps) {
                if (call(pm, "getPlot", new Class<?>[]{Player.class}, p) != null) continue;
                Object best = null;
                int bestN = -1;
                for (Object plot : plots) {
                    int n = ((Integer) call(plot, "getMembersSize")).intValue();
                    if (n < size && n > bestN) { best = plot; bestN = n; }
                }
                if (best == null) {
                    getLogger().warning("Aucun terrain libre pour " + p.getName() + " dans une arene Build Battle en equipes.");
                    continue;
                }
                Method add = null;
                for (Class<?> k = best.getClass(); k != null && add == null; k = k.getSuperclass()) {
                    for (Method m : k.getDeclaredMethods()) {
                        if (m.getName().equals("addMember") && m.getParameterCount() == 3) { add = m; break; }
                    }
                }
                if (add == null) return;
                add.setAccessible(true);
                if (!Boolean.TRUE.equals(add.invoke(best, p, arena, true))) continue;
                if (plotList instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked") Map<Object, Object> m = (Map<Object, Object>) plotList;
                    m.put(p, best);
                }
                Object cuboid = call(best, "getCuboid");
                if (cuboid != null && call(cuboid, "getCenter") instanceof org.bukkit.Location c) {
                    p.teleport(c);
                    p.setAllowFlight(true);
                    p.setFlying(true);
                }
                p.sendMessage(PREFIX + "§eTu n'avais pas de terrain : tu as ete place sur un terrain"
                        + (bestN > 0 ? " avec un coequipier." : "."));
                getLogger().info("Joueur sans terrain place : " + p.getName() + " (terrain avec " + bestN + " joueur(s)).");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!warnedPlots) {
                warnedPlots = true;
                getLogger().warning("Placement des joueurs sans terrain impossible : " + ex);
            }
        }
    }

    private boolean inGame(Object arena) throws ReflectiveOperationException {
        Object state = call(arena, "getArenaState");
        return state != null && "IN_GAME".equals(((Enum<?>) state).name());
    }

    private void tick() {
        if (bb == null || !bb.isEnabled()) {
            return;
        }
        if (getConfig().getBoolean("fix-orphans", true)) {
            try {
                for (Object arena : arenas()) {
                    if (inGame(arena)) placeOrphans(arena, playersLeft(arena));
                }
            } catch (ClassCastException | ReflectiveOperationException ignored) {
                // signale plus bas
            }
        }
        Map<UUID, Set<UUID>> wanted = new HashMap<>();
        building.clear();
        boolean hide = getConfig().getBoolean("hide-players", true);
        boolean mates = getConfig().getBoolean("show-teammates", true);
        try {
            for (Object arena : arenas()) {
                if (!isBuilding(arena)) {
                    ready.remove(arena);
                    continue;
                }
                List<Player> ps = playersLeft(arena);
                Set<UUID> r = ready.computeIfAbsent(arena, k -> new HashSet<>());
                r.removeIf(id -> ps.stream().noneMatch(p -> p.getUniqueId().equals(id)));
                Map<UUID, Object> plots = mates ? plotsOf(arena) : Map.of();
                for (Player p : ps) {
                    building.put(p.getUniqueId(), arena);
                    giveReadyItem(p, r.contains(p.getUniqueId()));
                    if (!hide) continue;
                    Set<UUID> h = wanted.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
                    for (Player o : ps) {
                        if (o == p) continue;
                        if (mates && sameTeam(plots, p.getUniqueId(), o.getUniqueId())) continue;
                        h.add(o.getUniqueId());
                    }
                }
            }
        } catch (ClassCastException | ReflectiveOperationException ex) {
            if (!warned) {
                warned = true;
                getLogger().warning("Impossible de lire les arenes Build Battle (version differente ?) : " + ex);
            }
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (building.containsKey(p.getUniqueId())) continue;
            removeReadyItem(p);
        }
        applyVisibility(wanted);
    }

    private void applyVisibility(Map<UUID, Set<UUID>> wanted) {
        for (Map.Entry<UUID, Set<UUID>> e : new HashMap<>(hidden).entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            Set<UUID> keep = wanted.getOrDefault(e.getKey(), Set.of());
            for (UUID id : new ArrayList<>(e.getValue())) {
                if (keep.contains(id)) continue;
                Player t = Bukkit.getPlayer(id);
                if (viewer != null && t != null) {
                    viewer.showPlayer(this, t);
                }
                e.getValue().remove(id);
            }
            if (e.getValue().isEmpty()) {
                hidden.remove(e.getKey());
            }
        }
        for (Map.Entry<UUID, Set<UUID>> e : wanted.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer == null) continue;
            Set<UUID> h = hidden.computeIfAbsent(e.getKey(), k -> new HashSet<>());
            for (UUID id : e.getValue()) {
                if (h.contains(id)) continue;
                Player t = Bukkit.getPlayer(id);
                if (t == null) continue;
                viewer.hidePlayer(this, t);
                h.add(id);
            }
        }
    }

    private void showAll() {
        for (Map.Entry<UUID, Set<UUID>> e : hidden.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer == null) continue;
            for (UUID id : e.getValue()) {
                Player t = Bukkit.getPlayer(id);
                if (t != null) viewer.showPlayer(this, t);
            }
        }
        hidden.clear();
    }

    private ItemStack readyItem(boolean isReady) {
        ItemStack it = new ItemStack(isReady ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(isReady
                ? Component.text("Pret ! (clic pour annuler)", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                : Component.text("Je suis pret ! (clic droit)", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Quand tout le monde est pret,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("on passe directement au vote.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(readyKey, PersistentDataType.BYTE, (byte) (isReady ? 1 : 0));
        it.setItemMeta(meta);
        return it;
    }

    private boolean isReadyItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(readyKey, PersistentDataType.BYTE);
    }

    private void giveReadyItem(Player p, boolean isReady) {
        int slot = getConfig().getInt("ready-slot", 7);
        ItemStack cur = p.getInventory().getItem(slot);
        if (isReadyItem(cur) && (cur.getType() == Material.LIME_DYE) == isReady) {
            return;
        }
        removeReadyItem(p);
        p.getInventory().setItem(slot, readyItem(isReady));
    }

    private void removeReadyItem(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            if (isReadyItem(c[i])) p.getInventory().setItem(i, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isReadyItem(e.getItem())) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Object arena = building.get(p.getUniqueId());
        if (arena == null) {
            return;
        }
        Set<UUID> r = ready.computeIfAbsent(arena, k -> new HashSet<>());
        boolean now = !r.remove(p.getUniqueId());
        if (now) {
            r.add(p.getUniqueId());
        }
        giveReadyItem(p, now);
        try {
            List<Player> ps = playersLeft(arena);
            int n = (int) ps.stream().filter(o -> r.contains(o.getUniqueId())).count();
            for (Player o : ps) {
                o.sendMessage(PREFIX + (now ? "§a" + p.getName() + " est pret !" : "§7" + p.getName() + " n'est plus pret.")
                        + " §8(" + n + "/" + ps.size() + ")");
                if (now) o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            }
            int skipTo = getConfig().getInt("ready-timer", 5);
            if (n >= ps.size() && !ps.isEmpty()) {
                int timer = ((Integer) call(arena, "getTimer")).intValue();
                if (timer > skipTo) {
                    call(arena, "setTimer", new Class<?>[]{Integer.TYPE}, skipTo);
                    for (Player o : ps) {
                        o.sendMessage(PREFIX + "§6Tout le monde est pret : vote dans " + skipTo + " secondes !");
                        o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    }
                }
            }
        } catch (ClassCastException | ReflectiveOperationException ex) {
            getLogger().warning("Objet pret : " + ex);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isReadyItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isReadyItem(e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        hidden.remove(id);
        for (Set<UUID> s : hidden.values()) s.remove(id);
        for (Set<UUID> s : ready.values()) s.remove(id);
    }
}
