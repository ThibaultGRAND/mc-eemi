package fr.eemicraft.abysse;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Génération des strates (grottes infinies) et des mondes vides (Bordgouffre, Concessions).
 *
 * Une strate : bedrock en y 0 et de y 117 à 119, roche entre les deux creusée par deux bruits de Simplex
 * (grandes salles + boyaux), un camp d'arrivée en (0, 0), l'antre du gardien à ~320 blocs, une galerie
 * principale éclairée de lanternes entre les deux (toujours praticable), des campements abandonnés (tonneau
 * rempli par le plugin quand le chunk est créé) et des filons (3 × 3 au sol, pour les foreuses).
 * Tout est déterministe : le plugin retrouve les mêmes positions sans rien enregistrer.
 */
final class Gen {

    static final int FLOOR = 0, CEIL = 117;
    static final int CAMP_Y = 59;
    static final int LAIR_DIST = 320, LAIR_FLOOR = 40, LAIR_R = 20;

    /** Description d'une strate. */
    static final class Strata {
        final int n;
        final String name;
        final Material[] filons;
        final String[] filonOres;
        final long seed;
        final double lairX, lairZ;
        final List<double[]> path = new ArrayList<>();

        Strata(int n, String name, Material[] filons, String[] filonOres) {
            this.n = n;
            this.name = name;
            this.filons = filons;
            this.filonOres = filonOres;
            this.seed = 0xAB155EL * 31 + n * 1_000_003L;
            Random r = new Random(seed);
            double a = r.nextDouble() * Math.PI * 2;
            lairX = Math.round(Math.cos(a) * LAIR_DIST);
            lairZ = Math.round(Math.sin(a) * LAIR_DIST);
            // galerie principale : du camp (0, 62, 0) à l'antre (lair, 45), qui serpente
            double ph1 = r.nextDouble() * 6, ph2 = r.nextDouble() * 6;
            double px = -Math.sin(a), pz = Math.cos(a);
            int steps = 260;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps, env = Math.sin(t * Math.PI);
                double side = Math.sin(t * Math.PI * 3 + ph1) * 38 * env;
                double x = lairX * t + px * side, z = lairZ * t + pz * side;
                double y = 62 + (LAIR_FLOOR + 5 - 62) * t + Math.sin(t * Math.PI * 5 + ph2) * 9 * env;
                path.add(new double[]{x, y, z});
            }
        }

        String world() {
            return "abysse_s" + n;
        }

        Location camp(World w) {
            return new Location(w, 0.5, CAMP_Y + 1, 3.5, 180, 0);
        }

        Location lair(World w) {
            return new Location(w, lairX + 0.5, LAIR_FLOOR + 1, lairZ + 0.5);
        }

        /** Minerai donné par un filon (bloc), ou null. */
        String filonOre(Material m) {
            for (int i = 0; i < filons.length; i++) if (filons[i] == m) return filonOres[i];
            return null;
        }

        boolean nearCamp(double x, double z, double r) {
            return x * x + z * z < r * r;
        }

