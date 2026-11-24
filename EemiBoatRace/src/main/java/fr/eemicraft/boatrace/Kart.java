package fr.eemicraft.boatrace;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Mode « Mario Kart » de la course de bateaux : boites ? sur le circuit, roulette, objets.
 * Les objets sont tires selon la place du pilote (le premier a surtout des objets faibles,
 * les derniers les plus forts). Section « kart » du config.yml.
 */
final class Kart {

    enum Item {
        CHAMPIGNON("Champignon", Material.RED_MUSHROOM, NamedTextColor.RED, "Acceleration de 1,5 s"),
        BANANE("Peau de banane", Material.YELLOW_DYE, NamedTextColor.YELLOW, "Piege pose derriere toi"),
        NEIGE("Boule de neige", Material.SNOWBALL, NamedTextColor.WHITE, "Tir droit devant : arrete celui qui est touche"),
        ENCRE("Nuage d'encre", Material.INK_SAC, NamedTextColor.DARK_GRAY, "Aveugle tous les autres pendant 3 s"),
        TURBO("Turbo maitrise", Material.FEATHER, NamedTextColor.AQUA, "10 s de vitesse, sans glisse"),
        ECLAIR("Eclair du leader", Material.LIGHTNING_ROD, NamedTextColor.GOLD, "Foudroie le premier"),
        ETOILE("Etoile", Material.NETHER_STAR, NamedTextColor.LIGHT_PURPLE, "Insensible aux objets 5 s + acceleration");

        final String label;
        final Material icon;
        final NamedTextColor color;
        final String desc;

        Item(String label, Material icon, NamedTextColor color, String desc) {
            this.label = label;
            this.icon = icon;
            this.color = color;
            this.desc = desc;
        }

        String key() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // Poids par defaut : {premier, dernier}, interpoles selon la place.
    private static final Map<Item, double[]> DEFAULT_WEIGHTS = new HashMap<>();
    static {
        DEFAULT_WEIGHTS.put(Item.CHAMPIGNON, new double[]{30, 15});
        DEFAULT_WEIGHTS.put(Item.BANANE, new double[]{35, 4});
        DEFAULT_WEIGHTS.put(Item.NEIGE, new double[]{25, 10});
        DEFAULT_WEIGHTS.put(Item.ENCRE, new double[]{5, 20});
        DEFAULT_WEIGHTS.put(Item.TURBO, new double[]{3, 20});
        DEFAULT_WEIGHTS.put(Item.ECLAIR, new double[]{0, 18});
        DEFAULT_WEIGHTS.put(Item.ETOILE, new double[]{2, 12});
    }

    private static final Color[] RAINBOW = {
        Color.fromARGB(170, 255, 60, 60), Color.fromARGB(170, 255, 170, 30), Color.fromARGB(170, 250, 230, 40),
        Color.fromARGB(170, 70, 220, 80), Color.fromARGB(170, 50, 160, 255), Color.fromARGB(170, 170, 80, 255)
    };

    private static final class Box {
        final Location loc;
        TextDisplay display;
        int cooldown;

        Box(Location loc) {
            this.loc = loc;
        }
    }

    private static final class Trap {
        final Location loc;
        final UUID owner;
        ItemDisplay display;
        int age;

        Trap(Location loc, UUID owner) {
            this.loc = loc;
            this.owner = owner;
        }
    }

    private static final class St {
        final List<Box> boxes = new ArrayList<>();
        final List<Trap> traps = new ArrayList<>();
        int ticks;
    }

    private final EemiBoatRace plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey projKey;
    private final Map<EemiBoatRace.Game, St> states = new HashMap<>();
    private final Random random = new Random();

    Kart(EemiBoatRace plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "kart_item");
        this.projKey = new NamespacedKey(plugin, "kart_shot");
    }

    private FileConfiguration cfg() {
        return this.plugin.getConfig();
    }

    boolean enabled() {
        return this.cfg().getBoolean("kart.enabled", true);
    }

    private int ticks(String key, double defSeconds) {
        return (int) Math.round(this.cfg().getDouble("kart." + key, defSeconds) * 20.0);
    }

    private double num(String key, double def) {
        return this.cfg().getDouble("kart." + key, def);
    }

    // ------------------------------------------------------------------ debut / fin de course

