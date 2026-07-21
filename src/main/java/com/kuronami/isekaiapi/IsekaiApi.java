package com.kuronami.isekaiapi;

import com.kuronami.isekaiapi.biomemodifier.IsekaiBiomeModifiers;
import com.kuronami.isekaiapi.structuremodifier.IsekaiStructureModifiers;
import com.kuronami.isekaiapi.densityfunction.IsekaiDensityFunctions;
import com.kuronami.isekaiapi.feature.IsekaiFeatures;
import com.kuronami.isekaiapi.placementmodifier.IsekaiPlacementModifiers;
import com.kuronami.isekaiapi.structure.IsekaiStructures;
import com.kuronami.isekaiapi.surfacerule.IsekaiSurfaceRules;
import com.kuronami.isekaiapi.biomesource.IsekaiBiomeSources;
import com.kuronami.isekaiapi.tree.IsekaiTreePlacers;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(IsekaiApi.MODID)
public final class IsekaiApi {
    public static final String MODID = "isekai_api";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mod version, read from this mod's {@link ModContainer} at construction (single source of
     * truth = the {@code neoforge.mods.toml} version), so it can never drift from the published
     * artifact the way a hardcoded constant did. {@code "unknown"} only if accessed before the
     * constructor runs, which the mod's own code never does.
     */
    private static volatile String version = "unknown";

    /** @return this mod's version string (e.g. {@code "1.1.0"}). */
    public static String version() {
        return version;
    }

    public IsekaiApi(IEventBus modBus, ModContainer container) {
        version = container.getModInfo().getVersion().toString();
        LOGGER.info("[Isekai] loading v{}", version);
        IsekaiDensityFunctions.register(modBus);
        IsekaiPlacementModifiers.register(modBus);
        IsekaiBiomeModifiers.register(modBus);
        IsekaiStructureModifiers.register(modBus);
        IsekaiSurfaceRules.register(modBus);
        IsekaiBiomeSources.register(modBus);
        IsekaiTreePlacers.register(modBus);
        IsekaiFeatures.register(modBus);
        IsekaiStructures.register(modBus);
    }
}
