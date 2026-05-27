package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines "what Y level counts as the surface" for surface-relative placement modifiers
 * and predicate evaluation. Neutral primitives only — every variant is JSON-encodable so
 * datapack consumers and Java consumers share a single dispatch path.
 *
 * <p>JSON form: {@code {"type": "isekai:world_surface"}} / {@code {"type": "isekai:fixed_y", "y": 64}}.
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

    private static Codec<SurfaceAnchor> buildDispatchCodec() {
        Map<String, MapCodec<? extends SurfaceAnchor>> registry = new LinkedHashMap<>();
        registry.put("isekai:world_surface", WorldSurface.MAP_CODEC);
        registry.put("isekai:below_fluid",   BelowFluid.MAP_CODEC);
        registry.put("isekai:fixed_y",       FixedY.MAP_CODEC);
        Map<String, MapCodec<? extends SurfaceAnchor>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                SurfaceAnchor::typeId,
                typeId -> {
                    MapCodec<? extends SurfaceAnchor> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown SurfaceAnchor type: '" + typeId
                                        + "'. Known types: " + frozen.keySet());
                    }
                    return mc;
                });
    }
}
