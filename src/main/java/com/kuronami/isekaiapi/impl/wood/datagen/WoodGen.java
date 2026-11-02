package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.api.wood.WoodSetDataGen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.ModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.ApiStatus;

/**
 * Read-side view of one {@link WoodEntry}: turns a {@link WoodPart} into the block, item, registry
 * id, texture or model that the generators need, applying the consumer's overrides.
 *
 * <p>Everything is read back from the objects the consumer actually registered, so a set whose
 * builder selected a part under the vanilla naming convention still generates against its own ids.
 */
@ApiStatus.Internal
public final class WoodGen {

    private final WoodEntry entry;

    /** Used to declare textures that this generator will reference but does not draw. */
    @Nullable
    private final ExistingFileHelper existingFiles;

    public WoodGen(WoodEntry entry, @Nullable ExistingFileHelper existingFiles) {
        this.entry = entry;
        this.existingFiles = existingFiles;
    }

    public WoodSet set() {
        return entry.set();
    }

    public WoodSetDataGen.Overrides overrides() {
        return entry.overrides();
    }

    public String namespace() {
        return entry.set().id().getNamespace();
    }

    /** The set's path, e.g. {@code maple}; also the prefix of every registry id in the set. */
    public String name() {
        return entry.set().id().getPath();
    }

    public boolean skipped(WoodPart part) {
        return entry.overrides().isSkipped(part);
    }

    // ---------------------------------------------------------------- blocks

    /** The block of {@code part}, if the set registered it. */
    public Optional<Block> block(WoodPart part) {
        return switch (part) {
            case LOG -> asBlock(entry.set().log());
            case WOOD -> asBlock(entry.set().wood());
            case STRIPPED_LOG -> asBlock(entry.set().strippedLog());
            case STRIPPED_WOOD -> asBlock(entry.set().strippedWood());
            case PLANKS -> asBlock(entry.set().planks());
            case STAIRS -> asBlock(entry.set().stairs());
            case SLAB -> asBlock(entry.set().slab());
            case FENCE -> asBlock(entry.set().fence());
            case FENCE_GATE -> asBlock(entry.set().fenceGate());
            case DOOR -> asBlock(entry.set().door());
            case TRAPDOOR -> asBlock(entry.set().trapdoor());
            case BUTTON -> asBlock(entry.set().button());
            case PRESSURE_PLATE -> asBlock(entry.set().pressurePlate());
            case SIGN -> asBlock(entry.set().sign());
            case HANGING_SIGN -> asBlock(entry.set().hangingSign());
            case LEAVES -> asBlock(entry.set().leaves());
            case SAPLING -> asBlock(entry.set().sapling());
            case POTTED_SAPLING -> asBlock(entry.set().pottedSapling());
            case BOAT, CHEST_BOAT -> Optional.empty();
        };
    }

    public Optional<RotatedPillarBlock> pillar(WoodPart part) {
        return block(part).map(RotatedPillarBlock.class::cast);
    }

    public Optional<StairBlock> stairs() {
        return block(WoodPart.STAIRS).map(StairBlock.class::cast);
    }

    public Optional<SlabBlock> slab() {
        return block(WoodPart.SLAB).map(SlabBlock.class::cast);
    }

    public Optional<FenceBlock> fence() {
        return block(WoodPart.FENCE).map(FenceBlock.class::cast);
    }

    public Optional<FenceGateBlock> fenceGate() {
        return block(WoodPart.FENCE_GATE).map(FenceGateBlock.class::cast);
    }

    public Optional<DoorBlock> door() {
        return block(WoodPart.DOOR).map(DoorBlock.class::cast);
    }

    public Optional<TrapDoorBlock> trapdoor() {
        return block(WoodPart.TRAPDOOR).map(TrapDoorBlock.class::cast);
    }

    public Optional<ButtonBlock> button() {
        return block(WoodPart.BUTTON).map(ButtonBlock.class::cast);
    }

    public Optional<PressurePlateBlock> pressurePlate() {
        return block(WoodPart.PRESSURE_PLATE).map(PressurePlateBlock.class::cast);
    }

    public Optional<StandingSignBlock> sign() {
        return block(WoodPart.SIGN).map(StandingSignBlock.class::cast);
    }

    public Optional<WallSignBlock> wallSign() {
        return asBlock(entry.set().wallSign()).map(WallSignBlock.class::cast);
    }

    public Optional<CeilingHangingSignBlock> hangingSign() {
        return block(WoodPart.HANGING_SIGN).map(CeilingHangingSignBlock.class::cast);
    }

    public Optional<WallHangingSignBlock> wallHangingSign() {
        return asBlock(entry.set().wallHangingSign()).map(WallHangingSignBlock.class::cast);
    }

    public Optional<LeavesBlock> leaves() {
        return block(WoodPart.LEAVES).map(LeavesBlock.class::cast);
    }

