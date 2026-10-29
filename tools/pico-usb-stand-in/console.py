#!/usr/bin/env python3
"""Send one command to the stand-in's diag console and print what it answers.

Usage: console.py [--port /dev/ttyACMn] [i|1|2|3|4|5|b]
Without --port it finds the board by its console's USB ID, 2e8a:000a, because another device can
hold ttyACM0.
Linux only, stdlib only. 'i' prints the info text; a digit stores that identity and reboots; 'b'
reboots into BOOTSEL so a new .uf2 can be copied onto the RPI-RP2 drive.
"""
import argparse
import glob
import os
import sys
import select
import termios
import time

ap = argparse.ArgumentParser()
ap.add_argument("--port")
ap.add_argument("command", nargs="?", default="i", choices=list("i12345b"))
args = ap.parse_args()


def find_port():
    for tty in sorted(glob.glob("/sys/class/tty/ttyACM*")):
        usb = os.path.realpath(os.path.join(tty, "device", ".."))
        try:
            ids = [open(os.path.join(usb, f)).read().strip() for f in ("idVendor", "idProduct")]
        except OSError:
            continue
        if ids == ["2e8a", "000a"]:
            return "/dev/" + os.path.basename(tty)
    sys.exit("no 2e8a:000a console found; is the board on identity 0? (hold KEY 4 s)")


if args.port is None:
    args.port = find_port()

fd = os.open(args.port, os.O_RDWR | os.O_NOCTTY)  # opening the tty raises DTR, which the board waits for
attrs = termios.tcgetattr(fd)
attrs[3] &= ~(termios.ICANON | termios.ECHO)
termios.tcsetattr(fd, termios.TCSANOW, attrs)


def drain(seconds):
    out, end = b"", time.time() + seconds
    while time.time() < end:
        ready, _, _ = select.select([fd], [], [], 0.1)
        if ready:
            try:
                out += os.read(fd, 4096)
            except OSError:  # the board left the bus after a switch or BOOTSEL
                break
    return out.decode(errors="replace")


drain(0.8)  # the banner printed on connect; 'i' prints it again
os.write(fd, args.command.encode())
print(drain(1.0), end="")
os.close(fd)