    void start(EemiBoatRace.Game g) {
        this.clear(g);
        if (!this.enabled()) {
            return;
        }
        St st = new St();
        this.states.put(g, st);
        Track t = g.track;
        double spacing = Math.max(60.0, this.num("box-spacing", 220));
        int rows = (int) Math.round(t.length / spacing);
        rows = Math.max(this.cfg().getInt("kart.min-boxes", 3), Math.min(this.cfg().getInt("kart.max-boxes", 16), rows));
        List<Double> lats = this.cfg().getDoubleList("kart.box-lateral");
        if (lats.isEmpty()) {
            lats = List.of(-2.5, 0.0, 2.5);
        }
        for (int k = 0; k < rows; ++k) {
            double s = t.length * (k + 0.5) / rows;
            if (Math.abs(t.wrap(s)) < 20.0) {
                s += 20.0;
            }
            s = t.solidBack(s);
            for (double lat : lats) {
                Location l = g.trackLocation(s, lat);
                l.setY(t.yAt(s) + 1.0);
                st.boxes.add(new Box(l));
            }
        }
        for (EemiBoatRace.Racer r : g.racers.values()) {
            r.item = null;
            r.rollTicks = 0;
            r.boost = 0;
            r.turbo = 0;
            r.stun = 0;
            r.star = 0;
            r.carry = 0.0;
        }
        for (Box b : st.boxes) {
            this.showBox(b);
        }
    }

    void clear(EemiBoatRace.Game g) {
        St st = this.states.remove(g);
        if (st == null) {
            return;
        }
        for (Box b : st.boxes) {
            if (b.display != null) {
                b.display.remove();
            }
        }
        for (Trap tr : st.traps) {
            if (tr.display != null) {
                tr.display.remove();
            }
        }
        for (EemiBoatRace.Racer r : g.racers.values()) {
            r.item = null;
            r.rollTicks = 0;
        }
    }

