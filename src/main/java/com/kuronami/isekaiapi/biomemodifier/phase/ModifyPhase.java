package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.RemapEngine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.Set;

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

        // Step 1: drop excluded entries entirely (consumer said "no zombies in my plains").
        Set<EntityType<?>> excludedTypes = descriptor.exclusions().mobSpawns();
        int removed = 0;
        if (!excludedTypes.isEmpty()) {
            for (MobCategory category : MobCategory.values()) {
                var list = spawnBuilder.getSpawner(category);
                int before = list.size();
                list.removeIf(sd -> excludedTypes.contains(sd.type));
                removed += before - list.size();
            }
        }

        // Step 2: scale remaining entry weights per the strategy's CountScale factor.
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

        // Step 3: inject any additional entries the consumer wants (custom mob, or
        // re-adding vanilla mob with new weight/count after exclusion).
        int added = 0;
        for (var add : descriptor.additions().mobSpawns()) {
            spawnBuilder.getSpawner(add.category()).add(new MobSpawnSettings.SpawnerData(
                    add.type(), add.weight(), add.minCount(), add.maxCount()));
            added++;
        }

        if (removed > 0 || rebuilt > 0 || added > 0) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] mob spawn modify (dim={}): removed={}, rescaled={}, added={}",
                    descriptor.dimension().location(), removed, rebuilt, added);
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

        // creature_generation_probability lives on MobSpawnSettings, not BiomeSpecialEffects;
        // grouped here with the other atmospheric tunables since it's a per-biome scalar
        // governing how often passive-creature chunk-population runs.
        var spawnBuilder = builder.getMobSpawnSettings();
        atmos.creatureGenerationProbability().ifPresent(spawnBuilder::creatureGenerationProbability);

        // mob_spawn_costs: per-entity (energyBudget, charge) controlling spawn-cap balance.
        // Higher charge = entity counts heavier against the cap.
        atmos.mobSpawnCosts().forEach((type, cost) ->
                spawnBuilder.addMobCharge(type, cost.charge(), cost.energyBudget()));

        IsekaiApi.LOGGER.debug("[Isekai] applied atmosphere override (dim={})",
                descriptor.dimension().location());
    }
}
