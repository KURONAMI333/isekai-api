package com.kuronami.isekaiapi.api.wood;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Builds the vanilla {@link TreeGrower} a {@link WoodSet} sapling grows from.
 *
 * <p>A {@code TreeGrower} is just a bag of {@link ConfiguredFeature} keys plus a name, but vanilla
 * only offers a four-argument and an eight-argument constructor that both take
 * {@code Optional} keys. This helper fills those in from the features a species actually has, and
 * derives the name from the wood set id so every grower is unique:
 * {@code "<namespace>/<path>"} of the same id passed to
 * {@link WoodSet#builder(ResourceLocation)}. Two species must not share an id, and this is where
 * that shows up: {@code TreeGrower} keeps a static name map for its codec, so a repeated name
 * would silently make the later grower win that lookup.
 *
 * <p>The configured features referenced here are ordinary datapack or modded worldgen entries with
 * {@code "type": "minecraft:tree"}; this helper does not create them. Leave a slot empty and the
 * grower simply never asks for it — a species without a 2x2 variant is built from {@link
 * Builder#tree} alone.
 *
 * <pre>{@code
 * // A single-shape species.
 * private static final TreeGrower MAPLE_GROWER = TreeGrowers
 *         .builder(ResourceLocation.fromNamespaceAndPath(MODID, "maple"))
 *         .tree(MAPLE_TREE)                       // ResourceKey<ConfiguredFeature<?, ?>>
 *         .build();
 *
 * // A species with a 2x2 variant and a bee-nest variant.
 * private static final TreeGrower PINE_GROWER = TreeGrowers
 *         .builder(ResourceLocation.fromNamespaceAndPath(MODID, "pine"))
 *         .tree(PINE_TREE)
 *         .megaTree(PINE_TALL_TREE)               // grown when four saplings are placed 2x2
 *         .bees(PINE_TREE_BEES)                   // used when a flower is within 2 blocks
 *         .build();
 *
 * public static final WoodSet PINE = WoodSet
 *         .builder(ResourceLocation.fromNamespaceAndPath(MODID, "pine"))
 *         .log().wood().family().leaves()
 *         .sapling(PINE_GROWER)
 *         .register(BLOCKS, ITEMS);
 * }</pre>
 *
 * <p>Build the grower once, in a static initialiser, and pass the same instance to the wood set.
 * The object is immutable after {@link Builder#build()}.
 */
public final class TreeGrowers {

    private TreeGrowers() {}

    /**
     * Starts a grower for the species {@code woodSetId}.
     *
     * @param woodSetId the same id the {@link WoodSet} is built with; it becomes the grower name
     */
    public static Builder builder(ResourceLocation woodSetId) {
        return new Builder(woodSetId);
    }

    /**
     * The grower name used for {@code woodSetId}: {@code "<namespace>/<path>"}.
     *
     * <p>This is the name vanilla's {@code TreeGrower} codec looks growers up by, so it has to be
     * unique per species. Derived from the wood set id for that reason.
     */
    public static String nameFor(ResourceLocation woodSetId) {
        Objects.requireNonNull(woodSetId, "woodSetId");
        return woodSetId.getNamespace() + "/" + woodSetId.getPath();
    }

    /** Mutable builder; one instance per species. */
    public static final class Builder {

        private final ResourceLocation woodSetId;
        private ResourceKey<ConfiguredFeature<?, ?>> tree;
        private ResourceKey<ConfiguredFeature<?, ?>> megaTree;
        private ResourceKey<ConfiguredFeature<?, ?>> secondaryTree;
        private ResourceKey<ConfiguredFeature<?, ?>> secondaryMegaTree;
        private ResourceKey<ConfiguredFeature<?, ?>> bees;
        private ResourceKey<ConfiguredFeature<?, ?>> secondaryBees;
        private float secondaryChance = 0.1F;

        private Builder(ResourceLocation woodSetId) {
            this.woodSetId = Objects.requireNonNull(woodSetId, "woodSetId");
        }

        /** The tree grown from one sapling. Required. */
        public Builder tree(ResourceKey<ConfiguredFeature<?, ?>> tree) {
            this.tree = Objects.requireNonNull(tree, "tree");
            return this;
        }

        /**
         * The tree grown when four saplings of this species are placed 2x2. Optional; without it
         * the 2x2 arrangement grows {@link #tree} like a single sapling.
         */
        public Builder megaTree(ResourceKey<ConfiguredFeature<?, ?>> megaTree) {
            this.megaTree = Objects.requireNonNull(megaTree, "megaTree");
            return this;
        }

        /**
         * A rarer alternative to {@link #tree}, picked with {@link #secondaryChance}. Optional.
         */
        public Builder secondaryTree(ResourceKey<ConfiguredFeature<?, ?>> secondaryTree) {
            this.secondaryTree = Objects.requireNonNull(secondaryTree, "secondaryTree");
            return this;
        }

        /**
         * A rarer alternative to {@link #megaTree}, picked with {@link #secondaryChance}.
         * Optional.
         */
        public Builder secondaryMegaTree(ResourceKey<ConfiguredFeature<?, ?>> secondaryMegaTree) {
            this.secondaryMegaTree = Objects.requireNonNull(secondaryMegaTree, "secondaryMegaTree");
            return this;
        }

        /**
         * The variant grown when a flower is within two blocks of the sapling, i.e. one whose
         * configured feature carries a bee nest. Optional.
         */
        public Builder bees(ResourceKey<ConfiguredFeature<?, ?>> bees) {
            this.bees = Objects.requireNonNull(bees, "bees");
            return this;
        }

        /** The flower variant of {@link #secondaryTree}. Optional. */
        public Builder secondaryBees(ResourceKey<ConfiguredFeature<?, ?>> secondaryBees) {
            this.secondaryBees = Objects.requireNonNull(secondaryBees, "secondaryBees");
            return this;
        }

        /**
         * Probability of picking a secondary shape over the primary one, {@code 0..1}. Defaults to
         * {@code 0.1}, the value vanilla oak uses; it has no effect when no secondary key is set.
         */
        public Builder secondaryChance(float chance) {
            if (!(chance >= 0.0F && chance <= 1.0F)) {
                throw new IllegalArgumentException("secondaryChance must be within [0, 1], got " + chance);
            }
            this.secondaryChance = chance;
            return this;
        }

        /** Builds the grower. */
        public TreeGrower build() {
            if (tree == null) {
                throw new IllegalStateException("no tree configured for wood set " + woodSetId
                        + " — call tree(...) with the configured_feature the sapling grows into");
            }
            return new TreeGrower(
                    nameFor(woodSetId),
                    secondaryChance,
                    Optional.ofNullable(megaTree),
                    Optional.ofNullable(secondaryMegaTree),
                    Optional.of(tree),
                    Optional.ofNullable(secondaryTree),
                    Optional.ofNullable(bees),
                    Optional.ofNullable(secondaryBees));
        }
    }
}
