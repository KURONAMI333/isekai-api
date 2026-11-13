package com.kuronami.isekaiapi.survey;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai survey biomes}: how much of a disc is each biome, how big the patches are, and
 * where the climate sits.
 *
 * <p>The three answers come from the same scan:
 *
 * <ul>
 *   <li><b>Share</b> — the biome at every cell of the 4-block lattice inside the disc, so
 *       "20% desert" is a count of cells, not an estimate.</li>
 *   <li><b>Patch size</b> — cells of one biome that share a face form one cluster. Each cluster's
 *       area (cells x 16 blocks<sup>2</sup>) is reported as the diameter of a circle with that area,
 *       {@code 2*sqrt(area/pi)}: a round patch of radius 64 reads as 128, and a long thin strip
 *       reads as the width it would have at the same area. A cluster that touches the rim of the
 *       disc is counted but left out of the median and p90, because its true size continues past
 *       where the scan stopped; the report says how many were excluded.</li>
 *   <li><b>Climate</b> — the six vanilla noise axes at the sampled positions, unquantized, as
 *       percentiles. The axis names are the JSON names and the F3 names both, because F3 shows
 *       vegetation and ridges where the code says humidity and weirdness.</li>
 * </ul>
 *
 * <p><b>Nothing is generated.</b> {@link BiomeSource#getNoiseBiome} and
 * {@link Climate.Sampler#sample} are pure functions of the seed and the coordinate, so this is the
 * same answer chunk generation would write, at one sample per 4 blocks and without touching the
 * chunk cache, the worldgen pipeline or the disk. That is what lets it run on a worker thread
 * ({@link BiomeSurveyRun}) while the server keeps ticking.
 *
 * <p><b>One Y plane.</b> Biomes are 3D in vanilla (cave biomes, the nether's vertical bands), so the
 * scan reads the biome at one quart Y — the command's {@code y}, which defaults to the centre's Y.
 * A radius-4096 disc holds 3.3 million cells; every cell is sampled for the biome share, and the
 * climate arrays are subsampled by {@link #climateStride(int)} so their memory stays bounded. The
 * stride is written into the JSON.
 */
@ApiStatus.Internal
public final class BiomeSurvey {

    /** The smallest radius the command accepts, in blocks. */
    public static final int MIN_RADIUS_BLOCKS = 16;

    /** The largest radius the command accepts: the disc inside an 8,193-block square is 3.3M cells. */
    public static final int MAX_RADIUS_BLOCKS = 4096;

    /** One sample covers a 4x4 column of blocks. */
    public static final int CELL_BLOCKS = 4;

    /** One sample's area in blocks squared. */
    public static final int CELL_AREA_BLOCKS = CELL_BLOCKS * CELL_BLOCKS;

    /** Climate samples kept per axis; above this the scan takes every nth cell. */
    private static final int CLIMATE_SAMPLE_CAP = 262_144;

    /** Percentiles reported for every climate axis, in percent. */
    public static final int[] PERCENTILES = {1, 5, 10, 25, 50, 75, 90, 95, 99};

    /** The six vanilla climate axes, in the order the report lists them. */
    private static final List<Axis> AXES = List.of(
            new Axis("temperature", "temperature", Climate.TargetPoint::temperature),
            new Axis("humidity", "vegetation", Climate.TargetPoint::humidity),
            new Axis("continentalness", "continents", Climate.TargetPoint::continentalness),
            new Axis("erosion", "erosion", Climate.TargetPoint::erosion),
            new Axis("depth", "depth", Climate.TargetPoint::depth),
            new Axis("weirdness", "ridges", Climate.TargetPoint::weirdness));

    /** What to scan. {@code centerY} is the Y the biome and climate are read at. */
    public record Params(int centerX, int centerY, int centerZ, int radiusBlocks) {}

    /** One biome's share of the disc. */
    public record BiomeShare(String biome, int cells, double share) {}

    /**
     * One biome's patch statistics. {@code clusters} counts every patch that has a cell inside the
     * disc; {@code edgeClusters} of those touch the rim and are left out of the two diameters, which
     * are computed over {@code measuredClusters = clusters - edgeClusters} patches.
     */
    public record BiomeClusters(
            String biome,
            int clusters,
            int measuredClusters,
            int edgeClusters,
            double medianDiameterBlocks,
            double p90DiameterBlocks
    ) {}

    /** One climate axis: its JSON name, its F3 name, and values in {@link #PERCENTILES} order. */
    public record ClimateAxis(String axis, String f3Name, int samples, List<Double> percentiles) {

        public ClimateAxis {
            percentiles = List.copyOf(percentiles);
        }
    }

    /** The measured result. */
    public record Result(
            String dimension,
            int centerX,
            int centerY,
            int centerZ,
            int radiusBlocks,
            int sampledCells,
            int climateStride,
            List<BiomeShare> shares,
            List<BiomeClusters> clusters,
            List<ClimateAxis> climate,
            long elapsedMillis
    ) {

        public Result {
            shares = List.copyOf(shares);
            clusters = List.copyOf(clusters);
            climate = List.copyOf(climate);
        }

        public JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("dimension", dimension);
            JsonArray center = new JsonArray();
            center.add(centerX);
            center.add(centerY);
            center.add(centerZ);
            root.add("center", center);
            root.addProperty("radius_blocks", radiusBlocks);
            root.addProperty("sampled_cells", sampledCells);
            root.addProperty("cell_blocks", CELL_BLOCKS);
            root.addProperty("cell_area_blocks", CELL_AREA_BLOCKS);
            root.addProperty("climate_stride", climateStride);
            root.addProperty("equivalent_diameter", "2*sqrt(area_blocks/pi)");
            root.addProperty("cluster_definition",
                    "face-connected cells of one biome on the 4-block lattice;"
                            + " clusters touching the scanned rim are excluded from the diameters");

            JsonArray shareArray = new JsonArray();
            for (BiomeShare entry : shares) {
                JsonObject share = new JsonObject();
                share.addProperty("biome", entry.biome());
                share.addProperty("cells", entry.cells());
                share.addProperty("share", rate(entry.share()));
                shareArray.add(share);
            }
            root.add("biomes", shareArray);

            JsonArray clusterArray = new JsonArray();
            for (BiomeClusters entry : clusters) {
                JsonObject cluster = new JsonObject();
                cluster.addProperty("biome", entry.biome());
                cluster.addProperty("clusters", entry.clusters());
                cluster.addProperty("measured_clusters", entry.measuredClusters());
                cluster.addProperty("edge_clusters", entry.edgeClusters());
                cluster.addProperty("median_diameter_blocks", oneDecimal(entry.medianDiameterBlocks()));
                cluster.addProperty("p90_diameter_blocks", oneDecimal(entry.p90DiameterBlocks()));
                clusterArray.add(cluster);
            }
            root.add("clusters", clusterArray);

            JsonObject climateJson = new JsonObject();
            for (ClimateAxis axis : climate) {
                JsonObject entry = new JsonObject();
                entry.addProperty("f3_name", axis.f3Name());
                entry.addProperty("samples", axis.samples());
                JsonObject values = new JsonObject();
                for (int i = 0; i < PERCENTILES.length; i++) {
                    values.addProperty(Integer.toString(PERCENTILES[i]), stat(axis.percentiles().get(i)));
                }
                entry.add("percentiles", values);
                climateJson.add(axis.axis(), entry);
            }
            root.add("climate", climateJson);

            root.addProperty("elapsed_ms", elapsedMillis);
            return root;
        }

        public String toJsonString() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(toJson());
        }

        /** The chat form: shares, patch sizes, and the middle and tails of each climate axis. */
        public List<String> summary() {
            List<String> lines = new ArrayList<>();
            lines.add("survey biomes in " + dimension + " (radius " + radiusBlocks + " blocks around "
                    + centerX + "," + centerY + "," + centerZ + "; " + sampledCells + " sampled cells"
                    + (climateStride == 1 ? "" : ", climate every " + climateStride + " cells")
                    + ")");
            lines.add("  biomes: " + shareLine());
            for (BiomeClusters entry : clusters) {
                lines.add("  " + entry.biome() + " clusters " + entry.clusters()
                        + " (" + entry.edgeClusters() + " touch the rim, excluded):"
                        + " median diameter " + oneDecimal(entry.medianDiameterBlocks())
                        + ", p90 " + oneDecimal(entry.p90DiameterBlocks()) + " blocks");
            }
            for (ClimateAxis axis : climate) {
                lines.add("  " + axis.axis() + " (F3 " + axis.f3Name() + ")"
                        + " p5 " + twoDecimals(at(axis, 5))
                        + ", p50 " + twoDecimals(at(axis, 50))
                        + ", p95 " + twoDecimals(at(axis, 95)));
            }
            lines.add("  elapsed " + elapsedMillis + " ms");
            return lines;
        }

        private String shareLine() {
            int shown = Math.min(shares.size(), 10);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < shown; i++) {
                BiomeShare entry = shares.get(i);
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(entry.biome()).append(' ').append(percent(entry.share()));
            }
            if (shares.size() > shown) {
                builder.append(", ... ").append(shares.size() - shown).append(" more");
            }
            return builder.toString();
        }

        private static double at(ClimateAxis axis, int percentile) {
            for (int i = 0; i < PERCENTILES.length; i++) {
                if (PERCENTILES[i] == percentile) {
                    return axis.percentiles().get(i);
                }
            }
            throw new IllegalArgumentException("no percentile " + percentile);
        }
    }

    private BiomeSurvey() {}

    /**
     * Samples the disc and summarizes it. Safe on a worker thread: the biome source and sampler are
     * read-only, and nothing in the result refers to the level.
     */
    public static Result run(BiomeSource source, Climate.Sampler sampler, String dimension,
                             Params params) {
        if (params.radiusBlocks() < MIN_RADIUS_BLOCKS || params.radiusBlocks() > MAX_RADIUS_BLOCKS) {
            throw new IllegalArgumentException("radius must be " + MIN_RADIUS_BLOCKS + "..."
                    + MAX_RADIUS_BLOCKS + " blocks, got " + params.radiusBlocks());
        }
        long startedNanos = System.nanoTime();

        int radius = params.radiusBlocks();
        long radiusSquared = (long) radius * radius;
        int minQx = QuartPos.fromBlock(params.centerX() - radius);
        int maxQx = QuartPos.fromBlock(params.centerX() + radius);
        int minQz = QuartPos.fromBlock(params.centerZ() - radius);
        int maxQz = QuartPos.fromBlock(params.centerZ() + radius);
        int quartY = QuartPos.fromBlock(params.centerY());
        int width = maxQx - minQx + 1;
        int depth = maxQz - minQz + 1;

        int stride = climateStride(radius);
        int climateCapacity = ((width + stride - 1) / stride) * ((depth + stride - 1) / stride);

        // One id per biome; -1 marks a cell outside the disc, -2 a cell already counted.
        int[] cells = new int[width * depth];
        Arrays.fill(cells, -1);
        List<Holder<Biome>> biomes = new ArrayList<>();
        Map<Holder<Biome>, Integer> ids = new HashMap<>();
        double[][] climateValues = new double[AXES.size()][];
        for (int axis = 0; axis < AXES.size(); axis++) {
            climateValues[axis] = new double[climateCapacity];
        }
        int climateSamples = 0;
        int sampled = 0;

        for (int iz = 0; iz < depth; iz++) {
            int quartZ = minQz + iz;
            int dz = (quartZ * CELL_BLOCKS) + (CELL_BLOCKS / 2) - params.centerZ();
            for (int ix = 0; ix < width; ix++) {
                int quartX = minQx + ix;
                int dx = (quartX * CELL_BLOCKS) + (CELL_BLOCKS / 2) - params.centerX();
                if ((long) dx * dx + (long) dz * dz > radiusSquared) {
                    continue;
                }
                Holder<Biome> biome = source.getNoiseBiome(quartX, quartY, quartZ, sampler);
                Integer id = ids.get(biome);
                if (id == null) {
                    id = biomes.size();
                    ids.put(biome, id);
                    biomes.add(biome);
                }
                cells[iz * width + ix] = id;
                sampled++;

                if (ix % stride == 0 && iz % stride == 0) {
                    Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
                    for (int axis = 0; axis < AXES.size(); axis++) {
                        climateValues[axis][climateSamples] = Climate.unquantizeCoord(
                                AXES.get(axis).value().applyAsLong(point));
                    }
                    climateSamples++;
                }
            }
        }

        List<BiomeShare> shares = shares(biomes, cells, sampled);
        List<BiomeClusters> clusters = clusters(biomes, cells, width, depth);
        List<ClimateAxis> climate = climate(climateValues, climateSamples);
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return new Result(dimension, params.centerX(), params.centerY(), params.centerZ(), radius,
                sampled, stride, shares, clusters, climate, elapsedMillis);
    }

    /**
     * How many cells to skip between climate samples. The biome share is exact at every cell; the
     * climate arrays only need enough samples for percentiles, so a large disc thins them out rather
     * than holding one double per axis per cell (radius 4096 would be 158 MB).
     */
    static int climateStride(int radiusBlocks) {
        double quartCells = Math.PI * Math.pow(radiusBlocks / (double) CELL_BLOCKS, 2);
        int stride = (int) Math.ceil(Math.sqrt(quartCells / CLIMATE_SAMPLE_CAP));
        return Math.max(1, stride);
    }

    private static List<BiomeShare> shares(List<Holder<Biome>> biomes, int[] cells, int sampled) {
        int[] counts = new int[biomes.size()];
        for (int id : cells) {
            if (id >= 0) {
                counts[id]++;
            }
        }
        List<BiomeShare> shares = new ArrayList<>(biomes.size());
        for (int id = 0; id < biomes.size(); id++) {
            shares.add(new BiomeShare(idOf(biomes.get(id)), counts[id],
                    sampled == 0 ? 0.0 : (double) counts[id] / sampled));
        }
        shares.sort(Comparator.comparingInt(BiomeShare::cells).reversed()
                .thenComparing(BiomeShare::biome));
        return shares;
    }

    /**
     * Labels face-connected groups. A group whose member has a face neighbour outside the disc is
     * recorded as edge-touching: its size is a lower bound, so it is counted and left out of the
     * diameters.
     */
    private static List<BiomeClusters> clusters(List<Holder<Biome>> biomes, int[] cells,
                                                int width, int depth) {
        List<List<Integer>> areas = new ArrayList<>(biomes.size());
        int[] edgeCounts = new int[biomes.size()];
        for (int id = 0; id < biomes.size(); id++) {
            areas.add(new ArrayList<>());
        }
        int[] stack = new int[cells.length];

        for (int start = 0; start < cells.length; start++) {
            int biome = cells[start];
            if (biome < 0) {
                continue;
            }
            int top = 0;
            stack[top++] = start;
            cells[start] = -2;
            int area = 0;
            boolean touchesEdge = false;
            while (top > 0) {
                int current = stack[--top];
                area++;
                int ix = current % width;
                int iz = current / width;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = ix + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int nz = iz + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    if (nx < 0 || nx >= width || nz < 0 || nz >= depth) {
                        touchesEdge = true;
                        continue;
                    }
                    int neighbour = nz * width + nx;
                    int value = cells[neighbour];
                    if (value == -1) {
                        touchesEdge = true;
                    } else if (value == biome) {
                        cells[neighbour] = -2;
                        stack[top++] = neighbour;
                    }
                }
            }
            if (touchesEdge) {
                edgeCounts[biome]++;
            } else {
                areas.get(biome).add(area);
            }
        }

        List<BiomeClusters> result = new ArrayList<>(biomes.size());
        for (int id = 0; id < biomes.size(); id++) {
            List<Integer> patchAreas = areas.get(id);
            double[] diameters = new double[patchAreas.size()];
            for (int i = 0; i < diameters.length; i++) {
                diameters[i] = equivalentDiameter((double) patchAreas.get(i) * CELL_AREA_BLOCKS);
            }
            Arrays.sort(diameters);
            result.add(new BiomeClusters(idOf(biomes.get(id)), patchAreas.size() + edgeCounts[id],
                    patchAreas.size(), edgeCounts[id],
                    percentile(diameters, 50), percentile(diameters, 90)));
        }
        result.sort(Comparator.comparingInt(BiomeClusters::clusters).reversed()
                .thenComparing(BiomeClusters::biome));
        return result;
    }

    private static List<ClimateAxis> climate(double[][] values, int samples) {
        List<ClimateAxis> axes = new ArrayList<>(AXES.size());
        for (int index = 0; index < AXES.size(); index++) {
            double[] sorted = Arrays.copyOf(values[index], samples);
            Arrays.sort(sorted);
            List<Double> percentiles = new ArrayList<>(PERCENTILES.length);
            for (int percentile : PERCENTILES) {
                percentiles.add(percentile(sorted, percentile));
            }
            Axis axis = AXES.get(index);
            axes.add(new ClimateAxis(axis.id(), axis.f3Name(), samples, percentiles));
        }
        return axes;
    }

    /**
     * The diameter of a circle with the given area, in blocks. Zero and negative areas return 0 so a
     * biome with no measurable patch reports a number rather than {@code NaN}.
     */
    public static double equivalentDiameter(double areaBlocks) {
        return areaBlocks <= 0.0 ? 0.0 : 2.0 * Math.sqrt(areaBlocks / Math.PI);
    }

    /**
     * Linear-interpolated percentile of an ascending array: {@code p50} of an even-length array sits
     * between the middle two values, which is what makes the result stable as the sample count
     * changes. The array must already be sorted; an empty array returns 0.
     */
    public static double percentile(double[] sortedValues, double percent) {
        if (sortedValues.length == 0) {
            return 0.0;
        }
        if (sortedValues.length == 1) {
            return sortedValues[0];
        }
        double rank = (percent / 100.0) * (sortedValues.length - 1);
        int low = (int) Math.floor(rank);
        int high = Math.min(low + 1, sortedValues.length - 1);
        double fraction = rank - low;
        return sortedValues[low] * (1.0 - fraction) + sortedValues[high] * fraction;
    }

    /** File name under {@code <world>/isekai/survey/}; the centre is in the name so runs do not overwrite. */
    public static String fileName(Params params) {
        return "biomes-r" + params.radiusBlocks() + "-" + params.centerX() + "_"
                + params.centerY() + "_" + params.centerZ() + ".json";
    }

    private static String idOf(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
    }

    /** A statistic as fixed-point text, so the JSON stays readable instead of printing 7.8E-4. */
    private static BigDecimal stat(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String twoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private record Axis(String id, String f3Name, ToLongFunction<Climate.TargetPoint> value) {}
}