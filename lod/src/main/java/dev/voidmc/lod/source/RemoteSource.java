package dev.voidmc.lod.source;

import dev.voidmc.lod.core.ColumnSample;
import dev.voidmc.lod.core.TerrainSource;
import dev.voidmc.lod.core.TileBuilder;
import dev.voidmc.lod.core.TileKey;
import dev.voidmc.lod.net.LodNet;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.LoggerFactory;

/**
 * The land past the view on a server that runs VOID LOD: its generator's facts, asked for tile by tile
 * ({@link LodNet.TileRequest}) and coloured here with this client's own textures ({@link ExpanseColours}), so the far
 * land on a server looks as it does in singleplayer, and the player sees the world beyond where they have been. A tile
 * the server has not described yet is not covered: it is asked for, and built again when its facts come in. Under the
 * generator cache, so a server's horizon is kept on disk like a singleplayer one, and under the chunks the player has
 * seen, which win.
 */
public final class RemoteSource implements TerrainSource {
	/** Asked again if no answer came in this long: the server's queue was full, or the request was lost. */
	private static final long RETRY_NANOS = 20_000_000_000L;
	/** Requests in flight at most: the server keeps up with a few dozen. */
	private static final int IN_FLIGHT = 64;
	private static final ExecutorService DECODE = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VOID LOD server facts");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** One tile's facts, as the server sent them for a grid. */
	private record Tile(int x0, int z0, int cell, int border, int size, ByteBuffer facts, Holder<Biome>[] biomes, short[] biome, byte[] bandOffset) {
	}

	private final Level level;
	private final ExpanseColours colours;
	private final int seaLevel;
	private final long version;
	private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();
	private final Map<Long, Long> asked = new ConcurrentHashMap<>();
	private final ThreadLocal<Tile> current = new ThreadLocal<>();
	private final ThreadLocal<ExpanseFacts> facts = ThreadLocal.withInitial(ExpanseFacts::new);
	private volatile Invalidate invalidate;
	public final AtomicLong received = new AtomicLong();
	public final AtomicLong bytes = new AtomicLong();

	/** Told when a tile's facts came in, with its block bounds: the engine builds the tiles there again. */
	public interface Invalidate {
		void invalidate(int minX, int minZ, int maxX, int maxZ);
	}

	public RemoteSource(Level level, LodNet.Hello hello, Materials materials, long version) {
		this.level = level;
		this.seaLevel = hello.seaLevel();
		this.version = version;
		BiomeLooks looks = new BiomeLooks(level, materials, hello.trees().isEmpty() ? null : hello.trees());
		int[] strata = null;
		int[] strataMaterials = null;
		if (!hello.strata().isEmpty()) {
			BlockState[] bands = new BlockState[hello.strata().size()];
			for (int i = 0; i < bands.length; i++) {
				try {
					bands[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, hello.strata().get(i), false).blockState();
				} catch (Exception e) {
					bands[i] = net.minecraft.world.level.block.Blocks.TERRACOTTA.defaultBlockState();
				}
			}
			strata = new int[bands.length];
			for (int i = 0; i < bands.length; i++) {
				strata[i] = looks.colorOf(bands[i]);
			}
			strataMaterials = looks.materials(bands, false);
		}
		this.colours = new ExpanseColours(looks, materials, this.seaLevel, strata, strataMaterials);
	}

	public void onFacts(Invalidate invalidate) {
		this.invalidate = invalidate;
	}

	@Override
	public long version() {
		return this.version;
	}

	public int seaLevel() {
		return this.seaLevel;
	}

	/** The grid tile {@code key} samples, as the tile builder lays it out: x0, z0, cell, border, size. */
	private static int[] grid(long key) {
		int level = TileKey.level(key);
		int cell = TileKey.cell(level);
		boolean corners = !TileBuilder.packFormat && level >= TileBuilder.smoothFrom;
		int border = corners ? 2 : 1;
		int size = (corners ? TileKey.TILE + 1 : TileKey.TILE) + 2 * border;
		int offset = corners ? 0 : cell >> 1;
		return new int[]{TileKey.minX(key) + offset, TileKey.minZ(key) + offset, cell, border, size};
	}

	@Override
	public boolean covers(int minX, int minZ, int maxX, int maxZ) {
		int span = maxX - minX;
		int level = Integer.numberOfTrailingZeros(span / TileKey.TILE);
		long key = TileKey.of(level, Math.floorDiv(minX, span), Math.floorDiv(minZ, span));
		int[] g = grid(key);
		Tile t = this.tiles.get(key);
		if (t != null && t.x0() == g[0] && t.z0() == g[1] && t.size() == g[4]) {
			return true;
		}
		this.ask(key, g);
		return false;
	}

