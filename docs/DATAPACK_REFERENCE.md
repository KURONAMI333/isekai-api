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

### BiomeZone (`isekai_api:`) — used inside `rule.zone`

Coordinates are authored in **block** space. Evaluated at biome-grid resolution (one sample per 4 blocks), coordinates only — no terrain context.

| Type | Fields | Matches |
|---|---|---|
| `isekai_api:always` | — | everywhere (catch-all) |
| `isekai_api:y_above` | `y` (int) | block Y ≥ y |
| `isekai_api:y_below` | `y` (int) | block Y < y |
| `isekai_api:y_between` | `min`, `max` (int, min < max) | min ≤ block Y < max |
| `isekai_api:within_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance from center ≤ radius |
| `isekai_api:beyond_distance` | `radius` (double ≥ 0), `center_x`, `center_z` (int, default 0) | XZ distance > radius |
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
| `isekai_api:column_relative` | `top` / `bottom` (SurfaceAnchor, default `world_surface` / `world_floor`), `from_depth` / `to_depth` (double), `scale` (`blocks`\|`proportional`, default `blocks`), `reference_thickness` (int 1–4096, default 128), `distribution` (HeightDistribution, default `uniform`) | place at a **depth into the column's own terrain** instead of an absolute Y. `0.0` is the free space above the body, `1.0` the free space below it. `blocks` measures a fixed block distance from whichever end the band is nearer to (so an ore keeps its distance from the surface whatever the body's size); `proportional` measures a fraction of the body's own thickness (so the layout stretches with it). Normally emitted by `isekai_api:column_local` rather than written by hand. |
| `isekai_api:slope_filter` | `min_slope` / `max_slope` (double 0–1, defaults 0/1), `sample_radius` (int 1–8, default 2), `heightmap` (Heightmap type, default `WORLD_SURFACE_WG`) | accept positions where the local heightmap slope (max neighbour-height-delta over `sample_radius`, normalised) falls within `[min_slope, max_slope]`. 0 = flat, 1 ≈ 45°+ cliff. |

(`IntProvider` = a vanilla int provider: a bare int like `5`, or `{"type":"minecraft:uniform","min_inclusive":0,"max_inclusive":8}` — flat fields, no `value:` wrapper in 1.21.1.)

---

## Features (`isekai_api:`)

Geometric placement primitives — use as `type` inside a configured_feature JSON. Block / fluid choices are codec fields, no themes baked in.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:cluster` | `block` (BlockStateProvider), `size` (IntProvider 1–256), `can_replace_solid` (bool, default false) | random-walk BFS from origin, places `size` connected blocks. Use for moss patches, dirt veins, fungus spreads, ore clusters — any "blob of N connected blocks". |
| `isekai_api:pool` | `fluid` (BlockState), `rim_block` (BlockStateProvider), `xz_radius` (IntProvider 1–8 — flat form `{"type":"minecraft:uniform","min_inclusive":3,"max_inclusive":5}`), `depth` (int 1–4, default 2), `irregularity` (double 0–1, default 0) | carves a horizontal footprint into terrain (interior cleared), lines the floor with `rim_block`, fills the carved volume with `fluid` up to the natural ground level. Avoids `waterlogged_vegetation_patch`'s grass→dirt drowning trap — the rim block is whatever you pass, never drowned grass. |

### `irregularity` — breaking the circle

`irregularity: 0` (the default) carves an exact circle, which reads as a compass-drawn disc on the ground. Raising it bites into the outline at a set of angles fixed by the world seed and the pool's position, giving a lopsided blob instead. **0.3–0.5 is the useful band**; below 0.2 the pool still reads as round, above 0.6 it starts to look gnawed.

Two consequences worth planning for:

- **The bite only ever removes blocks**, never adds them, so a pool is always inside its nominal `xz_radius` disc. That is deliberate — growing outward would carve cells your placement filter never checked, and the fluid would run off down the slope. The price is that the pool averages roughly `irregularity / 2` smaller than a circle, so **raise `xz_radius` to compensate** (`radius 5–8` at `irregularity 0.4` gives about the same water surface as `radius 4–6` at 0).
- **The shape is a function of the world seed and the block position**, so it survives chunk regeneration and is identical on every client.

```json
{
  "type": "isekai_api:pool",
  "config": {
    "fluid": { "Name": "minecraft:water" },
    "rim_block": {
      "type": "minecraft:simple_state_provider",
      "state": { "Name": "minecraft:sand" }
    },
    "xz_radius": { "type": "minecraft:uniform", "min_inclusive": 5, "max_inclusive": 8 },
    "depth": 2,
    "irregularity": 0.4
  }
}
```

