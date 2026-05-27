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

Registered under the `isekai:` namespace, usable from `noise_settings` JSON or Java:

| Category | Primitives |
|---|---|
| Value sources | `constant`, `coordinate` (axis x/y/z) |
| Arithmetic | `add`, `multiply`, `negate`, `abs` |
| Range | `clamp` |
| Combinators | `min`, `max`, `lerp`, `step` |
| Spatial reference | `distance` (mode xz / xyz) |
| Coordinate transforms | `translate`, `scale_coord` (negative factors mirror the axis), `repeat` (XZ-plane tiling) |
| Masks | `mask_y_range` |

Vanilla density functions stay accessible via standard `minecraft:` keys — Isekai does not re-export them. Compose Isekai primitives with vanilla density via `HOLDER_HELPER_CODEC`-aware fields.

### Rule adaptation layer

Three sealed interfaces let you adapt vanilla / modded rules to your new worldshape — composable, no specific worldshape committed to the API surface:

- **`SpatialPredicate`** (11 records) — `YInRange` / `SolidFloor` / `SolidCeiling` / `TerrainSlope` / `NearBlock` / `NearBiome` / `InFluid` / `Always` / `Never` + `And` / `Or` / `Not`. Combine these to describe arbitrary structure placement conditions.
- **`RemapStrategy`** — `Linear` / `BandSplit(List<Float> ratios)` / `FixedRange` / `Inverted` / `CountScale` / `Identity` / `NonLinear` / `Custom` / `Pipe`. Map vanilla Y bands and feature counts onto your playable range.
- **`SurfaceAnchor`** — `WorldSurface` / `BelowFluid(fluid)` / `FixedY(y)` / `Custom`. Defines what "the surface" means in your worldshape.
- **`TransitionRule`** for multi-layer worldshapes — `Hard` / `Blend(h)` / `Gap(h)`.

### Public facade

```java
import com.kuronami.isekaiapi.api.Isekai;

// Read vanilla + modded worldgen rules
Isekai.query().getAllOres();
Isekai.query().getStructurePlacement(StructureKeys.STRONGHOLD);
Isekai.query().getMobsByCategory(MobCategory.MONSTER);

// Declare your worldshape transformation
Isekai.remap().declareWorldshape(new WorldshapeDescriptor(
    Level.OVERWORLD,
    new VerticalRange(60, 200, HeightDistribution.UNIFORM),
    new SurfaceAnchor.WorldSurface(),
    new RemapStrategy.BandSplit(List.of(0.30f, 0.50f, 0.20f)),  // ore Y bands
    new RemapStrategy.Linear(),                                  // structure
    new RemapStrategy.Linear(),                                  // mob spawn
    Map.of(StructureKeys.STRONGHOLD,
           new SpatialPredicate.And(List.of(
               new SpatialPredicate.YInRange(60, 100),
               new SpatialPredicate.SolidCeiling(3)))),
    Set.of(BiomeTags.IS_OVERWORLD),                              // applies to
    Set.of(),                                                     // excluded features
    100));                                                        // priority
```

### Commands

| Command | Status |
|---|---|
| `/isekai version` | v0.1 |
| `/isekai reload` | v0.1 stub |
| `/isekai query dimensions` | v0.1 |
| `/isekai validate <namespace>` | v0.1 stub |
| `/isekai dump worldgen [dim]` | v0.2 |
| `/isekai dump ore <id>` | v0.2 |
| `/isekai dump structure <id>` | v0.2 |
| `/isekai preview <descriptor_id>` | v1.1 |

## Project status

| Version | Scope |
|---|---|
| **v0.1** ← current | API surface frozen. 16 density primitives registered and usable from datapack JSON. `IsekaiQuery` / `IsekaiRemap` return immutable no-op stubs for now (declarations are recorded in memory but not yet applied to worldgen). |
| v0.2 | Vanilla rule snapshot scanner, biome modifier generator (functional remap pipeline), `isekai:surface_relative` / `fluid_relative` / `in_block_context` placement modifiers, datapack reload pipeline, JSON schema validator. |
| v1.x | Fabric / 1.20.1 / 1.21.x port. `/isekai preview` visual chunk preview. |

The library is intentionally NeoForge 1.21.1-only for v1.0 so that the API surface can be frozen without loader-portability concerns. v1.x will extract a Common module.

## Why neutral primitives, not specialized helpers?

The first draft of this library shipped 8 density functions named after the consumers that needed them — `floating_island`, `tall_mountain`, `deep_basin`, etc. Each one was a specialized helper tuned for one specific worldshape.

That was wrong. A *universal* worldgen library has to be a small set of primitives you can combine to express *any* worldshape — including ones nobody has thought of yet. Naming primitives after the use cases you happen to know about freezes the design around those use cases.

So the 8 specialized helpers were replaced by 16 mathematical primitives. The 9 consumer mods that ship with this repo set express the same worldshapes using only those primitives — and so does any future consumer, from anyone, anywhere.

## Building from source

```bash
./gradlew build
```

Produces `build/libs/isekai_api-0.1.0.jar`.

## License

[MIT License](LICENSE) — modpack inclusion welcome, no credit required.

## Credits

- Author: KURONAMI
