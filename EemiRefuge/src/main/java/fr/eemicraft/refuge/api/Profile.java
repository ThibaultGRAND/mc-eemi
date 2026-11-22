package fr.eemicraft.refuge.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Données persistantes d'un joueur (tenues par le module Refuge). */
public interface Profile {
    long ferraille();

    /** n peut être négatif ; le solde ne descend jamais sous 0. */
    void addFerraille(long n);

    /** Dernier acte débloqué (1 au départ). */
    int act();

    void setAct(int act);

    int level();

    void addXp(int xp);

    /** Objets assurés pour l'expédition en cours (rendus au Refuge si le joueur meurt). */
    List<ItemStack> insured();

    void setInsured(List<ItemStack> items);

    void save();
}
