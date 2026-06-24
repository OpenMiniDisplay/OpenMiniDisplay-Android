# OpenMiniDisplay Controller Integration (Layout v2)

**Audience:** Agents and developers building controllers on any platform (Python, Node.js, Go, desktop apps, Home Assistant, etc.).

**Purpose:** Implement a TCP client that drives an OpenMiniDisplay Android device correctly.

| Meta | Value |
|------|-------|
| Layout schema | **v2** (`pages[].cards[].components[]`) |
| Transport port | **15180** |
| Encoding | UTF-8 |
| Framing | One command per line, terminated by `\n` |
| Display app | OpenMiniDisplay Android (`minSdk 28`) |

> **Normative keywords:** MUST, MUST NOT, SHOULD, MAY (RFC 2119 sense).

---

## 1. Agent quick start

Minimal integration loop:

```text
1. TCP connect to <display-ip>:15180
2. send "OPENMINIDISPLAY\n"
3. every ≤2 s send "PING\n"
4. send "SET cardId/componentId Hello\n" (and other SET commands)
5. keep the socket open
```

Bash smoke test (same LAN):

```bash
printf 'OPENMINIDISPLAY\nPING\nSET title/title Hello\n' | nc <display-ip> 15180
```

Reference implementations in this repo:

| Artifact | Path |
|----------|------|
| Bash layout loop | `scripts/layout-test.sh` |
| Bash Lua card demo | `scripts/card-script-test.sh` |
| Bash connect test | `scripts/connect-test.sh` |
| Python minimal client | `docs/examples/reference_client.py` |
| Sample Lua card script | `docs/examples/sample_card.lua` |
| Layout JSON Schema | `docs/schemas/layout.v2.schema.json` |

---

## 2. Transport

| Property | Requirement |
|----------|-------------|
| Protocol | TCP |
| Host | IP of the Android device on LAN |
| Port | **15180** |
| Byte order | N/A (text) |
| Encoding | **UTF-8** |
| Message delimiter | **LF (`\n`)** — one command per line |
| Responses | Display **does not** send text replies (fire-and-forget) |
| Concurrent clients | **Single active session** — a new TCP connection replaces the previous one |

Controllers MUST NOT wait for an ACK before sending the next command. Liveness is inferred from the TCP connection staying open and periodic heartbeats.

---

## 3. Connection lifecycle (controller view)

```mermaid
stateDiagram-v2
    [*] --> TcpConnected: TCP established
    TcpConnected --> Connected: OPENMINIDISPLAY or CONNECT
    Connected --> Connected: PING / SET / LAYOUT / PATCH / GOTO
    Connected --> Disconnected: no valid line for 5 s
    Disconnected --> Connected: OPENMINIDISPLAY + PING again
```

### Timing constants

| Constant | Value | Notes |
|----------|-------|-------|
| `HEARTBEAT_TIMEOUT` | **5000 ms** | No valid command → display state **DISCONNECTED** |
| `PING_INTERVAL` (recommended) | **≤ 2000 ms** | Keeps session **CONNECTED** |
| `LOW_POWER_DELAY` | 60 s after disconnect | Display-side only |
| `DIM_DURATION` | 10 s linear fade | Display-side only |

### Commands that refresh the heartbeat

Any successfully handled line among:

- `OPENMINIDISPLAY` / `CONNECT` (handshake)
- `PING`
- `SET …` (parsed successfully)
- `LAYOUT …` (valid JSON, ≥1 page)
- `PATCH …` (valid JSON, ≥1 page)
- `GOTO …` (valid index or page id)

---

## 4. Command reference

### 4.1 Grammar

```bnf
line        := command LF
command     := handshake | heartbeat | set | layout | patch | goto
handshake   := "OPENMINIDISPLAY" | "CONNECT" [suffix]
heartbeat   := "PING" [suffix]
set         := "SET" SP component-path SP value
layout      := "LAYOUT" SP json-object
patch       := "PATCH" SP json-object
goto        := "GOTO" SP target
component-path := card-id "/" component-id
card-id     := non-empty token without "/"
component-id := non-empty token without "/"
value       := any UTF-8 text (may contain spaces)
target      := page-index | page-id
page-index  := decimal integer (0-based)
page-id     := non-empty string matching a page "id" in current layout
suffix      := optional extra characters (ignored for CONNECT/PING prefix match)
SP          := one ASCII space
```

