package de.gener.mcdisplays.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.gener.mcdisplays.McDisplaysMod;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A colony-linked item that displays live MineColonies data on display panels.
 * <p>
 * The item stores a colony ID, dimension, and display mode in its data component. Players
 * link it to a colony by sneak-right-clicking a MineColonies building (Town Hall
 * or any hut), and cycle through modes by right-clicking in hand. Insert into a
 * display panel to render the selected view in the world.
 */
public class ColonyDashboardItem extends Item {
    public record ColonyDashboardData(int colonyId, String dimension, String mode) {
        public static final ColonyDashboardData EMPTY = new ColonyDashboardData(-1, "", DashboardMode.CITIZENS.id());
        public static final Codec<ColonyDashboardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("colony", -1).forGetter(ColonyDashboardData::colonyId),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(ColonyDashboardData::dimension),
            Codec.STRING.optionalFieldOf("mode", DashboardMode.CITIZENS.id()).forGetter(ColonyDashboardData::mode)
        ).apply(instance, ColonyDashboardData::new));
        public static final StreamCodec<ByteBuf, ColonyDashboardData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ColonyDashboardData::colonyId,
            ByteBufCodecs.STRING_UTF8, ColonyDashboardData::dimension,
            ByteBufCodecs.STRING_UTF8, ColonyDashboardData::mode,
            ColonyDashboardData::new
        );
    }

    public ColonyDashboardItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            Level level = context.getLevel();
            BlockPos clickedPos = context.getClickedPos();

            if (isColonyBuilding(level, clickedPos)) {
                if (!level.isClientSide) {
                    linkToColony(player, context.getItemInHand(), level, clickedPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            DashboardMode current = getMode(stack);
            DashboardMode next = current.next();
            setMode(stack, next);
            player.displayClientMessage(
                Component.translatable("message.mcdisplays.colony_dashboard.mode_changed",
                    Component.translatable("item.mcdisplays.colony_dashboard.mode." + next.id())),
                true
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DashboardMode mode = getMode(stack);
        tooltip.add(Component.translatable("item.mcdisplays.colony_dashboard.tooltip.mode",
            Component.translatable("item.mcdisplays.colony_dashboard.mode." + mode.id()))
            .withStyle(ChatFormatting.GRAY));

        ColonyDashboardData data = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
        if (data.colonyId() != -1) {
            tooltip.add(Component.translatable("item.mcdisplays.colony_dashboard.tooltip.colony", data.colonyId())
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.mcdisplays.colony_dashboard.tooltip.unlinked")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.translatable("item.mcdisplays.colony_dashboard.tooltip.hint")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- static accessors for extraction ----

    public static DashboardMode getMode(ItemStack stack) {
        ColonyDashboardData data = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
        return DashboardMode.fromId(data.mode());
    }

    public static int getColonyId(ItemStack stack) {
        ColonyDashboardData data = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
        return data.colonyId();
    }

    @Nullable
    public static String getDimension(ItemStack stack) {
        ColonyDashboardData data = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
        String dimension = data.dimension();
        return dimension.isBlank() ? null : dimension;
    }

    // ---- internal helpers ----

    private static void setMode(ItemStack stack, DashboardMode mode) {
        ColonyDashboardData oldData = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
        stack.set(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), new ColonyDashboardData(oldData.colonyId(), oldData.dimension(), mode.id()));
    }

    private void linkToColony(Player player, ItemStack stack, Level level, BlockPos pos) {
        try {
            Class<?> colonyManagerClass = Class.forName("com.minecolonies.api.colony.IColonyManager");
            Object colonyManager = colonyManagerClass.getMethod("getInstance").invoke(null);

            Object colony = tryFindColony(colonyManager, level, pos);
            if (colony == null) {
                player.displayClientMessage(
                    Component.translatable("message.mcdisplays.colony_dashboard.no_colony"),
                    true
                );
                return;
            }

            Object colonyId = findAndInvoke(colony, "getID");
            Object colonyName = findAndInvoke(colony, "getName");

            ColonyDashboardData oldData = stack.getOrDefault(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), ColonyDashboardData.EMPTY);
            stack.set(McDisplaysMod.COLONY_DASHBOARD_DATA.get(), new ColonyDashboardData(((Number) colonyId).intValue(), level.dimension().location().toString(), oldData.mode()));

            player.displayClientMessage(
                Component.translatable("message.mcdisplays.colony_dashboard.linked", String.valueOf(colonyName)),
                true
            );
        } catch (ReflectiveOperationException | RuntimeException exception) {
            McDisplaysMod.LOGGER.warn("Failed to link Colony Dashboard to colony", exception);
            player.displayClientMessage(
                Component.translatable("message.mcdisplays.colony_dashboard.link_failed"),
                true
            );
        }
    }

    @Nullable
    private static Object tryFindColony(Object colonyManager, Level level, BlockPos pos) {
        for (String methodName : List.of("getIColony", "getClosestIColony", "getClosestColony")) {
            try {
                for (Method method : colonyManager.getClass().getMethods()) {
                    if (method.getName().equals(methodName) && method.getParameterCount() == 2) {
                        Object result = method.invoke(colonyManager, level, pos);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try next method name.
            }
        }
        return null;
    }

    @Nullable
    private static Object findAndInvoke(Object target, String methodName) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                return method.invoke(target);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
    }

    private static boolean isColonyBuilding(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockId.getNamespace().equals("minecolonies") && blockId.getPath().startsWith("blockhut");
    }
}
