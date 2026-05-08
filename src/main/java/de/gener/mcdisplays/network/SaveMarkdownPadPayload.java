package de.gener.mcdisplays.network;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveMarkdownPadPayload(boolean mainHand, String title, String markdown) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SaveMarkdownPadPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "save_markdown_pad")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveMarkdownPadPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        SaveMarkdownPadPayload::mainHand,
        ByteBufCodecs.STRING_UTF8,
        SaveMarkdownPadPayload::title,
        ByteBufCodecs.STRING_UTF8,
        SaveMarkdownPadPayload::markdown,
        SaveMarkdownPadPayload::new
    );

    @Override
    public Type<SaveMarkdownPadPayload> type() {
        return TYPE;
    }

    public static void handle(SaveMarkdownPadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
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