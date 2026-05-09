package de.gener.mcdisplays.block;

import de.gener.mcdisplays.McDisplaysConfig;
import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.content.DisplayCluster;
import de.gener.mcdisplays.content.DisplayContentExtractor;
import de.gener.mcdisplays.content.DisplayDocument;
import de.gener.mcdisplays.content.DisplayTextLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import de.gener.mcdisplays.menu.DisplayPanelMenu;

public final class DisplayPanelBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final String TAG_SOURCE = "Source";
    private static final String TAG_PAGES = "Pages";
    private static final String TAG_TITLE = "Title";
    private static final String TAG_MANUAL_PAGE = "ManualPage";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_PRIVATE = "Private";
    private static final String TAG_GLOWING = "Glowing";
    private ItemStack sourceStack = ItemStack.EMPTY;
    private List<String> cachedPages = List.of(DisplayDocument.placeholder().pages().get(0));
    private String cachedTitle = DisplayDocument.placeholder().title();
    private int manualPageOffset;
    private int refreshTicks;
    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private boolean privateMode;
    private boolean glowingText;

    public DisplayPanelBlockEntity(BlockPos pos, BlockState blockState) {
        super(McDisplaysMod.DISPLAY_PANEL_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DisplayPanelBlockEntity blockEntity) {
        if (level.getBlockEntity(pos) != blockEntity || state != blockEntity.getBlockState()) {
            return;
        }

        blockEntity.refreshTicks++;
        if (blockEntity.refreshTicks >= McDisplaysConfig.refreshIntervalTicks()) {
            blockEntity.refreshTicks = 0;
            blockEntity.refreshFromSource();
        }
    }

    public boolean tryAcceptSupportedItem(Player player, InteractionHand hand) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (!DisplayContentExtractor.supports(heldStack) || level == null) {
            return false;
        }

        if (!canPlayerEdit(player)) {
            if (!level.isClientSide) {
                notifyAccessDenied(player);
            }
            return false;
        }

        DisplayPanelBlockEntity target = getOwningEntity();
        if (level.isClientSide) {
            return true;
        }

        claimOwnership(player);
        target.installSource(player, heldStack);
        return true;
    }

    public boolean tryApplyInkEffect(Player player, InteractionHand hand) {
        if (level == null) {
            return false;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        boolean enableGlow = heldStack.is(Items.GLOW_INK_SAC);
        boolean disableGlow = heldStack.is(Items.INK_SAC);
        if (!enableGlow && !disableGlow) {
            return false;
        }

        if (!canPlayerEdit(player)) {
            if (!level.isClientSide) {
                notifyAccessDenied(player);
            }
            return true;
        }

        if (level.isClientSide) {
            return true;
        }

        if (!setGlowing(player, enableGlow)) {
            return true;
        }

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }
        return true;
    }

    public boolean handleEmptyHand(Player player) {
        if (!player.isShiftKeyDown() || level == null || !player.getMainHandItem().isEmpty()) {
            return false;
        }

        DisplayPanelBlockEntity target = getCurrentSourceOwner();
        if (target == null || target.sourceStack.isEmpty()) {
            return false;
        }

        if (!canPlayerEdit(player)) {
            if (!level.isClientSide) {
                notifyAccessDenied(player);
            }
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        ItemStack ejected = target.sourceStack.copy();
        target.clearSource();
        if (!player.addItem(ejected)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, ejected);
        }
        return true;
    }

    public boolean openMenu(Player player) {
        if (level == null) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        NetworkHooks.openScreen(serverPlayer, this, worldPosition);
        return true;
    }

    public boolean tryTurnPage(Player player, BlockHitResult hit) {
        if (level == null || !McDisplaysConfig.allowManualPageTurning() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return false;
        }

        DisplayPanelBlockEntity target = getCurrentSourceOwner();
        if (target == null) {
            return false;
        }

        if (!canPlayerEdit(player)) {
            if (!level.isClientSide) {
                notifyAccessDenied(player);
            }
            return false;
        }

        DisplayCluster.Cluster cluster = DisplayCluster.find(level, worldPosition, getBlockState());
        int pageCount = target.getPageCount(cluster);
        if (pageCount <= 1) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        double horizontal = horizontalHit(hit, cluster);
        int delta = horizontal >= 0.0D ? 1 : -1;
        target.changePage(delta);
        return true;
    }

    public DisplayTextLayout.ScreenPage getPage(DisplayCluster.Cluster cluster) {
        DisplayPanelBlockEntity sourceOwner = getDocumentOwner(cluster);
        List<DisplayTextLayout.ScreenPage> pages = DisplayTextLayout.layout(sourceOwner.currentDocument(), cluster.width(), cluster.height());
        int pageIndex = pages.isEmpty() ? 0 : Math.floorMod(sourceOwner.manualPageOffset, pages.size());
        return pages.get(pageIndex);
    }

    public List<String> getSlice(DisplayCluster.Cluster cluster) {
        return DisplayTextLayout.slice(getPage(cluster), cluster.width(), cluster.height(), cluster.localX(), cluster.localY());
    }

    public boolean canPlayerEdit(@Nullable Player player) {
        if (player == null) {
            return false;
        }

        DisplayPanelBlockEntity settingsOwner = getSettingsOwnerOrSelf();
        return !settingsOwner.privateMode || settingsOwner.isOwnedBy(player);
    }

    public boolean canPlayerChangePrivacy(@Nullable Player player) {
        if (player == null) {
            return false;
        }

        DisplayPanelBlockEntity settingsOwner = getSettingsOwnerOrSelf();
        return settingsOwner.ownerUuid == null || settingsOwner.isOwnedBy(player);
    }

    public boolean isPrivateDisplay() {
        return getSettingsOwnerOrSelf().privateMode;
    }

    public boolean isGlowingDisplay() {
        return getSettingsOwnerOrSelf().glowingText;
    }

    public Component getOwnerDisplayName() {
        DisplayPanelBlockEntity settingsOwner = getSettingsOwnerOrSelf();
        if (settingsOwner.ownerUuid == null) {
            return Component.translatable("screen.mcdisplays.display_panel.owner.unclaimed_value");
        }

        return settingsOwner.ownerName.isBlank()
            ? Component.translatable("screen.mcdisplays.display_panel.owner.unknown_value")
            : Component.literal(settingsOwner.ownerName);
    }

    public void claimOwnership(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }

        getSettingsOwnerOrSelf().claimOwnershipInternal(player);
    }

    public boolean togglePrivacy(Player player) {
        if (level == null) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        if (!canPlayerChangePrivacy(player)) {
            notifyPrivacyDenied(player);
            return false;
        }

        DisplayPanelBlockEntity settingsOwner = getSettingsOwnerOrSelf();
        settingsOwner.claimOwnershipInternal(player);
        settingsOwner.privateMode = !settingsOwner.privateMode;
        settingsOwner.markUpdated();
        player.displayClientMessage(Component.translatable(settingsOwner.privateMode ? "message.mcdisplays.display_panel.private_enabled" : "message.mcdisplays.display_panel.private_disabled"), true);
        return true;
    }

    public void notifyAccessDenied(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        player.displayClientMessage(Component.translatable("message.mcdisplays.display_panel.locked", getOwnerDisplayName()), true);
    }

    private boolean setGlowing(Player player, boolean glowing) {
        DisplayPanelBlockEntity settingsOwner = getSettingsOwnerOrSelf();
        if (settingsOwner.glowingText == glowing) {
            return false;
        }

        settingsOwner.glowingText = glowing;
        settingsOwner.markUpdated();
        player.displayClientMessage(Component.translatable(glowing ? "message.mcdisplays.display_panel.glow_enabled" : "message.mcdisplays.display_panel.glow_disabled"), true);
        return true;
    }

    private int getPageCount(DisplayCluster.Cluster cluster) {
        DisplayPanelBlockEntity sourceOwner = getDocumentOwner(cluster);
        return DisplayTextLayout.layout(sourceOwner.currentDocument(), cluster.width(), cluster.height()).size();
    }

    private void installSource(Player player, ItemStack heldStack) {
        ItemStack previous = sourceStack.copy();
        sourceStack = heldStack.copy();
        sourceStack.setCount(1);
        manualPageOffset = 0;
        refreshFromSource();

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(1);
        }

        if (!previous.isEmpty() && !player.addItem(previous)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, previous);
        }
    }

    private void clearSource() {
        sourceStack = ItemStack.EMPTY;
        cachedTitle = DisplayDocument.placeholder().title();
        cachedPages = DisplayDocument.placeholder().pages();
        manualPageOffset = 0;
        markUpdated();
    }

    private void changePage(int delta) {
        manualPageOffset += delta;
        markUpdated();
    }

    private void refreshFromSource() {
        if (level == null || level.isClientSide) {
            return;
        }

        DisplayDocument document = sourceStack.isEmpty() ? DisplayDocument.placeholder() : DisplayContentExtractor.extract(level, sourceStack);
        if (!cachedTitle.equals(document.title()) || !cachedPages.equals(document.pages())) {
            cachedTitle = document.title();
            cachedPages = List.copyOf(document.pages());
            markUpdated();
        } else if (!sourceStack.isEmpty()) {
            setChanged();
        }
    }

    private DisplayDocument currentDocument() {
        return new DisplayDocument(cachedTitle, cachedPages);
    }

    private DisplayPanelBlockEntity getOwningEntity() {
        DisplayPanelBlockEntity currentOwner = getCurrentSourceOwner();
        return currentOwner != null ? currentOwner : this;
    }

    private DisplayPanelBlockEntity getSettingsOwnerOrSelf() {
        DisplayPanelBlockEntity settingsOwner = getSettingsOwner();
        return settingsOwner != null ? settingsOwner : this;
    }

    private double horizontalHit(BlockHitResult hit, DisplayCluster.Cluster cluster) {
        BlockPos pos = getBlockPos();
        double deltaX = hit.getLocation().x - (pos.getX() + 0.5D);
        double deltaZ = hit.getLocation().z - (pos.getZ() + 0.5D);
        return deltaX * cluster.facing().getCounterClockWise().getStepX() + deltaZ * cluster.facing().getCounterClockWise().getStepZ();
    }

    @Nullable
    private DisplayPanelBlockEntity getCurrentSourceOwner() {
        if (level == null) {
            return null;
        }

        DisplayCluster.Cluster cluster = DisplayCluster.find(level, worldPosition, getBlockState());
        return getCurrentSourceOwner(cluster);
    }

    @Nullable
    private DisplayPanelBlockEntity getCurrentSourceOwner(DisplayCluster.Cluster cluster) {
        return getClusterEntities(cluster).stream()
            .filter(blockEntity -> !blockEntity.sourceStack.isEmpty())
            .findFirst()
            .orElse(null);
    }

    @Nullable
    private DisplayPanelBlockEntity getSettingsOwner() {
        if (level == null) {
            return null;
        }

        return getSettingsOwner(DisplayCluster.find(level, worldPosition, getBlockState()));
    }

    @Nullable
    private DisplayPanelBlockEntity getSettingsOwner(DisplayCluster.Cluster cluster) {
        return getClusterEntities(cluster).stream().findFirst().orElse(null);
    }

    private List<DisplayPanelBlockEntity> getClusterEntities(DisplayCluster.Cluster cluster) {
        if (level == null) {
            return List.of();
        }

        return cluster.members().stream()
            .sorted(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getX()).thenComparingInt(pos -> pos.getZ()))
            .map(level::getBlockEntity)
            .filter(DisplayPanelBlockEntity.class::isInstance)
            .map(DisplayPanelBlockEntity.class::cast)
            .toList();
    }

    private DisplayPanelBlockEntity getDocumentOwner(DisplayCluster.Cluster cluster) {
        DisplayPanelBlockEntity sourceOwner = getCurrentSourceOwner(cluster);
        return sourceOwner != null ? sourceOwner : this;
    }

    private boolean isOwnedBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    private void claimOwnershipInternal(Player player) {
        String latestName = player.getGameProfile().getName();
        if (ownerUuid == null) {
            ownerUuid = player.getUUID();
            ownerName = latestName;
            markUpdated();
            return;
        }

        if (ownerUuid.equals(player.getUUID()) && !ownerName.equals(latestName)) {
            ownerName = latestName;
            markUpdated();
        }
    }

    private void notifyPrivacyDenied(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        player.displayClientMessage(Component.translatable("message.mcdisplays.display_panel.owner_only"), true);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void setSourceFromMenu(ItemStack stack) {
        if (stack.isEmpty()) {
            clearSource();
            return;
        }

        sourceStack = stack.copy();
        sourceStack.setCount(1);
        manualPageOffset = 0;
        refreshFromSource();
        markUpdated();
    }

    private ItemStack removeSourceFromMenu() {
        if (sourceStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = sourceStack.copy();
        clearSource();
        return removed;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container." + McDisplaysMod.MODID + ".display_panel");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DisplayPanelMenu(containerId, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getOwningEntity().sourceStack.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }

        return getOwningEntity().sourceStack;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) {
            return ItemStack.EMPTY;
        }

        return getOwningEntity().removeSourceFromMenu();
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) {
            return ItemStack.EMPTY;
        }

        return getOwningEntity().removeSourceFromMenu();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }

        getOwningEntity().setSourceFromMenu(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && DisplayContentExtractor.supports(stack);
    }

    @Override
    public void clearContent() {
        getOwningEntity().clearSource();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!sourceStack.isEmpty()) {
            tag.put(TAG_SOURCE, sourceStack.save(new CompoundTag()));
        }

        ListTag pageList = new ListTag();
        for (String page : cachedPages) {
            pageList.add(StringTag.valueOf(page));
        }
        tag.put(TAG_PAGES, pageList);
        tag.putString(TAG_TITLE, cachedTitle);
        tag.putInt(TAG_MANUAL_PAGE, manualPageOffset);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER, ownerUuid);
        }
        if (!ownerName.isBlank()) {
            tag.putString(TAG_OWNER_NAME, ownerName);
        }
        if (privateMode) {
            tag.putBoolean(TAG_PRIVATE, true);
        }
        if (glowingText) {
            tag.putBoolean(TAG_GLOWING, true);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        sourceStack = tag.contains(TAG_SOURCE, Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound(TAG_SOURCE)) : ItemStack.EMPTY;
        manualPageOffset = tag.getInt(TAG_MANUAL_PAGE);
        cachedTitle = tag.getString(TAG_TITLE);
        ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.getString(TAG_OWNER_NAME);
        privateMode = tag.getBoolean(TAG_PRIVATE);
        glowingText = tag.getBoolean(TAG_GLOWING);

        List<String> pages = new ArrayList<>();
        ListTag pageList = tag.getList(TAG_PAGES, Tag.TAG_STRING);
        for (int index = 0; index < pageList.size(); index++) {
            pages.add(pageList.getString(index));
        }

        if (pages.isEmpty()) {
            DisplayDocument placeholder = DisplayDocument.placeholder();
            cachedPages = placeholder.pages();
            if (cachedTitle.isBlank()) {
                cachedTitle = placeholder.title();
            }
        } else {
            cachedPages = List.copyOf(pages);
            if (cachedTitle.isBlank()) {
                cachedTitle = "Display";
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}