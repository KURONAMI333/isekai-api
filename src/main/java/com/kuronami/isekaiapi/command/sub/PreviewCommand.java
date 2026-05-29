package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai preview}:
 * <ul>
 *   <li>{@code range <id> [dim]} — inspect the resolved VerticalRange for a PlacedFeature</li>
 *   <li>{@code column <dim> <x> <z>} — sample the chunk generator's base column at (x, z)
 *       and report what block sits at each playable-band Y, so you can see immediately
 *       whether terrain is generating in the worldshape's active band.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class PreviewCommand {

    private PreviewCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("preview")
                .then(Commands.literal("range")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(PreviewCommand::previewAllDims)
                                .then(Commands.argument("dim", ResourceLocationArgument.id())
                                        .executes(PreviewCommand::previewSpecificDim))))
                .then(Commands.literal("column")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(PreviewCommand::previewColumn)))));
    }

    /**
     * Sample the dimension's base chunk-generator column at (x, z) and dump 1-per-8Y the
     * block type at that altitude. Sampling cadence is wide enough to fit in a single chat
     * message yet dense enough to see the active-band shape clearly. Reports "AIR" for the
     * void and the block's registry id for solid hits — distinguishing "stone island here"
     * from "stone but at unexpected Y" is the usual debugging question.
     */
    private static int previewColumn(CommandContext<CommandSourceStack> ctx) {
        var dimId = ResourceLocationArgument.getId(ctx, "dim");
        var dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        var server = ctx.getSource().getServer();
        var level = server.getLevel(dimKey);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("No loaded level: " + dimId));
            return 0;
        }
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        var column = generator.getBaseColumn(x, z, level, randomState);

        var descriptor = Isekai.remap().getActiveDescriptor(dimKey);
        int minY = descriptor.map(d -> d.playableRange().minY()).orElse(level.getMinBuildHeight());
        int maxY = descriptor.map(d -> d.playableRange().maxY()).orElse(level.getMaxBuildHeight());

        StringBuilder sb = new StringBuilder("Column ").append(dimId).append(" @ (")
                .append(x).append(", ?, ").append(z).append("), playable Y=")
                .append(minY).append("..").append(maxY).append(":\n");
        int step = Math.max(1, (maxY - minY) / 24);  // ~24 samples per column dump
        for (int y = maxY; y >= minY; y -= step) {
            BlockState state = column.getBlock(y - level.getMinBuildHeight());
            String tag = state.isAir() ? "AIR" :
                    state.getBlockHolder().unwrapKey()
                            .map(k -> k.location().toString())
                            .orElse(state.getBlock().getDescriptionId());
            sb.append(String.format("  Y=%4d : %s%n", y, tag));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int previewAllDims(CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var key = ResourceKey.create(Registries.PLACED_FEATURE, id);
        var global = Isekai.query().getPlacedFeatureVerticalRange(key);
        if (global.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No range for: " + id));
            return 0;
        }
        // "global" = the snapshot's overworld-build-height resolution; per-dim entries below
        // are only printed when they actually differ.
        ctx.getSource().sendSuccess(() -> Component.literal(
                id + " (global, overworld-resolved): " + global.get()), false);
        var server = ctx.getSource().getServer();
        for (var level : server.getAllLevels()) {
            var dim = level.dimension();
            var perDim = Isekai.query().getPlacedFeatureVerticalRangeInDimension(key, dim);
            if (perDim.isPresent() && !perDim.get().equals(global.get())) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + dim.location() + ": " + perDim.get()), false);
            }
        }
        return 1;
    }

    private static int previewSpecificDim(CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var dimId = ResourceLocationArgument.getId(ctx, "dim");
        var key = ResourceKey.create(Registries.PLACED_FEATURE, id);
        var dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        var range = Isekai.query().getPlacedFeatureVerticalRangeInDimension(key, dimKey);
        if (range.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No range for " + id + " in " + dimId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                id + " in " + dimId + ": " + range.get()), false);
        return 1;
    }
}
