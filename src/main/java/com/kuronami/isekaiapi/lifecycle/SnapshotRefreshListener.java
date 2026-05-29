package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Rebuilds the {@link VanillaRuleSnapshot} on every datapack reload. Without this, the
 * snapshot built once at {@code ServerAboutToStartEvent} would go stale whenever the
 * operator runs {@code /reload}: tag membership, biome features, structure sets,
 * and mob spawn settings can all change.
 *
 * <p>Implements {@link PreparableReloadListener} directly (not via
 * {@code SimplePreparableReloadListener}) because the snapshot scan needs the
 * {@link net.minecraft.server.MinecraftServer}, not the {@link ResourceManager} alone —
 * the scanner walks {@code registryAccess()} and {@code getAllLevels()}. Both phases
 * (prepare + apply) become no-ops on the server thread except for the apply step which
 * triggers the rescan once registries have settled.
 *
 * <p>Cost: O(features + structures + biomes) per reload. Vanilla 1.21.1 has ~500 features,
 * ~80 structures, ~70 biomes — well under a second on typical hardware. Heavier on
 * mod-loaded packs but still bounded by registry walks.
 */
@ApiStatus.Internal
public final class SnapshotRefreshListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier stage,
                                           ResourceManager rm,
                                           ProfilerFiller preparationsProfiler,
                                           ProfilerFiller reloadProfiler,
                                           Executor backgroundExecutor,
                                           Executor gameExecutor) {
        // Prepare phase: nothing — we don't read filesystem here, the rebuild reads from
        // the live registry access. Just pass through the barrier and run the rescan on
        // the game thread after every other listener has settled.
        return CompletableFuture.<Void>supplyAsync(() -> null, backgroundExecutor)
                .thenCompose(stage::wait)
                .thenAcceptAsync(unused -> rebuild(), gameExecutor);
    }

    private void rebuild() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            IsekaiApi.LOGGER.warn("[Isekai] reload-time snapshot rebuild skipped: no server context");
            return;
        }
        IsekaiApi.LOGGER.info("[Isekai] datapack reload: rebuilding VanillaRuleSnapshot");
        var snapshot = VanillaRuleSnapshot.scan(server);
        IsekaiInternal.publishSnapshot(snapshot);
        IsekaiApi.LOGGER.info("[Isekai] snapshot rebuilt (empty={})", snapshot.isEmpty());
    }
}
