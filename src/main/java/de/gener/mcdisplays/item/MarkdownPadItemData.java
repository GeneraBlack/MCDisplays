package de.gener.mcdisplays.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.content.DisplayDocument;
import de.gener.mcdisplays.content.MarkdownToDisplayFormatter;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public final class MarkdownPadItemData {
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_MARKDOWN_LENGTH = 12000;

    public record MarkdownPadData(String title, String markdown) {
        public static final MarkdownPadData EMPTY = new MarkdownPadData("", "");
        public static final Codec<MarkdownPadData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("title", "").forGetter(MarkdownPadData::title),
            Codec.STRING.optionalFieldOf("markdown", "").forGetter(MarkdownPadData::markdown)
        ).apply(instance, MarkdownPadData::new));
        public static final StreamCodec<ByteBuf, MarkdownPadData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MarkdownPadData::title,
            ByteBufCodecs.STRING_UTF8, MarkdownPadData::markdown,
            MarkdownPadData::new
        );
    }

    private MarkdownPadItemData() {
    }

    public static String getTitle(ItemStack stack) {
        return stack.getOrDefault(McDisplaysMod.MARKDOWN_PAD_DATA.get(), MarkdownPadData.EMPTY).title().trim();
    }

    public static String getMarkdown(ItemStack stack) {
        return normalizeMarkdown(stack.getOrDefault(McDisplaysMod.MARKDOWN_PAD_DATA.get(), MarkdownPadData.EMPTY).markdown());
    }

    public static void write(ItemStack stack, String title, String markdown) {
        String sanitizedTitle = sanitizeTitle(title);
        String sanitizedMarkdown = sanitizeMarkdown(markdown);

        if (sanitizedTitle.isBlank() && sanitizedMarkdown.isBlank()) {
            stack.remove(McDisplaysMod.MARKDOWN_PAD_DATA.get());
        } else {
            stack.set(McDisplaysMod.MARKDOWN_PAD_DATA.get(), new MarkdownPadData(sanitizedTitle, sanitizedMarkdown));
        }

        if (sanitizedTitle.isBlank()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(sanitizedTitle));
        }
    }

    public static DisplayDocument toDisplayDocument(ItemStack stack) {
        return MarkdownToDisplayFormatter.toDocument(getTitle(stack), getMarkdown(stack));
    }

    public static int maxTitleLength() {
        return MAX_TITLE_LENGTH;
    }

    public static int maxMarkdownLength() {
        return MAX_MARKDOWN_LENGTH;
    }

    private static String sanitizeTitle(String title) {
        String sanitized = title == null ? "" : title.trim();
        return sanitized.length() <= MAX_TITLE_LENGTH ? sanitized : sanitized.substring(0, MAX_TITLE_LENGTH);
    }

    private static String sanitizeMarkdown(String markdown) {
        String normalized = normalizeMarkdown(markdown == null ? "" : markdown);
        return normalized.length() <= MAX_MARKDOWN_LENGTH ? normalized : normalized.substring(0, MAX_MARKDOWN_LENGTH);
    }

    private static String normalizeMarkdown(String markdown) {
        return markdown.replace("\r\n", "\n").replace('\r', '\n');
    }
}