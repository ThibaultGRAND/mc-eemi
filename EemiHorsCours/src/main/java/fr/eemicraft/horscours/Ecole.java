package fr.eemicraft.horscours;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * L'école construite à la main par les builders (monde horscours, x 300..337, z 0..29, sol y 64),
 * sauvegardée le 12/11/2026. Le plugin ne pose ni ne casse AUCUN bloc ici : tout effet (néons qui
 * clignotent, coupure de courant, portes qui claquent) est envoyé aux seuls joueurs (sendBlockChange).
 * Les points du jeu (départ, lampe, craie, café, passage) se règlent en jeu : /horscours point <nom>.
 */
final class Ecole {
    final World w;
    final FileConfiguration cfg;
    final BoundingBox zone;
    final BoundingBox directeur = new BoundingBox(315, 63, 0, 323, 68, 8);
    final BoundingBox couloirOuest = new BoundingBox(312, 63, 9, 318, 68, 16);
    final BoundingBox cagibi = new BoundingBox(331, 63, 0, 337, 68, 8);

    /** Une source de lumière de l'école : position, état allumé (réel), état éteint (envoyé). */
    record Lum(Location at, BlockData on, BlockData off) {
    }

    final List<Lum> lights = new ArrayList<>();
    final List<Location> doors = new ArrayList<>();

    Ecole(World w, FileConfiguration cfg) {
        this.w = w;
        this.cfg = cfg;
        zone = new BoundingBox(cfg.getInt("ecole.zone.x1", 298), cfg.getInt("ecole.zone.y1", 62), cfg.getInt("ecole.zone.z1", -2),
                cfg.getInt("ecole.zone.x2", 339), cfg.getInt("ecole.zone.y2", 72), cfg.getInt("ecole.zone.z2", 31));
    }

    Location point(String name, String def) {
        String s = cfg.getString("ecole.points." + name, def);
        String[] p = s.split(",");
        Location l = new Location(w, Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        if (p.length > 3) l.setYaw(Float.parseFloat(p[3].trim()));
        return l;
    }

    Location spawn() {
        return point("depart", "324.5,64,12.5,90");
    }

    Location lampe() {
        return point("lampe", "306.5,65,20.5");
    }

    Location craie() {
        return point("craie", "307.5,65,6.5");
    }

    Location cafe() {
        return point("cafe", "329.5,65,22.5");
    }

    Location passage() {
        return point("passage", "334.5,65.5,0.5");
    }

    /** Bloc d'éclairage → son état « éteint » (null = ce n'est pas une lumière). */
    static BlockData offState(Block b) {
        Material m = b.getType();
        BlockData d = b.getBlockData();
        if (d instanceof Lightable l && l.isLit() && m != Material.FURNACE && m != Material.SMOKER
                && m != Material.BLAST_FURNACE && !m.name().endsWith("CAMPFIRE")) {
            Lightable c = (Lightable) d.clone();
            c.setLit(false);
            return c;
        }
        return switch (m) {
            case SEA_LANTERN, GLOWSTONE, SHROOMLIGHT, OCHRE_FROGLIGHT, PEARLESCENT_FROGLIGHT, VERDANT_FROGLIGHT ->
                    Material.LIGHT_GRAY_CONCRETE.createBlockData();
            case LANTERN, SOUL_LANTERN, TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH, END_ROD, LIGHT,
                 COPPER_LANTERN, COPPER_TORCH, COPPER_WALL_TORCH -> Material.AIR.createBlockData();
            default -> null;
        };
    }

    /** Relève les lumières et les portes de l'école (lecture seule). */
    void scan() {
        lights.clear();
        doors.clear();
        for (int x = (int) zone.getMinX(); x <= zone.getMaxX(); x++)
            for (int y = (int) zone.getMinY(); y <= zone.getMaxY(); y++)
                for (int z = (int) zone.getMinZ(); z <= zone.getMaxZ(); z++) {
                    Block b = w.getBlockAt(x, y, z);
                    BlockData off;
                    try {
                        off = offState(b);
                    } catch (Throwable t) {
                        off = null;
                    }
                    if (off != null) lights.add(new Lum(b.getLocation(), b.getBlockData(), off));
                    if (b.getBlockData() instanceof Door d && d.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM)
                        doors.add(b.getLocation());
                }
    }

    Lum randomLightNear(Location l, double r, Random rnd) {
        List<Lum> near = new ArrayList<>();
        for (Lum x : lights) if (x.at().distanceSquared(l) < r * r) near.add(x);
        return near.isEmpty() ? null : near.get(rnd.nextInt(near.size()));
    }

    boolean inside(Player p) {
        return p.getWorld().equals(w) && zone.contains(p.getLocation().toVector());
    }
}
