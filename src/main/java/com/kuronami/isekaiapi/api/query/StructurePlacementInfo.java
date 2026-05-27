package com.kuronami.isekaiapi.api.query;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.Set;

public record StructurePlacementInfo(
        ResourceKey<Structure> key,
        StructurePlacement placement,
        Set<TagKey<Biome>> validBiomes
) {
    public StructurePlacementInfo {
        validBiomes = Set.copyOf(validBiomes);
    }
}
