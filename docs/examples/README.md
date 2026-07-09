# Controller examples (PC / cross-platform)

Artifacts for building a **TCP client** that drives OpenMiniDisplay on port **15180**.

| File | Purpose |
|------|---------|
| [`reference_client.py`](reference_client.py) | Python 3 reference client (`demo`, `push-layout`, `ping`) |
| [`minimal_layout.json`](minimal_layout.json) | Small v2 layout to push with `push-layout` |
| [`sample_card.lua`](sample_card.lua) | Lua card script (embed in layout `script` field) |
| [`../schemas/layout.v2.schema.json`](../schemas/layout.v2.schema.json) | JSON Schema for layout validation |

**Normative spec:** [`../CONTROLLER_INTEGRATION.md`](../CONTROLLER_INTEGRATION.md)

## Quick try (same LAN)

```bash
# 1. Smoke test (handshake + heartbeat)
python3 docs/examples/reference_client.py ping 192.168.x.x

# 2. Drive default on-device layout with SET only
python3 docs/examples/reference_client.py demo 192.168.x.x

# 3. Push custom layout, then update values
python3 docs/examples/reference_client.py push-layout 192.168.x.x \
  --layout docs/examples/minimal_layout.json
```

Bash equivalents (from repo root, phone on USB or pass IP):

```bash
./scripts/connect-test.sh 192.168.x.x
./scripts/widget-test.sh 192.168.x.x
./scripts/layout-test.sh 192.168.x.x
./scripts/card-script-test.sh 192.168.x.x   # Lua card; PC sends LAYOUT + PING only
```

## Layout + Lua script

Embed script source in the card `"script"` string (JSON-escaped). In Python:

```python
import json
from pathlib import Path

script = Path("docs/examples/sample_card.lua").read_text(encoding="utf-8")
layout = { ... "cards": [{ "id": "demo", "script": script, "components": [...] }] }
line = "LAYOUT " + json.dumps(layout, separators=(",", ":"), ensure_ascii=False)
```

See [`../CONTROLLER_INTEGRATION.md`](../CONTROLLER_INTEGRATION.md) §5.6 and §8.4.
