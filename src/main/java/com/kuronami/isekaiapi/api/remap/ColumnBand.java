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
     * Resolve a normalized {@code depth} to an absolute Y for a column whose free space above
     * the body is at {@code topY} and below it at {@code bottomY}. Pure arithmetic — the
     * anchors are resolved by the caller.
     * @since 2.0.0
     */
    public int resolveY(int topY, int bottomY, double depth) {
        if (scale == DepthScale.PROPORTIONAL) {
            return topY - (int) Math.round(depth * (topY - bottomY));
        }
        return anchoredToTop()
                ? topY - (int) Math.round(depth * referenceThickness)
                : bottomY + (int) Math.round((1.0 - depth) * referenceThickness);
    }
}
