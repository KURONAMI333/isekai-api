package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.survey.BiomeSurvey;
import com.kuronami.isekaiapi.survey.BiomeSurveyRun;
import com.kuronami.isekaiapi.survey.BlockSurvey;
import com.kuronami.isekaiapi.survey.BlockSurveyRun;
import com.kuronami.isekaiapi.survey.StructureSurvey;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /isekai survey} — the questions an author asks when a datapack does not show up.
 *
 * <ul>
 *   <li>{@code /isekai survey <structure> [radius]} — how often a structure actually appears, and
 *       why it does not. Nothing is generated; see {@link StructureSurvey}.</li>
 *   <li>{@code /isekai survey blocks <block|#tag> [radius] [center] [biome <biome|#tag>]} — how many
 *       trees (or other blocks) each generated chunk really got. This one generates the square to
 *       {@code FEATURES}, one chunk per tick, and writes the terrain to disk like a player walking
 *       there; see {@link BlockSurvey} for what "one tree" means. Generation is synchronous on the
 *       server thread (tens of ms per fresh chunk), so TPS drops while it runs and only one survey
 *       runs at a time.</li>
 *   <li>{@code /isekai survey biomes <radius> [center <x y z>] [y <y>]} — how much of the disc is
 *       each biome, how big the patches are, and where the six climate axes sit. Nothing is
 *       generated: the biome source and the climate sampler answer per coordinate, so it runs on a
 *       worker thread while the server ticks; see {@link BiomeSurvey}.</li>
 * </ul>
 *
 * <p>All three write JSON to {@code <world>/isekai/survey/} and print the same numbers to chat.
 */
@ApiStatus.Internal
public final class SurveyCommand {

    /** Radius in chunks used by the structure survey when the command is given no argument. */
    public static final int DEFAULT_RADIUS = 64;

    /**
     * Upper bound for the structure survey. 128 is a 257x257 square (66,049 chunks), 16 times fewer
     * than 512's 1,050,625. Nothing is generated to disk, but every scanned chunk runs the placement
     * test and every candidate runs {@code Structure#generate}, all on the server thread in one
     * command call — a typo at the old limit could hold the server long enough for the watchdog.
     */
    public static final int MAX_RADIUS = 128;

    private SurveyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("survey")
                .then(blocks(context))
                .then(biomes())
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

    /**
     * {@code blocks <block|#tag> [radius] [center <x y z>] [biome <biome|#tag>]}.
     *
     * <p>{@code center} is the literal keyword followed by a block position; its chunk is the middle
     * of the square, and it defaults to where the command runs. {@code biome} filters chunks by the
     * biome at their centre column, and must follow a radius (Brigadier has one path per optional
     * argument, and keeping {@code biome} after {@code radius} keeps the two forms unambiguous).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> blocks(CommandBuildContext context) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceOrTagArgument.Result<Block>> block =
                Commands.argument("block",
                        ResourceOrTagArgument.resourceOrTag(context, Registries.BLOCK));
        block.executes(ctx -> blocks(ctx, BlockSurvey.DEFAULT_RADIUS_CHUNKS, null, null));

        RequiredArgumentBuilder<CommandSourceStack, Integer> radius = Commands.argument("radius",
                IntegerArgumentType.integer(1, BlockSurvey.MAX_RADIUS_CHUNKS));
        radius.executes(ctx -> blocks(ctx, IntegerArgumentType.getInteger(ctx, "radius"), null, null));
        radius.then(Commands.literal("biome")
                .then(Commands.argument("biome",
                                ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME))
                        .executes(ctx -> blocks(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                null, biomeSet(ctx)))));
        radius.then(Commands.literal("center")
                .then(Commands.argument("center", BlockPosArgument.blockPos())
                        .executes(ctx -> blocks(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                BlockPosArgument.getBlockPos(ctx, "center"), null))
                        .then(Commands.literal("biome")
                                .then(Commands.argument("biome",
                                                ResourceOrTagArgument.resourceOrTag(
                                                        context, Registries.BIOME))
                                        .executes(ctx -> blocks(ctx,
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                BlockPosArgument.getBlockPos(ctx, "center"),
                                                biomeSet(ctx)))))));
        block.then(radius);

        return Commands.literal("blocks").then(block);
    }

    /**
     * {@code biomes <radius> [center <x y z>] [y <y>]}.
     *
     * <p>{@code radius} is required here, unlike the other two surveys, and is in blocks: a biome disc
     * is cheap to read but the radius decides the run's cost (radius 4096 is 3.3 million cells), so a
     * default would hide that. {@code center} defaults to where the command runs. {@code y} defaults
     * to the centre's Y and picks the plane the biome is read at, because vanilla biomes are 3D
     * (cave biomes, the nether's vertical bands), so one plane is always a slice rather than the
     * column.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> biomes() {
        RequiredArgumentBuilder<CommandSourceStack, Integer> radius = Commands.argument("radius",
                IntegerArgumentType.integer(BiomeSurvey.MIN_RADIUS_BLOCKS,
                        BiomeSurvey.MAX_RADIUS_BLOCKS));
        radius.executes(ctx -> biomes(ctx, IntegerArgumentType.getInteger(ctx, "radius"), null, null));

        RequiredArgumentBuilder<CommandSourceStack, Integer> yAfterCenter =
                Commands.argument("y", IntegerArgumentType.integer());
        yAfterCenter.executes(ctx -> biomes(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                BlockPosArgument.getBlockPos(ctx, "center"),
                IntegerArgumentType.getInteger(ctx, "y")));
        radius.then(Commands.literal("center")
                .then(Commands.argument("center", BlockPosArgument.blockPos())
                        .executes(ctx -> biomes(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                BlockPosArgument.getBlockPos(ctx, "center"), null))
                        .then(Commands.literal("y").then(yAfterCenter))));

        RequiredArgumentBuilder<CommandSourceStack, Integer> yOnly =
                Commands.argument("y", IntegerArgumentType.integer());
        yOnly.executes(ctx -> biomes(ctx, IntegerArgumentType.getInteger(ctx, "radius"), null,
                IntegerArgumentType.getInteger(ctx, "y")));
        radius.then(Commands.literal("y").then(yOnly));

        return Commands.literal("biomes").then(radius);
    }

    private static int biomes(CommandContext<CommandSourceStack> ctx, int radius,
                              @Nullable BlockPos center, @Nullable Integer y) {
        CommandSourceStack source = ctx.getSource();
        BlockPos middle = center != null ? center : BlockPos.containing(source.getPosition());
        int planeY = y != null ? y : middle.getY();
        BiomeSurvey.Params params =
                new BiomeSurvey.Params(middle.getX(), planeY, middle.getZ(), radius);
        ServerLevel level = source.getLevel();
        String refusal = BiomeSurveyRun.refusalReason(level.getServer());
        if (refusal != null) {
            source.sendFailure(Component.literal("survey biomes: " + refusal));
            return 0;
        }
        int started = BiomeSurveyRun.start(level, params,
                result -> reportBiomes(source, params, result),
                error -> source.sendFailure(Component.literal("survey biomes failed: "
                        + error.getMessage())));
        if (started != 1) {
            source.sendFailure(Component.literal("survey biomes could not start"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("survey biomes: reading radius " + radius
                + " blocks around " + middle.getX() + "," + planeY + "," + middle.getZ()
                + " on a worker thread; nothing is generated, so the server keeps ticking."), false);
        return 1;
    }

    private static void reportBiomes(CommandSourceStack source, BiomeSurvey.Params params,
                                     BiomeSurvey.Result result) {
        Path file = null;
        try {
            Path dir = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("isekai").resolve("survey");
            Files.createDirectories(dir);
            file = dir.resolve(BiomeSurvey.fileName(params));
            Files.writeString(file, result.toJsonString());
        } catch (IOException e) {
            IsekaiApi.LOGGER.error("[Isekai] survey write failed", e);
            source.sendFailure(Component.literal("survey write failed: " + e.getMessage()));
        }
        for (String line : result.summary()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (file != null) {
            Path written = file;
            source.sendSuccess(() -> Component.literal("  written to " + written), false);
        }
    }

    private static int blocks(CommandContext<CommandSourceStack> ctx, int radius,
                              @Nullable BlockPos center, @Nullable HolderSet<Biome> biomes)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceOrTagArgument.Result<Block> target =
                ResourceOrTagArgument.getResourceOrTag(ctx, "block", Registries.BLOCK);
        HolderSet<Block> targetSet = target.unwrap().map(holder -> HolderSet.direct(holder), named -> named);
        Predicate<BlockState> matches = state -> state.is(targetSet);

        BlockPos middle = center != null ? center : BlockPos.containing(source.getPosition());
        ChunkPos centerChunk = new ChunkPos(middle);
        ServerLevel level = source.getLevel();
        String refusal = BlockSurveyRun.refusalReason(level.getServer());
        if (refusal != null) {
            source.sendFailure(Component.literal("survey blocks " + target.asPrintable() + ": "
                    + refusal));
            return 0;
        }
        BlockSurvey.Params params = new BlockSurvey.Params(target.asPrintable(), matches, radius,
                centerChunk, biomes);

        int queued = BlockSurveyRun.start(level, params, result -> report(source, params, result),
                error -> source.sendFailure(Component.literal("survey failed: " + error.getMessage())));
        source.sendSuccess(() -> Component.literal("survey blocks " + target.asPrintable() + ": generating "
                + queued + " chunks around chunk " + centerChunk.x + "," + centerChunk.z
                + " (radius " + radius + "), one chunk per tick at best. Generation runs on the"
                + " server thread and a fresh chunk costs tens of ms, so TPS drops while this runs."),
                false);
        return 1;
    }

    private static void report(CommandSourceStack source, BlockSurvey.Params params,
                               BlockSurvey.Result result) {
        Path file = null;
        try {
            Path dir = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("isekai").resolve("survey");
            Files.createDirectories(dir);
            file = dir.resolve(BlockSurvey.fileName(params));
            Files.writeString(file, result.toJsonString());
        } catch (IOException e) {
            IsekaiApi.LOGGER.error("[Isekai] survey write failed", e);
            source.sendFailure(Component.literal("survey write failed: " + e.getMessage()));
        }
        for (String line : result.summary()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (file != null) {
            Path written = file;
            source.sendSuccess(() -> Component.literal("  written to " + written), false);
        }
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

    private static HolderSet<Biome> biomeSet(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceOrTagArgument.Result<Biome> result =
                ResourceOrTagArgument.getResourceOrTag(ctx, "biome", Registries.BIOME);
        return result.unwrap().map(holder -> HolderSet.direct(holder), named -> named);
    }
}
