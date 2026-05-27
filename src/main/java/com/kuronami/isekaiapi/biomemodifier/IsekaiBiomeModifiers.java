package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers Isekai's {@link BiomeModifier} serializers. Each entry here is referenced from
 * datapack JSON via {@code "type": "isekai_api:<name>"}.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:apply_worldshape} — applies an entire {@link com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor}
 *       in one modifier entry; see {@link ApplyWorldshapeBiomeModifier} for behavior.</li>
 * </ul>
 *
 * <p>Future entries (v0.7+): {@code apply_layered_worldshape}, possibly finer-grained
 * pieces like {@code remap_ore_strategy} for consumers who want to mix worldshape
 * fragments without committing to the full descriptor.
 */
public final class IsekaiBiomeModifiers {

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, IsekaiApi.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ApplyWorldshapeBiomeModifier>> APPLY_WORLDSHAPE =
            SERIALIZERS.register("apply_worldshape", () -> ApplyWorldshapeBiomeModifier.MAP_CODEC);

    private IsekaiBiomeModifiers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        IsekaiApi.LOGGER.info("Isekai biome modifier serializers registered: apply_worldshape");
    }
}
