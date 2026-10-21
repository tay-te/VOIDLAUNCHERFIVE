package dev.voidmc.hearth.sim.roads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.RoadNetwork.RoadClass;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.sim.TestSupport;
import dev.voidmc.hearth.sim.atlas.History;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Journeys on the roads (docs/LIVING_WORLD_2.md §1.8): where a party is follows from its record alone (by day, stopped
 * at night, never going back), a caravan keeps the sim's books' times, residents on the road are away and come back.
 */
class TrafficTest {
	static final TerrainProbe FLAT = new SyntheticTerrain(SyntheticTerrain.Kind.FLAT, 3);

	/** A level for the traffic's books: two towns 1 600 blocks apart joined by a track, a caravan in the sim's books. */
	static final class World implements Traffic.Host {
		final Map<HearthDataKey<?>, Object> data = new HashMap<>();
		final Map<Long, SimSettlement> towns = new HashMap<>();
		long clock;
		HearthWorld.@Nullable CaravanView caravan;
		boolean doomed;
		int[] robbed;

		@Override
		@SuppressWarnings("unchecked")
		public <T> T data(HearthDataKey<T> key) {
			return (T) this.data.computeIfAbsent(key, k -> k.factory().get());
		}

		@Override
		public void markDirty() {
		}

		@Override
		public long clock() {
			return this.clock;
		}

		@Override
		public @Nullable SimSettlement settlement(long id) {
			return this.towns.get(id);
		}

		@Override
		public Collection<SimSettlement> settlements() {
			return this.towns.values();
		}

		@Override
		public HearthWorld.@Nullable CaravanView caravan(long id) {
			return this.caravan != null && this.caravan.id() == id ? this.caravan : null;
		}

		@Override
		public boolean doomed(long caravanId) {
			return this.doomed;
		}

		@Override
		public boolean caravanOnRoad(long caravanId) {
			return this.caravan != null && this.robbed == null;
		}

		@Override
		public int @Nullable [] robCaravan(long id) {
			this.robbed = this.caravan == null ? null : this.caravan.cargo();
			return this.robbed;
		}

		@Override
		public boolean escortCaravan(long id) {
			return true;
		}

		@Override
		public int groundY(int x, int z) {
			return FLAT.groundY(x, z);
		}

		@Override
		public boolean wooded(int x, int z) {
			return false;
		}

		@Override
		public long seed() {
			return 7;
		}

		@Override
		public @Nullable HearthWorld events() {
			return null;
		}

		@Override
		public @Nullable SimWorld world() {
			return null;
		}
	}

	static SimSettlement town(long id, String name, BlockPos at) {
		TestSupport.boot();
		History.Context ctx = TestSupport.context(FLAT);
		SimSettlement s = new SimSettlement(id, name, Culture.TEMPERATE, Level.OVERWORLD, at, 0, SimSettlement.NO_REGION, id);
		s.bind(ctx);
		var f = ctx.rules().config.founding();
		SettlementClock.found(s, ctx, f.residents(), f.goodsMap(), f.treasury());
		return s;
	}

	/** The traffic of two towns joined by one road. */
	static Object[] setup() {
		RoadWorld net = RoadWorldTest.world(5, 0.0);
		World w = new World();
		SimSettlement a = town(1001, "Ashbrook", new BlockPos(0, 65, 0));
		SimSettlement b = town(1002, "Coldbrook", new BlockPos(1600, 65, 300));
		w.towns.put(a.id(), a);
		w.towns.put(b.id(), b);
		Route r = RoadRouter.route(FLAT, FLAT, 80, 15, 1520, 285, RoadRouter.Avoid.NONE);
		assertNotNull(r);
		RoadGraph.Edge e = new RoadGraph.Edge(RoadGraph.edgeId(a.id(), b.id()), a.id(), b.id(), RoadClass.TRACK, false, r.length());
		Way way = Way.of(e, 80, 15, 1520, 285, a.name(), b.name(), r, RoadSegments.cut(e.id(), RoadClass.TRACK, r, RoadRouterTest.dressing()), true);
		net.publishLive(List.of(way), (x, i) -> true);
		return new Object[]{new Traffic(w, net), w, a, b, way};
	}

	@Test
	void aJourneyWalksByDayStopsAtNightAndNeverGoesBack() {
		Journey j = new Journey(1, TravelKind.PEDLAR, 1, 2, new long[0], 3000, 5000, Journey.WALK, Journey.arrival(5000, 3000, Journey.WALK), List.of(), false,
			0, Long.MAX_VALUE, 0, "", new CompoundTag());
		double last = -1;
		for (long t = j.depart(); t <= j.arrive() + 2000; t += 50) {
			double a = j.along(t);
			assertTrue(a >= last - 1e-9, "never back: " + a + " after " + last + " at " + t);
			if (!Journey.walking(t) && !Journey.walking(t - 50) && t - 50 > j.depart()) {
				assertEquals(last, a, 1e-9, "stopped at night at tick " + t);
			}
			last = a;
		}
		assertEquals(3000, j.along(j.arrive()), 1e-6, "there at its arrival");
		assertTrue(j.along(j.arrive() - 300) < 3000, "not there before");
		// 3 000 blocks at walking pace is more than a day's walking: it camps at least one night
		assertTrue(j.arrive() - j.depart() > Journey.DAY, "a night on the road");
		// the inverse of the walking clock
		for (long w = 0; w < 60000; w += 777) {
			assertEquals(w, Journey.walkingBetween(1234, Journey.afterWalking(1234, w)), "walking time round trip " + w);
		}
	}

