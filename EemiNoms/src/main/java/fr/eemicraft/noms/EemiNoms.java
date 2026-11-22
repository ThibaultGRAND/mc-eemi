package fr.eemicraft.noms;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * /rename : un joueur autorisé (eeminoms.self) met son prénom comme nom affiché (chat, liste des joueurs via
 * %eeminoms_nom% dans TAB). Les admins (eeminoms.admin) renomment n'importe qui et donnent le droit de se renommer.
 */
public final class EemiNoms extends JavaPlugin implements Listener, TabCompleter {

    static final String P = "§8[§eNom§8] §7";

    /** uuid -> fiche (nom affiché, vrai pseudo, qui l'a mis, date). */
    private final Map<UUID, Fiche> noms = new HashMap<>();
    private final Map<UUID, Long> dernier = new HashMap<>();
    private File fichier;

    record Fiche(String nom, String pseudo, String par, String date) { }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fichier = new File(getDataFolder(), "noms.yml");
        charger();
        getServer().getPluginManager().registerEvents(this, this);
        for (String c : List.of("rename", "vraipseudo")) Objects.requireNonNull(getCommand(c)).setTabCompleter(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new Expansion().register();
        for (Player p : Bukkit.getOnlinePlayers()) appliquer(p);
        getLogger().info(noms.size() + " nom(s) affiche(s).");
    }

    // ------------------------------------------------------------------ données

