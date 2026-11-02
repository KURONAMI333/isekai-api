package com.kuronami.isekaiapi.impl.wood.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.WoodPart;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import com.kuronami.isekaiapi.impl.wood.WoodSets;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodEntry;
import com.kuronami.isekaiapi.impl.wood.datagen.WoodGen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * Checks that every wood set declared for one namespace has all the files and tag memberships it is
 * supposed to have, and reports each missing piece by kind.
 *
 * <p>A species is spread over five kinds of data (loot tables, tags, recipes, blockstates/models/
 * textures, language), and a gap in any one of them is silent: a plank without a loot table drops
 * nothing, a log outside {@code logs_that_burn} does not burn, a model without a texture renders
 * black and purple. The checks below ask for what the bundled data generator writes for the same
 * set, so "the generator ran" and "the data is present" become the same question at runtime.
 *
 * <p>Expectations are read from the live objects, never from names chosen here: block ids come from
 * the registry, loot tables from {@link Block#getLootTable()}, tag membership from the resolved
 * tags, and textures from walking the blockstate and model JSON the consumer actually shipped.
 */
@ApiStatus.Internal
public final class WoodSetAudit {

    /** Longest {@code parent} chain followed, guarding against a cycle in hand-written models. */
    private static final int MAX_MODEL_DEPTH = 16;

    /** Which side of the mod a file belongs to. */
    public enum Layer {
        /** {@code data/} — present on a dedicated server. */
        DATA,
        /** {@code assets/} — present on a client, and in a mod jar on either side. */
        ASSETS;

        /** The root folder this layer lives in. */
        public String directory() {
            return this == DATA ? "data" : "assets";
        }
    }

    /** What is missing. One finding has exactly one kind. */
    public enum Kind {
        LOOT_TABLE("loot table"),
        BLOCK_TAG("block tag membership"),
        ITEM_TAG("item tag membership"),
        RECIPE("recipe"),
        LANGUAGE("language key"),
        BLOCKSTATE("blockstate"),
        MODEL("model"),
        TEXTURE("texture");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** Human-readable name, used in finding messages. */
        public String label() {
            return label;
        }
    }

    /**
     * One missing piece.
     *
     * @param subject the block, item, entity or file the finding is about
     * @param message the full sentence shown by {@code /isekai validate}
     */
    public record Finding(Layer layer, Kind kind, ResourceLocation subject, String message) {
        @Override
        public String toString() {
            return message;
        }
    }

    /**
     * Result of one audit.
     *
     * @param sets     wood sets found in the namespace
     * @param checks   expectations that were tested
     * @param skipped  expectations that could not be tested, because the layer is not visible here
     * @param findings one entry per missing piece; empty when the sets are complete
     */
    public record Report(int sets, int checks, int skipped, List<Finding> findings) {

        public Report {
            findings = List.copyOf(findings);
        }

        public boolean isOk() {
            return findings.isEmpty();
        }

        public List<String> messages() {
            return findings.stream().map(Finding::message).toList();
        }
    }

    private final WoodSetProbe probe;
    private final Map<String, Finding> findings = new LinkedHashMap<>();
    private int sets;
    private int checks;
    private int skipped;

    private boolean dataVisible;
    private boolean assetsVisible;

    private WoodSetAudit(WoodSetProbe probe) {
        this.probe = probe;
    }

    /** Audits every set of {@code namespace} against the packs the game currently has selected. */
    public static Report audit(String namespace, ResourceManager resources) {
        return run(namespace, new PackProbe(resources));
    }

    /** Audits with an explicit probe, so a test can present a deliberately holed set. */
    public static Report run(String namespace, WoodSetProbe probe) {
        WoodSetAudit audit = new WoodSetAudit(probe);
        for (WoodSet set : WoodSets.all()) {
            if (set.id().getNamespace().equals(namespace)) {
                audit.add(set);
            }
        }
        return audit.build();
    }

    private void add(WoodSet set) {
        sets++;
        new SetAudit(set).run();
    }

    private Report build() {
        return new Report(sets, checks, skipped, new ArrayList<>(findings.values()));
    }

    // ------------------------------------------------------------------ helpers

    private void finding(Layer layer, Kind kind, ResourceLocation subject, String message) {
        findings.putIfAbsent(kind + " " + subject, new Finding(layer, kind, subject, message));
    }

    /**
     * Checks one file and returns whether it is there.
     *
     * <p>When the layer is invisible here the check is counted as skipped instead of reported: a
     * dedicated server cannot see a client-only resource pack, and calling that "missing" would be
     * a false alarm.
     */
    private boolean expect(Layer layer, Kind kind, ResourceLocation file, String subject) {
        if (!visible(layer)) {
            skipped++;
            return false;
        }
        checks++;
        Optional<String> text = layer == Layer.ASSETS ? probe.readAsset(file) : probe.readData(file);
        if (text.isPresent()) {
            return true;
        }
        finding(layer, kind, file, subject + ": missing " + kind.label() + " " + file);
        return false;
    }

    private boolean visible(Layer layer) {
        return layer == Layer.ASSETS ? assetsVisible : dataVisible;
    }

    @Nullable
    private static JsonObject parse(@Nullable String text, ResourceLocation file) {
        if (text == null) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            IsekaiApi.LOGGER.warn("[Isekai] validate could not parse {}", file, e);
            return null;
        }
    }

    /** Resolves a reference the way a model does: a bare path belongs to the referring namespace. */
    private static ResourceLocation resolve(String value, String defaultNamespace) {
        return value.contains(":")
                ? ResourceLocation.parse(value)
                : ResourceLocation.fromNamespaceAndPath(defaultNamespace, value);
    }

    private static ResourceLocation file(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static boolean isVanilla(ResourceLocation id) {
        return ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace());
    }

    /** A label paired with the object it names, so messages say "Stairs" and not only an id. */
    private record Named<T>(String label, T value) {}

    // ---------------------------------------------------------------- one set

    /** Everything the audit needs to know about one wood set, resolved from its live objects. */
    private final class SetAudit {

        private final WoodSet set;
        private final WoodGen gen;
        private final String namespace;

        private final Map<WoodPart, Block> blocksByPart = new EnumMap<>(WoodPart.class);
        private final Map<WoodPart, Item> itemsByPart = new EnumMap<>(WoodPart.class);
        private final List<Named<Block>> blocks = new ArrayList<>();
        private final List<Named<Item>> items = new ArrayList<>();
        private final Set<ResourceLocation> visitedModels = new HashSet<>();

        @Nullable
        private Block wallSign;
        @Nullable
        private Block wallHangingSign;

        private SetAudit(WoodSet set) {
            this.set = set;
            this.gen = new WoodGen(new WoodEntry(set, new WoodSetDataGen.Overrides()), null);
            this.namespace = set.id().getNamespace();
        }

        private void run() {
            assetsVisible = probe.seesAssets(namespace);
            dataVisible = probe.seesData(namespace);

            for (WoodPart part : WoodPart.values()) {
                gen.block(part).ifPresent(block -> {
                    blocksByPart.put(part, block);
                    blocks.add(new Named<>(part.label(), block));
                });
                gen.item(part).ifPresent(item -> {
                    itemsByPart.put(part, item);
                    items.add(new Named<>(part.label(), item));
                });
            }
            wallSign = gen.wallSign().orElse(null);
            wallHangingSign = gen.wallHangingSign().orElse(null);
            if (wallSign != null) {
                blocks.add(new Named<>("Wall Sign", wallSign));
            }
            if (wallHangingSign != null) {
                blocks.add(new Named<>("Wall Hanging Sign", wallHangingSign));
            }

            lootTables();
            tags();
            recipes();
            blockstatesAndModels();
            language();
        }

        // -------------------------------------------------------- data files

        /**
         * Every block drops something: its loot table is whatever {@link Block#getLootTable()}
         * resolves to, which also covers the wall signs that borrow their standing counterpart's
         * table.
         */
        private void lootTables() {
            for (Named<Block> named : blocks) {
                Block block = named.value();
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                ResourceLocation table = block.getLootTable().location();
                ResourceLocation tableFile = file(table.getNamespace(), "loot_table/" + table.getPath() + ".json");
                expect(Layer.DATA, Kind.LOOT_TABLE, tableFile,
                        named.label() + " " + id + " (loot table " + table + ")");
            }
        }

        /** Tag membership vanilla oak has, asked of the resolved tags instead of the files. */
        private void tags() {
            blockTag(WoodPart.LOG, BlockTags.LOGS_THAT_BURN);
            blockTag(WoodPart.WOOD, BlockTags.LOGS_THAT_BURN);
            blockTag(WoodPart.STRIPPED_LOG, BlockTags.LOGS_THAT_BURN);
            blockTag(WoodPart.STRIPPED_WOOD, BlockTags.LOGS_THAT_BURN);
            blockTag(WoodPart.PLANKS, BlockTags.PLANKS);
            blockTag(WoodPart.STAIRS, BlockTags.WOODEN_STAIRS);
            blockTag(WoodPart.SLAB, BlockTags.WOODEN_SLABS);
            blockTag(WoodPart.FENCE, BlockTags.WOODEN_FENCES);
            blockTag(WoodPart.FENCE_GATE, BlockTags.FENCE_GATES);
            blockTag(WoodPart.BUTTON, BlockTags.WOODEN_BUTTONS);
            blockTag(WoodPart.PRESSURE_PLATE, BlockTags.WOODEN_PRESSURE_PLATES);
            blockTag(WoodPart.DOOR, BlockTags.WOODEN_DOORS);
            blockTag(WoodPart.TRAPDOOR, BlockTags.WOODEN_TRAPDOORS);
            blockTag(WoodPart.SIGN, BlockTags.STANDING_SIGNS);
            blockTag(WoodPart.HANGING_SIGN, BlockTags.CEILING_HANGING_SIGNS);
            blockTag(WoodPart.LEAVES, BlockTags.LEAVES);
            blockTag(WoodPart.SAPLING, BlockTags.SAPLINGS);
            blockTag(WoodPart.POTTED_SAPLING, BlockTags.FLOWER_POTS);
            blockTag(wallSign, "Wall Sign", BlockTags.WALL_SIGNS);
            blockTag(wallHangingSign, "Wall Hanging Sign", BlockTags.WALL_HANGING_SIGNS);

            itemTag(WoodPart.LOG, ItemTags.LOGS_THAT_BURN);
            itemTag(WoodPart.WOOD, ItemTags.LOGS_THAT_BURN);
            itemTag(WoodPart.STRIPPED_LOG, ItemTags.LOGS_THAT_BURN);
            itemTag(WoodPart.STRIPPED_WOOD, ItemTags.LOGS_THAT_BURN);
            itemTag(WoodPart.PLANKS, ItemTags.PLANKS);
            itemTag(WoodPart.STAIRS, ItemTags.WOODEN_STAIRS);
            itemTag(WoodPart.SLAB, ItemTags.WOODEN_SLABS);
            itemTag(WoodPart.FENCE, ItemTags.WOODEN_FENCES);
            itemTag(WoodPart.FENCE_GATE, ItemTags.FENCE_GATES);
            itemTag(WoodPart.BUTTON, ItemTags.WOODEN_BUTTONS);
            itemTag(WoodPart.PRESSURE_PLATE, ItemTags.WOODEN_PRESSURE_PLATES);
            itemTag(WoodPart.DOOR, ItemTags.WOODEN_DOORS);
            itemTag(WoodPart.TRAPDOOR, ItemTags.WOODEN_TRAPDOORS);
            itemTag(WoodPart.SIGN, ItemTags.SIGNS);
            itemTag(WoodPart.HANGING_SIGN, ItemTags.HANGING_SIGNS);
            itemTag(WoodPart.LEAVES, ItemTags.LEAVES);
            itemTag(WoodPart.SAPLING, ItemTags.SAPLINGS);
            itemTag(WoodPart.BOAT, ItemTags.BOATS);
            itemTag(WoodPart.CHEST_BOAT, ItemTags.CHEST_BOATS);
        }

        private void blockTag(WoodPart part, TagKey<Block> tag) {
            blockTag(blocksByPart.get(part), part.label(), tag);
        }

        private void blockTag(@Nullable Block block, String label, TagKey<Block> tag) {
            if (block == null) {
                return;
            }
            if (!dataVisible) {
                skipped++;
                return;
            }
            checks++;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!probe.inBlockTag(block, tag)) {
                finding(Layer.DATA, Kind.BLOCK_TAG, id,
                        label + " " + id + ": not in block tag #" + tag.location());
            }
        }

        private void itemTag(WoodPart part, TagKey<Item> tag) {
            Item item = itemsByPart.get(part);
            if (item == null) {
                return;
            }
            if (!dataVisible) {
                skipped++;
                return;
            }
            checks++;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!probe.inItemTag(item, tag)) {
                finding(Layer.DATA, Kind.ITEM_TAG, id,
                        part.label() + " " + id + ": not in item tag #" + tag.location());
            }
        }

        /**
         * The recipes the bundled generator writes: planks from the set's log tag, wood from logs,
         * and everything sawn from the planks. Bark blocks, leaves, saplings and the flower pot have
         * no recipe, exactly as in vanilla oak.
         */
        private List<Named<Item>> requiredRecipes() {
            List<Named<Item>> required = new ArrayList<>();
            if (!itemsByPart.containsKey(WoodPart.PLANKS)) {
                return required;
            }
            if (gen.hasLogs()) {
                addRecipe(required, WoodPart.PLANKS);
            }
            if (blocksByPart.containsKey(WoodPart.LOG)) {
                addRecipe(required, WoodPart.WOOD);
            }
            if (blocksByPart.containsKey(WoodPart.STRIPPED_LOG)) {
                addRecipe(required, WoodPart.STRIPPED_WOOD);
            }
            for (WoodPart part : List.of(WoodPart.STAIRS, WoodPart.SLAB, WoodPart.FENCE,
                    WoodPart.FENCE_GATE, WoodPart.DOOR, WoodPart.TRAPDOOR, WoodPart.BUTTON,
                    WoodPart.PRESSURE_PLATE, WoodPart.SIGN, WoodPart.BOAT, WoodPart.CHEST_BOAT)) {
                addRecipe(required, part);
            }
            if (blocksByPart.containsKey(WoodPart.STRIPPED_LOG)) {
                addRecipe(required, WoodPart.HANGING_SIGN);
            }
            return required;
        }

        private void addRecipe(List<Named<Item>> required, WoodPart part) {
            Item item = itemsByPart.get(part);
            if (item != null) {
                required.add(new Named<>(part.label(), item));
            }
        }

        /**
         * Recipes are matched by what they produce, not by file name, so a hand-written recipe
         * anywhere under {@code data/<namespace>/recipe/} counts.
         */
        private void recipes() {
            List<Named<Item>> required = requiredRecipes();
            if (required.isEmpty()) {
                return;
            }
            if (!dataVisible) {
                skipped += required.size();
                return;
            }
            Set<ResourceLocation> results = recipeResults();
            for (Named<Item> named : required) {
                checks++;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(named.value());
                if (results.contains(id)) {
                    continue;
                }
                finding(Layer.DATA, Kind.RECIPE, id,
                        named.label() + " " + id + ": no recipe with this result under data/"
                                + namespace + "/recipe/");
            }
        }

        private Set<ResourceLocation> recipeResults() {
            Set<ResourceLocation> results = new LinkedHashSet<>();
            for (Map.Entry<ResourceLocation, String> entry : probe.listData(namespace, "recipe").entrySet()) {
                JsonObject json = parse(entry.getValue(), entry.getKey());
                if (json == null) {
                    continue;
                }
                JsonElement result = json.get("result");
                if (result == null) {
                    continue;
                }
                ResourceLocation id = null;
                if (result.isJsonPrimitive()) {
                    id = ResourceLocation.tryParse(result.getAsString());
                } else if (result.isJsonObject()) {
                    JsonObject object = result.getAsJsonObject();
                    // 1.21 names the field "id"; older-format recipes used "item".
                    JsonElement value = object.has("id") ? object.get("id") : object.get("item");
                    if (value != null && value.isJsonPrimitive()) {
                        id = ResourceLocation.tryParse(value.getAsString());
                    }
                }
                if (id != null) {
                    results.add(id);
                }
            }
            return results;
        }

        // ------------------------------------------------------ assets files

        private void blockstatesAndModels() {
            for (Named<Block> named : blocks) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(named.value());
                ResourceLocation stateFile = file(id.getNamespace(),
                        "blockstates/" + id.getPath() + ".json");
                String subject = named.label() + " " + id;
                if (!expect(Layer.ASSETS, Kind.BLOCKSTATE, stateFile, subject)) {
                    continue;
                }
                JsonObject state = parse(probe.readAsset(stateFile).orElse(null), stateFile);
                if (state == null) {
                    continue;
                }
                for (ResourceLocation model : references(state, "model", id.getNamespace())) {
                    checkModel(model, subject, 0);
                }
            }

            for (Named<Item> named : items) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(named.value());
                ResourceLocation modelFile = file(id.getNamespace(),
                        "models/item/" + id.getPath() + ".json");
                String subject = named.label() + " item " + id;
                if (!expect(Layer.ASSETS, Kind.MODEL, modelFile, subject)) {
                    continue;
                }
                checkModelBody(modelFile, id, subject, 0);
            }

            Item boat = itemsByPart.get(WoodPart.BOAT);
            if (boat != null) {
                expect(Layer.ASSETS, Kind.TEXTURE, WoodSets.boatTexture(set.id(), false),
                        "Boat item " + BuiltInRegistries.ITEM.getKey(boat));
            }
            Item chestBoat = itemsByPart.get(WoodPart.CHEST_BOAT);
            if (chestBoat != null) {
                expect(Layer.ASSETS, Kind.TEXTURE, WoodSets.boatTexture(set.id(), true),
                        "Chest Boat item " + BuiltInRegistries.ITEM.getKey(chestBoat));
            }
        }

        /** Follows a model and its parents, checking every non-vanilla model and texture it names. */
        private void checkModel(ResourceLocation model, String subject, int depth) {
            if (depth > MAX_MODEL_DEPTH || isVanilla(model) || !visitedModels.add(model)) {
                return;
            }
            ResourceLocation modelFile = file(model.getNamespace(), "models/" + model.getPath() + ".json");
            if (!expect(Layer.ASSETS, Kind.MODEL, modelFile, subject + " (model " + model + ")")) {
                return;
            }
            checkModelBody(modelFile, model, subject, depth);
        }

        private void checkModelBody(ResourceLocation modelFile, ResourceLocation model,
                                    String subject, int depth) {
            JsonObject json = parse(probe.readAsset(modelFile).orElse(null), modelFile);
            if (json == null) {
                return;
            }
            JsonElement parent = json.get("parent");
            if (parent != null && parent.isJsonPrimitive()) {
                checkModel(resolve(parent.getAsString(), model.getNamespace()), subject, depth + 1);
            }
            JsonElement textures = json.get("textures");
            if (textures == null || !textures.isJsonObject()) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : textures.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    continue;
                }
                String reference = value.getAsString();
                if (reference.startsWith("#")) {
                    // A variable of the model itself; the parent chain resolves the real texture.
                    continue;
                }
                ResourceLocation texture = resolve(reference, model.getNamespace());
                if (isVanilla(texture)) {
                    // A dedicated server's vanilla pack has no assets; never call those missing.
                    continue;
                }
                ResourceLocation png = file(texture.getNamespace(),
                        "textures/" + texture.getPath() + ".png");
                expect(Layer.ASSETS, Kind.TEXTURE, png, subject + " (texture " + texture + ")");
            }
        }

        // ----------------------------------------------------------- language

        /**
         * The names the generator writes: {@code block.*} for every block (block items inherit it),
         * plus {@code item.*} for item-only parts and {@code entity.*} for the boats.
         *
         * <p>{@code en_us} is required. {@code ja_jp} is only cross-checked when the consumer
         * shipped one, so a set aimed at other languages is not forced to carry Japanese.
         */
        private void language() {
            List<Named<String>> keys = languageKeys();
            ResourceLocation english = file(namespace, "lang/en_us.json");
            ResourceLocation japanese = file(namespace, "lang/ja_jp.json");

            if (!assetsVisible) {
                skipped += keys.size() + 2;
                return;
            }

            checks++;
            JsonObject en = parse(probe.readAsset(english).orElse(null), english);
            if (en == null) {
                finding(Layer.ASSETS, Kind.LANGUAGE, english,
                        "language file " + english + " is missing, so no part has a name");
            } else {
                for (Named<String> key : keys) {
                    checks++;
                    if (!en.has(key.value())) {
                        finding(Layer.ASSETS, Kind.LANGUAGE, english,
                                key.label() + ": missing language key " + key.value()
                                        + " in " + english);
                    }
                }
            }

            String jaText = probe.readAsset(japanese).orElse(null);
            if (jaText == null) {
                return;
            }
            JsonObject ja = parse(jaText, japanese);
            if (ja == null) {
                return;
            }
            for (Named<String> key : keys) {
                checks++;
                if (!ja.has(key.value())) {
                    finding(Layer.ASSETS, Kind.LANGUAGE, japanese,
                            key.label() + ": missing language key " + key.value()
                                    + " in " + japanese);
                }
            }
        }

        private List<Named<String>> languageKeys() {
            List<Named<String>> keys = new ArrayList<>();
            for (Named<Block> named : blocks) {
                keys.add(new Named<>(named.label(), named.value().getDescriptionId()));
            }
            for (Named<Item> named : items) {
                // Block items take the block's name; only item-only parts need their own key.
                if (!(named.value() instanceof BlockItem)) {
                    keys.add(new Named<>(named.label(), named.value().getDescriptionId()));
                }
            }
            for (WoodPart part : List.of(WoodPart.BOAT, WoodPart.CHEST_BOAT)) {
                gen.entity(part).ifPresent(entity -> keys.add(new Named<>(part.label() + " entity",
                        entity.getDescriptionId())));
            }
            return keys;
        }
    }

    /** Every string value of {@code field}, at any depth, in document order. */
    private static List<ResourceLocation> references(JsonObject json, String field, String namespace) {
        List<ResourceLocation> result = new ArrayList<>();
        collect(json, field, namespace, result);
        return result;
    }

    private static void collect(JsonElement element, String field, String namespace,
                                List<ResourceLocation> result) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (field.equals(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
                    result.add(resolve(entry.getValue().getAsString(), namespace));
                } else {
                    collect(entry.getValue(), field, namespace, result);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collect(child, field, namespace, result);
            }
        }
    }
}
