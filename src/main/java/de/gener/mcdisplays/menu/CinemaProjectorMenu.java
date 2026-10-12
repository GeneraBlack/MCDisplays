package de.gener.mcdisplays.menu;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.block.CinemaProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CinemaProjectorMenu extends AbstractContainerMenu {
    private final CinemaProjectorBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public CinemaProjectorMenu(int containerId, Inventory playerInventory, BlockPos blockPos) {
        this(containerId, playerInventory, requireBlockEntity(playerInventory.player.level(), blockPos));
    }

    public CinemaProjectorMenu(int containerId, Inventory playerInventory, CinemaProjectorBlockEntity blockEntity) {
        super(McDisplaysMod.CINEMA_PROJECTOR_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public CinemaProjectorBlockEntity blockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, McDisplaysMod.CINEMA_PROJECTOR.get());
    }

    private static CinemaProjectorBlockEntity requireBlockEntity(Level level, BlockPos blockPos) {
        if (level.getBlockEntity(blockPos) instanceof CinemaProjectorBlockEntity blockEntity) {
            return blockEntity;
        }
        throw new IllegalStateException("Missing cinema projector block entity at " + blockPos);
    }
}
