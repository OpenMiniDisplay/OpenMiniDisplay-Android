#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

INTERVAL_SECONDS="${INTERVAL_SECONDS:-2}"

usage() {
    cat <<EOF
Usage: ./scripts/layout-test.sh [phone-ip]

Push a sample multi-page LAYOUT (schema v2), then loop SET updates for all components.
Includes a single-component card page ("focus") to verify borderless full-screen rendering.

Examples:
  ./scripts/layout-test.sh
  ./scripts/layout-test.sh 192.168.1.23
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./scripts/layout-test.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

read -r -d '' SAMPLE_LAYOUT <<'EOF' || true
{"version":2,"pages":[{"id":"overview","grid":{"rows":3,"cols":4,"gap":8,"padding":16},"cards":[{"id":"title","row":0,"col":0,"colSpan":4,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"title","type":"text","row":0,"col":0,"style":"headline"}]},{"id":"subtitle","row":1,"col":0,"colSpan":2,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"subtitle","type":"text","row":0,"col":0,"style":"body"}]},{"id":"status","row":1,"col":2,"colSpan":2,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"status","type":"text","row":0,"col":0,"style":"caption"}]},{"id":"progress","row":2,"col":0,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"progress","type":"progress","row":0,"col":0}]},{"id":"ring","row":2,"col":1,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"ring","type":"ring","row":0,"col":0}]},{"id":"line","row":2,"col":2,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"line","type":"line","row":0,"col":0}]},{"id":"bar","row":2,"col":3,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"bar","type":"bar","row":0,"col":0}]}]},{"id":"focus","grid":{"rows":1,"cols":1,"gap":0,"padding":0},"cards":[{"id":"metric","row":0,"col":0,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"metric","type":"metric","row":0,"col":0,"style":"metric"}]}]},{"id":"charts","grid":{"rows":1,"cols":2,"gap":8,"padding":16},"cards":[{"id":"pie","row":0,"col":0,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"pie","type":"pie","row":0,"col":0}]},{"id":"footer","row":0,"col":1,"grid":{"rows":1,"cols":1,"gap":0,"padding":0},"components":[{"id":"footer","type":"text","row":0,"col":0,"style":"caption"}]}]}]}
EOF

send_data_cycle() {
    local cycle="$1"
    local progress=$(( (cycle * 17) % 101 ))
    local ring=$(( (cycle * 23) % 101 ))

    printf 'SET title/title OpenMiniDisplay #%s\n' "$cycle"
    printf 'SET subtitle/subtitle Layout-driven dashboard\n'
    printf 'SET status/status Cycle %s running\n' "$cycle"
    printf 'SET metric/metric %s.%s C\n' "$((20 + cycle % 15))" "$((cycle % 10))"
    printf 'SET progress/progress %s\n' "$progress"
    printf 'SET ring/ring %s\n' "$ring"
    printf 'SET line/line 10,18,14,26,22,34,28,40\n'
    printf 'SET bar/bar 6,14,10,22,16,28\n'
    printf 'SET pie/pie CPU:%s,MEM:%s,IO:%s,NET:%s\n' \
        "$((20 + cycle % 15))" "$((15 + cycle % 10))" "$((10 + cycle % 8))" "$((25 + cycle % 12))"
    printf 'SET footer/footer Port %s | swipe for pages\n' "$LISTEN_PORT"
    printf 'PING\n'
}

main() {
    require_device

    if ! command -v nc >/dev/null 2>&1; then
        echo "nc (netcat) is required for this script." >&2
        exit 1
    fi

    local phone_ip="${1:-$(resolve_phone_ip)}"
    print_header "Streaming layout + data to ${phone_ip}:${LISTEN_PORT} (Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"
        printf 'LAYOUT %s\n' "$SAMPLE_LAYOUT"
        echo "PING"

        local cycle=1
        local page=0
        while true; do
            echo "==> Cycle ${cycle}: updating component data" >&2
            send_data_cycle "$cycle"
            page=$(( (page + 1) % 3 ))
            echo "GOTO ${page}" >&2
            printf 'GOTO %s\n' "$page"
            echo "PING"
            sleep "$INTERVAL_SECONDS"
            cycle=$((cycle + 1))
        done
    } | nc "$phone_ip" "$LISTEN_PORT"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

main "$@"
