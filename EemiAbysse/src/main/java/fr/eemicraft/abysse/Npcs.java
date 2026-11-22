package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import fr.eemicraft.abysse.Store.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PNJ de Bordgouffre (villageois immobiles) et leurs menus : marchand, ingénieure, notaire, liftier, cartographe. */
final class Npcs {

    /** @param angle position sur la place en degrés depuis le nord (sens horaire), -1 = à côté du monte-charge */
    record Def(String id, String name, String title, String job, double angle, Material roof) {
    }

    static final List<Def> DEFS = List.of(
            new Def("gaspard", "Gaspard", "Vieux Mineur", "toolsmith", 180, Material.BROWN_WOOL),
            new Def("odette", "Odette", "Banquière", "librarian", 135, Material.PURPLE_WOOL),
            new Def("margaux", "Margaux", "Ingénieure", "armorer", 225, Material.ORANGE_WOOL),
            new Def("bastien", "Bastien", "Marchand", "butcher", 90, Material.RED_WOOL),
            new Def("lebrun", "Maître Lebrun", "Notaire", "cartographer", 270, Material.GREEN_WOOL),
            new Def("ines", "Inès", "Cartographe", "cartographer", 45, Material.CYAN_WOOL),
            new Def("prosper", "Prosper", "Brocanteur", "cleric", 315, Material.YELLOW_WOOL),
            new Def("leon", "Léon", "Liftier", "mason", -1, Material.GRAY_WOOL));

    static Def def(String id) {
        for (Def d : DEFS) if (d.id().equals(id)) return d;
        return null;
    }

    static Location position(World w, Def d) {
        if (d.angle() < 0) return new Location(w, 3.5, 65, -(Worlds.HOLE + 3) + 0.5);
        double a = Math.toRadians(d.angle());
        return new Location(w, Math.round(Math.sin(a) * 22) + 0.5, 65, Math.round(-Math.cos(a) * 22) + 0.5);
    }

    /** Ce que vend Bastien : objet, quantité, prix. */
    record Offer(String item, Material vanilla, int amount, int price) {
    }

    static final List<Offer> SHOP = List.of(
            // outils de base gratuits (un seul à la fois) ; bronze (Fonderie) et acier (Alliageur) se fabriquent à l'Assembleur
            new Offer("pioche_fer", null, 1, 0), new Offer("epee_pierre", null, 1, 0),
            new Offer("lampe", null, 1, 200), new Offer("fusee", null, 2, 40), new Offer("kit_reparation", null, 1, 150),
            new Offer(null, Material.BREAD, 8, 16), new Offer(null, Material.COOKED_BEEF, 8, 32),
            new Offer(null, Material.TORCH, 16, 12), new Offer(null, Material.LANTERN, 4, 20),
            new Offer(null, Material.HOPPER, 4, 60), new Offer(null, Material.WATER_BUCKET, 1, 30),
            new Offer(null, Material.CHEST, 2, 15), new Offer(null, Material.STONE_BRICKS, 64, 30),
            new Offer(null, Material.OAK_PLANKS, 64, 20), new Offer(null, Material.SPRUCE_LOG, 32, 25),
            new Offer(null, Material.GLASS, 32, 30), new Offer(null, Material.COBBLESTONE, 64, 15),
            new Offer(null, Material.DIRT, 64, 10), new Offer(null, Material.OAK_SAPLING, 4, 20));

    /** Brocante de Prosper : objets à usages limités. */
    static final List<Offer> BROCANTE = List.of(
            new Offer("totem_garde", null, 1, 1500), new Offer("larme_soin", null, 1, 600),
            new Offer("trefle", null, 1, 2000), new Offer("boussole_retour", null, 1, 800), new Offer("lampe_eternelle", null, 1, 4000));

    private final EemiAbysse plugin;
    private final NamespacedKey key;
    private final Map<String, UUID> spawned = new HashMap<>();