	private void ask(long key, int[] g) {
		long now = System.nanoTime();
		Long at = this.asked.get(key);
		if (at != null && now - at < RETRY_NANOS) {
			return;
		}
		if (this.asked.size() >= IN_FLIGHT) {
			// forget the oldest, long unanswered: they are asked again when still wanted
			this.asked.entrySet().removeIf(e -> now - e.getValue() > RETRY_NANOS);
			if (this.asked.size() >= IN_FLIGHT) {
				return;
			}
		}
		this.asked.put(key, now);
		net.minecraft.client.Minecraft.getInstance().execute(() -> {
			if (ClientPlayNetworking.canSend(LodNet.TileRequest.TYPE)) {
				ClientPlayNetworking.send(new LodNet.TileRequest(key, g[0], g[1], g[2], g[3], g[4]));
			}
		});
	}

	/** A tile's facts came in (client thread): decoded off it, then the tiles there are built again. */
	public void receive(LodNet.TileFacts payload) {
		DECODE.execute(() -> {
			long key = payload.key();
			this.asked.remove(key);
			if (payload.data().length == 0) {
				return;
			}
			int[] g = grid(key);
			if (payload.size() != g[4]) {
				// asked for another layout (a pack switched on since): asked again when wanted
				return;
			}
			try {
				this.tiles.put(key, this.decode(payload, g));
			} catch (IOException | RuntimeException e) {
				LoggerFactory.getLogger("VOID LOD").warn("VOID LOD: could not read the server's facts for tile {}", key, e);
				return;
			}
			this.received.incrementAndGet();
			this.bytes.addAndGet(payload.data().length);
			Invalidate i = this.invalidate;
			if (i != null) {
				int span = TileKey.span(TileKey.level(key));
				i.invalidate(TileKey.minX(key), TileKey.minZ(key), TileKey.minX(key) + span, TileKey.minZ(key) + span);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private Tile decode(LodNet.TileFacts payload, int[] g) throws IOException {
		Inflater inflater = new Inflater();
		byte[] raw;
		try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(payload.data()), inflater)) {
			raw = in.readAllBytes();
		} finally {
			inflater.end();
		}
		DataInputStream h = new DataInputStream(new ByteArrayInputStream(raw));
		int names = h.readUnsignedShort();
		List<Holder<Biome>> biomes = new ArrayList<>(names);
		var registry = this.level.registryAccess().lookupOrThrow(Registries.BIOME);
		int headerBytes = 2;
		for (int i = 0; i < names; i++) {
			String name = h.readUTF();
			headerBytes += 2 + name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
			Holder<Biome> biome = name.isEmpty() ? null : registry.get(ResourceKey.create(Registries.BIOME, Identifier.parse(name))).orElse(null);
			biomes.add(biome);
		}
		int n = payload.size() * payload.size();
		ByteBuffer body = ByteBuffer.wrap(raw, headerBytes, raw.length - headerBytes).slice();
		ByteBuffer facts = ByteBuffer.allocate(n * ExpanseFacts.BYTES);
		short[] biome = new short[n];
		byte[] band = new byte[n];
		for (int s = 0; s < n; s++) {
			byte[] one = new byte[ExpanseFacts.BYTES];
			body.get(one);
			facts.put(one);
			biome[s] = body.getShort();
			band[s] = body.get();
		}
		facts.flip();
		return new Tile(g[0], g[1], g[2], g[3], g[4], facts, biomes.toArray(Holder[]::new), biome, band);
	}

	@Override
	public void beginTile(long key, int x0, int z0, int cell, int border, int size) {
		Tile t = this.tiles.get(key);
		this.current.set(t != null && t.x0() == x0 && t.z0() == z0 && t.cell() == cell && t.size() == size ? t : null);
	}

	@Override
	public boolean partial() {
		return true;
	}

	@Override
	public void endTile() {
		this.current.remove();
	}

	@Override
	public void sample(int x, int z, int cell, ColumnSample out) {
		Tile t = this.current.get();
		if (t == null || cell != t.cell()) {
			out.empty = true;
			return;
		}
		int dx = x - t.x0(), dz = z - t.z0();
		if (dx % cell != 0 || dz % cell != 0) {
			out.empty = true;
			return;
		}
		int i = dx / cell + t.border(), j = dz / cell + t.border();
		if (i < 0 || j < 0 || i >= t.size() || j >= t.size()) {
			out.empty = true;
			return;
		}
		int s = i + j * t.size();
		ExpanseFacts f = this.facts.get();
		f.read(t.facts().duplicate().position(s * ExpanseFacts.BYTES));
		Holder<Biome> biome = t.biomes()[t.biome()[s]];
		if (biome == null) {
			out.empty = true;
			return;
		}
		f.geometry(cell, this.seaLevel);
		this.colours.colour(f, biome, x, z, cell, t.bandOffset()[s], out);
	}

	/** Tiles asked for and not answered yet. */
	public int pending() {
		return this.asked.size();
	}

	public String stats() {
		return String.format(java.util.Locale.ROOT, "server facts: %d tiles (%.1f MB), %d asked", this.received.get(), this.bytes.get() / 1048576.0,
			this.asked.size());
	}
}
