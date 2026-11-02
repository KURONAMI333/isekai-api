package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import org.jetbrains.annotations.ApiStatus;

/** One wood set together with the overrides the consumer attached to it. */
@ApiStatus.Internal
public record WoodEntry(WoodSet set, WoodSetDataGen.Overrides overrides) {}
