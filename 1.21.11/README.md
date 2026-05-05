# 🗺️ Path Tracer

**See exactly where you've been.** Path Tracer renders a real-time color heatmap overlay on the ground showing your most-traveled routes — no commands, no maps, just a live visual layer on your world.

---

## How It Works

Every block you walk on gets tracked. The more you travel over a spot, the hotter its color becomes:

🟢 **Green** → lightly traveled  
🟡 **Yellow** → moderate use  
🟠 **Orange** → frequent path  
🔴 **Red** → heavily worn route

Tiles below your minimum threshold stay invisible, so only your real paths show up.

> Path Tracer records the block you're standing on **plus the 8 surrounding blocks** with each step, creating a natural-looking footprint rather than a single-block trail.

---

## Features

- **Live heatmap overlay** — updates as you walk, no reload needed
- **Fully configurable** via Mod Menu — adjust min/max walk counts, render radius, and how long data persists
- **Toggle on/off** with a keybind (default: `H`)
- **Iris shader compatible** — overlay renders correctly with shader packs active
- **Client-side only** — works on any server, no mod required server-side
- **No world modifications** — purely visual, nothing written to your save

---

## Configuration (via Mod Menu)

| Setting | Description |
|---|---|
| **Min Walk Count** | How many passes before a tile becomes visible |
| **Max Walk Count** | The count at which a tile reaches full red |
| **Render Radius** | How far from the player tiles are drawn (in blocks) |
| **Max Age** | How many in-game days before old data fades out |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop the `path-tracer-x.x.x.jar` into your `.minecraft/mods` folder
4. *(Optional)* Install [Mod Menu](https://modrinth.com/mod/modmenu) for in-game configuration

---

## Compatibility

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Mod Loader** | Fabric |
| **Mod Menu** | Optional, recommended |
| **Iris / Sodium** | Compatible |
| **Server-side** | Not required |

---

## Building from Source

Requires Java 21.

```bash
export JAVA_HOME=/path/to/java21
./gradlew build
```

Output jar will be in `build/libs/`.

---

## License

[MIT](LICENSE) — free to use, modify, and include in modpacks.

---

*Path Tracer is a lightweight client mod — your server friends will never know it's there.*
