package dev.voidmc.hearth.npc.travel;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.LoadCaps;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.roads.Band;
import dev.voidmc.hearth.sim.roads.Journey;
import dev.voidmc.hearth.sim.roads.RoadBook;
import dev.voidmc.hearth.sim.roads.Roads;
import dev.voidmc.hearth.sim.roads.RoadsConfig;
import dev.voidmc.hearth.sim.roads.Traffic;
import dev.voidmc.hearth.sim.roads.Way;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The roads' people near players (docs/LIVING_WORLD_2.md §1.3): journeys put on the road as parties when a player comes
 * within {@code spawn_distance} of where the books have them (one new party every two seconds server-wide), folded
 * beyond {@code fold_distance}; bands at their camps and spots; road crews at the heads of roads being built. Caps on
 * parties and on their entities shrink as the server slows ({@link LoadCaps}): above 60 % of {@code high_mspt} nothing
 * new comes on the road, above it the farthest are folded. Time spent is shown under {@code travel} in
 * {@code /hearth sim stats}.
 */
public final class Parties {
	private static final int PASS = 40;
	/** Lines a kind of traveller has ({@code hearth.roads.bark.<kind>.0} on, in {@code lw_roads.json}). */
	static final int BARKS = 12;
	private static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("travel");
	/** The two-second passes alone (spawning, folding, staging), within {@link #TIMER}. */
	private static final LivingWorldStats.Timer PASSES = LivingWorldStats.timer("travel passes");
	private static final Map<Long, TravelParty> PARTIES = new HashMap<>();
	private static final Map<Long, BanditParty> BANDS = new HashMap<>();
	private static final Map<Long, RoadCrew> CREWS = new HashMap<>();
	/** Party to the level it is in. */
	private static final Map<Object, ServerLevel> LEVELS = new HashMap<>();
	private static final Map<java.util.UUID, Long> BARKED = new HashMap<>();
	private static long ticks;

	private Parties() {
	}

	public static void tick(MinecraftServer server) {
		long t0 = System.nanoTime();
		ticks++;
		try {
			for (Roads.State st : Roads.states()) {
				ServerLevel level = st.world().level();
				if (!st.net().enabled()) {
					continue;
				}
				tickLive(level, st);
				if (ticks % PASS == 0) {
					long p0 = System.nanoTime();
					pass(level, st);
					PASSES.add(System.nanoTime() - p0);
				}
			}
		} catch (RuntimeException e) {
			Hearth.LOG.error("hearth travel: the parties' tick failed", e);
		}
		TIMER.add(System.nanoTime() - t0);
	}

	/** Every tick: the live parties walk, the bands keep their watch, the crews work. */
	private static void tickLive(ServerLevel level, Roads.State st) {
		long now = level.getGameTime();
		long clock = st.world().clock();
		Traffic traffic = st.traffic();
		for (Iterator<Map.Entry<Long, TravelParty>> it = PARTIES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, TravelParty> e = it.next();
			TravelParty p = e.getValue();
			if (LEVELS.get(p) != level) {
				continue;
			}
			Journey j = traffic.journey(e.getKey());
			if (j == null || !p.alive()) {
				p.fold(level);
				LEVELS.remove(p);
				it.remove();
				continue;
			}
			p.tick(level, traffic, j, clock);
			if (Math.floorMod(now, 40) == Math.floorMod(p.journeyId, 40)) {
				bark(level, p, now);
			}
		}
		for (Iterator<Map.Entry<Long, BanditParty>> it = BANDS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, BanditParty> e = it.next();
			BanditParty b = e.getValue();
			if (LEVELS.get(b) != level) {
				continue;
			}
			if (traffic.band(e.getKey()) == null && b.state != BanditParty.State.FLEE || !b.alive()) {
				b.fold(level, traffic);
				LEVELS.remove(b);
				it.remove();
				continue;
			}
			b.tick(level, traffic, now);
		}
		for (Iterator<Map.Entry<Long, RoadCrew>> it = CREWS.entrySet().iterator(); it.hasNext(); ) {
			RoadCrew c = it.next().getValue();
			if (LEVELS.get(c) != level) {
				continue;
			}
			c.tick(level, st.live(), st.world().settlement(c.town), now, clock);
		}
	}

