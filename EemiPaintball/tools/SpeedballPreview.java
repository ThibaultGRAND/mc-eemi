package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.Check;
import fr.eemicraft.paintball.build.RecordingSink;
import fr.eemicraft.paintball.build.SchemWriter;
import java.io.File;

/**
 * Test hors serveur du terrain de speedball (pas embarque dans le jar : dossier tools/).
 * Construit le VRAI builder dans un RecordingSink, ecrit deux .schem (map entiere sans la salle d'attente,
 * et zone jouable seule dans le filet, sans barrieres) puis affiche le rapport Check.
 * Usage : java -cp classes fr.eemicraft.paintball.SpeedballPreview <dossier de sortie> [prefixe]
 */
public final class SpeedballPreview {
    public static void main(String[] a) throws Exception {
        String prefix = a.length > 1 ? a[1] : "speedball_apres";
        RecordingSink s = new RecordingSink();
        new Speedball(s).build();
        // copie sans la salle d'attente (y >= 130)
        RecordingSink map = new RecordingSink();
        RecordingSink jeu = new RecordingSink();
        for (int x = -46; x <= 46; x++) {
            for (int z = -36; z <= 36; z++) {
                for (int y = 95; y <= 125; y++) {
                    String st = s.get(x, y, z);
                    if (st.equals("minecraft:air")) {
                        continue;
                    }
                    map.set(x, y, z, st);
                    if (Math.abs(x) < Speedball.NX && Math.abs(z) < Speedball.NZ && y >= Speedball.FY - 1
                        && !st.endsWith(":barrier")) {
                        jeu.set(x, y, z, st);
                    }
                }
            }
        }
        File dir = new File(a[0]);
        dir.mkdirs();
        SchemWriter.write(map, new File(dir, prefix + ".schem"));
        SchemWriter.write(jeu, new File(dir, prefix + "_jeu.schem"));
        System.out.println(Check.run(map));
        // contrat : dalles d'apparition plates et libres
        int bad = 0;
        for (int x : new int[]{-29, 29}) {
            for (int z = -5; z <= 5; z++) {
                if (!s.get(x, Speedball.FY + 1, z).equals("minecraft:air") || !s.get(x, Speedball.FY + 2, z).equals("minecraft:air")
                    || s.get(x, Speedball.FY, z).equals("minecraft:air")) {
                    bad++;
                    System.out.println("Apparition genee en " + x + " " + z + " : " + s.get(x, Speedball.FY + 1, z));
                }
            }
        }
        // contrat : sol plein en FY dans la zone jouable, aucun bloc invisible dans le filet
        for (int x = -34; x <= 34; x++) {
            for (int z = -22; z <= 22; z++) {
                if (s.get(x, Speedball.FY, z).equals("minecraft:air")) {
                    bad++;
                }
                for (int y = Speedball.FY; y <= Speedball.FY + 20; y++) {
                    String st = s.get(x, y, z);
                    if (st.contains("barrier") || st.equals("minecraft:light") || st.startsWith("minecraft:light[")) {
                        bad++;
                        System.out.println("Bloc invisible dans le terrain : " + x + " " + y + " " + z + " " + st);
                    }
                }
            }
        }
        System.out.println("Problemes de contrat : " + bad);
        // lignes de tir directes d'une base a l'autre, a hauteur des yeux (y = FY + 2)
        int open = 0, tot = 0;
        for (int z1 = -5; z1 <= 5; z1++) {
            for (int z2 = -5; z2 <= 5; z2++) {
                tot++;
                boolean blocked = false;
                for (int i = 0; i <= 116 && !blocked; i++) {
                    double t = i / 116.0;
                    int x = (int) Math.floor(-28.5 + 57 * t);
                    int z = (int) Math.floor(z1 + 0.5 + (z2 - z1) * t);
                    if (fr.eemicraft.paintball.build.B.isSolid(s.get(x, Speedball.FY + 2, z))) {
                        blocked = true;
                    }
                }
                if (!blocked) {
                    open++;
                    System.out.println("  ligne degagee z " + z1 + " -> " + z2);
                }
            }
        }
        System.out.println("Lignes de tir base a base degagees a hauteur des yeux : " + open + " / " + tot);
    }
}
