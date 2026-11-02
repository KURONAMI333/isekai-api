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
 * Drives {@link BlockSurvey} over ticks so generating a square of chunks never takes one long tick.
 *
 * <p>Chunk generation is requested one chunk at a time to {@code FEATURES} and counted immediately.
 * Each server tick gets a small time budget (a few chunks); the square is finished over as many ticks
 * as it needs. The ticket a chunk load takes expires on the next tick, so nothing stays pinned after
 * the survey — only the terrain that was written to disk, exactly as if a player had walked there.
 *
 * <p>A run belongs to one {@link ServerLevel} and is started on the server thread (the command does
 * that); the tick event also runs on the server thread, so the queue needs no locking. A run that is
 * still pending when the server stops is dropped.
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class BlockSurveyRun {

    /** Time a single tick may spend generating chunks for surveys, in nanoseconds (8 ms). */
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
     * Queues a survey and returns how many chunks it will generate. Biome filtering happens here,
     * from the biome source, so a chunk whose biome is filtered out is never generated.
     */
    public static int start(ServerLevel level, BlockSurvey.Params params,
                            Consumer<BlockSurvey.Result> onDone, Consumer<Throwable> onError) {
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

    /** For tests: whether a run is still queued on this server. */
    public static boolean hasPending(MinecraftServer server) {
        List<BlockSurveyRun> runs = RUNS.get(server);
        return runs != null && !runs.isEmpty();
    }
}
