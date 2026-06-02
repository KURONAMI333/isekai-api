# Isekai API — Datapack Reference

Every Isekai key you can write in datapack JSON, in one place. No Java required.

> Editing `isekai/worldshape/*.json` or `isekai/layered_worldshape/*.json`? Wire up the
> [JSON Schemas](schema/README.md) for completion and edit-time validation.

There are two kinds of identifier:

- **`isekai_api:<name>`** — a registered worldgen *type*, used as a `"type"` value where Minecraft expects a density function, biome source, surface rule, placement modifier, biome modifier, or structure modifier.
- **`isekai:<name>`** — an in-house *dispatch tag*, used as the `"type"` value inside Isekai's own sealed payloads (`SpatialPredicate`, `RemapStrategy`, `SurfaceAnchor`, `TransitionRule`, `BiomeZone`). These are not registry entries; they only appear nested inside a worldshape descriptor or a rule biome source.

Where each goes:

| You're editing… | Use |
|---|---|
| `data/minecraft/worldgen/noise_settings/<dim>.json` → `noise_router.final_density` | density functions |
| `…noise_settings/<dim>.json` → `surface_rule` | surface rules |
| `data/<ns>/dimension/<name>.json` → `generator.biome_source` | biome source |
| `data/<ns>/worldgen/placed_feature/*.json` → placement list | placement modifiers |
| `data/<ns>/neoforge/biome_modifier/*.json` | biome modifiers |
| `data/<ns>/neoforge/structure_modifier/*.json` | structure modifiers |
| `data/<ns>/isekai/worldshape/*.json` | the worldshape descriptor (uses the `isekai:` dispatch payloads) |

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
| `isekai_api:mask_y_range` | `min_y`, `max_y` (int), `inside`, `outside` (density) | `inside` within the Y band, else `outside` |
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

---

## Biome sources (`isekai_api:`)

Two biome sources for different placement strategies — pick whichever fits your world. Both go in a dimension's `generator.biome_source`.

### `isekai_api:rule` — by spatial rule

Places biomes by `BiomeZone` rules evaluated in declaration order; first match wins, else `fallback`.

```jsonc
{
  "type": "isekai_api:rule",
  "fallback": "minecraft:plains",
  "rules": [
    { "zone": { "type": "isekai:y_below", "y": 20 }, "biome": "minecraft:deep_dark" },
    { "zone": { "type": "isekai:within_distance", "radius": 1000 }, "biome": "minecraft:desert" }
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

### BiomeZone (`isekai:`) — used inside `rule.zone`

Coordinates are authored in **block** space. Evaluated at biome-grid resolution (one sample per 4 blocks), coordinates only — no terrain context.

| Type | Fields | Matches |
|---|---|---|
| `isekai:always` | — | everywhere (catch-all) |
| `isekai:y_above` | `y` (int) | block Y ≥ y |
| `isekai:y_below` | `y` (int) | block Y < y |
| `isekai:y_between` | `min`, `max` (int, min < max) | min ≤ block Y < max |
| `isekai:within_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance from center ≤ radius |
| `isekai:beyond_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance > radius |
| `isekai:and` | `all` (list of zones) | all match |
| `isekai:or` | `any` (list of zones) | any match |
| `isekai:not` | `inner` (zone) | inner does not match |
| `isekai:noise_threshold` | `noise` (noise key/inline), `seed` (long, default 0), `threshold` (double, default 0), `size_xz` / `size_y` (double, default 64) | true where a deterministic noise sample exceeds `threshold` — organic biome masks |
| `isekai:edge_jitter` | `inner` (zone), `noise` (noise key/inline), `seed` (long, default 0), `strength` (double 0–32, default 4), `size_xz` (double, default 32) | wraps `inner`, perturbs the test coordinate by a small noise offset before delegating — turns geometric borders into wavy organic ones |

---

## Surface rules (`isekai_api:`)

Go in `noise_settings.surface_rule` (inside a `minecraft:sequence`). Both read per-biome blocks from the active worldshape's `content_overrides.block_overrides` for the named dimension.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:worldshape_surface_top` | `dimension` (dimension key) | replaces the top block of matched biomes (`block_overrides.surface_top`). Put it **first** in the sequence. |
| `isekai_api:worldshape_default_block` | `dimension` (dimension key) | replaces the default (stone) fill of matched biomes (`block_overrides.default_block`). Put it **last** (after vanilla rules). |
| `isekai_api:strata` | `bands` (list of `{ block: BlockState, thickness: int ≥ 1 }`) | ordered downward stack — band 1 covers depths 0…t1−1, band 2 covers t1…t1+t2−1, etc. Emits null below the last band so the surrounding sequence handles deeper fill. Collapses an N-layer nested `stone_depth` sequence into one flat list. |

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
| `isekai_api:fluid_edge` | `fluid` (fluid id / list / `#tag`), `max_distance` (int 1–16, default 4), `mode` (`near`\|`far`, default `near`) | accept positions where a matching fluid is (`near`) or isn't (`far`) within `max_distance` blocks in XZ. Geometric distance filter — pure membership test, no theme. |
| `isekai_api:slope_filter` | `min_slope` / `max_slope` (double 0–1, defaults 0/1), `sample_radius` (int 1–8, default 2), `heightmap` (Heightmap type, default `WORLD_SURFACE_WG`) | accept positions where the local heightmap slope (max neighbour-height-delta over `sample_radius`, normalised) falls within `[min_slope, max_slope]`. 0 = flat, 1 ≈ 45°+ cliff. |

