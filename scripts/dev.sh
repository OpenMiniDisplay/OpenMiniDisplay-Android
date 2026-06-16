#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/common.sh
source "${ROOT_DIR}/scripts/common.sh"

usage() {
    cat <<'EOF'
Usage: ./scripts/dev.sh <command>

Commands:
  build      Compile debug APK
  install    Install debug APK to a connected device
  run        Install (if needed) and launch MainActivity
  logs       Stream filtered logcat for this app
  debug      build + install + launch + logs (one-shot dev loop)
  devices    List connected adb devices
  uninstall  Remove app from device

Other scripts:
  ./scripts/connect-test.sh [phone-ip]   # handshake smoke test
  ./scripts/widget-test.sh [phone-ip]    # loop preset widget SET updates
  ./scripts/chart-test.sh [phone-ip]     # loop chart widget SET updates
  ./scripts/layout-test.sh [phone-ip]    # push LAYOUT + loop SET + GOTO pages

Environment:
  ANDROID_SERIAL   Target a specific device when multiple are connected
  LOG_TAGS         Extra logcat tags (default: RemoteDisplayService ScreenManager)

Examples:
  ./scripts/dev.sh debug
  ANDROID_SERIAL=0123456789ABCDEF ./scripts/dev.sh run
  ./scripts/dev.sh logs
EOF
}

cmd_build() {
    print_header "Building debug APK"
    (cd "$ROOT_DIR" && ./gradlew assembleDebug)
    echo "APK: ${APK_PATH}"
}

cmd_install() {
    require_device
    if [[ ! -f "$APK_PATH" ]]; then
        cmd_build
    fi
    print_header "Installing ${APP_ID}"
    adb_cmd install -r "$APK_PATH"
}

cmd_run() {
    require_device
    cmd_install
    print_header "Launching ${MAIN_ACTIVITY}"
    adb_cmd shell am start -n "$MAIN_ACTIVITY"
}

cmd_logs() {
    require_device
    local tags="${LOG_TAGS:-RemoteDisplayService ScreenManager}"
    print_header "Streaming logcat for ${APP_ID} (Ctrl+C to stop)"
    adb_cmd logcat -c
    # shellcheck disable=SC2086
    adb_cmd logcat --pid="$(adb_cmd shell pidof -s "$APP_ID" 2>/dev/null || true)" -v time "$tags" "*:S" 2>/dev/null || \
        adb_cmd logcat -v time | grep --line-buffered -E "${APP_ID}|RemoteDisplayService|ScreenManager|AndroidRuntime|FATAL"
}

cmd_debug() {
    cmd_build
    cmd_run
    cmd_logs
}

cmd_devices() {
    setup_toolchain
    adb devices -l
}

cmd_uninstall() {
    require_device
    print_header "Uninstalling ${APP_ID}"
    adb_cmd uninstall "$APP_ID" || true
}

main() {
    local command="${1:-}"
    case "$command" in
        build) cmd_build ;;
        install) cmd_install ;;
        run) cmd_run ;;
        logs) cmd_logs ;;
        debug) cmd_debug ;;
        devices) cmd_devices ;;
        uninstall) cmd_uninstall ;;
        -h|--help|help|"") usage ;;
        *)
            echo "Unknown command: $command" >&2
            usage
            exit 1
            ;;
    esac
}

main "$@"
