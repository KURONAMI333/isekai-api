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
import net.minecraft.world.level.levelgen.placement.CaveSurface;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Datapack-driven surface override: a {@link SurfaceRules.RuleSource} that replaces the top
 * surface block of matched biomes with the {@code block_overrides.surface_top} entry from the
 * active worldshape (or emits {@code null} so the next rule in the sequence handles the column).
 *
 * <p>Self-gating. The rule affects the <em>surface layer only</em> — the single topmost floor
 * block of each column. It carries its own {@code stone_depth(offset 0, floor, add_surface_depth
 * false)} gate internally (vanilla's {@code ON_FLOOR} condition), so a consumer prepends it bare:
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
 * <p>No external {@code stone_depth} wrapper is needed or wanted; wrapping it in one anyway is
 * harmless (the gate double-applies to the same result).
 *
 * <p>Implementation. Like {@link StrataRule}, the gate is BUILT from vanilla {@link SurfaceRules}
 * primitives ({@code ifTrue(ON_FLOOR, lookup)}) so it inherits vanilla's exact surface-depth
 * handling and adds no access-transformer entries. The per-biome, per-Y lookup runs only when the
 * gate passes.
 *
 * <p>The hardcoded {@code dimension} on the rule lets multiple dimensions reuse the same source
 * type pointing at different worldshapes — there's no ambient "current dimension" available inside
 * a SurfaceRule.
 */
@ApiStatus.Internal
public record WorldshapeSurfaceTopRule(ResourceKey<Level> dimension, SurfaceRules.RuleSource gated)
        implements SurfaceRules.RuleSource {

    public static final MapCodec<WorldshapeSurfaceTopRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(WorldshapeSurfaceTopRule::dimension)
    ).apply(i, WorldshapeSurfaceTopRule::create));

    /**
     * Builds the surface-top rule for a dimension, wrapping the per-biome lookup in vanilla's
     * {@code ON_FLOOR} stone-depth gate so it touches only the topmost floor block. Called by the
     * codec on decode; the built {@code gated} tree is not itself serialized (only {@code dimension}
     * round-trips). Pure construction — no registry or worldshape access happens here.
     */
    public static WorldshapeSurfaceTopRule create(ResourceKey<Level> dimension) {
        SurfaceRules.RuleSource gated = SurfaceRules.ifTrue(
                SurfaceRules.stoneDepthCheck(0, false, CaveSurface.FLOOR),
                new SurfaceTopLookup(dimension));
        return new WorldshapeSurfaceTopRule(dimension, gated);
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        // Warn once if the rule is wired but nothing is declared for this dimension, then short out —
        // otherwise the override silently does nothing. When a worldshape IS active, delegate to the
        // gated vanilla tree, which applies the lookup only at the top floor block.
        if (Isekai.remap().getActiveDescriptor(dimension).isEmpty()
                && Isekai.remap().getActiveLayers(dimension).isEmpty()) {
            warnMissingOnce(dimension);
            return NULL_RULE;
        }
        return gated.apply(context);
    }

    /**
     * The inner per-biome surface-top lookup, run only when the enclosing {@code ON_FLOOR} gate
     * passes. Per-block layer resolution: a layered dimension has different blockOverrides per Y
     * band, so the descriptor lookup happens at tryApply time when blockY is known.
     */
    private record SurfaceTopLookup(ResourceKey<Level> dimension) implements SurfaceRules.RuleSource {

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            return new SurfaceRules.SurfaceRule() {
                @Override
                public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
                    var biome = context.biome.get();
                    var key = biome.unwrapKey().orElse(null);
                    if (key == null) return null;
                    var worldshape = Isekai.remap()
                            .getDescriptorAt(dimension, blockX, blockY, blockZ).orElse(null);
                    if (worldshape == null) return null;
                    return worldshape.blockOverrides().surfaceTop().get(key);
                }
            };
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            // Never serialized: only the enclosing WorldshapeSurfaceTopRule round-trips (its CODEC
            // carries just `dimension`); this inner rule is rebuilt in create(). Serialize the
            // enclosing worldshape_surface_top instead.
            throw new UnsupportedOperationException(
                    "internal surface-top lookup rule is not serializable");
        }
    }

    /**
     * Cached SurfaceRule that always returns null — used when there's no worldshape, so we don't
     * allocate a new lambda per (x, z) column.
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
