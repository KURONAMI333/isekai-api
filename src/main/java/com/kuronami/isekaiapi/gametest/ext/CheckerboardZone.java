package com.kuronami.isekaiapi.gametest.ext;

import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.QuartPos;

/**
 * A third-party {@link BiomeZone} variant that lives ONLY in the (jar-excluded) gametest tree —
 * the shipped library has no knowledge of it. It is registered from {@link IsekaiTestExtensions}
 * under {@code isekai_api_test:checkerboard}, exercising the SPI: a mod that isn't Isekai adds a
 * new variant purely by registering a codec against {@code IsekaiRegistries.BIOME_ZONE_TYPE}.
 *
 * <p>Matches an {@code size}×{@code size} block checkerboard in the XZ plane.
 */
public record CheckerboardZone(int size) implements BiomeZone {

    public static final MapCodec<CheckerboardZone> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.intRange(1, 4096).fieldOf("size").forGetter(CheckerboardZone::size)
    ).apply(i, CheckerboardZone::new));

    @Override
    public boolean test(int qx, int qy, int qz) {
        int cx = Math.floorDiv(QuartPos.toBlock(qx), size);
        int cz = Math.floorDiv(QuartPos.toBlock(qz), size);
        return ((cx + cz) & 1) == 0;
    }

    @Override
    public MapCodec<? extends BiomeZone> codec() {
        return MAP_CODEC;
    }
}
