package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/**
 * {@code /isekai preview range <id> [dim]} — inspect the resolved VerticalRange for a
 * PlacedFeature, globally or per-dim.
 */
public final class PreviewCommand {

    private PreviewCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("preview")
                .then(Commands.literal("range")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(PreviewCommand::previewAllDims)
                                .then(Commands.argument("dim", ResourceLocationArgument.id())
                                        .executes(PreviewCommand::previewSpecificDim))));
    }

    private static int previewAllDims(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var key = ResourceKey.create(Registries.PLACED_FEATURE, id);
        var global = Isekai.query().getOreVerticalRange(key);
        if (global.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No range for: " + id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                id + " (overworld-resolved): " + global.get()), false);
        var server = ctx.getSource().getServer();
        for (var level : server.getAllLevels()) {
            var dim = level.dimension();
            var perDim = Isekai.query().getOreVerticalRangeInDimension(key, dim);
            if (perDim.isPresent() && !perDim.get().equals(global.get())) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  " + dim.location() + ": " + perDim.get()), false);
            }
        }
        return 1;
    }

    private static int previewSpecificDim(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var dimId = ResourceLocationArgument.getId(ctx, "dim");
        var key = ResourceKey.create(Registries.PLACED_FEATURE, id);
        var dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
        var range = Isekai.query().getOreVerticalRangeInDimension(key, dimKey);
        if (range.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No range for " + id + " in " + dimId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                id + " in " + dimId + ": " + range.get()), false);
        return 1;
    }
}
