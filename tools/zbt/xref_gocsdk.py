#!/usr/bin/env python3
"""
Find the code that references a given string in a stripped ARM32 ELF, and disassemble around it.

Written to read `/system/bin/gocsdk_zj`, the ZBT daemon on QF001/K706 head units: 1.7 MB, ARM
EABI5, stripped, no symbols. The only handholds are its log format strings, so the way in is to
locate a string, find where its address is loaded, and read the code there.

Two reference forms are handled, which between them cover everything a GCC/Clang ARM32 build emits
for a .rodata address:

  * literal pool - a 4-byte word in .text holding the string's vaddr, loaded by a PC-relative LDR.
  * MOVW/MOVT pair - the address built as two 16-bit immediates. Thumb-2 encodes these as
    T3/T1 with the immediate split across four fields, so they are matched by decoding rather
    than by scanning for bytes.

Stdlib plus capstone. capstone is not installed on this machine; run through uv, which fetches it
into a cache without installing anything:

    uvx --with capstone python3 xref_gocsdk.py gocsdk_zj --string "enable_type"
    uvx --with capstone python3 xref_gocsdk.py gocsdk_zj --addr 0x1234 --before 40 --after 80

Nothing here is specific to that binary or that vendor.
"""

import argparse
import re
import struct
import sys

# ELF32 header layout, little-endian.
EI_NIDENT = 16
PT_LOAD = 1


class Elf32:
    """Enough of ELF32 to turn a virtual address into a file offset and back."""

    def __init__(self, blob):
        if blob[:4] != b"\x7fELF" or blob[4] != 1:
            raise ValueError("not a 32-bit ELF")
        if blob[5] != 1:
            raise ValueError("not little-endian")
        self.blob = blob
        (self.e_type, self.e_machine, _ver, self.e_entry, self.e_phoff, self.e_shoff,
         _flags, _ehsize, self.e_phentsize, self.e_phnum, self.e_shentsize,
         self.e_shnum, self.e_shstrndx) = struct.unpack_from("<HHIIIIIHHHHHH", blob, EI_NIDENT)
        if self.e_machine != 40:  # EM_ARM
            raise ValueError("not ARM (e_machine=%d)" % self.e_machine)
        self.segments = []
        for i in range(self.e_phnum):
            off = self.e_phoff + i * self.e_phentsize
            p_type, p_offset, p_vaddr, _paddr, p_filesz, p_memsz, p_flags, _align = \
                struct.unpack_from("<IIIIIIII", blob, off)
            if p_type == PT_LOAD:
                self.segments.append((p_vaddr, p_offset, p_filesz, p_memsz, p_flags))
        self.sections = self._read_sections()

    def _read_sections(self):
        out = []
        if not self.e_shoff:
            return out
        shstr_off = self.e_shoff + self.e_shstrndx * self.e_shentsize
        _n, _t, _f, _a, str_off, str_size = struct.unpack_from("<IIIIII", self.blob, shstr_off)
        strtab = self.blob[str_off:str_off + str_size]
        for i in range(self.e_shnum):
            off = self.e_shoff + i * self.e_shentsize
            sh_name, sh_type, sh_flags, sh_addr, sh_offset, sh_size = \
                struct.unpack_from("<IIIIII", self.blob, off)
            end = strtab.find(b"\0", sh_name)
            name = strtab[sh_name:end].decode("utf-8", "replace")
            out.append({"name": name, "type": sh_type, "flags": sh_flags,
                        "addr": sh_addr, "offset": sh_offset, "size": sh_size})
        return out

    def section(self, name):
        for s in self.sections:
            if s["name"] == name:
                return s
        return None

    def vaddr_to_off(self, vaddr):
        for p_vaddr, p_offset, p_filesz, _memsz, _flags in self.segments:
            if p_vaddr <= vaddr < p_vaddr + p_filesz:
                return p_offset + (vaddr - p_vaddr)
        return None

    def off_to_vaddr(self, off):
        for p_vaddr, p_offset, p_filesz, _memsz, _flags in self.segments:
            if p_offset <= off < p_offset + p_filesz:
                return p_vaddr + (off - p_offset)
        return None

    def exec_ranges(self):
        """(vaddr, offset, size) of every executable PT_LOAD, where code can live."""
        return [(v, o, sz) for (v, o, sz, _m, fl) in self.segments if fl & 1]


