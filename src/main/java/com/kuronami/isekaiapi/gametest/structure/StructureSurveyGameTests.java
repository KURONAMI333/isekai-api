package com.kuronami.isekaiapi.gametest.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.command.sub.SurveyCommand;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.MirrorMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.RotationMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.Variant;
import com.kuronami.isekaiapi.survey.StructureSurvey;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code /isekai survey} — the tool an author uses when a structure "never spawns".
 *
 * <p>The two properties a survey is worthless without: the numbers have to be right (every candidate
 * chunk is either placed or counted against exactly one reason), and the run has to be harmless (it
 * must not place anything or load chunks just to answer).
 *
 * <p>Each test drives {@link StructureSurvey#run} directly with a structure set built here, because
 * a test cannot add registry entries. A placement of {@code spacing 1, separation 0} makes every
 * chunk of the surveyed square a candidate, so the expected counts are exactly the square's size and
 * a miscount cannot hide behind sparse sampling.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class StructureSurveyGameTests {

    /** Where the survey floor template is assembled. */
    private static final int SCENE_Y = 100;

    /** Above the test world's surface (y=4), so nothing a survey would place lands on the ground. */
    private static final int VERTICAL_OFFSET = 8;

    private StructureSurveyGameTests() {}

    /**
     * On flat dry ground both gates pass for every chunk, so every candidate is placed: the survey
     * has to count the square, the candidates, and the placements, and must not write anything.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void surveyCountsCandidatesAndPlacements(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation dot = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "survey_dot");
        captureDot(level, 9000, 5200, dot);

        Structure structure = structure(level, List.of(Biomes.PLAINS), dot, 2);
        ChunkPos center = new ChunkPos(9000 >> 4, 5200 >> 4);
        StructureSurvey.Result result = StructureSurvey.run(level, structure,
                "isekai_api_test:survey_dot", List.of(set(structure, 1234)), center, 2);

        int expected = 25;
        if (result.scannedChunks() != expected || result.candidateChunks() != expected
                || result.placed() != expected) {
            helper.fail("expected " + expected + " scanned/candidate/placed, got "
                    + result.scannedChunks() + "/" + result.candidateChunks() + "/" + result.placed());
            return;
        }
        if (!result.rejections().isEmpty()) {
            helper.fail("flat dry ground rejected chunks: " + result.rejections());
            return;
        }
        if (result.placeRate() != 1.0 || result.candidateRate() != 1.0) {
            helper.fail("rates should be 1.0 with spacing 1 and no rejections: candidate="
                    + result.candidateRate() + " place=" + result.placeRate());
            return;
        }
        if (!result.dimension().equals("minecraft:overworld")) {
            helper.fail("dimension reported as " + result.dimension());
            return;
        }
        if (result.samples().size() != StructureSurvey.SAMPLE_LIMIT) {
            helper.fail("expected " + StructureSurvey.SAMPLE_LIMIT + " samples, got "
                    + result.samples().size());
            return;
        }

        JsonObject json = JsonParser.parseString(result.toJsonString()).getAsJsonObject();
        expectInt(helper, json, "scanned_chunks", expected);
        expectInt(helper, json, "candidate_chunks", expected);
        expectInt(helper, json, "placed", expected);
        if (json.getAsJsonArray("sample_placements").size() != StructureSurvey.SAMPLE_LIMIT) {
            helper.fail("json kept " + json.getAsJsonArray("sample_placements").size()
                    + " sample placements, expected " + StructureSurvey.SAMPLE_LIMIT);
            return;
        }
        if (json.has("rejections") && json.getAsJsonObject("rejections").size() != 0) {
            helper.fail("json lists rejections that did not happen: " + json.get("rejections"));
            return;
        }
        if (!json.get("structure").getAsString().equals("isekai_api_test:survey_dot")) {
            helper.fail("json structure id is " + json.get("structure"));
            return;
        }

        // The survey only computes starts. Nothing may have been placed at the height the fence
        // would sit at (surface 4 + vertical_offset 8).
        BlockPos probe = new BlockPos(center.getMiddleBlockX(), 4 + VERTICAL_OFFSET,
                center.getMiddleBlockZ());
        if (!level.getBlockState(probe).isAir()) {
            helper.fail("the survey placed a block at " + probe + ": "
                    + level.getBlockState(probe));
            return;
        }
        helper.succeed();
    }

    /**
     * A structure whose {@code biomes} do not include the test world's plains is rejected for the
     * biome alone: the gates pass on flat ground, so the reason has to be {@code biome} and not
     * {@code declined}.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void surveyNamesTheBiomeRejection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation dot = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "survey_biome_dot");
        captureDot(level, 9100, 5200, dot);

        // Nether biomes in an overworld plains world: no candidate can ever match.
        Structure structure = structure(level, List.of(Biomes.NETHER_WASTES), dot, 2);
        StructureSurvey.Result result = StructureSurvey.run(level, structure,
                "isekai_api_test:survey_biome_dot", List.of(set(structure, 1235)),
                new ChunkPos(9100 >> 4, 5200 >> 4), 1);

        if (result.placed() != 0) {
            helper.fail("placed " + result.placed() + " structures whose biomes cannot match");
            return;
        }
        if (!result.rejections().equals(Map.of("biome", 9))) {
            helper.fail("expected every candidate rejected as biome, got " + result.rejections());
            return;
        }
        JsonObject json = JsonParser.parseString(result.toJsonString()).getAsJsonObject();
        if (json.getAsJsonObject("rejections").get("biome").getAsInt() != 9) {
            helper.fail("json biome count is " + json.getAsJsonObject("rejections"));
            return;
        }
        helper.succeed();
    }

    /**
     * A gate, not the biome, is named when the terrain is the reason. The flat test world clears any
     * {@code clearance_above_fluid} the codec allows, so this runs in the End, where the sea level
     * sits far above the empty columns: nothing can clear 64 blocks over it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void surveyNamesTheGateRejection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation dot = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "survey_gate_dot");
        captureDot(level, 9200, 5200, dot);

        ServerLevel end = level.getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the End level is not loaded; the gate check needs a dimension the flat "
                    + "world's dry ground cannot provide");
            return;
        }
        Structure structure = structure(end, List.of(Biomes.THE_END), dot, 64);
        StructureSurvey.Result result = StructureSurvey.run(end, structure,
                "isekai_api_test:survey_gate_dot", List.of(set(structure, 1236)),
                new ChunkPos(2000, 2000), 1);

        if (result.placed() != 0) {
            helper.fail("placed " + result.placed() + " structures demanding 64 blocks of clearance");
            return;
        }
        Integer clearance = result.rejections().get("clearance_above_fluid");
        if (clearance == null || clearance != 9 || result.rejections().size() != 1) {
            helper.fail("expected 9 clearance_above_fluid rejections, got " + result.rejections());
            return;
        }
        if (!result.dimension().equals("minecraft:the_end")) {
            helper.fail("dimension reported as " + result.dimension());
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * The structure survey scans its whole square on the server thread in one command call, so the
     * radius a player can type is bounded: 512 was 1,050,625 chunks, 128 is 66,049.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void surveyRadiusIsBoundedAtTheCommand(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();

        if (SurveyCommand.MAX_RADIUS != 128) {
            helper.fail("the structure survey bound is " + SurveyCommand.MAX_RADIUS
                    + ", not the documented 128");
            return;
        }
        var atMax = dispatcher.parse("isekai survey minecraft:village_plains 128", source);
        if (!atMax.getExceptions().isEmpty() || atMax.getReader().canRead()) {
            helper.fail("radius " + SurveyCommand.MAX_RADIUS + " did not parse: "
                    + atMax.getExceptions());
            return;
        }
        var past = dispatcher.parse("isekai survey minecraft:village_plains 129", source);
        if (past.getExceptions().isEmpty() && !past.getReader().canRead()) {
            helper.fail("radius " + (SurveyCommand.MAX_RADIUS + 1) + " parsed; the bound is not"
                    + " applied to the command");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    private static void expectInt(GameTestHelper helper, JsonObject json, String key, int expected) {
        String raw = String.valueOf(json.get(key));
        if (raw.equals("null") || json.get(key).getAsInt() != expected) {
            helper.fail("json " + key + " should be " + expected + " but is " + raw);
        }
    }

    /** A structure set that offers every chunk as a candidate, so counts equal the square. */
    private static StructureSet set(Structure structure, int salt) {
        return new StructureSet(net.minecraft.core.Holder.direct(structure),
                new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, salt));
    }

    private static Structure structure(ServerLevel level, List<net.minecraft.resources.ResourceKey<Biome>> biomes,
                                        ResourceLocation template, int clearance) {
        HolderSet<Biome> holders = HolderSet.direct(biomes.stream()
                .map(key -> level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key))
                .toList());
        Structure.StructureSettings settings = new Structure.StructureSettings(holders, Map.of(),
                GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        return new GroundedTemplateStructure(settings, List.of(new Variant(template)),
                GroundedTemplateStructure.NO_PROCESSORS, RotationMode.NONE, MirrorMode.NONE,
                clearance, 4, VERTICAL_OFFSET);
    }

    /** Writes one stone block, captures it as a 1x1x1 template, and wipes the scene. */
    private static void captureDot(ServerLevel level, int x, int z, ResourceLocation id) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos pos = new BlockPos(x, SCENE_Y, z);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        StructureTemplate template = level.getServer().getStructureManager().getOrCreate(id);
        template.fillFromWorld(level, pos, new net.minecraft.core.Vec3i(1, 1, 1), false, null);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
