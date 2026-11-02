package com.kuronami.isekaiapi.survey;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kuronami.isekaiapi.structure.Footprint;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure;
import com.kuronami.isekaiapi.structure.GroundedTemplateStructure.Variant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai fit}: the ground under one chunk, next to the numbers the gates will compare it to.
 *
 * <p>A rejected structure tells you nothing on its own — the same terrain is fine for one datapack
 * and too steep for another. This reads the heightmap the gates read, prints it as a 3x3 grid around
 * the chunk middle, and puts the gate's own numbers beside it: what the lowest sampled column has to
 * clear, how much spread {@code max_slope} allows, and where {@code vertical_offset} puts the
 * template's y=0.
 *
 * <p>The grid is a reading aid. The gates sample five of the nine columns — the middle and the four
 * corners — and this report says so rather than letting an author tune against columns the
 * structure never looks at.
 *
 * <p>Nothing is placed and no chunk is generated: like {@link StructureSurvey} this only reads the
 * noise sampler's heights.
 */
@ApiStatus.Internal
public final class FootprintCheck {

    /** The verdict values a report can carry. */
    public static final String OK = "ok";
    public static final String NO_GATES = "no_footprint_gates";

    /** One column of the 3x3 grid, offset from the chunk middle in blocks. */
    public record Column(int offsetX, int offsetZ, int height) {}

    /** Where one template variant would sit vertically in this chunk. */
    public record VariantFit(ResourceLocation template, int weight, Vec3i size, int yZero,
                             int bottomY, int topY) {}

    /**
     * The measurement for one chunk. Gate fields are {@code null} for a structure without footprint
     * gates; {@code verdict} then says {@link #NO_GATES}.
     */
    public record Result(
            String structureId,
            String dimension,
            int chunkX,
            int chunkZ,
            int seaLevel,
            Integer clearanceAboveFluid,
            Integer maxSlope,
            Integer verticalOffset,
            Footprint footprint,
            List<Column> grid,
            String verdict,
            List<VariantFit> variants,
            boolean candidate,
            List<String> structureSets
    ) {

        public Result {
            grid = List.copyOf(grid);
            variants = List.copyOf(variants);
            structureSets = List.copyOf(structureSets);
        }

        /** The height the lowest sampled column has to exceed ({@code sea level + clearance}). */
        public int clearanceFloor() {
            return seaLevel + (clearanceAboveFluid == null ? 0 : clearanceAboveFluid);
        }

        public boolean clearancePass() {
            return clearanceAboveFluid != null && footprint.min() > clearanceFloor();
        }

        public boolean slopePass() {
            return maxSlope != null && footprint.spread() <= maxSlope;
        }

        public JsonObject toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("structure", structureId);
            root.addProperty("dimension", dimension);
            root.addProperty("chunk_x", chunkX);
            root.addProperty("chunk_z", chunkZ);
            root.addProperty("sea_level", seaLevel);
            if (clearanceAboveFluid != null) {
                root.addProperty("clearance_above_fluid", clearanceAboveFluid);
                root.addProperty("max_slope", maxSlope);
                root.addProperty("vertical_offset", verticalOffset);
            }

            JsonObject footprintJson = new JsonObject();
            footprintJson.addProperty("center", footprint.center());
            footprintJson.addProperty("north_west", footprint.northWest());
            footprintJson.addProperty("north_east", footprint.northEast());
            footprintJson.addProperty("south_west", footprint.southWest());
            footprintJson.addProperty("south_east", footprint.southEast());
            footprintJson.addProperty("min", footprint.min());
            footprintJson.addProperty("max", footprint.max());
            footprintJson.addProperty("spread", footprint.spread());
            root.add("footprint", footprintJson);

            JsonArray gridJson = new JsonArray();
            for (Column column : grid) {
                JsonObject entry = new JsonObject();
                entry.addProperty("offset_x", column.offsetX());
                entry.addProperty("offset_z", column.offsetZ());
                entry.addProperty("height", column.height());
                gridJson.add(entry);
            }
            root.add("surface_grid", gridJson);

            root.addProperty("verdict", verdict);
            root.addProperty("candidate_of_structure_set", candidate);
            JsonArray sets = new JsonArray();
            structureSets.forEach(sets::add);
            root.add("structure_sets", sets);

            JsonArray variantJson = new JsonArray();
            for (VariantFit fit : variants) {
                JsonObject entry = new JsonObject();
                entry.addProperty("template", fit.template().toString());
                entry.addProperty("weight", fit.weight());
                entry.addProperty("size_x", fit.size().getX());
                entry.addProperty("size_y", fit.size().getY());
                entry.addProperty("size_z", fit.size().getZ());
                entry.addProperty("y_zero", fit.yZero());
                entry.addProperty("bottom_y", fit.bottomY());
                entry.addProperty("top_y", fit.topY());
                variantJson.add(entry);
            }
            root.add("templates", variantJson);
            return root;
        }

        public String toJsonString() {
            return new GsonBuilder().setPrettyPrinting().create().toJson(toJson());
        }

