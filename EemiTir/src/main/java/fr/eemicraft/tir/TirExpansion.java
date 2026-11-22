package fr.eemicraft.tir;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** %eemitir_top_<n>%, %eemitir_best%, %eemitir_rank%. */
final class TirExpansion extends PlaceholderExpansion {
    private final EemiTir plugin;

    TirExpansion(EemiTir plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "eemitir";
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
