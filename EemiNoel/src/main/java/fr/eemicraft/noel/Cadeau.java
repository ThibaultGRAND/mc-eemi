package fr.eemicraft.noel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cadeau du Père Noël : /noel cadeau (lancé par le PNJ FancyNpcs en player_command), une fois par joueur et par
 * saison, interface d'un cadeau qui tremble puis s'ouvre, pièces EemiCore données par « eemi give ».
 * Le cadeau est noté ouvert dès le début (cadeaux.yml écrit tout de suite) ; la récompense est donnée une seule fois,
 * à l'ouverture du cadeau ou plus tôt si le joueur ferme l'interface, se déconnecte ou si le plugin s'arrête.
 */
final class Cadeau implements Listener {

    private static final String FW_TAG = "eeminoel_cadeau";
    private static final int SIZE = 45, CENTER = 22;
    /** Cases d'étincelles autour du cadeau (hors bloc 3x3 central). */
    private static final int[] SPARKLES = {1, 3, 5, 7, 9, 11, 15, 17, 19, 25, 27, 29, 33, 35, 37, 39, 41, 43};
    private static final Material[] SPARK_COLORS = {Material.RED_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};
    private static final Material[] BURST = {Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE};
    /** Durée du tremblement en pas de 2 ticks (30 pas = 3 s). */
    private static final int SHAKE_STEPS = 30;

    static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final class Session {
        final Player player;
        final Holder holder;
        final int coins;
        BukkitTask task;
        boolean rewarded, revealed;

        Session(Player player, Holder holder, int coins) {
            this.player = player;
            this.holder = holder;
            this.coins = coins;
        }
    }

    private final EemiNoel plugin;
    private final File file;
    private final Map<UUID, Session> sessions = new HashMap<>();

    Cadeau(EemiNoel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cadeaux.yml");
    }

    private String season() {
        return plugin.getConfig().getString("cadeau.saison", "noel2026");
    }

    private int coins() {
        return Math.max(1, plugin.getConfig().getInt("cadeau.pieces", 300));
    }

    // ------------------------------------------------------------------ fichier cadeaux.yml

