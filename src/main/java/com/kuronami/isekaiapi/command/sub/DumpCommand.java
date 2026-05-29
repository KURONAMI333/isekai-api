package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai dump}:
 * <ul>
 *   <li>{@code worldgen} — write the full snapshot to {@code <world>/isekai_dump/worldgen.txt}</li>
 *   <li>{@code ore <id>} — single-feature query</li>
 *   <li>{@code structure <id>} — single-structure query</li>
 * </ul>
 */
@ApiStatus.Internal
public final class DumpCommand {

    private DumpCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dump")
                .then(Commands.literal("worldgen").executes(DumpCommand::dumpWorldgen))
                .then(Commands.literal("ore")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(DumpCommand::dumpOre)))
                .then(Commands.literal("structure")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(DumpCommand::dumpStructure)));
    }

    private static int dumpWorldgen(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        var features = Isekai.query().getAllPlacedFeatures();
        var structures = Isekai.query().getAllStructures();
        Path dumpDir = server.getWorldPath(LevelResource.ROOT).resolve("isekai_dump");
        Path dumpFile = dumpDir.resolve("worldgen.txt");

        StringBuilder sb = new StringBuilder();
        sb.append("=== Isekai API worldgen dump (v").append(IsekaiApi.VERSION).append(") ===\n");
        sb.append("PlacedFeatures: ").append(features.size()).append("\n");
        sb.append("Structure placements: ").append(structures.size()).append("\n\n");
        sb.append("-- PlacedFeatures --\n");
        for (var info : features) {
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
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Dumped " + features.size() + " features + "
                            + structures.size() + " structure placements to " + dumpFile), false);
            return 1;
        } catch (IOException e) {
            IsekaiApi.LOGGER.error("[Isekai] dump worldgen failed", e);
            ctx.getSource().sendFailure(Component.literal("dump worldgen failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int dumpOre(CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var match = Isekai.query().getAllPlacedFeatures().stream()
                .filter(info -> info.key().location().equals(id))
                .findFirst();
        if (match.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No PlacedFeature found: " + id));
            return 0;
        }
        var info = match.get();
        ctx.getSource().sendSuccess(() -> Component.literal(
                info.key().location() + " -> " + info.range() + " (count=" + info.count() + ")"), false);
        return 1;
    }

    private static int dumpStructure(CommandContext<CommandSourceStack> ctx) {
        var id = ResourceLocationArgument.getId(ctx, "id");
        var matches = Isekai.query().getAllStructures().stream()
                .filter(info -> info.key().location().equals(id))
                .toList();
        if (matches.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No structure found: " + id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                id + " — " + matches.size() + " placement(s)"), false);
        for (var info : matches) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  placement=" + info.placement().getClass().getSimpleName()
                            + ", biome_tags=" + info.validBiomes()), false);
        }
        return 1;
    }
}
