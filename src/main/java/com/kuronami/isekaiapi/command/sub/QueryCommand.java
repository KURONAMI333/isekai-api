package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.api.Isekai;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai query}:
 * <ul>
 *   <li>{@code dimensions} — list declared dimensions</li>
 *   <li>{@code worldshape <dim>} — inspect a single dimension's declaration</li>
 * </ul>
 */
@ApiStatus.Internal
public final class QueryCommand {

    private QueryCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("query")
                .then(Commands.literal("dimensions").executes(ctx -> {
                    var dims = Isekai.remap().getDeclaredDimensions();
                    if (dims.isEmpty()) {
                        ctx.getSource().sendSuccess(() ->
                                Component.literal("No WorldshapeDescriptor registered yet"), false);
                    } else {
                        ctx.getSource().sendSuccess(() ->
                                Component.literal("Dimensions with worldshape: " + dims), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("worldshape")
                        .then(Commands.argument("dim", ResourceLocationArgument.id()).executes(ctx -> {
                            var dimId = ResourceLocationArgument.getId(ctx, "dim");
                            var dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
                            var single = Isekai.remap().getActiveDescriptor(dimKey);
                            var layers = Isekai.remap().getActiveLayers(dimKey);
                            if (single.isEmpty() && layers.isEmpty()) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("No worldshape declared for " + dimId), false);
                                return 0;
                            }
                            StringBuilder sb = new StringBuilder("Worldshape for ").append(dimId).append(": ");
                            single.ifPresent(d -> sb.append("single-layer range=")
                                    .append(d.playableRange())
                                    .append(", priority=").append(d.priority()));
                            if (!layers.isEmpty()) {
                                if (single.isPresent()) sb.append(" + ");
                                sb.append(layers.size()).append("-layer stack");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        })))
                .then(Commands.literal("atmosphere")
                        .then(Commands.argument("biome", ResourceLocationArgument.id()).executes(QueryCommand::queryAtmosphere)));
    }

    /**
     * {@code /isekai query atmosphere <biome>} — dumps a biome's resolved special-effects
     * (sky_color, fog_color, water_color, foliage_color, grass_color) so consumers can
     * verify that their {@code atmosphere} overrides actually took effect after a reload.
     * Uses the live BIOME registry, so the values reported are after Isekai biome modifiers
     * have run.
     */
    private static int queryAtmosphere(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var biomeId = ResourceLocationArgument.getId(ctx, "biome");
        var biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
        var server = ctx.getSource().getServer();
        var registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var holder = registry.get(biomeKey).orElse(null);
        if (holder == null) {
            ctx.getSource().sendFailure(Component.literal("No biome registered: " + biomeId));
            return 0;
        }
        var biome = holder.value();
        var effects = biome.getSpecialEffects();
        StringBuilder sb = new StringBuilder("Atmosphere for ").append(biomeId).append(":\n");
        sb.append("  sky_color: ").append(formatColor(effects.getSkyColor())).append('\n');
        sb.append("  fog_color: ").append(formatColor(effects.getFogColor())).append('\n');
        sb.append("  water_color: ").append(formatColor(effects.getWaterColor())).append('\n');
        sb.append("  water_fog_color: ").append(formatColor(effects.getWaterFogColor())).append('\n');
        effects.getFoliageColorOverride().ifPresent(c ->
                sb.append("  foliage_color (override): ").append(formatColor(c)).append('\n'));
        effects.getGrassColorOverride().ifPresent(c ->
                sb.append("  grass_color (override): ").append(formatColor(c)).append('\n'));
        sb.append("  temperature: ").append(biome.getBaseTemperature()).append('\n');
        sb.append("  has_precipitation: ").append(biome.hasPrecipitation());
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** Format an integer RGB as both decimal (matches JSON authoring) and #RRGGBB hex. */
    private static String formatColor(int rgb) {
        return rgb + " (#" + String.format("%06X", rgb & 0xFFFFFF) + ")";
    }
}
