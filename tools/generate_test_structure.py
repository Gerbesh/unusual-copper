"""Write a minimal vanilla NBT GameTest scene, without additional dependencies."""
from pathlib import Path
import gzip
import struct


def string(value):
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def named(tag_type, name, value):
    return bytes([tag_type]) + string(name) + value


payload = b"\x0a\x00\x00"
payload += named(3, "DataVersion", struct.pack(">i", 3465))
payload += named(9, "size", b"\x03" + struct.pack(">iiii", 3, 5, 5, 5))
payload += named(9, "entities", b"\x0a" + struct.pack(">i", 0))
blocks = []
for x in range(5):
    for y in range(5):
        for z in range(5):
            blocks.append(named(9, "pos", b"\x03" + struct.pack(">iiii", 3, x, y, z))
                          + named(3, "state", struct.pack(">i", 0)) + b"\x00")
payload += named(9, "blocks", b"\x0a" + struct.pack(">i", len(blocks)) + b"".join(blocks))
palette = named(8, "Name", string("minecraft:air")) + b"\x00"
payload += named(9, "palette", b"\x0a" + struct.pack(">i", 1) + palette)
payload += b"\x00"
path = Path(__file__).resolve().parents[1] / "src/gameTest/resources/data/unusualcopper/structures/empty.nbt"
path.parent.mkdir(parents=True, exist_ok=True)
path.write_bytes(gzip.compress(payload, mtime=0))
print("Generated 5x5x5 empty GameTest structure")
