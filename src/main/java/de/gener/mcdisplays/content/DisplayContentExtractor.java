package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
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

        if (stack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null || stack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
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

        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            return extractBook(stack, written.getPages(false));
        }

        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return extractBook(stack, writable.getPages(false).toList());
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

    private static DisplayDocument extractBook(ItemStack stack, List<?> pages) {
        List<String> flattenedPages = new ArrayList<>(pages.size());
        for (Object page : pages) {
            Object content = page;
            try {
                content = page.getClass().getMethod("get", boolean.class).invoke(page, false);
            } catch (ReflectiveOperationException ignored) {
                // Some page wrapper types stringify directly instead of exposing a getter.
            }

            if (content instanceof Component component) {
                flattenedPages.add(clean(component.getString()));
            } else {
                flattenedPages.add(clean(String.valueOf(content)));
            }
        }

        if (flattenedPages.isEmpty()) {
            flattenedPages = List.of("This book is empty.");
        }

        return new DisplayDocument(clean(stack.getHoverName().getString()), flattenedPages);
    }

    static String clean(String text) {
        return ChatFormatting.stripFormatting(text == null ? "" : text).replace("\t", "    ");
    }
}