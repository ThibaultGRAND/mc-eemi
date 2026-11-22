package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.EemiRefuge;
import fr.eemicraft.refuge.Txt;
import fr.eemicraft.refuge.api.ExpeditionApi;
import fr.eemicraft.refuge.api.ZoneInfo;
import fr.eemicraft.refuge.ui.Menu;
import fr.eemicraft.refuge.ui.Ui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Escouades du Refuge (1 à 4 joueurs) et matchmaking.
 * - Un groupe EemiCore (/party) est automatiquement une escouade (lu par réflexion dans EemiCore : champ privé
 *   partyOf Map&lt;UUID, Party&gt;, Party.leader / Party.members, membres = chef compris).
 * - Sinon escouades du mode : créer (ouverte), inviter, rejoindre, quitter, exclure, passer chef.
 * - Seul et sans escouade : une escouade privée d'une personne est créée à l'ouverture du menu Expédition.
 * - File publique : deux escouades qui cherchent la même zone sont fusionnées si le total tient (≤ 4 et ≤ max zone).
 */
final class Escouades {

    static final int MAX = 4;

    static final class Squad {
        final UUID id = UUID.randomUUID();
        UUID leader;
        final List<UUID> members = new ArrayList<>();
        /** Chef du groupe EemiCore dont vient l'escouade (null = escouade du mode). */
        UUID party;
        /** Membres venus du groupe EemiCore (les autres ont rejoint par la file). */
        final Set<UUID> fromParty = new LinkedHashSet<>();
        final Set<UUID> ready = new LinkedHashSet<>();
        boolean open;
        String zone;
        boolean night;
        boolean searching;

        boolean isParty() { return party != null; }
    }

    record Invite(UUID squad, UUID from, long until) { }

    private final EemiRefuge plugin;
    private final Refuge refuge;
    private final Map<UUID, Squad> byPlayer = new HashMap<>();
    private final List<Squad> squads = new ArrayList<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    // réflexion EemiCore
    private boolean partyTried;
    private Field partyOf, fLeader, fMembers;
    private Plugin core;

    Escouades(EemiRefuge plugin, Refuge refuge) {
        this.plugin = plugin;
        this.refuge = refuge;
    }

    // ------------------------------------------------------------------ groupes EemiCore

