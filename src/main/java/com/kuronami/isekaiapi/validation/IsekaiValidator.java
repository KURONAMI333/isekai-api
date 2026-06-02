package com.kuronami.isekaiapi.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * Datapack JSON validator for {@code data/<namespace>/isekai/} descriptors.
 *
 * <p>Validates:
 * <ul>
 *   <li>{@code isekai/worldshape/*.json} → {@link WorldshapeDescriptor#CODEC}</li>
 *   <li>{@code isekai/layered_worldshape/*.json} → list of {@link LayeredDescriptor}
 *       (one file per dimension)</li>
 * </ul>
 *
 * <p>Failures are accumulated as messages (one per file). Per-file failure modes:
 * <ul>
 *   <li>JSON syntactically invalid → parse error</li>
 *   <li>Codec decode failure (missing fields, wrong types) → {@code DataResult} error</li>
 *   <li>Record canonical-constructor invariant violated (e.g. {@code minY > maxY},
 *       empty Pipe) → caught as {@link IllegalArgumentException}</li>
 * </ul>
 *
 * <p>Cross-field validation includes BandSplit ratio sums, layered yRange non-overlap,
 * and structure_strategy meaningfulness — all performed after per-file codec decode.
 */
@ApiStatus.Internal
public final class IsekaiValidator {

    private static final String WORLDSHAPE_DIR = "isekai/worldshape";
    private static final String LAYERED_DIR = "isekai/layered_worldshape";

    private IsekaiValidator() {}

    /**
     * Validate every {@code isekai/} JSON under the given namespace.
     *
     * @param namespace data pack namespace to scan
     * @param rm        the server's resource manager (provides datapack file access)
     */
    public static ValidationResult validateNamespace(String namespace, ResourceManager rm) {
        List<String> errors = new ArrayList<>();
        int filesChecked = 0;

        filesChecked += validateDir(
                rm, namespace, WORLDSHAPE_DIR, errors,
                (id, json) -> decodeAndCheck(id, json, WorldshapeDescriptor.CODEC, IsekaiValidator::crossCheckWorldshape, errors));

        filesChecked += validateDir(
                rm, namespace, LAYERED_DIR, errors,
                (id, json) -> decodeAndCheck(id, json,
                        com.kuronami.isekaiapi.lifecycle.IsekaiReloadListener.LayeredFile.CODEC,
                        IsekaiValidator::crossCheckLayered, errors));

        IsekaiApi.LOGGER.info(
                "[Isekai] validateNamespace({}) -> {} files checked, {} errors",
                namespace, filesChecked, errors.size());
        return new ValidationResult(filesChecked, errors);
    }

    @FunctionalInterface
    private interface FileHandler {
        void handle(ResourceLocation id, JsonElement json);
    }

    /** Iterate every JSON file under {@code dir} for the given namespace. */
    private static int validateDir(ResourceManager rm, String namespace, String dir,
                                   List<String> errors, FileHandler handler) {
        Map<ResourceLocation, Resource> resources = rm.listResources(
                dir,
                id -> id.getNamespace().equals(namespace) && id.getPath().endsWith(".json"));
        int count = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            count++;
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                handler.handle(id, json);
            } catch (IOException e) {
                errors.add(id + ": I/O error: " + e.getMessage());
            } catch (RuntimeException e) {
                // JsonParseException + anything else
                errors.add(id + ": malformed JSON: " + e.getMessage());
            }
        }
        return count;
    }

    /** Decode JSON via the given codec, run cross-checks, accumulate any errors. */
    private static <T> void decodeAndCheck(ResourceLocation id, JsonElement json,
                                           com.mojang.serialization.Codec<T> codec,
                                           java.util.function.Consumer<T> crossCheck,
                                           List<String> errors) {
        DataResult<T> result;
        try {
            result = codec.parse(JsonOps.INSTANCE, json);
        } catch (IllegalArgumentException e) {
            // Some codec paths can leak invariant exceptions even when wrapped in DataResult.
            errors.add(id + ": invariant violation: " + e.getMessage());
            return;
        }
        result.ifSuccess(value -> {
            try {
                crossCheck.accept(value);
            } catch (RuntimeException e) {
                errors.add(id + ": cross-field check failed: " + e.getMessage());
            }
        });
        result.ifError(err -> errors.add(id + ": decode error: " + err.message()));
    }

    /** Cross-field checks for WorldshapeDescriptor that the codec layer can't express. */
    private static void crossCheckWorldshape(WorldshapeDescriptor d) {
        var r = d.playableRange();
        if (r.minY() >= r.maxY()) {
            throw new IllegalArgumentException(
                    "playable_range invalid: min_y (" + r.minY() + ") >= max_y (" + r.maxY() + ")");
        }
        if (d.priority() < 0) {
            throw new IllegalArgumentException("priority < 0: " + d.priority());
        }
        // Empty applies_to means BiomeMatcher rejects every biome (intentional, prevents
        // cross-dimension leakage). That's fine for a structure-exclusion-only worldshape,
        // but if the descriptor carries biome-side content (atmosphere, feature/carver
        // exclusions, additions, ore/mob remap, content overrides) that content is silently
        // wasted — almost always a typo'd "keys"/"tags" field. Flag that specific case.
        if (d.appliesTo().isEmpty() && hasBiomeSideContent(d)) {
            throw new IllegalArgumentException(
                    "applies_to is empty but the worldshape configures biome-side content "
                    + "(atmosphere / exclusions / additions / ore or mob strategy / content_overrides). "
                    + "That content will apply to NO biome. Did you typo the 'keys'/'tags' field, "
                    + "or intend a tag like '#minecraft:is_overworld'?");
        }
        crossCheckStrategy("ore_strategy", d.oreStrategy());
        crossCheckStrategy("structure_strategy", d.structureStrategy());
        crossCheckStrategy("mob_spawn_strategy", d.mobSpawnStrategy());
        // Walk per-category overrides too — each is an independent strategy tree.
        d.mobSpawnStrategyByCategory().forEach((cat, strat) ->
                crossCheckStrategy("mob_spawn_strategy_by_category[" + cat.getSerializedName() + "]", strat));
        // structure_strategy only reaches RandomSpreadStructurePlacement.spacing via the
        // CountScale factor — Linear / Inverted / FixedRange / BandSplit have no semantic
        // meaning for structure spawn frequency and are silently ignored at chunk gen.
        // Warn the consumer so the no-op isn't surprising.
        verifyStructureStrategyMeaningful(d.structureStrategy());
    }

    /**
     * True when the descriptor configures anything that is applied per-matched-biome (i.e.
     * needs a non-empty {@code applies_to} to have any effect). Structure-only concerns
     * (structure_predicates, exclusions.structures, structure_spawn_overrides) are excluded
     * because they apply via the structure modifier independent of biome matching.
     */
    private static boolean hasBiomeSideContent(WorldshapeDescriptor d) {
        boolean atmosphere = !d.atmosphere().isNoOp();
        boolean exclusions = !d.exclusions().features().isEmpty()
                || !d.exclusions().carvers().isEmpty()
                || !d.exclusions().mobSpawns().isEmpty();
        boolean additions = !d.additions().features().isEmpty()
                || !d.additions().carvers().isEmpty()
                || !d.additions().mobSpawns().isEmpty();
        boolean oreRemap = !(d.oreStrategy() instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.Identity);
        boolean mobRemap = !(d.mobSpawnStrategy() instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.Identity)
                || !d.mobSpawnStrategyByCategory().isEmpty();
        boolean contentOverrides = !d.featurePredicates().isEmpty() || !d.blockOverrides().isEmpty();
        return atmosphere || exclusions || additions || oreRemap || mobRemap || contentOverrides;
    }

    /**
     * Reject any {@code structure_strategy} other than Identity (or a Pipe of Identity).
     * Structure <em>placement density</em> (RandomSpread spacing/separation) is baked into
     * the immutable {@code StructureSet} at codec-decode time and cannot be retargeted from
     * a worldshape — there is no NeoForge StructureSet modifier hook. To change how often a
     * structure spawns, override its {@code StructureSet} JSON directly in your datapack.
     * Structure <em>exclusion</em> (via the structure modifier) and structure <em>placement
     * conditions</em> (via {@code structure_predicates}) ARE supported.
     */
    private static void verifyStructureStrategyMeaningful(com.kuronami.isekaiapi.api.remap.RemapStrategy s) {
        if (s instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.Identity) return;
        if (s instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.Pipe p) {
            for (var child : p.chain()) verifyStructureStrategyMeaningful(child);
            return;
        }
        throw new IllegalArgumentException(
                "structure_strategy: " + s.getClass().getSimpleName()
                        + " has no effect — structure placement density can't be remapped from a "
                        + "worldshape (StructureSet spacing is immutable; override the StructureSet "
                        + "JSON in your datapack instead). Use ore_strategy / mob_spawn_strategy for "
                        + "Y-range / count remap; structure_strategy must be isekai:identity.");
    }

    /** Cross-field checks for a {@code LayeredFile}: non-overlapping y_ranges, monotone. */
    private static void crossCheckLayered(com.kuronami.isekaiapi.lifecycle.IsekaiReloadListener.LayeredFile f) {
        var layers = f.layers();
        // Sort a defensive copy by minY so the overlap check is O(n).
        var sorted = new java.util.ArrayList<>(layers);
        sorted.sort(java.util.Comparator.comparingInt(l -> l.yRange().minY()));
        for (int i = 0; i < sorted.size(); i++) {
            var current = sorted.get(i).yRange();
            if (current.minY() >= current.maxY()) {
                throw new IllegalArgumentException(
                        "layer[" + i + "] yRange invalid: min_y >= max_y (" + current + ")");
            }
            // Each per-layer descriptor goes through the same scalar checks.
            crossCheckWorldshape(sorted.get(i).descriptor());
            if (i == 0) continue;
            var prev = sorted.get(i - 1).yRange();
            if (current.minY() < prev.maxY()) {
                throw new IllegalArgumentException(
                        "layer[" + i + "] y_range (" + current.minY() + ".." + current.maxY()
                                + ") overlaps previous (" + prev.minY() + ".." + prev.maxY() + ")");
            }
        }
    }

    /**
     * Cross-field checks for a {@link com.kuronami.isekaiapi.api.remap.RemapStrategy} tree.
     * Currently enforces {@code BandSplit.ratios} sums to ~1.0 (tolerance 0.01) and recurses
     * into {@code Pipe} chains. Other variants have nothing to validate beyond what the
     * record canonical constructor already enforces.
     */
    private static void crossCheckStrategy(String fieldLabel,
                                           com.kuronami.isekaiapi.api.remap.RemapStrategy s) {
        if (s instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.BandSplit bs) {
            double sum = 0.0;
            int prevMaxY = Integer.MIN_VALUE;
            for (int i = 0; i < bs.bands().size(); i++) {
                var band = bs.bands().get(i);
                if (band.targetRatio() < 0) {
                    throw new IllegalArgumentException(
                            fieldLabel + ".BandSplit.bands[" + i + "].target_ratio < 0: " + band.targetRatio());
                }
                sum += band.targetRatio();
                if (band.vanillaSource().minY() < prevMaxY) {
                    throw new IllegalArgumentException(
                            fieldLabel + ".BandSplit.bands[" + i + "] vanilla_source overlaps previous band");
                }
                prevMaxY = band.vanillaSource().maxY();
            }
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalArgumentException(
                        fieldLabel + ".BandSplit.bands target_ratio sum=" + sum + " (must be 1.0 ± 0.01)");
            }
        } else if (s instanceof com.kuronami.isekaiapi.api.remap.RemapStrategy.Pipe p) {
            for (var child : p.chain()) {
                crossCheckStrategy(fieldLabel + ".pipe", child);
            }
        }
    }

    /**
     * Result of a namespace validation pass. {@link #errors} is the canonical source of
     * truth; {@link #errorsFound()} is derived from it. {@link #filesChecked} counts
     * every JSON file that was inspected (including the ones that passed).
     */
    public record ValidationResult(int filesChecked, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
            if (filesChecked < 0) {
                throw new IllegalArgumentException("filesChecked < 0");
            }
        }

        public static ValidationResult ok(int filesChecked) {
            return new ValidationResult(filesChecked, List.of());
        }

        public int errorsFound() {
            return errors.size();
        }

        public boolean isOk() {
            return errors.isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // World preset overrides check — catches the silent-Nether/End-loss trap
    // that overriding `data/minecraft/worldgen/world_preset/<id>.json` causes.
    // -----------------------------------------------------------------------

    /** Dimensions a world_preset MUST list (re-declared verbatim if not customised). */
    private static final List<String> REQUIRED_DIMENSIONS = List.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    /**
     * Scan every {@code worldgen/world_preset/*.json} contributed by a non-{@code minecraft}
     * datapack (i.e. authored by a mod / pack, not vanilla) and warn when its
     * {@code dimensions} map is missing any of the standard three dimensions. Catches the
     * exact trap where a consumer overrides {@code data/minecraft/worldgen/world_preset/normal.json}
     * to customise the overworld and silently breaks Nether / End access because they
     * forgot to re-declare those stanzas verbatim.
     *
     * <p>Returns a list of warning strings (one per file with missing dimensions); the
     * caller is responsible for logging. Result is empty when nothing is wrong.
     */
    public static List<String> validateWorldPresets(ResourceManager rm) {
        List<String> warnings = new ArrayList<>();
        Map<ResourceLocation, Resource> files = rm.listResources(
                "worldgen/world_preset", path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
            ResourceLocation id = e.getKey();
            try (BufferedReader reader = e.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (!json.isJsonObject()) continue;
                JsonElement dims = json.getAsJsonObject().get("dimensions");
                if (dims == null || !dims.isJsonObject()) {
                    warnings.add("world_preset " + id + " has no dimensions map");
                    continue;
                }
                List<String> missing = new ArrayList<>();
                for (String required : REQUIRED_DIMENSIONS) {
                    if (!dims.getAsJsonObject().has(required)) missing.add(required);
                }
                if (!missing.isEmpty()) {
                    warnings.add("world_preset " + id + " is missing " + missing
                            + " — Nether / End access will silently break unless those "
                            + "stanzas are re-declared verbatim. See examples/templates/world_preset_normal_override.json.");
                }
            } catch (IOException ex) {
                warnings.add("world_preset " + id + ": " + ex.getMessage());
            }
        }
        return warnings;
    }
}
