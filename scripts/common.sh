#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.openminidisplay"
MAIN_ACTIVITY="${APP_ID}/.MainActivity"
LISTEN_PORT="15180"
APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"

resolve_sdk_dir() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        echo "$ANDROID_SDK_ROOT"
        return
    fi
    if [[ -n "${ANDROID_HOME:-}" ]]; then
        echo "$ANDROID_HOME"
        return
    fi
    if [[ -f "${ROOT_DIR}/local.properties" ]]; then
        local sdk_dir
        sdk_dir="$(grep -E '^sdk\.dir=' "${ROOT_DIR}/local.properties" | cut -d= -f2- | tr -d '\r')"
        if [[ -n "$sdk_dir" ]]; then
            echo "$sdk_dir"
            return
        fi
    fi
    if [[ -d "${HOME}/Android/Sdk" ]]; then
        echo "${HOME}/Android/Sdk"
        return
    fi
    echo "Unable to locate Android SDK. Set ANDROID_SDK_ROOT or create local.properties." >&2
    exit 1
}

setup_toolchain() {
    local sdk_dir
    sdk_dir="$(resolve_sdk_dir)"
    export ANDROID_SDK_ROOT="$sdk_dir"
    export ANDROID_HOME="$sdk_dir"
    export PATH="${sdk_dir}/platform-tools:${sdk_dir}/cmdline-tools/latest/bin:${PATH}"

    if ! command -v adb >/dev/null 2>&1; then
        echo "adb not found. Expected at: ${sdk_dir}/platform-tools/adb" >&2
        exit 1
    fi
}

require_device() {
    setup_toolchain

    local adb_args=()
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        adb_args=(-s "$ANDROID_SERIAL")
    fi

    local count
    count="$(adb "${adb_args[@]}" devices | awk 'NR>1 && $2=="device"{print $1}' | wc -l | tr -d ' ')"
    if [[ "$count" == "0" ]]; then
        echo "No authorized device found." >&2
        echo "Connect a phone with USB debugging enabled, then run: ./scripts/dev.sh devices" >&2
        exit 1
    fi
    if [[ "$count" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
        echo "Multiple devices detected. Set ANDROID_SERIAL to one of:" >&2
        adb devices -l >&2
        exit 1
    fi
}

adb_cmd() {
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        adb -s "$ANDROID_SERIAL" "$@"
    else
        adb "$@"
    fi
}

print_header() {
    echo "==> $*"
}
