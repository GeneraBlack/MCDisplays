package de.gener.mcdisplays.item;

import de.gener.mcdisplays.content.DisplayDocument;
import de.gener.mcdisplays.content.MarkdownToDisplayFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MarkdownPadItemData {
    private static final String ROOT_TAG = "MarkdownPad";
    private static final String TAG_TITLE = "Title";
    private static final String TAG_MARKDOWN = "Markdown";
    private static final int MAX_TITLE_LENGTH = 64;
    private static final int MAX_MARKDOWN_LENGTH = 12000;

    private MarkdownPadItemData() {
    }

    public static String getTitle(ItemStack stack) {
        return readPadTag(stack).getString(TAG_TITLE).trim();
    }

    public static String getMarkdown(ItemStack stack) {
        return normalizeMarkdown(readPadTag(stack).getString(TAG_MARKDOWN));
    }

    public static void write(ItemStack stack, String title, String markdown) {
        String sanitizedTitle = sanitizeTitle(title);
        String sanitizedMarkdown = sanitizeMarkdown(markdown);

        CompoundTag rootTag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        CompoundTag padTag = rootTag.contains(ROOT_TAG, Tag.TAG_COMPOUND) ? rootTag.getCompound(ROOT_TAG) : new CompoundTag();

        if (sanitizedTitle.isBlank()) {
            padTag.remove(TAG_TITLE);
        } else {
            padTag.putString(TAG_TITLE, sanitizedTitle);
        }

        if (sanitizedMarkdown.isBlank()) {
            padTag.remove(TAG_MARKDOWN);
        } else {
            padTag.putString(TAG_MARKDOWN, sanitizedMarkdown);
        }

        if (padTag.isEmpty()) {
            rootTag.remove(ROOT_TAG);
        } else {
            rootTag.put(ROOT_TAG, padTag);
        }

        stack.setTag(rootTag.isEmpty() ? null : rootTag);

        if (sanitizedTitle.isBlank()) {
            stack.resetHoverName();
        } else {
            stack.setHoverName(Component.literal(sanitizedTitle));
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

    private static CompoundTag readPadTag(ItemStack stack) {
        CompoundTag rootTag = stack.getTag();
        if (rootTag == null) {
            return new CompoundTag();
        }
        return rootTag.contains(ROOT_TAG, Tag.TAG_COMPOUND) ? rootTag.getCompound(ROOT_TAG) : new CompoundTag();
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