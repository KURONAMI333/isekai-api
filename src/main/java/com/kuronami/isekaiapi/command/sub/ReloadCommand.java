package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /isekai reload} — trigger {@code server.reloadResources(...)}. Runs every
 * PreparableReloadListener including Isekai's own three (worldshape, layered_worldshape,
 * snapshot-refresh). Returns immediately; the work happens off-thread.
 */
public final class ReloadCommand {

    private ReloadCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reload").executes(ctx -> {
            var server = ctx.getSource().getServer();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Isekai reload: triggering datapack reload (this re-runs every reload listener, not just Isekai's)"), true);
            IsekaiApi.LOGGER.info("[Isekai] reload command invoked by {}", ctx.getSource().getTextName());
            server.reloadResources(server.getPackRepository().getSelectedIds())
                    .exceptionally(ex -> {
                        IsekaiApi.LOGGER.error("[Isekai] reload failed", ex);
                        ctx.getSource().sendFailure(Component.literal("Reload failed: " + ex.getMessage()));
                        return null;
                    });
            return 1;
        });
    }
}
