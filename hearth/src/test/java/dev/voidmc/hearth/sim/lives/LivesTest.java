package dev.voidmc.hearth.sim.lives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * People with lives in the abstract (docs/LIVING_WORLD_2.md §4.8): an apprentice becomes a journeyman of their trade at
 * least nine times in ten when places exist; masters are made within 60–120 days of the journeyman's start; relations
 * stay bounded (8 a resident, ~200 bytes); feuds resolve or persist; the same seed gives the same town; and the day's
 * cost for a town of 150.
 */
class LivesTest {
	static final int CHILDHOOD = 48;
	static final Bonds.Rules RULES = () -> true;

	@BeforeAll
	static void boot() {
		dev.voidmc.hearth.sim.TestSupport.boot();
	}

	/** A made-up town's workplaces: plot id → trade and slots; homes in a row (each the next one's neighbour). */
	static final class Places implements Workplaces {
		final Map<JobType, List<Place>> places = new HashMap<>();

		Places add(int plot, JobType trade, int slots) {
			this.places.computeIfAbsent(trade, k -> new ArrayList<>()).add(new Place(plot, slots));
			return this;
		}

		@Override
		public List<Place> places(JobType trade) {
			return this.places.getOrDefault(trade, List.of());
		}

		@Override
		public int neighbour(int homePlot) {
			return homePlot < 0 ? Resident.NO_PLOT : homePlot ^ 1;
		}
	}

	private static int next;

	static Resident person(SimpleSettlement s, String name, boolean female, AgeStage age, JobType job, long born, int home, int work, String... traits) {
		Resident r = new Resident(new UUID(0x11FE5L, ++next), name, "Novak" + (home / 2), female, age, born, job, home, work, next, new CompoundTag());
		if (traits.length > 0) {
			r.extra("npc").store("traits", Codec.STRING.listOf(), List.of(traits));
		}
		s.addResident(r);
		return r;
	}

	static void parent(Resident child, Resident... parents) {
		child.extra("npc").store("parents", UUIDUtil.CODEC.listOf(), java.util.Arrays.stream(parents).map(p -> p.id).toList());
	}

	/** The clock's part each day: ages, and the job claims honoured (as SettlementClock.assignJobs does). */
	static void clock(SimpleSettlement s, Places wp, long day) {
		for (Resident r : s.residents()) {
			long age = day - r.bornDay;
			AgeStage st = age < CHILDHOOD ? AgeStage.CHILD : age < CHILDHOOD + 240 ? AgeStage.ADULT : AgeStage.ELDER;
			r.age = st;
			if (st == AgeStage.CHILD) {
				r.job = JobType.NONE;
				r.work = Resident.NO_PLOT;
			}
		}
		TownLives town = s.data(Lives.TOWN);
		Map<UUID, TownLives.Claim> claims = LivesClaims.claims(s, town, wp);
		for (Resident r : s.residents()) {
			TownLives.Claim c = claims.get(r.id);
			if (c != null && r.age == AgeStage.ADULT) {
				r.job = c.job();
				r.work = c.work();
			}
		}
	}

