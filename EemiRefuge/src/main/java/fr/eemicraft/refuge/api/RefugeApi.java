package fr.eemicraft.refuge.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Module Refuge : monde du Refuge, PNJ, interfaces, escouades, réserve, marchand, profils. */
public interface RefugeApi {
    void enable();
    void disable();

    World world();
    Location spawn();

    /** Téléporte au Refuge (sans toucher à l'inventaire). */
    void sendToRefuge(Player p);

    Profile profile(UUID id);

    /** Appelé par Expéditions quand le joueur revient au Refuge (extraction ou mort) : affiche le bilan. */
    void showBilan(Player p, Bilan bilan);

    /** Sous-commandes /refuge pour les joueurs (/refuge seul = aller au Refuge). true = traitée. */
    boolean handleCommand(CommandSender s, String[] args);
}
