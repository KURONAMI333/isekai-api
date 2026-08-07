package com.kuronami.isekaiapi.gametest;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.AtmosphereOverride;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.RemapTargets;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.structuremodifier.ApplyWorldshapeStructureModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * Headless server-in-the-loop verification of Isekai's chunk-gen adaptation layer.
 *
 * <p>These tests run under {@code ./gradlew runGameTestServer}: a real server boots, the
 * vanilla rule snapshot is scanned at {@code ServerAboutToStartEvent}, and each test drives
 * the actual remap/modifier logic against the live registries obtained from
 * {@link GameTestHelper#getLevel()}. GameTest's fixed flat world can't generate a custom
 * worldshape, so — rather than inspect generated chunks — each test exercises the real
 * appliers/evaluators/biome-source against real {@code Holder}s and asserts the result the
 * modifier pipeline would feed back into the registry. This is exactly the code W2 rewrites
 * (instanceof dispatch → interface methods), so these tests are the regression net for it.
 *
 * <p>Matrix coverage (HANDOFF Phase 2): items 1 (ore Y-remap), 2 (structure exclusion),
 * 3 (predicate gate), 5 (rule biome source), 6 (atmosphere override), 7 (reload re-scan)
 * are green here. Item 4 (surface block in a generated chunk) is GameTest-unreachable — it
 * lives in noise/surface-rule chunk generation with no per-biome builder seam — and is
 * documented in {@code GAP_LOG.md} with a codec-level alternative.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiWorldgenGameTests {

    private IsekaiWorldgenGameTests() {}

    /** Overworld build-height defaults used as the linear remap source envelope in tests. */
    private static final int WORLD_BOTTOM = -64;
    private static final int WORLD_TOP = 319;

    /** A deliberately narrow playable band; every remapped ore must land inside it. */
    private static final VerticalRange PLAYABLE_BAND =
            new VerticalRange(100, 140, HeightDistribution.UNIFORM);

    // =====================================================================
    // Phase 1 — smoke (Gate 1): the snapshot scanned and the query API is live.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snapshotScannedAtStart(GameTestHelper helper) {
        int features = Isekai.query().getAllPlacedFeatures().size();
        int structures = Isekai.query().getAllStructures().size();
        if (features <= 0) {
            helper.fail("snapshot has no placed features — scan did not run at server start");
            return;
        }
        if (structures <= 0) {
            helper.fail("snapshot has no structures — scan did not run at server start");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 1 — ore Y-remap reflected in a biome's generation settings.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void oreRemapReflectedInBiome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Biome plains = biomeLookup.getOrThrow(Biomes.PLAINS).value();

        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        Set<ResourceKey<PlacedFeature>> oreKeysInPlains = snapshot.featuresInBiome(Biomes.PLAINS);
        // Which snapshot ore keys will the REMOVE phase target (non-fallback, in this biome)?
        long targetable = snapshot.placedFeatures().stream()
                .filter(i -> !snapshot.isFallback(i))
                .filter(i -> oreKeysInPlains.contains(i.key()))
                .count();
        if (targetable == 0) {
            helper.fail("plains has no snapshot-tracked ore features to remap — test precondition unmet");
            return;
        }

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(plains.modifiableBiomeInfo().getOriginalBiomeInfo());
        WorldshapeDescriptor descriptor = linearOreDescriptor();

        RemovePhase.originalsPendingRemap(descriptor, Biomes.PLAINS, builder);
        AddPhase.remappedOreFeatures(descriptor, Biomes.PLAINS, builder);

        BiomeGenerationSettingsBuilder gen = builder.getGenerationSettings();

        // Assertion A: every original snapshot-ore key was removed from the builder.
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : gen.getFeatures(step)) {
                ResourceKey<PlacedFeature> key = h.unwrapKey().orElse(null);
                if (key != null && oreKeysInPlains.contains(key)
                        && snapshot.placedFeatures().stream()
                                .anyMatch(i -> i.key().equals(key) && !snapshot.isFallback(i))) {
                    helper.fail("original ore feature " + key.location() + " was not removed before remap");
                    return;
                }
            }
        }

        // Assertion B: the ADD phase injected rebuilt (direct-holder) ore features, and every
        // one of them resolves into the playable band.
        int injected = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : gen.getFeatures(step)) {
                if (h.unwrapKey().isPresent()) continue;   // registry ref = not one we injected
                int[] range = resolveAbsoluteRange(h.value());
                if (range == null) continue;               // no HRP (shouldn't happen for ores)
                injected++;
                if (range[0] < PLAYABLE_BAND.minY() || range[1] > PLAYABLE_BAND.maxY()) {
                    helper.fail("remapped ore range [" + range[0] + "," + range[1]
                            + "] escaped playable band [" + PLAYABLE_BAND.minY() + "," + PLAYABLE_BAND.maxY() + "]");
                    return;
                }
            }
        }
        if (injected == 0) {
            helper.fail("ADD phase injected no remapped ore features");
            return;
        }
        helper.succeed();
    }

    /**
     * A key in {@code exclusions.features} stays gone after the whole REMOVE→ADD pass.
     *
     * <p>This is the call-site lock for the remap re-injection bug: the ADD phase used to
     * re-inject every snapshot feature of the biome regardless of the descriptor's
     * exclusions, and because it re-injects through {@code Holder.direct} — no ResourceKey —
     * the resurrected feature could never be removed again. Measured in a Sky World save
     * over 9,409 chunks: {@code spring_water} and {@code spring_lava} were excluded and
     * generated anyway (437 water sources, 87,988 flowing water, 18,247 lava sources).
     *
     * <p>Two runs over identical copies of plains: one with no exclusions, one excluding a
     * single remap target picked from the live snapshot. The count of anonymous (injected)
     * holders must drop by exactly the number of decoration steps that feature occupied.
     * A unit test cannot cover this — the ADD phase needs {@code registryAccess()} off a
     * live server to look up the original feature.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void excludedFeatureIsNotReinjectedByRemap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Biome plains = biomeLookup.getOrThrow(Biomes.PLAINS).value();
        var originalInfo = plains.modifiableBiomeInfo().getOriginalBiomeInfo();
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();

        // Pick the victim from the live snapshot rather than hardcoding a feature id, so the
        // test doesn't depend on what plains happens to contain in this MC version. Lowest
        // location string = stable choice across runs.
        ResourceKey<PlacedFeature> victim = RemapTargets.select(snapshot, Biomes.PLAINS, Set.of())
                .stream()
                .min(java.util.Comparator.comparing(k -> k.location().toString()))
                .orElse(null);
        if (victim == null) {
            helper.fail("plains has no remap targets — test precondition unmet");
            return;
        }

        int withoutExclusion = runRemapAndCountInjected(originalInfo, Set.of());
        if (withoutExclusion == 0) {
            helper.fail("ADD phase injected nothing without exclusions — test precondition unmet");
            return;
        }
        // A feature indexed under several decoration steps is injected once per step.
        int victimInjections = Math.max(1, snapshot.stepsFor(victim).size());
        int withExclusion = runRemapAndCountInjected(originalInfo, Set.of(victim));

        int expected = withoutExclusion - victimInjections;
        if (withExclusion != expected) {
            helper.fail("excluding " + victim.location() + " should drop " + victimInjections
                    + " injected feature(s): expected " + expected + ", got " + withExclusion
                    + " (baseline " + withoutExclusion + ") — the ADD phase re-injected an "
                    + "excluded feature as an unremovable anonymous holder");
            return;
        }
        helper.succeed();
    }

    /**
     * Run REMOVE (exclusions + originals) then ADD over a fresh copy of {@code originalInfo}
     * and return how many anonymous holders the remap injected. Anonymous = no ResourceKey =
     * built by {@link AddPhase}, mirroring the counting idiom in
     * {@link #oreRemapReflectedInBiome}.
     */
    private static int runRemapAndCountInjected(ModifiableBiomeInfo.BiomeInfo originalInfo,
                                                 Set<ResourceKey<PlacedFeature>> excludedFeatures) {
        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        excludedFeatures, Set.of(), Set.of(), Set.of()))
                .build();

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(originalInfo);
        RemovePhase.excludedFeatures(descriptor, builder);
        RemovePhase.originalsPendingRemap(descriptor, Biomes.PLAINS, builder);
        AddPhase.remappedOreFeatures(descriptor, Biomes.PLAINS, builder);

        int injected = 0;
        BiomeGenerationSettingsBuilder gen = builder.getGenerationSettings();
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : gen.getFeatures(step)) {
                if (h.unwrapKey().isPresent()) continue;   // registry ref = not one we injected
                injected++;
            }
        }
        return injected;
    }

    // =====================================================================
    // Matrix item 2 — structure exclusion empties the structure's biome set.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void structureExclusionEmptiesBiomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structureLookup = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> village = structureLookup.getOrThrow(BuiltinStructures.VILLAGE_PLAINS);

        var originalInfo = village.value().modifiableStructureInfo().getOriginalStructureInfo();
        int biomesBefore = originalInfo.structureSettings().biomes().size();
        if (biomesBefore == 0) {
            helper.fail("village_plains already has an empty biome set — test precondition unmet");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(), Set.of(BuiltinStructures.VILLAGE_PLAINS), Set.of(), Set.of()))
                .build();

        var builder = ModifiableStructureInfo.StructureInfo.Builder.copyOf(originalInfo);
        new ApplyWorldshapeStructureModifier(descriptor)
                .modify(village, StructureModifier.Phase.REMOVE, builder);

        int biomesAfter = builder.getStructureSettings().build().biomes().size();
        if (biomesAfter != 0) {
            helper.fail("excluded structure still has " + biomesAfter + " biome(s); expected empty set");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 3 — SpatialPredicate evaluation (the gate the mixin drives).
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void spatialPredicateGate(GameTestHelper helper) {
        // (a) composition dispatch — the exact instanceof chain W2 moves into interfaces.
        var ctx = predicateContext(helper.getLevel());
        var pos = new net.minecraft.core.BlockPos(0, 64, 0);

        SpatialPredicate inRange = new SpatialPredicate.YInRange(0, 128);
        SpatialPredicate outRange = new SpatialPredicate.YInRange(200, 300);
        // And(inRange, Not(outRange)) at y=64 -> true; Or(outRange, Never) -> false.
        SpatialPredicate and = new SpatialPredicate.And(List.of(inRange, new SpatialPredicate.Not(outRange)));
        SpatialPredicate or = new SpatialPredicate.Or(List.of(outRange, new SpatialPredicate.Never()));
        if (!com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.evaluate(and, pos, ctx)) {
            helper.fail("And(YInRange, Not(YInRange)) should pass at y=64");
            return;
        }
        if (com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.evaluate(or, pos, ctx)) {
            helper.fail("Or(out-of-range, Never) should fail at y=64");
            return;
        }
        if (com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.evaluate(new SpatialPredicate.Never(), pos, ctx)) {
            helper.fail("Never predicate must never pass");
            return;
        }

        // (b) SolidFloor drives the real ChunkGenerator (getBaseColumn). Negative direction —
        // high in the air there is no floor below — holds in any generator (void or terrain).
        var highAir = new net.minecraft.core.BlockPos(0, WORLD_TOP - 8, 0);
        if (com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.evaluate(
                new SpatialPredicate.SolidFloor(1), highAir, ctx)) {
            helper.fail("SolidFloor should be false high in the air");
            return;
        }
        // Positive direction, guarded: discover the base surface from the generator and assert
        // SolidFloor is true just above it. Guarded on the world actually having terrain, so a
        // void generator can't flake the suite — but when there is a floor (the gametest flat
        // world has one near Y-60), this covers the true branch the negative case can't.
        ServerLevel level = helper.getLevel();
        var generator = level.getChunkSource().getGenerator();
        int surfaceY = generator.getBaseHeight(0, 0,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState());
        if (surfaceY - level.getMinBuildHeight() > 2) {
            var onFloor = new net.minecraft.core.BlockPos(0, surfaceY, 0);
            if (!com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.evaluate(
                    new SpatialPredicate.SolidFloor(1), onFloor, ctx)) {
                helper.fail("SolidFloor should be true just above the base surface at Y=" + surfaceY);
                return;
            }
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 5 — rule biome source zones biomes by position.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void ruleBiomeSourceZoning(GameTestHelper helper) {
        var biomeLookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> desert = biomeLookup.getOrThrow(Biomes.DESERT);
        Holder<Biome> plains = biomeLookup.getOrThrow(Biomes.PLAINS);

        RuleBiomeSource source = new RuleBiomeSource(plains,
                List.of(new RuleBiomeSource.Rule(new BiomeZone.WithinDistance(1000, 0, 0), desert)));

        // Zones are geometric (position -> biome), independent of the climate sampler.
        Holder<Biome> atCenter = source.getNoiseBiome(0, 0, 0, null);
        Holder<Biome> farOut = source.getNoiseBiome(1_000_000, 0, 0, null);

        if (!atCenter.is(Biomes.DESERT)) {
            helper.fail("center should resolve to the within_distance biome (desert)");
            return;
        }
        if (!farOut.is(Biomes.PLAINS)) {
            helper.fail("far position should fall through to the fallback biome (plains)");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 6 — atmosphere override applies to a biome's special effects.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void atmosphereOverrideApplied(GameTestHelper helper) {
        var biomeLookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Biome plains = biomeLookup.getOrThrow(Biomes.PLAINS).value();
        int originalSky = plains.getSpecialEffects().getSkyColor();
        int wantSky = originalSky ^ 0x00FFFFFF;   // guaranteed different from the current value

        AtmosphereOverride atmosphere = new AtmosphereOverride(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.of(wantSky),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                AtmosphereOverride.EffectsExtras.EMPTY,
                java.util.Optional.empty(), java.util.Map.of());

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .atmosphere(atmosphere)
                .build();

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(plains.modifiableBiomeInfo().getOriginalBiomeInfo());
        ModifyPhase.atmosphereOverride(descriptor, builder);

        int resolvedSky = builder.build().effects().getSkyColor();
        if (resolvedSky != wantSky) {
            helper.fail("sky_color override not applied: wanted " + Integer.toHexString(wantSky)
                    + ", got " + Integer.toHexString(resolvedSky));
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 7 — snapshot re-scan after invalidation leaves no stale data.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snapshotRescanDropsStale(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        VanillaRuleSnapshot first = IsekaiInternal.currentSnapshot();
        if (first.isEmpty()) {
            helper.fail("snapshot empty before re-scan test");
            return;
        }
        int firstFeatures = first.placedFeatures().size();

        // Simulate the world-switch / reload boundary: drop the cache the way onServerStopping
        // and the reload SnapshotRefreshListener both rely on, then force the lazy re-scan.
        IsekaiInternal.invalidateSnapshot();
        VanillaRuleSnapshot rescanned = IsekaiInternal.currentSnapshot();
        if (rescanned.isEmpty()) {
            helper.fail("snapshot did not re-scan after invalidation — stale/empty data would leak");
            return;
        }
        if (rescanned == first) {
            helper.fail("invalidate did not replace the snapshot instance");
            return;
        }

        // The re-scan must reflect a fresh registry walk, not a stale cached count.
        VanillaRuleSnapshot direct = VanillaRuleSnapshot.scan(server);
        if (rescanned.placedFeatures().size() != direct.placedFeatures().size()
                || rescanned.placedFeatures().size() != firstFeatures) {
            helper.fail("re-scanned feature count (" + rescanned.placedFeatures().size()
                    + ") disagrees with a direct scan (" + direct.placedFeatures().size() + ")");
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static WorldshapeDescriptor linearOreDescriptor() {
        return WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .build();
    }

    private static com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.Context predicateContext(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        return new com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator.Context(
                generator, level, chunkSource.randomState(), generator.getBiomeSource());
    }

    /**
     * Read the absolute [min, max] Y a PlacedFeature's HeightRangePlacement resolves to,
     * using the Access-Transformer-exposed provider fields. Returns {@code null} when the
     * feature has no HeightRangePlacement. Only the provider variants the rebuilder can emit
     * are handled (Uniform / Trapezoid / the two biased forms / constant), since this is used
     * to inspect rebuilt features.
     */
    private static int[] resolveAbsoluteRange(PlacedFeature feature) {
        HeightProvider hp = null;
        for (PlacementModifier mod : feature.placement()) {
            if (mod instanceof HeightRangePlacement hrp) {
                hp = hrp.height;
                break;
            }
        }
        if (hp == null) return null;
        if (hp instanceof UniformHeight uh) {
            return new int[]{abs(uh.minInclusive), abs(uh.maxInclusive)};
        }
        if (hp instanceof TrapezoidHeight th) {
            return new int[]{abs(th.minInclusive), abs(th.maxInclusive)};
        }
        if (hp instanceof BiasedToBottomHeight bbh) {
            return new int[]{abs(bbh.minInclusive), abs(bbh.maxInclusive)};
        }
        if (hp instanceof VeryBiasedToBottomHeight vbbh) {
            return new int[]{abs(vbbh.minInclusive), abs(vbbh.maxInclusive)};
        }
        if (hp instanceof ConstantHeight ch) {
            int y = abs(ch.value);
            return new int[]{y, y};
        }
        return null;
    }

    /** Resolve a VerticalAnchor against overworld bounds; the rebuilder emits Absolute anchors. */
    private static int abs(VerticalAnchor anchor) {
        if (anchor instanceof VerticalAnchor.Absolute a) return a.y();
        if (anchor instanceof VerticalAnchor.AboveBottom ab) return WORLD_BOTTOM + ab.offset();
        if (anchor instanceof VerticalAnchor.BelowTop bt) return WORLD_TOP - bt.offset();
        return 0;
    }
}
