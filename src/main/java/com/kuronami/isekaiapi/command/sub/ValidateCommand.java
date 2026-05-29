package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.validation.IsekaiValidator;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/** {@code /isekai validate <namespace>} — codec + cross-field validation. */
@ApiStatus.Internal
public final class ValidateCommand {

    private ValidateCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("validate")
                .then(Commands.argument("namespace", StringArgumentType.word()).executes(ctx -> {
                    String ns = StringArgumentType.getString(ctx, "namespace");
                    var rm = ctx.getSource().getServer().getResourceManager();
                    var result = IsekaiValidator.validateNamespace(ns, rm);
                    ctx.getSource().sendSuccess(() ->
                            Component.literal(String.format("Validated %d files, %d errors found",
                                    result.filesChecked(), result.errorsFound())), false);
                    for (String err : result.errors()) {
                        ctx.getSource().sendFailure(Component.literal("  " + err));
                    }
                    return result.isOk() ? 1 : 0;
                }));
    }
}
