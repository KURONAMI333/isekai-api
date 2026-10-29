package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.impl.MobSpawnScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Applies the per-(dimension, biome) mob spawn rule to every natural spawn attempt.
 *
 * <p>In 1.21.1 the biome spawn list is read by {@code NaturalSpawner#mobsAt}: it resolves the
 * biome, asks the chunk generator's {@code getMobsAt} (which carries no level and therefore no
 * dimension), then hands the result to NeoForge's {@code PotentialSpawns} event. This is the
 * first point where all three of level, biome and the final list are in hand, so the rule is
 * applied at its RETURN — after other mods' additions, which the rule must also see.
 *
 * <p>{@code ChunkGenerator#getMobsAt} itself is left alone: its 1.21.1 signature has no level,
 * and hooking it would need the dimension smuggled in from somewhere else. Since every caller
 * of {@code getMobsAt} goes through {@code mobsAt}, hooking here also covers custom chunk
 * generators that override {@code getMobsAt}.
 *
 * <p>The nether fortress override is deliberately skipped: {@code mobsAt} returns it before
 * reading the biome list, and 2.x never scoped fortress spawns either.
 *
 * <p>Only a rule-bearing pair is touched; every other spawn pays one map lookup. The returned
 * list keeps the original {@link MobSpawnSettings.SpawnerData} instances whose weight did not
 * change, because {@code NaturalSpawner#canSpawnMobAt} re-reads the list and checks
 * {@code contains(data)} by identity.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMobsAtMixin {

    @Inject(method = "mobsAt", at = @At("RETURN"), cancellable = true)
    private static void isekai$scopeMobsAt(ServerLevel level,
                                            StructureManager structureManager,
                                            ChunkGenerator generator,
                                            MobCategory category,
                                            BlockPos pos,
                                            @Nullable Holder<Biome> biome,
                                            CallbackInfoReturnable<WeightedRandomList<MobSpawnSettings.SpawnerData>> cir) {
        if (MobSpawnScope.isEmpty()) return;
        WeightedRandomList<MobSpawnSettings.SpawnerData> original = cir.getReturnValue();
        if (original == null) return;
        // Resolve the pair before the fortress probe: a dimension/biome without a rule pays no
        // structure lookup, and the probe only matters when this hook would change the list.
        Holder<Biome> target = biome != null ? biome : level.getBiome(pos);
        if (!MobSpawnScope.hasRulesFor(level.dimension(), target)) return;
        // The fortress override never went through the biome list; leave it as 2.x did.
        if (NaturalSpawner.isInNetherFortressBounds(pos, level, category, structureManager)) return;
        // Another mod's PotentialSpawns handler can cancel to an empty list, which reaches us
        // exactly like a genuinely empty one; never resurrect it with the rule's additions.
        if (original.isEmpty()) return;

        List<MobSpawnSettings.SpawnerData> scoped =
                MobSpawnScope.apply(level.dimension(), target, category, original.unwrap());
        if (scoped != original.unwrap()) {
            cir.setReturnValue(WeightedRandomList.create(scoped));
        }
    }
}
