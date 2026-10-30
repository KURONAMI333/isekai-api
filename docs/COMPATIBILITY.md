# Compatibility & Versioning

Isekai API has **two independently-versioned compatibility surfaces**. A change can break one
without touching the other, so they are stated separately.

| Surface | Who depends on it | What "breaking" means |
|---|---|---|
| **Datapack schema** | pack authors, and every Java consumer that ships JSON | a datapack that loaded before now fails to load or changes behavior |
| **Java API** (`com.kuronami.isekaiapi.api`) | mods that `compileOnly`-depend on the jar | source or binary incompatibility for code compiled against a previous version |

The version number is a single [SemVer](https://semver.org/) `MAJOR.MINOR.PATCH`. A **major**
bump means *at least one* of the two surfaces broke; the changelog states which. A change that
breaks only the Java API (as 2.0.0 did) is still a major bump, even though datapacks are
untouched.

Only the `com.kuronami.isekaiapi.api` package is API. Everything else is
`@ApiStatus.Internal` and may change in any release — do not compile against it.

## What each bump guarantees

- **PATCH** (`x.y.Z`) — bug fixes. No new API, no schema change.
- **MINOR** (`x.Y.0`) — additive only. New datapack types, new API types/methods, new preset
  files. Existing datapacks and existing consumer code keep working. New API elements are
  tagged with the `@since` of the release that introduced them.
- **MAJOR** (`X.0.0`) — may remove or change existing API and/or datapack schema. Migration is
  documented in the changelog and here.

### `@since`

Every public type and method in the `api` package carries a `@since` tag naming the release it
first appeared in (`1.0.0`, `1.1.0`, `2.0.0`, …). The javadoc jar is the reference. Coverage is
checked mechanically by `tools/check_since.py`, run before release, so the tags do not drift
from the code.

## Deprecation policy

A deprecated element is kept working for **one full major version** after the release that
deprecates it, then removed in the next major. Deprecated datapack keys log a one-time warning
at load; deprecated Java elements carry `@Deprecated` + `@since`/`@deprecated` javadoc.

Currently deprecated (still accepted in 3.0.0; removal in a future major):

- **Bare `isekai:` dispatch prefix** — the canonical prefix is `isekai_api:`. `"type":
  "isekai:y_in_range"` still decodes but warns once per id. Rewrite to `isekai_api:`.
- **Inline `apply_worldshape` / `apply_worldshape_structures` modifier forms** — use the
  `_ref` forms (see [DATAPACK_REFERENCE.md](DATAPACK_REFERENCE.md)). The inline forms warn once
  on decode.

## Java API compatibility across 2.0.0

2.0.0 turned the five dispatch interfaces (`SpatialPredicate`, `RemapStrategy`, `BiomeZone`,
`SurfaceAnchor`, `TransitionRule`) from `sealed` codec unions into open, registry-backed
extension points. If you wrote Java against 1.x:

| 1.x code | 2.0.0 |
|---|---|
| exhaustive `switch` over a sealed interface's variants | add a `default` branch — the type is no longer sealed |
| `variant.typeId()` | `registry.getKey(variant.codec())`, where `registry` is the matching `IsekaiRegistries` registry |
| implementing the interface directly | also implement the new abstract method (`test` / `remap` / `resolveY`) |

`RemapStrategy` additionally gained `remapToColumn(VerticalRange, RemapContext)` in 2.0.0. It
is a **default** method returning `Optional.empty()`, i.e. "this strategy has no
terrain-relative form, use `remap`" — existing implementations, built-in or third-party, need
no change. Override it only when a strategy's band cannot be stated as one absolute Y range;
`isekai_api:column_local` is the built-in that does. Callers that consume strategies directly
should try `remapToColumn` first and fall back to `remap`, the order
`AddPhase.remappedOreFeatures` uses.

Consumers who only build descriptors from the factory/record API (`WorldshapeDescriptor.builder()`,
`new SpatialPredicate.YInRange(...)`, etc.) and authors who only write JSON need no changes.

## Datapack behavior change in 2.1.0 — BiomeZone noise follows the world seed

`isekai_api:noise_threshold` and `isekai_api:edge_jitter` used to seed their noise from the
literal `seed` written in the JSON, which made the pattern a property of the *pack* rather than of
the *world*: every player who installed a given pack got a byte-identical biome layout. From 2.1.0
the world seed is folded in, so the pattern differs per world and repeats for the same world seed.

The schema is untouched — the same JSON loads, and `seed` keeps its role of separating sibling
zones from each other. What moves is the pattern a given world seed produces. Packs that relied on
a *fixed* pattern should state it with the geometric zone types, which are seed-independent by
construction.

## Java API compatibility across 2.1.0

`BiomeZone` gained two elements, both additive:

- `default BiomeZone withWorldSeed(long)` — returns `this` unless the zone's result depends on the
  world seed. Third-party variants written against 1.x or 2.0.0 inherit the default and keep
  compiling and behaving as before.
- `static long deriveSeed(long worldSeed, long zoneSeed)` — the combining function the built-ins
  use. Third-party noise-backed variants that want per-world patterns should override
  `withWorldSeed` and route their seed through this.

A third-party variant that does **not** override `withWorldSeed` is never re-derived, so it keeps
whatever determinism it had. A third-party *combinator* is the one case worth checking: since the
default returns `this`, it will not pass the world seed down to the zones it wraps, and a built-in
noise zone nested inside it stays on the unbound pattern. Override `withWorldSeed` to rebuild
around `child.withWorldSeed(worldSeed)` if your variant holds children.

## Datapack behavior change (unreleased) — a column's lower bodies receive ore

`isekai_api:column_relative` used to place into the topmost body of a column and nothing else.
Both default anchors resolve against that body by construction: `world_surface` is the heightmap,
and `world_floor` scans down from whatever it found. In terrain that stacks — floating islands,
orbiting planets — every body but the highest was skipped entirely. It now emits one position per
body, at the same normalized depth.

The schema is untouched, and a column holding a single body places exactly what it placed before,
at the same Y, having consumed the same amount of randomness. What moves is ore in worlds whose
columns stack: those bodies used to be bare and are not any more. Existing worlds keep their
already-generated chunks and pick the change up in newly explored ones.

## Java API compatibility (unreleased)

`SurfaceAnchor` gained one element, additive:

- `default Integer resolveYBelow(PlacementContext, BlockPos, int ceiling)` — the anchor's Y within
  the part of the column at or below `ceiling`. The default is single-shot: it reports
  `resolveY`'s answer when that lies at or below the ceiling and `null` otherwise, which ends a
  caller's walk after one body. Third-party variants written against 1.x, 2.0.0 or 2.1.0 inherit
  it and keep compiling and behaving as before.

Two points worth checking if you ship your own variant:

- A variant that *wraps* another (as `world_floor` wraps its `start`) does not pass
  `resolveYBelow` down to its child under the default, so a built-in anchor nested inside it stays
  on single-shot semantics. Override `resolveYBelow` to delegate if your variant holds children.
- `WorldFloor.resolveYBelow` deliberately ignores its own `start`. That field means "where to
  begin looking for the topmost body", and a caller asking about a body further down has already
  established where that body begins.

## 3.0.0 — dimension-scoped worldshapes and the nether preset

**Datapack behaviour.** A worldshape now governs one dimension. Feature add/remove, structure
exclusion, mob spawn changes and (on noise-based generators) carver changes apply only in the
descriptor's `dimension`; the same biome in another dimension keeps its original rules. The JSON
schema is unchanged and 2.x packs load, but a pack that relied on the old cross-dimension leak
must declare a descriptor for the other dimension.

**Worldshape bounds resolve per dimension**, from its `LevelStem`/`DimensionType`, so a descriptor
whose `dimension` is `minecraft:the_nether` (or any non-overworld dimension) gets that dimension's
`min_y`/`height` and ore is projected into the right Y band. Overworld behaviour is unchanged. Only
a level with no `LevelStem` entry falls back to the overworld envelope.

**Breaking behaviour: feature edits are dimension-scoped.** `exclusions.features`, the originals
taken over by `ore_strategy`, and the originals replaced by `content_overrides.feature_predicates`
used to be edited in the biome's generation settings inside a NeoForge `BiomeModifier` — which has
no dimension context, so a nether worldshape rewrote every dimension that reused nether biomes.
They are now dropped at decoration time (`DimensionFeatureScope`) by the id of the level generating
the chunk, so the overworld, the nether, the end, datapack dimensions and other mods' dimensions
are all first-class; no vanilla id is special-cased. A custom generator that bypasses
`applyBiomeDecoration` is covered by a final veto at `PlacedFeature#placeWithContext`.
`/isekai validate dimension` reports the resolved descriptor per dimension.

**Breaking behaviour: structure exclusions.** `exclusions.structures` used to empty the structure's
biome filter in a NeoForge `StructureModifier`, a global mutation, so excluding it for the nether
also removed it from every dimension sharing those biomes. Exclusions now veto generation-point
search in the generating dimension. During chunk generation the dimension comes from
`ChunkDimensionIndex` (a weak `ProtoChunk` → dimension index), not from `BiomeSource` matching;
`/locate` and `/place` use the level directly. An implementation that passes only a
`LevelHeightAccessor` is not indexed and still falls back to `BiomeSource` matching, which is
ambiguous if two dimensions share one source. `/place structure` skips generation-point search and
is not blocked.

**Breaking behaviour: mob spawns are dimension-scoped.** `exclusions.mob_spawns`,
`additions.mob_spawns`, `mob_spawn_strategy` and `mob_spawn_strategy_by_category` used to rewrite
the shared biome's spawn builder. They are now recorded per (dimension, biome) and filtered where
the list is read (`NaturalSpawner#mobsAt`, chunk-generation spawn). Exclusions also cancel at
`FinalizeSpawnEvent`, covering paths that never read a list: patrols, spawners, spawn eggs and
`/summon`. A custom mob that calls `addFreshEntity` without `finalizeSpawn` is not covered.

**Breaking behaviour: carvers are dimension-scoped on noise-based generators.**
`exclusions.carvers` / `additions.carvers` are applied in
`NoiseBasedChunkGenerator#applyCarvers` per (dimension, biome). A dimension with another generator,
or a `NoiseBasedChunkGenerator` subclass that overrides `applyCarvers` without calling super, keeps
the old biome-wide edit.

**Terrain replacement is limited to noise-based generators.** Swapping a worldshape's density
functions or a `noise_settings` entry only affects dimensions that use `NoiseBasedChunkGenerator`;
a dimension with its own `ChunkGenerator` is out of scope.

**Breaking behaviour: other mods' features follow the scope.** A feature another mod adds to a
declared biome is removed from the shared biome and re-injected as a guarded copy for the declaring
dimension (`ForeignFeatureReconciler`). A dimension that reuses the biome without declaring that
worldshape no longer receives the feature.

**Java API (mostly additive).** `DimensionProfile` (`api/query`) and
`IsekaiQuery.getDimensionProfile(ResourceKey<Level>)` expose a dimension's vertical bounds, sea
level, default block/fluid and `DimensionType` flags *before* `ServerLevel` exists — which is when
NeoForge runs biome modifiers.

The one break is `ClientAtmosphereOverride` (`api/remap`): the record gained a `skyColor`
component, so its canonical constructor is now four arguments (`skyColor`, `fogColor`,
`fogNearDistance`, `fogFarDistance`). Code that called the three-argument constructor must pass
`Optional.empty()` for `skyColor`; already-compiled callers need a recompile.

**Internal signatures changed** (`@ApiStatus.Internal`, no compatibility promise):
`ModifyPhase.mobSpawnStrategy(WorldshapeDescriptor, Holder<Biome>)`,
`RemovePhase.excludedCarvers(WorldshapeDescriptor, ResourceKey<Biome>, …)` and
`AddPhase.additionalCarvers(WorldshapeDescriptor, ResourceKey<Biome>, …)`.

**Reload.** NeoForge runs biome modifiers once at server start, so `/reload` does not change
worldgen rules. The drop set and the injected copies are recorded in the same ADD pass, which keeps
them consistent: re-deriving the drop from the live declaration would make a feature added to
`feature_predicates` by a reload vanish and one removed generate twice.

**New presets.** `isekai_api:hooked_nether` + `isekai_api:hook/final_density_nether` (a separate
hook id, so overworld overrides of `hook/final_density` never touch it) and the
`isekai_api:vanilla_nether_surface` rule source.

**Known limitations.**

- Climate, visual/sound effects and precipitation are biome attributes and cannot differ by
  dimension for a shared biome; use `applies_to` to bound which biomes a descriptor edits.
  `atmosphere.mob_spawn_costs` (no level at `NaturalSpawner#createState`) and
  `creature_generation_probability` are written to the same shared biome.
- Carver scoping needs `NoiseBasedChunkGenerator`; other generators keep biome-wide add/remove.
- Mob exclusion does not cover `addFreshEntity` without `finalizeSpawn`; additions and weight
  changes apply only where a biome spawn list is read.
- A declared biome loses other mods' features in every other dimension.
- `/place structure` is not blocked by `exclusions.structures`.
- The final feature veto covers the `placeWithContext` route only; the decoration filter is the
  primary mechanism.

## Depending on Isekai API

Isekai is a `compileOnly` worldgen library: you compile against its `api` package, and the
consumer's own NeoForge provides the runtime. The published pom carries **no dependencies**.

Two account-free distribution routes, both public:

**Cursemaven** (available once the CurseForge file is approved):

```gradle
repositories { maven { url = "https://cursemaven.com" } }
dependencies { compileOnly "curse.maven:isekai-api-1557389:8810242" }   // 2.2.0
```

The trailing number is the CurseForge *file* id, so it changes every release; the
raw-URL route below takes an ordinary version string instead.

**Raw-URL maven** (the repo's `maven` branch, served by GitHub):

```gradle
repositories { maven { url = "https://raw.githubusercontent.com/KURONAMI333/isekai-api/maven" } }
dependencies { compileOnly "com.kuronami.isekaiapi:isekai_api:2.0.0" }
```

Add `isekai_api` to your `neoforge.mods.toml` dependencies so it loads at runtime, then use the
`Isekai` facade (`Isekai.query()` / `Isekai.remap()`) — see the [README](../README.md).
