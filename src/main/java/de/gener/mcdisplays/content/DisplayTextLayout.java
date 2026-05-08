package de.gener.mcdisplays.content;

import java.util.ArrayList;
import java.util.List;

public final class DisplayTextLayout {
    public static final int CHARS_PER_BLOCK = 18;
    public static final int LINES_PER_BLOCK = 10;

    private DisplayTextLayout() {
    }

    public static List<ScreenPage> layout(DisplayDocument document, int blocksWide, int blocksHigh) {
        int screenWidth = Math.max(CHARS_PER_BLOCK, blocksWide * CHARS_PER_BLOCK);
        int screenHeight = Math.max(LINES_PER_BLOCK, blocksHigh * LINES_PER_BLOCK);
        int bodyLinesPerPage = Math.max(1, screenHeight - 2);

        List<String> flow = new ArrayList<>();
        List<String> sourcePages = document.pages();
        for (int pageIndex = 0; pageIndex < sourcePages.size(); pageIndex++) {
            if (sourcePages.size() > 1) {
                flow.addAll(wrapLine("Source " + (pageIndex + 1) + "/" + sourcePages.size(), screenWidth));
            }

            String[] paragraphs = sourcePages.get(pageIndex).split("\\n", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isBlank()) {
                    flow.add("");
                    continue;
                }
                flow.addAll(wrapLine(paragraph, screenWidth));
            }

            if (pageIndex + 1 < sourcePages.size()) {
                flow.add("");
            }
        }

        if (flow.isEmpty()) {
            flow.add("No content available.");
        }

        int pageCount = (flow.size() + bodyLinesPerPage - 1) / bodyLinesPerPage;
        List<ScreenPage> pages = new ArrayList<>(pageCount);

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            List<String> lines = new ArrayList<>(screenHeight);
            lines.add(center(trimToWidth(document.title(), screenWidth), screenWidth));

            int start = pageIndex * bodyLinesPerPage;
            for (int bodyLine = 0; bodyLine < bodyLinesPerPage; bodyLine++) {
                int lineIndex = start + bodyLine;
                lines.add(lineIndex < flow.size() ? trimToWidth(flow.get(lineIndex), screenWidth) : "");
            }

            lines.add(center("Page " + (pageIndex + 1) + "/" + pageCount, screenWidth));
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
            String line = sourceRow < page.lines().size() ? page.lines().get(sourceRow) : "";
            slice.add(segment(line, columnStart, CHARS_PER_BLOCK));
        }
        return slice;
    }

    private static List<String> wrapLine(String text, int width) {
        List<String> wrapped = new ArrayList<>();
        if (text.isBlank()) {
            wrapped.add("");
            return wrapped;
        }

        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.length() > width) {
                if (!line.isEmpty()) {
                    wrapped.add(line.toString());
                    line.setLength(0);
                }
                int start = 0;
                while (start < word.length()) {
                    int end = Math.min(word.length(), start + width);
                    wrapped.add(word.substring(start, end));
                    start = end;
                }
                continue;
            }

            if (line.isEmpty()) {
                line.append(word);
                continue;
            }

            if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                wrapped.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }

        if (!line.isEmpty()) {
            wrapped.add(line.toString());
        }

        return wrapped;
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

    private static String trimToWidth(String line, int width) {
        return line.length() <= width ? line : line.substring(0, width);
    }

    private static String center(String text, int width) {
        if (text.length() >= width) {
            return text;
        }

        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text;
    }

    public record ScreenPage(List<String> lines, int index, int totalPages) {
    }
}