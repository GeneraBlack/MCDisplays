package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class DisplayContentExtractor {
    private DisplayContentExtractor() {
    }

    public static boolean supports(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (stack.is(McDisplaysMod.MARKDOWN_PAD.get())) {
            return true;
        }

        if (stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK)) {
            return true;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MinecoloniesReflectionCompat.isSupported(itemId);
    }

    public static DisplayDocument extract(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return DisplayDocument.placeholder();
        }

        if (stack.is(McDisplaysMod.MARKDOWN_PAD.get())) {
            return MarkdownPadItemData.toDisplayDocument(stack);
        }

        if (stack.is(Items.WRITTEN_BOOK)) {
            return extractBook(stack, true);
        }

        if (stack.is(Items.WRITABLE_BOOK)) {
            return extractBook(stack, false);
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (MinecoloniesReflectionCompat.isResourceScroll(itemId)) {
            return MinecoloniesReflectionCompat.extractResourceScroll(level, stack);
        }

        if (MinecoloniesReflectionCompat.isClipboard(itemId)) {
            return MinecoloniesReflectionCompat.extractClipboard(level, stack);
        }

        McDisplaysMod.LOGGER.debug("Unsupported display source {}", itemId);
        return DisplayDocument.message("Unsupported Source", "Only books, Markdown Pads, resource scrolls and clipboards can be displayed.");
    }

    private static DisplayDocument extractBook(ItemStack stack, boolean writtenBook) {
        CompoundTag tag = stack.getTag();
        List<String> flattenedPages = new ArrayList<>();
        if (tag != null && tag.contains("pages", Tag.TAG_LIST)) {
            ListTag pages = tag.getList("pages", Tag.TAG_STRING);
            for (int index = 0; index < pages.size(); index++) {
                String page = pages.getString(index);
                flattenedPages.add(writtenBook ? parseWrittenPage(page) : clean(page));
            }
        }

        if (flattenedPages.isEmpty()) {
            flattenedPages = List.of("This book is empty.");
        }

        return new DisplayDocument(clean(stack.getHoverName().getString()), flattenedPages);
    }

    private static String parseWrittenPage(String rawPage) {
        try {
            Component component = Component.Serializer.fromJson(rawPage);
            if (component != null) {
                return clean(component.getString());
            }
        } catch (Exception ignored) {
            // Older or malformed written book pages can still fall back to plain text.
        }

        return clean(rawPage);
    }

    static String clean(String text) {
        return ChatFormatting.stripFormatting(text == null ? "" : text).replace("\t", "    ");
    }
}