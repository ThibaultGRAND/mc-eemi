package fr.eemicraft.dropper.build;

import fr.eemicraft.dropper.WaitingRoom;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Test hors serveur de la salle d'attente : java TestRoom old|new <dossier>.
 * Ecrit <dossier>/dropper_<mode>.schem, affiche Check et, pour "new", verifie le contrat
 * (point d'arrivee libre, aucune sortie a pied, aucun trou dans le sol).
 */
public final class TestRoom {
    static final int RY = 100;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "new";
        File dir = new File(args.length > 1 ? args[1] : "out");
        dir.mkdirs();
        RecordingSink s = new RecordingSink();
        if (mode.equals("old")) OldRoom.build(s, RY);
        else WaitingRoom.build(s, RY, false);
        System.out.println(mode + " : " + s.count() + " blocs, " + s.operations() + " operations");
        System.out.println(Check.run(s));
        File f = new File(dir, "dropper_" + mode + ".schem");
        SchemWriter.write(s, f);
        System.out.println("Ecrit " + f.getAbsolutePath());
        if (mode.equals("new")) contract(s);
    }

    static boolean passable(String st) {
        String n = B.name(st);
        return B.isAir(st) || n.equals("light") || n.endsWith("_sign") || n.endsWith("carpet") || n.equals("vine")
                || n.equals("iron_chain") || n.equals("water") || (n.equals("lantern") && st.contains("hanging=true"));
    }

    static boolean high(String st) { // plus de 1 bloc de collision : on ne saute pas dessus
        String n = B.name(st);
        return n.endsWith("_wall") || n.endsWith("_fence") || n.equals("barrier") || n.endsWith("_pane");
    }

    static void contract(RecordingSink s) {
        int fails = 0;
        for (int dy = 1; dy <= 3; dy++)
            if (!passable(s.get(0, RY + dy, 5))) { System.out.println("ECHEC point d'arrivee obstrue en y+" + dy + " : " + s.get(0, RY + dy, 5)); fails++; }
        if (!B.isSolid(s.get(0, RY, 5))) { System.out.println("ECHEC pas de sol sous le point d'arrivee"); fails++; }
        // parcours a pied depuis le point d'arrivee
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(new int[]{0, 5});
        seen.add(key(0, 5));
        int reached = 0;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int x = c[0], z = c[1];
            reached++;
            if (Math.max(Math.abs(x), Math.abs(z)) > 14) { System.out.println("ECHEC sortie possible en " + x + "," + z); fails++; break; }
            if (!B.isSolid(s.get(x, RY, z)) && !s.get(x, RY, z).contains("glass")) {
                System.out.println("ECHEC trou dans le sol en " + x + "," + z + " : " + s.get(x, RY, z)); fails++;
            }
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : d) {
                int nx = x + o[0], nz = z + o[1];
                if (seen.contains(key(nx, nz))) continue;
                String a = s.get(nx, RY + 1, nz), b = s.get(nx, RY + 2, nz), c3 = s.get(nx, RY + 3, nz);
                boolean ok = (passable(a) && passable(b)) || (!passable(a) && !high(a) && passable(b) && passable(c3));
                if (ok) { seen.add(key(nx, nz)); q.add(new int[]{nx, nz}); }
            }
        }
        System.out.println("Cases accessibles a pied : " + reached + (fails == 0 ? " ; contrat OK" : " ; " + fails + " echec(s)"));
    }

    static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
}
