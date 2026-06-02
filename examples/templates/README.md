# Isekai API — Datapack Templates

Copyable starting points for the boilerplate-heavy parts of worldgen authoring. Each template is annotated with `//` keys (treated as comments by Minecraft's lenient JSON parser) explaining what to swap and what to keep verbatim.

## `world_preset_normal_override.json`

Overrides vanilla's `minecraft:normal` world preset to customise the overworld. **The Nether and End stanzas MUST be re-declared verbatim** — overriding the preset replaces it entirely, and leaving either out silently breaks that dimension. The `IsekaiValidator` world-preset check fires a warning on server start when this happens.

Copy to: `data/minecraft/worldgen/world_preset/normal.json` in your mod's resources, then:
1. Replace the `minecraft:overworld` → `generator.biome_source` with your biome source (e.g. `isekai_api:rule` or `isekai_api:climate_zones`).
2. Replace `generator.settings` with your noise_settings key.
3. Leave `minecraft:the_nether` and `minecraft:the_end` blocks unchanged unless you're also customising those dimensions.

For a NEW dimension (not overriding overworld), you do NOT need to write a `world_preset` file — NeoForge auto-loads `data/<ns>/dimension/<name>.json` as an additional dimension.

## `biome_overworld_like.json`

Minimal overworld-style biome with every required field present. Vanilla refuses to load a biome JSON missing any field, so this is the floor; copy, rename, and edit only what you want to change.

Notable required fields:
- `features`: list of 11 arrays (one per `GenerationStep.Decoration`). Add `placed_feature` ids to the right slot for your biome's vegetation / surface decoration / ores.
- `effects`: required colour values + at least a `mood_sound`.
- `spawners`: 8-category map; entries are optional but the map itself must be present.

Copy to `data/<ns>/worldgen/biome/<name>.json`.

## `dimension_type_overworld_like.json`

Minimal overworld-style dimension type. Floor values are the vanilla overworld; the inline comments list the swaps for sky-world / nether-style variants.

Copy to `data/<ns>/dimension_type/<name>.json`.

## `noise_settings_skeleton.json`

A `noise_settings` skeleton with the noise_router axes wired to vanilla's overworld defaults and a placeholder `final_density` slot. The intended workflow:

1. Copy to `data/<ns>/worldgen/noise_settings/<name>.json` (or `data/minecraft/.../overworld.json` if overriding vanilla).
2. Replace the `final_density` placeholder with your terrain expression — typically `{"type":"minecraft:interpolated","argument":{"type":"minecraft:blend_density","argument":{"type":"isekai_api:sloped_density", ...}}}`.
3. Replace the surface_rule's `strata` band list with your blocks.
4. Set `aquifers_enabled:false` for ocean / island terrain to avoid perched-water waterfalls.
