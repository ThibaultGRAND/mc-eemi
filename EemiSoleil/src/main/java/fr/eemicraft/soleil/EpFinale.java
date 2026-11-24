package fr.eemicraft.soleil;

import static fr.eemicraft.soleil.Layout.*;

import fr.eemicraft.soleil.EemiSoleil.Joueur;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Finale : le jeu du calamar, 1 contre 1. Roles tires au sort.
 * <ul>
 * <li>Attaquant : part de la marque au bout de la queue ; il gagne s'il entre dans la tete du calamar (cercle a moins de
 * 3 blocs du centre). Tant qu'il n'a pas franchi la TAILLE (ligne au milieu du corps, en passant par l'interieur du
 * calamar), il avance « a cloche-pied » : Lenteur II. Sorti de l'aire de jeu (bordure a la craie) : il perd.</li>
 * <li>Defenseur : part dans le corps ; il ne doit jamais sortir du dessin du calamar (sinon il perd) et gagne en
 * poussant l'attaquant hors de l'aire.</li>
 * <li>Combat au couteau : aucun degat, chaque coup repousse (finale.recul 0,9 / finale.recul-haut 0,35), 0,5 s entre
 * deux coups.</li>
 * <li>Temps (finale.temps, 150 s) : manche 2 avec les roles inverses ; si elle finit aussi au temps, gagne celui qui,
 * en attaquant, s'est le plus approche du centre de la tete (egalite : tirage au sort).</li>
 * </ul>
 */
final class EpFinale extends Epreuve {
    Joueur att, def;
    int round = 1, roundTick, roundLen;
    boolean live, crossed, bodyAbove;
    final double[] best = {Double.MAX_VALUE, Double.MAX_VALUE}; // meilleure distance a la tete, par joueur (0 = premier attaquant)
    Joueur first;
    long lastHitA, lastHitD;

    EpFinale(EemiSoleil g) {
        super(g, "finale");
    }

    @Override String nom() { return "Finale du calamar"; }

    @Override
    String[] regles() {
        return new String[]{"L'attaquant doit atteindre la tête du calamar ; il avance à cloche-pied tant qu'il n'a pas passé la taille.",
                "Le défenseur ne doit pas sortir du calamar ; l'attaquant ne doit pas sortir de l'aire à la craie.",
                "Au couteau, chaque coup repousse. Au temps : manche retour avec les rôles inversés."};
    }

    @Override Location specSpot() { return new Location(g.world, FX + 0.5, FY + 22, 30.5, 180f, 45f); }

    @Override int[] box() { return SquidZones.FINALE_BOX; }

    Location attSpot() { return new Location(g.world, FX + 0.5, FY + 1, 21.5, 180f, 0f); }

    Location defSpot() { return new Location(g.world, FX + 0.5, FY + 1, -5.5, 0f, 0f); }

    @Override
    void begin(List<Joueur> alive) {
        if (alive.size() < 2) {
            g.finaleResult(alive.isEmpty() ? null : alive.get(0), null);
            return;
        }
        List<Joueur> l = shuffled(alive);
        att = l.get(0);
        def = l.get(1);
        first = att;
        for (int k = 0; k < 6; k++) {
            int side = k % 2 == 0 ? -1 : 1;
            g.guard(new Location(g.world, FX + side * 23 + 0.5, FY + 4, -10.5 + (k / 2) * 10, side < 0 ? -90f : 90f, 0f), k);
        }
        g.zoneText(new Location(g.world, FX + 0.5, FY + 3, F_HEADZ + 0.5), "&c&lTÊTE", 2f);
        g.zoneText(new Location(g.world, FX + 0.5, FY + 3, F_WAIST + 0.5), "&f&lTAILLE", 1.6f);
        startRound();
    }

    void startRound() {
        roundTick = 0;
        roundLen = Math.max(30, g.cfgI("finale.temps", 150)) * 20;
        live = false;
        crossed = false;
        bodyAbove = false;
        for (Joueur j : new Joueur[]{att, def}) {
            Player p = j.player();
            if (p == null) continue;
            g.clearHands(p);
            EemiSoleil.clearEffects(p);
            p.getInventory().setItem(0, g.skin(EemiSoleil.named(Material.IRON_SWORD, "&fCouteau", "&7Chaque coup repousse"), "couteau"));
            p.getInventory().setHeldItemSlot(0);
            g.anchor(p, j == att ? attSpot() : defSpot());
            g.title(p, j == att ? "&c&lATTAQUANT" : "&9&lDÉFENSEUR",
                    j == att ? "&7Atteins la tête du calamar" : "&7Ne sors pas du calamar, repousse-le", 3500);
        }
        g.tell("&c&lFinale" + (round == 2 ? " - manche retour" : "") + " &8» &cAttaquant : &f" + att.name + " &8| &9Défenseur : &f" + def.name);
    }

