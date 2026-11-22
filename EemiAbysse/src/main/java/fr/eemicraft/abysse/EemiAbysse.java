package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import fr.eemicraft.abysse.Store.Machine;
import fr.eemicraft.abysse.Store.PlayerData;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * L'Abysse : mode persistant (seul ou en Compagnie) où l'on descend strate par strate, mine, transforme le
 * minerai à son usine et vend à une bourse aux prix mouvants. Voir CONCEPTION.md.
 */
public final class EemiAbysse extends JavaPlugin implements Listener {

    Store store;
    Bourse bourse;
    Machines machines;
    /** Habillage des convoyeurs posés (cube du pack orienté). */
    Belts belts;
    Worlds worlds;
    Npcs npcs;
    Quests quests;
    Mobs mobs;
    Regen regen;
    Guide guide;
    Reset reset;
    Campements campements;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<UUID, Long> flares = new HashMap<>();
    /** Dernière mort dans une strate (pour la Boussole du dernier souffle) et recharge de la Larme de guérison. */
    record Death(int strata, double x, double y, double z, long time) {
    }

    private final Map<UUID, Death> deaths = new HashMap<>();
    private final Map<UUID, Long> tears = new HashMap<>();
    /** Fin de la protection de la soupe anti-spores. */
    private final Map<UUID, Long> sporeShield = new HashMap<>();
    private final Random random = new Random();

    /** Part des éclats gagnés (ventes, monstres, quêtes, paliers, gardiens, carnets) : réglage {@code gains}. */
    static double GAINS = 0.6;

