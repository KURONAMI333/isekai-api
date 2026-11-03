package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

/**
 * Filter placement by horizontal distance to a fluid. Accepts the input position when a block
 * matching the given {@code fluid} (single fluid, list, or {@code #tag}) is found within
 * {@code max_distance} blocks in the XZ plane (chebyshev distance) — or rejects, depending on
 * {@code mode}.
 *
 * <p>Pure geometry / membership filter — no biome or "shoreline" semantics baked in. Consumers
 * choose what the fluid means in their world.
 *
 * <p>{@code vertical_range} widens the search above and below the input Y, so one modifier can
 * say "no water anywhere between Y and Y+18 within 12 blocks" instead of stacking one modifier
 * plus one Y anchor per level. It is optional: omitted (or {@code {"below":0,"above":0}}) searches
 * the input Y only, which is what every pack written before it existed already gets.
 *
 * <p>JSON: {@code {"type":"isekai_api:fluid_edge", "fluid": "#minecraft:water", "max_distance": 4,
 * "mode": "near", "vertical_range": {"below": 8, "above": 8}}}.
 *
 * @since after 3.0.0; packs that omit the field keep the single-level behaviour.
 */
@ApiStatus.Internal
public class FluidEdgeModifier extends PlacementModifier {

    public enum Mode implements StringRepresentable {
        NEAR("near"), FAR("far");
        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String name;
        Mode(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    /**
     * How far above and below the input position to look, in blocks. Both bounds are inclusive,
     * so {@code {"below": 0, "above": 18}} searches 19 levels starting at the input Y.
     */
    public record VerticalSpan(int below, int above) {
        public static final VerticalSpan SINGLE = new VerticalSpan(0, 0);

        public static final Codec<VerticalSpan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 64).optionalFieldOf("below", 0).forGetter(VerticalSpan::below),
                Codec.intRange(0, 64).optionalFieldOf("above", 0).forGetter(VerticalSpan::above)
        ).apply(i, VerticalSpan::new));

        public boolean isSingleLevel() {
            return below == 0 && above == 0;
        }
    }

    public static final MapCodec<FluidEdgeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluid").forGetter(m -> m.fluid),
            Codec.intRange(1, 16).optionalFieldOf("max_distance", 4).forGetter(m -> m.maxDistance),
            Mode.CODEC.optionalFieldOf("mode", Mode.NEAR).forGetter(m -> m.mode),
            VerticalSpan.CODEC.optionalFieldOf("vertical_range", VerticalSpan.SINGLE)
                    .forGetter(m -> m.verticalRange)
    ).apply(i, FluidEdgeModifier::new));

    private final HolderSet<Fluid> fluid;
    private final int maxDistance;
    private final Mode mode;
    private final VerticalSpan verticalRange;

    public FluidEdgeModifier(HolderSet<Fluid> fluid, int maxDistance, Mode mode) {
        this(fluid, maxDistance, mode, VerticalSpan.SINGLE);
    }

    public FluidEdgeModifier(HolderSet<Fluid> fluid, int maxDistance, Mode mode,
                             VerticalSpan verticalRange) {
        this.fluid = fluid;
        this.maxDistance = maxDistance;
        this.mode = mode;
        this.verticalRange = verticalRange;
    }

    public VerticalSpan verticalRange() {
        return this.verticalRange;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        int yMin = Math.max(ctx.getMinBuildHeight(), pos.getY() - verticalRange.below());
        int yMax = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + verticalRange.above());
        boolean found = yMin <= yMax
                && (verticalRange.isSingleLevel()
                        ? anyAtLevel(level, pos, pos.getY())
                        : anyInBand(ctx, level, pos, yMin, yMax));
        boolean accept = (mode == Mode.NEAR) == found;
        return accept ? Stream.of(pos) : Stream.empty();
    }

    /** The pre-{@code vertical_range} search, unchanged: one horizontal plane at {@code y}. */
    private boolean anyAtLevel(WorldGenLevel level, BlockPos pos, int y) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                cursor.set(pos.getX() + dx, y, pos.getZ() + dz);
                if (level.getFluidState(cursor).is(fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The widened search, one column at a time.
     *
     * <p>{@code MOTION_BLOCKING} counts every block that either blocks motion or holds a fluid, so
     * a fluid at level {@code y} forces the column's height above {@code y}: capping the scan at
     * {@code height - 1} can miss no fluid, and a column whose height is already below the band
     * (open sea under a band that starts higher) costs one array read instead of one read per
     * level. Every level the cap leaves in range is still probed exactly, so fluid under a roof —
     * a pond below a canopy, a flooded cave — is found like any other.
     *
     * <p>That heightmap is maintained by every block write both during generation (every step from
     * carvers on primes and updates it) and on a live level, unlike the {@code _WG} variants.
     * Keeping the single-level path free of it also keeps its reads identical to the behaviour
     * packs already rely on.
     */
    private boolean anyInBand(PlacementContext ctx, WorldGenLevel level, BlockPos pos,
                              int yMin, int yMax) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            int x = pos.getX() + dx;
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                int z = pos.getZ() + dz;
                int top = Math.min(yMax, ctx.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1);
                for (int y = top; y >= yMin; y--) {
                    cursor.set(x, y, z);
                    if (level.getFluidState(cursor).is(fluid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.FLUID_EDGE.get();
    }
}
