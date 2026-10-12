package de.gener.mcdisplays.block;

import de.gener.mcdisplays.McDisplaysMod;
import de.gener.mcdisplays.content.DisplayCluster;
import de.gener.mcdisplays.menu.CinemaProjectorMenu;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CinemaProjectorBlockEntity extends BlockEntity implements MenuProvider {
    private static final String TAG_TARGET = "Target";
    private static final String TAG_WIDTH = "Width";
    private static final String TAG_HEIGHT = "Height";
    private static final String TAG_URL = "Url";
    private static final String TAG_PLAYING = "Playing";
    private static final String TAG_VOLUME = "Volume";
    private static final String TAG_DISTANCE = "Distance";
    private static final String TAG_LOOP = "Loop";
    private static final String TAG_AUDIO_PROJECTOR = "AudioFromProjector";

    @Nullable
    private BlockPos linkedScreenPos;
    private int linkedWidth;
    private int linkedHeight;
    private String url = "";
    private boolean playing;
    private int volume = 80;
    private int distance = 32;
    private boolean loop = true;
    private boolean audioFromProjector = true;
    private int checkTicks;

    public CinemaProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(McDisplaysMod.CINEMA_PROJECTOR_BLOCK_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CinemaProjectorBlockEntity entity) {
        if (level.isClientSide) {
            return;
        }

        entity.checkTicks++;
        if (entity.checkTicks < 20) {
            return;
        }
        entity.checkTicks = 0;

        if (entity.linkedScreenPos != null) {
            if (level.isLoaded(entity.linkedScreenPos)) {
                BlockState targetState = level.getBlockState(entity.linkedScreenPos);
                if (!(targetState.getBlock() instanceof DisplayPanelBlock)) {
                    entity.unlinkScreen();
                } else {
                    DisplayCluster.Cluster cluster = DisplayCluster.find(level, entity.linkedScreenPos, targetState);
                    BlockPos masterPos = cluster.origin();
                    if (level.getBlockEntity(masterPos) instanceof DisplayPanelBlockEntity masterDisplay) {
                        if (!masterDisplay.isCinemaActive()) {
                            entity.syncDisplayScreen();
                        }
                    }
                }
            }
        }
    }

    public void linkToScreen(BlockPos screenPos, int width, int height) {
        this.linkedScreenPos = screenPos;
        this.linkedWidth = width;
        this.linkedHeight = height;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(CinemaProjectorBlock.LINKED)) {
                level.setBlock(worldPosition, state.setValue(CinemaProjectorBlock.LINKED, true), Block.UPDATE_ALL);
            }
            syncDisplayScreen();
        }
        markUpdated();
    }

    public static String normalizeUrl(String raw) {
        return de.gener.mcdisplays.markdown.MediaPolicy.normalizeUrl(raw);
    }

    public void unlinkScreen() {
        if (linkedScreenPos != null && level != null) {
            BlockState targetState = level.getBlockState(linkedScreenPos);
            if (targetState.getBlock() instanceof DisplayPanelBlock) {
                DisplayCluster.Cluster cluster = DisplayCluster.find(level, linkedScreenPos, targetState);
                for (BlockPos member : cluster.members()) {
                    if (level.getBlockEntity(member) instanceof DisplayPanelBlockEntity display) {
                        display.clearCinemaFeed(worldPosition);
                    }
                }
            } else if (level.getBlockEntity(linkedScreenPos) instanceof DisplayPanelBlockEntity display) {
                display.clearCinemaFeed(worldPosition);
            }
        }
        this.linkedScreenPos = null;
        this.linkedWidth = 0;
        this.linkedHeight = 0;
        this.playing = false;
        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(CinemaProjectorBlock.LINKED) && state.hasProperty(CinemaProjectorBlock.PLAYING)) {
                level.setBlock(worldPosition, state.setValue(CinemaProjectorBlock.LINKED, false).setValue(CinemaProjectorBlock.PLAYING, false), Block.UPDATE_ALL);
            }
        }
        markUpdated();
    }

    public void updatePlayback(String newUrl, boolean newPlaying, int newVolume, int newDistance, boolean newLoop) {
        this.url = normalizeUrl(newUrl);
        this.volume = Math.clamp(newVolume, 0, 100);
        this.distance = Math.clamp(newDistance, 8, 128);
        this.loop = newLoop;
        this.playing = newPlaying && this.linkedScreenPos != null && !this.url.isBlank();

        if (level != null) {
            BlockState state = getBlockState();
            if (state.hasProperty(CinemaProjectorBlock.PLAYING)) {
                level.setBlock(worldPosition, state.setValue(CinemaProjectorBlock.PLAYING, this.playing), Block.UPDATE_ALL);
            }
            syncDisplayScreen();
        }
        markUpdated();
    }

    public void updateSettings(int newVolume, int newDistance, boolean newLoop, boolean newAudioFromProjector) {
        this.volume = Math.clamp(newVolume, 0, 100);
        this.distance = Math.clamp(newDistance, 8, 128);
        this.loop = newLoop;
        this.audioFromProjector = newAudioFromProjector;
        if (level != null && linkedScreenPos != null) {
            syncDisplayScreen();
        }
        markUpdated();
    }

    public boolean isAudioFromProjector() {
        return audioFromProjector;
    }

    public void setAudioFromProjector(boolean audioFromProjector) {
        this.audioFromProjector = audioFromProjector;
    }

    public void syncDisplayScreen() {
        if (level == null || linkedScreenPos == null || level.isClientSide) {
            return;
        }

        BlockState screenState = level.getBlockState(linkedScreenPos);
        if (!(screenState.getBlock() instanceof DisplayPanelBlock)) {
            return;
        }

        DisplayCluster.Cluster cluster = DisplayCluster.find(level, linkedScreenPos, screenState);
        BlockPos masterPos = cluster.origin();
        if (level.getBlockEntity(masterPos) instanceof DisplayPanelBlockEntity display) {
            if (this.playing && !this.url.isBlank()) {
                String videoTag = String.format("<video src=\"%s\" volume=\"%d\" distance=\"%d\" %saudio=\"%s\" cinema />",
                    this.url, this.volume, this.distance,
                    this.loop ? "loop " : "",
                    this.audioFromProjector ? "projector" : "screen");
                display.setCinemaFeed(worldPosition, "Cinema Video", videoTag);
            } else {
                String standby = String.format(
                    "# CINEMA READY\n**Status: Standby (%dx%d)**\n\n*Awaiting stream from Projector at [%d, %d, %d]*",
                    linkedWidth, linkedHeight, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()
                );
                display.setCinemaFeed(worldPosition, "Cinema Standby", standby);
            }
        }
    }

    public boolean openMenu(Player player) {
        if (level == null) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this, worldPosition);
            return true;
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mcdisplays.cinema_projector");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CinemaProjectorMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedScreenPos != null) {
            tag.putLong(TAG_TARGET, linkedScreenPos.asLong());
        }
        tag.putInt(TAG_WIDTH, linkedWidth);
        tag.putInt(TAG_HEIGHT, linkedHeight);
        tag.putString(TAG_URL, url);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putInt(TAG_VOLUME, volume);
        tag.putInt(TAG_DISTANCE, distance);
        tag.putBoolean(TAG_LOOP, loop);
        tag.putBoolean(TAG_AUDIO_PROJECTOR, audioFromProjector);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_TARGET)) {
            linkedScreenPos = BlockPos.of(tag.getLong(TAG_TARGET));
        } else {
            linkedScreenPos = null;
        }
        linkedWidth = tag.getInt(TAG_WIDTH);
        linkedHeight = tag.getInt(TAG_HEIGHT);
        url = tag.getString(TAG_URL);
        playing = tag.getBoolean(TAG_PLAYING);
        volume = tag.contains(TAG_VOLUME) ? tag.getInt(TAG_VOLUME) : 80;
        distance = tag.contains(TAG_DISTANCE) ? tag.getInt(TAG_DISTANCE) : 32;
        loop = !tag.contains(TAG_LOOP) || tag.getBoolean(TAG_LOOP);
        audioFromProjector = !tag.contains(TAG_AUDIO_PROJECTOR) || tag.getBoolean(TAG_AUDIO_PROJECTOR);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public BlockPos getLinkedScreenPos() {
        return linkedScreenPos;
    }

    public boolean isLinked() {
        return linkedScreenPos != null;
    }

    public int getLinkedWidth() {
        return linkedWidth;
    }

    public int getLinkedHeight() {
        return linkedHeight;
    }

    public String getUrl() {
        return url;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getVolume() {
        return volume;
    }

    public int getDistance() {
        return distance;
    }

    public boolean isLoop() {
        return loop;
    }
}
