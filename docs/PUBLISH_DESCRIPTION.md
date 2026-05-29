# Isekai API — Modrinth / CurseForge publish description

> First line is the card summary (SEO-critical). Capability-first, community-welcoming, no consumer comparisons.
> Categories: **library** (primary) + **worldgen** (secondary). Loaders: **neoforge**. Game versions: **1.21.1**.

---

**Summary (card):**
Build any world from datapack — a toolbox of neutral worldgen primitives for shape, biomes, dimensions, and re-placing existing content. No Java required.

---

**Isekai API is a toolbox for the *shape and rules* of a Minecraft world — not a world, but the machine that makes worlds.** Like `div`/`span` for web pages, it's a small set of neutral, composable primitives you combine to build *any* world you can imagine, straight from datapack JSON. It ships no biomes, structures, or themes of its own.

## What you can control

*Every axis of world generation, as composable primitives:*

- **Terrain shape** — 16 math/geometry density primitives + 4 worldshape composers (`squeeze`, `y_envelope`, `blended_noise`, `band_density`). Floating islands, hanging or inverted continents, hollow shells, capped mountains, mirrored/tiled space — any 3D form on any Y band.
- **Biome placement** — the `isekai_api:rule` biome source places biomes by pure spatial rules: vertical layers, concentric rings, regions, and `and`/`or`/`not` combos (`y_below 20 -> deep_dark`). No climate-noise tuning.
- **Surface & fill** — re-skin any biome's top block or stone fill from JSON.
- **New dimensions** — combine the above with vanilla dimension JSON to ship a whole new dimension. Zero Java, zero vanilla edits.
- **Atmosphere** — per-world sky, fog, and water colors.
- **Placement control** — decide exactly where features/structures go: relative to surface or fluid, by block context, Y range, slope, proximity to a block or biome, and logical combos.
- **Re-place existing content** — take any vanilla *or modded* ore, structure, mob, or feature and redistribute it into your world's new shape. A rare ore vanilla buries deep can be remapped to the underside of your floating islands. Strategies: linear, inverted, fixed-range, count-scaling, band-split, pipelines — plus exclude/add.
- **Boot-time validation** — broken or mistyped registry references are caught at server start, before they silently no-op.
- **Java API** — `Isekai.query()` reads vanilla/modded worldgen rules; `Isekai.remap()` declares worldshapes in code.

## Built to be built on

Isekai isn't tied to any one world or author. **However you want to use it, you're welcome** — datapack worldshapes, full Java mods, modpack glue, quick experiments, total conversions. MIT licensed: free to use, fork, and bundle in modpacks.

- **Datapack authors:** the [Datapack Reference](https://github.com/KURONAMI333/isekai-api/blob/main/docs/DATAPACK_REFERENCE.md) lists every key with its JSON shape; copy a runnable skeleton from [`examples/`](https://github.com/KURONAMI333/isekai-api/tree/main/examples) (`sky_archipelago`, `flipped`, `moon_world`).
- **Java modders:** add `isekai_api` as a `compileOnly` dependency and use the `Isekai` facade. Everything outside the `api` package is internal, so the public surface stays small and stable.
- **Questions, ideas, bugs:** [open an issue](https://github.com/KURONAMI333/isekai-api/issues). Built a world with Isekai? Share it — I'd love to see what people make.

MIT · [Source & docs](https://github.com/KURONAMI333/isekai-api). *Unrelated to the "Isekai Adventure" modpack.*
