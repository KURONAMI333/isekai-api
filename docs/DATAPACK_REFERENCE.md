# Isekai API — Datapack Reference

Every Isekai key you can write in datapack JSON, in one place. No Java required.

> Editing `isekai/worldshape/*.json` or `isekai/layered_worldshape/*.json`? Wire up the
> [JSON Schemas](schema/README.md) for completion and edit-time validation.

Every Isekai identifier uses the **`isekai_api:`** namespace and names a registry-backed *type*:

- **Worldgen types** — used as a `"type"` value where Minecraft expects a density function, biome source, surface rule, placement modifier, biome modifier, or structure modifier.
- **Extension-point types** (`SpatialPredicate`, `RemapStrategy`, `SurfaceAnchor`, `TransitionRule`, `BiomeZone`) — used as the `"type"` value nested inside a worldshape descriptor or a rule biome source. Each dispatches through its own Isekai custom registry (`IsekaiRegistries`), so third-party mods can register additional variants.

> The legacy `isekai:` prefix (used by older packs for the extension-point types) is accepted as a deprecated alias and logs a one-time warning per id. Use `isekai_api:` in new packs.

Where each goes:

| You're editing… | Use |
|---|---|
| `data/minecraft/worldgen/noise_settings/<dim>.json` → `noise_router.final_density` | density functions |
| `…noise_settings/<dim>.json` → `surface_rule` | surface rules |
| `data/<ns>/dimension/<name>.json` → `generator.biome_source` | biome source |
| `data/<ns>/worldgen/placed_feature/*.json` → placement list | placement modifiers |
| `data/<ns>/neoforge/biome_modifier/*.json` | biome modifiers |
| `data/<ns>/neoforge/structure_modifier/*.json` | structure modifiers |
| `data/<ns>/isekai/worldshape/*.json` | the worldshape descriptor (uses the `isekai_api:` dispatch payloads) |

---

## Density functions (`isekai_api:`)

Drop these anywhere a vanilla density function is expected. Compose them with each other and with `minecraft:` density functions freely.

### Primitives

| Type | Fields | Result |
|---|---|---|
| `isekai_api:constant` | `value` (double) | constant value |
| `isekai_api:coordinate` | `axis` (`x`\|`y`\|`z`) | the raw block coordinate on that axis |
| `isekai_api:add` | `a`, `b` (density) | a + b |
| `isekai_api:multiply` | `a`, `b` (density) | a × b |
| `isekai_api:negate` | `f` (density) | −f |
| `isekai_api:abs` | `f` (density) | \|f\| |
| `isekai_api:clamp` | `f` (density), `min`, `max` (double) | f clamped to [min, max] |
| `isekai_api:min` | `a`, `b` (density) | min(a, b) |
| `isekai_api:max` | `a`, `b` (density) | max(a, b) |
| `isekai_api:lerp` | `t`, `a`, `b` (density) | linear blend a→b by t |
| `isekai_api:step` | `value`, `threshold`, `low`, `high` (density) | `value ≥ threshold ? high : low` |
| `isekai_api:distance` | `ref_x`, `ref_y`, `ref_z` (double), `mode` (`xz`\|`xyz`) | distance from the reference point |
| `isekai_api:translate` | `f` (density), `dx`, `dy`, `dz` (double) | f sampled at a shifted position |
| `isekai_api:scale_coord` | `f` (density), `sx`, `sy`, `sz` (double) | f sampled at a scaled position (negative = mirror axis) |
| `isekai_api:repeat` | `f` (density), `period_x`, `period_z` (double) | f tiled on the XZ plane |
| `isekai_api:mask_y_range` | `ymin`, `ymax` (int), `inside`, `outside` (density) | `inside` within the Y band, else `outside` |
| `isekai_api:quarter_negative` | `argument` (density) | `v > 0 ? v : v × 0.25` — vanilla's `quarter_negative` re-exposed (the underlying `Mapped.Type` enum is package-private). Used inside the proven non-terraced terrain formula `add(mul(4, quarter_negative(mul(depth, factor))), base_3d_noise)`. |

### Worldshape composers

Theme-neutral building blocks for Y-banded terrain (floating islands, hanging continents, hollow shells…).

| Type | Fields | Result |
|---|---|---|
| `isekai_api:squeeze` | `argument` (density) | vanilla tone-mapping `x/2 − x³/24` clamped to [−1, 1] |
| `isekai_api:y_envelope` | `active_min_y`, `active_max_y` (int), `gradient_width` (int, default 30), `invert` (bool, default false) | 1 inside the Y band, 0 outside, linear ramp through `gradient_width`; `invert` flips polarity |
| `isekai_api:blended_noise` | `size_xz`, `size_y` (1–1000), `smear_multiplier` (1–8, default 8) | `old_blended_noise` wrapper (xz/y scale fixed at 0.25); bigger size = larger features |
| `isekai_api:band_density` | `active_min_y`, `active_max_y` (int), `gradient_width` (int ≥ 1, default 30), `invert` (bool, default false), `solidity_bias` (−1…1, default 0), `noise` (density) | full Aether-style band terrain: noise visible only inside the Y band. `invert` hangs terrain from the top; `solidity_bias` shifts island↔continent |
| `isekai_api:sloped_density` | `depth_field` (density), `factor` (0.5–6, default 4), `base_noise` (density, defaults to a {320,240,8} `blended_noise`) | emits `add(mul(4, quarter_negative(mul(depth_field, factor))), base_noise)` — vanilla's `sloped_cheese` shape. Use this **wherever you'd hand-write a surface terrain density**: it encapsulates the 4× post-amplifier, `quarter_negative` machinery, and the full-weight `base_noise` requirement, so you can't accidentally introduce the voxel-staircase or cliff-coast traps. Consumer holds all themes in `depth_field`. Pair with `aquifers_enabled: false` for clean ocean coasts. |

A standard floating-island `final_density` wraps `band_density` in `blend_density` + `interpolated`, then `squeeze`:

```jsonc
"final_density": {
  "type": "isekai_api:squeeze",
  "argument": {
    "type": "minecraft:interpolated",
    "argument": {
      "type": "minecraft:blend_density",
      "argument": {
        "type": "isekai_api:band_density",
        "active_min_y": 50, "active_max_y": 200, "gradient_width": 30,
        "noise": { "type": "isekai_api:blended_noise", "size_xz": 320, "size_y": 240 }
      }
    }
  }
}
```

**Two traps.** `band_density`'s outside is a fixed, shallow negative constant — do not assume it stays below the solid threshold once other densities are layered on top. Pin the band with `isekai_api:mask_y_range` (`ymin`/`ymax`, both inclusive; `outside` = a `minecraft:constant` negative value such as `-1.0`), then wrap that in `isekai_api:squeeze` / `minecraft:blend_density` / `minecraft:interpolated`. Air below `sea_level` also becomes water even with `aquifers_enabled: false` (`Aquifer.createDisabled` returns water for `y < sea_level`), so lower `sea_level` to the band's floor (e.g. `noise.min_y`) when the void must stay dry.

---

## Biome sources (`isekai_api:`)

Three biome sources for different placement strategies — pick whichever fits your world. All go in a dimension's `generator.biome_source`.

### `isekai_api:rule` — by spatial rule

Places biomes by `BiomeZone` rules evaluated in declaration order; first match wins, else `fallback`.

```jsonc
{
  "type": "isekai_api:rule",
  "fallback": "minecraft:plains",
  "rules": [
    { "zone": { "type": "isekai_api:y_below", "y": 20 }, "biome": "minecraft:deep_dark" },
    { "zone": { "type": "isekai_api:within_distance", "radius": 1000 }, "biome": "minecraft:desert" }
  ]
}
```

### `isekai_api:climate_zones` — by climate axes

Places biomes by matching the vanilla climate axes (temperature / humidity / continentalness / erosion / weirdness / depth) against per-rule range constraints. Same purpose as vanilla `minecraft:multi_noise` but compact: each rule lists only the axes it constrains (omitted = no constraint), and order is explicit instead of vanilla's nearest-point matching.

```jsonc
{
  "type": "isekai_api:climate_zones",
  "fallback": "minecraft:plains",
  "rules": [
    { "biome": "minecraft:warm_ocean", "continentalness": [-1.0, 0.05] },
    { "biome": "minecraft:desert",     "temperature": [0.55, 1.0], "humidity": [-1.0, -0.1] },
    { "biome": "minecraft:jungle",     "temperature": [0.55, 1.0], "humidity": [0.1, 1.0] },
    { "biome": "minecraft:plains" }
  ]
}
```

Ranges use the vanilla `Climate.Parameter` codec — `[min, max]` or a single value (treated as a point on that axis).

### `isekai_api:override` — replace a footprint, keep the base

Wraps another biome source and replaces the biome inside zone footprints. Every position no zone claims is answered by `base`, so the base's own rules, seed and climate sampling keep working; this is how you add an island or a single-biome region to otherwise vanilla terrain.

A complete `data/minecraft/dimension/overworld.json` that keeps the vanilla overworld and pins the 7×7 chunks around the origin to cherry grove, inside a 513×513-block jungle square:

```jsonc
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "minecraft:noise",
    "settings": "minecraft:overworld",
    "biome_source": {
      "type": "isekai_api:override",
      "base": { "type": "minecraft:multi_noise", "preset": "minecraft:overworld" },
      "overrides": [
        { "zone": { "type": "isekai_api:chunk_box", "min_chunk_x": -3, "max_chunk_x": 3, "min_chunk_z": -3, "max_chunk_z": 3 },
          "biome": "minecraft:cherry_grove" },
        { "zone": { "type": "isekai_api:box", "min_x": -256, "max_x": 256, "min_z": -256, "max_z": 256 },
          "biome": "minecraft:jungle" }
      ]
    }
  }
}
```

- **Order.** Overrides are evaluated top to bottom and the first match wins, so the smaller footprint goes first. In the example above, swapping the two entries would hide the cherry grove under the jungle square.
- **What changes.** The biome is replaced before terrain is built, so the patch gets that biome's surface blocks, vegetation, feature and structure eligibility, spawns, and sky and fog colour. The terrain *shape* still comes from the base generator's climate noise: a patch over an ocean stays ocean-shaped.
- **Which chunks.** Only chunks generated after the pack is active. A datapack's `dimension/` entry takes precedence over the dimension saved in `level.dat`, so adding the pack to an existing world works for new chunks, but worldgen registries load only at world start: restart the world (or server) after adding or editing the pack; `/reload` is not enough. Use vanilla `/fillbiome` to retag chunks that already exist.
- **The world-settings warning.** Vanilla marks the nether and end as "experimental" when their biome source is not the stock one, so wrapping those shows the experimental-settings prompt on load. The overworld check does not look at a non-`multi_noise` source, so wrapping the overworld does not trigger it.
- **F3.** The debug screen shows the wrapped source's lines (for `multi_noise`, its climate values).

### BiomeZone (`isekai_api:`) — used inside `rule.zone` and `override.overrides[].zone`

Coordinates are authored in **block** space. Evaluated at biome-grid resolution (one sample per 4 blocks), coordinates only — no terrain context.

