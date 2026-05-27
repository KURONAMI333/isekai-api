package com.kuronami.isekaiapi.command;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.validation.IsekaiValidator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers {@code /isekai} subcommand tree. v0.1 implements skeleton commands
 * (version, reload, query dimensions). Full suite per spec §5.4 lands incrementally:
 * <ul>
 *   <li>{@code /isekai dump worldgen [dim]} — v0.2</li>
 *   <li>{@code /isekai dump ore <id>} — v0.2</li>
 *   <li>{@code /isekai dump structure <id>} — v0.2</li>
 *   <li>{@code /isekai query worldshape [dim]} — v0.1 partial (dimension list only)</li>
 *   <li>{@code /isekai validate <namespace>} — v0.2</li>
 *   <li>{@code /isekai preview <descriptor_id>} — v1.1</li>
 *   <li>{@code /isekai reload} — v0.1 stub</li>
 * </ul>
 */
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class IsekaiCommand {

    private IsekaiCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("isekai")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("version").executes(ctx -> {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("Isekai API v" + IsekaiApi.VERSION + " (v0.1 skeleton)"), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("[Isekai v0.1 stub] reload requested (no-op; datapack reload pipeline lands v0.2)"), true);
                    IsekaiApi.LOGGER.info("Isekai reload command invoked by {}", ctx.getSource().getTextName());
                    return 1;
                }))
                .then(Commands.literal("query")
                        .then(Commands.literal("dimensions").executes(ctx -> {
                            var dims = Isekai.query().getDimensionsWithWorldshape();
                            if (dims.isEmpty()) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("No WorldshapeDescriptor registered yet"), false);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("Dimensions with worldshape: " + dims), false);
                            }
                            return 1;
                        })))
                .then(Commands.literal("validate")
                        .then(Commands.argument("namespace", StringArgumentType.word()).executes(ctx -> {
                            String ns = StringArgumentType.getString(ctx, "namespace");
                            var result = IsekaiValidator.validateNamespace(ns);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal(String.format("Validated %d files, %d errors found (v0.1 stub)",
                                            result.filesChecked(), result.errorsFound())), false);
                            return result.isOk() ? 1 : 0;
                        })));

        dispatcher.register(root);
        IsekaiApi.LOGGER.info("Isekai commands registered: /isekai version|reload|query dimensions|validate (4 of 7, v0.1)");
    }
}