(`IntProvider` = a vanilla int provider: a bare int like `5`, or `{"type":"minecraft:uniform","min_inclusive":0,"max_inclusive":8}` — flat fields, no `value:` wrapper in 1.21.1.)

---

## Features (`isekai_api:`)

Geometric placement primitives — use as `type` inside a configured_feature JSON. Block / fluid choices are codec fields, no themes baked in.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:cluster` | `block` (BlockStateProvider), `size` (IntProvider 1–256), `can_replace_solid` (bool, default false) | random-walk BFS from origin, places `size` connected blocks. Use for moss patches, dirt veins, fungus spreads, ore clusters — any "blob of N connected blocks". |
| `isekai_api:pool` | `fluid` (BlockState), `rim_block` (BlockStateProvider), `xz_radius` (IntProvider 1–8 — flat form `{"type":"minecraft:uniform","min_inclusive":3,"max_inclusive":5}`), `depth` (int 1–4, default 2) | carves a horizontal disc into terrain (interior cleared), lines the floor + outer ring with `rim_block`, fills the carved volume with `fluid`. Avoids `waterlogged_vegetation_patch`'s grass→dirt drowning trap — the rim block is whatever you pass, never drowned grass. |

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
  "clearance_above_fluid": 2,          // every footprint column must rise >= this above sea level
  "max_slope": 4,                      // reject if footprint height-spread exceeds this
  "vertical_offset": -1                // template y=0 lands at (surface + this); tune sink/raise
}
```
The footprint is sampled at the template's centre + four corners, so a too-aggressive `max_slope` (for a large template on hilly terrain) makes it rare or absent; loosen it or shrink the template if natural spawns dry up.

### `isekai_api:assembled` — loose scatter of features at one origin (NOT coordinated landmarks)

A `Structure` that places a list of `PlacedFeature`s at a single origin (Y snapped to live `WORLD_SURFACE_WG`). Use it **only** when the features are genuinely independent and don't need to line up — e.g. "scatter a few boulders and bushes near this point." It has **no** `terrain_adaptation`, no relative positioning between features, and no processors, so it is the wrong tool for a coordinated landmark; use the jigsaw+NBT path above for those.

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

---

## Tree placers (`isekai_api:`)

