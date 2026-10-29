package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.CarverScope;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Applies the dimension-scoped carver rule when a biome's carvers are read for carving.
 *
 * <p>NeoForge's {@code BiomeModifier} resolves once per biome with no dimension in hand, so
 * removing an excluded carver or adding an extra one there changed that biome in every
 * dimension sharing it. {@link CarverScope} instead holds the rule the REMOVE/ADD phases
 * recorded for this (dimension, biome) pair, and this redirect applies it at the one point
 * where both the dimension and the biome of the chunk being carved are known:
 * {@code NoiseBasedChunkGenerator#applyCarvers} asks each neighbour chunk for its carver
 * settings through {@code ChunkAccess#carverBiome}, and the {@link WorldGenRegion} it was
 * handed is the level being generated.
 *
 * <p>The biome is resolved exactly as the vanilla supplier resolves it — from the generator's
 * own {@code BiomeSource} at the neighbour chunk's corner quart — because the neighbour chunk
 * itself cannot be asked: for a chunk that has not been generated yet,
 * {@code WorldGenRegion#getChunk} hands out an EMPTY chunk whose
 * {@code ChunkAccess#getNoiseBiome} throws "Asking for biomes before we have biomes".
 * Reading the biome source is also what keeps the two answers identical when a dimension's
 * surface and noise biomes differ.
 *
 * <p>The settings object itself is returned by {@code carverBiome} unchanged, which keeps its
 * per-chunk cache intact; only the list this class hands back is filtered, and the filtered
 * result is cached per (dimension, biome) inside {@link CarverScope}.
 *
 * <p>Only a noise-based generator reaches this hook. A custom generator is unaffected — and
 * {@link CarverScope#hookable} makes the phases leave their old global edit in place for such a
 * dimension, so nothing silently stops working. A subclass of
 * {@code NoiseBasedChunkGenerator} that overrides {@code applyCarvers} without calling super is
 * the one case this cannot see; it is documented in {@code GAP_LOG.md}.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorCarverMixin {

    @Redirect(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;carverBiome"
                            + "(Ljava/util/function/Supplier;)Lnet/minecraft/world/level/biome/BiomeGenerationSettings;"))
    private BiomeGenerationSettings isekai$scopeCarvers(ChunkAccess chunk,
                                                        Supplier<BiomeGenerationSettings> supplier,
                                                        WorldGenRegion level,
                                                        long seed,
                                                        RandomState random,
                                                        BiomeManager biomeManager,
                                                        StructureManager structureManager,
                                                        ChunkAccess carvingChunk,
                                                        GenerationStep.Carving step) {
        BiomeGenerationSettings settings = chunk.carverBiome(supplier);
        if (CarverScope.isEmpty()) return settings;
        ChunkPos pos = chunk.getPos();
        Holder<Biome> biome = ((ChunkGenerator) (Object) this).getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(pos.getMinBlockX()), 0, QuartPos.fromBlock(pos.getMinBlockZ()),
                random.sampler());
        return CarverScope.filter(level.getLevel().dimension(), biome, settings,
                level.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER));
    }
}
