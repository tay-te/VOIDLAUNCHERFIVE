package dev.voidmc.atlas.core.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides which stored world a newly joined level belongs to, so a server with several worlds behind one address (a
 * proxy network, a reset, lobbies and games) gets one map per world instead of one map with all of them drawn on top
 * of each other.
 *
 * <p>The strongest evidence is the terrain itself. As the first chunks arrive, each is {@linkplain #offer offered}
 * as a 16 x 16 grid of surface heights and compared with what each stored world has for that chunk. A chunk
 * <em>matches</em> if at least {@value #CHUNK_MATCH_PERCENT}% of the columns both know are within one block of each
 * other (players dig and build), and <em>differs</em> if under {@value #CHUNK_DIFFER_PERCENT}% are. Over at least
 * {@value #MIN_COMPARED} compared chunks, a world whose chunks match {@value #WORLD_MATCH_PERCENT}% of the time or more
 * is the same world; one at {@value #WORLD_DIFFER_PERCENT}% or less is a different one, and is never chosen, whatever
 * else agrees: proxies and minigame servers often reuse one seed for different maps.
 *
 * <p>Without terrain to compare (the player is somewhere they never mapped), the matcher waits for
 * {@value #ENOUGH_CHUNKS} chunks or the timeout, then falls back to the other signals: the seed hash the server sent
 * (0 when the server hides it) and the spawn point. The same seed hash and spawn is a match; the same seed hash alone
 * is a match but unsure; nothing to go on is a new world, unsure, with the most likely stored world named so the UI
 * can ask "keep separate, or same as before?".
 *
 * <p>Two featureless chunks (flat, at the same level: ocean, superflat) prove nothing and are not compared.
 *
 * <p>Pure and deterministic. Not thread-safe; use it from one thread (the stored heights are looked up during
 * {@link #offer}).
 */
public final class WorldMatcher {
	public static final int MIN_COMPARED = 3;
	public static final int ENOUGH_CHUNKS = 16;
	public static final int CHUNK_MATCH_PERCENT = 85;
	public static final int CHUNK_DIFFER_PERCENT = 50;
	public static final int WORLD_MATCH_PERCENT = 80;
	public static final int WORLD_DIFFER_PERCENT = 30;
	/** A chunk is compared only if at least this many of its 256 columns are known in both. */
	public static final int MIN_COLUMNS = 64;
	/** Heights that vary by at most this across a chunk make it flat. */
	public static final int FLAT_RANGE = 2;
	/** A column whose height is not known. {@code Short.MIN_VALUE} (the raw region's marker) is also accepted. */
	public static final int UNKNOWN = Integer.MIN_VALUE;

	/**
	 * What the server told us about the level just joined.
	 *
	 * @param seedHash  the hashed seed from the login/respawn packet; 0 when hidden
	 * @param dimension the dimension id, like {@code minecraft:overworld}
	 * @param facts     the dimension's shape, or null
	 * @param spawn     the spawn point, or null if not known (yet)
	 */
	public record Signals(long seedHash, String dimension, WorldRegistry.DimensionFacts facts, WorldRegistry.Spawn spawn) {
		public Signals {
			Objects.requireNonNull(dimension);
		}
	}

	/** One chunk's surface heights, x fastest ({@code heights[z * 16 + x]}); {@link #UNKNOWN} where not known. */
	public record Fingerprint(int chunkX, int chunkZ, int[] heights) {
		public Fingerprint {
			if (heights.length != 256) {
				throw new IllegalArgumentException("a chunk has 256 columns, not " + heights.length);
			}
			heights = heights.clone();
		}
	}

	/** The stored map's heights for a chunk, or null if that world never mapped it. */
	@FunctionalInterface
	public interface StoredHeights {
		int[] heights(String worldId, String dimension, int chunkX, int chunkZ);
	}

	public enum Verdict {
		/** Not enough to go on yet: offer more chunks, or ask again later. */
		WAIT,
		/** The level is the stored world {@link Decision#worldId()}. */
		MATCH,
		/** The level is a world not stored yet. */
		NEW
	}

	public enum Confidence {
		SURE,
		/** The UI should let the player say otherwise ("same as before" / "keep separate"). */
		UNSURE
	}

	/** How one stored world's terrain compares. */
	public enum Terrain {
		/** None of the chunks so far were mapped in that world (or all were featureless). */
		NO_OVERLAP,
		/** Some compared, not enough or not clear enough to say. */
		UNDECIDED,
		MATCH,
		DIFFERENT
	}

	/** Everything known about one stored world against the level joined. */
	public record Evidence(String worldId, int compared, int matched, int differed, Terrain terrain, boolean sameSeed, boolean otherSeed,
		boolean sameSpawn, boolean factsConflict, boolean knowsDimension, long lastUsed) {
		/** Ruled out for good: its terrain differs, or the dimension has another shape there. */
		public boolean ruledOut() {
			return this.terrain == Terrain.DIFFERENT || this.factsConflict;
		}

		double matchRatio() {
			return this.compared == 0 ? 0 : (double) this.matched / this.compared;
		}
	}

	/**
	 * The answer so far.
	 *
	 * @param worldId       the stored world, for {@link Verdict#MATCH}; null otherwise
	 * @param bestCandidate for {@link Verdict#NEW}: the stored world it most resembles, to offer "same as before"
	 *                      (null if there is none worth naming)
	 * @param reason        a plain-English line for the log
	 */
	public record Decision(Verdict verdict, Confidence confidence, String worldId, String bestCandidate, String reason) {
	}

	private enum ChunkResult {
		MATCH,
		MIXED,
		DIFFER
	}

	private static final Comparator<Evidence> LIKELIEST = Comparator
		.comparing(Evidence::sameSeed).reversed()
		.thenComparing(Comparator.comparing(Evidence::sameSpawn).reversed())
		.thenComparing(Comparator.comparingDouble(Evidence::matchRatio).reversed())
		.thenComparing(Comparator.comparing(Evidence::otherSeed))
		.thenComparing(Comparator.comparing(Evidence::knowsDimension).reversed())
		.thenComparing(Comparator.comparingLong(Evidence::lastUsed).reversed())
		.thenComparing(Evidence::worldId);

	private static final Comparator<Evidence> BEST_TERRAIN = Comparator
		.comparingDouble(Evidence::matchRatio).reversed()
		.thenComparing(Comparator.comparingInt(Evidence::compared).reversed())
		.thenComparing(Comparator.comparingLong(Evidence::lastUsed).reversed())
		.thenComparing(Evidence::worldId);

	private final Signals signals;
	private final List<WorldRegistry.World> candidates;
	private final StoredHeights stored;
	private final long startedAt;
	private final long timeoutMillis;
	/** Per candidate id, per chunk (packed), how that chunk compared; absent if it could not be compared. */
	private final Map<String, Map<Long, ChunkResult>> results = new LinkedHashMap<>();
	private final Map<Long, Boolean> offered = new HashMap<>();

	/**
	 * @param candidates    the worlds stored for this server
	 * @param startedAt     when the level was joined (any clock, the same as {@link #decision}'s)
	 * @param timeoutMillis how long to wait for terrain before deciding on the other signals
	 */
	public WorldMatcher(Signals signals, Collection<WorldRegistry.World> candidates, StoredHeights stored, long startedAt, long timeoutMillis) {
		this.signals = Objects.requireNonNull(signals);
		this.candidates = new ArrayList<>(candidates);
		this.candidates.sort(Comparator.comparing(WorldRegistry.World::id));
		this.stored = Objects.requireNonNull(stored);
		this.startedAt = startedAt;
		this.timeoutMillis = timeoutMillis;
		for (WorldRegistry.World w : this.candidates) {
			this.results.put(w.id(), new HashMap<>());
		}
	}

	/** A chunk of the joined level arrived. The same chunk offered again replaces the earlier one. */
	public void offer(Fingerprint chunk) {
		long key = (long) chunk.chunkX() << 32 | (chunk.chunkZ() & 0xFFFFFFFFL);
		this.offered.put(key, Boolean.TRUE);
		for (WorldRegistry.World w : this.candidates) {
			int[] old = this.stored.heights(w.id(), this.signals.dimension(), chunk.chunkX(), chunk.chunkZ());
			ChunkResult r = old == null || old.length != 256 ? null : compare(chunk.heights(), old);
			Map<Long, ChunkResult> mine = this.results.get(w.id());
			if (r == null) {
				mine.remove(key);
			} else {
				mine.put(key, r);
			}
		}
	}

	/** How many distinct chunks have been offered. */
	public int offered() {
		return this.offered.size();
	}

	/** The evidence for each stored world, by id. */
	public List<Evidence> evidence() {
		List<Evidence> out = new ArrayList<>();
		for (WorldRegistry.World w : this.candidates) {
			int compared = 0;
			int matched = 0;
			int differed = 0;
			for (ChunkResult r : this.results.get(w.id()).values()) {
				compared++;
				if (r == ChunkResult.MATCH) {
					matched++;
				} else if (r == ChunkResult.DIFFER) {
					differed++;
				}
			}
			Terrain terrain;
			if (compared == 0) {
				terrain = Terrain.NO_OVERLAP;
			} else if (compared < MIN_COMPARED) {
				terrain = Terrain.UNDECIDED;
			} else if (matched * 100 >= WORLD_MATCH_PERCENT * compared) {
				terrain = Terrain.MATCH;
			} else if (matched * 100 <= WORLD_DIFFER_PERCENT * compared) {
				terrain = Terrain.DIFFERENT;
			} else {
				terrain = Terrain.UNDECIDED;
			}
			long seed = this.signals.seedHash();
			boolean sameSeed = seed != 0 && w.seedHashes().contains(seed);
			boolean otherSeed = seed != 0 && !w.seedHashes().isEmpty() && !sameSeed;
			boolean sameSpawn = this.signals.spawn() != null && this.signals.spawn().equals(w.spawns().get(this.signals.dimension()));
			WorldRegistry.DimensionFacts known = w.facts().get(this.signals.dimension());
			boolean conflict = this.signals.facts() != null && known != null && !known.equals(this.signals.facts());
			boolean knows = w.dimensions().contains(this.signals.dimension());
			out.add(new Evidence(w.id(), compared, matched, differed, terrain, sameSeed, otherSeed, sameSpawn, conflict, knows, w.lastUsed()));
		}
		return out;
	}

	/** The decision as of {@code now} (same clock as the start time). */
	public Decision decision(long now) {
		if (this.candidates.isEmpty()) {
			return new Decision(Verdict.NEW, Confidence.SURE, null, null, "no world is stored for this server yet");
		}
		List<Evidence> all = this.evidence();
		List<Evidence> viable = all.stream().filter(e -> !e.ruledOut()).toList();

		List<Evidence> terrainMatches = viable.stream().filter(e -> e.terrain() == Terrain.MATCH).sorted(BEST_TERRAIN).toList();
		if (!terrainMatches.isEmpty()) {
			Evidence best = terrainMatches.get(0);
			return new Decision(Verdict.MATCH, Confidence.SURE, best.worldId(), null,
				"terrain matches " + best.worldId() + " in " + best.matched() + " of " + best.compared() + " chunks compared");
		}
		if (viable.isEmpty()) {
			return new Decision(Verdict.NEW, Confidence.SURE, null, null, "every stored world was ruled out (" + ruledOutWhy(all) + ")");
		}
		boolean timedOut = now - this.startedAt >= this.timeoutMillis;
		if (!timedOut && this.offered() < ENOUGH_CHUNKS) {
			return new Decision(Verdict.WAIT, Confidence.UNSURE, null, null,
				"waiting for terrain: " + this.offered() + " of " + ENOUGH_CHUNKS + " chunks");
		}
		String why = timedOut && this.offered() < ENOUGH_CHUNKS ? "timed out without enough terrain" : "no terrain to compare";

		List<Evidence> seedAndSpawn = viable.stream().filter(e -> e.sameSeed() && e.sameSpawn()).sorted(LIKELIEST).toList();
		if (!seedAndSpawn.isEmpty()) {
			return new Decision(Verdict.MATCH, Confidence.SURE, seedAndSpawn.get(0).worldId(), null, why + "; same seed hash and spawn");
		}
		List<Evidence> seedOnly = viable.stream().filter(Evidence::sameSeed).sorted(LIKELIEST).toList();
		if (!seedOnly.isEmpty()) {
			return new Decision(Verdict.MATCH, Confidence.UNSURE, seedOnly.get(0).worldId(), null, why + "; same seed hash, spawn unknown or moved");
		}
		List<Evidence> open = viable.stream().filter(e -> !e.otherSeed()).sorted(LIKELIEST).toList();
		if (open.isEmpty()) {
			Evidence best = viable.stream().sorted(LIKELIEST).findFirst().orElseThrow();
			return new Decision(Verdict.NEW, Confidence.SURE, null, best.worldId(), why + "; every stored world has another seed hash");
		}
		return new Decision(Verdict.NEW, Confidence.UNSURE, null, open.get(0).worldId(), why + "; nothing to tell it apart, closest is " + open.get(0).worldId());
	}

	private static String ruledOutWhy(List<Evidence> all) {
		StringBuilder out = new StringBuilder();
		for (Evidence e : all) {
			if (!out.isEmpty()) {
				out.append(", ");
			}
			out.append(e.worldId()).append(e.factsConflict() ? ": dimension has another shape" : ": terrain differs");
		}
		return out.toString();
	}

	/** How a fresh chunk compares with a stored one; null if it cannot say (too few known columns, or featureless). */
	private static ChunkResult compare(int[] fresh, int[] old) {
		int known = 0;
		int close = 0;
		int fMin = Integer.MAX_VALUE;
		int fMax = Integer.MIN_VALUE;
		int oMin = Integer.MAX_VALUE;
		int oMax = Integer.MIN_VALUE;
		long fSum = 0;
		long oSum = 0;
		for (int i = 0; i < 256; i++) {
			int f = fresh[i];
			int o = old[i];
			if (unknown(f) || unknown(o)) {
				continue;
			}
			known++;
			if (Math.abs((long) f - o) <= 1) {
				close++;
			}
			fMin = Math.min(fMin, f);
			fMax = Math.max(fMax, f);
			oMin = Math.min(oMin, o);
			oMax = Math.max(oMax, o);
			fSum += f;
			oSum += o;
		}
		if (known < MIN_COLUMNS) {
			return null;
		}
		boolean flat = fMax - fMin <= FLAT_RANGE && oMax - oMin <= FLAT_RANGE;
		if (flat && Math.abs(fSum - oSum) <= known) {
			return null;
		}
		if (close * 100 >= CHUNK_MATCH_PERCENT * known) {
			return ChunkResult.MATCH;
		}
		if (close * 100 < CHUNK_DIFFER_PERCENT * known) {
			return ChunkResult.DIFFER;
		}
		return ChunkResult.MIXED;
	}

	private static boolean unknown(int h) {
		return h == UNKNOWN || h == Short.MIN_VALUE;
	}
}
