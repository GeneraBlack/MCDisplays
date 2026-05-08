# MC Displays

Turn books and MineColonies logistics into modular wall displays.

MC Displays adds clean, rectangular multiblock display panels for Minecraft 1.21.1 on NeoForge. Build a display wall, insert a supported item, and the screen will render its content directly in the world.

The mod is designed for practical survival use as well as colony management. Vanilla books work out of the box, and MineColonies integration lets you surface builder resource lists and clipboard request data on large wall-mounted panels.

## Features

- Build rectangular multiblock display walls from individual display panels.
- Show written books and books and quills directly in the world.
- Display MineColonies Resource Scroll data.
- Display MineColonies Clipboard request data.
- Refresh content automatically on a configurable interval.
- Turn pages manually with an empty hand when enabled.
- Configure refresh interval and maximum display size in-game.
- Keeps colony and builder links separated from item data, which is especially important for multiplayer servers with multiple colonies.

## Controls

- Right-click a display with a supported item to insert it directly.
- Sneak-right-click to open the source menu.
- With an empty hand, use the display to turn pages if manual page turning is enabled.

## Supported Sources

- Written Book
- Book and Quill
- MineColonies Resource Scroll
- MineColonies Clipboard

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
- Publish CurseForge workflow: publishes the mod jar to CurseForge on a pushed version tag, on a published GitHub Release, or via manual dispatch.

### Required GitHub configuration

Before the CurseForge workflow can publish anything, configure the following in the repository settings:

- Repository variable: CURSEFORGE_PROJECT_ID
- Repository secret: CURSEFORGE_TOKEN

### How publishing works

- Tag trigger: push a version tag such as 1.0.0 or v1.0.0 and the workflow uploads the built jar to CurseForge.
- Release trigger: publish a GitHub Release and the workflow uploads the built jar to CurseForge.
- Manual trigger: run the Publish CurseForge workflow from the Actions tab and provide an optional version, display name, changelog, and release channel.

For release-triggered uploads, prereleases are published as beta files and normal releases are published as release files.
