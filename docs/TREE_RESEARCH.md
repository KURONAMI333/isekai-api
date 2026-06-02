# Isekai Tree Primitive — Research & Design (grounded in real mods)

NeoForge 1.21.1. Goal: a neutral, datapack-configurable tree primitive in Isekai — shape, materials, density all easily set. Based on analysis of vanilla + Terraform API + Terrestria + Regions Unexplored + BetterEnd.

## The proven pattern (all studied mods use it)
Custom `TrunkPlacer` / `FoliagePlacer` subclasses in Java, each with a `MapCodec`, registered into `Registries.TRUNK_PLACER_TYPE` / `Registries.FOLIAGE_PLACER_TYPE` via `DeferredRegister`. Datapacks then use them inside the **vanilla `minecraft:tree` configured_feature** via the registry id (`"trunk_placer": {"type":"isekai_api:leaning", ...}`). Trees stay datapack-authorable, interoperable with vanilla, and blocks stay `BlockStateProvider` slots → neutral. This is exactly Isekai's philosophy. Do NOT invent a bespoke tree JSON format; ride `minecraft:tree`.

### Contract
- `TrunkPlacer.placeTrunk(LevelSimulatedReader, BiConsumer<BlockPos,BlockState>, RandomSource, int freeHeight, BlockPos, TreeConfiguration)` → returns `List<FoliagePlacer.FoliageAttachment>` (anchor points). A branching trunk emits many attachments → many crowns.
- `FoliagePlacer.createFoliage(...)` places leaves around each attachment; also impl `foliageHeight(...)` + `shouldSkipLocation(...)`.
- Base codec helpers: `trunkPlacerParts(inst)` (base_height/height_rand_a/height_rand_b) and `foliagePlacerParts(inst)` (radius/offset IntProviders); `.and(...)` extra fields.
- 1.21.1 gotchas: placer type CODEC must be **MapCodec** (1.20.5+), random is **RandomSource**, IntProvider uniform is flat `{"type":"minecraft:uniform","min_inclusive":N,"max_inclusive":M}`. `minecraft:tree` config requires `decorators` (may be `[]`), ground field is `dirt_provider`(+`force_dirt`).

### Registration (NeoForge)
`DeferredRegister<TrunkPlacerType<?>> X = DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, MODID);`
`X.register("leaning", () -> new TrunkPlacerType<>(LeaningTrunkPlacer.CODEC));` (same for FOLIAGE_PLACER_TYPE; FEATURE for fully-custom features).

## Terraform's key lesson — composable geometry
Reusable core = `Shape` (predicate + bounding box) with analytic primitives (`ellipsoid`, `hemiEllipsoid`, `cone`, `cylinder`, `prism`) + transform layers (`translate/rotate/dilate/noise`) + boolean layers (`subtract/intersect`) + validators (`air`, `safelist`). Foliage placers stream `shape.applyLayer(translate(...)).stream().forEach(setBlock)`. A **noise/jitter layer is essential** — pure analytic shapes look artificial. Express crowns through shapes, not hand-listed offsets.

## Three implementation tiers (pick per shape)
1. **Analytic** (round/broadleaf, hollow canopy): ellipsoid/hemi-ellipsoid via Shape. (Terrestria SphereFoliagePlacer/CanopyFoliagePlacer)
2. **Radius profile** (conifer/cypress/spruce taper): radius = polynomial of normalized height. (Terrestria CypressFoliagePlacer: `6.25x³-12.5x²+6.25x`)
3. **Imperative** (palm fronds, weeping, irregular): hand-coded but parameterize counts/length/spiral. (Terrestria PalmFanFoliagePlacer = top cap + 3x3 support + 2 dangly fronds per cardinal dir w/ spiral twist + droop. RU WillowFoliagePlacer = flat discs + random 1-3 hanging leaves.)
- Curved/spiral/organic beyond offsets → fully custom `Feature<C>` + SDF/spline (BetterEnd UmbrellaTreeFeature/HelixTreeFeature). Overkill; defer.

