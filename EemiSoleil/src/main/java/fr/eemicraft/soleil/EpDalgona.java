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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Epreuve 2 : biscuit dalgona.
 * <ol>
 * <li>Choix (dalgona.choix, 15 s) : chacun se place sur le tapis devant une des 4 boites (rond, triangle, etoile,
 * parapluie). Sans choix a la fin : forme tiree au sort.</li>
 * <li>Decoupe (dalgona.temps, 90 s) : chacun est fige sur son tabouret devant son biscuit vertical de 11 x 11 cases. La
 * forme est entouree d'un trait grave (cases orange) : clic (droit ou gauche) avec l'aiguille sur une case du trait =
 * elle est grattee (retiree). Clic sur une case DE LA FORME = fissure ; au-dela de dalgona.fissures.<forme> fissures
 * (rond 3, triangle 3, etoile 2, parapluie 2) le biscuit casse : elimine. Les autres cases (hors forme) s'emiettent sans
 * risque. Trait entierement gratte = forme detachee, reussi. Au temps, les biscuits inacheves sont elimines (moins
 * avances d'abord, dans la limite du quota).</li>
 * </ol>
 * Avec textures: true, chaque case est une plaque du pack (ItemDisplay) devant une barriere (la barriere recoit le clic).
 */
final class EpDalgona extends Epreuve {
    static final String[] FORMES = {"rond", "triangle", "etoile", "parapluie"};
    static final String[] NOMS = {"Rond", "Triangle", "Étoile", "Parapluie"};
    static final String[] NIVEAUX = {"facile", "moyen", "difficile", "très difficile"};
    static final int[] FISSURES = {3, 3, 2, 2};
    static final int N = D_SIZE;

    enum Ph { CHOICE, PREP, CUT, END }

    /** 's' forme intacte, 'f' forme fissuree, 'l' trait, 'L' trait gratte, 'e' hors forme, 'E' hors forme tombe. */
    static final class Biscuit {
        final Joueur j;
        final int forme, xc, w;
        final char[][] cell = new char[N][N];
        final ItemDisplay[][] disp = new ItemDisplay[N][N];
        int lines, scratched, fissures;
        boolean over, success;
        int doneTick;
        long lastClick;

        Biscuit(Joueur j, int forme, int xc, int w) {
            this.j = j;
            this.forme = forme;
            this.xc = xc;
            this.w = w;
        }

        int x(int i) { return xc - N / 2 + i; }
        int y(int jr) { return D_BY0 + N - 1 - jr; }
    }

    Ph ph = Ph.CHOICE;
    int phTick, phLen, cutTotal;
    final Map<UUID, Integer> choice = new HashMap<>();
    final Map<UUID, Biscuit> biscuits = new HashMap<>();

    EpDalgona(EemiSoleil g) {
        super(g, "dalgona");
    }

    @Override String nom() { return "Biscuit dalgona"; }

    @Override
    String[] regles() {
        return new String[]{"Choisis ta boîte : rond (facile), triangle, étoile, parapluie (très difficile).",
                "Gratte avec l'aiguille (clic droit) toutes les cases du TRAIT orange autour de la forme.",
                "Touche la forme elle-même et elle se fissure : trop de fissures, le biscuit casse."};
    }

    @Override Location specSpot() { return new Location(g.world, DX + 0.5, FY + 20, 46.5, 180f, 40f); }

    @Override int[] box() { return SquidZones.DALGONA_BOX; }

    // ------------------------------------------------------------ formes

    /** Case (i, j) dans la forme (cellule pleine du biscuit). */
    static boolean inShape(int forme, int i, int j) {
        double x = i, y = j;
        switch (forme) {
            case 0 -> {
                return Math.hypot(x - 5, y - 5) <= 3.6;
            }
            case 1 -> {
                if (j < 2 || j > 8) return false;
                double half = (j - 2) * 0.5 + 0.4;
                return Math.abs(x - 5) <= half;
            }
            case 2 -> {
                return pointInStar(x, y);
            }
            default -> {
                if (j <= 5 && j >= 1 && Math.hypot(x - 5, (y - 5) * 1.05) <= 4.2) return true;
                if (i == 5 && j >= 5 && j <= 8) return true;
                return (j == 8 && i == 4) || (j == 8 && i == 3) || (j == 7 && i == 3);
            }
        }
    }

    static boolean pointInStar(double x, double y) {
        double[][] pts = new double[10][];
        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5;
            double r = k % 2 == 0 ? 4.6 : 2.1;
            pts[k] = new double[]{5 + r * Math.cos(a), 5.3 + r * Math.sin(a)};
        }
        boolean in = false;
        for (int a = 0, b = 9; a < 10; b = a++) {
            if ((pts[a][1] > y) != (pts[b][1] > y)
                    && x < (pts[b][0] - pts[a][0]) * (y - pts[a][1]) / (pts[b][1] - pts[a][1]) + pts[a][0]) in = !in;
        }
        return in;
    }

    static char[][] pattern(int forme) {
        char[][] c = new char[N][N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) c[j][i] = inShape(forme, i, j) ? 's' : 'e';
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                if (c[j][i] != 'e') continue;
                boolean adj = (i > 0 && c[j][i - 1] == 's') || (i < N - 1 && c[j][i + 1] == 's')
                        || (j > 0 && c[j - 1][i] == 's') || (j < N - 1 && c[j + 1][i] == 's');
                if (adj) c[j][i] = 'l';
            }
        return c;
    }

    // ------------------------------------------------------------ deroulement

    @Override
    void begin(List<Joueur> alive) {
        int k = 0;
        for (Joueur j : alive) {
            Player p = j.player();
            if (p == null) continue;
            p.teleport(new Location(g.world, DX + 0.5 + (k % 6) * 2 - 5, FY + 1, D_SPAWN_Z + (k / 6) * 2, 180f, 0f));
            k++;
        }
        for (int i = 0; i < 4; i++) {
            int bx = DX + D_BOXX[i];
            g.zoneText(new Location(g.world, bx + 0.5, FY + 10.2, D_BOXZ - 1.5),
                    "&e&l" + NOMS[i] + "\n&7" + NIVEAUX[i] + " &8- &f" + fissures(i) + " fissure" + (fissures(i) > 1 ? "s" : "") + " max", 1.6f);
            if (g.textures()) {
                ItemStack sym = g.skin(new ItemStack(Material.PAPER), "symbole_" + FORMES[i]);
                displayItem(new Location(g.world, bx + 0.5, FY + 8.6, D_BOXZ - 1.5), sym, 2.2f, Display.Billboard.VERTICAL);
            }
        }
        g.guard(new Location(g.world, DX - 24.5, FY + 1, -20.5, -90f, 0f), 0);
        g.guard(new Location(g.world, DX + 25.5, FY + 1, -20.5, 90f, 0f), 1);
        g.guard(new Location(g.world, DX - 29.5, FY + 1, 20.5, -90f, 0f), 2);
        g.guard(new Location(g.world, DX + 30.5, FY + 1, 20.5, 90f, 0f), 0);
        ph = Ph.CHOICE;
        phTick = 0;
        phLen = Math.max(5, g.cfgI("dalgona.choix", 15)) * 20;
    }

    int fissures(int forme) {
        return Math.max(1, g.cfgI("dalgona.fissures." + FORMES[forme], FISSURES[forme]));
    }

    ItemDisplay displayItem(Location l, ItemStack it, float scale, Display.Billboard bb) {
        g.spawning = true;
        try {
            ItemDisplay d = g.world.spawn(l, ItemDisplay.class, e -> {
                e.setItemStack(it);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setBillboard(bb);
                e.setPersistent(false);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                e.setBrightness(new Display.Brightness(15, 15));
                e.addScoreboardTag(EemiSoleil.TAG);
            });
            g.zoneEntities.add(d);
            return d;
        } finally {
            g.spawning = false;
        }
    }

    int padUnder(Player p) {
        Location l = p.getLocation();
        for (int i = 0; i < 4; i++) {
            int bx = DX + D_BOXX[i];
            if (Math.abs(l.getBlockX() - bx) <= 1 && Math.abs(l.getBlockZ() - D_PADZ) <= 1) return i;
        }
        return -1;
    }

    @Override
    void tick() {
        phTick++;
        switch (ph) {
            case CHOICE -> {
                int left = (phLen - phTick + 19) / 20;
                if (phTick % 10 == 0) {
                    for (Joueur j : alive()) {
                        Player p = j.player();
                        if (p == null) continue;
                        int pad = padUnder(p);
                        p.sendActionBar(EemiSoleil.c(pad < 0 ? "&eVa sur le tapis d'une boîte &7(" + left + " s)"
                                : "&aTon choix : &f" + NOMS[pad] + " &7(" + left + " s)"));
                    }
                }
                if (phTick >= phLen) endChoice();
            }
            case PREP -> {
                if (phTick % 20 == 1) {
                    int left = (phLen - phTick + 19) / 20;
                    for (Player p : playersIn()) g.title(p, "&e" + left, "&7Gratte le trait, pas la forme", 900);
                }
                if (phTick >= phLen) {
                    ph = Ph.CUT;
                    phTick = 0;
                    cutTotal = phLen = Math.max(20, g.cfgI("dalgona.temps", 90)) * 20;
                    for (Player p : playersIn()) {
                        g.title(p, "&a&lC'EST PARTI", "&7Clic droit sur les cases orange", 1200);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
                    }
                }
            }
            case CUT -> {
                if (phTick % 5 == 0) {
                    int left = (phLen - phTick + 19) / 20;
                    for (Biscuit b : biscuits.values()) {
                        if (b.over || !b.j.alive) continue;
                        Player p = b.j.player();
                        if (p != null) p.sendActionBar(EemiSoleil.c("&6Trait : &f" + b.scratched + "&7/" + b.lines
                                + " &8| &cFissures : &f" + b.fissures + "&7/" + fissures(b.forme) + " &8| &f⏱ " + left + " s"));
                    }
                }
                if (phTick >= phLen) timeUp();
            }
            default -> {}
        }
    }

    List<Player> playersIn() {
        List<Player> l = new ArrayList<>();
        for (Joueur j : alive()) {
            Player p = j.player();
            if (p != null) l.add(p);
        }
        return l;
    }

    void endChoice() {
        List<Joueur> list = shuffled(alive());
        int k = 0;
        for (Joueur j : list) {
            Player p = j.player();
            if (p == null) continue;
            int f = padUnder(p);
            if (f < 0) {
                f = ThreadLocalRandom.current().nextInt(4);
                EemiSoleil.msg(p, "&7Pas de choix : le sort te donne le &e" + NOMS[f] + "&7.");
            } else {
                EemiSoleil.msg(p, "&7Tu as choisi le &e" + NOMS[f] + "&7.");
            }
            int c = k % 4, r = (k / 4) % D_ROWS;
            k++;
            Biscuit b = new Biscuit(j, f, dColX(c), dWallZ(r));
            biscuits.put(j.id, b);
            draw(b);
            g.anchor(p, new Location(g.world, b.xc + 0.5, FY + 6, b.w + 4.5, 180f, 12f));
            AttributeInstance a = p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            if (a != null) a.setBaseValue(Math.max(a.getDefaultValue(), g.cfgD("dalgona.portee", 10)));
            g.clearHands(p);
            p.getInventory().setItem(0, g.skin(EemiSoleil.named(Material.IRON_NUGGET, "&fAiguille", "&7Clic droit sur une case du trait"), "aiguille"));
            p.getInventory().setItem(8, g.skin(EemiSoleil.named(Material.COOKIE, "&6Dalgona : " + NOMS[f],
                    "&7" + fissures(f) + " fissure(s) au maximum"), "dalgona_" + FORMES[f]));
            p.getInventory().setHeldItemSlot(0);
        }
        ph = Ph.PREP;
        phTick = 0;
        phLen = 60;
    }

    void draw(Biscuit b) {
        char[][] pat = pattern(b.forme);
        boolean tex = g.textures();
        for (int jr = 0; jr < N; jr++)
            for (int i = 0; i < N; i++) {
                b.cell[jr][i] = pat[jr][i];
                if (pat[jr][i] == 'l') b.lines++;
                Block blk = g.world.getBlockAt(b.x(i), b.y(jr), b.w);
                if (tex) {
                    blk.setType(Material.BARRIER, false);
                    String model = pat[jr][i] == 'l' ? "dalgona_trait" : "dalgona_case";
                    b.disp[jr][i] = displayItem(new Location(g.world, b.x(i) + 0.5, b.y(jr) + 0.5, b.w + 1.04, 0f, 0f),
                            g.skin(new ItemStack(Material.PAPER), model), 1f, Display.Billboard.FIXED);
                } else {
                    blk.setType(pat[jr][i] == 'l' ? Material.ORANGE_TERRACOTTA : Material.HONEYCOMB_BLOCK, false);
                }
            }
    }

    void setCell(Biscuit b, int i, int jr, char st) {
        b.cell[jr][i] = st;
        Block blk = g.world.getBlockAt(b.x(i), b.y(jr), b.w);
        boolean gone = st == 'L' || st == 'E';
        if (g.textures()) {
            ItemDisplay d = b.disp[jr][i];
            if (gone) {
                blk.setType(Material.AIR, false);
                if (d != null) d.remove();
                b.disp[jr][i] = null;
            } else if (st == 'f' && d != null) {
                d.setItemStack(g.skin(new ItemStack(Material.PAPER), "dalgona_fissure"));
            }
        } else {
            blk.setType(gone ? Material.AIR : st == 'f' ? Material.BROWN_TERRACOTTA : blk.getType(), false);
        }
        if (gone) {
            BlockData crumb = Material.HONEYCOMB_BLOCK.createBlockData();
            g.world.spawnParticle(Particle.BLOCK, b.x(i) + 0.5, b.y(jr) + 0.5, b.w + 1.1, 6, 0.2, 0.2, 0.05, 0, crumb);
        }
    }

    @Override
    boolean onClick(Player p, Joueur j, PlayerInteractEvent e) {
        if (ph != Ph.CUT) return false;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        if (e.getHand() != EquipmentSlot.HAND) return true;
        Biscuit b = biscuits.get(j.id);
        Block blk = e.getClickedBlock();
        if (b == null || b.over || blk == null) return true;
        if (blk.getZ() != b.w) return true;
        int i = blk.getX() - (b.xc - N / 2);
        int jr = D_BY0 + N - 1 - blk.getY();
        if (i < 0 || i >= N || jr < 0 || jr >= N) return true;
        long now = g.world.getGameTime();
        if (now - b.lastClick < 3) return true;
        b.lastClick = now;
        e.setCancelled(true);
        char st = b.cell[jr][i];
        switch (st) {
            case 'l' -> {
                setCell(b, i, jr, 'L');
                b.scratched++;
                p.playSound(p.getLocation(), Sound.BLOCK_SAND_HIT, 0.8f, 1.6f);
                if (b.scratched >= b.lines) success(b, p);
            }
            case 'e' -> {
                setCell(b, i, jr, 'E');
                p.playSound(p.getLocation(), Sound.BLOCK_SAND_BREAK, 0.4f, 1.8f);
            }
            case 's', 'f' -> {
                if (st == 's') setCell(b, i, jr, 'f');
                b.fissures++;
                p.playSound(p.getLocation(), Sound.BLOCK_DECORATED_POT_SHATTER, 1f, 0.9f);
                p.sendActionBar(EemiSoleil.c("&c&lFISSURE ! &7(" + b.fissures + "/" + fissures(b.forme) + ")"));
                if (b.fissures > fissures(b.forme)) broken(b, p);
            }
            default -> {}
        }
        return true;
    }

    void success(Biscuit b, Player p) {
        b.over = true;
        b.success = true;
        b.doneTick = phTick;
        for (int jr = 0; jr < N; jr++)
            for (int i = 0; i < N; i++) if (b.cell[jr][i] == 'e') setCell(b, i, jr, 'E');
        g.title(p, "&a&lRÉUSSI !", "&7Ta forme est intacte", 2200);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        g.tell("&a✔ n°" + b.j.numero() + " " + b.j.name + " &7a découpé son &e" + NOMS[b.forme] + " &8(" + (b.doneTick / 20) + " s)");
        p.getInventory().setItem(0, null);
        checkAll();
    }

    void broken(Biscuit b, Player p) {
        b.over = true;
        for (int jr = 0; jr < N; jr++)
            for (int i = 0; i < N; i++) if (b.cell[jr][i] == 's' || b.cell[jr][i] == 'f') setCell(b, i, jr, 'E');
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
        if (!g.eliminate(b.j, "Biscuit cassé", 30)) {
            b.success = true;
            b.doneTick = cutTotal;
        }
        checkAll();
    }

    void checkAll() {
        for (Biscuit b : biscuits.values()) if (!b.over && b.j.alive) return;
        if (!done) {
            ph = Ph.END;
            finish();
        }
    }

    @Override
    void timeUp() {
        if (done) return;
        if (ph == Ph.CHOICE) {
            endChoice();
            return;
        }
        if (ph == Ph.PREP) {
            phTick = phLen;
            return;
        }
        g.tell("&c&lTemps écoulé ! &7Les biscuits inachevés sont refusés.");
        List<Biscuit> late = new ArrayList<>();
        for (Biscuit b : biscuits.values()) if (!b.over && b.j.alive) late.add(b);
        late.sort(Comparator.comparingDouble(b -> b.scratched / (double) Math.max(1, b.lines)));
        for (Biscuit b : late) {
            b.over = true;
            if (!g.eliminate(b.j, "Biscuit inachevé", 20)) {
                b.success = true;
                b.doneTick = cutTotal;
            }
        }
        ph = Ph.END;
        finish();
    }

    @Override
    List<Joueur> ranking() {
        List<Joueur> l = new ArrayList<>(alive());
        l.sort(Comparator.comparingInt((Joueur j) -> {
            Biscuit b = biscuits.get(j.id);
            return b == null ? Integer.MAX_VALUE : b.doneTick;
        }).reversed());
        return l;
    }

    @Override
    String departage() {
        return "parmi les plus lents à découper";
    }

    @Override
    void onLeave(Joueur j) {
        Biscuit b = biscuits.get(j.id);
        if (b != null) b.over = true;
        if (ph == Ph.CUT) checkAll();
    }

    @Override
    void cleanup() {
        // tous les biscuits redeviennent vierges (les plaques sont retirees avec les entites de la zone)
        for (int r = 0; r < D_ROWS; r++)
            for (int c = 0; c < 4; c++) {
                int xc = dColX(c), w = dWallZ(r);
                for (int i = 0; i < N; i++)
                    for (int jr = 0; jr < N; jr++) g.world.getBlockAt(xc - N / 2 + i, D_BY0 + jr, w).setType(Material.HONEYCOMB_BLOCK, false);
            }
    }

    @Override
    String barText() {
        return switch (ph) {
            case CHOICE -> "Biscuit dalgona &8| &eChoisis ta boîte &8| &f" + time(phLen - phTick);
            case PREP -> "Biscuit dalgona &8| &ePrêts ?";
            case CUT -> "Biscuit dalgona &8| &f⏱ " + time(phLen - phTick);
            default -> "Biscuit dalgona &8| &7Terminé";
        };
    }

    @Override
    float barProgress() {
        return ph == Ph.CUT || ph == Ph.CHOICE ? (phLen - phTick) / (float) Math.max(1, phLen) : 1f;
    }
}
