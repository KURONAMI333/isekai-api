package com.kuronami.isekaiapi.api.query;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * One biome mob-spawn entry: an entity type, its spawn category, and the weight / pack-size
 * bounds vanilla uses when rolling spawns. {@code min} must be &ge; 1 and {@code max} &ge;
 * {@code min}.
 * @since 1.0.0
 */
public record MobSpawnInfo(
        EntityType<?> type,
        MobCategory category,
        int weight,
        int min,
        int max
) {
    public MobSpawnInfo {
        if (min < 1) {
            throw new IllegalArgumentException("min must be >= 1, got " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException("max (" + max + ") < min (" + min + ")");
        }
    }
}
