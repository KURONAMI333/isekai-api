package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * Crafting recipes and their recipe advancements, matching what vanilla generates for oak: the
 * same shapes, counts, book categories, recipe folders, recipe groups ({@code wooden_stairs},
 * {@code wooden_sign}, …) and unlock criteria.
 *
 * <p>Every recipe is derived from the planks of the set, except the log recipes. A set that has no
 * planks (or skipped them) therefore gets the bark recipes and nothing else: a recipe whose
 * ingredient does not exist cannot be crafted.
 */
@ApiStatus.Internal
public final class WoodRecipeProvider extends RecipeProvider {

    private final List<WoodEntry> entries;

    public WoodRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries,
                              List<WoodEntry> entries) {
        super(output, registries);
        this.entries = entries;
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        for (WoodEntry entry : entries) {
            generate(new WoodGen(entry, null), output);
        }
    }

    private void generate(WoodGen gen, RecipeOutput output) {
        Block planks = gen.block(WoodPart.PLANKS).orElse(null);
        TagKey<Item> logs = ItemTags.create(
                ResourceLocation.fromNamespaceAndPath(gen.namespace(), gen.name() + "_logs"));

        if (planks != null && gen.hasLogs()) {
            planksFromLogs(output, planks, logs, 4);
        }
        gen.block(WoodPart.WOOD).ifPresent(wood ->
                gen.block(WoodPart.LOG).ifPresent(log -> woodFromLogs(output, wood, log)));
        gen.block(WoodPart.STRIPPED_WOOD).ifPresent(wood ->
                gen.block(WoodPart.STRIPPED_LOG).ifPresent(log -> woodFromLogs(output, wood, log)));

        if (planks == null) {
            return;
        }
        Ingredient material = Ingredient.of(planks);
        gen.block(WoodPart.STAIRS).ifPresent(block -> stairBuilder(block, material)
                .group("wooden_stairs").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.SLAB).ifPresent(block -> slabBuilder(RecipeCategory.BUILDING_BLOCKS, block, material)
                .group("wooden_slab").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.FENCE).ifPresent(block -> fenceBuilder(block, material)
                .group("wooden_fence").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.FENCE_GATE).ifPresent(block -> fenceGateBuilder(block, material)
                .group("wooden_fence_gate").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.DOOR).ifPresent(block -> doorBuilder(block, material)
                .group("wooden_door").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.TRAPDOOR).ifPresent(block -> trapdoorBuilder(block, material)
                .group("wooden_trapdoor").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.BUTTON).ifPresent(block -> buttonBuilder(block, material)
                .group("wooden_button").unlockedBy("has_planks", has(planks)).save(output));
        gen.block(WoodPart.PRESSURE_PLATE).ifPresent(block ->
                pressurePlateBuilder(RecipeCategory.REDSTONE, block, material)
                        .group("wooden_pressure_plate").unlockedBy("has_planks", has(planks)).save(output));

        gen.item(WoodPart.SIGN).ifPresent(sign -> signBuilder(sign, material)
                .group("wooden_sign").unlockedBy("has_planks", has(planks)).save(output));
        if (gen.item(WoodPart.HANGING_SIGN).isPresent()) {
            gen.block(WoodPart.STRIPPED_LOG).ifPresent(stripped ->
                    hangingSign(output, gen.item(WoodPart.HANGING_SIGN).orElseThrow(), stripped));
        }
        if (gen.item(WoodPart.BOAT).isPresent()) {
            Item boat = gen.item(WoodPart.BOAT).orElseThrow();
            woodenBoat(output, boat, planks);
            gen.item(WoodPart.CHEST_BOAT).ifPresent(chestBoat -> chestBoat(output, chestBoat, boat));
        }
    }
}
