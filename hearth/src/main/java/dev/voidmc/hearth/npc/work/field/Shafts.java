package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Going down the Great Mine's shaft and up again (docs/LIVING_WORLD_2.md §6.3): the old ladder runs up the shaft's north
 * wall from the foot to the deck, past every level's station. No path searches the length of a two-hundred-block ladder,
 * so a miner climbs it by hand: steps onto the ladder at the mouth (or at a level), goes down or up it a rung at a time,
 * and steps off at the level of the face (or out onto the deck at the end of the shift).
 */
public final class Shafts {
	/** Blocks a tick on the ladder (vanilla climbs at 0.2; a miner with a lamp goes steadily). */
	static final double CLIMB = 0.12;

	private Shafts() {
	}

	/** The shaft of a Great Mine pithead: its centre's column, and the deck's standing level. */
	public record Shaft(int x, int z, int deckY) {
		/** The ladder's column: on the shaft's north wall (Expanse puts it there). */
		public BlockPos ladder(int y) {
			return new BlockPos(this.x, y, this.z - 2);
		}

		/** Where one steps off the ladder at standing level {@code y}: onto the deck at the top, into the station below. */
		public BlockPos off(int y) {
			return y >= this.deckY - 1 ? new BlockPos(this.x, this.deckY, this.z - 3) : new BlockPos(this.x + 1, y, this.z - 3);
		}

		/** The deck by the mouth, where the climb begins and ends. */
		public BlockPos mouth() {
			return new BlockPos(this.x, this.deckY, this.z - 3);
		}
	}

	/** The shaft of a plot, if it is a Great Mine's pithead. */
	public static @Nullable Shaft of(@Nullable PlotView plot) {
		if (plot == null || !plot.spec().archetype().equals(RemoteSites.GREAT_PITHEAD)) {
			return null;
		}
		PlotSpec s = plot.spec();
		BlockPos c = RemoteSites.centre(s);
		return new Shaft(c.getX(), c.getZ(), s.floorY());
	}

	/**
	 * A climb a miner must make to stand at {@code target} (a face down a level, or the deck), or null if they are on its
	 * level already or the shaft does not join them (they are elsewhere: the routine walks them to the mouth first).
	 */
	public static @Nullable Task climb(FolkBrain brain, ServerLevel level, Shaft shaft, BlockPos target) {
		int from = brain.folk.getBlockY();
		int to = target.getY();
		if (Math.abs(to - from) < 6) {
			return null;
		}
		double dx = brain.folk.getX() - (shaft.x() + 0.5);
		double dz = brain.folk.getZ() - (shaft.z() + 0.5);
		if (dx * dx + dz * dz > 12 * 12 && from >= shaft.deckY() - 2) {
			// still on the surface away from the mouth: walked there first
			return new Climb(shaft, to, true);
		}
		return new Climb(shaft, to, false);
	}

	/** To the mouth, onto the ladder, down (or up) it, off at the level. */
	static final class Climb implements Task {
		enum Leg { WALK, ON, CLIMB, OFF }

		private final Shaft shaft;
		private final int to;
		private Leg leg;
		private int t;

		Climb(Shaft shaft, int to, boolean walk) {
			this.shaft = shaft;
			this.to = to;
			this.leg = walk ? Leg.WALK : Leg.ON;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.t += elapsed;
			switch (this.leg) {
				case WALK -> {
					if (brain.walk(level, this.shaft.mouth(), 1.2, 0.6) || this.t > 20 * 90) {
						brain.resetWalk();
						this.leg = Leg.ON;
					} else if (brain.walkFailed()) {
						return Status.FAILED;
					}
				}
				case ON -> {
					BlockPos rung = this.shaft.ladder(brain.folk.getBlockY());
					if (!level.isLoaded(rung) || !(level.getBlockState(rung).getBlock() instanceof LadderBlock)
						&& !(level.getBlockState(rung.below()).getBlock() instanceof LadderBlock)) {
						// no ladder here (dug away, not generated yet): the miner cannot go down
						return Status.FAILED;
					}
					brain.folk.getNavigation().stop();
					brain.folk.setNoGravity(true);
					brain.folk.snapTo(rung.getX() + 0.5, brain.folk.getY(), rung.getZ() + 0.5, 180, 0);
					brain.hold(new ItemStack(net.minecraft.world.item.Items.LANTERN), ItemStack.EMPTY);
					this.leg = Leg.CLIMB;
				}
				case CLIMB -> {
					Vec3 p = brain.folk.position();
					double dy = this.to - p.y;
					double step = Math.min(Math.abs(dy), CLIMB * Math.max(1, elapsed));
					brain.folk.setDeltaMovement(Vec3.ZERO);
					// moved, not snapped: the client glides between the steps
					brain.folk.setPos(this.shaft.x() + 0.5, p.y + Math.signum(dy) * step, this.shaft.z() - 2 + 0.5);
					brain.folk.setYRot(180);
					if ((brain.folk.tickCount & 7) == 0) {
						level.playSound(null, brain.folk.blockPosition(), SoundEvents.LADDER_STEP, SoundSource.NEUTRAL, 0.3F, 1.0F);
					}
					brain.folk.setFolkPose(FolkPose.TEND);
					if (Math.abs(dy) < 0.05 || this.t > 20 * 60 * 6) {
						this.leg = Leg.OFF;
					}
				}
				case OFF -> {
					brain.folk.setNoGravity(false);
					brain.folk.setFolkPose(FolkPose.NONE);
					BlockPos off = this.shaft.off(this.to);
					BlockPos stand = level.isLoaded(off) && FolkSpots.standable(level, off) ? off : FolkSpots.standableNear(level, off, 3);
					if (stand != null) {
						brain.folk.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, brain.folk.getYRot(), 0);
					}
					return Status.DONE;
				}
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			if (brain.folk.isNoGravity()) {
				brain.folk.setNoGravity(false);
				// let off the ladder: where they hang is in the shaft (the record takes them on if nobody sees)
				if (level.getNearestPlayer(brain.folk, 24) == null) {
					brain.slipAway();
				} else {
					BlockPos off = this.shaft.off(brain.folk.getBlockY());
					BlockPos stand = FolkSpots.standableNear(level, off, 3);
					if (stand != null) {
						brain.folk.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, brain.folk.getYRot(), 0);
					}
				}
			}
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return switch (this.leg) {
				case WALK -> "walking up to the pithead";
				case ON, CLIMB -> this.to < this.shaft.deckY() - 2 ? "climbing down the shaft" : "climbing up the shaft";
				case OFF -> "stepping off the ladder";
			};
		}
	}
}
