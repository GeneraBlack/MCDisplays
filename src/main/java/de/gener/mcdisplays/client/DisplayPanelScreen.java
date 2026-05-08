package de.gener.mcdisplays.client;

import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.menu.DisplayPanelMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DisplayPanelScreen extends AbstractContainerScreen<DisplayPanelMenu> {
    private static final int GLOW_STATUS_X = 8;
    private static final int GLOW_STATUS_Y = 40;
    private static final int GLOW_STATUS_WIDTH = 88;
    private List<FormattedCharSequence> hintLines;
    private Button privacyButton;

    public DisplayPanelScreen(DisplayPanelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 72;
        hintLines = List.of();
    }

    @Override
    protected void init() {
        super.init();
        hintLines = font.split(Component.translatable("screen.mcdisplays.display_panel.hint"), 160);
        privacyButton = addRenderableWidget(
            Button.builder(Component.empty(), button -> togglePrivacy())
                .bounds(leftPos + 102, topPos + 18, 66, 20)
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
        guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + 48, 0xCC3A3342);
        guiGraphics.fill(left + 4, top + 54, left + imageWidth - 4, top + imageHeight - 4, 0xCC221D27);

        drawBorder(guiGraphics, left, top, imageWidth, imageHeight, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + 4, top + 4, imageWidth - 8, 44, 0xFFB6A7D3);
        drawBorder(guiGraphics, left + 4, top + 54, imageWidth - 8, imageHeight - 58, 0xFF6D617E);

        guiGraphics.fill(left + 79, top + 19, left + 97, top + 37, 0xFF151118);
        drawBorder(guiGraphics, left + 79, top + 19, 18, 18, 0xFFD9D0E8);

        if (minecraft != null && minecraft.player != null && !menu.blockEntity().canPlayerEdit(minecraft.player)) {
            guiGraphics.fill(left + 79, top + 19, left + 97, top + 37, 0x88291117);
        }

        int indicatorLeft = left + 146;
        int indicatorTop = top + 39;
        int indicatorColor = blockEntity.isGlowingDisplay() ? 0xFF7EF8F8 : 0xFF5A5263;
        int indicatorBorder = blockEntity.isGlowingDisplay() ? 0xFFE9D47E : 0xFF7A708A;
        guiGraphics.fill(indicatorLeft, indicatorTop, indicatorLeft + 14, indicatorTop + 8, indicatorBorder);
        guiGraphics.fill(indicatorLeft + 1, indicatorTop + 1, indicatorLeft + 13, indicatorTop + 7, indicatorColor);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xF4EEFF, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xDDD4EA, false);

        DisplayPanelBlockEntity blockEntity = menu.blockEntity();
        guiGraphics.drawString(font, Component.translatable(blockEntity.isPrivateDisplay() ? "screen.mcdisplays.display_panel.access.private" : "screen.mcdisplays.display_panel.access.public"), 8, 18, 0xE6DDFC, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.display_panel.owner", blockEntity.getOwnerDisplayName()), 8, 30, 0xCFC4E6, false);
        guiGraphics.drawString(font, Component.translatable(blockEntity.isGlowingDisplay() ? "screen.mcdisplays.display_panel.glow.on" : "screen.mcdisplays.display_panel.glow.off"), GLOW_STATUS_X, GLOW_STATUS_Y, blockEntity.isGlowingDisplay() ? 0xF0E39A : 0xA79EB6, false);

        int y = 52;
        if (minecraft != null && minecraft.player != null && blockEntity.isPrivateDisplay() && !blockEntity.canPlayerEdit(minecraft.player)) {
            guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.display_panel.read_only"), 8, y, 0xFFB5A7B0, false);
            y += font.lineHeight + 2;
        }

        for (FormattedCharSequence line : hintLines) {
            guiGraphics.drawString(font, line, 8, y, 0xC8BDD7, false);
            y += font.lineHeight;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(GLOW_STATUS_X, GLOW_STATUS_Y, GLOW_STATUS_WIDTH, font.lineHeight, mouseX, mouseY)) {
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