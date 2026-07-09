# Demo scripts

Self-contained example apps for OpenMiniDisplay. **Each example lives in its own subdirectory** with Lua sources and a `run.sh` launcher.

| Example | Description |
|---------|-------------|
| [`clock-pomodoro/`](clock-pomodoro/) | Page 1: full-screen 24h clock; page 2: Pomodoro timer |

## Run an example

```bash
./demo_script/clock-pomodoro/run.sh [phone-ip]
```

Phone on USB: omit IP (script detects Wi‑Fi address via adb). Same LAN: pass the display IP.

All examples push `LAYOUT` once, then keep the TCP session alive with `PING` only — logic runs on device via card Lua scripts.
