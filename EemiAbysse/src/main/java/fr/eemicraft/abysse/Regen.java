package fr.eemicraft.abysse;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Régénération des minerais des strates : un minerai cassé devient un bloc « épuisé » (bedrock par défaut,
 * incassable) puis redevient le même minerai après {@code minerais.regeneration-secondes} (± 25 %). Les minerais
 * en attente sont gardés dans minerais.yml ; ceux d'une strate déchargée repoussent quand son chunk revient.
 */
final class Regen {

    record Spot(String world, int x, int y, int z, Material ore, long due) {
    }

    private final EemiAbysse plugin;
    private final Map<String, Spot> spots = new HashMap<>();
    private final Random random = new Random();
    private final File file;

    Regen(EemiAbysse plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "minerais.yml");
    }

    Material placeholder() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("minerais.bloc-epuise", "BEDROCK"));
        return m == null || !m.isBlock() ? Material.BEDROCK : m;
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    /** Le joueur vient de casser ce minerai : le bloc épuisé est posé au tick suivant. */
    void mined(Block b, Material ore) {
        long delay = (long) (plugin.getConfig().getDouble("minerais.regeneration-secondes", 180) * 1000 * (0.75 + random.nextDouble() * 0.5));
        Spot s = new Spot(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), ore, System.currentTimeMillis() + delay);
        spots.put(key(b), s);
        Material ph = placeholder();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (b.getType().isAir()) b.setType(ph, false);
        });
    }

    /** Secondes avant la repousse de ce bloc, ou -1 s'il n'est pas en attente. */
    long left(Block b) {
        Spot s = spots.get(key(b));
        return s == null ? -1 : Math.max(0, (s.due() - System.currentTimeMillis()) / 1000);
    }

    /** Toutes les 5 s : remet les minerais arrivés à échéance dans les chunks chargés. */
    void tick() {
        long now = System.currentTimeMillis();
        Material ph = placeholder();
        for (Iterator<Spot> it = spots.values().iterator(); it.hasNext(); ) {
            Spot s = it.next();
            if (s.due() > now) continue;
            World w = Bukkit.getWorld(s.world());
            if (w == null || !w.isChunkLoaded(s.x() >> 4, s.z() >> 4)) continue;
            Block b = w.getBlockAt(s.x(), s.y(), s.z());
            // un admin a pu remplacer le bloc entre-temps : on n'écrase que le bloc épuisé (ou un trou)
            if (b.getType() == ph || b.getType().isAir()) {
                b.setType(s.ore(), false);
                w.spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.4, 0.4, 0.4);
                w.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
            }
            it.remove();
        }
    }

    /** La strate vient d'être remise à neuf : plus rien à faire repousser dedans. */
    void clearWorld(String world) {
        spots.values().removeIf(s -> s.world().equals(world));
    }

    void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String line : y.getStringList("minerais")) {
            String[] p = line.split(";");
            if (p.length != 6) continue;
            Material m = Material.matchMaterial(p[4]);
            if (m == null) continue;
            try {
                Spot s = new Spot(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), m, Long.parseLong(p[5]));
                spots.put(s.world() + ";" + s.x() + ";" + s.y() + ";" + s.z(), s);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (Spot s : spots.values()) list.add(s.world() + ";" + s.x() + ";" + s.y() + ";" + s.z() + ";" + s.ore().name() + ";" + s.due());
        y.set("minerais", list);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("minerais.yml : " + e.getMessage());
        }
    }
}
