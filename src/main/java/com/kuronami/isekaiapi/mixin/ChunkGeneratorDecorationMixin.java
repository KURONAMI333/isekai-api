package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.DimensionFeatureScope;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/**
 * Swaps the biome's feature steps for the dimension-scoped ones at decoration time.
 *
 * <p>NeoForge's {@code BiomeModifier} resolves once per biome with no dimension in hand, so
 * deleting an excluded or remapped feature there removed it in every dimension sharing that
 * biome. {@link DimensionFeatureScope} instead holds the takeover set the ADD phase recorded
 * for this (dimension, biome) pair, and this redirect applies it at the only point where both
 * the dimension and the biome of the chunk being decorated are known: the
 * {@code generationSettingsGetter.apply(biome)} call inside
 * {@link ChunkGenerator#applyBiomeDecoration}.
 *
 * <p>Only the feature steps change here. Carvers are read later by {@code applyCarvers} from
 * a different call site, where the biome key is not exposed; they get their own hook there
 * ({@code NoiseBasedChunkGeneratorCarverMixin} → {@code CarverScope}) instead of staying
 * global — see the class javadoc of {@link DimensionFeatureScope}.
 *
 * <p>The redirect returns {@code Object} because the erased call site is
 * {@code Function.apply(Object)}; the following bytecode casts back to
 * {@code BiomeGenerationSettings}.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorDecorationMixin {

    @Redirect(
            method = "applyBiomeDecoration",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    @SuppressWarnings("unchecked")
    private Object isekai$scopeBiomeFeatures(Function<Holder<Biome>, BiomeGenerationSettings> getter,
                                             Object biome,
                                             WorldGenLevel level,
                                             ChunkAccess chunk,
                                             StructureManager structureManager) {
        Holder<Biome> holder = (Holder<Biome>) biome;
        BiomeGenerationSettings settings = getter.apply(holder);
        // The generating level's own dimension key: exact for every dimension, modded ones
        // included, and it cannot be confused by two dimensions sharing a BiomeSource.
        return DimensionFeatureScope.filter(level.getLevel().dimension(), holder, settings);
    }
}
