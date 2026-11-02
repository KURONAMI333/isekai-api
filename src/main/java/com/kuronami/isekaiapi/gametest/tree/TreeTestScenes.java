package com.kuronami.isekaiapi.gametest.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Scene building shared by the tree GameTests.
 *
 * <p>GameTest's {@code empty3x3x3} template is a 3x3x3 hole with barriers just outside it, which is
 * too small for a tree: a trunk is 4+ blocks tall and a crown is 5 wide. Every test here therefore
 * builds its own patch of ground far above the flat test world's surface (y=100, which is air) and
 * grows into that, instead of adding a bigger structure template — a template would have to ship in
 * the published jar, and Isekai ships no worldgen data.
 *
 * <p>Slots are 64 blocks apart so a crown, a decorator's ground patch (radius up to 16) or a
 * neighbouring test's leftovers cannot reach across.
 */
final class TreeTestScenes {

    /** X of slot 0's platform centre. */
    private static final int BASE_X = 3000;

    /** Z of every platform centre. */
    private static final int BASE_Z = 3000;

    /** Y of the ground block; the tree's lowest log sits one above. */
    static final int GROUND_Y = 100;

    /** Distance between two slots. */
    private static final int SLOT_SPACING = 64;

    /** Half-width of the grass platform, so a 2x2 trunk and a radius-2 crown fit. */
    private static final int PLATFORM_RADIUS = 4;

    /** Air cleared above the platform, enough for the tallest test tree. */
    private static final int CLEAR_HEIGHT = 24;

    /** A vanilla blob crown, used when a test only cares about decorators. */
    private static final String VANILLA_FOLIAGE = """
            {
              "type": "minecraft:blob_foliage_placer",
              "height": 3,
              "offset": 0,
              "radius": 2
            }
            """;

    /** A fixed 4-log trunk, used when a test only cares about decorators. */
    private static final String VANILLA_TRUNK = """
            {
              "type": "minecraft:straight_trunk_placer",
              "base_height": 4,
              "height_rand_a": 0,
              "height_rand_b": 0
            }
            """;

    /**
     * A vanilla {@code minecraft:tree} configured feature, with the decorator array, the foliage
     * placer and the trunk placer substituted in. Written as datapack JSON on purpose: the decorator
     * and placer types have to be reachable through the real configured-feature codec, not only
     * through a constructor.
     */
    private static final String TREE_JSON = """
            {
              "type": "minecraft:tree",
              "config": {
                "decorators": [__DECORATORS__],
                "dirt_provider": {
                  "type": "minecraft:simple_state_provider",
                  "state": {"Name": "minecraft:dirt"}
                },
                "foliage_placer": __FOLIAGE__,
                "foliage_provider": {
                  "type": "minecraft:simple_state_provider",
                  "state": {
                    "Name": "minecraft:oak_leaves",
                    "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}
                  }
                },
                "force_dirt": false,
                "ignore_vines": true,
                "minimum_size": {
                  "type": "minecraft:two_layers_feature_size",
                  "limit": 1,
                  "lower_size": 0,
                  "upper_size": 1
                },
                "trunk_placer": __TRUNK__,
                "trunk_provider": {
                  "type": "minecraft:simple_state_provider",
                  "state": {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}}
                }
              }
            }
            """;

    private TreeTestScenes() {}

    /**
     * Builds slot {@code slot}'s platform and returns the position a sapling or tree would occupy —
     * the top of the centre grass block.
     */
    static BlockPos prepare(GameTestHelper helper, int slot) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = new BlockPos(BASE_X + slot * SLOT_SPACING, GROUND_Y, BASE_Z);
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                level.setBlock(ground.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    level.setBlock(ground.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return ground.above();
    }

    /** Parses {@code decoratorJson} inside a real {@code minecraft:tree} configured feature. */
    static ConfiguredFeature<?, ?> parseTree(GameTestHelper helper, String decoratorJson) {
        return parseTree(helper, decoratorJson, VANILLA_FOLIAGE, VANILLA_TRUNK);
    }

    /**
     * Parses a tree with the given decorators, foliage placer and trunk placer. Each argument is the
     * raw JSON body of that field, so a test can swap in {@code isekai_api:none} or a taller trunk
     * without writing a whole configured feature.
     */
    static ConfiguredFeature<?, ?> parseTree(GameTestHelper helper, String decoratorJson,
                                             String foliageJson, String trunkJson) {
        String json = TREE_JSON
                .replace("__DECORATORS__", decoratorJson)
                .replace("__FOLIAGE__", foliageJson)
                .replace("__TRUNK__", trunkJson);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        return ConfiguredFeature.DIRECT_CODEC.parse(ops, JsonParser.parseString(json))
                .getOrThrow(message -> new IllegalStateException("minecraft:tree decode failed: " + message));
    }

    /** Places {@code feature} at {@code origin} and returns whether the feature reported success. */
    static boolean place(GameTestHelper helper, ConfiguredFeature<?, ?> feature, BlockPos origin) {
        return place(helper, feature, origin, 0x5EEDL);
    }

    /** Places {@code feature} at {@code origin} with an explicit seed. */
    static boolean place(GameTestHelper helper, ConfiguredFeature<?, ?> feature, BlockPos origin, long seed) {
        ServerLevel level = helper.getLevel();
        level.getChunk(origin);
        return feature.place(level, level.getChunkSource().getGenerator(),
                net.minecraft.util.RandomSource.create(seed), origin);
    }

    /** How many blocks matching {@code match} sit in the inclusive box. */
    static int count(ServerLevel level, BlockPos from, BlockPos to, Predicate<BlockState> match) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (match.test(level.getBlockState(pos))) {
                count++;
            }
        }
        return count;
    }

    /** The inclusive box around a tree: {@code radius} blocks out, {@code height} blocks up. */
    static BlockPos boxMin(BlockPos origin, int radius) {
        return origin.offset(-radius, 0, -radius);
    }

    static BlockPos boxMax(BlockPos origin, int radius, int height) {
        return origin.offset(radius, height, radius);
    }
}
