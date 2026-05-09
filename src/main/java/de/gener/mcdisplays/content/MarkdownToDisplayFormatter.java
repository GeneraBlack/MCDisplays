package de.gener.mcdisplays.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class MarkdownToDisplayFormatter {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern SETEXT_HEADING_PATTERN = Pattern.compile("^\\s{0,3}(=+|-+)\\s*$");
    private static final Pattern TASK_PATTERN = Pattern.compile("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.*)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$");

    private MarkdownToDisplayFormatter() {
    }

    public static DisplayDocument toDocument(String configuredTitle, String markdown) {
        String normalizedTitle = configuredTitle == null ? "" : configuredTitle.trim();
        String normalizedMarkdown = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<String> sourceLines = List.of(normalizedMarkdown.split("\\n", -1));

        List<String> lines = new ArrayList<>();
        boolean inCodeBlock = false;
        char codeFenceChar = 0;

        for (int index = 0; index < sourceLines.size(); index++) {
            String line = sourceLines.get(index).replace("\t", "    ");

            if (isCodeFence(line, codeFenceChar)) {
                String fenceLine = line.stripLeading();
                if (inCodeBlock) {
                    lines.add(fenceLine);
                    inCodeBlock = false;
                    codeFenceChar = 0;
                    appendBlankLine(lines);
                } else {
                    appendBlankLine(lines);
                    lines.add(fenceLine);
                    inCodeBlock = true;
                    codeFenceChar = fenceLine.charAt(0);
                }
                continue;
            }

            if (inCodeBlock) {
                lines.add(line);
                continue;
            }

            if (line.isBlank()) {
                appendBlankLine(lines);
                continue;
            }

            TableBlock tableBlock = tryParseTable(sourceLines, index);
            if (tableBlock != null) {
                appendBlankLine(lines);
                lines.addAll(tableBlock.lines());
                appendBlankLine(lines);
                index = tableBlock.endIndex();
                continue;
            }

            if (index + 1 < sourceLines.size()) {
                String nextLine = sourceLines.get(index + 1).replace("\t", "    ");
                Matcher setextMatcher = SETEXT_HEADING_PATTERN.matcher(nextLine);
                if (!line.isBlank() && setextMatcher.matches()) {
                    int level = nextLine.strip().startsWith("=") ? 1 : 2;
                    appendHeading(lines, formatInline(line.strip()), level);
                    index++;
                    continue;
                }
            }

            if (HORIZONTAL_RULE_PATTERN.matcher(line).matches()) {
                appendBlankLine(lines);
                lines.add("--------------------");
                appendBlankLine(lines);
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                String heading = formatInline(headingMatcher.group(2).strip());
                int level = headingMatcher.group(1).length();
                if (heading.isBlank()) {
                    continue;
                }

                appendHeading(lines, heading, level);
                continue;
            }

            Matcher taskMatcher = TASK_PATTERN.matcher(line);
            if (taskMatcher.matches()) {
                lines.add(normalizeIndent(taskMatcher.group(1)) + "- [" + taskMatcher.group(2).trim().toLowerCase(Locale.ROOT) + "] " + formatInline(taskMatcher.group(3)));
                continue;
            }

            Matcher orderedMatcher = ORDERED_LIST_PATTERN.matcher(line);
            if (orderedMatcher.matches()) {
                lines.add(normalizeIndent(orderedMatcher.group(1)) + orderedMatcher.group(2) + ". " + formatInline(orderedMatcher.group(3)));
                continue;
            }

            Matcher unorderedMatcher = UNORDERED_LIST_PATTERN.matcher(line);
            if (unorderedMatcher.matches()) {
                lines.add(normalizeIndent(unorderedMatcher.group(1)) + "- " + formatInline(unorderedMatcher.group(2)));
                continue;
            }

            String blockquote = tryFormatBlockquote(line);
            if (blockquote != null) {
                lines.add(blockquote);
                continue;
            }

            lines.add(formatInline(line));
        }

        trimBlankEdges(lines);

        String body = String.join("\n", lines);
        if (body.isBlank()) {
            body = "This note is empty.";
        }

        String title = !normalizedTitle.isBlank() ? normalizedTitle : "Markdown Pad";
        return new DisplayDocument(title, List.of(body));
    }

    private static boolean isCodeFence(String line, char activeFenceChar) {
        String trimmed = line.stripLeading();
        if (trimmed.length() < 3) {
            return false;
        }

        char candidate = trimmed.charAt(0);
        if (candidate != '`' && candidate != '~') {
            return false;
        }

        int fenceLength = 0;
        while (fenceLength < trimmed.length() && trimmed.charAt(fenceLength) == candidate) {
            fenceLength++;
        }

        if (fenceLength < 3) {
            return false;
        }

        return activeFenceChar == 0 || candidate == activeFenceChar;
    }

    private static String tryFormatBlockquote(String line) {
        String trimmed = line.stripLeading();
        if (!trimmed.startsWith(">")) {
            return null;
        }

        int depth = 0;
        while (trimmed.startsWith(">")) {
            depth++;
            trimmed = trimmed.substring(1);
            if (trimmed.startsWith(" ")) {
                trimmed = trimmed.substring(1);
            }
        }

        String prefix = "> ".repeat(depth);
        return trimmed.isBlank() ? prefix.stripTrailing() : prefix + formatInline(trimmed);
    }

    private static TableBlock tryParseTable(List<String> sourceLines, int startIndex) {
        if (startIndex + 1 >= sourceLines.size()) {
            return null;
        }

        String headerLine = sourceLines.get(startIndex).replace("\t", "    ");
        String separatorLine = sourceLines.get(startIndex + 1).replace("\t", "    ");
        if (!looksLikeTableRow(headerLine) || !looksLikeTableSeparator(separatorLine)) {
            return null;
        }

        List<List<String>> rawRows = new ArrayList<>();
        rawRows.add(parseTableCells(headerLine));

        int endIndex = startIndex + 1;
        for (int index = startIndex + 2; index < sourceLines.size(); index++) {
            String rowLine = sourceLines.get(index).replace("\t", "    ");
            if (rowLine.isBlank() || !looksLikeTableRow(rowLine)) {
                break;
            }

            rawRows.add(parseTableCells(rowLine));
            endIndex = index;
        }

        int columnCount = rawRows.stream().mapToInt(List::size).max().orElse(0);
        if (columnCount == 0) {
            return null;
        }

        List<TableAlignment> alignments = parseTableAlignments(separatorLine, columnCount);
        List<List<String>> rows = rawRows.stream().map(row -> normalizeRow(row, columnCount)).toList();
        int[] widths = computeColumnWidths(rows, columnCount);

        List<String> renderedLines = new ArrayList<>();
        renderedLines.add(renderTableRow(rows.get(0), widths, alignments));
        renderedLines.add(renderTableSeparator(widths, alignments));
        for (int index = 1; index < rows.size(); index++) {
            renderedLines.add(renderTableRow(rows.get(index), widths, alignments));
        }

        return new TableBlock(List.copyOf(renderedLines), endIndex);
    }

    private static boolean looksLikeTableRow(String line) {
        return parseTableCellsRaw(line).size() > 1;
    }

    private static boolean looksLikeTableSeparator(String line) {
        if (!looksLikeTableRow(line)) {
            return false;
        }

        List<String> cells = parseTableCellsRaw(line);
        if (cells.isEmpty()) {
            return false;
        }

        for (String cell : cells) {
            String trimmed = cell.trim();
            if (trimmed.isEmpty()) {
                return false;
            }

            String withoutOuterColons = trimmed;
            if (withoutOuterColons.startsWith(":")) {
                withoutOuterColons = withoutOuterColons.substring(1);
            }
            if (withoutOuterColons.endsWith(":")) {
                withoutOuterColons = withoutOuterColons.substring(0, withoutOuterColons.length() - 1);
            }
            if (withoutOuterColons.length() < 3 || !withoutOuterColons.chars().allMatch(ch -> ch == '-')) {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseTableCells(String line) {
        return parseTableCellsRaw(line).stream().map(MarkdownToDisplayFormatter::formatInline).toList();
    }

    private static List<String> parseTableCellsRaw(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        List<String> cells = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current == '\\' && index + 1 < trimmed.length()) {
                builder.append(current).append(trimmed.charAt(index + 1));
                index++;
                continue;
            }
            if (current == '|') {
                cells.add(builder.toString().trim());
                builder.setLength(0);
                continue;
            }
            builder.append(current);
        }
        cells.add(builder.toString().trim());
        return cells;
    }

    private static List<TableAlignment> parseTableAlignments(String separatorLine, int columnCount) {
        List<String> rawCells = parseTableCellsRaw(separatorLine);
        List<TableAlignment> alignments = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            String cell = index < rawCells.size() ? rawCells.get(index).trim() : "---";
            boolean left = cell.startsWith(":");
            boolean right = cell.endsWith(":");
            if (left && right) {
                alignments.add(TableAlignment.CENTER);
            } else if (right) {
                alignments.add(TableAlignment.RIGHT);
            } else {
                alignments.add(TableAlignment.LEFT);
            }
        }
        return List.copyOf(alignments);
    }

    private static List<String> normalizeRow(List<String> row, int columnCount) {
        List<String> normalized = new ArrayList<>(columnCount);
        normalized.addAll(row);
        while (normalized.size() < columnCount) {
            normalized.add("");
        }
        return List.copyOf(normalized);
    }

    private static int[] computeColumnWidths(List<List<String>> rows, int columnCount) {
        int[] widths = new int[columnCount];
        for (List<String> row : rows) {
            for (int index = 0; index < columnCount; index++) {
                widths[index] = Math.max(widths[index], Math.max(3, DisplayRichText.visibleLength(row.get(index))));
            }
        }
        return widths;
    }

    private static String renderTableRow(List<String> row, int[] widths, List<TableAlignment> alignments) {
        StringBuilder builder = new StringBuilder();
        builder.append('|');
        for (int index = 0; index < widths.length; index++) {
            builder.append(' ').append(padCell(row.get(index), widths[index], alignments.get(index))).append(' ').append('|');
        }
        return builder.toString();
    }

    private static String renderTableSeparator(int[] widths, List<TableAlignment> alignments) {
        StringBuilder builder = new StringBuilder();
        builder.append('|');
        for (int index = 0; index < widths.length; index++) {
            String dashes = "-".repeat(widths[index]);
            String rendered = switch (alignments.get(index)) {
                case LEFT -> ':' + dashes.substring(1);
                case RIGHT -> dashes.substring(0, dashes.length() - 1) + ':';
                case CENTER -> ':' + dashes.substring(1, dashes.length() - 1) + ':';
            };
            builder.append(' ').append(rendered).append(' ').append('|');
        }
        return builder.toString();
    }

    private static String padCell(String text, int width, TableAlignment alignment) {
        int visibleLength = DisplayRichText.visibleLength(text);
        int padding = Math.max(0, width - visibleLength);
        if (padding == 0) {
            return text;
        }

        return switch (alignment) {
            case LEFT -> text + " ".repeat(padding);
            case RIGHT -> " ".repeat(padding) + text;
            case CENTER -> {
                int leftPadding = padding / 2;
                int rightPadding = padding - leftPadding;
                yield " ".repeat(leftPadding) + text + " ".repeat(rightPadding);
            }
        };
    }

    private static void appendBlankLine(List<String> lines) {
        if (lines.isEmpty() || lines.get(lines.size() - 1).isBlank()) {
            return;
        }

        lines.add("");
    }

    private static void trimBlankEdges(List<String> lines) {
        while (!lines.isEmpty() && lines.get(0).isBlank()) {
            lines.remove(0);
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
    }

    private static String formatInline(String line) {
        String safeLine = line == null ? "" : line;
        StringBuilder builder = new StringBuilder(safeLine.length());
        int activeCodeFence = 0;

        int index = 0;
        while (index < safeLine.length()) {
            int backtickCount = countRepeated(safeLine, index, '`');
            if (backtickCount > 0) {
                builder.append("`".repeat(backtickCount));
                activeCodeFence = activeCodeFence == backtickCount ? 0 : backtickCount;
                index += backtickCount;
                continue;
            }

            if (safeLine.charAt(index) == '\\' && index + 1 < safeLine.length()) {
                builder.append('\\').append(safeLine.charAt(index + 1));
                index += 2;
                continue;
            }

            if (activeCodeFence == 0) {
                LinkReplacement replacement = tryParseLinkOrImage(safeLine, index);
                if (replacement != null) {
                    builder.append(replacement.replacement());
                    index = replacement.nextIndex();
                    continue;
                }
            }

            builder.append(safeLine.charAt(index));
            index++;
        }

        return builder.toString().stripTrailing();
    }

    private static void appendHeading(List<String> lines, String heading, int level) {
        if (heading.isBlank()) {
            return;
        }

        appendBlankLine(lines);
        lines.add(heading);
        if (level == 1 || level == 2) {
            int underlineLength = Math.max(3, DisplayRichText.visibleLength(heading));
            lines.add(String.valueOf(level == 1 ? '=' : '-').repeat(underlineLength));
        }
        appendBlankLine(lines);
    }

    private static String normalizeIndent(String indent) {
        return indent == null ? "" : indent;
    }

    private static LinkReplacement tryParseLinkOrImage(String text, int startIndex) {
        boolean image = text.startsWith("![", startIndex);
        int labelStart = image ? startIndex + 2 : text.startsWith("[", startIndex) ? startIndex + 1 : -1;
        if (labelStart < 0) {
            return null;
        }

        int labelEnd = findUnescaped(text, labelStart, ']');
        if (labelEnd < 0 || labelEnd + 1 >= text.length() || text.charAt(labelEnd + 1) != '(') {
            return null;
        }

        int urlEnd = findUnescaped(text, labelEnd + 2, ')');
        if (urlEnd < 0) {
            return null;
        }

        String label = text.substring(labelStart, labelEnd);
        String destination = text.substring(labelEnd + 2, urlEnd).trim();
        String replacement;
        if (image) {
            replacement = "[Image: " + (label.isBlank() ? "Image" : label) + "]";
        } else {
            replacement = destination.isBlank() ? label : label + " <" + destination + ">";
        }

        return new LinkReplacement(replacement, urlEnd + 1);
    }

    private static int findUnescaped(String text, int startIndex, char needle) {
        for (int index = startIndex; index < text.length(); index++) {
            if (text.charAt(index) == '\\') {
                index++;
                continue;
            }
            if (text.charAt(index) == needle) {
                return index;
            }
        }
        return -1;
    }

    private static int countRepeated(String text, int startIndex, char needle) {
        int count = 0;
        while (startIndex + count < text.length() && text.charAt(startIndex + count) == needle) {
            count++;
        }
        return count;
    }

    private enum TableAlignment {
        LEFT,
        RIGHT,
        CENTER
    }

    private record TableBlock(List<String> lines, int endIndex) {
    }

    private record LinkReplacement(String replacement, int nextIndex) {
    }
}