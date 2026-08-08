package com.kuronami.isekaiapi.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * A bounded body of fluid dug into the terrain, generated with vanilla {@code LakeFeature}'s
 * algorithm. Three properties come straight from that feature and are the whole point of this
 * class:
 *
 * <ol>
 *   <li><b>It refuses to place where it cannot be contained.</b> Before a single block is
 *       written, every cell of the <em>shell</em> (the cells immediately outside the excavated
 *       volume) is inspected: below the waterline the shell must be solid (or already the same
 *       fluid), above it the shell must not be liquid. Any violation returns {@code false} with
 *       nothing written. A pool never appears with an open edge, because it never appears on
 *       terrain that has one.</li>
 *   <li><b>The shell is lined with {@code rim_block}.</b> Not just the floor — every solid
 *       shell cell below the waterline, and half of them above it, so the basin reads as a
 *       deliberate bank rather than a hole punched into whatever happened to be there.</li>
 *   <li><b>The outline is the union of 4–7 random axis-aligned ellipsoids</b>, not a disc, so
 *       no two pools have the same silhouette and none of them is a perfect circle.</li>
 * </ol>
 *
 * <p>Deliberately <em>not</em> ported from {@code LakeFeature}: the ice-capping pass. It calls
 * {@code getBiome()} up to 15 blocks from the origin, and during worldgen that neighbouring
 * chunk may not exist yet — the call throws "Requested chunk unavailable during world
 * generation". (Block reads at the same distance are fine; vanilla's lava lakes do them.)
 * Vanilla ships no water {@code minecraft:lake} configured feature in 1.21.1, so that branch is
 * dead code there and the bug never fires; here it would. No biome is ever queried.
 *
 * <p>Why a dedicated primitive rather than {@code waterlogged_vegetation_patch}: that feature
 * replaces ground with grass blocks and then floods them, and grass underwater turns to dirt on
 * the next tick — the result is a muddy hole. Here the carve, the rim and the fill are separate
 * passes, so {@code rim_block} is exactly what the consumer chose and never sees water before
 * it is in place.
 *
 * <p>Neutral vocabulary: "pool" is geometric (a bounded body of fluid). The consumer picks
 * {@code fluid} (water, lava, a modded fluid) and {@code rim_block} (sand for desert ponds,
 * stone for highland tarns, mud for swamps).
 *
 * <p>Geometry, relative to the origin (the air cell above the top solid block): the fluid
 * occupies {@code y = -1 .. -depth}, so the surface sits level with the surrounding ground, and
 * the carved-out air above it reaches {@code y = +depth - 1}. The footprint stays within
 * {@code xz_radius + 1} of the origin in x and z.
 *
 * <p>JSON — note that {@code xz_radius} is an {@link IntProvider}: either a bare number, or a
 * dispatched form whose fields sit <em>inline</em> beside {@code "type"}. {@code UniformInt}'s
 * codec is a {@code MapCodec}, so there is no {@code "value"} wrapper; writing one fails
 * registry load with "Not a number / No key min_inclusive".
 *
 * <pre>{@code
 * {"type": "isekai_api:pool",
 *  "config": {
 *    "fluid": {"Name": "minecraft:water"},
 *    "rim_block": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:sand"}},
 *    "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 5},
 *    "depth": 2}}
 * }</pre>
 *
 * @since 2.1.0
 */
@ApiStatus.Internal
public class PoolFeature extends Feature<PoolFeature.Config> {

    /** Vanilla carves lake interiors to cave air, not plain air (LakeFeature:18). */
    private static final BlockState CARVED_AIR = Blocks.CAVE_AIR.defaultBlockState();

    /**
     * Ceiling on the sampled {@code xz_radius}. Reads and writes reach {@code radius + 1} from
     * the origin, and the origin can sit anywhere in the chunk; the feature stage only holds a
     * 3x3 chunk region, so anything past 15 blocks would ask for a chunk that does not exist
     * yet. 14 keeps the worst case at exactly the 15 blocks vanilla's own lakes reach. The
     * codec still accepts 1..64 (it always did) — larger values are clamped, not rejected.
     */
    static final int MAX_RADIUS = 14;

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

    // ------------------------------------------------------------------
    // World access seam
    // ------------------------------------------------------------------

    /**
     * The only way the algorithm touches the world: read a block, or write one of the three
     * kinds of block the feature places. Coordinates are relative to the origin.
     *
     * <p>Two reasons this exists rather than passing {@link WorldGenLevel} around. It makes the
     * geometry and the containment test unit-testable against synthetic terrain, and — because
     * there is no biome accessor on it — the {@code getBiome()} class of worldgen crash is
     * unreachable from the algorithm by construction.
     *
     * <p>Implementations decide replaceability (the {@code #features_cannot_replace} tag): the
     * algorithm asks for a write, the implementation may decline it.
     */
    interface Cells {
        BlockState get(int dx, int dy, int dz);

