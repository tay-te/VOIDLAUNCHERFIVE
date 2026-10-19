package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.TownState;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.service.BehaviourRunner;
import dev.voidmc.hearth.npc.service.Behaviours;
import dev.voidmc.hearth.npc.service.FolkHandleImpl;
import dev.voidmc.hearth.npc.talk.Greetings;
import dev.voidmc.hearth.npc.work.WorkBehaviour;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The mind of one embodied resident: their day ({@link Schedule}), what overrides it (danger, a raid, rain), what they
 * are doing about it now ({@link Task}), and the small human things on the side: looking at people, greeting the
 * player, chatting with neighbours.
 *
 * <p>Other modules' {@link NpcService.FolkBehaviour}s run alongside the routine through a {@link BehaviourRunner}: the
 * routine itself counts as priority {@link #routinePriority} (work 50, sleep 90, fleeing 250...), and a behaviour ranked
 * above it takes the folk over until it is done; the routine then plans afresh.
 *
 * <p>Cheap by design. It thinks every few ticks (more often when a player is close), re-reads its schedule once a
 * second, asks for paths only when it must and never more than once per cooldown, and looks for threats with one
 * small box query a second.
 */
public final class FolkBrain {
	private static final int EVALUATE_EVERY = 20;
	/** Ticks a player is remembered as greeted. */
	private static final int GREET_MEMORY = 20 * 60 * 8;

	public final Folk folk;
	public final Settlement settlement;
	public final Resident resident;
	public final TownState town;
	public final NpcData data;
	public final Set<Trait> traits;
	public final RandomSource random;
	private final long seed;
	private WorkBehaviour work;
	/** The trade {@link #work} was made for: when the sim moves the resident to another trade, the work follows. */
	private JobType workJob;
	private final BehaviourRunner behaviours = new BehaviourRunner();
	private final FolkHandleImpl handle;
	private final BehaviourRunner.Owner owner = new BehaviourRunner.Owner() {
		@Override
		public void takeOver(NpcService.FolkBehaviour behaviour) {
			onTakeOver();
		}

		@Override
		public void handBack(NpcService.FolkBehaviour behaviour) {
			onHandBack();
		}
	};
	private int nextPoll;
	private @Nullable ServerLevel lastLevel;

	private @Nullable Schedule schedule;
	private long scheduleDay = Long.MIN_VALUE;
	private Schedule.@Nullable Block block;
	private Activity activity = Activity.WAKE;
	private @Nullable Task task;
	private @Nullable ResidentPlaces places;
	private long placesAt = -1_000_000_000L;

	private int lastThink;
	private int nextEvaluate;
	private int thinkInterval = 2;
	private double nearestPlayer = Double.MAX_VALUE;
	private @Nullable LivingEntity threat;
	private int threatCount;

	// Walking.
	private @Nullable BlockPos walkTarget;
	private int nextRepath;
	private int stuckFor;
	private @Nullable Vec3 lastProgressPos;
	private boolean walkFailed;

	// People.
	private @Nullable ServerPlayer engagedWith;
	private int engagedUntil;
	private final Map<UUID, Integer> greeted = new HashMap<>();
	private @Nullable Folk chatPartner;
	private int chatUntil;
	private int poseUntil;
	private int workTicks;

	public FolkBrain(Folk folk, Settlement settlement, Resident resident, TownState town) {
		this.folk = folk;
		this.settlement = settlement;
		this.resident = resident;
		this.town = town;
		this.data = NpcData.of(resident);
		this.traits = this.data.traits();
		this.seed = resident.id.getMostSignificantBits() ^ resident.id.getLeastSignificantBits();
		this.random = RandomSource.create(this.seed ^ folk.getId());
		this.work = WorkBehaviour.of(resident.job, resident.age);
		this.workJob = resident.job;
		this.handle = new FolkHandleImpl(this);
		this.lastThink = folk.tickCount;
		// Stagger evaluations across a crowd.
		this.nextEvaluate = folk.tickCount + (int) Math.floorMod(this.seed, EVALUATE_EVERY);
		this.nextPoll = folk.tickCount + 1 + (int) Math.floorMod(this.seed >> 5, NpcModule.config().perception().behaviourPoll());
	}

	public static NpcConfig.Ai config() {
		return NpcModule.config().ai();
	}

	// ---------------------------------------------------------------- the tick

	public void tick(ServerLevel level) {
		int now = this.folk.tickCount;
		this.lastLevel = level;
		if (now >= this.nextPoll) {
			this.nextPoll = now + NpcModule.config().perception().behaviourPoll();
			List<NpcService.FolkBehaviour> all = Behaviours.all();
			if (!all.isEmpty() && all.get(0).priority() > Math.max(routinePriority(), this.behaviours.priority())) {
				this.behaviours.poll(this.handle, routinePriority(), all, this.owner);
			}
		}
		if (this.behaviours.active()) {
			// The day and its dangers stay current, so a reaction above the behaviour can still take over.
			if (now >= this.nextEvaluate) {
				this.nextEvaluate = now + EVALUATE_EVERY;
				evaluate(level);
			}
			if (this.behaviours.active() && this.behaviours.tick(this.handle, this.owner)) {
				return;
			}
		}
		if (this.engagedWith != null) {
			if (now >= this.nextEvaluate) {
				// danger still counts while they talk: a monster in sight or the alarm ends the conversation
				this.nextEvaluate = now + EVALUATE_EVERY;
				evaluate(level);
				if ((this.activity == Activity.FLEE || this.activity == Activity.ALARM) && this.engagedWith != null) {
					ServerPlayer p = this.engagedWith;
					disengage();
					NpcModule.conversations().close(p, this.folk.getId());
				}
			}
			if (this.engagedWith != null) {
				tickEngaged(level, now);
				return;
			}
		}
		if (now - this.lastThink < this.thinkInterval) {
			return;
		}
		int elapsed = now - this.lastThink;
		this.lastThink = now;
		if (now >= this.nextEvaluate) {
			this.nextEvaluate = now + EVALUATE_EVERY;
			evaluate(level);
		}
		if (this.poseUntil != 0 && now >= this.poseUntil) {
			this.poseUntil = 0;
			this.folk.setFolkPose(FolkPose.NONE);
		}
		if (this.chatPartner != null) {
			tickChat(level, now);
			return;
		}
		if (this.task == null) {
			this.task = Plans.plan(this, level);
		}
		boolean bySteps = false;
		if (this.task != null) {
			bySteps = this.task.creditedBySteps();
			Task.Status status = this.task.tick(this, level, elapsed);
			if (this.slipAway) {
				// gone home out of sight: the record takes them the rest of the way (to bed), and the entity is folded
				this.slipAway = false;
				NpcModule.embodiment().fold(level, this.folk, true);
				return;
			}
			if (status != Task.Status.RUNNING) {
				this.task.stop(this, level);
				this.task = null;
			}
		}
		if (this.activity == Activity.WORK && !bySteps) {
			this.workTicks += elapsed;
		}
		social(level, now);
	}

	/** Re-reads the day and the dangers; switches what they are doing if either changed. */
	private void evaluate(ServerLevel level) {
		Player near = level.getNearestPlayer(this.folk, -1.0);
		this.nearestPlayer = near == null ? Double.MAX_VALUE : near.distanceTo(this.folk);
		NpcConfig.Ai ai = config();
		this.thinkInterval = this.nearestPlayer <= ai.nearDistance() ? ai.thinkNear() : this.nearestPlayer <= ai.midDistance() ? ai.thinkMid() : ai.thinkFar();

		long day = NpcTime.today();
		if (this.schedule == null || day != this.scheduleDay) {
			this.schedule = Schedule.of(this.resident.job, this.resident.age, this.traits, this.settlement.culture(), this.seed, day);
			this.scheduleDay = day;
		}
		// Places are re-resolved now and then, and soon after spawning if a plot's POIs were not known yet.
		long gameTime = level.getGameTime();
		if (this.places == null || gameTime - this.placesAt > (this.places.complete ? 6000 : 100)) {
			this.places = ResidentPlaces.resolve(level, this.settlement, this.resident, this.town.places);
			this.placesAt = gameTime;
		}
		int minute = NpcTime.minuteOfDay(level);
		Schedule.Block b = this.schedule.at(minute);
		Activity want = b.activity();

		this.threat = findThreat(level);
		boolean guard = this.resident.job == JobType.GUARD && this.resident.age == AgeStage.ADULT;
		if (this.threat != null && !guard) {
			want = Activity.FLEE;
			// One monster is something to run from; several, or raiders, and the whole town takes cover.
			if (this.threatCount >= 2 || level.getRaidAt(this.folk.blockPosition()) != null) {
				this.town.raiseAlarm(gameTime, this.threat.blockPosition(), 900);
			}
		} else if (this.town.alarmed(gameTime) && !guard) {
			want = Activity.ALARM;
		} else if (level.getRaidAt(this.folk.blockPosition()) != null) {
			this.town.raiseAlarm(gameTime, this.folk.blockPosition(), 1200);
			want = guard ? Activity.WORK : Activity.ALARM;
		} else if (want.outdoors() && level.isRainingAt(this.folk.blockPosition().above()) && !hardy(level)) {
			want = Activity.SHELTER;
		}
		if (this.behaviours.active()) {
			if (routinePriority(want) >= this.behaviours.priority()) {
				// a reaction that outranks the behaviour (a monster, the alarm): the behaviour yields
				this.behaviours.stop(this.handle, this.owner);
			} else {
				// the behaviour keeps the folk; the routine only notes where the day has got to
				if (this.activity == Activity.WORK && want != Activity.WORK && this.workTicks > 0) {
					this.data.addLabour(this.workTicks);
					this.workTicks = 0;
				}
				this.activity = want;
				this.block = b;
				return;
			}
		}
		if (want == Activity.SLEEP && this.activity == Activity.SLEEP && want == b.activity()) {
			// the night goes on across a new block or a new day's schedule (at midnight): nobody gets up for it
			this.block = b;
		} else if (want != this.activity || (want == b.activity() && !b.equals(this.block))) {
			switchTo(level, want, b);
		} else if (guard && this.threat != null && this.activity == Activity.WORK && (this.task == null || !this.task.urgent())) {
			// A guard on watch drops everything for a monster.
			replan(level);
		}
	}

	/** Guards keep their watch in the rain (and miners are under ground); outdoor trades work through a shower, not a storm. */
	private boolean hardy(ServerLevel level) {
		JobType job = this.resident.job;
		if (this.activity != Activity.WORK) {
			return false;
		}
		return switch (job) {
			case GUARD, JAILER, MINER -> true;
			case FARMER, FISHER, BUILDER, FORESTER, QUARRIER, HERDER, HAULER, CLAY_DIGGER, SALTER, CHARCOAL_BURNER, STABLEMASTER -> !level.isThundering();
			default -> false;
		};
	}

	private void switchTo(ServerLevel level, Activity activity, Schedule.Block block) {
		if (this.task != null) {
			this.task.stop(this, level);
			this.task = null;
		}
		if (this.activity == Activity.WORK && activity != Activity.WORK && this.workTicks > 0) {
			// The labour done embodied, for the sim to credit (see docs/requests/npc.md).
			this.data.addLabour(this.workTicks);
			this.workTicks = 0;
		}
		endChat();
		this.activity = activity;
		this.block = block;
		if (this.folk.isSleeping() && activity != Activity.SLEEP) {
			this.folk.stopSleeping();
		}
		this.folk.setFolkPose(FolkPose.NONE);
		hold(ItemStack.EMPTY, ItemStack.EMPTY);
	}

	private @Nullable LivingEntity findThreat(ServerLevel level) {
		double range = this.traits.contains(Trait.CAUTIOUS) ? 14 : this.traits.contains(Trait.BRAVE) ? 7 : 10;
		if (this.resident.job == JobType.GUARD) {
			range = 18;
		}
		AABB box = this.folk.getBoundingBox().inflate(range, 5, range);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		this.threatCount = 0;
		for (Monster m : level.getEntitiesOfClass(Monster.class, box, m -> m.isAlive() && !(m instanceof Enderman))) {
			double d = m.distanceToSqr(this.folk);
			if (this.folk.getSensing().hasLineOfSight(m)) {
				this.threatCount++;
				if (d < bestD) {
					best = m;
					bestD = d;
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- behaviours from other modules

	/** The routine's own priority now, against which other modules' behaviours are weighed (docs/FOLK.md §13). */
	public int routinePriority() {
		if (this.task != null && this.task.urgent()) {
			return 299;
		}
		if (this.engagedWith != null) {
			// in conversation with a player: only reactions and duty interrupt
			return 199;
		}
		return routinePriority(this.activity);
	}

	/** The priority of each part of the day; reactions the brain handles itself rank with other modules' reactions. */
	public static int routinePriority(Activity activity) {
		return switch (activity) {
			case FLEE -> 250;
			case ALARM -> 240;
			case SLEEP -> 90;
			case SHELTER -> 60;
			case WORK -> 50;
			case EAT -> 40;
			case WAKE, HOME -> 30;
			case LEISURE -> 20;
		};
	}

	private void onTakeOver() {
		ServerLevel level = this.lastLevel;
		if (this.task != null && level != null) {
			this.task.stop(this, level);
		}
		this.task = null;
		if (this.activity == Activity.WORK && this.workTicks > 0) {
			this.data.addLabour(this.workTicks);
			this.workTicks = 0;
		}
		endChat();
		if (this.engagedWith != null) {
			disengage();
		}
		this.folk.setFolkPose(FolkPose.NONE);
		this.poseUntil = 0;
		hold(ItemStack.EMPTY, ItemStack.EMPTY);
		resetWalk();
	}

	private void onHandBack() {
		this.folk.getNavigation().stop();
		if (this.folk.isSleeping() && this.activity != Activity.SLEEP) {
			this.folk.stopSleeping();
		}
		this.folk.setFolkPose(FolkPose.NONE);
		hold(ItemStack.EMPTY, ItemStack.EMPTY);
		resetWalk();
		this.task = null;
	}

	/** This folk as other modules drive it. */
	public FolkHandleImpl handle() {
		return this.handle;
	}

	/** Other modules' behaviour now driving this folk, or null while the routine has them. */
	public NpcService.@Nullable FolkBehaviour behaviour() {
		return this.behaviours.current();
	}

	// ---------------------------------------------------------------- people

	/** Game tick (the folk's own count) until which this folk stays up though the routine says sleep: a fire, an alarm. */
	private int wokenUntil;

	/** Wakes the folk (a fire, an alarm bell, a caught thief): they get up and stay up for {@code ticks}. */
	public void wake(int ticks) {
		this.wokenUntil = Math.max(this.wokenUntil, this.folk.tickCount + ticks);
		if (this.folk.isSleeping()) {
			this.folk.stopSleeping();
		}
		this.folk.setFolkPose(FolkPose.NONE);
	}

	/** Whether the folk was woken and should not lie down again yet. */
	public boolean woken() {
		return this.folk.tickCount < this.wokenUntil;
	}

	/** A player started talking to them: stop, face the player, listen. */
	public void engage(ServerPlayer player) {
		this.engagedWith = player;
		this.engagedUntil = this.folk.tickCount + 20 * 60;
		this.folk.getNavigation().stop();
		if (this.folk.isSleeping()) {
			this.folk.stopSleeping();
		}
		this.folk.setFolkPose(FolkPose.TALK);
		this.poseUntil = this.folk.tickCount + 40;
	}

	/** The player is still talking (picked an option): the folk keeps listening for another minute. */
	public void keepEngaged(ServerPlayer player) {
		if (this.engagedWith == player) {
			this.engagedUntil = this.folk.tickCount + 20 * 60;
		}
	}

	public void disengage() {
		this.engagedWith = null;
		this.folk.setFolkPose(FolkPose.NONE);
	}

	public boolean engagedWith(ServerPlayer player) {
		return this.engagedWith == player;
	}

	private void tickEngaged(ServerLevel level, int now) {
		ServerPlayer p = this.engagedWith;
		if (p == null || !p.isAlive() || p.level() != level || p.distanceToSqr(this.folk) > dev.voidmc.hearth.npc.talk.Conversations.RANGE * dev.voidmc.hearth.npc.talk.Conversations.RANGE
			|| now > this.engagedUntil) {
			disengage();
			return;
		}
		this.folk.getLookControl().setLookAt(p, 30.0F, 30.0F);
		this.folk.getNavigation().stop();
		if (this.poseUntil != 0 && now >= this.poseUntil) {
			this.poseUntil = 0;
			this.folk.setFolkPose(FolkPose.NONE);
		}
	}

	/** Says something (the conversation calls this when a line is shown): gesture for a moment. */
	public void speak() {
		this.folk.setFolkPose(FolkPose.TALK);
		this.poseUntil = this.folk.tickCount + 50;
	}

	private void social(ServerLevel level, int now) {
		if (this.activity == Activity.SLEEP || this.activity == Activity.FLEE || this.folk.isSleeping()) {
			return;
		}
		// Look at a player passing close by; greet them if they are known here.
		if (this.nearestPlayer < 7 && (now + this.folk.getId()) % 10 == 0) {
			Player p = level.getNearestPlayer(this.folk, 7.0);
			if (p instanceof ServerPlayer sp && !sp.isSpectator() && this.folk.getSensing().hasLineOfSight(sp)) {
				if (this.folk.getNavigation().isDone()) {
					this.folk.getLookControl().setLookAt(sp, 20.0F, 20.0F);
				}
				Integer last = this.greeted.get(sp.getUUID());
				if (last == null || now - last > GREET_MEMORY) {
					this.greeted.put(sp.getUUID(), now);
					if (this.folk.folkPose() == FolkPose.NONE && this.folk.getNavigation().isDone()) {
						pose(FolkPose.WAVE, 30);
					}
					if (NpcModule.config().talk().greetings()) {
						Greetings.greetInPassing(level, this, sp);
					}
				}
			}
		}
		// Chat with a neighbour when both are at leisure.
		if ((this.activity == Activity.LEISURE || this.activity == Activity.HOME || this.activity == Activity.EAT) && this.chatPartner == null
			&& (now + this.folk.getId()) % 60 == 0 && this.folk.getNavigation().isDone() && this.random.nextInt(3) == 0) {
			List<Folk> near = level.getEntitiesOfClass(Folk.class, this.folk.getBoundingBox().inflate(4.5, 2, 4.5),
				f -> f != this.folk && f.brain() != null && f.brain().canChat());
			if (!near.isEmpty()) {
				Folk other = near.get(this.random.nextInt(near.size()));
				int length = 120 + this.random.nextInt(200);
				startChat(other, length);
				other.brain().startChat(this.folk, length);
			}
		}
	}

	boolean canChat() {
		return this.chatPartner == null && this.engagedWith == null && !this.folk.isSleeping()
			&& (this.activity == Activity.LEISURE || this.activity == Activity.HOME || this.activity == Activity.EAT)
			&& this.folk.getNavigation().isDone();
	}

	private void startChat(Folk other, int ticks) {
		this.chatPartner = other;
		this.chatUntil = this.folk.tickCount + ticks;
		this.folk.getNavigation().stop();
	}

	private void tickChat(ServerLevel level, int now) {
		Folk other = this.chatPartner;
		if (other == null || !other.isAlive() || other.isRemoved() || other.distanceToSqr(this.folk) > 49 || now > this.chatUntil) {
			endChat();
			return;
		}
		this.folk.getLookControl().setLookAt(other, 30.0F, 30.0F);
		// Take turns: one talks while the other listens.
		boolean mine = ((now / 50) + (this.folk.getId() < other.getId() ? 0 : 1)) % 2 == 0;
		this.folk.setFolkPose(mine ? FolkPose.TALK : FolkPose.NONE);
	}

	private void endChat() {
		if (this.chatPartner != null) {
			Folk other = this.chatPartner;
			this.chatPartner = null;
			this.folk.setFolkPose(FolkPose.NONE);
			UUID otherId = other.residentId();
			if (otherId != null && this.random.nextInt(4) == 0) {
				this.data.addFriend(otherId);
			}
		}
	}

	// ---------------------------------------------------------------- helpers for tasks

	/**
	 * Walks toward {@code target}; true once within {@code within} blocks (horizontally, and about level). Paths are
	 * asked for at most once per repath cooldown, and a folk that makes no progress for a while gives up
	 * ({@link #walkFailed()}) — or, if no player is near enough to see it or can see it, is moved there.
	 *
	 * <p>Long walks (across a town to bed) get a path budget to match the distance, up to {@link #MAX_PATH} blocks: the
	 * default of 48 gave partial paths that end wherever the search ran out, often on a terrace or a stair landing
	 * nearer the goal as the crow flies, where the next search ends again. A folk who arrives at the end of a partial
	 * path no nearer than the last time takes a detour (a spot on open ground part way toward the goal) before trying
	 * again.
	 */
	public boolean walk(ServerLevel level, BlockPos target, double within, double speed) {
		Vec3 pos = this.folk.position();
		double dx = target.getX() + 0.5 - pos.x;
		double dz = target.getZ() + 0.5 - pos.z;
		double dy = target.getY() - pos.y;
		if (dx * dx + dz * dz <= within * within && Math.abs(dy) < 2.0) {
			if (this.walkTarget != null) {
				this.folk.getNavigation().stop();
				this.walkTarget = null;
				this.detour = null;
			}
			return true;
		}
		int now = this.folk.tickCount;
		if (!target.equals(this.walkTarget)) {
			this.walkTarget = target;
			this.walkFailed = false;
			this.stuckFor = 0;
			this.lastProgressPos = pos;
			this.nextRepath = 0;
			this.detour = null;
			this.approaches = null;
			this.partialBest = Double.MAX_VALUE;
		}
		double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);
		if (this.approaches != null && this.detour == null) {
			// standing on one of the spots the path was asked for (the far side of a bed's foot is two blocks off)
			for (BlockPos a : this.approaches) {
				double ax = a.getX() + 0.5 - pos.x;
				double az = a.getZ() + 0.5 - pos.z;
				if (ax * ax + az * az < 0.6 * 0.6 && Math.abs(a.getY() - pos.y) < 1.0) {
					this.folk.getNavigation().stop();
					this.walkTarget = null;
					return true;
				}
			}
		}
		if (this.detour != null) {
			Vec3 d = Vec3.atBottomCenterOf(this.detour);
			if (pos.distanceToSqr(d) < 2.5 * 2.5 || now > this.detourUntil) {
				// at the detour (or it took too long): on to the goal again
				this.detour = null;
				this.nextRepath = 0;
				this.folk.getNavigation().stop();
			}
		}
		if (now >= this.nextRepath && (this.folk.getNavigation().isDone() || this.folk.getNavigation().getPath() == null)) {
			net.minecraft.world.level.pathfinder.Path last = this.folk.getNavigation().getPath();
			if (this.detour == null && last != null && !last.canReach() && last.isDone()) {
				// the end of a partial path: if it brought them no nearer, go round
				if (dist > this.partialBest - 2.0) {
					this.detour = detourToward(level, target);
					this.detourUntil = now + 20 * 15;
				}
				this.partialBest = Math.min(this.partialBest, dist);
			}
			BlockPos goal = this.detour != null ? this.detour : target;
			double far = Math.sqrt(goal.distSqr(this.folk.blockPosition()));
			// another storey wants a wider search: the way up is often at the far end of the house
			double budget = far * 1.4 + 16.0 + (Math.abs(goal.getY() - this.folk.getBlockY()) >= 3 ? 48.0 : 0.0);
			this.folk.getNavigation().setRequiredPathLength((float) Mth.clamp(budget, 16.0, MAX_PATH));
			net.minecraft.world.level.pathfinder.Path way;
			if (goal == target) {
				if (this.approaches == null) {
					this.approaches = FolkSpots.approaches(level, target);
				}
				way = this.approaches.isEmpty() ? this.folk.getNavigation().createPath(target, Math.max(0, (int) within - 1))
					: this.folk.getNavigation().createPath(new java.util.HashSet<>(this.approaches), 0);
			} else {
				way = this.folk.getNavigation().createPath(goal, 1);
			}
			boolean ok = this.folk.getNavigation().moveTo(way, speed);
			this.nextRepath = now + config().repathCooldown() + this.random.nextInt(10);
			if (!ok) {
				this.stuckFor += config().repathCooldown();
			}
			// No path at all, twice running, standing on the ground: they are somewhere no path starts from (a child up on
			// the well's rim by way of its cauldron, a fence top). A hop down to proper ground, seen or not.
			this.noPath = way == null && this.folk.onGround() ? this.noPath + 1 : 0;
			if (this.noPath >= 2) {
				this.noPath = 0;
				BlockPos down = FolkSpots.groundNear(level, this.folk.blockPosition(), 3);
				if (down != null) {
					this.folk.getNavigation().stop();
					this.folk.teleportTo(down.getX() + 0.5, down.getY(), down.getZ() + 0.5);
				}
			}
		}
		if (this.lastProgressPos != null && pos.distanceToSqr(this.lastProgressPos) > 1.0) {
			this.lastProgressPos = pos;
			this.stuckFor = 0;
		} else {
			this.stuckFor += this.thinkInterval;
		}
		if (this.stuckFor > 200) {
			if (unseen(level, target)) {
				// Nobody to see the shortcut: a folk out of sight is where it would have walked to.
				BlockPos spot = FolkSpots.standableNear(level, target, 3);
				if (spot != null) {
					this.folk.getNavigation().stop();
					this.folk.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					this.stuckFor = 0;
					this.detour = null;
					return Math.abs(spot.getY() - target.getY()) < 2 && spot.distManhattan(target) <= within + 3;
				}
			}
			this.walkFailed = true;
		}
		return false;
	}

	/** Blocks of path a folk may search for in one go (the vanilla follow range is 48). */
	private static final float MAX_PATH = 128.0F;
	/** A spot to make for first, when the last partial path toward the goal brought the folk no nearer. */
	private @Nullable BlockPos detour;
	private int detourUntil;
	/** Path searches in a row that found no path at all (not even a partial one). */
	private int noPath;
	/** Where one stands to use the current goal ({@link FolkSpots#approaches}), found once per goal. */
	private java.util.@Nullable List<BlockPos> approaches;
	/** The nearest the end of a partial path has brought the folk to the current goal. */
	private double partialBest = Double.MAX_VALUE;

	/**
	 * Somewhere to go round by: open ground 12 to 20 blocks on, toward the goal or off to either side of the line, where
	 * the folk has not just been. Null if none is found in loaded chunks.
	 */
	private @Nullable BlockPos detourToward(ServerLevel level, BlockPos target) {
		Vec3 from = this.folk.position();
		double ang = Math.atan2(target.getZ() + 0.5 - from.z, target.getX() + 0.5 - from.x);
		for (int tries = 0; tries < 6; tries++) {
			double a = ang + (this.random.nextDouble() - 0.5) * Math.PI * (tries < 3 ? 0.7 : 1.4);
			double r = 12 + this.random.nextInt(9);
			int x = Mth.floor(from.x + Math.cos(a) * r);
			int z = Mth.floor(from.z + Math.sin(a) * r);
			if (!level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			// the ground there, not a roof: the highest standable spot at or below the folk's own height plus a storey
			BlockPos spot = FolkSpots.standableNear(level, new BlockPos(x, Math.min(this.folk.getBlockY() + 4,
				level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)), z), 3);
			if (spot != null && level.canSeeSky(spot)) {
				return spot;
			}
		}
		return null;
	}

	/**
	 * Whether a shortcut would go unseen: no player (spectators included) within 32 blocks of the folk or of where it
	 * is going, or none of them has a line of sight to either.
	 */
	public boolean unseen(ServerLevel level, BlockPos target) {
		if (this.nearestPlayer > 32 && level.getNearestPlayer(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 32, false) == null) {
			return true;
		}
		Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 1.2, target.getZ() + 0.5);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this.folk) > 48 * 48 && p.distanceToSqr(to) > 48 * 48) {
				continue;
			}
			if (p.hasLineOfSight(this.folk)) {
				return false;
			}
			Vec3 eye = p.getEyePosition();
			if (eye.distanceToSqr(to) < 48 * 48 && level.clip(new net.minecraft.world.level.ClipContext(eye, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Asks to be folded back into the record once this think is done: a folk with a long way home at bedtime and nobody
	 * to see them go, whose record is then in bed ({@link RoutinePlacement}). Not while escorted.
	 */
	public void slipAway() {
		if (!dev.voidmc.hearth.npc.service.Escorts.pinned(this.resident.id)) {
			this.slipAway = true;
		}
	}

	private boolean slipAway;

	public boolean walkFailed() {
		return this.walkFailed;
	}

	public void resetWalk() {
		this.walkTarget = null;
		this.detour = null;
		this.walkFailed = false;
		this.stuckFor = 0;
	}

	/** Puts things in their hands (only if different, to spare equipment packets). */
	public void hold(ItemStack main, ItemStack off) {
		if (!ItemStack.isSameItem(this.folk.getItemBySlot(EquipmentSlot.MAINHAND), main) || main.isEmpty() != this.folk.getMainHandItem().isEmpty()) {
			this.folk.setItemSlot(EquipmentSlot.MAINHAND, main);
		}
		if (!ItemStack.isSameItem(this.folk.getItemBySlot(EquipmentSlot.OFFHAND), off) || off.isEmpty() != this.folk.getOffhandItem().isEmpty()) {
			this.folk.setItemSlot(EquipmentSlot.OFFHAND, off);
		}
	}

	/** Holds a pose for a while, then back to standing. */
	public void pose(FolkPose pose, int ticks) {
		this.folk.setFolkPose(pose);
		this.poseUntil = ticks <= 0 ? 0 : this.folk.tickCount + ticks;
	}

	public void faceTowards(BlockPos pos) {
		this.folk.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
		double dx = pos.getX() + 0.5 - this.folk.getX();
		double dz = pos.getZ() + 0.5 - this.folk.getZ();
		if (dx * dx + dz * dz > 0.01) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			this.folk.setYRot(yaw);
			this.folk.setYBodyRot(yaw);
			this.folk.setYHeadRot(yaw);
		}
	}

	public void swing() {
		this.folk.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	public ResidentPlaces places(ServerLevel level) {
		if (this.places == null) {
			this.places = ResidentPlaces.resolve(level, this.settlement, this.resident, this.town.places);
			this.placesAt = level.getGameTime();
		}
		return this.places;
	}

	public Activity activity() {
		return this.activity;
	}

	public Schedule.@Nullable Block block() {
		return this.block;
	}

	public @Nullable Schedule schedule() {
		return this.schedule;
	}

	public @Nullable LivingEntity threat() {
		return this.threat;
	}

	public WorkBehaviour work() {
		if (this.workJob != this.resident.job) {
			// the sim reassigned the trade (daily jobs, a player's hire): the old work's claims are let go
			ServerLevel level = this.folk.level() instanceof ServerLevel l ? l : null;
			if (level != null) {
				this.work.onFold(this, level);
			}
			this.work = WorkBehaviour.of(this.resident.job, this.resident.age);
			this.workJob = this.resident.job;
		}
		return this.work;
	}

	public double nearestPlayer() {
		return this.nearestPlayer;
	}

	public long seed() {
		return this.seed;
	}

	public @Nullable Task task() {
		return this.task;
	}

	/** Drops the current task so the next think plans afresh (a builder's plot finished, a door locked). */
	public void replan(ServerLevel level) {
		if (this.task != null) {
			this.task.stop(this, level);
			this.task = null;
		}
	}

	// ---------------------------------------------------------------- events

	public void onHurt(ServerLevel level, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			// Folk remember who hurt them; the town does too. With the law module, assault goes through witnesses and the
			// courts (it reports the deed); without it, the standing module hears of it directly, or the raw number moves.
			this.data.addMood(-15);
			if (dev.voidmc.hearth.api.HearthServices.law() == null) {
				dev.voidmc.hearth.api.StandingService standing = dev.voidmc.hearth.api.HearthServices.standing();
				if (standing != null) {
					standing.deed(new dev.voidmc.hearth.api.StandingService.Deed(player.getUUID(), this.settlement.id(), "npc:hurt", -3, this.resident.id,
						this.folk.blockPosition(), true));
				} else {
					this.settlement.addReputation(player.getUUID(), -3);
				}
			}
			this.settlement.markDirty();
			if (this.engagedWith == player) {
				disengage();
			}
		}
		if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Folk)) {
			if (this.resident.job == JobType.GUARD && !(attacker instanceof Player)) {
				this.folk.setTarget(attacker);
			}
			this.threat = attacker;
			if (this.resident.job != JobType.GUARD && this.activity != Activity.FLEE) {
				switchTo(level, Activity.FLEE, this.block != null ? this.block : new Schedule.Block(0, Activity.FLEE, null, null));
			}
		}
	}

	/** The entity is being folded back into its record: end the task (taking down scaffolding), credit labour. */
	public void onFold(ServerLevel level) {
		this.lastLevel = level;
		this.behaviours.stop(this.handle, this.owner);
		if (this.task != null) {
			this.task.stop(this, level);
			this.task = null;
		}
		this.work.onFold(this, level);
		if (this.workTicks > 0) {
			this.data.addLabour(this.workTicks);
			this.workTicks = 0;
		}
		endChat();
	}

	public String describe() {
		Schedule.Block b = this.block;
		NpcService.FolkBehaviour running = this.behaviours.current();
		if (running != null) {
			return "behaviour " + running.id() + " (priority " + running.priority() + ") over " + this.activity;
		}
		return this.activity + (b != null && b.leisure() != null ? " (" + b.leisure() + ")" : "")
			+ (this.task != null ? " / " + this.task.describe() : "") + (this.threat != null ? " / threat " + this.threat.getName().getString() : "");
	}
}
