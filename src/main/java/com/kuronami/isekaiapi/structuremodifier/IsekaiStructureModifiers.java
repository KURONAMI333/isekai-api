package com.kuronami.isekaiapi.structuremodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's {@link StructureModifier} serializers. Datapack consumers reference
 * these via {@code "type": "isekai_api:<name>"} inside
 * {@code data/<ns>/neoforge/structure_modifier/*.json}.
 *
 * <p>NeoForge keeps biome modifiers and structure modifiers in two parallel registries —
 * the latter targets {@code StructureSettings} (biomes / step / spawn overrides) rather
 * than {@code BiomeGenerationSettings}.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:apply_worldshape_structures} — clears
 *       {@code StructureSettings.biomes()} for any structure in
 *       {@code descriptor.exclusions().structures()}, making it unspawnable. See
 *       {@link ApplyWorldshapeStructureModifier} for behavior.</li>
 * </ul>
 *
 * <p>Biome-side serializers live in {@link com.kuronami.isekaiapi.biomemodifier.IsekaiBiomeModifiers}.
 */
@ApiStatus.Internal
public final class IsekaiStructureModifiers {

    public static final DeferredRegister<MapCodec<? extends StructureModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.STRUCTURE_MODIFIER_SERIALIZERS, IsekaiApi.MODID);

    public static final DeferredHolder<MapCodec<? extends StructureModifier>, MapCodec<ApplyWorldshapeStructureModifier>> APPLY_WORLDSHAPE_STRUCTURES =
            SERIALIZERS.register("apply_worldshape_structures", () -> ApplyWorldshapeStructureModifier.MAP_CODEC);

    public static final DeferredHolder<MapCodec<? extends StructureModifier>, MapCodec<ApplyWorldshapeStructuresRefStructureModifier>> APPLY_WORLDSHAPE_STRUCTURES_REF =
            SERIALIZERS.register("apply_worldshape_structures_ref", () -> ApplyWorldshapeStructuresRefStructureModifier.MAP_CODEC);

    private IsekaiStructureModifiers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] structure modifier serializers registered: apply_worldshape_structures, apply_worldshape_structures_ref");
    }
}
