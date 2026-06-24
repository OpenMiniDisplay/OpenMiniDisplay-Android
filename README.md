# OpenMiniDisplay Android

Turn an old Android phone (API 28+) into a low-power, remotely controlled smart display.

## Features

- Foreground TCP listener on port **15180** (`OPENMINIDISPLAY` / `PING` / `SET` / `LAYOUT` / `PATCH` / `GOTO`)
- **Layout v2**: pages → cards → components (`text`, `metric`, charts, `button`, `toggle`)
- Optional **per-card Lua scripts** (timers, HTTP, on-device IO events)
- Layout-driven full-screen Compose UI (multi-page, infinite horizontal swipe when `pages > 1`)
- Low-power mode: 60 s grace → 10 s smooth dim → pitch-black screen (no auto-lock)
- Device scripts under `scripts/` for build, install, and protocol testing

## Quick start (device)

```bash
./scripts/dev.sh debug
./scripts/layout-test.sh
./scripts/card-script-test.sh <phone-ip>
```

## Quick start (PC / controller app)

**Normative integration spec:** [docs/CONTROLLER_INTEGRATION.md](docs/CONTROLLER_INTEGRATION.md)

```bash
# Same LAN as the phone
python3 docs/examples/reference_client.py demo <display-ip>
python3 docs/examples/reference_client.py push-layout <display-ip> \
  --layout docs/examples/minimal_layout.json
```

More examples: [docs/examples/README.md](docs/examples/README.md)

See [AGENTS.md](AGENTS.md) for architecture and contributor guidelines.

## License

Copyright 2026 OpenMiniDisplay Contributors

Licensed under the **Apache License, Version 2.0** (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Full license text: [LICENSE](LICENSE)