    public Optional<SaplingBlock> sapling() {
        return block(WoodPart.SAPLING).map(SaplingBlock.class::cast);
    }

    public Optional<FlowerPotBlock> pottedSapling() {
        return block(WoodPart.POTTED_SAPLING).map(FlowerPotBlock.class::cast);
    }

    // ----------------------------------------------------------------- items

    /**
     * The item of {@code part}. Block parts resolve to their block item; signs, boats and chest
     * boats resolve to the item the builder registered.
     */
    public Optional<Item> item(WoodPart part) {
        return switch (part) {
            case SIGN -> asItem(entry.set().signItem());
            case HANGING_SIGN -> asItem(entry.set().hangingSignItem());
            case BOAT -> asItem(entry.set().boatItem());
            case CHEST_BOAT -> asItem(entry.set().chestBoatItem());
            case LEAVES -> asItem(entry.set().leavesItem());
            case SAPLING -> asItem(entry.set().saplingItem());
            default -> block(part).map(Block::asItem).filter(item -> item != Items.AIR);
        };
    }

    /** The boat entity of {@code part}, for the {@code entity.<namespace>.<path>} name. */
    public Optional<EntityType<?>> entity(WoodPart part) {
        return switch (part) {
            case BOAT -> entry.set().boatEntity().map(holder -> (EntityType<?>) holder.get());
            case CHEST_BOAT -> entry.set().chestBoatEntity().map(holder -> (EntityType<?>) holder.get());
            default -> Optional.empty();
        };
    }

    /** Whether {@code part} exists and was not skipped, i.e. whether it should be generated. */
    public boolean has(WoodPart part) {
        return !skipped(part) && (block(part).isPresent() || item(part).isPresent());
    }

    /** The blocks of {@code parts} that the set registered and the consumer did not skip. */
    public List<Block> blocks(WoodPart... parts) {
        List<Block> result = new ArrayList<>();
        for (WoodPart part : parts) {
            if (has(part)) {
                block(part).ifPresent(result::add);
            }
        }
        return result;
    }

    /** The items of {@code parts} that the set registered and the consumer did not skip. */
    public List<Item> items(WoodPart... parts) {
        List<Item> result = new ArrayList<>();
        for (WoodPart part : parts) {
            if (has(part)) {
                item(part).ifPresent(result::add);
            }
        }
        return result;
    }

    /** Whether the set has at least one bark block, i.e. whether a logs tag makes sense. */
    public boolean hasLogs() {
        return has(WoodPart.LOG) || has(WoodPart.WOOD)
                || has(WoodPart.STRIPPED_LOG) || has(WoodPart.STRIPPED_WOOD);
    }

    /** Registry id of the block of {@code part}; for item-only parts, of the item. */
    public ResourceLocation id(WoodPart part) {
        Block block = block(part).orElse(null);
        if (block != null) {
            return BuiltInRegistries.BLOCK.getKey(block);
        }
        return BuiltInRegistries.ITEM.getKey(item(part).orElseThrow(() -> new IllegalStateException(
                "wood set " + namespace() + ":" + name() + " has no " + part)));
    }

    /** The path of the block of {@code part}, used as the model name. */
    public String path(WoodPart part) {
        return id(part).getPath();
    }

    /** Vanilla-style block texture id of {@code block}: {@code <namespace>:block/<path>}. */
    public ResourceLocation blockTexture(Block block) {
        return blockTexture(BuiltInRegistries.BLOCK.getKey(block));
    }

