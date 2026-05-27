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
 * {@code ServerAboutToStartEvent}. Backs all {@link com.kuronami.isekaiapi.api.query.IsekaiQuery}
 * methods in O(1).
 *
 * <p>v0.2 status: scaffold + lifecycle wired. The actual registry-walk logic that
 * populates the lists from {@code BuiltInRegistries.PLACED_FEATURE} etc. lands in
 * v0.3 — the missing piece is the per-feature {@code VerticalRange} extraction
 * (parsing {@code HeightRangePlacement} variants out of each {@code PlacedFeature}'s
 * modifier list) which needs careful handling of all the vanilla
 * {@code HeightProvider} subtypes.
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
     * v0.2 skeleton — returns an EMPTY snapshot but exercises the lifecycle path
     * (event firing, log line, downstream cache assignment). Replace the empty
     * return with a full registry walk in v0.3.
     */
    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info(
                "[Isekai v0.2 skeleton] VanillaRuleSnapshot.scan: registry walk deferred to v0.3 "
                        + "(server={}, registries available={})",
                server.getMotd(),
                server.registryAccess() != null);
        // TODO v0.3: walk BuiltInRegistries.PLACED_FEATURE -> extract VerticalRange via
        // analysis of each feature's HeightRangePlacement (uniform / trapezoid / triangle / etc.)
        // TODO v0.3: walk BuiltInRegistries.STRUCTURE -> StructurePlacement metadata
        // TODO v0.3: walk BuiltInRegistries.BIOME -> mob spawn settings per MobCategory
        // TODO v0.3: cache modded ResourceKeys alongside vanilla
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
