# Floating island — 30 lines, zero copied worldgen

A complete floating-island **dimension** from two small files, by leaning on the shipped
`isekai_api:hooked_overworld` noise_settings preset.

```
data/skyland_example/dimension/skyland.json                          (8 lines)
data/isekai_api/worldgen/density_function/hook/final_density.json    (16 lines)
```

- **`dimension/skyland.json`** points `generator.settings` at `isekai_api:hooked_overworld` — so
  there is **no `noise_settings` copy at all**. Biomes come from a `minecraft:fixed` source here;
  swap in `isekai_api:rule` / `climate_zones` for a multi-biome sky.
- **`hook/final_density.json`** overrides the preset's terrain hook (default = vanilla-shaped
  ground) with a `band_density` shape: solid only inside `Y 50..200`, void above and below.
  This is the *only* file that decides the shape. Change the band, change the world.

The override lands in the **`isekai_api`** namespace on purpose — that is how a datapack replaces
the mod's default hook (datapack resources sit above mod-jar resources in the load order). Keep it
in a datapack (not a second mod jar) so the override is deterministic, and treat the
`hooked_overworld` preset as single-owner per world.

Launch, create a new world, and travel to the `skyland_example:skyland` dimension
(`/execute in skyland_example:skyland run tp @s 0 128 0`).

To reshape: edit only `hook/final_density.json` — e.g. `band_density.invert: true` hangs the
terrain from the top instead, `solidity_bias` shifts islands ↔ continents.
