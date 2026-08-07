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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Carve a horizontal footprint of {@code xz_radius} into the terrain at the origin, line its
 * floor with {@code rim_block}, and fill the carved volume to the natural ground level with
 * {@code fluid}. The footprint is {@code depth} blocks deep.
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
 * <p>The outline is a circle by default. {@code irregularity} bites into it at a
 * seed-dependent set of angles, so the pool reads as a natural puddle instead of a compass
 * circle. The bite is <em>inward only</em> — see {@link #footprint} for why that is not a
 * cosmetic detail.
 *
 * <p>JSON: {@code {"type":"isekai_api:pool", "fluid":{"Name":"minecraft:water","Properties":{"level":"0"}}, "rim_block":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:sand"}}, "xz_radius":{"type":"minecraft:uniform","min_inclusive":4,"max_inclusive":6}, "depth":2, "irregularity":0.35}}.
 *
 * <p>{@code xz_radius} is an {@link IntProvider}: either a bare number, or a dispatched
 * form whose fields sit <em>inline</em> beside {@code "type"}. {@code UniformInt}'s codec is
 * a {@code MapCodec}, so there is no {@code "value"} wrapper — writing one fails registry
 * load with "Not a number / No key min_inclusive".
 */
@ApiStatus.Internal
public class PoolFeature extends Feature<PoolFeature.Config> {

    /**
     * Relative strength of harmonics 1..5 in the outline wave. Sums to 1, so the wave stays in
     * [-1, 1] and the bite depth is exactly {@code irregularity} at its deepest. Five low
     * harmonics: one alone only slides the circle sideways (an egg), while anything above five
     * would put neighbouring blocks on opposite sides of the edge and fray the bank. This
     * mix gives a lopsided blob — a puddle, not a compass circle and not noise.
     */
    private static final double[] HARMONICS = {0.24, 0.24, 0.22, 0.16, 0.14};

    /**
     * @param fluid        block state the carved volume is filled with
     * @param rimBlock     substrate placed one block below the fluid volume
     * @param xzRadius     nominal (maximum) horizontal radius
     * @param depth        fluid volume thickness in blocks
     * @param irregularity 0 = exact circle; higher values bite deeper into the outline.
     *                     At 1.0 the outline can retreat to a single block from the centre.
     * @since 2.1.0 {@code irregularity}
     */
    public record Config(BlockState fluid, BlockStateProvider rimBlock, IntProvider xzRadius,
                         int depth, double irregularity) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("fluid").forGetter(Config::fluid),
                BlockStateProvider.CODEC.fieldOf("rim_block").forGetter(Config::rimBlock),
                IntProvider.codec(1, 64).fieldOf("xz_radius").forGetter(Config::xzRadius),
                Codec.intRange(1, 32).optionalFieldOf("depth", 2).forGetter(Config::depth),
                Codec.doubleRange(0.0, 1.0).optionalFieldOf("irregularity", 0.0)
                        .forGetter(Config::irregularity)
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

        // One footprint, computed once, iterated by all three passes. Floor coverage is then
        // identical to fluid coverage by construction — not by three conditionals agreeing.
        // A disagreement there would leave a gap in the floor and the pool would drain.
        List<int[]> footprint = footprint(r, config.irregularity(),
                shapeSeed(level.getSeed(), origin.getX(), origin.getZ()));

        // Geometry: the fluid's TOP SURFACE sits exactly at the natural ground level (y = -1
        // relative to origin, since origin is air above the surface). The pool is dug DOWN
        // from the surface to y = -depth; the floor (rim_block) is one block deeper at
        // y = -(depth+1). The natural surrounding terrain forms the walls — no popped-up rim
        // ring is needed (the previous algorithm placed a one-block-tall rim ABOVE the surface
        // which read as a floating basin).
        //
        // Cells from y = -1 (fluid surface) down to y = -depth are the fluid volume. Cells
        // above y = -1 stay untouched (sky).
        //
        // Step 1: replace the fluid-volume cells with air, in case the existing block isn't
        // already a clean carve. Doing it as a distinct pass lets the fluid-fill loop be
        // straightforward.
        for (int dy = 1; dy <= depth; dy++) {
            for (int[] o : footprint) {
                level.setBlock(origin.offset(o[0], -dy, o[1]),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // Step 2: floor at y = -(depth + 1). Force-place rim_block (no canBeReplaced check) so
        // the fluid sits on a known substrate regardless of original terrain.
        for (int[] o : footprint) {
            BlockPos floor = origin.offset(o[0], -(depth + 1), o[1]);
            level.setBlock(floor, config.rimBlock().getState(random, floor), 2);
        }
        // Step 3: fill the carved volume with fluid. y = -1 is the fluid surface, level with
        // the surrounding ground.
        for (int dy = 1; dy <= depth; dy++) {
            for (int[] o : footprint) {
                level.setBlock(origin.offset(o[0], -dy, o[1]), config.fluid(), 2);
            }
        }
        return true;
    }

    /**
     * The set of {@code {dx, dz}} offsets the pool occupies, in a deterministic order.
     *
     * <p><b>Containment invariant.</b> Every returned offset satisfies
     * {@code dx² + dz² <= (radius + 0.5)²} — the footprint is always a <em>subset</em> of the
     * plain radius-{@code radius} disc, for every {@code irregularity}. The wall bounding the
     * water in direction θ therefore sits at {@code rEff(θ) + 1 <= radius + 1}: never further
     * out than where the plain circle put its wall, and on any monotone slope terrain nearer
     * the origin is no lower, so the wall is at least as likely to be solid as before.
     * Modulating the radius <em>outward</em> would do the opposite — carve cells beyond
     * anything the placement filter vetted, whose neighbours' surface height is unknown — and
     * that is how a pool starts draining down a hillside. So the wave only ever removes cells.
     *
     * <p>The one case where a bite is not strictly better: a disc cell that is <em>not</em>
     * solid at the fluid's depth (a surface-breaking cave, a ravine) and that the bite now
     * excludes. The circle used to force-carve and force-floor that cell, sealing it; leaving
     * it alone can let fluid out sideways. The plain circle already leaked in that terrain
     * (past its own edge), so this moves where a leak can appear rather than introducing one
     * on the flat ground the feature is meant for.
     *
     * <p><b>Connectivity.</b> The threshold is radial (one edge distance per direction), so the
     * continuous region is star-shaped about the centre. Discretisation can in principle pinch
     * a diagonal, so the result is additionally pruned to the 4-connected component containing
     * the centre. Pruning only removes cells, so it cannot break the containment invariant.
     *
     * <p><b>Determinism.</b> {@code shapeSeed} is the only source of variation, and the whole
     * computation is IEEE {@code + - * /} plus {@code sqrt} (all exactly specified by the JLS)
     * — no trigonometric calls, no {@link RandomSource}. The same seed and radius always yield
     * the same list, on any machine, on every regeneration of the chunk.
     *
     * @param radius       nominal (maximum) radius in blocks
     * @param irregularity 0 = exact circle, 1 = deepest bite
     * @param shapeSeed    see {@link #shapeSeed}
     * @return offsets in the footprint; never empty (always contains {@code {0, 0}})
     * @since 2.1.0
     */
    public static List<int[]> footprint(int radius, double irregularity, long shapeSeed) {
        List<int[]> raw = new ArrayList<>();
        if (irregularity <= 0.0) {
            double edge = radius + 0.5;
            double edgeSq = edge * edge;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= edgeSq) raw.add(new int[]{dx, dz});
                }
            }
            return raw;                                 // exact legacy circle, bit for bit
        }

        // Per-harmonic phase as a unit vector (cos φ, sin φ), drawn without any trig call:
        // sample a point in the unit square, reject outside the unit disc, normalise.
        double[] cosPhase = new double[HARMONICS.length];
        double[] sinPhase = new double[HARMONICS.length];
        for (int k = 0; k < HARMONICS.length; k++) {
            long h = mix(shapeSeed + 0x9E3779B97F4A7C15L * (k + 1));
            double cx = 1.0;
            double cy = 0.0;
            for (int attempt = 0; attempt < 16; attempt++) {
                double u = unitDouble(h) * 2.0 - 1.0;
                h = mix(h);
                double v = unitDouble(h) * 2.0 - 1.0;
                h = mix(h);
                double m2 = u * u + v * v;
                if (m2 > 1.0e-6 && m2 <= 1.0) {
                    double m = Math.sqrt(m2);
                    cx = u / m;
                    cy = v / m;
                    break;
                }
            }
            cosPhase[k] = cx;
            sinPhase[k] = cy;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distSq = (double) dx * dx + (double) dz * dz;
                double edge = edgeRadius(dx, dz, radius, irregularity, cosPhase, sinPhase) + 0.5;
                if (distSq <= edge * edge) raw.add(new int[]{dx, dz});
            }
        }
        return keepCentreComponent(raw);
    }

    /**
     * Effective radius along the direction of {@code (dx, dz)}. Always in
     * {@code [min(radius, 1), radius]}, so the outline never grows past {@code radius}.
     */
    private static double edgeRadius(int dx, int dz, int radius, double irregularity,
                                     double[] cosPhase, double[] sinPhase) {
        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (dist == 0.0) return radius;
        // (cos θ, sin θ) of this cell's bearing; cos/sin of kθ then follow from the angle
        // addition recurrence, so harmonics cost multiplications instead of trig.
        double c = dx / dist;
        double s = dz / dist;
        double ck = c;
        double sk = s;
        double wave = 0.0;
        for (int k = 0; k < HARMONICS.length; k++) {
            wave += HARMONICS[k] * (sk * cosPhase[k] + ck * sinPhase[k]);   // sin(kθ + φ_k)
            double cNext = ck * c - sk * s;
            double sNext = sk * c + ck * s;
            ck = cNext;
            sk = sNext;
        }
        double bite = 0.5 * (wave + 1.0);               // [0, 1]
        double eff = radius * (1.0 - irregularity * bite);
        return Math.max(Math.min(radius, 1.0), eff);
    }

    /** Drop any cell not 4-connected to {@code {0, 0}}, preserving the input order. */
    private static List<int[]> keepCentreComponent(List<int[]> cells) {
        Set<Long> present = new HashSet<>();
        for (int[] o : cells) present.add(key(o[0], o[1]));
        Set<Long> reached = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        if (present.contains(key(0, 0))) {
            reached.add(key(0, 0));
            queue.add(new int[]{0, 0});
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] step : steps) {
                int nx = cur[0] + step[0];
                int nz = cur[1] + step[1];
                long k = key(nx, nz);
                if (present.contains(k) && reached.add(k)) queue.add(new int[]{nx, nz});
            }
        }
        List<int[]> out = new ArrayList<>(reached.size());
        for (int[] o : cells) {
            if (reached.contains(key(o[0], o[1]))) out.add(o);
        }
        return out;
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    /**
     * Shape seed for a pool anchored at {@code (x, z)} in a world of seed {@code worldSeed}.
     *
     * <p>Deliberately derived from the world seed and the block position rather than from the
     * placement {@link RandomSource}: the outline is then independent of how many random draws
     * happened earlier in the same placement, and this feature does not perturb the random
     * stream that later features in the same step consume.
     *
     * @since 2.1.0
     */
    public static long shapeSeed(long worldSeed, int x, int z) {
        return mix(worldSeed
                ^ (x * 0x9E3779B97F4A7C15L)
                ^ (z * 0xC2B2AE3D27D4EB4FL));
    }

    /** SplitMix64 finalizer — full avalanche, no state, identical on every JVM. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The top 53 bits of {@code h} as a double in {@code [0, 1)}. */
    private static double unitDouble(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
