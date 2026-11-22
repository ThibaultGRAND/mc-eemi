package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.PlayerData;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Monstres des strates (apparitions autour des joueurs dans le noir) et combats contre les gardiens. */
final class Mobs {

    record Drop(String item, double chance, int min, int max) {
    }

    record MobDef(String id, int strata, Class<? extends Mob> type, String name, double hp, double dmg, double speed,
                  double scale, Material hand, Material head, int weight, int group, int money, int xp, List<Drop> drops) {
    }

    static final List<MobDef> MOBS = List.of(
            new MobDef("rodeur", 1, Zombie.class, "§7Rôdeur des galeries", 24, 4, 1.0, 1.0, Material.IRON_PICKAXE, Material.LEATHER_HELMET, 5, 1, 2, 6,
                    List.of(new Drop("os_mineur", 0.5, 1, 2), new Drop("charbon", 0.4, 1, 3))),
            new MobDef("squelette", 1, org.bukkit.entity.Skeleton.class, "§fMineur squelette", 20, 3, 1.0, 1.0, Material.BOW, Material.CHAINMAIL_HELMET, 3, 1, 3, 8,
                    List.of(new Drop("os_mineur", 0.8, 1, 3))),
            new MobDef("ver", 1, org.bukkit.entity.Silverfish.class, "§8Ver de roche", 10, 2, 1.0, 1.0, null, null, 2, 3, 1, 3,
                    List.of(new Drop("minerai_fer", 0.2, 1, 1))),
            new MobDef("archer", 2, org.bukkit.entity.Bogged.class, "§2Archer moisi", 28, 4, 1.0, 1.0, Material.BOW, null, 4, 1, 4, 10,
                    List.of(new Drop("glande_spores", 0.6, 1, 2), new Drop("os_mineur", 0.4, 1, 2))),
            new MobDef("tisseuse", 2, org.bukkit.entity.CaveSpider.class, "§5Tisseuse de mycélium", 24, 4, 1.05, 1.3, null, null, 3, 2, 3, 9,
                    List.of(new Drop("glande_spores", 0.7, 1, 2))),
            new MobDef("gelee", 2, Slime.class, "§aGelée de spores", 18, 3, 1.0, 1.0, null, null, 3, 1, 3, 8,
                    List.of(new Drop("spore_lumineuse", 0.6, 1, 3))),
            new MobDef("forgeron", 3, org.bukkit.entity.WitherSkeleton.class, "§8Forgeron calciné", 80, 12, 1.15, 1.1, Material.IRON_SWORD, null, 5, 2, 6, 16,
                    List.of(new Drop("charbon", 0.7, 2, 4), new Drop("minerai_argent", 0.25, 1, 2))),
            new MobDef("braise", 3, org.bukkit.entity.MagmaCube.class, "§6Braise vivante", 50, 9, 1.15, 1.0, null, null, 4, 3, 4, 12,
                    List.of(new Drop("soufre", 0.6, 1, 3))),
            new MobDef("souffle", 3, org.bukkit.entity.Blaze.class, "§eSouffle des forges", 60, 11, 1.0, 1.0, null, null, 2, 2, 7, 18,
                    List.of(new Drop("soufre", 0.8, 2, 4))),
            // strate 4 (La Mer de Cristal) : 2.5.1 (#57) PV -40 % (120/90/100 -> 72/54/60), armure 12 -> 8 ; réglables (mobs.pv.<id>)
            new MobDef("noye", 4, org.bukkit.entity.Drowned.class, "§3Noyé des cristaux", 72, 16, 1.1, 1.1, null, null, 5, 2, 9, 24,
                    List.of(new Drop("cristal_resonant", 0.4, 1, 3), new Drop("minerai_cobalt", 0.3, 1, 2))),
            new MobDef("archer_cristal", 4, org.bukkit.entity.Stray.class, "§bArcher de cristal", 54, 13, 1.05, 1.0, Material.BOW, null, 3, 2, 8, 20,
                    List.of(new Drop("cristal_resonant", 0.6, 1, 3))),
            new MobDef("tourbillon", 4, org.bukkit.entity.Breeze.class, "§dTourbillon d'éclats", 60, 14, 1.0, 1.0, null, null, 2, 1, 10, 28,
                    List.of(new Drop("cristal_resonant", 0.7, 2, 4), new Drop("minerai_argent", 0.3, 1, 2))),
            // strate 5 (Le Néant) : 2.5.1 PV -50 % (180/70/140 -> 90/35/70), armure 16 -> 10, un peu plus dure que la strate 4
            new MobDef("rodeur_neant", 5, org.bukkit.entity.Enderman.class, "§5Rôdeur du Néant", 90, 22, 1.1, 1.0, null, null, 5, 1, 14, 36,
                    List.of(new Drop("essence_vide", 0.6, 1, 2), new Drop("minerai_enderite", 0.3, 1, 2))),
            new MobDef("mite", 5, org.bukkit.entity.Endermite.class, "§8Mite du vide", 35, 14, 1.15, 1.3, null, null, 3, 4, 6, 14,
                    List.of(new Drop("essence_vide", 0.4, 1, 1))),
            new MobDef("tourelle", 5, org.bukkit.entity.Shulker.class, "§dTourelle du Néant", 70, 16, 1.0, 1.0, null, null, 2, 1, 16, 40,
                    List.of(new Drop("essence_vide", 0.9, 1, 3), new Drop("eclat_neant", 0.15, 1, 1))));

