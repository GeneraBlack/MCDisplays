package de.gener.mcdisplays.menu;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.content.DisplayContentExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DisplayPanelMenu extends AbstractContainerMenu {
    public static final int SOURCE_SLOT_X = 14;
    public static final int SOURCE_SLOT_Y = 79;
    public static final int PLAYER_INVENTORY_X = 33;
    public static final int PLAYER_INVENTORY_Y = 114;
    public static final int HOTBAR_Y = 172;
    private static final int SLOT_SPACING = 18;
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

        addSlot(new Slot(blockEntity, SOURCE_SLOT, SOURCE_SLOT_X, SOURCE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return blockEntity.canPlayerEdit(playerInventory.player) && blockEntity.canPlaceItem(getSlotIndex(), stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return blockEntity.canPlayerEdit(player);
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
        boolean canEdit = blockEntity.canPlayerEdit(player);

        if (index == SOURCE_SLOT) {
            if (!canEdit) {
                blockEntity.notifyAccessDenied(player);
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (DisplayContentExtractor.supports(stack)) {
            if (!canEdit) {
                blockEntity.notifyAccessDenied(player);
                return ItemStack.EMPTY;
            }

            if (!player.level().isClientSide) {
                blockEntity.claimOwnership(player);
            }

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
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == SOURCE_SLOT && !blockEntity.canPlayerEdit(player)) {
            blockEntity.notifyAccessDenied(player);
            return;
        }

        if (clickType == ClickType.QUICK_MOVE && slotId >= PLAYER_SLOT_START && slotId < HOTBAR_SLOT_END && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            if (slot.hasItem() && DisplayContentExtractor.supports(slot.getItem()) && !blockEntity.canPlayerEdit(player)) {
                blockEntity.notifyAccessDenied(player);
                return;
            }
        }

        if (!player.level().isClientSide && blockEntity.canPlayerEdit(player)) {
            if (slotId == SOURCE_SLOT) {
                blockEntity.claimOwnership(player);
            } else if (clickType == ClickType.QUICK_MOVE && slotId >= PLAYER_SLOT_START && slotId < HOTBAR_SLOT_END && slotId < slots.size()) {
                Slot slot = slots.get(slotId);
                if (slot.hasItem() && DisplayContentExtractor.supports(slot.getItem())) {
                    blockEntity.claimOwnership(player);
                }
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            return blockEntity.togglePrivacy(player);
        }

        return super.clickMenuButton(player, id);
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
                addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INVENTORY_X + column * SLOT_SPACING, PLAYER_INVENTORY_Y + row * SLOT_SPACING));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new Slot(playerInventory, slot, PLAYER_INVENTORY_X + slot * SLOT_SPACING, HOTBAR_Y));
        }
    }
}