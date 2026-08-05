# Changelog

All notable changes to Isekai API follow this file. Format roughly follows
[Keep a Changelog](https://keepachangelog.com/). Compatibility policy — how datapack
and Java API stability are versioned — is in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## [2.1.0] — 2026-08-05

Three features that the documentation promised but the implementation never
delivered are now real: biome zones vary with the world seed, `count_scale`
thins structures, and `TransitionRule` blend/gap are evaluated.

**No Java API breaks.** Everything added is a `default` method or a new static,
so variants written against 1.x / 2.0.0 compile and run unchanged.

### Changed — datapack behaviour

- **`isekai_api:noise_threshold` and `isekai_api:edge_jitter` now vary with the
  world seed.** Their JSON `seed` field is unchanged in shape but changed in
  meaning: it was an absolute noise seed, and is now combined with the world
  seed via `BiomeZone.deriveSeed`. Two worlds made from the same datapack with
  different seeds get different patterns; the same seed reproduces. **Worlds
  created before 2.1.0 will generate a different pattern in newly explored
  chunks** — finish or retire such a world before updating.
- Combinators (`and` / `or` / `not`) propagate the world seed to their children.
  Purely geometric zones (`always`, `y_above`, `y_below`, `y_between`,
  `within_distance`, `beyond_distance`) are unaffected by seed, as before.
- **A world's terrain *shape* is not seeded by this change.** Density functions
  built only from `isekai_api:distance` / `repeat` / `add` / `max` and constants
  stay identical in every world; seed variation there comes from including a
  vanilla `minecraft:noise` term, which the world seed already drives.

### Added

- **`structure_strategy` accepts `isekai_api:count_scale`.** A factor below 1.0
  deterministically thins vanilla structures from `(world seed, chunk x, chunk z,
  structure id)`. It vetoes candidates at `Structure.findValidGenerationPoint`
  rather than touching `RandomSpreadStructurePlacement.spacing`: that placement
  object is shared by every dimension referencing the structure set, and
  `ChunkGeneratorStructureState` caches the candidate grid at world load, so
  rescaling spacing would leak across dimensions and break seed reproducibility.
  The consequence of the safer route is that structures can be **made rarer but
  not more common** — a factor above 1.0 is a validation error, not a silent
  round-down.
- **`TransitionRule.Blend` and `Gap` are evaluated** at layer boundaries.
  `Blend(b)` spreads the seam across a `b`-block band whose per-position choice
  between the two descriptors ramps from 0 to 1, turning a flat seam into a
  grainy gradient. `Gap(g)` leaves the top `g` blocks of the lower layer
  unclaimed. Blend affects a descriptor's positional output — `block_overrides`
  (`surface_top`, `default_block`) and `structure_predicates` — not terrain
  shape, and not ore/mob/feature remap (those select a layer by biome, not Y).
- `BiomeZone.withWorldSeed(long)` and `BiomeZone.deriveSeed(long, long)`.
  `withWorldSeed` defaults to returning `this`, so third-party variants keep
  working; a third-party *combinator* that does not override it will not
  propagate the seed to nested built-in noise zones.
- `IsekaiRemap.getDescriptorAt(dimension, x, y, z)` as a `default` method
  delegating to the existing Y-only form.
- A server-start warning when a rule biome source never received a world seed.

### Fixed

- `IsekaiValidator` rejected every non-identity `structure_strategy` while its
  own comment said it warned.
- The layered-worldshape reader documented a file-level `"transition"` field in
  its javadoc; `LayeredFile` has no such component and the key was silently
  discarded. The `layered_overworld` example wrote it, put `blend` on the
  topmost layer (which has nothing above it), and used a `structure_strategy`
  the validator rejects.
- `README.md` reported 3 `SurfaceAnchor` and 7 `RemapStrategy` variants; there
  are 4 and 8. Its two `BiomeZone` counts disagreed with each other (9 and 11);
  there are 11.
- `examples/2_placement/moon_world/worldshape.json` used the deprecated
  `isekai:` prefix.
- `tools/check_since.py` read `return foo(...)` inside a method body as a
  declaration.

## [2.0.0] — 2026-07-22

The rule-adaptation layer becomes an open extension point, terrain shape gets a
copy-free hook mechanism, and the library ships as a proper Gradle dependency.

**Datapacks are unaffected.** Every 1.x datapack loads unchanged: the `isekai:`
dispatch prefix is accepted as a deprecated alias (logs one warning per id), and all
existing payload schemas are byte-for-byte compatible. The breaking changes are on the
**Java API** only (see below) — this is a major bump because the SPI interfaces changed
shape, not because datapacks broke.

### Breaking (Java API)

The five in-house dispatch interfaces — `SpatialPredicate`, `RemapStrategy`, `BiomeZone`,
`SurfaceAnchor`, `TransitionRule` — are now **registry-backed extension points** instead of
`sealed` codec unions:

- `sealed` is removed from all five. Exhaustive `switch` statements over their variants no
  longer compile — handle them through their behavior methods or a `default` branch.
- `String typeId()` is **removed** from all five. To recover a variant's registered id, ask
  the registry: `registry.getKey(variant.codec())`. The registry keys live in
  `api/registry/IsekaiRegistries`.
- New abstract methods carry each interface's evaluation contract:
  `SpatialPredicate.test(EvaluationContext)`, `RemapStrategy.remap(VerticalRange, RemapContext)`,
  `SurfaceAnchor.resolveY(PlacementContext, BlockPos)`. Custom Java implementations must
  supply them.

Datapack authors and consumers who only use the built-in variants from JSON are unaffected.

### Added

- **Open SPI.** Third parties register their own variant of any of the five interfaces from
  their own mod id by adding a `MapCodec` to the matching registry in
  `api/registry/IsekaiRegistries` — no fork, no PR. See
  [docs/DATAPACK_REFERENCE.md](docs/DATAPACK_REFERENCE.md) → "Extending the SPI".
- `api/predicate/EvaluationContext` and `api/remap/RemapContext` — the world-access and
  math seams that variant `test` / `remap` implementations receive.
- **`isekai_api:hooked_overworld` preset + `hook/final_density` density function.** A new
  dimension references the preset and overrides one small hook file to change terrain shape —
  no 2500-line `noise_settings` copy. A whole floating-island world is two files.
  ([README](README.md#quick-start--a-floating-island-world-in-2-files))
- **Terrain-relative ore remap** — `isekai_api:column_local` (RemapStrategy),
  `isekai_api:world_floor` (SurfaceAnchor) and `isekai_api:column_relative` (placement
  modifier). Every other remap strategy produces one absolute Y band per feature, which can
  only ever be right for terrain at one altitude; `column_local` normalizes a feature's
  vanilla Y to a depth and resolves it against each column's own surface and underside as the
  feature is placed. Floating islands, orbiting planets and sky continents get the same
  internal ore layout whatever height they sit at. `RemapStrategy` gained a
  `remapToColumn(VerticalRange, RemapContext)` **default** method returning
  `Optional.empty()`, so every existing strategy — built-in or third-party — keeps the
  absolute behaviour unchanged.
- **`isekai_api:vanilla_overworld_surface`** — a delegate `SurfaceRules.RuleSource` that
  reproduces the vanilla overworld surface rule, so overworld-replacement datapacks skip the
  30 KB surface-rule copy.
- **Distribution as a Gradle dependency**: `sources` and `javadoc` jars, and a maven repo.
  See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md#depending-on-isekai-api).

### Changed

- **Dispatch prefix unified to `isekai_api:`.** All `"type"` values normalise to the
  `isekai_api` namespace; docs and examples use it throughout.
- Inline `apply_worldshape` / `apply_worldshape_structures` modifier forms are superseded by
  the `_ref` forms.

### Fixed

- **`isekai_api:worldshape_surface_top` self-gates to the surface layer.** Prepended bare into a
  `surface_rule` sequence it replaces only the topmost surface block; the blocks beneath keep the
  underlying surface rule's output. No `minecraft:stone_depth` wrapper is required, and wrapping it
  in one is harmless.

### Deprecated

- The bare `isekai:` dispatch prefix — accepted with a one-time warning, **removed in the
  next major**. Rewrite `"type": "isekai:*"` to `"type": "isekai_api:*"`.
- Inline modifier forms — use the `_ref` forms. Same one-major grace.

## [1.1.0] — 2026-06-02

Set-piece, vegetation, and surface tooling. Everything below is datapack-authorable
and registered under `isekai_api:`; the core language from 1.0.0 is unchanged.

### Structures
- **`isekai_api:grounded_template`** — places one hand-authored NBT template on flat,
  dry ground. Vanilla `minecraft:jigsaw` gates placement on biome only; a biome assigned
  by climate (continentalness) doesn't track the waterline or terrain steepness, so a
  jigsaw set-piece spawns half-submerged in shallow sea or tilted across a cliff. This
  structure adds the two gates vanilla can't express in a datapack: `clearance_above_fluid`
  (every footprint column must rise this many blocks above sea level) and `max_slope`
  (footprint height-spread ceiling). Placement reuses vanilla template machinery (correct
  per-chunk clamping) and honours `terrain_adaptation` (`beard_thin`). `vertical_offset`
  tunes sink/raise.
- **`isekai_api:assembled`** — a structure that places a list of `PlacedFeature`s at one
  origin (Y snapped to `WORLD_SURFACE_WG`). For a loose scatter of independent features
  near a point; not for coordinated set-pieces (use `grounded_template` or
  `minecraft:jigsaw` for those).

### Features
- **`isekai_api:cluster`** — a connected blob of N blocks grown by random walk
  (`block` provider, `size`, `can_replace_solid`).
- **`isekai_api:pool`** — carves a fluid basin, lines its floor/rim with `rim_block`, then
  fills with `fluid`, in one pass — avoiding the `waterlogged_vegetation_patch` failure
  where grass placed underwater decays to dirt. `xz_radius`, `depth`.

### Tree placers
- Trunk placers `isekai_api:branching`, `isekai_api:leaning`; foliage placers
  `isekai_api:cone`, `isekai_api:disc`, `isekai_api:fan`, `isekai_api:sphere`,
  `isekai_api:weeping`. Spread-leaf placement pins `LeavesBlock.DISTANCE` so foliage
  detached from the trunk doesn't decay.

### Density functions
- **`isekai_api:sloped_density`** — emits the vanilla `sloped_cheese` shape
  `add(mul(4, quarter_negative(mul(depth_field, factor))), base_noise)` from a neutral
  `depth_field` + `factor` + `base_noise`, encapsulating the anti-terracing knowledge
  (full-weight 3D base noise) without theme vocabulary.
- **`isekai_api:quarter_negative`** — `v > 0 ? v : v*0.25`, re-implemented because
  vanilla's `Mapped` type is package-private; used internally by `sloped_density`.

### Biome source
- **`isekai_api:climate_zones`** — assigns biomes by per-axis `Climate.Parameter` ranges
  (temperature/humidity/continentalness/erosion/weirdness/depth), first match wins, with a
  fallback. Complements `isekai_api:rule` (spatial) with a climate-space router.
- `BiomeZone` gains `noise_threshold` and `edge_jitter` variants for organic, non-straight
  zone borders (deterministic — fixed seed).

### Surface rule
- **`isekai_api:strata`** — an ordered list of `(block, thickness)` bands compiled to the
  vanilla stone-depth-check sequence, for layered surface columns.

### Placement modifiers
- **`isekai_api:scatter`** (count + radius + min-spacing rejection sampling),
  **`fluid_edge`** (distance-to-fluid filter, near/far), **`slope_filter`** (heightmap
  slope min/max), **`surface_relative`** / **`fluid_relative`** (offset from surface or
  fluid level), **`in_block_context`** (place only in a matching block context).

### Atmosphere
- `AtmosphereOverride` gains an `effects_extras` sub-record (grass-colour modifier,
  ambient particle, ambient/mood/additions sounds, music) to express full biome effects.
- **Client-side fog** via `ClientAtmosphereOverride` (`fog_color`, `fog_near_distance`,
  `fog_far_distance`) applied through `ViewportEvent` on `Dist.CLIENT`.

### Layered worldshapes (runtime)
- `LayeredDescriptor` is now resolved per-Y at runtime: surface rules, structure
  placement, and the biome/structure modifiers pick the layer that owns each block via
  `getDescriptorAt(dim, y)` — so a stacked worldshape applies the correct layer's content
  instead of silently using only the first.

### Validation & diagnostics
- World-preset structural check: warns when an authored
  `data/minecraft/worldgen/world_preset/normal.json` omits the overworld/nether/end
  dimensions (a silent break of the untouched dimensions).
- Server-start log lists every structure in the world that uses an Isekai structure type,
  confirming a consumer's `worldgen/structure/*.json` decoded and is in the live registry.

### Templates & docs
- `examples/templates/` ships annotated copy-paste starters: world-preset override,
  overworld-like biome, overworld-like dimension type, noise-settings skeleton.
- `docs/DATAPACK_REFERENCE.md` documents the set-piece path (jigsaw+NBT and
  `grounded_template`), the new primitives, and the NBT authoring/Y conventions.

### Tests
- First regression suite (JUnit 5 under moddev `unitTest`): density-function math, codec
  round-trips against bootstrapped registries, remap-strategy math, spatial-predicate
  logic, and registration coverage.

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
  `structure_strategy` accepts only `identity` in this release.

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
