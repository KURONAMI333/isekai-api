# sky_archipelago example

3-file minimal config that turns the overworld into a vertically-distributed floating-island world. Copy this directory into your consumer mod's `src/main/resources/data/<modid>/` and rename `<modid>` references in each file.

## Files

```
data/<modid>/isekai/worldshape/sky.json                 — the worldshape (Y range, predicates, exclusions, atmosphere, content_overrides)
data/<modid>/neoforge/biome_modifier/apply_sky.json     — 4-line ref to the worldshape
data/<modid>/neoforge/structure_modifier/apply_sky.json — 4-line ref to the worldshape
data/minecraft/worldgen/overworld_noise_overlay.txt     — manual overlay against vanilla noise_settings (see snippet below)
```

(The `overworld.json` overlay isn't shipped here — copy your project's existing `data/minecraft/worldgen/noise_settings/overworld.json` and swap the `final_density` block for the snippet below.)

## Required overworld.json overlay

```jsonc
{
  // ... keep everything else from vanilla overworld.json ...
  "aquifers_enabled": false,                  // void below islands stays empty
  "ore_veins_enabled": false,                 // optional — Isekai handles ore strategy
  "sea_level": -64,                           // push water way down so it doesn't intersect islands
  "default_fluid": { "Name": "minecraft:air" }, // empty void instead of water-filled
  "noise_router": {
    // ... most fields unchanged ...
    "final_density": {
      "type": "isekai_api:squeeze",
      "argument": {
        "type": "minecraft:interpolated",
        "argument": {
          "type": "minecraft:blend_density",
          "argument": {
            "type": "isekai_api:band_density",
            "active_min_y": 50,
            "active_max_y": 200,
            "gradient_width": 30,
            "noise": {
              "type": "isekai_api:blended_noise",
              "size_xz": 320,
              "size_y": 240
            }
          }
        }
      }
    }
  }
}
```

## Tuning knobs

- **`active_min_y` / `active_max_y`** — where islands actually form. Wider band = more vertical layers.
- **`gradient_width`** — Y-axis transition zone at each end of the active band. 30 is the empirical sweet spot.
- **`blended_noise.size_xz`** — horizontal feature size. Bigger = wider, less fragmented islands. 160/240/320 cover small/medium/large.
- **`blended_noise.size_y`** — vertical feature size. Smaller y-factor = more vertical layers (taller wireframe). 240/360/480 cover dense/normal/sparse.

The `playable_range` in `sky.json` should match the active band roughly (slightly wider is fine for spawn). `default_structure_predicate.y_in_range` should be tightened to the band's middle so structures land on actual islands.

## Worldshape (`sky.json`)

This example's `worldshape.json` also demonstrates `content_overrides.feature_predicates` — lava lakes only spawn where there's solid floor with 3+ blocks of clearance below, preventing them from leaking off cliff edges.
