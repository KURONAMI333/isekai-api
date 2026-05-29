/**
 * NeoForge {@code StructureModifier} integration. The single modifier type
 * {@link com.kuronami.isekaiapi.structuremodifier.ApplyWorldshapeStructureModifier}
 * (serialized as {@code isekai_api:apply_worldshape_structures}) consumes a
 * {@link com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor} and applies the
 * structure-side concerns: REMOVE-phase exclusion via empty biome filter, MODIFY-phase
 * per-structure mob-spawn overrides.
 */
package com.kuronami.isekaiapi.structuremodifier;
