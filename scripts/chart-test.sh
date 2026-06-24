#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

INTERVAL_SECONDS="${INTERVAL_SECONDS:-2}"
CYCLE_PAUSE_SECONDS="${CYCLE_PAUSE_SECONDS:-3}"

usage() {
    cat <<EOF
Usage: ./scripts/chart-test.sh [phone-ip]

Loop through chart components (progress, ring, line, bar, pie) on TCP port ${LISTEN_PORT}.
Uses v2 SET paths: cardId/componentId. Keeps the session alive with periodic PING heartbeats.

Environment:
  INTERVAL_SECONDS     Delay between chart updates (default: 2)
  CYCLE_PAUSE_SECONDS  Pause before restarting the chart loop (default: 3)

Examples:
  ./scripts/chart-test.sh
  ./scripts/chart-test.sh 192.168.1.23
  INTERVAL_SECONDS=1 ./scripts/chart-test.sh
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./scripts/chart-test.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

send_chart_updates() {
    local cycle="$1"
    local progress=$(( (cycle * 17) % 101 ))
    local ring=$(( (cycle * 23) % 101 ))
    local line="10,18,14,26,22,34,28,40"
    local bar="6,14,10,22,16,28"
    local pie="CPU:$((20 + cycle % 15)),MEM:$((15 + cycle % 10)),IO:$((10 + cycle % 8)),NET:$((25 + cycle % 12))"

    echo "==> Cycle ${cycle}: refreshing chart components" >&2

    printf 'SET progress/progress %s\n' "$progress"
    printf 'PING\n'
    echo "-> SET progress/progress = ${progress}" >&2
    sleep "$INTERVAL_SECONDS"

    printf 'SET ring/ring %s\n' "$ring"
    printf 'PING\n'
    echo "-> SET ring/ring = ${ring}" >&2
    sleep "$INTERVAL_SECONDS"

    printf 'SET line/line %s\n' "$line"
    printf 'PING\n'
    echo "-> SET line/line = ${line}" >&2
    sleep "$INTERVAL_SECONDS"

    printf 'SET bar/bar %s\n' "$bar"
    printf 'PING\n'
    echo "-> SET bar/bar = ${bar}" >&2
    sleep "$INTERVAL_SECONDS"

    printf 'SET pie/pie %s\n' "$pie"
    printf 'PING\n'
    echo "-> SET pie/pie = ${pie}" >&2
    sleep "$INTERVAL_SECONDS"
}

main() {
    require_device

    if ! command -v nc >/dev/null 2>&1; then
        echo "nc (netcat) is required for this script." >&2
        exit 1
    fi

    local phone_ip="${1:-$(resolve_phone_ip)}"
    print_header "Streaming chart updates to ${phone_ip}:${LISTEN_PORT} (Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"

        local cycle=1
        while true; do
            send_chart_updates "$cycle"
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
