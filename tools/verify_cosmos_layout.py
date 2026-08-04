#!/usr/bin/env python3
"""Cosmos planet-field layout regression check (isekai_verify dev fixture only).

Parses the live density function tree at
`data/isekai_verify/worldgen/density_function/planet_field.json` (a nested
`isekai_api:max` of spheres, each `isekai_api:add(constant r, negate(distance(...)))`,
optionally wrapped in `isekai_api:repeat(period_x, period_z, f=...)`) and checks,
against the *actual JSON values* (not a hand-copied table, so this fails the moment
the JSON drifts from what was verified):

  1. Tile-boundary safety: every tiled sphere's center, reduced mod its repeat
     period, stays at least (radius + BOUNDARY_MARGIN) away from every tile edge.
     This is the fix for the "vertical flat cutoff" bug — `isekai_api:repeat`
     tiles by folding the query coordinate into a canonical [0, period) cell
     before evaluating `f`; a sphere whose true (untiled) extent crosses a
     multiple of the period gets its far side evaluated against a wildly
     different effective coordinate and is clipped by a flat seam plane.
  2. Pairwise surface-to-surface separation (3D, `mode: xyz`) is at least
     MIN_SURFACE_GAP for every pair of spheres, searching periodic images over
     a shift window wide enough that further shifts can only be farther away.
  3. Every sphere's Y extent stays within the overworld's generated Y range.
  4. Visibility: every sphere has at least MIN_VISIBLE_NEIGHBORS other spheres
     (any periodic image) within MAX_VISIBLE_GAP surface-to-surface distance,
     so the sky is never empty at typical render distance.
  5. XZ column non-occlusion: `isekai_api:world_floor` walks a column straight
     down and only ever returns the *topmost* object's floor, so any pair of
     spheres whose XZ (not 3D) disks overlap will have the lower sphere's core
     ore silently fail to place in the overlap footprint — the 3D spacing
     check in (2) does not catch this because two spheres can be far apart in
     Y (and thus pass the 3D surface-gap check) while still overlapping when
     projected onto the XZ plane. This check requires every pair (including a
     sphere against its own periodic images, so a future radius bump past
     period/2 gets caught too) to have non-negative XZ surface-to-surface gap
     over the same periodic-image search as (2).

Exit 0 with "OK" when every check passes; exit 1 listing every violation
otherwise.

Run: python tools/verify_cosmos_layout.py
"""

from __future__ import annotations

import itertools
import json
import math
import pathlib
import sys
from dataclasses import dataclass

FIELD_PATH = (
    pathlib.Path(__file__).resolve().parent.parent
    / "src/main/resources/data/isekai_verify/worldgen/density_function/planet_field.json"
)

BOUNDARY_MARGIN = 8.0
MIN_SURFACE_GAP = 100.0
Y_MIN, Y_MAX = -64.0, 320.0
MAX_VISIBLE_GAP = 220.0
MIN_VISIBLE_NEIGHBORS = 2
SHIFT_RANGE = range(-2, 3)  # -2..2 inclusive; safe for period >> 2*(max radius + gap)


@dataclass
class Sphere:
    name: str
    cx: float
    cy: float
    cz: float
    r: float
    period: float | None  # None => not tiled (e.g. the home planet)


def _walk(node: dict, path: str, period: float | None, out: list[Sphere]) -> None:
    node_type = node["type"]
    if node_type == "isekai_api:max":
        _walk(node["a"], path + ".a", period, out)
        _walk(node["b"], path + ".b", period, out)
        return
    if node_type == "isekai_api:repeat":
        px, pz = node["period_x"], node["period_z"]
        if px != pz:
            raise ValueError(
                f"{path}: period_x != period_z ({px} vs {pz}), unsupported by this checker"
            )
        _walk(node["f"], path + ".f", px, out)
        return
    if node_type == "isekai_api:add":
        const_node, other = node["a"], node["b"]
        if const_node["type"] != "isekai_api:constant":
            const_node, other = node["b"], node["a"]
        if (
            const_node["type"] != "isekai_api:constant"
            or other["type"] != "isekai_api:negate"
        ):
            raise ValueError(
                f"{path}: expected add(constant, negate(distance)) sphere shape"
            )
        dist_node = other["f"]
        if dist_node["type"] != "isekai_api:distance":
            raise ValueError(f"{path}: expected negate(distance(...))")
        out.append(
            Sphere(
                name=path,
                cx=dist_node["ref_x"],
                cy=dist_node["ref_y"],
                cz=dist_node["ref_z"],
                r=const_node["value"],
                period=period,
            )
        )
        return
    raise ValueError(f"{path}: unrecognized node type {node_type!r}")


def load_spheres(field_path: pathlib.Path = FIELD_PATH) -> list[Sphere]:
    data = json.loads(field_path.read_text(encoding="utf-8"))
    spheres: list[Sphere] = []
    _walk(data, "root", None, spheres)
    return spheres


