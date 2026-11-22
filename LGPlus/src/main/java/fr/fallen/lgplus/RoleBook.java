package fr.fallen.lgplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Livre des rôles. Les 10 rôles sont ceux de l'enum PlayerRole du jar LoupGarou 1.0.0 (Modrinth) :
 * WEREWOLF, VILLAGER, SEER, GUARD, WITCH, HUNTER, ANCIENT, CUPID, LITTLE_GIRL, ASSASSIN.
 * Descriptions vérifiées sur le bytecode (messages et règles de GameManager / LoupGarouCommand).
 */
final class RoleBook {
    private record Role(String id, String name, NamedTextColor color, String camp, String text) {
    }

    private static final List<Role> ROLES = List.of(
            new Role("WEREWOLF", "Loup-Garou", NamedTextColor.DARK_RED, "Camp des loups",
                    "Chaque nuit, les loups se réveillent ensemble et votent pour dévorer un joueur (objet Nuit). "
                            + "La nuit, un message commençant par ! n'est lu que par les loups."),
            new Role("VILLAGER", "Villageois", NamedTextColor.DARK_GREEN, "Village",
                    "Aucun pouvoir. Le jour, débat et vote avec le bulletin pour éliminer un suspect."),
            new Role("SEER", "Voyante", NamedTextColor.DARK_PURPLE, "Village",
                    "Chaque nuit, à son tour, découvre le rôle exact d'un joueur."),
            new Role("GUARD", "Garde", NamedTextColor.DARK_AQUA, "Village",
                    "Chaque nuit, protège un joueur de l'attaque des loups. Il peut se proteger lui-même une seule fois par partie."),
            new Role("WITCH", "Sorcière", NamedTextColor.LIGHT_PURPLE, "Village",
                    "Joue après les loups. Potion de vie : sauve la victime des loups. Potion de mort : tue un joueur. "
                            + "Chaque potion ne sert qu'une fois."),
            new Role("HUNTER", "Chasseur", NamedTextColor.GOLD, "Village",
                    "Pas d'action la nuit. À sa mort, il a 20 s pour emporter un joueur avec lui (clic sur un nom dans le chat)."),
            new Role("ANCIENT", "Ancien", NamedTextColor.DARK_AQUA, "Village",
                    "Pas d'action. Il survit à la première attaque des loups."),
            new Role("CUPID", "Cupidon", NamedTextColor.LIGHT_PURPLE, "Village",
                    "La première nuit, il lie deux amoureux. Si l'un meurt, l'autre meurt de chagrin."),
            new Role("LITTLE_GIRL", "Petite Fille", NamedTextColor.GOLD, "Village",
                    "Chaque nuit, pendant le tour des loups, espionne un joueur pour savoir s'il est loup ou non."),
            new Role("ASSASSIN", "Assassin", NamedTextColor.DARK_RED, "Camp des loups",
                    "Une seule fois dans la partie, élimine un joueur la nuit (pas un allié des loups). "
                            + "Compte comme un loup pour la victoire."));

    private RoleBook() {
    }

    /** @param present rôles distribués dans la partie en cours, ou null hors partie (tous les rôles). */
    static Book build(Collection<String> present, boolean mayor) {
        List<Component> pages = new ArrayList<>();
        Component intro = Component.text()
                .append(Component.text("Loup-Garou", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text(present == null
                        ? "Tous les rôles possibles. Pendant une partie, ce livre ne montre que les rôles distribués."
                        : "Rôles présents dans cette partie :", NamedTextColor.BLACK))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("Le village gagne si tous les loups meurent. Les loups gagnent "
                        + "s'ils sont aussi nombreux que le village.", NamedTextColor.DARK_GRAY))
                .build();
        pages.add(intro);
        for (Role r : ROLES) {
            if (present != null && !present.contains(r.id)) continue;
            pages.add(Component.text()
                    .append(Component.text(r.name, r.color, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(r.camp, r.camp.startsWith("Village") ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED))
                    .append(Component.newline()).append(Component.newline())
                    .append(Component.text(r.text, NamedTextColor.BLACK))
                    .build());
        }
        if (mayor) {
            pages.add(Component.text()
                    .append(Component.text("Maire", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline()).append(Component.newline())
                    .append(Component.text("Élu le premier jour avec le casque doré. En cas d'égalité au vote, "
                            + "c'est lui qui decide. S'il meurt, il désigne son successeur.", NamedTextColor.BLACK))
                    .build());
        }
        pages.add(Component.text()
                .append(Component.text("Objets", NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text("Horloge : actions de la nuit\nBulletin : vote du village\n"
                        + "Casque dore : election du maire\nPapier vert : voter pour passer à la suite\n"
                        + "Livre : ce guide", NamedTextColor.BLACK))
                .build());
        return Book.book(Component.text("Rôles"), Component.text("LG+"), pages);
    }
}
