package com.kuronami.isekaiapi.gametest.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.tree.LeafPlacing;
import com.kuronami.isekaiapi.tree.decorators.AttachToLogsDecorator;
import com.kuronami.isekaiapi.tree.decorators.BranchStubsDecorator;
import com.kuronami.isekaiapi.tree.decorators.BrokenTopDecorator;
import com.kuronami.isekaiapi.tree.decorators.GroundPatchDecorator;
import com.kuronami.isekaiapi.tree.decorators.HangFromLeavesDecorator;
import com.kuronami.isekaiapi.tree.decorators.IsekaiTreeDecorators;
import com.kuronami.isekaiapi.tree.decorators.SnowCapDecorator;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance for the tree decorators and the bare-tree parts a datapack can put in a
 * {@code minecraft:tree} feature. Each one is decoded from the same JSON a datapack would contain,
 * grown as part of a real tree, and checked for the blocks it is supposed to add. Codec misuse
 * (missing field, out-of-range value) has to fail decoding rather than be silently clamped.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class TreeDecoratorGameTests {

    /** A block that only the decorator in question can place, and that survives anywhere. */
    private static final String STAIR_PROVIDER =
            "\"block_provider\": {\"type\": \"minecraft:simple_state_provider\", "
                    + "\"state\": {\"Name\": \"minecraft:oak_stairs\", "
                    + "\"Properties\": {\"facing\": \"south\", \"half\": \"bottom\", "
                    + "\"shape\": \"straight\", \"waterlogged\": \"false\"}}}";

    private static final String MOSS_PROVIDER =
            "\"block_provider\": {\"type\": \"minecraft:simple_state_provider\", "
                    + "\"state\": {\"Name\": \"minecraft:moss_block\"}}";

    private static final String JUNGLE_LEAVES_PROVIDER =
            "\"block_provider\": {\"type\": \"minecraft:simple_state_provider\", "
                    + "\"state\": {\"Name\": \"minecraft:jungle_leaves\", "
                    + "\"Properties\": {\"distance\": \"7\", \"persistent\": \"false\", "
                    + "\"waterlogged\": \"false\"}}}";

    private static final String ATTACH_TO_LOGS = "{\"type\": \"isekai_api:attach_to_logs\", "
            + STAIR_PROVIDER + ", \"chance\": 1.0, \"directions\": [\"north\"], \"max_per_log\": 1}";

    private static final String GROUND_PATCH = "{\"type\": \"isekai_api:ground_patch\", "
            + MOSS_PROVIDER + ", \"radius\": {\"type\": \"minecraft:constant\", \"value\": 2}}";

    private static final String HANG_FROM_LEAVES = "{\"type\": \"isekai_api:hang_from_leaves\", "
            + JUNGLE_LEAVES_PROVIDER + ", \"length\": {\"type\": \"minecraft:constant\", \"value\": 2}}";

    private static final String SNOW_CAP = "{\"type\": \"isekai_api:snow_cap\", "
            + "\"layers\": {\"type\": \"minecraft:constant\", \"value\": 2}}";

    /** No leaves at all; the tree codec still wants a foliage placer, so this is the one that isn't. */
    private static final String NO_FOLIAGE = "{\"type\": \"isekai_api:none\"}";

    private static final String FLAT_STUBS = "{\"type\": \"isekai_api:branch_stubs\", "
            + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 2}, "
            + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 2}, "
            + "\"start_fraction\": 0.0, \"upward_chance\": 0.0, \"directions\": [\"north\"]}";

    private static final String CLIMBING_STUB = "{\"type\": \"isekai_api:branch_stubs\", "
            + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 1}, "
            + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 4}, "
            + "\"start_fraction\": 0.0, \"upward_chance\": 1.0, \"directions\": [\"east\"]}";

    private static final String SNAPPED_TOP = "{\"type\": \"isekai_api:broken_top\", "
            + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 2}}";

    private static final String SNAPPED_TOP_FILLED = "{\"type\": \"isekai_api:broken_top\", "
            + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 1}, "
            + "\"fill_provider\": {\"type\": \"minecraft:simple_state_provider\", "
            + "\"state\": {\"Name\": \"minecraft:basalt\", \"Properties\": {\"axis\": \"y\"}}}, "
            + "\"fill_chance\": 1.0}";

    private TreeDecoratorGameTests() {}

    private static RegistryOps<JsonElement> ops(GameTestHelper helper) {
        return RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
    }

    /** Every id resolves in the registry a datapack's {@code type} string is looked up in. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void decoratorTypesAreRegisteredInIsekaiNamespace(GameTestHelper helper) {
        List<Supplier<? extends TreeDecoratorType<?>>> registered =
                List.<Supplier<? extends TreeDecoratorType<?>>>of(
                IsekaiTreeDecorators.ATTACH_TO_LOGS,
                IsekaiTreeDecorators.GROUND_PATCH,
                IsekaiTreeDecorators.HANG_FROM_LEAVES,
                IsekaiTreeDecorators.SNOW_CAP,
                IsekaiTreeDecorators.BRANCH_STUBS,
                IsekaiTreeDecorators.BROKEN_TOP);
        List<String> expected = List.of("attach_to_logs", "ground_patch", "hang_from_leaves", "snow_cap",
                "branch_stubs", "broken_top");
        for (int i = 0; i < registered.size(); i++) {
            ResourceLocation key = BuiltInRegistries.TREE_DECORATOR_TYPE.getKey(registered.get(i).get());
            ResourceLocation wanted = ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, expected.get(i));
            if (!wanted.equals(key)) {
                helper.fail("decorator " + expected.get(i) + " is registered as " + key + " instead of " + wanted);
                return;
            }
        }
        helper.succeed();
    }

    /** Every log gets a block on the listed side, and nothing appears on the sides left out. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void attachToLogsPlacesOnlyOnListedSides(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 0);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, ATTACH_TO_LOGS);
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the test tree did not place at all");
            return;
        }
        BlockState north = helper.getLevel().getBlockState(origin.north());
        if (!north.is(Blocks.OAK_STAIRS)) {
            helper.fail("no attachment north of the lowest log: found " + north);
            return;
        }
        // The provider says "south", which is the side the log is on, so the block has to be turned.
        if (north.getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH) {
            helper.fail("the attachment did not turn away from the log: " + north);
            return;
        }
        for (var direction : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            BlockState side = helper.getLevel().getBlockState(origin.relative(direction));
            if (!side.isAir()) {
                helper.fail("attached " + side + " to " + direction + ", which is not in directions");
                return;
            }
        }
        helper.succeed();
    }

    /** Cover mode lays the patch on top of the ground, keeps the ground, and skips the trunk. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void groundPatchCoversGroundAroundTheBase(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 1);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, GROUND_PATCH);
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the test tree did not place at all");
            return;
        }
        BlockPos from = TreeTestScenes.boxMin(origin, 3);
        BlockPos to = TreeTestScenes.boxMax(origin, 3, 5);
        int moss = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(Blocks.MOSS_BLOCK));
        // Radius 2 is a 13-column disc; the centre column holds the trunk, so cover mode skips it.
        if (moss != 12) {
            helper.fail("expected the 12 non-trunk columns of a radius-2 disc to be covered, got " + moss);
            return;
        }
        if (!helper.getLevel().getBlockState(origin.east().below()).is(Blocks.GRASS_BLOCK)) {
            helper.fail("cover mode replaced the ground instead of laying on top of it");
            return;
        }
        if (!helper.getLevel().getBlockState(origin).is(Blocks.OAK_LOG)) {
            helper.fail("the patch overwrote the trunk column");
            return;
        }
        if (!helper.getLevel().getBlockState(origin.east()).is(Blocks.MOSS_BLOCK)) {
            helper.fail("no patch east of the trunk, where the disc should reach");
            return;
        }
        helper.succeed();
    }

    /** Strands hang below the crown in the requested length and stay as leaves, not decayed husks. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void hangFromLeavesHangsDecaySafeStrands(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 2);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, HANG_FROM_LEAVES);
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the test tree did not place at all");
            return;
        }
        BlockPos from = TreeTestScenes.boxMin(origin, 4);
        BlockPos to = TreeTestScenes.boxMax(origin, 4, 8);
        int strands = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(Blocks.JUNGLE_LEAVES));
        if (strands == 0) {
            helper.fail("no strand block hangs from the crown");
            return;
        }
        int decayed = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(Blocks.JUNGLE_LEAVES)
                && state.getValue(LeavesBlock.DISTANCE) >= 7);
        if (decayed > 0) {
            helper.fail(decayed + " strand blocks were left at distance 7 and would decay");
            return;
        }
        BlockPos underCrown = null;
        BlockPos stacked = null;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!helper.getLevel().getBlockState(pos).is(Blocks.JUNGLE_LEAVES)) {
                continue;
            }
            if (helper.getLevel().getBlockState(pos.above()).is(Blocks.OAK_LEAVES)) {
                underCrown = pos.immutable();
            }
            if (helper.getLevel().getBlockState(pos.above()).is(Blocks.JUNGLE_LEAVES)) {
                stacked = pos.immutable();
            }
        }
        if (underCrown == null) {
            helper.fail("no strand starts directly below a crown leaf");
            return;
        }
        if (stacked == null) {
            helper.fail("no two-block strand: a length-2 strand should stack the body block");
            return;
        }
        helper.succeed();
    }

    /** Snow sits on the sky-facing leaves, at the sampled depth, without replacing the leaf. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snowCapSitsOnSkyFacingLeaves(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 3);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, SNOW_CAP);
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the test tree did not place at all");
            return;
        }
        BlockPos from = TreeTestScenes.boxMin(origin, 4);
        BlockPos to = TreeTestScenes.boxMax(origin, 4, 8);
        int caps = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(Blocks.SNOW)
                && state.getValue(SnowLayerBlock.LAYERS) == 2);
        if (caps == 0) {
            helper.fail("no leaf-facing-the-sky received a 2-layer snow cap");
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (helper.getLevel().getBlockState(pos).is(Blocks.SNOW)
                    && !helper.getLevel().getBlockState(pos.below()).is(Blocks.OAK_LEAVES)) {
                helper.fail("snow at " + pos + " sits on "
                        + helper.getLevel().getBlockState(pos.below()) + " instead of a leaf");
                return;
            }
        }
        helper.succeed();
    }

    /** The four leaf/surface decorators are accepted in a single vanilla {@code minecraft:tree} array. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void treeJsonWithAllFourDecoratorsParses(GameTestHelper helper) {
        String decorators = String.join(", ", ATTACH_TO_LOGS, GROUND_PATCH, HANG_FROM_LEAVES, SNOW_CAP);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, decorators);
        if (tree.feature() != Feature.TREE) {
            helper.fail("parsed as " + tree.feature() + " instead of minecraft:tree");
            return;
        }
        if (!(tree.config() instanceof TreeConfiguration config)) {
            helper.fail("tree config decoded as " + tree.config().getClass().getSimpleName());
            return;
        }
        List<TreeDecorator> decoded = config.decorators;
        if (decoded.size() != 4) {
            helper.fail("expected 4 decorators, decoded " + decoded.size());
            return;
        }
        List<Class<?>> expected = List.of(AttachToLogsDecorator.class, GroundPatchDecorator.class,
                HangFromLeavesDecorator.class, SnowCapDecorator.class);
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).isInstance(decoded.get(i))) {
                helper.fail("decorator " + i + " decoded as " + decoded.get(i).getClass().getSimpleName()
                        + " instead of " + expected.get(i).getSimpleName());
                return;
            }
        }
        helper.succeed();
    }

    /** The bare-tree decorators decode alongside each other in one tree. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void treeJsonWithBareTreeDecoratorsParses(GameTestHelper helper) {
        String decorators = String.join(", ", FLAT_STUBS, SNAPPED_TOP, GROUND_PATCH);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, decorators, NO_FOLIAGE, trunk(6));
        if (!(tree.config() instanceof TreeConfiguration config)) {
            helper.fail("tree config decoded as " + tree.config().getClass().getSimpleName());
            return;
        }
        if (config.decorators.size() != 3) {
            helper.fail("expected 3 decorators, decoded " + config.decorators.size());
            return;
        }
        if (!BranchStubsDecorator.class.isInstance(config.decorators.get(0))
                || !BrokenTopDecorator.class.isInstance(config.decorators.get(1))) {
            helper.fail("bare-tree decorators decoded as " + config.decorators);
            return;
        }
        helper.succeed();
    }

    /** {@code isekai_api:none} grows the trunk and not one leaf. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void foliagePlacerNonePlacesNoLeaves(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 6);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, "", NO_FOLIAGE, trunk(5));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the leafless tree did not place at all");
            return;
        }
        BlockPos from = TreeTestScenes.boxMin(origin, 4);
        BlockPos to = TreeTestScenes.boxMax(origin, 4, 10);
        int leaves = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(BlockTags.LEAVES));
        if (leaves != 0) {
            helper.fail("isekai_api:none placed " + leaves + " leaf blocks");
            return;
        }
        int logs = TreeTestScenes.count(helper.getLevel(), from, to, state -> state.is(Blocks.OAK_LOG));
        if (logs != 5) {
            helper.fail("expected a 5-log trunk, found " + logs + " logs");
            return;
        }
        helper.succeed();
    }

    /** A flat stub is a run of horizontal logs pointing away from the trunk. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void branchStubsPlacesHorizontalLogs(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 7);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, FLAT_STUBS, NO_FOLIAGE, trunk(5));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the stub tree did not place at all");
            return;
        }
        int stubs = 0;
        int far = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-3, -1, -3), origin.offset(3, 9, 3))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            if (pos.getX() == origin.getX() && pos.getZ() == origin.getZ()) {
                continue;
            }
            if (!state.hasProperty(BlockStateProperties.AXIS)
                    || state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Z) {
                helper.fail("stub at " + pos + " is " + state + ", which is not a north-south log");
                return;
            }
            stubs++;
            if (origin.getZ() - pos.getZ() >= 2) {
                far++;
            }
        }
        if (stubs == 0) {
            helper.fail("no branch stub grew off the trunk");
            return;
        }
        if (far == 0) {
            helper.fail("every stub was one block long; a length-2 stub should reach two blocks out");
            return;
        }
        helper.succeed();
    }

    /** With {@code upward_chance: 1}, the stub climbs: vertical logs appear away from the trunk. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void branchStubsClimbsWhenUpwardChanceIsOne(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 8);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, CLIMBING_STUB, NO_FOLIAGE, trunk(5));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the climbing-stub tree did not place at all");
            return;
        }
        boolean verticalOffTrunk = false;
        boolean horizontalOffTrunk = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-3, -1, -3), origin.offset(3, 9, 3))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!state.is(BlockTags.LOGS) || !state.hasProperty(BlockStateProperties.AXIS)) {
                continue;
            }
            if (pos.getX() == origin.getX() && pos.getZ() == origin.getZ()) {
                continue;
            }
            switch (state.getValue(BlockStateProperties.AXIS)) {
                case Y -> verticalOffTrunk = true;
                case X -> horizontalOffTrunk = true;
                default -> { }
            }
        }
        if (!verticalOffTrunk) {
            helper.fail("a climbing stub left no upright log away from the trunk");
            return;
        }
        if (!horizontalOffTrunk) {
            helper.fail("a climbing stub should still step outwards, but no east-west log appeared");
            return;
        }
        helper.succeed();
    }

    /** {@code broken_top} removes the highest logs and leaves the rest of the trunk standing. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void brokenTopRemovesHighestLogs(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 9);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, SNAPPED_TOP, NO_FOLIAGE, trunk(4));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the snapped tree did not place at all");
            return;
        }
        for (int dy = 2; dy <= 3; dy++) {
            if (!helper.getLevel().getBlockState(origin.above(dy)).isAir()) {
                helper.fail("log at +" + dy + " survived: " + helper.getLevel().getBlockState(origin.above(dy)));
                return;
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            if (!helper.getLevel().getBlockState(origin.above(dy)).is(Blocks.OAK_LOG)) {
                helper.fail("log at +" + dy + " should still stand: "
                        + helper.getLevel().getBlockState(origin.above(dy)));
                return;
            }
        }
        helper.succeed();
    }

    /** The break can be filled instead of opened, standing upright like the trunk it replaces. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void brokenTopFillsBrokenLogs(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 10);
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, SNAPPED_TOP_FILLED, NO_FOLIAGE, trunk(4));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the filled-break tree did not place at all");
            return;
        }
        BlockState top = helper.getLevel().getBlockState(origin.above(3));
        if (!top.is(Blocks.BASALT)) {
            helper.fail("the highest log should be replaced by the fill block, found " + top);
            return;
        }
        if (!top.hasProperty(BlockStateProperties.AXIS)
                || top.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
            helper.fail("the fill block was not turned upright: " + top);
            return;
        }
        helper.succeed();
    }

    /** A decorator missing a required field is a decode error, not a defaulted value. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void codecRejectsMissingRequiredField(GameTestHelper helper) {
        String json = "{\"type\": \"isekai_api:attach_to_logs\", " + STAIR_PROVIDER + "}";
        DataResult<TreeDecorator> result = TreeDecorator.CODEC.parse(
                ops(helper), JsonParser.parseString(json));
        if (result.result().isPresent()) {
            helper.fail("a decorator without the required chance field decoded anyway");
            return;
        }
        if (result.error().isEmpty()) {
            helper.fail("missing-field decode produced neither a value nor an error");
            return;
        }
        helper.succeed();
    }

    /** A chance outside 0..1 is rejected instead of being clamped. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void codecRejectsOutOfRangeChance(GameTestHelper helper) {
        String json = "{\"type\": \"isekai_api:attach_to_logs\", " + STAIR_PROVIDER
                + ", \"chance\": 1.5}";
        DataResult<TreeDecorator> result = TreeDecorator.CODEC.parse(
                ops(helper), JsonParser.parseString(json));
        if (result.result().isPresent()) {
            helper.fail("chance 1.5 decoded as " + result.result().get());
            return;
        }
        helper.succeed();
    }

    /** The bare-tree parts reject missing fields and out-of-range values at decode time. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void codecRejectsInvalidBareTreeFields(GameTestHelper helper) {
        List<String> invalid = List.of(
                // branch_stubs without a count
                "{\"type\": \"isekai_api:branch_stubs\", "
                        + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 2}}",
                // start_fraction above 1
                "{\"type\": \"isekai_api:branch_stubs\", "
                        + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 2}, "
                        + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 2}, "
                        + "\"start_fraction\": 1.5}",
                // broken_top count below 1
                "{\"type\": \"isekai_api:broken_top\", "
                        + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 0}}",
                // branch_stubs length below 1
                "{\"type\": \"isekai_api:branch_stubs\", "
                        + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 2}, "
                        + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 0}}");
        for (String json : invalid) {
            DataResult<TreeDecorator> result = TreeDecorator.CODEC.parse(ops(helper), JsonParser.parseString(json));
            if (result.result().isPresent()) {
                helper.fail("this tree decorator should not decode: " + json);
                return;
            }
        }
        // The bare foliage placer keeps the vanilla radius range.
        DataResult<FoliagePlacer> foliage = FoliagePlacer.CODEC.parse(
                ops(helper), JsonParser.parseString("{\"type\": \"isekai_api:none\", \"radius\": 99}"));
        if (foliage.result().isPresent()) {
            helper.fail("isekai_api:none accepted radius 99");
            return;
        }
        helper.succeed();
    }

    /** The shared pin: a leaf a decorator writes gets a decay-safe distance, other blocks do not. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void pinIfLeavesPinsDistance(GameTestHelper helper) {
        BlockState pinned = LeafPlacing.pinIfLeaves(Blocks.OAK_LEAVES.defaultBlockState());
        if (!pinned.hasProperty(LeavesBlock.DISTANCE)
                || pinned.getValue(LeavesBlock.DISTANCE) != LeafPlacing.SAFE_PINNED_DISTANCE) {
            helper.fail("a plain leaf was not pinned to distance " + LeafPlacing.SAFE_PINNED_DISTANCE
                    + ": " + pinned);
            return;
        }
        BlockState untouched = LeafPlacing.pinIfLeaves(Blocks.BROWN_MUSHROOM.defaultBlockState());
        if (!untouched.is(Blocks.BROWN_MUSHROOM) || untouched.hasProperty(LeavesBlock.DISTANCE)) {
            helper.fail("a non-leaf block was rewritten to " + untouched);
            return;
        }
        helper.succeed();
    }

    /**
     * A partly filled break never leaves a fill block over air: in the trunk column, once a log is
     * opened to air, nothing stands above it. Before the fix each removed log chose air or fill on
     * its own, so a basalt plug could float over a gap (seen in the snag example, seed 24303).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void brokenTopFillNeverFloats(GameTestHelper helper) {
        String halfFilled = "{\"type\": \"isekai_api:broken_top\", "
                + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 4}, "
                + "\"fill_provider\": {\"type\": \"minecraft:simple_state_provider\", "
                + "\"state\": {\"Name\": \"minecraft:basalt\", \"Properties\": {\"axis\": \"y\"}}}, "
                + "\"fill_chance\": 0.5}";
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, halfFilled, NO_FOLIAGE, trunk(8));
        int filledSomewhere = 0;
        for (long seed = 1; seed <= 32; seed++) {
            BlockPos origin = TreeTestScenes.prepare(helper, 20);
            if (!TreeTestScenes.place(helper, tree, origin, seed)) {
                helper.fail("the half-filled snag did not place (seed " + seed + ")");
                return;
            }
            boolean opened = false;
            for (int dy = 0; dy < 8; dy++) {
                BlockState state = helper.getLevel().getBlockState(origin.above(dy));
                if (state.isAir()) {
                    opened = true;
                } else if (opened) {
                    helper.fail("seed " + seed + ": " + state + " at +" + dy + " floats over an opened log");
                    return;
                } else if (state.is(Blocks.BASALT)) {
                    filledSomewhere++;
                }
            }
        }
        if (filledSomewhere == 0) {
            helper.fail("no seed used the fill block, so the test proved nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * Decorators after {@code broken_top} only see the logs that survived: no stub grows beside a
     * removed log.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void branchesSkipBrokenLogs(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 21);
        String stubsEverywhere = "{\"type\": \"isekai_api:branch_stubs\", "
                + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 16}, "
                + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 1}, "
                + "\"start_fraction\": 0.0, \"upward_chance\": 0.0}";
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper,
                SNAPPED_TOP + ", " + stubsEverywhere, NO_FOLIAGE, trunk(4));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the snapped, branched tree did not place at all");
            return;
        }
        int stubs = 0;
        for (int dy = 0; dy <= 3; dy++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockState state = helper.getLevel().getBlockState(origin.above(dy).relative(side));
                if (state.isAir()) {
                    continue;
                }
                if (dy >= 2) {
                    helper.fail("a stub grew beside the removed log at +" + dy + ": " + state);
                    return;
                }
                stubs++;
            }
        }
        if (stubs == 0) {
            helper.fail("no stub grew on the surviving logs either");
            return;
        }
        helper.succeed();
    }

    /**
     * A 2x2 trunk writes its ground blocks through the trunk setter, one per column. A decorator that
     * reuses the lowest log has to skip all four: before the fix only the first was dropped, so the
     * other three stayed in the list, the inherited stub block came back as dirt, and the stub base
     * moved one block down.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void giantTrunkGroundBlocksDoNotBecomeStubs(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 22);
        // No block_provider: the stub reuses whatever the tree's lowest log is.
        String northStubs = "{\"type\": \"isekai_api:branch_stubs\", "
                + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 4}, "
                + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 1}, "
                + "\"start_fraction\": 0.0, \"upward_chance\": 0.0, \"directions\": [\"north\"]}";
        String giantTrunk = "{\"type\": \"minecraft:giant_trunk_placer\", "
                + "\"base_height\": 4, \"height_rand_a\": 0, \"height_rand_b\": 0}";
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, northStubs, NO_FOLIAGE, giantTrunk);
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the 2x2-trunk tree did not place at all");
            return;
        }
        // Four columns for three layers, plus the single log the top layer of a giant trunk keeps.
        int trunkLogs = 0;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (dy == 3 && (dx != 0 || dz != 0)) {
                        continue;
                    }
                    if (helper.getLevel().getBlockState(origin.offset(dx, dy, dz)).is(Blocks.OAK_LOG)) {
                        trunkLogs++;
                    }
                }
            }
        }
        if (trunkLogs != 13) {
            helper.fail("expected the 13 logs of a 2x2 base_height-4 trunk, found " + trunkLogs);
            return;
        }
        int stubs = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(3, 3, 3))) {
            boolean inTrunk = pos.getX() >= origin.getX() && pos.getX() <= origin.getX() + 1
                    && pos.getZ() >= origin.getZ() && pos.getZ() <= origin.getZ() + 1;
            BlockState state = helper.getLevel().getBlockState(pos);
            if (inTrunk || state.isAir()) {
                continue;
            }
            if (state.is(Blocks.DIRT)) {
                helper.fail("dirt at " + pos + " above the ground layer: a decorator inherited a ground block");
                return;
            }
            if (state.is(Blocks.OAK_LOG)) {
                stubs++;
                if (state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Z) {
                    helper.fail("a north-going stub kept axis " + state.getValue(BlockStateProperties.AXIS));
                    return;
                }
            }
        }
        if (stubs == 0) {
            helper.fail("no stub grew above the ground layer, so the ground blocks were not skipped");
            return;
        }
        helper.succeed();
    }

    /**
     * A decorator-written leaf keeps its pinned distance only until the leaf ticker recomputes it:
     * after the tree feature's closing shape pass, leaves within six steps of a log keep their
     * distance and stay, while ones further out become distance 7 and are removed by a random tick.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void decoratorLeavesDecayBeyondSixStepsFromLog(GameTestHelper helper) {
        BlockPos origin = TreeTestScenes.prepare(helper, 23);
        // One eight-block run of leaf stubs north of the top log: steps 1..6 are reachable, 7..8 not.
        String leafStub = "{\"type\": \"isekai_api:branch_stubs\", "
                + JUNGLE_LEAVES_PROVIDER + ", "
                + "\"count\": {\"type\": \"minecraft:constant\", \"value\": 16}, "
                + "\"length\": {\"type\": \"minecraft:constant\", \"value\": 8}, "
                + "\"start_fraction\": 1.0, \"upward_chance\": 0.0, \"directions\": [\"north\"]}";
        ConfiguredFeature<?, ?> tree = TreeTestScenes.parseTree(helper, leafStub, NO_FOLIAGE, trunk(4));
        if (!TreeTestScenes.place(helper, tree, origin)) {
            helper.fail("the leaf-stub tree did not place at all");
            return;
        }
        BlockPos tip = origin.above(3);
        if (!helper.getLevel().getBlockState(tip.north(8)).is(Blocks.JUNGLE_LEAVES)) {
            helper.fail("the eight-block leaf strand was not placed, so there is nothing to tick");
            return;
        }
        // Let the shape-edge pass schedule its leaf ticks, then check what the ticker decided. The
        // tree grows in a chunk that was generated on demand, not one the test structures sit in,
        // so the chunk has to be forced for its scheduled block ticks to run at all.
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        helper.getLevel().setChunkForced(chunkX, chunkZ, true);
        helper.runAfterDelay(20, () -> {
            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
            for (int step = 1; step <= 6; step++) {
                BlockPos pos = tip.north(step);
                BlockState state = helper.getLevel().getBlockState(pos);
                if (!state.is(Blocks.JUNGLE_LEAVES)) {
                    helper.fail("leaf " + step + ", within six steps of the log, is " + state);
                    return;
                }
                if (state.getValue(LeavesBlock.DISTANCE) > step) {
                    helper.fail("leaf " + step + " is at distance " + state.getValue(LeavesBlock.DISTANCE)
                            + " instead of " + step);
                    return;
                }
            }
            for (int step = 7; step <= 8; step++) {
                BlockPos pos = tip.north(step);
                BlockState state = helper.getLevel().getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (!state.is(Blocks.JUNGLE_LEAVES) || state.getValue(LeavesBlock.DISTANCE) < 7) {
                    helper.fail("leaf " + step + " was recomputed to " + state + " instead of distance 7");
                    return;
                }
                // helper.randomTick takes a relative position, and this chunk is far from the test
                // structure, so tick the block directly at the absolute position instead.
                var level = helper.getLevel();
                level.getBlockState(pos).randomTick(level, pos, level.random);
                if (!helper.getLevel().getBlockState(pos).isAir()) {
                    helper.fail("leaf " + step + " at distance 7 survived a random tick");
                    return;
                }
            }
            helper.succeed();
        });
    }

    /** A straight trunk of a fixed height, for tests that only care about what grows on it. */
    private static String trunk(int height) {
        return "{\"type\": \"minecraft:straight_trunk_placer\", "
                + "\"base_height\": " + height + ", \"height_rand_a\": 0, \"height_rand_b\": 0}";
    }
}