    Npcs(EemiAbysse plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "npc");
    }

    String npcId(Entity e) {
        return e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** (Re)fait apparaître les PNJ manquants, si leur chunk est chargé (toutes les 5 s). */
    void ensure() {
        ensurePlots();
        World w = plugin.worlds.hub();
        if (w == null) return;
        for (Def d : DEFS) {
            Location l = position(w, d);
            if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
                spawned.remove(d.id());
                continue;
            }
            UUID u = spawned.get(d.id());
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                if (e.getLocation().distanceSquared(l) > 1) e.teleport(face(l));
                continue;
            }
            for (Entity old : w.getNearbyEntities(l, 3, 3, 3))
                if (d.id().equals(npcId(old))) old.remove();
            Villager v = w.spawn(face(l), Villager.class, vv -> {
                vv.setPersistent(false);
                vv.setAI(false);
                vv.setInvulnerable(true);
                vv.setSilent(true);
                vv.setCollidable(false);
                vv.setRemoveWhenFarAway(false);
                vv.customName(Txt.c("§6" + d.name() + " §7(" + d.title() + ")"));
                vv.setCustomNameVisible(true);
                vv.setProfession(profession(d.job()));
                vv.setVillagerLevel(5);
                vv.getPersistentDataContainer().set(key, PersistentDataType.STRING, d.id());
            });
            spawned.put(d.id(), v.getUniqueId());
        }
    }

    /** Un liftier dans chaque Concession (à 3 blocs du point d'arrivée) : ouvre le monte-charge vers les strates. */
    private void ensurePlots() {
        World w = plugin.worlds.base();
        if (w == null) return;
        for (Company c : plugin.store.companies.values()) {
            if (c.plot < 0) continue;
            Location center = Worlds.plotCenter(w, c.plot);
            int x = center.getBlockX() + 3, z = center.getBlockZ();
            String key = "plot:" + c.id;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                spawned.remove(key);
                continue;
            }
            Location l = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5, 90, 0);
            UUID u = spawned.get(key);
            Entity e = u == null ? null : Bukkit.getEntity(u);
            if (e != null && e.isValid()) {
                if (e.getLocation().distanceSquared(l) > 4) e.teleport(l);
                continue;
            }
            for (Entity old : w.getNearbyEntities(l, 3, 4, 3))
                if ("liftier".equals(npcId(old))) old.remove();
            Villager v = w.spawn(l, Villager.class, vv -> {
                vv.setPersistent(false);
                vv.setAI(false);
                vv.setInvulnerable(true);
                vv.setSilent(true);
                vv.setCollidable(false);
                vv.setRemoveWhenFarAway(false);
                vv.customName(Txt.c("§6Liftier §7(monte-charge)"));
                vv.setCustomNameVisible(true);
                vv.setProfession(profession("mason"));
                vv.setVillagerLevel(5);
                vv.getPersistentDataContainer().set(key(), PersistentDataType.STRING, "liftier");
            });
            spawned.put(key, v.getUniqueId());
        }
    }

    /** Retire le liftier d'une Concession (Compagnie supprimée). */
    void removePlot(String companyId) {
        UUID u = spawned.remove("plot:" + companyId);
        Entity e = u == null ? null : Bukkit.getEntity(u);
        if (e != null) e.remove();
    }

    private NamespacedKey key() {
        return key;
    }

    void removeAll() {
        for (UUID u : spawned.values()) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        spawned.clear();
    }

    /** Les constantes de profession passent par le registre : jamais dans un initialiseur statique. */
    private static Villager.Profession profession(String job) {
        return switch (job) {
            case "toolsmith" -> Villager.Profession.TOOLSMITH;
            case "librarian" -> Villager.Profession.LIBRARIAN;
            case "armorer" -> Villager.Profession.ARMORER;
            case "butcher" -> Villager.Profession.BUTCHER;
            case "cartographer" -> Villager.Profession.CARTOGRAPHER;
            case "cleric" -> Villager.Profession.CLERIC;
            default -> Villager.Profession.MASON;
        };
    }

    /** Tourné vers le centre de la place (ou vers le gouffre pour Léon). */
    private static Location face(Location l) {
        Location c = l.clone();
        double dx = -l.getX(), dz = -l.getZ();
        c.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        return c;
    }

    void click(Player p, String id) {
        p.playSound(p, Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 1);
        if (id.equals("liftier")) {
            lift(p);
            return;
        }
        boolean story = plugin.quests.talk(p, id);
        switch (id) {
            case "odette" -> plugin.bourse.open(p);
            case "margaux" -> machinesShop(p);
            case "bastien" -> shop(p);
            case "prosper" -> brocante(p);
            case "lebrun" -> notary(p);
            case "leon" -> lift(p);
            case "ines" -> carnets(p);
            default -> {
                if (!story) plugin.quests.remind(p);
            }
        }
    }

    // ------------------------------------------------------------------ boutiques

    private boolean pay(Player p, double price) {
        Company c = plugin.store.company(p);
        if (c.money < price) {
            p.sendMessage(Txt.PREFIX + "§cIl te faut " + Txt.money(price) + " §c(la Compagnie a " + Txt.money(c.money) + "§c).");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1, 1);
            return false;
        }
        c.money -= price;
        return true;
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItem(p.getLocation(), rest);
    }

    void shop(Player p) {
        Menu m = new Menu(4, "§cBastien §7- Marchand");
        offers(m, SHOP, 0);
        m.nav(null, null);
        m.open(p);
    }

    void brocante(Player p) {
        Menu m = new Menu(3, "§eProsper §7- Brocanteur");
        m.set(4, Txt.icon(Material.BOOK, "§eLa brocante de Prosper", "§7Des objets rares, remontés du fond.", "§7Ils s'usent : le nombre d'usages", "§7est écrit dessus. Gardés à la mort."));
        offers(m, BROCANTE, 10);
        m.set(26, Txt.icon(Material.GOLD_NUGGET, "§6Éclats de la Compagnie", Txt.money(plugin.store.company(p).money)));
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.open(p);
    }

    /** Une case par offre ; prix 0 = gratuit, mais un seul exemplaire à la fois. */
    private void offers(Menu m, List<Offer> list, int slot) {
        for (Offer o : list) {
            ItemStack icon = o.item() != null ? Items.make(o.item(), o.amount()) : new ItemStack(o.vanilla(), o.amount());
            var meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            lore.add(Txt.c(""));
            lore.add(Txt.c(o.price() <= 0 ? "§aGratuit §7(un à la fois)" : "§7Prix : " + Txt.money(o.price())));
            lore.add(Txt.c(o.price() <= 0 ? "§eClic pour le prendre" : "§eClic pour acheter"));
            meta.lore(lore);
            icon.setItemMeta(meta);
            m.set(slot++, icon, (pl, t) -> {
                if (o.price() <= 0 && o.item() != null && Bourse.countIn(pl, o.item()) > 0) {
                    pl.sendMessage(Txt.PREFIX + "§7Tu en as déjà un dans ton sac.");
                    pl.playSound(pl, Sound.ENTITY_VILLAGER_NO, 1, 1);
                    return;
                }
                if (o.price() > 0 && !pay(pl, o.price())) return;
                give(pl, o.item() != null ? Items.make(o.item(), o.amount()) : new ItemStack(o.vanilla(), o.amount()));
                pl.playSound(pl, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            });
        }
    }

    void machinesShop(Player p) {
        // 4 lignes de machines (les strates profondes en ajoutent), la dernière pour le guide, les éclats et fermer
        Menu m = new Menu(5, "§6Margaux §7- Ingénieure");
        m.refresh = mm -> {
            mm.clear();
            int slot = 0;
            for (Machines.Type t : Machines.TYPES.values()) {
                Items.Def d = Items.def(t.item());
                List<String> l = new ArrayList<>(Txt.wrap(d.desc(), "§7"));
                l.add("");
                Integer need = Machines.UNLOCK.get(t.id());
                int lvl = plugin.quests.level(plugin.store.player(p), "ingenieur");
                boolean locked = need != null && lvl < need;
                if (need != null) l.add(locked ? "§cIngénieur niveau " + need + " requis §7(tu es " + lvl + ")" : "§aDébloqué §7(Ingénieur " + need + ")");
                Integer strate = Machines.STRATA_UNLOCK.get(t.id());
                boolean deep = strate != null && plugin.store.player(p).unlocked < strate;
                if (strate != null) l.add(deep ? "§cEn vente quand la strate " + strate + " est ouverte §7(vaincs le gardien de la strate " + (strate - 1) + ")" : "§aDébloqué §7(strate " + strate + " ouverte)");
                l.add("§7Prix : " + Txt.money(t.price()));
                if (t.id().equals("foreuse")) l.add("§8(se fabrique aussi à l'Assembleur)");
                l.add("§eClic pour acheter");
                mm.set(slot++, Items.skin(Txt.icon(locked || deep ? Material.IRON_BARS : d.mat(), d.name(), l), locked || deep ? "" : t.item()), (pl, ty) -> {
                    if (deep) {
                        pl.sendMessage(Txt.PREFIX + "§cMargaux ne te vend ça qu'une fois la strate " + strate + " ouverte : vaincs d'abord le gardien de la strate " + (strate - 1) + ".");
                        pl.playSound(pl, Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return;
                    }
                    if (locked) {
                        pl.sendMessage(Txt.PREFIX + "§cIl te faut le niveau " + need + " d'Ingénieur (fais tourner ton usine).");
                        pl.playSound(pl, Sound.ENTITY_VILLAGER_NO, 1, 1);
                        return;
                    }
                    if (!pay(pl, t.price())) return;
                    give(pl, Items.make(t.item(), 1));
                    pl.sendMessage(Txt.PREFIX + "§7Tu as acheté : " + d.name() + "§7. Pose-la dans ta Concession.");
                    pl.playSound(pl, Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
                    plugin.quests.onBuyMachine(pl, t.id());
                });
            }
            int belts = plugin.getConfig().getInt("convoyeurs.prix-16", 80);
            mm.set(slot, Txt.icon(Material.MAGENTA_GLAZED_TERRACOTTA, "§dConvoyeurs §7x16", List.of("§7Relient les machines : la sortie", "§7d'une machine part seule vers la", "§7suivante (8 objets/s).", "",
                    "§7Prix : " + Txt.money(belts), "§eClic pour acheter")), (pl, ty) -> {
                if (!pay(pl, belts)) return;
                give(pl, Items.make("convoyeur", 16));
                pl.sendMessage(Txt.PREFIX + "§7Pose-les en regardant dans le sens du transport. §e/abysse guide §7pour l'explication.");
                pl.playSound(pl, Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
            });
            mm.set(36, Txt.icon(Material.KNOWLEDGE_BOOK, "§eGuide de l'usine", "§7A quoi sert chaque machine,", "§7comment les relier.", "", "§eClic pour l'ouvrir"), (pl, ty) -> plugin.guide.open(pl));
            mm.set(44, Txt.icon(Material.GOLD_NUGGET, "§6Éclats de la Compagnie", Txt.money(plugin.store.company(p).money)));
            mm.nav(null, null);

        };
        m.open(p);
    }

    void notary(Player p) {
        Company c = plugin.store.company(p);
        Menu m = new Menu(3, "§2Maître Lebrun §7- Notaire");
        if (c.plot < 0) {
            m.set(13, Txt.icon(Material.GRASS_BLOCK, "§aObtenir ta Concession", "§7Une parcelle de 33 × 33 rien qu'à", "§7ta Compagnie, pour y monter ton usine.", "", "§aGratuit"), (pl, t) -> {
                plugin.worlds.claim(c);
                pl.closeInventory();
                pl.sendMessage(Txt.PREFIX + "§aLa Concession est à toi ! §7(§f/abysse base§7)");
                pl.teleport(plugin.worlds.plotSpawn(c));
                plugin.quests.onPlot(pl);
            });
        } else {
            int r = plugin.worlds.radius(c);
            m.set(11, Txt.icon(Material.ENDER_PEARL, "§aAller à ta Concession", "§7Ou §f/abysse base"), (pl, t) -> {
                pl.closeInventory();
                pl.teleport(plugin.worlds.plotSpawn(c));
            });
            if (c.plotTier + 1 < Worlds.PLOT_RADIUS.length) {
                int next = Worlds.PLOT_RADIUS[c.plotTier + 1], price = Worlds.PLOT_PRICE[c.plotTier + 1];
                m.set(13, Txt.icon(Material.OAK_FENCE, "§eAgrandir la Concession", "§7Actuelle : §f" + (2 * r + 1) + " × " + (2 * r + 1),
                        "§7Suivante : §f" + (2 * next + 1) + " × " + (2 * next + 1), "", "§7Prix : " + Txt.money(price)), (pl, t) -> {
                    if (!pay(pl, price)) return;
                    int old = plugin.worlds.radius(c);
                    c.plotTier++;
                    plugin.worlds.buildPlot(c, old);
                    pl.closeInventory();
                    pl.sendMessage(Txt.PREFIX + "§aConcession agrandie !");
                });
            } else m.set(13, Txt.icon(Material.OAK_FENCE, "§7Concession à sa taille maximale"));
            m.set(15, Txt.icon(Material.PAPER, "§f" + c.name, "§7Membres : §f" + members(c), "§7Machines : §f" + Machines.summary(c),
                    "§7Éclats : " + Txt.money(c.money), "", "§7Gérer : §f/abysse compagnie"));
        }
        m.nav(null, null);
        m.open(p);
    }

    String members(Company c) {
        List<String> n = new ArrayList<>();
        for (UUID u : c.members) n.add(plugin.store.player(u).name);
        return String.join(", ", n);
    }

    void lift(Player p) {
        PlayerData d = plugin.store.player(p);
        Menu m = new Menu(3, "§7Léon §8- Monte-charge");
        int slot = 10;
        for (Gen.Strata s : Gen.STRATAS) {
            boolean open = d.unlocked >= s.n;
            m.set(slot++, Txt.icon(open ? (s.n == 1 ? Material.STONE : s.n == 2 ? Material.MYCELIUM : s.n == 3 ? Material.BLACKSTONE
                            : s.n == 4 ? Material.CALCITE : Material.END_STONE) : Material.BEDROCK,
                    (open ? "§a" : "§c") + "Strate " + s.n + " §7- " + s.name,
                    open ? "§7Clic pour descendre" : "§7Bats le gardien de la strate " + (s.n - 1) + " pour l'ouvrir."), (pl, t) -> {
                if (!open) return;
                pl.closeInventory();
                plugin.goStrata(pl, s.n);
            });
        }
        for (int n = Gen.STRATAS.length + 1; n <= 7; n++)
            m.set(slot++, Txt.icon(Material.BLACK_CONCRETE, "§8Strate " + n + " §7- ???", "§7Les ingénieurs creusent encore..."));
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.set(24, Txt.icon(Material.GRASS_BLOCK, "§aMa Concession"), (pl, t) -> {
            pl.closeInventory();
            pl.performCommand("abysse base");
        });
        m.open(p);
    }

    void carnets(Player p) {
        PlayerData d = plugin.store.player(p);
        // une ligne par strate, la dernière ligne pour fermer
        Menu m = new Menu(Math.min(6, Gen.STRATAS.length + 1), "§3Inès §7- Carnets de Varenne");
        int slot = 0;
        for (Gen.Strata s : Gen.STRATAS) {
            int found = 0;
            for (int i = 1; i <= Quests.PAGES; i++) {
                boolean has = d.pages.contains(s.n + ":" + i);
                if (has) found++;
                List<String> l = has ? Txt.wrap(Quests.page(s.n, i), "§7") : List.of("§8Pas encore trouvée.", "§8Fouille les campements abandonnés.");
                m.set(slot + i - 1, Txt.icon(has ? Material.FILLED_MAP : Material.MAP, (has ? "§f" : "§8") + s.name + " - page " + i, l));
            }
            boolean done = found == Quests.PAGES, claimed = d.pageRewards.contains(s.n);
            double reward = EemiAbysse.gain(500 * s.n);
            m.set(slot + 6, Txt.icon(claimed ? Material.LIME_DYE : done ? Material.GOLD_INGOT : Material.GRAY_DYE,
                    claimed ? "§aRécompense reçue" : done ? "§6Réclamer " + Txt.money(reward) : "§7" + found + " / " + Quests.PAGES + " pages"), (pl, t) -> {
                if (!done || claimed) return;
                d.pageRewards.add(s.n);
                plugin.store.company(pl).money += reward;
                pl.sendMessage(Txt.PREFIX + "§aInès te remercie : " + Txt.money(reward));
                pl.playSound(pl, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                carnets(pl);
            });
            slot += 9;
        }
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.open(p);
    }
}
