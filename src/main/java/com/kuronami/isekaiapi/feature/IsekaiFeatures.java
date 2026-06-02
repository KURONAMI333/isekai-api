package com.kuronami.isekaiapi.feature;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's neutral {@link Feature} types into the vanilla
 * {@code BuiltInRegistries.FEATURE} registry. Datapack consumers invoke these via
 * {@code "type": "isekai_api:<name>"} inside a configured_feature JSON.
 *
 * <p>Each feature is neutral — geometry / spatial pattern only, no biome / species / theme
 * baked in. Block / fluid choices are supplied by the consumer through codec fields.
 *
 * <p>Registered types:
 * <ul>
 *   <li>{@code isekai_api:cluster} — connected blob of N blocks (geometric primitive for
 *       moss patches, dirt veins, fungus spreads, etc.).</li>
 *   <li>{@code isekai_api:pool} — carved disc of fluid with a {@code rim_block} edge, sidesteps
 *       the {@code waterlogged_vegetation_patch} grass→dirt trap.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(BuiltInRegistries.FEATURE, IsekaiApi.MODID);

    public static final Supplier<ClusterFeature> CLUSTER =
            FEATURES.register("cluster", () -> new ClusterFeature());
    public static final Supplier<PoolFeature> POOL =
            FEATURES.register("pool", () -> new PoolFeature());

    private IsekaiFeatures() {}

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] features registered: cluster, pool");
    }
}