    private void charger() {
        noms.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String k : y.getKeys(false)) {
            try {
                noms.put(UUID.fromString(k), new Fiche(y.getString(k + ".nom", ""), y.getString(k + ".pseudo", ""),
                        y.getString(k + ".par", ""), y.getString(k + ".date", "")));
            } catch (IllegalArgumentException ignored) { }
        }
        noms.values().removeIf(f -> f.nom().isBlank());
    }

    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Fiche> e : noms.entrySet()) {
            String k = e.getKey().toString();
            y.set(k + ".nom", e.getValue().nom());
            y.set(k + ".pseudo", e.getValue().pseudo());
            y.set(k + ".par", e.getValue().par());
            y.set(k + ".date", e.getValue().date());
        }
        try { y.save(fichier); } catch (IOException ex) { getLogger().warning("noms.yml : " + ex.getMessage()); }
    }

    /** Nom affiché d'un joueur, ou son pseudo s'il n'en a pas. */
    String nom(UUID id, String pseudo) {
        Fiche f = noms.get(id);
        return f == null ? pseudo : f.nom();
    }

    /** Pose le nom affiché (chat : displayName avec le vrai pseudo au survol ; liste : playerListName). */
    void appliquer(Player p) {
        Fiche f = noms.get(p.getUniqueId());
        if (f == null) {
            p.displayName(null);
            p.playerListName(null);
            return;
        }
        String coul = getConfig().getString("couleur", "");
        Component c = (coul == null || coul.isBlank() ? Component.text(f.nom())
                : LegacyComponentSerializer.legacyAmpersand().deserialize(coul + f.nom()))
                .hoverEvent(HoverEvent.showText(Component.text("Pseudo : ", NamedTextColor.GRAY)
                        .append(Component.text(p.getName(), NamedTextColor.WHITE))));
        p.displayName(c);
        p.playerListName(Component.text(f.nom()));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Fiche f = noms.get(p.getUniqueId());
        if (f != null && !f.pseudo().equals(p.getName())) {
            noms.put(p.getUniqueId(), new Fiche(f.nom(), p.getName(), f.par(), f.date()));
            sauver();
        }
        appliquer(p);
    }

    // ------------------------------------------------------------------ règles

    private static String plat(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace(" ", "");
    }

    /** null = nom valable, sinon la raison du refus. */
    private String refus(UUID pour, String nom, boolean admin) {
        int min = getConfig().getInt("longueur-min", 2), max = getConfig().getInt("longueur-max", 16);
        if (nom.length() < min || nom.length() > max) return "Le nom doit faire entre " + min + " et " + max + " caractères.";
        if (!Pattern.compile(getConfig().getString("caracteres", "^[\\p{L}][\\p{L} '\\-]*$")).matcher(nom).matches())
            return "Seulement des lettres (accents compris), espaces, tirets et apostrophes.";
        if (nom.contains("  ")) return "Pas deux espaces de suite.";
        String n = plat(nom);
        if (!admin) for (String mot : getConfig().getStringList("interdits"))
            if (n.contains(plat(mot))) return "Ce nom n'est pas autorisé.";
        // pas le pseudo ni le nom affiché de quelqu'un d'autre (usurpation)
        for (Map.Entry<UUID, Fiche> e : noms.entrySet())
            if (!e.getKey().equals(pour) && plat(e.getValue().nom()).equals(n)) return "Ce nom est déjà pris par un autre joueur.";
        for (OfflinePlayer op : Bukkit.getOfflinePlayers())
            if (!op.getUniqueId().equals(pour) && op.getName() != null && plat(op.getName()).equals(n))
                return "C'est le pseudo d'un autre joueur.";
        return null;
    }

    private static String propre(String s) {
        s = s.trim().replaceAll("\\s+", " ");
        StringBuilder b = new StringBuilder();
        boolean debut = true;
        for (char c : s.toCharArray()) {
            b.append(debut ? Character.toUpperCase(c) : c);
            debut = c == ' ' || c == '-';
        }
        return b.toString();
    }

    private void poser(CommandSender par, UUID id, String pseudo, String nom) {
        if (nom == null) noms.remove(id);
        else noms.put(id, new Fiche(nom, pseudo, par.getName(), LocalDate.now().toString()));
        sauver();
        Player p = Bukkit.getPlayer(id);
        if (p != null) appliquer(p);
        getLogger().info(par.getName() + " : " + pseudo + (nom == null ? " reprend son pseudo" : " s'appelle " + nom));
    }

    // ------------------------------------------------------------------ commandes

    private boolean admin(CommandSender s) { return s.hasPermission("eeminoms.admin"); }

    private void aide(CommandSender s) {
        s.sendMessage(P + "§e/rename <prénom> §7: mettre ton prénom comme nom affiché");
        s.sendMessage(P + "§e/rename reset §7: reprendre ton pseudo");
        s.sendMessage(P + "§e/vraipseudo <nom> §7: qui se cache derrière un nom");
        if (!admin(s)) return;
        s.sendMessage(P + "§6Admin : §e/rename <joueur> <nom> §7| §e/rename reset <joueur>");
        s.sendMessage(P + "§6Admin : §e/rename droit <joueur> [on|off] §7: droit de se renommer");
        s.sendMessage(P + "§6Admin : §e/rename liste §7| §e/rename reload");
    }

    /** Pseudo -> joueur connu du serveur (en ligne d'abord). */
    private static OfflinePlayer connu(String pseudo) {
        Player p = Bukkit.getPlayerExact(pseudo);
        if (p != null) return p;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers())
            if (op.getName() != null && op.getName().equalsIgnoreCase(pseudo)) return op;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equals("vraipseudo")) return vraiPseudo(s, a);
        if (a.length == 0 || a[0].equalsIgnoreCase("aide") || a[0].equalsIgnoreCase("help")) { aide(s); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (admin(s)) switch (sub) {
            case "reload" -> {
                reloadConfig();
                charger();
                for (Player p : Bukkit.getOnlinePlayers()) appliquer(p);
                s.sendMessage(P + "Rechargé : " + noms.size() + " nom(s).");
                return true;
            }
            case "liste" -> {
                if (noms.isEmpty()) { s.sendMessage(P + "Personne n'a de nom affiché."); return true; }
                s.sendMessage(P + "§e" + noms.size() + " nom(s) affiché(s) :");
                noms.values().stream().sorted(Comparator.comparing(f -> f.pseudo().toLowerCase(Locale.ROOT)))
                        .forEach(f -> s.sendMessage("§8- §f" + f.nom() + " §7= " + f.pseudo() + " §8(par " + f.par() + ", " + f.date() + ")"));
                return true;
            }
            case "droit" -> {
                if (a.length < 2) { s.sendMessage(P + "/rename droit <joueur> [on|off]"); return true; }
                OfflinePlayer op = connu(a[1]);
                if (op == null || op.getName() == null) { s.sendMessage(P + "§cJoueur inconnu : " + a[1]); return true; }
                boolean on = a.length < 3 || !a[2].equalsIgnoreCase("off");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + op.getName() + " permission "
                        + (on ? "set eeminoms.self true" : "unset eeminoms.self"));
                s.sendMessage(P + "§f" + op.getName() + (on ? " §apeut maintenant se renommer §7(/rename <prénom>)."
                        : " §cne peut plus se renommer §7(son nom actuel reste : /rename reset " + op.getName() + ")."));
                getLogger().info(s.getName() + " : droit de se renommer " + (on ? "donne a " : "retire a ") + op.getName());
                return true;
            }
            case "reset" -> {
                if (a.length >= 2) {
                    OfflinePlayer op = connu(a[1]);
                    if (op == null) { s.sendMessage(P + "§cJoueur inconnu : " + a[1]); return true; }
                    if (!noms.containsKey(op.getUniqueId())) { s.sendMessage(P + op.getName() + " n'a pas de nom affiché."); return true; }
                    poser(s, op.getUniqueId(), op.getName(), null);
                    s.sendMessage(P + "§f" + op.getName() + " §7reprend son pseudo.");
                    Player p = op.getPlayer();
                    if (p != null && p != s) p.sendMessage(P + "Un admin t'a remis ton pseudo.");
                    return true;
                }
            }
            default -> {
                // /rename <joueur> <nom...> : le 1er mot est un joueur connu et il y a un nom derrière
                OfflinePlayer op = a.length >= 2 ? connu(a[0]) : null;
                if (op != null && op.getName() != null) {
                    String nom = propre(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
                    String r = refus(op.getUniqueId(), nom, true);
                    if (r != null) { s.sendMessage(P + "§c" + r); return true; }
                    poser(s, op.getUniqueId(), op.getName(), nom);
                    s.sendMessage(P + "§f" + op.getName() + " §7s'appelle maintenant §e" + nom + "§7.");
                    Player p = op.getPlayer();
                    if (p != null && p != s) p.sendMessage(P + "Un admin t'a renommé : §e" + nom);
                    return true;
                }
            }
        }

        if (!(s instanceof Player p)) { s.sendMessage(P + "Console : /rename <joueur> <nom>"); return true; }
        if (sub.equals("reset")) {
            if (!noms.containsKey(p.getUniqueId())) { p.sendMessage(P + "Tu as déjà ton pseudo."); return true; }
            poser(p, p.getUniqueId(), p.getName(), null);
            p.sendMessage(P + "Tu reprends ton pseudo §f" + p.getName() + "§7.");
            return true;
        }
        if (!p.hasPermission("eeminoms.self")) {
            p.sendMessage(P + "§cTu n'as pas le droit de te renommer. §7Demande à un admin.");
            return true;
        }
        long maintenant = System.currentTimeMillis(), delai = getConfig().getLong("delai", 60) * 1000L;
        Long avant = dernier.get(p.getUniqueId());
        if (!admin(p) && avant != null && maintenant - avant < delai) {
            p.sendMessage(P + "§cAttends encore " + ((delai - (maintenant - avant)) / 1000 + 1) + " s.");
            return true;
        }
        String nom = propre(String.join(" ", a));
        String r = refus(p.getUniqueId(), nom, admin(p));
        if (r != null) { p.sendMessage(P + "§c" + r); return true; }
        dernier.put(p.getUniqueId(), maintenant);
        poser(p, p.getUniqueId(), p.getName(), nom);
        p.sendMessage(P + "Tu t'appelles maintenant §e" + nom + "§7. §8(/rename reset pour reprendre ton pseudo)");
        return true;
    }

    private boolean vraiPseudo(CommandSender s, String[] a) {
        if (a.length == 0) { s.sendMessage(P + "/vraipseudo <nom affiché>"); return true; }
        String n = plat(String.join(" ", a));
        List<String> res = new ArrayList<>();
        for (Fiche f : noms.values()) if (plat(f.nom()).contains(n)) res.add("§e" + f.nom() + " §7= §f" + f.pseudo());
        s.sendMessage(res.isEmpty() ? P + "Aucun nom affiché ne correspond." : P + String.join("§7, ", res));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> r = new ArrayList<>();
        if (cmd.getName().equals("vraipseudo")) {
            if (a.length == 1) for (Fiche f : noms.values()) r.add(f.nom());
        } else if (a.length == 1) {
            r.add("reset");
            if (admin(s)) {
                r.addAll(List.of("droit", "liste", "reload"));
                for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
            }
        } else if (a.length == 2 && admin(s) && List.of("droit", "reset").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        } else if (a.length == 3 && admin(s) && a[0].equalsIgnoreCase("droit")) {
            r.addAll(List.of("on", "off"));
        }
        String d = a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(d));
        return r;
    }

    // ------------------------------------------------------------------ PlaceholderAPI

    /** %eeminoms_nom% (nom affiché ou pseudo), %eeminoms_pseudo%, %eeminoms_renomme% (oui/non). */
    private final class Expansion extends PlaceholderExpansion {
        @Override public String getIdentifier() { return "eeminoms"; }
        @Override public String getAuthor() { return "EEMICRAFT"; }
        @Override public String getVersion() { return getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onRequest(OfflinePlayer op, String p) {
            if (op == null) return "";
            String pseudo = op.getName() == null ? "" : op.getName();
            return switch (p) {
                case "nom" -> nom(op.getUniqueId(), pseudo);
                case "pseudo" -> pseudo;
                case "renomme" -> noms.containsKey(op.getUniqueId()) ? "oui" : "non";
                default -> null;
            };
        }
    }
}
