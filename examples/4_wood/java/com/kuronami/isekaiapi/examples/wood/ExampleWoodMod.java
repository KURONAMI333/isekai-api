package com.kuronami.isekaiapi.examples.wood;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.TreeGrowers;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A complete one-species mod, in one class and one data pack.
 *
 * <p>In a real mod this is what belongs in your {@code @Mod} constructor: three
 * {@code DeferredRegister}s, one {@link WoodSet} builder chain, and a
 * {@link GatherDataEvent} listener that hands the set to {@link WoodSetDataGen}. Everything else —
 * blocks, items, the two boat entities, wood type, block set type, axe stripping, sign block
 * entities, flower-pot acceptance, boat rendering — is installed by Isekai from that chain.
 *
 * <p>The example lives inside this repository so it is compiled and exercised by the GameTest
 * server, and it is excluded from the published jar: an example is documentation, not a library
 * part. Its species only exists while a GameTest server or the {@code dataExample} run is active, so
 * a normal development client is unaffected.
 *
 * <p>The tree the sapling grows is a data pack entry of this example
 * ({@code data/isekai_example/worldgen/configured_feature/example_tree.json}), which is how a real
 * mod ships its own tree shape: a decorator, a trunk placer and a foliage placer written as JSON.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ExampleWoodMod {

    /** The namespace of everything this example registers. */
    public static final String MOD_ID = "isekai_example";

    /** Set by the {@code dataExample} run in {@code build.gradle} so the data pack can regenerate. */
    private static final String DATAGEN_PROPERTY = "isekai.datagen.example";

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "example");

    private static final ResourceKey<ConfiguredFeature<?, ?>> EXAMPLE_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(MOD_ID, "example_tree"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    /** The example species, or {@code null} when this process does not install it. */
    @Nullable
    public static WoodSet example;

    private ExampleWoodMod() {}

    /** Whether this process should install the example at all. */
    private static boolean enabled() {
        return GameTestHooks.isGametestServer() || Boolean.getBoolean(DATAGEN_PROPERTY);
    }

    /** Declares the species, exactly as a mod constructor would. */
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

        example = WoodSet
                .builder(ID)
                .log().wood().strippedLog().strippedWood()
                .family()
                .signs()
                .leaves()
                .sapling(TreeGrowers.builder(ID).tree(EXAMPLE_TREE).build())
                .pottedSapling()
                .boats()
                .mapColor(MapColor.WOOD)
                .barkColor(MapColor.COLOR_BROWN)
                .register(BLOCKS, ITEMS, ENTITIES);
    }

    /**
     * Adds {@code example_species} as an always-on pack, so the JSON this species needs (loot
     * tables, recipes, tags, models, names, the tree's configured feature) is loaded next to the
     * code. The event fires once per pack type and
     * {@link AddPackFindersEvent#addPackFinders} only registers for the matching one, so passing
     * {@link AddPackFindersEvent#getPackType()} registers both halves of the single directory.
     *
     * <p>The pack's location is {@code isekai_api:example_species} because NeoForge resolves it
     * through the owning mod's files; the species itself is registered under {@code isekai_example}.
     *
     * <p>Only the GameTest server loads it. The {@code dataExample} run writes the pack and must
     * not read it back while writing, and a normal client has no reason to carry an example
     * species around.
     */
    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        if (!GameTestHooks.isGametestServer()) {
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "example_species"),
                event.getPackType(),
                Component.literal("Isekai wood-set example"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }

    /** Writes the species' data files; {@code _tools/gw <cell> dataExample} runs this. */
    @SubscribeEvent
    static void gatherData(GatherDataEvent event) {
        if (example == null || !Boolean.getBoolean(DATAGEN_PROPERTY)) {
            return;
        }
        WoodSetDataGen.create(event).add(example, new WoodSetDataGen.Overrides()
                .displayName("Example Wood", "例の木"));
    }
}
