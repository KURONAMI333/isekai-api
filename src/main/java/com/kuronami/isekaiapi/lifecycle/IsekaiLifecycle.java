package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.validation.IsekaiValidator;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Server lifecycle hooks:
 * <ul>
 *   <li>{@code ServerAboutToStartEvent} — re-scans the vanilla rule registries and
 *       publishes the result (authoritative refresh + datapack validation). Note: this
 *       fires AFTER {@code ServerLifecycleHooks.runModifiers}, so biome modifiers have
 *       already run; the snapshot they consume for ore/feature remap is produced earlier
 *       by the lazy scan in {@link IsekaiInternal#currentSnapshot()}.</li>
 *   <li>{@code ServerStoppingEvent} — invalidates the cached snapshot so the next world
 *       (possibly a different datapack set) re-scans rather than reusing a stale one.</li>
 *   <li>{@code AddReloadListenerEvent} — registers the two
 *       {@link IsekaiReloadListener} instances (worldshape / layered_worldshape JSON
 *       loading) plus {@link SnapshotRefreshListener} (rebuild the snapshot on every
 *       datapack reload so tag indices, biome step indices, and per-dim VerticalRange
 *       overrides stay current).</li>
 * </ul>
 */
@EventBusSubscriber(modid = IsekaiApi.MODID)
@ApiStatus.Internal
public final class IsekaiLifecycle {

    private IsekaiLifecycle() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        IsekaiApi.LOGGER.info("[Isekai] ServerAboutToStartEvent: scanning vanilla worldgen rules");
        var snapshot = VanillaRuleSnapshot.scan(event.getServer());
        IsekaiInternal.publishSnapshot(snapshot);
        IsekaiApi.LOGGER.info("[Isekai] Snapshot published (empty={}); query API now backed by cache",
                snapshot.isEmpty());

        // Auto-validate every consumer's isekai/ datapack directory: gives consumers
        // immediate feedback on typos, decode failures, and cross-field invariants the
        // first time their server boots. Doesn't block startup (lenient) — surfaces via
        // server log only. /isekai validate stays available for on-demand re-checks.
        autoValidateAllNamespaces(event.getServer().getResourceManager());

        // After codec-level validation, do a deeper registry-existence pass on every
        // active worldshape: catches "minecraft:ocean_monument"-style typos where the
        // ResourceKey decodes fine but doesn't resolve to anything in the registry.
        checkActiveWorldshapeReferences(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Drop the snapshot so the next world re-scans via the lazy path instead of
        // reusing this world's registry-derived data (datapacks may differ).
        IsekaiInternal.invalidateSnapshot();
    }

    /**
     * Iterate every active descriptor and report registry references that don't resolve.
     * Runs after VanillaRuleSnapshot.scan so the registries are guaranteed live. Reports
     * are WARNs because the missing reference is silently no-op at chunk gen — making it
     * a hard failure would block server start for what's often a typo in a one-off
     * exclusion list.
     */
    private static void checkActiveWorldshapeReferences(net.minecraft.server.MinecraftServer server) {
        var dims = com.kuronami.isekaiapi.api.Isekai.remap().getDeclaredDimensions();
        int totalMissing = 0;
        for (var dim : dims) {
            // Isolate each dimension: one dim whose registry lookup throws (an addon
            // stripping a vanilla registry, a not-yet-populated modded registry) must not
            // suppress the ref-check for every other dimension, nor escape this handler.
            try {
                var descriptor = com.kuronami.isekaiapi.api.Isekai.remap().getActiveDescriptor(dim).orElse(null);
                if (descriptor == null) continue;
                var missing = com.kuronami.isekaiapi.validation.RegistryRefChecker.findMissing(descriptor, server);
                for (String entry : missing) {
                    IsekaiApi.LOGGER.warn("[Isekai] registry-ref check ({}): {}", dim.location(), entry);
                }
                totalMissing += missing.size();
            } catch (RuntimeException e) {
                IsekaiApi.LOGGER.error("[Isekai] registry-ref check ({}) aborted: {}",
                        dim.location(), e.toString());
            }
        }
        if (totalMissing == 0 && !dims.isEmpty()) {
            IsekaiApi.LOGGER.info("[Isekai] registry-ref check: {} dim(s), 0 missing references",
                    dims.size());
        } else if (totalMissing > 0) {
            IsekaiApi.LOGGER.warn("[Isekai] registry-ref check: {} missing reference(s) across {} dim(s) — see above",
                    totalMissing, dims.size());
        }
    }

    /**
     * Collect every namespace that has any file under {@code isekai/worldshape/} or
     * {@code isekai/layered_worldshape/} and run {@link IsekaiValidator#validateNamespace}
     * for each. Empty result is logged at INFO so consumers know the validator did look.
     */
    private static void autoValidateAllNamespaces(net.minecraft.server.packs.resources.ResourceManager rm) {
        Set<String> namespaces = new HashSet<>();
        for (var dir : new String[]{"isekai/worldshape", "isekai/layered_worldshape"}) {
            for (ResourceLocation id : rm.listResources(dir, p -> p.getPath().endsWith(".json")).keySet()) {
                namespaces.add(id.getNamespace());
            }
        }
        if (namespaces.isEmpty()) {
            IsekaiApi.LOGGER.info("[Isekai] auto-validate: no isekai/ datapack content found");
            return;
        }
        int totalErrors = 0;
        int totalFiles = 0;
        for (String ns : namespaces) {
            var result = IsekaiValidator.validateNamespace(ns, rm);
            totalFiles += result.filesChecked();
            totalErrors += result.errorsFound();
            for (String err : result.errors()) {
                IsekaiApi.LOGGER.warn("[Isekai] auto-validate({}): {}", ns, err);
            }
        }
        if (totalErrors == 0) {
            IsekaiApi.LOGGER.info("[Isekai] auto-validate: {} file(s) across {} namespace(s) — all OK",
                    totalFiles, namespaces.size());
        } else {
            IsekaiApi.LOGGER.warn("[Isekai] auto-validate: {} error(s) across {} file(s) in {} namespace(s) — see above",
                    totalErrors, totalFiles, namespaces.size());
        }
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        // Register one listener per JSON directory so each runs on its own worker batch.
        event.addListener(IsekaiReloadListener.forSingleLayer());
        event.addListener(IsekaiReloadListener.forLayered());
        // Refresh the VanillaRuleSnapshot so tag indices, biome step indices, and
        // per-dim VerticalRange overrides reflect the post-reload registry state.
        event.addListener(new SnapshotRefreshListener());
        IsekaiApi.LOGGER.info("[Isekai] reload listeners registered: {} + {} + snapshot-refresh",
                IsekaiReloadListener.WORLDSHAPE_DIR, IsekaiReloadListener.LAYERED_DIR);
    }
}
