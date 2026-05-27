package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.impl.DimensionResolver;
import com.kuronami.isekaiapi.impl.RemapEngine;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale {@link RandomSpreadStructurePlacement#spacing()} and {@code separation()} by the
 * effective count factor of the worldshape's {@code structureStrategy} for the dimension
 * currently being queried.
 *
 * <p>The challenge: {@code spacing()} / {@code separation()} don't receive a dimension
 * context — they're parameterless getters. But their caller chain DOES have context:
 * {@code isPlacementChunk(ChunkGeneratorStructureState, x, z)} receives the structure
 * state, which carries the dim-specific {@code biomeSource} (AT-exposed).
 *
 * <p>So the Mixin uses a ThreadLocal&lt;ChunkGeneratorStructureState&gt; that gets set
 * at {@code isPlacementChunk} entry and cleared at exit; the spacing/separation
 * Mixins read it, ask {@link DimensionResolver} which dim that BiomeSource belongs to,
 * and pull the descriptor's {@code structureStrategy} factor for that specific dim.
 * Single-dim consumers see no change in behavior; multi-dim consumers get per-dim
 * factors applied correctly.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public class RandomSpreadSpacingMixin {

    /**
     * Per-thread cursor: the structureState currently being evaluated by
     * {@code isPlacementChunk}. Read by the spacing()/separation() Mixins on the
     * same thread to resolve the calling dimension.
     */
    private static final ThreadLocal<ChunkGeneratorStructureState> CURRENT_STATE = new ThreadLocal<>();

    @Inject(method = "isPlacementChunk", at = @At("HEAD"))
    private void isekai$captureState(ChunkGeneratorStructureState state, int x, int z,
                                     CallbackInfoReturnable<Boolean> cir) {
        CURRENT_STATE.set(state);
    }

    @Inject(method = "isPlacementChunk", at = @At("RETURN"))
    private void isekai$clearState(ChunkGeneratorStructureState state, int x, int z,
                                   CallbackInfoReturnable<Boolean> cir) {
        CURRENT_STATE.remove();
    }

    @Inject(method = "spacing", at = @At("HEAD"), cancellable = true)
    private void isekai$scaleSpacing(CallbackInfoReturnable<Integer> cir) {
        double factor = effectiveFactor();
        if (factor == 1.0) return;
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        int scaled = Math.max(1, (int) Math.round(self.spacing() / factor));
        cir.setReturnValue(scaled);
    }

    @Inject(method = "separation", at = @At("HEAD"), cancellable = true)
    private void isekai$scaleSeparation(CallbackInfoReturnable<Integer> cir) {
        double factor = effectiveFactor();
        if (factor == 1.0) return;
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        int scaled = Math.max(0, (int) Math.round(self.separation() / factor));
        cir.setReturnValue(scaled);
    }

    /**
     * Resolve the {@code structureStrategy} count factor for the dimension currently
     * being queried. Order of resolution:
     * <ol>
     *   <li>If no structureState is set on this thread (called outside a placement
     *       check), return 1.0 — leave vanilla behaviour untouched.</li>
     *   <li>If the state's BiomeSource can't be matched to a loaded dimension, return
     *       1.0.</li>
     *   <li>If that dim has no declared descriptor, return 1.0.</li>
     *   <li>Otherwise return that descriptor's {@code RemapEngine.effectiveCountFactor}.</li>
     * </ol>
     */
    private static double effectiveFactor() {
        var state = CURRENT_STATE.get();
        if (state == null) return 1.0;
        var dim = DimensionResolver.resolveByBiomeSource(state.biomeSource);
        if (dim == null) return 1.0;
        var descriptor = Isekai.remap().getActiveDescriptor(dim).orElse(null);
        if (descriptor == null) return 1.0;
        double f = RemapEngine.effectiveCountFactor(descriptor.structureStrategy());
        return (f > 0.0) ? f : 1.0;
    }
}
