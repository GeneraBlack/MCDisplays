package de.gener.mcdisplays.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.content.Ae2CpuMonitorExtractor;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Links to an Applied Energistics 2 network (sneak-right-click any block of the network)
 * and displays the status of all crafting CPUs of that network on a display panel.
 * <p>
 * The link (dimension + block position) and the last known state are stored in a data
 * component, so the display survives restarts and can still show the last known state
 * while the linked chunk is not loaded.
 */
public class Ae2CpuMonitorItem extends Item {
    public record Ae2MonitorData(String dimension, long pos, List<String> cachePages, long cacheTime) {
        public static final Ae2MonitorData EMPTY = new Ae2MonitorData("", 0L, List.of(), 0L);
        public static final Codec<Ae2MonitorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(Ae2MonitorData::dimension),
            Codec.LONG.optionalFieldOf("pos", 0L).forGetter(Ae2MonitorData::pos),
            Codec.STRING.listOf().optionalFieldOf("cache", List.of()).forGetter(Ae2MonitorData::cachePages),
            Codec.LONG.optionalFieldOf("cache_time", 0L).forGetter(Ae2MonitorData::cacheTime)
        ).apply(instance, Ae2MonitorData::new));
        public static final StreamCodec<ByteBuf, Ae2MonitorData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Ae2MonitorData::dimension,
            ByteBufCodecs.VAR_LONG, Ae2MonitorData::pos,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Ae2MonitorData::cachePages,
            ByteBufCodecs.VAR_LONG, Ae2MonitorData::cacheTime,
            Ae2MonitorData::new
        );

        public boolean linked() {
            return !dimension.isBlank();
        }

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    public Ae2CpuMonitorItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static Ae2MonitorData getData(ItemStack stack) {
        return stack.getOrDefault(McDisplaysMod.AE2_MONITOR_DATA.get(), Ae2MonitorData.EMPTY);
    }

    public static void setData(ItemStack stack, Ae2MonitorData data) {
        stack.set(McDisplaysMod.AE2_MONITOR_DATA.get(), data);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        if (!Ae2CpuMonitorExtractor.isAe2Loaded()) {
            player.displayClientMessage(Component.translatable("message.mcdisplays.ae2_cpu_monitor.ae2_missing"), true);
            return InteractionResult.sidedSuccess(false);
        }

        BlockPos pos = context.getClickedPos();
        int cpuCount = Ae2CpuMonitorExtractor.countCpus(level, pos);
        if (cpuCount < 0) {
            player.displayClientMessage(Component.translatable("message.mcdisplays.ae2_cpu_monitor.no_network"), true);
            return InteractionResult.sidedSuccess(false);
        }

        setData(context.getItemInHand(), new Ae2MonitorData(level.dimension().location().toString(), pos.asLong(), List.of(), 0L));
        player.displayClientMessage(Component.translatable("message.mcdisplays.ae2_cpu_monitor.linked", cpuCount), true);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Ae2MonitorData data = getData(stack);
        if (data.linked()) {
            BlockPos pos = data.blockPos();
            tooltip.add(Component.translatable("item.mcdisplays.ae2_cpu_monitor.tooltip.linked",
                pos.getX(), pos.getY(), pos.getZ(), data.dimension()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.mcdisplays.ae2_cpu_monitor.tooltip.unlinked")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.mcdisplays.ae2_cpu_monitor.tooltip.hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
