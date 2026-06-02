package com.kuronami.isekaiapi.surfacerule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutral surface-rule primitive: an ordered stack of (block, thickness) bands measured
 * downward from the floor surface. Replaces the deeply-nested {@code minecraft:sequence} of
 * {@code stone_depth} checks that an N-layer surface (e.g. sand → sandstone → stone) would
 * otherwise require, with a single flat list.
 *
 * <p>Behaviour. The bands are stacked in declaration order from the surface downward; the
 * first band covers depths {@code 0..t1-1}, the second covers {@code t1..t1+t2-1}, etc. The
 * rule emits {@code null} below the last band so the surrounding sequence (vanilla bulk fill
 * etc.) takes over.
 *
 * <p>Implementation. At apply()-time the rule BUILDS a vanilla {@link SurfaceRules} tree from
 * its bands ({@code ifTrue(stoneDepthCheck(cumulativeDepth, false, FLOOR), state(block))}
 * inside a {@code sequence}) and delegates — so it inherits vanilla's correct stone-depth
 * handling exactly and adds no access-transformer entries. Neutral: the band blocks are plain
 * {@link BlockState} entries, no species/biome baked in.
 *
 * <p>JSON: {@code {"type":"isekai_api:strata", "bands":[{"block":"...","thickness":3}, ...]}}.
 */
@ApiStatus.Internal
public record StrataRule(List<Band> bands, SurfaceRules.RuleSource inner) implements SurfaceRules.RuleSource {

    public record Band(BlockState block, int thickness) {
        public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("block").forGetter(Band::block),
                Codec.intRange(1, 64).fieldOf("thickness").forGetter(Band::thickness)
        ).apply(i, Band::new));
    }

    public static final MapCodec<StrataRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Band.CODEC.listOf().fieldOf("bands").forGetter(StrataRule::bands)
    ).apply(i, StrataRule::fromConfig));

    public static StrataRule fromConfig(List<Band> bands) {
        if (bands.isEmpty()) {
            throw new IllegalArgumentException("strata: bands must be non-empty");
        }
        List<SurfaceRules.RuleSource> steps = new ArrayList<>(bands.size());
        int cumulative = 0;
        for (Band b : bands) {
            cumulative += b.thickness;
            // stoneDepthCheck(N, false, FLOOR) matches all depths 0..N below the floor; sequence
            // first-match-wins picks the earliest band whose cumulative ceiling covers this depth.
            steps.add(SurfaceRules.ifTrue(
                    SurfaceRules.stoneDepthCheck(cumulative - 1, false, CaveSurface.FLOOR),
                    SurfaceRules.state(b.block)));
        }
        SurfaceRules.RuleSource built = SurfaceRules.sequence(steps.toArray(new SurfaceRules.RuleSource[0]));
        return new StrataRule(List.copyOf(bands), built);
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        return inner.apply(context);
    }
}