Used as the `trunk_placer` / `foliage_placer` of a vanilla `minecraft:tree` configured feature — the wood/leaf blocks are the feature's own `trunk_provider` / `foliage_provider` slots, the placer only decides geometry. Compose any trunk with any foliage. Spread leaves are placed with their `LeavesBlock.DISTANCE` pinned internally so the tree never decays — no extra work needed in the consumer JSON.

### Trunk placers (`trunk_placer`)

| Type | Fields | Shape |
|---|---|---|
| `isekai_api:leaning` | `base_height`, `height_rand_a`, `height_rand_b` (int — shared); `min_height_for_leaves` (positive int, default 1); `lean_length` (IntProvider 1–16); `convert_ground` (bool, default true — false skips the dirt-under-trunk conversion so beach palms stay sandy); `tip_crown_only` (bool, default false — true returns only the bent-tip attachment so a fan crown sprays from one point) | rises straight, leans near the top, then continues as a near-horizontal stalk |
| `isekai_api:branching` | `base_height`, `height_rand_a`, `height_rand_b` (int — shared); `branch_count` (int 1–6); `branch_length` (IntProvider 1–8); `branch_start_offset_from_top` (IntProvider 0–4, default 0 — how far below the trunk top branches sprout) | straight vertical trunk plus N upward-arcing branches; each branch tip becomes its own crown site, so paired with `disc`/`sphere` you get umbrella/forked silhouettes |

### Foliage placers (`foliage_placer`)

Common fields on every placer: `radius` (IntProvider — vanilla, often 0 since each placer drives its own geometry), `offset` (IntProvider — vertical shift above the attachment).

| Type | Extra fields | Shape |
|---|---|---|
| `isekai_api:sphere` | `height` (int 0–16); `jitter` (float 0–1, default 0.2) | ellipsoid crown (height vs radius selects flat disc vs tall ball); edge cells dropped randomly by `jitter` |
| `isekai_api:fan` | `crown_radius` (int 1–4, default 2); `hang` (int 0–3, default 1) | compact log-adjacent core + cardinal arms reaching out `crown_radius` and drooping `hang` past the tip — palm head |
| `isekai_api:cone` | `base_radius` (int 1–8); `height` (int 1–16); `taper` (`linear`\|`concave`, default linear); `jitter` (float 0–1, default 0.15) | conical crown; concave taper = conifer/cypress silhouette |
| `isekai_api:disc` | `disc_radius` (int 1–8); `thickness` (int 1–4, default 1); `jitter` (float 0–1, default 0.2) | flat wide horizontal disc — umbrella/savanna |
| `isekai_api:weeping` | `crown_radius` (int 2–6, default 3); `crown_thickness` (int 1–3, default 2); `strand_length` (IntProvider 1–6); `strand_chance` (float 0–1, default 0.4) | stacked discs + vertical leaf strands dripping from rim cells — willow/wisteria/sakura |

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

---

## Biome / structure modifiers (`isekai_api:`)

### Biome modifiers — `data/<ns>/neoforge/biome_modifier/*.json`

```jsonc
// inline form
{ "type": "isekai_api:apply_worldshape", "worldshape": { …descriptor… } }
// reference form (descriptor lives in isekai/worldshape/<name>.json)
{ "type": "isekai_api:apply_worldshape_ref", "dimension": "minecraft:overworld" }
```

### Structure modifiers — `data/<ns>/neoforge/structure_modifier/*.json`

```jsonc
{ "type": "isekai_api:apply_worldshape_structures", "worldshape": { …descriptor… } }
{ "type": "isekai_api:apply_worldshape_structures_ref", "dimension": "minecraft:overworld" }
```

The `_ref` forms look the descriptor up by dimension at apply-time, so the worldshape is declared once (in `isekai/worldshape/<name>.json`) and the modifier files stay 4 lines.

---

## In-house dispatch payloads (`isekai:`)

