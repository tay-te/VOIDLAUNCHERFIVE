package dev.voidmc.expanse.registry;

import dev.voidmc.expanse.block.DeepSpeleothemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The blocks of the world underground (world/terrain/CavernLife.java places them in the realms): icicles in
 * the frozen realms, amber and its spikes in the amber hollows, the giant roots of the root hollows with
 * glowing roots hanging from them, and glowworm silk in the grottos. Assets from tools/assets_deep.py,
 * textures from tools/textures/deep.py.
 */
public final class DeepBlocks {
	/** Hangs from packed ice (and stands on it), falls when its ice is broken. */
	public static final Block ICICLE = ExpanseBlocks.registerWithItem("icicle",
		p -> new DeepSpeleothemBlock(Blocks.PACKED_ICE.defaultBlockState(), 1045, p),
		speleothem(MapColor.ICE, SoundType.GLASS).strength(0.8F, 0.5F).friction(0.98F));
	/** Fossil resin, lit from within. */
	public static final Block AMBER = ExpanseBlocks.registerWithItem("amber", Block::new, amber());
	/** Amber with a beetle caught in it long ago. */
	public static final Block INSECT_AMBER = ExpanseBlocks.registerWithItem("insect_amber", Block::new, amber());
	/** A spike of amber, as resin hardens dripping. */
	public static final Block AMBER_SPIKE = ExpanseBlocks.registerWithItem("amber_spike",
		p -> new DeepSpeleothemBlock(AMBER.defaultBlockState(), 1045, p),
		speleothem(MapColor.COLOR_ORANGE, SoundType.RESIN).strength(1.0F, 2.0F).lightLevel(s -> 6).emissiveRendering(s -> true));
	/** The wood of the giant roots that hang from the vaults of the root hollows. */
	public static final Block DEEPROOT = ExpanseBlocks.registerWithItem("deeproot", RotatedPillarBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
	/** Hanging roots whose tips glow. */
	public static final Block GLOWROOTS = ExpanseBlocks.registerWithItem("glowroots", HangingRootsBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).replaceable().noCollision().instabreak().sound(SoundType.HANGING_ROOTS)
			.offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.POPPED).lightLevel(s -> 9));
	/** The silk threads of cave glowworms, beaded with light. */
	public static final Block GLOWWORM_SILK = ExpanseBlocks.registerWithItem("glowworm_silk", HangingMossBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).noCollision().instabreak().sound(SoundType.HANGING_ROOTS)
			.pushReaction(PushReaction.POPPED).lightLevel(s -> 7).emissiveRendering(s -> true));

	private DeepBlocks() {
	}

	public static void init() {
		// Class loading does the registering; this just makes sure it happens before the registries freeze.
	}

	private static BlockBehaviour.Properties speleothem(MapColor colour, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(colour).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).noOcclusion().sound(sound)
			.randomTicks().dynamicShape().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED)
			.isRedstoneConductor((s, l, p) -> false);
	}

	private static BlockBehaviour.Properties amber() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).sound(SoundType.RESIN)
			.requiresCorrectToolForDrops().strength(1.2F, 3.0F).lightLevel(s -> 10).emissiveRendering(s -> true);
	}
}
