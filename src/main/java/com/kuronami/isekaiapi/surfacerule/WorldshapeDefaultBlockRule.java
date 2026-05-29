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
 * Datapack-driven default-block override: a {@link SurfaceRules.RuleSource} that, for
 * every column block currently equal to the world's default block (stone), returns the
 * {@code BlockOverrides.defaultBlock} entry for the current biome. Effectively re-skins
 * the bulk of the column with a chosen block per biome.
 *
 * <p>Place this LATE in the surface_rule sequence (after surface_top but before vanilla
 * rules), so the surface band stays intact and only sub-surface stone gets remapped:
 *
 * <pre>{@code
 * "surface_rule": {
 *   "type": "minecraft:sequence",
 *   "sequence": [
 *     { "type": "isekai_api:worldshape_surface_top", "dimension": "minecraft:overworld" },
 *     ... vanilla overworld surface rule ...,
 *     { "type": "isekai_api:worldshape_default_block", "dimension": "minecraft:overworld" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Use case: "in my moon dimension every stone block is moon regolith"; "in this island,
 * the stone underneath is sandstone".
 */
@ApiStatus.Internal
public record WorldshapeDefaultBlockRule(ResourceKey<Level> dimension) implements SurfaceRules.RuleSource {

    public static final MapCodec<WorldshapeDefaultBlockRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(WorldshapeDefaultBlockRule::dimension)
    ).apply(i, WorldshapeDefaultBlockRule::new));

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        var worldshape = Isekai.remap().getActiveDescriptor(dimension).orElse(null);
        if (worldshape == null) {
            warnMissingOnce(dimension);
            return NULL_RULE;
        }
        var defaultBlock = worldshape.blockOverrides().defaultBlock();
        if (defaultBlock.isEmpty()) return NULL_RULE;
        return new SurfaceRules.SurfaceRule() {
            @Override
            public @Nullable BlockState tryApply(int blockX, int blockY, int blockZ) {
                var biome = context.biome.get();
                var key = biome.unwrapKey().orElse(null);
                if (key == null) return null;
                return defaultBlock.get(key);
            }
        };
    }

    private static final SurfaceRules.SurfaceRule NULL_RULE = (x, y, z) -> null;

    private static final java.util.Set<ResourceKey<Level>> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Warn once per dimension when wired but no worldshape is registered (see surface_top twin). */
    private static void warnMissingOnce(ResourceKey<Level> dim) {
        if (WARNED.add(dim)) {
            IsekaiApi.LOGGER.warn(
                    "[Isekai] worldshape_default_block: no worldshape registered for {} — " +
                    "default_block override inactive. Check data/<ns>/isekai/worldshape/*.json " +
                    "declares this dimension.", dim.location());
        }
    }
}
