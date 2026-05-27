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
 * {@code /isekai query}:
 * <ul>
 *   <li>{@code dimensions} — list declared dimensions</li>
 *   <li>{@code worldshape <dim>} — inspect a single dimension's declaration</li>
 * </ul>
 */
public final class QueryCommand {

    private QueryCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("query")
                .then(Commands.literal("dimensions").executes(ctx -> {
                    var dims = Isekai.remap().getDeclaredDimensions();
                    if (dims.isEmpty()) {
                        ctx.getSource().sendSuccess(() ->
                                Component.literal("No WorldshapeDescriptor registered yet"), false);
                    } else {
                        ctx.getSource().sendSuccess(() ->
                                Component.literal("Dimensions with worldshape: " + dims), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("worldshape")
                        .then(Commands.argument("dim", ResourceLocationArgument.id()).executes(ctx -> {
                            var dimId = ResourceLocationArgument.getId(ctx, "dim");
                            var dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
                            var single = Isekai.remap().getActiveDescriptor(dimKey);
                            var layers = Isekai.remap().getActiveLayers(dimKey);
                            if (single.isEmpty() && layers.isEmpty()) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("No worldshape declared for " + dimId), false);
                                return 0;
                            }
                            StringBuilder sb = new StringBuilder("Worldshape for ").append(dimId).append(": ");
                            single.ifPresent(d -> sb.append("single-layer range=")
                                    .append(d.playableRange())
                                    .append(", priority=").append(d.priority()));
                            if (!layers.isEmpty()) {
                                if (single.isPresent()) sb.append(" + ");
                                sb.append(layers.size()).append("-layer stack");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        })));
    }
}
