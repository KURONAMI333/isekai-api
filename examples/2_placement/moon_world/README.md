# moon_world example

A monochrome low-gravity vibe overworld override demonstrating three features:

1. **`block_overrides.surface_top`** — every overworld surface biome's top block becomes light gray concrete (the "moon dust" look).
2. **`block_overrides.default_block`** — stone under the surface becomes diorite (light gray rock).
3. **`applies_to` with biome tags** — uses `#minecraft:is_overworld` instead of listing 35 individual biomes.

## Files

```
data/<modid>/isekai/worldshape/moon.json              — the worldshape with tag-form applies_to + block overrides
data/<modid>/neoforge/biome_modifier/apply_moon.json  — 4-line ref
data/<modid>/neoforge/structure_modifier/apply_moon.json — 4-line ref
data/minecraft/worldgen/noise_settings/overworld.json — manual overlay with the two Isekai surface rules wired in
```

## Required overworld.json overlay snippet (the new bit)

```jsonc
"surface_rule": {
  "type": "minecraft:sequence",
  "sequence": [
    { "type": "isekai_api:worldshape_surface_top", "dimension": "minecraft:overworld" },
    // ... the normal vanilla overworld surface rule sequence here ...
    { "type": "isekai_api:worldshape_default_block", "dimension": "minecraft:overworld" }
  ]
}
```

`worldshape_surface_top` comes first so it gets the top hit before vanilla can put grass/sand/snow. `worldshape_default_block` comes last so vanilla's surface band (grass + dirt + 1 stone) survives, and only the deep fill gets re-skinned.

## How `applies_to` shrinks with tags

```jsonc
// list form — 35-entry biome list
"applies_to": [
  "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains",
  "minecraft:ice_spikes", "minecraft:desert", "minecraft:savanna", ...
]

// tag form — one tag
"applies_to": { "tags": ["#minecraft:is_overworld"] }
```

Both decode to the same set of biomes (tags are vanilla-defined). The list form is there for when you need to be selective.

## How `block_overrides` works

The two block-override rules read from the same `block_overrides` map per biome:

- `surface_top`: top block only — fills the position the surface rule normally places grass/sand/snow.
- `default_block`: every column position currently equal to the noise generator's default block (vanilla stone). Re-skins the bulk fill.

Listing a biome in only one map applies only that one override. Listing in both rules them together (top gets the top block, sub-surface gets the default-block block). Biomes absent from both maps are untouched.
