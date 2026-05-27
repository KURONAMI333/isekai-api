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
 * <p>v0.5 surface; richer cross-field checks (BandSplit ratio sum, layered yRange
 * non-overlap, dimension/biome existence) land in v0.6 alongside the reload pipeline.
 */
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
                (id, json) -> decodeAndCheck(id, json, LayeredDescriptor.CODEC, d -> {}, errors));

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
}
