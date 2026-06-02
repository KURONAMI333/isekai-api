# Isekai API

> Build any world from datapack — a toolbox of neutral worldgen primitives for shape, biomes, dimensions, and re-placing existing content. NeoForge 1.21.1.
> **Unrelated to the *Isekai Adventure* modpack.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net)

---

## What is Isekai API?

Isekai API is a toolbox for the *shape and rules* of a Minecraft world — not a world, but the machine that makes worlds.

Think of it the way HTML's `div`/`span` are primitives for any web page, not a tool for one specific site. Isekai gives you a small set of neutral, composable primitives — terrain shape, biome placement, surface blocks, new dimensions, atmosphere, placement control, and re-placement of existing content — and you combine them to express *any* worldshape you can imagine: floating islands, submerged worlds, hollow earths, archipelagos, mesa canyons, fully inverted Y axes, anything.

It ships no biomes, structures, or themes of its own. **Built to be built on — however you want to use it, you're welcome:** datapack worldshapes, full Java mods, modpack glue, quick experiments, total conversions. The same primitives power everything; there's no privileged "first-party" path.

## Quick start

Pick your path — most worldgen work needs no Java at all.

**Datapack author (no Java).** Your whole vocabulary is the set of `isekai_api:` / `isekai:` keys, fully listed in **[docs/DATAPACK_REFERENCE.md](docs/DATAPACK_REFERENCE.md)**. The fastest start is to copy a skeleton from [`examples/`](examples/):

1. Copy `examples/sky_archipelago/` (floating islands), `flipped/` (hanging continent), or `moon_world/` (re-skinned blocks + tag biome selection) into your mod's `src/main/resources/data/<modid>/`.
2. Rename the placeholder paths to your mod id.
3. Add `isekai_api` as a dependency in your `neoforge.mods.toml`.
4. Launch — the worldshape applies on world create.