Do **not** try to break the circle from the datapack side by placing two or three `pool` features of different radii at the same spot. Each call resolves its own surface height, so the second one reads the first one's carved floor as ground level, and you get a ring of bare blocks beside the water instead of a wider pool.

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

Declare the worldshape **once** under `data/<ns>/isekai/worldshape/<name>.json`, then point the `_ref` modifiers at it by dimension. The `_ref` forms are the recommended path (no descriptor duplication, 4-line modifier files).

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

### `exclusions` — what it removes, and what it holds back

Everything listed under `exclusions` is gone from the matched biomes. Each list is keyed by registry id:

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

**`exclusions.features` also removes the entry from the Y-remap.** When `ore_strategy` is anything other than `isekai_api:identity`, Isekai rewrites the vertical placement of every height-ranged feature the biome originally had: it deletes the originals and re-injects rebuilt copies at the strategy's Y. A key listed in `exclusions.features` is dropped from that set, so it is removed and never rebuilt. Without this the exclusion would be undone one phase later — the rebuilt copy carries no registry id, so nothing downstream (a second `exclusions` pass, `content_overrides.feature_predicates`) could ever match it again.

Two consequences worth knowing:

* `ore_strategy` is not ore-only. Springs, lakes, geodes and every other feature with a `minecraft:height_range` placement ride the same remap. Exclude them by id if you do not want them at the remapped height.
* Features whose height cannot be read (no `height_range` placement) are outside the remap entirely — they are neither moved nor removed, and `exclusions.features` is the only way to drop them.

`additions.features` and `additions.carvers` are not filtered against `exclusions`. If you list the same id in both, the addition currently wins — do not write that combination deliberately, and do not rely on it to re-add an excluded id.

---

See [`examples/`](../examples/) for complete runnable datapacks, organised by the three worldgen steps: `1_shape/` (the terrain-shape hook — `floating_island/`), `2_placement/` (biome/block selection — `moon_world/`), `3_adaptation/` (worldshape descriptors — `sky_archipelago/`, `flipped/`, `declaration_only/`, `runtime_effects/`). [`examples/templates/`](../examples/templates/) holds annotated copy-paste starting points.

---

## Shipped presets — shape a world without copying 2500 lines

Isekai ships two things so you never hand-copy vanilla's `noise_settings`:

| Preset | What it is | Use |
|---|---|---|
| `isekai_api:hooked_overworld` | a complete `noise_settings` whose `final_density` is the `isekai_api:hook/final_density` hook and whose `surface_rule` is the `vanilla_overworld_surface` delegate (aquifers/veins off) | point a **new** dimension's `generator.settings` at it; override the hook to set your shape |
| `isekai_api:hook/final_density` | a `density_function` — the hook. Default = vanilla terrain shape (`minecraft:overworld/sloped_cheese` reference) | **override** it at `data/isekai_api/worldgen/density_function/hook/final_density.json` in your **datapack** to reshape every world using the preset |

**Override physics.** The hook wins because your datapack loads *above* the mod jar (`RegistryDataLoader` takes the highest-priority pack's copy of each id). This is deterministic for a datapack; a second *mod* shipping the same file is mod-vs-mod load order (undefined) — so ship shape overrides as a datapack, and treat a preset as **single-owner per world** (two packs overriding the same hook conflict, exactly like two mods replacing `minecraft:overworld`). Overriding the hook takes effect on **world create**, not `/reload` (the noise_settings resolves its density references once at world load).

The 2-file floating-island world in [`examples/1_shape/floating_island/`](../examples/1_shape/floating_island/) is the whole pattern. To **replace the overworld itself** (not add a dimension), you still need a full `minecraft:overworld` noise_settings document — but [`examples/templates/minimal_overworld.json`](../examples/templates/minimal_overworld.json) is ~30 lines of vanilla density-function references + the hook + the delegate surface, not a 2500-line copy.

---

## World preset overrides — the Nether/End trap

Overriding `data/minecraft/worldgen/world_preset/normal.json` to customise the overworld **also requires re-declaring the Nether and End stanzas verbatim**. The preset map replaces vanilla's preset entirely; if you only list `minecraft:overworld`, Nether and End silently become inaccessible (no warning from vanilla — only Isekai's validator catches it).

The Isekai validator runs `validateWorldPresets` on server start and logs a warning when an authored `worldgen/world_preset/*.json` is missing any of the three standard dimensions. To copy the boilerplate, see [`examples/templates/world_preset_normal_override.json`](../examples/templates/world_preset_normal_override.json) — fully annotated.

For a **new** dimension (one you're adding alongside the standard ones, not replacing the overworld), you do NOT need to write a `world_preset` file at all. NeoForge auto-loads `data/<ns>/dimension/<name>.json` as an additional dimension at world create time.
