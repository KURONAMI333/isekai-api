# Isekai API — example datapacks

Two categories, both as self-contained datapacks:

- **`runtime_effects/`** — packs that go in `data/<ns>/neoforge/biome_modifier/` or
  `data/<ns>/neoforge/structure_modifier/` and produce visible in-game changes via
  NeoForge's modifier pipeline.
- **`declaration_only/`** — packs that go in `data/<ns>/isekai/worldshape/` or
  `data/<ns>/isekai/layered_worldshape/`. They populate Isekai's runtime registry
  (queryable via `/isekai query worldshape`) but do not, by themselves, alter chunk
  generation. Wrap the same descriptor in an `apply_worldshape` biome modifier to make
  it take effect.

To try one:

1. Copy the directory into your world's `datapacks/` folder.
2. Run `/reload` (or restart the server).
3. Confirm it loaded with `/isekai query dimensions` and
   `/isekai query worldshape minecraft:overworld`.
4. Validate the JSON without applying it with
   `/isekai validate <pack-namespace>`, e.g.
   `/isekai validate skyland_example`.

## skyland_minimal/

Minimal valid single-layer descriptor: overworld restricted to y=100..200
with a fixed surface at y=150 and linear remapping across all three
strategies. Demonstrates the smallest possible JSON shape — every required
field is present and every optional field is omitted (defaulting to empty
sets / `priority=100`).

## underground_only/

Registry-only example showing what the JSON for an underground worldshape
*would* contain — `isekai:pipe` of `inverted` + `linear` for ore remapping
(deepslate-band ores would get pulled toward the surface relative to the
playable range), `isekai:and`-composed default structure predicate
(`y_in_range` + `solid_ceiling`, so structures only spawn in roofed
chambers), `count_scale` 1.5× for mob density, `priority=110` so it wins
ties against the default-priority skyland_minimal pack.

To turn this into an observable effect, copy the `worldshape` JSON content
into a `neoforge/biome_modifier/` file under the `isekai_api:apply_worldshape`
type (see `biome_modifier_demo/` for the wrapping pattern).

## peaceful_plains/

Demonstrates `mob_spawn_strategy_by_category` (v0.11). In `minecraft:plains`,
passive creatures spawn 1.5× more often and hostile monsters spawn at 25%.
Other categories (water creatures, ambient, etc.) keep vanilla weights.

The global `mob_spawn_strategy` is `isekai:identity` (no-op) — the
per-category overrides do all the work. This is the pattern for "scale
some categories but leave others alone."

## no_villages/

A NeoForge **structure** modifier (note: different registry path from biome
modifier — `neoforge/structure_modifier/`) using
`isekai_api:apply_worldshape_structures`. Removes all five village
variants by clearing their biome filter to empty.

This demonstrates the v0.9 `excluded_structures` field, the
structure-side counterpart of `excluded_features`. Use it for "I want a
worldshape where this kind of structure shouldn't spawn at all."

## biome_modifier_demo/

A NeoForge biome modifier referencing Isekai's `isekai_api:apply_worldshape`
type. Removes `minecraft:lake_lava_surface` from `minecraft:desert` biomes.

When this pack is loaded, look for log lines like
`[Isekai] removed N excluded placed features (descriptor dim=...)` at
debug level.

The JSON is wrapped in `{ "type": "isekai_api:apply_worldshape",
"worldshape": { ... } }`. Inside `worldshape`, you write the same fields
documented below for `WorldshapeDescriptor`.


## layered_overworld/

Multi-layer worldshape: a vanilla-like terrain layer at y=-64..70 plus a
floating-island layer at y=120..200, with a 4-block `blend` transition
between them. The top layer requires `solid_floor` with 4-block clearance
so structures only place on viable platforms. Demonstrates `isekai:and`,
`isekai:blend`, and the layered file format (`{dimension, layers,
transition}`).

## JSON schema reference

### WorldshapeDescriptor (single-layer)

