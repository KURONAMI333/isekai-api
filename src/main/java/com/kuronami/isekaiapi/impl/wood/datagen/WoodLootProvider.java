package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jetbrains.annotations.ApiStatus;

/**
 * Block loot tables of every wood entry: {@code dropSelf} for the sawn blocks, vanilla's slab and
 * door tables, the leaves table and the flower-pot table.
 *
 * <p>Wall signs and wall hanging signs are left out on purpose. Both are registered with
 * {@code lootFrom} their standing counterpart, so they resolve to the same loot table and writing
 * a second copy would only overwrite an identical file.
 */
@ApiStatus.Internal
public final class WoodLootProvider extends BlockLootSubProvider {

    /** Fortune chances of the sticks pool, copied from vanilla's leaves table. */
    private static final float[] STICK_CHANCES = {0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

    private final List<WoodEntry> entries;
    private final List<Block> known = new ArrayList<>();

    public WoodLootProvider(List<WoodEntry> entries, HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        this.entries = entries;
    }

    @Override
    protected void generate() {
        for (WoodEntry entry : entries) {
            WoodGen gen = new WoodGen(entry, null);
            for (WoodPart part : WoodPart.values()) {
                if (gen.has(part)) {
                    addLoot(gen, part);
                }
            }
        }
    }

    private void addLoot(WoodGen gen, WoodPart part) {
        switch (part) {
            case LOG, WOOD, STRIPPED_LOG, STRIPPED_WOOD, PLANKS, STAIRS, FENCE, FENCE_GATE,
                    BUTTON, PRESSURE_PLATE, SIGN, HANGING_SIGN, TRAPDOOR, SAPLING ->
                    dropSelf(gen.block(part).orElseThrow());
            case SLAB -> {
                Block block = gen.block(part).orElseThrow();
                add(block, createSlabItemTable(block));
            }
            case DOOR -> {
                Block block = gen.block(part).orElseThrow();
                add(block, createDoorTable(block));
            }
            case LEAVES -> addLeaves(gen);
            case POTTED_SAPLING -> dropPottedContents(gen.block(part).orElseThrow());
            case BOAT, CHEST_BOAT -> {
                // Items only: no block, so no loot table.
            }
        }
    }

    private void addLeaves(WoodGen gen) {
        LeavesBlock leaves = gen.leaves().orElseThrow();
        Block sapling = gen.block(WoodPart.SAPLING).orElse(null);
        if (sapling == null) {
            // Nothing to drop as a sapling; keep silk touch and the sticks, skip the empty slot.
            add(leaves, sticksOnly(leaves));
            return;
        }
        WoodSetDataGen.LeavesDrops mode = gen.leavesDrops();
        add(leaves, mode == WoodSetDataGen.LeavesDrops.SAPLING_ONLY
                ? createLeavesDrops(leaves, sapling, NORMAL_LEAVES_SAPLING_CHANCES)
                : createOakLeavesDrops(leaves, sapling, NORMAL_LEAVES_SAPLING_CHANCES));
    }

    /** Vanilla's leaves table without the sapling entry, for a set that has no sapling. */
    private LootTable.Builder sticksOnly(Block leaves) {
        HolderLookup.RegistryLookup<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        LootItemCondition.Builder shearsOrSilkTouch = HAS_SHEARS.or(hasSilkTouch());
        LootPoolEntryContainer.Builder<?> sticks = applyExplosionDecay(leaves,
                LootItem.lootTableItem(Items.STICK)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F))))
                .when(BonusLevelTableCondition.bonusLevelFlatChance(
                        enchantments.getOrThrow(Enchantments.FORTUNE), STICK_CHANCES));
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(shearsOrSilkTouch)
                        .add(LootItem.lootTableItem(leaves)))
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .when(shearsOrSilkTouch.invert())
                        .add(sticks));
    }

    @Override
    protected void add(Block block, LootTable.Builder builder) {
        known.add(block);
        super.add(block, builder);
    }

    /** Only this mod's blocks: the loot provider must not demand a table for anyone else's. */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return known;
    }
}
