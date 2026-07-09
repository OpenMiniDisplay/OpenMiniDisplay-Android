#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

PING_INTERVAL_SECONDS="${PING_INTERVAL_SECONDS:-2}"

usage() {
    cat <<EOF
Usage: ./demo_script/clock-pomodoro/run.sh [phone-ip]

Push the clock + Pomodoro demo (two pages) to OpenMiniDisplay on port ${LISTEN_PORT}.
Both pages run Lua on device; PC only sends LAYOUT + PING.

Examples:
  ./demo_script/clock-pomodoro/run.sh
  ./demo_script/clock-pomodoro/run.sh 192.168.1.23
  PING_INTERVAL_SECONDS=1 ./demo_script/clock-pomodoro/run.sh
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./demo_script/clock-pomodoro/run.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

escape_json() {
    python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<<"$1"
}

build_layout() {
    local clock_script pomodoro_script
    clock_script="$(escape_json "$(<"${DEMO_DIR}/clock.lua")")"
    pomodoro_script="$(escape_json "$(<"${DEMO_DIR}/pomodoro.lua")")"

    python3 -c "
import json
clock = ${clock_script}
pomo = ${pomodoro_script}
layout = {
    'version': 2,
    'pages': [
        {
            'id': 'clock',
            'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
            'cards': [{
                'id': 'clock',
                'row': 0, 'col': 0,
                'grid': {'rows': 1, 'cols': 1, 'gap': 0, 'padding': 0},
                'script': clock,
                'components': [
                    {'id': 'time', 'type': 'metric', 'row': 0, 'col': 0, 'style': 'metric', 'fit': True, 'align': 'center'},
                ],
            }],
        },
        {
            'id': 'pomodoro',
            'grid': {'rows': 1, 'cols': 1, 'gap': 8, 'padding': 16},
            'cards': [{
                'id': 'pomo',
                'row': 0, 'col': 0,
                'grid': {'rows': 4, 'cols': 2, 'gap': 8, 'padding': 12},
                'script': pomo,
                'components': [
                    {'id': 'phase', 'type': 'text', 'row': 0, 'col': 0, 'colSpan': 2, 'style': 'headline', 'align': 'center'},
                    {'id': 'timer', 'type': 'metric', 'row': 1, 'col': 0, 'colSpan': 2, 'fit': True, 'fill': True, 'align': 'center'},
                    {'id': 'progress', 'type': 'ring', 'row': 2, 'col': 0, 'colSpan': 2, 'fill': True, 'scale': 0.9, 'showLabel': False},
                    {'id': 'toggle', 'type': 'button', 'row': 3, 'col': 0, 'label': '开始'},
                    {'id': 'next', 'type': 'button', 'row': 3, 'col': 1, 'label': '进入下一段'},
                ],
            }],
        },
    ],
}
print(json.dumps(layout, separators=(',', ':'), ensure_ascii=False))
"
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
        echo "nc (netcat) is required for this script." >&2
        exit 1
    fi

    local layout
    layout="$(build_layout)"

    print_header "Pushing clock + Pomodoro demo to ${phone_ip}:${LISTEN_PORT} (PING only, Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"
        printf 'LAYOUT %s\n' "$layout"
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
