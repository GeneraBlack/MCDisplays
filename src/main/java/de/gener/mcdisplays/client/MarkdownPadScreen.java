package de.gener.mcdisplays.client;

import de.gener.mcdisplays.item.MarkdownPadItemData;
import de.gener.mcdisplays.markdown.LayoutPage;
import de.gener.mcdisplays.markdown.MarkdownLayout;
import de.gener.mcdisplays.network.SaveMarkdownPadPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MarkdownPadScreen extends Screen {
    private static final int SCREEN_WIDTH = 440;
    private static final int SCREEN_HEIGHT = 244;
    private static final int[][] PREVIEW_SIZES = {
        {2, 1},
        {1, 1},
        {2, 2},
        {3, 2},
        {4, 2},
        {4, 3}
    };

    private final InteractionHand hand;
    private final String initialTitle;
    private final String initialMarkdown;

    private EditBox titleBox;
    private MultiLineEditBox markdownBox;
    private Button sizeButton;
    private Button previousPageButton;
    private Button nextPageButton;
    private int sizeIndex = 0;
    private List<LayoutPage> previewPages = List.of();
    private int previewPageIndex;
    private int left;
    private int top;

    public MarkdownPadScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("screen.mcdisplays.markdown_pad"));
        this.hand = hand;
        this.initialTitle = MarkdownPadItemData.getTitle(stack);
        this.initialMarkdown = MarkdownPadItemData.getMarkdown(stack);
    }

    @Override
    protected void init() {
        super.init();
        left = (width - SCREEN_WIDTH) / 2;
        top = (height - SCREEN_HEIGHT) / 2;

        titleBox = addRenderableWidget(new EditBox(font, left + 16, top + 26, 210, 20, Component.translatable("screen.mcdisplays.markdown_pad.title_label")));
        titleBox.setMaxLength(MarkdownPadItemData.maxTitleLength());
        titleBox.setHint(Component.translatable("screen.mcdisplays.markdown_pad.placeholder.title"));
        titleBox.setValue(initialTitle);
        titleBox.setResponder(value -> rebuildPreview());

        markdownBox = addRenderableWidget(
            new MultiLineEditBox(
                font,
                left + 16,
                top + 60,
                210,
                144,
                Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"),
                Component.translatable("screen.mcdisplays.markdown_pad.placeholder.markdown")
            )
        );
        markdownBox.setCharacterLimit(MarkdownPadItemData.maxMarkdownLength());
        markdownBox.setValue(initialMarkdown);
        markdownBox.setValueListener(value -> rebuildPreview());

        sizeButton = addRenderableWidget(
            Button.builder(currentSizeLabel(), button -> cyclePreviewSize())
                .bounds(left + 376, top + 6, 48, 18)
                .build()
        );

        previousPageButton = addRenderableWidget(
            Button.builder(Component.literal("<"), button -> changePreviewPage(-1))
                .bounds(left + 236, top + 212, 20, 20)
                .build()
        );
        nextPageButton = addRenderableWidget(
            Button.builder(Component.literal(">"), button -> changePreviewPage(1))
                .bounds(left + 316, top + 212, 20, 20)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.save"), button -> saveAndClose())
                .bounds(left + 344, top + 212, 42, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.cancel"), button -> onClose())
                .bounds(left + 390, top + 212, 42, 20)
                .build()
        );

        rebuildPreview();
        setInitialFocus(markdownBox);
    }

    private Component currentSizeLabel() {
        return Component.literal(PREVIEW_SIZES[sizeIndex][0] + "x" + PREVIEW_SIZES[sizeIndex][1]);
    }

    private void cyclePreviewSize() {
        sizeIndex = (sizeIndex + 1) % PREVIEW_SIZES.length;
        sizeButton.setMessage(currentSizeLabel());
        rebuildPreview();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT, 0xF01F1A22);
        guiGraphics.fill(left + 6, top + 6, left + SCREEN_WIDTH - 6, top + 24, 0xCC3A3342);
        guiGraphics.fill(left + 236, top + 26, left + SCREEN_WIDTH - 12, top + 206, 0xFF141119);
        drawBorder(guiGraphics, left, top, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + 6, top + 6, SCREEN_WIDTH - 12, 18, 0xFFB6A7D3);
        drawBorder(guiGraphics, left + 236, top + 26, SCREEN_WIDTH - 248, 180, 0xFFB6A7D3);

        guiGraphics.drawString(font, title, left + 12, top + 10, 0xF4EEFF, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.preview_label"), left + 238, top + 10, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.title_label"), left + 16, top + 16, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"), left + 16, top + 50, 0xDDD4EA, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderPreview(guiGraphics);
    }

    private void renderPreview(GuiGraphics guiGraphics) {
        if (previewPages.isEmpty()) {
            return;
        }

        int previewAreaX = left + 238;
        int previewAreaY = top + 28;
        int previewAreaW = SCREEN_WIDTH - 252;
        int previewAreaH = 176;

        LayoutPage page = previewPages.get(previewPageIndex);
        float scale = Math.min((float) previewAreaW / page.widthPx(), (float) previewAreaH / page.heightPx());
        float renderedW = page.widthPx() * scale;
        float renderedH = page.heightPx() * scale;
        float originX = previewAreaX + (previewAreaW - renderedW) / 2.0F;
        float originY = previewAreaY + (previewAreaH - renderedH) / 2.0F;

        guiGraphics.enableScissor(previewAreaX, previewAreaY, previewAreaX + previewAreaW, previewAreaY + previewAreaH);

        // Draw page background panel
        guiGraphics.fill((int) originX, (int) originY, (int) (originX + renderedW), (int) (originY + renderedH), 0xFF1C1822);
        drawBorder(guiGraphics, (int) originX, (int) originY, (int) renderedW, (int) renderedH, 0x66B6A7D3);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(originX, originY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.RectOp rect) {
                guiGraphics.fill((int) rect.x(), (int) rect.y(), (int) (rect.x() + rect.width()), (int) (rect.y() + rect.height()), rect.argb());
            }
        }

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.TextOp text) {
                ClientTextMetrics.Prepared prepared = ClientTextMetrics.prepare(text);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(text.x, text.y, 0.0F);
                guiGraphics.pose().scale(text.scale, text.scale, 1.0F);
                guiGraphics.drawString(font, prepared.sequence(), 0, 0, text.color | 0xFF000000, false);
                guiGraphics.pose().popPose();
            }
        }

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();

        if (previewPages.size() > 1) {
            String pageStr = (previewPageIndex + 1) + "/" + previewPages.size();
            int strW = font.width(pageStr);
            guiGraphics.drawString(font, pageStr, left + 286 - strW / 2, top + 218, 0xDDD4EA, false);
        }
    }

    private void rebuildPreview() {
        String titleText = titleBox == null ? initialTitle : titleBox.getValue();
        String markdownText = markdownBox == null ? initialMarkdown : markdownBox.getValue();
        int blocksWide = PREVIEW_SIZES[sizeIndex][0];
        int blocksHigh = PREVIEW_SIZES[sizeIndex][1];

        previewPages = MarkdownLayout.layout(titleText, markdownText, blocksWide, blocksHigh, ClientTextMetrics.INSTANCE);
        previewPageIndex = Mth.clamp(previewPageIndex, 0, Math.max(0, previewPages.size() - 1));
        updatePreviewButtons();
    }

    private void changePreviewPage(int delta) {
        if (previewPages.isEmpty()) {
            return;
        }
        previewPageIndex = Mth.clamp(previewPageIndex + delta, 0, previewPages.size() - 1);
        updatePreviewButtons();
    }

    private void updatePreviewButtons() {
        boolean multiplePages = previewPages.size() > 1;
        previousPageButton.active = multiplePages && previewPageIndex > 0;
        nextPageButton.active = multiplePages && previewPageIndex + 1 < previewPages.size();
    }

    private void saveAndClose() {
        if (minecraft != null && minecraft.player != null) {
            PacketDistributor.sendToServer(new SaveMarkdownPadPayload(hand == InteractionHand.MAIN_HAND, titleBox.getValue(), markdownBox.getValue()));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y + 1, x + 1, y + height - 1, color);
        guiGraphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}