package fr.eemicraft.paintball;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Grande map collee par le plugin dans le monde du paintball, loin du terrain de speedball, en plusieurs ticks.
 * Fichier maps/&lt;id&gt;.pbm (genere hors ligne) : palette + RLE (y, z, x) + points d'apparition des deux equipes.
 * Rouge (equipe 0) est toujours au nord (z petits), Bleu au sud.
 */
final class BigMap {
    /** Blocs que la peinture peut recouvrir sur les grandes maps (blocs pleins des sols, murs et toits). */
    static final Set<Material> PAINTABLE = Set.of(
        Material.LIGHT_GRAY_WOOL, Material.CYAN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.LIME_TERRACOTTA,
        Material.YELLOW_TERRACOTTA, Material.RED_TERRACOTTA, Material.STONE, Material.GRAVEL, Material.COBBLESTONE,
        Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, Material.ANDESITE, Material.POLISHED_DIORITE, Material.STONE_BRICKS,
        Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.BRICKS, Material.OAK_PLANKS, Material.BIRCH_PLANKS,
        Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, Material.COAL_BLOCK, Material.DIRT, Material.COARSE_DIRT,
        Material.ROOTED_DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MOSS_BLOCK, Material.TUFF, Material.BLACKSTONE,
        Material.POLISHED_BLACKSTONE_BRICKS, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.DEEPSLATE_BRICKS,
        Material.RED_NETHER_BRICKS, Material.DEAD_HORN_CORAL_BLOCK, Material.DEAD_FIRE_CORAL_BLOCK, Material.DEAD_TUBE_CORAL_BLOCK,
        Material.DEAD_BUBBLE_CORAL_BLOCK, Material.DEAD_BRAIN_CORAL_BLOCK, Material.BLACK_WOOL, Material.WHITE_WOOL);

    final String id, name, desc;
    /** Coin du fichier dans le monde. */
    final int ox, oy, oz;
    /** Hauteur (relative au fichier) sous laquelle on est tombe, point des spectateurs (relatif). */
    final int fallRel;
    final double sx, sy, sz;
    final float[] yaw;

    int w, h, l;
    BlockData[] pal;
    int[] runLen, runVal;
    final List<List<int[]>> spawns = List.of(new ArrayList<>(), new ArrayList<>());
    boolean loaded;
    boolean pasting;
    /** Collee et a jour : jouable. */
    boolean ready;
    /** Peinte et pas encore remise (sauvegarde dans data.yml). */
    boolean dirty;
    int version;
    int invalid;

