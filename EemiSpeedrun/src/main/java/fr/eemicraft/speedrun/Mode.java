package fr.eemicraft.speedrun;

import java.util.Locale;

enum Mode {
    OBJET("objet", "Roulette d'objet"),
    LETTRE("lettre", "Roulette de lettre");

    final String id;
    final String display;

    Mode(String id, String display) {
        this.id = id;
        this.display = display;
    }

    static Mode parse(String text) {
        if (text == null) return null;
        String s = text.toLowerCase(Locale.ROOT);
        for (Mode mode : values()) {
            if (mode.id.equals(s) || (mode.id + "s").equals(s)) return mode;
        }
        if (s.equals("item") || s.equals("objets")) return OBJET;
        if (s.equals("letter") || s.equals("lettres") || s.equals("alphabet")) return LETTRE;
        return null;
    }
}
