package fr.eemicraft.jump;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Meilleurs temps par niveau (carte #117) : un fichier texte par parcours et par mode dans plugins/EemiJump/levels/,
 * une ligne par joueur (« uuid TAB pseudo TAB niveau=ms,niveau=ms,... », seulement les niveaux joues).
 * Tout est en memoire ; les fichiers modifies sont reecrits (fichier .tmp puis deplacement atomique) toutes les
 * 30 s hors du fil principal, et a l'arret / au reload.
 */
final class LevelTimes {

    /** Classement d'un parcours dans un mode (cle = id du parcours, ou id#nofall). */
    static final class Board {
        final String key;
        final int levels;
        /** uuid -> meilleur temps par niveau en ms (indice = niveau, 0 = pas de temps). */
        final Map<UUID, int[]> times = new LinkedHashMap<>();
        final Map<UUID, String> names = new HashMap<>();
        /** Record de chaque niveau et son detenteur. */
        final int[] record;
        final UUID[] holder;
        boolean dirty;
        long seq;

        Board(String key, int levels) {
            this.key = key;
            this.levels = levels;
            this.record = new int[levels + 1];
            this.holder = new UUID[levels + 1];
        }

        int mine(UUID id, int level) {
            int[] t = times.get(id);
            return t == null || level < 1 || level >= t.length ? 0 : t[level];
        }

        String name(UUID id) {
            String n = names.get(id);
            return n == null ? "?" : n;
        }

        /** Top du niveau (uuid tries par temps). */
        List<UUID> top(int level) {
            List<UUID> l = new ArrayList<>();
            for (Map.Entry<UUID, int[]> e : times.entrySet()) {
                int[] t = e.getValue();
                if (level < t.length && t[level] > 0) {
                    l.add(e.getKey());
                }
            }
            l.sort((a, b) -> Integer.compare(times.get(a)[level], times.get(b)[level]));
            return l;
        }

        void put(UUID id, int level, int ms) {
            int[] t = times.computeIfAbsent(id, k -> new int[levels + 1]);
            t[level] = ms;
            if (record[level] <= 0 || ms < record[level]) {
                record[level] = ms;
                holder[level] = id;
            }
        }
    }

    /** Resultat d'un temps soumis. */
    static final class Result {
        boolean personal, levelRecord;
        int previousMine, previousRecord;
        UUID previousHolder;
    }

    private final File dir;
    private final Logger log;
    private final Map<String, Board> boards = new HashMap<>();
    private final Object ioLock = new Object();
    private final Map<String, Long> written = new HashMap<>();
    /** Numero d'instantane, jamais remis a zero (un vieil instantane n'ecrase jamais un plus recent). */
    private long globalSeq;

    LevelTimes(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "levels");
        this.log = log;
    }

    static String fileName(String key) {
        return key.replace("#nofall", "-sanschute").replaceAll("[^a-zA-Z0-9_-]", "_") + ".txt";
    }

    /** Classement d'un parcours (charge a la premiere demande). */
    Board board(String key, int levels) {
        Board b = boards.get(key);
        if (b != null && b.levels >= levels) {
            return b;
        }
        Board nb = new Board(key, Math.max(1, levels));
        if (b != null) {
            // parcours agrandi depuis le chargement : on recopie
            for (Map.Entry<UUID, int[]> e : b.times.entrySet()) {
                int[] t = e.getValue();
                for (int i = 1; i < t.length && i <= nb.levels; i++) {
                    if (t[i] > 0) {
                        nb.put(e.getKey(), i, t[i]);
                    }
                }
            }
            nb.names.putAll(b.names);
            nb.dirty = b.dirty;
            nb.seq = b.seq;
        } else {
            load(nb);
        }
        boards.put(key, nb);
        return nb;
    }

    private void load(Board b) {
        File f = new File(dir, fileName(b.key));
        if (!f.isFile()) {
            return;
        }
        int bad = 0;
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) {
                    bad++;
                    continue;
                }
                UUID id;
                try {
                    id = UUID.fromString(parts[0]);
                } catch (IllegalArgumentException e) {
                    bad++;
                    continue;
                }
                b.names.put(id, parts[1]);
                for (String kv : parts[2].split(",")) {
                    int eq = kv.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    try {
                        int lvl = Integer.parseInt(kv.substring(0, eq));
                        int ms = Integer.parseInt(kv.substring(eq + 1));
                        if (lvl >= 1 && lvl <= b.levels && ms > 0) {
                            b.put(id, lvl, ms);
                        }
                    } catch (NumberFormatException e) {
                        bad++;
                    }
                }
            }
        } catch (IOException e) {
            log.severe("Temps par niveau illisibles (" + f.getName() + ") : " + e.getMessage());
            return;
        }
        if (bad > 0) {
            log.warning("Temps par niveau " + f.getName() + " : " + bad + " entree(s) illisible(s) ignoree(s).");
        }
    }

    /** Soumet un temps ; garde le meilleur du joueur. */
    Result submit(String key, int levels, UUID id, String name, int level, int ms) {
        Board b = board(key, levels);
        Result r = new Result();
        if (level < 1 || level > b.levels || ms <= 0) {
            return r;
        }
        r.previousMine = b.mine(id, level);
        r.previousRecord = b.record[level];
        r.previousHolder = b.holder[level];
        if (!name.equals(b.names.get(id))) {
            b.names.put(id, name);
            b.dirty = true;
        }
        if (r.previousMine > 0 && ms >= r.previousMine) {
            return r;
        }
        r.personal = true;
        r.levelRecord = r.previousRecord <= 0 || ms < r.previousRecord;
        b.put(id, level, ms);
        b.dirty = true;
        return r;
    }

    /** Instantane des fichiers a reecrire (fil principal). */
    List<Object[]> snapshot() {
        List<Object[]> out = new ArrayList<>();
        for (Board b : boards.values()) {
            if (!b.dirty) {
                continue;
            }
            b.dirty = false;
            b.seq = ++globalSeq;
            StringBuilder sb = new StringBuilder(64 + b.times.size() * 64);
            sb.append("# EemiJump - meilleurs temps par niveau (ms), ").append(b.key).append('\n');
            sb.append("# uuid<TAB>pseudo<TAB>niveau=ms,niveau=ms,...\n");
            for (Map.Entry<UUID, int[]> e : b.times.entrySet()) {
                int[] t = e.getValue();
                StringBuilder lv = new StringBuilder();
                for (int i = 1; i < t.length; i++) {
                    if (t[i] > 0) {
                        if (lv.length() > 0) {
                            lv.append(',');
                        }
                        lv.append(i).append('=').append(t[i]);
                    }
                }
                if (lv.length() == 0) {
                    continue;
                }
                sb.append(e.getKey()).append('\t').append(b.name(e.getKey()).replace('\t', ' ')).append('\t').append(lv).append('\n');
            }
            out.add(new Object[] {b.key, b.seq, sb.toString()});
        }
        return out;
    }

    /** Ecrit un instantane (n'importe quel fil) ; un instantane plus ancien que le dernier ecrit est ignore. */
    void write(List<Object[]> snap) {
        synchronized (ioLock) {
            for (Object[] o : snap) {
                String key = (String) o[0];
                long seq = (Long) o[1];
                if (written.getOrDefault(key, 0L) >= seq) {
                    continue;
                }
                File f = new File(dir, fileName(key));
                File tmp = new File(f.getPath() + ".tmp");
                try {
                    dir.mkdirs();
                    Files.writeString(tmp.toPath(), (String) o[2], StandardCharsets.UTF_8);
                    try {
                        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    written.put(key, seq);
                } catch (IOException e) {
                    log.warning("Sauvegarde des temps par niveau " + f.getName() + " : " + e.getMessage());
                }
            }
        }
    }

    /** Oublie tout (reload) : a appeler apres un write synchrone. */
    void clear() {
        boards.clear();
    }
}
