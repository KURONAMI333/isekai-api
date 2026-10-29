package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.DimensionFeatureScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vetoes a scoped feature at the last placement step, independent of the generator.
 *
 * <p>{@code ChunkGeneratorDecorationMixin} scopes features by swapping the biome's generation
 * settings inside {@code ChunkGenerator#applyBiomeDecoration}. A generator that overrides
 * {@code applyBiomeDecoration} as a whole — a common pattern in mods with their own chunk
 * generator — never reaches that call, so the redirect never runs and the remap's originals
 * place next to the guarded copies. {@link DimensionFeatureScope#shouldDrop} holds the same
 * takeover set as values instead of keys, and this head injection rejects them here.
 *
 * <p>Why this exact method: {@code PlacedFeature#placeWithContext} is the single private body
 * both {@code place} and {@code placeWithBiomeCheck} delegate to ({@code PlacedFeature.java:44}
 * in 1.21.1), so one injection covers every route a generator can take into a placed feature.
 * The position passed to it is the feature's candidate origin, which is where
 * {@code WorldGenLevel#getBiome} resolves the biome the chunk generator is decorating.
 *
 * <p>Cost: the {@code hasAnyDrop()} guard returns before any biome lookup when no worldshape
 * declared a drop, so the common case is one volatile read per placed feature.
 */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureScopeMixin {

    @Inject(method = "placeWithContext", at = @At("HEAD"), cancellable = true)
    private void isekai$vetoPlacedFeature(PlacementContext context,
                                          RandomSource random,
                                          BlockPos pos,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!DimensionFeatureScope.hasAnyDrop()) return;
        WorldGenLevel level = context.getLevel();
        ResourceKey<Level> dimension = level.getLevel().dimension();
        Holder<Biome> biome = level.getBiome(pos);
        if (DimensionFeatureScope.shouldDrop(dimension, biome, (PlacedFeature) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