```jsonc
{
  "dimension":              "<dimension key>",          // required
  "playable_range":         VerticalRange,              // required
  "surface_anchor":         SurfaceAnchor,              // required
  "ore_strategy":           RemapStrategy,              // required
  "structure_strategy":     RemapStrategy,              // required
  "mob_spawn_strategy":     RemapStrategy,              // required
  "structure_predicates":   { "<structure key>": SpatialPredicate, ... }, // optional, default {}
  "default_structure_predicate": SpatialPredicate,      // required
  "applies_to":             ["<biome key>", ...],       // optional, default [] (dimension-wide)
  "exclusions": {                                       // optional, default { features: [], structures: [], carvers: [] }
    "features":   ["<feature key>", ...],
    "structures": ["<structure key>", ...],
    "carvers":    ["<configured_carver key>", ...]
  },
  "mob_spawn_strategy_by_category": { "monster": RemapStrategy, "creature": RemapStrategy, ... }, // optional, default {} — overrides per-category, falls back to mob_spawn_strategy
  "additions": {                                        // optional, default { features: [], carvers: [] }
    "features": [{ "feature": "<feature key>", "step": "<decoration step>" }, ...],
    "carvers":  [{ "carver": "<configured_carver key>", "step": "air|liquid" }, ...]
  },
  "atmosphere": {                                       // optional, default {}; every sub-field is independently optional
    "has_precipitation": <bool>, "temperature": <float>, "downfall": <float>,
    "sky_color": <int>, "fog_color": <int>, "water_color": <int>, "water_fog_color": <int>,
    "foliage_color": <int>, "grass_color": <int>
  },
  "priority": <int>                                      // optional, default 100
}
```

### Sealed-interface payloads

`VerticalRange`:
```json
{ "min_y": <int>, "max_y": <int>, "distribution": "uniform|trapezoid|triangle|biased_low|biased_high" }
```

`SurfaceAnchor` dispatch on `"type"`:
- `isekai:world_surface` — vanilla heightmap
- `isekai:below_fluid` — `{ "fluid": "<fluid key>" }`
- `isekai:fixed_y` — `{ "y": <int> }`

`RemapStrategy` dispatch on `"type"`:
- `isekai:linear`, `isekai:inverted`, `isekai:identity` — no payload
- `isekai:band_split` — `{ "bands": [{ "vanilla_source": VerticalRange, "target_ratio": <float> }, ...] }`
- `isekai:fixed_range` — `{ "min": <int>, "max": <int>, "dist": "<distribution>" }`
- `isekai:count_scale` — `{ "factor": <double>=0 }`
- `isekai:pipe` — `{ "chain": [RemapStrategy, ...] }` (non-empty)

`SpatialPredicate` dispatch on `"type"`:
- `isekai:y_in_range`     — `{ "min": <int>, "max": <int> }`
- `isekai:solid_floor`    — `{ "min_clearance": <int> }`
- `isekai:solid_ceiling`  — `{ "min_clearance": <int> }`
- `isekai:terrain_slope`  — `{ "min_slope": <dbl>, "max_slope": <dbl> }`
- `isekai:near_block`     — `{ "targets": "<block key>" | ["<block key>", ...] | "#<block tag>", "max_distance": <int> }`
- `isekai:near_biome`     — `{ "biome": "<biome key>", "max_distance": <int> }`
- `isekai:in_fluid`       — `{ "fluid": "<fluid key>" }`
- `isekai:always` / `isekai:never` — no payload
- `isekai:and` — `{ "all": [SpatialPredicate, ...] }`
- `isekai:or`  — `{ "any": [SpatialPredicate, ...] }`
- `isekai:not` — `{ "inner": SpatialPredicate }`

`TransitionRule` dispatch on `"type"`:
- `isekai:hard` — no payload
- `isekai:blend` — `{ "blend_height": <int>>=0 }`
- `isekai:gap`   — `{ "gap_height": <int>>=0 }`

### Layered file (`isekai/layered_worldshape/*.json`)

```jsonc
{
  "dimension":  "<dimension key>",
  "layers": [
    {
      "y_range":    VerticalRange,
      "descriptor": WorldshapeDescriptor,
      "transition": TransitionRule
    }, ...
  ],
  "transition": TransitionRule    // optional, default {"type":"isekai:hard"} — top-level seam rule
}
```
