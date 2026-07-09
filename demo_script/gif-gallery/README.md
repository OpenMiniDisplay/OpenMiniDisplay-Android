# GIF gallery

Three-page animated GIF demo for OpenMiniDisplay.

| Page | Content |
|------|---------|
| 1 — **mahiro** | Full-screen `mahiro.gif` |
| 2 — **miku** | Full-screen `miku.gif` |
| 3 — **cats** | `cat1.gif` (left) and `cat2.gif` (right), side by side |

GIFs are sent over TCP with **`PUSH`** (base64 payload), then bound to `image` components via **`SET … asset:<id>`**. No adb file transfer.

## Run

```bash
./demo_script/gif-gallery/run.sh [phone-ip]
```

Requires APK with `image` component + `PUSH` support. Phone on USB: omit IP. Same LAN: pass display IP.

## Wire example

```
PUSH mahiro.gif <base64…>
SET mahiro/img asset:mahiro.gif
```

Order: `LAYOUT` → `PUSH` each asset → `SET` each component → `PING` loop.