| Type | Fields | Matches |
|---|---|---|
| `isekai_api:always` | — | everywhere (catch-all) |
| `isekai_api:y_above` | `y` (int) | block Y ≥ y |
| `isekai_api:y_below` | `y` (int) | block Y < y |
| `isekai_api:y_between` | `min`, `max` (int, min < max) | min ≤ block Y < max |
| `isekai_api:within_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance from center ≤ radius |
| `isekai_api:beyond_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance > radius |
| `isekai_api:box` | `min_x`, `max_x`, `min_z`, `max_z` (int), `min_y` / `max_y` (int, optional) | block X/Z inside the inclusive box; Y inside `min_y..max_y` when given, unbounded when omitted. Tested per 4-block biome cell at the cell's lowest corner, so a bound that is not a multiple of 4 snaps to the cell grid; use `chunk_box` or multiples of 4 for exact edges |
| `isekai_api:chunk_box` | `min_chunk_x`, `max_chunk_x`, `min_chunk_z`, `max_chunk_z` (int) | chunk X/Z inside the inclusive range, full height |
| `isekai_api:and` | `all` (list of zones) | all match |
| `isekai_api:or` | `any` (list of zones) | any match |
| `isekai_api:not` | `inner` (zone) | inner does not match |
| `isekai_api:noise_threshold` | `noise` (noise key/inline), `seed` (long, default 0), `threshold` (double, default 0), `size_xz` / `size_y` (double, default 64) | true where a noise sample exceeds `threshold` — organic biome masks |
| `isekai_api:edge_jitter` | `inner` (zone), `noise` (noise key/inline), `seed` (long, default 0), `strength` (double 0–32, default 4), `size_xz` (double, default 32) | wraps `inner`, perturbs the test coordinate by a small noise offset before delegating — turns geometric borders into wavy organic ones |

#### What `seed` means on the two noise zones

`seed` is **not** the seed the noise is sampled with. It is combined with the **world seed** to
produce that seed, so:

- the same datapack draws a different pattern in every world — two players who both install your
  pack do not get the same continents;
- the same world seed always redraws the same pattern, so worlds stay reproducible and
  re-generating a chunk is stable;
- two zones in one pack with different `seed` values stay independent of each other, which is what
  the field is for. Give each zone that must not mirror another its own value.

Leaving `seed` off (default `0`) is fine — the world seed alone already separates worlds. Set it
only to keep sibling zones apart.

Because the world seed now participates, a pack carrying these zones produces a **different
layout than it did under 2.0.0** for the same world seed. Nothing needs rewriting; the JSON is
unchanged and only the pattern moves.

Fixed, seed-independent geometry is still available — that is what every other zone type in the
table above is. If a feature must sit at the same coordinates in every world, express it with
`within_distance` / `y_between` / … rather than a noise zone.

---

## Surface rules (`isekai_api:`)

Go in `noise_settings.surface_rule` (inside a `minecraft:sequence`). Both read per-biome blocks from the active worldshape's `content_overrides.block_overrides` for the named dimension.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:worldshape_surface_top` | `dimension` (dimension key) | replaces the top block of matched biomes (`block_overrides.surface_top`). Self-gates to the topmost surface block, so prepend it **bare and first** — no `stone_depth` wrapper needed. |
| `isekai_api:worldshape_default_block` | `dimension` (dimension key) | replaces the default (stone) fill of matched biomes (`block_overrides.default_block`). Put it **last** (after vanilla rules). |
| `isekai_api:strata` | `bands` (list of `{ block: BlockState, thickness: int ≥ 1 }`) | ordered downward stack — band 1 covers depths 0…t1−1, band 2 covers t1…t1+t2−1, etc. Emits null below the last band so the surrounding sequence handles deeper fill. Collapses an N-layer nested `stone_depth` sequence into one flat list. |
| `isekai_api:vanilla_overworld_surface` | — | the entire vanilla overworld surface (grass/dirt/sand/badlands bands/snow caps/…), so a noise_settings can write `{ "type": "isekai_api:vanilla_overworld_surface" }` instead of copying the expanded ~30 KB `surface_rule`. Reconstructed from runtime worldgen factories, so it is safe even inside a replaced `minecraft:overworld`. Wrap it in a `minecraft:sequence` with `worldshape_surface_top` first and `worldshape_default_block` last to layer worldshape block overrides on top. |
| `isekai_api:vanilla_nether_surface` | — | the entire vanilla nether surface: bedrock floor/roof (with roof holes), netherrack, basalt deltas, soul sand valley, lava sea, warped/crimson nylium, soul sand and gravel patches. The `hooked_nether` preset already wires it. |

---

## Placement modifiers (`isekai_api:`)

Go in a placed feature's modifier list.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:surface_relative` | `anchor` (SurfaceAnchor), `offset` (IntProvider) | place at `anchor` Y + offset |
| `isekai_api:fluid_relative` | `anchor` (`fluid_top` \| `fluid_bottom`), `offset` (IntProvider) | place relative to the water column top/bottom + offset |
| `isekai_api:in_block_context` | `match_blocks` (block / list / `#tag`), `exclude_in_fluid` (bool, default false), `require_air_above` (int, default 0) | place only where the block context matches |
| `isekai_api:spatial_predicate` | `predicate` (SpatialPredicate) | place only where the predicate holds |
| `isekai_api:scatter` | `count` (IntProvider), `radius` (int 1–32, default 8), `min_spacing` (int 0–32, default 0), `max_attempts_multiplier` (int 1–8, default 3) | jitter the input into `count` XZ samples within `radius`; if `min_spacing > 0`, reject samples within `min_spacing` blocks of an already-accepted one. Pair with a heightmap/Y-anchor modifier downstream. Use over `count + in_square` whenever you want clustered features that don't stack on each other. |
| `isekai_api:fluid_edge` | `fluid` (fluid id / list / `#tag`), `max_distance` (int 1–16, default 4), `mode` (`near`\|`far`, default `near`), `vertical_range` (`below` / `above`, int 0–64 each, default 0/0) | accept positions where a matching fluid is (`near`) or isn't (`far`) within `max_distance` blocks in XZ. `vertical_range` widens the search to the band `Y-below … Y+above`; both ends are inclusive, so `{"below": 6}` finds water exactly 6 levels down and `{"below": 5}` does not. Omitted, only the input Y is read, as before. Geometric distance filter — pure membership test, no theme. |
| `isekai_api:column_relative` | `top` / `bottom` (SurfaceAnchor, default `world_surface` / `world_floor`), `from_depth` / `to_depth` (double), `scale` (`blocks`\|`proportional`, default `blocks`), `reference_thickness` (int 1–4096, default 128), `distribution` (HeightDistribution, default `uniform`) | place at a **depth into the column's own terrain** instead of an absolute Y. `0.0` is the free space above the body, `1.0` the free space below it. `blocks` measures a fixed block distance from whichever end the band is nearer to (so an ore keeps its distance from the surface whatever the body's size); `proportional` measures a fraction of the body's own thickness (so the layout stretches with it). A column that holds several separate bodies gets one placement in each of them, at the same depth. Normally emitted by `isekai_api:column_local` rather than written by hand. |
| `isekai_api:slope_filter` | `min_slope` / `max_slope` (double 0–1, defaults 0/1), `sample_radius` (int 1–8, default 2), `heightmap` (Heightmap type, default `WORLD_SURFACE_WG`) | accept positions where the local heightmap slope (max neighbour-height-delta over `sample_radius`, normalised) falls within `[min_slope, max_slope]`. 0 = flat, 1 ≈ 45°+ cliff. |
| `isekai_api:dimension_guard` | `dimension` (any dimension id — vanilla or modded) | place only in that dimension. Prepended automatically to every feature `apply_worldshape` injects; write it by hand when a placed feature is shared across dimensions and must not run in one of them. |
| `isekai_api:biome_falloff` | `biome` (biome id / list / `#tag`), `max_distance` (int 4–128), `full_distance` (int 0–`max_distance`, default 0), `exponent` (float 0.25–8, default 1.0), `vertical_range` (`below` / `above`, int 0–64 each, default 0/0), `step` (`4` \| `8` \| `16`, default 4) | accept with probability `t^exponent`, `t = (max_distance - d) / (max_distance - full_distance)`, where `d` is the horizontal distance to the nearest cell of `biome`: always inside `full_distance`, never past `max_distance`. Density thins toward the border instead of stopping at a line. `full_distance = max_distance` gives a hard radius. |

`fluid_edge` reads each column of the band from that column's `MOTION_BLOCKING` height downwards, so a fluid under a roof (a pond below a canopy, a flooded cave) is read level by level instead of being hidden by its column top. Nothing to configure: the heightmap is the ceiling of the scan, and vanilla keeps it current for blocks written during generation and in a live level alike.

(`IntProvider` = a vanilla int provider: a bare int like `5`, or `{"type":"minecraft:uniform","min_inclusive":0,"max_inclusive":8}` — flat fields, no `value:` wrapper in 1.21.1.)

### `biome_falloff` — thinning out away from a biome

```json
{"type":"isekai_api:biome_falloff", "biome":"minecraft:crimson_forest",
 "max_distance":48, "full_distance":8, "exponent":1.5,
 "vertical_range":{"below":24,"above":8}, "step":4}
```

A position's acceptance rate depends only on its horizontal distance `d` to the nearest cell of
`biome`:

| `d` | probability |
|---|---|
| `d <= full_distance` | 1 |
| `full_distance < d <= max_distance` | `t^exponent`, `t = (max_distance - d) / (max_distance - full_distance)` |
| `d > max_distance` | 0 |

`exponent` 1 is linear. Above 1 the biome's own edge stays dense and density falls away sooner —
at half the band, `exponent` 2 gives a quarter instead of a half. Below 1 a long tail keeps a
little of the biome far out. The point of the curve is the border: a hard cutoff puts one pack of
features right up to a line and nothing on the other side, and that line reads as a seam. Here the
density thins toward the border, so the seam is not visible. If you do want a hard radius, set
`full_distance` equal to `max_distance`.

`vertical_range` searches `Y-below … Y+above` at the same `(x, z)` (four blocks per step, nearest
level first) and takes any hit. Give it width in dimensions whose biome changes with altitude —
in the nether a crimson forest sits above the lava sea and a soul sand valley under it, so a
single level only ever sees the slice at the candidate's own Y. Horizontal distance is still the
only thing that sets the probability; the band only decides where the biome can be found.

The distance is never measured. One uniform draw becomes a radius
`full_distance + (max_distance - full_distance) * (1 - u^(1/exponent))`, and the search walks
outward ring by ring (Chebyshev rings of the `step` lattice, cells farther than the radius
skipped) until it hits the biome. Acceptance is `radius >= d`, which happens with probability
`t^exponent` — the table above — and a position next to the biome stops after a few samples.
Cost is biome samples per candidate position (`step` 4):

| `max_distance` | `vertical_range` | no biome in range (worst case) | average when the biome is at `d = max/2` |
|---|---|---|---|
| 32 | `{0,0}` | 197 | 36 |
| 64 | `{0,0}` | 797 | 150 |
| 128 | `{0,0}` | 3,209 | 615 |
| 32 | `{0,16}` | 985 | 176 |
| 64 | `{0,16}` | 3,985 | 747 |
| 128 | `{0,16}` | 16,045 | 3,074 |

The worst case is a position with no such biome anywhere inside the radius — every one of them is
rejected, which is what the search is paying for. `step` 8 quarters the count and `step` 16 takes
a sixteenth, at a price: the search reads only the cells on its own `step`-block grid, so a strip of
the biome narrower than `step` can fall between two reads and be missed. With `step` 8 or 16 the
"always accepted within `full_distance`" row holds only for biomes wider than the step; at `step` 4
every cell is read and it holds exactly.

