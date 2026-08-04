#!/usr/bin/env python3
"""RCON block-probe: does every planet actually carry ore, at the right local depth?

Companion to `verify_cosmos_layout.py`, which only checks the *layout* JSON. This
one talks to a running headless dedicated server and counts real generated blocks,
so it is the acceptance gate for the planet-local ore wiring.

Planet coordinates are read from `planet_field.json` via
`verify_cosmos_layout.load_spheres()` — never hand-copied, so the probe cannot
drift away from the layout it is measuring.

Method per planet: force-load the chunks around the sphere's axis, wait for
generation, then split the sphere's Y extent into three slabs (lower / middle /
upper) and, for each ore, run `/fill <slab> minecraft:air replace <ore>`. The
command's "Successfully filled N block(s)" reply is the block count. This is
destructive — run it against a throwaway probe world only.

Usage:
    python tools/probe_planet_ores.py [--host 127.0.0.1] [--port 25599]
                                      [--password isekaiverify] [--half-width 8]

Exit 0 when every probed planet has coal or copper in its upper slab, iron
somewhere, and diamond or lapis in its lower slab; exit 1 listing the failures.
"""

from __future__ import annotations

import argparse
import re
import select
import socket
import struct
import sys
import time

from verify_cosmos_layout import Sphere, load_spheres

# logical ore -> the block ids it can generate as (plain + deepslate variant)
ORE_BLOCKS = {
    "coal": ("minecraft:coal_ore", "minecraft:deepslate_coal_ore"),
    "copper": ("minecraft:copper_ore", "minecraft:deepslate_copper_ore"),
    "iron": ("minecraft:iron_ore", "minecraft:deepslate_iron_ore"),
    "redstone": ("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"),
    "gold": ("minecraft:gold_ore", "minecraft:deepslate_gold_ore"),
    "lapis": ("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"),
    "diamond": ("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"),
    "emerald": ("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"),
}

SLABS = ("lower", "middle", "upper")
FILLED_RE = re.compile(r"Successfully filled (\d+)")