        boolean nearLair(double x, double z, double r) {
            double dx = x - lairX, dz = z - lairZ;
            return dx * dx + dz * dz < r * r;
        }
    }

    static final Strata[] STRATAS = {
            new Strata(1, "Les Galeries",
                    new Material[]{Material.COAL_BLOCK, Material.RAW_COPPER_BLOCK, Material.RAW_IRON_BLOCK},
                    new String[]{"charbon", "minerai_cuivre", "minerai_fer"}),
            new Strata(2, "La Champignonnière",
                    new Material[]{Material.LAPIS_BLOCK, Material.RAW_COPPER_BLOCK},
                    new String[]{"minerai_etain", "minerai_cuivre"}),
            new Strata(3, "Les Forges Englouties",
                    new Material[]{Material.IRON_BLOCK, Material.RAW_GOLD_BLOCK},
                    new String[]{"minerai_argent", "soufre"}),
            new Strata(4, "La Mer de Cristal",
                    new Material[]{Material.EMERALD_BLOCK, Material.AMETHYST_BLOCK},
                    new String[]{"minerai_cobalt", "cristal_resonant"}),
            new Strata(5, "Le Néant",
                    new Material[]{Material.QUARTZ_BLOCK, Material.NETHERITE_BLOCK},
                    new String[]{"minerai_enderite", "eclat_neant"}),
    };

    static Strata strata(int n) {
        return n >= 1 && n <= STRATAS.length ? STRATAS[n - 1] : null;
    }

    static Strata strata(World w) {
        return w == null ? null : strata(w.getName());
    }

    static Strata strata(String world) {
        for (Strata s : STRATAS) if (s.world().equals(world)) return s;
        return null;
    }

    /** Minerais (bloc → objet) de chaque strate. */
    static final Map<Material, String> ORES_1 = Map.of(
            Material.COAL_ORE, "charbon", Material.DEEPSLATE_COAL_ORE, "charbon",
            Material.COPPER_ORE, "minerai_cuivre", Material.DEEPSLATE_COPPER_ORE, "minerai_cuivre",
            Material.IRON_ORE, "minerai_fer", Material.DEEPSLATE_IRON_ORE, "minerai_fer");
    static final Map<Material, String> ORES_2 = Map.of(
            Material.DEEPSLATE_LAPIS_ORE, "minerai_etain", Material.DEEPSLATE_COPPER_ORE, "minerai_cuivre",
            Material.DEEPSLATE_COAL_ORE, "charbon", Material.SHROOMLIGHT, "spore_lumineuse");

    static final Map<Material, String> ORES_3 = Map.of(
            Material.DEEPSLATE_DIAMOND_ORE, "minerai_argent", Material.NETHER_GOLD_ORE, "soufre",
            Material.DEEPSLATE_COAL_ORE, "charbon", Material.DEEPSLATE_IRON_ORE, "minerai_fer");
    static final Map<Material, String> ORES_4 = Map.of(
            Material.DEEPSLATE_EMERALD_ORE, "minerai_cobalt", Material.BUDDING_AMETHYST, "cristal_resonant",
            Material.DEEPSLATE_DIAMOND_ORE, "minerai_argent", Material.DEEPSLATE_COAL_ORE, "charbon", Material.DEEPSLATE_IRON_ORE, "minerai_fer");
    static final Map<Material, String> ORES_5 = Map.of(
            Material.NETHER_QUARTZ_ORE, "minerai_enderite", Material.ANCIENT_DEBRIS, "eclat_neant",
            Material.DEEPSLATE_EMERALD_ORE, "minerai_cobalt", Material.DEEPSLATE_COAL_ORE, "charbon", Material.DEEPSLATE_DIAMOND_ORE, "minerai_argent");

    /** Sous ce y, les cavités de la Mer de Cristal (strate 4) sont noyées. */
    static final int SEA_4 = 30;

    /**
     * Dureté d'un minerai : palier de pioche nécessaire pour le casser (0 = n'importe laquelle). Chaque nouvelle
     * strate repousse d'abord : ses minerais neufs demandent une pioche qu'on ne fabrique qu'avec du métier.
     */
    static int hardness(int strata, Material m) {
        if (strata >= 5 && (m == Material.NETHER_QUARTZ_ORE || m == Material.ANCIENT_DEBRIS)) return 6;
        if (strata >= 4 && (m == Material.DEEPSLATE_EMERALD_ORE || m == Material.BUDDING_AMETHYST)) return 5;
        if (strata >= 3 && (m == Material.DEEPSLATE_DIAMOND_ORE || m == Material.NETHER_GOLD_ORE)) return 3;
        return 0;
    }

    /** Palier d'une pioche de l'Abysse (0 = pas une pioche de l'Abysse). */
    static int pickTier(String id) {
        if (id == null) return 0;
        return switch (id) {
            case "pioche_fer" -> 1;
            case "pioche_bronze" -> 2;
            case "pioche_acier" -> 3;
            case "pioche_abyssale" -> 4;
            case "pioche_forge" -> 5;
            case "pioche_cristal" -> 6;
            default -> 0;
        };
    }

    static String pickName(int tier) {
        return switch (tier) {
            case 3 -> "une Pioche en acier (ou mieux)";
            case 4 -> "une Pioche abyssale";
            case 5 -> "une Pioche de forge (ou mieux)";
            case 6 -> "une Pioche de cristal";
            default -> "une meilleure pioche";
        };
    }

    static Map<Material, String> ores(int strata) {
        return strata == 1 ? ORES_1 : strata == 2 ? ORES_2 : strata == 3 ? ORES_3 : strata == 4 ? ORES_4 : strata == 5 ? ORES_5 : Map.of();
    }

    // ------------------------------------------------------------------ hachage déterministe

    static int hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h & 0x7fffffff);
    }

    static int hash3(int x, int y, int z) {
        return hash(x * 31 + y, z, y * 7 + 3);
    }

    /** Un campement abandonné dans ce chunk ? */
    static boolean campChunk(Strata s, int cx, int cz) {
        double x = cx * 16 + 8, z = cz * 16 + 8;
        return hash(cx, cz, s.n * 11 + 1) % 9 == 0 && !s.nearCamp(x, z, 40) && !s.nearLair(x, z, 44);
    }

    // ------------------------------------------------------------------ générateur des strates

    static final class StrataGen extends ChunkGenerator {
        private final Strata s;
        private final SimplexNoiseGenerator big, small, ore, rock, tun1, tun2;

        StrataGen(Strata s) {
            this.s = s;
            big = new SimplexNoiseGenerator(s.seed);
            small = new SimplexNoiseGenerator(s.seed + 1);
            ore = new SimplexNoiseGenerator(s.seed + 2);
            rock = new SimplexNoiseGenerator(s.seed + 3);
            tun1 = new SimplexNoiseGenerator(s.seed + 4);
            tun2 = new SimplexNoiseGenerator(s.seed + 5);
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
            // les constantes de Biome passent par le registre : jamais dans un initialiseur statique
            return new OneBiome(s.n == 2 ? Biome.MUSHROOM_FIELDS : s.n == 3 ? Biome.BASALT_DELTAS
                    : s.n == 4 ? Biome.LUSH_CAVES : s.n == 5 ? Biome.END_BARRENS : Biome.DRIPSTONE_CAVES);
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return s.camp(world);
        }

        /** Densité de vide : > 0 = air. */
        double cave(int x, int y, int z) {
            double v = big.noise(x / 64.0, y / 30.0, z / 64.0) + small.noise(x / 22.0, y / 13.0, z / 22.0) * 0.55;
            // moins de vide près du sol et du plafond
            double edge = Math.min(y - 6, CEIL - 6 - y);
            if (edge < 10) v -= (10 - edge) * 0.06;
            if (v > 0.4) return 1;
            // boyaux (« spaghettis ») qui relient les salles : là où deux bruits valent tous les deux ~0
            double a = tun1.noise(x / 48.0, y / 26.0, z / 48.0), b = tun2.noise(x / 48.0, y / 26.0, z / 48.0);
            double t = 0.016 - (a * a + b * b);
            return Math.max(v - 0.4, edge < 4 ? -1 : t);
        }

        Material rockAt(int x, int y, int z) {
            if (s.n == 1) return y < 40 ? Material.DEEPSLATE : Material.STONE;
            if (s.n == 3) {
                double r = rock.noise(x / 26.0, y / 18.0, z / 26.0);
                return r > 0.45 ? Material.BASALT : r < -0.3 ? Material.DEEPSLATE : Material.BLACKSTONE;
            }
            if (s.n == 4) {
                double r = rock.noise(x / 24.0, y / 16.0, z / 24.0);
                return r > 0.3 ? Material.CALCITE : r < -0.45 ? Material.TUFF : Material.DEEPSLATE;
            }
            if (s.n == 5) {
                double r = rock.noise(x / 22.0, y / 14.0, z / 22.0);
                return r < -0.42 ? Material.OBSIDIAN : Material.END_STONE;
            }
            return rock.noise(x / 30.0, y / 20.0, z / 30.0) > 0.35 ? Material.DEEPSLATE : Material.TUFF;
        }

        Material oreAt(int x, int y, int z, Material base) {
            if (ore.noise(x / 5.0, y / 5.0, z / 5.0) < 0.76) return null;
            int h = hash3(x, y, z) % 100;
            boolean deep = base == Material.DEEPSLATE;
            if (s.n == 1) {
                if (h < 45) return deep ? Material.DEEPSLATE_COAL_ORE : Material.COAL_ORE;
                if (h < 80) return deep ? Material.DEEPSLATE_COPPER_ORE : Material.COPPER_ORE;
                return deep ? Material.DEEPSLATE_IRON_ORE : Material.IRON_ORE;
            }
            if (s.n == 3) {
                // l'argent et le soufre sont rares : il faut creuser et automatiser longtemps
                if (h < 22) return Material.DEEPSLATE_DIAMOND_ORE;
                if (h < 46) return Material.NETHER_GOLD_ORE;
                if (h < 78) return Material.DEEPSLATE_COAL_ORE;
                return Material.DEEPSLATE_IRON_ORE;
            }
            if (s.n == 4) {
                // cobalt et cristal : durs (Pioche de forge), et rares
                if (h < 18) return Material.DEEPSLATE_EMERALD_ORE;
                if (h < 38) return Material.BUDDING_AMETHYST;
                if (h < 56) return Material.DEEPSLATE_DIAMOND_ORE;
                if (h < 84) return Material.DEEPSLATE_COAL_ORE;
                return Material.DEEPSLATE_IRON_ORE;
            }
            if (s.n == 5) {
                // endérite (Pioche de cristal), éclats de néant très rares
                if (h < 18) return Material.NETHER_QUARTZ_ORE;
                if (h < 24) return Material.ANCIENT_DEBRIS;
                if (h < 44) return Material.DEEPSLATE_EMERALD_ORE;
                if (h < 74) return Material.DEEPSLATE_COAL_ORE;
                return Material.DEEPSLATE_DIAMOND_ORE;
            }
            if (h < 45) return Material.DEEPSLATE_LAPIS_ORE;
            if (h < 70) return Material.DEEPSLATE_COPPER_ORE;
            if (h < 92) return Material.DEEPSLATE_COAL_ORE;
            return Material.SHROOMLIGHT;
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
            int bx = cx << 4, bz = cz << 4;
            data.setRegion(0, FLOOR, 0, 16, FLOOR + 1, 16, Material.BEDROCK);
            data.setRegion(0, CEIL, 0, 16, CEIL + 3, 16, Material.BEDROCK);
            // points de la galerie principale proches du chunk
            List<double[]> near = new ArrayList<>();
            for (double[] p : s.path)
                if (p[0] > bx - 6 && p[0] < bx + 22 && p[2] > bz - 6 && p[2] < bz + 22) near.add(p);
            boolean campZone = s.nearCamp(bx + 8, bz + 8, 40);
            boolean lairZone = s.nearLair(bx + 8, bz + 8, 46);

            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++) {
                    int x = bx + lx, z = bz + lz;
                    for (int y = FLOOR + 1; y < CEIL; y++) {
                        boolean air = cave(x, y, z) > 0;
                        if (!air && !near.isEmpty()) air = inPath(near, x, y, z);
                        if (campZone) {
                            int c = camp(x, y, z);
                            if (c == 1) air = true;
                            else if (c == 2) {
                                data.setBlock(lx, y, lz, (x + z & 1) == 0 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE);
                                continue;
                            }
                        }
                        if (lairZone) {
                            int c = lair(x, y, z);
                            if (c == 1) air = true;
                            else if (c == 2) {
                                data.setBlock(lx, y, lz, (x + z & 1) == 0 ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE);
                                continue;
                            }
                        }
                        if (air) {
                            // la Mer de Cristal : les cavités basses sont noyées (le camp, l'antre et la galerie sont plus haut)
                            if (s.n == 4 && y < SEA_4) data.setBlock(lx, y, lz, Material.WATER);
                            continue;
                        }
                        Material base = rockAt(x, y, z);
                        Material o = oreAt(x, y, z, base);
                        data.setBlock(lx, y, lz, o != null ? o : base);
                    }
                }
            decorate(data, cx, cz, near);
            if (campZone) campStructure(data, bx, bz);
            if (lairZone) lairStructure(data, bx, bz);
        }

        /** Un point de la galerie principale à moins de r blocs (à l'horizontale) ? */
    private static boolean nearPath(List<double[]> near, int x, int z, double r) {
        for (double[] p : near) {
            double dx = x + 0.5 - p[0], dz = z + 0.5 - p[2];
            if (dx * dx + dz * dz < r * r) return true;
        }
        return false;
    }

    private static boolean inPath(List<double[]> near, int x, int y, int z) {

            for (double[] p : near) {
                double dx = x + 0.5 - p[0], dy = (y + 0.5 - p[1]) * 1.25, dz = z + 0.5 - p[2];
                if (dx * dx + dy * dy + dz * dz < 10.5) return true;
            }
            return false;
        }

        /** Camp : 1 = air, 2 = sol, 0 = rien. */
        private static int camp(int x, int y, int z) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 11) return 0;
            if (y == CAMP_Y && d <= 10) return 2;
            if (y > CAMP_Y && y <= CAMP_Y + 1 + Math.sqrt(Math.max(0, 121 - d * d)) * 0.8) return 1;
            return 0;
        }

        private int lair(int x, int y, int z) {
            double dx = x - s.lairX, dz = z - s.lairZ;
            double r2 = (dx * dx + dz * dz) / (LAIR_R * LAIR_R);
            if (y == LAIR_FLOOR && r2 < 0.95) return 2;
            if (y > LAIR_FLOOR) {
                double dy = (y - LAIR_FLOOR) / 15.0;
                if (r2 + dy * dy < 1) return 1;
            }
            return 0;
        }

        /** Sol, plafond, filons, campements, champignons géants, lanternes de la galerie. */
        private void decorate(ChunkData data, int cx, int cz, List<double[]> near) {
            int bx = cx << 4, bz = cz << 4;
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++) {
                    int x = bx + lx, z = bz + lz;
                    if (s.nearCamp(x, z, 12) || s.nearLair(x, z, LAIR_R + 1)) continue;
                    for (int y = FLOOR + 2; y < CEIL - 1; y++) {
                        Material here = data.getType(lx, y, lz);
                        if (!air(here)) continue;
                        Material below = data.getType(lx, y - 1, lz);
                        Material above = data.getType(lx, y + 1, lz);
                        int h = hash3(x, y, z) % 1000;
                        if (isRock(below)) {
                            if (s.n == 4) {
                                if (h < 30) data.setBlock(lx, y, lz, state("minecraft:amethyst_cluster[facing=up]"));
                                else if (h < 60) data.setBlock(lx, y, lz, state("minecraft:small_amethyst_bud[facing=up]"));
                                else if (h < 140) data.setBlock(lx, y - 1, lz, Material.CALCITE);
                            } else if (s.n == 5) {
                                if (h < 14) data.setBlock(lx, y, lz, state("minecraft:end_rod[facing=up]"));
                                else if (h < 90) data.setBlock(lx, y - 1, lz, Material.END_STONE_BRICKS);
                            } else if (s.n == 3) {
                                if (h < 30) data.setBlock(lx, y - 1, lz, Material.MAGMA_BLOCK);
                                else if (h < 110) data.setBlock(lx, y - 1, lz, Material.SMOOTH_BASALT);
                                else if (h < 118 && y < 30) data.setBlock(lx, y - 1, lz, Material.LAVA);
                            } else if (s.n == 1) {
                                if (h < 60) {
                                    data.setBlock(lx, y - 1, lz, Material.MOSS_BLOCK);
                                    if (h < 25) data.setBlock(lx, y, lz, Material.MOSS_CARPET);
                                } else if (h < 85) data.setBlock(lx, y - 1, lz, Material.GRAVEL);
                            } else {
                                data.setBlock(lx, y - 1, lz, Material.MYCELIUM);
                                if (h < 25) data.setBlock(lx, y, lz, Material.RED_MUSHROOM);
                                else if (h < 50) data.setBlock(lx, y, lz, Material.BROWN_MUSHROOM);
                            }
                        } else if (isRock(above)) {
                            if (s.n == 1 && h < 12) data.setBlock(lx, y, lz, Material.COBWEB);
                            else if (s.n == 3 && h < 6) data.setBlock(lx, y, lz, Material.POINTED_DRIPSTONE);
                            else if (s.n == 4 && h < 25) data.setBlock(lx, y, lz, state("minecraft:amethyst_cluster[facing=down]"));
                            else if (s.n == 5 && h < 10) data.setBlock(lx, y, lz, state("minecraft:end_rod[facing=down]"));
                            else if (h > 985) data.setBlock(lx, y, lz, state("minecraft:glow_lichen[up=true]"));
                        }
                    }
                }
            int h = hash(cx, cz, s.n * 13 + 5);
            double mx = bx + 8, mz = bz + 8;
            boolean free = !s.nearCamp(mx, mz, 30) && !s.nearLair(mx, mz, 40);
            // filon
            if (free && h % 5 == 1) {
                int lx = 3 + h % 10, lz = 3 + (h / 10) % 10;
                int y = floorY(data, lx, lz, 3);
                if (y > 0) {
                    Material f = s.filons[(h / 100) % s.filons.length];
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (!air(data.getType(lx + dx, y - 1, lz + dz))) data.setBlock(lx + dx, y - 1, lz + dz, f);
                    data.setBlock(lx, y, lz, f);
                }
            }
            // campement abandonné
            if (campChunk(s, cx, cz)) {
                int y = floorY(data, 8, 8, 3);
                if (y > 0) {
                    data.setBlock(8, y, 8, state("minecraft:barrel[facing=up]"));
                    data.setBlock(9, y, 8, state("minecraft:campfire[lit=false]"));
                    data.setBlock(7, y - 1, 8, Material.OAK_PLANKS);
                    data.setBlock(8, y - 1, 9, Material.OAK_PLANKS);
                    if (air(data.getType(8, y, 7))) data.setBlock(8, y, 7, Material.LANTERN);
                }
            }
            // fond de la Mer de Cristal : cornichons de mer lumineux
            if (s.n == 4) seaFloor(data, bx, bz);
            // flèches de cristal (strate 4) et piliers d'obsidienne (strate 5)
            if ((s.n == 4 || s.n == 5) && free && h % 4 == 2) {
                int lx = 3 + h % 10, lz = 3 + (h / 16) % 10;
                int y = floorY(data, lx, lz, 9);
                if (y > 0 && !nearPath(near, bx + lx, bz + lz, 5)) spire(data, lx, y, lz, 5 + (h / 7) % 7);
            }
            // champignon géant (Champignonnière)
            if (s.n == 2 && free && h % 3 == 0) {
                int lx = 4 + h % 8, lz = 4 + (h / 8) % 8;
                int y = floorY(data, lx, lz, 11);
                if (y > 0) giantMushroom(data, lx, y, lz, (h / 64) % 2 == 0);
            }
            // lanternes de la galerie principale
            for (int i = 0; i < s.path.size(); i += 12) {
                double[] p = s.path.get(i);
                int px = (int) Math.floor(p[0]), pz = (int) Math.floor(p[2]);
                if (px >> 4 != cx || pz >> 4 != cz) continue;
                int lx = px & 15, lz = pz & 15;
                for (int y = (int) p[1] + 4; y > (int) p[1] - 4; y--) {
                    if (air(data.getType(lx, y, lz)) && isSolid(data.getType(lx, y + 1, lz))) {
                        data.setBlock(lx, y, lz, state("minecraft:lantern[hanging=true]"));
                        break;
                    }
                }
            }
        }

        /** Flèche de calcite coiffée d'une grappe d'améthyste (strate 4), pilier d'obsidienne à bâton de l'End (strate 5). */
    private void spire(ChunkData data, int lx, int y, int lz, int hgt) {
        Material core = s.n == 4 ? Material.CALCITE : Material.OBSIDIAN;
        for (int i = 0; i < hgt; i++) {
            if (!air(data.getType(lx, y + i, lz))) return;
            data.setBlock(lx, y + i, lz, core);
            if (i < hgt / 3)
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    if (air(data.getType(lx + d[0], y + i, lz + d[1]))) data.setBlock(lx + d[0], y + i, lz + d[1], core);
        }
        if (air(data.getType(lx, y + hgt, lz)))
            data.setBlock(lx, y + hgt, lz, state(s.n == 4 ? "minecraft:amethyst_cluster[facing=up]" : "minecraft:end_rod[facing=up]"));
    }

    /** Sol noyé de la strate 4 : quelques cornichons de mer (lumière sous l'eau). */
    private void seaFloor(ChunkData data, int bx, int bz) {
        for (int i = 0; i < 6; i++) {
            int hh = hash(bx + i * 7, bz - i * 3, 404);
            int lx = hh % 14 + 1, lz = (hh / 14) % 14 + 1;
            for (int y = SEA_4 - 1; y > FLOOR + 2; y--) {
                if (data.getType(lx, y, lz) != Material.WATER) continue;
                if (isRock(data.getType(lx, y - 1, lz))) {
                    data.setBlock(lx, y, lz, state("minecraft:sea_pickle[pickles=" + (1 + hh % 4) + ",waterlogged=true]"));
                    break;
                }
            }
        }
    }

    private void giantMushroom(ChunkData data, int lx, int y, int lz, boolean red) {
            int hgt = 5 + (lx + lz) % 4;
            for (int i = 0; i < hgt; i++) data.setBlock(lx, y + i, lz, Material.MUSHROOM_STEM);
            Material cap = red ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK;
            int top = y + hgt;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > 10) continue;
                    if (d2 <= 5) data.setBlock(lx + dx, top, lz + dz, cap);
                    else data.setBlock(lx + dx, top - 1, lz + dz, cap);
                }
            data.setBlock(lx + 1, top - 1, lz, Material.SHROOMLIGHT);
            data.setBlock(lx - 1, top - 1, lz - 1, Material.SHROOMLIGHT);
        }

        /** Premier sol (air au-dessus d'un bloc plein, avec {@code room} blocs d'air) en descendant, ou -1. */
        private static int floorY(ChunkData data, int lx, int lz, int room) {
            for (int y = CEIL - room - 1; y > FLOOR + 2; y--) {
                if (!isSolid(data.getType(lx, y - 1, lz))) continue;
                boolean ok = true;
                for (int i = 0; i < room; i++)
                    if (!air(data.getType(lx, y + i, lz))) {
                        ok = false;
                        break;
                    }
                if (ok) return y;
            }
            return -1;
        }

        /** Camp d'arrivée : cloche du monte-charge, lanternes, poteaux. */
        private void campStructure(ChunkData data, int bx, int bz) {
            int y = CAMP_Y + 1;
            put(data, bx, bz, 0, y - 1, -3, Material.CHISELED_STONE_BRICKS);
            put(data, bx, bz, 0, y, -3, state("minecraft:bell[attachment=floor,facing=south]"));
            for (int[] c : new int[][]{{-2, -3}, {2, -3}}) {
                for (int i = 0; i < 4; i++) put(data, bx, bz, c[0], y + i, c[1], Material.DARK_OAK_LOG);
                put(data, bx, bz, c[0], y + 4, c[1], Material.LANTERN);
            }
            for (int[] c : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}, {0, 9}, {-9, 0}, {9, 0}}) {
                put(data, bx, bz, c[0], y, c[1], Material.DARK_OAK_FENCE);
                put(data, bx, bz, c[0], y + 1, c[1], Material.LANTERN);
            }
        }

        /** Antre du gardien : autel au centre, piliers et lanternes des âmes. */
        private void lairStructure(ChunkData data, int bx, int bz) {
            int x0 = (int) s.lairX, z0 = (int) s.lairZ, y = LAIR_FLOOR + 1;
            put(data, bx, bz, x0, y, z0, Material.REINFORCED_DEEPSLATE);
            put(data, bx, bz, x0, y + 1, z0, Material.SOUL_LANTERN);
            for (int[] c : new int[][]{{9, 9}, {-9, 9}, {9, -9}, {-9, -9}}) {
                for (int i = 0; i < 7; i++) put(data, bx, bz, x0 + c[0], y + i, z0 + c[1], Material.POLISHED_BASALT);
                put(data, bx, bz, x0 + c[0], y + 7, z0 + c[1], Material.SOUL_LANTERN);
            }
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                put(data, bx, bz, x0 + (int) Math.round(Math.cos(a) * 16), y, z0 + (int) Math.round(Math.sin(a) * 16), Material.SOUL_LANTERN);
            }
        }

        private static void put(ChunkData data, int bx, int bz, int x, int y, int z, Material m) {
            if (x >= bx && x < bx + 16 && z >= bz && z < bz + 16) data.setBlock(x - bx, y, z - bz, m);
        }

        private static void put(ChunkData data, int bx, int bz, int x, int y, int z, BlockData b) {
            if (x >= bx && x < bx + 16 && z >= bz && z < bz + 16) data.setBlock(x - bx, y, z - bz, b);
        }

        /** Sans Material.isSolid()/isAir() : ils passent par le registre (lent, et absent hors serveur). */
        private static boolean isSolid(Material m) {
            return !air(m) && !SOFT.contains(m);
        }

        private boolean isRock(Material m) {
            return m == Material.STONE || m == Material.DEEPSLATE || m == Material.TUFF || m == Material.BLACKSTONE || m == Material.BASALT
                    || m == Material.CALCITE || m == Material.END_STONE || m == Material.OBSIDIAN;
        }
    }

    static boolean air(Material m) {
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    private static final java.util.Set<Material> SOFT = java.util.EnumSet.of(Material.MOSS_CARPET, Material.COBWEB,
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.LANTERN, Material.SOUL_LANTERN, Material.CAMPFIRE,
            Material.GLOW_LICHEN, Material.BELL, Material.DARK_OAK_FENCE, Material.BARRIER, Material.POINTED_DRIPSTONE, Material.LAVA,
            Material.WATER, Material.AMETHYST_CLUSTER, Material.SMALL_AMETHYST_BUD, Material.END_ROD, Material.SEA_PICKLE);

    private static final java.util.concurrent.ConcurrentHashMap<String, BlockData> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    static BlockData state(String s) {
        return STATES.computeIfAbsent(s, Bukkit::createBlockData);
    }

    // ------------------------------------------------------------------ mondes vides

    static final class VoidGen extends ChunkGenerator {
        private final Location spawn;

        VoidGen(double x, double y, double z) {
            spawn = new Location(null, x, y, z);
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
            return new OneBiome(Biome.PLAINS);
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            Location l = spawn.clone();
            l.setWorld(world);
            return l;
        }
    }

    static final class OneBiome extends BiomeProvider {
        private final Biome b;

        OneBiome(Biome b) {
            this.b = b;
        }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return b;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(b);
        }
    }

    private Gen() {
    }
}
