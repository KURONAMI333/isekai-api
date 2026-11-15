package com.kuronami.isekaiapi.gametest;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.DimensionProfile;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.ApplyWorldshapeBiomeModifier;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.StructureExclusion;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.structuremodifier.ApplyWorldshapeStructureModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.Set;

/**
 * Non-overworld regression net: the remap path must build its source envelope from the
 * dimension's own build height, not from the overworld's {@code -64..319}.
 *
 * <p>These tests need no nether {@code ServerLevel}; the profile and per-dimension ranges
 * come from the {@code LevelStem} registry, which is the same source the first biome-modifier
 * pass reads before any level exists.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiDimensionGameTests {

    private IsekaiDimensionGameTests() {}

    private static final ResourceKey<PlacedFeature> ORE_QUARTZ_NETHER =
            ResourceKey.create(Registries.PLACED_FEATURE,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("ore_quartz_nether"));

    /** A narrow target band inside the nether's 0..255 column. */
    private static final VerticalRange PLAYABLE = new VerticalRange(32, 96, HeightDistribution.UNIFORM);

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void netherDimensionProfileMatchesVanilla(GameTestHelper helper) {
        DimensionProfile profile = Isekai.query().getDimensionProfile(Level.NETHER).orElse(null);
        if (profile == null) {
            helper.fail("no DimensionProfile for minecraft:the_nether — LevelStem scan did not run");
            return;
        }
        expect(helper, "minY", profile.minY(), 0);
        expect(helper, "height", profile.height(), 256);
        expect(helper, "maxY", profile.maxY(), 255);
        expect(helper, "logicalHeight", profile.logicalHeight(), 128);
        expect(helper, "hasCeiling", profile.hasCeiling(), true);
        expect(helper, "hasSkylight", profile.hasSkylight(), false);
        expect(helper, "ultrawarm", profile.ultrawarm(), true);
        expect(helper, "coordinateScale", profile.coordinateScale(), 8.0);
        expect(helper, "bedWorks", profile.bedWorks(), false);
        expect(helper, "respawnAnchorWorks", profile.respawnAnchorWorks(), true);
        expect(helper, "piglinSafe", profile.piglinSafe(), true);
        expect(helper, "seaLevel", profile.seaLevel(), 32);
        if (profile.defaultBlock().filter(b -> b == Blocks.NETHERRACK).isEmpty()) {
            helper.fail("nether default block is not netherrack: " + profile.defaultBlock());
            return;
        }
        if (profile.defaultFluid().filter(f -> f == Fluids.LAVA).isEmpty()) {
            helper.fail("nether default fluid is not lava: " + profile.defaultFluid());
            return;
        }
        helper.succeed();
    }

    /**
     * The same vanilla feature resolves to different Y in different dimensions, and the
     * nether resolution is the one the remap must use there. Quartz is
     * {@code above_bottom(10) .. below_top(10)}: {@code [-54,309]} in the overworld,
     * {@code [10,245]} in the nether.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void perDimensionRangeDiffersFromOverworld(GameTestHelper helper) {
        VerticalRange global = Isekai.query().getPlacedFeatureVerticalRange(ORE_QUARTZ_NETHER).orElse(null);
        VerticalRange nether = Isekai.query()
                .getPlacedFeatureVerticalRangeInDimension(ORE_QUARTZ_NETHER, Level.NETHER).orElse(null);
        if (global == null || nether == null) {
            helper.fail("ore_quartz_nether was not scanned (global=" + global + ", nether=" + nether + ")");
            return;
        }
        if (global.minY() != -54 || global.maxY() != 309) {
            helper.fail("overworld-resolved quartz range changed: " + global);
            return;
        }
        if (nether.minY() != 10 || nether.maxY() != 245) {
            helper.fail("nether-resolved quartz range is " + nether + "; expected [10,245]");
            return;
        }
        helper.succeed();
    }

    /**
     * End-to-end: a worldshape declared for {@code minecraft:the_nether} feeds the nether
     * envelope into the remap, so the re-injected quartz band differs from what the old
     * hardcoded overworld envelope produced.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void netherOreRemapUsesNetherEnvelope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeLookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> wastes = biomeLookup.getOrThrow(Biomes.NETHER_WASTES);
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();

        if (!snapshot.featuresInBiome(Biomes.NETHER_WASTES).contains(ORE_QUARTZ_NETHER)) {
            helper.fail("nether_wastes does not contain ore_quartz_nether — test precondition unmet");
            return;
        }
        DimensionProfile profile = Isekai.query().getDimensionProfile(Level.NETHER).orElse(null);
        if (profile == null) {
            helper.fail("no nether profile");
            return;
        }
        VerticalRange source = Isekai.query()
                .getPlacedFeatureVerticalRangeInDimension(ORE_QUARTZ_NETHER, Level.NETHER).orElse(null);
        if (source == null) {
            helper.fail("no nether range for ore_quartz_nether");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.NETHER)
                .playableRange(PLAYABLE)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.NETHER_WASTES))
                .build();

        var originalInfo = wastes.value().modifiableBiomeInfo().getOriginalBiomeInfo();
        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(originalInfo);
        var modifier = new ApplyWorldshapeBiomeModifier(descriptor);
        modifier.modify(wastes, BiomeModifier.Phase.REMOVE, builder);
        modifier.modify(wastes, BiomeModifier.Phase.ADD, builder);

        var quartzFeature = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ORE_QUARTZ_NETHER).value().feature();
        int[] injected = null;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            for (Holder<PlacedFeature> h : builder.getGenerationSettings().getFeatures(step)) {
                if (h.unwrapKey().isPresent()) continue;
                if (h.value().feature().equals(quartzFeature)) {
                    injected = absoluteRange(h.value());
                }
            }
        }
        if (injected == null) {
            helper.fail("ADD phase injected no variant of ore_quartz_nether");
            return;
        }

        int[] expected = linear(source, PLAYABLE, profile.minY(), profile.maxY());
        int[] overworld = linear(source, PLAYABLE, -64, 319);
        if (Arrays.equals(expected, overworld)) {
            helper.fail("nether and overworld envelopes produced the same band; test is not discriminating");
            return;
        }
        if (!Arrays.equals(injected, expected)) {
            helper.fail("injected quartz range " + Arrays.toString(injected)
                    + " != nether-envelope projection " + Arrays.toString(expected)
                    + " (overworld-envelope projection would be " + Arrays.toString(overworld) + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * The shipped {@code isekai_api:hooked_nether} preset decodes against the live registry:
     * nether noise height, netherrack/lava defaults, the nether surface delegate, and the
     * nether density hook (whose reference target {@code minecraft:nether/base_3d_noise} must
     * exist).
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void hookedNetherPresetIsWired(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var regs = level.registryAccess();
        ResourceKey<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> key =
                ResourceKey.create(Registries.NOISE_SETTINGS,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                IsekaiApi.MODID, "hooked_nether"));
        var holder = regs.lookupOrThrow(Registries.NOISE_SETTINGS).get(key).orElse(null);
        if (holder == null) {
            helper.fail("isekai_api:hooked_nether noise_settings not loaded — preset missing");
            return;
        }
        var settings = holder.value();
        expect(helper, "seaLevel", settings.seaLevel(), 32);
        expect(helper, "noise.minY", settings.noiseSettings().minY(), 0);
        expect(helper, "noise.height", settings.noiseSettings().height(), 128);
        if (settings.defaultBlock().getBlock() != Blocks.NETHERRACK) {
            helper.fail("hooked_nether default_block is not netherrack: " + settings.defaultBlock());
            return;
        }
        if (settings.defaultFluid().getFluidState().getType() != Fluids.LAVA) {
            helper.fail("hooked_nether default_fluid is not lava: " + settings.defaultFluid());
            return;
        }
        if (settings.noiseRouter().finalDensity() == null) {
            helper.fail("hooked_nether final_density did not resolve");
            return;
        }
        String surface = String.valueOf(settings.surfaceRule());
        if (!surface.contains("isekai_api:vanilla_nether_surface")) {
            helper.fail("hooked_nether surface_rule is not the vanilla_nether_surface delegate: " + surface);
            return;
        }
        var dfReg = regs.lookupOrThrow(Registries.DENSITY_FUNCTION);
        ResourceKey<net.minecraft.world.level.levelgen.DensityFunction> hook = ResourceKey.create(
                Registries.DENSITY_FUNCTION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        IsekaiApi.MODID, "hook/final_density_nether"));
        if (dfReg.get(hook).isEmpty()) {
            helper.fail("isekai_api:hook/final_density_nether not loaded");
            return;
        }
        ResourceKey<net.minecraft.world.level.levelgen.DensityFunction> base = ResourceKey.create(
                Registries.DENSITY_FUNCTION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "nether/base_3d_noise"));
        if (dfReg.get(base).isEmpty()) {
            helper.fail("minecraft:nether/base_3d_noise not present — hook default's target missing");
            return;
        }
        helper.succeed();
    }

    /** The nether hook carves caves: solid floor, air somewhere in the playable band. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void hookedNetherProducesNetherTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var regs = level.registryAccess();
        ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        IsekaiApi.MODID, "hooked_nether"));
        NoiseGeneratorSettings settings = regs.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(key).value();
        RandomState rs = RandomState.create(settings,
                regs.lookupOrThrow(Registries.NOISE), level.getSeed());
        DensityFunction fd = rs.router().finalDensity();

        int[][] columns = { {0, 0}, {96, -64}, {-48, 112}, {256, 32}, {-160, -96} };
        for (int[] c : columns) {
            double floor = fd.compute(new DensityFunction.SinglePointContext(c[0], 0, c[1]));
            if (!(floor > 0.0)) {
                helper.fail("nether hook not solid at Y=0 for column " + c[0] + "," + c[1]
                        + " (got " + floor + ")");
                return;
            }
        }
        boolean anyAir = false;
        for (int[] c : columns) {
            for (int y = 32; y <= 96 && !anyAir; y += 8) {
                if (fd.compute(new DensityFunction.SinglePointContext(c[0], y, c[1])) < 0.0) {
                    anyAir = true;
                }
            }
        }
        if (!anyAir) {
            helper.fail("nether hook is solid through Y=32..96 in every sampled column — no caves");
            return;
        }
        helper.succeed();
    }

    /** Diagnostics resolve the nether profile and find no errors for an untouched dimension. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void dimensionDiagnosticsReadsNetherProfile(GameTestHelper helper) {
        var report = com.kuronami.isekaiapi.validation.DimensionDiagnostics.validate(
                helper.getLevel().getServer(), Level.NETHER);
        var profile = report.profile().orElse(null);
        if (profile == null) {
            helper.fail("diagnostics found no nether profile");
            return;
        }
        if (profile.minY() != 0 || profile.height() != 256 || profile.seaLevel() != 32) {
            helper.fail("nether profile is " + profile.minY() + "/" + profile.height()
                    + "/" + profile.seaLevel() + "; expected 0/256/32");
            return;
        }
        if (!report.isOk()) {
            helper.fail("diagnostics reported an error for the nether: " + report.issues());
            return;
        }
        helper.succeed();
    }

    /** A dimension outside the LevelStem registry is an error, not an empty report. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void dimensionDiagnosticsRejectsUnknownDimension(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        IsekaiApi.MODID, "not_a_dimension"));
        var report = com.kuronami.isekaiapi.validation.DimensionDiagnostics.validate(
                helper.getLevel().getServer(), key);
        if (report.isOk()) {
            helper.fail("unknown dimension produced an ok report");
            return;
        }
        if (report.profile().isPresent()) {
            helper.fail("unknown dimension produced a profile");
            return;
        }
        helper.succeed();
    }

    /** Empty applies_to and an out-of-bounds playable range are reported as warnings. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void dimensionDiagnosticsWarnsOnSilentNoOps(GameTestHelper helper) {
        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.NETHER)
                .playableRange(new VerticalRange(-64, 400, HeightDistribution.UNIFORM))
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of())
                .build();
        Isekai.remap().declareWorldshape(descriptor);
        try {
            var report = com.kuronami.isekaiapi.validation.DimensionDiagnostics.validate(
                    helper.getLevel().getServer(), Level.NETHER);
            String text = report.issues().toString();
            if (report.count(com.kuronami.isekaiapi.validation.DimensionDiagnostics.Severity.WARN) < 2) {
                helper.fail("expected at least two warnings, got: " + text);
                return;
            }
            if (!text.contains("applies_to is empty")) {
                helper.fail("missing empty applies_to warning: " + text);
                return;
            }
            if (!text.contains("outside this dimension's build height")) {
                helper.fail("missing playable_range warning: " + text);
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(Level.NETHER);
        }
        helper.succeed();
    }

    /**
     * Validating a remapped dimension is read-only: the spawn-column check reports the ungenerated
     * (0,0) as a skip instead of sampling it, because the heightmap and block reads it would need
     * both generate the chunk. A diagnostic that writes world data changes the thing it measures.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void dimensionDiagnosticsDoesNotGenerateChunks(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.fail("the nether level is not loaded");
            return;
        }
        if (nether.getChunkSource().hasChunk(0, 0)) {
            helper.fail("nether chunk (0,0) already exists before the test, so generation by the"
                    + " diagnosis could not be told apart");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.NETHER)
                .playableRange(PLAYABLE)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .appliesTo(Set.of(Biomes.NETHER_WASTES))
                .build();
        Isekai.remap().declareWorldshape(descriptor);
        try {
            var report = com.kuronami.isekaiapi.validation.DimensionDiagnostics.validate(
                    helper.getLevel().getServer(), Level.NETHER);
            String text = report.issues().toString();
            if (!report.profile().isPresent()) {
                helper.fail("diagnostics found no nether profile: " + text);
                return;
            }
            if (!text.contains("spawn SKIP")) {
                helper.fail("expected the spawn column to be skipped, got: " + text);
                return;
            }
            if (!text.contains("is not loaded")) {
                helper.fail("the skip did not say the chunk is ungenerated: " + text);
                return;
            }
            if (text.contains("spawn ERROR") || text.contains("spawn WARN")) {
                helper.fail("an ungenerated chunk produced a spawn verdict: " + text);
                return;
            }
            if (!report.isOk()) {
                helper.fail("diagnostics reported an error for an untouched nether: " + text);
                return;
            }
            if (nether.getChunkSource().hasChunk(0, 0)) {
                helper.fail("the diagnosis generated nether chunk (0,0)");
                return;
            }
        } finally {
            Isekai.remap().removeWorldshape(Level.NETHER);
        }
        helper.succeed();
    }

    /** Structure exclusion is keyed by the descriptor, independent of dimension bounds. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void netherStructureExclusionIsDimensionScoped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structureLookup = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> fortress = structureLookup.getOrThrow(BuiltinStructures.FORTRESS);
        var originalInfo = fortress.value().modifiableStructureInfo().getOriginalStructureInfo();
        int before = originalInfo.structureSettings().biomes().size();
        if (before == 0) {
            helper.fail("fortress already has an empty biome set — test precondition unmet");
            return;
        }

        WorldshapeDescriptor descriptor = WorldshapeDescriptor.builder()
                .dimension(Level.NETHER)
                .playableRange(PLAYABLE)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .exclusions(new WorldshapeDescriptor.Exclusions(
                        Set.of(), Set.of(BuiltinStructures.FORTRESS), Set.of(), Set.of()))
                .build();

        if (!StructureExclusion.excludes(descriptor, BuiltinStructures.FORTRESS)) {
            helper.fail("descriptor does not report fortress as excluded");
            return;
        }

        // The exclusion must not touch the shared structure definition: emptying the biome
        // filter would also remove the fortress from every dimension that reuses nether biomes.
        var builder = ModifiableStructureInfo.StructureInfo.Builder.copyOf(originalInfo);
        new ApplyWorldshapeStructureModifier(descriptor)
                .modify(fortress, StructureModifier.Phase.REMOVE, builder);
        int after = builder.getStructureSettings().build().biomes().size();
        if (after != before) {
            helper.fail("REMOVE phase mutated the global biome filter (" + before + " -> " + after
                    + "); exclusion must stay in the per-dimension generation hook");
            return;
        }
        helper.succeed();
    }

    // -- helpers ----------------------------------------------------------------

    /** Mirrors production {@code Linear}: normalise through the world envelope, then into playable. */
    private static int[] linear(VerticalRange source, VerticalRange playable, int worldBottom, int worldTop) {
        int span = worldTop - worldBottom;
        int playSpan = playable.maxY() - playable.minY();
        double tMin = (source.minY() - worldBottom) / (double) span;
        double tMax = (source.maxY() - worldBottom) / (double) span;
        int min = playable.minY() + (int) Math.round(tMin * playSpan);
        int max = playable.minY() + (int) Math.round(tMax * playSpan);
        min = Math.max(playable.minY(), Math.min(min, playable.maxY()));
        max = Math.max(min, Math.min(max, playable.maxY()));
        return new int[]{min, max};
    }

    /** Absolute range of a rebuilder-produced PlacedFeature (its anchors are Absolute). */
    private static int[] absoluteRange(PlacedFeature feature) {
        HeightProvider hp = null;
        for (PlacementModifier mod : feature.placement()) {
            if (mod instanceof HeightRangePlacement hrp) {
                hp = hrp.height;
                break;
            }
        }
        if (hp == null) return null;
        if (hp instanceof UniformHeight uh) return new int[]{abs(uh.minInclusive), abs(uh.maxInclusive)};
        if (hp instanceof TrapezoidHeight th) return new int[]{abs(th.minInclusive), abs(th.maxInclusive)};
        if (hp instanceof BiasedToBottomHeight bth) return new int[]{abs(bth.minInclusive), abs(bth.maxInclusive)};
        if (hp instanceof VeryBiasedToBottomHeight v) return new int[]{abs(v.minInclusive), abs(v.maxInclusive)};
        if (hp instanceof ConstantHeight ch) {
            int y = abs(ch.value);
            return new int[]{y, y};
        }
        return null;
    }

    private static int abs(VerticalAnchor anchor) {
        if (anchor instanceof VerticalAnchor.Absolute a) return a.y();
        return 0;
    }

    private static void expect(GameTestHelper helper, String name, int actual, int expected) {
        if (actual != expected) {
            helper.fail(name + " = " + actual + "; expected " + expected);
        }
    }

    private static void expect(GameTestHelper helper, String name, boolean actual, boolean expected) {
        if (actual != expected) {
            helper.fail(name + " = " + actual + "; expected " + expected);
        }
    }

    private static void expect(GameTestHelper helper, String name, double actual, double expected) {
        if (actual != expected) {
            helper.fail(name + " = " + actual + "; expected " + expected);
        }
    }
}