**Case:** Command keywords are matched **case-insensitively** for `SET`, `LAYOUT`, `PATCH`, `GOTO`, `OPENMINIDISPLAY`, `PING`, and `CONNECT` prefix.

### 4.2 Handshake

| Send | Display effect |
|------|----------------|
| `OPENMINIDISPLAY` | **CONNECTED** — wake screen, max brightness, show dashboard |
| `CONNECT` | Same as handshake (prefix match) |

Example:

```text
OPENMINIDISPLAY
```

### 4.3 Heartbeat

| Send | Display effect |
|------|----------------|
| `PING` | Extends **CONNECTED** by 5 s |

Example:

```text
PING
```

### 4.4 SET (component data)

```text
SET <cardId>/<componentId> <value>
```

- Target MUST contain **`/`** separating card id from component id.
- **First ASCII space** after `SET` separates path from `value`.
- **Everything after the first space** (trimmed) is `value`, including embedded spaces.

| Send | Display effect |
|------|----------------|
| `SET weather/temp 23.5` | Updates component `temp` in card `weather` |
| Path without `/` | Rejected (logged, no update) |
| Invalid / blank value | Ignored (no error reply) |

Examples:

```text
SET title/title Hello World
SET dash/cpu 72
SET dash/line 10,20,15,30,25
SET dash/pie CPU:30,MEM:25,IO:20,NET:25
```

### 4.5 LAYOUT (replace structure)

```text
LAYOUT <json>
```

- `<json>` MUST be a **single-line** JSON object (no raw newlines inside the line).
- Replaces the entire layout.
- MUST contain at least one page.

| Send | Display effect |
|------|----------------|
| Valid layout JSON | Pages/grid/cards replaced; page index clamped; card scripts restarted |
| Invalid JSON / empty pages / version < 2 | Ignored |

### 4.6 PATCH (merge pages)

```text
PATCH <json>
```

- JSON shape: `{ "pages": [ … ] }`
- Each page is merged by **`id`**: existing page replaced, unknown page appended.

### 4.7 GOTO (switch page)

```text
GOTO <index>
GOTO <pageId>
```

| Send | Display effect |
|------|----------------|
| `GOTO 0` | First page (0-based), **animated** slide |
| `GOTO focus` | Page with `"id":"focus"`, animated |

Only meaningful when layout has **2+ pages**. With 1 page, display does not enable swipe; GOTO still updates internal index.

---

## 5. Layout schema v2 (structure)

Layout describes **where** cards and components are placed. Display values come from **`SET`** and/or **card Lua scripts** (not from layout JSON).

Validate with: [`docs/schemas/layout.v2.schema.json`](schemas/layout.v2.schema.json)

### 5.1 Top-level object

