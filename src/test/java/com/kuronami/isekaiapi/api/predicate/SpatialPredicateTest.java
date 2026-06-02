package com.kuronami.isekaiapi.api.predicate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SpatialPredicate} structural variants (combinators + Always/Never).
 * Pure data-logic; no game bootstrap needed.
 *
 * Note: YInRange, SolidFloor, SolidCeiling, TerrainSlope, NearBlock, NearBiome, InFluid
 * are data containers without a testable compute() method (evaluation requires a live world
 * context). We verify their construction, typeId, and immutability.
 */
class SpatialPredicateTest {

    // ===== Always / Never =====

    @Test void always_typeId() {
        assertEquals("isekai:always", SpatialPredicate.Always.INSTANCE.typeId());
    }

    @Test void never_typeId() {
        assertEquals("isekai:never", SpatialPredicate.Never.INSTANCE.typeId());
    }

    // ===== YInRange (data container) =====

    @Test void yInRange_typeId() {
        assertEquals("isekai:y_in_range", new SpatialPredicate.YInRange(0, 100).typeId());
    }

    @Test void yInRange_storesFields() {
        var r = new SpatialPredicate.YInRange(-64, 320);
        assertEquals(-64, r.min());
        assertEquals(320, r.max());
    }

    // ===== SolidFloor =====

    @Test void solidFloor_typeId() {
        assertEquals("isekai:solid_floor", new SpatialPredicate.SolidFloor(3).typeId());
    }

    @Test void solidFloor_storesClearance() {
        assertEquals(5, new SpatialPredicate.SolidFloor(5).minClearance());
    }

    // ===== SolidCeiling =====

    @Test void solidCeiling_typeId() {
        assertEquals("isekai:solid_ceiling", new SpatialPredicate.SolidCeiling(2).typeId());
    }

    @Test void solidCeiling_storesClearance() {
        assertEquals(2, new SpatialPredicate.SolidCeiling(2).minClearance());
    }

    // ===== TerrainSlope =====

    @Test void terrainSlope_typeId() {
        assertEquals("isekai:terrain_slope", new SpatialPredicate.TerrainSlope(0.0, 0.5).typeId());
    }

    @Test void terrainSlope_storesFields() {
        var ts = new SpatialPredicate.TerrainSlope(0.1, 0.8);
        assertEquals(0.1, ts.minSlope(), 1e-9);
        assertEquals(0.8, ts.maxSlope(), 1e-9);
    }

    // ===== And (combinator) =====

    @Test void and_typeId() {
        assertEquals("isekai:and", new SpatialPredicate.And(List.of()).typeId());
    }

    @Test void and_emptyAll_isImmutableList() {
        var and = new SpatialPredicate.And(List.of());
        assertTrue(and.all().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> and.all().add(null));
    }

    @Test void and_storesChildren() {
        var and = new SpatialPredicate.And(List.of(
                new SpatialPredicate.YInRange(0, 100),
                new SpatialPredicate.YInRange(50, 200)));
        assertEquals(2, and.all().size());
    }

    @Test void and_listIsCopied_mutationDoesNotAffect() {
        var list = new java.util.ArrayList<SpatialPredicate>();
        list.add(new SpatialPredicate.YInRange(0, 10));
        var and = new SpatialPredicate.And(list);
        list.clear();
        assertEquals(1, and.all().size());
    }

    // ===== Or (combinator) =====

    @Test void or_typeId() {
        assertEquals("isekai:or", new SpatialPredicate.Or(List.of()).typeId());
    }

    @Test void or_storesChildren() {
        var or = new SpatialPredicate.Or(List.of(new SpatialPredicate.Always()));
        assertEquals(1, or.any().size());
    }

    @Test void or_listIsCopied() {
        var list = new java.util.ArrayList<SpatialPredicate>();
        list.add(new SpatialPredicate.YInRange(0, 10));
        var or = new SpatialPredicate.Or(list);
        list.clear();
        assertEquals(1, or.any().size());
    }

    // ===== Not (combinator) =====

    @Test void not_typeId() {
        assertEquals("isekai:not", new SpatialPredicate.Not(new SpatialPredicate.Always()).typeId());
    }

    @Test void not_storesInner() {
        var inner = new SpatialPredicate.YInRange(10, 50);
        var not = new SpatialPredicate.Not(inner);
        assertSame(inner, not.inner());
    }

    // ===== NearBiome =====

    @Test void nearBiome_typeId() {
        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.parse("minecraft:plains"));
        assertEquals("isekai:near_biome", new SpatialPredicate.NearBiome(key, 100).typeId());
    }
}
