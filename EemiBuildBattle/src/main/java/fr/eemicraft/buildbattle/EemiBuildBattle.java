package fr.eemicraft.buildbattle;

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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compléments EEMICRAFT pour Build Battle (Plugily Projects 5.x), appelé par réflexion (pas de dépendance
 * de compilation). Toutes les 10 ticks, pour chaque arène :
 * - phase de construction (IArenaState IN_GAME + ArenaInGameState BUILD_TIME) : les joueurs de l'arène ne se
 *   voient plus entre eux, chacun reçoit l'objet « Je suis prêt » ; quand tous sont prêts, le temps restant
 *   est ramené à ready-timer secondes ;
 * - sinon : tout le monde se revoit et les marques « prêt » sont effacées.
 * Noms lus dans le source (master) : Main#getArenaRegistry(), PluginArenaRegistry#getArenas(),
 * PluginArena#getPlayers()/getPlayersLeft()/getTimer()/setTimer(int)/getArenaState(), BaseArena#getArenaInGameState().
 */
public final class EemiBuildBattle extends JavaPlugin implements Listener {

    private static final String PREFIX = "§e[§6Build Battle§e] §r";

    private NamespacedKey readyKey;
    private Plugin bb;
    /** Arène -> joueurs prêts. */
    private final Map<Object, Set<UUID>> ready = new HashMap<>();
    /** Joueur -> arène où il est en construction (pour retrouver son arène au clic). */
    private final Map<UUID, Object> building = new HashMap<>();
    /** Paires cachées par ce plugin : spectateur -> joueurs qu'il ne voit plus. */
    private final Map<UUID, Set<UUID>> hidden = new HashMap<>();
    private boolean warned;

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

    // ------------------------------------------------------------------ réflexion

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
        if (state == null || !"IN_GAME".equals(((Enum<?>) state).name())) return false;
        Object phase = call(arena, "getArenaInGameState");
        return phase != null && "BUILD_TIME".equals(((Enum<?>) phase).name());
    }

    @SuppressWarnings("unchecked")
    private List<Player> playersLeft(Object arena) throws ReflectiveOperationException {
        Object l = call(arena, "getPlayersLeft");
        return l instanceof List<?> list ? new ArrayList<>((List<Player>) list) : new ArrayList<>();
    }

    // ------------------------------------------------------------------ boucle

    private void tick() {
        if (bb == null || !bb.isEnabled()) return;
        Map<UUID, Set<UUID>> wanted = new HashMap<>();
        building.clear();
        try {
            for (Object arena : arenas()) {
                if (!isBuilding(arena)) {
                    ready.remove(arena);
                    continue;
                }
                List<Player> ps = playersLeft(arena);
                Set<UUID> r = ready.computeIfAbsent(arena, k -> new HashSet<>());
                r.removeIf(id -> ps.stream().noneMatch(p -> p.getUniqueId().equals(id)));
                for (Player p : ps) {
                    building.put(p.getUniqueId(), arena);
                    giveReadyItem(p, r.contains(p.getUniqueId()));
                    if (getConfig().getBoolean("hide-players", true)) {
                        Set<UUID> h = wanted.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
                        for (Player o : ps) if (o != p) h.add(o.getUniqueId());
                    }
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            if (!warned) {
                warned = true;
                getLogger().warning("Impossible de lire les arenes Build Battle (version differente ?) : " + ex);
            }
            return;
        }
        // retire l'objet « prêt » à ceux qui ne construisent plus
        for (Player p : Bukkit.getOnlinePlayers()) if (!building.containsKey(p.getUniqueId())) removeReadyItem(p);
        applyVisibility(wanted);
    }

    /** Cache ce qui doit l'être, remontre ce que ce plugin avait caché et qui ne doit plus l'être. */
    private void applyVisibility(Map<UUID, Set<UUID>> wanted) {
        for (Map.Entry<UUID, Set<UUID>> e : new HashMap<>(hidden).entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            Set<UUID> keep = wanted.getOrDefault(e.getKey(), Set.of());
            for (UUID id : new ArrayList<>(e.getValue())) {
                if (keep.contains(id)) continue;
                Player t = Bukkit.getPlayer(id);
                if (viewer != null && t != null) viewer.showPlayer(this, t);
                e.getValue().remove(id);
            }
            if (e.getValue().isEmpty()) hidden.remove(e.getKey());
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

    // ------------------------------------------------------------------ objet « Je suis prêt »

    private ItemStack readyItem(boolean isReady) {
        ItemStack it = new ItemStack(isReady ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(isReady
                ? Component.text("Pret ! (clic pour annuler)", NamedTextColor.GREEN).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                : Component.text("Je suis pret ! (clic droit)", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Quand tout le monde est pret,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
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
        if (isReadyItem(cur) && (cur.getType() == Material.LIME_DYE) == isReady) return;
        // une seule copie : on retire les autres (le joueur a pu la déplacer en créatif)
        removeReadyItem(p);
        p.getInventory().setItem(slot, readyItem(isReady));
    }

    private void removeReadyItem(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) if (isReadyItem(c[i])) p.getInventory().setItem(i, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isReadyItem(e.getItem())) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Object arena = building.get(p.getUniqueId());
        if (arena == null) return;
        Set<UUID> r = ready.computeIfAbsent(arena, k -> new HashSet<>());
        boolean now = !r.remove(p.getUniqueId());
        if (now) r.add(p.getUniqueId());
        giveReadyItem(p, now);
        try {
            List<Player> ps = playersLeft(arena);
            int n = (int) ps.stream().filter(o -> r.contains(o.getUniqueId())).count();
            for (Player o : ps) {
                o.sendMessage(PREFIX + (now ? "§a" + p.getName() + " est pret !" : "§7" + p.getName() + " n'est plus pret.") + " §8(" + n + "/" + ps.size() + ")");
                if (now) o.playSound(o.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
            }
            int skipTo = getConfig().getInt("ready-timer", 5);
            if (n >= ps.size() && ps.size() > 0) {
                int timer = (Integer) call(arena, "getTimer");
                if (timer > skipTo) {
                    call(arena, "setTimer", new Class<?>[]{int.class}, skipTo);
                    for (Player o : ps) {
                        o.sendMessage(PREFIX + "§6Tout le monde est pret : vote dans " + skipTo + " secondes !");
                        o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    }
                }
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
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
