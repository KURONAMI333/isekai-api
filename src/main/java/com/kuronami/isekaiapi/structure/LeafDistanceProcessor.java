package com.kuronami.isekaiapi.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

/**
 * Bakes leaf decay data into a structure template: every {@link LeavesBlock} in the template gets
 * the {@code distance} it would have had if the game had grown it around the template's own logs.
 *
 * <p>Why a structure processor rather than a tree feature: a template can be pasted with its logs
 * missing or with the crown reshaped by an editor, and the leaves then carry whatever distance the
 * editing tool left behind. If that value is 7, a leaf that is visually part of a crown decays on
 * the first shape update. This processor recomputes the value from the logs that are actually in the
 * template, and decides what to do with leaves that reach no log at all ({@code orphan_leaves}).
 *
 * <p>{@code distance} is 1 for a leaf touching a log and grows by one per leaf step, up to 6; a leaf
 * that cannot reach a log within six steps gets 7, which is vanilla's "decay me" value. A persistent
 * leaf never decays regardless of distance, so {@code orphan_leaves: persist} plus
 * {@code keep_persistent} is how a floating crown (a canopy with no trunk in the template) is kept.
 *
 * <p>Fields: {@code orphan_leaves} ({@code decay} or {@code persist}, default {@code decay}) and
 * {@code keep_persistent} (bool, default false — false resets a template leaf's {@code persistent}
 * flag to false, true leaves it as the template wrote it).
 *
 * <p>The processor runs in {@code finalizeProcessing}, so later processors in the same list have
 * already made their block substitutions and this pass sees the final states.
 */
@ApiStatus.Internal
public final class LeafDistanceProcessor extends StructureProcessor {

    /** Leaves further than this from every log are orphans (vanilla's decay threshold). */
    private static final int MAX_DISTANCE = 7;

    public static final MapCodec<LeafDistanceProcessor> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                            OrphanLeaves.CODEC.optionalFieldOf("orphan_leaves", OrphanLeaves.DECAY)
                                    .forGetter(p -> p.orphanLeaves),
                            Codec.BOOL.optionalFieldOf("keep_persistent", Boolean.FALSE)
                                    .forGetter(p -> p.keepPersistent))
                    .apply(inst, LeafDistanceProcessor::new));

    /** What to do with leaves that cannot reach any log in the template. */
    public enum OrphanLeaves implements StringRepresentable {
        /** Leave them at distance 7 and non-persistent: vanilla decays them. */
        DECAY("decay"),
        /** Mark them persistent so a crown without a trunk stays. */
        PERSIST("persist");

        public static final Codec<OrphanLeaves> CODEC = StringRepresentable.fromEnum(OrphanLeaves::values);

        private final String name;

        OrphanLeaves(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    private final OrphanLeaves orphanLeaves;
    private final boolean keepPersistent;

    public LeafDistanceProcessor(OrphanLeaves orphanLeaves, boolean keepPersistent) {
        this.orphanLeaves = orphanLeaves;
        this.keepPersistent = keepPersistent;
    }

    OrphanLeaves orphanLeaves() {
        return this.orphanLeaves;
    }

    boolean keepPersistent() {
        return this.keepPersistent;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return IsekaiStructures.LEAF_DISTANCES.get();
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(
            ServerLevelAccessor level,
            BlockPos templatePos,
            BlockPos settingsPos,
            List<StructureTemplate.StructureBlockInfo> rawInfos,
            List<StructureTemplate.StructureBlockInfo> processedInfos,
            StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> leaves = new ArrayList<>();
        Map<BlockPos, Integer> leafIndex = new HashMap<>();
        Set<BlockPos> logs = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : processedInfos) {
            if (info.state().getBlock() instanceof LeavesBlock) {
                leafIndex.put(info.pos(), leaves.size());
                leaves.add(info);
            } else if (info.state().is(BlockTags.LOGS)) {
                logs.add(info.pos());
            }
        }
        if (leaves.isEmpty()) {
            return processedInfos;
        }

        int[] distances = distances(leaves, leafIndex, logs);

        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processedInfos.size());
        int nextLeaf = 0;
        for (StructureTemplate.StructureBlockInfo info : processedInfos) {
            if (!(info.state().getBlock() instanceof LeavesBlock)) {
                result.add(info);
                continue;
            }
            int distance = distances[nextLeaf++];
            boolean templatePersistent = info.state().getValue(LeavesBlock.PERSISTENT);
            boolean persistent = (this.keepPersistent && templatePersistent)
                    || (this.orphanLeaves == OrphanLeaves.PERSIST && distance >= MAX_DISTANCE);
            result.add(new StructureTemplate.StructureBlockInfo(info.pos(),
                    info.state()
                            .setValue(LeavesBlock.DISTANCE, distance)
                            .setValue(LeavesBlock.PERSISTENT, persistent),
                    info.nbt()));
        }
        return result;
    }

    /**
     * Breadth-first leaf distance from the nearest log, in {@code leaves} order. Leaves seeded from
     * a log get 1; every further step adds one; anything not reached keeps {@link #MAX_DISTANCE}.
     */
    private static int[] distances(List<StructureTemplate.StructureBlockInfo> leaves,
                                   Map<BlockPos, Integer> leafIndex, Set<BlockPos> logs) {
        int[] distances = new int[leaves.size()];
        Arrays.fill(distances, MAX_DISTANCE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < leaves.size(); i++) {
            if (touchesLog(leaves.get(i).pos(), logs)) {
                distances[i] = 1;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            int next = distances[index] + 1;
            if (next >= MAX_DISTANCE) {
                continue;
            }
            BlockPos pos = leaves.get(index).pos();
            for (Direction direction : Direction.values()) {
                Integer neighbour = leafIndex.get(pos.relative(direction));
                if (neighbour != null && distances[neighbour] > next) {
                    distances[neighbour] = next;
                    queue.add(neighbour);
                }
            }
        }
        return distances;
    }

    private static boolean touchesLog(BlockPos pos, Set<BlockPos> logs) {
        for (Direction direction : Direction.values()) {
            if (logs.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }
}
