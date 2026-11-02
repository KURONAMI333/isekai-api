package com.kuronami.isekaiapi.impl.wood.datagen;

import com.kuronami.isekaiapi.api.wood.WoodPart;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.ApiStatus;

/**
 * Blockstates and the block / item models of every wood entry in one namespace.
 *
 * <p>One instance per namespace: the NeoForge helpers derive some model names from the bare block
 * path, so the provider's mod id must be the namespace of the blocks it writes.
 */
@ApiStatus.Internal
public final class WoodBlockStateProvider extends BlockStateProvider {

    private static final String CUTOUT = "minecraft:cutout";
    private static final String CUTOUT_MIPPED = "minecraft:cutout_mipped";

    private final String namespace;
    private final List<WoodEntry> entries;

    public WoodBlockStateProvider(PackOutput output, String namespace, ExistingFileHelper existingFiles,
                                  List<WoodEntry> entries) {
        super(output, namespace, existingFiles);
        this.namespace = namespace;
        this.entries = entries;
    }

    @Override
    protected void registerStatesAndModels() {
        for (WoodEntry entry : entries) {
            if (!entry.set().id().getNamespace().equals(namespace)) {
                continue;
            }
            WoodGen gen = new WoodGen(entry, models().existingFileHelper);
            for (WoodPart part : WoodPart.values()) {
                if (gen.has(part)) {
                    generate(gen, part);
                }
            }
        }
    }

    private void generate(WoodGen gen, WoodPart part) {
        switch (part) {
            case LOG, STRIPPED_LOG -> log(gen, part);
            case WOOD, STRIPPED_WOOD -> wood(gen, part);
            case PLANKS -> planks(gen);
            case STAIRS -> stairs(gen);
            case SLAB -> slab(gen);
            case FENCE -> fence(gen);
            case FENCE_GATE -> fenceGate(gen);
            case DOOR -> door(gen);
            case TRAPDOOR -> trapdoor(gen);
            case BUTTON -> button(gen);
            case PRESSURE_PLATE -> pressurePlate(gen);
            case SIGN -> sign(gen);
            case HANGING_SIGN -> hangingSign(gen);
            case LEAVES -> leaves(gen);
            case SAPLING -> sapling(gen);
            case POTTED_SAPLING -> pottedSapling(gen);
            case BOAT, CHEST_BOAT -> itemGenerated(gen, part);
        }
    }

    // ------------------------------------------------------------ bark blocks

    private void log(WoodGen gen, WoodPart part) {
        RotatedPillarBlock block = gen.pillar(part).orElseThrow();
        String path = blockPath(block);
        ResourceLocation side = gen.texture(part, "side");
        ResourceLocation end = gen.texture(part, "end");
        ModelFile vertical = gen.model(part, "vertical");
        if (vertical == null) {
            vertical = models().cubeColumn(path, side, end);
        }
        ModelFile horizontal = gen.model(part, "horizontal");
        if (horizontal == null) {
            horizontal = models().cubeColumnHorizontal(path + "_horizontal", side, end);
        }
        axisBlock(block, vertical, horizontal);
        itemModels().getBuilder(path).parent(vertical);
    }

    private void wood(WoodGen gen, WoodPart part) {
        RotatedPillarBlock block = gen.pillar(part).orElseThrow();
        String path = blockPath(block);
        ResourceLocation side = gen.texture(part, "side");
        ResourceLocation end = gen.texture(part, "end");
        ModelFile model = gen.model(part, "vertical");
        if (model == null) {
            // Vanilla names one column model and rotates it for all three axes.
            model = models().cubeColumn(path, side, end);
        }
        axisBlock(block, model, model);
        itemModels().getBuilder(path).parent(model);
    }

    // ------------------------------------------------------------------ family

    private void planks(WoodGen gen) {
        Block block = gen.block(WoodPart.PLANKS).orElseThrow();
        String path = blockPath(block);
        ModelFile model = gen.model(WoodPart.PLANKS, "default");
        if (model == null) {
            model = models().cubeAll(path, gen.texture(WoodPart.PLANKS, "default"));
        }
        simpleBlockWithItem(block, model);
    }

