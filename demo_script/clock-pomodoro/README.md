# Clock + Pomodoro

Two-page example for OpenMiniDisplay:

| Page | Content |
|------|---------|
| 1 — **clock** | Full-screen 24h time (`HH:mm:ss`), updates every second |
| 2 — **pomodoro** | Standard Pomodoro timer (25 / 5 / 15 min) with **开始/暂停** and **进入下一段** |

Both pages use on-device Lua card scripts. The PC client only pushes `LAYOUT` and keeps the session alive with `PING`.

Presentation highlights in this demo:

- Clock: `"fit": true, "align": "center"` — auto-fit time to full screen
- Pomodoro timer: `"fit": true, "fill": true` — large centered countdown
- Ring: `"fill": true, "scale": 0.9, "showLabel": false`
- Buttons: same row via 2-column card grid (`row` 3, `col` 0 / 1)

## Files

| File | Role |
|------|------|
| [`clock.lua`](clock.lua) | Clock card script (`local_time()` host API) |
| [`pomodoro.lua`](pomodoro.lua) | Pomodoro state machine + UI updates |
| [`run.sh`](run.sh) | Build layout JSON, push to phone, heartbeat loop |

## Run

From repo root (phone on USB or pass IP):

```bash
chmod +x demo_script/clock-pomodoro/run.sh   # once
./demo_script/clock-pomodoro/run.sh
./demo_script/clock-pomodoro/run.sh 192.168.1.23
```

Swipe horizontally to switch pages. Stop with Ctrl+C (device keeps last layout; scripts keep running until disconnect timeout).

## Pomodoro rules

- **专注** 25 min → **短休息** 5 min (×3 rounds) → **专注** → **长休息** 15 min after the 4th completed focus session
- **开始 / 暂停** toggles the countdown for the current segment
- **进入下一段** skips to the next segment immediately (manual skip does not count toward the long-break round counter)
- Segment ends automatically pause the timer; phase changes call `wake()` to light the screen when idle
