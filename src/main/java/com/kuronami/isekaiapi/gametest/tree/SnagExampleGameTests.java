package com.kuronami.isekaiapi.gametest.tree;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance for the bare-tree example shipped as a real datapack file:
 * {@code data/isekai_api_test/worldgen/configured_feature/snag.json}.
 *
 * <p>The file is written only with Isekai's parts — {@code isekai_api:none},
 * {@code isekai_api:branch_stubs}, {@code isekai_api:broken_top} and {@code isekai_api:ground_patch}
 * on top of a vanilla {@code minecraft:tree} — so this test doubles as the proof that a leafless,
 * scorched tree can be authored in a datapack at all. It is loaded by the game as a normal
 * configured feature; the test reads it back from the registry rather than from disk, so a broken
 * codec or an unloaded datapack fails here instead of silently passing.
 *
 * <p>The test also writes text cross-sections of the grown trees next to the worktree, so the shape
 * can be judged by eye without launching a client.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class SnagExampleGameTests {

    /** The datapack entry under {@code data/isekai_api_test/}. */
    private static final ResourceKey<ConfiguredFeature<?, ?>> SNAG = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath("isekai_api_test", "snag"));

    /** Seeds for the three preview trees; fixed so the files are reproducible. */
    private static final long[] SEEDS = {24301L, 24302L, 24303L};

    /** First slot used; the stripped twin of tree 0 uses {@code TWIN_SLOT}. */
    private static final int FIRST_SLOT = 12;

    /** Slot for the decorator-free control tree. */
    private static final int TWIN_SLOT = 16;

    /** Half-width of the preview grid. */
    private static final int PREVIEW_RADIUS = 6;

    private SnagExampleGameTests() {}

    /** The example grows: no leaves, bare branches, and a replaced ground ring. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snagExampleGrowsALeaflessBranchedTree(GameTestHelper helper) {
        ConfiguredFeature<?, ?> snag = snag(helper);
        if (snag == null) {
            return;
        }
        int leaves = 0;
        int branches = 0;
        int patchedGround = 0;
        for (int i = 0; i < SEEDS.length; i++) {
            BlockPos origin = TreeTestScenes.prepare(helper, FIRST_SLOT + i);
            if (!TreeTestScenes.place(helper, snag, origin, SEEDS[i])) {
                helper.fail("snag tree " + i + " did not place at seed " + SEEDS[i]);
                return;
            }
            BlockPos from = TreeTestScenes.boxMin(origin, PREVIEW_RADIUS);
            BlockPos to = TreeTestScenes.boxMax(origin, PREVIEW_RADIUS, 14);
            leaves += TreeTestScenes.count(helper.getLevel(), from, to,
                    state -> state.is(BlockTags.LEAVES));
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                BlockState state = helper.getLevel().getBlockState(pos);
                if (!inTrunkPalette(state)) {
                    continue;
                }
                if (pos.getX() != origin.getX() || pos.getZ() != origin.getZ()) {
                    branches++;
                }
            }
            for (int dx = -PREVIEW_RADIUS; dx <= PREVIEW_RADIUS; dx++) {
                for (int dz = -PREVIEW_RADIUS; dz <= PREVIEW_RADIUS; dz++) {
                    BlockPos ground = new BlockPos(origin.getX() + dx, TreeTestScenes.GROUND_Y, origin.getZ() + dz);
                    if (isPatchBlock(helper.getLevel().getBlockState(ground))) {
                        patchedGround++;
                    }
                }
            }
        }
        if (leaves != 0) {
            helper.fail("the snag example grew " + leaves + " leaf blocks; isekai_api:none should place none");
            return;
        }
        if (branches == 0) {
            helper.fail("no branch stub grew off any of the snag trees");
            return;
        }
        if (patchedGround == 0) {
            helper.fail("ground_patch replaced no ground around the snag trees");
            return;
        }
        helper.succeed();
    }

    /**
     * The example's tops are broken off: with the same seed the decorator-free twin keeps its full
     * trunk, so the difference is the decorator and not the random height.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snagExampleBreaksItsTop(GameTestHelper helper) {
        ConfiguredFeature<?, ?> snag = snag(helper);
        ConfiguredFeature<?, ?> twin = strippedTwin(helper);
        if (snag == null || twin == null) {
            return;
        }
        BlockPos snagOrigin = TreeTestScenes.prepare(helper, FIRST_SLOT);
        BlockPos twinOrigin = TreeTestScenes.prepare(helper, TWIN_SLOT);
        if (!TreeTestScenes.place(helper, snag, snagOrigin, SEEDS[0])
                || !TreeTestScenes.place(helper, twin, twinOrigin, SEEDS[0])) {
            helper.fail("a tree did not place at seed " + SEEDS[0]);
            return;
        }
        int snagTop = topLog(helper, snagOrigin);
        int twinTop = topLog(helper, twinOrigin);
        if (snagTop >= twinTop) {
            helper.fail("broken_top did not shorten the trunk: snag top " + snagTop + ", twin top " + twinTop);
            return;
        }
        helper.succeed();
    }

    /**
     * Writes the cross-sections the parent reviews by eye. Kept in its own test so a missing output
     * directory cannot hide a shape regression, and so the files exist even when only the preview
     * test is re-run.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void snagExampleWritesPreviewSheets(GameTestHelper helper) {
        ConfiguredFeature<?, ?> snag = snag(helper);
        if (snag == null) {
            return;
        }
        Path out = previewDirectory();
        if (out == null) {
            helper.fail("could not find the worktree root to write snag_preview/");
            return;
        }
        List<String> overview = new ArrayList<>();
        overview.add("Cross-sections of the bare-tree example, written by SnagExampleGameTests.");
        overview.add("Regenerate: _tools/gw <worktree> runGameTestServer");
        overview.add("");
        overview.add("legend: L = trunk block, B = branch block, G = replaced ground, g = untouched grass,");
        overview.add("        o = any other block, . = air");
        overview.add("Each sheet is one tree; every y layer is a top-down slice, z from +" + PREVIEW_RADIUS
                + " at the top to -" + PREVIEW_RADIUS + " at the bottom, x left to right.");
        for (int i = 0; i < SEEDS.length; i++) {
            BlockPos origin = TreeTestScenes.prepare(helper, FIRST_SLOT + i);
            if (!TreeTestScenes.place(helper, snag, origin, SEEDS[i])) {
                helper.fail("snag tree " + i + " did not place at seed " + SEEDS[i]);
                return;
            }
            String sheet = sheet(helper, origin, i, SEEDS[i]);
            try {
                Files.createDirectories(out);
                Files.writeString(out.resolve("snag_" + (i + 1) + ".txt"), sheet);
            } catch (IOException e) {
                helper.fail("could not write snag_" + (i + 1) + ".txt under " + out + ": " + e);
                return;
            }
            overview.add("snag_" + (i + 1) + ".txt  seed=" + SEEDS[i] + "  origin=" + origin.toShortString()
                    + "  trunk height=" + (topLog(helper, origin) - origin.getY() + 1));
        }
        try {
            Files.writeString(out.resolve("README.txt"), String.join(System.lineSeparator(), overview)
                    + System.lineSeparator());
        } catch (IOException e) {
            helper.fail("could not write README.txt under " + out + ": " + e);
            return;
        }
        IsekaiApi.LOGGER.info("[Isekai] snag preview sheets written to {}", out.toAbsolutePath());
        helper.succeed();
    }

    /** The example as the game loaded it. */
    private static ConfiguredFeature<?, ?> snag(GameTestHelper helper) {
        var holder = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(SNAG);
        if (holder.isEmpty()) {
            helper.fail("the snag example is not in the configured-feature registry; the datapack file "
                    + "was not loaded (see data/isekai_api_test/worldgen/configured_feature/snag.json)");
            return null;
        }
        return holder.get().value();
    }

    /**
     * The same tree with the decorator array emptied, decoded from the example's own JSON so the
     * two trees differ only by decorators. The trunk height is sampled before decorators run, so
     * equal seeds mean equal heights.
     */
    private static ConfiguredFeature<?, ?> strippedTwin(GameTestHelper helper) {
        String json;
        try {
            Path file = worktreeRoot().resolve("src/main/resources/data/isekai_api_test/worldgen/"
                    + "configured_feature/snag.json");
            json = Files.readString(file);
        } catch (IOException | RuntimeException e) {
            helper.fail("could not read the snag example from disk: " + e);
            return null;
        }
        var root = JsonParser.parseString(json).getAsJsonObject();
        root.getAsJsonObject("config").add("decorators", new com.google.gson.JsonArray());
        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        return ConfiguredFeature.DIRECT_CODEC.parse(ops, root)
                .getOrThrow(message -> new IllegalStateException("twin tree decode failed: " + message));
    }

    /** Y of the highest trunk block in the tree's trunk column. */
    private static int topLog(GameTestHelper helper, BlockPos origin) {
        int top = origin.getY() - 1;
        for (int dy = 0; dy <= 20; dy++) {
            if (inTrunkPalette(helper.getLevel().getBlockState(origin.above(dy)))) {
                top = origin.getY() + dy;
            }
        }
        return top;
    }

    /**
     * The example's trunk is a mix of dark oak and basalt, so a branch is any block of that palette
     * standing off the trunk column — checking only {@code BlockTags.LOGS} would miss basalt stubs.
     */
    private static boolean inTrunkPalette(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(Blocks.BASALT);
    }

    private static boolean isPatchBlock(BlockState state) {
        return state.is(Blocks.COARSE_DIRT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.SOUL_SOIL);
    }

    /** One tree's cross-sections, one block of text per layer. */
    private static String sheet(GameTestHelper helper, BlockPos origin, int index, long seed) {
        StringBuilder out = new StringBuilder();
        out.append("# snag example tree ").append(index + 1)
                .append("  seed=").append(seed)
                .append("  origin=").append(origin.toShortString()).append('\n');
        out.append("# legend: L=trunk block B=branch block G=replaced ground g=grass o=other .=air\n");
        int top = Math.max(topLog(helper, origin), origin.getY() + 1) + 1;
        for (int y = top; y >= TreeTestScenes.GROUND_Y; y--) {
            out.append("y=").append(y).append("  (dy=").append(y - origin.getY()).append(")\n");
            for (int dz = -PREVIEW_RADIUS; dz <= PREVIEW_RADIUS; dz++) {
                out.append(String.format(" %+2d ", dz));
                for (int dx = -PREVIEW_RADIUS; dx <= PREVIEW_RADIUS; dx++) {
                    out.append(symbol(helper, origin, dx, y, dz));
                }
                out.append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static char symbol(GameTestHelper helper, BlockPos origin, int dx, int y, int dz) {
        BlockState state = helper.getLevel().getBlockState(new BlockPos(origin.getX() + dx, y, origin.getZ() + dz));
        if (state.isAir()) {
            return '.';
        }
        if (y == TreeTestScenes.GROUND_Y) {
            if (isPatchBlock(state)) {
                return 'G';
            }
            return state.is(Blocks.GRASS_BLOCK) ? 'g' : 'o';
        }
        if (inTrunkPalette(state)) {
            return dx == 0 && dz == 0 ? 'L' : 'B';
        }
        return 'o';
    }

    /**
     * The directory the sheets are written to, or {@code null} when the worktree cannot be found.
     *
     * <p>The GameTest server runs with {@code run/} as its working directory, so walking up to the
     * build script finds the worktree; the sheets go next to it, where the hand-off documents for
     * this task already live.
     */
    private static Path previewDirectory() {
        Path root = worktreeRoot();
        if (root == null || root.getParent() == null) {
            return null;
        }
        return root.getParent().resolve("snag_preview");
    }

    private static Path worktreeRoot() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("build.gradle"))
                    && Files.isDirectory(p.resolve("src/main/java"))) {
                return p;
            }
        }
        return null;
    }
}
