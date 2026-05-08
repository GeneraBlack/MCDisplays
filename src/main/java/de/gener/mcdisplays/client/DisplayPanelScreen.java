package de.gener.mcdisplays.client;

import de.gener.mcdisplays.menu.DisplayPanelMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DisplayPanelScreen extends AbstractContainerScreen<DisplayPanelMenu> {
    private List<FormattedCharSequence> hintLines;

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
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;

        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF02A2430);
        guiGraphics.fill(left + 4, top + 4, left + imageWidth - 4, top + 48, 0xCC3A3342);
        guiGraphics.fill(left + 4, top + 54, left + imageWidth - 4, top + imageHeight - 4, 0xCC221D27);

        drawBorder(guiGraphics, left, top, imageWidth, imageHeight, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + 4, top + 4, imageWidth - 8, 44, 0xFFB6A7D3);
        drawBorder(guiGraphics, left + 4, top + 54, imageWidth - 8, imageHeight - 58, 0xFF6D617E);

        guiGraphics.fill(left + 79, top + 19, left + 97, top + 37, 0xFF151118);
        drawBorder(guiGraphics, left + 79, top + 19, 18, 18, 0xFFD9D0E8);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xF4EEFF, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xDDD4EA, false);

        int y = 40;
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
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}