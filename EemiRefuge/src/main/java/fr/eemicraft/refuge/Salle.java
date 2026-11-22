package fr.eemicraft.refuge;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.Campfire;
import org.bukkit.block.Container;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Lectern;
import org.bukkit.block.Shelf;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.ChiseledBookshelf;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.VaultDisplayItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * La salle d'essai, construite par le plugin dans le monde vide refuge_essai (sol y 64) :
 * hub (0,0), armurerie au nord, arène des zombies à l'est, stand de tir au sud, meubles et fouille à l'ouest.
 * La disposition est déterministe : au démarrage on la « rejoue » sans rien poser pour retrouver
 * les boutons, étagères, barricade, etc. (place = false).
 */
public final class Salle implements Listener {

    /** Changer ce numéro reconstruit la salle au prochain démarrage. */
    static final int VERSION = 1; // 0.1.1 : pas de reconstruction auto (entites pas encore chargees au demarrage = doublons) -> /refuge essai build en jeu
    static final int F = 64; // sol
    static final int X1 = -72, X2 = 72, Z1 = -42, Z2 = 64; // emprise

    private final EemiRefuge plugin;
    private World w;
    private boolean place;
    final NamespacedKey keyVersion;

    // --- points interactifs (recalculés à chaque relecture de la disposition)
    private final Map<Long, Consumer<Player>> buttons = new HashMap<>();
    private final Map<Long, List<ItemStack>> armoryShelves = new HashMap<>();
    private final Map<Long, ItemStack> galleryShelves = new HashMap<>();
    private final Map<Long, Material> barricade = new LinkedHashMap<>();
    private final List<long[]> glass = new ArrayList<>(); // x,y,z,(nord-sud ? 1 : 0)
    private long safeKey, leverKey;
    private int[] safePos;
    /** Contenu du coffre-fort : le vault vanilla n'a pas d'inventaire, le plugin ouvre celui-ci. */
    private Inventory safeInv;
    private final List<Runnable> refills = new ArrayList<>();
    Location spawn, arenaMin, arenaMax, pool;
    final List<Location> parade = new ArrayList<>();

    // --- état
    private boolean safeLocked = true;
    private int barricadeHits;
    private long barricadeLastHit;
    private final Map<UUID, Integer> forcing = new HashMap<>();

