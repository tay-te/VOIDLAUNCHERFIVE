package dev.voidmc.hearth.repair.folk;

import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairQueue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A column of vanilla scaffolding beside a high repair, put up the way a player (and the NPC module's builders) do it:
 * stand on the spot, jump, place a scaffold underfoot at the top of the jump, again and again; work from the top; drop
 * down through it; take it down top first. Every block goes in the settlement's ledger ({@link RepairQueue#addScaffold})
 * as it goes up, so nothing is left behind if the builder is called away.
 */
final class Column {
	private enum Phase { GO, POLE, TOP, DOWN, TEARDOWN, DONE }

	private final BlockPos base;
	private final int height;
	private final List<BlockPos> placed = new ArrayList<>();
	private Phase phase = Phase.GO;
	private int timer;
	private int jumpWait;
	private int goTicks;

	private Column(BlockPos base, int height) {
		this.base = base;
		this.height = height;
	}

	/** A clear column near the target whose top reaches it, outside the plot if one will do; null if there is none. */
	static @Nullable Column plan(ServerLevel level, PathfinderMob folk, PlotSpec spec, BlockPos target) {
		BlockPos best = null;
		int bestHeight = 0;
		double bestScore = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				if (ring == 0) {
					continue;
				}
				BlockPos ground = groundBelow(level, new BlockPos(target.getX() + dx, target.getY(), target.getZ() + dz), 24);
				if (ground == null) {
					continue;
				}
				int h = target.getY() - 1 - ground.getY();
				if (h < 2 || h > 20 || !clear(level, ground, h + 2)) {
					continue;
				}
				int x = ground.getX();
				int z = ground.getZ();
				boolean inside = x >= spec.origin().getX() && x < spec.origin().getX() + spec.sizeX() && z >= spec.origin().getZ()
					&& z < spec.origin().getZ() + spec.sizeZ();
				double score = ring + (inside ? 3 : 0) + ground.distSqr(folk.blockPosition()) * 0.001;
				if (score < bestScore) {
					best = ground;
					bestHeight = h;
					bestScore = score;
				}
			}
		}
		return best == null ? null : new Column(best, bestHeight);
	}

	private static @Nullable BlockPos groundBelow(ServerLevel level, BlockPos from, int depth) {
		BlockPos.MutableBlockPos p = from.mutable();
		for (int i = 0; i < depth; i++) {
			if (!level.isLoaded(p)) {
				return null;
			}
			if (Reach.standable(level, p)) {
				return p.immutable();
			}
			p.move(0, -1, 0);
		}
		return null;
	}

	private static boolean clear(ServerLevel level, BlockPos ground, int h) {
		for (int i = 0; i < h; i++) {
			BlockState s = level.getBlockState(ground.above(i));
			if (!s.isAir() && !s.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	/** Putting up, climbing down or taking down: the builder can do nothing else meanwhile. */
	boolean busy() {
		return this.phase == Phase.GO || this.phase == Phase.POLE || this.phase == Phase.DOWN || this.phase == Phase.TEARDOWN;
	}

	boolean atTop() {
		return this.phase == Phase.TOP;
	}

	boolean done() {
		return this.phase == Phase.DONE;
	}

	/** Never stood on: the column could not be reached. */
	boolean gaveUp() {
		return this.phase == Phase.DONE && this.placed.isEmpty();
	}

	boolean hasBlocks() {
		return !this.placed.isEmpty();
	}

	/** Down and take it away. */
	void dismantle() {
		if (this.phase == Phase.TOP || this.phase == Phase.POLE || this.phase == Phase.GO) {
			this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.DOWN;
		}
	}

	void tick(FolkHandle f, ServerLevel level, RepairQueue ledger) {
		PathfinderMob folk = f.entity();
		switch (this.phase) {
			case GO -> {
				f.hold(new ItemStack(Items.SCAFFOLDING));
				if (this.goTicks % 40 == 0 && !f.moveTo(this.base, 0.7)) {
					this.phase = Phase.DONE;
				}
				this.goTicks++;
				if (folk.blockPosition().equals(this.base) || f.arrived() && folk.blockPosition().distSqr(this.base) <= 1) {
					f.stop();
					this.phase = Phase.POLE;
				} else if (this.goTicks > 300) {
					f.stop();
					this.phase = Phase.DONE;
				}
			}
			case POLE -> {
				f.stop();
				centre(folk);
				if (this.placed.size() >= this.height) {
					this.phase = Phase.TOP;
					break;
				}
				int next = this.base.getY() + this.placed.size();
				if (folk.getY() >= next + 1.0) {
					BlockPos p = new BlockPos(this.base.getX(), next, this.base.getZ());
					BlockState s = level.getBlockState(p);
					if (s.isAir() || s.canBeReplaced()) {
						level.setBlock(p, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						level.playSound(null, p, SoundEvents.SCAFFOLDING_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F + folk.getRandom().nextFloat() * 0.2F);
						f.swing();
						ledger.addScaffold(f.resident().id, p);
						f.settlement().markDirty();
						this.placed.add(p);
					} else {
						this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.TOP;
					}
				} else if (folk.onGround() && --this.jumpWait <= 0) {
					folk.getJumpControl().jump();
					this.jumpWait = 3;
				}
				if (++this.timer > 20 * 40) {
					this.phase = this.placed.isEmpty() ? Phase.DONE : Phase.DOWN;
				}
			}
			case TOP -> {
				centre(folk);
				folk.setDeltaMovement(folk.getDeltaMovement().multiply(0, 1, 0));
			}
			case DOWN -> {
				f.stop();
				centre(folk);
				folk.setShiftKeyDown(true);
				if (folk.onGround() && folk.getBlockY() <= this.base.getY()) {
					folk.setShiftKeyDown(false);
					this.phase = Phase.TEARDOWN;
					this.timer = 0;
				} else if (++this.timer > 20 * 30) {
					folk.setShiftKeyDown(false);
					folk.teleportTo(this.base.getX() + 0.5, this.base.getY(), this.base.getZ() + 0.5);
					this.phase = Phase.TEARDOWN;
					this.timer = 0;
				}
			}
			case TEARDOWN -> {
				if (--this.timer > 0) {
					break;
				}
				this.timer = 3;
				if (this.placed.isEmpty()) {
					this.phase = Phase.DONE;
					break;
				}
				BlockPos top = this.placed.removeLast();
				takeDown(level, top);
				ledger.removeScaffold(f.resident().id, top);
				f.settlement().markDirty();
				f.swing();
			}
			case DONE -> {
			}
		}
	}

	/** Interrupted: it all comes down at once, and the builder with it (safely). */
	void removeAll(FolkHandle f, ServerLevel level) {
		PathfinderMob folk = f.entity();
		folk.setShiftKeyDown(false);
		if (!this.placed.isEmpty() && folk.getY() > this.base.getY() + 0.5) {
			folk.teleportTo(this.base.getX() + 0.5, this.base.getY(), this.base.getZ() + 0.5);
		}
		RepairQueue ledger = f.settlement().data(RepairModule.QUEUE);
		for (int i = this.placed.size() - 1; i >= 0; i--) {
			BlockPos p = this.placed.get(i);
			if (level.isLoaded(p)) {
				takeDown(level, p);
				ledger.removeScaffold(f.resident().id, p);
			}
		}
		this.placed.clear();
		this.phase = Phase.DONE;
		f.settlement().markDirty();
	}

	private static void takeDown(ServerLevel level, BlockPos p) {
		if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
			level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(Blocks.SCAFFOLDING.defaultBlockState()));
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.playSound(null, p, SoundEvents.SCAFFOLDING_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
	}

	private void centre(PathfinderMob folk) {
		double cx = this.base.getX() + 0.5;
		double cz = this.base.getZ() + 0.5;
		if (Math.abs(folk.getX() - cx) > 0.05 || Math.abs(folk.getZ() - cz) > 0.05) {
			folk.setPos(cx, folk.getY(), cz);
		}
	}
}
