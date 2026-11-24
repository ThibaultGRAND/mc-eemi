package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Sink;
import java.util.List;

/**
 * Petites maps : le terrain de speedball et trois maps a theme, toutes au meme endroit (centre 0, 0) et sur le meme
 * contrat. Une seule est posee a la fois ; laquelle est notee dans data.yml (small-map). Un id inconnu dans data.yml
 * (ex. « neige », retiree en 1.5.0, ou « scenario », remplacee par « tournoi » en 1.6.0) fait reconstruire le speedball au demarrage.
 */
final class SmallMaps {
    private SmallMaps() {
    }

    record Info(String id, String name) {
    }

    static final List<Info> ALL = List.of(
        new Info("speedball", "Terrain de speedball"),
        new Info("chantier", "Chantier"),
        new Info("western", "Western"),
        new Info("tournoi", "Terrain de tournoi"));

    static Info get(String id) {
        for (Info i : ALL) {
            if (i.id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return null;
    }

    static List<String> ids() {
        return ALL.stream().map(Info::id).toList();
    }

    /** Construit le terrain de la map (sans la salle d'attente). Le speedball pose aussi sa salle d'attente. */
    static void buildField(String id, Sink s) {
        switch (id) {
            case "chantier" -> new Chantier(s).build();
            case "western" -> new Western(s).build();
            case "tournoi" -> new Tournoi(s).build();
            default -> new Speedball(s).build();
        }
    }
}
