# Changelog

All notable changes to Isekai API follow this file. Format roughly follows
[Keep a Changelog](https://keepachangelog.com/).

## [1.0.0] — 2026-05-29

First public release. A universal worldgen library for NeoForge 1.21.1: a small,
complete language of neutral composable primitives for expressing any worldshape —
terrain geometry, biome distribution, new biomes, new dimensions, atmosphere,
surface blocks, and structures.

### Density function primitives
- 16 neutral primitives, registered under `isekai_api:` and usable from
  `noise_settings` JSON or Java: `constant`, `coordinate`, `add`, `multiply`,
  `negate`, `abs`, `clamp`, `min`, `max`, `lerp`, `step`, `distance`,
  `translate`, `scale_coord`, `repeat`, `mask_y_range`.

### Worldshape composers
- 4 neutral density composers that build common terrain patterns from the
  primitives without leaking consumer-theme names:
  - **`isekai_api:squeeze`** — vanilla `squeeze` tone-mapping, re-implemented
    because `DensityFunctions.Mapped.Type` is package-private and unreachable.
  - **`isekai_api:y_envelope`** — pure Y-axis mask (1 inside `[active_min_y,
    active_max_y]`, 0 outside, `gradient_width` linear transition; `invert`
    flips polarity).
  - **`isekai_api:blended_noise`** — `old_blended_noise` wrapper with
    `xz_scale`/`y_scale` fixed at 0.25 and intuitive `size_xz`/`size_y` knobs.
  - **`isekai_api:band_density`** — composer that takes any noise source plus an
    active Y-band and returns the fully wrapped density tree with the standard
    outer offsets.

### Biome source (`isekai_api:rule`)
- A `BiomeSource` that assigns biomes by evaluating `BiomeZone` rules in
  declaration order; the first matching entry's biome wins. This is the "where
  do biomes go" layer — vertical layering, concentric rings, half-and-half
  regions, all expressible from datapack.
- `BiomeZone` is a sealed dispatch family with 9 variants: `always`, `y_above`,
  `y_below`, `y_between`, `within_distance`, `beyond_distance`, `and`, `or`,
  `not`. Coordinates are authored in block space; the evaluator converts the
  quart positions it receives.

### Surface block overrides (`SurfaceRules.RuleSource`)
- **`isekai_api:worldshape_surface_top`** — per-biome top-block override read
  from the worldshape's `content_overrides.block_overrides.surface_top`.
- **`isekai_api:worldshape_default_block`** — per-biome default-block (stone)
  re-skin from `content_overrides.block_overrides.default_block`.
- Consumer wires either rule into their dimension's `surface_rule` sequence and
  declares `block_overrides` in their worldshape JSON.

### New dimensions
- New dimensions are pure datapack: `dimension/` + `dimension_type/` +
  `worldgen/noise_settings/`, combined with the `isekai_api:rule` biome source
  and `isekai_api:band_density`. No Java required.

### Biome / structure modifiers
- **`isekai_api:apply_worldshape`** — applies an entire `WorldshapeDescriptor`
  as a single biome modifier entry. Three phases:
  - **REMOVE**: drops `exclusions.features` / `carvers` / `mob_spawns` and any
    strategy-targeted PlacedFeatures pending remap (scoped to features that
    originally lived in the matched biome — never re-injects features that
    weren't there).
  - **ADD**: injects `additions.features` / `carvers` and strategy-remapped
    PlacedFeatures, step-preserving (rebuilt features land in their original
    decoration step).
  - **MODIFY**: mob spawn entry add/remove, per-category weight scaling via
    `mob_spawn_strategy` and `mob_spawn_strategy_by_category`, and `atmosphere`
    overrides (sky/fog/water/foliage/grass colors, temperature, downfall,
    has_precipitation, creature_generation_probability, per-entity
    `mob_spawn_costs`).
- **`isekai_api:apply_worldshape_ref`** — same behavior, but points at a
  dimension and looks up the worldshape from
  `data/<ns>/isekai/worldshape/<name>.json` at modify-time. Modifier files shrink
  to ~4 lines and the worldshape is declared once (single source of truth).
- **`isekai_api:apply_worldshape_structures`** — structure-side modifier.
  REMOVE phase clears matched structures' biome filter (from
  `exclusions.structures`) to make them unspawnable; MODIFY phase applies
  per-(structure, MobCategory) spawn overrides from `structure_spawn_overrides`,
  bounded by `piece` or `full` scope, with `replace` controlling whether the
  existing override is cleared first.
- **`isekai_api:apply_worldshape_structures_ref`** — reference-based variant of
  the structure modifier.

### Content overrides
- `WorldshapeDescriptor.content_overrides` bundles `feature_predicates`,
  `structure_spawn_overrides`, and `block_overrides`:
  - `feature_predicates` maps a placed-feature key to a `SpatialPredicate`;
    matched features are rebuilt with a prepended `isekai_api:spatial_predicate`
    placement modifier (e.g. "lake only where there's solid floor with 3+ block
    clearance").
  - `structure_spawn_overrides` replaces or augments a structure's mob spawn
    entries (e.g. "no creepers in pillager outposts").
  - `block_overrides` feeds the two surface rules above.

### BiomeSelection
- `applies_to` accepts **either** a list of biome keys **or** an object
  `{"keys": [...], "tags": ["#minecraft:is_overworld"]}`. Tags collapse 35-entry
  biome enumerations to a single line.

### Sealed dispatch codec families
- 5 in-house sealed-interface codecs with full JSON encodability, each
  dispatched by a `"type"` field:
  - `SpatialPredicate` (12 variants), `RemapStrategy` (7 variants),
    `SurfaceAnchor` (3 variants), `TransitionRule` (3 variants),
    `BiomeZone` (9 variants).
- `WorldshapeDescriptor` + `LayeredDescriptor` codecs for declarative
  worldshape JSON.

### Vanilla rule extraction (IsekaiQuery)
- Walks `PLACED_FEATURE`, `STRUCTURE_SET` / `STRUCTURE`, and `BIOME` registries
  at server start.
- Extracts `VerticalRange` from all 6 `HeightProvider` variants via Access
  Transformer-exposed fields.
- Per-dimension VerticalRange overrides for relative-anchor features.
- Tag indices for placed features and structures.
- Per-biome mob spawn lookup, per-category mob spawn aggregation.
- Snapshot rebuilds on every datapack reload.

### Validation
- `RegistryRefChecker` validates at server start that every
  `ResourceKey<Biome/Structure/PlacedFeature/ConfiguredCarver>` and `TagKey<Biome>`
  referenced in an active worldshape resolves in the live registry (WARN at
  `ServerAboutToStartEvent`). Catches typos like `minecraft:ocean_monument`
  (real key: `minecraft:monument`) before chunk generation silently no-ops.
- Every consumer's `isekai/` datapack directory is auto-validated at server
  start, reporting typos / decode failures / cross-field invariants.
- `-Disekai.strict=true` aborts datapack reload on any decode error.

### Mixins
- `Structure.findValidGenerationPoint` — enforces all 12 `SpatialPredicate`
  variants at structure placement time. Per-structure predicates from
  `structure_predicates` are honored; absent structures fall back to
  `defaultStructurePredicate`.
- `RandomSpreadStructurePlacement.spacing` / `.separation` — per-dimension
  scaling by the descriptor's `structure_strategy` CountScale factor.

### Commands (operator-only)
- `/isekai version`, `stats`, `reload`
- `/isekai query dimensions`, `query worldshape <dim>`,
  `query atmosphere <biome>`
- `/isekai validate <namespace>`
- `/isekai preview range <id> [dim]`, `preview column <dim> <x> <z>`
- `/isekai dump worldgen` (writes file), `dump ore <id>`, `dump structure <id>`

### Examples
- `sky_archipelago/`, `flipped/`, `moon_world/` — 3-file consumer mod skeletons.
- `runtime_effects/biome_modifier_demo` — strips lava lakes from desert.
- `runtime_effects/no_villages` — disables all village variants.
- `runtime_effects/peaceful_plains` — per-category mob spawn (creature 1.5×,
  monster 0.25×) in plains.
- `declaration_only/skyland_minimal` — minimal valid JSON.
- `declaration_only/underground_only` — Pipe + AND predicates.
- `declaration_only/layered_overworld` — two-layer + blend transition.

### Known limitations
- `noise_settings` are still hand-authored by consumers; Isekai does not
  auto-inject a `final_density` overlay into vanilla `noise_settings` JSON.
- `structure_strategy` non-CountScale variants (Linear / Inverted / FixedRange /
  BandSplit) have no semantic meaning for structure placement frequency; the
  validator rejects them rather than letting the descriptor silently no-op.
- Gameplay-level effects (gravity, time-of-day) are out of scope — Isekai shapes
  worldgen, not runtime game mechanics.
