package io.github.falphir.hub.service;

import java.util.HashMap;
import java.util.Map;

/**
 * How wide a line is in Minecraft's default font, to centre or right-align a MOTD line. Minecraft has no
 * alignment, so it's done with leading spaces, and how many depends on the line's width once a server's
 * pack and version are in it.
 * <p>
 * ponytail: close, not pixel-exact. The widths are the default font's ASCII glyphs; anything else
 * (✦ and friends) is guessed at 6px, and a resource pack or "Force Unicode Font" changes them all.
 */
final class MinecraftFont {

    public enum Alignment { LEFT, CENTER, RIGHT }

    /** The server list's MOTD area, in the font's pixels. Also approximate. */
    static final int MOTD_WIDTH = 270;

    private static final int SPACE = 4;
    private static final int DEFAULT = 6;

    /** A glyph's advance: its pixels plus the 1px gap after it. Everything not listed is {@link #DEFAULT}. */
    private static final Map<Character, Integer> ADVANCE = new HashMap<>();

    static {
        put(2, "!,.:;i|'");
        put(3, "`l");
        put(4, " I[]t");
        put(5, "\"()*<>fk{}");
        put(7, "@");
    }

    private static void put(int advance, String chars) {
        for (char c : chars.toCharArray()) ADVANCE.put(c, advance);
    }

    private MinecraftFont() {}

    /** The width of a line, codes (§ or &, as the mod reads both) taking none. Bold adds a pixel per character. */
    static int width(String line) {
        int width = 0;
        boolean bold = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < line.length() && isCode(line.charAt(i + 1))) {
                char code = Character.toLowerCase(line.charAt(++i));
                if (code == 'l') bold = true;
                else if (code == 'r' || Character.digit(code, 16) >= 0) bold = false; // a colour ends bold too
                continue;
            }
            width += ADVANCE.getOrDefault(c, DEFAULT) + (bold ? 1 : 0);
        }
        return width;
    }

    /** The line with enough leading spaces to sit where {@code alignment} says in the server list. */
    static String pad(String line, Alignment alignment) {
        int room = MOTD_WIDTH - width(line);
        int spaces = switch (alignment) {
            case LEFT -> 0;
            case CENTER -> Math.round(room / 2f / SPACE);
            case RIGHT -> room / SPACE;
        };
        return " ".repeat(Math.max(0, spaces)) + line;
    }

    /**
     * Reads a MOTD's lines in order, keeping track of whether underline or strikethrough is on: it carries
     * over a line break, so a line's first spaces can be a stripe that started on the line before.
     */
    static final class Lines {
        private boolean underline;
        private boolean strike;

        /**
         * The line without the spaces at its start and end that show nothing: plain ones, typed to line it
         * up by hand. A space with underline or strikethrough on is a line you can see, and stays. Codes stay.
         */
        String trimBlankSpaces(String line) {
            // first pass: which characters are visible, with the style as it stands at each one
            boolean[] visible = new boolean[line.length()];
            boolean[] isCode = new boolean[line.length()];
            int first = -1;
            int last = -1;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if ((c == '§' || c == '&') && i + 1 < line.length() && isCode(line.charAt(i + 1))) {
                    char code = Character.toLowerCase(line.charAt(i + 1));
                    if (code == 'n') underline = true;
                    else if (code == 'm') strike = true;
                    else if (code == 'r' || Character.digit(code, 16) >= 0) underline = strike = false; // a colour clears them too
                    isCode[i] = isCode[i + 1] = true;
                    i++;
                    continue;
                }
                visible[i] = c != ' ' || underline || strike;
                if (visible[i]) {
                    if (first < 0) first = i;
                    last = i;
                }
            }
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                if (isCode[i] || (first >= 0 && i >= first && i <= last)) out.append(line.charAt(i));
            }
            return out.toString();
        }
    }

    private static boolean isCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }
}