    static double gain(double n) {
        return n * GAINS;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        GAINS = getConfig().getDouble("gains", 0.6);
        Items.init(this);
        // états de blocs utilisés par le générateur : créés ici, sur le fil principal
        for (String s : new String[]{"minecraft:glow_lichen[up=true]", "minecraft:barrel[facing=up]", "minecraft:campfire[lit=false]",
                "minecraft:lantern[hanging=true]", "minecraft:bell[attachment=floor,facing=south]"}) Gen.state(s);
        store = new Store(this);
        store.load();
        bourse = new Bourse(this);
        bourse.load();
        machines = new Machines(this);
        worlds = new Worlds(this);
        npcs = new Npcs(this);
        quests = new Quests(this);
        mobs = new Mobs(this);
        regen = new Regen(this);
        regen.load();
        guide = new Guide(this);
        reset = new Reset(this);
        campements = new Campements(this);
        worlds.setup();
        getServer().getPluginManager().registerEvents(this, this);
        belts = new Belts(this);
        belts.start();
        getServer().getPluginManager().registerEvents(campements, this);
        if (bourse.contracts.isEmpty()) bourse.sample();

        Bukkit.getScheduler().runTaskTimer(this, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            machines.pullHoppers();
            machines.labels();
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, mobs::spawnTick, 60L, 60L);
        Bukkit.getScheduler().runTaskTimer(this, regen::tick, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, reset::tick, 200L, 200L);
        Bukkit.getScheduler().runTaskTimer(this, npcs::ensure, 20L, 100L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            bourse.decay();
            bourse.refreshContracts();
            worlds.unloadIdle();
            bourse.writeJson();
        }, 200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, worlds::updateBoard, 60L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, bourse::sample, 12000L, 12000L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);
        getLogger().info(store.companies.size() + " compagnies, " + Items.sellables().size() + " produits en bourse.");
    }

    @Override
    public void onDisable() {
        // fouilles de tonneau ouvertes : on les ferme tant que le plugin écoute encore (sinon le contenu serait perdu)
        campements.closeAll();
        mobs.stopAll();
        npcs.removeAll();
        machines.removeAllLabels();
        if (belts != null) belts.removeAll();
        worlds.removeBoard();
        saveAll();
    }

    private void saveAll() {
        store.saveAll();
        regen.save();
        bourse.save();
    }

    void broadcastAbysse(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) if (Worlds.isAbysse(p.getWorld())) p.sendMessage(msg);
    }

    boolean admin(CommandSender s) {
        return s.hasPermission("eemiabysse.admin");
    }

    // ------------------------------------------------------------------ chaque seconde

    private void second() {
        machines.tick();
        mobs.fightTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!Worlds.isAbysse(p.getWorld())) {
                stripStars(p, false);
                continue;
            }
            Gen.Strata s = Gen.strata(p.getWorld());
            if (s != null && p.getGameMode() != GameMode.SPECTATOR) environment(p, s);
            ensureStar(p);
            sidebar(p);
            guardianHint(p);
            gaspardHint(p);
            campements.hint(p);
            // menus ouverts (machines, bourse) : redessinés chaque seconde
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu m && m.refresh != null) m.refresh.accept(m);
        }
    }

    /** Contraintes des strates : lampe (vision nocturne contre du charbon), spores (masque), célérité du Mineur. */
    private void environment(Player p, Gen.Strata s) {
        ItemStack off = p.getInventory().getItemInOffHand();
        if (Items.is(off, "lampe_eternelle"))
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
        else if (Items.is(off, "lampe")) {
            int c = Items.charge(off);
            if (c <= 0 && Bourse.countIn(p, "charbon") > 0) {
                Bourse.take(p, "charbon", 1);
                c = getConfig().getInt("lampe-secondes-par-charbon", 90);
                p.sendActionBar(Txt.c("§eTa lampe brûle un charbon."));
            }
            if (c > 0) {
                Items.setCharge(off, c - 1);
                p.getInventory().setItemInOffHand(off);
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
            } else if (p.getTicksLived() % 10 == 0) p.sendActionBar(Txt.c("§cTa lampe est éteinte : il te faut du charbon."));
        }
        boolean soup = sporeShield.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis();
        if (s.n == 2 && !soup && !Items.filters(p.getInventory().getHelmet()) && p.getTicksLived() / 20 % 8 == 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            p.sendActionBar(Txt.c("§2Les spores te brûlent la gorge... il te faut un Masque filtrant."));
        }
        if (s.n == 3) heat(p);
        Profondeurs.environment(this, p, s);
        if (s.n == 2 && random.nextInt(3) == 0)
            p.spawnParticle(Particle.SPORE_BLOSSOM_AIR, p.getLocation().add(0, 1, 0), 6, 6, 3, 6);
        armorSet(p);
        int lvl = quests.level(store.player(p), "mineur");
        if (lvl >= 25) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 0, true, false, false));
    }

    /**
     * Quête de gardien en cours et joueur dans la bonne strate : toutes les 2 s, la direction et la distance de
     * l'antre dans la barre d'action (flèche relative au regard), puis « clic droit sur l'autel » une fois dedans.
     */
    private void guardianHint(Player p) {
        if (p.getTicksLived() / 20 % 2 != 0) return;
        Quests.Quest q = quests.current(store.player(p));
        if (q == null || q.obj() != Quests.Obj.GUARDIAN) return;
        Gen.Strata s = Gen.strata(p.getWorld());
        if (s == null) {
            if (Worlds.isAbysse(p.getWorld())) p.sendActionBar(Txt.c("§6Gardien à vaincre §7: descends dans la strate " + q.target() + " (étoile → Changer de strate)."));
            return;
        }
        if (!String.valueOf(s.n).equals(q.target()) || mobs.fightIn(s.n)) return;
        Location l = p.getLocation();
        double dx = s.lairX + 0.5 - l.getX(), dz = s.lairZ + 0.5 - l.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        if (dist < Gen.LAIR_R) {
            p.sendActionBar(Txt.c("§6Tu es dans l'antre §7: clic droit sur l'autel noir au centre pour réveiller le gardien."));
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((yaw - l.getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[((int) Math.round(rel / 45.0) + 8) % 8];
        p.sendActionBar(Txt.c("§6Antre du gardien §f§l" + arrow + " §f" + dist + " blocs §8- suis la galerie éclairée de lanternes"));
    }

    /** Où est Gaspard : au sud de la place (z +22), juste devant le point d'arrivée (z 30,5, regard vers le nord). */
    static final String GASPARD_WHERE = "le vieux mineur, au sud de la place (juste devant toi en arrivant)";

    /**
     * Joueur qui n'a pas encore parlé à Gaspard (quête 0), à Bordgouffre : colonne de particules au-dessus de Gaspard,
     * visible de ce joueur seul, et sa direction / distance dans la barre d'action (même flèche que les gardiens).
     */
    private void gaspardHint(Player p) {
        World hub = worlds.hub();
        if (hub == null || p.getWorld() != hub || store.player(p).quest > 0) return;
        Location g = Npcs.position(hub, Npcs.def("gaspard"));
        for (double y = 2.4; y <= 6.0; y += 0.3) p.spawnParticle(Particle.END_ROD, g.clone().add(0, y, 0), 1, 0, 0, 0, 0);
        // pointe de flèche vers le bas, au-dessus du nom
        for (int i = 1; i <= 3; i++) {
            double o = i * 0.18;
            p.spawnParticle(Particle.HAPPY_VILLAGER, g.clone().add(o, 2.4 + o, 0), 1, 0, 0, 0, 0);
            p.spawnParticle(Particle.HAPPY_VILLAGER, g.clone().add(-o, 2.4 + o, 0), 1, 0, 0, 0, 0);
        }
        Location l = p.getLocation();
        double dx = g.getX() - l.getX(), dz = g.getZ() - l.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        if (dist <= 3) {
            p.sendActionBar(Txt.c("§eGaspard est là §7: clic droit sur lui pour lui parler."));
            return;
        }
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((yaw - l.getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String arrow = arrows[((int) Math.round(rel / 45.0) + 8) % 8];
        p.sendActionBar(Txt.c("§eGaspard §f§l" + arrow + " §f" + dist + " blocs §8- le vieux mineur, au sud de la place"));
    }

    // ------------------------------------------------------------------ bilan d'absence

    /** Au départ (déconnexion dans l'Abysse, ou sortie de l'Abysse) : heure et compteurs de la Compagnie. */
    private void awaySnapshot(Player p) {
        Company c = store.companyOf(p.getUniqueId());
        if (c == null) return;
        PlayerData d = store.player(p);
        d.awayAt = System.currentTimeMillis();
        d.awayProduced = c.produced;
        d.awaySold = c.autoSold;
    }

    /** A l'arrivée dans l'Abysse après une absence : ce que l'usine a produit et vendu, et sa mise en veille. */
    private void welcomeBack(Player p) {
        if (!p.isOnline() || !Worlds.isAbysse(p.getWorld())) return;
        PlayerData d = store.player(p);
        if (d.awayAt <= 0) return;
        long now = System.currentTimeMillis(), away = now - d.awayAt;
        long since = d.awayAt;
        d.awayAt = 0;
        Company c = store.companyOf(p.getUniqueId());
        if (c == null || c.machines.isEmpty() || away < getConfig().getLong("bilan.minutes-min", 30) * 60_000L) return;
        long made = Math.max(0, c.produced - d.awayProduced);
        double sold = Math.max(0, c.autoSold - d.awaySold);
        StringBuilder b = new StringBuilder(Txt.PREFIX + "§7Pendant ton absence §8(" + Txt.time(away / 1000) + ")§7 : §f+" + Txt.num(made) + " objets produits");
        b.append("§7, ").append(sold > 0 ? "§6+" + Txt.num(Math.floor(sold)) + " " + Txt.COIN : "§7aucune vente").append(" §7au quai d'expédition.");
        p.sendMessage(b.toString());
        if (c.sleepStart > 0 && c.sleepStart < now && (c.sleepEnd == 0 || c.sleepEnd > since)) {
            long from = Math.max(c.sleepStart, since), to = c.sleepEnd == 0 ? now : c.sleepEnd;
            if (to > from)
                p.sendMessage(Txt.PREFIX + "§7L'usine s'est mise en veille §c" + Txt.time((to - from) / 1000) + "§7 : personne de la Compagnie connecté depuis "
                        + Txt.num(getConfig().getDouble("offline-hours", 12)) + " h.");
        }
    }

    /** Chaleur des Forges Englouties : 1 cœur toutes les 8 s sans protection (infusion, plastron réfrigéré, panoplie abyssale). */
    private void heat(Player p) {
        if (random.nextInt(3) == 0) p.spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 10, 5, 3, 5);
        if (p.getTicksLived() / 20 % 8 != 0) return;
        if (p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE) || Items.is(p.getInventory().getChestplate(), "plastron_refrigere")) return;
        int abyss = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            String id = Items.id(it);
            if (id != null && (id.endsWith("_abyssal") || id.endsWith("_abyssales") || Profondeurs.armorTier(id) >= 4)) abyss++;
        }
        if (abyss >= 4) return;
        p.damage(2);
        p.sendActionBar(Txt.c("§6La chaleur te brûle... §7(Infusion glacée, Plastron réfrigéré)"));
    }

    /** Bonus de panoplie dans les strates : 4 pièces d'acier ou abyssales = Résistance I, 4 abyssales = + Régénération I. */
    private void armorSet(Player p) {
        int steel = 0, abyss = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            String id = Items.id(it);
            if (id == null) continue;
            if (id.endsWith("_abyssal") || id.endsWith("_abyssales")) {
                abyss++;
                steel++;
            } else if (id.equals("casque_filtrant") || id.endsWith("_acier")) steel++;
        }
        if (steel >= 4) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, true));
        if (abyss >= 4) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
    }

    /** L'étoile du menu est toujours dans le sac dans l'Abysse, en un seul exemplaire (case 9 de la barre si elle est libre). */
    private void ensureStar(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (stripStars(p, true)) return;
        ItemStack star = Items.make("menu", 1);
        if (p.getInventory().getItem(8) == null) p.getInventory().setItem(8, star);
        else p.getInventory().addItem(star);
    }

    /**
     * Retire les étoiles en trop (sac, armure, main gauche, curseur, grille d'artisanat) ; avec keepOne, la première
     * trouvée reste. Renvoie vrai si une étoile a été gardée.
     */
    private boolean stripStars(Player p, boolean keepOne) {
        boolean kept = false;
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (!Items.is(c[i], "menu")) continue;
            if (keepOne && !kept) {
                kept = true;
                if (c[i].getAmount() > 1) {
                    c[i].setAmount(1);
                    inv.setItem(i, c[i]);
                }
            } else inv.setItem(i, null);
        }
        if (Items.is(p.getItemOnCursor(), "menu")) {
            if (keepOne && !kept) kept = true;
            else p.setItemOnCursor(null);
        }
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING)
            for (int i = 0; i < top.getSize(); i++) if (Items.is(top.getItem(i), "menu")) top.setItem(i, null);
        return kept;
    }

    private void sidebar(Player p) {
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
        Objective o = sb.getObjective("abysse");
        if (o != null) o.unregister();
        o = sb.registerNewObjective("abysse", Criteria.DUMMY, Txt.c("§5§lL'ABYSSE"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        PlayerData d = store.player(p);
        Company c = store.company(p);
        Gen.Strata s = Gen.strata(p.getWorld());
        String where = s != null ? "Strate " + s.n + " §8- §f" + s.name : p.getWorld().getName().equals(Worlds.BASE) ? "Concession" : "Bordgouffre";
        List<String> lines = new ArrayList<>();
        lines.add("§7 ");
        lines.add("§6⬥ §f" + Txt.num(Math.floor(c.money)) + " §7éclats");
        lines.add("§7" + where);
        if (c.plot >= 0) lines.add("§b⚡ §f" + Txt.num(Math.floor(c.energy)) + " §7énergie");
        lines.add("§7  ");
        lines.add("§eQuête");
        lines.add(cut(quests.objective(d)));
        String pr = quests.progress(d);
        if (!pr.isEmpty()) lines.add(pr + " ");
        lines.add("§7   ");
        lines.add("§fMineur §b" + quests.level(d, "mineur") + " §fIngé §b" + quests.level(d, "ingenieur") + " §fChasse §b" + quests.level(d, "chasseur"));
        lines.add("§8eemicraft.mine.fun");
        int score = lines.size();
        for (String l : lines) o.getScore(l).setScore(score--);
    }

    private static String cut(String s) {
        return s.length() > 34 ? s.substring(0, 33) + "…" : s;
    }

    private void leaveAbysse(Player p) {
        awaySnapshot(p);
        boards.remove(p.getUniqueId());
        if (p.isOnline()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ------------------------------------------------------------------ déplacements

    void goStrata(Player p, int n) {
        PlayerData d = store.player(p);
        if (d.unlocked < n) {
            p.sendMessage(Txt.PREFIX + "§cCette strate n'est pas encore ouverte pour toi.");
            return;
        }
        World w = worlds.strata(n);
        if (w == null) {
            if (reset.busy.contains(n)) p.sendMessage(Txt.PREFIX + "§eCette strate est en train d'être remise à neuf, reviens dans quelques secondes.");
            return;
        }
        Gen.Strata s = Gen.strata(n);
        p.teleport(s.camp(w));
        p.playSound(p, Sound.BLOCK_CHAIN_PLACE, 1, 0.6f);
        p.sendTitlePart(TitlePart.TITLE, Txt.c("§5" + s.name));
        p.sendTitlePart(TitlePart.SUBTITLE, Txt.c("§7Strate " + n));
        quests.onEnterStrata(p, n);
    }

    private void goHub(Player p) {
        World w = worlds.hub();
        if (w == null) return;
        p.teleport(worlds.hubSpawn());
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        store.player(p);
        if (Worlds.isAbysse(p.getWorld())) {
            Bukkit.getScheduler().runTaskLater(this, () -> sidebar(p), 20L);
            Bukkit.getScheduler().runTaskLater(this, () -> welcomeBack(p), 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Company c = store.companyOf(e.getPlayer().getUniqueId());
        if (c != null) c.lastActive = System.currentTimeMillis();
        if (Worlds.isAbysse(e.getPlayer().getWorld())) awaySnapshot(e.getPlayer());
        boards.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean from = Worlds.isAbysse(e.getFrom()), to = Worlds.isAbysse(p.getWorld());
        if (from && !to) leaveAbysse(p);
        if (to) {
            if (!from) {
                p.sendMessage(Txt.PREFIX + "§7Bienvenue dans §5l'Abysse§7. " + (store.player(p).quest <= 0 ? "§eVa parler à Gaspard, " + GASPARD_WHERE + "." : "§f/abysse §7pour le menu."));
                Bukkit.getScheduler().runTaskLater(this, () -> welcomeBack(p), 20L);
            }
            if (p.getWorld().getName().equals(Worlds.BASE)) {
                Company c = store.companyOf(p.getUniqueId());
                if (c != null) Bukkit.getScheduler().runTaskLater(this, () -> machines.relink(c), 20L);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        Player p = e.getPlayer();
        String w = p.getWorld().getName();
        if (w.equals(Worlds.HUB) || w.equals(Worlds.BASE)) {
            if (e.getTo().getY() < 40) worlds.fall(p);
        } else if (Gen.strata(w) != null) {
            Long t = flares.get(p.getUniqueId());
            if (t != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ())) {
                flares.remove(p.getUniqueId());
                p.sendActionBar(Txt.c("§cRemontée annulée : tu as bougé."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        World w = b.getWorld();
        if (!Worlds.isAbysse(w)) return;
        Machine m = store.machineAt(w.getName(), b.getX(), b.getY(), b.getZ());
        if (m != null) {
            Company c = store.owner(m);
            if (!admin(p) && (c == null || !c.members.contains(p.getUniqueId()))) {
                e.setCancelled(true);
                p.sendMessage(Txt.PREFIX + "§cCette machine appartient à une autre Compagnie.");
                return;
            }
            e.setDropItems(false);
            machines.breakMachine(p, m);
            return;
        }
        if (!canBuild(p, b)) {
            e.setCancelled(true);
            return;
        }
        if (w.getName().equals(Worlds.BASE) && Machines.isBelt(b)) {
            e.setDropItems(false);
            w.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), Items.make("convoyeur", 1));
            Company owner = worlds.plotAt(b.getLocation());
            if (owner != null) Bukkit.getScheduler().runTaskLater(this, () -> machines.relink(owner), 2L);
            return;
        }
        Gen.Strata s = Gen.strata(w);
        if (s == null) return;
        if (s.filonOre(b.getType()) != null && !admin(p)) {
            e.setCancelled(true);
            p.sendMessage(Txt.PREFIX + "§7Un filon s'exploite avec une §cForeuse §7(Margaux, ou l'Assembleur).");
            return;
        }
        String ore = Gen.ores(s.n).get(b.getType());
        if (ore == null) return;
        int hard = Gen.hardness(s.n, b.getType());
        if (hard > 0 && Gen.pickTier(Items.id(p.getInventory().getItemInMainHand())) < hard && !admin(p)) {
            e.setCancelled(true);
            p.sendActionBar(Txt.c("§cTrop dur : il te faut " + Gen.pickName(hard) + "."));
            p.playSound(p, Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
            return;
        }
        e.setDropItems(false);
        int n = b.getType() == Material.SHROOMLIGHT ? 1 + random.nextInt(2) : 1;
        int lvl = quests.level(store.player(p), "mineur");
        double dbl = lvl * 0.004 + (Items.is(p.getInventory().getItemInMainHand(), "pioche_abyssale") ? 0.15 : 0);
        if (random.nextDouble() < dbl) n *= 2;
        else {
            int clover = Items.slot(p, "trefle");
            if (clover >= 0 && random.nextInt(4) == 0) {
                n *= 2;
                Items.consume(p, clover);
                p.sendActionBar(Txt.c("§aTrèfle du prospecteur : minerai doublé !"));
            }
        }
        ItemStack drop = Items.make(ore, n);
        // le minerai va directement dans le sac (aux pieds du joueur si le sac est plein)
        for (ItemStack rest : p.getInventory().addItem(drop).values()) w.dropItemNaturally(p.getLocation(), rest);
        p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.6f);
        quests.onMine(p, ore, n);
        regen.mined(b, b.getType());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        String id = Items.id(e.getItemInHand());
        if ("convoyeur".equals(id)) {
            if (!b.getWorld().getName().equals(Worlds.BASE) || !canBuild(p, b)) {
                if (!b.getWorld().getName().equals(Worlds.BASE)) p.sendMessage(Txt.PREFIX + "§cLes convoyeurs se posent dans ta Concession.");
                e.setCancelled(true);
                return;
            }
            // sens = là où regarde le joueur ; le bloc, lui, « regarde » le joueur (comme en vanilla)
            org.bukkit.block.data.Directional dir = (org.bukkit.block.data.Directional) b.getBlockData();
            dir.setFacing(p.getFacing().getOppositeFace());
            b.setBlockData(dir, false);
            Company owner = worlds.plotAt(b.getLocation());
            if (owner != null) Bukkit.getScheduler().runTaskLater(this, () -> machines.relink(owner), 2L);
            return;
        }
        if (id != null && Items.def(id) != null) {
            // seules les machines se posent : lampe, laine (filtre), paratonnerre (bobine)... restent des objets
            if (Items.def(id).cat() != Items.Cat.MACHINE || !Worlds.isAbysse(b.getWorld()) || !machines.place(p, b, id)) e.setCancelled(true);
            return;
        }
        if (Worlds.isAbysse(b.getWorld()) && !canBuild(p, b)) e.setCancelled(true);
        if (Worlds.isAbysse(b.getWorld()) && b.getWorld().getName().equals(Worlds.BASE) && b.getType() == Material.HOPPER) {
            Company c = worlds.plotAt(b.getLocation());
            if (c != null) Bukkit.getScheduler().runTaskLater(this, () -> machines.relink(c), 2L);
        }
    }

    /** Construire : jamais à Bordgouffre (sauf admin en créatif), dans sa parcelle à la Concession, partout en strate sauf camp et antre. */
    private boolean canBuild(Player p, Block b) {
        World w = b.getWorld();
        if (admin(p) && p.getGameMode() == GameMode.CREATIVE) return true;
        if (w.getName().equals(Worlds.HUB)) return false;
        if (w.getName().equals(Worlds.BASE)) {
            boolean ok = worlds.inPlot(store.companyOf(p.getUniqueId()), b.getLocation());
            if (ok) {
                Company c = store.companyOf(p.getUniqueId());
                if (b.getType() == Material.HOPPER || c != null) Bukkit.getScheduler().runTaskLater(this, () -> machines.relink(c), 2L);
            } else p.sendActionBar(Txt.c("§cHors de ta Concession."));
            return ok;
        }
        Gen.Strata s = Gen.strata(w);
        if (s != null && (s.nearCamp(b.getX(), b.getZ(), 12) || s.nearLair(b.getX(), b.getZ(), Gen.LAIR_R + 2))) {
            p.sendActionBar(Txt.c("§cZone protégée."));
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!Worlds.isAbysse(p.getWorld()) || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = e.getItem();
        String id = Items.id(hand);
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null) {
            Machine m = store.machineAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (m != null && !p.isSneaking()) {
                e.setCancelled(true);
                Company c = store.owner(m);
                if (!admin(p) && (c == null || !c.members.contains(p.getUniqueId()))) {
                    p.sendMessage(Txt.PREFIX + "§7Machine de §f" + (c == null ? "?" : c.name) + "§7.");
                    return;
                }
                if (m.type.equals("foreuse") && "kit_reparation".equals(id)) machines.repair(p, m);
                else if (m.type.equals("foreuse") && "tete_forage".equals(id)) machines.upgrade(p, m);
                else if (m.type.equals("foreuse") && id != null && id.startsWith("tete_forage_")) machines.upgrade(p, m, id);
                else machines.open(p, m);
                return;
            }
            if (b.getType() == Material.BELL) {
                e.setCancelled(true);
                if (p.getWorld().getName().equals(Worlds.HUB)) npcs.lift(p);
                else if (Gen.strata(p.getWorld()) != null) {
                    p.playSound(p, Sound.BLOCK_BELL_USE, 1, 1);
                    goHub(p);
                }
                return;
            }
            Gen.Strata s = Gen.strata(p.getWorld());
            if (s != null && b.getType() == Material.REINFORCED_DEEPSLATE && s.nearLair(b.getX(), b.getZ(), 3)) {
                e.setCancelled(true);
                mobs.wake(p, s);
                return;
            }
            if (p.getWorld().getName().equals(Worlds.BASE) && !admin(p) && b.getState(false) instanceof org.bukkit.inventory.InventoryHolder) {
                Company owner = worlds.plotAt(b.getLocation());
                if (owner != null && !owner.members.contains(p.getUniqueId())) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && b != null && Gen.strata(p.getWorld()) != null && b.getType() == regen.placeholder()) {
            long left = regen.left(b);
            if (left >= 0) p.sendActionBar(Txt.c("§7Minerai épuisé : il repousse dans §f" + Txt.time(left)));
        }
        if ("menu".equals(id) && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
            e.setCancelled(true);
            menu(p);
            return;
        }
        if (id == null || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (id.equals("menu")) {
            e.setCancelled(true);
            menu(p);
        } else if (id.equals("page_carnet")) {
            e.setCancelled(true);
            readPage(p, hand);
        } else if (id.equals("fusee")) {
            e.setCancelled(true);
            flare(p);
        } else if (id.equals("dynamite")) {
            e.setCancelled(true);
            dynamite(p);
        } else if (id.equals("larme_soin")) {
            e.setCancelled(true);
            tear(p);
        } else if (id.equals("boussole_retour")) {
            e.setCancelled(true);
            backToDeath(p);
        } else if (id.equals("totem_garde")) {
            e.setCancelled(true);
            p.sendMessage(Txt.PREFIX + "§7Garde-le dans ton sac : il protège ton butin si tu meurs (§f" + Items.uses(hand) + " §7usages).");
        }
    }

    private void readPage(Player p, ItemStack hand) {
        String ref = Items.pageRef(hand);
        if (ref == null) return;
        PlayerData d = store.player(p);
        String[] sn = ref.split(":");
        int s = Integer.parseInt(sn[0]), n = Integer.parseInt(sn[1]);
        hand.setAmount(hand.getAmount() - 1);
        p.sendMessage("");
        p.sendMessage("§f§lCarnet de Varenne §7- " + Quests.pageTitle(s, n));
        p.sendMessage("§7§o« " + Quests.page(s, n) + " »");
        if (d.pages.add(ref)) {
            p.sendMessage(Txt.PREFIX + "§aPage rangée dans ton journal §7(" + d.pages.size() + " / " + Quests.PAGES * Gen.STRATAS.length + ")");
            quests.onPage(p);
        } else p.sendMessage(Txt.PREFIX + "§7Tu avais déjà cette page.");
        p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
    }

    /** Armes, outils et armures vanilla : dans l'Abysse, ils viennent des boutiques et des machines, jamais d'un établi. */
    static boolean vanillaGear(Material m) {
        return org.bukkit.Tag.ITEMS_SWORDS.isTagged(m) || org.bukkit.Tag.ITEMS_PICKAXES.isTagged(m) || org.bukkit.Tag.ITEMS_AXES.isTagged(m)
                || org.bukkit.Tag.ITEMS_SHOVELS.isTagged(m) || org.bukkit.Tag.ITEMS_HOES.isTagged(m) || org.bukkit.Tag.ITEMS_SPEARS.isTagged(m)
                || org.bukkit.Tag.ITEMS_HEAD_ARMOR.isTagged(m) || org.bukkit.Tag.ITEMS_CHEST_ARMOR.isTagged(m)
                || org.bukkit.Tag.ITEMS_LEG_ARMOR.isTagged(m) || org.bukkit.Tag.ITEMS_FOOT_ARMOR.isTagged(m)
                || m == Material.BOW || m == Material.CROSSBOW || m == Material.SHIELD || m == Material.MACE || m == Material.TRIDENT;
    }

    @EventHandler
    public void onCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p) || !Worlds.isAbysse(p.getWorld())) return;
        ItemStack res = e.getInventory().getResult();
        if (res == null || !vanillaGear(res.getType())) return;
        e.getInventory().setResult(null);
        p.sendActionBar(Txt.c("§cDans l'Abysse, l'équipement s'achète (Bastien) ou se fabrique à l'Assembleur."));
    }

    /** Pas de table de forgeron vanilla non plus (amélioration en netherite, garnitures). */
    @EventHandler
    public void onSmith(org.bukkit.event.inventory.PrepareSmithingEvent e) {
        if (e.getView().getPlayer() instanceof Player p && Worlds.isAbysse(p.getWorld())) e.setResult(null);
    }

    /** Plats de la Cuisine : la nourriture vanilla s'applique, le plugin ajoute l'effet. */
    @EventHandler(ignoreCancelled = true)
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        String id = Items.id(e.getItem());
        if (id == null) return;
        switch (id) {
            case "ration" -> Bukkit.getScheduler().runTask(this, () -> p.setSaturation(Math.min(20, p.getSaturation() + 6)));
            case "ragout" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 3 * 1200, 0));
            case "cafe" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3 * 1200, 0));
            case "infusion_glacee" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 4 * 1200, 0));
                p.sendActionBar(Txt.c("§bLa chaleur ne te touche plus pendant 4 min."));
            }
            case "elixir_silence", "serum_gravite" -> Profondeurs.drink(p, id);
            case "soupe_spores" -> {
                sporeShield.put(p.getUniqueId(), System.currentTimeMillis() + 5 * 60_000L);
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 5 * 1200, 0));
                p.removePotionEffect(PotionEffectType.POISON);
                p.sendActionBar(Txt.c("§2Les spores ne te font plus rien pendant 5 min."));
            }
            default -> {
            }
        }
    }

    private static final java.util.Set<Material> ROCK = java.util.EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.TUFF,
            Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT, Material.MAGMA_BLOCK,
            Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.GRAVEL, Material.DIRT, Material.ANDESITE, Material.GRANITE, Material.DIORITE,
            Material.CALCITE, Material.END_STONE, Material.END_STONE_BRICKS);

    /** Dynamite : roche et minerai dans un rayon de 2 autour du joueur, minerai ramassé (et qui repousse). */
    private void dynamite(Player p) {
        Gen.Strata s = Gen.strata(p.getWorld());
        if (s == null) {
            p.sendMessage(Txt.PREFIX + "§7La dynamite ne sert que dans les strates.");
            return;
        }
        Block c = p.getLocation().getBlock();
        if (s.nearCamp(c.getX(), c.getZ(), 14) || s.nearLair(c.getX(), c.getZ(), Gen.LAIR_R + 4)) {
            p.sendActionBar(Txt.c("§cPas de dynamite près du camp ou de l'antre."));
            return;
        }
        int slot = p.getInventory().getHeldItemSlot();
        ItemStack hand = p.getInventory().getItemInMainHand();
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItem(slot, hand.getAmount() <= 0 ? null : hand);
        Map<String, Integer> got = new HashMap<>();
        int lvl = quests.level(store.player(p), "mineur");
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -1; dy <= 3; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + (dy - 1) * (dy - 1) + dz * dz > 6) continue;
                    Block b = c.getRelative(dx, dy, dz);
                    if (b.getY() <= Gen.FLOOR + 1 || b.getY() >= Gen.CEIL - 1) continue;
                    String ore = Gen.ores(s.n).get(b.getType());
                    if (ore != null && Gen.hardness(s.n, b.getType()) > 0) continue; // la dynamite ne fend pas les minerais durs
                    if (ore != null) {
                        int n = b.getType() == Material.SHROOMLIGHT ? 1 + random.nextInt(2) : 1;
                        if (random.nextDouble() < lvl * 0.004) n *= 2;
                        got.merge(ore, n, Integer::sum);
                        regen.mined(b, b.getType());
                        b.setType(Material.AIR, false);
                    } else if (ROCK.contains(b.getType())) b.setType(Material.AIR, false);
                }
        p.getWorld().spawnParticle(Particle.EXPLOSION, c.getLocation().add(0.5, 1, 0.5), 3, 1, 1, 1);
        p.getWorld().playSound(c.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1, 1.1f);
        for (Map.Entry<String, Integer> e : got.entrySet()) {
            for (ItemStack rest : p.getInventory().addItem(Items.make(e.getKey(), e.getValue())).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            quests.onMine(p, e.getKey(), e.getValue());
        }
        if (!got.isEmpty()) p.sendActionBar(Txt.c("§aBoum ! §7" + got.values().stream().mapToInt(Integer::intValue).sum() + " minerais ramassés."));
    }

    private void tear(Player p) {
        long now = System.currentTimeMillis();
        Long last = tears.get(p.getUniqueId());
        if (last != null && now - last < 60_000) {
            p.sendActionBar(Txt.c("§cLa larme se recharge encore " + (60 - (now - last) / 1000) + " s."));
            return;
        }
        int slot = Items.slot(p, "larme_soin");
        if (slot < 0) return;
        tears.put(p.getUniqueId(), now);
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.removePotionEffect(PotionEffectType.POISON);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4);
        p.playSound(p, Sound.ITEM_TOTEM_USE, 0.4f, 1.6f);
        int left = Items.consume(p, slot);
        if (left > 0) p.sendActionBar(Txt.c("§dTu es soigné. §7(" + left + " usages restants)"));
    }

    private void backToDeath(Player p) {
        Death d = deaths.get(p.getUniqueId());
        if (d == null || System.currentTimeMillis() - d.time() > 20 * 60_000L) {
            p.sendMessage(Txt.PREFIX + "§7La boussole ne trouve aucune mort récente (moins de 20 min) dans une strate.");
            return;
        }
        if (store.player(p).unlocked < d.strata()) return;
        World w = worlds.strata(d.strata());
        int slot = Items.slot(p, "boussole_retour");
        if (w == null || slot < 0) return;
        p.teleport(new Location(w, d.x(), d.y(), d.z(), p.getLocation().getYaw(), p.getLocation().getPitch()));
        p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 0.8f);
        deaths.remove(p.getUniqueId());
        int left = Items.consume(p, slot);
        if (left > 0) p.sendMessage(Txt.PREFIX + "§bTe voilà où tu es tombé. §7(" + left + " usages restants)");
    }

    private void flare(Player p) {
        if (Gen.strata(p.getWorld()) == null) {
            p.sendMessage(Txt.PREFIX + "§7Une fusée ne sert que dans les strates.");
            return;
        }
        if (flares.containsKey(p.getUniqueId())) return;
        Bourse.take(p, "fusee", 1);
        flares.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendActionBar(Txt.c("§cFusée tirée : ne bouge plus pendant 5 s..."));
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 2, 0), 40, 0.2, 2, 0.2, 0.05);
        p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);
        UUID u = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (flares.remove(u) == null || !p.isOnline() || Gen.strata(p.getWorld()) == null) return;
            p.playSound(p, Sound.BLOCK_CHAIN_BREAK, 1, 0.6f);
            goHub(p);
        }, 100L);
    }

    @EventHandler
    public void onEntityClick(PlayerInteractEntityEvent e) {
        String id = npcs.npcId(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) npcs.click(e.getPlayer(), id);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu m) {
            m.handle(e);
            return;
        }
        // l'étoile du menu ne bouge jamais ; un clic dessus dans le sac ouvre le menu
        boolean star = Items.is(e.getCurrentItem(), "menu") || Items.is(e.getCursor(), "menu")
                || (e.getHotbarButton() >= 0 && Items.is(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()), "menu"))
                || (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND && Items.is(e.getWhoClicked().getInventory().getItemInOffHand(), "menu"));
        if (!star) return;
        e.setCancelled(true);
        if (e.getWhoClicked() instanceof Player p && Items.is(e.getCurrentItem(), "menu") && Worlds.isAbysse(p.getWorld()))
            Bukkit.getScheduler().runTask(this, () -> menu(p));
    }

    @EventHandler
    public void onSwapStar(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (Items.is(e.getMainHandItem(), "menu") || Items.is(e.getOffHandItem(), "menu")) e.setCancelled(true);
    }

    @EventHandler
    public void onDropStar(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (Items.is(e.getItemDrop().getItemStack(), "menu")) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Menu || Items.is(e.getOldCursor(), "menu")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!Worlds.isAbysse(e.getEntity().getWorld())) return;
        // nos monstres et PNJ passent même si WorldGuard bloque les apparitions de plugins
        if (mobs.isAbysseMob(e.getEntity()) || npcs.npcId(e.getEntity()) != null) e.setCancelled(false);
        else if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) e.setCancelled(true);
        // (#57) renforts vanilla des zombies / noyés : sans marque de l'Abysse, ils n'avaient ni la vie ni le butin prévus
    }

    /** #57 : un monstre de l'Abysse ne prend jamais pour cible un autre monstre de l'Abysse. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() == null || !Worlds.isAbysse(e.getEntity().getWorld())) return;
        if (mobs.isAbysseMob(e.getEntity()) && !(e.getTarget() instanceof Player) && mobs.isAbysseMob(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity();
        if (!Worlds.isAbysse(v.getWorld())) return;
        Player attacker = e.getDamager() instanceof Player p ? p
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2 ? p2 : null;
        if (npcs.npcId(v) != null) {
            e.setCancelled(true);
            return;
        }
        if (v instanceof Player && attacker != null) {
            e.setCancelled(true);
            return;
        }
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        // #57 : les monstres de l'Abysse ne se blessent plus entre eux (flèches des archers et rafales des tourbillons
        // sur les noyés, qui ripostaient et les tuaient : vie des monstres de la strate 4 qui fondait toute seule)
        if (!(v instanceof Player) && mobs.isAbysseMob(v) && mobs.isAbysseMob(src) && src != v) {
            e.setCancelled(true);
            return;
        }
        if (v instanceof Player victim && mobs.isAbysseMob(src)) {
            mobs.onHitPlayer(src, victim, e);
            return;
        }
        if (attacker != null && mobs.isAbysseMob(v)) {
            int lvl = quests.level(store.player(attacker), "chasseur");
            e.setDamage(e.getDamage() * (1 + 0.005 * lvl) / mobs.toughness(v));
            mobs.onDamage(v, attacker, e.getFinalDamage());
            mobs.showHealth(attacker, v);
            double steal = Profondeurs.lifeSteal(Items.id(attacker.getInventory().getItemInMainHand()));
            if (steal > 0) {
                var max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                attacker.setHealth(Math.min(max == null ? 20 : max.getValue(), attacker.getHealth() + e.getFinalDamage() * steal));
            }
            if (Items.is(attacker.getInventory().getItemInMainHand(), "epee_abyssale")) {

                var max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                attacker.setHealth(Math.min(max == null ? 20 : max.getValue(), attacker.getHealth() + e.getFinalDamage() * 0.15));
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!Worlds.isAbysse(v.getWorld()) || v instanceof Player || !mobs.isAbysseMob(v)) return;
        mobs.onDeath(v, v.getKiller(), e.getDrops());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!Worlds.isAbysse(p.getWorld())) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        flares.remove(p.getUniqueId());
        Gen.Strata ds = Gen.strata(p.getWorld());
        if (ds == null) return;
        Location dl = p.getLocation();
        deaths.put(p.getUniqueId(), new Death(ds.n, dl.getX(), Math.max(dl.getY(), Gen.FLOOR + 2), dl.getZ(), System.currentTimeMillis()));
        // on perd le butin transporté, il reste au sol... sauf avec un Totem de garde
        Inventory inv = p.getInventory();
        boolean carries = false;
        for (ItemStack it : inv.getContents()) if (Items.lostOnDeath(it)) carries = true;
        int totem = carries ? Items.slot(p, "totem_garde") : -1;
        if (totem >= 0) {
            int left = Items.consume(p, totem);
            p.sendMessage(Txt.PREFIX + "§6Ton Totem de garde a protégé ton butin. §7(" + left + " usages restants)");
            return;
        }
        int lost = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!Items.lostOnDeath(it)) continue;
            p.getWorld().dropItemNaturally(p.getLocation(), it);
            lost += it.getAmount();
            inv.setItem(i, null);
        }
        if (lost > 0) p.sendMessage(Txt.PREFIX + "§cTu as perdu " + lost + " objets de butin. Ils sont restés là où tu es tombé.");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (Worlds.isAbysse(e.getPlayer().getWorld()) && worlds.hub() != null) e.setRespawnLocation(worlds.hubSpawn());
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        World w = e.getEntity().getWorld();
        if ((w.getName().equals(Worlds.HUB) || w.getName().equals(Worlds.BASE)) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (Worlds.isAbysse(e.getLocation().getWorld())) e.blockList().clear();
    }

    /** Butin d'un tonneau de campement (copie personnelle d'un joueur, voir {@link Campements}). */
    void fillBarrel(Gen.Strata s, Inventory inv, int h) {
        Random r = new Random(h);
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(Material.BREAD, 2 + r.nextInt(5)));
        loot.add(new ItemStack(Material.TORCH, 4 + r.nextInt(8)));
        loot.add(Items.make("charbon", 3 + r.nextInt(8)));
        loot.add(Items.make(s.n == 1 ? "minerai_cuivre" : "minerai_etain", 2 + r.nextInt(6)));
        if (r.nextInt(3) == 0) loot.add(Items.make("fusee", 1));
        if (r.nextInt(4) == 0) loot.add(Items.make(s.n == 1 ? "os_mineur" : "glande_spores", 2 + r.nextInt(3)));
        Profondeurs.barrelLoot(s, loot, r);
        for (ItemStack it : loot) inv.setItem(r.nextInt(inv.getSize()), it);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bourse")) {
            if (sender instanceof Player p) {
                if (!Worlds.isAbysse(p.getWorld())) p.sendMessage(Txt.PREFIX + "§7La bourse s'ouvre depuis l'Abysse (§f/abysse§7).");
                else bourse.open(p);
            }
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (admin(sender) && adminCommand(sender, sub, args)) return true;
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commandes console : reload, give, page, donner, debloquer, quete, skip, gardien, reset, bourse reset, build");
            return true;
        }
        boolean in = Worlds.isAbysse(p.getWorld());
        switch (sub) {
            case "" -> {
                if (!in) goHub(p);
                else menu(p);
            }
            case "aller", "ville" -> goHub(p);
            case "quitter" -> {
                if (!p.performCommand("lobby")) p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            case "base", "concession" -> {
                Company c = store.company(p);
                if (c.plot < 0) {
                    p.sendMessage(Txt.PREFIX + "§7Tu n'as pas encore de Concession : va voir §6Maître Lebrun §7à Bordgouffre.");
                    return true;
                }
                p.teleport(worlds.plotSpawn(c));
            }
            case "visiter" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                Company c = t == null ? null : store.companyOf(t.getUniqueId());
                if (c == null || c.plot < 0) {
                    p.sendMessage(Txt.PREFIX + "§cCe joueur n'a pas de Concession.");
                    return true;
                }
                p.teleport(worlds.plotSpawn(c));
                p.sendMessage(Txt.PREFIX + "§7Bienvenue chez §f" + c.name + "§7.");
            }
            case "remonter" -> {
                if (Gen.strata(p.getWorld()) == null) p.sendMessage(Txt.PREFIX + "§7Tu n'es pas dans une strate.");
                else if (Bourse.countIn(p, "fusee") > 0) flare(p);
                else p.sendMessage(Txt.PREFIX + "§7Remonte par la cloche du camp, ou tire une §cFusée de détresse§7.");
            }
            case "bourse" -> {
                if (in) bourse.open(p);
            }
            case "metiers", "jobs" -> quests.openJobs(p);
            case "journal", "quetes" -> quests.openJournal(p);
            case "carnets" -> npcs.carnets(p);
            case "recommencer", "reinitialiser" -> {
                if (!in) p.sendMessage(Txt.PREFIX + "§7Fais-le depuis l'Abysse (§f/abysse§7).");
                else resetConfirm(p, p);
            }
            case "guide", "aide" -> {
                if (args.length > 1 && Machines.TYPES.containsKey(args[1].toLowerCase())) guide.machine(p, args[1].toLowerCase());
                else guide.open(p);
            }
            case "brocante" -> {
                if (in) npcs.brocante(p);
            }
            case "eclats", "argent" -> p.sendMessage(Txt.PREFIX + "§7Ta Compagnie a " + Txt.money(store.company(p).money));
            case "payer" -> pay(p, args);
            case "compagnie", "groupe" -> company(p, args);
            case "tpa" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) tpaMenu(p);
                else tpa(p, t);
            }
            case "tpaccept", "tpaccepter" -> tpAnswer(p, true);
            case "tpdeny", "tprefuser" -> tpAnswer(p, false);
            default -> p.sendMessage(Txt.PREFIX + "§7/abysse [base|visiter|remonter|tpa|bourse|guide|brocante|metiers|journal|carnets|eclats|payer|compagnie|recommencer|quitter]");
        }
        return true;
    }

    /**
     * Menu de l'Abysse (étoile, /abysse), en lignes thématiques : se déplacer, progresser, commerce et Compagnie ;
     * Fermer au centre de la dernière ligne, Quitter l'Abysse à droite.
     */
    void menu(Player p) {
        Company c = store.company(p);
        PlayerData d = store.player(p);
        Menu m = new Menu(5, "§5L'Abysse");
        Gen.Strata here = Gen.strata(p.getWorld());
        // ligne 2 : se déplacer
        m.set(10, Txt.icon(Material.LODESTONE, "§5Bordgouffre", here != null ? "§7Remonter à la ville, tout de suite." : "§7La ville : PNJ, bourse, monte-charge.",
                "", "§eClic pour y aller"), (pl, t) -> {
            pl.closeInventory();
            goHub(pl);
        });
        m.set(12, Txt.icon(Material.GRASS_BLOCK, "§aMa Concession", c.plot >= 0 ? "§7Ton usine." : "§7Pas encore : va voir Maître Lebrun.", "", "§eClic pour y aller"), (pl, t) -> {
            pl.closeInventory();
            pl.performCommand("abysse base");
        });
        m.set(14, Txt.icon(Material.IRON_CHAIN, "§5Changer de strate", "§7Strates ouvertes : §f1 à " + Math.min(d.unlocked, Gen.STRATAS.length),
                "§7Le monte-charge de Léon, d'ici.", "", "§eClic pour choisir"), (pl, t) -> npcs.lift(pl));
        m.set(16, Txt.icon(Material.ENDER_PEARL, "§dRejoindre un joueur", "§7Demande à un joueur de l'Abysse", "§7de te téléporter près de lui.", "", "§eClic pour choisir"),
                (pl, t) -> tpaMenu(pl));
        // ligne 3 : progresser
        m.set(19, Txt.icon(Material.WRITABLE_BOOK, "§eJournal et quêtes", "§7" + Bourse.strip(quests.objective(d)), "§7" + Bourse.strip(quests.progress(d)), "", "§eClic pour l'ouvrir"),
                (pl, t) -> quests.openJournal(pl));
        m.set(21, Txt.icon(Material.EXPERIENCE_BOTTLE, "§bMétiers et niveaux", "§7Mineur §f" + quests.level(d, "mineur") + " §7- Ingénieur §f" + quests.level(d, "ingenieur")
                + " §7- Chasseur §f" + quests.level(d, "chasseur"), "", "§eClic : XP, bonus et paliers"), (pl, t) -> quests.openJobs(pl));
        m.set(23, Txt.icon(Material.FILLED_MAP, "§fCarnets de Varenne", "§7Pages trouvées : §f" + d.pages.size() + " / " + Quests.PAGES * Gen.STRATAS.length, "", "§eClic pour les relire"),
                (pl, t) -> npcs.carnets(pl));
        m.set(25, Txt.icon(Material.KNOWLEDGE_BOOK, "§eGuide de l'usine", "§7A quoi sert chaque machine, son menu,", "§7les convoyeurs et l'énergie.", "", "§eClic pour l'ouvrir"),
                (pl, t) -> guide.open(pl));
        // ligne 4 : commerce et Compagnie
        m.set(28, Txt.icon(Material.EMERALD, "§5Bourse", "§7Cours, ventes, commandes du jour.", "", "§eClic pour l'ouvrir"), (pl, t) -> bourse.open(pl));
        m.set(30, Txt.icon(Material.GOLD_NUGGET, "§6" + Txt.num(Math.floor(c.money)) + " éclats", "§7Les éclats de ta Compagnie.",
                "§7Ventes automatiques (quai) : " + Txt.money(c.autoSold), "§7Objets produits par l'usine : §f" + Txt.num(c.produced),
                "", "§eClic : vendre à la bourse", "§8Donner : /abysse payer <joueur> <montant>"), (pl, t) -> bourse.open(pl));
        m.set(32, Txt.icon(Material.PLAYER_HEAD, "§f" + c.name, "§7Membres : §f" + npcs.members(c), "§7Éclats : " + Txt.money(c.money), "",
                "§eClic : membres, inviter, quitter"), (pl, t) -> companyMenu(pl));
        m.set(34, Txt.icon(Material.TOTEM_OF_UNDYING, "§eBrocante de Prosper", "§7Totem de garde, larme de guérison,", "§7trèfle, boussole, lampe...", "", "§eClic pour l'ouvrir"),
                (pl, t) -> npcs.brocante(pl));
        m.nav(null, null);
        m.set(44, Txt.icon(Material.OAK_DOOR, "§cQuitter l'Abysse", "§7Retour au lobby du serveur.", "§7Ta progression est gardée."), (pl, t) -> {
            pl.closeInventory();
            pl.performCommand("abysse quitter");
        });
        m.open(p);
    }

    // ------------------------------------------------------------------ menu de la Compagnie

    private ItemStack head(UUID u, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(u));
            sm.displayName(Txt.c(name));
            sm.lore(Txt.lines(lore));
            head.setItemMeta(sm);
        }
        return head;
    }

    /** Membres (tête et rôle), éclats, inviter un joueur de l'Abysse, invitation reçue, quitter (avec confirmation). */
    void companyMenu(Player p) {
        Company c = store.company(p);
        boolean chief = c.leader.equals(p.getUniqueId());
        int max = getConfig().getInt("compagnie-max", 4);
        Menu m = new Menu(4, "§5Compagnie §8- " + c.name);
        m.set(4, Txt.icon(Material.GOLD_BLOCK, "§f" + c.name, "§7Éclats : " + Txt.money(c.money), "§7Membres : §f" + c.members.size() + " / " + max,
                "§7Concession : §f" + (c.plot >= 0 ? "oui" : "pas encore (Maître Lebrun)"), "§7Machines : §f" + (c.machines.isEmpty() ? "aucune" : c.machines.size()),
                "", "§8Renommer (chef) : /abysse compagnie nom <nom>"));
        // membres : ligne 2, centrés
        List<UUID> mem = new ArrayList<>(c.members);
        int n = Math.min(mem.size(), 9), start = 9 + (9 - n) / 2;
        for (int i = 0; i < n; i++) {
            UUID u = mem.get(i);
            String name = store.player(u).name;
            Player online = Bukkit.getPlayer(u);
            boolean lead = c.leader.equals(u);
            List<String> l = new ArrayList<>();
            l.add(lead ? "§6Chef de la Compagnie" : "§7Membre");
            l.add(online == null ? "§8Hors ligne" : Worlds.isAbysse(online.getWorld()) ? "§aDans l'Abysse §8(" + where(online) + ")" : "§eConnecté, hors de l'Abysse");
            boolean canKick = chief && !u.equals(p.getUniqueId()) && online != null;
            if (canKick) l.addAll(List.of("", "§cMaj + clic : l'exclure"));
            m.set(start + i, head(u, (lead ? "§6" : "§f") + name, l), !canKick ? null : (pl, t) -> {
                if (!t.isShiftClick()) return;
                Menu.confirm(pl, "§4Exclure " + name + " ?", Txt.icon(Material.PLAYER_HEAD, "§fExclure §c" + name, "§7Il repart avec sa propre Compagnie.",
                                "§7Les machines et les éclats restent ici."),
                        "§cOui, l'exclure", List.of(), (pp, tt) -> {
                            pp.performCommand("abysse compagnie expulser " + name);
                            companyMenu(pp);
                        }, (pp, tt) -> companyMenu(pp));
            });
        }
        // invitation reçue
        String inv = invites.get(p.getUniqueId());
        Company from = inv == null ? null : store.companies.get(inv);
        if (from != null && from != c)
            m.set(22, Txt.icon(Material.WRITABLE_BOOK, "§aInvitation : §f" + from.name, "§7Membres : §f" + npcs.members(from),
                    "§8Si tu étais seul, tes machines te sont", "§8rendues et tes éclats suivent.", "", "§eClic : accepter"), (pl, t) -> {
                pl.performCommand("abysse compagnie accepter");
                companyMenu(pl);
            });
        // inviter / quitter
        if (chief && c.members.size() < max)
            m.set(20, Txt.icon(Material.ENDER_EYE, "§aInviter un joueur", "§7Un joueur présent dans l'Abysse.", "", "§eClic pour choisir"), (pl, t) -> inviteMenu(pl));
        else
            m.set(20, Txt.icon(Material.GRAY_DYE, "§7Inviter un joueur", chief ? "§7Ta Compagnie est complète (" + max + ")." : "§7Seul le chef peut inviter."));
        if (c.members.size() > 1)
            m.set(24, Txt.icon(Material.IRON_DOOR, "§cQuitter la Compagnie", "§7Tu fondes ta propre Compagnie,", "§7sans machine ni éclat.", "", "§eClic (avec confirmation)"),
                    (pl, t) -> Menu.confirm(pl, "§4Quitter la Compagnie ?", Txt.icon(Material.IRON_DOOR, "§cQuitter §f" + c.name,
                                    "§7Les machines, la Concession et les", "§7éclats restent aux autres membres.", "§7Tu repars avec une Compagnie vide."),
                            "§cOui, quitter", List.of(), (pp, tt) -> {
                                pp.performCommand("abysse compagnie quitter");
                                companyMenu(pp);
                            }, (pp, tt) -> companyMenu(pp)));
        else m.set(24, Txt.icon(Material.GRAY_DYE, "§7Quitter la Compagnie", "§7Tu es seul dans ta Compagnie."));
        m.nav((pl, t) -> menu(pl), "Menu de l'Abysse");
        m.open(p);
    }

    /** Têtes des joueurs présents dans l'Abysse hors de la Compagnie : clic = invitation. */
    private void inviteMenu(Player p) {
        Company c = store.company(p);
        Menu m = new Menu(6, "§aInviter un joueur");
        int slot = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t == p || c.members.contains(t.getUniqueId()) || !Worlds.isAbysse(t.getWorld()) || t.getGameMode() == GameMode.SPECTATOR
                    || t.hasMetadata("vanished") || slot >= 45) continue;
            Company other = store.companyOf(t.getUniqueId());
            m.set(slot++, head(t.getUniqueId(), "§f" + t.getName(), List.of("§7" + where(t), "§7Compagnie : §f" + (other == null ? "aucune" : other.name), "", "§eClic : l'inviter")),
                    (pl, ty) -> {
                        pl.performCommand("abysse compagnie inviter " + t.getName());
                        companyMenu(pl);
                    });
        }
        if (slot == 0) m.set(22, Txt.icon(Material.PAPER, "§7Personne à inviter", "§7Il faut être dans l'Abysse."));
        m.nav((pl, t) -> companyMenu(pl), "Compagnie");
        m.open(p);
    }

    // ------------------------------------------------------------------ tout recommencer

    /** Écran de confirmation : ce qui sera effacé, Annuler / Tout effacer. */
    void resetConfirm(Player viewer, Player target) {
        boolean self = viewer == target;
        Company c = store.company(target);
        boolean alone = c.members.size() <= 1;
        long drills = c.machines.values().stream().filter(m -> m.type.equals("foreuse")).count();
        Menu m = new Menu(3, "§4Tout recommencer ?");
        List<String> l = new ArrayList<>();
        l.add("§7Sera effacé pour §f" + target.getName() + "§7 :");
        l.add("§c- quêtes, strates ouvertes, carnets");
        l.add("§c- métiers (Mineur, Ingénieur, Chasseur)");
        l.add("§c- tout l'inventaire de l'Abysse");
        if (alone) {
            l.add("§c- la Compagnie et ses éclats (" + Txt.num(Math.floor(c.money)) + " ⬥)");
            l.add("§c- la Concession et toutes ses machines");
            l.add("§c- les " + drills + " foreuse(s) dans les strates");
        } else l.add("§e- quitte « " + c.name + " » (elle reste aux autres membres)");
        l.add("");
        l.add("§7On repart de zéro avec une nouvelle Compagnie.");
        l.add("§4§lIrréversible.");
        m.set(13, Txt.icon(Material.TNT, "§4" + (self ? "Recommencer l'Abysse" : "Remettre " + target.getName() + " à zéro"), l));
        m.set(11, Menu.model(Txt.icon(Material.LIME_CONCRETE, "§aAnnuler", "§7Rien n'est effacé."), Menu.UI_NO), (pl, t) -> pl.closeInventory());
        m.set(15, Menu.yes(Material.RED_CONCRETE, "§4§lOui, tout effacer", "§7Maj + clic pour confirmer."), (pl, t) -> {
            if (t != org.bukkit.event.inventory.ClickType.SHIFT_LEFT && t != org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                pl.sendMessage(Txt.PREFIX + "§7Maj + clic sur le bouton rouge pour confirmer.");
                return;
            }
            pl.closeInventory();
            if (!target.isOnline() || !Worlds.isAbysse(target.getWorld())) {
                pl.sendMessage(Txt.PREFIX + "§c" + target.getName() + " n'est plus dans l'Abysse : rien n'a été effacé.");
                return;
            }
            resetPlayer(target);
            if (!self) pl.sendMessage(Txt.PREFIX + "§7" + target.getName() + " a été remis à zéro dans l'Abysse.");
            getLogger().info(target.getName() + " remis a zero dans l'Abysse (par " + pl.getName() + ").");
        });
        m.open(viewer);
    }

    /** Remet un joueur (présent dans l'Abysse) à zéro. Seul dans sa Compagnie : elle disparaît avec ses machines et foreuses. */
    void resetPlayer(Player t) {
        Company c = store.companyOf(t.getUniqueId());
        if (c != null) {
            if (c.members.size() <= 1) {
                for (Machine m : new ArrayList<>(c.machines.values())) {
                    machines.removeLabel(m);
                    World w = Bukkit.getWorld(m.world);
                    if (w != null) w.getBlockAt(m.x, m.y, m.z).setType(Material.AIR);
                }
                c.machines.clear();
                npcs.removePlot(c.id);
                store.companies.remove(c.id);
            } else {
                c.members.remove(t.getUniqueId());
                if (c.leader.equals(t.getUniqueId())) c.leader = c.members.get(0);
            }
        }
        PlayerData d = store.player(t);
        d.company = null;
        d.quest = 0;
        d.questProgress = 0;
        d.unlocked = 1;
        d.kills = 0;
        d.kitGiven = false;
        d.jobXp.clear();
        d.pages.clear();
        d.pageRewards.clear();
        d.guides.clear();
        store.company(t);
        t.getInventory().clear();
        t.setItemOnCursor(null);
        deaths.remove(t.getUniqueId());
        tpRequests.remove(t.getUniqueId());
        goHub(t);
        store.saveAll();
        t.sendMessage(Txt.PREFIX + "§aTout est effacé : bienvenue de nouveau à Bordgouffre. §eVa voir Gaspard, " + GASPARD_WHERE + ".");
        t.playSound(t, Sound.BLOCK_BEACON_DEACTIVATE, 1, 0.8f);
    }

    // ------------------------------------------------------------------ téléportation vers un joueur

    /** Demande en attente : cible → demandeur et heure. */
    private record TpRequest(UUID from, long time) {
    }

    private final Map<UUID, TpRequest> tpRequests = new HashMap<>();

    /** Têtes des joueurs présents dans l'Abysse, clic = demande de téléportation. */
    private void tpaMenu(Player p) {
        Menu m = new Menu(6, "§dRejoindre un joueur");
        int slot = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t == p || !Worlds.isAbysse(t.getWorld()) || t.getGameMode() == GameMode.SPECTATOR || t.hasMetadata("vanished") || slot >= 45) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                sm.setOwningPlayer(t);
                sm.displayName(Txt.c("§f" + t.getName()));
                sm.lore(Txt.lines(List.of("§7" + where(t), "", "§eClic : demander à le rejoindre")));
                head.setItemMeta(sm);
            }
            m.set(slot++, head, (pl, ty) -> {
                pl.closeInventory();
                tpa(pl, t);
            });
        }
        if (slot == 0) m.set(22, Txt.icon(Material.PAPER, "§7Personne d'autre dans l'Abysse"));
        m.nav((pl, ty) -> menu(pl), "Menu de l'Abysse");
        m.open(p);
    }

    private String where(Player t) {
        Gen.Strata s = Gen.strata(t.getWorld());
        return s != null ? "Strate " + s.n + " - " + s.name : t.getWorld().getName().equals(Worlds.BASE) ? "Concessions" : "Bordgouffre";
    }

    private void tpa(Player p, Player t) {
        if (t == p || t.hasMetadata("vanished")) return;
        if (!Worlds.isAbysse(p.getWorld()) || !Worlds.isAbysse(t.getWorld())) {
            p.sendMessage(Txt.PREFIX + "§cVous devez être tous les deux dans l'Abysse.");
            return;
        }
        tpRequests.put(t.getUniqueId(), new TpRequest(p.getUniqueId(), System.currentTimeMillis()));
        p.sendMessage(Txt.PREFIX + "§7Demande envoyée à §f" + t.getName() + "§7 (60 s).");
        t.sendMessage(Txt.c(Txt.PREFIX + "§f" + p.getName() + " §7veut te rejoindre. ")
                .append(Txt.c("§a§l[ACCEPTER]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/abysse tpaccept")))
                .append(Txt.c(" "))
                .append(Txt.c("§c§l[REFUSER]").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/abysse tpdeny"))));
        t.playSound(t, Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1.4f);
    }

    private void tpAnswer(Player t, boolean yes) {
        TpRequest r = tpRequests.remove(t.getUniqueId());
        Player p = r == null ? null : Bukkit.getPlayer(r.from());
        if (r == null || p == null || System.currentTimeMillis() - r.time() > 60_000) {
            t.sendMessage(Txt.PREFIX + "§7Aucune demande en attente.");
            return;
        }
        if (!yes) {
            t.sendMessage(Txt.PREFIX + "§7Demande refusée.");
            p.sendMessage(Txt.PREFIX + "§c" + t.getName() + " a refusé ta demande.");
            return;
        }
        if (!Worlds.isAbysse(p.getWorld()) || !Worlds.isAbysse(t.getWorld())) {
            t.sendMessage(Txt.PREFIX + "§cL'un de vous a quitté l'Abysse.");
            return;
        }
        Gen.Strata s = Gen.strata(t.getWorld());
        if (s != null && store.player(p).unlocked < s.n) {
            t.sendMessage(Txt.PREFIX + "§c" + p.getName() + " n'a pas encore ouvert cette strate.");
            p.sendMessage(Txt.PREFIX + "§cTu n'as pas encore ouvert la strate " + s.n + ".");
            return;
        }
        p.teleport(t.getLocation());
        p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
        p.sendMessage(Txt.PREFIX + "§aTe voilà près de §f" + t.getName() + "§a.");
        t.sendMessage(Txt.PREFIX + "§f" + p.getName() + " §7t'a rejoint.");
    }

    private void pay(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(Txt.PREFIX + "§7/abysse payer <joueur> <montant>");
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        double n;
        try {
            n = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            n = -1;
        }
        Company from = store.company(p), to = t == null ? null : store.company(t);
        if (to == null || n <= 0 || from == to) {
            p.sendMessage(Txt.PREFIX + "§cJoueur ou montant invalide.");
            return;
        }
        if (from.money < n) {
            p.sendMessage(Txt.PREFIX + "§cPas assez d'éclats.");
            return;
        }
        from.money -= n;
        to.money += n;
        p.sendMessage(Txt.PREFIX + "§7Envoyé " + Txt.money(n) + " §7à §f" + t.getName());
        t.sendMessage(Txt.PREFIX + "§f" + p.getName() + " §7t'envoie " + Txt.money(n));
    }

    private void company(Player p, String[] args) {
        Company c = store.company(p);
        String sub = args.length > 1 ? args[1].toLowerCase() : "info";
        switch (sub) {
            case "inviter" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null || t == p) {
                    p.sendMessage(Txt.PREFIX + "§7/abysse compagnie inviter <joueur>");
                    return;
                }
                if (!c.leader.equals(p.getUniqueId())) {
                    p.sendMessage(Txt.PREFIX + "§cSeul le chef peut inviter.");
                    return;
                }
                if (c.members.size() >= getConfig().getInt("compagnie-max", 4)) {
                    p.sendMessage(Txt.PREFIX + "§cTa Compagnie est complète.");
                    return;
                }
                invites.put(t.getUniqueId(), c.id);
                p.sendMessage(Txt.PREFIX + "§7Invitation envoyée à §f" + t.getName());
                t.sendMessage(Txt.PREFIX + "§f" + p.getName() + " §7t'invite dans §f" + c.name + "§7. §f/abysse compagnie accepter");
                t.sendMessage(Txt.PREFIX + "§8(Tu quittes ta Compagnie actuelle : si tu y étais seul, tes machines et tes éclats sont rendus.)");
            }
            case "accepter" -> {
                String id = invites.remove(p.getUniqueId());
                Company n = id == null ? null : store.companies.get(id);
                if (n == null) {
                    p.sendMessage(Txt.PREFIX + "§7Aucune invitation.");
                    return;
                }
                leave(p, c, n);
            }
            case "quitter" -> {
                if (c.members.size() <= 1) {
                    p.sendMessage(Txt.PREFIX + "§7Tu es seul dans ta Compagnie.");
                    return;
                }
                c.members.remove(p.getUniqueId());
                if (c.leader.equals(p.getUniqueId())) c.leader = c.members.get(0);
                store.player(p).company = null;
                store.company(p);
                p.sendMessage(Txt.PREFIX + "§7Tu as quitté §f" + c.name + "§7 et fondé ta propre Compagnie.");
            }
            case "expulser" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (!c.leader.equals(p.getUniqueId()) || t == null || !c.members.contains(t.getUniqueId()) || t == p) {
                    p.sendMessage(Txt.PREFIX + "§7/abysse compagnie expulser <joueur> §8(chef seulement)");
                    return;
                }
                c.members.remove(t.getUniqueId());
                store.player(t).company = null;
                store.company(t);
                t.sendMessage(Txt.PREFIX + "§cTu as été exclu de " + c.name + ".");
                p.sendMessage(Txt.PREFIX + "§7" + t.getName() + " a été exclu.");
            }
            case "nom" -> {
                if (!c.leader.equals(p.getUniqueId()) || args.length < 3) {
                    p.sendMessage(Txt.PREFIX + "§7/abysse compagnie nom <nom> §8(chef seulement)");
                    return;
                }
                c.name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).replace("§", "");
                if (c.name.length() > 32) c.name = c.name.substring(0, 32);
                p.sendMessage(Txt.PREFIX + "§7Nouveau nom : §f" + c.name);
            }
            default -> {
                p.sendMessage(Txt.PREFIX + "§f" + c.name + " §7- membres : §f" + npcs.members(c) + " §7- " + Txt.money(c.money));
                p.sendMessage(Txt.PREFIX + "§7/abysse compagnie inviter|accepter|quitter|expulser|nom");
            }
        }
    }

    /** Rejoint une autre Compagnie ; si on était seul, l'ancienne est dissoute (éclats ajoutés, machines rendues en objets). */
    private void leave(Player p, Company old, Company to) {
        if (to.members.size() >= getConfig().getInt("compagnie-max", 4)) {
            p.sendMessage(Txt.PREFIX + "§cCette Compagnie est complète.");
            return;
        }
        old.members.remove(p.getUniqueId());
        if (old.members.isEmpty()) {
            to.money += old.money;
            for (Machine m : new ArrayList<>(old.machines.values())) {
                World w = Bukkit.getWorld(m.world);
                if (w != null) w.getBlockAt(m.x, m.y, m.z).setType(Material.AIR);
                machines.breakMachine(p, m);
            }
            store.companies.remove(old.id);
        } else if (old.leader.equals(p.getUniqueId())) old.leader = old.members.get(0);
        to.members.add(p.getUniqueId());
        store.player(p).company = to.id;
        p.sendMessage(Txt.PREFIX + "§aTu as rejoint §f" + to.name + "§a.");
        for (UUID u : to.members) {
            Player o = Bukkit.getPlayer(u);
            if (o != null && o != p) o.sendMessage(Txt.PREFIX + "§f" + p.getName() + " §7rejoint la Compagnie.");
        }
    }

    private boolean adminCommand(CommandSender s, String sub, String[] args) {
        switch (sub) {
            case "reload" -> {
                reloadConfig();
                GAINS = getConfig().getDouble("gains", 0.6);
                s.sendMessage(Txt.PREFIX + "§aConfig rechargée (gains x" + GAINS + ").");
            }
            case "give" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null || Items.def(args[2]) == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse give <joueur> <objet> [n] §8- objets : " + String.join(", ", Items.ALL.keySet()));
                    return true;
                }
                int n = args.length > 3 ? Integer.parseInt(args[3]) : 1;
                List<ItemStack> st = new ArrayList<>();
                Machines.stacks(args[2], n, st);
                for (ItemStack it : st) t.getInventory().addItem(it);
                s.sendMessage(Txt.PREFIX + "§7Donné.");
            }
            case "donner" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse donner <joueur> <éclats>");
                    return true;
                }
                store.company(t).money += Double.parseDouble(args[2]);
                s.sendMessage(Txt.PREFIX + "§7Compagnie de " + t.getName() + " : " + Txt.money(store.company(t).money));
            }
            case "debloquer" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse debloquer <joueur> <strate>");
                    return true;
                }
                store.player(t).unlocked = Integer.parseInt(args[2]);
                s.sendMessage(Txt.PREFIX + "§7Fait.");
            }
            case "quete" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse quete <joueur> <numero> §8(0 = pas commencé, 1 à " + Quests.QUESTS.size() + ")");
                    return true;
                }
                PlayerData d = store.player(t);
                d.quest = Integer.parseInt(args[2]);
                d.questProgress = 0;
                s.sendMessage(Txt.PREFIX + "§7Quête de " + t.getName() + " : " + d.quest);
            }
            case "reinit" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse reinit <joueur> [confirmer] §8- remet le joueur à zéro dans l'Abysse (il doit y être)");
                    return true;
                }
                if (!Worlds.isAbysse(t.getWorld())) {
                    s.sendMessage(Txt.PREFIX + "§c" + t.getName() + " doit être dans l'Abysse (son inventaire de l'Abysse n'est chargé que là).");
                    return true;
                }
                if (s instanceof Player admin) resetConfirm(admin, t);
                else if (args.length > 2 && args[2].equalsIgnoreCase("confirmer")) {
                    resetPlayer(t);
                    s.sendMessage(Txt.PREFIX + "§7" + t.getName() + " a été remis à zéro dans l'Abysse.");
                } else s.sendMessage(Txt.PREFIX + "§7Ajoute §fconfirmer§7 pour remettre " + t.getName() + " à zéro : tout son avancement dans l'Abysse sera effacé.");
            }
            case "skip", "passer" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse skip <joueur> §8- termine sa quête en cours avec la récompense");
                    return true;
                }
                String title = quests.skip(t);
                s.sendMessage(Txt.PREFIX + (title == null ? "§7" + t.getName() + " n'a pas de quête à terminer." : "§7Quête « " + title + " » terminée pour " + t.getName() + "."));
            }
            case "page" -> {
                Player t = args.length > 3 ? Bukkit.getPlayerExact(args[1]) : null;
                int st = 0, n = 0;
                try {
                    if (t != null) {
                        st = Integer.parseInt(args[2]);
                        n = Integer.parseInt(args[3]);
                    }
                } catch (NumberFormatException ex) {
                    t = null;
                }
                if (t == null || Gen.strata(st) == null || n < 1 || n > Quests.PAGES) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse page <joueur> <strate 1-" + Gen.STRATAS.length + "> <page 1-" + Quests.PAGES + ">");
                    return true;
                }
                for (ItemStack rest : t.getInventory().addItem(Items.page(st, n, Quests.pageTitle(st, n))).values()) t.getWorld().dropItem(t.getLocation(), rest);
                s.sendMessage(Txt.PREFIX + "§7Page " + st + ":" + n + " donnée à " + t.getName() + " (clic droit pour la lire).");
            }
            case "gardien" -> {
                Gen.Strata st = args.length > 1 ? Gen.strata(Integer.parseInt(args[1])) : null;
                if (st == null) {
                    s.sendMessage(Txt.PREFIX + "§7/abysse gardien <strate>");
                    return true;
                }
                World w = worlds.strata(st.n);
                for (Mobs.GuardDef g : Mobs.GUARDS) if (g.strata() == st.n && !mobs.fightIn(st.n)) mobs.start(g, st, w);
                s.sendMessage(Txt.PREFIX + "§7Gardien réveillé.");
            }
            case "tp" -> {
                if (!(s instanceof Player p) || args.length < 2) return false;
                String where = args[1].toLowerCase();
                if (where.equals("antre") && Gen.strata(p.getWorld()) != null) p.teleport(Gen.strata(p.getWorld()).lair(p.getWorld()).add(0, 0, 5));
                else {
                    int n = Integer.parseInt(where);
                    World w = worlds.strata(n);
                    if (w != null) p.teleport(Gen.strata(n).camp(w));
                }
            }
            case "reset" -> {
                if (args.length > 1) {
                    Gen.Strata st;
                    try {
                        st = Gen.strata(Integer.parseInt(args[1]));
                    } catch (NumberFormatException ex) {
                        st = null;
                    }
                    if (st == null) s.sendMessage(Txt.PREFIX + "§c/abysse reset [strate]");
                    else reset.reset(st);
                } else reset.run();
                s.sendMessage(Txt.PREFIX + "§7Remise à neuf lancée (voir la console).");
            }
            case "build" -> {
                World w = worlds.hub();
                if (w != null) w.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(this, "hub_build"));
                worlds.setup();
                s.sendMessage(Txt.PREFIX + "§7Bordgouffre reconstruite.");
            }
            case "bourse" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
                    bourse.reset();
                    bourse.sample();
                    s.sendMessage(Txt.PREFIX + "§7Bourse remise à zéro.");
                } else return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("abysse")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("base", "visiter", "remonter", "bourse", "metiers", "journal", "carnets", "eclats", "payer", "compagnie", "quitter"));
            if (admin(s)) out.addAll(List.of("reload", "give", "donner", "debloquer", "quete", "gardien", "tp", "build"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("compagnie")) out.addAll(List.of("inviter", "accepter", "quitter", "expulser", "nom"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("give")) out.addAll(Items.ALL.keySet());
        else return null;
        String last = args[args.length - 1].toLowerCase();
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
