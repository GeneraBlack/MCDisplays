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
        new WikiPage("Format", List.of(
            new WikiEntry("# Überschrift 1", "Große Überschrift", "# Überschrift 1\n\n", 0xF6D58B),
            new WikiEntry("## Überschrift 2", "Mittlere Überschrift", "## Überschrift 2\n\n", 0xE4EEF7),
            new WikiEntry("### Überschrift 3", "Kleine Überschrift", "### Überschrift 3\n\n", 0xCFC4E6),
            new WikiEntry("**Fett**", "Fetter Text", "**Fett**", 0xFFFFFF),
            new WikiEntry("*Kursiv*", "Schräger Text", "*Kursiv*", 0xDDD4EA),
            new WikiEntry("~~Durchstrich~~", "Durchgestrichen", "~~Durchgestrichen~~", 0xAAAAAA),
            new WikiEntry("`Code`", "Monospace Code", "`Code`", 0xA8E6A1)
        )),
        new WikiPage("Farben", List.of(
            new WikiEntry("[gold] Gold [/]", "Goldene Farbe", "[gold]Text[/]", 0xFFAA00),
            new WikiEntry("[red] Rot [/]", "Rote Farbe", "[red]Text[/]", 0xFF5555),
            new WikiEntry("[green] Grün [/]", "Grüne Farbe", "[green]Text[/]", 0x55FF55),
            new WikiEntry("[blue] Blau [/]", "Blaue Farbe", "[blue]Text[/]", 0x55AAFF),
            new WikiEntry("[yellow] Gelb [/]", "Gelbe Farbe", "[yellow]Text[/]", 0xFFFF55),
            new WikiEntry("[purple] Lila [/]", "Violette Farbe", "[purple]Text[/]", 0xFF55FF),
            new WikiEntry("[#FFAA00] Hex", "Eigene Hex-Farbe", "[#FFAA00]Farbe[/]", 0xFFAA00)
        )),
        new WikiPage("Listen", List.of(
            new WikiEntry("- Liste", "Aufzählungspunkt", "- Eintrag\n", 0xF4EEFF),
            new WikiEntry("1. Nummeriert", "Nummerierter Punkt", "1. Eintrag\n", 0xF4EEFF),
            new WikiEntry("- [ ] Aufgabe", "Offene Checkbox", "- [ ] Offene Aufgabe\n", 0xF4EEFF),
            new WikiEntry("- [x] Erledigt", "Erledigte Checkbox", "- [x] Erledigte Aufgabe\n", 0x55FF55),
            new WikiEntry("| Tabelle |", "Tabelle mit 2 Spalten", "| Name | Wert |\n| :--- | ---: |\n| Eisen | 64 |\n\n", 0xF6D58B),
            new WikiEntry("--- Trennlinie", "Horizontale Linie", "---\n\n", 0xAAAAAA),
            new WikiEntry("> Zitat", "Blockzitat", "> Wichtiges Zitat\n\n", 0xB8C2CC)
        )),
        new WikiPage("Layout", List.of(
            new WikiEntry("<center>", "Zentrierter Block", "<center>\nZentrierter Text\n</center>\n\n", 0xF6D58B),
            new WikiEntry("<div align=..>", "Rechtsbündiger Block", "<div align=\"right\">\nRechtsbündig\n</div>\n\n", 0xF6D58B),
            new WikiEntry("``` Codeblock", "Codeblock mit Rahmen", "```\nCode\n```\n\n", 0xA8E6A1),
            new WikiEntry("![Bild](URL)", "Medien-Platzhalter", "![Alt-Text](https://example.com/image.png)\n\n", 0x55AAFF),
            new WikiEntry("[reset]", "Farben zurücksetzen", "[reset]", 0xAAAAAA)
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
        setupLayout();
    }

    private void setupLayout() {
        clearWidgets();
        screenWidth = showWiki ? 566 : 428;
        left = (width - screenWidth) / 2;
        top = (height - SCREEN_HEIGHT) / 2;
        int wikiOffset = showWiki ? WIKI_WIDTH : 0;

        // Wiki Controls
        if (showWiki) {
            wikiPrevButton = addRenderableWidget(
                Button.builder(Component.literal("<"), button -> changeWikiPage(-1))
                    .bounds(left + 8, top + 6, 16, 16)
                    .build()
            );
            wikiNextButton = addRenderableWidget(
                Button.builder(Component.literal(">"), button -> changeWikiPage(1))
                    .bounds(left + 114, top + 6, 16, 16)
                    .build()
            );
            updateWikiButtons();
        }

        wikiToggleButton = addRenderableWidget(
            Button.builder(Component.literal(showWiki ? "◀ Wiki" : "Wiki ▶"), button -> toggleWiki())
                .bounds(left + wikiOffset + 16, top + 6, 44, 16)
                .build()
        );

        String currentTitle = titleBox == null ? initialTitle : titleBox.getValue();
        titleBox = addRenderableWidget(new EditBox(font, left + wikiOffset + 16, top + 26, 210, 20, Component.translatable("screen.mcdisplays.markdown_pad.title_label")));
        titleBox.setMaxLength(MarkdownPadItemData.maxTitleLength());
        titleBox.setHint(Component.translatable("screen.mcdisplays.markdown_pad.placeholder.title"));
        titleBox.setValue(currentTitle);
        titleBox.setResponder(value -> rebuildPreview());

        String currentMarkdown = markdownBox == null ? initialMarkdown : markdownBox.getValue();
        markdownBox = addRenderableWidget(
            new MultiLineEditBox(
                font,
                left + wikiOffset + 16,
                top + 60,
                210,
                144,
                Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"),
                Component.translatable("screen.mcdisplays.markdown_pad.placeholder.markdown")
            )
        );
        markdownBox.setCharacterLimit(MarkdownPadItemData.maxMarkdownLength());
        markdownBox.setValue(currentMarkdown);
        markdownBox.setValueListener(value -> rebuildPreview());

        sizeButton = addRenderableWidget(
            Button.builder(currentSizeLabel(), button -> cyclePreviewSize())
                .bounds(left + screenWidth - 54, top + 6, 46, 16)
                .build()
        );

        previousPageButton = addRenderableWidget(
            Button.builder(Component.literal("<"), button -> changePreviewPage(-1))
                .bounds(left + wikiOffset + 236, top + 210, 20, 20)
                .build()
        );
        nextPageButton = addRenderableWidget(
            Button.builder(Component.literal(">"), button -> changePreviewPage(1))
                .bounds(left + wikiOffset + 296, top + 210, 20, 20)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.save"), button -> saveAndClose())
                .bounds(left + wikiOffset + 322, top + 210, 46, 20)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.translatable("screen.mcdisplays.markdown_pad.cancel"), button -> onClose())
                .bounds(left + wikiOffset + 372, top + 210, 46, 20)
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
            int wikiY = top + 28;
            int wikiW = 122;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Use transparent background dimming without fullscreen blur
        renderTransparentBackground(guiGraphics);

        int wikiOffset = showWiki ? WIKI_WIDTH : 0;

        // Solid background container panels
        guiGraphics.fill(left, top, left + screenWidth, top + SCREEN_HEIGHT, 0xFF191520);
        guiGraphics.fill(left + 6, top + 6, left + screenWidth - 6, top + 24, 0xFF2A2334);
        drawBorder(guiGraphics, left, top, screenWidth, SCREEN_HEIGHT, 0xFF8E7AA5);
        drawBorder(guiGraphics, left + 6, top + 6, screenWidth - 12, 18, 0xFFB6A7D3);

        // Wiki Sidebar
        if (showWiki) {
            guiGraphics.fill(left + 6, top + 26, left + WIKI_WIDTH + 2, top + 206, 0xFF14101A);
            drawBorder(guiGraphics, left + 6, top + 26, WIKI_WIDTH - 4, 180, 0xFF5A4D6E);

            WikiPage page = WIKI_PAGES.get(wikiPageIndex);
            String pageIndicator = page.title() + " " + (wikiPageIndex + 1) + "/" + WIKI_PAGES.size();
            int titleW = font.width(pageIndicator);
            guiGraphics.drawString(font, pageIndicator, left + 69 - titleW / 2, top + 10, 0xE6DDFC, false);

            int wikiX = left + 8;
            int wikiY = top + 28;
            int wikiW = 122;
            for (int i = 0; i < page.entries().size(); i++) {
                WikiEntry entry = page.entries().get(i);
                int itemY = wikiY + i * 25;
                boolean hovered = mouseX >= wikiX && mouseX <= wikiX + wikiW && mouseY >= itemY && mouseY < itemY + 23;

                guiGraphics.fill(wikiX, itemY, wikiX + wikiW, itemY + 23, hovered ? 0xFF352B44 : 0xFF1F1828);
                drawBorder(guiGraphics, wikiX, itemY, wikiW, 23, hovered ? 0xFFD8C7F0 : 0xFF463A58);
                guiGraphics.drawString(font, entry.label(), wikiX + 4, itemY + 3, entry.color(), false);
                guiGraphics.drawString(font, entry.sub(), wikiX + 4, itemY + 13, 0x8E849E, false);
            }
        }

        // Preview box background
        int previewAreaX = left + wikiOffset + 236;
        int previewAreaY = top + 26;
        int previewAreaW = screenWidth - wikiOffset - 244;
        int previewAreaH = 180;
        guiGraphics.fill(previewAreaX, previewAreaY, previewAreaX + previewAreaW, previewAreaY + previewAreaH, 0xFF120E17);
        drawBorder(guiGraphics, previewAreaX, previewAreaY, previewAreaW, previewAreaH, 0xFFB6A7D3);

        // Labels
        guiGraphics.drawString(font, title, left + wikiOffset + 66, top + 10, 0xF4EEFF, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.preview_label"), previewAreaX + 2, top + 10, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.title_label"), left + wikiOffset + 16, top + 16, 0xDDD4EA, false);
        guiGraphics.drawString(font, Component.translatable("screen.mcdisplays.markdown_pad.markdown_label"), left + wikiOffset + 16, top + 50, 0xDDD4EA, false);

        // Char count
        String countStr = (markdownBox != null ? markdownBox.getValue().length() : 0) + "/" + MarkdownPadItemData.maxMarkdownLength();
        int countW = font.width(countStr);
        guiGraphics.drawString(font, countStr, left + wikiOffset + 226 - countW, top + 208, 0x8E849E, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderPreview(guiGraphics, previewAreaX + 2, previewAreaY + 2, previewAreaW - 4, previewAreaH - 4);

        // Tooltip for wiki hover
        if (showWiki) {
            int wikiX = left + 8;
            int wikiY = top + 28;
            int wikiW = 122;
            List<WikiEntry> entries = WIKI_PAGES.get(wikiPageIndex).entries();
            for (int i = 0; i < entries.size(); i++) {
                int itemY = wikiY + i * 25;
                if (mouseX >= wikiX && mouseX <= wikiX + wikiW && mouseY >= itemY && mouseY < itemY + 23) {
                    guiGraphics.renderTooltip(font, Component.literal("Klick zum Einfügen:\n" + entries.get(i).snippet().strip()), (int) mouseX, (int) mouseY);
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

        guiGraphics.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

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

        int wikiOffset = showWiki ? WIKI_WIDTH : 0;
        if (previewPages.size() > 1) {
            String pageStr = (previewPageIndex + 1) + "/" + previewPages.size();
            int strW = font.width(pageStr);
            guiGraphics.drawString(font, pageStr, left + wikiOffset + 276 - strW / 2, top + 216, 0xDDD4EA, false);
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
        int wikiOffset = showWiki ? WIKI_WIDTH : 0;
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