package de.gener.mcdisplays.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MarkdownTest {

    @Test
    void testBasicHeadingAndParagraph() {
        String md = "# Main Heading\n\nThis is a **bold** paragraph with [red]red text[/] inside.";
        List<Block> blocks = MarkdownParser.parse(md);
        assertEquals(2, blocks.size());

        assertInstanceOf(Block.Text.class, blocks.get(0));
        Block.Text heading = (Block.Text) blocks.get(0);
        assertEquals(1, heading.level());
        assertEquals(Block.Align.LEFT, heading.align());
        assertEquals("Main Heading", heading.spans().get(0).text());

        assertInstanceOf(Block.Text.class, blocks.get(1));
        Block.Text para = (Block.Text) blocks.get(1);
        assertEquals(0, para.level());

        // Check color tag parsed properly
        boolean foundRed = false;
        for (Block.Span span : para.spans()) {
            if (span.text().contains("red text") && (span.color() == 0xFF5555 || span.color() == 0xAA0000)) {
                foundRed = true;
                break;
            }
        }
        assertTrue(foundRed, "Red color tag should be recognized and applied");
    }

    @Test
    void testHtmlAlignment() {
        String md = "<center>\n\n# Centered Title\n\nCentered paragraph\n\n</center>\n\nNormal paragraph";
        List<Block> blocks = MarkdownParser.parse(md);
        assertTrue(blocks.size() >= 3);

        Block.Text h1 = (Block.Text) blocks.get(0);
        assertEquals(Block.Align.CENTER, h1.align());

        Block.Text p1 = (Block.Text) blocks.get(1);
        assertEquals(Block.Align.CENTER, p1.align());

        Block.Text p2 = (Block.Text) blocks.get(2);
        assertEquals(Block.Align.LEFT, p2.align());
    }

    @Test
    void testGfmTable() {
        String md = """
            | Item | Amount | Status |
            | :--- | :----: | -----: |
            | Iron | 64 | Done |
            | Gold | 12 | Busy |
            """;
        List<Block> blocks = MarkdownParser.parse(md);
        assertEquals(1, blocks.size());
        assertInstanceOf(Block.Table.class, blocks.get(0));

        Block.Table table = (Block.Table) blocks.get(0);
        assertEquals(3, table.rows().size()); // 1 header + 2 data rows
        assertTrue(table.header());
        assertEquals(List.of(Block.Align.LEFT, Block.Align.CENTER, Block.Align.RIGHT), table.aligns());
    }

    @Test
    void testLayoutPagination() {
        // A single short paragraph on a 2x1 display should produce exactly 1 page
        List<LayoutPage> pages1 = MarkdownLayout.layout("Title", "Short content", 2, 1, TextMetrics.APPROXIMATE);
        assertEquals(1, pages1.size());
        assertEquals(0, pages1.get(0).index());
        assertEquals(1, pages1.get(0).total());

        // Single page should NOT contain page footer like "< 1/1 >"
        boolean hasPageFooter = pages1.get(0).ops().stream()
            .filter(op -> op instanceof LayoutPage.TextOp)
            .map(op -> ((LayoutPage.TextOp) op).text)
            .anyMatch(t -> t.contains("< 1/1 >") || t.contains("Page 1/1"));
        assertFalse(hasPageFooter, "Single page display should not display page footer");

        // A very long document should paginate
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            sb.append("This is line ").append(i).append(" with some longer descriptive text to take up space.\n\n");
        }
        List<LayoutPage> pagesMulti = MarkdownLayout.layout("Multi", sb.toString(), 2, 1, TextMetrics.APPROXIMATE);
        assertTrue(pagesMulti.size() > 1, "Should paginate into multiple pages");

        // Multi-page display should contain footer
        boolean hasMultiFooter = pagesMulti.get(0).ops().stream()
            .filter(op -> op instanceof LayoutPage.TextOp)
            .map(op -> ((LayoutPage.TextOp) op).text)
            .anyMatch(t -> t.startsWith("< 1/"));
        assertTrue(hasMultiFooter, "Multi page display should have a page navigation footer");
    }

    @Test
    void testMediaPolicyBlacklist() {
        // Safe when config not loaded
        assertTrue(MediaPolicy.isAllowed("https://imgur.com/image.png"));
        assertFalse(MediaPolicy.isAllowed("not a valid url :// invalid"));

        // Direct blacklist checks
        List<String> blocked = List.of("badhost.com", "phishing.org");
        assertFalse(MediaPolicy.isHostAllowed("badhost.com", blocked));
        assertFalse(MediaPolicy.isHostAllowed("sub.badhost.com", blocked));
        assertFalse(MediaPolicy.isHostAllowed("deep.sub.badhost.com", blocked));
        assertFalse(MediaPolicy.isHostAllowed("phishing.org", blocked));
        assertTrue(MediaPolicy.isHostAllowed("goodsite.com", blocked));
        assertTrue(MediaPolicy.isHostAllowed("notbadhost.com", blocked));
    }

    @Test
    void testGfmExtensions() {
        String md = """
            - [ ] Todo item
            - [x] Done item
            
            This is ~~strikethrough~~ and `inline code`.
            
            ```
            int x = 42;
            ```
            """;
        List<Block> blocks = MarkdownParser.parse(md);
        assertTrue(blocks.size() >= 3);

        // Task items
        assertInstanceOf(Block.Text.class, blocks.get(0));
        Block.Text task1 = (Block.Text) blocks.get(0);
        assertEquals("[ ]", task1.marker());
        assertEquals("Todo item", task1.spans().get(0).text());

        assertInstanceOf(Block.Text.class, blocks.get(1));
        Block.Text task2 = (Block.Text) blocks.get(1);
        assertEquals("[x]", task2.marker());
        assertEquals("Done item", task2.spans().get(0).text());

        // Strikethrough & inline code
        assertInstanceOf(Block.Text.class, blocks.get(2));
        Block.Text textBlock = (Block.Text) blocks.get(2);
        assertTrue(textBlock.spans().stream().anyMatch(s -> (s.flags() & Block.Span.STRIKE) != 0 && s.text().contains("strikethrough")));
        assertTrue(textBlock.spans().stream().anyMatch(s -> (s.flags() & Block.Span.CODE) != 0 && s.text().contains("inline code")));

        // Code block
        assertInstanceOf(Block.Code.class, blocks.get(3));
        Block.Code code = (Block.Code) blocks.get(3);
        assertEquals(List.of("int x = 42;"), code.lines());
    }
}
