package com.kuronami.isekaiapi.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack contract for {@code isekai_api:leaf_distances}: which fields a structure JSON may
 * write, and what an empty processor object means.
 *
 * <p>The behaviour of the processor itself is a statement about blocks in a world, so it is
 * checked in {@code LeafDistanceProcessorGameTests} instead of here.
 */
class LeafDistanceProcessorTest {

    private static LeafDistanceProcessor decode(String json) {
        JsonElement element = JsonParser.parseString(json);
        DataResult<LeafDistanceProcessor> result =
                LeafDistanceProcessor.CODEC.codec().parse(JsonOps.INSTANCE, element);
        return result.getOrThrow(message -> new AssertionError("processor did not decode: " + message));
    }

    private static JsonElement encode(LeafDistanceProcessor processor) {
        return LeafDistanceProcessor.CODEC.codec().encodeStart(JsonOps.INSTANCE, processor)
                .getOrThrow(message -> new AssertionError("processor did not encode: " + message));
    }

    @Test
    void emptyObjectMeansDecayUnreachableLeaves() {
        // A structure JSON may list the processor as {} — no field may be required.
        LeafDistanceProcessor processor = decode("{}");
        assertEquals(LeafDistanceProcessor.OrphanLeaves.DECAY, processor.orphanLeaves());
        assertFalse(processor.keepPersistent());
        // Optional fields default away, so a processor written as {} saves back as {}.
        assertEquals(JsonParser.parseString("{}"), encode(processor));
    }

    @Test
    void bothFieldsAreReadFromJson() {
        LeafDistanceProcessor processor = decode("""
                {"orphan_leaves": "persist", "keep_persistent": true}""");
        assertEquals(LeafDistanceProcessor.OrphanLeaves.PERSIST, processor.orphanLeaves());
        assertTrue(processor.keepPersistent());
        assertEquals(JsonParser.parseString("""
                {"orphan_leaves": "persist", "keep_persistent": true}"""), encode(processor));
    }

    @Test
    void unknownOrphanModeFailsDecoding() {
        assertThrows(AssertionError.class, () -> decode("""
                {"orphan_leaves": "keep"}"""));
    }
}
