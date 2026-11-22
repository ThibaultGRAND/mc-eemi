package fr.eemicraft.core;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Filet de sécurité du hub : retire de l'inventaire d'un joueur AU LOBBY les objets marqués (PersistentDataContainer)
 * par un plugin de jeu ou par l'Abysse. Ils n'ont rien à faire au hub : s'ils y sont, c'est qu'un changement de
 * monde n'a pas échangé l'inventaire (Multiverse-Inventories, voir tickets #50 / #53).
 * <p>
 * Ne touche qu'aux joueurs présents dans le monde du lobby (jamais tntrun / buildbattle, pourtant dans le même groupe
 * Multiverse-Inventories), et seulement aux objets dont une clé PDC appartient à un espace de noms de la liste
 * {@code lobby-guard.namespaces}. Les objets DeluxeHub (custom_data « hotbarItem », pas de PDC), les chapeaux et
 * gadgets d'EemiCore (espace « eemicore ») et les outils du vanish (« eemiadmin ») ne sont jamais dans cette liste.
 * Chaque retrait est écrit dans la console (pour pouvoir rendre un objet à la main).
 */
final class LobbyGuard implements Listener {

    /** Espaces de noms PDC (nom du plugin en minuscules) des objets interdits au hub, si le config n'en donne pas. */
    static final List<String> DEFAULT_NAMESPACES = List.of(
            "eemiabysse", "eemiballsofsteel", "eemibingo", "eemiblockparty", "eemiboatrace", "eemibowspleef",
            "eemibridge", "eemibuildbattle", "eemihideseek", "eemihorscours", "eemihungergames", "eemijumpleague",
            "eemimurder", "eemipitchout", "eemipvprun", "eemisheepwars", "eemiskywars", "eemitnttag", "eemitntwars",
            "eemitonte", "eemitowers", "eemiuhc", "eemiwizards");

    /** Jamais retirés, même s'ils sont ajoutés par erreur à la liste. */
    private static final Set<String> NEVER = Set.of("eemicore", "eemiadmin", "minecraft", "deluxehub");

    private final EemiCore plugin;

    LobbyGuard(EemiCore plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = Math.max(20L, plugin.getConfig().getLong("lobby-guard.interval-ticks", 100L));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) clean(p, "verification");
        }, period, period);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("lobby-guard.enabled", true);
    }

    private String lobbyWorld() {
        return plugin.getConfig().getString("lobby-world", "lobby");
    }

    private Set<String> namespaces() {
        List<String> l = plugin.getConfig().isList("lobby-guard.namespaces")
                ? plugin.getConfig().getStringList("lobby-guard.namespaces") : DEFAULT_NAMESPACES;
        Set<String> s = new HashSet<>();
        for (String n : l) {
            String v = n.toLowerCase(Locale.ROOT).trim();
            if (!v.isEmpty() && !NEVER.contains(v)) s.add(v);
        }
        return s;
    }

    // Arrivée au hub : Multiverse-Inventories (LOW) et DeluxeHub (5 ticks) ont fini, on regarde ce qui reste.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> clean(p, "arrivee au hub depuis " + e.getFrom().getName()), 10L);
    }

    // Connexion au hub (reconnexion dans une strate de l'Abysse déchargée, monde de jeu supprimé...) : profil
    // Multiverse-Inventories appliqué à la connexion, puis contrôle.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> clean(p, "connexion"), 20L);
    }

    /** Retire les objets interdits si le joueur est AU LOBBY à ce moment (re-vérifié ici, tâches différées comprises). */
    int clean(Player p, String why) {
        if (!enabled() || p == null || !p.isOnline()) return 0;
        if (!p.getWorld().getName().equalsIgnoreCase(lobbyWorld())) return 0;
        Set<String> ns = namespaces();
        if (ns.isEmpty()) return 0;
        Map<String, Integer> removed = new LinkedHashMap<>();
        PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents(); // sac + armure + main secondaire
        for (int i = 0; i < c.length; i++) {
            String tag = foreign(c[i], ns);
            if (tag == null) continue;
            count(removed, tag, c[i]);
            inv.setItem(i, null);
        }
        String cur = foreign(p.getItemOnCursor(), ns);
        if (cur != null) {
            count(removed, cur, p.getItemOnCursor());
            p.setItemOnCursor(null);
        }
        InventoryView view = p.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) { // grille 2x2 du sac
            for (int i = 0; i < top.getSize(); i++) {
                String t = foreign(top.getItem(i), ns);
                if (t == null) continue;
                count(removed, t, top.getItem(i));
                top.setItem(i, null);
            }
        }
        if (removed.isEmpty()) return 0;
        p.updateInventory();
        int total = removed.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : removed.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(en.getValue()).append(" x ").append(en.getKey());
        }
        plugin.getLogger().info("[Lobby] " + total + " objet(s) d'un autre mode retire(s) a " + p.getName() + " (" + why + ") : " + sb);
        p.sendMessage(EemiCore.c("&6[EEMICRAFT] &r&7Des objets d'un autre mode étaient restés dans ton inventaire au lobby : ils ont été retirés."));
        return total;
    }

    private static void count(Map<String, Integer> m, String tag, ItemStack it) {
        m.merge(tag + " (" + it.getType().getKey().getKey() + ")", it.getAmount(), Integer::sum);
    }

    /** Première clé PDC de l'objet qui appartient à un espace de noms interdit (« espace:clé=valeur »), sinon null. */
    private static String foreign(ItemStack it, Set<String> ns) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        ItemMeta meta = it.getItemMeta();
        for (NamespacedKey k : meta.getPersistentDataContainer().getKeys()) {
            if (!ns.contains(k.getNamespace())) continue;
            String v = null;
            try {
                v = meta.getPersistentDataContainer().get(k, org.bukkit.persistence.PersistentDataType.STRING);
            } catch (IllegalArgumentException ignored) {
                // valeur d'un autre type (octet, entier...) : on garde seulement le nom de la clé
            }
            return k.getNamespace() + ":" + k.getKey() + (v != null ? "=" + v : "");
        }
        return null;
    }
}
