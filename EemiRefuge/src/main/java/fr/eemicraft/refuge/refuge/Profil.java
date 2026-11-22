package fr.eemicraft.refuge.refuge;

import fr.eemicraft.refuge.api.Profile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Profil persistant d'un joueur : plugins/EemiRefuge/joueurs/&lt;uuid&gt;.yml. */
public final class Profil implements Profile {

    public static final int RESERVE = 27;

    private final UUID id;
    private final File file;
    private final Logger log;
    String name = "";
    private long ferraille;
    private int act = 1;
    private int xp;
    private final List<ItemStack> insured = new ArrayList<>();
    /** Prix payé pour chaque objet assuré (même ordre que insured), remboursé si on annule. */
    final List<Long> insuredPrices = new ArrayList<>();
    /** Contenu de la réserve (copie de travail ; la réserve ouverte est recopiée ici à la fermeture). */
    final ItemStack[] reserve = new ItemStack[RESERVE];
    boolean reserveOpen;
    /** Kit de départ déjà reçu ; dernier kit de secours (ms). */
    boolean kitRecu;
    long kitSecoursAt;
    private boolean dirty;

    Profil(UUID id, File file, Logger log) {
        this.id = id;
        this.file = file;
        this.log = log;
    }

    public UUID id() { return id; }

    static Profil load(UUID id, File dir, Logger log) {
        File f = new File(dir, id + ".yml");
        Profil p = new Profil(id, f, log);
        if (!f.isFile()) return p;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        p.name = y.getString("nom", "");
        p.ferraille = Math.max(0, y.getLong("ferraille", 0));
        p.act = Math.max(1, y.getInt("acte", 1));
        p.xp = Math.max(0, y.getInt("xp", 0));
        p.kitRecu = y.getBoolean("kit.recu", false);
        p.kitSecoursAt = y.getLong("kit.secours", 0L);
        ItemStack[] res = decode(y.getString("reserve", ""), log, id);
        ItemStack[] ins = decode(y.getString("assurance.objets", ""), log, id);
        if (res == null || ins == null) {
            // On garde une copie du fichier d'origine avant qu'une sauvegarde ne l'écrase.
            File bak = new File(dir, id + ".yml.illisible-" + System.currentTimeMillis());
            try {
                java.nio.file.Files.copy(f.toPath(), bak.toPath());
            } catch (IOException ex) {
                log.severe("Copie de " + f + " impossible : " + ex);
            }
            log.severe("Profil " + id + " : copie gardee dans " + bak.getName() + ".");
            if (res == null) res = new ItemStack[0];
            if (ins == null) ins = new ItemStack[0];
        }
        for (int i = 0; i < Math.min(RESERVE, res.length); i++) p.reserve[i] = empty(res[i]) ? null : res[i];
        List<Long> prices = y.getLongList("assurance.prix");
        for (int i = 0; i < ins.length; i++) {
            if (empty(ins[i])) continue;
            p.insured.add(ins[i]);
            p.insuredPrices.add(i < prices.size() ? prices.get(i) : 0L);
        }
        return p;
    }

    private static boolean empty(ItemStack it) {
        return it == null || it.isEmpty();
    }

    private static ItemStack[] decode(String s, Logger log, UUID id) {
        if (s == null || s.isBlank()) return new ItemStack[0];
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s));
        } catch (RuntimeException ex) {
            log.severe("Profil " + id + " : objets illisibles (" + ex + ").");
            return null;
        }
    }

    private static String encode(ItemStack[] items) {
        ItemStack[] c = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) c[i] = empty(items[i]) ? ItemStack.empty() : items[i];
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(c));
    }

    @Override public long ferraille() { return ferraille; }

    @Override
    public void addFerraille(long n) {
        ferraille = Math.max(0, ferraille + n);
        dirty = true;
    }

    void setFerraille(long n) {
        ferraille = Math.max(0, n);
        dirty = true;
    }

    @Override public int act() { return act; }

    @Override
    public void setAct(int a) {
        act = Math.max(1, a);
        dirty = true;
    }

    /** Niveau L atteint quand xp ≥ 50 × L × (L - 1) : 100, 300, 600, 1000… */
    @Override
    public int level() {
        int l = 1;
        while (xp >= needFor(l + 1)) l++;
        return l;
    }

    public static int needFor(int level) {
        return 50 * level * (level - 1);
    }

    public int xp() { return xp; }

    @Override
    public void addXp(int n) {
        xp = Math.max(0, xp + n);
        dirty = true;
    }

    @Override
    public List<ItemStack> insured() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack it : insured) out.add(it.clone());
        return out;
    }

    @Override
    public void setInsured(List<ItemStack> items) {
        insured.clear();
        insuredPrices.clear();
        if (items != null)
            for (ItemStack it : items)
                if (!empty(it)) {
                    insured.add(it.clone());
                    insuredPrices.add(0L);
                }
        dirty = true;
    }

    List<ItemStack> insuredRaw() { return insured; }

    void addInsured(ItemStack copy, long price) {
        insured.add(copy.clone());
        insuredPrices.add(price);
        dirty = true;
    }

    long removeInsured(int i) {
        if (i < 0 || i >= insured.size()) return 0;
        insured.remove(i);
        dirty = true;
        return insuredPrices.remove(i);
    }

    void markDirty() { dirty = true; }

    boolean dirty() { return dirty; }

    @Override
    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nom", name);
        y.set("ferraille", ferraille);
        y.set("acte", act);
        y.set("xp", xp);
        y.set("kit.recu", kitRecu);
        y.set("kit.secours", kitSecoursAt);
        y.set("reserve", encode(reserve));
        y.set("assurance.objets", encode(insured.toArray(new ItemStack[0])));
        y.set("assurance.prix", new ArrayList<>(insuredPrices));
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            y.save(tmp);
            if (file.exists() && !file.delete()) log.warning("Profil " + id + " : ancien fichier non supprime.");
            if (!tmp.renameTo(file)) log.severe("Profil " + id + " : renommage impossible (" + tmp + ").");
            dirty = false;
        } catch (IOException ex) {
            log.severe("Profil " + id + " : sauvegarde impossible : " + ex);
        }
    }

    @Override
    public String toString() {
        return "Profil " + name + " " + Arrays.toString(new long[]{ferraille, act, xp});
    }
}
