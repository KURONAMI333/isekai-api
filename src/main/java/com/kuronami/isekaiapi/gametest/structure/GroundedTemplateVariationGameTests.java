package com.kuronami.isekaiapi.gametest.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.structure.GroundedTemplatePiece;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.MirrorMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.RotationMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.Variant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The three ways {@code isekai_api:grounded_template} can vary the block result: a quarter turn, a
 * mirror, and a weighted choice between templates.
 *
 * <p>The properties checked are the ones a datapack author relies on and the ones easy to get wrong:
 * <ul>
 *   <li>a turned template still sits <b>centred on the chunk middle</b> — the extents swap, so the
 *       half-width used for the anchor has to come from the rotated size and the corner vanilla
 *       anchors at the piece position has to be compensated for;</li>
 *   <li>a mirror really flips the content, not just the bookkeeping;</li>
 *   <li>weights decide which template is placed, and {@code random} draws a different pick on a
 *       different seed.</li>
 * </ul>
 *
 * <p>Every template is built in the test world and captured with
 * {@link StructureTemplate#fillFromWorld}, so the shapes are visible here and no NBT fixture is
 * needed. The real path runs: {@code structure.generate(...)} then
 * {@code StructureStart#placeInChunk(...)}, the same two calls chunk generation makes.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class GroundedTemplateVariationGameTests {

    /** Where scenes are assembled before being captured. */
    private static final int SCENE_Y = 100;

    /** Above the test world's surface (y=4), so the placed templates never touch the ground. */
    private static final int VERTICAL_OFFSET = 8;

    /** A 3x1x7 template: seven gold blocks in a line along Z, with air on either side in X. */
    private static final Vec3i LINE_SIZE = new Vec3i(3, 1, 7);

    /** A 3x1x3 template: gold in one corner, diamond in the opposite corner, so both axes show. */
    private static final Vec3i CORNER_SIZE = new Vec3i(3, 1, 3);

    /** A 1x1x1 template, used twice with different blocks for the weight test. */
    private static final Vec3i DOT_SIZE = new Vec3i(1, 1, 1);

    private GroundedTemplateVariationGameTests() {}

    /**
     * A quarter turn swaps the extents; the line must end up along X, still centred on the chunk
     * middle, rather than shifted by the rotation.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void aQuarterTurnIsCentredOnTheChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation line = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_line");
        Scene scene = new Scene(level, 8000, 4400, LINE_SIZE);
        scene.build(pos -> {
            for (int z = 0; z < LINE_SIZE.getZ(); z++) {
                scene.set(pos.offset(1, 0, z), Blocks.GOLD_BLOCK);
            }
        });
        scene.capture(line);

        StructureStart start = place(helper, level, 8000, 4400, 0x5157L, List.of(new Variant(line)),
                RotationMode.CLOCKWISE_90, MirrorMode.NONE);
        if (start == null) {
            return;
        }
        BoundingBox box = start.getBoundingBox();
        List<BlockPos> gold = find(level, box, Blocks.GOLD_BLOCK);
        if (gold.size() != LINE_SIZE.getZ()) {
            helper.fail("expected " + LINE_SIZE.getZ() + " gold blocks, found " + gold.size()
                    + " in " + box);
            return;
        }
        // The line along Z became a line along X, one block deep in Z.
        Set<Integer> xs = new HashSet<>();
        Set<Integer> zs = new HashSet<>();
        for (BlockPos pos : gold) {
            xs.add(pos.getX());
            zs.add(pos.getZ());
        }
        if (xs.size() != LINE_SIZE.getZ() || zs.size() != 1) {
            helper.fail("the turned line is not a row along X: x=" + xs + " z=" + zs);
            return;
        }
        // Centred on the chunk middle: the 7 blocks span middle-3 .. middle+3.
        int middleX = new ChunkPos(8000 >> 4, 4400 >> 4).getMiddleBlockX();
        for (int offset = -3; offset <= 3; offset++) {
            if (!xs.contains(middleX + offset)) {
                helper.fail("the turned line is not centred on x=" + middleX + "; found x=" + xs);
                return;
            }
        }
        // The piece's own box must agree: nothing placed outside the box the gates judged.
        for (BlockPos pos : gold) {
            if (!box.isInside(pos)) {
                helper.fail("a placed block at " + pos + " is outside the start's box " + box);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Each mirror flips the content along its own axis, and the flipped template stays inside a
     * footprint the same size as the original (mirroring must not shift the placement).
     *
     * <p>Vanilla's names are the other way round from what "left/right" suggests in isolation:
     * {@code left_right} negates Z and {@code front_back} negates X (see
     * {@code StructureTemplate#transform}). The template is asymmetric in both axes so one test can
     * tell the three cases apart: gold at the (low X, low Z) corner and diamond at (high X, high Z).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void eachMirrorFlipsTheTemplateContentOnItsOwnAxis(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation corner = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_corner");
        Scene scene = new Scene(level, 8064, 4464, CORNER_SIZE);
        scene.build(pos -> {
            scene.set(pos, Blocks.GOLD_BLOCK);
            scene.set(pos.offset(CORNER_SIZE.getX() - 1, 0, CORNER_SIZE.getZ() - 1),
                    Blocks.DIAMOND_BLOCK);
        });
        scene.capture(corner);

        if (!expectEnds(helper, level, "unmirrored", 8064, 4464, MirrorMode.NONE, false, false)
                || !expectEnds(helper, level, "left_right", 8128, 4528, MirrorMode.LEFT_RIGHT, false, true)
                || !expectEnds(helper, level, "front_back", 8192, 4592, MirrorMode.FRONT_BACK, true, false)) {
            return;
        }
        helper.succeed();
    }

    /**
     * Places {@code corner} once and checks which corner of the box the gold occupies:
     * {@code flippedX}/{@code flippedZ} say which axis the mirror is expected to have reversed.
     */
    private static boolean expectEnds(GameTestHelper helper, ServerLevel level, String label,
                                     int x, int z, MirrorMode mirror, boolean flippedX,
                                     boolean flippedZ) {
        StructureStart start = place(helper, level, x, z, 0x5158L,
                List.of(new Variant(ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_corner"))),
                RotationMode.NONE, mirror);
        if (start == null) {
            return false;
        }
        BlockPos gold = only(helper, level, start.getBoundingBox(), Blocks.GOLD_BLOCK);
        BlockPos diamond = only(helper, level, start.getBoundingBox(), Blocks.DIAMOND_BLOCK);
        if (gold == null || diamond == null) {
            return false;
        }
        boolean goldLowX = gold.getX() < diamond.getX();
        boolean goldLowZ = gold.getZ() < diamond.getZ();
        if (goldLowX == flippedX) {
            helper.fail(label + " put the gold at " + gold + " and the diamond at " + diamond
                    + "; expected the X order " + (flippedX ? "reversed" : "kept"));
            return false;
        }
        if (goldLowZ == flippedZ) {
            helper.fail(label + " put the gold at " + gold + " and the diamond at " + diamond
                    + "; expected the Z order " + (flippedZ ? "reversed" : "kept"));
            return false;
        }
        return true;
    }

    /** A weighted list places both templates, and the heavier one more often. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void weightsDecideWhichTemplateIsPlaced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation stone = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_stone");
        ResourceLocation dirt = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_dirt");
        new Scene(level, 8200, 4600, DOT_SIZE).capture(stone, Blocks.STONE);
        new Scene(level, 8232, 4600, DOT_SIZE).capture(dirt, Blocks.DIRT);

        List<Variant> variants = List.of(new Variant(stone, 1), new Variant(dirt, 3));
        int stoneCount = 0;
        int dirtCount = 0;
        int attempts = 32;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = 8256 + attempt * 64;
            int z = 4600;
            StructureStart start = place(helper, level, x, z, attempt, variants,
                    RotationMode.NONE, MirrorMode.NONE);
            if (start == null) {
                return;
            }
            BlockPos at = start.getBoundingBox().getCenter();
            Block block = level.getBlockState(at).getBlock();
            if (block == Blocks.STONE) {
                stoneCount++;
            } else if (block == Blocks.DIRT) {
                dirtCount++;
            } else {
                helper.fail("attempt " + attempt + " placed " + block + " at " + at
                        + "; expected the stone or dirt template");
                return;
            }
        }
        if (stoneCount == 0 || dirtCount == 0) {
            helper.fail("weights 1:3 placed only one variant over " + attempts + " attempts (stone="
                    + stoneCount + ", dirt=" + dirtCount + ")");
            return;
        }
        if (dirtCount <= stoneCount) {
            helper.fail("the 3-weight template was not preferred over " + attempts
                    + " attempts (stone=" + stoneCount + ", dirt=" + dirtCount + ")");
            return;
        }
        helper.succeed();
    }

    /** {@code rotation: random} turns some attempts and not others. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void randomRotationVariesBetweenAttempts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Its own template id: tests run concurrently, and a shared id would let two
        // fillFromWorld calls write the same StructureTemplate at once.
        ResourceLocation line = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "variation_line_random");
        Scene scene = new Scene(level, 8400, 4800, LINE_SIZE);
        scene.build(pos -> {
            for (int z = 0; z < LINE_SIZE.getZ(); z++) {
                scene.set(pos.offset(1, 0, z), Blocks.GOLD_BLOCK);
            }
        });
        scene.capture(line);

        Set<Integer> shapes = new HashSet<>();
        int attempts = 16;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = 8400 + attempt * 64;
            int z = 4800;
            StructureStart start = place(helper, level, x, z, attempt, List.of(new Variant(line)),
                    RotationMode.RANDOM, MirrorMode.NONE);
            if (start == null) {
                return;
            }
            List<BlockPos> gold = find(level, start.getBoundingBox(), Blocks.GOLD_BLOCK);
            if (gold.size() != LINE_SIZE.getZ()) {
                helper.fail("attempt " + attempt + " placed " + gold.size() + " gold blocks, expected "
                        + LINE_SIZE.getZ());
                return;
            }
            int dx = gold.get(0).getX();
            boolean sameX = true;
            boolean sameZ = true;
            for (BlockPos pos : gold) {
                sameX &= pos.getX() == dx;
                sameZ &= pos.getZ() == gold.get(0).getZ();
            }
            if (sameX == sameZ) {
                helper.fail("the placed line is neither along X nor along Z: " + gold);
                return;
            }
            shapes.add(sameX ? 0 : 1);
        }
        if (shapes.size() < 2) {
            helper.fail("rotation random produced a single orientation over " + attempts
                    + " attempts; the random source is not reaching the rotation");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * Builds a small scene above the ground, captures it as a template, and wipes the scene again so
     * the later heightmap samples see flat air.
     */
    private static final class Scene {

        private final ServerLevel level;
        private final BlockPos origin;
        private final Vec3i size;

        Scene(ServerLevel level, int x, int z, Vec3i size) {
            this.level = level;
            this.origin = new BlockPos(x, SCENE_Y, z);
            this.size = size;
            level.getChunk(x >> 4, z >> 4);
            clear(level, origin.offset(-1, -1, -1),
                    origin.offset(size.getX(), size.getY(), size.getZ()));
        }

        void build(java.util.function.Consumer<BlockPos> filler) {
            filler.accept(origin);
        }

        void set(BlockPos pos, Block block) {
            level.setBlock(pos, block.defaultBlockState(), 2);
        }

        /** Captures the scene into {@code id}; {@code only} replaces the whole scene with one block. */
        void capture(ResourceLocation id, Block only) {
            StructureTemplateManager templates = level.getServer().getStructureManager();
            StructureTemplate template = templates.getOrCreate(id);
            if (only != null) {
                for (BlockPos pos : BlockPos.betweenClosed(origin,
                        origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                    level.setBlock(pos, only.defaultBlockState(), 2);
                }
            }
            template.fillFromWorld(level, origin, size, false, null);
            clear(level, origin.offset(-1, -1, -1),
                    origin.offset(size.getX(), size.getY(), size.getZ()));
        }

        void capture(ResourceLocation id) {
            capture(id, null);
        }
    }

    /**
     * Runs the real generation path once and returns the start, or null after reporting why it
     * could not be placed. The area the piece will write is cleared first so a block from an
     * earlier run cannot be mistaken for this one's.
     */
    private static StructureStart place(GameTestHelper helper, ServerLevel level, int x, int z,
                                        long seed, List<Variant> variants, RotationMode rotation,
                                        MirrorMode mirror) {
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        level.getChunk(chunkPos.x, chunkPos.z);

        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomeLookup.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        Structure.StructureSettings settings = new Structure.StructureSettings(
                HolderSet.direct(plains), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE);
        GroundedTemplateStructure structure = new GroundedTemplateStructure(settings, variants,
                GroundedTemplateStructure.NO_PROCESSORS, rotation, mirror, 2, 4, VERTICAL_OFFSET);

        StructureStart start = structure.generate(
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getServer().getStructureManager(),
                seed,
                chunkPos,
                0,
                level,
                settings.biomes()::contains);
        if (!start.isValid()) {
            helper.fail("no structure start for chunk " + chunkPos + " (gates rejected the flat "
                    + "test world?)");
            return null;
        }
        if (!(start.getPieces().get(0) instanceof GroundedTemplatePiece)) {
            helper.fail("the start's piece is " + start.getPieces().get(0).getClass().getName()
                    + ", not a grounded_template piece");
            return null;
        }
        BoundingBox box = start.getBoundingBox();
        clear(level, box.minX() - 1, box.minY() - 1, box.minZ() - 1,
                box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(seed), box, chunkPos);
        return start;
    }

    private static List<BlockPos> find(ServerLevel level, BoundingBox box, Block block) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).is(block)) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static BlockPos only(GameTestHelper helper, ServerLevel level, BoundingBox box, Block block) {
        List<BlockPos> found = find(level, box, block);
        if (found.size() != 1) {
            helper.fail("expected exactly one " + block + " in " + box + ", found " + found);
            return null;
        }
        return found.get(0);
    }

    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        clear(level, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private static void clear(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
