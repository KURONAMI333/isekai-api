# Isekai API — example datapacks

Every Isekai world is three steps. The examples are organised by which step they show.

| Step | What it decides | Isekai tools | Examples |
|---|---|---|---|
| **1. Shape** | where terrain is solid vs. air | noise_settings + density functions (the `final_density` hook) | [`1_shape/`](1_shape/) |
| **2. Placement** | which biome / blocks go where | biome sources (`isekai_api:rule`, `climate_zones`), surface & block overrides | [`2_placement/`](2_placement/) |
| **3. Adaptation** | keep the game working after the shape changed | worldshape descriptors — ore Y-remap, structure gating, atmosphere, exclusions | [`3_adaptation/`](3_adaptation/) |

Reusable copy-paste starting points (not runnable packs) live in [`templates/`](templates/).

---

## 1. Shape — `1_shape/`

**`floating_island/`** — a whole floating-island dimension in **2 files, ~24 lines, zero copied worldgen**. It references the shipped `isekai_api:hooked_overworld` noise_settings preset (so no `noise_settings` copy at all) and overrides one file — the `isekai_api:hook/final_density` density function — with a `band_density` shape. Override that one hook and the terrain changes; everything else (surface, router) comes from the preset.

> **Hook overrides are datapack-tier.** The override wins because a datapack sits above mod-jar resources in the load order (deterministic). Two mods overriding the same `isekai_api:hook/*` file is mod-vs-mod load order — undefined — so ship shape overrides as a datapack, and treat any preset as *single-owner per world*.

For **replacing the overworld itself** (not adding a dimension), see [`templates/minimal_overworld.json`](templates/minimal_overworld.json) — the full `minecraft:overworld` noise_settings in ~30 lines instead of ~2500, via vanilla density-function references + the hook + the `isekai_api:vanilla_overworld_surface` one-line surface.

## 2. Placement — `2_placement/`

**`moon_world/`** — selects biomes with a tag `applies_to` (`#minecraft:is_overworld`) instead of a 35-entry list, then re-skins every matched biome's surface and stone fill via `block_overrides.surface_top` / `default_block`, wired through the `isekai_api:worldshape_surface_top` / `worldshape_default_block` surface rules.

## 3. Adaptation — `3_adaptation/`

Worldshape descriptors that re-place existing content so a reshaped world still plays.

- **`sky_archipelago/`**, **`flipped/`** — 3-file consumer skeletons (`apply_worldshape_ref` + `apply_worldshape_structures_ref` pointing at a worldshape declared once under `isekai/worldshape/<name>.json`), with `content_overrides` gating lakes on solid ground so water doesn't leak into the void.
- **`runtime_effects/`** — biome / structure modifier packs (`data/<ns>/neoforge/{biome,structure}_modifier/`) that produce visible in-game changes through NeoForge's modifier pipeline.
- **`declaration_only/`** — worldshape / layered_worldshape descriptors that populate Isekai's runtime registry (queryable via `/isekai query worldshape`) without themselves altering chunk generation; wrap them in an `apply_worldshape` modifier to apply.

## Trying a pack

1. Copy the pack directory into your world's `datapacks/` folder (or your mod's `src/main/resources/`).
2. New dimensions and shape/overworld changes apply on **world create**; descriptor-only packs apply on `/reload`.
3. Confirm with `/isekai query dimensions`, `/isekai query worldshape <dim>`, and validate JSON with `/isekai validate <pack-namespace>`.