    BigMap(String id, String name, String desc, int ox, int oy, int oz, int fallRel, double sx, double sy, double sz, float yawRed, float yawBlue) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.fallRel = fallRel;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.yaw = new float[] {yawRed, yawBlue};
    }

    boolean load(EemiPaintball pl) {
        try (InputStream raw = pl.getResource("maps/" + id + ".pbm")) {
            if (raw == null) {
                pl.getLogger().warning("maps/" + id + ".pbm absent du jar : map " + name + " desactivee.");
                return false;
            }
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw)));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"PBM1".equals(new String(magic, java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IOException("format inconnu");
            }
            w = in.readInt();
            h = in.readInt();
            l = in.readInt();
            int n = in.readInt();
            pal = new BlockData[n];
            invalid = 0;
            for (int i = 0; i < n; i++) {
                String s = in.readUTF();
                BlockData d;
                try {
                    d = Bukkit.createBlockData(s);
                } catch (IllegalArgumentException e) {
                    // etat inconnu en 26.3 : on garde le bloc sans ses proprietes, sinon de l'air
                    String base = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
                    try {
                        d = Bukkit.createBlockData(base);
                    } catch (IllegalArgumentException e2) {
                        d = Bukkit.createBlockData(Material.AIR);
                        pl.getLogger().warning("Map " + name + " : bloc inconnu ignore " + s);
                    }
                    invalid++;
                }
                // feuilles collees sans physique : persistantes, sinon elles pourrissent avec les ticks aleatoires
                if (d instanceof org.bukkit.block.data.type.Leaves lv) {
                    lv.setPersistent(true);
                }
                pal[i] = d;
            }
            long total = (long) w * h * l;
            List<int[]> runs = new ArrayList<>();
            long done = 0;
            while (done < total) {
                int len = varint(in);
                int val = varint(in);
                runs.add(new int[] {len, val});
                done += len;
            }
            runLen = new int[runs.size()];
            runVal = new int[runs.size()];
            for (int i = 0; i < runs.size(); i++) {
                runLen[i] = runs.get(i)[0];
                runVal[i] = runs.get(i)[1];
            }
            for (int t = 0; t < 2; t++) {
                int c = in.readInt();
                for (int i = 0; i < c; i++) {
                    spawns.get(t).add(new int[] {in.readInt(), in.readInt(), in.readInt()});
                }
            }
            loaded = !spawns.get(0).isEmpty() && !spawns.get(1).isEmpty();
            return loaded;
        } catch (IOException e) {
            pl.getLogger().warning("Map " + name + " illisible : " + e.getMessage());
            return false;
        }
    }

    private static int varint(DataInputStream in) throws IOException {
        int v = 0, s = 0;
        while (true) {
            int b = in.readUnsignedByte();
            v |= (b & 0x7F) << s;
            if ((b & 0x80) == 0) {
                return v;
            }
            s += 7;
        }
    }

    /** Colle la map (blocs non vides seulement) sur plusieurs ticks, puis appelle done (true = reussi). */
    void paste(EemiPaintball pl, World world, java.util.function.Consumer<Boolean> done) {
        if (!loaded || pasting || pal == null) {
            done.accept(false);
            return;
        }
        pasting = true;
        final long t0 = System.currentTimeMillis();
        new BukkitRunnable() {
            int run = 0;
            int off = 0;
            long cell = 0;
            long ops = 0;

            @Override
            public void run() {
                long end = System.nanoTime() + 30_000_000L;
                try {
                    while (run < runLen.length) {
                        BlockData d = pal[runVal[run]];
                        int len = runLen[run];
                        if (d.getMaterial() == Material.AIR) {
                            cell += len - off;
                            off = 0;
                            run++;
                            continue;
                        }
                        while (off < len) {
                            long c = cell + off;
                            int x = (int) (c % w);
                            int z = (int) ((c / w) % l);
                            int y = (int) (c / ((long) w * l));
                            world.getBlockAt(ox + x, oy + y, oz + z).setBlockData(d, false);
                            ops++;
                            off++;
                            if ((ops & 63) == 0 && System.nanoTime() > end) {
                                return;
                            }
                        }
                        cell += len;
                        off = 0;
                        run++;
                    }
                } catch (RuntimeException e) {
                    pl.getLogger().warning("Map " + name + " : erreur pendant le collage : " + e);
                    pasting = false;
                    cancel();
                    done.accept(false);
                    return;
                }
                cancel();
                pasting = false;
                pl.getLogger().info("Grande map " + name + " collee : " + ops + " blocs en " + (System.currentTimeMillis() - t0) / 1000 + " s"
                    + (invalid > 0 ? " (" + invalid + " etats de bloc adaptes)" : "") + ".");
                done.accept(true);
            }
        }.runTaskTimer(pl, 1L, 1L);
    }

    /** Libere la palette et le RLE apres le collage (les points d'apparition et la taille restent). */
    void freeData() {
        pal = null;
        runLen = null;
        runVal = null;
    }

    /** Zone peignable de la map. */
    boolean inside(int x, int y, int z) {
        return loaded && x >= ox && x < ox + w && z >= oz && z < oz + l && y >= oy && y < oy + h;
    }

    /** Zone de la map au sens large (pour le seuil de chute). */
    boolean near(double x, double z) {
        return loaded && x >= ox - 64 && x < ox + w + 64 && z >= oz - 64 && z < oz + l + 64;
    }

    double fallY() {
        return oy + fallRel;
    }

    Location stand(World world) {
        return new Location(world, ox + sx, oy + sy, oz + sz, 0f, 60f);
    }

    Location spawn(World world, int team, java.util.Random rnd) {
        List<int[]> list = spawns.get(team);
        int[] s = list.get(rnd.nextInt(list.size()));
        return new Location(world, ox + s[0] + 0.5, oy + s[1], oz + s[2] + 0.5, yaw[team], 0f);
    }

    void force(World world, boolean on) {
        if (world == null || !loaded) {
            return;
        }
        for (int cx = ox >> 4; cx <= (ox + w) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + l) >> 4; cz++) {
                world.setChunkForceLoaded(cx, cz, on);
            }
        }
    }
}
