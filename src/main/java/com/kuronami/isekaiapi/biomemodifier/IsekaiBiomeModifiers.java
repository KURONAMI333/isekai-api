package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers Isekai's {@link BiomeModifier} serializers. Datapack consumers reference these
 * via {@code "type": "isekai_api:<name>"} inside
 * {@code data/<ns>/neoforge/biome_modifier/*.json}.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:apply_worldshape} — applies an entire
 *       {@link com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor} as a single modifier
 *       entry. Handles REMOVE / ADD / MODIFY phases (features, carvers, mob spawns,
 *       atmosphere). See {@link ApplyWorldshapeBiomeModifier} for behavior.</li>
 * </ul>
 *
 * <p>Structure-side serializers live in {@link com.kuronami.isekaiapi.structuremodifier.IsekaiStructureModifiers}.
 */
public final class IsekaiBiomeModifiers {

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, IsekaiApi.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ApplyWorldshapeBiomeModifier>> APPLY_WORLDSHAPE =
            SERIALIZERS.register("apply_worldshape", () -> ApplyWorldshapeBiomeModifier.MAP_CODEC);

    private IsekaiBiomeModifiers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] biome modifier serializers registered: apply_worldshape");
    }
}
