package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.validation.IsekaiValidator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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
 *   <li>{@code LevelEvent.Load} — hands the world seed to the level's {@code isekai_api:rule}
 *       biome source, the one point in 1.21.1 where a {@code BiomeSource} can obtain it.</li>
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

        // World-preset structural check: catches a consumer overriding
        // data/minecraft/worldgen/world_preset/normal.json without re-declaring Nether/End,
        // which silently breaks those dimensions. Warning only, never blocks.
        for (String w : com.kuronami.isekaiapi.validation.IsekaiValidator
                .validateWorldPresets(event.getServer().getResourceManager())) {
            IsekaiApi.LOGGER.warn("[Isekai] world_preset check: {}", w);
        }

        // After codec-level validation, do a deeper registry-existence pass on every
        // active worldshape: catches "minecraft:ocean_monument"-style typos where the
        // ResourceKey decodes fine but doesn't resolve to anything in the registry.
        checkActiveWorldshapeReferences(event.getServer());

        // Report which structures in this world use an Isekai structure type. Confirms a
        // consumer's data/<ns>/worldgen/structure/*.json decoded against the right codec and
        // is present in the live registry (a JSON that fails to decode is silently dropped,
        // so /place and /locate then report the id as unknown).
        logIsekaiStructures(event.getServer());
    }

    /** Log every entry of the world's structure registry whose {@code type} is one Isekai
     * registered. Neutral diagnostic — no per-consumer ids hardcoded. */
    private static void logIsekaiStructures(net.minecraft.server.MinecraftServer server) {
        try {
            var structureLookup = server.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var grounded = com.kuronami.isekaiapi.structure.IsekaiStructures.GROUNDED_TEMPLATE.get();
            var assembled = com.kuronami.isekaiapi.structure.IsekaiStructures.ASSEMBLED.get();
            int[] n = {0};
            structureLookup.listElements().forEach(ref -> {
                var t = ref.value().type();
                if (t == grounded || t == assembled) {
                    IsekaiApi.LOGGER.info("[Isekai] world structure present: {} (type {})",
                            ref.key().location(), (t == grounded ? "grounded_template" : "assembled"));
                    n[0]++;
                }
            });
            IsekaiApi.LOGGER.info("[Isekai] {} structure(s) using isekai_api types loaded in this world", n[0]);
        } catch (RuntimeException e) {
            IsekaiApi.LOGGER.warn("[Isekai] structure-presence check failed: {}", e.toString());
        }
    }

    /**
     * Hand the world seed to every {@code isekai_api:rule} biome source in the level that just
     * loaded.
     *
     * <p>This is the only point where a {@code BiomeSource} can learn the seed. 1.21.1 routes the
     * seed through {@code RandomState} — {@code ChunkMap} builds one from {@code level.getSeed()}
     * and hands only its {@code Climate.Sampler} down to
     * {@code BiomeSource#getNoiseBiome(int, int, int, Climate.Sampler)}, and the sampler carries
     * six density functions and a spawn target, no seed. So the source has to be told out of band,
     * before generation starts.
     *
     * <p>The timing is safe by construction: {@code MinecraftServer#createLevels} posts this event
     * for a level immediately after constructing it and before both {@code setInitialSpawn} (the
     * first thing that samples biomes) and {@code prepareLevels} (chunk generation). Client levels
     * are skipped — they have no chunk generator and receive their biomes from the server.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        if (biomeSource instanceof RuleBiomeSource source) {
            source.bindWorldSeed(level.getSeed());
            IsekaiApi.LOGGER.info("[Isekai] {}: rule biome source bound to the world seed",
                    level.dimension().location());
        } else {
            IsekaiApi.LOGGER.debug("[Isekai] {}: biome source is {}, no rule zones to seed",
                    level.dimension().location(), biomeSource.getClass().getSimpleName());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Drop the snapshot so the next world re-scans via the lazy path instead of
        // reusing this world's registry-derived data (datapacks may differ).
        IsekaiInternal.invalidateSnapshot();
        // Clear consumer-declared worldshapes so a Java-side declaration from this world
        // doesn't leak into the next one (JSON declarations re-apply on the next reload).
        IsekaiInternal.clearDeclarations();
        // Reset degraded flag + lenient-drop tally so the next world starts with a clean bill.
        com.kuronami.isekaiapi.impl.IsekaiHealth.reset();
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
