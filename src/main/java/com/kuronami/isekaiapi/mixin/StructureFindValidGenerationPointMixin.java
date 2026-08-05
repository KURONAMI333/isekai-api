package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.DimensionResolver;
import com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator;
import com.kuronami.isekaiapi.impl.StructureThinning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Hook into {@link Structure#findValidGenerationPoint} to enforce the consumer's
 * {@code structure_predicates} (or {@code default_structure_predicate}) and
 * {@code structure_strategy} at structure placement time.
 *
 * <p>Without this Mixin, the {@code structure_predicates} field of
 * {@link WorldshapeDescriptor} is declared in JSON but has zero effect at chunk
 * generation. With it, the descriptor's spatial predicate is evaluated against the
 * structure's candidate GenerationStub position; failure short-circuits the chain
 * to {@code Optional.empty()}, so the structure doesn't spawn.
 *
 * <p>The same veto carries the descriptor's {@code structure_strategy} {@code count_scale}
 * factor: a share of the candidates vanilla's grid produced is dropped deterministically
 * (see {@link StructureThinning} for why the placement grid itself is left alone). Both
 * vetoes only ever remove a structure — neither can move one or add one.
 *
 * <p>Resolution order: the descriptor's {@code structurePredicates} map is checked first
 * for an entry keyed by this specific structure; if absent, the
 * {@code defaultStructurePredicate} is used.
 *
 * <p>Inject point: {@code @At("RETURN")} with {@code cancellable = true}, so the
 * returned Optional can be replaced with empty when the predicate fails.
 *
 * <p>Scope: the predicate applied is the one for the dimension the structure is actually
 * generating in (resolved from the generation context's biome source via
 * {@link com.kuronami.isekaiapi.impl.DimensionResolver}). Dimensions without a declared
 * descriptor — and structures whose dimension can't be resolved — skip the check entirely,
 * so a restrictive predicate in one worldshape never suppresses structures in another
 * dimension that happens to reuse the same biomes.
 */
@Mixin(Structure.class)
public class StructureFindValidGenerationPointMixin {

    @Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void isekai$applySpatialPredicate(Structure.GenerationContext context,
                                               CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Optional<Structure.GenerationStub> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;  // vanilla already said no, leave it

        // Apply ONLY the descriptor for the dimension this structure is actually generating
        // in — resolved from the generation context's biome source. Applying every declared
        // dimension's predicate (as an earlier version did) suppressed structures in dim B
        // whenever dim A declared a restrictive predicate, since biomes are reused across
        // dimensions. If the dimension can't be resolved or has no descriptor, leave vanilla
        // untouched.
        ResourceKey<Level> dimKey = DimensionResolver.resolveByBiomeSource(context.biomeSource());
        if (dimKey == null) return;
        // Layered worlds: pick the descriptor that covers the structure's chosen position.
        // Single-descriptor dims fall through to getActiveDescriptor inside getDescriptorAt.
        BlockPos pos = result.get().position();
        WorldshapeDescriptor descriptor = Isekai.remap()
                .getDescriptorAt(dimKey, pos.getX(), pos.getY(), pos.getZ()).orElse(null);
        if (descriptor == null) return;

        Structure structure = (Structure) (Object) this;
        ResourceKey<Structure> structureKey = resolveStructureKey(structure);

        // structure_strategy count_scale: thin the candidates vanilla's grid produced. Runs
        // before the predicate because it is the cheaper of the two vetoes.
        double countFactor = descriptor.structureStrategy().countFactor();
        if (countFactor < 1.0) {
            String structureId = structureKey != null
                    ? structureKey.location().toString()
                    : structure.getClass().getName();
            if (!StructureThinning.keep(countFactor, context.seed(),
                    context.chunkPos().x, context.chunkPos().z, structureId)) {
                IsekaiApi.LOGGER.debug(
                        "[Isekai] structure {} thinned at {} (dim={}, count_scale={})",
                        structureId, pos, dimKey.location(), countFactor);
                cir.setReturnValue(Optional.empty());
                return;
            }
        }

        SpatialPredicate predicate = predicateFor(descriptor, structureKey);
        if (predicate == null) return;
        SpatialPredicateEvaluator.Context evalCtx = new SpatialPredicateEvaluator.Context(
                context.chunkGenerator(), context.heightAccessor(), context.randomState(),
                context.biomeSource());
        if (!SpatialPredicateEvaluator.evaluate(predicate, pos, evalCtx)) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] structure {} rejected by predicate at {} (dim={})",
                    structureKey != null ? structureKey.location() : structure, pos, dimKey.location());
            cir.setReturnValue(Optional.empty());
        }
    }

    /**
     * Look up the predicate for this Structure: per-structure entry first
     * ({@link WorldshapeDescriptor#structurePredicates()}), then fall back to the
     * descriptor's {@link WorldshapeDescriptor#defaultStructurePredicate()}.
     * Returns {@code null} when neither path applies (silent allow).
     */
    private SpatialPredicate predicateFor(WorldshapeDescriptor descriptor,
                                           ResourceKey<Structure> structureKey) {
        if (structureKey != null) {
            SpatialPredicate perStructure = descriptor.structurePredicates().get(structureKey);
            if (perStructure != null) return perStructure;
        }
        return descriptor.defaultStructurePredicate();
    }

    /**
     * Recover the {@link ResourceKey} for a Structure instance via the live registry.
     * Returns {@code null} when no server is available (e.g. datagen) or when the
     * structure isn't in any registry (impossible in practice but defensive).
     */
    private static ResourceKey<Structure> resolveStructureKey(Structure structure) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getResourceKey(structure)
                .orElse(null);
    }
}
