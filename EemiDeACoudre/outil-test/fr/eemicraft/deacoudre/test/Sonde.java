package fr.eemicraft.deacoudre.test;

import fr.eemicraft.deacoudre.Builder;
import fr.eemicraft.deacoudre.build.B;
import fr.eemicraft.deacoudre.build.RecordingSink;

/** Sonde : liste les blocs non-air d'une boite (x1 y1 z1 x2 y2 z2). */
public final class Sonde {
    public static void main(String[] a) {
        RecordingSink s = new RecordingSink();
        Builder.build(s, Integer.parseInt(a[6]), Integer.parseInt(a[7]));
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(a[i]);
        for (int y = v[1]; y <= v[4]; y++)
            for (int z = v[2]; z <= v[5]; z++)
                for (int x = v[0]; x <= v[3]; x++) {
                    String st = s.get(x, y, z);
                    if (!B.isAir(st)) System.out.println(x + " " + y + " " + z + " " + st);
                }
    }
}
