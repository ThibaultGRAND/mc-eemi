package fr.eemicraft.soleil;

import fr.eemicraft.soleil.EemiSoleil.Joueur;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Epreuve 1 : 1, 2, 3 Soleil (feu rouge, feu vert). La poupee chante dos tourne (on avance), crie « SOLEIL ! », se
 * retourne et regarde : bouger de plus de move-threshold (0,15 bloc) ou sauter elimine. Franchir la ligne d'arrivee
 * avant time-limit (150 s) = qualifie ; au temps, ceux qui sont encore en course sont elimines (les plus loin de
 * l'arrivee d'abord, dans la limite du quota). Pousser les autres est permis pendant le chant. Tout le monde peut passer.
 */
final class EpSoleil extends Epreuve {
    enum Phase { INTRO, TURN_AWAY, CHANT, TURN_FACE, GRACE, WATCH }

    enum RS { RACE, QUAL, OUT }

    static final class R {
        RS st = RS.RACE;
        boolean hasRef;
        int refAt;
        int place;
        double rx, ry, rz;
    }

    static final float FACE = 180f, BACK = 360f;

    final Map<UUID, R> rs = new HashMap<>();
    final Map<UUID, Long> pushCooldown = new HashMap<>();
    Phase phase = Phase.INTRO;
    int phaseTick, phaseLen, timeLeft, timeTotal, qualified;
    int[] chantMarks = new int[0];
    float yawFrom = FACE, yawTo = FACE;

    EpSoleil(EemiSoleil g) {
        super(g, "soleil");
    }

    @Override String nom() { return "1, 2, 3 Soleil"; }

    @Override
    String[] regles() {
        return new String[]{"Quand la poupée chante, avance vers la ligne d'arrivée.",
                "Quand elle crie SOLEIL et se retourne, ne bouge plus : le moindre pas élimine.",
                "Franchis la ligne avant la fin du temps. Pousser les autres est permis pendant le chant."};
    }

    @Override Location specSpot() { return new Location(g.world, 0.5, EemiSoleil.FY + 18, 22.5, 0f, 35f); }

    @Override int[] box() { return EemiSoleil.zoneBox("terrain"); }

    boolean turning() {
        return phase == Phase.TURN_AWAY || phase == Phase.TURN_FACE;
    }

    Location startSpot(int i) {
        int col = i % 13, row = (i / 13) % 3;
        return new Location(g.world, -12 + col * 2 + 0.5, EemiSoleil.FY + 1, -2.5 - row * 2, 0f, 0f);
    }

    Location standSpot(int place) {
        int side = place % 2 == 1 ? -1 : 1;
        int slot = ((place - 1) / 2) % 8;
        return new Location(g.world, side * (6 + slot) + 0.5, EemiSoleil.FY + 4, MapBuilder.FINISH_Z + 6.5, 180f, 10f);
    }

    @Override
    void begin(List<Joueur> alive) {
        timeTotal = Math.max(20, g.cfgI("time-limit", 150)) * 20;
        timeLeft = timeTotal;
        placeBarrier(true);
        g.applyYaw(FACE);
        int i = 0;
        for (Joueur j : shuffled(alive)) {
            Player p = j.player();
            rs.put(j.id, new R());
            if (p == null) continue;
            p.teleport(startSpot(i++));
            p.setGameMode(GameMode.ADVENTURE);
            p.setFallDistance(0);
        }
        g.guard(new Location(g.world, -12.5, EemiSoleil.FY + 1, MapBuilder.FINISH_Z + 3.5, 180f, 0f), 0);
        g.guard(new Location(g.world, 13.5, EemiSoleil.FY + 1, MapBuilder.FINISH_Z + 3.5, 180f, 0f), 1);
        g.guard(new Location(g.world, -13.5, EemiSoleil.FY + 1, -6.5, 0f, 0f), 2);
        g.guard(new Location(g.world, 14.5, EemiSoleil.FY + 1, -6.5, 0f, 0f), 0);
        phase = Phase.INTRO;
        phaseTick = 0;
        phaseLen = Math.max(1, g.cfgI("intro", 5)) * 20;
    }

