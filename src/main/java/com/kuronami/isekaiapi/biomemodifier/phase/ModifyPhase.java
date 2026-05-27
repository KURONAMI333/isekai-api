package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.RemapEngine;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * MODIFY-phase logic for {@code isekai_api:apply_worldshape}. Two concerns:
 * <ul>
 *   <li>{@link #mobSpawnStrategy} — scale every mob spawn entry's weight by the
 *       effective count factor of the per-category strategy. Per-category overrides
 *       (from {@code mob_spawn_strategy_by_category}) take precedence over the global
 *       {@code mob_spawn_strategy}.</li>
 *   <li>{@link #atmosphereOverride} — apply any set fields of the descriptor's
 *       {@code atmosphere} to the biome's ClimateSettings and BiomeSpecialEffects.
 *       Unset fields (Optional.empty()) leave the biome's value unchanged.</li>
 * </ul>
 */
public final class ModifyPhase {

    private ModifyPhase() {}

    public static void mobSpawnStrategy(WorldshapeDescriptor descriptor,
                                         ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var spawnBuilder = builder.getMobSpawnSettings();
        int rebuilt = 0;
        for (MobCategory category : MobCategory.values()) {
            double factor = RemapEngine.effectiveCountFactor(descriptor.resolveMobSpawnStrategy(category));
            if (Math.abs(factor - 1.0) < 1e-6) continue;
            var list = spawnBuilder.getSpawner(category);
            for (int i = 0; i < list.size(); i++) {
                var sd = list.get(i);
                int oldWeight = sd.getWeight().asInt();
                int newWeight = Math.max(0, (int) Math.round(oldWeight * factor));
                if (newWeight == oldWeight) continue;
                list.set(i, new MobSpawnSettings.SpawnerData(
                        sd.type, newWeight, sd.minCount, sd.maxCount));
                rebuilt++;
            }
        }
        if (rebuilt > 0) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] scaled {} mob spawn weights (dim={}, per-category overrides={})",
                    rebuilt, descriptor.dimension().location(),
                    descriptor.mobSpawnStrategyByCategory().keySet());
        }
    }

    public static void atmosphereOverride(WorldshapeDescriptor descriptor,
                                           ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var atmos = descriptor.atmosphere();
        if (atmos.isNoOp()) return;
        var climate = builder.getClimateSettings();
        atmos.hasPrecipitation().ifPresent(climate::setHasPrecipitation);
        atmos.temperature().ifPresent(climate::setTemperature);
        atmos.downfall().ifPresent(climate::setDownfall);

        var effects = builder.getSpecialEffects();
        atmos.skyColor().ifPresent(effects::skyColor);
        atmos.fogColor().ifPresent(effects::fogColor);
        atmos.waterColor().ifPresent(effects::waterColor);
        atmos.waterFogColor().ifPresent(effects::waterFogColor);
        atmos.foliageColor().ifPresent(effects::foliageColorOverride);
        atmos.grassColor().ifPresent(effects::grassColorOverride);

        IsekaiApi.LOGGER.debug("[Isekai] applied atmosphere override (dim={})",
                descriptor.dimension().location());
    }
}