    private void stairs(WoodGen gen) {
        StairBlock block = gen.stairs().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.STAIRS, "default");
        ModelFile base = gen.model(WoodPart.STAIRS, "default");
        if (base == null) {
            base = models().stairs(path, texture, texture, texture);
        }
        ModelFile inner = gen.model(WoodPart.STAIRS, "inner");
        if (inner == null) {
            inner = models().stairsInner(path + "_inner", texture, texture, texture);
        }
        ModelFile outer = gen.model(WoodPart.STAIRS, "outer");
        if (outer == null) {
            outer = models().stairsOuter(path + "_outer", texture, texture, texture);
        }
        stairsBlock(block, base, inner, outer);
        itemModels().getBuilder(path).parent(base);
    }

    private void slab(WoodGen gen) {
        SlabBlock block = gen.slab().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.SLAB, "default");
        ModelFile bottom = gen.model(WoodPart.SLAB, "default");
        if (bottom == null) {
            bottom = models().slab(path, texture, texture, texture);
        }
        ModelFile top = gen.model(WoodPart.SLAB, "top");
        if (top == null) {
            top = models().slabTop(path + "_top", texture, texture, texture);
        }
        ModelFile doubled = gen.model(WoodPart.SLAB, "double");
        if (doubled == null) {
            doubled = doubleSlabModel(gen, texture);
        }
        slabBlock(block, bottom, top, doubled);
        itemModels().getBuilder(path).parent(bottom);
    }

    private ModelFile doubleSlabModel(WoodGen gen, ResourceLocation texture) {
        Block planks = gen.block(WoodPart.PLANKS).orElse(null);
        if (planks != null) {
            // Vanilla's double slab is the planks model, not a copy of it.
            return models().getExistingFile(blockModel(planks));
        }
        // A slab without planks cannot point at a plank model; a full cube of its own texture is
        // the closest honest default.
        return models().cubeAll(gen.path(WoodPart.SLAB), texture);
    }

    private void fence(WoodGen gen) {
        FenceBlock block = gen.fence().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.FENCE, "default");
        ModelFile post = gen.model(WoodPart.FENCE, "post");
        if (post == null) {
            post = models().fencePost(path + "_post", texture);
        }
        ModelFile side = gen.model(WoodPart.FENCE, "side");
        if (side == null) {
            side = models().fenceSide(path + "_side", texture);
        }
        fourWayBlock(block, post, side);
        ModelFile inventory = gen.model(WoodPart.FENCE, "inventory");
        if (inventory == null) {
            inventory = models().fenceInventory(path + "_inventory", texture);
        }
        itemModels().getBuilder(path).parent(inventory);
    }

    private void fenceGate(WoodGen gen) {
        FenceGateBlock block = gen.fenceGate().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.FENCE_GATE, "default");
        ModelFile base = gen.model(WoodPart.FENCE_GATE, "default");
        if (base == null) {
            base = models().fenceGate(path, texture);
        }
        ModelFile open = gen.model(WoodPart.FENCE_GATE, "open");
        if (open == null) {
            open = models().fenceGateOpen(path + "_open", texture);
        }
        ModelFile wall = gen.model(WoodPart.FENCE_GATE, "wall");
        if (wall == null) {
            wall = models().fenceGateWall(path + "_wall", texture);
        }
        ModelFile wallOpen = gen.model(WoodPart.FENCE_GATE, "wall_open");
        if (wallOpen == null) {
            wallOpen = models().fenceGateWallOpen(path + "_wall_open", texture);
        }
        fenceGateBlock(block, base, open, wall, wallOpen);
        itemModels().getBuilder(path).parent(base);
    }

    private void door(WoodGen gen) {
        DoorBlock block = gen.door().orElseThrow();
        String path = blockPath(block);
        ResourceLocation bottom = gen.texture(WoodPart.DOOR, "bottom");
        ResourceLocation top = gen.texture(WoodPart.DOOR, "top");
        ModelFile bottomLeft = doorModel(gen, "bottom_left", path + "_bottom_left", bottom, top);
        ModelFile bottomLeftOpen = doorModel(gen, "bottom_left_open", path + "_bottom_left_open", bottom, top);
        ModelFile bottomRight = doorModel(gen, "bottom_right", path + "_bottom_right", bottom, top);
        ModelFile bottomRightOpen = doorModel(gen, "bottom_right_open", path + "_bottom_right_open", bottom, top);
        ModelFile topLeft = doorModel(gen, "top_left", path + "_top_left", bottom, top);
        ModelFile topLeftOpen = doorModel(gen, "top_left_open", path + "_top_left_open", bottom, top);
        ModelFile topRight = doorModel(gen, "top_right", path + "_top_right", bottom, top);
        ModelFile topRightOpen = doorModel(gen, "top_right_open", path + "_top_right_open", bottom, top);
        doorBlock(block, bottomLeft, bottomLeftOpen, bottomRight, bottomRightOpen,
                topLeft, topLeftOpen, topRight, topRightOpen);
        itemGenerated(gen, WoodPart.DOOR);
    }

    private ModelFile doorModel(WoodGen gen, String key, String name, ResourceLocation bottom, ResourceLocation top) {
        ModelFile override = gen.model(WoodPart.DOOR, key);
        if (override != null) {
            return override;
        }
        ModelBuilder<?> builder = switch (key) {
            case "bottom_left" -> models().doorBottomLeft(name, bottom, top);
            case "bottom_left_open" -> models().doorBottomLeftOpen(name, bottom, top);
            case "bottom_right" -> models().doorBottomRight(name, bottom, top);
            case "bottom_right_open" -> models().doorBottomRightOpen(name, bottom, top);
            case "top_left" -> models().doorTopLeft(name, bottom, top);
            case "top_left_open" -> models().doorTopLeftOpen(name, bottom, top);
            case "top_right" -> models().doorTopRight(name, bottom, top);
            case "top_right_open" -> models().doorTopRightOpen(name, bottom, top);
            default -> throw new IllegalArgumentException("unknown door model " + key);
        };
        applyRenderType(gen, WoodPart.DOOR, builder, CUTOUT);
        return (ModelFile) builder;
    }

    private void trapdoor(WoodGen gen) {
        TrapDoorBlock block = gen.trapdoor().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.TRAPDOOR, "default");
        ModelFile bottom = trapdoorModel(gen, "bottom", path + "_bottom", texture, false);
        ModelFile top = trapdoorModel(gen, "top", path + "_top", texture, true);
        ModelFile open = trapdoorModel(gen, "open", path + "_open", texture, false);
        trapdoorBlock(block, bottom, top, open, false);
        itemModels().getBuilder(path).parent(bottom);
    }

    private ModelFile trapdoorModel(WoodGen gen, String key, String name, ResourceLocation texture, boolean isTop) {
        ModelFile override = gen.model(WoodPart.TRAPDOOR, key);
        if (override != null) {
            return override;
        }
        ModelBuilder<?> builder;
        if ("open".equals(key)) {
            builder = models().trapdoorOpen(name, texture);
        } else if (isTop) {
            builder = models().trapdoorTop(name, texture);
        } else {
            builder = models().trapdoorBottom(name, texture);
        }
        applyRenderType(gen, WoodPart.TRAPDOOR, builder, CUTOUT);
        return (ModelFile) builder;
    }

    private void button(WoodGen gen) {
        ButtonBlock block = gen.button().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.BUTTON, "default");
        ModelFile base = gen.model(WoodPart.BUTTON, "default");
        if (base == null) {
            base = models().button(path, texture);
        }
        ModelFile pressed = gen.model(WoodPart.BUTTON, "pressed");
        if (pressed == null) {
            pressed = models().buttonPressed(path + "_pressed", texture);
        }
        buttonBlock(block, base, pressed);
        ModelFile inventory = gen.model(WoodPart.BUTTON, "inventory");
        if (inventory == null) {
            inventory = models().buttonInventory(path + "_inventory", texture);
        }
        itemModels().getBuilder(path).parent(inventory);
    }

    private void pressurePlate(WoodGen gen) {
        PressurePlateBlock block = gen.pressurePlate().orElseThrow();
        String path = blockPath(block);
        ResourceLocation texture = gen.texture(WoodPart.PRESSURE_PLATE, "default");
        ModelFile base = gen.model(WoodPart.PRESSURE_PLATE, "default");
        if (base == null) {
            base = models().pressurePlate(path, texture);
        }
        ModelFile down = gen.model(WoodPart.PRESSURE_PLATE, "down");
        if (down == null) {
            down = models().pressurePlateDown(path + "_down", texture);
        }
        pressurePlateBlock(block, base, down);
        itemModels().getBuilder(path).parent(base);
    }

    // ------------------------------------------------------------------ signs

    private void sign(WoodGen gen) {
        Block standing = gen.block(WoodPart.SIGN).orElseThrow();
        String path = blockPath(standing);
        ModelFile model = gen.model(WoodPart.SIGN, "default");
        if (model == null) {
            model = models().sign(path, gen.texture(WoodPart.SIGN, "default"));
        }
        Block wall = gen.wallSign().orElse(null);
        if (wall == null) {
            simpleBlock(standing, model);
        } else {
            signBlock((net.minecraft.world.level.block.StandingSignBlock) standing,
                    (net.minecraft.world.level.block.WallSignBlock) wall, model);
        }
        itemGenerated(gen, WoodPart.SIGN);
    }

    private void hangingSign(WoodGen gen) {
        Block ceiling = gen.block(WoodPart.HANGING_SIGN).orElseThrow();
        String path = blockPath(ceiling);
        ModelFile model = gen.model(WoodPart.HANGING_SIGN, "default");
        if (model == null) {
            model = models().sign(path, gen.texture(WoodPart.HANGING_SIGN, "default"));
        }
        Block wall = gen.wallHangingSign().orElse(null);
        if (wall == null) {
            simpleBlock(ceiling, model);
        } else {
            hangingSignBlock((net.minecraft.world.level.block.CeilingHangingSignBlock) ceiling,
                    (net.minecraft.world.level.block.WallHangingSignBlock) wall, model);
        }
        itemGenerated(gen, WoodPart.HANGING_SIGN);
    }

    // ------------------------------------------------------- plants and items

    private void leaves(WoodGen gen) {
        LeavesBlock block = gen.leaves().orElseThrow();
        ModelFile model = gen.model(WoodPart.LEAVES, "default");
        if (model == null) {
            model = models().leaves(blockPath(block), gen.texture(WoodPart.LEAVES, "default"));
            applyRenderType(gen, WoodPart.LEAVES, (ModelBuilder<?>) model, CUTOUT_MIPPED);
        }
        simpleBlockWithItem(block, model);
    }

    private void sapling(WoodGen gen) {
        SaplingBlock block = gen.sapling().orElseThrow();
        ModelFile model = gen.model(WoodPart.SAPLING, "default");
        if (model == null) {
            model = models().cross(blockPath(block), gen.texture(WoodPart.SAPLING, "default"));
            applyRenderType(gen, WoodPart.SAPLING, (ModelBuilder<?>) model, CUTOUT);
        }
        simpleBlock(block, model);
        itemGenerated(gen, WoodPart.SAPLING);
    }

    private void pottedSapling(WoodGen gen) {
        Block block = gen.block(WoodPart.POTTED_SAPLING).orElseThrow();
        ModelFile model = gen.model(WoodPart.POTTED_SAPLING, "default");
        if (model == null) {
            ResourceLocation plant = gen.block(WoodPart.SAPLING)
                    .map(gen::blockTexture)
                    .orElseGet(() -> gen.texture(WoodPart.POTTED_SAPLING, "default"));
            model = models().withExistingParent(blockPath(block), mcLoc("block/flower_pot_cross"))
                    .texture("plant", plant);
            applyRenderType(gen, WoodPart.POTTED_SAPLING, (ModelBuilder<?>) model, CUTOUT);
        }
        simpleBlock(block, model);
    }

    private void itemGenerated(WoodGen gen, WoodPart part) {
        itemModels().getBuilder(gen.path(part))
                .parent(models().getExistingFile(mcLoc("item/generated")))
                .texture("layer0", gen.itemTexture(part));
    }

    // ---------------------------------------------------------------- helpers

    private void applyRenderType(WoodGen gen, WoodPart part, ModelBuilder<?> builder,
                                 @Nullable String defaultType) {
        String renderType = gen.renderType(part, defaultType);
        if (renderType != null) {
            builder.renderType(renderType);
        }
    }

    private static String blockPath(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    private static ResourceLocation blockModel(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath());
    }
}
