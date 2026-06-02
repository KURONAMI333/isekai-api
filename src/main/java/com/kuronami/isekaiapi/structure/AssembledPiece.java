package com.kuronami.isekaiapi.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@link StructurePiece} backing {@link AssembledStructure}. At {@code postProcess} time
 * it snaps the origin's Y to the current {@code WORLD_SURFACE_WG} heightmap and invokes each
 * configured feature's {@code place(level, generator, random, origin)} in declaration order.
 *
 * <p>NBT persistence stores the feature list as {@link ResourceLocation} strings and the
 * origin XZ; at load time the pieces are reconstructed via the registry access from the
 * {@link StructurePieceSerializationContext}. This keeps the piece self-sufficient across
 * chunk save/load (the {@link AssembledStructure} configuration is also re-resolved from
 * the same registry, so consistency is automatic).
 */
@ApiStatus.Internal
public final class AssembledPiece extends StructurePiece {

    /** Generous bounding box around the origin so cross-chunk feature placement still runs.
     * A 33x97x33 box centred on origin covers most reasonable features (large palms / pools /
     * clusters); features themselves may write outside this and that's allowed — the box only
     * controls which chunks fire postProcess. */
    private static final int HALF_HORIZONTAL = 16;
    private static final int BOX_Y_DOWN = 16;
    private static final int BOX_Y_UP = 80;

    private static final String TAG_ORIGIN_X = "ox";
    private static final String TAG_ORIGIN_Z = "oz";
    private static final String TAG_FEATURES = "features";

    private final int originX;
    private final int originZ;
    private final List<ResourceLocation> featureIds;

    public AssembledPiece(BlockPos origin, HolderSet<PlacedFeature> features) {
        super(IsekaiStructures.ASSEMBLED_PIECE.get(), 0, makeBox(origin));
        this.originX = origin.getX();
        this.originZ = origin.getZ();
        this.featureIds = collectIds(features);
    }

    public AssembledPiece(CompoundTag tag) {
        super(IsekaiStructures.ASSEMBLED_PIECE.get(), tag);
        this.originX = tag.getInt(TAG_ORIGIN_X);
        this.originZ = tag.getInt(TAG_ORIGIN_Z);
        this.featureIds = new ArrayList<>();
        ListTag list = tag.getList(TAG_FEATURES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
            if (rl != null) this.featureIds.add(rl);
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt(TAG_ORIGIN_X, originX);
        tag.putInt(TAG_ORIGIN_Z, originZ);
        ListTag list = new ListTag();
        for (ResourceLocation rl : featureIds) {
            list.add(StringTag.valueOf(rl.toString()));
        }
        tag.put(TAG_FEATURES, list);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager mgr,
                            ChunkGenerator gen, RandomSource random, BoundingBox box, ChunkPos chunkPos,
                            BlockPos pivot) {
        // Snap Y to the live heightmap so the structure sits on the actual surface even if
        // terrain finalised differently from initial expectation. Use WORLD_SURFACE_WG (no
        // floor restriction) — consumers can write their own y_anchor into individual
        // features if they need a different reference.
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originX, originZ);
        BlockPos origin = new BlockPos(originX, y, originZ);

        // Resolve features through the live registry — captures any datapack reload between
        // chunk gen and structure place (rare but possible during /reload).
        var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (ResourceLocation id : featureIds) {
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, id);
            Optional<Holder.Reference<PlacedFeature>> holder = registry.get(key);
            if (holder.isEmpty()) {
                IsekaiApi.LOGGER.warn("[Isekai] assembled structure: configured feature {} not in registry, skipped", id);
                continue;
            }
            holder.get().value().place(level, gen, random, origin);
        }
    }

    private static BoundingBox makeBox(BlockPos origin) {
        return new BoundingBox(
                origin.getX() - HALF_HORIZONTAL, origin.getY() - BOX_Y_DOWN, origin.getZ() - HALF_HORIZONTAL,
                origin.getX() + HALF_HORIZONTAL, origin.getY() + BOX_Y_UP, origin.getZ() + HALF_HORIZONTAL);
    }

    private static List<ResourceLocation> collectIds(HolderSet<PlacedFeature> features) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Holder<PlacedFeature> h : features) {
            h.unwrapKey().ifPresent(k -> ids.add(k.location()));
        }
        return ids;
    }

    @ApiStatus.Internal
    public static StructurePieceType.ContextlessType pieceType() {
        return AssembledPiece::new;
    }
}
