package com.kuronami.isekaiapi.structure;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * A {@link Structure} whose generation is the placement of an ordered list of vanilla
 * {@link PlacedFeature}s at the structure's origin (Y snapped to the live
 * {@code WORLD_SURFACE_WG} heightmap). The Structure layer supplies {@code /locate} support,
 * spacing via {@code structure_set}, biome-tag filtering, and generation-step ordering, then
 * delegates block placement to the consumer-supplied features.
 *
 * <p>Scope: this is for a <strong>loose scatter of independent features at one point</strong>
 * (a few boulders and bushes near a spot). Each referenced feature re-decides its own
 * placement, so this is the wrong tool for a <strong>coordinated set-piece</strong> whose
 * blocks must line up (an oasis: pool + beach + clustered trees) — there is no
 * {@code terrain_adaptation}, no relative positioning between features, and no processors.
 * Build coordinated landmarks the vanilla way instead: a hand-authored NBT placed by a
 * {@code minecraft:jigsaw} structure with {@code terrain_adaptation: beard_thin} and
 * {@code project_start_to_heightmap} (see {@code docs/DATAPACK_REFERENCE.md} → "set-pieces").
 *
 * <p>Neutral by design: this class knows nothing about what its features do — they are
 * opaque references invoked at the resolved origin.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "type": "isekai_api:assembled",
 *   "biomes": "#namespace:can_have_my_landmark",
 *   "step": "vegetal_decoration",
 *   "spawn_overrides": {},
 *   "terrain_adaptation": "none",
 *   "features": [
 *     "namespace:my_landmark/pool",
 *     "namespace:my_landmark/sand_ring",
 *     "namespace:my_landmark/tree_cluster"
 *   ]
 * }
 * }</pre>
 *
 * <p>Pair with a {@code structure_set} JSON that points back at this structure with a
 * {@code random_spread} placement to control density and enable {@code /locate}.
 */
@ApiStatus.Internal
public final class AssembledStructure extends Structure {

    public static final MapCodec<AssembledStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(s -> s.features)
    ).apply(i, AssembledStructure::new));

    private final HolderSet<PlacedFeature> features;

    public AssembledStructure(StructureSettings settings, HolderSet<PlacedFeature> features) {
        super(settings);
        this.features = features;
    }

    public HolderSet<PlacedFeature> features() {
        return features;
    }

    @Override
    public StructureType<?> type() {
        return IsekaiStructures.ASSEMBLED.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        // Anchor at the chunk-center XZ. Reject the position outright when the chunk-center
        // surface lies AT or BELOW the dimension's sea level: any feature that builds blocks
        // around it (a fluid pool, a tree, a cluster) is meant to sit on dry land. The biome
        // filter alone isn't enough because biome assignment uses the climate axes and can
        // extend a "land" biome (e.g. tropical_island via continentalness) over underwater
        // areas — without this check the structure spawns on the seabed.
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int surfaceY = ctx.chunkGenerator().getFirstFreeHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int seaLevel = ctx.chunkGenerator().getSeaLevel();
        if (surfaceY <= seaLevel) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, surfaceY, z);
        return Optional.of(new GenerationStub(origin, builder ->
                builder.addPiece(new AssembledPiece(origin, features))));
    }
}
