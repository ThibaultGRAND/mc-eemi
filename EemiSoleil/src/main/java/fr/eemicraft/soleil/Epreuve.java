package fr.eemicraft.soleil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Une epreuve de la partie. Le moteur (EemiSoleil) l'annonce au dortoir, appelle {@link #begin}, puis {@link #tick}
 * a chaque tick jusqu'a ce qu'elle appelle {@link EemiSoleil#epreuveDone()} (ou {@link EemiSoleil#finaleResult} pour
 * la finale). Les eliminations passent par {@link EemiSoleil#eliminate} qui applique le quota (max) et le plancher de
 * 2 joueurs ; a la fin, le moteur complete jusqu'au minimum avec {@link #ranking()} (les moins bons d'abord).
 */
abstract class Epreuve {
    final EemiSoleil g;
    final String id;
    /** Quotas d'eliminations (en joueurs) calcules au debut, d'apres les % du config. */
    int minElim, maxElim, elimCount;
    /** Derniere epreuve avant la finale : doit laisser exactement 2 joueurs. */
    boolean last;
    boolean done;
    boolean failedOnce;
    int tick;

    Epreuve(EemiSoleil g, String id) {
        this.g = g;
        this.id = id;
    }

    abstract String nom();

    /** Trois lignes de regles affichees au dortoir avant l'epreuve. */
    abstract String[] regles();

    /** Point des spectateurs (elimines) pendant l'epreuve. */
    abstract Location specSpot();

    /** Boite de la zone (chunks a charger). */
    abstract int[] box();

    abstract void begin(List<EemiSoleil.Joueur> alive);

    void tick() {}

    /** Temps limite atteint (ou /soleil passer). */
    abstract void timeUp();

    /** Survivants classes du moins bon au meilleur, pour le departage. */
    abstract List<EemiSoleil.Joueur> ranking();

    String departage() {
        return "moins bon résultat de l'épreuve";
    }

    void onMove(Player p, EemiSoleil.Joueur j, PlayerMoveEvent e) {}

    /** true si le clic a ete pris par l'epreuve. */
    boolean onClick(Player p, EemiSoleil.Joueur j, PlayerInteractEvent e) {
        return false;
    }

    /** true si le coup a ete pris (poussee) ; tous les degats restent annules. */
    boolean onHit(Player attacker, EemiSoleil.Joueur ja, Player victim, EemiSoleil.Joueur jv) {
        return false;
    }

    /** Clic dans un menu de l'epreuve (appele au tick suivant, hors de l'evenement d'inventaire). */
    void onMenu(Player p, EemiSoleil.Joueur j, int slot, SquidMenu menu) {}

    /** Joueur parti (deconnexion, /lobby) : deja marque elimine par le moteur. */
    void onLeave(EemiSoleil.Joueur j) {}

    /** Nettoyage : entites, blocs remis, effets. */
    void cleanup() {}

    String barText() {
        return nom();
    }

    float barProgress() {
        return 1f;
    }

    /** Fin normale de l'epreuve. */
    void finish() {
        if (done) return;
        done = true;
        g.epreuveDone();
    }

    // ------------------------------------------------------------ outils

    List<EemiSoleil.Joueur> alive() {
        return g.aliveList();
    }

    static List<EemiSoleil.Joueur> shuffled(List<EemiSoleil.Joueur> l) {
        List<EemiSoleil.Joueur> c = new ArrayList<>(l);
        Collections.shuffle(c);
        return c;
    }

    static String time(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
