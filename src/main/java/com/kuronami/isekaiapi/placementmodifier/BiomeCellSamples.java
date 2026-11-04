package com.kuronami.isekaiapi.placementmodifier;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;

/**
 * Per-thread memo of the biome cells one {@code biome_falloff} search touches.
 *
 * <p>A search asks the same cells over and over: one candidate walks every cell of its disc, and
 * the next candidate in the same chunk walks almost the same disc. A biome sample is not free — the
 * nether measures about 450 ns on this machine — and a chunk of 40 candidates with no biome in
 * range costs 77,000 samples, about 28 ms. The cells it asks number in the thousands instead, so
 * remembering them turns the second candidate's walk into array lookups.
 *
 * <p>Keys are biome cells, not blocks. The caller rounds a block position to its cell before asking
 * ({@code x >> 2}), which is what the search would sample anyway, so two candidates in different
 * corners of one cell share every entry. Values are {@link Holder}s rather than "is this the target"
 * answers, so the entry stays valid for a modifier with a different {@link net.minecraft.core.HolderSet}
 * and only the biome source decides what is stored.
 *
 * <p>Three bounds keep it honest:
 * <ul>
 *   <li>One cache per thread — worldgen runs on several threads and none of them may see a
 *       half-written table. A {@link ThreadLocal} costs one lookup per placement, not per sample.</li>
 *   <li>{@value #MAX_ENTRIES} entries, cleared whole when three quarters are used. A wrong answer is
 *       worse than a miss, so nothing is evicted by key; the next search simply starts empty.</li>
 *   <li>The source and the climate sampler are part of the identity. Another dimension, another
 *       world or a reloaded datapack hands back a different sampler, and the stale table — which
 *       knows nothing of seeds or dimension — is dropped before it can answer for them.</li>
 * </ul>
 *
 * <p>Not thread-safe by itself; {@link #current()} gives each thread its own.
 */
final class BiomeCellSamples {

    /** Entries held per thread. 16,384 cells is a little under two 64-block discs of full height. */
    static final int MAX_ENTRIES = 1 << 14;

    private static final int MASK = MAX_ENTRIES - 1;

    /** Cleared when this many slots are taken; linear probing needs room to stay short. */
    private static final int CLEAR_AT = MAX_ENTRIES * 3 / 4;

    /** How far linear probing may run before the key is treated as absent. */
    private static final int MAX_PROBE = 32;

    private static final long EMPTY = Long.MIN_VALUE;

    private static final ThreadLocal<BiomeCellSamples> PER_THREAD =
            ThreadLocal.withInitial(BiomeCellSamples::new);

    private final long[] keys = new long[MAX_ENTRIES];
    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] values = (Holder<Biome>[]) new Holder<?>[MAX_ENTRIES];
    private BiomeSource source;
    private Climate.Sampler sampler;
    private int size;

    private BiomeCellSamples() {
        Arrays.fill(keys, EMPTY);
    }

    /** The cache belonging to the calling thread. */
    static BiomeCellSamples current() {
        return PER_THREAD.get();
    }

    /**
     * The biome at one biome cell, asked of {@code source} at most once per thread until the table
     * is cleared.
     *
     * <p>{@code qx}, {@code qy} and {@code qz} are cell coordinates — block coordinates shifted
     * right by two — not blocks.
     */
    Holder<Biome> at(BiomeSource source, Climate.Sampler sampler, int qx, int qy, int qz) {
        if (source != this.source || sampler != this.sampler) {
            clear();
            this.source = source;
            this.sampler = sampler;
        }
        long key = key(qx, qy, qz);
        int start = slot(key);
        for (int i = 0; i < MAX_PROBE; i++) {
            int at = (start + i) & MASK;
            long held = keys[at];
            if (held == key) {
                return values[at];
            }
            if (held == EMPTY) {
                break;
            }
        }
        Holder<Biome> value = source.getNoiseBiome(qx, qy, qz, sampler);
        if (size >= CLEAR_AT) {
            clear();
            start = slot(key);
        }
        for (int i = 0; i <= MASK; i++) {
            int at = (start + i) & MASK;
            if (keys[at] == EMPTY) {
                keys[at] = key;
                values[at] = value;
                size++;
                break;
            }
        }
        return value;
    }

    /** How many cells are remembered; for tests and for reasoning about the size bound. */
    int size() {
        return size;
    }

    void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, null);
        size = 0;
    }

    /**
     * One cell as a long: 25 bits of x, 25 of z, 13 of y.
     *
     * <p>25 bits covers ±16.7 million cells, four times the ±30 million blocks a world can reach;
     * 13 bits covers ±4,096 cells of height. The top bit stays clear so {@link #EMPTY} cannot be a
     * real key.
     */
    static long key(int qx, int qy, int qz) {
        return ((long) (qx & 0x1FFFFFF) << 38)
                | ((long) (qz & 0x1FFFFFF) << 13)
                | (qy & 0x1FFF);
    }

    private static int slot(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> 50) & MASK;
    }
}
