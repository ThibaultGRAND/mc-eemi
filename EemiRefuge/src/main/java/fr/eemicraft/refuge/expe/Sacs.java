package fr.eemicraft.refuge.expe;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Fichiers de données des expéditions :
 * - sacs.yml : le sac laissé par un joueur mort dans une zone (position + contenu). Il est reposé au départ de la
 *   prochaine expédition de ce joueur dans cette zone, puis perdu à la fin de celle-ci (décision de Thibault).
 * - attente.yml : objets assurés à rendre au joueur à son prochain passage au Refuge (mort par déconnexion...).
 * - restauration.yml : blocs du monde d'origine d'une zone modifiés pendant une expédition (sacs, portes), remis
 *   à la fin ; relu au démarrage si le serveur s'est arrêté en pleine expédition.
 */
final class Sacs {

    /** Un sac enregistré. Comparé par identité (un nouveau sac remplace l'ancien). */
    static final class Saved {
        final String zone;
        final UUID owner;
        final String ownerName;
        final int x, y, z;
        ItemStack[] items;

        Saved(String zone, UUID owner, String ownerName, int x, int y, int z, ItemStack[] items) {
            this.zone = zone;
            this.owner = owner;
            this.ownerName = ownerName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = items;
        }
    }

    private final Logger log;
    private final File sacsFile, waitFile, restoreFile;
    private final Map<String, Saved> saved = new HashMap<>();
    private final Map<UUID, ItemStack[]> waiting = new HashMap<>();
    /** zone → (clé de bloc → "x,y,z|blockdata") */
    private final Map<String, Map<Long, String>> restore = new HashMap<>();

    Sacs(File folder, Logger log) {
        this.log = log;
        sacsFile = new File(folder, "sacs.yml");
        waitFile = new File(folder, "attente.yml");
        restoreFile = new File(folder, "restauration.yml");
    }

    static String key(String zone, UUID owner) {
        return zone + ":" + owner;
    }

    // ------------------------------------------------------------------ sérialisation

    static String encode(ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack i : items) if (i != null && !i.getType().isAir()) list.add(i);
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(list));
    }

    static ItemStack[] decode(String s) {
        if (s == null || s.isEmpty()) return new ItemStack[0];
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
    }

    void load() {
        saved.clear();
        waiting.clear();
        restore.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(sacsFile);
        for (String zone : y.getKeys(false)) {
            ConfigurationSection z = y.getConfigurationSection(zone);
            if (z == null) continue;
            for (String id : z.getKeys(false)) {
                ConfigurationSection s = z.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    UUID u = UUID.fromString(id);
                    saved.put(key(zone, u), new Saved(zone, u, s.getString("nom", "?"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                            decode(s.getString("objets"))));
                } catch (RuntimeException e) {
                    log.warning("sacs.yml : sac illisible " + zone + "/" + id + " (" + e.getMessage() + ")");
                }
            }
        }
        YamlConfiguration w = YamlConfiguration.loadConfiguration(waitFile);
        for (String id : w.getKeys(false)) {
            try {
                waiting.put(UUID.fromString(id), decode(w.getString(id)));
            } catch (RuntimeException e) {
                log.warning("attente.yml : entree illisible " + id);
            }
        }
        YamlConfiguration r = YamlConfiguration.loadConfiguration(restoreFile);
        for (String zone : r.getKeys(false)) {
            Map<Long, String> m = new HashMap<>();
            for (String line : r.getStringList(zone)) {
                String[] p = line.split("\\|", 2);
                String[] c = p[0].split(",");
                if (p.length < 2 || c.length < 3) continue;
                try {
                    m.put(Zone.blockKey(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])), line);
                } catch (NumberFormatException ignored) {
                }
            }
            if (!m.isEmpty()) restore.put(zone, m);
        }
    }

    void saveSacs() {
        YamlConfiguration y = new YamlConfiguration();
        for (Saved s : saved.values()) {
            String p = s.zone + "." + s.owner;
            y.set(p + ".nom", s.ownerName);
            y.set(p + ".x", s.x);
            y.set(p + ".y", s.y);
            y.set(p + ".z", s.z);
            y.set(p + ".objets", encode(s.items));
        }
        write(y, sacsFile);
    }

    private void saveWaiting() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> e : waiting.entrySet()) y.set(e.getKey().toString(), encode(e.getValue()));
        write(y, waitFile);
    }

    private void saveRestore() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Map<Long, String>> e : restore.entrySet())
            if (!e.getValue().isEmpty()) y.set(e.getKey(), new ArrayList<>(e.getValue().values()));
        write(y, restoreFile);
    }

    private void write(YamlConfiguration y, File f) {
        try {
            y.save(f);
        } catch (IOException e) {
            log.warning("Ecriture de " + f.getName() + " impossible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ sacs

    Saved get(String zone, UUID owner) {
        return saved.get(key(zone, owner));
    }

    void put(Saved s) {
        saved.put(key(s.zone, s.owner), s);
        saveSacs();
    }

    /** Retire le sac s'il est toujours celui enregistré (pas remplacé par un sac plus récent). */
    void removeIfSame(Saved s) {
        String k = key(s.zone, s.owner);
        if (saved.get(k) == s) {
            saved.remove(k);
            saveSacs();
        }
    }

    // ------------------------------------------------------------------ objets assurés en attente

    void addWaiting(UUID id, List<ItemStack> items) {
        if (items.isEmpty()) return;
        List<ItemStack> all = new ArrayList<>();
        ItemStack[] old = waiting.get(id);
        if (old != null) all.addAll(List.of(old));
        all.addAll(items);
        waiting.put(id, all.toArray(new ItemStack[0]));
        saveWaiting();
    }

    ItemStack[] takeWaiting(UUID id) {
        ItemStack[] it = waiting.remove(id);
        if (it != null) saveWaiting();
        return it;
    }

    boolean hasWaiting(UUID id) {
        return waiting.containsKey(id);
    }

    // ------------------------------------------------------------------ restauration du monde d'origine

    void noteRestore(String zone, int x, int y, int z, String blockData) {
        Map<Long, String> m = restore.computeIfAbsent(zone, k -> new HashMap<>());
        if (m.putIfAbsent(Zone.blockKey(x, y, z), x + "," + y + "," + z + "|" + blockData) == null) saveRestore();
    }

    /** Lignes « x,y,z|blockdata » à remettre (vidées ensuite). */
    List<String> takeRestore(String zone) {
        Map<Long, String> m = restore.remove(zone);
        if (m == null) return List.of();
        saveRestore();
        return new ArrayList<>(m.values());
    }
}
