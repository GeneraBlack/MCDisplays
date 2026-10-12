package de.gener.mcdisplays.item;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.block.CinemaProjectorBlockEntity;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.content.DisplayCluster;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CinemaRemoteItem extends Item {
    public CinemaRemoteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState state = level.getBlockState(pos);

        // 1. Linking to a Display Panel
        if (state.getBlock() instanceof DisplayPanelBlock) {
            DisplayCluster.Cluster cluster = DisplayCluster.find(level, pos, state);
            if (!level.isClientSide && player != null) {
                CinemaRemoteData data = new CinemaRemoteData(cluster.origin(), cluster.width(), cluster.height());
                stack.set(McDisplaysMod.CINEMA_REMOTE_DATA.get(), data);
                player.displayClientMessage(
                    Component.translatable(
                        "message.mcdisplays.cinema_remote.linked_screen",
                        cluster.width(),
                        cluster.height(),
                        cluster.origin().getX(),
                        cluster.origin().getY(),
                        cluster.origin().getZ()
                    ).withStyle(ChatFormatting.GREEN),
                    true
                );
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 2. Connecting the Remote's target to a Cinema Projector block
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CinemaProjectorBlockEntity projector) {
            CinemaRemoteData data = stack.getOrDefault(McDisplaysMod.CINEMA_REMOTE_DATA.get(), CinemaRemoteData.EMPTY);
            if (!data.hasTarget()) {
                if (!level.isClientSide && player != null) {
                    player.displayClientMessage(
                        Component.translatable("message.mcdisplays.cinema_remote.no_target")
                            .withStyle(ChatFormatting.RED),
                        true
                    );
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!level.isClientSide && player != null) {
                projector.linkToScreen(data.targetPos(), data.width(), data.height());
                player.displayClientMessage(
                    Component.translatable(
                        "message.mcdisplays.cinema_projector.connected_screen",
                        data.width(),
                        data.height(),
                        data.targetPos().getX(),
                        data.targetPos().getY(),
                        data.targetPos().getZ()
                    ).withStyle(ChatFormatting.AQUA),
                    true
                );
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CinemaRemoteData data = stack.getOrDefault(McDisplaysMod.CINEMA_REMOTE_DATA.get(), CinemaRemoteData.EMPTY);
        if (data.hasTarget()) {
            tooltip.add(
                Component.translatable(
                    "tooltip.mcdisplays.cinema_remote.target",
                    data.width(),
                    data.height(),
                    data.targetPos().getX(),
                    data.targetPos().getY(),
                    data.targetPos().getZ()
                ).withStyle(ChatFormatting.GOLD)
            );
        } else {
            tooltip.add(Component.translatable("tooltip.mcdisplays.cinema_remote.unlinked").withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.mcdisplays.cinema_remote.hint_screen").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.mcdisplays.cinema_remote.hint_projector").withStyle(ChatFormatting.DARK_GRAY));
    }
}
