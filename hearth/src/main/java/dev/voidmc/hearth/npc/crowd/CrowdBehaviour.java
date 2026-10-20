package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One crowd reaction as a folk behaviour (docs/LIVING_WORLD_2.md §0.3, §5.3): it wants a folk when the
 * {@link CrowdDirector} has left an {@link Order} of its kind for them, and acts it out — stepping back from a blade,
 * gathering in a ring, waving at a hero, running indoors from an outlaw, crossing the lane, or a word (a guard walking over
 * first). Short, cheap, and over when the order is.
 */
public final class CrowdBehaviour implements FolkBehaviour {
	private final Reaction reaction;
	private final CrowdDirector director;
	private final Map<UUID, Job> jobs = new HashMap<>();

	private static final class Job {
		Order order;
		int ticks;
		int repath;
		int poseLeft;
		int nextBark;
		boolean arrived;
		@Nullable BlockPos goal;
		@Nullable BlockPos watched;

		Job(Order order) {
			this.order = order;
		}
	}

	public CrowdBehaviour(Reaction reaction, CrowdDirector director) {
		this.reaction = reaction;
		this.director = director;
	}

	public Reaction reaction() {
		return this.reaction;
	}

	@Override
	public Identifier id() {
		return this.reaction.id();
	}

	@Override
	public int priority() {
		return this.reaction.priority();
	}

	@Override
	public boolean wants(FolkHandle folk) {
		if (!Crowds.config().crowds().enabled() || !folk.awake()) {
			return false;
		}
		Order o = this.director.order(folk.resident().id, this.reaction, folk.gameTime());
		return o != null && !o.started;
	}

	@Override
	public void start(FolkHandle folk) {
		Order o = this.director.order(folk.resident().id, this.reaction, folk.gameTime());
		if (o == null) {
			return;
		}
		o.started = true;
		Job job = new Job(o);
		this.jobs.put(folk.resident().id, job);
		begin(folk, job);
	}

	@Override
	public boolean tick(FolkHandle folk) {
		Job job = this.jobs.get(folk.resident().id);
		if (job == null) {
			return false;
		}
		Order now = this.director.order(folk.resident().id, folk.gameTime());
		if (now == null) {
			return false;
		}
		if (now != job.order) {
			if (now.reaction != this.reaction) {
				return false;
			}
			// the director moved this folk on to another player or stimulus of the same kind
			now.started = true;
			job.order = now;
			job.ticks = 0;
			job.arrived = false;
			begin(folk, job);
		}
		job.ticks++;
		job.repath--;
		if (job.poseLeft > 0 && --job.poseLeft == 0) {
			pose(folk, FolkPose.NONE, 0);
		}
		boolean more = switch (this.reaction) {
			case SCATTER -> scatter(folk, job);
			case GAWK -> gawk(folk, job);
			case HAIL -> hail(folk, job);
			case SHUN -> shun(folk, job);
			case MUTTER -> mutter(folk, job);
			case REMARK -> remark(folk, job);
		};
		if (!more) {
			job.order.done = true;
		}
		return more;
	}

	@Override
	public void stop(FolkHandle folk) {
		Job job = this.jobs.remove(folk.resident().id);
		if (job != null) {
			job.order.done = true;
		}
		folk.stop();
		pose(folk, FolkPose.NONE, 0);
	}

	public void clear() {
		this.jobs.clear();
	}

	// ---------------------------------------------------------------- the reactions

	private void begin(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		switch (this.reaction) {
			case SCATTER -> {
				job.goal = away(folk, from(job, p), 5 + Math.floorMod(seed(folk), 3), true);
				if (job.order.rank == 0 || job.order.role == Role.MOTHER) {
					bark(folk, job, p);
				}
			}
			case SHUN -> {
				BlockPos home = home(folk);
				job.goal = home != null && home.closerThan(folk.entity().blockPosition(), 64) ? home : away(folk, from(job, p), 12, false);
				if (job.order.rank == 0) {
					bark(folk, job, p);
				}
			}
			case MUTTER -> {
				job.goal = aside(folk, from(job, p));
				if (job.order.rank == 0) {
					bark(folk, job, p);
				}
			}
			case GAWK -> {
				job.watched = from(job, p);
				job.goal = ringSpot(folk, job, job.watched);
				job.nextBark = 20 + Math.floorMod(seed(folk), 60);
			}
			case HAIL -> {
				folk.stop();
				job.nextBark = job.order.rank * 90;
				pose(folk, FolkPose.WAVE, 40);
				job.poseLeft = 40;
			}
			case REMARK -> {
				job.goal = null;
				if (!job.order.approach) {
					folk.stop();
				}
			}
		}
		job.repath = 0;
	}

