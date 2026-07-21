package com.kuronami.isekaiapi.registry;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared machinery for the five registry-backed dispatch codecs (SpatialPredicate,
 * RemapStrategy, BiomeZone, SurfaceAnchor, TransitionRule). Each dispatches on a {@code "type"}
 * field whose value is a namespaced id resolving to a {@code MapCodec} in the corresponding
 * Isekai custom registry — mirroring vanilla's {@code density_function_type} dispatch.
 *
 * <p>The {@code "type"} value is normalized before lookup:
 * <ul>
 *   <li>a bare name (no namespace) defaults to {@code isekai_api:};</li>
 *   <li>the legacy {@code isekai:} namespace is accepted and rewritten to {@code isekai_api:},
 *       emitting a one-time-per-id deprecation warning (the alias is honoured for one major
 *       version).</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiDispatch {

    private IsekaiDispatch() {}

    private static final String CANON_NAMESPACE = "isekai_api";
    private static final String LEGACY_NAMESPACE = "isekai";

    /** Ids already warned about, so the deprecation nudge fires once per id across all worldgen threads. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Codec for a dispatch {@code "type"} id: parses a resource location, defaulting a bare name
     * to the {@code isekai_api} namespace and normalizing the legacy {@code isekai} namespace
     * (with a one-time deprecation warning).
     */
    public static final Codec<ResourceLocation> TYPE_ID = Codec.STRING.comapFlatMap(
            IsekaiDispatch::parseTypeId, ResourceLocation::toString);

    private static DataResult<ResourceLocation> parseTypeId(String raw) {
        try {
            ResourceLocation id = raw.indexOf(':') < 0
                    ? ResourceLocation.fromNamespaceAndPath(CANON_NAMESPACE, raw)
                    : ResourceLocation.parse(raw);
            return DataResult.success(normalize(id));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "Not a valid type id: '" + raw + "' (" + e.getMessage() + ")");
        }
    }

    /** Rewrite {@code isekai:x} to {@code isekai_api:x}, warning once per legacy id. */
    private static ResourceLocation normalize(ResourceLocation id) {
        if (!LEGACY_NAMESPACE.equals(id.getNamespace())) {
            return id;
        }
        ResourceLocation canon = ResourceLocation.fromNamespaceAndPath(CANON_NAMESPACE, id.getPath());
        if (WARNED.add(id.toString())) {
            IsekaiApi.LOGGER.warn(
                    "[Isekai] type id '{}' uses the deprecated '{}:' prefix; use '{}'. "
                            + "The legacy prefix is accepted this major version and will be removed in the next.",
                    id, LEGACY_NAMESPACE, canon);
        }
        return canon;
    }

    /**
     * Build a registry-backed dispatch codec for {@code T}. The registry holds one
     * {@code MapCodec<? extends T>} per variant, keyed by its registration id; each variant
     * returns its own registered codec from {@code codecGetter} so the encode direction can
     * recover the id by reverse lookup.
     *
     * @param registry    supplier of the Isekai custom registry for {@code T} (resolved lazily,
     *                    after the registry is created and populated)
     * @param codecGetter maps a value to its registered {@code MapCodec}
     * @param label       human-readable type name for error messages
     */
    public static <T> Codec<T> dispatchCodec(Supplier<Registry<MapCodec<? extends T>>> registry,
                                             Function<T, MapCodec<? extends T>> codecGetter,
                                             String label) {
        return Codec.lazyInitialized(() -> TYPE_ID.<T>dispatch(
                "type",
                value -> keyOf(registry.get(), codecGetter.apply(value), label),
                id -> lookup(registry.get(), id, label)));
    }

    private static <T> ResourceLocation keyOf(Registry<MapCodec<? extends T>> registry,
                                              MapCodec<? extends T> codec, String label) {
        ResourceLocation id = registry.getKey(codec);
        if (id == null) {
            throw new IllegalStateException(
                    "Unregistered " + label + " codec " + codec + " — every variant's codec() must be "
                            + "registered under " + registry.key().location());
        }
        return id;
    }

    private static <T> MapCodec<? extends T> lookup(Registry<MapCodec<? extends T>> registry,
                                                    ResourceLocation id, String label) {
        MapCodec<? extends T> codec = registry.get(id);
        if (codec == null) {
            throw new IllegalArgumentException(
                    "Unknown " + label + " type '" + id + "'. Known types: " + registry.keySet());
        }
        return codec;
    }
}
