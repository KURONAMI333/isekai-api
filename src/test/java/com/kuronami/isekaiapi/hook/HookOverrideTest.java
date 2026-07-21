package com.kuronami.isekaiapi.hook;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate 2(b) — the hook-override physics the whole Wave-3 ergonomics design rests on.
 *
 * <p>The mechanism: Isekai ships a default
 * {@code data/isekai_api/worldgen/density_function/hook/final_density.json}; a consumer overrides
 * the world's terrain shape by placing their own file at the same path in a <em>datapack</em>.
 * {@code RegistryDataLoader.loadContentsFromManager} reads each registry entry via
 * {@code FileToIdConverter.listMatchingResources(resourceManager)}, whose return is
 * {@code Map<ResourceLocation, Resource>} — <b>one Resource per id</b>, the highest-priority
 * pack's. So the later (datapack-tier) pack replaces the mod-jar default; the density function
 * registry loads the override.
 *
 * <p>This test reproduces exactly that resource collapse with a two-pack
 * {@link MultiPackResourceManager} (mod pack first = lower priority, datapack second = higher),
 * both providing {@code hook/final_density.json} with different values, then closes the chain by
 * decoding the winning resource through {@link DensityFunction#DIRECT_CODEC} and evaluating it —
 * proving the value the registry would load flips from the mod default to the consumer override.
 */
class HookOverrideTest {

    private static final ResourceLocation HOOK =
            ResourceLocation.fromNamespaceAndPath("isekai_api", "worldgen/density_function/hook/final_density.json");

    private static final String MOD_DEFAULT  = "{\"type\":\"minecraft:constant\",\"argument\":1.0}";
    private static final String CONSUMER_OVR = "{\"type\":\"minecraft:constant\",\"argument\":-1.0}";

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static PathPackResources pack(String id, Path root) {
        PackLocationInfo loc = new PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty());
        return new PathPackResources(loc, root);
    }

    private static Path writeHook(Path root, String json) throws IOException {
        Path f = root.resolve("data/isekai_api/worldgen/density_function/hook/final_density.json");
        Files.createDirectories(f.getParent());
        Files.writeString(f, json);
        return root;
    }

    private static double evalHook(MultiPackResourceManager rm) throws IOException {
        Resource res = rm.getResourceOrThrow(HOOK);
        String content;
        try (var in = res.open()) {
            content = new String(in.readAllBytes());
        }
        JsonElement json = JsonParser.parseString(content);
        DensityFunction df = DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        return df.compute(new DensityFunction.SinglePointContext(0, 64, 0));
    }

    @Test
    void datapackOverridesModJarHook(@TempDir Path modRoot, @TempDir Path dataRoot) throws IOException {
        writeHook(modRoot, MOD_DEFAULT);
        writeHook(dataRoot, CONSUMER_OVR);

        PathPackResources modPack = pack("isekai_api", modRoot);
        PathPackResources dataPack = pack("consumer_datapack", dataRoot);

        // Baseline: mod pack alone loads the default (+1.0).
        try (MultiPackResourceManager modOnly =
                     new MultiPackResourceManager(PackType.SERVER_DATA, List.of(modPack))) {
            assertEquals(1.0, evalHook(modOnly), 1e-9,
                    "mod pack alone must load the shipped default hook value");
        }

        // Override: datapack listed AFTER the mod pack (higher priority) wins the same id.
        // This is the exact ordering RegistryDataLoader sees when a user datapack sits above
        // mod-jar resources — so the density_function registry would load the consumer override.
        try (MultiPackResourceManager withOverride =
                     new MultiPackResourceManager(PackType.SERVER_DATA, List.of(modPack, dataPack))) {
            assertEquals(-1.0, evalHook(withOverride), 1e-9,
                    "a later datapack providing the same hook id must replace the mod default");

            // And the collapse is single-resource (not a merge / not a stack): exactly the
            // datapack's bytes are what listResources / getResource hand the registry loader.
            Resource top = withOverride.getResourceOrThrow(HOOK);
            String content;
            try (var in = top.open()) {
                content = new String(in.readAllBytes());
            }
            assertTrue(content.contains("-1.0"),
                    "the winning resource must be the datapack's file verbatim");
        }
    }
}
