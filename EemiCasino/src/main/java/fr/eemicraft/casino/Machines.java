package fr.eemicraft.casino;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.joml.Quaternionf;

/**
 * Machines a sous et video poker en 3D (modeles eemicraft:casino/machine_sous, machine_levier, video_poker).
 * Machine a sous : face au sud contre le mur nord, 3 rouleaux de 3 symboles devant l'ecran (memes icones que le menu),
 * levier qui s'abaisse a chaque lancer. Video poker : face a l'ouest contre le mur est, les 5 cartes du joueur sur l'ecran
 * (les cartes gardees remontent un peu). Reperes en unites de modele (16 = la largeur du modele), centre en 8,8,8.
 */
final class Machines {
    static final float SLOT_SCALE = 1.6f;
    static final float VP_SCALE = 1.4f;
    private static final double[] REEL_X = {4.75, 8, 11.25};
    private static final double[] REEL_Y = {17.5, 15.5, 13.5};

    private final EemiCasino pl;
    private final ItemDisplay[] slotBody = new ItemDisplay[Layout.SLOT_X.length];
    private final ItemDisplay[] levers = new ItemDisplay[Layout.SLOT_X.length];
    private final ItemDisplay[][] reels = new ItemDisplay[Layout.SLOT_X.length][9];
    private final String[][] reelIcon = new String[Layout.SLOT_X.length][9];
    private final ItemDisplay[] vpBody = new ItemDisplay[Layout.VP_Z.length];
    private final ItemDisplay[][] vpCards = new ItemDisplay[Layout.VP_Z.length][5];
    private final Location[][] vpCardBase = new Location[Layout.VP_Z.length][5];
    private final int[] leverToken = new int[Layout.SLOT_X.length];

    /** Joueur qui pilote l'ecran 3D de chaque machine (un seul a la fois). */
    private final java.util.Map<Integer, java.util.UUID> slotOwner = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.UUID> vpOwner = new java.util.HashMap<>();

    Machines(EemiCasino pl) {
        this.pl = pl;
    }

    /** Libre, deja a lui, ou titulaire parti du casino : la machine est a lui. */
    private boolean claim(java.util.Map<Integer, java.util.UUID> owners, int m, java.util.UUID id) {
        if (m < 0) {
            return false;
        }
        java.util.UUID cur = owners.get(m);
        org.bukkit.entity.Player p = cur == null ? null : org.bukkit.Bukkit.getPlayer(cur);
        if (cur == null || cur.equals(id) || p == null || !pl.inCasino(p)) {
            owners.put(m, id);
            return true;
        }
        return false;
    }

    boolean claimSlot(int m, java.util.UUID id) {
        return claim(slotOwner, m, id);
    }

    void releaseSlot(int m, java.util.UUID id) {
        if (m >= 0 && id.equals(slotOwner.get(m))) {
            slotOwner.remove(m);
        }
    }

    boolean claimVp(int m, java.util.UUID id) {
        return claim(vpOwner, m, id);
    }

    /** Libere le video poker et remet les cartes de dos. */
    void releaseVp(int m, java.util.UUID id) {
        if (m >= 0 && id.equals(vpOwner.get(m))) {
            vpOwner.remove(m);
            showVideoPoker(m, new int[]{-1, -1, -1, -1, -1}, new boolean[5], false);
        }
    }

    static String ownerName(java.util.Map<Integer, java.util.UUID> owners, int m) {
        java.util.UUID id = owners.get(m);
        org.bukkit.entity.Player p = id == null ? null : org.bukkit.Bukkit.getPlayer(id);
        return p == null ? "un joueur" : p.getName();
    }

    String slotOwnerName(int m) {
        return ownerName(slotOwner, m);
    }

    String vpOwnerName(int m) {
        return ownerName(vpOwner, m);
    }

    static Location slotCenter(EemiCasino pl, int i) {
        return new Location(pl.world, Layout.SLOT_X[i] + 0.5, Layout.FLOOR + 1 + 8 * SLOT_SCALE / 16.0, Layout.SLOT_Z + 0.7);
    }

    static Location vpCenter(EemiCasino pl, int i) {
        return new Location(pl.world, Layout.VP_X + 0.4, Layout.FLOOR + 1 + 8 * VP_SCALE / 16.0, Layout.VP_Z[i] + 0.5);
    }

