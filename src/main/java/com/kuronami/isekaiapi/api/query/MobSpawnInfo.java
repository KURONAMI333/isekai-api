package com.kuronami.isekaiapi.api.query;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

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
