package de.gener.mcdisplays.markdown;

import java.util.List;

/** Parsed Markdown document structure that the layout engine consumes. */
public sealed interface Block {
    enum Align { LEFT, CENTER, RIGHT }

    /** A run of text with uniform style. {@code color == -1} means "use the block's default color". */
    record Span(String text, int color, int flags) {
        public static final int BOLD = 1;
        public static final int ITALIC = 2;
        public static final int UNDERLINE = 4;
        public static final int STRIKE = 8;
        public static final int CODE = 16;
        public static final String LINE_BREAK = "\n";

        public boolean isBreak() {
            return LINE_BREAK.equals(text);
        }
    }

    /**
     * Headings and paragraphs (also list items and quote lines).
     *
     * @param level      heading level 1-6, 0 for normal text
     * @param indent     indentation in nesting steps (lists)
     * @param marker     list marker such as "•" or "1." (empty if none)
     * @param quoteDepth number of enclosing block quotes
     * @param tight      true if no vertical gap should precede the block (consecutive list items)
     */
    record Text(int level, List<Span> spans, Align align, int indent, String marker, int quoteDepth, boolean tight) implements Block {
    }

    /** Fenced or indented code. */
    record Code(List<String> lines, int quoteDepth) implements Block {
    }

    /** GFM table. Rows are lists of cells; the first row is the header when {@code header} is true. */
    record Table(List<List<List<Span>>> rows, List<Align> aligns, boolean header) implements Block {
    }

    record Rule() implements Block {
    }

    /** Image placeholder. {@code blocked} is set when the media policy rejects the URL. */
    record Image(String url, String alt, boolean blocked) implements Block {
    }

    /** Video stream block (YouTube, Twitch, CDN, MP4, WebM, HLS). */
    record Video(String url, String title, int volume, int distance, boolean loop, boolean cinema, boolean blocked, String audioSource, long startEpoch) implements Block {
        public Video(String url, String title, int volume, int distance, boolean loop, boolean cinema, boolean blocked, String audioSource) {
            this(url, title, volume, distance, loop, cinema, blocked, audioSource, 0L);
        }

        public Video(String url, String title, int volume, int distance, boolean loop, boolean cinema, boolean blocked) {
            this(url, title, volume, distance, loop, cinema, blocked, "projector", 0L);
        }
    }
}
