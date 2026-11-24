package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.B;
import fr.eemicraft.paintball.build.Check;
import fr.eemicraft.paintball.build.RecordingSink;
import fr.eemicraft.paintball.build.SchemWriter;
import java.io.File;
import java.io.PrintWriter;
import java.util.TreeMap;

/**
 * Test hors serveur d'une petite map (pas embarque dans le jar : dossier tools/).
 * Construit le VRAI builder dans un RecordingSink (terrain + salle d'attente comme sur le serveur), ecrit
 * &lt;id&gt;.schem (map sans la salle d'attente) et &lt;id&gt;_jeu.schem (zone dans le filet, sans barrieres), la liste
 * des etats de bloc (&lt;id&gt;_etats.txt, pour la validation contre blocks.json), puis verifie le contrat.
 * Usage : java -cp classes fr.eemicraft.paintball.FieldPreview &lt;dossier&gt; &lt;id&gt;
 */
public final class FieldPreview {
    public static void main(String[] a) throws Exception {
        String id = a[1];
        RecordingSink s = new RecordingSink();
        // on part du speedball pose (cas reel : changement de map au lancement d'une partie)
        new Speedball(s).build();
        long before = s.operations();
        SmallMaps.buildField(id, s);
        long ops = s.operations() - before;
        RecordingSink map = new RecordingSink();
        RecordingSink jeu = new RecordingSink();
        TreeMap<String, Integer> states = new TreeMap<>();
        for (int x = -46; x <= 46; x++) {
            for (int z = -36; z <= 36; z++) {
                for (int y = 95; y <= 125; y++) {
                    String st = s.get(x, y, z);
                    if (st.equals("minecraft:air")) {
                        continue;
                    }
                    states.merge(st, 1, Integer::sum);
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
        SchemWriter.write(map, new File(dir, id + ".schem"));
        SchemWriter.write(jeu, new File(dir, id + "_jeu.schem"));
        try (PrintWriter w = new PrintWriter(new File(dir, "states_" + id + ".txt"), "UTF-8")) {
            for (var e : states.entrySet()) {
                w.println(e.getKey() + "\t" + e.getValue());
            }
        }
        System.out.println("Poses pour changer de map : " + ops);
        System.out.println(Check.run(map));
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
        // spectateurs : point 0.5 FY+6.5 29.5 libre, marche dessous
        if (!s.isAir(0, Speedball.FY + 6, 29) || !s.isAir(0, Speedball.FY + 7, 29)) {
            bad++;
            System.out.println("Point spectateurs gene : " + s.get(0, Speedball.FY + 6, 29));
        }
        for (int x = -34; x <= 34; x++) {
            for (int z = -22; z <= 22; z++) {
                if (s.get(x, Speedball.FY, z).equals("minecraft:air")) {
                    bad++;
                    System.out.println("Trou dans le sol en " + x + " " + z);
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
        // restes de l'ancienne map hors de la zone videe par la nouvelle
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                for (int y = 90; y <= 135; y++) {
                    boolean inBox = x >= -48 && x <= 47 && z >= -48 && z <= 47 && y >= 95 && y <= 122;
                    if (!inBox && !s.get(x, y, z).equals("minecraft:air") && y < 130) {
                        bad++;
                        System.out.println("Bloc hors de la zone videe : " + x + " " + y + " " + z + " " + s.get(x, y, z));
                    }
                }
            }
        }
        // la map seule doit tenir dans la zone que le speedball vide (retour au speedball sans reste)
        if (!id.equals("speedball")) {
            RecordingSink alone = new RecordingSink();
            SmallMaps.buildField(id, alone);
            int[] bb = alone.bounds();
            for (int x = bb[0]; x <= bb[3]; x++) {
                for (int y = bb[1]; y <= bb[4]; y++) {
                    for (int z = bb[2]; z <= bb[5]; z++) {
                        boolean inBox = Math.abs(x) <= 46 && Math.abs(z) <= 36 && y >= 95 && y <= 122;
                        if (!inBox && !alone.get(x, y, z).equals("minecraft:air")) {
                            bad++;
                            System.out.println("Bloc hors de la zone videe par le speedball : " + x + " " + y + " " + z);
                        }
                    }
                }
            }
        }
        System.out.println("Problemes de contrat : " + bad);
        int open = 0, tot = 0;
        for (int z1 = -5; z1 <= 5; z1++) {
            for (int z2 = -5; z2 <= 5; z2++) {
                tot++;
                boolean blocked = false;
                for (int i = 0; i <= 116 && !blocked; i++) {
                    double t = i / 116.0;
                    int x = (int) Math.floor(-28.5 + 57 * t);
                    int z = (int) Math.floor(z1 + 0.5 + (z2 - z1) * t);
                    if (B.isSolid(s.get(x, Speedball.FY + 2, z))) {
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
