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

    private static final int CLEAR_FROM = 30;
    private static final int CLEAR_TO = 300;

    private static final long SEED = 20260804L;

    private static final VerticalRange COSMOS_COAL = new VerticalRange(51, 62, HeightDistribution.UNIFORM);
    private static final VerticalRange COSMOS_DIAMOND = new VerticalRange(-63, -48, HeightDistribution.UNIFORM);

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
