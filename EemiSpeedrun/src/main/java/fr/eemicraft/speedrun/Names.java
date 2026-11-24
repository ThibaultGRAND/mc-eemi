package fr.eemicraft.speedrun;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Noms francais des objets (noms_fr.tsv, tire du fr_fr.json officiel de Minecraft 26.3)
 * et objets tirables par niveau (objets.yml). Tout est lu dans le jar.
 */
final class Names {

    private final Map<Material, String> names = new EnumMap<>(Material.class);
    private final Map<String, List<Material>> tiers = new LinkedHashMap<>();

    Names(EemiSpeedrun plugin) {
        Logger log = plugin.getLogger();
        try (InputStream in = plugin.getResource("noms_fr.tsv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                Material material = Material.getMaterial(line.substring(0, tab));
                if (material != null && material.isItem()) names.put(material, line.substring(tab + 1));
            }
        } catch (Exception e) {
            log.severe("Impossible de lire noms_fr.tsv : " + e.getMessage());
        }

        try (InputStream in = plugin.getResource("objets.yml");
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            for (String tier : yaml.getKeys(false)) {
                List<Material> list = new ArrayList<>();
                for (String id : yaml.getStringList(tier)) {
                    Material material = Material.getMaterial(id);
                    if (material == null || !names.containsKey(material)) {
                        log.warning("Objet inconnu ou sans nom francais ignore dans objets.yml : " + id);
                        continue;
                    }
                    list.add(material);
                }
                tiers.put(tier, list);
            }
        } catch (Exception e) {
            log.severe("Impossible de lire objets.yml : " + e.getMessage());
        }
    }

    /** Nom francais de l'objet (repli sur l'identifiant si absent). */
    String name(Material material) {
        String name = names.get(material);
        return name != null ? name : material.name().toLowerCase(Locale.ROOT);
    }

    /** Premiere lettre du nom francais, sans accent, en majuscule (OE pour Œ), ou 0 si aucune. */
    char initial(Material material) {
        String name = names.get(material);
        if (name == null || name.isEmpty()) return 0;
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("Œ", "OE").replace("œ", "oe").replace("Æ", "AE").replace("æ", "ae")
                .toUpperCase(Locale.ROOT);
        char c = plain.charAt(0);
        return c >= 'A' && c <= 'Z' ? c : 0;
    }

    List<Material> tier(String tier) {
        return tiers.getOrDefault(tier, List.of());
    }

    int itemCount() {
        return names.size();
    }

    Map<String, List<Material>> tiers() {
        return tiers;
    }

    /** Lettres jouables : au moins {@code minItems} objets de objets.yml (tous niveaux) commencent par elles. */
    List<Character> playableLetters(int minItems) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (List<Material> list : tiers.values()) {
            for (Material material : list) {
                char c = initial(material);
                if (c != 0) counts.merge(c, 1, Integer::sum);
            }
        }
        List<Character> letters = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            if (counts.getOrDefault(c, 0) >= minItems) letters.add(c);
        }
        return letters;
    }

    /** Exemples d'objets de objets.yml qui commencent par la lettre (pour le message de fin de manche). */
    List<Material> examples(char letter, int max) {
        List<Material> out = new ArrayList<>();
        for (List<Material> list : tiers.values()) {
            for (Material material : list) {
                if (initial(material) == letter && out.size() < max) out.add(material);
            }
        }
        return out;
    }
}
