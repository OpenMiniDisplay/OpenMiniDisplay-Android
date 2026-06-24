#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

PING_INTERVAL_SECONDS="${PING_INTERVAL_SECONDS:-2}"

usage() {
    cat <<EOF
Usage: ./scripts/card-script-test.sh [phone-ip]

Push a v2 LAYOUT with an embedded Lua card script, then keep the session
alive with PING only (no SET loop). The card runs autonomously on device:
  - auto counter tick every 3 s (toggle to pause)
  - Refresh button -> api.ipify.org GET -> shows public IP

Examples:
  ./scripts/card-script-test.sh
  ./scripts/card-script-test.sh 192.168.1.23
  PING_INTERVAL_SECONDS=1 ./scripts/card-script-test.sh
EOF
}

resolve_phone_ip() {
    local ip
    ip="$(adb_cmd shell ip route 2>/dev/null | awk '/wlan/ {print $9; exit}')"
    if [[ -z "$ip" ]]; then
        ip="$(adb_cmd shell ip -f inet addr show wlan0 2>/dev/null | awk '/inet / {print $2}' | cut -d/ -f1)"
    fi
    if [[ -z "$ip" ]]; then
        echo "Unable to detect phone IP. Pass it explicitly: ./scripts/card-script-test.sh 192.168.1.23" >&2
        exit 1
    fi
    echo "$ip"
}

read -r -d '' SAMPLE_SCRIPT <<'EOF' || true
counter = 0
auto = true
function on_init()
  set("status", "Ready")
  set("counter", "0")
  every(3, "tick")
end
function on_timer(name)
  if name == "tick" and auto then
    counter = counter + 1
    set("counter", tostring(counter))
  end
end
function on_event(id, event, value)
  if id == "refresh" and event == "click" then
    set("status", "Fetching...")
    http_get("https://api.ipify.org?format=json", function(status, body, err)
      if status >= 200 and status < 300 and body ~= nil then
        local ip = body.ip or "?"
        set("metric", tostring(ip))
        set("status", "OK " .. tostring(status))
      else
        set("status", "ERR " .. tostring(status) .. " " .. tostring(err))
      end
    end)
  elseif id == "auto" and event == "change" then
    auto = (value == "true")
    if auto then every(3, "tick") else cancel("tick") end
  end
end
EOF

# Escape script for JSON string
escape_json() {
    python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))' <<<"$1"
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

    local script_json
    script_json="$(escape_json "$SAMPLE_SCRIPT")"

    read -r -d '' SAMPLE_LAYOUT <<EOF || true
{"version":2,"pages":[{"id":"script_demo","grid":{"rows":1,"cols":1,"gap":0,"padding":16},"cards":[{"id":"demo","row":0,"col":0,"grid":{"rows":5,"cols":1,"gap":8,"padding":8},"script":${script_json},"components":[{"id":"status","type":"text","row":0,"col":0,"style":"headline"},{"id":"counter","type":"metric","row":1,"col":0},{"id":"metric","type":"text","row":2,"col":0,"style":"body"},{"id":"refresh","type":"button","row":3,"col":0,"label":"Refresh"},{"id":"auto","type":"toggle","row":4,"col":0,"label":"Auto tick","checked":true}]}]}]}
EOF

    print_header "Pushing Lua card layout to ${phone_ip}:${LISTEN_PORT} (PING only, Ctrl+C to stop)"

    {
        echo "OPENMINIDISPLAY"
        echo "PING"
        printf 'LAYOUT %s\n' "$SAMPLE_LAYOUT"
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
