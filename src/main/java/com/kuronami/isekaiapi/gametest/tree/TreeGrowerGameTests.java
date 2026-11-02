package com.kuronami.isekaiapi.gametest.tree;

import com.google.gson.JsonElement;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.TreeGrowers;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.gametest.wood.WoodSetTestFixtures;
import com.mojang.serialization.JsonOps;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance for {@link TreeGrowers}: a grower built from configured-feature keys actually grows
 * the trees it was given, and the grower a {@link WoodSet} sapling carries grows when bonemealed —
 * both the single-sapling shape and the 2x2 mega shape.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class TreeGrowerGameTests {

    private static final ResourceKey<ConfiguredFeature<?, ?>> OAK =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("oak"));

    private TreeGrowerGameTests() {}

    private static WoodSet full() {
        WoodSet set = WoodSetTestFixtures.full;
        if (set == null) {
            throw new IllegalStateException("wood-set fixtures were not registered — this must run on a GameTest server");
        }
        return set;
    }

    /**
     * The grower name is what vanilla's {@code TreeGrower} codec resolves, so it has to come out of
     * the wood set id without extra wiring.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void growerNameIsTheWoodSetId(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WoodSetTestFixtures.TEST_MODID, "name_probe");
        TreeGrower grower = TreeGrowers.builder(id).tree(OAK).build();
        JsonElement encoded = TreeGrower.CODEC.encodeStart(JsonOps.INSTANCE, grower)
                .getOrThrow(message -> new IllegalStateException("grower name encode failed: " + message));
        if (!"isekai_api_test/name_probe".equals(encoded.getAsString())) {
            helper.fail("grower encodes as " + encoded + " instead of the wood set id");
            return;
        }
        helper.succeed();
    }

    /** The helper refuses a grower with no tree and a secondary chance outside 0..1. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void builderRejectsMissingTreeAndBadChance(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(WoodSetTestFixtures.TEST_MODID, "invalid_probe");
        boolean missingTreeRejected = false;
        try {
            TreeGrowers.builder(id).build();
        } catch (IllegalStateException expected) {
            missingTreeRejected = true;
        }
        if (!missingTreeRejected) {
            helper.fail("build() without tree(...) produced a grower");
            return;
        }
        boolean badChanceRejected = false;
        try {
            TreeGrowers.builder(id).tree(OAK).secondaryChance(1.5F);
        } catch (IllegalArgumentException expected) {
            badChanceRejected = true;
        }
        if (!badChanceRejected) {
            helper.fail("secondaryChance(1.5) was accepted");
            return;
        }
        helper.succeed();
    }

    /** One bonemealed sapling of the fixture set grows the configured tree it was built with. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void helperBuiltGrowerGrowsOneSapling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = TreeTestScenes.prepare(helper, 4);
        BlockState sapling = full().sapling().orElseThrow().get().defaultBlockState();
        level.setBlock(origin, sapling, 2);

        if (!bonemealUntil(level, origin, state -> state.is(Blocks.OAK_LOG))) {
            helper.fail("the helper-built grower never grew from one sapling; block is now "
                    + level.getBlockState(origin));
            return;
        }
        int leaves = TreeTestScenes.count(level,
                TreeTestScenes.boxMin(origin, 3), TreeTestScenes.boxMax(origin, 3, 8),
                state -> state.is(Blocks.OAK_LEAVES));
        if (leaves == 0) {
            helper.fail("a trunk grew but the crown is missing");
            return;
        }
        helper.succeed();
    }

    /** Four saplings in a 2x2 use the grower's mega tree, which the fixture sets to dark oak. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void helperBuiltGrowerGrowsFourSaplingsAsMegaTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = TreeTestScenes.prepare(helper, 5);
        BlockState sapling = full().sapling().orElseThrow().get().defaultBlockState();
        level.setBlock(origin, sapling, 2);
        level.setBlock(origin.east(), sapling, 2);
        level.setBlock(origin.south(), sapling, 2);
        level.setBlock(origin.south().east(), sapling, 2);

        if (!bonemealUntil(level, origin, state -> state.is(Blocks.DARK_OAK_LOG))) {
            helper.fail("four saplings did not grow the mega tree; block at the corner is now "
                    + level.getBlockState(origin) + ", east " + level.getBlockState(origin.east())
                    + ", south " + level.getBlockState(origin.south()));
            return;
        }
        if (!level.getBlockState(origin.east()).is(Blocks.DARK_OAK_LOG)) {
            helper.fail("the mega tree did not take the whole 2x2 base: east is "
                    + level.getBlockState(origin.east()));
            return;
        }
        int leaves = TreeTestScenes.count(level,
                TreeTestScenes.boxMin(origin, 4), TreeTestScenes.boxMax(origin, 4, 12),
                state -> state.is(Blocks.DARK_OAK_LEAVES));
        if (leaves == 0) {
            helper.fail("a mega trunk grew but the crown is missing");
            return;
        }
        helper.succeed();
    }

    /**
     * Applies bone meal at {@code pos} until {@code grown} matches, the way a player would: the
     * sapling only advances one stage per successful roll, so a single use is not enough.
     */
    private static boolean bonemealUntil(ServerLevel level, BlockPos pos, Predicate<BlockState> grown) {
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 64);
        for (int attempt = 0; attempt < 256; attempt++) {
            if (grown.test(level.getBlockState(pos))) {
                return true;
            }
            if (meal.isEmpty()) {
                meal = new ItemStack(Items.BONE_MEAL, 64);
            }
            net.minecraft.world.item.BoneMealItem.applyBonemeal(meal, level, pos, null);
        }
        return grown.test(level.getBlockState(pos));
    }
}
