# Android dev container

OpenMiniDisplay builds and uses **adb only inside Docker** via [`xianii/android-dev`](https://hub.docker.com/r/xianii/android-dev) (see [android-dev-docker](https://github.com/Nigh/android-dev-docker)). **Do not use host `adb`** alongside container adb.

```bash
docker pull xianii/android-dev:latest
mkdir -p ~/.gradle ~/.android ~/.adb
```

---

## Persistent host directories

Always mounted by [`scripts/common.sh`](../scripts/common.sh) (matches upstream README):

| Host path | Container path (`--user`) | Purpose |
|-----------|---------------------------|---------|
| `~/.gradle` | `/home/android-dev/.gradle` | Gradle cache |
| `~/.android` | `/home/android-dev/.android` | **debug.keystore** (APK signing) |
| `~/.adb` | `/home/android-dev/.adb` | **adb RSA keys** (USB authorization) |

**Important:** Android Studio uses `~/.android/adbkey` for adb; container scripts use `~/.adb/adbkey`. These are **different keys**. Mixing host adb and container adb causes repeated authorization prompts or `unauthorized` devices. Fix: revoke USB debugging authorizations on the phone, then run `./scripts/dev.sh devices` once and tap **Always allow**.

---

## What OpenMiniDisplay scripts do

| Operation | Default identity | USB flags |
|-----------|------------------|-----------|
| `./scripts/dev.sh build` | host uid (`--user`) | — |
| `./scripts/dev.sh install` / `adb` | host uid (`--user`) | `--device=/dev/bus/usb`, `--group-add plugdev` |
| `./scripts/dev.sh shell` | host uid (`--user`) | USB + project mount |

Set `ANDROID_DEV_ROOT=1` to run Gradle/shell as container root (not recommended; can root-own `~/.adb` or `app/build/`).

Install runs `adb uninstall` first (same as upstream `dev.sh install --package`) to avoid `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when debug signing changed.

Smoke test (equivalent to `./scripts/dev.sh devices`):

```bash
docker run --rm \
  --device=/dev/bus/usb \
  --group-add "$(getent group plugdev | cut -d: -f3)" \
  --user "$(id -u):$(id -g)" \
  -e HOME=/home/android-dev \
  -v "$HOME/.adb:/home/android-dev/.adb" \
  xianii/android-dev:latest \
  adb devices -l
```

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Empty device list | User in `plugdev`; pull latest image; run smoke test above |
| `unauthorized` | Accept prompt; or revoke old authorizations and re-run `./scripts/dev.sh devices` |
| `~/.adb/adbkey` not readable | `sudo chown -R "$USER:$USER" ~/.adb` then regenerate via `./scripts/dev.sh devices` |
| Signature mismatch on install | Mount `~/.android` (default); `./scripts/dev.sh uninstall` then install |
| `dev.sh logs` drops with `- waiting for device -` | Avoid parallel `adb_cmd` containers; run one long adb session at a time |
| Gradle `Permission denied` on `~/.gradle` | `sudo chown -R "$USER:$USER" ~/.gradle` (often after old root container runs) |
| Docker `--user` build fails on SDK / `local.properties` | Remove or fix host `local.properties` `sdk.dir` (path must exist in container), or `ANDROID_DEV_ROOT=1 ./scripts/dev.sh build` |
| macOS / Windows Docker Desktop | USB passthrough unavailable; build in Docker, use network adb or Linux host for USB |

Full FAQ (image versions, NDK, CI): [android-dev-docker README](https://github.com/Nigh/android-dev-docker/blob/main/README.md).

---

## Related

- [`scripts/dev.sh`](../scripts/dev.sh), [`scripts/common.sh`](../scripts/common.sh)
- [`AGENTS.md`](../AGENTS.md)
