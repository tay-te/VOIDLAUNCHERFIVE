package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The everyday tasks: walking, lingering at something, sitting, sleeping, eating, wandering, fleeing, sounding the alarm. */
public final class Tasks {
	private Tasks() {
	}

	/** Something happening while a folk lingers: tool sounds, sparks, smoke, the turn of a page. */
	@FunctionalInterface
	public interface Effect {
		void tick(FolkBrain brain, ServerLevel level, int elapsed, @Nullable BlockPos at);
	}

	/** Periodic sound, particles and a swing of the arm, roughly every {@code interval} ticks. */
	public static Effect every(int interval, @Nullable SoundEvent sound, float volume, @Nullable Effect extra, boolean swing) {
		int[] timer = {interval / 2};
		return (brain, level, elapsed, at) -> {
			timer[0] -= elapsed;
			if (timer[0] > 0) {
				return;
			}
			timer[0] = interval + brain.random.nextInt(Math.max(1, interval / 2));
			BlockPos where = at != null ? at : brain.folk.blockPosition();
			if (sound != null) {
				level.playSound(null, where, sound, SoundSource.NEUTRAL, volume, 0.9F + brain.random.nextFloat() * 0.2F);
			}
			if (swing) {
				brain.swing();
			}
			if (extra != null) {
				extra.tick(brain, level, elapsed, at);
			}
		};
	}

	public static final class GoTo implements Task {
		private final BlockPos target;
		private final double within;
		private final double speed;
		private int timeout;

		public GoTo(BlockPos target, double within, double speed, int timeout) {
			this.target = target;
			this.within = within;
			this.speed = speed;
			this.timeout = timeout;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (brain.walk(level, this.target, this.within, this.speed)) {
				return Status.DONE;
			}
			this.timeout -= elapsed;
			return brain.walkFailed() || this.timeout < 0 ? Status.FAILED : Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return "walking to " + this.target.toShortString();
		}
	}

	/** Goes somewhere (optional), then stays a while facing something, in a pose, holding things, with an effect. */
	public static final class Linger implements Task {
		private final @Nullable BlockPos at;
		private final @Nullable BlockPos look;
		private final FolkPose pose;
		private final ItemStack main;
		private final ItemStack off;
		private final @Nullable Effect effect;
		private final String what;
		private int ticks;
		private boolean arrived;
		private int walkTimeout = 20 * 60;

		public Linger(@Nullable BlockPos at, @Nullable BlockPos look, FolkPose pose, int ticks, ItemStack main, ItemStack off, @Nullable Effect effect,
			String what) {
			this.at = at;
			this.look = look;
			this.pose = pose;
			this.ticks = ticks;
			this.main = main;
			this.off = off;
			this.effect = effect;
			this.what = what;
		}

		public static Linger at(@Nullable BlockPos at, int ticks, String what) {
			return new Linger(at, null, FolkPose.NONE, ticks, ItemStack.EMPTY, ItemStack.EMPTY, null, what);
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (!this.arrived) {
				this.walkTimeout -= elapsed;
				if (this.at == null || brain.walk(level, this.at, 1.3, 0.6) || brain.walkFailed() || this.walkTimeout < 0) {
					this.arrived = true;
					brain.resetWalk();
					brain.hold(this.main, this.off);
					brain.folk.setFolkPose(this.pose);
				}
				return Status.RUNNING;
			}
			if (this.look != null && (brain.folk.tickCount & 15) == 0) {
				brain.faceTowards(this.look);
			}
			if (brain.folk.folkPose() != this.pose && brain.folk.folkPose() == FolkPose.NONE) {
				brain.folk.setFolkPose(this.pose);
			}
			if (this.effect != null) {
				this.effect.tick(brain, level, elapsed, this.look);
			}
			this.ticks -= elapsed;
			return this.ticks <= 0 ? Status.DONE : Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.what + (this.arrived ? "" : " (on the way)");
		}
	}

	/** Sits on a seat (a stair chair, a bench) for a while. */
	public static final class Sit implements Task {
		private final Poi seat;
		private final ItemStack held;
		private final @Nullable Effect effect;
		private int ticks;
		private boolean seated;
		private int walkTimeout = 20 * 45;

