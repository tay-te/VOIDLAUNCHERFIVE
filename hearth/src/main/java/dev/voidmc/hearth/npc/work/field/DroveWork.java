package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The drove (docs/LIVING_WORLD_2.md §6.1, §6.3): a herder whose pasture has a far meadow drives the flock out at 07:00,
 * grazes it there and drives it home for 18:00. The herder walks the way with a crook, behind the flock; the beasts
 * themselves are steered by {@link Herds} from where the herder is. It is the herder's routine work (their time is
 * labour the sim credits, as it would abstractly); {@code HerderWork} asks here first and tends the flock on the meadow
 * with its own tasks when it has arrived.
 */
public final class DroveWork {
	private DroveWork() {
	}

	/** The drove's phase for a pasture's site now. */
	public static Commute.Where phase(ServerLevel level, RemoteSites.Site site) {
		int[] w = site.window();
		BlockPos from = site.from() != null ? site.from() : site.work();
		if (w == null) {
			return Commute.Where.NONE;
		}
		return Commute.at(NpcTime.minuteOfDay(level), w[0], w[1], FieldSites.travelMinutes(from, site.work()));
	}

	/**
	 * The herder's next task with the flock out: the way out, the way home, or (out on the meadow but not there yet) the
	 * walk to it. Null when the drove is not on, or the herder is on the meadow (tend the flock there).
	 */
	public static @Nullable Task next(FolkBrain brain, ServerLevel level, RemoteSites.Site site) {
		Commute.Where at = phase(level, site);
		BlockPos from = site.from() != null ? site.from() : site.work();
		return switch (at.phase()) {
			case NONE -> null;
			case OUT -> new Way(site.work(), Commute.Phase.OUT, site, "driving the flock out to the meadow");
			case BACK -> new Way(from, Commute.Phase.BACK, site, "driving the flock home");
			case AT -> brain.folk.blockPosition().distSqr(site.work()) > sq(site.radius() + 10) && Math.abs(brain.folk.getY() - site.work().getY()) < 40
				? new Way(site.work(), Commute.Phase.AT, site, "going out to the flock") : null;
		};
	}

	/** The meadow a flock grazes, as a box (the herder's round-up keeps the beasts in it). */
	public static AABB meadow(RemoteSites.Site site) {
		BlockPos c = site.work();
		int r = site.radius();
		return new AABB(c.getX() - r, c.getY() - 6, c.getZ() - r, c.getX() + r + 1, c.getY() + 8, c.getZ() + r + 1);
	}

	private static double sq(double v) {
		return v * v;
	}

	/**
	 * Walking the way, a stretch at a time (a long walk is a string of short paths over the land between): crook in hand,
	 * at the flock's pace, now and then a word to the beasts. Ends at the far end, or when the day moves on to the next
	 * phase.
	 */
	static final class Way implements Task {
		/** Blocks a stretch reaches ahead along the way. */
		private static final int STRETCH = 20;
		private final BlockPos target;
		private final Commute.Phase phase;
		private final RemoteSites.Site site;
		private final String what;
		private @Nullable BlockPos stretch;
		private int checked;
		private int failures;

		Way(BlockPos target, Commute.Phase phase, RemoteSites.Site site, String what) {
			this.target = target;
			this.phase = phase;
			this.site = site;
			this.what = what;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.checked += elapsed;
			if (this.checked >= 100) {
				this.checked = 0;
				Commute.Phase now = phase(level, this.site).phase();
				if (now != this.phase && !(this.phase == Commute.Phase.AT && now == Commute.Phase.AT)) {
					return Status.DONE;
				}
			}
			brain.hold(new ItemStack(Items.STICK), new ItemStack(Items.WHEAT));
			Vec3 pos = brain.folk.position();
			double dx = this.target.getX() + 0.5 - pos.x;
			double dz = this.target.getZ() + 0.5 - pos.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < 3.0) {
				brain.resetWalk();
				return Status.DONE;
			}
			if (this.stretch == null) {
				double f = Math.min(1, STRETCH / Math.max(1, d));
				BlockPos ahead = Commute.along(brain.folk.blockPosition(), this.target, f);
				this.stretch = FieldPlacement.ground(level, ahead);
				if (!level.isLoaded(this.stretch)) {
					// the way runs on out of the loaded land: the herder waits at its edge (folded soon enough)
					this.stretch = null;
					return Status.RUNNING;
				}
			}
			if (brain.walk(level, this.stretch, 2.0, 0.55)) {
				this.stretch = null;
				this.failures = 0;
				WorkBarks.maybe(brain, level, "drove", 6, null);
				if (brain.random.nextInt(5) == 0) {
					brain.swing();
				}
			} else if (brain.walkFailed()) {
				brain.resetWalk();
				this.stretch = null;
				if (++this.failures > 6) {
					return Status.FAILED;
				}
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.resetWalk();
			brain.folk.setFolkPose(FolkPose.NONE);
		}

		@Override
		public String describe() {
			return this.what;
		}
	}
}
