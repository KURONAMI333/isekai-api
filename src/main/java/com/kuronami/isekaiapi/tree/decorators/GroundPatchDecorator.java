package com.kuronami.isekaiapi.tree.decorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Spreads a patch of blocks on the ground around the base of the tree — leaf litter, moss, snow
 * dust, gravel.
 *
 * <p>Datapack type {@code isekai_api:ground_patch}. Fields:
 * <ul>
 *   <li>{@code block_provider} (required) — what to lay down.</li>
 *   <li>{@code radius} (required, 0..16) — the patch is a disc of this radius, sampled once per
 *       tree, so a uniform 1..3 gives some trees a small patch and some a wide one.</li>
 *   <li>{@code chance} (optional, 0..1, default 1) — rolled per column inside the disc, so a
 *       lower value thins the patch instead of shrinking it.</li>
 *   <li>{@code replaceable} (optional block tag, default {@code #minecraft:dirt}) — which ground
 *       the patch may touch.</li>
 *   <li>{@code mode} (optional, {@code cover} or {@code replace}, default {@code cover}) —
 *       {@code cover} lays the block in the air on top of the ground and leaves the ground alone;
 *       {@code replace} swaps the ground block itself (podzol under a conifer, mycelium, and so
 *       on).</li>
 * </ul>
 *
 * <p>Columns are searched from just above the trunk base downwards, so the trunk column itself is
 * skipped in {@code cover} mode instead of being overwritten, and a tree standing on a slope gets
 * its patch on the slope rather than at a single flat height.
 *
 * <p>Limitation: the radius is a flat disc around the trunk. A tree whose crown leans away (a
 * palm) still gets its patch centred on the trunk, not under the crown.
 */
@ApiStatus.Internal
public final class GroundPatchDecorator extends TreeDecorator {

    /** Highest offset above the trunk base that is still searched; air there can sit on ground. */
    private static final int SEARCH_TOP = 1;

    /** How far below the trunk base a column is searched for ground. */
    private static final int SEARCH_DEPTH = 3;

    public static final MapCodec<GroundPatchDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(decorator -> decorator.blockProvider),
            IntProvider.codec(0, 16).fieldOf("radius").forGetter(decorator -> decorator.radius),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(decorator -> decorator.chance),
            TagKey.codec(Registries.BLOCK)
                    .optionalFieldOf("replaceable", BlockTags.DIRT)
                    .forGetter(decorator -> decorator.replaceable),
            Mode.CODEC.optionalFieldOf("mode", Mode.COVER).forGetter(decorator -> decorator.mode)
    ).apply(instance, GroundPatchDecorator::new));

    private final BlockStateProvider blockProvider;
    private final IntProvider radius;
    private final float chance;
    private final TagKey<Block> replaceable;
    private final Mode mode;

    private GroundPatchDecorator(BlockStateProvider blockProvider, IntProvider radius, float chance,
                                 TagKey<Block> replaceable, Mode mode) {
        this.blockProvider = blockProvider;
        this.radius = radius;
        this.chance = chance;
        this.replaceable = replaceable;
        this.mode = mode;
    }

    /** Whether the patch sits on top of the ground or replaces it. */
    public enum Mode implements StringRepresentable {
        /** Lay the block in the air above the ground, keeping the ground block. */
        COVER("cover"),
        /** Replace the ground block itself. */
        REPLACE("replace");

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.GROUND_PATCH.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        BlockPos base = DecoratorPlacing.trunkBase(context);
        if (base == null) {
            return;
        }
        RandomSource random = context.random();
        int patchRadius = this.radius.sample(random);
        int radiusSquared = patchRadius * patchRadius;
        for (int dx = -patchRadius; dx <= patchRadius; dx++) {
            for (int dz = -patchRadius; dz <= patchRadius; dz++) {
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                if (random.nextFloat() >= this.chance) {
                    continue;
                }
                placeColumn(context, base, dx, dz);
            }
        }
    }

    private void placeColumn(TreeDecorator.Context context, BlockPos base, int dx, int dz) {
        BlockState state = this.blockProvider.getState(context.random(), base.offset(dx, 0, dz));
        for (int dy = SEARCH_TOP; dy >= -SEARCH_DEPTH; dy--) {
            BlockPos pos = base.offset(dx, dy, dz);
            if (this.mode == Mode.COVER) {
                if (context.isAir(pos) && isReplaceableGround(context, pos.below())) {
                    DecoratorPlacing.place(context, pos, state);
                    return;
                }
            } else if (context.level().isStateAtPosition(pos, candidate -> candidate.is(this.replaceable))) {
                DecoratorPlacing.place(context, pos, state);
                return;
            }
        }
    }

    private boolean isReplaceableGround(TreeDecorator.Context context, BlockPos pos) {
        return context.level().isStateAtPosition(pos, candidate -> candidate.is(this.replaceable));
    }
}