		public Sit(Poi seat, int ticks, ItemStack held, @Nullable Effect effect) {
			this.seat = seat;
			this.ticks = ticks;
			this.held = held;
			this.effect = effect;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			Folk folk = brain.folk;
			if (!this.seated) {
				this.walkTimeout -= elapsed;
				if (brain.walk(level, this.seat.pos(), 1.6, 0.6)) {
					if (seatTaken(level, folk)) {
						return Status.FAILED;
					}
					brain.resetWalk();
					BlockPos p = this.seat.pos();
					BlockState s = level.getBlockState(p);
					VoxelShape shape = s.getCollisionShape(level, p);
					double top = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
					Direction f = this.seat.facing();
					folk.getNavigation().stop();
					folk.teleportTo(p.getX() + 0.5 + f.getStepX() * 0.12, p.getY() + Math.min(top, 0.5), p.getZ() + 0.5 + f.getStepZ() * 0.12);
					float yaw = f.toYRot();
					folk.setYRot(yaw);
					folk.setYBodyRot(yaw);
					folk.setYHeadRot(yaw);
					folk.setFolkPose(FolkPose.SIT);
					brain.hold(this.held, ItemStack.EMPTY);
					this.seated = true;
				} else if (brain.walkFailed() || this.walkTimeout < 0) {
					return Status.FAILED;
				}
				return Status.RUNNING;
			}
			folk.setDeltaMovement(Vec3.ZERO);
			folk.getNavigation().stop();
			if (this.effect != null) {
				this.effect.tick(brain, level, elapsed, this.seat.pos());
			}
			this.ticks -= elapsed;
			return this.ticks <= 0 ? Status.DONE : Status.RUNNING;
		}

		private boolean seatTaken(ServerLevel level, Folk self) {
			return !level.getEntitiesOfClass(Folk.class, new net.minecraft.world.phys.AABB(this.seat.pos()).inflate(0.2),
				f -> f != self && f.folkPose() == FolkPose.SIT).isEmpty();
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			if (this.seated) {
				brain.folk.setFolkPose(FolkPose.NONE);
				BlockPos stand = FolkSpots.standableNear(level, this.seat.pos().relative(this.seat.facing()), 2);
				if (stand != null) {
					brain.folk.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
				}
			}
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.seated ? "sitting" : "going to sit";
		}
	}

	/** Goes to bed and sleeps until the day moves on. */
	public static final class Sleep implements Task {
		/** How far from bed a folk going to bed out of sight is taken home by their record instead of walking. */
		static final int FAR_HOME = 32;
		private final @Nullable BlockPos bed;
		private final BlockPos home;
		private boolean asleep;
		private int walkTimeout = 20 * 90;

		public Sleep(@Nullable BlockPos bed, BlockPos home) {
			this.bed = bed;
			this.home = home;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			Folk folk = brain.folk;
			if (brain.woken()) {
				// woken by a fire or an alarm: stay up until it is over
				if (folk.isSleeping()) {
					folk.stopSleeping();
				}
				this.asleep = false;
				return Status.RUNNING;
			}
			if (this.asleep) {
				if (!folk.isSleeping()) {
					this.asleep = false;
				}
				return Status.RUNNING;
			}
			this.walkTimeout -= elapsed;
			BlockPos target = this.bed != null ? this.bed : this.home;
			if (target.distSqr(folk.blockPosition()) > FAR_HOME * FAR_HOME && brain.nearestPlayer() > 16 && brain.unseen(level, folk.blockPosition())) {
				// a long way home at bedtime and nobody to see them go: they are home (their record goes to bed)
				brain.slipAway();
				return Status.RUNNING;
			}
			boolean there = brain.walk(level, target, this.bed != null ? 1.8 : 1.5, 0.6);
			if (there || brain.walkFailed() || this.walkTimeout < 0) {
				brain.resetWalk();
				// in bed if they got there or as good as (a step from it), or if nobody would see them slip into it (a stair
				// the pathfinder will not take)
				if (this.bed != null && (there || folk.blockPosition().closerThan(this.bed, 4) || brain.nearestPlayer() > 24 || brain.unseen(level, this.bed))
					&& level.getBlockState(this.bed).getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock && !bedTaken(level, folk)) {
					folk.sleepIn(this.bed);
					this.asleep = true;
				} else {
					// No bed to be had: rest quietly at home.
					folk.setFolkPose(FolkPose.SIT);
					this.asleep = true;
				}
			}
			return Status.RUNNING;
		}

		private boolean bedTaken(ServerLevel level, Folk self) {
			return !level.getEntitiesOfClass(Folk.class, new net.minecraft.world.phys.AABB(this.bed).inflate(0.5),
				f -> f != self && f.isSleeping() && this.bed.equals(f.getSleepingPos().orElse(null))).isEmpty();
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			if (brain.folk.isSleeping()) {
				brain.folk.stopSleeping();
			}
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.asleep ? "asleep" : "going to bed";
		}
	}

	/** Several tasks one after another; fails over to the next when one fails. */
	public static final class Sequence implements Task {
		private final Deque<Task> tasks;
		private final String what;

		public Sequence(String what, Task... tasks) {
			this.tasks = new ArrayDeque<>(List.of(tasks));
			this.what = what;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			Task t = this.tasks.peekFirst();
			if (t == null) {
				return Status.DONE;
			}
			Status s = t.tick(brain, level, elapsed);
			if (s != Status.RUNNING) {
				t.stop(brain, level);
				this.tasks.pollFirst();
			}
			return this.tasks.isEmpty() ? Status.DONE : Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			Task t = this.tasks.peekFirst();
			if (t != null) {
				t.stop(brain, level);
			}
		}

		@Override
		public String describe() {
			Task t = this.tasks.peekFirst();
			return this.what + (t != null ? ": " + t.describe() : "");
		}
	}

