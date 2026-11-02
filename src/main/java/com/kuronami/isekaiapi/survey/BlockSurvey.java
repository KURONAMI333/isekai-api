package com.kuronami.isekaiapi.survey;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /isekai survey blocks}: how many trees (or other blocks) actually made it into each chunk.
 *
 * <p>{@link StructureSurvey} answers "why is my structure missing" from the seed alone. This answers
 * the other half of a density complaint — "the palm feature fires, but the world does not look like
 * a palm forest" — by generating the chunks and counting the blocks in them. Generation is the price:
 * unlike a structure survey this writes terrain to disk, exactly as walking there would.
 *
 * <h2>What counts as one tree</h2>
 *
 * <p>A tree is one <b>connected group of matching blocks inside one chunk</b>: blocks that share a
 * face (six-neighbour connectivity) belong to the same tree. This is the definition that survives the
 * two shapes an author actually writes:
 *
 * <ul>
 *   <li>a 2x2 trunk (dark oak, some palm species) has four log columns on the same layer. Counting
 *       "the lowest block of each column" would call it four trees; connectivity calls it one.</li>
 *   <li>a leaning trunk steps sideways. Its lower side columns have air underneath, so a
 *       "lowest block of each column" rule adds a phantom tree at every step; the blocks are still
 *       joined to the trunk, so connectivity calls it one.</li>
 * </ul>
 *
 * <p>The cost of the rule is at the chunk border: a tree whose blocks straddle two chunks is cut in
 * two, and each half is counted in its own chunk. A 2x2 trunk centred on a chunk corner therefore
 * reads as one tree in each of the four chunks. The error is bounded by the number of trees standing
 * on a border (a few per thousand chunks in a normal forest), and it keeps the per-chunk map honest:
 * every count describes blocks that exist in that chunk. Two separate trees whose trunks touch are
 * also one group; that is why the tool is meant for a log (or log tag), not for a leaf tag.
 *
 * <p>Alongside the tree count every chunk reports its matching <b>block</b> count, so "many short
 * palms" and "few tall palms" can be told apart.
 *
 * <p>Chunks are generated to {@code FEATURES}, the stage where features (trees) have been placed but
 * lighting and spawn work have not. Generation is <b>synchronous on the server thread</b>: a fresh
 * chunk costs tens of milliseconds, and {@code BlockSurveyRun} can only decide after each chunk
 * whether to keep going in this tick. A survey therefore lowers TPS for about one tick per chunk, and
 * only one runs per server at a time.
 *
 * <p>Each chunk is counted the moment its own {@code FEATURES} stage finishes, before its neighbours
 * generate. A tree rooted in a later chunk can still write logs or leaves into a chunk that was
 * already counted, so a per-chunk count is a lower bound — noticeably more so for a leaf tag than
 * for a log tag, because a crown reaches further across the border than a trunk does.
 */
@ApiStatus.Internal
public final class BlockSurvey {

    /** Radius in chunks used when the command is given no radius. */
    public static final int DEFAULT_RADIUS_CHUNKS = 8;

    /** Upper bound on the radius: 65x65 = 4,225 chunks at 32. */
    public static final int MAX_RADIUS_CHUNKS = 32;

    /** Histogram buckets, in display order. */
    private static final List<String> BUCKETS =
            List.of("0", "1-2", "3-5", "6-10", "11-20", "21-40", "41+");

    /** What to count, where, and which chunks to count. */
    public record Params(
            String target,
            Predicate<BlockState> matches,
            int radiusChunks,
            ChunkPos center,
            @Nullable HolderSet<Biome> biomes
    ) {}

    /** One counted chunk. */
    public record ChunkCounts(int chunkX, int chunkZ, int trees, int blocks) {}

