package com.kuronami.isekaiapi.command;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.command.sub.DumpCommand;
import com.kuronami.isekaiapi.command.sub.PreviewCommand;
import com.kuronami.isekaiapi.command.sub.QueryCommand;
import com.kuronami.isekaiapi.command.sub.ReloadCommand;
import com.kuronami.isekaiapi.command.sub.StatsCommand;
import com.kuronami.isekaiapi.command.sub.ValidateCommand;
import com.kuronami.isekaiapi.command.sub.VersionCommand;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the {@code /isekai} subcommand tree. Each subcommand is implemented in its
 * own {@code command/sub/} class; this orchestrator just composes them.
 *
 * <ul>
 *   <li>{@link VersionCommand}  — {@code /isekai version}</li>
 *   <li>{@link ReloadCommand}   — {@code /isekai reload}</li>
 *   <li>{@link StatsCommand}    — {@code /isekai stats}</li>
 *   <li>{@link QueryCommand}    — {@code /isekai query dimensions} / {@code worldshape <dim>}</li>
 *   <li>{@link ValidateCommand} — {@code /isekai validate <namespace>}</li>
 *   <li>{@link PreviewCommand}  — {@code /isekai preview range <id> [dim]}</li>
 *   <li>{@link DumpCommand}     — {@code /isekai dump worldgen} / {@code ore <id>} / {@code structure <id>}</li>
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
        dispatcher.register(Commands.literal("isekai")
                .requires(src -> src.hasPermission(2))
                .then(VersionCommand.build())
                .then(ReloadCommand.build())
                .then(StatsCommand.build())
                .then(QueryCommand.build())
                .then(ValidateCommand.build())
                .then(PreviewCommand.build())
                .then(DumpCommand.build()));
        IsekaiApi.LOGGER.info("[Isekai] commands registered: /isekai version|reload|stats|query|validate|preview|dump");
    }
}
