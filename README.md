# Isekai API

> A universal worldgen library for NeoForge 1.21.1.
> Provides world rule extraction and remap for Java MOD and datapack consumers.
> **Unrelated to the *Isekai Adventure* modpack.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![NeoForge 1.21.1](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net)

---

## What is Isekai API?

Isekai API is a small, complete *language* for expressing Minecraft worldgen — not a toolbox for any specific worldshape.

Think of it the way HTML/CSS are languages for expressing web pages, not tools for building Twitter. The library provides a set of neutral mathematical primitives; consumers compose them to express any worldshape they can imagine — floating islands, submerged worlds, hollow earths, archipelagos, mesa canyons, fully inverted Y axes, anything.

The 9 consumer mods shipped alongside this library are **validation set, not design target**: they exist to prove the API is expressive enough, and they ride on the same primitives any third-party modder gets.

## Quick start

Pick your path — most worldgen work needs no Java at all.

**Datapack author (no Java).** Your whole vocabulary is the set of `isekai_api:` / `isekai:` keys, fully listed in **[docs/DATAPACK_REFERENCE.md](docs/DATAPACK_REFERENCE.md)**. The fastest start is to copy a skeleton from [`examples/`](examples/):

1. Copy `examples/sky_archipelago/` (floating islands), `flipped/` (hanging continent), or `moon_world/` (re-skinned blocks + tag biome selection) into your mod's `src/main/resources/data/<modid>/`.
2. Rename the placeholder paths to your mod id.
3. Add `isekai_api` as a dependency in your `neoforge.mods.toml`.
4. Launch — the worldshape applies on world create.

**Java modder.** Add `isekai_api` as a `compileOnly` dependency, then use the facade — `Isekai.query()` to read vanilla worldgen rules and `Isekai.remap()` to declare a worldshape (see [Java facade](#java-facade)). Everything outside the `com.kuronami.isekaiapi.api` package is marked `@ApiStatus.Internal`; you only ever touch the `api` package.

## What it gives you

### 16 neutral density function primitives + 4 worldshape composers

Registered under the `isekai_api:` namespace (mod id), usable from `noise_settings` JSON or Java:

| Category | Primitives |
|---|---|
| Value sources | `constant`, `coordinate` (axis x/y/z) |
| Arithmetic | `add`, `multiply`, `negate`, `abs` |
| Range | `clamp` |
| Combinators | `min`, `max`, `lerp`, `step` |
| Spatial reference | `distance` (mode xz / xyz) |
| Coordinate transforms | `translate`, `scale_coord` (negative factors mirror the axis), `repeat` (XZ-plane tiling) |
| Masks | `mask_y_range` |
| **Worldshape composers** | `squeeze`, `y_envelope`, `blended_noise`, `band_density` |

The four worldshape composers build common terrain patterns without leaking consumer-theme names into the API:

- **`squeeze`** — vanilla tone-mapping (`x/2 - x^3/24` clamped to [-1, 1]). Re-implementation because `DensityFunctions.Mapped.Type` is package-private and unreachable from outside.
- **`y_envelope(active_min_y, active_max_y, gradient_width, invert)`** — pure Y-axis mask. Returns 1 inside the active band, 0 outside, linear ramp through `gradient_width`. `invert: true` flips polarity (1 outside, 0 inside) — foundation for hollow-shell / inverted worlds.
- **`blended_noise(size_xz, size_y, smear_multiplier)`** — `old_blended_noise` wrapper with `xz_scale`/`y_scale` fixed at 0.25. Bigger `size_xz` = chunkier terrain blobs; bigger `size_y` = taller ones.
- **`band_density(active_min_y, active_max_y, gradient_width, invert, noise)`** — composer that takes any noise source and an active Y-band, wraps it in the standard `(-0.05 + -0.1 + mul(y_envelope, ...) + ...)` tree, then `blend_density` + `interpolated`. Drop in your noise via the `noise` field; wrap the result in `minecraft:squeeze` for the final tone.

Vanilla density functions stay accessible via standard `minecraft:` keys — Isekai does not re-export them. Compose Isekai primitives with vanilla density via standard density function references.

### Rule-based biome source (`isekai_api:rule`)

Density functions decide the *shape* of terrain; the biome source decides *which biome goes where*. `isekai_api:rule` is a `BiomeSource` that places biomes by spatial rules, evaluated in declaration order — the first matching `BiomeZone` wins. Reference it from a dimension's `biome_source` field:

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

### New dimensions

A new dimension is pure datapack — no Java. Combine `dimension/` + `dimension_type/` + `worldgen/noise_settings/` with the `isekai_api:rule` biome source (which biomes) and `isekai_api:band_density` (terrain shape). New biomes are likewise plain `worldgen/biome/` JSON, placed by the rule biome source.

### Per-biome surface block overrides

Two `SurfaceRules.RuleSource` types let you re-skin per-biome blocks without writing surface rule trees by hand:

- **`isekai_api:worldshape_surface_top`** — overrides the top block of matched biomes (e.g. "in moon biomes, top with moon_regolith instead of grass") from the worldshape's `block_overrides.surface_top` map.
- **`isekai_api:worldshape_default_block`** — overrides the default block (the stone-equivalent bulk fill) from `block_overrides.default_block`.

Wire either rule into your dimension's `surface_rule` sequence — surface_top should come first, default_block should come last (after vanilla rules) so the surface band stays intact and only sub-surface stone gets remapped.

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
- **`BiomeZone`** (9 variants) — the biome-placement conditions used by the `isekai_api:rule` biome source (see above): `always` / `y_above` / `y_below` / `y_between` / `within_distance` / `beyond_distance` plus combinators `and` / `or` / `not`.

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

Isekai ships 16 mathematical primitives instead of named helpers. The 9 consumer mods that ride this library express their worldshapes using only those primitives — and so does any third-party consumer, with the same expressive power.

## License

[MIT License](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
