package com.kuronami.isekaiapi.api.wood;

import com.kuronami.isekaiapi.impl.wood.StrippablePillarBlock;
import com.kuronami.isekaiapi.impl.wood.WoodBoat;
import com.kuronami.isekaiapi.impl.wood.WoodBoatItem;
import com.kuronami.isekaiapi.impl.wood.WoodChestBoat;
import com.kuronami.isekaiapi.impl.wood.WoodProperties;
import com.kuronami.isekaiapi.impl.wood.WoodSets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Declares one wooden species — every block, item and entity the species needs — in a single
 * builder chain.
 *
 * <p>Parts are opt-in. Only the parts selected on the builder are registered; a species without
 * leaves, or without boats, simply does not create them. The {@link WoodType} and
 * {@link BlockSetType} are created and registered internally from the same id, so no separate
 * registry bookkeeping is needed.
 *
 * <p>Registration targets are the consumer's own {@link DeferredRegister}s, so every object lands
 * in the consumer's namespace and can be referenced, tagged and datagenned like hand-written
 * blocks:
 *
 * <pre>{@code
 * public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
 * public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
 * public static final DeferredRegister<EntityType<?>> ENTITIES =
 *         DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
 *
 * public static final WoodSet MAPLE = WoodSet
 *         .builder(ResourceLocation.fromNamespaceAndPath(MODID, "maple"))
 *         .log().wood().strippedLog().strippedWood()
 *         .family()
 *         .signs()
 *         .leaves()
 *         .sapling(MAPLE_GROWER)
 *         .pottedSapling()
 *         .boats()
 *         .mapColor(MapColor.COLOR_ORANGE)
 *         .register(BLOCKS, ITEMS, ENTITIES);
 * }</pre>
 *
 * <p>Declare the set from the mod constructor, before any registry event: the behaviour below is
 * installed once during mod setup, from the sets that exist at that point.
 *
 * <p>Supporting behaviour that cannot be expressed on the blocks themselves is installed once by
 * Isekai at mod load: axe stripping, vanilla-oak flammability, compostability of leaves and
 * saplings, sign and hanging-sign block entities, flower-pot acceptance, and boat rendering.
 * Flammability can be turned off per set with {@link Builder#flammable(boolean)}.
 *
 * <p>Boat and chest-boat textures are read from {@code textures/entity/boat/<path>.png} and
 * {@code textures/entity/chest_boat/<path>.png} in the wood set's namespace.
 */
public final class WoodSet {

    private final ResourceLocation id;
    private final WoodType woodType;
    private final BlockSetType blockSetType;
    private final boolean flammable;

    @Nullable private final DeferredBlock<RotatedPillarBlock> log;
    @Nullable private final DeferredBlock<RotatedPillarBlock> wood;
    @Nullable private final DeferredBlock<RotatedPillarBlock> strippedLog;
    @Nullable private final DeferredBlock<RotatedPillarBlock> strippedWood;
    @Nullable private final DeferredBlock<Block> planks;
    @Nullable private final DeferredBlock<StairBlock> stairs;
    @Nullable private final DeferredBlock<SlabBlock> slab;
    @Nullable private final DeferredBlock<FenceBlock> fence;
    @Nullable private final DeferredBlock<FenceGateBlock> fenceGate;
    @Nullable private final DeferredBlock<DoorBlock> door;
    @Nullable private final DeferredBlock<TrapDoorBlock> trapdoor;
    @Nullable private final DeferredBlock<ButtonBlock> button;
    @Nullable private final DeferredBlock<PressurePlateBlock> pressurePlate;
    @Nullable private final DeferredBlock<StandingSignBlock> sign;
    @Nullable private final DeferredBlock<WallSignBlock> wallSign;
    @Nullable private final DeferredBlock<CeilingHangingSignBlock> hangingSign;
    @Nullable private final DeferredBlock<WallHangingSignBlock> wallHangingSign;
    @Nullable private final DeferredBlock<LeavesBlock> leaves;
    @Nullable private final DeferredBlock<SaplingBlock> sapling;
    @Nullable private final DeferredBlock<FlowerPotBlock> pottedSapling;
    @Nullable private final DeferredItem<BlockItem> leavesItem;
    @Nullable private final DeferredItem<BlockItem> saplingItem;
    @Nullable private final DeferredItem<SignItem> signItem;
    @Nullable private final DeferredItem<HangingSignItem> hangingSignItem;
    @Nullable private final DeferredHolder<EntityType<?>, EntityType<Boat>> boatEntity;
    @Nullable private final DeferredHolder<EntityType<?>, EntityType<Boat>> chestBoatEntity;
    @Nullable private final DeferredItem<Item> boatItem;
    @Nullable private final DeferredItem<Item> chestBoatItem;

    private WoodSet(Builder builder) {
        this.id = builder.id;
        this.woodType = Objects.requireNonNull(builder.woodType, "wood type");
        this.blockSetType = Objects.requireNonNull(builder.blockSetType, "block set type");
        this.flammable = builder.flammable;
        this.log = builder.log;
        this.wood = builder.wood;
        this.strippedLog = builder.strippedLog;
        this.strippedWood = builder.strippedWood;
        this.planks = builder.planks;
        this.stairs = builder.stairs;
        this.slab = builder.slab;
        this.fence = builder.fence;
        this.fenceGate = builder.fenceGate;
        this.door = builder.door;
        this.trapdoor = builder.trapdoor;
        this.button = builder.button;
        this.pressurePlate = builder.pressurePlate;
        this.sign = builder.sign;
        this.wallSign = builder.wallSign;
        this.hangingSign = builder.hangingSign;
        this.wallHangingSign = builder.wallHangingSign;
        this.leaves = builder.leaves;
        this.sapling = builder.sapling;
        this.pottedSapling = builder.pottedSapling;
        this.leavesItem = builder.leavesItem;
        this.saplingItem = builder.saplingItem;
        this.signItem = builder.signItem;
        this.hangingSignItem = builder.hangingSignItem;
        this.boatEntity = builder.boatEntity;
        this.chestBoatEntity = builder.chestBoatEntity;
        this.boatItem = builder.boatItem;
        this.chestBoatItem = builder.chestBoatItem;
    }

    /** Starts a wood set whose every block, item and entity id is derived from {@code id}. */
    public static Builder builder(ResourceLocation id) {
        return new Builder(Objects.requireNonNull(id, "id"));
    }

    /** The wood set's id; also the {@link WoodType} / {@link BlockSetType} name. */
    public ResourceLocation id() {
        return id;
    }

    /** The wood type shared by this set's fence gate and signs. */
    public WoodType woodType() {
        return woodType;
    }

    /** The block set type shared by this set's door, trapdoor, button and pressure plate. */
    public BlockSetType blockSetType() {
        return blockSetType;
    }

    /**
     * Whether this set behaves like vanilla wood: its logs, wood and sawn blocks catch fire at
     * oak's odds, its leaves spread fire, and {@code WoodSetDataGen} tags the pillar blocks as
     * {@code #minecraft:logs_that_burn}. False for a set declared with
     * {@link Builder#flammable(boolean) flammable(false)}.
     */
    public boolean isFlammable() {
        return flammable;
    }

    public Optional<DeferredBlock<RotatedPillarBlock>> log() {
        return Optional.ofNullable(log);
    }

    public Optional<DeferredBlock<RotatedPillarBlock>> wood() {
        return Optional.ofNullable(wood);
    }

    public Optional<DeferredBlock<RotatedPillarBlock>> strippedLog() {
        return Optional.ofNullable(strippedLog);
    }

    public Optional<DeferredBlock<RotatedPillarBlock>> strippedWood() {
        return Optional.ofNullable(strippedWood);
    }

    public Optional<DeferredBlock<Block>> planks() {
        return Optional.ofNullable(planks);
    }

    public Optional<DeferredBlock<StairBlock>> stairs() {
        return Optional.ofNullable(stairs);
    }

    public Optional<DeferredBlock<SlabBlock>> slab() {
        return Optional.ofNullable(slab);
    }

    public Optional<DeferredBlock<FenceBlock>> fence() {
        return Optional.ofNullable(fence);
    }

    public Optional<DeferredBlock<FenceGateBlock>> fenceGate() {
        return Optional.ofNullable(fenceGate);
    }

    public Optional<DeferredBlock<DoorBlock>> door() {
        return Optional.ofNullable(door);
    }

    public Optional<DeferredBlock<TrapDoorBlock>> trapdoor() {
        return Optional.ofNullable(trapdoor);
    }

    public Optional<DeferredBlock<ButtonBlock>> button() {
        return Optional.ofNullable(button);
    }

    public Optional<DeferredBlock<PressurePlateBlock>> pressurePlate() {
        return Optional.ofNullable(pressurePlate);
    }

    public Optional<DeferredBlock<StandingSignBlock>> sign() {
        return Optional.ofNullable(sign);
    }

    public Optional<DeferredBlock<WallSignBlock>> wallSign() {
        return Optional.ofNullable(wallSign);
    }

    public Optional<DeferredBlock<CeilingHangingSignBlock>> hangingSign() {
        return Optional.ofNullable(hangingSign);
    }

    public Optional<DeferredBlock<WallHangingSignBlock>> wallHangingSign() {
        return Optional.ofNullable(wallHangingSign);
    }

    public Optional<DeferredItem<SignItem>> signItem() {
        return Optional.ofNullable(signItem);
    }

    public Optional<DeferredItem<HangingSignItem>> hangingSignItem() {
        return Optional.ofNullable(hangingSignItem);
    }

    public Optional<DeferredBlock<LeavesBlock>> leaves() {
        return Optional.ofNullable(leaves);
    }

    public Optional<DeferredItem<BlockItem>> leavesItem() {
        return Optional.ofNullable(leavesItem);
    }

    public Optional<DeferredBlock<SaplingBlock>> sapling() {
        return Optional.ofNullable(sapling);
    }

    public Optional<DeferredItem<BlockItem>> saplingItem() {
        return Optional.ofNullable(saplingItem);
    }

    public Optional<DeferredBlock<FlowerPotBlock>> pottedSapling() {
        return Optional.ofNullable(pottedSapling);
    }

    public Optional<DeferredHolder<EntityType<?>, EntityType<Boat>>> boatEntity() {
        return Optional.ofNullable(boatEntity);
    }

    public Optional<DeferredHolder<EntityType<?>, EntityType<Boat>>> chestBoatEntity() {
        return Optional.ofNullable(chestBoatEntity);
    }

    public Optional<DeferredItem<Item>> boatItem() {
        return Optional.ofNullable(boatItem);
    }

    public Optional<DeferredItem<Item>> chestBoatItem() {
        return Optional.ofNullable(chestBoatItem);
    }

    /** Selects parts and options, then registers them into the consumer's registries. */
    public static final class Builder {

        private final ResourceLocation id;

        private MapColor planksColor = MapColor.WOOD;
        private MapColor barkColor = MapColor.PODZOL;
        private SoundType sound = SoundType.WOOD;

        private TreeGrower saplingGrower;

        private boolean wantLog;
        private boolean wantWood;
        private boolean wantStrippedLog;
        private boolean wantStrippedWood;
        private boolean wantPlanks;
        private boolean wantStairs;
        private boolean wantSlab;
        private boolean wantFence;
        private boolean wantFenceGate;
        private boolean wantDoor;
        private boolean wantTrapdoor;
        private boolean wantButton;
        private boolean wantPressurePlate;
        private boolean wantSign;
        private boolean wantHangingSign;
        private boolean wantLeaves;
        private boolean wantPottedSapling;
        private boolean wantBoat;
        private boolean wantChestBoat;

        private boolean registered;

        /**
         * Whether the set behaves like vanilla wood: the logs burn in fire and the pillar blocks
         * belong to {@code #minecraft:logs_that_burn}. Default true; see {@link #flammable(boolean)}.
         */
        private boolean flammable = true;

        private WoodType woodType;
        private BlockSetType blockSetType;
        private DeferredBlock<RotatedPillarBlock> log;
        private DeferredBlock<RotatedPillarBlock> wood;
        private DeferredBlock<RotatedPillarBlock> strippedLog;
        private DeferredBlock<RotatedPillarBlock> strippedWood;
        private DeferredBlock<Block> planks;
        private DeferredBlock<StairBlock> stairs;
        private DeferredBlock<SlabBlock> slab;
        private DeferredBlock<FenceBlock> fence;
        private DeferredBlock<FenceGateBlock> fenceGate;
        private DeferredBlock<DoorBlock> door;
        private DeferredBlock<TrapDoorBlock> trapdoor;
        private DeferredBlock<ButtonBlock> button;
        private DeferredBlock<PressurePlateBlock> pressurePlate;
        private DeferredBlock<StandingSignBlock> sign;
        private DeferredBlock<WallSignBlock> wallSign;
        private DeferredBlock<CeilingHangingSignBlock> hangingSign;
        private DeferredBlock<WallHangingSignBlock> wallHangingSign;
        private DeferredBlock<LeavesBlock> leaves;
        private DeferredBlock<SaplingBlock> sapling;
        private DeferredBlock<FlowerPotBlock> pottedSapling;
        private DeferredItem<BlockItem> leavesItem;
        private DeferredItem<BlockItem> saplingItem;
        private DeferredItem<SignItem> signItem;
        private DeferredItem<HangingSignItem> hangingSignItem;
        private DeferredHolder<EntityType<?>, EntityType<Boat>> boatEntity;
        private DeferredHolder<EntityType<?>, EntityType<Boat>> chestBoatEntity;
        private DeferredItem<Item> boatItem;
        private DeferredItem<Item> chestBoatItem;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /** Map colour for planks and the other sawn parts, and for log end grain. */
        public Builder mapColor(MapColor color) {
            this.planksColor = Objects.requireNonNull(color, "color");
            return this;
        }

        /** Map colour for bark: the log side and the wood block. */
        public Builder barkColor(MapColor color) {
            this.barkColor = Objects.requireNonNull(color, "color");
            return this;
        }

        /** Sound type for every block in the set. */
        public Builder sound(SoundType soundType) {
            this.sound = Objects.requireNonNull(soundType, "soundType");
            return this;
        }

        /**
         * Whether the set behaves like vanilla wood. On by default.
         *
         * <p>{@code false} is for a species that is not wood at all — stone, bone, metal — and it
         * does two things: the pillar blocks and the sawn blocks are left out of
         * {@code FireBlock}'s odds map, so they do not spread fire or burn away, and
         * {@code WoodSetDataGen} tags them as {@code #minecraft:logs} instead of
         * {@code #minecraft:logs_that_burn}. Vanilla links those two tags only through the fire
         * behaviour, so both halves are needed for the blocks to be treated as non-flammable
         * everywhere.
         *
         * <p>One part cannot be turned off: every block keeps vanilla's {@code ignited_by_lava}
         * flag from the oak properties it copies. Lava therefore still sets them alight. There is
         * no supported way to remove a property flag in 1.21.1 without rebuilding every property
         * value by hand, which would change more than this flag.
         */
        public Builder flammable(boolean flammable) {
            this.flammable = flammable;
            return this;
        }

        public Builder log() {
            this.wantLog = true;
            return this;
        }

        public Builder wood() {
            this.wantWood = true;
            return this;
        }

        /** Stripped log; without it an axe leaves {@link #log()} unmodified. */
        public Builder strippedLog() {
            this.wantStrippedLog = true;
            return this;
        }

        /** Stripped wood; without it an axe leaves {@link #wood()} unmodified. */
        public Builder strippedWood() {
            this.wantStrippedWood = true;
            return this;
        }

        public Builder planks() {
            this.wantPlanks = true;
            return this;
        }

        /** Stairs; implies {@link #planks()} because stairs are shaped from the plank block. */
        public Builder stairs() {
            this.wantPlanks = true;
            this.wantStairs = true;
            return this;
        }

        public Builder slab() {
            this.wantSlab = true;
            return this;
        }

        public Builder fence() {
            this.wantFence = true;
            return this;
        }

        public Builder fenceGate() {
            this.wantFenceGate = true;
            return this;
        }

        public Builder door() {
            this.wantDoor = true;
            return this;
        }

        public Builder trapdoor() {
            this.wantTrapdoor = true;
            return this;
        }

        public Builder button() {
            this.wantButton = true;
            return this;
        }

        public Builder pressurePlate() {
            this.wantPressurePlate = true;
            return this;
        }

        /** Standing and wall sign, plus the placeable sign item. */
        public Builder sign() {
            this.wantSign = true;
            return this;
        }

        /** Ceiling and wall hanging sign, plus the placeable hanging sign item. */
        public Builder hangingSign() {
            this.wantHangingSign = true;
            return this;
        }

        /** Both sign kinds. */
        public Builder signs() {
            this.wantSign = true;
            this.wantHangingSign = true;
            return this;
        }

        public Builder leaves() {
            this.wantLeaves = true;
            return this;
        }

        /** Sapling, grown by {@code grower}. */
        public Builder sapling(TreeGrower grower) {
            this.saplingGrower = Objects.requireNonNull(grower, "grower");
            return this;
        }

        /** Flower pot holding the sapling; requires {@link #sapling(TreeGrower)}. */
        public Builder pottedSapling() {
            this.wantPottedSapling = true;
            return this;
        }

        public Builder boat() {
            this.wantBoat = true;
            return this;
        }

        public Builder chestBoat() {
            this.wantChestBoat = true;
            return this;
        }

        /** Boat and chest boat. */
        public Builder boats() {
            this.wantBoat = true;
            this.wantChestBoat = true;
            return this;
        }

        /**
         * The full sawn family: planks, stairs, slab, fence, fence gate, door, trapdoor, button
         * and pressure plate.
         */
        public Builder family() {
            this.wantPlanks = true;
            this.wantStairs = true;
            this.wantSlab = true;
            this.wantFence = true;
            this.wantFenceGate = true;
            this.wantDoor = true;
            this.wantTrapdoor = true;
            this.wantButton = true;
            this.wantPressurePlate = true;
            return this;
        }

        /**
         * Registers the selected parts. Use this overload when no boat is selected; it throws if
         * one is, rather than silently dropping it.
         */
        public WoodSet register(DeferredRegister.Blocks blocks, DeferredRegister.Items items) {
            return register(blocks, items, null);
        }

        /**
         * Registers the selected parts.
         *
         * @param blocks   the consumer's block register
         * @param items    the consumer's item register
         * @param entities the consumer's entity-type register; required when a boat is selected
         * @return the registered wood set
         */
        public WoodSet register(DeferredRegister.Blocks blocks, DeferredRegister.Items items,
                                @Nullable DeferredRegister<EntityType<?>> entities) {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(items, "items");
            if (registered) {
                throw new IllegalStateException("wood set " + id + " is already registered");
            }
            if ((wantBoat || wantChestBoat) && entities == null) {
                throw new IllegalArgumentException("wood set " + id
                        + " selects a boat; pass the DeferredRegister<EntityType<?>> to register(...)");
            }
            if (wantPottedSapling && saplingGrower == null) {
                throw new IllegalArgumentException("wood set " + id
                        + " selects a potted sapling; call sapling(TreeGrower) first");
            }

            final String path = id.getPath();
            final String name = id.toString();
            // The wood type and block set type are what doors, trapdoors, buttons and signs take
            // their sounds from, so the set's sound has to reach them as well as the blocks.
            this.blockSetType = BlockSetType.register(new BlockSetType(name, true, true, true,
                    BlockSetType.PressurePlateSensitivity.EVERYTHING, sound,
                    SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.WOODEN_DOOR_OPEN,
                    SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundEvents.WOODEN_TRAPDOOR_OPEN,
                    SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundEvents.WOODEN_BUTTON_CLICK_ON));
            this.woodType = WoodType.register(new WoodType(name, blockSetType, sound,
                    SoundType.HANGING_SIGN, SoundEvents.FENCE_GATE_CLOSE, SoundEvents.FENCE_GATE_OPEN));

            if (wantStrippedLog) {
                this.strippedLog = blocks.registerBlock("stripped_" + path + "_log",
                        RotatedPillarBlock::new, WoodProperties.log(planksColor, planksColor, sound));
            }
            if (wantStrippedWood) {
                this.strippedWood = blocks.registerBlock("stripped_" + path + "_wood",
                        RotatedPillarBlock::new, WoodProperties.wood(planksColor, sound));
            }
            if (wantLog) {
                this.log = blocks.registerBlock(path + "_log",
                        props -> new StrippablePillarBlock(props, () -> strippedLog == null ? null : strippedLog.get()),
                        WoodProperties.log(planksColor, barkColor, sound));
            }
            if (wantWood) {
                this.wood = blocks.registerBlock(path + "_wood",
                        props -> new StrippablePillarBlock(props, () -> strippedWood == null ? null : strippedWood.get()),
                        WoodProperties.wood(barkColor, sound));
            }

            if (wantPlanks) {
                this.planks = blocks.registerBlock(path + "_planks",
                        Block::new, WoodProperties.planks(planksColor, sound));
            }
            if (wantStairs) {
                this.stairs = blocks.registerBlock(path + "_stairs",
                        props -> new StairBlock(planks.get().defaultBlockState(), props),
                        WoodProperties.stairs(planksColor, sound));
            }
            if (wantSlab) {
                this.slab = blocks.registerBlock(path + "_slab",
                        SlabBlock::new, WoodProperties.slab(planksColor, sound));
            }
            if (wantFence) {
                this.fence = blocks.registerBlock(path + "_fence",
                        FenceBlock::new, WoodProperties.fence(planksColor, sound));
            }
            if (wantFenceGate) {
                this.fenceGate = blocks.registerBlock(path + "_fence_gate",
                        props -> new FenceGateBlock(woodType, props), WoodProperties.fenceGate(planksColor, sound));
            }
            if (wantDoor) {
                this.door = blocks.registerBlock(path + "_door",
                        props -> new DoorBlock(blockSetType, props), WoodProperties.door(planksColor, sound));
            }
            if (wantTrapdoor) {
                this.trapdoor = blocks.registerBlock(path + "_trapdoor",
                        props -> new TrapDoorBlock(blockSetType, props), WoodProperties.trapdoor(planksColor, sound));
            }
            if (wantButton) {
                this.button = blocks.registerBlock(path + "_button",
                        props -> new ButtonBlock(blockSetType, 30, props), WoodProperties.button(sound));
            }
            if (wantPressurePlate) {
                this.pressurePlate = blocks.registerBlock(path + "_pressure_plate",
                        props -> new PressurePlateBlock(blockSetType, props), WoodProperties.pressurePlate(planksColor, sound));
            }

            if (wantSign) {
                this.sign = blocks.registerBlock(path + "_sign",
                        props -> new StandingSignBlock(woodType, props), WoodProperties.sign(planksColor));
                this.wallSign = blocks.registerBlock(path + "_wall_sign",
                        props -> new WallSignBlock(woodType, props.lootFrom(() -> sign.get())),
                        WoodProperties.sign(planksColor));
            }
            if (wantHangingSign) {
                this.hangingSign = blocks.registerBlock(path + "_hanging_sign",
                        props -> new CeilingHangingSignBlock(woodType, props), WoodProperties.sign(planksColor));
                this.wallHangingSign = blocks.registerBlock(path + "_wall_hanging_sign",
                        props -> new WallHangingSignBlock(woodType, props.lootFrom(() -> hangingSign.get())),
                        WoodProperties.sign(planksColor));
            }

            if (wantLeaves) {
                this.leaves = blocks.registerBlock(path + "_leaves",
                        LeavesBlock::new, WoodProperties.leaves());
            }
            if (saplingGrower != null) {
                this.sapling = blocks.registerBlock(path + "_sapling",
                        props -> new SaplingBlock(saplingGrower, props), WoodProperties.sapling());
            }
            if (wantPottedSapling) {
                this.pottedSapling = blocks.registerBlock("potted_" + path + "_sapling",
                        props -> {
                            FlowerPotBlock potted = new FlowerPotBlock(
                                    () -> (FlowerPotBlock) Blocks.FLOWER_POT, sapling::get, props);
                            // The pot looks plants up by block id; the NeoForge constructor does
                            // not register itself, unlike the deprecated vanilla one.
                            ((FlowerPotBlock) Blocks.FLOWER_POT).getEmptyPot()
                                    .addPlant(sapling.getKey().location(), () -> potted);
                            return potted;
                        },
                        WoodProperties.pottedSapling());
            }

            if (wantLog) {
                blockItem(items, path + "_log", () -> log.get());
            }
            if (wantWood) {
                blockItem(items, path + "_wood", () -> wood.get());
            }
            if (wantStrippedLog) {
                blockItem(items, "stripped_" + path + "_log", () -> strippedLog.get());
            }
            if (wantStrippedWood) {
                blockItem(items, "stripped_" + path + "_wood", () -> strippedWood.get());
            }
            if (wantPlanks) {
                blockItem(items, path + "_planks", () -> planks.get());
            }
            if (wantStairs) {
                blockItem(items, path + "_stairs", () -> stairs.get());
            }
            if (wantSlab) {
                blockItem(items, path + "_slab", () -> slab.get());
            }
            if (wantFence) {
                blockItem(items, path + "_fence", () -> fence.get());
            }
            if (wantFenceGate) {
                blockItem(items, path + "_fence_gate", () -> fenceGate.get());
            }
            if (wantDoor) {
                blockItem(items, path + "_door", () -> door.get());
            }
            if (wantTrapdoor) {
                blockItem(items, path + "_trapdoor", () -> trapdoor.get());
            }
            if (wantButton) {
                blockItem(items, path + "_button", () -> button.get());
            }
            if (wantPressurePlate) {
                blockItem(items, path + "_pressure_plate", () -> pressurePlate.get());
            }
            if (wantSign) {
                this.signItem = items.register(path + "_sign",
                        rl -> new SignItem(new Item.Properties().stacksTo(16), sign.get(), wallSign.get()));
            }
            if (wantHangingSign) {
                this.hangingSignItem = items.register(path + "_hanging_sign",
                        rl -> new HangingSignItem(hangingSign.get(), wallHangingSign.get(),
                                new Item.Properties().stacksTo(16)));
            }
            if (wantLeaves) {
                this.leavesItem = blockItem(items, path + "_leaves", () -> leaves.get());
            }
            if (saplingGrower != null) {
                this.saplingItem = blockItem(items, path + "_sapling", () -> sapling.get());
            }

            if (wantBoat) {
                this.boatEntity = registerBoat(entities, path + "_boat", WoodBoat::new);
                this.boatItem = items.register(path + "_boat",
                        rl -> new WoodBoatItem(this::boatType, new Item.Properties().stacksTo(1)));
            }
            if (wantChestBoat) {
                this.chestBoatEntity = registerBoat(entities, path + "_chest_boat", WoodChestBoat::new);
                this.chestBoatItem = items.register(path + "_chest_boat",
                        rl -> new WoodBoatItem(this::chestBoatType, new Item.Properties().stacksTo(1)));
            }

            WoodSet set = new WoodSet(this);
            // Claim the builder only once nothing below can throw any more: a failure part-way
            // through leaves the block set type and wood type registered but must not also leave
            // this builder permanently unusable.
            registered = true;
            WoodSets.add(set);
            return set;
        }

        private EntityType<Boat> boatType() {
            return boatEntity.get();
        }

        private EntityType<Boat> chestBoatType() {
            return chestBoatEntity.get();
        }

        private DeferredItem<BlockItem> blockItem(DeferredRegister.Items items, String name,
                                                 Supplier<? extends Block> block) {
            return items.registerSimpleBlockItem(name, block);
        }

        private DeferredHolder<EntityType<?>, EntityType<Boat>> registerBoat(
                @Nullable DeferredRegister<EntityType<?>> entities, String name,
                EntityType.EntityFactory<Boat> factory) {
            Objects.requireNonNull(entities, "entities");
            return entities.register(name, rl -> EntityType.Builder
                    .of(factory, MobCategory.MISC)
                    .sized(1.375F, 0.5625F)
                    .eyeHeight(0.5625F)
                    .clientTrackingRange(10)
                    .build(rl.toString()));
        }
    }
}
