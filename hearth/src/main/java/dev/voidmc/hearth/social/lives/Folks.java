package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small shared pieces of the lives' folk behaviours: the town's clock, walking across town, the timer. */
final class Folks {
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("lives folk");
	static final double FAR = 56;
	static final int STRIDE = 32;

	private Folks() {
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

	/** Walks toward {@code target}, by way of a point on open ground along the way when it is far. */
	static boolean walk(NpcService.FolkHandle folk, BlockPos target, double speed) {
		BlockPos at = folk.entity().blockPosition();
		double dx = target.getX() - at.getX();
		double dz = target.getZ() - at.getZ();
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d > FAR && folk.entity().level() instanceof ServerLevel level) {
			int x = at.getX() + (int) Math.round(dx / d * STRIDE);
			int z = at.getZ() + (int) Math.round(dz / d * STRIDE);
			if (level.isLoaded(new BlockPos(x, 0, z))) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos step = Venues.standable(level, new BlockPos(x, y, z));
				if (step != null && folk.moveTo(step, speed)) {
					return true;
				}
			}
		}
		return folk.moveTo(target, speed);
	}

	static boolean near(NpcService.FolkHandle folk, BlockPos p, double r) {
		return folk.entity().blockPosition().distSqr(p) <= r * r;
	}

	static long t0() {
		return System.nanoTime();
	}

	static void t1(long t0) {
		TIMER.add(System.nanoTime() - t0);
	}
}
