# flipped example

3-file minimal config that inverts the overworld — terrain hangs from the top of the world like stalactite biomes, with the sky/void below. Same structure as `../sky_archipelago/` but `band_density.invert` is set to true.

## Files

```
data/<modid>/isekai/worldshape/flipped.json
data/<modid>/neoforge/biome_modifier/apply_flipped.json
data/<modid>/neoforge/structure_modifier/apply_flipped.json
data/minecraft/worldgen/noise_settings/overworld.json   — overlay using band_density invert=true
```

## Required overworld.json overlay snippet

```jsonc
"final_density": {
  "type": "isekai_api:squeeze",
  "argument": {
    "type": "minecraft:interpolated",
    "argument": {
      "type": "minecraft:blend_density",
      "argument": {
        "type": "isekai_api:band_density",
        "active_min_y": 40,
        "active_max_y": 200,
        "gradient_width": 30,
        "invert": true,
        "noise": {
          "type": "isekai_api:blended_noise",
          "size_xz": 320,
          "size_y": 360
        }
      }
    }
  }
}
```

The key change vs `sky_archipelago` is `"invert": true` on `band_density` — flips both Y envelopes so terrain forms hanging from the top of the active band instead of sitting at the bottom.

## Notes

- Spawn carefully: with terrain at the top of the world, the player's default Y=64 spawn lands inside the void. Set a custom spawn point via gameplay or a `setspawn` rule.
- Atmosphere should reflect the inverted feel (overcast fog color, dim sky).
- `ore_strategy: isekai:inverted` makes diamond etc spawn near Y=240 instead of Y=-50 (since the "ground" is up there).