	/** Steps back 4–8 blocks from the weapon; a child runs to its mother if she is near. */
	private boolean scatter(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		if (job.goal == null || job.ticks > 140) {
			if (p != null) {
				folk.lookAt(p);
			}
			return job.ticks < 180;
		}
		if (job.repath <= 0) {
			folk.moveTo(job.goal, folk.resident().age == dev.voidmc.hearth.api.AgeStage.CHILD ? 1.35 : 1.25);
			job.repath = 10;
		}
		if (folk.arrived()) {
			folk.stop();
			job.goal = null;
			job.ticks = Math.max(job.ticks, 140);
		}
		return true;
	}

	/** Walks to a place in the ring round what is watched, then stands and watches, cheering now and then. */
	private boolean gawk(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		BlockPos watched = from(job, p);
		if (job.watched == null || !watched.closerThan(job.watched, 3)) {
			job.watched = watched;
			job.goal = ringSpot(folk, job, watched);
			job.arrived = false;
		}
		if (!job.arrived && job.goal != null) {
			if (job.repath <= 0) {
				folk.moveTo(job.goal, 0.95);
				job.repath = 20;
			}
			if (folk.arrived() || job.ticks > 200) {
				folk.stop();
				job.arrived = true;
			}
		} else {
			if (p != null && job.order.player != null) {
				folk.lookAt(p);
			} else {
				folk.lookAt(watched);
			}
			if (--job.nextBark <= 0) {
				job.nextBark = 60 + Math.floorMod(seed(folk) + job.ticks, 80);
				if (!cautious(folk) && job.order.role != Role.ELDER && folk.entity().getRandom().nextInt(3) > 0) {
					pose(folk, FolkPose.CHEER, 24);
					job.poseLeft = 24;
				}
				bark(folk, job, p);
			}
		}
		return true;
	}

	/** Turns and waves; the first few cheer the hero's name; children follow them about for half a minute. */
	private boolean hail(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		if (p == null) {
			return false;
		}
		folk.lookAt(p);
		if (job.order.rank < 3 && job.ticks == Math.max(1, job.nextBark)) {
			if (bark(folk, job, p)) {
				pose(folk, FolkPose.CHEER, 30);
				job.poseLeft = 30;
			}
		}
		boolean child = folk.resident().age == dev.voidmc.hearth.api.AgeStage.CHILD;
		if (child) {
			if (job.repath <= 0 && folk.entity().distanceTo(p) > 3.5) {
				folk.moveTo(p.blockPosition(), 1.15);
				job.repath = 20;
			} else if (folk.entity().distanceTo(p) <= 2.5) {
				folk.stop();
			}
			return job.ticks < 20 * 30;
		}
		return job.ticks < Math.max(100, job.order.rank * 90 + 50);
	}

	/** Runs home, shuts the door behind them, and stays in while the outlaw is about. */
	private boolean shun(FolkHandle folk, Job job) {
		if (job.goal == null) {
			return false;
		}
		if (!job.arrived) {
			if (job.repath <= 0) {
				folk.moveTo(job.goal, 1.35);
				job.repath = 15;
			}
			if (folk.arrived() || job.ticks > 20 * 25 && folk.entity().blockPosition().closerThan(job.goal, 4)) {
				folk.stop();
				job.arrived = true;
				CrowdStats.INDOORS.increment();
				shutDoor(folk);
			}
			return job.ticks < 20 * 40;
		}
		// inside: keep still until the order runs out (the director keeps it going while the outlaw is in town)
		return true;
	}

