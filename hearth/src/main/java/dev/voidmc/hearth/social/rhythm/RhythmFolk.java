package dev.voidmc.hearth.social.rhythm;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/** Small shared pieces of the rhythm's folk behaviours: the town's clock, the timer, a player near. */
final class RhythmFolk {
	static final LivingWorldStats.Timer FOLK_TIMER = LivingWorldStats.timer("rhythm folk");

	private RhythmFolk() {
	}

	static int minute(Settlement s) {
		return SocialTime.minute(TownLife.now(s));
	}

	static long today(Settlement s) {
		return TownLife.today(s);
	}

	/** Whether a minute falls in [from, to), allowing for the span to run past midnight. */
	static boolean within(int minute, int from, int to) {
		return from <= to ? minute >= from && minute < to : minute >= from || minute < to;
	}

	static boolean anyPlayerNear(NpcService.FolkHandle folk, double range) {
		return folk.entity().level() instanceof ServerLevel level && level.getNearestPlayer(folk.entity(), range) != null;
	}

	static Player nearestPlayer(NpcService.FolkHandle folk, double range, Predicate<Player> which) {
		if (!(folk.entity().level() instanceof ServerLevel level)) {
			return null;
		}
		Player best = null;
		double bd = range * range;
		for (Player p : level.players()) {
			double d = p.distanceToSqr(folk.entity());
			if (d <= bd && !p.isSpectator() && which.test(p)) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/**
	 * Walks toward {@code target}; when it is far (beyond the pathfinder's comfortable reach) toward a point on open ground
	 * {@link #STRIDE} blocks along the way first, so a walk across town arrives instead of failing to find a path.
	 */
	static boolean walk(NpcService.FolkHandle folk, BlockPos target, double speed) {
		BlockPos at = folk.entity().blockPosition();
		double dx = target.getX() - at.getX();
		double dz = target.getZ() - at.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > FAR && folk.entity().level() instanceof ServerLevel level) {
			int x = at.getX() + (int) Math.round(dx / d * STRIDE);
			int z = at.getZ() + (int) Math.round(dz / d * STRIDE);
			if (level.isLoaded(new BlockPos(x, 0, z))) {
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos step = dev.voidmc.hearth.social.life.Venues.standable(level, new BlockPos(x, y, z));
				if (step != null && folk.moveTo(step, speed)) {
					return true;
				}
			}
		}
		return folk.moveTo(target, speed);
	}

	static final double FAR = 56;
	static final int STRIDE = 32;

	static boolean near(NpcService.FolkHandle folk, BlockPos p, double r) {
		return folk.entity().blockPosition().distSqr(p) <= r * r;
	}

	/** Times one step of a behaviour into the lane's folk timer. */
	static long t0() {
		return System.nanoTime();
	}

	static void t1(long t0) {
		FOLK_TIMER.add(System.nanoTime() - t0);
	}
}
