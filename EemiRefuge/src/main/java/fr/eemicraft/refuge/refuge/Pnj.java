package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.EemiRefuge;
import fr.eemicraft.refuge.Txt;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerTextures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PNJ du Refuge (Mannequin, skins vanilla par défaut : aucune vraie personne) et panneaux (TextDisplay).
 * Non persistants : recréés toutes les 2 s si leur chunk est chargé et qu'ils manquent.
 */
final class Pnj {

    record Def(String id, String name, String role, double[] pos, String skin, boolean slim, Material hand,
               int coat) { }

    static final List<Def> DEFS = List.of(
            new Def("passeur", "§6Le Passeur", "§7Expéditions", Poste.PNJ[0], "kai", false, Material.COMPASS, 0x3B4A2F),
            new Def("armurier", "§cL'Armurier", "§7Armes et rachat", Poste.PNJ[1], "makena", false, Material.IRON_AXE, 0x5B3A24),
            new Def("notaire", "§9La Notaire", "§7Assurance", Poste.PNJ[2], "efe", true, Material.WRITABLE_BOOK, 0x2C3550));

    record Sign(String id, double x, double y, double z, float yaw, String text) { }

    static final List<Sign> SIGNS = List.of(
            new Sign("s_portail", 0.5, Poste.F + 8.2, -Poste.R + 0.6, 0, "§6§lDÉPART DES EXPÉDITIONS\n§7Parle au Passeur"),
            new Sign("s_armurerie", 12.2, Poste.F + 5.9, -0.5, 90, "§c§lARMURERIE"),
            new Sign("s_notaire", -11.2, Poste.F + 5.9, -0.5, -90, "§9§lNOTAIRE\n§7Assurance"),
            new Sign("s_reserve", 0.5, Poste.F + 5.9, 12.8, 180, "§e§lRÉSERVE\n§7Clic sur un coffre"));

    private final EemiRefuge plugin;
    private final Refuge refuge;
    final NamespacedKey key;
    private final Map<String, UUID> alive = new HashMap<>();

    Pnj(EemiRefuge plugin, Refuge refuge) {
        this.plugin = plugin;
        this.refuge = refuge;
        this.key = new NamespacedKey(plugin, "pnj");
    }

    String idOf(Entity e) {
        return e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    void tick() {
        World w = refuge.world();
        if (w == null) return;
        for (Def d : DEFS) {
            Location at = new Location(w, d.pos()[0], d.pos()[1], d.pos()[2], (float) d.pos()[3], 0);
            if (missing(d.id(), at)) spawnNpc(d, at);
        }
        for (Sign s : SIGNS) {
            Location at = new Location(w, s.x(), s.y(), s.z(), s.yaw(), 0);
            if (missing(s.id(), at)) spawnSign(s, at);
        }
    }

    private boolean missing(String id, Location at) {
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return false;
        UUID u = alive.get(id);
        Entity e = u == null ? null : plugin.getServer().getEntity(u);
        if (e != null && e.isValid()) {
            if (e.getLocation().distanceSquared(at) > 0.25) e.teleport(at);
            return false;
        }
        // restes d'une session précédente (ne devrait pas arriver : non persistants)
        for (Entity o : at.getWorld().getNearbyEntities(at, 2, 3, 2))
            if (id.equals(idOf(o))) o.remove();
        return true;
    }

    private void spawnNpc(Def d, Location at) {
        try {
            Mannequin m = at.getWorld().spawn(at, Mannequin.class, e -> {
                e.setPersistent(false);
                e.setInvulnerable(true);
                e.setImmovable(true);
                e.setGravity(false);
                e.setSilent(true);
                e.setCollidable(false);
                e.customName(Txt.c(d.name()));
                e.setCustomNameVisible(true);
                e.setDescription(Txt.c(d.role()));
                e.setProfile(ResolvableProfile.resolvableProfile()
                        .skinPatch(b -> b.body(Key.key("minecraft", "entity/player/" + (d.slim() ? "slim/" : "wide/") + d.skin()))
                                .model(d.slim() ? PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC))
                        .build());
                e.getEquipment().setItemInMainHand(new ItemStack(d.hand()));
                e.getEquipment().setChestplate(coat(Material.LEATHER_CHESTPLATE, d.coat()));
                e.getPersistentDataContainer().set(key, PersistentDataType.STRING, d.id());
                e.addScoreboardTag("eemirefuge_pnj");
            });
            alive.put(d.id(), m.getUniqueId());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("PNJ " + d.id() + " impossible : " + ex.getMessage());
        }
    }

    private void spawnSign(Sign s, Location at) {
        try {
            TextDisplay t = at.getWorld().spawn(at, TextDisplay.class, e -> {
                e.setPersistent(false);
                e.text(Txt.c(s.text()));
                e.setBillboard(Display.Billboard.VERTICAL);
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setShadowed(true);
                e.setBackgroundColor(Color.fromARGB(110, 20, 16, 12));
                e.getPersistentDataContainer().set(key, PersistentDataType.STRING, s.id());
                e.addScoreboardTag("eemirefuge_pnj");
            });
            alive.put(s.id(), t.getUniqueId());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Panneau " + s.id() + " impossible : " + ex.getMessage());
        }
    }

    private static ItemStack coat(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(rgb));
            it.setItemMeta(lm);
        }
        return it;
    }

    void removeAll() {
        for (UUID u : alive.values()) {
            Entity e = plugin.getServer().getEntity(u);
            if (e != null) e.remove();
        }
        alive.clear();
    }
}
