package com.kuronami.isekaiapi.api.wood;

import com.kuronami.isekaiapi.impl.wood.datagen.WoodBlockStateProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodBlockTagProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodCompostProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodEntry;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodItemTagProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodLanguageProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodLanguages;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodLootProvider;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodRecipeProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Generates the data files of a {@link WoodSet}: blockstates, block and item models, loot tables,
 * crafting recipes and their advancements, block and item tags, the {@code en_us} / {@code ja_jp}
 * names, and the compostable data map entry.
 *
 * <p>Call it from a {@link GatherDataEvent} listener, after the sets are registered:
 *
 * <pre>{@code
 * @SubscribeEvent
 * static void gatherData(GatherDataEvent event) {
 *     WoodSetDataGen wood = WoodSetDataGen.create(event);
 *     wood.add(MAPLE);
 *     wood.add(ELM, new WoodSetDataGen.Overrides()
 *             .displayName("Elm", "ニレ")
 *             .skip(WoodPart.BOAT, WoodPart.CHEST_BOAT));
 * }
 * }</pre>
 *
 * <p>Registration is split by side, as the event is: blockstates, models and names follow
 * {@link GatherDataEvent#includeClient()}, while loot, recipes, tags and the data map follow
 * {@link GatherDataEvent#includeServer()}. A set may live in any namespace; one provider is created
 * per namespace, so a mod that ships several species under different namespaces still gets the
 * vanilla file names.
 *
 * <p>Textures are not drawn. The set's blocks point at the vanilla layout
 * {@code <namespace>:block/<block path>} (with {@code _top} for log end grain, {@code _bottom} and
 * {@code _top} for doors, and the planks texture for every sawn part), so a species becomes visible
 * as soon as the consumer adds those images under {@code src/main/resources}.
 *
 * <p>{@link Overrides} replaces any single output. Everything else keeps the default, so a set can
 * be datagenned without touching the class at all.
 */
public final class WoodSetDataGen {

    private final GatherDataEvent event;
    private final List<WoodEntry> entries = new ArrayList<>();
    private final Map<String, WoodBlockStateProvider> blockStates = new LinkedHashMap<>();
    private final Map<String, WoodLanguageProvider> english = new LinkedHashMap<>();
    private final Map<String, WoodLanguageProvider> japanese = new LinkedHashMap<>();

    private boolean lang = true;
    private boolean serverAdded;

    private WoodSetDataGen(GatherDataEvent event) {
        this.event = event;
    }

    /** Starts a datagen run; add one or more sets to it, then let the event finish. */
    public static WoodSetDataGen create(GatherDataEvent event) {
        return new WoodSetDataGen(event);
    }

    /** Adds a set with the default output. */
    public WoodSetDataGen add(WoodSet set) {
        return add(set, new Overrides());
    }

    /**
     * Adds a set, replacing the outputs selected on {@code overrides}.
     *
     * <p>Call this from the event listener, not after it: providers are registered while the
     * listener runs.
     */
    public WoodSetDataGen add(WoodSet set, Overrides overrides) {
        WoodEntry entry = new WoodEntry(set, overrides);
        entries.add(entry);
        String namespace = set.id().getNamespace();

        if (event.includeClient()) {
            PackOutput output = event.getGenerator().getPackOutput();
            if (!blockStates.containsKey(namespace)) {
                WoodBlockStateProvider provider = new WoodBlockStateProvider(output, namespace,
                        event.getExistingFileHelper(), entries);
                blockStates.put(namespace, provider);
                event.addProvider(provider);
            }
            if (!english.containsKey(namespace)) {
                WoodLanguageProvider en = new WoodLanguageProvider(output, namespace,
                        WoodLanguages.EN_US, entries, () -> lang);
                english.put(namespace, en);
                event.addProvider(en);

                WoodLanguageProvider ja = new WoodLanguageProvider(output, namespace,
                        WoodLanguages.JA_JP, entries, () -> lang);
                japanese.put(namespace, ja);
                event.addProvider(ja);
            }
        }

        if (event.includeServer() && !serverAdded) {
            addServerProviders();
            serverAdded = true;
        }
        return this;
    }

    /**
     * Turns the {@code en_us} / {@code ja_jp} files off.
     *
     * <p>Use this when the consumer already has a {@code LanguageProvider} for the same namespace:
     * both providers write the whole {@code assets/<namespace>/lang/<locale>.json}, so only one of
     * them may run. {@link #translations(WoodSet, String)} gives the same names for a consumer that
     * wants to merge them into its own provider.
     */
    public WoodSetDataGen lang(boolean enabled) {
        this.lang = enabled;
        return this;
    }

    /**
     * The names this generator would write for {@code locale}.
     *
     * <p>{@code en_us} always has a name (the set path in title case unless overridden);
     * {@code ja_jp} is empty unless {@link Overrides#displayName(String, String)} was called. Other
     * locales are empty: the part-name patterns only exist for these two.
     */
    public static Map<String, String> translations(WoodSet set, String locale) {
        return translations(set, new Overrides(), locale);
    }

    /** @see #translations(WoodSet, String) */
    public static Map<String, String> translations(WoodSet set, Overrides overrides, String locale) {
        return WoodLanguages.names(new WoodEntry(set, overrides), locale);
    }

    private void addServerProviders() {
        PackOutput output = event.getGenerator().getPackOutput();
        String modId = entries.get(0).set().id().getNamespace();
        event.addProvider(new WoodRecipeProvider(output, event.getLookupProvider(), entries));
        // Loot tables need the registries (enchantments for silk touch / fortune), so they are
        // registered through the vanilla wrapper that resolves the future per sub-provider.
        event.addProvider(new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        registries -> new WoodLootProvider(entries, registries),
                        LootContextParamSets.BLOCK)),
                event.getLookupProvider()));
        WoodBlockTagProvider blockTags = new WoodBlockTagProvider(output,
                event.getLookupProvider(), modId, event.getExistingFileHelper(), entries);
        event.addProvider(blockTags);
        event.addProvider(new WoodItemTagProvider(output, event.getLookupProvider(),
                blockTags.contentsGetter(), modId, event.getExistingFileHelper(), entries));
        event.addProvider(new WoodCompostProvider(output, event.getLookupProvider(), entries));
    }

    /**
     * Per-set replacements for the default output. Every method returns {@code this}, so calls
     * chain.
     *
     * <p>Parts that the set did not register are ignored; an override never creates a part.
     */
    public static final class Overrides {

        private final Set<WoodPart> skipped = EnumSet.noneOf(WoodPart.class);
        private final Map<WoodPart, Map<String, String>> textures = new EnumMap<>(WoodPart.class);
        private final Map<WoodPart, String> itemTextures = new EnumMap<>(WoodPart.class);
        private final Map<WoodPart, Map<String, ModelFile>> models = new EnumMap<>(WoodPart.class);
        private final Map<WoodPart, String> renderTypes = new EnumMap<>(WoodPart.class);
        private final Map<String, String> displayNames = new LinkedHashMap<>();
        private float compostChance = WoodLanguages.DEFAULT_COMPOST_CHANCE;
        @Nullable
        private LeavesDrops leavesDrops;

        /** Stops generating {@code parts}: no blockstate, model, loot, recipe, tag or name. */
        public Overrides skip(WoodPart... parts) {
            for (WoodPart part : parts) {
                skipped.add(part);
            }
            return this;
        }

        /**
         * Points every texture slot of {@code part} at {@code texture}.
         *
         * <p>{@code texture} is either a full id ({@code mymod:block/maple_planks}), a path under
         * the set's namespace ({@code block/maple_planks}), or a bare name, in which case
         * {@code block/} is prefixed.
         */
        public Overrides texture(WoodPart part, String texture) {
            if (part == WoodPart.LOG || part == WoodPart.STRIPPED_LOG
                    || part == WoodPart.WOOD || part == WoodPart.STRIPPED_WOOD) {
                texture(part, "side", texture);
                return texture(part, "end", texture);
            }
            if (part == WoodPart.DOOR) {
                texture(part, "bottom", texture);
                return texture(part, "top", texture);
            }
            return texture(part, "default", texture);
        }

        /**
         * Points one texture slot of {@code part} at {@code texture}.
         *
         * <p>Slots are {@code side} and {@code end} for logs and wood, {@code bottom} and
         * {@code top} for doors, and {@code default} for every other part.
         */
        public Overrides texture(WoodPart part, String slot, String texture) {
            List<String> slots = slots(part);
            if (!slots.contains(slot)) {
                throw new IllegalArgumentException("wood set part " + part + " has no texture slot '"
                        + slot + "'; expected one of " + slots);
            }
            textures.computeIfAbsent(part, key -> new LinkedHashMap<>()).put(slot, texture);
            return this;
        }

        /** Points the item model of {@code part} at {@code texture} instead of {@code item/<path>}. */
        public Overrides itemTexture(WoodPart part, String texture) {
            itemTextures.put(part, texture);
            return this;
        }

        /**
         * Uses {@code model} for every model of {@code part}.
         *
         * <p>This replaces the model files but not the blockstate: the blockstate is generated
         * against the supplied model, so the consumer does not have to write one.
         */
        public Overrides model(WoodPart part, ModelFile model) {
            for (String key : part.modelKeys()) {
                model(part, key, model);
            }
            return this;
        }

        /**
         * Uses {@code model} for one model of {@code part}.
         *
         * <p>Keys are listed by {@link WoodPart#modelKeys()}: {@code vertical} and
         * {@code horizontal} for logs, {@code default} / {@code inner} / {@code outer} for stairs,
         * and so on. Models that are not replaced are still generated.
         */
        public Overrides model(WoodPart part, String key, ModelFile model) {
            if (!part.modelKeys().contains(key)) {
                throw new IllegalArgumentException("wood set part " + part + " has no model '"
                        + key + "'; expected one of " + part.modelKeys());
            }
            models.computeIfAbsent(part, ignored -> new LinkedHashMap<>()).put(key, model);
            return this;
        }

        /**
         * Overrides the render type of the models of {@code part}, e.g. {@code minecraft:cutout}.
         *
         * <p>The default already covers the vanilla translucency rules: leaves are
         * {@code cutout_mipped}, saplings, doors and trapdoors are {@code cutout}, and everything
         * else takes the opaque default.
         */
        public Overrides renderType(WoodPart part, String renderType) {
            renderTypes.put(part, renderType);
            return this;
        }

        /** Replaces the drop table of the leaves block. */
        public Overrides leavesDrops(LeavesDrops drops) {
            this.leavesDrops = drops;
            return this;
        }

        /** Compost chance of the leaves and sapling items; vanilla oak uses {@code 0.3}. */
        public Overrides compostChance(float chance) {
            if (chance < 0.0F || chance > 1.0F) {
                throw new IllegalArgumentException("compost chance must be within [0, 1], got " + chance);
            }
            this.compostChance = chance;
            return this;
        }

        /**
         * Sets the set's name in {@code en_us} and {@code ja_jp}.
         *
         * <p>The default is the set path in title case ({@code maple} becomes {@code Maple}). Part
         * names are built from it, e.g. {@code Stripped Maple Log} or
         * {@code 樹皮を剥いだメープルの原木}.
         */
        public Overrides displayName(String english, String japanese) {
            displayNames.put(WoodLanguages.EN_US, english);
            displayNames.put(WoodLanguages.JA_JP, japanese);
            return this;
        }

        // ------------------------------------------------------------ read side

        public boolean isSkipped(WoodPart part) {
            return skipped.contains(part);
        }

        /** The texture path of {@code part}/{@code slot}, or {@code null} for the default. */
        @Nullable
        public String texturePath(WoodPart part, String slot) {
            Map<String, String> byslot = textures.get(part);
            if (byslot == null) {
                return null;
            }
            String value = byslot.get(slot);
            if (value == null && "default".equals(slot)) {
                value = byslot.get("default");
            }
            return value;
        }

        @Nullable
        public String itemTexturePath(WoodPart part) {
            return itemTextures.get(part);
        }

        @Nullable
        public ModelFile model(WoodPart part, String key) {
            Map<String, ModelFile> bykey = models.get(part);
            return bykey == null ? null : bykey.get(key);
        }

        @Nullable
        public String renderType(WoodPart part) {
            return renderTypes.get(part);
        }

        public float compostChance() {
            return compostChance;
        }

        @Nullable
        public LeavesDrops leavesDrops() {
            return leavesDrops;
        }

        @Nullable
        public String displayName(String locale) {
            return displayNames.get(locale);
        }

        private static List<String> slots(WoodPart part) {
            return switch (part) {
                case LOG, STRIPPED_LOG, WOOD, STRIPPED_WOOD -> List.of("side", "end");
                case DOOR -> List.of("bottom", "top");
                default -> List.of("default");
            };
        }
    }

    /** What a leaves block drops besides itself. */
    public enum LeavesDrops {

        /** Vanilla oak: a sapling, sticks, and an apple with a small chance. */
        VANILLA_OAK,

        /** A sapling and sticks, without the apple. */
        SAPLING_ONLY
    }
}
