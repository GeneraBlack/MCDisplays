# Short Description

Turn books, Markdown notes, and MineColonies logistics into modular multiblock wall displays.

# Long Description

MC Displays adds clean, rectangular multiblock display panels for Minecraft 1.20.1 on Forge. Build a display wall, insert a supported item, and the screen will render its content directly in the world.

The mod is designed for practical survival use as well as colony management. Vanilla books work out of the box, the Markdown Pad lets players write custom screen content in-game, and optional MineColonies integration lets you surface builder resource lists and clipboard request data on large wall-mounted panels.

## Features

- Build rectangular multiblock display walls from individual display panels.
- Show written books, books and quills, Markdown Pads, MineColonies Resource Scrolls, and MineColonies Clipboards directly in the world.
- Render larger multiblock displays with improved text scaling across the available screen area.
- Refresh live content automatically on a configurable interval.
- Turn pages manually with an empty hand when enabled.
- Protect displays with ownership and private mode.
- Illuminate displays with Glow Ink Sacs for bright text, outline rendering, and a visible lit frame.
- Configure refresh interval and maximum display size through the Forge server config.
- Keep colony and builder links separated from item data, which is especially useful on multiplayer servers with multiple colonies.

## Markdown Pad

The Markdown Pad is a dedicated item for writing custom display content directly in Minecraft.

- Edit text in an in-game screen.
- Preview how the content will look on a display.
- Store title and content inside the item.
- Place the item into a display just like a book.

## Markdown Support

Markdown rendering is tuned for in-world display readability.

- Headings using # and underline-style headings
- Ordered lists, unordered lists, and task lists
- Nested blockquotes
- Bold, italic, strikethrough, and inline code
- Fenced code blocks with preserved spacing
- Pipe tables with alignment support
- Links converted into readable text output
- Color tags such as [red], [green], [blue], [color=#ff8800], [#55ff55], and [/]

This makes the Markdown Pad useful for signs, dashboards, colony planning boards, task trackers, and wall-mounted information panels.

## MineColonies Integration

- Display MineColonies Resource Scroll data.
- Display MineColonies Clipboard request data.
- Refresh live colony information instead of relying on stale snapshots.

MineColonies support is optional, but required for Resource Scroll and Clipboard displays.
The backport reads the legacy MineColonies 1.20.1 item data format used by older resource scroll and clipboard items.

## Controls

- Right-click a display with a supported item to insert it directly.
- Right-click with a Glow Ink Sac to enable illuminated mode.
- Right-click with an Ink Sac to disable illuminated mode.
- Sneak-right-click to open the source menu.
- Use an empty hand to turn pages when manual page turning is enabled.

## Multiplayer

- Displays are claimed automatically by the first player who edits them.
- Private displays can only be edited by their owner.
- Ownership, privacy, and illumination are shared across the whole rectangular multiblock.

## Compatibility

- Minecraft 1.20.1
- Forge 47.4.20

## Notes

Clipboard content is generated from live MineColonies request data, so the display reflects the current state of the linked colony instead of a static snapshot.

Markdown support is intentionally tuned for practical in-world display use rather than full browser-grade rendering.
