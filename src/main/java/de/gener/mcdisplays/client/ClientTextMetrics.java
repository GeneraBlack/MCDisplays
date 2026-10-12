package de.gener.mcdisplays.client;

import de.gener.mcdisplays.markdown.Block;
import de.gener.mcdisplays.markdown.LayoutPage;
import de.gener.mcdisplays.markdown.TextMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Client-side font metrics and styled text helpers shared by the display renderer and the editor preview. */
public final class ClientTextMetrics implements TextMetrics {
    public static final ClientTextMetrics INSTANCE = new ClientTextMetrics();

    private ClientTextMetrics() {
    }

    @Override
    public float width(String text, int flags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return APPROXIMATE.width(text, flags);
        }
        return mc.font.width(component(text, flags, 0xFFFFFF));
    }

    @Override
    public int lineHeight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return APPROXIMATE.lineHeight();
        }
        return mc.font.lineHeight;
    }

    public static MutableComponent component(String text, int flags, int color) {
        return Component.literal(text).withStyle(style -> style
            .withColor(color & 0xFFFFFF)
            .withBold((flags & Block.Span.BOLD) != 0)
            .withItalic((flags & Block.Span.ITALIC) != 0)
            .withUnderlined((flags & Block.Span.UNDERLINE) != 0)
            .withStrikethrough((flags & Block.Span.STRIKE) != 0));
    }

    /** Prepared text for one op, cached on the op so it is not rebuilt every frame. */
    public record Prepared(MutableComponent component, FormattedCharSequence sequence) {
    }

    public static Prepared prepare(LayoutPage.TextOp op) {
        if (op.renderCache instanceof Prepared prepared) {
            return prepared;
        }
        MutableComponent component = component(op.text, op.flags, op.color);
        Prepared prepared = new Prepared(component, component.getVisualOrderText());
        op.renderCache = prepared;
        return prepared;
    }
}
