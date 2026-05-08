package de.gener.mcdisplays.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class McDisplaysClientHooks {
    private McDisplaysClientHooks() {
    }

    public static void openMarkdownPadEditor(InteractionHand hand, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new MarkdownPadScreen(hand, stack.copy()));
    }
}