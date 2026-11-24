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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Epreuve 5 : pont de verre. 18 rangees de deux dalles (2 x 2) : a chaque rangee une seule est en verre trempe, l'autre
 * casse sous le pied. Choix des dossards (pont.choix, 15 s, menu ; sinon au hasard) = ordre de passage. Un joueur a la
 * fois s'elance (les autres attendent sur le quai) : pont.temps-tour (45 s) par joueur, sinon il est elimine ; une chute
 * elimine. Les dalles cassees le restent. Quota atteint : toutes les dalles restantes deviennent trempees. Temps total
 * pont.temps (420 s) : ceux qui n'ont pas traverse sont elimines. Derniere epreuve avant la finale : il ne doit rester
 * que 2 joueurs ; s'il en reste plus, departage par le temps de traversee (les plus lents sont elimines).
 */
final class EpPont extends Epreuve {
    enum Ph { CHOICE, RUN, END }

    Ph ph = Ph.CHOICE;
    int phTick, phLen, turnTick, globalTick;
    final boolean[] safeLeft = new boolean[P_ROWS];
    final boolean[] broken = new boolean[2 * P_ROWS];
    boolean tempered;
    final Map<UUID, Integer> dossard = new HashMap<>();
    final List<Joueur> order = new ArrayList<>();
    int turn = -1;
    Joueur runner;
    final Map<UUID, Integer> crossTicks = new HashMap<>();
    final Map<UUID, Integer> bestRow = new HashMap<>();

    EpPont(EemiSoleil g) {
        super(g, "pont");
    }

    @Override String nom() { return "Pont de verre"; }

    @Override
    String[] regles() {
        return new String[]{"Choisis ton dossard : il fixe l'ordre de passage.",
                "A chaque rangée, une dalle est en verre trempé, l'autre casse sous ton poids.",
                "Saute de dalle en dalle jusqu'au quai d'arrivée, un joueur à la fois."};
    }

    @Override Location specSpot() { return new Location(g.world, PX + 0.5, PB + 8, -12.5, 0f, 30f); }

    @Override int[] box() { return SquidZones.PONT_BOX; }

    Location waitSpot(int i) {
        return new Location(g.world, P_START1 + 2.5 + (i % 6) * 2, PB + 1, -3.5 + (i / 6) * 2.5, -90f, 0f);
    }

    Location startSpot() {
        return new Location(g.world, P_START2 - 0.5, PB + 1, 0.5, -90f, 0f);
    }

    @Override
    void begin(List<Joueur> alive) {
        for (int r = 0; r < P_ROWS; r++) safeLeft[r] = ThreadLocalRandom.current().nextBoolean();
        resetTiles();
        int i = 0;
        for (Joueur j : alive) {
            Player p = j.player();
            if (p == null) continue;
            p.teleport(waitSpot(i++));
            g.clearHands(p);
            p.getInventory().setItem(0, EemiSoleil.named(Material.NAME_TAG, "&eDossards", "&7Clic droit : choisir ton dossard"));
            p.getInventory().setHeldItemSlot(0);
            openChoice(p);
        }
        g.guard(new Location(g.world, P_START1 + 1.5, PB + 1, -5.5, -90f, 0f), 0);
        g.guard(new Location(g.world, P_START1 + 1.5, PB + 1, 6.5, -90f, 0f), 2);
        g.guard(new Location(g.world, P_END2 - 0.5, PB + 1, -5.5, 90f, 0f), 1);
        g.guard(new Location(g.world, P_END2 - 0.5, PB + 1, 6.5, 90f, 0f), 0);
        ph = Ph.CHOICE;
        phTick = 0;
        phLen = Math.max(5, g.cfgI("pont.choix", 15)) * 20;
    }

    void resetTiles() {
        for (int r = 0; r < P_ROWS; r++)
            for (int s = 0; s < 2; s++) setTile(r, s, Material.GLASS);
        java.util.Arrays.fill(broken, false);
    }

