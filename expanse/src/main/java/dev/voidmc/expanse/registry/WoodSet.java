package dev.voidmc.expanse.registry;

import java.util.function.Function;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * One tree's worth of blocks, registered the way vanilla registers oak: the same block classes, the
 * same strengths and sounds, the same fuel values. Doors and buttons borrow oak's {@link BlockSetType}
 * because a set type only carries sounds and pressure rules — a new one would just be oak again.
 *
 * <p>Every name here has a matching set of generated assets and data (tools/gen_assets.py), so a
 * block added here without one renders as the missing texture rather than failing quietly.
 */
public final class WoodSet {
	public final String name;
	public final Block log;
	public final Block wood;
	public final Block strippedLog;
	public final Block strippedWood;
	public final Block planks;
	public final Block stairs;
	public final Block slab;
	public final Block fence;
	public final Block fenceGate;
	public final Block door;
	public final Block trapdoor;
	public final Block button;
	public final Block pressurePlate;
	public final Block leaves;
	public final Block sapling;
	public final Block pottedSapling;

	/**
	 * @param leafColor   tint of the falling-leaf particle, which is the one place a leaf's colour is
	 *                    not baked into its texture
	 * @param leafLight   block light the leaves give off; nonzero leaves also render full-bright
	 * @param saplingSoil which blocks the sapling may be planted on, beyond vanilla's dirt
	 */
	WoodSet(
		String name,
		MapColor planksColor,
		MapColor barkColor,
		int leafColor,
		int leafLight,
		Function<BlockBehaviour.Properties, Block> saplingFactory
	) {
		this.name = name;
		this.log = ExpanseBlocks.registerWithItem(name + "_log", RotatedPillarBlock::new,
			Blocks.logProperties(planksColor, barkColor, SoundType.WOOD), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.wood = ExpanseBlocks.registerWithItem(name + "_wood", RotatedPillarBlock::new,
			Blocks.logProperties(barkColor, barkColor, SoundType.WOOD), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.strippedLog = ExpanseBlocks.registerWithItem("stripped_" + name + "_log", RotatedPillarBlock::new,
			Blocks.logProperties(planksColor, planksColor, SoundType.WOOD), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.strippedWood = ExpanseBlocks.registerWithItem("stripped_" + name + "_wood", RotatedPillarBlock::new,
			Blocks.logProperties(planksColor, planksColor, SoundType.WOOD), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.planks = ExpanseBlocks.registerWithItem(name + "_planks", Block::new,
			BlockBehaviour.Properties.of().mapColor(planksColor).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava(),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		Block base = this.planks;
		this.stairs = ExpanseBlocks.registerWithItem(name + "_stairs", p -> new StairBlock(base.defaultBlockState(), p),
			BlockBehaviour.Properties.ofLegacyCopy(base), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.slab = ExpanseBlocks.registerWithItem(name + "_slab", SlabBlock::new,
			BlockBehaviour.Properties.ofLegacyCopy(base), p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_SLABS));
		this.fence = ExpanseBlocks.registerWithItem(name + "_fence", FenceBlock::new,
			BlockBehaviour.Properties.of().mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava(),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.fenceGate = ExpanseBlocks.registerWithItem(name + "_fence_gate", p -> new FenceGateBlock(WoodType.OAK, p),
			BlockBehaviour.Properties.of().mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava(),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.door = ExpanseBlocks.register(name + "_door", p -> new DoorBlock(BlockSetType.OAK, p),
			BlockBehaviour.Properties.of().mapColor(planksColor).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED));
		ExpanseItems.registerBlockItem(name + "_door", this.door,
			(block, p) -> new DoubleHighBlockItem(block, p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE)));
		this.trapdoor = ExpanseBlocks.registerWithItem(name + "_trapdoor", p -> new TrapDoorBlock(BlockSetType.OAK, p),
			BlockBehaviour.Properties.of().mapColor(planksColor).instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion()
				.isValidSpawn((s, l, pos, e) -> false).ignitedByLava(),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.button = ExpanseBlocks.registerWithItem(name + "_button", p -> new ButtonBlock(BlockSetType.OAK, 30, p),
			BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL));
		this.pressurePlate = ExpanseBlocks.registerWithItem(name + "_pressure_plate", p -> new PressurePlateBlock(BlockSetType.OAK, p),
			BlockBehaviour.Properties.of().mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollision().strength(0.5F)
				.ignitedByLava().pushReaction(PushReaction.POPPED),
			p -> p.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
		this.leaves = leaves(name + "_leaves", leafColor, leafLight);
		this.sapling = ExpanseBlocks.registerWithItem(name + "_sapling", saplingFactory,
			BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS)
				.pushReaction(PushReaction.POPPED),
			p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW).cookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS));
		Block potted = this.sapling;
		this.pottedSapling = ExpanseBlocks.register("potted_" + name + "_sapling", p -> new FlowerPotBlock(potted, p), ExpanseBlocks.flowerPotProperties());
	}

	/** Untinted leaves: the colour lives in the texture, so every biome shows the tree as drawn. */
	static Block leaves(String id, int particleColor, int light) {
		BlockBehaviour.Properties properties = Blocks.leavesProperties(SoundType.GRASS);
		if (light > 0) {
			properties = properties.lightLevel(s -> light).emissiveRendering(s -> true);
		}
		return ExpanseBlocks.registerWithItem(id,
			p -> new UntintedParticleLeavesBlock(0.02F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, particleColor),
				AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
			properties,
			p -> p.compostable(ContextIntProviders.COMPOSTABLE_LOW));
	}

	static Function<BlockBehaviour.Properties, Block> sapling(TreeGrower grower) {
		return p -> new SaplingBlock(grower, p);
	}
}
