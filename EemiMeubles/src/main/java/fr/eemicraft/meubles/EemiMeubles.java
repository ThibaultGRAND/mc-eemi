package fr.eemicraft.meubles;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Meubles 3D de l'EEMI. Un meuble = une ItemDisplay (modèle eemicraft:meubles/&lt;id&gt; du pack), des
 * barrières pour la collision (cases du catalogue), des entités Interaction pour les clics. Tout est
 * persistant (sauvé avec le monde). Les places assises créent un siège invisible le temps de s'asseoir.
 *
 * Builders (eemimeubles.build) : /meubles = catalogue ; meuble en main + clic droit sur un bloc = poser
 * (face au joueur, ou dos au mur pour un objet mural) ; Marteau du builder : clic droit = tourner de 90°,
 * accroupi + clic droit = retirer. Tout le monde : clic droit sur une place = s'asseoir, s'accroupir = se lever.
 */
public final class EemiMeubles extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacySection();

    /** Définition d'un meuble (catalogue meubles.yml généré par gen_meubles.py). */
    record Def(String id, String nom, String cat, float echelle, double hauteur, int largeur, boolean mur,
               boolean surface, List<int[]> cases, List<double[]> places) {
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();
    private NamespacedKey kMeuble, kUid, kYaw, kCases, kOutil, kSiege, kItem;
    private final Set<UUID> sieges = new HashSet<>();

    @Override
    public void onEnable() {
        kMeuble = new NamespacedKey(this, "meuble");
        kUid = new NamespacedKey(this, "uid");
        kYaw = new NamespacedKey(this, "yaw");
        kCases = new NamespacedKey(this, "cases");
        kOutil = new NamespacedKey(this, "outil");
        kSiege = new NamespacedKey(this, "siege");
        kItem = new NamespacedKey(this, "item");
        saveDefaultConfig();
        loadCatalog();
        Bukkit.getPluginManager().registerEvents(this, this);
        // Sièges orphelins (redémarrage pendant qu'un joueur était assis)
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class))
                if (e.getPersistentDataContainer().has(kSiege)) e.remove();
        getLogger().info(defs.size() + " meubles au catalogue.");
    }

    @Override
    public void onDisable() {
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class))
                if (e.getPersistentDataContainer().has(kSiege)) {
                    for (Entity p : e.getPassengers()) e.removePassenger(p);
                    e.remove();
                }
    }

    private void loadCatalog() {
        defs.clear();
        try (InputStreamReader r = new InputStreamReader(getResource("meubles.yml"), StandardCharsets.UTF_8)) {
            ConfigurationSection root = YamlConfiguration.loadConfiguration(r).getConfigurationSection("meubles");
            if (root == null) return;
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                List<int[]> cases = new ArrayList<>();
                for (String c : s.getStringList("cases")) {
                    String[] p = c.split(",");
                    cases.add(new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())});
                }
                List<double[]> places = new ArrayList<>();
                for (String c : s.getStringList("places")) {
                    String[] p = c.split(",");
                    places.add(new double[] {Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim())});
                }
                defs.put(id, new Def(id, s.getString("nom", id), s.getString("categorie", "divers"),
                        (float) s.getDouble("echelle", 1), s.getDouble("hauteur", 1), s.getInt("largeur", 1),
                        s.getBoolean("mur"), s.getBoolean("surface"), cases, places));
            }
        } catch (Exception e) {
            getLogger().severe("Catalogue illisible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ objets
    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEG.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(LEG.deserialize(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    ItemStack furnitureItem(Def d) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Clic droit sur un bloc pour le poser");
        if (d.mur()) lore.add("§7Se pose contre un mur");
        if (d.surface()) lore.add("§7Se pose sur une table ou un meuble");
        if (!d.places().isEmpty()) lore.add("§aOn peut s'y asseoir");
        ItemStack it = named(Material.PAPER, "§f" + d.nom(), lore);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("eemicraft", "meubles/" + d.id()));
        meta.getPersistentDataContainer().set(kItem, PersistentDataType.STRING, d.id());
        it.setItemMeta(meta);
        return it;
    }

    ItemStack hammer() {
        ItemStack it = named(Material.BRUSH, "§6Marteau du builder",
                List.of("§7Clic droit sur un meuble : le tourner", "§7Accroupi + clic droit : le retirer"));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(kOutil, PersistentDataType.STRING, "marteau");
        it.setItemMeta(meta);
        return it;
    }

    private String str(ItemStack it, NamespacedKey k) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(k, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ catalogue (menu)
    private static final class Menu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void openMenu(Player p) {
        Menu h = new Menu();
        Inventory inv = Bukkit.createInventory(h, 54, LEG.deserialize("§8Meubles de l'EEMI"));
        h.inv = inv;
        int i = 0;
        for (Def d : defs.values()) {
            if (i >= 45) break;
            ItemStack it = furnitureItem(d);
            ItemMeta m = it.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
            lore.add(LEG.deserialize("§8" + d.cat()).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(i++, it);
        }
        inv.setItem(49, hammer());
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) return;
        if ("marteau".equals(str(it, kOutil))) p.getInventory().addItem(hammer());
        else {
            Def d = defs.get(str(it, kItem));
            if (d != null) p.getInventory().addItem(furnitureItem(d));
        }
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("eemimeubles.build")) {
            s.sendMessage("Reserve aux builders.");
            return true;
        }
        if (a.length >= 2 && a[0].equalsIgnoreCase("give") && s instanceof Player p) {
            Def d = defs.get(a[1]);
            if (d == null) s.sendMessage("Meuble inconnu : " + a[1]);
            else p.getInventory().addItem(furnitureItem(d));
            return true;
        }
        if (a.length >= 1 && a[0].equalsIgnoreCase("liste")) {
            s.sendMessage(String.join(", ", defs.keySet()));
            return true;
        }
        if (s instanceof Player p) openMenu(p);
        return true;
    }

    // ------------------------------------------------------------------ géométrie
    /** Rotation d'une case du modèle (avant vers +z) selon le yaw de l'entité (vérifié avec Loïc : yaw 0 = avant vers -z). */
    static int[] rot(double dx, double dz, float yaw) {
        double a = Math.toRadians(yaw + 180);
        double c = Math.cos(a), s = Math.sin(a);
        return new int[] {(int) Math.round(dx * c - dz * s), (int) Math.round(dx * s + dz * c)};
    }

    static double[] rotD(double dx, double dz, float yaw) {
        double a = Math.toRadians(yaw + 180);
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {dx * c - dz * s, dx * s + dz * c};
    }

    private static float yawOf(BlockFace f) {
        return switch (f) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private static boolean free(Block b) {
        Material m = b.getType();
        return m.isAir() || m.name().endsWith("_CARPET") || m == Material.SNOW || m == Material.SHORT_GRASS
                || m == Material.LIGHT;
    }

    /** Cases occupées (avec barrière) : liste de {x, y, z, hauteur}, en coordonnées du monde. */
    private List<int[]> worldCells(Def d, Block anchor, float yaw) {
        List<int[]> out = new ArrayList<>();
        for (int[] c : d.cases()) {
            int[] r = rot(c[0], c[1], yaw);
            out.add(new int[] {anchor.getX() + r[0], anchor.getY(), anchor.getZ() + r[1], c[2]});
        }
        return out;
    }

    /** Cases cliquables (Interaction) : les cases à barrière, sinon la largeur du meuble le long de x. */
    private List<int[]> clickCells(Def d, Block anchor, float yaw) {
        if (!d.cases().isEmpty()) return worldCells(d, anchor, yaw);
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < d.largeur(); i++) {
            int[] r = rot(i, 0, yaw);
            out.add(new int[] {anchor.getX() + r[0], anchor.getY(), anchor.getZ() + r[1], 1});
        }
        return out;
    }

    // ------------------------------------------------------------------ poser / retirer
    boolean place(Player p, Def d, Block anchor, float yaw) {
        World w = anchor.getWorld();
        List<int[]> cells = worldCells(d, anchor, yaw);
        for (int[] c : cells)
            for (int y = 0; y < c[3]; y++)
                if (!free(w.getBlockAt(c[0], c[1] + y, c[2]))) {
                    if (p != null) p.sendActionBar(LEG.deserialize("§cPas assez de place ici."));
                    return false;
                }
        String uid = UUID.randomUUID().toString();
        StringBuilder enc = new StringBuilder();
        for (int[] c : cells)
            for (int y = 0; y < c[3]; y++) {
                Block b = w.getBlockAt(c[0], c[1] + y, c[2]);
                enc.append(b.getX()).append(',').append(b.getY()).append(',').append(b.getZ()).append('|')
                        .append(b.getBlockData().getAsString()).append(';');
                b.setType(Material.BARRIER, false);
            }
        Location at = anchor.getLocation().add(0.5, 0.5, 0.5);
        at.setYaw(yaw);
        float k = d.echelle();
        w.spawn(at, ItemDisplay.class, e -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("eemicraft", "meubles/" + d.id()));
            it.setItemMeta(m);
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(k, k, k), new AxisAngle4f()));
            e.setPersistent(true);
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            pdc.set(kMeuble, PersistentDataType.STRING, d.id());
            pdc.set(kUid, PersistentDataType.STRING, uid);
            pdc.set(kYaw, PersistentDataType.FLOAT, yaw);
            pdc.set(kCases, PersistentDataType.STRING, enc.toString());
        });
        double h = Math.max(0.3, Math.min(2.0, d.hauteur())) + 0.02;
        for (int[] c : clickCells(d, anchor, yaw)) {
            Location il = new Location(w, c[0] + 0.5, c[1] - 0.01, c[2] + 0.5);
            w.spawn(il, Interaction.class, e -> {
                e.setInteractionWidth(1.02f);
                e.setInteractionHeight((float) h);
                e.setResponsive(true);
                e.setPersistent(true);
                e.getPersistentDataContainer().set(kUid, PersistentDataType.STRING, uid);
            });
        }
        return true;
    }

    /** Toutes les entités d'un meuble (l'ItemDisplay et ses Interaction), cherchées autour d'un point. */
    private List<Entity> parts(Location near, String uid) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : near.getWorld().getNearbyEntities(near, 4, 4, 4)) {
            if (e.getPersistentDataContainer().has(kSiege)) continue;
            if (uid.equals(e.getPersistentDataContainer().get(kUid, PersistentDataType.STRING))) out.add(e);
        }
        return out;
    }

    private ItemDisplay body(List<Entity> parts) {
        for (Entity e : parts) if (e instanceof ItemDisplay d) return d;
        return null;
    }

    /** Retire le meuble (blocs d'origine remis à la place des barrières). Renvoie sa définition. */
    Def remove(Location near, String uid) {
        List<Entity> ps = parts(near, uid);
        ItemDisplay b = body(ps);
        Def d = null;
        if (b != null) {
            d = defs.get(b.getPersistentDataContainer().get(kMeuble, PersistentDataType.STRING));
            String enc = b.getPersistentDataContainer().get(kCases, PersistentDataType.STRING);
            if (enc != null)
                for (String part : enc.split(";")) {
                    if (part.isBlank()) continue;
                    String[] xs = part.split("\\|", 2);
                    String[] c = xs[0].split(",");
                    Block bl = b.getWorld().getBlockAt(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
                    if (bl.getType() == Material.BARRIER) {
                        try {
                            bl.setBlockData(Bukkit.createBlockData(xs[1]), false);
                        } catch (Exception ex) {
                            bl.setType(Material.AIR, false);
                        }
                    }
                }
        }
        for (Entity e : ps) {
            for (Entity seat : e.getWorld().getNearbyEntities(e.getLocation(), 3, 3, 3))
                if (uid.equals(seat.getPersistentDataContainer().get(kSiege, PersistentDataType.STRING))) {
                    for (Entity pas : seat.getPassengers()) seat.removePassenger(pas);
                    seat.remove();
                }
            e.remove();
        }
        return d;
    }

    // ------------------------------------------------------------------ clics
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        String id = str(e.getItem(), kItem);
        if (id == null) return;
        e.setCancelled(true);
        if (!p.hasPermission("eemimeubles.build")) return;
        Def d = defs.get(id);
        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();
        if (d == null || clicked == null || face == null) return;
        Block anchor;
        float yaw;
        if (d.mur()) {
            if (face == BlockFace.UP || face == BlockFace.DOWN) {
                p.sendActionBar(LEG.deserialize("§cCe meuble se pose contre un mur."));
                return;
            }
            anchor = clicked.getRelative(face);
            yaw = yawOf(face) - 180f;
        } else {
            anchor = free(clicked) && !d.surface() ? clicked : clicked.getRelative(face == BlockFace.DOWN ? BlockFace.UP : face);
            yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f;
        }
        if (!free(anchor) && !(anchor.getType() == Material.BARRIER && d.surface())) {
            p.sendActionBar(LEG.deserialize("§cPas assez de place ici."));
            return;
        }
        if (place(p, d, anchor, yaw)) {
            p.playSound(anchor.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1f);
            if (p.getGameMode() != GameMode.CREATIVE) e.getItem().subtract();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickFurniture(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity ent = e.getRightClicked();
        String uid = ent.getPersistentDataContainer().get(kUid, PersistentDataType.STRING);
        if (uid == null || ent.getPersistentDataContainer().has(kSiege)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        List<Entity> ps = parts(ent.getLocation(), uid);
        ItemDisplay b = body(ps);
        if (b == null) return;
        Def d = defs.get(b.getPersistentDataContainer().get(kMeuble, PersistentDataType.STRING));
        if (d == null) return;
        float yaw = b.getPersistentDataContainer().getOrDefault(kYaw, PersistentDataType.FLOAT, 0f);
        Block anchor = b.getLocation().getBlock();

        if ("marteau".equals(str(p.getInventory().getItemInMainHand(), kOutil)) && p.hasPermission("eemimeubles.build")) {
            remove(b.getLocation(), uid);
            if (p.isSneaking()) {
                if (p.getGameMode() != GameMode.CREATIVE) p.getInventory().addItem(furnitureItem(d));
                p.playSound(anchor.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 1f);
                return;
            }
            float next = yaw + 90f;
            if (!place(p, d, anchor, next)) place(null, d, anchor, yaw); // pas la place : on remet comme avant
            p.playSound(anchor.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.8f, 1.2f);
            return;
        }
        if (!d.places().isEmpty() && !p.isInsideVehicle()) sit(p, b, d, yaw, ent.getLocation());
    }

    // ------------------------------------------------------------------ s'asseoir
    private void sit(Player p, ItemDisplay b, Def d, float yaw, Location clicked) {
        String uid = b.getPersistentDataContainer().get(kUid, PersistentDataType.STRING);
        Location center = b.getLocation();
        double best = Double.MAX_VALUE;
        Location seatAt = null;
        double off = getConfig().getDouble("assise.decalage", 0.0);
        for (double[] s : d.places()) {
            double[] r = rotD(s[0] / 16.0 - 0.5, s[2] / 16.0 - 0.5, yaw);
            Location l = new Location(center.getWorld(), center.getX() + r[0], center.getBlockY() + s[1] / 16.0 + off,
                    center.getZ() + r[1]);
            boolean taken = false;
            for (Entity e : l.getWorld().getNearbyEntities(l, 0.3, 0.3, 0.3))
                if (e.getPersistentDataContainer().has(kSiege) && !e.getPassengers().isEmpty()) taken = true;
            double dist = l.distanceSquared(clicked);
            if (!taken && dist < best) {
                best = dist;
                seatAt = l;
            }
        }
        if (seatAt == null) {
            p.sendActionBar(LEG.deserialize("§7C'est déjà pris."));
            return;
        }
        // On regarde vers l'avant du meuble
        float face = yaw + 180f;
        seatAt.setYaw(face);
        Location look = p.getLocation();
        look.setYaw(face);
        look.setPitch(0);
        p.teleport(look);
        ItemDisplay seat = seatAt.getWorld().spawn(seatAt, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(kSiege, PersistentDataType.STRING, uid);
        });
        seat.addPassenger(p);
        sieges.add(seat.getUniqueId());
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        Entity v = e.getDismounted();
        if (!v.getPersistentDataContainer().has(kSiege)) return;
        Location l = v.getLocation().add(0, 0.6, 0);
        sieges.remove(v.getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> {
            v.remove();
            if (e.getEntity() instanceof Player p && p.isOnline() && p.getWorld().equals(l.getWorld()))
                p.teleport(l.setDirection(p.getLocation().getDirection()));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Entity v = e.getPlayer().getVehicle();
        if (v != null && v.getPersistentDataContainer().has(kSiege)) {
            v.removePassenger(e.getPlayer());
            v.remove();
        }
    }
}