    void setTile(int r, int side, Material m) {
        int x = pRowX(r);
        int[] zs = side == 0 ? new int[]{-3, -2} : new int[]{2, 3};
        for (int dx = 0; dx <= 1; dx++)
            for (int z : zs) g.world.getBlockAt(x + dx, PB, z).setType(m, false);
    }

    void openChoice(Player p) {
        SquidMenu m = new SquidMenu("dossard", null);
        int n = alive().size();
        Inventory inv = Bukkit.createInventory(m, 27, EemiSoleil.c("&8Choisis ton dossard (ordre de passage)"));
        m.inv = inv;
        fillChoice(inv, n);
        p.openInventory(inv);
    }

    void fillChoice(Inventory inv, int n) {
        Map<Integer, String> taken = new HashMap<>();
        for (Joueur j : alive()) {
            Integer d = dossard.get(j.id);
            if (d != null) taken.put(d, j.name);
        }
        for (int d = 1; d <= Math.min(n, 27); d++) {
            String who = taken.get(d);
            ItemStack it = EemiSoleil.named(who == null ? Material.LIME_DYE : Material.GRAY_DYE,
                    (who == null ? "&a" : "&7") + "Dossard " + d, who == null ? "&7Libre" : "&7Pris par " + who);
            it.setAmount(d);
            inv.setItem(d - 1, it);
        }
    }

    @Override
    void onMenu(Player p, Joueur j, int slot, SquidMenu menu) {
        if (!menu.kind.equals("dossard") || ph != Ph.CHOICE) return;
        int d = slot + 1;
        int n = alive().size();
        if (d < 1 || d > n) return;
        for (Map.Entry<UUID, Integer> en : dossard.entrySet()) if (en.getValue() == d && !en.getKey().equals(j.id)) return;
        dossard.put(j.id, d);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        EemiSoleil.msg(p, "&7Tu as le dossard &e" + d + "&7.");
        p.closeInventory();
        for (Joueur o : alive()) {
            Player op = o.player();
            if (op != null && op.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu sm && sm.kind.equals("dossard")) {
                fillChoice(op.getOpenInventory().getTopInventory(), n);
            }
        }
    }

    void endChoice() {
        List<Integer> free = new ArrayList<>();
        int n = alive().size();
        for (int d = 1; d <= n; d++) if (!dossard.containsValue(d)) free.add(d);
        java.util.Collections.shuffle(free);
        for (Joueur j : alive()) {
            if (!dossard.containsKey(j.id) && !free.isEmpty()) {
                int d = free.remove(0);
                dossard.put(j.id, d);
                Player p = j.player();
                if (p != null) EemiSoleil.msg(p, "&7Le sort te donne le dossard &e" + d + "&7.");
            }
            Player p = j.player();
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
        }
        order.clear();
        order.addAll(alive());
        order.sort(Comparator.comparingInt(j -> dossard.getOrDefault(j.id, 99)));
        StringBuilder sb = new StringBuilder();
        for (Joueur j : order) sb.append("&e").append(dossard.get(j.id)).append(" &f").append(j.name).append("&7, ");
        g.tell("&7Ordre de passage : " + sb);
        ph = Ph.RUN;
        globalTick = 0;
        nextTurn();
    }

    /** 2.0.2 : partie test ou personne ne peut etre elimine (2 joueurs) : on traverse quand meme, les dalles cassent,
     *  une chute renvoie au depart (gracie) au lieu de qualifier tout le monde d'office. */
    boolean practice() {
        return g.testMode && maxElim <= 0;
    }

    void nextTurn() {
        runner = null;
        if (!practice() && elimCount >= maxElim && !done) {
            // quota atteint : les suivants sont tous qualifies, inutile de les faire traverser
            for (Joueur j : order) {
                if (j.alive && !crossTicks.containsKey(j.id)) crossTicks.put(j.id, Integer.MAX_VALUE / 4);
            }
            g.tell("&e✦ Le quota est atteint : les joueurs restants sont qualifiés.");
            ph = Ph.END;
            finish();
            return;
        }
        while (++turn < order.size()) {
            Joueur j = order.get(turn);
            if (!j.alive) continue;
            Player p = j.player();
            if (p == null) continue;
            runner = j;
            turnTick = 0;
            p.teleport(startSpot());
            g.tell("&eDossard " + dossard.get(j.id) + " &8» &f" + j.name + " &7s'élance !");
            g.title(p, "&e&lÀ TOI", "&7Gauche ou droite ?", 1800);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            return;
        }
        if (!done) {
            ph = Ph.END;
            finish();
        }
    }

