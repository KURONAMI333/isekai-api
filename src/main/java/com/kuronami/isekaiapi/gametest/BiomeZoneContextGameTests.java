package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.biomesource.OverrideBiomeSource;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The two world-state zones against a real registry and a climate sampler that varies: {@code base_biome}
 * reads the wrapped source's answer, {@code climate} reads the vanilla sample.
 *
 * <p>The base here is the vanilla nether {@code multi_noise} preset, so "the base biome" is a
 * distribution no zone can predict from coordinates alone — which is exactly the point of the
 * condition. The marker biome is {@code minecraft:plains}, which the nether preset cannot produce,
 * so every plains cell is the override firing.
 *
 * <p>Assertions scan a wide XZ window at several heights rather than a handful of points, because
 * the property under test is spatial ("the marker only appears where the base says so") and a
 * single lucky point would not distinguish it from a zone that matches everywhere.
 *
 * <p>The sampler is built in the test rather than taken from the level: the game test server
 * replaces the overworld noise settings with the test preset, whose axes are all
 * {@code minecraft:zero}, which would make "matches the sample" and "matches everywhere" agree.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class BiomeZoneContextGameTests {

    private BiomeZoneContextGameTests() {}

    private static final String MARKER = "minecraft:plains";
    private static final String NETHER_BASE =
            "{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:nether\"}";
    private static final String NOISE = "{\"firstOctave\":0,\"amplitudes\":[1.0]}";

    private static final int QUART_MIN = -256;
    private static final int QUART_MAX = 256;
    private static final int QUART_STEP = 4;
    private static final int[] Y_LEVELS = {4, 16, 28};
    /** The six climate axes, in the order of {@link Climate.TargetPoint}'s record components. */
    private static final String[] AXES = {"temperature", "humidity", "continentalness", "erosion",
            "weirdness", "depth"};

    // ---- decode helpers -------------------------------------------------

    private static BiomeSource source(GameTestHelper helper, String json) {
        BiomeSource source = BiomeSource.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                        JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("biome source did not decode: " + msg));
        return source;
    }

    private static OverrideBiomeSource override(GameTestHelper helper, String base, String overrides) {
        BiomeSource decoded = source(helper, "{\"type\":\"isekai_api:override\",\"base\":" + base
                + ",\"overrides\":" + overrides + "}");
        if (!(decoded instanceof OverrideBiomeSource override)) {
            throw new AssertionError("expected an OverrideBiomeSource, got " + decoded.getClass().getName());
        }
        return override;
    }

    private static BiomeZone zone(GameTestHelper helper, String json) {
        return BiomeZone.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                        JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("zone did not decode: " + msg));
    }

    /**
     * A deterministic, smoothly varying stand-in for a dimension's climate. The game test server
     * replaces the overworld noise settings with the test preset (every axis is
     * {@code minecraft:zero}), so the level's own sampler cannot exercise {@code climate} at all.
     * This one varies temperature, humidity and weirdness, the three axes the nether preset
     * distinguishes its biomes by, and is repeatable run to run.
     */
    private record ClimateWave(double xzScale, double zScale, double phase) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return Math.sin(context.blockX() * xzScale + phase)
                    * Math.cos(context.blockZ() * zScale + phase * 1.7);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return this;
        }

        @Override
        public double minValue() {
            return -1.0;
        }

        @Override
        public double maxValue() {
            return 1.0;
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return null;
        }
    }

    private static final DensityFunction FLAT = new ClimateWave(0.0, 0.0, 0.0);

    private static Climate.Sampler sampler(GameTestHelper helper) {
        return new Climate.Sampler(
                new ClimateWave(1.0 / 700.0, 1.0 / 900.0, 0.0),
                new ClimateWave(1.0 / 900.0, 1.0 / 1100.0, 1.3),
                FLAT,
                FLAT,
                FLAT,
                new ClimateWave(1.0 / 500.0, 1.0 / 800.0, 2.1),
                List.of());
    }

    private static String key(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().toString()).orElse("<direct>");
    }

    // ---- scan helpers ---------------------------------------------------

    /** The base biomes present in the scan window, most common first. */
    private static List<String> baseBiomesByFrequency(BiomeSource base, Climate.Sampler sampler) {
        Map<String, Integer> counts = new HashMap<>();
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    counts.merge(key(base.getNoiseBiome(x, y, z, sampler)), 1, Integer::sum);
                }
            }
        }
        List<String> ordered = new ArrayList<>(counts.keySet());
        ordered.sort(Comparator.comparingInt((String k) -> counts.get(k)).reversed());
        return ordered;
    }

    /** How many cells the override marks, and how many of them sit outside the allowed base set. */
    private record Containment(int cells, int markers, int outsideParent) {}

    private static Containment scanContainment(BiomeSource base, BiomeSource over,
                                               Climate.Sampler sampler,
                                               Predicate<Holder<Biome>> parent) {
        int cells = 0;
        int markers = 0;
        int outsideParent = 0;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    cells++;
                    if (!key(over.getNoiseBiome(x, y, z, sampler)).equals(MARKER)) continue;
                    markers++;
                    if (!parent.test(base.getNoiseBiome(x, y, z, sampler))) outsideParent++;
                }
            }
        }
        return new Containment(cells, markers, outsideParent);
    }

    private static void assertContained(GameTestHelper helper, String variant, Containment result) {
        IsekaiApi.LOGGER.info("[isekai-zone-base-biome] {}: {} of {} cells marked, {} outside the parent",
                variant, result.markers(), result.cells(), result.outsideParent());
        if (result.markers() == 0) {
            helper.fail("base_biome (" + variant + "): the override never fired in "
                    + result.cells() + " scanned cells", net.minecraft.core.BlockPos.ZERO);
        }
        if (result.outsideParent() != 0) {
            helper.fail("base_biome (" + variant + "): " + result.outsideParent() + " of "
                    + result.markers() + " marked cells sit outside the parent biome",
                    net.minecraft.core.BlockPos.ZERO);
        }
    }

    private static String baseBiomeZone(String biomeJson) {
        return "{\"type\":\"isekai_api:base_biome\",\"biome\":" + biomeJson + "}";
    }

    private static String andBaseBiomeWithNoise(String biomeJson) {
        return "{\"type\":\"isekai_api:and\",\"all\":["
                + baseBiomeZone(biomeJson)
                + ",{\"type\":\"isekai_api:noise_threshold\",\"noise\":" + NOISE + "}]}";
    }

    private static String singleOverride(String zoneJson, String biome) {
        return "[{\"zone\":" + zoneJson + ",\"biome\":\"" + biome + "\"}]";
    }

    // ---- behaviour ------------------------------------------------------

    /**
     * The core promise: a zone {@code and(base_biome X, ...)} may only fire where the base
     * answers X. Checked with a single id, with a {@code #tag}, and with a list of ids.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void baseBiomeKeepsTheOverrideInsideTheParentBiome(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource base = source(helper, NETHER_BASE);
        List<String> observed = baseBiomesByFrequency(base, sampler);
        String target = observed.get(0);

        OverrideBiomeSource byId = override(helper, NETHER_BASE,
                singleOverride(andBaseBiomeWithNoise("\"" + target + "\""), MARKER));
        assertContained(helper, "id " + target,
                scanContainment(base, byId, sampler, h -> key(h).equals(target)));

        OverrideBiomeSource byTag = override(helper, NETHER_BASE,
                singleOverride(andBaseBiomeWithNoise("\"#minecraft:is_nether\""), MARKER));
        assertContained(helper, "tag #minecraft:is_nether",
                scanContainment(base, byTag, sampler, h -> h.is(BiomeTags.IS_NETHER)));

        String second = observed.size() > 1 ? observed.get(1) : target;
        if (observed.size() < 2) {
            helper.fail("the nether preset produced only " + observed
                    + " over the scan window, so the list variant would not test a list",
                    net.minecraft.core.BlockPos.ZERO);
        }
        List<String> pair = List.of(target, second);
        String listJson = "[\"" + pair.get(0) + "\",\"" + pair.get(1) + "\"]";
        OverrideBiomeSource byList = override(helper, NETHER_BASE,
                singleOverride(andBaseBiomeWithNoise(listJson), MARKER));
        assertContained(helper, "list " + pair,
                scanContainment(base, byList, sampler, h -> pair.contains(key(h))));

        helper.succeed();
    }

    /** Everything the zone does not claim has to be the base source's own answer, unchanged. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void unmarkedCellsKeepTheBaseAnswer(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource base = source(helper, NETHER_BASE);
        String target = baseBiomesByFrequency(base, sampler).get(0);
        OverrideBiomeSource over = override(helper, NETHER_BASE,
                singleOverride(andBaseBiomeWithNoise("\"" + target + "\""), MARKER));

        int cells = 0;
        int markers = 0;
        int mismatches = 0;
        String firstMismatch = null;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    cells++;
                    String got = key(over.getNoiseBiome(x, y, z, sampler));
                    if (got.equals(MARKER)) {
                        markers++;
                        continue;
                    }
                    String expected = key(base.getNoiseBiome(x, y, z, sampler));
                    if (!got.equals(expected)) {
                        mismatches++;
                        if (firstMismatch == null) {
                            firstMismatch = "quart (" + x + "," + y + "," + z + "): " + got
                                    + " instead of " + expected;
                        }
                    }
                }
            }
        }
        if (markers == 0) {
            helper.fail("base_biome never fired, so the fall-through was not exercised",
                    net.minecraft.core.BlockPos.ZERO);
        }
        IsekaiApi.LOGGER.info("[isekai-zone-base-biome] fall-through: {} marked, {} unmarked, {} mismatches",
                markers, cells - markers, mismatches);
        if (mismatches != 0) {
            helper.fail(mismatches + " of " + (cells - markers)
                    + " unmarked cells differ from the base answer; first " + firstMismatch,
                    net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    /**
     * The {@code climate} zone has to agree with the sampler cell by cell. Which axis carries the
     * band is picked from the sampler's own readings (the axis with the widest spread on this
     * level), so the test adapts to the level's climate instead of depending on a band written
     * down by hand. A sampler that is flat on every axis is reported as such rather than passing
     * vacuously.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void climateMatchesExactlyWhereTheSampleIsInRange(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);

        List<Climate.TargetPoint> points = new ArrayList<>();
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    points.add(sampler.sample(x, y, z));
                }
            }
        }

        long[][] sorted = new long[AXES.length][points.size()];
        int[] distinct = new int[AXES.length];
        int bestAxis = -1;
        for (int axis = 0; axis < AXES.length; axis++) {
            for (int i = 0; i < points.size(); i++) {
                sorted[axis][i] = axisValue(points.get(i), axis);
            }
            Arrays.sort(sorted[axis]);
            distinct[axis] = 1;
            for (int i = 1; i < sorted[axis].length; i++) {
                if (sorted[axis][i] != sorted[axis][i - 1]) distinct[axis]++;
            }
            IsekaiApi.LOGGER.info("[isekai-zone-climate] {}: {} distinct values over {} cells, range [{}, {}]",
                    AXES[axis], distinct[axis], points.size(),
                    sorted[axis][0], sorted[axis][sorted[axis].length - 1]);
            if (bestAxis < 0 || distinct[axis] > distinct[bestAxis]) bestAxis = axis;
        }
        if (distinct[bestAxis] < 2) {
            helper.fail("the level's climate sampler returns one value on every axis, so a band "
                    + "cannot be cut and a match-everywhere zone would pass this test for the wrong reason",
                    net.minecraft.core.BlockPos.ZERO);
        }

        long[] values = sorted[bestAxis];
        float low = Climate.unquantizeCoord(values[values.length / 3]);
        float high = Climate.unquantizeCoord(values[values.length * 2 / 3]);
        String zoneJson = "{\"type\":\"isekai_api:climate\",\"" + AXES[bestAxis] + "\":["
                + low + "," + high + "]}";
        BiomeZone.ClimateMatch zone = (BiomeZone.ClimateMatch) zone(helper, zoneJson);
        Climate.Parameter band = axisParameter(zone, bestAxis);
        long min = band.min();
        long max = band.max();
        OverrideBiomeSource over = override(helper, "{\"type\":\"minecraft:fixed\",\"biome\":\"minecraft:plains\"}",
                singleOverride(zoneJson, "minecraft:jungle"));

        int cells = points.size();
        int inside = 0;
        for (Climate.TargetPoint point : points) {
            long t = axisValue(point, bestAxis);
            if (t >= min && t <= max) inside++;
        }
        int mismatches = 0;
        String firstMismatch = null;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    long t = axisValue(sampler.sample(x, y, z), bestAxis);
                    boolean expectedInside = t >= min && t <= max;
                    boolean gotInside = key(over.getNoiseBiome(x, y, z, sampler)).equals("minecraft:jungle");
                    if (gotInside != expectedInside) {
                        mismatches++;
                        if (firstMismatch == null) {
                            firstMismatch = "quart (" + x + "," + y + "," + z + "): "
                                    + AXES[bestAxis] + " " + t + " in [" + min + "," + max
                                    + "] expectedInside=" + expectedInside
                                    + " but gotInside=" + gotInside;
                        }
                    }
                }
            }
        }
        IsekaiApi.LOGGER.info("[isekai-zone-climate] axis {} band [{}, {}] matched {} of {} cells",
                AXES[bestAxis], min, max, inside, cells);
        if (inside == 0 || inside == cells) {
            helper.fail("the chosen " + AXES[bestAxis] + " band [" + min + "," + max + "] matched "
                    + inside + " of " + cells + " cells, so both branches were not exercised",
                    net.minecraft.core.BlockPos.ZERO);
        }
        if (mismatches != 0) {
            helper.fail(mismatches + " climate mismatches; first " + firstMismatch,
                    net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    private static long axisValue(Climate.TargetPoint point, int axis) {
        return switch (axis) {
            case 0 -> point.temperature();
            case 1 -> point.humidity();
            case 2 -> point.continentalness();
            case 3 -> point.erosion();
            case 4 -> point.weirdness();
            case 5 -> point.depth();
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }

    private static Climate.Parameter axisParameter(BiomeZone.ClimateMatch zone, int axis) {
        return switch (axis) {
            case 0 -> zone.temperature().orElseThrow();
            case 1 -> zone.humidity().orElseThrow();
            case 2 -> zone.continentalness().orElseThrow();
            case 3 -> zone.erosion().orElseThrow();
            case 4 -> zone.weirdness().orElseThrow();
            case 5 -> zone.depth().orElseThrow();
            default -> throw new IllegalArgumentException("axis " + axis);
        };
    }

    /** Inside {@code isekai_api:rule} there is no wrapped source, so {@code base_biome} is false. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void baseBiomeNeverMatchesInsideARule(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource rule = source(helper, "{\"type\":\"isekai_api:rule\","
                + "\"fallback\":\"minecraft:plains\",\"rules\":[{\"zone\":"
                + baseBiomeZone("\"minecraft:plains\"") + ",\"biome\":\"minecraft:jungle\"}]}");

        int cells = 0;
        int jungle = 0;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    cells++;
                    if (key(rule.getNoiseBiome(x, y, z, sampler)).equals("minecraft:jungle")) jungle++;
                }
            }
        }
        if (jungle != 0) {
            helper.fail("base_biome matched in " + jungle + " of " + cells
                    + " cells inside isekai_api:rule, but that source has no base to ask",
                    net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    /** The context has to survive {@code edge_jitter}, which replaces the coordinate it asks at. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void contextReachesBaseBiomeUnderEdgeJitter(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource base = source(helper, NETHER_BASE);
        String target = baseBiomesByFrequency(base, sampler).get(0);
        String jittered = "{\"type\":\"isekai_api:edge_jitter\",\"inner\":"
                + baseBiomeZone("\"" + target + "\"") + ",\"noise\":" + NOISE
                + ",\"strength\":16.0}";
        OverrideBiomeSource over = override(helper, NETHER_BASE,
                singleOverride(jittered, MARKER));

        int markers = 0;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += QUART_STEP) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += QUART_STEP) {
                    if (key(over.getNoiseBiome(x, y, z, sampler)).equals(MARKER)) markers++;
                }
            }
        }
        if (markers == 0) {
            helper.fail("edge_jitter(base_biome " + target + ") never fired, so the context did not"
                    + " reach the inner zone", net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    /** Both zone types encode back to JSON and decode into a source with the same answers. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void bothZonesRoundTripThroughJson(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource base = source(helper, NETHER_BASE);
        String target = baseBiomesByFrequency(base, sampler).get(0);
        String json = "{\"type\":\"isekai_api:override\",\"base\":" + NETHER_BASE + ",\"overrides\":["
                + "{\"zone\":{\"type\":\"isekai_api:and\",\"all\":["
                + baseBiomeZone("\"" + target + "\"")
                + ",{\"type\":\"isekai_api:climate\",\"temperature\":[-1.0,1.0]}]},"
                + "\"biome\":\"" + MARKER + "\"}]}";

        OverrideBiomeSource decoded = (OverrideBiomeSource) source(helper, json);
        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        com.google.gson.JsonElement encoded = BiomeSource.CODEC.encodeStart(ops, decoded)
                .getOrThrow(msg -> new AssertionError("biome source did not encode: " + msg));
        String text = encoded.toString();
        if (!text.contains("isekai_api:base_biome") || !text.contains("isekai_api:climate")) {
            helper.fail("the encoded JSON lost a zone type: " + text, net.minecraft.core.BlockPos.ZERO);
        }
        OverrideBiomeSource again = (OverrideBiomeSource) source(helper, text);

        int mismatches = 0;
        String firstMismatch = null;
        for (int y : Y_LEVELS) {
            for (int x = QUART_MIN; x <= QUART_MAX; x += 32) {
                for (int z = QUART_MIN; z <= QUART_MAX; z += 32) {
                    String a = key(decoded.getNoiseBiome(x, y, z, sampler));
                    String b = key(again.getNoiseBiome(x, y, z, sampler));
                    if (!a.equals(b)) {
                        mismatches++;
                        if (firstMismatch == null) {
                            firstMismatch = "quart (" + x + "," + y + "," + z + "): " + a + " vs " + b;
                        }
                    }
                }
            }
        }
        if (mismatches != 0) {
            helper.fail(mismatches + " answers changed after the round trip; first " + firstMismatch,
                    net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    // ---- cost -----------------------------------------------------------

    /**
     * Not a pass/fail threshold — a recorded measurement. What matters is the shape: asking the
     * base once per sample is what keeps a stack of fifteen {@code base_biome} overrides from
     * costing fifteen base samples, and that is visible in the numbers here. Log only.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 6000)
    public static void timingOfContextZonesIsLogged(GameTestHelper helper) {
        Climate.Sampler sampler = sampler(helper);
        BiomeSource base = source(helper, NETHER_BASE);
        List<String> observed = baseBiomesByFrequency(base, sampler);

        List<String> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(observed.get(i % observed.size()));

        List<String> entryList = new ArrayList<>();
        String[] markers = {"minecraft:plains", "minecraft:jungle", "minecraft:desert",
                "minecraft:taiga", "minecraft:swamp"};
        for (int b = 0; b < 5; b++) {
            for (int v = 0; v < 3; v++) {
                entryList.add("{\"zone\":{\"type\":\"isekai_api:and\",\"all\":["
                        + baseBiomeZone("\"" + five.get(b) + "\"")
                        + ",{\"type\":\"isekai_api:noise_threshold\",\"noise\":" + NOISE
                        + ",\"seed\":" + (v + 1) + "}]},\"biome\":\"" + markers[b] + "\"}");
            }
        }
        String baseBiomeEntries = String.join(",", entryList);
        List<String> reversed = new ArrayList<>(entryList);
        java.util.Collections.reverse(reversed);
        String reversedEntries = String.join(",", reversed);

        StringBuilder climateEntries = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            float lo = -1.0F + i * 0.4F;
            float hi = lo + 0.4F;
            if (climateEntries.length() != 0) climateEntries.append(',');
            climateEntries.append("{\"zone\":{\"type\":\"isekai_api:climate\",\"temperature\":[")
                    .append(lo).append(',').append(hi).append("]},\"biome\":\"")
                    .append(markers[i]).append("\"}");
        }

        Map<String, BiomeSource> stacks = new LinkedHashMap<>();
        stacks.put("a nether multi_noise alone", base);
        stacks.put("b override with 0 overrides", override(helper, NETHER_BASE, "[]"));
        stacks.put("c 15 and(base_biome, noise_threshold)", override(helper, NETHER_BASE,
                "[" + baseBiomeEntries + "]"));
        stacks.put("d same 15 entries reversed", override(helper, NETHER_BASE,
                "[" + reversedEntries + "]"));
        stacks.put("e 5 climate-only overrides", override(helper, NETHER_BASE,
                "[" + climateEntries + "]"));

        Random random = new Random(20261111L);
        int[] xs = new int[200_000];
        int[] ys = new int[200_000];
        int[] zs = new int[200_000];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = random.nextInt(4000) - 2000;
            ys[i] = random.nextInt(32);
            zs[i] = random.nextInt(4000) - 2000;
        }

        IsekaiApi.LOGGER.info("[isekai-zone-timing] {} random quart samples per run, warmup + 3 runs, median",
                xs.length);
        for (Map.Entry<String, BiomeSource> e : stacks.entrySet()) {
            measure(e.getValue(), xs, ys, zs, sampler); // warmup
            long[] runNanos = new long[3];
            for (int r = 0; r < runNanos.length; r++) {
                long start = System.nanoTime();
                measure(e.getValue(), xs, ys, zs, sampler);
                runNanos[r] = System.nanoTime() - start;
            }
            long[] sortedRuns = runNanos.clone();
            Arrays.sort(sortedRuns);
            double medianNsPerCall = sortedRuns[1] / (double) xs.length;
            IsekaiApi.LOGGER.info("[isekai-zone-timing] {}: median {} ns/call, runs {} (ns/call)",
                    e.getKey(), String.format(java.util.Locale.ROOT, "%.1f", medianNsPerCall),
                    String.format(java.util.Locale.ROOT, "%.1f / %.1f / %.1f",
                            runNanos[0] / (double) xs.length,
                            runNanos[1] / (double) xs.length,
                            runNanos[2] / (double) xs.length));
        }
        helper.succeed();
    }

    private static long measure(BiomeSource source, int[] xs, int[] ys, int[] zs,
                                Climate.Sampler sampler) {
        int sink = 0;
        for (int i = 0; i < xs.length; i++) {
            sink += source.getNoiseBiome(xs[i], ys[i], zs[i], sampler).value()
                    .getSpecialEffects().getSkyColor();
        }
        return sink;
    }
}
