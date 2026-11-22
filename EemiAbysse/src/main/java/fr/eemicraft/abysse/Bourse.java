package fr.eemicraft.abysse;

import fr.eemicraft.abysse.Store.Company;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Bourse de l'Abysse. Cours = base × (plancher + (1 − plancher) × e^(−pression / élasticité)) : la baisse
 * ralentit en approchant du plancher (62 % par défaut) sans jamais l'atteindre. Seules les ventes ajoutent de
 * la pression, divisée selon le nombre de joueurs actifs ({@code bourse.normalisation}) et réduite au-delà de
 * {@code bourse.pression-heure} × élasticité par heure ; elle revient vers 0 (demi-vie 2,5 h). Acheter coûte
 * 125 % du meilleur prix de vente possible et ne bouge pas le cours (pas d'aller-retour gagnant). Deux
 * commandes du jour, communes à tout le serveur, achètent un produit au-dessus du cours.
 */
final class Bourse {

    static final class Contract {
        String item;
        int qty, filled;
        double bonus;
        long expires;
    }

    private final EemiAbysse plugin;
    private final Map<String, Double> pressure = new HashMap<>();
    /** Pression ajoutée récemment par produit (demi-vie 1 h, en mémoire seulement) : plafond horaire. */
    private final Map<String, double[]> recent = new HashMap<>();
    /** Prix relevés toutes les 10 min (6 h). */
    private final Map<String, Deque<Double>> history = new HashMap<>();
    final List<Contract> contracts = new ArrayList<>();
    private long lastDecay = System.currentTimeMillis();
    private final Random random = new Random();

    Bourse(EemiAbysse plugin) {
        this.plugin = plugin;
    }

    private double floor() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("bourse.plancher-doux", 0.62)));
    }

    /** Pression maximale (multiple de l'élasticité) : au-delà le cours ne bouge plus et la remontée serait trop lente. */
    private double maxPressure(Items.Def d) {
        return d.elasticity() * plugin.getConfig().getDouble("bourse.pression-max", 3.0);
    }

    /** Joueurs actifs dans l'Abysse (hors spectateurs et vanish), au moins 1. */
    private int activePlayers() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers())
            if (Worlds.isAbysse(p.getWorld()) && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !p.hasMetadata("vanished")) n++;
        return Math.max(1, n);
    }

    /** Pression ajoutée par une unité vendue : 1 / (1 + normalisation × (joueurs actifs − 1)). */
    private double unitStep() {
        return 1 / (1 + Math.max(0, plugin.getConfig().getDouble("bourse.normalisation", 0.5)) * (activePlayers() - 1));
    }

    /** Pression récente (demi-vie 1 h) du produit, ramenée à maintenant : {valeur, date}. */
    private double[] recentOf(String id) {
        long now = System.currentTimeMillis();
        double[] r = recent.computeIfAbsent(id, k -> new double[]{0, now});
        r[0] *= Math.exp(-Math.log(2) * (now - r[1]) / 3_600_000.0);
        r[1] = now;
        return r;
    }

    private double ceil() {
        return plugin.getConfig().getDouble("bourse.plafond", 3.0);
    }

    double buyFactor() {
        return plugin.getConfig().getDouble("bourse.achat", 1.25);
    }

    double price(String id) {
        Items.Def d = Items.def(id);
        if (d == null || !d.sellable()) return 0;
        return priceAt(d, pressure.getOrDefault(id, 0.0));
    }

    private double priceAt(Items.Def d, double p) {
        return d.price() * ratio(d, p) * EemiAbysse.gain(1);
    }

    /** Cours / prix de base, sans le réglage gains : approche douce du plancher. */
    private double ratio(Items.Def d, double p) {
        double f = Math.exp(-p / d.elasticity());
        if (p <= 0) return Math.min(ceil(), f);
        double fl = floor();
        return fl + (1 - fl) * f;
    }

    /** Cours / prix de base du produit en ce moment (1 = au prix de base). */
    double ratio(String id) {
        Items.Def d = Items.def(id);
        return d == null || !d.sellable() ? 1 : ratio(d, pressure.getOrDefault(id, 0.0));
    }

    /** Indice du marché : moyenne des cours / prix de base de tous les produits (1 = tout au prix de base). */
    double marketIndex() {
        double sum = 0;
        int n = 0;
        for (Items.Def d : Items.sellables()) {
            sum += ratio(d, pressure.getOrDefault(d.id(), 0.0));
            n++;
        }
        return n == 0 ? 1 : sum / n;
    }

    /** Prix de base réellement payé quand le cours est au plus haut (base × gains). */
    static double baseEff(Items.Def d) {
        return d.price() * EemiAbysse.gain(1);
    }

    /** Écart au prix de base, ex. « -12 % » pour un ratio de 0,88. */
    static String pct(double ratio) {
        long v = Math.round((ratio - 1) * 100);
        return (v > 0 ? "+" : "") + v + " %";
    }

    /** Fait baisser la pression (appelé chaque minute). */
    void decay() {
        long now = System.currentTimeMillis();
        double hours = Math.max(0.1, plugin.getConfig().getDouble("bourse.remontee-heures", 2.5));
        double k = Math.exp(-Math.log(2) * (now - lastDecay) / (hours * 3_600_000.0));
        lastDecay = now;
        pressure.replaceAll((id, p) -> p * k);
        pressure.values().removeIf(p -> Math.abs(p) < 0.01);
    }

    void sample() {
        for (Items.Def d : Items.sellables()) {
            Deque<Double> h = history.computeIfAbsent(d.id(), k -> new ArrayDeque<>());
            h.addLast(price(d.id()));
            while (h.size() > 36) h.removeFirst();
        }
    }

    /** Variation en % depuis 6 h (ou le plus vieux relevé). */
    double trend(String id) {
        Deque<Double> h = history.get(id);
        if (h == null || h.isEmpty()) return 0;
        double old = h.peekFirst();
        return old <= 0 ? 0 : (price(id) - old) / old * 100;
    }

    String sparkline(String id) {
        Deque<Double> h = history.get(id);
        if (h == null || h.size() < 2) return "§8(pas encore d'historique)";
        List<Double> pts = new ArrayList<>(h);
        int step = Math.max(1, pts.size() / 12);
        List<Double> s = new ArrayList<>();
        for (int i = 0; i < pts.size(); i += step) s.add(pts.get(i));
        s.add(price(id));
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : s) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        String chars = "▁▂▃▄▅▆▇█";
        StringBuilder b = new StringBuilder("§b");
        for (double v : s) b.append(chars.charAt(max - min < 1e-9 ? 3 : (int) Math.round((v - min) / (max - min) * 7)));
        return b.toString();
    }

    static String arrow(double t) {
        if (t > 0.5) return "§a▲ +" + Txt.num(t) + " %";
        if (t < -0.5) return "§c▼ " + Txt.num(t) + " %";
        return "§7= stable";
    }

    // ------------------------------------------------------------------ ventes et achats

    /**
     * Vend n unités : d'abord à une commande du jour ouverte sur ce produit, puis au cours (chaque unité
     * fait baisser le prix de la suivante). Crédite la Compagnie, renvoie le gain.
     */
    double sell(Company c, String id, int n) {
        Items.Def d = Items.def(id);
        if (d == null || !d.sellable() || n <= 0) return 0;
        double gain = 0;
        for (Contract k : contracts) {
            if (!k.item.equals(id) || k.filled >= k.qty || n <= 0) continue;
            int take = Math.min(n, k.qty - k.filled);
            gain += take * d.price() * k.bonus * EemiAbysse.gain(1);
            k.filled += take;
            n -= take;
            if (k.filled >= k.qty)
                plugin.broadcastAbysse(Txt.PREFIX + "§aLa commande de §f" + strip(d.name()) + " §aest remplie, merci à tous !");
        }
        double p = pressure.getOrDefault(id, 0.0);
        double step = unitStep(), cap = d.elasticity() * plugin.getConfig().getDouble("bourse.pression-heure", 0.5);
        double beyond = plugin.getConfig().getDouble("bourse.au-dela", 0.25), max = maxPressure(d);
        double[] r = recentOf(id);
        for (int i = 0; i < n; i++) {
            gain += priceAt(d, p);
            double add = step * (r[0] < cap ? 1 : beyond);
            r[0] += add;
            p = Math.min(max, p + add);
        }
        pressure.put(id, p);
        c.money += gain;
        return gain;
    }

    /**
     * Prix d'achat d'une unité : {@code achat} × le plus haut entre le cours sans gains, la base × gains (cours
     * de vente le plus haut possible, la pression n'étant jamais négative) et une commande du jour ouverte.
     * Acheter ne bouge plus le cours : revendre un achat, tout de suite ou plus tard, rapporte toujours moins.
     */
    double unitCost(String id) {
        Items.Def d = Items.def(id);
        if (d == null) return 0;
        double best = Math.max(d.price() * ratio(d, pressure.getOrDefault(id, 0.0)), baseEff(d));
        for (Contract k : contracts)
            if (k.item.equals(id) && k.filled < k.qty) best = Math.max(best, baseEff(d) * k.bonus);
        return best * buyFactor();
    }

    /** Coût d'achat de n unités (sans acheter). */
    double cost(String id, int n) {
        return unitCost(id) * Math.max(0, n);
    }

    boolean buy(Company c, String id, int n) {
        double cost = cost(id, n);
        if (c.money < cost) return false;
        c.money -= cost;
        return true;
    }

    static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    // ------------------------------------------------------------------ commandes du jour

    void refreshContracts() {
        long now = System.currentTimeMillis();
        contracts.removeIf(k -> k.expires < now);
        List<Items.Def> pool = new ArrayList<>();
        for (Items.Def d : Items.sellables())
            if (d.price() >= 5 && d.price() <= 80) pool.add(d);
        while (contracts.size() < plugin.getConfig().getInt("bourse.commandes", 2) && !pool.isEmpty()) {
            Items.Def d = pool.remove(random.nextInt(pool.size()));
            boolean dup = false;
            for (Contract k : contracts) dup |= k.item.equals(d.id());
            if (dup) continue;
            Contract k = new Contract();
            k.item = d.id();
            k.bonus = 1.4 + random.nextInt(4) * 0.1;
            k.qty = (int) Math.max(50, Math.round(12000 / d.price() / 50) * 50);
            k.expires = now + 24 * 3_600_000L;
            contracts.add(k);
            plugin.broadcastAbysse(Txt.PREFIX + "§eNouvelle commande : §f" + k.qty + " " + strip(d.name()) + " §epayés §a+"
                    + Math.round((k.bonus - 1) * 100) + " % §eà la bourse pendant 24 h.");
        }
    }

    // ------------------------------------------------------------------ menu

    /** Page de la bourse ouverte par chaque joueur (45 produits par page, les strates 4-5 en ajoutent). */
    private final Map<java.util.UUID, Integer> pages = new HashMap<>();
    /** Joueurs qui ont choisi « Tout » (sinon « Mon sac » : seulement ce qu'ils ont sur eux) ; gardé jusqu'au redémarrage. */
    private final java.util.Set<java.util.UUID> showAll = new java.util.HashSet<>();

    void open(Player p) {
        pages.remove(p.getUniqueId());
        Menu m = new Menu(6, "§5Bourse de Bordgouffre");
        m.tag = "bourse";
        m.refresh = mm -> render(p, mm);
        m.open(p);
    }

    /** Cours coloré : vert au prix de base (ou au-dessus), rouge en dessous. */
    private String priceColored(Items.Def d) {
        double pr = price(d.id()), base = baseEff(d);
        return (pr >= base * 0.995 ? "§a" : "§c") + Txt.num(Math.round(pr * 100) / 100.0) + " " + Txt.COIN;
    }

    private void render(Player p, Menu m) {
        m.clear();
        Company c = plugin.store.company(p);
        boolean all = showAll.contains(p.getUniqueId());
        List<Items.Def> list = new ArrayList<>();
        for (Items.Def d : Items.sellables()) if (all || countIn(p, d.id()) > 0) list.add(d);
        int pageCount = Math.max(1, (list.size() + 44) / 45), page = Math.min(pages.getOrDefault(p.getUniqueId(), 0), pageCount - 1);
        int slot = 0;
        for (int i = page * 45; i < list.size() && slot < 45; i++) {
            Items.Def d = list.get(i);
            String id = d.id();
            int have = countIn(p, id);
            List<String> lore = new ArrayList<>();
            lore.add("§7Cours : " + priceColored(d) + " §8(base " + Txt.num(Math.round(baseEff(d) * 100) / 100.0) + ", " + pct(ratio(id)) + ")");
            lore.add("§76 h : " + arrow(trend(id)) + " " + sparkline(id));
            for (Contract k : contracts)
                if (k.item.equals(id) && k.filled < k.qty)
                    lore.add("§e★ Commande : " + Txt.money(d.price() * k.bonus * EemiAbysse.gain(1)) + " §7(" + k.filled + "/" + k.qty + ")");
            lore.add("§7Sur toi : §f" + have);
            lore.add("§eGauche §7: vendre 16 §8| §eDroit §7: tout vendre");
            lore.add("§bMaj §7: acheter 1 / 16 §8(" + Txt.num(Math.round(cost(id, 1) * 10) / 10.0) + " / " + Txt.num(Math.round(cost(id, 16))) + " " + Txt.COIN + ")");
            ItemStack icon = Items.make(id, Math.max(1, Math.min(64, have)));
            var meta = icon.getItemMeta();
            meta.lore(Txt.lines(lore));
            icon.setItemMeta(meta);
            m.set(slot++, icon, (pl, type) -> click(pl, id, type));
        }
        if (list.isEmpty())
            m.set(22, Txt.icon(Material.BUNDLE, "§7Rien à vendre dans ton sac", "§7Ressources et produits de l'usine", "§7apparaissent ici quand tu en as.",
                    "", "§eBouton « Tout » en bas : voir tous les cours"));
        // dernière ligne : tout vendre, filtre, commande, Retour, Fermer, commande, éclats, pages
        m.set(45, Txt.icon(Material.HOPPER, "§aTout vendre", "§7Toutes les ressources et tous les", "§7produits de ton sac (avec confirmation)."), (pl, t) -> sellAllConfirm(pl));
        m.set(46, all ? Txt.icon(Material.BOOKSHELF, "§fAffichage : §eTout", "§7Tous les produits de la bourse.", "", "§eClic : seulement mon sac")
                        : Txt.icon(Material.CHEST, "§fAffichage : §eMon sac", "§7Seulement ce que tu as sur toi.", "", "§eClic : tous les produits"),
                (pl, t) -> {
                    if (!showAll.remove(pl.getUniqueId())) showAll.add(pl.getUniqueId());
                    pages.remove(pl.getUniqueId());
                    render(pl, m);
                });
        int[] contractSlots = {47, 50};
        int ci = 0;
        for (Contract k : contracts) {
            Items.Def d = Items.def(k.item);
            if (d == null || ci >= contractSlots.length) continue;
            long left = Math.max(0, (k.expires - System.currentTimeMillis()) / 1000);
            m.set(contractSlots[ci++], Txt.icon(Material.WRITABLE_BOOK, "§eCommande : " + d.name(),
                    "§7Payé " + Txt.money(d.price() * k.bonus * EemiAbysse.gain(1)) + " §7l'unité", "§7Livré : §f" + k.filled + " / " + k.qty,
                    "§7Fin dans " + Txt.time(left)));
        }
        m.nav((pl, t) -> plugin.menu(pl), "Menu de l'Abysse");
        m.set(51, Txt.icon(Material.GOLD_NUGGET, "§6Éclats de la Compagnie", "§f" + Txt.money(c.money)));
        if (page > 0) m.set(52, Txt.icon(Material.ARROW, "§7Page précédente", "§7Page " + page + " / " + pageCount), (pl, t) -> {
            pages.put(pl.getUniqueId(), page - 1);
            render(pl, m);
        });
        if (page < pageCount - 1) m.set(53, Txt.icon(Material.ARROW, "§7Page suivante", "§7Page " + (page + 2) + " / " + pageCount), (pl, t) -> {
            pages.put(pl.getUniqueId(), page + 1);
            render(pl, m);
        });
        m.fill(Txt.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
    }

    /**
     * Ce que rapporterait la vente de n unités maintenant, sans rien vendre (même calcul que {@link #sell} :
     * commandes du jour d'abord, puis le cours qui baisse à chaque unité).
     */
    double quote(String id, int n) {
        Items.Def d = Items.def(id);
        if (d == null || !d.sellable() || n <= 0) return 0;
        double gain = 0;
        for (Contract k : contracts) {
            if (!k.item.equals(id) || k.filled >= k.qty || n <= 0) continue;
            int take = Math.min(n, k.qty - k.filled);
            gain += take * d.price() * k.bonus * EemiAbysse.gain(1);
            n -= take;
        }
        double p = pressure.getOrDefault(id, 0.0);
        double step = unitStep(), cap = d.elasticity() * plugin.getConfig().getDouble("bourse.pression-heure", 0.5);
        double beyond = plugin.getConfig().getDouble("bourse.au-dela", 0.25), max = maxPressure(d);
        double r0 = recentOf(id)[0];
        for (int i = 0; i < n; i++) {
            gain += priceAt(d, p);
            double add = step * (r0 < cap ? 1 : beyond);
            r0 += add;
            p = Math.min(max, p + add);
        }
        return gain;
    }

    private void click(Player p, String id, ClickType type) {
        Company c = plugin.store.company(p);
        if (type.isShiftClick()) {
            int n = type.isRightClick() ? 16 : 1;
            double cost = cost(id, n);
            if (!buy(c, id, n)) {
                p.sendMessage(Txt.PREFIX + "§cIl te faut " + Txt.money(cost) + "§c.");
                p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            for (ItemStack rest : p.getInventory().addItem(Items.make(id, n)).values()) p.getWorld().dropItem(p.getLocation(), rest);
            p.sendMessage(Txt.PREFIX + "§7Acheté §f" + n + " " + strip(Items.def(id).name()) + " §7pour " + Txt.money(cost));
            p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            return;
        }
        int have = countIn(p, id);
        if (have <= 0) {
            p.sendMessage(Txt.PREFIX + "§7Tu n'en as pas sur toi.");
            return;
        }
        if (type.isRightClick()) {
            // tout vendre d'un produit : confirmation avec le total prévu
            Items.Def d = Items.def(id);
            Menu.confirm(p, "§5Vendre tout ?", Items.skin(Txt.icon(d.mat(), "§fVendre §e" + have + " " + strip(d.name()),
                            "§7Total prévu : " + Txt.money(quote(id, have)), "§8Le cours baisse un peu à chaque unité."), id),
                    "§aOui, vendre", List.of("§7" + have + " " + strip(d.name())), (pl, t) -> {
                        sellNow(pl, id, countIn(pl, id));
                        open(pl);
                    }, (pl, t) -> open(pl));
            return;
        }
        sellNow(p, id, Math.min(16, have));
    }

    private void sellNow(Player p, String id, int n) {
        if (n <= 0) {
            p.sendMessage(Txt.PREFIX + "§7Tu n'en as pas sur toi.");
            return;
        }
        take(p, id, n);
        double gain = sell(plugin.store.company(p), id, n);
        p.sendMessage(Txt.PREFIX + "§7Vendu §f" + n + " " + strip(Items.def(id).name()) + " §7pour " + Txt.money(gain));
        p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        plugin.quests.onSell(p, gain);
    }

    /** « Tout vendre » : écran de confirmation avec le nombre d'objets et le total prévu. */
    void sellAllConfirm(Player p) {
        int count = 0;
        double total = 0;
        for (Items.Def d : Items.sellables()) {
            int n = countIn(p, d.id());
            if (n <= 0) continue;
            count += n;
            total += quote(d.id(), n);
        }
        if (count == 0) {
            p.sendMessage(Txt.PREFIX + "§7Rien à vendre.");
            return;
        }
        Menu.confirm(p, "§5Tout vendre ?", Txt.icon(Material.HOPPER, "§fVendre §e" + count + " §fobjets", "§7Toutes les ressources et produits", "§7de ton sac.",
                        "§7Total prévu : " + Txt.money(total)),
                "§aOui, tout vendre", List.of("§7Total prévu : " + Txt.money(total)), (pl, t) -> {
                    sellAll(pl);
                    open(pl);
                }, (pl, t) -> open(pl));
    }

    void sellAll(Player p) {
        Company c = plugin.store.company(p);
        double total = 0;
        int count = 0;
        for (Items.Def d : Items.sellables()) {
            int n = countIn(p, d.id());
            if (n <= 0) continue;
            take(p, d.id(), n);
            total += sell(c, d.id(), n);
            count += n;
        }
        if (count == 0) {
            p.sendMessage(Txt.PREFIX + "§7Rien à vendre.");
            return;
        }
        p.sendMessage(Txt.PREFIX + "§7Vendu §f" + count + " §7objets pour " + Txt.money(total));
        p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        plugin.quests.onSell(p, total);
    }

    static int countIn(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (Items.is(it, id)) n += it.getAmount();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (Items.is(off, id)) n += off.getAmount();
        return n;
    }

    static void take(Player p, String id, int n) {
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && n > 0; i++) {
            ItemStack it = cont[i];
            if (!Items.is(it, id)) continue;
            int t = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - t);
            n -= t;
            cont[i] = it.getAmount() <= 0 ? null : it;
        }
        p.getInventory().setStorageContents(cont);
        if (n > 0) {
            ItemStack off = p.getInventory().getItemInOffHand();
            if (Items.is(off, id)) off.setAmount(Math.max(0, off.getAmount() - n));
        }
    }

    /** Texte du tableau de la bourse (hologramme de Bordgouffre). */
    String board() {
        StringBuilder b = new StringBuilder("§5§lBOURSE DE BORDGOUFFRE\n§7Cours en éclats ⬥ et tendance sur 6 h\n§7Indice du marché : §f" + pct(marketIndex()) + "\n");
        int n = 0;
        for (Items.Def d : Items.sellables()) {
            if (d.price() > 100) continue;
            b.append("\n").append(d.name()).append(" §8» ").append(Txt.money(price(d.id()))).append(" ").append(arrow(trend(d.id())));
            if (++n >= 14) break;
        }
        for (Contract k : contracts) {
            Items.Def d = Items.def(k.item);
            if (d != null && k.filled < k.qty)
                b.append("\n\n§e★ Commande : ").append(strip(d.name())).append(" ").append(k.filled).append("/").append(k.qty);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ fichiers

    void load() {
        File f = new File(plugin.getDataFolder(), "bourse.yml");
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        lastDecay = y.getLong("last-decay", System.currentTimeMillis());
        ConfigurationSection p = y.getConfigurationSection("pressure");
        if (p != null) for (String k : p.getKeys(false)) pressure.put(k, p.getDouble(k));
        ConfigurationSection h = y.getConfigurationSection("history");
        if (h != null) for (String k : h.getKeys(false)) history.put(k, new ArrayDeque<>(h.getDoubleList(k)));
        clampLoaded();
        ConfigurationSection c = y.getConfigurationSection("contracts");
        if (c != null) for (String k : c.getKeys(false)) {
            Contract ct = new Contract();
            ct.item = c.getString(k + ".item");
            ct.qty = c.getInt(k + ".qty");
            ct.filled = c.getInt(k + ".filled");
            ct.bonus = c.getDouble(k + ".bonus");
            ct.expires = c.getLong(k + ".expires");
            if (Items.def(ct.item) != null) contracts.add(ct);
        }
    }

    /**
     * Ramène les données d'une ancienne version dans les bornes actuelles : pression entre 0 et
     * {@code pression-max} × élasticité (les anciens achats pouvaient la rendre négative), relevés de
     * l'historique entre plancher et base × gains (sinon la tendance afficherait une fausse hausse).
     */
    private void clampLoaded() {
        int fixed = 0;
        for (Map.Entry<String, Double> e : pressure.entrySet()) {
            Items.Def d = Items.def(e.getKey());
            if (d == null || !d.sellable()) continue;
            double v = Math.max(0, Math.min(maxPressure(d), e.getValue()));
            if (v != e.getValue()) {
                e.setValue(v);
                fixed++;
            }
        }
        for (Map.Entry<String, Deque<Double>> e : history.entrySet()) {
            Items.Def d = Items.def(e.getKey());
            if (d == null || !d.sellable()) continue;
            double lo = baseEff(d) * floor(), hi = baseEff(d);
            Deque<Double> q = new ArrayDeque<>();
            for (double v : e.getValue()) q.addLast(Math.max(lo, Math.min(hi, v)));
            e.setValue(q);
        }
        if (fixed > 0) plugin.getLogger().info("Bourse : " + fixed + " pression(s) ramenee(s) dans les nouvelles bornes.");
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("last-decay", lastDecay);
        for (Map.Entry<String, Double> e : pressure.entrySet()) y.set("pressure." + e.getKey(), e.getValue());
        for (Map.Entry<String, Deque<Double>> e : history.entrySet()) y.set("history." + e.getKey(), new ArrayList<>(e.getValue()));
        int i = 0;
        for (Contract k : contracts) {
            String b = "contracts.c" + (i++) + ".";
            y.set(b + "item", k.item);
            y.set(b + "qty", k.qty);
            y.set(b + "filled", k.filled);
            y.set(b + "bonus", k.bonus);
            y.set(b + "expires", k.expires);
        }
        try {
            y.save(new File(plugin.getDataFolder(), "bourse.yml"));
        } catch (IOException ex) {
            plugin.getLogger().warning("Sauvegarde de la bourse impossible : " + ex.getMessage());
        }
    }

    void reset() {
        pressure.clear();
        recent.clear();
        history.clear();
        contracts.clear();
    }

    /** Cours publics pour le site et le bot Discord ({@code bourse.json}, écrit en asynchrone). */
    void writeJson() {
        double idx = marketIndex();
        StringBuilder b = new StringBuilder("{\"version\":1,\"updated_at\":").append(System.currentTimeMillis())
                .append(",\"gains\":").append(EemiAbysse.gain(1)).append(",\"floor\":").append(floor())
                .append(",\"market_index\":").append(Math.round(idx * 1000) / 1000.0)
                .append(",\"market_change_pct\":").append(Math.round((idx - 1) * 1000) / 10.0).append(",\"items\":[");
        boolean first = true;
        for (Items.Def d : Items.sellables()) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"id\":\"").append(d.id()).append("\",\"name\":\"").append(strip(d.name()).replace("\"", "\\\""))
                    .append("\",\"base\":").append(Math.round(baseEff(d) * 100) / 100.0).append(",\"price\":").append(Math.round(price(d.id()) * 100) / 100.0)
                    .append(",\"trend_6h\":").append(Math.round(trend(d.id()) * 10) / 10.0)
                    .append(",\"base_raw\":").append(d.price()).append(",\"ratio\":").append(Math.round(ratio(d.id()) * 1000) / 1000.0)
                    .append(",\"change_pct\":").append(Math.round((ratio(d.id()) - 1) * 1000) / 10.0)
                    .append(",\"buy_price\":").append(Math.round(unitCost(d.id()) * 100) / 100.0).append('}');
        }
        b.append("],\"contracts\":[");
        first = true;
        for (Contract k : contracts) {
            if (!first) b.append(',');
            first = false;
            b.append("{\"item\":\"").append(k.item).append("\",\"qty\":").append(k.qty).append(",\"filled\":").append(k.filled)
                    .append(",\"bonus\":").append(k.bonus).append(",\"expires\":").append(k.expires).append('}');
        }
        b.append("]}");
        String json = b.toString();
        File dir = plugin.getDataFolder();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File tmp = new File(dir, "bourse.json.tmp");
                Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), new File(dir, "bourse.json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
            }
        });
    }
}
