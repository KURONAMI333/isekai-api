package com.kuronami.isekaiapi.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * Carve a horizontal disc of {@code xz_radius} into the terrain at the origin, line its rim
 * (the cells just outside the disc and along its bottom) with {@code rim_block}, and fill the
 * interior to the rim height with {@code fluid}. The disc is {@code depth} blocks deep.
 *
 * <p>Why a dedicated primitive instead of {@code waterlogged_vegetation_patch}: that vanilla
 * feature replaces ground with grass blocks and then floods them, but grass underwater turns
 * to dirt on next tick — the result is a muddy hole, not a clean pool. {@code pool} carves
 * THEN places the rim THEN fills with fluid, so the rim block is whatever the consumer chose
 * (sand, stone, custom) and never sees water before it's in place.
 *
 * <p>Neutral: "pool" is geometric (a bounded body of fluid). No oasis/lake/spring vocabulary.
 * Consumer chooses {@code fluid} (typically water/lava) and {@code rim_block} (sand for
 * desert-style ponds, stone for highland tarns, mud for swamps, etc.).
 *
 * <p>JSON: {@code {"type":"isekai_api:pool", "fluid":{"Name":"minecraft:water","Properties":{"level":"0"}}, "rim_block":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:sand"}}, "xz_radius":{"type":"minecraft:uniform","value":{"min_inclusive":3,"max_inclusive":5}}, "depth":2}}.
 */
@ApiStatus.Internal
public class PoolFeature extends Feature<PoolFeature.Config> {

    public record Config(BlockState fluid, BlockStateProvider rimBlock, IntProvider xzRadius,
                         int depth) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("fluid").forGetter(Config::fluid),
                BlockStateProvider.CODEC.fieldOf("rim_block").forGetter(Config::rimBlock),
                IntProvider.codec(1, 64).fieldOf("xz_radius").forGetter(Config::xzRadius),
                Codec.intRange(1, 32).optionalFieldOf("depth", 2).forGetter(Config::depth)
        ).apply(i, Config::new));
    }

    public PoolFeature(Codec<Config> codec) {
        super(codec);
    }

    public PoolFeature() {
        this(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config config = ctx.config();
        BlockPos origin = ctx.origin();                 // air cell above the top solid block
        int r = config.xzRadius().sample(random);
        int depth = config.depth();
        double rsq = (r + 0.5) * (r + 0.5);

        // Geometry: the water's TOP SURFACE sits exactly at the natural ground level (y = -1
        // relative to origin, since origin is air above the surface). The pool is dug DOWN
        // from the surface to y = -depth; the floor (rim_block) is one block deeper at
        // y = -(depth+1). The natural surrounding terrain forms the walls — no popped-up rim
        // ring is needed (the previous algorithm placed a one-block-tall rim ABOVE the surface
        // which read as a floating basin).
        //
        // Cells from y = -1 (water surface) down to y = -depth are the fluid volume. Cells
        // above y = -1 stay untouched (sky).
        //
        // Step 1: replace the fluid-volume cells with air, in case the existing block isn't
        // already a clean carve. Doing it as a distinct pass lets the fluid-fill loop be
        // straightforward.
        for (int dy = 1; dy <= depth; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > rsq) continue;
                    level.setBlock(origin.offset(dx, -dy, dz),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // Step 2: floor at y = -(depth + 1). Force-place rim_block (no canBeReplaced check) so
        // the fluid sits on a known substrate regardless of original terrain.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rsq) continue;
                BlockPos floor = origin.offset(dx, -(depth + 1), dz);
                level.setBlock(floor, config.rimBlock().getState(random, floor), 2);
            }
        }
        // Step 3: fill the carved volume with fluid. y = -1 is the water surface, level with
        // the surrounding ground.
        for (int dy = 1; dy <= depth; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > rsq) continue;
                    level.setBlock(origin.offset(dx, -dy, dz), config.fluid(), 2);
                }
            }
        }
        return true;
    }
}
