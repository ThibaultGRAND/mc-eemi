package fr.eemicraft.droits;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

/**
 * Applique les refus : commandes de join tapées (avant EemiCore), puis entrée dans un monde
 * (filet pour les PNJ, menus et /jouer qui lancent le join sans passer par le chat).
 */
final class Garde implements Listener {

    private final EemiDroits pl;

    Garde(EemiDroits pl) { this.pl = pl; }

    /** null = autorisé, sinon le message de refus. */
    String refus(Player p, Jeux.Jeu j) {
        if (pl.fiches.jeuInterdit(p.getUniqueId(), j.id)) return "Tu n'as pas accès à " + j.nom + "§7.";
        Jeux.Etat e = pl.jeux.etat(j.id);
        if (e == Jeux.Etat.MAINTENANCE && !p.hasPermission("eemidroits.bypass")) {
            String r = pl.jeux.raison(j.id);
            return j.nom + " §7est en §cmaintenance§7" + (r.isBlank() ? "." : " : " + r);
        }
        if (e == Jeux.Etat.BETA && !p.hasPermission("eemidroits.beta") && !p.hasPermission("eemidroits.bypass"))
            return j.nom + " §7est en §dbêta§7 : réservé aux bêta-testeurs.";
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommande(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String tape = Txt.normCommande(e.getMessage());
        Jeux.Jeu cible = null;
        if (tape.startsWith("eemi join ") || tape.startsWith("eemi spectate ")) {
            String[] a = tape.split(" ");
            if (a.length >= 3) cible = pl.jeux.jeu(a[2]);
        }
        if (cible == null) {
            for (Jeux.Jeu j : pl.jeux.jeux.values())
                if (pl.jeux.estCommandeDuJeu(j, tape)) { cible = j; break; }
        }
        if (cible == null) return;
        String r = refus(p, cible);
        if (r == null) return;
        e.setCancelled(true);
        p.sendMessage(Txt.PREFIXE + r);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        World de = e.getFrom().getWorld(), vers = to.getWorld();
        if (de == vers) return;
        Player p = e.getPlayer();
        String nom = vers.getName();
        if (estProtege(nom)) return;
        if (pl.fiches.mondeInterdit(p.getUniqueId(), nom)) {
            e.setCancelled(true);
            p.sendMessage(Txt.PREFIXE + "Tu n'as pas accès à ce monde.");
            return;
        }
        Jeux.Jeu j = pl.jeux.jeuDuMonde(nom);
        if (j == null) return;
        // Un joueur déjà dans le jeu passe d'un monde du jeu à l'autre : une partie en cours continue.
        Jeux.Jeu depuis = de == null ? null : pl.jeux.jeuDuMonde(de.getName());
        if (depuis == j && !pl.fiches.jeuInterdit(p.getUniqueId(), j.id)) return;
        String r = refus(p, j);
        if (r == null) return;
        e.setCancelled(true);
        p.sendMessage(Txt.PREFIXE + r);
        String sortie = pl.jeux.commandeSortie(j);
        if (sortie != null)
            Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) p.performCommand(sortie); }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArrivee(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Fiches.Fiche f = pl.fiches.fiches.get(p.getUniqueId());
        if (f != null && !p.getName().equals(f.nom)) { f.nom = p.getName(); pl.fiches.sauver(); }
        Bukkit.getScheduler().runTaskLater(pl, () -> verifier(p), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangement(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> verifier(p));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onReapparition(PlayerRespawnEvent e) {
        Location l = e.getRespawnLocation();
        if (l.getWorld() != null && !estProtege(l.getWorld().getName())
                && pl.fiches.mondeInterdit(e.getPlayer().getUniqueId(), l.getWorld().getName())) {
            Location hub = pl.positionHub();
            if (hub != null) e.setRespawnLocation(hub);
        }
    }

    /** Renvoie au hub un joueur qui se trouve dans un monde qui lui est interdit. */
    void verifier(Player p) {
        if (!p.isOnline()) return;
        String w = p.getWorld().getName();
        if (estProtege(w) || !pl.fiches.mondeInterdit(p.getUniqueId(), w)) return;
        Location hub = pl.positionHub();
        if (hub == null) return;
        p.sendMessage(Txt.PREFIXE + "Tu n'as pas accès à ce monde : retour au hub.");
        p.teleport(hub);
    }

    boolean estProtege(String monde) {
        for (String m : pl.getConfig().getStringList("mondes-proteges"))
            if (m.equalsIgnoreCase(monde)) return true;
        return monde.equalsIgnoreCase(pl.getConfig().getString("monde-hub", "lobby"));
    }
}
