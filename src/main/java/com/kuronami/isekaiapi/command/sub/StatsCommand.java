package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;
import org.jetbrains.annotations.ApiStatus;

/** {@code /isekai stats} — concise snapshot health report. */
@ApiStatus.Internal
public final class StatsCommand {

    private StatsCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("stats").executes(ctx -> {
            var placedFeatures = Isekai.query().getAllPlacedFeatures();
            var structures = Isekai.query().getAllStructures();
            int totalMobs = 0;
            for (var category : MobCategory.values()) {
                totalMobs += Isekai.query().getMobsByCategory(category).size();
            }
            var dimsDeclared = Isekai.remap().getDeclaredDimensions().size();
            int totalMobsFinal = totalMobs;
            ctx.getSource().sendSuccess(() -> Component.literal("Isekai snapshot stats:"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  PlacedFeatures: " + placedFeatures.size()), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Structure placements: " + structures.size()), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Mob spawn entries: " + totalMobsFinal), false);
            ctx.getSource().sendSuccess(() -> Component.literal("  Declared worldshape dimensions: " + dimsDeclared), false);
            return 1;
        });
    }
}
