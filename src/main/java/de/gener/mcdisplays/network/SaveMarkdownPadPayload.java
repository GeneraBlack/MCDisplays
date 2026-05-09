package de.gener.mcdisplays.network;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record SaveMarkdownPadPayload(boolean mainHand, String title, String markdown) {
    public static void encode(SaveMarkdownPadPayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.mainHand());
        buffer.writeUtf(payload.title(), MarkdownPadItemData.maxTitleLength());
        buffer.writeUtf(payload.markdown(), MarkdownPadItemData.maxMarkdownLength());
    }

    public static SaveMarkdownPadPayload decode(FriendlyByteBuf buffer) {
        return new SaveMarkdownPadPayload(
            buffer.readBoolean(),
            buffer.readUtf(MarkdownPadItemData.maxTitleLength()),
            buffer.readUtf(MarkdownPadItemData.maxMarkdownLength())
        );
    }

    public static void handle(SaveMarkdownPadPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null) {
                return;
            }

            InteractionHand hand = payload.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(McDisplaysMod.MARKDOWN_PAD.get())) {
                return;
            }

            MarkdownPadItemData.write(stack, payload.title, payload.markdown);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}