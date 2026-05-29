# Isekai API — example datapacks

## Quick-start consumer mod skeletons

`sky_archipelago/` and `flipped/` are minimal-config consumer mod skeletons that ship a working worldshape in **3 files of ~10 lines each**. They demonstrate:

- `isekai_api:apply_worldshape_ref` / `isekai_api:apply_worldshape_structures_ref` — the 4-line biome and structure modifier files that point at a worldshape declared once under `isekai/worldshape/<name>.json` (no duplication)
- `isekai_api:band_density` + `isekai_api:blended_noise` + `isekai_api:squeeze` — drop-in `final_density` for `data/minecraft/worldgen/noise_settings/overworld.json` that produces floating-island terrain. `band_density.invert: true` for hanging/flipped terrain.
- `content_overrides.feature_predicates` — gate lake placement on `solid_floor` clearance so water lakes don't spawn on cliff edges and leak waterfalls into the void
- `content_overrides.block_overrides.surface_top` / `default_block` — per-biome surface or fill-block override via `isekai_api:worldshape_surface_top` and `isekai_api:worldshape_default_block` surface rules

For a new consumer mod, prefer the `*_ref` modifiers (worldshape declared once). The inline `apply_worldshape` modifier also works but duplicates the descriptor across files.

`moon_world/` uses biome-tag `applies_to` instead of a 35-entry list, plus `block_overrides.surface_top` and `default_block` to re-skin every overworld biome's surface and stone fill. It wires the two `isekai_api:worldshape_*` surface rules into the noise_settings surface_rule sequence.

## Detailed examples (also runtime modifiers + declaration-only descriptors)

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

Demonstrates `mob_spawn_strategy_by_category`. In `minecraft:plains`,
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

This demonstrates the `exclusions.structures` field, the
structure-side counterpart of `exclusions.features`. Use it for "I want a
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
  "applies_to":             ["<biome key>", ...],       // optional, default [] — accepts a list of biome keys OR an object { "keys": ["<biome key>", ...], "tags": ["#<biome tag>", ...] }. Empty matches no biome; you MUST list at least one biome or tag for the descriptor to apply (BiomeModifier has no dimension scope, so "empty = all" would silently cross dimensions)
                            // example object form: "applies_to": { "keys": [...], "tags": ["#minecraft:is_overworld"] }
  "exclusions": {                                       // optional, default all-empty
    "features":   ["<feature key>", ...],
    "structures": ["<structure key>", ...],
    "carvers":    ["<configured_carver key>", ...],
    "mob_spawns": ["<entity_type key>", ...]            // mob entries with these types are dropped before re-scaling
  },
  "mob_spawn_strategy_by_category": { "monster": RemapStrategy, "creature": RemapStrategy, ... }, // optional, default {} — overrides per-category, falls back to mob_spawn_strategy
  "additions": {                                        // optional, default all-empty
    "features":   [{ "feature": "<feature key>", "step": "<decoration step>" }, ...],
    "carvers":    [{ "carver": "<configured_carver key>", "step": "air|liquid" }, ...],
    "mob_spawns": [{ "category": "<MobCategory>", "type": "<entity_type key>",
                     "weight": <int>=1, "min_count": <int>=1, "max_count": <int>=1 }, ...]
  },
  "atmosphere": {                                       // optional, default {}; every sub-field is independently optional
    "has_precipitation": <bool>, "temperature": <float>, "downfall": <float 0..1>,
    "sky_color": <int>, "fog_color": <int>, "water_color": <int>, "water_fog_color": <int>,
    "foliage_color": <int>, "grass_color": <int>,
    "creature_generation_probability": <float 0..1>,    // per-biome scalar for passive-creature chunk population
    "mob_spawn_costs": { "<entity_type key>": { "energy_budget": <double>, "charge": <double> }, ... }
  },
  "content_overrides": {                                 // optional, default all-empty
    "feature_predicates": { "<placed_feature key>": SpatialPredicate, ... }, // optional, default {} — wrap a feature's placement so it only spawns where the predicate holds
    "structure_spawn_overrides": [                       // optional, default []
      { "structure": "<structure key>", "category": "<MobCategory>",
        "bounding_box": "piece|full",
        "spawns": [{ "category": "<MobCategory>", "type": "<entity_type key>",
                     "weight": <int>=1, "min_count": <int>=1, "max_count": <int>=1 }, ...],
        "replace": <bool>                                // optional, default true — clear existing override before injecting
      }, ...
    ],
    "block_overrides": {                                 // optional, default {} — read by the isekai_api:worldshape_surface_top / worldshape_default_block surface rules
      "surface_top":   { "<biome key>": "<block key>", ... },
      "default_block": { "<biome key>": "<block key>", ... }
    }
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