    /** Vanilla-style block texture id of a block id: {@code <namespace>:block/<path>}. */
    public ResourceLocation blockTexture(ResourceLocation blockId) {
        return ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(),
                ModelProvider.BLOCK_FOLDER + "/" + blockId.getPath());
    }

    // -------------------------------------------------------------- textures

    /**
     * Texture id for {@code slot} of {@code part}, honouring the consumer's overrides.
     *
     * <p>Single-texture parts accept the slot {@code default}; logs and wood accept {@code side}
     * and {@code end}; doors accept {@code bottom} and {@code top}.
     */
    public ResourceLocation texture(WoodPart part, String slot) {
        String override = overrides().texturePath(part, slot);
        return track(override != null ? WoodLanguages.resolve(override, namespace(), ModelProvider.BLOCK_FOLDER)
                : defaultTexture(part, slot));
    }

    private @Nullable ResourceLocation defaultTexture(WoodPart part, String slot) {
        return switch (part) {
            case LOG -> barkTexture(entry.set().log(), part, slot);
            case WOOD -> barkTexture(entry.set().log(), part, slot);
            case STRIPPED_LOG -> barkTexture(entry.set().strippedLog(), part, slot);
            case STRIPPED_WOOD -> barkTexture(entry.set().strippedLog(), part, slot);
            case HANGING_SIGN -> hangingSignTexture(part);
            case DOOR -> doorTexture(slot);
            case PLANKS, STAIRS, SLAB, FENCE, FENCE_GATE, BUTTON, PRESSURE_PLATE, SIGN ->
                    planksTexture(part);
            case TRAPDOOR, LEAVES, SAPLING -> blockTexture(id(part));
            case POTTED_SAPLING -> sapling().map(this::blockTexture).orElse(blockTexture(id(WoodPart.POTTED_SAPLING)));
            case BOAT, CHEST_BOAT -> itemTexture(part);
        };
    }

    /**
     * Bark texture: the log's own texture for both slots, plus {@code _top} for the log's end
     * grain. Wood blocks reuse the log texture for the end grain, exactly as vanilla does.
     */
    private ResourceLocation barkTexture(Optional<? extends DeferredHolder<Block, ? extends Block>> source,
                                         WoodPart part, String slot) {
        Block block = asBlock(source).orElse(null);
        if (block == null) {
            return blockTexture(id(part));
        }
        ResourceLocation base = blockTexture(block);
        if (block instanceof RotatedPillarBlock && "end".equals(slot) && !isWood(part)) {
            return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "_top");
        }
        return base;
    }

    private static boolean isWood(WoodPart part) {
        return part == WoodPart.WOOD || part == WoodPart.STRIPPED_WOOD;
    }

    private ResourceLocation planksTexture(WoodPart part) {
        Block planks = block(WoodPart.PLANKS).orElse(null);
        return planks != null ? blockTexture(planks) : blockTexture(id(part));
    }

    private ResourceLocation hangingSignTexture(WoodPart part) {
        Block strippedLog = block(WoodPart.STRIPPED_LOG).orElse(null);
        return strippedLog != null ? blockTexture(strippedLog) : planksTexture(part);
    }

    private ResourceLocation doorTexture(String slot) {
        ResourceLocation base = blockTexture(id(WoodPart.DOOR));
        String suffix = "top".equals(slot) ? "_top" : "_bottom";
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + suffix);
    }

    /** Texture of the item model of {@code part}, honouring the consumer's override. */
    public ResourceLocation itemTexture(WoodPart part) {
        String override = overrides().itemTexturePath(part);
        if (override != null) {
            return track(WoodLanguages.resolve(override, namespace(), ModelProvider.ITEM_FOLDER));
        }
        if (part == WoodPart.SAPLING) {
            // Vanilla draws the sapling item from the block texture, not from item/<path>.
            Block sapling = block(WoodPart.SAPLING).orElse(null);
            return track(sapling != null ? blockTexture(sapling) : defaultItemTexture(part));
        }
        return track(defaultItemTexture(part));
    }

    private ResourceLocation defaultItemTexture(WoodPart part) {
        ResourceLocation itemId = id(part);
        return ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(),
                ModelProvider.ITEM_FOLDER + "/" + itemId.getPath());
    }

    /**
     * Declares a texture that this generator references but does not draw, so model generation
     * does not fail on a consumer that adds textures to {@code src/main/resources} separately.
     */
    private ResourceLocation track(ResourceLocation texture) {
        if (existingFiles != null) {
            existingFiles.trackGenerated(texture, ModelProvider.TEXTURE);
        }
        return texture;
    }

    // ---------------------------------------------------------------- models

    /** The model override for {@code part}/{@code key}, or {@code null} to build the default. */
    @Nullable
    public ModelFile model(WoodPart part, String key) {
        return overrides().model(part, key);
    }

    /** The render type for {@code part}: the override, else {@code defaultType}. */
    @Nullable
    public String renderType(WoodPart part, @Nullable String defaultType) {
        String override = overrides().renderType(part);
        return override != null ? override : defaultType;
    }

    // ------------------------------------------------------------ behaviour

    public float compostChance() {
        return overrides().compostChance();
    }

    @Nullable
    public WoodSetDataGen.LeavesDrops leavesDrops() {
        return overrides().leavesDrops();
    }

    /** The set's display name in {@code locale}, or {@code null} if that locale has none. */
    @Nullable
    public String displayName(String locale) {
        String override = overrides().displayName(locale);
        if (override != null) {
            return override;
        }
        return WoodLanguages.EN_US.equals(locale) ? titleCase(name()) : null;
    }

    private static String titleCase(String path) {
        StringBuilder result = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    // --------------------------------------------------------------- helpers

    private static Optional<Block> asBlock(
            Optional<? extends DeferredHolder<Block, ? extends Block>> holder) {
        return holder.map(DeferredHolder::get).map(block -> (Block) block);
    }

    private static Optional<Item> asItem(
            Optional<? extends DeferredHolder<Item, ? extends Item>> holder) {
        return holder.map(DeferredHolder::get).map(item -> (Item) item);
    }
}
