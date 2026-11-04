package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bookkeeping of {@link BiomeCellSamples}: one ask per cell, and no answer surviving the world
 * it came from.
 *
 * <p>The biome source is a counter. Whether it answers with a real biome does not matter here —
 * what is checked is how often it is asked, and when the table is thrown away.
 */
class BiomeCellSamplesTest {

    private static final Climate.Sampler A =
            new Climate.Sampler(null, null, null, null, null, null, List.of());
    private static final Climate.Sampler B =
            new Climate.Sampler(null, null, null, null, null, null, List.of());

    private static Holder<Biome> first;
    private static Holder<Biome> second;
    private CountingSource source;
    private BiomeCellSamples samples;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        first = Holder.direct(biome(0x112233));
        second = Holder.direct(biome(0x445566));
    }

    @BeforeEach
    void freshTable() {
        source = new CountingSource(first);
        samples = BiomeCellSamples.current();
        samples.clear();
    }

    @Test
    void oneCellIsAskedOnce() {
        Holder<Biome> a = samples.at(source, A, 4, 8, 12);
        Holder<Biome> b = samples.at(source, A, 4, 8, 12);

        assertEquals(1, source.calls, "the second look at one cell must not reach the source");
        assertSame(first, a);
        assertSame(a, b);
    }

    @Test
    void twoCandidatesSharingCellsDoNotAskTwice() {
        // The caller rounds blocks to cells, so two candidates inside one cell (x 0..3, z 0..3)
        // are one entry; a cell they do not share costs one sample for both.
        samples.at(source, A, 0, 8, 0);
        samples.at(source, A, 0, 8, 0);
        samples.at(source, A, 0, 8, 1);
        assertEquals(2, source.calls);

        samples.at(source, A, 0, 8, 0);
        samples.at(source, A, 0, 8, 1);
        assertEquals(2, source.calls, "a new cell is asked once, the old ones not again");
    }

    @Test
    void anotherSamplerDropsTheTable() {
        samples.at(source, A, 0, 8, 0);
        assertEquals(1, source.calls);

        // A different dimension or a reloaded world: the old answer was for another seed.
        source.answer = second;
        Holder<Biome> after = samples.at(source, B, 0, 8, 0);

        assertEquals(2, source.calls, "the same cell in another world must be asked again");
        assertSame(second, after);
    }

    @Test
    void anotherSourceDropsTheTable() {
        samples.at(source, A, 0, 8, 0);
        assertEquals(1, source.calls);

        CountingSource other = new CountingSource(second);
        Holder<Biome> after = samples.at(other, A, 0, 8, 0);

        assertEquals(1, other.calls);
        assertSame(second, after);
    }

    @Test
    void theTableIsBounded() {
        int writes = BiomeCellSamples.MAX_ENTRIES + 4_000;
        for (int i = 0; i < writes; i++) {
            samples.at(source, A, i, 0, 0);
        }
        assertTrue(samples.size() <= BiomeCellSamples.MAX_ENTRIES,
                "a table of " + samples.size() + " entries overran " + BiomeCellSamples.MAX_ENTRIES);

        // Cleared whole rather than evicted: the first cell is asked again.
        int before = source.calls;
        samples.at(source, A, 0, 0, 0);
        assertEquals(before + 1, source.calls);
    }

    @Test
    void theEmptyMarkerIsNotARealCell() {
        long[] extremes = {
                BiomeCellSamples.key(0, 0, 0),
                BiomeCellSamples.key(0x1FFFFFF, 0x1FFF, 0x1FFFFFF),
                BiomeCellSamples.key(-0x1FFFFFF, -0x1000, -0x1FFFFFF),
                BiomeCellSamples.key(0, -0x1000, 0),
        };
        for (long key : extremes) {
            assertNotEquals(Long.MIN_VALUE, key, "a table key must never collide with the empty slot");
        }
        assertNotEquals(extremes[0], extremes[1], "far corners must not share a key");
        assertNotEquals(extremes[1], extremes[2]);
    }

    private static Biome biome(int color) {
        return new Biome.BiomeBuilder()
                .temperature(0.8f)
                .downfall(0.4f)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(color)
                        .waterColor(color)
                        .waterFogColor(color)
                        .skyColor(color)
                        .build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }

    /** A biome source that counts its calls and answers with whatever it is holding. */
    private static final class CountingSource extends BiomeSource {

        private Holder<Biome> answer;
        private int calls;

        private CountingSource(Holder<Biome> answer) {
            this.answer = answer;
        }

        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException("not a serialisable source");
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return Stream.of(answer);
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            calls++;
            return answer;
        }
    }
}