    Salle(EemiRefuge plugin) {
        this.plugin = plugin;
        keyVersion = new NamespacedKey(plugin, "salle_version");
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static long key(Block b) {
        return key(b.getX(), b.getY(), b.getZ());
    }

    // ================================================================== construction

    /** Relit la disposition. Si build : vide l'emprise et pose tout. */
    void layout(World world, boolean build) {
        this.w = world;
        this.place = build;
        buttons.clear();
        armoryShelves.clear();
        galleryShelves.clear();
        barricade.clear();
        glass.clear();
        refills.clear();
        parade.clear();
        long t0 = System.currentTimeMillis();
        List<Chunk> chunks = new ArrayList<>();
        if (build) {
            for (int cx = X1 >> 4; cx <= X2 >> 4; cx++)
                for (int cz = Z1 >> 4; cz <= Z2 >> 4; cz++) {
                    Chunk c = w.getChunkAt(cx, cz);
                    c.addPluginChunkTicket(plugin);
                    chunks.add(c);
                }
            plugin.zombies().killAll();
            for (Chunk c : chunks)
                for (Entity e : c.getEntities())
                    if (!(e instanceof Player)) e.remove();
            fill(X1, F - 6, Z1, X2, F + 14, Z2, Material.AIR);
        }
        spawn = new Location(w, 0.5, F + 1, 0.5, 180, 0);
        buildBase();
        buildHub();
        buildArmory();
        buildArena();
        buildRange();
        buildFurniture();
        if (build) {
            safeLocked = true;
            for (Runnable r : refills) r.run();
            w.getPersistentDataContainer().set(keyVersion, PersistentDataType.INTEGER, VERSION);
            for (Chunk c : chunks) c.removePluginChunkTicket(plugin);
            plugin.getLogger().info("Salle d'essai construite en " + (System.currentTimeMillis() - t0) + " ms.");
        }
        this.place = false;
    }

    /** Remet tous les supports de fouille, la barricade, les vitres et le coffre-fort comme au départ. */
    void refill() {
        if (w == null) return;
        boolean old = place;
        place = true;
        safeLocked = true;
        barricadeHits = 0;
        for (Runnable r : refills) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Remise en place impossible : " + ex);
            }
        }
        place = old;
    }

    // ------------------------------------------------------------------ outils de pose

    private void set(int x, int y, int z, Material m) {
        if (!place) return;
        try {
            w.getBlockAt(x, y, z).setType(m, false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Bloc " + m + " impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
        }
    }

    private void set(int x, int y, int z, BlockData d) {
        if (!place || d == null) return;
        try {
            w.getBlockAt(x, y, z).setBlockData(d, false);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Etat de bloc impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
        }
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        if (!place) return;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    private BlockData facing(Material m, BlockFace f) {
        try {
            BlockData d = m.createBlockData();
            if (d instanceof Directional dir && dir.getFaces().contains(f)) dir.setFacing(f);
            return d;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Etat de bloc invalide " + m + " " + f + " : " + ex.getMessage());
            return null;
        }
    }

    /** Bouton (ou levier) : sur le sol (FLOOR) ou sur un mur (WALL, tourné vers f). */
    private BlockData attached(Material m, FaceAttachable.AttachedFace face, BlockFace f) {
        try {
            BlockData d = m.createBlockData();
            if (d instanceof FaceAttachable fa) fa.setAttachedFace(face);
            if (d instanceof Directional dir) dir.setFacing(f);
            return d;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Etat de bloc invalide " + m + " : " + ex.getMessage());
            return null;
        }
    }

    private void button(int x, int y, int z, BlockData d, Consumer<Player> action) {
        set(x, y, z, d);
        buttons.put(key(x, y, z), action);
    }

    private void room(int x1, int z1, int x2, int z2, int h, Material wall, Material floor, Material roof) {
        fill(x1, F, z1, x2, F, z2, floor);
        for (int x = x1; x <= x2; x++)
            for (int y = F + 1; y <= F + h; y++) {
                set(x, y, z1, wall);
                set(x, y, z2, wall);
            }
        for (int z = z1; z <= z2; z++)
            for (int y = F + 1; y <= F + h; y++) {
                set(x1, y, z, wall);
                set(x2, y, z, wall);
            }
        if (roof != null) fill(x1, F + h + 1, z1, x2, F + h + 1, z2, roof);
        // 0.1.1 : salle trop sombre (capture de Thibault) -> blocs de lumière invisibles (niveau 15) sous le plafond.
        for (int x = x1 + 2; x < x2; x += 4)
            for (int z = z1 + 2; z < z2; z += 4)
                if (w.getBlockAt(x, F + h, z).getType().isAir()) set(x, F + h, z, Material.LIGHT);
    }

    private TextDisplay label(double x, double y, double z, String text, float scale) {
        if (!place) return null;
        try {
            return w.spawn(new Location(w, x, y, z), TextDisplay.class, t -> {
                t.text(Txt.c(text));
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                // 0.1.1 : étiquettes illisibles (trop petites, sombres) -> plus grandes, toujours pleinement éclairées.
                t.setViewRange(1.0f);
                t.setBrightness(new Display.Brightness(15, 15));
                float s = Math.max(0.7f, scale * 1.6f);
                t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(s, s, s), new AxisAngle4f()));
                decor(t);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Texte impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
            return null;
        }
    }

    private void decor(Entity e) {
        e.addScoreboardTag("eemirefuge_decor");
        e.setPersistent(true);
    }

    /** Cadre accroché dans le bloc (x,y,z), tourné vers f (le mur est derrière). */
    private ItemFrame frame(int x, int y, int z, BlockFace f, ItemStack item, boolean glow, boolean fixed, String tag) {
        if (!place) return null;
        try {
            Class<? extends ItemFrame> cls = glow ? GlowItemFrame.class : ItemFrame.class;
            return w.spawn(new Location(w, x + 0.5, y + 0.5, z + 0.5), cls, fr -> {
                fr.setFacingDirection(f, true);
                fr.setItem(item, false);
                fr.setFixed(fixed);
                fr.setInvulnerable(fixed);
                if (tag != null) fr.addScoreboardTag(tag);
                decor(fr);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Cadre impossible en " + x + " " + y + " " + z + " : " + ex.getMessage());
            return null;
        }
    }

    private static ItemStack it(String id, int n) {
        return Catalogue.create(id, n);
    }

    private static ItemStack it(String id, int level, List<String> eng) {
        return Catalogue.create(id, level, eng, 1);
    }

    private static ItemStack v(Material m, int n) {
        return new ItemStack(m, n);
    }

    // ------------------------------------------------------------------ socle et hub

    private void buildBase() {
        fill(X1, F, Z1, X2, F, Z2, Material.SMOOTH_STONE);
        fill(X1, F - 1, Z1, X2, F - 1, Z2, Material.STONE);
        // Bordure : muret + barrières invisibles pour ne pas tomber dans le vide.
        for (int x = X1; x <= X2; x++)
            for (int z : new int[]{Z1, Z2}) {
                set(x, F + 1, z, Material.STONE_BRICK_WALL);
                fill(x, F + 2, z, x, F + 6, z, Material.BARRIER);
            }
        for (int z = Z1; z <= Z2; z++)
            for (int x : new int[]{X1, X2}) {
                set(x, F + 1, z, Material.STONE_BRICK_WALL);
                fill(x, F + 2, z, x, F + 6, z, Material.BARRIER);
            }
    }

    private void buildHub() {
        fill(-8, F, -8, 8, F, 8, Material.POLISHED_ANDESITE);
        fill(-2, F, -2, 2, F, 2, Material.CHISELED_STONE_BRICKS);
        set(0, F, 0, Material.SEA_LANTERN);
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            set(c[0], F + 1, c[1], Material.ANDESITE_WALL);
            set(c[0], F + 2, c[1], Material.SPRUCE_FENCE);
            set(c[0], F + 3, c[1], Material.LANTERN);
        }
        // allées
        fill(-2, F, -10, 2, F, -9, Material.POLISHED_ANDESITE);
        fill(9, F, -2, 19, F, 2, Material.POLISHED_ANDESITE);
        fill(-2, F, 9, 2, F, 11, Material.POLISHED_ANDESITE);
        fill(-11, F, -2, -9, F, 2, Material.POLISHED_ANDESITE);
        label(0.5, F + 4.2, -3.5, "§a§lSalle d'essai\n§7Dernier Refuge §80.1.3\n§8/refuge essai quitter pour sortir", 1.2f);
        label(0.5, F + 2.6, -8.2, "§6Armurerie", 1f);
        label(8.2, F + 2.6, 0.5, "§cArène des zombies", 1f);
        label(0.5, F + 2.6, 8.2, "§eStand de tir", 1f);
        label(-8.2, F + 2.6, 0.5, "§bMeubles et fouille\n§bGalerie du pack", 1f);
    }

    // ------------------------------------------------------------------ armurerie (nord)

    private void buildArmory() {
        room(-11, -36, 11, -10, 6, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, null);
        for (int[] c : new int[][]{{-11, -36}, {11, -36}, {-11, -10}, {11, -10}})
            fill(c[0], F + 1, c[1], c[0], F + 6, c[1], Material.STRIPPED_SPRUCE_LOG);
        fill(-2, F + 1, -10, 2, F + 3, -10, Material.AIR); // entrée
        fill(-2, F, -10, 2, F, -10, Material.POLISHED_ANDESITE);
        for (int x : new int[]{-8, 8}) {
            set(x, F + 1, -35, Material.SPRUCE_FENCE);
            set(x, F + 2, -35, Material.SPRUCE_FENCE);
            set(x, F + 3, -35, Material.LANTERN);
        }
        label(0.5, F + 5.6, -34.4, "§6§lArmurerie\n§7Clic droit sur un cadre, une étagère ou un porte-armure : tu le reçois.\n§7Menu complet : bouton de gauche ou /refuge essai armes", 0.9f);
        // Mur nord : trois rangées de cadres
        String[] row68 = {"epee_bois", "epee_pierre", "epee_fer", "epee_diamant", "epee_netherite", "dague", "hache", "lance", "masse", "arc", "arbalete"};
        String[] row67 = {"couteau_lancer", "bouteille_incendiaire", "fusee_appat", "tnt_meche", "pistolet", "tromblon", "carabine",
                "balle_pistolet", "cartouche_tromblon", "balle_carabine", "fleches"};
        for (int i = 0; i < row68.length; i++)
            frame(-5 + i, F + 4, -35, BlockFace.SOUTH, it(row68[i], 1), false, true, "eemirefuge_donne");
        for (int i = 0; i < row67.length; i++) {
            Catalogue.Def d = Catalogue.def(row67[i]);
            frame(-5 + i, F + 3, -35, BlockFace.SOUTH, it(row67[i], d.kind == Catalogue.Kind.AMMO ? 16 : 1), false, true, "eemirefuge_donne");
        }
        List<ItemStack> row66 = new ArrayList<>();
        row66.add(it("epee_fer", 0, List.of()));
        row66.add(it("epee_fer", 5, List.of()));
        row66.add(it("dague", 5, List.of()));
        row66.add(it("pistolet", 5, List.of()));
        for (String g : Catalogue.ENGRAVINGS.keySet()) row66.add(it("epee_fer", 0, List.of(g)));
        for (int i = 0; i < row66.size(); i++)
            frame(-5 + i, F + 2, -35, BlockFace.SOUTH, row66.get(i), false, true, "eemirefuge_donne");
        label(-5.5, F + 2.3, -34.4, "§7+0 / +5", 0.5f);
        label(-1.5, F + 1.7, -34.4, "§d6 gravures", 0.5f);

        // Mur ouest : étagères de munitions et d'objets
        List<List<ItemStack>> shelves = List.of(
                List.of(it("balle_pistolet", 16), it("cartouche_tromblon", 16), it("balle_carabine", 16)),
                List.of(it("couteau_lancer", 8), it("bouteille_incendiaire", 4), it("fusee_appat", 4)),
                List.of(it("tnt_meche", 4), it("fleches", 64), it("bandage", 8)),
                List.of(it("gravure_saignement", 1), it("gravure_feu", 1), it("gravure_recul", 1)),
                List.of(it("gravure_vol_de_vie", 1), it("gravure_fendoir", 1), it("gravure_silence", 1)),
                List.of(it("pierre_amelioration", 1), it("conserve", 8), it("cle_coffre_fort", 1)));
        for (int i = 0; i < shelves.size(); i++) {
            int z = -30 + i * 2;
            List<ItemStack> content = shelves.get(i);
            armoryShelves.put(key(-10, F + 1, z), content);
            set(-10, F + 1, z, facing(Material.SPRUCE_SHELF, BlockFace.EAST));
            refills.add(() -> fillInventory(-10, F + 1, z, content));
        }
        label(-9.3, F + 2.7, -24.5, "§eMunitions, lancers, gravures", 0.7f);

        // Mur est : porte-armures (cuir, maille, fer, diamant)
        String[] mats = {"cuir", "maille", "fer", "diamant"};
        String[] names = {"Cuir", "Mailles", "Fer", "Diamant"};
        for (int i = 0; i < 4; i++) {
            String m = mats[i];
            double z = -30.5 + i * 4;
            stand(9.5, z, 90, "eemirefuge_donne",
                    it("casque_" + m, 1), it("plastron_" + m, 1), it("jambieres_" + m, 1), it("bottes_" + m, 1), null);
            label(9.5, F + 2.5, z, "§fArmure : " + names[i], 0.6f);
        }

        // Centre : menu, munitions infinies, enclume décorative
        set(-3, F + 1, -22, Material.POLISHED_BLACKSTONE);
        button(-3, F + 2, -22, attached(Material.STONE_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.SOUTH),
                p -> plugin.openArmsMenu(p, 0));
        label(-2.5, F + 3.1, -21.5, "§fMenu des armes", 0.7f);
        set(3, F + 1, -22, Material.POLISHED_BLACKSTONE);
        leverKey = key(3, F + 2, -22);
        set(3, F + 2, -22, attached(Material.LEVER, FaceAttachable.AttachedFace.FLOOR, BlockFace.SOUTH));
        label(3.5, F + 3.1, -21.5, "§bMunitions infinies\n§7(levier)", 0.7f);
        set(0, F + 1, -24, facing(Material.ANVIL, BlockFace.EAST));
        label(0.5, F + 2.6, -23.5, "§6Améliorer : prends la pierre d'amélioration,\n§6clique avec elle sur l'arme dans ton inventaire.\n§dGraver : pareil avec une pierre de gravure.", 0.6f);
    }

    private ArmorStand stand(double x, double z, float yaw, String tag, ItemStack head, ItemStack chest, ItemStack legs,
                             ItemStack boots, ItemStack hand) {
        if (!place) return null;
        try {
            return w.spawn(new Location(w, x, F + 1, z, yaw, 0), ArmorStand.class, s -> {
                s.setArms(true);
                s.setBasePlate(false);
                s.getEquipment().setHelmet(head);
                s.getEquipment().setChestplate(chest);
                s.getEquipment().setLeggings(legs);
                s.getEquipment().setBoots(boots);
                if (hand != null) s.getEquipment().setItemInMainHand(hand);
                if (tag != null) s.addScoreboardTag(tag);
                decor(s);
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Porte-armure impossible : " + ex.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ arène (est)

    private void buildArena() {
        int x1 = 20, x2 = 64, z1 = -22, z2 = 22;
        room(x1, z1, x2, z2, 6, Material.STONE_BRICKS, Material.COARSE_DIRT, null);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++)
                if (((x * 31 + z * 17) & 7) == 0) set(x, F, z, Material.GRAVEL);
        fill(x1, F + 6, z1, x2, F + 6, z2, Material.AIR);
        for (int x = x1; x <= x2; x++) { set(x, F + 6, z1, Material.STONE_BRICK_WALL); set(x, F + 6, z2, Material.STONE_BRICK_WALL); }
        for (int z = z1; z <= z2; z++) { set(x1, F + 6, z, Material.STONE_BRICK_WALL); set(x2, F + 6, z, Material.STONE_BRICK_WALL); }
        for (int x = x1 + 4; x < x2; x += 8) { set(x, F + 4, z1, Material.SEA_LANTERN); set(x, F + 4, z2, Material.SEA_LANTERN); }
        // Entrée : portillons (les zombies ne les ouvrent pas et ne sautent pas par-dessus)
        for (int z = -1; z <= 1; z++) {
            fill(x1, F + 1, z, x1, F + 2, z, Material.AIR);
            set(x1, F + 1, z, facing(Material.OAK_FENCE_GATE, BlockFace.EAST));
        }
        // Bassin du Noyé (coin sud-est) : eau sur 4 de profondeur
        int px1 = 53, px2 = 61, pz1 = 12, pz2 = 20;
        fill(px1 - 1, F - 5, pz1 - 1, px2 + 1, F, pz2 + 1, Material.PRISMARINE_BRICKS);
        fill(px1, F - 4, pz1, px2, F - 4, pz2, Material.SAND);
        fill(px1, F - 3, pz1, px2, F, pz2, Material.WATER);
        pool = new Location(w, (px1 + px2) / 2.0 + 0.5, F - 2, (pz1 + pz2) / 2.0 + 0.5);
        label((px1 + px2) / 2.0 + 0.5, F + 3, pz1 - 1.5, "§3Bassin du Noyé", 0.8f);
        arenaMin = new Location(w, 42.5, F + 1, -18.5);
        arenaMax = new Location(w, 58.5, F + 1, 8.5);
        // Pupitre : deux comptoirs, boutons sur le dessus (normal devant, élite derrière)
        fill(24, F + 1, -13, 25, F + 1, 13, Material.POLISHED_DEEPSLATE);
        Zombies.Type[] types = Zombies.Type.values();
        for (int i = 0; i < types.length; i++) {
            Zombies.Type t = types[i];
            int z = -8 + i * 2;
            button(24, F + 2, z, attached(Material.STONE_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST),
                    p -> spawnButton(p, t, false));
            button(25, F + 2, z, attached(Material.POLISHED_BLACKSTONE_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST),
                    p -> spawnButton(p, t, true));
            label(25.0, F + 2.9, z + 0.5, "§f" + t.label, 0.55f);
        }
        label(24.5, F + 2.9, -10.5, "§7Devant : §fnormal\n§7Derrière : §6élite", 0.55f);
        button(24, F + 2, -12, attached(Material.OAK_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST), this::horde);
        label(24.5, F + 2.9, -12.5, "§cHorde de 10", 0.55f);
        button(25, F + 2, -12, attached(Material.OAK_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST), this::paradeButton);
        label(25.5, F + 3.5, -12.5, "§eDéfilé figé\n§7(un de chaque)", 0.55f);
        button(24, F + 2, 11, attached(Material.STONE_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST), p -> {
            plugin.zombies().setFrozen(!plugin.zombies().frozen);
            Txt.msg(p, plugin.zombies().frozen ? "Zombies §bfigés§7." : "Zombies §adéfigés§7.");
        });
        label(24.5, F + 2.9, 11.5, "§bFiger / défiger", 0.55f);
        button(24, F + 2, 13, attached(Material.STONE_BUTTON, FaceAttachable.AttachedFace.FLOOR, BlockFace.WEST), p -> {
            plugin.zombies().killAll();
            Txt.msg(p, "Tous les zombies sont retirés.");
        });
        label(24.5, F + 2.9, 13.5, "§4Tout tuer", 0.55f);
        label(22.5, F + 4.3, 0.5, "§c§lArène des zombies\n§7Un bouton = un zombie au fond de l'arène.\n§7Ils ne te repèrent qu'à 20 blocs : fais du bruit (cours, tire) pour les attirer.\n§7Ton bruit et les dégâts s'affichent en bas de l'écran.", 0.8f);
        for (int i = 0; i < types.length; i++) {
            parade.add(new Location(w, 48.5, F + 1, -16.5 + i * 4, 90, 0));
            parade.add(new Location(w, 52.5, F + 1, -16.5 + i * 4, 90, 0));
        }
    }

    private void spawnButton(Player p, Zombies.Type t, boolean elite) {
        Location loc = t == Zombies.Type.NOYE ? pool.clone() : plugin.zombies().randomSpawn(arenaMin, arenaMax);
        if (plugin.zombies().spawn(loc, t, elite) != null)
            Txt.msg(p, (elite ? "§6" + t.label + " élite" : "§f" + t.label) + " §7apparaît" + (plugin.zombies().frozen ? " (figé)." : "."));
    }

    private void horde(Player p) {
        Zombies.Type[] pool10 = {Zombies.Type.RODEUR, Zombies.Type.RODEUR, Zombies.Type.RODEUR, Zombies.Type.RODEUR,
                Zombies.Type.COUREUR, Zombies.Type.COUREUR, Zombies.Type.CRACHEUR, Zombies.Type.HURLEUR, Zombies.Type.BRUTE,
                Zombies.Type.BLINDE};
        for (Zombies.Type t : pool10) plugin.zombies().spawn(plugin.zombies().randomSpawn(arenaMin, arenaMax), t, false);
        Txt.msg(p, "§cUne horde de 10 zombies arrive !");
    }

    private void paradeButton(Player p) {
        plugin.zombies().killAll();
        plugin.zombies().setFrozen(true);
        Zombies.Type[] types = Zombies.Type.values();
        for (int i = 0; i < types.length; i++) {
            plugin.zombies().spawn(parade.get(2 * i), types[i], false);
            plugin.zombies().spawn(parade.get(2 * i + 1), types[i], true);
        }
        Txt.msg(p, "Défilé : chaque zombie (normal devant, élite derrière), §bfigés§7. Bouton « Figer / défiger » pour les lâcher.");
    }

    // ------------------------------------------------------------------ stand de tir (sud)

    private void buildRange() {
        room(-7, 9, 7, 58, 5, Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, null);
        fill(-2, F + 1, 9, 2, F + 3, 9, Material.AIR);
        fill(-6, F + 1, 15, 6, F + 1, 15, Material.SMOOTH_STONE_SLAB);
        fill(-6, F, 16, 6, F, 57, Material.GRASS_BLOCK);
        int[][] targets = {{-4, 10}, {0, 20}, {4, 40}};
        for (int[] t : targets) {
            int x = t[0], dist = t[1];
            int z = 13 + dist;
            fill(x - 1, F + 1, z + 2, x + 1, F + 3, z + 2, Material.TARGET);
            label(x + 0.5, F + 2.4, 15.5, "§e" + dist + " m", 0.8f);
            if (place) {
                try {
                    w.spawn(new Location(w, x + 0.5, F + 1, z + 0.5, 180, 0), Mannequin.class, m -> {
                        m.setImmovable(true);
                        m.setDescription(null);
                        m.customName(Txt.c("§fCible " + dist + " m"));
                        m.setCustomNameVisible(false);
                        m.getEquipment().setChestplate(colored(Material.LEATHER_CHESTPLATE, 0xB02E26));
                        m.getEquipment().setHelmet(colored(Material.LEATHER_HELMET, 0xF9FFFE));
                        m.getPersistentDataContainer().set(plugin.armes().keyCible(), PersistentDataType.INTEGER, dist);
                        decor(m);
                    });
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Cible impossible : " + ex.getMessage());
                }
                TextDisplay td = label(x + 0.5, F + 3.3, z + 0.5, "§fCible " + dist + " m\n§7pas encore touchée", 0.7f);
                if (td != null) td.addScoreboardTag("eemirefuge_ciblelabel");
            }
        }
        label(0.5, F + 4.2, 12.5, "§e§lStand de tir\n§7Tête = haut du corps (25 %). Les cibles ne meurent pas.", 0.8f);
    }

    private static ItemStack colored(Material m, int rgb) {
        ItemStack i = new ItemStack(m);
        if (i.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(rgb));
            i.setItemMeta(lm);
        }
        return i;
    }

    // ------------------------------------------------------------------ meubles, fouille et galerie (ouest)

    static String woodName(Material m) {
        String n = m.name().replace("_SHELF", "");
        return switch (n) {
            case "OAK" -> "Chêne";
            case "SPRUCE" -> "Sapin";
            case "BIRCH" -> "Bouleau";
            case "JUNGLE" -> "Acajou";
            case "ACACIA" -> "Acacia";
            case "DARK_OAK" -> "Chêne noir";
            case "MANGROVE" -> "Palétuvier";
            case "CHERRY" -> "Cerisier";
            case "PALE_OAK" -> "Chêne pâle";
            case "BAMBOO" -> "Bambou";
            case "CRIMSON" -> "Carmin";
            case "WARPED" -> "Biscornu";
            case "POPLAR" -> "Peuplier";
            default -> n.toLowerCase().replace('_', ' ');
        };
    }

    static List<Material> shelfMaterials() {
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.endsWith("_SHELF") && !n.contains("BOOKSHELF") && !n.startsWith("LEGACY_") && !m.isLegacy()) list.add(m);
        }
        return list;
    }

    private void buildFurniture() {
        int x1 = -66, x2 = -12, z1 = -25, z2 = 24;
        room(x1, z1, x2, z2, 7, Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.DARK_OAK_PLANKS);
        for (int x = x1 + 3; x < x2; x += 6)
            for (int z = z1 + 3; z < z2; z += 6) set(x, F + 8, z, Material.SEA_LANTERN);
        // Porte d'entrée (bois, bruit 6)
        fill(x2, F + 1, -1, x2, F + 2, 1, Material.SPRUCE_PLANKS);
        door(x2, 0, BlockFace.WEST);
        label(x2 - 0.5, F + 3.4, 0.5, "§bMeubles et fouille", 0.7f);
        label(-40.5, F + 6.6, -20.5, "§b§lSupports de fouille §7(comportement vanilla : prends, casse, fouille)\n§7Bouton près de la porte : tout remettre en place.", 0.8f);

        // Bouton « Remettre les objets »
        button(x2 - 1, F + 2, -3, attached(Material.OAK_BUTTON, FaceAttachable.AttachedFace.WALL, BlockFace.WEST), p -> {
            refill();
            Txt.msg(p, "Supports de fouille, barricade, vitres et coffre-fort remis en place.");
        });
        label(x2 - 0.6, F + 2.9, -2.5, "§aRemettre les objets", 0.6f);

        // -- Étagères de chaque bois, mur nord
        List<Material> shelves = shelfMaterials();
        List<ItemStack> pool = List.of(it("conserve", 1), it("bandage", 2), it("balle_pistolet", 4), it("ferraille", 6),
                it("piece_lame", 1), v(Material.BREAD, 2), v(Material.APPLE, 3), it("couteau_lancer", 2), v(Material.GLASS_BOTTLE, 1),
                it("piece_ressort", 1), v(Material.GUNPOWDER, 3), it("conserve", 2));
        for (int i = 0; i < shelves.size(); i++) {
            int x = -64 + 2 * i;
            int idx = i;
            Material sm = shelves.get(i);
            set(x, F + 1, -24, facing(sm, BlockFace.SOUTH));
            label(x + 0.5, F + 2.45, -23.4, "§f" + woodName(sm), 0.45f);
            refills.add(() -> {
                List<ItemStack> c = new ArrayList<>();
                // ~40 % de cases occupées en moyenne : 1 ou 2 objets sur 3 cases
                c.add(idx % 3 == 0 ? pool.get(idx % pool.size()) : null);
                c.add(pool.get((idx + 4) % pool.size()));
                c.add(idx % 2 == 0 ? null : pool.get((idx + 7) % pool.size()));
                fillInventory(x, F + 1, -24, c);
            });
        }
        // -- Contenants
        int cx = -64 + 2 * shelves.size() + 2;
        cx = Math.max(cx, -30);
        container(cx, -24, Material.CHEST, "Coffre", List.of(it("conserve", 3), it("bandage", 2), it("ferraille", 8),
                it("piece_ressort", 1), it("fleches", 12), v(Material.BREAD, 2)));
        container(cx + 2, -24, Material.COPPER_CHEST, "Coffre en cuivre", List.of(it("balle_pistolet", 6), it("piece_mecanisme", 1),
                it("ferraille", 4)));
        container(cx + 4, -24, Material.BARREL, "Tonneau", List.of(it("conserve", 1), v(Material.APPLE, 3), v(Material.BEEF, 2)));
        container(cx + 6, -24, Material.BARREL, "Tonneau", List.of(it("piece_lame", 1), it("piece_canon", 1), v(Material.GUNPOWDER, 4)));
        pot(cx + 8, -24, it("bandage", 1), "Pot décoré");
        pot(cx + 10, -24, it("fusee_appat", 1), "Pot décoré");
        pot(cx + 12, -24, it("cle_coffre_fort", 1), "Pot décoré §7(clé)");

        // -- Rangée du milieu (z -16)
        int z = -16;
        set(-65, F + 1, z, facing(Material.CHISELED_BOOKSHELF, BlockFace.EAST));
        label(-64.3, F + 2.5, z + 0.5, "§fBibliothèque ciselée", 0.5f);
        refills.add(() -> chiseled(-65, F + 1, z));
        set(-60, F + 1, z, facing(Material.LECTERN, BlockFace.SOUTH));
        label(-59.5, F + 2.6, z + 0.5, "§fPupitre", 0.5f);
        refills.add(() -> lectern(-60, F + 1, z));
        fill(-57, F, z - 1, -55, F, z + 1, Material.COBBLESTONE);
        set(-56, F + 1, z, facing(Material.CAMPFIRE, BlockFace.SOUTH));
        label(-55.5, F + 2.6, z + 0.5, "§fFeu de camp", 0.5f);
        refills.add(() -> campfire(-56, F + 1, z));
        brushable(-52, z, Material.SUSPICIOUS_SAND, it("balle_pistolet", 3), "Sable suspect");
        brushable(-50, z, Material.SUSPICIOUS_GRAVEL, it("piece_ressort", 1), "Gravier suspect");
        label(-50.5, F + 3.2, z + 0.5, "§7(au pinceau)", 0.45f);
        // porte-armures habillés (vanilla : on prend la pièce)
        refills.add(() -> dressStands());
        if (place) {
            stand(-45.5, z + 0.5, 0, "eemirefuge_stand_a", null, null, null, null, null);
            stand(-42.5, z + 0.5, 0, "eemirefuge_stand_b", null, null, null, null, null);
        }
        label(-44, F + 3.2, z + 0.5, "§fPorte-armures habillés", 0.5f);
        // cadres sur un petit mur
        fill(-40, F + 1, z, -36, F + 3, z, Material.STRIPPED_OAK_LOG);
        if (place) {
            frame(-39, F + 2, z + 1, BlockFace.SOUTH, null, false, false, "eemirefuge_cadre_a");
            frame(-37, F + 2, z + 1, BlockFace.SOUTH, null, true, false, "eemirefuge_cadre_b");
        }
        refills.add(() -> {
            for (Entity e : w.getNearbyEntities(new Location(w, -38, F + 2, z + 1), 3, 2, 2)) {
                if (e instanceof ItemFrame f && f.getScoreboardTags().contains("eemirefuge_cadre_a")) f.setItem(it("hache", 1), false);
                if (e instanceof ItemFrame f && f.getScoreboardTags().contains("eemirefuge_cadre_b")) f.setItem(it("pistolet", 1), false);
            }
        });
        label(-37.5, F + 4.3, z + 1.5, "§fCadre et cadre lumineux", 0.5f);
        // cadavre
        if (place) {
            try {
                w.spawn(new Location(w, -31.5, F + 1, z + 0.5, 90, 0), Mannequin.class, m -> {
                    m.setImmovable(true);
                    m.setInvulnerable(true);
                    m.setDescription(null);
                    m.setCustomNameVisible(false);
                    Pose pose = Mannequin.validPoses().contains(Pose.SLEEPING) ? Pose.SLEEPING
                            : Mannequin.validPoses().contains(Pose.SWIMMING) ? Pose.SWIMMING : Pose.STANDING;
                    m.setPose(pose, true);
                    m.getEquipment().setChestplate(colored(Material.LEATHER_CHESTPLATE, 0x3C5A8A));
                    m.getEquipment().setLeggings(colored(Material.LEATHER_LEGGINGS, 0x4A3B2A));
                    m.addScoreboardTag("eemirefuge_cadavre");
                    decor(m);
                });
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Cadavre impossible : " + ex.getMessage());
            }
        }
        label(-31.5, F + 2.2, z + 0.5, "§fCadavre §7(clic droit : fouiller)", 0.5f);
        // coffre-fort
        int sx = -27;
        // 0.1.3 : vault vanilla (retexturé en coffre-fort à molette par le pack), contenu dans un inventaire virtuel
        set(sx, F + 1, z, facing(Material.VAULT, BlockFace.SOUTH));
        safeKey = key(sx, F + 1, z);
        safePos = new int[]{sx, F + 1, z};
        refills.add(() -> fillSafe(List.of(it("balle_carabine", 5), it("gravure_saignement", 1),
                it("piece_canon", 1), v(Material.GOLD_INGOT, 2))));
        label(sx + 0.5, F + 3.2, z + 0.5, "§6Coffre-fort\n§7clé (pot) ou forcer : accroupi + clic droit 5 s", 0.5f);

        // -- Pièce fermée : porte barricadée + vitres
        int rx = -55;
        for (int y = F + 1; y <= F + 7; y++) {
            for (int zz = -7; zz <= 5; zz++) set(rx, y, zz, Material.SPRUCE_PLANKS);
            for (int xx = x1; xx <= rx; xx++) { set(xx, y, -7, Material.SPRUCE_PLANKS); set(xx, y, 5, Material.SPRUCE_PLANKS); }
        }
        // 0.1.3 : planches de chêne pâle = barricade clouée (pack Refuge)
        barricade.put(key(rx, F + 1, -1), Material.PALE_OAK_PLANKS);
        barricade.put(key(rx, F + 2, -1), Material.PALE_OAK_PLANKS);
        refills.add(() -> {
            set(rx, F + 1, -1, Material.PALE_OAK_PLANKS);
            set(rx, F + 2, -1, Material.PALE_OAK_PLANKS);
        });
        label(rx + 0.6, F + 3.4, -0.5, "§6Porte barricadée\n§73 coups de hache (bruit 16)", 0.5f);
        for (int zz : new int[]{-4, 2}) {
            glass.add(new long[]{rx, F + 2, zz, 1});
            refills.add(() -> glassPane(rx, F + 2, zz));
        }
        label(rx + 0.6, F + 3.4, 2.5, "§bVitre §7(la casser : bruit 16)", 0.5f);
        container(-63, -1, Material.CHEST, null, List.of(it("tromblon", 1), it("cartouche_tromblon", 4)));
        label(-62.5, F + 2.6, -0.5, "§fPièce fermée", 0.5f);

        buildRetextures();

        // -- Galerie du pack (mur sud) : chaque objet du pack sur chaque support
        List<Catalogue.Def> customs = new ArrayList<>();
        for (Catalogue.Def d : Catalogue.all()) if (d.custom) customs.add(d);
        label(-40, F + 6.6, 15.5, "§b§lGalerie du pack Refuge\n§7Par colonne : étagère, pot, cadre, cadre lumineux, au sol, en main (mannequin).\n§7Clic droit sur une étagère, un cadre ou un mannequin : tu reçois l'objet.", 0.8f);
        for (int i = 0; i < customs.size(); i++) {
            Catalogue.Def d = customs.get(i);
            int x = -64 + 2 * i;
            if (x > x2 - 2) break;
            ItemStack item = it(d.id, 1);
            set(x, F + 1, 23, facing(Material.OAK_SHELF, BlockFace.NORTH));
            galleryShelves.put(key(x, F + 1, 23), item);
            refills.add(() -> fillInventory(x, F + 1, 23, List.of(item, item, item)));
            set(x, F + 2, 23, Material.DECORATED_POT);
            refills.add(() -> potItem(x, F + 2, 23, item));
            if (place) {
                frame(x, F + 3, 23, BlockFace.NORTH, item, false, true, "eemirefuge_donne");
                frame(x, F + 4, 23, BlockFace.NORTH, item, true, true, "eemirefuge_donne");
                try {
                    w.spawn(new Location(w, x + 0.5, F + 1.05, 20.5), Item.class, ent -> {
                        ent.setItemStack(item);
                        ent.setUnlimitedLifetime(true);
                        ent.setCanPlayerPickup(false);
                        ent.setCanMobPickup(false);
                        ent.setPickupDelay(Integer.MAX_VALUE);
                        ent.setVelocity(new org.bukkit.util.Vector());
                        decor(ent);
                    });
                    w.spawn(new Location(w, x + 0.5, F + 1, 18.5, 180, 0), Mannequin.class, m -> {
                        m.setImmovable(true);
                        m.setInvulnerable(true);
                        m.setDescription(null);
                        m.setCustomNameVisible(false);
                        m.getEquipment().setItemInMainHand(item);
                        m.addScoreboardTag("eemirefuge_donne");
                        decor(m);
                    });
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("Galerie : " + d.id + " : " + ex.getMessage());
                }
            }
            label(x + 0.5, F + 5.75, 22.6, d.color + d.name, 0.4f);
        }
    }

    /** 0.1.3 : un exemplaire de chaque bloc vanilla retexturé par le pack Refuge, avec son étiquette (z 9 et 12). */
    private void buildRetextures() {
        label(-36.5, F + 4.4, 10.5, "§b§lBlocs retexturés par le pack Refuge\n§7Sans le pack : blocs vanilla. Étagères, coffres, pots, vault : rangée du fond.", 0.7f);
        String[][] row = {
                {"LODESTONE", "Magnétite\n§7→ poste radio"}, {"TARGET", "Cible\n§7→ balise"},
                {"REDSTONE_BLOCK", "Bloc de redstone\n§7→ générateur"}, {"PALE_OAK_PLANKS", "Chêne pâle\n§7→ barricade"},
                {"BARREL", "Tonneau\n§7→ caisse"}, {"GLASS", "Verre"}, {"GLASS_PANE", "Vitre"},
                {"RED_STAINED_GLASS", "Verre teinté"}, {"LIGHT_BLUE_STAINED_GLASS_PANE", "Vitre teintée"},
                {"LANTERN", "Lanterne\n§7→ ampoule"}, {"SOUL_LANTERN", "Lanterne des âmes"}, {"GRASS_BLOCK", "Herbe"},
                {"OAK_LEAVES", "Feuilles"}, {"BRICKS", "Briques"}, {"STONE_BRICKS", "Briques de pierre"},
                {"MOSSY_STONE_BRICKS", "Briques moussues"}, {"CRACKED_STONE_BRICKS", "Briques craquelées"}};
        for (int i = 0; i < row.length; i++) exhibit(-52 + 2 * i, 9, row[i][0], row[i][1]);
        String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK",
                "BAMBOO", "CRIMSON", "WARPED", "POPLAR"};
        for (int i = 0; i < woods.length; i++) {
            Material shelf = Material.matchMaterial(woods[i] + "_SHELF");
            exhibit(-52 + 2 * i, 12, woods[i] + "_PLANKS", "Planches\n§7" + (shelf != null ? woodName(shelf) : woods[i].toLowerCase()));
        }
    }

    private void exhibit(int x, int z, String material, String name) {
        Material m = Material.matchMaterial(material);
        if (m == null || !m.isBlock()) {
            plugin.getLogger().warning("Bloc inconnu pour la vitrine : " + material);
            return;
        }
        try {
            BlockData d = m.createBlockData();
            if (d instanceof Leaves l) l.setPersistent(true);
            if (d instanceof MultipleFacing mf && mf.getAllowedFaces().contains(BlockFace.EAST)) {
                mf.setFace(BlockFace.EAST, true);
                mf.setFace(BlockFace.WEST, true);
            }
            set(x, F + 1, z, d);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Vitrine : " + material + " : " + ex.getMessage());
        }
        label(x + 0.5, F + 2.55, z + 0.5, "§f" + name, 0.45f);
    }

    /** Remplit l'inventaire virtuel du coffre-fort (créé au premier appel). */
    private void fillSafe(List<ItemStack> content) {
        if (safeInv == null) safeInv = Bukkit.createInventory(null, 27, Txt.c("§8Coffre-fort"));
        safeInv.clear();
        for (int i = 0; i < content.size() && i < safeInv.getSize(); i++) safeInv.setItem(i, content.get(i).clone());
    }

    /** Le vault vanilla fait défiler des objets dans sa cage : inutile ici (texture opaque du coffre-fort). */
    @EventHandler(ignoreCancelled = true)
    public void onVaultDisplay(VaultDisplayItemEvent e) {
        if (here(e.getBlock().getWorld())) e.setCancelled(true);
    }

    private void door(int x, int z, BlockFace facing) {
        if (!place) return;
        try {
            Door lower = (Door) Material.OAK_DOOR.createBlockData();
            lower.setFacing(facing);
            lower.setHalf(Bisected.Half.BOTTOM);
            Door upper = (Door) lower.clone();
            upper.setHalf(Bisected.Half.TOP);
            set(x, F + 1, z, lower);
            set(x, F + 2, z, upper);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Porte impossible : " + ex.getMessage());
        }
    }

    private void container(int x, int z, Material m, String name, List<ItemStack> content) {
        set(x, F + 1, z, facing(m, BlockFace.SOUTH));
        if (name != null) label(x + 0.5, F + 2.5, z + 0.6, "§f" + name, 0.5f);
        refills.add(() -> fillInventory(x, F + 1, z, content));
    }

    private void pot(int x, int z, ItemStack item, String name) {
        set(x, F + 1, z, Material.DECORATED_POT);
        label(x + 0.5, F + 2.6, z + 0.6, "§f" + name, 0.5f);
        refills.add(() -> potItem(x, F + 1, z, item));
    }

    private void brushable(int x, int z, Material m, ItemStack item, String name) {
        set(x, F + 1, z, m);
        label(x + 0.5, F + 2.5, z + 0.5, "§f" + name, 0.5f);
        refills.add(() -> {
            set(x, F + 1, z, m);
            if (w.getBlockAt(x, F + 1, z).getState() instanceof BrushableBlock bb) {
                bb.setItem(item);
                bb.update(true, false);
            }
        });
    }

    private void glassPane(int x, int y, int z) {
        try {
            BlockData d = Material.GLASS_PANE.createBlockData();
            if (d instanceof MultipleFacing mf) {
                mf.setFace(BlockFace.NORTH, true);
                mf.setFace(BlockFace.SOUTH, true);
            }
            set(x, y, z, d);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Vitre impossible : " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ remplissage des supports

    /** Remplit l'inventaire d'un bloc (coffre, tonneau, étagère...) par l'état « snapshot » puis update. */
    private void fillInventory(int x, int y, int z, List<ItemStack> content) {
        Block b = w.getBlockAt(x, y, z);
        BlockState st = b.getState();
        Inventory inv = null;
        if (st instanceof Shelf s) inv = s.getSnapshotInventory();
        else if (st instanceof Container c) inv = c.getSnapshotInventory();
        if (inv == null) {
            plugin.getLogger().warning("Pas d'inventaire en " + x + " " + y + " " + z + " (" + b.getType() + ").");
            return;
        }
        inv.clear();
        for (int i = 0; i < content.size() && i < inv.getSize(); i++) {
            ItemStack c = content.get(i);
            inv.setItem(i, c == null ? null : c.clone());
        }
        st.update(true, false);
    }

    private void potItem(int x, int y, int z, ItemStack item) {
        if (w.getBlockAt(x, y, z).getState() instanceof DecoratedPot pot) {
            pot.getSnapshotInventory().setItem(item.clone());
            pot.update(true, false);
        }
    }

    private void chiseled(int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (!(b.getState() instanceof org.bukkit.block.ChiseledBookshelf cb)) return;
        Inventory inv = cb.getSnapshotInventory();
        inv.clear();
        inv.setItem(0, v(Material.BOOK, 1));
        inv.setItem(2, book("Rapport n°3", "Gendarmerie", "La pluie noire est tombée vers 2 h.", "Les morts se sont relevés le lendemain."));
        inv.setItem(3, v(Material.WRITABLE_BOOK, 1));
        inv.setItem(5, v(Material.ENCHANTED_BOOK, 1));
        cb.update(true, false);
        if (b.getBlockData() instanceof ChiseledBookshelf data) {
            for (int i = 0; i < 6; i++) data.setSlotOccupied(i, i == 0 || i == 2 || i == 3 || i == 5);
            b.setBlockData(data, false);
        }
    }

    private void lectern(int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        if (!(b.getState() instanceof Lectern l)) return;
        l.getSnapshotInventory().setItem(0, book("Carnet de route", "Un survivant", "Jour 3. Le Refuge tient encore.",
                "Jour 4. Ne jamais tirer près de l'église : le bruit les attire tous."));
        l.update(true, false);
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Lectern ld) {
            ld.setHasBook(true);
            b.setBlockData(ld, false);
        }
    }

    private static ItemStack book(String title, String author, String... pages) {
        ItemStack b = new ItemStack(Material.WRITTEN_BOOK);
        if (b.getItemMeta() instanceof BookMeta bm) {
            bm.setTitle(title);
            bm.setAuthor(author);
            bm.setPages(pages);
            b.setItemMeta(bm);
        }
        return b;
    }

    private void campfire(int x, int y, int z) {
        if (!(w.getBlockAt(x, y, z).getState() instanceof Campfire c)) return;
        Material[] meats = {Material.BEEF, Material.PORKCHOP, Material.MUTTON, Material.CHICKEN};
        for (int i = 0; i < Math.min(c.getSize(), meats.length); i++) {
            c.setItem(i, new ItemStack(meats[i]));
            c.setCookTime(i, 0);
            c.setCookTimeTotal(i, 20 * 60 * 60 * 24); // ne cuit jamais pendant l'essai
        }
        c.update(true, false);
    }

    private void dressStands() {
        for (Entity e : w.getNearbyEntities(new Location(w, -44, F + 1, -15.5), 4, 3, 3)) {
            if (!(e instanceof ArmorStand s)) continue;
            if (s.getScoreboardTags().contains("eemirefuge_stand_a")) {
                s.getEquipment().setHelmet(it("casque_fer", 1));
                s.getEquipment().setChestplate(it("plastron_fer", 1));
                s.getEquipment().setLeggings(it("jambieres_cuir", 1));
                s.getEquipment().setBoots(null);
                s.getEquipment().setItemInMainHand(it("epee_fer", 1));
            } else if (s.getScoreboardTags().contains("eemirefuge_stand_b")) {
                s.getEquipment().setHelmet(it("casque_cuir", 1));
                s.getEquipment().setChestplate(it("plastron_maille", 1));
                s.getEquipment().setLeggings(it("jambieres_maille", 1));
                s.getEquipment().setBoots(it("bottes_maille", 1));
                s.getEquipment().setItemInMainHand(null);
            }
        }
    }

    // ------------------------------------------------------------------ interactions

    private boolean here(World world) {
        return plugin.inEssai(world);
    }

    /** Bloc interactif de la salle (bouton, levier, étagère d'armurerie ou de galerie, coffre-fort). */
    public boolean isInteractive(Block b) {
        long k = key(b);
        return buttons.containsKey(k) || k == leverKey || armoryShelves.containsKey(k) || galleryShelves.containsKey(k) || k == safeKey;
    }

    public boolean infiniteFromLever() {
        if (w == null) return false;
        int x = (int) (leverKey >> 38), z = (int) (leverKey << 26 >> 38), y = (int) (leverKey & 0xFFF);
        if (x >= 1 << 25) x -= 1 << 26;
        Block b = w.getBlockAt(x, y, z);
        return b.getBlockData() instanceof Powerable pw && pw.isPowered();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null || !here(b.getWorld()) || e.getHand() != EquipmentSlot.HAND) return;
        long k = key(b);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (barricade.containsKey(k) && barricade.get(k) == b.getType()) hitBarricade(p, b);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Consumer<Player> action = buttons.get(k);
        if (action != null) {
            action.accept(p);
            return;
        }
        if (k == leverKey) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.setInfiniteAmmo(infiniteFromLever());
                Txt.msg(p, "Munitions infinies : " + (plugin.infiniteAmmo() ? "§aactivées" : "§cdésactivées") + "§7.");
            });
            return;
        }
        List<ItemStack> armory = armoryShelves.get(k);
        if (armory != null) {
            e.setCancelled(true);
            for (ItemStack i : armory) give(p, i);
            return;
        }
        ItemStack gal = galleryShelves.get(k);
        if (gal != null) {
            e.setCancelled(true);
            give(p, gal);
            return;
        }
        if (k == safeKey) {
            e.setCancelled(true); // jamais le comportement vanilla du vault (clé des épreuves, butin éjecté)
            if (!safeLocked) {
                if (safeInv == null) fillSafe(List.of());
                p.openInventory(safeInv);
                p.playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.3f);
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if ("cle_coffre_fort".equals(Catalogue.id(hand))) {
                safeLocked = false;
                p.playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1.2f);
                Txt.msg(p, "Coffre-fort déverrouillé avec la clé. Clique à nouveau pour l'ouvrir.");
            } else if (p.isSneaking()) {
                startForcing(p, b);
            } else {
                p.playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.8f);
                Txt.msg(p, "Verrouillé. Il faut la clé (dans un pot), ou forcer : accroupi + clic droit, 5 s, bruyant.");
            }
            return;
        }
        String type = b.getType().name();
        if (type.endsWith("_DOOR") || type.endsWith("_TRAPDOOR") || type.endsWith("_FENCE_GATE"))
            plugin.bruit().emit(b.getLocation(), 6, p);
    }

    private void startForcing(Player p, Block b) {
        if (forcing.containsKey(p.getUniqueId())) return;
        final int[] t = {0};
        forcing.put(p.getUniqueId(), 0);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || !p.isSneaking() || p.getLocation().distanceSquared(b.getLocation().add(0.5, 0, 0.5)) > 9 || !safeLocked) {
                forcing.remove(p.getUniqueId());
                if (safeLocked && p.isOnline()) plugin.bruit().hitMessage(p, "§cForçage interrompu.");
                task.cancel();
                return;
            }
            t[0] += 5;
            int pct = Math.min(100, t[0] * 100 / 100);
            plugin.bruit().hitMessage(p, "§eForçage du coffre-fort : " + pct + " %");
            p.getWorld().playSound(b.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
            if (t[0] % 20 == 0) plugin.bruit().emit(b.getLocation(), 16, p);
            if (t[0] >= 100) {
                safeLocked = false;
                forcing.remove(p.getUniqueId());
                p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.8f);
                plugin.bruit().hitMessage(p, "§aCoffre-fort forcé ! Clique pour l'ouvrir.");
                task.cancel();
            }
        }, 0L, 5L);
    }

    private void hitBarricade(Player p, Block b) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!Tag.ITEMS_AXES.isTagged(hand.getType())) {
            plugin.bruit().hitMessage(p, "§7Il faut une hache pour casser la barricade.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - barricadeLastHit < 350) return; // un coup à la fois
        if (now - barricadeLastHit > 5000) barricadeHits = 0;
        barricadeLastHit = now;
        barricadeHits++;
        w.playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 1.1f);
        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0, b.getBlockData());
        plugin.bruit().emit(b.getLocation(), 6, p);
        if (barricadeHits >= 3) {
            breakBarricade();
            plugin.bruit().emit(b.getLocation(), 16, p);
            plugin.bruit().hitMessage(p, "§6Barricade cassée ! §7(bruit 16)");
        } else {
            plugin.bruit().hitMessage(p, "§6Barricade : coup " + barricadeHits + "/3");
        }
    }

    private void breakBarricade() {
        boolean any = false;
        for (Map.Entry<Long, Material> en : barricade.entrySet()) {
            long k = en.getKey();
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k & 0xFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == en.getValue()) {
                b.setType(Material.AIR, false);
                any = true;
            }
        }
        if (any && !barricade.isEmpty()) {
            long k = barricade.keySet().iterator().next();
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k & 0xFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            w.playSound(new Location(w, x, y, z), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
        }
        barricadeHits = 0;
    }

    /** Une Brute qui reste 3 s collée à la barricade l'enfonce (appelé toutes les 10 ticks). */
    void bruteNear(Mob m, Zombies.State s) {
        if (barricade.isEmpty() || w == null || !m.getWorld().equals(w)) return;
        long k = barricade.keySet().iterator().next();
        int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k & 0xFFF);
        if (x >= 1 << 25) x -= 1 << 26;
        if (w.getBlockAt(x, y, z).getType() != barricade.get(k)) return;
        if (m.getLocation().distanceSquared(new Location(w, x + 0.5, y, z + 0.5)) > 2.5 * 2.5) {
            s.barricadeTicks = 0;
            return;
        }
        if (++s.barricadeTicks >= 6) {
            breakBarricade();
            plugin.bruit().emit(new Location(w, x, y, z), 16, null);
            s.barricadeTicks = 0;
        }
    }

    /** Une balle traverse une vitre de la salle. */
    void shootGlass(Player p, Block b) {
        if (!isGlass(b)) return;
        breakGlass(p, b);
    }

    private boolean isGlass(Block b) {
        for (long[] g : glass) if (g[0] == b.getX() && g[1] == b.getY() && g[2] == b.getZ()) return true;
        return false;
    }

    private void breakGlass(Player p, Block b) {
        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0, b.getBlockData());
        b.setType(Material.AIR, false);
        w.playSound(b.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1f);
        plugin.bruit().emit(b.getLocation(), 16, p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!here(b.getWorld())) return;
        Player p = e.getPlayer();
        if (isGlass(b) && b.getType() == Material.GLASS_PANE) {
            e.setCancelled(true);
            breakGlass(p, b);
            return;
        }
        if (barricade.containsKey(key(b))) {
            e.setCancelled(true);
            hitBarricade(p, b);
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (here(e.getBlock().getWorld()) && e.getPlayer().getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (here(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (here(e.getEntity().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecorDamage(EntityDamageEvent e) {
        Entity en = e.getEntity();
        if (!here(en.getWorld()) || !en.getScoreboardTags().contains("eemirefuge_decor")) return;
        if (plugin.armes().isCible(en)) return; // géré par Armes
        if (en instanceof ItemFrame) return;    // un coup sur un cadre de fouille fait tomber l'objet (vanilla)
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityClick(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!here(en.getWorld()) || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (en.getScoreboardTags().contains("eemirefuge_donne")) {
            e.setCancelled(true);
            if (en instanceof ItemFrame f && f.getItem().getType() != Material.AIR) give(p, f.getItem());
            else if (en instanceof Mannequin m) give(p, m.getEquipment().getItemInMainHand());
            else if (en instanceof ArmorStand s) giveStand(p, s);
            return;
        }
        if (en.getScoreboardTags().contains("eemirefuge_cadavre")) {
            e.setCancelled(true);
            openCorpse(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        ArmorStand s = e.getRightClicked();
        if (!here(s.getWorld()) || !s.getScoreboardTags().contains("eemirefuge_donne")) return;
        e.setCancelled(true);
        giveStand(e.getPlayer(), s);
    }

    private void giveStand(Player p, ArmorStand s) {
        for (ItemStack i : s.getEquipment().getArmorContents()) if (i != null && i.getType() != Material.AIR) give(p, i);
    }

    static final class CorpseHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private void openCorpse(Player p) {
        CorpseHolder h = new CorpseHolder();
        Inventory inv = Bukkit.createInventory(h, 9, Txt.c("§8Sac d'un survivant"));
        h.inv = inv;
        inv.setItem(1, it("conserve", 2));
        inv.setItem(3, it("bandage", 1));
        inv.setItem(4, it("balle_pistolet", 3));
        inv.setItem(6, v(Material.CLOCK, 1));
        inv.setItem(7, it("ferraille", 5));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.8f);
    }

    static void give(Player p, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Integer, ItemStack> rest = p.getInventory().addItem(item.clone());
        for (ItemStack r : rest.values()) p.getWorld().dropItem(p.getLocation(), r);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }
}
