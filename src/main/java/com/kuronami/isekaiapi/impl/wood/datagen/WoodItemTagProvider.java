package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.ApiStatus;

/**
 * Item tags for every wood entry, mirroring vanilla oak: the item halves of the block families,
 * plus the item-only tags for signs, boats and chest boats.
 *
 * <p>The {@code <namespace>:<name>_logs} tag is copied from its block tag, then added to
 * {@code minecraft:logs_that_burn} so {@code minecraft:logs} picks it up transitively.
 */
@ApiStatus.Internal
public final class WoodItemTagProvider extends ItemTagsProvider {

    private final List<WoodEntry> entries;

    public WoodItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                               CompletableFuture<TagsProvider.TagLookup<Block>> blockTags, String modId,
                               ExistingFileHelper existingFiles, List<WoodEntry> entries) {
        super(output, lookup, blockTags, modId, existingFiles);
        this.entries = entries;
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        for (WoodEntry entry : entries) {
            itemTags(new WoodGen(entry, existingFileHelper));
        }
    }

    private void itemTags(WoodGen gen) {
        if (gen.hasLogs()) {
            ResourceLocation logs = ResourceLocation.fromNamespaceAndPath(gen.namespace(),
                    gen.name() + "_logs");
            // Copied before it is referenced: a reference to a tag this provider has not written
            // yet fails the reference check.
            copy(BlockTags.create(logs), ItemTags.create(logs));
            tag(ItemTags.LOGS_THAT_BURN).addTag(ItemTags.create(logs));
        }

        gen.items(WoodPart.PLANKS).forEach(item -> tag(ItemTags.PLANKS).add(item));
        gen.items(WoodPart.STAIRS).forEach(item -> tag(ItemTags.WOODEN_STAIRS).add(item));
        gen.items(WoodPart.SLAB).forEach(item -> tag(ItemTags.WOODEN_SLABS).add(item));
        gen.items(WoodPart.FENCE).forEach(item -> tag(ItemTags.WOODEN_FENCES).add(item));
        gen.items(WoodPart.FENCE_GATE).forEach(item -> tag(ItemTags.FENCE_GATES).add(item));
        gen.items(WoodPart.BUTTON).forEach(item -> tag(ItemTags.WOODEN_BUTTONS).add(item));
        gen.items(WoodPart.PRESSURE_PLATE).forEach(item -> tag(ItemTags.WOODEN_PRESSURE_PLATES).add(item));
        gen.items(WoodPart.DOOR).forEach(item -> tag(ItemTags.WOODEN_DOORS).add(item));
        gen.items(WoodPart.TRAPDOOR).forEach(item -> tag(ItemTags.WOODEN_TRAPDOORS).add(item));
        gen.items(WoodPart.SIGN).forEach(item -> tag(ItemTags.SIGNS).add(item));
        gen.items(WoodPart.HANGING_SIGN).forEach(item -> tag(ItemTags.HANGING_SIGNS).add(item));
        gen.items(WoodPart.LEAVES).forEach(item -> tag(ItemTags.LEAVES).add(item));
        gen.items(WoodPart.SAPLING).forEach(item -> tag(ItemTags.SAPLINGS).add(item));
        gen.items(WoodPart.BOAT).forEach(item -> tag(ItemTags.BOATS).add(item));
        gen.items(WoodPart.CHEST_BOAT).forEach(item -> tag(ItemTags.CHEST_BOATS).add(item));
    }
}