Those are counts, so here is the clock. Measured on 1.21.1 against the nether's own
`MultiNoiseBiomeSource`, where one biome sample costs about 450–640 ns, with the example below
(`max_distance` 64, `full_distance` 8, `exponent` 1.6, `vertical_range` `{below:24, above:8}`),
`count` 40 per chunk and `minecraft:crimson_forest` as the target:

| where the chunk is | samples per chunk | ms per chunk |
|---|---|---|
| inside the forest | 40 | 0.03 |
| 24 blocks past the border | 14,058 | 5.2 |
| 200+ blocks away, no crimson forest in range | 76,741 | 29.2 |

The 40 candidates of one chunk ask the same biome cells over and over, so the modifier remembers
them per thread — 16,384 cells, dropped whole when the table fills or the world or sampler changes.
The same three sites then take 0.01 / 0.51 / 0.89 ms per chunk cold, and 0.01 / 0.48 / 0.39 ms once
the chunk next door has warmed the table.

Two rules follow. Put this modifier after the cheap ones — `count` first, then anything that needs
a heightmap or a block lookup — because its cost is paid per candidate that reaches it, not per
chunk. And above `max_distance` 64, consider `step` 8: 128 blocks at `step` 4 is 16,045 samples per
position against 3,985 at `step` 8, with the border landing on an 8-block grid instead of a 4-block
one.

The biome is read from the dimension's `BiomeSource`, not from the chunks around the position: a
feature can only read the 3x3 chunks it is decorating, which is smaller than the radius it has to
measure, and the source also gives the same answer in every chunk of one biome so the falloff does
not flicker along a chunk border. If the source cannot be read, the position is rejected — allowing
would place at full density everywhere, the opposite of what the pack asked for.

Example — mushroom caps floating on the nether's lava sea, thinning out as the crimson forest gets
farther away. The feature itself belongs to the pack; Isekai only supplies the placement filter.

```json
{
  "feature": "mypack:crimson_cap",
  "placement": [
    {"type": "minecraft:count", "count": 40},
    {"type": "isekai_api:scatter", "count": 1, "radius": 24},
    {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
    {"type": "isekai_api:biome_falloff",
     "biome": "minecraft:crimson_forest",
     "max_distance": 64, "full_distance": 8, "exponent": 1.6,
     "vertical_range": {"below": 24, "above": 8}}
  ]
}
```

`MOTION_BLOCKING` counts fluids, so in a lava-sea column it returns the Y just above the lava.

---

## Features (`isekai_api:`)

Geometric placement primitives — use as `type` inside a configured_feature JSON. Block / fluid choices are codec fields, no themes baked in.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:cluster` | `block` (BlockStateProvider), `size` (IntProvider 1–256), `can_replace_solid` (bool, default false) | random-walk BFS from origin, places `size` connected blocks. Use for moss patches, dirt veins, fungus spreads, ore clusters — any "blob of N connected blocks". |
| `isekai_api:pool` | `fluid` (BlockState), `rim_block` (BlockStateProvider), `xz_radius` (IntProvider 1–64, values above 14 are clamped — flat form `{"type":"minecraft:uniform","min_inclusive":3,"max_inclusive":5}`), `depth` (int 1–32, default 2), `shore_width` (int 0–6, default 0) | a bounded body of fluid dug into the terrain, using vanilla `LakeFeature`'s algorithm. Outline is the union of 4–7 random ellipsoids, so no two are alike and none is a circle. Lines the whole shell with `rim_block`, not just the floor. `shore_width` repeats that rim outward N more rings (a beach); `0` is the single-ring bank every existing datapack already has. **Refuses to place unless the terrain can contain the fluid** — see below. Avoids `waterlogged_vegetation_patch`'s grass→dirt drowning trap: the rim block is whatever you pass, never drowned grass. |

### `pool` — containment, and what it costs you

Before writing a single block, `pool` checks every cell just outside the volume it would excavate: below the waterline that cell must be solid (or already the same fluid), above it, it must not be liquid. Any violation and the feature returns without touching the world. This is what stops a pool appearing with an open edge on sloped ground — it does not appear there at all.

The practical rule: **the origin's column must be the lowest surface within `xz_radius + 1` blocks, and there must be at least `depth + 1` blocks of solid ground under it.**

| | needs |
|---|---|
| solid ground below the origin | `depth + 1` blocks. A `depth: 2` pool on a 2-block-thin plate never places, at any seed |
| flat ground around the origin | half-width `xz_radius + 1`. One block of drop anywhere inside that square refuses ~90% of seeds; a drop right at the edge of the footprint refuses all of them |

All figures below are from synthetic terrain (`PoolFeatureTest` covers the two threshold rows; the rate tables were measured the same way), so read them as orders of magnitude, not as promises about your worldgen. Rate = share of `in_square` attempts that place.

| terrain (relief peak-to-peak / hill spacing) | `xz_radius` 2 | 3 | 4 | 5 |
|---|---|---|---|---|
| flat | 100% | 100% | 100% | 100% |
| 4 blocks / 128 | 91% | 88% | 85% | 81% |
| 4 blocks / 32 | 57% | 43% | 30% | 19% |
| 8 blocks / 64 | 55% | 41% | 27% | 17% |
| 16 blocks / 64 | 18% | 8% | 4% | 2% |
| 32 blocks / 32 | 0.7% | 0.6% | 0.7% | 0.8% |

Floating islands are the case that looks hostile and is not, as long as the island is thicker than `depth + 1`. Circular island, keel 16 blocks, top domed by `cap` blocks from rim to centre; rate = share of the island's own surface columns that accept a pool:

| island radius | flat top | dome 1 | dome 2 | dome 4 |
|---|---|---|---|---|
| 8 | 31% | 17% | 6% | 0.5% |
| 16 | 62% | 43% | 34% | 17% |
| 40 | 84% | 70% | 68% | 51% |

(at `xz_radius` 3; a keel of 1–2 blocks under a `depth: 2` pool places nothing anywhere on the island.)

Two consequences for tuning. **`xz_radius` costs placements, `depth` does not** — radius 2→5 on rolling terrain drops the rate 3x, while depth 1→3 moves it under 2%. And **if you carry a `pool` placement over from before 2.1.0, raise its frequency**: on anything but flat terrain the same `rarity_filter` / `count` yields 2–6x fewer pools at `xz_radius` 3–4. Compensate by lowering the `rarity_filter` chance or raising `count` — not by growing the radius, which moves it the wrong way.

```json
{
  "type": "isekai_api:pool",
  "config": {
    "fluid": {"Name": "minecraft:water"},
    "rim_block": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:sand"}},
    "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 5},
    "depth": 2,
    "shore_width": 2
  }
}
```

`xz_radius` is an `IntProvider`, so its fields sit **inline** beside `"type"` — there is no `"value"` wrapper. Writing one fails registry load with "Not a number / No key min_inclusive".

**`shore_width` (0–6, default 0)** grows the rim outward `N` rings, so a desert pond gets a beach instead of a one-block lip. The rings are grown from the shell by adjacency, so the bank follows the basin's real outline, not a circle around the origin. It changes nothing about whether the pool places — containment is still judged on the single-cell shell — and it only replaces solid cells (below the waterline always, above it half the time, the same rule the shell uses), so it never writes into air or water. `0` is the pool that existed before the field, and the default: existing datapacks generate exactly the same blocks. The shore is subtracted from the radius, because a rim that grows past the region the feature is placed into would be cut off at the chunk border: the horizontal reach is `xz_radius + shore_width` capped at 15 blocks from the centre, so `xz_radius` alone is not the reach when a shore is set.

---

## Structures — set-pieces

A **set-piece** is a landmark whose blocks have fixed spatial relationships to each other (an oasis: pool + beach + reeds + clustered palms; a ruin; a well). There are two distinct cases, and Isekai is **not** the right tool for either of them — vanilla already is.

### Coordinated, locatable set-pieces → vanilla `minecraft:jigsaw` + a hand-authored NBT

This is the canonical path. **Do not** try to compose a coordinated landmark out of independent features (`isekai_api:assembled` or a stack of `configured_feature`s) — features each re-decide their own placement, so the pool, sand, and trees land at scattered heights on a slope and never line up. Every shipping structure mod (Structory, Incendium, DungeonsArise) bakes the whole scene into one NBT and places it with `minecraft:jigsaw`. Zero Java, and you get `/locate`, spacing, biome filtering, generation-step ordering, and a terrain beardifier for free.

The wiring (four files, all vanilla types):

```jsonc
// data/<ns>/worldgen/structure/<name>.json — the jigsaw structure
{
  "type": "minecraft:jigsaw",
  "biomes": "#<ns>:has_structure/<name>",
  "step": "surface_structures",
  "spawn_overrides": {},
  "start_pool": "<ns>:<name>",
  "size": 1,
  "start_height": { "absolute": 0 },
  "project_start_to_heightmap": "WORLD_SURFACE_WG",  // anchors NBT y=0 to the surface
  "max_distance_from_center": 80,
  "use_expansion_hack": false,
  "terrain_adaptation": "beard_thin"                  // blends the base into terrain
}
```
```jsonc
// data/<ns>/worldgen/template_pool/<name>.json — one-element pool
{
  "name": "<ns>:<name>",
  "fallback": "minecraft:empty",
  "elements": [{
    "weight": 1,
    "element": {
      "element_type": "minecraft:single_pool_element",
      "location": "<ns>:<name>",          // -> data/<ns>/structure/<name>.nbt
      "processors": "minecraft:empty",
      "projection": "rigid"
    }
  }]
}
```
```jsonc
// data/<ns>/worldgen/structure_set/<name>.json — density + /locate. Larger spacing = rarer.
{
  "structures": [{ "structure": "<ns>:<name>", "weight": 1 }],
  "placement": { "type": "minecraft:random_spread", "salt": 70032417, "spacing": 44, "separation": 18 }
}
```
```jsonc
// data/<ns>/tags/worldgen/biome/has_structure/<name>.json — where it may spawn
{ "values": ["<ns>:<my_biome>"] }
```

**NBT authoring (the part that actually decides quality):**
- The NBT lives at `data/<ns>/structure/<name>.nbt` (note: `structure`, singular, in 1.21+) — gzip-compressed, `DataVersion: 3955` for 1.21.1, with `size`/`palette`/`blocks`/`entities`.
- **Y convention:** `project_start_to_heightmap` aligns the NBT's `y=0` plane to the surface. Author so `y=0` is the base/foundation and everything rises from there; NBT coordinates cannot be negative, so a pool dug "into" the ground is modelled as a shallow basin a block or two above `y=0` with `beard_thin` blending the base. Persistent leaves (`persistent: true`) won't decay.
- **Best quality comes from building it by hand in Creative** and exporting via a structure block (`/give @s structure_block` → Save mode). A human-built oasis beats any procedurally-emitted one. A drop-in NBT swap needs no other change to the four files above.
- Verify instantly with `/place structure <ns>:<name>` (places it at you — no hunting a rare structure) and `/locate structure <ns>:<name>` (confirms rarity/placement). New structures only appear in **freshly created** worlds — a world made before the structure existed has the structure registry baked in and won't show it.

### `isekai_api:grounded_template` — same NBT, but only on flat dry land

Plain `minecraft:jigsaw` gates placement on biome alone. When a biome is assigned by climate (e.g. continentalness) its edges don't track the waterline or terrain steepness, so a jigsaw landmark spawns half-submerged in shallow sea or tilted across a cliff. `isekai_api:grounded_template` places the **same NBT** (via the vanilla template machinery, correct per-chunk clamping) but rejects positions that aren't level and clear of fluid — the two gates vanilla structures can't express in a datapack.

Use it instead of `minecraft:jigsaw` for any world where a set-piece could land on water or steep ground (archipelagos, mountains, floating islands). Everything else (the NBT, `structure_set`, biome tag, `/place`, `/locate`) is identical — only the `worldgen/structure/<name>.json` differs:

```jsonc
// data/<ns>/worldgen/structure/<name>.json
{
  "type": "isekai_api:grounded_template",
  "biomes": "#<ns>:has_structure/<name>",
  "step": "surface_structures",
  "spawn_overrides": {},
  "terrain_adaptation": "beard_thin",
  "template": "<ns>:<name>",          // -> data/<ns>/structure/<name>.nbt
  "processors": "<ns>:<list>",         // optional; omit and the NBT is placed exactly as saved
  "rotation": "none",                  // optional; none | clockwise_90 | clockwise_180 | counterclockwise_90 | random
  "mirror": "none",                    // optional; none | left_right | front_back | random
  "clearance_above_fluid": 2,          // every footprint column must rise >= this above sea level
  "max_slope": 4,                      // reject if footprint height-spread exceeds this
  "vertical_offset": -1                // template y=0 lands at (surface + this); tune sink/raise
}
```

`processors` works like the same field on a jigsaw `single_pool_element`: a processor list id
(`"<ns>:palm_crown"`) or the list written inline (`{"processors": [ ... ]}`). Omitting it is the
behaviour every older datapack already had — the template lands with the block states the NBT
saved. Attach `isekai_api:leaf_distances` here to fix a hand-authored tree in place, without
routing it through a jigsaw pool.

**Variation.** `rotation` and `mirror` default to `none`, which is the orientation an existing
datapack gets, and `random` draws once per placement attempt from the chunk's structure seed, so the
same world always gives the same result for the same chunk. `left_right` reverses the template's Z
axis and `front_back` its X axis (vanilla's names). To vary *which* template is used, write
`template` as a weighted list instead of an id:

```jsonc
"template": [
  { "template": "<ns>:oasis_large", "weight": 3 },
  { "template": "<ns>:oasis_small" }        // weight defaults to 1
]
```

**Footprint.** The gates sample the ground the piece will really cover: the extents come from the
rotated template (a quarter turn swaps X and Z) and the template is centred on the chunk middle in
every orientation. A too-aggressive `max_slope` (a large template on hilly terrain) makes it rare or
absent; loosen it or shrink the template if natural spawns dry up. `/isekai survey` reports exactly
which gate rejects how many candidate chunks, and `/isekai fit` shows one chunk's heights beside the
limits (see [A structure that never spawns](#a-structure-that-never-spawns--isekai-survey-and-isekai-fit)).

### `isekai_api:assembled` — loose scatter of features at one origin (NOT coordinated landmarks)

A `Structure` that places a list of `PlacedFeature`s at a single origin (Y snapped to the live ground height). Use it **only** when the features are genuinely independent and don't need to line up — e.g. "scatter a few boulders and bushes near this point." It has **no** `terrain_adaptation`, no relative positioning between features, and no processors, so it is the wrong tool for a coordinated landmark; use the jigsaw+NBT path above for those.

The Y it snaps to is the ground, not whatever grows on it: `WORLD_SURFACE_WG` counts leaves and logs, so the origin is walked down past air, `#minecraft:leaves`, `#minecraft:logs` and replaceable plants to the first block that is terrain. Without the walk, a neighbouring tree whose crown covered the origin column lifted the whole feature list onto the canopy — a pool dug into open air, a pad spread its sand at tree height. Fluids stop the walk, so a feature placed over water still starts at the water surface.

