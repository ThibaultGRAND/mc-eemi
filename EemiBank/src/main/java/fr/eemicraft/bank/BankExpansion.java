package fr.eemicraft.bank;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Placeholders : %eemibank_bank%, %eemibank_bank_raw%, %eemibank_wallet%, %eemibank_total%. */
final class BankExpansion extends PlaceholderExpansion {

    private final EemiBank plugin;

    BankExpansion(EemiBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "eemibank";
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
        if (player == null) return "";
        double bank = plugin.bank(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "bank" -> plugin.format(bank);
            case "bank_raw" -> String.valueOf(bank);
            case "wallet" -> plugin.format(plugin.wallet(player));
            case "total" -> plugin.format(bank + plugin.wallet(player));
            default -> null;
        };
    }
}
