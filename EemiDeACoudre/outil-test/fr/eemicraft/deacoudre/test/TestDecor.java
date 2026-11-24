package fr.eemicraft.deacoudre.test;

import fr.eemicraft.deacoudre.Builder;
import fr.eemicraft.deacoudre.build.B;
import fr.eemicraft.deacoudre.build.Check;
import fr.eemicraft.deacoudre.build.RecordingSink;
import fr.eemicraft.deacoudre.build.SchemWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Test hors serveur du decor : java ... TestDecor <avant|apres> <dossier> [rayon] [hauteur].
 * Ecrit dac_<mode>.schem, affiche Check et (pour "apres") verifie le contrat de jeu.
 */
public final class TestDecor {
    static final int WY = 100;

    public static void main(String[] a) throws Exception {
        String mode = a[0];
        File dir = new File(a[1]);
        int r = a.length > 2 ? Integer.parseInt(a[2]) : 4;
        int h = a.length > 3 ? Integer.parseInt(a[3]) : 40;
        int by = WY + h;
        dir.mkdirs();
        RecordingSink s = new RecordingSink();
        long t0 = System.currentTimeMillis();
        if (mode.equals("avant")) AncienDecor.build(s, r, by);
        else Builder.build(s, r, by);
        System.out.println(mode + " : " + s.count() + " blocs, " + s.operations() + " operations, "
                + (System.currentTimeMillis() - t0) + " ms");
        System.out.println(Check.run(s));
        TreeMap<String, Integer> types = new TreeMap<>();
        int[] bd = s.bounds();
        for (int x = bd[0]; x <= bd[3]; x++)
            for (int y = bd[1]; y <= bd[4]; y++)
                for (int z = bd[2]; z <= bd[5]; z++) {
                    String st = s.get(x, y, z);
                    if (!B.isAir(st)) types.merge(B.name(st), 1, Integer::sum);
                }
        System.out.println("Types (" + types.size() + ") : " + types.keySet());
        if (!mode.equals("avant")) {
            List<String> err = contract(s, r, by);
            System.out.println(err.isEmpty() ? "CONTRAT OK" : "CONTRAT KO :\n  " + String.join("\n  ", err));
        }
        File f = new File(dir, "dac_" + mode + (a.length > 2 ? "_r" + r + "_h" + h : "") + ".schem");
        SchemWriter.write(s, f);
        System.out.println("Ecrit " + f.getAbsolutePath());
    }

    static List<String> contract(RecordingSink s, int r, int by) {
        List<String> e = new ArrayList<>();
        int z1 = -(r + 12), z2 = -(r + 3);
        int water = 0;
        for (int x = -r - 1; x <= r + 1; x++)
            for (int z = -r - 1; z <= r + 1; z++) {
                if (x * x + z * z > r * r + r) continue;
                water++;
                if (!B.name(s.get(x, WY - 3, z)).equals("prismarine_bricks")) e.add("fond " + x + "," + z);
                if (!B.name(s.get(x, WY - 2, z)).equals("sea_lantern")) e.add("lanterne " + x + "," + z);
                if (!B.name(s.get(x, WY - 1, z)).equals("water") || !B.name(s.get(x, WY, z)).equals("water")) e.add("eau " + x + "," + z);
                for (int y = WY + 1; y < by; y++)
                    if (!B.isAir(s.get(x, y, z))) { e.add("bloc au-dessus de la piscine " + x + "," + y + "," + z + " " + s.get(x, y, z)); break; }
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], nz = z + d[1];
                    if (nx * nx + nz * nz <= r * r + r) continue;
                    String m = s.get(nx, WY, nz);
                    if (!B.isSolid(m)) e.add("margelle non pleine " + nx + "," + nz + " " + m);
                    for (int y = WY - 3; y <= WY - 1; y++) if (!B.isSolid(s.get(nx, y, nz))) e.add("paroi percee " + nx + "," + y + "," + nz);
                }
            }
        System.out.println("Cases d'eau : " + water);
        // trajectoire sous le plongeoir (|x| <= 2 autour de la planche, jusqu'au bord de la piscine)
        for (int x = -2; x <= 2; x++)
            for (int z = z2 + 1; z <= 0; z++)
                for (int y = WY + 1; y < by; y++)
                    if (!B.isAir(s.get(x, y, z))) { e.add("obstacle sous le plongeoir " + x + "," + y + "," + z + " " + s.get(x, y, z)); break; }
        // plongeoir
        for (int z = z2 + 1; z <= -2; z++) {
            String m = s.get(0, by, z);
            if (z == -2 ? !B.name(m).equals("yellow_concrete") : !m.startsWith("minecraft:spruce_slab")) e.add("planche " + z + " " + m);
            if (!B.isAir(s.get(1, by, z)) || !B.isAir(s.get(-1, by, z))) e.add("plongeoir plus large qu'un bloc en z " + z);
            for (int y = by + 1; y <= by + 4; y++) if (!B.isAir(s.get(0, y, z))) e.add("tete bloquee sur la planche " + y + "," + z);
        }
        // salle d'attente
        int ws = -(r + 7);
        if (!B.isSolid(s.get(0, by, ws))) e.add("pas de sol sous l'arrivee");
        for (int y = by + 1; y <= by + 3; y++) if (!B.isAir(s.get(0, y, ws))) e.add("arrivee bouchee " + y + " " + s.get(0, y, ws));
        for (int z = z2 - 3; z <= z2; z++)
            for (int x = -4; x <= 4; x++) {
                String m = s.get(x, by, z);
                if (!B.name(m).equals("glass") && !(z == z2 && (Math.abs(x) == 1))) e.add("sol non vitre " + x + "," + z + " " + m);
            }
        for (int x = -5; x <= 5; x++) {
            if (Math.abs(x) <= 1) continue;
            if (!B.name(s.get(x, by + 2, z2)).equals("glass")) e.add("mur avant pas en verre " + x);
        }
        for (int z = z1 + 1; z < z2; z++) {
            if (!B.name(s.get(6, by + 2, z)).equals("glass") && !B.name(s.get(6, by + 2, z)).contains("log")) e.add("mur est " + z);
        }
        for (int y = by + 1; y <= by + 3; y++) if (!B.isAir(s.get(0, y, z2))) e.add("porte du plongeoir bouchee " + y);
        return e;
    }
}
