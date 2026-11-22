package fr.eemicraft.refuge.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Résultat d'une expédition pour UN joueur, envoyé par Expéditions au module Refuge à son retour. */
public record Bilan(String zoneName, boolean extracted, int durationSeconds, int kills, int revives,
                    long ferraille, int xp, List<ItemStack> loot, List<String> notes) {
}
