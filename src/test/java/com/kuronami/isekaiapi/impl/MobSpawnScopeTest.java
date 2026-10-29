package com.kuronami.isekaiapi.impl;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-state tests for {@link MobSpawnScope}: the rule is keyed by dimension, and the
 * transformed list keeps the original {@link MobSpawnSettings.SpawnerData} instances wherever
 * the weight did not change. That identity matters because {@code NaturalSpawner} compares the
 * entries it picked against a second lookup of the same list with {@code contains}.
 */
class MobSpawnScopeTest {

    private static final ResourceKey<Biome> PLAINS =
            ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:plains"));
    private static final ResourceKey<Level> DIM_A =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("isekai_test:dim_a"));
    private static final ResourceKey<Level> DIM_B =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("isekai_test:dim_b"));
    private static final ResourceKey<Level> DIM_C =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("isekai_test:dim_c"));

    private static final HolderOwner<Biome> OWNER = new HolderOwner<>() {};

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clean() {
        MobSpawnScope.clear();
    }

    @Test
    void mobSpawnScopeIsDimensionScoped() {
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(OWNER, PLAINS);
        MobSpawnSettings.SpawnerData zombie =
                new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4);
        MobSpawnSettings.SpawnerData skeleton =
                new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 100, 1, 4);
        List<MobSpawnSettings.SpawnerData> original = List.of(zombie, skeleton);

        MobSpawnScope.record(DIM_A, PLAINS, new MobSpawnScope.MobSpawnRule(
                Set.of(MobSpawnScope.entityKey(EntityType.ZOMBIE)), Map.of(), 0.5, Map.of()));

        List<MobSpawnSettings.SpawnerData> inA =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, original);
        assertEquals(1, inA.size(), "zombie is excluded in dim A only");
        assertEquals(EntityType.SKELETON, inA.get(0).type);
        assertEquals(50, inA.get(0).getWeight().asInt(), "weight scaled by 0.5");

        // Same biome, different dimension: the rule must not leak, and the caller's own list
        // instance has to come back untouched (no copy, no re-scaled entries).
        List<MobSpawnSettings.SpawnerData> inB =
                MobSpawnScope.apply(DIM_B, plains, MobCategory.MONSTER, original);
        assertSame(original, inB);

        assertTrue(MobSpawnScope.hasRulesFor(DIM_A, plains));
        assertFalse(MobSpawnScope.hasRulesFor(DIM_B, plains));
    }

    @Test
    void mobSpawnScopeKeepsSpawnerDataIdentityWhenUnscaled() {
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(OWNER, PLAINS);
        MobSpawnSettings.SpawnerData zombie =
                new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4);
        MobSpawnSettings.SpawnerData skeleton =
                new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 100, 1, 4);
        List<MobSpawnSettings.SpawnerData> original = List.of(zombie, skeleton);

        MobSpawnScope.record(DIM_A, PLAINS, new MobSpawnScope.MobSpawnRule(
                Set.of(MobSpawnScope.entityKey(EntityType.ZOMBIE)), Map.of(), 1.0, Map.of()));

        List<MobSpawnSettings.SpawnerData> first =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, original);
        assertEquals(1, first.size());
        // Surviving entry is the very same instance, not a rebuilt one with weight 100.
        assertSame(skeleton, first.get(0));

        // The transform runs again per call, but the surviving entry is the original instance,
        // so the second lookup still matches by identity — what NaturalSpawner#canSpawnMobAt
        // relies on.
        List<MobSpawnSettings.SpawnerData> second =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, original);
        assertSame(first.get(0), second.get(0));
        assertNotSame(original, first);

        MobSpawnScope.clear();
        assertTrue(MobSpawnScope.isEmpty());
    }

    @Test
    void mobSpawnScopeInternsRebuiltEntriesAcrossEquivalentLists() {
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(OWNER, PLAINS);
        MobSpawnSettings.SpawnerData zombie =
                new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4);
        // Same entries, distinct list instances: what EventHooks.getPotentialSpawns hands out
        // per call once any subscriber exists. Element identity must survive the rebuild.
        List<MobSpawnSettings.SpawnerData> firstList = List.of(zombie);
        List<MobSpawnSettings.SpawnerData> secondList = List.of(zombie);
        assertNotSame(firstList, secondList);

        MobSpawnScope.record(DIM_A, PLAINS, new MobSpawnScope.MobSpawnRule(
                Set.of(), Map.of(), 0.5, Map.of()));

        List<MobSpawnSettings.SpawnerData> first =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, firstList);
        List<MobSpawnSettings.SpawnerData> second =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, secondList);

        assertEquals(50, first.get(0).getWeight().asInt());
        assertEquals(50, second.get(0).getWeight().asInt());
        // The rebuilt entry is interned by value, so NaturalSpawner#canSpawnMobAt's
        // contains(data) still matches across the two per-call lists.
        assertSame(first.get(0), second.get(0));
    }

    @Test
    void mobSpawnScopeIsNotSharedAcrossDimensionsWithDifferentRules() {
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(OWNER, PLAINS);
        MobSpawnSettings.SpawnerData zombie =
                new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4);
        MobSpawnSettings.SpawnerData skeleton =
                new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 100, 1, 4);
        MobSpawnSettings.SpawnerData creeper =
                new MobSpawnSettings.SpawnerData(EntityType.CREEPER, 100, 1, 4);
        List<MobSpawnSettings.SpawnerData> original = List.of(zombie, skeleton, creeper);

        MobSpawnScope.record(DIM_A, PLAINS, new MobSpawnScope.MobSpawnRule(
                Set.of(MobSpawnScope.entityKey(EntityType.ZOMBIE)), Map.of(), 1.0, Map.of()));
        MobSpawnScope.record(DIM_B, PLAINS, new MobSpawnScope.MobSpawnRule(
                Set.of(MobSpawnScope.entityKey(EntityType.SKELETON)), Map.of(), 1.0, Map.of()));

        List<MobSpawnSettings.SpawnerData> inA =
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, original);
        assertFalse(inA.stream().anyMatch(entry -> entry.type == EntityType.ZOMBIE),
                "dim A drops zombie");
        assertTrue(inA.stream().anyMatch(entry -> entry.type == EntityType.SKELETON),
                "dim A keeps skeleton");

        // Same biome, same list content, different rule: dim B must not see dim A's transform.
        List<MobSpawnSettings.SpawnerData> inB =
                MobSpawnScope.apply(DIM_B, plains, MobCategory.MONSTER, original);
        assertFalse(inB.stream().anyMatch(entry -> entry.type == EntityType.SKELETON),
                "dim B drops skeleton");
        assertTrue(inB.stream().anyMatch(entry -> entry.type == EntityType.ZOMBIE),
                "dim B keeps zombie");

        List<MobSpawnSettings.SpawnerData> inC =
                MobSpawnScope.apply(DIM_C, plains, MobCategory.MONSTER, original);
        assertSame(original, inC, "a dimension without a rule gets its own list back");
    }

    @Test
    void mobSpawnScopeWithoutRulesReturnsTheArgumentList() {
        Holder.Reference<Biome> plains = Holder.Reference.createStandAlone(OWNER, PLAINS);
        List<MobSpawnSettings.SpawnerData> original = List.of(
                new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 1, 4));

        assertSame(original,
                MobSpawnScope.apply(DIM_A, plains, MobCategory.MONSTER, original));
    }
}