    private void showBox(Box b) {
        if (b.display != null && b.display.isValid()) {
            return;
        }
        if (!b.loc.isChunkLoaded()) {
            return;
        }
        b.display = b.loc.getWorld().spawn(b.loc, TextDisplay.class, td -> {
            td.text(Component.text("?", NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(RAINBOW[this.random.nextInt(RAINBOW.length)]);
            td.setShadowed(true);
            td.setPersistent(false);
            float sc = (float) this.num("box-scale", 2.5);
            td.setTransformation(new Transformation(new Vector3f(0f, -0.4f * sc, 0f), new AxisAngle4f(),
                    new Vector3f(sc, sc, sc), new AxisAngle4f()));
        });
    }

    // ------------------------------------------------------------------ chaque tick de course

    void tick(EemiBoatRace.Game g) {
        St st = this.states.get(g);
        if (st == null) {
            return;
        }
        ++st.ticks;
        int respawn = this.ticks("box-respawn", 3.0);
        for (int i = 0; i < st.boxes.size(); ++i) {
            Box b = st.boxes.get(i);
            if (b.cooldown > 0) {
                if (--b.cooldown == 0) {
                    this.showBox(b);
                }
                continue;
            }
            if (b.display == null || !b.display.isValid()) {
                if (st.ticks % 20 == 0) {
                    this.showBox(b);
                }
                continue;
            }
            if (st.ticks % 4 == 0) {
                b.display.setBackgroundColor(RAINBOW[(st.ticks / 4 + i) % RAINBOW.length]);
            }
        }
        // pieges
        int trapLife = this.ticks("banana-life", 60.0);
        st.traps.removeIf(tr -> {
            if (++tr.age > trapLife) {
                if (tr.display != null) {
                    tr.display.remove();
                }
                return true;
            }
            return false;
        });
        for (EemiBoatRace.Racer r : new ArrayList<>(g.racers.values())) {
            if (r.finish >= 0L) {
                continue;
            }
            Player p = Bukkit.getPlayer(r.id);
            if (p == null) {
                continue;
            }
            if (r.rollTicks > 0) {
                this.roll(p, r);
            }
            if (r.star > 0) {
                --r.star;
            }
            if (r.respawning) {
                continue;
            }
            Entity boat = this.plugin.boatOf(r);
            if (boat == null) {
                continue;
            }
            Location bl = boat.getLocation();
            // boites
            if (r.item == null && r.rollTicks == 0) {
                for (Box b : st.boxes) {
                    if (b.cooldown > 0) {
                        continue;
                    }
                    double dx = bl.getX() - b.loc.getX();
                    double dz = bl.getZ() - b.loc.getZ();
                    if (dx * dx + dz * dz > 4.84 || Math.abs(bl.getY() - b.loc.getY()) > 3.0) {
                        continue;
                    }
                    b.cooldown = Math.max(1, respawn);
                    if (b.display != null) {
                        b.display.remove();
                        b.display = null;
                    }
                    b.loc.getWorld().spawnParticle(Particle.FIREWORK, b.loc, 15, 0.4, 0.4, 0.4, 0.05);
                    p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
                    r.item = this.draw(g, r);
                    r.rollTicks = Math.max(2, this.ticks("roulette", 1.5));
                    break;
                }
            }
            // pieges
            for (Trap tr : new ArrayList<>(st.traps)) {
                if (tr.owner.equals(r.id) && tr.age < 20) {
                    continue;
                }
                double dx = bl.getX() - tr.loc.getX();
                double dz = bl.getZ() - tr.loc.getZ();
                if (dx * dx + dz * dz > 2.56 || Math.abs(bl.getY() - tr.loc.getY()) > 2.0) {
                    continue;
                }
                st.traps.remove(tr);
                if (tr.display != null) {
                    tr.display.remove();
                }
                if (r.star > 0) {
                    p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                    continue;
                }
                this.stun(p, r, this.ticks("banana-stun", 1.25), "Peau de banane !");
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
            }
            // effets de vitesse
            this.motion(r, boat);
        }
    }

    private void motion(EemiBoatRace.Racer r, Entity boat) {
        Vector v = boat.getVelocity();
        if (r.stun > 0) {
            --r.stun;
            r.carry = 0.0;
            boat.setVelocity(new Vector(0.0, Math.min(0.0, v.getY()), 0.0));
            boat.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, boat.getLocation().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0.0);
            return;
        }
        double h = Math.hypot(v.getX(), v.getZ());
        double sp = -1.0;
        if (r.boost > 0) {
            --r.boost;
            sp = Math.max(h, this.num("boost-speed", 1.8));
        } else if (r.star > 0) {
            sp = Math.max(h, this.num("star-speed", 1.5));
        } else if (r.turbo > 0) {
            sp = Math.max(h, this.num("turbo-min-speed", 0.8));
        }
        if (r.turbo > 0) {
            --r.turbo;
        }
        if (r.boost > 0 || r.star > 0) {
            r.carry = sp;
        } else if (r.carry > 0.0) {
            // fin du champignon ou de l'etoile : la vitesse retombe peu a peu au lieu de s'arreter net
            r.carry *= this.num("boost-decay", 0.96);
            if (r.carry <= h + 0.02 || r.carry < 0.4) {
                r.carry = 0.0;
            } else {
                sp = Math.max(sp, r.carry);
            }
        }
        if (sp < 0.0) {
            return;
        }
        sp = Math.min(sp, this.num("max-speed", 2.6));
        Vector dir = boat.getLocation().getDirection().setY(0.0);
        if (dir.lengthSquared() < 1.0E-6) {
            return;
        }
        dir.normalize();
        boat.setVelocity(new Vector(dir.getX() * sp, v.getY(), dir.getZ() * sp));
        if (r.boost > 0 || r.star > 0) {
            boat.getWorld().spawnParticle(r.star > 0 ? Particle.END_ROD : Particle.FLAME,
                    boat.getLocation().subtract(dir.clone().multiply(1.2)).add(0, 0.4, 0), 2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private void stun(Player p, EemiBoatRace.Racer r, int t, String why) {
        r.stun = Math.max(r.stun, t);
        r.boost = 0;
        p.playSound(p, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.8f, 1.4f);
        p.showTitle(Title.title(Component.empty(), Component.text(why, NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900L), Duration.ofMillis(200L))));
    }

    // ------------------------------------------------------------------ tirage et roulette

    private Item draw(EemiBoatRace.Game g, EemiBoatRace.Racer r) {
        List<EemiBoatRace.Racer> rank = g.ranking();
        int n = 0;
        for (EemiBoatRace.Racer o : rank) {
            if (o.finish < 0L) {
                ++n;
            }
        }
        int place = 1;
        for (EemiBoatRace.Racer o : rank) {
            if (o == r) {
                break;
            }
            if (o.finish < 0L) {
                ++place;
            }
        }
        double q = n <= 1 ? 0.5 : (double) (place - 1) / (double) (n - 1);
        double total = 0.0;
        double[] w = new double[Item.values().length];
        for (Item it : Item.values()) {
            double[] def = DEFAULT_WEIGHTS.get(it);
            List<Double> conf = this.cfg().getDoubleList("kart.weights." + it.key());
            double a = conf.size() >= 2 ? conf.get(0) : def[0];
            double b = conf.size() >= 2 ? conf.get(1) : def[1];
            double x = a + (b - a) * q;
            if (it == Item.ECLAIR && place == 1 && n > 1) {
                x = 0.0;
            }
            if (it == Item.TURBO) {
                // turbo maitrise retire du jeu (carte #54) : plus jamais tire
                x = 0.0;
            }
            w[it.ordinal()] = Math.max(0.0, x);
            total += w[it.ordinal()];
        }
        if (total <= 0.0) {
            return Item.CHAMPIGNON;
        }
        double pick = this.random.nextDouble() * total;
        for (Item it : Item.values()) {
            pick -= w[it.ordinal()];
            if (pick < 0.0) {
                return it;
            }
        }
        return Item.CHAMPIGNON;
    }

    private void roll(Player p, EemiBoatRace.Racer r) {
        --r.rollTicks;
        if (r.rollTicks > 0) {
            if (r.rollTicks % 3 == 0) {
                Item[] all = Item.values();
                Item shown = all[this.random.nextInt(all.length)];
                if (shown == Item.TURBO) {
                    shown = Item.CHAMPIGNON;
                }
                ItemStack it = new ItemStack(shown.icon);
                ItemMeta m = it.getItemMeta();
                m.displayName(Component.text("? ? ?", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                it.setItemMeta(m);
                p.getInventory().setItem(0, it);
                p.playSound(p, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + 0.6f * this.random.nextFloat());
            }
            return;
        }
        if (r.item == null) {
            p.getInventory().setItem(0, null);
            return;
        }
        p.getInventory().setItem(0, this.itemStack(r.item));
        p.getInventory().setHeldItemSlot(0);
        p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        p.showTitle(Title.title(Component.empty(), Component.text(r.item.label, r.item.color).decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1000L), Duration.ofMillis(200L))));
    }

    private ItemStack itemStack(Item item) {
        ItemStack it = new ItemStack(item.icon);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(item.label, item.color).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text(item.desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Clic droit ou Q pour l'utiliser", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(this.itemKey, PersistentDataType.STRING, item.name());
        it.setItemMeta(m);
        return it;
    }

    boolean isKartItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(this.itemKey, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ utilisation

    void use(EemiBoatRace.Game g, Player p) {
        EemiBoatRace.Racer r = g.racers.get(p.getUniqueId());
        if (r == null || r.finish >= 0L || g.phase != EemiBoatRace.Phase.RACE || this.states.get(g) == null) {
            return;
        }
        if (r.rollTicks > 0 || r.item == null) {
            return;
        }
        Item item = r.item;
        r.item = null;
        p.getInventory().setItem(0, null);
        Entity boat = this.plugin.boatOf(r);
        Location at = boat != null ? boat.getLocation() : p.getLocation();
        Vector dir = at.getDirection().setY(0.0);
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vector(1, 0, 0);
        }
        dir.normalize();
        switch (item) {
            case CHAMPIGNON -> {
                r.boost = this.ticks("boost-time", 1.5);
                p.playSound(p, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.2f);
            }
            case ETOILE -> {
                r.star = this.ticks("star-time", 5.0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, r.star, 0, false, false, false));
                p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
            }
            case TURBO -> {
                r.turbo = this.ticks("turbo-time", 10.0);
                p.playSound(p, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.2f);
                p.sendActionBar(Component.text("Turbo maitrise : plus de glisse pendant " + (r.turbo / 20) + " s !", NamedTextColor.AQUA));
            }
            case ENCRE -> {
                int t = this.ticks("ink-time", 3.0);
                for (EemiBoatRace.Racer o : g.racers.values()) {
                    Player op;
                    if (o == r || o.finish >= 0L || (op = Bukkit.getPlayer(o.id)) == null) {
                        continue;
                    }
                    if (o.star > 0) {
                        continue;
                    }
                    op.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, t, 0, false, false, false));
                    op.playSound(op, Sound.ENTITY_SQUID_SQUIRT, 1.0f, 0.8f);
                    op.showTitle(Title.title(Component.empty(), Component.text("Encre de " + p.getName() + " !", NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200L), Duration.ofMillis(300L))));
                }
                p.playSound(p, Sound.ENTITY_GLOW_SQUID_SQUIRT, 1.0f, 1.0f);
                g.broadcast("§8" + p.getName() + " §7a lance un §8nuage d'encre §7!");
            }
            case ECLAIR -> {
                EemiBoatRace.Racer target = null;
                for (EemiBoatRace.Racer o : g.ranking()) {
                    if (o.finish >= 0L || o == r) {
                        continue;
                    }
                    target = o;
                    break;
                }
                Player tp = target == null ? null : Bukkit.getPlayer(target.id);
                if (tp == null) {
                    p.sendActionBar(Component.text("Personne a foudroyer !", NamedTextColor.GRAY));
                    break;
                }
                Entity tb = this.plugin.boatOf(target);
                tp.getWorld().strikeLightningEffect(tb != null ? tb.getLocation() : tp.getLocation());
                if (target.star > 0) {
                    g.broadcast("§6" + p.getName() + " §7a lance l'eclair, mais §d" + tp.getName() + " §7etait protege par l'etoile !");
                } else {
                    this.stun(tp, target, this.ticks("lightning-stun", 1.5), "Foudroye par " + p.getName() + " !");
                    g.broadcast("§6" + p.getName() + " §7foudroie §e" + tp.getName() + " §7!");
                }
            }
            case BANANE -> {
                St st = this.states.get(g);
                Location l = at.clone().subtract(dir.clone().multiply(2.5));
                l.setY(at.getY() + 0.3);
                Trap tr = new Trap(l, r.id);
                tr.display = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(Material.YELLOW_DYE));
                    d.setBillboard(Display.Billboard.VERTICAL);
                    d.setPersistent(false);
                    d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            new Vector3f(1.4f, 1.4f, 1.4f), new AxisAngle4f()));
                });
                st.traps.add(tr);
                p.playSound(p, Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 1.2f);
            }
            case NEIGE -> {
                Snowball sb = p.launchProjectile(Snowball.class, dir.clone().multiply(this.num("snowball-speed", 2.4)).setY(0.05));
                sb.getPersistentDataContainer().set(this.projKey, PersistentDataType.STRING, r.id.toString());
                sb.setItem(new ItemStack(Material.SNOWBALL));
                p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
            }
        }
    }

    /**
     * Une boule de neige du kart touche quelque chose. 0 = pas une boule du kart (ou un bloc : elle casse
     * normalement), 1 = elle doit traverser (son propre bateau), 2 = touche, elle disparait.
     */
    int hit(EemiBoatRace.Game g, Projectile proj, Entity hit) {
        String shooter = proj.getPersistentDataContainer().get(this.projKey, PersistentDataType.STRING);
        if (shooter == null || hit == null) {
            return 0;
        }
        UUID target = null;
        if (hit instanceof Player hp) {
            target = hp.getUniqueId();
        } else if (this.plugin.isOurBoat(hit)) {
            String owner = hit.getPersistentDataContainer().get(this.plugin.boatKeyForKart(), PersistentDataType.STRING);
            if (owner != null) {
                target = UUID.fromString(owner);
            }
        }
        if (target == null || target.toString().equals(shooter)) {
            return 1;
        }
        EemiBoatRace.Racer r = g.racers.get(target);
        Player tp = Bukkit.getPlayer(target);
        if (r == null || tp == null || r.finish >= 0L) {
            return 1;
        }
        if (r.star > 0) {
            tp.playSound(tp, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
            return 2;
        }
        Player sp = Bukkit.getPlayer(UUID.fromString(shooter));
        this.stun(tp, r, this.ticks("snowball-stun", 1.25), "Touche" + (sp != null ? " par " + sp.getName() : "") + " !");
        if (sp != null) {
            sp.playSound(sp, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
        }
        return 2;
    }

    /** Texte ajoute a la barre d'action du pilote. */
    Component hud(EemiBoatRace.Racer r) {
        if (this.states.isEmpty()) {
            return Component.empty();
        }
        Component c = Component.empty();
        if (r.rollTicks > 0) {
            c = c.append(Component.text("  [ ? ? ? ]", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        } else if (r.item != null) {
            c = c.append(Component.text("  " + r.item.label, r.item.color).decorate(TextDecoration.BOLD))
                    .append(Component.text(" (clic droit)", NamedTextColor.GRAY));
        }
        if (r.stun > 0) {
            c = c.append(Component.text("  ETOURDI", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        } else if (r.star > 0) {
            c = c.append(Component.text("  ETOILE " + (r.star / 20 + 1) + "s", NamedTextColor.LIGHT_PURPLE));
        } else if (r.turbo > 0) {
            c = c.append(Component.text("  TURBO " + (r.turbo / 20 + 1) + "s", NamedTextColor.AQUA));
        }
        return c;
    }

    void onRespawn(EemiBoatRace.Racer r) {
        r.stun = 0;
        r.boost = 0;
        r.carry = 0.0;
    }
}
