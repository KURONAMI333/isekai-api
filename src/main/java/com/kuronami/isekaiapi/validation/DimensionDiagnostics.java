package com.kuronami.isekaiapi.validation;

import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.query.DimensionProfile;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Runtime diagnosis for one dimension: is its {@code LevelStem} profile present, and does
 * every worldshape declared for it make sense against that profile?
 *
 * <p>Complements {@link IsekaiValidator}, which only decodes datapack JSON and therefore
 * cannot see the dimension's build height, sea level, or the registry's biome tags. The
 * checks here are the ones that silently produce no-op or wrong-Y worlds:
 *
 * <ul>
 *   <li>the dimension is absent from the {@code LevelStem} registry (ERROR)</li>
 *   <li>{@code playable_range} falls outside the dimension's build height (WARN — the remap
 *       clamps or falls back)</li>
 *   <li>{@code applies_to} is empty, so the descriptor matches no biome and does nothing
 *       (WARN — the opt-in semantics documented on {@code BiomeSelection})</li>
 *   <li>a biome tag in {@code applies_to} does not exist or resolves to no biome (WARN)</li>
 *   <li>placed features whose anchors cannot be represented in this dimension (INFO; they
 *       are skipped rather than projected against the overworld envelope)</li>
 *   <li>the start point is buried, has no footing within 32 blocks, or stands in lava
 *       (ERROR); it is under water (WARN); the dimension has no loaded level, or its (0,0)
 *       chunk is not generated yet (SKIP, INFO). A skip never samples the column: height and
 *       block reads would force chunk generation from a read-only diagnosis. Checked only
 *       when a worldshape is declared — that is the terrain Isekai is responsible for; an
 *       untouched dimension keeps vanilla's own spawn handling.</li>
 * </ul>
 *
 * <p>Read-only: nothing here declares, removes, or reloads a worldshape.
 * @since 3.0.0
 */
@ApiStatus.Internal
public final class DimensionDiagnostics {

    private DimensionDiagnostics() {}

    /** Severity of one diagnostic line. */
    public enum Severity { ERROR, WARN, INFO }

    /** One diagnostic line. {@code message} is player-facing and already includes the subject. */
    public record Issue(Severity severity, String message) {}

    /**
     * The full result. {@link #isOk()} is false only for {@link Severity#ERROR} — warnings
     * describe a worldshape that will run but probably not as the author intended.
     */
    public record Report(ResourceKey<Level> dimension,
                         Optional<DimensionProfile> profile,
                         List<Issue> issues) {

        public Report {
            issues = List.copyOf(issues);
        }

        public boolean isOk() {
            return issues.stream().noneMatch(i -> i.severity() == Severity.ERROR);
        }

        public long count(Severity severity) {
            return issues.stream().filter(i -> i.severity() == severity).count();
        }
    }

    public static Report validate(MinecraftServer server, ResourceKey<Level> dimension) {
        List<Issue> issues = new ArrayList<>();
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        DimensionProfile profile = snapshot.dimensionProfile(dimension).orElse(null);
        if (profile == null) {
            issues.add(new Issue(Severity.ERROR,
                    "dimension " + dimension.location() + " has no LevelStem — it is not defined "
                    + "by any datapack or mod (check data/<ns>/dimension/)"));
            return new Report(dimension, Optional.empty(), issues);
        }

        List<WorldshapeDescriptor> descriptors = descriptorsFor(dimension);
        if (descriptors.isEmpty()) {
            issues.add(new Issue(Severity.INFO,
                    "no worldshape declared for this dimension — Isekai leaves it untouched"));
            return new Report(dimension, Optional.of(profile), issues);
        }

        checkSpawnSafety(server, dimension, issues);

        for (WorldshapeDescriptor descriptor : descriptors) {
            checkPlayableRange(descriptor, profile, issues);
            checkAppliesTo(server, descriptor, issues);
            checkFallbackFeatures(snapshot, descriptor, dimension, issues);
        }
        return new Report(dimension, Optional.of(profile), issues);
    }