	/** Every two seconds: who comes on the road, who goes. */
	private static void pass(ServerLevel level, Roads.State st) {
		RoadsConfig.Parties cfg = Roads.config().parties();
		double mspt = LoadCaps.mspt(level.getServer());
		double high = LoadCaps.highMspt();
		int maxParties = LoadCaps.cap(cfg.maxParties(), 0, mspt, high);
		int maxEntities = LoadCaps.cap(cfg.maxEntities(), 0, mspt, high);
		boolean admitting = LoadCaps.admitting(mspt, high);
		boolean shedding = LoadCaps.shedding(mspt, high);
		Traffic traffic = st.traffic();
		long clock = st.world().clock();
		// folding: out of sight, or the server is slow (the farthest first)
		foldFar(level, st, cfg.foldDistance(), shedding);
		if (level.players().isEmpty()) {
			return;
		}
		int parties = PARTIES.size() + BANDS.size() + CREWS.size();
		int entities = entities();
		// a robbery the books have coming in front of a player is played out
		for (TravelParty p : List.copyOf(PARTIES.values())) {
			if (LEVELS.get(p) != level) {
				continue;
			}
			Journey j = traffic.journey(p.journeyId);
			if (j != null && !j.outcome().isEmpty() && j.waylay() - clock < 600 && j.waylay() > clock - 200 && j.band() != 0 && !BANDS.containsKey(j.band())) {
				Band band = traffic.band(j.band());
				Vec3 at = p.position();
				if (band != null && at != null) {
					BanditParty b = BanditParty.spawn(level, traffic, band, BlockPos.containing(at).offset(12, 0, 6), true);
					if (b != null) {
						BANDS.put(band.id(), b);
						LEVELS.put(b, level);
						b.stage(p, level.getGameTime());
						traffic.postpone(j.id(), clock + 1200);
					}
				}
			}
		}
		if (!admitting || parties >= maxParties || entities >= maxEntities) {
			return;
		}
		// one new party a pass, the nearest wanted first
		int spawn = cfg.spawnDistance();
		Journey best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			for (Journey j : traffic.near(p.blockPosition(), spawn)) {
				if (PARTIES.containsKey(j.id())) {
					continue;
				}
				if (j.kind() == TravelKind.CARAVAN) {
					// an escort quest's own train is this caravan on the road: no second party
					var view = st.world().caravan(j.details().getLongOr("caravan", 0));
					if (view != null && view.escorted()) {
						continue;
					}
				}
				BlockPos at = traffic.position(j, clock);
				double d = Math.hypot(at.getX() - p.getX(), at.getZ() - p.getZ());
				if (d < bestD && level.isLoaded(at) && level.isPositionEntityTicking(at)) {
					bestD = d;
					best = j;
				}
				break;
			}
		}
		if (best != null) {
			TravelParty party = TravelParty.spawn(level, traffic, best, traffic.position(best, clock));
			if (party != null) {
				PARTIES.put(best.id(), party);
				LEVELS.put(party, level);
				return;
			}
		}
		// a band at its camp (by day) or its spot (by night) near a player
		for (Band band : traffic.bands()) {
			if (BANDS.containsKey(band.id())) {
				continue;
			}
			Way w = st.net().known(band.road());
			if (w == null || w.route() == null) {
				continue;
			}
			BlockPos spot = spot(st, w, band);
			boolean night = BanditParty.night(level);
			double toCamp = Wayfarers.nearestPlayer(level, band.camp().getX(), band.camp().getZ());
			double toSpot = Wayfarers.nearestPlayer(level, spot.getX(), spot.getZ());
			boolean atSpot = night && toSpot < 64;
			if (!atSpot && toCamp > spawn || !level.isLoaded(atSpot ? spot : band.camp())) {
				continue;
			}
			BanditParty b = BanditParty.spawn(level, traffic, band, spot, atSpot);
			if (b != null) {
				BANDS.put(band.id(), b);
				LEVELS.put(b, level);
				return;
			}
		}
		// a road crew at the head of a road being built, a player near it
		for (RoadBook.LiveRoad r : st.live().book().live()) {
			if (r.finished() || r.way() == null || r.spur()) {
				continue;
			}
			for (boolean fromA : new boolean[]{true, false}) {
				long key = r.id() * 2 + (fromA ? 0 : 1);
				int head = r.head(fromA);
				if (head < 0 || CREWS.containsKey(key)) {
					continue;
				}
				var seg = r.way().segments().get(head);
				int cx = (seg.minX() + seg.maxX()) / 2;
				int cz = (seg.minZ() + seg.maxZ()) / 2;
				if (Wayfarers.nearestPlayer(level, cx, cz) > spawn || !level.isLoaded(new BlockPos(cx, 64, cz))) {
					continue;
				}
				SimSettlement town = st.world().settlement(fromA ? r.a() : r.b());
				if (town == null) {
					continue;
				}
				RoadCrew crew = RoadCrew.spawn(level, r, fromA, town, clock);
				if (crew != null) {
					CREWS.put(key, crew);
					LEVELS.put(crew, level);
					return;
				}
			}
		}
	}

	/** The ambush spot of a band: on its road at its distance along. */
	static BlockPos spot(Roads.State st, Way w, Band band) {
		var r = w.route();
		if (r == null) {
			return band.camp();
		}
		double acc = 0;
		for (int i = 1; i < r.size(); i++) {
			acc += Math.hypot(r.xs()[i] - r.xs()[i - 1], r.zs()[i] - r.zs()[i - 1]);
			if (acc >= band.along()) {
				return new BlockPos(r.xs()[i], r.ys()[i], r.zs()[i]);
			}
		}
		return new BlockPos(r.xs()[r.size() - 1], r.ys()[r.size() - 1], r.zs()[r.size() - 1]);
	}

	private static void foldFar(ServerLevel level, Roads.State st, int foldDistance, boolean shedding) {
		Object farthest = null;
		double farD = -1;
		for (Iterator<Map.Entry<Long, TravelParty>> it = PARTIES.entrySet().iterator(); it.hasNext(); ) {
			TravelParty p = it.next().getValue();
			if (LEVELS.get(p) != level) {
				continue;
			}
			Vec3 at = p.position();
			double d = at == null ? Double.MAX_VALUE : Wayfarers.nearestPlayer(level, at.x, at.z);
			if (d > foldDistance) {
				p.fold(level);
				LEVELS.remove(p);
				it.remove();
			} else if (d > farD) {
				farD = d;
				farthest = p;
			}
		}
		for (Iterator<Map.Entry<Long, BanditParty>> it = BANDS.entrySet().iterator(); it.hasNext(); ) {
			BanditParty b = it.next().getValue();
			if (LEVELS.get(b) != level) {
				continue;
			}
			double d = Math.min(Wayfarers.nearestPlayer(level, b.campPos.getX(), b.campPos.getZ()), Wayfarers.nearestPlayer(level, b.spot.getX(), b.spot.getZ()));
			if (!b.men.isEmpty()) {
				d = Math.min(d, Wayfarers.nearestPlayer(level, b.men.getFirst().getX(), b.men.getFirst().getZ()));
			}
			if (d > foldDistance + 32 || b.state == BanditParty.State.FLEE && d > 40 && b.men.stream().allMatch(f -> Wayfarers.nearestPlayer(level, f.getX(), f.getZ()) > 32)) {
				b.fold(level, st.traffic());
				LEVELS.remove(b);
				it.remove();
			}
		}
		for (Iterator<Map.Entry<Long, RoadCrew>> it = CREWS.entrySet().iterator(); it.hasNext(); ) {
			RoadCrew c = it.next().getValue();
			if (LEVELS.get(c) != level) {
				continue;
			}
			BlockPos at = c.centre();
			RoadBook.LiveRoad r = st.live().book().road(c.roadId);
			boolean done = r == null || r.finished() || r.way() == null || c.men.isEmpty();
			if (done || at == null || Wayfarers.nearestPlayer(level, at.getX(), at.getZ()) > foldDistance) {
				c.fold(level, st.world().settlement(c.town));
				LEVELS.remove(c);
				it.remove();
			}
		}
		if (shedding && farthest instanceof TravelParty p) {
			p.fold(level);
			LEVELS.remove(p);
			PARTIES.remove(p.journeyId);
		}
	}

	static int entities() {
		int n = 0;
		for (TravelParty p : PARTIES.values()) {
			n += p.size();
		}
		for (BanditParty b : BANDS.values()) {
			n += b.size();
		}
		for (RoadCrew c : CREWS.values()) {
			n += c.size();
		}
		return n;
	}

	public static int parties() {
		return PARTIES.size() + BANDS.size() + CREWS.size();
	}

	public static int travellers() {
		return entities();
	}

	static @Nullable TravelParty party(long journeyId) {
		return PARTIES.get(journeyId);
	}

	/** Whether a journey's party is on the road now. */
	public static boolean embodied(long journeyId) {
		return PARTIES.containsKey(journeyId);
	}

	/** The settlement whose residents travel with a party. */
	static @Nullable SimSettlement home(ServerLevel level, TravelParty party) {
		SimWorld w = SimServer.world(level);
		return w == null ? null : w.settlement(party.kind == TravelKind.SETTLERS ? party.to : party.from);
	}

	/** Everyone off the roads (the server stopping). */
	public static void foldAll() {
		for (TravelParty p : PARTIES.values()) {
			ServerLevel level = LEVELS.get(p);
			if (level != null) {
				p.fold(level);
			}
		}
		for (BanditParty b : BANDS.values()) {
			ServerLevel level = LEVELS.get(b);
			Roads.State st = level == null ? null : Roads.state(SimServer.world(level));
			if (level != null && st != null) {
				b.fold(level, st.traffic());
			}
		}
		for (RoadCrew c : CREWS.values()) {
			ServerLevel level = LEVELS.get(c);
			if (level != null) {
				SimWorld w = SimServer.world(level);
				c.fold(level, w == null ? null : w.settlement(c.town));
			}
		}
		PARTIES.clear();
		BANDS.clear();
		CREWS.clear();
		LEVELS.clear();
		BARKED.clear();
	}

	/** A tagged pack or draught animal died: its party hears of it. */
	public static void beastDied(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (entity instanceof Mob mob) {
			for (TravelParty p : PARTIES.values()) {
				p.beastKilled(level, mob, source);
			}
		}
	}

	// ---------------------------------------------------------------- barks

	/** Now and then someone of a party says a line to a player passing close by (cooldowns per party and per player). */
	static void bark(ServerLevel level, TravelParty p, long now) {
		Vec3 at = p.position();
		if (at == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(at) > 12 * 12 || NpcModule.conversations().talkingTo(player) != null) {
				continue;
			}
			Long last = BARKED.get(player.getUUID());
			if (last != null && now - last < 20 * 20) {
				continue;
			}
			BARKED.put(player.getUUID(), now);
			Folk f = p.lead();
			if (f != null) {
				String kind = switch (p.kind) {
					case PEDLAR -> "pedlar";
					case PILGRIMS -> "pilgrim";
					case MESSENGER -> "messenger";
					case CARTER, MARKET_GOERS, SETTLERS -> "carter";
					case PATROL -> "patrol";
					case CARAVAN -> "caravan";
					default -> "traveller";
				};
				BanditParty.say(level, f, Component.translatable("hearth.roads.bark." + kind + "." + (int) Math.floorMod(now / 20 + f.getId(), BARKS)));
			}
			return;
		}
	}

	// ---------------------------------------------------------------- camps left by a crash

	/** The camp blocks standing now, per level (cleared as camps are struck; what is left after a crash is taken down). */
	static final HearthDataKey<List<Long>> CAMPS = HearthDataKey.register(Hearth.id("npc/road_camps"), HearthDataKey.Scope.WORLD,
		Codec.LONG.listOf().xmap(ArrayList::new, l -> l), ArrayList::new);

	/** The camp blocks' key (the tour reads it). */
	public static HearthDataKey<List<Long>> campKey() {
		return CAMPS;
	}

	static void remember(ServerLevel level, Camp camp) {
		HearthWorld w = HearthWorld.of(level);
		if (w == null) {
			return;
		}
		List<Long> l = w.data(CAMPS);
		for (BlockPos p : camp.placed()) {
			l.add(p.asLong());
		}
		w.markDirty();
	}

	static void forget(ServerLevel level, Camp camp) {
		HearthWorld w = HearthWorld.of(level);
		if (w == null) {
			return;
		}
		List<Long> l = w.data(CAMPS);
		for (BlockPos p : camp.placed()) {
			l.remove(p.asLong());
		}
		w.markDirty();
	}

	/**
	 * Takes down camp blocks a crash left standing (on the first tick a level is loaded): campfires, carpets, wool and
	 * fences of the camps, only where they are still those blocks.
	 */
	public static void sweepCamps(ServerLevel level) {
		HearthWorld w = HearthWorld.of(level);
		if (w == null) {
			return;
		}
		List<Long> l = w.data(CAMPS);
		if (l.isEmpty()) {
			return;
		}
		int n = 0;
		List<Long> later = new ArrayList<>();
		for (long at : l) {
			BlockPos p = BlockPos.of(at);
			if (!level.isLoaded(p)) {
				later.add(at);
				continue;
			}
			var s = level.getBlockState(p);
			if (s.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) || s.is(net.minecraft.tags.BlockTags.WOOL_CARPETS) || s.is(net.minecraft.tags.BlockTags.WOOL)
				|| s.is(net.minecraft.world.level.block.Blocks.OAK_FENCE) || s.is(net.minecraft.world.level.block.Blocks.CHEST)) {
				if (level.getBlockEntity(p) instanceof net.minecraft.world.Container box) {
					box.clearContent();
				}
				level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				n++;
			}
		}
		l.clear();
		l.addAll(later);
		w.markDirty();
		if (n > 0) {
			Hearth.LOG.info("hearth travel: took down {} camp blocks left on the roads", n);
		}
	}
}