	@Test
	void aRoundTripTurnsAndComesHome() {
		long depart = 200;
		double len = 400;
		long out = Journey.arrival(depart, len, Journey.WALK);
		long turn = out + 2400;
		long home = Journey.arrival(turn, len, Journey.WALK);
		Journey j = new Journey(2, TravelKind.PATROL, 1, 1, new long[0], len, depart, Journey.WALK, home, List.of(), true, turn, Long.MAX_VALUE, 0, "",
			new CompoundTag());
		assertEquals(len, j.along(out), 1e-6, "at the far end");
		assertEquals(len, j.along(turn), 1e-6, "waits there till it turns");
		assertTrue(j.resting(out + 100), "resting at the far end");
		assertTrue(j.returning(turn + 1) && j.along(turn + 2000) < len, "walking home");
		assertEquals(0, j.along(home), 1e-6, "home at the end");
	}

	@Test
	void aCaravanKeepsTheSimsTimesAndFollowsTheRoad() {
		Object[] s = setup();
		Traffic t = (Traffic) s[0];
		World w = (World) s[1];
		SimSettlement a = (SimSettlement) s[2];
		SimSettlement b = (SimSettlement) s[3];
		Way way = (Way) s[4];
		w.caravan = new HearthWorld.CaravanView(77, a.id(), b.id(), new int[]{0, 0, 40}, 300, 1000, 60_000, a.center(), b.center(), false);
		t.onCaravan(a, b, SettlementEvents.CaravanEvent.Kind.DEPARTED, 77, new int[]{0, 0, 40});
		Journey j = t.journeys().getFirst();
		assertEquals(TravelKind.CARAVAN, j.kind());
		assertEquals(1000, j.depart(), "the books' departure");
		assertEquals(60_000, j.arrive(), "the books' arrival");
		assertEquals(way.length(), j.length(), 1e-6, "along the road's length, not the crow's");
		// its place: the road's start when it leaves, the road's end when it arrives, on the road between
		BlockPos start = t.position(j, 1000);
		BlockPos end = t.position(j, 60_000);
		assertTrue(start.distSqr(new BlockPos(way.ax(), start.getY(), way.az())) < 4, "leaves from the road's end at " + start);
		assertTrue(end.distSqr(new BlockPos(way.bx(), end.getY(), way.bz())) < 4, "arrives at the far end at " + end);
		double[] mid = t.place(j, 30_000);
		double[] on = RoadWorld.nearestOn(way, (int) mid[0], (int) mid[2]);
		assertTrue(on != null && on[0] < 9, "on the road between: " + (on == null ? "?" : Math.sqrt(on[0])) + " blocks off");
		// the sim says it arrived: the journey ends with it
		t.onCaravan(a, b, SettlementEvents.CaravanEvent.Kind.ARRIVED, 77, new int[0]);
		assertTrue(t.journeys().isEmpty());
	}

	@Test
	void residentsOnTheRoadAreAwayUntilTheyAreHome() {
		Object[] s = setup();
		Traffic t = (Traffic) s[0];
		World w = (World) s[1];
		SimSettlement a = (SimSettlement) s[2];
		SimSettlement b = (SimSettlement) s[3];
		w.clock = a.simTick();
		List<UUID> who = Traffic.pick(a, 2);
		assertEquals(2, who.size());
		long id = t.send(TravelKind.MARKET_GOERS, a, b, who, w.clock, 0, true, 0, 2400, new CompoundTag());
		assertNotEquals(0, id);
		for (UUID r : who) {
			assertTrue(a.isResidentAway(r), "away while on the road");
		}
		Journey j = t.journey(id);
		assertNotNull(j);
		// the clock runs on to their return (the settlement's own clock with it)
		a.setSimTick(j.arrive() + 1);
		w.clock = j.arrive() + 1;
		t.tick();
		assertTrue(t.journeys().isEmpty(), "the journey ended");
		for (UUID r : who) {
			assertFalse(a.isResidentAway(r), "home again");
			Resident res = a.resident(r);
			assertNotNull(res);
		}
	}

	@Test
	void whenTheRoadsAreFullTheLeastImportantStayHome() {
		Object[] s = setup();
		Traffic t = (Traffic) s[0];
		SimSettlement a = (SimSettlement) s[2];
		SimSettlement b = (SimSettlement) s[3];
		int cap = Roads.config().traffic().maxJourneys();
		int sent = 0;
		for (int i = 0; i < cap; i++) {
			sent += t.send(TravelKind.PEDLAR, a, b, List.of(), 0, 0, false, 0, 0, new CompoundTag()) != 0 ? 1 : 0;
		}
		assertTrue(sent < cap, "pedlars stop before the roads are full (" + sent + ")");
		assertNotEquals(0, t.send(TravelKind.MESSENGER, a, b, List.of(), 0, 0, false, 0, 0, new CompoundTag()), "a messenger still goes");
		List<Journey> js = new ArrayList<>(t.journeys());
		assertTrue(js.size() <= cap);
	}
}
