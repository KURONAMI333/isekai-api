package com.kuronami.isekaiapi.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's per-dimension stability verdict so GameTests can check it end to end. */
@Mixin(WorldDimensions.class)
public interface WorldDimensionsInvoker {

    @Invoker("checkStability")
    static Lifecycle isekai$checkStability(ResourceKey<LevelStem> key, LevelStem stem) {
        throw new AssertionError("mixin invoker not applied");
    }
}
