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

Currently deprecated (removed in 3.0.0):

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

## Depending on Isekai API

Isekai is a `compileOnly` worldgen library: you compile against its `api` package, and the
consumer's own NeoForge provides the runtime. The published pom carries **no dependencies**.

Two account-free distribution routes, both public:

**Cursemaven** (available once the CurseForge file is approved):

```gradle
repositories { maven { url = "https://cursemaven.com" } }
dependencies { compileOnly "curse.maven:isekai-api-1557389:8581037" }   // 2.0.0
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
