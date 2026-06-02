package com.kuronami.isekaiapi.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * A {@link Structure} that places a single hand-authored NBT template on flat, dry ground.
 *
 * <p>It exists because vanilla {@code minecraft:jigsaw} — the normal way to place an NBT
 * set-piece — has no datapack way to require the spot be above water or reasonably level. Its
 * only placement gate is the biome, and a biome assigned by climate (continentalness) does not
 * track the actual waterline or terrain steepness, so a jigsaw landmark spawns half-submerged in
 * shallow sea or tilted across a cliff. This structure adds those two gates back:
 * <ul>
 *   <li><b>clearance_above_fluid</b> — every sampled footprint column must rise at least this
 *       many blocks above the dimension's sea level (rejects water / shoreline spawns).</li>
 *   <li><b>max_slope</b> — the height spread across the footprint's centre and four corners must
 *       not exceed this (rejects steep / cliff spawns).</li>
 * </ul>
 * Placement itself reuses vanilla {@link StructureTemplate} machinery via
 * {@link GroundedTemplatePiece} (proper per-chunk clamping), and the structure honours
 * {@code terrain_adaptation} (e.g. {@code beard_thin}) from its settings for final blending.
 *
 * <p>Neutral: nothing about the template's content is known here — it is an opaque
 * {@link ResourceLocation}. "Grounded template" is geometric (a template anchored to level dry
 * ground). The example use ("an oasis NBT") lives entirely in consumer datapack assets.
 *
 * <p>JSON:
 * <pre>{@code
 * {
 *   "type": "isekai_api:grounded_template",
 *   "biomes": "#<ns>:has_structure/<name>",
 *   "step": "surface_structures",
 *   "spawn_overrides": {},
 *   "terrain_adaptation": "beard_thin",
 *   "template": "<ns>:<name>",          // -> data/<ns>/structure/<name>.nbt
 *   "clearance_above_fluid": 2,
 *   "max_slope": 4,
 *   "vertical_offset": -3                // template y=0 lands at (surface + this)
 * }
 * }</pre>
 * Pair with a {@code structure_set} (random_spread) for density + {@code /locate}.
 */
@ApiStatus.Internal
public final class GroundedTemplateStructure extends Structure {

    public static final MapCodec<GroundedTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.intRange(0, 64).optionalFieldOf("clearance_above_fluid", 2).forGetter(s -> s.clearanceAboveFluid),
            Codec.intRange(0, 128).optionalFieldOf("max_slope", 4).forGetter(s -> s.maxSlope),
            Codec.intRange(-32, 32).optionalFieldOf("vertical_offset", -3).forGetter(s -> s.verticalOffset)
    ).apply(i, GroundedTemplateStructure::new));

    private final ResourceLocation template;
    private final int clearanceAboveFluid;
    private final int maxSlope;
    private final int verticalOffset;

    public GroundedTemplateStructure(StructureSettings settings, ResourceLocation template,
                                     int clearanceAboveFluid, int maxSlope, int verticalOffset) {
        super(settings);
        this.template = template;
        this.clearanceAboveFluid = clearanceAboveFluid;
        this.maxSlope = maxSlope;
        this.verticalOffset = verticalOffset;
    }

    @Override
    public StructureType<?> type() {
        return IsekaiStructures.GROUNDED_TEMPLATE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int seaLevel = ctx.chunkGenerator().getSeaLevel();

        // Footprint extents from the template, so the slope sample matches the real footprint
        // and the template is centred on the chunk centre.
        StructureTemplate tmpl = ctx.structureTemplateManager().getOrCreate(template);
        Vec3i size = tmpl.getSize();
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;

        int hc = surfaceY(ctx, x, z);
        int h1 = surfaceY(ctx, x - halfX, z - halfZ);
        int h2 = surfaceY(ctx, x + halfX, z - halfZ);
        int h3 = surfaceY(ctx, x - halfX, z + halfZ);
        int h4 = surfaceY(ctx, x + halfX, z + halfZ);

        int min = Math.min(hc, Math.min(Math.min(h1, h2), Math.min(h3, h4)));
        int max = Math.max(hc, Math.max(Math.max(h1, h2), Math.max(h3, h4)));

        // Reject water / shoreline: the whole footprint must clear sea level.
        if (min <= seaLevel + clearanceAboveFluid) return Optional.empty();
        // Reject steep terrain.
        if (max - min > maxSlope) return Optional.empty();

        // Centre the template on the chunk centre; anchor y=0 at (centre surface + offset).
        BlockPos templatePos = new BlockPos(x - halfX, hc + verticalOffset, z - halfZ);
        return Optional.of(new GenerationStub(new BlockPos(x, hc, z), builder ->
                builder.addPiece(new GroundedTemplatePiece(ctx.structureTemplateManager(), template, templatePos))));
    }

    private static int surfaceY(GenerationContext ctx, int x, int z) {
        return ctx.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
    }
}