    /**
     * The measured result. {@code countedChunks} is the square minus the chunks the biome filter
     * skipped and the chunks that failed to generate; {@code chunks} holds one entry per counted
     * chunk so a later tool can draw the spatial distribution.
     */
    public record Result(
            String target,
            String dimension,
            int radiusChunks,
            ChunkPos center,
            @Nullable String biomeFilter,
            int countedChunks,
            int skippedChunks,
            int failedChunks,
            int totalTrees,
            int totalBlocks,
            double meanTrees,
            int minTrees,
            double medianTrees,
            int maxTrees,
            int zeroChunks,
            double zeroShare,
            int topTenChunks,
            double topTenShare,
            Map<String, Integer> histogram,
            List<ChunkCounts> chunks,
            long elapsedMillis
    ) {

        public Result {
            histogram = Map.copyOf(histogram);
            chunks = List.copyOf(chunks);
        }

        public JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("target", target);
            root.addProperty("dimension", dimension);
            root.addProperty("radius_chunks", radiusChunks);
            JsonArray centerJson = new JsonArray();
            centerJson.add(center.x);
            centerJson.add(center.z);
            root.add("center_chunk", centerJson);
            if (biomeFilter != null) {
                root.addProperty("biome_filter", biomeFilter);
            }
            root.addProperty("counted_chunks", countedChunks);
            root.addProperty("skipped_chunks", skippedChunks);
            root.addProperty("failed_chunks", failedChunks);
            root.addProperty("tree_definition",
                    "one six-connected group of matching blocks inside one chunk");

            JsonObject trees = new JsonObject();
            trees.addProperty("total", totalTrees);
            trees.addProperty("mean_per_chunk", rate(meanTrees));
            trees.addProperty("min_per_chunk", minTrees);
            trees.addProperty("median_per_chunk", rate(medianTrees));
            trees.addProperty("max_per_chunk", maxTrees);
            trees.addProperty("zero_chunks", zeroChunks);
            trees.addProperty("zero_share", rate(zeroShare));
            trees.addProperty("top_ten_percent_chunks", topTenChunks);
            trees.addProperty("top_ten_percent_share", rate(topTenShare));
            JsonObject buckets = new JsonObject();
            for (String key : BUCKETS) {
                buckets.addProperty(key, histogram.getOrDefault(key, 0));
            }
            trees.add("histogram", buckets);
            root.add("trees", trees);

            JsonObject blocks = new JsonObject();
            blocks.addProperty("total", totalBlocks);
            blocks.addProperty("mean_per_chunk", rate(countedChunks == 0
                    ? 0.0 : (double) totalBlocks / countedChunks));
            root.add("blocks", blocks);

            JsonArray chunkArray = new JsonArray();
            for (ChunkCounts entry : chunks) {
                JsonObject chunk = new JsonObject();
                chunk.addProperty("x", entry.chunkX());
                chunk.addProperty("z", entry.chunkZ());
                chunk.addProperty("trees", entry.trees());
                chunk.addProperty("blocks", entry.blocks());
                chunkArray.add(chunk);
            }
            root.add("chunks", chunkArray);
            root.addProperty("elapsed_ms", elapsedMillis);
            return root;
        }

