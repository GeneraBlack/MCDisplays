# MC Displays 1.3.0 (NeoForge 1.21.1)

Welcome to MC Displays 1.3.0! This release introduces the **Cinema Projector** system with real-time video streaming, spatial cinema acoustics, remote linking, modern player UI, alongside major Markdown Pad and display improvements.

---

### 🎬 Cinema Projector & Video Streaming
- **Cinema Projector Block:** Place a projector and stream videos, live streams, and media directly onto any size modular screen cluster (e.g. 16x9, 8x4, or massive cinema walls).
- **Cinema Remote Control:** Bind any screen cluster to a projector with right-click linking. Projector stores cluster coordinates and resolution.
- **WATERMeDIA Integration:** Seamlessly streams direct MP4, WebM, HLS (`.m3u8`), Twitch, Kick, and YouTube (with the WATERMeDIA YouTube addon).
- **Borderless Fullscreen Auto-Scaling:** Videos are cleanly scaled to the display wall geometry without distortion or letterbox borders.
- **Nighttime Full-Bright Cinema Lighting:** Displays render cinema feeds with full brightness in the dark, matching a true theater experience.

### 🔊 Spatial Cinema Acoustics & Real-Time Controls
- **Flexible Sound Origin:** Choose whether audio emanates from the **Cinema Projector block** (e.g. booth speaker) or the **Screen wall** via a 1-click toggle button.
- **Continuous Camera-Independent Ticking:** Audio volume and 3D falloff track player movement smoothly even when the player looks away or walks around the cinema room (independent of frustum culling).
- **Linear Sound Falloff:** Natural sound fade across the configured distance without sudden cutoffs.
- **Zero-Latency Slider Feedback:** Volume (0–100%) and Range (8–128m) adjustments update immediately in your headphones and synchronize across the network.

### 🖥️ Modern Projector Interface (480x320)
- **Spacious & Clean Layout:** Plenty of space for long URLs, clipboard paste button (`Paste`), quick-clear (`X`), and playback controls.
- **Live Status Badges:** Visual indicators for Link status, Playback state (Playing / Standby / Idle), and Video Engine availability.
- **Diagnostics & Error Reporting:** Real-time feedback for loading, buffering, or format errors.

### 📝 Markdown Pad & Display Enhancements
- **Live In-Game Preview:** Interactive WYSIWYG preview pane side-by-side with the editor.
- **Syntax Wiki Sidebar:** Built-in markdown documentation with 1-click formatting insertion.
- **Remote Image Loading:** Asynchronous image loading with disk caching and URL host blacklist protection.
- **Improved Layout Engine:** Better text wrapping, table formatting, and proportional font scaling.

### 📦 Crafting & Recipes
- **Cinema Projector Recipe:** Craftable using Redstone Lamps, Smooth Stone, Iron Ingots, and a Daylight Detector.
- **Cinema Remote Recipe:** Craftable using Iron Ingots, Redstone Dust, and an Ender Pearl.
- **Custom Pixel-Art Textures:** Hand-crafted textures for Projector (active/inactive), Remote, and Markdown Pad.
