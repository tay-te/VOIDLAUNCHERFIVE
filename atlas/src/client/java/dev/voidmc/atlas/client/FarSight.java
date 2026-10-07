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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Terrain seen from afar: what VOID LOD drew on the player's screen beyond the loaded chunks, mapped as its own layer
 * ({@value #LAYER}), in less detail, under the walked land and never mixed into it. Optional both ways: with no LOD
 * (mod id {@value #LOD_MOD}) nothing here runs, and Atlas has no compile dependency on it (LOD's API is reached by
 * reflection, its listener is a proxy).
 *
 * <p>LOD's API ({@code dev.voidmc.lod.api}): {@code LodApi.addVisibleTerrainListener(listener)} returns an
 * {@code AutoCloseable}; LOD then sends the tiles it has drawn so far and each tile it newly draws, on one LOD thread, in
 * order, as {@code VisibleTerrain(dimension, minX, minZ, cell, size, heights, colors, palette, blocks, seen)} (row major,
 * x fastest; {@code cell} blocks a cell; fresh arrays). Atlas takes a tile in a few microseconds on LOD's thread (checks
 * it, files it by place, replacing an older copy not yet written) and writes it on its own workers within a time budget
 * per run; the client thread never does any of it.
 *
 * <p>Fair play: only what LOD drew for this player, only in the dimension the player is in, only while the map and the
 * server allow it; only cells coarser than the walked map (never a 1-block cell), never over a walked column, and never
 * a discovery: the far layer is drawn under the walked land and nothing reads places, areas or the journal from it. On
 * a server, only cells LOD built from chunks it was sent ({@code seen}), not from a generator.
 */
public final class FarSight {
	public static final String LOD_MOD = "void_lod";
	public static final String LAYER = "far";
	/** The finest cell taken: the walked map is 1 block a column, so far sight only takes what is coarser. */
	public static final int MIN_CELL = 2;
	/** The coarsest cell taken: a 64-cell tile of 16-block cells covers 1,024 blocks a side (a million columns). */
	public static final int MAX_CELL = 16;
	public static final int MAX_SIZE = 256;
	/** How long one worker run may write far tiles before it gives the worker back (and runs again later). */
	public static final long RUN_BUDGET_NANOS = 4_000_000L;
	/** Tiles waiting to be written, at most; past it the oldest waiting are dropped (LOD sends them again when redrawn). */
	public static final int MAX_PENDING = 512;

	/** For the perf log: tiles taken and written, samples written, worker time, and time on LOD's thread. */
	public static final AtomicLong TAKEN = new AtomicLong();
	public static final AtomicLong TILES = new AtomicLong();
	public static final AtomicLong SAMPLES = new AtomicLong();
	public static final AtomicLong WORKER_NANOS = new AtomicLong();
	public static final AtomicLong LISTENER_NANOS = new AtomicLong();
	public static final AtomicLong DROPPED = new AtomicLong();
	/** The longest worker run (one run writes tiles until {@link #RUN_BUDGET_NANOS} is spent). */
	public static final AtomicLong MAX_RUN_NANOS = new AtomicLong();

	/** One far tile, as Atlas keeps it until written. */
	public record Tile(String dimension, int minX, int minZ, int cell, int size, short[] heights, int[] colours, String[] blocks, boolean[] seen) {
		long key() {
			return (long) this.minX * 0x9E3779B1L ^ (long) this.minZ * 0x85EBCA77L ^ this.cell;
		}
	}

	private final AtlasClient atlas;
	private final Map<Long, Tile> pending = new ConcurrentHashMap<>();
	private final AtomicBoolean draining = new AtomicBoolean();
	private volatile boolean on;
	private AutoCloseable subscription;
	private String subscribedFor = "";
	private boolean lodMissing;

	public FarSight(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/** Kept for the start-up call; the listener is added by {@link #update} once there is a map to fill. */
	public void register() {
		this.lodMissing = !FabricLoader.getInstance().isModLoaded(LOD_MOD);
	}

	/**
	 * Client thread, about once a second: listens to LOD while far sight is wanted, and listens again (so LOD sends what
	 * it has drawn so far once more) when the map, the dimension or the setting changes.
	 */
	public void update() {
		this.on = this.enabled();
		Minecraft mc = Minecraft.getInstance();
		MapSession session = this.atlas.session();
		String want = this.on && session != null && mc.level != null ? session.worldKey + "|" + mc.level.dimension().identifier() : "";
		if (want.equals(this.subscribedFor)) {
			return;
		}
		this.close();
		this.pending.clear();
		if (!want.isEmpty() && !this.lodMissing) {
			this.subscription = Lod.listen(this::take);
			if (this.subscription == null) {
				this.lodMissing = true;
			}
		}
		this.subscribedFor = want;
	}

	/** Stops listening (closing the map, leaving the world). */
	public void close() {
		AutoCloseable s = this.subscription;
		this.subscription = null;
		this.subscribedFor = "";
		if (s != null) {
			try {
				s.close();
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
			Tile tile = Lod.tile(visibleTerrain);
			if (tile != null) {
				this.offer(tile);
			}
		} catch (RuntimeException | LinkageError e) {
			// a tile that does not keep the contract is dropped
		} finally {
			LISTENER_NANOS.addAndGet(System.nanoTime() - t0);
		}
	}

	/** Files a far tile to be written (any thread). Public for the perf scenario, which feeds made-up tiles. */
	public void offer(Tile tile) {
		if (!this.on || tile.cell() < MIN_CELL || tile.cell() > MAX_CELL || tile.size() <= 0 || tile.size() > MAX_SIZE) {
			return;
		}
		int n = tile.size() * tile.size();
		if (tile.heights().length < n || tile.colours().length < n || tile.blocks() != null && tile.blocks().length < n
			|| tile.seen() != null && tile.seen().length < n) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (level == null || !tile.dimension().equals(level.dimension().identifier().toString())) {
			return;
		}
		MapSession session = this.atlas.session();
		if (session == null) {
			return;
		}
		TAKEN.incrementAndGet();
		if (this.pending.size() >= MAX_PENDING) {
			// LOD sends a tile again whenever it draws it again; the newest are worth more
			var it = this.pending.keySet().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
				DROPPED.incrementAndGet();
			}
		}
		this.pending.put(tile.key(), tile);
		this.drainLater(session);
	}

	private void drainLater(MapSession session) {
		if (this.draining.compareAndSet(false, true)) {
			try {
				session.workers.execute(() -> this.drain(session));
			} catch (RuntimeException e) {
				this.draining.set(false);
			}
		}
	}

	/** Writes waiting tiles until the run's budget is spent, then hands the worker back and runs again (worker). */
	private void drain(MapSession session) {
		long t0 = System.nanoTime();
		try {
			boolean multiplayer = this.atlas.multiplayer();
			var it = this.pending.entrySet().iterator();
			while (it.hasNext() && System.nanoTime() - t0 < RUN_BUDGET_NANOS) {
				var e = it.next();
				it.remove();
				if (this.on && this.atlas.session() == session) {
					this.write(session, e.getValue(), multiplayer);
				}
			}
		} finally {
			long spent = System.nanoTime() - t0;
			WORKER_NANOS.addAndGet(spent);
			MAX_RUN_NANOS.accumulateAndGet(spent, Math::max);
			this.draining.set(false);
		}
		if (!this.pending.isEmpty() && this.atlas.session() == session) {
			this.drainLater(session);
		}
	}

	/** Writes a far tile into the far layer (worker): each cell fills its cell x cell block columns. */
	private void write(MapSession session, Tile t, boolean multiplayer) {
		DimensionData d = session.dimensionIfOpen(t.dimension());
		if (d == null || d.ceiling) {
			return;
		}
		LayerData far = d.layer(LAYER);
		LayerData walked = d.layer(DimensionData.SURFACE);
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		RegionRaw raw = null;
		RegionRaw own = null;
		long rawKey = Long.MIN_VALUE;
		int written = 0;
		int n = t.size();
		int scale = t.cell();
		Map<String, Integer> ids = new HashMap<>();
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				int k = j * n + i;
				short y = t.heights()[k];
				if (y == Short.MIN_VALUE || multiplayer && t.seen() != null && !t.seen()[k]) {
					continue;
				}
				String name = t.blocks() == null ? null : t.blocks()[k];
				boolean water = "minecraft:water".equals(name);
				int block;
				if (name == null || water) {
					// no block (or water, whose bed LOD does not say): the colour LOD drew, through the far names
					block = ids.computeIfAbsent(FarColour.name(t.colours()[k]), session.blockNames::id);
				} else {
					block = ids.computeIfAbsent(name, session.blockNames::id);
				}
				int depth = water ? 4 : 0;
				for (int dz = 0; dz < scale; dz++) {
					for (int dx = 0; dx < scale; dx++) {
						int x = t.minX() + i * scale + dx;
						int z = t.minZ() + j * scale + dz;
						long key = Coords.pack(Coords.region(x), Coords.region(z));
						if (key != rawKey) {
							raw = far.raw(Coords.region(x), Coords.region(z), true);
							own = walked.raw(Coords.region(x), Coords.region(z), false);
							rawKey = key;
						}
						int lx = Coords.inRegion(x);
						int lz = Coords.inRegion(z);
						// walked land needs no far copy, and is never written over: the far layer is only drawn under it
						if (own != null && own.known(RegionRaw.index(lx, lz))) {
							continue;
						}
						if (raw.set(lx, lz, y, block, depth, NamePalette.NONE, RegionRaw.KNOWN)) {
							written++;
							minX = Math.min(minX, x);
							minZ = Math.min(minZ, z);
							maxX = Math.max(maxX, x);
							maxZ = Math.max(maxZ, z);
						}
					}
				}
			}
		}
		if (minX <= maxX) {
			far.dirty(minX, minZ, maxX + 1, maxZ + 1);
		}
		TILES.incrementAndGet();
		SAMPLES.addAndGet(written);
	}

	/** VOID LOD's API, reached by reflection so Atlas needs nothing of LOD to build or run. */
	static final class Lod {
		private static Map<String, Method> parts;

		private Lod() {
		}

		/** Adds a listener that hands each tile to {@code sink}; null if LOD has no such API. */
		static AutoCloseable listen(java.util.function.Consumer<Object> sink) {
			try {
				ClassLoader loader = FarSight.class.getClassLoader();
				Class<?> api = Class.forName("dev.voidmc.lod.api.LodApi", true, loader);
				Class<?> listener = Class.forName("dev.voidmc.lod.api.VisibleTerrainListener", true, loader);
				Method add = api.getMethod("addVisibleTerrainListener", listener);
				Object target = Modifier.isStatic(add.getModifiers()) ? null : api.getMethod("get").invoke(null);
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

		/** A {@code VisibleTerrain} record as Atlas's own tile (block states turned into names), or null. */
		static Tile tile(Object v) throws RuntimeException {
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
				String dim = ((ResourceKey<?>) p.get("dimension").invoke(v)).identifier().toString();
				int minX = (Integer) p.get("minX").invoke(v);
				int minZ = (Integer) p.get("minZ").invoke(v);
				int cell = (Integer) p.get("cell").invoke(v);
				int size = (Integer) p.get("size").invoke(v);
				short[] heights = (short[]) p.get("heights").invoke(v);
				int[] colours = (int[]) p.get("colors").invoke(v);
				Object[] palette = p.containsKey("palette") ? (Object[]) p.get("palette").invoke(v) : null;
				byte[] blocks = p.containsKey("blocks") ? (byte[]) p.get("blocks").invoke(v) : null;
				boolean[] seen = p.containsKey("seen") ? (boolean[]) p.get("seen").invoke(v) : null;
				if (cell < MIN_CELL || cell > MAX_CELL) {
					return null;
				}
				String[] names = null;
				if (palette != null && blocks != null) {
					String[] byIndex = new String[palette.length];
					for (int i = 0; i < palette.length; i++) {
						if (palette[i] instanceof BlockState s && !s.isAir() && !s.is(Blocks.VOID_AIR)) {
							byIndex[i] = s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && !s.isSolid() ? "minecraft:water"
								: BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
						}
					}
					names = new String[blocks.length];
					for (int i = 0; i < blocks.length; i++) {
						int b = blocks[i] & 0xFF;
						names[i] = blocks[i] == -1 || b >= byIndex.length ? null : byIndex[b];
					}
				}
				return new Tile(dim, minX, minZ, cell, size, heights, colours, names, seen);
			} catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
				return null;
			}
		}
	}
}
