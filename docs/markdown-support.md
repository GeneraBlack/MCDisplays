# Markdown Support

MC Displays includes a Markdown Pad item for authoring custom display content directly in-game. This document describes the Markdown-oriented formatting features currently supported by the renderer.

## Design Goal

Markdown support in MC Displays is designed for readable wall displays inside Minecraft, not for pixel-perfect browser rendering. The implementation focuses on practical formatting for signs, dashboards, colony boards, task lists, and status walls.

## Supported Sources

- Markdown Pad items created and edited in-game

## Supported Block-Level Features

- ATX headings using # through ######
- Setext headings using underline-style = and -
- Ordered lists
- Unordered lists
- Task lists
- Nested blockquotes
- Horizontal rules
- Fenced code blocks using ``` or ~~~
- Pipe tables with basic alignment markers

## Supported Inline Features

- Bold using **text** or __text__
- Italic using *text* or _text_
- Strikethrough using ~~text~~
- Inline code using `code`
- Inline links converted into readable text such as Label <https://example.com>
- Images converted into placeholder text such as [Image: Alt Text]

## Color Tags

The Markdown Pad also supports display-specific color tags.

Supported styles include:

- Named colors such as [red], [green], [blue], [yellow], [white], [black], [orange], [cyan], [purple], and more
- Hex colors such as [#ff8800]
- Explicit hex tags such as [color=#ff8800]
- RGB tags such as [color=rgb(255, 128, 0)]
- Nested color scopes
- Reset and pop tags such as [/], [/color], [reset], and [default]

### Example

```md
# Colony Board

[gold]Builder Queue[/]
- [x] Town Hall
- [ ] Warehouse

> [blue]Bring more planks[/]

| Item | Amount |
| --- | ---: |
| Oak Planks | [green]128[/] |
| Glass | [#55ccff]48[/] |
```

## Code Blocks And Tables

- Fenced code blocks preserve spacing better than regular wrapped text.
- Pipe tables are rendered into a display-friendly aligned layout.
- Larger multiblock displays are recommended for tables and code blocks.

## Multiplayer And Ownership

- Markdown Pad content can be displayed like any other supported source.
- Display privacy rules still apply when the content source is a Markdown Pad.
- Illumination and glow effects work with Markdown Pad content as well.

## Known Limitations

- Markdown rendering is display-oriented and does not aim to implement every CommonMark edge case.
- HTML blocks and embedded HTML are not supported.
- Links are shown as readable text, but they are not clickable.
- Images are represented by text placeholders instead of rendered graphics.
- Advanced Markdown features such as footnotes, reference-style links, and full HTML tables are not supported.
- Very wide tables or long code lines may still require a larger multiblock display for best readability.

## Recommended Authoring Tips

- Use wider multiblock displays for tables, dashboards, and code-heavy content.
- Use illuminated mode when the display needs to remain readable in darker builds.
- Keep headings short so they remain visually strong on smaller displays.
- Use color tags sparingly for emphasis, warnings, or item counts.