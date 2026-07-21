package com.kuronami.isekaiapi.examples;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate 4 (real codec decode): the load-bearing literal JSON snippets shown in README.md decode
 * through the actual production codecs — not just "parses as JSON". This is what stops the docs
 * from drifting to non-decodable content while still reading plausibly.
 *
 * <p>Covers the two snippets that map onto Isekai codecs: the worldshape descriptor body
 * (→ {@link WorldshapeDescriptor#CODEC}, which transitively drives SurfaceAnchor / RemapStrategy /
 * SpatialPredicate) and the terrain-shape hook density function
 * (→ {@link DensityFunction#DIRECT_CODEC}). The vocabulary + well-formedness of every other fence
 * is enforced by {@code tools/check_docs.py}; the {@code examples/**} datapacks by
 * {@link ExamplesDecodeTest}. Schema fences with {@code <int>} placeholders are illustrative and
 * intentionally not decoded here.
 */
class DocSnippetDecodeTest {

    private static final Pattern FENCE = Pattern.compile("```jsonc?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static DynamicOps<JsonElement> registryOps;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        registryOps = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }

    private static Path readme() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("examples").resolve("templates"))) {
                return p.resolve("README.md");
            }
        }
        fail("README.md not found by walking up from " + dir);
        throw new AssertionError("unreachable");
    }

    private static List<JsonObject> literalObjects() throws IOException {
        String md = Files.readString(readme());
        List<JsonObject> objs = new ArrayList<>();
        Matcher m = FENCE.matcher(md);
        while (m.find()) {
            String cleaned = LINE_COMMENT.matcher(m.group(1)).replaceAll("").trim();
            if (cleaned.contains("<") || cleaned.contains("...")) {
                continue; // schema/placeholder fence — not literal JSON
            }
            try {
                JsonElement el = JsonParser.parseString(cleaned);
                if (el.isJsonObject()) {
                    objs.add(el.getAsJsonObject());
                }
            } catch (RuntimeException ignored) {
                // fragment (e.g. a bare "key": value) — covered by check_docs.py, skip here
            }
        }
        return objs;
    }

    private static boolean isDensityFunctionType(String type) {
        return type != null && type.startsWith("isekai_api:")
                && (type.contains("squeeze") || type.contains("density")
                    || type.contains("envelope") || type.contains("noise"));
    }

    @Test
    void readmeSnippetsDecodeThroughRealCodecs() throws IOException {
        int worldshapes = 0;
        int densityFunctions = 0;
        for (JsonObject obj : literalObjects()) {
            String type = obj.has("type") ? obj.get("type").getAsString() : null;

            if (obj.has("dimension") && obj.has("playable_range")) {
                // the canonical (ref-form) worldshape descriptor, authored as its own file
                WorldshapeDescriptor.CODEC.parse(registryOps, obj)
                        .getOrThrow(err -> new AssertionError(
                                "README worldshape descriptor did not decode: " + err));
                worldshapes++;
            } else if (isDensityFunctionType(type)) {
                DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, obj)
                        .getOrThrow(err -> new AssertionError(
                                "README hook density_function did not decode: " + err));
                densityFunctions++;
            }
        }
        assertTrue(worldshapes >= 1,
                "expected the README worldshape descriptor example to be present and decode");
        assertTrue(densityFunctions >= 1,
                "expected the terrain-shape hook density_function README example to decode");
    }
}
