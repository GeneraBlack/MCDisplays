package de.gener.mcdisplays.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

public record CinemaRemoteData(@Nullable BlockPos targetPos, int width, int height) {
    public static final CinemaRemoteData EMPTY = new CinemaRemoteData(null, 0, 0);

    public static final Codec<CinemaRemoteData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.optionalFieldOf("target").forGetter(d -> Optional.ofNullable(d.targetPos())),
        Codec.INT.optionalFieldOf("width", 0).forGetter(CinemaRemoteData::width),
        Codec.INT.optionalFieldOf("height", 0).forGetter(CinemaRemoteData::height)
    ).apply(instance, (pos, w, h) -> new CinemaRemoteData(pos.orElse(null), w, h)));

    public static final StreamCodec<ByteBuf, CinemaRemoteData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBoolean(data.targetPos() != null);
            if (data.targetPos() != null) {
                buf.writeLong(data.targetPos().asLong());
            }
            buf.writeInt(data.width());
            buf.writeInt(data.height());
        },
        buf -> {
            boolean hasPos = buf.readBoolean();
            BlockPos pos = hasPos ? BlockPos.of(buf.readLong()) : null;
            int w = buf.readInt();
            int h = buf.readInt();
            return new CinemaRemoteData(pos, w, h);
        }
    );

    public boolean hasTarget() {
        return targetPos != null;
    }
}
