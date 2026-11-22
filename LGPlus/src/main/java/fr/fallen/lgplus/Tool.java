/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package fr.fallen.lgplus;

import org.bukkit.Material;

enum Tool {
    CLAW(Material.BONE, "\u00a7c\u00a7lGriffe", "lg tuer %s"),
    CRYSTAL(Material.AMETHYST_SHARD, "\u00a75\u00a7lBoule de cristal", "lg voir %s"),
    PROTECT(Material.TOTEM_OF_UNDYING, "\u00a7b\u00a7lProtection", "lg proteger %s"),
    LIFE(Material.GLISTERING_MELON_SLICE, "\u00a7a\u00a7lPotion de vie", "lg soigner %s"),
    DEATH(Material.FERMENTED_SPIDER_EYE, "\u00a72\u00a7lPotion de mort", "lg empoisonner %s"),
    NOTHING(Material.BARRIER, "\u00a77\u00a7lTerminer mon tour", null),
    ARROW(Material.ARROW, "\u00a7d\u00a7lFl\u00e8che de Cupidon", null),
    SPY(Material.SPYGLASS, "\u00a7e\u00a7lLongue-vue", "lg espionner %s"),
    DAGGER(Material.IRON_SWORD, "\u00a74\u00a7lDague", "lg assassiner %s"),
    BALLOT(Material.PAPER, "\u00a7f\u00a7lBulletin de vote", "vote %s"),
    NIGHT(Material.CLOCK, "\u00a79\u00a7lNuit", null),
    MAYOR(Material.GOLDEN_HELMET, "\u00a76\u00a7l\u00c9lection du maire", null),
    BOOK(Material.WRITTEN_BOOK, "\u00a7e\u00a7lLivre des r\u00f4les", null, 7),
    SKIP(Material.PAPER, "\u00a7a\u00a7lPasser \u00e0 la suite", null, 8);

    final Material material;
    final String name;
    final String command;
    /** Emplacement fixe dans la barre (-1 = premier emplacement libre entre 0 et 6). */
    final int slot;

    Tool(Material material, String name, String command) {
        this(material, name, command, -1);
    }

    Tool(Material material, String name, String command, int slot) {
        this.material = material;
        this.name = name;
        this.command = command;
        this.slot = slot;
    }
}

