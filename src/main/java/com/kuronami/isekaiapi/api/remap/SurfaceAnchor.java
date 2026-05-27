package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;

/**
 * Defines "what Y level counts as the surface" for surface-relative placement modifiers
 * and predicate evaluation. Neutral primitives only.
 *
 * <p>JSON form: {@code {"type": "isekai:world_surface"}} / {@code {"type": "isekai:fixed_y", "y": 64}}.
 *
 * <p>{@link Custom} is Java-only — it carries a function reference that cannot be encoded
 * to JSON, so datapack consumers must use one of the data-driven variants instead.
 */
public sealed interface SurfaceAnchor {

    String typeId();
    MapCodec<? extends SurfaceAnchor> codec();

    Codec<SurfaceAnchor> CODEC = Codec.lazyInitialized(SurfaceAnchor::buildDispatchCodec);

    /** Topmost solid block per column (vanilla heightmap WORLD_SURFACE). */
    record WorldSurface() implements SurfaceAnchor {
        public static final WorldSurface INSTANCE = new WorldSurface();
        public static final MapCodec<WorldSurface> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:world_surface"; }
        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
    }

    /** Top of the highest contiguous body of the given fluid in each column. */
    record BelowFluid(Fluid fluid) implements SurfaceAnchor {
        public static final MapCodec<BelowFluid> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(BelowFluid::fluid)
        ).apply(i, BelowFluid::new));

        @Override public String typeId() { return "isekai:below_fluid"; }
        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
    }

    /** Fixed Y level regardless of terrain. */
    record FixedY(int y) implements SurfaceAnchor {
        public static final MapCodec<FixedY> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(FixedY::y)
        ).apply(i, FixedY::new));

        @Override public String typeId() { return "isekai:fixed_y"; }
        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
    }

    /** Caller-supplied function (level, pos) -> surface Y. Datapack consumers cannot use this. */
    record Custom(SurfaceLocator fn) implements SurfaceAnchor {
        @Override public String typeId() { return "isekai:custom"; }
        @Override public MapCodec<? extends SurfaceAnchor> codec() {
            throw new UnsupportedOperationException(
                    "SurfaceAnchor.Custom is Java-only and cannot be serialized to JSON");
        }
    }

    @FunctionalInterface
    interface SurfaceLocator extends ToIntBiFunction<LevelReader, BlockPos> {}

    private static Codec<SurfaceAnchor> buildDispatchCodec() {
        Map<String, MapCodec<? extends SurfaceAnchor>> registry = new LinkedHashMap<>();
        registry.put("isekai:world_surface", WorldSurface.MAP_CODEC);
        registry.put("isekai:below_fluid",   BelowFluid.MAP_CODEC);
        registry.put("isekai:fixed_y",       FixedY.MAP_CODEC);
        // "isekai:custom" intentionally omitted — Java-only, not encodable.
        Map<String, MapCodec<? extends SurfaceAnchor>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                SurfaceAnchor::typeId,
                typeId -> {
                    MapCodec<? extends SurfaceAnchor> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown SurfaceAnchor type: '" + typeId
                                        + "'. Known types: " + frozen.keySet()
                                        + " (note: 'isekai:custom' is Java-only and cannot be used in JSON)");
                    }
                    return mc;
                });
    }
}
