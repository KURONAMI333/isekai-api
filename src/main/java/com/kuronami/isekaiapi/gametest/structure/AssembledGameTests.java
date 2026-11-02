package com.kuronami.isekaiapi.gametest.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.structure.AssembledPiece;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code isekai_api:assembled} places its feature list from the origin's chunk only.
 *
 * <p>{@code StructureStart#placeInChunk} calls a piece's {@code postProcess} once for every chunk
 * its bounding box overlaps, each call with a random seeded per chunk. The assembled piece's box
 * is 33x97x33, so that was up to nine calls with nine random streams — nine pools dug at nine
 * shapes, nine tree clusters scattered. The fix is not a "first call wins" flag (that would make
 * the result depend on chunk visit order) but a position test: only the chunk that contains the
 * origin runs the list.
 *
 * <p>The marker is a real datapack placed feature ({@code isekai_api_test:assembled_marker},
 * excluded from the published jar): one gold block at a random offset inside a 9x9 square around
 * the input position. One invocation therefore leaves exactly one gold block; N invocations leave
 * between 1 and N. Counting blocks distinguishes the two without depending on feature internals.
 *
 * <p>Each test owns its own patch of the flat test world (GameTests in one batch share a world and
 * run concurrently, so a shared origin would let one test count the other's blocks).
 */
@GameTestHolder(IsekaiApi.MODID)
public final class AssembledGameTests {

    /** Patch for the "all overlapping chunks" test. */
    private static final int SLOT_A_X = 4000;
    private static final int SLOT_A_Z = 4000;

    /** Patch for the "neighbour chunk first" test. */
    private static final int SLOT_B_X = 4200;
    private static final int SLOT_B_Z = 4200;

    private static final ResourceLocation MARKER =
            ResourceLocation.fromNamespaceAndPath("isekai_api_test", "assembled_marker");

    private AssembledGameTests() {}

    /** Every overlapping chunk calls postProcess; the feature list must still run once. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void featuresRunOnceAcrossAllOverlappingChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = origin(level, SLOT_A_X, SLOT_A_Z);
        clearMarkers(level, origin);
        AssembledPiece piece = piece(helper, level, origin);
        if (piece == null) {
            return;
        }
        BoundingBox box = piece.getBoundingBox();
        if (chunkOf(box.minX()) == chunkOf(box.maxX()) && chunkOf(box.minZ()) == chunkOf(box.maxZ())) {
            helper.fail("the piece's box covers one chunk; this test would not exercise the repeat");
            return;
        }

        // Exactly the calls StructureStart#placeInChunk makes: every chunk the box touches.
        int calls = 0;
        int firstCallPlaced = 0;
        long seed = 0x5EEDL;
        for (int cx = chunkOf(box.minX()); cx <= chunkOf(box.maxX()); cx++) {
            for (int cz = chunkOf(box.minZ()); cz <= chunkOf(box.maxZ()); cz++) {
                level.getChunk(cx, cz);
                piece.postProcess(level, level.structureManager(),
                        level.getChunkSource().getGenerator(), RandomSource.create(seed++),
                        box, new ChunkPos(cx, cz), origin);
                calls++;
                if (calls == 1) {
                    firstCallPlaced = countMarker(level, origin);
                }
            }
        }
        if (calls < 4) {
            helper.fail("only " + calls + " chunk(s) fired postProcess; expected several");
            return;
        }

        int placed = countMarker(level, origin);
        if (placed != 1) {
            helper.fail("marker feature ran " + placed + " time(s) across " + calls
                    + " overlapping chunks; expected exactly 1 (after the first chunk: "
                    + firstCallPlaced + ")");
            return;
        }
        helper.succeed();
    }

    /** The guard is a position test, not "whichever chunk happens to be first". */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void aNeighbourChunkFirstDoesNotConsumeTheRun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = origin(level, SLOT_B_X, SLOT_B_Z);
        clearMarkers(level, origin);
        AssembledPiece piece = piece(helper, level, origin);
        if (piece == null) {
            return;
        }
        BoundingBox box = piece.getBoundingBox();
        int ox = chunkOf(SLOT_B_X);
        int oz = chunkOf(SLOT_B_Z);

        level.getChunk(ox - 1, oz);
        level.getChunk(ox, oz);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(1L), box, new ChunkPos(ox - 1, oz), origin);
        if (countMarker(level, origin) != 0) {
            helper.fail("a neighbour chunk placed the marker features");
            return;
        }

        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(2L), box, new ChunkPos(ox, oz), origin);
        int placed = countMarker(level, origin);
        if (placed != 1) {
            helper.fail("the origin chunk placed " + placed + " marker block(s); expected 1");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    private static AssembledPiece piece(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        Holder.Reference<PlacedFeature> holder = level.registryAccess()
                .lookupOrThrow(Registries.PLACED_FEATURE)
                .get(ResourceKey.create(Registries.PLACED_FEATURE, MARKER))
                .orElse(null);
        if (holder == null) {
            helper.fail(MARKER + " is not in the placed_feature registry — the GameTest fixture "
                    + "data/isekai_api_test/worldgen/placed_feature/assembled_marker.json did not load");
            return null;
        }
        return new AssembledPiece(origin, HolderSet.direct(List.of(holder)));
    }

    /**
     * The piece's anchor. The chunk is loaded first so {@code WORLD_SURFACE_WG} is primed: an
     * unprimed heightmap answers {@code Integer.MIN_VALUE} and the test would look for blocks
     * nowhere near where the feature wrote them.
     */
    private static BlockPos origin(ServerLevel level, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        return new BlockPos(x, y, z);
    }

    private static int chunkOf(int blockCoord) {
        return SectionPos.blockToSectionCoord(blockCoord);
    }

    /** The marker lands at the origin's Y, +0..+8 in x and z; the Y span tolerates a re-snap. */
    private static int countMarker(ServerLevel level, BlockPos origin) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-1, -16, -1), origin.offset(9, 16, 9))) {
            if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Drops gold left by an earlier run of this test. The GameTest server's level is a saved world
     * at the cell root, so a fixed patch is not clean between runs; clearing only gold keeps the
     * flat terrain (and every other test's scene) untouched.
     */
    private static void clearMarkers(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-1, -16, -1), origin.offset(9, 16, 9))) {
            if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
