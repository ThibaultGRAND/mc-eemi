package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tonneaux des campements abandonnés, personnels : chaque joueur fouille SA copie de chaque tonneau (butin tiré à sa
 * première ouverture, page de carnet comprise), et ce qu'il y laisse reste dans sa copie. Plusieurs joueurs peuvent
 * fouiller le même tonneau en même temps ; le vrai tonneau reste vide (rien à voler ni à dupliquer en le cassant,
 * et il est incassable).
 * <p>
 * État par joueur dans {@code campements/<uuid>.yml}, par strate et par « époque » : l'époque est un nombre posé
 * dans les données du monde de la strate à sa création ; une remise à neuf supprime le monde, donc la nouvelle
 * strate a une autre époque et les copies de l'ancienne sont oubliées (les tonneaux sont pleins à nouveau). Les
 * pages lues (journal) ne sont jamais redonnées. Le fichier ne garde que les tonneaux ouverts dans la strate
 * actuelle : il ne grossit pas sans fin.
 * <p>
 * Pages garanties : un tonneau « à page » (réglage {@code carnets.chance}) donne toujours une page encore inconnue
 * du joueur (la sienne si possible) ; après {@code carnets.garantie} tonneaux ouverts sans page, le suivant en donne
 * une. Pendant une quête de pages, la barre d'action montre le campement le plus proche encore à fouiller.
 */
final class Campements implements Listener {

    private final EemiAbysse plugin;
    /** Hauteur du tonneau du campement, posée dans les données du chunk à sa génération. */
    private final NamespacedKey keyY;
    /** Époque de la strate (données du monde). */
    private final NamespacedKey keyEpoch;
    private final Map<UUID, Camps> cache = new HashMap<>();

    /** Copie personnelle d'un tonneau. */
    static final class State {
        /** Page réservée à ce joueur dans ce tonneau (« strate:numéro »), ou null. */
        String page;
        ItemStack[] items;
    }

    /** Tonneaux d'une strate pour un joueur, valables pour une époque. */
    static final class StrataCamps {
        long epoch;
        final Map<String, State> barrels = new HashMap<>();
    }

    static final class Camps {
        /** Tonneaux ouverts sans page depuis la dernière page donnée. */
        int dry;
        final Map<Integer, StrataCamps> strata = new HashMap<>();
    }

    /** Inventaire d'une fouille (copie personnelle). */
    static final class Fouille implements InventoryHolder {
        final UUID player;
        final int strata;
        final long epoch;
        final String key;
        final String ref;
        Inventory inv;

