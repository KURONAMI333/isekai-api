package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai reload} — trigger {@code server.reloadResources(...)}. Runs every
 * PreparableReloadListener including Isekai's own three (worldshape, layered_worldshape,
 * snapshot-refresh). Returns immediately; the work happens off-thread.
 */
@ApiStatus.Internal
public final class ReloadCommand {

    private ReloadCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reload").executes(ctx -> {
            var server = ctx.getSource().getServer();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Isekai reload: triggering datapack reload..."), true);
            IsekaiApi.LOGGER.info("[Isekai] reload command invoked by {}", ctx.getSource().getTextName());
            server.reloadResources(server.getPackRepository().getSelectedIds())
                    .thenRun(() -> ctx.getSource().sendSuccess(() -> Component.literal(
                            "Reload complete. NOTE: terrain (noise_settings, density functions, " +
                            "world height) only affects newly-generated chunks. Existing chunks " +
                            "keep their old blocks. Create a new world or move far enough to load " +
                            "fresh chunks to see worldgen changes. Biome modifiers (atmosphere, " +
                            "feature predicates, mob spawns) update immediately."), false))
                    .exceptionally(ex -> {
                        IsekaiApi.LOGGER.error("[Isekai] reload failed", ex);
                        ctx.getSource().sendFailure(Component.literal("Reload failed: " + ex.getMessage()));
                        return null;
                    });
            return 1;
        });
    }
}
