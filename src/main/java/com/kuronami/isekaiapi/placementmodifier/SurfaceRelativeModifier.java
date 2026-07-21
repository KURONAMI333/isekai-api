package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Places features at {@code resolveAnchor(pos) + offset(rand)}. Drop-in replacement for
 * vanilla {@code HeightRangePlacement.uniform(...)} when the consumer's playable range
 * is anchored to a non-fixed Y reference.
 *
 * <p>The anchor is a {@link SurfaceAnchor}, dispatched at placement time:
 * <ul>
 *   <li>{@link SurfaceAnchor.WorldSurface} — vanilla {@code WORLD_SURFACE_WG} heightmap</li>
 *   <li>{@link SurfaceAnchor.FixedY} — fixed Y level (degenerates to absolute)</li>
 *   <li>{@link SurfaceAnchor.BelowFluid} — top of contiguous fluid body in column</li>
 * </ul>
 *
 * <p>JSON form:
 * <pre>{@code
 * {
 *   "type": "isekai_api:surface_relative",
 *   "anchor": { "type": "isekai:world_surface" },
 *   "offset": { "type": "minecraft:uniform", "value": { "min_inclusive": -3, "max_inclusive": 3 } }
 * }
 * }</pre>
 */
@ApiStatus.Internal
public class SurfaceRelativeModifier extends PlacementModifier {
    public static final MapCodec<SurfaceRelativeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SurfaceAnchor.CODEC.fieldOf("anchor").forGetter(m -> m.anchor),
            IntProvider.CODEC.fieldOf("offset").forGetter(m -> m.offset)
    ).apply(i, SurfaceRelativeModifier::new));

    private final SurfaceAnchor anchor;
    private final IntProvider offset;

    public SurfaceRelativeModifier(SurfaceAnchor anchor, IntProvider offset) {
        this.anchor = anchor;
        this.offset = offset;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        Integer anchorY = anchor.resolveY(ctx, pos);
        if (anchorY == null) {
            return Stream.empty();
        }
        int y = anchorY + offset.sample(rand);
        return Stream.of(new BlockPos(pos.getX(), y, pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SURFACE_RELATIVE.get();
    }
}
