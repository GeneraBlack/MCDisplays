package de.gener.mcdisplays.markdown;

import java.util.List;

/**
 * One laid-out display page. All coordinates are in font pixels from the top-left corner of the whole display;
 * a display block is {@link MarkdownLayout#PX_PER_BLOCK} pixels wide and high.
 */
public record LayoutPage(List<Op> ops, int index, int total, int widthPx, int heightPx) {
    public sealed interface Op permits TextOp, RectOp, ImageOp, VideoOp {
    }

    /** A single styled text run. {@link #renderCache} lets the renderer keep prepared text between frames. */
    public static final class TextOp implements Op {
        public final String text;
        public final int flags;
        public final int color;
        public final float x;
        public final float y;
        public final float scale;
        public Object renderCache;

        public TextOp(String text, int flags, int color, float x, float y, float scale) {
            this.text = text;
            this.flags = flags;
            this.color = color;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        public TextOp moved(float dx, float dy) {
            return new TextOp(text, flags, color, x + dx, y + dy, scale);
        }
    }

    /** A filled rectangle with an ARGB color. */
    public record RectOp(float x, float y, float width, float height, int argb) implements Op {
        public RectOp moved(float dx, float dy) {
            return new RectOp(x + dx, y + dy, width, height, argb);
        }
    }

    /** A remote or local image drawn on the display. */
    public record ImageOp(String url, String alt, boolean blocked, float x, float y, float width, float height) implements Op {
        public ImageOp moved(float dx, float dy) {
            return new ImageOp(url, alt, blocked, x + dx, y + dy, width, height);
        }
    }

    /** A video stream drawn on the display. */
    public record VideoOp(String url, String title, int volume, int distance, boolean loop, boolean cinema, boolean blocked, String audioSource, float x, float y, float width, float height) implements Op {
        public VideoOp(String url, String title, int volume, int distance, boolean loop, boolean cinema, boolean blocked, float x, float y, float width, float height) {
            this(url, title, volume, distance, loop, cinema, blocked, "projector", x, y, width, height);
        }

        public VideoOp moved(float dx, float dy) {
            return new VideoOp(url, title, volume, distance, loop, cinema, blocked, audioSource, x + dx, y + dy, width, height);
        }
    }
}
