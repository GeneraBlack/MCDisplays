package de.gener.mcdisplays.network;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveMarkdownPadPayload(boolean mainHand, String title, String markdown) implements CustomPacketPayload {
    public static final Type<SaveMarkdownPadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "save_markdown_pad"));
    public static final StreamCodec<FriendlyByteBuf, SaveMarkdownPadPayload> STREAM_CODEC = StreamCodec.ofMember(SaveMarkdownPadPayload::encode, SaveMarkdownPadPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(this.mainHand());
        buffer.writeUtf(this.title(), MarkdownPadItemData.maxTitleLength());
        buffer.writeUtf(this.markdown(), MarkdownPadItemData.maxMarkdownLength());
    }

    public static SaveMarkdownPadPayload decode(FriendlyByteBuf buffer) {
        return new SaveMarkdownPadPayload(
            buffer.readBoolean(),
            buffer.readUtf(MarkdownPadItemData.maxTitleLength()),
            buffer.readUtf(MarkdownPadItemData.maxMarkdownLength())
        );
    }

    public static void handle(SaveMarkdownPadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
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
    }
}