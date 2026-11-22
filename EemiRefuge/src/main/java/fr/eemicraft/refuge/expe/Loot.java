package fr.eemicraft.refuge.expe;

import fr.eemicraft.refuge.Catalogue;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Tables de butin (plugins/EemiRefuge/loot.yml). Une table par type de pièce ; chaque entrée a un objet
 * (id du Catalogue ou matériau vanilla), un poids, une rareté, une quantité et un acte minimal.
 * Tirage : on tire d'abord un cran de rareté (poids « raretes »), +1 cran la nuit, puis un objet de ce cran
 * dans la table de la pièce (on descend d'un cran s'il n'y en a pas). Environ « vide » (40 %) des cases tirées
 * restent vides (« déjà pillé »).
 */
final class Loot {

    static final String[] TIERS = {"commun", "peu_commun", "rare", "epique", "legendaire"};

    record Entry(String item, int weight, int tier, int min, int max, int actMin, int actMax) { }

    private final Logger log;
    private final Map<String, List<Entry>> tables = new HashMap<>();
    private final Map<String, String> alias = new HashMap<>();
    private final Map<String, Integer> values = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final int[] tierWeights = {60, 26, 10, 3, 1};
    private double empty = 0.4;
    private int rollsMin = 2, rollsMax = 5, nightBonus = 1;
    private final Map<String, Integer> sizes = new HashMap<>();

    Loot(Logger log) {
        this.log = log;
    }

    void load(File f) {
        tables.clear();
        alias.clear();
        values.clear();
        warned.clear();
        sizes.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection r = y.getConfigurationSection("raretes");
        if (r != null) for (int i = 0; i < TIERS.length; i++) tierWeights[i] = Math.max(0, r.getInt(TIERS[i], tierWeights[i]));
        empty = Math.max(0, Math.min(0.95, y.getDouble("vide", empty)));
        rollsMin = Math.max(1, y.getInt("objets-par-conteneur.min", rollsMin));
        rollsMax = Math.max(rollsMin, y.getInt("objets-par-conteneur.max", rollsMax));
        nightBonus = y.getInt("nuit-bonus", nightBonus);
        ConfigurationSection sz = y.getConfigurationSection("tirages-par-type");
        if (sz != null) for (String k : sz.getKeys(false)) sizes.put(k.toLowerCase(Locale.ROOT), sz.getInt(k));
        ConfigurationSection a = y.getConfigurationSection("alias");
        if (a != null) for (String k : a.getKeys(false)) alias.put(k.toLowerCase(Locale.ROOT), a.getString(k, "defaut").toLowerCase(Locale.ROOT));
        ConfigurationSection v = y.getConfigurationSection("valeurs");
        if (v != null) for (String k : v.getKeys(false)) values.put(k.toLowerCase(Locale.ROOT), v.getInt(k));
        ConfigurationSection p = y.getConfigurationSection("pieces");
        int n = 0;
        if (p != null)
            for (String piece : p.getKeys(false)) {
                List<Entry> list = new ArrayList<>();
                for (Map<?, ?> m : p.getMapList(piece)) {
                    Object item = m.get("objet") != null ? m.get("objet") : m.get("item");
                    if (item == null) continue;
                    String id = item.toString().toLowerCase(Locale.ROOT);
                    int tier = tierOf(m.get("rarete") != null ? m.get("rarete") : m.get("rarity"), id);
                    int w = intOf(m.get("poids"), intOf(m.get("weight"), 10));
                    int min = intOf(m.get("min"), 1), max = Math.max(min, intOf(m.get("max"), min));
                    int actMin = intOf(m.get("acte"), intOf(m.get("acte-min"), 1));
                    int actMax = intOf(m.get("acte-max"), 99);
                    if (w > 0) list.add(new Entry(id, w, tier, min, max, actMin, actMax));
                }
                tables.put(piece.toLowerCase(Locale.ROOT), list);
                n += list.size();
            }
        if (!tables.containsKey("defaut")) log.warning("loot.yml : pas de table « defaut ».");
        log.info("Butin : " + tables.size() + " tables, " + n + " entrees.");
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number num) return num.intValue();
        if (o == null) return def;
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int tierOf(Object o, String item) {
        if (o != null) {
            String s = o.toString().toLowerCase(Locale.ROOT).replace(' ', '_').replace("é", "e").replace("-", "_");
            for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(s)) return i;
            try {
                return Math.max(0, Math.min(4, Integer.parseInt(s)));
            } catch (NumberFormatException ignored) {
            }
        }
        Catalogue.Def d = Catalogue.def(item);
        if (d == null) return 0;
        return switch (d.color) {
            case Catalogue.PEU_COMMUN -> 1;
            case Catalogue.RARE -> 2;
            case Catalogue.EPIQUE -> 3;
            case Catalogue.LEGENDAIRE -> 4;
            default -> 0;
        };
    }

    /** Table utilisée pour une pièce (alias puis « defaut »), ou null si aucune. */
    String tableFor(String piece) {
        String p = piece == null ? "defaut" : piece.toLowerCase(Locale.ROOT);
        if (tables.containsKey(p)) return p;
        String a = alias.get(p);
        if (a != null && tables.containsKey(a)) return a;
        return tables.containsKey("defaut") ? "defaut" : null;
    }

    boolean hasOwnTable(String piece) {
        String p = piece == null ? "" : piece.toLowerCase(Locale.ROOT);
        return tables.containsKey(p) || (alias.containsKey(p) && tables.containsKey(alias.get(p)));
    }

    /**
     * Vide puis remplit un inventaire de support. size = cases utilisables (3 pour une étagère, 1 pour un pot...).
     * type = type de bloc du marqueur (barrel, chest, shelf...), pour le nombre de tirages.
     */
    void fill(Inventory inv, String type, String piece, int act, boolean night) {
        inv.clear();
        String t = tableFor(piece);
        if (t == null) return;
        List<Entry> table = tables.get(t);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        InventoryType invType = inv.getType();
        if (invType == InventoryType.FURNACE || invType == InventoryType.BLAST_FURNACE || invType == InventoryType.SMOKER) {
            // Four : un seul objet, dans la case du résultat (comme un plat oublié).
            if (r.nextDouble() >= empty) {
                ItemStack one = roll(table, act, night, r);
                if (one != null) inv.setItem(2, one);
            }
            return;
        }
        int size = inv.getSize();
        int rolls = sizes.containsKey(type) ? sizes.get(type) : r.nextInt(rollsMin, rollsMax + 1);
        rolls = Math.min(rolls, size);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) slots.add(i);
        Collections.shuffle(slots);
        for (int i = 0; i < rolls; i++) {
            if (r.nextDouble() < empty) continue;
            ItemStack it = roll(table, act, night, r);
            if (it == null) continue;
            try {
                inv.setItem(slots.get(i), it);
            } catch (IllegalArgumentException ex) {
                // Support qui refuse l'objet (bibliothèque ciselée : livres seulement) : case laissée vide.
            }
        }
    }

    private ItemStack roll(List<Entry> table, int act, boolean night, ThreadLocalRandom r) {
        int total = 0;
        for (int w : tierWeights) total += w;
        int tier = 0;
        if (total > 0) {
            int x = r.nextInt(total);
            for (int i = 0; i < tierWeights.length; i++) {
                x -= tierWeights[i];
                if (x < 0) {
                    tier = i;
                    break;
                }
            }
        }
        if (night) tier = Math.min(4, tier + nightBonus);
        for (int step = 0; step < 10; step++) {
            int want = step <= tier ? tier - step : step;  // on descend d'abord, puis on remonte
            if (want > 4) break;
            List<Entry> cands = new ArrayList<>();
            int sum = 0;
            for (Entry e : table)
                if (e.tier == want && act >= e.actMin && act <= e.actMax) {
                    cands.add(e);
                    sum += e.weight;
                }
            if (cands.isEmpty()) continue;
            int x = r.nextInt(sum);
            for (Entry e : cands) {
                x -= e.weight;
                if (x < 0) return make(e.item, r.nextInt(e.min, e.max + 1));
            }
        }
        return null;
    }

    ItemStack make(String id, int n) {
        Catalogue.Def d = Catalogue.def(id);
        if (d != null) return Catalogue.create(id, Math.max(1, Math.min(n, Math.max(1, d.stack))));
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isItem() || m.isAir()) {
            if (warned.add(id)) log.warning("loot.yml : objet inconnu « " + id + " » (ignore).");
            return null;
        }
        return new ItemStack(m, Math.max(1, Math.min(n, m.getMaxStackSize())));
    }

    /** Clé d'objet pour les comptes (id du Catalogue ou matériau). */
    static String key(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        String id = Catalogue.id(it);
        return id != null ? id : it.getType().getKey().getKey();
    }

    /** Valeur estimée en Ferraille d'une pile. */
    int value(ItemStack it) {
        String k = key(it);
        if (k == null) return 0;
        Integer v = values.get(k);
        return v == null ? 0 : v * it.getAmount();
    }
}
