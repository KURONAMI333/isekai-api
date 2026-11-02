package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.ApiStatus;

/**
 * Block tags for every wood entry, with the membership vanilla oak has: planks, the
 * {@code wooden_*} families, the sign and flower-pot tags, leaves, saplings and the log chain.
 *
 * <p>Each set owns a {@code <namespace>:<name>_logs} tag, the way vanilla has
 * {@code minecraft:oak_logs}; that tag is what {@code logs_that_burn} (and through it
 * {@code minecraft:logs}) references, so the set's logs burn and are recognised as logs without
 * touching {@code minecraft:logs} directly.
 *
 * <p>{@code minecraft:overworld_natural_logs} is not filled. That tag means "generates in the
 * overworld" — it drives goat-horn ramming — and the data generator cannot know whether the
 * consumer places this species in worldgen.
 */
@ApiStatus.Internal
public final class WoodBlockTagProvider extends BlockTagsProvider {

    private final List<WoodEntry> entries;

    public WoodBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                String modId, ExistingFileHelper existingFiles, List<WoodEntry> entries) {
        super(output, lookup, modId, existingFiles);
        this.entries = entries;
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (WoodEntry entry : entries) {
            blockTags(new WoodGen(entry, existingFileHelper));
        }
    }

    private void blockTags(WoodGen gen) {
        if (gen.hasLogs()) {
            // Created first: logs_that_burn references it, and a reference to a tag that this
            // generator has not written yet fails the reference check.
            TagKey<Block> logs = BlockTags.create(
                    ResourceLocation.fromNamespaceAndPath(gen.namespace(), gen.name() + "_logs"));
            var appender = tag(logs);
            gen.blocks(WoodPart.LOG, WoodPart.WOOD, WoodPart.STRIPPED_LOG, WoodPart.STRIPPED_WOOD)
                    .forEach(appender::add);
            // logs_that_burn is what vanilla consults when a fire block looks for fuel; a set that
            // is not wood declares itself in the plain logs tag instead.
            tag(gen.set().isFlammable() ? BlockTags.LOGS_THAT_BURN : BlockTags.LOGS).addTag(logs);
        }

        gen.blocks(WoodPart.PLANKS).forEach(block -> tag(BlockTags.PLANKS).add(block));
        gen.blocks(WoodPart.STAIRS).forEach(block -> tag(BlockTags.WOODEN_STAIRS).add(block));
        gen.blocks(WoodPart.SLAB).forEach(block -> tag(BlockTags.WOODEN_SLABS).add(block));
        gen.blocks(WoodPart.FENCE).forEach(block -> tag(BlockTags.WOODEN_FENCES).add(block));
        gen.blocks(WoodPart.FENCE_GATE).forEach(block -> tag(BlockTags.FENCE_GATES).add(block));
        gen.blocks(WoodPart.BUTTON).forEach(block -> tag(BlockTags.WOODEN_BUTTONS).add(block));
        gen.blocks(WoodPart.PRESSURE_PLATE)
                .forEach(block -> tag(BlockTags.WOODEN_PRESSURE_PLATES).add(block));
        gen.blocks(WoodPart.DOOR).forEach(block -> tag(BlockTags.WOODEN_DOORS).add(block));
        gen.blocks(WoodPart.TRAPDOOR).forEach(block -> tag(BlockTags.WOODEN_TRAPDOORS).add(block));
        gen.blocks(WoodPart.SIGN).forEach(block -> tag(BlockTags.STANDING_SIGNS).add(block));
        gen.wallSign().ifPresent(block -> tag(BlockTags.WALL_SIGNS).add(block));
        gen.blocks(WoodPart.HANGING_SIGN).forEach(block -> tag(BlockTags.CEILING_HANGING_SIGNS).add(block));
        gen.wallHangingSign().ifPresent(block -> tag(BlockTags.WALL_HANGING_SIGNS).add(block));
        gen.blocks(WoodPart.LEAVES).forEach(block -> {
            tag(BlockTags.LEAVES).add(block);
            tag(BlockTags.MINEABLE_WITH_HOE).add(block);
        });
        gen.blocks(WoodPart.SAPLING).forEach(block -> tag(BlockTags.SAPLINGS).add(block));
        gen.blocks(WoodPart.POTTED_SAPLING).forEach(block -> tag(BlockTags.FLOWER_POTS).add(block));
    }
}
