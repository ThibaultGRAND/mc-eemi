package fr.eemicraft.soleil;

import static fr.eemicraft.soleil.Layout.*;

import fr.eemicraft.soleil.EemiSoleil.Joueur;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Epreuve 3 : tir a la corde. Deux equipes egales sur deux plateformes au-dessus du gouffre, reliees par la corde.
 * Nombre impair : un joueur tire au sort est EXEMPTE (il passe directement, comme les chanceux de la serie) - choix
 * plus juste qu'une equipe en sous-nombre « compensee » en force, et lisible pour tout le monde.
 * <p>Tirage (2.0.2) : au spam. Chaque clic (droit ou gauche, corde en main) = +1 point et deplace la corde de
 * corde.pas (0,05 bloc) vers son camp ; au plus un clic compte par tick. La barre d'action montre ses clics et sa cadence.
 * La corde a corde.victoire (6) blocs de son cote = victoire ; au temps (corde.temps, 60 s), le camp ou se trouve la
 * corde gagne (a egalite : plus de points, puis tirage au sort). La plateforme des perdants s'effondre : elimines (dans
 * la limite du quota ; les gracies sont mis a l'abri avant).
 */
final class EpCorde extends Epreuve {
    enum Ph { READY, PULL, FALL, END }

    static final class T {
        final Joueur j;
        final int team; // -1 ouest (rouge), 1 est (bleu)
        final int phase;
        int points;
        long lastClick, lockUntil;
        final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();
        Location spot;

        T(Joueur j, int team, int phase) {
            this.j = j;
            this.team = team;
            this.phase = phase;
        }
    }

    Ph ph = Ph.READY;
    int phTick, phLen, pullTotal;
    double pos; // > 0 : corde tiree vers l'ouest (equipe -1 gagne)
    final Map<UUID, T> ts = new HashMap<>();
    Joueur exempt;
    final List<BlockDisplay> rope = new ArrayList<>();
    BlockDisplay marker;
    final Map<Block, BlockData> collapsed = new HashMap<>();

    EpCorde(EemiSoleil g) {
        super(g, "corde");
    }

    @Override String nom() { return "Tir à la corde"; }

    @Override
    String[] regles() {
        return new String[]{"Deux équipes égales sur deux plateformes au-dessus du vide (impair : un exempté au sort).",
                "Au signal TIREZ, clique le plus vite possible (clic gauche ou droit, corde en main) : chaque clic tire la corde.",
                "Amène la corde de ton côté : la plateforme des perdants s'effondre."};
    }

    @Override Location specSpot() { return new Location(g.world, CX + 0.5, CP + 12, -14.5, 0f, 30f); }

    @Override int[] box() { return SquidZones.CORDE_BOX; }

    Location exemptSpot() {
        return new Location(g.world, CX + 0.5, CP + 5, -19.5, 0f, 10f);
    }

    /** Plancher des deux plateformes remis s'il manque (plateforme restee effondree apres un arret brutal). */
    void repairFloors() {
        for (int side = -1; side <= 1; side += 2) {
            int near = CX + side * C_EDGE, far = CX + side * (C_EDGE + C_LEN - 1);
            for (int x = Math.min(near, far); x <= Math.max(near, far); x++)
                for (int z = -2; z <= 2; z++) {
                    Block b = g.world.getBlockAt(x, CP, z);
                    if (b.getType().isAir()) b.setType(x == near ? Material.WHITE_CONCRETE : Material.BIRCH_PLANKS, false);
                }
        }
    }