The feature list runs **once**, from the chunk that contains the origin. Vanilla calls a piece's `postProcess` for every chunk its bounding box overlaps (each with its own random), so without that guard a 33-block-wide assembled piece would re-place the whole list up to nine times and stack nine differently-shaped results on the same spot. Features may still write past the origin chunk (a pool whose rim reaches 10 blocks out) — that is the same neighbour write a `place` feature makes during decoration.

```jsonc
// data/<ns>/worldgen/structure/<name>.json
{
  "type": "isekai_api:assembled",
  "biomes": "#<ns>:can_have_my_scatter",
  "step": "vegetal_decoration",
  "spawn_overrides": {},
  "terrain_adaptation": "none",
  "features": ["<ns>:boulder", "<ns>:bush_patch"]
}
```
Pair with a `structure_set` (same shape as above) for density + `/locate`.

### `isekai_api:leaf_distances` — recompute leaf decay data for a pasted template

A structure processor, not a structure type. Paste a tree template whose leaves were written by an editor (or saved before the trunk existed) and the leaves carry whatever `distance` the tool left behind; if that value is `7`, a leaf that is visually part of the crown decays on the first shape update and the tree sheds. This processor walks the template's own logs and writes the distance the game would have computed: **1** for a leaf touching a `#minecraft:logs` block, **+1 per leaf step**, **7** for a leaf that cannot reach a log within six steps.

It runs in `finalizeProcessing`, so it sees the states that earlier processors in the same list produced — put it last in the list and it rewrites the final crown, not an intermediate one.

| Field | Values | Default | Meaning |
|---|---|---|---|
| `orphan_leaves` | `decay` \| `persist` | `decay` | what to do with leaves that reach no log. `decay` leaves them at distance 7 and non-persistent (vanilla removes them); `persist` marks them persistent, which is how a floating canopy with no trunk in the template survives |
| `keep_persistent` | bool | `false` | `false` resets every template leaf's `persistent` flag, so the leaf decays as the recomputed distance says; `true` keeps the flag the template wrote (a leaf that was persistent stays persistent, everything else still gets the recomputed distance) |

```jsonc
// data/<ns>/worldgen/processor_list/palm_crown.json
{
  "processors": [
    { "processor_type": "isekai_api:leaf_distances", "orphan_leaves": "decay" }
  ]
}
```

```jsonc
// data/<ns>/worldgen/template_pool/<name>/<variant>.json — the processor list is attached
// to the pool element, not to the jigsaw structure
{
  "element": {
    "element_type": "minecraft:single_pool_element",
    "location": "<ns>:<name>",
    "processors": "<ns>:palm_crown",
    "projection": "rigid"
  },
  "weight": 1
}
```

Where it runs: only where vanilla reads a processor list — a jigsaw pool element's `processors` field, `isekai_api:grounded_template`'s `processors` field, and the vanilla structures that reference one. `/place template` does not run processors, and `isekai_api:assembled` does not take a processor list, so a template placed by that one keeps the leaf data it was saved with.

**It only sees the one template it is pasting.** A trunk in another piece of the same structure is invisible to it, so put the trunk and its crown in the same template. If you split them across jigsaw pieces (a shared crown pasted over a shared trunk), the leaves reach no log at all and are treated as orphans — use `orphan_leaves: persist` for that arrangement rather than expecting the processor to find the trunk next door.

A persistent leaf never decays regardless of distance, so the two flags are independent: `keep_persistent: true` alone turns nothing into a persistent leaf, and `orphan_leaves: persist` only touches leaves the processor could not reach.

---

## Tree placers (`isekai_api:`)

Used as the `trunk_placer` / `foliage_placer` of a vanilla `minecraft:tree` configured feature — the wood/leaf blocks are the feature's own `trunk_provider` / `foliage_provider` slots, the placer only decides geometry. Compose any trunk with any foliage. Spread leaves are placed with their `LeavesBlock.DISTANCE` pinned, which keeps them through worldgen; the game recomputes the real distance from the trunk on the first tick, so a leaf more than six steps from a log still decays as a vanilla leaf does. Keep a spread crown within that reach.

### Trunk placers (`trunk_placer`)

| Type | Fields | Shape |
|---|---|---|
| `isekai_api:leaning` | `base_height`, `height_rand_a`, `height_rand_b` (int — shared); `min_height_for_leaves` (positive int, default 1); `lean_length` (IntProvider 1–16); `convert_ground` (bool, default true — false skips the dirt-under-trunk conversion so beach palms stay sandy); `tip_crown_only` (bool, default false — true returns only the bent-tip attachment so a fan crown sprays from one point); `slant_curve` (double 1.0–2.0, default 1.0 — each stalk step moves `round(slant_curve^step)` blocks sideways, so 1.0 is one block per step and 1.3 bends like a palm); `space_check` (bool, default true — an occupied block ends the lean and the crown stays on the trunk instead of floating where no log could be placed); `min_height` (int 1–16, default 4 — a tree whose trunk would be shorter places nothing) | rises straight, leans near the top, then continues as a near-horizontal stalk |
| `isekai_api:branching` | `base_height`, `height_rand_a`, `height_rand_b` (int — shared); `branch_count` (int 1–6); `branch_length` (IntProvider 1–8); `branch_start_offset_from_top` (IntProvider 0–4, default 0 — how far below the trunk top branches sprout) | straight vertical trunk plus N upward-arcing branches; each branch tip becomes its own crown site, so paired with `disc`/`sphere` you get umbrella/forked silhouettes |
| `isekai_api:curved` | `base_height`, `height_rand_a`, `height_rand_b` (int — shared); `bend` (double 0.0–2.0, default 1.0 — chance of a sideways step at the very top; at level `y` of `top` the chance is `bend * (y / top)^2`, which reaches 1 at `y / top = 1 / sqrt(bend)`); `convert_ground` (bool, default true — false keeps the sand under a beach palm); `space_check` (bool, default true — an occupied cell ends the trunk at its last placed log); `min_height` (int 1–16, default 4 — a tree whose trunk would be shorter places nothing) | one stem that bends as it climbs: every level places its vertical log, then — with the height-weighted chance — one more log on the lean side of the same level. At most one block sideways per block up, so there is never a horizontal run; bent logs take the lean axis (`x`/`z`) and vertical ones keep `y`. One crown attachment, at the top log. Horizontal travel is capped at 11 blocks so the trunk plus a full `length` 5 frond fits the 16 blocks a feature's 3×3 chunk region covers |

`leaning`'s three newer fields are additive: a datapack that omits all of them gets exactly the old tree. `slant_curve` 1.0 walks one block sideways per step; raising it lengthens each successive step, so the same `lean_length` covers more ground and the bend accelerates instead of forming an L. **The stalk's horizontal travel is capped at 12 blocks from the origin** (the near-vertical lean counts too): an exponential stride would otherwise walk hundreds of blocks and leave the 3×3 chunks a feature is placed into, so `slant_curve` 2.0 with `lean_length` 16 stops at 12 instead of 131,071. A `lean_length` longer than the budget at `slant_curve` 1.0 (13–16) is cut at the same limit. `space_check` defaults to true because the old behaviour could attach a crown to a position where the trunk log was blocked by a neighbour — a palm growing into a dense stand ended up with leaves in mid-air. Set it to false only to reproduce that old shape deliberately. `min_height` is about the trunk, not about where leaves start (`min_height_for_leaves`), so it is what makes a deliberately short palm possible: raise it above `base_height` and the tree does not generate.

