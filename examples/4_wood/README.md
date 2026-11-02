# A complete wood species, in one class and one data pack

This directory is a working one-species mod. It is compiled with Isekai and loaded by the GameTest
server, so it cannot rot: if `WoodSet`, the generators or the validation change in a way that breaks
this example, the build says so.

| File | What it is |
|---|---|
| `java/com/kuronami/isekaiapi/examples/wood/ExampleWoodMod.java` | the whole mod: three `DeferredRegister`s, one `WoodSet` builder chain, one tree, one `GatherDataEvent` listener |
| `example_species/` | the pack the species needs — loot tables, recipes, tags, blockstates, models, textures, names, and the tree's configured feature |
| `example_species/data/isekai_example/worldgen/configured_feature/example_tree.json` | the tree the sapling grows, hand-written |

## What the example builds

Fifteen blocks and their items (log, wood, stripped log, stripped wood, planks, stairs, slab, fence,
fence gate, door, trapdoor, button, pressure plate, sign + wall sign, hanging sign + wall hanging
sign, leaves, sapling, potted sapling), two boat entities with their items, and the vanilla
behaviour that comes with them: axe stripping, sign block entities, flower-pot acceptance, burning,
composting, fuel values, boat rendering.

## Reading order

1. `ExampleWoodMod.onConstruct` — the builder chain. This is the entire declaration.
2. `example_species/data/isekai_example/worldgen/configured_feature/example_tree.json` — the tree
   shape. A trunk placer, a foliage placer, one decorator, and the species' own log and leaves as
   providers.
3. `example_species/data/isekai_example/recipe/` — what a sawn wood family sells as recipes.
4. `example_species/data/minecraft/tags/` — the vanilla tags a species joins. These are additive:
   a tag file without `"replace": true` adds to the vanilla entries, it does not overwrite them.

## Using it as a template

Copy the class, rename `MOD_ID`, the wood set id and the tree's configured feature key, then either
write the data by hand or regenerate it:

```bash
../../_tools/gw <this repository directory> dataExample
```

`dataExample` runs the bundled generators into `build/example_species` and copies the result onto
`examples/4_wood/example_species`, replacing only the generated subtrees. Two things are
hand-written and survive a regeneration: `pack.mcmeta` (the pack format and description) and the
tree's configured feature. The textures are placeholders — flat 16×16 patterns keyed to
"bark / planks / leaves" so a screenshot reads correctly; replace them with real art.

The pack is registered from code, not from a folder name:

```java
event.addPackFinders(
        ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "example_species"),
        event.getPackType(), …);
```

The location's namespace must be a **real mod id** because NeoForge resolves the path through that
mod's files, which is why the pack says `isekai_api:example_species` while the species registers
under `isekai_example`. A mod that ships its data in `src/main/resources/data/` needs none of this.

## Why the pack is not in the published jar

The example carries `data/minecraft/tags/**` entries for `isekai_example` blocks. Shipped to
consumers, those files would reference blocks that do not exist in their game. `build.gradle`
therefore keeps `example_species/**` and `com/kuronami/isekaiapi/examples/**` out of both the main
and the sources jar, and the example only registers while a GameTest server (or the
`dataExample` run) is active.

## What the tests check

`gametest/wood/ExampleSpeciesGameTests` grows the sapling with bone meal on a real server,
strips the log with an axe, and asks the recipe manager whether every part has a recipe it can
match — plus `WoodSetValidationGameTests`, which runs `/isekai validate`'s audit over this species
and expects no gaps at all.
