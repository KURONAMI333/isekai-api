/**
 * Internal implementation — not part of the public API. Consumers must not import
 * from this package; everything here may change without notice between versions.
 *
 * <p>Bridges to the public surface go through
 * {@link com.kuronami.isekaiapi.impl.IsekaiInternal}, which owns the singleton
 * query + remap implementations and the snapshot-publish hook used by the lifecycle
 * listeners.
 */
package com.kuronami.isekaiapi.impl;
