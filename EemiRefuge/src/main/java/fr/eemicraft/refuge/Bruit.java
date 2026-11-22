package fr.eemicraft.refuge;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le bruit (CONCEPTION 5.3). Chaque action a un rayon ; les zombies dans ce rayon viennent voir.
 * Déplacement : accroupi 0, marche 4, course/saut 10 (émis toutes les 10 ticks).
 * La barre d'action montre le bruit actuel du joueur (ou le dernier message de combat pendant 2,5 s).
 */
public final class Bruit {

    static final class PState {
        Location last;
        int moveNoise;
        int eventNoise;
        long eventUntil;
        String message;
        long messageUntil;
        boolean wasOnGround = true;
    }

    private final EemiRefuge plugin;
    private final Map<UUID, PState> players = new HashMap<>();
    private int tickCount;

    Bruit(EemiRefuge plugin) {
        this.plugin = plugin;
    }

    private PState ps(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), k -> new PState());
    }

    void forget(Player p) {
        players.remove(p.getUniqueId());
    }

    /** Bruit au point loc (rayon en blocs). source peut être null (explosion, cri). */
    public void emit(Location loc, int radius, Player source) {
        if (radius <= 0 || loc == null || !plugin.inCombat(loc.getWorld())) return;
        plugin.zombies().hear(loc, radius);
        if (source != null) {
            PState s = ps(source);
            if (radius >= s.eventNoise || System.currentTimeMillis() > s.eventUntil) {
                s.eventNoise = radius;
                s.eventUntil = System.currentTimeMillis() + 1500;
            }
        }
    }

    /** Message de combat dans la barre d'action pendant 2,5 s (prioritaire sur le bruit). */
    public void hitMessage(Player p, String legacy) {
        PState s = ps(p);
        s.message = legacy;
        s.messageUntil = System.currentTimeMillis() + 2500;
        p.sendActionBar(Txt.c(legacy));
    }

    /** Appelé à chaque tick. */
    void tick() {
        tickCount++;
        long now = System.currentTimeMillis();
        for (Player p : plugin.combatPlayers()) {
            PState s = ps(p);
            Location loc = p.getLocation();
            int move = 0;
            if (s.last != null && s.last.getWorld() == loc.getWorld() && p.getGameMode() != GameMode.SPECTATOR
                    && !p.isFlying() && p.getVehicle() == null) {
                double dx = loc.getX() - s.last.getX(), dz = loc.getZ() - s.last.getZ();
                double h = Math.sqrt(dx * dx + dz * dz);
                boolean onGround = p.isOnGround();
                boolean jumped = s.wasOnGround && !onGround && loc.getY() - s.last.getY() > 0.08;
                s.wasOnGround = onGround;
                if (p.isSneaking()) move = 0;
                else if (p.isSprinting() && h > 0.02 || jumped) move = 10;
                else if (h > 0.02) move = 4;
                if (jumped) {
                    s.eventNoise = Math.max(s.eventNoise, 10);
                    s.eventUntil = now + 500;
                }
            }
            s.last = loc;
            // Lissage : on garde le max sur une demi-seconde pour l'affichage et l'émission.
            if (move >= s.moveNoise || tickCount % 10 == 0) s.moveNoise = move;
            if (tickCount % 10 == 0 && s.moveNoise > 0) {
                plugin.zombies().hear(loc, s.moveNoise);
            }
            if (tickCount % 4 == 0) display(p, s, now);
        }
    }

    private void display(Player p, PState s, long now) {
        if (s.message != null && now < s.messageUntil) {
            p.sendActionBar(Txt.c(s.message));
            return;
        }
        int noise = Math.max(s.moveNoise, now < s.eventUntil ? s.eventNoise : 0);
        StringBuilder sb = new StringBuilder();
        int bleed = plugin.armes().bleedLeft(p);
        if (bleed > 0) sb.append("§c☠ Saignement ").append(bleed).append(" s §8| ");
        ItemStack hand = p.getInventory().getItemInMainHand();
        String gunId = Catalogue.id(hand);
        Catalogue.Gun gun = gunId == null ? null : Catalogue.GUNS.get(gunId);
        if (gun != null) {
            int ammo = Catalogue.ammo(hand);
            if (plugin.armes().isReloading(p)) sb.append("§eRechargement... §8| ");
            else sb.append(ammo == 0 ? "§c" : "§f").append("Chargeur ").append(ammo).append("/").append(gun.magazine())
                    .append(" §7(").append(plugin.armes().countAmmo(p, gun.ammo())).append(" en réserve) §8| ");
        }
        sb.append("§7Bruit ").append(bar(noise)).append(" §f").append(noise).append(noise > 1 ? " blocs" : " bloc");
        if (plugin.infiniteAmmo() && plugin.inEssai(p.getWorld())) sb.append(" §8| §bMunitions infinies");
        p.sendActionBar(Txt.c(sb.toString()));
    }

    private static String bar(int noise) {
        int filled = noise <= 0 ? 0 : noise <= 4 ? 1 : noise <= 6 ? 2 : noise <= 10 ? 3 : noise <= 16 ? 4 : noise <= 40 ? 6 : 8;
        String color = filled <= 1 ? "§a" : filled <= 3 ? "§e" : filled <= 4 ? "§6" : "§c";
        return color + "▌".repeat(filled) + "§8" + "▌".repeat(8 - filled);
    }
}
