package fr.eemicraft.horscours;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

/**
 * Le niveau 1 « vivant » : meubles 3D, fouille (tiroirs, étagères), armoires à cadenas (pince coupante,
 * bruyante : attire Loïc) et à code (digicode), cachettes dans les armoires vides, mannequins de bureau
 * qui changent de place quand personne ne les regarde, chaises qui se tournent vers les joueurs,
 * cloisons qui glissent hors de vue. Les objets ne brillent pas : un reflet apparaît seulement sous la lampe.
 * Tout est en entités non persistantes (tag du plugin) : rien ne survit à un redémarrage.
 */
final class Niveau1 {
    private static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacySection();

    private final EemiHorsCours plugin;
    private final Maze maze;
    private final World w;
    private final Random rnd = new Random();

    /** Un meuble interactif : son modèle affiché et son état. */
    private final class Box {
        final int id;
        final Maze.Piece piece;
        ItemDisplay display, overlay, item;
        boolean opened, searched;
        String content;
        Box(int id, Maze.Piece p) {
            this.id = id;
            this.piece = p;
            this.content = p.content;
        }
        Location center() {
            return new Location(w, piece.x + 0.5, Maze.Y + 0.9, piece.z + 0.5);
        }
    }

    private final Map<Integer, Box> boxes = new HashMap<>();
    private final List<ItemDisplay> chairs = new ArrayList<>();
    private final List<ItemDisplay> mannequins = new ArrayList<>();
    private final Map<UUID, Integer> hidden = new HashMap<>();      // joueur -> armoire
    private final Set<UUID> spotted = new HashSet<>();              // vu en entrant dans sa cachette
    private final Map<UUID, int[]> action = new HashMap<>();        // {boxId, fin (tick), type 0 fouille / 1 coupe}
    private final Map<UUID, Location> actionAt = new HashMap<>();
    private final Map<UUID, Long> rattle = new HashMap<>();
    private long nextWall, tick;
    int pinceOwners;

    Niveau1(EemiHorsCours plugin, Maze maze) {
        this.plugin = plugin;
        this.maze = maze;
        this.w = maze.w;
    }

    // ------------------------------------------------------------------ mise en place
    void setup() {
        boxes.clear();
        chairs.clear();
        mannequins.clear();
        hidden.clear();
        spotted.clear();
        action.clear();
        actionAt.clear();
        tick = 0;
        nextWall = 150; // premier glissement après ~15 s (boucle toutes les 2 ticks)
        int k = 0;
        for (Maze.Piece p : maze.pieces) {
            ItemDisplay d = display(p.model, p.x + 0.5, Maze.Y + p.yOff + 0.5, p.z + 0.5, p.yaw, p.scale, "n1deco");
            switch (p.role) {
                case "shelf", "lock", "code", "drawer", "hide", "board" -> {
                    Box b = new Box(k++, p);
                    b.display = d;
                    boxes.put(b.id, b);
                    if (p.role.equals("lock")) b.overlay = display("horscours_cadenas", p.x + 0.5, Maze.Y + 0.5, p.z + 0.5, p.yaw, 1f, "n1deco");
                    if (p.role.equals("code")) b.overlay = display("horscours_digicode", p.x + 0.5, Maze.Y + 0.5, p.z + 0.5, p.yaw, 1f, "n1deco");
                    if (!p.role.equals("board")) clickable(b);
                    if (p.role.equals("shelf") && (b.content.startsWith("fuse") || b.content.startsWith("pile"))) showOnShelf(b);
                    if (p.role.equals("board")) writeBoard(b, b.content.equals("code"));
                }
                case "chair" -> chairs.add(d);
                default -> {
                }
            }
        }
        // Mannequins dans des coins libres
        int count = plugin.getConfig().getInt("maze.mannequins", 7);
        List<int[]> spots = new ArrayList<>(maze.freeSpots);
        java.util.Collections.shuffle(spots, rnd);
        for (int i = 0; i < Math.min(count, spots.size()); i++) {
            int[] s = spots.get(i);
            Location c = maze.center(maze.cellAt(s[0], s[1])[0], maze.cellAt(s[0], s[1])[1]);
            float yaw = Maze.yawFacing((int) Math.signum(c.getBlockX() - s[0]), (int) Math.signum(c.getBlockZ() - s[1]));
            mannequins.add(display("horscours_mannequin", s[0] + 0.5, Maze.Y + 0.5, s[1] + 0.5, yaw, 1f, "n1deco"));
        }
    }

