package dev.voidmc.atlas.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.world.WorldMatcher.Confidence;
import dev.voidmc.atlas.core.world.WorldMatcher.Decision;
import dev.voidmc.atlas.core.world.WorldMatcher.Fingerprint;
import dev.voidmc.atlas.core.world.WorldMatcher.Signals;
import dev.voidmc.atlas.core.world.WorldMatcher.Terrain;
import dev.voidmc.atlas.core.world.WorldMatcher.Verdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/** Which stored world a joined level is: terrain first, then seed hash and spawn, never a world whose terrain differs. */
class WorldMatcherTest {
	static final String OVERWORLD = "minecraft:overworld";
	static final WorldRegistry.DimensionFacts OVERWORLD_FACTS = new WorldRegistry.DimensionFacts(false, -64, 384);
	static final WorldRegistry.Spawn SPAWN = new WorldRegistry.Spawn(0, 70, 0);
	static final long TIMEOUT = 5000;

	/** Rolling hills, different for every seed. */
	static IntBinaryOperator hills(long seed) {
		double a = (seed % 97) * 0.13;
		double b = (seed % 89) * 0.29;
		return (x, z) -> 70 + (int) Math.round(12 * Math.sin(x * 0.09 + a) + 9 * Math.cos(z * 0.07 + b) + 4 * Math.sin((x + z) * 0.31 + a * b));
	}

