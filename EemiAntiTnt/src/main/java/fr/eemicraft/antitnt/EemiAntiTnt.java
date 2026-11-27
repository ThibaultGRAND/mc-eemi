package fr.eemicraft.antitnt;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti machines volantes à TNT sur les îles OneBlock / SkyBlock.
 * - Une machine à pistons (piston, piston collant ou observeur déplacé par slime/miel) qui transporte de la TNT,
 *   ou une TNT allumée sans joueur à côté d'une telle machine, est FIGÉE (pistons annulés) :
 *   les membres de l'île ont N secondes pour la démonter.
 * - Sinon la machine est retirée et l'île du propriétaire prend une pluie de TNT, après une sauvegarde complète
 *   (blocs, contenus, entités) ; l'île est ensuite remise à l'identique (voir Punition).
 * - Filet : aucun assemblage poussé par pistons ne peut franchir la limite d'une île.
 */
public final class EemiAntiTnt extends JavaPlugin implements Listener {

    static final String PREFIXE = "§8[§cAnti-TNT§8] §7";

    static final Set<Material> MOTEUR = EnumSet.of(Material.PISTON, Material.STICKY_PISTON, Material.OBSERVER);
    static final Set<Material> MACHINE = EnumSet.of(Material.TNT, Material.SLIME_BLOCK, Material.HONEY_BLOCK,
            Material.PISTON, Material.STICKY_PISTON, Material.PISTON_HEAD, Material.MOVING_PISTON, Material.OBSERVER);

    NamespacedKey keyPluie, keyFige;
    /** Vrai pendant que Punition recrée les entités : on ne bloque pas leurs apparitions. */
    boolean respawnEnCours;
    Iles iles;
    private final Set<String> mondes = new HashSet<>();

    /** Machine détectée, figée en attendant d'être démontée. */
    private static final class Incident {
        Iles.Ile ile;
        Location centre;
        long limite;
        String raison;
        List<Block> graines = new ArrayList<>(); // blocs de la machine vus dans l'événement de piston
    }

