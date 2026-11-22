package fr.eemicraft.refuge;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue central de tous les objets du mode. Chaque objet porte dans son PDC :
 * eemirefuge:id, eemirefuge:level (0..5), eemirefuge:engrave (liste séparée par des virgules).
 * Les objets sans équivalent vanilla ont item_model refuge:&lt;id&gt; (pack RefugePack).
 */
public final class Catalogue {

    public enum Kind { MELEE, BOW, GUN, AMMO, THROW, ARMOR, ENGRAVING, TOOL, MISC }

    /** Raretés aux couleurs de Minecraft. */
    public static final String COMMUN = "§f", PEU_COMMUN = "§a", RARE = "§9", EPIQUE = "§5", LEGENDAIRE = "§6";

    public static final class Def {
        public final String id, name, color;
        public final Material base;
        public final Kind kind;
        public final boolean custom;   // item_model refuge:<id>
        public final int stack;
        public final List<String> lore;
        public double damage;          // dégâts de base affichés (corps à corps, arme à feu par plomb, lancer)

        Def(String id, Material base, String color, String name, Kind kind, boolean custom, int stack, String... lore) {
            this.id = id;
            this.base = base;
            this.color = color;
            this.name = name;
            this.kind = kind;
            this.custom = custom;
            this.stack = stack;
            this.lore = Arrays.asList(lore);
        }

        public boolean upgradable() {
            return kind == Kind.MELEE || kind == Kind.BOW || kind == Kind.GUN || kind == Kind.ARMOR;
        }

        public boolean engravable() {
            return kind == Kind.MELEE;
        }
    }

    /** Données des armes à feu (section 7.3). */
    public record Gun(String ammo, double damage, int pellets, double spreadDeg, int magazine, int reloadTicks,
                      int fireTicks, double range, double headMult, int noise, double recoil) { }

    public static final Map<String, Gun> GUNS = Map.of(
            "pistolet", new Gun("balle_pistolet", 10, 1, 0.8, 6, 40, 6, 48, 1.5, 40, 0),
            "tromblon", new Gun("cartouche_tromblon", 3, 5, 7.0, 2, 50, 20, 16, 1.25, 50, 0.6),
            "carabine", new Gun("balle_carabine", 16, 1, 0.2, 5, 60, 30, 80, 2.0, 60, 0));

    /** Gravures : id de la pierre → (clé stockée, nom). */
    public static final Map<String, String> ENGRAVINGS = new LinkedHashMap<>();
    static {
        ENGRAVINGS.put("saignement", "Saignement");
        ENGRAVINGS.put("feu", "Feu");
        ENGRAVINGS.put("recul", "Recul");
        ENGRAVINGS.put("vol_de_vie", "Vol de vie");
        ENGRAVINGS.put("fendoir", "Fendoir");
        ENGRAVINGS.put("silence", "Silence");
    }

    public static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static Def add(Def d) {
        DEFS.put(d.id, d);
        return d;
    }