	/** A few unhurried hops round a centre. */
	public static final class Wander implements Task {
		private final BlockPos centre;
		private final int radius;
		private int hops;
		private @Nullable BlockPos target;
		private int pause;

		public Wander(BlockPos centre, int radius, int hops) {
			this.centre = centre;
			this.radius = radius;
			this.hops = hops;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (this.pause > 0) {
				this.pause -= elapsed;
				return Status.RUNNING;
			}
			if (this.target == null) {
				if (this.hops-- <= 0) {
					return Status.DONE;
				}
				BlockPos guess = this.centre.offset(brain.random.nextInt(2 * this.radius + 1) - this.radius, 0,
					brain.random.nextInt(2 * this.radius + 1) - this.radius);
				guess = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, guess);
				if (Math.abs(guess.getY() - this.centre.getY()) > 6) {
					guess = this.centre;
				}
				this.target = FolkSpots.standableNear(level, guess, 2);
				if (this.target == null) {
					this.target = this.centre;
				}
			}
			if (brain.walk(level, this.target, 1.5, 0.5) || brain.walkFailed()) {
				brain.resetWalk();
				this.target = null;
				this.pause = 40 + brain.random.nextInt(120);
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return "strolling";
		}
	}

	/** Runs from a threat, then heads home. */
	public static final class Flee implements Task {
		private @Nullable BlockPos away;
		private int ticks = 160;

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.ticks -= elapsed;
			var threat = brain.threat();
			if (this.away == null || (threat != null && brain.folk.distanceToSqr(threat) < 16 && brain.folk.getNavigation().isDone())) {
				Vec3 from = threat != null ? threat.position() : brain.folk.position();
				Vec3 v = DefaultRandomPos.getPosAway(brain.folk, 14, 6, from);
				BlockPos home = brain.places(level).home;
				// Prefer running home when home lies away from the danger.
				if (home.distSqr(BlockPos.containing(from)) > brain.folk.blockPosition().distSqr(BlockPos.containing(from))) {
					this.away = home;
				} else {
					this.away = v != null ? BlockPos.containing(v) : home;
				}
				brain.resetWalk();
			}
			if (brain.walk(level, this.away, 1.5, 0.85) || brain.walkFailed()) {
				brain.resetWalk();
				this.away = null;
			}
			return this.ticks <= 0 ? Status.DONE : Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return "running away";
		}
	}

	/** The town is attacked: the nearest folk rings the bell, everyone gets indoors and keeps their head down. */
	public static final class Alarm implements Task {
		private final @Nullable BlockPos bell;
		private boolean rung;
		private boolean home;

		public Alarm(@Nullable BlockPos bell) {
			this.bell = bell;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (this.bell != null && !this.rung) {
				if (brain.walk(level, this.bell, 2.2, 0.85) || brain.walkFailed()) {
					brain.resetWalk();
					this.rung = true;
					BlockState s = level.getBlockState(this.bell);
					if (s.getBlock() instanceof BellBlock bellBlock) {
						bellBlock.attemptToRing(brain.folk, level, this.bell, null);
					}
				}
				return Status.RUNNING;
			}
			if (!this.home) {
				BlockPos target = brain.places(level).bed != null ? brain.places(level).bed : brain.places(level).home;
				if (brain.walk(level, target, 1.8, 0.85) || brain.walkFailed()) {
					brain.resetWalk();
					this.home = true;
					brain.folk.setFolkPose(FolkPose.COWER);
				}
				return Status.RUNNING;
			}
			brain.folk.setFolkPose(FolkPose.COWER);
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.rung || this.bell == null ? "hiding" : "ringing the bell";
		}
	}

	/** Eating: crumbs and the sound of it, now and then. */
	public static Effect eating(ItemStack food) {
		return every(70, SoundEvents.GENERIC_EAT.value(), 0.4F, (brain, level, elapsed, at) -> {
			Vec3 mouth = brain.folk.getEyePosition().add(brain.folk.getLookAngle().scale(0.3));
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food.getItem()), mouth.x, mouth.y - 0.1, mouth.z, 4, 0.08, 0.05, 0.08, 0.04);
		}, false);
	}

	/** What a culture eats with its hands. */
	public static ItemStack meal(FolkBrain brain) {
		return switch (brain.settlement.culture()) {
			case TROPICAL -> new ItemStack(Items.COOKED_COD);
			case MARITIME -> new ItemStack(Items.COOKED_SALMON);
			case DESERT, STEPPE -> new ItemStack(Items.COOKED_MUTTON);
			case KARST, WISTERIA -> new ItemStack(Items.BEETROOT_SOUP);
			case NORTHERN, TUNDRA -> new ItemStack(Items.BAKED_POTATO);
			case BAYOU -> new ItemStack(Items.RABBIT_STEW);
			case MEDITERRANEAN -> new ItemStack(Items.APPLE);
			default -> brain.random.nextBoolean() ? new ItemStack(Items.BREAD) : new ItemStack(Items.COOKED_CHICKEN);
		};
	}
}
