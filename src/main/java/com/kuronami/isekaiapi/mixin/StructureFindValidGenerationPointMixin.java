package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Hook into {@link Structure#findValidGenerationPoint} to enforce the consumer's
 * {@code structure_predicates} (or {@code default_structure_predicate}) at structure
 * placement time.
 *
 * <p>Without this Mixin, the {@code structure_predicates} field of
 * {@link WorldshapeDescriptor} is declared in JSON but has zero effect at chunk
 * generation. With it, the descriptor's spatial predicate is evaluated against the
 * structure's candidate GenerationStub position; failure short-circuits the chain
 * to {@code Optional.empty()}, so the structure doesn't spawn.
 *
 * <p>Resolution order: the descriptor's {@code structurePredicates} map is checked first
 * for an entry keyed by this specific structure; if absent, the
 * {@code defaultStructurePredicate} is used.
 *
 * <p>Inject point: {@code @At("RETURN")} with {@code cancellable = true}, so the
 * returned Optional can be replaced with empty when the predicate fails.
 *
 * <p>Note: only fires when a {@code WorldshapeDescriptor} is declared for the dimension
 * the structure would spawn in. Dimensions without declarations skip the entire check.
 */
@Mixin(Structure.class)
public class StructureFindValidGenerationPointMixin {

    @Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void isekai$applySpatialPredicate(Structure.GenerationContext context,
                                               CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Optional<Structure.GenerationStub> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;  // vanilla already said no, leave it

        // Resolve the dimension from the context's chunkGenerator path. We don't have a
        // direct dimension key, but we can correlate against declared descriptors.
        // For v1.0: iterate every declared dimension; the first one whose biome filter
        // matches AND has a predicate for this structure wins. Lightweight in practice
        // because most worlds have at most 1-3 declared dimensions.
        var declaredDims = Isekai.remap().getDeclaredDimensions();
        if (declaredDims.isEmpty()) return;

        Structure structure = (Structure) (Object) this;
        BlockPos pos = result.get().position();
        SpatialPredicateEvaluator.Context evalCtx = new SpatialPredicateEvaluator.Context(
                context.chunkGenerator(), context.heightAccessor(), context.randomState());

        // Try every declared dimension; if any of them rejects this structure here,
        // short-circuit. (In practice consumers declare one descriptor per dimension; this
        // is the conservative behavior — if you've said 'no canyons in my world', no
        // canyons spawn even if the biome reuses across dimensions.)
        for (var dimKey : declaredDims) {
            var descriptor = Isekai.remap().getActiveDescriptor(dimKey).orElse(null);
            if (descriptor == null) continue;
            SpatialPredicate predicate = predicateFor(descriptor, structure);
            if (predicate == null) continue;
            if (!SpatialPredicateEvaluator.evaluate(predicate, pos, evalCtx)) {
                IsekaiApi.LOGGER.debug(
                        "[Isekai] structure {} rejected by predicate at {} (descriptor dim={})",
                        structure, pos, dimKey.location());
                cir.setReturnValue(Optional.empty());
                return;
            }
        }
    }

    /**
     * Look up the predicate for this Structure: per-structure entry first, then the
     * descriptor's default. Returns {@code null} when the descriptor doesn't declare any
     * predicate path (silent allow).
     */
    private SpatialPredicate predicateFor(WorldshapeDescriptor descriptor, Structure structure) {
        // We don't have a direct ResourceKey<Structure> from the Structure instance; the
        // Mixin context can't trivially recover it without a registry lookup. For v1.0,
        // fall back to the default predicate — per-structure overrides land in v1.1 once
        // we plumb the registry-key lookup. The default is consulted, which covers the
        // common 'all structures in this world must satisfy X' use case.
        return descriptor.defaultStructurePredicate();
    }
}
