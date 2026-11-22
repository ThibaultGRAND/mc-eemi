/*
 * Decompiled with CFR 0.152.
 */
package fr.fallen.lgplus;

import fr.fallen.lgplus.Tool;
import java.util.EnumSet;
import java.util.Set;

enum Turn {
    CUPID("Cupidon", "Cupidon se r\u00e9veille et choisit deux amoureux...", 20, Set.of("CUPID"), EnumSet.of(Tool.ARROW)),
    SEER("Voyante", "La voyante se r\u00e9veille...", 20, Set.of("SEER"), EnumSet.of(Tool.CRYSTAL)),
    GUARD("Garde", "Le garde se r\u00e9veille...", 20, Set.of("GUARD"), EnumSet.of(Tool.PROTECT)),
    WOLVES("Loups-garous", "Les loups-garous se r\u00e9veillent et choisissent leur victime...", 30, Set.of("WEREWOLF", "LITTLE_GIRL"), EnumSet.of(Tool.CLAW, Tool.SPY)),
    WITCH("Sorci\u00e8re", "La sorci\u00e8re se r\u00e9veille...", 20, Set.of("WITCH"), EnumSet.of(Tool.LIFE, Tool.DEATH, Tool.NOTHING)),
    ASSASSIN("Assassin", "L'assassin se r\u00e9veille...", 20, Set.of("ASSASSIN"), EnumSet.of(Tool.DAGGER));

    final String label;
    final String wake;
    final int seconds;
    final Set<String> roles;
    final Set<Tool> tools;

    private Turn(String label, String wake, int seconds, Set<String> roles, Set<Tool> tools) {
        this.label = label;
        this.wake = wake;
        this.seconds = seconds;
        this.roles = roles;
        this.tools = tools;
    }
}