def min_surface_gap(a: Sphere, b: Sphere) -> float:
    """Minimum surface-to-surface distance between a and b over all periodic
    images (a body with period=None only exists at its literal position)."""
    shifts_a = SHIFT_RANGE if a.period is not None else (0,)
    shifts_b = SHIFT_RANGE if b.period is not None else (0,)
    best = math.inf
    for sxa, sza in itertools.product(shifts_a, shifts_a):
        ax = a.cx + sxa * (a.period or 0.0)
        az = a.cz + sza * (a.period or 0.0)
        for sxb, szb in itertools.product(shifts_b, shifts_b):
            if a is b and (sxa, sza) == (sxb, szb):
                continue
            bx = b.cx + sxb * (b.period or 0.0)
            bz = b.cz + szb * (b.period or 0.0)
            d = math.sqrt((ax - bx) ** 2 + (a.cy - b.cy) ** 2 + (az - bz) ** 2)
            gap = d - a.r - b.r
            if gap < best:
                best = gap
    return best


def min_xz_surface_gap(a: Sphere, b: Sphere) -> float:
    """Minimum XZ-only surface-to-surface distance between a and b over all
    periodic images (including a vs its own other periodic images when
    a is b). This is the quantity `isekai_api:world_floor` column occlusion
    actually cares about: two spheres can be arbitrarily far apart in Y (and
    so pass min_surface_gap's 3D check) while still overlapping when the
    column is collapsed onto the XZ plane, which silently drops the lower
    sphere's floor-anchored ore for every column in the overlap."""
    shifts_a = SHIFT_RANGE if a.period is not None else (0,)
    shifts_b = SHIFT_RANGE if b.period is not None else (0,)
    best = math.inf
    for sxa, sza in itertools.product(shifts_a, shifts_a):
        ax = a.cx + sxa * (a.period or 0.0)
        az = a.cz + sza * (a.period or 0.0)
        for sxb, szb in itertools.product(shifts_b, shifts_b):
            if a is b and (sxa, sza) == (sxb, szb):
                continue
            bx = b.cx + sxb * (b.period or 0.0)
            bz = b.cz + szb * (b.period or 0.0)
            d = math.hypot(ax - bx, az - bz)
            gap = d - a.r - b.r
            if gap < best:
                best = gap
    return best


def check_boundary(sphere: Sphere) -> str | None:
    if sphere.period is None:
        return None
    lo = sphere.r + BOUNDARY_MARGIN
    hi = sphere.period - (sphere.r + BOUNDARY_MARGIN)
    cxm = sphere.cx % sphere.period
    czm = sphere.cz % sphere.period
    if not (lo <= cxm <= hi):
        return f"{sphere.name}: cx%period={cxm:.2f} outside safe [{lo:.2f},{hi:.2f}]"
    if not (lo <= czm <= hi):
        return f"{sphere.name}: cz%period={czm:.2f} outside safe [{lo:.2f},{hi:.2f}]"
    return None


def check_y_extent(sphere: Sphere) -> str | None:
    lo, hi = sphere.cy - sphere.r, sphere.cy + sphere.r
    if lo < Y_MIN or hi > Y_MAX:
        return f"{sphere.name}: y-extent [{lo:.1f},{hi:.1f}] breaks bounds [{Y_MIN},{Y_MAX}]"
    return None


def main() -> int:
    spheres = load_spheres()
    violations: list[str] = []

    for s in spheres:
        msg = check_boundary(s)
        if msg:
            violations.append("BOUNDARY: " + msg)
        msg = check_y_extent(s)
        if msg:
            violations.append("Y-RANGE: " + msg)

    for a, b in itertools.combinations(spheres, 2):
        gap = min_surface_gap(a, b)
        if gap < MIN_SURFACE_GAP:
            violations.append(
                f"SPACING: {a.name} - {b.name}: gap={gap:.2f} < {MIN_SURFACE_GAP}"
            )

    for a, b in itertools.combinations(spheres, 2):
        xz_gap = min_xz_surface_gap(a, b)
        if xz_gap < 0:
            violations.append(
                f"XZ-OCCLUSION: {a.name} - {b.name}: xz_gap={xz_gap:.2f} < 0 "
                "(world_floor column occlusion — lower sphere loses its core ore "
                "in the overlap footprint)"
            )
    for a in spheres:
        if a.period is None:
            continue
        xz_gap = min_xz_surface_gap(a, a)
        if xz_gap < 0:
            violations.append(
                f"XZ-OCCLUSION: {a.name} vs its own periodic image: "
                f"xz_gap={xz_gap:.2f} < 0 (radius >= period/2)"
            )

    for a in spheres:
        n = sum(
            1
            for b in spheres
            if b is not a and min_surface_gap(a, b) <= MAX_VISIBLE_GAP
        )
        if n < MIN_VISIBLE_NEIGHBORS:
            violations.append(
                f"VISIBILITY: {a.name}: only {n} neighbor(s) within {MAX_VISIBLE_GAP} "
                f"(need >= {MIN_VISIBLE_NEIGHBORS})"
            )

    print(f"Parsed {len(spheres)} sphere(s) from {FIELD_PATH}:")
    for s in spheres:
        tiled = f"period={s.period:.0f}" if s.period is not None else "not tiled"
        print(
            f"  {s.name:60s} r={s.r:5.1f} center=({s.cx:.1f}, {s.cy:.1f}, {s.cz:.1f}) {tiled}"
        )
    print()

    if violations:
        print(f"FAILED: {len(violations)} violation(s)")
        for v in violations:
            print(f"  - {v}")
        return 1

    print("OK: no boundary, spacing, Y-range, visibility, or XZ-occlusion violations")
    return 0


if __name__ == "__main__":
    sys.exit(main())
