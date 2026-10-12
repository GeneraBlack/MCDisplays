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
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 320;

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
        this.titleLabelX = 16;
        this.titleLabelY = 12;
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

        // 1. URL Input Row (Y = top + 104)
        int urlY = top + 104;
        urlBox = addRenderableWidget(new EditBox(font, left + 16, urlY, 338, 20, Component.translatable("gui.mcdisplays.cinema_projector.url")));
        urlBox.setMaxLength(2048);
        urlBox.setHint(Component.translatable("gui.mcdisplays.cinema_projector.url_hint"));
        urlBox.setValue(projector.getUrl());

        // Paste button
        addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.paste"), button -> pasteClipboard())
                .bounds(left + 360, urlY, 62, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.paste_tooltip")))
                .build()
        );

        // Clear button
        addRenderableWidget(
            Button.builder(Component.literal("X"), button -> urlBox.setValue(""))
                .bounds(left + 428, urlY, 34, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.clear_tooltip")))
                .build()
        );

        // 2. Acoustics & Sliders (Y = top + 188)
        int controlsY = top + 188;
        double volInitial = Mth.clamp(currentVolume / 100.0D, 0.0D, 1.0D);
        volumeSlider = addRenderableWidget(new VolumeSlider(left + 16, controlsY, 218, 20, volInitial));

        double distInitial = Mth.clamp((currentDistance - 8.0D) / (128.0D - 8.0D), 0.0D, 1.0D);
        distanceSlider = addRenderableWidget(new DistanceSlider(left + 244, controlsY, 218, 20, distInitial));

        int subControlsY = top + 216;
        loopButton = addRenderableWidget(
            Button.builder(getLoopText(), button -> toggleLoop())
                .bounds(left + 16, subControlsY, 140, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.loop_tooltip")))
                .build()
        );

        // 3. Bottom Action Row (Y = top + 274)
        int actionY = top + 274;
        playButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.play"), button -> handlePlay())
                .bounds(left + 25, actionY, 100, 26)
                .build()
        );

        stopButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.stop"), button -> handleStop())
                .bounds(left + 135, actionY, 100, 26)
                .build()
        );

        unlinkButton = addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.unlink"), button -> handleUnlink())
                .bounds(left + 245, actionY, 100, 26)
                .tooltip(Tooltip.create(Component.translatable("gui.mcdisplays.cinema_projector.unlink_tooltip")))
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("gui.mcdisplays.cinema_projector.close"), button -> onClose())
                .bounds(left + 355, actionY, 100, 26)
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

        // Outer Screen base
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF6131118);
        drawBorder(guiGraphics, left, top, imageWidth, imageHeight, 0xFF4A3E5C);

        // Header Panel
        guiGraphics.fill(left + 8, top + 8, left + imageWidth - 8, top + 78, 0xDD1E1925);
        drawBorder(guiGraphics, left + 8, top + 8, imageWidth - 16, 70, 0xFF5D4F75);

        // Status badges in header
        boolean linked = projector.isLinked();
        boolean playing = projector.isPlaying();

        // 1. Link badge (130px)
        int linkBadgeColor = linked ? 0xFF2E7D32 : 0xFFC62828;
        int linkBadgeBorder = linked ? 0xFF81C784 : 0xFFE57373;
        guiGraphics.fill(left + 16, top + 26, left + 146, top + 44, linkBadgeColor);
        drawBorder(guiGraphics, left + 16, top + 26, 130, 18, linkBadgeBorder);
        Component linkText = Component.translatable(linked ? "gui.mcdisplays.cinema_projector.status_linked" : "gui.mcdisplays.cinema_projector.status_unlinked");
        guiGraphics.drawCenteredString(font, linkText, left + 81, top + 31, 0xFFFFFF);

        // 2. Playback badge (130px)
        int playBadgeColor = playing ? 0xFF1565C0 : (linked ? 0xFFF57F17 : 0xFF424242);
        int playBadgeBorder = playing ? 0xFF64B5F6 : (linked ? 0xFFFFD54F : 0xFF757575);
        guiGraphics.fill(left + 154, top + 26, left + 284, top + 44, playBadgeColor);
        drawBorder(guiGraphics, left + 154, top + 26, 130, 18, playBadgeBorder);
        Component playText = Component.translatable(playing ? "gui.mcdisplays.cinema_projector.state_playing" : (linked ? "gui.mcdisplays.cinema_projector.state_standby" : "gui.mcdisplays.cinema_projector.state_idle"));
        guiGraphics.drawCenteredString(font, playText, left + 219, top + 31, 0xFFFFFF);

        // 3. Engine badge (170px)
        boolean engineOk = ClientVideoManager.isWaterMediaAvailable();
        int engineBadgeColor = engineOk ? 0xFF00695C : 0xFFAD1457;
        int engineBadgeBorder = engineOk ? 0xFF4DB6AC : 0xFFF06292;
        guiGraphics.fill(left + 292, top + 26, left + 462, top + 44, engineBadgeColor);
        drawBorder(guiGraphics, left + 292, top + 26, 170, 18, engineBadgeBorder);
        Component engineText = Component.translatable(engineOk ? "gui.mcdisplays.cinema_projector.engine_ready" : "gui.mcdisplays.cinema_projector.engine_missing");
        guiGraphics.drawCenteredString(font, engineText, left + 377, top + 31, 0xFFFFFF);

        // Screen Target details
        if (linked && projector.getLinkedScreenPos() != null) {
            BlockPos tPos = projector.getLinkedScreenPos();
            String details = String.format("%dx%d Screen Cluster @ [%d, %d, %d]",
                projector.getLinkedWidth(), projector.getLinkedHeight(),
                tPos.getX(), tPos.getY(), tPos.getZ());
            guiGraphics.drawString(font, details, left + 16, top + 50, 0xFF81C784, false);
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.screen_scale_info"), left + 16, top + 62, 0xFFB0BEC5, false);
        } else {
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.no_screen_hint1"), left + 16, top + 50, 0xFFEF9A9A, false);
            guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.no_screen_hint2"), left + 16, top + 62, 0xFFB0BEC5, false);
        }

        // Stream URL Area Box
        guiGraphics.fill(left + 8, top + 84, left + imageWidth - 8, top + 158, 0xBB1E1925);
        drawBorder(guiGraphics, left + 8, top + 84, imageWidth - 16, 74, 0xFF433954);
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.source_section"), left + 16, top + 91, 0xFFCE93D8, false);
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.platform_hint"), left + 16, top + 134, 0xFF90A4AE, false);

        // Sliders & Acoustics Area Box
        guiGraphics.fill(left + 8, top + 164, left + imageWidth - 8, top + 252, 0xBB1E1925);
        drawBorder(guiGraphics, left + 8, top + 164, imageWidth - 16, 88, 0xFF433954);
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.acoustics_section"), left + 16, top + 171, 0xFFCE93D8, false);
        guiGraphics.drawString(font, Component.translatable("gui.mcdisplays.cinema_projector.spatial_subhint"), left + 166, top + 222, 0xFF90A4AE, false);

        // Action row background panel
        guiGraphics.fill(left + 8, top + 258, left + imageWidth - 8, top + imageHeight - 8, 0xDD1E1925);
        drawBorder(guiGraphics, left + 8, top + 258, imageWidth - 16, imageHeight - 266, 0xFF5D4F75);
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