	/** Crosses the lane away from the distrusted player, muttering. */
	private boolean mutter(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		if (job.goal != null) {
			if (job.repath <= 0) {
				folk.moveTo(job.goal, 1.0);
				job.repath = 20;
			}
			if (folk.arrived() || job.ticks > 80) {
				folk.stop();
				job.goal = null;
			}
		} else if (p != null) {
			folk.lookAt(p);
		}
		return job.ticks < 110;
	}

	/** A word to the player; a guard walks over first when something calls for it. */
	private boolean remark(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		BlockPos target = job.order.origin != null ? job.order.origin : p == null ? null : p.blockPosition();
		if (target == null) {
			return false;
		}
		if (job.order.approach && !job.arrived) {
			double d = Math.sqrt(folk.entity().blockPosition().distSqr(target));
			if (d > 3.5 && job.ticks < 20 * 15) {
				if (job.repath <= 0) {
					folk.moveTo(target, 1.1);
					job.repath = 20;
				}
				return true;
			}
			folk.stop();
			job.arrived = true;
			job.ticks = 0;
		}
		if (p != null) {
			folk.lookAt(p);
		} else {
			folk.lookAt(target);
		}
		if (job.ticks == 12) {
			if (bark(folk, job, p)) {
				pose(folk, FolkPose.TALK, 40);
				job.poseLeft = 40;
			}
		}
		return job.ticks < 60;
	}

	// ---------------------------------------------------------------- helpers

	private boolean bark(FolkHandle folk, Job job, @Nullable ServerPlayer p) {
		Order o = job.order;
		// a spectacle's own lines, else any onlooker's, else the stimulus's
		String category = o.category;
		if (!Barks.lines(category).isEmpty()) {
			return Barks.say(folk, category, o.role, p, o.title);
		}
		if (category.startsWith("crowd_spectacle_")) {
			return Barks.say(folk, "crowd_spectacle", o.role, p, o.title);
		}
		return Barks.say(folk, o.stimulus.category(), o.role, p, o.title);
	}

	/** Where the thing reacted to is: the order's origin, else the player, else the folk itself. */
	private static BlockPos from(Job job, @Nullable ServerPlayer p) {
		if (job.order.origin != null) {
			return job.order.origin;
		}
		return p != null ? p.blockPosition() : BlockPos.ZERO;
	}

	/** A spot in the ring round {@code centre}: 4–6 blocks out (the cautious 8–9), spread by each folk's place. */
	private static BlockPos ringSpot(FolkHandle folk, Job job, BlockPos centre) {
		boolean cautious = cautious(folk) || folk.resident().age == dev.voidmc.hearth.api.AgeStage.ELDER;
		double r = cautious ? 8 + job.order.rank % 2 : 4 + job.order.rank % 3;
		// golden-angle spacing, turned per folk so two rings do not line up
		double a = job.order.rank * 2.399963 + Math.floorMod(seed(folk), 628) / 100.0;
		BlockPos want = BlockPos.containing(centre.getX() + 0.5 + Math.cos(a) * r, centre.getY(), centre.getZ() + 0.5 + Math.sin(a) * r);
		BlockPos spot = FolkSpots.standableNear(folk.entity().level(), want, 2);
		return spot != null ? spot : want;
	}

	/** A standable point {@code dist} blocks further from {@code from}, along the line from it through the folk. */
	private static BlockPos away(FolkHandle folk, BlockPos from, int dist, boolean toMother) {
		Vec3 me = folk.entity().position();
		if (toMother && folk.resident().age == dev.voidmc.hearth.api.AgeStage.CHILD) {
			BlockPos mother = mother(folk);
			if (mother != null) {
				return mother;
			}
		}
		double dx = me.x - (from.getX() + 0.5);
		double dz = me.z - (from.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.5) {
			double a = Math.floorMod(seed(folk), 628) / 100.0;
			dx = Math.cos(a);
			dz = Math.sin(a);
			len = 1;
		}
		BlockPos want = BlockPos.containing(me.x + dx / len * dist, me.y, me.z + dz / len * dist);
		BlockPos spot = FolkSpots.standableNear(folk.entity().level(), want, 2);
		return spot != null ? spot : want;
	}

