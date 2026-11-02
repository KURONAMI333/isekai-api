package com.kuronami.isekaiapi.survey;

import com.kuronami.isekaiapi.IsekaiApi;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Drives {@link BlockSurvey} over ticks so a whole square of chunks is not generated inside one
 * command call.
 *
 * <p><b>Chunk generation is synchronous.</b> {@code level.getChunk(pos, FEATURES, true)} returns a
 * chunk that is already complete, or blocks the server thread until the generator has produced it;
 * a fresh chunk costs tens of milliseconds. The tick loop therefore does not make generation cheap:
 * it only decides when to stop, by checking a time budget after each chunk and returning to the
 * server for that tick. A survey of N chunks can hold the server for about N ticks, each one a
 * normal tick plus that chunk, so a run visibly lowers TPS and a large square can reach the
 * server watchdog.
 *
 * <p>Because of that cost, <b>one blocks survey runs per server at a time</b>
 * ({@link #refusalReason}): two would take turns inside the same ticks and double the time each tick
 * spends inside chunk generation.
 *
 * <p>The ticket a chunk load takes expires on the next tick, so nothing stays pinned after the
 * survey — only the terrain that was written to disk, exactly as if a player had walked there.
 *
 * <p>A run belongs to one {@link ServerLevel} and is started on the server thread (the command does
 * that); the tick event also runs on the server thread, so the queue needs no locking. A run that is
 * still pending when the server stops is dropped.
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class BlockSurveyRun {

    /**
     * Time after which the tick loop stops starting another chunk, in nanoseconds (8 ms).
     *
     * <p>This cannot cut inside one chunk's generation: the check runs after a chunk is back, so a
     * tick that generates a fresh chunk always overruns by that chunk's cost (tens of
     * milliseconds). The budget only keeps a saved chunk from being followed by another one in the
     * same tick.
     */
    private static final long TICK_BUDGET_NANOS = 8_000_000L;

    private static final Map<MinecraftServer, List<BlockSurveyRun>> RUNS = new HashMap<>();

    private final ServerLevel level;
    private final BlockSurvey.Params params;
    private final Deque<ChunkPos> pending;
    private final List<BlockSurvey.ChunkCounts> counts = new ArrayList<>();
    private final long startedNanos;
    private final Consumer<BlockSurvey.Result> onDone;
    private final Consumer<Throwable> onError;
    private int skipped;
    private int failed;

    private BlockSurveyRun(ServerLevel level, BlockSurvey.Params params,
                           Deque<ChunkPos> pending, Consumer<BlockSurvey.Result> onDone,
                           Consumer<Throwable> onError) {
        this.level = level;
        this.params = params;
        this.pending = pending;
        this.onDone = onDone;
        this.onError = onError;
        this.startedNanos = System.nanoTime();
    }

    /**
     * Why a new run cannot start on this server, or {@code null} when it can.
     *
     * <p>Generation happens on the server thread, so two surveys would interleave inside the same
     * ticks and each tick would wait for both chunks. One at a time keeps the stall predictable and
     * makes the report the only thing the server is spending ticks on.
     */
    public static String refusalReason(MinecraftServer server) {
        List<BlockSurveyRun> runs = RUNS.get(server);
        if (runs == null || runs.isEmpty()) {
            return null;
        }
        BlockSurveyRun running = runs.get(0);
        return "a blocks survey is already running on this server ("
                + running.pending.size() + " chunk(s) left, radius " + running.params.radiusChunks()
                + " around chunk " + running.params.center().x + "," + running.params.center().z
                + "); chunk generation runs on the server thread, so two at once would double the"
                + " time each tick spends generating";
    }

    /**
     * Queues a survey and returns how many chunks it will generate. Biome filtering happens here,
     * from the biome source, so a chunk whose biome is filtered out is never generated.
     *
     * @return the queued chunk count, or {@code -1} when a survey is already running on this server
     *         (see {@link #refusalReason})
     */
    public static int start(ServerLevel level, BlockSurvey.Params params,
                            Consumer<BlockSurvey.Result> onDone, Consumer<Throwable> onError) {
        if (refusalReason(level.getServer()) != null) {
            return -1;
        }
        Deque<ChunkPos> pending = new ArrayDeque<>();
        int skipped = 0;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        ChunkPos center = params.center();
        for (int cx = center.x - params.radiusChunks(); cx <= center.x + params.radiusChunks(); cx++) {
            for (int cz = center.z - params.radiusChunks(); cz <= center.z + params.radiusChunks(); cz++) {
                if (params.biomes() != null
                        && !biomeMatches(level, generator, randomState, cx, cz, params.biomes())) {
                    skipped++;
                    continue;
                }
                pending.add(new ChunkPos(cx, cz));
            }
        }
        BlockSurveyRun run = new BlockSurveyRun(level, params, pending, onDone, onError);
        run.skipped = skipped;
        RUNS.computeIfAbsent(level.getServer(), server -> new ArrayList<>()).add(run);
        return pending.size();
    }

    /** Whether the chunk's centre column sits in one of {@code biomes}, using the seed only. */
    private static boolean biomeMatches(ServerLevel level, ChunkGenerator generator,
                                        RandomState randomState, int chunkX, int chunkZ,
                                        net.minecraft.core.HolderSet<Biome> biomes) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int surface = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
                randomState);
        int y = Mth.clamp(surface, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                randomState.sampler());
        return biomes.contains(biome);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<BlockSurveyRun> runs = RUNS.get(event.getServer());
        if (runs == null) {
            return;
        }
        for (Iterator<BlockSurveyRun> iterator = runs.iterator(); iterator.hasNext(); ) {
            if (iterator.next().step()) {
                iterator.remove();
            }
        }
        if (runs.isEmpty()) {
            RUNS.remove(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNS.remove(event.getServer());
    }

    /** Returns {@code true} when this run is finished and should be dropped. */
    private boolean step() {
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        while (!pending.isEmpty()) {
            ChunkPos pos = pending.removeFirst();
            try {
                ChunkAccess chunk = level.getChunk(pos.x, pos.z, ChunkStatus.FEATURES, true);
                counts.add(BlockSurvey.count(chunk, params.matches()));
            } catch (RuntimeException e) {
                failed++;
                IsekaiApi.LOGGER.warn("[Isekai] survey could not generate chunk {},{}", pos.x, pos.z, e);
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (!pending.isEmpty()) {
            return false;
        }
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        BlockSurvey.Result result = BlockSurvey.summarize(params,
                level.dimension().location().toString(), skipped, failed, counts, elapsedMillis);
        try {
            onDone.accept(result);
        } catch (RuntimeException e) {
            IsekaiApi.LOGGER.error("[Isekai] survey report failed", e);
            onError.accept(e);
        }
        return true;
    }

    /** Whether a run is still queued on this server (used to refuse a second survey). */
    public static boolean hasPending(MinecraftServer server) {
        List<BlockSurveyRun> runs = RUNS.get(server);
        return runs != null && !runs.isEmpty();
    }
}