	static int[] chunk(IntBinaryOperator terrain, int cx, int cz) {
		int[] h = new int[256];
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				h[z * 16 + x] = terrain.applyAsInt(cx * 16 + x, cz * 16 + z);
			}
		}
		return h;
	}

	/** Stored maps: per world, per chunk, heights. */
	static final class Maps implements WorldMatcher.StoredHeights {
		final Map<String, Map<Long, int[]>> worlds = new HashMap<>();

		Maps map(String world, IntBinaryOperator terrain, int fromX, int toX, int fromZ, int toZ) {
			Map<Long, int[]> chunks = this.worlds.computeIfAbsent(world, k -> new HashMap<>());
			for (int cx = fromX; cx <= toX; cx++) {
				for (int cz = fromZ; cz <= toZ; cz++) {
					chunks.put(key(cx, cz), chunk(terrain, cx, cz));
				}
			}
			return this;
		}

		@Override
		public int[] heights(String worldId, String dimension, int chunkX, int chunkZ) {
			if (!dimension.equals(OVERWORLD)) {
				return null;
			}
			Map<Long, int[]> chunks = this.worlds.get(worldId);
			return chunks == null ? null : chunks.get(key(chunkX, chunkZ));
		}

		static long key(int x, int z) {
			return (long) x << 32 | (z & 0xFFFFFFFFL);
		}
	}

	static WorldRegistry.World world(String id, long seed, WorldRegistry.Spawn spawn, long lastUsed) {
		return new WorldRegistry.World(id, id, seed == 0 ? Set.of() : Set.of(seed), Set.of(OVERWORLD), Map.of(OVERWORLD, OVERWORLD_FACTS),
			spawn == null ? Map.of() : Map.of(OVERWORLD, spawn), 0, lastUsed);
	}

	static WorldMatcher matcher(long seed, WorldRegistry.Spawn spawn, Maps maps, WorldRegistry.World... worlds) {
		return new WorldMatcher(new Signals(seed, OVERWORLD, OVERWORLD_FACTS, spawn), List.of(worlds), maps, 0, TIMEOUT);
	}

	/** Offers the chunks of a square around (cx, cz), nearest first, up to {@code count}. */
	static void offer(WorldMatcher m, IntBinaryOperator terrain, int cx, int cz, int count) {
		List<int[]> order = new ArrayList<>();
		for (int r = 0; order.size() < count; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r && order.size() < count) {
						order.add(new int[] {cx + dx, cz + dz});
					}
				}
			}
		}
		for (int[] c : order) {
			m.offer(new Fingerprint(c[0], c[1], chunk(terrain, c[0], c[1])));
		}
	}

	@Test
	void noStoredWorldIsNewAndSure() {
		Decision d = matcher(42, SPAWN, new Maps()).decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.SURE, d.confidence());
	}

	@Test
	void sameWorldAgainMatchesOnTerrainAlone() {
		IntBinaryOperator t = hills(1);
		Maps maps = new Maps().map("a", t, -5, 5, -5, 5);
		WorldMatcher m = matcher(42, SPAWN, maps, world("a", 42, SPAWN, 10), world("b", 7, SPAWN, 20));
		offer(m, t, 0, 0, 2);
		assertEquals(Verdict.WAIT, m.decision(0).verdict(), "two chunks are not enough");
		offer(m, t, 0, 0, 3);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("a", d.worldId());
		assertEquals(Confidence.SURE, d.confidence());
	}

	@Test
	void differentWorldWithTheSameSeedIsNeverChosen() {
		// a proxy network: lobby and game share the seed hash and even the spawn, but not the terrain
		Maps maps = new Maps().map("lobby", hills(1), -5, 5, -5, 5);
		WorldMatcher m = matcher(42, SPAWN, maps, world("lobby", 42, SPAWN, 10));
		offer(m, hills(2), 0, 0, 3);
		Decision d = m.decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.SURE, d.confidence());
		assertEquals(Terrain.DIFFERENT, m.evidence().get(0).terrain());
		// and still after the timeout, when seed and spawn would otherwise decide
		offer(m, hills(2), 0, 0, 30);
		assertEquals(Verdict.NEW, m.decision(TIMEOUT * 2).verdict());
	}

	@Test
	void sameSeedTwoWorldsTerrainPicksTheRightOne() {
		Maps maps = new Maps().map("game1", hills(1), -5, 5, -5, 5).map("game2", hills(2), -5, 5, -5, 5);
		WorldMatcher m = matcher(42, SPAWN, maps, world("game1", 42, SPAWN, 99), world("game2", 42, SPAWN, 1));
		offer(m, hills(2), 0, 0, 9);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("game2", d.worldId());
	}

	@Test
	void hiddenSeedReturningWorldMatchesOnTerrain() {
		Maps maps = new Maps().map("a", hills(5), -5, 5, -5, 5);
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 10));
		offer(m, hills(5), 2, 2, 4);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("a", d.worldId());
	}

	@Test
	void hiddenSeedAndNoOverlapIsNewButUnsureNamingTheCandidate() {
		Maps maps = new Maps().map("a", hills(5), -5, 5, -5, 5).map("b", hills(6), 100, 105, 100, 105);
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 10), world("b", 0, null, 50));
		offer(m, hills(5), 1000, 1000, 10);
		assertEquals(Verdict.WAIT, m.decision(100).verdict());
		offer(m, hills(5), 1000, 1000, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(100);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence());
		assertEquals("b", d.bestCandidate(), "nothing else to go on: the most recently used world");
		assertNull(d.worldId());
	}

	@Test
	void hiddenSeedSameSpawnNamesThatWorld() {
		Maps maps = new Maps();
		WorldMatcher m = matcher(0, SPAWN, maps, world("a", 0, SPAWN, 10), world("b", 0, new WorldRegistry.Spawn(5, 5, 5), 50));
		offer(m, hills(5), 0, 0, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence());
		assertEquals("a", d.bestCandidate());
	}

	@Test
	void newAreaOfKnownWorldSameSeedAndSpawnMatches() {
		Maps maps = new Maps().map("a", hills(3), -5, 5, -5, 5);
		WorldMatcher m = matcher(42, SPAWN, maps, world("a", 42, SPAWN, 10), world("b", 7, SPAWN, 50));
		offer(m, hills(3), 500, 500, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("a", d.worldId());
		assertEquals(Confidence.SURE, d.confidence());
	}

	@Test
	void newAreaOfKnownWorldSameSeedOnlyMatchesUnsure() {
		Maps maps = new Maps().map("a", hills(3), -5, 5, -5, 5);
		WorldMatcher m = matcher(42, new WorldRegistry.Spawn(100, 64, 100), maps, world("a", 42, SPAWN, 10));
		offer(m, hills(3), 500, 500, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("a", d.worldId());
		assertEquals(Confidence.UNSURE, d.confidence());
	}

	@Test
	void playerModifiedTerrainStillMatches() {
		IntBinaryOperator original = hills(9);
		Maps maps = new Maps().map("a", original, -5, 5, -5, 5);
		// a tenth of every chunk dug out or built up, and one chunk levelled flat for a big build
		IntBinaryOperator changed = (x, z) -> {
			if (Math.floorDiv(x, 16) == 1 && Math.floorDiv(z, 16) == 0) {
				return 200;
			}
			int h = original.applyAsInt(x, z);
			return Math.floorMod(x * 7 + z * 13, 10) == 0 ? h + 6 : h;
		};
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 1));
		offer(m, changed, 0, 0, 5);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict(), m.evidence().toString());
		WorldMatcher.Evidence e = m.evidence().get(0);
		assertEquals(5, e.compared());
		assertEquals(4, e.matched());
		assertEquals(1, e.differed());
	}

	@Test
	void tooFewChunksThenTimeout() {
		Maps maps = new Maps().map("a", hills(3), -5, 5, -5, 5);
		WorldMatcher m = matcher(42, SPAWN, maps, world("a", 42, SPAWN, 10));
		offer(m, hills(3), 0, 0, 2); // overlapping, but only two
		assertEquals(Verdict.WAIT, m.decision(TIMEOUT - 1).verdict());
		assertEquals(Terrain.UNDECIDED, m.evidence().get(0).terrain());
		Decision d = m.decision(TIMEOUT);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals("a", d.worldId());
		assertEquals(Confidence.SURE, d.confidence(), "same seed and spawn");
		assertTrue(d.reason().contains("timed out"), d.reason());
	}

	@Test
	void timeoutWithNothingAtAllIsNewUnsure() {
		WorldMatcher m = matcher(0, null, new Maps(), world("a", 0, null, 10));
		assertEquals(Verdict.WAIT, m.decision(0).verdict());
		Decision d = m.decision(TIMEOUT);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence());
		assertEquals("a", d.bestCandidate());
	}

	@Test
	void anotherSeedEverywhereIsNewAndSure() {
		WorldMatcher m = matcher(42, SPAWN, new Maps(), world("a", 7, SPAWN, 10), world("b", 8, null, 20));
		offer(m, hills(1), 0, 0, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.SURE, d.confidence());
		assertEquals("a", d.bestCandidate(), "same spawn makes it the closest");
	}

	@Test
	void anotherSeedButSameTerrainStillMatches() {
		// servers that scramble the seed hash on every join: the terrain still knows
		Maps maps = new Maps().map("a", hills(4), -5, 5, -5, 5);
		WorldMatcher m = matcher(1234, SPAWN, maps, world("a", 42, SPAWN, 10));
		offer(m, hills(4), 0, 0, 3);
		assertEquals("a", m.decision(0).worldId());
	}

	@Test
	void featurelessChunksProveNothing() {
		IntBinaryOperator flat = (x, z) -> -61;
		Maps maps = new Maps().map("flat", flat, -5, 5, -5, 5);
		WorldMatcher m = matcher(0, null, maps, world("flat", 0, null, 10));
		offer(m, flat, 0, 0, WorldMatcher.ENOUGH_CHUNKS);
		assertEquals(Terrain.NO_OVERLAP, m.evidence().get(0).terrain());
		Decision d = m.decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence());
		assertEquals("flat", d.bestCandidate());
	}

	@Test
	void flatAtAnotherLevelDiffers() {
		Maps maps = new Maps().map("flat", (x, z) -> -61, -5, 5, -5, 5);
		WorldMatcher m = matcher(0, null, maps, world("flat", 0, null, 10));
		offer(m, (x, z) -> 63, 0, 0, 3);
		assertEquals(Terrain.DIFFERENT, m.evidence().get(0).terrain());
		assertEquals(Verdict.NEW, m.decision(0).verdict());
	}

	@Test
	void mixedTerrainIsUndecidedAndFallsBack() {
		IntBinaryOperator a = hills(11);
		IntBinaryOperator b = hills(12);
		Maps maps = new Maps().map("a", a, -5, 5, -5, 5);
		// half of the chunks match, half do not
		IntBinaryOperator half = (x, z) -> Math.floorDiv(x, 16) % 2 == 0 ? a.applyAsInt(x, z) : b.applyAsInt(x, z) + 30;
		WorldMatcher m = matcher(42, null, maps, world("a", 42, null, 10));
		offer(m, half, 0, 0, WorldMatcher.ENOUGH_CHUNKS);
		assertEquals(Terrain.UNDECIDED, m.evidence().get(0).terrain(), m.evidence().toString());
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence());
	}

	@Test
	void dimensionOfAnotherShapeIsRuledOut() {
		Maps maps = new Maps().map("a", hills(1), -5, 5, -5, 5);
		WorldMatcher m = new WorldMatcher(new Signals(42, OVERWORLD, new WorldRegistry.DimensionFacts(false, 0, 256), SPAWN),
			List.of(world("a", 42, SPAWN, 1)), maps, 0, TIMEOUT);
		offer(m, hills(1), 0, 0, 5);
		Decision d = m.decision(0);
		assertEquals(Verdict.NEW, d.verdict());
		assertEquals(Confidence.SURE, d.confidence());
	}

	@Test
	void columnsUnknownInTheStoredMapAreNotCompared() {
		IntBinaryOperator t = hills(8);
		Maps maps = new Maps().map("a", t, -5, 5, -5, 5);
		for (int[] h : maps.worlds.get("a").values()) {
			Arrays.fill(h, 0, 200, Short.MIN_VALUE); // only 56 columns known: too few to compare
		}
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 1));
		offer(m, t, 0, 0, 9);
		assertEquals(0, m.evidence().get(0).compared());
	}

	@Test
	void partlyUnknownChunksCompareOnTheKnownColumns() {
		IntBinaryOperator t = hills(8);
		Maps maps = new Maps().map("a", t, -5, 5, -5, 5);
		for (int[] h : maps.worlds.get("a").values()) {
			Arrays.fill(h, 0, 128, Short.MIN_VALUE);
		}
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 1));
		int[] fresh = chunk(t, 0, 0);
		Arrays.fill(fresh, 200, 256, WorldMatcher.UNKNOWN);
		m.offer(new Fingerprint(0, 0, fresh));
		offer(m, t, 0, 0, 3);
		assertEquals(3, m.evidence().get(0).compared());
		assertEquals(Verdict.MATCH, m.decision(0).verdict());
	}

	@Test
	void theSameChunkOfferedTwiceCountsOnce() {
		Maps maps = new Maps().map("a", hills(1), -5, 5, -5, 5);
		WorldMatcher m = matcher(0, null, maps, world("a", 0, null, 1));
		for (int i = 0; i < 5; i++) {
			m.offer(new Fingerprint(0, 0, chunk(hills(1), 0, 0)));
		}
		assertEquals(1, m.offered());
		assertEquals(1, m.evidence().get(0).compared());
		assertEquals(Verdict.WAIT, m.decision(0).verdict());
	}

	@Test
	void twoTerrainMatchesPreferTheCloserOne() {
		IntBinaryOperator t = hills(1);
		IntBinaryOperator edited = (x, z) -> Math.floorDiv(x, 16) == 0 && Math.floorDiv(z, 16) == 0 ? 250 : t.applyAsInt(x, z);
		// "copy" is an older copy of the same map with one chunk built over
		Maps maps = new Maps().map("orig", t, -5, 5, -5, 5).map("copy", edited, -5, 5, -5, 5);
		WorldMatcher m = matcher(0, null, maps, world("copy", 0, null, 99), world("orig", 0, null, 1));
		offer(m, t, 0, 0, 9);
		assertEquals("orig", m.decision(0).worldId());
	}

	@Test
	void otherDimensionsHaveNoStoredTerrain() {
		Maps maps = new Maps().map("a", hills(1), -5, 5, -5, 5);
		WorldMatcher m = new WorldMatcher(new Signals(42, "minecraft:the_nether", null, null), List.of(world("a", 42, SPAWN, 1)), maps, 0, TIMEOUT);
		offer(m, hills(1), 0, 0, WorldMatcher.ENOUGH_CHUNKS);
		Decision d = m.decision(0);
		assertEquals(Verdict.MATCH, d.verdict());
		assertEquals(Confidence.UNSURE, d.confidence(), "seed only: the Nether was never mapped and has no spawn");
	}

	@Test
	void deterministic() {
		Maps maps = new Maps().map("a", hills(1), -5, 5, -5, 5).map("b", hills(1), -5, 5, -5, 5);
		for (int run = 0; run < 3; run++) {
			WorldMatcher m = matcher(0, null, maps, world("b", 0, null, 5), world("a", 0, null, 5));
			offer(m, hills(1), 0, 0, 9);
			assertEquals("a", m.decision(0).worldId(), "equal evidence: the lower id, every time");
		}
	}

	@Test
	void fingerprintMustBeAChunk() {
		assertThrows(IllegalArgumentException.class, () -> new Fingerprint(0, 0, new int[255]));
		int[] h = new int[256];
		Fingerprint f = new Fingerprint(0, 0, h);
		h[0] = 99;
		assertEquals(0, f.heights()[0], "the fingerprint keeps its own copy");
	}
}
