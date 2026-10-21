package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.Hearth;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What counts as damage, what a repair may overwrite, and what is litter: pure rules over block states, so they are unit
 * tested without a world.
 *
 * <p>The blueprint is the truth (docs/LIVING_WORLD.md §2), but only up to what living in a building changes: doors open,
 * candles are lit, crops are harvested, grass is trampled, copper weathers. So blocks are compared by kind, not by
 * state, and blocks that grow, are worked or melt are never damage.
 */
public final class DamageRules {
	/** Natural ground and debris construction may build over (sim module data); repairs may overwrite it too. */
	public static final TagKey<Block> BUILDABLE_OVER = TagKey.create(Registries.BLOCK, Hearth.id("buildable_over"));

	private DamageRules() {
	}

	/**
	 * Blueprint blocks whose absence is never damage: air, fluids, plants and crops (they grow and are harvested),
	 * leaves, farmland, snow, fire, cake (eaten), scaffolding, and anything replaceable (grass, the invisible light block,
	 * which nobody can break).
	 */
	public static boolean volatileBlock(BlockState expected) {
		if (expected.isAir() || expected.canBeReplaced()) {
			return true;
		}
		Block b = expected.getBlock();
		return b instanceof LiquidBlock || b instanceof VegetationBlock || b instanceof CropBlock || b instanceof StemBlock || b instanceof BushBlock
			|| b instanceof SaplingBlock || b instanceof VineBlock || b instanceof LeavesBlock || b instanceof FarmlandBlock || b instanceof SnowLayerBlock
			|| b instanceof BaseFireBlock || b instanceof CakeBlock || b instanceof ScaffoldingBlock || expected.is(Blocks.SNOW_BLOCK)
			|| expected.is(Blocks.ICE) || expected.is(BlockTags.CROPS) || expected.is(Blocks.DIRT_PATH);
	}

	/**
	 * Two states of one block that differ only in being open or shut: a door, a trapdoor (a window shutter closed at night,
	 * docs/LIVING_WORLD_2.md §5.3) or a fence gate. Never damage, never something a repair puts back: {@link #sameBlock}
	 * compares by kind, so a shutter the crowds closed is the blueprint's block still.
	 */
	public static boolean onlyOpenDiffers(BlockState a, BlockState b) {
		if (a.getBlock() != b.getBlock() || !a.hasProperty(BlockStateProperties.OPEN)) {
			return false;
		}
		return a.setValue(BlockStateProperties.OPEN, b.getValue(BlockStateProperties.OPEN)) == b;
	}

	/** The same kind of block: states differ by use (an open door, a lit candle), copper by weathering and wax. */
	public static boolean sameBlock(BlockState a, BlockState b) {
		Block x = a.getBlock();
		Block y = b.getBlock();
		return x == y || copperFamily(x) == copperFamily(y) && copperFamily(x) != null;
	}

	private static Block copperFamily(Block b) {
		Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().getOrDefault(b, b);
		if (unwaxed instanceof WeatheringCopper) {
			return WeatheringCopper.getFirst(unwaxed);
		}
		return unwaxed == b ? null : unwaxed;
	}

	/**
	 * A blueprint block was destroyed: what stood there was the blueprint's (by kind) and what stands there now is not.
	 * Blocks the blueprint expects to come and go ({@link #volatileBlock}) never count.
	 */
	public static boolean isDamage(BlockState expected, BlockState before, BlockState after) {
		return !volatileBlock(expected) && sameBlock(expected, before) && !sameBlock(expected, after);
	}

	/**
	 * Whether a repair may overwrite this block without asking whose it is: air, fluids, plants, snow, fire, natural
	 * ground, leaves of wild trees. Never a block entity (somebody's chest), never what a player built.
	 */
	public static boolean restorable(BlockState current) {
		if (current.isAir() || current.canBeReplaced() || current.getBlock() instanceof BaseFireBlock) {
			return true;
		}
		if (current.hasBlockEntity()) {
			return false;
		}
		if (current.is(BUILDABLE_OVER)) {
			return true;
		}
		if (current.getBlock() instanceof LeavesBlock) {
			return current.hasProperty(LeavesBlock.PERSISTENT) && !current.getValue(LeavesBlock.PERSISTENT);
		}
		return false;
	}

	/**
	 * For inspections: the cell is missing its blueprint block (a hole where a wall was, a window gone) and putting it
	 * back overwrites nothing anyone placed.
	 */
	public static boolean missing(BlockState expected, BlockState current) {
		return !volatileBlock(expected) && !sameBlock(expected, current) && restorable(current);
	}

	/**
	 * A block left where the blueprint has air, which may be cleared when it blocks a door or a street: anything solid
	 * (a player's dirt pillar, a fence across a doorway) except block entities, whose contents are someone's, and what
	 * weather and plants put there (snow layers, grass), which nobody minds.
	 */
	public static boolean litterBlock(BlockState current) {
		return !current.isAir() && !current.canBeReplaced() && !current.hasBlockEntity() && !(current.getBlock() instanceof LiquidBlock)
			&& !(current.getBlock() instanceof BaseFireBlock);
	}
}
