package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State-leak regression for {@link IsekaiRemapImpl#clearAll()} (HANDOFF Phase 4). Reproduces
 * the "world A declaration bleeds into world B" leak: declare a Java-side worldshape, clear it
 * the way {@code onServerStopping} does, and assert nothing survives.
 *
 * <p>Uses {@code Level.OVERWORLD} (a static ResourceKey, no registry access) and pure record
 * descriptor fields, so no game bootstrap is required.
 */
class IsekaiRemapImplClearTest {

    private static WorldshapeDescriptor descriptor() {
        return WorldshapeDescriptor.builder()
                .dimension(Level.OVERWORLD)
                .playableRange(new VerticalRange(80, 200, HeightDistribution.UNIFORM))
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .build();
    }

    @Test
    void clearAllRemovesDeclaration() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(descriptor());
        assertTrue(remap.getActiveDescriptor(Level.OVERWORLD).isPresent(),
                "precondition: declaration is present");
        assertEquals(1, remap.getDeclaredDimensions().size());

        remap.clearAll();

        assertFalse(remap.getActiveDescriptor(Level.OVERWORLD).isPresent(),
                "declaration must not survive server stop");
        assertTrue(remap.getDeclaredDimensions().isEmpty());
    }

    @Test
    void clearAllOnEmptyIsNoop() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.clearAll();
        assertTrue(remap.getDeclaredDimensions().isEmpty());
    }
}
