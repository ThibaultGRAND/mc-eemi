package fr.eemicraft.refuge.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Contrat étape 1 (13/11/2026) entre le module Expéditions (fr.eemicraft.refuge.expe.Expeditions)
 * et le module Refuge/interfaces (fr.eemicraft.refuge.refuge.Refuge). Voir EemiRefuge/ETAPE1_CONTRAT.md.
 * Ne pas changer une signature sans mettre à jour le contrat et l'autre module.
 */
public interface ExpeditionApi {
    void enable();
    void disable();

    /** Zones jouables (chargées et prêtes). */
    List<ZoneInfo> zones();

    /**
     * Lance une expédition pour une escouade déjà formée (le chef en fait partie, 1 à 4 joueurs, tous au Refuge).
     * @return null si lancée, sinon le message d'erreur à afficher (en français).
     */
    String start(Player leader, List<Player> squad, String zoneId, boolean night);

    boolean inExpedition(Player p);

    /** Sous-commandes /refuge propres aux expéditions (admin surtout). true = traitée. */
    boolean handleCommand(CommandSender s, String[] args);
}
