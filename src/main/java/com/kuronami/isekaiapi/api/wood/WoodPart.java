package com.kuronami.isekaiapi.api.wood;

import java.util.List;

/**
 * One piece of a {@link WoodSet}, used by {@link WoodSetDataGen} to name what to generate or
 * skip.
 *
 * <p>A part is a logical unit, not a file: {@link #SIGN} covers the standing sign, the wall sign,
 * the blockstate and the item model. Wall signs are generated together with their standing
 * counterpart and cannot be selected separately, exactly as vanilla generates them.
 *
 * <p>{@link #modelKeys()} lists the model slots a part owns. The keys are the names accepted by
 * {@link WoodSetDataGen.Overrides#model(WoodPart, String, net.neoforged.neoforge.client.model.generators.ModelFile)}
 * when only one model of a multi-model part should be replaced.
 */
public enum WoodPart {

    /** Log block: vertical and horizontal models. */
    LOG("Log", List.of("vertical", "horizontal")),

    /** Bark-covered block made of four logs: one model, rotated per axis. */
    WOOD("Wood", List.of("vertical")),

    /** Stripped log block. */
    STRIPPED_LOG("Stripped Log", List.of("vertical", "horizontal")),

    /** Stripped bark-covered block. */
    STRIPPED_WOOD("Stripped Wood", List.of("vertical")),

    /** Planks block. */
    PLANKS("Planks", List.of("default")),

    /** Stairs: base, inner and outer models. */
    STAIRS("Stairs", List.of("default", "inner", "outer")),

    /** Slab: bottom, top and double (the double model is the planks model). */
    SLAB("Slab", List.of("default", "top", "double")),

    /** Fence: post, side and the inventory model used by the item. */
    FENCE("Fence", List.of("post", "side", "inventory")),

    /** Fence gate: closed, open, in-wall and in-wall open. */
    FENCE_GATE("Fence Gate", List.of("default", "open", "wall", "wall_open")),

    /** Door: eight models, one per half / hinge / open combination. */
    DOOR("Door", List.of(
            "bottom_left", "bottom_left_open", "bottom_right", "bottom_right_open",
            "top_left", "top_left_open", "top_right", "top_right_open")),

    /** Trapdoor: bottom, top and open. */
    TRAPDOOR("Trapdoor", List.of("bottom", "top", "open")),

    /** Button: idle, pressed and the inventory model used by the item. */
    BUTTON("Button", List.of("default", "pressed", "inventory")),

    /** Pressure plate: up and down. */
    PRESSURE_PLATE("Pressure Plate", List.of("default", "down")),

    /** Standing sign and wall sign, plus the sign item. */
    SIGN("Sign", List.of("default")),

    /** Ceiling hanging sign and wall hanging sign, plus the item. */
    HANGING_SIGN("Hanging Sign", List.of("default")),

    /** Leaves block. */
    LEAVES("Leaves", List.of("default")),

    /** Sapling block and its item. */
    SAPLING("Sapling", List.of("default")),

    /** Sapling inside a flower pot; has no item of its own. */
    POTTED_SAPLING("Potted Sapling", List.of("default")),

    /** Boat item and entity; has no block. */
    BOAT("Boat", List.of("default")),

    /** Chest boat item and entity; has no block. */
    CHEST_BOAT("Chest Boat", List.of("default"));

    private final String label;
    private final List<String> modelKeys;

    WoodPart(String label, List<String> modelKeys) {
        this.label = label;
        this.modelKeys = modelKeys;
    }

    /** Human-readable part name, used as the suffix of the default display name. */
    public String label() {
        return label;
    }

    /** The model slots this part owns, in generation order. */
    public List<String> modelKeys() {
        return modelKeys;
    }
}
