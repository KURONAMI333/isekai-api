package com.kuronami.isekaiapi.lifecycle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Loads {@code data/<ns>/isekai/worldshape/*.json} on every datapack reload and invokes
 * {@link com.kuronami.isekaiapi.api.remap.IsekaiRemap#declareWorldshape} for each entry.
 *
 * <p>Reload model:
 * <ul>
 *   <li>On every reload, previously-JSON-declared worldshapes are removed for the
 *       dimensions appearing in the new pack (via {@code removeWorldshape}).</li>
 *   <li>Java-side declarations (consumers calling {@code declareWorldshape} directly)
 *       are <b>not</b> touched — this listener only owns JSON-sourced state.</li>
 *   <li>If a JSON file fails to decode, that single entry is skipped with an error log;
 *       the rest still apply. Set system property {@code -Disekai.strict=true} to flip
 *       this — under strict mode, any decode failure throws and aborts the entire reload,
 *       preventing a partially-applied pack from going live.</li>
 * </ul>
 *
 * <p>The companion {@code layered_worldshape/} directory is handled the same way; each
 * file there is a list of {@link LayeredDescriptor} for one dimension. Each layer carries
 * its own {@link com.kuronami.isekaiapi.api.remap.TransitionRule} that controls the seam
 * to the layer above it.
 */
@ApiStatus.Internal
public final class IsekaiReloadListener extends SimpleJsonResourceReloadListener {

    /** Directory under {@code data/<ns>/} scanned for single-layer worldshape JSON. */
    public static final String WORLDSHAPE_DIR = "isekai/worldshape";

    /** Directory under {@code data/<ns>/} scanned for multi-layer worldshape JSON. */
    public static final String LAYERED_DIR = "isekai/layered_worldshape";

    /**
     * Strict validation mode toggle. Read once at class load from
     * {@code -Disekai.strict=true}; flipping at runtime is not supported. When enabled,
     * any decode/declare failure during reload throws and aborts the whole reload —
     * Minecraft falls back to the previously-good pack state, surfacing the underlying
     * exception in the server log instead of letting a half-applied pack go live.
     */
    public static final boolean STRICT_MODE = Boolean.getBoolean("isekai.strict");

    /**
     * Per-dimension keys most recently loaded from JSON. We retain this so a subsequent
     * reload can remove no-longer-present entries before applying the new set.
     */
    private final Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> lastJsonDimensions = new HashSet<>();

    /** Whether this listener loads single-layer or layered descriptors. */
    private final Mode mode;

    public enum Mode { SINGLE, LAYERED }

    private IsekaiReloadListener(Mode mode, String directory) {
        super(new Gson(), directory);
        this.mode = mode;
    }

    /** Listener for {@code data/<ns>/isekai/worldshape/*.json}. */
    public static IsekaiReloadListener forSingleLayer() {
        return new IsekaiReloadListener(Mode.SINGLE, WORLDSHAPE_DIR);
    }

    /** Listener for {@code data/<ns>/isekai/layered_worldshape/*.json}. */
    public static IsekaiReloadListener forLayered() {
        return new IsekaiReloadListener(Mode.LAYERED, LAYERED_DIR);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager rm, ProfilerFiller profiler) {
        IsekaiApi.LOGGER.info("[Isekai] reload: {} mode={}, {} entries (strict={})",
                mode == Mode.SINGLE ? WORLDSHAPE_DIR : LAYERED_DIR, mode, entries.size(), STRICT_MODE);

        Set<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> previous = Set.copyOf(lastJsonDimensions);
        lastJsonDimensions.clear();

        java.util.List<String> failedIds = new java.util.ArrayList<>();
        if (mode == Mode.SINGLE) {
            for (Map.Entry<ResourceLocation, JsonElement> e : entries.entrySet()) {
                applySingle(e.getKey(), e.getValue(), failedIds);
            }
        } else {
            for (Map.Entry<ResourceLocation, JsonElement> e : entries.entrySet()) {
                applyLayered(e.getKey(), e.getValue(), failedIds);
            }
        }

        if (STRICT_MODE && !failedIds.isEmpty()) {
            // Throwing here aborts the entire reload; Minecraft restores the previous pack
            // state and logs the failure. Lenient mode just continues with whatever succeeded.
            throw new IllegalStateException(
                    "Isekai strict mode: aborting reload — " + failedIds.size()
                            + " " + (mode == Mode.SINGLE ? WORLDSHAPE_DIR : LAYERED_DIR)
                            + " entries failed: " + failedIds);
        }

        // Remove dimensions that were in JSON before but not in the new pack.
        for (var dim : previous) {
            if (!lastJsonDimensions.contains(dim)) {
                Isekai.remap().removeWorldshape(dim);
                IsekaiApi.LOGGER.info("[Isekai] reload: removed stale JSON-sourced worldshape for {}", dim);
            }
        }
    }

    private void applySingle(ResourceLocation entryId, JsonElement json, java.util.List<String> failedIds) {
        DataResult<WorldshapeDescriptor> result = decode(WorldshapeDescriptor.CODEC, json, entryId);
        boolean[] failed = {false};
        result.ifSuccess(d -> {
            try {
                Isekai.remap().declareWorldshape(d);
                lastJsonDimensions.add(d.dimension());
            } catch (RuntimeException ex) {
                IsekaiApi.LOGGER.error("[Isekai] reload: declareWorldshape failed for {}: {}",
                        entryId, ex.getMessage());
                failed[0] = true;
            }
        });
        result.ifError(err -> {
            IsekaiApi.LOGGER.error("[Isekai] reload: failed to decode {}: {}", entryId, err.message());
            failed[0] = true;
        });
        if (failed[0]) failedIds.add(entryId.toString());
    }

    /**
     * Layered JSON shape:
     * <pre>
     * {
     *   "dimension": "minecraft:overworld",
     *   "layers": [ { "y_range": ..., "descriptor": ..., "transition": ... }, ... ],
     *   "transition": { "type": "isekai:hard" }   // top-level transition between adjacent layers
     * }
     * </pre>
     */
    private void applyLayered(ResourceLocation entryId, JsonElement json, java.util.List<String> failedIds) {
        DataResult<LayeredFile> result = decode(LayeredFile.CODEC, json, entryId);
        boolean[] failed = {false};
        result.ifSuccess(f -> {
            try {
                Isekai.remap().declareLayeredWorldshape(f.dimension(), f.layers());
                lastJsonDimensions.add(f.dimension());
            } catch (RuntimeException ex) {
                IsekaiApi.LOGGER.error("[Isekai] reload: declareLayeredWorldshape failed for {}: {}",
                        entryId, ex.getMessage());
                failed[0] = true;
            }
        });
        result.ifError(err -> {
            IsekaiApi.LOGGER.error("[Isekai] reload: failed to decode {}: {}", entryId, err.message());
            failed[0] = true;
        });
        if (failed[0]) failedIds.add(entryId.toString());
    }

    private static <T> DataResult<T> decode(Codec<T> codec, JsonElement json, ResourceLocation id) {
        try {
            return codec.parse(JsonOps.INSTANCE, json);
        } catch (IllegalArgumentException e) {
            // Codec invariant violation that wasn't wrapped in DataResult
            return DataResult.error(() -> "invariant violation in " + id + ": " + e.getMessage());
        }
    }

    /**
     * Wire format for one file under {@code isekai/layered_worldshape/}. Each layer's
     * {@link LayeredDescriptor#transition()} controls the seam to the layer above it —
     * there is no file-level transition, since per-layer is strictly more expressive.
     */
    public record LayeredFile(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            List<LayeredDescriptor> layers
    ) {
        public LayeredFile {
            layers = List.copyOf(layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("layered_worldshape: layers must not be empty");
            }
        }

        public static final Codec<LayeredFile> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                net.minecraft.resources.ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION)
                        .fieldOf("dimension").forGetter(LayeredFile::dimension),
                LayeredDescriptor.CODEC.listOf().fieldOf("layers").forGetter(LayeredFile::layers)
        ).apply(i, LayeredFile::new));
    }
}
