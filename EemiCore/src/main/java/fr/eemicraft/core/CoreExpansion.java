/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 */
package fr.eemicraft.core;

import fr.eemicraft.core.EemiCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

final class CoreExpansion
extends PlaceholderExpansion {
    private final EemiCore plugin;

    CoreExpansion(EemiCore plugin) {
        this.plugin = plugin;
    }

    public String getIdentifier() {
        return "eemi";
    }

    public String getAuthor() {
        return "EEMICRAFT";
    }

    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return this.plugin.placeholder(player, params);
    }
}

