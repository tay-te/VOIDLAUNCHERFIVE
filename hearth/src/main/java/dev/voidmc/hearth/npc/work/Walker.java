package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Getting about a building site (docs/FOLK.md §6): on the ground by path; on the scaffolding the way players use it —
 * into a column at its foot and up it, along the ring at the height one stands (each column's scaffolding is underfoot
 * at every height), and down a column to step out at the bottom. The pathfinder does not walk on scaffolding, so the
 * ring is moved on by hand: climbing is a steady rise inside the column, walking a move to the next column's middle.
 * While on the scaffolding a builder is not pushed by others (so nobody is shoved off the ring).
 */
final class Walker {
	/** Blocks a tick, climbing inside a column (vanilla climbs at 0.2). */
	private static final double CLIMB = 0.15;

	private boolean failed;
	private int stuck;
	private int towards = -1;
	private int centring;
	/** Where the walk last made headway, and for how long it has not. */
	private @Nullable Vec3 anchor;
	private int noHeadway;
	private String doing = "walking to the work";
	/** Where a builder standing in a column's foot steps out to, and for how long they have been at it. */
	private @Nullable BlockPos out;
	private int outTicks;
	/** Why the last walk failed, for the builders' debug trace: w no way, s stalled, h on another level. */
	private String why = "";

	void reset() {
		this.failed = false;
		this.stuck = 0;
		this.towards = -1;
		this.centring = 0;
		this.anchor = null;
		this.noHeadway = 0;
		this.out = null;
		this.outTicks = 0;
	}

	/** Standing in the foot of a ring column (in its scaffolding), where no path can start. */
	static boolean inFoot(@Nullable Staging st, Folk folk) {
		BlockPos feet = folk.blockPosition();
		return st != null && st.cellAt(feet.getX(), feet.getZ()) >= 0 && folk.level().getBlockState(feet).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
	}