    void spawn() {
        for (int i = 0; i < slotBody.length; i++) {
            slotBody[i] = null;
            levers[i] = null;
            for (int k = 0; k < 9; k++) {
                reels[i][k] = null;
                reelIcon[i][k] = null;
            }
        }
        for (int i = 0; i < vpBody.length; i++) {
            vpBody[i] = null;
            for (int k = 0; k < 5; k++) {
                vpCards[i][k] = null;
            }
        }
        if (!Models.on(pl) || pl.world == null) {
            return;
        }
        double u = SLOT_SCALE / 16.0;
        Slots.Sym[] syms = Slots.Sym.values();
        for (int i = 0; i < Layout.SLOT_X.length; i++) {
            Location c = slotCenter(pl, i);
            slotBody[i] = Models.spawn(pl, c, "machine_sous", new Quaternionf(), SLOT_SCALE);
            levers[i] = Models.spawn(pl, c.clone().add((15.8 - 8) * u, (14.5 - 8) * u, (8.5 - 8) * u), "machine_levier", new Quaternionf(), SLOT_SCALE);
            for (int r = 0; r < 3; r++) {
                for (int row = 0; row < 3; row++) {
                    int k = r * 3 + row;
                    String icon = syms[(i + k * 7) % syms.length].icon;
                    Location l = c.clone().add((REEL_X[r] - 8) * u, (REEL_Y[row] - 8) * u, (14.6 - 8) * u + 0.012);
                    reels[i][k] = Models.spawn(pl, l, "icone/" + icon, new Quaternionf(), 0.19f);
                    reelIcon[i][k] = icon;
                }
            }
        }
        double v = VP_SCALE / 16.0;
        Quaternionf west = Models.rotY(-90);
        for (int i = 0; i < Layout.VP_Z.length; i++) {
            Location c = vpCenter(pl, i);
            vpBody[i] = Models.spawn(pl, c, "video_poker", west, VP_SCALE);
            for (int k = 0; k < 5; k++) {
                // modele tourne de -90 : decalage (dx, dy, dz) du modele -> monde (-dz, dy, dx)
                double dx = (k - 2) * 2.3;
                Location l = c.clone().add(-(13.6 - 8) * v - 0.012, (16.5 - 8) * v, dx * v);
                vpCardBase[i][k] = l;
                vpCards[i][k] = Models.spawn(pl, l, "carte/dos", Models.rotY(-90), 0.26f);
                if (vpCards[i][k] != null) {
                    vpCards[i][k].setTeleportDuration(3);
                }
            }
        }
    }

    /** Rouleaux 3D : colonne r, ligne row (0 = haut). */
    void showReels(int machine, Slots.Sym[][] shown) {
        if (machine < 0 || machine >= reels.length) {
            return;
        }
        for (int r = 0; r < 3; r++) {
            for (int row = 0; row < 3; row++) {
                int k = r * 3 + row;
                ItemDisplay d = reels[machine][k];
                String icon = shown[r][row].icon;
                if (d != null && d.isValid() && !icon.equals(reelIcon[machine][k])) {
                    d.setItemStack(Models.item("icone/" + icon));
                    reelIcon[machine][k] = icon;
                }
            }
        }
    }

    /** Levier abaisse puis remonte. */
    void pull(int machine) {
        if (machine < 0 || machine >= levers.length) {
            return;
        }
        ItemDisplay d = levers[machine];
        if (d == null || !d.isValid()) {
            return;
        }
        int token = ++leverToken[machine];
        Models.pose(pl, d, new Quaternionf().rotateX((float) Math.toRadians(55)), SLOT_SCALE, 3);
        pl.later(8, () -> {
            if (leverToken[machine] == token && d.isValid()) {
                Models.pose(pl, d, new Quaternionf(), SLOT_SCALE, 6);
            }
        });
    }

    /** Cartes du video poker (-1 = dos), cartes gardees un peu plus haut. */
    void showVideoPoker(int machine, int[] cards, boolean[] hold, boolean dealt) {
        if (machine < 0 || machine >= vpCards.length) {
            return;
        }
        for (int k = 0; k < 5; k++) {
            ItemDisplay d = vpCards[machine][k];
            if (d == null || !d.isValid()) {
                continue;
            }
            d.setItemStack(Models.item(cards[k] < 0 ? "carte/dos" : Models.card(cards[k])));
            Location base = vpCardBase[machine][k];
            d.teleport(dealt && hold[k] ? base.clone().add(0, 0.05, 0) : base);
        }
    }
}
