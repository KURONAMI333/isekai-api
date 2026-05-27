package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.MobSpawnInfo;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.StructurePlacementInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;

import java.util.List;
import java.util.Map;

/**
 * Immutable cached view of vanilla + modded worldgen rules taken at
 * {@code ServerAboutToStartEvent}. Backs all
 * {@link com.kuronami.isekaiapi.api.query.IsekaiQuery} methods in O(1).
 *
 * <p>v0.2 status: scaffold + lifecycle wired. Actual registry-walk implementation
 * deferred to v0.3. Two v0.3 attempts have failed and been reverted; the next attempt
 * needs API verification via the NeoForge-extracted Mojang source (under
 * {@code .gradle/caches} after a successful build) rather than community-mod
 * reference searches, because mapping differences (Parchment vs Mojang vs Yarn)
 * cause false positives in {@code gh search code} results.
 *
 * <p>Confirmed-incorrect guesses so far (both via build error):
 * <ul>
 *   <li>v0.3 attempt 1: assumed {@code RegistryAccess.lookupOrThrow} returns
 *       {@code Registry<T>}. It returns {@code HolderLookup.RegistryLookup<T>}.</li>
 *   <li>v0.3 attempt 2: assumed {@code HeightRangePlacement.height()} is the accessor.
 *       That method does not exist in NeoForge 1.21.1's Mojang-mapping; the field is
 *       likely either a direct {@code height} field reference or a different getter
 *       name (e.g. {@code getHeight()}). Resolution requires reading the actual
 *       extracted source from {@code .gradle/caches/.../neoforge/.../sources/}.</li>
 * </ul>
 */
public final class VanillaRuleSnapshot {

    public static final VanillaRuleSnapshot EMPTY =
            new VanillaRuleSnapshot(List.of(), List.of(), Map.of());

    private final List<PlacedFeatureInfo> ores;
    private final List<StructurePlacementInfo> structures;
    private final Map<MobCategory, List<MobSpawnInfo>> mobsByCategory;

    public VanillaRuleSnapshot(List<PlacedFeatureInfo> ores,
                                List<StructurePlacementInfo> structures,
                                Map<MobCategory, List<MobSpawnInfo>> mobsByCategory) {
        this.ores = List.copyOf(ores);
        this.structures = List.copyOf(structures);
        this.mobsByCategory = Map.copyOf(mobsByCategory);
    }

    /**
     * v0.2 skeleton — returns {@link #EMPTY} but exercises the lifecycle path so
     * {@code IsekaiLifecycle.onServerAboutToStart} can call this without error.
     * v0.3 will replace this body with a real
     * {@code BuiltInRegistries.PLACED_FEATURE} walk.
     */
    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai v0.2 skeleton] VanillaRuleSnapshot.scan: registry walk "
                + "implementation deferred to v0.3 (server motd={}, registries available={})",
                server.getMotd(),
                server.registryAccess() != null);
        return EMPTY;
    }

    public List<PlacedFeatureInfo> ores() { return ores; }
    public List<StructurePlacementInfo> structures() { return structures; }

    public List<MobSpawnInfo> mobsForCategory(MobCategory category) {
        return mobsByCategory.getOrDefault(category, List.of());
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobsByCategory.isEmpty();
    }
}