	/**
	 * Out of a column's foot before walking anywhere on the ground: no path starts inside scaffolding, so the builder
	 * steps out by hand to the side nearest the goal that is not another column. True while stepping out.
	 */
	private boolean stepOut(ServerLevel level, @Nullable Staging st, Folk folk, BlockPos goal, int elapsed) {
		if (!inFoot(st, folk)) {
			this.out = null;
			return false;
		}
		BlockPos feet = folk.blockPosition();
		if (this.out == null) {
			double bestD = Double.MAX_VALUE;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos p = feet.relative(d);
				if (st.cellAt(p.getX(), p.getZ()) >= 0 && level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)
					|| !FolkSpots.standable(level, p)) {
					continue;
				}
				double dist = p.distSqr(goal);
				if (dist < bestD) {
					bestD = dist;
					this.out = p;
				}
			}
			if (this.out == null) {
				return false;
			}
			this.outTicks = 0;
		}
		folk.getNavigation().stop();
		double x = this.out.getX() + 0.5;
		double z = this.out.getZ() + 0.5;
		this.outTicks += elapsed;
		if (this.outTicks > 15) {
			folk.setPos(x, this.out.getY(), z);
			folk.setDeltaMovement(Vec3.ZERO);
		} else {
			folk.getMoveControl().setWantedPosition(x, this.out.getY(), z, 0.5);
		}
		return true;
	}

	/**
	 * A walk about the site that has got nowhere for three seconds has failed (a wall in the way, a door shut, a path
	 * that ends short): known in three seconds rather than ten, so the builder chooses again. A long walk (from across
	 * town) is left to the brain's own walk, which moves a folk nobody sees past what holds it up.
	 */
	private boolean stalled(Folk folk, double x, double z, int elapsed) {
		if (Mth.square(x - folk.getX()) + Mth.square(z - folk.getZ()) > 24 * 24) {
			this.anchor = null;
			return false;
		}
		Vec3 here = folk.position();
		if (this.anchor == null || here.distanceToSqr(this.anchor) > 0.5 * 0.5) {
			this.anchor = here;
			this.noHeadway = 0;
			return false;
		}
		this.noHeadway += elapsed;
		return this.noHeadway > 60;
	}

	boolean failed() {
		return this.failed;
	}

	String doing() {
		return this.doing;
	}

	String why() {
		return this.why;
	}

	/** Where a failed walk stood, for the builders' debug log. */
	static String stallInfo(Folk folk, BlockPos goal) {
		net.minecraft.world.level.pathfinder.Path path = folk.getNavigation().getPath();
		StringBuilder b = new StringBuilder();
		b.append(String.format(java.util.Locale.ROOT, "at %.2f,%.2f,%.2f goal %s", folk.getX(), folk.getY(), folk.getZ(), goal.toShortString()));
		b.append(" in ").append(folk.level().getBlockState(folk.blockPosition()).getBlock().getName().getString());
		b.append(" on ").append(folk.level().getBlockState(folk.blockPosition().below()).getBlock().getName().getString());
		if (path == null) {
			b.append(" path none");
		} else {
			b.append(" path ").append(path.getNextNodeIndex()).append('/').append(path.getNodeCount()).append(path.isDone() ? " done" : "")
				.append(path.canReach() ? " reach" : " short");
			if (!path.isDone()) {
				BlockPos n = path.getNextNodePos();
				b.append(" next ").append(n.toShortString()).append(' ').append(folk.level().getBlockState(n).getBlock().getName().getString())
					.append(" over ").append(folk.level().getBlockState(n.below()).getBlock().getName().getString());
			}
		}
		b.append(" nav ").append(folk.getNavigation().isDone() ? "idle" : "moving").append(folk.isPushable() ? "" : " steady")
			.append(folk.isNoGravity() ? " nograv" : "").append(folk.onGround() ? " ground" : " air");
		return b.toString();
	}

	/** The ring column the folk stands in above its foot, or -1 (on the ground, a floor, anywhere else). */
	static int ringCell(@Nullable Staging st, Folk folk) {
		if (st == null) {
			return -1;
		}
		int c = st.cellAt(Mth.floor(folk.getX()), Mth.floor(folk.getZ()));
		if (c < 0 || !st.valid(c) || folk.getY() <= st.base[c] + 0.4) {
			return -1;
		}
		// in the column's scaffolding, or standing on it (not on something built in the cell since)
		BlockPos feet = BlockPos.containing(folk.getX(), folk.getY() + 0.1, folk.getZ());
		return folk.level().getBlockState(feet).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)
			|| folk.level().getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING) ? c : -1;
	}

	boolean onRing(@Nullable Staging st, Folk folk) {
		return ringCell(st, folk) >= 0;
	}

	/** One move toward a spot; true once there. */
	boolean tick(FolkBrain brain, ServerLevel level, BuildSite site, BuilderWork.Spot spot, int elapsed) {
		Staging st = site.staging;
		Folk folk = brain.folk;
		int here = ringCell(st, folk);
		BlockPos goal = spot.feet();
		if (spot.cell() < 0 || st == null || !st.valid(spot.cell())) {
			if (here >= 0) {
				this.doing = "climbing down the scaffold";
				vertical(folk, st, here, st.base[here], elapsed);
				return false;
			}
			release(folk);
			if (stepOut(level, st, folk, goal, elapsed)) {
				this.doing = "stepping out of the scaffold";
				return false;
			}
			this.doing = "walking to the work";
			double gx = goal.getX() + 0.5 - folk.getX();
			double gz = goal.getZ() + 0.5 - folk.getZ();
			// the path ends within half a block or so of the spot's middle (the navigation's waypoint slack): the last
			// stretch is a step across on the same level, not more pathfinding that never gets closer
			boolean at = gx * gx + gz * gz <= 1.3 * 1.3 && Math.abs(folk.getY() - goal.getY()) < 0.75 || brain.walk(level, goal, 0.45, 0.65);
			if (!at && brain.walkFailed() || at && Math.abs(folk.getY() - goal.getY()) >= 0.75) {
				// no way there, or below or above it (a floor or wall top the path does not climb to)
				this.failed = true;
				this.why = (at ? "h" : "w");
				return false;
			}
			if (!at) {
				this.centring = 0;
				openHatch(level, folk);
				if (stalled(folk, goal.getX() + 0.5, goal.getZ() + 0.5, elapsed)) {
					this.failed = true;
					this.why = "s";
				}
				return false;
			}
			// square on the spot: half a block off and the wall beside them would be "in their way"
			double dx = goal.getX() + 0.5 - folk.getX();
			double dz = goal.getZ() + 0.5 - folk.getZ();
			if (dx * dx + dz * dz > 0.15 * 0.15) {
				folk.getNavigation().stop();
				this.centring += elapsed;
				if (this.centring > 12) {
					folk.setPos(goal.getX() + 0.5, folk.getY(), goal.getZ() + 0.5);
					folk.setDeltaMovement(Vec3.ZERO);
				} else {
					folk.getMoveControl().setWantedPosition(goal.getX() + 0.5, folk.getY(), goal.getZ() + 0.5, 0.4);
				}
				return false;
			}
			return true;
		}
		int cell = spot.cell();
		int feet = goal.getY();
		if (here < 0) {
			// on the ground: to the foot of the column, in, and up
			double dx = st.xs[cell] + 0.5 - folk.getX();
			double dz = st.zs[cell] + 0.5 - folk.getZ();
			if (dx * dx + dz * dz < 0.35 * 0.35 && Math.abs(folk.getY() - st.base[cell]) < 0.6) {
				brain.resetWalk();
				folk.getNavigation().stop();
				if (feet <= st.base[cell]) {
					settle(folk, st, cell, feet);
					return true;
				}
				this.doing = "climbing the scaffold";
				folk.setSteady(true);
				vertical(folk, st, cell, feet, elapsed);
				return false;
			}
			release(folk);
			if (stepOut(level, st, folk, new BlockPos(st.xs[cell], st.base[cell], st.zs[cell]), elapsed)) {
				this.doing = "stepping out of the scaffold";
				return false;
			}
			this.doing = "walking to the scaffold";
			boolean at = dx * dx + dz * dz <= 1.3 * 1.3 && Math.abs(folk.getY() - st.base[cell]) < 0.75
				|| brain.walk(level, new BlockPos(st.xs[cell], st.base[cell], st.zs[cell]), 0.5, 0.65);
			if (!at && brain.walkFailed()) {
				this.failed = true;
				this.why = "w";
			} else if (at) {
				// the path ends near the middle of the column's foot: in under the scaffolding
				folk.getNavigation().stop();
				this.centring += elapsed;
				if (this.centring > 12) {
					folk.setPos(st.xs[cell] + 0.5, folk.getY(), st.zs[cell] + 0.5);
					folk.setDeltaMovement(Vec3.ZERO);
				} else {
					folk.getMoveControl().setWantedPosition(st.xs[cell] + 0.5, folk.getY(), st.zs[cell] + 0.5, 0.4);
				}
			} else {
				this.centring = 0;
				if (stalled(folk, st.xs[cell] + 0.5, st.zs[cell] + 0.5, elapsed)) {
					this.failed = true;
					this.why = "s";
				}
			}
			return false;
		}
		folk.setSteady(true);
		if (here == cell) {
			if (Math.abs(folk.getY() - feet) > 0.02) {
				this.doing = folk.getY() < feet ? "climbing the scaffold" : "climbing down the scaffold";
				vertical(folk, st, cell, feet, elapsed);
				return false;
			}
			settle(folk, st, cell, feet);
			return true;
		}
		UUID me = brain.resident.id;
		int h = Mth.floor(folk.getY() + 0.5);
		int dir = route(site, st, here, cell, h, me);
		if (dir == 0 && h != feet && st.supports(here, feet) && route(site, st, here, cell, feet, me) != 0) {
			// round at the height of the spot instead
			this.doing = feet > h ? "climbing the scaffold" : "climbing down the scaffold";
			vertical(folk, st, here, feet, elapsed);
			return false;
		}
		if (dir == 0) {
			// no way round up here (a gap in the ring, or a builder in the way): down, round by the ground, up again
			this.doing = "climbing down the scaffold";
			vertical(folk, st, here, st.base[here], elapsed);
			return false;
		}
		int next = st.next(here, dir);
		if (next != this.towards) {
			this.towards = next;
			this.stuck = 0;
		}
		this.doing = "moving along the scaffold";
		folk.getNavigation().stop();
		folk.setNoGravity(false);
		double x = st.xs[next] + 0.5;
		double z = st.zs[next] + 0.5;
		folk.getMoveControl().setWantedPosition(x, h, z, 0.6);
		this.stuck += elapsed;
		if (this.stuck > 30) {
			// edged round a corner post or a neighbour: a step across
			folk.setPos(x, h, z);
			folk.setDeltaMovement(Vec3.ZERO);
			this.stuck = 0;
		}
		return false;
	}

	/**
	 * Opens a shut trapdoor the path goes through, as a player does: the hatch over a loft ladder that a builder upstairs
	 * has just fitted (the pathfinder counts trapdoors as open, so the path down goes through it, and the folk would
	 * stand on it for good). Left open, as a busy builder leaves it.
	 */
	static void openHatch(ServerLevel level, Folk folk) {
		net.minecraft.world.level.pathfinder.Path path = folk.getNavigation().getPath();
		if (path == null || path.isDone()) {
			return;
		}
		net.minecraft.world.level.pathfinder.Node next = path.getNextNode();
		BlockPos feet = folk.blockPosition();
		BlockPos node = next.asBlockPos();
		BlockPos[] look = next.y < feet.getY() ? new BlockPos[]{feet.below(), node, node.above()}
			: next.y > feet.getY() ? new BlockPos[]{feet.above(), feet.above(2), node.above()} : new BlockPos[]{node, node.above()};
		for (BlockPos p : look) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock && !s.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
				level.setBlock(p, s.setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				level.playSound(null, p, net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
			}
		}
	}

	/** Which way round the ring (+1, -1) reaches {@code to} from {@code from} at feet height {@code feet}, or 0 if neither does. */
	static int route(BuildSite site, Staging st, int from, int to, int feet, UUID me) {
		int best = 0;
		int bestLen = Integer.MAX_VALUE;
		for (int dir = 1; dir >= -1; dir -= 2) {
			int len = st.steps(from, to, dir);
			if (len >= bestLen) {
				continue;
			}
			boolean ok = true;
			int c = from;
			for (int k = 0; k < len && ok; k++) {
				c = st.next(c, dir);
				ok = st.supports(c, feet) && !occupied(site, st, c, feet, me);
			}
			if (ok) {
				best = dir;
				bestLen = len;
			}
		}
		return best;
	}

	private static boolean occupied(BuildSite site, Staging st, int c, int feet, UUID me) {
		for (Map.Entry<UUID, Long> e : site.spots.entrySet()) {
			if (e.getKey().equals(me)) {
				continue;
			}
			long p = e.getValue();
			if (BlockPos.getX(p) == st.xs[c] && BlockPos.getZ(p) == st.zs[c] && Math.abs(BlockPos.getY(p) - feet) < 2) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Down off the building the way one gets down a wall with no stair yet: over the edge beside them and down the face
	 * to the ground (a steady descent, as down a ladder). For a builder up on a wall top or a roof whose path down is
	 * gone. True once on the ground.
	 */
	boolean climbDown(Folk folk, BlockPos column, int groundY, int elapsed) {
		this.doing = "climbing down";
		folk.getNavigation().stop();
		double x = column.getX() + 0.5;
		double z = column.getZ() + 0.5;
		double dx = x - folk.getX();
		double dz = z - folk.getZ();
		if (dx * dx + dz * dz > 0.05 * 0.05) {
			// over the edge (a step out into the air beside the wall)
			double d = Math.sqrt(dx * dx + dz * dz);
			double step = Math.min(d, 0.25 * Math.max(1, elapsed));
			folk.setNoGravity(true);
			folk.setPos(folk.getX() + dx / d * step, folk.getY(), folk.getZ() + dz / d * step);
			folk.setDeltaMovement(Vec3.ZERO);
			return false;
		}
		double y = folk.getY();
		double ny = Math.max(groundY, y - CLIMB * Math.max(1, elapsed));
		folk.setNoGravity(true);
		folk.setPos(x, ny, z);
		folk.setDeltaMovement(Vec3.ZERO);
		if (ny <= groundY) {
			release(folk);
			return true;
		}
		return false;
	}

	/** Keeps a builder where they work: on the scaffolding, square in the column and still. */
	void hold(FolkBrain brain, @Nullable Staging st, BuilderWork.Spot spot) {
		if (spot.cell() >= 0 && st != null && spot.cell() < st.n && ringCell(st, brain.folk) == spot.cell()) {
			settle(brain.folk, st, spot.cell(), spot.feet().getY());
		}
	}

	/** Up or down inside a column, steadily. */
	private static void vertical(Folk folk, Staging st, int c, int to, int elapsed) {
		folk.getNavigation().stop();
		folk.setNoGravity(true);
		double x = st.xs[c] + 0.5;
		double z = st.zs[c] + 0.5;
		double y = folk.getY();
		double step = Math.min(0.6, CLIMB * Math.max(1, elapsed));
		double ny = y < to ? Math.min(to, y + step) : Math.max(to, y - step);
		folk.setPos(x, ny, z);
		folk.setDeltaMovement(Vec3.ZERO);
		if (ny == to) {
			folk.setNoGravity(false);
		}
	}

	private static void settle(Folk folk, Staging st, int c, int feet) {
		double x = st.xs[c] + 0.5;
		double z = st.zs[c] + 0.5;
		if (Math.abs(folk.getX() - x) > 0.1 || Math.abs(folk.getZ() - z) > 0.1 || Math.abs(folk.getY() - feet) > 0.05) {
			folk.setPos(x, feet, z);
		}
		folk.setDeltaMovement(folk.getDeltaMovement().multiply(0, 1, 0));
		folk.setNoGravity(false);
	}

	/** The task ends: a builder up on the scaffolding comes down it (at once) and stands at its foot. */
	void leave(FolkBrain brain, @Nullable Staging st) {
		int c = ringCell(st, brain.folk);
		if (c >= 0) {
			brain.folk.teleportTo(st.xs[c] + 0.5, st.base[c], st.zs[c] + 0.5);
		}
		release(brain.folk);
	}

	static void release(Folk folk) {
		folk.setNoGravity(false);
		folk.setSteady(false);
	}
}
