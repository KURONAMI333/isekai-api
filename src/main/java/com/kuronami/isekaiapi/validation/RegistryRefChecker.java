package com.kuronami.isekaiapi.validation;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Verifies that every {@link ResourceKey} referenced in a {@link WorldshapeDescriptor}
 * actually exists in the live registry. Catches typos like {@code minecraft:ocean_monument}
 * (the real key is {@code minecraft:monument}) at server-start time rather than letting
 * them silently no-op at chunk generation.
 *
 * <p>Codec-level decode validates registry-key SYNTAX (well-formed RL) but not existence —
 * any well-formed RL parses successfully into a {@code ResourceKey<T>} regardless of
 * whether the registry contains it. This walker performs the existence half of the check.
 *
 * <p>Tags are checked against the registry's tag table: a tag is "valid" if it has at
 * least one entry, since vanilla registers tag definitions only when at least one biome
 * is bound to them.
 */
@ApiStatus.Internal
public final class RegistryRefChecker {

    private RegistryRefChecker() {}

    /**
     * Walk every registry-key reference in {@code d} and return the list of references
     * that don't resolve. Empty list means everything is wired up. Each entry in the
     * returned list is human-readable {@code "<field path>: <missing key>"}.
     */
    public static List<String> findMissing(WorldshapeDescriptor d, MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        var access = server.registryAccess();

        var biomes = access.lookupOrThrow(Registries.BIOME);
        var structures = access.lookupOrThrow(Registries.STRUCTURE);
        var features = access.lookupOrThrow(Registries.PLACED_FEATURE);
        var carvers = access.lookupOrThrow(Registries.CONFIGURED_CARVER);

        // applies_to: biome keys + tags
        for (ResourceKey<Biome> k : d.appliesTo().keys()) {
            if (biomes.get(k).isEmpty()) missing.add("applies_to.keys: " + k.location());
        }
        for (TagKey<Biome> t : d.appliesTo().tags()) {
            checkTag(biomes, t, "applies_to.tags", missing);
        }

        // structure_predicates keys
        for (ResourceKey<Structure> k : d.structurePredicates().keySet()) {
            if (structures.get(k).isEmpty()) missing.add("structure_predicates: " + k.location());
        }

        // feature_predicates keys
        for (ResourceKey<PlacedFeature> k : d.featurePredicates().keySet()) {
            if (features.get(k).isEmpty()) missing.add("feature_predicates: " + k.location());
        }

        // exclusions
        for (ResourceKey<PlacedFeature> k : d.exclusions().features()) {
            if (features.get(k).isEmpty()) missing.add("exclusions.features: " + k.location());
        }
        for (ResourceKey<Structure> k : d.exclusions().structures()) {
            if (structures.get(k).isEmpty()) missing.add("exclusions.structures: " + k.location());
        }
        for (ResourceKey<ConfiguredWorldCarver<?>> k : d.exclusions().carvers()) {
            if (carvers.get(k).isEmpty()) missing.add("exclusions.carvers: " + k.location());
        }

        // additions.features
        for (var af : d.additions().features()) {
            if (features.get(af.feature()).isEmpty()) {
                missing.add("additions.features: " + af.feature().location());
            }
        }
        // additions.carvers
        for (var ac : d.additions().carvers()) {
            if (carvers.get(ac.carver()).isEmpty()) {
                missing.add("additions.carvers: " + ac.carver().location());
            }
        }

        // structure_spawn_overrides
        for (var sso : d.structureSpawnOverrides()) {
            if (structures.get(sso.structure()).isEmpty()) {
                missing.add("structure_spawn_overrides.structure: " + sso.structure().location());
            }
        }
        return missing;
    }

    /**
     * A tag is considered "valid" if the registry's lookup returns a populated holder set.
     * Tags with zero registered members may indicate a typo (vanilla tags always have at
     * least one entry); we still allow them with a softer report.
     */
    private static <T> void checkTag(HolderLookup.RegistryLookup<T> lookup, TagKey<T> tag,
                                      String fieldPath, List<String> missing) {
        var holderSet = lookup.get(tag).orElse(null);
        if (holderSet == null) {
            missing.add(fieldPath + " (unknown tag): " + tag.location());
            return;
        }
        // Vanilla biome tags always have members; an empty tag at this point is suspicious.
        long count = holderSet.stream().count();
        if (count == 0) {
            missing.add(fieldPath + " (empty tag — likely typo): " + tag.location());
        }
    }
}
