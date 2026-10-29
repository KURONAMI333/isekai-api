package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pure-state tests for {@link CarverScope}: the rule is keyed by (dimension, biome), additions
 * are appended after the base list, and an exclusion still wins over an addition — the same
 * result the two biome-modifier phases produced when both edited one builder.
 */
class CarverScopeTest {

    private static final HolderOwner<ConfiguredWorldCarver<?>> CARVER_OWNER = new HolderOwner<>() {};
    private static final HolderOwner<Biome> BIOME_OWNER = new HolderOwner<>() {};

    private static final ResourceKey<Biome> PLAINS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:plains"));
    private static final ResourceKey<Biome> DESERT =
            ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:desert"));
    private static final ResourceKey<Level> DIM_A =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("isekai_test:dim_a"));
    private static final ResourceKey<Level> DIM_B =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("isekai_test:dim_b"));

    private static final ResourceKey<ConfiguredWorldCarver<?>> CAVE = carver("minecraft:cave");
    private static final ResourceKey<ConfiguredWorldCarver<?>> NETHER_CAVE =
            carver("minecraft:nether_cave");
    private static final ResourceKey<ConfiguredWorldCarver<?>> CANYON = carver("minecraft:canyon");

    /** Resolves every addition key to a stand-alone reference — no registry needed. */
    private static final HolderGetter<ConfiguredWorldCarver<?>> CARVERS = new HolderGetter<>() {
        @Override
        public Optional<Holder.Reference<ConfiguredWorldCarver<?>>> get(
                ResourceKey<ConfiguredWorldCarver<?>> key) {
            return Optional.of(Holder.Reference.createStandAlone(CARVER_OWNER, key));
        }

        @Override
        public Optional<HolderSet.Named<ConfiguredWorldCarver<?>>> get(
                TagKey<ConfiguredWorldCarver<?>> tag) {
            return Optional.empty();
        }
    };

    @AfterEach
    void clean() {
        CarverScope.clearAll();
    }

    @Test
    void carverScopeIsDimensionScoped() {
        BiomeGenerationSettings base = new BiomeGenerationSettings(
                Map.of(
                        GenerationStep.Carving.AIR, HolderSet.direct(List.of(holder(CAVE))),
                        GenerationStep.Carving.LIQUID, HolderSet.direct(List.of(holder(NETHER_CAVE)))),
                List.of());
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(BIOME_OWNER, PLAINS);
        Holder.Reference<Biome> desert = Holder.Reference.createStandAlone(BIOME_OWNER, DESERT);

        // ADD runs before REMOVE in NeoForge, so the effective list is
        // (base + additions) - exclusions: the canyon is appended to AIR, the nether cave is
        // dropped from LAVA, and an addition that names an excluded carver is dropped too.
        CarverScope.recordAdditions(DIM_A, PLAINS, List.of(
                new WorldshapeDescriptor.AdditionalCarver(CANYON, GenerationStep.Carving.AIR),
                new WorldshapeDescriptor.AdditionalCarver(NETHER_CAVE, GenerationStep.Carving.AIR)));
        CarverScope.recordRemovals(DIM_A, PLAINS, Set.of(NETHER_CAVE));

        BiomeGenerationSettings scoped = CarverScope.filter(DIM_A, plains, base, CARVERS);
        assertEquals(List.of(CAVE, CANYON), keys(scoped, GenerationStep.Carving.AIR));
        assertEquals(List.of(), keys(scoped, GenerationStep.Carving.LIQUID));

        // Same biome, undeclared dimension: the shared settings pass through untouched.
        assertSame(base, CarverScope.filter(DIM_B, plains, base, CARVERS));
        // Declared dimension, biome the descriptor never matched: also untouched.
        assertSame(base, CarverScope.filter(DIM_A, desert, base, CARVERS));
        // Resolved once per pair, then reused — the carve pass asks per neighbour chunk.
        assertSame(scoped, CarverScope.filter(DIM_A, plains, base, CARVERS));
        // The base object itself is never mutated.
        assertEquals(List.of(NETHER_CAVE), keys(base, GenerationStep.Carving.LIQUID));
    }

    private static List<ResourceKey<ConfiguredWorldCarver<?>>> keys(BiomeGenerationSettings settings,
                                                                   GenerationStep.Carving step) {
        List<ResourceKey<ConfiguredWorldCarver<?>>> out = new ArrayList<>();
        settings.getCarvers(step).forEach(holder ->
                out.add(holder.unwrapKey().orElseThrow()));
        return out;
    }

    private static Holder<ConfiguredWorldCarver<?>> holder(
            ResourceKey<ConfiguredWorldCarver<?>> key) {
        return Holder.Reference.createStandAlone(CARVER_OWNER, key);
    }

    private static ResourceKey<ConfiguredWorldCarver<?>> carver(String id) {
        return ResourceKey.create(Registries.CONFIGURED_CARVER, ResourceLocation.parse(id));
    }
}
