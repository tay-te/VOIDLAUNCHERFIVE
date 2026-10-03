package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.block.FumaroleBlock;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** The blocks of the landform biomes (world/terrain/Landform): what volcanoes, canyons and salt pans are made of. */
public final class LandformBlocks {
	/** Fine grey ash lying on a volcano's upper slopes; falls like sand. */
	public static final Block VOLCANIC_ASH = ExpanseBlocks.registerWithItem("volcanic_ash", p -> new SandBlock(new ColorRGBA(0x5A5554), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
	/** A vent in volcanic rock, breathing smoke. */
	public static final Block FUMAROLE = ExpanseBlocks.registerWithItem("fumarole", FumaroleBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(1.5F, 6.0F).sound(SoundType.TUFF).lightLevel(s -> 3));

	/** The crust of a salt flat: hard, white, crystalline. */
	public static final Block SALT_BLOCK = ExpanseBlocks.registerWithItem("salt_block", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(1.2F).sound(SoundType.CALCITE));

	private LandformBlocks() {
	}

	public static void init() {
	}
}
