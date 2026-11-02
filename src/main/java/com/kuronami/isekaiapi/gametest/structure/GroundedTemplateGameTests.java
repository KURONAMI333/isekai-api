package com.kuronami.isekaiapi.gametest.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.structure.GroundedTemplatePiece;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure;
import com.kuronami.isekaiapi.structure.LeafDistanceProcessor;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code isekai_api:grounded_template} accepts a processor list and applies it to the template it
 * pastes, the way vanilla's {@code single_pool_element} does.
 *
 * <p>Before this, a tree baked into an NBT set-piece kept the leaf data the editor saved: leaves at
 * {@code distance: 7} shed on the first shape update, and the only workaround was
 * {@code persistent: true}, which turns the tree into decoration. The processor list is the fix,
 * and the two tests here are its two sides — with {@code leaf_distances} the crown gets real
 * distances and stops being persistent; with no {@code processors} field the template is placed
 * exactly as saved, which is what every existing datapack already gets.
 *
 * <p>The template is built in the test world and captured with
 * {@link StructureTemplate#fillFromWorld}, so the "saved by an editor" states are visible in this
 * source and no NBT fixture is needed. The real structure path runs: the structure resolves the
 * template and the processor list, generates its start, and the piece places it.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class GroundedTemplateGameTests {

    /** Patch for "the processor list runs". */
    private static final int SLOT_A_X = 7000;
    private static final int SLOT_A_Z = 4400;

    /** Patch for "no processors accepts the saved leaf data". */
    private static final int SLOT_B_X = 7064;
    private static final int SLOT_B_Z = 4464;

    /** Where the "editor-saved" tree is assembled before being captured. */
    private static final int SCENE_Y = 100;

    /** The tree as saved: x, y, z. Trunk in the middle, 5x5 crown, one extra tip leaf. */
    private static final Vec3i SIZE = new Vec3i(5, 7, 5);

    /**
     * Above the flat test world's surface (y=4) and below the scratch scenes, so the placed tree
     * never touches the ground or another test's patch.
     */
    private static final int VERTICAL_OFFSET = 8;

    private GroundedTemplateGameTests() {}

    /** A leaf_distances processor list makes the pasted crown behave like a grown tree's. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void processorsRunOnThePlacedTemplate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GroundedTemplatePiece piece = place(helper, level, SLOT_A_X, SLOT_A_Z,
                ResourceLocation.fromNamespaceAndPath("isekai_api_test", "grounded_tree_processed"),
                new StructureProcessorList(List.of(
                        new LeafDistanceProcessor(LeafDistanceProcessor.OrphanLeaves.DECAY, false))));
        if (piece == null) {
            return;
        }
        BlockPos at = piece.templatePosition();

        // Two steps from the trunk's top log, the 5x5 layer's middle, its corner, the 3x3 layer,
        // and the tip: the template wrote 7 for every leaf, so these numbers can only come from
        // the processor.
        if (!expectLeaf(helper, level, at, 2, 4, 2, 1, false)
                || !expectLeaf(helper, level, at, 1, 4, 1, 3, false)
                || !expectLeaf(helper, level, at, 0, 4, 0, 5, false)
                || !expectLeaf(helper, level, at, 1, 5, 1, 4, false)
                || !expectLeaf(helper, level, at, 2, 6, 2, 3, false)) {
            return;
        }
        // The trunk leaf reached a log within six steps, so it is not a "persistent" leaf any more.
        if (level.getBlockState(at.offset(2, 4, 2)).getValue(LeavesBlock.PERSISTENT)) {
            helper.fail("the processor left a leaf persistent although it has a real distance");
            return;
        }
        helper.succeed();
    }

    /** Omitting the field is the old behaviour: the template's own leaf data lands unchanged. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void noProcessorsKeepsTheSavedLeafData(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GroundedTemplatePiece piece = place(helper, level, SLOT_B_X, SLOT_B_Z,
                ResourceLocation.fromNamespaceAndPath("isekai_api_test", "grounded_tree_plain"),
                null);
        if (piece == null) {
            return;
        }
        BlockPos at = piece.templatePosition();
        // What the editor wrote: distance 7 everywhere, persistent on the crown centre.
        if (!expectLeaf(helper, level, at, 2, 4, 2, 7, true)
                || !expectLeaf(helper, level, at, 1, 4, 1, 7, false)
                || !expectLeaf(helper, level, at, 0, 4, 0, 7, false)) {
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * Builds the "editor-saved" tree at {@code x}/{@code z}, captures it under {@code templateId},
     * then runs the real structure path so its piece pastes the template. Returns the piece (its
     * {@code templatePosition()} is where the template landed), or null after reporting why.
     */
    private static GroundedTemplatePiece place(GameTestHelper helper, ServerLevel level, int x, int z,
                                               ResourceLocation templateId,
                                               StructureProcessorList processors) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos scene = new BlockPos(x, SCENE_Y, z);
        clear(level, scene.offset(-1, -1, -1), scene.offset(SIZE.getX(), SIZE.getY(), SIZE.getZ()));
        saveEditorTree(level, scene);

        StructureTemplateManager templates = level.getServer().getStructureManager();
        StructureTemplate template = templates.getOrCreate(templateId);
        template.fillFromWorld(level, scene, SIZE, false, null);
        clear(level, scene.offset(-1, -1, -1), scene.offset(SIZE.getX(), SIZE.getY(), SIZE.getZ()));
        if (!template.getSize().equals(SIZE)) {
            helper.fail("captured template is " + template.getSize() + "; expected " + SIZE);
            return null;
        }

        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomeLookup.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        Structure.StructureSettings settings = new Structure.StructureSettings(
                HolderSet.direct(plains), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE);
        GroundedTemplateStructure structure = new GroundedTemplateStructure(
                settings, templateId,
                processors == null ? GroundedTemplateStructure.NO_PROCESSORS : net.minecraft.core.Holder.direct(processors),
                2, 4, VERTICAL_OFFSET);

        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        StructureStart start = structure.generate(
                level.registryAccess(),
                level.getChunkSource().getGenerator(),
                level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState(),
                templates,
                level.getSeed(),
                chunkPos,
                0,
                level,
                settings.biomes()::contains);
        if (!start.isValid()) {
            helper.fail("the grounded_template produced no structure start (gates rejected the flat "
                    + "test world?)");
            return null;
        }
        if (!(start.getPieces().get(0) instanceof GroundedTemplatePiece piece)) {
            helper.fail("the start's piece is " + start.getPieces().get(0).getClass().getName()
                    + ", not a grounded_template piece");
            return null;
        }
        // Clear where the template will land before pasting it. The template writes its own air
        // too, so this only matters for the ring around it — but a leftover block from an earlier
        // run at the same spot would otherwise sit next to the crown. The box sits well above the
        // test world's surface, so the ground is untouched.
        BlockPos at = piece.templatePosition();
        clear(level, at.offset(-1, -1, -1), at.offset(SIZE.getX(), SIZE.getY(), SIZE.getZ()));

        start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
                RandomSource.create(11L), start.getBoundingBox(), chunkPos);
        return piece;
    }

    /**
     * The exact states an editor leaves behind: a 4-log trunk, a 5x5 layer of leaves at distance 7,
     * a 3x3 layer above it and one tip leaf. The crown centre is persistent so the no-processor case
     * can be told apart from the processed one.
     */
    private static void saveEditorTree(ServerLevel level, BlockPos origin) {
        for (int y = 0; y < 4; y++) {
            level.setBlock(origin.offset(2, y, 2), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                level.setBlock(origin.offset(x, 4, z), editorLeaf(x == 2 && z == 2), 2);
            }
        }
        for (int x = 1; x < 4; x++) {
            for (int z = 1; z < 4; z++) {
                level.setBlock(origin.offset(x, 5, z), editorLeaf(false), 2);
            }
        }
        level.setBlock(origin.offset(2, 6, 2), editorLeaf(false), 2);
    }

    private static BlockState editorLeaf(boolean persistent) {
        return Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, persistent);
    }

    /** Wipes an inclusive box; the scenes and the placed trees are small. */
    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static boolean expectLeaf(GameTestHelper helper, ServerLevel level, BlockPos at,
                                      int dx, int dy, int dz, int distance, boolean persistent) {
        BlockPos pos = at.offset(dx, dy, dz);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeavesBlock)) {
            helper.fail("expected leaves at " + dx + "," + dy + "," + dz + " (world " + pos
                    + ") but found " + state);
            return false;
        }
        int actual = state.getValue(LeavesBlock.DISTANCE);
        boolean actualPersistent = state.getValue(LeavesBlock.PERSISTENT);
        if (actual != distance || actualPersistent != persistent) {
            helper.fail("leaf " + dx + "," + dy + "," + dz + " is distance " + actual
                    + " persistent " + actualPersistent + "; expected distance " + distance
                    + " persistent " + persistent);
            return false;
        }
        return true;
    }
}