    @Override
    void begin(List<Joueur> alive) {
        repairFloors();
        List<Joueur> l = shuffled(alive);
        if (l.size() % 2 == 1) {
            exempt = l.remove(0);
            Player p = exempt.player();
            if (p != null) {
                p.teleport(exemptSpot());
                g.title(p, "&e&lEXEMPTÉ", "&7Le sort t'épargne cette épreuve", 2500);
            }
            g.tell("&e✦ n°" + exempt.numero() + " " + exempt.name + " &7est exempté (nombre impair).");
        }
        int half = l.size() / 2;
        int ia = 0, ib = 0;
        for (int k = 0; k < l.size(); k++) {
            Joueur j = l.get(k);
            int team = k < half ? -1 : 1;
            T t = new T(j, team, ThreadLocalRandom.current().nextInt(20));
            int i = team < 0 ? ia++ : ib++;
            t.spot = new Location(g.world, team < 0 ? CX - 8 - 2 * i + 0.5 : CX + 8 + 2 * i + 0.5, CP + 1, 0.5, team < 0 ? -90f : 90f, 5f);
            ts.put(j.id, t);
            Player p = j.player();
            if (p == null) continue;
            g.anchor(p, t.spot);
            g.clearHands(p);
            p.getInventory().setItem(0, g.skin(EemiSoleil.named(Material.LEAD, team < 0 ? "&cCorde (équipe rouge)" : "&9Corde (équipe bleue)",
                    "&7Clique le plus vite possible pour tirer"), "corde"));
            p.getInventory().setHeldItemSlot(0);
            EemiSoleil.msg(p, "&7Tu es dans l'équipe " + (team < 0 ? "&crouge &7(ouest)" : "&9bleue &7(est)") + "&7.");
        }
        spawnRope();
        g.guard(new Location(g.world, CX - 27.5, CP + 1, -2.5, -90f, 0f), 2);
        g.guard(new Location(g.world, CX - 27.5, CP + 1, 3.5, -90f, 0f), 1);
        g.guard(new Location(g.world, CX + 28.5, CP + 1, -2.5, 90f, 0f), 0);
        g.guard(new Location(g.world, CX + 28.5, CP + 1, 3.5, 90f, 0f), 2);
        ph = Ph.READY;
        phTick = 0;
        phLen = 100;
    }

