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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
		/** A scatter's ways out, best first, and which is being tried; how far from the blade it stood at the start. */
		List<BlockPos> ways = List.of();
		int way;
		double startGap;
		int stalls;
		/** Path searches made for a scatter so far. */
		int searches;
		/** What a scatter runs from. */
		@Nullable BlockPos src;
		@Nullable Vec3 lastPos;

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
				BlockPos src = from(job, p);
				job.ways = ways(folk, src, SCATTER_DIST + Math.floorMod(seed(folk), 3));
				job.way = 0;
				job.goal = null;
				job.src = src;
				job.startGap = Math.sqrt(folk.entity().position().distanceToSqr(Vec3.atBottomCenterOf(src)));
				job.lastPos = null;
				job.stalls = 0;
				job.searches = 0;
				// the first steps at once (the order has waited for the director and the poll already)
				headOff(folk, job);
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

	/** How far a scatter's ways out reach from the folk (7–9 blocks), and how much further from the blade a way must end. */
	static final int SCATTER_DIST = 7;
	static final double SCATTER_GAIN = 5;
	/** Path searches a scatter may make in all (ways out, then a vanilla "anywhere away"). */
	static final int SCATTER_SEARCHES = 5;

	/**
	 * Steps back from the weapon, well clear (the way out ends 5+ blocks further from it than the folk stood); a child runs
	 * to its mother if she is near. A way the path will not take far enough, or on which the folk makes no headway, gives
	 * way to the next at once; when none will do, anywhere away the vanilla way.
	 */
	private boolean scatter(FolkHandle folk, Job job) {
		ServerPlayer p = player(folk, job.order);
		if (job.goal == null || job.ticks > 140) {
			// keep the distance a while, watching the blade
			if (p != null) {
				folk.lookAt(p);
			}
			return job.ticks < 240;
		}
		if (job.repath <= 0) {
			// no headway over two looks (a wall, a fence, a crowd in the lane): the next way out
			Vec3 here = folk.entity().position();
			boolean stuck = job.lastPos != null && here.distanceToSqr(job.lastPos) < 0.36;
			job.stalls = stuck ? job.stalls + 1 : 0;
			job.lastPos = here;
			if (job.stalls >= 2 || folk.entity().getNavigation().isDone() && !clear(folk, job)) {
				job.stalls = 0;
				job.way++;
				headOff(folk, job);
			} else {
				folk.moveTo(job.goal, speed(folk));
			}
			job.repath = 8;
		}
		if (job.goal != null && folk.arrived() && clear(folk, job)) {
			folk.stop();
			job.goal = null;
		}
		return true;
	}

	private static double speed(FolkHandle folk) {
		return folk.resident().age == dev.voidmc.hearth.api.AgeStage.CHILD ? 1.4 : 1.3;
	}

	/** Whether the folk stands well clear of the blade now (4.5+ blocks further than at the start). */
	private static boolean clear(FolkHandle folk, Job job) {
		return job.src == null || Math.sqrt(folk.entity().position().distanceToSqr(Vec3.atBottomCenterOf(job.src))) >= job.startGap + 4.5;
	}

	/**
	 * Sets the folk off on the first way out from {@code job.way} on whose path it ends well away from the blade (a path
	 * that stops short of a fence still does if it gets far enough); with the ways spent, a vanilla spot away from the
	 * blade. Each try is one path search, {@link #SCATTER_SEARCHES} in all.
	 */
	private static void headOff(FolkHandle folk, Job job) {
		BlockPos src = job.src;
		if (src == null) {
			return;
		}
		double need = job.startGap + SCATTER_GAIN - 1;
		while (job.searches < SCATTER_SEARCHES) {
			BlockPos goal;
			if (job.way < job.ways.size()) {
				goal = job.ways.get(job.way);
			} else {
				Vec3 v = folk.entity() instanceof net.minecraft.world.entity.PathfinderMob mob
					? net.minecraft.world.entity.ai.util.LandRandomPos.getPosAway(mob, SCATTER_DIST + 2, 4, Vec3.atBottomCenterOf(src)) : null;
				if (v == null) {
					job.searches++;
					continue;
				}
				goal = BlockPos.containing(v);
			}
			job.searches++;
			if (folk.moveTo(goal, speed(folk)) && reaches(folk, src, need)) {
				job.goal = goal;
				return;
			}
			job.way++;
		}
		// nothing better: straight away from the blade, and the path does what it can
		job.goal = away(folk, src, SCATTER_DIST, true);
		folk.moveTo(job.goal, speed(folk));
	}

	/** Whether the folk's current path ends at least {@code need} blocks from {@code src}. */
	private static boolean reaches(FolkHandle folk, BlockPos src, double need) {
		net.minecraft.world.level.pathfinder.Path path = folk.entity().getNavigation().getPath();
		if (path == null || path.getNodeCount() == 0) {
			return false;
		}
		net.minecraft.world.level.pathfinder.Node end = path.getEndNode();
		if (end == null) {
			return false;
		}
		double dx = end.x + 0.5 - (src.getX() + 0.5);
		double dz = end.z + 0.5 - (src.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz) >= need;
	}

	/**
	 * Ways out from a blade, best first: straight away, then fanning to either side (40°, 80°, 120°), each a standable
	 * spot at least four blocks further from it than the folk stands, with open ground along the way (no wall between);
	 * a child's first way is to its mother when she is further off than the child. Block reads only, a few dozen.
	 */
	static List<BlockPos> ways(FolkHandle folk, BlockPos from, int dist) {
		List<BlockPos> out = new ArrayList<>(4);
		Vec3 me = folk.entity().position();
		net.minecraft.world.level.Level level = folk.entity().level();
		double gap = Math.sqrt(me.distanceToSqr(Vec3.atBottomCenterOf(from)));
		if (folk.resident().age == dev.voidmc.hearth.api.AgeStage.CHILD) {
			BlockPos mother = mother(folk);
			if (mother != null && Math.sqrt(mother.distSqr(from)) >= gap + 3) {
				out.add(mother);
			}
		}
		double dx = me.x - (from.getX() + 0.5);
		double dz = me.z - (from.getZ() + 0.5);
		double len = Math.sqrt(dx * dx + dz * dz);
		double base = len < 0.5 ? Math.floorMod(seed(folk), 628) / 100.0 : Math.atan2(dz, dx);
		double side = (seed(folk) & 1) == 0 ? 1 : -1;
		List<BlockPos> closed = new ArrayList<>(2);
		for (int k = 0; k < 7 && out.size() < 4; k++) {
			double turn = Math.toRadians(40 * ((k + 1) / 2)) * (k % 2 == 1 ? side : -side);
			double a = base + turn;
			BlockPos want = BlockPos.containing(me.x + Math.cos(a) * dist, me.y, me.z + Math.sin(a) * dist);
			BlockPos spot = FolkSpots.standableNear(level, want, 2);
			if (spot == null || Math.sqrt(spot.distSqr(from)) < gap + SCATTER_GAIN) {
				continue;
			}
			if (open(level, folk.entity().blockPosition(), spot)) {
				out.add(spot);
			} else if (closed.size() < 2) {
				closed.add(spot); // not open along a straight line, but the path may find a way round
			}
		}
		out.addAll(closed);
		return out;
	}

	/** Open ground from one spot to another: every block on the line has room to stand a block up or down. */
	private static boolean open(net.minecraft.world.level.Level level, BlockPos a, BlockPos b) {
		int steps = (int) Math.ceil(Math.sqrt(a.distSqr(b)));
		int y = a.getY();
		for (int i = 1; i < steps; i++) {
			double t = i / (double) steps;
			int x = (int) Math.floor(a.getX() + 0.5 + (b.getX() - a.getX()) * t);
			int z = (int) Math.floor(a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t);
			BlockPos p = new BlockPos(x, y, z);
			if (!level.isLoaded(p)) {
				return false;
			}
			if (FolkSpots.standable(level, p)) {
				continue;
			}
			if (FolkSpots.standable(level, p.above())) {
				y++;
			} else if (FolkSpots.standable(level, p.below())) {
				y--;
			} else {
				return false;
			}
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
