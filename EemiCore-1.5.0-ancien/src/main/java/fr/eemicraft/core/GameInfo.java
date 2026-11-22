package fr.eemicraft.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Un jeu du registre (section games: du config) et son dernier statut connu.
 * Les jeux maison envoient « eemi status » ; pour les jeux tiers (ou un statut expiré),
 * le nombre de joueurs est déduit des joueurs présents dans les mondes du jeu.
 */
final class GameInfo {

    final String id;
    String name;
    String join = "";
    List<String> joinAliases = new ArrayList<>(); // autres commandes de join (bloquées en maintenance)
    String spectate = "";
    List<String> worlds = new ArrayList<>();
    int min = 2;
    int max = 16;
    double multiplier = 1.0;
    Material icon = Material.PAPER;

    // dernier statut reçu (eemi status)
    String state = null;      // waiting | starting | playing | ending, null = inconnu
    int waiting;
    int playing;
    long lastUpdate;          // ms

    GameInfo(String id) {
        this.id = id;
        this.name = id;
    }

    /** Vrai si un statut a été reçu il y a moins de expireMs. */
    boolean fresh(long expireMs) {
        return state != null && System.currentTimeMillis() - lastUpdate < expireMs;
    }

    /** Joueurs présents dans les mondes du jeu (statut déduit). */
    int worldPlayers() {
        int n = 0;
        for (String w : worlds) {
            World world = Bukkit.getWorld(w);
            if (world != null) n += world.getPlayers().size();
        }
        return n;
    }

    boolean hasWorld(String world) {
        for (String w : worlds) if (w.equalsIgnoreCase(world)) return true;
        return false;
    }
}
