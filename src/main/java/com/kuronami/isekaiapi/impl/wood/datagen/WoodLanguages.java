package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * The names and the texture-path grammar shared by {@link WoodLanguageProvider} and
 * {@link com.kuronami.isekaiapi.api.wood.WoodSetDataGen}.
 *
 * <p>English part names follow vanilla's word order ({@code Stripped Maple Log}); Japanese ones
 * follow vanilla's {@code 樹皮を剥いだメープルの原木}. Only these two locales have a part-name
 * grammar, so a set that needs another language generates its own names.
 */
@ApiStatus.Internal
public final class WoodLanguages {

    public static final String EN_US = "en_us";
    public static final String JA_JP = "ja_jp";

    /** Vanilla oak's compost chance for leaves and saplings. */
    public static final float DEFAULT_COMPOST_CHANCE = 0.3F;

    private WoodLanguages() {}

    /**
     * Resolves a texture reference against a namespace.
     *
     * <p>{@code mymod:block/x} is used as-is, {@code block/x} gets the namespace, and a bare
     * {@code x} gets the namespace and {@code folder}.
     */
    public static ResourceLocation resolve(String value, String namespace, String folder) {
        if (value.indexOf(':') >= 0) {
            return ResourceLocation.parse(value);
        }
        if (value.indexOf('/') >= 0) {
            return ResourceLocation.fromNamespaceAndPath(namespace, value);
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, folder + "/" + value);
    }

    /** The translation keys and names for {@code locale}; empty when the locale has no name. */
    public static Map<String, String> names(WoodEntry entry, String locale) {
        WoodGen gen = new WoodGen(entry, null);
        Map<String, String> names = new LinkedHashMap<>();
        String base = gen.displayName(locale);
        if (base == null) {
            return names;
        }
        for (WoodPart part : WoodPart.values()) {
            if (!gen.has(part)) {
                continue;
            }
            gen.block(part).ifPresent(block ->
                    names.put(blockKey(block), partName(base, part, false, locale)));
            if (part == WoodPart.SIGN) {
                gen.wallSign().ifPresent(block ->
                        names.put(blockKey(block), partName(base, part, true, locale)));
            }
            if (part == WoodPart.HANGING_SIGN) {
                gen.wallHangingSign().ifPresent(block ->
                        names.put(blockKey(block), partName(base, part, true, locale)));
            }
            if (part == WoodPart.BOAT || part == WoodPart.CHEST_BOAT) {
                gen.item(part).ifPresent(item ->
                        names.put(item.getDescriptionId(), partName(base, part, false, locale)));
                gen.entity(part).ifPresent(entity ->
                        names.put(entityKey(entity), partName(base, part, false, locale)));
            }
        }
        return names;
    }

    /**
     * The translation key of a block, derived from its registry id.
     *
     * <p>{@code Block#getDescriptionId} is not used: a wall sign overrides it with
     * {@code asItem().getDescriptionId()}, and its item is the standing sign, so both would share
     * {@code block.<namespace>.<path>_sign} and the wall name would overwrite the standing one.
     * Vanilla keeps a separate {@code block.minecraft.oak_wall_sign} entry, and so does this.
     */
    private static String blockKey(Block block) {
        return Util.makeDescriptionId("block", BuiltInRegistries.BLOCK.getKey(block));
    }

    /** The translation key of an entity, derived from its registry id. */
    private static String entityKey(EntityType<?> entity) {
        return Util.makeDescriptionId("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity));
    }

    /** The name of one part; {@code wall} selects the wall variant of a sign. */
    public static String partName(String base, WoodPart part, boolean wall, String locale) {
        return JA_JP.equals(locale) ? japanese(base, part, wall) : english(base, part, wall);
    }

    private static String english(String base, WoodPart part, boolean wall) {
        if (wall) {
            return base + (part == WoodPart.SIGN ? " Wall Sign" : " Wall Hanging Sign");
        }
        return switch (part) {
            case STRIPPED_LOG -> "Stripped " + base + " Log";
            case STRIPPED_WOOD -> "Stripped " + base + " Wood";
            case POTTED_SAPLING -> "Potted " + base + " Sapling";
            case CHEST_BOAT -> base + " Boat with Chest";
            default -> base + " " + part.label();
        };
    }

    private static String japanese(String base, WoodPart part, boolean wall) {
        if (wall) {
            return "壁に付けられた" + base + (part == WoodPart.SIGN ? "の看板" : "の吊り看板");
        }
        return switch (part) {
            case LOG -> base + "の原木";
            case WOOD -> base + "の木";
            case STRIPPED_LOG -> "樹皮を剥いだ" + base + "の原木";
            case STRIPPED_WOOD -> "樹皮を剥いだ" + base + "の木";
            case PLANKS -> base + "の板材";
            case STAIRS -> base + "の階段";
            case SLAB -> base + "のハーフブロック";
            case FENCE -> base + "のフェンス";
            case FENCE_GATE -> base + "のフェンスゲート";
            case DOOR -> base + "のドア";
            case TRAPDOOR -> base + "のトラップドア";
            case BUTTON -> base + "のボタン";
            case PRESSURE_PLATE -> base + "の感圧板";
            case SIGN -> base + "の看板";
            case HANGING_SIGN -> base + "の吊り看板";
            case LEAVES -> base + "の葉";
            case SAPLING -> base + "の苗木";
            case POTTED_SAPLING -> base + "の苗木入り植木鉢";
            case BOAT -> base + "のボート";
            case CHEST_BOAT -> "チェスト付きの" + base + "のボート";
        };
    }
}
