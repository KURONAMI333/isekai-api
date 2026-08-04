package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Defines "what Y level counts as the surface" for surface-relative placement modifiers.
 *
 * <p><b>Extensible.</b> Built-in variants are registered in
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#SURFACE_ANCHOR_TYPE}; third parties
 * add their own by registering a {@link MapCodec} under that key. The {@link #CODEC} dispatches on
 * a {@code "type"} field, e.g. {@code {"type": "isekai_api:fixed_y", "y": 64}}. The legacy
 * {@code isekai:} prefix is accepted as a deprecated alias.
 *
 * @since 1.0.0
 */
public interface SurfaceAnchor {

    /** This variant's payload codec (no {@code "type"} field); must be the registered instance. @since 1.0.0 */
    MapCodec<? extends SurfaceAnchor> codec();

    /**
     * Resolve this anchor's Y for the column at {@code pos}. Returns {@code null} when the anchor
     * can't be resolved for the column (e.g. {@link BelowFluid} where that fluid is absent), in
     * which case the surface-relative placement is skipped. @since 2.0.0
     */
    @Nullable Integer resolveY(PlacementContext ctx, BlockPos pos);

    /** Dispatching codec keyed on a {@code "type"} field, backed by the SurfaceAnchor registry. */
    Codec<SurfaceAnchor> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.SURFACE_ANCHOR_REGISTRY, SurfaceAnchor::codec, "SurfaceAnchor");

    /** Topmost solid block per column (vanilla heightmap WORLD_SURFACE). @since 1.0.0 */
    record WorldSurface() implements SurfaceAnchor {
        public static final WorldSurface INSTANCE = new WorldSurface();
        public static final MapCodec<WorldSurface> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
        @Override public Integer resolveY(PlacementContext ctx, BlockPos pos) {
            return ctx.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        }
    }

    /** Top of the highest contiguous body of the given fluid in each column. @since 1.0.0 */
    record BelowFluid(Fluid fluid) implements SurfaceAnchor {
        public static final MapCodec<BelowFluid> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(BelowFluid::fluid)
        ).apply(i, BelowFluid::new));

        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
        @Override public @Nullable Integer resolveY(PlacementContext ctx, BlockPos pos) {
            WorldGenLevel level = ctx.getLevel();
            int top = level.getMaxBuildHeight() - 1;
            int bottom = level.getMinBuildHeight();
            for (int y = top; y >= bottom; y--) {
                BlockState state = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
                if (state.getFluidState().getType() == fluid) {
                    return y;
                }
            }
            return null;  // no matching fluid in column
        }
    }

    /**
     * Underside of the topmost body in the column — the mirror of {@link WorldSurface}. Where
     * that returns the first free space <i>above</i> the terrain, this scans downward from
     * {@code start} and returns the first free space <i>below</i> it.
     *
     * <p>The scan crosses at most {@code maxScan} blocks in total: leading air (from
     * {@code start} down to the top of the body) plus the body itself. It returns {@code null}
     * when no body is found within the budget, and also when the body never ends — so in solid
     * ground-to-bedrock terrain this anchor never resolves and the placement is skipped. It is
     * a floating-terrain anchor by construction: islands, orbiting planets, sky continents.
     *
     * <p>JSON: {@code {"type": "isekai_api:world_floor"}}, or with either field overridden,
     * {@code {"type": "isekai_api:world_floor", "start": {"type": "isekai_api:fixed_y", "y": 320},
     * "max_scan": 256}}.
     * @since 2.0.0
     */
    record WorldFloor(SurfaceAnchor start, int maxScan) implements SurfaceAnchor {

        /** Default total scan budget in blocks. @since 2.0.0 */
        public static final int DEFAULT_MAX_SCAN = 128;

        /** Scan down from the world surface with the default budget. @since 2.0.0 */
        public static final WorldFloor DEFAULT = new WorldFloor(WorldSurface.INSTANCE, DEFAULT_MAX_SCAN);

        public static final MapCodec<WorldFloor> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).optionalFieldOf("start", WorldSurface.INSTANCE)
                        .forGetter(WorldFloor::start),
                Codec.intRange(1, 4096).optionalFieldOf("max_scan", DEFAULT_MAX_SCAN)
                        .forGetter(WorldFloor::maxScan)
        ).apply(i, WorldFloor::new));

        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }

        @Override public @Nullable Integer resolveY(PlacementContext ctx, BlockPos pos) {
            Integer from = start.resolveY(ctx, pos);
            if (from == null) return null;
            WorldGenLevel level = ctx.getLevel();
            int bottom = level.getMinBuildHeight();
            int y = Math.min(from, level.getMaxBuildHeight() - 1);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            boolean inBody = false;
            for (int step = 0; step <= maxScan; step++) {
                int cy = y - step;
                if (cy < bottom) return null;
                cursor.set(pos.getX(), cy, pos.getZ());
                boolean air = level.getBlockState(cursor).isAir();
                if (!inBody) {
                    inBody = !air;          // still descending through the space above the body
                } else if (air) {
                    return cy;              // first free space under the body
                }
            }
            return null;  // no body, or a body deeper than the scan budget
        }
    }

    /** Fixed Y level regardless of terrain. @since 1.0.0 */
    record FixedY(int y) implements SurfaceAnchor {
        public static final MapCodec<FixedY> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(FixedY::y)
        ).apply(i, FixedY::new));

        @Override public MapCodec<? extends SurfaceAnchor> codec() { return MAP_CODEC; }
        @Override public Integer resolveY(PlacementContext ctx, BlockPos pos) { return y; }
    }
}
