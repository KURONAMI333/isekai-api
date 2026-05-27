package com.kuronami.isekaiapi;

import com.kuronami.isekaiapi.densityfunction.IsekaiDensityFunctions;
import com.kuronami.isekaiapi.placementmodifier.IsekaiPlacementModifiers;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(IsekaiApi.MODID)
public final class IsekaiApi {
    public static final String MODID = "isekai_api";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IsekaiApi(IEventBus modBus) {
        LOGGER.info("Isekai API v{} loading", VERSION);
        IsekaiDensityFunctions.register(modBus);
        IsekaiPlacementModifiers.register(modBus);
        // v0.2: vanilla rule snapshot scanner + datapack reload pipeline.
    }
}