        /** Excavate above the waterline. */
        void carve(int dx, int dy, int dz);

        /** Fill below the waterline with the configured fluid. */
        void fill(int dx, int dy, int dz);

        /** Line a shell cell with {@code rim_block}. */
        void rim(int dx, int dy, int dz);
    }

    private final class LevelCells implements Cells {
        private final WorldGenLevel level;
        private final BlockPos origin;
        private final Config config;
        private final RandomSource random;

        LevelCells(WorldGenLevel level, BlockPos origin, Config config, RandomSource random) {
            this.level = level;
            this.origin = origin;
            this.config = config;
            this.random = random;
        }

        @Override
        public BlockState get(int dx, int dy, int dz) {
            return level.getBlockState(origin.offset(dx, dy, dz));
        }

        @Override
        public void carve(int dx, int dy, int dz) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!canReplace(level.getBlockState(pos))) {
                return;
            }
            level.setBlock(pos, CARVED_AIR, 2);
            level.scheduleTick(pos, CARVED_AIR.getBlock(), 0);
            markAboveForPostProcessing(level, pos);
        }

        @Override
        public void fill(int dx, int dy, int dz) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!canReplace(level.getBlockState(pos))) {
                return;
            }
            level.setBlock(pos, config.fluid(), 2);
        }

        @Override
        public void rim(int dx, int dy, int dz) {
            BlockPos pos = origin.offset(dx, dy, dz);
            if (!canReplace(level.getBlockState(pos))) {
                return;
            }
            level.setBlock(pos, config.rimBlock().getState(random, pos), 2);
            markAboveForPostProcessing(level, pos);
        }
    }

    /** LakeFeature:150-152. */
    private static boolean canReplace(BlockState state) {
        return !state.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    // ------------------------------------------------------------------
    // Shape
    // ------------------------------------------------------------------

    /**
     * The excavated volume as a boolean grid, plus the shell test over it. The grid carries one
     * cell of margin on every side (that margin <em>is</em> the shell), so neighbour lookups
     * need no bounds guards at the call site: out-of-range reads are simply "not excavated".
     */
    static final class Blob {
        private final int radius;
        private final int depth;
        private final boolean[] cells;
        private int marked;

        Blob(int radius, int depth) {
            this.radius = radius;
            this.depth = depth;
            this.cells = new boolean[(2 * radius + 3) * (2 * radius + 3) * (2 * depth + 2)];
        }

        /** Grid extent: x/z in {@code [-(r+1), r+1]}, y in {@code [-(depth+1), depth]}. */
        private boolean inGrid(int dx, int dy, int dz) {
            return dx >= -(radius + 1) && dx <= radius + 1
                    && dz >= -(radius + 1) && dz <= radius + 1
                    && dy >= -(depth + 1) && dy <= depth;
        }

        private int index(int dx, int dy, int dz) {
            int nz = 2 * radius + 3;
            int ny = 2 * depth + 2;
            return ((dx + radius + 1) * nz + (dz + radius + 1)) * ny + (dy + depth + 1);
        }

        boolean get(int dx, int dy, int dz) {
            return inGrid(dx, dy, dz) && cells[index(dx, dy, dz)];
        }

        void mark(int dx, int dy, int dz) {
            int i = index(dx, dy, dz);
            if (!cells[i]) {
                cells[i] = true;
                marked++;
            }
        }

        boolean isEmpty() {
            return marked == 0;
        }

        /** LakeFeature:65-73 / 111-119 — not excavated, but touching an excavated cell. */
        boolean isShell(int dx, int dy, int dz) {
            if (get(dx, dy, dz)) {
                return false;
            }
            return get(dx + 1, dy, dz) || get(dx - 1, dy, dz)
                    || get(dx, dy + 1, dz) || get(dx, dy - 1, dz)
                    || get(dx, dy, dz + 1) || get(dx, dy, dz - 1);
        }
    }

    /**
     * LakeFeature:35-58 — the union of {@code 4..7} axis-aligned ellipsoids, each sized and
     * positioned to fit inside the working box.
     *
     * <p>Vanilla hard-codes its box at 16x16x8 with the blob confined to 14 cells in x/z and 4
     * in y. Here the box follows the config, so vanilla's constants are scaled by
     * {@code sxz} and {@code sy}; at {@code xz_radius = 7, depth = 2} the two agree almost
     * exactly. The {@code max(2.0, ...)} floor is the one addition: with a small radius the
     * scaled diameter can drop below a single cell and the ellipsoid would mark nothing, which
     * vanilla cannot hit because its diameters start at 3 in a 16-wide box.
     */
    static Blob shape(int radius, int depth, RandomSource random) {
        Blob blob = new Blob(radius, depth);
        double sxz = (2 * radius + 1) / 14.0;
        double sy = (2 * depth) / 4.0;
        double minXZ = -radius - 0.5;
        double spanXZ = 2 * radius + 1;
        double minY = -depth - 0.5;
        double spanY = 2 * depth;

        int count = random.nextInt(4) + 4;
        for (int n = 0; n < count; n++) {
            double diameterX = Math.max(2.0, (random.nextDouble() * 6.0 + 3.0) * sxz);
            double diameterY = Math.max(2.0, (random.nextDouble() * 4.0 + 2.0) * sy);
            double diameterZ = Math.max(2.0, (random.nextDouble() * 6.0 + 3.0) * sxz);
            double centreX = minXZ + diameterX / 2.0 + random.nextDouble() * (spanXZ - diameterX);
            double centreY = minY + diameterY / 2.0 + random.nextDouble() * (spanY - diameterY);
            double centreZ = minXZ + diameterZ / 2.0 + random.nextDouble() * (spanXZ - diameterZ);

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -depth; dy <= depth - 1; dy++) {
                        double ex = (dx - centreX) / (diameterX / 2.0);
                        double ey = (dy - centreY) / (diameterY / 2.0);
                        double ez = (dz - centreZ) / (diameterZ / 2.0);
                        if (ex * ex + ey * ey + ez * ez < 1.0) {
                            blob.mark(dx, dy, dz);
                        }
                    }
                }
            }
        }
        return blob;
    }

    // ------------------------------------------------------------------
    // Containment + placement
    // ------------------------------------------------------------------

    /**
     * LakeFeature:62-86 — can this terrain hold the fluid? Below the waterline every shell cell
     * must be solid, or already be the same fluid (two pools may share a wall); above it, no
     * shell cell may be liquid, or the pool would drain into it. Reads only — this runs to
     * completion before any write happens.
     *
     * <p>{@code liquid()} and {@code isSolid()} carry Mojang's bare {@code @Deprecated} marker
     * with no replacement; these are the two predicates {@code LakeFeature} itself uses for this
     * test, and substituting a nearby-looking one would change which terrain holds fluid.
     */
    @SuppressWarnings("deprecation")
    static boolean canHold(Cells cells, Blob blob, int radius, int depth, BlockState fluid) {
        for (int dx = -(radius + 1); dx <= radius + 1; dx++) {
            for (int dz = -(radius + 1); dz <= radius + 1; dz++) {
                for (int dy = -(depth + 1); dy <= depth; dy++) {
                    if (!blob.isShell(dx, dy, dz)) {
                        continue;
                    }
                    BlockState state = cells.get(dx, dy, dz);
                    if (dy >= 0) {
                        if (state.liquid()) {
                            return false;
                        }
                    } else if (!state.isSolid() && state != fluid) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * The whole algorithm, with the world behind {@link Cells}. Order is load-bearing: the
     * shape is pure computation, {@link #canHold} is read-only, and the first write happens
     * only after both have succeeded — so a refusal leaves the world untouched.
     */
    @SuppressWarnings("deprecation")  // BlockState#isSolid — see canHold
    static boolean placeInto(Cells cells, int radius, int depth, BlockState fluid,
                             RandomSource random) {
        Blob blob = shape(radius, depth, random);
        if (blob.isEmpty()) {
            return false;
        }
        if (!canHold(cells, blob, radius, depth, fluid)) {
            return false;
        }

        // LakeFeature:88-104 — excavate above the waterline, fill below it.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -depth; dy <= depth - 1; dy++) {
                    if (!blob.get(dx, dy, dz)) {
                        continue;
                    }
                    if (dy >= 0) {
                        cells.carve(dx, dy, dz);
                    } else {
                        cells.fill(dx, dy, dz);
                    }
                }
            }
        }

        // LakeFeature:106-131 — line the shell. Below the waterline always (that is the part
        // holding the fluid in); above it half the time, so the bank breaks up instead of
        // reading as a moulded ring.
        for (int dx = -(radius + 1); dx <= radius + 1; dx++) {
            for (int dz = -(radius + 1); dz <= radius + 1; dz++) {
                for (int dy = -(depth + 1); dy <= depth; dy++) {
                    if (!blob.isShell(dx, dy, dz)) {
                        continue;
                    }
                    if (dy >= 0 && random.nextInt(2) == 0) {
                        continue;
                    }
                    if (cells.get(dx, dy, dz).isSolid()) {
                        cells.rim(dx, dy, dz);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config config = ctx.config();
        BlockPos origin = ctx.origin();
        int radius = Math.min(config.xzRadius().sample(random), MAX_RADIUS);
        int depth = config.depth();

        // LakeFeature:30-32 — the working box must fit inside the build height.
        if (origin.getY() - (depth + 1) <= level.getMinBuildHeight()
                || origin.getY() + depth >= level.getMaxBuildHeight()) {
            return false;
        }
        return placeInto(new LevelCells(level, origin, config, random),
                radius, depth, config.fluid(), random);
    }
}
