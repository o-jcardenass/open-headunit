#!/usr/bin/env python3
"""
Recover .proto definitions from a protobuf-c binary.

protobuf-c does not strip its schema: every generated message carries a
ProtobufCMessageDescriptor in .data.rel.ro, holding the message name, field names, field
numbers, labels and wire types as ordinary data. Finding the descriptors' magic word is
enough to reconstruct the .proto.

Written to recover the ZBT IPC schema from ZLink's libzbt-main.so - the protocol OHU would
have to speak to reach an external Bluetooth module's RFCOMM channel (see the
706-external-bt-module note). Nothing here is specific to that library.

Usage:
    python3 extract_zbt_proto.py libzbt-main.so [-o zbt.proto]

Only 32-bit little-endian ELF (armeabi-v7a) and 64-bit little-endian are handled, which
covers every Android .so worth pointing this at.
"""

import argparse
import struct
import sys
from collections import OrderedDict

MESSAGE_MAGIC = 0x28AAEEF9
ENUM_MAGIC = 0x114315AF

# protobuf-c 1.x ProtobufCType, in declaration order.
PROTOBUF_C_TYPES = [
    "int32", "sint32", "sfixed32", "int64", "sint64", "sfixed64",
    "uint32", "fixed32", "uint64", "fixed64", "float", "double",
    "bool", "enum", "string", "bytes", "message",
]

LABELS = {0: "required", 1: "optional", 2: "repeated", 3: ""}


class Elf:
    """Just enough ELF to turn a virtual address into bytes."""

    def __init__(self, data):
        if data[:4] != b"\x7fELF":
            raise ValueError("not an ELF file")
        self.data = data
        self.is64 = data[4] == 2
        if data[5] != 1:
            raise ValueError("only little-endian is supported")
        self.ptr = 8 if self.is64 else 4
        self.segments = self._read_segments()

    def _read_segments(self):
        d = self.data
        if self.is64:
            e_phoff = struct.unpack_from("<Q", d, 0x20)[0]
            e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x36)
        else:
            e_phoff = struct.unpack_from("<I", d, 0x1C)[0]
            e_phentsize, e_phnum = struct.unpack_from("<HH", d, 0x2A)
        segments = []
        for i in range(e_phnum):
            off = e_phoff + i * e_phentsize
            if self.is64:
                p_type, = struct.unpack_from("<I", d, off)
                p_offset, p_vaddr = struct.unpack_from("<QQ", d, off + 0x08)
                p_filesz, = struct.unpack_from("<Q", d, off + 0x20)
            else:
                p_type, p_offset, p_vaddr = struct.unpack_from("<III", d, off)
                p_filesz, = struct.unpack_from("<I", d, off + 0x10)
            if p_type == 1:  # PT_LOAD
                segments.append((p_vaddr, p_offset, p_filesz))
        return segments

    def offset_of(self, vaddr):
        for p_vaddr, p_offset, p_filesz in self.segments:
            if p_vaddr <= vaddr < p_vaddr + p_filesz:
                return p_offset + (vaddr - p_vaddr)
        return None

    def read(self, vaddr, size):
        off = self.offset_of(vaddr)
        if off is None or off + size > len(self.data):
            return None
        return self.data[off:off + size]

    def read_ptr(self, vaddr):
        raw = self.read(vaddr, self.ptr)
        if raw is None:
            return None
        return struct.unpack("<Q" if self.is64 else "<I", raw)[0]

    def read_u32(self, vaddr):
        raw = self.read(vaddr, 4)
        return None if raw is None else struct.unpack("<I", raw)[0]

    def read_cstr(self, vaddr, limit=512):
        if not vaddr:
            return None
        off = self.offset_of(vaddr)
        if off is None:
            return None
        end = self.data.find(b"\x00", off, off + limit)
        if end < 0:
            return None
        try:
            return self.data[off:end].decode("utf-8")
        except UnicodeDecodeError:
            return None


def field_size(elf):
    # name, id, label, type, quantifier_offset, offset, descriptor, default_value,
    # flags, reserved_flags, reserved2, reserved3
    # Pointers are ptr-sized; the rest are 32-bit. 64-bit builds pad each 32-bit pair.
    return 12 * elf.ptr if elf.is64 else 12 * 4


def parse_field(elf, addr):
    p = elf.ptr
    if elf.is64:
        # name*, id, label, type, quantifier_offset, offset, descriptor*, default_value*,
        # flags, reserved_flags, reserved2*, reserved3*  -- with natural alignment the four
        # 32-bit members after name pack into two 8-byte slots.
        name = elf.read_cstr(elf.read_ptr(addr))
        fid = elf.read_u32(addr + 8)
        label = elf.read_u32(addr + 12)
        ftype = elf.read_u32(addr + 16)
        desc = elf.read_ptr(addr + 32)
    else:
        name = elf.read_cstr(elf.read_ptr(addr))
        fid = elf.read_u32(addr + 4)
        label = elf.read_u32(addr + 8)
        ftype = elf.read_u32(addr + 12)
        desc = elf.read_ptr(addr + 24)
    return name, fid, label, ftype, desc


