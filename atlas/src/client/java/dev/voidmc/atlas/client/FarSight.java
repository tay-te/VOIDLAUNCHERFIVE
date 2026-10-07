package dev.voidmc.atlas.client;

import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.FarColour;
import dev.voidmc.atlas.core.NamePalette;
import dev.voidmc.atlas.core.RegionRaw;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrain seen from afar: what VOID LOD drew on the player's screen beyond the loaded chunks, mapped as its own layer
 * ({@value #LAYER}), in less detail, under the walked land and never mixed into it. Optional both ways: with no LOD
 * (mod id {@value #LOD_MOD}) nothing here runs, and Atlas has no compile dependency on it (LOD's API is reached by
 * reflection, its listener is a proxy).
 *
 * <p>LOD's API ({@code dev.voidmc.lod.api}, VOID LOD 16d57b36): {@code LodApi.addVisibleTerrainListener(listener)}
 * returns an {@code AutoCloseable}. LOD captures far terrain only while someone listens, and sends each new listener
 * every tile drawn so far, then each tile as it is drawn, on its "VOID LOD API" thread, as {@code VisibleTerrain(
 * dimension, revision, minX, minZ, cell, size, heights, colors, blockPalette, blocks, biomePalette, biomes, seen)} (64 x
 * 64 cells, row major, x fastest; {@code cell} blocks a cell; -1 an unknown block or biome; fresh arrays; the higher
 * revision wins for the same ground). Atlas listens from start-up (so capture runs from the first tile), and listens
 * anew when the map or the dimension changes (so the tiles drawn so far come again). It takes a tile in microseconds on
 * LOD's thread (checks it, files it by place, keeping the newer revision) and writes it on its own workers, oldest
 * revision first, within a time budget a run; the client thread never does any of it.
 *
 * <p>Fair play: only what LOD drew for this player, only in the dimension the player is in, only while the map and the
 * server allow it; only cells coarser than the walked map (never a 1-block cell), never over a walked column, and never
 * a discovery: the far layer is drawn under the walked land and nothing reads places, areas or the journal from it.
 * Cells LOD made from the generator ({@code seen = false}: singleplayer or LAN) are "seen from afar" like the rest.
 */
public final class FarSight {
	public static final String LOD_MOD = "void_lod";
	/**
	 * Far sight's own writer: one thread, so a burst of tiles is written run after run instead of each run
	 * waiting behind the workers' chunk and image jobs (and never holds a worker the map needs).
	 */
	private static final java.util.concurrent.ScheduledExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Atlas far sight");
		t.setDaemon(true);
		// the workers' priority: below it, a busy machine starves it and a burst never ends
		return t;
	});
	public static final String LAYER = "far";
	/**
	 * The coarse far store: VOID LOD's 32- to 512-block cells, kept apart from walked land and the fine far layer, one
	 * sample per {@value #COARSE} blocks (a page, 512 samples a side, covers 16,384 blocks). Drawn under both, so the
	 * finer land shows wherever it is known: walked, then fine far, then coarse far.
	 */
	public static final String COARSE_LAYER = "farc";
	/** Blocks a coarse sample stands for. */
	public static final int COARSE = 32;
	/** The coarsest cell taken into the coarse store. */
	public static final int MAX_COARSE_CELL = 512;
	public static final AtomicLong COARSE_TILES = new AtomicLong();
	public static final AtomicLong COARSE_NANOS = new AtomicLong();
	public static final AtomicLong COARSE_SAMPLES = new AtomicLong();
	/**
	 * The finest cell taken: 1 block, the ring VOID LOD draws right past the loaded chunks (walked land is never written
	 * over, so it only fills what the player has not walked).
	 */
	public static final int MIN_CELL = 1;
	/**
	 * The coarsest cell taken: 16 blocks (a 64-cell tile covers 1,024 blocks a side, a million columns). Far land is kept
	 * a column a block like walked land, so VOID LOD's 32- and 64-block cells at its horizon (4,096-block tiles, 64
	 * regions each) would cost more to write and keep than they show; they are left out.
	 */
	public static final int MAX_CELL = 16;
	public static final int MAX_SIZE = 256;
	/** How long one worker run may write far tiles before it gives the worker back (and runs again later). */
	public static final long RUN_BUDGET_NANOS = 4_000_000L;
	/** Tiles waiting to be written, at most; past it the oldest waiting is dropped (VOID LOD hands each over once when idle: keep room). */
	public static final int MAX_PENDING = 4096;
	/** The far layer's raw regions kept in memory, at most (each is about 2.4 MB); the rest are saved and let go. */
	public static final int MAX_RAWS = 48;
	/** Tiles offered by cell size (index: log2 of the cell), for the tour. */
	public static final java.util.concurrent.atomic.AtomicLongArray OFFERED_BY_CELL = new java.util.concurrent.atomic.AtomicLongArray(11);
	/** Far regions in memory at which writing waits for the disk to catch up. */
	public static final int HARD_RAWS = MAX_RAWS * 2;

	/** For the perf log: tiles taken and written, columns written, worker time, and time on LOD's thread. */
	public static final AtomicLong TAKEN = new AtomicLong();
	public static final AtomicLong TILES = new AtomicLong();
	public static final AtomicLong SAMPLES = new AtomicLong();
	public static final AtomicLong WORKER_NANOS = new AtomicLong();
	public static final AtomicLong LISTENER_NANOS = new AtomicLong();
	public static final AtomicLong DROPPED = new AtomicLong();
	/** The longest worker run (one run writes tiles until {@link #RUN_BUDGET_NANOS} is spent). */
	public static final AtomicLong MAX_RUN_NANOS = new AtomicLong();

	/** One far tile, as Atlas keeps it until written: block and biome names per cell (null unknown). */
	public record Tile(String dimension, long revision, int minX, int minZ, int cell, int size, short[] heights, int[] colours, String[] blocks,
		String[] biomes, boolean[] seen) {
		long key() {
			return (long) this.minX * 0x9E3779B1L ^ (long) this.minZ * 0x85EBCA77L ^ this.cell;
		}
	}

	private final AtlasClient atlas;
	private final Map<Long, Tile> pending = new ConcurrentHashMap<>();

	/** Tiles taken and not yet written (the tour waits for none). */
	public int pendingCount() {
		return this.pending.size();
	}
	private final AtomicBoolean draining = new AtomicBoolean();
	private final AtomicBoolean trimming = new AtomicBoolean();
	private volatile boolean on;
	private AutoCloseable subscription;
	private String subscribedFor = "";
	private boolean lodMissing;

	public FarSight(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/**
	 * Whether VOID LOD is installed (or the tour says to act as if: {@code -Datlas.test.assumeLod=true}, for its far
	 * sight shots, fed made-up far tiles).
	 */
	/**
	 * Whether VOID LOD has built, drawn and handed over every tile its view wants, with nothing queued
	 * ({@code LodApi.complete()}, VOID LOD 003b81f5 on); null when this LOD has no such call. Turns false again as the
	 * player moves into new land.
	 */
	public static @org.jspecify.annotations.Nullable Boolean lodComplete() {
		Object v = Lod.call("complete");
		return v instanceof Boolean b ? b : null;
	}

	/** How far VOID LOD draws the far land, in blocks (0 while it is not drawing, or with no such call). */
	public static double lodRadius() {
		Object v = Lod.call("radius");
		return v instanceof Number n ? n.doubleValue() : 0;
	}

	public static boolean lodPresent() {
		return FabricLoader.getInstance().isModLoaded(LOD_MOD) || Boolean.getBoolean("atlas.test.assumeLod");
	}

	/** At start-up: listens to LOD at once, so it captures far terrain from the first tile it draws. */
	public void register() {
		this.lodMissing = !FabricLoader.getInstance().isModLoaded(LOD_MOD);
		if (!this.lodMissing && this.atlas.settings().farSight) {
			this.subscription = Lod.listen(this::take);
			this.lodMissing = this.subscription == null;
		}
	}

	/**
	 * Client thread, about once a second: listens anew (so LOD sends what it has drawn so far once more) when the map or
	 * the dimension changes, and stops listening while the setting is off.
	 */
	public void update() {
		this.on = this.enabled();
		Minecraft mc = Minecraft.getInstance();
		MapSession session = this.atlas.session();
		String want = this.on && session != null && mc.level != null ? session.worldKey + "|" + mc.level.dimension().identifier() : "";
		if (want.equals(this.subscribedFor) || this.lodMissing) {
			return;
		}
		this.pending.clear();
		if (!this.atlas.settings().farSight) {
			// off: LOD need not capture anything for Atlas
			this.close();
		} else if (!want.isEmpty()) {
			// the new listener first, so capture never stops; then the old one goes
			AutoCloseable old = this.subscription;
			this.subscription = Lod.listen(this::take);
			closeQuietly(old);
			this.lodMissing = this.subscription == null;
		}
		this.subscribedFor = want;
	}

	/** Stops listening. */
	public void close() {
		AutoCloseable s = this.subscription;
		this.subscription = null;
		this.subscribedFor = "";
		closeQuietly(s);
	}

	private static void closeQuietly(AutoCloseable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception e) {
				// LOD is going away too
			}
		}
	}

	/** Whether LOD is there and listened to (for the settings row and the perf log). */
	public boolean listening() {
		return this.subscription != null;
	}

	private boolean enabled() {
		return this.atlas.settings().farSight && this.atlas.session() != null && !this.atlas.worlds.finding()
			&& (!this.atlas.multiplayer() || this.atlas.rules().minimapAllowed());
	}

	/** A far tile from LOD (its thread): checked, filed, and written later on a worker. Never blocks. */
	void take(Object visibleTerrain) {
		long t0 = System.nanoTime();
		try {
			// what VOID LOD hands over, by cell size, before anything is filtered (the tour's raw account)
			int rawCell = Lod.cell(visibleTerrain);
			if (rawCell > 0) {
				OFFERED_BY_CELL.incrementAndGet(Math.min(10, 31 - Integer.numberOfLeadingZeros(rawCell)));
			}
			if (this.on) {
				Tile tile = Lod.tile(visibleTerrain);
				if (tile != null) {
					this.offer(tile);
				}
			}
		} catch (RuntimeException | LinkageError e) {
			// a tile that does not keep the contract is dropped
		} finally {
			LISTENER_NANOS.addAndGet(System.nanoTime() - t0);
		}
	}

	/** Files a far tile to be written (any thread). Public for the perf scenario, which feeds made-up tiles. */
	public void offer(Tile tile) {
		boolean coarse = tile.cell() >= COARSE && tile.cell() <= MAX_COARSE_CELL;
		if (!this.on || tile.cell() < MIN_CELL || tile.cell() > MAX_CELL && !coarse || tile.size() <= 0 || tile.size() > MAX_SIZE) {
			return;
		}
		int n = tile.size() * tile.size();
		if (tile.heights().length < n || tile.colours().length < n || tile.blocks() != null && tile.blocks().length < n
			|| tile.biomes() != null && tile.biomes().length < n || tile.seen() != null && tile.seen().length < n) {
			return;
		}
		var level = Minecraft.getInstance().level;
		if (level == null || !tile.dimension().equals(level.dimension().identifier().toString())) {
			return;
		}
		MapSession session = this.atlas.session();
		if (session == null) {
			return;
		}
		TAKEN.incrementAndGet();
		if (this.pending.size() >= MAX_PENDING) {
			// LOD sends a tile again whenever it draws it again
			var it = this.pending.keySet().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
				DROPPED.incrementAndGet();
			}
		}
		// the same ground drawn again: the higher revision wins
		this.pending.merge(tile.key(), tile, (a, b) -> b.revision() >= a.revision() ? b : a);
		this.drainLater(session);
	}

	private void drainLater(MapSession session) {
		if (this.draining.compareAndSet(false, true)) {
			try {
				WRITER.execute(() -> this.drain(session));
			} catch (RuntimeException e) {
				this.draining.set(false);
			}
		}
	}

	/** Writes waiting tiles, oldest revision first, until the run's budget is spent; then runs again later (worker). */
	private void drain(MapSession session) {
		if (PerfGovernor.paused()) {
			// Light: far sight waits (LOD's tiles stay filed, the newest of each), and looks again in a second
			this.draining.set(false);
			try {
				WRITER.schedule(() -> this.drainLater(session), 1, java.util.concurrent.TimeUnit.SECONDS);
			} catch (RuntimeException e) {
				// closing
			}
			return;
		}
		long t0 = System.nanoTime();
		boolean backlogged = false;
		try {
			List<Tile> order = new ArrayList<>(this.pending.values());
			// where tiles overlap (a coarser and a finer one), the newer is written last
			order.sort(Comparator.comparingLong(Tile::revision));
			for (Tile t : order) {
				if (System.nanoTime() - t0 >= RUN_BUDGET_NANOS) {
					break;
				}
				if (this.full(session, t)) {
					// more far land in that store than the disk has caught up with: this tile waits for the IO thread's
					// trim (never written, or saved, past the cap); tiles for the other store go on
					backlogged = true;
					continue;
				}
				if (!this.pending.remove(t.key(), t)) {
					continue; // replaced by a newer copy meanwhile: that one is written in its turn
				}
				if (this.on && this.atlas.session() == session) {
					if (t.cell() >= COARSE) {
						this.writeCoarse(session, t);
					} else {
						this.write(session, t);
					}
				}
			}
		} finally {
			long spent = System.nanoTime() - t0;
			WORKER_NANOS.addAndGet(spent);
			MAX_RUN_NANOS.accumulateAndGet(spent, Math::max);
			this.draining.set(false);
		}
		if (backlogged && this.atlas.session() == session) {
			try {
				WRITER.schedule(() -> this.drainLater(session), 50, java.util.concurrent.TimeUnit.MILLISECONDS);
			} catch (RuntimeException e) {
				// closing
			}
		} else if (!this.pending.isEmpty() && this.atlas.session() == session) {
			this.drainLater(session);
		}
	}

	/** Whether the far layer a tile goes to holds as much as memory may (then a trim is under way). */
	private boolean full(MapSession session, Tile t) {
		DimensionData d = session.dimensionIfOpen(t.dimension());
		if (d == null) {
			return false;
		}
		LayerData far = d.layerIfOpen(t.cell() >= COARSE ? COARSE_LAYER : LAYER);
		if (far == null || far.rawsInMemory() < HARD_RAWS) {
			return false;
		}
		this.trim(session, far);
		return true;
	}

	/** Sends what of the far layer is not being drawn to disk, on the IO thread; one trim at a time. */
	private void trim(MapSession session, LayerData far) {
		if (!this.trimming.compareAndSet(false, true)) {
			return;
		}
		try {
			session.io.execute(() -> {
				try {
					far.saveAndTrim(0, MAX_RAWS / 2, MAX_RAWS / 2, false);
				} finally {
					this.trimming.set(false);
				}
			});
		} catch (RuntimeException e) {
			this.trimming.set(false);
		}
	}

	/**
	 * Writes a far tile into the far layer (worker): each block column gets its cell's height, and the look (block,
	 * biome, water) of the cell at its chunk's middle: one colour a chunk, as the design draws far land.
	 */
	/**
	 * Writes a coarse far tile (32- to 512-block cells) into the coarse store (worker): each sample gets the height and
	 * look of the cell its middle falls in. A 512-block tile is about a million samples over a page or four; never per
	 * block.
	 */
	private void writeCoarse(MapSession session, Tile t) {
		long t0 = System.nanoTime();
		DimensionData d = session.dimensionIfOpen(t.dimension());
		if (d == null || d.ceiling) {
			return;
		}
		LayerData store = d.layer(COARSE_LAYER);
		int n = t.size();
		int cell = t.cell();
		int[] blocks = new int[n * n];
		int[] biomes = new int[n * n];
		byte[] depths = new byte[n * n];
		this.looks(session, t, blocks, biomes, depths);
		short[] heights = t.heights();
		int s0x = Math.floorDiv(t.minX(), COARSE);
		int s0z = Math.floorDiv(t.minZ(), COARSE);
		int s1x = Math.floorDiv(t.minX() + n * cell - 1, COARSE);
		int s1z = Math.floorDiv(t.minZ() + n * cell - 1, COARSE);
		int written = 0;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int rz = Coords.region(s0z); rz <= Coords.region(s1z); rz++) {
			int z0 = Math.max(s0z, rz * Coords.REGION_SIZE);
			int z1 = Math.min(s1z + 1, (rz + 1) * Coords.REGION_SIZE);
			for (int rx = Coords.region(s0x); rx <= Coords.region(s1x); rx++) {
				int x0 = Math.max(s0x, rx * Coords.REGION_SIZE);
				int x1 = Math.min(s1x + 1, (rx + 1) * Coords.REGION_SIZE);
				RegionRaw raw = store.raw(rx, rz, true);
				for (int sz = z0; sz < z1; sz++) {
					int j = Math.floorDiv(sz * COARSE + COARSE / 2 - t.minZ(), cell);
					if (j < 0 || j >= n) {
						continue;
					}
					int lz = Coords.inRegion(sz);
					for (int sx = x0; sx < x1; sx++) {
						int i = Math.floorDiv(sx * COARSE + COARSE / 2 - t.minX(), cell);
						if (i < 0 || i >= n) {
							continue;
						}
						int k = j * n + i;
						short y = heights[k];
						if (y == Short.MIN_VALUE) {
							continue;
						}
						if (raw.set(Coords.inRegion(sx), lz, y, blocks[k], depths[k], biomes[k], RegionRaw.KNOWN)) {
							written++;
							minX = Math.min(minX, sx);
							maxX = Math.max(maxX, sx);
							minZ = Math.min(minZ, sz);
							maxZ = Math.max(maxZ, sz);
						}
					}
				}
			}
		}
		if (written > 0) {
			store.dirty(minX, minZ, maxX + 1, maxZ + 1);
		}
		if (store.rawsInMemory() > MAX_RAWS) {
			// pages not being drawn go to disk on the IO thread, as the fine far layer's do
			this.trim(session, store);
		}
		COARSE_TILES.incrementAndGet();
		COARSE_SAMPLES.addAndGet(written);
		COARSE_NANOS.addAndGet(System.nanoTime() - t0);
	}

	/** Each cell's look (block, biome, water) as the map's names, once a tile. */
	private void looks(MapSession session, Tile t, int[] blocks, int[] biomes, byte[] depths) {
		int n = t.size();
		Map<String, Integer> blockIds = new HashMap<>();
		Map<String, Integer> biomeIds = new HashMap<>();
		for (int k = 0; k < n * n; k++) {
			if (t.heights()[k] == Short.MIN_VALUE) {
				continue;
			}
			String name = t.blocks() == null ? null : t.blocks()[k];
			boolean water = "minecraft:water".equals(name) || looksLikeWater(t.colours()[k]) && (name == null || !name.contains("ice"));
			blocks[k] = blockIds.computeIfAbsent(name == null || water ? FarColour.name(t.colours()[k]) : name, session.blockNames::id);
			depths[k] = (byte) (water ? 4 : 0);
			String biomeName = t.biomes() == null ? null : t.biomes()[k];
			biomes[k] = biomeName == null ? NamePalette.NONE : biomeIds.computeIfAbsent(biomeName, session.biomeNames::id);
		}
	}

	private void write(MapSession session, Tile t) {
		DimensionData d = session.dimensionIfOpen(t.dimension());
		if (d == null || d.ceiling) {
			return;
		}
		LayerData far = d.layer(LAYER);
		LayerData walked = d.layer(DimensionData.SURFACE);
		int n = t.size();
		int scale = t.cell();
		// each cell's look, once
		int[] blocks = new int[n * n];
		int[] biomes = new int[n * n];
		byte[] depths = new byte[n * n];
		Map<String, Integer> blockIds = new HashMap<>();
		Map<String, Integer> biomeIds = new HashMap<>();
		for (int k = 0; k < n * n; k++) {
			if (t.heights()[k] == Short.MIN_VALUE) {
				continue;
			}
			String name = t.blocks() == null ? null : t.blocks()[k];
			// LOD names the floor under water (sand, gravel); its colour says it is water
			boolean water = "minecraft:water".equals(name) || looksLikeWater(t.colours()[k]) && (name == null || !name.contains("ice"));
			// no block, or water (whose bed LOD does not say): the colour LOD drew, through the far names
			blocks[k] = blockIds.computeIfAbsent(name == null || water ? FarColour.name(t.colours()[k]) : name, session.blockNames::id);
			depths[k] = (byte) (water ? 4 : 0);
			String biomeName = t.biomes() == null ? null : t.biomes()[k];
			biomes[k] = biomeName == null ? NamePalette.NONE : biomeIds.computeIfAbsent(biomeName, session.biomeNames::id);
		}
		int span = n * scale;
		// per column of the tile, once: its cell, and the cell at its chunk's middle
		int[] colCell = new int[span];
		int[] colChunk = new int[span];
		for (int dx = 0; dx < span; dx++) {
			int x = t.minX() + dx;
			colCell[dx] = dx / scale;
			colChunk[dx] = Math.max(0, Math.min(n - 1, Math.floorDiv((x & ~15) + 8 - t.minX(), scale)));
		}
		short[] heights = t.heights();
		int written = 0;
		// region by region: each region's raws looked up once, then its rows written straight in
		int rx0 = Coords.region(t.minX());
		int rx1 = Coords.region(t.minX() + span - 1);
		int rz0 = Coords.region(t.minZ());
		int rz1 = Coords.region(t.minZ() + span - 1);
		for (int rz = rz0; rz <= rz1; rz++) {
			int z0 = Math.max(t.minZ(), rz * Coords.REGION_SIZE);
			int z1 = Math.min(t.minZ() + span, (rz + 1) * Coords.REGION_SIZE);
			for (int rx = rx0; rx <= rx1; rx++) {
				int x0 = Math.max(t.minX(), rx * Coords.REGION_SIZE);
				int x1 = Math.min(t.minX() + span, (rx + 1) * Coords.REGION_SIZE);
				RegionRaw raw = far.raw(rx, rz, true);
				RegionRaw own = walked.raw(rx, rz, false);
				for (int z = z0; z < z1; z++) {
					int dz = z - t.minZ();
					int rowCell = dz / scale * n;
					int rowChunk = Math.max(0, Math.min(n - 1, Math.floorDiv((z & ~15) + 8 - t.minZ(), scale))) * n;
					int lz = Coords.inRegion(z);
					for (int x = x0; x < x1; x++) {
						int dx = x - t.minX();
						int k = rowCell + colCell[dx];
						short y = heights[k];
						if (y == Short.MIN_VALUE) {
							continue;
						}
						int kc = rowChunk + colChunk[dx];
						if (heights[kc] == Short.MIN_VALUE) {
							kc = k;
						}
						int lx = Coords.inRegion(x);
						// walked land needs no far copy, and is never written over: the far layer is only drawn under it
						if (own != null && own.known(RegionRaw.index(lx, lz))) {
							continue;
						}
						if (raw.set(lx, lz, y, blocks[kc], depths[kc], biomes[kc], RegionRaw.KNOWN)) {
							written++;
						}
					}
				}
			}
		}
		if (written > 0) {
			far.dirty(t.minX(), t.minZ(), t.minX() + span, t.minZ() + span);
		}
		if (far.rawsInMemory() > MAX_RAWS) {
			// far land reaches wide: what is not being drawn goes to disk (on the IO thread, never in a worker's run), so
			// memory stays small; past HARD_RAWS the writing waits for it
			this.trim(session, far);
		}
		TILES.incrementAndGet();
		SAMPLES.addAndGet(written);
	}

	/** Whether a colour LOD drew is water's: clearly bluer than it is red or green. */
	static boolean looksLikeWater(int rgb) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return b > r + 24 && b > g + 6;
	}

	/** VOID LOD's API, reached by reflection so Atlas needs nothing of LOD to build or run. */
	static final class Lod {
		private static volatile Map<String, Method> parts;

		private Lod() {
		}

		private static final Map<String, Method> CALLS = new java.util.concurrent.ConcurrentHashMap<>();
		private static final Method NONE;

		static {
			try {
				NONE = Object.class.getMethod("hashCode");
			} catch (NoSuchMethodException e) {
				throw new IllegalStateException(e);
			}
		}

		/** Calls one of LodApi's static no-argument methods ({@code complete}, {@code radius}); null if it has none. */
		static Object call(String name) {
			Method m = CALLS.computeIfAbsent(name, n -> {
				try {
					Class<?> api = Class.forName("dev.voidmc.lod.api.LodApi", true, FarSight.class.getClassLoader());
					Method found = api.getMethod(n);
					return Modifier.isStatic(found.getModifiers()) ? found : NONE;
				} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
					return NONE;
				}
			});
			if (m == NONE) {
				return null;
			}
			try {
				return m.invoke(null);
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				return null;
			}
		}

		/** Adds a listener that hands each tile to {@code sink}; null if LOD has no such API. */
		static AutoCloseable listen(Consumer<Object> sink) {
			try {
				ClassLoader loader = FarSight.class.getClassLoader();
				Class<?> api = Class.forName("dev.voidmc.lod.api.LodApi", true, loader);
				Class<?> listener = Class.forName("dev.voidmc.lod.api.VisibleTerrainListener", true, loader);
				Method add = api.getMethod("addVisibleTerrainListener", listener);
				Object target = Modifier.isStatic(add.getModifiers()) ? null : api.getMethod("get").invoke(null);
				// VisibleTerrainListener has one method, onTerrain(VisibleTerrain)
				InvocationHandler handler = (proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class) {
						return switch (method.getName()) {
							case "hashCode" -> System.identityHashCode(proxy);
							case "equals" -> proxy == args[0];
							default -> "VOID Atlas far sight";
						};
					}
					if (method.isDefault()) {
						return InvocationHandler.invokeDefault(proxy, method, args);
					}
					if (args != null && args.length == 1) {
						sink.accept(args[0]);
					}
					return null;
				};
				Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, handler);
				Object closer = add.invoke(target, proxy);
				AtlasClient.LOGGER.info("VOID Atlas: far sight listening to VOID LOD");
				return closer instanceof AutoCloseable c ? c : () -> {
				};
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				AtlasClient.LOGGER.info("VOID Atlas: VOID LOD has no far-terrain API here ({}); far sight is off", e.toString());
				return null;
			}
		}

		/** A {@code VisibleTerrain} record's cell size, read before anything else (0 if it is not one). */
		static int cell(Object v) {
			try {
				for (RecordComponent c : v.getClass().getRecordComponents()) {
					if (c.getName().equals("cell")) {
						Method m = c.getAccessor();
						m.setAccessible(true);
						return (Integer) m.invoke(v);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// not a tile
			}
			return 0;
		}

		/** A {@code VisibleTerrain} record as Atlas's own tile (block states and biome keys turned into names), or null. */
		static Tile tile(Object v) {
			try {
				Map<String, Method> p = parts;
				if (p == null || !p.get("cell").getDeclaringClass().isInstance(v)) {
					Map<String, Method> got = new HashMap<>();
					for (RecordComponent c : v.getClass().getRecordComponents()) {
						Method m = c.getAccessor();
						m.setAccessible(true);
						got.put(c.getName(), m);
					}
					parts = p = got;
				}
				int cell = (Integer) p.get("cell").invoke(v);
				// fine (1 to 16 blocks) or coarse (32 to 512): anything else is left
				if (cell < MIN_CELL || cell > MAX_CELL && (cell < COARSE || cell > MAX_COARSE_CELL)) {
					return null;
				}
				String dim = ((ResourceKey<?>) p.get("dimension").invoke(v)).identifier().toString();
				long revision = p.containsKey("revision") ? (Long) p.get("revision").invoke(v) : 0;
				int minX = (Integer) p.get("minX").invoke(v);
				int minZ = (Integer) p.get("minZ").invoke(v);
				int size = (Integer) p.get("size").invoke(v);
				short[] heights = (short[]) p.get("heights").invoke(v);
				int[] colours = (int[]) p.get("colors").invoke(v);
				Object[] blockPalette = p.containsKey("blockPalette") ? (Object[]) p.get("blockPalette").invoke(v) : null;
				byte[] blocks = p.containsKey("blocks") ? (byte[]) p.get("blocks").invoke(v) : null;
				Object[] biomePalette = p.containsKey("biomePalette") ? (Object[]) p.get("biomePalette").invoke(v) : null;
				byte[] biomes = p.containsKey("biomes") ? (byte[]) p.get("biomes").invoke(v) : null;
				boolean[] seen = p.containsKey("seen") ? (boolean[]) p.get("seen").invoke(v) : null;
				String[] blockNames = names(blockPalette, blocks, o -> {
					if (o instanceof BlockState s && !s.isAir() && !s.is(Blocks.VOID_AIR)) {
						return s.getFluidState().is(FluidTags.WATER) && !s.isSolid() ? "minecraft:water" : BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
					}
					return null;
				});
				String[] biomeNames = names(biomePalette, biomes, o -> o instanceof ResourceKey<?> key ? key.identifier().toString() : null);
				return new Tile(dim, revision, minX, minZ, cell, size, heights, colours, blockNames, biomeNames, seen);
			} catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
				return null;
			}
		}

		/** A palette and per-cell indices (-1 unknown) as names per cell; null without them. */
		private static String[] names(Object[] palette, byte[] cells, java.util.function.Function<Object, String> name) {
			if (palette == null || cells == null) {
				return null;
			}
			String[] byIndex = new String[palette.length];
			for (int i = 0; i < palette.length; i++) {
				byIndex[i] = name.apply(palette[i]);
			}
			String[] out = new String[cells.length];
			for (int i = 0; i < cells.length; i++) {
				int b = cells[i] & 0xFF;
				out[i] = cells[i] == -1 || b >= byIndex.length ? null : byIndex[b];
			}
			return out;
		}
	}
}
