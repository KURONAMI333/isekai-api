package com.kuronami.isekaiapi.gametest;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.ColumnBand;
import com.kuronami.isekaiapi.api.remap.RemapContext;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.placementmodifier.ColumnRelativeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Machine checks for the terrain-relative ore path ({@code isekai_api:column_local} +
 * {@code isekai_api:world_floor} + {@code isekai_api:column_relative}).
 *
 * <p>The load-bearing one is {@link #columnRemapIsAltitudeInvariant}: two synthetic floating
 * bodies of identical thickness are built 200 blocks apart vertically, and the same band is
 * resolved against both with an identically seeded random source. Every sample must land at the
 * same depth into its body. That is the property the whole feature exists for, and an absolute
 * Y band cannot satisfy it.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiColumnRemapGameTests {

    private IsekaiColumnRemapGameTests() {}

    /** Synthetic bodies: {air below, lowest solid, highest solid, air above}. */
    private static final int LOW_BODY_BOTTOM = 40;
    private static final int LOW_BODY_TOP = 79;
    private static final int HIGH_BODY_BOTTOM = 240;
    private static final int HIGH_BODY_TOP = 279;
    /** A third, deliberately thinner body — proportional mode must scale against it. */
    private static final int THIN_BODY_BOTTOM = 200;
    private static final int THIN_BODY_TOP = 219;

    /** One column holding three separate bodies — the shape the column walk exists for. */
    private static final int STACK_LOW_BOTTOM = 40;
    private static final int STACK_LOW_TOP = 79;
    private static final int STACK_MID_BOTTOM = 160;
    private static final int STACK_MID_TOP = 199;
    private static final int STACK_TOP_BOTTOM = 240;
    private static final int STACK_TOP_TOP = 279;

    private static final int CLEAR_FROM = 30;
    private static final int CLEAR_TO = 300;

    private static final long SEED = 20260804L;

    private static final VerticalRange COSMOS_COAL = new VerticalRange(51, 62, HeightDistribution.UNIFORM);
    private static final VerticalRange COSMOS_DIAMOND = new VerticalRange(-63, -48, HeightDistribution.UNIFORM);
    /**
     * Vanilla's {@code ore_andesite_upper}: entirely above the default reference surface, so
     * {@code ColumnLocal.depthOf} clamps both ends onto depth 0 and the band collapses.
     */
    private static final VerticalRange ANDESITE_UPPER = new VerticalRange(64, 128, HeightDistribution.UNIFORM);

    private static final RemapContext CTX = new RemapContext(
            new VerticalRange(-64, 320, HeightDistribution.UNIFORM), -64, 319);

    // =====================================================================
    // The altitude-invariance gate.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void columnRemapIsAltitudeInvariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);

        // Three isolated columns, well clear of the 3x3 arena footprint.
        int lowX = origin.getX() + 6;
        int highX = origin.getX() + 8;
        int thinX = origin.getX() + 10;
        int z = origin.getZ() + 6;

        buildBody(level, lowX, z, LOW_BODY_BOTTOM, LOW_BODY_TOP);
        buildBody(level, highX, z, HIGH_BODY_BOTTOM, HIGH_BODY_TOP);
        buildBody(level, thinX, z, THIN_BODY_BOTTOM, THIN_BODY_TOP);

        // Read the heightmap only after every block is in place: WORLD_SURFACE_WG is primed
        // lazily on a loaded chunk, so an early read would freeze a stale surface.
        if (!surfaceIs(helper, level, lowX, z, LOW_BODY_TOP + 1)) return;
        if (!surfaceIs(helper, level, highX, z, HIGH_BODY_TOP + 1)) return;
        if (!surfaceIs(helper, level, thinX, z, THIN_BODY_TOP + 1)) return;

        PlacementContext ctx = placementContext(level);

        // (a) world_floor finds each body's underside, not the world's floor.
        SurfaceAnchor floor = SurfaceAnchor.WorldFloor.DEFAULT;
        Integer lowFloor = floor.resolveY(ctx, new BlockPos(lowX, 0, z));
        Integer highFloor = floor.resolveY(ctx, new BlockPos(highX, 0, z));
        if (lowFloor == null || lowFloor != LOW_BODY_BOTTOM - 1) {
            helper.fail("world_floor under the low body: expected " + (LOW_BODY_BOTTOM - 1) + ", got " + lowFloor);
            return;
        }
        if (highFloor == null || highFloor != HIGH_BODY_BOTTOM - 1) {
            helper.fail("world_floor under the high body: expected " + (HIGH_BODY_BOTTOM - 1) + ", got " + highFloor);
            return;
        }

        // (b) a shallow (surface-anchored) band lands at identical depths in both bodies.
        ColumnBand shallow = RemapStrategy.ColumnLocal.DEFAULT.remapToColumn(COSMOS_COAL, CTX).orElseThrow();
        List<Integer> lowDepths = depthsBelowSurface(ctx, shallow, lowX, z, LOW_BODY_TOP + 1);
        List<Integer> highDepths = depthsBelowSurface(ctx, shallow, highX, z, HIGH_BODY_TOP + 1);
        if (!lowDepths.equals(highDepths)) {
            helper.fail("shallow band drifted with altitude: low=" + lowDepths + " high=" + highDepths);
            return;
        }
        for (int d : lowDepths) {
            if (d < 2 || d > 13) {
                helper.fail("shallow band escaped its 2..13 depth window: " + d);
                return;
            }
        }

        // (c) a deep (floor-anchored) band likewise, measured up from each body's underside.
        ColumnBand deep = RemapStrategy.ColumnLocal.DEFAULT.remapToColumn(COSMOS_DIAMOND, CTX).orElseThrow();
        List<Integer> lowHeights = heightsAboveFloor(ctx, deep, lowX, z, LOW_BODY_BOTTOM - 1);
        List<Integer> highHeights = heightsAboveFloor(ctx, deep, highX, z, HIGH_BODY_BOTTOM - 1);
        if (!lowHeights.equals(highHeights)) {
            helper.fail("deep band drifted with altitude: low=" + lowHeights + " high=" + highHeights);
            return;
        }
        for (int h : lowHeights) {
            if (h < 1 || h > 16) {
                helper.fail("deep band escaped its 1..16 height window: " + h);
                return;
            }
        }

        // (d) proportional mode scales with the body instead: same fraction, different blocks.
        ColumnBand half = new ColumnBand(SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                0.5, 0.5, ColumnBand.DepthScale.PROPORTIONAL, ColumnBand.VANILLA_THICKNESS,
                HeightDistribution.UNIFORM);
        int thickDepth = depthsBelowSurface(ctx, half, highX, z, HIGH_BODY_TOP + 1).get(0);
        int thinDepth = depthsBelowSurface(ctx, half, thinX, z, THIN_BODY_TOP + 1).get(0);
        // High body spans 41 blocks between its two free spaces, thin body 21.
        if (thickDepth != 21 || thinDepth != 11) {
            helper.fail("proportional depths wrong: 41-thick body -> " + thickDepth
                    + " (want 21), 21-thick body -> " + thinDepth + " (want 11)");
            return;
        }

        helper.succeed();
    }

    // =====================================================================
    // The descriptor path: column_local rewrites placement, not just the Y numbers.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void columnLocalDescriptorInjectsColumnRelative(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Biome plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(
                plains.modifiableBiomeInfo().getOriginalBiomeInfo());
        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.OVERWORLD)
                .playableRange(new VerticalRange(-64, 320, HeightDistribution.UNIFORM))
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(RemapStrategy.ColumnLocal.DEFAULT)
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .build();

        RemovePhase.originalsPendingRemap(descriptor, Biomes.PLAINS, builder);
        AddPhase.remappedOreFeatures(descriptor, Biomes.PLAINS, builder);

        int injected = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : builder.getGenerationSettings().getFeatures(step)) {
                if (h.unwrapKey().isPresent()) continue;   // registry ref = not one we injected
                injected++;
                boolean hasColumn = false;
                for (PlacementModifier mod : h.value().placement()) {
                    if (mod instanceof HeightRangePlacement) {
                        helper.fail("injected feature still carries an absolute height_range");
                        return;
                    }
                    if (mod instanceof ColumnRelativeModifier) hasColumn = true;
                }
                if (!hasColumn) {
                    helper.fail("injected feature has no column_relative modifier");
                    return;
                }
            }
        }
        if (injected == 0) {
            helper.fail("column_local descriptor injected nothing");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Scope guard: ore_strategy sweeps every ranged feature in a matched biome, so the cosmos
    // planets must not list a non-ore feature that carries a height_range.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void cosmosStrategySweepsOresOnly(GameTestHelper helper) {
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            helper.fail("no snapshot — cannot check the cosmos sweep set");
            return;
        }
        String[] planets = {"verdant", "ember", "frost", "stone", "desert",
                "mushroom", "jungle", "volcanic", "crystal", "dead"};
        for (String planet : planets) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath("isekai_verify", "planet_" + planet));
            Set<ResourceKey<PlacedFeature>> inBiome = snapshot.featuresInBiome(key);
            if (inBiome.isEmpty()) {
                helper.fail("planet_" + planet + " has no features in the snapshot");
                return;
            }
            int swept = 0;
            for (var info : snapshot.placedFeatures()) {
                if (snapshot.isFallback(info)) continue;
                if (!inBiome.contains(info.key())) continue;
                swept++;
                ResourceLocation id = info.key().location();
                if (!id.getNamespace().equals("isekai_verify") || !id.getPath().startsWith("ore_")) {
                    helper.fail("planet_" + planet + ": ore_strategy would also sweep " + id
                            + " — it carries a height_range but is not one of the planet ores");
                    return;
                }
            }
            if (swept < 8) {
                helper.fail("planet_" + planet + " only exposes " + swept + " remappable ores (want >= 8)");
                return;
            }
        }
        helper.succeed();
    }

    // =====================================================================
    // Every body in the column, not just the topmost one.
    // =====================================================================

    /**
     * A column of floating terrain holds more than one island, and each of them is terrain the
     * band describes. Before the column walk, the two anchors always bracketed the <i>topmost</i>
     * body — the heightmap by definition, and world_floor by scanning down from it — so an island
     * with anything floating above it received no ore at all. That is the mechanism behind Sky
     * World issue #2: the probe found andesite, granite, diorite, coal, iron and copper at exactly
     * 0 in the lower band, in a region where 29 of 29 lower-island chunks also carried an upper
     * island.
     *
     * <p>Pins the pair of defects together as well: a band that {@code collapsedAtAnchor()} (the
     * 2.1.0 clamp) must land in solid ground in <i>every</i> body, not in air and not only in the
     * topmost one.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void columnRemapReachesEveryBody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // Arenas sit 8 blocks apart along X, so an X offset of 14 or more reaches into the next
        // test's working columns. Separate along Z instead — rows are 9 apart, and every other
        // test in this class works at Z+6.
        int x = origin.getX() + 6;
        int z = origin.getZ() + 8;

        int[][] bodies = {{STACK_TOP_BOTTOM, STACK_TOP_TOP},
                          {STACK_MID_BOTTOM, STACK_MID_TOP},
                          {STACK_LOW_BOTTOM, STACK_LOW_TOP}};
        buildStack(level, x, z, bodies);
        if (!surfaceIs(helper, level, x, z, STACK_TOP_TOP + 1)) return;

        PlacementContext ctx = placementContext(level);
        BlockPos pos = new BlockPos(x, 0, z);

        // The heightmap read and the block walk must agree on the topmost body, or the first
        // body and the rest would drift apart on any column their predicates disagree about.
        Integer viaHeightmap = SurfaceAnchor.WorldSurface.INSTANCE.resolveY(ctx, pos);
        Integer viaWalk = SurfaceAnchor.WorldSurface.INSTANCE
                .resolveYBelow(ctx, pos, level.getMaxBuildHeight());
        if (viaHeightmap == null || !viaHeightmap.equals(viaWalk)) {
            helper.fail("world_surface disagrees with itself: heightmap=" + viaHeightmap
                    + " walk=" + viaWalk);
            return;
        }

        ColumnBand shallow = RemapStrategy.ColumnLocal.DEFAULT.remapToColumn(COSMOS_COAL, CTX).orElseThrow();
        ColumnBand collapsed = RemapStrategy.ColumnLocal.DEFAULT
                .remapToColumn(ANDESITE_UPPER, CTX).orElseThrow();
        if (!collapsed.collapsedAtAnchor()) {
            helper.fail("the collapsed-band case is no longer collapsed — pick another source range");
            return;
        }

        for (ColumnBand band : List.of(shallow, collapsed)) {
            ColumnRelativeModifier modifier = new ColumnRelativeModifier(band);
            RandomSource random = RandomSource.create(SEED);
            for (int i = 0; i < 24; i++) {
                List<BlockPos> placed = modifier.getPositions(ctx, random, pos).toList();
                if (placed.size() != bodies.length) {
                    helper.fail("expected one placement per body (" + bodies.length + "), got "
                            + placed.size() + ": " + placed);
                    return;
                }
                for (int b = 0; b < bodies.length; b++) {
                    int y = placed.get(b).getY();
                    if (y < bodies[b][0] || y > bodies[b][1]) {
                        helper.fail("placement " + b + " at Y" + y + " is outside body "
                                + bodies[b][0] + ".." + bodies[b][1]);
                        return;
                    }
                    if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        helper.fail("placement " + b + " at Y" + y + " landed in air");
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * The regression side of the same change: a column holding one body behaves exactly as it did
     * before — one position, at the same Y, having drawn the same amount from the random source.
     * A column holding none still draws nothing at all, or every later placement of that feature
     * in the chunk would shift.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void columnRemapSingleBodyIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int soloX = origin.getX() + 6;
        int voidX = origin.getX() + 8;
        int z = origin.getZ() + 10;   // its own Z lane; see columnRemapReachesEveryBody

        buildStack(level, soloX, z, new int[][]{{LOW_BODY_BOTTOM, LOW_BODY_TOP}});
        buildStack(level, voidX, z, new int[][]{});
        if (!surfaceIs(helper, level, soloX, z, LOW_BODY_TOP + 1)) return;

        PlacementContext ctx = placementContext(level);
        ColumnBand band = RemapStrategy.ColumnLocal.DEFAULT.remapToColumn(COSMOS_COAL, CTX).orElseThrow();
        ColumnRelativeModifier modifier = new ColumnRelativeModifier(band);

        RandomSource actual = RandomSource.create(SEED);
        RandomSource control = RandomSource.create(SEED);
        for (int i = 0; i < 24; i++) {
            List<BlockPos> placed = modifier.getPositions(ctx, actual, new BlockPos(soloX, 0, z)).toList();
            if (placed.size() != 1) {
                helper.fail("single-body column emitted " + placed.size() + " positions, want 1");
                return;
            }
            // Free space above the body is TOP+1 and below it BOTTOM-1: the same two anchors the
            // modifier resolves, computed here without it.
            int want = band.resolveY(LOW_BODY_TOP + 1, LOW_BODY_BOTTOM - 1, band.sampleDepth(control));
            if (placed.get(0).getY() != want) {
                helper.fail("single-body Y moved: got " + placed.get(0).getY() + ", want " + want);
                return;
            }
        }
        // Identical draw counts, so the rest of the chunk is untouched.
        if (actual.nextLong() != control.nextLong()) {
            helper.fail("the single-body path consumed a different amount of randomness");
            return;
        }

        RandomSource untouched = RandomSource.create(SEED);
        RandomSource overVoid = RandomSource.create(SEED);
        List<BlockPos> none = modifier.getPositions(ctx, overVoid, new BlockPos(voidX, 0, z)).toList();
        if (!none.isEmpty()) {
            helper.fail("void column produced " + none);
            return;
        }
        if (untouched.nextLong() != overVoid.nextLong()) {
            helper.fail("void column consumed randomness");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Clear a tall slice of the column, then fill {@code [bottom, top]} with stone. */
    private static void buildBody(ServerLevel level, int x, int z, int bottom, int top) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = CLEAR_FROM; y <= CLEAR_TO; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
        }
        for (int y = bottom; y <= top; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, Blocks.STONE.defaultBlockState(), 2);
        }
        reprimeWorldSurface(level, x, z);
    }

    /**
     * Re-derive {@code WORLD_SURFACE_WG} for the chunk we just edited.
     *
     * <p>That heightmap is a worldgen artefact: a loaded chunk primes it once, on the first read,
     * and {@code setBlock} never touches it afterwards. Two tests whose columns share a chunk
     * would otherwise be ordered against each other — whichever reads first freezes the surface
     * the other one built. Re-priming after every edit makes each test independent of the order
     * the batch happens to run in.
     */
    private static void reprimeWorldSurface(ServerLevel level, int x, int z) {
        Heightmap.primeHeightmaps(level.getChunk(new BlockPos(x, 0, z)),
                EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
    }

    /**
     * Clear the whole column down to the build floor, then fill each {@code {bottom, top}} pair
     * with stone. Unlike {@link #buildBody} this empties everything below {@code CLEAR_FROM} too,
     * so the bodies listed here are the only ones in the column and a walk down it has an exact
     * expected length.
     */
    private static void buildStack(ServerLevel level, int x, int z, int[][] bodies) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y <= CLEAR_TO; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
        }
        for (int[] body : bodies) {
            for (int y = body[0]; y <= body[1]; y++) {
                cursor.set(x, y, z);
                level.setBlock(cursor, Blocks.STONE.defaultBlockState(), 2);
            }
        }
        reprimeWorldSurface(level, x, z);
    }

    /** Fail the test (returning false) when the WG heightmap doesn't see the body we built. */
    private static boolean surfaceIs(GameTestHelper helper, ServerLevel level, int x, int z, int expected) {
        int actual = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (actual != expected) {
            helper.fail("WORLD_SURFACE_WG at (" + x + "," + z + ") is " + actual + ", expected " + expected);
            return false;
        }
        return true;
    }

    private static PlacementContext placementContext(ServerLevel level) {
        return new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
    }

    private static List<Integer> depthsBelowSurface(PlacementContext ctx, ColumnBand band,
                                                     int x, int z, int surfaceY) {
        return sampleOffsets(ctx, band, x, z, surfaceY, true);
    }

    private static List<Integer> heightsAboveFloor(PlacementContext ctx, ColumnBand band,
                                                    int x, int z, int floorY) {
        return sampleOffsets(ctx, band, x, z, floorY, false);
    }

    /**
     * Draw a fixed number of placements from an identically seeded random source and report each
     * one's block distance from the given reference. Same seed + same band = same list, so two
     * columns can be compared element by element.
     */
    private static List<Integer> sampleOffsets(PlacementContext ctx, ColumnBand band,
                                                int x, int z, int reference, boolean below) {
        ColumnRelativeModifier modifier = new ColumnRelativeModifier(band);
        RandomSource random = RandomSource.create(SEED);
        BlockPos pos = new BlockPos(x, 0, z);
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            List<BlockPos> placed = modifier.getPositions(ctx, random, pos).toList();
            if (placed.isEmpty()) {
                out.add(Integer.MIN_VALUE);   // records a skip; comparison still meaningful
                continue;
            }
            int y = placed.get(0).getY();
            out.add(below ? reference - y : y - reference);
        }
        return out;
    }
}
