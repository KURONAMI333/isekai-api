package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.Scope;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural lock for the multi-scope single-layer registry. Selectable world types share a
 * dimension key (e.g. {@code minecraft:overworld}) while shipping a different
 * {@code dimension_type}; two worldshapes for those worlds must coexist instead of one
 * replacing the other, and the active one must follow the level's type.
 *
 * <p>The dimension type is injected through the package-private
 * {@link IsekaiRemapImpl#getActiveDescriptor(ResourceKey, java.util.function.Function)}
 * overload, so these tests need no game snapshot.
 */
class IsekaiRemapImplMultiDescriptorTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<DimensionType> SKY = dimensionType("sky_world", "sky");
    private static final ResourceKey<DimensionType> FROZEN = dimensionType("frozen_world", "frozen");
    private static final ResourceKey<DimensionType> VANILLA = dimensionType("minecraft", "overworld");

    private static ResourceKey<DimensionType> dimensionType(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION_TYPE,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static WorldshapeDescriptor descriptor(Scope scope, int priority) {
        return WorldshapeDescriptor.builder()
                .scope(scope)
                .playableRange(new VerticalRange(80, 200, HeightDistribution.UNIFORM))
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Linear())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .priority(priority)
                .build();
    }

    private static Optional<WorldshapeDescriptor> active(IsekaiRemapImpl remap,
                                                         ResourceKey<DimensionType> levelType) {
        return remap.getActiveDescriptor(OVERWORLD, dimension -> levelType);
    }

    @Test
    void dimensionTypePicksTheMatchingScope() {
        WorldshapeDescriptor sky = descriptor(Scope.of(OVERWORLD, SKY), 100);
        WorldshapeDescriptor frozen = descriptor(Scope.of(OVERWORLD, FROZEN), 100);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(sky);
        remap.declareWorldshape(frozen);

        assertSame(sky, active(remap, SKY).orElseThrow(),
                "sky_world:sky must resolve to the Sky World shape, not the later declaration");
        assertSame(frozen, active(remap, FROZEN).orElseThrow(),
                "frozen_world:frozen must resolve to the Tundra shape");
        assertTrue(active(remap, VANILLA).isEmpty(),
                "neither restricted descriptor was written for vanilla's overworld type");
    }

    @Test
    void restrictedBeatsUnrestrictedForAMatchingType() {
        WorldshapeDescriptor unrestricted = descriptor(Scope.of(OVERWORLD), 100);
        WorldshapeDescriptor sky = descriptor(Scope.of(OVERWORLD, SKY), 1);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(unrestricted);
        remap.declareWorldshape(sky);

        assertSame(sky, active(remap, SKY).orElseThrow(),
                "a restricted match wins even with a lower priority");
        assertSame(unrestricted, active(remap, VANILLA).orElseThrow(),
                "the unrestricted declaration still covers every other world type");
    }

    @Test
    void sameScopeKeepsThePriorityRule() {
        WorldshapeDescriptor high = descriptor(Scope.of(OVERWORLD, SKY), 200);
        WorldshapeDescriptor low = descriptor(Scope.of(OVERWORLD, SKY), 100);
        WorldshapeDescriptor higher = descriptor(Scope.of(OVERWORLD, SKY), 300);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(high);
        remap.declareWorldshape(low);
        assertSame(high, active(remap, SKY).orElseThrow(),
                "a lower priority must not replace the existing same-scope declaration");

        remap.declareWorldshape(higher);
        assertSame(higher, active(remap, SKY).orElseThrow(),
                "an equal-or-higher priority replaces the same-scope declaration");
    }

    @Test
    void twoRestrictedScopesWithUnknownTypeAreAmbiguous() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(descriptor(Scope.of(OVERWORLD, SKY), 100));
        remap.declareWorldshape(descriptor(Scope.of(OVERWORLD, FROZEN), 100));

        assertTrue(remap.getActiveDescriptor(OVERWORLD, dimension -> null).isEmpty(),
                "without a known dimension type, picking either world would be a guess");
    }

    @Test
    void singleRestrictedScopeFailsOpenWhenTypeIsUnknown() {
        WorldshapeDescriptor sky = descriptor(Scope.of(OVERWORLD, SKY), 100);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(sky);

        assertSame(sky, remap.getActiveDescriptor(OVERWORLD, dimension -> null).orElseThrow(),
                "a lone declaration keeps the pre-3.0.0 fail-open behaviour");
    }

    @Test
    void unrestrictedWinsWhenTypeIsUnknown() {
        WorldshapeDescriptor unrestricted = descriptor(Scope.of(OVERWORLD), 100);
        WorldshapeDescriptor sky = descriptor(Scope.of(OVERWORLD, SKY), 100);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(unrestricted);
        remap.declareWorldshape(sky);

        assertSame(unrestricted, remap.getActiveDescriptor(OVERWORLD, dimension -> null).orElseThrow(),
                "when the type is unknown, the declaration valid in every world is the safe pick");
    }

    @Test
    void updateWorldshapeReplacesOnlyTheSameScope() {
        WorldshapeDescriptor sky = descriptor(Scope.of(OVERWORLD, SKY), 100);
        WorldshapeDescriptor frozen = descriptor(Scope.of(OVERWORLD, FROZEN), 100);
        WorldshapeDescriptor skyUpdated = descriptor(Scope.of(OVERWORLD, SKY), 100);

        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(sky);
        remap.declareWorldshape(frozen);
        remap.updateWorldshape(OVERWORLD, skyUpdated);

        assertSame(skyUpdated, active(remap, SKY).orElseThrow());
        assertSame(frozen, active(remap, FROZEN).orElseThrow(),
                "updating one scope must not disturb the other");
    }

    @Test
    void removeWorldshapeDropsEveryScope() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(descriptor(Scope.of(OVERWORLD, SKY), 100));
        remap.declareWorldshape(descriptor(Scope.of(OVERWORLD, FROZEN), 100));
        assertEquals(1, remap.getDeclaredDimensions().size(),
                "two scopes for one dimension key are still one declared dimension");

        remap.removeWorldshape(OVERWORLD);

        assertTrue(active(remap, SKY).isEmpty());
        assertTrue(active(remap, FROZEN).isEmpty());
        assertTrue(remap.getDeclaredDimensions().isEmpty());
    }
}
