package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.survey.FootprintCheck;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
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
 * {@code /isekai fit <structure> [pos]} — the ground under one chunk and the numbers the placement
 * gates compare it to.
 *
 * <p>Writes {@code <world>/isekai/fit/<namespace>_<path>-<chunkX>_<chunkZ>.json} and prints the 3x3
 * surface grid with the verdict. The chunk comes from {@code pos}, or from where the command was run.
 * See {@link FootprintCheck}.
 */
@ApiStatus.Internal
public final class FitCommand {

    private FitCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fit")
                .then(Commands.argument("structure", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> {
                            ctx.getSource().registryAccess()
                                    .lookupOrThrow(Registries.STRUCTURE)
                                    .listElements()
                                    .forEach(holder -> holder.unwrapKey()
                                            .ifPresent(key -> builder.suggest(key.location().toString())));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> fit(ctx, ctx.getSource().getPosition()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> fit(ctx, BlockPosArgument.getBlockPos(ctx, "pos")
                                        .getCenter()))));
    }

    private static int fit(CommandContext<CommandSourceStack> ctx, net.minecraft.world.phys.Vec3 at) {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "structure");
        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(at));
        try {
            FootprintCheck.Result result = FootprintCheck.run(source.getLevel(),
                    ResourceKey.create(Registries.STRUCTURE, id), chunkPos);
            Path dir = source.getServer().getWorldPath(LevelResource.ROOT).resolve("isekai").resolve("fit");
            Path file = dir.resolve(fileName(id, chunkPos));
            Files.createDirectories(dir);
            Files.writeString(file, result.toJsonString());
            for (String line : result.summary()) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            source.sendSuccess(() -> Component.literal("  written to " + file), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("fit failed: " + e.getMessage()));
            return 0;
        } catch (IOException e) {
            IsekaiApi.LOGGER.error("[Isekai] fit write failed", e);
            source.sendFailure(Component.literal("fit write failed: " + e.getMessage()));
            return 0;
        }
    }

    /** The file name a fit report writes for a structure id and chunk. */
    static String fileName(ResourceLocation structureId, ChunkPos chunkPos) {
        return (structureId.getNamespace() + "_" + structureId.getPath()).replace('/', '_')
                + "-" + chunkPos.x + "_" + chunkPos.z + ".json";
    }
}
