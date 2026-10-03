package dev.voidmc.expanse.world.tree;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;

/**
 * Drawing logs along a line, for the trunk placers whose branches are not axis-aligned.
 *
 * <p>The line steps one axis at a time (6-connected) rather than diagonally: a branch that only touches
 * its neighbour at an edge reads in-game as a row of floating logs, and a falling-tree or leaf-decay
 * search walks faces, not edges. Each log is turned to the axis the branch is mostly travelling along,
 * so bark runs the way the branch grows.
 */
final class Logs {
	private Logs() {
	}

	interface Placer {
		boolean place(BlockPos pos, Direction.Axis axis);
	}

	static Placer placer(TreeFeature tree, WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random) {
		return (pos, axis) -> {
			if (!TreeFeature.validTreePos(level, pos) && !level.isStateAtPosition(pos, s -> s.is(net.minecraft.tags.BlockTags.LOGS))) {
				return false;
			}
			BlockState state = tree.trunkProvider().value().getState(level, random, pos);
			if (state.hasProperty(RotatedPillarBlock.AXIS)) {
				state = state.setValue(RotatedPillarBlock.AXIS, axis);
			}
			setter.accept(pos, state);
			return true;
		};
	}

	/** Draws from {@code from} to {@code to} inclusive and returns the last position drawn. */
	static BlockPos line(Placer placer, BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		int dz = to.getZ() - from.getZ();
		Direction.Axis main = Math.abs(dy) >= Math.max(Math.abs(dx), Math.abs(dz))
			? Direction.Axis.Y
			: (Math.abs(dx) >= Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z);
		int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
		BlockPos.MutableBlockPos cur = from.mutable();
		placer.place(cur.immutable(), main);
		for (int i = 1; i <= steps; i++) {
			int tx = from.getX() + Math.round((float) dx * i / steps);
			int ty = from.getY() + Math.round((float) dy * i / steps);
			int tz = from.getZ() + Math.round((float) dz * i / steps);
			// Walk each axis separately so consecutive logs always share a face.
			while (cur.getX() != tx) {
				cur.move(Integer.signum(tx - cur.getX()), 0, 0);
				placer.place(cur.immutable(), main);
			}
			while (cur.getZ() != tz) {
				cur.move(0, 0, Integer.signum(tz - cur.getZ()));
				placer.place(cur.immutable(), main);
			}
			while (cur.getY() != ty) {
				cur.move(0, Integer.signum(ty - cur.getY()), 0);
				placer.place(cur.immutable(), main);
			}
		}
		return cur.immutable();
	}
}
