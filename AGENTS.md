# OpenMiniDisplay — Agent Guide

This document describes how agents should work in this repository.

## Project Purpose

OpenMiniDisplay turns an old Android phone (API 28+) into a low-power, remotely controlled smart display. A foreground service listens on port **15180**; the UI is driven by a **layout document** (structure) and **data updates** (values).

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| Min SDK | 28 (Android 9.0) |
| UI | Jetpack Compose (layout-driven, full-screen) |
| Networking | Java `ServerSocket` |
| Layout format | JSON via `org.json` (no extra deps) |
| Build | Gradle Kotlin DSL + Version Catalog |

## Architecture

```
Controller (TCP :15180)
        │
        ▼
RemoteDisplayService ──► DisplayCommandHandler
        │                      ├── DisplayLayoutRepository  (structure)
        │                      ├── DisplayDataRepository     (values)
        │                      └── DisplayNavigationRepository (page index)
        ├── ConnectionStateRepository
        └── ScreenManager
                    │
                    ▼
              DisplayHost (HorizontalPager if pages > 1)
                    │
                    ▼
              PageRenderer → WidgetRegistry
```

### UI rules

- Full-screen immersive (no status/navigation bars)
- No vertical scrolling
- **1 page** → no horizontal swipe
- **2+ pages** → horizontal swipe + page dots
- **1 widget on a page** → borderless full-screen widget (no card chrome)
- **2+ widgets on a page** → card chrome + grid layout

## Protocol (Port 15180)

Newline-terminated UTF-8 text.

| Command | Effect |
|---------|--------|
| `OPENMINIDISPLAY` / `CONNECT` | Handshake → **CONNECTED** |
| `PING` | Heartbeat |
| `SET <id> <value>` | Update widget data |
| `LAYOUT <json>` | Replace entire layout |
| `PATCH <json>` | Merge pages by `id` |
| `GOTO <index\|pageId>` | Switch page remotely |

### Layout JSON shape

```json
{
  "version": 1,
  "pages": [
    {
      "id": "overview",
      "grid": { "rows": 3, "cols": 4, "gap": 8, "padding": 16 },
      "widgets": [
        { "id": "title", "type": "text", "row": 0, "col": 0, "colSpan": 4, "style": "headline" },
        { "id": "metric", "type": "metric", "row": 0, "col": 0, "style": "metric" }
      ]
    }
  ]
}
```

Widget types: `text`, `metric`, `progress`, `ring`, `line`, `bar`, `pie`.

Grid fields per widget: `row`, `col`, `rowSpan`, `colSpan`, optional `style`, optional `label`.

### Data formats (`SET`)

| Type | Example |
|------|---------|
| text / metric | `SET title Hello` |
| progress / ring | `SET progress 72` |
| line / bar | `SET line 10,20,15,30` |
| pie | `SET pie CPU:30,MEM:25,IO:20` |

## Low-power behavior

| Phase | Behavior |
|-------|----------|
| DISCONNECTED | Screen stays on; 60 s countdown starts |
| After 60 s | Dashboard visible; brightness dims over 10 s |
| After dim | Navigate to `PitchBlackActivity` (content hidden) |
| User touch | Restore brightness, return to dashboard, reset 60 s timer |

## Build & device scripts

```bash
./scripts/dev.sh debug
./scripts/layout-test.sh      # LAYOUT + SET loop + GOTO pages
./scripts/widget-test.sh      # SET loop (default layout)
./scripts/chart-test.sh       # chart SET loop
```

## Key files

```
display/
├── DefaultDisplayLayout.kt
├── model/DisplayModels.kt
├── repo/DisplayLayoutRepository.kt
├── repo/DisplayDataRepository.kt
├── repo/DisplayNavigationRepository.kt
└── protocol/DisplayCommandHandler.kt
ui/display/
├── DisplayHost.kt
├── PageRenderer.kt
├── WidgetSlotContainer.kt
└── WidgetRegistry.kt
```

## Agent conventions

1. Keep layout and data separate — do not embed values in layout JSON.
2. Preserve single-widget borderless rendering (`page.widgets.size == 1`).
3. Do not add vertical scroll to display pages.
4. Prefer extending `WidgetType` + registry over one-off UI in `MainActivity`.
5. Test on device with `./scripts/layout-test.sh`.
