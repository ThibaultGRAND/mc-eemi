package fr.eemicraft.antitnt;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Punition d'une île : sauvegarde complète (blocs + contenus en structures NBT par tuiles de 32,
 * entités en snapshots), pluie de TNT qui casse vraiment l'île (sans rien faire tomber), puis remise
 * à l'identique (deux passes de pose pour les blocs accrochés aux bords des tuiles). Tout est écrit sur
 * disque avant de casser quoi que ce soit : un redémarrage pendant la pluie relance la restauration.
 *
 * Phases (state.yml) : SAUVEGARDE (rien n'est encore cassé) -> PLUIE (entités retirées) -> RESTAURATION -> FINI.
 */
final class Punition {

    static final int TUILE = 32;

    final EemiAntiTnt pl;
    final File dir;
    final String id;
    final String nom; // pseudo du propriétaire (affichage)
    final String worldName;
    final int minX, maxX, minZ, maxZ; // max exclusifs
    int minY, maxY;                   // inclusifs (blocs sauvegardés)
    final int cx, cz;
    String phase = "SAUVEGARDE";
    boolean depuisArchive; // restauration manuelle d'une archive : pas de pluie
    final Map<UUID, GameMode> bloques = new LinkedHashMap<>();
    private final List<int[]> tuiles = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private BukkitTask tache;
    private final Random rnd = new Random();

    Punition(EemiAntiTnt pl, File dir, String id, String nom, World w, int minX, int maxX, int minZ, int maxZ, int cx, int cz) {
        this.pl = pl;
        this.dir = dir;
        this.id = id;
        this.nom = nom;
        this.worldName = w.getName();
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.cx = cx;
        this.cz = cz;
        this.minY = w.getMinHeight();
        this.maxY = w.getMaxHeight() - 1;
    }

    World world() {
        return Bukkit.getWorld(worldName);
    }

    boolean contains(Location l) {
        if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(worldName)) return false;
        int x = l.getBlockX(), z = l.getBlockZ();
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    /** Boîte des entités : toute la hauteur du monde (perroquets, abeilles au-dessus de l'île comprises). */
    BoundingBox boxEntites(World w) {
        return new BoundingBox(minX, w.getMinHeight(), minZ, maxX, w.getMaxHeight(), maxZ);
    }

    // ================================================================== persistance

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("id", id);
        y.set("nom", nom);
        y.set("monde", worldName);
        y.set("min-x", minX);
        y.set("max-x", maxX);
        y.set("min-z", minZ);
        y.set("max-z", maxZ);
        y.set("min-y", minY);
        y.set("max-y", maxY);
        y.set("centre-x", cx);
        y.set("centre-z", cz);
        y.set("phase", phase);
        y.set("depuis-archive", depuisArchive);
        List<String> t = new ArrayList<>();
        for (int[] a : tuiles) t.add(a[0] + " " + a[1] + " " + a[2] + " " + a[3] + " " + a[4] + " " + a[5]);
        y.set("tuiles", t);
        for (Map.Entry<UUID, GameMode> e : bloques.entrySet()) y.set("bloques." + e.getKey(), e.getValue().name());
        try {
            dir.mkdirs();
            File tmp = new File(dir, "state.yml.tmp");
            y.save(tmp);
            Files.move(tmp.toPath(), new File(dir, "state.yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            pl.getLogger().severe("Ecriture de " + dir + "/state.yml impossible : " + ex);
        }
    }

    /** Monde de la punition décrite dans ce dossier (sans la charger), ou null. */
    static YamlConfiguration lireEtat(File dir) {
        File f = new File(dir, "state.yml");
        if (!f.isFile()) f = new File(dir, "state.yml.tmp");
        return f.isFile() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    static Punition load(EemiAntiTnt pl, File dir) {
        YamlConfiguration y = lireEtat(dir);
        if (y == null) return null;
        World w = Bukkit.getWorld(y.getString("monde", ""));
        if (w == null) return null;
        Punition p = new Punition(pl, dir, y.getString("id", dir.getName()), y.getString("nom", "?"), w,
                y.getInt("min-x"), y.getInt("max-x"), y.getInt("min-z"), y.getInt("max-z"),
                y.getInt("centre-x"), y.getInt("centre-z"));
        p.minY = y.getInt("min-y", w.getMinHeight());
        p.maxY = y.getInt("max-y", w.getMaxHeight() - 1);
        p.phase = y.getString("phase", "SAUVEGARDE");
        p.depuisArchive = y.getBoolean("depuis-archive", false);
        for (String s : y.getStringList("tuiles")) {
            String[] a = s.trim().split(" ");
            if (a.length != 6) continue;
            int[] t = new int[6];
            for (int i = 0; i < 6; i++) t[i] = Integer.parseInt(a[i]);
            p.tuiles.add(t);
        }
        ConfigurationSection b = y.getConfigurationSection("bloques");
        if (b != null) for (String k : b.getKeys(false)) {
            try {
                p.bloques.put(UUID.fromString(k), GameMode.valueOf(b.getString(k, "SURVIVAL")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return p;
    }

    private File tuileFile(int i) {
        return new File(dir, "tuiles/" + i + ".nbt");
    }

    // ================================================================== déroulé

    /** Lance la punition complète : verrou, sauvegarde, pluie, restauration. */
    void demarrer() {
        World w = world();
        if (w == null) return;
        save();
        verrouillerPresents();
        chargerChunks(w);
        attendreEntites(w, 0, ok -> {
            if (!ok) {
                annuler("entites de l'ile pas chargees apres 10 s");
                return;
            }
            sauverBlocs(w);
        });
    }

    /** Abandon pendant la SAUVEGARDE : rien n'a été cassé ni retiré. */
    private void annuler(String raison) {
        if (tache != null) tache.cancel();
        pl.getLogger().severe("[" + nom + "] punition ANNULEE : " + raison);
        pl.annoncerAdmins("§cPunition de l'île de " + nom + " annulée (" + raison + ") : rien n'a été touché.");
        deverrouiller();
        libererChunks();
        pl.terminer(this, false);
    }

    private void chargerChunks(World w) {
        for (int x = minX >> 4; x <= (maxX - 1) >> 4; x++)
            for (int z = minZ >> 4; z <= (maxZ - 1) >> 4; z++) {
                Chunk c = w.getChunkAt(x, z);
                pl.prendreTicket(c);
                chunks.add(c);
            }
    }

    void libererChunks() {
        for (Chunk c : chunks) pl.rendreTicket(c);
        chunks.clear();
    }

    /** Attend que les entités de tous les chunks soient chargées (10 s max). */
    private void attendreEntites(World w, int essais, java.util.function.Consumer<Boolean> suite) {
        boolean tous = true;
        for (Chunk c : chunks) if (!c.isEntitiesLoaded()) {
            tous = false;
            break;
        }
        if (tous || essais >= 200) {
            suite.accept(tous);
            return;
        }
        Bukkit.getScheduler().runTaskLater(pl, () -> attendreEntites(w, essais + 1, suite), 1L);
    }

    private void sauverBlocs(World w) {
        // hauteur utile : plus haut bloc de la zone + 2
        int top = w.getMinHeight();
        for (int x = minX; x < maxX; x++)
            for (int z = minZ; z < maxZ; z++)
                top = Math.max(top, w.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        maxY = Math.min(w.getMaxHeight() - 1, top + 2);
        minY = w.getMinHeight();
        tuiles.clear();
        for (int x = minX; x < maxX; x += TUILE)
            for (int z = minZ; z < maxZ; z += TUILE)
                for (int y = minY; y <= maxY; y += TUILE)
                    tuiles.add(new int[]{x, y, z, Math.min(TUILE, maxX - x), Math.min(TUILE, maxY + 1 - y), Math.min(TUILE, maxZ - z)});
        new File(dir, "tuiles").mkdirs();
        StructureManager sm = Bukkit.getStructureManager();
        int parTick = Math.max(1, pl.getConfig().getInt("tuiles-par-tick", 6));
        final int[] i = {0};
        tache = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            try {
                for (int n = 0; n < parTick && i[0] < tuiles.size(); n++, i[0]++) {
                    int[] t = tuiles.get(i[0]);
                    Structure s = sm.createStructure();
                    s.fill(new Location(w, t[0], t[1], t[2]), new BlockVector(t[3], t[4], t[5]), false);
                    sm.saveStructure(tuileFile(i[0]), s);
                }
            } catch (Throwable ex) {
                annuler("sauvegarde des blocs impossible : " + ex);
                return;
            }
            if (i[0] >= tuiles.size()) {
                tache.cancel();
                save(); // tuiles connues sur disque avant de toucher aux entités
                sauverEntites(w);
            }
        }, 1L, 1L);
    }

    /** Copie toutes les entités (sauf joueurs) puis les retire : elles ne peuvent ni tomber ni mourir. */
    private void sauverEntites(World w) {
        List<Entity> liste = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(boxEntites(w))) {
            if (e instanceof Player || pl.estNotreTnt(e)) continue;
            liste.add(e);
        }
        Map<Entity, Integer> index = new HashMap<>();
        for (int k = 0; k < liste.size(); k++) index.put(liste.get(k), k);
        List<Map<String, Object>> out = new ArrayList<>();
        List<Entity> aRetirer = new ArrayList<>();
        List<Entity> figees = new ArrayList<>();
        for (Entity e : liste) {
            EntitySnapshot snap;
            try {
                snap = e.createSnapshot();
            } catch (Throwable ex) {
                snap = null;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            Location l = e.getLocation();
            m.put("type", e.getType().name());
            m.put("x", l.getX());
            m.put("y", l.getY());
            m.put("z", l.getZ());
            m.put("yaw", (double) l.getYaw());
            m.put("pitch", (double) l.getPitch());
            Entity v = e.getVehicle();
            m.put("vehicule", v != null && index.containsKey(v) ? index.get(v) : -1);
            if (snap == null) {
                m.put("nbt", "");
                figees.add(e);
            } else {
                m.put("nbt", snap.getAsString());
                aRetirer.add(e);
            }
            out.add(m);
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("entites", out);
        try {
            y.save(new File(dir, "entites.yml"));
        } catch (IOException ex) {
            annuler("entites.yml impossible : " + ex);
            return;
        }
        // impossibles à copier : laissées en place, figées et invulnérables
        for (Entity e : figees) {
            e.setGravity(false);
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(pl.keyFige, PersistentDataType.BYTE, (byte) 1);
        }
        for (Entity e : aRetirer) e.eject();
        for (Entity e : aRetirer) e.remove();
        phase = "PLUIE";
        save();
        pl.getLogger().info("[" + nom + "] ile sauvegardee : " + tuiles.size() + " tuiles, " + out.size() + " entites (y " + minY + ".." + maxY + ").");
        pleuvoir(w);
    }

    private void defiger(Entity e) {
        if (e.getPersistentDataContainer().has(pl.keyFige)) {
            e.getPersistentDataContainer().remove(pl.keyFige);
            e.setGravity(true);
            e.setInvulnerable(false);
        }
    }

    private void pleuvoir(World w) {
        int duree = Math.max(5, pl.getConfig().getInt("pluie-secondes", 30)) * 20;
        int intervalle = Math.max(1, pl.getConfig().getInt("pluie-intervalle-ticks", 4));
        for (UUID u : pl.membresEnLigne(id)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.sendTitle("§c§lPLUIE DE TNT", "§7Ta machine n'a pas été démontée", 5, 60, 10);
                p.sendMessage(EemiAntiTnt.PREFIXE + "Ton île prend une pluie de TNT pendant " + duree / 20
                        + " s. §fTout sera remis à l'identique ensuite§7 (blocs, coffres, animaux, villageois).");
            }
        }
        final int[] t = {0};
        tache = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            t[0] += intervalle;
            if (t[0] >= duree) {
                tache.cancel();
                Bukkit.getScheduler().runTaskLater(pl, this::restaurer, 60L);
                return;
            }
            int x = minX + rnd.nextInt(Math.max(1, maxX - minX));
            int z = minZ + rnd.nextInt(Math.max(1, maxZ - minZ));
            // vise plutôt la partie construite : une fois sur deux près du centre
            if (rnd.nextBoolean()) {
                x = Math.max(minX, Math.min(maxX - 1, cx + rnd.nextInt(31) - 15));
                z = Math.max(minZ, Math.min(maxZ - 1, cz + rnd.nextInt(31) - 15));
            }
            int y = Math.min(w.getMaxHeight() - 2, w.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 25);
            TNTPrimed tnt = w.spawn(new Location(w, x + 0.5, y, z + 0.5), TNTPrimed.class, e -> {
                e.setFuseTicks(60);
                e.getPersistentDataContainer().set(pl.keyPluie, PersistentDataType.STRING, id);
            });
            tnt.setVelocity(tnt.getVelocity().setY(-0.6));
        }, intervalle, intervalle);
    }

    /** Explosion d'une TNT de la pluie : casse une sphère dans la zone de l'île, sans rien faire tomber. */
    void exploser(Location l) {
        World w = l.getWorld();
        if (w == null || !phase.equals("PLUIE")) return;
        double r = Math.max(1.5, pl.getConfig().getDouble("pluie-rayon", 3.0));
        int ri = (int) Math.ceil(r);
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        for (int dx = -ri; dx <= ri; dx++)
            for (int dy = -ri; dy <= ri; dy++)
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    int x = bx + dx, y = by + dy, z = bz + dz;
                    if (x < minX || x >= maxX || z < minZ || z >= maxZ || y < minY || y > maxY) continue;
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir() || m == Material.BEDROCK) continue;
                    b.setType(Material.AIR, false);
                }
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.9f + rnd.nextFloat() * 0.2f);
    }

    /** Restauration manuelle d'une archive (/antitnt restaurer). */
    void restaurerArchive() {
        depuisArchive = true;
        restaurer();
    }

    /** Remet les blocs (deux passes) puis les entités, rend les joueurs. Utilisé aussi au démarrage après un arrêt. */
    void restaurer() {
        World w = world();
        if (w == null) {
            pl.getLogger().severe("[" + nom + "] monde " + worldName + " introuvable : restauration reportee.");
            return;
        }
        if (tache != null) tache.cancel();
        phase = "RESTAURATION";
        save();
        verrouillerPresents();
        if (chunks.isEmpty()) chargerChunks(w);
        attendreEntites(w, 0, ok -> {
            if (!ok) pl.getLogger().warning("[" + nom + "] entites pas toutes chargees, restauration quand meme.");
            // tout retirer (sauf entités figées) : après un plantage, le monde sur disque peut encore
            // contenir les entités d'origine, qui feraient doublon avec les copies
            nettoyer(w, true);
            StructureManager sm = Bukkit.getStructureManager();
            int parTick = Math.max(1, pl.getConfig().getInt("tuiles-par-tick", 6));
            Random r = new Random();
            int total = tuiles.size() * 2; // 2e passe : blocs accrochés aux bords d'une tuile posée avant leur support
            final int[] i = {0};
            final int[] erreurs = {0};
            tache = Bukkit.getScheduler().runTaskTimer(pl, () -> {
                for (int n = 0; n < parTick && i[0] < total; n++, i[0]++) {
                    int k = i[0] % tuiles.size();
                    int[] t = tuiles.get(k);
                    try {
                        Structure s = sm.loadStructure(tuileFile(k));
                        s.place(new Location(w, t[0], t[1], t[2]), false, StructureRotation.NONE, Mirror.NONE, 0, 1f, r);
                    } catch (Throwable ex) {
                        if (i[0] >= tuiles.size()) {
                            erreurs[0]++;
                            pl.getLogger().severe("[" + nom + "] tuile " + k + " non restauree : " + ex);
                        }
                    }
                }
                if (i[0] >= total) {
                    tache.cancel();
                    nettoyer(w, false);
                    int[] ent = respawnEntites(w);
                    for (Entity e : w.getNearbyEntities(boxEntites(w))) defiger(e);
                    deverrouiller();
                    libererChunks();
                    phase = "FINI";
                    save();
                    String bilan = "Île de " + nom + " restaurée : " + tuiles.size() + " tuiles, " + ent[0] + " entités"
                            + (ent[1] > 0 ? ", §c" + ent[1] + " entités non recréées (entites-echec.yml)§7" : "")
                            + (erreurs[0] > 0 ? ", §c" + erreurs[0] + " tuiles en erreur" : "") + "§7.";
                    pl.annoncerAdmins(bilan);
                    for (UUID u : pl.membresEnLigne(id)) {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) p.sendMessage(EemiAntiTnt.PREFIXE + "§aTon île a été remise à l'identique.");
                    }
                    pl.terminer(this, erreurs[0] == 0 && ent[1] == 0);
                }
            }, 1L, 1L);
        });
    }

    /** Retire objets au sol, blocs qui tombent, TNT ; avec tout=true, retire aussi les autres entités (sauf figées). */
    private void nettoyer(World w, boolean tout) {
        for (Entity e : w.getNearbyEntities(boxEntites(w))) {
            if (e instanceof Player || e.getPersistentDataContainer().has(pl.keyFige)) continue;
            if (tout || e instanceof Item || e instanceof FallingBlock || e instanceof TNTPrimed) e.remove();
        }
    }

    /** @return {recréées, échecs} */
    private int[] respawnEntites(World w) {
        File f = new File(dir, "entites.yml");
        if (!f.isFile()) return new int[]{0, 0};
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<Map<?, ?>> liste = y.getMapList("entites");
        Entity[] cree = new Entity[liste.size()];
        int n = 0;
        List<Map<?, ?>> echecs = new ArrayList<>();
        pl.respawnEnCours = true;
        try {
            // 1) entités sans véhicule, 2) passagers (sauf si le véhicule les a déjà recréés avec lui)
            for (int passe = 0; passe < 2; passe++) {
                for (int k = 0; k < liste.size(); k++) {
                    Map<?, ?> m = liste.get(k);
                    Object v = m.get("vehicule");
                    int vi = v instanceof Number num ? num.intValue() : -1;
                    if ((passe == 0) != (vi < 0)) continue;
                    String nbt = String.valueOf(m.get("nbt"));
                    if (nbt.isEmpty() || nbt.equals("null")) continue;
                    if (passe == 1 && vi < cree.length && cree[vi] != null && !cree[vi].getPassengers().isEmpty()) continue;
                    try {
                        EntitySnapshot s = Bukkit.getEntityFactory().createEntitySnapshot(nbt);
                        Location l = new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z")),
                                (float) num(m.get("yaw")), (float) num(m.get("pitch")));
                        Entity e = s.createEntity(l);
                        if (e == null || !e.isValid()) throw new IllegalStateException("apparition refusee");
                        cree[k] = e;
                        n++;
                        if (passe == 1 && vi < cree.length && cree[vi] != null) cree[vi].addPassenger(e);
                    } catch (Throwable ex) {
                        echecs.add(m);
                        pl.getLogger().warning("[" + nom + "] entite " + m.get("type") + " non recreee : " + ex);
                    }
                }
            }
        } finally {
            pl.respawnEnCours = false;
        }
        if (!echecs.isEmpty()) {
            YamlConfiguration e = new YamlConfiguration();
            e.set("entites", echecs);
            try {
                e.save(new File(dir, "entites-echec.yml"));
            } catch (IOException ignored) {
            }
        }
        return new int[]{n, echecs.size()};
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    // ================================================================== joueurs

    private void verrouillerPresents() {
        World w = world();
        if (w == null) return;
        for (Player p : w.getPlayers()) if (contains(p.getLocation())) bloquer(p);
    }

    /** Passe le joueur en spectateur tant que l'île est punie (il ne tombe pas, ne touche à rien). */
    void bloquer(Player p) {
        bloquer(p, p.getGameMode() == GameMode.SPECTATOR ? GameMode.SURVIVAL : p.getGameMode());
    }

    void bloquer(Player p, GameMode avant) {
        if (!bloques.containsKey(p.getUniqueId())) {
            bloques.put(p.getUniqueId(), avant);
            save();
            p.sendMessage(EemiAntiTnt.PREFIXE + "Cette île est en cours de punition : tu passes spectateur quelques instants.");
        }
        if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
    }

    /** Rend leur mode de jeu aux joueurs bloqués (hors ligne : à la reconnexion). */
    void deverrouiller() {
        World w = world();
        for (Map.Entry<UUID, GameMode> e : bloques.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                if (w != null && p.getWorld() == w) {
                    Location sur = EemiAntiTnt.caseSure(w, cx, cz);
                    if (sur != null) p.teleport(sur);
                }
                p.setGameMode(e.getValue());
            } else {
                pl.retourEnAttente(e.getKey(), e.getValue(), worldName, cx, cz);
            }
        }
        bloques.clear();
    }

    /** Laisse partir un joueur qui change de monde pendant la punition. */
    void relacher(Player p) {
        GameMode g = bloques.remove(p.getUniqueId());
        if (g != null) {
            p.setGameMode(g);
            save();
        }
    }
}
