# Isekai API — Datapack Reference

Every Isekai key you can write in datapack JSON, in one place. No Java required.

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

### Worldshape composers

Theme-neutral building blocks for Y-banded terrain (floating islands, hanging continents, hollow shells…).

| Type | Fields | Result |
|---|---|---|
| `isekai_api:squeeze` | `argument` (density) | vanilla tone-mapping `x/2 − x³/24` clamped to [−1, 1] |
| `isekai_api:y_envelope` | `active_min_y`, `active_max_y` (int), `gradient_width` (int, default 30), `invert` (bool, default false) | 1 inside the Y band, 0 outside, linear ramp through `gradient_width`; `invert` flips polarity |
| `isekai_api:blended_noise` | `size_xz`, `size_y` (1–1000), `smear_multiplier` (1–8, default 8) | `old_blended_noise` wrapper (xz/y scale fixed at 0.25); bigger size = larger features |
| `isekai_api:band_density` | `active_min_y`, `active_max_y` (int), `gradient_width` (int ≥ 1, default 30), `invert` (bool, default false), `solidity_bias` (−1…1, default 0), `noise` (density) | full Aether-style band terrain: noise visible only inside the Y band. `invert` hangs terrain from the top; `solidity_bias` shifts island↔continent |

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

## Biome source (`isekai_api:rule`)

Goes in a dimension's `generator.biome_source`. Places biomes by spatial rules, evaluated in order — first matching `BiomeZone` wins, else `fallback`.

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

---

## Surface rules (`isekai_api:`)

Go in `noise_settings.surface_rule` (inside a `minecraft:sequence`). Both read per-biome blocks from the active worldshape's `content_overrides.block_overrides` for the named dimension.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:worldshape_surface_top` | `dimension` (dimension key) | replaces the top block of matched biomes (`block_overrides.surface_top`). Put it **first** in the sequence. |
| `isekai_api:worldshape_default_block` | `dimension` (dimension key) | replaces the default (stone) fill of matched biomes (`block_overrides.default_block`). Put it **last** (after vanilla rules). |

---

## Placement modifiers (`isekai_api:`)

Go in a placed feature's modifier list.

| Type | Fields | Effect |
|---|---|---|
| `isekai_api:surface_relative` | `anchor` (SurfaceAnchor), `offset` (IntProvider) | place at `anchor` Y + offset |
| `isekai_api:fluid_relative` | `anchor` (`fluid_top` \| `fluid_bottom`), `offset` (IntProvider) | place relative to the water column top/bottom + offset |
| `isekai_api:in_block_context` | `match_blocks` (block / list / `#tag`), `exclude_in_fluid` (bool, default false), `require_air_above` (int, default 0) | place only where the block context matches |
| `isekai_api:spatial_predicate` | `predicate` (SpatialPredicate) | place only where the predicate holds |

(`IntProvider` = a vanilla int provider: a bare int like `5`, or `{"type":"minecraft:uniform","value":{"min_inclusive":0,"max_inclusive":8}}`.)

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
  "atmosphere": { "sky_color": <int>, "fog_color": <int>, … },            // optional
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

See [`examples/`](../examples/) for complete runnable datapacks (`sky_archipelago/`, `flipped/`, `moon_world/`).
