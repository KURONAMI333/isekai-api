package com.kuronami.isekaiapi.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
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
 *   <li><b>max_slope</b> — the height spread across the footprint's sample grid must not exceed
 *       this (rejects steep / cliff spawns).</li>
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
 *   "processors": "<ns>:<list>",        // optional; empty means "place the NBT as saved"
 *   "rotation": "none",                 // optional; "random" or a fixed quarter turn
 *   "mirror": "none",                   // optional; "random" or a fixed flip
 *   "clearance_above_fluid": 2,
 *   "max_slope": 4,
 *   "vertical_offset": -3                // template y=0 lands at (surface + this)
 * }
 * }</pre>
 *
 * <p>{@code template} also accepts a weighted list to vary which NBT is placed:
 * <pre>{@code
 * "template": [
 *   { "template": "<ns>:palm_oasis", "weight": 3 },
 *   { "template": "<ns>:palm_oasis_small" }
 * ]
 * }</pre>
 * {@code rotation}/{@code mirror} default to {@code none}, which is the orientation every older
 * datapack already got. The footprint the gates sample uses the rotated extents, so a template
 * turned a quarter turn is judged on the ground it will actually cover.
 *
 * <p>Pair with a {@code structure_set} (random_spread) for density + {@code /locate}.
 */
@ApiStatus.Internal
public final class GroundedTemplateStructure extends Structure {

    /** No processors: the template is placed exactly as saved (the historical behaviour). */
    public static final Holder<StructureProcessorList> NO_PROCESSORS =
            Holder.direct(new StructureProcessorList(List.of()));

    /** One template in the {@code template} field, with the weight it is chosen at. */
    public record Variant(ResourceLocation template, int weight) {

