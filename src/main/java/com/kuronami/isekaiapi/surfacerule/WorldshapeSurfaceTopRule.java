package com.kuronami.isekaiapi.surfacerule;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Datapack-driven surface override: a {@link SurfaceRules.RuleSource} that, when consulted
 * for a position, returns the {@code BlockOverrides.surfaceTop} entry for the current
 * biome (if any) or {@code null} (i.e. let the next rule in the sequence handle it).
 *
 * <p>Consumer wires this into the dimension's noise_settings surface_rule by prepending
 * it to the vanilla overworld rule:
 *
 * <pre>{@code
 * "surface_rule": {
 *   "type": "minecraft:sequence",
 *   "sequence": [
 *     { "type": "isekai_api:worldshape_surface_top", "dimension": "minecraft:overworld" },
 *     ... original overworld rule ...
 *   ]
 * }
 * }</pre>
 *
 * <p>Per-biome lookup happens once per (x, z, surface block) and is O(1) — the worldshape's
 * {@code blockOverrides.surfaceTop} is a {@code Map}. Returning {@code null} short-circuits
 * the sequence step and lets vanilla handle the column.
 *
 * <p>The hardcoded {@code dimension} on the rule lets multiple dimensions reuse the same
 * source type pointing at different worldshapes — there's no ambient "current dimension"
 * available inside a SurfaceRule.
 */
@ApiStatus.Internal
public record WorldshapeSurfaceTopRule(ResourceKey<Level> dimension) implements SurfaceRules.RuleSource {

    public static final MapCodec<WorldshapeSurfaceTopRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(WorldshapeSurfaceTopRule::dimension)
    ).apply(i, WorldshapeSurfaceTopRule::new));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        // Per-block layer resolution. We can't cache the descriptor outside the lambda because
        // a layered dimension has different blockOverrides per Y band — the lookup happens at
        // tryApply time when we know blockY. For single-descriptor dims the cost is one extra
        // map lookup; for layered dims it's the only correct path. Warns once per dim if
        // nothing is declared at all (regardless of Y).
        if (Isekai.remap().getActiveDescriptor(dimension).isEmpty()
                && Isekai.remap().getActiveLayers(dimension).isEmpty()) {
            warnMissingOnce(dimension);
            return NULL_RULE;
        }
        return new SurfaceRules.SurfaceRule() {
            @Override
            public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
                var biome = context.biome.get();
                var key = biome.unwrapKey().orElse(null);
                if (key == null) return null;
                var worldshape = Isekai.remap().getDescriptorAt(dimension, blockY).orElse(null);
                if (worldshape == null) return null;
                var surfaceTop = worldshape.blockOverrides().surfaceTop();
                return surfaceTop.get(key);
            }
        };
    }

    /**
     * Cached SurfaceRule that always returns null — used when there's no worldshape or
     * no overrides, so we don't allocate a new lambda per (x, z) column.
     */
    private static final SurfaceRules.SurfaceRule NULL_RULE = (x, y, z) -> null;

    private static final java.util.Set<ResourceKey<Level>> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Warn exactly once per dimension when this rule is wired into a surface_rule but no
     * worldshape is registered for the named dimension — otherwise the override silently
     * does nothing and the author has no signal whether the rule, the worldshape JSON, or
     * the dimension key is at fault.
     */
    private static void warnMissingOnce(ResourceKey<Level> dim) {
        if (WARNED.add(dim)) {
            IsekaiApi.LOGGER.warn(
                    "[Isekai] worldshape_surface_top: no worldshape registered for {} — " +
                    "surface_top override inactive. Check data/<ns>/isekai/worldshape/*.json " +
                    "declares this dimension.", dim.location());
        }
    }
}
