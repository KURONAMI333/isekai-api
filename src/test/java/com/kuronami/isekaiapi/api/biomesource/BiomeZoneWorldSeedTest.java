package com.kuronami.isekaiapi.api.biomesource;

import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The world-seed contract for {@link BiomeZone}: a noise-backed zone must draw a different
 * pattern in every world and the same pattern for the same world seed. Before 2.1.0 the noise
 * came from the datapack's literal {@code seed} alone, so every player of a given pack got a
 * byte-identical biome layout — the defect these tests pin shut.
 *
 * <p>"Pattern" here is the boolean vector the zone produces over a fixed sample grid; two
 * patterns are compared by that vector, not by any single position (a single position agreeing
 * across seeds is expected roughly half the time and would make a flaky test).
 */
class BiomeZoneWorldSeedTest {

    private static HolderLookup.Provider registries;
    private static Holder<NormalNoise.NoiseParameters> noise;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
        // A direct, deliberately short-wavelength noise rather than a registry one: the sample
        // grid below has to cross several noise features, or two different fields would agree
        // everywhere on it and the seed-sensitivity assertions would pass vacuously.
        noise = Holder.direct(new NormalNoise.NoiseParameters(-4, 1.0, 1.0, 1.0));
    }

    // A 20x20 quart grid at y=16 — wide enough that two independent noise fields disagree
    // somewhere with overwhelming probability, small enough to stay instant.
    private static boolean[] pattern(BiomeZone zone) {
        boolean[] out = new boolean[400];
        int i = 0;
        for (int qx = 0; qx < 20; qx++) {
            for (int qz = 0; qz < 20; qz++) {
                out[i++] = zone.test(qx * 8, 16, qz * 8);
            }
        }
        return out;
    }

    private static BiomeZone.NoiseThreshold threshold(long zoneSeed) {
        return BiomeZone.NoiseThreshold.fromConfig(noise, zoneSeed, 0.0, 8.0, 8.0);
    }

    // ===== deriveSeed =====

    @Test void deriveSeed_isDeterministic() {
        assertEquals(BiomeZone.deriveSeed(42L, 7L), BiomeZone.deriveSeed(42L, 7L));
    }

    @Test void deriveSeed_separatesWorlds() {
        assertNotEquals(BiomeZone.deriveSeed(1L, 7L), BiomeZone.deriveSeed(2L, 7L));
        // Adjacent world seeds must not produce adjacent noise seeds.
        assertNotEquals(BiomeZone.deriveSeed(1L, 0L) + 1, BiomeZone.deriveSeed(2L, 0L));
    }

    @Test void deriveSeed_separatesZonesWithinAWorld() {
        assertNotEquals(BiomeZone.deriveSeed(99L, 1L), BiomeZone.deriveSeed(99L, 2L));
    }

    // ===== noise_threshold =====

    @Test void noiseThreshold_differentWorldSeeds_differentPattern() {
        BiomeZone.NoiseThreshold z = threshold(4001L);
        assertFalse(java.util.Arrays.equals(
                pattern(z.withWorldSeed(1L)), pattern(z.withWorldSeed(2L))));
    }

    @Test void noiseThreshold_sameWorldSeed_reproduces() {
        BiomeZone.NoiseThreshold a = threshold(4001L);
        BiomeZone.NoiseThreshold b = threshold(4001L);
        assertArrayEquals(pattern(a.withWorldSeed(777L)), pattern(b.withWorldSeed(777L)));
    }

    @Test void noiseThreshold_rebindingIsNotCumulative() {
        BiomeZone.NoiseThreshold z = threshold(4001L);
        // Binding a already-bound zone must re-derive from the codec `seed`, not from the
        // seed it currently samples with — otherwise a second bind drifts the pattern.
        assertArrayEquals(
                pattern(z.withWorldSeed(500L)),
                pattern(z.withWorldSeed(1L).withWorldSeed(500L)));
    }

    @Test void noiseThreshold_zoneSeedStillSeparatesZonesInOneWorld() {
        assertFalse(java.util.Arrays.equals(
                pattern(threshold(1L).withWorldSeed(123L)),
                pattern(threshold(2L).withWorldSeed(123L))));
    }

    @Test void noiseThreshold_codecFieldsSurviveBinding() {
        BiomeZone.NoiseThreshold z = threshold(4001L);
        BiomeZone.NoiseThreshold bound = (BiomeZone.NoiseThreshold) z.withWorldSeed(9L);
        // The JSON `seed` must not be overwritten by the derived value, or the zone would no
        // longer encode back to the datapack it was read from.
        assertEquals(4001L, bound.seed());
        assertEquals(z.noise(), bound.noise());
        assertEquals(z.threshold(), bound.threshold());
        assertEquals(z.sizeXz(), bound.sizeXz());
        assertEquals(z.sizeY(), bound.sizeY());
    }

    // ===== edge_jitter =====

    @Test void edgeJitter_differentWorldSeeds_differentPattern() {
        // A disc border is where jitter is observable: the offset only flips the result for
        // sample points near the edge, so centre the grid on the boundary.
        BiomeZone inner = new BiomeZone.WithinDistance(320.0, 0, 0);
        BiomeZone.EdgeJitter z = BiomeZone.EdgeJitter.fromConfig(inner, noise, 4002L, 24.0, 32.0);
        assertFalse(java.util.Arrays.equals(
                pattern(z.withWorldSeed(1L)), pattern(z.withWorldSeed(2L))));
    }

    @Test void edgeJitter_sameWorldSeed_reproduces() {
        BiomeZone inner = new BiomeZone.WithinDistance(320.0, 0, 0);
        BiomeZone a = BiomeZone.EdgeJitter.fromConfig(inner, noise, 4002L, 24.0, 32.0);
        BiomeZone b = BiomeZone.EdgeJitter.fromConfig(inner, noise, 4002L, 24.0, 32.0);
        assertArrayEquals(pattern(a.withWorldSeed(31L)), pattern(b.withWorldSeed(31L)));
    }

    @Test void edgeJitter_bindsItsInnerZoneToo() {
        BiomeZone.EdgeJitter z = BiomeZone.EdgeJitter.fromConfig(threshold(5L), noise, 6L, 8.0, 32.0);
        BiomeZone.EdgeJitter bound = (BiomeZone.EdgeJitter) z.withWorldSeed(88L);
        assertEquals(
                ((BiomeZone.NoiseThreshold) threshold(5L).withWorldSeed(88L)).sampler().getValue(1, 2, 3),
                ((BiomeZone.NoiseThreshold) bound.inner()).sampler().getValue(1, 2, 3));
    }

    // ===== combinators propagate =====

    @Test void not_propagatesWorldSeedToChild() {
        BiomeZone z = new BiomeZone.Not(threshold(11L));
        assertFalse(java.util.Arrays.equals(
                pattern(z.withWorldSeed(1L)), pattern(z.withWorldSeed(2L))));
    }

    @Test void and_propagatesWorldSeedToChildren() {
        BiomeZone z = new BiomeZone.And(List.of(new BiomeZone.YAbove(0), threshold(12L)));
        assertFalse(java.util.Arrays.equals(
                pattern(z.withWorldSeed(1L)), pattern(z.withWorldSeed(2L))));
    }

    @Test void or_propagatesWorldSeedToChildren() {
        BiomeZone z = new BiomeZone.Or(List.of(new BiomeZone.YBelow(-64), threshold(13L)));
        assertFalse(java.util.Arrays.equals(
                pattern(z.withWorldSeed(1L)), pattern(z.withWorldSeed(2L))));
    }

    // ===== zones that must NOT move with the seed =====

    @Test void geometricZones_areUnaffectedByWorldSeed() {
        for (BiomeZone z : List.of(
                new BiomeZone.Always(),
                new BiomeZone.YAbove(64),
                new BiomeZone.YBelow(64),
                new BiomeZone.YBetween(0, 100),
                new BiomeZone.WithinDistance(200.0, 0, 0),
                new BiomeZone.BeyondDistance(200.0, 0, 0))) {
            assertSame(z, z.withWorldSeed(12345L), z.getClass().getSimpleName());
            assertArrayEquals(pattern(z), pattern(z.withWorldSeed(12345L)),
                    z.getClass().getSimpleName());
        }
    }

    // ===== SPI: a third-party variant written before 2.1.0 =====

    /** Implements only the 1.0.0 surface — no {@code withWorldSeed}, no {@code children}. */
    private record LegacyThirdPartyZone(int parity) implements BiomeZone {
        static final MapCodec<LegacyThirdPartyZone> MAP_CODEC =
                MapCodec.unit(() -> new LegacyThirdPartyZone(0));
        @Override public boolean test(int qx, int qy, int qz) { return (qx & 1) == parity; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    @Test void thirdPartyZone_keepsWorkingUnbound() {
        BiomeZone z = new LegacyThirdPartyZone(0);
        assertSame(z, z.withWorldSeed(999L));
        assertArrayEquals(pattern(z), pattern(z.withWorldSeed(999L)));
    }

    @Test void thirdPartyZone_nestedInBuiltInCombinator_survivesBinding() {
        BiomeZone z = new BiomeZone.And(List.of(new LegacyThirdPartyZone(0), threshold(14L)));
        BiomeZone bound = z.withWorldSeed(999L);
        assertInstanceOf(BiomeZone.And.class, bound);
        assertInstanceOf(LegacyThirdPartyZone.class, ((BiomeZone.And) bound).all().get(0));
    }

    // ===== end to end through the biome source =====

    @Test void ruleBiomeSource_differentWorldSeeds_placeBiomesDifferently() {
        Holder<Biome> plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        List<RuleBiomeSource.Rule> rules =
                List.of(new RuleBiomeSource.Rule(threshold(4001L), desert));

        RuleBiomeSource a = new RuleBiomeSource(plains, rules);
        RuleBiomeSource b = new RuleBiomeSource(plains, rules);
        RuleBiomeSource c = new RuleBiomeSource(plains, rules);
        assertFalse(a.isBound());
        a.bindWorldSeed(1L);
        b.bindWorldSeed(2L);
        c.bindWorldSeed(1L);
        assertTrue(a.isBound());

        assertFalse(java.util.Arrays.equals(biomeGrid(a), biomeGrid(b)),
                "two world seeds produced an identical biome layout");
        assertArrayEquals(biomeGrid(a), biomeGrid(c),
                "the same world seed must reproduce the biome layout");
    }

    /** Sampler is unused by RuleBiomeSource (its rules are pure coordinate tests), hence null. */
    private static Object[] biomeGrid(RuleBiomeSource source) {
        Object[] out = new Object[400];
        int i = 0;
        for (int qx = 0; qx < 20; qx++) {
            for (int qz = 0; qz < 20; qz++) {
                out[i++] = source.getNoiseBiome(qx * 8, 16, qz * 8, null);
            }
        }
        return out;
    }
}
