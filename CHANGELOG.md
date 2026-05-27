# Changelog

All notable changes to Isekai API follow this file. Format roughly follows
[Keep a Changelog](https://keepachangelog.com/); versions before 1.0.0 are
omitted because they were development-only and not released.

## [1.0.0] — 2026-05-28

First public release. Universal worldgen library for NeoForge 1.21.1.

### Core API
- 16 neutral density function primitives (`constant`, `coordinate`, `add`,
  `multiply`, `negate`, `abs`, `clamp`, `min`, `max`, `lerp`, `step`,
  `distance`, `translate`, `scale_coord`, `repeat`, `mask_y_range`).
- 4 sealed-interface dispatch codecs with full JSON encodability:
  `SpatialPredicate` (12 variants), `RemapStrategy` (9 variants),
  `SurfaceAnchor` (3 variants), `TransitionRule` (3 variants).
- `WorldshapeDescriptor` + `LayeredDescriptor` codecs for declarative
  worldshape JSON.

### Vanilla rule extraction (IsekaiQuery)
- Walks `PLACED_FEATURE`, `STRUCTURE_SET` / `STRUCTURE`, and `BIOME`
  registries at server start.
- Extracts `VerticalRange` from 6/6 `HeightProvider` variants via Access
  Transformer-exposed fields.
- Per-dimension VerticalRange overrides for relative-anchor features.
- Tag indices for placed features and structures.
- Per-biome mob spawn lookup, per-category mob spawn aggregation.
- Snapshot rebuilds on every datapack reload.

### Biome modifier (`isekai_api:apply_worldshape`)
- REMOVE phase: drops `exclusions.features`, `exclusions.carvers`, and any
  ore-step PlacedFeatures pending strategy remap.
- ADD phase: injects `additions.features`, `additions.carvers`, and
  strategy-remapped ore PlacedFeatures (step-preserving — rebuilt features
  land in their original decoration step).
- MODIFY phase:
  - mob spawn entry add (`additions.mob_spawns`) and remove
    (`exclusions.mob_spawns`)
  - per-category weight scaling via `mob_spawn_strategy` and
    `mob_spawn_strategy_by_category`
  - `atmosphere` overrides: sky/fog/water/foliage/grass colors,
    temperature, downfall, has_precipitation,
    creature_generation_probability, per-entity `mob_spawn_costs`
    (energy budget / charge for spawn-cap tuning)

### Structure modifier (`isekai_api:apply_worldshape_structures`)
- REMOVE phase: clears matched structures' biome filter (from
  `exclusions.structures`) to make them unspawnable.
- MODIFY phase: applies per-(structure, MobCategory) spawn overrides
  from `structure_spawn_overrides` — bounded by `piece` or `full`
  bounding-box scope, with `replace=true` clearing the existing
  override before injecting consumer spawns.

### Mixins
- `Structure.findValidGenerationPoint` — enforces all 12
  `SpatialPredicate` variants at structure placement time.
  Per-structure predicates from `structure_predicates` honored;
  default falls back to `defaultStructurePredicate`.
- `RandomSpreadStructurePlacement.spacing` / `.separation` — per-dimension
  scaling by the descriptor's `structure_strategy` CountScale factor.
  Uses a ThreadLocal<ChunkGeneratorStructureState> set/cleared by
  `isPlacementChunk` to resolve the calling dim via BiomeSource match.

### Commands (operator-only)
- `/isekai version`, `stats`, `reload`
- `/isekai query dimensions`, `query worldshape <dim>`
- `/isekai validate <namespace>` — codec + cross-field validation for
  `data/<ns>/isekai/*.json`
- `/isekai preview range <id> [dim]` — overworld + per-dim VerticalRange
- `/isekai dump worldgen` (writes file), `dump ore <id>`, `dump structure <id>`

### Configuration
- `-Disekai.strict=true` aborts datapack reload on any decode error.

### Examples
- `runtime_effects/biome_modifier_demo` — strips lava lakes from desert
- `runtime_effects/no_villages` — disables all village variants
- `runtime_effects/peaceful_plains` — per-category mob spawn (creature
  1.5×, monster 0.25×) in plains
- `declaration_only/skyland_minimal` — minimal valid JSON
- `declaration_only/underground_only` — Pipe + AND predicates
- `declaration_only/layered_overworld` — two-layer + blend transition

### Known limitations
- `structure_strategy` non-CountScale variants (Linear / Inverted /
  FixedRange / BandSplit) have no semantic meaning for structure
  placement frequency; the validator rejects them rather than letting
  the descriptor silently no-op.
- Surface rules, sea level, BiomeSource — explicitly out of scope
  (these are vanilla `noise_settings` JSON or TerraBlender territory).
