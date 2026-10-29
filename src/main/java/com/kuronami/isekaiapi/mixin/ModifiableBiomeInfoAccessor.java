package com.kuronami.isekaiapi.mixin;

import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to {@link ModifiableBiomeInfo}'s cached modified info.
 *
 * <p>NeoForge only exposes {@code get()} / {@code getModifiedBiomeInfo()}; the backing field is
 * private and has no setter. The foreign-feature reconcile
 * ({@code com.kuronami.isekaiapi.biomemodifier.ForeignFeatureReconciler}) runs at the tail of
 * {@code ServerLifecycleHooks.runModifiers} and must replace the generation settings the pass
 * produced, so it needs to write that field. Doing it here keeps the reflection out of the
 * reconciler and lets Mixin verify the field exists at load time ({@code required: true}).
 */
@Mixin(ModifiableBiomeInfo.class)
public interface ModifiableBiomeInfoAccessor {

    @Accessor("modifiedBiomeInfo")
    void isekai$setModifiedBiomeInfo(ModifiableBiomeInfo.BiomeInfo info);
}
