package fr.eemicraft.wizards;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Les 10 classes de sorcier. Chacune a sa baguette (objet distinct) : clic droit = sort principal
 * (coûte du mana), clic gauche = sort secondaire (temps de recharge). Les valeurs des sorts sont dans
 * le config (classes.<id>.<clé>), avec des défauts dans le code (EemiWizards#spell).
 */
enum Wizard {
    KINETIQUE("kinetique", "Kinétique", NamedTextColor.WHITE, Material.BREEZE_ROD,
            "Orbe explosif", "Une orbe qui explose à l'impact et projette les ennemis.",
            "Saut propulsé", "Te propulse loin dans la direction visée."),
    GLACE("glace", "Glace", NamedTextColor.AQUA, Material.DIAMOND_HOE,
            "Rayon de glace", "Rayon instantané qui blesse et ralentit.",
            "Onde de gel", "Gèle et ralentit fortement les ennemis autour de toi."),
    FEU("feu", "Feu", NamedTextColor.GOLD, Material.BLAZE_ROD,
            "Boule de feu", "Boule de feu qui explose et enflamme.",
            "Jet de flammes", "Cône de flammes devant toi, enflamme les ennemis."),
    WITHER("wither", "Wither", NamedTextColor.DARK_GRAY, Material.NETHERITE_HOE,
            "Crâne de wither", "Crâne rapide qui flétrit et vole un peu de vie.",
            "Volée de crânes", "Trois crânes en éventail."),
    SANG("sang", "Sang", NamedTextColor.DARK_RED, Material.REDSTONE_TORCH,
            "Rayon de sang", "Rayon qui vole la vie de l'ennemi touché.",
            "Soin de zone", "Soigne ton équipe autour de toi (et toi)."),
    TEMPETE("tempete", "Tempête", NamedTextColor.YELLOW, Material.LIGHTNING_ROD,
            "Éclair ciblé", "La foudre tombe là où tu vises.",
            "Bourrasque", "Repousse et blesse les ennemis proches."),
    TOXIQUE("toxique", "Toxique", NamedTextColor.GREEN, Material.SLIME_BALL,
            "Fiole acide", "Fiole qui éclate et empoisonne autour de l'impact.",
            "Nuage toxique", "Nuage posé à tes pieds qui ronge les ennemis."),
    HYDRO("hydro", "Hydro", NamedTextColor.BLUE, Material.PRISMARINE_SHARD,
            "Jet d'eau", "Jet instantané qui blesse et repousse.",
            "Vague", "Balaie et repousse devant toi, soigne et éteint tes alliés."),
    TERRE("terre", "Terre", NamedTextColor.GOLD, Material.FLINT,
            "Éclat de roche", "Rocher lent mais lourd, ralentit la cible.",
            "Séisme", "Le sol tremble : les ennemis proches sont projetés en l'air."),
    ARCANE("arcane", "Arcane", NamedTextColor.LIGHT_PURPLE, Material.AMETHYST_SHARD,
            "Missile arcanique", "Petit projectile très rapide et peu coûteux.",
            "Clignement", "Te téléporte de quelques blocs devant toi.");

    final String id, label;
    final NamedTextColor color;
    final Material wand;
    final String primary, primaryDesc, secondary, secondaryDesc;

    Wizard(String id, String label, NamedTextColor color, Material wand, String primary, String primaryDesc,
           String secondary, String secondaryDesc) {
        this.id = id;
        this.label = label;
        this.color = color;
        this.wand = wand;
        this.primary = primary;
        this.primaryDesc = primaryDesc;
        this.secondary = secondary;
        this.secondaryDesc = secondaryDesc;
    }

    static Wizard byId(String id) {
        if (id == null) return null;
        String k = id.toLowerCase(Locale.ROOT);
        for (Wizard w : values()) if (w.id.equals(k)) return w;
        return null;
    }
}