	/** Three blocks to the side of the line to the player and two back: across the lane. */
	private static BlockPos aside(FolkHandle folk, BlockPos from) {
		Vec3 me = folk.entity().position();
		double dx = me.x - (from.getX() + 0.5);
		double dz = me.z - (from.getZ() + 0.5);
		double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
		double side = (seed(folk) & 1) == 0 ? 1 : -1;
		double x = me.x + dx / len * 2 - dz / len * 3 * side;
		double z = me.z + dz / len * 2 + dx / len * 3 * side;
		BlockPos want = BlockPos.containing(x, me.y, z);
		BlockPos spot = FolkSpots.standableNear(folk.entity().level(), want, 2);
		return spot != null ? spot : want;
	}

	/** Where the folk's mother stands, if she is about and near. */
	private static @Nullable BlockPos mother(FolkHandle folk) {
		dev.voidmc.hearth.api.NpcService npc = dev.voidmc.hearth.api.HearthServices.npc();
		if (npc == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return null;
		}
		int home = folk.resident().home;
		for (FolkHandle other : npc.folkNear(level, folk.entity().position(), 10)) {
			if (other.resident().female && other.resident().age == dev.voidmc.hearth.api.AgeStage.ADULT && other.resident().home == home && home >= 0) {
				return other.entity().blockPosition();
			}
		}
		return null;
	}

	/** Inside the folk's home: by the hearth, a seat or the bed, else the home's centre. */
	private static @Nullable BlockPos home(FolkHandle folk) {
		if (!(folk.entity() instanceof Folk f) || f.brain() == null || !(f.level() instanceof ServerLevel level)) {
			return null;
		}
		ResidentPlaces places = f.brain().places(level);
		if (!places.homeHearths.isEmpty()) {
			return places.homeHearths.get(0).pos();
		}
		if (!places.homeSeats.isEmpty()) {
			return places.homeSeats.get(0).pos();
		}
		if (places.bed != null) {
			return places.bed;
		}
		return folk.resident().home >= 0 ? places.home : null;
	}

	/** Shuts the home's door (both halves of a door are one block state change). */
	private static void shutDoor(FolkHandle folk) {
		if (!(folk.entity() instanceof Folk f) || f.brain() == null || !(f.level() instanceof ServerLevel level)) {
			return;
		}
		ResidentPlaces places = f.brain().places(level);
		Poi door = places.homeDoor;
		BlockPos near = door != null ? door.pos() : null;
		if (near == null) {
			return;
		}
		boolean shut = false;
		for (BlockPos q : BlockPos.betweenClosed(near.offset(-1, -1, -1), near.offset(1, 1, 1))) {
			BlockState s = level.getBlockState(q);
			if (s.getBlock() instanceof DoorBlock door2 && door2.isOpen(s)) {
				door2.setOpen(f, level, s, q.immutable(), false);
				shut = true;
			}
		}
		if (shut) {
			CrowdStats.DOORS_SHUT.increment();
		}
	}

	private static boolean cautious(FolkHandle folk) {
		return folk.entity() instanceof Folk f && f.brain() != null && f.brain().traits.contains(Trait.CAUTIOUS);
	}

	private static long seed(FolkHandle folk) {
		UUID id = folk.resident().id;
		return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
	}

	private static void pose(FolkHandle folk, FolkPose pose, int ticks) {
		if (folk.entity() instanceof Folk f) {
			FolkBrain brain = f.brain();
			if (brain != null) {
				brain.pose(pose, ticks);
			} else {
				f.setFolkPose(pose);
			}
		}
	}

	private static @Nullable ServerPlayer player(FolkHandle folk, Order o) {
		if (o.player == null) {
			return null;
		}
		MinecraftServer server = folk.entity().level().getServer();
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(o.player);
		return p != null && p.level() == folk.entity().level() ? p : null;
	}
}
