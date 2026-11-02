package com.kuronami.isekaiapi.gametest.wood;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.TreeGrowers;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Two wood sets declared exactly the way a consumer mod would, in a foreign {@code isekai_api_test}
 * namespace.
 *
 * <p>One set selects every part the builder offers; the other selects only a log and planks, so the
 * gametests can tell "registered because it was asked for" apart from "registered anyway". Nothing
 * is registered unless a GameTest server is running ({@link GameTestHooks#isGametestServer()}) or a
 * data run asks for the fixtures with {@code -Disekai.datagen.testwood=true}, and the whole
 * {@code gametest} tree is excluded from the published jar, so Isekai itself ships no species.
 *
 * <p>The data run generates the fixtures' data files into a scratch directory, which is how the
 * generators are checked against vanilla's oak files without shipping a test species.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class WoodSetTestFixtures {

    /** Foreign namespace, standing in for a consumer mod id. */
    public static final String TEST_MODID = "isekai_api_test";

    /** Set by the {@code data} run in {@code build.gradle}; never present in a shipped mod. */
    public static final String DATAGEN_PROPERTY = "isekai.datagen.testwood";

    /** The id the full fixture wood set is built with; the grower name comes from it. */
    private static final ResourceLocation FULL_ID =
            ResourceLocation.fromNamespaceAndPath(TEST_MODID, "test_wood");

    private static final ResourceKey<ConfiguredFeature<?, ?>> OAK_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("oak"));

    /** The 2x2 shape, so a mega-sapling growth is distinguishable from a single one. */
    private static final ResourceKey<ConfiguredFeature<?, ?>> MEGA_TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace("dark_oak"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TEST_MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TEST_MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TEST_MODID);

    /** Every part selected. Assigned only for a GameTest server or a data run. */
    public static WoodSet full;

    /** Log and planks only. Assigned only for a GameTest server or a data run. */
    public static WoodSet sparse;

    private WoodSetTestFixtures() {}

    /** Whether the fixtures exist in this process. */
    private static boolean enabled() {
        return GameTestHooks.isGametestServer() || Boolean.getBoolean(DATAGEN_PROPERTY);
    }

    /**
     * Declares the sets once the mod buses exist but before any registry event, which is where a
     * consumer would do it from its own mod constructor.
     */
    @SubscribeEvent
    static void onConstruct(FMLConstructModEvent event) {
        if (!enabled()) {
            return;
        }
        IEventBus bus = ModList.get().getModContainerById(IsekaiApi.MODID)
                .orElseThrow(() -> new IllegalStateException("isekai_api container missing"))
                .getEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);

        full = WoodSet
                .builder(FULL_ID)
                .log().wood().strippedLog().strippedWood()
                .family()
                .signs()
                .leaves()
                .sapling(TreeGrowers.builder(FULL_ID).tree(OAK_TREE).megaTree(MEGA_TREE).build())
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

    /**
     * Generates the fixtures' data files during a data run, using the public API exactly as a
     * consumer would.
     */
    @SubscribeEvent
    static void gatherData(GatherDataEvent event) {
        if (!Boolean.getBoolean(DATAGEN_PROPERTY)) {
            return;
        }
        WoodSetDataGen wood = WoodSetDataGen.create(event);
        wood.add(full, new WoodSetDataGen.Overrides()
                .displayName("Test Wood", "テストウッド"));
        wood.add(sparse, new WoodSetDataGen.Overrides()
                .displayName("Sparse Wood", "スパースウッド"));
    }
}