    static {
        // --- corps à corps (dégâts vanilla modernes)
        add(new Def("epee_bois", Material.WOODEN_SWORD, COMMUN, "Épée en bois", Kind.MELEE, false, 1,
                "Équilibrée. Le balayage touche les zombies collés.")).damage = 4;
        add(new Def("epee_pierre", Material.STONE_SWORD, COMMUN, "Épée en pierre", Kind.MELEE, false, 1,
                "Équilibrée. Le balayage touche les zombies collés.")).damage = 5;
        add(new Def("epee_fer", Material.IRON_SWORD, PEU_COMMUN, "Épée en fer", Kind.MELEE, false, 1,
                "Équilibrée. Le balayage touche les zombies collés.")).damage = 6;
        add(new Def("epee_diamant", Material.DIAMOND_SWORD, RARE, "Épée en diamant", Kind.MELEE, false, 1,
                "Équilibrée. Le balayage touche les zombies collés.")).damage = 7;
        add(new Def("epee_netherite", Material.NETHERITE_SWORD, EPIQUE, "Épée en netherite", Kind.MELEE, false, 1,
                "Équilibrée. Le balayage touche les zombies collés.")).damage = 8;
        add(new Def("dague", Material.IRON_NUGGET, PEU_COMMUN, "Dague", Kind.MELEE, true, 1,
                "Très rapide, faible.", "×3 dans le dos d'un zombie qui ne t'a pas vu.")).damage = 3;
        add(new Def("hache", Material.IRON_AXE, PEU_COMMUN, "Hache", Kind.MELEE, false, 1,
                "Lente, très forte.", "Casse les barricades, étourdit les Brutes.")).damage = 9;
        add(new Def("lance", Material.IRON_SPEAR, PEU_COMMUN, "Lance", Kind.MELEE, false, 1,
                "Portée longue, charge.", "Garde les zombies à distance.")).damage = 0;
        add(new Def("masse", Material.MACE, EPIQUE, "Masse", Kind.MELEE, false, 1,
                "Coup en tombant d'un étage.", "Onde qui renverse.")).damage = 0;
        // --- distance sans poudre
        add(new Def("arc", Material.BOW, COMMUN, "Arc", Kind.BOW, false, 1, "Silencieux (bruit 5)."));
        add(new Def("arbalete", Material.CROSSBOW, PEU_COMMUN, "Arbalète", Kind.BOW, false, 1,
                "Lente, forte, perce un zombie."));
        add(new Def("fleches", Material.ARROW, COMMUN, "Flèches", Kind.AMMO, false, 64, "Pour l'arc et l'arbalète."));
        add(new Def("couteau_lancer", Material.IRON_NUGGET, PEU_COMMUN, "Couteau de lancer", Kind.THROW, true, 16,
                "Clic droit : lancer (5 dégâts).")).damage = 5;
        add(new Def("bouteille_incendiaire", Material.GLASS_BOTTLE, RARE, "Bouteille incendiaire", Kind.THROW, true, 4,
                "Clic droit : lancer.", "Flaque de feu 5 s (bruit 16)."));
        add(new Def("fusee_appat", Material.FIREWORK_STAR, RARE, "Fusée d'appât", Kind.THROW, true, 4,
                "Clic droit : lancer.", "Attire tous les zombies à 48 blocs pendant 15 s."));
        add(new Def("tnt_meche", Material.TNT, RARE, "TNT à mèche", Kind.THROW, false, 4,
                "Clic droit : allumer et lancer.", "Explosion sans casser de blocs (bruit 64)."));
        // --- armes à feu
        add(new Def("pistolet", Material.IRON_HORSE_ARMOR, RARE, "Pistolet bricolé", Kind.GUN, true, 1,
                "Clic droit : tirer. F : recharger.", "Chargeur 6, recharge 2 s, bruit 40.")).damage = 10;
        add(new Def("tromblon", Material.GOLDEN_HORSE_ARMOR, EPIQUE, "Tromblon", Kind.GUN, true, 1,
                "Clic droit : tirer. F : recharger.", "5 plombs × 3, courte portée, recule.", "Chargeur 2, bruit 50.")).damage = 3;
        add(new Def("carabine", Material.DIAMOND_HORSE_ARMOR, EPIQUE, "Carabine à levier", Kind.GUN, true, 1,
                "Clic droit : tirer. F : recharger.", "Tête ×2, lente. Chargeur 5, bruit 60.")).damage = 16;
        add(new Def("balle_pistolet", Material.GOLD_NUGGET, PEU_COMMUN, "Balles de pistolet", Kind.AMMO, true, 64,
                "Munitions du pistolet bricolé."));
        add(new Def("cartouche_tromblon", Material.GOLD_NUGGET, RARE, "Cartouches de plombs", Kind.AMMO, true, 64,
                "Munitions du tromblon."));
        add(new Def("balle_carabine", Material.GOLD_NUGGET, RARE, "Balles de carabine", Kind.AMMO, true, 64,
                "Munitions de la carabine à levier."));
        // --- armures (vanilla)
        String[][] armors = {
                {"cuir", "en cuir", COMMUN, "LEATHER"}, {"maille", "en mailles", PEU_COMMUN, "CHAINMAIL"},
                {"fer", "en fer", PEU_COMMUN, "IRON"}, {"diamant", "en diamant", RARE, "DIAMOND"}};
        String[][] pieces = {{"casque", "Casque", "HELMET"}, {"plastron", "Plastron", "CHESTPLATE"},
                {"jambieres", "Jambières", "LEGGINGS"}, {"bottes", "Bottes", "BOOTS"}};
        for (String[] a : armors)
            for (String[] p : pieces) {
                String name = p[1] + " " + a[1];
                if (a[0].equals("maille") && p[0].equals("jambieres")) name = "Jambières en mailles";
                add(new Def(p[0] + "_" + a[0], Material.valueOf(a[3] + "_" + p[2]), a[2], name, Kind.ARMOR, false, 1,
                        "Chaque niveau : -2 % de dégâts subis."));
            }
        // --- gravures
        for (Map.Entry<String, String> e : ENGRAVINGS.entrySet())
            add(new Def("gravure_" + e.getKey(), Material.AMETHYST_SHARD, EPIQUE, "Pierre de gravure : " + e.getValue(),
                    Kind.ENGRAVING, true, 16, engravingLore(e.getKey()), "Pose-la sur une arme de corps à corps",
                    "dans ton inventaire (clic avec la pierre)."));
        // --- outils d'essai, soins et objets
        add(new Def("pierre_amelioration", Material.AMETHYST_SHARD, LEGENDAIRE, "Pierre d'amélioration (essai)", Kind.TOOL, true, 1,
                "Outil de la salle d'essai.", "Clic avec la pierre sur une arme ou une armure :",
                "niveau +1 (après +5, retour à +0)."));
        add(new Def("bandage", Material.PAPER, COMMUN, "Bandage", Kind.MISC, true, 16,
                "Clic droit : arrête le saignement, +2 PV."));
        add(new Def("conserve", Material.BREAD, COMMUN, "Conserve", Kind.MISC, true, 16, "Nourriture."));
        add(new Def("ferraille", Material.IRON_NUGGET, COMMUN, "Ferraille", Kind.MISC, true, 64, "Matériau."));
        add(new Def("piece_lame", Material.IRON_INGOT, PEU_COMMUN, "Lame", Kind.MISC, true, 16, "Pièce d'arme."));
        add(new Def("piece_ressort", Material.IRON_INGOT, PEU_COMMUN, "Ressort", Kind.MISC, true, 16, "Pièce d'arme."));
        add(new Def("piece_canon", Material.IRON_INGOT, RARE, "Canon", Kind.MISC, true, 16, "Pièce d'arme à feu."));
        add(new Def("piece_mecanisme", Material.IRON_INGOT, RARE, "Mécanisme", Kind.MISC, true, 16, "Pièce d'arme à feu."));
        add(new Def("cle_coffre_fort", Material.TRIAL_KEY, RARE, "Clé du coffre-fort", Kind.MISC, false, 1,
                "Ouvre le coffre-fort de la salle d'essai."));
    }

