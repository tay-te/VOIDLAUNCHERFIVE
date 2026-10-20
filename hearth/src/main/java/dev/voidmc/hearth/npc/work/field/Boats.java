package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Boats at the docks (docs/LIVING_WORLD_2.md §6.1, §6.3): every {@code DOCK} of an embodied town near a player has its
 * boats moored off it (1 to 3, as the water allows; at most 6 a town and 24 on the server, fewer as the server slows), in
 * the culture's wood. A fisher takes one out ({@link Row}): rows out 20 to 60 blocks over open water, fishes there, and
 * rows back before the working stretch ends (at noon, at dusk). The boat is moved by the work — a little arithmetic a
 * tick — and the fisher rides it. Boats are views: tagged {@link #TAG}, never saved, moored again from the books when
 * nobody is near, taken away when the town goes abstract.
 */
public final class Boats {
	/** Entity tag of a dock's boats: never written into a chunk. */
	public static final String TAG = "hearth.boat";
	static final int PASS_TICKS = 40;
	/** A dock is dressed with its boats within this many blocks of a player. */
	static final int PLAYER_RANGE = 64;

	/** A place a boat lies off a dock. */
	static final class Mooring {
		final long settlement;
		final BlockPos dock;
		final BlockPos water;
		final Direction out;
		@Nullable UUID boat;
		@Nullable UUID rower;

		Mooring(long settlement, BlockPos dock, BlockPos water, Direction out) {
			this.settlement = settlement;
			this.dock = dock;
			this.water = water;
			this.out = out;
		}
	}

	/** Moorings by settlement (worked out once each time the town's docks are dressed). */
	private static final Map<Long, List<Mooring>> MOORINGS = new HashMap<>();
	private static long ticks;

	private Boats() {
	}

	public static int count() {
		int n = 0;
		for (List<Mooring> l : MOORINGS.values()) {
			for (Mooring m : l) {
				if (m.boat != null) {
					n++;
				}
			}
		}
		return n;
	}

	static int count(long settlement) {
		int n = 0;
		for (Mooring m : MOORINGS.getOrDefault(settlement, List.of())) {
			if (m.boat != null) {
				n++;
			}
		}
		return n;
	}

	/** From the field work tick. */
	static void tick(MinecraftServer server) {
		if (++ticks % PASS_TICKS != 0) {
			return;
		}
		FieldWorkConfig.Boats cfg = FieldWork.config().boats();
		double mspt = LoadCaps.mspt(server);
		double high = LoadCaps.highMspt();
		int serverCap = LoadCaps.cap(cfg.maxPerServer(), 0, mspt, high);
		boolean admitting = LoadCaps.admitting(mspt, high);
		Set<Long> live = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players();
			for (Settlement s : SettlementSources.embodied(level)) {
				live.add(s.id());
				List<Poi> docks = s.pois(PoiType.DOCK);
				if (docks.isEmpty()) {
					continue;
				}
				List<Mooring> moorings = MOORINGS.computeIfAbsent(s.id(), k -> new ArrayList<>());
				for (Poi dock : docks) {
					boolean near = false;
					for (ServerPlayer p : players) {
						if (!p.isSpectator() && p.blockPosition().distSqr(dock.pos()) < (long) PLAYER_RANGE * PLAYER_RANGE) {
							near = true;
							break;
						}
					}
					List<Mooring> here = at(moorings, dock.pos());
					if (!near) {
						for (Mooring m : here) {
							if (m.rower == null) {
								discard(level, m);
							}
						}
						continue;
					}
					if (here.isEmpty() && level.isLoaded(dock.pos())) {
						for (BlockPos w : waters(level, dock, cfg.perDock())) {
							Mooring m = new Mooring(s.id(), dock.pos(), w, dock.facing());
							moorings.add(m);
							here.add(m);
						}
					}
					for (Mooring m : here) {
						keep(level, s, m, cfg, serverCap, admitting);
					}
				}
			}
		}
		Iterator<Map.Entry<Long, List<Mooring>>> it = MOORINGS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, List<Mooring>> e = it.next();
			if (!live.contains(e.getKey())) {
				for (ServerLevel level : server.getAllLevels()) {
					for (Mooring m : e.getValue()) {
						discard(level, m);
					}
				}
				it.remove();
			}
		}
	}

	private static List<Mooring> at(List<Mooring> all, BlockPos dock) {
		List<Mooring> out = new ArrayList<>();
		for (Mooring m : all) {
			if (m.dock.equals(dock)) {
				out.add(m);
			}
		}
		return out;
	}

	/** A boat at its mooring: put there if there is none (room allowing), brought back if it drifted and nobody watches. */
	private static void keep(ServerLevel level, Settlement s, Mooring m, FieldWorkConfig.Boats cfg, int serverCap, boolean admitting) {
		AbstractBoat boat = m.boat == null ? null : level.getEntity(m.boat) instanceof AbstractBoat b && b.isAlive() ? b : null;
		if (boat == null) {
			m.boat = null;
			m.rower = null;
			if (!admitting || count(s.id()) >= cfg.maxPerTown() || count() >= serverCap || !level.isLoaded(m.water)) {
				return;
			}
			AbstractBoat b = boatType(s.culture()).create(level, EntitySpawnReason.EVENT);
			if (b == null) {
				return;
			}
			b.addTag(TAG);
			b.snapTo(m.water.getX() + 0.5, m.water.getY() + 1, m.water.getZ() + 0.5, m.out.toYRot() + 90, 0);
			if (level.addFreshEntity(b)) {
				m.boat = b.getUUID();
			}
			return;
		}
		if (m.rower != null) {
			return;
		}
		// moored: nobody aboard but a player (a mob that stepped in is put ashore), and back at its place if it drifted
		boolean player = false;
		for (Entity p : boat.getPassengers()) {
			player |= p instanceof Player;
		}
		if (!player && !boat.getPassengers().isEmpty()) {
			boat.ejectPassengers();
		}
		if (!player && boat.position().distanceToSqr(Vec3.atBottomCenterOf(m.water)) > 9 && level.getNearestPlayer(boat, 24) == null
			&& level.getNearestPlayer(m.water.getX(), m.water.getY(), m.water.getZ(), 24, false) == null) {
			boat.snapTo(m.water.getX() + 0.5, m.water.getY() + 1, m.water.getZ() + 0.5, m.out.toYRot() + 90, 0);
			boat.setDeltaMovement(Vec3.ZERO);
		}
	}

	/**
	 * Where boats lie off a dock: open water three to five blocks out from its point and a little to either side, never
	 * touching the dock's planks (a boat that brushes a folk takes them aboard).
	 */
	static List<BlockPos> waters(ServerLevel level, Poi dock, int max) {
		List<BlockPos> out = new ArrayList<>();
		Direction f = dock.facing();
		Direction side = f.getClockWise();
		int[][] offsets = {{4, 0}, {4, -3}, {4, 3}, {6, 0}, {5, -2}, {5, 2}};
		for (int[] o : offsets) {
			if (out.size() >= max) {
				break;
			}
			BlockPos base = dock.pos().relative(f, o[0]).relative(side, o[1]);
			for (int dy = 1; dy >= -3; dy--) {
				BlockPos w = base.offset(0, dy, 0);
				if (open(level, w) && clearOfLand(level, w)) {
					boolean apart = true;
					for (BlockPos q : out) {
						apart &= q.distSqr(w) >= 9;
					}
					if (apart) {
						out.add(w);
					}
					break;
				}
			}
		}
		return out;
	}

	/** Water a boat floats on: a water source with air over it. */
	static boolean open(ServerLevel level, BlockPos w) {
		if (!level.isLoaded(w)) {
			return false;
		}
		BlockState st = level.getBlockState(w);
		return st.getFluidState().is(FluidTags.WATER) && st.getFluidState().isSource() && level.getBlockState(w.above()).isAir();
	}

	private static boolean clearOfLand(ServerLevel level, BlockPos w) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!open(level, w.relative(d))) {
				return false;
			}
		}
		return true;
	}

	static EntityType<? extends AbstractBoat> boatType(Culture culture) {
		return switch (culture) {
			case NORTHERN, ALPINE, TUNDRA, MARITIME -> EntityTypes.SPRUCE_BOAT;
			case WISTERIA -> EntityTypes.CHERRY_BOAT;
			case TROPICAL -> EntityTypes.JUNGLE_BOAT;
			case DESERT, STEPPE, MESA -> EntityTypes.ACACIA_BOAT;
			case BAYOU -> EntityTypes.MANGROVE_BOAT;
			case KARST -> EntityTypes.BAMBOO_RAFT;
			case MOORLAND -> EntityTypes.BIRCH_BOAT;
			case MEDITERRANEAN -> EntityTypes.DARK_OAK_BOAT;
			default -> EntityTypes.OAK_BOAT;
		};
	}

	private static void discard(ServerLevel level, Mooring m) {
		if (m.boat != null) {
			Entity e = level.getEntity(m.boat);
			if (e != null) {
				e.ejectPassengers();
				e.discard();
			}
		}
		m.boat = null;
		m.rower = null;
	}

	/** A free boat at a dock of the fisher's town within 48 blocks, now theirs to take out; or null. */
	static @Nullable Mooring take(FolkBrain brain, ServerLevel level) {
		List<Mooring> all = MOORINGS.get(brain.settlement.id());
		if (all == null) {
			return null;
		}
		Mooring best = null;
		double bestD = 48 * 48;
		for (Mooring m : all) {
			if (m.boat == null || m.rower != null || !(level.getEntity(m.boat) instanceof AbstractBoat b) || !b.getPassengers().isEmpty()) {
				continue;
			}
			double d = m.dock.distSqr(brain.folk.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		if (best != null) {
			best.rower = brain.resident.id;
		}
		return best;
	}

	static void clear() {
		MOORINGS.clear();
	}

	// ---------------------------------------------------------------- the fisher in a boat

	/** A fisher's next stretch: out in a boat when one lies free at a dock near and there is time; else null. */
	public static @Nullable Task row(FolkBrain brain, ServerLevel level, Item fish) {
		if (minutesLeft(brain, level) < 90) {
			return null;
		}
		Mooring m = take(brain, level);
		if (m == null) {
			return null;
		}
		BlockPos spot = fishingSpot(level, m, brain);
		if (spot == null) {
			m.rower = null;
			return null;
		}
		return new Row(m, spot, fish);
	}

	/** Minutes left in the folk's working stretch (until their day moves on from work). */
	static int minutesLeft(FolkBrain brain, ServerLevel level) {
		Schedule sch = brain.schedule();
		if (sch == null) {
			return 0;
		}
		int now = NpcTime.minuteOfDay(level);
		Schedule.Block b = sch.at(now);
		int m = now;
		while (m < now + Schedule.DAY && sch.at(m % Schedule.DAY).equals(b)) {
			m += 5;
		}
		return m - now;
	}

	/**
	 * Open water 20 to {@code reach} blocks out from a mooring with nothing but water between (looked along a few
	 * headings out from the dock), or null.
	 */
	static @Nullable BlockPos fishingSpot(ServerLevel level, Mooring m, FolkBrain brain) {
		int reach = FieldWork.config().boats().reach();
		float base = m.out.toYRot();
		for (int tries = 0; tries < 10; tries++) {
			float yaw = base + (brain.random.nextFloat() - 0.5F) * 140F;
			int dist = 20 + brain.random.nextInt(Math.max(1, reach - 19));
			double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
			double dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
			boolean clear = true;
			BlockPos last = m.water;
			for (int k = 2; k <= dist; k += 2) {
				BlockPos p = new BlockPos((int) Math.floor(m.water.getX() + 0.5 + dx * k), m.water.getY(), (int) Math.floor(m.water.getZ() + 0.5 + dz * k));
				if (!open(level, p)) {
					clear = false;
					break;
				}
				last = p;
			}
			if (clear && last.distSqr(m.water) >= 18 * 18) {
				return last;
			}
		}
		return null;
	}

	/** Rowing out, fishing on the water, rowing back. */
	static final class Row implements Task {
		enum Leg { WALK, OUT, FISH, BACK, LAND }

		private final Mooring mooring;
		private final BlockPos spot;
		private final Item fish;
		private Leg leg = Leg.WALK;
		private int t;
		private int casts;
		private int wait;

		Row(Mooring mooring, BlockPos spot, Item fish) {
			this.mooring = mooring;
			this.spot = spot;
			this.fish = fish;
		}

		private @Nullable AbstractBoat boat(ServerLevel level) {
			return this.mooring.boat != null && level.getEntity(this.mooring.boat) instanceof AbstractBoat b && b.isAlive() ? b : null;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.t += elapsed;
			AbstractBoat boat = boat(level);
			if (boat == null || this.mooring.rower == null || !this.mooring.rower.equals(brain.resident.id)) {
				return Status.FAILED;
			}
			brain.hold(new ItemStack(Items.FISHING_ROD), ItemStack.EMPTY);
			switch (this.leg) {
				case WALK -> {
					if (brain.walk(level, this.mooring.dock, 1.5, 0.6) || this.t > 20 * 60) {
						brain.resetWalk();
						if (boat.distanceToSqr(brain.folk) > 9 * 9 || !boat.getPassengers().isEmpty()) {
							return Status.FAILED;
						}
						if (!brain.folk.startRiding(boat, true, false)) {
							return Status.FAILED;
						}
						this.leg = Leg.OUT;
						this.t = 0;
					} else if (brain.walkFailed()) {
						return Status.FAILED;
					}
				}
				case OUT -> {
					if (steer(boat, this.spot, elapsed) || this.t > 20 * 120) {
						this.leg = Leg.FISH;
						this.t = 0;
						this.wait = 60;
					}
					WorkBarks.maybe(brain, level, "fisher", 30, null);
				}
				case FISH -> {
					boat.setDeltaMovement(boat.getDeltaMovement().scale(0.5));
					this.wait -= elapsed;
					if (this.wait <= 0) {
						Vec3 side = boat.position().add(boat.getLookAngle().yRot(90 * Mth.DEG_TO_RAD).scale(2.5));
						if (this.casts % 2 == 0) {
							brain.swing();
							level.playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.6F);
							this.wait = 160 + brain.random.nextInt(260);
						} else {
							level.sendParticles(ParticleTypes.SPLASH, side.x, side.y + 0.4, side.z, 8, 0.2, 0, 0.2, 0.1);
							level.playSound(null, boat.blockPosition(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
							brain.swing();
							if (brain.random.nextInt(3) != 0) {
								brain.hold(new ItemStack(Items.FISHING_ROD), new ItemStack(this.fish));
								brain.pose(FolkPose.CHEER, 30);
							}
							this.wait = 40;
						}
						this.casts++;
						// home in good time: the boat back before the stretch ends (noon, dusk)
						if (this.casts >= 10 || minutesLeft(brain, level) < 40) {
							this.leg = Leg.BACK;
							this.t = 0;
						}
					}
				}
				case BACK -> {
					if (steer(boat, this.mooring.water, elapsed) || this.t > 20 * 120) {
						this.leg = Leg.LAND;
						this.t = 0;
					}
				}
				case LAND -> {
					land(brain, level, boat);
					return Status.DONE;
				}
			}
			return Status.RUNNING;
		}

		/** Moves the boat a little toward a point: true when it is there. */
		private static boolean steer(AbstractBoat boat, BlockPos to, int elapsed) {
			Vec3 at = boat.position();
			double dx = to.getX() + 0.5 - at.x;
			double dz = to.getZ() + 0.5 - at.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < 1.5) {
				boat.setDeltaMovement(Vec3.ZERO);
				return true;
			}
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float turn = Mth.wrapDegrees(yaw - boat.getYRot());
			boat.setYRot(boat.getYRot() + Mth.clamp(turn, -8F * elapsed, 8F * elapsed));
			double speed = Math.min(0.16, d * 0.04 + 0.04);
			Vec3 v = boat.getDeltaMovement();
			boat.setDeltaMovement(dx / d * speed, v.y, dz / d * speed);
			return false;
		}

		private void land(FolkBrain brain, ServerLevel level, AbstractBoat boat) {
			if (brain.folk.isPassenger()) {
				brain.folk.stopRiding();
			}
			BlockPos stand = FolkSpots.standableNear(level, this.mooring.dock, 3);
			if (stand != null) {
				brain.folk.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, brain.folk.getYRot(), 0);
			}
			boat.setDeltaMovement(Vec3.ZERO);
			this.mooring.rower = null;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			AbstractBoat boat = boat(level);
			if (brain.folk.isPassenger()) {
				if (boat != null) {
					land(brain, level, boat);
				} else {
					brain.folk.stopRiding();
				}
			}
			if (brain.resident.id.equals(this.mooring.rower)) {
				this.mooring.rower = null;
			}
			brain.folk.setFolkPose(FolkPose.NONE);
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return switch (this.leg) {
				case WALK -> "going down to the boat";
				case OUT -> "rowing out";
				case FISH -> "fishing from the boat (" + this.casts / 2 + " casts)";
				case BACK -> "rowing back";
				case LAND -> "tying up the boat";
			};
		}
	}
}
