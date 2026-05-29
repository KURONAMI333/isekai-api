/**
 * Server-lifecycle hooks — registered as {@code @EventBusSubscriber} handlers under
 * {@link com.kuronami.isekaiapi.lifecycle.IsekaiLifecycle}.
 *
 * <ul>
 *   <li>{@code ServerAboutToStartEvent} → scan the vanilla rule registries and publish
 *       the snapshot used by the query API.</li>
 *   <li>{@code AddReloadListenerEvent} → register the two
 *       {@link com.kuronami.isekaiapi.lifecycle.IsekaiReloadListener} instances
 *       (worldshape + layered_worldshape JSON) plus
 *       {@link com.kuronami.isekaiapi.lifecycle.SnapshotRefreshListener}.</li>
 * </ul>
 */
package com.kuronami.isekaiapi.lifecycle;
