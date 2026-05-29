# Isekai API — Modrinth / CurseForge publish description

> Paste-ready project description. The first 1–2 lines are the card summary (SEO-critical).
> Categories: **library** (primary) + **worldgen** (secondary).
> Tags/loaders: **neoforge**, **server-side** (worldgen runs server-side; safe on clients too).
> Game versions: **1.21.1**.

---

**A neutral primitive language for Minecraft worldgen — compose any worldshape from datapack.**
Floating islands, hanging continents, hollow shells, vertical biome layers, brand-new dimensions — all from JSON, no Java required. Coexists with Terralith, TerraBlender, YUNG's, and Journeymap.

## What it is

Isekai API is to worldgen what `div`/`span` are to web pages: a small set of **neutral, composable primitives** rather than a bundle of pre-made worlds. You combine them to express *any* worldshape you can imagine. Isekai ships no biomes, no structures, no "themes" of its own — it gives modders and datapack authors the building blocks.

## Features

- **20 density-function primitives** — 16 math/geometry building blocks (`add`, `clamp`, `distance`, `scale_coord`, `mask_y_range`, …) + 4 worldshape composers (`squeeze`, `y_envelope`, `blended_noise`, `band_density`). Express floating islands, inverted terrain, hollow worlds, capped mountains.
- **Rule-based biome placement** (`isekai_api:rule`) — put biomes anywhere by spatial rules: vertical layers (`y_below 20 → deep_dark`), concentric rings (`within_distance 1000 → desert`), regions. No TerraBlender climate-tuning required.
- **Per-biome surface & fill blocks** — re-skin any biome's top block or stone fill from JSON (`worldshape_surface_top` / `worldshape_default_block`).
- **Declarative worldshape descriptors** — one JSON file declares Y range, structure-placement predicates, feature gating, biome exclusions, atmosphere (sky/fog/water colors), ore & mob remapping. Apply via a 4-line biome/structure modifier.
- **New dimensions, pure datapack** — combine the primitives with vanilla `dimension/` + `dimension_type/` JSON to ship an entire new dimension with zero Java.
- **Java API** — `Isekai.query()` to read vanilla/modded worldgen rules, `Isekai.remap()` to declare worldshapes programmatically.
- **Server-start validation** — typo'd registry keys (`minecraft:ocean_monument` → the real `minecraft:monument`) and broken references are reported at boot, before they silently no-op.

## Compatibility

Tested in-game on NeoForge 1.21.1, all loaded simultaneously without crash:

| Mod | Support | Notes |
|---|---|---|
| TerraBlender | compatible | biome framework; coexists |
| Terralith | compatible* | *both overhaul the overworld — only one can own `overworld.json` (last loaded wins). Isekai **new-dimension** worlds coexist fully with Terralith. |
| William Wythers' Overhauled Overworld | compatible* | same overworld-override caveat as Terralith |
| Nullscape | compatible | End overhaul — fully independent dimension, coexists |
| YUNG's Better X (Ocean Monuments, etc.) | compatible | structure mixins coexist with Isekai's |
| Lithostitched | compatible | worldgen merge library; different niche |
| Journeymap / map mods | compatible | renders Isekai terrain & dimensions normally |

**Rule of thumb:** an Isekai **new-dimension** mod coexists with everything. An Isekai **overworld-override** mod is mutually exclusive with other overworld-overhaul mods (true of all overworld overhauls, not specific to Isekai) — but never crashes; the last-loaded datapack simply wins.

## Getting started

- **Datapack authors:** see the [Datapack Reference](https://github.com/KURONAMI333/isekai-api/blob/main/docs/DATAPACK_REFERENCE.md) (every key + JSON shape) and copy a skeleton from [`examples/`](https://github.com/KURONAMI333/isekai-api/tree/main/examples).
- **Java modders:** add `isekai_api` as a `compileOnly` dependency and use the `Isekai` facade. Everything outside the `api` package is `@ApiStatus.Internal`.

## License & links

MIT. Source, docs, and issue tracker: https://github.com/KURONAMI333/isekai-api

*Unrelated to the "Isekai Adventure" modpack.*
