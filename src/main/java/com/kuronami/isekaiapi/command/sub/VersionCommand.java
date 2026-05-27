package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /isekai version} — print mod version. */
public final class VersionCommand {

    private VersionCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("version").executes(ctx -> {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("Isekai API v" + IsekaiApi.VERSION), false);
            return 1;
        });
    }
}
