# Seven-worldshape self-test (v2, machine-checked)

Seven worldshapes, each expressed as a scratch datapack against the v2 API and backed by at
least one machine assertion of its characteristic behaviour (not just a decode). The asserts
live in `src/test/java/.../examples/SevenExamplesSelfTest.java` (unit: remap math, biome
selection, predicate delegation) and `src/main/java/.../gametest/IsekaiSelfTestGameTests.java`
(server-in-the-loop: density shape, rule biome source, sea_level). These are validation
fixtures — they are not shipped in the jar.

| # | Worldshape | Verdict | v2 expression | Machine assertion |
|---|---|---|---|---|
| 1 | Desert + oasis | ✓ | `applies_to` biome selection + `additions.features` | `BiomeMatcher` applies to desert, **not** ocean; one oasis feature injected (unit) |
| 2 | Magma + ant-nest caves | ✓ | density subtraction `add(terrain, negate(cave_mask))` + `ore_strategy: pipe[linear, count_scale 0.7]` + carver addition | subtraction removes exactly the cave band (Δ=3.0) at cave Y, terrain solid elsewhere (gametest); count factor 0.7 (unit) |
| 3 | Needle mountains | ✓ | `multiply`-of-noise sharp peaks + `ore_strategy: linear` | Linear projects a deep ore band proportionally into the tall `[0,300]` playable band (unit); density resolves against the live registry (gametest) |
| 4 | Submerged (sea level 200) | △ | `in_fluid(water)` predicate (Isekai) + a hooked noise_settings with `sea_level: 200` (consumer vanilla territory) | in_fluid gates on fluid (unit); noise_settings decodes with `sea_level == 200` (gametest) — see note |
| 5 | One biome per region | ✓ | `isekai_api:rule` biome source + `BiomeZone` | rule source resolves desert at the centre zone, plains far out (gametest) |
| 6 | Mirror world | ✓* | `band_density(invert)` + `ore_strategy: inverted` + `solid_ceiling` gate | inverted band yields solid floor **and** solid ceiling with a void gap — the mirror signature a normal world never has (gametest); Inverted + SolidCeiling (unit) |
| 7 | Spherical (Mario Galaxy) | ✓ | `add(constant R, negate(distance xyz))` + `solid_floor` gate | solid inside the shell radius, void outside (gametest); SolidFloor gate (unit) |

**6 of 7 fully expressible; item 4 is △.** No hard expressibility gap — nothing required an API
change, and no example failed to decode.

## Item 4 (△) — why the shape half stays consumer territory

The **adaptation** half of a submerged world is fully Isekai-owned: `in_fluid(water)` gates
structures/features to underwater placement, asserted in the unit test.

The **water shape** half is not something the shape-hook abstracts. `sea_level` is a one-line
field on the noise_settings (the hooked preset makes the whole document ~30 lines, not a 2500-line
copy — asserted `sea_level == 200`), but genuine flooding needs the aquifer fluid router axes
(`barrier`, `fluid_level_floodedness`, `fluid_level_spread`, `lava`) filled with vanilla noise —
and those have no standalone density_function file to reference, so they must be inlined by the
consumer. The hook covers terrain *shape* (`final_density`), not the *fluid* router. This matches
the v1 verdict (consumer責務) and confirms SPEC §0: Isekai's value is adaptation automation, not
owning every noise_settings field.

## Item 6 (✓*) — mechanically expressible, aesthetically constrained

`band_density(invert)` and `scale_coord(sy=-1)` both express a mirrored/ceiling world, and the
gametest proves the signature (solid ceiling terrain). The caveat is aesthetic, not expressive:
a *playable* literal upside-down world is janky because Minecraft's surface rules, water, and
gravity all assume "up = sky" (this is why the mod-037 flipped consumer was shelved). The API
expresses the shape; making it a pleasant world to play is a content/gameplay concern outside v2.