    void placeBarrier(boolean on) {
        for (int x = -MapBuilder.HW + 1; x <= MapBuilder.HW - 1; x++)
            for (int y = EemiSoleil.FY + 1; y <= EemiSoleil.FY + 3; y++)
                g.world.getBlockAt(x, y, MapBuilder.START_Z).setType(on ? Material.BARRIER : Material.AIR, false);
    }

    List<Player> racersOnline() {
        List<Player> l = new ArrayList<>();
        for (Joueur j : alive()) {
            R r = rs.get(j.id);
            Player p = j.player();
            if (r != null && r.st == RS.RACE && p != null) l.add(p);
        }
        return l;
    }

    int count(RS s) {
        int n = 0;
        for (Joueur j : alive()) {
            R r = rs.get(j.id);
            if (r != null && r.st == s) n++;
        }
        return n;
    }

    @Override
    void tick() {
        phaseTick++;
        if (phase != Phase.INTRO) {
            timeLeft--;
            if (timeLeft <= 0) {
                timeUp();
                return;
            }
        }
        switch (phase) {
            case INTRO -> {
                int left = (phaseLen - phaseTick + 19) / 20;
                if (phaseTick % 20 == 1 && left > 0) {
                    for (Player p : racersOnline()) {
                        g.title(p, "&e" + left, "&7Avance quand elle chante", 900);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                    }
                }
                if (phaseTick >= phaseLen) {
                    placeBarrier(false);
                    for (Player p : racersOnline()) {
                        g.title(p, "&a&lPARTEZ !", "&7Elle se retourne...", 1000);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                    }
                    beginTurn(Phase.TURN_AWAY);
                }
            }
            case TURN_AWAY, TURN_FACE -> {
                float t = Math.min(1f, phaseTick / (float) phaseLen);
                float e = t * t * (3 - 2 * t);
                g.applyYaw(yawFrom + (yawTo - yawFrom) * e);
                if (phaseTick >= phaseLen) {
                    g.applyYaw(yawTo % 360f);
                    if (phase == Phase.TURN_AWAY) beginChant();
                    else {
                        phase = Phase.GRACE;
                        phaseTick = 0;
                        phaseLen = (int) Math.round(g.cfgD("grace", 0.3) * 20);
                        if (phaseLen <= 0) beginWatch();
                    }
                }
            }
            case CHANT -> {
                chantTick();
                if (phaseTick >= phaseLen) soleil();
            }
            case GRACE -> {
                if (phaseTick >= phaseLen) beginWatch();
            }
            case WATCH -> {
                watchTick();
                if (done) return;
                if (phaseTick >= phaseLen) beginTurn(Phase.TURN_AWAY);
            }
        }
        if (done) return;
        finishCheck();
    }

    void beginTurn(Phase ph) {
        phase = ph;
        phaseTick = 0;
        phaseLen = g.ticks("turn-time", 0.5);
        if (ph == Phase.TURN_AWAY) {
            yawFrom = FACE;
            yawTo = BACK;
        } else {
            yawFrom = BACK;
            yawTo = FACE;
        }
        if (g.doll != null && g.doll.isValid()) g.world.playSound(g.doll.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 3f, 0.5f);
    }

    void beginChant() {
        phase = Phase.CHANT;
        phaseTick = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double sec = r.nextDouble() < g.cfgD("fast-chance", 0.15) ? range(g.cfgD("fast-min", 0.7), g.cfgD("fast-max", 1.3))
                : range(g.cfgD("chant-min", 1.5), g.cfgD("chant-max", 5.0));
        phaseLen = Math.max(8, (int) Math.round(sec * 20));
        chantMarks = new int[]{0, (int) (phaseLen * 0.28), (int) (phaseLen * 0.55), (int) (phaseLen * 0.82)};
    }

    static double range(double a, double b) {
        double lo = Math.min(a, b), hi = Math.max(a, b);
        return lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
    }

