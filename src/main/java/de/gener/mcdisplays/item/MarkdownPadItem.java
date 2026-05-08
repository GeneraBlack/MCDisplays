package de.gener.mcdisplays.item;

import de.gener.mcdisplays.client.McDisplaysClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MarkdownPadItem extends Item {
    public MarkdownPadItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            McDisplaysClientHooks.openMarkdownPadEditor(hand, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}