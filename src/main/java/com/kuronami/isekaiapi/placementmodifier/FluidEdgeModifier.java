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
 * <p>JSON: {@code {"type":"isekai_api:fluid_edge", "fluid": "#minecraft:water", "max_distance": 4,
 * "mode": "near"}}.
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

    public static final MapCodec<FluidEdgeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.FLUID).fieldOf("fluid").forGetter(m -> m.fluid),
            Codec.intRange(1, 16).optionalFieldOf("max_distance", 4).forGetter(m -> m.maxDistance),
            Mode.CODEC.optionalFieldOf("mode", Mode.NEAR).forGetter(m -> m.mode)
    ).apply(i, FluidEdgeModifier::new));

    private final HolderSet<Fluid> fluid;
    private final int maxDistance;
    private final Mode mode;

    public FluidEdgeModifier(HolderSet<Fluid> fluid, int maxDistance, Mode mode) {
        this.fluid = fluid;
        this.maxDistance = maxDistance;
        this.mode = mode;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean found = false;
        outer:
        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                if (level.getFluidState(cursor).is(fluid)) {
                    found = true;
                    break outer;
                }
            }
        }
        boolean accept = (mode == Mode.NEAR) == found;
        return accept ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.FLUID_EDGE.get();
    }
}
