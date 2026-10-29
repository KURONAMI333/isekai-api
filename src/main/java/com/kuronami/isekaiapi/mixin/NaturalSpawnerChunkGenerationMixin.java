package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.MobSpawnScope;
import net.minecraft.core.Holder;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Applies the per-(dimension, biome) mob spawn rule to the initial creature population of a
 * newly generated chunk.
 *
 * <p>{@code NaturalSpawner#spawnMobsForChunkGeneration} reads the biome settings directly
 * ({@code biome.getMobSettings().getMobs(CREATURE)}), so it bypasses the {@code mobsAt} hook
 * entirely. This redirects that {@code getMobs} call: the enclosing method carries a
 * {@link ServerLevelAccessor}, so the generating level's dimension is available, and the
 * creature list is scoped before the probability loop picks from it.
 *
 * <p>This is the chunk-generation path only. Ordinary spawning after a chunk exists goes
 * through {@code mobsAt} and {@code NaturalSpawnerMobsAtMixin}.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerChunkGenerationMixin {

    @Redirect(
            method = "spawnMobsForChunkGeneration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobs(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedRandomList;"))
    private static WeightedRandomList<MobSpawnSettings.SpawnerData> isekai$scopeChunkGenerationMobs(
            MobSpawnSettings settings,
            MobCategory category,
            ServerLevelAccessor levelAccessor,
            Holder<Biome> biome) {
        WeightedRandomList<MobSpawnSettings.SpawnerData> original = settings.getMobs(category);
        List<MobSpawnSettings.SpawnerData> scoped = MobSpawnScope.apply(
                levelAccessor.getLevel().dimension(), biome, category, original.unwrap());
        return scoped == original.unwrap() ? original : WeightedRandomList.create(scoped);
    }
}
