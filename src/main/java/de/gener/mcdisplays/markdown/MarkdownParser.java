package de.gener.mcdisplays.markdown;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

/**
 * CommonMark + GFM (tables, strikethrough, task lists) parser producing {@link Block}s.
 * Inline color tags such as {@code [red]text[/]} are an additional inline feature on top of standard Markdown.
 * Alignment follows Markdown: GFM table column alignment, plus HTML-in-Markdown {@code <center>} / {@code align="center"}.
 */
public final class MarkdownParser {
    private static final Parser PARSER = Parser.builder()
        .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()))
        .build();
    private static final Pattern COLOR_TAG = Pattern.compile("\\[([^\\[\\]\\n]{1,40})]");
    private static final Pattern HTML_OPEN = Pattern.compile("<\\s*(center|div|p|h[1-6])\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_CLOSE = Pattern.compile("</\\s*(center|div|p|h[1-6])\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ALIGN = Pattern.compile("align\\s*=\\s*[\"']?(left|center|right)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMG = Pattern.compile("<\\s*img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_VIDEO = Pattern.compile("<\\s*video\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_SRC = Pattern.compile("src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_ALT = Pattern.compile("alt\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_TITLE = Pattern.compile("title\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_VOLUME = Pattern.compile("volume\\s*=\\s*[\"']?(\\d+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_DISTANCE = Pattern.compile("distance\\s*=\\s*[\"']?(\\d+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_AUDIO = Pattern.compile("audio\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_START_EPOCH = Pattern.compile("startEpoch\\s*=\\s*[\"']?(\\d+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_LOOP = Pattern.compile("\\bloop\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ATTR_CINEMA = Pattern.compile("\\b(cinema|fullscreen)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BR = Pattern.compile("<\\s*br\\s*/?\\s*>", Pattern.CASE_INSENSITIVE);
    private static final int LINK_COLOR = 0x55AAFF;

    private final List<Block> blocks = new ArrayList<>();
    private Block.Align htmlAlign = Block.Align.LEFT;

    private MarkdownParser() {
    }

    public static List<Block> parse(String markdown) {
        MarkdownParser parser = new MarkdownParser();
        parser.walk(PARSER.parse(markdown == null ? "" : markdown), 0, 0, null);
        return List.copyOf(parser.blocks);
    }

    public static boolean isVideoUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT).trim();
        if (u.contains("youtube.com/") || u.contains("youtu.be/") || u.contains("twitch.tv/")) {
            return true;
        }
        return u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".m3u8") || u.endsWith(".mov")
            || u.endsWith(".mkv") || u.endsWith(".ogg") || u.endsWith(".ogv") || u.endsWith(".mpd");
    }

    private Block.Align currentAlign(Block.Align forced) {
        return forced != null ? forced : htmlAlign;
    }

    private void walk(Node parent, int quoteDepth, int indent, Block.Align forcedAlign) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                blocks.add(new Block.Text(heading.getLevel(), inlines(heading), currentAlign(forcedAlign), indent, "", quoteDepth, false));
            } else if (node instanceof Paragraph paragraph) {
                addParagraph(paragraph, quoteDepth, indent, forcedAlign, "", false);
            } else if (node instanceof BulletList || node instanceof OrderedList) {
                addList((ListBlock) node, quoteDepth, indent, forcedAlign);
            } else if (node instanceof BlockQuote) {
                walk(node, quoteDepth + 1, indent, forcedAlign);
            } else if (node instanceof FencedCodeBlock fenced) {
                blocks.add(new Block.Code(codeLines(fenced.getLiteral()), quoteDepth));
            } else if (node instanceof IndentedCodeBlock indented) {
                blocks.add(new Block.Code(codeLines(indented.getLiteral()), quoteDepth));
            } else if (node instanceof ThematicBreak) {
                blocks.add(new Block.Rule());
            } else if (node instanceof TableBlock table) {
                addTable(table);
            } else if (node instanceof HtmlBlock html) {
                addHtml(html, quoteDepth, indent);
            }
        }
    }

    private void addParagraph(Paragraph paragraph, int quoteDepth, int indent, Block.Align forcedAlign, String marker, boolean tight) {
        Node only = paragraph.getFirstChild();
        if (only instanceof org.commonmark.node.Image image && only.getNext() == null && marker.isEmpty()) {
            String dest = image.getDestination();
            String alt = plainText(image);
            if (isVideoUrl(dest) || "video".equalsIgnoreCase(alt)) {
                blocks.add(new Block.Video(dest, alt.isBlank() || "video".equalsIgnoreCase(alt) ? "Video" : alt, 100, 32, true, false, !MediaPolicy.isAllowed(dest)));
                return;
            }
            blocks.add(new Block.Image(dest, alt, !MediaPolicy.isAllowed(dest)));
            return;
        }

        if (marker.isEmpty()) {
            String rawText = plainText(paragraph).trim();
            if ((rawText.startsWith("http://") || rawText.startsWith("https://")) && isVideoUrl(rawText)) {
                blocks.add(new Block.Video(rawText, "Video", 100, 32, true, false, !MediaPolicy.isAllowed(rawText)));
                return;
            }
        }

        blocks.add(new Block.Text(0, inlines(paragraph), currentAlign(forcedAlign), indent, marker, quoteDepth, tight));
    }

    private void addList(ListBlock list, int quoteDepth, int indent, Block.Align forcedAlign) {
        int number = list instanceof OrderedList ordered && ordered.getMarkerStartNumber() != null ? ordered.getMarkerStartNumber() : 1;
        boolean first = true;
        for (Node item = list.getFirstChild(); item instanceof ListItem; item = item.getNext()) {
            String marker = list instanceof OrderedList ? number++ + "." : "\u2022";
            for (Node c = item.getFirstChild(); c != null; c = c.getNext()) {
                if (c instanceof TaskListItemMarker taskMarker) {
                    marker = taskMarker.isChecked() ? "[x]" : "[ ]";
                    break;
                }
            }
            boolean markerPending = true;
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof TaskListItemMarker) {
                    continue;
                }
                if (child instanceof Paragraph paragraph) {
                    addParagraph(paragraph, quoteDepth, indent + 1, forcedAlign, markerPending ? marker : "", list.isTight() && !first && markerPending);
                    markerPending = false;
                } else if (child instanceof BulletList || child instanceof OrderedList) {
                    addList((ListBlock) child, quoteDepth, indent + 1, forcedAlign);
                } else {
                    walkSingle(child, quoteDepth, indent + 1, forcedAlign);
                    markerPending = false;
                }
            }
            if (markerPending) {
                blocks.add(new Block.Text(0, List.of(new Block.Span("", -1, 0)), currentAlign(forcedAlign), indent + 1, marker, quoteDepth, false));
            }
            first = false;
        }
    }

    private void walkSingle(Node child, int quoteDepth, int indent, Block.Align forcedAlign) {
        if (child instanceof FencedCodeBlock fenced) {
            blocks.add(new Block.Code(codeLines(fenced.getLiteral()), quoteDepth));
        } else if (child instanceof IndentedCodeBlock indented) {
            blocks.add(new Block.Code(codeLines(indented.getLiteral()), quoteDepth));
        } else if (child instanceof BlockQuote) {
            walk(child, quoteDepth + 1, indent, forcedAlign);
        } else if (child instanceof Heading heading) {
            blocks.add(new Block.Text(heading.getLevel(), inlines(heading), currentAlign(forcedAlign), indent, "", quoteDepth, false));
        } else if (child instanceof TableBlock table) {
            addTable(table);
        } else if (child instanceof ThematicBreak) {
            blocks.add(new Block.Rule());
        }
    }

    private void addTable(TableBlock table) {
        List<List<List<Block.Span>>> rows = new ArrayList<>();
        List<Block.Align> aligns = new ArrayList<>();
        boolean header = false;
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            boolean isHead = section instanceof org.commonmark.ext.gfm.tables.TableHead;
            for (Node rowNode = section.getFirstChild(); rowNode instanceof TableRow; rowNode = rowNode.getNext()) {
                List<List<Block.Span>> cells = new ArrayList<>();
                int column = 0;
                for (Node cellNode = rowNode.getFirstChild(); cellNode instanceof TableCell cell; cellNode = cellNode.getNext()) {
                    cells.add(inlines(cell));
                    if (aligns.size() <= column) {
                        aligns.add(toAlign(cell.getAlignment()));
                    } else if (aligns.get(column) == Block.Align.LEFT && cell.getAlignment() != null) {
                        aligns.set(column, toAlign(cell.getAlignment()));
                    }
                    column++;
                }
                rows.add(cells);
                header |= isHead;
            }
        }
        if (!rows.isEmpty()) {
            blocks.add(new Block.Table(rows, aligns, header));
        }
    }

    private static Block.Align toAlign(TableCell.Alignment alignment) {
        if (alignment == null) {
            return Block.Align.LEFT;
        }
        return switch (alignment) {
            case CENTER -> Block.Align.CENTER;
            case RIGHT -> Block.Align.RIGHT;
            default -> Block.Align.LEFT;
        };
    }

    private void addHtml(HtmlBlock html, int quoteDepth, int indent) {
        String literal = html.getLiteral();

        Matcher image = HTML_IMG.matcher(literal);
        while (image.find()) {
            Matcher src = HTML_ATTR_SRC.matcher(image.group());
            if (src.find()) {
                Matcher alt = HTML_ATTR_ALT.matcher(image.group());
                blocks.add(new Block.Image(src.group(1), alt.find() ? alt.group(1) : "", !MediaPolicy.isAllowed(src.group(1))));
            }
        }

        Matcher video = HTML_VIDEO.matcher(literal);
        while (video.find()) {
            String tag = video.group();
            Matcher src = HTML_ATTR_SRC.matcher(tag);
            if (src.find()) {
                String url = src.group(1);
                Matcher title = HTML_ATTR_TITLE.matcher(tag);
                Matcher vol = HTML_ATTR_VOLUME.matcher(tag);
                Matcher dist = HTML_ATTR_DISTANCE.matcher(tag);
                Matcher audioMatch = HTML_ATTR_AUDIO.matcher(tag);
                boolean loop = HTML_ATTR_LOOP.matcher(tag).find();
                boolean cinema = HTML_ATTR_CINEMA.matcher(tag).find();

                int volume = 100;
                if (vol.find()) {
                    try {
                        volume = Integer.parseInt(vol.group(1));
                    } catch (Exception ignored) {
                    }
                }
                int distance = 32;
                if (dist.find()) {
                    try {
                        distance = Integer.parseInt(dist.group(1));
                    } catch (Exception ignored) {
                    }
                }
                String titleStr = title.find() ? title.group(1) : "Video";
                String audioSource = audioMatch.find() ? audioMatch.group(1) : "projector";
                Matcher startEpochMatch = HTML_ATTR_START_EPOCH.matcher(tag);
                long startEpoch = 0L;
                if (startEpochMatch.find()) {
                    try {
                        startEpoch = Long.parseLong(startEpochMatch.group(1));
                    } catch (Exception ignored) {
                    }
                }

                blocks.add(new Block.Video(url, titleStr, volume, distance, loop, cinema, !MediaPolicy.isAllowed(url), audioSource, startEpoch));
            }
        }

        Matcher open = HTML_OPEN.matcher(literal);
        if (open.find()) {
            String tag = open.group(1).toLowerCase(Locale.ROOT);
            Matcher align = HTML_ALIGN.matcher(open.group(2));
            if (tag.equals("center")) {
                htmlAlign = Block.Align.CENTER;
            } else if (align.find()) {
                htmlAlign = Block.Align.valueOf(align.group(1).toUpperCase(Locale.ROOT));
            }
        }

        String cleaned = HTML_VIDEO.matcher(HTML_IMG.matcher(literal).replaceAll("")).replaceAll("");
        String inner = HTML_BR.matcher(cleaned).replaceAll("\n").replaceAll("(?s)<[^>]*>", "").trim();
        if (!inner.isBlank()) {
            walk(PARSER.parse(inner), quoteDepth, indent, null);
        }

        if (HTML_CLOSE.matcher(literal).find()) {
            htmlAlign = Block.Align.LEFT;
        }
    }

    private static List<String> codeLines(String literal) {
        String text = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        return List.of(text.split("\n", -1));
    }

    // ---- inline handling ----

    private static String plainText(Node node) {
        StringBuilder builder = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text text) {
                builder.append(text.getLiteral());
            } else if (child instanceof Code code) {
                builder.append(code.getLiteral());
            } else {
                builder.append(plainText(child));
            }
        }
        return builder.toString();
    }

    private static List<Block.Span> inlines(Node container) {
        InlineState state = new InlineState();
        state.visitChildren(container);
        state.flush();
        return state.finish();
    }

    private static final class InlineState {
        private final List<Block.Span> spans = new ArrayList<>();
        private final Deque<Integer> colors = new ArrayDeque<>();
        private final StringBuilder pending = new StringBuilder();
        private int flags;

        int color() {
            return colors.isEmpty() ? -1 : colors.peek();
        }

        void emit(String text, int extraFlags) {
            if (!text.isEmpty()) {
                spans.add(new Block.Span(text, color(), flags | extraFlags));
            }
        }

        void flush() {
            if (pending.length() == 0) {
                return;
            }
            String text = pending.toString();
            pending.setLength(0);

            Matcher matcher = COLOR_TAG.matcher(text);
            int last = 0;
            while (matcher.find()) {
                int tag = MarkdownColors.classifyColorTag(matcher.group(1));
                if (tag == MarkdownColors.TAG_NONE) {
                    continue;
                }
                emit(text.substring(last, matcher.start()), 0);
                last = matcher.end();
                if (tag == MarkdownColors.TAG_POP) {
                    colors.poll();
                } else if (tag == MarkdownColors.TAG_RESET) {
                    colors.clear();
                } else {
                    colors.push(tag);
                }
            }
            emit(text.substring(last), 0);
        }

        void visitChildren(Node parent) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof org.commonmark.node.Text text) {
                    pending.append(text.getLiteral());
                    continue;
                }
                flush();
                if (node instanceof Code code) {
                    emit(code.getLiteral(), Block.Span.CODE);
                } else if (node instanceof Emphasis) {
                    withFlag(node, Block.Span.ITALIC);
                } else if (node instanceof StrongEmphasis) {
                    withFlag(node, Block.Span.BOLD);
                } else if (node instanceof Strikethrough) {
                    withFlag(node, Block.Span.STRIKE);
                } else if (node instanceof Link) {
                    boolean addColor = colors.isEmpty();
                    if (addColor) {
                        colors.push(LINK_COLOR);
                    }
                    withFlag(node, Block.Span.UNDERLINE);
                    if (addColor) {
                        colors.poll();
                    }
                } else if (node instanceof org.commonmark.node.Image image) {
                    emit("[" + plainText(image) + "]", 0);
                } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                    spans.add(new Block.Span(Block.Span.LINE_BREAK, -1, 0));
                } else if (node instanceof HtmlInline inline) {
                    if (HTML_BR.matcher(inline.getLiteral()).matches()) {
                        spans.add(new Block.Span(Block.Span.LINE_BREAK, -1, 0));
                    }
                } else if (node instanceof TaskListItemMarker marker) {
                    emit(marker.isChecked() ? "[x] " : "[ ] ", 0);
                } else {
                    visitChildren(node);
                    flush();
                }
            }
            flush();
        }

        private void withFlag(Node node, int flag) {
            int previous = flags;
            flags |= flag;
            visitChildren(node);
            flush();
            flags = previous;
        }

        List<Block.Span> finish() {
            List<Block.Span> merged = new ArrayList<>();
            for (Block.Span span : spans) {
                if (!merged.isEmpty()) {
                    Block.Span previous = merged.get(merged.size() - 1);
                    if (!previous.isBreak() && !span.isBreak() && previous.color() == span.color() && previous.flags() == span.flags()) {
                        merged.set(merged.size() - 1, new Block.Span(previous.text() + span.text(), span.color(), span.flags()));
                        continue;
                    }
                }
                merged.add(span);
            }
            return merged;
        }
    }
}