    void chantTick() {
        String[] words = {"Un...", "Un, deux...", "Un, deux, trois...", "Un, deux, trois, so..."};
        float[] notes = {pitch(7), pitch(9), pitch(9), pitch(12)};
        int step = 0;
        for (int i = 0; i < chantMarks.length; i++) {
            if (phaseTick - 1 >= chantMarks[i]) step = i;
            if (phaseTick - 1 == chantMarks[i]) {
                for (Player p : g.online()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, notes[i]);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.5f, notes[i]);
                }
            }
        }
        if (phaseTick % 2 == 1) for (Player p : g.online()) p.sendActionBar(EemiSoleil.c("&a&l" + words[step] + " &7(avance !)"));
    }

    static float pitch(int semitone) {
        return (float) Math.pow(2, (semitone - 12) / 12.0);
    }

    void soleil() {
        for (Player p : g.online()) {
            g.title(p, "&c&lSOLEIL !", "&7Ne bouge plus !", 1200);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch(7));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch(19));
        }
        beginTurn(Phase.TURN_FACE);
    }

    void beginWatch() {
        phase = Phase.WATCH;
        phaseTick = 0;
        phaseLen = Math.max(10, (int) Math.round(range(g.cfgD("look-min", 2.0), g.cfgD("look-max", 4.0)) * 20));
        for (Joueur j : alive()) {
            R r = rs.get(j.id);
            if (r == null || r.st != RS.RACE) continue;
            Player p = j.player();
            r.hasRef = false;
            // forte latence : position de reference prise plus tard (6 ticks max)
            r.refAt = p == null ? 0 : Math.min(6, Math.max(0, p.getPing() / 50));
        }
        for (Player p : g.online()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
    }

    void watchTick() {
        double thr = g.cfgD("move-threshold", 0.15);
        double jump = g.cfgD("jump-threshold", 0.3);
        for (Joueur j : alive()) {
            if (done) return;
            R r = rs.get(j.id);
            if (r == null || r.st != RS.RACE) continue;
            Player p = j.player();
            if (p == null) continue;
            Location l = p.getLocation();
            if (!r.hasRef) {
                if (phaseTick >= r.refAt) {
                    r.hasRef = true;
                    r.rx = l.getX();
                    r.ry = l.getY();
                    r.rz = l.getZ();
                }
                continue;
            }
            double dx = l.getX() - r.rx, dz = l.getZ() - r.rz;
            String why = null;
            if (dx * dx + dz * dz > thr * thr) why = "Tu as bougé";
            else if (l.getY() - r.ry > jump) why = "Tu as sauté";
            if (why != null) shoot(j, r, p, why);
        }
        if (!done && g.getConfig().getBoolean("doll.red-eyes", true) && phaseTick % 2 == 0) {
            Particle.DustOptions red = new Particle.DustOptions(Color.RED, 1.8f);
            g.world.spawnParticle(Particle.DUST, g.eye(-1), 2, 0.03, 0.03, 0.03, 0, red, true);
            g.world.spawnParticle(Particle.DUST, g.eye(1), 2, 0.03, 0.03, 0.03, 0, red, true);
        }
        if (phaseTick % 2 == 1) for (Player p : g.online()) p.sendActionBar(EemiSoleil.c("&c&lNE BOUGE PLUS !"));
    }

    void shoot(Joueur j, R r, Player p, String why) {
        Location at = p.getLocation().add(0, 1.4, 0);
        if (g.eliminate(j, why, 0)) {
            r.st = RS.OUT;
            if (g.getConfig().getBoolean("doll.laser", true) && g.doll != null && g.doll.isValid()) {
                g.laser(g.eye(-1), at);
                g.laser(g.eye(1), at);
            }
            Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(170, 0, 0), 1.6f);
            g.world.spawnParticle(Particle.DUST, at, 30, 0.3, 0.6, 0.3, 0, red, true);
            checkEnd();
        } else {
            // gracie : nouvelle position de reference
            r.hasRef = false;
            r.refAt = phaseTick + 2;
        }
    }

    void finishCheck() {
        for (Joueur j : alive()) {
            R r = rs.get(j.id);
            Player p = j.player();
            if (r != null && r.st == RS.RACE && p != null && p.getLocation().getZ() >= MapBuilder.FINISH_Z) qualify(j, r, p);
        }
    }

    void qualify(Joueur j, R r, Player p) {
        r.st = RS.QUAL;
        r.place = ++qualified;
        if (r.place == 1) g.tell("&6&l★ " + j.name + " &6arrive en premier !");
        else g.tell("&a✔ n°" + j.numero() + " " + j.name + " &7a franchi la ligne &8(#" + r.place + ")");
        g.world.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        g.title(p, "&a&lQUALIFIÉ !", "&7Place &f#" + r.place, 2000);
        p.teleport(standSpot(r.place));
        checkEnd();
    }

    void checkEnd() {
        if (!done && count(RS.RACE) == 0) {
            g.applyYaw(FACE);
            finish();
        }
    }

    @Override
    void timeUp() {
        if (done) return;
        g.applyYaw(FACE);
        g.tell("&c&lTemps écoulé ! &7La poupée élimine ceux qui n'ont pas franchi la ligne.");
        List<Joueur> late = new ArrayList<>();
        for (Joueur j : alive()) {
            R r = rs.get(j.id);
            if (r != null && r.st == RS.RACE) late.add(j);
        }
        // les plus loin de l'arrivee d'abord
        late.sort(Comparator.comparingDouble(j -> {
            Player p = j.player();
            return p == null ? -1e9 : p.getLocation().getZ();
        }));
        for (Joueur j : late) {
            R r = rs.get(j.id);
            Player p = j.player();
            if (g.eliminate(j, "Temps écoulé", 0)) r.st = RS.OUT;
            else if (p != null) qualify(j, r, p);
            else r.st = RS.OUT;
        }
        if (!done) finish();
    }

    @Override
    List<Joueur> ranking() {
        List<Joueur> l = new ArrayList<>(alive());
        // derniers arrives d'abord
        l.sort(Comparator.comparingInt((Joueur j) -> rs.containsKey(j.id) ? rs.get(j.id).place : 0).reversed());
        return l;
    }

    @Override
    String departage() {
        return "parmi les derniers à franchir la ligne";
    }

    @Override
    void onMove(Player p, Joueur j, PlayerMoveEvent e) {
        R r = rs.get(j.id);
        if (r == null) return;
        Location to = e.getTo();
        if (to.getY() < EemiSoleil.FY - 5) {
            p.teleport(r.st == RS.QUAL ? standSpot(Math.max(1, r.place)) : startSpot(0));
            return;
        }
        if (r.st == RS.QUAL && to.getZ() < MapBuilder.FINISH_Z + 1) p.teleport(standSpot(Math.max(1, r.place)));
    }

    @Override
    boolean onHit(Player a, Joueur ja, Player v, Joueur jv) {
        if (!g.getConfig().getBoolean("pushing", true)) return false;
        if (phase == Phase.TURN_FACE || phase == Phase.GRACE || phase == Phase.WATCH) return false;
        R ra = rs.get(ja.id), rv = rs.get(jv.id);
        if (ra == null || rv == null || ra.st != RS.RACE || rv.st != RS.RACE) return false;
        long now = System.currentTimeMillis();
        Long last = pushCooldown.get(a.getUniqueId());
        if (last != null && now - last < g.getConfig().getLong("push-cooldown", 600)) return true;
        pushCooldown.put(a.getUniqueId(), now);
        Vector dir = v.getLocation().toVector().subtract(a.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-4) dir = a.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) return true;
        dir.normalize().multiply(g.cfgD("push-strength", 0.55)).setY(g.cfgD("push-up", 0.25));
        v.setVelocity(dir);
        g.world.playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        return true;
    }

    @Override
    void onLeave(Joueur j) {
        R r = rs.get(j.id);
        if (r != null) r.st = RS.OUT;
        checkEnd();
    }

    @Override
    void cleanup() {
        placeBarrier(false);
        g.applyYaw(FACE);
    }

    @Override
    String barText() {
        String ph = switch (phase) {
            case INTRO -> "&ePrêts ?";
            case TURN_AWAY, CHANT -> "&a&lAvance !";
            default -> "&c&lNe bouge plus !";
        };
        return "1, 2, 3 Soleil &8| &f⏱ " + time(timeLeft) + " &8| " + ph + " &8| &a" + count(RS.QUAL) + " qualifiés";
    }

    @Override
    float barProgress() {
        return timeLeft / (float) Math.max(1, timeTotal);
    }
}
