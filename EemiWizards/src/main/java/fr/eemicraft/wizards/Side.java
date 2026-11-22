package fr.eemicraft.wizards;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;

/** Les deux équipes : Rouge à l'ouest (x négatif), Bleu à l'est (x positif). */
enum Side {
    ROUGE("Rouge", "Rouges", NamedTextColor.RED, Color.fromRGB(0xB02E26), BossBar.Color.RED, -1, "red"),
    BLEU("Bleu", "Bleus", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), BossBar.Color.BLUE, 1, "blue");

    final String label, plural;
    final NamedTextColor color;
    final Color armor;
    final BossBar.Color bar;
    /** Signe en x du camp, et sens du contrôle des points (Bleu = +100, Rouge = -100). */
    final int sign;
    /** Préfixe des blocs de couleur (red_concrete, blue_stained_glass...). */
    final String dye;

    Side(String label, String plural, NamedTextColor color, Color armor, BossBar.Color bar, int sign, String dye) {
        this.label = label;
        this.plural = plural;
        this.color = color;
        this.armor = armor;
        this.bar = bar;
        this.sign = sign;
        this.dye = dye;
    }

    /** Nom accordé avec « équipe » ou « robe » (féminin) : Rouge, Bleue. */
    String fem() {
        return this == BLEU ? "Bleue" : label;
    }

    Side other() {
        return this == ROUGE ? BLEU : ROUGE;
    }

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    static Side ofSign(int s) {
        return s < 0 ? ROUGE : BLEU;
    }
}
