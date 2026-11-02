package com.kuronami.isekaiapi.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.MirrorMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.RotationMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.Variant;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack contract for {@code isekai_api:grounded_template}: which fields a structure JSON may
 * write and what leaving them out means.
 *
 * <p>The {@code processors} field is the addition checked here. Its default must be "place the NBT
 * as saved", because every datapack written before the field existed has to keep the same output,
 * and an absent field has to resolve to the field's default instance exactly — not to some
 * equivalent list — so the no-processor path is one value, not two. Both ways of naming a list are
 * accepted, matching vanilla {@code single_pool_element}: a resource location resolved through the
 * registry ({@code "minecraft:empty"}) and an inline list
 * ({@code {"processors": [...]}}).
 *
 * <p>Only decoding is checked here. Encoding a structure means writing a biome tag back through a
 * registry, and the generated vanilla lookup a unit test can build is a {@code HolderLookup}, not
 * the {@code RegistryAccess} a server encodes with, so the encoder rejects its own holders
 * ({@code canSerializeIn}). The save direction is covered where a server exists, in the GameTests.
 *
 * <p>What the processors do to blocks in a world is checked in
 * {@code GroundedTemplateGameTests}, not here. The variation fields ({@code template} as a weighted
 * list, {@code rotation}, {@code mirror}) are checked here for their datapack contract, and for
 * their effect on placement in {@code GroundedTemplateVariationGameTests}.
 */
class GroundedTemplateCodecTest {

    /** Enough of a structure JSON to satisfy {@link net.minecraft.world.level.levelgen.structure.Structure.StructureSettings}. */
    private static final String SETTINGS = """
            "biomes": "#minecraft:is_overworld",
            "step": "surface_structures",
            "spawn_overrides": {},
            "terrain_adaptation": "beard_thin",
            "template": "isekai_api_test:grounded_tree",
            "clearance_above_fluid": 2,
            "max_slope": 4,
            "vertical_offset": -3""";

    private static DynamicOps<JsonElement> ops;
    private static Codec<GroundedTemplateStructure> codec;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        // The vanilla registry set, so the biome tag and the processor-list lookups resolve the way
        // they do when a server loads a structure JSON.
        ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
        codec = GroundedTemplateStructure.CODEC.codec();
    }

    private static GroundedTemplateStructure decode(String json) {
        DataResult<GroundedTemplateStructure> result =
                codec.parse(ops, JsonParser.parseString("{" + json + "}"));
        return result.getOrThrow(message -> new AssertionError("structure did not decode: " + message));
    }

    @Test
    void omittingProcessorsMeansPlaceTheTemplateAsSaved() {
        GroundedTemplateStructure structure = decode(SETTINGS);

        // The identity of the holder matters, not just "some empty list": an absent field must land
        // on the field's own default, so the unprocessed path has one representation.
        assertEquals(GroundedTemplateStructure.NO_PROCESSORS, structure.processors(),
                "no processors field must resolve to the field's default");
        assertTrue(structure.processors().value().list().isEmpty());
        // Unrelated fields still read as before, so the addition changed nothing else.
        assertEquals(List.of(new Variant(
                        ResourceLocation.fromNamespaceAndPath("isekai_api_test", "grounded_tree"))),
                structure.templates());
        assertEquals(RotationMode.NONE, structure.rotation());
        assertEquals(MirrorMode.NONE, structure.mirror());
        assertEquals(2, structure.clearanceAboveFluid());
        assertEquals(4, structure.maxSlope());
        assertEquals(-3, structure.verticalOffset());
    }

    @Test
    void processorsAcceptARegistryReference() {
        GroundedTemplateStructure structure = decode(SETTINGS + """
                ,
                "processors": "minecraft:empty\"""");

        assertTrue(structure.processors().value().list().isEmpty());
        assertEquals("minecraft:empty", structure.processors().unwrap().left()
                        .orElseThrow(() -> new AssertionError("an id must decode to a registry reference"))
                        .location().toString(),
                "a referenced list must stay a reference, not be inlined");
    }

    @Test
    void processorsAcceptAnInlineList() {
        GroundedTemplateStructure structure = decode(SETTINGS + """
                ,
                "processors": {"processors": [{"processor_type": "isekai_api:leaf_distances"}]}""");

        assertEquals(1, structure.processors().value().list().size(),
                "an inline list decodes to its entries");
        assertInstanceOf(LeafDistanceProcessor.class, structure.processors().value().list().get(0));
        assertTrue(structure.processors().unwrap().right().isPresent(),
                "an inline list is a direct holder, not a registry reference");
    }

    @Test
    void emptyInlineListPlacesTheTemplateAsSaved() {
        GroundedTemplateStructure structure = decode(SETTINGS + """
                ,
                "processors": {"processors": []}""");

        // A different holder object from the default (Holder.Direct has no value equality), but the
        // same behaviour: nothing processes the template.
        assertTrue(structure.processors().value().list().isEmpty());
    }

    @Test
    void unknownProcessorListFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode(SETTINGS + """
                ,
                "processors": "isekai_api_test:no_such_list\""""));
    }

    @Test
    void unknownProcessorTypeFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode(SETTINGS + """
                ,
                "processors": {"processors": [{"processor_type": "isekai_api_test:no_such_processor"}]}"""));
    }

    @Test
    void templateAcceptsAWeightedList() {
        GroundedTemplateStructure structure = decode(withoutTemplate() + """
                "template": [
                  {"template": "isekai_api_test:palm_oasis", "weight": 3},
                  {"template": "isekai_api_test:palm_oasis_small"}
                ]""");

        assertEquals(List.of(
                        new Variant(ResourceLocation.fromNamespaceAndPath("isekai_api_test", "palm_oasis"), 3),
                        new Variant(ResourceLocation.fromNamespaceAndPath("isekai_api_test", "palm_oasis_small"), 1)),
                structure.templates(),
                "an omitted weight is 1, and the order is the datapack's order");
    }

    @Test
    void anEmptyTemplateListFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode(withoutTemplate() + """
                "template": []"""));
    }

    @Test
    void aZeroWeightFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode(withoutTemplate() + """
                "template": [{"template": "isekai_api_test:palm_oasis", "weight": 0}]"""));
    }

    @Test
    void rotationAndMirrorAcceptEveryMode() {
        GroundedTemplateStructure structure = decode(SETTINGS + """
                ,
                "rotation": "clockwise_180",
                "mirror": "front_back\"""");

        assertEquals(RotationMode.CLOCKWISE_180, structure.rotation());
        assertEquals(MirrorMode.FRONT_BACK, structure.mirror());

        assertEquals(RotationMode.RANDOM, decode(SETTINGS + """
                ,
                "rotation": "random\"""").rotation());
        assertEquals(MirrorMode.RANDOM, decode(SETTINGS + """
                ,
                "mirror": "random\"""").mirror());
    }

    @Test
    void unknownRotationFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode(SETTINGS + """
                ,
                "rotation": "upside_down\""""));
    }

    /**
     * {@link #SETTINGS} with the {@code template} entry removed and the separator kept, so a test
     * can append its own {@code "template"} entry as the last field.
     */
    private static String withoutTemplate() {
        return SETTINGS.replace("\"template\": \"isekai_api_test:grounded_tree\",\n", "") + ",\n";
    }
}