## Trunk shapes
- **Leaning/palm** (Terrestria BentTrunkPlacer): go straight a few logs, then "move sideways in bendDir + move down 1" repeatedly to stay connected → lean. Return displaced top as attachment.
- **Branching/umbrella** (RU Redwood / vanilla forking): emit branches at descending Y, each tip → an attachment; set `RotatedPillarBlock.AXIS` so branch logs point outward.
- **Mega 2x2**: 4 columns + corner bark + roots.

## Density = placement, not the placer
The placer = shape only. Density (forest vs sparse) = the placed_feature placement (count / rarity_filter) OR Isekai's SpatialPredicate. "森でかすぎ" and "few sparse trees" are placement knobs, solved on Isekai's placement side — keep separate from shape.

## Isekai design (decision)
Add Isekai placer types (neutral-named, geometric): trunk `leaning`, `branching`; foliage `sphere`(ellipsoid+noise), `disc`(flat umbrella), `cone`(conifer profile), `fan`(palm fronds), `weeping`(hanging). Internal `Shape` helper for analytic ones. Consumers compose palms / charred snags / etc. in `minecraft:tree` JSON with block providers + Isekai placement for density. Isekai stays neutral (no baked species/blocks). Build incrementally: register scaffold → 1 trunk + 1 foliage → in-game verify a tree generates → add rest → density → consumer demo.

## Local jar analysis (mods kura actually plays — 1.21.1)
- **Nature's Spirit 2.2.5** = the exact target pattern. 35 `minecraft:tree` features; ~13 CUSTOM placers + vanilla for simple trees. Real param-rich custom placer configs:
  - `coconut_trunk_placer` (palm): `base_height`, `height_rand_a/b`, `fork_probability` (float), `trunk_steps` (IntProvider), `can_grow_through` (block tag). foliage `coconut_foliage_placer` radius 0/offset 0 (crown shape hardcoded in placer).
  - `wisteria_trunk_placer` (drooping): `extra_branch_length`/`extra_branch_steps` (IntProvider), `place_branch_per_log_probability` (float). foliage `wisteria_foliage_placer`.
  - `maple_trunk_placer`: `branch_count` (weighted_list IntProvider), `branch_horizontal_length`, `branch_start/end_offset_from_top` (IntProviders). foliage `maple_foliage_placer` with `hanging_leaves_chance` + `hanging_leaves_extension_chance` + `height` + `radius`.
  - Takeaway: expose params as IntProvider/float in the MapCodec; complex crown geometry lives in the placer's Java. This is precisely Isekai's plan.
- **Biomes O' Plenty 21.1.0.13** = the heavier route. Almost all trees are CUSTOM `Feature`s (`biomesoplenty:big_tree`×16, `twiglet_tree`×14, `basic_tree`×12, `palm_tree`, `redwood_tree`, `cypress_tree`, `bayou_tree`, `mahogany_tree`...), only 2 use vanilla `minecraft:tree`. Uses `minecraft:random_selector` ×40 to mix tree variants per biome (variety/weighting). Confirms: for shapes vanilla placers can't express, mods write custom Features; `random_selector` is how you mix species + weight rarity.
- **Hexerei 0.5.0.3**: no datapack worldgen (trees are code/structure-based) — not a useful reference.

Conclusion: Isekai's plan (custom placers within `minecraft:tree`, param-rich MapCodecs, neutral block providers) is the mainstream proven approach (Nature's Spirit, Terrestria, Regions Unexplored). Offer `random_selector`-style mixing + Isekai placement for density/variety. Reserve custom `Feature` for shapes the placer/attachment model can't express.

## Sources
Minecraft Wiki (Tree_definition, Configured_feature), NeoForged docs 1.21.1 (registries), TerraformersMC/Terraform (shapes-api, tree-api), TerraformersMC/Terrestria (Bent/PalmFan/Sphere/Cypress placers), UHQ-GAMES-MODS/RegionsUnexplored (Redwood/Willow placers, NeoForge DeferredRegister), paulevsGitch/BetterEnd (SDF features), Soumeh/1.21.1-Deobfuscated (codec structure).
