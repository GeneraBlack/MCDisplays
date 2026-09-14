package de.gener.mcdisplays.content;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.server.network.Filterable;
import net.minecraft.core.component.DataComponents;

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

        if (stack.is(McDisplaysMod.COLONY_DASHBOARD.get())) {
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

        if (stack.is(McDisplaysMod.COLONY_DASHBOARD.get())) {
            return ColonyDashboardExtractor.extract(level, stack);
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
        List<String> flattenedPages = new ArrayList<>();
        if (writtenBook) {
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content != null) {
                for (Filterable<Component> page : content.pages()) {
                    flattenedPages.add(page.raw().getString());
                }
            }
        } else {
            WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            if (content != null) {
                for (Filterable<String> page : content.pages()) {
                    flattenedPages.add(page.raw());
                }
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