package com.kuronami.isekaiapi.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link TemplateStructurePiece} backing {@link GroundedTemplateStructure}. Delegates to
 * vanilla {@link StructureTemplate} placement, which clamps to each chunk's writable bounding
 * box across successive {@code postProcess} calls — so a template larger than one chunk is
 * placed correctly instead of being clipped or duplicated.
 */
@ApiStatus.Internal
public final class GroundedTemplatePiece extends TemplateStructurePiece {

    public GroundedTemplatePiece(StructureTemplateManager mgr, ResourceLocation template, BlockPos pos) {
        super(IsekaiStructures.GROUNDED_TEMPLATE_PIECE.get(), 0, mgr, template, template.toString(), makeSettings(), pos);
    }

    public GroundedTemplatePiece(StructureTemplateManager mgr, CompoundTag tag) {
        super(IsekaiStructures.GROUNDED_TEMPLATE_PIECE.get(), tag, mgr, rl -> makeSettings());
    }

    private static StructurePlaceSettings makeSettings() {
        return new StructurePlaceSettings();
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