    private static List<WorldshapeDescriptor> descriptorsFor(ResourceKey<Level> dimension) {
        List<LayeredDescriptor> layers = Isekai.remap().getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            return layers.stream().map(LayeredDescriptor::descriptor).toList();
        }
        return Isekai.remap().getActiveDescriptor(dimension).map(List::of).orElse(List.of());
    }

    /**
     * Reports whether a player can stand at the dimension's start point. The start point is the
     * overworld's shared spawn; other dimensions have no per-dimension spawn, so the column at
     * (0,0) is used and named in the line.
     */
    private static void checkSpawnSafety(MinecraftServer server,
                                         ResourceKey<Level> dimension,
                                         List<Issue> issues) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            issues.add(new Issue(Severity.INFO,
                    "spawn SKIP: dimension " + dimension.location() + " is not loaded"));
            return;
        }

        if (!dimension.equals(Level.OVERWORLD) && !level.getChunkSource().hasChunk(0, 0)) {
            // hasChunk is the ticket-free view, so this check costs nothing. getHeightmapPos
            // and getBlockState below would both generate the chunk; a read-only diagnosis
            // must not create world data, so an ungenerated (0,0) is reported as a skip.
            issues.add(new Issue(Severity.INFO,
                    "spawn SKIP: chunk (0,0) of " + dimension.location()
                    + " is not generated yet — the column is not sampled (diagnosis never "
                    + "generates chunks)"));
            return;
        }

        BlockPos start;
        String source;
        if (dimension.equals(Level.OVERWORLD)) {
            start = level.getSharedSpawnPos();
            source = "shared_spawn";
        } else {
            start = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
            source = "heightmap(0,0)";
        }

        SpawnSafety.Column column = y -> sample(level, start.getX(), y, start.getZ());
        SpawnSafety.Result result = SpawnSafety.evaluate(column, start.getY(),
                level.getMinBuildHeight());
        Severity severity = switch (result.status()) {
            case ERROR -> Severity.ERROR;
            case WARN -> Severity.WARN;
            case OK, SKIP -> Severity.INFO;
        };
        issues.add(new Issue(severity, "spawn " + result.status()
                + " (" + source + " " + start.getX() + "," + start.getY() + "," + start.getZ()
                + "): " + result.detail()));
    }

    private static SpawnSafety.Sample sample(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        boolean solid = state.isSolidRender(level, pos);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return solid ? SpawnSafety.Sample.SOLID : SpawnSafety.Sample.AIR;
        }
        return new SpawnSafety.Sample(solid, true, fluid.is(FluidTags.LAVA));
    }

    private static void checkPlayableRange(WorldshapeDescriptor descriptor,
                                           DimensionProfile profile,
                                           List<Issue> issues) {
        VerticalRange playable = descriptor.playableRange();
        if (playable.minY() < profile.minY() || playable.maxY() > profile.maxY()) {
            issues.add(new Issue(Severity.WARN,
                    "playable_range [" + playable.minY() + "," + playable.maxY() + "]"
                    + " is outside this dimension's build height [" + profile.minY() + ","
                    + profile.maxY() + "]; remapped features are clamped or fall back"));
        }
        if (descriptor.dimension() != null && !descriptor.dimension().equals(profile.dimension())) {
            issues.add(new Issue(Severity.ERROR,
                    "descriptor dimension " + descriptor.dimension().location()
                    + " does not match the validated dimension " + profile.dimension().location()));
        }
    }

    private static void checkAppliesTo(MinecraftServer server,
                                       WorldshapeDescriptor descriptor,
                                       List<Issue> issues) {
        if (descriptor.appliesTo().isEmpty()) {
            issues.add(new Issue(Severity.WARN,
                    "applies_to is empty — the descriptor matches no biome and has no effect "
                    + "(NeoForge modifiers have no dimension scope; opt into biomes or a tag)"));
            return;
        }
        if (!descriptor.appliesTo().keys().isEmpty()) {
            var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
            for (ResourceKey<Biome> key : descriptor.appliesTo().keys()) {
                if (biomes.get(key).isEmpty()) {
                    issues.add(new Issue(Severity.WARN,
                            "applies_to biome " + key.location() + " is not in the biome registry"));
                }
            }
        }
        if (!descriptor.appliesTo().tags().isEmpty()) {
            var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
            for (TagKey<Biome> tag : descriptor.appliesTo().tags()) {
                var holders = biomes.get(tag);
                if (holders.isEmpty() || holders.get().size() == 0) {
                    issues.add(new Issue(Severity.WARN,
                            "applies_to tag #" + tag.location()
                            + " does not exist or resolves to no biome"));
                }
            }
        }
    }

    private static void checkFallbackFeatures(VanillaRuleSnapshot snapshot,
                                              WorldshapeDescriptor descriptor,
                                              ResourceKey<Level> dimension,
                                              List<Issue> issues) {
        if (descriptor.oreStrategy() instanceof RemapStrategy.Identity) return;
        long skipped = snapshot.placedFeatures().stream()
                .filter(info -> snapshot.isFallbackInDimension(info, dimension))
                .count();
        if (skipped > 0) {
            issues.add(new Issue(Severity.INFO,
                    skipped + " placed feature(s) have anchors that do not fit this dimension "
                    + "and are skipped by the ore remap"));
        }
    }
}
