package com.kuronami.isekaiapi.survey;

import com.kuronami.isekaiapi.IsekaiApi;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Runs a {@link BiomeSurvey} on a worker thread and hands the result back on the server thread.
 *
 * <p><b>Nothing is generated, so this needs no tick loop.</b> The biome source and the climate
 * sampler are pure functions of the seed and the coordinate, so the whole disc can be read from
 * {@link Util#backgroundExecutor()} while the server keeps ticking: no chunk, no ticket, no disk.
 * Only the two inputs are taken on the server thread first ({@link #start}), because
 * {@code ServerChunkCache#randomState} belongs to the level; the worker only reads clamped longs and
 * an immutable {@link BiomeSource}.
 *
 * <p>The report is written and printed back on the server thread ({@code server.execute}), because
 * the file goes through the command source and chat must not be touched from a worker.
 *
 * <p><b>One survey per server at a time</b> ({@link #refusalReason}). A radius-4096 scan reads 3.3
 * million cells and holds its arrays for the duration; two at once would double the memory peak and
 * the time the server spends on worker threads for no gain.
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class BiomeSurveyRun {

    /** The run in flight per server, used for the refusal message and for cleanup. */
    private static final Map<MinecraftServer, BiomeSurveyRun> RUNS = new HashMap<>();

    private final BiomeSurvey.Params params;
    private volatile boolean running = true;

    private BiomeSurveyRun(BiomeSurvey.Params params) {
        this.params = params;
    }

    /**
     * Why a new run cannot start on this server, or {@code null} when it can. The shape of the
     * message matches {@link BlockSurveyRun#refusalReason}, so a player who sees both knows which
     * survey is holding the slot.
     */
    public static String refusalReason(MinecraftServer server) {
        BiomeSurveyRun running = RUNS.get(server);
        if (running == null) {
            return null;
        }
        BiomeSurvey.Params params = running.params;
        return "a biomes survey is already running on this server (radius " + params.radiusBlocks()
                + " around " + params.centerX() + "," + params.centerY() + "," + params.centerZ()
                + "); it reads millions of cells on a worker thread, so two at once would double the"
                + " memory peak";
    }

    /**
     * Starts a scan on a worker thread.
     *
     * @return {@code 1} when queued, {@code -1} when a biomes survey is already running on this
     *         server (see {@link #refusalReason}); callers print the refusal themselves
     */
    public static int start(ServerLevel level, BiomeSurvey.Params params,
                            Consumer<BiomeSurvey.Result> onDone, Consumer<Throwable> onError) {
        MinecraftServer server = level.getServer();
        if (refusalReason(server) != null) {
            return -1;
        }
        // Read the level-owned inputs here, on the server thread, so the worker touches neither the
        // level nor the chunk cache.
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        String dimension = level.dimension().location().toString();

        BiomeSurveyRun run = new BiomeSurveyRun(params);
        RUNS.put(server, run);
        CompletableFuture<BiomeSurvey.Result> future;
        try {
            future = CompletableFuture.supplyAsync(
                    () -> BiomeSurvey.run(source, sampler, dimension, params),
                    Util.backgroundExecutor());
        } catch (RuntimeException e) {
            // The executor is shut down, which only happens while the server is stopping.
            RUNS.remove(server, run);
            IsekaiApi.LOGGER.warn("[Isekai] survey biomes could not start", e);
            return 0;
        }
        future.whenComplete((result, error) -> server.execute(() -> {
            run.running = false;
            RUNS.remove(server, run);
            if (error != null) {
                IsekaiApi.LOGGER.error("[Isekai] survey biomes failed", error);
                onError.accept(unwrap(error));
                return;
            }
            try {
                onDone.accept(result);
            } catch (RuntimeException e) {
                IsekaiApi.LOGGER.error("[Isekai] survey report failed", e);
                onError.accept(e);
            }
        }));
        return 1;
    }

    /** Whether a run is still in flight on this server. */
    public static boolean hasPending(MinecraftServer server) {
        BiomeSurveyRun run = RUNS.get(server);
        return run != null && run.running;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BiomeSurveyRun run = RUNS.remove(event.getServer());
        if (run != null) {
            // The worker keeps reading until it finishes; its callback is dropped because the
            // server no longer executes tasks.
            run.running = false;
        }
    }

    /** Drops the {@link CompletionException} wrapper so the command can print the real cause. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
    }
}