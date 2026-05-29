/**
 * Datapack JSON validator — codec decode + cross-field invariants for
 * {@code data/<ns>/isekai/} descriptors. Invoked by the {@code /isekai validate
 * <namespace>} command; not part of the runtime reload pipeline (decode errors there
 * surface as direct codec failures on each file).
 */
package com.kuronami.isekaiapi.validation;