    @Override
    void tick() {
        roundTick++;
        if (!live) {
            if (roundTick % 20 == 0 && roundTick < 100) {
                int left = (100 - roundTick) / 20;
                for (Joueur j : new Joueur[]{att, def}) {
                    Player p = j.player();
                    if (p != null) {
                        g.title(p, "&e" + left, "&7" + (j == att ? "Attaquant" : "Défenseur"), 900);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                    }
                }
            }
            if (roundTick >= 100) {
                live = true;
                roundTick = 0;
                g.clearAnchors();
                for (Joueur j : new Joueur[]{att, def}) {
                    Player p = j.player();
                    if (p != null) {
                        g.title(p, "&c&lCOMBAT", "", 900);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                    }
                }
            }
            return;
        }
        Player pa = att.player(), pd = def.player();
        if (pa == null || pd == null) return;
        Location la = pa.getLocation(), ld = pd.getLocation();
        // cloche-pied jusqu'a la taille (franchie par l'interieur du calamar)
        if (!crossed) {
            // il faut franchir la ligne DANS le corps (venir du corps cote queue, z >= taille) : le tour par l'exterieur ne compte pas
            boolean inBody = Math.abs(la.getBlockX() - FX) <= F_BODY_HW && la.getBlockZ() >= F_BODY_Z1 && la.getBlockZ() <= F_BODY_Z2;
            boolean crossNow = inBody && la.getZ() < F_WAIST && bodyAbove;
            bodyAbove = inBody && la.getZ() >= F_WAIST;
            if (crossNow) {
                crossed = true;
                pa.removePotionEffect(PotionEffectType.SLOWNESS);
                g.title(pa, "", "&aTaille franchie : tu cours !", 1200);
                pa.playSound(la, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
            } else if (roundTick % 20 == 1) {
                pa.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true));
            }
        }
        double dist = Math.hypot(la.getX() - (FX + 0.5), la.getZ() - (F_HEADZ + 0.5));
        int idx = att == first ? 0 : 1;
        best[idx] = Math.min(best[idx], dist);
        if (inHead(la.getX(), la.getZ(), 3.0) && crossed) {
            win(att, def, "a atteint la tête du calamar");
            return;
        }
        if (!inSquid(ld.getBlockX(), ld.getBlockZ())) {
            win(att, def, "a fait sortir le défenseur du calamar");
            return;
        }
        if (!inArena(la.getX(), la.getZ())) {
            win(def, att, "a poussé l'attaquant hors de l'aire");
            return;
        }
        if (roundTick % 10 == 0) {
            String s = "&f⏱ " + time(roundLen - roundTick) + (crossed ? " &8| &aTaille franchie" : " &8| &7À cloche-pied");
            pa.sendActionBar(EemiSoleil.c("&cAttaquant &8| " + s + " &8| &fTête à " + (int) dist + " blocs"));
            pd.sendActionBar(EemiSoleil.c("&9Défenseur &8| " + s));
        }
        if (roundTick >= roundLen) timeUp();
    }

    void win(Joueur w, Joueur l, String how) {
        if (done) return;
        done = true;
        live = false;
        g.tell("&6&l★ " + w.name + " &6" + how + " !");
        g.finaleResult(w, l);
    }

    @Override
    boolean onHit(Player a, Joueur ja, Player v, Joueur jv) {
        if (!live) return true;
        long now = System.currentTimeMillis();
        boolean isAtt = ja == att;
        if (now - (isAtt ? lastHitA : lastHitD) < 500) return true;
        if (isAtt) lastHitA = now;
        else lastHitD = now;
        Vector dir = v.getLocation().toVector().subtract(a.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1e-4) dir = a.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return true;
        dir.normalize().multiply(g.cfgD("finale.recul", 0.9)).setY(g.cfgD("finale.recul-haut", 0.35));
        v.setVelocity(dir);
        g.world.playSound(v.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.9f);
        return true;
    }

    @Override
    void timeUp() {
        if (done) return;
        if (!live) {
            roundTick = 100;
            return;
        }
        if (round == 1) {
            g.tell("&c&lTemps écoulé ! &7Manche retour : les rôles s'inversent.");
            round = 2;
            Joueur t = att;
            att = def;
            def = t;
            startRound();
            return;
        }
        Joueur w;
        if (Math.abs(best[0] - best[1]) < 0.05) w = ThreadLocalRandom.current().nextBoolean() ? att : def;
        else w = best[0] < best[1] ? first : (first == att ? def : att);
        Joueur l = w == att ? def : att;
        g.tell("&7Deux manches au temps : &f" + w.name + " &7s'est le plus approché de la tête &8("
                + String.format("%.1f", Math.min(best[0], best[1])) + " blocs)");
        win(w, l, "gagne au plus près de la tête");
    }

    @Override
    List<Joueur> ranking() {
        return new ArrayList<>(alive());
    }

    @Override
    void onLeave(Joueur j) {
        // le moteur declare le restant gagnant par forfait
    }

    @Override
    void cleanup() {
        for (Joueur j : new Joueur[]{att, def}) {
            if (j == null) continue;
            Player p = j.player();
            if (p != null) EemiSoleil.clearEffects(p);
        }
    }

    @Override
    String barText() {
        if (att == null) return "Finale";
        return "Finale" + (round == 2 ? " (retour)" : "") + " &8| &c" + att.name + " &7contre &9" + def.name + " &8| &f⏱ " + time(roundLen - roundTick);
    }

    @Override
    float barProgress() {
        return live ? (roundLen - roundTick) / (float) Math.max(1, roundLen) : 1f;
    }
}