    int turnTime() {
        return Math.max(10, g.cfgI("pont.temps-tour", 45)) * 20;
    }

    int totalTime() {
        return Math.max(60, g.cfgI("pont.temps", 420)) * 20;
    }

    @Override
    void tick() {
        phTick++;
        if (ph == Ph.CHOICE) {
            if (phTick % 10 == 0) {
                int left = (phLen - phTick + 19) / 20;
                for (Joueur j : alive()) {
                    Player p = j.player();
                    Integer d = dossard.get(j.id);
                    if (p != null) p.sendActionBar(EemiSoleil.c(d == null ? "&eChoisis un dossard &7(" + left + " s) &8- clic droit pour rouvrir"
                            : "&aDossard " + d + " &7(" + left + " s)"));
                }
            }
            if (phTick >= phLen) endChoice();
            return;
        }
        if (ph != Ph.RUN) return;
        globalTick++;
        turnTick++;
        if (runner != null) {
            Player p = runner.player();
            if (p != null && tick % 5 == 0) {
                int left = (turnTime() - turnTick + 19) / 20;
                p.sendActionBar(EemiSoleil.c("&eRangée " + Math.max(0, bestRow.getOrDefault(runner.id, -1) + 1) + "/" + P_ROWS + " &8| &f⏱ " + left + " s"));
            }
            if (turnTick >= turnTime()) {
                Joueur r = runner;
                if (g.eliminate(r, "Trop lent sur le pont", 0)) {
                    nextTurn();
                } else {
                    arrive(r);
                }
            }
        }
        if (globalTick >= totalTime()) timeUp();
    }

    @Override
    void onMove(Player p, Joueur j, PlayerMoveEvent e) {
        if (ph == Ph.CHOICE) {
            if (e.getTo().getX() > P_START2 - 1) e.setTo(waitSpot(0));
            return;
        }
        if (ph != Ph.RUN) return;
        Location to = e.getTo();
        if (j != runner) {
            // en attente sur le quai (ou deja arrive sur le quai d'en face)
            boolean arrived = crossTicks.containsKey(j.id);
            if (!arrived && to.getX() > P_START2 - 1) {
                Location b = waitSpot(order.indexOf(j));
                b.setYaw(to.getYaw());
                e.setTo(b);
            } else if (arrived && to.getX() < P_END1 + 1) {
                Location b = new Location(g.world, P_END1 + 3.5, PB + 1, 0.5, to.getYaw(), to.getPitch());
                e.setTo(b);
            }
            if (to.getY() < P_FALL) p.teleport(arrived ? new Location(g.world, P_END1 + 3.5, PB + 1, 0.5) : waitSpot(0));
            return;
        }
        if (to.getY() < P_FALL) {
            if (g.eliminate(j, "Chute du pont de verre", 25)) {
                nextTurn();
            } else {
                p.teleport(startSpot());
            }
            return;
        }
        if (to.getX() >= P_END1 + 0.5 && to.getY() >= PB + 0.9) {
            arrive(j);
            return;
        }
        // atterrissage sur une dalle
        if (Math.abs(to.getY() - (PB + 1)) > 0.3) return;
        int bx = to.getBlockX(), bz = to.getBlockZ();
        int rel = bx - P_XB;
        if (rel < 0 || rel >= 3 * P_ROWS || rel % 3 == 2) return;
        int r = rel / 3;
        int side = (bz == -3 || bz == -2) ? 0 : (bz == 2 || bz == 3) ? 1 : -1;
        if (side < 0) return;
        bestRow.merge(j.id, r, Math::max);
        boolean safe = (side == 0) == safeLeft[r];
        if (safe || tempered || broken[r * 2 + side]) return;
        if (!practice() && !g.canEliminate()) {
            // quota atteint ou 2 joueurs restants : toutes les dalles deviennent trempees
            tempered = true;
            g.tell("&e✦ Les dalles restantes sont désormais toutes en verre trempé.");
            return;
        }
        broken[r * 2 + side] = true;
        setTile(r, side, Material.AIR);
        Location c = new Location(g.world, pRowX(r) + 1, PB + 0.5, side == 0 ? -2 : 3);
        g.world.playSound(c, Sound.BLOCK_GLASS_BREAK, 2f, 0.8f);
        g.world.spawnParticle(Particle.BLOCK, c, 40, 0.6, 0.2, 0.6, 0, Material.GLASS.createBlockData());
    }

