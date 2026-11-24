package fr.eemicraft.resto;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Progression d'un joueur : plugins/EemiResto/players/&lt;uuid&gt;.yml, ecrite de facon atomique. */
final class PData {
    final UUID uuid;
    String name = "?";
    int parcel = -1;
    double money, pending, earned;
    int rebirths;
    final Set<String> bought = new LinkedHashSet<>();
    final Set<String> secrets = new LinkedHashSet<>();
    final Set<String> dishes = new LinkedHashSet<>();
    final List<Integer> milestones = new ArrayList<>();
    boolean magic, bkButton;
    long sold, lastSeen, presence, rebirthAt;
    boolean broken, dirty;

    PData(UUID uuid) {
        this.uuid = uuid;
    }

    static File file(File dir, UUID id) {
        return new File(dir, "players" + File.separator + id + ".yml");
    }

    static PData load(File dir, UUID id, Logger log) {
        PData d = new PData(id);
        File f = file(dir, id);
        if (!f.exists()) return d;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            d.broken = true;
            try {
                Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // la copie est un bonus
            }
            log.severe("Fichier illisible " + f.getName() + " (copie .bak) : il ne sera pas reecrit. " + e.getMessage());
            return d;
        }
        d.name = y.getString("name", "?");
        d.parcel = y.getInt("parcel", -1);
        d.money = Math.max(0, y.getDouble("money"));
        d.pending = Math.max(0, y.getDouble("pending"));
        d.earned = Math.max(0, y.getDouble("earned"));
        d.rebirths = Math.max(0, y.getInt("rebirths"));
        d.bought.addAll(y.getStringList("bought"));
        d.secrets.addAll(y.getStringList("secrets"));
        d.dishes.addAll(y.getStringList("dishes"));
        d.milestones.addAll(y.getIntegerList("milestones"));
        d.magic = y.getBoolean("magic");
        d.bkButton = y.getBoolean("bk-button");
        d.sold = y.getLong("sold");
        d.lastSeen = y.getLong("last-seen");
        d.presence = y.getLong("presence");
        d.rebirthAt = y.getLong("rebirth-at");
        // achats inconnus (version future/ancienne) ignores
        d.bought.retainAll(Defs.INDEX.keySet());
        return d;
    }

    /** Ecriture atomique (fichier .tmp puis remplacement) ; jamais sur un fichier illisible. */
    void save(File dir, Logger log) {
        if (broken) return;
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("parcel", parcel);
        y.set("money", money);
        y.set("pending", pending);
        y.set("earned", earned);
        y.set("rebirths", rebirths);
        y.set("bought", new ArrayList<>(bought));
        y.set("secrets", new ArrayList<>(secrets));
        y.set("dishes", new ArrayList<>(dishes));
        y.set("milestones", milestones);
        y.set("magic", magic);
        y.set("bk-button", bkButton);
        y.set("sold", sold);
        y.set("last-seen", lastSeen);
        y.set("presence", presence);
        y.set("rebirth-at", rebirthAt);
        File f = file(dir, uuid);
        File tmp = new File(f.getPath() + ".tmp");
        try {
            f.getParentFile().mkdirs();
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException e) {
            log.warning("Sauvegarde de " + name + " : " + e.getMessage());
        }
    }

    /** Remet le restaurant a zero (renaissance ou reset) : garde nom, secrets, collection, stats a vie. */
    void resetRun() {
        bought.clear();
        for (Defs.Purchase p : Defs.PURCHASES) if (p.auto()) bought.add(p.id());
        money = 0;
        pending = 0;
    }
}
