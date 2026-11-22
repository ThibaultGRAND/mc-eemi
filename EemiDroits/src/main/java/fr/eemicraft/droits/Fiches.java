package fr.eemicraft.droits;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Mondes et jeux interdits joueur par joueur (joueurs.yml). */
final class Fiches {

    static final class Fiche {
        String nom = "";
        final TreeSet<String> mondes = new TreeSet<>();
        final TreeSet<String> jeux = new TreeSet<>();
        boolean vide() { return mondes.isEmpty() && jeux.isEmpty(); }
    }

    private final EemiDroits pl;
    private final File fichier;
    final Map<UUID, Fiche> fiches = new HashMap<>();

    Fiches(EemiDroits pl) {
        this.pl = pl;
        this.fichier = new File(pl.getDataFolder(), "joueurs.yml");
    }

    void charger() {
        fiches.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        for (String k : y.getKeys(false)) {
            try {
                Fiche f = new Fiche();
                f.nom = y.getString(k + ".nom", "");
                for (String m : y.getStringList(k + ".mondes")) f.mondes.add(m.toLowerCase(Locale.ROOT));
                for (String j : y.getStringList(k + ".jeux")) f.jeux.add(j.toLowerCase(Locale.ROOT));
                fiches.put(UUID.fromString(k), f);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Fiche> e : fiches.entrySet()) {
            Fiche f = e.getValue();
            if (f.vide()) continue;
            String k = e.getKey().toString();
            y.set(k + ".nom", f.nom);
            y.set(k + ".mondes", new ArrayList<>(f.mondes));
            y.set(k + ".jeux", new ArrayList<>(f.jeux));
        }
        try { y.save(fichier); } catch (IOException ex) { pl.getLogger().warning("joueurs.yml : " + ex.getMessage()); }
    }

    Fiche fiche(UUID id, String nom) {
        Fiche f = fiches.computeIfAbsent(id, k -> new Fiche());
        if (nom != null && !nom.isEmpty()) f.nom = nom;
        return f;
    }

    boolean mondeInterdit(UUID id, String monde) {
        Fiche f = fiches.get(id);
        if (f == null || f.mondes.isEmpty()) return false;
        return f.mondes.contains(monde.toLowerCase(Locale.ROOT)) || f.mondes.contains(Jeux.base(monde));
    }

    boolean jeuInterdit(UUID id, String jeu) {
        Fiche f = fiches.get(id);
        return f != null && f.jeux.contains(jeu);
    }

    /** true = interdit après l'appel. */
    boolean basculerMonde(UUID id, String nom, String monde) {
        Fiche f = fiche(id, nom);
        String m = monde.toLowerCase(Locale.ROOT);
        boolean interdit = f.mondes.add(m) || !f.mondes.remove(m);
        sauver();
        return interdit;
    }

    boolean basculerJeu(UUID id, String nom, String jeu) {
        Fiche f = fiche(id, nom);
        boolean interdit = f.jeux.add(jeu) || !f.jeux.remove(jeu);
        sauver();
        return interdit;
    }
}
