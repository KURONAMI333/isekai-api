Build world generation from datapack JSON: terrain shape, biome placement, surface blocks, trees and set-pieces, and the re-placement of ores, structures and mobs that already exist, in the overworld, the nether, the end or a dimension of your own.

Isekai API is a library for datapack authors, pack makers and mod developers. It adds no biomes, structures or themes of its own. It supplies the parts; the world is yours.

## What you can build

- **Terrain shape**: density-function primitives and worldshape composers for floating islands, shells, layered or capped terrain. The `isekai_api:hooked_overworld` and `isekai_api:hooked_nether` presets reshape vanilla terrain through one hook file, so you never copy vanilla's `noise_settings`.
- **Biome placement**: `isekai_api:rule` places biomes by Y bands, distance, boxes and noise. `isekai_api:climate_zones` matches vanilla's climate axes. `isekai_api:override` wraps an existing biome source, vanilla's included, and replaces the biome only inside the zones you draw or wherever the base already put a given biome.
- **Surface and atmosphere**: per-biome top block and stone fill, strata bands, and sky, fog and water colours per biome or per dimension, synced to clients on dedicated servers.
- **Trees and features**: trunk placers (`leaning`, `curved`, `branching`), foliage placers (`sphere`, `fan`, `frond`, `cone`, `disc`, `weeping`), tree decorators, fluid pools, block clusters, and placement filters by slope, block context, distance to fluid or distance to a biome.
- **Set-pieces**: `isekai_api:grounded_template` puts a hand-built NBT structure only on flat, dry ground, with `/locate` and structure-set spacing.
- **Existing content in a new shape**: move vanilla or modded ores, structures and mobs to where your world now has ground, scoped to the dimension you declare. Ore depth can be measured from each column's own surface, so every floating island gets the same ore layout at any altitude.

## Checking a world

- `/isekai validate`, given your namespace, checks your JSON and your wood sets (loot tables, tags, recipes, models, textures, language keys) in one pass.
- `/isekai validate dimension` reports a dimension's build height, rules that match no biome, and whether the start point is safe.
- `/isekai survey biomes` measures a seed's biome shares and patch sizes over a radius without generating chunks. `/isekai survey` and `/isekai fit`, given a structure, show where a structure can spawn and which check rejected the rest.

All commands need operator permission (level 2).

## Getting started

Start with the [floating-island datapack](https://github.com/KURONAMI333/isekai-api/tree/main/examples/1_shape/floating_island), then edit its terrain hook. The [Datapack Reference](https://github.com/KURONAMI333/isekai-api/blob/main/docs/DATAPACK_REFERENCE.md) lists every key with its JSON shape, and [`examples/`](https://github.com/KURONAMI333/isekai-api/tree/main/examples) has runnable packs for shape, placement, adaptation and wood sets.

Java mods add `isekai_api` as a `compileOnly` dependency (see the [compatibility guide](https://github.com/KURONAMI333/isekai-api/blob/main/docs/COMPATIBILITY.md)) and use the `Isekai` facade: `Isekai.query()` reads vanilla and modded worldgen rules, `Isekai.remap()` declares worldshapes in code, and `WoodSet` registers a complete wood set with signs, boats and a sapling. Biome zones, spatial predicates, remap strategies, surface anchors and transition rules are registries, so your mod can add its own variant and use it from JSON.

## Before you build

- Worldgen changes apply to newly generated chunks. Existing chunks keep their terrain, and `/reload` does not change worldgen rules; restart the server.
- A nether or end on an Isekai biome source opens without the experimental-settings prompt when it keeps the stock dimension type and a `minecraft:noise` generator. Worldgen entries in a user datapack (dimension types, noise settings, biomes, features) still bring up vanilla's prompt.
- Climate, colours and sounds belong to the biome, so one biome looks the same in every dimension.
- 3.0.0 scopes mob spawns, structures and carvers to the dimension that declares them. 2.x datapacks load unchanged; the [changelog](https://github.com/KURONAMI333/isekai-api/blob/main/CHANGELOG.md) lists what moved.

Unrelated to the Isekai Adventure modpack.

<p><a href="https://www.patreon.com/KURONAMI333?utm_source=cf&utm_medium=mod-description&utm_campaign=support&utm_content=isekai-api"><img src="https://media.forgecdn.net/attachments/description/1564253/description_0b5d69f3-bf0d-4a38-8894-6883abaa65e9.png" width="250" height="100" alt="Support my mods on Patreon"></a> <a href="https://x.com/kuronami333"><img src="https://media.forgecdn.net/attachments/description/1564253/description_0ed421f9-f6e1-4334-8e1e-fac25c804fe6.png" width="250" height="100" alt="Follow @kuronami333 on X"></a> <a href="https://www.youtube.com/channel/UCoimycJCVZmxNKyX9Vr7Apg"><img src="https://media.forgecdn.net/attachments/description/1564253/description_b953be14-e0c1-4290-b5d3-5ae75107ce90.png" width="250" height="100" alt="Watch KURONAMI on YouTube"></a></p>

<p><img src="https://media.forgecdn.net/attachments/description/1537959/description_582d8b5a-1840-40ee-b403-c361b7f74006.png" alt="Need a custom mod?" width="776" height="95"></p>

<p align="center" style="text-align: center;">Paid commissions for videos, streams and servers. Send me your idea: <a href="mailto:kuronami.mods@gmail.com">kuronami.mods@gmail.com</a></p>

<p><img src="https://media.forgecdn.net/attachments/description/1537959/description_a7712950-22d2-40cb-94f6-b8edc51870b9.png" alt="" width="776" height="56"></p>

MIT License. Free to use, fork and include in any modpack.
Bugs and questions: comment on the CurseForge page, or DM [@kuronami333 on X](https://x.com/kuronami333).
Source code: https://github.com/KURONAMI333/isekai-api
