package fr.eemicraft.reparer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Retire l'invulnerabilite d'entite qu'un plugin a laissee sur un joueur (on ne pouvait plus le frapper,
 * ni meme le /kill). Verifie a la connexion et a chaque arrivee au hub ; les admins en vanish sont ignores.
 * /reparer <joueur> le fait a la main, n'importe ou.
 */
public final class EemiReparer extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                check(p, "demarrage");
            }
        }, 40L);
    }

    boolean vanished(Player p) {
        return p.hasMetadata("vanished") && !p.getMetadata("vanished").isEmpty() && p.getMetadata("vanished").get(0).asBoolean();
    }

    boolean inHub(Player p) {
        return getConfig().getStringList("hub-worlds").contains(p.getWorld().getName());
    }

    void check(Player p, String why) {
        if (!p.isOnline() || !p.isInvulnerable() || vanished(p)) {
            return;
        }
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) {
            return;
        }
        if (!inHub(p) && !"connexion".equals(why)) {
            return;
        }
        p.setInvulnerable(false);
        getLogger().warning("Invulnerabilite retiree a " + p.getName() + " (" + why + ", monde " + p.getWorld().getName() + ") : laissee par un autre plugin.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> check(p, inHub(p) ? "connexion" : "connexion hors hub"), 40L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> check(p, "arrivee au hub"), 20L);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW + "/reparer <joueur> : retire l'invulnerabilite laissee par un plugin.");
            return true;
        }
        Player p = Bukkit.getPlayerExact(a[0]);
        if (p == null) {
            s.sendMessage(ChatColor.RED + "Joueur introuvable.");
            return true;
        }
        boolean was = p.isInvulnerable();
        p.setInvulnerable(false);
        s.sendMessage(ChatColor.GREEN + p.getName() + (was ? " n'est plus invulnerable." : " n'etait pas invulnerable."));
        if (was) {
            getLogger().warning("Invulnerabilite retiree a " + p.getName() + " par " + s.getName() + ".");
        }
        return true;
    }
}
