package de.gener.mcdisplays.network;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.block.CinemaProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateCinemaProjectorPayload(
    BlockPos projectorPos,
    int action,
    String url,
    boolean loop,
    int volume,
    int distance
) implements CustomPacketPayload {
    public static final int ACTION_PLAY = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_UNLINK = 2;

    public static final Type<UpdateCinemaProjectorPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(McDisplaysMod.MODID, "update_cinema_projector")
    );

    public static final StreamCodec<FriendlyByteBuf, UpdateCinemaProjectorPayload> STREAM_CODEC = StreamCodec.ofMember(
        UpdateCinemaProjectorPayload::encode,
        UpdateCinemaProjectorPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(projectorPos);
        buffer.writeVarInt(action);
        buffer.writeUtf(url, 2048);
        buffer.writeBoolean(loop);
        buffer.writeVarInt(volume);
        buffer.writeVarInt(distance);
    }

    public static UpdateCinemaProjectorPayload decode(FriendlyByteBuf buffer) {
        return new UpdateCinemaProjectorPayload(
            buffer.readBlockPos(),
            buffer.readVarInt(),
            buffer.readUtf(2048),
            buffer.readBoolean(),
            buffer.readVarInt(),
            buffer.readVarInt()
        );
    }

    public static void handle(UpdateCinemaProjectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }

            if (player.distanceToSqr(
                payload.projectorPos.getX() + 0.5D,
                payload.projectorPos.getY() + 0.5D,
                payload.projectorPos.getZ() + 0.5D
            ) > 64.0D) {
                return;
            }

            if (player.level().getBlockEntity(payload.projectorPos) instanceof CinemaProjectorBlockEntity projector) {
                if (payload.action == ACTION_PLAY) {
                    projector.updatePlayback(payload.url, true, payload.volume, payload.distance, payload.loop);
                } else if (payload.action == ACTION_STOP) {
                    projector.updatePlayback(payload.url, false, payload.volume, payload.distance, payload.loop);
                } else if (payload.action == ACTION_UNLINK) {
                    projector.unlinkScreen();
                }
            }
        });
    }
}
