#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

usage() {
    cat <<EOF
Usage: ./scripts/connect-test.sh [phone-ip]

Send a handshake and heartbeat to the device listener on port ${LISTEN_PORT}.
If phone-ip is omitted, tries to read it from the connected adb device.
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./scripts/connect-test.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

main() {
    require_device

    local phone_ip="${1:-$(resolve_phone_ip)}"
    print_header "Testing TCP handshake to ${phone_ip}:${LISTEN_PORT}"

    if ! command -v nc >/dev/null 2>&1; then
        echo "nc (netcat) is required for this script." >&2
        exit 1
    fi

    printf 'OPENMINIDISPLAY\nPING\n' | nc -w 3 "$phone_ip" "$LISTEN_PORT" || true
    echo "Sent OPENMINIDISPLAY + PING to ${phone_ip}:${LISTEN_PORT}"
    echo "Use './scripts/dev.sh logs' on the device to verify state changes."
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

main "$@"
