package de.gener.mcdisplays.markdown;

/** Measures text for the layout engine. The client installs a real font-based implementation. */
public interface TextMetrics {
    /** Width in font pixels of {@code text} drawn with the given {@link Block.Span} flags at scale 1. */
    float width(String text, int flags);

    /** Height of one text line in font pixels. */
    int lineHeight();

    /** True for the approximate fallback (dedicated server, tests). */
    default boolean isApproximate() {
        return false;
    }

    TextMetrics APPROXIMATE = new TextMetrics() {
        @Override
        public float width(String text, int flags) {
            float total = 0.0F;
            for (int index = 0; index < text.length(); index++) {
                total += glyphAdvance(text.charAt(index));
                if ((flags & Block.Span.BOLD) != 0) {
                    total += 1.0F;
                }
            }
            return total;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public boolean isApproximate() {
            return true;
        }
    };

    /** Advance widths of the vanilla default font (including the 1px letter gap). */
    static float glyphAdvance(char character) {
        return switch (character) {
            case ' ' -> 4.0F;
            case '!', '.', ',', ':', ';', '|', 'i', '\'' -> 2.0F;
            case 'l', '`' -> 3.0F;
            case 'I', 't', '[', ']', '"', '*', '(', ')', '{', '}' -> 4.0F;
            case 'f', 'k', '<', '>' -> 5.0F;
            case '@', '~' -> 7.0F;
            default -> 6.0F;
        };
    }

    /** Holder for the active metrics used when rendering. */
    final class Holder {
        private static volatile TextMetrics active = APPROXIMATE;

        private Holder() {
        }

        public static TextMetrics get() {
            return active;
        }

        public static void install(TextMetrics metrics) {
            active = metrics == null ? APPROXIMATE : metrics;
        }
    }
}