Used inside the worldshape descriptor fields. Each dispatches on a `"type"` value.

### SpatialPredicate (`structure_predicates`, `default_structure_predicate`, `content_overrides.feature_predicates`, `isekai_api:spatial_predicate`)

| Type | Fields |
|---|---|
| `isekai:y_in_range` | `min`, `max` (int) |
| `isekai:solid_floor` | `min_clearance` (int) |
| `isekai:solid_ceiling` | `min_clearance` (int) |
| `isekai:terrain_slope` | `min_slope`, `max_slope` (double) |
| `isekai:near_block` | `targets` (block id / list / `#tag`), `max_distance` (int) |
| `isekai:near_biome` | `biome` (biome key), `max_distance` (int) |
| `isekai:in_fluid` | `fluid` (fluid key) |
| `isekai:always` / `isekai:never` | — |
| `isekai:and` | `all` (list) |
| `isekai:or` | `any` (list) |
| `isekai:not` | `inner` (predicate) |

### RemapStrategy (`ore_strategy`, `structure_strategy`, `mob_spawn_strategy`, `mob_spawn_strategy_by_category`)

| Type | Fields |
|---|---|
| `isekai:identity` / `isekai:linear` / `isekai:inverted` | — |
| `isekai:fixed_range` | `min`, `max` (int), `dist` (HeightDistribution) |
| `isekai:count_scale` | `factor` (double ≥ 0) |
| `isekai:band_split` | `bands` (list of `{ vanilla_source: VerticalRange, target_ratio: float }`) |
| `isekai:pipe` | `chain` (non-empty list of strategies) |

`structure_strategy` only acts through the `count_scale` factor (it scales RandomSpread spacing); other variants are no-ops there.

### SurfaceAnchor (`surface_anchor`)

| Type | Fields |
|---|---|
| `isekai:world_surface` | — |
| `isekai:below_fluid` | `fluid` (fluid key) |
| `isekai:fixed_y` | `y` (int) |

### TransitionRule (layered worldshapes)

| Type | Fields |
|---|---|
| `isekai:hard` | — |
| `isekai:blend` | `blend_height` (int ≥ 0) |
| `isekai:gap` | `gap_height` (int ≥ 0) |

### HeightDistribution (used by `playable_range.distribution`, `fixed_range.dist`)

`uniform` \| `trapezoid` \| `triangle` \| `biased_low` \| `biased_high`

---

## WorldshapeDescriptor (the `isekai/worldshape/*.json` body)

```jsonc
{
  "dimension": "<dimension key>",                 // required
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

`applies_to` empty = matches no biome (explicit opt-in; prevents cross-dimension leakage). Higher `priority` wins when two descriptors target the same dimension.

---

See [`examples/`](../examples/) for complete runnable datapacks (`sky_archipelago/`, `flipped/`, `moon_world/`), and [`examples/templates/`](../examples/templates/) for annotated copy-paste starting points (currently: a `world_preset` override template that re-declares Nether/End verbatim — see the section below).

---

## World preset overrides — the Nether/End trap

Overriding `data/minecraft/worldgen/world_preset/normal.json` to customise the overworld **also requires re-declaring the Nether and End stanzas verbatim**. The preset map replaces vanilla's preset entirely; if you only list `minecraft:overworld`, Nether and End silently become inaccessible (no warning from vanilla — only Isekai's validator catches it).

The Isekai validator runs `validateWorldPresets` on server start and logs a warning when an authored `worldgen/world_preset/*.json` is missing any of the three standard dimensions. To copy the boilerplate, see [`examples/templates/world_preset_normal_override.json`](../examples/templates/world_preset_normal_override.json) — fully annotated.

For a **new** dimension (one you're adding alongside the standard ones, not replacing the overworld), you do NOT need to write a `world_preset` file at all. NeoForge auto-loads `data/<ns>/dimension/<name>.json` as an additional dimension at world create time.
