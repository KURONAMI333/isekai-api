package com.kuronami.isekaiapi.tree;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's neutral tree-shape primitives into the vanilla
 * {@code TRUNK_PLACER_TYPE} / {@code FOLIAGE_PLACER_TYPE} registries, so datapacks reference
 * them from a standard {@code minecraft:tree} configured_feature via {@code "type":
 * "isekai_api:<name>"}. Trees stay datapack-authorable and interoperable with vanilla; blocks
 * remain {@code BlockStateProvider} slots, so the primitives carry no baked species or biome.
 *
 * <p>The placer = SHAPE only. Density (forest vs sparse) is a placement concern (count /
 * rarity / Isekai's spatial conditions), kept separate from shape.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:leaning} — a curving/leaning trunk (palm, wind-bent), see
 *       {@link LeaningTrunkPlacer}.</li>
 *   <li>{@code isekai_api:sphere} — an ellipsoid leaf crown with edge jitter, see
 *       {@link SphereFoliagePlacer}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiTreePlacers {

    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(BuiltInRegistries.TRUNK_PLACER_TYPE, IsekaiApi.MODID);
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACERS =
            DeferredRegister.create(BuiltInRegistries.FOLIAGE_PLACER_TYPE, IsekaiApi.MODID);

    public static final Supplier<TrunkPlacerType<LeaningTrunkPlacer>> LEANING =
            TRUNK_PLACERS.register("leaning", () -> new TrunkPlacerType<>(LeaningTrunkPlacer.CODEC));
    public static final Supplier<TrunkPlacerType<BranchingTrunkPlacer>> BRANCHING =
            TRUNK_PLACERS.register("branching", () -> new TrunkPlacerType<>(BranchingTrunkPlacer.CODEC));
    public static final Supplier<FoliagePlacerType<SphereFoliagePlacer>> SPHERE =
            FOLIAGE_PLACERS.register("sphere", () -> new FoliagePlacerType<>(SphereFoliagePlacer.CODEC));
    public static final Supplier<FoliagePlacerType<FanFoliagePlacer>> FAN =
            FOLIAGE_PLACERS.register("fan", () -> new FoliagePlacerType<>(FanFoliagePlacer.CODEC));
    public static final Supplier<FoliagePlacerType<ConeFoliagePlacer>> CONE =
            FOLIAGE_PLACERS.register("cone", () -> new FoliagePlacerType<>(ConeFoliagePlacer.CODEC));
    public static final Supplier<FoliagePlacerType<DiscFoliagePlacer>> DISC =
            FOLIAGE_PLACERS.register("disc", () -> new FoliagePlacerType<>(DiscFoliagePlacer.CODEC));
    public static final Supplier<FoliagePlacerType<WeepingFoliagePlacer>> WEEPING =
            FOLIAGE_PLACERS.register("weeping", () -> new FoliagePlacerType<>(WeepingFoliagePlacer.CODEC));

    private IsekaiTreePlacers() {}

    public static void register(IEventBus modBus) {
        TRUNK_PLACERS.register(modBus);
        FOLIAGE_PLACERS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] tree placers registered: leaning, branching (trunk); sphere, fan, cone, disc, weeping (foliage)");
    }
}
