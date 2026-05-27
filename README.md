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

## What it gives you

### 16 neutral density function primitives

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

Vanilla density functions stay accessible via standard `minecraft:` keys — Isekai does not re-export them. Compose Isekai primitives with vanilla density via standard density function references.

### Rule adaptation layer

Four sealed interfaces let you adapt vanilla / modded rules to your new worldshape — composable, no specific worldshape committed to the API surface. Each dispatches via a `"type"` field in JSON; the dispatch keys use the bare `isekai:` prefix (these are in-house codecs, not registry-backed types):

- **`SpatialPredicate`** (12 records) — `YInRange` / `SolidFloor` / `SolidCeiling` / `TerrainSlope` / `NearBlock` (HolderSet, tag-aware) / `NearBiome` / `InFluid` / `Always` / `Never` plus combinators `And` / `Or` / `Not`. Compose arbitrary structure placement conditions.
- **`RemapStrategy`** (9 variants) — `Identity` / `Linear` / `Inverted` / `FixedRange` / `CountScale` / `BandSplit(List<Band>)` / `Pipe(List<RemapStrategy>)` plus Java-only `NonLinear(Function)` / `Custom(BiFunction)`. Map vanilla Y bands and feature counts onto your playable range.
- **`SurfaceAnchor`** — `WorldSurface` / `BelowFluid(fluid)` / `FixedY(y)` plus Java-only `Custom`. Defines what "the surface" means in your worldshape.
- **`TransitionRule`** — `Hard` / `Blend(blend_height)` / `Gap(gap_height)` for multi-layer worldshapes.

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
    "excluded_features": [],
    "excluded_structures": [],
    "additional_features": []
  }
}
```

This causes the biome modifier's three phases to:
- **REMOVE** features in `excluded_features` plus the original ore-step features pending remap.
- **ADD** features in `additional_features` plus remap-derived variants of the ore features (with new Y ranges per `ore_strategy`).
- **MODIFY** mob spawn weights per the (optionally per-category) strategy.

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
Isekai.query().getAllOres();
Isekai.query().getOresByTag(BiomeTags.IS_OVERWORLD);  // example
Isekai.query().getOreVerticalRangeInDimension(featureKey, Level.NETHER);
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
| `/isekai validate <namespace>` | Validate every `isekai/*.json` under that namespace |
| `/isekai preview range <id> [dim]` | Show overworld-resolved + per-dim VerticalRange |
| `/isekai dump worldgen` | Write the full snapshot to `<world>/isekai_dump/worldgen.txt` |
| `/isekai dump ore <id>` | Single-feature query |
| `/isekai dump structure <id>` | Single-structure query |

All subcommands require permission level 2 (operators).

## Building from source

```bash
./gradlew build
```

Produces `build/libs/isekai_api-0.1.0.jar`.

## Examples

`examples/` ships six runnable datapacks:

| Pack | Demonstrates |
|---|---|
| `skyland_minimal/` | Smallest valid single-layer descriptor (every required field, every optional omitted) |
| `underground_only/` | `isekai:pipe` of `inverted` + `linear`, AND-composed structure predicate |
| `layered_overworld/` | Two-layer stack with `isekai:blend` transition |
| `biome_modifier_demo/` | REMOVE-phase demo (strips lava lakes from desert biomes) |
| `no_villages/` | Structure modifier disabling all five village variants |
| `peaceful_plains/` | Per-category mob spawn (creature 1.5×, monster 0.25× in plains) |

See `examples/README.md` for the full JSON schema reference.

## Why neutral primitives, not specialized helpers?

The first draft of this library shipped 8 density functions named after the consumers that needed them — `floating_island`, `tall_mountain`, `deep_basin`, etc. Each one was a specialized helper tuned for one specific worldshape.

That was wrong. A *universal* worldgen library has to be a small set of primitives you can combine to express *any* worldshape — including ones nobody has thought of yet. Naming primitives after the use cases you happen to know about freezes the design around those use cases.

So the 8 specialized helpers were replaced by 16 mathematical primitives. The 9 consumer mods that ship with this repo set express the same worldshapes using only those primitives — and so does any future consumer, from anyone, anywhere.

## License

[MIT License](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