```json
{
  "version": 2,
  "pages": [ { "...": "..." } ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `version` | **Yes** (must be ≥ 2) | Schema version |
| `pages` | **Yes** | Non-empty array of pages |

### 5.2 Page object

```json
{
  "id": "overview",
  "grid": { "rows": 3, "cols": 4, "gap": 8, "padding": 16 },
  "cards": [ { "...": "..." } ]
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `grid.rows` | `1` | Grid rows (≥1) |
| `grid.cols` | `1` | Grid columns (≥1) |
| `grid.gap` | `8` | Gap dp between cells |
| `grid.padding` | `16` | Page padding dp |

### 5.3 Card object

```json
{
  "id": "weather",
  "row": 0,
  "col": 0,
  "rowSpan": 1,
  "colSpan": 1,
  "grid": { "rows": 3, "cols": 1, "gap": 4, "padding": 8 },
  "script": "function on_init() set('temp','--') end",
  "components": [ { "...": "..." } ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Card id; used in `SET` path prefix |
| `row`, `col` | No (default `0`) | Page grid placement |
| `rowSpan`, `colSpan` | No (default `1`) | Page grid span |
| `grid` | No | Inner grid for components |
| `script` | No | Lua source; enables autonomous card runtime |
| `components` | **Yes** | Non-empty component list |

### 5.4 Component object

```json
{
  "id": "cpu",
  "type": "ring",
  "row": 0,
  "col": 0,
  "rowSpan": 1,
  "colSpan": 1,
  "style": "body",
  "label": "CPU"
}
```

| Field | Required | Values |
|-------|----------|--------|
| `id` | Yes | Used in `SET` as `<cardId>/<componentId>` |
| `type` | Yes | `text`, `metric`, `progress`, `ring`, `line`, `bar`, `pie`, `button`, `toggle` |
| `row`, `col` | Yes | 0-based inner grid origin |
| `rowSpan`, `colSpan` | No (default `1`) | Cell span |
| `style` | No | `headline`, `body`, `caption`, `metric` |
| `label` | No | Default label for charts / button / toggle |
| `checked` | No | Initial on-state for `toggle` (default `false`) |

### 5.5 Display rendering rules (for layout authors)

Controllers SHOULD design layouts knowing:

| Rule | Behavior |
|------|----------|
| Orientation | Landscape |
| Vertical scroll | **Not supported** — content clips |
| 1 page | No horizontal swipe |
| 2+ pages | Infinite horizontal swipe + page dots |
| 1 card on page, 1 component in card | **Borderless** full-screen |
| 2+ cards on page | Card chrome + page grid |
| 2+ components in card | Inner grid inside card |

### 5.6 Card Lua scripts (optional)

When a card includes a non-empty `script` field, the device runs it in **Luaj** (Lua 5.2 semantics) inside `RemoteDisplayService`.

**Lifecycle functions** (implement in script; host calls):

| Function | When |
|----------|------|
| `on_init()` | Card loaded / layout updated / resume from battery deep idle |
| `on_timer(name)` | After `every(seconds, name)` |
| `on_event(id, event, value?)` | IO component interaction |
| `on_destroy()` | Card unloaded / service stop |

**Host globals:**

| Function | Description |
|----------|-------------|
| `set(id, value)` | Update display component in this card |
| `set_prop(id, key, val)` | `label`, `enabled`, or `checked` |
| `every(sec, name)` | Start repeating timer |
| `cancel(name)` | Stop timer |
| `http_get(url, fn)` | Async GET; `fn(status, body_table_or_nil, err_string)` |
| `log(msg)` | Logcat |

**IO events:**

| Component | `on_event` |
|-----------|------------|
| `button` | `on_event("btnId", "click")` |
| `toggle` | `on_event("toggleId", "change", "true"\|"false")` |

Scripts pause during **battery deep idle** and restart `on_init` on wake.

Example script: [`docs/examples/sample_card.lua`](examples/sample_card.lua). Integration test: `./scripts/card-script-test.sh`.

---

## 6. Data formats (`SET` values)

Widget type is taken from **current layout** for that `cardId/componentId`. If not in layout, type is inferred from component id name.

| Type | Value format | Parsing |
|------|--------------|---------|
| `text`, `metric` | Any string | Used as-is |
| `progress`, `ring` | Number 0–100 | Optional `%` suffix; clamped 0–100 |
| `line`, `bar` | Comma-separated floats | `10, 20.5, 3` |
| `pie` | `label:value` pairs OR comma numbers | `CPU:30,MEM:70` or `30,70` → S1, S2… |
| `button`, `toggle` | Ignored for display value | Use `set_prop` from Lua or toggle UI |

---

## 7. Recommended client architecture

```text
OpenMiniDisplayClient
├── TcpConnection        # connect(), reconnect(), single socket
├── HeartbeatScheduler   # interval ≤ 2 s → PING
├── CommandWriter        # send_line(cmd) with UTF-8 + \n
├── LayoutStore          # optional local copy of last LAYOUT
└── DataCache            # card/comp path → last SET value (for reconnect replay)
```

### 7.1 Reconnect strategy (SHOULD)

1. On disconnect → exponential backoff reconnect.
2. After reconnect → `OPENMINIDISPLAY`.
3. If layout was customized → resend `LAYOUT …`.
4. Resend all cached `SET` values.
5. Restart heartbeat timer.

### 7.2 Threading

- One writer thread/coroutine serializing lines onto the socket is sufficient.
- Heartbeat and data updates MAY share the same queue.

---

## 8. Reference sessions

### 8.1 Default layout — data only

Display ships with a default 3-page layout. Controller only sends data:

```text
OPENMINIDISPLAY
PING
SET title/title Server Room
SET metric/metric 23.5 C
SET progress/progress 65
PING
```

### 8.2 Custom layout + live data

```text
OPENMINIDISPLAY
LAYOUT {"version":2,"pages":[{"id":"dash","grid":{"rows":2,"cols":2,"gap":8,"padding":16},"cards":[{"id":"title","row":0,"col":0,"colSpan":2,"grid":{"rows":1,"cols":1,"padding":0},"components":[{"id":"title","type":"text","row":0,"col":0,"style":"headline"}]},{"id":"cpu","row":1,"col":0,"grid":{"rows":1,"cols":1,"padding":0},"components":[{"id":"cpu","type":"ring","row":0,"col":0}]},{"id":"mem","row":1,"col":1,"grid":{"rows":1,"cols":1,"padding":0},"components":[{"id":"mem","type":"progress","row":0,"col":0}]}]}]}
PING
SET title/title Production
SET cpu/cpu 72
SET mem/mem 54
PING
```

### 8.3 Multi-page carousel

```text
OPENMINIDISPLAY
LAYOUT {"version":2,"pages":[{"id":"a","grid":{"rows":1,"cols":1,"padding":0},"cards":[{"id":"metric","row":0,"col":0,"grid":{"rows":1,"cols":1,"padding":0},"components":[{"id":"metric","type":"metric","row":0,"col":0,"style":"metric"}]}]},{"id":"b","grid":{"rows":1,"cols":1,"padding":0},"cards":[{"id":"status","row":0,"col":0,"grid":{"rows":1,"cols":1,"padding":0},"components":[{"id":"status","type":"text","row":0,"col":0,"style":"headline"}]}]}]}
PING
SET metric/metric 100%
SET status/status All systems go
GOTO 0
PING
GOTO 1
PING
GOTO b
```

### 8.4 Autonomous Lua card

Push layout with `script` on a card, then only heartbeat — device updates UI locally:

```text
OPENMINIDISPLAY
LAYOUT …
PING
PING
…
```

See `./scripts/card-script-test.sh` and `docs/examples/sample_card.lua`.

---

## 9. Conformance checklist

Before shipping a controller client, verify:

- [ ] Connects to port **15180**
- [ ] Sends `OPENMINIDISPLAY` immediately after TCP connect
- [ ] Sends `PING` at least every **2 s** while connected
- [ ] `SET` uses `cardId/componentId` paths
- [ ] `SET` with spaces in value works (`SET title/title Hello World`)
- [ ] `LAYOUT` JSON is single-line UTF-8 with `"version":2`
- [ ] After reconnect, layout + data are replayed
- [ ] Handles display not responding (no ACK) without deadlock
- [ ] Tested against `./scripts/layout-test.sh`, `./scripts/card-script-test.sh`, or `docs/examples/reference_client.py`

---

## 10. Versioning

| Layout schema | Android app | Breaking changes |
|---------------|-------------|------------------|
| **v2** (cards + components + Lua) | ≥ 1.0 | Replaces v1 `widgets`; SET requires `/` path |
| v1 (`widgets`) | — | **No longer accepted** |

Future changes MUST increment layout `version` or this document. Controllers SHOULD ignore unknown JSON fields.

---

## 11. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Display dims after ~5 s | No `PING` / commands | Send heartbeat every ≤2 s |
| Updates ignored | Not connected / wrong path | Send handshake first; use `cardId/componentId` from layout |
| `LAYOUT` has no effect | Invalid JSON or empty pages | Validate against schema |
| Connection drops when reconnecting | Single-client design | Close old socket before opening new one |
| Cannot connect | Firewall / wrong IP | Same LAN; check device Wi‑Fi IP |

---

## 12. Related documents

| Document | Audience |
|----------|----------|
| [`AGENTS.md`](../AGENTS.md) | Agents modifying the **Android app** |
| [`README.md`](../README.md) | Human overview + license |
| [`LICENSE`](../LICENSE) | Apache-2.0 |

**Maintenance:** When the wire protocol or layout schema changes in the Android app, **`docs/CONTROLLER_INTEGRATION.md` MUST be updated in the same change** (see `AGENTS.md`).