    record GuardDef(int strata, Class<? extends Mob> type, String name, double hp, double dmg, double scale, BarColor color, String trophy, int money) {
    }

    static final List<GuardDef> GUARDS = List.of(
            new GuardDef(1, org.bukkit.entity.Vindicator.class, "§c§lLe Contremaître", 400, 9, 1.7, BarColor.RED, "coeur_contremaitre", 600),
            new GuardDef(2, org.bukkit.entity.Witch.class, "§d§lLa Reine Mycélienne", 650, 8, 2.0, BarColor.PURPLE, "coeur_reine", 1200),
            new GuardDef(3, org.bukkit.entity.WitherSkeleton.class, "§6§lLe Forgeron Oublié", 1800, 17, 1.9, BarColor.YELLOW, "coeur_forgeron", 3000),
            new GuardDef(4, org.bukkit.entity.Ravager.class, "§b§lLe Colosse de Cristal", 3000, 24, 1.3, BarColor.BLUE, "coeur_cristal", 6000),
            new GuardDef(5, org.bukkit.entity.Evoker.class, "§5§lVarenne, le Veilleur du Néant", 5000, 26, 1.8, BarColor.PINK, "coeur_neant", 12000));

    /** Offrande à poser sur l'autel pour réveiller un gardien (prise dans le sac de celui qui clique). */
    static final Map<Integer, Map<String, Integer>> OFFERINGS = Map.of(
            3, Map.of("lingot_argent_pur", 16, "circuit", 4),
            4, Map.of("lingot_cobalt_pur", 24, "lentille_cristal", 8, "bobine_resonante", 2),
            5, Map.of("lingot_enderite_pur", 24, "matiere_noire", 6, "noyau_vide", 2));

    /** À partir de cette strate, les monstres ont de l'armure, frappent plus souvent en groupe et percent l'armure ordinaire. */
    static final int HARD_FROM = 3;

    /** Combat en cours contre un gardien. */
    final class Fight {
        final GuardDef def;
        final Mob boss;
        final BossBar bar;
        final Map<UUID, Double> damage = new HashMap<>();
        int t;
        boolean phase2;
        int nobodyFor;
        /** Les PV d'une créature sont plafonnés à 1 024 : au-delà, le gardien encaisse moins (dégâts divisés). */
        double toughness = 1;

        Fight(GuardDef def, Mob boss) {
            this.def = def;
            this.boss = boss;
            bar = Bukkit.createBossBar(Bourse.strip(def.name()), def.color(), BarStyle.SEGMENTED_10);
        }
    }

    private final EemiAbysse plugin;
    final NamespacedKey key, guardKey, minionKey;
    private final Random random = new Random();
    final Map<Integer, Fight> fights = new HashMap<>();
    private final Map<Integer, Long> cooldown = new HashMap<>();

