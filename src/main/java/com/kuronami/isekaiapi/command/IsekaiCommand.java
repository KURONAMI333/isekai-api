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
 * Registers the {@code /isekai} subcommand tree:
 *
 * <ul>
 *   <li>{@code /isekai version} — print mod version</li>
 *   <li>{@code /isekai reload} — trigger {@code server.reloadResources(...)}</li>
 *   <li>{@code /isekai stats} — concise snapshot health report</li>
 *   <li>{@code /isekai query dimensions} — list dimensions with declared worldshape</li>
 *   <li>{@code /isekai query worldshape <dim>} — inspect a single dimension's declaration</li>
 *   <li>{@code /isekai validate <namespace>} — validate every isekai/*.json under that namespace</li>
 *   <li>{@code /isekai preview range <id> [dim]} — show overworld-resolved + per-dim VerticalRange</li>
 *   <li>{@code /isekai dump worldgen} — write the full snapshot to {@code <world>/isekai_dump/worldgen.txt}</li>
 *   <li>{@code /isekai dump ore <id>} — single-feature query</li>
 *   <li>{@code /isekai dump structure <id>} — single-structure query</li>
 * </ul>
 *
 * <p>All subcommands require permission level 2 (operators).
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
                    var server = ctx.getSource().getServer();
                    ctx.getSource().sendSuccess(() ->
                            Component.literal("Isekai reload: triggering datapack reload (this re-runs every reload listener, not just Isekai's)"), true);
                    IsekaiApi.LOGGER.info("Isekai reload command invoked by {}", ctx.getSource().getTextName());
                    // Reload all currently-selected datapacks. This runs every PreparableReloadListener,
                    // including the two IsekaiReloadListener instances. The work happens off-thread; the
                    // command returns immediately.
                    server.reloadResources(server.getPackRepository().getSelectedIds())
                            .exceptionally(ex -> {
                                IsekaiApi.LOGGER.error("Isekai reload failed", ex);
                                ctx.getSource().sendFailure(Component.literal("Reload failed: " + ex.getMessage()));
                                return null;
                            });
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
                .then(Commands.literal("stats").executes(ctx -> {
                    var ores = Isekai.query().getAllOres();
                    var structures = Isekai.query().getAllStructures();
                    int totalMobs = 0;
                    for (var category : net.minecraft.world.entity.MobCategory.values()) {
                        totalMobs += Isekai.query().getMobsByCategory(category).size();
                    }
                    var dimsDeclared = Isekai.remap().getDeclaredDimensions().size();
                    int totalMobsFinal = totalMobs;
                    ctx.getSource().sendSuccess(() -> Component.literal("Isekai snapshot stats:"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "  PlacedFeatures: " + ores.size()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "  Structure placements: " + structures.size()), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "  Mob spawn entries: " + totalMobsFinal), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "  Declared worldshape dimensions: " + dimsDeclared), false);
                    return 1;
                }))
                .then(Commands.literal("preview")
                        .then(Commands.literal("range")
                                .then(Commands.argument("id", ResourceLocationArgument.id()).executes(ctx -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                    var key = net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.PLACED_FEATURE, id);
                                    var global = Isekai.query().getOreVerticalRange(key);
                                    if (global.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("No range for: " + id));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            id + " (overworld-resolved): " + global.get()), false);
                                    // Also show every dim where the resolved range differs.
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
                                }))
                                .then(Commands.argument("dim", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                            ResourceLocation dimId = ResourceLocationArgument.getId(ctx, "dim");
                                            var key = net.minecraft.resources.ResourceKey.create(
                                                    net.minecraft.core.registries.Registries.PLACED_FEATURE, id);
                                            var dimKey = net.minecraft.resources.ResourceKey.create(
                                                    net.minecraft.core.registries.Registries.DIMENSION, dimId);
                                            var range = Isekai.query().getOreVerticalRangeInDimension(key, dimKey);
                                            if (range.isEmpty()) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "No range for " + id + " in " + dimId));
                                                return 0;
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    id + " in " + dimId + ": " + range.get()), false);
                                            return 1;
                                        }))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("namespace", StringArgumentType.word()).executes(ctx -> {
                            String ns = StringArgumentType.getString(ctx, "namespace");
                            var rm = ctx.getSource().getServer().getResourceManager();
                            var result = IsekaiValidator.validateNamespace(ns, rm);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal(String.format("Validated %d files, %d errors found",
                                            result.filesChecked(), result.errorsFound())), false);
                            // Report each error so the operator can see what to fix.
                            for (String err : result.errors()) {
                                ctx.getSource().sendFailure(Component.literal("  " + err));
                            }
                            return result.isOk() ? 1 : 0;
                        })))
                .then(Commands.literal("dump")
                        .then(Commands.literal("worldgen").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            var ores = Isekai.query().getAllOres();
                            var structures = Isekai.query().getAllStructures();
                            Path dumpDir = server.getWorldPath(LevelResource.ROOT).resolve("isekai_dump");
                            Path dumpFile = dumpDir.resolve("worldgen.txt");

                            StringBuilder sb = new StringBuilder();
                            sb.append("=== Isekai API worldgen dump (v0.6) ===\n");
                            sb.append("PlacedFeatures: ").append(ores.size()).append("\n");
                            sb.append("Structure placements: ").append(structures.size()).append("\n\n");
                            sb.append("-- PlacedFeatures --\n");
                            for (var info : ores) {
                                sb.append(info.key().location()).append(" -> ").append(info.range()).append("\n");
                            }
                            sb.append("\n-- Structure placements --\n");
                            for (var info : structures) {
                                sb.append(info.key().location())
                                        .append(" -> ")
                                        .append(info.placement().getClass().getSimpleName())
                                        .append(", biome_tags=").append(info.validBiomes())
                                        .append("\n");
                            }

                            try {
                                Files.createDirectories(dumpDir);
                                Files.writeString(dumpFile, sb.toString());
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("Dumped " + ores.size() + " features + "
                                                + structures.size() + " structure placements to " + dumpFile), false);
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
                                    var matches = Isekai.query().getAllStructures().stream()
                                            .filter(info -> info.key().location().equals(id))
                                            .toList();
                                    if (matches.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "No structure found: " + id));
                                        return 0;
                                    }
                                    // A structure can appear in multiple StructureSets — each yields its own placement entry.
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            id + " — " + matches.size() + " placement(s)"), false);
                                    for (var info : matches) {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "  placement=" + info.placement().getClass().getSimpleName()
                                                        + ", biome_tags=" + info.validBiomes()), false);
                                    }
                                    return 1;
                                }))));

        dispatcher.register(root);
        IsekaiApi.LOGGER.info("Isekai commands registered: /isekai version|reload|query|validate|dump|preview range");
    }
}
