package de.gener.mcdisplays.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pixel-based layout of Markdown blocks onto display pages. Text is wrapped to the full display width,
 * measured with the supplied {@link TextMetrics}, aligned per block and paginated by pixel height.
 */
public final class MarkdownLayout {
    public static final int PX_PER_BLOCK = 112;
    private static final int MARGIN = 6;
    private static final float QUOTE_STEP = 7.0F;
    private static final float INDENT_STEP = 10.0F;
    private static final float TABLE_PAD = 3.0F;
    private static final int COLOR_TEXT = 0xF3F3F3;
    private static final int COLOR_TITLE = 0xF6D58B;
    private static final int COLOR_HEADING_LOW = 0xE4EEF7;
    private static final int COLOR_QUOTE = 0xB8C2CC;
    private static final int COLOR_CODE = 0xA8E6A1;
    private static final int COLOR_FOOTER = 0x9CB5C8;
    private static final float[] HEADING_SCALE = {1.0F, 1.6F, 1.35F, 1.15F, 1.0F, 1.0F, 1.0F};

    private static final Map<String, List<Block>> PARSE_CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Block>> eldest) {
            return size() > 48;
        }
    };

    private MarkdownLayout() {
    }

    public static String join(List<String> pages) {
        return String.join("\n\n", pages);
    }

    public static List<Block> parse(String markdown) {
        synchronized (PARSE_CACHE) {
            List<Block> cached = PARSE_CACHE.get(markdown);
            if (cached == null) {
                cached = MarkdownParser.parse(markdown);
                PARSE_CACHE.put(markdown, cached);
            }
            return cached;
        }
    }

    public static List<LayoutPage> layout(String title, List<String> sourcePages, int blocksWide, int blocksHigh, TextMetrics metrics) {
        return layout(title, join(sourcePages), blocksWide, blocksHigh, metrics);
    }

    public static List<LayoutPage> layout(String title, String markdown, int blocksWide, int blocksHigh, TextMetrics metrics) {
        Engine engine = new Engine(metrics, Math.max(1, blocksWide) * PX_PER_BLOCK, Math.max(1, blocksHigh) * PX_PER_BLOCK);
        return engine.run(title == null ? "" : title.strip(), parse(markdown));
    }

    private record Box(float spaceBefore, float height, boolean keepWithNext, List<LayoutPage.Op> ops) {
    }

    private record Placed(Box box, float y) {
    }

    private record Run(String text, int color, int flags, float width) {
    }

    private record Line(List<Run> runs, float width) {
    }

    private static final class Engine {
        private final TextMetrics metrics;
        private final int pageWidth;
        private final int pageHeight;
        private final float contentWidth;
        private final float lineHeight;

        Engine(TextMetrics metrics, int pageWidth, int pageHeight) {
            this.metrics = metrics;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.contentWidth = pageWidth - 2.0F * MARGIN;
            this.lineHeight = metrics.lineHeight() + 1.0F;
        }

        List<LayoutPage> run(String title, List<Block> blocks) {
            // Cinema fullscreen mode: if the document consists solely of a video block and title is empty/none/cinema
            if (blocks.size() == 1 && blocks.get(0) instanceof Block.Video video && (video.cinema() || title.isEmpty() || "none".equalsIgnoreCase(title))) {
                List<LayoutPage.Op> ops = List.of(new LayoutPage.VideoOp(video.url(), video.title(), video.volume(), video.distance(), video.loop(), true, video.blocked(), video.audioSource(), video.startEpoch(), 0.0F, 0.0F, pageWidth, pageHeight));
                return List.of(new LayoutPage(ops, 0, 1, pageWidth, pageHeight));
            }

            List<Box> boxes = new ArrayList<>();
            for (Block block : blocks) {
                boxes.addAll(layoutBlock(block));
            }

            float headerHeight = title.isEmpty() ? 0.0F : metrics.lineHeight() + 6.0F;
            float footerHeight = metrics.lineHeight() + 4.0F;
            float bodyHeight = pageHeight - 2.0F * MARGIN - headerHeight;
            List<List<Placed>> pages = paginate(boxes, bodyHeight);
            if (pages.size() > 1) {
                pages = paginate(boxes, bodyHeight - footerHeight);
            }

            List<LayoutPage> result = new ArrayList<>(pages.size());
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                List<LayoutPage.Op> ops = new ArrayList<>();
                if (!title.isEmpty()) {
                    String shown = truncate(title, contentWidth, Block.Span.BOLD);
                    float titleWidth = metrics.width(shown, Block.Span.BOLD);
                    ops.add(new LayoutPage.TextOp(shown, Block.Span.BOLD, COLOR_TITLE, MARGIN + (contentWidth - titleWidth) / 2.0F, MARGIN, 1.0F));
                    ops.add(new LayoutPage.RectOp(MARGIN, MARGIN + metrics.lineHeight() + 2.0F, contentWidth, 1.0F, 0x88F6D58B));
                }
                for (Placed placed : pages.get(pageIndex)) {
                    float dx = MARGIN;
                    float dy = MARGIN + headerHeight + placed.y();
                    for (LayoutPage.Op op : placed.box().ops()) {
                        if (op instanceof LayoutPage.TextOp text) {
                            ops.add(text.moved(dx, dy));
                        } else if (op instanceof LayoutPage.RectOp rect) {
                            ops.add(rect.moved(dx, dy));
                        } else if (op instanceof LayoutPage.ImageOp img) {
                            ops.add(img.moved(dx, dy));
                        } else if (op instanceof LayoutPage.VideoOp vid) {
                            ops.add(vid.moved(dx, dy));
                        }
                    }
                }
                if (pages.size() > 1) {
                    String label = "< " + (pageIndex + 1) + "/" + pages.size() + " >";
                    float labelWidth = metrics.width(label, 0);
                    ops.add(new LayoutPage.TextOp(label, 0, COLOR_FOOTER, MARGIN + (contentWidth - labelWidth) / 2.0F, pageHeight - MARGIN - metrics.lineHeight(), 1.0F));
                }
                result.add(new LayoutPage(List.copyOf(ops), pageIndex, pages.size(), pageWidth, pageHeight));
            }
            return result;
        }

        private List<List<Placed>> paginate(List<Box> boxes, float bodyHeight) {
            List<List<Placed>> pages = new ArrayList<>();
            List<Placed> current = new ArrayList<>();
            float y = 0.0F;
            for (int index = 0; index < boxes.size(); index++) {
                Box box = boxes.get(index);
                float before = current.isEmpty() ? 0.0F : box.spaceBefore();
                float needed = before + box.height();
                if (box.keepWithNext() && index + 1 < boxes.size()) {
                    Box next = boxes.get(index + 1);
                    needed += next.spaceBefore() + next.height();
                }
                if (!current.isEmpty() && y + needed > bodyHeight) {
                    pages.add(current);
                    current = new ArrayList<>();
                    y = 0.0F;
                    before = 0.0F;
                }
                y += before;
                current.add(new Placed(box, y));
                y += box.height();
            }
            if (!current.isEmpty() || pages.isEmpty()) {
                pages.add(current);
            }
            return pages;
        }

        private List<Box> layoutBlock(Block block) {
            if (block instanceof Block.Text text) {
                return layoutText(text);
            } else if (block instanceof Block.Code code) {
                return layoutCode(code);
            } else if (block instanceof Block.Table table) {
                return layoutTable(table);
            } else if (block instanceof Block.Rule) {
                return List.of(new Box(4.0F, 5.0F, false, List.of(new LayoutPage.RectOp(0.0F, 2.0F, contentWidth, 1.0F, 0x66FFFFFF))));
            } else if (block instanceof Block.Image image) {
                return layoutImage(image);
            } else if (block instanceof Block.Video video) {
                return layoutVideo(video);
            }
            return List.of();
        }

        // ---- text ----

        private List<Box> layoutText(Block.Text block) {
            int level = Math.min(Math.max(block.level(), 0), 6);
            float scale = HEADING_SCALE[level];
            int baseFlags = level > 0 ? Block.Span.BOLD : 0;
            int color = level == 0 ? (block.quoteDepth() > 0 ? COLOR_QUOTE : COLOR_TEXT) : (level <= 2 ? COLOR_TITLE : COLOR_HEADING_LOW);
            float textStart = block.quoteDepth() * QUOTE_STEP + block.indent() * INDENT_STEP;
            float available = Math.max(8.0F, contentWidth - textStart);

            List<Line> lines = wrap(block.spans(), available, scale, color, baseFlags);
            float height = lineHeight * scale;
            float gap = level > 0 ? Math.round(5.0F * scale) : (block.tight() ? 1.0F : 4.0F);

            if (lines.isEmpty()) {
                if (!block.marker().isEmpty()) {
                    List<LayoutPage.Op> ops = new ArrayList<>();
                    float markerWidth = metrics.width(block.marker(), 0);
                    ops.add(new LayoutPage.TextOp(block.marker(), 0, COLOR_TITLE, textStart - 3.0F - markerWidth, 0.0F, 1.0F));
                    for (int depth = 0; depth < block.quoteDepth(); depth++) {
                        ops.add(new LayoutPage.RectOp(depth * QUOTE_STEP + 1.0F, 0.0F, 2.0F, height, 0x88F6D58B));
                    }
                    return List.of(new Box(gap, height, false, ops));
                }
                return List.of();
            }
            List<Box> boxes = new ArrayList<>(lines.size());
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                List<LayoutPage.Op> ops = new ArrayList<>();
                float x = textStart + alignOffset(block.align(), available, line.width());
                for (Run run : line.runs()) {
                    ops.add(new LayoutPage.TextOp(run.text(), run.flags(), run.color(), x, 0.0F, scale));
                    x += run.width();
                }
                if (index == 0 && !block.marker().isEmpty()) {
                    float markerWidth = metrics.width(block.marker(), 0);
                    ops.add(new LayoutPage.TextOp(block.marker(), 0, COLOR_TITLE, textStart - 3.0F - markerWidth, 0.0F, 1.0F));
                }
                for (int depth = 0; depth < block.quoteDepth(); depth++) {
                    ops.add(new LayoutPage.RectOp(depth * QUOTE_STEP + 1.0F, 0.0F, 2.0F, height, 0x88F6D58B));
                }
                boxes.add(new Box(index == 0 ? gap : 0.0F, height, level > 0, ops));
            }
            return boxes;
        }

        private static float alignOffset(Block.Align align, float available, float width) {
            return switch (align) {
                case CENTER -> Math.max(0.0F, (available - width) / 2.0F);
                case RIGHT -> Math.max(0.0F, available - width);
                default -> 0.0F;
            };
        }

        private List<Line> wrap(List<Block.Span> spans, float available, float scale, int defaultColor, int baseFlags) {
            LineBuilder builder = new LineBuilder(available);
            boolean pendingSpace = false;
            int spaceFlags = 0;
            int spaceColor = defaultColor;

            for (Block.Span span : spans) {
                if (span.isBreak()) {
                    builder.newLine(true);
                    pendingSpace = false;
                    continue;
                }

                int flags = span.flags() | baseFlags;
                int color = span.color() >= 0 ? span.color() : defaultColor;
                String text = span.text();
                int index = 0;
                while (index < text.length()) {
                    int end = index;
                    boolean whitespace = Character.isWhitespace(text.charAt(index));
                    while (end < text.length() && Character.isWhitespace(text.charAt(end)) == whitespace) {
                        end++;
                    }
                    String token = text.substring(index, end);
                    index = end;

                    if (whitespace) {
                        if (!builder.isEmpty()) {
                            pendingSpace = true;
                            spaceFlags = flags;
                            spaceColor = color;
                        }
                        continue;
                    }

                    float wordWidth = metrics.width(token, flags) * scale;
                    float spaceWidth = pendingSpace ? metrics.width(" ", spaceFlags) * scale : 0.0F;
                    if (!builder.isEmpty() && builder.width + spaceWidth + wordWidth > available) {
                        builder.newLine(false);
                        pendingSpace = false;
                        spaceWidth = 0.0F;
                    }
                    if (pendingSpace) {
                        builder.append(" ", spaceColor, spaceFlags, spaceWidth);
                        pendingSpace = false;
                    }
                    if (wordWidth <= available) {
                        builder.append(token, color, flags, wordWidth);
                    } else {
                        for (int charIndex = 0; charIndex < token.length(); charIndex++) {
                            String glyph = token.substring(charIndex, charIndex + 1);
                            float glyphWidth = metrics.width(glyph, flags) * scale;
                            if (!builder.isEmpty() && builder.width + glyphWidth > available) {
                                builder.newLine(false);
                            }
                            builder.append(glyph, color, flags, glyphWidth);
                        }
                    }
                }
            }
            return builder.finish();
        }

        private static final class LineBuilder {
            private final List<Line> lines = new ArrayList<>();
            private List<Run> runs = new ArrayList<>();
            float width;

            LineBuilder(float available) {
            }

            boolean isEmpty() {
                return runs.isEmpty();
            }

            void append(String text, int color, int flags, float runWidth) {
                if (!runs.isEmpty()) {
                    Run last = runs.get(runs.size() - 1);
                    if (last.color() == color && last.flags() == flags) {
                        runs.set(runs.size() - 1, new Run(last.text() + text, color, flags, last.width() + runWidth));
                        width += runWidth;
                        return;
                    }
                }
                runs.add(new Run(text, color, flags, runWidth));
                width += runWidth;
            }

            void newLine(boolean allowEmpty) {
                if (!runs.isEmpty() || allowEmpty) {
                    lines.add(new Line(List.copyOf(runs), width));
                }
                runs = new ArrayList<>();
                width = 0.0F;
            }

            List<Line> finish() {
                if (!runs.isEmpty()) {
                    newLine(false);
                }
                return lines;
            }
        }

        // ---- code ----

        private List<Box> layoutCode(Block.Code block) {
            float left = block.quoteDepth() * QUOTE_STEP;
            float available = Math.max(8.0F, contentWidth - left - 6.0F);
            List<String> rows = new ArrayList<>();
            for (String line : block.lines()) {
                String expanded = line.replace("\t", "    ");
                if (expanded.isEmpty()) {
                    rows.add("");
                    continue;
                }
                StringBuilder current = new StringBuilder();
                for (int index = 0; index < expanded.length(); index++) {
                    current.append(expanded.charAt(index));
                    if (metrics.width(current.toString(), Block.Span.CODE) > available && current.length() > 1) {
                        current.setLength(current.length() - 1);
                        rows.add(current.toString());
                        current.setLength(0);
                        current.append(expanded.charAt(index));
                    }
                }
                rows.add(current.toString());
            }

            List<Box> boxes = new ArrayList<>(rows.size());
            for (int index = 0; index < rows.size(); index++) {
                List<LayoutPage.Op> ops = new ArrayList<>();
                ops.add(new LayoutPage.RectOp(left, 0.0F, contentWidth - left, lineHeight, 0x44000000));
                if (!rows.get(index).isEmpty()) {
                    ops.add(new LayoutPage.TextOp(rows.get(index), Block.Span.CODE, COLOR_CODE, left + 3.0F, 0.0F, 1.0F));
                }
                boxes.add(new Box(index == 0 ? 4.0F : 0.0F, lineHeight, false, ops));
            }
            return boxes;
        }

        // ---- table ----

        private List<Box> layoutTable(Block.Table table) {
            int columns = 0;
            for (List<List<Block.Span>> row : table.rows()) {
                columns = Math.max(columns, row.size());
            }
            if (columns == 0) {
                return List.of();
            }

            float[] natural = new float[columns];
            float[] minimal = new float[columns];
            for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
                int flags = table.header() && rowIndex == 0 ? Block.Span.BOLD : 0;
                List<List<Block.Span>> row = table.rows().get(rowIndex);
                for (int column = 0; column < row.size(); column++) {
                    float total = 0.0F;
                    float longestWord = 0.0F;
                    for (Block.Span span : row.get(column)) {
                        if (span.isBreak()) {
                            continue;
                        }
                        total += metrics.width(span.text(), span.flags() | flags);
                        for (String word : span.text().split("\\s+")) {
                            longestWord = Math.max(longestWord, metrics.width(word, span.flags() | flags));
                        }
                    }
                    natural[column] = Math.max(natural[column], total + 2.0F * TABLE_PAD);
                    minimal[column] = Math.max(minimal[column], Math.min(longestWord, total) + 2.0F * TABLE_PAD);
                }
            }

            float[] widths = columnWidths(natural, minimal, contentWidth);
            float[] starts = new float[columns + 1];
            for (int column = 0; column < columns; column++) {
                starts[column + 1] = starts[column] + widths[column];
            }

            List<Box> boxes = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
                boolean headerRow = table.header() && rowIndex == 0;
                int flags = headerRow ? Block.Span.BOLD : 0;
                int color = headerRow ? COLOR_TITLE : COLOR_TEXT;
                List<List<Block.Span>> row = table.rows().get(rowIndex);

                List<List<Line>> cellLines = new ArrayList<>();
                int rowLines = 1;
                for (int column = 0; column < row.size(); column++) {
                    List<Line> lines = wrap(row.get(column), widths[column] - 2.0F * TABLE_PAD, 1.0F, color, flags);
                    cellLines.add(lines);
                    rowLines = Math.max(rowLines, lines.size());
                }

                float rowHeight = rowLines * lineHeight + 4.0F;
                List<LayoutPage.Op> ops = new ArrayList<>();
                if (headerRow) {
                    ops.add(new LayoutPage.RectOp(0.0F, 0.0F, contentWidth, rowHeight, 0x33FFFFFF));
                }
                for (int column = 0; column < row.size(); column++) {
                    Block.Align align = column < table.aligns().size() ? table.aligns().get(column) : Block.Align.LEFT;
                    float cellAvailable = widths[column] - 2.0F * TABLE_PAD;
                    List<Line> lines = cellLines.get(column);
                    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                        Line line = lines.get(lineIndex);
                        float x = starts[column] + TABLE_PAD + alignOffset(align, cellAvailable, line.width());
                        for (Run run : line.runs()) {
                            ops.add(new LayoutPage.TextOp(run.text(), run.flags(), run.color(), x, 2.0F + lineIndex * lineHeight, 1.0F));
                            x += run.width();
                        }
                    }
                }
                int border = 0x55FFFFFF;
                if (rowIndex == 0) {
                    ops.add(new LayoutPage.RectOp(0.0F, 0.0F, contentWidth, 1.0F, border));
                }
                ops.add(new LayoutPage.RectOp(0.0F, rowHeight - 1.0F, contentWidth, 1.0F, border));
                for (int column = 0; column <= columns; column++) {
                    float x = Math.min(starts[column], contentWidth - 1.0F);
                    ops.add(new LayoutPage.RectOp(x, 0.0F, 1.0F, rowHeight, border));
                }
                boxes.add(new Box(rowIndex == 0 ? 4.0F : 0.0F, rowHeight, headerRow, ops));
            }
            return boxes;
        }

        private static float[] columnWidths(float[] natural, float[] minimal, float available) {
            int columns = natural.length;
            float[] widths = new float[columns];
            float naturalTotal = 0.0F;
            float minimalTotal = 0.0F;
            for (int column = 0; column < columns; column++) {
                naturalTotal += natural[column];
                minimalTotal += minimal[column];
            }

            if (naturalTotal <= available) {
                for (int column = 0; column < columns; column++) {
                    widths[column] = natural[column] / naturalTotal * available;
                }
            } else if (minimalTotal >= available) {
                for (int column = 0; column < columns; column++) {
                    widths[column] = minimal[column] / minimalTotal * available;
                }
            } else {
                float slackTotal = naturalTotal - minimalTotal;
                for (int column = 0; column < columns; column++) {
                    widths[column] = minimal[column] + (available - minimalTotal) * (natural[column] - minimal[column]) / slackTotal;
                }
            }
            return widths;
        }

        // ---- media placeholder ----

        private List<Box> layoutImage(Block.Image image) {
            float available = contentWidth;
            float aspect = 1.0F;
            if (metrics != null) {
                float a = metrics.imageAspectRatio(image.url());
                if (a > 0.05F) {
                    aspect = a;
                }
            }

            float targetWidth = available;
            float targetHeight = targetWidth / aspect;
            float maxHeight = Math.max(32.0F, pageHeight - 2.0F * MARGIN - 16.0F);
            if (targetHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = targetHeight * aspect;
            }

            float x = Math.max(0.0F, (available - targetWidth) / 2.0F);
            List<LayoutPage.Op> ops = new ArrayList<>();
            ops.add(new LayoutPage.ImageOp(image.url(), image.alt(), image.blocked(), x, 0.0F, targetWidth, targetHeight));
            return List.of(new Box(4.0F, targetHeight, false, ops));
        }

        private List<Box> layoutVideo(Block.Video video) {
            float available = contentWidth;
            float aspect = 16.0F / 9.0F; // Standard widescreen 16:9 ratio

            float targetWidth = available;
            float targetHeight = targetWidth / aspect;
            float maxHeight = Math.max(32.0F, pageHeight - 2.0F * MARGIN - 16.0F);
            if (targetHeight > maxHeight && !video.cinema()) {
                targetHeight = maxHeight;
                targetWidth = targetHeight * aspect;
            }

            float x = Math.max(0.0F, (available - targetWidth) / 2.0F);
            List<LayoutPage.Op> ops = new ArrayList<>();
            ops.add(new LayoutPage.VideoOp(video.url(), video.title(), video.volume(), video.distance(), video.loop(), video.cinema(), video.blocked(), video.audioSource(), video.startEpoch(), x, 0.0F, targetWidth, targetHeight));
            return List.of(new Box(4.0F, targetHeight, false, ops));
        }

        private String truncate(String text, float maxWidth, int flags) {
            if (metrics.width(text, flags) <= maxWidth) {
                return text;
            }
            String result = text;
            while (result.length() > 1 && metrics.width(result + "...", flags) > maxWidth) {
                result = result.substring(0, result.length() - 1);
            }
            return result + "...";
        }
    }
}
