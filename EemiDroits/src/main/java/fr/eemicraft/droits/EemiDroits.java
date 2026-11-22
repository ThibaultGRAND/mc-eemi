package fr.eemicraft.droits;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

public final class EemiDroits extends JavaPlugin implements TabCompleter {

    LuckPerms lp;
    Jeux jeux;
    Fiches fiches;
    Garde garde;
    Menus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lp = LuckPermsProvider.get();
        lp.getGroupManager().loadAllGroups();
        jeux = new Jeux(this);
        fiches = new Fiches(this);
        jeux.charger();
        fiches.charger();
        garde = new Garde(this);
        menus = new Menus(this);
        fr.eemicraft.droits.ui.Ui.log = getLogger();
        getServer().getPluginManager().registerEvents(garde, this);
        getServer().getPluginManager().registerEvents(menus, this);
        Objects.requireNonNull(getCommand("droits")).setTabCompleter(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) new DroitsExpansion(this).register();
        getLogger().info(fiches.fiches.size() + " fiches de joueurs.");
    }

    Location positionHub() {
        String s = getConfig().getString("position-hub", "");
        if (s != null && !s.isBlank()) {
            String[] a = s.trim().split("\\s+");
            World w = a.length >= 4 ? Bukkit.getWorld(a[0]) : null;
            if (w != null) {
                try {
                    return new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                            a.length >= 5 ? Float.parseFloat(a[4]) : 0f, a.length >= 6 ? Float.parseFloat(a[5]) : 0f);
                } catch (NumberFormatException ignored) {}
            }
        }
        World w = Bukkit.getWorld(getConfig().getString("monde-hub", "lobby"));
        return w == null ? null : w.getSpawnLocation();
    }

    // ------------------------------------------------------------------ commande

    private static final String AIDE = String.join("\n",
            Txt.PREFIXE + "§eCommandes :",
            "§e/droits §7: interface",
            "§e/droits joueur <pseudo> §7: fiche d'un joueur (même hors ligne)",
            "§e/droits beta <pseudo> [on|off] §7: tag Bêta",
            "§e/droits jeu <jeu> <ouvert|beta|maintenance> [raison] §7: état d'un jeu",
            "§e/droits jeux §7: état de tous les jeux",
            "§e/droits monde <pseudo> <monde> [on|off] §7: interdire un monde (on = interdit)",
            "§e/droits interdire <pseudo> <jeu> [on|off] §7: interdire un jeu",
            "§e/droits reload");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("eemidroits.admin")) {
            s.sendMessage(Txt.PREFIXE + "§cTu n'as pas la permission.");
            return true;
        }
        if (a.length == 0) {
            if (s instanceof Player p) menus.accueil(p);
            else s.sendMessage(AIDE);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "aide", "help" -> s.sendMessage(AIDE);
            case "reload" -> {
                reloadConfig();
                jeux.charger();
                fiches.charger();
                s.sendMessage(Txt.PREFIXE + "Rechargé : " + jeux.jeux.size() + " jeux, " + fiches.fiches.size() + " fiches.");
            }
            case "jeux" -> {
                s.sendMessage(Txt.PREFIXE + "§eÉtat des jeux :");
                for (Jeux.Jeu j : jeux.jeux.values()) {
                    Jeux.Etat e = jeux.etat(j.id);
                    String r = jeux.raison(j.id);
                    s.sendMessage("§8- §f" + j.id + " §7(" + j.nom + "§7) : " + e.nom + (r.isBlank() ? "" : " §7- " + r));
                }
            }
            case "jeu" -> {
                if (a.length < 3) { s.sendMessage(Txt.PREFIXE + "/droits jeu <jeu> <ouvert|beta|maintenance> [raison]"); return true; }
                Jeux.Jeu j = jeux.jeu(a[1]);
                Jeux.Etat e = Jeux.Etat.lire(a[2]);
                if (j == null) { s.sendMessage(Txt.PREFIXE + "§cJeu inconnu : " + a[1] + " §7(/droits jeux)"); return true; }
                if (e == null) { s.sendMessage(Txt.PREFIXE + "§cÉtat inconnu : ouvert, beta ou maintenance."); return true; }
                String raison = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : "";
                jeux.changer(s, j, e, raison);
                s.sendMessage(Txt.PREFIXE + j.nom + " §7: " + e.nom);
            }
            case "joueur" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Commande en jeu seulement."); return true; }
                if (a.length < 2) { menus.joueurs(p, 0); return true; }
                trouver(s, a[1], (id, nom) -> menus.joueur(p, id, nom));
            }
            case "beta" -> {
                if (a.length < 2) { s.sendMessage(Txt.PREFIXE + "/droits beta <pseudo> [on|off]"); return true; }
                Boolean on = a.length >= 3 ? onOff(a[2]) : null;
                trouver(s, a[1], (id, nom) -> lp.getUserManager().modifyUser(id, u -> {
                    boolean avait = Menus.etatDroit(u, List.of(Menus.BETA)) == 1;
                    boolean veut = on == null ? !avait : on;
                    Menus.effacer(u, Menus.BETA);
                    if (veut) u.data().add(PermissionNode.builder(Menus.BETA).value(true).build());
                    Bukkit.getScheduler().runTask(this, () -> {
                        s.sendMessage(Txt.PREFIXE + "Tag Bêta de §f" + nom + " §7: " + (veut ? "§doui" : "§7non"));
                        getLogger().info(s.getName() + " : tag beta de " + nom + (veut ? " donne" : " retire"));
                    });
                }));
            }
            case "monde" -> {
                if (a.length < 3) { s.sendMessage(Txt.PREFIXE + "/droits monde <pseudo> <monde> [on|off]"); return true; }
                String monde = Jeux.base(a[2]);
                if (garde.estProtege(monde)) { s.sendMessage(Txt.PREFIXE + "§c" + monde + " est protégé."); return true; }
                Boolean on = a.length >= 4 ? onOff(a[3]) : null;
                trouver(s, a[1], (id, nom) -> {
                    boolean deja = fiches.fiche(id, nom).mondes.contains(monde);
                    boolean interdit = on == null || on != deja ? fiches.basculerMonde(id, nom, monde) : deja;
                    s.sendMessage(Txt.PREFIXE + "§f" + monde + " §7: " + (interdit ? "§cinterdit" : "§aautorisé") + " §7pour " + nom + ".");
                    Player c = Bukkit.getPlayer(id);
                    if (c != null) garde.verifier(c);
                });
            }
            case "interdire" -> {
                if (a.length < 3) { s.sendMessage(Txt.PREFIXE + "/droits interdire <pseudo> <jeu> [on|off]"); return true; }
                Jeux.Jeu j = jeux.jeu(a[2]);
                if (j == null) { s.sendMessage(Txt.PREFIXE + "§cJeu inconnu : " + a[2]); return true; }
                Boolean on = a.length >= 4 ? onOff(a[3]) : null;
                trouver(s, a[1], (id, nom) -> {
                    boolean deja = fiches.fiche(id, nom).jeux.contains(j.id);
                    boolean interdit = on == null || on != deja ? fiches.basculerJeu(id, nom, j.id) : deja;
                    s.sendMessage(Txt.PREFIXE + j.nom + " §7: " + (interdit ? "§cinterdit" : "§aautorisé") + " §7pour " + nom + ".");
                });
            }
            default -> s.sendMessage(AIDE);
        }
        return true;
    }

    private static Boolean onOff(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "on", "oui", "true" -> true;
            case "off", "non", "false" -> false;
            default -> null;
        };
    }

    /** Pseudo -> UUID : joueur en ligne, sinon LuckPerms (joueur déjà venu). */
    private void trouver(CommandSender s, String pseudo, java.util.function.BiConsumer<UUID, String> suite) {
        Player p = Bukkit.getPlayerExact(pseudo);
        if (p != null) { suite.accept(p.getUniqueId(), p.getName()); return; }
        lp.getUserManager().lookupUniqueId(pseudo).thenAccept(id -> Bukkit.getScheduler().runTask(this, () -> {
            if (id == null) { s.sendMessage(Txt.PREFIXE + "§cJoueur inconnu : " + pseudo); return; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            suite.accept(id, op.getName() != null ? op.getName() : pseudo);
        }));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (!s.hasPermission("eemidroits.admin")) return List.of();
        List<String> r = new ArrayList<>();
        if (a.length == 1) r.addAll(List.of("joueur", "beta", "jeu", "jeux", "monde", "interdire", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("jeu")) r.addAll(jeux.jeux.keySet());
        else if (a.length == 2) for (Player p : Bukkit.getOnlinePlayers()) r.add(p.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("jeu")) r.addAll(List.of("ouvert", "beta", "maintenance"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("interdire")) r.addAll(jeux.jeux.keySet());
        else if (a.length == 3 && a[0].equalsIgnoreCase("monde")) for (World w : Bukkit.getWorlds()) r.add(w.getName());
        else if (a.length == 3 && a[0].equalsIgnoreCase("beta") || a.length == 4 && (a[0].equalsIgnoreCase("monde") || a[0].equalsIgnoreCase("interdire")))
            r.addAll(List.of("on", "off"));
        String debut = a[a.length - 1].toLowerCase(Locale.ROOT);
        r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(debut));
        return r;
    }
}
