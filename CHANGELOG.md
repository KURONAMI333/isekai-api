# Changelog

All notable changes to Isekai API follow this file. Format roughly follows
[Keep a Changelog](https://keepachangelog.com/). Compatibility policy — how datapack
and Java API stability are versioned — is in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## [3.0.0] — 2026-10-29

### Breaking changes

- **Mob spawn changes are dimension-scoped.** `exclusions.mob_spawns`, `additions.mob_spawns`,
  `mob_spawn_strategy` and `mob_spawn_strategy_by_category` used to rewrite the shared biome's
  spawn builder; they now apply only in the descriptor's dimension. The same biome in another
  dimension keeps its original list.
- **Carver exclusions and additions are dimension-scoped on noise-based dimensions.**
  `exclusions.carvers` and `additions.carvers` now apply only in the declared dimension when its
  generator is a `NoiseBasedChunkGenerator`. Dimensions with another generator keep the old
  biome-wide edit (see limitations).
- **Other mods' features follow the declared scope.** A feature another mod adds to a declared
  biome is removed from the shared biome and re-injected as a guarded copy for the declaring
  dimension. Dimensions that do not declare the worldshape no longer receive it
  (`ForeignFeatureReconciler`).
- **Internal signatures changed** (`@ApiStatus.Internal`, no compatibility promise):
  `ModifyPhase.mobSpawnStrategy(WorldshapeDescriptor, Holder<Biome>)`,
  `RemovePhase.excludedCarvers(WorldshapeDescriptor, ResourceKey<Biome>, …)` and
  `AddPhase.additionalCarvers(WorldshapeDescriptor, ResourceKey<Biome>, …)`.
- **2.x datapacks load unchanged, but the scope changes.** The same JSON now affects one dimension
  instead of every dimension that shares the biome. A pack that relied on the old cross-dimension
  leak must declare a descriptor for the other dimension.
- **`ClientAtmosphereOverride` gained a `skyColor` component.** The record is now
  `(skyColor, fogColor, fogNearDistance, fogFarDistance)`; the three-argument constructor is gone.
  Source callers pass `Optional.empty()` for `skyColor`, and compiled callers must recompile.

### Added

- **Per-dimension feature drop at decoration time.** `ChunkGeneratorDecorationMixin` redirects
  `generationSettingsGetter.apply(biome)` in `ChunkGenerator#applyBiomeDecoration`;
  `DimensionFeatureScope` removes the recorded takeover set for the dimension generating the
  chunk. The set is recorded by the ADD phase, so the drop and the injected copy always agree.
- **Final feature veto for custom generators.** `PlacedFeatureScopeMixin` injects at
  `PlacedFeature#placeWithContext` HEAD and rejects the same takeover values, so a generator that
  overrides `applyBiomeDecoration` wholesale also honours `exclusions.features`,
  `feature_predicates` and `ore_strategy`.
- **Injected features carry `isekai_api:dimension_guard`.** `additions.features`, remap copies and
  `feature_predicates` copies place only in the descriptor's dimension.
- **Structure exclusions are dimension-scoped.** `StructureFindValidGenerationPointMixin` vetoes
  during generation-point search. During chunk generation the dimension comes from
  `ChunkDimensionIndex` (a weak `ProtoChunk` → dimension index); `/locate` and `/place` use the
  level directly; only an index miss falls back to matching the `BiomeSource`.
- **Mob spawn rules are enforced at the runtime read points.** `MobSpawnScope` holds a
  per-(dimension, biome) rule. `NaturalSpawner#mobsAt` (RETURN) and
  `spawnMobsForChunkGeneration` (`getMobs` redirect) filter the list; `MobSpawnVetoHandler`
  cancels at `FinalizeSpawnEvent` (LOWEST) for paths that never read a list, so an exclusion also
  covers patrols, spawners, spawn eggs and `/summon`.
- **Carver rules are enforced at carve time.** `NoiseBasedChunkGeneratorCarverMixin` redirects
  `ChunkAccess#carverBiome` in `NoiseBasedChunkGenerator#applyCarvers`; `CarverScope` applies the
  recorded add/remove rule per (dimension, biome).
- **`client_atmosphere` reaches clients on a dedicated server.** The server sends the active
  Y-bands over the optional `isekai_api:client_atmosphere` channel on login, `/reload` and
  dimension change; the client applies them before falling back to its own registry, so sky and
  fog match what the server loaded. Vanilla clients and clients without the channel are
  unaffected.
- **`/isekai validate dimension <id>`** reports the `LevelStem` profile (build height, sea level,
  default block, ceiling) and the silent failures: `applies_to` matching no biome, a
  `playable_range` outside the build height, unresolved biome tags, features skipped in that
  dimension.
- **Start-point safety in `/isekai validate dimension <id>`.** For a dimension with a declared
  worldshape the report adds a `spawn OK`/`WARN`/`ERROR`/`SKIP` line: the first solid block below
  the start point (max 32 blocks) is the footing, the two blocks above it must be neither solid
  nor fluid, water is a warning, and lava, a buried start or no footing is an error. The start
  point is the overworld's shared spawn, or the (0,0) `MOTION_BLOCKING_NO_LEAVES` heightmap
  position for other dimensions.
- **Worldshapes can be narrowed to dimension types.** `dimension` also accepts
  `{ "dimension": "<key>", "required_dimension_types": ["<type>", …] }`; a level whose
  `dimension_type` is not listed is left alone. This lets a selectable world type keep its
  worldshape out of vanilla worlds that share the `minecraft:overworld` key. The string form and
  an empty list mean no restriction (the 2.x behaviour). Java: `Scope` (`api/remap`) on
  `WorldshapeDescriptor`.
- **`DimensionProfile`** (`api/query`) and **`IsekaiQuery.getDimensionProfile(ResourceKey<Level>)`**
  expose a dimension's vertical bounds, sea level, default block/fluid and `DimensionType` flags
  before a `ServerLevel` exists.
- **Nether preset.** `isekai_api:hooked_nether` (`min_y 0`, `height 128`, sea level 32,
  netherrack/lava defaults, legacy random source) with `isekai_api:hook/final_density_nether` and
  `isekai_api:vanilla_nether_surface`. The separate hook id keeps overworld overrides of
  `hook/final_density` out of the nether.
- **Dimension-scoped sky colour.** `client_atmosphere.sky_color` (`0xRRGGBB`) replaces the
  biome sky colour while the descriptor is active, resolved per camera Y like the fog overrides.
  Only the sampled base colour changes: vanilla's day/night, rain/thunder and lightning-flash
  shading still apply. Carried by `ClientLevelSkyColorMixin`, so it exists on the client only, and
  it applies to dimensions whose `effects` is `minecraft:overworld` (the vanilla sky path).
- **`isekai_api:none` foliage placer.** A crown of nothing, so a `minecraft:tree` feature can place
  a bare trunk without leaves. `radius` and `offset` are optional and default to `0`, which makes
  `{"type": "isekai_api:none"}` valid on its own.
- **Six tree decorators.** `minecraft:tree`'s `decorators` array now accepts `isekai_api:` parts:
  `attach_to_logs` (blocks on trunk sides), `ground_patch` (a disc of blocks around the base,
  `cover` or `replace`), `hang_from_leaves` (strands from the crown's underside), `snow_cap`
  (snow layers on sky-facing leaves), `branch_stubs` (bare limbs out of the trunk, each `axis`
  following the step, block optional so it can reuse the trunk's own block) and `broken_top`
  (snaps the top `count` trunk blocks off, keeping at least one; optional `fill_provider` turns the
  break into basalt or charcoal instead of air). Every block is a `block_provider` slot, so the
  parts carry no species; leaf states written by a decorator are pinned on `distance` so they do
  not decay. Fields and the charred-tree example are in `docs/DATAPACK_REFERENCE.md`.
- **`TreeGrowers`** (`api/wood`) builds the `TreeGrower` a sapling needs from configured feature
  keys — plain, mega, secondary and flower forms — with Isekai's own name convention, so a wood set
  does not have to hand-write the eight-argument constructor; a wood set hands the result to
  `WoodSet.Builder#sapling`.
- **Bare-tree fixture and coverage.** The GameTests load a leafless, branch-bearing tree from disk
  through the real configured-feature registry and assert the trunk, stubs, snap and ground ring,
  alongside codec round-trips for the new placers and decorators. The fixture and its test wrapper
  classes stay out of the jar (`data/isekai_api_test/**`, `gametest/**`).
- **`/isekai validate <namespace>` reports missing wood-set files.** For every species the namespace
  declares with `WoodSet`, it checks the loot tables, block and item tags, recipes, language keys,
  blockstates, models and textures that the species' parts imply, and lists each gap by kind and
  subject. Members that are not visible from the current side (client-only resource packs on a
  dedicated server) are counted as "not visible here" instead of missing. This catches a missing
  texture before it shows up as the purple-black checker in game.
- **A one-species example under `examples/4_wood/`.** `ExampleWoodMod` declares a full wood set
  (log, wood, stripped variants, planks, stairs, slab, fence, gate, door, trapdoor, pressure plate,
  button, signs, leaves, sapling, potted sapling, boats) and its datagen; the pack beside it holds
  the configured feature with an `isekai_api` decorator. The class is compiled and the pack is
  loaded only on the GameTest server, and neither ships in the jar.
- **`WoodSet.Builder#flammable(boolean)`.** A set declared `flammable(false)` is left out of the fire odds
  map and its logs are data-generated into `#logs` instead of `#logs_that_burn`, so neither the logs nor
  the planks spread fire or burn away; `WoodSet#isFlammable()` reports the setting. One part cannot be
  turned off: every block keeps vanilla's `ignited_by_lava` flag from the oak properties it copies, so
  lava still sets it alight.
- **The wood-set audit covers sign textures.** A set with `sign` or `hanging_sign` also needs
  `textures/entity/signs/<path>.png`, `textures/entity/signs/hanging/<path>.png` and
  `textures/gui/hanging_signs/<path>.png`, which `WoodSets.signTexture` and
  `WoodSets.hangingSignGuiTexture` name. The example pack ships the three files.
- **`isekai_api:pool` takes a `shore_width`.** `shore_width` (int 0–6, default 0) grows the pool's
  rim block outward that many more rings, so a pond can have a beach instead of a one-block lip.
  The rings are grown from the shell by adjacency, so the bank follows the basin's real outline,
  and only solid cells are replaced (below the waterline always, above it half the time — the same
  rule the shell uses). It does not change containment: a pool still refuses terrain that cannot
  hold it. `0` is the old single ring, and the default, so existing datapacks place the same blocks.
- **`isekai_api:leaning` trunk placer takes `slant_curve`, `space_check` and `min_height`.**
  `slant_curve` (double 1.0–2.0, default 1.0) lengthens each successive sideways step, so the same
  `lean_length` covers more ground and the bend accelerates instead of forming an L; 1.0 is one
  block per step and byte-identical to the old tree. `space_check` (bool, default true) ends the
  lean at an occupied cell and keeps the crown on the trunk — see Fixed. `min_height` (int 1–16,
  default 4) makes the tree place nothing when its trunk would be shorter, which is what a
  deliberately short palm needs. A datapack that omits all three gets the old shape.
- **`isekai_api:leaf_distances` structure processor.** Recomputes a pasted template's leaf decay
  data from the template's own `#minecraft:logs` blocks: distance 1 for a leaf touching a log, +1
  per leaf step, 7 when no log is reachable. `orphan_leaves` (`decay`|`persist`, default `decay`)
  decides whether unreachable leaves are left to decay or marked persistent, and `keep_persistent`
  (default false) decides whether a template leaf's existing persistent flag survives. It runs in
  `finalizeProcessing`, so it sees the states earlier processors produced. A template saved with
  every leaf at distance 7 no longer sheds its whole crown on the first shape update.
- **`isekai_api:grounded_template` takes a `processors` list.** The same field a jigsaw
  `single_pool_element` has: a processor-list id or the list written inline, applied when the piece
  pastes the template. Omitting it places the NBT exactly as saved, which is what every existing
  datapack already got. Attaching `isekai_api:leaf_distances` here fixes a hand-authored tree
  without routing it through a jigsaw pool.
- **`isekai_api:grounded_template` varies the shape.** `rotation` (`none` by default, plus
  `clockwise_90`, `clockwise_180`, `counterclockwise_90`, `random`) and `mirror` (`none` by
  default, plus `left_right`, `front_back`, `random`) turn or flip the pasted template, and
  `template` now also takes a weighted list, so one structure entry can share several NBT variants.
  `random` draws once per placement attempt from the chunk's structure seed. The gates sample the
  rotated extents, so a quarter-turned template is judged on the ground it actually covers, and the
  template stays centred on the chunk middle in every orientation. A datapack that omits the new
  fields places the same blocks as before.
- **`/isekai survey <structure> [radius]`** replays structure placement over a square of chunks and
  reports how many chunks the structure set offered, how many were placed, and, for the rest, which
  gate rejected them (`clearance_above_fluid`, `max_slope`, `biome`, or `declined` for a structure
  type with its own reason). Nothing is generated to disk and no chunk is loaded, so it answers
  "why does this never spawn" on a live world; the same numbers are written to
  `<world>/isekai/survey/<ns>_<path>-r<radius>.json`. The rejection attribution runs through
  `StructureProbe`, which is inert outside a survey.
- **`/isekai fit <structure> [pos]`** shows one chunk at full detail: the `WORLD_SURFACE_WG` heights
  the gates read as a 3x3 grid, the minimum/spread against `clearance_above_fluid` and `max_slope`,
  the verdict, and where `vertical_offset` puts each template variant. It also flags the two
  authoring mistakes that make a structure vanish without an error: a `template` id with no NBT
  (reported as `0x0x0`) and a chunk no structure set offers. Written to
  `<world>/isekai/fit/<ns>_<path>-<chunkX>_<chunkZ>.json`.
- **`/isekai survey blocks <block|#tag> [radius] [center <x y z>] [biome <biome|#tag>]`** counts what
  actually made it into each chunk: it generates the square (default radius 8, max 32) to `FEATURES`,
  spread over ticks, and reports trees, matching blocks, mean/median/min/max, the share of empty
  chunks, how much of the total sits in the busiest 10% of chunks, and a histogram. One tree is one
  six-connected group of matching blocks inside a chunk, so a 2x2 trunk counts once and a leaning
  trunk counts once. The optional biome filter is answered from the seed, so skipped chunks are never
  generated. The per-chunk map and the same summary are written to
  `<world>/isekai/survey/blocks-<target>-r<radius>.json`. This one does generate terrain to disk.

### Fixed

- **`isekai_api:assembled` puts its origin on the ground, not on a tree crown.** The origin's Y came
  straight from `WORLD_SURFACE_WG`, which counts leaves and logs, so a neighbouring tree whose crown
  covered the origin column lifted the whole feature list onto the canopy: a pool dug into open air
  (and its containment check refused it) and a pad spread its sand at tree height. The Y now walks
  down past air, `#minecraft:leaves`, `#minecraft:logs` and replaceable plants to the first block
  that is terrain, and stops on fluids, so an over-water structure keeps its water-surface origin.
- **`isekai_api:assembled` places its features once, not once per overlapping chunk.** A piece's
  `postProcess` runs for every chunk its box overlaps, each with its own random, so a
  three-chunk-wide assembled landmark could re-place the whole feature list up to nine times and
  stack nine differently-shaped pools and tree clusters on one spot. The run is now gated on the
  chunk that contains the origin, which does not depend on chunk visit order. Features may still
  write past that chunk, exactly as a `place` feature does during decoration.
- **A `leaning` trunk no longer attaches its crown to a blocked cell.** With `space_check` on (the
  new default), a lean that runs into an occupied cell stops there and the crown stays on the
  trunk; previously the crown could sit in mid-air where no log could be placed. Set
  `space_check: false` to reproduce the old shape.
- **Worldshapes for different dimension types of one dimension key coexist.** Single-layer
  declarations were stored one-per-dimension key, so a Sky World shape (`sky_world:sky`) and a
  Tundra World shape (`frozen_world:frozen`) both targeting `minecraft:overworld` replaced each
  other in load order and one world type got no shape at all. Declarations are now grouped by
  `Scope`; `getActiveDescriptor` picks the one whose `required_dimension_types` contains the
  level's actual `dimension_type` (restricted over unrestricted, then higher `priority`). Same-
  scope declarations keep the old priority rule. If the type is unknown and two restricted
  declarations exist, the registry returns none rather than guessing.
- **Worldshape bounds resolve per dimension.** Ore/feature Y-remapping and the `playable_range`
  envelope used the overworld's `min_y`/`height` for every dimension. They now come from the
  dimension's `LevelStem`/`DimensionType`, keyed by the descriptor's `dimension`. Only a level with
  no `LevelStem` entry falls back to the overworld envelope.
- **No declarations, no cost.** Every hook returns on an empty check before a registry or biome
  lookup; the reconcile returns immediately when no worldshape is declared.
- **Decorators no longer inherit a 2x2 trunk's ground blocks.** `TreeDecorator.Context#logs()` carries
  one block per trunk column written by `TrunkPlacer.setDirtAt` (four for `giant_trunk_placer` on
  grass), and only the first was dropped. `branch_stubs` without a `block_provider` could therefore
  take its block from dirt and start one row too low. Every dirt-like entry at the lowest level is now
  dropped, and only when a trunk block sits above it.
- **The wood-set audit reads references the way the game does.** A bare path in a model `parent`, a
  `textures` value or a blockstate `model` resolves to `minecraft:`, not to the namespace of the file
  that names it; a value that is not a valid resource location becomes a `reference` finding instead of
  failing the whole audit. `/isekai validate <namespace>` prints the validation result before the
  wood-set audit, and an audit failure is logged and reported as one line instead of hiding it.
- **`broken_top`'s `fill_provider` uses the decorator placement helpers.** A leaf used as the fill is
  pinned like every other decorator-written leaf rather than written at `distance: 7`.
- **A wood set's builder is claimed only after registration succeeds.** A failure part-way through
  `register()` no longer leaves the builder marked as registered and unusable.
- **`WoodSets` survives parallel mod construction.** NeoForge constructs mods in parallel; the set list
  and the boat lookup are now concurrent collections.
- **Docs: a pinned leaf is not a permanent leaf.** `LeafPlacing`, the placer section of
  `docs/DATAPACK_REFERENCE.md` and the README now state that the pin carries a leaf through generation,
  and that the first tick recomputes the real distance, so a leaf more than six steps from a log decays
  as a vanilla leaf does. The behaviour is unchanged.
- **Docs: `ground_patch`'s radius limit has a stated reason.** 16 is the largest disc that stays inside
  the chunks a feature may write (`ChunkStatus.FEATURES` writes one chunk out), while a placement
  modifier that moves the feature's origin into a neighbouring chunk can still reach past it.
- **A `leaning` trunk's horizontal walk is capped at 12 blocks.** `slant_curve` is exponential, so
  `slant_curve` 2.0 with `lean_length` 16 walked 131,071 blocks sideways — far outside the chunks a
  feature is placed into, and the crown was attached there. The stalk now stops at 12 blocks of
  total horizontal travel from the origin (the near-vertical lean counts). `slant_curve` 1.0 with a
  `lean_length` of 13–16 is truncated at the same limit; every configuration that stayed inside 12
  blocks (including 1.0 up to 12) generates exactly as before.
- **A `leaning` trunk with `space_check` on keeps its crown on the last log it placed.** The fix for
  the blocked cell moved the crown but left the returned attachment pointing at the intended end
  position, so a decorator or `tip_crown_only` fan could still hang from a spot with no log under
  it. The last attachment is now the last placed log itself. `space_check: false` keeps the old
  geometry for packs that reproduce it deliberately.
- **`/isekai survey blocks` no longer claims to stay under 8 ms.** Chunk generation runs on the
  server thread inside the tick, so one chunk per tick means tens of milliseconds per chunk and TPS
  drops for the length of the run; the tick loop can only decide whether to start another chunk, not
  slice one. The docs and README now say so, and the per-chunk count is stated as a lower bound
  because a later chunk's tree can still write into a chunk already counted.
- **Only one `/isekai survey blocks` runs per server.** A second run inside the same window
  interleaved with the first inside the same ticks and counted the same chunks twice; it is now
  refused with a message naming the run that is still going.
- **`isekai_api:pool`'s `shore_width` is taken out of `xz_radius`.** A radius plus a shore larger
  than the 15 blocks a feature may reach was cut off at the chunk border, leaving a straight edge
  instead of a bank. The effective radius is now `xz_radius` shrunk by `shore_width` (floor 1), so
  `xz_radius` 14 with `shore_width` 6 reaches 15 rather than 19. Pools whose radius plus shore
  already fit are unchanged.
- **`/isekai fit` says which variant it measured.** The verdict describes one footprint — the first
  template variant, unrotated — but the report looked like it covered every variant, and
  `rotation: random` draws a different turn per chunk. The summary now names the measured template
  and rotation, and the JSON carries `measured_template` / `measured_rotation`.

### Changed

- **`/isekai survey` caps `radius` at 128 instead of 512.** The scan is quadratic and runs inside the
  command call on the server thread: 512 was 1,050,625 chunks of placement tests plus a generation
  check for every candidate in one tick. 128 is 66,049 chunks; use several smaller runs instead.

### Known limitations

- **Biome attributes are shared.** Climate (`temperature`, `downfall`, precipitation) and effects
  (colors, particles, sounds, music) cannot differ by dimension when a biome is shared; use
  `applies_to` to limit which biomes a descriptor edits.
- **`atmosphere.mob_spawn_costs` stays global**, because `NaturalSpawner#createState` has no
  level. `creature_generation_probability` is written to the same shared biome.
- **Carvers are dimension-scoped only on `NoiseBasedChunkGenerator`.** A dimension with another
  generator, or a `NoiseBasedChunkGenerator` subclass that overrides `applyCarvers` without
  calling super, keeps the old biome-wide add/remove (`CarverScope.hookable`).
- **Mob exclusions do not reach a custom mob that calls `addFreshEntity` without
  `finalizeSpawn`.** Additions and weight changes only apply where a biome spawn list is read.
- **`/reload` does not change worldgen rules.** NeoForge runs biome modifiers once at server
  start, so adding or removing a rule needs a restart.
- **A declared biome loses other mods' features in every other dimension.** The reconcile removes
  the original holder, so a dimension that reuses the biome without declaring it no longer gets
  that feature.
- **Structure dimension resolution during generation depends on the chunk being a
  `ChunkAccess`.** An implementation that passes only a `LevelHeightAccessor` is not in the index
  and falls back to `BiomeSource` matching, which is ambiguous when two dimensions share one
  biome source (`ChunkDimensionIndex`, `DimensionResolver`).
- **`/place structure` is not blocked by `exclusions.structures`** because it skips the
  generation-point search.
- **The final feature veto covers the `placeWithContext` route only.** A generator that places a
  feature by another path can still place it; the decoration filter is the primary mechanism.

## [2.2.0] — 2026-09-04

**No Java API breaks.** The one addition is a `default` method, so `SurfaceAnchor` variants
written against 1.x / 2.0.0 / 2.1.0 compile and run unchanged. The datapack schema is untouched;
what changes is where ore lands in a column that holds more than one body.

### Fixed — datapack behaviour

- **`isekai_api:column_relative` reaches every body in a column, not just the topmost one.**
  A column of floating terrain can hold several islands stacked above one another, and both
  default anchors resolved against the highest of them by construction — `world_surface` reads
  the heightmap, and `world_floor` scans down from whatever that found. An island with anything
  floating above it therefore received no ore at all. It now emits one position per body, at the
  same normalized depth, so an island's ore no longer depends on what happens to float above it.
  Measured on Sky World: andesite, granite, diorite, coal, iron and copper were all exactly 0
  through the lower island band, in a region where 29 of 29 lower-island chunks also carried an
  upper island.
- A body too thin for the band is skipped on its own instead of ending the column, so a thin
  island no longer hides the ones below it.
- **A column holding one body places exactly what it placed before**, at the same Y, having drawn
  the same amount from the random source — the depth is drawn once per placement however many
  bodies the column turns out to hold, and a column that yields nothing still draws nothing.
  Worlds whose terrain never stacks generate identically.

### Added — Java API

- **`SurfaceAnchor.resolveYBelow(PlacementContext, BlockPos, int)`** — resolve an anchor against
  the part of a column at or below a ceiling, which is how the walk reaches the second body and
  the ones under it. It is a `default` method with single-shot semantics (the anchor's own Y when
  that lies at or below the ceiling, otherwise nothing), so a variant that does not override it
  keeps reporting one body per column exactly as before. `world_surface` and `world_floor`
  override it; see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## [2.1.0] — 2026-08-08

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

### Changed — `isekai_api:pool` behaviour

`pool` used to carve, floor and fill unconditionally, so a basin that straddled a step
in the ground ended up with its water surface above the neighbouring ground and drained
over the lip. It now follows `LakeFeature`'s shape: it **tests the shell for solidity
before writing anything** and returns without placing a single block when the site
cannot hold the fluid, it **lines the whole shell** with `rim_block` rather than only
the floor, and its outline is a **union of several random ellipsoids** instead of a
mathematical disc. The ice-capping pass is deliberately not ported — it calls
`getBiome()` up to 15 blocks out, which throws during worldgen.

No codec change: `fluid` / `rim_block` / `xz_radius` / `depth` are untouched and 1.x
datapacks decode unchanged. **Placement gets rarer on uneven ground** — measured at
roughly 2–6x fewer successes on rolling terrain, and a wider `xz_radius` costs more
than a deeper `depth` — so raise the `rarity_filter` chance rather than the radius if a
world wants the old frequency back.

### Fixed

- **`exclusions.features` did not hold.** The REMOVE phase deleted the listed
  features and the ore-remap ADD phase put them straight back: it re-injected
  every snapshot feature whose placement exposed a `HeightRangePlacement` —
  which is not only ores — and did so as an anonymous `Holder.direct`, with no
  `ResourceKey` for any later pass to match. A world that excluded
  `minecraft:spring_water` still generated springs, and nothing in the datapack
  could remove them. Measured on a generated Sky World save: 437 water sources
  and 18,247 lava sources across 9,409 chunks, all from excluded features.
  REMOVE and ADD now derive their target set from one function
  (`impl/RemapTargets.select`), so the two phases cannot drift apart again.
  Carvers and structures were checked for the same shape and have no equivalent
  path. `additions.*` is deliberately unchanged: naming the same id in both
  `additions` and `exclusions` still lets `additions` win, since that is an
  explicit-versus-explicit conflict rather than an unrequested re-injection.
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
