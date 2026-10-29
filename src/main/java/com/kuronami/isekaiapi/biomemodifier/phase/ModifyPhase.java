package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.MobSpawnScope;
import com.kuronami.isekaiapi.impl.MobSpawnScope.MobSpawnRule;
import com.kuronami.isekaiapi.impl.RemapEngine;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * MODIFY-phase logic for {@code isekai_api:apply_worldshape}. Two concerns:
 * <ul>
 *   <li>{@link #mobSpawnStrategy} — record the descriptor's mob spawn changes as a
 *       per-(dimension, biome) {@link MobSpawnRule} instead of rewriting the biome's
 *       {@link MobSpawnSettings.Builder}. Weights scale by the effective count factor of the
 *       per-category strategy; per-category overrides (from
 *       {@code mob_spawn_strategy_by_category}) take precedence over the global
 *       {@code mob_spawn_strategy}. The rule is enforced at the runtime spawn read points
 *       ({@link MobSpawnScope}), where the level is known, so it no longer leaks into every
 *       other dimension that shares the biome.</li>
 *   <li>{@link #atmosphereOverride} — apply any set fields of the descriptor's
 *       {@code atmosphere} to the biome's ClimateSettings and BiomeSpecialEffects.
 *       Unset fields (Optional.empty()) leave the biome's value unchanged.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class ModifyPhase {

    private ModifyPhase() {}

    public static void mobSpawnStrategy(WorldshapeDescriptor descriptor,
                                         Holder<Biome> biome) {
        // The biome is reusable across dimensions and a BiomeModifier has no dimension in
        // hand, so nothing here may touch the builder: the same plains biome is the overworld's
        // and, say, a modded skyland dimension's. Record the rule and let the runtime read
        // points apply it in the descriptor's dimension only.
        ResourceKey<Biome> biomeKey = biome.unwrapKey().orElse(null);
        if (biomeKey == null) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] mob spawn modify (dim={}): biome has no registry key, rule not recorded",
                    descriptor.dimension().location());
            return;
        }

        // Step 1: drop excluded entries entirely (consumer said "no zombies in my plains").
        Set<EntityType<?>> excludedTypes = descriptor.exclusions().mobSpawns();
        Set<ResourceKey<EntityType<?>>> removed = new java.util.HashSet<>();
        for (EntityType<?> type : excludedTypes) {
            ResourceKey<EntityType<?>> key = MobSpawnScope.entityKey(type);
            if (key != null) removed.add(key);
        }
        // Warn when an EntityType appears in both exclusions and additions — the addition
        // re-creates the entry the exclusion just removed, which is usually a config mistake
        // (the user meant either to re-tune OR to fully exclude, not both).
        for (var add : descriptor.additions().mobSpawns()) {
            if (excludedTypes.contains(add.type())) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] mob spawn additions include {} which is also in exclusions (dim={}) "
                                + "— addition will override exclusion; remove one to clarify intent",
                        EntityType.getKey(add.type()), descriptor.dimension().location());
            }
        }

        // Step 2: resolve the strategy's CountScale factor per category. A uniform factor is
        // kept as the rule's global scale; otherwise every category that differs is listed.
        Map<MobCategory, Double> factors = new EnumMap<>(MobCategory.class);
        double first = Double.NaN;
        boolean uniform = true;
        for (MobCategory category : MobCategory.values()) {
            double factor = RemapEngine.effectiveCountFactor(descriptor.resolveMobSpawnStrategy(category));
            if (Double.isNaN(first)) {
                first = factor;
            } else if (Math.abs(factor - first) >= 1e-9) {
                uniform = false;
            }
            if (Math.abs(factor - 1.0) >= 1e-6) {
                factors.put(category, factor);
            }
        }
        double globalScale = 1.0;
        if (uniform) {
            globalScale = Double.isNaN(first) ? 1.0 : first;
            factors.clear();
        }

        // Step 3: entries the consumer wants appended (custom mob, or a vanilla mob re-added
        // with a new weight/count after exclusion). Built once so the runtime appends the same
        // instances on every lookup.
        Map<MobCategory, List<MobSpawnSettings.SpawnerData>> additions = new EnumMap<>(MobCategory.class);
        for (var add : descriptor.additions().mobSpawns()) {
            additions.computeIfAbsent(add.category(), category -> new ArrayList<>())
                    .add(new MobSpawnSettings.SpawnerData(
                            add.type(), add.weight(), add.minCount(), add.maxCount()));
        }

        MobSpawnRule rule = new MobSpawnRule(removed, factors, globalScale, additions);
        MobSpawnScope.record(descriptor.dimension(), biomeKey, rule);
    }

    /**
     * {@code feature_predicates} is no longer applied here. Rewriting the biome's feature
     * entry happened inside a {@code BiomeModifier}, where no dimension is in scope, so a
     * nether descriptor's predicate also gated the feature in every other dimension sharing
     * the biome. The ADD phase now injects a guarded, predicated copy in the descriptor's
     * dimension and {@link com.kuronami.isekaiapi.impl.DimensionFeatureScope} drops the
     * original there — see {@code AddPhase} and {@code DimensionFeatureScope}.
     */

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
        var extras = atmos.effectsExtras();
        extras.grassColorModifier().ifPresent(effects::grassColorModifier);
        extras.ambientParticle().ifPresent(effects::ambientParticle);
        extras.ambientLoopSound().ifPresent(effects::ambientLoopSound);
        extras.ambientMoodSound().ifPresent(effects::ambientMoodSound);
        extras.ambientAdditionsSound().ifPresent(effects::ambientAdditionsSound);
        extras.backgroundMusic().ifPresent(effects::backgroundMusic);

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
