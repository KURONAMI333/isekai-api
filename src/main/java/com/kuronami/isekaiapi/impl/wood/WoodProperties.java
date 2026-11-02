package com.kuronami.isekaiapi.impl.wood;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Block property sets for {@link com.kuronami.isekaiapi.api.wood.WoodSet}.
 *
 * <p>Every set is a full copy of the matching vanilla oak block, so strength, tool requirement,
 * collision, occlusion, tick rate, lava ignition and loot-table behaviour match vanilla without
 * this class duplicating (and eventually drifting from) vanilla's values. Only the map colour and
 * the sound type are replaced.
 *
 * <p>The four sign variants are built from scratch instead of copied: vanilla points the wall
 * variant's loot table at the standing sign, which must be found from this wood set rather than
 * oak's.
 */
@ApiStatus.Internal
public final class WoodProperties {

    private WoodProperties() {}

    /** Log: end grain uses {@code ends}, the bark side uses {@code bark}. */
    public static BlockBehaviour.Properties log(MapColor ends, MapColor bark, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)
                .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? ends : bark)
                .sound(sound);
    }

    /** Wood (bark on all six faces). */
    public static BlockBehaviour.Properties wood(MapColor bark, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD).mapColor(bark).sound(sound);
    }

    public static BlockBehaviour.Properties planks(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties stairs(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_STAIRS).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties slab(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SLAB).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties fence(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties fenceGate(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties door(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_DOOR).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties trapdoor(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_TRAPDOOR).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties button(SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_BUTTON).sound(sound);
    }

    public static BlockBehaviour.Properties pressurePlate(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE).mapColor(color).sound(sound);
    }

    public static BlockBehaviour.Properties leaves() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES);
    }

    public static BlockBehaviour.Properties sapling() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING);
    }

    public static BlockBehaviour.Properties pottedSapling() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.POTTED_OAK_SAPLING);
    }

    /** Standing sign / ceiling hanging sign properties (vanilla values). */
    public static BlockBehaviour.Properties sign(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASS)
                .noCollission()
                .strength(1.0F)
                .ignitedByLava();
    }
}
