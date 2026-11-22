package fr.fallen.lgplus;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Répare le texte « double encodé » (UTF-8 relu en Windows-1252, parfois plusieurs fois) :
 * le jar LoupGarou 1.0.0 (Modrinth) contient des chaînes comme « Ã‚Â§9ÃƒÆ’... » au lieu de « §9🌙 ».
 * Chaque suite de caractères non ASCII est retraduite en octets (Windows-1252, ou Latin-1 pour les
 * 5 octets non définis comme 0x8F) puis relue en UTF-8 strict, tant que ça réussit. Un texte sain
 * (accents normaux, §, emoji) échoue au décodage strict et reste tel quel.
 */
final class Mojibake {
    private static final Map<Character, Byte> CP1252 = new HashMap<>();

    static {
        String high = "€\u0000‚ƒ„…†‡ˆ‰Š‹Œ\u0000Ž\u0000"
                + "\u0000‘’“”•–—˜™š›œ\u0000žŸ";
        for (int i = 0; i < high.length(); i++) {
            char c = high.charAt(i);
            if (c != 0) CP1252.put(c, (byte) (0x80 + i));
        }
    }

    private Mojibake() {
    }

    private static boolean suspicious(char c) {
        return c >= 0x80 && (c <= 0xFF || CP1252.containsKey(c));
    }

    /** Vrai si le texte contient au moins une séquence typique (Ã, Â, â, ð suivi d'un caractère 0x80-0xBF ou cp1252). */
    static boolean looksBroken(String s) {
        for (int i = 0; i + 1 < s.length(); i++) {
            char a = s.charAt(i), b = s.charAt(i + 1);
            if (a >= 0xC0 && a <= 0xFF && ((b >= 0x80 && b <= 0xBF) || CP1252.containsKey(b))) return true;
        }
        return false;
    }

    static String repair(String s) {
        if (s == null || !looksBroken(s)) return s;
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (!suspicious(c)) {
                out.append(c);
                i++;
                continue;
            }
            int j = i;
            while (j < s.length() && suspicious(s.charAt(j))) j++;
            out.append(repairRun(s.substring(i, j)));
            i = j;
        }
        return out.toString();
    }

    private static String repairRun(String run) {
        String cur = run;
        for (int pass = 0; pass < 8; pass++) {
            String next = decodeOnce(cur);
            if (next == null || next.equals(cur)) break;
            cur = next;
            if (!looksBroken(cur) && cur.chars().noneMatch(ch -> ch >= 0x80 && ch <= 0x9F)) break;
        }
        return cur;
    }

    private static String decodeOnce(String run) {
        byte[] bytes = new byte[run.length()];
        for (int k = 0; k < run.length(); k++) {
            char c = run.charAt(k);
            if (c < 0x80) {
                bytes[k] = (byte) c;
            } else {
                Byte b = CP1252.get(c);
                if (b != null) bytes[k] = b;
                else if (c <= 0xFF) bytes[k] = (byte) c;
                else return null;
            }
        }
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer cb = dec.decode(ByteBuffer.wrap(bytes));
            return cb.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
