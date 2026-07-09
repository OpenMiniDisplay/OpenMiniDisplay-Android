#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

PING_INTERVAL_SECONDS="${PING_INTERVAL_SECONDS:-2}"

usage() {
    cat <<EOF
Usage: ./demo_script/gif-gallery/run.sh [phone-ip]

Push GIF assets over TCP (PUSH + SET), send 3-page layout, then PING.

Pages:
  1 — mahiro.gif (full screen)
  2 — miku.gif (full screen)
  3 — cat1.gif | cat2.gif (side by side)

Examples:
  ./demo_script/gif-gallery/run.sh
  ./demo_script/gif-gallery/run.sh 192.168.1.23
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly." >&2
        exit 1
    fi
    echo "$ip"
}

build_layout() {
    python3 -c "
import json
layout = {
    'version': 2,
    'pages': [
        {
            'id': 'mahiro',
            'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
            'cards': [{
                'id': 'mahiro',
                'row': 0, 'col': 0,
                'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
                'components': [
                    {'id': 'img', 'type': 'image', 'row': 0, 'col': 0, 'fill': True},
                ],
            }],
        },
        {
            'id': 'miku',
            'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
            'cards': [{
                'id': 'miku',
                'row': 0, 'col': 0,
                'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
                'components': [
                    {'id': 'img', 'type': 'image', 'row': 0, 'col': 0, 'fill': True},
                ],
            }],
        },
        {
            'id': 'cats',
            'grid': {'rows': 1, 'cols': 1, 'gap': 8, 'padding': 8},
            'cards': [{
                'id': 'cats',
                'row': 0, 'col': 0,
                'grid': {'rows': 1, 'cols': 2, 'gap': 8, 'padding': 0},
                'components': [
                    {'id': 'left', 'type': 'image', 'row': 0, 'col': 0, 'fill': True},
                    {'id': 'right', 'type': 'image', 'row': 0, 'col': 1, 'fill': True},
                ],
            }],
        },
    ],
}
print(json.dumps(layout, separators=(',', ':'), ensure_ascii=False))
"
}

emit_push_and_set() {
    DEMO_DIR="$DEMO_DIR" python3 <<'PY'
import base64
import os

demo = os.environ["DEMO_DIR"]
assets = [
    ("mahiro.gif", "mahiro", "img"),
    ("miku.gif", "miku", "img"),
    ("cat1.gif", "cats", "left"),
    ("cat2.gif", "cats", "right"),
]
for fname, card, comp in assets:
    path = os.path.join(demo, fname)
    if not os.path.isfile(path):
        raise SystemExit(f"missing {path}")
    data = base64.standard_b64encode(open(path, "rb").read()).decode("ascii")
    print(f"PUSH {fname} {data}")
    print(f"SET {card}/{comp} asset:{fname}")
PY
}

main() {
    local phone_ip="${1:-}"

    if [[ -z "$phone_ip" ]]; then
        require_device
        phone_ip="$(resolve_phone_ip)"
    elif [[ ! "$phone_ip" =~ ^[0-9.]+$ ]]; then
        echo "Invalid phone IP: $phone_ip" >&2
        usage
        exit 1
    fi

    if ! command -v nc >/dev/null 2>&1; then
        echo "nc (netcat) is required." >&2
        exit 1
    fi

    for name in mahiro.gif miku.gif cat1.gif cat2.gif; do
        if [[ ! -f "${DEMO_DIR}/${name}" ]]; then
            echo "Missing ${DEMO_DIR}/${name}" >&2
            exit 1
        fi
    done

    local layout
    layout="$(build_layout)"

    print_header "Streaming GIF gallery to ${phone_ip}:${LISTEN_PORT} (PUSH + SET, Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"
        printf 'LAYOUT %s\n' "$layout"
        emit_push_and_set
        echo "PING"
        while true; do
            echo "PING"
            sleep "$PING_INTERVAL_SECONDS"
        done
    } | nc "$phone_ip" "$LISTEN_PORT"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

main "$@"