    void spawnRope() {
        BlockData rd = Material.BROWN_WOOL.createBlockData();
        double len = 2 * (C_EDGE + C_LEN) + 2;
        g.spawning = true;
        try {
            BlockDisplay d = g.world.spawn(new Location(g.world, CX + 0.5 - len / 2, CP + 1.9, 0.44), BlockDisplay.class, e -> {
                e.setBlock(rd);
                e.setPersistent(false);
                e.setTeleportDuration(3);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) len, 0.12f, 0.12f), new AxisAngle4f()));
                e.addScoreboardTag(EemiSoleil.TAG);
            });
            rope.add(d);
            g.zoneEntities.add(d);
            marker = g.world.spawn(new Location(g.world, CX + 0.5 - 0.2, CP + 1.75, 0.3), BlockDisplay.class, e -> {
                e.setBlock(Material.RED_WOOL.createBlockData());
                e.setPersistent(false);
                e.setTeleportDuration(3);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.4f, 0.4f, 0.4f), new AxisAngle4f()));
                e.addScoreboardTag(EemiSoleil.TAG);
            });
            g.zoneEntities.add(marker);
        } finally {
            g.spawning = false;
        }
    }

    void moveRope() {
        double len = 2 * (C_EDGE + C_LEN) + 2;
        for (BlockDisplay d : rope) if (d.isValid()) d.teleport(new Location(g.world, CX + 0.5 - len / 2 - pos, CP + 1.9, 0.44));
        if (marker != null && marker.isValid()) marker.teleport(new Location(g.world, CX + 0.5 - 0.2 - pos, CP + 1.75, 0.3));
    }

    int period() {
        return Math.max(8, (int) Math.round(g.cfgD("corde.periode", 1.0) * 20));
    }

    /** Position du curseur 0..1 (aller-retour). */
    double cursor(T t) {
        int per = period();
        double f = ((g.world.getGameTime() + t.phase * per / 20) % per) / (double) per;
        return f < 0.5 ? f * 2 : 2 - f * 2;
    }

    /** 2 vert, 1 jaune, 0 rouge. */
    static int zone(double c) {
        double d = Math.abs(c - 0.5);
        return d <= 0.13 ? 2 : d <= 0.25 ? 1 : 0;
    }

    String barFor(T t) {
        // 2.0.2 : tirage au spam -> la barre montre la cadence du joueur (clics des 2 dernieres secondes)
        long now = g.world.getGameTime();
        int recent = 0;
        for (long c : t.recent) if (now - c <= 40) recent++;
        String sb = "&fClics : &e" + t.points + " &8(&f" + (recent / 2) + "/s&8)";
        double win = winDist();
        int side = (int) Math.round(Math.max(-1, Math.min(1, pos / win)) * 6);
        StringBuilder rp = new StringBuilder("&c◀ ");
        for (int k = -6; k <= 6; k++) rp.append(k == -side ? "&f█" : k == 0 ? "&7|" : "&8-");
        rp.append(" &9▶");
        return sb + "   " + rp;
    }

    double winDist() {
        return Math.max(2, g.cfgD("corde.victoire", 6.0));
    }

    @Override
    void tick() {
        phTick++;
        switch (ph) {
            case READY -> {
                if (phTick % 20 == 1) {
                    int left = (phLen - phTick + 19) / 20;
                    for (T t : ts.values()) {
                        Player p = t.j.player();
                        if (p != null) g.title(p, "&e" + left, "&7Prends la corde...", 900);
                    }
                }
                if (phTick >= phLen) {
                    ph = Ph.PULL;
                    phTick = 0;
                    pullTotal = phLen = Math.max(15, g.cfgI("corde.temps", 60)) * 20;
                    for (T t : ts.values()) {
                        Player p = t.j.player();
                        if (p != null) {
                            g.title(p, "&a&lTIREZ !", "&7Clique le plus vite possible !", 1000);
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        }
                    }
                }
            }
            case PULL -> {
                for (T t : ts.values()) {
                    Player p = t.j.player();
                    if (p != null && t.j.alive) p.sendActionBar(EemiSoleil.c(barFor(t)));
                }
                if (phTick % 3 == 0) moveRope();
                if (Math.abs(pos) >= winDist()) {
                    resolve(pos > 0 ? -1 : 1);
                    return;
                }
                if (phTick >= phLen) timeUp();
            }
            case FALL -> {
                if (phTick >= phLen) {
                    ph = Ph.END;
                    finish();
                }
            }
            default -> {}
        }
    }

    @Override
    boolean onClick(Player p, Joueur j, PlayerInteractEvent e) {
        if (ph != Ph.PULL) return false;
        if (e.getAction() == Action.PHYSICAL) return false;
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return true;
        T t = ts.get(j.id);
        if (t == null) return false;
        long now = g.world.getGameTime();
        // 2.0.2 (demande d'Esteban) : celui qui clique le plus vite gagne. Chaque clic (droit ou gauche) = +1,
        // au plus un clic compte par tick (corde.clics-max-par-seconde, 20 par defaut = pas de plafond reel).
        int maxCps = Math.max(1, Math.min(20, g.cfgI("corde.clics-max-par-seconde", 20)));
        if (now - t.lastClick < Math.max(1, 20 / maxCps)) return true;
        t.lastClick = now;
        t.recent.addLast(now);
        while (!t.recent.isEmpty() && now - t.recent.peekFirst() > 40) t.recent.removeFirst();
        if (t.points % 4 == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
        t.points++;
        // equipe -1 (ouest) tire vers pos > 0 ; force par clic divisee par la taille de l'equipe (equipes egales)
        pos += -t.team * g.cfgD("corde.pas", 0.05);
        return true;
    }

    void resolve(int winner) {
        if (ph != Ph.PULL) return;
        ph = Ph.FALL;
        phTick = 0;
        phLen = 80;
        moveRope();
        String wn = winner < 0 ? "&crouge" : "&9bleue";
        g.tell("&a&lL'équipe " + wn + " &a&lremporte le tir à la corde !");
        List<T> losers = new ArrayList<>();
        for (T t : ts.values()) {
            Player p = t.j.player();
            if (t.team == winner) {
                if (p != null) {
                    g.title(p, "&a&lVICTOIRE", "&7Ton équipe a tenu bon", 2000);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            } else if (t.j.alive) {
                losers.add(t);
            }
        }
        // les moins actifs tombent en premier si le quota coupe l'equipe
        losers.sort(Comparator.comparingInt(t -> t.points));
        List<T> falling = new ArrayList<>();
        for (T t : losers) {
            if (g.canEliminate()) {
                falling.add(t);
                g.eliminate(t.j, "Ton équipe a perdu le tir à la corde", 60);
            } else {
                g.eliminate(t.j, "Ton équipe a perdu le tir à la corde", 0); // message « gracie »
                Player p = t.j.player();
                g.anchors.remove(t.j.id);
                if (p != null) p.teleport(exemptSpot());
            }
        }
        // effondrement de la plateforme perdante
        int lose = -winner;
        int a = CX + lose * C_EDGE, b = CX + lose * (C_EDGE + C_LEN - 1);
        for (int x = Math.min(a, b); x <= Math.max(a, b); x++)
            for (int z = -2; z <= 2; z++) {
                Block blk = g.world.getBlockAt(x, CP, z);
                collapsed.put(blk, blk.getBlockData());
                blk.setType(Material.AIR, false);
            }
        g.world.playSound(new Location(g.world, CX + lose * 16, CP, 0), Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        for (T t : falling) {
            g.anchors.remove(t.j.id);
            Player p = t.j.player();
            if (p != null) p.setVelocity(new Vector(-lose * 0.6, 0.1, 0));
        }
    }

    @Override
    void timeUp() {
        if (done) return;
        if (ph == Ph.READY) {
            phTick = phLen;
            return;
        }
        if (ph != Ph.PULL) {
            if (!done) finish();
            return;
        }
        int winner;
        if (Math.abs(pos) > 0.01) winner = pos > 0 ? -1 : 1;
        else {
            int sa = 0, sb = 0;
            for (T t : ts.values()) if (t.team < 0) sa += t.points; else sb += t.points;
            winner = sa == sb ? (ThreadLocalRandom.current().nextBoolean() ? -1 : 1) : sa > sb ? -1 : 1;
        }
        g.tell("&c&lTemps écoulé !");
        resolve(winner);
    }

    @Override
    void onMove(Player p, Joueur j, org.bukkit.event.player.PlayerMoveEvent e) {
        if (e.getTo().getY() < C_FALL) {
            // gracie tombe par erreur, ou exempte : retour a la loge
            p.teleport(exemptSpot());
        }
    }

    @Override
    List<Joueur> ranking() {
        List<Joueur> l = new ArrayList<>(alive());
        l.sort(Comparator.comparingInt(j -> {
            T t = ts.get(j.id);
            return t == null ? Integer.MAX_VALUE : t.points;
        }));
        return l;
    }

    @Override
    String departage() {
        return "le moins de points à la corde";
    }

    @Override
    void onLeave(Joueur j) {
        // une equipe vide perd tout de suite
        if (ph != Ph.PULL) return;
        boolean west = false, east = false;
        for (T t : ts.values()) {
            if (!t.j.alive) continue;
            if (t.team < 0) west = true;
            else east = true;
        }
        if (!west) resolve(1);
        else if (!east) resolve(-1);
    }

    @Override
    void cleanup() {
        for (Map.Entry<Block, BlockData> e : collapsed.entrySet()) e.getKey().setBlockData(e.getValue(), false);
        collapsed.clear();
    }

    @Override
    String barText() {
        return switch (ph) {
            case READY -> "Tir à la corde &8| &ePrenez la corde";
            case PULL -> "Tir à la corde &8| &f⏱ " + time(phLen - phTick);
            default -> "Tir à la corde &8| &7Terminé";
        };
    }

    @Override
    float barProgress() {
        return ph == Ph.PULL ? (phLen - phTick) / (float) Math.max(1, phLen) : 1f;
    }
}