`curved` is the other half of that family, for a palm whose trunk should not have an L in it at all. `leaning` rises straight and then walks sideways along one plane; `curved` drifts while it rises, so the stem is one arc and the crown sits on a leaning tip rather than at the end of a horizontal arm. The two shapes are separate types on purpose: `leaning` and `fan` shipped before this one, so their geometry stays exactly what it was and old datapacks keep their trees. **The stem's horizontal budget is 11 blocks**, one less than `leaning`'s 12: a feature may write into the 16 blocks its 3×3 chunk region covers in each direction, and the crown hangs off the far end of the drift, so the widest frond (`length` 5) has to fit inside that 16 as well (`11 + 5`). A datapack that pairs `curved` with a crown wider than five blocks past its site — a vanilla placer with a large `radius`, say — is keeping its own budget. `bend` is the only shape knob — 0.0 is a straight trunk, 1.0 steps sideways at the top and almost never at the root. The chance at level `y` of `top` is `bend * (y / top)^2`, so it reaches 1 at `y / top = 1 / sqrt(bend)`: 1.0 only at the very tip, 2.0 on almost every level of the upper third, and 1.6–2.0 all read as strongly wind-bent. Because the sideways step is written as a second log at the same height, a bent level is two blocks across; that is what keeps the stem six-connected (a log one across and one up would only touch on an edge). The trunk always returns a single crown site at its top log, so it pairs with `frond` or `fan` and needs no `tip_crown_only`.

Leaf distance counts face contact, not distance, so a crown has to touch the trunk: a crown whose lowest leaves sit a block above the top log, or beside it only diagonally, is more than six leaf steps from any log and turns to distance 7. `frond` therefore fills the `offset` lift and trims `offset + rise + length + droop` to six; a palm that loses its crown to decay is a lift or a reach past that budget, not a loading problem. A frond is one chain of leaves, so it also **stops at the first cell it cannot fill** — a log or stone in the middle of an arm ends that arm, and the lift column above the trunk stops the same way — instead of jumping the gap and leaving leaves that hang on an edge and decay on the first tick. Leaves are the exception: `#minecraft:leaves` sits inside vanilla's `replaceable_by_trees`, so a cell that already holds leaves takes the frond's leaf, and a frond running into another tree's crown (or into an arm of its own) writes over it instead of stopping.

### Foliage placers (`foliage_placer`)

Common fields on every placer: `radius` (IntProvider — vanilla, often 0 since each placer drives its own geometry), `offset` (IntProvider — vertical shift above the attachment).

| Type | Extra fields | Shape |
|---|---|---|
| `isekai_api:sphere` | `height` (int 0–16); `jitter` (float 0–1, default 0.2) | ellipsoid crown (height vs radius selects flat disc vs tall ball); edge cells dropped randomly by `jitter` |
| `isekai_api:fan` | `crown_radius` (int 1–4, default 2); `hang` (int 0–3, default 1) | compact log-adjacent core + cardinal arms reaching out `crown_radius` and drooping `hang` past the tip — palm head |
| `isekai_api:frond` | `length` (IntProvider 1–5, required — how far each frond reaches out); `droop` (IntProvider 0–3, default 0 — how many of its tip blocks fall); `rise` (int 0–2, default 1 — how many of the outward steps also climb) | eight fronds, one per cardinal **and** diagonal direction, each arching up while it extends and dropping at its tip. Lengths and droops are sampled per frond, so the eight arms differ; a diagonal frond advances one axis at a time and alternates, so every leaf shares a face with the previous one. The top view is eight-pointed, not a cross. `offset` lifts the crown above the trunk top and the lifted cells are filled with leaves, so the crown keeps face contact with the wood; the lift is capped at 2 blocks and counts against the six-step reach. An arm stops at the first cell it cannot fill (a log or a wall), because leaves past the gap would decay |
| `isekai_api:cone` | `base_radius` (int 1–8); `height` (int 1–16); `taper` (`linear`\|`concave`, default linear); `jitter` (float 0–1, default 0.15) | conical crown; concave taper = conifer/cypress silhouette |
| `isekai_api:disc` | `disc_radius` (int 1–8); `thickness` (int 1–4, default 1); `jitter` (float 0–1, default 0.2) | flat wide horizontal disc — umbrella/savanna |
| `isekai_api:weeping` | `crown_radius` (int 2–6, default 3); `crown_thickness` (int 1–3, default 2); `strand_length` (IntProvider 1–6); `strand_chance` (float 0–1, default 0.4) | stacked discs + vertical leaf strands dripping from rim cells — willow/wisteria/sadesign review |
| `isekai_api:none` | none — `radius` and `offset` are accepted and default to 0, so `{"type": "isekai_api:none"}` is enough | places no leaves at all. `minecraft:tree` always requires a foliage placer, so this is what a leafless trunk (dead, burnt, pollarded) uses; pair it with `branch_stubs` for the limbs |

Minimal palm example:

```jsonc
// data/<ns>/worldgen/configured_feature/palm.json
{
  "type": "minecraft:tree",
  "config": {
    "ignore_vines": true, "decorators": [], "force_dirt": false,
    "trunk_provider":  { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:jungle_log" } },
    "foliage_provider":{ "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:jungle_leaves" } },
    "dirt_provider":   { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:dirt" } },
    "minimum_size":    { "type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1 },
    "trunk_placer":  { "type": "isekai_api:leaning", "base_height": 6, "height_rand_a": 2, "height_rand_b": 1,
                       "lean_length": { "type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2 },
                       "convert_ground": false, "tip_crown_only": true },
    "foliage_placer":{ "type": "isekai_api:fan", "radius": 0, "offset": 1, "crown_radius": 2, "hang": 1 }
  }
}
```

Same palm with a stem that bends while it climbs, and a crown of eight fronds:

```jsonc
// data/<ns>/worldgen/configured_feature/palm_curved.json
{
  "type": "minecraft:tree",
  "config": {
    "ignore_vines": true, "decorators": [], "force_dirt": false,
    "trunk_provider":  { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:jungle_log" } },
    "foliage_provider":{ "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:jungle_leaves" } },
    "dirt_provider":   { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:dirt" } },
    "minimum_size":    { "type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1 },
    "trunk_placer":  { "type": "isekai_api:curved", "base_height": 7, "height_rand_a": 2, "height_rand_b": 1,
                       "bend": 1.2, "convert_ground": false, "min_height": 5 },
    "foliage_placer":{ "type": "isekai_api:frond", "radius": 0, "offset": 0,
                       "length": { "type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 4 },
                       "droop": { "type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 1 },
                       "rise": 1 }
  }
}
```

---

## Tree decorators (`isekai_api:`)

