package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import org.jetbrains.annotations.ApiStatus;

/**
 * The compostable data map entry of each set's leaves and sapling, at the set's compost chance
 * (vanilla oak's {@code 0.3} unless overridden).
 *
 * <p>This is the data-driven half of compostability; {@code WoodSetHooks} keeps a runtime hook for
 * sets that do not run datagen, and that hook does not overwrite what a datapack already decided.
 */
@ApiStatus.Internal
public final class WoodCompostProvider extends DataMapProvider {

    private final List<WoodEntry> entries;

    public WoodCompostProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                               List<WoodEntry> entries) {
        super(output, lookup);
        this.entries = entries;
    }

    @Override
    protected void gather(HolderLookup.Provider provider) {
        for (WoodEntry entry : entries) {
            WoodGen gen = new WoodGen(entry, null);
            Compostable compostable = new Compostable(gen.compostChance());
            add(WoodPart.LEAVES, gen, compostable);
            add(WoodPart.SAPLING, gen, compostable);
        }
    }

    private void add(WoodPart part, WoodGen gen, Compostable compostable) {
        gen.item(part).ifPresent(item -> {
            ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
            // Never replace: a consumer's own data map entry wins over the generated default.
            builder(NeoForgeDataMaps.COMPOSTABLES).add(key, compostable, false);
        });
    }
}
