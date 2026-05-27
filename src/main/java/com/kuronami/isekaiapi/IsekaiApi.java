package com.kuronami.isekaiapi;

import com.kuronami.isekaiapi.densityfunction.IsekaiDensityFunctions;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(IsekaiApi.MODID)
public final class IsekaiApi {
    public static final String MODID = "isekai_api";
    public static final String PRESET_NAMESPACE = "isekai_presets";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IsekaiApi(IEventBus modBus) {
        LOGGER.info("Isekai API v{} loading", VERSION);
        IsekaiDensityFunctions.register(modBus);
        // TODO §5.6: ServerAboutToStartEvent -> vanilla rule snapshot
        // TODO §5.6: AddReloadListenerEvent -> datapack reload
        // TODO §5.6: register placement modifiers (surface_relative, fluid_relative, in_block_context)
    }
}