**Java modder.** Add `isekai_api` as a `compileOnly` dependency, then use the facade — `Isekai.query()` to read vanilla worldgen rules and `Isekai.remap()` to declare a worldshape (see [Java facade](#java-facade)). Everything outside the `com.kuronami.isekaiapi.api` package is marked `@ApiStatus.Internal`; you only ever touch the `api` package.

## What it gives you

### 17 neutral density function primitives + 5 worldshape composers

Registered under the `isekai_api:` namespace (mod id), usable from `noise_settings` JSON or Java:

| Category | Primitives |
|---|---|
| Value sources | `constant`, `coordinate` (axis x/y/z) |
| Arithmetic | `add`, `multiply`, `negate`, `abs` |
| Mapping | `quarter_negative` (vanilla `quarter_negative` re-exposed — `Mapped.Type` is package-private) |
| Range | `clamp` |
| Combinators | `min`, `max`, `lerp`, `step` |
| Spatial reference | `distance` (mode xz / xyz) |
| Coordinate transforms | `translate`, `scale_coord` (negative factors mirror the axis), `repeat` (XZ-plane tiling) |
| Masks | `mask_y_range` |
| **Worldshape composers** | `squeeze`, `y_envelope`, `blended_noise`, `band_density`, `sloped_density` |

The four worldshape composers build common terrain patterns without leaking consumer-theme names into the API:

- **`squeeze`** — vanilla tone-mapping (`x/2 - x^3/24` clamped to [-1, 1]). Re-implementation because `DensityFunctions.Mapped.Type` is package-private and unreachable from outside.
- **`y_envelope(active_min_y, active_max_y, gradient_width, invert)`** — pure Y-axis mask. Returns 1 inside the active band, 0 outside, linear ramp through `gradient_width`. `invert: true` flips polarity (1 outside, 0 inside) — foundation for hollow-shell / inverted worlds.
- **`blended_noise(size_xz, size_y, smear_multiplier)`** — `old_blended_noise` wrapper with `xz_scale`/`y_scale` fixed at 0.25. Bigger `size_xz` = chunkier terrain blobs; bigger `size_y` = taller ones.
- **`band_density(active_min_y, active_max_y, gradient_width, invert, noise)`** — composer that takes any noise source and an active Y-band, wraps it in the standard `(-0.05 + -0.1 + mul(y_envelope, ...) + ...)` tree, then `blend_density` + `interpolated`. Drop in your noise via the `noise` field; wrap the result in `minecraft:squeeze` for the final tone.

Vanilla density functions stay accessible via standard `minecraft:` keys — Isekai does not re-export them. Compose Isekai primitives with vanilla density via standard density function references.

### Biome sources

Density functions decide the *shape* of terrain; the biome source decides *which biome goes where*. Isekai offers two complementary biome sources — both go in a dimension's `biome_source` field, pick whichever fits.

**`isekai_api:rule`** — places biomes by spatial `BiomeZone` rules (Y bands, radial distance, noise threshold, edge jitter, combinators), evaluated in declaration order; first match wins. Suited to layered / radial / explicitly-shaped worlds. Reference it from a dimension's `biome_source` field:

```jsonc
// data/<ns>/dimension/<name>.json → generator.biome_source
{
  "type": "isekai_api:rule",
  "rules": [
    { "biome": "minecraft:plains",     "zone": { "type": "isekai:y_above", "y": 100 } },
    { "biome": "minecraft:deep_dark",  "zone": { "type": "isekai:y_below", "y": 0 } },
    { "biome": "minecraft:basalt_deltas", "zone": { "type": "isekai:always" } }   // catch-all last
  ]
}
```

`BiomeZone` is a neutral spatial condition evaluated at biome-grid resolution (one sample per 4 blocks, coordinates only — biome assignment happens before terrain exists). It dispatches on `"type"` with 9 variants:

| Variant | Payload | Matches |
|---|---|---|
| `isekai:always` | — | everywhere (use as the catch-all last entry) |
| `isekai:y_above` | `{ y }` | block Y ≥ y |
| `isekai:y_below` | `{ y }` | block Y < y |
| `isekai:y_between` | `{ min, max }` | min ≤ block Y < max |
| `isekai:within_distance` | `{ radius, [center_x], [center_z] }` | XZ distance from center ≤ radius |
| `isekai:beyond_distance` | `{ radius, [center_x], [center_z] }` | XZ distance > radius |
| `isekai:and` | `{ all: [...] }` | all inner zones match |
| `isekai:or` | `{ any: [...] }` | any inner zone matches |
| `isekai:not` | `{ inner }` | inner zone does not match |

This makes arbitrary biome distributions expressible from datapack: vertical layers, concentric rings, half-and-half regions, etc.

**`isekai_api:climate_zones`** — places biomes by matching the vanilla climate axes (temperature / humidity / continentalness / erosion / weirdness / depth) against per-rule range constraints. Same purpose as vanilla `minecraft:multi_noise` but each rule lists only the axes it cares about (others default to no constraint), and order is explicit (first match wins) instead of vanilla's nearest-point matching. Suited to Overworld-style multi-biome worlds where biome choice is climate-driven rather than position-driven.

### New dimensions

A new dimension is pure datapack — no Java. Combine `dimension/` + `dimension_type/` + `worldgen/noise_settings/` with the `isekai_api:rule` biome source (which biomes) and `isekai_api:band_density` (terrain shape). New biomes are likewise plain `worldgen/biome/` JSON, placed by the rule biome source.

If you're **overriding** vanilla's `minecraft:normal` world_preset to customise the overworld instead of adding a new dimension, you must re-declare the Nether and End stanzas verbatim — leaving them out silently breaks those dimensions. The Isekai validator warns on server start when this trap fires; see [`examples/templates/world_preset_normal_override.json`](examples/templates/world_preset_normal_override.json) for an annotated copy-paste starting point.

### Per-biome surface block overrides

Two `SurfaceRules.RuleSource` types let you re-skin per-biome blocks without writing surface rule trees by hand:

- **`isekai_api:worldshape_surface_top`** — overrides the top block of matched biomes (e.g. "in moon biomes, top with moon_regolith instead of grass") from the worldshape's `block_overrides.surface_top` map.
- **`isekai_api:worldshape_default_block`** — overrides the default block (the stone-equivalent bulk fill) from `block_overrides.default_block`.

Wire either rule into your dimension's `surface_rule` sequence — surface_top should come first, default_block should come last (after vanilla rules) so the surface band stays intact and only sub-surface stone gets remapped.

A third rule, **`isekai_api:strata`**, takes an ordered list of `{block, thickness}` bands measured downward from the floor surface — collapses an N-layer nested `stone_depth` sequence (e.g. sand → sandstone → stone) into one flat list.

### Tree placers — neutral, decay-safe geometry

Used as the `trunk_placer` / `foliage_placer` of a vanilla `minecraft:tree` configured feature. The wood/leaf blocks come from the feature's own `trunk_provider` / `foliage_provider` slots — the placer only decides geometry. Compose any trunk with any foliage. Spread leaves are placed with their `LeavesBlock.DISTANCE` pinned internally so the tree never decays.

| | Placers |
|---|---|
| Trunks | `leaning` (palm/wind-bent, optional sandy beach mode, optional single-tip crown), `branching` (vertical + N upward branches, one crown per branch tip) |
| Foliage | `sphere` (ellipsoid), `fan` (palm head), `cone` (linear or concave taper — conifer), `disc` (flat umbrella), `weeping` (stacked discs + hanging strands — willow) |

See [docs/DATAPACK_REFERENCE.md](docs/DATAPACK_REFERENCE.md#tree-placers-isekai_api) for the full field list and a minimal palm example.

### Placement modifiers

Beyond vanilla `count`/`in_square`/`rarity_filter`/etc.:

- **`isekai_api:surface_relative`** / **`fluid_relative`** — anchor to the world surface or the water column top/bottom + offset.
- **`isekai_api:in_block_context`** — gate placement on the surrounding block context (match a block/tag, optionally require air above, optionally exclude in fluid).
- **`isekai_api:spatial_predicate`** — gate placement on any `SpatialPredicate` (Y range, slope, near block, near biome…).
- **`isekai_api:scatter`** — jitter the input into N samples within a radius, optionally rejecting any sample within `min_spacing` blocks of an already-accepted one. Replaces `count + in_square` whenever you want clustered features that don't stack on each other.
- **`isekai_api:fluid_edge`** — accept positions within (or beyond) `max_distance` blocks of a fluid. Pure distance filter — use for placements that need a water-adjacent context without baking "shoreline" into the API.
- **`isekai_api:slope_filter`** — accept positions whose local heightmap slope falls in `[min, max]`. 0 = flat, 1 ≈ 45°+ cliff.

### Features — neutral geometric primitives

Two `Feature<?>` types for the patterns consumers hit constantly:

- **`isekai_api:cluster`** — random-walk BFS blob of N connected blocks. Use for moss patches, dirt veins, fungus spreads, ore clusters.
- **`isekai_api:pool`** — carves a disc into terrain, lines floor + outer rim with `rim_block`, fills with `fluid`. Dodges the `waterlogged_vegetation_patch` grass→dirt drowning trap.

### Structures — set-pieces

Two Structure types, both datapack-only (zero Java), both getting `/locate`, biome filtering, generation-step ordering, and `structure_set` spacing for free:

- **`isekai_api:grounded_template`** — places a hand-authored NBT template on flat, dry ground. Vanilla `minecraft:jigsaw` gates placement on biome alone, so a biome assigned by climate doesn't track the waterline or terrain steepness and an NBT set-piece spawns half-submerged in shallow sea or tilted across a cliff. This adds the two gates vanilla can't express in a datapack — `clearance_above_fluid` (every footprint column must clear sea level) and `max_slope` (the footprint must be level enough) — while reusing vanilla template machinery (correct per-chunk placement) and honouring `terrain_adaptation: beard_thin`. The right tool for a coordinated landmark whose blocks must line up — an oasis (pool + beach + clustered trees), a ruin, a well.
- **`isekai_api:assembled`** — places a list of `PlacedFeature`s at one origin (Y snapped to the live surface). For a *loose scatter* of independent features near a point (a few boulders and bushes) — **not** coordinated set-pieces, since each feature re-decides its own placement. Reach for `grounded_template` (above) or vanilla `minecraft:jigsaw` for those.

For the canonical coordinated-set-piece workflow (hand-authored NBT + `minecraft:jigsaw` with `beard_thin`), see [docs/DATAPACK_REFERENCE.md](docs/DATAPACK_REFERENCE.md) → "set-pieces".

### Per-biome atmosphere overrides

The worldshape descriptor's `atmosphere` field overrides every per-biome `BiomeSpecialEffects` value plus climate axes — colours (sky / fog / water / water_fog / foliage / grass), precipitation / temperature / downfall, and (under `effects_extras`) grass-colour algorithm, ambient particles, ambient loop / mood / additions sound, and background music. Plus spawning tunables (creature generation probability, per-entity mob spawn cost). Each field is independently optional — only set what you want to change.

This covers the whole server-side atmosphere surface.

### Client-side rendering — fog overrides

In addition to the per-biome `atmosphere` field, the worldshape descriptor has a `client_atmosphere` field for **dimension-wide rendering overrides** that apply via NeoForge's `ViewportEvent` hooks at the camera's current position. Currently exposes:

- `fog_color` — overrides the rendered fog colour dimension-wide (or per layer, in a layered worldshape, resolved by camera Y)
- `fog_near_distance` / `fog_far_distance` — fog gradient band, in blocks

Sky / cloud / weather / sun / moon rendering remain hardcoded by vanilla; reaching those requires a `DimensionSpecialEffects` subclass + client mixin (not currently shipped). For sky colour, use the per-biome `atmosphere.sky_color` — same visual effect, biome-driven.

### `BiomeSelection` with tag support

`applies_to` accepts either a list of biome keys or an object with both keys and tags:

```jsonc
// list form
"applies_to": ["minecraft:plains", "minecraft:forest", ...]

// tag form — collapse 35 biome entries to one tag
"applies_to": { "tags": ["#minecraft:is_overworld"] }
```

### Rule adaptation layer (sealed dispatch codecs)

Five in-house sealed interfaces let you adapt vanilla / modded rules to your worldshape — composable, no specific worldshape committed to the API surface. Each dispatches via a `"type"` field in JSON; the dispatch keys use the bare `isekai:` prefix (these are in-house codecs, not registry-backed types):

- **`SpatialPredicate`** (12 records) — `YInRange` / `SolidFloor` / `SolidCeiling` / `TerrainSlope` / `NearBlock` (HolderSet, tag-aware) / `NearBiome` / `InFluid` / `Always` / `Never` plus combinators `And` / `Or` / `Not`. Compose arbitrary structure placement conditions.
- **`RemapStrategy`** (7 variants) — `Identity` / `Linear` / `Inverted` / `FixedRange` / `CountScale` / `BandSplit(List<Band>)` / `Pipe(List<RemapStrategy>)`. Map vanilla Y bands and feature counts onto your playable range. Every variant is JSON-encodable.
- **`SurfaceAnchor`** (3 variants) — `WorldSurface` / `BelowFluid(fluid)` / `FixedY(y)`. Defines what "the surface" means in your worldshape.
- **`TransitionRule`** — `Hard` / `Blend(blend_height)` / `Gap(gap_height)` for multi-layer worldshapes.
- **`BiomeZone`** (11 variants) — the biome-placement conditions used by the `isekai_api:rule` biome source (see above): `always` / `y_above` / `y_below` / `y_between` / `within_distance` / `beyond_distance` / `noise_threshold` (organic noise mask) / `edge_jitter` (perturbs an inner zone's borders with a small noise offset) plus combinators `and` / `or` / `not`.

### Biome / Structure modifier integration

Datapack consumers don't need to write any Java. Drop a worldshape descriptor inside a NeoForge biome modifier and it takes effect at chunk gen:

```jsonc
// data/<ns>/neoforge/biome_modifier/my_world.json
{
  "type": "isekai_api:apply_worldshape",
  "worldshape": {
    "dimension": "minecraft:overworld",
    "playable_range": { "min_y": 80, "max_y": 200, "distribution": "uniform" },
    "surface_anchor": { "type": "isekai:fixed_y", "y": 150 },
    "ore_strategy": { "type": "isekai:linear" },
    "structure_strategy": { "type": "isekai:identity" },
    "mob_spawn_strategy": { "type": "isekai:identity" },
    "mob_spawn_strategy_by_category": {
      "creature": { "type": "isekai:count_scale", "factor": 1.5 },
      "monster":  { "type": "isekai:count_scale", "factor": 0.25 }
    },
    "default_structure_predicate": {
      "type": "isekai:y_in_range", "min": 80, "max": 200
    },
    "applies_to": ["minecraft:plains"],
    "exclusions": {
      "features": [],
      "structures": [],
      "carvers": [],
      "mob_spawns": []
    },
    "additions": {
      "features": [],
      "carvers": [],
      "mob_spawns": []
    }
  }
}
```

This causes the biome modifier's three phases to:
- **REMOVE** features in `exclusions.features` (plus carvers / structures / mob_spawns in the corresponding sub-fields) plus the original strategy-targeted features pending remap.
- **ADD** features in `additions.features` (plus carvers / mob_spawns) plus remap-derived variants of the original placed features (with new Y ranges per `ore_strategy`).
- **MODIFY** mob spawn weights per the (optionally per-category) strategy, and apply any `atmosphere` overrides (temperature, downfall, colors, etc.).

Structure removal goes through a parallel `isekai_api:apply_worldshape_structures` modifier under `data/<ns>/neoforge/structure_modifier/`.

See `examples/` for six runnable example datapacks and full JSON schema documentation.

### Java facade

```java
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;

// Read vanilla + modded worldgen rules
Isekai.query().getAllPlacedFeatures();
Isekai.query().getPlacedFeaturesByTag(BiomeTags.IS_OVERWORLD);  // example
Isekai.query().getPlacedFeatureVerticalRangeInDimension(featureKey, Level.NETHER);
Isekai.query().getMobsByCategory(MobCategory.MONSTER);

// Declare a worldshape from Java (alternative to datapack JSON)
Isekai.remap().declareWorldshape(WorldshapeDescriptor.builder()
    .dimension(Level.OVERWORLD)
    .playableRange(new VerticalRange(80, 200, HeightDistribution.UNIFORM))
    .surfaceAnchor(new SurfaceAnchor.FixedY(150))
    .oreStrategy(new RemapStrategy.Linear())
    .structureStrategy(new RemapStrategy.Identity())
    .mobSpawnStrategy(new RemapStrategy.Identity())
    .defaultStructurePredicate(new SpatialPredicate.YInRange(80, 200))
    .priority(110)
    .build());
```

Optional fields (`appliesTo`, `exclusions`, `additions`, `atmosphere`,
`structurePredicates`, `mobSpawnStrategyByCategory`) default to empty / EMPTY when
omitted from the builder chain.

### Commands

| Command | Effect |
|---|---|
| `/isekai version` | Print mod version |
| `/isekai reload` | Trigger `server.reloadResources(...)` |
| `/isekai stats` | Snapshot health check (feature / structure / spawn counts) |
| `/isekai query dimensions` | List dimensions with declared worldshape |
| `/isekai query worldshape <dim>` | Inspect a single dimension's declaration |
| `/isekai query atmosphere <biome>` | Report a biome's resolved sky/fog/water/foliage/grass colors (decimal + hex) |
| `/isekai validate <namespace>` | Validate every `isekai/*.json` under that namespace |
| `/isekai preview range <id> [dim]` | Show overworld-resolved + per-dim VerticalRange |
| `/isekai preview column <dim> <x> <z>` | Sample the base column at (x, z) and dump the block at each playable-band Y |
| `/isekai dump worldgen` | Write the full snapshot to `<world>/isekai_dump/worldgen.txt` |
| `/isekai dump ore <id>` | Single-feature query |
| `/isekai dump structure <id>` | Single-structure query |

All subcommands require permission level 2 (operators).

## Building from source

```bash
./gradlew build
```

Produces `build/libs/isekai_api-1.0.0.jar`.

## Examples

`examples/` ships ready-to-copy consumer skeletons plus focused feature demos.

**Headline consumer skeletons** (3-file worldshapes — copy, rename, ship):

| Pack | Demonstrates |
|---|---|
| `sky_archipelago/` | Floating-island overworld via `band_density` + `blended_noise`; `content_overrides.feature_predicates` keeps lakes off cliff edges |
| `flipped/` | Hanging-continent terrain via `band_density` `invert: true` + `solidity_bias` |
| `moon_world/` | Per-biome `block_overrides` (surface + fill) and tag-form `applies_to` (`#minecraft:is_overworld`) |

**Focused feature demos:**

| Pack | Demonstrates |
|---|---|
| `declaration_only/skyland_minimal/` | Smallest valid single-layer descriptor |
| `declaration_only/underground_only/` | `isekai:pipe` of `inverted` + `linear`, AND-composed structure predicate |
| `declaration_only/layered_overworld/` | Two-layer stack with `isekai:blend` transition |
| `runtime_effects/biome_modifier_demo/` | REMOVE-phase demo (strips lava lakes from desert biomes) |
| `runtime_effects/no_villages/` | Structure modifier disabling all five village variants |
| `runtime_effects/peaceful_plains/` | Per-category mob spawn (creature 1.5×, monster 0.25× in plains) |

See `examples/README.md` for the layout and `docs/DATAPACK_REFERENCE.md` for every key.

## Why neutral primitives, not specialized helpers?

A *universal* worldgen library has to be a small set of primitives you can combine to express *any* worldshape — including ones nobody has thought of yet. Naming primitives after specific use cases (`floating_island`, `tall_mountain`, etc.) freezes the design around those use cases and quietly excludes everything else.

Isekai ships 16 mathematical primitives instead of named helpers. Every consumer — whether a worldshape built by the author or by any third-party modder — expresses its world using only those primitives, with the same expressive power.

## License

[MIT License](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
