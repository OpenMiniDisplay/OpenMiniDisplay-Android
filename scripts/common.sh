#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.openminidisplay"
MAIN_ACTIVITY="${APP_ID}/.MainActivity"
LISTEN_PORT="15180"
APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
CONTAINER_APK_PATH="/workspace/app/build/outputs/apk/debug/app-debug.apk"

# https://github.com/Nigh/android-dev-docker — adb and Gradle run in container only; do not use host adb.
DOCKER_IMAGE="${ANDROID_DEV_IMAGE:-xianii/android-dev:latest}"

plugdev_gid() {
    getent group plugdev 2>/dev/null | cut -d: -f3 || true
}

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
        local plugdev
        plugdev="$(plugdev_gid)"
        if [[ -n "$plugdev" ]]; then
            args+=(--group-add "$plugdev")
        fi
    fi
    args+=("$DOCKER_IMAGE" "$@")
    "${args[@]}"
}

warn_adb_key_permissions() {
    if [[ -e "$HOME/.adb/adbkey" && ! -r "$HOME/.adb/adbkey" ]]; then
        echo "Warning: ~/.adb/adbkey is not readable (often root-owned from an old container run)." >&2
        echo "Fix: sudo chown -R \"$(id -un):$(id -gn)\" \"$HOME/.adb\"" >&2
        echo "Then remove the key and re-run ./scripts/dev.sh devices to regenerate inside the container." >&2
    fi
}

setup_toolchain() {
    require_docker
    ensure_cache_dirs
}

adb_cmd() {
    local mount_project=0
    if [[ "${1:-}" == "--project" ]]; then
        mount_project=1
        shift
    fi

    local -a adb_args=()
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        adb_args=(-s "$ANDROID_SERIAL")
    fi

    warn_adb_key_permissions

    local -a docker_extra=(--usb --user)
    if [[ "$mount_project" -eq 1 ]]; then
        docker_extra+=(--project "$ROOT_DIR")
    fi

    docker_run "${docker_extra[@]}" -- bash -c '
ensure_adb_key() {
    mkdir -p "$HOME/.adb"
    chmod 700 "$HOME/.adb"
    if [ ! -s "$HOME/.adb/adbkey" ]; then
        adb keygen "$HOME/.adb/adbkey"
        chmod 600 "$HOME/.adb/adbkey"
        echo "==> New adb key in ~/.adb — authorize once on device (Always allow)."
    fi
}
ensure_adb_key
adb start-server >/dev/null 2>&1
exec adb "$@"
' bash "${adb_args[@]}" "$@"
}

device_lines() {
    adb_cmd devices 2>/dev/null | awk 'NR>1 && NF>0'
}

require_device() {
    setup_toolchain

    local authorized unauthorized offline
    authorized="$(device_lines | awk '$2=="device"{print $1}')"
    unauthorized="$(device_lines | awk '$2=="unauthorized"{print $1}')"
    offline="$(device_lines | awk '$2=="offline"{print $1}')"

    if [[ -n "$unauthorized" ]]; then
        echo "Device connected but unauthorized:" >&2
        echo "$unauthorized" | sed 's/^/  /' >&2
        echo "Unlock the phone and accept the USB debugging prompt (Always allow)." >&2
        echo "If you mixed host adb with container adb, revoke USB debugging authorizations on the device and run ./scripts/dev.sh devices again." >&2
        exit 1
    fi

    if [[ -n "$offline" ]]; then
        echo "Device connected but offline:" >&2
        echo "$offline" | sed 's/^/  /' >&2
        exit 1
    fi

    local count
    count="$(printf '%s\n' "$authorized" | sed '/^$/d' | wc -l | tr -d ' ')"
    if [[ "$count" == "0" ]]; then
        echo "No authorized device found (container adb)." >&2
        echo "Connect a phone with USB debugging enabled, then run: ./scripts/dev.sh devices" >&2
        echo "See docs/ANDROID_DEV_CONTAINER.md for Docker USB / image requirements." >&2
        exit 1
    fi
    if [[ "$count" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
        echo "Multiple devices detected. Set ANDROID_SERIAL to one of:" >&2
        adb_cmd devices -l >&2
        exit 1
    fi
}

docker_run_project() {
    require_docker
    # ponytail: default --user matches upstream android-dev-docker; ANDROID_DEV_ROOT=1 for legacy root
    if [[ "${ANDROID_DEV_ROOT:-}" == "1" ]]; then
        docker_run --project "$ROOT_DIR" -- "$@"
    else
        docker_run --user --project "$ROOT_DIR" -- "$@"
    fi
}

print_header() {
    echo "==> $*"
}
