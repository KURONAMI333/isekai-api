package com.kuronami.isekaiapi.gametest.ext;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.registry.IsekaiRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the gametest-only SPI variants ({@link CheckerboardZone}, {@link XParityPredicate})
 * under a foreign {@code isekai_api_test} namespace, using the ordinary {@link RegisterEvent}
 * against {@link IsekaiRegistries} keys — exactly what a third-party mod would do.
 *
 * <p>The whole {@code gametest} tree is excluded from the published jar, so this registration is
 * the entire footprint of the "external" mod: Isekai's shipped code contains no reference to
 * these variants. That the JSON below decodes and evaluates (see the SPI extension gametests) is
 * the machine proof that registry membership alone makes a variant usable.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class IsekaiTestExtensions {

    /** Foreign namespace, standing in for a third-party mod id. */
    public static final String TEST_MODID = "isekai_api_test";

    private IsekaiTestExtensions() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        // Each call is a no-op unless this event is for that registry, so both are safe here.
        event.register(IsekaiRegistries.BIOME_ZONE_TYPE,
                ResourceLocation.fromNamespaceAndPath(TEST_MODID, "checkerboard"),
                () -> CheckerboardZone.MAP_CODEC);
        event.register(IsekaiRegistries.SPATIAL_PREDICATE_TYPE,
                ResourceLocation.fromNamespaceAndPath(TEST_MODID, "x_parity"),
                () -> XParityPredicate.MAP_CODEC);
    }
}