	static void step(SimpleSettlement s, Places wp, long day) {
		clock(s, wp, day);
		LivesDay.day(s, wp, day, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
	}

	/** A craft town: masters of four trades with room, and children of all ages, some theirs. */
	static SimpleSettlement craftTown(long id, int children) {
		SimpleSettlement s = new SimpleSettlement(id, "Lhota", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Places wp = places();
		JobType[] trades = {JobType.SMITH, JobType.CARPENTER, JobType.BAKER, JobType.TAILOR};
		List<Resident> masters = new ArrayList<>();
		for (int i = 0; i < trades.length; i++) {
			for (int k = 0; k < 2; k++) {
				masters.add(person(s, "M" + i + k, k == 1, AgeStage.ADULT, trades[i], -60 - i * 4 - k, 2 * (i * 2 + k), 100 + i * 2 + k));
			}
		}
		for (int c = 0; c < children; c++) {
			Resident kid = person(s, "C" + c, c % 2 == 0, AgeStage.CHILD, JobType.NONE, -5 - (c * 37) % 40, 2 * (c % 8), Resident.NO_PLOT);
			if (c % 3 == 0) {
				parent(kid, masters.get(c % masters.size()));
			}
		}
		return s;
	}

	static Places places() {
		Places wp = new Places();
		JobType[] trades = {JobType.SMITH, JobType.CARPENTER, JobType.BAKER, JobType.TAILOR};
		for (int i = 0; i < trades.length; i++) {
			wp.add(100 + i * 2, trades[i], 3).add(101 + i * 2, trades[i], 3);
		}
		return wp;
	}

	@Test
	void apprenticesBecomeJourneymenOfTheirTrade() {
		SimpleSettlement s = craftTown(31, 12);
		Places wp = places();
		Map<UUID, JobType> taught = new HashMap<>();
		Map<UUID, Long> grownOn = new HashMap<>();
		Map<UUID, Long> journeyOn = new HashMap<>();
		Map<UUID, Long> masterOn = new HashMap<>();
		for (long day = 0; day < 260; day++) {
			step(s, wp, day);
			for (Resident r : s.residents()) {
				JobType t = Life.apprenticeTrade(r);
				if (t != null) {
					taught.put(r.id, t);
				}
				if (taught.containsKey(r.id) && r.age == AgeStage.ADULT) {
					grownOn.putIfAbsent(r.id, day);
				}
				if (Life.journeyman(r) != null) {
					journeyOn.putIfAbsent(r.id, day);
				}
				if (taught.containsKey(r.id) && Life.masterOf(r) != null) {
					masterOn.putIfAbsent(r.id, day);
				}
			}
		}
		assertTrue(taught.size() >= 8, "most children apprenticed: " + taught.size());
		int ok = 0;
		int grown = 0;
		for (Map.Entry<UUID, Long> e : grownOn.entrySet()) {
			Resident r = s.resident(e.getKey());
			if (r == null) {
				continue; // gone with a thread (a runaway)
			}
			grown++;
			JobType trade = taught.get(e.getKey());
			// a journeyman of the trade, working it (the claim), or past it to master of it
			boolean journey = journeyOn.containsKey(e.getKey());
			boolean atTrade = r.job == trade || Life.masterOf(r) == trade;
			if (journey && atTrade) {
				ok++;
			}
		}
		assertTrue(grown >= 6, "apprentices grown: " + grown);
		assertTrue(ok >= 0.9 * grown, "journeymen of their trade " + ok + " of " + grown);
		// masters 60-120 days after they took up the trade
		assertTrue(!masterOn.isEmpty(), "some apprentices made master");
		for (Map.Entry<UUID, Long> e : masterOn.entrySet()) {
			Long took = journeyOn.get(e.getKey());
			if (took == null) {
				continue; // made master by a thread's turn (the forge passed to the lad), not by serving the time
			}
			long d = e.getValue() - took;
			assertTrue(d >= 60 && d <= 120, "master " + d + " days after the journeyman began");
		}
		assertTrue(s.data(Lives.TOWN).stats.get("masters_from_apprentices") >= masterOn.size());
	}

	@Test
	void journeymanWaitsWithoutPlaces() {
		// no places for the trade: the journeyman cannot be claimed into it, and their time does not run
		SimpleSettlement s = new SimpleSettlement(32, "Brod", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Places none = new Places();
		Resident m = person(s, "Master", false, AgeStage.ADULT, JobType.SMITH, -100, 0, 100);
		Life.master(m, JobType.SMITH, -100);
		Resident kid = person(s, "Kid", true, AgeStage.CHILD, JobType.NONE, -40, 0, Resident.NO_PLOT);
		for (long d = 0; d < 120; d++) {
			step(s, none, d);
		}
		assertNull(Life.masterOf(kid), "no master without working the trade");
		assertEquals(1, s.data(Lives.TOWN).stats.get("journeymen"), "a journeyman of the trade when grown");
		assertEquals(1, s.data(Lives.TOWN).stats.get("journeymen_gave_up"), "who gave it up after a season kept from it");
	}

	@Test
	void relationsStayBounded() {
		SimpleSettlement s = new SimpleSettlement(33, "Velka", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Places wp = new Places();
		for (int i = 0; i < 150; i++) {
			wp.add(200 + i / 6, JobType.FARMER, 6);
			person(s, "P" + i, i % 2 == 0, AgeStage.ADULT, JobType.FARMER, -60 - i % 80, i / 4, 200 + i / 6, i % 5 == 0 ? "sociable" : "shy");
		}
		for (long d = 0; d < 150; d++) {
			LivesDay.day(s, wp, d, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
		}
		int friends = 0;
		for (Resident r : s.residents()) {
			long[] a = Life.rel(r);
			int n = a == null ? 0 : a.length / 3;
			assertTrue(n <= Life.MAX_RELATIONS, "at most 8 relations: " + n);
			assertTrue(n * 24 <= 192, "relations within 192 bytes");
			assertTrue(written(r) <= 260, "a resident's lives within ~200 bytes saved, has " + written(r));
			for (Life.Relation rel : Life.relations(r, 150, LivesConfig.DEFAULTS.relations())) {
				if (rel.bond() == Life.Bond.FRIEND) {
					friends++;
				}
			}
		}
		assertTrue(friends > 50, "friendships formed: " + friends);
		assertTrue(s.data(Lives.TOWN).stats.get("sweethearts") > 0, "sweethearts among the single");
	}

	/** The bytes the resident's lives take on disk. */
	static int written(Resident r) {
		CompoundTag t = Life.peek(r);
		if (t == null) {
			return 0;
		}
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			net.minecraft.nbt.NbtIo.write(t, new java.io.DataOutputStream(out));
			return out.size();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void friendshipsFadeWithoutContact() {
		LivesConfig.Relations rc = LivesConfig.DEFAULTS.relations();
		SimpleSettlement s = new SimpleSettlement(34, "Ves", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Resident a = person(s, "A", true, AgeStage.ADULT, JobType.FARMER, -60, 0, 1);
		Resident b = person(s, "B", false, AgeStage.ADULT, JobType.FARMER, -60, 2, 1);
		Life.touch(a, b.id, 60, null, 0, rc);
		assertEquals(Life.Bond.FRIEND, Life.relation(a, b.id, 0, rc).bond());
		assertEquals(60, Life.relation(a, b.id, 0, rc).strength());
		assertTrue(Life.relation(a, b.id, 30, rc).strength() < 60, "faded after a month apart");
		assertNull(Life.relation(a, b.id, 200, rc), "forgotten after long apart");
	}

	@Test
	void feudsResolveOrPersist() {
		int resolved = 0;
		int persisted = 0;
		for (long seed = 1; seed <= 8; seed++) {
			SimpleSettlement s = new SimpleSettlement(40 + seed, "Hora", Culture.TEMPERATE, BlockPos.ZERO, -400);
			Places wp = new Places();
			List<Resident> heads = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				heads.add(person(s, "H" + i, i % 2 == 0, AgeStage.ADULT, JobType.FARMER, -100 - i, i, Resident.NO_PLOT, seed % 2 == 0 ? "grumpy" : "generous"));
			}
			TownLives town = s.data(Lives.TOWN);
			for (int i = 0; i < 6; i += 2) {
				assertNotNull(Feuds.start(s, town, heads.get(i), heads.get(i + 1), "boundary", 0, LivesConfig.DEFAULTS, (r, k) -> {
				}));
			}
			for (long d = 0; d < 120; d++) {
				LivesDay.day(s, wp, d, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
			}
			resolved += town.stats.get("feuds_ended");
			persisted += town.feuds.size();
		}
		assertTrue(resolved > 0, "some feuds made up: " + resolved);
		assertTrue(persisted > 0, "some feuds go on: " + persisted);
	}

	@Test
	void feudPassesToKin() {
		SimpleSettlement s = new SimpleSettlement(50, "Dol", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Resident a = person(s, "Old", false, AgeStage.ELDER, JobType.NONE, -400, 0, Resident.NO_PLOT);
		Resident son = person(s, "Son", false, AgeStage.ADULT, JobType.FARMER, -100, 0, Resident.NO_PLOT);
		parent(son, a);
		Resident b = person(s, "Other", true, AgeStage.ADULT, JobType.FARMER, -120, 1, Resident.NO_PLOT);
		TownLives town = s.data(Lives.TOWN);
		Feuds.start(s, town, a, b, "theft", 0, LivesConfig.DEFAULTS, (r, k) -> {
		});
		s.removeResident(a.id);
		LivesConfig quiet = new LivesConfig(LivesConfig.DEFAULTS.lives(), LivesConfig.DEFAULTS.crafts(), LivesConfig.DEFAULTS.relations(),
			new LivesConfig.Feuds(6, 0, 0, 0, 0, 0), LivesConfig.DEFAULTS.talk());
		LivesDay.day(s, new Places(), 1, quiet, LivesBook.builtin(), CHILDHOOD, RULES);
		assertEquals(1, town.feuds.size());
		assertTrue(town.feuds.get(0).involves(son.id), "the son carries it on");
	}

	@Test
	void sameSeedSameTown() {
		next = 0;
		SimpleSettlement a = craftTown(60, 10);
		next = 0;
		SimpleSettlement b = craftTown(60, 10);
		// both towns' residents have the same ids (the counter was reset), so the draws line up
		Places wp = places();
		for (long d = 0; d < 80; d++) {
			step(a, wp, d);
		}
		for (long d = 0; d < 80; d++) {
			step(b, wp, d);
		}
		assertEquals(save(a), save(b));
	}

	private static String save(SimpleSettlement s) {
		StringBuilder sb = new StringBuilder();
		List<Resident> rs = new ArrayList<>(s.residents());
		rs.sort((x, y) -> x.id.compareTo(y.id));
		for (Resident r : rs) {
			sb.append(r.id).append(r.extra.getCompoundOrEmpty(Life.KEY)).append('\n');
		}
		sb.append(TownLives.CODEC.encodeStart(NbtOps.INSTANCE, s.data(Lives.TOWN)).getOrThrow());
		return sb.toString();
	}

	@Test
	void namedCharactersAndThreadsMoveOn() {
		SimpleSettlement s = craftTown(70, 14);
		Places wp = places();
		// a town (60+) keeps five
		for (int i = 0; i < 50; i++) {
			person(s, "T" + i, i % 2 == 0, i % 7 == 0 ? AgeStage.ELDER : AgeStage.ADULT, JobType.FARMER, i % 7 == 0 ? -300 : -80 - i, 20 + i / 3,
				Resident.NO_PLOT, i % 4 == 0 ? "pious" : i % 4 == 1 ? "gossip" : "brave");
		}
		for (long d = 0; d < 120; d++) {
			step(s, wp, d);
		}
		TownLives town = s.data(Lives.TOWN);
		// a village keeps three, a town five (folk may have left with their stories, so the tier is read now)
		int want = Notables.target(s.tier(), s.population(), 6);
		assertTrue(want >= 3, "a village or more: " + s.population());
		assertEquals(want, town.notables.size(), "a " + s.tier() + " keeps " + want + " named characters");
		assertTrue(town.stats.get("beats") >= 5, "threads moved on by themselves: " + town.stats.get("beats"));
		java.util.Set<String> roles = new java.util.HashSet<>();
		for (TownLives.Notable n : town.notables) {
			assertTrue(roles.add(n.role), "one of each role: " + n.role);
		}
	}

	@Test
	void choiceIsOpenToTheFirstPlayer() {
		SimpleSettlement s = craftTown(80, 4);
		Resident smith = null;
		for (Resident r : s.residents()) {
			if (r.job == JobType.SMITH && smith == null) {
				smith = r;
			}
		}
		LivesDay.day(s, places(), 0, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
		TownLives.Notable n = LivesDev.make(s, smith, "drunk_smith");
		assertNotNull(n);
		assertTrue(LivesDev.beat(s, n, "debt"));
		LivesBook.Choice pay = LivesBook.builtin().thread("drunk_smith").beat("debt").choices().get(0);
		UUID first = new UUID(1, 1);
		List<Threads.PlayerEffect> got = Threads.choose(s, s.data(Lives.TOWN), n, pay, null, true, first, "First", 1);
		assertEquals("sworn_off", n.beat);
		assertEquals(first, n.by);
		assertTrue(got.stream().anyMatch(e -> e.type().equals("deed")), "the payer's deed");
		// the beat has moved on: the same choice is no longer there for a second player
		assertTrue(LivesView.beat(n).choices().isEmpty());
		// and the reward that follows by itself waits for the first player
		for (long d = 2; d < 12; d++) {
			LivesDay.day(s, places(), d, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
		}
		assertEquals("sober", n.beat);
		assertTrue(Threads.takePending(n, new UUID(2, 2)).isEmpty(), "not for another player");
		assertTrue(Threads.takePending(n, first).stream().anyMatch(e -> e.type().equals("gift") && e.item().equals("minecraft:iron_sword")),
			"the smith's blade for the one who paid");
	}

	@Test
	void costOfATownOf150() {
		SimpleSettlement s = new SimpleSettlement(90, "Mesto", Culture.TEMPERATE, BlockPos.ZERO, -400);
		Places wp = new Places();
		for (int i = 0; i < 25; i++) {
			wp.add(300 + i, JobType.values()[1 + i % 20], 4);
		}
		for (int i = 0; i < 150; i++) {
			person(s, "P" + i, i % 2 == 0, i < 30 ? AgeStage.CHILD : i > 140 ? AgeStage.ELDER : AgeStage.ADULT, JobType.FARMER, i < 30 ? -10 - i : -100 - i,
				i / 4, i < 30 ? Resident.NO_PLOT : 300 + i % 25, i % 3 == 0 ? "sociable" : "cheerful");
		}
		// warm up (relations fill their books), then measure
		for (long d = 0; d < 200; d++) {
			LivesDay.day(s, wp, d, LivesConfig.DEFAULTS, LivesBook.builtin(), CHILDHOOD, RULES);
		}
		LivesBook book = LivesBook.builtin();
		int days = 400;
		long t0 = System.nanoTime();
		for (long d = 200; d < 200 + days; d++) {
			LivesDay.day(s, wp, d, LivesConfig.DEFAULTS, book, CHILDHOOD, RULES);
		}
		double us = (System.nanoTime() - t0) / 1e3 / days;
		System.out.printf(java.util.Locale.ROOT, "[lives bench] %d people: %.1f us a sim day (budget 30)%n", s.population(), us);
		assertTrue(us < 300, "a town's lives day in well under a millisecond: " + us + " us");
	}
}
