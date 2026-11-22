package fr.fallen.lgplus;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.gh0st.loupGarou.LoupGarouPlugin;
import org.gh0st.loupGarou.utils.extern.WorldGuardIntegration;

/**
 * Remplace l'intégration WorldGuard de LoupGarou (champ privé LoupGarouPlugin#worldGuardIntegration).
 * LoupGarou choisit les participants (startGame, démarrage auto, compteur) avec
 * isPlayerInCorrectWorld + canPlayerPlay : on y ajoute deux règles.
 * <ul>
 * <li>un joueur en vanish (métadonnée « vanished » = true, posée par EemiAdmin) ne peut jamais jouer ;</li>
 * <li>hors du monde de jeu on répond « non » directement, sans passer par isPlayerInRegion qui écrit
 * « [DEBUG] Monde incorrect » dans la console à chaque appel (plusieurs fois par seconde).</li>
 * </ul>
 */
final class VanishAwareWorldGuard extends WorldGuardIntegration {
    private final LoupGarouPlugin lg;

    VanishAwareWorldGuard(LoupGarouPlugin lg) {
        super(lg);
        this.lg = lg;
    }

    static boolean isVanished(Player p) {
        if (!p.hasMetadata("vanished")) return false;
        for (MetadataValue v : p.getMetadata("vanished")) {
            if (v.asBoolean()) return true;
        }
        return false;
    }

    private boolean wrongWorld(Player p) {
        return this.lg.getConfigManager().isWorldGuardEnabled()
                && !p.getWorld().getName().equals(this.lg.getConfigManager().getWorldGuardWorld());
    }

    @Override
    public boolean canPlayerPlay(Player player) {
        if (isVanished(player) || this.wrongWorld(player)) return false;
        return super.canPlayerPlay(player);
    }

    @Override
    public boolean isPlayerInCorrectWorld(Player player) {
        if (isVanished(player)) return false;
        return super.isPlayerInCorrectWorld(player);
    }
}
