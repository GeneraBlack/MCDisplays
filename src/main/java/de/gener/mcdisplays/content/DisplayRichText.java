package de.gener.mcdisplays.content;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class DisplayRichText {
    private static final ResourceLocation UNIFORM_FONT = Objects.requireNonNull(ResourceLocation.tryParse("minecraft:uniform"));
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

    private DisplayRichText() {
    }

    public static StyledLine emptyLine() {
        return new StyledLine(List.of());
    }

    public static StyledLine parse(String rawText) {
        return fromCharacters(parseCharacters(rawText));
    }

    public static List<StyledLine> wrap(String rawText, int width) {
        return wrapWords(parseCharacters(rawText), List.of(), List.of(), width);
    }

    public static List<StyledLine> wrapWithPrefix(String firstPrefix, String continuationPrefix, String rawText, int width) {
        List<StyledCharacter> firstPrefixCharacters = parseCharacters(firstPrefix);
        List<StyledCharacter> continuationPrefixCharacters = parseCharacters(continuationPrefix);
        List<StyledCharacter> contentCharacters = parseCharacters(rawText);
        if (contentCharacters.isEmpty()) {
            return firstPrefixCharacters.isEmpty() ? List.of(emptyLine()) : wrapFixed(firstPrefixCharacters, width);
        }

        return wrapWords(contentCharacters, firstPrefixCharacters, continuationPrefixCharacters, width);
    }

    public static List<StyledLine> wrapVerbatim(String rawText, int width) {
        return wrapFixed(parseCharacters(rawText), width);
    }

    public static List<StyledLine> wrapLiteral(String rawText, int width) {
        String safeText = rawText == null ? "" : rawText;
        List<StyledCharacter> characters = new ArrayList<>(safeText.length());
        StyleState literalState = StyleState.plain();
        for (int index = 0; index < safeText.length(); index++) {
            characters.add(new StyledCharacter(safeText.charAt(index), literalState));
        }
        return wrapFixed(characters, width);
    }

    public static StyledLine center(String rawText, int width) {
        StyledLine line = parse(rawText).truncate(width);
        int padding = Math.max(0, (width - line.visibleLength()) / 2);
        if (padding <= 0) {
            return line;
        }

        List<StyledRun> runs = new ArrayList<>();
        runs.add(new StyledRun(" ".repeat(padding), StyleState.plain()));
        runs.addAll(line.runs());
        return new StyledLine(runs);
    }

    public static int visibleLength(String rawText) {
        return parse(rawText).visibleLength();
    }

    private static List<StyledCharacter> parseCharacters(String rawText) {
        String safeText = rawText == null ? "" : rawText;
        List<StyledCharacter> characters = new ArrayList<>(safeText.length());
        Deque<Integer> colorStack = new ArrayDeque<>();
        StyleState state = StyleState.plain();
        int activeCodeFenceLength = 0;

        int index = 0;
        while (index < safeText.length()) {
            if (activeCodeFenceLength > 0) {
                int fenceLength = countRepeated(safeText, index, '`');
                if (fenceLength == activeCodeFenceLength) {
                    activeCodeFenceLength = 0;
                    state = state.withCode(false);
                    index += fenceLength;
                    continue;
                }

                characters.add(new StyledCharacter(safeText.charAt(index), state));
                index++;
                continue;
            }

            char current = safeText.charAt(index);
            if (current == '\\' && index + 1 < safeText.length()) {
                characters.add(new StyledCharacter(safeText.charAt(index + 1), state));
                index += 2;
                continue;
            }

            if (current == '[') {
                int end = safeText.indexOf(']', index + 1);
                if (end > index + 1) {
                    TagAction action = parseTag(safeText.substring(index + 1, end));
                    if (action != null) {
                        switch (action.kind()) {
                            case PUSH_COLOR -> colorStack.push(action.color());
                            case POP_COLOR -> {
                                if (!colorStack.isEmpty()) {
                                    colorStack.pop();
                                }
                            }
                            case CLEAR_COLORS -> colorStack.clear();
                        }
                        state = state.withColor(colorStack.peek());
                        index = end + 1;
                        continue;
                    }
                }
            }

            int backtickCount = countRepeated(safeText, index, '`');
            if (backtickCount > 0) {
                if (hasMatchingFence(safeText, index + backtickCount, backtickCount)) {
                    activeCodeFenceLength = backtickCount;
                    state = state.withCode(true);
                    index += backtickCount;
                    continue;
                }

                characters.add(new StyledCharacter(current, state));
                index++;
                continue;
            }

            String marker = detectStyleMarker(safeText, index);
            if (marker != null && shouldToggleStyle(marker, state, safeText, index)) {
                state = toggleStyle(state, marker);
                index += marker.length();
                continue;
            }

            characters.add(new StyledCharacter(current, state));
            index++;
        }

        return characters;
    }

    private static List<StyledLine> wrapWords(List<StyledCharacter> characters, List<StyledCharacter> firstPrefix, List<StyledCharacter> continuationPrefix, int width) {
        int safeWidth = Math.max(1, width);
        List<List<StyledCharacter>> words = splitWords(characters);
        if (words.isEmpty()) {
            return firstPrefix.isEmpty() ? List.of(emptyLine()) : wrapFixed(firstPrefix, safeWidth);
        }

        int firstContentWidth = Math.max(1, safeWidth - firstPrefix.size());
        int continuationContentWidth = Math.max(1, safeWidth - continuationPrefix.size());
        List<StyledLine> wrapped = new ArrayList<>();

        List<StyledCharacter> currentLine = new ArrayList<>(firstPrefix);
        int currentPrefixSize = firstPrefix.size();
        int currentContentWidth = firstContentWidth;
        boolean firstLine = true;

        for (List<StyledCharacter> word : words) {
            if (word.size() > currentContentWidth) {
                if (currentLine.size() > currentPrefixSize) {
                    wrapped.add(fromCharacters(currentLine));
                    firstLine = false;
                }

                int start = 0;
                while (start < word.size()) {
                    List<StyledCharacter> prefix = firstLine ? firstPrefix : continuationPrefix;
                    int prefixSize = firstLine ? firstPrefix.size() : continuationPrefix.size();
                    int contentWidth = Math.max(1, safeWidth - prefixSize);
                    int end = Math.min(word.size(), start + contentWidth);

                    List<StyledCharacter> line = new ArrayList<>(prefix);
                    line.addAll(word.subList(start, end));
                    wrapped.add(fromCharacters(line));

                    firstLine = false;
                    start = end;
                }

                currentLine = new ArrayList<>(continuationPrefix);
                currentPrefixSize = continuationPrefix.size();
                currentContentWidth = continuationContentWidth;
                continue;
            }

            int usedCharacters = currentLine.size() - currentPrefixSize;
            int nextLength = usedCharacters == 0 ? word.size() : usedCharacters + 1 + word.size();
            if (usedCharacters > 0 && nextLength > currentContentWidth) {
                wrapped.add(fromCharacters(currentLine));
                firstLine = false;
                currentLine = new ArrayList<>(continuationPrefix);
                currentPrefixSize = continuationPrefix.size();
                currentContentWidth = continuationContentWidth;
                usedCharacters = 0;
            }

            if (usedCharacters > 0) {
                StyleState separatorState = currentLine.get(currentLine.size() - 1).style();
                currentLine.add(new StyledCharacter(' ', separatorState));
            }
            currentLine.addAll(word);
        }

        if (currentLine.size() > currentPrefixSize || wrapped.isEmpty()) {
            wrapped.add(fromCharacters(currentLine));
        }

        return List.copyOf(wrapped);
    }

    private static TagAction parseTag(String rawTag) {
        String normalizedTag = rawTag.trim().toLowerCase(Locale.ROOT);
        if (normalizedTag.isEmpty()) {
            return null;
        }

        if (normalizedTag.equals("/") || normalizedTag.equals("/color") || normalizedTag.equals("/colour")) {
            return new TagAction(TagActionKind.POP_COLOR, null);
        }

        if (normalizedTag.equals("reset") || normalizedTag.equals("default")) {
            return new TagAction(TagActionKind.CLEAR_COLORS, null);
        }

        Integer resolvedColor = resolveColor(normalizedTag);
        return resolvedColor == null ? null : new TagAction(TagActionKind.PUSH_COLOR, resolvedColor);
    }

    private static Integer resolveColor(String tag) {
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
        if (normalizedValue.length() != 6 || !normalizedValue.chars().allMatch(DisplayRichText::isHexCharacter)) {
            return null;
        }

        return Integer.parseInt(normalizedValue, 16);
    }

    private static boolean isHexCharacter(int character) {
        return (character >= '0' && character <= '9')
            || (character >= 'a' && character <= 'f')
            || (character >= 'A' && character <= 'F');
    }

    private static String detectStyleMarker(String text, int index) {
        if (text.startsWith("***", index) || text.startsWith("___", index)) {
            return text.substring(index, index + 3);
        }
        if (text.startsWith("**", index) || text.startsWith("__", index) || text.startsWith("~~", index)) {
            return text.substring(index, index + 2);
        }

        char current = text.charAt(index);
        if ((current == '*' || current == '_') && isSingleEmphasisCandidate(text, index)) {
            return String.valueOf(current);
        }

        return null;
    }

    private static boolean shouldToggleStyle(String marker, StyleState state, String text, int index) {
        return isStyleActive(marker, state) || text.indexOf(marker, index + marker.length()) >= 0;
    }

    private static boolean isStyleActive(String marker, StyleState state) {
        return switch (marker) {
            case "***", "___" -> state.bold() && state.italic();
            case "**", "__" -> state.bold();
            case "*", "_" -> state.italic();
            case "~~" -> state.strikethrough();
            default -> false;
        };
    }

    private static StyleState toggleStyle(StyleState state, String marker) {
        return switch (marker) {
            case "***", "___" -> state.withBold(!state.bold()).withItalic(!state.italic());
            case "**", "__" -> state.withBold(!state.bold());
            case "*", "_" -> state.withItalic(!state.italic());
            case "~~" -> state.withStrikethrough(!state.strikethrough());
            default -> state;
        };
    }

    private static boolean isSingleEmphasisCandidate(String text, int index) {
        char current = text.charAt(index);
        if (current != '*' && current != '_') {
            return false;
        }

        char previous = index > 0 ? text.charAt(index - 1) : ' ';
        char next = index + 1 < text.length() ? text.charAt(index + 1) : ' ';
        if (Character.isLetterOrDigit(previous) && Character.isLetterOrDigit(next)) {
            return false;
        }

        return !Character.isWhitespace(next);
    }

    private static boolean hasMatchingFence(String text, int searchStart, int fenceLength) {
        String fence = "`".repeat(fenceLength);
        return text.indexOf(fence, searchStart) >= 0;
    }

    private static int countRepeated(String text, int index, char character) {
        int count = 0;
        while (index + count < text.length() && text.charAt(index + count) == character) {
            count++;
        }
        return count;
    }

    private static List<List<StyledCharacter>> splitWords(List<StyledCharacter> characters) {
        List<List<StyledCharacter>> words = new ArrayList<>();
        List<StyledCharacter> currentWord = new ArrayList<>();

        for (StyledCharacter character : characters) {
            if (Character.isWhitespace(character.character())) {
                if (!currentWord.isEmpty()) {
                    words.add(List.copyOf(currentWord));
                    currentWord.clear();
                }
                continue;
            }

            currentWord.add(character);
        }

        if (!currentWord.isEmpty()) {
            words.add(List.copyOf(currentWord));
        }

        return words;
    }

    private static StyledLine fromCharacters(List<StyledCharacter> characters) {
        if (characters.isEmpty()) {
            return emptyLine();
        }

        List<StyledRun> runs = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        StyleState currentState = characters.get(0).style();

        for (StyledCharacter character : characters) {
            if (!currentState.equals(character.style())) {
                runs.add(new StyledRun(builder.toString(), currentState));
                builder.setLength(0);
                currentState = character.style();
            }
            builder.append(character.character());
        }

        if (!builder.isEmpty()) {
            runs.add(new StyledRun(builder.toString(), currentState));
        }

        return new StyledLine(runs);
    }

    private static List<StyledLine> wrapFixed(List<StyledCharacter> characters, int width) {
        int safeWidth = Math.max(1, width);
        if (characters.isEmpty()) {
            return List.of(emptyLine());
        }

        List<StyledLine> wrapped = new ArrayList<>();
        for (int start = 0; start < characters.size(); start += safeWidth) {
            int end = Math.min(characters.size(), start + safeWidth);
            wrapped.add(fromCharacters(new ArrayList<>(characters.subList(start, end))));
        }
        return List.copyOf(wrapped);
    }

    private enum TagActionKind {
        PUSH_COLOR,
        POP_COLOR,
        CLEAR_COLORS
    }

    private record TagAction(TagActionKind kind, Integer color) {
    }

    private record StyleState(Integer color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean code) {
        private static final StyleState PLAIN = new StyleState(null, false, false, false, false, false);

        private static StyleState plain() {
            return PLAIN;
        }

        private StyleState withColor(Integer newColor) {
            return new StyleState(newColor, bold, italic, underlined, strikethrough, code);
        }

        private StyleState withBold(boolean newBold) {
            return new StyleState(color, newBold, italic, underlined, strikethrough, code);
        }

        private StyleState withItalic(boolean newItalic) {
            return new StyleState(color, bold, newItalic, underlined, strikethrough, code);
        }

        private StyleState withStrikethrough(boolean newStrikethrough) {
            return new StyleState(color, bold, italic, underlined, newStrikethrough, code);
        }

        private StyleState withCode(boolean newCode) {
            return new StyleState(color, bold, italic, underlined, strikethrough, newCode);
        }
    }

    private record StyledCharacter(char character, StyleState style) {
    }

    public record StyledRun(String text, StyleState style) {
    }

    public record StyledLine(List<StyledRun> runs) {
        public StyledLine {
            runs = List.copyOf(runs);
        }

        public int visibleLength() {
            int length = 0;
            for (StyledRun run : runs) {
                length += run.text().length();
            }
            return length;
        }

        public boolean isBlank() {
            return plainText().isBlank();
        }

        public String plainText() {
            StringBuilder builder = new StringBuilder();
            for (StyledRun run : runs) {
                builder.append(run.text());
            }
            return builder.toString();
        }

        public StyledLine truncate(int width) {
            if (visibleLength() <= width) {
                return this;
            }

            List<StyledCharacter> characters = new ArrayList<>(width);
            int remaining = width;
            for (StyledRun run : runs) {
                for (int index = 0; index < run.text().length() && remaining > 0; index++) {
                    characters.add(new StyledCharacter(run.text().charAt(index), run.style()));
                    remaining--;
                }

                if (remaining <= 0) {
                    break;
                }
            }

            return DisplayRichText.fromCharacters(characters);
        }

        public MutableComponent toComponent(int defaultColor, ResourceLocation font) {
            MutableComponent component = Component.empty();
            for (StyledRun run : runs) {
                if (run.text().isEmpty()) {
                    continue;
                }

                StyleState styleState = run.style();
                int resolvedColor = styleState.color() != null ? styleState.color() : defaultColor;
                ResourceLocation resolvedFont = font != null ? font : (styleState.code() ? UNIFORM_FONT : null);

                component.append(
                    Component.literal(run.text()).withStyle(style -> {
                        style = style.withColor(resolvedColor)
                            .withBold(styleState.bold())
                            .withItalic(styleState.italic())
                            .withUnderlined(styleState.underlined())
                            .withStrikethrough(styleState.strikethrough());
                        if (resolvedFont != null) {
                            style = style.withFont(resolvedFont);
                        }
                        return style;
                    })
                );
            }
            return component;
        }
    }
}