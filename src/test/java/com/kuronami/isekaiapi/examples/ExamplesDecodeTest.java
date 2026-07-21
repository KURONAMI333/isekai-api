package com.kuronami.isekaiapi.examples;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.lifecycle.IsekaiReloadListener;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate 4: every shipped example worldshape / layered descriptor decodes through the real codecs,
 * which transitively exercise all five registry-backed extension-point dispatchers. This includes
 * {@code moon_world/worldshape.json}, which deliberately keeps the legacy {@code isekai:} prefix —
 * so a green here is an end-to-end proof of the deprecated-alias path on real content.
 */
class ExamplesDecodeTest {

    private static DynamicOps<JsonElement> ops;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        // Full vanilla registry set (includes the dynamic biome registry), so applies_to biome
        // tags / keys and block overrides in the examples resolve.
        ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }

    private static Path examplesDir() {
        // The test run dir varies (moddev may run from a build subdir), so walk up from the
        // working directory looking for the module's examples/ folder (identified by templates/,
        // which is stable across the 1_shape / 2_placement / 3_adaptation reorg).
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            Path candidate = p.resolve("examples");
            if (Files.isDirectory(candidate.resolve("templates"))) {
                return candidate;
            }
        }
        fail("examples/ dir not found by walking up from " + dir);
        throw new AssertionError("unreachable");
    }

    private static <T> void decodeOrFail(Path file, Codec<T> codec) {
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file));
            DataResult<T> r = codec.parse(ops, json);
            r.error().ifPresent(err -> fail(file + ": decode error: " + err.message()));
        } catch (IOException e) {
            fail(file + ": " + e.getMessage());
        }
    }

    @Test void allWorldshapeExamplesDecode() throws IOException {
        List<Path> worldshapes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(examplesDir())) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> {
                    String s = p.toString().replace('\\', '/');
                    // Worldshape descriptors: files under a .../worldshape/ dir, or the loose
                    // top-level worldshape.json skeletons — but NOT layered_worldshape files.
                    return !s.contains("/layered_worldshape/")
                            && (s.contains("/worldshape/") || p.getFileName().toString().equals("worldshape.json"));
                })
                .forEach(worldshapes::add);
        }
        assertTrue(worldshapes.size() >= 4, "expected several worldshape examples, found " + worldshapes);
        for (Path f : worldshapes) {
            decodeOrFail(f, WorldshapeDescriptor.CODEC);
        }
    }

    @Test void allLayeredExamplesDecode() throws IOException {
        List<Path> layered = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(examplesDir())) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().replace('\\', '/').contains("/layered_worldshape/"))
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .forEach(layered::add);
        }
        assertTrue(layered.size() >= 1, "expected at least one layered example, found " + layered);
        for (Path f : layered) {
            decodeOrFail(f, IsekaiReloadListener.LayeredFile.CODEC);
        }
    }
}