        Fouille(UUID player, int strata, long epoch, String key, String ref) {
            this.player = player;
            this.strata = strata;
            this.epoch = epoch;
            this.key = key;
            this.ref = ref;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    Campements(EemiAbysse plugin) {
        this.plugin = plugin;
        keyY = new NamespacedKey(plugin, "camp_barrel_y");
        keyEpoch = new NamespacedKey(plugin, "camp_epoch");
    }

    // ------------------------------------------------------------------ repérage

    /** Le tonneau d'un campement abandonné (et pas un tonneau posé par un joueur) ? */
    boolean isCamp(Block b) {
        if (b.getType() != Material.BARREL) return false;
        Gen.Strata s = Gen.strata(b.getWorld());
        if (s == null || (b.getX() & 15) != 8 || (b.getZ() & 15) != 8 || !Gen.campChunk(s, b.getX() >> 4, b.getZ() >> 4)) return false;
        Integer y = b.getChunk().getPersistentDataContainer().get(keyY, PersistentDataType.INTEGER);
        // chunk généré avant ce marquage : le premier tonneau de la colonne ouvert est adopté comme celui du campement
        if (y == null) {
            b.getChunk().getPersistentDataContainer().set(keyY, PersistentDataType.INTEGER, b.getY());
            return true;
        }
        return y == b.getY();
    }

    /** Époque de la strate : posée à la première demande, perdue (donc renouvelée) quand la strate est remise à neuf. */
    long epoch(World w) {
        Long e = w.getPersistentDataContainer().get(keyEpoch, PersistentDataType.LONG);
        if (e == null) {
            e = System.currentTimeMillis();
            w.getPersistentDataContainer().set(keyEpoch, PersistentDataType.LONG, e);
        }
        return e;
    }

    /** Cette strate a-t-elle des pages de carnet (texte écrit) ? */
    static boolean hasPages(int strata) {
        return !"...".equals(Quests.page(strata, 1));
    }

    /** Le campement de ce chunk porte-t-il une page (d'après sa position) ? 0 = non, sinon son numéro. */
    private int campPage(int bx, int bz) {
        Random r = new Random(Gen.hash(bx, bz, 78));
        if (r.nextInt(100) >= plugin.getConfig().getInt("carnets.chance", 40)) return 0;
        return 1 + r.nextInt(Quests.PAGES);
    }

    private static String key(Block b) {
        return b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    /** Nouveau chunk d'une strate : on note où est le tonneau du campement (le vrai tonneau reste vide). */
    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        Gen.Strata s = Gen.strata(e.getWorld());
        if (s == null || !Gen.campChunk(s, e.getChunk().getX(), e.getChunk().getZ())) return;
        int bx = e.getChunk().getX() << 4, bz = e.getChunk().getZ() << 4;
        for (int y = Gen.CEIL - 1; y > Gen.FLOOR; y--) {
            if (e.getWorld().getBlockAt(bx + 8, y, bz + 8).getType() != Material.BARREL) continue;
            e.getChunk().getPersistentDataContainer().set(keyY, PersistentDataType.INTEGER, y);
            break;
        }
    }

    // ------------------------------------------------------------------ fouille

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder(false) instanceof Barrel barrel)) return;
        Block b = barrel.getBlock();
        if (!isCamp(b)) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getWorld() != b.getWorld() || b.getType() != Material.BARREL) return;
            open(p, b);
        });
    }

    private void open(Player p, Block b) {
        Gen.Strata s = Gen.strata(b.getWorld());
        if (s == null) return;
        // ancien contenu partagé (versions précédentes) : retiré, chacun a maintenant sa copie
        if (b.getState(false) instanceof Barrel real && !real.getInventory().isEmpty()) real.getInventory().clear();
        long epoch = epoch(b.getWorld());
        Camps c = camps(p.getUniqueId());
        StrataCamps sc = strata(c, s.n, epoch);
        String key = key(b);
        PlayerData d = plugin.store.player(p);
        State st = sc.barrels.get(key);
        boolean first = st == null;
        if (first) {
            st = new State();
            Inventory tmp = Bukkit.createInventory(null, 27);
            plugin.fillBarrel(s, tmp, Gen.hash(b.getX() & ~15, b.getZ() & ~15, 77) ^ (int) (epoch & 0x7fffffff));
            st.items = tmp.getContents();
            st.page = choosePage(p, d, c, sc, s.n, b);
            sc.barrels.put(key, st);
            save(p.getUniqueId());
        }
        Fouille f = new Fouille(p.getUniqueId(), s.n, epoch, key, st.page);
        f.inv = Bukkit.createInventory(f, 27, Txt.c("§8Campement abandonné"));
        f.inv.setContents(st.items);
        if (st.page != null && !d.pages.contains(st.page) && !carried(p, st.page)) {
            ItemStack page = pageItem(st.page);
            int slot = f.inv.firstEmpty();
            if (slot >= 0) f.inv.setItem(slot, page);
            else for (ItemStack rest : p.getInventory().addItem(page).values()) p.getWorld().dropItem(p.getLocation(), rest);
            if (first) p.sendActionBar(Txt.c("§fUne page des carnets de Varenne est glissée au fond du tonneau..."));
        }
        p.openInventory(f.inv);
        p.playSound(p, Sound.BLOCK_BARREL_OPEN, 0.7f, 1f);
    }

    /**
     * Page donnée à la première ouverture d'un tonneau : une page de la strate que le joueur n'a ni lue, ni sur lui,
     * ni en attente dans un autre tonneau ; si le campement porte une page, ou après {@code carnets.garantie}
     * tonneaux sans page.
     */
    private String choosePage(Player p, PlayerData d, Camps c, StrataCamps sc, int strata, Block b) {
        List<Integer> free = new ArrayList<>();
        if (hasPages(strata))
            for (int i = 1; i <= Quests.PAGES; i++) {
                String ref = strata + ":" + i;
                if (d.pages.contains(ref) || carried(p, ref) || pending(sc, ref)) continue;
                free.add(i);
            }
        if (free.isEmpty()) return null;
        int own = campPage(b.getX() & ~15, b.getZ() & ~15);
        if (own == 0 && c.dry < plugin.getConfig().getInt("carnets.garantie", 3)) {
            c.dry++;
            return null;
        }
        c.dry = 0;
        int n = free.contains(own) ? own : free.get(Gen.hash(b.getX(), b.getZ(), 79) % free.size());
        return strata + ":" + n;
    }

    /** Page réservée dans un tonneau de cette strate et pas encore prise. */
    private static boolean pending(StrataCamps sc, String ref) {
        for (State st : sc.barrels.values()) if (ref.equals(st.page)) return true;
        return false;
    }

    static boolean carried(Player p, String ref) {
        for (ItemStack it : p.getInventory().getContents()) if (ref.equals(Items.pageRef(it))) return true;
        return ref.equals(Items.pageRef(p.getItemOnCursor()));
    }

    private static ItemStack pageItem(String ref) {
        String[] r = ref.split(":");
        int s = Integer.parseInt(r[0]), n = Integer.parseInt(r[1]);
        return Items.page(s, n, Quests.pageTitle(s, n));
    }

    /** Fin de la fouille : la copie du joueur garde ce qu'il a laissé (sans sa page, reposée à chaque ouverture). */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Fouille f)) return;
        Camps c = camps(f.player);
        StrataCamps sc = c.strata.get(f.strata);
        if (sc == null || sc.epoch != f.epoch) return; // strate remise à neuf entre-temps
        State st = sc.barrels.get(f.key);
        if (st == null) return;
        ItemStack[] items = f.inv.getContents();
        boolean taken = true;
        for (int i = 0; i < items.length; i++)
            if (f.ref != null && f.ref.equals(Items.pageRef(items[i]))) {
                items[i] = null;
                taken = false;
            }
        st.items = items;
        // page prise : la réservation est levée (elle ne bloque plus le tirage des autres tonneaux)
        if (taken && f.ref != null && e.getPlayer() instanceof Player p && (carried(p, f.ref) || plugin.store.player(p).pages.contains(f.ref)))
            st.page = null;
        save(f.player);
    }

    /** Le vrai tonneau d'un campement ne se casse pas (seul un admin le peut). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!isCamp(e.getBlock()) || plugin.admin(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Txt.c("§7Ce tonneau de campement ne se casse pas : ouvre-le, son contenu est à toi."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(id) != null) return;
            save(id);
            cache.remove(id);
        }, 20L);
    }

    /** Fouilles ouvertes fermées (le contenu est sauvé), à l'arrêt du plugin. */
    void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Fouille) p.closeInventory();
        for (UUID id : cache.keySet()) save(id);
    }

    // ------------------------------------------------------------------ indice

    /**
     * Quête de pages en cours, dans une strate : toutes les 2 s (en alternance avec l'indice du gardien), le
     * campement le plus proche encore à fouiller (ou dont la page attend le joueur) dans la barre d'action.
     */
    void hint(Player p) {
        if (p.getTicksLived() / 20 % 2 != 1 || !plugin.getConfig().getBoolean("carnets.indice", true)) return;
        PlayerData d = plugin.store.player(p);
        Quests.Quest q = plugin.quests.current(d);
        if (q == null || q.obj() != Quests.Obj.PAGES) return;
        Gen.Strata s = Gen.strata(p.getWorld());
        if (s == null) return;
        boolean unread = false;
        if (hasPages(s.n))
            for (int i = 1; i <= Quests.PAGES; i++) if (!d.pages.contains(s.n + ":" + i)) unread = true;
        if (!unread) {
            p.sendActionBar(Txt.c("§7Tu as lu toutes les pages de cette strate : cherche dans une autre."));
            return;
        }
        for (int i = 1; i <= Quests.PAGES; i++)
            if (!d.pages.contains(s.n + ":" + i) && carried(p, s.n + ":" + i)) {
                p.sendActionBar(Txt.c("§fTu as une page de carnet dans ton sac §7: clic droit dessus pour la lire."));
                return;
            }
        StrataCamps sc = strata(camps(p.getUniqueId()), s.n, epoch(p.getWorld()));
        Location l = p.getLocation();
        int pcx = l.getBlockX() >> 4, pcz = l.getBlockZ() >> 4;
        int radius = Math.max(2, plugin.getConfig().getInt("carnets.indice-rayon", 160) >> 4);
        double best = Double.MAX_VALUE;
        int bx = 0, bz = 0;
        boolean waiting = false;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++)
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!Gen.campChunk(s, cx, cz)) continue;
                int x = (cx << 4) + 8, z = (cz << 4) + 8;
                double dist = (x + 0.5 - l.getX()) * (x + 0.5 - l.getX()) + (z + 0.5 - l.getZ()) * (z + 0.5 - l.getZ());
                if (dist >= best) continue;
                State st = opened(sc, x, z);
                boolean wait = st != null && st.page != null && !d.pages.contains(st.page) && !carried(p, st.page);
                if (st != null && !wait) continue; // déjà fouillé
                if (!hasBarrel(p.getWorld(), cx, cz)) continue;
                best = dist;
                bx = x;
                bz = z;
                waiting = wait;
            }
        if (best == Double.MAX_VALUE) {
            p.sendActionBar(Txt.c("§7Aucun campement à fouiller près d'ici : explore plus loin."));
            return;
        }
        double dx = bx + 0.5 - l.getX(), dz = bz + 0.5 - l.getZ();
        int dist = (int) Math.sqrt(best);
        if (dist < 3) return;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((yaw - l.getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[((int) Math.round(rel / 45.0) + 8) % 8];
        p.sendActionBar(Txt.c((waiting ? "§fTa page t'attend au campement " : "§fCampement abandonné ") + "§f§l" + arrow + " §f" + dist
                + " blocs §8- un tonneau près d'un feu de camp éteint"));
    }

    /** Copie déjà ouverte d'un tonneau de campement dans la colonne (x, z). */
    private static State opened(StrataCamps sc, int x, int z) {
        String prefix = x + ";", suffix = ";" + z;
        for (Map.Entry<String, State> e : sc.barrels.entrySet())
            if (e.getKey().startsWith(prefix) && e.getKey().endsWith(suffix)) return e.getValue();
        return null;
    }

    /** Le campement de ce chunk a-t-il bien son tonneau ? (chunk pas chargé : on suppose que oui) */
    private boolean hasBarrel(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return true;
        Chunk ch = w.getChunkAt(cx, cz);
        Integer y = ch.getPersistentDataContainer().get(keyY, PersistentDataType.INTEGER);
        if (y != null) return w.getBlockAt((cx << 4) + 8, y, (cz << 4) + 8).getType() == Material.BARREL;
        for (int yy = Gen.CEIL - 1; yy > Gen.FLOOR; yy--)
            if (w.getBlockAt((cx << 4) + 8, yy, (cz << 4) + 8).getType() == Material.BARREL) return true;
        return false;
    }

    // ------------------------------------------------------------------ données

    private StrataCamps strata(Camps c, int n, long epoch) {
        StrataCamps sc = c.strata.get(n);
        if (sc == null || sc.epoch != epoch) {
            sc = new StrataCamps();
            sc.epoch = epoch;
            c.strata.put(n, sc);
        }
        return sc;
    }

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "campements/" + id + ".yml");
    }

    private Camps camps(UUID id) {
        Camps c = cache.get(id);
        if (c != null) return c;
        c = new Camps();
        File f = file(id);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            c.dry = y.getInt("sans-page");
            ConfigurationSection strata = y.getConfigurationSection("strates");
            if (strata != null)
                for (String n : strata.getKeys(false)) {
                    ConfigurationSection ss = strata.getConfigurationSection(n);
                    if (ss == null) continue;
                    StrataCamps sc = new StrataCamps();
                    sc.epoch = ss.getLong("epoque");
                    ConfigurationSection bs = ss.getConfigurationSection("tonneaux");
                    if (bs != null)
                        for (String k : bs.getKeys(false)) {
                            State st = new State();
                            st.page = bs.getString(k + ".page");
                            try {
                                st.items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(bs.getString(k + ".objets", "")));
                            } catch (Exception ex) {
                                st.items = new ItemStack[27];
                            }
                            sc.barrels.put(k, st);
                        }
                    try {
                        c.strata.put(Integer.parseInt(n), sc);
                    } catch (NumberFormatException ignored) {
                    }
                }
        }
        cache.put(id, c);
        return c;
    }

    private void save(UUID id) {
        Camps c = cache.get(id);
        if (c == null) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("sans-page", c.dry);
        for (Map.Entry<Integer, StrataCamps> e : c.strata.entrySet()) {
            String base = "strates." + e.getKey();
            y.set(base + ".epoque", e.getValue().epoch);
            for (Map.Entry<String, State> b : e.getValue().barrels.entrySet()) {
                String k = base + ".tonneaux." + b.getKey();
                y.set(k + ".page", b.getValue().page);
                ItemStack[] items = b.getValue().items == null ? new ItemStack[0] : b.getValue().items.clone();
                for (int i = 0; i < items.length; i++) if (items[i] == null) items[i] = ItemStack.empty();
                y.set(k + ".objets", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items)));
            }
        }
        try {
            y.save(file(id));
        } catch (IOException ex) {
            plugin.getLogger().warning("Sauvegarde des campements de " + id + " impossible : " + ex.getMessage());
        }
    }
}
