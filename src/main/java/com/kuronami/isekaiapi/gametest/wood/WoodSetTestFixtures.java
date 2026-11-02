package com.kuronami.isekaiapi.gametest.wood;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Two wood sets declared exactly the way a consumer mod would, in a foreign {@code isekai_api_test}
 * namespace.
 *
 * <p>One set selects every part the builder offers; the other selects only a log and planks, so the
 * gametests can tell "registered because it was asked for" apart from "registered anyway". Nothing
 * is registered unless a GameTest server is running ({@link GameTestHooks#isGametestServer()}), and
 * the whole {@code gametest} tree is excluded from the published jar, so Isekai itself ships no
 * species.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WoodSetTestFixtures {

    /** Foreign namespace, standing in for a consumer mod id. */
    public static final String TEST_MODID = "isekai_api_test";

    private static final ResourceKey<ConfiguredFeature<?, ?>> OAK_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("oak"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TEST_MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TEST_MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TEST_MODID);

    /** Every part selected. Assigned only on a GameTest server. */
    public static WoodSet full;

    /** Log and planks only. Assigned only on a GameTest server. */
    public static WoodSet sparse;

    private WoodSetTestFixtures() {}

    /**
     * Declares the sets once the mod buses exist but before any registry event, which is where a
     * consumer would do it from its own mod constructor.
     */
    @SubscribeEvent
    static void onConstruct(FMLConstructModEvent event) {
        if (!GameTestHooks.isGametestServer()) {
            return;
        }
        IEventBus bus = ModList.get().getModContainerById(IsekaiApi.MODID)
                .orElseThrow(() -> new IllegalStateException("isekai_api container missing"))
                .getEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);

        full = WoodSet
                .builder(ResourceLocation.fromNamespaceAndPath(TEST_MODID, "test_wood"))
                .log().wood().strippedLog().strippedWood()
                .family()
                .signs()
                .leaves()
                .sapling(new TreeGrower(TEST_MODID + "/test_wood", Optional.empty(), Optional.of(OAK_TREE), Optional.empty()))
                .pottedSapling()
                .boats()
                .mapColor(MapColor.COLOR_ORANGE)
                .barkColor(MapColor.COLOR_BROWN)
                .register(BLOCKS, ITEMS, ENTITIES);

        sparse = WoodSet
                .builder(ResourceLocation.fromNamespaceAndPath(TEST_MODID, "sparse_wood"))
                .log().planks()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .register(BLOCKS, ITEMS);
    }
}