        public String toJsonString() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(toJson());
        }

        /** The chat form: totals, the shape of the distribution, and the elapsed time. */
        public List<String> summary() {
            List<String> lines = new ArrayList<>();
            lines.add("survey blocks " + target + " in " + dimension + " (radius " + radiusChunks
                    + " chunks = " + countedChunks + " counted"
                    + (skippedChunks == 0 ? "" : ", " + skippedChunks + " skipped by biome")
                    + (failedChunks == 0 ? "" : ", " + failedChunks + " failed") + ")");
            lines.add("  trees " + totalTrees + " total, mean " + oneDecimal(meanTrees)
                    + "/chunk; min " + minTrees + ", median " + oneDecimal(medianTrees)
                    + ", max " + maxTrees);
            lines.add("  empty chunks " + zeroChunks + " (" + percent(zeroShare)
                    + "); busiest top 10% (" + topTenChunks + " chunks) hold "
                    + percent(topTenShare) + " of all trees");
            lines.add("  blocks " + totalBlocks + " total, mean "
                    + oneDecimal(countedChunks == 0 ? 0.0 : (double) totalBlocks / countedChunks)
                    + "/chunk; " + histogramLine());
            lines.add("  elapsed " + elapsedMillis + " ms");
            return lines;
        }

        private String histogramLine() {
            StringBuilder builder = new StringBuilder("histogram");
            for (String key : BUCKETS) {
                Integer value = histogram.get(key);
                if (value != null) {
                    builder.append(' ').append(key).append(':').append(value);
                }
            }
            return builder.toString();
        }
    }

    private BlockSurvey() {}

    /**
     * Counts one chunk. Only sections whose palette may contain a match are scanned, so an air or
     * stone chunk costs almost nothing.
     */
    public static ChunkCounts count(ChunkAccess chunk, Predicate<BlockState> matches) {
        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        int volume = height * 256;
        boolean[] matching = new boolean[volume];
        int blockCount = 0;

        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(matches)) {
                continue;
            }
            int sectionBottom = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                int rowBase = (sectionBottom + y - minY) << 8;
                for (int z = 0; z < 16; z++) {
                    int row = rowBase | (z << 4);
                    for (int x = 0; x < 16; x++) {
                        if (matches.test(section.getBlockState(x, y, z))) {
                            matching[row | x] = true;
                            blockCount++;
                        }
                    }
                }
            }
        }

        int trees = 0;
        boolean[] visited = new boolean[volume];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < volume; start++) {
            if (!matching[start] || visited[start]) {
                continue;
            }
            trees++;
            visited[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                int y = current >> 8;
                int z = (current >> 4) & 15;
                int x = current & 15;
                if (x > 0) {
                    push(matching, visited, queue, current - 1);
                }
                if (x < 15) {
                    push(matching, visited, queue, current + 1);
                }
                if (z > 0) {
                    push(matching, visited, queue, current - 16);
                }
                if (z < 15) {
                    push(matching, visited, queue, current + 16);
                }
                if (y > 0) {
                    push(matching, visited, queue, current - 256);
                }
                if (y + 1 < height) {
                    push(matching, visited, queue, current + 256);
                }
            }
        }

        ChunkPos pos = chunk.getPos();
        return new ChunkCounts(pos.x, pos.z, trees, blockCount);
    }

    private static void push(boolean[] matching, boolean[] visited, Deque<Integer> queue, int index) {
        if (matching[index] && !visited[index]) {
            visited[index] = true;
            queue.add(index);
        }
    }

    /** Turns the per-chunk counts into the distribution the summary and JSON report. */
    public static Result summarize(Params params, String dimension, int skippedChunks,
                                   int failedChunks, List<ChunkCounts> chunks, long elapsedMillis) {
        int counted = chunks.size();
        int totalTrees = 0;
        int totalBlocks = 0;
        int zeroChunks = 0;
        int[] sorted = new int[counted];
        for (int i = 0; i < counted; i++) {
            ChunkCounts entry = chunks.get(i);
            totalTrees += entry.trees();
            totalBlocks += entry.blocks();
            sorted[i] = entry.trees();
            if (entry.trees() == 0) {
                zeroChunks++;
            }
        }
        Arrays.sort(sorted);

        double mean = counted == 0 ? 0.0 : (double) totalTrees / counted;
        int min = counted == 0 ? 0 : sorted[0];
        int max = counted == 0 ? 0 : sorted[counted - 1];
        double median = 0.0;
        if (counted > 0) {
            median = counted % 2 == 1
                    ? sorted[counted / 2]
                    : (sorted[counted / 2 - 1] + sorted[counted / 2]) / 2.0;
        }
        double zeroShare = counted == 0 ? 0.0 : (double) zeroChunks / counted;

        int topChunks = counted == 0 ? 0 : Math.max(1, (int) Math.ceil(counted * 0.1));
        int topSum = 0;
        for (int i = sorted.length - topChunks; i < sorted.length; i++) {
            topSum += sorted[i];
        }
        double topShare = totalTrees == 0 ? 0.0 : (double) topSum / totalTrees;

        Map<String, Integer> histogram = new LinkedHashMap<>();
        for (String bucket : BUCKETS) {
            histogram.put(bucket, 0);
        }
        for (int value : sorted) {
            histogram.merge(bucketOf(value), 1, Integer::sum);
        }

        return new Result(params.target(), dimension, params.radiusChunks(), params.center(),
                params.biomes() == null ? null : describeBiomes(params.biomes()), counted,
                skippedChunks, failedChunks, totalTrees, totalBlocks, mean, min, median, max,
                zeroChunks, zeroShare, topChunks, topShare, histogram, chunks, elapsedMillis);
    }

    private static String bucketOf(int trees) {
        if (trees == 0) {
            return "0";
        }
        if (trees <= 2) {
            return "1-2";
        }
        if (trees <= 5) {
            return "3-5";
        }
        if (trees <= 10) {
            return "6-10";
        }
        if (trees <= 20) {
            return "11-20";
        }
        if (trees <= 40) {
            return "21-40";
        }
        return "41+";
    }

    /**
     * The biome filter as written in the report: a single biome id, or {@code #namespace:tag} for a
     * tag. A tag is shown with its tag key, which is what the command argument held.
     */
    private static String describeBiomes(HolderSet<Biome> biomes) {
        if (biomes instanceof HolderSet.Named<Biome> named) {
            return "#" + named.key().location();
        }
        List<String> ids = new ArrayList<>();
        biomes.forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.location().toString())));
        return ids.isEmpty() ? "unknown" : String.join(",", ids);
    }

    /**
     * File name under {@code <world>/isekai/survey/}. The biome filter is part of the name when one
     * is given, so two runs of the same target on different biomes do not overwrite each other.
     */
    public static String fileName(Params params) {
        StringBuilder name = new StringBuilder("blocks-").append(sanitize(params.target()));
        if (params.biomes() != null) {
            name.append('-').append(sanitize(describeBiomes(params.biomes())));
        }
        return name.append("-r").append(params.radiusChunks()).append(".json").toString();
    }

    private static String sanitize(String value) {
        return value.replace('#', ' ').trim().replace(':', '_').replace('/', '_').replace(' ', '_');
    }

    /**
     * A statistic as a fixed-point decimal. A plain double can print as {@code 7.8E-4}, which is
     * valid JSON but unreadable in a file meant to be looked at.
     */
    private static BigDecimal rate(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }
}
