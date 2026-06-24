#!/usr/bin/env python3
"""Minimal OpenMiniDisplay controller reference client (layout v2).

Usage:
  python3 reference_client.py 192.168.1.23
  python3 reference_client.py 192.168.1.23 --metric 23.5

Requires: Python 3.9+ (stdlib only)
"""

from __future__ import annotations

import argparse
import socket
import sys
import time

PORT = 15180
PING_INTERVAL_SEC = 2.0


def send_line(sock: socket.socket, line: str) -> None:
    sock.sendall((line.rstrip("\n") + "\n").encode("utf-8"))


def connect(host: str) -> socket.socket:
    sock = socket.create_connection((host, PORT), timeout=5)
    sock.settimeout(None)
    send_line(sock, "OPENMINIDISPLAY")
    send_line(sock, "PING")
    return sock


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenMiniDisplay reference controller")
    parser.add_argument("host", help="Display device IP address")
    parser.add_argument("--metric", default="42.0", help="Value for SET metric/metric")
    args = parser.parse_args()

    try:
        sock = connect(args.host)
    except OSError as exc:
        print(f"connect failed: {exc}", file=sys.stderr)
        return 1

    print(f"connected to {args.host}:{PORT}")
    cycle = 0
    try:
        while True:
            send_line(sock, "PING")
            send_line(sock, f"SET metric/metric {args.metric}")
            send_line(sock, f"SET status/status cycle {cycle}")
            print(f"heartbeat + SET (cycle {cycle})")
            cycle += 1
            time.sleep(PING_INTERVAL_SEC)
    except KeyboardInterrupt:
        print("stopped")
    finally:
        sock.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
