package com.kuronami.isekaiapi.gametest.survey;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.survey.BiomeSurvey;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code /isekai survey biomes} — the share, the patch sizes and the climate percentiles have to be
 * right on distributions whose answer is known without a world.
 *
 * <p>The first two tests use {@code isekai_api:rule}, whose zones are pure coordinate geometry, so
 * the expected numbers are arithmetic: a half-plane cut through a disc is 50% of it, and a
 * {@code within_distance} circle of radius 64 is a patch 128 blocks across. Sampling is the same
 * {@link BiomeSurvey#run} the command calls, so the scan, the cluster labelling and the JSON all run
 * here rather than only the arithmetic.
 *
 * <p>The nether tests use the vanilla {@code minecraft:multi_noise} preset with a nether
 * {@link RandomState}, which is the case the tool was built for: no world to walk, and a dimension
 * whose biomes are not on the surface. The last test exists to print the wall time of a radius-2048
 * scan in the test logs; it asserts nothing, because a slow machine is not a failure.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class BiomeSurveyGameTests {

    /** No worldgen, so the plane only has to be a number the zones ignore. */
    private static final int PLANE_Y = 0;

    private static final String PLAINS_FALLBACK =
            "{\"type\":\"isekai_api:rule\",\"fallback\":\"minecraft:plains\",\"rules\":[";

    private BiomeSurveyGameTests() {}

    /**
     * A half-plane of desert cut through the middle of a radius-256 disc: the share must be half,
     * and the single desert patch runs off the rim, so it is counted but left out of the diameters.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void halfPlaneIsHalfTheDiscAndTouchesTheRim(GameTestHelper helper) {
        BiomeSource source = decode(helper, PLAINS_FALLBACK
                + "{\"zone\":{\"type\":\"isekai_api:box\","
                + "\"min_x\":-30000000,\"max_x\":-1,"
                + "\"min_z\":-30000000,\"max_z\":30000000},"
                + "\"biome\":\"minecraft:desert\"}]}");

        BiomeSurvey.Result result = BiomeSurvey.run(source, sampler(helper), "isekai_api:test",
                new BiomeSurvey.Params(0, PLANE_Y, 0, 256));

        BiomeSurvey.BiomeShare desert = shareOf(helper, result, "minecraft:desert");
        BiomeSurvey.BiomeShare plains = shareOf(helper, result, "minecraft:plains");
        if (Math.abs(desert.share() - 0.5) > 0.02) {
            helper.fail("a half-plane covers half the disc, measured " + desert.share()
                    + " (" + desert.cells() + " of " + result.sampledCells() + " cells)");
            return;
        }
        if (desert.cells() + plains.cells() != result.sampledCells()) {
            helper.fail("the two shares do not add up: " + desert.cells() + " + " + plains.cells()
                    + " != " + result.sampledCells());
            return;
        }

        // Each biome is one patch here, and both reach the rim: nothing may enter the diameters.
        BiomeSurvey.BiomeClusters desertClusters =
                clustersOf(helper, result, "minecraft:desert");
        if (desertClusters.clusters() != 1 || desertClusters.edgeClusters() != 1
                || desertClusters.measuredClusters() != 0) {
            helper.fail("the half-plane must be one rim-touching patch, got " + desertClusters);
            return;
        }
        if (desertClusters.medianDiameterBlocks() != 0.0
                || desertClusters.p90DiameterBlocks() != 0.0) {
            helper.fail("a patch that only touches the rim has no measured diameter, got "
                    + desertClusters.medianDiameterBlocks());
            return;
        }
        IsekaiApi.LOGGER.info("[Isekai] survey biomes half-plane: desert {} of {} cells ({}%), clusters {}",
                desert.cells(), result.sampledCells(),
                String.format(Locale.ROOT, "%.1f", desert.share() * 100.0), desertClusters);
        helper.succeed();
    }

    /**
     * One round desert patch of radius 64 inside a radius-256 plains disc: one cluster, no edge
     * contact, and a diameter of 128 blocks because that is the circle of the same area.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void roundPatchReportsOneClusterOfItsDiameter(GameTestHelper helper) {
        BiomeSource source = decode(helper, PLAINS_FALLBACK
                + "{\"zone\":{\"type\":\"isekai_api:within_distance\",\"radius\":64,"
                + "\"center_x\":-100,\"center_z\":100},"
                + "\"biome\":\"minecraft:desert\"}]}");

        // The patch spans blocks -164..-36 by 36..164, so its farthest corner is 232 blocks out:
        // it sits wholly inside the radius-256 disc and never meets the rim.
        BiomeSurvey.Result result = BiomeSurvey.run(source, sampler(helper), "isekai_api:test",
                new BiomeSurvey.Params(0, PLANE_Y, 0, 256));

        BiomeSurvey.BiomeClusters desert = clustersOf(helper, result, "minecraft:desert");
        BiomeSurvey.BiomeShare desertShare = shareOf(helper, result, "minecraft:desert");
        if (desert.clusters() != 1) {
            helper.fail("expected one desert patch, got " + desert.clusters() + " (" + desert + ") "
                    + "shares " + result.shares() + " clusters " + result.clusters());
            return;
        }
        if (desert.edgeClusters() != 0 || desert.measuredClusters() != 1) {
            helper.fail("a patch inside the disc must not count as edge-touching, got " + desert);
            return;
        }
        // A radius-64 circle sampled on the 4-block lattice is about 800 cells, and the diameter of
        // the same area stays within a couple of blocks of 128 whatever the lattice picks up.
        if (Math.abs(desert.medianDiameterBlocks() - 128.0) > 12.0) {
            helper.fail("a radius-64 patch is 128 across, measured "
                    + desert.medianDiameterBlocks() + " (" + desert);
            return;
        }
        if (Math.abs(desert.p90DiameterBlocks() - desert.medianDiameterBlocks()) > 0.001) {
            helper.fail("one patch means p90 equals the median, got " + desert);
            return;
        }
        IsekaiApi.LOGGER.info("[Isekai] survey biomes round patch: desert {} of {} cells ({}%), {}",
                desertShare.cells(), result.sampledCells(),
                String.format(Locale.ROOT, "%.1f", desertShare.share() * 100.0), desert);
        helper.succeed();
    }

    /**
     * The nether preset on a nether {@link RandomState}: the six axes come back as increasing
     * percentiles, and temperature actually varies across a radius-512 disc (otherwise the
     * percentiles would be a flat line and the tool would be reporting nothing).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void netherClimatePercentilesAreOrderedAndVary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BiomeSource source = decode(helper, NETHER);
        Climate.Sampler sampler = netherSampler(level);

        BiomeSurvey.Result result = BiomeSurvey.run(source, sampler, "minecraft:the_nether",
                new BiomeSurvey.Params(0, 32, 0, 512));

        if (result.shares().size() < 2) {
            helper.fail("the nether preset has several biomes, found " + result.shares());
            return;
        }
        for (BiomeSurvey.ClimateAxis axis : result.climate()) {
            for (int i = 1; i < axis.percentiles().size(); i++) {
                if (axis.percentiles().get(i) < axis.percentiles().get(i - 1)) {
                    helper.fail(axis.axis() + " percentiles are not increasing: "
                            + axis.percentiles());
                    return;
                }
            }
        }
        BiomeSurvey.ClimateAxis temperature = axisOf(helper, result, "temperature");
        double p1 = temperature.percentiles().get(0);
        double p99 = temperature.percentiles().get(temperature.percentiles().size() - 1);
        if (p1 == p99) {
            helper.fail("temperature does not vary across the disc: every percentile is " + p1);
            return;
        }
        IsekaiApi.LOGGER.info("[Isekai] survey biomes nether climate: {} biomes, temperature"
                        + " p1 {} p50 {} p99 {}, {} cells", result.shares().size(), p1,
                temperature.percentiles().get(4), p99, result.sampledCells());
        helper.succeed();
    }

    /**
     * The radius-2048 measurement the docs quote. Nothing is asserted: the point is to print the
     * time in the test log on the machine that ran it, so the documented number is not a guess.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 600)
    public static void radius2048TimingIsLogged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BiomeSource source = decode(helper, NETHER);
        Climate.Sampler sampler = netherSampler(level);

        BiomeSurvey.Result result = BiomeSurvey.run(source, sampler, "minecraft:the_nether",
                new BiomeSurvey.Params(0, 32, 0, 2048));
        IsekaiApi.LOGGER.info("[Isekai] survey biomes radius 2048 ({} cells, climate every {} cells)"
                + " took {} ms", result.sampledCells(), result.climateStride(),
                result.elapsedMillis());
        helper.succeed();
    }

    /** Every documented shape of the subcommand must parse, including the optional plane. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void biomesCommandAcceptsEveryDocumentedShape(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();

        String[][] accepted = {
                {"isekai survey biomes 256", "radius"},
                {"isekai survey biomes 256 center 0 64 0", "center"},
                {"isekai survey biomes 256 center 0 64 0 y -32", "y"},
                {"isekai survey biomes 256 y 64", "y"},
        };
        for (String[] shape : accepted) {
            var parsed = dispatcher.parse(shape[0], source);
            if (!parsed.getExceptions().isEmpty()) {
                helper.fail("\"" + shape[0] + "\" did not parse: "
                        + parsed.getExceptions().values().iterator().next().getMessage());
                return;
            }
            if (parsed.getReader().canRead()) {
                helper.fail("\"" + shape[0] + "\" left \"" + parsed.getReader().getRemaining()
                        + "\" unread");
                return;
            }
            var nodes = parsed.getContext().getNodes();
            String last = nodes.get(nodes.size() - 1).getNode().getName();
            if (!shape[1].equals(last)) {
                helper.fail("\"" + shape[0] + "\" ended at \"" + last + "\", expected \""
                        + shape[1] + "\"");
                return;
            }
        }

        // The radius is required and bounded: a biome disc is cheap per cell but the radius is what
        // decides the cost, and a typo must not become a 3.3-million-cell scan. Brigadier does not
        // raise on a missing *required* argument, it just stops at the last node it could match, so
        // the test is whether the parse reached the radius argument at all.
        for (String tooBig : new String[] {"isekai survey biomes", "isekai survey biomes 4097",
                "isekai survey biomes 8"}) {
            var parsed = dispatcher.parse(tooBig, source);
            boolean reachedRadius = parsed.getExceptions().isEmpty() && !parsed.getReader().canRead()
                    && parsed.getContext().getNodes().stream()
                            .anyMatch(node -> "radius".equals(node.getNode().getName()));
            if (reachedRadius) {
                helper.fail("\"" + tooBig + "\" parsed, but the radius is required and 16..4096");
                return;
            }
        }
        helper.succeed();
    }

    /** The nether's own preset, read through the same codec a datapack would use. */
    private static final String NETHER =
            "{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:nether\"}";

    private static BiomeSource decode(GameTestHelper helper, String json) {
        return BiomeSource.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                        JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("biome source did not decode: " + msg));
    }

    /**
     * A sampler built from the nether's noise settings, the way the nether level builds one at
     * load. The overworld sampler would answer with overworld climate values, which would then be
     * looked up in the nether's parameter list and pick biomes for the wrong reason.
     */
    private static Climate.Sampler netherSampler(ServerLevel level) {
        return RandomState.create(
                level.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.NETHER).value(),
                level.registryAccess().lookupOrThrow(Registries.NOISE),
                level.getSeed()).sampler();
    }

    private static Climate.Sampler sampler(GameTestHelper helper) {
        return helper.getLevel().getChunkSource().randomState().sampler();
    }

    private static BiomeSurvey.BiomeShare shareOf(GameTestHelper helper, BiomeSurvey.Result result,
                                                  String biome) {
        for (BiomeSurvey.BiomeShare share : result.shares()) {
            if (share.biome().equals(biome)) {
                return share;
            }
        }
        throw new AssertionError("no share for " + biome + " in " + result.shares());
    }

    private static BiomeSurvey.BiomeClusters clustersOf(GameTestHelper helper,
                                                        BiomeSurvey.Result result, String biome) {
        for (BiomeSurvey.BiomeClusters clusters : result.clusters()) {
            if (clusters.biome().equals(biome)) {
                return clusters;
            }
        }
        throw new AssertionError("no clusters for " + biome + " in " + result.clusters());
    }

    private static BiomeSurvey.ClimateAxis axisOf(GameTestHelper helper, BiomeSurvey.Result result,
                                                  String axis) {
        for (BiomeSurvey.ClimateAxis entry : result.climate()) {
            if (entry.axis().equals(axis)) {
                return entry;
            }
        }
        throw new AssertionError("no climate axis " + axis + " in " + result.climate());
    }
}