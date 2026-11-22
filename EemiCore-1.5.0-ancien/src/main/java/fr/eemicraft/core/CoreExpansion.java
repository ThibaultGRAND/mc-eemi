package fr.eemicraft.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Expansion PlaceholderAPI « eemi ». Cette classe n'est chargée que si PlaceholderAPI est activé
 * (sinon le plugin tourne sans placeholders, sans erreur).
 */
final class CoreExpansion extends PlaceholderExpansion {

    private final EemiCore plugin;

    CoreExpansion(EemiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "eemi";
    }

    @Override
    public String getAuthor() {
        return "EEMICRAFT";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return plugin.placeholder(player, params);
    }
}
