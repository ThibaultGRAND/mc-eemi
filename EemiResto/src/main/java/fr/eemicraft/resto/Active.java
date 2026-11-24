package fr.eemicraft.resto;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Production d'un restaurant dont le proprietaire est dans le monde : les droppers posent des plats (ItemDisplay,
 * jamais des objets ramassables) sur les tapis, les plats avancent, passent sous les ameliorateurs et sont vendus
 * au comptoir ; la vente va dans la caisse (pending). Au-dela du plafond d'entites, un plat est vendu directement
 * (meme valeur, sans affichage). Tout est retire a la desactivation.
 */
final class Active {
    static final double DT = 0.1; // tache toutes les 2 ticks

    final EemiResto pl;
    final PData d;
    final int parcel;
    final double[] timers = new double[Defs.DROPPERS.length];
    final List<Plate> plates = new ArrayList<>();
    private int soundTick;

    static final class Plate {
        ItemDisplay e;
        int line;
        double s;
        double value;
        int passed; // bits des ameliorateurs deja passes
        String dish;
        boolean legendary;
    }

    Active(EemiResto pl, PData d) {
        this.pl = pl;
        this.d = d;
        this.parcel = d.parcel;
        for (int i = 0; i < timers.length; i++) timers[i] = Defs.DROPPERS[i].interval() * (0.3 + 0.7 * i / timers.length);
    }

    int count() {
        return plates.size();
    }

    void tick() {
        var econ = pl.econ;
        double f = econ.intervalFactor(d.bought);
        double gm = econ.globalMult(d.bought, d.rebirths);
        for (int i = 0; i < Defs.DROPPERS.length; i++) {
            Defs.Dropper dr = Defs.DROPPERS[i];
            if (!d.bought.contains(dr.purchase()) || !Econ.lineOn(d.bought, dr.line())) continue;
            timers[i] -= DT;
            if (timers[i] > 0) continue;
            timers[i] += dr.interval() * f;
            drop(dr, gm);
        }
        double speed = pl.beltSpeed(d.bought);
        Iterator<Plate> it = plates.iterator();
        while (it.hasNext()) {
            Plate p = it.next();
            Defs.Line l = Defs.LINES[p.line];
            if (p.e == null || !p.e.isValid()) {
                // entite perdue : le plat est vendu sans affichage, au multiplicateur deja acquis
                it.remove();
                sell(p, false);
                continue;
            }
            double ns = p.s + speed * DT;
            for (int k = 0; k < Defs.UPGRADERS.length; k++) {
                Defs.Upgrader u = Defs.UPGRADERS[k];
                if (u.line() != p.line || (p.passed & (1 << k)) != 0 || !d.bought.contains(u.purchase())) continue;
                int idx = Defs.pathIndex(l, u.u(), u.v());
                if (idx >= 0 && ns >= idx) {
                    p.passed |= 1 << k;
                    p.value *= u.mult();
                    pl.particle(p.e.getLocation(), Particle.HAPPY_VILLAGER, 3);
                }
            }
            p.s = ns;
            int last = l.path().length - 1;
            if (p.s >= last) {
                it.remove();
                p.e.remove();
                sell(p, true);
                continue;
            }
            p.e.teleport(loc(l, p.s));
        }
        soundTick++;
    }

    Location loc(Defs.Line l, double s) {
        int a = (int) Math.floor(s);
        int b = Math.min(a + 1, l.path().length - 1);
        double t = s - a;
        double u = l.path()[a][0] + (l.path()[b][0] - l.path()[a][0]) * t + 0.5;
        double v = l.path()[a][1] + (l.path()[b][1] - l.path()[a][1]) * t + 0.5;
        return new Location(pl.world, pl.geo.wxd(parcel, u, v), Geo.FLOOR + 2.18, pl.geo.wzd(parcel, u, v));
    }

