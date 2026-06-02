package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scatter the input position into {@code count} samples within an XZ {@code radius} of it,
 * optionally rejecting any sample within {@code min_spacing} blocks of an already-accepted
 * sample. Combines what vanilla offers via {@code count + in_square} but adds the
 * minimum-spacing constraint that vanilla lacks — so features that should be present
 * multiple times per chunk but NOT stack on each other (clustered trees, scattered shrubs,
 * spaced ore veins) have a single declarative primitive.
 *
 * <p>Neutral: purely statistical / geometric; no biome, block, or feature semantics. Pairs
 * with any downstream heightmap / Y-anchor modifier (vanilla {@code heightmap} or Isekai's
 * {@code surface_relative} / {@code fluid_relative}) — Y on the output is left at the input
 * Y for the next modifier to resolve.
 *
 * <p>Schema. {@code count} (IntProvider): how many samples to attempt. {@code radius} (int
 * 1-32, default 8): XZ jitter radius around the input position. {@code min_spacing} (int 0-32,
 * default 0): minimum block distance between any two accepted samples; 0 disables the
 * constraint and the modifier becomes equivalent to {@code count + in_square} with arbitrary
 * radius. {@code max_attempts_multiplier} (int 1-8, default 3): rejection sampling cap —
 * when {@code min_spacing > 0} samples may be rejected, and this multiplier bounds how many
 * extra tries we do before giving up to keep the modifier non-blocking on impossible spacings.
 *
 * <p>Exposed as {@code isekai_api:scatter}.
 */
@ApiStatus.Internal
public class ScatterPlacementModifier extends PlacementModifier {

    public static final MapCodec<ScatterPlacementModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProvider.codec(0, 256).fieldOf("count").forGetter(m -> m.count),
            Codec.intRange(1, 32).optionalFieldOf("radius", 8).forGetter(m -> m.radius),
            Codec.intRange(0, 32).optionalFieldOf("min_spacing", 0).forGetter(m -> m.minSpacing),
            Codec.intRange(1, 8).optionalFieldOf("max_attempts_multiplier", 3)
                    .forGetter(m -> m.maxAttemptsMultiplier)
    ).apply(i, ScatterPlacementModifier::new));

    private final IntProvider count;
    private final int radius;
    private final int minSpacing;
    private final int maxAttemptsMultiplier;

    public ScatterPlacementModifier(IntProvider count, int radius, int minSpacing,
                                    int maxAttemptsMultiplier) {
        this.count = count;
        this.radius = radius;
        this.minSpacing = minSpacing;
        this.maxAttemptsMultiplier = maxAttemptsMultiplier;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        int target = this.count.sample(rand);
        if (target <= 0) {
            return Stream.empty();
        }
        if (this.minSpacing <= 0) {
            // Fast path: no spacing constraint — same as count + in_square but with our radius.
            List<BlockPos> out = new ArrayList<>(target);
            for (int i = 0; i < target; i++) {
                int dx = rand.nextIntBetweenInclusive(-this.radius, this.radius);
                int dz = rand.nextIntBetweenInclusive(-this.radius, this.radius);
                out.add(pos.offset(dx, 0, dz));
            }
            return out.stream();
        }
        // Slow path: rejection sample so accepted samples stay >= min_spacing apart in XZ.
        int spacingSq = this.minSpacing * this.minSpacing;
        int maxAttempts = target * this.maxAttemptsMultiplier;
        List<BlockPos> accepted = new ArrayList<>(target);
        for (int attempt = 0; attempt < maxAttempts && accepted.size() < target; attempt++) {
            int dx = rand.nextIntBetweenInclusive(-this.radius, this.radius);
            int dz = rand.nextIntBetweenInclusive(-this.radius, this.radius);
            BlockPos candidate = pos.offset(dx, 0, dz);
            boolean ok = true;
            for (BlockPos a : accepted) {
                int ddx = candidate.getX() - a.getX();
                int ddz = candidate.getZ() - a.getZ();
                if (ddx * ddx + ddz * ddz < spacingSq) {
                    ok = false;
                    break;
                }
            }
            if (ok) accepted.add(candidate);
        }
        return accepted.stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SCATTER.get();
    }
}
