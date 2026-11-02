package com.kuronami.isekaiapi.tree.decorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Puts snow layers on the leaves that face the sky — the cold-forest look where the crown is white
 * on top but the leaves are still there.
 *
 * <p>Datapack type {@code isekai_api:snow_cap}. Fields:
 * <ul>
 *   <li>{@code layers} (optional, 1..8, default 1) — snow depth, sampled per leaf. Vanilla caps a
 *       snow layer at 8; higher sampled values are clamped rather than rejected so a provider
 *       written against a wider range still works.</li>
 *   <li>{@code chance} (optional, 0..1, default 1) — per capped leaf, for a patchy melt.</li>
 * </ul>
 *
 * <p>Only leaves whose block above is air get snow: a leaf under another leaf is not a cap, and
 * snow is never written into an occupied cell. The leaf block itself is left in place.
 */
@ApiStatus.Internal
public final class SnowCapDecorator extends TreeDecorator {

    /** Vanilla's maximum snow depth. */
    private static final int MAX_LAYERS = 8;

    public static final MapCodec<SnowCapDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProvider.codec(1, MAX_LAYERS).optionalFieldOf("layers", ConstantInt.of(1))
                    .forGetter(decorator -> decorator.layers),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(decorator -> decorator.chance)
    ).apply(instance, SnowCapDecorator::new));

    private final IntProvider layers;
    private final float chance;

    private SnowCapDecorator(IntProvider layers, float chance) {
        this.layers = layers;
        this.chance = chance;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.SNOW_CAP.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource random = context.random();
        for (BlockPos leaf : context.leaves()) {
            BlockPos capPos = leaf.above();
            if (!context.isAir(capPos)) {
                continue;
            }
            if (random.nextFloat() >= this.chance) {
                continue;
            }
            int depth = Math.min(MAX_LAYERS, Math.max(1, this.layers.sample(random)));
            BlockState snow = Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, depth);
            DecoratorPlacing.place(context, capPos, snow);
        }
    }
}
