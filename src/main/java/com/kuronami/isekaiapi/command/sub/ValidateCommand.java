package com.kuronami.isekaiapi.command.sub;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.impl.wood.validation.WoodSetAudit;
import com.kuronami.isekaiapi.validation.DimensionDiagnostics;
import com.kuronami.isekaiapi.validation.IsekaiValidator;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@code /isekai validate <namespace>} — datapack codec + cross-field validation, plus the wood-set
 * completeness audit (missing loot tables, tags, recipes, blockstates/models/textures and names).
 *
 * <p>{@code /isekai validate dimension <id>} — runtime diagnosis against the dimension's
 * {@code LevelStem} profile and the worldshape currently registered for it (build height,
 * sea level, {@code applies_to}, skipped features, structure-exclusion scope). Exit code is
 * 0 when there is at least one error.
 */
@ApiStatus.Internal
public final class ValidateCommand {

    private ValidateCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("validate")
                .then(Commands.literal("dimension")
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ctx.getSource().getServer().levelKeys().stream()
                                                .map(ResourceKey::location),
                                        builder))
                                .executes(ctx -> validateDimension(ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "dimension")))))
                .then(Commands.argument("namespace", StringArgumentType.word()).executes(ctx -> {
                    String ns = StringArgumentType.getString(ctx, "namespace");
                    var rm = ctx.getSource().getServer().getResourceManager();
                    var result = IsekaiValidator.validateNamespace(ns, rm);
                    // Report what already succeeded first: a crash in the audit must not hide it.
                    ctx.getSource().sendSuccess(() ->
                            Component.literal(String.format("Validated %d files, %d errors found",
                                    result.filesChecked(), result.errorsFound())), false);
                    for (String err : result.errors()) {
                        ctx.getSource().sendFailure(Component.literal("  " + err));
                    }
                    WoodSetAudit.Report wood;
                    try {
                        wood = WoodSetAudit.audit(ns, rm);
                    } catch (RuntimeException e) {
                        IsekaiApi.LOGGER.warn("[Isekai] the wood-set audit for {} failed", ns, e);
                        ctx.getSource().sendFailure(Component.literal(
                                "  [audit] the wood-set audit failed; the log has the cause"));
                        return 0;
                    }
                    if (wood.sets() > 0) {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal(String.format(
                                        "Wood sets: %d set(s), %d checks, %d missing, %d not visible here",
                                        wood.sets(), wood.checks(), wood.findings().size(),
                                        wood.skipped())), false);
                    }
                    for (WoodSetAudit.Finding finding : wood.findings()) {
                        ctx.getSource().sendFailure(Component.literal(
                                "  [" + finding.kind().name().toLowerCase(java.util.Locale.ROOT)
                                        + "] " + finding.message()));
                    }
                    return result.isOk() && wood.isOk() ? 1 : 0;
                }));
    }

    private static int validateDimension(CommandSourceStack source, ResourceLocation id) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        var report = DimensionDiagnostics.validate(source.getServer(), key);

        source.sendSuccess(() -> Component.literal(String.format(
                "Dimension %s: %d error(s), %d warning(s), %d info",
                id, report.count(DimensionDiagnostics.Severity.ERROR),
                report.count(DimensionDiagnostics.Severity.WARN),
                report.count(DimensionDiagnostics.Severity.INFO))), false);
        report.profile().ifPresent(profile -> source.sendSuccess(() -> Component.literal(
                "  build " + profile.minY() + ".." + profile.maxY()
                + " (" + profile.height() + "), sea_level "
                + (profile.seaLevel() == Integer.MIN_VALUE ? "unknown" : profile.seaLevel())
                + ", ceiling=" + profile.hasCeiling()
                + ", default_block=" + profile.defaultBlock()
                    .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
                    .orElse("none")), false));

        for (var issue : report.issues()) {
            Component line = Component.literal("  [" + issue.severity() + "] " + issue.message());
            if (issue.severity() == DimensionDiagnostics.Severity.ERROR) {
                source.sendFailure(line);
            } else {
                source.sendSuccess(() -> line, false);
            }
        }
        return report.isOk() ? 1 : 0;
    }
}