def find_strings(elf, pattern):
    """Every NUL-terminated string matching `pattern`, as (vaddr, text)."""
    rx = re.compile(pattern.encode() if isinstance(pattern, str) else pattern)
    hits = []
    for sec in elf.sections:
        # SHT_PROGBITS with no SHF_EXECINSTR - .rodata and friends.
        if sec["type"] != 1 or sec["flags"] & 0x4 or not sec["size"]:
            continue
        data = elf.blob[sec["offset"]:sec["offset"] + sec["size"]]
        for m in rx.finditer(data):
            start = data.rfind(b"\0", 0, m.start()) + 1
            end = data.find(b"\0", m.start())
            if end < 0:
                continue
            text = data[start:end]
            if not text or len(text) > 400:
                continue
            hits.append((sec["addr"] + start, text.decode("utf-8", "replace")))
    return sorted(set(hits))


def find_literal_pool_refs(elf, target_vaddr):
    """Offsets of 4-byte words in executable segments equal to `target_vaddr`."""
    needle = struct.pack("<I", target_vaddr)
    out = []
    for _v, off, size in elf.exec_ranges():
        blob = elf.blob[off:off + size]
        pos = blob.find(needle)
        while pos >= 0:
            if pos % 4 == 0:
                out.append(off + pos)
            pos = blob.find(needle, pos + 1)
    return out


def find_movw_movt_refs(elf, target_vaddr, md_thumb, md_arm):
    """
    Addresses where a MOVW/MOVT pair builds `target_vaddr`.

    Decoding rather than byte-matching, because the immediate is split across four fields in both
    encodings and the two halves need not be adjacent - a scheduler will happily put an unrelated
    instruction between them.
    """
    lo, hi = target_vaddr & 0xFFFF, (target_vaddr >> 16) & 0xFFFF
    found = []
    for vaddr, off, size in elf.exec_ranges():
        code = elf.blob[off:off + size]
        for md in (md_thumb, md_arm):
            pending = {}
            for insn in md.disasm(code, vaddr):
                if insn.id == 0:
                    continue  # skipdata filler
                m = insn.mnemonic
                if m not in ("movw", "movt") or len(insn.operands) != 2:
                    continue
                reg, imm = insn.operands[0], insn.operands[1]
                if imm.type != 2:  # ARM_OP_IMM
                    continue
                if m == "movw" and imm.imm == lo:
                    pending[reg.reg] = insn.address
                elif m == "movt" and imm.imm == hi and reg.reg in pending:
                    found.append(pending.pop(reg.reg))
    return sorted(set(found))


def build_pic_index(elf, md):
    """
    Map every address a PIC sequence computes -> the site that computes it.

    `gocsdk_zj` is position-independent, so a .rodata address is never a word in the literal pool.
    The compiler emits an offset instead and adds the program counter to it:

        ldr  rX, [pc, #imm]     ; rX = target - (addr_of_add + 4)
        add  rX, pc             ; rX = target

    which means a scan for the absolute address finds nothing. Recovering the target needs both
    halves, so this walks the whole text once, remembers what each LDR loaded, and resolves the
    sum when the matching ADD turns up. Returns {target_vaddr: [site, ...]}.
    """
    index = {}
    for vaddr, off, size in elf.exec_ranges():
        code = elf.blob[off:off + size]
        loaded = {}  # register -> value most recently loaded from a literal pool
        for insn in md.disasm(code, vaddr):
            if insn.id == 0:
                continue  # skipdata filler: undecodable bytes, no operands to read
            m, ops = insn.mnemonic, insn.operands
            if m == "ldr" and len(ops) == 2 and ops[1].type == 3:  # ARM_OP_MEM
                mem = ops[1].mem
                # capstone reports pc as register 11 in ARM mode and resolves the base itself;
                # comparing the register name keeps this readable and mode-independent.
                if insn.reg_name(mem.base) == "pc":
                    pool = ((insn.address + 4) & ~3) + mem.disp
                    pool_off = elf.vaddr_to_off(pool)
                    if pool_off is not None and pool_off + 4 <= len(elf.blob):
                        loaded[ops[0].reg] = struct.unpack_from("<I", elf.blob, pool_off)[0]
                        continue
                loaded.pop(ops[0].reg, None)
            elif m == "add" and len(ops) == 2 and ops[1].type == 1 \
                    and insn.reg_name(ops[1].reg) == "pc" and ops[0].reg in loaded:
                # Thumb reads pc as the instruction address + 4.
                target = (loaded.pop(ops[0].reg) + insn.address + 4) & 0xFFFFFFFF
                index.setdefault(target, []).append(insn.address)
            elif ops and ops[0].type == 1 and m not in ("cmp", "cmn", "tst", "teq"):
                loaded.pop(ops[0].reg, None)  # register overwritten; its pool value is stale
    return index