    /** Zone d'une punition encore sur disque mais pas chargée (monde pas prêt) : on y bloque quand même les joueurs. */
    private record ZoneAttente(File dir, String monde, int minX, int maxX, int minZ, int maxZ) {
        boolean contains(Location l) {
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(monde)) return false;
            return l.getBlockX() >= minX && l.getBlockX() < maxX && l.getBlockZ() >= minZ && l.getBlockZ() < maxZ;
        }
    }

    private final List<ZoneAttente> zonesAttente = new ArrayList<>();
    private final Map<UUID, GameMode> bloquesAttente = new HashMap<>();
    private final Map<Long, Integer> tickets = new HashMap<>();

    private record Moteur(World w, int x, int y, int z, long t) {
    }

    private final List<Incident> incidents = new ArrayList<>();
    private final ArrayDeque<Moteur> moteurs = new ArrayDeque<>();
    final Map<String, Punition> punitions = new LinkedHashMap<>();
    private final Map<String, Long> dernierAvisLimite = new HashMap<>();
    private File dossierPunitions, dossierArchives, fichierRetours, fichierRecidives;

    // ================================================================== cycle de vie

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyPluie = new NamespacedKey(this, "pluie");
        keyFige = new NamespacedKey(this, "fige");
        iles = new Iles(this);
        dossierPunitions = new File(getDataFolder(), "punitions");
        dossierArchives = new File(getDataFolder(), "archives");
        fichierRetours = new File(getDataFolder(), "retours.yml");
        fichierRecidives = new File(getDataFolder(), "recidives.yml");
        lireConfig();
        lireZonesAttente();
        Bukkit.getPluginManager().registerEvents(this, this);
        // BentoBox crée ses mondes après les plugins : branchement et reprise des punitions un peu plus tard
        final int[] essais = {0};
        Bukkit.getScheduler().runTaskTimer(this, t -> {
            essais[0]++;
            if (iles.hook() || essais[0] >= 12) {
                t.cancel();
                if (!iles.ok) getLogger().severe("BentoBox non branche : " + iles.erreur + " (detection des iles inactive)");
                reprendrePunitions();
                purgerArchives();
            }
        }, 100L, 100L);
        // une punition illisible (monde pas encore chargé) est réessayée chaque minute
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (iles.ok && !zonesAttente.isEmpty()) reprendrePunitions();
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::seconde, 20L, 20L);
    }

    @Override
    public void onDisable() {
        // les punitions en cours restent sur disque : elles seront restaurées au prochain démarrage
        for (Punition p : punitions.values()) {
            p.save();
            p.libererChunks();
        }
    }

    private void lireConfig() {
        reloadConfig();
        mondes.clear();
        mondes.addAll(getConfig().getStringList("mondes"));
    }

    private boolean surveille(World w) {
        return w != null && mondes.contains(w.getName());
    }

    // ================================================================== détection

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExtend(BlockPistonExtendEvent e) {
        if (piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRetract(BlockPistonRetractEvent e) {
        if (piston(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    /** @return true pour annuler le mouvement. */
    private boolean piston(Block piston, List<Block> blocs, BlockFace dir) {
        World w = piston.getWorld();
        if (!surveille(w) || !iles.ok) return false;
        Location pl = piston.getLocation();
        int gel = getConfig().getInt("rayon-gel", 10);
        for (Incident i : incidents)
            if (i.centre.getWorld() == w && i.centre.distanceSquared(pl) <= (double) gel * gel) return true;
        for (Punition p : punitions.values()) if (p.contains(pl)) return true;
        if (blocs.isEmpty()) return false;

        boolean moteur = false, tnt = false, colle = false;
        for (Block b : blocs) {
            Material m = b.getType();
            if (MOTEUR.contains(m)) moteur = true;
            if (m == Material.TNT) tnt = true;
            if (m == Material.SLIME_BLOCK || m == Material.HONEY_BLOCK) colle = true;
        }
        Iles.Ile ile = iles.at(pl);
        if (ile == null && iles.derniereErreur) return false; // BentoBox en erreur : on ne décide rien

        // filet : rien ne sort de son île
        {
            if (ile == null) {
                if (colle || moteur) {
                    avisLimite(null, pl);
                    return true;
                }
            } else {
                for (Block b : blocs) {
                    if (!ile.contains(w, b.getX(), b.getZ())) return refuserLimite(ile, pl);
                    Block d1 = b.getRelative(dir);
                    if (!ile.contains(w, d1.getX(), d1.getZ())) return refuserLimite(ile, pl);
                }
            }
        }

        if (moteur && colle) {
            moteurs.addLast(new Moteur(w, pl.getBlockX(), pl.getBlockY(), pl.getBlockZ(), System.currentTimeMillis()));
            if (moteurs.size() > 2000) moteurs.removeFirst();
        }
        if (moteur && tnt) {
            List<Block> graines = new ArrayList<>(blocs);
            graines.add(piston);
            signaler(ile, pl, "machine volante qui transporte de la TNT", graines);
            return true;
        }
        return false;
    }

    private boolean refuserLimite(Iles.Ile ile, Location l) {
        avisLimite(ile, l);
        return true;
    }

    private void avisLimite(Iles.Ile ile, Location l) {
        String cle = ile == null ? "hors-ile" : ile.id();
        long now = System.currentTimeMillis();
        Long d = dernierAvisLimite.get(cle);
        if (d != null && now - d < 15000) return;
        dernierAvisLimite.put(cle, now);
        String ou = l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        getLogger().info("Pistons bloques a la limite d'une ile (" + (ile == null ? "hors ile" : nom(ile.owner())) + ") en " + ou);
        if (ile != null) for (UUID u : ile.members()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(PREFIXE + "Une machine à pistons a été bloquée à la limite de ton île : rien ne peut en sortir.");
        }
    }

    /** TNT allumée sans joueur à côté d'une machine en mouvement = duplicateur embarqué. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        Entity en = e.getEntity();
        World w = en.getWorld();
        if (!surveille(w) || !iles.ok) return;
        Location l = en.getLocation();
        if (en instanceof TNTPrimed t) {
            if (estNotreTnt(t) || t.getSource() != null) return;
            long now = System.currentTimeMillis();
            for (Moteur m : moteurs) {
                if (m.w() != w || now - m.t() > 3000) continue;
                double dx = m.x() - l.getX(), dy = m.y() - l.getY(), dz = m.z() - l.getZ();
                if (dx * dx + dy * dy + dz * dz <= 64) {
                    Location c = new Location(w, m.x(), m.y(), m.z());
                    Iles.Ile ile = iles.at(c);
                    if (ile == null && iles.derniereErreur) return;
                    signaler(ile, c, "duplicateur de TNT sur une machine volante", List.of(c.getBlock()));
                    return;
                }
            }
        }
    }

    private void signaler(Iles.Ile ile, Location centre, String raison, List<Block> graines) {
        if (ile == null) {
            // déjà hors de toute île : on la retire tout de suite
            int n = retirer(machine(graines, centre));
            annoncerAdmins("Machine à TNT hors île retirée (" + n + " blocs) en " + coord(centre) + ".");
            return;
        }
        for (Incident i : incidents) if (i.ile.id().equals(ile.id())) return;
        if (punitions.containsKey(ile.id())) return;
        Incident inc = new Incident();
        inc.ile = ile;
        inc.centre = centre.clone();
        inc.raison = raison;
        inc.graines.addAll(graines);
        int delai = Math.max(3, getConfig().getInt("delai-secondes", 10));
        inc.limite = System.currentTimeMillis() + delai * 1000L;
        incidents.add(inc);
        int n = recidive(ile.owner());
        for (UUID u : ile.members()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.sendTitle("§c§lMachine à TNT !", "§eDémonte-la : " + delai + " s", 5, 60, 10);
            p.sendMessage(PREFIXE + "§cUne " + raison + " a été détectée sur ton île§7 (" + coord(centre) + "). Elle est figée. "
                    + "§fRetire sa TNT dans les " + delai + " secondes§7, sinon elle sera détruite et ton île prendra une pluie de TNT "
                    + "(elle sera remise à l'identique juste après).");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
        }
        annoncerAdmins("Machine à TNT sur l'île de " + nom(ile.owner()) + " en " + coord(centre) + " (" + raison + ") : "
                + (n <= 1 ? "1re détection" : "§c" + n + "e détection") + "§7.");
    }

    private void seconde() {
        long now = System.currentTimeMillis();
        while (!moteurs.isEmpty() && now - moteurs.peekFirst().t() > 5000) moteurs.removeFirst();
        Iterator<Incident> it = incidents.iterator();
        List<Incident> fini = new ArrayList<>();
        while (it.hasNext()) {
            Incident i = it.next();
            long reste = (i.limite - now + 999) / 1000;
            if (reste > 0) {
                for (UUID u : i.ile.members()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) p.sendActionBar("§cMachine à TNT figée : §e" + reste + " s §cpour la démonter");
                }
                continue;
            }
            it.remove();
            fini.add(i);
        }
        for (Incident i : fini) juger(i);
    }

    private void juger(Incident i) {
        List<Block> machine = machine(i.graines, i.centre);
        boolean tnt = false;
        for (Block b : machine) if (b.getType() == Material.TNT) tnt = true;
        if (!tnt) {
            for (UUID u : i.ile.members()) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) p.sendMessage(PREFIXE + "§aMachine démontée, merci. §7Pas de punition cette fois.");
            }
            annoncerAdmins("Machine de " + nom(i.ile.owner()) + " démontée à temps.");
            return;
        }
        int n = retirer(machine);
        annoncerAdmins("Machine de " + nom(i.ile.owner()) + " non démontée : " + n + " blocs retirés, pluie de TNT sur son île.");
        punir(i.ile);
    }

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /**
     * Blocs de la machine : partent des blocs vus dans l'événement de piston (la machine est figée, ils sont
     * toujours là) et suivent les blocs reliés (TNT, slime, miel, pistons, observeurs), à 10 blocs au plus.
     */
    List<Block> machine(List<Block> graines, Location c) {
        ArrayDeque<Block> file = new ArrayDeque<>();
        Set<Block> vus = new HashSet<>();
        for (Block g : graines) if (MACHINE.contains(g.getType()) && vus.add(g)) file.add(g);
        BlockFace[] faces = FACES;
        List<Block> machine = new ArrayList<>();
        while (!file.isEmpty() && machine.size() < 4000) {
            Block b = file.poll();
            machine.add(b);
            for (BlockFace f : faces) {
                Block n = b.getRelative(f);
                if (vus.contains(n) || !MACHINE.contains(n.getType())) continue;
                if (n.getLocation().distanceSquared(c) > 10 * 10) continue;
                vus.add(n);
                file.add(n);
            }
        }
        return machine;
    }

    /** Retire les blocs de la machine (sans rien faire tomber) et les TNT allumées autour. */
    int retirer(List<Block> machine) {
        if (machine.isEmpty()) return 0;
        Location c = machine.get(0).getLocation();
        World w = c.getWorld();
        for (Block b : machine) b.setType(Material.AIR, false);
        for (Entity e : w.getNearbyEntities(c, 16, 16, 16))
            if (e instanceof TNTPrimed && !estNotreTnt(e)) e.remove();
        w.playSound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        return machine.size();
    }

    // ================================================================== punitions

    void punir(Iles.Ile ile) {
        if (punitions.containsKey(ile.id())) return;
        File dir = new File(dossierPunitions, ile.id().replaceAll("[^A-Za-z0-9_-]", "_"));
        if (dir.exists()) {
            // une ancienne punition de cette île est encore sur disque (pas restaurée ?) : on ne l'efface jamais
            annoncerAdmins("§cÎle de " + nom(ile.owner()) + " : une ancienne punition non terminée est sur disque ("
                    + dir.getName() + "), nouvelle punition refusée. /antitnt reprendre");
            return;
        }
        Punition p = new Punition(this, dir, ile.id(), nom(ile.owner()), ile.world(),
                ile.minX(), ile.maxX(), ile.minZ(), ile.maxZ(), ile.cx(), ile.cz());
        punitions.put(ile.id(), p);
        p.demarrer();
    }

    /** Appelé par Punition à la fin (réussie ou annulée) : déplace la sauvegarde dans les archives. */
    void terminer(Punition p, boolean ok) {
        punitions.remove(p.id);
        dossierArchives.mkdirs();
        String horo = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File dest = new File(dossierArchives, p.dir.getName().replaceAll("-\\d{8}-\\d{6}$", "") + "-" + horo);
        if (!p.dir.renameTo(dest)) getLogger().warning("Archive impossible : " + p.dir + " -> " + dest);
        if (!ok) getLogger().warning("Punition de " + p.nom + " terminee avec des erreurs, archive : " + dest.getName());
    }

    private void lireZonesAttente() {
        zonesAttente.clear();
        File[] dirs = dossierPunitions.listFiles(File::isDirectory);
        if (dirs == null) return;
        for (File d : dirs) {
            YamlConfiguration y = Punition.lireEtat(d);
            if (y == null) continue;
            zonesAttente.add(new ZoneAttente(d, y.getString("monde", ""), y.getInt("min-x"), y.getInt("max-x"),
                    y.getInt("min-z"), y.getInt("max-z")));
        }
    }

    int reprendrePunitions() {
        File[] dirs = dossierPunitions.listFiles(File::isDirectory);
        if (dirs == null) return 0;
        int echecs = 0;
        for (File d : dirs) {
            boolean dejaLa = false;
            for (Punition q : punitions.values()) if (q.dir.equals(d)) dejaLa = true;
            if (dejaLa) continue;
            Punition p = Punition.load(this, d);
            if (p == null) {
                echecs++;
                getLogger().severe("Punition " + d.getName() + " illisible ou monde absent : nouvel essai dans 1 min (/antitnt reprendre).");
                continue;
            }
            zonesAttente.removeIf(z -> z.dir().equals(d));
            // joueurs bloqués avant que la punition soit chargée
            for (Iterator<Map.Entry<UUID, GameMode>> it = bloquesAttente.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, GameMode> e = it.next();
                Player pj = Bukkit.getPlayer(e.getKey());
                if (pj != null && p.contains(pj.getLocation())) {
                    p.bloquer(pj, e.getValue());
                    it.remove();
                }
            }
            punitions.put(p.id, p);
            switch (p.phase) {
                case "SAUVEGARDE" -> { // rien n'avait été touché
                    p.deverrouiller();
                    terminer(p, true);
                }
                case "FINI" -> {
                    p.deverrouiller();
                    terminer(p, true);
                }
                default -> {
                    getLogger().warning("Punition de " + p.nom + " interrompue (" + p.phase + ") : restauration de l'ile.");
                    p.restaurer();
                }
            }
        }
        return echecs;
    }

    private void purgerArchives() {
        File[] dirs = dossierArchives.listFiles(File::isDirectory);
        if (dirs == null) return;
        long limite = System.currentTimeMillis() - getConfig().getInt("garder-jours", 7) * 86400000L;
        for (File d : dirs) if (d.lastModified() < limite) supprimer(d);
    }

    private static void supprimer(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File x : l) supprimer(x);
        if (!f.delete()) f.deleteOnExit();
    }

    // ================================================================== joueurs pendant une punition

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (punitions.isEmpty() || e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        verifier(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (punitions.isEmpty() || e.getTo() == null) return;
        Bukkit.getScheduler().runTask(this, () -> verifier(e.getPlayer(), e.getPlayer().getLocation()));
    }

    private void verifier(Player p, Location to) {
        for (Punition pu : punitions.values())
            if (!pu.phase.equals("FINI") && pu.contains(to)) {
                pu.bloquer(p);
                return;
            }
        for (ZoneAttente z : zonesAttente)
            if (z.contains(to) && !bloquesAttente.containsKey(p.getUniqueId())) {
                bloquesAttente.put(p.getUniqueId(), p.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : p.getGameMode());
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage(PREFIXE + "Cette île est en cours de restauration : tu passes spectateur quelques instants.");
                return;
            }
    }

    private boolean estBloque(UUID u) {
        if (bloquesAttente.containsKey(u)) return true;
        for (Punition pu : punitions.values()) if (pu.bloques.containsKey(u)) return true;
        return false;
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        GameMode avant = bloquesAttente.remove(e.getPlayer().getUniqueId());
        if (avant != null) e.getPlayer().setGameMode(avant);
        for (Punition pu : punitions.values()) if (pu.bloques.containsKey(e.getPlayer().getUniqueId())) {
            if (!e.getPlayer().getWorld().getName().equals(pu.worldName)) pu.relacher(e.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        appliquerRetour(p);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) verifier(p, p.getLocation());
        }, 5L);
    }

    void retourEnAttente(UUID u, GameMode g, String monde, int x, int z) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierRetours);
        y.set(u + ".mode", g.name());
        y.set(u + ".monde", monde);
        y.set(u + ".x", x);
        y.set(u + ".z", z);
        try {
            y.save(fichierRetours);
        } catch (IOException ex) {
            getLogger().warning("retours.yml : " + ex);
        }
    }

    private void appliquerRetour(Player p) {
        if (!fichierRetours.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierRetours);
        String k = p.getUniqueId().toString();
        if (!y.isConfigurationSection(k)) return;
        GameMode g;
        try {
            g = GameMode.valueOf(y.getString(k + ".mode", "SURVIVAL"));
        } catch (IllegalArgumentException ex) {
            g = GameMode.SURVIVAL;
        }
        World w = Bukkit.getWorld(y.getString(k + ".monde", ""));
        int x = y.getInt(k + ".x"), z = y.getInt(k + ".z");
        y.set(k, null);
        try {
            y.save(fichierRetours);
        } catch (IOException ignored) {
        }
        GameMode mode = g;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (estBloque(p.getUniqueId())) { // de nouveau sur une île punie : on garde le retour pour plus tard
                retourEnAttente(p.getUniqueId(), mode, w == null ? "" : w.getName(), x, z);
                return;
            }
            if (w != null && p.getWorld() == w) {
                Location sur = caseSure(w, x, z);
                if (sur != null) p.teleport(sur);
            }
            p.setGameMode(mode);
        }, 10L);
    }

    // ================================================================== pluie et protection de l'île punie

    boolean estNotreTnt(Entity e) {
        return e != null && e.getPersistentDataContainer().has(keyPluie, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent e) {
        if (!estNotreTnt(e.getEntity())) return;
        e.setCancelled(true);
        String id = e.getEntity().getPersistentDataContainer().get(keyPluie, PersistentDataType.STRING);
        Punition p = punitions.get(id);
        if (p != null) p.exploser(e.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (estNotreTnt(e.getDamager())) e.setCancelled(true);
    }

    private Punition punieA(Location l) {
        if (punitions.isEmpty()) return null;
        if (respawnEnCours) return null;
        for (Punition p : punitions.values())
            if (!p.phase.equals("FINI") && p.contains(l)) return p;
        return null;
    }

    /** Pendant la punition, l'île ne bouge pas toute seule : pas d'eau qui coule, de sable qui tombe, d'objets qui apparaissent. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnPunie(EntitySpawnEvent e) {
        Entity en = e.getEntity();
        if ((en instanceof Item || en instanceof FallingBlock) && punieA(en.getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFlow(BlockFromToEvent e) {
        if (punieA(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeaves(LeavesDecayEvent e) {
        if (punieA(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDispense(BlockDispenseEvent e) {
        if (punieA(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (punieA(e.getItem().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopper(InventoryMoveItemEvent e) {
        Location l = e.getSource().getLocation();
        if (l != null && punieA(l) != null) e.setCancelled(true);
    }

    // ================================================================== outils

    private static long cleChunk(Chunk c) {
        return (((long) c.getX()) << 32) ^ (c.getZ() & 0xffffffffL) ^ ((long) c.getWorld().getName().hashCode() << 48);
    }

    /** Tickets de chunks comptés : deux punitions voisines ne se libèrent pas leurs chunks. */
    void prendreTicket(Chunk c) {
        if (tickets.merge(cleChunk(c), 1, Integer::sum) == 1) c.addPluginChunkTicket(this);
    }

    void rendreTicket(Chunk c) {
        long k = cleChunk(c);
        Integer n = tickets.get(k);
        if (n == null || n <= 1) {
            tickets.remove(k);
            try {
                c.removePluginChunkTicket(this);
            } catch (Throwable ignored) {
            }
        } else tickets.put(k, n - 1);
    }

    /** Case sûre près de (x, z) : sol plein (ni liquide ni magma), deux cases d'air au-dessus ; sinon spawn du monde. */
    static Location caseSure(World w, int x0, int z0) {
        for (int r = 0; r <= 12; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = x0 + dx, z = z0 + dz;
                    for (int y = w.getMaxHeight() - 3; y > w.getMinHeight(); y--) {
                        Block sol = w.getBlockAt(x, y, z);
                        Material m = sol.getType();
                        if (m.isAir()) continue;
                        if (m.isSolid() && !sol.isLiquid() && m != Material.MAGMA_BLOCK && m != Material.CACTUS
                                && sol.getRelative(0, 1, 0).getType().isAir() && sol.getRelative(0, 2, 0).getType().isAir())
                            return new Location(w, x + 0.5, y + 1, z + 0.5);
                        break; // premier bloc rencontré pas sûr : colonne suivante
                    }
                }
        return w.getSpawnLocation();
    }

    int recidive(UUID owner) {
        if (owner == null) return 1;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichierRecidives);
        int n = y.getInt(owner + ".nombre") + 1;
        y.set(owner + ".nombre", n);
        y.set(owner + ".pseudo", nom(owner));
        y.set(owner + ".derniere", new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
        try {
            getDataFolder().mkdirs();
            y.save(fichierRecidives);
        } catch (IOException ignored) {
        }
        return n;
    }

    Set<UUID> membresEnLigne(String id) {
        Set<UUID> out = new HashSet<>();
        Punition p = punitions.get(id);
        if (p == null || !iles.ok) return out;
        World w = p.world();
        if (w == null) return out;
        Iles.Ile ile = iles.at(new Location(w, p.cx, 100, p.cz));
        if (ile == null) return out;
        for (UUID u : ile.members()) if (Bukkit.getPlayer(u) != null) out.add(u);
        return out;
    }

    void annoncerAdmins(String msg) {
        getLogger().info(msg.replaceAll("§.", ""));
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.hasPermission("eemiantitnt.admin")) p.sendMessage(PREFIXE + msg);
    }

    static String nom(UUID u) {
        if (u == null) return "?";
        OfflinePlayer o = Bukkit.getOfflinePlayer(u);
        return o.getName() == null ? u.toString().substring(0, 8) : o.getName();
    }

    static String coord(Location l) {
        return l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    // ================================================================== commande

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("statut")) {
            s.sendMessage(PREFIXE + "BentoBox : " + (iles.ok ? "§abranche" : "§c" + iles.erreur) + "§7, mondes " + mondes);
            s.sendMessage(PREFIXE + "Machines figées : " + incidents.size() + ", punitions en cours : " + punitions.size());
            for (Incident i : incidents) s.sendMessage("§7 - machine de " + nom(i.ile.owner()) + " en " + coord(i.centre));
            for (Punition p : punitions.values()) s.sendMessage("§7 - île de " + p.nom + " : " + p.phase);
            for (ZoneAttente z : zonesAttente) s.sendMessage("§c - punition sur disque pas encore chargée : " + z.dir().getName());
            s.sendMessage("§7/antitnt punir <joueur> confirmer | restaurer <joueur> confirmer | reprendre | annuler | reload");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "reload" -> {
                lireConfig();
                s.sendMessage(PREFIXE + "Config relue.");
            }
            case "reprendre" -> {
                int e = reprendrePunitions();
                s.sendMessage(PREFIXE + "Punitions reprises" + (e > 0 ? ", §c" + e + " encore illisibles (voir console)" : "") + "§7.");
            }
            case "annuler" -> {
                incidents.clear();
                s.sendMessage(PREFIXE + "Machines dégelées, aucune punition lancée.");
            }
            case "punir", "restaurer" -> {
                if (a.length < 2) {
                    s.sendMessage(PREFIXE + "/antitnt " + a[0] + " <joueur> confirmer");
                    return true;
                }
                Iles.Ile ile = ileDe(s, a[1]);
                if (ile == null) {
                    s.sendMessage(PREFIXE + "§cAucune île trouvée pour " + a[1] + " dans " + mondes + ".");
                    return true;
                }
                if (punitions.containsKey(ile.id())) {
                    s.sendMessage(PREFIXE + "§cUne punition est déjà en cours sur cette île.");
                    return true;
                }
                if (a[0].equalsIgnoreCase("punir")) {
                    if (a.length < 3 || !a[2].equalsIgnoreCase("confirmer")) {
                        s.sendMessage(PREFIXE + "Île de " + nom(ile.owner()) + " (" + ile.world().getName() + " " + ile.cx() + " " + ile.cz()
                                + ") : sauvegarde, pluie de TNT puis remise à l'identique. Ajoute §fconfirmer§7.");
                        return true;
                    }
                    annoncerAdmins("Punition manuelle de l'île de " + nom(ile.owner()) + " par " + s.getName() + ".");
                    punir(ile);
                } else {
                    File arch = derniereArchive(ile.id());
                    if (arch == null) {
                        s.sendMessage(PREFIXE + "§cAucune sauvegarde de cette île dans les archives.");
                        return true;
                    }
                    if (a.length < 3 || !a[2].equalsIgnoreCase("confirmer")) {
                        s.sendMessage(PREFIXE + "Remettre l'île de " + nom(ile.owner()) + " dans l'état de la sauvegarde §f" + arch.getName()
                                + "§7 ? Tout ce qui a changé depuis sera perdu. Ajoute §fconfirmer§7.");
                        return true;
                    }
                    // copie dans punitions/ : une restauration interrompue par un plantage sera reprise au démarrage
                    File dest = new File(dossierPunitions, arch.getName().replaceAll("-\\d{8}-\\d{6}$", ""));
                    if (dest.exists()) {
                        s.sendMessage(PREFIXE + "§cUne punition de cette île est déjà sur disque : /antitnt reprendre.");
                        return true;
                    }
                    try {
                        copier(arch.toPath(), dest.toPath());
                    } catch (IOException ex) {
                        s.sendMessage(PREFIXE + "§cCopie de la sauvegarde impossible : " + ex.getMessage());
                        return true;
                    }
                    Punition p = Punition.load(this, dest);
                    if (p == null) {
                        s.sendMessage(PREFIXE + "§cSauvegarde illisible.");
                        return true;
                    }
                    p.depuisArchive = true;
                    punitions.put(p.id, p);
                    annoncerAdmins("Restauration de l'île de " + p.nom + " depuis " + arch.getName() + " par " + s.getName() + ".");
                    p.restaurerArchive();
                }
            }
            default -> s.sendMessage(PREFIXE + "Inconnu. /antitnt statut");
        }
        return true;
    }

    private Iles.Ile ileDe(CommandSender s, String pseudo) {
        OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(pseudo);
        if (o == null) return null;
        List<World> ordre = new ArrayList<>();
        if (s instanceof Player p && surveille(p.getWorld())) ordre.add(p.getWorld());
        for (String m : mondes) {
            World w = Bukkit.getWorld(m);
            if (w != null && !ordre.contains(w)) ordre.add(w);
        }
        for (World w : ordre) {
            Iles.Ile i = iles.of(w, o.getUniqueId());
            if (i != null) return i;
        }
        return null;
    }

    private static void copier(Path src, Path dst) throws IOException {
        try (var st = Files.walk(src)) {
            for (Path p : (Iterable<Path>) st::iterator) {
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private File derniereArchive(String id) {
        String base = id.replaceAll("[^A-Za-z0-9_-]", "_") + "-";
        File[] l = dossierArchives.listFiles(f -> f.isDirectory() && f.getName().startsWith(base)
                && new File(f, "state.yml").isFile());
        if (l == null || l.length == 0) return null;
        File best = null;
        for (File f : l) if (best == null || f.getName().compareTo(best.getName()) > 0) best = f;
        return best;
    }
}