class Rcon:
    """Minimal synchronous RCON client (no third-party dependency)."""

    def __init__(self, host: str, port: int, password: str) -> None:
        self.sock = socket.create_connection((host, port), timeout=30)
        self.req_id = 0
        if self._send(3, password) is None:
            raise SystemExit("RCON auth failed")

    def _send(self, packet_type: int, body: str) -> str | None:
        self.req_id += 1
        sent_id = self.req_id
        payload = (
            struct.pack("<ii", sent_id, packet_type)
            + body.encode("utf-8")
            + b"\x00\x00"
        )
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        length = struct.unpack("<i", self._recv_exact(4))[0]
        resp_id, _ = struct.unpack("<ii", self._recv_exact(8))
        text = self._recv_exact(length - 8)[:-2].decode("utf-8", "replace")
        if resp_id != sent_id:
            return None  # auth failure is signalled by resp_id == -1
        return text

    def _recv_exact(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise SystemExit("RCON connection closed")
            buf += chunk
        return buf

    def cmd(self, command: str) -> str:
        return self._send(2, command) or ""

    def close(self) -> None:
        self.sock.close()


def wait_loaded(rcon: Rcon, x: int, y: int, z: int, timeout: float = 180.0) -> bool:
    """Block until the chunk containing (x,y,z) has generated.

    A naive probe against an ungenerated chunk silently reports neither branch,
    which historically read as a false 'solid'. Force-load first, then poll.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        if "The time is" in rcon.cmd(
            f"execute if loaded {x} {y} {z} run time query gametime"
        ):
            return True
        time.sleep(2.0)
    return False


def slab_bounds(s: Sphere) -> list[tuple[str, int, int]]:
    lo, hi = int(s.cy - s.r), int(s.cy + s.r)
    step = (hi - lo) / 3.0
    edges = [lo, int(lo + step), int(lo + 2 * step), hi]
    return [(SLABS[i], edges[i], edges[i + 1]) for i in range(3)]


def probe(rcon: Rcon, s: Sphere, half: int) -> dict[str, dict[str, int]]:
    w = max(2, min(half, int(s.r) - 4))
    x, z = int(s.cx), int(s.cz)
    rcon.cmd(f"forceload add {x - w} {z - w} {x + w} {z + w}")
    if not wait_loaded(rcon, x, int(s.cy), z):
        raise SystemExit(f"{s.name}: chunks never finished generating")

    counts: dict[str, dict[str, int]] = {}
    for slab, y0, y1 in slab_bounds(s):
        counts[slab] = {}
        for ore, blocks in ORE_BLOCKS.items():
            total = 0
            for block in blocks:
                reply = rcon.cmd(
                    f"fill {x - w} {y0} {z - w} {x + w} {y1} {z + w} "
                    f"minecraft:air replace {block}"
                )
                m = FILLED_RE.search(reply)
                total += int(m.group(1)) if m else 0
            counts[slab][ore] = total
    rcon.cmd(f"forceload remove {x - w} {z - w} {x + w} {z + w}")
    return counts


BIOMES = [
    "planet_verdant",
    "planet_ember",
    "planet_frost",
    "planet_stone",
    "planet_desert",
    "planet_mushroom",
    "planet_jungle",
    "planet_volcanic",
    "planet_crystal",
    "planet_dead",
]
AT_ZERO_RE = re.compile(r"\(0 blocks? away\)")
SHIFTS = range(-2, 3)


def biome_at(rcon: Rcon, x: int, y: int, z: int) -> str | None:
    """Which planet biome owns (x,y,z)? `locate biome` reporting "0 blocks away"
    means the probe point IS that biome (no chunk loading required)."""
    for b in BIOMES:
        reply = rcon.cmd(
            f"execute positioned {x} {y} {z} run locate biome isekai_verify:{b}"
        )
        if AT_ZERO_RE.search(reply):
            return b
    return None


def find_planet_per_biome(rcon: Rcon, spheres: list[Sphere]) -> dict[str, Sphere]:
    """One concrete planet instance per biome, searched over periodic images.

    Needed because the biome hash is an XZ noise independent of the planet grid,
    so a biome is not tied to any one layer — the only way to prove that e.g.
    planet_volcanic (blackstone filler) really produces ore is to find a
    volcanic planet and dig it.
    """
    found: dict[str, Sphere] = {}
    for s in spheres:
        shifts = SHIFTS if s.period is not None else (0,)
        for sx in shifts:
            for sz in shifts:
                if len(found) == len(BIOMES):
                    return found
                x = int(s.cx + sx * (s.period or 0.0))
                z = int(s.cz + sz * (s.period or 0.0))
                b = biome_at(rcon, x, int(s.cy), z)
                if b and b not in found:
                    found[b] = Sphere(f"{b}@{x},{int(s.cy)},{z}", x, s.cy, z, s.r, None)
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25599)
    ap.add_argument("--password", default="isekaiverify")
    ap.add_argument("--half-width", type=int, default=8)
    ap.add_argument(
        "--per-biome",
        action="store_true",
        help="probe one planet of each of the 10 biomes instead of "
        "one planet of each layer",
    )
    args = ap.parse_args()

    spheres = load_spheres()
    rcon = Rcon(args.host, args.port, args.password)
    violations: list[str] = []

    if args.per_biome:
        found = find_planet_per_biome(rcon, spheres)
        missing = [b for b in BIOMES if b not in found]
        if missing:
            violations.append(f"no planet instance found for: {', '.join(missing)}")
        spheres = [found[b] for b in BIOMES if b in found]

    for s in spheres:
        counts = probe(rcon, s, args.half_width)
        print(f"\n{s.name}  r={s.r:.0f}  center=({s.cx:.0f}, {s.cy:.0f}, {s.cz:.0f})")
        header = "  slab    " + "".join(f"{o:>10}" for o in ORE_BLOCKS)
        print(header)
        for slab in SLABS:
            print(
                f"  {slab:<8}" + "".join(f"{counts[slab][o]:>10}" for o in ORE_BLOCKS)
            )

        upper_shallow = counts["upper"]["coal"] + counts["upper"]["copper"]
        iron_any = sum(counts[sl]["iron"] for sl in SLABS)
        lower_deep = counts["lower"]["diamond"] + counts["lower"]["lapis"]
        if upper_shallow == 0:
            violations.append(f"{s.name}: no coal/copper in the upper slab")
        if iron_any == 0:
            violations.append(f"{s.name}: no iron anywhere")
        if lower_deep == 0:
            violations.append(f"{s.name}: no diamond/lapis in the lower slab")

    rcon.close()
    print()
    if violations:
        print(f"FAILED: {len(violations)} violation(s)")
        for v in violations:
            print(f"  - {v}")
        return 1
    print("OK: every planet has shallow ore up top, iron, and deep ore at the core")
    return 0


if __name__ == "__main__":
    sys.exit(main())
