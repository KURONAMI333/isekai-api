package com.kuronami.isekaiapi.event;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.impl.MobSpawnScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Enforces a worldshape's mob exclusions on spawn paths that never read a biome spawn list.
 *
 * <p>{@code NaturalSpawnerMobsAtMixin} / {@code NaturalSpawnerChunkGenerationMixin} filter the
 * lists vanilla builds, which covers natural spawning and the initial creature population of a
 * chunk. A patrol, a spawner, or a structure builds its mob directly and never touches those
 * lists, so an excluded type could still appear there. {@link FinalizeSpawnEvent} is the one
 * point every mob passes through before it enters the level: vanilla calls
 * {@code Mob#finalizeSpawn}, NeoForge's coremod turns that call into the event, and
 * {@link FinalizeSpawnEvent#setSpawnCancelled} makes the eventual {@code addFreshEntity} fail
 * (checked by NeoForge's {@code builtinMobSpawnBlocker} on {@code EntityJoinLevelEvent}).
 *
 * <p>{@code PatrolSpawner#spawnPatrolMember} is the concrete case this exists for: it calls
 * {@code checkSpawnRules} and {@code finalizeSpawn} directly, then
 * {@code addFreshEntityWithPassengers}, so the list-filtering mixins never see it.
 *
 * <p>Only an exclusion is enforced here. Weights and additions are meaningless without a list
 * and stay with the mixins, so this handler is a no-op unless the entity type is in the rule's
 * {@code removed} set for this exact (dimension, biome). It runs on every finalized spawn, so
 * it checks {@link MobSpawnScope#isEmpty()} first and never logs.
 *
 * <p>This deliberately does not restrict by {@code MobSpawnType}: an excluded type is treated
 * as excluded for every route (patrol, spawner, structure, command) in the declaring
 * dimension x biome, which is what "no zombies in my plains" means.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID)
@ApiStatus.Internal
public final class MobSpawnVetoHandler {

    private MobSpawnVetoHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (MobSpawnScope.isEmpty()) return;
        ServerLevelAccessor accessor = event.getLevel();
        if (accessor == null) return;
        ServerLevel level = accessor.getLevel();
        if (level == null) return;

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Holder<Biome> biome = level.getBiome(pos);
        if (!MobSpawnScope.isRemoved(level.dimension(), biome, event.getEntity().getType())) return;

        event.setSpawnCancelled(true);
    }
}