    private ItemDisplay display(String model, double x, double y, double z, float yaw, float scale, String tag) {
        Location at = new Location(w, x, y, z, yaw, 0);
        return w.spawn(at, ItemDisplay.class, e -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("eemicraft", "meubles/" + model));
            it.setItemMeta(m);
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            e.setTeleportDuration(3);
            e.setPersistent(false);
            plugin.tag(e, tag);
        });
    }

    private void clickable(Box b) {
        double h = b.piece.model.equals("horscours_armoire") ? 2.4 : b.piece.model.equals("horscours_etagere") ? 1.95 : 1.0;
        for (int[] t : b.piece.tiles) {
            w.spawn(new Location(w, t[0] + 0.5, Maze.Y - 0.01, t[1] + 0.5), Interaction.class, e -> {
                e.setInteractionWidth(1.02f);
                e.setInteractionHeight((float) h);
                e.setResponsive(true);
                e.setPersistent(false);
                plugin.tag(e, "n1:" + b.id);
            });
        }
    }

    /** Objet posé sur l'étagère, à plat, sans halo. */
    private void showOnShelf(Box b) {
        String model = b.content.startsWith("fuse") ? "fusible" : "pile";
        double y = Maze.Y + (rnd.nextBoolean() ? 0.78 : 1.40);
        // un peu vers l'avant de l'étagère
        int[] f = Maze.rot(0, 1, b.piece.yaw);
        b.item = smallItem(model, b.piece.x + 0.5 + f[0] * 0.12, y, b.piece.z + 0.5 + f[1] * 0.12, b.piece.yaw + rnd.nextInt(60) - 30);
    }

    private ItemDisplay smallItem(String model, double x, double y, double z, float yaw) {
        return w.spawn(new Location(w, x, y, z, yaw, 0), ItemDisplay.class, e -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("eemicraft", "horscours/" + model));
            it.setItemMeta(m);
            e.setItemStack(it);
            e.setBillboard(Display.Billboard.FIXED);
            // à plat sur la planche
            e.setTransformation(new Transformation(new Vector3f(0f, 0.02f, 0f), new AxisAngle4f((float) Math.toRadians(90), 1f, 0f, 0f),
                    new Vector3f(0.42f, 0.42f, 0.42f), new AxisAngle4f()));
            e.setPersistent(false);
            plugin.tag(e, "n1item");
        });
    }

    private void writeBoard(Box b, boolean code) {
        int[] f = Maze.rot(0, 1, b.piece.yaw);
        int[] o = Maze.rot(1, 0, b.piece.yaw);
        double x = b.piece.x + 0.5 + o[0] * 0.5 - f[0] * 0.41, z = b.piece.z + 0.5 + o[1] * 0.5 - f[1] * 0.41;
        String[] boring = {"§8Cours de marketing\n§8- persona\n§8- tunnel de vente", "§8RAPPEL : rendre les\n§8projets avant vendredi",
                "§8HTML / CSS\n§8<div> <div> <div>", "§8Pitch : 2 minutes\n§8pas plus !!", "§4ON VOUS VOIT"};
        String text = code ? "§1Armoire de la réserve\n§1code : §4§l" + maze.code.charAt(0) + " " + maze.code.charAt(1) + " " + maze.code.charAt(2)
                + "\n§8(ne pas effacer)" : boring[rnd.nextInt(boring.length)];
        w.spawn(new Location(w, x, Maze.Y + 1.75, z, b.piece.yaw + 180f, 0), TextDisplay.class, t -> {
            t.text(LEG.deserialize(text));
            t.setBillboard(Display.Billboard.FIXED);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setLineWidth(140);
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f, 0.55f, 0.55f), new AxisAngle4f()));
            plugin.tag(t, "n1deco");
        });
    }

    // ------------------------------------------------------------------ interactions
    /** Clic sur un meuble interactif. */
    void click(Player p, int id) {
        Box b = boxes.get(id);
        if (b == null) return;
        if (hidden.containsKey(p.getUniqueId())) return;
        if (action.containsKey(p.getUniqueId())) return;
        switch (b.piece.role) {
            case "hide" -> hide(p, b);
            case "shelf" -> {
                if (b.content.startsWith("fuse") || b.content.startsWith("pile")) take(p, b);
                else p.sendActionBar(LEG.deserialize("§7Des cartons, de la poussiere... rien d'utile."));
            }
            case "drawer" -> {
                if (b.searched) {
                    p.sendActionBar(LEG.deserialize("§7Deja fouille."));
                    return;
                }
                start(p, b, 0, 30);
                w.playSound(b.center(), Sound.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.8f, 1.3f);
            }
            case "lock" -> {
                if (b.opened) {
                    take(p, b);
                    return;
                }
                if (!plugin.holdsPince(p)) {
                    p.sendActionBar(LEG.deserialize("§cFermee par un cadenas. §7Il faudrait de quoi le couper."));
                    long now = System.currentTimeMillis();
                    if (now - rattle.getOrDefault(p.getUniqueId(), 0L) > 1500) {
                        rattle.put(p.getUniqueId(), now);
                        w.playSound(b.center(), Sound.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 1f, 0.7f);
                        w.playSound(b.center(), Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.6f);
                        plugin.noise(b.center(), 10);
                    }
                    return;
                }
                start(p, b, 1, 50);
            }
            case "code" -> {
                if (b.opened) {
                    take(p, b);
                    return;
                }
                openKeypad(p, b);
            }
            default -> {
            }
        }
    }

    private void start(Player p, Box b, int type, int ticks) {
        action.put(p.getUniqueId(), new int[] {b.id, (int) (tick + ticks / 2), type, ticks / 2});
        actionAt.put(p.getUniqueId(), p.getLocation());
    }

    private void finish(Player p, Box b, int type) {
        if (type == 0) {
            b.searched = true;
            w.playSound(b.center(), Sound.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.7f, 1.4f);
            switch (b.content.contains(":") ? b.content.substring(0, b.content.indexOf(':')) : b.content) {
                case "pince" -> {
                    plugin.givePince(p);
                    b.content = "";
                    p.sendActionBar(LEG.deserialize("§fUne §cpince coupante§f. Ca coupe un cadenas... mais ca fait du bruit."));
                }
                case "pile" -> {
                    plugin.pile(p);
                    b.content = "";
                }
                case "photo" -> {
                    b.content = "";
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.8f, 0.6f);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
                    p.showTitle(Title.title(Component.empty(), LEG.deserialize("§7Une photo de Loic. §8Il sourit."),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1600), Duration.ofMillis(600))));
                }
                default -> p.sendActionBar(LEG.deserialize(pick("§7Vide.", "§7Des trombones et un vieux badge.",
                        "§7Un ticket de cantine de 2019.", "§7Des feutres secs.", "§7Un post-it : « On se voit a l'happy hour ! »")));
            }
            return;
        }
        // Cadenas coupé
        b.opened = true;
        if (b.overlay != null) b.overlay.remove();
        b.overlay = null;
        openDoors(b);
        w.playSound(b.center(), Sound.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1f, 1.6f);
        w.playSound(b.center(), Sound.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
        plugin.noise(b.center(), 40);
        p.sendActionBar(LEG.deserialize("§fLe cadenas cede. §cCa a resonne dans tout l'etage..."));
        plugin.say(30);
    }

    private String pick(String... s) {
        return s[rnd.nextInt(s.length)];
    }

    /** L'armoire s'ouvre : modèle ouvert + contenu visible sur l'étagère du milieu. */
    private void openDoors(Box b) {
        b.display.setItemStack(model("meubles/horscours_armoire_ouverte"));
        w.playSound(b.center(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.7f);
        if (b.content.startsWith("fuse") || b.content.startsWith("pile")) {
            String m = b.content.startsWith("fuse") ? "fusible" : "pile";
            b.item = smallItem(m, b.piece.x + 0.5, Maze.Y + 1.03, b.piece.z + 0.5, b.piece.yaw);
        }
    }

    private ItemStack model(String path) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("eemicraft", path));
        it.setItemMeta(m);
        return it;
    }

    private void take(Player p, Box b) {
        if (b.content.isEmpty()) {
            p.sendActionBar(LEG.deserialize("§7Il n'y a plus rien."));
            return;
        }
        String c = b.content;
        b.content = "";
        if (b.item != null) b.item.remove();
        b.item = null;
        if (c.startsWith("fuse")) plugin.fuse(p);
        else if (c.startsWith("pile")) plugin.pile(p);
    }

    // ------------------------------------------------------------------ digicode
    static final class Keypad implements InventoryHolder {
        final int box;
        String typed = "";
        Inventory inv;
        Keypad(int box) {
            this.box = box;
        }
        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void openKeypad(Player p, Box b) {
        Keypad k = new Keypad(b.id);
        Inventory inv = Bukkit.createInventory(k, 36, LEG.deserialize("§8Digicode de l'armoire"));
        k.inv = inv;
        int[] slots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        for (int i = 0; i < 9; i++) inv.setItem(slots[i], key(String.valueOf(i + 1), Material.STONE_BUTTON));
        inv.setItem(31, key("0", Material.STONE_BUTTON));
        inv.setItem(30, key("§cEffacer", Material.RED_DYE));
        inv.setItem(32, key("§aValider", Material.LIME_DYE));
        refreshScreen(k);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.8f);
    }

    private ItemStack key(String label, Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEG.deserialize("§f" + label).decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(meta);
        return it;
    }

    private void refreshScreen(Keypad k) {
        StringBuilder s = new StringBuilder("§a");
        for (int i = 0; i < 3; i++) s.append(i < k.typed.length() ? k.typed.charAt(i) : '_').append(' ');
        k.inv.setItem(0, key(s.toString().trim(), Material.GREEN_STAINED_GLASS_PANE));
    }

    /** Clic dans le digicode (appelé par le plugin, l'événement est déjà annulé). */
    void keypadClick(Player p, Keypad k, int slot) {
        ItemStack it = k.inv.getItem(slot);
        if (it == null || slot == 0) return;
        String label = LEG.serialize(it.getItemMeta().displayName()).replace("§f", "");
        Box b = boxes.get(k.box);
        if (b == null) return;
        if (label.contains("Effacer")) k.typed = "";
        else if (label.contains("Valider")) {
            if (k.typed.equals(maze.code)) {
                p.closeInventory();
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
                b.opened = true;
                if (b.overlay != null) b.overlay.remove();
                b.overlay = null;
                openDoors(b);
                return;
            }
            k.typed = "";
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            w.playSound(b.center(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, SoundCategory.BLOCKS, 0.8f, 0.5f);
            plugin.noise(b.center(), 12);
            p.sendActionBar(LEG.deserialize("§cCode faux."));
        } else if (k.typed.length() < 3) {
            k.typed += label.trim();
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f + k.typed.length() * 0.1f);
        }
        refreshScreen(k);
    }

    // ------------------------------------------------------------------ cachettes
    private void hide(Player p, Box b) {
        for (Map.Entry<UUID, Integer> e : hidden.entrySet())
            if (e.getValue() == b.id) {
                p.sendActionBar(LEG.deserialize("§7Il y a deja quelqu'un dedans."));
                return;
            }
        boolean seen = plugin.loicSees(p);
        hidden.put(p.getUniqueId(), b.id);
        if (seen) spotted.add(p.getUniqueId());
        int[] f = Maze.rot(0, 1, b.piece.yaw);
        Location in = new Location(w, b.piece.x + 0.5, Maze.Y, b.piece.z + 0.5, Maze.yawFacing(f[0], f[1]) - 180f, 0);
        p.teleport(in);
        // Invisible pour les autres joueurs (corps et pseudo) tant qu'il est dans l'armoire
        for (Player o : w.getPlayers()) if (o != p) o.hidePlayer(plugin, p);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 600, 0, false, false, false));
        w.playSound(b.center(), Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        p.sendActionBar(LEG.deserialize("§7Cache dans l'armoire. §8(accroupis-toi pour sortir)"));
        plugin.say(25);
    }

    boolean isHidden(Player p) {
        return hidden.containsKey(p.getUniqueId()) && !spotted.contains(p.getUniqueId());
    }

    boolean inLocker(Player p) {
        return hidden.containsKey(p.getUniqueId());
    }

    void unhide(Player p) {
        Integer id = hidden.remove(p.getUniqueId());
        spotted.remove(p.getUniqueId());
        if (id == null) return;
        showAgain(p);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        Box b = boxes.get(id);
        if (b == null) return;
        int[] f = Maze.rot(0, 1, b.piece.yaw);
        Location out = new Location(w, b.piece.x + f[0] + 0.5, Maze.Y, b.piece.z + f[1] + 0.5, Maze.yawFacing(f[0], f[1]) - 180f, 0);
        p.teleport(out);
        w.playSound(b.center(), Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 1.2f);
    }

    private void showAgain(Player p) {
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.showPlayer(plugin, p);
    }

    void forget(Player p) {
        if (hidden.containsKey(p.getUniqueId())) showAgain(p);
        hidden.remove(p.getUniqueId());
        spotted.remove(p.getUniqueId());
        action.remove(p.getUniqueId());
        actionAt.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ boucle (toutes les 2 ticks)
    void tick(List<Player> players, double loicX, double loicZ, boolean loicAlive) {
        tick++;
        // Actions en cours (fouille, coupe)
        for (Player p : players) {
            int[] a = action.get(p.getUniqueId());
            if (a == null) continue;
            Box b = boxes.get(a[0]);
            Location at = actionAt.get(p.getUniqueId());
            if (b == null || at == null || p.getLocation().distanceSquared(at) > 1.0 || (a[2] == 1 && !plugin.holdsPince(p))) {
                action.remove(p.getUniqueId());
                p.sendActionBar(LEG.deserialize("§7Interrompu."));
                continue;
            }
            int left = (int) (a[1] - tick);
            int total = a[3];
            int done = Math.max(0, Math.min(10, (int) Math.round(10.0 * (total - left) / total)));
            p.sendActionBar(LEG.deserialize((a[2] == 0 ? "§7Fouille " : "§cCoupe le cadenas ") + "§f" + "▮".repeat(done) + "§8" + "▮".repeat(10 - done)));
            if (a[2] == 1 && tick % 5 == 0) {
                w.playSound(b.center(), Sound.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 1f, 0.6f + rnd.nextFloat() * 0.3f);
                plugin.noise(b.center(), 25);
            }
            if (left <= 0) {
                action.remove(p.getUniqueId());
                finish(p, b, a[2]);
            }
        }
        // Dans une cachette : immobile, respiration, Loïc qui passe
        for (Player p : players) {
            if (!hidden.containsKey(p.getUniqueId())) continue;
            if (tick % 20 == 0) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.5f, 0.7f);
        }
        // Reflet sous la lampe (pas de halo permanent)
        if (tick % 5 == 0)
            for (Player p : players) {
                if (!plugin.lampOn(p)) continue;
                Location eye = p.getEyeLocation();
                for (Box b : boxes.values()) {
                    if (b.item == null || !b.item.isValid()) continue;
                    Location l = b.item.getLocation();
                    Vector to = l.toVector().subtract(eye.toVector());
                    double d = to.length();
                    if (d > 7 || d < 0.3 || to.multiply(1 / d).dot(eye.getDirection()) < 0.85) continue;
                    if (rnd.nextDouble() < 0.5) p.spawnParticle(Particle.WAX_OFF, l.clone().add(0, 0.08, 0), 1, 0.05, 0.02, 0.05, 0);
                }
            }
        // Mannequins et chaises : ils bougent quand personne ne regarde
        if (tick % 15 == 0 && !players.isEmpty()) {
            for (ItemDisplay m : mannequins) moveMannequin(m, players);
            for (ItemDisplay c : chairs) if (rnd.nextDouble() < 0.08 && !seen(c.getLocation().add(0, 0.5, 0), players)) turnChair(c, players);
        }
        // Cloisons qui glissent
        if (tick >= nextWall && !players.isEmpty()) {
            int min = plugin.getConfig().getInt("maze.walls-min", 18), max = plugin.getConfig().getInt("maze.walls-max", 40);
            nextWall = tick + (min + rnd.nextInt(Math.max(1, max - min))) * 10L;
            shiftWall(players, loicX, loicZ, loicAlive);
        }
    }

    private void moveMannequin(ItemDisplay m, List<Player> players) {
        Location here = m.getLocation();
        if (seen(here.clone().add(0, 1.2, 0), players)) return;
        Player near = nearest(players, here);
        if (near == null) return;
        double d = near.getLocation().distance(here);
        if (d < 4) return;
        if (rnd.nextDouble() < 0.5) {
            // se tourne vers le joueur le plus proche
            float yaw = (float) Math.toDegrees(Math.atan2(-(near.getX() - here.getX()), near.getZ() - here.getZ())) + 180f;
            m.setRotation(yaw, 0);
            return;
        }
        // change de coin : plus près des joueurs, toujours hors de vue
        if (maze.freeSpots.isEmpty()) return;
        for (int tries = 0; tries < 12; tries++) {
            int[] s = maze.freeSpots.get(rnd.nextInt(maze.freeSpots.size()));
            Location to = new Location(w, s[0] + 0.5, Maze.Y + 0.5, s[1] + 0.5);
            double nd = to.distance(near.getLocation());
            if (nd < 5 || nd > 16 || seen(to.clone().add(0, 1.2, 0), players)) continue;
            boolean taken = false;
            for (ItemDisplay o : mannequins) if (o != m && o.getLocation().distanceSquared(to) < 0.5) taken = true;
            if (taken) continue;
            float yaw = (float) Math.toDegrees(Math.atan2(-(near.getX() - to.getX()), near.getZ() - to.getZ())) + 180f;
            to.setYaw(yaw);
            m.setTeleportDuration(0);
            m.teleport(to);
            m.setTeleportDuration(3);
            return;
        }
    }

    private void turnChair(ItemDisplay c, List<Player> players) {
        Player near = nearest(players, c.getLocation());
        if (near == null || near.getLocation().distance(c.getLocation()) > 14) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-(near.getX() - c.getX()), near.getZ() - c.getZ())) + 180f;
        c.setRotation(yaw, 0);
        if (near.getLocation().distance(c.getLocation()) < 9)
            w.playSound(c.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.3f, 0.5f);
    }

    /** Fait glisser une cloison mobile hors de vue, sans jamais couper le labyrinthe en deux. */
    private void shiftWall(List<Player> players, double lx, double lz, boolean loicAlive) {
        List<int[]> cand = new ArrayList<>(); // {e?1:0, i, j}
        for (int i = 0; i < maze.n; i++)
            for (int j = 0; j < maze.n; j++) {
                if (maze.movE[i][j]) cand.add(new int[] {1, i, j});
                if (maze.movS[i][j]) cand.add(new int[] {0, i, j});
            }
        java.util.Collections.shuffle(cand, rnd);
        for (int[] c : cand) {
            boolean alongE = c[0] == 1;
            int i = c[1], j = c[2];
            double mx = alongE ? Maze.OX + (i + 1) * Maze.P + 0.5 : Maze.OX + i * Maze.P + 3.5;
            double mz = alongE ? Maze.OZ + j * Maze.P + 3.5 : Maze.OZ + (j + 1) * Maze.P + 0.5;
            Location mid = new Location(w, mx, Maze.Y + 1.5, mz);
            boolean tooClose = false;
            for (Player p : players) if (p.getLocation().distanceSquared(mid) < 7 * 7) tooClose = true;
            if (loicAlive && (lx - mx) * (lx - mx) + (lz - mz) * (lz - mz) < 4) tooClose = true;
            if (tooClose || seen(mid, players)) continue;
            boolean isOpen = alongE ? maze.east[i][j] : maze.south[i][j];
            if (isOpen) {
                if (alongE) maze.east[i][j] = false;
                else maze.south[i][j] = false;
                boolean ok = maze.allConnected();
                if (alongE) maze.east[i][j] = true;
                else maze.south[i][j] = true;
                if (!ok) continue;
            }
            maze.setSegment(alongE, i, j, !isOpen);
            for (Player p : players) if (p.getLocation().distanceSquared(mid) < 25 * 25) plugin.say(9);
            w.playSound(mid, Sound.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 1.4f, 0.45f);
            w.playSound(mid, Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2f, 0.5f);
            w.playSound(mid, Sound.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0f, 0.5f);
            return;
        }
    }

    private Player nearest(List<Player> players, Location l) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Un joueur voit-il ce point (champ de vision large + ligne de vue) ? */
    boolean seen(Location target, List<Player> players) {
        for (Player p : players) {
            Location eye = p.getEyeLocation();
            Vector to = target.toVector().subtract(eye.toVector());
            double d = to.length();
            if (d > 40) continue;
            if (d < 1.5) return true;
            Vector dir = to.clone().multiply(1 / d);
            if (dir.dot(eye.getDirection()) < 0.45) continue;
            RayTraceResult hit = w.rayTraceBlocks(eye, dir, Math.max(0, d - 0.6), FluidCollisionMode.NEVER, true);
            if (hit == null) return true;
        }
        return false;
    }

    void clear() {
        for (Entity e : w.getEntities()) {
            String t = plugin.tagOf(e);
            if (t != null && (t.startsWith("n1"))) e.remove();
        }
        for (UUID id : hidden.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) showAgain(p);
        }
        boxes.clear();
        chairs.clear();
        mannequins.clear();
        hidden.clear();
        spotted.clear();
        action.clear();
    }
}
