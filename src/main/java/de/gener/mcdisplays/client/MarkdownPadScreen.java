package de.gener.mcdisplays.client;

import de.gener.mcdisplays.content.DisplayDocument;
import de.gener.mcdisplays.content.DisplayRichText;
import de.gener.mcdisplays.content.DisplayTextLayout;
import de.gener.mcdisplays.content.MarkdownToDisplayFormatter;
import de.gener.mcdisplays.item.MarkdownPadItemData;
import de.gener.mcdisplays.network.SaveMarkdownPadPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class MarkdownPadScreen extends Screen {
    private static final ResourceLocation UNIFORM_FONT = ResourceLocation.parse("minecraft:uniform");
    private static final int SCREEN_WIDTH = 420;
    private static final int SCREEN_HEIGHT = 244;
    private static final int PREVIEW_BLOCKS_WIDE = 2;
    private static final int PREVIEW_BLOCKS_HIGH = 1;

    private final InteractionHand hand;
    private final String initialTitle;
    private final String initialMarkdown;

    private EditBox titleBox;
    private MultiLineEditBox markdownBox;
    private Button previousPageButton;
    private Button nextPageButton;
    private List<DisplayTextLayout.ScreenPage> previewPages = List.of();
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

        titleBox = addRenderableWidget(new EditBox(font, left + 16, top + 28, 210, 20, Component.translatable("screen.mcdisplays.markdown_pad.title_label")));
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

        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.save"), button -> saveAndClose())
                .bounds(left + 244, top + 208, 76, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.cancel"), button -> onClose())
                .bounds(left + 328, top + 208, 76, 20)
                .build()
        );
        previousPageButton = addRenderableWidget(
            Button.builder(Component.literal("<"), button -> changePreviewPage(-1))
                .bounds(left + 244, top + 176, 20, 20)
                .build()
        );
        nextPageButton = addRenderableWidget(
            Button.builder(Component.literal(">"), button -> changePreviewPage(1))
                .bounds(left + 384, top + 176, 20, 20)
                .build()
        );

        rebuildPreview();
        setInitialFocus(markdownBox);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT, 0xF01F1A22);
        guiGraphics.fill(left + 6, top + 6, left + SCREEN_WIDTH - 6, top + 46, 0xCC3A3342);
        guiGraphics.fill(left + 236, top + 28, left + SCREEN_WIDTH - 16, top + 168, 0xCC17131C);
        drawBorder(guiGraphics, left, top, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + 6, top + 6, SCREEN_WIDTH - 12, 40, 0xFFB6A7D3);
        drawBorder(guiGraphics, left + 236, top + 28, 168, 140, 0xFFB6A7D3);

        guiGraphics.drawString(font, title, left + 14, top + 12, 0xF4EEFF, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.title_label"), left + 16, top + 16, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"), left + 16, top + 50, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.preview_label"), left + 244, top + 16, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.hint"), left + 244, top + 188, 0xBFB4D2, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderPreview(guiGraphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderPreview(GuiGraphics guiGraphics) {
        if (previewPages.isEmpty()) {
            return;
        }

        DisplayTextLayout.ScreenPage page = previewPages.get(previewPageIndex);
        int previewLeft = left + 246;
        int previewTop = top + 32;
        int lineY = previewTop + 6;
        for (DisplayRichText.StyledLine line : page.lines()) {
            guiGraphics.drawString(font, line.toComponent(0xF1EAFE, UNIFORM_FONT), previewLeft + 4, lineY, 0xF1EAFE, false);
            lineY += font.lineHeight;
        }

        guiGraphics.drawString(
            font,
            Component.translatable("screen.mcdisplays.markdown_pad.preview_page", previewPageIndex + 1, previewPages.size()),
            left + 270,
            top + 182,
            0xDDD4EA,
            false
        );
    }

    private void rebuildPreview() {
        DisplayDocument document = MarkdownToDisplayFormatter.toDocument(titleBox == null ? initialTitle : titleBox.getValue(), markdownBox == null ? initialMarkdown : markdownBox.getValue());
        previewPages = DisplayTextLayout.layout(document, PREVIEW_BLOCKS_WIDE, PREVIEW_BLOCKS_HIGH);
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
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new SaveMarkdownPadPayload(hand == InteractionHand.MAIN_HAND, titleBox.getValue(), markdownBox.getValue()));
        }
        onClose();
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}