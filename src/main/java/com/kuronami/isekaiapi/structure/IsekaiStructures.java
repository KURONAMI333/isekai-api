package com.kuronami.isekaiapi.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's {@link StructureType} and {@link StructurePieceType} entries into the
 * vanilla runtime registries. The actual {@code Structure} and {@code StructureSet} instances
 * are datapack JSON (a frozen registry); only the dispatch codecs / piece deserialisers
 * live in code.
 *
 * <p>Registered types:
 * <ul>
 *   <li>{@code isekai_api:assembled} — a {@link StructureType} backed by
 *       {@link AssembledStructure}. Lets a datapack declare a locatable composite
 *       landmark whose content is a list of {@code ConfiguredFeature}s placed at the
 *       structure origin. The neutral on-ramp for {@code /locate}-able landmarks
 *       without writing Java.</li>
 *   <li>{@code isekai_api:assembled_piece} — the {@link StructurePieceType} backing
 *       {@link AssembledPiece}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_TYPE, IsekaiApi.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(BuiltInRegistries.STRUCTURE_PIECE, IsekaiApi.MODID);

    public static final Supplier<StructureType<AssembledStructure>> ASSEMBLED =
            STRUCTURE_TYPES.register("assembled", () -> () -> AssembledStructure.CODEC);

    public static final Supplier<StructurePieceType> ASSEMBLED_PIECE =
            STRUCTURE_PIECE_TYPES.register("assembled_piece", AssembledPiece::pieceType);

    public static final Supplier<StructureType<GroundedTemplateStructure>> GROUNDED_TEMPLATE =
            STRUCTURE_TYPES.register("grounded_template", () -> () -> GroundedTemplateStructure.CODEC);

    public static final Supplier<StructurePieceType> GROUNDED_TEMPLATE_PIECE =
            STRUCTURE_PIECE_TYPES.register("grounded_template_piece", GroundedTemplatePiece::pieceType);

    private IsekaiStructures() {}

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECE_TYPES.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] structures registered: assembled, grounded_template (types) + pieces");
    }
}
