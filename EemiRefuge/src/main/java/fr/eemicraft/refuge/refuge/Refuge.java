package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.EemiRefuge;
import fr.eemicraft.refuge.Txt;
import fr.eemicraft.refuge.api.Bilan;
import fr.eemicraft.refuge.api.Profile;
import fr.eemicraft.refuge.api.RefugeApi;
import fr.eemicraft.refuge.ui.Menu;
import fr.eemicraft.refuge.ui.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Module Refuge (contrat étape 1, ETAPE1_CONTRAT.md) : monde « refuge » construit par le plugin, PNJ
 * (Passeur, Armurier, Notaire), réserve, boutique, assurance, escouades et matchmaking, profils, bilan.
 * Tous les listeners ne s'occupent que du monde « refuge ».
 */
public class Refuge implements RefugeApi, Listener {

    public static final String WORLD = "refuge";

    private final EemiRefuge plugin;
    private World world;
    private final Poste poste;
    private final Pnj pnj;
    private final Escouades esc;
    private final Boutique shop;
    private final Map<UUID, Profil> profiles = new HashMap<>();
    private final Map<UUID, Bilan> lastBilan = new HashMap<>();
    private final Set<UUID> pendingBilan = new HashSet<>();
    private File dir;
    private NamespacedKey keyVersion;

    public Refuge(EemiRefuge plugin) {
        this.plugin = plugin;
        this.poste = new Poste(plugin);
        this.pnj = new Pnj(plugin, this);
        this.esc = new Escouades(plugin, this);
        this.shop = new Boutique(plugin);
    }

    EemiRefuge plugin() { return plugin; }
    Escouades esc() { return esc; }
    Boutique shop() { return shop; }

    // ================================================================== cycle de vie

