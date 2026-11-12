#!/usr/bin/env python3
"""Forwards RTP datagrams from one UDP port to another, and loses some on purpose.

Times are seconds from the first datagram. --drop-at loses a burst, --start-at loses
everything before it (a receiver that starts late), --loss loses at random, and
--rcvbuf with --drain-pps copies a slow receiver with a small socket buffer, where
the kernel itself drops what does not fit. --forward repeats to feed several receivers,
--record keeps every datagram with its arrival time for rtp_replay.py.
"""
import argparse
import random
import socket
import struct
import sys
import time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, required=True)
    ap.add_argument("--forward", action="append", required=True, help="host:port, repeatable")
    ap.add_argument("--bind", default="127.0.0.1", help="address to listen on")
    ap.add_argument("--first-wait", type=float, default=30.0, help="seconds to wait for the first datagram")
    ap.add_argument("--record", help="file for each datagram: t_ns u64, len u16, bytes")
    ap.add_argument("--drop-at", type=float, action="append", default=[])
    ap.add_argument("--drop-count", type=int, default=5)
    ap.add_argument("--start-at", type=float, default=0.0)
    ap.add_argument("--loss", type=float, default=0.0)
    ap.add_argument("--rcvbuf", type=int, default=0)
    ap.add_argument("--drain-pps", type=float, default=0.0)
    ap.add_argument("--idle", type=float, default=4.0)
    ap.add_argument("--seed", type=int, default=1)
    a = ap.parse_args()

    dests = []
    for target in a.forward:
        host, port = target.rsplit(":", 1)
        dests.append((host, int(port)))
    record = open(a.record, "wb") if a.record else None
    rx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    if a.rcvbuf:
        rx.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, a.rcvbuf)
    rx.bind((a.bind, a.listen))
    rx.settimeout(a.first_wait)
    tx = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rng = random.Random(a.seed)
    pending = sorted(a.drop_at)
    burst_left = 0
    t0 = None
    seen = forwarded = dropped = 0
    print(f"PROXY listening={a.listen} forward={",".join(a.forward)} rcvbuf={rx.getsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF)}", flush=True)
    while True:
        try:
            data = rx.recv(65536)
        except socket.timeout:
            break
        now = time.monotonic()
        if t0 is None:
            t0 = now
            rx.settimeout(a.idle)
        t = now - t0
        seen += 1
        if record:
            record.write(struct.pack(">QH", int(t * 1e9), len(data)) + data)
        if pending and t >= pending[0]:
            pending.pop(0)
            burst_left = a.drop_count
        lose = t < a.start_at or burst_left > 0 or (a.loss and rng.random() < a.loss)
        if burst_left > 0:
            burst_left -= 1
        if lose:
            dropped += 1
        else:
            for dest in dests:
                tx.sendto(data, dest)
            forwarded += 1
        if a.drain_pps:
            time.sleep(1.0 / a.drain_pps)
    if record:
        record.close()
    print(f"PROXY seen={seen} forwarded={forwarded} dropped={dropped}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
