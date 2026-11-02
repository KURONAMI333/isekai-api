package com.kuronami.isekaiapi.structure;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link TemplateStructurePiece} backing {@link GroundedTemplateStructure}. Delegates to
 * vanilla {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate}
 * placement, which clamps to each chunk's writable bounding box across successive
 * {@code postProcess} calls — so a template larger than one chunk is placed correctly instead of
 * being clipped or duplicated.
 *
 * <p>The processor list is part of the piece's saved data, not just the structure's: a structure
 * start can be saved when its chunk is only partially generated and be re-placed after a reload,
 * and the processors must still run then. It is written as the resolved inline list
 * ({@code StructureProcessorType.DIRECT_CODEC}) rather than as a reference, because the piece's
 * load path has no registry access; processor lists are static datapack data, so the resolved form
 * is equivalent.
 */
@ApiStatus.Internal
public final class GroundedTemplatePiece extends TemplateStructurePiece {

    private static final String PROCESSORS_KEY = "isekai_processors";
    private static final String ROTATION_KEY = "isekai_rotation";
    private static final String MIRROR_KEY = "isekai_mirror";

    private final Rotation rotation;
    private final Mirror mirror;
    private final List<StructureProcessor> processors;

    public GroundedTemplatePiece(StructureTemplateManager mgr, ResourceLocation template, BlockPos pos,
                                 Rotation rotation, Mirror mirror, List<StructureProcessor> processors) {
        super(IsekaiStructures.GROUNDED_TEMPLATE_PIECE.get(), 0, mgr, template, template.toString(),
                makeSettings(rotation, mirror, processors), pos);
        this.rotation = rotation;
        this.mirror = mirror;
        this.processors = processors;
    }

    public GroundedTemplatePiece(StructureTemplateManager mgr, CompoundTag tag) {
        super(IsekaiStructures.GROUNDED_TEMPLATE_PIECE.get(), tag, mgr,
                rl -> makeSettings(readRotation(tag), readMirror(tag), readProcessors(tag)));
        this.rotation = readRotation(tag);
        this.mirror = readMirror(tag);
        this.processors = readProcessors(tag);
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation, Mirror mirror,
                                                      List<StructureProcessor> processors) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror);
        processors.forEach(settings::addProcessor);
        return settings;
    }

    private static Rotation readRotation(CompoundTag tag) {
        return decode(Rotation.CODEC, tag.get(ROTATION_KEY)).orElse(Rotation.NONE);
    }

    private static Mirror readMirror(CompoundTag tag) {
        return decode(Mirror.CODEC, tag.get(MIRROR_KEY)).orElse(Mirror.NONE);
    }

    private static List<StructureProcessor> readProcessors(CompoundTag tag) {
        return decode(StructureProcessorType.DIRECT_CODEC, tag.get(PROCESSORS_KEY))
                .map(StructureProcessorList::list)
                .orElse(List.of());
    }

    private static <T> java.util.Optional<T> decode(Codec<T> codec, Tag tag) {
        if (tag == null) {
            return java.util.Optional.empty();
        }
        return codec.parse(NbtOps.INSTANCE, tag).result();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super.addAdditionalSaveData(ctx, tag);
        tag.put(ROTATION_KEY, Rotation.CODEC.encodeStart(NbtOps.INSTANCE, rotation)
                .result().orElseGet(() -> net.minecraft.nbt.StringTag.valueOf("none")));
        tag.put(MIRROR_KEY, Mirror.CODEC.encodeStart(NbtOps.INSTANCE, mirror)
                .result().orElseGet(() -> net.minecraft.nbt.StringTag.valueOf("none")));
        StructureProcessorType.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, new StructureProcessorList(processors))
                .result().ifPresent(encoded -> tag.put(PROCESSORS_KEY, encoded));
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // Isekai templates carry no data markers.
    }

    @ApiStatus.Internal
    public static StructurePieceType.StructureTemplateType pieceType() {
        return GroundedTemplatePiece::new;
    }
}
