package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal access bridge for impl-class consumers (biome / structure modifiers,
 * evaluators, lifecycle hooks). Not part of the public consumer API — external mods
 * should go through {@link Isekai#query()} / {@link Isekai#remap()}.
 *
 * <p>This class owns the singleton query + remap implementations. The public
 * {@link Isekai} facade delegates to {@link #query()} / {@link #remap()} so that
 * snapshot publishing can be done without an unchecked downcast.
 */
@ApiStatus.Internal
public final class IsekaiInternal {

    private static final IsekaiQueryImpl QUERY = new IsekaiQueryImpl();
    private static final IsekaiRemapImpl REMAP = new IsekaiRemapImpl();

    private IsekaiInternal() {}

    /** Publicly-typed query singleton — exposed via {@link Isekai#query()}. */
    public static IsekaiQuery query() {
        return QUERY;
    }

    /** Publicly-typed remap singleton — exposed via {@link Isekai#remap()}. */
    public static IsekaiRemap remap() {
        return REMAP;
    }

    /** Publish a freshly-scanned snapshot to the query cache. */
    public static void publishSnapshot(VanillaRuleSnapshot snapshot) {
        QUERY.setSnapshot(snapshot);
    }

    /**
     * Drop the cached snapshot so the next {@link #currentSnapshot()} re-scans. Called on
     * server stop so a subsequent world (possibly a different datapack set) doesn't reuse
     * the previous world's scan via the lazy path.
     */
    public static void invalidateSnapshot() {
        QUERY.setSnapshot(VanillaRuleSnapshot.EMPTY);
    }

    private static final Object SCAN_LOCK = new Object();
    private static final ThreadLocal<Boolean> SCANNING = ThreadLocal.withInitial(() -> false);

    /**
     * Read the current snapshot, scanning lazily on first need.
     *
     * <p>Why lazy: NeoForge applies biome modifiers in {@code ServerLifecycleHooks.runModifiers},
     * which runs <em>before</em> {@code ServerAboutToStartEvent} is posted (verified in
     * 21.1.227 bytecode: {@code runModifiers} then {@code post(event)}). The biome modifier's
     * ADD/REMOVE phases need this snapshot for ore / feature Y-remap, so publishing it only
     * at the event would leave it {@link VanillaRuleSnapshot#EMPTY} exactly when the remap
     * runs — silently disabling the whole remap thesis. {@code currentServer} is already set
     * at the top of {@code handleServerAboutToStart}, so the first access during
     * {@code runModifiers} can scan on demand.
     *
     * <p>Safety: read-only registry walk (reads {@code getOriginalBiomeInfo}, immutable), so
     * the nested iteration inside the outer modifier-application loop is benign. A reentrancy
     * guard returns EMPTY if the scan itself somehow re-enters; any scan exception is
     * swallowed (returns EMPTY) so worldgen never crashes on our account.
     */
    public static VanillaRuleSnapshot currentSnapshot() {
        VanillaRuleSnapshot s = QUERY.getSnapshot();
        if (!s.isEmpty()) return s;
        if (SCANNING.get()) return s;  // re-entrant call mid-scan — avoid recursion
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return s;  // truly too early (e.g. reload at construction)
        synchronized (SCAN_LOCK) {
            s = QUERY.getSnapshot();
            if (!s.isEmpty()) return s;
            SCANNING.set(true);
            try {
                VanillaRuleSnapshot scanned = VanillaRuleSnapshot.scan(server);
                QUERY.setSnapshot(scanned);
                IsekaiApi.LOGGER.debug("[Isekai] lazy snapshot scan completed (empty={})", scanned.isEmpty());
                return scanned;
            } catch (RuntimeException e) {
                IsekaiApi.LOGGER.error("[Isekai] lazy snapshot scan failed; ore/feature remap "
                        + "inactive this session: {}", e.toString());
                return VanillaRuleSnapshot.EMPTY;
            } finally {
                SCANNING.set(false);
            }
        }
    }
}
