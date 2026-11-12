#!/usr/bin/env python3
"""Sends a lossy_proxy.py --record file again to host:port with its original spacing."""
import argparse
import socket
import struct
import sys
import time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("record")
    ap.add_argument("target", help="host:port")
    ap.add_argument("--speed", type=float, default=1.0, help="2.0 sends twice as fast")
    a = ap.parse_args()

    host, port = a.target.rsplit(":", 1)
    dest = (host, int(port))
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    start = time.monotonic()
    sent = 0
    with open(a.record, "rb") as f:
        while True:
            head = f.read(10)
            if len(head) < 10:
                break
            t_ns, size = struct.unpack(">QH", head)
            data = f.read(size)
            wait = start + t_ns / 1e9 / a.speed - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            tx.sendto(data, dest)
            sent += 1
    print(f"REPLAY sent={sent}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
