#!/usr/bin/env python3
"""OpenMiniDisplay controller reference client (layout v2).

Examples:
  python3 reference_client.py ping 192.168.1.23
  python3 reference_client.py demo 192.168.1.23
  python3 reference_client.py push-layout 192.168.1.23 --layout minimal_layout.json
  python3 reference_client.py push-layout 192.168.1.23 --layout minimal_layout.json --set title/title Hello

Requires: Python 3.9+ (stdlib only)
Spec: docs/CONTROLLER_INTEGRATION.md
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
import time
from pathlib import Path

PORT = 15180
PING_INTERVAL_SEC = 2.0

EXAMPLES_DIR = Path(__file__).resolve().parent

# Default on-device layout (3 pages). See CONTROLLER_INTEGRATION.md §8.1.
DEFAULT_SET_LOOP = [
    ("title/title", "OpenMiniDisplay"),
    ("subtitle/subtitle", "Controlled from PC"),
    ("status/status", "Connected"),
    ("progress/progress", "65"),
    ("ring/ring", "72"),
    ("line/line", "10,20,15,30,25"),
    ("bar/bar", "6,14,10,22"),
    ("pie/pie", "CPU:30,MEM:25,IO:20,NET:25"),
    ("metric/metric", "42.0"),
    ("footer/footer", "Port 15180"),
]

PUSH_LAYOUT_SET_LOOP = [
    ("title/title", "PC Dashboard"),
    ("cpu/cpu", "72"),
    ("mem/mem", "54"),
]


def send_line(sock: socket.socket, line: str) -> None:
    sock.sendall((line.rstrip("\n") + "\n").encode("utf-8"))


def connect(host: str) -> socket.socket:
    sock = socket.create_connection((host, PORT), timeout=5)
    sock.settimeout(None)
    send_line(sock, "OPENMINIDISPLAY")
    send_line(sock, "PING")
    return sock


def load_layout(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        layout = json.load(handle)
    if layout.get("version", 0) < 2:
        raise ValueError("layout version must be >= 2")
    if not layout.get("pages"):
        raise ValueError("layout must contain at least one page")
    return layout


def push_layout(sock: socket.socket, layout: dict) -> None:
    payload = json.dumps(layout, separators=(",", ":"), ensure_ascii=False)
    send_line(sock, f"LAYOUT {payload}")
    send_line(sock, "PING")


def run_ping_loop(sock: socket.socket, host: str) -> None:
    print(f"ping loop on {host}:{PORT} (Ctrl+C to stop)")
    while True:
        send_line(sock, "PING")
        print("PING")
        time.sleep(PING_INTERVAL_SEC)


def run_set_loop(sock: socket.socket, pairs: list[tuple[str, str]], label: str) -> None:
    print(f"{label} (Ctrl+C to stop)")
    cycle = 0
    while True:
        send_line(sock, "PING")
        for path, value in pairs:
            if "{cycle}" in value:
                value = value.replace("{cycle}", str(cycle))
            send_line(sock, f"SET {path} {value}")
        print(f"heartbeat + {len(pairs)} SET (cycle {cycle})")
        cycle += 1
        time.sleep(PING_INTERVAL_SEC)


def cmd_ping(args: argparse.Namespace) -> int:
    sock = connect(args.host)
    try:
        run_ping_loop(sock, args.host)
    except KeyboardInterrupt:
        print("stopped")
    finally:
        sock.close()
    return 0


def cmd_demo(args: argparse.Namespace) -> int:
    sock = connect(args.host)
    pairs = list(DEFAULT_SET_LOOP)
    pairs[2] = ("status/status", f"cycle {{cycle}}")
    try:
        run_set_loop(sock, pairs, "demo SET loop on default layout")
    except KeyboardInterrupt:
        print("stopped")
    finally:
        sock.close()
    return 0


def cmd_push_layout(args: argparse.Namespace) -> int:
    layout_path = Path(args.layout)
    if not layout_path.is_file():
        layout_path = EXAMPLES_DIR / args.layout
    if not layout_path.is_file():
        print(f"layout file not found: {args.layout}", file=sys.stderr)
        return 1

    layout = load_layout(layout_path)
    sock = connect(args.host)
    try:
        push_layout(sock, layout)
        print(f"pushed layout from {layout_path}")

        if args.set:
            for item in args.set:
                if "/" not in item:
                    print(f"ignored --set (need card/component): {item}", file=sys.stderr)
                    continue
                path, _, value = item.partition(" ")
                if not value:
                    print(f"ignored --set (missing value): {item}", file=sys.stderr)
                    continue
                send_line(sock, f"SET {path} {value}")
                print(f"SET {path} {value}")

        pairs = PUSH_LAYOUT_SET_LOOP if layout_path.name == "minimal_layout.json" else []
        if args.set:
            # Also keep heartbeat with explicit --set only once; then ping-only or loop defaults
            if not pairs:
                run_ping_loop(sock, args.host)
                return 0
        if pairs:
            run_set_loop(sock, pairs, "SET loop for minimal_layout.json")
        else:
            run_ping_loop(sock, args.host)
    except KeyboardInterrupt:
        print("stopped")
    finally:
        sock.close()
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="OpenMiniDisplay reference controller (layout v2)",
    )
    parser.add_argument("host", help="Display device IP address")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("ping", help="Handshake + PING loop only")

    sub.add_parser("demo", help="SET loop targeting the app default layout")

    push = sub.add_parser("push-layout", help="Send LAYOUT JSON then SET and/or PING")
    push.add_argument(
        "--layout",
        default="minimal_layout.json",
        help="Path to layout JSON (default: docs/examples/minimal_layout.json)",
    )
    push.add_argument(
        "--set",
        action="append",
        metavar="CARD/COMP VALUE",
        help="Optional SET after layout (repeatable)",
    )

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if args.command == "ping":
            return cmd_ping(args)
        if args.command == "demo":
            return cmd_demo(args)
        if args.command == "push-layout":
            return cmd_push_layout(args)
    except OSError as exc:
        print(f"connect failed: {exc}", file=sys.stderr)
        return 1
    except (json.JSONDecodeError, ValueError) as exc:
        print(f"layout error: {exc}", file=sys.stderr)
        return 1
    parser.error("unknown command")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
