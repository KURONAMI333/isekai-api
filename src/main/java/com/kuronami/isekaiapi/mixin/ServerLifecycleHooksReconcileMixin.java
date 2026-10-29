package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.biomemodifier.ForeignFeatureReconciler;
import com.kuronami.isekaiapi.impl.IsekaiHealth;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the foreign-feature reconcile at the one point where every biome modifier has been
 * applied and no chunk has been generated yet.
 *
 * <p>NeoForge applies biome modifiers in {@link ServerLifecycleHooks#runModifiers} and posts
 * {@code ServerAboutToStartEvent} immediately after; world generation starts later, in
 * {@code createLevels}/{@code prepareLevels}. A modifier's ADD phase only sees the modifiers
 * that ran before it, so features another mod adds afterwards are missing from our
 * dimension-scoped copies. The TAIL of {@code runModifiers} is the only correct place to pick
 * them up: the final {@code ModifiableBiomeInfo} is built, the registry still has no level,
 * and nothing has been decorated.
 *
 * <p>A failure is logged and marks the health state degraded rather than escaping: this is a
 * compatibility sweep, and a broken third-party modifier must not stop the server from
 * starting. {@code IsekaiApi.LOGGER} is used so the line is attributable.
 */
@Mixin(ServerLifecycleHooks.class)
public abstract class ServerLifecycleHooksReconcileMixin {

    @Inject(method = "runModifiers(Lnet/minecraft/server/MinecraftServer;)V", at = @At("TAIL"))
    private static void isekai$reconcileForeignFeatures(MinecraftServer server, CallbackInfo ci) {
        try {
            ForeignFeatureReconciler.reconcile(server);
        } catch (RuntimeException e) {
            IsekaiApi.LOGGER.error("[Isekai] foreign-feature reconcile failed; features other mods "
                    + "added after our biome modifier pass stay unscoped this session", e);
            IsekaiHealth.markDegraded("foreign-feature reconcile failed: " + e);
        }
    }
}