    private static String engravingLore(String key) {
        return switch (key) {
            case "saignement" -> "Le zombie touché saigne (1 dégât/s, 4 s).";
            case "feu" -> "Le zombie touché prend feu (4 s).";
            case "recul" -> "Repousse fort le zombie touché.";
            case "vol_de_vie" -> "Rend 20 % des dégâts infligés en PV.";
            case "fendoir" -> "Balayage plus large : 50 % aux zombies à 2,5 blocs.";
            case "silence" -> "Coups sans bruit.";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ PDC

    public static NamespacedKey KEY_ID, KEY_LEVEL, KEY_ENGRAVE, KEY_AMMO;

    static void init(EemiRefuge plugin) {
        KEY_ID = new NamespacedKey(plugin, "id");
        KEY_LEVEL = new NamespacedKey(plugin, "level");
        KEY_ENGRAVE = new NamespacedKey(plugin, "engrave");
        KEY_AMMO = new NamespacedKey(plugin, "ammo");
    }

    public static Collection<Def> all() {
        return DEFS.values();
    }

    public static Def def(String id) {
        return DEFS.get(id);
    }

    public static String id(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    public static Def def(ItemStack it) {
        String id = id(it);
        return id == null ? null : DEFS.get(id);
    }

    public static int level(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer l = it.getItemMeta().getPersistentDataContainer().get(KEY_LEVEL, PersistentDataType.INTEGER);
        return l == null ? 0 : l;
    }

    public static List<String> engravings(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return List.of();
        String s = it.getItemMeta().getPersistentDataContainer().get(KEY_ENGRAVE, PersistentDataType.STRING);
        if (s == null || s.isBlank()) return List.of();
        return new ArrayList<>(Arrays.asList(s.split(",")));
    }

    public static int ammo(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer a = it.getItemMeta().getPersistentDataContainer().get(KEY_AMMO, PersistentDataType.INTEGER);
        return a == null ? 0 : a;
    }

    public static void setAmmo(ItemStack it, int ammo) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(KEY_AMMO, PersistentDataType.INTEGER, ammo);
        it.setItemMeta(m);
    }

    public static int engraveSlots(int level) {
        return level >= 5 ? 2 : 1;
    }

    // ------------------------------------------------------------------ fabrication

    public static ItemStack create(String id, int amount) {
        return create(id, 0, List.of(), amount);
    }

    public static ItemStack create(String id, int level, List<String> engrave, int amount) {
        Def d = DEFS.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.base, Math.max(1, Math.min(amount, Math.max(d.stack, 1))));
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(KEY_ID, PersistentDataType.STRING, id);
        int lvl = d.upgradable() ? Math.max(0, Math.min(5, level)) : 0;
        if (d.upgradable()) pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, lvl);
        List<String> eng = d.engravable() ? engrave : List.of();
        if (!eng.isEmpty()) pdc.set(KEY_ENGRAVE, PersistentDataType.STRING, String.join(",", eng));
        Gun gun = GUNS.get(id);
        if (gun != null) pdc.set(KEY_AMMO, PersistentDataType.INTEGER, gun.magazine());

        String title = d.color + d.name + (d.upgradable() && lvl > 0 ? " §e+" + lvl : "");
        m.displayName(Txt.item(title));
        List<String> lore = new ArrayList<>();
        for (String l : d.lore) lore.add("§7" + l);
        if (d.upgradable()) {
            lore.add("");
            if (d.kind == Kind.ARMOR) lore.add("§7Niveau §e+" + lvl + " §8(-" + (2 * lvl) + " % de dégâts)");
            else lore.add("§7Niveau §e+" + lvl + " §8(+" + Txt.num(0.5 * lvl) + " dégât)");
        }
        if (d.engravable()) {
            int slots = engraveSlots(lvl);
            lore.add("§7Gravures (" + eng.size() + "/" + slots + ") :");
            if (eng.isEmpty()) lore.add("§8  aucune");
            for (String e : eng) lore.add("§d  " + ENGRAVINGS.getOrDefault(e, e));
        }
        if (gun != null) {
            lore.add("§7Dégâts : §f" + (gun.pellets() > 1 ? gun.pellets() + " × " : "") + Txt.num(gun.damage())
                    + " §8| §7Tête ×" + Txt.num(gun.headMult()));
        }
        lore.add("§8" + rarityName(d.color));
        List<net.kyori.adventure.text.Component> comps = new ArrayList<>();
        for (String l : lore) comps.add(Txt.item(l));
        m.lore(comps);

        if (d.custom) m.setItemModel(new NamespacedKey("refuge", id));
        if (d.stack != d.base.getMaxStackSize()) m.setMaxStackSize(Math.max(1, Math.min(99, d.stack)));
        if (id.equals("dague")) {
            // Base 1 (poing) + 2 = 3 dégâts ; vitesse 4 - 1 = 3 coups/s.
            m.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey("eemirefuge", "dague_degats"), 2.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            m.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey("eemirefuge", "dague_vitesse"), -1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (id.equals("arbalete")) m.addEnchant(Enchantment.PIERCING, 1, true);
        if (id.equals("pierre_amelioration")) m.setEnchantmentGlintOverride(true);
        if (m instanceof LeatherArmorMeta lam) lam.setColor(Color.fromRGB(0x8B5A2B));
        it.setItemMeta(m);
        if (gun != null) {
            // Groupe de recharge propre à chaque arme : la barre de recharge vanilla ne s'affiche que sur elle.
            it.setData(DataComponentTypes.USE_COOLDOWN,
                    UseCooldown.useCooldown(0.05f).cooldownGroup(Key.key("refuge", "cooldown_" + id)).build());
        }
        return it;
    }

    public static Key cooldownGroup(String gunId) {
        return Key.key("refuge", "cooldown_" + gunId);
    }

    /** Recrée l'objet avec un autre niveau / d'autres gravures en gardant les munitions et le nombre. */
    public static ItemStack remake(ItemStack old, int level, List<String> engrave) {
        String id = id(old);
        ItemStack it = create(id, level, engrave, old.getAmount());
        if (GUNS.containsKey(id)) setAmmo(it, ammo(old));
        return it;
    }

    public static String rarityName(String color) {
        return switch (color) {
            case PEU_COMMUN -> "Peu commun";
            case RARE -> "Rare";
            case EPIQUE -> "Épique";
            case LEGENDAIRE -> "Légendaire";
            default -> "Commun";
        };
    }

    private Catalogue() { }
}
