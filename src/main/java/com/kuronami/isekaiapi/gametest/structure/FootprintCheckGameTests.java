package com.kuronami.isekaiapi.gametest.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.MirrorMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.RotationMode;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.Variant;
import com.kuronami.isekaiapi.survey.FootprintCheck;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
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
 * {@code /isekai fit} — the report has to describe the terrain the gates will judge, in the numbers
 * the gates will compare, or an author tunes against the wrong number.
 *
 * <p>Three cases: a chunk that passes, a chunk that fails on spread, and a chunk that fails on
 * clearance. Each also checks where {@code vertical_offset} puts the template, because "the ground is
 * fine" and "the building fits the ground" are different questions.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class FootprintCheckGameTests {

    /** Where templates are assembled. */
    private static final int SCENE_Y = 100;

    /** Where the template's y=0 would sit relative to the surface (negative = sunk into it). */
    private static final int VERTICAL_OFFSET = -3;

    /** A 5x4x5 template: 4 blocks tall, so the piece spans y=0..3 above its anchor. */
    private static final Vec3i HOUSE_SIZE = new Vec3i(5, 4, 5);

    private FootprintCheckGameTests() {}

    /**
     * On flat ground every sampled column is the same height: the report must show nine equal
     * numbers, a spread of 0, both gates passing, and the template sitting where
     * {@code vertical_offset} puts it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void flatGroundPassesBothGates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation house = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "fit_house");
        capture(level, 9400, 5300, HOUSE_SIZE, house);

        Structure structure = structure(level, List.of(Biomes.PLAINS), house, 2);
        StructureSet set = set(structure, 4711);
        ChunkPos chunkPos = new ChunkPos(9400 >> 4, 5300 >> 4);
        FootprintCheck.Result result = FootprintCheck.run(level, structure,
                "isekai_api_test:fit_house", List.of(set), chunkPos);

        if (result.footprint().spread() != 0) {
            helper.fail("flat ground reported a spread of " + result.footprint().spread()
                    + "; summary:\n" + String.join("\n", result.summary()));
            return;
        }
        int surface = result.footprint().center();
        for (FootprintCheck.Column column : result.grid()) {
            if (column.height() != surface) {
                helper.fail("grid column " + column.offsetX() + "," + column.offsetZ()
                        + " reads " + column.height() + ", expected " + surface);
                return;
            }
        }
        if (!result.verdict().equals(FootprintCheck.OK)) {
            helper.fail("flat ground was judged " + result.verdict() + "; the summary was:\n"
                    + String.join("\n", result.summary()));
            return;
        }
        if (!result.candidate()) {
            helper.fail("a set with spacing 1 should offer this chunk");
            return;
        }
        if (result.variants().size() != 1) {
            helper.fail("expected one variant, got " + result.variants().size());
            return;
        }
        FootprintCheck.VariantFit fit = result.variants().get(0);
        int yZero = surface + VERTICAL_OFFSET;
        if (fit.yZero() != yZero || fit.bottomY() != yZero
                || fit.topY() != yZero + HOUSE_SIZE.getY() - 1) {
            helper.fail("template reported y=0 at " + fit.yZero() + " spanning " + fit.bottomY()
                    + ".." + fit.topY() + ", expected " + yZero + ".."
                    + (yZero + HOUSE_SIZE.getY() - 1));
            return;
        }

        JsonObject json = JsonParser.parseString(result.toJsonString()).getAsJsonObject();
        if (json.getAsJsonArray("surface_grid").size() != 9) {
            helper.fail("json kept " + json.getAsJsonArray("surface_grid").size() + " grid columns");
            return;
        }
        if (!json.get("verdict").getAsString().equals(FootprintCheck.OK)) {
            helper.fail("json verdict is " + json.get("verdict"));
            return;
        }
        if (json.getAsJsonObject("footprint").get("min").getAsInt() != surface) {
            helper.fail("json footprint min is " + json.getAsJsonObject("footprint"));
            return;
        }

        // A template id with no NBT is the most common authoring mistake, and it silently shrinks
        // the footprint to one column; the report has to say so instead of reporting a neat grid.
        Structure missing = structure(level, List.of(Biomes.PLAINS),
                ResourceLocation.fromNamespaceAndPath("isekai_api_test", "fit_missing"), 2);
        FootprintCheck.Result missingResult = FootprintCheck.run(level, missing,
                "isekai_api_test:fit_missing", List.of(set(missing, 4714)), chunkPos);
        if (!missingResult.variants().get(0).size().equals(Vec3i.ZERO)) {
            helper.fail("a template with no NBT reported size "
                    + missingResult.variants().get(0).size());
            return;
        }
        if (!String.join("\n", missingResult.summary()).contains("has no NBT")) {
            helper.fail("the report does not warn about a missing template:\n"
                    + String.join("\n", missingResult.summary()));
            return;
        }
        helper.succeed();
    }

    /**
     * Real terrain that varies inside one footprint is reported as a slope rejection, with the
     * measured spread — the author learns how far off the ground is, not only that the chunk failed.
     *
     * <p>The surface the gates read is the generator's ({@code WORLD_SURFACE_WG}), so blocks placed
     * in the world cannot make a slope: this has to find ground that really is uneven. The flat
     * overworld has none, the Nether reads as its ceiling (uniform 128), and the End's main island
     * does — its rim steps several blocks within a footprint. {@code max_slope: 0} turns the
     * smallest real step into a rejection, so the test does not depend on how uneven the island
     * happens to be, only on it not being perfectly flat.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void unevenGroundIsReportedAsSlope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Its own template id: tests share one saved world, and a missing template would silently
        // collapse the footprint to a single column.
        ResourceLocation house = ResourceLocation.fromNamespaceAndPath("isekai_api_test", "fit_house_slope");
        capture(level, 9300, 5400, HOUSE_SIZE, house);
        ServerLevel end = level.getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the End level is not loaded");
            return;
        }
        Structure structure = structure(end, List.of(Biomes.THE_END), house, 2, 0);
        List<StructureSet> sets = List.of(set(structure, 4712));

        FootprintCheck.Result found = null;
        FootprintCheck.Result best = null;
        for (int cx = -6; cx <= 6 && found == null; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                FootprintCheck.Result candidate = FootprintCheck.run(end, structure,
                        "isekai_api_test:fit_house_slope", sets, new ChunkPos(cx, cz));
                // Clearance is checked first; a column the sea floor would reject cannot show the
                // slope verdict at all.
                if (!candidate.clearancePass()) {
                    continue;
                }
                if (best == null || candidate.footprint().spread() > best.footprint().spread()) {
                    best = candidate;
                }
                if (candidate.footprint().spread() > 0) {
                    found = candidate;
                    break;
                }
            }
        }
        if (found == null) {
            helper.fail("no chunk near the End's main island had uneven ground; the best spread was "
                    + (best == null ? "unreachable" : best.footprint().spread()));
            return;
        }
        if (!found.verdict().equals("max_slope")) {
            helper.fail("verdict is " + found.verdict() + " for a spread of "
                    + found.footprint().spread() + "; summary:\n" + String.join("\n", found.summary()));
            return;
        }
        if (found.slopePass()) {
            helper.fail("slopePass() is true for a spread of " + found.footprint().spread());
            return;
        }
        if (found.footprint().spread() != found.footprint().max() - found.footprint().min()) {
            helper.fail("spread " + found.footprint().spread() + " does not match max-min "
                    + found.footprint().max() + "-" + found.footprint().min());
            return;
        }
        // The summary has to carry the numbers the verdict came from, or it cannot be acted on.
        String grid = String.join("\n", found.summary());
        if (!grid.contains("spread " + found.footprint().spread())
                || !grid.contains("max_slope " + found.maxSlope())) {
            helper.fail("the summary does not show the measured spread and limit:\n" + grid);
            return;
        }
        helper.succeed();
    }

    /**
     * The clearance gate is judged against the dimension's sea level, so the flat overworld cannot
     * show a failure. The End can: its empty columns sit far below the sea level, and the report must
     * name the clearance and print the floor the column failed to exceed.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void emptyEndColumnIsReportedAsClearance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation house = ResourceLocation.fromNamespaceAndPath("isekai_api_test",
                "fit_house_clearance");
        capture(level, 9320, 5400, HOUSE_SIZE, house);
        ServerLevel end = level.getServer().getLevel(Level.END);
        if (end == null) {
            helper.fail("the End level is not loaded");
            return;
        }

        Structure structure = structure(end, List.of(Biomes.THE_END), house, 64);
        ChunkPos chunkPos = new ChunkPos(2000, 2000);
        FootprintCheck.Result result = FootprintCheck.run(end, structure,
                "isekai_api_test:fit_house_clearance", List.of(set(structure, 4713)), chunkPos);

        if (!result.verdict().equals("clearance_above_fluid")) {
            helper.fail("verdict is " + result.verdict() + ", expected clearance_above_fluid;"
                    + " summary:\n" + String.join("\n", result.summary()));
            return;
        }
        if (result.footprint().min() > result.clearanceFloor()) {
            helper.fail("the report says the lowest column " + result.footprint().min()
                    + " clears the floor " + result.clearanceFloor() + " but still failed");
            return;
        }
        if (result.clearanceAboveFluid() != 64) {
            helper.fail("clearance reported as " + result.clearanceAboveFluid());
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    private static StructureSet set(Structure structure, int salt) {
        return new StructureSet(Holder.direct(structure),
                new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, salt));
    }

    private static Structure structure(ServerLevel level,
                                       List<ResourceKey<Biome>> biomes,
                                       ResourceLocation template, int clearance) {
        return structure(level, biomes, template, clearance, 4);
    }

    private static Structure structure(ServerLevel level,
                                       List<ResourceKey<Biome>> biomes,
                                       ResourceLocation template, int clearance, int maxSlope) {
        HolderSet<Biome> holders = HolderSet.direct(biomes.stream()
                .map(key -> level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key))
                .toList());
        Structure.StructureSettings settings = new Structure.StructureSettings(holders, Map.of(),
                GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        return new GroundedTemplateStructure(settings, List.of(new Variant(template)),
                GroundedTemplateStructure.NO_PROCESSORS, RotationMode.NONE, MirrorMode.NONE,
                clearance, maxSlope, VERTICAL_OFFSET);
    }

    /** Builds a stone block, captures it as a template of {@code size}, and wipes the scene. */
    private static void capture(ServerLevel level, int x, int z, Vec3i size, ResourceLocation id) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos origin = new BlockPos(x, SCENE_Y, z);
        for (BlockPos pos : BlockPos.betweenClosed(origin,
                origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        StructureTemplate template = level.getServer().getStructureManager().getOrCreate(id);
        template.fillFromWorld(level, origin, size, false, null);
        for (BlockPos pos : BlockPos.betweenClosed(origin,
                origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
