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
| Layout format | JSON via `org.json` (no extra deps) |
| Build | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) |
| License | Apache-2.0 |

## Architecture

```
Controller (TCP :15180)
        │
        ▼
RemoteDisplayService ──► DisplayCommandHandler
        │                      └── DisplayStore (layout + data + page index)
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
| `DisplayStore` | Layout structure, widget values, and page index |
| `RuntimeState` | Connection state, screen brightness, and power plug status |
| `PowerState` | Reads whether the device is connected to external power |
| `ChargeLimitManager` | Best-effort 80% charge limit when plugged in (OEM/settings dependent) |
| `OpenMiniDisplayApp` | Application entry; holds singleton `ScreenManager` |
| `ScreenManager` | Brightness, wake locks, low-power transitions |
| `DisplayHost` | Full-screen pager + page dots |
| `PageRenderer` | Grid layout; single-widget pages are borderless |
| `PitchBlackActivity` | Black screen after dim completes |

## UI rules

- Full-screen immersive — hide status/navigation bars (`MainActivity`)
- Landscape default (`AndroidManifest`)
- **No vertical scrolling** on display pages
- **1 page** → static view, **no swipe**
- **2+ pages** → infinite horizontal swipe (wrap: first ↔ last) + bottom page dots
- **`GOTO`** → `animateScrollToPage` (animated, shortest circular path)
- **1 widget on a page** → borderless full-screen (`showChrome = false`)
- **2+ widgets on a page** → card chrome + grid layout

Widget types: `text`, `metric`, `progress`, `ring`, `line`, `bar`, `pie`.

## Protocol (Port 15180)

Newline-terminated UTF-8 text over TCP.

**External controller spec (normative for client authors):** [`docs/CONTROLLER_INTEGRATION.md`](docs/CONTROLLER_INTEGRATION.md)

| Command | Effect |
|---------|--------|
| `OPENMINIDISPLAY` / `CONNECT` | Handshake → **CONNECTED** |
| `PING` | Heartbeat (5 s timeout → **DISCONNECTED**) |
| `SET <id> <value>` | Update widget data (counts as activity) |
| `LAYOUT <json>` | Replace entire layout |
| `PATCH <json>` | Merge/replace pages by `id` |
| `GOTO <index\|pageId>` | Switch page with slide animation |

### Layout JSON

```json
{
  "version": 1,
  "pages": [
    {
      "id": "overview",
      "grid": { "rows": 3, "cols": 4, "gap": 8, "padding": 16 },
      "widgets": [
        { "id": "title", "type": "text", "row": 0, "col": 0, "colSpan": 4, "style": "headline" }
      ]
    }
  ]
}
```

Per-widget fields: `id`, `type`, `row`, `col`, `rowSpan`, `colSpan`, optional `style` (`headline|body|caption|metric`), optional `label`.

### Data formats (`SET`)

| Type | Example |
|------|---------|
| text / metric | `SET title Hello` |
| progress / ring | `SET progress 72` |
| line / bar | `SET line 10,20,15,30` |
| pie | `SET pie CPU:30,MEM:25,IO:20` |

## Low-power behavior

Power source affects screen and charging policy.

| Power | Connection | Screen behavior |
|-------|------------|-----------------|
| **Plugged in** | CONNECTED | Wake lock, prevent lock, max brightness |
| **Plugged in** | DISCONNECTED (idle) | After 60 s: 10 s dim → `PitchBlackActivity` (screen stays on) |
| **On battery** | CONNECTED | Wake lock, prevent lock (active display) |
| **On battery** | DISCONNECTED (idle) | After 60 s: allow system lock / sleep (no dim, no pitch-black) |

| Phase | Behavior |
|-------|----------|
| **CONNECTED** | Screen wake lock, max brightness, dashboard visible |
| **DISCONNECTED** | UI stays on dashboard; 60 s countdown to low-power |
| **Dimming (plugged only)** | Linear 10 s fade (~60 fps); content stays visible during dim |
| **After dim (plugged only)** | Navigate to `PitchBlackActivity` (content hidden, screen stays on) |
| **Battery low-power** | Clear keep-screen-on; move task to back; system may lock/sleep |
| **User touch** (while disconnected) | Restore brightness, return to dashboard, reset 60 s timer |
| **Plugged in** | Try to enable **80% charge limit** via OEM/settings keys when permitted (`WRITE_SETTINGS` / device support) |
| **Unplugged** | Restore previous charge-limit setting if app had applied one |

## Build & device scripts

```bash
./scripts/dev.sh devices
./scripts/dev.sh build
./scripts/dev.sh install
./scripts/dev.sh run
./scripts/dev.sh logs
./scripts/dev.sh debug          # build + install + launch + logs

./scripts/layout-test.sh        # LAYOUT + SET loop + GOTO pages
./scripts/widget-test.sh        # SET loop (default layout)
./scripts/chart-test.sh         # chart SET loop
./scripts/connect-test.sh       # handshake smoke test
```

Scripts read `LISTEN_PORT=15180` from `scripts/common.sh`. Use `ANDROID_SERIAL=...` when multiple devices are connected.

Gradle directly: `./gradlew assembleDebug` / `./gradlew installDebug`

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
    ├── DefaultDisplayLayout.kt
    ├── model/DisplayModels.kt
    └── protocol/
        ├── DisplayCommandHandler.kt
        └── DisplayLayoutParser.kt
└── ui/
    ├── display/
    │   ├── DisplayHost.kt
    │   ├── PageRenderer.kt
    │   ├── WidgetSlotContainer.kt
    │   └── WidgetRenderer.kt
    └── widgets/
        ├── ChartWidgets.kt
        └── TextWidget.kt
scripts/
├── common.sh
├── dev.sh
├── layout-test.sh
├── widget-test.sh
├── chart-test.sh
└── connect-test.sh
docs/
├── CONTROLLER_INTEGRATION.md   # external controller / cross-platform agent spec
├── schemas/layout.v1.schema.json
└── examples/reference_client.py
LICENSE
README.md
AGENTS.md
```

## Agent conventions

1. **Sync `AGENTS.md`** — mandatory with every substantive change (see above).
2. **Sync `docs/CONTROLLER_INTEGRATION.md`** — mandatory when protocol, layout schema, or controller-visible behavior changes.
3. Keep **layout and data separate** — no values embedded in layout JSON.
4. Preserve **single-widget borderless** rendering.
5. **No vertical scroll** on display pages.
6. Extend **`WidgetType` + `WidgetRenderer`** instead of hardcoding UI in `MainActivity`.
7. Preserve **infinite pager** behavior for multi-page layouts.
8. Prefer minimal diffs; avoid new dependencies unless clearly necessary.
9. Test on device when behavior changes: `./scripts/layout-test.sh` or `./scripts/dev.sh debug`.
10. Do not commit secrets (`local.properties`, keystores, tokens).

## Repository

- Remote: `git@github.com:OpenMiniDisplay/OpenMiniDisplay-Android.git`
- Ignored: `local.properties`, `build/`, `.gradle/`, `.idea/` (see `.gitignore`)
