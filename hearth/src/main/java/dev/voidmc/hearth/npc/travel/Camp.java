package dev.voidmc.hearth.npc.travel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The few blocks of a camp by the road or a bandits' camp in the woods (docs/LIVING_WORLD_2.md §1.3): a campfire,
 * bedrolls, a tent of wool and fences, a chest. A dressing, not a building: it is put only where nothing stands (air or
 * plants over natural ground, never on anyone's work), remembered block by block, and taken down when the party leaves
 * or folds — each block only if it is still as the camp left it. The road book's traffic keeps the camps up now, so one
 * left by a crash is taken down when the server comes back ({@link Parties#sweepCamps}).
 */
public final class Camp {
	private final List<BlockPos> placed = new ArrayList<>();
	private final List<BlockState> states = new ArrayList<>();
	private final List<BlockState> before = new ArrayList<>();
	private final BlockPos centre;

	public Camp(BlockPos centre) {
		this.centre = centre.immutable();
	}

	public BlockPos centre() {
		return this.centre;
	}

	public List<BlockPos> placed() {
		return List.copyOf(this.placed);
	}

	/** Puts a block where nothing stands (air or a plant), on solid ground; returns whether it went in. */
	public boolean put(ServerLevel level, BlockPos at, BlockState state) {
		if (!level.isLoaded(at)) {
			return false;
		}
		BlockState current = level.getBlockState(at);
		if (!(current.isAir() || current.canBeReplaced() && !current.hasBlockEntity())) {
			return false;
		}
		BlockPos below = at.below();
		BlockState under = level.getBlockState(below);
		if (!state.is(net.minecraft.tags.BlockTags.WOOL) && state.getBlock() != Blocks.OAK_FENCE && !under.isFaceSturdy(level, below, Direction.UP)
			&& !this.placed.contains(below)) {
			return false;
		}
		dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, at, state, Block.UPDATE_CLIENTS);
		this.placed.add(at.immutable());
		this.states.add(level.getBlockState(at));
		this.before.add(current);
		return true;
	}

	/** A campfire at the camp's centre (lit). */
	public boolean fire(ServerLevel level) {
		return put(level, this.centre, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
	}

	/** Bedrolls round the fire: carpets on the ground, {@code count} of them. */
	public void bedrolls(ServerLevel level, int count, long seed) {
		Block[] rolls = {Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.BROWN), Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.GRAY),
			Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY), Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.GREEN)};
		int[][] ring = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-2, -1}, {1, -2}, {-1, 2}};
		int n = 0;
		for (int[] o : ring) {
			if (n >= count) {
				break;
			}
			BlockPos at = ground(level, this.centre.offset(o[0], 0, o[1]));
			if (at != null && put(level, at, rolls[(int) Math.floorMod(seed + n, rolls.length)].defaultBlockState())) {
				n++;
			}
		}
	}

	/** A small tent: wool over a ridge of fences, open at the front, beside the fire. */
	public void tent(ServerLevel level, Direction front) {
		Direction side = front.getClockWise();
		BlockPos base = ground(level, this.centre.relative(front.getOpposite(), 3));
		if (base == null) {
			return;
		}
		for (int k = -1; k <= 1; k++) {
			BlockPos p = base.relative(side, 0).relative(front, k);
			put(level, p.above(), Blocks.OAK_FENCE.defaultBlockState());
		}
		for (int k = -1; k <= 1; k++) {
			BlockPos ridge = base.relative(front, k).above(2);
			put(level, ridge, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
			put(level, ridge.relative(side).below(), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
			put(level, ridge.relative(side.getOpposite()).below(), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
		}
	}

	/** Where a block may stand on the ground near {@code at} (the first air over solid ground within two of it), or null. */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos at) {
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos p = at.above(dy);
			BlockState s = level.getBlockState(p);
			BlockState under = level.getBlockState(p.below());
			if ((s.isAir() || s.canBeReplaced() && !s.hasBlockEntity()) && under.isFaceSturdy(level, p.below(), Direction.UP)) {
				return p;
			}
		}
		return null;
	}

	/** Takes the camp down: each block only if it is still as the camp left it. */
	public void strike(ServerLevel level) {
		for (int i = this.placed.size() - 1; i >= 0; i--) {
			BlockPos p = this.placed.get(i);
			if (level.isLoaded(p) && level.getBlockState(p) == this.states.get(i)) {
				BlockState back = this.before.get(i);
				dev.voidmc.hearth.npc.work.field.QuietBlocks.set(level, p, back.isAir() || back.canBeReplaced() ? Blocks.AIR.defaultBlockState() : back, Block.UPDATE_CLIENTS);
			}
		}
		this.placed.clear();
		this.states.clear();
		this.before.clear();
	}

	public boolean isEmpty() {
		return this.placed.isEmpty();
	}
}
