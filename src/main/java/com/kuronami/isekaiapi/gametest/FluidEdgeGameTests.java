package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.placementmodifier.FluidEdgeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Machine checks for {@code isekai_api:fluid_edge}'s {@code vertical_range}.
 *
 * <p>Each test builds its own 3x3 pond high above the arena, in its own Y band, and reads it
 * back through the modifier. The property that matters: a pond several levels below the input
 * position is found by one modifier, so a pack no longer needs one Y anchor plus one fluid probe
 * per level — and a pack that omits the field sees exactly the old single-level answer.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class FluidEdgeGameTests {

    private FluidEdgeGameTests() {}

    /** Ponds sit this far above the arena floor, clear of every other test's blocks. */
    private static final int BAND_OFFSET = 100;
    /** Distance between the bands, larger than any span a test uses (9). */
    private static final int BAND_SPACING = 20;
    /** How far above the water the input position sits. */
    private static final int ABOVE_WATER = 6;
    /** Horizontal distance from the pond centre to the input position. */
    private static final int AWAY = 6;

    // =====================================================================
    // The three acceptance checks
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void verticalRangeFindsWaterFarBelowTheInputY(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int waterY = origin.getY() + BAND_OFFSET;
        buildPond(level, origin, waterY, false);
        if (!hasWater(level, origin, waterY)) return;
        PlacementContext ctx = placementContext(level);
        HolderSet<Fluid> water = waterFluids(level);
        BlockPos above = new BlockPos(origin.getX() + AWAY, waterY + ABOVE_WATER, origin.getZ());

        FluidEdgeModifier widened = new FluidEdgeModifier(water, 12, FluidEdgeModifier.Mode.NEAR,
                new FluidEdgeModifier.VerticalSpan(9, 0));
        if (!accepts(widened, ctx, above)) {
            helper.fail("vertical_range below 9 did not find the pond "
                    + ABOVE_WATER + " levels down and " + AWAY + " blocks away");
            return;
        }

        // The same search without the field looks at one level only: nothing there.
        FluidEdgeModifier singleLevel = new FluidEdgeModifier(water, 12, FluidEdgeModifier.Mode.NEAR);
        if (accepts(singleLevel, ctx, above)) {
            helper.fail("the omitted-field search found water at a level it never looks at — "
                    + "the single-level path changed");
            return;
        }

        // far flips with the same range: the pond is inside it, so the position is rejected.
        FluidEdgeModifier far = new FluidEdgeModifier(water, 12, FluidEdgeModifier.Mode.FAR,
                new FluidEdgeModifier.VerticalSpan(9, 0));
        if (accepts(far, ctx, above)) {
            helper.fail("far mode accepted a position 6 levels above a pond inside its own range");
            return;
        }

        // A band that stops short of the water must still report nothing: the range is a bound,
        // not a licence to scan the whole column.
        FluidEdgeModifier shortBand = new FluidEdgeModifier(water, 12, FluidEdgeModifier.Mode.NEAR,
                new FluidEdgeModifier.VerticalSpan(3, 0));
        if (accepts(shortBand, ctx, above)) {
            helper.fail("vertical_range below 3 found water 6 levels down");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void omittedVerticalRangeKeepsSingleLevelBehaviour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int waterY = origin.getY() + BAND_OFFSET + BAND_SPACING;
        buildPond(level, origin, waterY, false);
        if (!hasWater(level, origin, waterY)) return;
        PlacementContext ctx = placementContext(level);
        HolderSet<Fluid> water = waterFluids(level);
        BlockPos atWater = new BlockPos(origin.getX() + AWAY, waterY, origin.getZ());
        BlockPos above = new BlockPos(origin.getX() + AWAY, waterY + ABOVE_WATER, origin.getZ());

        // Old JSON, decoded: no vertical_range means one level, exactly as before.
        FluidEdgeModifier decoded = decode(helper, level,
                "{\"type\":\"isekai_api:fluid_edge\",\"fluid\":\"#minecraft:water\","
                        + "\"max_distance\":12,\"mode\":\"near\"}");
        if (decoded == null) return;
        if (!decoded.verticalRange().isSingleLevel()) {
            helper.fail("a fluid_edge without vertical_range decoded to " + decoded.verticalRange()
                    + " instead of a single level");
            return;
        }
        if (!accepts(decoded, ctx, atWater)) {
            helper.fail("a fluid_edge without vertical_range no longer accepts a position at the "
                    + "water level");
            return;
        }
        if (accepts(decoded, ctx, above)) {
            helper.fail("a fluid_edge without vertical_range now reaches "
                    + ABOVE_WATER + " levels down — packs that omit the field changed");
            return;
        }

        // far, also as written before the field existed.
        FluidEdgeModifier far = decode(helper, level,
                "{\"type\":\"isekai_api:fluid_edge\",\"fluid\":\"#minecraft:water\","
                        + "\"max_distance\":12,\"mode\":\"far\"}");
        if (far == null) return;
        if (!accepts(far, ctx, above)) {
            helper.fail("far without vertical_range rejected a position with no water at its own "
                    + "level");
            return;
        }

        // And the field itself round-trips.
        FluidEdgeModifier widened = decode(helper, level,
                "{\"type\":\"isekai_api:fluid_edge\",\"fluid\":\"#minecraft:water\","
                        + "\"max_distance\":12,\"mode\":\"near\","
                        + "\"vertical_range\":{\"below\":4,\"above\":2}}");
        if (widened == null) return;
        if (widened.verticalRange().below() != 4 || widened.verticalRange().above() != 2) {
            helper.fail("vertical_range decoded to " + widened.verticalRange()
                    + " instead of below 4 / above 2");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void verticalRangeSeesWaterUnderACanopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int waterY = origin.getY() + BAND_OFFSET + BAND_SPACING * 2;
        buildPond(level, origin, waterY, true);
        if (!hasWater(level, origin, waterY)) return;

        // Precondition: the pond's own column is topped by the canopy, not by the water, so a
        // search that only looks at each column's top block would miss it.
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                origin.getX() + 1, origin.getZ() + 1);
        if (surface <= waterY) {
            helper.fail("precondition: the canopy did not reach above the pond (surface " + surface
                    + ", water " + waterY + ")");
            return;
        }

        PlacementContext ctx = placementContext(level);
        FluidEdgeModifier widened = new FluidEdgeModifier(waterFluids(level), 12,
                FluidEdgeModifier.Mode.NEAR, new FluidEdgeModifier.VerticalSpan(9, 0));
        BlockPos above = new BlockPos(origin.getX() + AWAY, waterY + ABOVE_WATER, origin.getZ());
        if (!accepts(widened, ctx, above)) {
            helper.fail("the widened search missed a pond sitting under a canopy");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** A 3x3 pond with a stone floor, built inside the arena footprint but above it. */
    private static void buildPond(ServerLevel level, BlockPos origin, int waterY, boolean canopy) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = origin.getX() + 1 + dx;
                int z = origin.getZ() + 1 + dz;
                level.setBlock(cursor.set(x, waterY - 1, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, waterY, z), Blocks.WATER.defaultBlockState(), 2);
                if (canopy) {
                    level.setBlock(cursor.set(x, waterY + 3, z),
                            Blocks.OAK_LEAVES.defaultBlockState(), 2);
                }
            }
        }
    }

    private static boolean hasWater(ServerLevel level, BlockPos origin, int waterY) {
        BlockPos centre = new BlockPos(origin.getX() + 1, waterY, origin.getZ() + 1);
        return !level.getFluidState(centre).isEmpty();
    }

    private static HolderSet<Fluid> waterFluids(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.FLUID).getOrThrow(FluidTags.WATER);
    }

    private static PlacementContext placementContext(ServerLevel level) {
        return new PlacementContext(level, level.getChunkSource().getGenerator(),
                java.util.Optional.empty());
    }

    private static boolean accepts(FluidEdgeModifier modifier, PlacementContext ctx, BlockPos pos) {
        return modifier.getPositions(ctx, RandomSource.create(1L), pos).findAny().isPresent();
    }

    private static FluidEdgeModifier decode(GameTestHelper helper, ServerLevel level, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                level.registryAccess());
        var parsed = FluidEdgeModifier.CODEC.codec().parse(ops, JsonParser.parseString(json));
        if (parsed.error().isPresent()) {
            helper.fail("fluid_edge JSON did not decode: " + parsed.error().get().message());
            return null;
        }
        return parsed.result().orElseThrow();
    }
}