        public List<String> summary() {
            List<String> lines = new ArrayList<>();
            lines.add("fit " + structureId + " at chunk " + chunkX + "," + chunkZ + " in " + dimension);
            lines.add("  sea level " + seaLevel
                    + (clearanceAboveFluid == null ? "" : ", clearance_above_fluid " + clearanceAboveFluid
                    + ", max_slope " + maxSlope + ", vertical_offset " + verticalOffset));
            lines.add("  surface heights, 3x3 (rows north to south, columns west to east; the gates"
                    + " read the middle and the four corners):");
            for (int row = 0; row < 3; row++) {
                StringBuilder line = new StringBuilder("   ");
                for (int column = 0; column < 3; column++) {
                    line.append(String.format(java.util.Locale.ROOT, "%6d", grid.get(row * 3 + column).height()));
                }
                lines.add(line.toString());
            }
            if (clearanceAboveFluid == null) {
                lines.add("  verdict: " + verdict + " — this structure type has no footprint gates, so"
                        + " only the terrain is reported");
            } else {
                lines.add("  gate columns: min " + footprint.min() + ", max " + footprint.max()
                        + ", spread " + footprint.spread());
                lines.add("  verdict: " + verdict
                        + " — lowest column " + footprint.min()
                        + (clearancePass() ? " > " : " <= ")
                        + "clearance floor " + clearanceFloor()
                        + "; spread " + footprint.spread()
                        + (slopePass() ? " <= " : " > ") + "max_slope " + maxSlope);
            }
            for (VariantFit fit : variants) {
                boolean empty = fit.size().equals(Vec3i.ZERO);
                lines.add("  template " + fit.template() + " (" + fit.size().getX() + "x"
                        + fit.size().getY() + "x" + fit.size().getZ() + ", weight " + fit.weight()
                        + "): " + (empty ? "no blocks (no NBT)"
                        : "y=0 at " + fit.yZero() + ", blocks y " + fit.bottomY() + ".."
                        + fit.topY()));
                if (empty) {
                    lines.add("    warning: " + fit.template() + " has no NBT of that name, so both the"
                            + " gates and this footprint collapse to the chunk middle — check that the"
                            + " file exists at data/<namespace>/structure/<path>.nbt");
                }
            }
            if (!candidate) {
                lines.add("  note: no structure set offers this chunk"
                        + (structureSets.isEmpty() ? " (the structure is in no set)" : "")
                        + " — an empty chunk here is the placement's answer, not the gates'");
            }
            return lines;
        }
    }

    private FootprintCheck() {}

    /** Inspects one chunk of a structure found in the level's registry. */
    public static Result run(ServerLevel level, ResourceKey<Structure> key, ChunkPos chunkPos) {
        Structure structure = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .get(key)
                .map(holder -> holder.value())
                .orElseThrow(() -> new IllegalArgumentException("unknown structure: " + key.location()));
        return run(level, structure, key.location().toString(), StructureSurvey.setsFor(level, key),
                chunkPos);
    }

    /** Inspects one chunk against an explicit list of sets; a test has no registry entry to use. */
    public static Result run(ServerLevel level, Structure structure, String structureId,
                             List<StructureSet> sets, ChunkPos chunkPos) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        boolean grounded = structure instanceof GroundedTemplateStructure;
        GroundedTemplateStructure template = grounded ? (GroundedTemplateStructure) structure : null;

        // The footprint the gates would use. Without variants there is no size to judge, so the
        // chunk middle alone stands in and the report says the structure has no gates.
        List<Variant> variants = template == null ? List.of() : template.templates();
        Vec3i size = variants.isEmpty()
                ? new Vec3i(1, 1, 1)
                : level.getServer().getStructureManager().getOrCreate(variants.get(0).template())
                        .getSize(template.rotation().sizingRotation());

        Footprint footprint = Footprint.sample(generator, level, randomState, chunkPos, size);
        List<Column> grid = new ArrayList<>(9);
        for (int row = -1; row <= 1; row++) {
            for (int column = -1; column <= 1; column++) {
                int offsetX = column * footprint.halfX();
                int offsetZ = row * footprint.halfZ();
                grid.add(new Column(offsetX, offsetZ, Footprint.height(generator, level, randomState,
                        footprint.centerX() + offsetX, footprint.centerZ() + offsetZ)));
            }
        }

        List<VariantFit> fits = new ArrayList<>();
        if (template != null) {
            for (Variant variant : template.templates()) {
                StructureTemplate structureTemplate = level.getServer().getStructureManager()
                        .getOrCreate(variant.template());
                Vec3i variantSize = structureTemplate.getSize();
                int yZero = footprint.center() + template.verticalOffset();
                // Turning about Y and mirroring leave the vertical extent alone, so the variant's
                // unrotated height is the height the piece will occupy.
                fits.add(new VariantFit(variant.template(), variant.weight(), variantSize, yZero,
                        yZero, yZero + variantSize.getY() - 1));
            }
        }

        String verdict;
        if (template == null) {
            verdict = NO_GATES;
        } else if (!(footprint.min() > level.getChunkSource().getGenerator().getSeaLevel()
                + template.clearanceAboveFluid())) {
            verdict = "clearance_above_fluid";
        } else if (footprint.spread() > template.maxSlope()) {
            verdict = "max_slope";
        } else {
            verdict = OK;
        }

        return new Result(structureId, level.dimension().location().toString(), chunkPos.x, chunkPos.z,
                generator.getSeaLevel(),
                template == null ? null : template.clearanceAboveFluid(),
                template == null ? null : template.maxSlope(),
                template == null ? null : template.verticalOffset(),
                footprint, grid, verdict, fits,
                StructureSurvey.isCandidate(level, sets, chunkPos),
                StructureSurvey.setNames(level, sets));
    }
}
