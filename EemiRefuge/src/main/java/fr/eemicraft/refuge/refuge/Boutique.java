package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.Catalogue;
import fr.eemicraft.refuge.EemiRefuge;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prix fixes du Refuge : plugins/EemiRefuge/refuge-shop.yml (créé au premier démarrage, relu au démarrage et par
 * /refuge refuge boutique).
 * Lignes « id prix [nombre] » : id = objet du catalogue (dague, bandage…) ou matériau vanilla (gold_ingot).
 */
public final class Boutique {

    /** Article à vendre par l'Armurier : nombre d'objets donnés pour le prix. */
    public record Article(String key, long price, int amount) { }

    private final EemiRefuge plugin;
    final List<Article> buy = new ArrayList<>();
    /** Prix de rachat par objet (clé = id du catalogue ou « minecraft:matériau »). */
    final Map<String, Long> sell = new LinkedHashMap<>();
    final Map<String, Long> insurance = new LinkedHashMap<>();
    long insuranceStack = 16;

    Boutique(EemiRefuge plugin) {
        this.plugin = plugin;
    }

    void load() {
        File f = new File(plugin.getDataFolder(), "refuge-shop.yml");
        if (!f.isFile()) writeDefault(f);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        buy.clear();
        sell.clear();
        insurance.clear();
        for (String line : y.getStringList("acheter")) {
            String[] a = line.trim().split("\\s+");
            if (a.length < 2) continue;
            String key = key(a[0]);
            if (key == null) {
                plugin.getLogger().warning("refuge-shop.yml : objet inconnu « " + a[0] + " » (acheter).");
                continue;
            }
            try {
                long price = Long.parseLong(a[1]);
                int n = a.length > 2 ? Integer.parseInt(a[2]) : 1;
                buy.add(new Article(key, Math.max(0, price), Math.max(1, Math.min(64, n))));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("refuge-shop.yml : ligne invalide « " + line + " ».");
            }
        }
        for (String line : y.getStringList("vendre")) {
            String[] a = line.trim().split("\\s+");
            if (a.length < 2) continue;
            String key = key(a[0]);
            if (key == null) {
                plugin.getLogger().warning("refuge-shop.yml : objet inconnu « " + a[0] + " » (vendre).");
                continue;
            }
            try {
                sell.put(key, Math.max(0, Long.parseLong(a[1])));
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("refuge-shop.yml : ligne invalide « " + line + " ».");
            }
        }
        for (String r : List.of("commun", "peu_commun", "rare", "epique", "legendaire"))
            insurance.put(r, Math.max(0, y.getLong("assurance." + r, defaultInsurance(r))));
        insuranceStack = Math.max(1, y.getLong("assurance.pile", 16));
        plugin.getLogger().info("Boutique du Refuge : " + buy.size() + " articles, " + sell.size() + " rachats.");
    }

    private static long defaultInsurance(String r) {
        return switch (r) {
            case "peu_commun" -> 30;
            case "rare" -> 60;
            case "epique" -> 120;
            case "legendaire" -> 250;
            default -> 15;
        };
    }

    /** « dague » → dague ; « gold_ingot » → minecraft:gold_ingot ; inconnu → null. */
    private static String key(String raw) {
        String s = raw.toLowerCase(Locale.ROOT);
        if (Catalogue.def(s) != null) return s;
        if (s.startsWith("minecraft:")) s = s.substring(10);
        Material m = Material.matchMaterial(s);
        return m == null || !m.isItem() ? null : "minecraft:" + m.name().toLowerCase(Locale.ROOT);
    }

    /** Clé d'un objet réel pour le rachat, ou null (objet amélioré, gravé, renommé…). */
    static String keyOf(ItemStack it) {
        if (it == null || it.isEmpty()) return null;
        String id = Catalogue.id(it);
        if (id != null) {
            if (Catalogue.level(it) > 0 || !Catalogue.engravings(it).isEmpty()) return null;
            return id;
        }
        ItemMeta m = it.getItemMeta();
        if (m != null && (m.hasDisplayName() || m.hasLore() || m.hasEnchants())) return null;
        return "minecraft:" + it.getType().name().toLowerCase(Locale.ROOT);
    }

    static ItemStack create(String key, int amount) {
        if (key.startsWith("minecraft:")) {
            Material m = Material.matchMaterial(key);
            return m == null ? null : new ItemStack(m, Math.max(1, Math.min(amount, m.getMaxStackSize())));
        }
        return Catalogue.create(key, amount);
    }

    static String name(String key) {
        if (key.startsWith("minecraft:")) {
            String s = key.substring(10).replace('_', ' ');
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        Catalogue.Def d = Catalogue.def(key);
        return d == null ? key : d.name;
    }

    /** Prix d'assurance d'une pile : rareté × nombre de paquets de « pile » objets. */
    long insurancePrice(ItemStack it) {
        Catalogue.Def d = Catalogue.def(it);
        String r = "commun";
        if (d != null) r = switch (d.color) {
            case Catalogue.PEU_COMMUN -> "peu_commun";
            case Catalogue.RARE -> "rare";
            case Catalogue.EPIQUE -> "epique";
            case Catalogue.LEGENDAIRE -> "legendaire";
            default -> "commun";
        };
        long base = insurance.getOrDefault(r, 15L);
        long packs = (it.getAmount() + insuranceStack - 1) / insuranceStack;
        return base * Math.max(1, packs);
    }

    private void writeDefault(File f) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "Boutique du Refuge (Dernier Refuge). Prix en Ferraille.",
                "acheter : « id prix [nombre] » = ce que vend l'Armurier (nombre d'objets donnés pour le prix).",
                "vendre : « id prix » = prix de rachat d'UN objet (objets améliorés, gravés ou renommés jamais rachetés).",
                "id = objet du catalogue du mode (voir /refuge give) ou matériau vanilla (gold_ingot, diamond...).",
                "assurance : prix pour assurer une pile selon sa rareté, par paquet de « pile » objets.",
                "Pris en compte au redémarrage du serveur."));
        y.set("acheter", List.of("epee_pierre 60", "epee_fer 180", "hache 220", "arc 90", "fleches 25 16",
                "couteau_lancer 45 4", "bouteille_incendiaire 90", "fusee_appat 120", "bandage 15 2", "conserve 10 2",
                "casque_cuir 30", "plastron_cuir 50", "jambieres_cuir 40", "bottes_cuir 25", "plastron_maille 140",
                "pistolet 600", "balle_pistolet 80 6"));
        y.set("vendre", List.of("ferraille 1", "piece_lame 12", "piece_ressort 12", "piece_canon 30",
                "piece_mecanisme 30", "gravure_saignement 60", "gravure_feu 60", "gravure_recul 60",
                "gravure_vol_de_vie 60", "gravure_fendoir 60", "gravure_silence 60", "conserve 3", "bandage 4",
                "iron_ingot 3", "gold_nugget 1", "gold_ingot 6", "diamond 25", "emerald 10"));
        y.set("assurance.commun", 15);
        y.set("assurance.peu_commun", 30);
        y.set("assurance.rare", 60);
        y.set("assurance.epique", 120);
        y.set("assurance.legendaire", 250);
        y.set("assurance.pile", 16);
        try {
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().warning("refuge-shop.yml : ecriture impossible : " + ex);
        }
    }
}
