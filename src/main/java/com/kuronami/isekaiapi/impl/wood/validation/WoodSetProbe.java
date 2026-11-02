package com.kuronami.isekaiapi.impl.wood.validation;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * Where {@link WoodSetAudit} reads the resources of a wood set from.
 *
 * <p>Minecraft's {@code ResourceManager} is bound to one {@code PackType}, so the two layers a
 * species is made of ({@code data/} and {@code assets/}) are answered separately. A probe that can
 * see one layer but not the other says so through {@link #seesData} / {@link #seesAssets}, so an
 * audit reports "cannot see" instead of inventing a missing file.
 *
 * <p>Ids passed to {@link #readData} are relative to {@code data/<namespace>/} and ids passed to
 * {@link #readAsset} are relative to {@code assets/<namespace>/} — for example
 * {@code mymod:loot_table/blocks/maple_log.json} and {@code mymod:blockstates/maple_log.json}.
 */
@ApiStatus.Internal
public interface WoodSetProbe {

    /** Whether {@code data/} of {@code namespace} is readable at all. */
    boolean seesData(String namespace);

    /** Whether {@code assets/} of {@code namespace} is readable at all. */
    boolean seesAssets(String namespace);

    /** The text of {@code data/<id.namespace>/<id.path>}, or empty when absent. */
    Optional<String> readData(ResourceLocation id);

    /** The text of {@code assets/<id.namespace>/<id.path>}, or empty when absent. */
    Optional<String> readAsset(ResourceLocation id);

    /** Every data file under {@code data/<namespace>/<directory>}, by id. */
    Map<ResourceLocation, String> listData(String namespace, String directory);

    /** Whether {@code block} is a member of {@code tag} as the game currently resolves it. */
    boolean inBlockTag(Block block, TagKey<Block> tag);

    /** Whether {@code item} is a member of {@code tag} as the game currently resolves it. */
    boolean inItemTag(Item item, TagKey<Item> tag);
}
