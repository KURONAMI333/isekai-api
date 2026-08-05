package com.kuronami.isekaiapi.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * Deterministic thinning of structure placements, backing a worldshape's
 * {@code structure_strategy} {@code count_scale} factor.
 *
 * <p><b>Why veto and not spacing.</b> The obvious implementation — scaling
 * {@code RandomSpreadStructurePlacement.spacing} / {@code separation} — is not available.
 * Those values live on the placement object held by the {@code StructureSet} registry, which
 * is shared by every dimension in the save, and {@code spacing()} takes no dimension
 * argument; a per-worldshape scale applied there would leak into every other dimension that
 * references the same structure set. Worse, {@code ChunkGeneratorStructureState} derives its
 * candidate-chunk grid from those values once at world load and caches it, so changing them
 * afterwards desyncs the cached grid from the live one — the same coordinates then answer
 * "structure here?" differently depending on when they are asked, which breaks seed
 * reproducibility.
 *
 * <p>Thinning instead leaves vanilla's grid untouched and vetoes a share of the candidates it
 * produces, at the point where a structure has already been offered a valid generation point.
 * The grid, its seed derivation, and every other dimension are unaffected.
 *
 * <p><b>Consequence.</b> This can only remove structures, never add them: a factor above 1.0
 * has no candidates to promote and is rejected at validation time rather than silently
 * rounded down.
 *
 * <p><b>Determinism.</b> The keep/drop decision is a pure hash of
 * {@code (world seed, chunk X, chunk Z, structure id)}. It does not touch the generation
 * context's {@code WorldgenRandom}, so vanilla's random stream is undisturbed, and the same
 * chunk always resolves the same way — regenerating a chunk reproduces the original result.
 */
@ApiStatus.Internal
public final class StructureThinning {

    private StructureThinning() {}

    /**
     * Whether this structure placement survives the {@code count_scale} factor.
     * {@code factor >= 1} keeps everything; {@code factor <= 0} keeps nothing.
     *
     * @param structureId stable identity of the structure (its registry id), so that thinning
     *                    one structure type does not correlate with thinning another
     */
    public static boolean keep(double factor, long seed, int chunkX, int chunkZ, String structureId) {
        if (factor >= 1.0) return true;
        if (factor <= 0.0) return false;
        return sample(seed, chunkX, chunkZ, salt(structureId)) < factor;
    }

    /**
     * Stable 32-bit hash of a structure id. Uses FNV-1a over the UTF-16 units rather than
     * {@code String.hashCode} on a Mojang object, so the value cannot drift with a Minecraft
     * or JDK change and existing worlds keep generating the same structures.
     */
    static int salt(String structureId) {
        int h = 0x811C9DC5;
        for (int i = 0; i < structureId.length(); i++) {
            h ^= structureId.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }

    /** Uniform value in {@code [0, 1)} from the seed, chunk coordinates and salt. */
    static double sample(long seed, int chunkX, int chunkZ, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= (long) chunkX * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) chunkZ * 0x165667B19E3779F9L;
        h ^= (long) salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
