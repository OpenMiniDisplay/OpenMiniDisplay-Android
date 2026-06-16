#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

INTERVAL_SECONDS="${INTERVAL_SECONDS:-2}"
CYCLE_PAUSE_SECONDS="${CYCLE_PAUSE_SECONDS:-3}"

usage() {
    cat <<EOF
Usage: ./scripts/widget-test.sh [phone-ip]

Loop through all preset widgets and push sample content over TCP port ${LISTEN_PORT}.
Keeps the session alive with periodic PING heartbeats.

Environment:
  INTERVAL_SECONDS     Delay between widget updates (default: 2)
  CYCLE_PAUSE_SECONDS  Pause before restarting the widget loop (default: 3)

Examples:
  ./scripts/widget-test.sh
  ./scripts/widget-test.sh 192.168.1.23
  INTERVAL_SECONDS=1 ./scripts/widget-test.sh
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./scripts/widget-test.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

declare -a WIDGET_SAMPLES=(
    "title|OpenMiniDisplay Demo"
    "subtitle|Preset widget refresh test"
    "status|Running widget loop"
    "metric|23.5 C"
    "footer|Updated via SET command"
)

send_widget_updates() {
    local cycle="$1"
    local index=1
    local total="${#WIDGET_SAMPLES[@]}"

    echo "==> Cycle ${cycle}: refreshing ${total} preset widgets" >&2

    for entry in "${WIDGET_SAMPLES[@]}"; do
        local widget_id="${entry%%|*}"
        local sample_value="${entry#*|}"
        local timestamp
        timestamp="$(date +%H:%M:%S)"
        local payload="${sample_value} (#${cycle}.${index} @ ${timestamp})"

        printf 'SET %s %s\n' "$widget_id" "$payload"
        printf 'PING\n'
        echo "-> SET ${widget_id} = ${payload}" >&2
        sleep "$INTERVAL_SECONDS"
        index=$((index + 1))
    done
}

main() {
    require_device

    if ! command -v nc >/dev/null 2>&1; then
        echo "nc (netcat) is required for this script." >&2
        exit 1
    fi

    local phone_ip="${1:-$(resolve_phone_ip)}"
    print_header "Streaming widget updates to ${phone_ip}:${LISTEN_PORT} (Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"

        local cycle=1
        while true; do
            send_widget_updates "$cycle"
            echo "PING"
            sleep "$CYCLE_PAUSE_SECONDS"
            cycle=$((cycle + 1))
        done
    } | nc "$phone_ip" "$LISTEN_PORT"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

main "$@"
