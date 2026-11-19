"""Read-only, bounded MCA/NBT heightmap audit of a stopped disposable test save.

No Minecraft process, world write, screenshot editing, or extra package is used.
The projection is a diagnostic estimate, not a replacement for live GPU evidence.
"""
import gzip
import io
import math
from pathlib import Path
import re
import struct
import sys
import zlib


class Nbt:
    def __init__(self, data):
        if len(data) > 8 * 1024 * 1024:
            raise ValueError("NBT size limit")
        self.stream = io.BytesIO(data)

    def read(self, size):
        value = self.stream.read(size)
        if len(value) != size:
            raise ValueError("Truncated NBT")
        return value

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.read(struct.calcsize(">" + fmt)))[0]

    def length(self):
        value = self.number("i")
        if not 0 <= value <= 2_000_000:
            raise ValueError("Invalid NBT collection length")
        return value

    def name(self):
        return self.read(self.number("H")).decode("utf-8")

    def payload(self, kind, depth=0):
        if depth > 64:
            raise ValueError("NBT depth limit")
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 7:
            return self.read(self.length())
        if kind == 8:
            return self.name()
        if kind == 9:
            element = self.number("B")
            return [self.payload(element, depth + 1) for _ in range(self.length())]
        if kind == 10:
            value = {}
            while True:
                child = self.number("B")
                if child == 0:
                    return value
                name = self.name()
                value[name] = self.payload(child, depth + 1)
        if kind in (11, 12):
            return [self.number("i" if kind == 11 else "q") for _ in range(self.length())]
        raise ValueError(f"Unsupported NBT tag {kind}")

    def root(self):
        if self.number("B") != 10:
            raise ValueError("Expected compound root")
        self.name()
        return self.payload(10)


def chunks(region):
    with region.open("rb") as source:
        header = source.read(4096)
        if len(header) != 4096:
            raise ValueError("Truncated region header")
        for index in range(1024):
            offset = int.from_bytes(header[index * 4:index * 4 + 3], "big")
            sectors = header[index * 4 + 3]
            if offset == 0:
                continue
            if offset < 2 or sectors == 0:
                raise ValueError("Invalid region offset")
            source.seek(offset * 4096)
            framing = source.read(5)
            if len(framing) != 5:
                raise ValueError("Truncated chunk framing")
            length, compression = struct.unpack(">IB", framing)
            if not 1 <= length <= sectors * 4096 - 4:
                raise ValueError("Invalid chunk length")
            if compression & 128:
                raise ValueError("External chunk streams not supported by this audit")
            compressed = source.read(length - 1)
            if len(compressed) != length - 1:
                raise ValueError("Truncated chunk")
            if compression == 2:
                inflater = zlib.decompressobj()
                data = inflater.decompress(compressed, 8 * 1024 * 1024 + 1)
                if not inflater.eof or inflater.unconsumed_tail:
                    raise ValueError("Compressed NBT limit or incomplete stream")
            elif compression == 3:
                data = compressed
            else:
                raise ValueError(f"Compression {compression} not supported by this audit")
            yield Nbt(data).root()


def project(x, y, z):
    # Matched gallery camera: feet (48.5, 96, 48.5), yaw -45, pitch 18.
    dx, dy, dz = x - 48.5, y - 97.62, z - 48.5
    yaw, pitch = math.radians(-45), math.radians(18)
    forward = -math.sin(yaw) * dx + math.cos(yaw) * dz
    right = -math.cos(yaw) * dx - math.sin(yaw) * dz
    depth = math.cos(pitch) * forward - math.sin(pitch) * dy
    up = math.sin(pitch) * forward + math.cos(pitch) * dy
    if depth <= 0:
        return None
    tangent = math.tan(math.radians(70) / 2)
    return (640 + 360 * right / (depth * tangent),
            360 - 360 * up / (depth * tangent), math.hypot(dx, dz))


def main():
    repo = Path(__file__).resolve().parent.parent
    save = repo / "tools/artifact-smoke/run-26.2-milestone-gallery-20261028/saves/BufferRoutingTest"
    regions = save / "dimensions/minecraft/overworld/region"
    level = Nbt(gzip.decompress((save / "level.dat").read_bytes())).root()["Data"]
    print("Level keys:", ", ".join(sorted(level)))
    count = 0
    candidates = []
    for region in sorted(regions.glob("r.*.*.mca")):
        match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", region.name)
        if not match or any(abs(int(v)) > 1 for v in match.groups()):
            continue
        for chunk in chunks(region):
            cx, cz = chunk["xPos"], chunk["zPos"]
            if abs(cx - 3) > 12 or abs(cz - 3) > 12:
                continue
            raw = chunk.get("Heightmaps", {}).get("WORLD_SURFACE")
            if raw is None:
                continue
            if len(raw) != 37:
                raise ValueError(f"Expected 384-high Overworld's 9-bit heightmap, got {len(raw)} longs")
            count += 1
            for index in range(256):
                height = ((raw[index // 7] >> ((index % 7) * 9)) & 511) - 64
                x, z = cx * 16 + index % 16, cz * 16 + index // 16
                point = project(x + .5, height, z + .5)
                if point and 180 <= point[0] <= 390 and 40 <= point[1] <= 240:
                    candidates.append((height, x, z, point))
    print("Read-only heightmapped chunks:", count)
    print("Saved columns projected near the apparent spike (1280x720 reference):")
    for height, x, z, point in sorted(candidates, reverse=True)[:24]:
        print(f"  block column x={x} z={z}, first-air y={height}, screen=({point[0]:.1f},{point[1]:.1f}), horizontal distance={point[2]:.1f}")
    print("Matching saved terrain is a hypothesis to verify with live shaders-off viewing, not GPU correctness proof.")


if __name__ == "__main__":
    main()
