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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MarkdownPadScreen extends Screen {
    private static final int SCREEN_HEIGHT = 244;
    private static final int WIKI_WIDTH = 138;
    private static final int[][] PREVIEW_SIZES = {
        {2, 1},
        {1, 1},
        {2, 2},
        {3, 2},
        {4, 2},
        {4, 3}
    };

    private record WikiEntry(String label, String sub, String snippet, int color) {
    }

    private record WikiPage(String title, List<WikiEntry> entries) {
    }

    private static final List<WikiPage> WIKI_PAGES = List.of(
        new WikiPage("Formatting", List.of(
            new WikiEntry("# Heading 1", "Large heading", "# Heading 1\n\n", 0xFFD54F),
            new WikiEntry("## Heading 2", "Medium heading", "## Heading 2\n\n", 0xFFF0B0),
            new WikiEntry("### Heading 3", "Small heading", "### Heading 3\n\n", 0xFFFFFF),
            new WikiEntry("**Bold**", "Bold text", "**Bold text**", 0xFFFFFF),
            new WikiEntry("*Italic*", "Italic text", "*Italic text*", 0xE8E0F0),
            new WikiEntry("~~Strike~~", "Strikethrough text", "~~Strikethrough~~", 0xC0B8C8),
            new WikiEntry("`Code`", "Monospace code", "`inline code`", 0xA6FFA6)
        )),
        new WikiPage("Colors", List.of(
            new WikiEntry("[gold] Gold [/]", "Gold text color", "[gold]Gold text[/]", 0xFFAA00),
            new WikiEntry("[red] Red [/]", "Red text color", "[red]Red text[/]", 0xFF6B6B),
            new WikiEntry("[green] Green [/]", "Green text color", "[green]Green text[/]", 0x6BFF6B),
            new WikiEntry("[blue] Blue [/]", "Blue text color", "[blue]Blue text[/]", 0x6BB5FF),
            new WikiEntry("[yellow] Yellow [/]", "Yellow text color", "[yellow]Yellow text[/]", 0xFFFF6B),
            new WikiEntry("[purple] Purple [/]", "Purple text color", "[purple]Purple text[/]", 0xFF80FF),
            new WikiEntry("[#FFAA00] Hex", "Custom hex color", "[#FFAA00]Hex color[/]", 0xFFAA00)
        )),
        new WikiPage("Lists", List.of(
            new WikiEntry("- Bullet", "Bullet list item", "- List item\n", 0xFFFFFF),
            new WikiEntry("1. Numbered", "Numbered list item", "1. Numbered item\n", 0xFFFFFF),
            new WikiEntry("- [ ] Task", "Open checkbox task", "- [ ] Open task\n", 0xFFD54F),
            new WikiEntry("- [x] Done", "Completed task", "- [x] Completed task\n", 0x6BFF6B),
            new WikiEntry("| Table |", "2-column data table", "| Item | Amount |\n| :--- | ---: |\n| Iron | 64 |\n\n", 0x80DEEA),
            new WikiEntry("--- Divider", "Horizontal divider line", "---\n\n", 0xC0B8C8),
            new WikiEntry("> Quote", "Blockquote text", "> Important quote\n\n", 0x80DEEA)
        )),
        new WikiPage("Layout", List.of(
            new WikiEntry("<center>", "Centered text block", "<center>\nCentered text\n</center>\n\n", 0xFFD54F),
            new WikiEntry("<div align=..>", "Right-aligned block", "<div align=\"right\">\nRight-aligned\n</div>\n\n", 0xFFD54F),
            new WikiEntry("``` Codeblock", "Code block with box", "```\nCode block\n```\n\n", 0xA6FFA6),
            new WikiEntry("![Image](URL)", "Media placeholder", "![Image description](https://example.com/image.png)\n\n", 0x6BB5FF),
            new WikiEntry("[reset]", "Reset text colors", "[reset]", 0xC0B8C8)
        ))
    );

    private final InteractionHand hand;
    private final String initialTitle;
    private final String initialMarkdown;

    private EditBox titleBox;
    private MultiLineEditBox markdownBox;
    private Button wikiToggleButton;
    private Button wikiPrevButton;
    private Button wikiNextButton;
    private Button sizeButton;
    private Button previousPageButton;
    private Button nextPageButton;

    private boolean showWiki = true;
    private int wikiPageIndex = 0;
    private int sizeIndex = 0;
    private List<LayoutPage> previewPages = List.of();
    private int previewPageIndex;
    private int screenWidth;
    private int left;
    private int top;
    private long screenImageVersion;

    public MarkdownPadScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("screen.mcdisplays.markdown_pad"));
        this.hand = hand;
        this.initialTitle = MarkdownPadItemData.getTitle(stack);
        this.initialMarkdown = MarkdownPadItemData.getMarkdown(stack);
    }

    @Override
    protected void init() {
        super.init();
        showWiki = width >= 580;
        screenImageVersion = ClientImageManager.getVersion();
        setupLayout();
    }

    private void setupLayout() {
        clearWidgets();
        screenWidth = showWiki ? 566 : 428;
        left = (width - screenWidth) / 2;
        top = (height - SCREEN_HEIGHT) / 2;
        int wikiOffset = showWiki ? WIKI_WIDTH : 0;

        // Top bar Wiki controls
        if (showWiki) {
            wikiPrevButton = addRenderableWidget(
                Button.builder(Component.literal("<"), button -> changeWikiPage(-1))
                    .bounds(left + 8, top + 8, 14, 16)
                    .build()
            );
            wikiNextButton = addRenderableWidget(
                Button.builder(Component.literal(">"), button -> changeWikiPage(1))
                    .bounds(left + 102, top + 8, 14, 16)
                    .build()
            );
            wikiToggleButton = addRenderableWidget(
                Button.builder(Component.literal("◀"), button -> toggleWiki())
                    .bounds(left + 118, top + 8, 14, 16)
                    .tooltip(Tooltip.create(Component.literal("Collapse Wiki")))
                    .build()
            );
            updateWikiButtons();
        } else {
            wikiToggleButton = addRenderableWidget(
                Button.builder(Component.literal("Wiki ▶"), button -> toggleWiki())
                    .bounds(left + 8, top + 8, 48, 16)
                    .tooltip(Tooltip.create(Component.literal("Expand Wiki")))
                    .build()
            );
            wikiPrevButton = null;
            wikiNextButton = null;
        }

        String currentTitle = titleBox == null ? initialTitle : titleBox.getValue();
        titleBox = addRenderableWidget(new EditBox(font, left + wikiOffset + 14, top + 39, 210, 18, Component.translatable("screen.mcdisplays.markdown_pad.title_label")));
        titleBox.setMaxLength(MarkdownPadItemData.maxTitleLength());
        titleBox.setHint(Component.translatable("screen.mcdisplays.markdown_pad.placeholder.title"));
        titleBox.setValue(currentTitle);
        titleBox.setResponder(value -> rebuildPreview());

        String currentMarkdown = markdownBox == null ? initialMarkdown : markdownBox.getValue();
        markdownBox = addRenderableWidget(
            new MultiLineEditBox(
                font,
                left + wikiOffset + 14,
                top + 73,
                210,
                132,
                Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"),
                Component.translatable("screen.mcdisplays.markdown_pad.placeholder.markdown")
            )
        );
        markdownBox.setCharacterLimit(MarkdownPadItemData.maxMarkdownLength());
        markdownBox.setValue(currentMarkdown);
        markdownBox.setValueListener(value -> rebuildPreview());

        sizeButton = addRenderableWidget(
            Button.builder(currentSizeLabel(), button -> cyclePreviewSize())
                .bounds(left + screenWidth - 52, top + 8, 44, 16)
                .tooltip(Tooltip.create(Component.literal("Cycle preview display size")))
                .build()
        );

        int previewAreaX = left + wikiOffset + 232;
        previousPageButton = addRenderableWidget(
            Button.builder(Component.literal("<"), button -> changePreviewPage(-1))
                .bounds(previewAreaX, top + 210, 18, 18)
                .build()
        );
        nextPageButton = addRenderableWidget(
            Button.builder(Component.literal(">"), button -> changePreviewPage(1))
                .bounds(previewAreaX + 54, top + 210, 18, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.save"), button -> saveAndClose())
                .bounds(left + screenWidth - 110, top + 209, 50, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.cancel"), button -> onClose())
                .bounds(left + screenWidth - 56, top + 209, 48, 20)
                .build()
        );

        rebuildPreview();
        setInitialFocus(markdownBox);
    }

    private void toggleWiki() {
        showWiki = !showWiki;
        setupLayout();
    }

    private void changeWikiPage(int delta) {
        wikiPageIndex = Mth.clamp(wikiPageIndex + delta, 0, WIKI_PAGES.size() - 1);
        updateWikiButtons();
    }

    private void updateWikiButtons() {
        if (wikiPrevButton != null) {
            wikiPrevButton.active = wikiPageIndex > 0;
        }
        if (wikiNextButton != null) {
            wikiNextButton.active = wikiPageIndex + 1 < WIKI_PAGES.size();
        }
    }

    private void insertSnippet(String snippet) {
        String current = markdownBox.getValue();
        if (current.isEmpty()) {
            markdownBox.setValue(snippet);
        } else if (current.endsWith("\n")) {
            markdownBox.setValue(current + snippet);
        } else {
            markdownBox.setValue(current + "\n" + snippet);
        }
        rebuildPreview();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showWiki && button == 0) {
            int wikiX = left + 8;
            int wikiY = top + 30;
            int wikiW = 120;
            List<WikiEntry> entries = WIKI_PAGES.get(wikiPageIndex).entries();
            for (int i = 0; i < entries.size(); i++) {
                int itemY = wikiY + i * 25;
                if (mouseX >= wikiX && mouseX <= wikiX + wikiW && mouseY >= itemY && mouseY < itemY + 23) {
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    insertSnippet(entries.get(i).snippet());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Kept empty! Overrides Screen.renderBackground to prevent Minecraft from running
        // renderBlurredBackground (post-processing blur shader) and renderMenuBackground (dirt overlay).
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long currentImageVersion = ClientImageManager.getVersion();
        if (currentImageVersion != screenImageVersion) {
            screenImageVersion = currentImageVersion;
            rebuildPreview();
        }

        // 1. Clean dark backdrop scrim behind the editor without post-process blur shader
        renderTransparentBackground(guiGraphics);
        guiGraphics.fillGradient(0, 0, width, height, 0x88000000, 0xB8000000);

        // 2. Main dialog background panel and borders
        guiGraphics.fill(left, top, left + screenWidth, top + SCREEN_HEIGHT, 0xFF191520);
        drawBorder(guiGraphics, left, top, screenWidth, SCREEN_HEIGHT, 0xFF8E7AA5);

        // Top bar header panel
        guiGraphics.fill(left + 6, top + 6, left + screenWidth - 6, top + 26, 0xFF2A2334);
        drawBorder(guiGraphics, left + 6, top + 6, screenWidth - 12, 20, 0xFFB6A7D3);

        int wikiOffset = showWiki ? WIKI_WIDTH : 0;

        // Wiki Sidebar
        if (showWiki) {
            guiGraphics.fill(left + 6, top + 28, left + WIKI_WIDTH - 4, top + 206, 0xFF16121C);
            drawBorder(guiGraphics, left + 6, top + 28, WIKI_WIDTH - 10, 178, 0xFF4A3E60);

            WikiPage page = WIKI_PAGES.get(wikiPageIndex);
            String pageIndicator = page.title() + " " + (wikiPageIndex + 1) + "/" + WIKI_PAGES.size();
            int titleW = font.width(pageIndicator);
            guiGraphics.drawString(font, pageIndicator, left + 63 - titleW / 2, top + 12, 0xF4EEFF, false);

            int wikiX = left + 8;
            int wikiY = top + 30;
            int wikiW = 120;
            for (int i = 0; i < page.entries().size(); i++) {
                WikiEntry entry = page.entries().get(i);
                int itemY = wikiY + i * 25;
                boolean hovered = mouseX >= wikiX && mouseX <= wikiX + wikiW && mouseY >= itemY && mouseY < itemY + 23;

                guiGraphics.fill(wikiX, itemY, wikiX + wikiW, itemY + 23, hovered ? 0xFF3D3252 : 0xFF231B2E);
                drawBorder(guiGraphics, wikiX, itemY, wikiW, 23, hovered ? 0xFFC8B5EE : 0xFF4A3E60);
                guiGraphics.drawString(font, entry.label(), wikiX + 4, itemY + 3, entry.color(), false);
                guiGraphics.drawString(font, entry.sub(), wikiX + 4, itemY + 13, 0xC5BDD4, false);
            }
        }

        // Preview box background
        int previewAreaX = left + wikiOffset + 232;
        int previewAreaY = top + 28;
        int previewAreaW = screenWidth - wikiOffset - 240;
        int previewAreaH = 177;
        guiGraphics.fill(previewAreaX, previewAreaY, previewAreaX + previewAreaW, previewAreaY + previewAreaH, 0xFF110D16);
        drawBorder(guiGraphics, previewAreaX, previewAreaY, previewAreaW, previewAreaH, 0xFF5A4D6E);

        // 3. Render all widgets (buttons, editboxes) cleanly on top of panels
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 4. Render labels, previews, and tooltips
        // Editor title centered over editor column
        int editorCenterX = left + wikiOffset + 119;
        int titleW = font.width(title);
        guiGraphics.drawString(font, title, editorCenterX - titleW / 2, top + 12, 0xF4EEFF, false);

        // Headline / Title label above title box
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.title_label"), left + wikiOffset + 14, top + 29, 0xDDD4EA, false);

        // Markdown input label above markdown box
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"), left + wikiOffset + 14, top + 62, 0xDDD4EA, false);

        // Preview header label
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.preview_label"), previewAreaX + 4, top + 12, 0xDDD4EA, false);

        // Character count
        String countStr = (markdownBox != null ? markdownBox.getValue().length() : 0) + "/" + MarkdownPadItemData.maxMarkdownLength();
        int countW = font.width(countStr);
        guiGraphics.drawString(font, countStr, left + wikiOffset + 224 - countW, top + 208, 0xA69BB8, false);

        // Multi-page preview indicator if > 1 page
        if (previewPages.size() > 1) {
            String pageStr = (previewPageIndex + 1) + "/" + previewPages.size();
            int strW = font.width(pageStr);
            guiGraphics.drawString(font, pageStr, previewAreaX + 36 - strW / 2, top + 215, 0xDDD4EA, false);
        }

        // Live Preview display rendering
        renderPreview(guiGraphics, previewAreaX + 2, previewAreaY + 2, previewAreaW - 4, previewAreaH - 4);

        // Tooltip for wiki hover
        if (showWiki) {
            int wikiX = left + 8;
            int wikiY = top + 30;
            int wikiW = 120;
            List<WikiEntry> entries = WIKI_PAGES.get(wikiPageIndex).entries();
            for (int i = 0; i < entries.size(); i++) {
                int itemY = wikiY + i * 25;
                if (mouseX >= wikiX && mouseX <= wikiX + wikiW && mouseY >= itemY && mouseY < itemY + 23) {
                    guiGraphics.renderTooltip(font, Component.literal("Click to insert:\n" + entries.get(i).snippet().strip()), (int) mouseX, (int) mouseY);
                    break;
                }
            }
        }
    }

    private void renderPreview(GuiGraphics guiGraphics, int areaX, int areaY, int areaW, int areaH) {
        if (previewPages.isEmpty()) {
            return;
        }

        LayoutPage page = previewPages.get(previewPageIndex);
        float scale = Math.min((float) areaW / page.widthPx(), (float) areaH / page.heightPx());
        float renderedW = page.widthPx() * scale;
        float renderedH = page.heightPx() * scale;
        float originX = areaX + (areaW - renderedW) / 2.0F;
        float originY = areaY + (areaH - renderedH) / 2.0F;

        // Draw display panel background and frame
        guiGraphics.fill((int) originX, (int) originY, (int) (originX + renderedW), (int) (originY + renderedH), 0xFF1C1822);
        drawBorder(guiGraphics, (int) originX, (int) originY, (int) renderedW, (int) renderedH, 0x88B6A7D3);

        // If the page has no content ops, display a clean placeholder inside the display
        if (page.ops().isEmpty()) {
            int blocksWide = PREVIEW_SIZES[sizeIndex][0];
            int blocksHigh = PREVIEW_SIZES[sizeIndex][1];
            String sizeStr = blocksWide + "x" + blocksHigh + " Display";
            String hintStr = "(No content)";
            int sw = font.width(sizeStr);
            int hw = font.width(hintStr);
            guiGraphics.drawString(font, sizeStr, (int) (originX + (renderedW - sw) / 2.0F), (int) (originY + renderedH / 2.0F - 9), 0x77B6A7D3, false);
            guiGraphics.drawString(font, hintStr, (int) (originX + (renderedW - hw) / 2.0F), (int) (originY + renderedH / 2.0F + 3), 0x558E849E, false);
            return;
        }

        guiGraphics.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(originX, originY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.RectOp rect) {
                guiGraphics.fill((int) rect.x(), (int) rect.y(), (int) (rect.x() + rect.width()), (int) (rect.y() + rect.height()), rect.argb());
            }
        }

        for (LayoutPage.Op op : page.ops()) {
            if (op instanceof LayoutPage.ImageOp img) {
                renderPreviewImage(guiGraphics, img);
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
    }

    private void renderPreviewImage(GuiGraphics guiGraphics, LayoutPage.ImageOp img) {
        int x = (int) img.x();
        int y = (int) img.y();
        int w = (int) img.width();
        int h = (int) img.height();

        if (img.blocked()) {
            drawBorder(guiGraphics, x, y, w, h, 0x66FF5555);
            String label = "[Blocked] " + img.url();
            int tw = font.width(label);
            int tx = x + Math.max(0, (w - tw) / 2);
            int ty = y + Math.max(0, (h - font.lineHeight) / 2);
            guiGraphics.drawString(font, label, tx, ty, 0xFFFF7777, false);
            return;
        }

        ClientImageManager.ImageInfo info = ClientImageManager.getImage(img.url());
        if (info != null && info.location() != null) {
            guiGraphics.blit(
                info.location(),
                x,
                y,
                w,
                h,
                0.0F,
                0.0F,
                info.width(),
                info.height(),
                info.width(),
                info.height()
            );
            return;
        }

        ClientImageManager.Status status = ClientImageManager.getStatus(img.url());
        if (status == ClientImageManager.Status.FAILED) {
            drawBorder(guiGraphics, x, y, w, h, 0x66FF5555);
            String label = img.alt().isBlank() ? "Failed to load image" : img.alt();
            int tw = font.width(label);
            int tx = x + Math.max(0, (w - tw) / 2);
            int ty = y + Math.max(0, (h - font.lineHeight) / 2);
            guiGraphics.drawString(font, label, tx, ty, 0xFFFF6666, false);
        } else {
            drawBorder(guiGraphics, x, y, w, h, 0x6688B6A7);
            String label = img.alt().isBlank() ? "Loading image..." : img.alt();
            int tw = font.width(label);
            int tx = x + Math.max(0, (w - tw) / 2);
            int ty = y + Math.max(0, (h - font.lineHeight) / 2);
            guiGraphics.drawString(font, label, tx, ty, 0xFFB6A7D3, false);
        }
    }

    private void rebuildPreview() {
        String rawTitle = titleBox == null ? initialTitle : titleBox.getValue();
        String titleText = MarkdownPadItemData.getEffectiveTitle(rawTitle, null);
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
        previousPageButton.visible = multiplePages;
        nextPageButton.visible = multiplePages;
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