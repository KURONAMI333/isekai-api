package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.gametest.ext.CheckerboardZone;
import com.kuronami.isekaiapi.gametest.ext.XParityPredicate;
import com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Gate 5 — third-party SPI extension proof. The variants under test
 * ({@code isekai_api_test:checkerboard} BiomeZone, {@code isekai_api_test:x_parity}
 * SpatialPredicate) are registered entirely from the gametest tree
 * ({@link com.kuronami.isekaiapi.gametest.ext.IsekaiTestExtensions}), which is excluded from the
 * published jar. Isekai's shipped code has zero reference to them. That they decode from JSON via
 * the registry-backed dispatch and then evaluate through the same seams as the built-ins is the
 * machine proof that a third-party mod extends every vocabulary by registry membership alone.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiSpiExtensionGameTests {

    private IsekaiSpiExtensionGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void thirdPartyBiomeZoneRegisteredAndUsable(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        DataResult<BiomeZone> result = BiomeZone.CODEC.parse(ops,
                JsonParser.parseString("{\"type\":\"isekai_api_test:checkerboard\",\"size\":16}"));
        BiomeZone zone = result.result().orElse(null);
        if (zone == null) {
            helper.fail("isekai_api_test:checkerboard did not decode — third-party SPI registration "
                    + "not visible to the dispatch: " + result.error().map(e -> e.message()).orElse("?"));
            return;
        }
        if (!(zone instanceof CheckerboardZone)) {
            helper.fail("decoded to the wrong type: " + zone.getClass().getName());
            return;
        }
        // Behavior: 16-block cells, so blocks 0..15 -> cell (0,0)=match, 16..31 -> cell (1,0)=miss, etc.
        if (!zone.test(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0))) {
            helper.fail("checkerboard cell (0,0) should match");
            return;
        }
        if (zone.test(QuartPos.fromBlock(16), 0, QuartPos.fromBlock(0))) {
            helper.fail("checkerboard cell (1,0) should NOT match");
            return;
        }
        if (!zone.test(QuartPos.fromBlock(32), 0, QuartPos.fromBlock(0))) {
            helper.fail("checkerboard cell (2,0) should match");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void thirdPartySpatialPredicateRegisteredAndUsable(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        DataResult<SpatialPredicate> result = SpatialPredicate.CODEC.parse(ops,
                JsonParser.parseString("{\"type\":\"isekai_api_test:x_parity\"}"));
        SpatialPredicate pred = result.result().orElse(null);
        if (pred == null) {
            helper.fail("isekai_api_test:x_parity did not decode — third-party SPI registration not "
                    + "visible to the dispatch: " + result.error().map(e -> e.message()).orElse("?"));
            return;
        }
        if (!(pred instanceof XParityPredicate)) {
            helper.fail("decoded to the wrong type: " + pred.getClass().getName());
            return;
        }
        var ctx = predicateContext(helper.getLevel());
        // Evaluates through the same EvaluationContext seam as the built-ins.
        if (!SpatialPredicateEvaluator.evaluate(pred, new BlockPos(0, 64, 0), ctx)) {
            helper.fail("x_parity should match even X (x=0)");
            return;
        }
        if (SpatialPredicateEvaluator.evaluate(pred, new BlockPos(1, 64, 0), ctx)) {
            helper.fail("x_parity should NOT match odd X (x=1)");
            return;
        }
        // And it composes with a built-in through the same registry dispatch.
        SpatialPredicate composed = new SpatialPredicate.And(List.of(pred, new SpatialPredicate.YInRange(0, 128)));
        if (!SpatialPredicateEvaluator.evaluate(composed, new BlockPos(0, 64, 0), ctx)) {
            helper.fail("And(x_parity, y_in_range) should match at (0,64)");
            return;
        }
        helper.succeed();
    }

    private static SpatialPredicateEvaluator.Context predicateContext(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        return new SpatialPredicateEvaluator.Context(
                generator, level, chunkSource.randomState(), generator.getBiomeSource());
    }
}
