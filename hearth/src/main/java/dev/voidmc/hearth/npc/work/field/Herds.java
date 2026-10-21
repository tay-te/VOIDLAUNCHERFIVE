package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The flocks themselves (docs/LIVING_WORLD_2.md §6.3): stocked pastures' beasts as entities near players — in the pen
 * by night, on the way out and home with the herder and a dog at 07:00 and 18:00, grazing on the far meadow by day. Where
 * a flock is is a function of the time and of the herder's own folk while they walk it, so the beasts appear where the
 * books have them. They are views: tagged {@link #TAG} (never saved), spawned only within {@code player_distance} of a
 * player, at most 8 a pasture, 24 a town and 96 on the server (fewer as the server slows; the farthest go first when it
 * is slow), and taken away again when nobody is near. A beast killed or taken is a loss to the flock's books
 * ({@code hearth:npc/flocks}), made good a beast a day.
 */
public final class Herds {
	/** Entity tag of a flock's beasts and dog: never written into a chunk. */
	public static final String TAG = "hearth.flock";
	/** Server ticks between passes. */
	static final int PASS_TICKS = 20;

	/** One pasture's flock as it stands now. */
	static final class Herd {
		final long settlement;
		final int plot;
		final List<UUID> beasts = new ArrayList<>();
		@Nullable UUID dog;
		final List<BlockPos> openedGates = new ArrayList<>();
		BlockPos anchor;
		Commute.Phase phase = Commute.Phase.NONE;

		Herd(long settlement, int plot, BlockPos anchor) {
			this.settlement = settlement;
			this.plot = plot;
			this.anchor = anchor;
		}

		int size() {
			return this.beasts.size() + (this.dog != null ? 1 : 0);
		}
	}

	private static final Map<Long, Herd> HERDS = new HashMap<>();
	/** Which herd each beast belongs to (for its loss when it dies). */
	private static final Map<UUID, Herd> BEAST = new HashMap<>();
	private static long ticks;

	private Herds() {
	}

	/** Flock entities (beasts and dogs) now, server-wide. */
	public static int count() {
		int n = 0;
		for (Herd h : HERDS.values()) {
			n += h.size();
		}
		return n;
	}

	/** Flock entities of one town now. */
	public static int count(long settlementId) {
		int n = 0;
		for (Herd h : HERDS.values()) {
			if (h.settlement == settlementId) {
				n += h.size();
			}
		}
		return n;
	}

	static long key(long settlement, int plot) {
		return settlement * 1_000_003L ^ plot;
	}

	/** From the field work tick. */
	static void tick(MinecraftServer server) {
		if (++ticks % PASS_TICKS != 0) {
			return;
		}
		FieldWorkConfig.Flocks cfg = FieldWork.config().flocks();
		double mspt = LoadCaps.mspt(server);
		double high = LoadCaps.highMspt();
		int serverCap = LoadCaps.cap(cfg.maxPerServer(), 0, mspt, high);
		boolean admitting = LoadCaps.admitting(mspt, high);
		Set<Long> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players();
			if (players.isEmpty()) {
				continue;
			}
			for (Settlement s : SettlementSources.all(level)) {
				if (!near(s, players, cfg)) {
					continue;
				}
				RemoteSites.Plan plan = FieldSites.plan(s);
				for (PlotView p : s.plots()) {
					if (p.state() == PlotState.BUILT && p.spec().archetype().equals(RemoteSites.PASTURE)) {
						seen.add(key(s.id(), p.id()));
						pasture(level, s, p, plan, players, cfg, serverCap, admitting);
					}
				}
			}
		}
		// herds of pastures no longer looked at (their town far from everyone, the plot gone)
		Iterator<Map.Entry<Long, Herd>> it = HERDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Herd> e = it.next();
			if (!seen.contains(e.getKey())) {
				discard(server, e.getValue());
				it.remove();
			}
		}
		if (LoadCaps.shedding(mspt, high) || count() > serverCap) {
			shed(server, serverCap);
		}
	}

	/** Whether any player is near enough to a town that one of its flocks (wherever it grazes) could be near them. */
	private static boolean near(Settlement s, List<ServerPlayer> players, FieldWorkConfig.Flocks cfg) {
		double reach = s.territoryRadius() + FieldWork.config().sites().pastureDistance() + cfg.playerDistance() + 32;
		for (ServerPlayer p : players) {
			if (Commute.distance(p.blockPosition(), s.center()) <= reach) {
				return true;
			}
		}
		return false;
	}

	private static void pasture(ServerLevel level, Settlement s, PlotView p, RemoteSites.Plan plan, List<ServerPlayer> players, FieldWorkConfig.Flocks cfg,
		int serverCap, boolean admitting) {
		long key = key(s.id(), p.id());
		Herd herd = HERDS.get(key);
		List<Resident> herders = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.work == p.id() && r.job == JobType.HERDER && r.age == AgeStage.ADULT && !s.isResidentAway(r.id)) {
				herders.add(r);
			}
		}
		PlotSpec spec = p.spec();
		int pens = 0;
		for (Poi poi : s.pois(PoiType.PEN)) {
			if (inside(spec, poi.pos(), 0)) {
				pens++;
			}
		}
		Flocks.Book book = s.data(Flocks.KEY);
		int size = Flocks.size(herders.size(), cfg.herdPerHerder(), spec.sizeX() * spec.sizeZ(), pens, book.losses(p.id()));
		RemoteSites.Site site = null;
		for (RemoteSites.Site st : plan.sites()) {
			if (st.kind() == RemoteSites.Kind.PASTURE && st.plot() == p.id()) {
				site = st;
				break;
			}
		}
		// where the flock is: the pen, the way, the meadow; with the herder's own folk while they walk it
		Folk herder = null;
		for (Resident r : herders) {
			Folk f = NpcModule.embodiment().entityOf(r.id);
			if (f != null && !f.isFolded()) {
				herder = f;
				break;
			}
		}
		Commute.Where at = site == null ? Commute.Where.NONE : DroveWork.phase(level, site);
		BlockPos pen = RemoteSites.centre(spec);
		BlockPos anchor = switch (at.phase()) {
			case NONE -> pen;
			case OUT, BACK -> herder != null ? herder.blockPosition() : Commute.along(site.from(), site.work(), at.fraction());
			case AT -> site.work();
		};
		boolean onTheWay = at.phase() == Commute.Phase.OUT || at.phase() == Commute.Phase.BACK;
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer pl : players) {
			if (!pl.isSpectator()) {
				nearest = Math.min(nearest, Math.sqrt(pl.distanceToSqr(Vec3.atCenterOf(anchor))));
			}
		}
		boolean ticking = level.isLoaded(anchor) && level.isPositionEntityTicking(anchor);
		// a flock on the way is only out with its herder; one at the pen or on the meadow is there for anyone who comes
		boolean wanted = size > 0 && ticking && (!onTheWay || herder != null);
		if (herd != null && (!wanted || nearest > cfg.playerDistance() + 16)) {
			discard(level.getServer(), herd);
			HERDS.remove(key);
			return;
		}
		if (herd == null) {
			if (!wanted || nearest > cfg.playerDistance() || !admitting) {
				return;
			}
			int townRoom = cfg.maxPerTown() - count(s.id());
			int room = Math.min(Math.min(cfg.maxPerPasture(), townRoom), serverCap - count());
			if (room <= 0) {
				return;
			}
			herd = new Herd(s.id(), p.id(), anchor);
			HERDS.put(key, herd);
			boolean drove = site != null && at.phase() != Commute.Phase.NONE;
			int beasts = Math.min(size, drove ? room - 1 : room);
			spawn(level, s, herd, spec, at.phase(), anchor, beasts, drove && herder != null && room > beasts);
		}
		if (onTheWay && herder != null && herd.dog == null && herd.size() < cfg.maxPerPasture() && count() < serverCap && admitting) {
			// the herder's dog comes out with them when the flock leaves the pen
			spawn(level, s, herd, spec, at.phase(), herder.blockPosition(), 0, true);
		}
		herd.anchor = anchor;
		herd.phase = at.phase();
		steer(level, herd, spec, site, at.phase(), herder, pen);
	}

	// ---------------------------------------------------------------- the beasts

	private static void spawn(ServerLevel level, Settlement s, Herd herd, PlotSpec spec, Commute.Phase phase, BlockPos anchor, int beasts, boolean dog) {
		RandomSource r = RandomSource.create(herd.settlement * 31 + herd.plot + level.getGameTime());
		for (int i = 0; i < beasts; i++) {
			BlockPos at = phase == Commute.Phase.NONE ? inPen(level, spec, r) : near(level, anchor, 6, r);
			if (at == null) {
				continue;
			}
			Flocks.Species kind = Flocks.speciesOf(s.culture(), i);
			EntityType<? extends Animal> type = switch (kind) {
				case SHEEP -> EntityTypes.SHEEP;
				case COW -> EntityTypes.COW;
				case GOAT -> EntityTypes.GOAT;
				case PIG -> EntityTypes.PIG;
			};
			Animal a = type.create(level, EntitySpawnReason.EVENT);
			if (a == null) {
				continue;
			}
			a.addTag(TAG);
			if (a instanceof Sheep sheep) {
				// mostly white, a few brown, grey and black, as a real flock
				int c = r.nextInt(12);
				sheep.setColor(c == 0 ? DyeColor.BROWN : c == 1 ? DyeColor.BLACK : c == 2 ? DyeColor.GRAY : c == 3 ? DyeColor.LIGHT_GRAY : DyeColor.WHITE);
			}
			// no lambs of a view: the flock's increase is the sim's
			a.setAge(6000);
			a.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
			if (level.addFreshEntity(a)) {
				herd.beasts.add(a.getUUID());
				BEAST.put(a.getUUID(), herd);
			}
		}
		if (dog) {
			BlockPos at = near(level, anchor, 3, r);
			Wolf w = at == null ? null : EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
			if (w != null) {
				w.addTag(TAG);
				// the herder's dog: tame (it leaves the sheep alone), nobody's to order about
				w.setTame(true, false);
				w.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
				if (level.addFreshEntity(w)) {
					herd.dog = w.getUUID();
					BEAST.put(w.getUUID(), herd);
				}
			}
		}
	}

	/**
	 * Keeps the beasts with the work: in the pen, ahead of the herder on the way (the dog at the herder's heel), on the
	 * meadow by day. Gates of the pen stand open while the flock goes through them.
	 */
	private static void steer(ServerLevel level, Herd herd, PlotSpec spec, RemoteSites.@Nullable Site site, Commute.Phase phase, @Nullable Folk herder,
		BlockPos pen) {
		RandomSource r = level.getRandom();
		Vec3 goal;
		double keep;
		switch (phase) {
			case OUT, BACK -> {
				if (herder == null) {
					return;
				}
				BlockPos end = phase == Commute.Phase.OUT ? site.work() : pen;
				Vec3 h = herder.position();
				Vec3 dir = Vec3.atCenterOf(end).subtract(h).multiply(1, 0, 1);
				double len = dir.length();
				goal = len < 6 ? Vec3.atBottomCenterOf(end) : h.add(dir.scale(5 / len));
				keep = 4;
				gates(level, herd, spec, herder.blockPosition());
			}
			case AT -> {
				goal = Vec3.atBottomCenterOf(site.work());
				keep = site.radius() + 4;
				closeGates(level, herd);
			}
			default -> {
				goal = Vec3.atBottomCenterOf(pen);
				keep = Math.max(spec.sizeX(), spec.sizeZ()) / 2.0 + 2;
				if (herder != null && herder.blockPosition().distSqr(RemoteSites.gate(spec)) < 10 * 10) {
					gates(level, herd, spec, herder.blockPosition());
				} else {
					closeGates(level, herd);
				}
			}
		}
		for (UUID id : herd.beasts) {
			if (!(level.getEntity(id) instanceof Animal a) || !a.isAlive()) {
				continue;
			}
			if (a.getAge() < 1000) {
				a.setAge(6000);
			}
			double d = a.position().distanceTo(goal);
			if (d > keep && (a.getNavigation().isDone() || r.nextInt(3) == 0)) {
				double jx = (r.nextDouble() - 0.5) * 4;
				double jz = (r.nextDouble() - 0.5) * 4;
				a.getNavigation().moveTo(goal.x + jx, goal.y, goal.z + jz, phase == Commute.Phase.NONE || phase == Commute.Phase.AT ? 1.0 : 1.15);
			}
		}
		if (herd.dog != null && level.getEntity(herd.dog) instanceof Wolf w && w.isAlive()) {
			if (herder != null && w.distanceTo(herder) > 5) {
				w.getNavigation().moveTo(herder, 1.2);
			}
		}
	}

	/** Opens the pen's fence gates near the herder (they swing shut again when the flock is through). */
	private static void gates(ServerLevel level, Herd herd, PlotSpec spec, BlockPos herder) {
		BlockPos gate = RemoteSites.gate(spec);
		if (herder.distSqr(gate) > 12 * 12) {
			closeGates(level, herd);
			return;
		}
		if (!herd.openedGates.isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int dy = -3; dy <= 3; dy++) {
					m.set(gate.getX() + dx, gate.getY() + dy, gate.getZ() + dz);
					if (!level.isLoaded(m) || !inside(spec, m, 1)) {
						continue;
					}
					BlockState st = level.getBlockState(m);
					if (st.getBlock() instanceof FenceGateBlock && !st.getValue(FenceGateBlock.OPEN)) {
						level.setBlock(m, st.setValue(FenceGateBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
						herd.openedGates.add(m.immutable());
					}
				}
			}
		}
	}

	private static void closeGates(ServerLevel level, Herd herd) {
		for (BlockPos g : herd.openedGates) {
			if (level.isLoaded(g)) {
				BlockState st = level.getBlockState(g);
				if (st.getBlock() instanceof FenceGateBlock && st.getValue(FenceGateBlock.OPEN)) {
					level.setBlock(g, st.setValue(FenceGateBlock.OPEN, false), net.minecraft.world.level.block.Block.UPDATE_ALL);
				}
			}
		}
		herd.openedGates.clear();
	}

	private static @Nullable BlockPos inPen(ServerLevel level, PlotSpec spec, RandomSource r) {
		for (int tries = 0; tries < 12; tries++) {
			int x = spec.origin().getX() + 2 + r.nextInt(Math.max(1, spec.sizeX() - 4));
			int z = spec.origin().getZ() + 2 + r.nextInt(Math.max(1, spec.sizeZ() - 4));
			BlockPos g = FieldPlacement.ground(level, new BlockPos(x, spec.origin().getY(), z));
			if (level.isLoaded(g) && FolkSpots.standable(level, g)) {
				return g;
			}
		}
		return null;
	}

	private static @Nullable BlockPos near(ServerLevel level, BlockPos anchor, int radius, RandomSource r) {
		for (int tries = 0; tries < 12; tries++) {
			BlockPos g = FieldPlacement.ground(level, anchor.offset(r.nextInt(2 * radius + 1) - radius, 0, r.nextInt(2 * radius + 1) - radius));
			if (level.isLoaded(g) && FolkSpots.standable(level, g)) {
				return g;
			}
		}
		return null;
	}

	private static boolean inside(PlotSpec spec, BlockPos p, int margin) {
		BlockPos o = spec.origin();
		return p.getX() >= o.getX() - margin && p.getX() < o.getX() + spec.sizeX() + margin && p.getZ() >= o.getZ() - margin
			&& p.getZ() < o.getZ() + spec.sizeZ() + margin;
	}

	// ---------------------------------------------------------------- going away

	private static void discard(MinecraftServer server, Herd herd) {
		// a copy: discarding a beast unloads it, and the unload listener (onRemoved) takes it out of herd.beasts
		List<UUID> beasts = List.copyOf(herd.beasts);
		for (ServerLevel level : server.getAllLevels()) {
			closeGates(level, herd);
			for (UUID id : beasts) {
				Entity e = level.getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
			if (herd.dog != null) {
				Entity e = level.getEntity(herd.dog);
				if (e != null) {
					e.discard();
				}
			}
		}
		for (UUID id : beasts) {
			BEAST.remove(id);
		}
		if (herd.dog != null) {
			BEAST.remove(herd.dog);
		}
		herd.beasts.clear();
		herd.dog = null;
	}

	/** Over the server's cap or the server slow: the herds farthest from any player go first. */
	private static void shed(MinecraftServer server, int cap) {
		List<Map.Entry<Long, Herd>> all = new ArrayList<>(HERDS.entrySet());
		Map<Herd, Double> far = new HashMap<>();
		for (Map.Entry<Long, Herd> e : all) {
			double best = Double.MAX_VALUE;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				best = Math.min(best, Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(e.getValue().anchor))));
			}
			far.put(e.getValue(), best);
		}
		all.sort(Comparator.comparingDouble(e -> -far.get(e.getValue())));
		int n = count();
		for (Map.Entry<Long, Herd> e : all) {
			if (n <= cap) {
				break;
			}
			n -= e.getValue().size();
			discard(server, e.getValue());
			HERDS.remove(e.getKey());
		}
	}

	/** A beast of a flock died (killed, drowned, taken by wolves): one fewer in the books until it is made good. */
	static void onDeath(ServerLevel level, Entity entity) {
		Herd herd = BEAST.remove(entity.getUUID());
		if (herd == null) {
			return;
		}
		herd.beasts.remove(entity.getUUID());
		if (entity.getUUID().equals(herd.dog)) {
			herd.dog = null;
			return;
		}
		Settlement s = SettlementSources.find(level, herd.settlement);
		if (s != null) {
			s.data(Flocks.KEY).lost(herd.plot);
			s.markDirty();
		}
	}

	/** A beast went away otherwise (its chunk unloaded, a command): the herd forgets it, the books do not change. */
	static void onRemoved(Entity entity) {
		Herd herd = BEAST.remove(entity.getUUID());
		if (herd != null) {
			herd.beasts.remove(entity.getUUID());
			if (entity.getUUID().equals(herd.dog)) {
				herd.dog = null;
			}
		}
	}

	/** Makes good the flocks' losses once a sim day (embodied towns: the abstract ones' books wait until looked at). */
	static void heal(Settlement s, long day) {
		Flocks.Book book = s.data(Flocks.KEY);
		if (book.heal(day, FieldWork.config().flocks().healPerDay())) {
			s.markDirty();
		}
	}

	static void clear() {
		HERDS.clear();
		BEAST.clear();
	}
}