    void arrive(Joueur j) {
        if (crossTicks.containsKey(j.id)) return;
        crossTicks.put(j.id, turnTick);
        Player p = j.player();
        if (p != null) {
            p.teleport(new Location(g.world, P_END1 + 3.5 + (crossTicks.size() % 4), PB + 1, -2.5 + (crossTicks.size() / 4), 90f, 0f));
            g.title(p, "&a&lTRAVERSÉ !", "&7en " + (turnTick / 20) + " s", 2000);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        g.tell("&a✔ " + j.name + " &7a traversé le pont &8(" + (turnTick / 20) + " s)");
        if (j == runner) nextTurn();
    }

    @Override
    void timeUp() {
        if (done) return;
        if (ph == Ph.CHOICE) {
            endChoice();
            return;
        }
        if (ph != Ph.RUN) return;
        g.tell("&c&lTemps écoulé ! &7Ceux qui n'ont pas traversé sont éliminés.");
        List<Joueur> late = new ArrayList<>();
        for (int k = order.size() - 1; k >= 0; k--) {
            Joueur j = order.get(k);
            if (j.alive && !crossTicks.containsKey(j.id)) late.add(j);
        }
        runner = null;
        for (Joueur j : late) {
            if (!g.eliminate(j, "Pas traversé à temps", 0)) {
                crossTicks.put(j.id, Integer.MAX_VALUE / 2);
                Player p = j.player();
                if (p != null) p.teleport(new Location(g.world, P_END1 + 3.5, PB + 1, 0.5, 90f, 0f));
            }
        }
        ph = Ph.END;
        finish();
    }

    @Override
    List<Joueur> ranking() {
        List<Joueur> l = new ArrayList<>(alive());
        // traversee la plus lente d'abord ; ceux qui n'ont pas traverse avant tout le monde
        l.sort(Comparator.comparingInt((Joueur j) -> crossTicks.getOrDefault(j.id, Integer.MAX_VALUE)).reversed());
        return l;
    }

    @Override
    String departage() {
        return "traversée la plus lente";
    }

    @Override
    boolean onClick(Player p, Joueur j, org.bukkit.event.player.PlayerInteractEvent e) {
        if (ph == Ph.CHOICE && (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) openChoice(p);
            return true;
        }
        return false;
    }

    @Override
    void onLeave(Joueur j) {
        if (j == runner) nextTurn();
    }

    @Override
    void cleanup() {
        resetTiles();
        for (Joueur j : alive()) {
            Player p = j.player();
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof SquidMenu) p.closeInventory();
        }
    }

    @Override
    String barText() {
        if (ph == Ph.CHOICE) return "Pont de verre &8| &eDossards &8| &f" + time(phLen - phTick);
        if (ph == Ph.RUN) return "Pont de verre &8| &f" + (runner == null ? "-" : runner.name) + " &8| &f⏱ " + time(totalTime() - globalTick);
        return "Pont de verre &8| &7Terminé";
    }

    @Override
    float barProgress() {
        return ph == Ph.RUN ? (totalTime() - globalTick) / (float) Math.max(1, totalTime()) : 1f;
    }
}
