package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * A vertical band expressed as a <i>depth into the terrain of one column</i> rather than as an
 * absolute Y range. Two {@link SurfaceAnchor}s bracket the column's body — {@link #top()} (the
 * free space above it) and {@link #bottom()} (the free space below it) — and the band occupies
 * normalized depths {@link #fromDepth()}..{@link #toDepth()}, where {@code 0.0} is the top
 * anchor and {@code 1.0} is the bottom anchor.
 *
 * <p>This is what lets one descriptor describe ore placement for terrain whose altitude varies
 * per column: floating islands, orbiting planets, sky continents. An absolute Y band can only
 * ever be right for terrain at one altitude; a ColumnBand resolves against each column as it is
 * placed, so a body at Y=280 and a body at Y=-40 receive the identical internal layout.
 *
 * <p>{@link DepthScale} decides how a normalized depth becomes blocks:
 * <ul>
 *   <li>{@link DepthScale#BLOCKS} — depth is measured in blocks against a fixed
 *       {@link #referenceThickness()}, from whichever anchor the band lies nearer to (see
 *       {@link #anchoredToTop()}). Bodies thicker than the reference keep a hollow middle;
 *       bodies thinner than it clip the band away. This is the conservative mode: an ore's
 *       distance from the surface does not change with the size of the body.</li>
 *   <li>{@link DepthScale#PROPORTIONAL} — depth is a fraction of the column's own thickness,
 *       so the whole layout stretches with the body. A big planet gets a proportionally deeper
 *       core, a small one a shallow one, and no middle is ever left empty.</li>
 * </ul>
 *
 * <p>JSON (as consumed by the {@code isekai_api:column_relative} placement modifier):
 * <pre>{@code
 * {
 *   "type": "isekai_api:column_relative",
 *   "top": { "type": "isekai_api:world_surface" },
 *   "bottom": { "type": "isekai_api:world_floor" },
 *   "from_depth": 0.0156,
 *   "to_depth": 0.1016,
 *   "scale": "blocks",
 *   "reference_thickness": 128,
 *   "distribution": "uniform"
 * }
 * }</pre>
 *
 * @since 2.0.0
 */
public record ColumnBand(
        SurfaceAnchor top,
        SurfaceAnchor bottom,
        double fromDepth,
        double toDepth,
        DepthScale scale,
        int referenceThickness,
        HeightDistribution distribution
) {

    /** Reference body thickness matching vanilla's surface(64)..bedrock(-64) column. @since 2.0.0 */
    public static final int VANILLA_THICKNESS = 128;

    public ColumnBand {
        if (fromDepth > toDepth) {
            throw new IllegalArgumentException("fromDepth (" + fromDepth + ") > toDepth (" + toDepth + ")");
        }
        if (referenceThickness <= 0) {
            throw new IllegalArgumentException("referenceThickness must be > 0: " + referenceThickness);
        }
    }

    /** How a normalized depth is converted into blocks. @since 2.0.0 */
    public enum DepthScale implements StringRepresentable {
        /** Fixed block distance from the nearer anchor, independent of the body's thickness. @since 2.0.0 */
        BLOCKS("blocks"),
        /** Fraction of the column's own thickness, so the layout scales with the body. @since 2.0.0 */
        PROPORTIONAL("proportional");

        /** Serialized as its lowercase name. @since 2.0.0 */
        public static final Codec<DepthScale> CODEC = StringRepresentable.fromEnum(DepthScale::values);

        private final String serializedName;

        DepthScale(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override public String getSerializedName() { return serializedName; }
    }

    /** Payload codec (no {@code "type"} field). @since 2.0.0 */
    public static final MapCodec<ColumnBand> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SurfaceAnchor.CODEC.optionalFieldOf("top", SurfaceAnchor.WorldSurface.INSTANCE)
                    .forGetter(ColumnBand::top),
            SurfaceAnchor.CODEC.optionalFieldOf("bottom", SurfaceAnchor.WorldFloor.DEFAULT)
                    .forGetter(ColumnBand::bottom),
            Codec.DOUBLE.fieldOf("from_depth").forGetter(ColumnBand::fromDepth),
            Codec.DOUBLE.fieldOf("to_depth").forGetter(ColumnBand::toDepth),
            DepthScale.CODEC.optionalFieldOf("scale", DepthScale.BLOCKS).forGetter(ColumnBand::scale),
            Codec.intRange(1, 4096).optionalFieldOf("reference_thickness", VANILLA_THICKNESS)
                    .forGetter(ColumnBand::referenceThickness),
            HeightDistribution.CODEC.optionalFieldOf("distribution", HeightDistribution.UNIFORM)
                    .forGetter(ColumnBand::distribution)
    ).apply(i, ColumnBand::new));

    /** Standalone codec form of {@link #MAP_CODEC}. @since 2.0.0 */
    public static final Codec<ColumnBand> CODEC = MAP_CODEC.codec();

    /**
     * Which end {@link DepthScale#BLOCKS} measures from: the top anchor when the band's midpoint
     * lies in the upper half of the column, otherwise the bottom anchor. Decided once for the
     * whole band (not per sample) so a band never splits into two clusters at opposite ends of a
     * thick body. A band centred exactly on {@code 0.5} is measured from the top.
     * @since 2.0.0
     */
    public boolean anchoredToTop() {
        return (fromDepth + toDepth) / 2.0 <= 0.5;
    }

    /**
     * Draw one normalized depth from this band, honouring {@link #distribution()}. Distributions
     * are stated in terms of Y, so {@code biased_low} (toward low Y) biases toward the
     * <i>deep</i> end of the band and {@code biased_high} toward the shallow end.
     * @since 2.0.0
     */
    public double sampleDepth(RandomSource random) {
        double t = switch (distribution) {
            case UNIFORM -> random.nextDouble();
            // Sum of two uniforms = triangular, matching vanilla's trapezoid with plateau 0.
            case TRAPEZOID, TRIANGLE -> (random.nextDouble() + random.nextDouble()) / 2.0;
            // Low Y is the deep end, so bias toward depth 1.
            case BIASED_LOW -> Math.max(random.nextDouble(), random.nextDouble());
            case BIASED_HIGH -> Math.min(random.nextDouble(), random.nextDouble());
        };
        return fromDepth + t * (toDepth - fromDepth);
    }

    /**
     * Whether this band has collapsed onto one of the two normalized anchors — zero width at
     * depth {@code 0.0} or {@code 1.0}. That is the signature of a source range that lay
     * entirely outside the strategy's vanilla reference column and was flattened by the clamp
     * in {@code ColumnLocal.depthOf}, e.g. vanilla's {@code ore_andesite_upper} (Y 64..128)
     * read against the default reference surface of Y 64: both ends clamp to depth 0.0 and the
     * band names a single plane instead of a span.
     *
     * <p>A zero-width band <i>away</i> from both anchors is legitimate — a source range whose
     * min and max are the same Y is one plane by construction — so it is not reported here.
     *
     * @since 2.1.0
     */
    public boolean collapsedAtAnchor() {
        if (toDepth - fromDepth > DEPTH_EPSILON) {
            return false;
        }
        return fromDepth <= DEPTH_EPSILON || fromDepth >= 1.0 - DEPTH_EPSILON;
    }

    /**
     * Depth widths at or below this are arithmetic collapse, not a thin band. Deliberately far
     * below one block's worth of depth for any plausible body (1/4096), so a genuinely thin
     * band is never mistaken for a collapsed one.
     */
    private static final double DEPTH_EPSILON = 1.0e-9;

    /**
     * Resolve a normalized {@code depth} to an absolute Y for a column whose free space above
     * the body is at {@code topY} and below it at {@code bottomY}. Pure arithmetic — the
     * anchors are resolved by the caller.
     *
     * <p>The result is clamped into the body itself, {@code bottomY + 1 .. topY - 1}. Both
     * anchors name <i>free space</i>, not terrain: {@link SurfaceAnchor.WorldSurface} reports
     * the first air block above the body, so depth {@code 0.0} lands one block too high and
     * depth {@code 1.0} (under {@link DepthScale#PROPORTIONAL}, which resolves to exactly
     * {@code bottomY}) one block too low. Before 2.1.0 both ends were returned unclamped, and a
     * band that had collapsed onto an anchor (see {@link #collapsedAtAnchor()}) therefore aimed
     * every one of its samples at air. Ore features rooted in air place nothing, which is what
     * made vanilla's {@code _upper} stone variants absent from Sky World's islands.
     *
     * <p>Only the endpoints move, and only by one block: a depth that already resolved inside
     * the body resolves to the same Y as before.
     *
     * <p>When the body is thinner than one block the clamp window inverts, and the raw value is
     * returned instead. {@code ColumnRelativeModifier} already drops such columns before
     * calling here ({@code topY - bottomY < 2}), so that branch exists for direct callers only.
     *
     * @since 2.0.0
     */
    public int resolveY(int topY, int bottomY, double depth) {
        int y = scale == DepthScale.PROPORTIONAL
                ? topY - (int) Math.round(depth * (topY - bottomY))
                : anchoredToTop()
                        ? topY - (int) Math.round(depth * referenceThickness)
                        : bottomY + (int) Math.round((1.0 - depth) * referenceThickness);
        int highestSolid = topY - 1;
        int lowestSolid = bottomY + 1;
        if (highestSolid < lowestSolid) {
            return y;
        }
        return Math.max(lowestSolid, Math.min(highestSolid, y));
    }
}
