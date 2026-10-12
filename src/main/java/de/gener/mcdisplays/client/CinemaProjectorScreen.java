package de.gener.mcdisplays.client;

import de.gener.mcdisplays.block.CinemaProjectorBlockEntity;
import de.gener.mcdisplays.client.video.ClientVideoManager;
import de.gener.mcdisplays.menu.CinemaProjectorMenu;
import de.gener.mcdisplays.network.UpdateCinemaProjectorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CinemaProjectorScreen extends AbstractContainerScreen<CinemaProjectorMenu> {
    private static final int SCREEN_WIDTH = 380;
    private static final int SCREEN_HEIGHT = 262;

    private EditBox urlBox;
    private Button playButton;
    private Button stopButton;
    private Button loopButton;
    private Button unlinkButton;
    private VolumeSlider volumeSlider;
    private DistanceSlider distanceSlider;

    private int currentVolume = 80;
    private int currentDistance = 32;
    private boolean currentLoop = true;

    public CinemaProjectorScreen(CinemaProjectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SCREEN_WIDTH;
        this.imageHeight = SCREEN_HEIGHT;
        this.titleLabelX = 14;
        this.titleLabelY = 10;
    }

    @Override
    protected void init() {
        super.init();
        CinemaProjectorBlockEntity projector = menu.blockEntity();
        this.currentVolume = projector.getVolume();
        this.currentDistance = projector.getDistance();
        this.currentLoop = projector.isLoop();

        int left = leftPos;
        int top = topPos;

        // 1. URL Input Row
        int urlY = top + 92;
        urlBox = addRenderableWidget(new EditBox(font, left + 14, urlY, 252, 20, Component.translatable("gui.mcdisplays.cinema_projector.url")));
        urlBox.setMaxLength(2048);
        urlBox.setHint(Component.translatable("gui.mcdisplays.cinema_projector.url_hint"));
        urlBox.setValue(projector.getUrl());

        // Paste button
        addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.paste"), button -> pasteClipboard())
                .bounds(left + 272, urlY, 52, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.paste_tooltip")))
                .build()
        );

        // Clear button
        addRenderableWidget(
            Button.builder(Component.literal("X"), button -> urlBox.setValue(""))
                .bounds(left + 330, urlY, 36, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.clear_tooltip")))
                .build()
        );

        // 2. Acoustics & Sliders
        int controlsY = top + 150;
        double volInitial = Mth.clamp(currentVolume / 100.0D, 0.0D, 1.0D);
        volumeSlider = addRenderableWidget(new VolumeSlider(left + 14, controlsY, 170, 20, volInitial));

        double distInitial = Mth.clamp((currentDistance - 8.0D) / (128.0D - 8.0D), 0.0D, 1.0D);
        distanceSlider = addRenderableWidget(new DistanceSlider(left + 196, controlsY, 170, 20, distInitial));

        int subControlsY = top + 178;
        loopButton = addRenderableWidget(
            Button.builder(getLoopText(), button -> toggleLoop())
                .bounds(left + 14, subControlsY, 120, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.loop_tooltip")))
                .build()
        );

        // 3. Bottom Action Row
        int actionY = top + 226;
        playButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.play"), button -> handlePlay())
                .bounds(left + 14, actionY, 92, 24)
                .build()
        );

        stopButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.stop"), button -> handleStop())
                .bounds(left + 112, actionY, 92, 24)
                .build()
        );

        unlinkButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.unlink"), button -> handleUnlink())
                .bounds(left + 210, actionY, 84, 24)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.unlink_tooltip")))
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.close"), button -> onClose())
                .bounds(left + 300, actionY, 66, 24)
                .build()
        );

        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        CinemaProjectorBlockEntity projector = menu.blockEntity();
        boolean linked = projector.isLinked();
        boolean playing = projector.isPlaying();

        if (playButton != null) {
            playButton.active = linked && !urlBox.getValue().trim().isEmpty();
        }
        if (stopButton != null) {
            stopButton.active = linked && playing;
        }
        if (unlinkButton != null) {
            unlinkButton.active = linked;
        }
        if (loopButton != null) {
            loopButton.setMessage(getLoopText());
        }
    }

    private Component getLoopText() {
        return Component.translatable("gui.mcdisplays.cinema_projector.loop_state", currentLoop ? Component.translatable("gui.mcdisplays.cinema_projector.on") : Component.translatable("gui.mcdisplays.cinema_projector.off"));
    }

    private void toggleLoop() {
        currentLoop = !currentLoop;
        if (loopButton != null) {
            loopButton.setMessage(getLoopText());
        }
    }

    private void pasteClipboard() {
        if (minecraft != null) {
            String clipboard = minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isBlank()) {
                String clean = CinemaProjectorBlockEntity.normalizeUrl(clipboard);
                urlBox.setValue(clean);
            }
        }
    }

    private void handlePlay() {
        CinemaProjectorBlockEntity projector = menu.blockEntity();
        String normalized = CinemaProjectorBlockEntity.normalizeUrl(urlBox.getValue());
        urlBox.setValue(normalized);
        PacketDistributor.sendToServer(new UpdateCinemaProjectorPayload(
            projector.getBlockPos(),
            UpdateCinemaProjectorPayload.ACTION_PLAY,
            normalized,
            currentLoop,
            currentVolume,
            currentDistance
        ));
    }

    private void handleStop() {
        CinemaProjectorBlockEntity projector = menu.blockEntity();
        PacketDistributor.sendToServer(new UpdateCinemaProjectorPayload(
            projector.getBlockPos(),
            UpdateCinemaProjectorPayload.ACTION_STOP,
            urlBox.getValue().trim(),
            currentLoop,
            currentVolume,
            currentDistance
        ));
    }

    private void handleUnlink() {
        CinemaProjectorBlockEntity projector = menu.blockEntity();
        PacketDistributor.sendToServer(new UpdateCinemaProjectorPayload(
            projector.getBlockPos(),
            UpdateCinemaProjectorPayload.ACTION_UNLINK,
            "",
            false,
            currentVolume,
            currentDistance
        ));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        CinemaProjectorBlockEntity projector = menu.blockEntity();

        // Screen background base
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF518151D);
        drawBorder(guiGraphics, left, top, imageWidth, imageHeight, 0xFF4A3E5C);

        // Header section panel
        guiGraphics.fill(left + 6, top + 6, left + imageWidth - 6, top + 76, 0xDD25202E);
        drawBorder(guiGraphics, left + 6, top + 6, imageWidth - 12, 70, 0xFF5D4F75);

        // Status badges in header
        boolean linked = projector.isLinked();
        boolean playing = projector.isPlaying();

        // Link badge (110px)
        int linkBadgeColor = linked ? 0xFF2E7D32 : 0xFFC62828;
        int linkBadgeBorder = linked ? 0xFF81C784 : 0xFFE57373;
        guiGraphics.fill(left + 14, top + 26, left + 124, top + 42, linkBadgeColor);
        drawBorder(guiGraphics, left + 14, top + 26, 110, 16, linkBadgeBorder);
        Component linkText = Component.translatable(linked ? "gui.mcdisplays.cinema_projector.status_linked" : "gui.mcdisplays.cinema_projector.status_unlinked");
        guiGraphics.drawCenteredString(font, linkText, left + 69, top + 30, 0xFFFFFF);

        // Playback badge (110px)
        int playBadgeColor = playing ? 0xFF1565C0 : (linked ? 0xFFF57F17 : 0xFF424242);
        int playBadgeBorder = playing ? 0xFF64B5F6 : (linked ? 0xFFFFD54F : 0xFF757575);
        guiGraphics.fill(left + 130, top + 26, left + 240, top + 42, playBadgeColor);
        drawBorder(guiGraphics, left + 130, top + 26, 110, 16, playBadgeBorder);
        Component playText = Component.translatable(playing ? "gui.mcdisplays.cinema_projector.state_playing" : (linked ? "gui.mcdisplays.cinema_projector.state_standby" : "gui.mcdisplays.cinema_projector.state_idle"));
        guiGraphics.drawCenteredString(font, playText, left + 185, top + 30, 0xFFFFFF);

        // Engine badge (120px)
        boolean engineOk = ClientVideoManager.isWaterMediaAvailable();
        int engineBadgeColor = engineOk ? 0xFF00695C : 0xFFAD1457;
        int engineBadgeBorder = engineOk ? 0xFF4DB6AC : 0xFFF06292;
        guiGraphics.fill(left + 246, top + 26, left + 366, top + 42, engineBadgeColor);
        drawBorder(guiGraphics, left + 246, top + 26, 120, 16, engineBadgeBorder);
        Component engineText = Component.translatable(engineOk ? "gui.mcdisplays.cinema_projector.engine_ready" : "gui.mcdisplays.cinema_projector.engine_missing");
        guiGraphics.drawCenteredString(font, engineText, left + 306, top + 30, 0xFFFFFF);

        // Screen Target details
        if (linked && projector.getLinkedScreenPos() != null) {
            BlockPos tPos = projector.getLinkedScreenPos();
            String details = String.format("%dx%d Screen Cluster @ [%d, %d, %d]",
                projector.getLinkedWidth(), projector.getLinkedHeight(),
                tPos.getX(), tPos.getY(), tPos.getZ());
            guiGraphics.drawString(font, details, left + 14, top + 48, 0xFF81C784, false);
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.screen_scale_info"), left + 14, top + 60, 0xFFB0BEC5, false);
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.no_screen_hint1"), left + 14, top + 48, 0xFFEF9A9A, false);
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.no_screen_hint2"), left + 14, top + 60, 0xFFB0BEC5, false);
        }

        // Stream URL Area background
        guiGraphics.fill(left + 6, top + 82, left + imageWidth - 6, top + 120, 0xBB201B27);
        drawBorder(guiGraphics, left + 6, top + 82, imageWidth - 12, 38, 0xFF433954);

        // Sliders & Acoustics Area background
        guiGraphics.fill(left + 6, top + 126, left + imageWidth - 6, top + 210, 0xBB201B27);
        drawBorder(guiGraphics, left + 6, top + 126, imageWidth - 12, 84, 0xFF433954);

        // Acoustics header and spatial info
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.spatial_hint"), left + 14, top + 134, 0xFFCE93D8, false);
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.spatial_subhint"), left + 142, top + 184, 0xFF90A4AE, false);

        // Action row background
        guiGraphics.fill(left + 6, top + 216, left + imageWidth - 6, top + imageHeight - 6, 0xDD25202E);
        drawBorder(guiGraphics, left + 6, top + 216, imageWidth - 12, imageHeight - 222, 0xFF5D4F75);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFF3E5F5, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, color);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private class VolumeSlider extends AbstractSliderButton {
        public VolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.mcdisplays.cinema_projector.volume_val", (int) Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            currentVolume = (int) Math.round(value * 100);
        }
    }

    private class DistanceSlider extends AbstractSliderButton {
        public DistanceSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int dist = (int) Math.round(8 + value * (128 - 8));
            setMessage(Component.translatable("gui.mcdisplays.cinema_projector.distance_val", dist));
        }

        @Override
        protected void applyValue() {
            currentDistance = (int) Math.round(8 + value * (128 - 8));
        }
    }
}
