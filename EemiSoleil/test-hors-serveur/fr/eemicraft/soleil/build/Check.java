package fr.eemicraft.soleil.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contrôles automatiques (§4 du skill) sur un RecordingSink, mêmes définitions qu'outils/analyze.py :
 * types de blocs, part de détail sur le bâti visible, mélange local, plus long pan coplanaire (tous blocs) et
 * d'un même bloc, zones 10×10 de façade dominées à &gt; 70 % par un bloc, marches entre colonnes voisines.
 * À journaliser à la fin d'un build (test hors serveur) ; les cibles sont rappelées dans le rapport.
 */
public final class Check {
    private Check() {}

    public static final class Report {
        public long blocks;
        public int types, types01;
        public double detailBati, mixing, mixingBati, vegVisible;
        public int panMax, panP90, panSameMax, panSameP90;
        public int zones, zonesDominated;
        public double flat, steps1, steps24, steps5;
        public List<String> topBlocks = new ArrayList<>();

        /** Cibles tenues : détail ≥ 15 %, mélange local &lt; 15 %, pan coplanaire ≤ maxPan. */
        public boolean ok(int maxPan) { return detailBati >= 15 && mixing < 15 && panMax <= maxPan; }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Blocs non-air : %d, types : %d (dont %d à 0,1 %% ou plus) [cible map >= 40 / 20]%n", blocks, types, types01));
            sb.append(String.format("Detail sur le bati visible : %.1f %% [cible >= 15]%n", detailBati));
            sb.append(String.format("Melange local : %.1f %% (bati seul : %.1f %%) [cible < 15]%n", mixing, mixingBati));
            sb.append(String.format("Vegetation visible : %.1f %% [5-9 au sol]%n", vegVisible));
            sb.append(String.format("Pan coplanaire : max %d, p90 %d [cible <= 7 fantasy, <= 12 urbain]%n", panMax, panP90));
            sb.append(String.format("Pan d'un meme bloc : max %d, p90 %d%n", panSameMax, panSameP90));
            sb.append(String.format("Zones 10x10 de facade dominees > 70 %% : %d / %d%n", zonesDominated, zones));
            sb.append(String.format("Voisins meme hauteur %.1f %%, marches de 1 : %.1f %% [12-30], ressauts 2-4 : %.1f %% [4-13], ruptures 5+ : %.1f %%%n",
                    flat, steps1, steps24, steps5));
            sb.append("Top blocs : ").append(String.join(", ", topBlocks));
            return sb.toString();
        }
    }

    private static final int AIR = 0, INVIS = 1, LIQUID = 2, LEAVES = 3, VEG = 4, FULL = 5, DET = 6;

    private static final String[] DET_SUBS = {"_stairs", "_slab", "sign", "banner", "skull", "_head", "flower_pot", "potted_",
            "_wall", "fence", "trapdoor", "_door", "pane", "iron_bars", "carpet", "button", "pressure_plate", "lantern", "torch",
            "candle", "end_rod", "sea_pickle", "chain", "ladder", "lever", "cobweb", "anvil", "cauldron", "hopper", "bell",
            "scaffolding", "rail", "tripwire", "brewing_stand", "enchanting_table", "lectern", "grindstone", "campfire", "bed",
            "pointed_dripstone", "amethyst_cluster", "lightning_rod", "chest", "decorated_pot", "cake", "stonecutter", "cocoa",
            "frame", "daylight_detector", "repeater", "comparator", "redstone_wire", "conduit"};
    private static final String[] VEG_SUBS = {"sapling", "flower", "tulip", "orchid", "allium", "bluet", "daisy", "cornflower", "lily",
            "vine", "fern", "short_grass", "tall_grass", "bush", "petals", "kelp", "seagrass", "sugar_cane", "bamboo", "azalea",
            "dripleaf", "propagule", "poppy", "dandelion", "wheat", "carrots", "potatoes", "beetroot", "cactus", "hanging_roots",
            "lichen", "blossom", "leaf_litter", "wildflowers", "sunflower", "lilac", "peony", "rose_bush"};
    private static final Set<String> NATURAL = new HashSet<>(List.of("stone", "dirt", "grass_block", "andesite", "diorite",
            "granite", "gravel", "sand", "red_sand", "deepslate", "tuff", "coarse_dirt", "podzol", "mycelium", "clay", "calcite",
            "netherrack", "end_stone", "bedrock", "mud", "rooted_dirt", "moss_block", "dirt_path", "farmland", "snow_block",
            "powder_snow", "ice", "packed_ice", "blue_ice", "basalt", "blackstone", "soul_sand", "soul_soil", "magma_block",
            "obsidian", "dripstone_block", "smooth_basalt"));

    static int classify(String n) {
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air")) return AIR;
        if (n.equals("barrier") || n.equals("light") || n.equals("structure_void")) return INVIS;
        if (n.equals("water") || n.equals("lava") || n.equals("bubble_column")) return LIQUID;
        if (n.contains("leaves")) return LEAVES;
        if (n.equals("snow")) return DET;
        if (n.contains("mushroom") && !n.contains("block") && !n.contains("stem")) return VEG;
        for (String s : DET_SUBS) {
            if (!n.contains(s)) continue;
            if (s.equals("_slab") && n.startsWith("double_")) return FULL;
            if (s.equals("_wall") && !n.endsWith("_wall")) continue;
            if (s.equals("bed") && (!n.endsWith("bed") || n.equals("bedrock"))) continue;
            if (s.equals("lantern") && (n.equals("sea_lantern") || n.equals("jack_o_lantern"))) continue;
            return DET;
        }
        for (String s : VEG_SUBS) if (n.contains(s)) return VEG;
        return FULL;
    }

    static boolean natural(String n) { return n.endsWith("_ore") || NATURAL.contains(n); }

    static String family(String b) {
        for (String suf : new String[]{"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_button", "_pressure_plate", "_trapdoor", "_door"})
            if (b.endsWith(suf)) { b = b.substring(0, b.length() - suf.length()); break; }
        for (String pre : new String[]{"waxed_", "exposed_", "weathered_", "oxidized_", "cracked_", "mossy_", "chiseled_", "polished_",
                "smooth_", "cut_", "stripped_", "infested_", "double_"})
            if (b.startsWith(pre)) b = b.substring(pre.length());
        if (b.endsWith("terracotta")) return "terracotta";
        for (String c : new String[]{"wool", "concrete_powder", "concrete", "stained_glass", "glass"}) if (b.endsWith(c)) return c;
        for (String s : new String[]{"_bricks", "_brick", "_tiles", "_planks", "_log", "_wood", "_block", "s"})
            if (b.endsWith(s) && b.length() > s.length() + 2) { b = b.substring(0, b.length() - s.length()); break; }
        if (List.of("cobblestone", "stonebrick", "andesite", "diorite", "granite", "tuff", "stone", "cobbled_deepslate", "stone_brick", "smooth_stone").contains(b))
            return b.contains("deepslate") ? "deepslate" : "stone";
        if (b.contains("deepslate")) return "deepslate";
        return b;
    }

    private static boolean smallPass(String n) {
        for (String s : new String[]{"carpet", "button", "pressure_plate", "lantern", "torch", "candle", "end_rod", "sea_pickle",
                "sign", "banner", "skull", "_head", "flower_pot", "potted_"})
            if (n.contains(s) && !n.equals("sea_lantern") && !n.equals("jack_o_lantern")) return true;
        return false;
    }

    public static Report run(RecordingSink r) {
        Report R = new Report();
        if (r.isEmpty()) return R;
        int[] b = r.bounds();
        int W = b[3] - b[0] + 1, H = b[4] - b[1] + 1, L = b[5] - b[2] + 1;
        int P = r.paletteSize();
        String[] name = new String[P];
        int[] cat = new int[P];
        String[] fam = new String[P];
        boolean[] nat = new boolean[P], wallb = new boolean[P], pass = new boolean[P], full = new boolean[P];
        for (int i = 0; i < P; i++) {
            name[i] = B.name(r.state(i));
            cat[i] = classify(name[i]);
            fam[i] = family(name[i]);
            nat[i] = natural(name[i]);
            full[i] = cat[i] == FULL;
            wallb[i] = full[i] && !nat[i];
            pass[i] = cat[i] == AIR || cat[i] == VEG || (cat[i] == INVIS && !name[i].equals("barrier")) || (cat[i] == DET && smallPass(name[i]));
        }
        short[] D = new short[W * H * L];
        for (int y = 0; y < H; y++)
            for (int z = 0; z < L; z++)
                for (int x = 0; x < W; x++) D[(y * L + z) * W + x] = (short) r.id(b[0] + x, b[1] + y, b[2] + z);
        int sz = W, sy = W * L;
        // ---- palette, visibles, mélange ----
        Map<String, Long> cnt = new HashMap<>();
        long tot = 0, vis = 0, visDet = 0, visBati = 0, visVeg = 0, visFull = 0, mixed = 0, visFullB = 0, mixedB = 0;
        for (int idx = 0; idx < D.length; idx++) {
            int p = D[idx];
            if (cat[p] == AIR || cat[p] == INVIS) continue;
            tot++;
            cnt.merge(name[p], 1L, Long::sum);
            int x = idx % W, z = (idx / W) % L, y = idx / sy;
            boolean e = false, mx = false;
            int[][] nb = {{1, x < W - 1 ? 1 : 0}, {-1, x > 0 ? 1 : 0}, {sz, z < L - 1 ? 1 : 0}, {-sz, z > 0 ? 1 : 0}, {sy, y < H - 1 ? 1 : 0}, {-sy, y > 0 ? 1 : 0}};
            for (int[] d : nb) {
                if (d[1] == 0) { e = true; continue; }
                int q = D[idx + d[0]];
                if (cat[q] == AIR || cat[q] == INVIS || cat[q] == VEG) e = true;
                else if (full[q] && q != p && fam[q].equals(fam[p]) && !name[q].equals(name[p])) mx = true;
            }
            if (!e) continue;
            vis++;
            if (cat[p] == VEG || cat[p] == LEAVES) visVeg++;
            if (!nat[p] && cat[p] != VEG && cat[p] != LEAVES && cat[p] != LIQUID) {
                visBati++;
                if (cat[p] == DET) visDet++;
            }
            if (full[p]) { visFull++; if (mx) mixed++; if (!nat[p]) { visFullB++; if (mx) mixedB++; } }
        }
        R.blocks = tot;
        R.types = cnt.size();
        final long ftot = tot;
        R.types01 = (int) cnt.values().stream().filter(c -> c >= ftot * 0.001).count();
        R.detailBati = 100.0 * visDet / Math.max(1, visBati);
        R.mixing = 100.0 * mixed / Math.max(1, visFull);
        R.mixingBati = 100.0 * mixedB / Math.max(1, visFullB);
        R.vegVisible = 100.0 * visVeg / Math.max(1, vis);
        cnt.entrySet().stream().sorted((a, c) -> Long.compare(c.getValue(), a.getValue())).limit(8)
                .forEach(e -> R.topBlocks.add(String.format("%s %.1f%%", e.getKey(), 100.0 * e.getValue() / ftot)));
        // ---- pans de façade + zones ----
        List<Integer> runs = new ArrayList<>(), same = new ArrayList<>();
        int[][] normals = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // {axe x?, signe}
        for (int nIdx = 0; nIdx < 4; nIdx++) {
            boolean axZ = nIdx < 2;
            int s = normals[nIdx][axZ ? 1 : 0];
            int dn = (axZ ? sz : 1) * s;
            int TL = axZ ? W : L, AL = axZ ? L : W;
            for (int a = 0; a < AL; a++) {
                if (a + s < 0 || a + s >= AL) continue;
                int[][] g = new int[TL][H];
                for (int y = 1; y < H - 1; y++) {
                    int run = 0, sm = 0, prev = -1;
                    for (int t = 0; t <= TL; t++) {
                        boolean face = false;
                        int p = 0;
                        if (t < TL) {
                            int idx = y * sy + (axZ ? a * sz + t : t * sz + a);
                            p = D[idx];
                            if (wallb[p] && pass[D[idx + dn]]) {
                                int up = D[idx + sy], dw = D[idx - sy];
                                face = (wallb[up] && pass[D[idx + sy + dn]]) || (wallb[dw] && pass[D[idx - sy + dn]]);
                            }
                            g[t][y] = face ? p : -1;
                        }
                        if (face) {
                            run++;
                            if (p == prev || (prev >= 0 && name[p].equals(name[prev]))) sm++;
                            else { if (sm >= 3) same.add(sm); sm = 1; }
                            prev = p;
                        } else {
                            if (run >= 3) runs.add(run);
                            if (sm >= 3) same.add(sm);
                            run = 0; sm = 0; prev = -1;
                        }
                    }
                }
                for (int t0 = 0; t0 < TL; t0 += 10)
                    for (int y0 = 0; y0 < H; y0 += 10) {
                        Map<String, Integer> c = new HashMap<>();
                        int n = 0;
                        for (int t = t0; t < Math.min(TL, t0 + 10); t++)
                            for (int y = y0; y < Math.min(H, y0 + 10); y++) {
                                int v = g[t][y];
                                if (v <= 0) continue;
                                n++;
                                c.merge(name[v], 1, Integer::sum);
                            }
                        if (n < 20) continue;
                        R.zones++;
                        int mxc = Collections.max(c.values());
                        if (mxc > 0.7 * n) R.zonesDominated++;
                    }
            }
        }
        Collections.sort(runs); Collections.sort(same);
        R.panMax = runs.isEmpty() ? 0 : runs.get(runs.size() - 1);
        R.panP90 = runs.isEmpty() ? 0 : runs.get(Math.min(runs.size() - 1, (int) (0.9 * runs.size())));
        R.panSameMax = same.isEmpty() ? 0 : same.get(same.size() - 1);
        R.panSameP90 = same.isEmpty() ? 0 : same.get(Math.min(same.size() - 1, (int) (0.9 * same.size())));
        // ---- relief : marches entre colonnes voisines ----
        int[][] top = new int[W][L];
        for (int x = 0; x < W; x++)
            for (int z = 0; z < L; z++) {
                top[x][z] = -1;
                for (int y = H - 1; y >= 0; y--) {
                    int p = D[(y * L + z) * W + x];
                    if (cat[p] != AIR && cat[p] != INVIS && cat[p] != VEG) { top[x][z] = y; break; }
                }
            }
        long n0 = 0, n1 = 0, n24 = 0, n5 = 0, ns = 0;
        for (int x = 0; x < W - 1; x++)
            for (int z = 0; z < L - 1; z++) {
                int h = top[x][z];
                if (h < 0) continue;
                for (int h2 : new int[]{top[x + 1][z], top[x][z + 1]}) {
                    if (h2 < 0) continue;
                    int d = Math.abs(h2 - h);
                    ns++;
                    if (d == 0) n0++; else if (d == 1) n1++; else if (d <= 4) n24++; else n5++;
                }
            }
        R.flat = 100.0 * n0 / Math.max(1, ns);
        R.steps1 = 100.0 * n1 / Math.max(1, ns);
        R.steps24 = 100.0 * n24 / Math.max(1, ns);
        R.steps5 = 100.0 * n5 / Math.max(1, ns);
        return R;
    }
}