    /** Un dropper pose un plat (ou un plat legendaire). */
    void drop(Defs.Dropper dr, double gm) {
        String dish = dr.dish();
        double value = dr.value() * gm;
        boolean legend = false;
        for (Defs.Secret sc : Defs.SECRETS) {
            if (!sc.droppers().contains(dr.purchase()) || !pl.secretEnabled(d, sc.dish())) continue;
            if (pl.rnd.nextDouble() < pl.legendaryChance) {
                dish = sc.dish();
                value *= pl.econ.legendMult;
                legend = true;
                break;
            }
        }
        d.dishes.add(dr.dish());
        if (legend) pl.discover(d, dish);
        Defs.Line l = Defs.LINES[dr.line()];
        int start = Math.max(0, Defs.pathIndex(l, dr.u(), dr.v() - 1));
        spawnPlate(dr.line(), start, dish, value, legend);
    }

    void spawnPlate(int line, int start, String dish, double value, boolean legend) {
        Defs.Line l = Defs.LINES[line];
        Plate p = new Plate();
        p.line = line;
        p.s = start;
        p.value = value;
        p.dish = dish;
        p.legendary = legend;
        // ameliorateurs deja en amont du point de depart : non appliques (aucun n'est en amont d'un dropper)
        if (!legend && (plates.size() >= pl.maxPlates || pl.totalPlates() >= pl.maxPlatesTotal)) {
            // plafond d'entites : vendu tout de suite avec tous les ameliorateurs de la ligne
            p.value = value * Econ.lineMult(d.bought, line);
            sell(p, false);
            return;
        }
        Location at = loc(l, start);
        p.e = pl.spawnDisplay(at, e -> {
            e.setItemStack(U.dish(dish, pl.textures, null));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            float sc = legend ? 0.7f : 0.45f;
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (-Math.PI / 2), 1, 0, 0),
                    new Vector3f(sc, sc, sc), new AxisAngle4f()));
            e.setTeleportDuration(2);
            e.setViewRange(0.6f);
            if (legend) {
                e.setGlowing(true);
                e.setGlowColorOverride(Color.fromRGB(255, 200, 40));
                e.setBrightness(new Display.Brightness(15, 15));
            }
        });
        if (p.e == null) {
            p.value = value * Econ.lineMult(d.bought, line);
            sell(p, false);
            return;
        }
        plates.add(p);
    }

    void sell(Plate p, boolean visible) {
        d.pending += p.value;
        d.earned += p.value;
        d.sold++;
        d.dirty = true;
        if (visible) {
            Defs.Line l = Defs.LINES[p.line];
            Location at = loc(l, l.path().length - 1);
            if (p.legendary) {
                pl.popup(at.clone().add(0, 0.6, 0), "&6&l+" + U.eur(p.value), 60);
                Defs.Secret sc = Defs.secret(p.dish);
                Player o = pl.getServer().getPlayer(d.uuid);
                if (o != null && sc != null) {
                    U.msg(o, "&d&l" + Defs.DISHES.get(p.dish).name() + " &7vendu " + "&6" + U.eur(p.value) + " &7! &f" + sc.line());
                    o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
                }
            } else if (soundTick % 6 == 0) {
                Player o = pl.getServer().getPlayer(d.uuid);
                if (o != null && o.getWorld().equals(pl.world) && o.getLocation().distanceSquared(at) < 400) {
                    o.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25f, 1.6f);
                }
            }
        }
    }

    /** Retire toutes les entites (les plats en cours sont vendus a leur valeur actuelle, rien n'est perdu). */
    void stop() {
        for (Plate p : plates) {
            if (p.e != null && p.e.isValid()) p.e.remove();
            d.pending += p.value;
            d.earned += p.value;
            d.sold++;
        }
        if (!plates.isEmpty()) d.dirty = true;
        plates.clear();
    }

    static boolean lunch(int h1, int h2) {
        int h = LocalTime.now(ZoneId.of("Europe/Paris")).getHour();
        return h >= h1 && h < h2;
    }
}
