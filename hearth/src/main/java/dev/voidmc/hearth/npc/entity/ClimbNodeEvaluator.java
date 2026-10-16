package dev.voidmc.hearth.npc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla's walking paths plus straight up and down climbs on ladders and vines, so folk use the ladders of a pueblo.
 * The move control already jumps when the next node is above it, which on a climbable block is how a mob climbs; going
 * down they simply slide. Not scaffolding: a path up a site's scaffold ring and over the wall top is one the folk
 * cannot follow (builders get about the ring by hand, work/Walker), so it must not be offered as a way in.
 */
public class ClimbNodeEvaluator extends WalkNodeEvaluator {
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	@Override
	public int getNeighbors(Node[] neighbors, Node pos) {
		int count = super.getNeighbors(neighbors, pos);
		if (climbable(pos.x, pos.y, pos.z) || climbable(pos.x, pos.y + 1, pos.z)) {
			if (count < neighbors.length && open(pos.x, pos.y + 1, pos.z) && open(pos.x, pos.y + 2, pos.z)) {
				Node up = climbNode(pos.x, pos.y + 1, pos.z);
				if (up != null) {
					neighbors[count++] = up;
				}
			}
		}
		if (count < neighbors.length && climbable(pos.x, pos.y - 1, pos.z)) {
			Node down = climbNode(pos.x, pos.y - 1, pos.z);
			if (down != null) {
				neighbors[count++] = down;
			}
		}
		return count;
	}

	private Node climbNode(int x, int y, int z) {
		Node node = this.getNode(x, y, z);
		if (node.closed) {
			return null;
		}
		node.type = PathType.WALKABLE;
		node.costMalus = Math.max(node.costMalus, 0.0F);
		return node;
	}

	private boolean climbable(int x, int y, int z) {
		BlockState s = state(x, y, z);
		return s.is(BlockTags.CLIMBABLE) && !s.is(Blocks.SCAFFOLDING);
	}

	private boolean open(int x, int y, int z) {
		BlockState s = state(x, y, z);
		return s.is(BlockTags.CLIMBABLE) || s.getCollisionShape(this.currentContext.level(), this.probe).isEmpty();
	}

	private BlockState state(int x, int y, int z) {
		return this.currentContext.getBlockState(this.probe.set(x, y, z));
	}
}
