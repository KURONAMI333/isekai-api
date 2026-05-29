package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Comparator;
import java.util.Set;

/**
 * Combined biome filter: a set of explicit {@link ResourceKey biome keys} plus a set of
 * {@link TagKey biome tags}. A biome matches the selection if it's either listed directly
 * or carries any of the named tags.
 *
 * <p>Common tag groups like {@code minecraft:is_overworld} or {@code minecraft:is_forest}
 * collapse a 35+ entry biome enumeration to one line.
 *
 * <p>JSON shapes accepted (codec dispatches by type):
 * <ul>
 *   <li><b>List form</b> (biome IDs only): {@code ["minecraft:plains",
 *       "minecraft:forest"]} — decodes to a selection with those keys and no tags.</li>
 *   <li><b>Object form</b>: {@code {"keys": [...], "tags": ["#minecraft:is_overworld"]}} —
 *       both fields optional, default empty.</li>
 * </ul>
 *
 * <p>Empty selection matches no biome (explicit-opt-in semantics — see
 * {@link com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher} for rationale).
 */
public record BiomeSelection(Set<ResourceKey<Biome>> keys, Set<TagKey<Biome>> tags) {

    public BiomeSelection {
        keys = Set.copyOf(keys);
        tags = Set.copyOf(tags);
    }

    public static final BiomeSelection EMPTY = new BiomeSelection(Set.of(), Set.of());

    public static BiomeSelection ofKeys(Set<ResourceKey<Biome>> keys) {
        return new BiomeSelection(keys, Set.of());
    }

    public static BiomeSelection ofTags(Set<TagKey<Biome>> tags) {
        return new BiomeSelection(Set.of(), tags);
    }

    public boolean isEmpty() {
        return keys.isEmpty() && tags.isEmpty();
    }

    /** Match a {@link Holder Biome holder} against this selection. */
    public boolean matches(Holder<Biome> biome) {
        for (var key : keys) {
            if (biome.is(key)) return true;
        }
        for (var tag : tags) {
            if (biome.is(tag)) return true;
        }
        return false;
    }

    /** Object-form codec: explicit {@code keys} and {@code tags} fields. */
    private static final Codec<BiomeSelection> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.BIOME).listOf().optionalFieldOf("keys", java.util.List.of())
                    .forGetter(s -> s.keys().stream()
                            .sorted(Comparator.comparing(k -> k.location().toString()))
                            .toList()),
            TagKey.codec(Registries.BIOME).listOf().optionalFieldOf("tags", java.util.List.of())
                    .forGetter(s -> s.tags().stream()
                            .sorted(Comparator.comparing(t -> t.location().toString()))
                            .toList())
    ).apply(i, (keys, tags) -> new BiomeSelection(Set.copyOf(keys), Set.copyOf(tags))));

    /**
     * List-form codec: a plain JSON array of biome keys, decoded as
     * {@code BiomeSelection.ofKeys(...)}. Handles worldshape JSON that writes
     * {@code "applies_to": ["minecraft:plains", ...]}.
     */
    private static final Codec<BiomeSelection> LIST_CODEC = ResourceKey.codec(Registries.BIOME).listOf().xmap(
            list -> BiomeSelection.ofKeys(Set.copyOf(list)),
            sel -> sel.keys().stream()
                    .sorted(Comparator.comparing(k -> k.location().toString()))
                    .toList());

    /**
     * Dispatch: try LIST_CODEC first, fall through to OBJECT_CODEC. Encode uses OBJECT_CODEC
     * when tags are present, LIST_CODEC when only keys.
     */
    public static final Codec<BiomeSelection> CODEC = Codec.either(LIST_CODEC, OBJECT_CODEC)
            .xmap(
                    either -> either.map(l -> l, r -> r),
                    sel -> sel.tags().isEmpty()
                            ? com.mojang.datafixers.util.Either.left(sel)
                            : com.mojang.datafixers.util.Either.right(sel)
            );
}
