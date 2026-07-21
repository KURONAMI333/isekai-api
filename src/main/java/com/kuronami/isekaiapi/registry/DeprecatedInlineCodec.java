package com.kuronami.isekaiapi.registry;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.jetbrains.annotations.ApiStatus;

/**
 * Wraps a {@link MapCodec} so that decoding it logs a one-time deprecation warning. Encoding and
 * key enumeration are unchanged — the warning fires only when a datapack's JSON is actually read,
 * so old packs keep working (SPEC §2: {@code isekai:} / inline forms are accepted for one major
 * with a WARN, not broken).
 *
 * <p>Used for the inline {@code isekai_api:apply_worldshape} / {@code apply_worldshape_structures}
 * modifiers, which are superseded by the {@code *_ref} forms (worldshape declared once under
 * {@code isekai/worldshape/}). The flag is per-codec-instance, so each deprecated key warns once
 * per server run regardless of how many files use it.
 */
@ApiStatus.Internal
public final class DeprecatedInlineCodec {

    private DeprecatedInlineCodec() {}

    public static <A> MapCodec<A> warnOnDecode(MapCodec<A> delegate, String message) {
        AtomicBoolean warned = new AtomicBoolean(false);
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                if (warned.compareAndSet(false, true)) {
                    IsekaiApi.LOGGER.warn("[Isekai] {}", message);
                }
                return delegate.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return delegate.encode(input, ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }
        };
    }
}
