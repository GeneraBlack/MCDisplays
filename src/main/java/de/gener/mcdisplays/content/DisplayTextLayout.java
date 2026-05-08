package de.gener.mcdisplays.content;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DisplayTextLayout {
    public static final int CHARS_PER_BLOCK = 18;
    public static final int LINES_PER_BLOCK = 10;
    private static final Pattern BLOCKQUOTE_PREFIX_PATTERN = Pattern.compile("^((?:>\\s*)+)(.*)$");
    private static final Pattern TASK_PREFIX_PATTERN = Pattern.compile("^(\\s*-\\s+\\[[ xX]\\]\\s+)(.*)$");
    private static final Pattern ORDERED_PREFIX_PATTERN = Pattern.compile("^(\\s*\\d+\\.\\s+)(.*)$");
    private static final Pattern UNORDERED_PREFIX_PATTERN = Pattern.compile("^(\\s*[-*+]\\s+)(.*)$");

    private DisplayTextLayout() {
    }

    public static List<ScreenPage> layout(DisplayDocument document, int blocksWide, int blocksHigh) {
        int screenWidth = Math.max(CHARS_PER_BLOCK, blocksWide * CHARS_PER_BLOCK);
        int screenHeight = Math.max(LINES_PER_BLOCK, blocksHigh * LINES_PER_BLOCK);
        int bodyLinesPerPage = Math.max(1, screenHeight - 2);

        List<DisplayRichText.StyledLine> flow = new ArrayList<>();
        List<String> sourcePages = document.pages();
        boolean inCodeBlock = false;
        for (int pageIndex = 0; pageIndex < sourcePages.size(); pageIndex++) {
            if (sourcePages.size() > 1) {
                flow.addAll(DisplayRichText.wrap("Source " + (pageIndex + 1) + "/" + sourcePages.size(), screenWidth));
            }

            String[] paragraphs = sourcePages.get(pageIndex).split("\\n", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isBlank()) {
                    flow.add(DisplayRichText.emptyLine());
                    continue;
                }

                if (isFenceLine(paragraph)) {
                    flow.addAll(DisplayRichText.wrapLiteral(paragraph.stripLeading(), screenWidth));
                    inCodeBlock = !inCodeBlock;
                    continue;
                }

                if (inCodeBlock) {
                    flow.addAll(DisplayRichText.wrapLiteral(paragraph, screenWidth));
                    continue;
                }

                if (isTableLine(paragraph)) {
                    flow.addAll(DisplayRichText.wrapVerbatim(paragraph, screenWidth));
                    continue;
                }

                PrefixedParagraph prefixedParagraph = detectPrefixedParagraph(paragraph);
                if (prefixedParagraph != null) {
                    flow.addAll(DisplayRichText.wrapWithPrefix(prefixedParagraph.firstPrefix(), prefixedParagraph.continuationPrefix(), prefixedParagraph.content(), screenWidth));
                    continue;
                }

                flow.addAll(DisplayRichText.wrap(paragraph, screenWidth));
            }

            if (pageIndex + 1 < sourcePages.size()) {
                flow.add(DisplayRichText.emptyLine());
            }
        }

        if (flow.isEmpty()) {
            flow.addAll(DisplayRichText.wrap("No content available.", screenWidth));
        }

        int pageCount = (flow.size() + bodyLinesPerPage - 1) / bodyLinesPerPage;
        List<ScreenPage> pages = new ArrayList<>(pageCount);

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            List<DisplayRichText.StyledLine> lines = new ArrayList<>(screenHeight);
            lines.add(DisplayRichText.center(document.title(), screenWidth).truncate(screenWidth));

            int start = pageIndex * bodyLinesPerPage;
            for (int bodyLine = 0; bodyLine < bodyLinesPerPage; bodyLine++) {
                int lineIndex = start + bodyLine;
                lines.add(lineIndex < flow.size() ? flow.get(lineIndex).truncate(screenWidth) : DisplayRichText.emptyLine());
            }

            lines.add(DisplayRichText.center("Page " + (pageIndex + 1) + "/" + pageCount, screenWidth));
            pages.add(new ScreenPage(List.copyOf(lines), pageIndex, pageCount));
        }

        return pages;
    }

    public static List<String> slice(ScreenPage page, int blocksWide, int blocksHigh, int localX, int localY) {
        List<String> slice = new ArrayList<>(LINES_PER_BLOCK);
        int rowStart = (blocksHigh - localY - 1) * LINES_PER_BLOCK;
        int columnStart = localX * CHARS_PER_BLOCK;
        for (int row = 0; row < LINES_PER_BLOCK; row++) {
            int sourceRow = rowStart + row;
            String line = sourceRow < page.lines().size() ? page.lines().get(sourceRow).plainText() : "";
            slice.add(segment(line, columnStart, CHARS_PER_BLOCK));
        }
        return slice;
    }

    private static boolean isFenceLine(String paragraph) {
        String trimmed = paragraph.stripLeading();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private static boolean isTableLine(String paragraph) {
        String trimmed = paragraph.trim();
        return trimmed.startsWith("|") && trimmed.indexOf('|', 1) > 0;
    }

    private static PrefixedParagraph detectPrefixedParagraph(String paragraph) {
        Matcher blockquoteMatcher = BLOCKQUOTE_PREFIX_PATTERN.matcher(paragraph);
        if (blockquoteMatcher.matches()) {
            String prefix = blockquoteMatcher.group(1);
            return new PrefixedParagraph(prefix, prefix, blockquoteMatcher.group(2));
        }

        Matcher taskMatcher = TASK_PREFIX_PATTERN.matcher(paragraph);
        if (taskMatcher.matches()) {
            return createListParagraph(taskMatcher.group(1), taskMatcher.group(2));
        }

        Matcher orderedMatcher = ORDERED_PREFIX_PATTERN.matcher(paragraph);
        if (orderedMatcher.matches()) {
            return createListParagraph(orderedMatcher.group(1), orderedMatcher.group(2));
        }

        Matcher unorderedMatcher = UNORDERED_PREFIX_PATTERN.matcher(paragraph);
        if (unorderedMatcher.matches()) {
            return createListParagraph(unorderedMatcher.group(1), unorderedMatcher.group(2));
        }

        return null;
    }

    private static PrefixedParagraph createListParagraph(String prefix, String content) {
        return new PrefixedParagraph(prefix, " ".repeat(DisplayRichText.visibleLength(prefix)), content);
    }

    private static String segment(String line, int start, int width) {
        if (line.length() <= start) {
            return " ".repeat(width);
        }

        StringBuilder builder = new StringBuilder(width);
        String trimmed = line.substring(start, Math.min(line.length(), start + width));
        builder.append(trimmed);
        while (builder.length() < width) {
            builder.append(' ');
        }
        return builder.toString();
    }

    public record ScreenPage(List<DisplayRichText.StyledLine> lines, int index, int totalPages) {
    }

    private record PrefixedParagraph(String firstPrefix, String continuationPrefix, String content) {
    }
}