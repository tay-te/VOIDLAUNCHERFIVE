package dev.voidmc.atlas.core.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.api.DiscoveryState;
import dev.voidmc.atlas.api.Place;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.core.journal.JournalEvent.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JournalTest {
	static final String OW = "minecraft:overworld";
	static final String NETHER = "minecraft:the_nether";

	static JournalEvent discover(Journal j, int i, long day) {
		return j.placeDiscovered("p" + i, "Place " + i, "minecraft:village", OW, i, 64, i, day, 1000L + i);
	}

	// ---------------------------------------------------------------- timeline

	@Test
	void timelineIsNewestFirstAndPaged() {
		Journal j = new Journal();
		for (int i = 0; i < 25; i++) {
			assertNotNull(discover(j, i, i / 10));
		}
		assertEquals(25, j.size());
		List<JournalEvent> first = j.page(0, 10);
		assertEquals(10, first.size());
		assertEquals("Discovered Place 24", first.get(0).text());
		assertEquals("Discovered Place 15", first.get(9).text());
		List<JournalEvent> second = j.page(10, 10);
		assertEquals("Discovered Place 14", second.get(0).text());
		List<JournalEvent> last = j.page(20, 10);
		assertEquals(5, last.size());
		assertEquals("Discovered Place 0", last.get(4).text());
		assertTrue(j.page(25, 10).isEmpty());
		assertTrue(j.page(100, 10).isEmpty());
		assertEquals(j.page(0, 3), j.newest(3));
		assertEquals(25, j.all().size());
		JournalEvent e = first.get(0);
		assertEquals(Kind.DISCOVERY, e.kind());
		assertEquals("p24", e.placeId());
		assertEquals("minecraft:village", e.subject());
		assertEquals(2, e.day());
		assertEquals(1024L, e.epoch());
		assertEquals(OW, e.dimension());
		assertEquals(24, e.x());
		assertEquals(64, e.y());
	}

	@Test
	void eachFirstIsToldOnce() {
		Journal j = new Journal();
		assertNotNull(discover(j, 1, 0));
		assertNull(discover(j, 1, 3), "a place is discovered once");
		assertNotNull(j.areaEntered("a1", "Larkspur Meadow", "minecraft:plains", OW, 0, 64, 0, 0, 1));
		assertNull(j.areaEntered("a1", "Larkspur Meadow", "minecraft:plains", OW, 5, 64, 5, 1, 2));
		assertEquals("Entered Larkspur Meadow", j.newest(1).get(0).text());
		assertEquals("Entered Dark forest", j.areaEntered("a2", "", "minecraft:dark_forest", OW, 0, 64, 0, 0, 3).text());
		assertEquals("Visited the Nether", j.dimensionVisited(NETHER, 0, 70, 0, 1, 4).text());
		assertNull(j.dimensionVisited(NETHER, 0, 70, 0, 1, 5));
		assertEquals(4, j.size());
	}

	@Test
	void placeRecordAndBlankName() {
		Journal j = new Journal();
		Place p = new Place("v1", "minecraft:village_plains", "", "VILLAGE", Tier.MAJOR, DiscoveryState.DISCOVERED, Source.seen(), 10, 70, 20, OW,
			false, 40, 1, 1);
		JournalEvent e = j.placeDiscovered(p, 4, 99);
		assertTrue(e.text().startsWith("Discovered "), e.text());
		assertFalse(e.text().equals("Discovered "), "a blank name falls back to the kind");
		assertEquals("v1", e.placeId());
		assertEquals(20, e.z());
	}

	// ---------------------------------------------------------------- capping

	@Test
	void pastTheCapTheOldestAreMergedIntoDailySummaries() {
		Journal j = new Journal(100);
		// 30 events on each of days 0..9
		int n = 0;
		for (int day = 0; day < 10; day++) {
			for (int k = 0; k < 30; k++) {
				discover(j, n++, day);
			}
		}
		assertTrue(j.size() <= 100, "size " + j.size());
		assertEquals(300, j.eventCount(), "summaries keep the count of what they merged");
		List<JournalEvent> all = j.all();
		// the newest events are untouched
		assertEquals("Discovered Place 299", all.get(0).text());
		assertFalse(all.get(0).isSummary());
		// summaries are the oldest lines, one per day, in day order
		List<JournalEvent> summaries = all.stream().filter(JournalEvent::isSummary).toList().reversed();
		assertFalse(summaries.isEmpty());
		Set<Long> days = new HashSet<>();
		for (int i = 0; i < summaries.size(); i++) {
			JournalEvent s = summaries.get(i);
			assertEquals(s.day(), s.lastDay(), "one day per summary while there are plain events to merge");
			assertTrue(days.add(s.day()), "one summary per day");
			if (i > 0) {
				assertTrue(s.day() > summaries.get(i - 1).day());
			}
		}
		JournalEvent day0 = summaries.get(0);
		assertEquals(0, day0.day());
		assertEquals(30, day0.count(Kind.DISCOVERY));
		assertEquals("Day 0: 30 discoveries", day0.text());
		assertNull(day0.dimension());
		// every line after the summaries is a plain event
		int firstPlain = all.size() - summaries.size();
		for (int i = 0; i < firstPlain; i++) {
			assertFalse(all.get(i).isSummary());
		}
	}

	@Test
	void summariesTallyEachKind() {
		Journal j = new Journal(10);
		j.sample(OW, 0, 64, 0, 0, 0);
		j.areaEntered("a", "Meadow", "minecraft:plains", OW, 0, 64, 0, 0, 1);
		discover(j, 1, 0);
		discover(j, 2, 0);
		for (int i = 3; i < 20; i++) {
			discover(j, i, 1);
		}
		JournalEvent oldest = j.page(j.size() - 1, 1).get(0);
		assertTrue(oldest.isSummary());
		assertEquals(0, oldest.day());
		assertEquals(1, oldest.count(Kind.DIMENSION));
		assertEquals(1, oldest.count(Kind.AREA));
		assertEquals(2, oldest.count(Kind.DISCOVERY));
		assertEquals("Day 0: 2 discoveries, 1 new area, 1 new dimension", oldest.text());
		assertEquals(21, j.eventCount());
	}

	@Test
	void summariesOfSummariesSpanDays() {
		Journal j = new Journal(4);
		for (int i = 0; i < 40; i++) {
			discover(j, i, i); // a day each: nothing to merge within a day
		}
		assertTrue(j.size() <= 4);
		assertEquals(40, j.eventCount());
		JournalEvent oldest = j.page(j.size() - 1, 1).get(0);
		assertTrue(oldest.isSummary());
		assertEquals(0, oldest.day());
		assertTrue(oldest.lastDay() > oldest.day());
		assertTrue(oldest.text().startsWith("Days 0-"), oldest.text());
		assertEquals("Discovered Place 39", j.newest(1).get(0).text());
	}

	// ---------------------------------------------------------------- distance

	@Test
	void distanceIgnoresTeleportsAndJitter() {
		Journal j = new Journal();
		j.sample(OW, 0, 64, 0, 0, 0);
		j.sample(OW, 3, 64, 4, 0, 1); // 5 blocks
		j.sample(OW, 3.2, 64, 4.2, 0, 2); // jitter, under half a block
		j.sample(OW, 3.1, 64, 3.9, 0, 3); // jitter around the same spot
		assertEquals(5.0, j.stats().distance(), 1e-9);
		j.sample(OW, 503, 64, 4, 0, 4); // teleport
		assertEquals(5.0, j.stats().distance(), 1e-9);
		j.sample(OW, 503, 80, 14, 0, 5); // 10 blocks from where it landed; Y is not distance
		assertEquals(15.0, j.stats().distance(), 1e-9);
		j.sample(OW, 503, 80, 78, 0, 6); // exactly 64: still travel
		assertEquals(79.0, j.stats().distance(), 1e-9);
		j.sample(OW, 503, 80, 142.5, 0, 7); // over 64
		assertEquals(79.0, j.stats().distance(), 1e-9);
	}

	@Test
	void slowWalkingStillAdds() {
		Journal j = new Journal();
		for (int i = 0; i <= 10; i++) {
			j.sample(OW, i * 0.3, 64, 0, 0, i); // sneaking: 0.3 a sample
		}
		double d = j.stats().distance();
		assertTrue(d >= 2.4 && d <= 3.0 + 1e-9, "sub-jitter steps add up once they pass half a block: " + d);
	}

	@Test
	void distancePerDimensionAndMilestones() {
		Journal j = new Journal();
		List<JournalEvent> made = new ArrayList<>();
		for (int i = 0; i <= 260; i++) {
			made.addAll(j.sample(OW, i * 5.0, 64, 0, 0, i)); // 1300 blocks
		}
		j.sample(NETHER, 0, 40, 0, 1, 1000); // a new dimension: no distance for the jump
		for (int i = 1; i <= 160; i++) {
			made.addAll(j.sample(NETHER, 0, 40, i * 5.0, 1, 1000 + i)); // 800 blocks
		}
		JournalStats s = j.stats();
		assertEquals(1300.0, s.distanceByDimension().get(OW), 1e-6);
		assertEquals(800.0, s.distanceByDimension().get(NETHER), 1e-6);
		assertEquals(2100.0, s.distance(), 1e-6);
		assertEquals(2.1, s.kilometres(), 1e-9);
		List<String> km = made.stream().filter(e -> e.kind() == Kind.DISTANCE).map(JournalEvent::text).toList();
		assertEquals(List.of("Travelled 1 km", "Travelled 2 km"), km);
		assertEquals(List.of(OW, NETHER), s.dimensions());
		assertEquals("Visited the Overworld", made.get(0).text());
	}

	// ---------------------------------------------------------------- heights

	@Test
	void deepestYIsAnEventOnlyOnANewRecord() {
		Journal j = new Journal();
		assertEquals(List.of(Kind.DIMENSION), kinds(j.sample(OW, 0, 64, 0, 0, 0)), "the first position sets the baseline");
		assertTrue(j.sample(OW, 1, 70, 0, 0, 1).isEmpty(), "higher is no depth record");
		List<JournalEvent> down = j.sample(OW, 2, 50.7, 0, 0, 2);
		assertEquals(1, down.size());
		assertEquals("Reached Y 50", down.get(0).text());
		assertTrue(j.sample(OW, 3, 50.2, 0, 0, 3).isEmpty(), "the same block is no new record");
		assertTrue(j.sample(OW, 4, 60, 0, 0, 4).isEmpty());
		discover(j, 1, 0); // something else in between
		List<JournalEvent> deeper = j.sample(OW, 5, -54, 0, 0, 5);
		assertEquals("Reached Y -54", deeper.get(0).text());
		assertTrue(j.sample(OW, 6, -20, 0, 0, 6).isEmpty());
		JournalStats s = j.stats();
		assertEquals(-54, s.deepestY());
		assertEquals(70, s.highestY());
		assertEquals(-54, s.deepestByDimension().get(OW));
		assertEquals(2, j.all().stream().filter(e -> e.kind() == Kind.DEPTH).count());
	}

	@Test
	void diggingDownMovesOneRecordInsteadOfAddingMany() {
		Journal j = new Journal();
		j.sample(OW, 0, 64, 0, 0, 0);
		for (int y = 63; y >= 10; y--) {
			assertEquals(1, j.sample(OW, 0, y, 0, 0, 64 - y).size());
		}
		List<JournalEvent> depth = j.all().stream().filter(e -> e.kind() == Kind.DEPTH).toList();
		assertEquals(1, depth.size());
		assertEquals("Reached Y 10", depth.get(0).text());
	}

	@Test
	void heightsArePerDimension() {
		Journal j = new Journal();
		j.sample(OW, 0, -40, 0, 0, 0);
		assertTrue(kinds(j.sample(NETHER, 0, 100, 0, 0, 1)).equals(List.of(Kind.DIMENSION)), "the nether's first position is its baseline");
		assertEquals(1, j.sample(NETHER, 0, 30, 0, 0, 2).size(), "a nether record though the overworld is deeper");
		JournalStats s = j.stats();
		assertEquals(-40, s.deepestY());
		assertEquals(100, s.highestY());
		assertEquals(30, s.deepestByDimension().get(NETHER));
	}

	static List<Kind> kinds(List<JournalEvent> events) {
		return events.stream().map(JournalEvent::kind).toList();
	}

	// ---------------------------------------------------------------- stats

	@Test
	void exploredShareAndTopAreas() {
		Journal j = new Journal();
		j.areaProgress("a", "Larkspur Meadow", "minecraft:plains", 200, 50, 1, 10);
		j.areaProgress("b", "Greywood", "minecraft:forest", 100, 80, 1, 11);
		j.areaProgress("c", "Small Pond", "minecraft:river", 10, 10, 1, 12);
		j.areaProgress("d", "Unseen", "minecraft:desert", 0, 0, 1, 13);
		AreaStat a = j.areaProgress("a", null, null, 200, 120, 1, 20); // later the same day: replaces the day's point
		assertEquals(0.6, a.share(), 1e-9);
		assertEquals(60, a.percent());
		assertEquals("Larkspur Meadow", a.name(), "a null name keeps the last one");
		assertEquals(1, a.history().size());
		a = j.areaProgress("a", null, null, 210, 150, 2, 30);
		assertEquals(2, a.history().size());
		assertEquals(120, a.history().get(0).explored());
		assertEquals(150, a.history().get(1).explored());
		assertEquals(210, a.total());
		assertEquals(71, a.percent());
		assertEquals(2, j.areaProgress("a", null, null, 210, 150, 3, 40).history().size(), "no change, no new point");
		assertEquals(10, j.areaProgress("c", null, null, 10, 99, 1, 14).explored(), "explored never exceeds the total");

		List<AreaStat> top = j.topAreas(10);
		assertEquals(List.of("c", "b", "a"), top.stream().map(AreaStat::id).toList(), "an empty area is left out");
		assertEquals(100, top.get(0).percent());
		assertEquals(List.of("c", "b"), j.topAreas(2).stream().map(AreaStat::id).toList());
		assertEquals(4, j.areas().size());
		assertEquals("minecraft:forest", j.area("b").biome());
		assertNull(j.area("zz"));
		assertEquals(3, j.stats().topAreas().size());
	}

	@Test
	void historyIsCapped() {
		Journal j = new Journal();
		for (int day = 0; day < Journal.HISTORY_KEPT + 40; day++) {
			j.areaProgress("a", "A", "minecraft:plains", 1000, day, day, day);
		}
		AreaStat a = j.area("a");
		assertEquals(Journal.HISTORY_KEPT, a.history().size());
		assertEquals(40, a.history().get(0).day());
	}

	@Test
	void placeCountsDaysAndAreas() {
		Journal j = new Journal();
		Place v1 = new Place("1", "minecraft:village", "V", "VILLAGE", Tier.MAJOR, DiscoveryState.DISCOVERED, Source.seen(), 0, 0, 0, OW, false, 0, 1, 1);
		Place v2 = new Place("2", "minecraft:village", "V", "VILLAGE", Tier.MAJOR, DiscoveryState.SIGHTED, Source.seen(), 0, 0, 0, OW, false, 0, 1, 0);
		Place v3 = new Place("3", "minecraft:village", "V", "VILLAGE", Tier.MAJOR, DiscoveryState.DISCOVERED, Source.seen(), 0, 0, 0, OW, false, 0, 1, 1);
		Place peak = new Place("4", Place.PEAK, "P", "NATURAL", Tier.MAJOR, DiscoveryState.DISCOVERED, Source.seen(), 0, 0, 0, OW, false, 0, 1, 1);
		j.countPlaces(List.of(v1, v2, v3, peak));
		JournalStats s = j.stats();
		assertEquals(2, s.places("minecraft:village", DiscoveryState.DISCOVERED));
		assertEquals(1, s.places("minecraft:village", DiscoveryState.SIGHTED));
		assertEquals(0, s.places("minecraft:village", DiscoveryState.UNKNOWN));
		assertEquals(3, s.places(DiscoveryState.DISCOVERED));
		j.setPlaceCounts(Map.of(Place.LAKE, Map.of(DiscoveryState.SIGHTED, 4)));
		assertEquals(0, j.stats().places(DiscoveryState.DISCOVERED), "the counts are replaced, not added");
		assertEquals(4, j.stats().places(Place.LAKE, DiscoveryState.SIGHTED));

		assertEquals(0, j.stats().daysPlayed());
		assertEquals(-1, j.stats().firstDay());
		j.sample(OW, 0, 64, 0, 3, 0);
		j.sample(OW, 0, 64, 0, 9, 0);
		j.areaEntered("x", "X", "minecraft:plains", OW, 0, 0, 0, 5, 0);
		s = j.stats();
		assertEquals(3, s.firstDay());
		assertEquals(9, s.lastDay());
		assertEquals(7, s.daysPlayed());
		assertEquals(1, s.areasEntered());
	}

	@Test
	void versionAndDirty() {
		Journal j = new Journal();
		assertFalse(j.dirty());
		long v = j.version();
		discover(j, 1, 0);
		assertTrue(j.version() > v);
		assertTrue(j.dirty());
		v = j.version();
		assertNull(discover(j, 1, 0));
		assertEquals(v, j.version(), "nothing new, no change");
	}

	// ---------------------------------------------------------------- concurrency

	@Test
	void manyThreadsAppending() throws Exception {
		int threads = 16;
		int each = 1000;
		Journal j = new Journal(5000);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> done = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			int thread = t;
			done.add(pool.submit(() -> {
				start.await();
				for (int i = 0; i < each; i++) {
					int n = thread * each + i;
					if (j.placeDiscovered("p" + n, "P" + n, "minecraft:village", OW, n, 64, 0, n / 2000, n) == null) {
						throw new AssertionError("a new place was refused");
					}
					// a place told again is refused
					if (j.placeDiscovered("p" + (thread * each + i / 2), "P", "minecraft:village", OW, 0, 64, 0, 0, 0) != null) {
						throw new AssertionError("a place was told twice");
					}
					if (i % 50 == 0) {
						j.page(0, 20);
						j.stats();
						j.areaProgress("a" + thread, "A", "minecraft:plains", each, i, i, i);
					}
				}
				return null;
			}));
		}
		start.countDown();
		for (Future<?> f : done) {
			f.get(60, TimeUnit.SECONDS);
		}
		pool.shutdown();
		assertTrue(j.size() <= 5000);
		assertEquals(threads * each, j.eventCount(), "every append is in the timeline, plain or summarised");
		assertEquals(threads * each, j.all().stream().mapToInt(e -> e.count(Kind.DISCOVERY)).sum());
		assertEquals(threads, j.areas().size());
		Set<String> ids = new HashSet<>();
		for (JournalEvent e : j.all()) {
			if (!e.isSummary()) {
				assertTrue(ids.add(e.placeId()), "no place twice: " + e.placeId());
			}
		}
	}

	@Test
	void manyThreadsWithoutCapKeepEveryEvent() throws Exception {
		Journal j = new Journal(100_000);
		Thread[] threads = new Thread[8];
		for (int t = 0; t < threads.length; t++) {
			int thread = t;
			threads[t] = new Thread(() -> {
				for (int i = 0; i < 2000; i++) {
					j.placeDiscovered(thread + ":" + i, "P", "minecraft:village", OW, 0, 0, 0, 0, 0);
				}
			});
			threads[t].start();
		}
		for (Thread t : threads) {
			t.join();
		}
		assertEquals(16000, j.size());
		assertEquals(16000, j.all().stream().map(JournalEvent::placeId).distinct().count());
	}
}
