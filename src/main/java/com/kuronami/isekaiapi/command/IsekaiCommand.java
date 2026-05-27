package com.kuronami.isekaiapi.command;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.validation.IsekaiValidator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers {@code /isekai} subcommand tree.
 *
 * <p>v0.1 (implemented):
 * <ul>
 *   <li>{@code /isekai version}</li>
 *   <li>{@code /isekai reload} (stub — full datapack reload lands v0.2)</li>
 *   <li>{@code /isekai query dimensions}</li>
 *   <li>{@code /isekai validate <namespace>} (stub — schema validator lands v0.2)</li>
 * </ul>
 *
 * <p>v0.2 (planned):
 * <ul>
 *   <li>{@code /isekai dump worldgen [dim]}</li>
 *   <li>{@code /isekai dump ore <id>}</li>
 *   <li>{@code /isekai dump structure <id>}</li>
 * </ul>
 *
 * <p>v1.1: {@code /isekai preview <descriptor_id>}.
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
                            Component.literal("Isekai API v" + IsekaiApi.VERSION), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("Isekai reload requested (stub; full pipeline lands v0.2)"), true);
                    IsekaiApi.LOGGER.info("Isekai reload command invoked by {}", ctx.getSource().getTextName());
                    return 1;
                }))
                .then(Commands.literal("query")
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
                                    ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dim");
                                    var dimKey = net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DIMENSION, dimId);
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
                                }))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("namespace", StringArgumentType.word()).executes(ctx -> {
                            String ns = StringArgumentType.getString(ctx, "namespace");
                            var result = IsekaiValidator.validateNamespace(ns);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal(String.format("Validated %d files, %d errors found",
                                            result.filesChecked(), result.errorsFound())), false);
                            return result.isOk() ? 1 : 0;
                        })))
                .then(Commands.literal("dump")
                        .then(Commands.literal("worldgen").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            var ores = Isekai.query().getAllOres();
                            Path dumpDir = server.getWorldPath(LevelResource.ROOT).resolve("isekai_dump");
                            Path dumpFile = dumpDir.resolve("worldgen.txt");

                            StringBuilder sb = new StringBuilder();
                            sb.append("=== Isekai API worldgen dump (v0.4) ===\n");
                            sb.append("PlacedFeatures with VerticalRange: ").append(ores.size()).append("\n\n");
                            for (var info : ores) {
                                sb.append(info.key().location()).append(" -> ").append(info.range()).append("\n");
                            }

                            try {
                                Files.createDirectories(dumpDir);
                                Files.writeString(dumpFile, sb.toString());
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("Dumped " + ores.size() + " features to " + dumpFile), false);
                                return 1;
                            } catch (IOException e) {
                                IsekaiApi.LOGGER.error("dump worldgen failed", e);
                                ctx.getSource().sendFailure(Component.literal("dump worldgen failed: " + e.getMessage()));
                                return 0;
                            }
                        }))
                        .then(Commands.literal("ore")
                                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(ctx -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                    var match = Isekai.query().getAllOres().stream()
                                            .filter(info -> info.key().location().equals(id))
                                            .findFirst();
                                    if (match.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "No PlacedFeature found: " + id));
                                        return 0;
                                    }
                                    var info = match.get();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            info.key().location() + " -> " + info.range()
                                                    + " (count=" + info.count() + ")"), false);
                                    return 1;
                                })))
                        .then(Commands.literal("structure")
                                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(ctx -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("[Isekai v0.1 stub] dump structure " + id + " — vanilla rule scanner lands v0.2"), false);
                                    return 1;
                                }))));

        dispatcher.register(root);
        IsekaiApi.LOGGER.info("Isekai commands registered: /isekai version|reload|query dimensions|validate|dump worldgen|dump ore|dump structure");
    }
}