        public static final Codec<Variant> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("template").forGetter(Variant::template),
                Codec.intRange(1, 1024).optionalFieldOf("weight", 1).forGetter(Variant::weight)
        ).apply(i, Variant::new));

        public Variant(ResourceLocation template) {
            this(template, 1);
        }
    }

    /**
     * How the piece is turned when it is placed. {@code none} is the default and the only value an
     * older datapack can have, so it must not consume randomness: a structure that never asked for
     * variation keeps producing the same starts for the same seed.
     */
    public enum RotationMode implements StringRepresentable {
        NONE("none", Rotation.NONE),
        CLOCKWISE_90("clockwise_90", Rotation.CLOCKWISE_90),
        CLOCKWISE_180("clockwise_180", Rotation.CLOCKWISE_180),
        COUNTERCLOCKWISE_90("counterclockwise_90", Rotation.COUNTERCLOCKWISE_90),
        RANDOM("random", null);

        public static final Codec<RotationMode> CODEC = StringRepresentable.fromEnum(RotationMode::values);

        private final String name;
        private final Rotation fixed;

        RotationMode(String name, Rotation fixed) {
            this.name = name;
            this.fixed = fixed;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The rotation for one attempt; only {@link #RANDOM} draws from the random source. */
        public Rotation pick(RandomSource random) {
            return fixed != null ? fixed : Rotation.getRandom(random);
        }

        /**
         * The rotation to size a footprint with before any attempt happens: the declared turn, or
         * unturned for {@link #RANDOM} (any turn may be drawn, so no single extent is "the" one).
         */
        public Rotation sizingRotation() {
            return fixed != null ? fixed : Rotation.NONE;
        }
    }

    /** How the piece is mirrored; same contract as {@link RotationMode}. */
    public enum MirrorMode implements StringRepresentable {
        NONE("none", Mirror.NONE),
        LEFT_RIGHT("left_right", Mirror.LEFT_RIGHT),
        FRONT_BACK("front_back", Mirror.FRONT_BACK),
        RANDOM("random", null);

        public static final Codec<MirrorMode> CODEC = StringRepresentable.fromEnum(MirrorMode::values);

        private final String name;
        private final Mirror fixed;

        MirrorMode(String name, Mirror fixed) {
            this.name = name;
            this.fixed = fixed;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The mirror for one attempt; only {@link #RANDOM} draws from the random source. */
        public Mirror pick(RandomSource random) {
            return fixed != null ? fixed : Util.getRandom(Mirror.values(), random);
        }
    }

    /**
     * A bare id and a list of one entry mean the same thing; keeping both directions tidy means a
     * structure that adds a second variant and then drops it writes out the short form again.
     */
    private static final Codec<List<Variant>> TEMPLATES_CODEC = Codec.either(
                    ResourceLocation.CODEC, Variant.CODEC.listOf())
            .xmap(either -> either.map(id -> List.of(new Variant(id)), List::copyOf),
                    variants -> variants.size() == 1 && variants.get(0).weight() == 1
                            ? Either.left(variants.get(0).template())
                            : Either.right(variants))
            .validate(variants -> variants.isEmpty()
                    ? DataResult.error(() -> "template: a list of variants must not be empty")
                    : DataResult.success(variants));

    public static final MapCodec<GroundedTemplateStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            TEMPLATES_CODEC.fieldOf("template").forGetter(s -> s.templates),
            StructureProcessorType.LIST_CODEC.optionalFieldOf("processors", NO_PROCESSORS).forGetter(s -> s.processors),
            RotationMode.CODEC.optionalFieldOf("rotation", RotationMode.NONE).forGetter(s -> s.rotation),
            MirrorMode.CODEC.optionalFieldOf("mirror", MirrorMode.NONE).forGetter(s -> s.mirror),
            Codec.intRange(0, 64).optionalFieldOf("clearance_above_fluid", 2).forGetter(s -> s.clearanceAboveFluid),
            Codec.intRange(0, 128).optionalFieldOf("max_slope", 4).forGetter(s -> s.maxSlope),
            Codec.intRange(-32, 32).optionalFieldOf("vertical_offset", -3).forGetter(s -> s.verticalOffset)
    ).apply(i, GroundedTemplateStructure::new));

    private final List<Variant> templates;
    private final Holder<StructureProcessorList> processors;
    private final RotationMode rotation;
    private final MirrorMode mirror;
    private final int clearanceAboveFluid;
    private final int maxSlope;
    private final int verticalOffset;

    public GroundedTemplateStructure(StructureSettings settings, List<Variant> templates,
                                     Holder<StructureProcessorList> processors,
                                     RotationMode rotation, MirrorMode mirror,
                                     int clearanceAboveFluid, int maxSlope, int verticalOffset) {
        super(settings);
        this.templates = List.copyOf(templates);
        this.processors = processors;
        this.rotation = rotation;
        this.mirror = mirror;
        this.clearanceAboveFluid = clearanceAboveFluid;
        this.maxSlope = maxSlope;
        this.verticalOffset = verticalOffset;
    }

    /** One fixed template, no processors, no variation: the shape every older datapack has. */
    public GroundedTemplateStructure(StructureSettings settings, ResourceLocation template,
                                     Holder<StructureProcessorList> processors,
                                     int clearanceAboveFluid, int maxSlope, int verticalOffset) {
        this(settings, List.of(new Variant(template)), processors, RotationMode.NONE, MirrorMode.NONE,
                clearanceAboveFluid, maxSlope, verticalOffset);
    }

    public List<Variant> templates() {
        return templates;
    }

    public Holder<StructureProcessorList> processors() {
        return processors;
    }

    public RotationMode rotation() {
        return rotation;
    }

    public MirrorMode mirror() {
        return mirror;
    }

    public int clearanceAboveFluid() {
        return clearanceAboveFluid;
    }

    public int maxSlope() {
        return maxSlope;
    }

    public int verticalOffset() {
        return verticalOffset;
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

        Variant variant = pickVariant(ctx.random());
        Rotation rot = rotation.pick(ctx.random());
        Mirror mir = mirror.pick(ctx.random());

        // Footprint extents from the template as it will be turned, so the slope sample matches the
        // ground the piece really covers (a quarter turn swaps the X and Z extents).
        StructureTemplate tmpl = ctx.structureTemplateManager().getOrCreate(variant.template());
        Vec3i size = tmpl.getSize(rot);

        Footprint footprint = Footprint.sample(ctx.chunkGenerator(), ctx.heightAccessor(),
                ctx.randomState(), cp, size);
        int hc = footprint.center();

        // Reject water / shoreline: the whole footprint must clear sea level.
        if (footprint.min() <= seaLevel + clearanceAboveFluid) {
            StructureProbe.reject(StructureProbe.Gate.CLEARANCE_ABOVE_FLUID);
            return Optional.empty();
        }
        // Reject steep terrain.
        if (footprint.spread() > maxSlope) {
            StructureProbe.reject(StructureProbe.Gate.MAX_SLOPE);
            return Optional.empty();
        }

        int halfX = footprint.halfX();
        int halfZ = footprint.halfZ();

        // A turned or mirrored template is not anchored with a corner at the piece position:
        // vanilla's transform puts one corner there and lets the box grow in the other direction.
        // Ask the template where its box starts and shift, so "centred on the chunk middle" means
        // the same thing for all four orientations.
        BoundingBox ownBox = tmpl.getBoundingBox(BlockPos.ZERO, rot, BlockPos.ZERO, mir);
        BlockPos templatePos = new BlockPos(x - halfX - ownBox.minX(), hc + verticalOffset,
                z - halfZ - ownBox.minZ());
        return Optional.of(new GenerationStub(new BlockPos(x, hc, z), builder -> builder.addPiece(
                new GroundedTemplatePiece(ctx.structureTemplateManager(), variant.template(),
                        templatePos, rot, mir, processors.value().list()))));
    }

    /**
     * Chooses which template this attempt places. A single variant returns without touching the
     * random source, so a datapack that does not ask for variation draws exactly the same numbers
     * as before the field existed.
     */
    private Variant pickVariant(RandomSource random) {
        if (templates.size() == 1) {
            return templates.get(0);
        }
        int totalWeight = 0;
        for (Variant variant : templates) {
            totalWeight += variant.weight();
        }
        int roll = random.nextInt(totalWeight);
        for (Variant variant : templates) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant;
            }
        }
        return templates.get(templates.size() - 1);
    }
}
