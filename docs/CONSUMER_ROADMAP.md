# Isekai Consumer Roadmap

Internal reference — assessment of each planned consumer mod's
implementability against Isekai API v1.0.0.

| ID | Concept | Difficulty | Status | Gap |
|---|---|---|---|---|
| mod-030 sky-world      | Aether-style floating islands Y=120..220                  | Easy       | **shipped 1.0.0, runtime-verified** | none |
| mod-031 mountain-world | All-mountain overworld, Y=80..300                         | Easy       | **shipped 1.0.0**                  | none |
| mod-032 deep-sea-world | Submerged overworld; oceanic structures primary           | Easy       | **shipped 1.0.0**                  | none |
| mod-033 hollow-earth   | Stacked overworlds: inverted Y=0..60 + normal Y=80..200   | Medium     | skeleton                           | `LayeredDescriptor` is loaded into the Java-side IsekaiRemap registry but no biome_modifier currently dispatches per-layer. Implementable today via two single-layer biome_modifiers split by Y range + a more complex noise_settings overlay. |
| mod-034 supercontinent | Single massive landmass, near-zero ocean                  | Easy       | **shipped 1.0.0**                  | none |
| mod-035 island-world   | Tiled scattered islands                                   | Easy-Med   | **shipped 1.0.0**                  | none (`repeat` validates as expected) |
| mod-036 canyon-world   | Plateaus Y=60..180 + canyons Y=0..30                      | Easy       | **shipped 1.0.0**                  | none |
| mod-037 flipped-world  | Y axis fully inverted                                     | Easy-Med   | **shipped 1.0.0**                  | bedrock not flipped (surface_rule is out of scope for Isekai); documented in mod README |
| mod-038 three-layered  | Three stacked overworlds with two void gaps               | Med-Hard   | skeleton                           | depends on mod-033's layered application pattern landing first |

## Recommended build order (completed → pending)

Low-risk → high-risk; ordering by what each new mod adds to the validation set:

Done (1.0.0 build green):
- **030 sky-world** — `mask_y_range` + `constant` for hard-edge floating islands (runtime-verified)
- **031 mountain-world** — vanilla `amplified` shortcut + structure Y-banding via predicate
- **032 deep-sea-world** — `mask_y_range` cap + `in_fluid` predicate + `below_fluid` anchor
- **034 supercontinent** — `min(vanilla, step(distance_xz))` for disc-shaped continent
- **036 canyon-world** — `step` reading vanilla `continents` DF + nested `mask_y_range`
- **035 island-world** — `repeat` (XZ tiling) + `distance` + `step` for archipelago
- **037 flipped-world** — `scale_coord(sy=-1)` + `translate(dy=-256)` for Y mirror

Remaining (require `LayeredDescriptor` machinery — biome_modifier-level extension to apply per-layer would simplify, but a workaround via two single-layer modifiers is possible today):
- **033 hollow-earth** — 2 stacked overworlds + void gap; first real layered-config user
- **038 three-layered** — capstone composing sky + middle + hollow patterns

## Reusable consumer template

Both shipped mods (030 sky-world, 031 mountain-world) follow the same
3-file pattern. To bootstrap a new consumer:

1. **Cleanup**: delete `forge-*/` and `fabric-*/` Architectury scaffolding;
   bump `mod_version` and `isekai_api_version` to `1.0.0` in `gradle.properties`;
   bump the version field in `<ModName>.java` and the dep version range in
   `neoforge.mods.toml`.

2. **Wire Isekai runtime**: in `build.gradle`, change the dependency from
   `compileOnly` to both `compileOnly` + `localRuntime`. NeoForge moddev
   discovers the jar's own `neoforge.mods.toml` and loads it as a sibling mod.

3. **Datapack files** (under `src/main/resources/`):
   - `pack.mcmeta` (one-liner; `pack_format: 48`)
   - `data/minecraft/worldgen/noise_settings/overworld.json` — overlay,
     either copy vanilla + wrap a density function in Isekai primitives,
     or just copy `amplified.json` if vanilla's amplified preset already
     captures the shape (mod-031 takes this shortcut).
   - `data/<mod_id>/neoforge/biome_modifier/apply_<name>.json` — type
     `isekai_api:apply_worldshape` with inlined descriptor.
   - `data/<mod_id>/neoforge/structure_modifier/apply_<name>.json` — type
     `isekai_api:apply_worldshape_structures` for excluded structures.
   - `data/<mod_id>/isekai/worldshape/<name>.json` — mirror of the
     descriptor for Java-side query API (`/isekai query worldshape <dim>`).

4. **Build verify**: `./gradlew build` should produce
   `build/libs/<mod_id>-1.0.0.jar` clean. The jar bundles the datapack
   resources automatically.

5. **Runtime verify**: `./gradlew runClient` from the consumer mod's dir;
   Isekai jar is picked up via `localRuntime`. Create a new world (existing
   ones keep their pre-modification terrain). See sky-world's
   `TEST_CHECKLIST.md` for a generic test template.

## Limitations encountered

- **bedrock relocation** (mod-037 flipped): vanilla bedrock is placed by the
  `surface_rule` block of noise_settings, which Isekai treats as out of scope.
  The consumer must override surface_rule in its noise_settings overlay
  (same pattern as sky-world's `final_density` wrap, applied to the
  `surface_rule` field instead).

- **`/isekai stats` shows declared dimensions = 0** when worldshapes are
  registered via NeoForge biome_modifier (not via Java
  `Isekai.remap().declareWorldshape()` or `data/<ns>/isekai/worldshape/*.json`).
  This is by design — biome_modifier route uses the NeoForge registry, not
  the Java-side `IsekaiRemap` map. The chunk-gen behavior is still correct;
  the query API simply doesn't see those descriptors. Consumers wanting query
  visibility can also drop a copy of the descriptor under
  `data/<mod_id>/isekai/worldshape/<name>.json`.
