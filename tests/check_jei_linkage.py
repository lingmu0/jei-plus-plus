"""Check built mod class references against the installed JEI JAR.

Usage: python tests/check_jei_linkage.py <mod.jar> <jei.jar>
Catches removed JEI classes even when the Java compiler used an older JEI.
Only executable class references and member descriptors are checked; optional
mixin target names stored as annotation strings are deliberately excluded.
"""
import re
import struct
import sys
from zipfile import ZipFile


def referenced_jei_classes(data):
    assert data[:4] == b"\xca\xfe\xba\xbe", "Not a Java class"
    count = struct.unpack_from(">H", data, 8)[0]
    pool = {}
    offset = 10
    index = 1
    while index < count:
        tag = data[offset]
        offset += 1
        if tag == 1:
            size = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            pool[index] = (tag, data[offset:offset + size].decode("utf-8", errors="replace"))
            offset += size
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, struct.unpack_from(">H", data, offset)[0])
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, struct.unpack_from(">HH", data, offset))
            offset += 4
        elif tag in (3, 4):
            offset += 4
        elif tag in (5, 6):
            offset += 8
            index += 1
        elif tag == 15:
            offset += 3
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        index += 1
    for tag, value in pool.values():
        if tag == 7:
            name = pool[value][1]
            if name.startswith("mezz/jei/"):
                yield name
            else:
                yield from re.findall(r"L(mezz/jei/[\w/$]+);", name)
        elif tag in (12, 16):
            descriptor = pool[value[1] if tag == 12 else value][1]
            yield from re.findall(r"L(mezz/jei/[\w/$]+);", descriptor)


def check(mod_path, jei_path):
    with ZipFile(jei_path) as jei, ZipFile(mod_path) as mod:
        available = {name[:-6] for name in jei.namelist() if name.endswith(".class")}
        missing = []
        checked = set()
        for name in mod.namelist():
            if name.endswith(".class"):
                references = set(referenced_jei_classes(mod.read(name)))
                checked.update(references)
                missing.extend(f"{name}: {ref}" for ref in sorted(references - available))
    if missing:
        raise SystemExit("Missing JEI classes:\n" + "\n".join(missing))
    print(f"PASS: {len(checked)} referenced JEI classes exist in {jei_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    check(*sys.argv[1:])
