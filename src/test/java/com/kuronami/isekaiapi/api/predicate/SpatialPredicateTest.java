package com.kuronami.isekaiapi.api.predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SpatialPredicate} variants: construction/immutability, the {@link #codec()}
 * contract (each variant returns its registered {@code MapCodec}), the {@link SpatialPredicate#test}
 * evaluation logic (leaf delegation + combinator composition), and {@link SpatialPredicate#children()}.
 *
 * <p>Pure data-logic; the world is a hand-built {@link StubContext}, so no game bootstrap is needed.
 */
class SpatialPredicateTest {

    /**
     * Minimal {@link EvaluationContext} for unit tests. {@code y} drives {@code YInRange};
     * the boolean fields drive the terrain queries; {@link #pos()} reports {@code (x, y, z)}.
     */
    private record StubContext(int x, int y, int z, boolean solidFloor, boolean solidCeiling,
                               boolean inFluid, boolean nearBlock, boolean nearBiome,
                               boolean terrainSlope) implements EvaluationContext {
        static StubContext atY(int y) { return new StubContext(0, y, 0, false, false, false, false, false, false); }
        @Override public BlockPos pos() { return new BlockPos(x, y, z); }
        @Override public boolean solidFloor(int minClearance) { return solidFloor; }
        @Override public boolean solidCeiling(int minClearance) { return solidCeiling; }
        @Override public boolean inFluid(Fluid fluid) { return inFluid; }
        @Override public boolean nearBlock(HolderSet<Block> targets, int maxDistance) { return nearBlock; }
        @Override public boolean nearBiome(ResourceKey<Biome> biome, int maxDistance) { return nearBiome; }
        @Override public boolean terrainSlope(double minSlope, double maxSlope) { return terrainSlope; }
    }

    // ===== codec() contract: each variant returns its own registered MapCodec =====

    @Test void always_codecIsRegistered() {
        assertSame(SpatialPredicate.Always.MAP_CODEC, SpatialPredicate.Always.INSTANCE.codec());
    }

    @Test void never_codecIsRegistered() {
        assertSame(SpatialPredicate.Never.MAP_CODEC, SpatialPredicate.Never.INSTANCE.codec());
    }

    @Test void yInRange_codecIsRegistered() {
        assertSame(SpatialPredicate.YInRange.MAP_CODEC, new SpatialPredicate.YInRange(0, 100).codec());
    }

    @Test void solidFloor_codecIsRegistered() {
        assertSame(SpatialPredicate.SolidFloor.MAP_CODEC, new SpatialPredicate.SolidFloor(3).codec());
    }

    // ===== Always / Never behavior =====

    @Test void always_isTrueEverywhere() {
        assertTrue(SpatialPredicate.Always.INSTANCE.test(StubContext.atY(0)));
    }

    @Test void never_isFalseEverywhere() {
        assertFalse(SpatialPredicate.Never.INSTANCE.test(StubContext.atY(0)));
    }

    // ===== YInRange (data + behavior) =====

    @Test void yInRange_storesFields() {
        var r = new SpatialPredicate.YInRange(-64, 320);
        assertEquals(-64, r.min());
        assertEquals(320, r.max());
    }

    @Test void yInRange_inclusiveBounds() {
        var r = new SpatialPredicate.YInRange(0, 100);
        assertTrue(r.test(StubContext.atY(0)));
        assertTrue(r.test(StubContext.atY(100)));
        assertTrue(r.test(StubContext.atY(50)));
        assertFalse(r.test(StubContext.atY(-1)));
        assertFalse(r.test(StubContext.atY(101)));
    }

    // ===== Leaf variants delegate to the matching context query =====

    @Test void solidFloor_delegatesToContext() {
        var sf = new SpatialPredicate.SolidFloor(3);
        assertTrue(sf.test(new StubContext(0, 64, 0, true, false, false, false, false, false)));
        assertFalse(sf.test(new StubContext(0, 64, 0, false, false, false, false, false, false)));
    }

    @Test void solidCeiling_delegatesToContext() {
        var sc = new SpatialPredicate.SolidCeiling(2);
        assertTrue(sc.test(new StubContext(0, 64, 0, false, true, false, false, false, false)));
        assertFalse(sc.test(new StubContext(0, 64, 0, false, false, false, false, false, false)));
    }

    @Test void terrainSlope_delegatesToContext() {
        var ts = new SpatialPredicate.TerrainSlope(0.1, 0.8);
        assertTrue(ts.test(new StubContext(0, 64, 0, false, false, false, false, false, true)));
        assertFalse(ts.test(new StubContext(0, 64, 0, false, false, false, false, false, false)));
        assertEquals(0.1, ts.minSlope(), 1e-9);
        assertEquals(0.8, ts.maxSlope(), 1e-9);
    }

    // ===== And / Or / Not composition + children() =====

    @Test void and_allChildrenMustHold() {
        var inRange = new SpatialPredicate.YInRange(0, 128);
        var and = new SpatialPredicate.And(List.of(inRange, new SpatialPredicate.Not(new SpatialPredicate.YInRange(200, 300))));
        assertTrue(and.test(StubContext.atY(64)));
        assertFalse(new SpatialPredicate.And(List.of(inRange, new SpatialPredicate.Never())).test(StubContext.atY(64)));
    }

    @Test void and_emptyIsTrue_andImmutable() {
        var and = new SpatialPredicate.And(List.of());
        assertTrue(and.test(StubContext.atY(0)));
        assertTrue(and.all().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> and.all().add(null));
    }

    @Test void and_childrenExposesAll() {
        var and = new SpatialPredicate.And(List.of(
                new SpatialPredicate.YInRange(0, 100), new SpatialPredicate.YInRange(50, 200)));
        assertEquals(2, and.children().size());
        assertEquals(and.all(), and.children());
    }

    @Test void and_listIsCopied_mutationDoesNotAffect() {
        var list = new java.util.ArrayList<SpatialPredicate>();
        list.add(new SpatialPredicate.YInRange(0, 10));
        var and = new SpatialPredicate.And(list);
        list.clear();
        assertEquals(1, and.all().size());
    }

    @Test void or_anyChildHolds() {
        var or = new SpatialPredicate.Or(List.of(new SpatialPredicate.YInRange(200, 300), new SpatialPredicate.Never()));
        assertFalse(or.test(StubContext.atY(64)));
        assertTrue(new SpatialPredicate.Or(List.of(new SpatialPredicate.Never(), new SpatialPredicate.Always())).test(StubContext.atY(64)));
    }

    @Test void or_childrenExposesAny() {
        var or = new SpatialPredicate.Or(List.of(new SpatialPredicate.Always()));
        assertEquals(1, or.children().size());
    }

    @Test void or_listIsCopied() {
        var list = new java.util.ArrayList<SpatialPredicate>();
        list.add(new SpatialPredicate.YInRange(0, 10));
        var or = new SpatialPredicate.Or(list);
        list.clear();
        assertEquals(1, or.any().size());
    }

    @Test void not_negatesInner_andExposesChild() {
        var inner = new SpatialPredicate.YInRange(10, 50);
        var not = new SpatialPredicate.Not(inner);
        assertFalse(not.test(StubContext.atY(20)));
        assertTrue(not.test(StubContext.atY(100)));
        assertSame(inner, not.inner());
        assertEquals(List.of(inner), not.children());
    }

    @Test void leaf_hasNoChildren() {
        assertTrue(new SpatialPredicate.YInRange(0, 1).children().isEmpty());
        assertTrue(SpatialPredicate.Always.INSTANCE.children().isEmpty());
    }
}
