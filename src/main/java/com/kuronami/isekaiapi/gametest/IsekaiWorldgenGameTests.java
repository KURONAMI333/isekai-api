package com.kuronami.isekaiapi.gametest;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.AtmosphereOverride;
import com.kuronami.isekaiapi.api.remap.BlockOverrides;
import com.kuronami.isekaiapi.api.remap.ContentOverrides;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.ApplyWorldshapeBiomeModifier;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.impl.ChunkDimensionIndex;
import com.kuronami.isekaiapi.impl.DimensionFeatureScope;
import com.kuronami.isekaiapi.impl.DimensionResolver;
import com.kuronami.isekaiapi.placementmodifier.DimensionGuardPlacementModifier;
import com.kuronami.isekaiapi.placementmodifier.SpatialPredicatePlacementModifier;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.MobSpawnScope;
import com.kuronami.isekaiapi.impl.RemapTargets;
import com.kuronami.isekaiapi.impl.StructureExclusion;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.structuremodifier.ApplyWorldshapeStructureModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
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

        AddPhase.remappedOreFeatures(descriptor, Biomes.PLAINS, builder);

        BiomeGenerationSettingsBuilder gen = builder.getGenerationSettings();

        // Assertion A: the originals stay in the biome. Removal is no longer a modifier-time
        // edit (that leaked into every dimension sharing the biome); DimensionFeatureScope
        // filters them at decoration time instead.
        int originalsKept = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : gen.getFeatures(step)) {
                ResourceKey<PlacedFeature> key = h.unwrapKey().orElse(null);
                if (key != null && oreKeysInPlains.contains(key)
                        && snapshot.placedFeatures().stream()
                                .anyMatch(i -> i.key().equals(key) && !snapshot.isFallback(i))) {
                    originalsKept++;
                }
            }
        }
        if (originalsKept == 0) {
            helper.fail("originals were removed from the biome's settings; removals must be "
                    + "dimension-scoped at decoration time, not applied to the shared biome");
            return;
        }

        // Assertion B: the ADD phase injected rebuilt (direct-holder) ore features, each
        // pinned to the descriptor's dimension, and every one resolves into the playable band.
        int injected = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : gen.getFeatures(step)) {
                if (h.unwrapKey().isPresent()) continue;   // registry ref = not one we injected
                injected++;
                var guard = h.value().placement().stream()
                        .filter(m -> m instanceof DimensionGuardPlacementModifier)
                        .map(m -> (DimensionGuardPlacementModifier) m)
                        .findFirst().orElse(null);
                if (guard == null) {
                    helper.fail("injected feature has no dimension_guard; it could place in "
                            + "every dimension that reuses plains");
                    return;
                }
                if (!guard.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                    helper.fail("injected feature is guarded to " + guard.dimension()
                            + " instead of the declared overworld");
                    return;
                }
                int[] range = resolveAbsoluteRange(h.value());
                if (range == null) continue;               // no HRP (shouldn't happen for ores)
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
     * The takeover set is scoped to the declaring dimension: the same biome in another
     * dimension keeps every feature, and the declaring dimension's copy is filtered.
     *
     * <p>This is the regression lock for the hole that the structure fix closed on its side:
     * a worldshape declared for the nether must not rewrite plains' features in the
     * overworld, and vice versa. The scope is recorded by the ADD phase, so the test drives it
     * the way startup does rather than reading the declaration alone.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void featureScopeIsDimensionScoped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> plains = biomeLookup.getOrThrow(Biomes.PLAINS);

        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        Set<ResourceKey<PlacedFeature>> targets = RemapTargets.select(snapshot, Biomes.PLAINS, Set.of());
        if (targets.isEmpty()) {
            helper.fail("plains has no remap targets — test precondition unmet");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.PLAINS))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            // Run the real ADD phase once: the scope is recorded there (biome modifiers do not
            // re-run on /reload, so deriving the set at decoration time would desynchronize).
            runRemapAndCountInjected(
                    plains, plains.value().modifiableBiomeInfo().getOriginalBiomeInfo(), Set.of());
            Set<ResourceKey<PlacedFeature>> scoped =
                    DimensionFeatureScope.skipKeys(net.minecraft.world.level.Level.OVERWORLD, plains);
            if (!scoped.containsAll(targets)) {
                long missing = targets.stream().filter(k -> !scoped.contains(k)).count();
                helper.fail("declared dimension's skip set is missing " + missing + " remap target(s)");
                return;
            }
            if (!DimensionFeatureScope.skipKeys(net.minecraft.world.level.Level.NETHER, plains).isEmpty()) {
                helper.fail("skip set leaked into a dimension that did not declare this biome");
                return;
            }

            BiomeGenerationSettings settings = plains.value().getGenerationSettings();
            // Decoration in every other dimension must be untouched: filter returns the very
            // settings instance when nothing is scoped away, so identity is the assertion. The
            // key is the dimension registry id, so this covers nether/end/modded alike.
            if (DimensionFeatureScope.filter(net.minecraft.world.level.Level.NETHER, plains,
                    settings) != settings) {
                helper.fail("the nether had its features filtered although it applied no worldshape");
                return;
            }
            if (DimensionFeatureScope.filter(net.minecraft.world.level.Level.END, plains,
                    settings) != settings) {
                helper.fail("the end had its features filtered although it applied no worldshape");
                return;
            }

            BiomeGenerationSettings filtered = DimensionFeatureScope.filter(
                    net.minecraft.world.level.Level.OVERWORLD, plains, settings);
            for (var step : filtered.features()) {
                for (Holder<PlacedFeature> h : step) {
                    ResourceKey<PlacedFeature> key = h.unwrapKey().orElse(null);
                    if (key != null && targets.contains(key)) {
                        helper.fail("filter kept remap target " + key.location()
                                + " in the declaring dimension");
                        return;
                    }
                }
            }
        } finally {
            Isekai.remap().removeWorldshape(net.minecraft.world.level.Level.OVERWORLD);
            DimensionFeatureScope.forget(net.minecraft.world.level.Level.OVERWORLD, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * The takeover set is recorded by the ADD phase, not derived from the live declaration.
     *
     * <p>{@code /reload} does not re-run biome modifiers, so a decoration-time derivation would
     * desynchronize the drop from the injected replacement: a predicate added by a reload would
     * delete the original while no copy exists (feature vanishes), and one removed would restore
     * the original next to the startup copy (duplicate). Recording at ADD time binds both to the
     * same pass. This locks that: declaring a worldshape alone changes nothing, the ADD pass
     * records it, and the record is dimension-scoped.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void featureScopeIsRecordedByTheAddPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);
        ResourceKey<PlacedFeature> excluded = IsekaiInternal.currentSnapshot()
                .featuresInBiome(Biomes.PLAINS).stream()
                .min(java.util.Comparator.comparing(k -> k.location().toString()))
                .orElse(null);
        if (excluded == null) {
            helper.fail("plains has no snapshot features — test precondition unmet");
            return;
        }
        // A dimension no level resolves to, so no other test can observe or disturb the record.
        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("isekai_api", "scope_test"));

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(dimension)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.PLAINS))
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(excluded), Set.of(), Set.of(), Set.of()))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            if (!DimensionFeatureScope.skipKeys(dimension, plains).isEmpty()) {
                helper.fail("declaration alone recorded a takeover set; the scope must come "
                        + "from the ADD pass the biome modifier runs");
                return;
            }
            AddPhase.registerFeatureScope(descriptor, Biomes.PLAINS);
            if (!DimensionFeatureScope.skipKeys(dimension, plains).contains(excluded)) {
                helper.fail("ADD pass did not record 'excluded' for the declaring dimension");
                return;
            }
            if (!DimensionFeatureScope.skipKeys(net.minecraft.world.level.Level.NETHER, plains).isEmpty()) {
                helper.fail("recorded takeover set leaked into a dimension that never applied it");
                return;
            }
            // Redeclaring the dimension with a different descriptor is exactly what /reload
            // does; it must not move the recorded set, or the drop and the injected copy (both
            // baked at startup) would disagree. This is the invariant of the whole design.
            Isekai.remap().declareWorldshape(WorldshapeDescriptor.builder()
                    .dimension(dimension)
                    .playableRange(PLAYABLE_BAND)
                    .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                    .oreStrategy(new RemapStrategy.Identity())
                    .structureStrategy(new RemapStrategy.Identity())
                    .mobSpawnStrategy(new RemapStrategy.Identity())
                    .defaultStructurePredicate(new SpatialPredicate.Always())
                    .appliesTo(Set.of(Biomes.PLAINS))
                    .build());
            Set<ResourceKey<PlacedFeature>> afterRedeclare =
                    DimensionFeatureScope.skipKeys(dimension, plains);
            if (!afterRedeclare.contains(excluded) || afterRedeclare.size() != 1) {
                helper.fail("a redeclare changed the recorded takeover set (" + afterRedeclare
                        + "); the drop must stay frozen with the injected copies from startup");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(dimension);
            DimensionFeatureScope.forget(dimension, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * The scope is keyed by the dimension registry id, so it is not nether- or
     * overworld-specific: the end (different DimensionType, height, light) works the same,
     * and a declaration for one dimension never touches another.
     *
     * <p>This is the genericity lock. Nothing in the path branches on a vanilla dimension id;
     * the only inputs are the descriptor's {@code dimension} and the generating level's own
     * key, so a modded dimension gets the same treatment as a vanilla one.
     *
     * <p>Scope of this test: it pins {@code registerFeatureScope} / {@code skipKeys} /
     * {@code filter} as a dimension-keyed contract. It drives the ADD phase directly, so the
     * {@code ChunkGeneratorDecorationMixin} wiring (which supplies the key from the real
     * {@code WorldGenLevel} during decoration) is only proven by the mixin applying at startup,
     * not by real chunk generation.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void featureScopeWorksForAnyDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);
        ResourceKey<net.minecraft.world.level.Level> theEnd = net.minecraft.world.level.Level.END;
        // Per dimension: the end's build height is not the overworld's, so the set of features
        // whose anchors resolve there (and stay in the playable band) differs. The scope must
        // be that dimension's own set, not the overworld's.
        Set<ResourceKey<PlacedFeature>> targets = RemapTargets.select(
                IsekaiInternal.currentSnapshot(), Biomes.PLAINS, theEnd, Set.of());
        if (targets.isEmpty()) {
            helper.fail("plains has no remap targets in the end — test precondition unmet");
            return;
        }
        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(theEnd)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.PLAINS))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            AddPhase.registerFeatureScope(descriptor, Biomes.PLAINS);
            Set<ResourceKey<PlacedFeature>> recorded = DimensionFeatureScope.skipKeys(theEnd, plains);
            if (!recorded.equals(targets)) {
                helper.fail("the end's scope is " + recorded.size() + " keys, expected the "
                        + targets.size() + " of the end — the scope is not dimension-agnostic");
                return;
            }
            if (!DimensionFeatureScope.skipKeys(net.minecraft.world.level.Level.OVERWORLD, plains)
                    .isEmpty()) {
                helper.fail("an end declaration put keys into the overworld's scope");
                return;
            }
            BiomeGenerationSettings settings = plains.value().getGenerationSettings();
            for (var step : DimensionFeatureScope.filter(theEnd, plains, settings).features()) {
                for (Holder<PlacedFeature> h : step) {
                    ResourceKey<PlacedFeature> key = h.unwrapKey().orElse(null);
                    if (key != null && targets.contains(key)) {
                        helper.fail("filter kept remap target " + key.location() + " in the end");
                        return;
                    }
                }
            }
            if (DimensionFeatureScope.filter(net.minecraft.world.level.Level.OVERWORLD, plains,
                    settings) != settings) {
                helper.fail("an end declaration filtered overworld decoration");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(theEnd);
            DimensionFeatureScope.forget(theEnd, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * The value-level veto is scoped by dimension and by value, so a custom generator that
     * never reaches {@code applyBiomeDecoration}'s redirect still cannot place a replaced
     * original in the declaring dimension — and the same feature stays placeable elsewhere.
     *
     * <p>{@link DimensionFeatureScope#shouldDrop} is what {@code PlacedFeatureScopeMixin}
     * consults at {@code placeWithContext}. This test drives the record through the real ADD
     * phase, takes a target value out of plains' live settings, and asserts the three outcomes
     * that distinguish "scoped" from "everything": true in the declared dimension, false in
     * another dimension, false for a feature the remap did not take over.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void placedFeatureVetoIsDimensionAndValueScoped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);
        ResourceKey<net.minecraft.world.level.Level> theEnd = net.minecraft.world.level.Level.END;
        Set<ResourceKey<PlacedFeature>> targets = RemapTargets.select(
                IsekaiInternal.currentSnapshot(), Biomes.PLAINS, theEnd, Set.of());
        if (targets.isEmpty()) {
            helper.fail("plains has no remap targets in the end — test precondition unmet");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(theEnd)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.PLAINS))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            AddPhase.registerFeatureScope(descriptor, Biomes.PLAINS);

            BiomeGenerationSettings settings = plains.value().getGenerationSettings();
            PlacedFeature target = null;
            PlacedFeature other = null;
            for (var step : settings.features()) {
                for (Holder<PlacedFeature> h : step) {
                    ResourceKey<PlacedFeature> key = h.unwrapKey().orElse(null);
                    if (key == null) continue;
                    if (target == null && targets.contains(key)) {
                        target = h.value();
                    } else if (other == null && !targets.contains(key)) {
                        other = h.value();
                    }
                }
            }
            if (target == null) {
                helper.fail("no remap target is present in plains' live settings — test "
                        + "precondition unmet (the record could not have resolved any value)");
                return;
            }
            if (other == null) {
                helper.fail("all of plains' placed features are remap targets — test "
                        + "precondition unmet (need a feature the remap left alone)");
                return;
            }

            boolean targetInEnd = DimensionFeatureScope.shouldDrop(theEnd, plains, target);
            boolean targetInOverworld = DimensionFeatureScope.shouldDrop(
                    net.minecraft.world.level.Level.OVERWORLD, plains, target);
            boolean otherInEnd = DimensionFeatureScope.shouldDrop(theEnd, plains, other);

            if (!targetInEnd) {
                helper.fail("shouldDrop(END, plains, target) = false; expected true for a "
                        + "feature the ADD phase replaced in the end");
                return;
            }
            if (targetInOverworld) {
                helper.fail("shouldDrop(OVERWORLD, plains, target) = true; expected false — "
                        + "the veto leaked into a dimension that declared no worldshape");
                return;
            }
            if (otherInEnd) {
                helper.fail("shouldDrop(END, plains, other) = true; expected false for a "
                        + "feature outside the remap's takeover set");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(theEnd);
            DimensionFeatureScope.forget(theEnd, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * A key in {@code exclusions.features} is never re-injected by the ADD pass, so the
     * decoration filter has nothing to drop for it and it stays gone.
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

        try {
            Holder<Biome> plainsHolder = biomeLookup.getOrThrow(Biomes.PLAINS);
            int withoutExclusion = runRemapAndCountInjected(plainsHolder, originalInfo, Set.of());
            if (withoutExclusion == 0) {
                helper.fail("ADD phase injected nothing without exclusions — test precondition unmet");
                return;
            }
            // A feature indexed under several decoration steps is injected once per step.
            int victimInjections = Math.max(1, snapshot.stepsFor(victim).size());
            int withExclusion = runRemapAndCountInjected(plainsHolder, originalInfo, Set.of(victim));

            int expected = withoutExclusion - victimInjections;
            if (withExclusion != expected) {
                helper.fail("excluding " + victim.location() + " should drop " + victimInjections
                        + " injected feature(s): expected " + expected + ", got " + withExclusion
                        + " (baseline " + withoutExclusion + ") — the ADD phase re-injected an "
                        + "excluded feature as an unremovable anonymous holder");
                return;
            }
        } finally {
            DimensionFeatureScope.forget(net.minecraft.world.level.Level.OVERWORLD, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * Run the real modifier's ADD phase over a fresh copy of {@code originalInfo} (NeoForge
     * fixes the order ADD → REMOVE; its REMOVE only touches carvers now)
     * and return how many anonymous holders the remap injected. Anonymous = no ResourceKey =
     * built by {@link AddPhase}, mirroring the counting idiom in
     * {@link #oreRemapReflectedInBiome}.
     *
     * <p>Driven through {@link ApplyWorldshapeBiomeModifier#modify} rather than the phase
     * statics so the dispatch itself is covered. {@code apply_worldshape_ref} and the layered
     * path run the identical switch body over a resolved descriptor, so all three datapack
     * entry points are locked by this one path.
     */
    private static int runRemapAndCountInjected(Holder<Biome> biome,
                                                 ModifiableBiomeInfo.BiomeInfo originalInfo,
                                                 Set<ResourceKey<PlacedFeature>> excludedFeatures) {
        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(net.minecraft.world.level.Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.PLAINS))
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        excludedFeatures, Set.of(), Set.of(), Set.of()))
                .build();

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(originalInfo);
        var modifier = new ApplyWorldshapeBiomeModifier(descriptor);
        modifier.modify(biome, BiomeModifier.Phase.ADD, builder);
        modifier.modify(biome, BiomeModifier.Phase.REMOVE, builder);

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

    /**
     * {@code feature_predicates} no longer rewrites the shared biome: the original stays and a
     * guarded copy is injected instead. This locks both halves of that contract — a guard on
     * the copy, and a predicate applied to it — at the phase level.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void featurePredicateInjectsGuardedCopy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> plains = biomeLookup.getOrThrow(Biomes.PLAINS);

        ResourceKey<PlacedFeature> target = IsekaiInternal.currentSnapshot()
                .featuresInBiome(Biomes.PLAINS).stream()
                .min(java.util.Comparator.comparing(k -> k.location().toString()))
                .orElse(null);
        if (target == null) {
            helper.fail("plains has no snapshot features — test precondition unmet");
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
                .appliesTo(Set.of(Biomes.PLAINS))
                .contentOverrides(new ContentOverrides(
                        java.util.Map.of(target, new SpatialPredicate.Always()),
                        java.util.List.of(),
                        BlockOverrides.EMPTY))
                .build();

        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(
                plains.value().modifiableBiomeInfo().getOriginalBiomeInfo());
        AddPhase.predicatedFeatures(descriptor, Biomes.PLAINS, builder);

        boolean stillOriginal = false;
        boolean guardedCopy = false;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : builder.getGenerationSettings().getFeatures(step)) {
                if (h.is(target)) stillOriginal = true;
                if (h.unwrapKey().isPresent()) continue;
                boolean guard = h.value().placement().stream()
                        .anyMatch(m -> m instanceof DimensionGuardPlacementModifier);
                boolean predicated = h.value().placement().stream()
                        .anyMatch(m -> m instanceof SpatialPredicatePlacementModifier);
                if (guard && predicated) guardedCopy = true;
            }
        }
        if (!stillOriginal) {
            helper.fail("predicated feature's original was removed from the biome; "
                    + "dimension scoping must leave the shared biome untouched");
            return;
        }
        if (!guardedCopy) {
            helper.fail("no guarded+predicated copy was injected for " + target.location());
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Matrix item 2 — structure exclusion is scoped to the declaring dimension.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void structureExclusionIsScopedToDeclaredDimension(GameTestHelper helper) {
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
                .dimension(net.minecraft.world.level.Level.END)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(), Set.of(BuiltinStructures.VILLAGE_PLAINS), Set.of(), Set.of()))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            if (!StructureExclusion.isExcluded(net.minecraft.world.level.Level.END,
                    BuiltinStructures.VILLAGE_PLAINS)) {
                helper.fail("exclusion not reported for the declaring dimension");
                return;
            }
            if (StructureExclusion.isExcluded(net.minecraft.world.level.Level.NETHER,
                    BuiltinStructures.VILLAGE_PLAINS)) {
                helper.fail("exclusion leaked into a dimension that did not declare it");
                return;
            }

            // The REMOVE phase must not mutate the shared structure definition: the exclusion
            // is a per-dimension generation veto, not a global biome-filter edit.
            var builder = ModifiableStructureInfo.StructureInfo.Builder.copyOf(originalInfo);
            new ApplyWorldshapeStructureModifier(descriptor)
                    .modify(village, StructureModifier.Phase.REMOVE, builder);
            int biomesAfter = builder.getStructureSettings().build().biomes().size();
            if (biomesAfter != biomesBefore) {
                helper.fail("REMOVE phase mutated the global biome filter (" + biomesBefore
                        + " -> " + biomesAfter + ")");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(net.minecraft.world.level.Level.END);
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
    // Mob spawn exclusion — list path and the non-list veto.
    // =====================================================================

    /**
     * A recorded exclusion drops its entity type from the spawn list in the declaring
     * dimension only, and the same rule cancels a finalized spawn — the path patrols and other
     * list-free spawners take.
     *
     * <p>Unlike the feature tests this drives the live biome: it seeds the rule through
     * {@link MobSpawnScope#record} — the same call {@code ModifyPhase} makes — then reads the
     * real generator's list for the test position's biome. The declaring dimension is the
     * GameTest level's own dimension; a different vanilla dimension must be untouched.
     *
     * <p>{@code NaturalSpawner#mobsAt} (where the production mixin sits) is private, so the
     * scope is applied directly to the generator's list — the exact input the mixin receives
     * after {@code EventHooks.getPotentialSpawns}. The veto itself is exercised end to end by
     * posting a real {@link FinalizeSpawnEvent}, which is what {@code PatrolSpawner} triggers
     * before {@code addFreshEntityWithPassengers}.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void mobSpawnRemovalIsDimensionScoped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Holder<Biome> biome = level.getBiome(origin);
        ResourceKey<Biome> biomeKey = biome.unwrapKey().orElse(null);
        if (biomeKey == null) {
            helper.fail("test position's biome has no registry key — rule cannot be recorded");
            return;
        }

        ResourceKey<Level> declared = level.dimension();
        ResourceKey<Level> other = declared.equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        WeightedRandomList<MobSpawnSettings.SpawnerData> base = generator.getMobsAt(
                biome, level.structureManager(), MobCategory.MONSTER, origin);
        List<MobSpawnSettings.SpawnerData> original = base.unwrap();
        if (original.isEmpty()) {
            helper.fail("biome " + biomeKey.location() + " has an empty monster list — test precondition unmet");
            return;
        }

        // Prefer the canonical example (zombie) when this biome has it; otherwise exclude a type
        // the list actually contains so the assertion is never vacuous.
        EntityType<?> candidate = EntityType.ZOMBIE;
        boolean present = original.stream().anyMatch(d -> d.type == EntityType.ZOMBIE);
        EntityType<?> excluded = present ? candidate : original.get(0).type;
        ResourceKey<EntityType<?>> excludedKey = MobSpawnScope.entityKey(excluded);
        if (excludedKey == null) {
            helper.fail("excluded type " + excluded + " is not registered — test precondition unmet");
            return;
        }

        MobSpawnScope.record(declared, biomeKey, new MobSpawnScope.MobSpawnRule(
                Set.of(excludedKey), Map.of(), 1.0, Map.of()));
        try {
            if (!MobSpawnScope.isRemoved(declared, biome, excluded)) {
                helper.fail("isRemoved() is false in the declaring dimension for " + excluded);
                return;
            }
            if (MobSpawnScope.isRemoved(other, biome, excluded)) {
                helper.fail("exclusion leaked into " + other.location() + ", which declared no rule");
                return;
            }

            List<MobSpawnSettings.SpawnerData> scoped =
                    MobSpawnScope.apply(declared, biome, MobCategory.MONSTER, original);
            boolean stillPresent = scoped.stream().anyMatch(d -> d.type == excluded);
            if (stillPresent) {
                helper.fail("excluded type " + excluded + " is still in the scoped list");
                return;
            }
            if (scoped.size() != original.size() - 1) {
                helper.fail("scoped list has " + scoped.size() + " entries, expected "
                        + (original.size() - 1));
                return;
            }

            // Another dimension sharing the biome must get the caller's own list back, identity
            // included: that is the leak this scoping exists to prevent.
            List<MobSpawnSettings.SpawnerData> untouched =
                    MobSpawnScope.apply(other, biome, MobCategory.MONSTER, original);
            if (untouched != original) {
                helper.fail("a dimension without a rule had its spawn list rewritten");
                return;
            }

            // Non-list path: finalizeSpawn is where patrol/spawner/structure spawns can be
            // vetoed. POST a real event and require the veto handler to cancel the spawn.
            var created = excluded.create(level);
            if (!(created instanceof Mob mob)) {
                helper.fail("excluded type " + excluded + " did not create a Mob");
                return;
            }
            FinalizeSpawnEvent spawnEvent = new FinalizeSpawnEvent(
                    mob, level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5,
                    level.getCurrentDifficultyAt(origin), MobSpawnType.PATROL, null, null);
            NeoForge.EVENT_BUS.post(spawnEvent);
            boolean cancelled = mob.isSpawnCancelled();
            mob.discard();
            if (!cancelled) {
                helper.fail("FinalizeSpawnEvent for " + excluded
                        + " was not cancelled — patrol/spawner spawns would leak through");
                return;
            }
        } finally {
            MobSpawnScope.forget(declared, biomeKey);
        }
        helper.succeed();
    }

    // =====================================================================
    // Generation-time dimension resolution for structure placement.
    // =====================================================================

    /**
     * A chunk handed to structure generation is a {@code ProtoChunk} built with its owning
     * {@code ServerLevel}, so {@link DimensionResolver} must read the dimension off the
     * {@link ChunkDimensionIndex} without needing the biome source at all.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void chunkDimensionIndexResolvesProtoChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), null, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        ResourceKey<Level> resolved = DimensionResolver.resolve(chunk, null);
        if (resolved == null) {
            helper.fail("DimensionResolver.resolve(ProtoChunk, null) returned null; expected "
                    + level.dimension().location());
            return;
        }
        if (!resolved.equals(level.dimension())) {
            helper.fail("DimensionResolver.resolve(ProtoChunk, null) returned " + resolved.location()
                    + "; expected " + level.dimension().location());
            return;
        }
        helper.succeed();
    }

    /**
     * An {@code ImposterProtoChunk} wraps an already-generated {@code LevelChunk}. The index is
     * consulted through {@code getWrapped()} first, then through the imposter's own constructor
     * entry (which carries the level too), so both routes must agree with the level.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void imposterProtoChunkResolvesToItsLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ImposterProtoChunk imposter = new ImposterProtoChunk(level.getChunk(0, 0), false);
        ResourceKey<Level> resolved = DimensionResolver.resolve(imposter, null);
        if (resolved == null) {
            helper.fail("DimensionResolver.resolve(ImposterProtoChunk, null) returned null; expected "
                    + level.dimension().location());
            return;
        }
        if (!resolved.equals(level.dimension())) {
            helper.fail("DimensionResolver.resolve(ImposterProtoChunk, null) returned "
                    + resolved.location() + "; expected " + level.dimension().location());
            return;
        }
        helper.succeed();
    }

    // =====================================================================
    // Mixin wiring — drive the real methods the mixins are attached to.
    // =====================================================================

    /**
     * The value-level veto must run in the real placement path, not only in the
     * {@link DimensionFeatureScope#shouldDrop} predicate the test above calls directly.
     *
     * <p>{@code PlacedFeature#placeWithBiomeCheck} is the public entry decoration uses and,
     * like {@code place}, delegates to the private {@code placeWithContext} where
     * {@code PlacedFeatureScopeMixin} is injected. ({@code place} itself cannot be used for a
     * vanilla feature: every plains placed feature carries a {@code minecraft:biome} modifier,
     * and {@code BiomeFilter#shouldPlace} throws when the context has no top feature —
     * {@code BiomeFilter.java:21}.)
     *
     * <p>The test declares {@code exclusions.features = {minecraft:ore_diamond_medium}} for
     * {@code (overworld, plains)} through the real ADD phase and prepares a stone volume (the
     * flat GameTest world has none). It then places the exact registry value at the same
     * position and with the same random seed three times: once before declaring (the positive
     * control that the fixture can place it at all), once in the declared overworld (must be
     * vetoed) and once through a level view whose {@code getLevel().dimension()} is the
     * undeclared end (must place). A "always false" implementation fails the control just as
     * loudly as a leak fails the declared case.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void placedFeatureMixinVetoesRealPlacementAcrossDimensions(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        MinecraftServer server = overworld.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            helper.fail("GameTest server has no end level — cannot exercise the non-declared dimension");
            return;
        }

        var featureLookup = overworld.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        ResourceKey<PlacedFeature> targetKey = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.withDefaultNamespace("ore_diamond_medium"));
        Holder<PlacedFeature> targetHolder = featureLookup.get(targetKey).orElse(null);
        if (targetHolder == null) {
            helper.fail("minecraft:ore_diamond_medium is not in the placed feature registry");
            return;
        }
        PlacedFeature target = targetHolder.value();

        Holder<Biome> plains = overworld.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.PLAINS);
        boolean inPlains = false;
        for (var step : plains.value().getGenerationSettings().features()) {
            for (Holder<PlacedFeature> holder : step) {
                if (holder.unwrapKey().map(targetKey::equals).orElse(false)) {
                    inPlains = true;
                    break;
                }
            }
        }
        if (!inPlains) {
            helper.fail("minecraft:ore_diamond_medium is not in plains' generation settings — the "
                    + "ADD phase could not resolve a value for it");
            return;
        }

        int centerX = 4000;
        int centerZ = 4000;
        BlockPos center = new BlockPos(centerX, -20, centerZ);
        if (!overworld.getBiome(center).is(Biomes.PLAINS)) {
            helper.fail("the GameTest flat world is not plains at " + center + " — test precondition unmet");
            return;
        }
        prepareStoneBox(overworld, centerX, centerZ);
        if (!overworld.getBlockState(center).is(Blocks.STONE)) {
            helper.fail("the prepared stone box is missing at " + center + " — the flat GameTest chunk was not loadable");
            return;
        }

        // The flat GameTest world runs FlatLevelGeneratorSettings without decoration, so its own
        // generator reports no biome features and BiomeFilter would reject every placed feature.
        // The normal overworld generator uses the biome's registered settings, which is what the
        // filter is meant to read; the feature itself places into the flat level's blocks.
        ChunkGenerator generator = WorldPresets.getNormalOverworld(server.registryAccess()).generator();
        boolean baseline = target.placeWithBiomeCheck(overworld, generator,
                RandomSource.create(1L), center);
        if (!baseline) {
            helper.fail("positive control: " + targetKey.location() + " does not place in the plain "
                    + "overworld before any declaration — the test cannot distinguish a veto from a broken fixture");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.OVERWORLD)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(targetKey), Set.of(), Set.of(), Set.of()))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            AddPhase.registerFeatureScope(descriptor, Biomes.PLAINS);

            boolean scoped = target.placeWithBiomeCheck(overworld, generator,
                    RandomSource.create(1L), center);
            if (scoped) {
                helper.fail("PlacedFeature#placeWithBiomeCheck placed " + targetKey.location()
                        + " in the overworld although the mixin should have vetoed it "
                        + "(PlacedFeatureScopeMixin -> DimensionFeatureScope#shouldDrop)");
                return;
            }

            // Same feature, position, biome and seed. Only the ServerLevel returned by
            // WorldGenLevel#getLevel differs, so the mixin reads the end as the dimension while
            // the blocks and biome stay the prepared overworld ones.
            WorldGenLevel undeclared = dimensionView(overworld, end);
            boolean placed = target.placeWithBiomeCheck(undeclared, generator,
                    RandomSource.create(1L), center);
            if (!placed) {
                helper.fail("PlacedFeature#placeWithBiomeCheck placed nothing in "
                        + end.dimension().location() + " although that dimension declared no "
                        + "worldshape — the veto leaked or the positive control is broken");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(Level.OVERWORLD);
            DimensionFeatureScope.forget(Level.OVERWORLD, Biomes.PLAINS);
        }
        helper.succeed();
    }

    /**
     * The structure exclusion must be enforced from {@code Structure#findValidGenerationPoint},
     * where {@code StructureFindValidGenerationPointMixin} injects, not only from the
     * {@link StructureExclusion#isExcluded} helper the test above calls directly.
     *
     * <p>A real {@link Structure.GenerationContext} is built for the end and for the overworld
     * (both are real {@code ServerLevel}s the flat GameTest server creates), with the same
     * chunk position and an always-true biome predicate. {@code minecraft:igloo}'s
     * {@code findGenerationPoint} unconditionally calls {@code onTopOfChunkCenter}, so the
     * overworld call is a genuine positive control: it returns a stub only when the mixin lets
     * the vanilla body run.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void structureMixinRejectsExcludedStructureInRealGenerationContext(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        MinecraftServer server = overworld.getServer();
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            helper.fail("GameTest server has no end level — cannot exercise the non-declared dimension");
            return;
        }

        Holder<Structure> igloo = overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(BuiltinStructures.IGLOO);

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.END)
                .playableRange(PLAYABLE_BAND)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(), Set.of(BuiltinStructures.IGLOO), Set.of(), Set.of()))
                .build();

        Isekai.remap().declareWorldshape(descriptor);
        try {
            if (!StructureExclusion.isExcluded(Level.END, BuiltinStructures.IGLOO)) {
                helper.fail("precondition: the exclusion is not active for the declaring dimension");
                return;
            }

            ChunkPos chunkPos = new ChunkPos(0, 0);
            var excluded = igloo.value().findValidGenerationPoint(
                    generationContext(server, end, chunkPos));
            if (excluded.isPresent()) {
                helper.fail("Structure#findValidGenerationPoint returned a stub in "
                        + end.dimension().location() + " although "
                        + "StructureFindValidGenerationPointMixin should have vetoed the excluded structure");
                return;
            }

            var allowed = igloo.value().findValidGenerationPoint(
                    generationContext(server, overworld, chunkPos));
            if (allowed.isEmpty()) {
                helper.fail("Structure#findValidGenerationPoint returned nothing in "
                        + overworld.dimension().location() + " although that dimension declared no "
                        + "worldshape — the veto leaked or the positive control is broken");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(Level.END);
        }
        helper.succeed();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Structure.GenerationContext generationContext(MinecraftServer server,
                                                                  ServerLevel level,
                                                                  ChunkPos chunkPos) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(
                server.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                server.getStructureManager(),
                0L,
                chunkPos,
                level,
                biome -> true);
    }

    /**
     * A {@link WorldGenLevel} that reports {@code dimension} from {@link WorldGenLevel#getLevel}
     * and delegates every other call to {@code delegate}. The mixin's dimension lookup goes
     * through exactly that method, so this lets one prepared overworld position stand in for an
     * undeclared dimension without a second populated level.
     */
    private static WorldGenLevel dimensionView(WorldGenLevel delegate, ServerLevel dimension) {
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                (proxy, method, args) -> {
                    if (method.getParameterCount() == 0 && method.getName().equals("getLevel")) {
                        return dimension;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /**
     * Fill the stone volume {@code minecraft:ore_diamond_medium}'s modifiers sample from:
     * {@code InSquarePlacement} adds 0..15 to both X and Z, and the height provider is uniform
     * from -64 to -4. The flat GameTest world has no stone, so without this the feature could
     * not place even when the mixin allows it.
     */
    private static void prepareStoneBox(ServerLevel level, int originX, int originZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        var stone = Blocks.STONE.defaultBlockState();
        for (int x = originX; x <= originX + 15; x++) {
            for (int z = originZ; z <= originZ + 15; z++) {
                for (int y = -64; y <= 16; y++) {
                    level.setBlock(cursor.set(x, y, z), stone, 2);
                }
            }
        }
    }

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
