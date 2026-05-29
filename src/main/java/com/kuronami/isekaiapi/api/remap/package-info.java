/**
 * Worldshape declaration surface — describe how a dimension's worldgen should be
 * transformed.
 *
 * <p>{@link com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor} is the central
 * record. Consumers either:
 *
 * <ul>
 *   <li>Author one via the {@code WorldshapeDescriptor.builder()} pattern and pass it
 *       to {@link com.kuronami.isekaiapi.api.remap.IsekaiRemap#declareWorldshape}.</li>
 *   <li>Or ship a JSON file under {@code data/<ns>/isekai/worldshape/} (loaded by the
 *       reload listener) and reference it from a NeoForge biome modifier via the
 *       {@code isekai_api:apply_worldshape} type.</li>
 * </ul>
 *
 * <p>Sub-records ({@code Exclusions}, {@code Additions}, {@code StructureSpawnConfig},
 * {@code AdditionalFeature}, {@code AdditionalCarver}, {@code AdditionalMobSpawn})
 * bundle related fields so the descriptor stays under the {@code RecordCodecBuilder}
 * 16-field limit while keeping the JSON schema self-documenting.
 */
package com.kuronami.isekaiapi.api.remap;