def read_cstring(elf, vaddr, limit=200):
    """The NUL-terminated string at `vaddr`, or None if that is not printable text."""
    off = elf.vaddr_to_off(vaddr)
    if off is None:
        return None
    end = elf.blob.find(b"\0", off, off + limit)
    if end < 0 or end == off:
        return None
    raw = elf.blob[off:end]
    if any(b < 0x09 or (0x0e <= b < 0x20) or b >= 0x7f for b in raw):
        return None
    return raw.decode("ascii")


def disasm_around(elf, md, vaddr, before, after):
    """
    Disassemble a window either side of `vaddr`.

    Every PC-relative address the window builds is resolved and, when it points at text, shown as a
    trailing comment. In a stripped binary the log strings are the only names there are, so putting
    them beside the code they belong to is most of what makes it readable.
    """
    start = vaddr - before
    off = elf.vaddr_to_off(start)
    if off is None:
        return ["  <%#x is not in a mapped segment>" % start]
    code = elf.blob[off:off + before + after]
    lines = []
    loaded = {}
    for insn in md.disasm(code, start):
        note = ""
        if insn.id != 0:
            m, ops = insn.mnemonic, insn.operands
            if m == "ldr" and len(ops) == 2 and ops[1].type == 3 \
                    and insn.reg_name(ops[1].mem.base) == "pc":
                pool = ((insn.address + 4) & ~3) + ops[1].mem.disp
                pool_off = elf.vaddr_to_off(pool)
                if pool_off is not None:
                    loaded[ops[0].reg] = struct.unpack_from("<I", elf.blob, pool_off)[0]
            elif m == "add" and len(ops) == 2 and ops[1].type == 1 \
                    and insn.reg_name(ops[1].reg) == "pc" and ops[0].reg in loaded:
                target = (loaded.pop(ops[0].reg) + insn.address + 4) & 0xFFFFFFFF
                text = read_cstring(elf, target)
                note = "  ; %#x %r" % (target, text) if text else "  ; %#x" % target
        mark = "->" if insn.address == vaddr else "  "
        lines.append("%s %#010x  %-8s %-28s%s" % (mark, insn.address, insn.mnemonic,
                                                  insn.op_str, note))
    return lines


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("elf")
    ap.add_argument("--string", help="regex; find this string and every reference to it")
    ap.add_argument("--addr", help="disassemble at this vaddr instead (hex or decimal)")
    ap.add_argument("--before", type=int, default=32, help="bytes before the site (default 32)")
    ap.add_argument("--after", type=int, default=96, help="bytes after the site (default 96)")
    ap.add_argument("--arm", action="store_true", help="decode as ARM, not Thumb")
    ap.add_argument("--list-only", action="store_true", help="print matching strings, no disassembly")
    args = ap.parse_args()

    try:
        import capstone
    except ImportError:
        sys.exit("capstone is missing. Run through uv:\n"
                 "  uvx --with capstone python3 %s ..." % sys.argv[0])

    with open(args.elf, "rb") as fh:
        elf = Elf32(fh.read())

    md_thumb = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
    md_arm = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)
    for md in (md_thumb, md_arm):
        md.detail = True
        md.skipdata = True
    md_main = md_arm if args.arm else md_thumb

    if args.addr:
        target = int(args.addr, 0)
        print("== %#x ==" % target)
        for line in disasm_around(elf, md_main, target, args.before, args.after):
            print(line)
        return

    if not args.string:
        ap.error("one of --string or --addr is required")

    hits = find_strings(elf, args.string)
    if not hits:
        sys.exit("no string matched %r" % args.string)
    pic_index = None  # built lazily; the whole-text pass is only worth it if a string matched

    for vaddr, text in hits:
        print("=" * 78)
        print("string %#010x  %r" % (vaddr, text))
        if args.list_only:
            continue

        pool = find_literal_pool_refs(elf, vaddr)
        movs = find_movw_movt_refs(elf, vaddr, md_thumb, md_arm)
        if pic_index is None:
            pic_index = build_pic_index(elf, md_main)
        pic = pic_index.get(vaddr, [])
        if not pool and not movs and not pic:
            print("  no reference found (may be reached through a table or a computed address)")
            continue

        for site in pic:
            print("\n-- pc-relative construction at %#x --" % site)
            for line in disasm_around(elf, md_main, site, args.before, args.after):
                print(line)

        for off in pool:
            site = elf.off_to_vaddr(off)
            print("\n-- literal pool slot at %#x, code around it --" % site)
            for line in disasm_around(elf, md_main, site, args.before, args.after):
                print(line)
        for site in movs:
            print("\n-- movw/movt at %#x --" % site)
            for line in disasm_around(elf, md_main, site, args.before, args.after):
                print(line)


if __name__ == "__main__":
    main()