    @Override
    public void enable() {
        Ui.init(plugin);
        dir = new File(plugin.getDataFolder(), "joueurs");
        keyVersion = new NamespacedKey(plugin, "refuge_version");
        shop.load();
        world = Bukkit.getWorld(WORLD);
        if (world == null) {
            world = new WorldCreator(WORLD).generator(new Vide()).createWorld();
            if (world != null) plugin.getLogger().info("Monde " + WORLD + " cree (vide).");
        }
        if (world == null) {
            plugin.getLogger().severe("Impossible de creer le monde " + WORLD + " : Refuge desactive.");
            return;
        }
        rules(world);
        Integer v = world.getPersistentDataContainer().get(keyVersion, PersistentDataType.INTEGER);
        if (v == null) {
            plugin.getLogger().info("Construction du Refuge (version " + Poste.VERSION + ").");
            build();
        } else poste.layout(world, false);
        world.setSpawnLocation(poste.spawn(world));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            esc.matchmaking();
            pnj.tick();
        }, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, 600L, 600L);
        for (Player p : Bukkit.getOnlinePlayers()) profile(p.getUniqueId()).name = p.getName();
        plugin.getLogger().info("Refuge pret.");
    }

    @Override
    public void disable() {
        try {
            Ui.closeAll(); // la réserve ouverte est recopiée et enregistrée à la fermeture
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Fermeture des menus : " + ex);
        }
        for (Profil p : profiles.values()) p.save();
        pnj.removeAll();
    }

    private void build() {
        poste.layout(world, true);
        world.getPersistentDataContainer().set(keyVersion, PersistentDataType.INTEGER, Poste.VERSION);
    }

    private static final class Vide extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Poste.F + 1, 9.5);
        }
    }

    private void rules(World w) {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
        w.setGameRule(GameRules.SPAWN_MONSTERS, false);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.KEEP_INVENTORY, true);
        w.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRules.PVP, false);
        w.setGameRule(GameRules.FALL_DAMAGE, false);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
        w.setDifficulty(Difficulty.PEACEFUL);
    }

    private void second() {
        esc.sync();
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().getY() < Poste.F - 20) {
                p.teleport(spawn());
                p.setFallDistance(0);
            }
        }
    }

    private void saveDirty() {
        for (Profil p : profiles.values()) if (p.dirty()) p.save();
    }

    // ================================================================== API

    @Override public World world() { return world; }

    @Override
    public Location spawn() {
        return world == null ? null : poste.spawn(world);
    }

    public boolean atRefuge(Player p) {
        return world != null && p.getWorld().equals(world);
    }

    private boolean isRefuge(World w) {
        return world != null && world.equals(w);
    }

    @Override
    public void sendToRefuge(Player p) {
        if (world == null) return;
        p.setFallDistance(0);
        p.teleport(spawn());
    }

    @Override
    public Profil profile(UUID id) {
        Profil p = profiles.get(id);
        if (p == null) {
            p = Profil.load(id, dir, plugin.getLogger());
            profiles.put(id, p);
        }
        return p;
    }

    /**
     * Décision REFUGE (ETAPE1_CONTRAT.md) : la Ferraille du bilan est créditée ici ; l'XP est déjà créditée
     * par EXPÉ (profile.addXp) et n'est PAS recréditée ; l'assurance est soldée par EXPÉ.
     */
    @Override
    public void showBilan(Player p, Bilan bilan) {
        if (bilan == null) return;
        Profil prof = profile(p.getUniqueId());
        if (bilan.ferraille() > 0) prof.addFerraille(bilan.ferraille());
        prof.save();
        lastBilan.put(p.getUniqueId(), bilan);
        Txt.msg(p, (bilan.extracted() ? "§aExtrait" : "§cMort") + "§7 à §f" + bilan.zoneName() + "§7 en "
                + Ecrans.BilanMenu.time(bilan.durationSeconds()) + " : §f" + bilan.kills() + "§7 zombies, §f"
                + bilan.revives() + "§7 relèves" + (bilan.ferraille() > 0 ? ", §6+" + Ecrans.fmt(bilan.ferraille()) + " ⚙" : "")
                + (bilan.xp() > 0 ? "§7, §a+" + bilan.xp() + " XP" : "") + "§7.");
        if (p.isOnline() && atRefuge(p)) openBilanLater(p);
        else pendingBilan.add(p.getUniqueId());
    }

    private void openBilanLater(Player p) {
        pendingBilan.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bilan b = lastBilan.get(p.getUniqueId());
            if (b != null && p.isOnline() && atRefuge(p)) new Ecrans.BilanMenu(this, p, b).open();
        }, 30L);
    }

    Bilan lastBilan(UUID u) {
        return lastBilan.get(u);
    }

    void reopen(Player p, Menu m) {
        if (m instanceof Ecrans.Expedition) new Ecrans.Expedition(this, p).open();
        else if (m instanceof Ecrans.Escouade) new Ecrans.Escouade(this, p).open();
    }

    // ================================================================== boutique et assurance

    void buy(Player p, Boutique.Article a) {
        Profil prof = profile(p.getUniqueId());
        ItemStack it = Boutique.create(a.key(), a.amount());
        if (it == null) return;
        if (prof.ferraille() < a.price()) {
            Ecrans.deny(p, "Il te manque " + Ecrans.fmt(a.price() - prof.ferraille()) + " ⚙.");
            return;
        }
        if (!Ui.fits(p, it)) {
            Ecrans.deny(p, "Ton sac est plein.");
            return;
        }
        prof.addFerraille(-a.price());
        p.getInventory().addItem(it);
        prof.save();
        Ui.success(p);
        Txt.msg(p, "Acheté : §f" + Boutique.name(a.key()) + (a.amount() > 1 ? " ×" + a.amount() : "") + "§7 pour §6"
                + Ecrans.fmt(a.price()) + " ⚙§7.");
        reopenShop(p);
    }

    void sell(Player p, String key, boolean all) {
        Long unit = shop.sell.get(key);
        if (unit == null) return;
        ItemStack[] st = p.getInventory().getStorageContents();
        int sold = 0;
        for (int i = 0; i < st.length; i++) {
            ItemStack it = st[i];
            if (!key.equals(Boutique.keyOf(it))) continue;
            int take = all ? it.getAmount() : 1;
            sold += take;
            if (take >= it.getAmount()) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - take);
            if (!all) break;
        }
        if (sold == 0) return;
        Profil prof = profile(p.getUniqueId());
        prof.addFerraille(unit * sold);
        prof.save();
        Ui.success(p);
        Txt.msg(p, "Vendu : §f" + Boutique.name(key) + " ×" + sold + "§7 pour §6" + Ecrans.fmt(unit * sold) + " ⚙§7.");
        reopenShop(p);
    }

    private void reopenShop(Player p) {
        Menu m = Ui.openMenu(p);
        if (m instanceof Ecrans.Boutique b) new Ecrans.Boutique(this, p, b.sellMode(), b.page()).open();
    }

    /** Au départ : un objet assuré qui n'est plus sur le joueur est retiré et remboursé. */
    void checkInsurance(Player p) {
        Profil prof = profile(p.getUniqueId());
        List<ItemStack> ins = prof.insuredRaw();
        for (int i = ins.size() - 1; i >= 0; i--) {
            ItemStack it = ins.get(i);
            if (p.getInventory().containsAtLeast(it, it.getAmount())) continue;
            long back = prof.removeInsured(i);
            prof.addFerraille(back);
            Txt.msg(p, "§eAssurance annulée§7 : " + Boutique.name(Boutique.keyOf(it) == null ? "objet" : Boutique.keyOf(it))
                    + " n'est plus sur toi (" + Ecrans.fmt(back) + " ⚙ remboursés).");
        }
        prof.save();
    }

    // ================================================================== commandes

    @Override
    public boolean handleCommand(CommandSender s, String[] a) {
        // 0.2.1 : accès réservé pendant les tests (Thibault, 13/11/2026) : eemirefuge.play retiré au groupe
        // default dans LuckPerms et donné aux admins. Les admins (eemirefuge.admin) passent toujours.
        if (s instanceof Player pl && !pl.hasPermission("eemirefuge.play") && !pl.hasPermission("eemirefuge.admin")) {
            Txt.msg(pl, "Dernier Refuge arrive bientôt : le mode est encore en test.");
            return true;
        }
        if (a.length == 0) {
            if (!(s instanceof Player p)) return false;
            if (esc.inExpe(p)) {
                Ecrans.deny(p, "Tu es en expédition : rejoins une extraction pour rentrer.");
                return true;
            }
            if (world == null) {
                Ecrans.deny(p, "Le Refuge n'est pas disponible.");
                return true;
            }
            if (atRefuge(p)) {
                new Ecrans.ProfilMenu(this, p).open();
                return true;
            }
            sendToRefuge(p);
            Txt.msg(p, "Bienvenue au §fRefuge§7. Parle au §6Passeur§7 (au nord) pour partir en expédition.");
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "escouade" -> {
                if (!(s instanceof Player p)) return true;
                String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                switch (act) {
                    case "accepter" -> {
                        String err = esc.accept(p, a.length > 2 ? a[2] : null);
                        if (err != null) Ecrans.deny(p, err);
                    }
                    case "refuser" -> esc.deny(p);
                    case "quitter" -> esc.leave(p, true);
                    default -> {
                        if (!atRefuge(p)) Ecrans.deny(p, "Les escouades se forment au Refuge (/refuge).");
                        else new Ecrans.Escouade(this, p).open();
                    }
                }
                return true;
            }
            case "reserve" -> {
                if (!(s instanceof Player p)) return true;
                new Ecrans.Reserve(this, p).open();
                return true;
            }
            case "profil" -> {
                if (!(s instanceof Player p)) return true;
                if (p.getWorld().getName().toLowerCase(Locale.ROOT).startsWith("refuge")) new Ecrans.ProfilMenu(this, p).open();
                else {
                    Profil prof = profile(p.getUniqueId());
                    Txt.msg(p, "§6" + Ecrans.fmt(prof.ferraille()) + " ⚙§7 · acte §f" + prof.act() + "§7 · niveau §f"
                            + prof.level() + "§7 (" + prof.xp() + " XP)");
                }
                return true;
            }
            case "bilan" -> {
                if (!(s instanceof Player p)) return true;
                Bilan b = lastBilan.get(p.getUniqueId());
                if (b == null) Ecrans.deny(p, "Aucune expédition pour l'instant.");
                else if (!atRefuge(p)) Ecrans.deny(p, "Le bilan se consulte au Refuge.");
                else new Ecrans.BilanMenu(this, p, b).open();
                return true;
            }
            case "refuge" -> {
                if (!s.hasPermission(EemiRefuge.PERM)) return false;
                String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
                switch (act) {
                    case "build" -> {
                        if (world == null) {
                            Txt.msg(s, "§cMonde " + WORLD + " absent.");
                            return true;
                        }
                        pnj.removeAll();
                        build();
                        world.setSpawnLocation(poste.spawn(world));
                        Txt.msg(s, "Refuge reconstruit (version " + Poste.VERSION + ").");
                    }
                    case "boutique" -> {
                        shop.load();
                        Txt.msg(s, "refuge-shop.yml relu : " + shop.buy.size() + " articles, " + shop.sell.size() + " rachats.");
                    }
                    case "tp" -> {
                        if (s instanceof Player p) sendToRefuge(p);
                    }
                    default -> Txt.msg(s, "/refuge refuge <build|boutique|tp>");
                }
                return true;
            }
            case "ferraille", "acte" -> {
                if (!s.hasPermission(EemiRefuge.PERM)) return false;
                if (a.length < 3) {
                    Txt.msg(s, sub.equals("acte") ? "/refuge acte <joueur> <n>" : "/refuge ferraille <joueur> <+n|-n|=n>");
                    return true;
                }
                OfflinePlayer t = Bukkit.getPlayerExact(a[1]);
                if (t == null) t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) {
                    Txt.msg(s, "§cJoueur inconnu.");
                    return true;
                }
                Profil prof = profile(t.getUniqueId());
                try {
                    if (sub.equals("acte")) {
                        prof.setAct(Integer.parseInt(a[2]));
                        Txt.msg(s, a[1] + " : acte " + prof.act() + ".");
                    } else {
                        String n = a[2];
                        if (n.startsWith("=")) prof.setFerraille(Long.parseLong(n.substring(1)));
                        else prof.addFerraille(Long.parseLong(n.startsWith("+") ? n.substring(1) : n));
                        Txt.msg(s, a[1] + " : " + Ecrans.fmt(prof.ferraille()) + " ⚙.");
                    }
                    prof.save();
                } catch (NumberFormatException ex) {
                    Txt.msg(s, "§cNombre invalide.");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ================================================================== événements (monde refuge seulement)

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        profile(p.getUniqueId()).name = p.getName();
        if (atRefuge(p) && pendingBilan.contains(p.getUniqueId())) openBilanLater(p);
        if (atRefuge(p)) kitLater(p);
    }

    // ------------------------------------------------------------------ kit de départ (0.2.1, demande de Thibault)

    /**
     * Kit de départ à la première arrivée au Refuge (épée en bois, conserves, bandages, lampe torche = torche).
     * Kit de secours (épée en bois + 2 conserves) si le joueur revient sans aucune arme de corps à corps
     * (sac + réserve), au plus une fois toutes les `kit.secours-minutes` (30) : on ne reste jamais bloqué.
     * Donné 10 ticks après l'arrivée (Multiverse-Inventories et DeluxeHub ont fini de toucher l'inventaire).
     */
    private void kitLater(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !atRefuge(p) || esc.inExpe(p) || free(p)) return;
            Profil prof = profile(p.getUniqueId());
            if (!prof.kitRecu) {
                give(p, fr.eemicraft.refuge.Catalogue.create("epee_bois", 1));
                give(p, fr.eemicraft.refuge.Catalogue.create("conserve", 4));
                give(p, fr.eemicraft.refuge.Catalogue.create("bandage", 3));
                give(p, new ItemStack(Material.TORCH, 8));
                prof.kitRecu = true;
                prof.kitSecoursAt = System.currentTimeMillis();
                prof.markDirty();
                prof.save();
                Txt.msg(p, "Le §6Passeur§7 te confie un §fkit de départ§7 : une épée en bois, des conserves, des bandages et des torches. Bonne chance.");
                return;
            }
            long wait = Math.max(0, plugin.getConfig().getLong("kit.secours-minutes", 30)) * 60_000L;
            if (hasMeleeWeapon(p, prof) || System.currentTimeMillis() - prof.kitSecoursAt < wait) return;
            give(p, fr.eemicraft.refuge.Catalogue.create("epee_bois", 1));
            give(p, fr.eemicraft.refuge.Catalogue.create("conserve", 2));
            prof.kitSecoursAt = System.currentTimeMillis();
            prof.markDirty();
            prof.save();
            Txt.msg(p, "Tu es revenu les mains vides : le §6Passeur§7 te tend une vieille §fépée en bois§7.");
        }, 10L);
    }

    private static void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack rest : p.getInventory().addItem(it).values())
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private static boolean isMelee(ItemStack it) {
        if (it == null || it.isEmpty()) return false;
        String id = fr.eemicraft.refuge.Catalogue.id(it);
        if (id != null) return id.startsWith("epee_") || id.equals("dague") || id.startsWith("hache") || id.startsWith("lance") || id.startsWith("masse");
        String m = it.getType().name();
        return m.endsWith("_SWORD") || m.endsWith("_AXE") || m.endsWith("_SPEAR") || m.equals("MACE");
    }

    private static boolean hasMeleeWeapon(Player p, Profil prof) {
        for (ItemStack it : p.getInventory().getContents()) if (isMelee(it)) return true;
        for (ItemStack it : prof.reserve) if (isMelee(it)) return true;
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        esc.quit(e.getPlayer());
        Profil prof = profiles.get(e.getPlayer().getUniqueId());
        if (prof != null && prof.dirty()) prof.save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!isRefuge(e.getFrom().getWorld()) || e.getTo() == null || isRefuge(e.getTo().getWorld())) return;
        Player p = e.getPlayer();
        if (Ui.openMenu(p) != null) p.closeInventory(); // la réserve ne se consulte qu'au Refuge
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isRefuge(e.getFrom()) && !esc.inExpe(p)) esc.leftRefuge(p);
        if (atRefuge(p) && pendingBilan.contains(p.getUniqueId())) openBilanLater(p);
        if (atRefuge(p)) kitLater(p);
    }

    private boolean free(Player p) {
        return p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!isRefuge(e.getEntity().getWorld())) return;
        Entity en = e.getEntity();
        if (en instanceof Player p) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
                p.teleport(spawn());
                p.setFallDistance(0);
            }
        } else if (pnj.idOf(en) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHitNpc(EntityDamageByEntityEvent e) {
        if (!isRefuge(e.getEntity().getWorld())) return;
        String id = pnj.idOf(e.getEntity());
        if (id == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p) talk(p, id);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (isRefuge(e.getEntity().getWorld()) && e.getFoodLevel() < e.getEntity().getFoodLevel()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (isRefuge(e.getBlock().getWorld()) && !free(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (isRefuge(e.getBlock().getWorld()) && !free(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (isRefuge(e.getEntity().getWorld()) && !(e.getRemover() instanceof Player p && free(p))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (!atRefuge(e.getPlayer()) || free(e.getPlayer())) return;
        e.setCancelled(true);
        Txt.msg(e.getPlayer(), "Au Refuge, on ne jette rien : range-le dans ta réserve ou vends-le à l'Armurier.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (isRefuge(e.getLocation().getWorld()) && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!atRefuge(p)) return;
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
            return;
        }
        Block b = e.getClickedBlock();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || b == null) return;
        if (poste.reserveBlocks.contains(Poste.key(b.getX(), b.getY(), b.getZ()))) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) new Ecrans.Reserve(this, p).open();
            return;
        }
        if (free(p)) return;
        Material m = b.getType();
        String n = m.name();
        boolean door = n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE_GATE");
        if (m.isInteractable() && !door) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!atRefuge(e.getPlayer())) return;
        String id = pnj.idOf(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) talk(e.getPlayer(), id);
    }

    private void talk(Player p, String id) {
        if (Ui.openMenu(p) != null) return;
        switch (id) {
            case "passeur" -> new Ecrans.Expedition(this, p).open();
            case "armurier" -> new Ecrans.Boutique(this, p, false, 0).open();
            case "notaire" -> new Ecrans.Assurance(this, p).open();
            default -> { }
        }
    }
}
