package fr.eemicraft.infection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Armes a feu des survivants (tir instantane par lancer de rayon, chargeur, rechargement), grenades,
 * menu de choix du kit, crachat acide des zombies (flaques). Objets vanilla ; avec textures: true, modeles du pack
 * EEMICRAFT (eemicraft:infection/...).
 */
final class Arsenal {
    static final String GRENADE_TAG = "eemiinf_grenade";
    static final String ACID_TAG = "eemiinf_acid";
    static final List<String> KITS = List.of("assault", "pump", "sniper", "smg");

    /** Definition d'une arme (valeurs du config, defauts dans le code). */
    record Gun(String id, String name, Material mat, double damage, double head, int pellets, double spread, double range,
               int interval, boolean auto, int mag, int reserve, int reload, double knock, int pierce, double selfRecoil,
               Sound sound, float pitch, float volume, Particle trail) {
    }

    /** Defauts : un zombie a 20 PV. */
    static final Map<String, Gun> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("assault", new Gun("assault", "&6Fusil d'assaut", Material.IRON_HORSE_ARMOR, 3.0, 1.5, 1, 0.025, 40, 3, true, 30, 150, 40, 0.12, 1, 0,
                Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.6f, 0.9f, Particle.CRIT));
        DEFAULTS.put("pump", new Gun("pump", "&cFusil à pompe", Material.GOLDEN_HORSE_ARMOR, 2.5, 1.5, 8, 0.11, 14, 18, false, 6, 36, 50, 1.2, 1, 0.35,
                Sound.ENTITY_GENERIC_EXPLODE, 1.9f, 0.6f, Particle.SMOKE));
        DEFAULTS.put("sniper", new Gun("sniper", "&bFusil de précision", Material.DIAMOND_HORSE_ARMOR, 14.0, 2.0, 1, 0.0, 90, 30, false, 5, 25, 60, 0.45, 3, 0.2,
                Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1.4f, Particle.ELECTRIC_SPARK));
        DEFAULTS.put("smg", new Gun("smg", "&eMitraillette", Material.LEATHER_HORSE_ARMOR, 2.0, 1.5, 1, 0.06, 25, 2, true, 40, 200, 40, 0.06, 1, 0,
                Sound.BLOCK_NOTE_BLOCK_HAT, 2.0f, 1.0f, Particle.CRIT));
        DEFAULTS.put("pistol", new Gun("pistol", "&7Pistolet", Material.COPPER_HORSE_ARMOR, 2.5, 1.5, 1, 0.02, 30, 6, false, 12, -1, 30, 0.1, 1, 0,
                Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 0.7f, Particle.CRIT));
    }

    static final Map<String, String> KIT_DESC = Map.of(
            "assault", "&7Rafale en maintenant le clic droit, polyvalent",
            "pump", "&7Plusieurs plombs, courte portée, gros recul",
            "sniper", "&7Gros dégâts, perce, tête x2, cadence lente",
            "smg", "&7Cadence très rapide, petits dégâts");

    static final Map<String, Material> KIT_ICON = Map.of(
            "assault", Material.IRON_HORSE_ARMOR, "pump", Material.GOLDEN_HORSE_ARMOR,
            "sniper", Material.DIAMOND_HORSE_ARMOR, "smg", Material.LEATHER_HORSE_ARMOR);

    /** Modele du pack (eemicraft:infection/<nom>) de chaque arme, pose seulement si textures: true. */
    static final Map<String, String> MODEL = Map.of(
            "assault", "fusil_assaut", "pump", "fusil_pompe", "sniper", "sniper", "smg", "mitraillette", "pistol", "pistolet");

    record Puddle(Location center, long end, UUID owner) {
    }

    static final class KitMenu implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    final EemiInfection pl;
    final Map<String, Gun> guns = new LinkedHashMap<>();
    /** Kit choisi par joueur (garde d'une partie a l'autre tant que le serveur tourne). */
    final Map<UUID, String> kitChoice = new HashMap<>();
    /** Grenades en vol : projectile -> lanceur. */
    final Map<UUID, UUID> grenades = new HashMap<>();
    /** Crachats acides en vol : projectile -> zombie. */
    final Map<UUID, UUID> spits = new HashMap<>();
    final List<Puddle> puddles = new ArrayList<>();
    long ticks;

    Arsenal(EemiInfection pl) {
        this.pl = pl;
        load();
    }

    FileConfiguration cfg() {
        return pl.getConfig();
    }

    void load() {
        guns.clear();
        for (Gun d : DEFAULTS.values()) {
            ConfigurationSection s = cfg().getConfigurationSection("weapons." + d.id());
            if (s == null) {
                guns.put(d.id(), d);
                continue;
            }
            guns.put(d.id(), new Gun(d.id(), s.getString("name", d.name()), d.mat(),
                    s.getDouble("damage", d.damage()), s.getDouble("headshot", d.head()), Math.max(1, s.getInt("pellets", d.pellets())),
                    Math.max(0, s.getDouble("spread", d.spread())), Math.max(2, s.getDouble("range", d.range())),
                    Math.max(1, s.getInt("interval-ticks", d.interval())), s.getBoolean("auto", d.auto()),
                    Math.max(1, s.getInt("magazine", d.mag())), s.getInt("reserve", d.reserve()), Math.max(1, s.getInt("reload-ticks", d.reload())),
                    s.getDouble("knockback", d.knock()), Math.max(1, s.getInt("pierce", d.pierce())), s.getDouble("self-recoil", d.selfRecoil()),
                    d.sound(), d.pitch(), d.volume(), d.trail()));
        }
    }

    Gun gun(String id) {
        Gun g = guns.get(id);
        return g == null ? guns.get("assault") : g;
    }

    String kitOf(UUID id) {
        String k = kitChoice.get(id);
        if (k == null || !KITS.contains(k) || !kitEnabled(k)) {
            k = cfg().getString("kits.default", "assault");
        }
        return KITS.contains(k) ? k : "assault";
    }

    boolean kitEnabled(String k) {
        return cfg().getBoolean("kits." + k + ".enabled", true);
    }

    static boolean isGunMat(Material m) {
        for (Gun g : DEFAULTS.values()) {
            if (g.mat() == m) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ objets

    String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) {
            return null;
        }
        return it.getItemMeta().getPersistentDataContainer().get(pl.kitKey, PersistentDataType.STRING);
    }

    ItemStack tagged(ItemStack it, String tag) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(pl.kitKey, PersistentDataType.STRING, tag);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    ItemStack gunItem(Gun g) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Clic droit : tirer" + (g.auto() ? " &8(maintenir = rafale)" : ""));
        lore.add("&7F (échange de main) : recharger");
        lore.add("&8Dégâts " + num(g.damage()) + (g.pellets() > 1 ? " x" + g.pellets() + " plombs" : "") + ", tête x" + num(g.head())
                + ", chargeur " + g.mag() + (g.reserve() < 0 ? ", munitions infinies" : ""));
        return pl.skin(tagged(pl.item(g.mat(), g.name() + " &7(clic droit)", lore.toArray(new String[0])), "gun:" + g.id()), MODEL.get(g.id()));
    }

    ItemStack kitMenuItem() {
        return pl.skin(tagged(pl.item(Material.NETHER_STAR, "&e&lChoisir mon arme &7(clic droit)", "&7Assaut, Pompe, Sniper ou Mitraillette",
                "&8Modifiable jusqu'à la fin de la fuite"), "kitmenu"), "caisse_armes");
    }

    ItemStack grenadeItem(int n) {
        ItemStack it = tagged(pl.item(Material.FIRE_CHARGE, "&6Grenade &7(clic droit)", "&7Explose à l'impact ou après "
                + num(cfg().getDouble("grenades.fuse-seconds", 1.5)) + " s", "&7Blesse et repousse les zombies, ne casse rien",
                "&8+1 toutes les " + cfg().getInt("grenades.regen-seconds", 60) + " s (max " + cfg().getInt("grenades.max", 2) + ")"), "grenade");
        it.setAmount(Math.max(1, n));
        return pl.skin(it, "grenade");
    }

    static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d).replace('.', ',');
    }

    /** Equipe les armes du kit choisi (epee, arme principale, pistolet, grenades). Munitions pleines. */
    void giveWeapons(Player p, EemiInfection.Gamer g) {
        PlayerInventory inv = p.getInventory();
        String kit = kitOf(p.getUniqueId());
        g.kit = kit;
        Gun prim = gun(kit);
        Gun pist = gun("pistol");
        g.mag.clear();
        g.reserve.clear();
        g.mag.put(prim.id(), prim.mag());
        g.reserve.put(prim.id(), prim.reserve());
        g.mag.put(pist.id(), pist.mag());
        g.reserve.put(pist.id(), pist.reserve());
        cancelReload(p, g, false);
        g.holdUntil = 0;
        g.next.clear();
        inv.setItem(1, gunItem(prim));
        inv.setItem(2, gunItem(pist));
        syncGrenades(p, g);
    }

    void syncGrenades(Player p, EemiInfection.Gamer g) {
        p.getInventory().setItem(3, g.grenades > 0 ? grenadeItem(g.grenades) : null);
    }

    /** Retire l'objet de choix du kit (fin de la fuite). */
    void removeKitMenuItem(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if ("kitmenu".equals(tag(inv.getItem(i)))) {
                inv.setItem(i, null);
            }
        }
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof KitMenu) {
            p.closeInventory();
        }
    }

    // ================================================================ menu des kits

    void openMenu(Player p) {
        KitMenu h = new KitMenu();
        Inventory inv = Bukkit.createInventory(h, 27, EemiInfection.c("&8Choisis ton arme"));
        h.inv = inv;
        String cur = kitOf(p.getUniqueId());
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < KITS.size(); i++) {
            String k = KITS.get(i);
            if (!kitEnabled(k)) {
                continue;
            }
            Gun g = gun(k);
            String name = cfg().getString("kits." + k + ".name", kitName(k));
            List<String> lore = new ArrayList<>();
            lore.add(cfg().getString("kits." + k + ".description", KIT_DESC.get(k)));
            lore.add("");
            lore.add("&7Arme : " + g.name());
            lore.add("&7Dégâts : &f" + num(g.damage()) + (g.pellets() > 1 ? " x" + g.pellets() : "") + " &7| tête x" + num(g.head()));
            lore.add("&7Cadence : &f" + num(Math.round(200.0 / g.interval()) / 10.0) + " tirs/s &7| portée &f" + (int) g.range());
            lore.add("&7Chargeur : &f" + g.mag() + " &7| réserve &f" + (g.reserve() < 0 ? "∞" : g.reserve()));
            if (g.pierce() > 1) {
                lore.add("&7Perce jusqu'à &f" + g.pierce() + " &7zombies");
            }
            lore.add("&7+ épée, pistolet, grenades");
            lore.add("");
            lore.add(k.equals(cur) ? "&a✔ Kit choisi" : "&eClique pour choisir");
            ItemStack it = pl.item(KIT_ICON.get(k), name, lore.toArray(new String[0]));
            tagged(it, "menu:" + k);
            if (k.equals(cur)) {
                ItemMeta m = it.getItemMeta();
                m.setEnchantmentGlintOverride(true);
                it.setItemMeta(m);
            }
            inv.setItem(slots[i], pl.skin(it, MODEL.get(k)));
        }
        p.openInventory(inv);
    }

    static String kitName(String k) {
        return switch (k) {
            case "pump" -> "&c&lPompe";
            case "sniper" -> "&b&lSniper";
            case "smg" -> "&e&lMitraillette";
            default -> "&6&lAssaut";
        };
    }

    /** Clic dans le menu des kits. */
    void menuClick(Player p, ItemStack it) {
        String t = tag(it);
        if (t == null || !t.startsWith("menu:")) {
            return;
        }
        String k = t.substring(5);
        if (!KITS.contains(k) || !kitEnabled(k)) {
            return;
        }
        EemiInfection.Gamer g = pl.active(p);
        boolean waiting = pl.state == EemiInfection.State.WAITING || pl.state == EemiInfection.State.COUNTDOWN;
        boolean flee = pl.state == EemiInfection.State.FLEE && g != null && !g.zombie;
        if (g == null || !(waiting || flee)) {
            p.closeInventory();
            EemiInfection.msg(p, "&7Le choix de l'arme est fermé.");
            return;
        }
        boolean same = k.equals(g.kit);
        kitChoice.put(p.getUniqueId(), k);
        // meme kit pendant la fuite : on ne redonne pas l'arme (sinon chargeur et reserve remplis gratuitement)
        if (flee && !same) {
            giveWeapons(p, g);
            p.getInventory().setHeldItemSlot(1);
        }
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.2f);
        EemiInfection.msg(p, "&7Kit choisi : " + cfg().getString("kits." + k + ".name", kitName(k)));
        p.closeInventory();
    }

    // ================================================================ tir

    /** Clic droit avec une arme. */
    void trigger(Player p, EemiInfection.Gamer g, Gun gun) {
        if (gun.auto()) {
            g.holdUntil = ticks + Math.max(5, cfg().getInt("weapons.hold-window-ticks", 6));
        }
        if (ticks >= g.next.getOrDefault(gun.id(), 0L)) {
            fire(p, g, gun);
        }
    }

    boolean canShoot(EemiInfection.Gamer g) {
        return pl.state == EemiInfection.State.FLEE || pl.state == EemiInfection.State.PLAYING;
    }

    void fire(Player p, EemiInfection.Gamer g, Gun gun) {
        if (!canShoot(g) || g.zombie || p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (g.stunUntil > ticks) {
            g.next.put(gun.id(), ticks + 5);
            return;
        }
        if (inAcid(p.getLocation())) {
            g.hitInfo = "&2Impossible de tirer dans l'acide !";
            g.hitInfoUntil = ticks + 20;
            g.next.put(gun.id(), ticks + 10);
            return;
        }
        if (g.reloading != null) {
            return;
        }
        int m = g.mag.getOrDefault(gun.id(), 0);
        if (m <= 0) {
            g.holdUntil = 0;
            startReload(p, g, gun);
            return;
        }
        g.mag.put(gun.id(), m - 1);
        g.next.put(gun.id(), ticks + gun.interval());
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector base = eye.getDirection();
        Map<Player, double[]> dealt = new LinkedHashMap<>();
        for (int i = 0; i < gun.pellets(); i++) {
            Vector dir = base.clone();
            if (gun.spread() > 0) {
                dir.add(new Vector(pl.rnd.nextGaussian(), pl.rnd.nextGaussian(), pl.rnd.nextGaussian()).multiply(gun.spread() * 0.5)).normalize();
            }
            // traces limitees : une particule = un paquet par joueur proche (rafales de 15 tireurs = beaucoup de paquets)
            trace(p, eye, dir, gun, dealt, i == 0 || gun.pellets() <= 2 || i == gun.pellets() / 2);
        }
        w.playSound(eye, gun.sound(), gun.volume(), gun.pitch());
        w.spawnParticle(Particle.SMOKE, eye.clone().add(base.clone().multiply(0.8)).add(0, -0.15, 0), 2, 0.02, 0.02, 0.02, 0.005);
        // 1.6.0 : par defaut les balles ne repoussent plus (bullet-knockback: false) : le recul a chaque balle figeait le zombie
        boolean bulletKb = cfg().getBoolean("weapons.bullet-knockback", false);
        for (Map.Entry<Player, double[]> e : dealt.entrySet()) {
            Player z = e.getKey();
            double[] d = e.getValue();
            if (!bulletKb) {
                pl.damageZombie(z, p, d[0], d[1] > 0, null);
                continue;
            }
            Vector kb = z.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (kb.lengthSquared() < 1.0E-4) {
                kb = base.clone().setY(0);
            }
            double share = Math.min(1.0, d[2] / gun.pellets() * (gun.pellets() > 1 ? 1.6 : 1.0));
            kb.normalize().multiply(gun.knock() * share).setY(gun.knock() > 0.5 ? 0.25 * share : 0.02);
            pl.damageZombie(z, p, d[0], d[1] > 0, kb);
        }
        if (gun.selfRecoil() > 0) {
            Vector back = base.clone().setY(0);
            if (back.lengthSquared() > 1.0E-4) {
                p.setVelocity(p.getVelocity().add(back.normalize().multiply(-gun.selfRecoil())));
            }
        }
        if (g.mag.getOrDefault(gun.id(), 0) <= 0) {
            g.holdUntil = 0;
            startReload(p, g, gun);
        }
    }

    /** Un rayon : les murs arretent la balle, on touche jusqu'a `pierce` zombies (les survivants ne bloquent pas). */
    void trace(Player p, Location eye, Vector dir, Gun gun, Map<Player, double[]> dealt, boolean drawTrail) {
        World w = eye.getWorld();
        RayTraceResult wall = w.rayTraceBlocks(eye, dir, gun.range(), FluidCollisionMode.NEVER, true);
        double max = wall == null ? gun.range() : wall.getHitPosition().distance(eye.toVector());
        record Hit(Player z, double dist, double y) {
        }
        List<Hit> hits = new ArrayList<>();
        Vector start = eye.toVector();
        for (Player z : w.getPlayers()) {
            if (z == p) {
                continue;
            }
            EemiInfection.Gamer gz = pl.active(z);
            if (gz == null || !gz.zombie || gz.inCrypt || z.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            BoundingBox bb = z.getBoundingBox().expand(0.12);
            RayTraceResult r = bb.rayTrace(start, dir, max);
            if (r != null) {
                hits.add(new Hit(z, r.getHitPosition().distance(start), r.getHitPosition().getY()));
            }
        }
        hits.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        double end = max;
        double headRatio = cfg().getDouble("weapons.head-ratio", 0.78);
        for (int i = 0; i < Math.min(gun.pierce(), hits.size()); i++) {
            Hit h = hits.get(i);
            boolean head = (h.y() - h.z().getLocation().getY()) / Math.max(0.5, h.z().getHeight()) >= headRatio;
            double dmg = gun.damage() * (head ? gun.head() : 1.0);
            double[] acc = dealt.computeIfAbsent(h.z(), k -> new double[3]);
            acc[0] += dmg;
            if (head) {
                acc[1] = 1;
            }
            acc[2]++;
            Location at = new Location(w, start.getX() + dir.getX() * h.dist(), start.getY() + dir.getY() * h.dist(), start.getZ() + dir.getZ() * h.dist());
            w.spawnParticle(Particle.DAMAGE_INDICATOR, at, head ? 4 : 2, 0.1, 0.1, 0.1, 0.05);
            end = h.dist();
        }
        if (hits.size() <= gun.pierce()) {
            // la balle n'a pas epuise sa perforation : elle continue jusqu'au mur (ou la portee)
            end = max;
            if (wall != null) {
                w.spawnParticle(Particle.SMOKE, wall.getHitPosition().toLocation(w), 3, 0.05, 0.05, 0.05, 0.01);
            }
        }
        if (drawTrail) {
            double step = Math.max(1.6, end / 12.0);
            for (double t = 1.2; t < end; t += step) {
                w.spawnParticle(gun.trail(), start.getX() + dir.getX() * t, start.getY() + dir.getY() * t - 0.1, start.getZ() + dir.getZ() * t, 1, 0, 0, 0, 0);
            }
        }
    }

    void startReload(Player p, EemiInfection.Gamer g, Gun gun) {
        if (g.reloading != null) {
            return;
        }
        int m = g.mag.getOrDefault(gun.id(), 0);
        int res = g.reserve.getOrDefault(gun.id(), gun.reserve());
        if (m >= gun.mag()) {
            return;
        }
        if (res == 0) {
            // clic maintenu = appel toutes les 4 ticks : pas de son en boucle
            if (g.hitInfoUntil <= ticks) {
                p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.6f);
            }
            g.hitInfo = "&cPlus de munitions : prends le pistolet !";
            g.hitInfoUntil = ticks + 30;
            return;
        }
        g.reloading = gun.id();
        g.reloadDone = ticks + gun.reload();
        p.setCooldown(gun.mat(), gun.reload());
        p.playSound(p.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.7f, 1.6f);
    }

    void cancelReload(Player p, EemiInfection.Gamer g, boolean tell) {
        if (g.reloading != null) {
            Gun gun = guns.get(g.reloading);
            if (gun != null && p.hasCooldown(gun.mat())) {
                p.setCooldown(gun.mat(), 0);
            }
            if (tell) {
                g.hitInfo = "&7Rechargement annulé";
                g.hitInfoUntil = ticks + 20;
            }
        }
        g.reloading = null;
    }

    /** Touche F : recharger l'arme en main. */
    void manualReload(Player p, EemiInfection.Gamer g) {
        Gun gun = held(p);
        if (gun != null && canShoot(g)) {
            startReload(p, g, gun);
        }
    }

    Gun held(Player p) {
        String t = tag(p.getInventory().getItemInMainHand());
        if (t == null || !t.startsWith("gun:")) {
            return null;
        }
        return guns.get(t.substring(4));
    }

    /** Chaque tick : rechargements, rafales, barre d'action des survivants, flaques d'acide, grenades. */
    void tick() {
        ticks++;
        boolean live = pl.state == EemiInfection.State.FLEE || pl.state == EemiInfection.State.PLAYING;
        if (!live) {
            return;
        }
        for (EemiInfection.Gamer g : pl.players.values()) {
            if (g.zombie || pl.gone.contains(g.id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(g.id);
            if (p == null || !pl.inWorld(p)) {
                continue;
            }
            Gun h = held(p);
            if (g.reloading != null) {
                Gun r = guns.get(g.reloading);
                if (r == null || h == null || !h.id().equals(r.id())) {
                    cancelReload(p, g, true);
                } else if (ticks >= g.reloadDone) {
                    int m = g.mag.getOrDefault(r.id(), 0);
                    int res = g.reserve.getOrDefault(r.id(), r.reserve());
                    int need = r.mag() - m;
                    int take = res < 0 ? need : Math.min(need, res);
                    g.mag.put(r.id(), m + take);
                    if (res >= 0) {
                        g.reserve.put(r.id(), res - take);
                    }
                    g.reloading = null;
                    p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8f, 1.3f);
                }
            } else if (h != null && h.auto() && ticks < g.holdUntil && ticks >= g.next.getOrDefault(h.id(), 0L)) {
                fire(p, g, h);
            }
            if (ticks % 5 == 0) {
                p.sendActionBar(EemiInfection.c(survivorBar(p, g, h)));
            }
        }
        if (pl.state == EemiInfection.State.PLAYING) {
            puddleTick();
        } else {
            puddles.clear();
        }
    }

    String survivorBar(Player p, EemiInfection.Gamer g, Gun h) {
        StringBuilder b = new StringBuilder();
        if (g.hitInfoUntil > ticks && g.hitInfo != null) {
            b.append(g.hitInfo).append(" &8| ");
        }
        Gun show = h != null ? h : gun(g.kit == null ? "assault" : g.kit);
        int m = g.mag.getOrDefault(show.id(), 0);
        int res = g.reserve.getOrDefault(show.id(), show.reserve());
        b.append(show.name()).append(" ");
        if (g.reloading != null && g.reloading.equals(show.id())) {
            b.append("&eRechargement...");
        } else {
            b.append(m == 0 ? "&c" : "&f").append(m).append("&7/").append(show.mag()).append(" &8(").append(res < 0 ? "∞" : String.valueOf(res)).append(")");
        }
        b.append(" &8| &6Grenades &f").append(g.grenades).append("&7/").append(cfg().getInt("grenades.max", 2));
        if (g.wounds > 0) {
            b.append(" &8| &c&lBLESSÉ");
        }
        return b.toString();
    }

    // ================================================================ grenades

    void throwGrenade(Player p, EemiInfection.Gamer g) {
        if (pl.state != EemiInfection.State.PLAYING || g.zombie) {
            if (pl.state == EemiInfection.State.FLEE) {
                g.hitInfo = "&7Garde ta grenade pour les zombies !";
                g.hitInfoUntil = ticks + 20;
            }
            return;
        }
        if (g.grenades <= 0 || g.stunUntil > ticks) {
            return;
        }
        if (inAcid(p.getLocation())) {
            g.hitInfo = "&2Impossible dans l'acide !";
            g.hitInfoUntil = ticks + 20;
            return;
        }
        if (p.hasCooldown(Material.FIRE_CHARGE)) {
            return;
        }
        Vector v = p.getEyeLocation().getDirection().multiply(cfg().getDouble("grenades.throw-speed", 1.1));
        Snowball s = launch(p, v, Material.FIRE_CHARGE, GRENADE_TAG, "grenade");
        if (s == null) {
            return;
        }
        g.grenades--;
        syncGrenades(p, g);
        p.setCooldown(Material.FIRE_CHARGE, 10);
        grenades.put(s.getUniqueId(), p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.8f, 1.4f);
        UUID sid = s.getUniqueId();
        pl.later(Math.max(5, Math.round(cfg().getDouble("grenades.fuse-seconds", 1.5) * 20)), () -> {
            if (grenades.containsKey(sid)) {
                Entity e = Bukkit.getEntity(sid);
                UUID owner = grenades.remove(sid);
                if (e != null) {
                    explode(e.getLocation(), owner);
                    e.remove();
                }
            }
        });
    }

    Snowball launch(Player p, Vector v, Material look, String tag, String model) {
        Snowball s;
        pl.spawning = true;
        try {
            s = p.launchProjectile(Snowball.class, v);
        } finally {
            pl.spawning = false;
        }
        if (s == null || !s.isValid()) {
            return null;
        }
        s.setItem(pl.skin(new ItemStack(look), model));
        s.addScoreboardTag(EemiInfection.TAG);
        s.addScoreboardTag(tag);
        s.setPersistent(false);
        return s;
    }

    void explode(Location at, UUID ownerId) {
        World w = at.getWorld();
        if (w == null || pl.world == null || !w.equals(pl.world)) {
            return;
        }
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        EemiInfection.Gamer go = owner == null ? null : pl.active(owner);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        w.spawnParticle(Particle.LARGE_SMOKE, at, 20, 1, 0.6, 1, 0.03);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 1.0f);
        if (pl.state != EemiInfection.State.PLAYING || go == null || go.zombie) {
            return;
        }
        double r = cfg().getDouble("grenades.radius", 4);
        double maxDmg = cfg().getDouble("grenades.damage", 16);
        double minDmg = cfg().getDouble("grenades.min-damage", 3);
        double kbMax = cfg().getDouble("grenades.knockback", 1.3);
        Location from = at.clone().add(0, 0.3, 0);
        // point d'impact colle a un mur : on ne teste pas la ligne de vue depuis l'interieur d'un bloc
        boolean los = !from.getBlock().getType().isOccluding();
        for (Player z : w.getPlayers()) {
            EemiInfection.Gamer gz = pl.active(z);
            if (gz == null || !gz.zombie || gz.inCrypt || z.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location c = z.getLocation().add(0, 0.9, 0);
            double d = c.distance(from);
            if (d > r) {
                continue;
            }
            Vector to = c.toVector().subtract(from.toVector());
            if (los && to.lengthSquared() > 1.0E-4 && w.rayTraceBlocks(from, to.clone().normalize(), Math.max(0.01, d - 0.3), FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            double f = 1.0 - d / r;
            double dmg = Math.max(minDmg, maxDmg * f);
            Vector kb = to.setY(0);
            if (kb.lengthSquared() < 1.0E-4) {
                kb = new Vector(pl.rnd.nextDouble() - 0.5, 0, pl.rnd.nextDouble() - 0.5);
            }
            kb.normalize().multiply(0.4 + kbMax * f).setY(0.35 + 0.3 * f);
            pl.damageZombie(z, owner, dmg, false, kb);
        }
    }

    // ================================================================ crachat acide

    void spit(Player z) {
        Vector v = z.getEyeLocation().getDirection().multiply(cfg().getDouble("abilities.acid-speed", 1.3));
        Snowball s = launch(z, v, Material.SLIME_BALL, ACID_TAG, "acide_vol");
        if (s == null) {
            return;
        }
        spits.put(s.getUniqueId(), z.getUniqueId());
        z.getWorld().playSound(z.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.3f, 0.6f);
        UUID sid = s.getUniqueId();
        pl.later(60L, () -> {
            if (spits.containsKey(sid)) {
                Entity e = Bukkit.getEntity(sid);
                UUID owner = spits.remove(sid);
                if (e != null) {
                    puddle(e.getLocation(), owner);
                    e.remove();
                }
            }
        });
    }

    void puddle(Location at, UUID owner) {
        if (pl.state != EemiInfection.State.PLAYING || pl.world == null || !at.getWorld().equals(pl.world)) {
            return;
        }
        // on pose la flaque au sol sous le point d'impact
        Location c = at.clone();
        int y = c.getBlockY();
        for (int i = 0; i < 8; i++) {
            Block b = pl.world.getBlockAt(c.getBlockX(), y - 1, c.getBlockZ());
            if (b.getType().isSolid()) {
                break;
            }
            y--;
        }
        c.setY(y + 0.1);
        puddles.add(new Puddle(c, ticks + Math.max(20, cfg().getInt("abilities.acid-duration", 6) * 20L), owner));
        pl.world.playSound(c, Sound.BLOCK_LAVA_EXTINGUISH, 1f, 1.4f);
        pl.world.playSound(c, Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
    }

    double acidRadius() {
        return cfg().getDouble("abilities.acid-radius", 3);
    }

    boolean inAcid(Location l) {
        double r = acidRadius();
        for (Puddle pd : puddles) {
            Location c = pd.center();
            if (!c.getWorld().equals(l.getWorld())) {
                continue;
            }
            double dx = l.getX() - c.getX();
            double dz = l.getZ() - c.getZ();
            double dy = l.getY() - c.getY();
            if (dx * dx + dz * dz <= r * r && dy > -1.2 && dy < 2.2) {
                return true;
            }
        }
        return false;
    }

    void puddleTick() {
        double r = acidRadius();
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(0x7CFF3A), 1.4f);
        for (Iterator<Puddle> itp = puddles.iterator(); itp.hasNext(); ) {
            Puddle pd = itp.next();
            if (ticks >= pd.end()) {
                itp.remove();
                continue;
            }
            Location c = pd.center();
            World w = c.getWorld();
            if (ticks % 4 == 0) {
                for (int i = 0; i < 14; i++) {
                    double a = pl.rnd.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(pl.rnd.nextDouble()) * r;
                    w.spawnParticle(Particle.DUST, c.getX() + Math.cos(a) * d, c.getY() + 0.1, c.getZ() + Math.sin(a) * d, 1, 0, 0, 0, 0, green);
                }
                w.spawnParticle(Particle.ITEM_SLIME, c, 4, r / 2, 0.1, r / 2, 0);
            }
            if ((ticks - pd.end()) % 20 != 0) {
                continue;
            }
            for (EemiInfection.Gamer g : pl.players.values()) {
                if (g.zombie || pl.gone.contains(g.id)) {
                    continue;
                }
                Player p = Bukkit.getPlayer(g.id);
                if (p == null || !pl.inWorld(p) || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                Location l = p.getLocation();
                double dx = l.getX() - c.getX();
                double dz = l.getZ() - c.getZ();
                double dy = l.getY() - c.getY();
                if (dx * dx + dz * dz > r * r || dy <= -1.2 || dy >= 2.2) {
                    continue;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, true, false, false));
                Vector out = new Vector(dx, 0, dz);
                if (out.lengthSquared() < 0.01) {
                    out = new Vector(pl.rnd.nextDouble() - 0.5, 0, pl.rnd.nextDouble() - 0.5);
                }
                out.normalize().multiply(cfg().getDouble("abilities.acid-push", 0.7)).setY(0.3);
                p.setVelocity(out);
                p.playSound(l, Sound.ENTITY_PLAYER_HURT, 0.6f, 1.5f);
                g.hitInfo = "&2Acide ! Sors de la flaque";
                g.hitInfoUntil = ticks + 20;
            }
        }
    }

    void clearGame() {
        grenades.clear();
        spits.clear();
        puddles.clear();
    }
}
