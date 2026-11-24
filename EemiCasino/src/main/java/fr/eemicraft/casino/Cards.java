package fr.eemicraft.casino;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Cartes : entier 0..51, rang = c % 13 (0 = 2 ... 12 = As), couleur = c / 13 (pique, coeur, carreau, trefle).
 * Evaluation des mains de poker : score = categorie << 20 | 5 rangs de departage (4 bits chacun).
 */
final class Cards {
    static final String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "V", "D", "R", "A"};
    static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    static final String[] SUIT_NAMES = {"Pique", "Coeur", "Carreau", "Trefle"};
    static final String[] CATEGORIES = {"Carte haute", "Paire", "Double paire", "Brelan", "Quinte", "Couleur", "Full", "Carre", "Quinte flush"};

    private Cards() {
    }

    static int rank(int c) {
        return c % 13;
    }

    static int suit(int c) {
        return c / 13;
    }

    static boolean red(int c) {
        return suit(c) == 1 || suit(c) == 2;
    }

    /** Nom colore pour le chat et les menus. */
    static String name(int c) {
        return (red(c) ? "&c" : "&f") + RANKS[rank(c)] + SUITS[suit(c)];
    }

    static String names(List<Integer> cs) {
        StringBuilder b = new StringBuilder();
        for (int c : cs) {
            if (b.length() > 0) {
                b.append("&7 ");
            }
            b.append(name(c));
        }
        return b.toString();
    }

    /** Carte en objet : papier, quantite = chiffre de la carte (2 a 10), 1 pour les figures et l'as (lettre dans le nom). */
    static ItemStack item(int c, List<String> lore) {
        int r = rank(c);
        return item(c, lore, r <= 8 ? r + 2 : 1);
    }

    static ItemStack item(int c, List<String> lore, int amount) {
        int r = rank(c);
        List<String> l = new ArrayList<>();
        l.add("&7" + RANKS[r] + " de " + SUIT_NAMES[suit(c)]);
        if (lore != null) {
            l.addAll(lore);
        }
        ItemStack it = U.item(Material.PAPER, amount, (red(c) ? "&c&l" : "&f&l") + RANKS[r] + " " + SUITS[suit(c)], l, false);
        return Icons.on ? Models.apply(it, Models.card(c)) : it;
    }

    static ItemStack back() {
        ItemStack it = U.item(Material.MAP, "&7Carte cachee", List.of(), false);
        return Icons.on ? Models.apply(it, "carte/dos") : it;
    }

    /** Vraies cartes dessinees du pack dans les menus (modeles-3d). */
    static volatile boolean models;

    static Deque<Integer> shoe(int decks, Random rnd) {
        List<Integer> l = new ArrayList<>(52 * decks);
        for (int d = 0; d < decks; d++) {
            for (int i = 0; i < 52; i++) {
                l.add(i);
            }
        }
        Collections.shuffle(l, rnd);
        return new ArrayDeque<>(l);
    }

    // ---------- evaluation poker ----------

    static int category(int score) {
        return score >>> 20;
    }

    static int eval5(int[] c) {
        int[] cnt = new int[13];
        int mask = 0;
        boolean flush = true;
        for (int x : c) {
            cnt[rank(x)]++;
            mask |= 1 << rank(x);
            if (suit(x) != suit(c[0])) {
                flush = false;
            }
        }
        int straight = -1;
        if (Integer.bitCount(mask) == 5) {
            int hi = 31 - Integer.numberOfLeadingZeros(mask);
            int lo = Integer.numberOfTrailingZeros(mask);
            if (hi - lo == 4) {
                straight = hi;
            } else if (mask == 0b1000000001111) {
                straight = 3; // A-2-3-4-5
            }
        }
        if (straight >= 0 && flush) {
            return 8 << 20 | straight;
        }
        // rangs tries par nombre puis par valeur
        List<int[]> groups = new ArrayList<>();
        for (int r = 12; r >= 0; r--) {
            if (cnt[r] > 0) {
                groups.add(new int[]{cnt[r], r});
            }
        }
        groups.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]);
        int kick = 0;
        for (int[] g : groups) {
            for (int i = 0; i < g[0]; i++) {
                kick = kick << 4 | g[1];
            }
        }
        int c1 = groups.get(0)[0];
        int c2 = groups.size() > 1 ? groups.get(1)[0] : 0;
        int cat;
        if (c1 == 4) {
            cat = 7;
        } else if (c1 == 3 && c2 == 2) {
            cat = 6;
        } else if (flush) {
            cat = 5;
        } else if (straight >= 0) {
            return 4 << 20 | straight;
        } else if (c1 == 3) {
            cat = 3;
        } else if (c1 == 2 && c2 == 2) {
            cat = 2;
        } else if (c1 == 2) {
            cat = 1;
        } else {
            cat = 0;
        }
        return cat << 20 | kick;
    }

    /** Meilleure main de 5 parmi 5 a 7 cartes. */
    static int best(List<Integer> cards) {
        int n = cards.size();
        int best = -1;
        int[] h = new int[5];
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                for (int c = b + 1; c < n; c++) {
                    for (int d = c + 1; d < n; d++) {
                        for (int e = d + 1; e < n; e++) {
                            h[0] = cards.get(a);
                            h[1] = cards.get(b);
                            h[2] = cards.get(c);
                            h[3] = cards.get(d);
                            h[4] = cards.get(e);
                            best = Math.max(best, eval5(h));
                        }
                    }
                }
            }
        }
        return best;
    }

    static String handName(int score) {
        int cat = category(score);
        if (cat == 8 && (score & 0xF) == 12) {
            return "Quinte flush royale";
        }
        return CATEGORIES[cat];
    }
}
