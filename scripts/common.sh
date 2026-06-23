#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.openminidisplay"
MAIN_ACTIVITY="${APP_ID}/.MainActivity"
LISTEN_PORT="15180"
APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
CONTAINER_APK_PATH="/workspace/app/build/outputs/apk/debug/app-debug.apk"

# https://github.com/Nigh/android-dev-docker — use container adb/gradle only; do not mix with host adb.
DOCKER_IMAGE="${ANDROID_DEV_IMAGE:-xianii/android-dev:latest}"

ADB_INIT='
ensure_adb_key() {
    mkdir -p "$HOME/.adb"
    chmod 700 "$HOME/.adb"
    if [ ! -s "$HOME/.adb/adbkey" ]; then
        adb keygen "$HOME/.adb/adbkey"
        chmod 600 "$HOME/.adb/adbkey"
        echo "==> New adb key in ~/.adb — authorize once on device (Always allow)."
    fi
    adb start-server >/dev/null 2>&1
}
'

ensure_cache_dirs() {
    mkdir -p "$HOME/.gradle" "$HOME/.android" "$HOME/.adb"
}

require_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        echo "docker is required. Install Docker and pull ${DOCKER_IMAGE}." >&2
        exit 1
    fi
}

container_home() {
    if [[ -n "${DOCKER_RUN_USER:-}" ]]; then
        echo "/home/android-dev"
    else
        echo "/root"
    fi
}

# Usage: docker_run [--interactive] [--usb] [--user] [--project DIR] [--] <command...>
docker_run() {
    local usb=0 interactive=0
    local project=""
    DOCKER_RUN_USER=""

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --interactive) interactive=1; shift ;;
            --usb) usb=1; shift ;;
            --user) DOCKER_RUN_USER=1; shift ;;
            --project)
                project="$2"
                shift 2
                ;;
            --) shift; break ;;
            *) break ;;
        esac
    done

    require_docker
    ensure_cache_dirs

    local home
    home="$(container_home)"

    local -a args=(docker run --rm)
    if [[ "$interactive" -eq 1 ]]; then
        args+=(-it --init)
    fi
    if [[ -n "$DOCKER_RUN_USER" ]]; then
        args+=(--user "$(id -u):$(id -g)" -e "HOME=$home")
    fi

    args+=(
        -v "$HOME/.gradle:$home/.gradle"
        -v "$HOME/.android:$home/.android"
        -e "GRADLE_USER_HOME=$home/.gradle"
        -e "ANDROID_SDK_HOME=$home/.android"
    )
    if [[ "$usb" -eq 1 || -n "$DOCKER_RUN_USER" ]]; then
        args+=(-v "$HOME/.adb:$home/.adb")
    fi
    if [[ -n "$project" ]]; then
        args+=(-v "$(realpath "$project"):/workspace" -w /workspace)
    fi
    if [[ "$usb" -eq 1 ]]; then
        args+=(--device=/dev/bus/usb)
    fi
    args+=("$DOCKER_IMAGE" "$@")
    "${args[@]}"
}

setup_toolchain() {
    require_docker
    ensure_cache_dirs
}

adb_cmd() {
    local -a adb_args=()
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        adb_args=(-s "$ANDROID_SERIAL")
    fi

    # shellcheck disable=SC2046
    docker_run --usb bash -c "$ADB_INIT
ensure_adb_key
exec adb $(printf '%q ' "${adb_args[@]}" "$@")
"
}

require_device() {
    setup_toolchain

    local count
    count="$(adb_cmd devices | awk 'NR>1 && $2=="device"{print $1}' | wc -l | tr -d ' ')"
    if [[ "$count" == "0" ]]; then
        echo "No authorized device found." >&2
        echo "Connect a phone with USB debugging enabled, then run: ./scripts/dev.sh devices" >&2
        exit 1
    fi
    if [[ "$count" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
        echo "Multiple devices detected. Set ANDROID_SERIAL to one of:" >&2
        adb_cmd devices -l >&2
        exit 1
    fi
}

docker_run_project() {
    if [[ "${ANDROID_DEV_USER:-}" == "1" ]]; then
        docker_run --user --project "$ROOT_DIR" -- "$@"
    else
        docker_run --project "$ROOT_DIR" -- "$@"
    fi
}

print_header() {
    echo "==> $*"
}
