package fr.eemicraft.droits;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %eemidroits_etat_<jeu>%   : "" (ouvert), "&dBêta" ou "&cMaintenance" (pour les menus et hologrammes)
 * %eemidroits_acces_<jeu>%  : true/false pour le joueur qui regarde
 * %eemidroits_beta%         : oui/non
 * %eemidroits_tag%          : tag-beta du config si le joueur est bêta-testeur, sinon vide
 */
final class DroitsExpansion extends PlaceholderExpansion {

    private final EemiDroits pl;

    DroitsExpansion(EemiDroits pl) { this.pl = pl; }

    @Override public String getIdentifier() { return "eemidroits"; }
    @Override public String getAuthor() { return "EEMICRAFT"; }
    @Override public String getVersion() { return pl.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer op, String p) {
        if (p.startsWith("etat_")) {
            Jeux.Jeu j = pl.jeux.jeu(p.substring(5));
            if (j == null) return "";
            return switch (pl.jeux.etat(j.id)) {
                case OUVERT -> "";
                case BETA -> "§dBêta";
                case MAINTENANCE -> "§cMaintenance";
            };
        }
        Player pj = op == null ? null : op.getPlayer();
        if (p.startsWith("acces_")) {
            Jeux.Jeu j = pl.jeux.jeu(p.substring(6));
            if (j == null || pj == null) return "false";
            return String.valueOf(pl.garde.refus(pj, j) == null);
        }
        if (p.equals("beta")) return pj != null && pj.hasPermission(Menus.BETA) ? "oui" : "non";
        if (p.equals("tag")) return pj != null && pj.hasPermission(Menus.BETA) ? Txt.col(pl.getConfig().getString("tag-beta", "")) : "";
        return null;
    }
}