def parse_message(elf, addr):
    p = elf.ptr
    # magic, name*, short_name*, c_name*, package_name*, sizeof_message,
    # n_fields, fields*, ...
    name = elf.read_cstr(elf.read_ptr(addr + p))
    short_name = elf.read_cstr(elf.read_ptr(addr + 2 * p))
    package = elf.read_cstr(elf.read_ptr(addr + 4 * p))
    # sizeof_message is size_t, so ptr-sized; n_fields is unsigned.
    n_fields = elf.read_u32(addr + 6 * p)
    fields_ptr = elf.read_ptr(addr + 7 * p) if elf.is64 else elf.read_ptr(addr + 7 * p)
    if name is None or n_fields is None or n_fields > 512 or not fields_ptr:
        return None
    fsz = field_size(elf)
    fields = []
    for i in range(n_fields):
        f = parse_field(elf, fields_ptr + i * fsz)
        if f[0] is None:
            return None
        fields.append(f)
    return {
        "name": name,
        "short_name": short_name or name.rsplit(".", 1)[-1],
        "package": package,
        "fields": fields,
    }


def parse_enum(elf, addr):
    p = elf.ptr
    name = elf.read_cstr(elf.read_ptr(addr + p))
    short_name = elf.read_cstr(elf.read_ptr(addr + 2 * p))
    package = elf.read_cstr(elf.read_ptr(addr + 4 * p))
    n_values = elf.read_u32(addr + 5 * p)
    values_ptr = elf.read_ptr(addr + 6 * p)
    if name is None or n_values is None or n_values > 512 or not values_ptr:
        return None
    # ProtobufCEnumValue: const char *name; const char *c_name; int value;
    vsz = 2 * p + (8 if elf.is64 else 4)
    values = []
    for i in range(n_values):
        base = values_ptr + i * vsz
        vname = elf.read_cstr(elf.read_ptr(base))
        value = elf.read_u32(base + 2 * p)
        if vname is None or value is None:
            return None
        values.append((vname, value if value < 2 ** 31 else value - 2 ** 32))
    return {
        "name": name,
        "short_name": short_name or name.rsplit(".", 1)[-1],
        "package": package,
        "values": values,
    }


def scan(elf, magic):
    """Every vaddr in a loaded segment holding `magic` as a 32-bit word."""
    needle = struct.pack("<I", magic)
    hits = []
    for p_vaddr, p_offset, p_filesz in elf.segments:
        chunk = elf.data[p_offset:p_offset + p_filesz]
        start = 0
        while True:
            i = chunk.find(needle, start)
            if i < 0:
                break
            if i % 4 == 0:
                hits.append(p_vaddr + i)
            start = i + 1
    return hits


def type_name(ftype, desc_name):
    if ftype is None or ftype >= len(PROTOBUF_C_TYPES):
        return "bytes"
    t = PROTOBUF_C_TYPES[ftype]
    if t in ("message", "enum") and desc_name:
        return desc_name
    return t


def render(messages, enums):
    out = ["// Recovered from protobuf-c descriptors by extract_zbt_proto.py.",
           "// Field names, numbers, labels and types are exact; syntax and package are",
           "// inferred. Message and enum ordering follows address order in the binary.",
           'syntax = "proto2";', ""]
    packages = {m["package"] for m in messages.values() if m["package"]}
    packages |= {e["package"] for e in enums.values() if e["package"]}
    if len(packages) == 1:
        out.append("package %s;" % packages.pop())
        out.append("")

    for e in enums.values():
        out.append("enum %s {" % e["short_name"])
        for vname, value in e["values"]:
            out.append("  %s = %d;" % (vname, value))
        out.append("}")
        out.append("")

    by_full_name = {m["name"]: m["short_name"] for m in messages.values()}
    by_full_name.update({e["name"]: e["short_name"] for e in enums.values()})

    for m in messages.values():
        out.append("message %s {" % m["short_name"])
        for name, fid, label, ftype, desc in m["fields"]:
            desc_short = None
            if desc:
                desc_short = by_full_name.get(_desc_full_names.get(desc))
            lbl = LABELS.get(label, "optional")
            lbl = (lbl + " ") if lbl else ""
            out.append("  %s%s %s = %d;" % (lbl, type_name(ftype, desc_short), name, fid))
        out.append("}")
        out.append("")
    return "\n".join(out)


_desc_full_names = {}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("binary")
    ap.add_argument("-o", "--output")
    args = ap.parse_args()

    with open(args.binary, "rb") as fh:
        elf = Elf(fh.read())

    messages, enums = OrderedDict(), OrderedDict()
    for addr in scan(elf, ENUM_MAGIC):
        e = parse_enum(elf, addr)
        if e:
            enums[addr] = e
            _desc_full_names[addr] = e["name"]
    for addr in scan(elf, MESSAGE_MAGIC):
        m = parse_message(elf, addr)
        if m:
            messages[addr] = m
            _desc_full_names[addr] = m["name"]

    sys.stderr.write("%s: %d messages, %d enums\n"
                     % (args.binary, len(messages), len(enums)))
    text = render(messages, enums)
    if args.output:
        with open(args.output, "w") as fh:
            fh.write(text + "\n")
        sys.stderr.write("wrote %s\n" % args.output)
    else:
        print(text)


if __name__ == "__main__":
    main()
