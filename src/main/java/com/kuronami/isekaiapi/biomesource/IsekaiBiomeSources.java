package com.kuronami.isekaiapi.biomesource;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's {@link BiomeSource} types into {@code BuiltInRegistries.BIOME_SOURCE}.
 * Datapack consumers reference them via {@code "type": "isekai_api:<name>"} inside a
 * dimension's {@code biome_source} field.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:rule} — assigns biomes by evaluating
 *       {@link com.kuronami.isekaiapi.api.biomesource.BiomeZone} rules in order. See
 *       {@link RuleBiomeSource}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiBiomeSources {

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> CODECS =
            DeferredRegister.create(BuiltInRegistries.BIOME_SOURCE, IsekaiApi.MODID);

    public static final Supplier<MapCodec<? extends BiomeSource>> RULE =
            CODECS.register("rule", () -> RuleBiomeSource.CODEC);

    private IsekaiBiomeSources() {}

    public static void register(IEventBus modBus) {
        CODECS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] biome sources registered: rule");
    }
}
