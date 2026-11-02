package com.kuronami.isekaiapi.structure;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Why a structure declined a position, for {@code /isekai survey}.
 *
 * <p>Vanilla reports a declined structure as nothing at all: {@code Structure#generate} returns
 * {@code INVALID_START} and the chunk simply has no structure. For an author tuning
 * {@code clearance_above_fluid} / {@code max_slope}, "it did not spawn" is not actionable — the
 * question is which gate said no. A structure reports its own rejection through {@link #reject}
 * while a probe is open; outside a probe the call is a no-op, so production generation does not pay
 * for it and cannot change behaviour.
 *
 * <p>The probe is thread-local: chunk generation can run on worker threads, and a survey must not
 * see another thread's rejection.
 */
@ApiStatus.Internal
public final class StructureProbe {

    /** The reasons a structure can report; {@code biome} is reported by the survey's own filter. */
    public enum Gate implements StringRepresentable {
        /** {@code clearance_above_fluid}: part of the footprint sits at or below the sea level. */
        CLEARANCE_ABOVE_FLUID("clearance_above_fluid"),
        /** {@code max_slope}: the footprint's height spread is too large. */
        MAX_SLOPE("max_slope"),
        /** The origin's biome is not in the structure's {@code biomes}. */
        BIOME("biome");

        private final String name;

        Gate(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private static final ThreadLocal<Consumer<Gate>> SINK = new ThreadLocal<>();

    private StructureProbe() {}

    /**
     * Runs {@code attempt} with rejection reporting enabled on this thread and returns its result.
     * Nested calls keep the outer sink.
     */
    public static <T> T record(Consumer<Gate> sink, Supplier<T> attempt) {
        Consumer<Gate> previous = SINK.get();
        SINK.set(sink);
        try {
            return attempt.get();
        } finally {
            if (previous == null) {
                SINK.remove();
            } else {
                SINK.set(previous);
            }
        }
    }

    /** Reports that the current attempt is being rejected for {@code gate}; no-op without a probe. */
    public static void reject(Gate gate) {
        Consumer<Gate> sink = SINK.get();
        if (sink != null) {
            sink.accept(gate);
        }
    }
}
