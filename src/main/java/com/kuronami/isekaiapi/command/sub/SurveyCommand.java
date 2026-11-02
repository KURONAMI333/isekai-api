package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.survey.StructureSurvey;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai survey <structure> [radius]} — how often a structure actually appears, and why it
 * does not.
 *
 * <p>Prints a summary to chat and writes the same numbers as JSON to
 * {@code <world>/isekai/survey/<namespace>_<path>-r<radius>.json}. The radius is in chunks and is
 * counted around the command's own position, so run it where the datapack is meant to place the
 * structure. See {@link StructureSurvey} for what the two rates mean and why nothing is generated to
 * disk.
 */
@ApiStatus.Internal
public final class SurveyCommand {

    /** Radius in chunks used when the command is given no argument. */
    public static final int DEFAULT_RADIUS = 64;

    /** Upper bound, so a typo cannot turn into a minute of work. */
    public static final int MAX_RADIUS = 512;

    private SurveyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("survey")
                .then(Commands.argument("structure", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().registryAccess()
                                    .lookupOrThrow(Registries.STRUCTURE)
                                    .listElements()
                                    .forEach(holder -> holder.unwrapKey()
                                            .ifPresent(key -> builder.suggest(key.location().toString())));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> survey(ctx, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                .executes(ctx -> survey(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int survey(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "structure");
        ChunkPos center = new ChunkPos(BlockPos.containing(source.getPosition()));
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
        try {
            StructureSurvey.Result result = StructureSurvey.run(source.getLevel(), key, center, radius);
            Path dir = source.getServer().getWorldPath(LevelResource.ROOT).resolve("isekai").resolve("survey");
            Path file = dir.resolve(StructureSurvey.fileName(id, radius));
            Files.createDirectories(dir);
            Files.writeString(file, result.toJsonString());
            for (String line : result.summary()) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            source.sendSuccess(() -> Component.literal("  written to " + file), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("survey failed: " + e.getMessage()));
            return 0;
        } catch (IOException e) {
            IsekaiApi.LOGGER.error("[Isekai] survey write failed", e);
            source.sendFailure(Component.literal("survey write failed: " + e.getMessage()));
            return 0;
        }
    }
}
