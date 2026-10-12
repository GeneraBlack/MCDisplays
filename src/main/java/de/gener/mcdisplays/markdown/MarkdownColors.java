package de.gener.mcdisplays.markdown;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownColors {
    public static final int TAG_NONE = -1;
    public static final int TAG_POP = -2;
    public static final int TAG_RESET = -3;

    private static final Pattern RGB_COLOR_PATTERN = Pattern.compile("^rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)$");

    private static final Map<String, Integer> COLOR_TAGS = Map.ofEntries(
        Map.entry("black", 0x000000),
        Map.entry("dark_blue", 0x0000AA),
        Map.entry("navy", 0x0000AA),
        Map.entry("dark_green", 0x00AA00),
        Map.entry("dark_aqua", 0x00AAAA),
        Map.entry("teal", 0x00AAAA),
        Map.entry("dark_red", 0xAA0000),
        Map.entry("maroon", 0xAA0000),
        Map.entry("dark_purple", 0xAA00AA),
        Map.entry("gold", 0xFFAA00),
        Map.entry("orange", 0xFFAA00),
        Map.entry("brown", 0x8B5A2B),
        Map.entry("gray", 0xAAAAAA),
        Map.entry("grey", 0xAAAAAA),
        Map.entry("silver", 0xC0C0C0),
        Map.entry("dark_gray", 0x555555),
        Map.entry("dark_grey", 0x555555),
        Map.entry("blue", 0x5555FF),
        Map.entry("light_blue", 0x55AAFF),
        Map.entry("green", 0x55FF55),
        Map.entry("lime", 0x55FF55),
        Map.entry("aqua", 0x55FFFF),
        Map.entry("cyan", 0x55FFFF),
        Map.entry("red", 0xFF5555),
        Map.entry("light_purple", 0xFF55FF),
        Map.entry("purple", 0xFF55FF),
        Map.entry("magenta", 0xFF55FF),
        Map.entry("pink", 0xFF7FBF),
        Map.entry("yellow", 0xFFFF55),
        Map.entry("white", 0xFFFFFF)
    );

    private MarkdownColors() {
    }

    public static int classifyColorTag(String rawTag) {
        String normalizedTag = rawTag.trim().toLowerCase(Locale.ROOT);
        if (normalizedTag.isEmpty()) {
            return TAG_NONE;
        }

        if (normalizedTag.equals("/") || normalizedTag.equals("/color") || normalizedTag.equals("/colour")) {
            return TAG_POP;
        }

        if (normalizedTag.equals("reset") || normalizedTag.equals("default")) {
            return TAG_RESET;
        }

        Integer color = resolveColor(normalizedTag);
        return color != null ? color : TAG_NONE;
    }

    public static Integer resolveColor(String tag) {
        String normalizedTag = tag;
        if (normalizedTag.startsWith("color=")) {
            normalizedTag = normalizedTag.substring("color=".length()).trim();
        } else if (normalizedTag.startsWith("colour=")) {
            normalizedTag = normalizedTag.substring("colour=".length()).trim();
        }

        Integer namedColor = COLOR_TAGS.get(normalizedTag);
        if (namedColor != null) {
            return namedColor;
        }

        if (normalizedTag.startsWith("#")) {
            return parseHexColor(normalizedTag.substring(1));
        }
        if (normalizedTag.startsWith("0x")) {
            return parseHexColor(normalizedTag.substring(2));
        }
        if (normalizedTag.length() == 6 || normalizedTag.length() == 3) {
            Integer hexColor = parseHexColor(normalizedTag);
            if (hexColor != null) {
                return hexColor;
            }
        }

        Matcher rgbMatcher = RGB_COLOR_PATTERN.matcher(normalizedTag);
        if (rgbMatcher.matches()) {
            int red = Integer.parseInt(rgbMatcher.group(1));
            int green = Integer.parseInt(rgbMatcher.group(2));
            int blue = Integer.parseInt(rgbMatcher.group(3));
            if (red <= 255 && green <= 255 && blue <= 255) {
                return (red << 16) | (green << 8) | blue;
            }
        }

        return null;
    }

    private static Integer parseHexColor(String value) {
        String normalizedValue = value.trim();
        if (normalizedValue.length() == 3) {
            normalizedValue = "" + normalizedValue.charAt(0) + normalizedValue.charAt(0)
                + normalizedValue.charAt(1) + normalizedValue.charAt(1)
                + normalizedValue.charAt(2) + normalizedValue.charAt(2);
        }
        if (normalizedValue.length() != 6 || !normalizedValue.chars().allMatch(MarkdownColors::isHexCharacter)) {
            return null;
        }

        return Integer.parseInt(normalizedValue, 16);
    }

    private static boolean isHexCharacter(int character) {
        return (character >= '0' && character <= '9')
            || (character >= 'a' && character <= 'f')
            || (character >= 'A' && character <= 'F');
    }
}
