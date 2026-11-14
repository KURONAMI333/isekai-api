package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.IsekaiDimensionStability;
import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports a nether or end built on an {@code isekai_api:} biome source as stable, so the world
 * does not open behind the experimental-settings prompt. The rule lives in
 * {@link IsekaiDimensionStability}; this only answers before vanilla's own check.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsStabilityMixin {

    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void isekai$vouchForIsekaiDimensions(ResourceKey<LevelStem> key, LevelStem stem,
                                                        CallbackInfoReturnable<Lifecycle> cir) {
        if (IsekaiDimensionStability.isVouchedReplacement(key, stem)) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
