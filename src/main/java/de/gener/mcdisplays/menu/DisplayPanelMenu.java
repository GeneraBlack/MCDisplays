package de.gener.mcdisplays.menu;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.content.DisplayContentExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DisplayPanelMenu extends AbstractContainerMenu {
    private static final int SOURCE_SLOT = 0;
    private static final int PLAYER_SLOT_START = 1;
    private static final int PLAYER_SLOT_END = 28;
    private static final int HOTBAR_SLOT_START = 28;
    private static final int HOTBAR_SLOT_END = 37;

    private final DisplayPanelBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public DisplayPanelMenu(int containerId, Inventory playerInventory, BlockPos blockPos) {
        this(containerId, playerInventory, requireBlockEntity(playerInventory.player.level(), blockPos));
    }

    public DisplayPanelMenu(int containerId, Inventory playerInventory, DisplayPanelBlockEntity blockEntity) {
        super(McDisplaysMod.DISPLAY_PANEL_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        addSlot(new Slot(blockEntity, SOURCE_SLOT, 80, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return blockEntity.canPlaceItem(getSlotIndex(), stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    public DisplayPanelBlockEntity blockEntity() {
        return blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == SOURCE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (DisplayContentExtractor.supports(stack)) {
            if (!moveItemStackTo(stack, SOURCE_SLOT, SOURCE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_SLOT_START) {
            if (!moveItemStackTo(stack, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_SLOT_START, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, McDisplaysMod.DISPLAY_PANEL.get());
    }

    private static DisplayPanelBlockEntity requireBlockEntity(Level level, BlockPos blockPos) {
        if (level.getBlockEntity(blockPos) instanceof DisplayPanelBlockEntity blockEntity) {
            return blockEntity;
        }

        throw new IllegalStateException("Missing display panel block entity at " + blockPos);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new Slot(playerInventory, slot, 8 + slot * 18, 142));
        }
    }
}