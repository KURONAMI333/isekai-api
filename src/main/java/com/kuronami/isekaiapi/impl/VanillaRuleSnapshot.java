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
 * <p>v0.2 status: scaffold + lifecycle wired (scan invocation, snapshot publication
 * to {@link IsekaiQueryImpl}, AtomicReference-based cache swap). The actual registry
 * walk that populates the lists is deferred to v0.3 — first v0.3 attempt was
 * reverted because the API path needed verification: NeoForge 1.21.1
 * {@code RegistryAccess.lookupOrThrow} returns {@code RegistryLookup<T>} not
 * {@code Registry<T>}, and {@code UniformHeight} / {@code TrapezoidHeight} field
 * access pattern (getter vs direct field) needs confirmation. Next session reattempts
 * with confirmed API references rather than guesses.
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
