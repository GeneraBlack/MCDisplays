# MC Displays

Turn books, Markdown notes, and MineColonies logistics into modular wall displays.

MC Displays adds clean, rectangular multiblock display panels for Minecraft 1.21.1 on NeoForge. Build a display wall, insert a supported item, and the screen will render its content directly in the world.

The mod is designed for practical survival use as well as colony management. Vanilla books work out of the box, the Markdown Pad lets players author custom screen content in-game, and MineColonies integration lets you surface builder resource lists and clipboard request data on large wall-mounted panels.

## Highlights

- Build rectangular multiblock display walls from individual display panels.
- Show written books, books and quills, Markdown Pads, MineColonies Resource Scrolls, and MineColonies Clipboards directly in the world.
- Render formatted Markdown with headings, lists, task lists, blockquotes, tables, inline code, fenced code blocks, and links converted into readable display text.
- Use color tags such as [red], [blue], [color=#ff8800], or [#55ff55] inside Markdown Pad content.
- Refresh live content automatically on a configurable interval.
- Protect displays with ownership and private mode.
- Illuminate displays with Glow Ink Sacs and remove illumination with regular Ink Sacs.
- Use glowing text outlines and a visible lit frame for better readability at distance.
- Scale text more effectively across larger multiblock displays.

## Features

- Build rectangular multiblock display walls from individual display panels.
- Show written books and books and quills directly in the world.
- Create custom display content with the Markdown Pad item and its live preview editor.
- Display MineColonies Resource Scroll data.
- Display MineColonies Clipboard request data.
- Render richer formatting for Markdown Pad content, including colors and structured blocks.
- Refresh content automatically on a configurable interval.
- Turn pages manually with an empty hand when enabled.
- Claim displays automatically on first edit and restrict them to the owner when private mode is enabled.
- Toggle illuminated mode with Glow Ink Sacs for brighter, outlined text.
- Configure refresh interval and maximum display size in-game.
- Keeps colony and builder links separated from item data, which is especially important for multiplayer servers with multiple colonies.

## Controls

- Right-click a display with a supported item to insert it directly.
- Right-click a display with a Glow Ink Sac to enable illuminated mode.
- Right-click an illuminated display with a regular Ink Sac to disable illuminated mode.
- Sneak-right-click to open the source menu.
- With an empty hand, use the display to turn pages if manual page turning is enabled.
- Sneak-right-click with an empty hand to remove the current source item.

## Supported Sources

- Written Book
- Book and Quill
- Markdown Pad
- MineColonies Resource Scroll
- MineColonies Clipboard

## Markdown Support

The Markdown Pad is intended for practical in-game signage, dashboards, notes, and colony boards.

Supported formatting includes:

- ATX headings using # through ######
- Setext headings using underline-style = and -
- Ordered lists, unordered lists, and task lists
- Nested blockquotes
- Inline emphasis such as bold, italic, strikethrough, and inline code
- Fenced code blocks with preserved spacing
- Pipe tables with basic alignment markers
- Inline links converted into readable text output
- Color tags such as [red], [green], [blue], [color=#ff8800], [#55ff55], and [/] to return to the previous color

For details, examples, and current limitations, see [docs/markdown-support.md](docs/markdown-support.md).

## Multiplayer And Ownership

- Displays are claimed automatically by the first player who edits them.
- Private mode prevents other players from changing the source item, page state, glow mode, or privacy setting.
- Ownership and privacy are shared across the full rectangular multiblock.

## Illumination

- Glow Ink Sacs enable illuminated display mode.
- Ink Sacs disable illuminated display mode.
- Illuminated displays render text at full brightness, add an outline for readability, and show a lit frame on the display surface.

## Configuration

MC Displays includes an in-game configuration screen. You can adjust:

- Refresh interval
- Maximum display width
- Maximum display height
- Manual page turning

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.218
- MineColonies support is optional, but required for Resource Scroll and Clipboard displays.

## Notes

Clipboard content is generated from live MineColonies request data, so the display reflects the current state of the linked colony instead of a static snapshot.

Markdown support is intentionally display-oriented rather than a full browser-grade Markdown implementation. The goal is readable, practical in-world output on Minecraft display walls.

## Development

Build the mod with:

```bash
./gradlew build
```

Start the development client with:

```bash
./gradlew runClient
```

For MineColonies integration tests in the dev client, the project includes local runtime dependencies for the MineColonies stack.

## CurseForge Assets

Repository branding assets for publishing are stored in the branding folder, including a 100x100 icon and an English CurseForge description draft.

## Publishing

The repository includes two GitHub Actions workflows:

- Build workflow: runs on pushes, pull requests, and manual dispatch, then uploads the built jar as an artifact.
- Publish CurseForge workflow: publishes the mod jar to CurseForge on a pushed version tag or via manual dispatch.

### Required GitHub configuration

Before the CurseForge workflow can publish anything, configure the following in the repository settings:

- Repository variable: CURSEFORGE_PROJECT_ID
- Repository secret: CURSEFORGE_TOKEN

### How publishing works

- Tag trigger: push a version tag such as 1.0.0 or v1.0.0 and the workflow uploads the built jar to CurseForge.
- Manual trigger: run the Publish CurseForge workflow from the Actions tab and provide an optional version, display name, changelog, and release channel.
