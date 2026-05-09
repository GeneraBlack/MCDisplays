package de.gener.mcdisplays.client;

import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.menu.DisplayPanelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DisplayPanelScreen extends AbstractContainerScreen<DisplayPanelMenu> {
    private static final int SCREEN_IMAGE_WIDTH = 228;
    private static final int SCREEN_IMAGE_HEIGHT = 196;
    private static final int HEADER_HEIGHT = 68;
    private static final int CONTROLS_TOP = 76;
    private static final int CONTROLS_HEIGHT = 24;
    private static final int CONTENT_TOP = 104;
    private static final int FRAME_INSET = 4;
    private static final int SOURCE_SLOT_FRAME_X = DisplayPanelMenu.SOURCE_SLOT_X - 1;
    private static final int SOURCE_SLOT_FRAME_Y = DisplayPanelMenu.SOURCE_SLOT_Y - 1;
    private static final int PRIVACY_BUTTON_WIDTH = 108;
    private static final int PRIVACY_BUTTON_HEIGHT = 20;
    private static final int PRIVACY_BUTTON_X = DisplayPanelMenu.SOURCE_SLOT_X + 26;
    private static final int PRIVACY_BUTTON_Y = DisplayPanelMenu.SOURCE_SLOT_Y - 1;
    private static final int GLOW_STATUS_X = 10;
    private static final int GLOW_STATUS_Y = 44;
    private static final int GLOW_INDICATOR_WIDTH = 14;
    private static final int GLOW_INDICATOR_HEIGHT = 8;
    private static final int ACCESS_STATUS_Y = 18;
    private static final int OWNER_STATUS_Y = 31;
    private static final int READ_ONLY_Y = 57;
    private Button privacyButton;

    public DisplayPanelScreen(DisplayPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = SCREEN_IMAGE_WIDTH;
        imageHeight = SCREEN_IMAGE_HEIGHT;
        titleLabelX = 10;
    }

    @Override
    protected void init() {
        super.init();
        privacyButton = addRenderableWidget(
            Button.builder(Component.empty(), button -> togglePrivacy())
                .bounds(leftPos + PRIVACY_BUTTON_X, topPos + PRIVACY_BUTTON_Y, PRIVACY_BUTTON_WIDTH, PRIVACY_BUTTON_HEIGHT)
                .build()
        );
        updatePrivacyButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updatePrivacyButton();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        DisplayPanelBlockEntity blockEntity = menu.blockEntity();

        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF02A2430);
        guiGraphics.fill(left + FRAME_INSET, top + FRAME_INSET, left + imageWidth - FRAME_INSET, top + FRAME_INSET + HEADER_HEIGHT, 0xCC3A3342);
        guiGraphics.fill(left + FRAME_INSET, top + CONTROLS_TOP, left + imageWidth - FRAME_INSET, top + CONTROLS_TOP + CONTROLS_HEIGHT, 0xCC302A38);
        guiGraphics.fill(left + FRAME_INSET, top + CONTENT_TOP, left + imageWidth - FRAME_INSET, top + imageHeight - FRAME_INSET, 0xCC221D27);

        drawBorder(guiGraphics, left, top, imageWidth, imageHeight, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + FRAME_INSET, top + FRAME_INSET, imageWidth - FRAME_INSET * 2, HEADER_HEIGHT, 0xFFB6A7D3);
        drawBorder(guiGraphics, left + FRAME_INSET, top + CONTROLS_TOP, imageWidth - FRAME_INSET * 2, CONTROLS_HEIGHT, 0xFF8A7BA4);
        drawBorder(guiGraphics, left + FRAME_INSET, top + CONTENT_TOP, imageWidth - FRAME_INSET * 2, imageHeight - CONTENT_TOP - FRAME_INSET, 0xFF6D617E);

        guiGraphics.fill(left + SOURCE_SLOT_FRAME_X, top + SOURCE_SLOT_FRAME_Y, left + SOURCE_SLOT_FRAME_X + 18, top + SOURCE_SLOT_FRAME_Y + 18, 0xFF151118);
        drawBorder(guiGraphics, left + SOURCE_SLOT_FRAME_X, top + SOURCE_SLOT_FRAME_Y, 18, 18, 0xFFD9D0E8);

        if (minecraft != null && minecraft.player != null && !menu.blockEntity().canPlayerEdit(minecraft.player)) {
            guiGraphics.fill(left + SOURCE_SLOT_FRAME_X, top + SOURCE_SLOT_FRAME_Y, left + SOURCE_SLOT_FRAME_X + 18, top + SOURCE_SLOT_FRAME_Y + 18, 0x88291117);
        }

        int indicatorLeft = left + imageWidth - FRAME_INSET - 16 - GLOW_INDICATOR_WIDTH;
        int indicatorTop = top + GLOW_STATUS_Y - 1;
        int indicatorColor = blockEntity.isGlowingDisplay() ? 0xFF7EF8F8 : 0xFF5A5263;
        int indicatorBorder = blockEntity.isGlowingDisplay() ? 0xFFE9D47E : 0xFF7A708A;
        guiGraphics.fill(indicatorLeft, indicatorTop, indicatorLeft + GLOW_INDICATOR_WIDTH, indicatorTop + GLOW_INDICATOR_HEIGHT, indicatorBorder);
        guiGraphics.fill(indicatorLeft + 1, indicatorTop + 1, indicatorLeft + GLOW_INDICATOR_WIDTH - 1, indicatorTop + GLOW_INDICATOR_HEIGHT - 1, indicatorColor);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xF4EEFF, false);

        DisplayPanelBlockEntity blockEntity = menu.blockEntity();
        guiGraphics.drawString(font, Component.translatable(blockEntity.isPrivateDisplay() ? "screen.mcdisplays.display_panel.access.private" : "screen.mcdisplays.display_panel.access.public"), 10, ACCESS_STATUS_Y, 0xE6DDFC, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.display_panel.owner", blockEntity.getOwnerDisplayName()), 10, OWNER_STATUS_Y, 0xCFC4E6, false);
        guiGraphics.drawString(font, Component.translatable(blockEntity.isGlowingDisplay() ? "screen.mcdisplays.display_panel.glow.on" : "screen.mcdisplays.display_panel.glow.off"), GLOW_STATUS_X, GLOW_STATUS_Y, blockEntity.isGlowingDisplay() ? 0xF0E39A : 0xA79EB6, false);

        if (minecraft != null && minecraft.player != null && blockEntity.isPrivateDisplay() && !blockEntity.canPlayerEdit(minecraft.player)) {
            guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.display_panel.read_only"), 10, READ_ONLY_Y, 0xFFB5A7B0, false);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(GLOW_STATUS_X, GLOW_STATUS_Y, imageWidth - GLOW_STATUS_X - FRAME_INSET, font.lineHeight, mouseX, mouseY)) {
            guiGraphics.renderTooltip(font, Component.translatable("screen.mcdisplays.display_panel.glow.hint"), mouseX, mouseY);
        }
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void togglePrivacy() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }
    }

    private void updatePrivacyButton() {
        if (privacyButton == null || minecraft == null || minecraft.player == null) {
            return;
        }

        DisplayPanelBlockEntity blockEntity = menu.blockEntity();
        privacyButton.setMessage(Component.translatable(blockEntity.isPrivateDisplay() ? "screen.mcdisplays.display_panel.toggle_public" : "screen.mcdisplays.display_panel.toggle_private"));
        privacyButton.active = blockEntity.canPlayerChangePrivacy(minecraft.player);
    }
}