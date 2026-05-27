# Isekai API — example datapacks

Each subdirectory is a self-contained datapack. To try one:

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

Inverted+linear pipe for ore remapping: deepslate-band ores get pulled
toward the surface relative to the playable range. Uses `isekai:and` to
compose two leaf predicates (`y_in_range` + `solid_ceiling`) so structures
only spawn in roofed chambers. `count_scale` 1.5x amplifies mob spawning.
`priority=110` wins ties against the default-priority skyland_minimal pack.

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

This is the only example that produces an **observable in-game effect**;
the other examples populate Isekai's runtime registry (queryable via
`/isekai query worldshape`) but don't yet alter biome generation. v0.6
biome-modifier behavior is limited to the REMOVE phase
(`excluded_features`); ADD-phase remapping for ore/structure/mob_spawn
strategies lands in v0.7.

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
  "excluded_features":      ["<feature key>", ...],     // optional, default []
  "excluded_structures":    ["<structure key>", ...],   // optional, default []
  "additional_features":    [{ "feature": "<feature key>", "step": "<decoration step>" }, ...], // optional, default []
  "priority":               <int>                        // optional, default 100
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
- `isekai:band_split` — `{ "ratios": [<float>, ...] }`
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
