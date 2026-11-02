package com.kuronami.isekaiapi.gametest.wood;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.impl.wood.WoodBoat;
import com.kuronami.isekaiapi.impl.wood.WoodChestBoat;
import com.kuronami.isekaiapi.impl.wood.WoodSets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side acceptance for the wood-set builder: what a consumer asks for gets registered, the
 * vanilla behaviour a species is expected to have is actually wired up, and Isekai ships no species
 * of its own.
 *
 * <p>The fixtures come from {@link WoodSetTestFixtures}, which declares them through the same public
 * builder a consumer mod uses.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class WoodSetGameTests {

    private WoodSetGameTests() {}

    private static WoodSet full() {
        WoodSet set = WoodSetTestFixtures.full;
        if (set == null) {
            throw new IllegalStateException("wood-set fixtures were not registered — this must run on a GameTest server");
        }
        return set;
    }

    private static WoodSet sparse() {
        WoodSet set = WoodSetTestFixtures.sparse;
        if (set == null) {
            throw new IllegalStateException("wood-set fixtures were not registered — this must run on a GameTest server");
        }
        return set;
    }

    /** Every selected part exists, and both halves of each paired part are bound. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void fullSetRegistersEveryPart(GameTestHelper helper) {
        WoodSet set = full();
        if (set.log().orElseThrow().get().asItem() == Items.AIR) {
            helper.fail("log has no item");
            return;
        }
        if (set.strippedLog().isEmpty() || set.strippedWood().isEmpty()) {
            helper.fail("stripped log/wood missing after log().wood().strippedLog().strippedWood()");
            return;
        }
        if (set.planks().isEmpty() || set.stairs().isEmpty() || set.slab().isEmpty() || set.fence().isEmpty()
                || set.fenceGate().isEmpty() || set.door().isEmpty() || set.trapdoor().isEmpty()
                || set.button().isEmpty() || set.pressurePlate().isEmpty()) {
            helper.fail("family() did not register all nine sawn parts");
            return;
        }
        if (set.sign().isEmpty() || set.wallSign().isEmpty() || set.signItem().isEmpty()
                || set.hangingSign().isEmpty() || set.wallHangingSign().isEmpty() || set.hangingSignItem().isEmpty()) {
            helper.fail("signs() did not register both sign kinds and their items");
            return;
        }
        if (set.leaves().isEmpty() || set.leavesItem().isEmpty() || set.sapling().isEmpty()
                || set.saplingItem().isEmpty() || set.pottedSapling().isEmpty()) {
            helper.fail("leaves/sapling/potted sapling not all registered");
            return;
        }
        if (set.boatEntity().isEmpty() || set.chestBoatEntity().isEmpty()
                || set.boatItem().isEmpty() || set.chestBoatItem().isEmpty()) {
            helper.fail("boats() did not register both entity types and both items");
            return;
        }
        if (set.woodType() == null || set.blockSetType() == null) {
            helper.fail("wood type / block set type were not created");
            return;
        }
        if (!set.id().getNamespace().equals(BuiltInRegistries.BLOCK.getKey(set.log().orElseThrow().get()).getNamespace())) {
            helper.fail("log was not registered in the wood set's namespace");
            return;
        }
        if (!set.id().getNamespace().equals(BuiltInRegistries.ENTITY_TYPE.getKey(set.boatEntity().orElseThrow().get()).getNamespace())) {
            helper.fail("boat entity was not registered in the wood set's namespace");
            return;
        }
        helper.succeed();
    }

    /** A set that selects two parts registers those two and nothing else. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void sparseSetRegistersOnlySelectedParts(GameTestHelper helper) {
        WoodSet set = sparse();
        if (set.log().isEmpty() || set.planks().isEmpty()) {
            helper.fail("log().planks() did not register its two parts");
            return;
        }
        if (set.wood().isPresent() || set.strippedLog().isPresent() || set.strippedWood().isPresent()
                || set.leaves().isPresent() || set.sapling().isPresent() || set.pottedSapling().isPresent()
                || set.sign().isPresent() || set.hangingSign().isPresent() || set.stairs().isPresent()
                || set.door().isPresent() || set.boatEntity().isPresent() || set.boatItem().isPresent()) {
            helper.fail("unselected parts were registered anyway");
            return;
        }
        helper.succeed();
    }

    /** An axe turns the log into its stripped log and the wood into its stripped wood, keeping the axis. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void axeStripsLogAndWoodKeepingAxis(GameTestHelper helper) {
        WoodSet set = full();
        Block log = set.log().orElseThrow().get();
        Block wood = set.wood().orElseThrow().get();
        Block strippedLog = set.strippedLog().orElseThrow().get();
        Block strippedWood = set.strippedWood().orElseThrow().get();

        strip(helper, log, strippedLog, new BlockPos(0, 2, 0), Direction.Axis.X);
        strip(helper, wood, strippedWood, new BlockPos(2, 2, 2), Direction.Axis.Z);
        helper.succeed();
    }

    private static void strip(GameTestHelper helper, Block from, Block expected, BlockPos pos, Direction.Axis axis) {
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
            helper.fail("stripping " + BuiltInRegistries.BLOCK.getKey(from) + " lost the axis ("
                    + after.getValue(RotatedPillarBlock.AXIS) + " instead of " + axis + ")");
        }
    }

    /** Fire odds match vanilla oak part for part, including the parts vanilla does not burn. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void flammabilityMatchesVanillaOak(GameTestHelper helper) {
        WoodSet set = full();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();

        compareFire(helper, level, pos, "planks", set.planks().orElseThrow().get(), Blocks.OAK_PLANKS, 5, 20);
        compareFire(helper, level, pos, "stairs", set.stairs().orElseThrow().get(), Blocks.OAK_STAIRS, 5, 20);
        compareFire(helper, level, pos, "slab", set.slab().orElseThrow().get(), Blocks.OAK_SLAB, 5, 20);
        compareFire(helper, level, pos, "fence", set.fence().orElseThrow().get(), Blocks.OAK_FENCE, 5, 20);
        compareFire(helper, level, pos, "fence gate", set.fenceGate().orElseThrow().get(), Blocks.OAK_FENCE_GATE, 5, 20);
        compareFire(helper, level, pos, "log", set.log().orElseThrow().get(), Blocks.OAK_LOG, 5, 5);
        compareFire(helper, level, pos, "wood", set.wood().orElseThrow().get(), Blocks.OAK_WOOD, 5, 5);
        compareFire(helper, level, pos, "stripped log", set.strippedLog().orElseThrow().get(), Blocks.STRIPPED_OAK_LOG, 5, 5);
        compareFire(helper, level, pos, "stripped wood", set.strippedWood().orElseThrow().get(), Blocks.STRIPPED_OAK_WOOD, 5, 5);
        compareFire(helper, level, pos, "leaves", set.leaves().orElseThrow().get(), Blocks.OAK_LEAVES, 30, 60);

        // Vanilla does not burn these, and neither should the set's copies.
        compareFire(helper, level, pos, "door", set.door().orElseThrow().get(), Blocks.OAK_DOOR, 0, 0);
        compareFire(helper, level, pos, "trapdoor", set.trapdoor().orElseThrow().get(), Blocks.OAK_TRAPDOOR, 0, 0);
        compareFire(helper, level, pos, "sign", set.sign().orElseThrow().get(), Blocks.OAK_SIGN, 0, 0);

        helper.succeed();
    }

    private static void compareFire(GameTestHelper helper, ServerLevel level, BlockPos pos, String part,
                                    Block ours, Block vanilla, int expectedSpread, int expectedFlammability) {
        BlockState ourState = ours.defaultBlockState();
        BlockState vanillaState = vanilla.defaultBlockState();
        int spread = ourState.getFireSpreadSpeed(level, pos, Direction.UP);
        int flammable = ourState.getFlammability(level, pos, Direction.UP);
        int vanillaSpread = vanillaState.getFireSpreadSpeed(level, pos, Direction.UP);
        int vanillaFlammable = vanillaState.getFlammability(level, pos, Direction.UP);
        if (vanillaSpread != expectedSpread || vanillaFlammable != expectedFlammability) {
            helper.fail("vanilla " + BuiltInRegistries.BLOCK.getKey(vanilla) + " is " + vanillaSpread + "/"
                    + vanillaFlammable + ", test expects " + expectedSpread + "/" + expectedFlammability);
            return;
        }
        if (spread != vanillaSpread || flammable != vanillaFlammable) {
            helper.fail(part + " is " + spread + "/" + flammable + " but vanilla oak is "
                    + vanillaSpread + "/" + vanillaFlammable);
        }
    }

    /** Leaves and saplings compost with vanilla oak's chance, so a composter accepts them. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void leavesAndSaplingCompost(GameTestHelper helper) {
        WoodSet set = full();
        float leaves = ComposterBlock.getValue(new ItemStack(set.leavesItem().orElseThrow().get()));
        float sapling = ComposterBlock.getValue(new ItemStack(set.saplingItem().orElseThrow().get()));
        float vanillaLeaves = ComposterBlock.getValue(new ItemStack(Items.OAK_LEAVES));
        float vanillaSapling = ComposterBlock.getValue(new ItemStack(Items.OAK_SAPLING));

        if (leaves <= 0.0F) {
            helper.fail("leaves are not compostable (chance " + leaves + ")");
            return;
        }
        if (sapling <= 0.0F) {
            helper.fail("sapling is not compostable (chance " + sapling + ")");
            return;
        }
        if (Math.abs(leaves - vanillaLeaves) > 1.0E-6F || Math.abs(sapling - vanillaSapling) > 1.0E-6F) {
            helper.fail("compost chance differs from vanilla oak: leaves " + leaves + " vs " + vanillaLeaves
                    + ", sapling " + sapling + " vs " + vanillaSapling);
            return;
        }
        helper.succeed();
    }

    /** Sign and hanging-sign blocks are valid blocks of vanilla's block entity types, and really hold one. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void signBlockEntitiesAcceptSetBlocks(GameTestHelper helper) {
        WoodSet set = full();
        Block sign = set.sign().orElseThrow().get();
        Block wallSign = set.wallSign().orElseThrow().get();
        Block hangingSign = set.hangingSign().orElseThrow().get();
        Block wallHangingSign = set.wallHangingSign().orElseThrow().get();

        if (!BlockEntityType.SIGN.isValid(sign.defaultBlockState())
                || !BlockEntityType.SIGN.isValid(wallSign.defaultBlockState())) {
            helper.fail("sign blocks are not in BlockEntityType.SIGN's valid blocks");
            return;
        }
        if (!BlockEntityType.HANGING_SIGN.isValid(hangingSign.defaultBlockState())
                || !BlockEntityType.HANGING_SIGN.isValid(wallHangingSign.defaultBlockState())) {
            helper.fail("hanging sign blocks are not in BlockEntityType.HANGING_SIGN's valid blocks");
            return;
        }

        BlockPos support = new BlockPos(1, 1, 1);
        helper.setBlock(support, Blocks.STONE);
        BlockPos signPos = support.above();
        helper.setBlock(signPos, sign.defaultBlockState());
        BlockEntity entity = helper.getBlockEntity(signPos);
        if (!(entity instanceof SignBlockEntity)) {
            helper.fail("placed sign holds " + (entity == null ? "no block entity" : entity.getType().toString())
                    + " instead of a SignBlockEntity");
            return;
        }
        helper.succeed();
    }

    /** The flower pot knows the sapling, so it can be placed into one. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void saplingFitsInFlowerPot(GameTestHelper helper) {
        WoodSet set = full();
        Block sapling = set.sapling().orElseThrow().get();
        FlowerPotBlock pot = (FlowerPotBlock) Blocks.FLOWER_POT;
        Block filled = pot.getFullPotsView().get(BuiltInRegistries.BLOCK.getKey(sapling)) != null
                ? pot.getFullPotsView().get(BuiltInRegistries.BLOCK.getKey(sapling)).get()
                : null;
        if (filled == null) {
            helper.fail("flower pot has no entry for " + BuiltInRegistries.BLOCK.getKey(sapling));
            return;
        }
        if (filled != set.pottedSapling().orElseThrow().get()) {
            helper.fail("flower pot maps the sapling to " + BuiltInRegistries.BLOCK.getKey(filled)
                    + " instead of the set's potted sapling");
            return;
        }
        helper.succeed();
    }

    /** Both boats spawn as wood-set vehicles, drop their own item, and carry a passenger. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void boatsSpawnAndCarryAPassenger(GameTestHelper helper) {
        WoodSet set = full();
        ServerLevel level = helper.getLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));

        Boat boat = set.boatEntity().orElseThrow().get().create(level);
        if (!(boat instanceof WoodBoat)) {
            helper.fail("boat entity type created " + (boat == null ? "null" : boat.getClass().getSimpleName()));
            return;
        }
        boat.setPos(at.x, at.y, at.z);
        if (!level.addFreshEntity(boat)) {
            helper.fail("boat could not be placed in the level");
            return;
        }
        if (boat.getDropItem() != set.boatItem().orElseThrow().get()) {
            helper.fail("boat drops " + BuiltInRegistries.ITEM.getKey(boat.getDropItem())
                    + " instead of the set's boat item");
            boat.discard();
            return;
        }
        rideAndCheck(helper, level, boat, at);
        boat.discard();

        Boat chestBoat = set.chestBoatEntity().orElseThrow().get().create(level);
        if (!(chestBoat instanceof WoodChestBoat)) {
            helper.fail("chest boat entity type created "
                    + (chestBoat == null ? "null" : chestBoat.getClass().getSimpleName()));
            return;
        }
        chestBoat.setPos(at.x, at.y, at.z);
        if (!level.addFreshEntity(chestBoat)) {
            helper.fail("chest boat could not be placed in the level");
            return;
        }
        if (chestBoat.getDropItem() != set.chestBoatItem().orElseThrow().get()) {
            helper.fail("chest boat drops " + BuiltInRegistries.ITEM.getKey(chestBoat.getDropItem())
                    + " instead of the set's chest boat item");
            chestBoat.discard();
            return;
        }
        rideAndCheck(helper, level, chestBoat, at);
        chestBoat.discard();
        helper.succeed();
    }

    private static void rideAndCheck(GameTestHelper helper, ServerLevel level, Boat boat, Vec3 at) {
        Player rider = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        rider.setPos(at.x, at.y, at.z);
        if (!rider.startRiding(boat, true)) {
            helper.fail("a player cannot board " + BuiltInRegistries.ENTITY_TYPE.getKey(boat.getType()));
            return;
        }
        if (rider.getVehicle() != boat || !boat.getPassengers().contains(rider)) {
            helper.fail("rider is not on " + BuiltInRegistries.ENTITY_TYPE.getKey(boat.getType()));
            return;
        }
        rider.stopRiding();
    }

    /** The texture a boat of this set is drawn with follows vanilla's layout. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void boatTextureLocationFollowsVanillaLayout(GameTestHelper helper) {
        WoodSet set = full();
        String path = set.id().getPath();
        String namespace = set.id().getNamespace();
        if (!WoodSets.boatTexture(set.id(), false).equals(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "textures/entity/boat/" + path + ".png"))) {
            helper.fail("boat texture is " + WoodSets.boatTexture(set.id(), false));
            return;
        }
        if (!WoodSets.boatTexture(set.id(), true).equals(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "textures/entity/chest_boat/" + path + ".png"))) {
            helper.fail("chest boat texture is " + WoodSets.boatTexture(set.id(), true));
            return;
        }
        helper.succeed();
    }

    /** Only the sets declared through the builder exist: the library itself ships no species. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void libraryDeclaresNoSpeciesOfItsOwn(GameTestHelper helper) {
        List<WoodSet> declared = WoodSets.all();
        if (declared.size() != 2 || declared.get(0) != full() || declared.get(1) != sparse()) {
            helper.fail("expected exactly the two fixture sets, got " + declared.size());
            return;
        }
        helper.succeed();
    }
}
