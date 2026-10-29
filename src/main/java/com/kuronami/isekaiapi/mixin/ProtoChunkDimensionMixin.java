package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.ChunkDimensionIndex;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the owning dimension of every {@link ProtoChunk} at construction, so
 * {@link com.kuronami.isekaiapi.impl.DimensionResolver} can resolve a structure's dimension
 * exactly during normal chunk generation.
 *
 * <p>Only meaningful at generation time: {@code ChunkMap} builds its {@code ProtoChunk} with the
 * owning {@code ServerLevel} as the {@code LevelHeightAccessor}, and structure placement reads the
 * context's height accessor while the chunk is still a {@code ProtoChunk}. {@code LevelChunk} is
 * deliberately not a target: once a chunk is full, structures have already been placed, and a
 * full chunk carries no such constructor argument to index.
 */
@Mixin(ProtoChunk.class)
public class ProtoChunkDimensionMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
            at = @At("TAIL"))
    private void isekai$rememberDimension(ChunkPos chunkPos, UpgradeData upgradeData,
                                          LevelHeightAccessor accessor, Registry<Biome> biomeRegistry,
                                          BlendingData blendingData, CallbackInfo ci) {
        if (accessor instanceof ServerLevel level) {
            ChunkDimensionIndex.remember((ChunkAccess) (Object) this, level.dimension());
        }
    }
}