The `decorators` array of the same `minecraft:tree` configured feature. Placers decide where the trunk and the crown go; decorators add what grows on them. They run in array order, after the trunk and the crown are in the world, and every block they place is a `block_provider` slot, so nothing here bakes in a species.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:attach_to_logs` | `block_provider` (required); `chance` (0–1, required); `directions` (default the four horizontals); `max_per_log` (1–16, default 1) | attaches a block to an empty side of each trunk block — shelf fungus, moss, resin. A block with a facing property is turned away from the trunk |
| `isekai_api:ground_patch` | `block_provider` (required); `radius` (IntProvider 0–16, required); `chance` (0–1, default 1); `replaceable` (block tag, default `minecraft:dirt`); `mode` (`cover`\|`replace`, default `cover`) | lays a disc of blocks around the base. `cover` puts the block on top of the ground, `replace` switches the ground block itself, which is how a scorched ring or moss carpet is made. The radius is capped at 16 because that is the largest disc that stays inside the chunks a feature may write while generating (vanilla's `ChunkStatus.FEATURES` writes one chunk out); a `placement` modifier that moves the feature's origin into a neighbouring chunk can still reach past that, and the game then logs a "far chunk" warning |
| `isekai_api:hang_from_leaves` | `block_provider` (required); `tip_provider` (optional); `length` (IntProvider 1–16, required); `chance` (0–1, default 1); `only_on_rim` (bool, default false) | strands hanging from the underside of the crown. Leaf strands are pinned so they survive worldgen; like any leaf, one more than six steps from a log still decays |
| `isekai_api:snow_cap` | `layers` (IntProvider 1–8, default 1); `chance` (0–1, default 1) | snow layers on the sky-facing leaves |
| `isekai_api:branch_stubs` | `count` (IntProvider 0–16, required); `length` (IntProvider 1–8, required); `start_fraction` (0–1, default 0.4); `upward_chance` (0–1, default 0.5); `directions` (default the four horizontals); `block_provider` (optional) | short bare branches out of the trunk's sides — the leafless limbs of a dead or burnt tree. `start_fraction` keeps them in the upper part of the trunk (`0` = the whole trunk); `upward_chance` decides, per stub, whether it stays flat or steps out and up. Each block's `axis` follows the step, so a flat stub is a run of horizontal logs. Omit `block_provider` to reuse the block the tree's own lowest trunk block is made of |
| `isekai_api:broken_top` | `count` (IntProvider 1–12, required); `fill_provider` (optional); `fill_chance` (0–1, default 1) | snaps the top `count` trunk blocks off, always keeping at least one. With `fill_provider` the top is replaced by that block (turned upright) instead of air, which is how a snapped trunk keeps a charred end. In each column the fill can only sit at the bottom of the break; above an opened log everything is air. List it before `branch_stubs` and the other trunk decorators: decorators run in list order, and later ones skip logs the break has removed |

`isekai_api:branching` (above, in trunk placers) also grows branches, but it makes them part of the crown: every branch tip becomes a leaf site. A tree with no leaves needs branches and no leaf sites, which is `branch_stubs`.

`broken_top` is for trees whose height varies and should still look snapped. If every tree of the species should be the same short height, lower the trunk placer's `base_height` instead and skip the decorator.

A leafless, charred tree — vanilla `minecraft:tree`, Isekai parts only:

```jsonc
// data/<ns>/worldgen/configured_feature/snag.json
{
  "type": "minecraft:tree",
  "config": {
    "ignore_vines": true, "force_dirt": false,
    "trunk_provider": { "type": "minecraft:weighted_state_provider", "entries": [
      { "data": { "Name": "minecraft:dark_oak_log", "Properties": { "axis": "y" } }, "weight": 3 },
      { "data": { "Name": "minecraft:basalt",       "Properties": { "axis": "y" } }, "weight": 1 } ] },
    "foliage_provider": { "type": "minecraft:simple_state_provider",
                          "state": { "Name": "minecraft:dark_oak_leaves" } },
    "dirt_provider": { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:coarse_dirt" } },
    "minimum_size": { "type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1 },
    "trunk_placer": { "type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 3, "height_rand_b": 2 },
    "foliage_placer": { "type": "isekai_api:none" },
    "decorators": [
      { "type": "isekai_api:broken_top",
        "count": { "type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2 },
        "fill_provider": { "type": "minecraft:simple_state_provider",
                           "state": { "Name": "minecraft:basalt", "Properties": { "axis": "y" } } },
        "fill_chance": 0.45 },
      { "type": "isekai_api:branch_stubs",
        "count":  { "type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4 },
        "length": { "type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2 },
        "start_fraction": 0.35, "upward_chance": 0.6 },
      { "type": "isekai_api:ground_patch",
        "block_provider": { "type": "minecraft:simple_state_provider", "state": { "Name": "minecraft:coarse_dirt" } },
        "radius": { "type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3 },
        "chance": 0.8, "mode": "replace" }
    ]
  }
}
```

A datapack cannot bind a sapling to a tree — that mapping is Java (`WoodSet` + `TreeGrowers`, which takes the configured features above). The JSON is what those keys point at.

A complete species, end to end, is in [`examples/4_wood/`](../examples/4_wood/README.md): one Java class
declares the set and its generators, and the pack beside it holds the configured feature plus the
generated data and assets. Run `/isekai validate <namespace>` in that world to list what the species
is still missing.

---

## Biome / structure modifiers (`isekai_api:`) under `data/<ns>/isekai/worldshape/<name>.json`, then point the `_ref` modifiers at it by dimension. The `_ref` forms are the recommended path (no descriptor duplication, 4-line modifier files).

### Biome modifiers — `data/<ns>/neoforge/biome_modifier/*.json`

```jsonc
// reference form (recommended) — descriptor lives in isekai/worldshape/<name>.json
{ "type": "isekai_api:apply_worldshape_ref", "dimension": "minecraft:overworld" }
```

### Structure modifiers — `data/<ns>/neoforge/structure_modifier/*.json`

```jsonc
{ "type": "isekai_api:apply_worldshape_structures_ref", "dimension": "minecraft:overworld" }
```

> **Deprecated (still accepted, logs a one-time warning):** the inline forms
> `{ "type": "isekai_api:apply_worldshape", "worldshape": { …descriptor… } }` and
> `isekai_api:apply_worldshape_structures` embed the whole descriptor in the modifier file,
> duplicating it across the biome and structure modifiers. Migrate to the `_ref` forms.

---

## Extension-point payloads (`isekai_api:`)

Used inside the worldshape descriptor fields. Each dispatches on a `"type"` value through an
Isekai custom registry, so third parties can register additional variants (see `IsekaiRegistries`).
The legacy `isekai:` prefix is accepted as a deprecated alias.

### SpatialPredicate (`structure_predicates`, `default_structure_predicate`, `content_overrides.feature_predicates`, `isekai_api:spatial_predicate`)

| Type | Fields |
|---|---|
| `isekai_api:y_in_range` | `min`, `max` (int) |
| `isekai_api:solid_floor` | `min_clearance` (int) |
| `isekai_api:solid_ceiling` | `min_clearance` (int) |
| `isekai_api:terrain_slope` | `min_slope`, `max_slope` (double) |
| `isekai_api:near_block` | `targets` (block id / list / `#tag`), `max_distance` (int) |
| `isekai_api:near_biome` | `biome` (biome key), `max_distance` (int) |
| `isekai_api:in_fluid` | `fluid` (fluid key) |
| `isekai_api:always` / `isekai_api:never` | — |
| `isekai_api:and` | `all` (list) |
| `isekai_api:or` | `any` (list) |
| `isekai_api:not` | `inner` (predicate) |

`near_biome` is enforced at structure-placement time only. Inside a placed feature,
`isekai_api:spatial_predicate` cannot read a biome source, so `near_biome` lets every position
through there — it always passes. To filter feature placement by biome, use
`isekai_api:biome_falloff`, with `full_distance` equal to `max_distance` if you want a hard radius.

### RemapStrategy (`ore_strategy`, `structure_strategy`, `mob_spawn_strategy`, `mob_spawn_strategy_by_category`)

| Type | Fields |
|---|---|
| `isekai_api:identity` / `isekai_api:linear` / `isekai_api:inverted` | — |
| `isekai_api:fixed_range` | `min`, `max` (int), `dist` (HeightDistribution) |
| `isekai_api:count_scale` | `factor` (double ≥ 0) |
| `isekai_api:band_split` | `bands` (list of `{ vanilla_source: VerticalRange, target_ratio: float }`) |
| `isekai_api:column_local` | `top` / `bottom` (SurfaceAnchor, default `world_surface` / `world_floor`), `scale` (`blocks`\|`proportional`, default `blocks`), `reference_thickness` (int, default 128), `surface_y` (int, default 64), `floor_y` (int, default -64) |
| `isekai_api:pipe` | `chain` (non-empty list of strategies) |

`isekai_api:column_local` is the strategy for worldshapes whose terrain altitude varies per
column — floating islands, orbiting planets, sky continents. Every other variant produces one
absolute Y band per feature, which can be right for terrain at exactly one altitude; this one
normalizes each feature's Y to a depth (`(surface_y - y) / (surface_y - floor_y)`, clamped to
`0..1`) and resolves that depth against each column's own anchors as the feature is placed. With
the default `reference_thickness` of 128 a feature declared at vanilla Y=51 stays "13 blocks
below the surface" wherever the surface happens to be; a smaller value compresses vanilla's whole
128-block surface-to-bedrock span into a thinner body. Put it last in a `pipe` — strategies after
it can no longer affect the Y.

```json
{
  "ore_strategy": {
    "type": "isekai_api:column_local",
    "scale": "blocks",
    "reference_thickness": 48
  }
}
```

### `structure_strategy` thins, it does not multiply

`structure_strategy` accepts only `isekai_api:identity`, `isekai_api:count_scale` with a
`factor` of `0.0`–`1.0`, and `isekai_api:pipe`s of those. A factor of `0.4` keeps roughly 40% of
the structures the dimension would otherwise get:

```json
{
  "structure_strategy": { "type": "isekai_api:count_scale", "factor": 0.4 }
}
```

The thinning is a veto on placements the vanilla grid already produced, applied per chunk and
per structure type, and derived from the world seed — so it is stable across saves and chunk
regeneration, and two structure types thin independently.

A `factor` above `1.0` is a validation error rather than a silent clamp. Making a structure
*more* common needs candidate chunks that vanilla's grid never produced, and that grid is not
reachable from a worldshape: the `RandomSpreadStructurePlacement` carrying `spacing` /
`separation` is shared by every dimension referencing the same structure set, and the chunk set
derived from it is computed once per world load and cached. Editing it per-dimension would leak
into other dimensions and desynchronise the cache from the live values, which breaks seed
reproducibility. To raise a structure's frequency, override its `StructureSet` JSON in your
datapack.

The Y-band variants (`linear`, `inverted`, `fixed_range`, `band_split`, `column_local`) are
rejected here: they describe where in a column something sits, which says nothing about how
often a structure spawns. Use them in `ore_strategy` / `mob_spawn_strategy` instead.

### SurfaceAnchor (`surface_anchor`)

| Type | Fields |
|---|---|
| `isekai_api:world_surface` | — |
| `isekai_api:below_fluid` | `fluid` (fluid key) |
| `isekai_api:fixed_y` | `y` (int) |
| `isekai_api:world_floor` | `start` (SurfaceAnchor, default `world_surface`), `max_scan` (int 1–4096, default 128) |

`isekai_api:world_floor` is the mirror of `world_surface`: it scans downward from `start` and
returns the first free space *below* the terrain — the underside of a floating body. It resolves
to nothing when no body is found within `max_scan`, and equally when the body never ends, so in
solid ground-to-bedrock terrain it simply skips the placement.

Both anchors answer for the **topmost** body in the column. `isekai_api:column_relative` walks
past it to reach the ones underneath, so a column of stacked islands receives a placement in each
of them; anywhere an anchor is used on its own (`isekai_api:surface_relative`, for instance) it is
the topmost body that is meant.

```json
{
  "type": "isekai_api:surface_relative",
  "anchor": { "type": "isekai_api:world_floor" },
  "offset": { "type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 16 }
}
```

### TransitionRule (layered worldshapes)

| Type | Fields |
|---|---|
| `isekai_api:hard` | — |
| `isekai_api:blend` | `blend_height` (int ≥ 0) |
| `isekai_api:gap` | `gap_height` (int ≥ 0) |

A layer's `transition` governs the seam to the layer directly above it — the layer whose
`y_range.min_y` equals this layer's `y_range.max_y`.

- **`hard`** — butt join. Every block belongs to whichever layer's half-open
  `[min_y, max_y)` contains it.
- **`blend`** — the two descriptors interleave across a band of `blend_height` blocks centred
  on the seam. Each block in the band picks the upper or the lower descriptor, with the odds
  running from "almost always lower" at the bottom of the band to "almost always upper" at the
  top, so the seam reads as a speckled gradient instead of a flat line. What actually changes
  is the descriptor's per-position output — `content_overrides.block_overrides`
  (`surface_top`, `default_block`) and `structure_predicates`. Terrain shape comes from density
  functions and is not affected. A `blend` whose seam has no layer directly above it has
  nothing to blend into and behaves as `hard`.
- **`gap`** — the layer gives up its top `gap_height` blocks: no descriptor applies there,
  exactly as if the two `y_range`s had been authored that far apart. Use it to keep a layer's
  `y_range` stated as its nominal span while still opening space above it.

```json
{
  "layers": [
    {
      "y_range": { "min_y": -64, "max_y": 64, "distribution": "uniform" },
      "descriptor": { "…": "underground layer" },
      "transition": { "type": "isekai_api:blend", "blend_height": 8 }
    },
    {
      "y_range": { "min_y": 64, "max_y": 320, "distribution": "uniform" },
      "descriptor": { "…": "surface layer" },
      "transition": { "type": "isekai_api:hard" }
    }
  ]
}
```

The per-block choice is a pure hash of the coordinates and the seam Y — no world seed, no RNG
state — so a regenerated chunk always reproduces the original. Client fog resolves by Y alone
and therefore sees `blend` seams as `hard` (a per-position pick would make fog flicker as the
camera moves); `gap` is a pure Y interval and applies there too.

### HeightDistribution (used by `playable_range.distribution`, `fixed_range.dist`)

`uniform` \| `trapezoid` \| `triangle` \| `biased_low` \| `biased_high`

---

## Extending the SPI — register your own variant

Each of the five dispatch types (`SpatialPredicate`, `RemapStrategy`, `BiomeZone`,
`SurfaceAnchor`, `TransitionRule`) is a NeoForge custom registry of `MapCodec`s, so any mod can
add a variant from its own mod id without a fork or PR. The registry keys are in
`com.kuronami.isekaiapi.api.registry.IsekaiRegistries`. Three steps:

**1. Implement the interface.** Return your payload `MapCodec` from `codec()` and implement the
evaluation method. A leaf `SpatialPredicate` matching even-X positions:

```java
public record XParityPredicate() implements SpatialPredicate {
    public static final MapCodec<XParityPredicate> MAP_CODEC = MapCodec.unit(new XParityPredicate());

    @Override public boolean test(EvaluationContext ctx) { return (ctx.pos().getX() & 1) == 0; }
    @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
}
```

`EvaluationContext` is the world-access seam: `pos()`, `solidFloor(n)`, `inFluid(f)`,
`nearBlock(set, d)`, `nearBiome(key, d)`, `terrainSlope(min, max)`. `RemapStrategy` receives a
`RemapContext` and returns a remapped `VerticalRange`; `BiomeZone.test(x, y, z)` takes bare
coordinates; `SurfaceAnchor.resolveY(ctx, pos)` returns a Y or `null`.

**2. Register the codec** on your mod's `RegisterEvent` (MOD bus), under the matching key:

```java
@EventBusSubscriber(modid = "yourmod", bus = EventBusSubscriber.Bus.MOD)
public final class YourSpiTypes {
    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(IsekaiRegistries.SPATIAL_PREDICATE_TYPE,
                ResourceLocation.fromNamespaceAndPath("yourmod", "x_parity"),
                () -> XParityPredicate.MAP_CODEC);
    }
}
```

**3. Use it from JSON** anywhere the type is accepted — dispatched by its registered id, and
composable with the built-ins:

```json
{ "type": "isekai_api:and", "all": [
  { "type": "yourmod:x_parity" },
  { "type": "isekai_api:y_in_range", "min": 60, "max": 200 }
] }
```

That is the entire footprint — Isekai's own code holds no reference to your variant; registry
membership alone makes it decodable and evaluable.

---

## WorldshapeDescriptor (the `isekai/worldshape/*.json` body)

```jsonc
{
  "dimension": "<dimension key>",                 // required — or the object form below
  "playable_range": { "min_y": <int>, "max_y": <int>, "distribution": "<HeightDistribution>" },
  "surface_anchor": <SurfaceAnchor>,
  "ore_strategy": <RemapStrategy>,
  "structure_strategy": <RemapStrategy>,
  "mob_spawn_strategy": <RemapStrategy>,
  "default_structure_predicate": <SpatialPredicate>,
  "structure_predicates": { "<structure key>": <SpatialPredicate>, … },   // optional
  "applies_to": ["<biome key>", …]  OR  { "keys": [...], "tags": ["#minecraft:is_overworld"] },
  "exclusions": { "features": [...], "structures": [...], "carvers": [...], "mob_spawns": [...] },
  "additions": { "features": [...], "carvers": [...], "mob_spawns": [...] },
  "mob_spawn_strategy_by_category": { "monster": <RemapStrategy>, … },     // optional
  "atmosphere": {                                                          // optional
    "has_precipitation": <bool>, "temperature": <float>, "downfall": <float>,
    "sky_color": <int>, "fog_color": <int>, "water_color": <int>, "water_fog_color": <int>,
    "foliage_color": <int>, "grass_color": <int>,
    "effects_extras": {                                                    // optional
      "grass_color_modifier": "none|dark_forest|swamp",                    // optional
      "particle":        { /* AmbientParticleSettings */ },                // optional — drifting particles
      "ambient_sound":   "<sound id>",                                     // optional — continuous loop
      "mood_sound":      { /* AmbientMoodSettings */ },                    // optional — cave whispers
      "additions_sound": { /* AmbientAdditionsSettings */ },               // optional — occasional thuds
      "music":           { /* Music */ }                                   // optional — BGM
    },
    "creature_generation_probability": <float>, "mob_spawn_costs": { /* per-entity */ }
  },
  "client_atmosphere": {                                                   // optional, client-side rendering
    "sky_color":         <int>,    // 0xRRGGBB — sky colour; day/night brightness still applies
    "fog_color":         <int>,    // 0xRRGGBB — overrides rendered fog colour dimension-wide
    "fog_near_distance": <float>,  // fog gradient start, in blocks
    "fog_far_distance":  <float>   // fog gradient end, in blocks
  },
  "content_overrides": {                                                  // optional
    "feature_predicates": { "<placed_feature key>": <SpatialPredicate>, … },
    "structure_spawn_overrides": [ … ],
    "block_overrides": {
      "surface_top":   { "<biome key>": <block state>, … },
      "default_block": { "<biome key>": <block state>, … }
    }
  },
  "priority": <int>                                                       // optional, default 100
}
```

`applies_to` empty = matches no biome (explicit opt-in; prevents cross-dimension leakage). Two
descriptors with the same `dimension` and the same `required_dimension_types` compete by
`priority` (higher wins; an equal or higher priority replaces the earlier one). A different
`required_dimension_types` list is a separate declaration: descriptors for the same dimension
key coexist, and the active one is the descriptor whose list contains the level's
`dimension_type` — a restricted descriptor beats an unrestricted one, then the higher
`priority` wins.

`dimension` also takes an object, which narrows the descriptor to the `dimension_type`s it
was written for. Use it when your world is a selectable world type: its overworld is
`minecraft:overworld` by key but not the same world as vanilla's, and without the restriction
the descriptor would change vanilla worlds too. An empty or omitted list means no restriction
(the string form above is exactly that).

```jsonc
  "dimension": {
    "dimension": "minecraft:overworld",
    "required_dimension_types": ["my_world:my_type"]   // empty/omitted = every world type
  },
```

`client_atmosphere` is resolved by camera Y, so a layered world can give each band its own sky
and fog. While a descriptor is active, its `client_atmosphere.sky_color` replaces the biome
sky colour; use the per-biome `atmosphere.sky_color` when the colour has to vary inside one
dimension. `sky_color` rides the vanilla sky path (`ClientLevel#getSkyColor`), so it needs a
dimension whose `effects` is `minecraft:overworld`; dimensions using `the_end` / `the_nether`
effects render their own sky and ignore it.

On a dedicated server the client cannot read the datapack registry, so the server sends the
active bands over an optional `isekai_api:client_atmosphere` channel on login, `/reload` and
dimension change. A client that joins a server using Isekai API therefore renders the server's
`client_atmosphere`. Vanilla clients and clients without the channel are unaffected.

### Scope by key

A descriptor governs its own `dimension`. Biome attributes are the exception: they live on the
shared biome. The schema is unchanged from 2.x — an existing pack loads as-is, but its feature,
structure, mob spawn and carver rules now apply only in the descriptor's dimension.

| Key | Applies to | Enforced at |
|---|---|---|
| `exclusions.features`, the originals remapped by `ore_strategy`, the originals replaced by `content_overrides.feature_predicates` | the descriptor's dimension | decoration time (`DimensionFeatureScope`), with a final veto in `PlacedFeature#placeWithContext` |
| `additions.features`, remap copies, predicated copies | the descriptor's dimension | `isekai_api:dimension_guard` on each injected feature |
| `exclusions.structures` | the descriptor's dimension | generation-point search; `/place structure` is not blocked |
| `exclusions.mob_spawns`, `additions.mob_spawns`, `mob_spawn_strategy`, `mob_spawn_strategy_by_category` | the descriptor's dimension, for the matched biomes | runtime spawn-list read; exclusions also at `FinalizeSpawnEvent` |
| `exclusions.carvers`, `additions.carvers` | the descriptor's dimension **on `NoiseBasedChunkGenerator`**; biome-wide on other generators | `NoiseBasedChunkGenerator#applyCarvers` |
| `atmosphere` climate/effects, `creature_generation_probability`, `mob_spawn_costs` | the shared biome — cannot differ by dimension | biome modifier |
| `content_overrides.block_overrides` | the descriptor's dimension | the surface rule's `dimension` key |

### `exclusions` — what it removes, and what it holds back

Feature, structure, mob spawn and (on noise-based dimensions) carver exclusions are scoped to the
descriptor's dimension; climate and effects are not. Each list is keyed by registry id:

```jsonc
{
  "exclusions": {
    "features":   ["minecraft:spring_water", "minecraft:spring_lava"],  // placed_feature ids
    "structures": ["minecraft:village_plains"],                          // structure ids
    "carvers":    ["minecraft:cave"],                                    // configured_carver ids
    "mob_spawns": ["minecraft:drowned"]                                  // entity_type ids
  }
}
```

**`exclusions.features` also removes the entry from the Y-remap, in the declaring dimension.** When `ore_strategy` is anything other than `isekai_api:identity`, Isekai re-injects rebuilt copies of every height-ranged feature the biome originally had at the strategy's Y. A key listed in `exclusions.features` is dropped from that set, so no rebuilt copy is made and the original is skipped where the descriptor applies. The original is never deleted from the shared biome: `isekai_api` filters it at decoration time per dimension, so another dimension that reuses the biome keeps generating it. The same is true of `content_overrides.feature_predicates` — the original stays and a guarded, predicated copy runs in the declaring dimension.

Two consequences worth knowing:

* `ore_strategy` is not ore-only. Springs, lakes, geodes and every other feature with a `minecraft:height_range` placement ride the same remap. Exclude them by id if you do not want them at the remapped height.
* Features whose height cannot be read (no `height_range` placement) are outside the remap entirely — they are neither moved nor removed, and `exclusions.features` is the only way to drop them.

`additions.features` and `additions.carvers` are not filtered against `exclusions`. If you list the same id in both, the addition currently wins — do not write that combination deliberately, and do not rely on it to re-add an excluded id.

**Feature scoping is installed by the biome modifier.** The ADD phase records what it took over for
this (dimension, biome) pair, and decoration drops exactly that set. A generator that overrides
`applyBiomeDecoration` entirely is still covered by the `PlacedFeature#placeWithContext` veto. A
worldshape JSON that no `neoforge/biome_modifier` entry references therefore changes no features —
the same rule as `additions`, `carvers`, mob spawns and atmosphere. It is recorded once at server
start, so a `/reload` does not add or remove feature rules; it also means the drop and the injected
copy can never disagree.

**Additions carry a dimension guard.** Every feature the ADD phase injects (explicit `additions.features`, remap copies, predicated copies) is a copy with `isekai_api:dimension_guard` first in its modifier list, so it places only in the descriptor's `dimension`. Without it a nether addition would also spawn in any modded dimension that reuses the same nether biome.

**Another mod's feature on a declared biome follows the scope.** The reconcile removes the original
holder from the shared biome and re-injects a guarded copy for the declaring dimension. That keeps
the feature in the declaring dimension even when it was added by a biome modifier that runs after
Isekai's, but a dimension that reuses the biome without declaring the worldshape no longer receives
it (`ForeignFeatureReconciler`).

**`exclusions.structures` is dimension-scoped.** It is enforced while the structure searches for a
generation point, against the dimension the structure is actually generating in — the structure's
shared biome filter is never mutated. Excluding `minecraft:fortress` in a nether worldshape
therefore leaves it intact in any other dimension that reuses nether biomes. During chunk
generation the dimension comes from `ChunkDimensionIndex`; `/locate` and `/place` use the level
directly. An implementation that passes only a `LevelHeightAccessor` is not indexed and falls back
to `BiomeSource` matching, which is ambiguous when two dimensions share one source. This also means
`/place structure` (which skips the generation-point search) is not blocked by an exclusion.

**Mob spawn changes are dimension-scoped.** `exclusions.mob_spawns` / `additions.mob_spawns` and
the optional per-category strategy are recorded per (dimension, biome) and applied when
`NaturalSpawner` reads the list (`mobsAt`, chunk-generation spawn). Exclusions also cancel at
`FinalizeSpawnEvent`, so patrols, spawners, spawn eggs and `/summon` are covered; a custom mob that
calls `addFreshEntity` without `finalizeSpawn` is not. A different dimension reusing the biome keeps
its original list.

**Carver changes are dimension-scoped on `NoiseBasedChunkGenerator`.** `exclusions.carvers` /
`additions.carvers` are applied in `NoiseBasedChunkGenerator#applyCarvers` per (dimension, biome).
A dimension using another generator, or a `NoiseBasedChunkGenerator` subclass that overrides
`applyCarvers` without calling super, keeps the old biome-wide add/remove.

---

See [`examples/`](../examples/) for complete runnable datapacks, organised by the three worldgen steps: `1_shape/` (the terrain-shape hook — `floating_island/`), `2_placement/` (biome/block selection — `moon_world/`), `3_adaptation/` (worldshape descriptors — `sky_archipelago/`, `flipped/`, `declaration_only/`, `runtime_effects/`). [`examples/templates/`](../examples/templates/) holds annotated copy-paste starting points.

---

## Shipped presets — shape a world without copying 2500 lines

Isekai ships two things so you never hand-copy vanilla's `noise_settings`:

| Preset | What it is | Use |
|---|---|---|
| `isekai_api:hooked_overworld` | a complete `noise_settings` whose `final_density` is the `isekai_api:hook/final_density` hook and whose `surface_rule` is the `vanilla_overworld_surface` delegate (aquifers/veins off) | point a **new** dimension's `generator.settings` at it; override the hook to set your shape |
| `isekai_api:hook/final_density` | a `density_function` — the overworld hook. Default = vanilla terrain shape (`minecraft:overworld/sloped_cheese` reference) | **override** it at `data/isekai_api/worldgen/density_function/hook/final_density.json` in your **datapack** to reshape every world using the preset |
| `isekai_api:hooked_nether` | the nether twin: `min_y 0`, `height 128`, sea level 32, netherrack/lava defaults, legacy random source, `final_density` = `isekai_api:hook/final_density_nether`, `surface_rule` = worldshape hooks + `vanilla_nether_surface` | point a **new** nether-shaped dimension's `generator.settings` at it, or override `data/minecraft/worldgen/noise_settings/nether.json` with it to replace `minecraft:the_nether` itself |
| `isekai_api:hook/final_density_nether` | the nether hook — vanilla nether composition (`minecraft:nether/base_3d_noise` + the two Y gradients), **separate id** so overworld overrides of `hook/final_density` do not touch the nether | override it in your datapack to reshape only nether-preset worlds |

The nether preset keeps vanilla's `temperature`/`vegetation` shifted-noise axes inline, so multi-noise biome placement in `minecraft:the_nether` keeps working; the other axes are `0.0` and `initial_density_without_jaggedness` is `0.0`, exactly as vanilla nether. Its two worldshape surface hooks name `minecraft:the_nether` — retarget them if you point a *new* dimension at the preset. Worldshape bounds (`min_y`/`height`) resolve per dimension, so the same worldshape can carry separate overworld and nether envelopes via `worldshape_surface_top` / `worldshape_default_block` for `minecraft:the_nether`.

**Override physics.** The hook wins because your datapack loads *above* the mod jar (`RegistryDataLoader` takes the highest-priority pack's copy of each id). This is deterministic for a datapack; a second *mod* shipping the same file is mod-vs-mod load order (undefined) — so ship shape overrides as a datapack, and treat a preset as **single-owner per world** (two packs overriding the same hook conflict, exactly like two mods replacing `minecraft:overworld`). Overriding the hook takes effect on **world create**, not `/reload` (the noise_settings resolves its density references once at world load).

The 2-file floating-island world in [`examples/1_shape/floating_island/`](../examples/1_shape/floating_island/) is the whole pattern. To **replace the overworld itself** (not add a dimension), you still need a full `minecraft:overworld` noise_settings document — but [`examples/templates/minimal_overworld.json`](../examples/templates/minimal_overworld.json) is ~30 lines of vanilla density-function references + the hook + the delegate surface, not a 2500-line copy.

---

## Validating a dimension — `/isekai validate dimension <id>`

Prints one line per check, including `spawn OK`/`WARN`/`ERROR`/`SKIP` for a dimension with a
declared worldshape: buried, no footing within 32 blocks, or lava is an error; standing under
water is a warning; an unloaded dimension is skipped. The start point is the overworld shared
spawn, or the (0,0) `MOTION_BLOCKING_NO_LEAVES` heightmap position for other dimensions; the line
names which one was used.

---

## A structure that never spawns — `/isekai survey` and `/isekai fit`

`/locate` answers *where is the nearest one*. When the answer is "nowhere", these two answer *why*.

```
/isekai survey <structure> [radius]     # default radius 64 chunks, max 128
/isekai fit <structure> [pos]
```

Both write JSON under `<world>/isekai/` (`survey/<ns>_<path>-r<radius>.json`, `fit/<ns>_<path>-<chunkX>_<chunkZ>.json`) and print a summary to chat. Neither loads chunks or generates to disk, so they are safe on a live world.

The scan itself runs inside the command call, on the server thread, and it is quadratic in the radius: 64 is 16,641 chunks, 128 is 66,049. The radius is capped at 128 for that reason — the old 512 was 1,050,625 chunks of placement tests plus a generation check for every candidate, in one tick. Nothing is written, but the server waits for the answer before it ticks again.

**`/isekai survey`** replays the two steps chunk generation takes over a square of chunks around you: the structure set's `StructurePlacement#isStructureChunk` decides which chunks are tried, then the structure's own generation decides whether it exists there. Every candidate ends up either placed or counted against exactly one reason:

| reason | meaning |
|---|---|
| `clearance_above_fluid` | a footprint column sits at or below the placement's clearance floor |
| `max_slope` | the footprint's height spread exceeds `max_slope` |
| `biome` | the position's biome is not in the structure's `biomes` (only checked after both gates pass) |
| `declined` | the structure type returned "nothing here" for a reason of its own (`jigsaw`, `assembled`, modded types) |

The two rates separate the two halves of the problem: `candidate_rate` is how often the structure set offers a chunk at all (set spacing/separation), `place_rate_of_candidates` is how often the gates say yes. A measured run on a real pack's world, radius 64 (the JSON keeps up to five sample placements; one is shown):

```json
{
  "structure": "minecraft:village_plains",
  "dimension": "minecraft:overworld",
  "radius_chunks": 64,
  "scanned_chunks": 16641,
  "candidate_chunks": 13,
  "candidate_rate": 0.000781,
  "placed": 13,
  "place_rate_of_candidates": 1.000000,
  "place_rate_of_scanned": 0.000781,
  "rejections": {},
  "structure_sets": [ "minecraft:villages" ],
  "sample_placements": [
    { "chunk_x": -15, "chunk_z": -63, "box": [ -298, -76, -1088, -179, -36, -927 ] }
  ],
  "elapsed_ms": 421
}
```

Every candidate became a village, so the set is the limit, not the terrain. The opposite reading — 16,641 chunks scanned, 9 candidates, none placed, and `rejections` naming the split — is what sends you to `max_slope`/`clearance` instead of the spacing.

**`/isekai fit`** answers the single-chunk version at full detail: the heightmap the gates read, laid out as a 3x3 grid, beside the numbers they compare it to, plus where `vertical_offset` puts each variant of the template.

```
fit <ns>:<name> at chunk 4,-4 in minecraft:the_end
  sea level 0, clearance_above_fluid 2, max_slope 4, vertical_offset -3
  surface heights, 3x3 (rows north to south, columns west to east; the gates read the middle and the four corners):
       57    48    48
       57    51    50
       57    57    55
  gate columns: min 48, max 57, spread 9
  verdict: max_slope — lowest column 48 > clearance floor 2; spread 9 > max_slope 4
  template <ns>:<name> (5x4x5, weight 3): y=0 at 48, blocks y 48..51
  measured on <ns>:<name> at rotation none (the first variant); other variants and turns cover other ground and are not judged here
```

The gates sample five of those nine columns (middle and corners); the grid is there so you can see what the footprint would hit if you shrank the template, not so you can tune against columns the structure never reads. Two things the report catches that cost hours otherwise: a `template` whose NBT does not exist (reported as `0x0x0` with a warning — both the gates and the footprint collapse to one column), and a chunk no structure set offers at all (`note: no structure set offers this chunk`), where the gates are not the reason the chunk is empty.

The verdict describes one footprint: the first variant of the template list, unrotated. The last line says so, and the JSON carries `measured_template` / `measured_rotation`. A structure with several variants, or `rotation: random` (drawn per placement), covers different ground in other chunks than the one this report judges.

### Counting trees and other blocks — `/isekai survey blocks`

```
/isekai survey blocks <block|#tag> [radius] [center <x y z>] [biome <biome|#tag>]
```

```
/isekai survey blocks #minecraft:logs 16 biome minecraft:forest
/isekai survey blocks minecraft:palm_log 16 center 0 64 0 biome #minecraft:is_forest
```

`survey blocks` answers the half `/isekai survey` cannot: the feature fires, but the world does not look like a palm forest. It generates the square of chunks to `FEATURES` (trees placed, no lighting or spawn pass), counts the matching blocks in each chunk, and moves to the next chunk on the next tick. **Generation is synchronous on the server thread**: a fresh chunk costs tens of milliseconds, so one tick per chunk means TPS drops for the length of the run (a radius-8 square is 289 chunks). The tick loop cannot cut inside a chunk's generation; it only decides whether to start another one. Only one `survey blocks` runs per server at a time — a second one is refused with a message naming the run that is still going, because two would interleave inside the same ticks. Unlike the structure survey this **writes terrain to disk**, exactly as a player walking there would; run it on a test world or where generated terrain is acceptable.

Each chunk is counted as soon as its own `FEATURES` stage finishes, before its neighbours generate. A tree rooted in a later chunk can still write logs or leaves into a chunk that was already counted, so the per-chunk numbers are a lower bound; the effect is small for a log tag and larger for a leaf tag, because a crown reaches further across a border than a trunk does.

`radius` is in chunks (default 8, max 32), `center` is a block position (defaults to where you run it), and `biome` keeps only chunks whose centre column is in that biome or tag. The biome filter is resolved from the seed before generation, so skipped chunks are never generated; the report still counts them under `skipped_chunks`. `biome` must come after `radius` because Brigadier needs one unambiguous path per optional argument.

**One tree is one six-connected group of matching blocks inside one chunk.** A 2x2 trunk is one tree, not four; a leaning trunk is one tree, not one per step. The tradeoff is the chunk border: a tree straddling two chunks is cut and counted in each chunk, so a 2x2 trunk on a chunk corner reads as one tree in each of the four chunks. Trunks that touch are also one group, so give it a log id or a log tag, not a leaf tag. Every chunk also reports its matching **block** count, which separates "many short palms" from "few tall palms".

```json
{
  "target": "#minecraft:logs",
  "dimension": "minecraft:overworld",
  "radius_chunks": 8,
  "center_chunk": [ -32, -24 ],
  "biome_filter": "minecraft:forest",
  "counted_chunks": 157,
  "skipped_chunks": 132,
  "failed_chunks": 0,
  "tree_definition": "one six-connected group of matching blocks inside one chunk",
  "trees": {
    "total": 1045, "mean_per_chunk": 6.658, "min_per_chunk": 0,
    "median_per_chunk": 7.0, "max_per_chunk": 19,
    "zero_chunks": 2, "zero_share": 0.0127,
    "top_ten_percent_chunks": 16, "top_ten_percent_share": 0.1895,
    "histogram": { "0": 2, "1-2": 12, "3-5": 24, "6-10": 109, "11-20": 10, "21-40": 0, "41+": 0 }
  },
  "blocks": { "total": 5120, "mean_per_chunk": 32.6115 },
  "chunks": [ { "x": -32, "z": -24, "trees": 5, "blocks": 22 } ],
  "elapsed_ms": 7435
}
```

The written file keeps one entry per counted chunk, so the distribution can be mapped afterwards: `<world>/isekai/survey/blocks-<target>-r<radius>.json`, plus `-<biome>` when a filter is given. Measured on a fresh vanilla world: `minecraft:forest` averages **6.7 trees per chunk** at both radius 8 (157 chunks) and radius 16 (260 chunks), with 1.2–1.3% empty chunks and about 19% of all trees in the busiest 10% of chunks. Vanilla `trees_birch_and_oak` makes ten placement attempts per chunk and some fail on slope or space, so compare against measured numbers like these rather than the attempt count — and always compare runs with the same biome filter, since an unfiltered square includes ocean and plains and drops the mean (5.0 at radius 8 in the same world).

---

## World preset overrides — the Nether/End trap

Overriding `data/minecraft/worldgen/world_preset/normal.json` to customise the overworld **also requires re-declaring the Nether and End stanzas verbatim**. The preset map replaces vanilla's preset entirely; if you only list `minecraft:overworld`, Nether and End silently become inaccessible (no warning from vanilla — only Isekai's validator catches it).

The Isekai validator runs `validateWorldPresets` on server start and logs a warning when an authored `worldgen/world_preset/*.json` is missing any of the three standard dimensions. To copy the boilerplate, see [`examples/templates/world_preset_normal_override.json`](../examples/templates/world_preset_normal_override.json) — fully annotated.

For a **new** dimension (one you're adding alongside the standard ones, not replacing the overworld), you do NOT need to write a `world_preset` file at all. NeoForge auto-loads `data/<ns>/dimension/<name>.json` as an additional dimension at world create time.
