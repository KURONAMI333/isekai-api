package com.kuronami.isekaiapi.survey;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kuronami.isekaiapi.structure.StructureProbe;
import com.kuronami.isekaiapi.structure.StructureProbe.Gate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * Counts how often a structure really appears, and why it does not.
 *
 * <p>Vanilla {@code /locate} answers "where is the nearest one"; it cannot answer "why does my
 * structure never spawn". This runs the same two steps chunk generation runs — the structure set's
 * {@code StructurePlacement#isStructureChunk} decides which chunks are tried, then
 * {@code Structure#generate} decides whether the structure actually exists there — over a square of
 * chunks around a point, and counts the outcomes.
 *
 * <p>Nothing is generated to disk and no chunk is loaded: the placement test and
 * {@code Structure#generate} only read the seed and the noise sampler, so a survey costs
 * milliseconds per thousand chunks and cannot alter the world. Block counting is deliberately not
 * part of this tool — that would mean generating every chunk in the square, and "did a block
 * appear" is already answered by standing in the world.
 *
 * <p>The rejection reasons come from {@link StructureProbe}: a gate inside the structure reports
 * itself, and the biome filter is recorded by the predicate this class hands to
 * {@code Structure#generate} (the same predicate chunk generation uses). A structure that returns
 * {@code INVALID_START} without reporting anything is counted as {@code declined} — the structure
 * type has its own reason and this tool cannot see inside it.
 */
@ApiStatus.Internal
public final class StructureSurvey {

    /** How many placed chunks the JSON keeps as examples. */
    public static final int SAMPLE_LIMIT = 5;

    private static final String DECLINED = "declined";

    /** One chunk that produced a structure. */
    public record Placed(int chunkX, int chunkZ, BoundingBox box) {}

    /**
     * The measured result: {@code scannedChunks} is the square, {@code candidateChunks} is what the
     * placement offered, and every candidate is either {@code placed} or appears in
     * {@code rejections} exactly once.
     */
    public record Result(
            String structureId,
            String dimension,
            int radiusChunks,
            int scannedChunks,
            int candidateChunks,
            int placed,
            Map<String, Integer> rejections,
            List<String> structureSets,
            List<Placed> samples,
            long elapsedMillis
    ) {

        public Result {
            rejections = Map.copyOf(rejections);
            structureSets = List.copyOf(structureSets);
            samples = List.copyOf(samples);
        }

        /** Candidate chunks as a share of the scanned square — the structure set's density. */
        public double candidateRate() {
            return scannedChunks == 0 ? 0.0 : (double) candidateChunks / scannedChunks;
        }

        /** Placed chunks as a share of the candidates — how often the structure's own gates say yes. */
        public double placeRate() {
            return candidateChunks == 0 ? 0.0 : (double) placed / candidateChunks;
        }

        public JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("structure", structureId);
            root.addProperty("dimension", dimension);
            root.addProperty("radius_chunks", radiusChunks);
            root.addProperty("scanned_chunks", scannedChunks);
            root.addProperty("candidate_chunks", candidateChunks);
            root.addProperty("candidate_rate", rate(candidateRate()));
            root.addProperty("placed", placed);
            root.addProperty("place_rate_of_candidates", rate(placeRate()));
            root.addProperty("place_rate_of_scanned", rate(scannedChunks == 0
                    ? 0.0 : (double) placed / scannedChunks));

            JsonObject rejected = new JsonObject();
            new TreeSet<>(rejections.keySet()).forEach(key -> rejected.addProperty(key, rejections.get(key)));
            root.add("rejections", rejected);

            JsonArray sets = new JsonArray();
            structureSets.forEach(sets::add);
            root.add("structure_sets", sets);

            JsonArray placedSamples = new JsonArray();
            for (Placed placedChunk : samples) {
                JsonObject entry = new JsonObject();
                entry.addProperty("chunk_x", placedChunk.chunkX());
                entry.addProperty("chunk_z", placedChunk.chunkZ());
                JsonArray box = new JsonArray();
                box.add(placedChunk.box().minX());
                box.add(placedChunk.box().minY());
                box.add(placedChunk.box().minZ());
                box.add(placedChunk.box().maxX());
                box.add(placedChunk.box().maxY());
                box.add(placedChunk.box().maxZ());
                entry.add("box", box);
                placedSamples.add(entry);
            }
            root.add("sample_placements", placedSamples);
            root.addProperty("elapsed_ms", elapsedMillis);
            return root;
        }

        public String toJsonString() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(toJson());
        }

        /** The chat form: one line of numbers, then the rejections and a few example chunks. */
        public List<String> summary() {
            List<String> lines = new ArrayList<>();
            lines.add("survey " + structureId + " in " + dimension + " (radius " + radiusChunks
                    + " chunks = " + scannedChunks + " scanned)");
            lines.add("  candidate chunks " + candidateChunks + " ("
                    + percent(candidateRate()) + " of scanned); placed " + placed + " ("
                    + percent(placeRate()) + " of candidates, "
                    + percent(scannedChunks == 0 ? 0.0 : (double) placed / scannedChunks)
                    + " of scanned)");
            if (rejections.isEmpty()) {
                lines.add("  rejections: none");
            } else {
                StringBuilder reasons = new StringBuilder();
                new TreeSet<>(rejections.keySet()).forEach(key -> {
                    if (reasons.length() > 0) {
                        reasons.append(", ");
                    }
                    reasons.append(key).append(' ').append(rejections.get(key));
                });
                lines.add("  rejected: " + reasons);
            }
            for (Placed placedChunk : samples) {
                lines.add("  placed at chunk " + placedChunk.chunkX() + ","
                        + placedChunk.chunkZ() + " box " + placedChunk.box().minX() + ","
                        + placedChunk.box().minY() + "," + placedChunk.box().minZ() + " .. "
                        + placedChunk.box().maxX() + "," + placedChunk.box().maxY() + ","
                        + placedChunk.box().maxZ());
            }
            if (placed > samples.size()) {
                lines.add("  (" + (placed - samples.size()) + " more placed chunks not listed)");
            }
            lines.add("  elapsed " + elapsedMillis + " ms");
            return lines;
        }

        /**
         * A rate as a fixed-point decimal. A plain double would be written as {@code 7.8E-4} for the
         * sparse rates a real structure set produces, which is valid JSON but unreadable in a file
         * meant to be looked at.
         */
        private static BigDecimal rate(double value) {
            return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
        }

        private static String percent(double value) {
            return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0);
        }
    }

    private StructureSurvey() {}

    /**
     * Surveys one structure found in the level's registry. Fails when the id is unknown, and reports
     * an empty candidate set when the structure is not part of any structure set (it can then only
     * be placed by command).
     */
    public static Result run(ServerLevel level, ResourceKey<Structure> key, ChunkPos center, int radius) {
        Holder<Structure> holder = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .get(key)
                .orElseThrow(() -> new IllegalArgumentException("unknown structure: " + key.location()));
        return run(level, holder.value(), key.location().toString(), setsFor(level, key), center, radius);
    }

    /**
     * The structure sets this world's generator knows that can place {@code key}. Empty when the
     * structure exists in the registry but no structure set offers it.
     */
    public static List<StructureSet> setsFor(ServerLevel level, ResourceKey<Structure> key) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        return state.possibleStructureSets().stream()
                .map(Holder::value)
                .filter(set -> set.structures().stream()
                        .anyMatch(entry -> entry.structure().is(key)))
                .toList();
    }

    /** Whether any of {@code sets} offers {@code chunkPos} to its structures at all. */
    public static boolean isCandidate(ServerLevel level, List<StructureSet> sets, ChunkPos chunkPos) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        return sets.stream().anyMatch(set -> set.placement()
                .isStructureChunk(state, chunkPos.x, chunkPos.z));
    }

    /** Registry ids of {@code sets}, for reports; a set built in code has no id. */
    public static List<String> setNames(ServerLevel level, List<StructureSet> sets) {
        return sets.stream()
                .map(set -> {
                    ResourceLocation key = level.registryAccess()
                            .registryOrThrow(Registries.STRUCTURE_SET)
                            .getKey(set);
                    return key == null ? "unregistered" : key.toString();
                })
                .toList();
    }

    /**
     * Surveys {@code structure} against an explicit list of structure sets. Exposed so a test can
     * survey a structure that is not in the world's registry.
     */
    public static Result run(ServerLevel level, Structure structure, String structureId,
                             List<StructureSet> sets, ChunkPos center, int radius) {
        long started = System.nanoTime();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templates = level.getServer().getStructureManager();
        long seed = level.getSeed();

        Set<Long> candidates = new TreeSet<>();
        int scanned = 0;
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                scanned++;
                for (StructureSet set : sets) {
                    if (set.placement().isStructureChunk(state, cx, cz)) {
                        candidates.add(ChunkPos.asLong(cx, cz));
                        break;
                    }
                }
            }
        }

        Map<String, Integer> rejections = new LinkedHashMap<>();
        List<Placed> samples = new ArrayList<>();
        int placed = 0;
        for (long packed : candidates) {
            ChunkPos chunkPos = new ChunkPos(packed);
            AtomicReference<Gate> gate = new AtomicReference<>();
            StructureStart start = StructureProbe.record(gate::set, () -> structure.generate(
                    level.registryAccess(),
                    generator,
                    generator.getBiomeSource(),
                    randomState,
                    templates,
                    seed,
                    chunkPos,
                    0,
                    level,
                    biome -> {
                        // The same predicate chunk generation passes (ChunkGenerator
                        // #tryGenerateStructure): the structure's own biomes.
                        boolean allowed = structure.biomes().contains(biome);
                        if (!allowed) {
                            StructureProbe.reject(Gate.BIOME);
                        }
                        return allowed;
                    }));
            if (start.isValid()) {
                placed++;
                if (samples.size() < SAMPLE_LIMIT) {
                    samples.add(new Placed(chunkPos.x, chunkPos.z, start.getBoundingBox()));
                }
            } else {
                String reason = Optional.ofNullable(gate.get())
                        .map(Gate::getSerializedName)
                        .orElse(DECLINED);
                rejections.merge(reason, 1, Integer::sum);
            }
        }

        List<String> setNames = setNames(level, sets);
        return new Result(structureId, level.dimension().location().toString(), radius, scanned,
                candidates.size(), placed, rejections, setNames, samples,
                (System.nanoTime() - started) / 1_000_000L);
    }

    /** The file name a survey writes for a structure id and radius. */
    public static String fileName(ResourceLocation structureId, int radius) {
        return (structureId.getNamespace() + "_" + structureId.getPath()).replace('/', '_')
                + "-r" + radius + ".json";
    }
}
