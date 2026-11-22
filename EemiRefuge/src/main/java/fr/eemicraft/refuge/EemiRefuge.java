package fr.eemicraft.refuge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Dernier Refuge (EemiRefuge) : salle d'essai admin (0.1) ; étape 1 (0.2) : Refuge (module refuge) et expéditions (module expe).
 * Tout ce que fait le plugin se passe dans le monde refuge_essai, sauf l'envoi du pack de textures du mode
 * à la connexion (pack séparé, empilé sur celui du serveur).
 */
public final class EemiRefuge extends JavaPlugin implements Listener {

    public static final String WORLD = "refuge_essai";
    public static final String PERM = "eemirefuge.admin";
    /** Salle d'essai seule (sans give, reload, expe, zone). */
    public static final String PERM_ESSAI = "eemirefuge.essai";

    private static boolean canEssai(CommandSender s) {
        return s.hasPermission(PERM) || s.hasPermission(PERM_ESSAI);
    }
    /** UUID fixe du pack du mode (pour qu'un nouvel envoi remplace l'ancien et ne s'empile pas deux fois). */
    public static final UUID PACK_UUID = UUID.nameUUIDFromBytes("eemicraft:refuge-pack".getBytes(StandardCharsets.UTF_8));

    private World world;
    private Zombies zombies;
    private Bruit bruit;
    private Armes armes;
    private Salle salle;
    // Étape 1 (contrat ETAPE1_CONTRAT.md) : module Refuge/interfaces et module Expéditions.
    private fr.eemicraft.refuge.api.RefugeApi refuge;
    private fr.eemicraft.refuge.api.ExpeditionApi expe;
    private boolean infiniteAmmo;
    private final Map<UUID, Location> returnLoc = new HashMap<>();
    private final Map<UUID, GameMode> returnMode = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Catalogue.init(this);
        zombies = new Zombies(this);
        bruit = new Bruit(this);
        armes = new Armes(this);
        salle = new Salle(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(zombies, this);
        Bukkit.getPluginManager().registerEvents(armes, this);
        Bukkit.getPluginManager().registerEvents(salle, this);
        refuge = new fr.eemicraft.refuge.refuge.Refuge(this);
        expe = new fr.eemicraft.refuge.expe.Expeditions(this);
        try { refuge.enable(); } catch (RuntimeException ex) { getLogger().severe("Module Refuge : " + ex); ex.printStackTrace(); }
        try { expe.enable(); } catch (RuntimeException ex) { getLogger().severe("Module Expeditions : " + ex); ex.printStackTrace(); }

        world = Bukkit.getWorld(WORLD);
        if (world == null) {
            world = new WorldCreator(WORLD).generator(new VoidGenerator()).createWorld();
            if (world != null) getLogger().info("Monde " + WORLD + " cree (vide).");
        }
        if (world == null) {
            getLogger().severe("Impossible de creer le monde " + WORLD + " : salle d'essai desactivee.");
            return;
        }
        rules(world);
        Integer v = world.getPersistentDataContainer().get(salle.keyVersion, PersistentDataType.INTEGER);
        if (v == null || v != Salle.VERSION) {
            getLogger().info("Construction de la salle d'essai (version " + Salle.VERSION + ", trouvee " + v + ").");
            salle.layout(world, true);
        } else {
            salle.layout(world, false);
            infiniteAmmo = salle.infiniteFromLever();
        }
        world.setSpawnLocation(salle.spawn);
        // Restes de zombies d'une session précédente (non persistants en principe).
        zombies.killAll();

        Bukkit.getScheduler().runTaskTimer(this, bruit::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, zombies::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            armes.bleedTick();
            for (Player p : world.getPlayers()) guard(p);
        }, 20L, 20L);
        getLogger().info("Salle d'essai prete : " + Catalogue.DEFS.size() + " objets au catalogue, "
                + Zombies.Type.values().length + " types de zombies.");
    }

    @Override
    public void onDisable() {
        try { if (expe != null) expe.disable(); } catch (RuntimeException ex) { getLogger().severe("Module Expeditions : " + ex); }
        try { if (refuge != null) refuge.disable(); } catch (RuntimeException ex) { getLogger().severe("Module Refuge : " + ex); }
        if (zombies != null) zombies.killAll();
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, 65, 0.5);
        }
    }

    private void rules(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRules.TNT_EXPLOSION_DROP_DECAY, false);
        w.setTime(getConfig().getLong("essai.time", 6000));
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(org.bukkit.Difficulty.NORMAL);
    }

    // ------------------------------------------------------------------ accès

    public World essaiWorld() {
        return world;
    }

    public boolean inEssai(World w) {
        return w != null && w.getName().equals(WORLD);
    }

    public List<Player> essaiPlayers() {
        return world == null ? List.of() : world.getPlayers();
    }

    /** Monde où armes, bruit et zombies sont actifs : salle d'essai ou expédition en cours. */
    public boolean inCombat(World w) {
        return inEssai(w) || (expe instanceof fr.eemicraft.refuge.expe.Expeditions x && x.isZoneWorld(w));
    }

    /** Joueurs de la salle d'essai et des expéditions en cours. */
    public List<Player> combatPlayers() {
        List<Player> out = new ArrayList<>(essaiPlayers());
        if (expe instanceof fr.eemicraft.refuge.expe.Expeditions x) out.addAll(x.zonePlayers());
        return out;
    }

    /** Fichier du jar (copie des zones fournies). */
    public java.io.File jarFile() {
        return getFile();
    }

    public Zombies zombies() { return zombies; }
    public Bruit bruit() { return bruit; }
    public Armes armes() { return armes; }
    public Salle salle() { return salle; }
    public fr.eemicraft.refuge.api.RefugeApi refuge() { return refuge; }
    public fr.eemicraft.refuge.api.ExpeditionApi expe() { return expe; }

    public boolean infiniteAmmo() {
        return infiniteAmmo;
    }

    public void setInfiniteAmmo(boolean b) {
        infiniteAmmo = b;
    }

    // ------------------------------------------------------------------ garde du monde

    private Location lobbySpawn() {
        World lobby = Bukkit.getWorld("lobby");
        if (lobby == null) lobby = Bukkit.getWorlds().get(0);
        return lobby.getSpawnLocation();
    }

    /** Un joueur sans la permission n'a rien à faire dans la salle : renvoyé au spawn du lobby. */
    private void guard(Player p) {
        if (!inEssai(p.getWorld()) || canEssai(p)) return;
        p.teleport(lobbySpawn());
        Txt.msg(p, "La salle d'essai est réservée aux admins.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() != null && inEssai(e.getTo().getWorld()) && !inEssai(e.getFrom().getWorld())
                && !canEssai(e.getPlayer())) {
            e.setCancelled(true);
            Txt.msg(e.getPlayer(), "La salle d'essai est réservée aux admins.");
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (inEssai(p.getWorld())) Bukkit.getScheduler().runTask(this, () -> guard(p));
        if (inEssai(e.getFrom()) || (expe instanceof fr.eemicraft.refuge.expe.Expeditions x && x.isAnyZoneWorld(e.getFrom()))) {
            armes.stopBleed(p);
            bruit.forget(p);
        }
        updatePack(p, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) guard(p); }, 2L);
        packed.remove(p.getUniqueId());
        updatePack(p, Math.max(1, getConfig().getLong("pack.delay-ticks", 40)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        packed.remove(e.getPlayer().getUniqueId());
        bruit.forget(e.getPlayer());
        armes.stopBleed(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (inEssai(e.getPlayer().getWorld()) && salle.spawn != null) e.setRespawnLocation(salle.spawn);
    }

    // ------------------------------------------------------------------ pack du mode

    /** Joueurs qui ont le pack du mode chargé (envoyé par ce plugin). */
    private final java.util.Set<java.util.UUID> packed = new java.util.HashSet<>();

    /**
     * 0.1.2 (Thibault : « je voulais un pack qu'on peut activer / désactiver, pour retexturer les blocs sans
     * impacter le reste du serveur »). pack.mode = monde : le pack n'est chargé QUE dans les mondes du mode
     * (pack.mondes, préfixes de nom) et retiré dès qu'on en sort ; il peut donc retexturer des blocs vanilla.
     * pack.mode = connexion : ancien comportement (chargé à la connexion, jamais retiré).
     */
    boolean wantsPack(Player p) {
        if (!getConfig().getBoolean("pack.enabled", false)) return false;
        if ("connexion".equalsIgnoreCase(getConfig().getString("pack.mode", "monde"))) return true;
        String w = p.getWorld().getName().toLowerCase(Locale.ROOT);
        for (String pre : getConfig().getStringList("pack.mondes").isEmpty()
                ? List.of("refuge") : getConfig().getStringList("pack.mondes"))
            if (w.startsWith(pre.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    void updatePack(Player p, long delay) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            boolean want = wantsPack(p);
            boolean has = packed.contains(p.getUniqueId());
            if (want && !has) {
                String url = getConfig().getString("pack.url", "");
                String sha1 = getConfig().getString("pack.sha1", "").trim().toLowerCase(Locale.ROOT);
                if (url.isBlank() || !sha1.matches("[0-9a-f]{40}")) {
                    getLogger().warning("pack.url ou pack.sha1 invalide dans le config : pack du mode non envoye.");
                    return;
                }
                byte[] hash = new byte[20];
                for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha1.substring(2 * i, 2 * i + 2), 16);
                p.addResourcePack(PACK_UUID, url, hash, null, false);
                packed.add(p.getUniqueId());
            } else if (!want && has) {
                p.removeResourcePack(PACK_UUID);
                packed.remove(p.getUniqueId());
            }
        }, delay);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (refuge != null && refuge.handleCommand(s, a)) return true;
        if (expe != null && expe.handleCommand(s, a)) return true;
        if (a.length > 0 && a[0].equalsIgnoreCase("essai") && canEssai(s)) {
            essai(s, a);
            return true;
        }
        if (!s.hasPermission(PERM)) {
            Txt.msg(s, "Réservé aux admins pour l'instant.");
            return true;
        }
        if (a.length == 0) {
            Txt.msg(s, "/refuge essai [quitter|armes|build] §8| §7/refuge give <joueur> <objet>[+niveau] [n] §8| §7/refuge reload");
            Txt.msg(s, "/refuge expe liste|stop|start|reload §8| §7/refuge zone liste|verifier <id>|modele <id>");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "essai" -> essai(s, a);
            case "give" -> give(s, a);
            case "reload" -> {
                reloadConfig();
                if (world != null) world.setTime(getConfig().getLong("essai.time", 6000));
                Txt.msg(s, "Configuration rechargée. Pack du mode remis à jour pour les joueurs connectés.");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (packed.remove(p.getUniqueId())) p.removeResourcePack(PACK_UUID);
                    updatePack(p, 1L);
                }
            }
            default -> Txt.msg(s, "Sous-commande inconnue.");
        }
        return true;
    }

    private void essai(CommandSender s, String[] a) {
        if (world == null) {
            Txt.msg(s, "§cLe monde " + WORLD + " n'a pas pu être créé.");
            return;
        }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "build" -> {
                if (!s.hasPermission(PERM)) {
                    Txt.msg(s, "Reconstruire la salle est réservé aux admins.");
                    return;
                }
                salle.layout(world, true);
                infiniteAmmo = false;
                Txt.msg(s, "Salle d'essai reconstruite.");
            }
            case "quitter" -> {
                if (!(s instanceof Player p)) return;
                if (!inEssai(p.getWorld())) {
                    Txt.msg(p, "Tu n'es pas dans la salle d'essai.");
                    return;
                }
                Location back = returnLoc.remove(p.getUniqueId());
                GameMode gm = returnMode.remove(p.getUniqueId());
                if (back == null || back.getWorld() == null || inEssai(back.getWorld())) back = lobbySpawn();
                p.teleport(back);
                if (gm != null) p.setGameMode(gm);
                Txt.msg(p, "Tu as quitté la salle d'essai.");
            }
            case "armes" -> {
                if (!(s instanceof Player p)) return;
                if (!inEssai(p.getWorld())) {
                    Txt.msg(p, "Le menu des armes ne s'ouvre que dans la salle d'essai.");
                    return;
                }
                openArmsMenu(p, 0);
            }
            case "" -> {
                if (!(s instanceof Player p)) {
                    Txt.msg(s, "Commande réservée aux joueurs.");
                    return;
                }
                if (!inEssai(p.getWorld())) {
                    returnLoc.put(p.getUniqueId(), p.getLocation());
                    returnMode.put(p.getUniqueId(), p.getGameMode());
                }
                p.teleport(salle.spawn);
                if (getConfig().getBoolean("essai.survival-on-enter", true)) p.setGameMode(GameMode.SURVIVAL);
                Txt.msg(p, "Bienvenue dans la salle d'essai. Tu es en survie pour que les zombies t'attaquent.");
                Txt.msg(p, "Armes à feu : clic droit pour tirer, §fF§7 pour recharger. Sortie : /refuge essai quitter");
            }
            default -> Txt.msg(s, "/refuge essai [quitter|armes|build]");
        }
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) {
            Txt.msg(s, "/refuge give <joueur> <objet>[+niveau] [n]");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            Txt.msg(s, "Joueur introuvable.");
            return;
        }
        String id = a[2].toLowerCase(Locale.ROOT);
        int level = 0;
        int plus = id.indexOf('+');
        if (plus > 0) {
            try {
                level = Integer.parseInt(id.substring(plus + 1));
            } catch (NumberFormatException ex) {
                Txt.msg(s, "Niveau invalide.");
                return;
            }
            id = id.substring(0, plus);
        }
        if (Catalogue.def(id) == null) {
            Txt.msg(s, "Objet inconnu : " + id + ". Objets : " + String.join(", ", Catalogue.DEFS.keySet()));
            return;
        }
        int n = 1;
        if (a.length > 3) {
            try {
                n = Math.max(1, Math.min(640, Integer.parseInt(a[3])));
            } catch (NumberFormatException ex) {
                Txt.msg(s, "Nombre invalide.");
                return;
            }
        }
        int stack = Math.max(1, Catalogue.def(id).stack);
        int left = n;
        while (left > 0) {
            int k = Math.min(stack, left);
            Salle.give(t, Catalogue.create(id, level, List.of(), k));
            left -= k;
        }
        Txt.msg(s, n + " × " + Catalogue.def(id).name + (level > 0 ? " +" + level : "") + " donné(s) à " + t.getName() + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission(PERM)) return out;
        if (a.length == 1) out.addAll(List.of("essai", "give", "reload", "expe", "zone"));
        else if ((a[0].equalsIgnoreCase("expe") || a[0].equalsIgnoreCase("zone")) && expe instanceof fr.eemicraft.refuge.expe.Expeditions x)
            out.addAll(x.complete(a));
        else if (a.length == 2 && a[0].equalsIgnoreCase("essai")) out.addAll(List.of("quitter", "armes", "build"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) out.addAll(Catalogue.DEFS.keySet());
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    // ------------------------------------------------------------------ menu des armes

    static final class ArmsMenu implements InventoryHolder {
        final int page;
        final List<ItemStack> items;
        Inventory inv;

        ArmsMenu(int page, List<ItemStack> items) {
            this.page = page;
            this.items = items;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static List<ItemStack> menuItems() {
        List<ItemStack> list = new ArrayList<>();
        for (Catalogue.Def d : Catalogue.all()) {
            int n = d.kind == Catalogue.Kind.AMMO ? Math.min(d.stack, 32) : d.kind == Catalogue.Kind.THROW ? Math.min(d.stack, 4) : 1;
            list.add(Catalogue.create(d.id, n));
        }
        // Exemples de niveau et de gravures
        list.add(Catalogue.create("epee_fer", 5, List.of(), 1));
        list.add(Catalogue.create("dague", 5, List.of(), 1));
        list.add(Catalogue.create("pistolet", 5, List.of(), 1));
        list.add(Catalogue.create("casque_fer", 5, List.of(), 1));
        for (String g : Catalogue.ENGRAVINGS.keySet()) list.add(Catalogue.create("epee_fer", 0, List.of(g), 1));
        list.add(Catalogue.create("epee_diamant", 5, List.of("saignement", "vol_de_vie"), 1));
        return list;
    }

    public void openArmsMenu(Player p, int page) {
        List<ItemStack> items = menuItems();
        int pages = (items.size() + 44) / 45;
        page = Math.max(0, Math.min(page, pages - 1));
        ArmsMenu h = new ArmsMenu(page, items);
        Inventory inv = Bukkit.createInventory(h, 54, Txt.c("§8Armes et objets du Refuge (" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;
        for (int i = 0; i < 45 && page * 45 + i < items.size(); i++) inv.setItem(i, items.get(page * 45 + i));
        if (page > 0) inv.setItem(45, button(Material.ARROW, "§fPage précédente"));
        inv.setItem(49, button(Material.BARRIER, "§cFermer"));
        if (page < pages - 1) inv.setItem(53, button(Material.ARROW, "§fPage suivante"));
        p.openInventory(inv);
    }

    private static ItemStack button(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Txt.item(name));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ArmsMenu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 45 && h.page > 0) openArmsMenu(p, h.page - 1);
        else if (slot == 53) openArmsMenu(p, h.page + 1);
        else if (slot == 49) p.closeInventory();
        else if (slot < 45 && h.page * 45 + slot < h.items.size()) Salle.give(p, h.items.get(h.page * 45 + slot));
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ArmsMenu) e.setCancelled(true);
    }
}