    private void initParty() {
        if (partyTried) return;
        partyTried = true;
        core = Bukkit.getPluginManager().getPlugin("EemiCore");
        if (core == null) {
            plugin.getLogger().info("EemiCore absent : escouades du mode seulement.");
            return;
        }
        try {
            partyOf = core.getClass().getDeclaredField("partyOf");
            partyOf.setAccessible(true);
            plugin.getLogger().info("Groupes EemiCore lus par reflexion : un /party = une escouade.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            partyOf = null;
            plugin.getLogger().warning("Groupes EemiCore illisibles (" + ex + ") : escouades du mode seulement.");
        }
    }

    /** Groupes EemiCore : chef → membres (chef compris), ou vide si illisible. */
    private Map<UUID, List<UUID>> parties() {
        initParty();
        Map<UUID, List<UUID>> out = new HashMap<>();
        if (partyOf == null || core == null || !core.isEnabled()) return out;
        try {
            Map<?, ?> map = (Map<?, ?>) partyOf.get(core);
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            for (Object party : map.values()) {
                if (party == null || seen.put(party, Boolean.TRUE) != null) continue;
                if (fLeader == null) {
                    fLeader = party.getClass().getDeclaredField("leader");
                    fMembers = party.getClass().getDeclaredField("members");
                    fLeader.setAccessible(true);
                    fMembers.setAccessible(true);
                }
                UUID leader = (UUID) fLeader.get(party);
                List<UUID> members = new ArrayList<>();
                for (Object o : (List<?>) fMembers.get(party)) if (o instanceof UUID u) members.add(u);
                if (leader != null && !members.isEmpty()) out.put(leader, members);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Lecture des groupes EemiCore impossible (" + ex + ") : desactivee.");
            partyOf = null;
        }
        return out;
    }

    /** Synchronise les escouades « groupe » avec les groupes EemiCore (toutes les secondes). */
    void sync() {
        Map<UUID, List<UUID>> parties = parties();
        boolean changed = false;
        // groupes disparus → escouades du mode
        for (Squad s : new ArrayList<>(squads)) {
            if (s.isParty() && !parties.containsKey(s.party)) {
                s.party = null;
                s.fromParty.clear();
                changed = true;
                notifyMenus(s);
            }
        }
        for (Map.Entry<UUID, List<UUID>> e : parties.entrySet()) {
            Set<UUID> want = new LinkedHashSet<>();
            for (UUID u : e.getValue()) if (Bukkit.getPlayer(u) != null) want.add(u);
            if (want.isEmpty()) continue;
            Squad s = null;
            for (Squad q : squads) if (e.getKey().equals(q.party)) s = q;
            if (s == null) {
                // l'escouade du chef devient celle du groupe s'il en avait une
                Squad own = byPlayer.get(e.getKey());
                if (own != null && !own.isParty() && e.getKey().equals(own.leader)) s = own;
                else {
                    s = new Squad();
                    squads.add(s);
                }
                s.party = e.getKey();
                changed = true;
            }
            for (UUID u : new ArrayList<>(s.fromParty))
                if (!want.contains(u)) {
                    s.fromParty.remove(u);
                    removeMember(s, u, false);
                    changed = true;
                }
            for (UUID u : want) {
                if (s.members.contains(u)) {
                    s.fromParty.add(u);
                    continue;
                }
                Squad old = byPlayer.get(u);
                if (old != null && old != s) removeMember(old, u, true);
                s.members.add(u);
                s.fromParty.add(u);
                byPlayer.put(u, s);
                changed = true;
            }
            if (!e.getKey().equals(s.leader) && s.members.contains(e.getKey())) {
                s.leader = e.getKey();
                changed = true;
            }
            if (changed) notifyMenus(s);
            changed = false;
        }
        // invitations expirées
        long now = System.currentTimeMillis();
        invites.values().removeIf(i -> i.until() < now);
    }

    // ------------------------------------------------------------------ accès

    Squad of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    /** Escouade du joueur ; s'il n'en a pas, une escouade privée à lui seul. */
    Squad ensure(Player p) {
        Squad s = byPlayer.get(p.getUniqueId());
        if (s != null) return s;
        s = new Squad();
        s.leader = p.getUniqueId();
        s.members.add(p.getUniqueId());
        squads.add(s);
        byPlayer.put(p.getUniqueId(), s);
        return s;
    }

    List<Squad> openSquads(Player viewer) {
        List<Squad> out = new ArrayList<>();
        for (Squad s : squads) {
            if (!s.open || s.isParty() || s.members.size() >= MAX || s.members.contains(viewer.getUniqueId())) continue;
            Player l = Bukkit.getPlayer(s.leader);
            if (l == null || !refuge.atRefuge(l) || inExpe(l)) continue;
            out.add(s);
        }
        return out;
    }

    List<Player> players(Squad s) {
        List<Player> out = new ArrayList<>();
        for (UUID u : s.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    boolean inExpe(Player p) {
        ExpeditionApi e = plugin.expe();
        try {
            return e != null && e.inExpedition(p);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ membres

    private void removeMember(Squad s, UUID u, boolean silent) {
        s.members.remove(u);
        s.ready.remove(u);
        s.fromParty.remove(u);
        if (byPlayer.get(u) == s) byPlayer.remove(u);
        if (s.members.isEmpty()) {
            squads.remove(s);
            return;
        }
        if (u.equals(s.leader)) {
            s.leader = s.members.get(0);
            if (!silent) broadcast(s, "§e" + name(s.leader) + "§7 est le nouveau chef de l'escouade.");
        }
        notifyMenus(s);
    }

    /** Le joueur quitte son escouade (refusé dans un groupe /party). */
    boolean leave(Player p, boolean announce) {
        Squad s = byPlayer.get(p.getUniqueId());
        if (s == null) return false;
        if (s.isParty() && s.fromParty.contains(p.getUniqueId())) {
            Txt.msg(p, "Ton escouade est ton groupe : quitte-le avec §f/party leave§7.");
            return false;
        }
        removeMember(s, p.getUniqueId(), false);
        if (announce) {
            broadcast(s, "§f" + p.getName() + "§7 a quitté l'escouade.");
            Txt.msg(p, "Tu as quitté l'escouade.");
        }
        return true;
    }

    void quit(Player p) {
        Squad s = byPlayer.get(p.getUniqueId());
        invites.remove(p.getUniqueId());
        if (s == null) return;
        if (s.isParty() && s.fromParty.contains(p.getUniqueId())) {
            s.ready.remove(p.getUniqueId());
            return; // le groupe EemiCore gère les départs (sync)
        }
        removeMember(s, p.getUniqueId(), true);
        if (!s.members.isEmpty()) broadcast(s, "§f" + p.getName() + "§7 s'est déconnecté et a quitté l'escouade.");
    }

    /** Hors du Refuge (pas en expédition) : plus prêt. */
    void leftRefuge(Player p) {
        Squad s = byPlayer.get(p.getUniqueId());
        if (s != null && s.ready.remove(p.getUniqueId())) notifyMenus(s);
    }

    String create(Player p) {
        Squad s = byPlayer.get(p.getUniqueId());
        if (s != null && s.isParty()) return "Ton groupe (/party) est déjà ton escouade.";
        if (s != null && s.members.size() > 1) return "Tu es déjà dans une escouade.";
        s = ensure(p);
        if (s.open) return "Ton escouade est déjà ouverte : d'autres joueurs peuvent la rejoindre.";
        s.open = true;
        notifyMenus(s);
        return null;
    }

    String invite(Player from, Player to) {
        Squad s = ensure(from);
        if (!s.leader.equals(from.getUniqueId())) return "Seul le chef peut inviter.";
        if (s.isParty()) return "Ton escouade est ton groupe : invite avec §f/party invite " + to.getName() + "§7.";
        if (s.members.size() >= MAX) return "Ton escouade est complète (4).";
        if (s.members.contains(to.getUniqueId())) return to.getName() + " est déjà dans ton escouade.";
        if (!refuge.atRefuge(to)) return to.getName() + " n'est pas au Refuge.";
        invites.put(to.getUniqueId(), new Invite(s.id, from.getUniqueId(), System.currentTimeMillis() + 60_000));
        s.open = true;
        Txt.msg(to, "§f" + from.getName() + "§7 t'invite dans son escouade.");
        Component accept = Txt.c("  §a§l[REJOINDRE]").clickEvent(ClickEvent.runCommand("/refuge escouade accepter " + from.getName()))
                .hoverEvent(HoverEvent.showText(Txt.c("§7Rejoindre l'escouade de " + from.getName())));
        Component deny = Txt.c("  §c§l[REFUSER]").clickEvent(ClickEvent.runCommand("/refuge escouade refuser"))
                .hoverEvent(HoverEvent.showText(Txt.c("§7Refuser l'invitation")));
        to.sendMessage(accept.append(deny).append(Txt.c(" §8(60 s)")));
        to.playSound(to.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
        return null;
    }

    String accept(Player p, String fromName) {
        Invite inv = invites.get(p.getUniqueId());
        if (inv == null || inv.until() < System.currentTimeMillis()) return "Aucune invitation en cours.";
        Player from = Bukkit.getPlayer(inv.from());
        if (fromName != null && from != null && !from.getName().equalsIgnoreCase(fromName)) return "Aucune invitation de ce joueur.";
        Squad target = null;
        for (Squad s : squads) if (s.id.equals(inv.squad())) target = s;
        invites.remove(p.getUniqueId());
        if (target == null) return "Cette escouade n'existe plus.";
        return join(p, target);
    }

    void deny(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) {
            Txt.msg(p, "Aucune invitation en cours.");
            return;
        }
        Txt.msg(p, "Invitation refusée.");
        Player from = Bukkit.getPlayer(inv.from());
        if (from != null) Txt.msg(from, "§f" + p.getName() + "§7 a refusé ton invitation.");
    }

    String join(Player p, Squad target) {
        Squad cur = byPlayer.get(p.getUniqueId());
        if (cur == target) return "Tu es déjà dans cette escouade.";
        if (cur != null && cur.isParty() && cur.fromParty.contains(p.getUniqueId()))
            return "Tu es dans un groupe (/party) : c'est ton escouade.";
        if (!refuge.atRefuge(p)) return "Rejoins d'abord le Refuge (/refuge).";
        if (inExpe(p)) return "Tu es en expédition.";
        if (target.members.size() >= MAX) return "Cette escouade est complète.";
        if (cur != null) removeMember(cur, p.getUniqueId(), false);
        target.members.add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), target);
        target.ready.clear();
        broadcast(target, "§f" + p.getName() + "§7 a rejoint l'escouade.");
        notifyMenus(target);
        return null;
    }

    String kick(Player leader, UUID target) {
        Squad s = byPlayer.get(leader.getUniqueId());
        if (s == null || !s.leader.equals(leader.getUniqueId())) return "Seul le chef peut exclure.";
        if (!s.members.contains(target) || target.equals(leader.getUniqueId())) return "Ce joueur n'est pas dans ton escouade.";
        if (s.fromParty.contains(target)) return "Ce joueur vient de ton groupe : exclus-le avec /party kick.";
        removeMember(s, target, true);
        Player t = Bukkit.getPlayer(target);
        if (t != null) Txt.msg(t, "Tu as été exclu de l'escouade.");
        broadcast(s, "§f" + name(target) + "§7 a été exclu de l'escouade.");
        return null;
    }

    String promote(Player leader, UUID target) {
        Squad s = byPlayer.get(leader.getUniqueId());
        if (s == null || !s.leader.equals(leader.getUniqueId())) return "Seul le chef peut passer la main.";
        if (s.isParty()) return "Le chef de l'escouade est celui du groupe (/party).";
        if (!s.members.contains(target)) return "Ce joueur n'est pas dans ton escouade.";
        s.leader = target;
        s.ready.clear();
        broadcast(s, "§e" + name(target) + "§7 est le nouveau chef de l'escouade.");
        notifyMenus(s);
        return null;
    }

    // ------------------------------------------------------------------ expédition

    List<ZoneInfo> zones() {
        ExpeditionApi e = plugin.expe();
        if (e == null) return List.of();
        try {
            List<ZoneInfo> z = e.zones();
            return z == null ? List.of() : z;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("zones() : " + ex);
            return List.of();
        }
    }

    ZoneInfo zone(Squad s) {
        List<ZoneInfo> zs = zones();
        if (zs.isEmpty()) return null;
        for (ZoneInfo z : zs) if (z.id().equals(s.zone)) return z;
        s.zone = zs.get(0).id();
        return zs.get(0);
    }

    void toggleReady(Player p) {
        Squad s = ensure(p);
        if (!s.ready.remove(p.getUniqueId())) s.ready.add(p.getUniqueId());
        notifyMenus(s);
    }

    void toggleSearch(Player p) {
        Squad s = ensure(p);
        if (!s.leader.equals(p.getUniqueId())) {
            Txt.msg(p, "Seul le chef lance la recherche de coéquipiers.");
            Ui.error(p);
            return;
        }
        ZoneInfo z = zone(s);
        if (z == null) {
            Txt.msg(p, "Aucune zone n'est disponible pour l'instant.");
            Ui.error(p);
            return;
        }
        s.searching = !s.searching;
        broadcast(s, s.searching ? "Recherche de coéquipiers pour §f" + z.name() + "§7… (file publique)"
                : "Recherche de coéquipiers arrêtée.");
        notifyMenus(s);
    }

    /** File publique : fusionne les escouades qui cherchent la même zone (toutes les 2 s). */
    void matchmaking() {
        List<Squad> q = new ArrayList<>();
        for (Squad s : squads) {
            if (!s.searching) continue;
            Player l = Bukkit.getPlayer(s.leader);
            if (l == null || !refuge.atRefuge(l) || inExpe(l)) {
                s.searching = false;
                continue;
            }
            q.add(s);
        }
        q.sort((a, b) -> b.members.size() - a.members.size());
        for (int i = 0; i < q.size(); i++) {
            Squad a = q.get(i);
            if (!a.searching) continue;
            ZoneInfo z = zone(a);
            int cap = z == null ? MAX : Math.min(MAX, Math.max(1, z.maxPlayers()));
            for (int j = i + 1; j < q.size(); j++) {
                Squad b = q.get(j);
                if (!b.searching || b == a || !java.util.Objects.equals(a.zone, b.zone)) continue;
                if (a.isParty() && b.isParty()) continue;
                if (a.members.size() + b.members.size() > cap) continue;
                Squad host = b.isParty() ? b : a, guest = host == a ? b : a;
                for (UUID u : new ArrayList<>(guest.members)) {
                    guest.members.remove(u);
                    host.members.add(u);
                    byPlayer.put(u, host);
                }
                squads.remove(guest);
                guest.searching = false;
                host.ready.clear();
                host.open = true;
                if (z != null && host.members.size() >= cap) host.searching = false;
                broadcast(host, "§aCoéquipiers trouvés !§7 Escouade de §f" + name(host.leader) + "§7 ("
                        + host.members.size() + " joueurs). Mettez-vous prêts.");
                for (Player p : players(host)) Ui.success(p);
                notifyMenus(host);
                if (host != a) break;
            }
        }
    }

    /** Lancement par le chef. null = parti. */
    String depart(Player leader) {
        Squad s = ensure(leader);
        if (!s.leader.equals(leader.getUniqueId())) return "Seul le chef de l'escouade lance l'expédition.";
        ZoneInfo z = zone(s);
        if (z == null) return "Aucune zone n'est disponible pour l'instant.";
        List<Player> team = new ArrayList<>();
        for (UUID u : s.members) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) return name(u) + " n'est pas connecté.";
            team.add(p);
        }
        if (team.size() > MAX) return "Une escouade compte 4 joueurs au plus (ton groupe en a " + team.size() + ").";
        if (team.size() < z.minPlayers()) return z.name() + " demande au moins " + z.minPlayers() + " joueurs.";
        if (team.size() > z.maxPlayers()) return z.name() + " accepte " + z.maxPlayers() + " joueurs au plus.";
        if (z.freeInstances() <= 0) return "Toutes les instances de " + z.name() + " sont occupées : réessaie dans un moment.";
        for (Player p : team) {
            if (!refuge.atRefuge(p)) return p.getName() + " n'est pas au Refuge.";
            if (inExpe(p)) return p.getName() + " est déjà en expédition.";
            if (refuge.profile(p.getUniqueId()).act() < z.act())
                return p.getName() + " n'a pas débloqué l'acte " + z.act() + ".";
            if (!p.equals(leader) && !s.ready.contains(p.getUniqueId())) return p.getName() + " n'est pas prêt.";
        }
        for (Player p : team) {
            refuge.checkInsurance(p);
            if (Ui.openMenu(p) != null) p.closeInventory();
        }
        String err;
        try {
            err = plugin.expe().start(leader, team, z.id(), s.night);
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("expe().start : " + ex);
            ex.printStackTrace();
            err = "Erreur au lancement de l'expédition (voir la console).";
        }
        if (err != null) return err;
        s.ready.clear();
        s.searching = false;
        return null;
    }

    // ------------------------------------------------------------------ outils

    void broadcast(Squad s, String msg) {
        for (Player p : players(s)) Txt.msg(p, msg);
    }

    static String name(UUID u) {
        Player p = Bukkit.getPlayer(u);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    /** Rouvre les menus Expédition / Escouade ouverts par les membres pour montrer le nouvel état. */
    void notifyMenus(Squad s) {
        for (Player p : players(s)) {
            Menu m = Ui.openMenu(p);
            if (m instanceof Ecrans.Expedition || m instanceof Ecrans.Escouade) refuge.reopen(p, m);
        }
    }
}
