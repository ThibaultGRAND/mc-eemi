package fr.eemicraft.murder;

import fr.eemicraft.murder.build.RecordingSink;

/** Sonde de mise au point : java Sonde x1 z1 x2 z2 [y1 y2] -> blocs (colonne du haut, ou tranche y1..y2). */
public final class Sonde {
    public static void main(String[] a) {
        RecordingSink s = new RecordingSink();
        Manoir.build(s);
        int x1 = Integer.parseInt(a[0]), z1 = Integer.parseInt(a[1]), x2 = Integer.parseInt(a[2]), z2 = Integer.parseInt(a[3]);
        if (a.length >= 6) {
            int y1 = Integer.parseInt(a[4]), y2 = Integer.parseInt(a[5]);
            for (int y = y2; y >= y1; y--)
                for (int x = x1; x <= x2; x++)
                    for (int z = z1; z <= z2; z++) {
                        String st = s.get(x, y, z);
                        if (!st.equals("minecraft:air")) System.out.println(x + " " + y + " " + z + " " + st);
                    }
            return;
        }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = 100; y > 50; y--)
                    if (!s.get(x, y, z).equals("minecraft:air")) {
                        System.out.println(x + " " + z + " top " + y + " " + s.get(x, y, z));
                        break;
                    }
    }
}
