package fr.eemicraft.casino;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Classement permanent des 10 plus gros gains en UN seul coup (gain net d'une partie : un tour de roulette, une main,
 * un lancer...), tous jeux confondus. Une seule entree par joueur (son meilleur coup) : plus lisible qu'un classement
 * ou un meme joueur prendrait plusieurs places. A egalite, le premier arrive reste devant.
 * La liste est immuable et remplacee a chaque changement : lisible hors du fil principal (placeholders).
 */
final class BigWins {
    static final int SIZE = 10;

    record Entry(UUID uuid, String name, long amount, String game, long time) {
    }

    /** Resultat d'une tentative d'entree : ancien rang (-1 si absent), nouveau rang (-1 si pas entre). */
    record Change(int oldRank, int newRank, boolean record) {
        boolean entered() {
            return newRank > 0;
        }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::amount).reversed().thenComparingLong(Entry::time);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private volatile List<Entry> list = List.of();
    /** Faux tant que le classement n'a jamais ete amorce depuis les "biggest" des comptes. */
    private boolean initialized;

    List<Entry> list() {
        return list;
    }

    Entry get(int rank) {
        List<Entry> l = list;
        return rank >= 1 && rank <= l.size() ? l.get(rank - 1) : null;
    }

    int rankOf(UUID id) {
        List<Entry> l = list;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).uuid().equals(id)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Propose un gain net. Ne change rien si le gain est nul/negatif ou trop petit. */
    Change offer(UUID id, String name, long net, String game, long now) {
        if (net <= 0 || id == null) {
            return new Change(-1, -1, false);
        }
        List<Entry> l = new ArrayList<>(list);
        int old = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).uuid().equals(id)) {
                old = i + 1;
                break;
            }
        }
        if (old > 0) {
            if (net <= l.get(old - 1).amount()) {
                return new Change(old, -1, false);
            }
            l.remove(old - 1);
        } else if (l.size() >= SIZE && net <= l.get(l.size() - 1).amount()) {
            return new Change(-1, -1, false);
        }
        Entry prevFirst = l.isEmpty() ? null : l.get(0);
        l.add(new Entry(id, name == null ? "?" : name, net, game == null ? "?" : game, now));
        l.sort(ORDER);
        while (l.size() > SIZE) {
            l.remove(l.size() - 1);
        }
        list = List.copyOf(l);
        int now1 = rankOf(id);
        // record n°1 : premier du classement et (classement vide avant, ou ancien n°1 battu strictement)
        boolean record = now1 == 1 && (prevFirst == null || net > prevFirst.amount());
        return new Change(old, now1, record);
    }

    /** Retire l'entree d'un joueur (par pseudo, sans tenir compte de la casse). Renvoie l'entree retiree ou null. */
    Entry remove(String name) {
        List<Entry> l = new ArrayList<>(list);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).name().equalsIgnoreCase(name)) {
                Entry e = l.remove(i);
                list = List.copyOf(l);
                return e;
            }
        }
        return null;
    }

    void reset() {
        list = List.of();
        initialized = true;
    }

    /** Met a jour le pseudo affiche d'un joueur qui a change de nom. */
    void rename(UUID id, String name) {
        if (name == null) {
            return;
        }
        List<Entry> l = list;
        for (int i = 0; i < l.size(); i++) {
            Entry e = l.get(i);
            if (e.uuid().equals(id) && !e.name().equals(name)) {
                List<Entry> c = new ArrayList<>(l);
                c.set(i, new Entry(e.uuid(), name, e.amount(), e.game(), e.time()));
                list = List.copyOf(c);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ sauvegarde

    void load(YamlConfiguration y) {
        initialized = y.getBoolean("big-top-init", false);
        List<Entry> l = new ArrayList<>();
        List<java.util.Map<?, ?>> raw = y.getMapList("big-top");
        for (java.util.Map<?, ?> m : raw) {
            try {
                UUID id = UUID.fromString(String.valueOf(m.get("uuid")));
                long amount = m.get("amount") instanceof Number n ? n.longValue() : 0;
                if (amount <= 0) {
                    continue;
                }
                long time = m.get("time") instanceof Number n ? n.longValue() : 0;
                Object name = m.get("name");
                Object game = m.get("game");
                if (l.stream().anyMatch(e -> e.uuid().equals(id))) {
                    continue;
                }
                l.add(new Entry(id, name == null ? "?" : String.valueOf(name), amount, game == null ? "?" : String.valueOf(game), time));
            } catch (IllegalArgumentException ignored) {
            }
        }
        l.sort(ORDER);
        while (l.size() > SIZE) {
            l.remove(l.size() - 1);
        }
        list = List.copyOf(l);
        if (!l.isEmpty()) {
            initialized = true;
        }
    }

    /** Premier chargement de la 1.4.0 : remplit avec le "biggest" de chaque compte (jeu et date inconnus). */
    int seedIfNeeded(Collection<PData> players) {
        if (initialized) {
            return 0;
        }
        initialized = true;
        List<Entry> l = new ArrayList<>();
        for (PData d : players) {
            if (d.biggest > 0 && d.name != null && !d.name.equals("?")) {
                l.add(new Entry(d.uuid, d.name, d.biggest, "?", 0));
            }
        }
        l.sort(ORDER);
        while (l.size() > SIZE) {
            l.remove(l.size() - 1);
        }
        list = List.copyOf(l);
        return l.size();
    }

    void save(YamlConfiguration y) {
        y.set("big-top-init", initialized);
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (Entry e : list) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("uuid", e.uuid().toString());
            m.put("name", e.name());
            m.put("amount", e.amount());
            m.put("game", e.game());
            m.put("time", e.time());
            out.add(m);
        }
        y.set("big-top", out);
    }

    // ------------------------------------------------------------------ affichage

    /** Nom du jeu avec accents, "?" si inconnu (entrees amorcees). */
    static String gameName(String g) {
        if (g == null) {
            return "?";
        }
        return switch (g) {
            case "machine a sous" -> "Machine à sous";
            case "blackjack" -> "Blackjack";
            case "roulette" -> "Roulette";
            case "video poker" -> "Vidéo poker";
            case "roue de la fortune" -> "Roue de la fortune";
            case "plinko" -> "Plinko";
            case "poker" -> "Texas Hold'em";
            case "?" -> "?";
            default -> g.isEmpty() ? "?" : Character.toUpperCase(g.charAt(0)) + g.substring(1);
        };
    }

    /** Date jj/mm/aaaa, "" si inconnue. */
    static String date(long time) {
        return time <= 0 ? "" : DATE.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));
    }
}