    Mobs(EemiAbysse plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "mob");
        guardKey = new NamespacedKey(plugin, "guardian");
        minionKey = new NamespacedKey(plugin, "sbire");
    }

    boolean isAbysseMob(Entity e) {
        return e.getPersistentDataContainer().has(key);
    }

    MobDef def(Entity e) {
        String id = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return null;
        for (MobDef d : MOBS) if (d.id().equals(id)) return d;
        return null;
    }

    boolean fightIn(int strata) {
        return fights.containsKey(strata);
    }

    // ------------------------------------------------------------------ apparitions

    /** Toutes les 3 s : quelques monstres autour de chaque joueur des strates, dans le noir. */
    void spawnTick() {
        for (Gen.Strata s : Gen.STRATAS) {
            World w = Bukkit.getWorld(s.world());
            if (w == null || w.getPlayers().isEmpty()) continue;
            int total = 0;
            for (LivingEntity e : w.getLivingEntities()) if (isAbysseMob(e)) total++;
            if (total >= plugin.getConfig().getInt("mobs.max-par-strate", 70)) continue;
            List<MobDef> pool = new ArrayList<>();
            for (MobDef d : MOBS) if (d.strata() == s.n) pool.add(d);
            if (pool.isEmpty()) continue;
            for (Player p : w.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                Location pl = p.getLocation();
                if (s.nearCamp(pl.getX(), pl.getZ(), 24) || s.nearLair(pl.getX(), pl.getZ(), Gen.LAIR_R + 4)) continue;
                int near = 0;
                for (Entity e : p.getNearbyEntities(32, 16, 32)) if (isAbysseMob(e)) near++;
                if (near >= plugin.getConfig().getInt("mobs.max-par-joueur", 6) + nearBonus(s.n)) continue;
                Location at = spot(p);
                if (at == null || s.nearCamp(at.getX(), at.getZ(), 20)) continue;
                // pas d'apparition naturelle dans l'antre pendant un combat de gardien (les sbires suffisent)
                if (fights.containsKey(s.n) && s.nearLair(at.getX(), at.getZ(), Gen.LAIR_R + 4)) continue;
                MobDef d = weighted(pool);
                for (int i = 0; i < d.group(); i++) spawn(d, at.clone().add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5));
            }
        }
        // ménage : monstres loin de tout joueur
        for (Gen.Strata s : Gen.STRATAS) {
            World w = Bukkit.getWorld(s.world());
            if (w == null) continue;
            for (LivingEntity e : w.getLivingEntities()) {
                if (!isAbysseMob(e) || e.getPersistentDataContainer().has(guardKey)) continue;
                boolean seen = false;
                for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < 72 * 72) seen = true;
                if (!seen) e.remove();
            }
        }
    }

    private MobDef weighted(List<MobDef> pool) {
        int sum = 0;
        for (MobDef d : pool) sum += d.weight();
        int r = random.nextInt(sum);
        for (MobDef d : pool) {
            r -= d.weight();
            if (r < 0) return d;
        }
        return pool.get(0);
    }

    /** Case libre à 12-26 blocs du joueur, sur un sol, dans le noir. */
    private Location spot(Player p) {
        World w = p.getWorld();
        Location base = p.getLocation();
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 12 + random.nextDouble() * 14;
            int x = base.getBlockX() + (int) (Math.cos(a) * d), z = base.getBlockZ() + (int) (Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = base.getBlockY() + 8; y > base.getBlockY() - 8; y--) {
                Block b = w.getBlockAt(x, y, z);
                if (!b.isEmpty() || !b.getRelative(0, 1, 0).isEmpty() || !b.getRelative(0, -1, 0).getType().isSolid()) continue;
                if (b.getLightFromBlocks() > 7) break;
                return b.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    Mob spawn(MobDef d, Location l) {
        return spawn(d, l, 0);
    }

    // ------------------------------------------------------------------ réglages (config, défauts dans le code)

    /** PV d'un monstre : {@code mobs.pv.<id>} du config, sinon la table {@link #MOBS} (plafonnés à 1 024 par Paper). */
    double hp(MobDef d) {
        return Math.max(1, Math.min(1024, plugin.getConfig().getDouble("mobs.pv." + d.id(), d.hp())));
    }

    /** Armure des monstres d'une strate ({@code mobs.armure.<n>}) : 8 en strates 3 et 4, 10 en strate 5 (2.5.1 ; avant 8/12/16). */
    double armor(int strata) {
        double def = strata < HARD_FROM ? 0 : strata >= 5 ? 10 : 8;
        return Math.max(0, Math.min(30, plugin.getConfig().getDouble("mobs.armure." + strata, def)));
    }

    /** Résistance au recul ({@code mobs.recul.<n>}) : 0,4 en strates 3 et 4, 0,5 en strate 5 (2.5.1 ; avant 0,4/0,5/0,6). */
    double knockback(int strata) {
        double def = strata < HARD_FROM ? 0 : strata >= 5 ? 0.5 : 0.4;
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("mobs.recul." + strata, def)));
    }

    /** Monstres en plus autour d'un joueur ({@code mobs.bonus-par-joueur.<n>}) : +3 en strate 3, +2 en 4 et 5 (2.5.1 ; avant +3 partout). */
    int nearBonus(int strata) {
        int def = strata < HARD_FROM ? 0 : strata == HARD_FROM ? 3 : 2;
        return Math.max(0, plugin.getConfig().getInt("mobs.bonus-par-joueur." + strata, def));
    }

    /** @param minionOf strate du gardien qui l'invoque (0 = monstre ordinaire) : marque « sbire », jamais sauvegardé. */
    Mob spawn(MobDef d, Location l, int minionOf) {
        return l.getWorld().spawn(l, d.type(), m -> {
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, d.id());
            if (minionOf > 0) {
                m.getPersistentDataContainer().set(minionKey, PersistentDataType.INTEGER, minionOf);
                m.setPersistent(false);
            }
            m.customName(Txt.c(d.name()));
            m.setCustomNameVisible(false);
            m.setRemoveWhenFarAway(true);
            m.setCanPickupItems(false);
            if (m instanceof Zombie z) z.setAdult();
            // un sbire gélatineux est de taille 1 (ne se divise pas en petits slimes sans marque), grossi par l'échelle
            if (m instanceof Slime sl) sl.setSize(minionOf > 0 ? 1 : 2);
            double hp = hp(d);
            attr(m, Attribute.MAX_HEALTH, hp);
            m.setHealth(hp);
            attr(m, Attribute.ATTACK_DAMAGE, d.dmg());
            AttributeInstance sp = m.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(sp.getBaseValue() * d.speed());
            attr(m, Attribute.SCALE, d.scale() * (minionOf > 0 && m instanceof Slime ? 2 : 1));
            if (d.strata() >= HARD_FROM) {
                attr(m, Attribute.ARMOR, armor(d.strata()));
                attr(m, Attribute.KNOCKBACK_RESISTANCE, knockback(d.strata()));
                attr(m, Attribute.FOLLOW_RANGE, 32);
            }
            // le Rôdeur du Néant ne reste pas neutre
            if (m instanceof org.bukkit.entity.Enderman) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!m.isValid()) return;
                Player t = null;
                for (Player p : m.getWorld().getPlayers())
                    if (p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE
                            && p.getLocation().distanceSquared(m.getLocation()) < 32 * 32 && (t == null
                            || p.getLocation().distanceSquared(m.getLocation()) < t.getLocation().distanceSquared(m.getLocation()))) t = p;
                if (t != null) m.setTarget(t);
            }, 10L);
            EntityEquipment eq = m.getEquipment();
            if (eq != null) {
                eq.clear();
                if (d.hand() != null) eq.setItemInMainHand(new ItemStack(d.hand()));
                if (d.head() != null) eq.setHelmet(new ItemStack(d.head()));
            }
        });
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    /** Butin d'un monstre tué : objets de l'Abysse, éclats et XP de Chasseur. */
    void onDeath(LivingEntity e, Player killer, List<ItemStack> drops) {
        drops.clear();
        if (e.getPersistentDataContainer().has(guardKey)) return;
        MobDef d = def(e);
        if (d == null) return;
        for (Drop dr : d.drops())
            if (random.nextDouble() < dr.chance())
                drops.add(Items.make(dr.item(), dr.min() + random.nextInt(dr.max() - dr.min() + 1)));
        if (killer == null) return;
        plugin.store.company(killer).money += EemiAbysse.gain(d.money());
        PlayerData pd = plugin.store.player(killer);
        pd.kills++;
        plugin.quests.jobXp(killer, "chasseur", d.xp());
        plugin.quests.onKill(killer, d.strata());
    }

    void clearWorld(World w) {
        for (LivingEntity e : w.getLivingEntities()) if (isAbysseMob(e)) e.remove();
    }

    // ------------------------------------------------------------------ gardiens

    /** Clic sur l'autel de l'antre : réveille le gardien. */
    void wake(Player p, Gen.Strata s) {
        if (fights.containsKey(s.n)) {
            p.sendMessage(Txt.PREFIX + "§cLe gardien est déjà réveillé !");
            return;
        }
        long cd = cooldown.getOrDefault(s.n, 0L) - System.currentTimeMillis();
        if (cd > 0) {
            p.sendMessage(Txt.PREFIX + "§7L'autel se recharge... (" + Txt.time(cd / 1000 + 1) + ")");
            return;
        }
        GuardDef g = null;
        for (GuardDef gd : GUARDS) if (gd.strata() == s.n) g = gd;
        if (g == null) return;
        Map<String, Integer> offer = OFFERINGS.get(s.n);
        if (offer != null) {
            StringBuilder miss = new StringBuilder();
            for (Map.Entry<String, Integer> o : offer.entrySet()) {
                int have = Bourse.countIn(p, o.getKey());
                if (have < o.getValue())
                    miss.append(miss.length() == 0 ? "" : ", ").append(o.getValue() - have).append(" ").append(Bourse.strip(Items.def(o.getKey()).name()));
            }
            if (miss.length() > 0) {
                p.sendMessage(Txt.PREFIX + "§7L'autel reste froid. Il réclame une offrande : §e"
                        + offerText(offer) + "§7. §cIl te manque " + miss + ".");
                p.playSound(p, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.5f);
                return;
            }
            for (Map.Entry<String, Integer> o : offer.entrySet()) Bourse.take(p, o.getKey(), o.getValue());
            p.sendMessage(Txt.PREFIX + "§6L'autel engloutit ton offrande...");
        }
        start(g, s, p.getWorld());
    }

    static String offerText(Map<String, Integer> offer) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> o : offer.entrySet())
            b.append(b.length() == 0 ? "" : ", ").append(o.getValue()).append(" ").append(Bourse.strip(Items.def(o.getKey()).name()));
        return b.toString();
    }

    /** Coup d'un monstre de l'Abysse sur un joueur : armure ordinaire percée et brûlures à partir de la strate 3. */
    void onHitPlayer(Entity mob, Player p, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        MobDef d = def(mob);
        int strata = d != null ? d.strata() : mob.getPersistentDataContainer().getOrDefault(guardKey, PersistentDataType.INTEGER, 0);
        if (strata < HARD_FROM) return;
        // armure en dessous du palier de la strate : +15 % par pièce (+30 % avec deux paliers de retard)
        double pierce = Profondeurs.pierce(strata, p.getInventory().getArmorContents());
        if (pierce > 0) {
            e.setDamage(e.getDamage() * (1 + pierce));
            if (random.nextInt(6) == 0) p.sendActionBar(Txt.c(Profondeurs.pierceText(strata)));
        }
        if (d == null) return;
        switch (d.id()) {
            case "forgeron" -> p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 60, 0));
            case "braise" -> p.setFireTicks(Math.max(p.getFireTicks(), 60));
            case "souffle" -> p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 60, 0));
            case "noye" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            case "archer_cristal" -> p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            case "tourbillon" -> p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
            case "rodeur_neant" -> p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
            case "mite" -> p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1));
            case "tourelle" -> p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
            default -> { }
        }
    }

    void start(GuardDef g, Gen.Strata s, World w) {
        Location c = s.lair(w).add(0, 0, 6);
        int players = 0;
        for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(c) < 35 * 35) players++;
        double wanted = g.hp() * (1 + (g.strata() >= HARD_FROM ? 0.8 : 0.6) * Math.max(0, players - 1));
        double hp = Math.min(MAX_HP, wanted);
        Mob boss = w.spawn(c, g.type(), m -> {
            m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "gardien" + g.strata());
            m.getPersistentDataContainer().set(guardKey, PersistentDataType.INTEGER, g.strata());
            m.customName(Txt.c(g.name()));
            m.setCustomNameVisible(true);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(false);
            m.setCanPickupItems(false);
            attr(m, Attribute.MAX_HEALTH, hp);
            m.setHealth(hp);
            attr(m, Attribute.ATTACK_DAMAGE, g.dmg());
            attr(m, Attribute.SCALE, g.scale());
            attr(m, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            attr(m, Attribute.FOLLOW_RANGE, 40);
            EntityEquipment eq = m.getEquipment();
            if (eq != null && g.strata() == 1) eq.setItemInMainHand(new ItemStack(Material.IRON_AXE));
            if (eq != null && g.strata() == 3) eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            if (g.strata() >= 4) attr(m, Attribute.ARMOR, 16);
        });
        Fight f = new Fight(g, boss);
        f.toughness = wanted / hp;
        fights.put(s.n, f);
        w.playSound(c, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.7f);
        for (Player pl : w.getPlayers())
            if (pl.getLocation().distanceSquared(c) < 60 * 60) {
                pl.sendTitlePart(TitlePart.TITLE, Txt.c(g.name()));
                pl.sendTitlePart(TitlePart.SUBTITLE, Txt.c("§7Gardien de " + s.name));
            }
    }

    /** Chaque seconde : capacités des gardiens, barre de vie, fin du combat. */
    void fightTick() {
        for (Map.Entry<Integer, Fight> en : new ArrayList<>(fights.entrySet())) {
            Fight f = en.getValue();
            Gen.Strata s = Gen.strata(en.getKey());
            Mob b = f.boss;
            if (!b.isValid()) {
                end(en.getKey(), f, !b.isDead() ? false : b.getHealth() <= 0);
                continue;
            }
            World w = b.getWorld();
            Location lair = s.lair(w);
            List<Player> near = new ArrayList<>();
            for (Player p : w.getPlayers())
                if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(lair) < 40 * 40) near.add(p);
            for (Player p : Bukkit.getOnlinePlayers())
                if (near.contains(p)) f.bar.addPlayer(p);
                else f.bar.removePlayer(p);
            double max = b.getAttribute(Attribute.MAX_HEALTH).getValue();
            f.bar.setProgress(Math.max(0, Math.min(1, b.getHealth() / max)));
            if (near.isEmpty()) {
                if (++f.nobodyFor > 20) {
                    b.remove();
                    end(en.getKey(), f, false);
                }
                continue;
            }
            f.nobodyFor = 0;
            if (b.getLocation().distanceSquared(lair) > (Gen.LAIR_R + 2) * (Gen.LAIR_R + 2)) b.teleport(lair.clone().add(0, 0, 3));
            f.t++;
            if (!f.phase2 && b.getHealth() < max / 2) {
                f.phase2 = true;
                phase2(f, near);
            }
            if (f.def.strata() == 1) contremaitre(f, near);
            else if (f.def.strata() == 2) reine(f, near);
            else if (f.def.strata() == 3) forgeron(f, near);
            else if (f.def.strata() == 4) colosse(f, near);
            else veilleur(f, near);
        }
    }

    private void contremaitre(Fight f, List<Player> near) {
        Mob b = f.boss;
        World w = b.getWorld();
        if (f.t % 10 == 0) {
            w.playSound(b.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.6f);
            w.spawnParticle(Particle.EXPLOSION, b.getLocation(), 6, 3, 0.3, 3);
            for (Player p : near)
                if (p.getLocation().distanceSquared(b.getLocation()) < 36) {
                    p.damage(5, b);
                    Vector v = p.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
                    p.setVelocity(v.normalize().multiply(1.1).setY(0.6));
                }
        }
        if (f.t % 18 == 0) {
            if (summon(f, MOBS.get(0), 2 + near.size(), near.size()) > 0)
                say(near, "§c" + Bourse.strip(f.def.name()) + " §7: §fAu travail, les gars !");
        }
        if (f.phase2 && f.t % 14 == 7) rockfall(f, near);
    }

    private void rockfall(Fight f, List<Player> near) {
        World w = f.boss.getWorld();
        List<Location> marks = new ArrayList<>();
        for (Player p : near) marks.add(p.getLocation().clone());
        for (Location l : marks) w.spawnParticle(Particle.DUST_PLUME, l.clone().add(0, 0.2, 0), 30, 1.2, 0.05, 1.2);
        w.playSound(f.boss.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 2, 0.5f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location l : marks) {
                w.spawnParticle(Particle.BLOCK, l.clone().add(0, 3, 0), 60, 1.2, 2, 1.2, Material.GRAVEL.createBlockData());
                w.playSound(l, Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
                for (Player p : near) if (p.isValid() && p.getWorld() == w && p.getLocation().distanceSquared(l) < 6.25) p.damage(7, f.boss);
            }
        }, 40L);
    }

    private void reine(Fight f, List<Player> near) {
        Mob b = f.boss;
        World w = b.getWorld();
        if (f.t % 8 == 0 && !near.isEmpty()) {
            Player target = near.get(random.nextInt(near.size()));
            w.spawn(target.getLocation(), AreaEffectCloud.class, c -> {
                c.setRadius(3);
                c.setDuration(120);
                c.setColor(org.bukkit.Color.fromRGB(120, 200, 80));
                c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 80, 1), true);
                c.setPersistent(false);
            });
            w.playSound(target.getLocation(), Sound.BLOCK_FUNGUS_BREAK, 1.5f, 0.6f);
        }
        if (f.t % 15 == 0) {
            int n = summon(f, MOBS.get(5), 2 + near.size(), near.size());
            n += summon(f, MOBS.get(4), 1, near.size());
            if (n > 0) say(near, "§d" + Bourse.strip(f.def.name()) + " §7: §fGrandissez, mes enfants...");
        }
        if (f.phase2 && f.t % 25 < 8) {
            double max = b.getAttribute(Attribute.MAX_HEALTH).getValue();
            b.setHealth(Math.min(max, b.getHealth() + max * 0.01));
            w.spawnParticle(Particle.SPORE_BLOSSOM_AIR, b.getLocation().add(0, 2, 0), 30, 1.5, 1.5, 1.5);
        }
    }

    /** Le Forgeron Oublié : pluie de braises sur les joueurs, apprentis calcinés, et en phase 2 le sol s'embrase. */
    private void forgeron(Fight f, List<Player> near) {
        Mob b = f.boss;
        World w = b.getWorld();
        if (f.t % 9 == 0) {
            for (Player p : near) {
                Location l = p.getLocation().clone();
                w.spawnParticle(Particle.FLAME, l.clone().add(0, 0.2, 0), 40, 1.3, 0.05, 1.3, 0.01);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.spawnParticle(Particle.LAVA, l.clone().add(0, 3, 0), 30, 1.3, 1.5, 1.3);
                    w.playSound(l, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.7f);
                    for (Player q : near)
                        if (q.isValid() && q.getWorld() == w && q.getLocation().distanceSquared(l) < 6.25) {
                            q.damage(6, b);
                            q.setFireTicks(60);
                        }
                }, 30L);
            }
        }
        if (f.t % 16 == 0) {
            if (summon(f, MOBS.get(6), 1 + near.size(), near.size()) > 0)
                say(near, "§6" + Bourse.strip(f.def.name()) + " §7: §fÀ la forge, apprentis !");
        }
        if (f.phase2 && f.t % 6 == 0)
            for (Player p : near)
                if (p.isOnGround() && !p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                    p.setFireTicks(Math.max(p.getFireTicks(), 40));
                    w.spawnParticle(Particle.SMALL_FLAME, p.getLocation(), 10, 0.4, 0.05, 0.4, 0.01);
                }
    }

    // ------------------------------------------------------------------ gardiens des strates 4 et 5

    /** Le Colosse de Cristal : pluie d'éclats, onde de choc, noyés, et en phase 2 une carapace de cristal. */
    private void colosse(Fight f, List<Player> near) {
        Mob b = f.boss;
        World w = b.getWorld();
        if (f.t % 8 == 0) {
            for (Player p : near) {
                Location l = p.getLocation().clone();
                w.spawnParticle(Particle.END_ROD, l.clone().add(0, 0.2, 0), 40, 1.3, 0.05, 1.3, 0.01);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.spawnParticle(Particle.BLOCK, l.clone().add(0, 3, 0), 50, 1.3, 2, 1.3, Material.AMETHYST_BLOCK.createBlockData());
                    w.playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 0.6f);
                    for (Player q : near)
                        if (q.isValid() && q.getWorld() == w && q.getLocation().distanceSquared(l) < 6.25) {
                            q.damage(8, b);
                            q.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        }
                }, 40L);
            }
        }
        if (f.t % 14 == 0) {
            w.playSound(b.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2, 0.7f);
            w.spawnParticle(Particle.SONIC_BOOM, b.getLocation().add(0, 1, 0), 1);
            for (Player p : near)
                if (p.getLocation().distanceSquared(b.getLocation()) < 49) {
                    p.damage(7, b);
                    Vector v = p.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
                    p.setVelocity(v.normalize().multiply(1.3).setY(0.7));
                }
        }
        if (f.t % 18 == 0) colosseMinions(f, near);
        if (f.phase2 && f.t % 20 == 0) {
            b.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 3));
            w.spawnParticle(Particle.WAX_OFF, b.getLocation().add(0, 1.5, 0), 60, 1.5, 1.5, 1.5);
            w.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2, 0.5f);
            say(near, "§b" + Bourse.strip(f.def.name()) + " §7: §f*le cristal se referme sur lui*");
        }
    }

    // SBIRES : invocations du Colosse de Cristal (plafonnées par summon)
    private void colosseMinions(Fight f, List<Player> near) {
        Mob b = f.boss;
        MobDef noye = byId("noye"), archer = byId("archer_cristal");
        int n = summon(f, noye, 1 + near.size(), near.size());
        if (f.phase2) n += summon(f, archer, 1, near.size());
        if (n > 0) say(near, "§b" + Bourse.strip(f.def.name()) + " §7: §f*grondement de cristal*");
    }

    /**
     * Varenne, le Veilleur du Néant : aspire un joueur (lévitation et ténèbres), orbes du vide sur tous, mites et
     * rôdeurs ; en phase 2 il se téléporte derrière un joueur et le vide ronge ceux qui s'éloignent de lui.
     */
    private void veilleur(Fight f, List<Player> near) {
        Mob b = f.boss;
        World w = b.getWorld();
        if (f.t % 7 == 0 && !near.isEmpty()) {
            Player t = near.get(random.nextInt(near.size()));
            t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1));
            t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
            w.playSound(t.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.6f);
            t.sendMessage("§5" + Bourse.strip(f.def.name()) + " §7: §fDescends... encore...");
        }
        if (f.t % 10 == 0) {
            for (Player p : near) {
                Location l = p.getLocation().clone();
                w.spawnParticle(Particle.REVERSE_PORTAL, l.clone().add(0, 0.2, 0), 60, 1.3, 0.05, 1.3, 0.05);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    w.spawnParticle(Particle.DRAGON_BREATH, l.clone().add(0, 1, 0), 40, 1.3, 1, 1.3, 0.01);
                    w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.5f);
                    for (Player q : near)
                        if (q.isValid() && q.getWorld() == w && q.getLocation().distanceSquared(l) < 6.25) q.damage(9, b);
                }, 30L);
            }
        }
        if (f.t % 16 == 0) veilleurMinions(f, near);
        if (f.phase2) {
            if (f.t % 12 == 6 && !near.isEmpty()) {
                Player t = near.get(random.nextInt(near.size()));
                Location behind = t.getLocation().clone().subtract(t.getLocation().getDirection().setY(0).normalize().multiply(2));
                if (behind.getBlock().isEmpty() && behind.clone().add(0, 1, 0).getBlock().isEmpty()) {
                    w.spawnParticle(Particle.PORTAL, b.getLocation(), 60, 0.5, 1, 0.5);
                    b.teleport(behind);
                    w.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
                }
            }
            if (f.t % 4 == 0)
                for (Player p : near)
                    if (p.getLocation().distanceSquared(b.getLocation()) > 12 * 12) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                        p.spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.01);
                    }
        }
    }

    // SBIRES : invocations de Varenne (plafonnées par summon)
    private void veilleurMinions(Fight f, List<Player> near) {
        Mob b = f.boss;
        MobDef mite = byId("mite"), rodeur = byId("rodeur_neant");
        int n = summon(f, mite, 2 + near.size(), near.size());
        n += summon(f, rodeur, 1, near.size());
        if (n > 0) say(near, "§5" + Bourse.strip(f.def.name()) + " §7: §fVous ne remonterez pas.");
    }

    static MobDef byId(String id) {
        for (MobDef d : MOBS) if (d.id().equals(id)) return d;
        return null;
    }

    private void phase2(Fight f, List<Player> near) {
        if (f.def.strata() == 4) say(near, "§b" + Bourse.strip(f.def.name()) + " §7: §f*le cristal hurle*");
        else if (f.def.strata() == 5) say(near, "§5" + Bourse.strip(f.def.name()) + " §7: §fJ'ai vu le fond. Il n'y a rien. Restez avec moi.");
        if (f.def.strata() == 3) say(near, "§6" + Bourse.strip(f.def.name()) + " §7: §fLe feu de la forge ne s'éteint jamais !");
        else if (f.def.strata() == 1) say(near, "§c" + Bourse.strip(f.def.name()) + " §7: §fLa roche m'appelle ! Elle va vous ensevelir !");
        else if (f.def.strata() == 2) say(near, "§d" + Bourse.strip(f.def.name()) + " §7: §fMon jardin se referme sur vous...");

        f.boss.getWorld().playSound(f.boss.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.8f);
    }

    // ------------------------------------------------------------------ sbires des gardiens

    /**
     * Plafond de sbires vivants pour un combat : {@code gardiens.max-sbires} (6) + {@code gardiens.sbires-par-joueur} (2)
     * par joueur en plus dans l'antre, jamais plus de {@code gardiens.max-sbires-absolu} (12). Vaut pour tout gardien.
     */
    int minionCap(int players) {
        var c = plugin.getConfig();
        int cap = c.getInt("gardiens.max-sbires", 6) + c.getInt("gardiens.sbires-par-joueur", 2) * Math.max(0, players - 1);
        return Math.max(0, Math.min(cap, c.getInt("gardiens.max-sbires-absolu", 12)));
    }

    /** Sbires vivants invoqués par le gardien de cette strate (comptés par leur marque, pas par leur type). */
    int minions(World w, int strata) {
        int n = 0;
        for (LivingEntity e : w.getLivingEntities())
            if (!e.isDead() && e.getPersistentDataContainer().getOrDefault(minionKey, PersistentDataType.INTEGER, 0) == strata) n++;
        return n;
    }

    /** Invoque jusqu'à {@code wanted} sbires autour du gardien, sans dépasser le plafond. Rend le nombre invoqué. */
    int summon(Fight f, MobDef d, int wanted, int players) {
        Mob b = f.boss;
        int n = Math.min(wanted, minionCap(players) - minions(b.getWorld(), f.def.strata()));
        for (int i = 0; i < n; i++) spawn(d, b.getLocation().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), f.def.strata());
        return Math.max(0, n);
    }

    /** Retire les sbires du gardien de cette strate (fin de combat, remise à neuf, arrêt). */
    void removeMinions(World w, int strata) {
        if (w == null) return;
        for (LivingEntity e : w.getLivingEntities())
            if (e.getPersistentDataContainer().getOrDefault(minionKey, PersistentDataType.INTEGER, 0) == strata) {
                w.spawnParticle(Particle.POOF, e.getLocation().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0.02);
                e.remove();
            }
    }

    private static void say(List<Player> to, String msg) {
        for (Player p : to) p.sendMessage(msg);
    }

    /** Vie maximale d'une créature dans Paper (attribut max_health). */
    static final double MAX_HP = 1024;

    /** Diviseur des dégâts reçus par ce gardien (1 si ce n'est pas un gardien en combat). */
    double toughness(Entity victim) {
        Integer s = victim.getPersistentDataContainer().get(guardKey, PersistentDataType.INTEGER);
        Fight f = s == null ? null : fights.get(s);
        return f == null ? 1 : f.toughness;
    }

    /** #57 : après un coup sur un monstre (pas un gardien, qui a sa barre de boss), sa vie dans la barre d'action. */
    void showHealth(Player attacker, Entity victim) {
        if (!(victim instanceof LivingEntity le) || victim.getPersistentDataContainer().has(guardKey)) return;
        MobDef d = def(victim);
        if (d == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!attacker.isOnline()) return;
            AttributeInstance mx = le.getAttribute(Attribute.MAX_HEALTH);
            double max = mx == null ? d.hp() : mx.getValue();
            double hp = le.isDead() || !le.isValid() ? 0 : Math.max(0, le.getHealth());
            attacker.sendActionBar(Txt.c(d.name() + " §c❤ §f" + (int) Math.ceil(hp) + "§7/" + (int) Math.round(max)
                    + " " + Txt.bar(max <= 0 ? 0 : hp / max, 10, "§c", "§8")));
        });
    }

    void onDamage(Entity victim, Player attacker, double dmg) {
        Integer s = victim.getPersistentDataContainer().get(guardKey, PersistentDataType.INTEGER);
        if (s == null) return;
        Fight f = fights.get(s);
        if (f != null) f.damage.merge(attacker.getUniqueId(), dmg, Double::sum);
    }

    /** Le gardien est mort ou a été abandonné. */
    private void end(int strata, Fight f, boolean won) {
        fights.remove(strata);
        f.bar.removeAll();
        removeMinions(f.boss.getWorld(), strata);
        cooldown.put(strata, System.currentTimeMillis() + 30_000L);
        if (!won) return;
        Gen.Strata s = Gen.strata(strata);
        for (UUID u : f.damage.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            PlayerData d = plugin.store.player(p);
            boolean first = d.unlocked <= strata;
            if (first) d.unlocked = strata + 1;
            plugin.store.company(p).money += EemiAbysse.gain(f.def.money());
            for (ItemStack rest : p.getInventory().addItem(Items.make(f.def.trophy(), 1)).values()) p.getWorld().dropItem(p.getLocation(), rest);
            plugin.quests.jobXp(p, "chasseur", 150 * strata);
            plugin.quests.onGuardian(p, strata);
            p.sendTitlePart(TitlePart.TITLE, Txt.c("§6§lVICTOIRE"));
            p.sendTitlePart(TitlePart.SUBTITLE, Txt.c(f.def.name() + " §7est tombé" + (strata == 2 ? "e" : "")));
            if (strata == 3) p.sendMessage(Txt.PREFIX + "§7Le Cœur du Forgeron servira à percer plus bas : garde-le.");
            if (strata == 3 && first) p.sendMessage(Txt.PREFIX + "§7Avec lui, l'Établi de précision forge la §6Pioche de forge§7 : sans elle, le cristal de la strate 4 ne cède pas.");
            if (strata == 4) p.sendMessage(Txt.PREFIX + "§7Le Cœur de cristal taille la §bPioche de cristal§7 : sans elle, le Néant ne s'entame pas.");
            p.sendMessage(Txt.PREFIX + "§a+" + Txt.money(EemiAbysse.gain(f.def.money())) + " §aet " + Items.def(f.def.trophy()).name() + "§a.");
            if (strata == 5) Profondeurs.finale(plugin, p, first);
            else if (first) {
                Gen.Strata next = Gen.strata(strata + 1);
                p.sendMessage(Txt.PREFIX + "§e" + (next != null ? "La strate " + (strata + 1) + " (" + next.name + ") est ouverte au monte-charge !"
                        : "La strate " + (strata + 1) + " n'est pas encore creusée... reviens bientôt."));
            }
        }
        plugin.broadcastAbysse(Txt.PREFIX + "§6" + Bourse.strip(f.def.name()) + " §7a été vaincu" + (strata == 2 ? "e" : "") + " dans " + s.name + " !");
    }

    /** Arrête le combat de gardien d'une strate (remise à neuf), sans récompense. */
    void abort(int strata) {
        Fight f = fights.remove(strata);
        if (f == null) return;
        f.bar.removeAll();
        removeMinions(f.boss.getWorld(), strata);
        f.boss.remove();
    }

    void stopAll() {
        for (Map.Entry<Integer, Fight> en : fights.entrySet()) {
            Fight f = en.getValue();
            f.bar.removeAll();
            removeMinions(f.boss.getWorld(), en.getKey());
            f.boss.remove();
        }
        fights.clear();
    }
}
