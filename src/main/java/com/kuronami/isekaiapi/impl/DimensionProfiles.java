package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.DimensionProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.ApiStatus;

/**
 * Builds {@link DimensionProfile}s from the datapack's {@code LevelStem} registry.
 *
 * <p>Deliberately registry-based rather than {@code ServerLevel}-based: NeoForge applies biome
 * modifiers (and therefore Isekai's remap) before {@code ServerAboutToStartEvent}, when no
 * level is loaded yet. The {@code LevelStem} entries are already decoded at that point, so a
 * profile — and the nether's real {@code 0..255} envelope — is available for the first
 * modifier pass instead of after the world has been created.
 */
@ApiStatus.Internal
public final class DimensionProfiles {

    private DimensionProfiles() {}

    /** Read every {@code LevelStem} into a dimension-keyed profile map. */
    public static Map<ResourceKey<Level>, DimensionProfile> scan(MinecraftServer server) {
        Map<ResourceKey<Level>, DimensionProfile> out = new HashMap<>();
        var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        stems.listElements().forEach(ref -> {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ref.key().location());
            out.put(dimension, from(dimension, ref.value()));
        });
        return Map.copyOf(out);
    }

    /** Build one profile from a decoded level stem. */
    public static DimensionProfile from(ResourceKey<Level> dimension, LevelStem stem) {
        DimensionType type = stem.type().value();
        Optional<Block> defaultBlock = Optional.empty();
        Optional<Fluid> defaultFluid = Optional.empty();
        int seaLevel = DimensionProfile.UNKNOWN_SEA_LEVEL;
        if (stem.generator() instanceof NoiseBasedChunkGenerator noise) {
            var settings = noise.generatorSettings().value();
            seaLevel = settings.seaLevel();
            defaultBlock = Optional.of(settings.defaultBlock().getBlock());
            defaultFluid = Optional.of(settings.defaultFluid().getFluidState().getType());
        }
        return new DimensionProfile(
                dimension,
                stem.type().unwrapKey().orElseThrow(
                        () -> new IllegalStateException("dimension type has no key: " + stem.type())),
                type.minY(),
                type.height(),
                type.logicalHeight(),
                type.hasCeiling(),
                type.hasSkyLight(),
                type.ultraWarm(),
                type.natural(),
                type.coordinateScale(),
                type.ambientLight(),
                type.bedWorks(),
                type.respawnAnchorWorks(),
                type.piglinSafe(),
                type.hasRaids(),
                seaLevel,
                defaultBlock,
                defaultFluid);
    }
}
