package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Places only in the declared dimension.
 *
 * <p>NeoForge's {@code BiomeModifier} runs per biome, not per dimension, so an injected
 * feature would otherwise generate in every dimension that reuses the biome it was added
 * to. {@code isekai_api:apply_worldshape} prepends this modifier to every feature it injects
 * to pin it to the descriptor's dimension.
 *
 * <p>Fails closed: a placement context without a server level yields nothing rather than
 * letting the feature escape into an unchecked dimension.
 *
 * <p>JSON form:
 * {@code {"type": "isekai_api:dimension_guard", "dimension": "minecraft:the_nether"}}.
 */
@ApiStatus.Internal
public class DimensionGuardPlacementModifier extends PlacementModifier {

    public static final MapCodec<DimensionGuardPlacementModifier> CODEC =
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .xmap(DimensionGuardPlacementModifier::new, DimensionGuardPlacementModifier::dimension);

    private final ResourceKey<Level> dimension;

    public DimensionGuardPlacementModifier(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        var serverLevel = ctx.getLevel().getLevel();
        if (serverLevel == null || !dimension.equals(serverLevel.dimension())) {
            return Stream.empty();
        }
        return Stream.of(pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.DIMENSION_GUARD.get();
    }
}
