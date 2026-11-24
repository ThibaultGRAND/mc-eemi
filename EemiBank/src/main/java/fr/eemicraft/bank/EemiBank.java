package fr.eemicraft.bank;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Banque du skyblock : argent a l'abri + interets, et perte d'une partie du portefeuille a la mort dans le vide. */
public final class EemiBank extends JavaPlugin implements Listener {

    static final String PREFIX = "§6§lBanque §8» §7";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Economy economy;
    private File dataFile;
    private YamlConfiguration data;
    private boolean dirty;
    /** Joueurs qui doivent taper un montant dans le chat : true = depot, false = retrait. */
    private final Map<UUID, Boolean> awaitingAmount = new HashMap<>();
    private final DecimalFormat money;

    public EemiBank() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        money = new DecimalFormat("#,##0.##", symbols);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Aucune economie Vault trouvee : plugin desactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BankExpansion(this).register();
            getLogger().info("Placeholders enregistres : %eemibank_bank%, %eemibank_bank_raw%, %eemibank_wallet%, %eemibank_total%.");
        }
        // Interets et sauvegarde : toutes les minutes.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) payInterest(p);
            if (dirty) save();
        }, 1200L, 1200L);
        getLogger().info("Economie : " + economy.getName() + ". " + data.getKeys(false).size() + " comptes en banque.");
    }

    @Override
    public void onDisable() {
        if (data != null) save();
    }

    // ------------------------------------------------------------------ comptes

    double bank(UUID id) {
        return data.getDouble(id + ".bank", 0);
    }

    double wallet(OfflinePlayer p) {
        return economy.getBalance(p);
    }

    String format(double amount) {
        return money.format(Math.floor(amount * 100) / 100) + " " + getConfig().getString("currency", "coins");
    }

    private void setBank(OfflinePlayer p, double amount) {
        data.set(p.getUniqueId() + ".bank", Math.max(0, Math.floor(amount * 100) / 100));
        if (p.getName() != null) data.set(p.getUniqueId() + ".name", p.getName());
        if (!data.contains(p.getUniqueId() + ".last-interest")) {
            data.set(p.getUniqueId() + ".last-interest", System.currentTimeMillis());
        }
        dirty = true;
    }

    private void save() {
        try {
            data.save(dataFile);
            dirty = false;
        } catch (IOException e) {
            getLogger().warning("Sauvegarde de data.yml impossible : " + e.getMessage());
        }
    }

    /** Montant demande : nombre, "tout"/"all" ou "moitie"/"half", par rapport au maximum disponible. */
    private double parseAmount(String text, double max) {
        String s = text.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace(" ", "").replace(",", ".");
        if (s.equals("tout") || s.equals("all") || s.equals("max")) return max;
        if (s.equals("moitie") || s.equals("half") || s.equals("1/2")) return max / 2;
        try {
            if (s.endsWith("k")) return Double.parseDouble(s.substring(0, s.length() - 1)) * 1000;
            if (s.endsWith("m")) return Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000;
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    void deposit(Player p, double amount) {
        amount = Math.floor(amount * 100) / 100;
        double wallet = wallet(p);
        if (amount <= 0) {
            p.sendMessage(PREFIX + "§cMontant invalide.");
            return;
        }
        if (amount > wallet) {
            p.sendMessage(PREFIX + "§cTu n'as que §6" + format(wallet) + " §cdans ton portefeuille.");
            return;
        }
        EconomyResponse r = economy.withdrawPlayer(p, amount);
        if (!r.transactionSuccess()) {
            p.sendMessage(PREFIX + "§cDepot refuse : " + r.errorMessage);
            return;
        }
        setBank(p, bank(p.getUniqueId()) + amount);
        save();
        p.sendMessage(PREFIX + "Tu as depose §6" + format(amount) + "§7. En banque : §6" + format(bank(p.getUniqueId())) + "§7.");
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
    }

    void withdraw(Player p, double amount) {
        amount = Math.floor(amount * 100) / 100;
        double bank = bank(p.getUniqueId());
        if (amount <= 0) {
            p.sendMessage(PREFIX + "§cMontant invalide.");
            return;
        }
        if (amount > bank) {
            p.sendMessage(PREFIX + "§cTu n'as que §6" + format(bank) + " §cen banque.");
            return;
        }
        setBank(p, bank - amount);
        EconomyResponse r = economy.depositPlayer(p, amount);
        if (!r.transactionSuccess()) {
            setBank(p, bank);
            save();
            p.sendMessage(PREFIX + "§cRetrait refuse : " + r.errorMessage);
            return;
        }
        save();
        p.sendMessage(PREFIX + "Tu as retire §6" + format(amount) + "§7. Portefeuille : §6" + format(wallet(p)) + "§7.");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    /** Verse les interets si la periode est passee (une seule periode a la fois). */
    private void payInterest(Player p) {
        if (!getConfig().getBoolean("interest.enabled", true)) return;
        String key = p.getUniqueId() + ".last-interest";
        double bank = bank(p.getUniqueId());
        if (bank <= 0 || !data.contains(key)) return;
        long period = (long) (getConfig().getDouble("interest.every-hours", 24) * 3_600_000L);
        long last = data.getLong(key);
        long now = System.currentTimeMillis();
        if (period <= 0 || now - last < period) return;
        double gain = bank * getConfig().getDouble("interest.percent", 2.0) / 100.0;
        gain = Math.min(gain, getConfig().getDouble("interest.max-per-payout", 10000));
        gain = Math.floor(gain * 100) / 100;
        data.set(key, now);
        dirty = true;
        if (gain <= 0) return;
        setBank(p, bank + gain);
        p.sendMessage(PREFIX + "Interets : §a+" + format(gain) + "§7. En banque : §6" + format(bank(p.getUniqueId())) + "§7.");
    }

    /** Temps avant les prochains interets, pour le menu. */
    String nextInterest(UUID id) {
        if (!data.contains(id + ".last-interest") || bank(id) <= 0) return "des ton premier depot";
        long period = (long) (getConfig().getDouble("interest.every-hours", 24) * 3_600_000L);
        long left = data.getLong(id + ".last-interest") + period - System.currentTimeMillis();
        if (left <= 0) return "dans moins d'une minute";
        long h = left / 3_600_000L, m = (left / 60_000L) % 60;
        return "dans " + (h > 0 ? h + " h " : "") + m + " min";
    }

    // ------------------------------------------------------------------ mort

    /** Garde l'inventaire et l'XP (mort dans le vide, ou toute mort selon keep-inventory.all-deaths). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeathKeep(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("keep-inventory.enabled", true)) return;
        if (!getConfig().getStringList("worlds").contains(p.getWorld().getName())) return;
        EntityDamageEvent last = p.getLastDamageCause();
        boolean voidDeath = last != null && last.getCause() == EntityDamageEvent.DamageCause.VOID;
        if (!voidDeath && !getConfig().getBoolean("keep-inventory.all-deaths", false)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!getConfig().getBoolean("death-penalty.enabled", true)) return;
        if (!getConfig().getStringList("worlds").contains(p.getWorld().getName())) return;
        EntityDamageEvent last = p.getLastDamageCause();
        boolean voidDeath = last != null && last.getCause() == EntityDamageEvent.DamageCause.VOID;
        if (getConfig().getBoolean("death-penalty.only-void", true) && !voidDeath) return;
        double wallet = wallet(p);
        if (wallet <= 0) return;
        double lost = Math.floor(wallet * getConfig().getDouble("death-penalty.percent", 50) / 100.0 * 100) / 100;
        if (lost <= 0) return;
        if (!economy.withdrawPlayer(p, lost).transactionSuccess()) return;
        getLogger().info(p.getName() + " perd " + format(lost) + " en mourant (" + (voidDeath ? "vide" : "autre") + ").");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(PREFIX + "§cTu as perdu §6" + format(lost) + " §c(" + getConfig().getInt("death-penalty.percent", 50)
                    + " % de ton portefeuille), mais tu gardes ton stuff. §7L'argent en banque ne se perd jamais : §e/bank");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
        }, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> payInterest(e.getPlayer()), 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingAmount.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ montant tape dans le chat

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Boolean depositing = awaitingAmount.remove(id);
        if (depositing == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(this, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            if (text.trim().equalsIgnoreCase("annuler") || text.trim().equalsIgnoreCase("cancel")) {
                p.sendMessage(PREFIX + "Annule.");
                return;
            }
            double max = depositing ? wallet(p) : bank(id);
            double amount = parseAmount(text, max);
            if (depositing) deposit(p, amount);
            else withdraw(p, amount);
            openMenu(p);
        });
    }

    // ------------------------------------------------------------------ menu

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void openMenu(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, LEGACY.deserialize("§8Banque"));
        holder.inventory = inv;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        UUID id = p.getUniqueId();
        double bank = bank(id), wallet = wallet(p);
        double rate = getConfig().getDouble("interest.percent", 2.0);
        inv.setItem(4, item(Material.GOLD_BLOCK, "§6§lTa banque",
                "§7En banque : §6" + format(bank),
                "§7Portefeuille : §6" + format(wallet),
                "",
                "§7L'argent en banque ne se perd jamais.",
                "§7Le portefeuille perd §c" + getConfig().getInt("death-penalty.percent", 50) + " % §7si tu",
                "§7meurs dans le vide au skyblock.",
                "",
                getConfig().getBoolean("interest.enabled", true)
                        ? "§7Interets : §a" + money.format(rate) + " % §7toutes les §a" + money.format(getConfig().getDouble("interest.every-hours", 24)) + " h"
                        : "§7Pas d'interets.",
                getConfig().getBoolean("interest.enabled", true) ? "§7Prochains : §a" + nextInterest(id) : ""));
        inv.setItem(10, item(Material.CHEST, "§a§lDeposer tout", "§7" + format(wallet)));
        inv.setItem(11, item(Material.GOLD_INGOT, "§a§lDeposer la moitie", "§7" + format(wallet / 2)));
        inv.setItem(12, item(Material.OAK_SIGN, "§a§lDeposer un montant", "§7Tape le montant dans le chat", "§7(ex. 1500, 2k, tout, moitie)."));
        inv.setItem(14, item(Material.OAK_SIGN, "§e§lRetirer un montant", "§7Tape le montant dans le chat", "§7(ex. 1500, 2k, tout, moitie)."));
        inv.setItem(15, item(Material.GOLD_NUGGET, "§e§lRetirer la moitie", "§7" + format(bank / 2)));
        inv.setItem(16, item(Material.DROPPER, "§e§lRetirer tout", "§7" + format(bank)));
        inv.setItem(22, item(Material.BARRIER, "§cFermer"));
        p.openInventory(inv);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(LEGACY.deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(LEGACY.deserialize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        if (!lines.isEmpty()) meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        UUID id = p.getUniqueId();
        switch (e.getSlot()) {
            case 10 -> { deposit(p, wallet(p)); openMenu(p); }
            case 11 -> { deposit(p, wallet(p) / 2); openMenu(p); }
            case 15 -> { withdraw(p, bank(id) / 2); openMenu(p); }
            case 16 -> { withdraw(p, bank(id)); openMenu(p); }
            case 12, 14 -> {
                boolean depositing = e.getSlot() == 12;
                awaitingAmount.put(id, depositing);
                p.closeInventory();
                p.sendMessage(PREFIX + "Tape dans le chat le montant a " + (depositing ? "deposer" : "retirer")
                        + " §8(ex. 1500, 2k, tout, moitie ; §7annuler§8 pour arreter)§7.");
            }
            case 22 -> p.closeInventory();
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("eemibank.admin");
        if (sub.equals("reload") && admin) {
            reloadConfig();
            sender.sendMessage(PREFIX + "Config rechargee.");
            return true;
        }
        if (sub.equals("voir") && admin && args.length > 1) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            sender.sendMessage(PREFIX + args[1] + " : banque §6" + format(bank(target.getUniqueId()))
                    + "§7, portefeuille §6" + format(wallet(target)) + "§7.");
            return true;
        }
        if (sub.equals("set") && admin && args.length > 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            double amount = parseAmount(args[2], 0);
            if (amount < 0) {
                sender.sendMessage(PREFIX + "§cMontant invalide.");
                return true;
            }
            setBank(target, amount);
            save();
            sender.sendMessage(PREFIX + "Banque de " + args[1] + " : §6" + format(amount) + "§7.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(PREFIX + "/bank voir <joueur> | set <joueur> <montant> | reload");
            return true;
        }
        if (sub.isEmpty()) {
            openMenu(p);
            return true;
        }
        boolean depositing = sub.equals("deposer") || sub.equals("deposit") || sub.equals("d");
        boolean withdrawing = sub.equals("retirer") || sub.equals("withdraw") || sub.equals("r");
        if ((depositing || withdrawing) && args.length > 1) {
            double max = depositing ? wallet(p) : bank(p.getUniqueId());
            double amount = parseAmount(args[1], max);
            if (depositing) deposit(p, amount);
            else withdraw(p, amount);
            return true;
        }
        p.sendMessage(PREFIX + "§e/bank §7: ouvrir la banque");
        p.sendMessage(PREFIX + "§e/bank deposer <montant|tout|moitie>");
        p.sendMessage(PREFIX + "§e/bank retirer <montant|tout|moitie>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("deposer", "retirer"));
            if (sender.hasPermission("eemibank.admin")) out.addAll(List.of("voir", "set", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("deposer") || args[0].equalsIgnoreCase("retirer"))) {
            out.addAll(List.of("tout", "moitie", "1000"));
        } else {
            return null;
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(prefix));
        return out;
    }
}
