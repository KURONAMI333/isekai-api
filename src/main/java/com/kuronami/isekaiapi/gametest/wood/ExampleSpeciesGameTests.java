package com.kuronami.isekaiapi.gametest.wood;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.examples.wood.ExampleWoodMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * End-to-end acceptance for {@code examples/4_wood}: the example species is declared by one class,
 * shipped as one data pack, and behaves like a real wood species in a running server.
 *
 * <p>These tests are what makes the example evidence rather than a snippet: the sapling grows the
 * tree the pack describes, an axe strips the logs, and every part has a recipe the recipe manager
 * actually decoded and can match.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class ExampleSpeciesGameTests {

    private ExampleSpeciesGameTests() {}

    private static WoodSet example() {
        WoodSet set = ExampleWoodMod.example;
        if (set == null) {
            throw new IllegalStateException(
                    "the example species was not registered — this must run on a GameTest server");
        }
        return set;
    }

    /**
     * The sapling grows the configured feature shipped in the example's pack — the trunk is the
     * species' own log, so the tree and the blocks come from the same species and the same pack.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void saplingGrowsThePacksTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WoodSet set = example();
        BlockPos origin = WoodTestScenes.prepare(helper, 0);
        Block log = set.log().orElseThrow().get();
        Block leaves = set.leaves().orElseThrow().get();

        level.setBlock(origin, set.sapling().orElseThrow().get().defaultBlockState(), 2);
        if (!bonemealUntil(level, origin, state -> state.is(log))) {
            helper.fail("bone meal never grew the example sapling; the block is now "
                    + level.getBlockState(origin));
            return;
        }
        int crowns = WoodTestScenes.count(level,
                WoodTestScenes.boxMin(origin, 3), WoodTestScenes.boxMax(origin, 3, 8),
                state -> state.is(leaves));
        if (crowns == 0) {
            helper.fail("a trunk grew but the species' own leaves are missing");
            return;
        }
        helper.succeed();
    }

    /** An axe strips the species' log and wood, keeping the axis. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void axeStripsTheSpecies(GameTestHelper helper) {
        WoodSet set = example();
        strip(helper, set.log().orElseThrow().get(), set.strippedLog().orElseThrow().get(),
                new BlockPos(0, 2, 0), Direction.Axis.X);
        strip(helper, set.wood().orElseThrow().get(), set.strippedWood().orElseThrow().get(),
                new BlockPos(2, 2, 2), Direction.Axis.Z);
        helper.succeed();
    }

    private static void strip(GameTestHelper helper, Block from, Block expected, BlockPos pos,
                              Direction.Axis axis) {
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        helper.setBlock(pos, from.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));

        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        InteractionResult result = axe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (result == InteractionResult.PASS) {
            helper.fail("axe did nothing to " + BuiltInRegistries.BLOCK.getKey(from));
            return;
        }
        BlockState after = helper.getBlockState(pos);
        if (after.getBlock() != expected) {
            helper.fail("axe produced " + BuiltInRegistries.BLOCK.getKey(after.getBlock())
                    + " instead of " + BuiltInRegistries.BLOCK.getKey(expected));
            return;
        }
        if (after.getValue(RotatedPillarBlock.AXIS) != axis) {
            helper.fail("stripping " + BuiltInRegistries.BLOCK.getKey(from) + " lost the axis");
        }
    }

    /**
     * Every part the generators write a recipe for has one the recipe manager decoded, and the
     * planks recipe really matches a log. A recipe file that fails to parse would be missing here
     * rather than silently absent in game.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void everyPartHasARecipeAndPlanksCraftFromOneLog(GameTestHelper helper) {
        WoodSet set = example();
        ServerLevel level = helper.getLevel();
        HolderLookup.Provider registries = level.registryAccess();

        if (level.getRecipeManager().hadErrorsLoading()) {
            helper.fail("the recipe manager reported load errors; the example pack is malformed");
            return;
        }

        List<Item> expected = new ArrayList<>();
        expected.add(set.planks().orElseThrow().get().asItem());
        expected.add(set.wood().orElseThrow().get().asItem());
        expected.add(set.strippedWood().orElseThrow().get().asItem());
        expected.add(set.stairs().orElseThrow().get().asItem());
        expected.add(set.slab().orElseThrow().get().asItem());
        expected.add(set.fence().orElseThrow().get().asItem());
        expected.add(set.fenceGate().orElseThrow().get().asItem());
        expected.add(set.door().orElseThrow().get().asItem());
        expected.add(set.trapdoor().orElseThrow().get().asItem());
        expected.add(set.button().orElseThrow().get().asItem());
        expected.add(set.pressurePlate().orElseThrow().get().asItem());
        expected.add(set.signItem().orElseThrow().get());
        expected.add(set.hangingSignItem().orElseThrow().get());
        expected.add(set.boatItem().orElseThrow().get());
        expected.add(set.chestBoatItem().orElseThrow().get());

        Set<Item> produced = new HashSet<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            produced.add(holder.value().getResultItem(registries).getItem());
        }
        List<String> missing = new ArrayList<>();
        for (Item item : expected) {
            if (!produced.contains(item)) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("no loaded recipe produces " + missing);
            return;
        }

        CraftingInput oneLog = CraftingInput.of(1, 1,
                List.of(new ItemStack(set.log().orElseThrow().get())));
        Optional<RecipeHolder<CraftingRecipe>> planks =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, oneLog, level);
        if (planks.isEmpty()) {
            helper.fail("one " + BuiltInRegistries.BLOCK.getKey(set.log().orElseThrow().get())
                    + " matches no crafting recipe");
            return;
        }
        ItemStack result = planks.get().value().getResultItem(registries);
        if (!result.is(set.planks().orElseThrow().get().asItem()) || result.getCount() != 4) {
            helper.fail("one log crafts " + result + " instead of 4 planks");
            return;
        }
        helper.succeed();
    }

    /**
     * Applies bone meal at {@code pos} until {@code grown} matches, the way a player would: the
     * sapling only advances one stage per successful roll, so a single use is not enough.
     */
    private static boolean bonemealUntil(ServerLevel level, BlockPos pos,
                                         java.util.function.Predicate<BlockState> grown) {
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