    private Set<String> opened() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        return new HashSet<>(y.getStringList(season()));
    }

    /** Écriture sûre : fichier temporaire puis remplacement atomique. */
    private boolean saveOpened(Set<String> ids) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        y.set(season(), new ArrayList<>(ids));
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "cadeaux.yml.tmp");
            Files.writeString(tmp.toPath(), y.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomic) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire cadeaux.yml : " + e.getMessage());
            return false;
        }
    }

    private static boolean coreReady() {
        Plugin core = Bukkit.getPluginManager().getPlugin("EemiCore");
        return core != null && core.isEnabled() && Bukkit.getPluginCommand("eemi") != null;
    }

    // ------------------------------------------------------------------ commandes

    /** /noel cadeau [reset <joueur>|liste]. */
    void command(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        if (sub.equals("reset") || sub.equals("liste") || sub.equals("list")) {
            if (!sender.hasPermission("eeminoel.admin")) {
                sender.sendMessage(plugin.prefix() + "§cPermission refusée.");
                return;
            }
            if (sub.equals("reset")) {
                if (args.length < 3) {
                    sender.sendMessage(plugin.prefix() + "/noel cadeau reset <joueur>");
                    return;
                }
                OfflinePlayer op = Bukkit.getPlayerExact(args[2]);
                if (op == null) op = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (op == null) {
                    sender.sendMessage(plugin.prefix() + "§cJoueur inconnu : §f" + args[2]);
                    return;
                }
                Set<String> ids = opened();
                if (!ids.remove(op.getUniqueId().toString())) {
                    sender.sendMessage(plugin.prefix() + "§f" + args[2] + "§7 n'a pas encore ouvert son cadeau (saison §f" + season() + "§7).");
                    return;
                }
                if (saveOpened(ids)) sender.sendMessage(plugin.prefix() + "§aCadeau rendu à §f" + args[2] + "§a : il peut l'ouvrir à nouveau.");
                else sender.sendMessage(plugin.prefix() + "§cÉcriture de cadeaux.yml impossible, rien n'a changé.");
            } else {
                sender.sendMessage(plugin.prefix() + "Cadeaux ouverts (saison §f" + season() + "§7) : §e" + opened().size()
                        + "§7, §e" + coins() + "§7 pièces chacun.");
            }
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.prefix() + "§cÀ faire en jeu. Admin : /noel cadeau reset <joueur> | liste");
            return;
        }
        open(p);
    }

    private void open(Player p) {
        if (!p.hasPermission("eeminoel.cadeau")) {
            p.sendMessage(plugin.prefix() + "§cPermission refusée.");
            return;
        }
        if (!plugin.ambianceOn()) {
            p.sendMessage(plugin.prefix() + plugin.msg("cadeau-hors-saison",
                    "Le Père Noël n'est pas encore arrivé : reviens pendant les fêtes !"));
            return;
        }
        if (!plugin.isInLobby(p)) {
            p.sendMessage(plugin.prefix() + plugin.msg("cadeau-hors-lobby", "Le Père Noël distribue ses cadeaux au §flobby§7 !"));
            return;
        }
        if (sessions.containsKey(p.getUniqueId())) return; // interface déjà ouverte
        String id = p.getUniqueId().toString();
        Set<String> ids = opened();
        if (ids.contains(id)) {
            p.sendMessage(plugin.prefix() + plugin.msg("cadeau-deja",
                    "§fTu as déjà ouvert ton cadeau de Noël §7(§6{pieces} pièces§7 reçues). Reviens l'an prochain ! §c❤",
                    "pieces", coins()));
            p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    plugin.msg("cadeau-deja-barre", "§7Cadeau déjà ouvert cette saison")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 0.7f, 0.6f);
            return;
        }
        if (!coreReady()) {
            p.sendMessage(plugin.prefix() + "§cLa hotte est coincée (EemiCore indisponible) : réessaie plus tard.");
            plugin.getLogger().warning("Cadeau de " + p.getName() + " refusé : EemiCore absent ou commande /eemi introuvable.");
            return;
        }
        ids.add(id);
        if (!saveOpened(ids)) {
            p.sendMessage(plugin.prefix() + "§cLe cadeau n'a pas pu être enregistré : réessaie dans un instant.");
            return;
        }
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, SIZE, Component.text("Cadeau du Père Noël"));
        Session s = new Session(p, h, coins());
        sessions.put(p.getUniqueId(), s);
        Material dark = Material.BLACK_STAINED_GLASS_PANE;
        for (int i = 0; i < SIZE; i++) h.inv.setItem(i, item(i % 2 == 0 ? dark : Material.GRAY_STAINED_GLASS_PANE, " "));
        drawGift(s, CENTER, false);
        p.openInventory(h.inv);
        p.sendMessage(plugin.prefix() + plugin.msg("cadeau-ouverture", "§fLe Père Noël te tend ton cadeau... §7il s'ouvre !"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 0.8f, 0.8f);
        final int[] step = {0};
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> animate(s, step[0]++), 2L, 2L);
    }

    // ------------------------------------------------------------------ animation

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        LegacyComponentSerializer ser = LegacyComponentSerializer.legacySection();
        meta.displayName(ser.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(ser.deserialize(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    /** Cadeau emballé en {@code slot} (ruban doré au-dessus) ; les cases 21..23 / 12..14 libres redeviennent sombres. */
    private void drawGift(Session s, int slot, boolean alt) {
        Inventory inv = s.holder.inv;
        for (int c : new int[]{12, 13, 14, 21, 22, 23}) inv.setItem(c, item(Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(slot, item(alt ? Material.RED_CONCRETE : Material.RED_WOOL, "§c§lCadeau",
                "§7Pour §f" + s.player.getName(), "§7De la part du §cPère Noël"));
        inv.setItem(slot - 9, item(Material.GOLD_NUGGET, "§6Ruban"));
    }

    private void animate(Session s, int step) {
        Player p = s.player;
        if (!p.isOnline()) {
            finish(s);
            return;
        }
        Inventory inv = s.holder.inv;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (step < SHAKE_STEPS) {
            // tremblement, de plus en plus rapide
            double t = step / (double) SHAKE_STEPS;
            int period = t < 0.4 ? 4 : t < 0.75 ? 2 : 1;
            int phase = (step / period) % 4;
            int slot = phase == 1 ? CENTER - 1 : phase == 3 ? CENTER + 1 : CENTER;
            drawGift(s, slot, (step / period) % 2 == 1);
            // étincelles de plus en plus nombreuses
            double lit = 0.15 + 0.6 * t;
            for (int c : SPARKLES)
                inv.setItem(c, r.nextDouble() < lit ? item(SPARK_COLORS[r.nextInt(SPARK_COLORS.length)], "§e✦")
                        : item(Material.BLACK_STAINED_GLASS_PANE, " "));
            if (step % 2 == 0) {
                float pitch = (float) Math.min(2.0, 0.5 + 1.5 * t);
                p.playSound(p.getLocation(), step % 4 == 0 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_CHIME,
                        SoundCategory.MASTER, 0.7f, pitch);
            }
            return;
        }
        if (step == SHAKE_STEPS) {
            reveal(s);
            return;
        }
        // après l'ouverture : les couleurs scintillent encore un peu, puis on ferme
        if (step < SHAKE_STEPS + 40) {
            if (step % 3 == 0)
                for (int c = 0; c < SIZE; c++) {
                    if (c == CENTER || c == CENTER - 1 || c == CENTER + 1 || c == CENTER - 9 || c == CENTER + 9) continue;
                    inv.setItem(c, item(BURST[r.nextInt(BURST.length)], "§6✦ §c§lJoyeux Noël ! §6✦"));
                }
            return;
        }
        finish(s);
        if (p.getOpenInventory().getTopInventory().getHolder() == s.holder) p.closeInventory();
    }

    private void reveal(Session s) {
        s.revealed = true;
        Player p = s.player;
        Inventory inv = s.holder.inv;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int c = 0; c < SIZE; c++) inv.setItem(c, item(BURST[r.nextInt(BURST.length)], "§6✦ §c§lJoyeux Noël ! §6✦"));
        ItemStack gold = item(Material.GOLD_INGOT, "§6§l+" + s.coins + " pièces", "§7Joyeux Noël, §f" + p.getName() + "§7 !",
                "§7Elles sont déjà dans ton porte-monnaie.");
        ItemMeta gm = gold.getItemMeta();
        gm.setEnchantmentGlintOverride(true);
        gold.setItemMeta(gm);
        gold.setAmount(3);
        inv.setItem(CENTER, gold);
        for (int c : new int[]{CENTER - 1, CENTER + 1, CENTER - 9, CENTER + 9})
            inv.setItem(c, item(Material.GOLD_NUGGET, "§6§l+" + s.coins + " pièces"));
        reward(s);
        // fête autour du joueur
        Location at = p.getLocation();
        p.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);
        p.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.MASTER, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.3);
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 2, 0), 40, 0.8, 0.6, 0.8, 0.05);
        try {
            Firework fw = p.getWorld().spawn(at.clone().add(0, 1.5, 0), Firework.class, f -> {
                f.addScoreboardTag(FW_TAG);
                FireworkMeta fm = f.getFireworkMeta();
                fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR)
                        .withColor(Color.RED, Color.GREEN, Color.fromRGB(255, 215, 0)).withFade(Color.WHITE)
                        .flicker(true).trail(true).build());
                fm.setPower(0);
                f.setFireworkMeta(fm);
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (fw.isValid()) fw.detonate();
            }, 2L);
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Feu d'artifice du cadeau impossible : " + e);
        }
    }

    /** Donne les pièces (une seule fois par session). */
    private void reward(Session s) {
        if (s.rewarded) return;
        s.rewarded = true;
        Player p = s.player;
        boolean ok;
        try {
            ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eemi give " + p.getName() + " coins " + s.coins);
        } catch (RuntimeException e) {
            ok = false;
            plugin.getLogger().warning("eemi give a échoué pour " + p.getName() + " : " + e);
        }
        plugin.getLogger().info("Cadeau de Noël ouvert par " + p.getName() + " (" + s.coins + " pièces, "
                + (ok ? "données" : "ÉCHEC de eemi give") + ").");
        if (!ok) {
            if (p.isOnline())
                p.sendMessage(plugin.prefix() + "§cTes pièces n'ont pas pu être versées : préviens un admin (cadeau noté ouvert).");
            return;
        }
        if (p.isOnline()) {
            p.showTitle(Title.title(LegacyComponentSerializer.legacySection().deserialize("§c§lJoyeux Noël !"),
                    LegacyComponentSerializer.legacySection().deserialize("§6+" + s.coins + " pièces"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
            p.sendMessage(plugin.prefix() + plugin.msg("cadeau-recu",
                    "Le Père Noël t'offre §6{pieces} pièces§7, elles sont dans ton porte-monnaie. Joyeuses fêtes !",
                    "pieces", s.coins));
        }
    }

    private void finish(Session s) {
        if (s.task != null) s.task.cancel();
        s.task = null;
        sessions.remove(s.player.getUniqueId());
        reward(s);
    }

    /** Arrêt du plugin : personne ne perd ses pièces. */
    void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) {
            finish(s);
            if (s.player.isOnline() && s.player.getOpenInventory().getTopInventory().getHolder() == s.holder)
                s.player.closeInventory();
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || s.holder != h) return;
        if (!s.revealed) {
            Player p = s.player;
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);
        }
        finish(s);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s != null) finish(s);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFireworkDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework f && f.getScoreboardTags().contains(FW_TAG)) e.setCancelled(true);
    }
}
