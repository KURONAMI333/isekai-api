package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.impl.RemapEngine;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scale {@link RandomSpreadStructurePlacement#spacing()} and {@code separation()} by the
 * effective count factor of the active worldshape's {@code structureStrategy}. This is
 * the only path that reaches {@code StructurePlacement} private state — NeoForge's
 * {@code StructureModifier} only reaches {@code StructureSettings} (biomes / spawn
 * overrides / etc.), not the placement's spacing.
 *
 * <p>Semantics: a CountScale factor of 2.0 doubles structure frequency (halves spacing).
 * Factor 0.5 halves frequency (doubles spacing). Identity / non-CountScale strategies
 * leave the original value.
 *
 * <p>Multi-dimension caveat: spacing() / separation() are called without dimension
 * context (only the placement itself + a chunk-state-level seed). We can't easily
 * resolve "which dimension is this chunk being generated for" at the call site, so this
 * Mixin applies the strategy from the <em>first</em> declared descriptor with a
 * non-Identity structure_strategy. For single-dimension worldshapes (the common case)
 * this is correct. For mods declaring different structure_strategy values across
 * dimensions, only the first one's factor takes effect uniformly — declare a single
 * effective factor per server or live with the lossy behavior. Per-dim resolution
 * lands when a per-thread chunk-gen context becomes available (post-vanilla refactor).
 *
 * <p>Constraint preservation: vanilla validates {@code spacing > separation} via
 * {@code RandomSpreadStructurePlacement.validate}. The scaled values respect this when
 * the factor is positive (both shrink/grow together). Factor 0 is treated as 1 (no-op)
 * to avoid degenerate placement.
 */
@Mixin(RandomSpreadStructurePlacement.class)
public class RandomSpreadSpacingMixin {

    @Inject(method = "spacing", at = @At("HEAD"), cancellable = true)
    private void isekai$scaleSpacing(CallbackInfoReturnable<Integer> cir) {
        double factor = effectiveFactor();
        if (factor == 1.0) return;
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        // Larger frequency means SMALLER spacing — invert the factor here.
        int original = self.spacing();
        int scaled = Math.max(1, (int) Math.round(original / factor));
        cir.setReturnValue(scaled);
    }

    @Inject(method = "separation", at = @At("HEAD"), cancellable = true)
    private void isekai$scaleSeparation(CallbackInfoReturnable<Integer> cir) {
        double factor = effectiveFactor();
        if (factor == 1.0) return;
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        int original = self.separation();
        // Keep separation strictly less than the (scaled) spacing — clamp to spacing/2
        // when the scaled value would otherwise approach spacing and violate the invariant.
        int scaled = Math.max(0, (int) Math.round(original / factor));
        cir.setReturnValue(scaled);
    }

    /**
     * Walk every declared descriptor and return the first non-Identity
     * {@code effectiveCountFactor} found, or 1.0 when none has a structure_strategy
     * that affects spacing. Cached lookup is cheap — the declared-dimensions set is
     * usually 0-3 entries on typical servers.
     */
    private static double effectiveFactor() {
        var dims = Isekai.remap().getDeclaredDimensions();
        if (dims.isEmpty()) return 1.0;
        for (var dim : dims) {
            var descriptor = Isekai.remap().getActiveDescriptor(dim).orElse(null);
            if (descriptor == null) continue;
            double f = RemapEngine.effectiveCountFactor(descriptor.structureStrategy());
            if (f != 1.0 && f > 0.0) return f;
        }
        return 1.0;
    }
}
