package de.gener.mcdisplays.content;

import java.util.List;
import java.util.Objects;

public record DisplayDocument(String title, List<String> pages) {
    public DisplayDocument {
        title = title == null || title.isBlank() ? "Display" : title.trim();
        pages = pages == null
            ? List.of("No content available.")
            : pages.stream()
                .filter(Objects::nonNull)
                .map(DisplayDocument::normalize)
                .toList();

        if (pages.isEmpty()) {
            pages = List.of("No content available.");
        }

        pages = List.copyOf(pages);
    }

    public static DisplayDocument placeholder() {
        return new DisplayDocument(
            "MC Displays",
            List.of("Right-click the panel with a supported item to insert it directly, or sneak-right-click to open the source menu.")
        );
    }

    public static DisplayDocument message(String title, String... lines) {
        return new DisplayDocument(title, List.of(String.join("\n", lines)));
    }

    private static String normalize(String page) {
        return page.replace("\r\n", "\n").replace('\r', '\n');
    }
}