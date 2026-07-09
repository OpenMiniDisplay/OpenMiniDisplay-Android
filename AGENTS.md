# OpenMiniDisplay — Agent Guide

This document is the **single source of truth** for agents working in this repository.
Keep it **fully synchronized** with the codebase at all times (see [Mandatory AGENTS.md sync](#mandatory-agentsmd-sync)).

## License

- **Apache License 2.0** — see [LICENSE](LICENSE) and [README.md](README.md)
- Copyright 2026 OpenMiniDisplay Contributors
- New source files may include: `SPDX-License-Identifier: Apache-2.0` (optional)

## Mandatory AGENTS.md sync

**Any agent that modifies this project MUST update `AGENTS.md` in the same change** so it reflects the current project state completely.

Before finishing a task, verify `AGENTS.md` still accurately describes:

1. **Architecture** — components, data flow, new modules removed/added
2. **Protocol** — TCP commands, payload formats, port, behavior changes
3. **UI rules** — display, navigation, paging, low-power, permissions
4. **Scripts** — `scripts/*.sh` commands and purpose
5. **Key files** — paths agents should read first
6. **Conventions** — patterns agents must follow

**If the wire protocol, layout schema, or controller-facing behavior changes, also update [`docs/CONTROLLER_INTEGRATION.md`](docs/CONTROLLER_INTEGRATION.md) in the same change** so external platform agents stay in sync.

If a change affects only implementation details inside an unchanged design, still update `AGENTS.md` when filenames, defaults, or workflows shift. **Do not leave stale sections.**

## Project Purpose

OpenMiniDisplay turns an old Android phone (API 28+) into a low-power, remotely controlled smart display. A foreground service listens on port **15180**; the UI is driven by a **layout document** (structure) and **data updates** (values).

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| Min SDK | 28 (Android 9.0) |
| Target SDK | 35 |
| UI | Jetpack Compose (layout-driven, full-screen immersive) |
| Networking | Java `ServerSocket` (port 15180) |
| Layout format | JSON v2 via `org.json` (cards + components) |
| Scripting | Luaj (`org.luaj:luaj-jse`) per-card Lua |
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| License | Apache-2.0 |

## Architecture

```
Controller (TCP :15180)
        │
        ▼
RemoteDisplayService ──► DisplayCommandHandler
        │                      └── DisplayStore (layout + data + props + page index)
        ├── CardScriptManager ──► LuaCardRuntime (per scripted card)
        │       └── HttpBridge (HttpURLConnection)
        ├── RuntimeState (connection + brightness + power)
        ├── PowerState / ChargeLimitManager
        └── ScreenManager (Application singleton via OpenMiniDisplayApp)
                    │
        ┌───────────┴────────────┐
        ▼                        ▼
 MainActivity / DisplayHost   PitchBlackActivity
 (layout UI)                   (post-dim black screen)
```

### Core runtime components

| Component | Role |
|-----------|------|
| `RemoteDisplayService` | Foreground service, TCP listener, heartbeat, command dispatch |
| `DisplayCommandHandler` | Parses `SET` / `LAYOUT` / `PATCH` / `GOTO` |
| `DisplayStore` | Layout structure, component values (`cardId:componentId`), props, page index |
| `CardScriptManager` | Luaj runtimes for cards with `script`; timers, HTTP, IO events. **`syncLayout` runs on every `LAYOUT`/`PATCH`/`resumeAll`** (not via layout StateFlow — equal layouts must still restart scripts) |
| `LuaCardRuntime` | Single-card Lua state, lifecycle hooks, host API |
| `RuntimeState` | Connection state, screen brightness, and power plug status |
| `PowerState` | Reads whether the device is connected to external power |
| `ChargeLimitManager` | Best-effort 80% charge limit when plugged in (OEM/settings dependent) |
| `OpenMiniDisplayApp` | Application entry; singleton `ScreenManager`; tracks resumed `MainActivity` for plugged idle dim |
| `ScreenManager` | Brightness, wake locks, low-power transitions |
| `DisplayHost` | Full-screen pager + page dots |
| `PageRenderer` | Page grid of cards; single-component card pages are borderless |
| `CardRenderer` | Inner component grid within a card |
| `ComponentRenderer` | Renders display + IO components |
| `PitchBlackActivity` | Black screen after dim completes |

## UI rules

- Full-screen immersive — hide status/navigation bars (`MainActivity`)
- Landscape default (`AndroidManifest`)
- **No vertical scrolling** on display pages
- **1 page** → static view, **no swipe**
- **2+ pages** → infinite horizontal swipe (wrap: first ↔ last) + bottom page dots
- **`GOTO`** → `animateScrollToPage` (animated, shortest circular path)
- **1 card on a page** with **1 component** → borderless full-screen
- **2+ cards on a page** → card chrome + page grid
- **2+ components in a card** → inner grid (no per-component chrome)

Component types: `text`, `metric`, `progress`, `ring`, `line`, `bar`, `pie`, `button`, `toggle`, `image`.

## Protocol (Port 15180)

Newline-terminated UTF-8 text over TCP.

**External controller spec (normative for PC / cross-platform clients):** [`docs/CONTROLLER_INTEGRATION.md`](docs/CONTROLLER_INTEGRATION.md)  
**Examples:** [`docs/examples/README.md`](docs/examples/README.md)

| Command | Effect |
|---------|--------|
| `OPENMINIDISPLAY` / `CONNECT` | Handshake → **CONNECTED** |
| `PING` | Heartbeat (5 s timeout → **DISCONNECTED**) |
| `SET <cardId>/<componentId> <value>` | Update component data (counts as activity) |
| `PUSH <assetId> <base64>` | Store binary asset (e.g. GIF); reference with `SET … asset:<assetId>` |
| `LAYOUT <json>` | Replace entire layout |
| `PATCH <json>` | Merge/replace pages by `id` |
| `GOTO <index\|pageId>` | Switch page with slide animation |

### Layout JSON (v2)

```json
{
  "version": 2,
  "pages": [
    {
      "id": "overview",
      "grid": { "rows": 3, "cols": 4, "gap": 8, "padding": 16 },
      "cards": [
        {
          "id": "weather",
          "row": 0, "col": 0, "rowSpan": 2, "colSpan": 1,
          "grid": { "rows": 3, "cols": 1, "gap": 4, "padding": 8 },
          "script": "function on_init() set('temp','--') end",
          "components": [
            { "id": "title", "type": "text", "row": 0, "col": 0, "style": "headline" },
            { "id": "refresh", "type": "button", "row": 2, "col": 0, "label": "Refresh" }
          ]
        }
      ]
    }
  ]
}
```

Per-component fields: `id`, `type`, `row`, `col`, `rowSpan`, `colSpan`, optional `style`, optional `label`, optional `checked` (toggle initial state).
Optional presentation: `align` (`start`/`center`/`end`), `fill` (use full grid cell), `fit` (auto-fit text to cell), `scale` (0.2–1.0, ring/charts fill fraction), `showLabel` (override chart/ring label).
Single-component borderless pages still default to `fill` + center when `fit` is set.
Optional per-card `script` (Lua source). Layout **v2 only** (`pages` → `cards` → `components`).

### Card Lua API (host-provided globals)

| Function | Purpose |
|----------|---------|
| `set(id, value)` | Update component value in this card |
| `set_prop(id, key, val)` | `label`, `enabled`, `checked`, `align`, `fill`, `fit`, `scale`, `showLabel` |
| `every(sec, name)` / `cancel(name)` | Repeating timer → `on_timer(name)` |
| `http_get(url, fn)` | Async GET → `fn(status, body_table, err)` |
| `local_time()` | Device local time as `HH:mm:ss` (24h) |
| `wake()` | Exit idle / restore brightness (same as user touch when disconnected) |
| `log(msg)` | Logcat tag `CardScript` |

Lifecycle: `on_init`, `on_timer`, `on_event(id, event, value?)`, `on_destroy`.

### Data formats (`SET`)

| Type | Example |
|------|---------|
| text / metric | `SET weather/temp Hello` |
| progress / ring | `SET dash/cpu 72` |
| line / bar | `SET dash/line 10,20,15,30` |
| pie | `SET dash/pie CPU:30,MEM:25,IO:20` |
| image | `PUSH hero.gif <base64>` then `SET card/img asset:hero.gif` |

## Low-power behavior

Power source affects screen and charging policy.

| Power | Connection | Screen behavior |
|-------|------------|-----------------|
| **Plugged in** | CONNECTED | Wake lock, prevent lock, max brightness |
| **Plugged in** | DISCONNECTED (idle) | After 60 s: 10 s dim → `PitchBlackActivity` (screen stays on) |
| **On battery** | CONNECTED | Wake lock, prevent lock (active display) |
| **On battery** | DISCONNECTED (idle) | After 60 s: **battery deep idle** — stop TCP listener, release wake/Wi‑Fi locks, allow lock/sleep |
| **Battery deep idle** | — | **No TCP listener**; controller cannot connect until user wakes the device (open app / tap notification) |

| Phase | Behavior |
|-------|----------|
| **CONNECTED** | Screen wake lock, max brightness, dashboard visible |
| **DISCONNECTED** | UI stays on dashboard; 60 s countdown to low-power |
| **Dimming (plugged only)** | Linear 10 s fade (~60 fps); content stays visible during dim |
| **After dim (plugged only)** | Navigate to `PitchBlackActivity` (content hidden, screen stays on), **unless card Lua timers are active** → hold ~20% brightness on dashboard |
| **Battery low-power** | Release service wake/Wi‑Fi locks; clear keep-screen-on; finish UI task |
| **Battery deep idle** | Stop TCP listener; **pause card scripts**; notification shows sleep state; wake restores listener + scripts |
| **User touch / power plug or unplug** (while disconnected) | Restore brightness, return to dashboard, reset 60 s timer |
| **Leave dashboard** (settings, home, task switch) | Restore pre-display **system** brightness (`ScreenManager.restoreUserBrightness`); return to dashboard restores display brightness when connected |
| **Plugged in** | Try to enable **80% charge limit** via OEM/settings keys when permitted (`WRITE_SETTINGS` / device support) |
| **Unplugged** | Restore previous charge-limit setting if app had applied one |

## Build & device scripts

Gradle and **adb run inside** [android-dev-docker](https://github.com/Nigh/android-dev-docker) (`xianii/android-dev:latest`). Do **not** use host `adb`. Build/adb/shell default to **host uid** (`--user`); USB uses `--device=/dev/bus/usb`, `--group-add plugdev`, mount `~/.gradle`, `~/.android`, `~/.adb`. See [`docs/ANDROID_DEV_CONTAINER.md`](docs/ANDROID_DEV_CONTAINER.md).

```bash
docker pull xianii/android-dev:latest   # once
mkdir -p ~/.gradle ~/.android ~/.adb
```

```bash
./scripts/dev.sh devices
./scripts/dev.sh build                  # Docker + ./gradlew assembleDebug (--user)
./scripts/dev.sh install                # uninstall + install debug APK
./scripts/dev.sh run
./scripts/dev.sh logs
./scripts/dev.sh debug                  # build + install + launch + logs
./scripts/dev.sh shell                  # interactive container (USB adb, --user)
```

Scripts read `LISTEN_PORT=15180` from `scripts/common.sh`. Use `ANDROID_SERIAL=...` when multiple devices are connected. Override the image with `ANDROID_DEV_IMAGE=...`. Set `ANDROID_DEV_ROOT=1` only if you need container-root Gradle/shell (not recommended).

Persistent host dirs (always mounted):

| Path | Purpose |
|------|---------|
| `~/.gradle` | Gradle cache |
| `~/.android` | debug signing keystore |
| `~/.adb` | adb USB keys (**not** the same as `~/.android/adbkey` used by Android Studio) |

If USB authorization breaks after mixing host and container adb, revoke authorizations on the device and run `./scripts/dev.sh devices` again.

```bash
./scripts/layout-test.sh                # LAYOUT v2 + SET loop + GOTO pages
./scripts/widget-test.sh                # SET loop (default layout)
./scripts/chart-test.sh                 # chart SET loop
./scripts/card-script-test.sh [phone-ip]  # Lua card layout, PING only (IP arg skips adb)
./scripts/connect-test.sh               # handshake smoke test
./demo_script/clock-pomodoro/run.sh [phone-ip]  # clock + Pomodoro two-page demo
./demo_script/gif-gallery/run.sh [phone-ip]      # 3-page animated GIF gallery
```

## Key files

```
app/src/main/java/com/openminidisplay/
├── OpenMiniDisplayApp.kt
├── RemoteDisplayService.kt
├── RuntimeState.kt
├── PowerState.kt
├── ChargeLimitManager.kt
├── ScreenManager.kt
├── MainActivity.kt
├── PitchBlackActivity.kt
└── display/
    ├── DisplayStore.kt
    ├── DisplayAssetStore.kt
    ├── DefaultDisplayLayout.kt
    ├── model/DisplayModels.kt
    └── protocol/
        ├── DisplayCommandHandler.kt
        └── DisplayLayoutParser.kt
└── script/
    ├── CardScriptManager.kt
    ├── LuaCardRuntime.kt
    ├── LuaHostApi.kt
    ├── HttpBridge.kt
    ├── LuaSandbox.kt
    └── LuaSandboxSelfCheck.kt
└── ui/
    ├── display/
    │   ├── DisplayHost.kt
    │   ├── PageRenderer.kt
    │   ├── CardRenderer.kt
    │   ├── CardContainer.kt
    │   └── ComponentRenderer.kt
    └── widgets/
        ├── ChartWidgets.kt
        ├── TextWidget.kt
        ├── ButtonComponent.kt
        ├── ToggleComponent.kt
        └── ImageWidget.kt
scripts/
├── common.sh
├── dev.sh
├── layout-test.sh
├── widget-test.sh
├── chart-test.sh
├── card-script-test.sh
└── connect-test.sh
demo_script/
├── README.md
└── clock-pomodoro/     # clock + Pomodoro two-page demo
    ├── README.md
    ├── clock.lua
    ├── pomodoro.lua
    └── run.sh
└── gif-gallery/        # 3-page GIF gallery (mahiro / miku / cats)
    ├── README.md
    ├── *.gif
    └── run.sh
docs/
├── CONTROLLER_INTEGRATION.md   # normative spec for PC / cross-platform controllers
├── ANDROID_DEV_CONTAINER.md    # Docker image USB adb requirements
├── examples/
│   ├── README.md
│   ├── reference_client.py
│   ├── minimal_layout.json
│   └── sample_card.lua
└── schemas/layout.v2.schema.json
LICENSE
README.md
AGENTS.md
```

## Agent conventions

1. **Sync `AGENTS.md`** — mandatory with every substantive change (see above).
2. **Sync `docs/CONTROLLER_INTEGRATION.md`** — mandatory when protocol, layout schema, or controller-visible behavior changes.
3. Keep **layout and data separate** — no values embedded in layout JSON.
4. Preserve **single-component card borderless** rendering on single-card pages.
5. **No vertical scroll** on display pages.
6. Extend **`ComponentType` + `ComponentRenderer`** instead of hardcoding UI in `MainActivity`.
7. Preserve **infinite pager** behavior for multi-page layouts.
8. Prefer minimal diffs; avoid new dependencies unless clearly necessary.
9. Test on device when behavior changes: `./scripts/layout-test.sh`, `./scripts/card-script-test.sh`, `./demo_script/clock-pomodoro/run.sh`, or `./scripts/dev.sh debug`.
10. Do not commit secrets (`local.properties`, keystores, tokens).

## Repository

- Remote: `git@github.com:OpenMiniDisplay/OpenMiniDisplay-Android.git`
- Ignored: `local.properties`, `build/`, `.gradle/`, `.idea/` (see `.gitignore`)
